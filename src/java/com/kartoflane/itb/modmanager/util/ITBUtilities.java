package com.kartoflane.itb.modmanager.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;


public class ITBUtilities
{
	public static final String GAME_NAME = "Into the Breach";
	public static final String STEAM_APPID = "590380";
	public static final String GOGGALAXY_APPID = "-1"; // TODO Galaxy appid


	/**
	 * Returns Into The Breach game directory, or null.
	 */
	public static Path findGameDirectory()
	{
		List<Path> candidates = getGameDirectoryCandidates();

		Optional<Path> opt = candidates.stream()
			.filter( ITBUtilities::isGameDirValid )
			.findFirst();

		return opt.isPresent() ? opt.get() : null;
	}

	/**
	 * Returns a list containing all candidate paths across all platforms, where the game could be installed.
	 */
	public static List<Path> getGameDirectoryCandidates()
	{
		String steamPath = "Steam/steamapps/common/Into The Breach";
		String steamPathAlt = "Steam/SteamApps/common/Into The Breach";
		String gogPath = "GOG.com/Into The Breach";
		String gogGalaxyPath = "GOG Galaxy/Games";
		String humblePath = "Into The Breach";

		String programFiles86 = System.getenv( "ProgramFiles(x86)" );
		String programFiles = System.getenv( "ProgramFiles" );

		String home = System.getProperty( "user.home" );

		String xdgDataHome = System.getenv( "XDG_DATA_HOME" );
		if ( xdgDataHome == null && home != null )
			xdgDataHome = home + "/.local/share";

		String winePrefix = System.getProperty( "WINEPREFIX" );
		if ( winePrefix == null && home != null )
			winePrefix = home + "/.wine";

		List<Path> candidates = new ArrayList<>();

		// Windows - Steam, GOG, Humble Bundle.
		if ( programFiles86 != null ) {
			candidates.add( Paths.get( programFiles86, steamPath ) );
			candidates.add( Paths.get( programFiles86, gogPath ) );
			candidates.add( Paths.get( programFiles86, gogGalaxyPath ) );
			candidates.add( Paths.get( programFiles86, humblePath ) );
		}
		if ( programFiles != null ) {
			candidates.add( Paths.get( programFiles, steamPath ) );
			candidates.add( Paths.get( programFiles, gogPath ) );
			candidates.add( Paths.get( programFiles, gogGalaxyPath ) );
			candidates.add( Paths.get( programFiles, humblePath ) );
		}
		// Linux - Steam.
		if ( xdgDataHome != null ) {
			// TODO: Verify if correct
			candidates.add( Paths.get( xdgDataHome, steamPath ) );
			candidates.add( Paths.get( xdgDataHome, steamPathAlt ) );
		}
		if ( home != null ) {  // I think .steam/ contains symlinks to the paths above.
			// TODO: Verify if correct
			candidates.add( Paths.get( home, ".steam/steam/steamapps/common/Into The Breach" ) );
			candidates.add( Paths.get( home, ".steam/steam/SteamApps/common/Into The Breach" ) );
		}
		// Linux - Wine.
		if ( winePrefix != null ) {
			// TODO: Verify if correct
			candidates.add( Paths.get( winePrefix, "drive_c/Program Files (x86)/", gogPath ) );
			candidates.add( Paths.get( winePrefix, "drive_c/Program Files (x86)/", humblePath ) );
			candidates.add( Paths.get( winePrefix, "drive_c/Program Files/", gogPath ) );
			candidates.add( Paths.get( winePrefix, "drive_c/Program Files/", humblePath ) );
		}
		// OSX - Steam.
		if ( home != null ) {
			// TODO: Verify if correct
			candidates.add( Paths.get( home, "Library/Application Support", steamPath, "ITB.app/Contents" ) );
			candidates.add( Paths.get( home, "Library/Application Support", steamPath, "ITB.app/Contents" ) );
		}
		// OSX - Standalone.
		// TODO: Verify if correct
		candidates.add( Paths.get( "/Applications/ITB.app/Contents/Resources" ) );

		return candidates;
	}

	public static Path findProfilesDirectory()
	{
		// TODO: Other platforms
		String userHome = System.getProperty( "user.home" );

		return Paths.get( userHome, "Documents", "My Games", "Into The Breach" );
	}

	/**
	 * Modally prompts the user for Into The Breach game directory.
	 * 
	 * @param parentStage
	 *            parent for the chooser dialog, or null.
	 * @return path to the selected directory, or null if user aborted selection
	 */
	public static Path promptForGameDirectory( Stage parentStage )
	{
		String message = ""
			+ "You will now be prompted to locate Into The Breach manually.\n"
			+ "Select the game's directory, or select 'Into The Breach.app', if you're on OSX.";

		Alert alert = new Alert( AlertType.INFORMATION, message, ButtonType.OK );
		alert.showAndWait();

		DirectoryChooser dirChooser = new DirectoryChooser();
		dirChooser.setTitle( "Find Into The Breach game directory" );

		File selectedDir = dirChooser.showDialog( parentStage );

		if ( selectedDir != null && isGameDirValid( selectedDir.toPath() ) ) {
			return selectedDir.toPath();
		}

		return null;
	}

