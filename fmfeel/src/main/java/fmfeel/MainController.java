package fmfeel;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

/**
 * Controller backing {@code main.fxml}.
 */
public class MainController
{
	@FXML
	private Rectangle dial;
	@FXML
	private Circle minimizeButton;
	@FXML
	private Circle closeButton;

	@FXML
	private void initialize()
	{
		minimizeButton.setOnMouseClicked(event -> {
		    ((Stage)((Circle)event.getSource()).getScene().getWindow()).setIconified(true);
		});
		closeButton.setOnMouseClicked(event -> {
		    ((Stage)((Circle)event.getSource()).getScene().getWindow()).close();
		});
	}

    private class Delta
    {
        public double x,y;
    }
    
    private Delta delta = new Delta();
    
    @FXML public void panePressed(MouseEvent me){
        Stage stage = (Stage)((Node)me.getSource()).getScene().getWindow();
        delta.x= stage.getX()- me.getScreenX();
        delta.y= stage.getY()- me.getScreenY();
    }

    @FXML public void paneDragged(MouseEvent me){
        Stage stage = (Stage)((Node)me.getSource()).getScene().getWindow();
        stage.setX(delta.x+me.getScreenX());
        stage.setY(delta.y+me.getScreenY());
    }
}
