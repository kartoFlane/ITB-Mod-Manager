package com.kartoflane.itb.modmanager.ui;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;

import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;


public class FileSelectorController
{
	@FXML
	private HBox root;
	@FXML
	private Label label;
	@FXML
	private TextField textPath;
	@FXML
	private Button btnBrowse;

	private final SelectorType type;


	public FileSelectorController( SelectorType type, String labelText ) throws IOException
	{
		this.type = type;
		createGUI( labelText );
	}

	private void createGUI( String labelText ) throws IOException
	{
		FXMLLoader loader = new FXMLLoader( getClass().getResource( "FileSelector.fxml" ) );
		loader.setController( this );
		loader.load();

		label.setText( labelText );
		if ( labelText == null || labelText.isEmpty() ) {
			root.getChildren().remove( label );
		}

		textPath.setOnDragOver( this::onDragOver );
		textPath.setOnDragDropped( this::onDragDropped );
	}

	public SelectorType getType()
	{
		return type;
	}

	public HBox getRoot()
	{
		return root;
	}

	public void bindLabelWidth( ObservableDoubleValue widthProperty )
	{
		if ( widthProperty == null )
			label.prefWidthProperty().unbind();
		else
			label.prefWidthProperty().bind( widthProperty );
	}

	public ReadOnlyDoubleProperty labelWidthProperty()
	{
		return label.widthProperty();
	}

	public void setLabelWidth( double width )
	{
		label.setPrefWidth( width );
	}

	public String getSelectedPath()
	{
		return textPath.getText();
	}

	@FXML
	private void onBrowseClicked( ActionEvent e )
	{
		if ( type == SelectorType.FILE ) {
			FileChooser chooser = new FileChooser();
			chooser.setTitle( "Select a file" );
			chooser.showOpenDialog( root.getScene().getWindow() );
		}
		else if ( type == SelectorType.DIRECTORY ) {
			DirectoryChooser chooser = new DirectoryChooser();
			chooser.setTitle( "Select a directory" );
			chooser.showDialog( root.getScene().getWindow() );
		}
		else {
			throw new RuntimeException( "Implementation error: no case for " + type );
		}
	}

	private void onDragOver( DragEvent e )
	{
		Dragboard db = e.getDragboard();

		if ( db.hasString() || db.hasUrl() || db.hasFiles() ) {
			e.acceptTransferModes( TransferMode.COPY );
		}
		else {
			e.acceptTransferModes( TransferMode.NONE );
		}

		e.consume();
	}

	private void onDragDropped( DragEvent e )
	{
		Dragboard db = e.getDragboard();

		if ( db.hasString() || db.hasUrl() || db.hasFiles() ) {
			String path = textPath.getText();
			path = path == null ? "" : path;

			if ( db.hasString() ) {
				path += db.getString();
			}
			else if ( db.hasUrl() ) {
				try {
					URL url = new URL( db.getUrl() );
					path += url.getPath();
				}
				catch ( Exception ex ) {
					e.setDropCompleted( false );
					e.consume();
					return;
				}
			}
			else if ( db.hasFiles() ) {
				path += db.getFiles().get( 0 ).getPath();
			}

			textPath.setText( path );
			e.setDropCompleted( true );
			e.consume();
		}
	}


	public static enum SelectorType
	{
		FILE,
		DIRECTORY;

		public static final SelectorType[] FILE_AND_DIR = alternatingFile( 2 );
		public static final SelectorType[] DIR_AND_FILE = alternatingDir( 2 );
		public static final SelectorType[] TWO_FILES = files( 2 );
		public static final SelectorType[] TWO_DIRS = directories( 2 );


		/**
		 * Returns an array of specified length, with all values equal to {@link FILE}.
		 */
		public static SelectorType[] files( int count )
		{
			return fill( count, FILE );
		}

		/**
		 * Returns an array of specified length, with all values equal to {@link DIRECTORY}.
		 */
		public static SelectorType[] directories( int count )
		{
			return fill( count, DIRECTORY );
		}

		private static SelectorType[] fill( int count, SelectorType type )
		{
			SelectorType[] result = new SelectorType[count];
			Arrays.fill( result, type );
			return result;
		}

		/**
		 * Returns an array of specified length, with values alternating between
		 * {@link FILE} and {@link DIRECTORY} (starting with {@link FILE}).
		 */
		public static SelectorType[] alternatingFile( int count )
		{
			return alternating( count, FILE );
		}

		/**
		 * Returns an array of specified length, with values alternating between
		 * {@link DIRECTORY} and {@link FILE} (starting with {@link DIRECTORY}).
		 */
		public static SelectorType[] alternatingDir( int count )
		{
			return alternating( count, DIRECTORY );
		}

		private static SelectorType[] alternating( int count, SelectorType first )
		{
			SelectorType[] result = new SelectorType[count];
			for ( int i = count - 1; i >= 0; --i ) {
				result[i] = first;
				first = first == FILE ? DIRECTORY : FILE;
			}
			return result;
		}
	}
}
