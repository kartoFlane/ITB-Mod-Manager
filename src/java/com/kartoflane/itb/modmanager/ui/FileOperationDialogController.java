package com.kartoflane.itb.modmanager.ui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import com.kartoflane.itb.modmanager.ui.FileSelectorController.SelectorType;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;


public class FileOperationDialogController
{
	@FXML
	protected ScrollPane scrollPane;
	@FXML
	protected VBox contentPane;
	@FXML
	protected HBox buttonsPane;
	@FXML
	protected Button btnOk;
	@FXML
	protected Button btnCancel;

	protected List<FileSelectorController> selectors = null;
	protected Consumer<File[]> fileOperation = null;
	protected boolean closeOnAccept = false;

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
		}

		stage = new Stage();
		stage.setScene( new Scene( root ) );

		stage.initOwner( owner );
		stage.setResizable( false );
		stage.initModality( Modality.APPLICATION_MODAL );
	}

	protected void layoutLabels()
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

	public void setCloseOnAccept( boolean close )
	{
		closeOnAccept = close;
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
		layoutLabels();
	}

	/**
	 * @see Stage#showAndWait()
	 */
	public void showAndWait()
	{
		stage.showAndWait();
	}

	@FXML
	protected void onOkClicked( ActionEvent e )
	{
		if ( closeOnAccept ) {
			stage.fireEvent( new WindowEvent( stage, WindowEvent.WINDOW_CLOSE_REQUEST ) );
		}

		if ( fileOperation != null ) {
			fileOperation.accept( getFiles() );
		}
	}

	@FXML
	protected void onCancelClicked( ActionEvent e )
	{
		stage.fireEvent( new WindowEvent( stage, WindowEvent.WINDOW_CLOSE_REQUEST ) );
	}
}
