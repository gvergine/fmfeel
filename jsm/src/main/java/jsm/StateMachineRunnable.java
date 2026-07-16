package jsm;

import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class StateMachineRunnable implements Runnable
{
	private final BlockingQueue<Event> eventQueue = new LinkedBlockingQueue<>();
	private final boolean enterState;
	private final StateMachine stateMachine;
	private final Logger logger;
	private final Event exitEvent;

	public StateMachineRunnable(StateMachine stateMachine, boolean enterState, Logger logger)
	{
		this.stateMachine = Objects.requireNonNull(stateMachine, "stateMachine");
		this.enterState = enterState;
		this.exitEvent = Event.build("__JSM_INTERNAL_EXIT_EVENT");
		this.logger = Objects.requireNonNull(logger, "logger");
	}

	public Queue<Event> getEventQueue()
	{
		return eventQueue;
	}
	
	public StateMachine getStateMachine()
	{
		return stateMachine;
	}

	public void stop()
	{
		eventQueue.clear();
		eventQueue.offer(exitEvent);
	}

	@Override
	public void run()
	{
		stateMachine.start(enterState);

		while (true)
		{
			Event event;
			try
			{
				event = eventQueue.take();
			}
			catch (InterruptedException e)
			{
				logger.log(Logger.stackTraceToString(e));
				Thread.currentThread().interrupt(); // preserve interrupt status for callers
				break;                              // an interrupt is a stop signal
			}

			if (event == exitEvent) break;
			stateMachine.dispatchEvent(event);
		}
	}

}