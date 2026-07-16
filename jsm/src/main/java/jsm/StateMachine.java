package jsm;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class StateMachine
{
	private record Key(State from, String eventName) {}

	private final State initialState;
	private final Logger logger;
	private final Map<Key, State> transitions = new HashMap<>();
	volatile private State currentState;

	public StateMachine(State initialState, Logger logger)
	{
		this.initialState = Objects.requireNonNull(initialState, "initialState");
		this.logger = Objects.requireNonNull(logger, "logger");
	}

	public State getCurrentState()
	{
		return currentState;
	}

	/**
	 * Adds a transition. A null nextState means the event is handled in
	 * currentState without leaving it (no exit/enter callbacks).
	 */
	public void addTransition(State currentState, String eventName, State nextState)
	{
		Key key = new Key(
				Objects.requireNonNull(currentState, "currentState"),
				Objects.requireNonNull(eventName, "eventName"));

		if (transitions.containsKey(key))
		{
			throw new IllegalStateException(
					"Duplicate transition for state " + currentState.getName() + " on event " + eventName);
		}
		transitions.put(key, nextState);
	}

	// Run the SM

	@FunctionalInterface
	private interface ThrowingRunnable
	{
		void run() throws Exception;
	}

	private void runSafely(ThrowingRunnable action)
	{
		try
		{
			action.run();
		}
		catch (Exception e)
		{
			logger.log(Logger.stackTraceToString(e));
		}
	}

	private void enterCurrentState()
	{
		logger.log("Entering state " + currentState.getName());
		runSafely(currentState::onEnter);
		logger.log("Entered state " + currentState.getName());
	}

	private void exitCurrentState()
	{
		logger.log("Exiting state " + currentState.getName());
		runSafely(currentState::onExit);
		logger.log("Exited state " + currentState.getName());
	}

	public void start(boolean enterState)
	{
		this.currentState = initialState;
		if (enterState)
		{
			enterCurrentState();
		}
		else
		{
			logger.log("Entered state " + currentState.getName());
		}
	}

	public void dispatchEvent(Event event)
	{
		if (event == null)
		{
			throw new IllegalArgumentException("Event can't be null");
		}
		
		if (currentState == null)
		{
			throw new IllegalStateException("State machine not started - call start() first");
		}

		logger.log("Processing event " + event.getName() + " in state " + currentState.getName());

		Key key = new Key(currentState, event.getName());
		if (!transitions.containsKey(key))
		{
			logger.log("Ignoring event " + event.getName() + " for state " + currentState.getName());
			return;
		}

		runSafely(() -> currentState.onEvent(event));

		State nextState = transitions.get(key);
		if (nextState != null)
		{
			exitCurrentState();
			this.currentState = nextState;
			enterCurrentState();
		}
	}

	// Validate the SM

	public void validate()
	{
		validateReachableStates();
	}

	private void validateReachableStates()
	{
		Map<State, List<State>> graph = transitions.entrySet().stream()
				.filter(e -> e.getValue() != null)
				.collect(Collectors.groupingBy(
						e -> e.getKey().from(),
						Collectors.mapping(Map.Entry::getValue, Collectors.toList())));

		Set<State> reachable = new HashSet<>();
		Deque<State> queue = new ArrayDeque<>();
		queue.add(initialState);

		while (!queue.isEmpty())
		{
			State current = queue.removeFirst();
			if (reachable.add(current))
			{
				queue.addAll(graph.getOrDefault(current, List.of()));
			}
		}

		Set<State> unreachable = new HashSet<>();
		for (Map.Entry<Key, State> entry : transitions.entrySet())
		{
			unreachable.add(entry.getKey().from());
			if (entry.getValue() != null)
			{
				unreachable.add(entry.getValue());
			}
		}
		unreachable.removeAll(reachable);

		if (!unreachable.isEmpty())
		{
			throw new IllegalStateException("Unreachable states: " + unreachable);
		}
	}
}