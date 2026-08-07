package fmfeel;


import java.io.IOException;

import javafx.application.Platform;
import jsm.Event;

public class ShuttingDownState extends StateBase
{

	public ShuttingDownState(Context context)
	{
		super("shuttingDown", context);
	}

	@Override
	public void onEnter()
	{
		Platform.runLater(() -> {
			context.guiController.display("shutting down...");
		});
		context.runner.stop();
		context.deviceController.stop();
		try
		{
			context.persistency.save();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void onExit()
	{

	}

	@Override
	public void onEvent(Event event)
	{

	}

}
