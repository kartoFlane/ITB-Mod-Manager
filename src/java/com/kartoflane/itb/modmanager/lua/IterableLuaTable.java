package com.kartoflane.itb.modmanager.lua;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;


// TODO: Make this extend LuaTable, so that we can use this as a drop-in replacement.
public class IterableLuaTable implements Iterable<Entry<LuaValue, LuaValue>>
{
	private LuaTable table = null;


	public IterableLuaTable( LuaTable sourceTable )
	{
		this.table = sourceTable;
	}

	public Stream<Entry<LuaValue, LuaValue>> stream()
	{
		return StreamSupport.stream( spliterator(), false );
	}

	public Set<Entry<LuaValue, LuaValue>> entrySet()
	{
		return Collections.unmodifiableSet( stream().collect( Collectors.toSet() ) );
	}

	public Set<LuaValue> keys()
	{
		return Collections.unmodifiableSet( stream().map( entry -> entry.getKey() ).collect( Collectors.toSet() ) );
	}

	public Collection<LuaValue> values()
	{
		return Collections.unmodifiableList( stream().map( entry -> entry.getValue() ).collect( Collectors.toList() ) );
	}

	@Override
	public Iterator<Entry<LuaValue, LuaValue>> iterator()
	{
		return new LuaTableIterator( table );
	}
}
