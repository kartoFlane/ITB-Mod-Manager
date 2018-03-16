package com.kartoflane.itb.modmanager.patcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import net.vhati.ftldat.AbstractPack;


/**
 * Handles FMOD .bank files? Or .wav / .mp3 files and includes them in the .bank somehow?
 */
public class FMODPatcher implements ResourcePatcher
{
	public FMODPatcher()
	{
		// TODO FMOD patcher
	}

	public String normalizeInnerPath( File modFile, String innerPath, String parentPath, String root, String fileName )
	{
		// TODO FMOD patcher
		throw new UnsupportedOperationException( "Not yet implemented" );
	}

	public void patch( AbstractPack pack, String innerPath, InputStream is ) throws IOException
	{
		// TODO FMOD patcher
		throw new UnsupportedOperationException( "Not yet implemented" );
	}
}
