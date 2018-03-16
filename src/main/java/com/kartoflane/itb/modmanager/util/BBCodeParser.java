package com.kartoflane.itb.modmanager.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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

	public static final String TAG_BOLD = "b";
	public static final String TAG_ITALIC = "i";
	public static final String TAG_SIZE = "size";
	public static final String TAG_UNDERLINE = "u";
	public static final String TAG_STRIKETHROUGH = "s";


	/**
	 * @param input
	 *            text to parse
	 * @return list of pairs, with pair key being the chunk of text to be styled,
	 *         and value being a list of bbtags which are to be applied to this
	 *         chunk of text.
	 */
	public static List<Entry<String, List<BBTag>>> parse( String input )
	{
		if ( input == null || input.isEmpty() ) {
			return Collections.emptyList();
		}

		Matcher bbMatcher = BB_PTN.matcher( input );
		Matcher textMatcher = TEXT_PTN.matcher( input );

		List<BBTag> bbTags = new ArrayList<>();
		List<Entry<String, List<BBTag>>> chunks = new ArrayList<>();

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
					Util.entryOf(
						textMatcher.group(),
						new ArrayList<>( bbTags )
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


	public static class BBTag
	{
		private String tag;
		private String arg;


		public BBTag( String tag, String arg )
		{
			this.tag = tag.toLowerCase( Locale.ENGLISH );
			this.arg = arg;
		}

		public String getTag()
		{
			return tag;
		}

		public String getArg()
		{
			return arg;
		}
	}
}
