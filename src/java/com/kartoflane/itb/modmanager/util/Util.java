package com.kartoflane.itb.modmanager.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Calendar;


public class Util
{
	/**
	 * Calculates an MD5 hash of data from an InputStream.
	 *
	 * The returned string will be lowercase hexadecimal.
	 */
	public static String calcStreamMD5( InputStream is ) throws NoSuchAlgorithmException, IOException
	{
		MessageDigest md = MessageDigest.getInstance( "MD5" );
		byte[] buf = new byte[4096];
		int len;
		while ( ( len = is.read( buf ) ) >= 0 ) {
			md.update( buf, 0, len );
		}

		byte[] hashBytes = md.digest();
		StringBuilder hashStringBuf = new StringBuilder();
		for ( byte b : hashBytes ) {
			hashStringBuf.append( Integer.toString( ( b & 0xff ) + 0x100, 16 ).substring( 1 ) );
		}
		return hashStringBuf.toString();
	}

	public static String calcFileMD5( File f ) throws NoSuchAlgorithmException, IOException
	{
		String result = null;
		FileInputStream is = null;
		try {
			is = new FileInputStream( f );
			result = calcStreamMD5( is );
		}
		finally {
			try {
				if ( is != null ) is.close();
			}
			catch ( Exception e ) {
			}
		}
		return result;
	}

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
}
