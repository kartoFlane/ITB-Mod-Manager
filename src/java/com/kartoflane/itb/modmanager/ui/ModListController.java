package com.kartoflane.itb.modmanager.ui;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import com.kartoflane.itb.modmanager.event.Event;
import com.kartoflane.itb.modmanager.event.EventDouble;
import com.kartoflane.itb.modmanager.event.EventSingle;
import com.kartoflane.itb.modmanager.util.UIUtilities;

import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.input.TransferMode;
import net.vhati.modmanager.core.ModFileInfo;
import net.vhati.modmanager.ui.table.ListState;


/**
 * Controls interaction with the mod list.
 */
public class ModListController
{
	private final EventSingle<ModFileInfo> modSelected = new EventSingle<>();
	private final EventDouble<ModFileInfo, Boolean> modSelectionToggled = new EventDouble<>();

	private final ChangeListener<Boolean> checkboxSelectionListener;

	@FXML
	protected TreeView<ModFileInfo> treeView;


	public ModListController() throws IOException
	{
		checkboxSelectionListener = this::onCheckBoxSelectionChanged;
		createGUI();
	}

	/**
	 * Sent when a mod is selected (focused, highlighted) in the tree view.
	 */
	public Event.Single<ModFileInfo> modSelectedEvent()
	{
		return modSelected;
	}

	/**
	 * Sent when a mod's checkbox is toggled, meaning that the mod is selected for patching.
	 */
	public Event.Double<ModFileInfo, Boolean> modSelectionToggledEvent()
	{
		return modSelectionToggled;
	}

	protected void createGUI() throws IOException
	{
		FXMLLoader loader = new FXMLLoader( getClass().getResource( "/ModList.fxml" ) );
		loader.setController( this );
		loader.load();

		treeView.setCellFactory( this::modListCheckBoxTreeCellFactory );

		treeView.getSelectionModel().selectedItemProperty().addListener( this::onTreeItemSelectionChanged );

		treeView.setOnDragDetected( this::onDragDetected );
		treeView.setOnDragOver( this::onDragOver );
		treeView.setOnDragEntered( this::onDragEntered );
		treeView.setOnDragExited( this::onDragExited );
		treeView.setOnDragDropped( this::onDragDropped );
		treeView.setOnDragDone( this::onDragDone );
	}

	public TreeView<ModFileInfo> getTreeView()
	{
		return treeView;
	}

	public ListState<ModFileInfo> getCurrentModsTableState()
	{
		ListState<ModFileInfo> tableState = new ListState<ModFileInfo>();

		for ( TreeItem<ModFileInfo> treeItem : treeView.getRoot().getChildren() ) {
			tableState.addItem( treeItem.getValue() );
		}

		return tableState;
	}

	public void updateModel( ListState<ModFileInfo> tableState )
	{
		clearModel();

		ObservableList<TreeItem<ModFileInfo>> rootChildren = treeView.getRoot().getChildren();
		for ( ModFileInfo modFileInfo : tableState.getItems() ) {
			CheckBoxTreeItem<ModFileInfo> checkBoxItem = new CheckBoxTreeItem<ModFileInfo>( modFileInfo );
			checkBoxItem.selectedProperty().addListener( checkboxSelectionListener );
			rootChildren.add( checkBoxItem );
		}
	}

	public List<ModFileInfo> getSelectedMods()
	{
		return treeView.getRoot().getChildren().stream()
			.map( treeItem -> (CheckBoxTreeItem<ModFileInfo>)treeItem )
			.filter( treeItem -> treeItem.isSelected() )
			.map( treeItem -> treeItem.getValue() )
			.collect( Collectors.toList() );
	}

	public void toggleAllItemSelection()
	{
		List<TreeItem<ModFileInfo>> rootChildren = treeView.getRoot().getChildren();

		boolean anyDeselected = rootChildren.stream()
			.map( item -> (CheckBoxTreeItem<ModFileInfo>)item )
			.anyMatch( item -> !item.isSelected() );

		rootChildren.stream()
			.map( item -> (CheckBoxTreeItem<ModFileInfo>)item )
			.forEach( item -> item.setSelected( anyDeselected ) );
	}


	private void clearModel()
	{
		// Unregister listeners
		ObservableList<TreeItem<ModFileInfo>> rootChildren = treeView.getRoot().getChildren();
		rootChildren.stream()
			.filter( item -> item instanceof CheckBoxTreeItem )
			.map( item -> (CheckBoxTreeItem<ModFileInfo>)item )
			.forEach( item -> item.selectedProperty().removeListener( checkboxSelectionListener ) );

		rootChildren.clear();
	}

	private <T> CheckBoxTreeCell<T> modListCheckBoxTreeCellFactory( TreeView<T> treeView )
	{
		CheckBoxTreeCell<T> result = UIUtilities.unfocusableCheckBoxTreeCellFactory( treeView );
		result.setOnMouseClicked( this::onItemMouseClicked );
		return result;
	}

	// ---------------------------------------------------------------------------------
	// Listeners

	public void onModsTableStateAmended( ListState<ModFileInfo> tableState )
	{
		Platform.runLater( () -> updateModel( tableState ) );
	}

