package com.kartoflane.itb.modmanager.lua;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;


/**
 * A simple container holding both the LuaValue returned by a lua script as well
 * as the environment in which it was executed.
 * 
 * returnValue is the object returned by the script.
 * environment contains all global variables defined in the script.
 */
public class LuaResult
{
	public Globals environment;
	public LuaValue returnValue;


	public LuaResult( Globals env, LuaValue retVal )
	{
		environment = env;
		returnValue = retVal.call();
	}
}
