package fmfeel;


import device.DeviceController;
import jsm.EventDispatcher;
import jsm.StateMachineRunner;

public class Context {
	public EventDispatcher eventDispatcher;
	public StateMachineRunner runner;
	public DeviceController deviceController;
	public Thread clockThread;
	public RadioGUIController guiController;
	public Tuner tuner;
	public Volume volume;
	public Persistency persistency;
	public RadioPlayer radioPlayer;

}
