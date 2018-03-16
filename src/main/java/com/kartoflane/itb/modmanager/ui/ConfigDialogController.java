package com.kartoflane.itb.modmanager.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.kartoflane.itb.modmanager.core.ITBConfig;
import com.kartoflane.itb.modmanager.ui.FieldEditorPaneController.ContentType;
import com.kartoflane.itb.modmanager.util.ITBUtilities;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;


public class ConfigDialogController
{
	@FXML
	protected ScrollPane scrollPane;
	@FXML
	protected Button btnApply;

	protected FieldEditorPaneController fieldController;
	protected Stage stage = null;

	private ITBConfig config;


	public ConfigDialogController( Stage owner, ITBConfig config ) throws IOException
	{
		this.config = config;

		createGUI( owner );
	}

	protected void createGUI( Stage owner ) throws IOException
	{
		FXMLLoader loader = new FXMLLoader( getClass().getResource( "/ConfigDialog.fxml" ) );
		loader.setController( this );
		Region root = loader.load();

		fieldController = new FieldEditorPaneController();
		fieldController.setLabelMinWidth( 150 );

		fieldController.addRow( ITBConfig.ALLOW_ZIP, ContentType.BOOLEAN );
		fieldController.addTextRow( "Treat .zip files as .itb files." );
		fieldController.addSeparatorRow();

		fieldController.addRow( ITBConfig.LOAD_PREV_MODS, ContentType.BOOLEAN );
		fieldController.addTextRow( "Restore selection of previously installed mods on startup." );
		fieldController.addSeparatorRow();

		fieldController.addRow( ITBConfig.RUN_STEAM_ITB, ContentType.BOOLEAN );
		fieldController.addTextRow( "Use Steam to run Into the Breach, if possible." );
		fieldController.addSeparatorRow();

		fieldController.addRow( ITBConfig.NEVER_RUN_ITB, ContentType.BOOLEAN );
		fieldController.addTextRow( "Don't offer to run Into the Breach after patching." );
		fieldController.addSeparatorRow();

		fieldController.addRow( ITBConfig.REMEMBER_GEOMETRY, ContentType.BOOLEAN );
		fieldController.addTextRow( "Save window geometry on exit." );
		fieldController.addSeparatorRow();

		fieldController.addRow( ITBConfig.APP_UPDATE_INTERVAL, ContentType.INTEGER );
		fieldController.addTextRow( "Check for updates to the program every N days (0 to disable)." );
		fieldController.addSeparatorRow();

		fieldController.addRow( ITBConfig.GAME_PATH, ContentType.CHOOSER );
		fieldController.setChooserBrowseListener( ITBConfig.GAME_PATH, this::onBrowseGamePath );
		fieldController.addTextRow( "Path to Into the Breach's game folder." );
		fieldController.addSeparatorRow();

		fieldController.addRow( ITBConfig.STEAM_EXE_PATH, ContentType.CHOOSER );
		fieldController.setChooserBrowseListener( ITBConfig.STEAM_EXE_PATH, this::onBrowseSteamExe );
		fieldController.addTextRow( "Path to Steam's executable." );
		fieldController.addBlankRow();

		fieldController.setBoolean( ITBConfig.ALLOW_ZIP, config.getPropertyAsBoolean( ITBConfig.ALLOW_ZIP, false ) );
		fieldController.setBoolean( ITBConfig.LOAD_PREV_MODS, config.getPropertyAsBoolean( ITBConfig.LOAD_PREV_MODS, true ) );
		fieldController.setBoolean( ITBConfig.RUN_STEAM_ITB, config.getPropertyAsBoolean( ITBConfig.RUN_STEAM_ITB, false ) );
		fieldController.setBoolean( ITBConfig.NEVER_RUN_ITB, config.getPropertyAsBoolean( ITBConfig.NEVER_RUN_ITB, false ) );
		fieldController.setBoolean( ITBConfig.REMEMBER_GEOMETRY, config.getPropertyAsBoolean( ITBConfig.REMEMBER_GEOMETRY, true ) );
		fieldController.setInt( ITBConfig.APP_UPDATE_INTERVAL, config.getPropertyAsInt( ITBConfig.APP_UPDATE_INTERVAL, 0 ) );
		fieldController.setChooserPath( ITBConfig.GAME_PATH, config.getProperty( ITBConfig.GAME_PATH, "" ) );
		fieldController.setChooserPath( ITBConfig.STEAM_EXE_PATH, config.getProperty( ITBConfig.STEAM_EXE_PATH, "" ) );

		scrollPane.setContent( fieldController.getRoot() );

		stage = new Stage();
		stage.setScene( new Scene( root ) );
		stage.initOwner( owner );
		stage.initModality( Modality.APPLICATION_MODAL );

		stage.setTitle( "Preferences" );
		stage.setMinWidth( root.getMinWidth() );
		stage.setMinHeight( root.getMinHeight() );
	}

