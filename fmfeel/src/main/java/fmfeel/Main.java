package fmfeel;

import device.DeviceController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
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
		
		final State initialState = State.build("initial", logger);
		final State findingDeviceState = State.build("findingDevice", logger);
		final State runningState = State.build("running", logger);
		final State shuttingDownState = State.build("shuttingDown", logger);
		
		final StateMachine machine = new StateMachine(initialState, logger);

		machine.addTransition(initialState, "gui_start", findingDeviceState);
		machine.addTransition(findingDeviceState, "device_found", runningState);
		machine.addTransition(runningState, "device_disconnected", findingDeviceState);
		machine.addTransition(runningState, "shutdown_request", shuttingDownState);
		machine.addTransition(findingDeviceState, "shutdown_request", shuttingDownState);

		
		machine.validate();
		
		final StateMachineRunnable runnable = new StateMachineRunnable(machine, true, logger);
		final StateMachineRunner runner = new StateMachineRunner(runnable,"fmfeel-runner");
		
		runner.start();
		
		
        Platform.startup(() -> {
            // runs on the JavaFX Application Thread
            try {
                Stage stage = new Stage();
                stage.setOnHidden(e -> runner.stop());

				new HelloApp(runner).start(stage);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        });
		
		runner.join();

	}
}
