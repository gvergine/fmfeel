package jsm;

import java.util.Queue;

public class StateMachineRunner
{
	private final StateMachineRunnable runnable;
	private final Thread thread;

	public StateMachineRunner(StateMachineRunnable runnable, String name)
	{
		this.runnable = runnable;
		this.thread = new Thread(runnable, "name");
	}
	
	public void start()
	{
		thread.start();
	}
	
	public void stop()
	{
		runnable.stop();
	}
	
	public void join() throws InterruptedException
	{
		thread.join();
	}
	
	public Queue<Event> getEventQueue()
	{
		return runnable.getEventQueue();
	}

	public StateMachine getStateMachine()
	{
		return runnable.getStateMachine();
	}


}
