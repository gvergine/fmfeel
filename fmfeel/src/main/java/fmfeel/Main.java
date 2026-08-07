package fmfeel;

import device.DeviceController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import jsm.Event;
import jsm.Logger;
import jsm.State;
import jsm.StateMachine;
import jsm.StateMachineRunnable;
import jsm.StateMachineRunner;

/**
 * Plain (non-{@link Application}) entry point. Launching a class that extends
 * {@code Application} directly can trigger the "JavaFX runtime components are
 * missing" error when JavaFX is on the classpath rather than the module path,
 * so we delegate to {@link HelloApp} from here.
 */
public class Main
{
	public static final Logger logger = message -> System.out.println("[fmfeel] " + message);

	public static void main(String[] args) throws InterruptedException
	{
		final Context context = new Context();
		final State initialState = State.build("initial", logger);
		final State findingDeviceState = new FindingDeviceState(context);
		final State standbyState = new StandbyState(context);
		final State runningState = new RunningState(context);
		final State shuttingDownState = new ShuttingDownState(context);
		
		final StateMachine machine = new StateMachine(initialState, logger);

		machine.addTransition(initialState, "gui_start", findingDeviceState);
		machine.addTransition(findingDeviceState, "device_connected", standbyState);
		machine.addTransition(findingDeviceState, "shutdown_request", shuttingDownState);
		machine.addTransition(standbyState, "toggle_power", runningState);
		machine.addTransition(standbyState, "device_disconnected", findingDeviceState);
		machine.addTransition(standbyState, "shutdown_request", shuttingDownState);
		machine.addTransition(runningState, "toggle_power", standbyState);
		machine.addTransition(runningState, "tune_up", null);
		machine.addTransition(runningState, "tune_down", null);
		machine.addTransition(runningState, "volume_up", null);
		machine.addTransition(runningState, "volume_down", null);
		machine.addTransition(runningState, "device_disconnected", findingDeviceState);
		machine.addTransition(runningState, "shutdown_request", shuttingDownState);

		
		machine.validate();
		
		final StateMachineRunnable runnable = new StateMachineRunnable(machine, true, logger);
		final StateMachineRunner runner = new StateMachineRunner(runnable,"fmfeel-runner");
		context.eventDispatcher = runner;
		context.runner = runner;
		context.tuner = new Tuner();
		context.volume = new Volume();
		context.persistency = new Persistency();
		context.deviceController = new DeviceController();
		context.deviceController.setOnDeviceConnected(() -> {
			context.eventDispatcher.getEventQueue().offer(Event.build("device_connected"));
		});
		context.deviceController.setOnDeviceDisconnected(() -> {
			context.eventDispatcher.getEventQueue().offer(Event.build("device_disconnected"));
		});
		context.deviceController.setOnMessage(message -> {
			if (message.compareTo("HB") == 0) return;
			if (message.compareTo("ENCODER LEFT CW") == 0) {
				context.eventDispatcher.getEventQueue().offer(Event.build("tune_up"));
				return;
			};
			if (message.compareTo("ENCODER LEFT CCW") == 0) {
				context.eventDispatcher.getEventQueue().offer(Event.build("tune_down"));
				return;
			};
			if (message.compareTo("LEFT BUTTON DOWN") == 0) {
				context.eventDispatcher.getEventQueue().offer(Event.build("toggle_power"));
				return;
			};
			if (message.compareTo("LEFT BUTTON UP") == 0) return;
			if (message.compareTo("ENCODER RIGHT CW") == 0) {
				context.eventDispatcher.getEventQueue().offer(Event.build("volume_up"));
				return;
			};
			if (message.compareTo("ENCODER RIGHT CCW") == 0) {
				context.eventDispatcher.getEventQueue().offer(Event.build("volume_down"));
				return;
			};
			if (message.compareTo("RIGHT BUTTON DOWN") == 0) return;
			if (message.compareTo("RIGHT BUTTON UP") == 0) return;
			
			logger.log("unkown message from device: " + message);
			
		});
		
		runner.start();
		
		
        Platform.startup(() -> {
            // runs on the JavaFX Application Thread
            try {
                Stage stage = new Stage();
                stage.setOnHidden(e -> {
            		context.eventDispatcher.getEventQueue().offer(Event.build("shutdown_request"));
                });

				new HelloApp(context).start(stage); 
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        });
		
		runner.join();

	}
}
