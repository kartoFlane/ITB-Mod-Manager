package com.kartoflane.itb.modmanager.ui;

import java.io.IOException;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.stage.Modality;
import javafx.stage.Stage;


public class ProgressDialogController
{
	@FXML
	protected ProgressBar progressBar;
	@FXML
	protected ScrollPane scrollStatus;
	@FXML
	protected TextArea txtStatus;
	@FXML
	protected Button btnContinue;

	protected boolean continueOnSuccess = false;
	protected boolean done = false;
	protected boolean succeeded = false;
	protected Runnable successTask = null;

	protected Stage stage = null;


	public ProgressDialogController( Stage owner, boolean continueOnSuccess ) throws IOException
	{
		this.continueOnSuccess = continueOnSuccess;
		createGUI( owner );
	}

	protected void createGUI( Stage owner ) throws IOException
	{
		FXMLLoader loader = new FXMLLoader( getClass().getResource( "ProgressDialog.fxml" ) );
		loader.setController( this );
		Parent root = loader.load();

		stage = new Stage();
		stage.setScene( new Scene( root ) );

		stage.initOwner( owner );
		stage.setResizable( false );
		stage.setOnCloseRequest( e -> e.consume() );
		stage.initModality( Modality.APPLICATION_MODAL );
	}

	public void show()
	{
		stage.show();
	}

	/**
	 * @see Stage#showAndWait()
	 */
	public void showAndWait()
	{
		stage.showAndWait();
	}

	/**
	 * Updates the text area's content. (Thread-safe)
	 *
	 * @param message
	 *            a string, or null
	 */
	public void setStatusTextLater( final String message )
	{
		Platform.runLater( () -> setStatusText( message != null ? message : "..." ) );
	}

	protected void setStatusText( String message )
	{
		txtStatus.setText( message != null ? message : "..." );
		txtStatus.selectPositionCaret( 0 );
		txtStatus.deselect();
	}

	/**
	 * Updates the progress bar. (Thread-safe)
	 *
	 * If the arg is negative, the bar will become indeterminate.
	 * 
	 * Max is implicitly set at 1.
	 *
	 * @param value
	 *            the new value, range [0, 1]
	 */
	public void setProgressLater( final double value )
	{
		Platform.runLater( () -> setProgress( value, 1 ) );
	}

	/**
	 * Updates the progress bar. (Thread-safe)
	 *
	 * If either arg is negative, the bar will become indeterminate.
	 *
	 * @param value
	 *            the new value
	 * @param max
	 *            the new maximum
	 */
	public void setProgressLater( final double value, final double max )
	{
		Platform.runLater( () -> setProgress( value, max ) );
	}

	protected void setProgress( final double value, final double max )
	{
		if ( value >= 0 && max >= 0 ) {
			double normalizedValue = max == 0 ? 1 : value / max;
			progressBar.setProgress( normalizedValue );
		}
		else {
			// Negative values automatically set the progress bar
			// to indeterminate mode.
			progressBar.setProgress( -1 );
		}
	}

	/**
	 * Triggers a response to the immediate task ending. (Thread-safe)
	 *
	 * If anything went wrong, e may be non-null.
	 */
	public void setTaskOutcomeLater( final boolean success, final Exception e )
	{
		Platform.runLater( () -> setTaskOutcome( success, e ) );
	}

	protected void setTaskOutcome( final boolean outcome, final Exception e )
	{
		done = true;
		succeeded = outcome;

		if ( !stage.isShowing() ) {
			// The window's not visible, no continueBtn to click.
			stage.close();

			if ( succeeded && successTask != null ) {
				successTask.run();
			}
		}
		if ( continueOnSuccess && succeeded && successTask != null ) {
			stage.close();
			successTask.run();
		}
		else {
			btnContinue.setDisable( false );
			btnContinue.requestFocus();
		}
	}

	/**
	 * Sets a runnable to trigger after the immediate task ends successfully.
	 */
	public void setSuccessTask( Runnable r )
	{
		successTask = r;
	}

	/**
	 * Shows or hides this component depending on the value of parameter b.
	 *
	 * If the immediate task has already completed, this method will do nothing.
	 */
	public void setVisible( boolean b )
	{
		if ( !done ) {
			if ( b )
				stage.show();
			else
				stage.hide();
		}
	}

	@FXML
	protected void onContinueClicked()
	{
		stage.close();

		if ( done && succeeded && successTask != null ) {
			successTask.run();
		}
	}
}
