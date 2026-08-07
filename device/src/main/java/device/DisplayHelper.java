package device;

public class DisplayHelper {
	
	public static String center(String s, int width) {
	    if (s.length() >= width) return s.substring(0, width);
	    int left = (width - s.length()) / 2;
	    StringBuilder sb = new StringBuilder(width);
	    for (int i = 0; i < left; i++) sb.append(' ');
	    sb.append(s);
	    while (sb.length() < width) sb.append(' ');   // trailing pad clears leftovers
	    return sb.toString();
	}
}
