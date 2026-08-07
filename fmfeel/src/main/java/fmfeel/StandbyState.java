package fmfeel;


import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import device.DisplayHelper;
import javafx.application.Platform;
import jsm.Event;

public class StandbyState extends StateBase
{
	private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");
	
	public StandbyState(Context context)
	{
		super("standby", context);
	}

	@Override
	public void onEnter()
	{
		Platform.runLater(() -> {
			context.guiController.display("STANDBY");
		});
		context.clockThread = new Thread(() -> {
			while (true)
			{
				try
				{
					LocalDateTime now = LocalDateTime.now();
					context.deviceController.display(0, DisplayHelper.center(now.format(DATE_FMT), 16));
					context.deviceController.display(1, DisplayHelper.center(now.format(TIME_FMT), 16));
					Thread.sleep(1000);
				}
				catch (InterruptedException e)
				{
					break;
				}
			}
		});
		context.clockThread.start();
		
	}

	@Override
	public void onExit()
	{
		context.clockThread.interrupt();
		context.clockThread = null;

	}

	@Override
	public void onEvent(Event event)
	{

	}

}
