package com.kartoflane.itb.modmanager.util;

import java.util.List;
import java.util.stream.Collectors;

import com.kartoflane.itb.modmanager.util.BBCodeParser.BBTag;

import javafx.beans.value.ObservableValue;
import javafx.scene.Node;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.util.Pair;


public class StyledTextBuilder
{
	/**
	 * Creates decorated text from coded input, parsed using {@link BBCodeParser}
	 * 
	 * @param input
	 *            text to be converted to decorated form
	 * @param widthProperty
	 *            width property of the container the pane will be inserted into, allowing
	 *            the pane to layout its children accordingly.
	 * @return {@link TextFlow} instance containing appropriately styled {@link Node}s
	 */
	public static TextFlow build( String input, ObservableValue<? extends Number> widthProperty )
	{
		List<Pair<String, String>> styledChunkList = parse( input );

		return UIUtilities.wrappingTextFlow(
			widthProperty,
			styledChunkList.stream()
				.map( StyledTextBuilder::processStyledChunk )
				.collect( Collectors.toList() )
		);
	}

	/**
	 * @param input
	 *            text to parse, using {@link BBCodeParser}
	 * @return list of pairs, with pair key being the chunk of text, and pair value
	 *         being the JavaFX style that is to be applied to that chunk of text
	 */
	public static List<Pair<String, String>> parse( String input )
	{
		return BBCodeParser.parse( input ).stream()
			.map( entry -> Util.pairOf( entry.getKey(), constructStyleFromBBTags( entry.getValue() ) ) )
			.collect( Collectors.toList() );
	}

	public static Node processStyledChunk( Pair<String, String> styledChunk )
	{
		Text text = new Text( styledChunk.getKey() );
		text.setStyle( styledChunk.getValue() );
		return text;
	}

	public static String tagToStyle( BBTag tag )
	{
		switch ( tag.getTag() ) {
			case BBCodeParser.TAG_BOLD:
				return "-fx-font-weight: bold;";
			case BBCodeParser.TAG_ITALIC:
				return "-fx-font-style: italic;";
			case BBCodeParser.TAG_SIZE:
				return "-fx-font-size: " + Integer.parseInt( tag.getArg() ) + ";";
			case BBCodeParser.TAG_UNDERLINE:
				return "-fx-underline: true;";
			case BBCodeParser.TAG_STRIKETHROUGH:
				return "-fx-strikethrough: true;";
			default:
				return "";
		}
	}

	public static String constructStyleFromBBTags( List<BBTag> bbtags )
	{
		return bbtags.stream()
			.map( StyledTextBuilder::tagToStyle )
			.collect( Collectors.joining() );
	}
}
