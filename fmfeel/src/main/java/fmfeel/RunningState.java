package fmfeel;

import java.util.Map;

import device.DisplayHelper;
import fmfeel.Persistency.Config;
import javafx.application.Platform;
import jsm.Event;

public class RunningState extends StateBase
{

	public RunningState(Context context)
	{
		super("running", context);
	}

	@Override
	public void onEnter()
	{
		context.tuner.setCurrentFrequency(context.persistency.getConfig().frequency);
		

		Map<Integer, String> stations = context.persistency.getConfig().stations;
		int freq = context.tuner.getCurrentFrequency();
		
		String url = stations.getOrDefault(freq, null);
		context.radioPlayer = new RadioPlayer(url, new RadioPlayer.Listener() {
			
			@Override
			public void onTitle(String title) {

			}
			
			@Override
			public void onStationName(String name) {
				context.deviceController.display(1, DisplayHelper.center(name,16));						
			}
		});
		int vol = context.volume.getCurrentVolume();
		context.radioPlayer.setStationVolume(vol / 30.0);
		if (url != null) context.radioPlayer.setNoiseVolume(0);
		else context.radioPlayer.setNoiseVolume(0.1);
		context.radioPlayer.play();


		
		Platform.runLater(() -> {
			context.guiController.moveTunerDial(context.tuner.getCurrentFrequency());
			context.guiController.showTunerDial();
		});
		context.deviceController.display(0, DisplayHelper.center(
				String.format("FM %.2f", context.tuner.getCurrentFrequencyMhz()),
				16
				));
	}

	@Override
	public void onExit()
	{
		context.radioPlayer.stop();
		context.radioPlayer = null;
		Platform.runLater(() -> {
			context.guiController.hideTunerDial();
		});
		context.deviceController.display(0, "");
		context.deviceController.display(1, "");
	}


	@Override
	public void onEvent(Event event)
	{
		if (event.getName().compareTo("tune_up") == 0)
		{
			handleTune(true);
		}
		else if (event.getName().compareTo("tune_down") == 0)
		{
			handleTune(false);
		}
		else if (event.getName().compareTo("volume_up") == 0)
		{
			handleVolume(true);
		}
		else if (event.getName().compareTo("volume_down") == 0)
		{
			handleVolume(false);
		}
	}
	
	private void handleTune(boolean up) // false is down
	{
		context.tuner.tune(up? 1 : -1);
		
		Map<Integer, String> stations = context.persistency.getConfig().stations;
		int freq = context.tuner.getCurrentFrequency();
		
		String url = stations.getOrDefault(freq, null);

		if (url != null) context.radioPlayer.setNoiseVolume(0);
		else context.radioPlayer.setNoiseVolume(0.1);
		context.radioPlayer.setUrl(url);

		
		context.persistency.getConfig().frequency = context.tuner.getCurrentFrequency();
		context.deviceController.display(0, DisplayHelper.center(
				String.format("FM %.2f", context.tuner.getCurrentFrequencyMhz()),
				16
				));

		Platform.runLater(() -> {
			context.guiController.moveTunerDial(context.tuner.getCurrentFrequency());
		});	
	}
	
	private void handleVolume(boolean up) // false is down
	{
		context.volume.changeVolume(up ? 1 : -1);
		
		int vol = context.volume.getCurrentVolume();
		
		context.radioPlayer.setStationVolume(vol / 30.0);
		
		
		context.persistency.getConfig().volume = vol;
		
	//	int normalizedVolForDisplay = (int)(vol / 30.0 * 12.0);
		
	//	context.deviceController.display(1, "VOL: " + "*".repeat(normalizedVolForDisplay) );

	}

}
