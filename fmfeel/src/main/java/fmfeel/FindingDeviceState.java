package fmfeel;


import javafx.application.Platform;
import jsm.Event;

public class FindingDeviceState extends StateBase
{

	public FindingDeviceState(Context context)
	{
		super("findingDevice", context);
	}

	@Override
	public void onEnter()
	{
		Platform.runLater(() -> {
			context.guiController.display("...searching device...");
		});
		context.deviceController.start();
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
