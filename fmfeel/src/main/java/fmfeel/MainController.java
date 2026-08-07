package fmfeel;

import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.Stage;

/**
 * Controller backing {@code main.fxml}.
 */
public class MainController implements RadioGUIController
{
	@FXML
	private Rectangle dial;
	@FXML
	private Circle minimizeButton;
	@FXML
	private Circle closeButton;
	@FXML
	private ProgressBar volume;
	@FXML
	private Label label;

	@FXML
	private void initialize()
	{
		minimizeButton.setOnMouseClicked(event -> {
		    ((Stage)((Circle)event.getSource()).getScene().getWindow()).setIconified(true);
		});
		closeButton.setOnMouseClicked(event -> {
		    ((Stage)((Circle)event.getSource()).getScene().getWindow()).close();
		});
		hideTunerDial();
		hideVolume();
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

	@Override
	public void showTunerDial() {
		dial.setVisible(true);
	}

	@Override
	public void hideTunerDial() {
		dial.setVisible(false);		
	}

	private static final double left_boundary = 28.6;
	private static final double right_boundary = 606.14;
	private static final double tuner_width = right_boundary - left_boundary;
	private static final double width_per_tuner_unit = tuner_width / (Tuner.MAX_FREQ - Tuner.MIN_FREQ);
	

	@Override
	public void moveTunerDial(int tuner_units)
    {
		dial.setX(left_boundary + (tuner_units - Tuner.MIN_FREQ) * width_per_tuner_unit);
		System.out.println(String.format("tu=%d mhz=%.2f px=%.2f", tuner_units, tuner_units * 0.05, dial.getX()));
	}

	@Override
	public void display(String text) {
		label.setText(text);
	}

	@Override
	public void displayVolume(double level) {
		volume.setProgress(level);
		volume.setVisible(true);

	}

	@Override
	public void hideVolume() {
		volume.setVisible(false);
		
	}
	
}
