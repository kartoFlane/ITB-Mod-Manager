package com.kartoflane.itb.modmanager.core;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;

import com.kartoflane.itb.modmanager.core.BackupManager.BackedUpFile;
import com.kartoflane.itb.modmanager.event.Event;
import com.kartoflane.itb.modmanager.event.EventDouble;
import com.kartoflane.itb.modmanager.event.EventSingle;
import com.kartoflane.itb.modmanager.lua.LuaLoader;
import com.kartoflane.itb.modmanager.lua.LuaResult;
import com.kartoflane.itb.modmanager.patcher.DefaultPatcher;
import com.kartoflane.itb.modmanager.patcher.FMODPatcher;
import com.kartoflane.itb.modmanager.patcher.LuaPatcher;
import com.kartoflane.itb.modmanager.patcher.ResourcePatcher;
import com.kartoflane.itb.modmanager.patcher.TxtPatcher;
import com.kartoflane.itb.modmanager.util.Util;

import net.vhati.ftldat.AbstractPack;
import net.vhati.ftldat.FTLPack;
import net.vhati.ftldat.FolderPack;
import net.vhati.ftldat.PackContainer;
import net.vhati.ftldat.PackUtilities;
import net.vhati.modmanager.core.ModInfo;
import net.vhati.modmanager.core.ModUtilities;


public class ModPatchThread extends Thread
{
	public static final String MODDED_INFO_INNERPATH = "modded.info";
	public static final String SCRIPTS_LIST_INNERPATH = "scripts.lua";

	private static final Logger log = LogManager.getLogger();

	private final EventDouble<Integer, Integer> patchingProgressChanged = new EventDouble<>();
	private final EventSingle<String> patchingStatusChanged = new EventSingle<>();
	private final EventSingle<File> patchingModStarted = new EventSingle<>();
	private final EventDouble<Boolean, Exception> patchingEnded = new EventDouble<>();


	// Other threads can check or set this.
	public volatile boolean keepRunning = true;

	private Thread shutdownHook = null;

	private final BackupManager backupManager;
	private final List<ModInfo> modInfos = new ArrayList<>();
	private final List<File> modFiles = new ArrayList<>();
	private File gameDir = null;

	private final int progMax = 100;
	private final int progBackupMax = 25;
	private final int progClobberMax = 25;
	private final int progModsMax = 40;
	private final int progRepackMax = 5;
	private int progMilestone = 0;


	public ModPatchThread(
		BackupManager backupManager,
		List<ModInfo> modInfos,
		List<File> modFiles,
		File gameDir
	)
	{
		super( "patch" );
		this.backupManager = backupManager;
		this.modInfos.addAll( modInfos );
		this.modFiles.addAll( modFiles );
		this.gameDir = gameDir;
	}

	public Event.Double<Integer, Integer> patchingProgressChangedEvent()
	{
		return patchingProgressChanged;
	}

	public Event.Single<String> patchingStatusChangedEvent()
	{
		return patchingStatusChanged;
	}

	public Event.Single<File> patchingModStartedEvent()
	{
		return patchingModStarted;
	}

	public Event.Double<Boolean, Exception> patchingEndedEvent()
	{
		return patchingEnded;
	}

	public void run()
	{
		boolean result;
		Exception exception = null;

		// When JVM tries to exit, stall until this thread ends on its own.
		shutdownHook = new Thread(
			() -> {
				keepRunning = false;
				boolean interrupted = false;
				try {
					while ( ModPatchThread.this.isAlive() ) {
						try {
							ModPatchThread.this.join();
						}
						catch ( InterruptedException e ) {
							interrupted = true;
						}
					}
				}
				finally {
					if ( interrupted ) Thread.currentThread().interrupt();
				}
			}
		);
		Runtime.getRuntime().addShutdownHook( shutdownHook );

		try {
			log.info( "" );
			log.info( "Patching..." );
			log.info( "" );

			result = patch();

			if ( result ) {
				log.info( "" );
				log.info( "Patching finished successfully." );
				log.info( "" );
			}
		}
		catch ( Exception e ) {
			log.error( "Patching failed.", e );
			exception = e;
			result = false;
		}

		patchingEnded.broadcast( result, exception );

		// Cleanup
		patchingProgressChanged.clearListeners();
		patchingStatusChanged.clearListeners();
		patchingModStarted.clearListeners();
		patchingEnded.clearListeners();

		Runtime.getRuntime().removeShutdownHook( shutdownHook );
	}

