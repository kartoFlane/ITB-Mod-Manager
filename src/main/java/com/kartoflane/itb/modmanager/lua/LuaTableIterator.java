package com.kartoflane.itb.modmanager.lua;

import java.util.AbstractMap.SimpleEntry;
import java.util.Iterator;
import java.util.Map.Entry;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;


public class LuaTableIterator implements Iterator<Entry<LuaValue, LuaValue>>
{
	protected LuaTable table;
	protected LuaValue currentKey = LuaValue.NIL;


	public LuaTableIterator( LuaTable table )
	{
		this.table = table;
	}

	@Override
	public boolean hasNext()
	{
		return !table.next( currentKey ).arg1().isnil();
	}

	@Override
	public Entry<LuaValue, LuaValue> next()
	{
		Varargs n = table.next( currentKey );
		currentKey = n.arg1();
		return new SimpleEntry<>( currentKey, n.arg( 2 ) );
	}
}
