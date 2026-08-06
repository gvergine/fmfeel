package device;

import com.fazecast.jSerialComm.SerialPort;

import jsm.EventDispatcher;

public class Context {
	public EventDispatcher eventDispatcher;
	public SerialPort[] serialPorts;
	public int probingIndex;
	public SerialLink serialLink;
}