	private boolean patch()
		throws IOException, NoSuchAlgorithmException, ReinstallRequiredException
	{
		patchingProgressChanged.broadcast( 0, progMax );

		PackContainer packContainer = null;

		try {
			int modsInstalled = 0;

			File resourcesDir = new File( gameDir, "resources" );

			File scriptsDir = new File( gameDir, "scripts" );
			File mapsDir = new File( gameDir, "maps" );

			File resourceDatFile = new File( resourcesDir, "resource.dat" );

			List<BackedUpFile> backedUpDats = backupManager.listBackedUpFiles();
			BackedUpFile resourceBud = backupManager.getBackupForFile( resourceDatFile );

			patchingStatusChanged.broadcast( "Checking hashes..." );
			boolean forceBackup = backupManager.checkDatHash( resourceBud, MODDED_INFO_INNERPATH );

			boolean resourceBakExisted = resourceBud.bakFile.exists();
			boolean backupSuccessful = backupAndRestoreGameData( backedUpDats, forceBackup );
			if ( !backupSuccessful )
				return false;

			if ( !resourceBakExisted ) {
				// resource.dat.bak did not exist - need to write hash info to it.
				ModdedDatInfo datInfo = new ModdedDatInfo( PackUtilities.calcFileMD5( resourceBud.srcFile ) );
				try (
					InputStream is = Util.getInputStream( datInfo.toLuaString() );
					AbstractPack pack = new FTLPack( resourceBud.bakFile, "r+" )
				) {
					if ( pack.contains( MODDED_INFO_INNERPATH ) ) {
						log.warn( "Game's resources already contained modded info. Game may not be in vanilla state." );
						// Don't overwrite, since the hash we just computed is wrong.
						// TODO: Display an alert warning the user, and ask if they want to continue patching anyway?
						// will need a way to stop this thread and wait for the alert to be dismissed, tho.
					}
					else {
						pack.add( MODDED_INFO_INNERPATH, is );
						pack.repack();
					}
				}
			}

			if ( modFiles.isEmpty() ) {
				// No mods. Nothing else to do.
				patchingProgressChanged.broadcast( progMax, progMax );
				return true;
			}

			patchingStatusChanged.broadcast( "Preparing to install mods..." );
			packContainer = new PackContainer();
			AbstractPack datPack = new FTLPack( resourceDatFile, "r+" );
			AbstractPack scriptsPack = new FolderPack( scriptsDir );
			AbstractPack mapsPack = new FolderPack( mapsDir );
			// TODO: An FMODPack that allows assigning innerPaths to specific .bank files?
			// Or just do `setPackFor( "audio/sfx/", sfxAudioPack )`, etc. for each .bank file
			AbstractPack audioPack = new FolderPack( resourcesDir );

			packContainer.setPackFor( "mod-appendix/", null );
			packContainer.setPackFor( "scripts/", scriptsPack );
			packContainer.setPackFor( "audio/", audioPack );
			packContainer.setPackFor( "maps/", mapsPack );
			packContainer.setPackFor( "fonts/", datPack );
			packContainer.setPackFor( "img/", datPack );
			packContainer.setPackFor( null, null );

			ModdedDatInfo datInfo = ModdedDatInfo.build( datPack, MODDED_INFO_INNERPATH );

			// Track modified innerPaths in case they're clobbered.
			List<String> moddedItems = new ArrayList<>();

			List<String> knownPaths = new ArrayList<>();
			for ( AbstractPack pack : packContainer.getPacks() ) {
				knownPaths.addAll( pack.list() );
			}

			List<String> knownPathsLower = new ArrayList<>( knownPaths.size() );
			for ( String innerPath : knownPaths ) {
				knownPathsLower.add( innerPath.toLowerCase() );
			}

			List<String> knownRoots = packContainer.getRoots();

			List<String> vanillaScriptsList = readScriptsList( scriptsPack, SCRIPTS_LIST_INNERPATH );
			List<String> moddedScriptsList = new ArrayList<String>();

			// TODO: Insert modding API file here
			// moddedScriptsList.add( "moddingAPI.lua" )
			// pack.add( "moddingAPI.lua", is )

			final String encoding = "UTF-8";

			// Preserve insertion order
			Map<String, ResourcePatcher> patcherMap = new LinkedHashMap<>();
			patcherMap.put( "txt", new TxtPatcher( log, encoding, moddedItems ) );
			patcherMap.put( "lua", new LuaPatcher( (TxtPatcher)patcherMap.get( "txt" ), moddedScriptsList ) );
			FMODPatcher fmodPatcher = new FMODPatcher(); // TODO ????
			patcherMap.put( "bank", fmodPatcher );
			patcherMap.put( "wav", fmodPatcher );
			patcherMap.put( "mp3", fmodPatcher );
			ResourcePatcher defaultPatcher = new DefaultPatcher( log, moddedItems );

			// Group1: parentPath/, Group2: root/, Group3: fileName.
			Pattern pathPtn = Pattern.compile( "^(?:(([^/]+/)(?:.*/)?))?([^./]+\\.([^/]+))$" );

			for ( int i = 0; i < modFiles.size(); ++i ) {
				if ( !keepRunning ) return false;

				File modFile = modFiles.get( i );

				try (
					FileInputStream fis = new FileInputStream( modFile );
					ZipInputStream zis = new ZipInputStream( new BufferedInputStream( fis ) )
				) {
					log.info( "" );
					log.info( String.format( "Installing mod: %s", modFile.getName() ) );
					patchingModStarted.broadcast( modFile );

					ZipEntry item;
					while ( ( item = zis.getNextEntry() ) != null ) {
						if ( item.isDirectory() ) {
							zis.closeEntry();
							continue;
						}

						String innerPath = item.getName();
						innerPath = innerPath.replace( '\\', '/' );  // Non-standard zips.

						Matcher m = pathPtn.matcher( innerPath );
						if ( !m.matches() ) {
							log.warn( String.format( "Unexpected innerPath: %s", innerPath ) );
							zis.closeEntry();
							continue;
						}

						String parentPath = m.group( 1 );
						String root = m.group( 2 );
						String fileName = m.group( 3 );
						String extension = m.group( 4 );

						AbstractPack pack = packContainer.getPackFor( innerPath );
						if ( pack == null ) {
							if ( !knownRoots.contains( root ) ) {
								log.warn( String.format( "Unexpected innerPath: %s", innerPath ) );
							}
							else {
								log.debug( String.format( "Ignoring innerPath with known root: %s", innerPath ) );
							}
							zis.closeEntry();
							continue;
						}

						if ( ModUtilities.isJunkFile( innerPath ) ) {
							log.warn( String.format( "Skipping junk file: %s", innerPath ) );
							zis.closeEntry();
							continue;
						}

						ResourcePatcher patcher = patcherMap.getOrDefault( extension, defaultPatcher );
						innerPath = patcher.normalizeInnerPath( modFile, innerPath, parentPath, root, fileName );
						innerPath = checkCase( innerPath, knownPaths, knownPathsLower );
						patcher.patch( pack, innerPath, zis );

						zis.closeEntry();
					}

					datInfo.addModInfo( modInfos.get( i ) );
				}
				finally {
					System.gc();
				}

				modsInstalled++;
				patchingProgressChanged.broadcast( progMilestone + progModsMax / modFiles.size() * modsInstalled, progMax );
			}

			try ( InputStream is = Util.getInputStream( datInfo.toLuaString() ) ) {
				if ( datPack.contains( MODDED_INFO_INNERPATH ) )
					datPack.remove( MODDED_INFO_INNERPATH );
				datPack.add( MODDED_INFO_INNERPATH, is );
			}

			progMilestone += progModsMax;
			patchingProgressChanged.broadcast( progMilestone, progMax );

			// Rebuild scripts.lua
			try ( InputStream is = Util.getInputStream( rebuildScriptsList( vanillaScriptsList, moddedScriptsList ) ) ) {
				if ( scriptsPack.contains( SCRIPTS_LIST_INNERPATH ) )
					scriptsPack.remove( SCRIPTS_LIST_INNERPATH );
				scriptsPack.add( SCRIPTS_LIST_INNERPATH, is );
				scriptsPack.repack();
			}

			// Prune 'removed' files from dats.
			for ( AbstractPack pack : packContainer.getPacks() ) {
				patchingStatusChanged.broadcast( String.format( "Repacking \"%s\"...", pack.getName() ) );

				AbstractPack.RepackResult repackResult = pack.repack();
				if ( repackResult != null ) {
					long bytesChanged = repackResult.bytesChanged;
					log.info( String.format( "Repacked \"%s\" (%d bytes affected)", pack.getName(), bytesChanged ) );
				}

				patchingProgressChanged.broadcast( progMilestone + progRepackMax, progMax );
			}
			progMilestone += progRepackMax;
			patchingProgressChanged.broadcast( progMilestone, progMax );

			patchingProgressChanged.broadcast( 100, progMax );
			return true;
		}
		finally {
			if ( packContainer != null ) {
				for ( AbstractPack pack : packContainer.getPacks() ) {
					try {
						pack.close();
					}
					catch ( Exception e ) {
					}
				}
			}
		}
	}

