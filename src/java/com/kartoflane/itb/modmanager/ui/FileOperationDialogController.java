package com.kartoflane.itb.modmanager.ui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.kartoflane.itb.modmanager.ui.FileSelectorController.SelectorType;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;


/**
 * A generic dialog for performing operations on any number of selectable files.
 * 
 * @author kartoFlane
 */
public class FileOperationDialogController
{
	protected static final Logger log = LogManager.getLogger();

	@FXML
	protected ScrollPane scrollPane;
	@FXML
	protected VBox contentPane;
	@FXML
	protected HBox buttonsPane;
	@FXML
	protected CheckBox btnKeepOpen;
	@FXML
	protected Button btnOk;
	@FXML
	protected Button btnCancel;

	protected List<FileSelectorController> selectors = null;
	protected Consumer<File[]> fileOperation = null;

	protected Stage stage = null;


	public FileOperationDialogController( Stage owner, SelectorType[] types, String... labels ) throws IOException
	{
		if ( labels != null && types.length != labels.length )
			throw new IllegalArgumentException( "Array of types and array of labels must have equal length." );
		createGUI( owner, types, labels );
	}

	public FileOperationDialogController( Stage owner, SelectorType... types ) throws IOException
	{
		this( owner, types, (String[])null );
	}

	protected void createGUI( Stage owner, SelectorType[] types, String[] labels ) throws IOException
	{
		FXMLLoader loader = new FXMLLoader( getClass().getResource( "FileOperationDialog.fxml" ) );
		loader.setController( this );
		Parent root = loader.load();

		selectors = new ArrayList<>( types.length );
		for ( int i = 0; i < types.length; ++i ) {
			String label = labels == null ? null : labels[i];
			FileSelectorController selector = new FileSelectorController( types[i], label );

			selectors.add( selector );
			contentPane.getChildren().add( selector.getRoot() );

			if ( i == 0 )
				selector.requestFocus();
		}

		stage = new Stage();
		stage.setScene( new Scene( root ) );

		stage.initOwner( owner );
		stage.setResizable( false );
		stage.initModality( Modality.APPLICATION_MODAL );
	}

	protected void init()
	{
		double maxWidth = selectors.stream()
			.map( selector -> selector.labelWidthProperty().get() )
			.max( Double::compare ).get();

		selectors.forEach( selector -> selector.setLabelWidth( maxWidth ) );
	}

	public void setTitle( String title )
	{
		stage.setTitle( title );
	}

	public void setOkButtonText( String text )
	{
		btnOk.setText( text );
	}

	public void setKeepOpen( boolean keepOpen )
	{
		btnKeepOpen.setSelected( keepOpen );
	}

	public void setFileOperation( Consumer<File[]> operation )
	{
		fileOperation = operation;
	}

	public int getFileCount()
	{
		return selectors.size();
	}

	public File[] getFiles()
	{
		return selectors.stream()
			.map(
				selector -> {
					String path = selector.getSelectedPath();
					return path == null ? null : new File( path );
				}
			)
			.collect( Collectors.toList() )
			.toArray( new File[0] );
	}

	public void show()
	{
		stage.show();
		init();
	}

	/**
	 * @see Stage#showAndWait()
	 */
	public void showAndWait()
	{
		stage.show();
		init();
		stage.hide();

		stage.showAndWait();
	}

	@FXML
	protected void onOkClicked( ActionEvent e )
	{
		boolean anyEmpty = selectors.stream()
			.map( selector -> selector.getSelectedPath() )
			.anyMatch( path -> path == null || path.isEmpty() );

		if ( anyEmpty ) {
			String msg = ""
				+ "One or more fields are still empty.\n\n"
				+ "Please fill out all fields before proceeding.";
			new Alert( AlertType.WARNING, msg ).show();
			return;
		}

		if ( !btnKeepOpen.isSelected() ) {
			stage.fireEvent( new WindowEvent( stage, WindowEvent.WINDOW_CLOSE_REQUEST ) );
		}

		if ( fileOperation != null ) {
			try {
				fileOperation.accept( getFiles() );
			}
			catch ( Exception ex ) {
				log.error( "Error while executing file dialog's operation:", ex );
				String msg = ""
					+ "An error has occurred while executing requested operation:\n\n"
					+ ex.getMessage();

				new Alert( AlertType.ERROR, msg ).show();
			}
		}
	}

	@FXML
	protected void onCancelClicked( ActionEvent e )
	{
		stage.fireEvent( new WindowEvent( stage, WindowEvent.WINDOW_CLOSE_REQUEST ) );
	}
}
