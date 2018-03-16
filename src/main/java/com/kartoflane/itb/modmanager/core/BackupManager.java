package com.kartoflane.itb.modmanager.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.kartoflane.itb.modmanager.ITBModManager;
import com.kartoflane.itb.modmanager.core.ModPatchThread.ReinstallRequiredException;
import com.kartoflane.itb.modmanager.util.Util;

import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import net.vhati.ftldat.AbstractPack;
import net.vhati.ftldat.FTLPack;
import net.vhati.ftldat.PackUtilities;


public class BackupManager
{
	private static final Logger log = LogManager.getLogger();

	private final File backupDir;
	private final List<BackedUpFile> backedUpFiles;


	public BackupManager( File backupDir, File... filesToBackUp )
	{
		this.backupDir = backupDir;
		this.backedUpFiles = Arrays.stream( filesToBackUp )
			.map( this::toBackedUpFile )
			.collect( Collectors.toList() );
	}

	public File getBackupDir()
	{
		return backupDir;
	}

	public List<BackedUpFile> listBackedUpFiles()
	{
		return backedUpFiles;
	}

	public BackedUpFile getBackupForFile( File f )
	{
		return backedUpFiles.stream()
			.filter( bud -> bud.srcFile.equals( f ) )
			.findFirst().get();
	}

	public void deleteBackups()
	{
		List<String> failures = new ArrayList<String>( backedUpFiles.size() );
		boolean backupsExist = false;

		for ( BackedUpFile bud : backedUpFiles ) {
			if ( bud.bakFile.exists() ) {
				backupsExist = true;

				try {
					Files.delete( bud.bakFile.toPath() );
				}
				catch ( Exception e ) {
					log.error( "Unable to delete backup: " + bud.bakFile.getName(), e );
					failures.add( bud.bakFile.getName() );
				}
			}
		}

		if ( !backupsExist ) {
			Alert alert = new Alert( AlertType.INFORMATION, "There were no backups to delete.", ButtonType.OK );
			alert.setHeaderText( "Nothing to do" );
			alert.show();
		}
		else if ( failures.isEmpty() ) {
			Alert alert = new Alert( AlertType.INFORMATION, "Backups were deleted successfully.", ButtonType.OK );
			alert.setHeaderText( "Success" );
			alert.show();
		}
		else {
			StringBuilder failBuf = new StringBuilder( "The following files couldn't be deleted:" );
			for ( String s : failures ) {
				failBuf.append( "- \"" ).append( s ).append( "\"\n" );
			}
			failBuf.append( "\nTry going in the manager's \"/backup/\" folder and deleting them manually?" );

			Alert alert = new Alert( AlertType.ERROR, failBuf.toString(), ButtonType.YES, ButtonType.NO );
			Optional<ButtonType> response = alert.showAndWait();

			if ( response.isPresent() && response.get() == ButtonType.YES ) {
				HostServices host = ITBModManager.getApplication().getHostServices();
				host.showDocument( "file://" + backupDir.toPath() );
			}
		}
	}

