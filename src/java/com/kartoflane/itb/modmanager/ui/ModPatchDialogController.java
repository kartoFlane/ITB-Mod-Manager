package com.kartoflane.itb.modmanager.ui;

import java.io.File;
import java.io.IOException;

import javafx.stage.Stage;


public class ModPatchDialogController extends ProgressDialogController
{
	public ModPatchDialogController( Stage owner, boolean continueOnSuccess ) throws IOException
	{
		super( owner, continueOnSuccess );
		stage.setTitle( "Patching..." );
	}

	/**
	 * Updates the progress bar.
	 *
	 * If either arg is -1, the bar will become indeterminate.
	 *
	 * @param value
	 *            the new value
	 * @param max
	 *            the new maximum
	 */
	public void patchingProgress( int value, int max )
	{
		setProgressLater( value, max );
	}

	/**
	 * Non-specific activity.
	 *
	 * @param message
	 *            a string, or null
	 */
	public void patchingStatus( String message )
	{
		setStatusTextLater( message != null ? message : "..." );
	}

	/**
	 * A mod is about to be processed.
	 */
	public void patchingMod( final File modFile )
	{
		setStatusTextLater( String.format( "Installing mod \"%s\"...", modFile.getName() ) );
	}

	/**
	 * Patching ended.
	 *
	 * If anything went wrong, e may be non-null.
	 */
	public void patchingEnded( boolean outcome, Exception e )
	{
		setTaskOutcomeLater( outcome, e );
	}

	@Override
	protected void setTaskOutcome( boolean outcome, Exception e )
	{
		super.setTaskOutcome( outcome, e );
		if ( !stage.isShowing() )
			return;

		if ( succeeded == true ) {
			setStatusText( "Patching completed." );
		}
		else {
			setStatusText( String.format( "Patching failed: %s", e ) );
		}
	}
}
