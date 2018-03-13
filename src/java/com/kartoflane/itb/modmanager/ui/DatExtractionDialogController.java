package com.kartoflane.itb.modmanager.ui;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.kartoflane.itb.modmanager.ui.FileSelectorController.SelectorType;

import javafx.stage.Stage;
import net.vhati.ftldat.AbstractPack;
import net.vhati.ftldat.FTLPack;
import net.vhati.ftldat.FolderPack;


public class DatExtractionDialogController extends FileOperationDialogController
{
	private static final Logger log = LogManager.getLogger();


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
			Thread workerThread = new DatExtractThread( datFile, extractDir, dialog );
			workerThread.start();

			dialog.show();
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
