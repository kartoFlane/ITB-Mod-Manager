package com.kartoflane.itb.modmanager.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import com.kartoflane.itb.modmanager.lua.LuaLoader;
import com.kartoflane.itb.modmanager.lua.LuaWriter;

import net.vhati.ftldat.AbstractPack;
import net.vhati.ftldat.FTLPack;


public class ModdedDatInfo
{
	/** Hash of the original, unmodded .dat */
	public String originalHash;
	public List<String> installedModsNames;
	public List<String> installedModsHashes;


	public ModdedDatInfo()
	{
		installedModsNames = new ArrayList<>();
		installedModsHashes = new ArrayList<>();
	}

	/**
	 * Returns a string containing the Lua object representation of the specified ModdedDatInfo.
	 */
	public String toLuaString()
	{
		Map<String, Object> root = new LinkedHashMap<>();

		Map<String, Object> v1 = new LinkedHashMap<>();
		v1.put( "original_hash", originalHash );

		List<Map<String, String>> installedMods = new ArrayList<>( installedModsNames.size() );
		for ( int i = 0; i < installedModsNames.size(); i++ ) {
			Map<String, String> modInfo = new LinkedHashMap<>();
			modInfo.put( "name", installedModsNames.get( i ) );
			modInfo.put( "hash", installedModsHashes.get( i ) );
			installedMods.add( modInfo );
		}
		v1.put( "installed_mods", installedMods );

		root.put( "v1", v1 );

		return LuaWriter.toLuaString( root );
	}

	public static ModdedDatInfo build( AbstractPack datPack, String infoFileInnerPath ) throws IOException
	{
		try ( InputStream is = datPack.getInputStream( infoFileInnerPath ) ) {
			ModdedDatInfo mi = new ModdedDatInfo();

			LuaLoader parser = LuaLoader.minimal();
			LuaTable root = parser.loadAsTable( is, infoFileInnerPath );

			LuaTable v1Node = root.get( "v1" ).checktable();

			mi.originalHash = v1Node.get( "original_hash" ).checkjstring();

			LuaTable modsNode = v1Node.get( "installed_mods" ).checktable();
			for ( LuaValue value : LuaLoader.values( modsNode ) ) {
				LuaTable table = value.checktable();
				mi.installedModsNames.add( table.get( "name" ).checkjstring() );
				mi.installedModsHashes.add( table.get( "hash" ).checkjstring() );
			}

			return mi;
		}
	}

	public static ModdedDatInfo build( File datFile, String infoFileInnerPath ) throws IOException
	{
		try ( AbstractPack datPack = new FTLPack( datFile, "r" ) ) {
			return build( datFile, infoFileInnerPath );
		}
	}
}