	/**
	 * Backs up game data if backups don't exist already, or if overridden by forceBackup argument.
	 * If backups weren't created just now, then restores vanilla files from backups.
	 * 
	 * @param backedUpDats
	 *            list of game's files to back up
	 * @param forceBackup
	 *            whether backups should be created regardless of whether they exist already or not
	 * @return true if the entire method completed successfully;
	 *         false if it was told to stop by setting keepRunning to false
	 */
	private boolean backupAndRestoreGameData( List<BackedUpFile> backedUpDats, boolean forceBackup ) throws IOException
	{
		// Create backup dats, if necessary.
		int backupsCreated = 0;
		for ( BackedUpFile bud : backedUpDats ) {
			if ( forceBackup || !bud.bakFile.exists() ) {
				log.info( String.format( "Backing up \"%s\".", bud.srcFile.getName() ) );
				patchingStatusChanged.broadcast( String.format( "Backing up \"%s\".", bud.srcFile.getName() ) );

				if ( bud.srcFile.isDirectory() ) {
					PackUtilities.backUpDirAsPack( bud.srcFile, bud.bakFile );
				}
				else {
					PackUtilities.copyFile( bud.srcFile, bud.bakFile );
				}

				backupsCreated++;
				patchingProgressChanged.broadcast( progBackupMax / backedUpDats.size() * backupsCreated, progMax );

				if ( !keepRunning ) return false;
			}
		}

		progMilestone += progBackupMax;
		patchingProgressChanged.broadcast( progMilestone, progMax );
		patchingStatusChanged.broadcast( null );

		if ( backupsCreated != backedUpDats.size() ) {
			// Clobber current dat file with its backup.
			// But don't bother if we made the backup just now.

			int datsClobbered = 0;
			for ( BackedUpFile bud : backedUpDats ) {
				log.info( String.format( "Restoring vanilla \"%s\"...", bud.srcFile.getName() ) );
				patchingStatusChanged.broadcast( String.format( "Restoring vanilla \"%s\"...", bud.srcFile.getName() ) );

				if ( bud.srcFile.isDirectory() ) {
					PackUtilities.restorePackAsDir( bud.bakFile, bud.srcFile );
				}
				else {
					PackUtilities.copyFile( bud.bakFile, bud.srcFile );
				}
				datsClobbered++;
				patchingProgressChanged.broadcast( progClobberMax / backedUpDats.size() * datsClobbered, progMax );

				if ( !keepRunning ) return false;
			}
			patchingStatusChanged.broadcast( null );
		}

		progMilestone += progClobberMax;
		patchingProgressChanged.broadcast( progMilestone, progMax );

		return true;
	}

