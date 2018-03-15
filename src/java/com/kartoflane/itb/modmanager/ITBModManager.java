package com.kartoflane.itb.modmanager;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.kartoflane.itb.modmanager.cli.ITBModManagerCLI;
import com.kartoflane.itb.modmanager.core.AppVersionChecker;
import com.kartoflane.itb.modmanager.core.BackupManager;
import com.kartoflane.itb.modmanager.core.ITBConfig;
import com.kartoflane.itb.modmanager.core.ModPatchThread;
import com.kartoflane.itb.modmanager.core.ModdedDatInfo;
import com.kartoflane.itb.modmanager.core.ModsScanner;
import com.kartoflane.itb.modmanager.ui.ManagerWindow;
import com.kartoflane.itb.modmanager.util.ITBUtilities;

import javafx.application.Application;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import net.vhati.modmanager.core.ComparableVersion;


public class ITBModManager extends Application
{
	private static final Logger log = LogManager.getLogger();

	public static final ComparableVersion APP_VERSION = new ComparableVersion( "dev-1" );
	public static final String APP_NAME = "Into The Breach Mod Manager";
	public static final String APP_AUTHOR = "kartoFlane";
	public static final String APP_URL = "http://www.subsetgames.com/forum/viewforum.php?f=26"; // TODO forum link

	private static Application APP = null;

	private final File backupDir = new File( "./backup/" );
	private final File modsDir = new File( "./mods/" );
	private final File configFile = new File( "modman.cfg" );

	private final File modsTableStateFile = new File( modsDir, "modorder.txt" );
	private final File metadataFile = new File( backupDir, "cached_metadata.lua" );

	private final File appUpdateFile = new File( backupDir, "auto_update.lua" );
	private final File appUpdateETagFile = new File( backupDir, "auto_update_etag.txt" );

	private File gameDir = null;
	private ITBConfig config = null;


	public static void main( String[] args )
	{
		log.info( "{}, version {}", APP_NAME, APP_VERSION );

		Thread.setDefaultUncaughtExceptionHandler(
			new Thread.UncaughtExceptionHandler() {
				@Override
				public void uncaughtException( Thread t, Throwable e )
				{
					log.error( "Uncaught exception in thread: " + t.toString(), e );
				}
			}
		);

		if ( args.length > 0 ) {
			APP = null;
			ITBModManagerCLI.main( args );
			return;
		}

		launch();
	}

	public static Application getApplication()
	{
		return APP;
	}

	@Override
	public void init()
	{
		APP = this;
	}

	@Override
	public void start( Stage primaryStage )
	{
		config = new ITBConfig( configFile );
		try {
			if ( configFile.exists() ) {
				config.read();
			}
		}
		catch ( IOException e ) {
			log.error( "Error while reading configuration file.", e );
		}

		if ( !backupDir.exists() )
			backupDir.mkdirs();
		if ( !modsDir.exists() )
			modsDir.mkdirs();

		Path gameDirPath = findGamePath( config );

		configSetup( config );

		if ( gameDirPath == null ) {
			Alert alert = new Alert(
				AlertType.ERROR,
				"Game resources were not found.\nPatching functionality will be disabled.",
				ButtonType.OK
			);
			alert.showAndWait();
			gameDir = null;
		}
		else {
			gameDir = gameDirPath.toFile();
		}

		try {
			AppVersionChecker appVersionChecker = new AppVersionChecker(
				config, APP_VERSION, appUpdateFile, appUpdateETagFile
			);
			ModsScanner modsScanner = new ModsScanner(
				config, modsDir, modsTableStateFile, metadataFile
			);

			File resourcesDir = new File( gameDir, "resources" );
			File scriptsDir = new File( gameDir, "scripts" );
			File mapsDir = new File( gameDir, "maps" );

			File resourceDatFile = new File( resourcesDir, "resource.dat" );
			File ambienceBankFile = new File( resourcesDir, "ambience.bank" );
			File masterBankFile = new File( resourcesDir, "Master Bank.bank" );
			File masterStringsBankFile = new File( resourcesDir, "Master Bank.strings.bank" );
			File musicBankFile = new File( resourcesDir, "music.bank" );
			File sfxBankFile = new File( resourcesDir, "sfx.bank" );

			BackupManager backupManager = new BackupManager(
				backupDir,
				scriptsDir, mapsDir,
				resourceDatFile,
				ambienceBankFile, masterBankFile, masterStringsBankFile, musicBankFile, sfxBankFile
			);

			ManagerWindow managerWindow = new ManagerWindow( config, modsScanner, backupManager, gameDir );
			appVersionChecker.updateAvailableEvent().addListener( managerWindow::onUpdateAvailable );
			managerWindow.show();

			Thread initThread = new Thread(
				() -> {
					try {
						if ( config.getPropertyAsBoolean( ITBConfig.LOAD_PREV_MODS, true ) ) {
							try {
								ModdedDatInfo datInfo = ModdedDatInfo.build( resourceDatFile, ModPatchThread.MODDED_INFO_INNERPATH );
								managerWindow.onInstalledModsLoaded( datInfo.listInstalledMods() );
							}
							catch ( IOException e ) {
								// resource.dat did not contain modded.info, *shrug*
							}
						}

						modsScanner.loadCachedModMetadata();
						appVersionChecker.checkUpdateInfo();
					}
					catch ( Exception e ) {
						log.error( "Error during ManagerWindow init.", e );
					}
				}
			);
			initThread.setDaemon( true );
			initThread.setPriority( Thread.MIN_PRIORITY );
			initThread.start();
		}
		catch ( IOException e ) {
			log.error( "Error while creating ManagerWindow.", e );
		}
	}

