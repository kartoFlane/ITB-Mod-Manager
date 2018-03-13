package com.kartoflane.itb.modmanager.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.kartoflane.itb.modmanager.ITBModManager;
import com.kartoflane.itb.modmanager.ui.UnfocusableCheckBoxTreeCell;

import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Orientation;
import javafx.geometry.Point3D;
import javafx.scene.Node;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.input.PickResult;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Callback;
import javafx.util.StringConverter;


public class UIUtilities
{
	/**
	 * Factory method creating instances of {@link UnfocusableCheckBoxTreeCell}.
	 */
	public static <T> CheckBoxTreeCell<T> unfocusableCheckBoxTreeCellFactory( TreeView<T> treeView )
	{
		// Copied from CheckBoxTreeCell.forTreeView()

		Callback<TreeItem<T>, ObservableValue<Boolean>> getSelectedProperty = item -> {
			if ( item instanceof CheckBoxTreeItem<?> ) {
				return ( (CheckBoxTreeItem<?>)item ).selectedProperty();
			}
			return null;
		};

		final StringConverter<TreeItem<T>> converter = new StringConverter<TreeItem<T>>() {
			@Override
			public String toString( TreeItem<T> treeItem )
			{
				return ( treeItem == null || treeItem.getValue() == null ) ? "" : treeItem.getValue().toString();
			}

			@Override
			public TreeItem<T> fromString( String string )
			{
				// Can't convert from String to T (which in our case will be ModFileInstance)
				throw new UnsupportedOperationException();
			}
		};

		CheckBoxTreeCell<T> result = new UnfocusableCheckBoxTreeCell<T>(
			getSelectedProperty, (StringConverter<TreeItem<T>>)converter
		);

		return result;
	}

	/**
	 * Returns a scrollbar from the specified pane, with the specified orientation.
	 * Null if the scrollbar could not be found, or is not visible.
	 */
	public static ScrollBar getScrollBar( ScrollPane pane, Orientation orientation )
	{
		Optional<ScrollBar> o = pane.lookupAll( ".scroll-bar" ).stream()
			.filter( n -> n instanceof ScrollBar )
			.map( n -> (ScrollBar)n )
			.filter( sb -> sb.getOrientation() == orientation )
			.findFirst();

		return o.isPresent() ? o.get() : null;
	}

	/**
	 * Finds and returns the first instance of TreeCell in the specified node's
	 * parent hierarchy, starting from the node.
	 */
	@SuppressWarnings("unchecked")
	public static <T> TreeCell<T> getCellParent( Node node )
	{
		while ( node != null && node instanceof TreeCell<?> == false ) {
			node = node.getParent();
		}
		return node == null ? null : (TreeCell<T>)node;
	}

	/**
	 * Returns the 'insertion direction' -- whether the item being dragged should be inserted
	 * above the item being dragged over, or below it.
	 * 
	 * @param node
	 *            the node being hovered over, obtained from PickResult
	 * @param cell
	 *            TreeCell instance that is parent to the node being hovered over
	 * @param intersectedPoint
	 *            intersection point, obtained from PickResult
	 * @return 0 if should be inserted above; 1 if below; -1 if can't insert.
	 */
	public static <T> int getInsertionDirection( Node node, TreeCell<T> cell, Point3D intersectedPoint )
	{
		if ( node == null || cell == null || intersectedPoint == null )
			return -1;

		Point3D absolutePoint = node.localToScene( intersectedPoint );
		Point3D normalizedPoint = cell.sceneToLocal( absolutePoint );

		return normalizedPoint.getY() < cell.getHeight() / 2 ? 0 : 1;
	}

	/**
	 * @see #getInsertionDirection(Node, TreeCell, Point3D)
	 */
	public static <T> int getInsertionDirection( PickResult pick )
	{
		Node node = pick.getIntersectedNode();
		TreeCell<T> cell = getCellParent( node );
		return getInsertionDirection( node, cell, pick.getIntersectedPoint() );
	}

