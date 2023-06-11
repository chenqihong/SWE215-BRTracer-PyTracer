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
package org.eclipse.jdt.internal.ui.javaeditor;


import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.ui.IElementFactory;
import org.eclipse.ui.IMemento;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

/**
 * The factory which is capable of recreating class file editor
 * inputs stored in a memento.
 */
public class ClassFileEditorInputFactory implements IElementFactory {

	public final static String ID=  "org.eclipse.jdt.ui.ClassFileEditorInputFactory"; //$NON-NLS-1$
	public final static String KEY= "org.eclipse.jdt.ui.ClassFileIdentifier"; //$NON-NLS-1$

	public ClassFileEditorInputFactory() {
	}

	/**
	 * @see IElementFactory#createElement
	 */
	public IAdaptable createElement(IMemento memento) {
		String identifier= memento.getString(KEY);
		if (identifier != null) {
			IJavaElement element= JavaCore.create(identifier);
			try {
				return EditorUtility.getEditorInput(element);
			} catch (JavaModelException x) {
			}
		}
		return null;
	}

	public static void saveState(IMemento memento, InternalClassFileEditorInput input) {
		IClassFile c= input.getClassFile();
		memento.putString(KEY, c.getHandleIdentifier());
	}
}