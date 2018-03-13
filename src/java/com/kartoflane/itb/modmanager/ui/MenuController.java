package com.kartoflane.itb.modmanager.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.kartoflane.itb.modmanager.ITBModManager;
import com.kartoflane.itb.modmanager.core.BackupManager;
import com.kartoflane.itb.modmanager.core.ITBConfig;
import com.kartoflane.itb.modmanager.core.ModsScanner;
import com.kartoflane.itb.modmanager.util.ITBUtilities;
import com.kartoflane.itb.modmanager.util.UIUtilities;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.stage.WindowEvent;
import net.vhati.modmanager.core.AutoUpdateInfo;
import net.vhati.modmanager.core.ComparableVersion;
import net.vhati.modmanager.core.ModFileInfo;
import net.vhati.modmanager.ui.table.ListState;


/**
 * Controls interaction with the application menu bar.
 */
public class MenuController
{
	private static final Logger log = LogManager.getLogger();

	@FXML
	protected MenuBar menuBar;
	@FXML
	protected MenuItem mntmRescan;
	@FXML
	protected MenuItem mntmExtract;
	@FXML
	protected MenuItem mntmRepack;
	@FXML
	protected MenuItem mntmPreferences;
	@FXML
	protected MenuItem mntmExit;
	@FXML
	protected MenuItem mntmDeleteBackups;
	@FXML
	protected MenuItem mntmVerifyIntegrity;
	@FXML
	protected MenuItem mntmAbout;
	@FXML
	protected ClickableMenu mntmUpdate;

	protected final ModListController modListController;

	protected final ModsScanner modsScanner;
	protected final BackupManager backupManager;

	protected final ITBConfig config;

	private AutoUpdateInfo updateInfo = null;


	public MenuController(
		ITBConfig config,
		ModsScanner modsScanner, BackupManager backupManager,
		ModListController modListController
	) throws IOException
	{
		this.config = config;

		this.modsScanner = modsScanner;
		this.backupManager = backupManager;

		this.modListController = modListController;

		createGUI();
	}

	private void createGUI() throws IOException
	{
		FXMLLoader loader = new FXMLLoader( getClass().getResource( "MenuBar.fxml" ) );
		loader.setController( this );
		loader.load();

		// ClickableMenu doesn't work when created from FXML; have to create it in code.
		mntmUpdate = new ClickableMenu( "Update Available" );
		mntmUpdate.setId( "mntmUpdate" );
		mntmUpdate.setVisible( false );
		mntmUpdate.setDisable( true );
		mntmUpdate.setOnAction( this::onUpdateAvailableClicked );
		menuBar.getMenus().add( mntmUpdate );

		// Temp while those buttons' functions are not implemented yet
		mntmRepack.setDisable( true );
		mntmPreferences.setDisable( true );
	}

	public MenuBar getMenuBar()
	{
		return menuBar;
	}

	/**
	 * Sets app update info (thread-safe)
	 */
	public void setAutoUpdateInfo( AutoUpdateInfo aui )
	{
		Platform.runLater(
			() -> {
				this.updateInfo = aui;
				mntmUpdate.setVisible( aui != null );
				mntmUpdate.setDisable( aui == null );
			}
		);
	}

	// --------------------------------------------------------------------------------

	public void onScanningStateChanged( boolean scanningInProgress )
	{
		Platform.runLater( () -> mntmRescan.setDisable( scanningInProgress ) );
	}

	@FXML
	private void onRescanClicked( ActionEvent event )
	{
		if ( mntmRescan.isDisable() ) return;

		ListState<ModFileInfo> tableState = modListController.getCurrentModsTableState();
		modsScanner.rescanMods( tableState );
	}

	@FXML
	private void onExtractClicked( ActionEvent event )
	{
		try {
			DatExtractionDialogController dialog = new DatExtractionDialogController(
				(Stage)menuBar.getScene().getWindow()
			);

			dialog.show();
		}
		catch ( IOException e ) {
			log.error( "Error while creating extract dialog.", e );
		}
	}

	@FXML
	private void onRepackClicked( ActionEvent event )
	{
		// TODO repack .dat archives
		throw new UnsupportedOperationException( "TODO" );
	}

	@FXML
	private void onPreferencesClicked( ActionEvent event )
	{
		// TODO preferences
		throw new UnsupportedOperationException( "TODO" );
	}

	@FXML
	private void onExitClicked( ActionEvent event )
	{
		Window window = menuBar.getScene().getWindow();
		window.fireEvent( new WindowEvent( window, WindowEvent.WINDOW_CLOSE_REQUEST ) );
	}

