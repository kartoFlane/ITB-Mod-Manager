package com.kartoflane.itb.modmanager.event;

import java.util.function.Consumer;
import java.util.function.Predicate;


/**
 * Event class for a callback with a single argument.
 * This class provides public {@link #broadcast()} and {@link #clearListeners()} methods.
 * It is intended to be visible only in the class that 'owns' the event -- listeners
 * interested in the event should only be able to see the base {@link EventBase} class, which
 * exposes methods for listener de/registration.
 * 
 * @param <T>
 *            type of the argument received by listeners of this event
 */
public class EventSingle<T> extends EventBase<Consumer<T>> implements Event.Single<T>
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
	 * @param args
	 *            the event arguments to pass
	 */
	public void broadcast( T args )
	{
		if ( listeners == null )
			return;
		// Iterates over a snapshot of the original collection
		listeners.forEach( listener -> safeNotify( listener, args ) );
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
	private void safeNotify( Consumer<T> listener, T args )
	{
		try {
			listener.accept( args );
		}
		catch ( RuntimeException e ) {
			Thread.currentThread().getUncaughtExceptionHandler().uncaughtException( Thread.currentThread(), e );
		}
	}

	@SuppressWarnings("unchecked")
	public Consumer<T> addListenerSelfCleaning( Consumer<T> listener, Predicate<T> selfCleanPredicate )
	{
		final Consumer<T>[] c = new Consumer[1];

		c[0] = arg -> {
			safeNotify( listener, arg );

			if ( selfCleanPredicate == null || selfCleanPredicate.test( arg ) )
				removeListener( c[0] );
		};

		return addListener( c[0] );
	}
}
