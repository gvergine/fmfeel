package jsm;

public interface State 
{
	String getName();
	void onEnter();
	void onExit();
	void onEvent(Event event);
	
	public static State build(String name, Logger logger)
	{
		return new State() {
			
			@Override
			public void onExit()
			{
				logger.log(name + "::onExit");
			}
			
			@Override
			public void onEvent(Event event)
			{
				logger.log(name + "::onEvent(" + event.getName() + ")");
			}
			
			@Override
			public void onEnter()
			{
				logger.log(name + "::onEnter");
			}
			
			@Override
			public String getName()
			{
				return name;
			}
		};
	}

}
