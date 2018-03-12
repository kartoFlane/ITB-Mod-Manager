package com.kartoflane.itb.modmanager.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
	 * Checks the hashes of backups and game's resource.dat.
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
	 * @param hashFileInnerPath
	 *            innerPath to the file containing hash info of the .dat file it's in
	 * @return true if backups need to be redone, false otherwise.
	 * @throws ReinstallRequiredException
	 *             if game's dat contained hash info that did not match the backed up hash
	 */
	public boolean checkDatHash( BackedUpFile resourceBud, String hashFileInnerPath )
		throws IOException, NoSuchAlgorithmException, ReinstallRequiredException
	{
		if ( !resourceBud.bakFile.exists() ) {
			// Backups don't exist yet, nothing to do here.
			return true;
		}

		try (
			AbstractPack datPack = new FTLPack( resourceBud.srcFile, "r+" );
			AbstractPack bakPack = new FTLPack( resourceBud.bakFile, "r" )
		) {
			String readBakHash = extractToString( bakPack, hashFileInnerPath );

			if ( datPack.contains( hashFileInnerPath ) ) {
				// Check if read and backed up dats hashes match.
				// If they don't, warn that backups are likely stale, and
				// the current dats are modded by another copy of the manager
				// So ITB needs a reinstall or Steam cache verified
				// If they do match, then we don't need to do anything.
				String readDatHash = extractToString( datPack, hashFileInnerPath );

				if ( !readDatHash.equals( readBakHash ) ) {
					log.warn( "Game's dat contained hash info, but didn't match backed up hash - reinstall required." );

					Platform.runLater(
						() -> {
							Alert alert = new Alert(
								AlertType.WARNING,
								"Game's resource.dat contains computed hash information that did not match backed up hash.\n\n"
									+ "This means that the manager's backups are most likely stale, and need to be updated, and "
									+ "that the game files have been modded.\n\n"
									+ "Reinstall the game or use Steam's 'Verify Integrity' option to fix this.",
								ButtonType.OK
							);
							alert.showAndWait();
						}
					);

					// Throw an exception to stop patching
					throw new ReinstallRequiredException();
				}

				return false;
			}
			else {
				// Check if current and backed up dats hashes match.
				// If they don't, assume there was an update, and force a backup.
				// If they do, insert backupHash.txt into datPack so that we don't
				// recompute the hash next time.
				String computedDatHash = PackUtilities.calcFileMD5( resourceBud.srcFile );

				if ( computedDatHash.equals( readBakHash ) ) {
					log.info( "Game's dat did not contain hash info, but backed up hash matches - inserting hash file." );
					try (
						ByteArrayInputStream is = new ByteArrayInputStream(
							computedDatHash.getBytes( StandardCharsets.UTF_8 )
						)
					) {
						datPack.add( hashFileInnerPath, is );
						datPack.repack();
					}
					return false;
				}
				else {
					log.warn(
						"Game's dat did not contain hash info, and backed up hash did not match - "
							+ "assuming the game was updated; forcing backup."
					);
					return true;
				}
			}
		}
	}

	/**
	 * Reads contents of the specified innerPath from the specified pack and returns it as string.
	 * 
	 * @param pack
	 *            pack to read from
	 * @param innerPath
	 *            the innerPath to read
	 * @return content of the innerPath
	 */
	private static String extractToString( AbstractPack pack, String innerPath ) throws IOException
	{
		try ( ByteArrayOutputStream hashOut = new ByteArrayOutputStream() ) {
			pack.extractTo( innerPath, hashOut );
			return hashOut.toString( StandardCharsets.UTF_8.name() );
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
