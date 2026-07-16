package jsm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StateMachineTest
{
	// ------------------------------------------------------------------
	// Fixtures: generic states A-D that record every callback, a logger
	// that records every line, and small reusable topologies.
	// ------------------------------------------------------------------

	/** Every line the machine logs, in order. */
	private final List<String> log = new ArrayList<>();

	/** Every state callback, in order: "enter:A", "event:A:go", "exit:A". */
	private final List<String> trace = new ArrayList<>();

	/**
	 * Generic test state that records its callbacks and can be told to
	 * fail in any of them. Assumes State is an interface; if it is a
	 * class in your codebase, change 'implements' to 'extends'.
	 */
	private class TraceState implements State
	{
		private final String name;
		private RuntimeException enterFailure, exitFailure, eventFailure;

		TraceState(String name) { this.name = name; }

		TraceState failingOnEnter() { enterFailure = new RuntimeException("boom-enter-" + name); return this; }
		TraceState failingOnExit()  { exitFailure  = new RuntimeException("boom-exit-"  + name); return this; }
		TraceState failingOnEvent() { eventFailure = new RuntimeException("boom-event-" + name); return this; }

		@Override public String getName() { return name; }

		@Override public void onEnter()
		{
			trace.add("enter:" + name);
			if (enterFailure != null) throw enterFailure;
		}

		@Override public void onExit()
		{
			trace.add("exit:" + name);
			if (exitFailure != null) throw exitFailure;
		}

		@Override public void onEvent(Event event)
		{
			trace.add("event:" + name + ":" + event.getName());
			if (eventFailure != null) throw eventFailure;
		}

		@Override public String toString() { return name; }
	}

	private TraceState a, b, c, d;

	@BeforeEach
	void freshStates()
	{
		a = new TraceState("A");
		b = new TraceState("B");
		c = new TraceState("C");
		d = new TraceState("D");
	}

	private StateMachine machine(State initial)
	{
		return new StateMachine(initial, log::add);
	}

	private static Event event(String name)
	{
		return Event.build(name);
	}

	// --- topologies ---------------------------------------------------

	/** A -go-> B -go-> C */
	private StateMachine linear()
	{
		StateMachine sm = machine(a);
		sm.addTransition(a, "go", b);
		sm.addTransition(b, "go", c);
		return sm;
	}

	/** A -fwd-> B -fwd-> C -fwd-> A */
	private StateMachine ring()
	{
		StateMachine sm = machine(a);
		sm.addTransition(a, "fwd", b);
		sm.addTransition(b, "fwd", c);
		sm.addTransition(c, "fwd", a);
		return sm;
	}

	/** A <-toggle-> B */
	private StateMachine toggle()
	{
		StateMachine sm = machine(a);
		sm.addTransition(a, "toggle", b);
		sm.addTransition(b, "toggle", a);
		return sm;
	}

	/** A -l-> B -j-> D and A -r-> C -j-> D */
	private StateMachine diamond()
	{
		StateMachine sm = machine(a);
		sm.addTransition(a, "l", b);
		sm.addTransition(a, "r", c);
		sm.addTransition(b, "j", d);
		sm.addTransition(c, "j", d);
		return sm;
	}

	// ------------------------------------------------------------------

	@Nested @DisplayName("Lifecycle")
	class Lifecycle
	{
		@Test void currentStateIsNullBeforeStart()
		{
			assertNull(linear().getCurrentState());
		}

		@Test void dispatchingBeforeStartThrows()
		{
			StateMachine sm = linear();
			assertThrows(IllegalStateException.class, () -> sm.dispatchEvent(event("go")));
		}

		@Test void startWithoutEnteringSetsStateButSkipsOnEnter()
		{
			StateMachine sm = linear();
			sm.start(false);
			assertSame(a, sm.getCurrentState());
			assertTrue(trace.isEmpty());
		}

		@Test void startWithEnteringCallsOnEnter()
		{
			StateMachine sm = linear();
			sm.start(true);
			assertSame(a, sm.getCurrentState());
			assertEquals(List.of("enter:A"), trace);
		}

		@Test void restartResetsToInitialWithoutExitingCurrentState()
		{
			StateMachine sm = linear();
			sm.start(false);
			sm.dispatchEvent(event("go"));          // now in B
			sm.start(false);
			assertSame(a, sm.getCurrentState());
			// documents current semantics: restarting does not call onExit of B
			assertFalse(trace.contains("exit:B"));
		}

		@Test void nullInitialStateIsRejected()
		{
			assertThrows(NullPointerException.class, () -> new StateMachine(null, log::add));
		}

		@Test void nullLoggerIsRejected()
		{
			assertThrows(NullPointerException.class, () -> new StateMachine(a, null));
		}
	}

	@Nested @DisplayName("Transitions")
	class Transitions
	{
		@Test void eventMovesMachineToTargetState()
		{
			StateMachine sm = linear();
			sm.start(false);
			sm.dispatchEvent(event("go"));
			assertSame(b, sm.getCurrentState());
		}

		@Test void callbacksFireInOrderEventExitEnter()
		{
			StateMachine sm = linear();
			sm.start(false);
			sm.dispatchEvent(event("go"));
			assertEquals(List.of("event:A:go", "exit:A", "enter:B"), trace);
		}

		@Test void chainedTransitionsWalkTheWholeTopology()
		{
			StateMachine sm = linear();
			sm.start(true);
			sm.dispatchEvent(event("go"));
			sm.dispatchEvent(event("go"));
			assertSame(c, sm.getCurrentState());
			assertEquals(List.of(
					"enter:A",
					"event:A:go", "exit:A", "enter:B",
					"event:B:go", "exit:B", "enter:C"), trace);
		}

		@Test void ringTopologyReturnsToInitialState()
		{
			StateMachine sm = ring();
			sm.start(false);
			sm.dispatchEvent(event("fwd"));
			sm.dispatchEvent(event("fwd"));
			sm.dispatchEvent(event("fwd"));
			assertSame(a, sm.getCurrentState());
		}

		@Test void toggleTopologySurvivesManyRoundTrips()
		{
			StateMachine sm = toggle();
			sm.start(false);
			for (int i = 0; i < 101; i++)
			{
				sm.dispatchEvent(event("toggle"));
			}
			assertSame(b, sm.getCurrentState());    // odd number of toggles
		}

		@Test void diamondTopologyReachesJoinFromEitherBranch()
		{
			StateMachine sm = diamond();
			sm.start(false);
			sm.dispatchEvent(event("r"));
			sm.dispatchEvent(event("j"));
			assertSame(d, sm.getCurrentState());
		}

		@Test void unknownEventIsIgnoredWithoutCallbacks()
		{
			StateMachine sm = linear();
			sm.start(false);
			sm.dispatchEvent(event("does-not-exist"));
			assertSame(a, sm.getCurrentState());
			assertTrue(trace.isEmpty());            // not even onEvent fires
			assertTrue(log.stream().anyMatch(l -> l.contains("Ignoring event does-not-exist")));
		}

		@Test void eventBelongingToAnotherStateIsIgnored()
		{
			StateMachine sm = machine(a);
			sm.addTransition(a, "go", b);
			sm.addTransition(b, "back", a);
			sm.start(false);
			sm.dispatchEvent(event("back"));        // only valid in B
			assertSame(a, sm.getCurrentState());
			assertTrue(trace.isEmpty());
		}

		@Test void sinkStateIgnoresEverything()
		{
			StateMachine sm = linear();
			sm.start(false);
			sm.dispatchEvent(event("go"));
			sm.dispatchEvent(event("go"));          // in C, no outgoing transitions
			trace.clear();
			sm.dispatchEvent(event("go"));
			assertSame(c, sm.getCurrentState());
			assertTrue(trace.isEmpty());
		}

		@Test void machinesSharingStatesStayIndependent()
		{
			StateMachine first = machine(a);
			StateMachine second = machine(a);
			first.addTransition(a, "go", b);
			second.addTransition(a, "go", c);
			first.start(false);
			second.start(false);

			first.dispatchEvent(event("go"));

			assertSame(b, first.getCurrentState());
			assertSame(a, second.getCurrentState());
		}
	}

	@Nested @DisplayName("Internal and self transitions")
	class InternalAndSelfTransitions
	{
		@Test void nullTargetHandlesEventWithoutLeavingTheState()
		{
			StateMachine sm = machine(a);
			sm.addTransition(a, "tick", null);
			sm.start(false);
			sm.dispatchEvent(event("tick"));
			assertSame(a, sm.getCurrentState());
			assertEquals(List.of("event:A:tick"), trace);   // no exit, no enter
		}

		@Test void internalTransitionCanFireRepeatedly()
		{
			StateMachine sm = machine(a);
			sm.addTransition(a, "tick", null);
			sm.start(false);
			sm.dispatchEvent(event("tick"));
			sm.dispatchEvent(event("tick"));
			sm.dispatchEvent(event("tick"));
			assertEquals(List.of("event:A:tick", "event:A:tick", "event:A:tick"), trace);
		}

		@Test void explicitSelfTransitionExitsAndReenters()
		{
			StateMachine sm = machine(a);
			sm.addTransition(a, "reset", a);
			sm.start(false);
			sm.dispatchEvent(event("reset"));
			assertSame(a, sm.getCurrentState());
			assertEquals(List.of("event:A:reset", "exit:A", "enter:A"), trace);
		}
	}

	@Nested @DisplayName("Registering transitions")
	class RegisteringTransitions
	{
		@Test void duplicateStateEventPairIsRejected()
		{
			StateMachine sm = machine(a);
			sm.addTransition(a, "go", b);
			IllegalStateException ex = assertThrows(IllegalStateException.class,
					() -> sm.addTransition(a, "go", c));
			assertTrue(ex.getMessage().contains("Duplicate"));
		}

		@Test void duplicateIsRejectedEvenWithTheSameTarget()
		{
			StateMachine sm = machine(a);
			sm.addTransition(a, "go", b);
			assertThrows(IllegalStateException.class, () -> sm.addTransition(a, "go", b));
		}

		@Test void sameEventNameFromDifferentStatesIsAllowed()
		{
			StateMachine sm = machine(a);
			sm.addTransition(a, "go", b);
			assertDoesNotThrow(() -> sm.addTransition(b, "go", c));
		}

		@Test void differentEventsFromTheSameStateAreAllowed()
		{
			StateMachine sm = machine(a);
			sm.addTransition(a, "left", b);
			assertDoesNotThrow(() -> sm.addTransition(a, "right", c));
		}

		@Test void nullSourceStateIsRejected()
		{
			assertThrows(NullPointerException.class, () -> machine(a).addTransition(null, "go", b));
		}

		@Test void nullEventNameIsRejected()
		{
			assertThrows(NullPointerException.class, () -> machine(a).addTransition(a, null, b));
		}
	}

	@Nested @DisplayName("Event matching")
	class EventMatching
	{
		@Test void eventsMatchByNameNotByInstance()
		{
			StateMachine sm = toggle();
			sm.start(false);
			sm.dispatchEvent(Event.build("toggle"));
			sm.dispatchEvent(Event.build("toggle"));   // a different instance each time
			assertSame(a, sm.getCurrentState());
		}

		@Test void runtimeBuiltEventNameMatches()
		{
			// Regression test: matching must use equals(), not ==. A name built
			// at runtime is not the same String instance as the registered literal.
			StateMachine sm = linear();
			sm.start(false);
			String name = new StringBuilder("g").append("o").toString();
			sm.dispatchEvent(event(name));
			assertSame(b, sm.getCurrentState());
		}

		@Test void eventNamesAreCaseSensitive()
		{
			StateMachine sm = linear();
			sm.start(false);
			sm.dispatchEvent(event("GO"));
			assertSame(a, sm.getCurrentState());
		}
	}

	@Nested @DisplayName("Validation")
	class Validation
	{
		@Test void machineWithNoTransitionsIsValid()
		{
			assertDoesNotThrow(() -> machine(a).validate());
		}

		@Test void wellFormedTopologiesAreValid()
		{
			assertDoesNotThrow(() -> linear().validate(), "linear");
			assertDoesNotThrow(() -> ring().validate(), "ring");
			assertDoesNotThrow(() -> diamond().validate(), "diamond");
		}

		@Test void sinkStateIsValid()
		{
			StateMachine sm = machine(a);
			sm.addTransition(a, "go", b);           // B has no way out
			assertDoesNotThrow(sm::validate);
		}

		@Test void internalTransitionsDoNotBreakValidation()
		{
			StateMachine sm = linear();
			sm.addTransition(a, "tick", null);
			sm.addTransition(b, "tick", null);
			assertDoesNotThrow(sm::validate);
		}

		@Test void unreachableSourceStateIsDetected()
		{
			StateMachine sm = machine(a);
			sm.addTransition(a, "go", b);
			sm.addTransition(c, "x", a);            // nothing ever leads to C
			IllegalStateException ex = assertThrows(IllegalStateException.class, sm::validate);
			assertTrue(ex.getMessage().contains("Unreachable"));
			assertTrue(ex.getMessage().contains("C"));
		}

		@Test void disconnectedIslandIsDetected()
		{
			StateMachine sm = machine(a);
			sm.addTransition(a, "go", b);
			sm.addTransition(c, "x", d);            // C <-> D island
			sm.addTransition(d, "y", c);
			assertThrows(IllegalStateException.class, sm::validate);
		}

		@Test void stateWithOnlyInternalTransitionsMustStillBeReachable()
		{
			StateMachine sm = machine(a);
			sm.addTransition(a, "go", b);
			sm.addTransition(c, "tick", null);      // C handles an event but is unreachable
			assertThrows(IllegalStateException.class, sm::validate);
		}
	}

	@Nested @DisplayName("Callback failures")
	class CallbackFailures
	{
		@Test void exceptionInOnEventDoesNotStopTheTransition()
		{
			a.failingOnEvent();
			StateMachine sm = machine(a);
			sm.addTransition(a, "go", b);
			sm.start(false);
			assertDoesNotThrow(() -> sm.dispatchEvent(event("go")));
			assertSame(b, sm.getCurrentState());
			assertEquals(List.of("event:A:go", "exit:A", "enter:B"), trace);
		}

		@Test void exceptionInOnExitDoesNotStopTheTransition()
		{
			a.failingOnExit();
			StateMachine sm = machine(a);
			sm.addTransition(a, "go", b);
			sm.start(false);
			sm.dispatchEvent(event("go"));
			assertSame(b, sm.getCurrentState());
		}

		@Test void exceptionInOnEnterLeavesMachineInNewStateAndUsable()
		{
			b.failingOnEnter();
			StateMachine sm = machine(a);
			sm.addTransition(a, "go", b);
			sm.addTransition(b, "back", a);
			sm.start(false);
			sm.dispatchEvent(event("go"));
			assertSame(b, sm.getCurrentState());

			sm.dispatchEvent(event("back"));        // machine keeps working afterwards
			assertSame(a, sm.getCurrentState());
		}

		@Test void startSurvivesThrowingOnEnter()
		{
			a.failingOnEnter();
			StateMachine sm = machine(a);
			sm.addTransition(a, "go", b);
			assertDoesNotThrow(() -> sm.start(true));
			assertSame(a, sm.getCurrentState());
		}

		@Test void callbackFailuresAreLogged()
		{
			a.failingOnEvent();
			StateMachine sm = machine(a);
			sm.addTransition(a, "go", b);
			sm.start(false);
			sm.dispatchEvent(event("go"));
			assertTrue(log.stream().anyMatch(l -> l.contains("boom-event-A")));
		}
	}
}

