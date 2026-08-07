package fmfeel;

public interface RadioGUIController
{
	void showTunerDial();
	void hideTunerDial();
	void moveTunerDial(int tuner_units);
	void display(String text);
	void hideVolume();
	void displayVolume(double level);
}
