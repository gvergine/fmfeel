package jsm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Concurrency tests for {@link StateMachineRunnable}.
 *
 * Two rules keep these deterministic instead of timing-dependent:
 *   - Coordinate with CountDownLatches, never Thread.sleep.
 *   - Always join() the runner thread before reading machine state, so the
 *     runner thread's writes are guaranteed visible (join establishes
 *     happens-before). getCurrentState() is NOT safe to read cross-thread
 *     while the runner is still live.
 */
@Timeout(10) // no test should ever hang; a hang is a failure, not a wait
class StateMachineRunnerTest
{
	/** Lines the runner/machine logged (written by the runner thread). */
	private final List<String> log = Collections.synchronizedList(new ArrayList<>());

	/** Callback trace, e.g. "enter:A", "event:A:go" (written by the runner thread). */
	private final List<String> trace = Collections.synchronizedList(new ArrayList<>());

	/** Generic state A-C with optional hooks so a test can inject blocking / latches. */
	private final class Node implements State
	{
		private final String name;
		private Consumer<Event> onEventHook;
		private Runnable onEnterHook;

		private Node(String name) { this.name = name; }

		Node onEvent(Consumer<Event> hook) { this.onEventHook = hook; return this; }
		Node onEnter(Runnable hook)        { this.onEnterHook = hook; return this; }

		@Override public String getName() { return name; }

		@Override public void onEnter()
		{
			trace.add("enter:" + name);
			if (onEnterHook != null) onEnterHook.run();
		}

		@Override public void onExit() { trace.add("exit:" + name); }

		@Override public void onEvent(Event event)
		{
			trace.add("event:" + name + ":" + event.getName());
			if (onEventHook != null) onEventHook.accept(event);
		}

		@Override public String toString() { return name; }
	}

	private Node a, b, c;

	@BeforeEach
	void freshStates()
	{
		a = new Node("A");
		b = new Node("B");
		c = new Node("C");
	}

	private StateMachine machine(State initial)          { return new StateMachine(initial, log::add); }
	private StateMachineRunnable runner(StateMachine sm, boolean enter) { return new StateMachineRunnable(sm, enter, log::add); }
	private static Event event(String name)              { return Event.build(name); }

	private static Thread start(StateMachineRunnable runner)
	{
		Thread t = new Thread(runner, "sm-runner-test");
		t.setDaemon(true);
		t.start();
		return t;
	}

	private static void post(StateMachineRunnable runner, String... names)
	{
		for (String n : names) runner.getEventQueue().add(event(n));
	}

	private static void awaitOrFail(CountDownLatch latch) throws InterruptedException
	{
		assertTrue(latch.await(8, TimeUnit.SECONDS), "timed out waiting for latch");
	}

	private static void joinOrFail(Thread t) throws InterruptedException
	{
		t.join(8000);
		assertFalse(t.isAlive(), "runner thread did not terminate");
	}

	// ------------------------------------------------------------------

	@Nested @DisplayName("Event processing")
	class EventProcessing
	{
		@Test void processesQueuedEventsInFifoOrder() throws InterruptedException
		{
			CountDownLatch processed = new CountDownLatch(3);
			a.onEvent(e -> processed.countDown());

			StateMachine sm = machine(a);
			sm.addTransition(a, "e1", null);
			sm.addTransition(a, "e2", null);
			sm.addTransition(a, "e3", null);

			StateMachineRunnable runner = runner(sm, false);
			Thread t = start(runner);
			post(runner, "e1", "e2", "e3");

			awaitOrFail(processed);        // all three handled before we stop
			runner.stop();
			joinOrFail(t);

			assertEquals(List.of("event:A:e1", "event:A:e2", "event:A:e3"), trace);
		}

		@Test void drivesTheMachineThroughStateTransitions() throws InterruptedException
		{
			CountDownLatch reachedC = new CountDownLatch(1);
			c.onEnter(reachedC::countDown);

			StateMachine sm = machine(a);
			sm.addTransition(a, "go", b);
			sm.addTransition(b, "go", c);

			StateMachineRunnable runner = runner(sm, false);
			Thread t = start(runner);
			post(runner, "go", "go");

			awaitOrFail(reachedC);
			runner.stop();
			joinOrFail(t);

			assertSame(c, sm.getCurrentState());   // safe: read after join
			assertEquals(List.of(
					"event:A:go", "exit:A", "enter:B",
					"event:B:go", "exit:B", "enter:C"), trace);
		}

