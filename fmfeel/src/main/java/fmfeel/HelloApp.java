package fmfeel;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import jsm.Event;

/**
 * JavaFX application that loads its UI from {@code main.fxml}, wired to
 * {@link MainController}.
 */
public class HelloApp extends Application
{
	private final Context context;
	
	public HelloApp(Context context)
	{
		this.context = context;
	}
	@Override
	public void start(Stage stage) throws Exception
	{
		FXMLLoader loader = new FXMLLoader(getClass().getResource("main.fxml"));
		Parent root = loader.load();
		context.guiController = (MainController)loader.getController();

		stage.setTitle("FmFeel");
		stage.setResizable(false);
		Scene scene = new Scene(root);
		stage.setScene(scene);
		stage.initStyle(StageStyle.TRANSPARENT);
		scene.setFill(Color.TRANSPARENT);
		scene.getStylesheets().add(getClass().getResource("fmfeel.css").toExternalForm());
		//stage.initStyle(StageStyle.UNDECORATED);
		stage.getIcons().add(new Image(getClass().getResourceAsStream("fmfeel.png")));

		stage.show();
		context.eventDispatcher.getEventQueue().offer(Event.build("gui_start"));
	}
//
//	public static void main(String[] args)
//	{
//		launch(args);
//	}
}
