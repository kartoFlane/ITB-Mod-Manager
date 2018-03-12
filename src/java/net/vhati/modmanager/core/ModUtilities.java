package net.vhati.modmanager.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


public class ModUtilities {
	private static Pattern junkFilePtn = Pattern.compile( "[.]DS_Store$|(?:^|/)thumbs[.]db$|(?:^|/)[.]dropbox$|(?:^|/)~|~$|(?:^|/)#.+#$" );


	/**
	 * Encodes a string (throwing an exception on bad chars) to bytes in a stream.
	 * Line endings will not be normalized.
	 *
	 * @param text a String to encode
	 * @param encoding the name of a Charset
	 * @param description how error messages should refer to the string, or null
	 */
	public static InputStream encodeText( String text, String encoding, String description ) throws IOException {
		CharsetEncoder encoder = Charset.forName( encoding ).newEncoder();

		ByteArrayOutputStream tmpData = new ByteArrayOutputStream();
		Writer writer = new OutputStreamWriter( tmpData, encoder );
		writer.write( text );
		writer.flush();

		InputStream result = new ByteArrayInputStream( tmpData.toByteArray() );
		return result;
	}

	/**
	 * Determines text encoding for an InputStream and decodes its bytes as a string.
	 *
	 * CR and CR-LF line endings will be normalized to LF.
	 *
	 * @param is a stream to read
	 * @param description how error messages should refer to the stream, or null
	 */
	public static DecodeResult decodeText( InputStream is, String description ) throws IOException {
		String result = null;

		byte[] buf = new byte[4096];
		int len;
		ByteArrayOutputStream tmpData = new ByteArrayOutputStream();
		while ( (len = is.read( buf )) >= 0 ) {
			tmpData.write( buf, 0, len );
		}
		byte[] allBytes = tmpData.toByteArray();
		tmpData.reset();

		Map<byte[],String> boms = new LinkedHashMap<byte[],String>();
		boms.put( new byte[] {(byte)0xEF,(byte)0xBB,(byte)0xBF}, "UTF-8" );
		boms.put( new byte[] {(byte)0xFF,(byte)0xFE}, "UTF-16LE" );
		boms.put( new byte[] {(byte)0xFE,(byte)0xFF}, "UTF-16BE" );

		String encoding = null;
		byte[] bom = null;

		for ( Map.Entry<byte[],String> entry : boms.entrySet() ) {
			byte[] tmpBom = entry.getKey();
			byte[] firstBytes = Arrays.copyOfRange( allBytes, 0, tmpBom.length );
			if ( Arrays.equals( tmpBom, firstBytes ) ) {
				encoding = entry.getValue();
				bom = tmpBom;
				break;
			}
		}

		if ( encoding != null ) {
			// This may throw CharacterCodingException.
			CharsetDecoder decoder = Charset.forName( encoding ).newDecoder();
			ByteBuffer byteBuffer = ByteBuffer.wrap( allBytes, bom.length, allBytes.length-bom.length );
			result = decoder.decode( byteBuffer ).toString();
			allBytes = null;    // GC hint.
		}
		else {
			ByteBuffer byteBuffer = ByteBuffer.wrap( allBytes );

			Map<String,Exception> errorMap = new LinkedHashMap<String,Exception>();
			for ( String guess : new String[] {"UTF-8", "windows-1252"} ) {
				try {
					byteBuffer.rewind();
					byteBuffer.limit( allBytes.length );
					CharsetDecoder decoder = Charset.forName( guess ).newDecoder();
					result = decoder.decode( byteBuffer ).toString();
					encoding = guess;
					break;
				}
				catch ( CharacterCodingException e ) {
					errorMap.put( guess, e );
				}
			}
			if ( encoding == null ) {
				// All guesses failed!?
				String msg = String.format( "Could not guess encoding for %s.", (description!=null ? "\""+description+"\"" : "a file") );
				for ( Map.Entry<String,Exception> entry : errorMap.entrySet() ) {
					msg += String.format( "\nFailed to decode as %s: %s", entry.getKey(), entry.getValue() );
				}
				throw new IOException( msg );
			}
			allBytes = null;    // GC hint.
		}

		// Determine the original line endings.
		int eol = DecodeResult.EOL_NONE;
		Matcher m = Pattern.compile( "(\r(?!\n))|((?<!\r)\n)|(\r\n)" ).matcher( result );
		if ( m.find() ) {
			if ( m.group(3) != null ) eol = DecodeResult.EOL_CRLF;
			else if ( m.group(2) != null ) eol = DecodeResult.EOL_LF;
			else if ( m.group(1) != null ) eol = DecodeResult.EOL_CR;
		}

		result = result.replaceAll( "\r(?!\n)|\r\n", "\n" );
		return new DecodeResult( result, encoding, eol, bom );
	}

	/**
	 * Returns true if a path matches known junk files, false otherwise.
	 *
	 * This includes:
	 *   *.DS_Store
	 *   thumbs.db
	 *   .dropbox
	 *   #*#
	 *   ~*
	 *   *~
	 *
	 * @param innerPath a path with forward slashes
	 */
	public static boolean isJunkFile( String innerPath ) {
		return junkFilePtn.matcher( innerPath ).find();
	}

	/**
	 * Returns the latest modification time among files within a mod.
	 *
	 * If no files have timestamps, -1 is returned.
	 *
	 * Presumably, this time is measured in milliseconds since the
	 * epoch (00:00:00 GMT, January 1, 1970).
	 *
	 * @see java.util.zip.ZipEntry#getTime()
	 */
	public static long getModFileTime( File modFile ) throws IOException {
		long result = -1;

		try ( ZipInputStream zis = new ZipInputStream( new FileInputStream( modFile ) ) ) {
			ZipEntry item;
			while ( (item = zis.getNextEntry()) != null ) {
				long n = item.getTime();
				if ( n > result ) result = n;
				zis.closeEntry();
			}
		}

		return result;
	}


	/**
	 * A holder for results from decodeText().
	 *
	 * text     - The decoded string.
	 * encoding - The encoding used.
	 * eol      - A constant describing the original line endings.
	 * bom      - The BOM bytes found, or null.
	 */
	public static class DecodeResult {
		public static final int EOL_NONE = 0;
		public static final int EOL_CRLF = 1;
		public static final int EOL_LF = 2;
		public static final int EOL_CR = 3;

		public final String text;
		public final String encoding;
		public final int eol;
		public final byte[] bom;

		public DecodeResult( String text, String encoding, int eol, byte[] bom ) {
			this.text = text;
			this.encoding = encoding;
			this.eol = eol;
			this.bom = bom;
		}

		public String getEOLName() {
			if ( eol == EOL_CRLF ) return "CR-LF";
			if ( eol == EOL_LF ) return "LF";
			if ( eol == EOL_CR ) return "CR";
			return "None";
		}
	}
}
