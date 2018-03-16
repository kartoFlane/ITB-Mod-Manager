package com.kartoflane.itb.modmanager.lua;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.vhati.modmanager.core.ModsInfo;


public class LuaCatalogWriter
{
	public static void write( List<ModsInfo> modsInfoList, File dstFile ) throws IOException
	{
		Map<String, Object> root = new LinkedHashMap<>();

		List<Object> modsArray = new ArrayList<>( modsInfoList.size() );
		for ( ModsInfo modsInfo : modsInfoList ) {
			Map<String, Object> modTable = new LinkedHashMap<>();

			modTable.put( "title", modsInfo.getTitle() );
			modTable.put( "author", modsInfo.getAuthor() );
			modTable.put( "desc", modsInfo.getDescription() );
			modTable.put( "url", modsInfo.getThreadURL() );
			modTable.put( "thread_hash", modsInfo.getThreadHash() );

			List<Object> versionsArray = new ArrayList<>( modsInfo.getVersionsMap().size() );
			for ( Map.Entry<String, String> entry : modsInfo.getVersionsMap().entrySet() ) {
				Map<String, Object> versionInfo = new LinkedHashMap<>();
				versionInfo.put( "hash", entry.getKey() );
				versionInfo.put( "version", entry.getValue() );
				versionsArray.add( versionInfo );
			}

			modTable.put( "versions", versionsArray );
			modsArray.add( modTable );
		}

		root.put( "v1", modsArray );

		try (
			BufferedWriter bw = new BufferedWriter(
				new OutputStreamWriter( new FileOutputStream( dstFile ), StandardCharsets.UTF_8 )
			)
		) {
			bw.write( LuaWriter.toLuaString( root ) );
			bw.flush();
		}
	}
}
