package fmfeel;

public class Volume
{
	public final static int MIN_VOL = 0;
	public final static int MAX_VOL = 30;
	public final static int DEFAULT_VOL = 5;
	
	private int currentVolume = DEFAULT_VOL;

	public int getCurrentVolume()
	{
		return currentVolume;
	}

	public void setCurrentVolume(int currentVolume)
	{
		if (currentVolume < MIN_VOL) this.currentVolume = MIN_VOL;
		else if (currentVolume > MAX_VOL) this.currentVolume = MAX_VOL;
		else this.currentVolume = currentVolume;
	}
	
	public int changeVolume(int steps)
	{
		setCurrentVolume(this.currentVolume + steps);
		return getCurrentVolume();
	}

}
