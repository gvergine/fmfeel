package device;

public enum DeviceMessage
{
	HB,
	LEFT_ENCODER_CCW,
	LEFT_ENCODER_CW,
	LEFT_BUTTON_DOWN,
	LEFT_BUTTON_UP,
	RIGHT_ENCODER_CCW,
	RIGHT_ENCODER_CW,
	RIGHT_BUTTON_DOWN,
	RIGHT_BUTTON_UP,
	INVALID;
	
	public boolean isValid(DeviceMessage msg)
	{
		return this != INVALID;
	}
}
