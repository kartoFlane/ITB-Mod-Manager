package com.kartoflane.itb.modmanager.ui;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;


public class FieldEditorPaneController
{
	public enum ContentType
	{
		WRAPPED_LABEL,
		LABEL,
		STRING,
		TEXT_AREA,
		INTEGER,
		BOOLEAN,
		SLIDER,
		COMBO,
		CHOOSER
	}


	private Map<String, Text> wrappedLabelMap = new HashMap<>();
	private Map<String, Label> labelMap = new HashMap<>();
	private Map<String, TextField> stringMap = new HashMap<>();
	private Map<String, TextArea> textAreaMap = new HashMap<>();
	private Map<String, TextField> integerMap = new HashMap<>();
	private Map<String, CheckBox> booleanMap = new HashMap<>();
	private Map<String, Slider> sliderMap = new HashMap<>();
	private Map<String, ComboBox<?>> comboMap = new HashMap<>();
	private Map<String, Chooser> chooserMap = new HashMap<>();

	private GridPane root;
	private int rows = 0;


	public FieldEditorPaneController()
	{
		root = new GridPane();
		root.setHgap( 10 );
		root.setVgap( 10 );
		root.setPadding( new Insets( 10 ) );

		ColumnConstraints cc = new ColumnConstraints();
		cc.setHalignment( HPos.LEFT );
		root.getColumnConstraints().add( 0, cc );

		cc = new ColumnConstraints();
		cc.setHalignment( HPos.RIGHT );
		cc.setHgrow( Priority.ALWAYS );
		root.getColumnConstraints().add( 1, cc );
	}

	public GridPane getRoot()
	{
		return root;
	}

	public void setLabelMinWidth( int minWidth )
	{
		root.getColumnConstraints().get( 0 ).setMinWidth( minWidth );
	}

	public void addRow( String valueName, ContentType contentType ) throws IOException
	{
		int columns = 0;

		root.add( new Label( valueName + ":" ), columns, rows );

		columns++;
		if ( contentType == ContentType.WRAPPED_LABEL ) {
			Text node = new Text();
			wrappedLabelMap.put( valueName, node );
			root.add( new TextFlow( node ), columns, rows );
		}
		else if ( contentType == ContentType.LABEL ) {
			Label node = new Label();
			labelMap.put( valueName, node );
			root.add( node, columns, rows );
		}
		else if ( contentType == ContentType.STRING ) {
			TextField node = new TextField();
			stringMap.put( valueName, node );
			root.add( node, columns, rows );
		}
		else if ( contentType == ContentType.TEXT_AREA ) {
			TextArea node = new TextArea();
			textAreaMap.put( valueName, node );
			root.add( node, columns, rows );
		}
		else if ( contentType == ContentType.INTEGER ) {
			TextField node = new TextField();
			node.textProperty().addListener( this::numericInputOnly );
			node.setMaxWidth( 80 );
			GridPane.setConstraints(
				node,
				columns, rows, 1, 1,
				HPos.RIGHT, VPos.CENTER,
				Priority.NEVER, Priority.NEVER
			);

			integerMap.put( valueName, node );
			root.add( node, columns, rows );
		}
		else if ( contentType == ContentType.BOOLEAN ) {
			CheckBox node = new CheckBox();
			booleanMap.put( valueName, node );
			root.add( node, columns, rows );
		}
		else if ( contentType == ContentType.SLIDER ) {
			Slider node = new Slider();
			sliderMap.put( valueName, node );
			root.add( node, columns, rows );
		}
		else if ( contentType == ContentType.COMBO ) {
			ComboBox<?> node = new ComboBox<>();
			comboMap.put( valueName, node );
			root.add( node, columns, rows );
		}
		else if ( contentType == ContentType.CHOOSER ) {
			HBox hbox = new HBox();
			hbox.setSpacing( 10 );

			TextField chooserField = new TextField();
			HBox.setHgrow( chooserField, Priority.ALWAYS );
			Button chooserBtn = new Button( "..." );
			chooserBtn.setPrefWidth( 30 );

			hbox.getChildren().addAll( chooserField, chooserBtn );
			Chooser chooser = new Chooser( chooserField, chooserBtn );

			chooserMap.put( valueName, chooser );
			root.add( hbox, columns, rows );
		}

		rows++;
	}

	public void addTextRow( String text )
	{
		root.add( new TextFlow( new Text( text ) ), 0, rows, 2, 1 );
		rows++;
	}

	public void addSeparatorRow()
	{
		root.add( new Separator(), 0, rows, 2, 1 );
		rows++;
	}

	public void addBlankRow()
	{
		root.add( new Label(), 0, rows, 2, 1 );
		rows++;
	}

	public String getValue( String valueName, ContentType type )
	{
		switch ( type ) {
			case STRING:
				return stringMap.get( valueName ).getText();
			case TEXT_AREA:
				return textAreaMap.get( valueName ).getText();
			case INTEGER:
				return integerMap.get( valueName ).getText();
			case BOOLEAN:
				return booleanMap.get( valueName ).isSelected() ? "true" : "false";
			case CHOOSER:
				return chooserMap.get( valueName ).getTextField().getText();
			default:
				throw new IllegalArgumentException( "Can't get value for type: " + type );
		}
	}

	public void setString( String valueName, String value )
	{
		TextField node = stringMap.get( valueName );
		if ( node != null )
			node.setText( value );
	}

	public void setInt( String valueName, int value )
	{
		TextField node = integerMap.get( valueName );
		if ( node != null )
			node.setText( "" + value );
	}

	public void setBoolean( String valueName, boolean value )
	{
		CheckBox node = booleanMap.get( valueName );
		if ( node != null )
			node.setSelected( value );
	}

	public void setChooserPath( String valueName, String value )
	{
		Chooser chooser = chooserMap.get( valueName );
		if ( chooser != null )
			chooser.getTextField().setText( value );
	}

	public void setChooserBrowseListener( String valueName, EventHandler<ActionEvent> value )
	{
		Chooser chooser = chooserMap.get( valueName );
		if ( chooser != null )
			chooser.getButton().setOnAction( value );
	}

	private void numericInputOnly( ObservableValue<? extends String> observable, String old, String neu )
	{
		if ( !neu.matches( "\\d*" ) ) {
			StringProperty property = (StringProperty)observable;
			TextInputControl textField = (TextInputControl)property.getBean();
			textField.setText( neu.replaceAll( "[^\\d]", "" ) );
		}
	}


	public static class Chooser
	{
		private TextField textField;
		private Button button;


		public Chooser( TextField field, Button btn )
		{
			textField = field;
			button = btn;
		}

		public TextField getTextField()
		{
			return textField;
		}

		public Button getButton()
		{
			return button;
		}
	}
}
