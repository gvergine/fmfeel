package jsm;

public interface Event
{
	public String getName();
	
	public static Event build(String name)
	{
		return new Event() {			
			@Override
			public String getName()
			{
				return name;
			}
		};
	}
}