		@Test void unknownEventsAreIgnoredAndDoNotStopTheRunner() throws InterruptedException
		{
			CountDownLatch handled = new CountDownLatch(1);
			a.onEvent(e -> handled.countDown());

			StateMachine sm = machine(a);
			sm.addTransition(a, "known", null);

			StateMachineRunnable runner = runner(sm, false);
			Thread t = start(runner);
			post(runner, "mystery");   // no transition -> ignored, runner keeps going
			post(runner, "known");     // proves the loop is still alive afterwards

			awaitOrFail(handled);
			runner.stop();
			joinOrFail(t);

			assertEquals(List.of("event:A:known"), trace);
		}
	}

	@Nested @DisplayName("Stopping")
	class Stopping
	{
		@Test void requestStopTerminatesTheThread() throws InterruptedException
		{
			StateMachineRunnable runner = runner(machine(a), false);
			Thread t = start(runner);

			runner.stop();
			joinOrFail(t);
		}

		@Test void requestStopBeforeAnyEventStillStops() throws InterruptedException
		{
			CountDownLatch entered = new CountDownLatch(1);
			a.onEnter(entered::countDown);

			StateMachineRunnable runner = runner(machine(a), true);
			Thread t = start(runner);

			awaitOrFail(entered);          // machine is up and looping
			runner.stop();
			joinOrFail(t);
		}

		@Test void requestStopIsIdempotent() throws InterruptedException
		{
			StateMachineRunnable runner = runner(machine(a), false);
			Thread t = start(runner);

			runner.stop();
			runner.stop();          // extra sentinels must not wedge anything
			runner.stop();
			joinOrFail(t);
		}

		/**
		 * Documents the CURRENT semantics: requestStop() clears the queue before
		 * injecting the sentinel, so events already queued but not yet taken are
		 * DROPPED rather than drained. If you switch to graceful drain (remove the
		 * clear()), flip this test to assert later1/later2 ARE processed.
		 */
		@Test void requestStopDropsPendingQueuedEvents() throws InterruptedException
		{
			CountDownLatch insideBlock = new CountDownLatch(1);
			CountDownLatch release = new CountDownLatch(1);

			a.onEvent(e -> {
				if (e.getName().equals("block"))
				{
					insideBlock.countDown();
					try { release.await(); } catch (InterruptedException ignored) { }
				}
			});

			StateMachine sm = machine(a);
			sm.addTransition(a, "block", null);
			sm.addTransition(a, "later1", null);
			sm.addTransition(a, "later2", null);

			StateMachineRunnable runner = runner(sm, false);
			Thread t = start(runner);

			// Consumer takes "block" and parks inside onEvent; later1/later2 sit queued.
			post(runner, "block", "later1", "later2");
			awaitOrFail(insideBlock);

			runner.stop();          // clears later1/later2, injects sentinel
			release.countDown();           // let "block" finish
			joinOrFail(t);

			assertEquals(List.of("event:A:block"), trace);
			assertFalse(trace.contains("event:A:later1"));
			assertFalse(trace.contains("event:A:later2"));
		}
	}

	@Nested @DisplayName("Interruption")
	class Interruption
	{
		/**
		 * The run loop treats an interrupt as a stop signal: it logs the
		 * InterruptedException, restores the thread's interrupt status, and
		 * breaks out directly. So interrupting the thread terminates the runner
		 * without relying on the (interruptible, and therefore historically
		 * loss-prone) stop sentinel.
		 */
		@Test void interruptingTheThreadStopsTheRunner() throws InterruptedException
		{
			CountDownLatch entered = new CountDownLatch(1);
			a.onEnter(entered::countDown);

			StateMachineRunnable runner = runner(machine(a), true);
			Thread t = start(runner);

			awaitOrFail(entered);          // guarantees we're past start() and blocked in take()
			t.interrupt();
			joinOrFail(t);

			assertTrue(log.stream().anyMatch(l -> l.contains("InterruptedException")));
		}
	}

