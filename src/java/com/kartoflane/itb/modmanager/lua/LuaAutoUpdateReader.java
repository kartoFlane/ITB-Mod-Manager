package com.kartoflane.itb.modmanager.lua;

import java.io.File;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import net.vhati.modmanager.core.AutoUpdateInfo;
import net.vhati.modmanager.core.ComparableVersion;


public class LuaAutoUpdateReader
{
	private static final Logger log = LogManager.getLogger();


	public static AutoUpdateInfo parse( File luaFile )
	{
		try {
			AutoUpdateInfo aui = new AutoUpdateInfo();

			LuaLoader parser = LuaLoader.minimal();
			LuaTable root = parser.loadFileAsTable( luaFile );
			
			LuaTable v1Node = root.get( "v1" ).checktable();

			LuaTable latestNode = v1Node.get( "latest" ).checktable();
			aui.setLatestVersion( new ComparableVersion( latestNode.get( "version" ).checkjstring() ) );

			LuaTable urlsNode = latestNode.get( "urls" ).checktable();
			for ( Entry<LuaValue, LuaValue> entry : LuaLoader.entrySet( urlsNode ) ) {
				aui.putLatestURL( entry.getKey().checkjstring(), entry.getValue().checkjstring() );
			}

			aui.setNotice( latestNode.get( "notice" ).optjstring( null ) );

			LuaTable changelogNode = v1Node.get( "changelog" ).checktable();
			for ( LuaTable releaseNode : LuaLoader.values( changelogNode, LuaValue::checktable ) ) {
				// Skip any versions with optional "hidden" field set to true
				boolean hidden = releaseNode.get( "hidden" ).optboolean( false );
				if ( hidden ) {
					continue;
				}

				LuaTable changesNode = releaseNode.get( "changes" ).checktable();
				List<String> changeList = LuaLoader.stream( changesNode )
					.map( entry -> entry.getValue().checkjstring() )
					.collect( Collectors.toList() );

				String releaseVersion = releaseNode.get( "version" ).checkjstring();
				aui.putChanges( new ComparableVersion( releaseVersion ), changeList );
			}

			return aui;
		}
		catch ( Exception e ) {
			log.error( "Failed to parse info about available updates:\n", e );
		}

		return null;
	}
}
