package jsm;

public record Transition(State from, String eventName, State to) {}