	/**
	 * Fetches all script files declared in the specified file, and returns them as a list.
	 * 
	 * @param pack
	 *            a pack containing the game's script files
	 * @param innerPath
	 *            path to file listing all lua scripts used by the game
	 * @return list containing all script files loaded and used by the game.
	 */
	private List<String> readScriptsList( AbstractPack pack, String innerPath ) throws IOException, LuaError
	{
		try ( InputStream is = pack.getInputStream( innerPath ) ) {
			LuaLoader parser = LuaLoader.minimal();
			LuaResult result = parser.load( is, innerPath );

			LuaTable table = result.environment.get( "GetScripts" ).call().checktable();

			return LuaLoader.stream( table )
				.map( entry -> entry.getValue().checkjstring() )
				.collect( Collectors.toList() );
		}
	}

	/**
	 * Rebuilds the content of scripts.lua by listing all vanilla script files, and then all modded
	 * script files.
	 */
	private String rebuildScriptsList( List<String> vanillaScriptsList, List<String> moddedScriptsList )
	{
		StringBuilder buf = new StringBuilder();

		Consumer<String> scriptPaster = script -> buf.append( "\t\"" ).append( script ).append( "\",\n" );

		buf.append( "function GetScripts() return {\n" );
		vanillaScriptsList.forEach( scriptPaster );
		buf.append( "\n\t-------------- MODDED SCRIPTS --------------\n\n" );
		moddedScriptsList.forEach( scriptPaster );
		buf.append( "}\nend\n" );

		return buf.toString();
	}

	/**
	 * Checks if an innerPath exists, ignoring letter case.
	 *
	 * If there is no collision, the innerPath is added to the known lists.
	 * A warning will be logged if a path with differing case exists.
	 *
	 * @param knownPaths
	 *            a list of innerPaths seen so far
	 * @param knownPathsLower
	 *            a copy of knownPaths, lower-cased
	 * @return the existing path (if different), or innerPath
	 */
	private String checkCase( String innerPath, List<String> knownPaths, List<String> knownPathsLower )
	{
		if ( knownPaths.contains( innerPath ) ) return innerPath;

		String lowerPath = innerPath.toLowerCase();
		int lowerIndex = knownPathsLower.indexOf( lowerPath );
		if ( lowerIndex != -1 ) {
			String knownPath = knownPaths.get( lowerIndex );
			log.warn( String.format( "Modded file's case doesn't match existing path: \"%s\" vs \"%s\"", innerPath, knownPath ) );
			return knownPath;
		}

		knownPaths.add( innerPath );
		knownPathsLower.add( lowerPath );
		return innerPath;
	}


	@SuppressWarnings("serial")
	public static class ReinstallRequiredException extends Exception
	{
	}
}
