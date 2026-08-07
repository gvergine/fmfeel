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
		context.serialLink.setOnStop(t -> {
			context.eventDispatcher.getEventQueue().offer(Event.build("connection_error"));
		});
		
		context.serialLink.removeListener(context.onMessageConsumer);
		context.serialLink.addListener(context.onMessageConsumer);
		
		context.serialLink.start();
		
		context.onDeviceConnectedRunnable.run();
		

	}

	@Override
	public void onExit()
	{
		context.serialLink.removeListener(context.onMessageConsumer);
		context.serialLink.stop();
		context.onDeviceDisconnectedRunnable.run();

	}

	@Override
	public void onEvent(Event event)
	{
		// TODO Auto-generated method stub	
	}

}
