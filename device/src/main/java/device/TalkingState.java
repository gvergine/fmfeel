package device;

import com.fazecast.jSerialComm.SerialPort;

import jsm.Event;

public class TalkingState extends StateBase
{

	public TalkingState(Context context) {
		super("talking", context);
	}

	@Override
	public void onEnter()
	{
		SerialPort p = context.serialPorts[context.probingIndex];
		
		context.serialLink = new SerialLink(p);
		
		context.serialLink.start();
		

	}

	@Override
	public void onExit()
	{
		context.serialLink.stop();

	}

	@Override
	public void onEvent(Event event)
	{
		// TODO Auto-generated method stub	
	}

}
