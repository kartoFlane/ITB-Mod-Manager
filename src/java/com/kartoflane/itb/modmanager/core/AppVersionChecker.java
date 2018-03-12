package com.kartoflane.itb.modmanager.core;

import java.io.File;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.kartoflane.itb.modmanager.event.Event;
import com.kartoflane.itb.modmanager.event.EventSingle;
import com.kartoflane.itb.modmanager.lua.LuaAutoUpdateReader;
import com.kartoflane.itb.modmanager.util.Util;

import net.vhati.modmanager.core.AutoUpdateInfo;
import net.vhati.modmanager.core.ComparableVersion;
import net.vhati.modmanager.json.URLFetcher;


public class AppVersionChecker
{
	private static final Logger log = LogManager.getLogger();

	public static final String APP_UPDATE_URL = "https://raw.github.com/kartoFlane/ITB-Mod-Manager/master/skel_common/backup/auto_update.json";

	private final EventSingle<AutoUpdateInfo> updateAvailable = Event.create( null );

	private final ITBConfig config;
	private final ComparableVersion appVersion;
	private final File appUpdateFile;
	private final File appUpdateETagFile;


	public AppVersionChecker( ITBConfig config, ComparableVersion appVersion, File appUpdateFile, File appUpdateETagFile )
	{
		this.config = config;
		this.appVersion = appVersion;
		this.appUpdateFile = appUpdateFile;
		this.appUpdateETagFile = appUpdateETagFile;
	}

	public Event<Consumer<AutoUpdateInfo>> updateAvailableEvent()
	{
		return updateAvailable;
	}

	public void checkUpdateInfo()
	{
		// Load the cached info first, before downloading.
		if ( appUpdateFile.exists() ) {
			reloadAppUpdateInfo();
		}

		int appUpdateInterval = config.getPropertyAsInt( ITBConfig.APP_UPDATE_INTERVAL, 0 );
		boolean needAppUpdate = false;

		if ( appUpdateInterval > 0 ) {
			if ( appUpdateFile.exists() ) {
				// Check if the app update info is stale.
				if ( Util.isFileStale( appUpdateFile, appUpdateInterval ) ) {
					log.debug( String.format( "App update info is older than %d days", appUpdateInterval ) );
					needAppUpdate = true;
				}
				else {
					log.debug( "App update info isn't stale yet" );
				}
			}
			else {
				// App update file doesn't exist.
				needAppUpdate = true;
			}
		}

		if ( needAppUpdate ) {
			boolean fetched = URLFetcher.refetchURL( APP_UPDATE_URL, appUpdateFile, appUpdateETagFile );
			if ( fetched && appUpdateFile.exists() ) {
				reloadAppUpdateInfo();
			}
		}
	}

	private void reloadAppUpdateInfo()
	{
		final AutoUpdateInfo aui = LuaAutoUpdateReader.parse( appUpdateFile );
		if ( aui != null ) {
			boolean isUpdateAvailable = ( appVersion.compareTo( aui.getLatestVersion() ) < 0 );
			if ( isUpdateAvailable ) {
				updateAvailable.broadcast( aui );
			}
		}
	}
}
