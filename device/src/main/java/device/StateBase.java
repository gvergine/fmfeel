package device;

import jsm.Logger;
import jsm.State;

public abstract class StateBase implements State
{
	protected final Logger logger;
	protected final Context context;
	private final String name;
	
	public StateBase(String name, Context context)
	{
		this.name = name;
		this.context = context;
		this.logger = message -> System.out.println("[device state: " + name + "] " + message);
	}
	
	@Override
	public String getName() {
		return name;
	}

}