	// ----------------------------------------------------------------------------

	private void configSetup( ITBConfig config )
	{
		// Ask about Steam.
		if ( config.getProperty( ITBConfig.STEAM_DISTRO, "" ).length() == 0 ) {
			Alert alert = new Alert(
				AlertType.NONE, "Was Into the Breach installed via Steam?",
				ButtonType.YES, ButtonType.NO
			);

			Optional<ButtonType> response = alert.showAndWait();

			if ( response.isPresent() && response.get() == ButtonType.YES ) {
				config.setProperty( ITBConfig.STEAM_DISTRO, "true" );
			}
			else {
				config.setProperty( ITBConfig.STEAM_DISTRO, "false" );
			}
		}

		// If this is a Steam distro.
		if ( config.getPropertyAsBoolean( ITBConfig.STEAM_DISTRO, false ) ) {
			// Find Steam's executable.
			if ( config.getProperty( ITBConfig.STEAM_EXE_PATH, "" ).length() == 0 ) {
				Path steamExePath = ITBUtilities.findSteamExe();

				if ( steamExePath == null && System.getProperty( "os.name" ).startsWith( "Windows" ) ) {
					try {
						String registryExePath = ITBUtilities.queryRegistryKey( "HKCU\\Software\\Valve\\Steam", "SteamExe", "REG_SZ" );
						if ( registryExePath != null ) {
							steamExePath = Paths.get( registryExePath );
							steamExePath = Files.exists( steamExePath ) ? steamExePath : null;
						}
					}
					catch ( IOException e ) {
						log.error( "Error while querying registry for Steam's path", e );
					}
				}

				if ( steamExePath != null ) {
					Alert alert = new Alert(
						AlertType.CONFIRMATION, "Steam was found at:\n" + steamExePath + "\nIs this correct?",
						ButtonType.YES, ButtonType.NO
					);

					Optional<ButtonType> response = alert.showAndWait();

					if ( !response.isPresent() || response.get() == ButtonType.NO ) steamExePath = null;
				}

				if ( steamExePath == null ) {
					log.debug( "Steam was not located automatically. Prompting user for location" );

					String steamPrompt = ""
						+ "You will be prompted to locate Steam's executable.\n"
						+ "- Windows: Steam.exe\n"
						+ "- Linux: steam\n"
						+ "- OSX: Steam.app\n"
						+ "\n"
						+ "If you can't find it, you can cancel and set it later.";

					Alert alert = new Alert( AlertType.INFORMATION, steamPrompt, ButtonType.OK );
					alert.setHeaderText( "Find Steam" );
					alert.showAndWait();

					FileChooser chooser = new FileChooser();
					chooser.setTitle( "Find Steam.exe or steam or Steam.app" );
					File file = chooser.showOpenDialog( null );

					if ( file != null && file.exists() ) {
						steamExePath = file.toPath();
					}
				}

				if ( steamExePath != null ) {
					config.setProperty( ITBConfig.STEAM_EXE_PATH, steamExePath.toAbsolutePath().toString() );
					log.info( "Steam located at: " + steamExePath.toAbsolutePath() );
				}
			}

			if ( config.getProperty( ITBConfig.STEAM_EXE_PATH, "" ).length() > 0 ) {
				if ( config.getProperty( ITBConfig.RUN_STEAM_ITB, "" ).length() == 0 ) {
					ButtonType directly = new ButtonType( "Directly", ButtonBar.ButtonData.OK_DONE );
					ButtonType steam = new ButtonType( "Steam", ButtonBar.ButtonData.CANCEL_CLOSE );
					Alert alert = new Alert(
						AlertType.NONE,
						"Would you prefer to launch Into the Breach directly, or via Steam?",
						directly, steam
					);
					alert.setHeaderText( "How to launch?" );

					Optional<ButtonType> response = alert.showAndWait();

					if ( response.isPresent() ) {
						if ( response.get() == directly ) {
							config.setProperty( ITBConfig.RUN_STEAM_ITB, "false" );
						}
						else if ( response.get() == steam ) {
							config.setProperty( ITBConfig.RUN_STEAM_ITB, "true" );
						}
					}
				}
			}
		}

		if ( config.getProperty( ITBConfig.LOAD_PREV_MODS, "" ).length() == 0 ) {
			Alert alert = new Alert(
				AlertType.NONE,
				"Would you like the manager to load your selection of previously installed mods when it runs?",
				ButtonType.YES, ButtonType.NO
			);
			alert.setHeaderText( "" );

			Optional<ButtonType> response = alert.showAndWait();

			if ( response.isPresent() && response.get() == ButtonType.YES ) {
				config.setProperty( ITBConfig.LOAD_PREV_MODS, "true" );
			}
			else {
				config.setProperty( ITBConfig.LOAD_PREV_MODS, "false" );
			}
		}

		// Prompt if update_catalog is invalid or hasn't been set.
		if ( !config.getProperty( ITBConfig.APP_UPDATE_INTERVAL, "" ).matches( "^\\d+$" ) ) {
			String updatePrompt = ""
				+ "Would you like the manager to periodically check for updates?\n"
				+ "\n"
				+ "You can change this later.";

			Alert alert = new Alert(
				AlertType.NONE, updatePrompt,
				ButtonType.YES, ButtonType.NO
			);

			Optional<ButtonType> response = alert.showAndWait();

			if ( response.isPresent() && response.get() == ButtonType.YES ) {
				config.setProperty( ITBConfig.APP_UPDATE_INTERVAL, "4" );
			}
			else {
				config.setProperty( ITBConfig.APP_UPDATE_INTERVAL, "0" );
			}
		}

		try {
			config.write();
		}
		catch ( IOException ex ) {
			log.error( "Error while saving configuration file.", ex );
		}
	}

