package com.kartoflane.itb.modmanager.core;

import java.util.List;

import net.vhati.modmanager.core.ModFileInfo;
import net.vhati.modmanager.ui.table.ListState;


/**
 * An interface for a UI element which displays a list of
 * selectable items, each representing a mod file.
 * 
 * This UI element allows the user to select which items
 * they want installed.
 */
public interface ModFileInfoView
{
	/**
	 * Returns a ListState describing content in the mods table.
	 */
	public ListState<ModFileInfo> getCurrentModsTableState();

	/**
	 * Updates this view's model to display the specified table state.
	 * 
	 * @param tableState
	 *            an existing state that this view should display
	 */
	public void updateModel( ListState<ModFileInfo> tableState );

	/**
	 * Returns a list of selected mods.
	 */
	public List<ModFileInfo> getSelectedMods();
}
