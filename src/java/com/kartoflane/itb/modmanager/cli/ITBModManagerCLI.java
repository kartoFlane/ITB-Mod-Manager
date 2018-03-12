package com.kartoflane.itb.modmanager.cli;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


public class ITBModManagerCLI
{
	private static final Logger log = LogManager.getLogger();

	private static Thread.UncaughtExceptionHandler exceptionHandler = null;


	public static void main( String[] args )
	{
		exceptionHandler = new Thread.UncaughtExceptionHandler() {
			@Override
			public void uncaughtException( Thread t, Throwable e )
			{
				log.error( "Uncaught exception in thread: " + t.toString(), e );
				System.exit( 1 );
			}
		};

		// TODO
		throw new UnsupportedOperationException( "Not yet implemented" );
	}
}
