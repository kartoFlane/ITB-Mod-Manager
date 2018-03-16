package com.kartoflane.itb.modmanager.lua;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.jse.JsePlatform;

import net.vhati.modmanager.core.ModUtilities;


/**
 * A minimal lua environment used to parse and load all lua data files read by the manager.
 * 
 * Using lua for such a trivial task might be a bit of an overkill, but if ITB modding
 * is going to use that language, we might as well use it here, too.
 */
public class LuaLoader
{
	private Globals globals;


	private LuaLoader()
	{
		globals = new Globals();
	}

	public static LuaLoader minimal()
	{
		LuaLoader loader = new LuaLoader();
		LuaC.install( loader.globals );
		return loader;
	}

	public static LuaLoader standard()
	{
		LuaLoader parser = new LuaLoader();
		parser.globals = JsePlatform.standardGlobals();
		return parser;
	}

	/**
	 * The environment with which the scripts will be loaded.
	 */
	public Globals getEnvironment()
	{
		return globals;
	}

	private String readFile( File file ) throws FileNotFoundException, IOException
	{
		return Files.readAllLines( file.toPath(), StandardCharsets.UTF_8 ).stream()
			.collect( Collectors.joining( "\n" ) );
	}

	public LuaResult loadFile( File file ) throws FileNotFoundException, IOException, LuaError
	{
		return load( readFile( file ), file.getName() );
	}

	public LuaTable loadFileAsTable( File file ) throws FileNotFoundException, IOException, LuaError
	{
		return load( "return " + readFile( file ), file.getName() ).returnValue.checktable();
	}

	public LuaResult load( String script, String chunkName ) throws LuaError
	{
		Globals g = new Globals();
		return new LuaResult( g, globals.load( script, chunkName, g ) );
	}

	public LuaTable loadAsTable( String script, String chunkName ) throws LuaError
	{
		return load( "return " + script, chunkName ).returnValue.checktable();
	}

	public LuaResult load( InputStream stream, String chunkName ) throws IOException, LuaError
	{
		return load( ModUtilities.decodeText( stream, chunkName ).text, chunkName );
	}

	public LuaTable loadAsTable( InputStream stream, String chunkName ) throws IOException, LuaError
	{
		return load( "return " + ModUtilities.decodeText( stream, chunkName ).text, chunkName ).returnValue.checktable();
	}

	// ------------------------------------------------------------------------------

	/**
	 * Returns a wrapper around a LuaTable instance, which implements {@link Iterable},
	 * allowing iteration over the table's entries.
	 */
	public static IterableLuaTable iterable( LuaTable table )
	{
		return new IterableLuaTable( table );
	}

	/**
	 * Returns an iterator over the table's entries.
	 */
	public static Iterator<Entry<LuaValue, LuaValue>> iterator( LuaTable table )
	{
		return new LuaTableIterator( table );
	}

	/**
	 * Returns a {@link Stream} over the table's entries.
	 * 
	 * @param table
	 * @return
	 */
	public static Stream<Entry<LuaValue, LuaValue>> stream( LuaTable table )
	{
		return StreamSupport.stream(
			Spliterators.spliteratorUnknownSize( new LuaTableIterator( table ), Spliterator.ORDERED ),
			false
		);
	}

	/**
	 * Returns a set containing the table's entries.
	 */
	public static Set<Entry<LuaValue, LuaValue>> entrySet( LuaTable table )
	{
		return Collections.unmodifiableSet(
			stream( table ).collect( Collectors.toSet() )
		);
	}

	/**
	 * Returns a set containing the table's keys.
	 * If the table is an array, then this collection contains array indices.
	 * If the table is a map, then this collection contains actual keys.
	 */
	public static Set<LuaValue> keys( LuaTable table )
	{
		return Collections.unmodifiableSet(
			stream( table ).map( entry -> entry.getKey() ).collect( Collectors.toSet() )
		);
	}

	/**
	 * Returns a list containing the table's values.
	 */
	public static Collection<LuaValue> values( LuaTable table )
	{
		return Collections.unmodifiableList(
			stream( table ).map( entry -> entry.getValue() ).collect( Collectors.toList() )
		);
	}

	/**
	 * Returns a list containing the table's values, coverted from {@link LuaValue} instance to the specified
	 * type using the specified converter function.
	 */
	public static <T extends LuaValue> Collection<T> values( LuaTable table, Function<LuaValue, T> converter )
	{
		return Collections.unmodifiableList(
			stream( table ).map( entry -> converter.apply( entry.getValue() ) ).collect( Collectors.toList() )
		);
	}
}
