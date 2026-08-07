package fmfeel;

public class Tuner
{
	public final static int MIN_FREQ = 1520; // 76 Mhz
	public final static int MAX_FREQ = 2160; // 108 Mhz
	public final static int DEFAULT_FREQ = 1750; // 87.5 Mhz Mhz
	
	private int currentFrequency = DEFAULT_FREQ; // 76 MHz in tuner units -> 1 tuner unit = 50 KHz

	public int getCurrentFrequency()
	{
		return currentFrequency;
	}

	public double getCurrentFrequencyMhz()
	{
		return currentFrequency * 0.05;
	}
	public void setCurrentFrequency(int currentFrequency)
	{
		if (currentFrequency < MIN_FREQ) this.currentFrequency = MIN_FREQ;
		else if (currentFrequency > MAX_FREQ) this.currentFrequency = MAX_FREQ;
		else this.currentFrequency = currentFrequency;
	}
	
	public int tune(int tuner_units)
	{
		setCurrentFrequency(this.currentFrequency + tuner_units);
		return getCurrentFrequency();
	}

}
