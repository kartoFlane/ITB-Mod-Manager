package com.kartoflane.itb.modmanager.ui;

import java.lang.reflect.Field;

import com.kartoflane.itb.modmanager.util.UIUtilities;

import javafx.beans.value.ObservableValue;
import javafx.scene.control.CheckBox;
import javafx.scene.control.SelectionModel;
import javafx.scene.control.TreeItem;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.input.DragEvent;
import javafx.scene.input.MouseEvent;
import javafx.util.Callback;
import javafx.util.StringConverter;


public class UnfocusableCheckBoxTreeCell<T> extends CheckBoxTreeCell<T>
{
	private static final String STYLE_TOP = "-fx-border-color: -dnd-indicator-insert-color transparent transparent transparent;";
	private static final String STYLE_BOT = "-fx-border-color: transparent transparent -dnd-indicator-insert-color transparent;";
	private static final String STYLE_TOP_SEL = "-fx-border-color: -dnd-indicator-selected-insert-color transparent transparent transparent;";
	private static final String STYLE_BOT_SEL = "-fx-border-color: transparent transparent -dnd-indicator-selected-insert-color transparent;";


	public UnfocusableCheckBoxTreeCell(
		final Callback<TreeItem<T>, ObservableValue<Boolean>> getSelectedProperty,
		final StringConverter<TreeItem<T>> converter
	)
	{
		super( getSelectedProperty, converter );
		makeCheckBoxUnfocusable();

		setOnDragOver( this::onDragOver );
		setOnDragExited( this::onDragExited );
	}

	private void makeCheckBoxUnfocusable()
	{
		// Need to access the field by reflection, unless we want to completely reimplement
		// the entirety of CheckBoxTreeCell.

		Field f;
		try {
			f = CheckBoxTreeCell.class.getDeclaredField( "checkBox" );
			f.setAccessible( true );
			CheckBox checkBox = (CheckBox)f.get( this );
			checkBox.setFocusTraversable( false );
			checkBox.setOnMouseClicked( this::onMouseClicked );
		}
		catch ( Exception e ) {
		}
	}

	private void onMouseClicked( MouseEvent event )
	{
		getTreeView().requestFocus();

		SelectionModel<TreeItem<T>> model = getTreeView().getSelectionModel();

		boolean wasSelected = model.getSelectedItem() == getTreeItem();
		if ( !wasSelected ) {
			model.clearSelection();
			model.select( getTreeItem() );
		}
	}

	private void onDragOver( DragEvent e )
	{
		if ( getItem() == null )
			return;

		Object source = e.getGestureSource();
		if ( source == getTreeView() ) {
			int insertDirection = UIUtilities.getInsertionDirection( e.getPickResult() );

			if ( insertDirection == -1 ) {
				return;
			}

			setStyle( selectStyle( isSelected(), insertDirection == 0 ) );
		}
	}

	private static String selectStyle( boolean selected, boolean top )
	{
		return selected
			? top
				? STYLE_TOP_SEL
				: STYLE_BOT_SEL
			: top
				? STYLE_TOP
				: STYLE_BOT;
	}

	private void onDragExited( DragEvent e )
	{
		setStyle( null );
	}
}
