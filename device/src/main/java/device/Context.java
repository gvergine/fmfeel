package device;

import java.util.function.Consumer;

import com.fazecast.jSerialComm.SerialPort;

import jsm.EventDispatcher;

public class Context {
	public EventDispatcher eventDispatcher;
	public SerialPort[] serialPorts;
	public int probingIndex;
	public SerialLink serialLink;
	public Runnable onDeviceConnectedRunnable;
	public Runnable onDeviceDisconnectedRunnable;
	public Consumer<String> onMessageConsumer;
}
