package com.kartoflane.itb.modmanager.ui;

import javafx.beans.property.StringProperty;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;


/**
 * @source https://gist.github.com/Warlander/815f5c435b2b11527ce65ff165dde023
 */
public class ClickableMenu extends Menu
{
	private final Label label;
	private final MenuItem dummyItem;


	/**
	 * Creates new ClickableMenu without title.
	 */
	public ClickableMenu()
	{
		this( "" );
	}

	/**
	 * Creates new ClickableMenu with given title.
	 * 
	 * @param title
	 *            initial title
	 */
	public ClickableMenu( String title )
	{
		// dummy item is needed to make JavaFX "believe", that menu item was pressed
		dummyItem = new MenuItem();
		dummyItem.setVisible( false );
		getItems().add( dummyItem );

		label = new Label();
		label.setText( title );
		// forced child MenuItem click (this item is hidden, so this action
		// is not visible but triggers parent "onAction" event handler anyway)
		label.setOnMouseClicked( e -> fire() );
		setGraphic( label );
	}

	public void fire()
	{
		dummyItem.fire();
	}

	/**
	 * This method should be used instead of {@link #getText() getText()} method.
	 * 
	 * @return title of this Menu
	 */
	public String getTitle()
	{
		return label.getText();
	}

	/**
	 * This method should be used instead of {@link #setText() setText()} method.
	 * 
	 * @param text
	 *            new title of this menu
	 */
	public void setTitle( String text )
	{
		label.setText( text );
	}

	/**
	 * This method should be used instead of {@link #textProperty() textProperty()} method.
	 * 
	 * @return title property
	 */
	public StringProperty titleProperty()
	{
		return label.textProperty();
	}
}