	/**
	 * Returns the executable that will launch FTL, or null.
	 *
	 * FTL 1.01-1.5.13:
	 * Windows
	 * {FTL dir}/resources/*.dat
	 * {FTL dir}/FTLGame.exe
	 * Linux
	 * {FTL dir}/data/resources/*.dat
	 * {FTL dir}/data/FTL
	 * OSX
	 * {FTL dir}/Contents/Resources/*.dat
	 * {FTL dir}
	 *
	 * FTL 1.6.1:
	 * Windows
	 * {FTL dir}/*.dat
	 * {FTL dir}/FTLGame.exe
	 * Linux
	 * {FTL dir}/data/*.dat
	 * {FTL dir}/data/FTL
	 * OSX
	 * {FTL dir}/Contents/Resources/*.dat
	 * {FTL dir}
	 *
	 * On Windows, FTLGame.exe is a binary.
	 * On Linux, FTL is a script.
	 * On OSX, FTL.app is the grandparent dir itself (a bundle).
	 */
	public static Path findGameExe( Path gameDir )
	{
		Path result = null;

		if ( System.getProperty( "os.name" ).startsWith( "Windows" ) ) {
			for ( Path candidateDir : new Path[] { gameDir } ) {
				if ( candidateDir == null ) continue;

				Path exeFile = candidateDir.resolve( "FTLGame.exe" );
				if ( Files.exists( exeFile ) ) {
					result = exeFile;
					break;
				}
			}
		}
		else if ( System.getProperty( "os.name" ).equals( "Linux" ) ) {
			for ( Path candidateDir : new Path[] { gameDir } ) {
				if ( candidateDir == null ) continue;

				Path exeFile = candidateDir.resolve( "FTL" );
				if ( Files.exists( exeFile ) ) {
					result = exeFile;
					break;
				}
			}
		}
		else if ( System.getProperty( "os.name" ).contains( "OS X" ) ) {
			// ITB.app/Contents/Resources/
			Path contentsDir = gameDir.resolve( "Contents" );
			Path bundleDir = gameDir;
			if ( contentsDir != null ) {
				Path infoPlist = contentsDir.resolve( "Info.plist" );
				if ( Files.exists( infoPlist ) ) {
					result = bundleDir;
				}
			}
		}

		return result;
	}

	/**
	 * Checks whether the specified path contains 'maps', 'scripts' directories
	 * and 'resources/resource.dat' file.
	 * 
	 * If the specified path is a Mac .app, then this method automatically looks
	 * for the above in the 'Contents' directory in the .app.
	 * 
	 * @param path
	 *            path to the game's directory
	 * @return true if the directory is valid, false otherwise
	 */
	public static boolean isGameDirValid( Path path )
	{
		if ( Files.notExists( path ) || !Files.isDirectory( path ) )
			return false;

		if ( path.endsWith( ".app" ) ) {
			// MacOS handling
			Path contentsPath = path.resolve( "Contents" );
			if ( Files.exists( contentsPath ) && Files.isDirectory( contentsPath ) ) {
				path = contentsPath;
			}
		}

		Path maps = path.resolve( "maps" );
		Path scripts = path.resolve( "scripts" );
		Path resourceDat = path.resolve( Paths.get( "resources", "resource.dat" ) );

		return Files.exists( maps ) && Files.exists( scripts ) && Files.exists( resourceDat );
	}

	/**
	 * Returns the executable that will launch Steam, or null.
	 *
	 * On Windows, "Steam.exe".
	 * On Linux, "steam" is a script. ( http://moritzmolch.com/815 )
	 * On OSX, "Steam.app" is a bundle.
	 *
	 * The definitive Windows registry will not be checked.
	 * Key,Name,Type: "HKCU\\Software\\Valve\\Steam", "SteamExe", "REG_SZ".
	 *
	 * The args to launch Into the Breach are: ["-applaunch", STEAM_APPID]
	 *
	 * @see #queryRegistryKey(String, String, String)
	 */
	public static Path findSteamExe()
	{
		String programFiles86 = System.getenv( "ProgramFiles(x86)" );
		String programFiles = System.getenv( "ProgramFiles" );

		String osName = System.getProperty( "os.name" );

		List<Path> candidates = new ArrayList<Path>();

		if ( osName.startsWith( "Windows" ) ) {
			if ( programFiles86 != null ) {
				candidates.add( Paths.get( programFiles86, "Steam/Steam.exe" ) );
			}
			if ( programFiles != null ) {
				candidates.add( Paths.get( programFiles, "Steam/Steam.exe" ) );
			}
		}
		else if ( osName.equals( "Linux" ) ) {
			candidates.add( Paths.get( "/usr/bin/steam" ) );
		}
		else if ( osName.contains( "OS X" ) ) {
			candidates.add( Paths.get( "/Applications/Steam.app" ) );
		}

		Path result = null;

		for ( Path candidate : candidates ) {
			if ( Files.exists( candidate ) ) {
				result = candidate;
				break;
			}
		}

		return result;
	}

