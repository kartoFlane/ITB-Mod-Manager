package com.kartoflane.itb.modmanager.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.kartoflane.itb.modmanager.ITBModManager;
import com.kartoflane.itb.modmanager.core.BackupManager;
import com.kartoflane.itb.modmanager.core.ITBConfig;
import com.kartoflane.itb.modmanager.core.ModPatchThread;
import com.kartoflane.itb.modmanager.core.ModsScanner;
import com.kartoflane.itb.modmanager.util.ITBUtilities;
import com.kartoflane.itb.modmanager.util.UIUtilities;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import net.vhati.modmanager.core.AutoUpdateInfo;
import net.vhati.modmanager.core.ModFileInfo;
import net.vhati.modmanager.core.ModInfo;


public class ManagerWindow
{
	private static final Logger log = LogManager.getLogger();

	@FXML
	protected SplitPane splitPane;
	@FXML
	protected VBox contentPaneLeft;
	@FXML
	protected ScrollPane scrollPaneRight;
	@FXML
	protected VBox contentPaneRight;
	@FXML
	protected Button btnPatch;

	protected Stage stage;

	protected final ModListController modListController;
	protected final MenuController menuController;

	protected final ModsScanner modsScanner;
	protected final BackupManager backupManager;

	protected final ITBConfig config;

	private final File gameDir;


	public ManagerWindow(
		ITBConfig config,
		ModsScanner modsScanner, BackupManager backupManager,
		File gameDir
	) throws IOException
	{
		this.config = config;

		this.modsScanner = modsScanner;
		this.backupManager = backupManager;

		this.gameDir = gameDir;

		this.modListController = new ModListController();
		this.menuController = new MenuController( config, modsScanner, backupManager, modListController );

		createGUI();

		showUsageInformation();
	}

	public void show()
	{
		stage.show();
	}

	public void showUsageInformation()
	{
		removePreviousContentRight();

		String body = ""
			+ "- Drag to reorder mods.\n"
			+ "- Click the checkboxes (or doubleclick) to select mods to load.\n"
			+ "- Click 'Patch' to apply mods ( select none for vanilla ).\n"
			+ "\n"
			+ "Thanks for using this mod manager.\n"
			+ "Make sure to visit the forum for updates!";

		TextFlow flow = UIUtilities.decoratedText( body, contentPaneRight.widthProperty() );
		contentPaneRight.getChildren().add( flow );
	}

	public void showLocalModInfo( ModFileInfo modFileInfo )
	{
		removePreviousContentRight();

		Region r = modsScanner.buildModInfoPane( modFileInfo, contentPaneRight.widthProperty() );

		r.prefWidthProperty().bind( contentPaneRight.widthProperty() );
		contentPaneRight.getChildren().add( r );
	}

	protected void createGUI() throws IOException
	{
		FXMLLoader loader = new FXMLLoader( getClass().getResource( "ManagerWindow.fxml" ) );
		loader.setController( this );
		VBox root = loader.load();

		Scene scene = new Scene( root, 500, 400 );
		scene.getStylesheets().add( ITBModManager.class.getResource( "application.css" ).toExternalForm() );

		stage = new Stage();
		stage.setTitle( ITBModManager.APP_NAME );
		stage.setScene( scene );
		stage.setMinHeight( 250 );
		stage.setMinWidth( 250 );
		stage.setOnCloseRequest( this::onCloseRequest );

		scrollPaneRight.maxWidthProperty().bindBidirectional( scrollPaneRight.prefWidthProperty() );
		contentPaneRight.maxWidthProperty().bindBidirectional( contentPaneRight.prefWidthProperty() );
		contentPaneRight.prefWidthProperty().bind(
			Bindings.createDoubleBinding(
				() -> {
					double w = scrollPaneRight.getWidth();
					ScrollBar sb = UIUtilities.getScrollBar( scrollPaneRight, false );
					if ( sb != null && sb.isVisible() ) {
						w -= sb.getWidth();
					}
					return w;
				}, scrollPaneRight.widthProperty(), scrollPaneRight.heightProperty()
			)
		);

		root.getChildren().add( 0, menuController.getMenuBar() );

		TreeView<ModFileInfo> treeView = modListController.getTreeView();
		contentPaneLeft.getChildren().add( 0, treeView );

		treeView.setPrefHeight( Double.POSITIVE_INFINITY );
		treeView.maxHeightProperty().bind(
			Bindings.createDoubleBinding(
				() -> {
					Insets i = contentPaneLeft.getInsets();

					double w = root.getHeight();
					w -= btnPatch.getHeight();
					w -= menuController.getMenuBar().getHeight();
					w -= ( i.getTop() + i.getBottom() );
					w -= contentPaneLeft.getSpacing();

					return w;
				}, root.heightProperty()
			)
		);

		btnPatch.setDisable( gameDir == null );

		// Both objects have a lifetime of the entire application - no need to unregister listeners.
		modsScanner.modsTableStateAmendedEvent().addListener( modListController::onModsTableStateAmended );
		modsScanner.scanningStateChangedEvent().addListener( menuController::onScanningStateChanged );

		modListController.modSelectedEvent().addListener( this::onModSelected );
	}

