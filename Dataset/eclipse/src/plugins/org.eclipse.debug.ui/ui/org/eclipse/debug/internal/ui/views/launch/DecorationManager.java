/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.ui.views.launch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IThread;

/**
 * Manages source selections and decorated editors for launch views.
 */
public class DecorationManager {
	
	// map of targets to lists of active decorations
	private static Map fDecorations = new HashMap(10);
		
	/**
	 * Adds the given decoration for the given stack frame.
	 * 
	 * @param decoration
	 * @param frame
	 */
	public static void addDecoration(Decoration decoration) {
		IDebugTarget target= decoration.getThread().getDebugTarget();
		List list = (List)fDecorations.get(target);
		if (list == null) {
			list = new ArrayList();
			fDecorations.put(target, list);
		}
		list.add(decoration);
	}
	
	/**
	 * Removes any decorations for the given debug target.
	 *  
	 * @param target to remove editor decorations for
	 */
	public static void removeDecorations(IDebugTarget target) {
		List list = (List)fDecorations.get(target);
		if (list != null) {
			ListIterator iterator = list.listIterator();
			while (iterator.hasNext()) {
				Decoration decoration = (Decoration)iterator.next();
				decoration.remove();
				iterator.remove();
			}
		}
	}
	
	/**
	 * Removes any decorations for the given thread
	 * 
	 * @param thread thread to remove decorations for
	 */
	public static void removeDecorations(IThread thread) {
		List list = (List)fDecorations.get(thread.getDebugTarget());
		if (list != null) {
			ListIterator iterator = list.listIterator();
			while (iterator.hasNext()) {
				Decoration decoration = (Decoration)iterator.next();
				if (thread.equals(decoration.getThread())) {
					decoration.remove();
					iterator.remove();
				}
			}
		}
	}

}
