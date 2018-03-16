package com.kartoflane.itb.modmanager.core;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.kartoflane.itb.modmanager.event.Event;
import com.kartoflane.itb.modmanager.event.EventSingle;
import com.kartoflane.itb.modmanager.lua.LuaCatalogReader;
import com.kartoflane.itb.modmanager.lua.LuaCatalogWriter;
import com.kartoflane.itb.modmanager.util.StyledTextBuilder;
import com.kartoflane.itb.modmanager.util.UIUtilities;

import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Hyperlink;
import javafx.scene.layout.Region;
import javafx.scene.text.Text;
import net.vhati.ftldat.PackUtilities;
import net.vhati.modmanager.core.ModDB;
import net.vhati.modmanager.core.ModFileInfo;
import net.vhati.modmanager.core.ModInfo;
import net.vhati.modmanager.core.ModUtilities;
import net.vhati.modmanager.ui.table.ListState;


public class ModsScanner
{
	private static final Logger log = LogManager.getLogger();

	private final EventSingle<Boolean> scanningStateChanged = new EventSingle<>();
	private final EventSingle<ListState<ModFileInfo>> modsTableStateAmended = new EventSingle<>();

	private final ITBConfig config;
	private final File modsDir;
	private final File modsTableStateFile;
	private final File modsMetadataFile;

	private boolean scanning;
	private final Lock managerLock = new ReentrantLock();
	private final Condition scanEndedCond = managerLock.newCondition();

	private Map<File, String> modFileHashes = new HashMap<File, String>();
	private Map<String, Date> modFileDates = new HashMap<String, Date>();
	private ModDB localModDB = new ModDB();


	public ModsScanner(
		ITBConfig config,
		File modsDir,
		File modsTableStateFile,
		File modMetadataFile
	)
	{
		this.config = config;
		this.modsDir = modsDir;
		this.modsTableStateFile = modsTableStateFile;
		this.modsMetadataFile = modMetadataFile;
	}

	public Event.Single<Boolean> scanningStateChangedEvent()
	{
		return scanningStateChanged;
	}

	public Event.Single<ListState<ModFileInfo>> modsTableStateAmendedEvent()
	{
		return modsTableStateAmended;
	}

	/**
	 * Constructs and returns a info panel for the specified mod, to be inserted in
	 * {@link ManagerFWindow}'s rightContentPane.
	 * 
	 * @param modFileInfo
	 *            the mod file to construct info pane for
	 * @param widthProperty
	 *            width property of the container the pane will be inserted into, allowing
	 *            the pane to layout its children accordingly.
	 */
	public Region buildModInfoPane( ModFileInfo modFileInfo, ObservableValue<? extends Number> widthProperty )
	{
		// TODO: Should probably move this method somewhere else
		String modHash = modFileHashes.get( modFileInfo.getFile() );
		ModInfo modInfo = localModDB.getModInfo( modHash );

		if ( modInfo == null || modInfo.isBlank() ) {
			// NOTE: If we ever decide to bring over the catalog from SMM
			// modInfo = catalogModDB.getModInfo( modHash );
		}

		if ( modInfo != null && !modInfo.isBlank() ) {
			Text title = new Text( modInfo.getTitle() );
			title.setStyle( "-fx-font-weight: bold;" + "-fx-font-size: 20;" );
			Text authorVersion = new Text(
				String.format( "%nCreated by %s (version %s)", modInfo.getAuthor(), modInfo.getVersion() )
			);
			Text website = new Text( "\nWebsite:" );
			Hyperlink hyperlink = UIUtilities.createHyperlink( "Link", modInfo.getURL() );
			Text description = new Text( "\n\n" + modInfo.getDescription() );

			return UIUtilities.wrappingTextFlow( widthProperty, title, authorVersion, website, hyperlink, description );
		}
		else {
			boolean notYetReady = scanning;

			if ( notYetReady ) {
				String body = ""
					+ "No info is currently available for the selected mod\n\n."
					+ "But the mod manager has not yet finished scanning the mods/ folder. "
					+ "Try clicking on this mod again after waiting a few seconds.";

				return StyledTextBuilder.build( body, widthProperty );
			}
			else {
				Date modDate = modFileDates.get( modHash );
				if ( modDate == null ) {
					long epochTime = -1;
					try {
						epochTime = ModUtilities.getModFileTime( modFileInfo.getFile() );
					}
					catch ( IOException e ) {
						log.error( String.format( "Error while getting modified time of mod file contents for \"%s\"", modFileInfo.getFile() ), e );
					}
					if ( epochTime != -1 ) {
						modDate = new Date( epochTime );
						modFileDates.put( modHash, modDate );
					}
				}

				StringBuilder bodyBuf = new StringBuilder();
				bodyBuf.append( "No info is available for the selected mod.\n\n" );

				if ( modDate != null ) {
					SimpleDateFormat dateFormat = new SimpleDateFormat( "yyyy-MM-dd" );
					bodyBuf.append( String.format( "It was released some time after %s.\n\n", dateFormat.format( modDate ) ) );
				}
				else {
					bodyBuf.append( "The date of its release could not be determined.\n\n" );
				}

				bodyBuf.append( "Mods can include an embedded description, but this one did not.\n" );

				return StyledTextBuilder.build( bodyBuf.toString(), widthProperty );
			}
		}
	}