	private void removePreviousContentRight()
	{
		contentPaneRight.getChildren().stream()
			.filter( n -> n instanceof Region )
			.map( n -> (Region)n )
			.forEach( r -> r.prefWidthProperty().unbind() );

		contentPaneRight.getChildren().clear();
	}

	// --------------------------------------------------------------------------------
	// Listeners

	public void onUpdateAvailable( AutoUpdateInfo aui )
	{
		menuController.setAutoUpdateInfo( aui );
	}

	@FXML
	protected void onPatchClicked( ActionEvent event )
	{
		if ( gameDir != null ) {
			List<File> modFiles = modListController.getSelectedMods().stream()
				.map( modFileInfo -> modFileInfo.getFile() )
				.collect( Collectors.toList() );

			try {
				ModPatchDialogController patchDialog = new ModPatchDialogController( stage, true );

				// Offer to run Into the Breach.
				if ( !"true".equals( config.getProperty( ITBConfig.NEVER_RUN_ITB, "false" ) ) ) {
					Path exePath = null;
					String[] exeArgs = null;

					// Try to run via Steam.
					if ( "true".equals( config.getProperty( ITBConfig.RUN_STEAM_ITB, "false" ) ) ) {
						String steamPath = config.getProperty( ITBConfig.STEAM_EXE_PATH );
						if ( steamPath.length() > 0 ) {
							exePath = Paths.get( steamPath );

							if ( Files.exists( exePath ) ) {
								exeArgs = new String[] { "-applaunch", ITBUtilities.STEAM_APPID };
							}
							else {
								log.warn( String.format( "%s does not exist: %s", ITBConfig.STEAM_EXE_PATH, exePath.toAbsolutePath() ) );
								exePath = null;
							}
						}

						if ( exePath == null ) {
							log.warn( "Steam executable could not be found, so Into the Breach will be launched directly" );
						}
					}

					// Try to run directly.
					if ( exePath == null ) {
						exePath = ITBUtilities.findGameExe( gameDir.toPath() );

						if ( exePath != null ) {
							exeArgs = new String[0];
						}
						else {
							log.warn( "Into the Breach executable could not be found" );
						}
					}

					if ( exePath != null ) {
						final Path finalExePath = exePath;
						final String[] finalExeArgs = exeArgs;
						patchDialog.setSuccessTask(
							() -> {
								if ( finalExePath != null ) {
									Alert alert = new Alert(
										AlertType.NONE, "Do you want to run the game now?",
										ButtonType.YES, ButtonType.NO
									);
									alert.setHeaderText( "Ready to play" );

									Optional<ButtonType> response = alert.showAndWait();

									if ( response.isPresent() && response.get() == ButtonType.YES ) {
										log.info( "Running Into the Breach..." );
										try {
											ITBUtilities.launchExe( finalExePath, finalExeArgs );
										}
										catch ( Exception e ) {
											log.error( "Error launching Into the Breach", e );
										}

										Platform.exit();
									}
								}
							}
						);
					}
				}

				List<ModInfo> modInfos = modFiles.stream()
					.map( modsScanner::getModInfo )
					.collect( Collectors.toList() );

				ModPatchThread patchThread = new ModPatchThread( backupManager, modInfos, modFiles, gameDir );
				patchThread.patchingProgressChangedEvent().addListener( patchDialog::patchingProgress );
				patchThread.patchingStatusChangedEvent().addListener( patchDialog::setStatusTextLater );
				patchThread.patchingModStartedEvent().addListener( patchDialog::patchingMod );
				patchThread.patchingEndedEvent().addListener( patchDialog::patchingEnded );

				patchThread.start();

				patchDialog.show();
			}
			catch ( IOException e ) {
				log.error( "Failed to create ProgressDialogController.", e );
			}
		}
	}

	private void onModSelected( ModFileInfo modFileInfo )
	{
		showLocalModInfo( modFileInfo );
	}

	private void onCloseRequest( WindowEvent e )
	{
		log.info( "Exiting." );

		try {
			config.write();
		}
		catch ( IOException ex ) {
			log.error( "Error while saving configuration file.", ex );
		}

		modsScanner.saveModsTableState( modListController.getCurrentModsTableState() );
		modsScanner.saveCachedModMetadata();

		Platform.exit();
	}
}
