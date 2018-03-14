package com.kartoflane.itb.modmanager.lua;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;


/**
 * A writer that can output simple Lua objects.
 * Outputs:
 * - null values as nil
 * - integers, chars, longs, floats and doubles
 * - boolean values
 * - strings in double quotes
 * - maps as tables with named values (only with string keys; strings
 * are checked whether they're valid Lua identifiers)
 * - lists and arrays as tables with unnamed values
 */
public class LuaWriter
{
	private static final String identifierPtn = "([a-zA-Z_][a-zA-Z0-9_]*)";
	private static final List<String> keywords = Arrays.asList(
		new String[] {
			"and", "end", "in", "repeat", "break", "false", "local", "return", "do",
			"for", "nil", "then", "else", "function", "not", "true", "elseif", "if",
			"or", "until", "while"
		}
	);


	public static String toLuaString( Object o )
	{
		StringBuilder buf = new StringBuilder();

		appendObject( buf, o, 0 );

		return buf.toString();
	}

	@SuppressWarnings("unchecked")
	private static StringBuilder appendObject( StringBuilder buf, Object o, int indent )
	{
		if ( o == null ) {
			buf.append( "nil" );
		}
		else if ( o instanceof Character ) {
			buf.append( (Character)o );
		}
		else if ( o instanceof Integer ) {
			buf.append( (Integer)o );
		}
		else if ( o instanceof Long ) {
			buf.append( (Long)o );
		}
		else if ( o instanceof Boolean ) {
			buf.append( (Boolean)o );
		}
		else if ( o instanceof Float ) {
			buf.append( (Float)o );
		}
		else if ( o instanceof Double ) {
			buf.append( (Double)o );
		}
		else if ( o instanceof String ) {
			appendString( buf, (String)o, indent );
		}
		else if ( o instanceof Map<?, ?> ) {
			appendTable( buf, (Map<String, ?>)o, indent );
		}
		else if ( o instanceof Collection<?> ) {
			appendArray( buf, (Collection<?>)o, indent );
		}
		else if ( o.getClass().isArray() ) {
			appendArray( buf, Arrays.asList( (Object[])o ), indent );
		}
		else {
			throw new IllegalArgumentException( "Don't know how to print: " + o.getClass() );
		}

		return buf;
	}

	private static StringBuilder appendTable( StringBuilder buf, Map<String, ?> table, int indent )
	{
		buf.append( '{' );

		if ( !table.isEmpty() ) {
			buf.append( '\n' );
			++indent;
			for ( Entry<String, ?> entry : table.entrySet() ) {
				checkValidTableKey( entry.getKey() );

				appendIndent( buf, indent );
				buf.append( entry.getKey() ).append( " = " );
				appendObject( buf, entry.getValue(), indent ).append( ",\n" );
			}
			--indent;

			appendIndent( buf, indent );
		}

		buf.append( '}' );

		return buf;
	}

	private static StringBuilder appendArray( StringBuilder buf, Collection<?> array, int indent )
	{
		buf.append( '{' );

		if ( !array.isEmpty() ) {
			buf.append( '\n' );
			++indent;
			for ( Object entry : array ) {
				appendIndent( buf, indent );
				appendObject( buf, entry, indent ).append( ",\n" );
			}
			--indent;

			appendIndent( buf, indent );
		}

		buf.append( '}' );

		return buf;
	}

	private static StringBuilder appendString( StringBuilder buf, String string, int indent )
	{
		// Normalize and escape newlines
		string = string.replaceAll( "\r?\n", "\\\\n" );
		return buf.append( '\"' ).append( string ).append( '\"' );
	}

	private static StringBuilder appendIndent( StringBuilder buf, int indentLevel )
	{
		for ( int i = 0; i < indentLevel; ++i ) {
			buf.append( '\t' );
		}

		return buf;
	}

	private static void checkValidTableKey( String key )
	{
		if ( !key.matches( identifierPtn ) || keywords.contains( key ) ) {
			throw new IllegalArgumentException( "Not a valid Lua identifier: " + key );
		}
	}
}
