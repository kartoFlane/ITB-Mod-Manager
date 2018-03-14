package com.kartoflane.itb.modmanager.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


@SuppressWarnings("serial")
public class ITBConfig extends Properties
{
	private static final Logger log = LogManager.getLogger();

	// @formatter:off
	// All keys the config can save
	public static final String GAME_PATH =				"gamePath";
	public static final String PROFILES_PATH =			"profilesPath";
	public static final String ALLOW_ZIP =				"allowZip";
	public static final String APP_UPDATE_INTERVAL =	"appUpdateInterval";
	public static final String STEAM_DISTRO =			"steamDistro";
	public static final String STEAM_EXE_PATH =			"steamExePath";
	public static final String RUN_STEAM_ITB =			"runSteamITB";
	public static final String NEVER_RUN_ITB =			"neverRunITB";
	public static final String REMEMBER_GEOMETRY =		"rememberGeometry";
	public static final String MANAGER_GEOMETRY =		"managerGeometry";
	// @formatter:on

	private File configFile = null;


	public ITBConfig( File configFile )
	{
		if ( configFile == null ) {
			throw new IllegalArgumentException( "configFile must not be null!" );
		}

		this.configFile = configFile;
	}

	public ITBConfig( ITBConfig sourceConfig )
	{
		configFile = sourceConfig.configFile;
		putAll( sourceConfig );
	}

	/**
	 * Returns the file in which configuration data is saved.
	 */
	public File getFile()
	{
		return configFile;
	}

	/**
	 * Reads values from the config file and saves them in this config object.
	 */
	public void read() throws FileNotFoundException, IOException
	{
		log.trace( "Reading config data from {}", configFile );

		try ( FileReader reader = new FileReader( configFile ) ) {
			load( reader );
		}
	}

	public void write() throws IOException
	{
		log.trace( "Writing config data to {}", configFile );

		try ( OutputStream out = new FileOutputStream( configFile ) ) {
			Map<String, String> userFieldsMap = new LinkedHashMap<>();
			Map<String, String> appFieldsMap = new LinkedHashMap<>();

			// @formatter:off
			userFieldsMap.put( GAME_PATH,			"The path to Into the Breach's game directory. If invalid, you'll be prompted." );
			userFieldsMap.put( PROFILES_PATH,		"The path to the game's profiles directory." );
			userFieldsMap.put( ALLOW_ZIP,			"Sets whether to treat .zip files as .itb files. Default: false." );
			userFieldsMap.put( APP_UPDATE_INTERVAL,	"If a number greater than 0, check for newer app versions every N days." );
			userFieldsMap.put( STEAM_DISTRO,		"If true, Into the Breach was installed via Steam. Stops the GUI asking for a path." );
			userFieldsMap.put( STEAM_EXE_PATH,		"The path to Steam's executable, if Into the Breach was installed via Steam." );
			userFieldsMap.put( RUN_STEAM_ITB,		"If true, the manager will use Steam to launch Into the Breach, if possible." );
			userFieldsMap.put( NEVER_RUN_ITB,		"If true, there will be no offer to run the game after patching. Default: false." );
			userFieldsMap.put( REMEMBER_GEOMETRY,	"If true, window geometry will be saved on exit and restored on startup." );

			appFieldsMap.put( MANAGER_GEOMETRY,		"Last saved position/size/etc of the main window." );
			// @formatter:on

			int fieldWidth = Stream.of( userFieldsMap, appFieldsMap )
				.map( m -> m.keySet() )
				.flatMap( Collection::stream )
				.map( key -> key.length() )
				.max( Integer::compare ).get();

			StringBuilder buf = new StringBuilder( "\n" );
			buf.append(
				userFieldsMap.entrySet().stream()
					.map( entry -> String.format( " %-" + fieldWidth + "s - %s", entry.getKey(), entry.getValue() ) )
					.collect( Collectors.joining( "\n" ) )
			);
			buf.append( "\n" );
			buf.append(
				appFieldsMap.entrySet().stream()
					.map( entry -> String.format( " %-" + fieldWidth + "s - %s", entry.getKey(), entry.getValue() ) )
					.collect( Collectors.joining( "\n" ) )
			);

			OutputStreamWriter writer = new OutputStreamWriter( out, StandardCharsets.UTF_8 );
			store( writer, buf.toString() );
			writer.flush();
		}
	}

	// ------------------------------------------------------------------------

	public int getPropertyAsInt( String key, int defaultValue )
	{
		return Integer.parseInt( getProperty( key, Integer.toString( defaultValue ) ) );
	}

	public boolean getPropertyAsBoolean( String key, boolean defaultValue )
	{
		return Boolean.parseBoolean( getProperty( key, Boolean.toString( defaultValue ) ) );
	}
}