	@FXML
	private void onKeyInput( InputEvent event )
	{
		if ( event instanceof KeyEvent ) {
			final KeyEvent keyEvent = (KeyEvent)event;
			if ( keyEvent.getCode() == KeyCode.SPACE || keyEvent.getCode() == KeyCode.ENTER ) {
				CheckBoxTreeItem<ModFileInfo> selectedItem = (CheckBoxTreeItem<ModFileInfo>)treeView.getSelectionModel().getSelectedItem();
				if ( selectedItem != null ) {
					// Override spacebar and enter keys' behaviour to toggle checkboxes instead of
					// selecting/deselecting the tree item
					event.consume();
					selectedItem.setSelected( !selectedItem.isSelected() );
				}
			}
		}
	}

	@SuppressWarnings("unchecked")
	private void onItemMouseClicked( MouseEvent event )
	{
		if ( event.getButton().equals( MouseButton.PRIMARY ) ) {
			if ( event.getClickCount() == 1 ) {
				Node node = event.getPickResult().getIntersectedNode();
				if ( node instanceof CheckBoxTreeCell<?> ) {
					CheckBoxTreeCell<ModFileInfo> cell = (CheckBoxTreeCell<ModFileInfo>)node;
					if ( cell.getItem() == null ) {
						event.consume();
						treeView.getSelectionModel().clearSelection();
						modSelected.broadcast( null );
					}
				}
			}
			else if ( event.getClickCount() == 2 ) {
				if ( event.getSource() instanceof CheckBoxTreeCell<?> ) {
					CheckBoxTreeCell<ModFileInfo> cell = (CheckBoxTreeCell<ModFileInfo>)event.getSource();
					CheckBoxTreeItem<ModFileInfo> item = (CheckBoxTreeItem<ModFileInfo>)cell.getTreeItem();

					if ( item != null && item.isLeaf() ) {
						event.consume();
						item.setSelected( !item.isSelected() );
					}
				}
			}
		}
	}

	private void onTreeItemSelectionChanged(
		ObservableValue<? extends TreeItem<ModFileInfo>> property,
		TreeItem<ModFileInfo> old, TreeItem<ModFileInfo> neu
	)
	{
		if ( neu == null ) return;
		modSelected.broadcast( neu.getValue() );
	}

	@SuppressWarnings("unchecked")
	private void onCheckBoxSelectionChanged(
		ObservableValue<? extends Boolean> property,
		Boolean old, Boolean neu
	)
	{
		// Less than ideal, but we can't get the object that sent the event otherwise.
		SimpleBooleanProperty prop = (SimpleBooleanProperty)property;
		TreeItem<ModFileInfo> treeItem = (TreeItem<ModFileInfo>)prop.getBean();
		modSelectionToggled.broadcast( treeItem.getValue(), neu );
	}

	// ---------------------------------------------------------------------------------------
	// Drag and drop

	private void onDragDetected( MouseEvent e )
	{
		if ( e.getSource() == treeView ) {
			int index = treeView.getSelectionModel().getSelectedIndex();

			ClipboardContent content = new ClipboardContent();
			content.putString( Integer.toString( index ) );

			Dragboard db = treeView.startDragAndDrop( TransferMode.MOVE );
			db.setContent( content );

			treeView.getCellFactory().call( treeView );
			e.consume();
		}
	}

	private void onDragOver( DragEvent e )
	{
		Dragboard db = e.getDragboard();

		if ( e.getGestureSource() == treeView && db.hasString() ) {
			int index = Integer.parseInt( db.getString() );
			PickResult pick = e.getPickResult();
			int receiverIndex = UIUtilities.getReceiverIndex( treeView::getRow, pick, index );

			if ( receiverIndex == -1 ) {
				e.acceptTransferModes( TransferMode.NONE );
			}
			else {
				e.acceptTransferModes( TransferMode.MOVE );
			}
		}
		else {
			e.acceptTransferModes( TransferMode.NONE );
		}
	}

	private void onDragEntered( DragEvent e )
	{
		// TODO: Disallow dragging outside of the treeView?
	}

	private void onDragExited( DragEvent e )
	{
		// TODO: Disallow dragging outside of the treeView?
	}

	private void onDragDropped( DragEvent e )
	{
		Dragboard db = e.getDragboard();
		boolean success = false;

		if ( e.getGestureSource() == treeView && db.hasString() && e.getTransferMode() == TransferMode.MOVE ) {
			int index = Integer.parseInt( db.getString() );
			TreeItem<ModFileInfo> toRemove = treeView.getTreeItem( index );

			PickResult pick = e.getPickResult();
			int receiverIndex = UIUtilities.getReceiverIndex( treeView::getRow, pick, index );

			if ( receiverIndex == -1 ) {
				success = false;
			}
			else {
				if ( index != receiverIndex ) {
					ObservableList<TreeItem<ModFileInfo>> children = treeView.getRoot().getChildren();
					children.remove( toRemove );
					children.add( receiverIndex, toRemove );
					treeView.getSelectionModel().clearAndSelect( receiverIndex );
				}

				success = true;
			}
		}

		e.getDragboard().clear();
		e.setDropCompleted( success );
	}

	private void onDragDone( DragEvent e )
	{
	}
}