	@Nested @DisplayName("Exit sentinel safety")
	class ExitSentinelSafety
	{
		/**
		 * The sentinel is matched by identity (==), not by name, so a user event
		 * that merely shares the internal name must still be dispatched normally
		 * and must NOT stop the runner.
		 */
		@Test void userEventWithSentinelNameIsDispatchedNotSwallowed() throws InterruptedException
		{
			CountDownLatch handled = new CountDownLatch(1);
			a.onEvent(e -> handled.countDown());

			StateMachine sm = machine(a);
			sm.addTransition(a, "__JSM_INTERNAL_EXIT_EVENT", null);

			StateMachineRunnable runner = runner(sm, false);
			Thread t = start(runner);
			post(runner, "__JSM_INTERNAL_EXIT_EVENT");   // distinct instance from the real sentinel

			awaitOrFail(handled);          // if it were swallowed, this would time out
			runner.stop();
			joinOrFail(t);

			assertEquals(List.of("event:A:__JSM_INTERNAL_EXIT_EVENT"), trace);
		}
	}

	@Nested @DisplayName("Concurrent producers")
	class ConcurrentProducers
	{
		@Test void eventsFromManyThreadsAreAllProcessedExactlyOnce() throws InterruptedException
		{
			final int producers = 8;
			final int perProducer = 250;
			final int total = producers * perProducer;

			CountDownLatch allHandled = new CountDownLatch(total);
			a.onEvent(e -> allHandled.countDown());

			StateMachine sm = machine(a);
			sm.addTransition(a, "tick", null);

			StateMachineRunnable runner = runner(sm, false);
			Queue<Event> q = runner.getEventQueue();
			Thread runnerThread = start(runner);

			CountDownLatch go = new CountDownLatch(1);
			List<Thread> threads = new ArrayList<>();
			for (int p = 0; p < producers; p++)
			{
				Thread producer = new Thread(() -> {
					try { go.await(); } catch (InterruptedException ignored) { return; }
					for (int i = 0; i < perProducer; i++) q.add(event("tick"));
				});
				producer.start();
				threads.add(producer);
			}
			go.countDown();
			for (Thread producer : threads) producer.join();

			// Wait for the machine to actually process everything BEFORE stopping,
			// otherwise requestStop()'s clear() could legitimately drop stragglers.
			awaitOrFail(allHandled);
			runner.stop();
			joinOrFail(runnerThread);

			long handled = trace.stream().filter("event:A:tick"::equals).count();
			assertEquals(total, handled, "every tick from every producer handled exactly once");
			assertSame(a, sm.getCurrentState());
		}
	}

	@Nested @DisplayName("Construction and start")
	class ConstructionAndStart
	{
		@Test void nullStateMachineIsRejected()
		{
			assertThrows(NullPointerException.class, () -> runner(null, false));
		}

		@Test void nullLoggerIsRejected()
		{
			StateMachine sm = machine(a);
			assertThrows(NullPointerException.class, () -> new StateMachineRunnable(sm, false, null));
		}

		@Test void enterStateTrueCallsOnEnterOfInitialState() throws InterruptedException
		{
			CountDownLatch entered = new CountDownLatch(1);
			a.onEnter(entered::countDown);

			StateMachineRunnable runner = runner(machine(a), true);
			Thread t = start(runner);

			awaitOrFail(entered);
			runner.stop();
			joinOrFail(t);

			assertTrue(trace.contains("enter:A"));
		}

		@Test void enterStateFalseSkipsOnEnterOfInitialState() throws InterruptedException
		{
			CountDownLatch handled = new CountDownLatch(1);
			a.onEvent(e -> handled.countDown());

			StateMachine sm = machine(a);
			sm.addTransition(a, "ping", null);

			StateMachineRunnable runner = runner(sm, false);
			Thread t = start(runner);
			post(runner, "ping");

			awaitOrFail(handled);
			runner.stop();
			joinOrFail(t);

			assertFalse(trace.contains("enter:A"));
		}
	}
}