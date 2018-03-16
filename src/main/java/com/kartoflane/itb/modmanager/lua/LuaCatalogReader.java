package com.kartoflane.itb.modmanager.lua;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;

import net.vhati.modmanager.core.ModDB;
import net.vhati.modmanager.core.ModInfo;


public class LuaCatalogReader
{
	private static final Logger log = LogManager.getLogger();


	public static ModDB parse( File luaFile )
	{
		try {
			ModDB modDB = new ModDB();

			LuaLoader parser = LuaLoader.minimal();
			LuaTable root = parser.loadFileAsTable( luaFile );

			LuaTable v1Node = root.get( "v1" ).checktable();

			for ( LuaValue infoNode : LuaLoader.values( v1Node ) ) {
				String threadURL = infoNode.get( "url" ).optjstring( "???" );
				String threadHash = infoNode.get( "thread_hash" ).optjstring( "???" );
				if ( !"???".equals( threadURL ) && !"???".equals( threadHash ) )
					modDB.putThreadHash( threadURL, threadHash );

				LuaTable versionsNode = infoNode.get( "versions" ).checktable();
				for ( LuaValue versionNode : LuaLoader.values( versionsNode ) ) {
					ModInfo modInfo = new ModInfo();

					modInfo.setTitle( infoNode.get( "title" ).optjstring( "???" ) );
					modInfo.setAuthor( infoNode.get( "author" ).optjstring( "???" ) );
					modInfo.setURL( infoNode.get( "url" ).optjstring( "???" ) );
					modInfo.setDescription( infoNode.get( "desc" ).optjstring( "???" ) );

					modInfo.setFileHash( versionNode.get( "hash" ).optjstring( "???" ) );
					modInfo.setVersion( versionNode.get( "version" ).optjstring( "???" ) );

					modDB.addMod( modInfo );
				}
			}

			return modDB;
		}
		catch ( Exception e ) {
			log.error( String.format( "While processing %s, lua parsing failed.", luaFile.getName() ), e );
		}

		return null;
	}
}
