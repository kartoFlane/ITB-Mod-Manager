package com.kartoflane.itb.modmanager.patcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import net.vhati.ftldat.AbstractPack;


/**
 * Patcher for lua script files.
 * Reuses patching functionality implemented by {@link TxtPatcher}.
 * Appends all script files it handles to a list of modded script items.
 * Puts all modded script files inside scripts/mods/[mod_name]
 */
public class LuaPatcher implements ResourcePatcher
{
	private final TxtPatcher txtPatcher;
	private final List<String> scriptsList;


	public LuaPatcher( TxtPatcher txtPatcher, List<String> scriptsList )
	{
		this.txtPatcher = txtPatcher;
		this.scriptsList = scriptsList;
	}

	public String normalizeInnerPath( File modFile, String innerPath, String parentPath, String root, String fileName )
	{
		return String.join( "/", "mods", stripExtension( modFile.getName() ), fileName );
	}

	private String stripExtension( String filename )
	{
		int pos = filename.lastIndexOf( '.' );
		return pos == -1 ? filename : filename.substring( 0, pos );
	}

	public void patch( AbstractPack pack, String innerPath, InputStream is ) throws IOException
	{
		scriptsList.add( innerPath );

		// Reuse text patcher so we don't duplicate code
		txtPatcher.patch( pack, innerPath, is );
	}
}
