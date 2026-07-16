package jsm;

import java.io.PrintWriter;
import java.io.StringWriter;

@FunctionalInterface
public interface Logger {
	void log(String message);
	
	public static String stackTraceToString(Throwable t) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		t.printStackTrace(pw);
		return sw.toString();
	}

}