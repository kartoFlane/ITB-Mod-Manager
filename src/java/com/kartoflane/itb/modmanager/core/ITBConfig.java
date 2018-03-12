package com.kartoflane.itb.modmanager.core;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

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
	public static final String RUN_STEAM_ITB =				"runSteamITB";
	public static final String NEVER_RUN_ITB =				"neverRunITB";
	// @formatter:on

	private File configFile = null;


	// public List<String> modorder = null; // ?
	// public Set<String> installedMods = null; // ?

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
			Map<String, String> commentsMap = new LinkedHashMap<String, String>();

			// @formatter:off
			commentsMap.put( GAME_PATH,				"The path to Into the Breach's game directory. If invalid, you'll be prompted." );
			commentsMap.put( PROFILES_PATH,			"The path to the game's profiles directory." );
			commentsMap.put( ALLOW_ZIP,				"Sets whether to treat .zip files as .itb files. Default: false." );
			commentsMap.put( APP_UPDATE_INTERVAL,	"If a number greater than 0, check for newer app versions every N days." );
			commentsMap.put( STEAM_DISTRO,			"If true, Into the Breach was installed via Steam. Stops the GUI asking for a path." );
			commentsMap.put( STEAM_EXE_PATH,		"The path to Steam's executable, if Into the Breach was installed via Steam." );
			commentsMap.put( RUN_STEAM_ITB,			"If true, the manager will use Steam to launch Into the Breach, if possible." );
			commentsMap.put( NEVER_RUN_ITB,			"If true, there will be no offer to run the game after patching. Default: false." );
			// @formatter:on

			int fieldWidth = commentsMap.keySet().stream()
				.map( key -> key.length() )
				.max( Integer::compare ).get();

			String comments = commentsMap.entrySet().stream()
				.map( entry -> String.format( " %-" + fieldWidth + "s - %s", entry.getKey(), entry.getValue() ) )
				.collect( Collectors.joining( "\n" ) );

			OutputStreamWriter writer = new OutputStreamWriter( out, "UTF-8" );
			store( writer, comments );
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
