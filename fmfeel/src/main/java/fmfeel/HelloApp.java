package fmfeel;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import jsm.Event;
import jsm.EventDispatcher;

/**
 * JavaFX application that loads its UI from {@code main.fxml}, wired to
 * {@link MainController}.
 */
public class HelloApp extends Application
{
	private final EventDispatcher eventDispatcher;
	
	public HelloApp(EventDispatcher eventDispatcher)
	{
		this.eventDispatcher = eventDispatcher;
	}
	@Override
	public void start(Stage stage) throws Exception
	{
		FXMLLoader loader = new FXMLLoader(getClass().getResource("main.fxml"));
		Parent root = loader.load();

		stage.setTitle("FmFeel");
		stage.setResizable(false);
		stage.setScene(new Scene(root));
		stage.initStyle(StageStyle.UNDECORATED);
		stage.show();
		eventDispatcher.getEventQueue().offer(Event.build("gui_start"));
	}
//
//	public static void main(String[] args)
//	{
//		launch(args);
//	}
}