	/**
	 * Returns ModInfo for the specified file.
	 * 
	 * @throws IllegalArgumentException
	 *             if no ModInfo could be found for the specified file
	 */
	public ModInfo getModInfo( File modFile )
	{
		String hash = modFileHashes.get( modFile );
		if ( hash == null ) {
			try {
				hash = PackUtilities.calcFileMD5( modFile );
			}
			catch ( Exception e ) {
			}
		}

		ModInfo modInfo = localModDB.getModInfo( hash );

		if ( modInfo == null ) {
			if ( scanning ) {
				// TODO: Some more intelligent handling of this case; disable patch button while scanning is still in progress?
				throw new IllegalArgumentException( "ModInfo is not yet available for " + modFile.getName() );
			}
			else {
				throw new IllegalArgumentException( "Scanning completed, but no ModInfo available for " + modFile.getName() );
			}
		}

		return modInfo;
	}

	/**
	 * Returns a File instance that matches the specified hash.
	 * Returns null if no matching file could be found.
	 * 
	 * Important: this method simply iterates over the ModsScanner's
	 * internal Map of files-to-hashes, which is built on a separate
	 * thread when the mod manager is started. Depending on the number
	 * and size of mod files, this Map may not hold all the values
	 * yet by the time you want to call this method.
	 */
	public File getFileForHash( String hash )
	{
		return modFileHashes.entrySet().stream()
			.filter( entry -> entry.getValue().equals( hash ) )
			.map( entry -> entry.getKey() )
			.findFirst()
			.orElse( null );
	}

	public void addModFiles( List<File> modsToAdd )
	{
		for ( File file : modsToAdd ) {
			try {
				File dstFile = new File( modsDir, file.getName() );
				PackUtilities.copyFile( file, dstFile );
			}
			catch ( IOException e ) {
				log.error( "Error occurred while copying mod " + file.getName(), e );
			}
		}
	}

	/**
	 * Clears and syncs the mods list with mods/ dir, then starts a new hash thread.
	 */
	public void rescanMods( ListState<ModFileInfo> tableState )
	{
		managerLock.lock();
		try {
			if ( scanning ) return;
			scanning = true;
		}
		finally {
			managerLock.unlock();
		}

		scanningStateChanged.broadcast( true );
		modFileHashes.clear();

		boolean allowZip = config.getPropertyAsBoolean( ITBConfig.ALLOW_ZIP, false );
		File[] modFiles = modsDir.listFiles( new ModFileFilter( allowZip ) );

		List<ModFileInfo> unsortedMods = new ArrayList<ModFileInfo>();
		for ( File f : modFiles ) {
			ModFileInfo modFileInfo = new ModFileInfo( f );
			unsortedMods.add( modFileInfo );
		}

		amendModsTableState( tableState, unsortedMods );
		modsTableStateAmended.broadcast( tableState );

		ModsScanThread scanThread = new ModsScanThread( modFiles, localModDB );
		scanThread.setDaemon( true );
		scanThread.setPriority( Thread.MIN_PRIORITY );

		// The thread will clean its listener lists when it exits, no need to bother with
		// unregistaring listeners on our end.
		scanThread.hashCalculatedEvent().addListener( this::onHashCalculated );
		scanThread.localModDBUpdatedEvent().addListener( this::onLocalModDBUpdated );
		scanThread.scanEndedEvent().addListener( this::onModsScanEnded );

		scanThread.start();
	}

	// --------------------------------------------------------------------------------------

