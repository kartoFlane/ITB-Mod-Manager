package com.kartoflane.itb.modmanager.patcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.logging.log4j.Logger;

import net.vhati.ftldat.AbstractPack;


/**
 * Default patcher which simply adds resources to the target pack without
 * any additional processing.
 */
public class DefaultPatcher implements ResourcePatcher
{
	private final Logger log;
	private final List<String> moddedItems;


	public DefaultPatcher( Logger log, List<String> moddedItems )
	{
		this.log = log;
		this.moddedItems = moddedItems;
	}

	public String normalizeInnerPath( File modFile, String innerPath, String parentPath, String root, String fileName )
	{
		return innerPath;
	}

	public void patch( AbstractPack pack, String innerPath, InputStream is ) throws IOException
	{
		if ( !moddedItems.contains( innerPath ) ) {
			moddedItems.add( innerPath );
		}
		else {
			log.warn( String.format( "Clobbering earlier mods: %s", innerPath ) );
		}

		if ( pack.contains( innerPath ) )
			pack.remove( innerPath );
		pack.add( innerPath, is );
	}
}