	public void show()
	{
		stage.show();
	}

	/**
	 * @see Stage#showAndWait()
	 */
	public void showAndWait()
	{
		stage.showAndWait();
	}

	private void onBrowseGamePath( ActionEvent e )
	{
		DirectoryChooser dialog = new DirectoryChooser();
		dialog.setTitle( "Find Into The Breach game directory" );

		File selectedDir = dialog.showDialog( null );

		if ( selectedDir != null && ITBUtilities.isGameDirValid( selectedDir.toPath() ) ) {
			fieldController.setChooserPath( ITBConfig.GAME_PATH, selectedDir.getPath() );
		}
	}

	private void onBrowseSteamExe( ActionEvent e )
	{
		FileChooser dialog = new FileChooser();
		dialog.setTitle( "Find Steam.exe or steam or Steam.app" );

		File selectedFile = dialog.showOpenDialog( null );

		if ( selectedFile != null && selectedFile.exists() ) {
			fieldController.setChooserPath( ITBConfig.STEAM_EXE_PATH, selectedFile.getPath() );
		}
	}

	@FXML
	protected void onApplyClicked( ActionEvent e )
	{
		String tmp;

		config.setProperty( ITBConfig.ALLOW_ZIP, fieldController.getValue( ITBConfig.ALLOW_ZIP, ContentType.BOOLEAN ) );
		config.setProperty( ITBConfig.LOAD_PREV_MODS, fieldController.getValue( ITBConfig.LOAD_PREV_MODS, ContentType.BOOLEAN ) );
		config.setProperty( ITBConfig.RUN_STEAM_ITB, fieldController.getValue( ITBConfig.RUN_STEAM_ITB, ContentType.BOOLEAN ) );
		config.setProperty( ITBConfig.NEVER_RUN_ITB, fieldController.getValue( ITBConfig.NEVER_RUN_ITB, ContentType.BOOLEAN ) );
		config.setProperty( ITBConfig.REMEMBER_GEOMETRY, fieldController.getValue( ITBConfig.REMEMBER_GEOMETRY, ContentType.BOOLEAN ) );

		tmp = fieldController.getValue( ITBConfig.APP_UPDATE_INTERVAL, ContentType.INTEGER );
		try {
			int n = Integer.parseInt( tmp );
			n = Math.max( 0, n );
			config.setProperty( ITBConfig.APP_UPDATE_INTERVAL, Integer.toString( n ) );
		}
		catch ( NumberFormatException ex ) {
		}

		tmp = fieldController.getValue( ITBConfig.GAME_PATH, ContentType.CHOOSER );
		if ( tmp.length() > 0 && ITBUtilities.isGameDirValid( Paths.get( tmp ) ) ) {
			config.setProperty( ITBConfig.GAME_PATH, tmp );
		}

		tmp = fieldController.getValue( ITBConfig.STEAM_EXE_PATH, ContentType.CHOOSER );
		if ( tmp.length() > 0 && Files.exists( Paths.get( tmp ) ) ) {
			config.setProperty( ITBConfig.STEAM_EXE_PATH, tmp );
		}

		stage.close();
	}
}