	/**
	 * Works out index of the mod entry the user is currently hovering over,
	 * and returns index where the dragged mod should be inserted.
	 * 
	 * @param getRow
	 *            function that, given a tree item, returns its row index
	 * @param pick
	 *            PickResult from a dragging gesture
	 * @param index
	 *            index of the item that is being dragged
	 * @return the index where the dragged mod should be inserted,
	 *         or -1 if can't insert at the current location
	 */
	public static <T> int getReceiverIndex( Function<TreeItem<T>, Integer> getRow, PickResult pick, int index )
	{
		Node node = pick.getIntersectedNode();
		TreeCell<T> cell = UIUtilities.getCellParent( node );

		if ( cell == null )
			return -1;

		int insertDirection = UIUtilities.getInsertionDirection( node, cell, pick.getIntersectedPoint() );
		if ( insertDirection == -1 )
			return -1;

		TreeItem<T> receiver = cell.getTreeItem();

		int receiverIndex = getRow.apply( receiver );

		if ( receiverIndex == -1 )
			return -1;
		if ( receiverIndex == index )
			return receiverIndex;

		receiverIndex += index == receiverIndex
			? 0
			: index > receiverIndex
				? 0
				: -1;
		receiverIndex += insertDirection;

		return receiverIndex;
	}

	/**
	 * Parses the specified input text using a kind of BBCode-like syntax to
	 * achieve decorated text.
	 * Currently supports:
	 * - bold via [b][/b]
	 * - italic via [i][/i]
	 */
	public static TextFlow decoratedText( String input, ObservableValue<? extends Number> widthProperty )
	{
		if ( input == null || input.isEmpty() ) {
			return new TextFlow();
		}

		final Pattern bbPtn = Pattern.compile( "\\[([^\\]]+)\\]([^\\]]+)\\[\\/[^\\]]+\\]" );
		final Pattern normalPtn = Pattern.compile( "[^\\[^\\]]+" );

		Matcher bbM = bbPtn.matcher( input );
		Matcher normalM = normalPtn.matcher( input );

		List<Text> chunks = new ArrayList<Text>();

		int start = 0;
		int end = input.length();
		while ( start < end ) {
			int i = -1;
			int j = -1;

			if ( bbM.find( start ) ) i = bbM.start();
			if ( normalM.find( start ) ) j = normalM.start();

			if ( i != -1 && ( i < j || j == -1 ) ) {
				start = bbM.end();

				String tag = bbM.group( 1 );
				String content = bbM.group( 2 );

				if ( tag.equals( "b" ) ) {
					Text t = new Text( content );
					t.setStyle( "-fx-font-weight: bold;" );
					chunks.add( t );
				}
				else if ( tag.equals( "i" ) ) {
					Text t = new Text( content );
					t.setStyle( "-fx-font-style: italic;" );
					chunks.add( t );
				}
			}
			else if ( j != -1 && ( i > j || i == -1 ) ) {
				start = normalM.end();
				chunks.add( new Text( normalM.group() ) );
			}
			else {
				System.out.println( i + " == " + j + " ?\n" + input.substring( start ) );
				break;
			}
		}

		return wrappingTextFlow( widthProperty, chunks );
	}

	public static TextFlow wrappingTextFlow(
		ObservableValue<? extends Number> widthProperty,
		Collection<? extends Node> nodes
	)
	{
		TextFlow flow = new TextFlow();
		flow.getChildren().addAll( nodes );
		flow.maxWidthProperty().bindBidirectional( flow.prefWidthProperty() );
		if ( widthProperty != null ) {
			flow.prefWidthProperty().bind( widthProperty );
		}
		return flow;
	}

	public static TextFlow wrappingTextFlow(
		ObservableValue<? extends Number> widthProperty,
		Node... nodes
	)
	{
		return wrappingTextFlow( widthProperty, Arrays.asList( nodes ) );
	}

	public static Hyperlink createHyperlink( String url )
	{
		return createHyperlink( url, url );
	}

	public static Hyperlink createHyperlink( String text, String url )
	{
		Hyperlink result = new Hyperlink( text );
		result.setOnAction( e -> ITBModManager.getApplication().getHostServices().showDocument( url ) );
		return result;
	}

	/**
	 * If called on UI thread, executes the runnable immediately.
	 * If called on another thread, schedules the runnable for exeution on UI thread.
	 */
	public static void runNowOrLater( Runnable r )
	{
		if ( Platform.isFxApplicationThread() ) {
			r.run();
		}
		else {
			Platform.runLater( r );
		}
	}
}
