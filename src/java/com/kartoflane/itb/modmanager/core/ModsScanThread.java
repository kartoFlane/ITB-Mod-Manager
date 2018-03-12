package com.kartoflane.itb.modmanager.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.kartoflane.itb.modmanager.event.Event;
import com.kartoflane.itb.modmanager.event.EventCallback;
import com.kartoflane.itb.modmanager.event.EventDouble;
import com.kartoflane.itb.modmanager.event.EventSingle;
import com.kartoflane.itb.modmanager.lua.LuaModMetadataReader;

import net.vhati.ftldat.PackUtilities;
import net.vhati.modmanager.core.ModDB;
import net.vhati.modmanager.core.ModInfo;


/**
 * A thread to calculate MD5 hashes of files in the background.
 *
 * As each file is hashed, a class implementing HashObserver is notified.
 * Note: The callback on that class needs to be thread-safe.
 */
public class ModsScanThread extends Thread
{
	private static final Logger log = LogManager.getLogger();

	private final EventDouble<File, String> hashCalculated = Event.create( null, null );
	private final EventSingle<ModDB> localModDBUpdated = Event.create( null );
	private final EventCallback scanEnded = Event.create();

	private List<File> fileList = new ArrayList<File>();
	private ModDB newDB;


	public ModsScanThread( File[] files, ModDB knownDB )
	{
		super( "scan" );
		this.fileList.addAll( Arrays.asList( files ) );
		this.newDB = new ModDB( knownDB );
	}

	public Event<BiConsumer<File, String>> hashCalculatedEvent()
	{
		return hashCalculated;
	}

	public Event<Consumer<ModDB>> localModDBUpdatedEvent()
	{
		return localModDBUpdated;
	}

	public Event<Runnable> scanEndedEvent()
	{
		return scanEnded;
	}

	public void run()
	{
		Map<File, String> hashMap = new HashMap<File, String>();

		for ( File f : fileList ) {
			String hash = calcFileMD5( f );
			if ( hash != null ) {
				hashMap.put( f, hash );
				hashCalculated.broadcast( f, hash );
			}
		}
		log.info( "Background hashing finished." );

		// Cache info about new files.
		for ( File f : fileList ) {
			String fileHash = hashMap.get( f );

			if ( fileHash != null && newDB.getModInfo( fileHash ) == null ) {
				ModInfo modInfo = LuaModMetadataReader.parseModFile( f );
				if ( modInfo != null ) {
					modInfo.setFileHash( fileHash );
					newDB.addMod( modInfo );
				}
			}
		}

		// Prune info about absent files.
		for ( Iterator<ModInfo> it = newDB.getCatalog().iterator(); it.hasNext(); ) {
			ModInfo modInfo = it.next();
			if ( !hashMap.containsValue( modInfo.getFileHash() ) )
				it.remove();
		}

		localModDBUpdated.broadcast( new ModDB( newDB ) );
		log.info( "Background metadata caching finished." );

		scanEnded.broadcast();

		// Cleanup listeners
		hashCalculated.clearListeners();
		localModDBUpdated.clearListeners();
		scanEnded.clearListeners();
	}

	private String calcFileMD5( File f )
	{
		String result = null;
		try {
			result = PackUtilities.calcFileMD5( f );
		}
		catch ( Exception e ) {
			log.error( "Error while calculating hash for file: " + f.getPath(), e );
		}
		return result;
	}
}
