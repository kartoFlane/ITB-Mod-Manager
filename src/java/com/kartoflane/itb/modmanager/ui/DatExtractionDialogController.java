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
		super( owner, SelectorType.alternatingFile( 2 ), "File to extract:", "Destination:" );
		setTitle( "Extract .dats" );
		setOkButtonText( "Extract" );
		setFileOperation( this::extractFiles );
		setCloseOnAccept( true );
	}

	public void extractFiles( File[] files )
	{
		try {
			ProgressDialogController dialog = new ProgressDialogController( stage, true );

			File datFile = files[0];
			File extractDir = files[1];

			if ( !datFile.isFile() ) {
				new Alert( AlertType.ERROR, "Not a file:\n\n" + datFile.getPath() ).show();
				return;
			}
			if ( !extractDir.isDirectory() ) {
				new Alert( AlertType.ERROR, "Not a directory:\n\n" + extractDir.getPath() ).show();
				return;
			}

			Optional<ButtonType> response = Optional.of( ButtonType.OK );
			if ( !Util.isDirectoryEmpty( extractDir.toPath() ) ) {
				String msg = ""
					+ "The directory you have specified is not empty:\n\n"
					+ extractDir.getPath()
					+ "\n\n"
					+ "Are you sure you want to extract the archive there?";

				Alert alert = new Alert( AlertType.CONFIRMATION, msg );
				response = alert.showAndWait();
			}

			if ( response.isPresent() && response.get() == ButtonType.OK ) {
				new DatExtractThread( datFile, extractDir, dialog ).start();
				dialog.show();
			}
		}
		catch ( IOException e ) {
			log.error( e );
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
			if ( !datFile.exists() ) {
				dialog.setStatusTextLater( "File doesn't exist: " + datFile.getPath() );
				dialog.setTaskOutcomeLater( false, null );
				return;
			}
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