	/**
	 * Reads modorder.txt and returns a list of mod names in preferred order.
	 */
	public ListState<ModFileInfo> loadModsTableState()
	{
		List<String> fileNames = Collections.emptyList();

		try {
			if ( modsTableStateFile.exists() ) {
				fileNames = Files.readAllLines( modsTableStateFile.toPath(), StandardCharsets.UTF_8 );
			}
		}
		catch ( IOException e ) {
			log.error( String.format( "Error reading \"%s\"", modsTableStateFile.getName() ), e );
		}

		ListState<ModFileInfo> result = new ListState<ModFileInfo>();

		for ( String fileName : fileNames ) {
			File modFile = new File( modsDir, fileName );
			ModFileInfo modFileInfo = new ModFileInfo( modFile );
			result.addItem( modFileInfo );
		}

		return result;
	}

	public void saveModsTableState( ListState<ModFileInfo> tableState )
	{
		try (
			BufferedWriter bw = new BufferedWriter(
				new OutputStreamWriter( new FileOutputStream( modsTableStateFile ), StandardCharsets.UTF_8 )
			)
		) {
			for ( ModFileInfo modFileInfo : tableState.getItems() ) {
				bw.write( modFileInfo.getFile().getName() );
				bw.write( "\r\n" );
			}
			bw.flush();
		}
		catch ( IOException e ) {
			log.error( String.format( "Error writing \"%s\"", modsTableStateFile.getName() ), e );
		}
	}

	public void loadCachedModMetadata() throws InterruptedException
	{
		if ( modsMetadataFile.exists() ) {
			// Load cached metadata first, before scanning for new info.
			ModDB cachedDB = LuaCatalogReader.parse( modsMetadataFile );
			if ( cachedDB != null ) {
				setLocalModDB( cachedDB );
			}
		}

		final ListState<ModFileInfo> tableState = loadModsTableState();

		managerLock.lock();
		try {
			Platform.runLater( () -> rescanMods( tableState ) );

			// Wait until notified that "mods/" has been scanned.
			while ( scanning ) {
				scanEndedCond.await();
			}
		}
		finally {
			managerLock.unlock();
		}
	}

	public void saveCachedModMetadata()
	{
		try {
			LuaCatalogWriter.write( localModDB.getCollatedModInfo(), modsMetadataFile );
		}
		catch ( IOException e ) {
			log.error( String.format( "Error writing metadata from local mods to \"%s\"", modsMetadataFile.getName() ), e );
		}
	}

	// --------------------------------------------------------------------------------------

	/**
	 * Sets the ModDB for local metadata. (thread-safe)
	 */
	private void setLocalModDB( final ModDB newDB )
	{
		UIUtilities.runNowOrLater( () -> localModDB = newDB );
	}

	private void amendModsTableState( ListState<ModFileInfo> tableState, List<ModFileInfo> unsortedMods )
	{
		List<ModFileInfo> availableMods = new ArrayList<ModFileInfo>( unsortedMods );
		Collections.sort( availableMods );

		for ( ModFileInfo modFileInfo : availableMods ) {
			if ( !tableState.containsItem( modFileInfo ) ) {
				tableState.addItem( modFileInfo );
			}
		}
		for ( ModFileInfo modFileInfo : tableState.getItems() ) {
			if ( !availableMods.contains( modFileInfo ) ) {
				tableState.removeItem( modFileInfo );
			}
		}
	}

	// --------------------------------------------------------------------------------------

	private void onHashCalculated( File f, String hash )
	{
		UIUtilities.runNowOrLater( () -> modFileHashes.put( f, hash ) );
	}

	private void onLocalModDBUpdated( ModDB newDB )
	{
		setLocalModDB( newDB );
	}

	private void onModsScanEnded()
	{
		UIUtilities.runNowOrLater(
			() -> {
				managerLock.lock();
				try {
					scanning = false;
					scanEndedCond.signalAll();
				}
				finally {
					managerLock.unlock();
				}

				scanningStateChanged.broadcast( false );
			}
		);
	}


	private static class ModFileFilter implements FileFilter
	{
		private boolean allowZip;


		public ModFileFilter( boolean allowZip )
		{
			this.allowZip = allowZip;
		}

		@Override
		public boolean accept( File f )
		{
			if ( f.isFile() ) {
				if ( f.getName().endsWith( ".itb" ) ) return true;

				if ( allowZip ) {
					if ( f.getName().endsWith( ".zip" ) ) return true;
				}
			}
			return false;
		}
	}
}
