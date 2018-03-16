package com.kartoflane.itb.modmanager.event;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;


public abstract class EventBase<L> implements Event<L>
{
	protected Set<L> listeners = null;


	public L addListener( L listener )
	{
		Objects.requireNonNull( listener );
		if ( listeners == null )
			listeners = new CopyOnWriteArraySet<>();
		listeners.add( listener );
		return listener;
	}

	public void removeListener( L listener ) throws ListenerException
	{
		Objects.requireNonNull( listener );
		if ( listeners == null || listeners.size() == 0 )
			return;
		if ( !listeners.remove( listener ) ) {
			throw new ListenerException();
		}
	}
}
