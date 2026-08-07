package device;

import java.util.function.Consumer;

import jsm.Logger;
import jsm.State;
import jsm.StateMachine;
import jsm.StateMachineRunnable;
import jsm.StateMachineRunner;

// states : searching, talking
public class DeviceController
{
	public static final Logger logger = message -> System.out.println("[device] " + message);


	private final Context context = new Context();
	
	private String[] display_line = {"",""};
	

	final StateMachineRunner runner;

	public DeviceController()
	{
		final State enumerating = new EnumeratingState(context);
		final State probingnext = new ProbingNextState(context);
		final State talking = new TalkingState(context);
		
		final StateMachine machine = new StateMachine(enumerating, logger);
		
		final StateMachineRunnable runnable;

		machine.addTransition(enumerating, "enumeration_done", probingnext);
		machine.addTransition(enumerating, "no_more_devices", enumerating);
		machine.addTransition(probingnext, "device_recognized", talking);
		machine.addTransition(probingnext, "device_unrecognized", probingnext);
		machine.addTransition(probingnext, "no_more_devices", enumerating);
		machine.addTransition(talking, "connection_error", enumerating);
		machine.addTransition(probingnext, "connection_error", probingnext);
		machine.addTransition(enumerating, "connection_error", enumerating);
		machine.validate();
		
		runnable = new StateMachineRunnable(machine, true, logger);
		runner = new StateMachineRunner(runnable,"device-runner");
		
		context.eventDispatcher = runner;


	}
	
	public void start()
	{
		runner.start();
	}
	
	public void stop()
	{
		runner.stop();
	}
	
	public void join() throws InterruptedException
	{
		runner.join();
	}
	
	public void send(String message)
	{
		context.serialLink.send(message);
	}

	public void setOnDeviceConnected(Runnable onDeviceConnectedRunnable)
	{
		context.onDeviceConnectedRunnable = onDeviceConnectedRunnable;
	}
	
	public void setOnDeviceDisconnected(Runnable onDeviceDisconnectedRunnable)
	{
		context.onDeviceDisconnectedRunnable = onDeviceDisconnectedRunnable;
	}
	
	public void setOnMessage(Consumer<String> onMessageConsumer)
	{
		context.onMessageConsumer = onMessageConsumer;
	}
	
	public void display(int line, String text)
	{
		if (line != 0 && line != 1) return;

		if (text.compareTo(display_line[line]) != 0)
		{
			display_line[line] = text;
			send("SHOW " + line + " " + text);
		}
	}
}
