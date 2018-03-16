package com.kartoflane.itb.modmanager.ui;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import com.kartoflane.itb.modmanager.ui.FileSelectorController.SelectorType;
import com.kartoflane.itb.modmanager.util.Util;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.Stage;
import net.vhati.ftldat.AbstractPack;
import net.vhati.ftldat.FTLPack;
import net.vhati.ftldat.FolderPack;


public class DatExtractionDialogController extends FileOperationDialogController
{
	public DatExtractionDialogController( Stage owner ) throws IOException
	{
		super( owner, SelectorType.FILE_AND_DIR, "File to extract:", "Destination:" );
		setTitle( "Extract .dats" );
		setOkButtonText( "Extract" );
		setFileOperation( this::extractFiles );
		setAcceptancePredicate( this::testFiles );
		setKeepOpen( false );
	}

	public boolean testFiles( File[] files )
	{
		File datFile = files[0];
		File extractDir = files[1];

		if ( !datFile.exists() ) {
			new Alert( AlertType.ERROR, "Specified file doesn't exist:\n\n" + datFile.getPath() ).show();
			return false;
		}
		if ( !datFile.isFile() ) {
			new Alert( AlertType.ERROR, "Not a file:\n\n" + datFile.getPath() ).show();
			return false;
		}
		if ( extractDir.exists() && !extractDir.isDirectory() ) {
			new Alert( AlertType.ERROR, "Not a directory:\n\n" + extractDir.getPath() ).show();
			return false;
		}

		return true;
	}

	public void extractFiles( File[] files )
	{
		try {
			ProgressDialogController dialog = new ProgressDialogController( stage, true );

			File datFile = files[0];
			File extractDir = files[1];

			Optional<ButtonType> response = Optional.of( ButtonType.OK );
			if ( !Util.isDirectoryEmpty( extractDir.toPath() ) ) {
				String msg = ""
					+ "The destination directory you have specified is not empty:\n\n"
					+ extractDir.getPath()
					+ "\n\n"
					+ "Some files might be overwritten during extraction."
					+ "\n\n"
					+ "Are you sure you want to continue?";

				Alert alert = new Alert( AlertType.CONFIRMATION, msg );
				response = alert.showAndWait();
			}

			if ( response.isPresent() && response.get() == ButtonType.OK ) {
				new DatExtractThread( datFile, extractDir, dialog ).start();
				dialog.show();
			}
		}
		catch ( IOException e ) {
			log.error( "Error while creating extraction progress dialog.", e );
		}
	}


	private class DatExtractThread extends Thread
	{
		private File datFile = null;
		private File extractDir = null;
		private ProgressDialogController dialog;


		public DatExtractThread( File datFile, File extractDir, ProgressDialogController dialog )
		{
			this.datFile = datFile;
			this.extractDir = extractDir;
			this.dialog = dialog;
		}

		@Override
		public void run()
		{
			if ( !extractDir.exists() )
				extractDir.mkdirs();

			try (
				AbstractPack srcPack = new FTLPack( datFile, "r" );
				AbstractPack dstPack = new FolderPack( extractDir )
			) {
				int progress = 0;

				List<String> innerPaths = srcPack.list();
				int max = innerPaths.size();
				dialog.setProgressLater( progress, max );

				for ( String innerPath : innerPaths ) {
					dialog.setStatusTextLater( innerPath );
					if ( dstPack.contains( innerPath ) ) {
						log.info( "While extracting resources, this file was overwritten: " + innerPath );
						dstPack.remove( innerPath );
					}

					try ( InputStream is = srcPack.getInputStream( innerPath ) ) {
						dstPack.add( innerPath, is );
					}

					dialog.setProgressLater( ++progress, max );
				}

				dialog.setStatusTextLater( "All resources extracted successfully." );
				dialog.setTaskOutcomeLater( true, null );
			}
			catch ( Exception e ) {
				log.error( "Error extracting dats", e );
				dialog.setStatusTextLater( String.format( "Error extracting dats: %s", e ) );
				dialog.setTaskOutcomeLater( false, e );
			}
		}
	}
}
