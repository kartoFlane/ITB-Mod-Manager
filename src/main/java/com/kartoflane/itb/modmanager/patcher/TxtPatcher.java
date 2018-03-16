package com.kartoflane.itb.modmanager.patcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;

import net.vhati.ftldat.AbstractPack;
import net.vhati.modmanager.core.ModUtilities;


/**
 * Patcher for text files, which rewrites the resources using standardized line endings
 * and encoding, provided as argument to the constructor.
 */
public class TxtPatcher implements ResourcePatcher
{
	private final Logger log;
	private final String ultimateEncoding;
	private final List<String> moddedItems;


	public TxtPatcher( Logger log, String ultimateEncoding, List<String> moddedItems )
	{
		this.log = log;
		this.ultimateEncoding = ultimateEncoding;
		this.moddedItems = moddedItems;
	}

	public String normalizeInnerPath( File modFile, String innerPath, String parentPath, String root, String fileName )
	{
		return innerPath;
	}

	public void patch( AbstractPack pack, String innerPath, InputStream is ) throws IOException
	{
		// Normalize line endings for other text files to CR-LF.
		// decodeText() reads anything and returns an LF string.
		String fixedText = ModUtilities.decodeText( is, innerPath ).text;
		fixedText = Pattern.compile( "\n" ).matcher( fixedText ).replaceAll( "\r\n" );

		InputStream fixedStream = ModUtilities.encodeText( fixedText, ultimateEncoding, innerPath + " (with new EOL)" );

		if ( !moddedItems.contains( innerPath ) ) {
			moddedItems.add( innerPath );
		}
		else {
			log.warn( String.format( "Clobbering earlier mods: %s", innerPath ) );
		}

		if ( pack.contains( innerPath ) )
			pack.remove( innerPath );
		pack.add( innerPath, fixedStream );
	}
}