	/**
	 * Attempts to use game path set in config.
	 * If not set or invalid, attempts to find it automatically.
	 * If it's STILL invalid or not found, asks the user to find it themselves.
	 */
	private Path findGamePath( ITBConfig config )
	{
		String gamePathConfig = config.getProperty( ITBConfig.GAME_PATH, null );
		Path gameDirPath = null;

		if ( gamePathConfig == null || gamePathConfig.length() == 0 ) {
			log.debug( "No {} previously set.", ITBConfig.GAME_PATH );
		}
		else {
			log.info( "Using game path from config: {}", gamePathConfig );
			gameDirPath = Paths.get( gamePathConfig );
			if ( ITBUtilities.isGameDirValid( gameDirPath ) == false ) {
				log.error( "Config's {} does not exist, or it is invalid.", ITBConfig.GAME_PATH );
				gameDirPath = null;
			}
		}

		if ( gameDirPath == null ) {
			gameDirPath = ITBUtilities.findGameDirectory();

			if ( gameDirPath != null ) {
				Alert alert = new Alert(
					AlertType.CONFIRMATION,
					String.format( "Into The Breach game directory was found in:%n%s%nIs this correct?", gameDirPath ),
					ButtonType.YES, ButtonType.NO
				);
				alert.showAndWait();

				if ( alert.getResult() == ButtonType.NO ) {
					gameDirPath = null;
				}
			}

			if ( gameDirPath == null ) {
				log.debug( "Game directory was not located automatically. Prompting user for location." );
				gameDirPath = ITBUtilities.promptForGameDirectory( null );
			}

			if ( gameDirPath != null ) {
				gameDirPath = gameDirPath.toAbsolutePath();
				config.setProperty( ITBConfig.GAME_PATH, gameDirPath.toString() );
				log.info( "Game directory located at: {}", gameDirPath );
			}
		}

		return gameDirPath;
	}
}
