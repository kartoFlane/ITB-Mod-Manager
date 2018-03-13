package com.kartoflane.itb.modmanager.ui;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Stack;

import com.kartoflane.itb.modmanager.ui.FileSelectorController.SelectorType;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import net.vhati.ftldat.AbstractPack;
import net.vhati.ftldat.FTLPack;
import net.vhati.ftldat.FolderPack;


public class DatRepackDialogController extends FileOperationDialogController
{
	public DatRepackDialogController( Stage owner ) throws IOException
	{
		super( owner, SelectorType.FILE_AND_DIR, "Directory to repack:", "Destination file:" );
		setTitle( "Repack .dat" );
		setOkButtonText( "Repack" );
		setFileOperation( this::repackFiles );
		setAcceptancePredicate( this::testFiles );
		setKeepOpen( false );
	}

	public boolean testFiles( File[] files )
	{
		File repackDir = files[0];
		File datFile = files[1];

		if ( !repackDir.exists() ) {
			new Alert( AlertType.ERROR, "Specified directory doesn't exist::\n\n" + repackDir.getPath() ).show();
			return false;
		}
		if ( !repackDir.isDirectory() ) {
			new Alert( AlertType.ERROR, "Not a directory:\n\n" + repackDir.getPath() ).show();
			return false;
		}
		if ( datFile.exists() && !datFile.isFile() ) {
			new Alert( AlertType.ERROR, "Not a file:\n\n" + datFile.getPath() ).show();
			return false;
		}
		
		return true;
	}

	public void repackFiles( File[] files )
	{
		try {
			ProgressDialogController dialog = new ProgressDialogController( stage, true );

			File repackDir = files[0];
			File datFile = files[1];

			Optional<ButtonType> response = Optional.of( ButtonType.OK );
			if ( Files.exists( datFile.toPath() ) ) {
				String msg = ""
					+ "The destination file you have specified already exists:\n\n"
					+ datFile.getPath()
					+ "\n\n"
					+ "It will be overwritten."
					+ "\n\n"
					+ "Are you sure you want to continue?";

				Alert alert = new Alert( AlertType.CONFIRMATION, msg );
				response = alert.showAndWait();
			}

			if ( response.isPresent() && response.get() == ButtonType.OK ) {
				new DatRepackThread( repackDir, datFile, dialog ).start();
				dialog.show();
			}
		}
		catch ( IOException e ) {
			log.error( "Error while creating extraction progress dialog.", e );
		}
	}


	private class DatRepackThread extends Thread
	{
		private File repackDir = null;
		private File datFile = null;
		private ProgressDialogController dialog;


		public DatRepackThread( File repackDir, File datFile, ProgressDialogController dialog )
		{
			this.repackDir = repackDir;
			this.datFile = datFile;
			this.dialog = dialog;
		}

		@Override
		public void run()
		{
			if ( datFile.exists() )
				datFile.delete();

			File datParent = datFile.getParentFile();
			if ( !datParent.exists() )
				datParent.mkdirs();

			// List files BEFORE we attempt to open the destination pack,
			// (which creates it if it doesn't exist), to prevent it from
			// being listed in the file list, and trying to repack itself
			// inside of itself.
			List<File> files = listFiles( repackDir );

			// Still, the user might point to a directory to repack, which
			// contains the destination dat already. Remove it from the list.
			files.remove( datFile );

			try (
				AbstractPack srcPack = new FolderPack( repackDir );
				AbstractPack dstPack = new FTLPack( datFile, "w+" )
			) {
				int progress = 0;

				int max = files.size();
				dialog.setProgressLater( progress, max );

				for ( File file : files ) {
					if ( file.isDirectory() ) {
						dialog.setProgressLater( ++progress, max );
						continue;
					}

					String innerPath = relativize( file, repackDir );
					innerPath = innerPath.replace( "\\", "/" );
					dialog.setStatusTextLater( innerPath );

					try ( InputStream is = new BufferedInputStream( new FileInputStream( file ) ) ) {
						if ( dstPack.contains( innerPath ) )
							dstPack.remove( innerPath );
						dstPack.add( innerPath, is );

						dialog.setProgressLater( ++progress, max );
					}
				}

				dstPack.repack();
				dialog.setStatusTextLater( "All resources repacked successfully." );
				dialog.setTaskOutcomeLater( true, null );
			}
			catch ( IOException e ) {
				log.error( "Error repacking dats.", e );
				dialog.setStatusTextLater( String.format( "Error repacking dats: %s", e ) );
				dialog.setTaskOutcomeLater( false, e );
			}
			finally {
				System.gc();
			}
		}

		private String relativize( File absoluteFile, File relativeToFile )
		{
			if ( !absoluteFile.isAbsolute() ) {
				absoluteFile = absoluteFile.getAbsoluteFile();
			}

			return relativeToFile.toURI().relativize( absoluteFile.toURI() ).getPath();
		}

		private List<File> listFiles( File parentDir )
		{
			List<File> result = new ArrayList<>();

			Stack<File> pending = new Stack<>();
			pending.push( parentDir );

			while ( !pending.isEmpty() ) {
				File file = pending.pop();
				if ( file.isDirectory() ) {
					Arrays.stream( file.listFiles() ).forEach( pending::push );
				}
				else {
					result.add( file );
				}
			}

			return result;
		}
	}
}
