package jsm;

import java.util.Queue;

public interface EventDispatcher {
	Queue<Event> getEventQueue();
}
