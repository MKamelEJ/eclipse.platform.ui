/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ltk.core.refactoring;


/**
 * Listener to monitor state changes of an {@link IUndoManager}.
 * <p>
 * Clients may implement this interface to listen to undo manger changes.
 * </p>
 * @since 3.0
 */
public interface IUndoManagerListener {
	
	/**
	 * This method is called by the undo manager if the undo stack has
	 * changed (for example a undo object got added or the undo stack
	 * got flushed).
	 * 
	 * @param manager the manager this listener is registered to
	 */
	public void undoStackChanged(IUndoManager manager);
	
	/**
	 * This method is called by the undo manager if the redo stack has
	 * changed (for example a redo object got added or the redo stack 
	 * got flushed).
	 * 
	 * @param manager the manager this listener is registered to
	 */
	public void redoStackChanged(IUndoManager manager);
	
	/**
	 * This method gets called by the undo manager if a change gets
	 * executed in the context of the undo manager.
	 * 
	 * @param manager the manager this listener is registered to
	 * @param change the change to be executed
	 */
	public void aboutToPerformChange(IUndoManager manager, Change change);
	
	/**
	 * This method gets called by the undo manager when a change has 
	 * been executed in the context of the undo manager.
	 * 
	 * @param manager the manager this listener is registered to
	 * @param change the change that has been executed
	 */
	public void changePerformed(IUndoManager manager, Change change);	
}
