package com.kartoflane.itb.modmanager.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javafx.util.Pair;


/**
 * Parser for text using a kind of BBCode-like syntax to achieve decorated text.
 * 
 * Currently supports:
 * - bold via [b][/b]
 * - italic via [i][/i]
 * - size via [size=#][/size]
 * - underline via [u][/u]
 * - strikethough via [s][/s]
 * 
 * @author kartoFlane
 */
public class BBCodeParser
{
	private static final Pattern BB_PTN = Pattern.compile( "\\[\\/?[^\\]]+\\]" );
	private static final Pattern TEXT_PTN = Pattern.compile( "[^\\[\\]]+" );

	private static final String TAG_BOLD = "b";
	private static final String TAG_ITALIC = "i";
	private static final String TAG_SIZE = "size";
	private static final String TAG_UNDERLINE = "u";
	private static final String TAG_STRIKETHROUGH = "s";


	/**
	 * @param input
	 *            text to parse
	 * @return list of pairs, with pair key being the chunk of text, and pair value
	 *         being the JavaFX style that is to be applied to that chunk of text
	 */
	public static List<Pair<String, String>> parse( String input )
	{
		if ( input == null || input.isEmpty() ) {
			return Collections.emptyList();
		}

		Matcher bbMatcher = BB_PTN.matcher( input );
		Matcher textMatcher = TEXT_PTN.matcher( input );

		List<BBTag> bbTags = new ArrayList<>();
		List<Pair<String, String>> chunks = new ArrayList<>();

		int start = 0;
		int end = input.length();
		while ( start <= end ) {
			int i = -1;
			int j = -1;

			// Decide which matcher to try first
			if ( bbMatcher.find( start ) ) i = bbMatcher.start();
			if ( textMatcher.find( start ) ) j = textMatcher.start();

			if ( i != -1 && ( i < j || j == -1 ) ) {
				String tag = bbMatcher.group();
				start = bbMatcher.start() + tag.length();

				boolean closing = tag.startsWith( "[/" );
				tag = tag.replaceAll( "[\\[\\]\\/]", "" );

				if ( closing ) {
					final String key = tag;
					Util.removeFirstIf( bbTags, e -> e.tag.equals( key ) );
				}
				else {
					String arg = null;
					int idx = tag.indexOf( '=' );
					if ( idx >= 0 ) {
						arg = tag.substring( idx + 1 );
						tag = tag.substring( 0, idx );
					}
					bbTags.add( new BBTag( tag, arg ) );
				}
			}
			else if ( j != -1 && ( i > j || i == -1 ) ) {
				start = textMatcher.end();

				chunks.add(
					Util.pairOf(
						textMatcher.group(),
						constructStyleFromTagArgs( bbTags )
					)
				);
			}
			else {
				// Neither matcher matched remaining content? End of string, I guess.
				break;
			}
		}

		return chunks;
	}

	private static String tagToStyle( BBTag tag )
	{
		if ( tag.tag.equals( TAG_BOLD ) ) {
			return "-fx-font-weight: bold;";
		}
		else if ( tag.tag.equals( TAG_ITALIC ) ) {
			return "-fx-font-style: italic;";
		}
		else if ( tag.tag.equals( TAG_SIZE ) ) {
			return "-fx-font-size: " + Integer.parseInt( tag.arg ) + ";";
		}
		else if ( tag.tag.equals( TAG_UNDERLINE ) ) {
			return "-fx-underline: true;";
		}
		else if ( tag.tag.equals( TAG_STRIKETHROUGH ) ) {
			return "-fx-strikethrough: true;";
		}
		else {
			return "";
		}
	}

	private static String constructStyleFromTagArgs( List<BBTag> bbtags )
	{
		return bbtags.stream()
			.map( BBCodeParser::tagToStyle )
			.collect( Collectors.joining() );
	}


	private static class BBTag
	{
		private String tag;
		private String arg;


		public BBTag( String tag, String arg )
		{
			this.tag = tag.toLowerCase( Locale.ENGLISH );
			this.arg = arg;
		}
	}
}