	@FXML
	private void onDeleteBackupsClicked( ActionEvent event )
	{
		String msg = ""
			+ "The mod manager uses backups to revert Into the Breach to a state without mods.\n"
			+ "You are about to delete them.\n"
			+ "\n"
			+ "The next time you click \"[i]Patch[/i]\", fresh backups will be created.\n"
			+ "\n"
			+ "Into the Breach [b]must be[/b] in a working unmodded state [b]before[/b] you click 'patch'.\n"
			+ "\n"
			+ "To get Into the Breach into a working unmodded state, you may need to reinstall it, "
			+ "or use Steam's \"[i]Verify integrity of game files[/i]\" feature.\n"
			+ "\n"
			+ "Are you sure you want to continue?";
		Alert alert = new Alert( AlertType.CONFIRMATION, msg, ButtonType.YES, ButtonType.NO );
		alert.setHeaderText( "Continue?" );
		alert.getDialogPane().setContent( UIUtilities.decoratedText( msg, alert.getDialogPane().widthProperty() ) );

		Optional<ButtonType> response = alert.showAndWait();

		if ( response.isPresent() && response.get().equals( ButtonType.YES ) ) {
			backupManager.deleteBackups();
		}
	}

	@FXML
	private void onVerifyIntegrityClicked( ActionEvent event )
	{
		Path steamExePath = Paths.get( config.getProperty( ITBConfig.STEAM_EXE_PATH, "" ) );

		if ( Files.exists( steamExePath ) ) {
			String verifyPrompt = ""
				+ "The mod manager is about to tell Steam to re-download Into the Breach's resources. "
				+ "This will get the game back to a working unmodded state, but it could take a while.\n"
				+ "\n"
				+ "You can do it manually like this...\n"
				+ "- Go to Steam's Library.\n"
				+ "- Right-click FTL, choose \"Properties\".\n"
				+ "- Click the \"Verify integrity of game files...\" button.\n"
				+ "\n"
				+ "If you do not have Steam, you will need to reinstall Into the Breach instead.\n"
				+ "\n"
				+ "Either way, you should delete the manager's backups as well.\n"
				+ "\n"
				+ "Are you sure you want to continue?";

			Alert alert = new Alert( AlertType.CONFIRMATION, verifyPrompt, ButtonType.YES, ButtonType.NO );
			alert.setHeaderText( "Continue?" );

			Optional<ButtonType> response = alert.showAndWait();

			if ( response.isPresent() && response.get().equals( ButtonType.YES ) ) {
				try {
					ITBUtilities.verifySteamGameCache( steamExePath, ITBUtilities.STEAM_APPID );
				}
				catch ( IOException e ) {
					log.error( "Couldn't tell Steam to verify the integrity of game files.", e );
				}
			}
		}
		else {
			String msg = "Steam's location was either not set or doesn't exist.";
			log.warn( msg );
			Alert alert = new Alert( AlertType.WARNING, msg, ButtonType.OK );
			alert.show();
		}
	}

	@FXML
	private void onAboutClicked( ActionEvent event )
	{
		Alert alert = new Alert( AlertType.NONE, null, ButtonType.OK );
		alert.setHeaderText( "About" );
		alert.setTitle( "About" );

		String msg = ""
			+ ITBModManager.APP_NAME + ", version " + ITBModManager.APP_VERSION + "\n"
			+ "Created by " + ITBModManager.APP_AUTHOR + "\n\n"
			+ "Based on Slipstream Mod Manager for FTL, by Vhati\n"
			+ "\n";

		VBox vbox = new VBox();
		Label label = new Label( msg );
		Hyperlink link = UIUtilities.createHyperlink( ITBModManager.APP_URL );
		vbox.getChildren().addAll( label, link );

		alert.getDialogPane().setContent( vbox );

		alert.show();
	}

	@FXML
	private void onUpdateAvailableClicked( Event event )
	{
		Alert alert = new Alert( AlertType.NONE, null, ButtonType.OK );
		alert.setHeaderText( "What's New - Version " + updateInfo.getLatestVersion().toString() );
		alert.setTitle( "Update Available" );

		VBox content = new VBox();

		// Links
		for ( Map.Entry<String, String> entry : updateInfo.getLatestURLs().entrySet() ) {
			content.getChildren().add( UIUtilities.createHyperlink( entry.getKey(), entry.getValue() ) );
		}

		if ( !content.getChildren().isEmpty() )
			content.getChildren().addAll( new Label() );

		// Notice
		if ( updateInfo.getNotice() != null && updateInfo.getNotice().length() > 0 ) {
			content.getChildren().addAll(
				UIUtilities.decoratedText( updateInfo.getNotice(), content.widthProperty() ),
				new Label()
			);
		}

		// Changelog
		StringBuilder buf = new StringBuilder();
		for ( Map.Entry<ComparableVersion, List<String>> entry : updateInfo.getChangelog().entrySet() ) {
			if ( ITBModManager.APP_VERSION.compareTo( entry.getKey() ) >= 0 ) break;

			if ( buf.length() > 0 ) buf.append( "\n" );
			buf.append( "[b]" ).append( entry.getKey() ).append( "[/b]:\n" );

			for ( String change : entry.getValue() ) {
				buf.append( "  - " ).append( change ).append( "\n" );
			}
		}

		content.getChildren().add( UIUtilities.decoratedText( buf.toString(), content.widthProperty() ) );

		ScrollPane root = new ScrollPane();
		root.setContent( content );
		root.setFitToWidth( true );
		root.setPrefHeight( 300 );
		root.setPrefWidth( 450 );

		alert.getDialogPane().setContent( root );

		alert.show();
	}
}
