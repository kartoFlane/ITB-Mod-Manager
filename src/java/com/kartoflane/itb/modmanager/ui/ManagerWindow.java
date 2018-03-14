package com.kartoflane.itb.modmanager.ui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.kartoflane.itb.modmanager.ITBModManager;
import com.kartoflane.itb.modmanager.core.BackupManager;
import com.kartoflane.itb.modmanager.core.ITBConfig;
import com.kartoflane.itb.modmanager.core.ModPatchThread;
import com.kartoflane.itb.modmanager.core.ModsScanner;
import com.kartoflane.itb.modmanager.util.ITBUtilities;
import com.kartoflane.itb.modmanager.util.StyledTextBuilder;
import com.kartoflane.itb.modmanager.util.UIUtilities;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
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
	protected Button btnToggleAll;
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

		if ( config.getPropertyAsBoolean( ITBConfig.REMEMBER_GEOMETRY, true ) ) {
			setGeometryFromConfig();
		}
	}

	public void show()
	{
		stage.show();
		showUsageInformation();

		if ( config.getPropertyAsBoolean( ITBConfig.REMEMBER_GEOMETRY, true ) ) {
			setGeometryFromConfig();
		}
	}

	public void showUsageInformation()
	{
		removePreviousContentRight();

		String body = ""
			+ "- Drag to reorder mods.\n"
			+ "- Select mods to install by clicking the checkboxes "
			+ "( you can also doubleclick, or press spacebar/enter )\n"
			+ "- Click 'Patch' to apply mods ( select none for vanilla ).\n"
			+ "\n"
			+ "Thanks for using this mod manager.\n"
			+ "Make sure to visit the forum for updates!";

		TextFlow flow = StyledTextBuilder.build( body, contentPaneRight.widthProperty() );
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
		stage.setMinWidth(
			contentPaneLeft.getMinWidth() + contentPaneLeft.getPadding().getRight()
				+ scrollPaneRight.getMinWidth() + contentPaneRight.getPadding().getLeft()
		);
		stage.setOnCloseRequest( this::onCloseRequest );

		scrollPaneRight.maxWidthProperty().bindBidirectional( scrollPaneRight.prefWidthProperty() );
		contentPaneRight.maxWidthProperty().bindBidirectional( contentPaneRight.prefWidthProperty() );
		contentPaneRight.prefWidthProperty().bind(
			Bindings.createDoubleBinding(
				() -> {
					double w = scrollPaneRight.getWidth();
					ScrollBar sb = UIUtilities.getScrollBar( scrollPaneRight, Orientation.VERTICAL );
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

	private void setGeometryFromConfig()
	{
		String geometry = config.getProperty( ITBConfig.MANAGER_GEOMETRY );

		if ( geometry != null ) {
			double[] xywh = new double[4];
			double dividerLoc = -1;

			Matcher m = Pattern.compile( "([^;,]+),(\\d+\\.?\\d+)" ).matcher( geometry );

			try {
				while ( m.find() ) {
					if ( m.group( 1 ).equals( "x" ) )
						xywh[0] = Integer.parseInt( m.group( 2 ) );
					else if ( m.group( 1 ).equals( "y" ) )
						xywh[1] = Integer.parseInt( m.group( 2 ) );
					else if ( m.group( 1 ).equals( "w" ) )
						xywh[2] = Integer.parseInt( m.group( 2 ) );
					else if ( m.group( 1 ).equals( "h" ) )
						xywh[3] = Integer.parseInt( m.group( 2 ) );
					else if ( m.group( 1 ).equals( "divider" ) )
						dividerLoc = NumberFormat.getInstance( Locale.ENGLISH ).parse( m.group( 2 ) ).doubleValue();
				}

				boolean badGeometry = Arrays.stream( xywh ).anyMatch( d -> d <= 0 );

				if ( !badGeometry && dividerLoc > 0 ) {
					stage.setX( xywh[0] );
					stage.setY( xywh[1] );
					stage.setWidth( xywh[2] );
					stage.setHeight( xywh[3] );
					splitPane.setDividerPosition( 0, dividerLoc );
				}
			}
			catch ( ParseException e ) {
				log.error( "Error parsing manager geometry: ", e );
			}
		}
	}

	// --------------------------------------------------------------------------------
	// Listeners

	public void onUpdateAvailable( AutoUpdateInfo aui )
	{
		menuController.setAutoUpdateInfo( aui );
	}

	@FXML
	private void onToggleAllClicked( ActionEvent event )
	{
		modListController.toggleAllItemSelection();
	}

	@FXML
	private void onPatchClicked( ActionEvent event )
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
		if ( modFileInfo == null ) {
			showUsageInformation();
		}
		else {
			showLocalModInfo( modFileInfo );
		}
	}

	private void onCloseRequest( WindowEvent e )
	{
		log.info( "Exiting." );

		if ( config.getPropertyAsBoolean( ITBConfig.REMEMBER_GEOMETRY, true ) ) {
			if ( !stage.isMaximized() && !stage.isIconified() && !stage.isFullScreen() ) {
				String geometry = String.format(
					Locale.ENGLISH,
					"x,%.0f;y,%.0f;w,%.0f;h,%.0f;divider,%.3f",
					stage.getX(), stage.getY(),
					stage.getWidth(), stage.getHeight(),
					splitPane.getDividerPositions()[0]
				);

				config.setProperty( ITBConfig.MANAGER_GEOMETRY, geometry );
			}
		}

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
