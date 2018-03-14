package com.kartoflane.itb.modmanager.event;

import java.util.function.BiConsumer;
import java.util.function.Consumer;


/**
 * This class is meant to emulate C#-like events in syntax, and streamline the implementation of
 * event-driven communication.
 * 
 * 
 * <h1>Thread Safety</h1>
 * <p>
 * Theoretically this class should be thread-safe (follows recommended practices on multi-threaded
 * event delivery), but this has not been thoroughly tested.
 * </p>
 * <p>
 * An important thing to note when using this class with threads: listeners have their handler
 * methods executed on the thread that invoked the {@link #broadcast(Object)} method.
 * Consider using a lock, or executing the handler code inside of a {@code synchronized} block,
 * to avoid potential threading issues. When working with GUI applications, listeners might
 * consider implementing callbacks wrapped in {@link javax.swing.SwingUtilities#invokeLater(Runnable)},
 * or the GUI library's equivalent.
 * </p>
 * 
 * <h1>Java 8 Lambdas and Method References</h1>
 * <p>
 * Using this class in conjunction with Java 8 method references requires one to be aware
 * that each method reference ({@code this::example}) creates a new closure. Despite referencing the same
 * method, these closures are not only different, but also impossible to compare. Both {@code ==} and
 * {@code equals()} will return false.
 * As such, it's not possible to do something like this:
 * </p>
 * <p>
 * <code>
 * event.removeListener( this::onEvent )
 * </code>
 * </p>
 * <p>
 * ...as much as I'd want to. The listener has to be saved to a variable when registered, and then
 * unregistered by passing that variable into {@link #removeListener(Consumer)}.
 * </p>
 * <p>
 * Technically, it is possible to serialize lambda expressions and then compare their byte arrays
 * for equality, however this solution only works for static method references. Instance method
 * references involve some random additional bytes, which make direct comparison impossible.
 * One could implement a byte array comparer which ignores those seemingly random bytes and works out
 * whether the two lambda expressions are in fact equal, but I figured that it's not worth the tradeoff.
 * </p>
 *
 * @param <L>
 *            the type of listener that can be registered to this event
 */
public interface Event<L>
{
	public L addListener( L listener );

	public void removeListener( L listener ) throws ListenerException;


	public static interface Callback extends Event<Runnable>
	{
	}

	public static interface Single<T> extends Event<Consumer<T>>
	{
	}

	public static interface Double<T, U> extends Event<BiConsumer<T, U>>
	{
	}

	@SuppressWarnings("serial")
	public static class ListenerException extends RuntimeException
	{
		public ListenerException()
		{
			super( "Attempted to remove a listener that was not registered." );
		}
	}
}