	/**
	 * Checks the modded info of backups and game's resource.dat.
	 * 
	 * If hash file exists inside the game's archive, then compare it to the backup hash.
	 * If they match, then we don't need to update.
	 * If they don't match, then that means that the game has been modded by another instance
	 * of the manager, and that our own backups are likely stale. The game needs to be reinstalled.
	 * 
	 * If hash file does not exist inside the game's archive, then compute it and compare to
	 * the backup hash.
	 * If they match, then insert the hash information into the game's dat. We don't need to update.
	 * If they don't match, then that means that the game was reinstalled or updated, and that
	 * we need to update our backups.
	 * 
	 * @param resourceBud
	 *            BackedUpDat instance for the resource.dat file
	 * @param infoFileInnerPath
	 *            innerPath to the modded info file
	 * @return true if backups need to be redone, false otherwise.
	 * @throws ReinstallRequiredException
	 *             if the patching process cannot continue, because the manager's backups are stale,
	 *             and the game is in a modded state.
	 */
	public boolean checkModdedInfo( BackedUpFile resourceBud, String infoFileInnerPath )
		throws IOException, NoSuchAlgorithmException, ReinstallRequiredException
	{
		if ( resourceBud.bakFile.exists() ) {
			try (
				AbstractPack datPack = new FTLPack( resourceBud.srcFile, "r+" );
				AbstractPack bakPack = new FTLPack( resourceBud.bakFile, "r" )
			) {
				ModdedDatInfo bakInfo = ModdedDatInfo.build( bakPack, infoFileInnerPath );

				if ( datPack.contains( infoFileInnerPath ) ) {
					// Check if read and backed up dats hashes match.
					// If they don't, warn that backups are likely stale, and
					// the current dats are modded by another copy of the manager
					// So ITB needs a reinstall or Steam cache verified
					// If they do match, then we don't need to do anything.
					ModdedDatInfo datInfo = ModdedDatInfo.build( datPack, infoFileInnerPath );

					if ( !datInfo.getOriginalHash().equals( bakInfo.getOriginalHash() ) ) {
						if ( datInfo.hasMods() ) {
							log.warn( "Game's dat contained modded info, but hashes didn't match - reinstall required." );

							Platform.runLater(
								() -> {
									String msg = ""
										+ "Game's resource.dat contains modded info, but its computed hash did not "
										+ "match backed up hash.\n\n"
										+ "This means that the manager's backups are most likely stale, amd need to "
										+ "be updated, and that the game files have been modded.\n\n"
										+ "Reinstall the game or use Steam's 'Verify Integrity' option to fix this.";

									Alert alert = new Alert( AlertType.ERROR, msg, ButtonType.OK );
									alert.showAndWait();
								}
							);

							// Throw an exception to stop patching
							throw new ReinstallRequiredException();
						}
						else {
							// No listed mods, but hashes don't match.
							// Can't really assume new version, because modded.info file exists.
							// Assume old version, and do nothing for now.
						}
					}

					return false;
				}
				else {
					// Check if current and backed up dats hashes match.
					// If they don't, assume there was an update, and force a backup.
					// If they do, insert backupHash.txt into datPack so that we don't
					// recompute the hash next time.
					String computedDatHash = PackUtilities.calcFileMD5( resourceBud.srcFile );

					if ( computedDatHash.equals( bakInfo.getOriginalHash() ) ) {
						log.info( "Game's dat did not contain modded info, but backed up hash matches - inserting modded info file." );
						ModdedDatInfo datInfo = new ModdedDatInfo( computedDatHash );

						try ( InputStream is = Util.getInputStream( datInfo.toLuaString() ) ) {
							datPack.add( infoFileInnerPath, is );
							datPack.repack();
						}

						return false;
					}
					else {
						log.warn(
							"Game's dat did not contain modded info, and backed up hash did not match - "
								+ "assuming the game was updated; forcing backup."
						);

						return true;
					}
				}
			}
		}
		else {
			// If backups don't exist yet, then instead check the src dat to see if it's modded.
			try ( AbstractPack pack = new FTLPack( resourceBud.srcFile, "r" ) ) {
				if ( pack.contains( infoFileInnerPath ) ) {
					// Was modded. Check modded.info file for list of installed mods.
					ModdedDatInfo datInfo = ModdedDatInfo.build( pack, infoFileInnerPath );
					if ( datInfo.hasMods() ) {
						log.warn(
							"Game's resources already contained modded info file, and mods list is not empty. "
								+ "Game is not in vanilla state."
						);

						Platform.runLater(
							() -> {
								String msg = ""
									+ "The manager's backups were missing, and the game is already in a modded state.\n"
									+ "This means that the manager is unable to properly patch mods."
									+ "\n\n"
									+ "Reinstall the game or use Steam's 'Verify Integrity' option to fix this.";

								Alert alert = new Alert( AlertType.ERROR, msg, ButtonType.OK );
								alert.showAndWait();
							}
						);

						throw new ReinstallRequiredException();
					}
					else {
						// Was processed by the mod manager, but has no mods.
						return false;
					}
				}
				else {
					// Not modded. Don't force backups, in case only resource.dat was missing.
					return false;
				}
			}
		}
	}

	private BackedUpFile toBackedUpFile( File f )
	{
		if ( !f.exists() )
			return null;
		BackedUpFile bud = new BackedUpFile();
		bud.srcFile = f;
		bud.bakFile = new File( backupDir, f.getName() + ".bak" );
		bud.srcFile.setWritable( true );
		return bud;
	}


	public static class BackedUpFile
	{
		public File srcFile = null;
		public File bakFile = null;
	}
}
