package device;

import com.fazecast.jSerialComm.SerialPort;

import jsm.Event;
import jsm.Logger;

public class EnumeratingState extends StateBase
{
	
	public EnumeratingState(Context context) {
		super("enumerating", context);
	}

	@Override
	public void onEnter()
	{
		
		try
		{
			logger.log("begin sleeping");
			Thread.sleep(1000);
		}
		catch (InterruptedException e)
		{
			logger.log(Logger.stackTraceToString(e));
		}
		finally
		{
		    logger.log("end sleeping");
		}
		
		logger.log("begin enumerating");

		context.probingIndex = 0;
		
		context.serialPorts = SerialPort.getCommPorts();
		
		for (SerialPort p : context.serialPorts)
		{
			logger.log("found port " + p.getDescriptivePortName());
		}
		
		logger.log("end enumerating");
		
		if (context.serialPorts.length > 0)
		{
			context.eventDispatcher.getEventQueue().offer(Event.build("enumeration_done"));
		}
		else
		{
			context.eventDispatcher.getEventQueue().offer(Event.build("no_more_devices"));
		}
	}

	@Override
	public void onExit()
	{
		
	}

	@Override
	public void onEvent(Event event)
	{
		// TODO Auto-generated method stub	
	}

}
