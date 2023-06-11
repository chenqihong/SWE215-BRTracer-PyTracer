/**********************************************************************
 * Copyright (c) 2004, 2005 QNX Software Systems and others.
 * All rights reserved.   This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 * QNX Software Systems - Initial API and implementation
***********************************************************************/
package org.eclipse.debug.internal.ui.views;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.core.runtime.IPath;
import org.eclipse.debug.core.DebugException;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.TreeItem;

/**
 * The abstract superclass for mementos of the expanded and 
 * selected items in a tree viewer.
 */
public abstract class AbstractViewerState {

	// paths to expanded elements
	private List fSavedExpansion = null;
	private IPath[] fSelection;
	
	/**
	 * Constructs a memento for the given viewer.
	 */
	public AbstractViewerState(TreeViewer viewer) {
		saveState(viewer);
	}

	/**
	 * Saves the current state of the given viewer into
	 * this memento.
	 * 
	 * @param viewer viewer of which to save the state
	 */
	public void saveState(TreeViewer viewer) {
		List expanded = new ArrayList();
		fSavedExpansion = null;
		TreeItem[] items = viewer.getTree().getItems();
		try {
			for (int i = 0; i < items.length; i++) {
				collectExpandedItems(items[i], expanded);
			}
			if (expanded.size() > 0) {
				fSavedExpansion = expanded;
			}
		} catch (DebugException e) {
			fSavedExpansion = null;
		}
		TreeItem[] selection = viewer.getTree().getSelection();
		fSelection = new IPath[selection.length];
		try {
		    for (int i = 0; i < selection.length; i++) {
		        fSelection[i] = encodeElement(selection[i]);
		        if (fSelection[i] == null) {
		            fSelection = null;
		            return;
		        }
		    }
		} catch (DebugException e) {
		    fSelection = null;
		}
	}

	protected void collectExpandedItems(TreeItem item, List expanded) throws DebugException {
        if (item.getExpanded()) {
            IPath path = encodeElement(item);
            if (path != null) {
                expanded.add(path);
                TreeItem[] items = item.getItems();
                for (int i = 0; i < items.length; i++) {
                    collectExpandedItems(items[i], expanded);
                }
            }
        }
    }

	/**
	 * Constructs a path representing the given tree item. The segments in the
	 * path denote parent items, and the last segment is the name of
	 * the given item.
	 *   
	 * @param item tree item to encode
	 * @return path encoding the given item
	 * @throws DebugException if unable to generate a path
	 */
	protected abstract IPath encodeElement(TreeItem item) throws DebugException;

	/**
	 * Restores the state of the given viewer to this memento's
	 * saved state.
	 * 
	 * @param viewer viewer to which state is restored
	 */
	public void restoreState(TreeViewer viewer) {
	    boolean expansionComplete = true;
	    if (fSavedExpansion != null && fSavedExpansion.size() > 0) {		
	        for (int i = 0; i < fSavedExpansion.size(); i++) {
	            IPath path = (IPath) fSavedExpansion.get(i);
	            if (path != null) {
	                Object obj;
	                try {
	                    obj = decodePath(path, viewer);
	                    if (obj != null) {
	                        viewer.expandToLevel(obj, 1);
	                    } else {
	                        expansionComplete = false;                  
	                    }
	                } catch (DebugException e) {
	                }
	            }
	        }
	        if (expansionComplete) {
	            fSavedExpansion = null;
	        }
	    }
	    
	    boolean selectionComplete = true;
	    if (fSelection != null && fSelection.length > 0) {
	        List selection = new ArrayList(fSelection.length);
	        for (int i = 0; i < fSelection.length; i++) {
	            IPath path = fSelection[i];
	            Object obj;
	            try {
	                obj = decodePath(path, viewer);
	                if (obj != null) {
	                    selection.add(obj);
	                } else {
	                    selectionComplete = false;               
	                }
	            } catch (DebugException e) {
	            }
	        }
            if (selection.size() > 0) {
                viewer.setSelection(new StructuredSelection(selection));
            }
	        if (selectionComplete) {
	            fSelection = null;
	        }
	    }
	}
	
	/**
	 * Returns an element in the given viewer that corresponds to the given
	 * path, or <code>null</code> if none.
	 * 
	 * @param path encoded element path
	 * @param viewer viewer to search for the element in
	 * @return element represented by the path, or <code>null</code> if none
	 * @throws DebugException if unable to locate a variable
	 */
	protected abstract Object decodePath(IPath path, TreeViewer viewer) throws DebugException;

}