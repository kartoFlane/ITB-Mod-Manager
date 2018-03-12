package com.kartoflane.itb.modmanager.patcher;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import net.vhati.ftldat.AbstractPack;


public interface ResourcePatcher
{
	/**
	 * Normalizes the innerpath. Useful fro when the patcher accepts files
	 * with several different extensions (as was the case with .append etc
	 * formats in FTL -- this method would be used to normalize them back
	 * to .xml).
	 * 
	 * @param modFile
	 *            the original mod file from which the resource comes
	 * @param innerPath
	 *            the innerPath to normalize
	 * @param parentPath
	 *            path to the immediate parent of the resource file,
	 *            obtained from the innerPath
	 *            (root/grandparent/immediate_parent/resource.ext)
	 * @param root
	 *            name of the topmost parent of the resource file,
	 *            obtained from the innerPath
	 *            (root/grandparent/immediate_parent/resource.ext)
	 * @param fileName
	 *            name of the resource file, obtained from the innerPath
	 * @return normalized innerPath
	 */
	public String normalizeInnerPath( File modFile, String innerPath, String parentPath, String root, String fileName );

	/**
	 * @param pack
	 *            the pack the patched resource will be added to
	 * @param innerPath
	 *            complete innerPath of the resource within the mod file
	 * @param is
	 *            input stream of the resource file
	 */
	public void patch( AbstractPack pack, String innerPath, InputStream is ) throws IOException;
}
