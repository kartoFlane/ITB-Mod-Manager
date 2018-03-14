package com.kartoflane.itb.modmanager.event;

import java.util.function.BooleanSupplier;


/**
 * Event class for an argumentless callback.
 * This class provides public {@link #broadcast()} and {@link #clearListeners()} methods.
 * It is intended to be visible only in the class that 'owns' the event -- listeners
 * interested in the event should only be able to see the base {@link EventBase} class, which
 * exposes methods for listener de/registration.
 */
public class EventCallback extends EventBase<Runnable> implements Event.Callback
{
	/**
	 * Clears the list of listeners of this event, so no dangling references are left over.
	 */
	public void clearListeners()
	{
		if ( listeners != null )
			listeners.clear();
	}

	/**
	 * Notifies all registered listeners of this event.
	 */
	public void broadcast()
	{
		if ( listeners == null )
			return;
		// Iterates over a snapshot of the original collection
		listeners.forEach( listener -> safeNotify( listener ) );
	}

	/**
	 * Notifies the listener of the event inside of a try-catch block, so that if a single
	 * listener throws an exception, it won't break the whole chain.
	 * 
	 * @param listener
	 *            the listener to notify
	 */
	private void safeNotify( Runnable listener )
	{
		try {
			listener.run();
		}
		catch ( RuntimeException e ) {
			e.printStackTrace();
		}
	}

	public Runnable addListenerSelfCleaning( Runnable listener, BooleanSupplier selfCleanPredicate )
	{
		final Runnable[] c = new Runnable[1];

		c[0] = () -> {
			safeNotify( listener );

			if ( selfCleanPredicate == null || selfCleanPredicate.getAsBoolean() )
				removeListener( c[0] );
		};

		return addListener( c[0] );
	}
}
