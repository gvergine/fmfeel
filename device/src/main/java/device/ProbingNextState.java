package device;

import java.io.IOException;
import java.util.Arrays;

import com.fazecast.jSerialComm.SerialPort;

import device.SerialLink.ProbeResult;
import jsm.Event;
import jsm.Logger;

public class ProbingNextState extends StateBase
{

	public ProbingNextState(Context context) {
		super("probingnext", context);
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

		if (context.serialPorts.length - context.probingIndex < 1)
		{
			context.eventDispatcher.getEventQueue().offer(Event.build("no_more_devices"));
			return;
		}

		SerialPort p = context.serialPorts[context.probingIndex];
		logger.log("probing [" + (context.probingIndex + 1) + "/" + context.serialPorts.length + "]: " + p.getDescriptivePortName());


		ProbeResult pr = SerialLink.probe(p, 115200, Arrays.asList("HB"), 2000);

		if (pr == SerialLink.ProbeResult.GOOD)
		{
			context.eventDispatcher.getEventQueue().offer(Event.build("device_recognized"));
		}
		else
		{
			context.probingIndex++;
			context.eventDispatcher.getEventQueue().offer(Event.build("connection_error"));
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