/*package jsm;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StateMachineTest 
{
    @Test void basicTest()
    {
        State initState = State.build("INIT",System.out::println);
        State shutdownState = State.build("SHUTDOWN",System.out::println);
        State standbyState = State.build("STANDBY",System.out::println);
        State playState = State.build("PLAY",System.out::println);
        
        Event deviceConnectedEvent = Event.build("DEVICE_CONNECTED");        
        Event deviceDisconnectedEvent = Event.build("DEVICE_DISCONNECTED");        
        Event timeoutEvent = Event.build("TIMEOUT");        
        Event powerToggleEvent = Event.build("POWER_TOGGLE");
        Event muteToggleEvent = Event.build("MUTE_TOGGLE");
        Event volumeDownEvent = Event.build("VOLUME_DOWN");
        Event volumeUpEvent = Event.build("VOLUME_UP");
        Event tuneDownEvent = Event.build("TUNE_DOWN");
        Event tuneUpEvent = Event.build("TUNE_UP");
        Event shutdownEvent = Event.build("SHUTDOWN");
        
        StateMachine sm = new StateMachine(initState,System.out::println);

        sm.addTransition(initState, "SHUTDOWN", shutdownState);
        sm.addTransition(standbyState, "SHUTDOWN", shutdownState);
        sm.addTransition(playState, "SHUTDOWN", shutdownState);
        sm.addTransition(initState, "TIMEOUT", null);
        sm.addTransition(standbyState, "TIMEOUT", null);
        sm.addTransition(playState, "TIMEOUT", null);
        sm.addTransition(shutdownState, "TIMEOUT", null);
        sm.addTransition(standbyState, "DEVICE_DISCONNECTED", initState);
        sm.addTransition(playState, "DEVICE_DISCONNECTED", initState);
        sm.addTransition(initState, "DEVICE_CONNECTED", standbyState);
        sm.addTransition(standbyState, "POWER_TOGGLE", playState);
        sm.addTransition(playState, "POWER_TOGGLE", standbyState);
        
        sm.addTransition(playState, "MUTE_TOGGLE", null);
        sm.addTransition(playState, "VOLUME_DOWN", null);
        sm.addTransition(playState, "VOLUME_UP", null);
        sm.addTransition(playState, "TUNE_DOWN", null);
        sm.addTransition(playState, "TUNE_UP", null);
        
        assertDoesNotThrow(() -> sm.validate());
        assertDoesNotThrow(() -> sm.start(false));
        assertTrue(() -> sm.getCurrentState() == initState);
        assertDoesNotThrow(() -> sm.dispatchEvent(deviceConnectedEvent));
        assertDoesNotThrow(() -> sm.dispatchEvent(deviceConnectedEvent));
        assertTrue(() -> sm.getCurrentState() == standbyState);
        assertDoesNotThrow(() -> sm.dispatchEvent(powerToggleEvent));
        assertTrue(() -> sm.getCurrentState() == playState);
        assertDoesNotThrow(() -> sm.dispatchEvent(tuneDownEvent));
        assertTrue(() -> sm.getCurrentState() == playState);
        assertDoesNotThrow(() -> sm.dispatchEvent(powerToggleEvent));
        assertTrue(() -> sm.getCurrentState() == standbyState);
        assertDoesNotThrow(() -> sm.dispatchEvent(shutdownEvent));
        assertTrue(() -> sm.getCurrentState() == shutdownState);
        
        
    }
}

*/
