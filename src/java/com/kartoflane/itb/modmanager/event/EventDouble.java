package com.kartoflane.itb.modmanager.event;

import java.util.function.BiConsumer;


/**
 * Event class for a callback with two arguments.
 * This class provides public {@link #broadcast()} and {@link #clearListeners()} methods.
 * It is intended to be visible only in the class that 'owns' the event -- listeners
 * interested in the event should only be able to see the base {@link Event} class, which
 * exposes methods for listener de/registration.
 * 
 * @param <T>
 *            type of the first argument received by listeners of this event
 * @param <U>
 *            type of the second argument received by listeners of this event
 */
public class EventDouble<T, U> extends Event<BiConsumer<T, U>>
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
	 * 
	 * @param arg1
	 *            the first event argument to pass
	 * @param arg2
	 *            the second event argument to pass
	 */
	public void broadcast( T arg1, U arg2 )
	{
		if ( listeners == null )
			return;
		// Iterates over a snapshot of the original collection
		listeners.forEach( listener -> safeNotify( listener, arg1, arg2 ) );
	}

	/**
	 * Notifies the listener of the event inside of a try-catch block, so that if a single
	 * listener throws an exception, it won't break the whole chain.
	 * 
	 * @param listener
	 *            the listener to notify
	 * @param args
	 *            the event arguments to pass
	 */
	private void safeNotify( BiConsumer<T, U> listener, T arg1, U arg2 )
	{
		try {
			listener.accept( arg1, arg2 );
		}
		catch ( RuntimeException e ) {
			Thread.currentThread().getUncaughtExceptionHandler().uncaughtException( Thread.currentThread(), e );
		}
	}
}