	/**
	 * Tells Steam to "verify game cache".
	 *
	 * This will spawn a process to notify Steam and exit immediately.
	 *
	 * Steam will start, if not already running, and a popup with progress bar
	 * will appear.
	 *
	 * For Into the Breach, this method amounts to running:
	 * Steam.exe "steam://validate/590380"
	 *
	 * Steam registers itself with the OS as a custom URI handler. The URI gets
	 * passed as an argument when a "steam://" address is visited.
	 */
	public static Process verifySteamGameCache( Path exeFile, String appId ) throws IOException
	{
		if ( appId == null || appId.length() == 0 ) throw new IllegalArgumentException( "No Steam APP_ID was provided" );

		String[] exeArgs = new String[] { "steam://validate/" + appId };
		return launchExe( exeFile, exeArgs );
	}

	/**
	 * Launches an executable.
	 *
	 * On Windows, *.exe.
	 * On Linux, a binary or script.
	 * On OSX, an *.app bundle dir.
	 *
	 * OSX bundles are executed with: "open -a bundle.app".
	 *
	 * @param exeFile
	 *            see findGameExe() or findSteamExe()
	 * @param exeArgs
	 *            arguments for the executable
	 * @return a Process object, or null
	 */
	public static Process launchExe( Path exeFile, String... exeArgs ) throws IOException
	{
		if ( exeFile == null ) return null;
		if ( exeArgs == null ) exeArgs = new String[0];

		Process result = null;
		ProcessBuilder pb = null;
		if ( System.getProperty( "os.name" ).contains( "OS X" ) ) {
			String[] args = new String[3 + exeArgs.length];
			args[0] = "open";
			args[1] = "-a";
			args[2] = exeFile.toAbsolutePath().toString();
			System.arraycopy( exeArgs, 0, args, 3, exeArgs.length );

			pb = new ProcessBuilder( args );
		}
		else {
			String[] args = new String[1 + exeArgs.length];
			args[0] = exeFile.toAbsolutePath().toString();
			System.arraycopy( exeArgs, 0, args, 1, exeArgs.length );

			pb = new ProcessBuilder( args );
		}
		if ( pb != null ) {
			pb.directory( exeFile.getParent().toFile() );
			result = pb.start();
		}
		return result;
	}

	/**
	 * Returns a value from the Windows registry, by scraping reg.exe, or null.
	 *
	 * This is equivalent to: reg.exe query {key} /v {valueName} /t {valueType}
	 *
	 * This view will not be jailed in Wow6432Node, even if Java is?
	 * Characters outside windows-1252 are unsupported (results will be mangled).
	 *
	 * Bad unicode example: "HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Console\\TrueTypeFont", "932", "REG_SZ".
	 *
	 * @param key
	 *            a backslash path starting with HKLM, HKCU, HKCR, HKU, HKCC
	 * @param valueName
	 *            a value name, or "" for the "(Default)" value
	 * @param valueType
	 *            REG_SZ ("Abc"), REG_DWORD ("0x1"), REG_BINARY ("44E09C"), etc
	 */
	public static String queryRegistryKey( String key, String valueName, String valueType ) throws IOException
	{
		if ( !System.getProperty( "os.name" ).startsWith( "Windows" ) ) return null;
		if ( key == null || valueType == null || key.length() * valueType.length() == 0 ) {
			throw new IllegalArgumentException( "key and valueType cannot be null or empty" );
		}

		BufferedReader r = null;
		try {
			String regExePath = "reg.exe";
			String winDir = System.getenv( "windir" );

			if ( winDir != null && winDir.length() > 0 ) {
				// When Java's in Wow64 redirection jail, sysnative is a virtual dir with the 64bit commands.
				// I don't know if this will ever happen to Java.
				File unWowRegExeFile = new File( winDir, "sysnative\\reg.exe" );
				if ( unWowRegExeFile.exists() ) regExePath = unWowRegExeFile.getAbsolutePath();
			}

			String[] steamRegArgs = new String[] { regExePath, "query", key, "/v", valueName, "/t", valueType };
			Pattern regPtn = Pattern
				.compile( Pattern.quote( ( ( valueName != null ) ? valueName : "(Default)" ) ) + "\\s+" + Pattern.quote( valueType ) + "\\s+(.*)" );

			Process p = new ProcessBuilder( steamRegArgs ).start();
			p.waitFor();
			if ( p.exitValue() == 0 ) {
				r = new BufferedReader( new InputStreamReader( p.getInputStream(), "windows-1252" ) );
				Matcher m;
				String line;
				while ( ( line = r.readLine() ) != null ) {
					if ( ( m = regPtn.matcher( line ) ).find() ) {
						return m.group( 1 );
					}
				}
			}
		}
		catch ( InterruptedException e ) {  // *shrug*
			Thread.currentThread().interrupt();  // Set interrupt flag.
		}
		finally {
			try {
				if ( r != null ) r.close();
			}
			catch ( IOException e ) {
			}
		}

		return null;
	}
}
