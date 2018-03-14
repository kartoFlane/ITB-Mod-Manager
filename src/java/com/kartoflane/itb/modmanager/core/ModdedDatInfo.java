package com.kartoflane.itb.modmanager.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import com.kartoflane.itb.modmanager.lua.LuaLoader;
import com.kartoflane.itb.modmanager.lua.LuaWriter;
import com.kartoflane.itb.modmanager.util.Util;

import net.vhati.ftldat.AbstractPack;
import net.vhati.ftldat.FTLPack;
import net.vhati.modmanager.core.ModInfo;


public class ModdedDatInfo
{
	private String originalHash;
	private List<String> installedModNames;
	private List<String> installedModHashes;


	private ModdedDatInfo()
	{
		installedModNames = new ArrayList<>();
		installedModHashes = new ArrayList<>();
	}

	public ModdedDatInfo( String hash )
	{
		originalHash = hash;
	}

	public void addModInfo( ModInfo modInfo )
	{
		installedModNames.add( modInfo.getTitle() );
		installedModHashes.add( modInfo.getFileHash() );
	}

	/**
	 * Returns hash of the original, unmodded .dat
	 */
	public String getOriginalHash()
	{
		return originalHash;
	}

	public List<String> listInstalledModNames()
	{
		return Collections.unmodifiableList( installedModNames );
	}

	public List<String> listInstalledModHashes()
	{
		return Collections.unmodifiableList( installedModHashes );
	}

	public List<Entry<String, String>> listInstalledMods()
	{
		return IntStream.range( 0, installedModNames.size() )
			.mapToObj( i -> Util.entryOf( installedModNames.get( i ), installedModHashes.get( i ) ) )
			.collect( Collectors.toList() );
	}

	/**
	 * Returns a string containing the Lua object representation of the specified ModdedDatInfo.
	 */
	public String toLuaString()
	{
		Map<String, Object> root = new LinkedHashMap<>();

		Map<String, Object> v1 = new LinkedHashMap<>();
		v1.put( "original_hash", originalHash );

		List<Map<String, String>> installedMods = new ArrayList<>( installedModNames.size() );
		for ( int i = 0; i < installedModNames.size(); i++ ) {
			Map<String, String> modInfo = new LinkedHashMap<>();
			modInfo.put( "name", installedModNames.get( i ) );
			modInfo.put( "hash", installedModHashes.get( i ) );
			installedMods.add( modInfo );
		}
		v1.put( "installed_mods", installedMods );

		root.put( "v1", v1 );

		return LuaWriter.toLuaString( root );
	}

	/**
	 * Builds an instance of {@link #ModdedDatInfo} from the modded-info file from the specified pack.
	 */
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
				mi.installedModNames.add( table.get( "name" ).checkjstring() );
				mi.installedModHashes.add( table.get( "hash" ).checkjstring() );
			}

			return mi;
		}
	}

	/**
	 * Builds an instance of {@link #ModdedDatInfo} from the modded-info file from the specified .dat archive.
	 */
	public static ModdedDatInfo build( File datFile, String infoFileInnerPath ) throws IOException
	{
		try ( AbstractPack datPack = new FTLPack( datFile, "r" ) ) {
			return build( datPack, infoFileInnerPath );
		}
	}
}
