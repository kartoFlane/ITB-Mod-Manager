package com.kartoflane.itb.modmanager.ui;

import java.util.function.Function;

import javafx.beans.property.DoublePropertyBase;


public class MathProperty extends DoublePropertyBase
{
	private final Function<Double, Double> func;


	public MathProperty( Function<Double, Double> func )
	{
		this.func = func;
	}

	@Override
	public double get()
	{
		return func.apply( super.get() );
	}

	@Override
	public Object getBean()
	{
		return null;
	}

	@Override
	public String getName()
	{
		return null;
	}

}
