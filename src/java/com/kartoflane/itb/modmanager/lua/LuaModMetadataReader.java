package com.kartoflane.itb.modmanager.lua;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;

import net.vhati.modmanager.core.ModInfo;
import net.vhati.modmanager.core.ModUtilities;


/**
 * Reader for mod metadata file, in lua format.
 * 
 * Based on net.vhati.modmanager.xml.JDOMModMetadataReader
 */
public class LuaModMetadataReader
{
	private static final Logger log = LogManager.getLogger();

	public static final String METADATA_INNERPATH = "mod-appendix/metadata.lua";


	public static ModInfo parseModFile( File modFile )
	{
		ModInfo modInfo = null;

		InputStream fis = null;
		ZipInputStream zis = null;
		Exception exception = null;
		try {
			fis = new FileInputStream( modFile );
			zis = new ZipInputStream( new BufferedInputStream( fis ) );
			ZipEntry item;
			while ( ( item = zis.getNextEntry() ) != null ) {
				if ( item.isDirectory() ) {
					zis.closeEntry();
					continue;
				}

				String innerPath = item.getName();
				innerPath = innerPath.replace( '\\', '/' );  // Non-standard zips.

				if ( innerPath.equals( METADATA_INNERPATH ) ) {
					String metadataText = ModUtilities.decodeText( zis, modFile.getName() + ":" + METADATA_INNERPATH ).text;
					modInfo = parse( metadataText );

					zis.closeEntry();
					break;
				}

				zis.closeEntry();
			}
		}
		catch ( LuaError e ) {
			exception = e;
		}
		catch ( IOException e ) {
			exception = e;
		}
		finally {
			try {
				if ( zis != null ) zis.close();
			}
			catch ( IOException e ) {
			}

			try {
				if ( fis != null ) fis.close();
			}
			catch ( IOException e ) {
			}
		}
		if ( exception != null ) {
			log.error(
				String.format(
					"While processing \"%s:%s\", parsing failed.\n",
					modFile.getName(), METADATA_INNERPATH
				), exception
			);
			return null;
		}

		if ( modInfo == null ) modInfo = new ModInfo();
		return modInfo;
	}

	/**
	 * Reads a mod's metadata.lua and returns a ModInfo object.
	 */
	public static ModInfo parse( String metadataText ) throws IOException, LuaError
	{
		ModInfo modInfo = new ModInfo();

		LuaLoader parser = LuaLoader.minimal();
		LuaTable root = parser.loadAsTable( metadataText, "metadata" );

		String modTitle = getTableValueTrim( root, "title" );
		if ( modTitle != null && modTitle.length() > 0 )
			modInfo.setTitle( modTitle );
		else
			throw new LuaError( "Missing title." );

		String modURL = getTableValueTrim( root, "threadUrl" );
		if ( modURL != null && modURL.length() > 0 )
			modInfo.setURL( modURL );
		else
			throw new LuaError( "Missing threadUrl." );

		String modAuthor = getTableValueTrim( root, "author" );
		if ( modAuthor != null && modAuthor.length() > 0 )
			modInfo.setAuthor( modAuthor );
		else
			throw new LuaError( "Missing author." );

		String modVersion = getTableValueTrim( root, "version" );
		if ( modVersion != null && modVersion.length() > 0 )
			modInfo.setVersion( modVersion );
		else
			throw new LuaError( "Missing version." );

		String modDesc = getTableValueTrim( root, "description" );
		if ( modDesc != null && modDesc.length() > 0 )
			modInfo.setDescription( modDesc );
		else
			throw new LuaError( "Missing description." );

		return modInfo;
	}

	private static String getTableValueTrim( LuaTable table, String key )
	{
		String result = table.get( key ).optjstring( null );
		return result == null ? null : result.trim();
	}
}
