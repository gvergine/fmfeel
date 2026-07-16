package fmfeel;

import jsm.Event;
import jsm.Logger;
import jsm.State;
import jsm.StateMachine;
import jsm.StateMachineRunnable;
import jsm.StateMachineRunner;


public class Main
{
	public static final Logger logger = message -> System.out.println("[fmfeel] " + message);
	
	private static StateMachineRunner buildMachine()
	{
		State locked = State.build("locked", logger);
		State unlocked = State.build("unlocked", logger);

		StateMachine machine = new StateMachine(locked, logger);
		machine.addTransition(locked, "coin", unlocked);
		machine.addTransition(locked, "push", null);      // no-op while locked
		machine.addTransition(unlocked, "push", locked);
		machine.addTransition(unlocked, "coin", null);    // no-op while unlocked
		machine.validate();
		StateMachineRunnable runnable = new StateMachineRunnable(machine, true, logger);
		StateMachineRunner runner = new StateMachineRunner(runnable,"fmfeel-runner");
		return runner;
	}

	
	public static void main(String[] args) throws InterruptedException
	{


		StateMachineRunner runner = buildMachine();

		runner.start();
		
		// add event with: runner.getEventQueue().add(Event.build(name));

		runner.stop();
		runner.join();

		logger.log("final state: " + runner.getStateMachine().getCurrentState().getName());
	}
}
