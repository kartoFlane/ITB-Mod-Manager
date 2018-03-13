package com.kartoflane.itb.modmanager.util;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;


public class Util
{
	public static String stringValueOrEmpty( Object o )
	{
		return o == null ? "" : o.toString();
	}

	/**
	 * Returns true if a file is older than N days.
	 */
	public static boolean isFileStale( File f, int maxDays )
	{
		Calendar fileCal = Calendar.getInstance();
		fileCal.setTimeInMillis( f.lastModified() );
		fileCal.getTimeInMillis();  // Re-calculate calendar fields.

		Calendar freshCal = Calendar.getInstance();
		freshCal.add( Calendar.DATE, maxDays * -1 );
		freshCal.getTimeInMillis();  // Re-calculate calendar fields.

		return fileCal.compareTo( freshCal ) < 0;
	}

	/**
	 * Returns a ByteArrayInputStream over the specified string's bytes, with UTF-8 encoding.
	 */
	public static InputStream getInputStream( String input )
	{
		return new ByteArrayInputStream( input.getBytes( StandardCharsets.UTF_8 ) );
	}

	/**
	 * Returns true if the directory is empty, false otherwise.
	 * 
	 * @source https://stackoverflow.com/a/5937917
	 */
	public static boolean isDirectoryEmpty( final Path directory ) throws IOException
	{
		try ( DirectoryStream<Path> dirStream = Files.newDirectoryStream( directory ) ) {
			return !dirStream.iterator().hasNext();
		}
	}
}
