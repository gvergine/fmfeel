package device;

import com.fazecast.jSerialComm.SerialPort;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * A self-managing serial link on top of jSerialComm.
 *
 * <p>Behaviour:
 * <ul>
 *   <li>Opens the port and continuously reads newline-delimited messages
 *       ('\n' terminates a message, '\r' is ignored).</li>
 *   <li>Each complete message is checked against a set of allowed messages.
 *       An unknown message stops the link.</li>
 *   <li>A read error / port failure / overlong line stops the link.</li>
 *   <li>If no valid message is received for {@code RECEIVE_TIMEOUT_MS}, the link stops.</li>
 *   <li>{@link #stop()} stops the link (idempotent, callable from any thread).</li>
 *   <li>{@link #send(String)} writes a line ("\r\n" appended). A failed write stops the link.</li>
 *   <li>If nothing has been sent for {@code HEARTBEAT_SILENCE_MS}, "HB\r\n" is sent automatically.</li>
 *   <li>Every successfully received (allowed) message is delivered to the registered listeners,
 *       which can be added and removed at will.</li>
 * </ul>
 *
 * <p>When the link stops for any reason, the optional {@code onStop} callback is invoked once
 * with the {@link StopReason}.
 *
 * <pre>{@code
 * SerialPort port = SerialPort.getCommPort("/dev/ttyACM0"); // or "COM5"
 * Set<String> allowed = Set.of(
 *     "HB",
 *     "LEFT BUTTON DOWN", "LEFT BUTTON UP", "RIGHT BUTTON DOWN", "RIGHT BUTTON UP",
 *     "ENCODER LEFT CW", "ENCODER LEFT CCW", "ENCODER RIGHT CW", "ENCODER RIGHT CCW");
 *
 * SerialLink link = new SerialLink(port, 115200, allowed);
 * link.setOnStop(reason -> System.out.println("link down: " + reason));
 *
 * Consumer<String> onMsg = msg -> System.out.println("rx: " + msg);
 * link.addListener(onMsg);
 * link.start();
 *
 * link.send("SHOW 0 Hello, radio!");
 * // ...
 * link.removeListener(onMsg);
 * link.stop();
 * }</pre>
 *
 * <p>Threading: listeners and the {@code onStop} callback are invoked synchronously on the
 * link's internal threads, so they must not block. Sends are serialised internally, so
 * {@link #send(String)} may be called from any thread.
 */
public class SerialLink {

    public enum StopReason {
        STOP_REQUESTED,      // stop() was called
        READ_ERROR,          // read failed / port error / overlong line
        DISALLOWED_MESSAGE,  // received a complete line that is not in the allowed set
        RECEIVE_TIMEOUT,     // no valid message for RECEIVE_TIMEOUT_MS
        SEND_ERROR,          // a write failed
        INTERNAL_ERROR       // unexpected failure in the watchdog task
    }

    /** Outcome of {@link #probe}. Only {@link #GOOD} means "this is the device". */
    public enum ProbeResult {
        GOOD,        // first clean message is in the allowed set (e.g. the device's HB)
        DISALLOWED,  // first clean message is not allowed (some other device)
        TIMEOUT,     // no clean message within the timeout (silent port)
        PORT_ERROR   // could not open the port, or a read error occurred
    }

    // ---- tunables (defaults match the requested behaviour) ----
    private static final long RECEIVE_TIMEOUT_MS   = 5_000;  // stop if silent this long
    private static final long HEARTBEAT_SILENCE_MS = 1_000;  // send HB after this much send-silence
    private static final long TICK_MS              = 250;    // watchdog + heartbeat check period
    private static final int  READ_POLL_MS         = 200;    // semi-blocking read wakeup
    private static final int  WRITE_TIMEOUT_MS     = 1_000;  // blocking write timeout
    private static final int  MAX_LINE             = 256;    // longer line -> treated as read error
    private static final String LINE_END           = "\r\n";
    private static final String HEARTBEAT          = "HB";   // sent as "HB\r\n"
    private static final int  PROBE_POLL_MS        = 100;    // read wakeup while probing

    private final SerialPort port;
    private final int baudRate;
    private final Set<String> allowed;
    private final CopyOnWriteArrayList<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object writeLock = new Object();

    private volatile Consumer<StopReason> onStop;
    private volatile long lastRxMs;   // time of last valid message received
    private volatile long lastTxMs;   // time of last successful send

    private volatile Thread reader;
    private volatile ScheduledExecutorService timers;
    
    private static final List<String> allowedMessages = Arrays.asList(
    		"HB",
    		"LEFT BUTTON DOWN",
    		"LEFT BUTTON UP",
    		"RIGHT BUTTON DOWN",
    		"RIGHT BUTTON UP",
    		"ENCODER LEFT CW",
    		"ENCODER LEFT CCW",
    		"ENCODER RIGHT CW",
    		"ENCODER RIGHT CCW"
    		);

    public SerialLink(SerialPort port) {
        this.port = port;
        this.baudRate = 115200;
        this.allowed = new HashSet<>(allowedMessages);
    }

    // ---- listeners ----

    /** Register a listener for received (allowed) messages. Keep the reference to remove it later. */
    public void addListener(Consumer<String> listener) { listeners.add(listener); }

    /** Remove a previously registered listener. */
    public void removeListener(Consumer<String> listener) { listeners.remove(listener); }

    /** Optional callback invoked once when the link stops, with the reason. */
    public void setOnStop(Consumer<StopReason> callback) { this.onStop = callback; }

    public boolean isRunning() { return running.get(); }

    // ---- lifecycle ----

    /** Opens the port and starts the reader + heartbeat. Throws if already running or the port won't open. */
    public void start() {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("SerialLink already started");
        }
        port.setBaudRate(baudRate);
        if (!port.openPort()) {
            running.set(false);
            throw new IllegalStateException("Could not open port " + port.getSystemPortName());
        }
        // Semi-blocking reads so the reader wakes every READ_POLL_MS to honour stop();
        // blocking writes so a failed send is reported promptly.
        port.setComPortTimeouts(
                SerialPort.TIMEOUT_READ_SEMI_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING,
                READ_POLL_MS, WRITE_TIMEOUT_MS);

        long now = System.currentTimeMillis();
        lastRxMs = now;
        lastTxMs = now;

        reader = new Thread(this::readLoop, "SerialLink-reader");
        reader.setDaemon(true);
        reader.start();

        timers = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SerialLink-timers");
            t.setDaemon(true);
            return t;
        });
        timers.scheduleAtFixedRate(this::tick, TICK_MS, TICK_MS, TimeUnit.MILLISECONDS);
    }

    /** Stops the link (idempotent, safe from any thread). */
    public void stop() { stop(StopReason.STOP_REQUESTED); }

    private void stop(StopReason reason) {
    	System.out.println("Stopping because " + reason);
        if (!running.compareAndSet(true, false)) {
            return; // already stopped; run teardown exactly once
        }
        try { port.closePort(); } catch (Exception ignore) {}   // also unblocks a pending read
        ScheduledExecutorService t = timers;
        if (t != null) t.shutdown();                            // safe even if called from a timer task
        Consumer<StopReason> cb = onStop;
        if (cb != null) {
            try { cb.accept(reason); } catch (Exception ignore) {}
        }
        // The reader thread notices running==false (or a -1 read) and exits on its own.
    }

    // ---- sending ----

    /**
     * Sends one line, appending "\r\n". Serialised against other sends and the heartbeat.
     * On any write failure the link stops. Returns {@code false} if the link is not running
     * or the write failed.
     */
    public boolean send(String line) {
        boolean failed = false;
        synchronized (writeLock) {
            if (!running.get()) {
                return false;
            }
            byte[] data = (line + LINE_END).getBytes(StandardCharsets.US_ASCII);
            try {
                int written = port.writeBytes(data, data.length);
                if (written != data.length) {
                    failed = true;
                } else {
                    lastTxMs = System.currentTimeMillis();
                }
            } catch (Exception e) {
                failed = true;
            }
        }
        if (failed) {
            stop(StopReason.SEND_ERROR);   // called outside the lock
            return false;
        }
        return true;
    }

    // ---- reader thread ----

    private void readLoop() {
        final byte[] buf = new byte[256];
        final StringBuilder line = new StringBuilder();
        try {
            while (running.get()) {
                int n = port.readBytes(buf, buf.length);
                if (!running.get()) {
                    break;                              // stopped while we were blocked
                }
                if (n < 0) {
                    stop(StopReason.READ_ERROR);        // genuine read/port error
                    break;
                }
                if (n == 0) {
                    continue;                           // read poll timeout; tick() enforces the 5 s
                }
                for (int i = 0; i < n; i++) {
                    char c = (char) (buf[i] & 0xFF);
                    if (c == '\n') {
                        String msg = line.toString();
                        line.setLength(0);
                        if (!msg.isEmpty() && !deliver(msg)) {
                            return;                     // disallowed message -> already stopped
                        }
                    } else if (c != '\r') {
                        line.append(c);
                        if (line.length() > MAX_LINE) {
                            stop(StopReason.READ_ERROR); // runaway line without a terminator
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            stop(StopReason.READ_ERROR);
        }
    }

    /** Validates and delivers a message. Returns false (after stopping) if it is not allowed. */
    private boolean deliver(String msg) {
    	System.out.println("received " + msg);
        if (!allowed.contains(msg)) {
            stop(StopReason.DISALLOWED_MESSAGE);
            return false;
        }
        lastRxMs = System.currentTimeMillis();
        for (Consumer<String> l : listeners) {
            try {
                l.accept(msg);
            } catch (Exception ignore) {
                // a misbehaving listener must not take down the link
            }
        }
        return true;
    }

    // ---- periodic watchdog + heartbeat ----

    private void tick() {
        try {
            if (!running.get()) {
                return;
            }
            long now = System.currentTimeMillis();
            if (now - lastRxMs >= RECEIVE_TIMEOUT_MS) {
                stop(StopReason.RECEIVE_TIMEOUT);
                return;
            }
            if (now - lastTxMs >= HEARTBEAT_SILENCE_MS) {
                send(HEARTBEAT);   // updates lastTxMs, or stops the link on write failure
            }
        } catch (Throwable t) {
            stop(StopReason.INTERNAL_ERROR);   // never let the scheduled task die silently
        }
    }

    // ---- probing (does not start the link) ----

    /**
     * Blocks up to {@code timeoutMs} to decide whether a port belongs to the device, WITHOUT
     * starting a link. Opens the port, discards the first (possibly partial) line to resync to
     * a message boundary, then classifies the next complete line:
     * <ul>
     *   <li>{@link ProbeResult#GOOD} - that line is in {@code allowedMessages} (e.g. the device's HB)</li>
     *   <li>{@link ProbeResult#DISALLOWED} - that line is not allowed (a different device)</li>
     *   <li>{@link ProbeResult#TIMEOUT} - no complete post-resync line within {@code timeoutMs}</li>
     *   <li>{@link ProbeResult#PORT_ERROR} - the port could not be opened, or a read failed</li>
     * </ul>
     * The port is always closed again before returning, so construct and {@link #start()} a
     * SerialLink on the winning port afterwards.
     *
     * <p>Because the first partial line is dropped to resync, allow at least two device message
     * intervals here (e.g. &ge; 4 s for a 2 s heartbeat). Blocks the caller - never call it on the UI thread.
     */
    public static ProbeResult probe(SerialPort port, int baudRate,
                                    Collection<String> allowedMessages, long timeoutMs) {
        Set<String> allowedSet = new HashSet<>(allowedMessages);
        port.setBaudRate(baudRate);
        if (!port.openPort()) {
            return ProbeResult.PORT_ERROR;
        }
        try {
            int poll = (int) Math.max(1, Math.min(PROBE_POLL_MS, timeoutMs));
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, poll, 0);
            long deadline = System.currentTimeMillis() + timeoutMs;
            byte[] buf = new byte[256];
            StringBuilder line = new StringBuilder();
            boolean synced = false;   // false until we pass the first '\n' (drop the partial line)
            while (System.currentTimeMillis() < deadline) {
                int n = port.readBytes(buf, buf.length);
                if (n < 0) {
                    return ProbeResult.PORT_ERROR;
                }
                if (n == 0) {
                    continue;   // read poll timeout; keep waiting until the deadline
                }
                for (int i = 0; i < n; i++) {
                    char c = (char) (buf[i] & 0xFF);
                    if (c == '\n') {
                        if (!synced) {
                            synced = true;         // first newline: partial line dropped, resynced
                            line.setLength(0);
                            continue;
                        }
                        String msg = line.toString();
                        line.setLength(0);
                        if (!msg.isEmpty()) {
                            return allowedSet.contains(msg) ? ProbeResult.GOOD : ProbeResult.DISALLOWED;
                        }
                    } else if (c != '\r' && synced) {
                        line.append(c);
                        if (line.length() > MAX_LINE) {
                            return ProbeResult.DISALLOWED;   // overlong garbage: not our device
                        }
                    }
                    // pre-sync non-newline bytes are discarded
                }
            }
            return ProbeResult.TIMEOUT;
        } finally {
            try { port.closePort(); } catch (Exception ignore) {}
        }
    }
}