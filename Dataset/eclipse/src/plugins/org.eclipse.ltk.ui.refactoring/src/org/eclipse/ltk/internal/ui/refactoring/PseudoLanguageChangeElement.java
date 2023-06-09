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
 package org.eclipse.ltk.internal.ui.refactoring;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.TextChange;
import org.eclipse.ltk.core.refactoring.TextEditChangeGroup;
import org.eclipse.ltk.ui.refactoring.IChangePreviewViewer;

import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.util.Assert;

public abstract class PseudoLanguageChangeElement extends ChangeElement {

	private List fChildren;
	
	public PseudoLanguageChangeElement(ChangeElement parent) {
		super(parent);
	}

	public Change getChange() {
		return null;
	}
	
	public ChangePreviewViewerDescriptor getChangePreviewViewerDescriptor() throws CoreException {
		DefaultChangeElement element= getDefaultChangeElement();
		if (element == null)
			return null;
		return element.getChangePreviewViewerDescriptor();
	}
	
	public void feedInput(IChangePreviewViewer viewer) throws CoreException {
		DefaultChangeElement element= getDefaultChangeElement();
		if (element != null) {
			Change change= element.getChange();
			if (change instanceof TextChange) {
				List edits= collectTextEditChanges();
				viewer.setInput(TextChangePreviewViewer.createInput(change,
					(TextEditChangeGroup[])edits.toArray(new TextEditChangeGroup[edits.size()]),
					getTextRange()));
			}
		} else {
			viewer.setInput(null);
		}
	}
	
	public void setEnabled(boolean enabled) {
		for (Iterator iter= fChildren.iterator(); iter.hasNext();) {
			ChangeElement element= (ChangeElement)iter.next();
			element.setEnabled(enabled);
		}
	}
	
	public void setEnabledShallow(boolean enabled) {
		// do nothing. We don't manage a own enablement state.
	}
	
	public int getActive() {
		Assert.isTrue(fChildren.size() > 0);
		int result= ((ChangeElement)fChildren.get(0)).getActive();
		for (int i= 1; i < fChildren.size(); i++) {
			ChangeElement element= (ChangeElement)fChildren.get(i);
			result= ACTIVATION_TABLE[element.getActive()][result];
			if (result == PARTLY_ACTIVE)
				break;
		}
		return result;
	}
	
	/* non Java-doc
	 * @see ChangeElement.getChildren
	 */
	public ChangeElement[] getChildren() {
		if (fChildren == null)
			return EMPTY_CHILDREN;
		return (ChangeElement[]) fChildren.toArray(new ChangeElement[fChildren.size()]);
	}
	
	/**
	 * Adds the given <code>TextEditChangeElement<code> as a child to this 
	 * <code>PseudoJavaChangeElement</code>
	 * 
	 * @param child the child to be added
	 */
	public void addChild(TextEditChangeElement child) {
		doAddChild(child);
	}
	
	/**
	 * Adds the given <code>PseudoJavaChangeElement<code> as a child to this 
	 * <code>PseudoJavaChangeElement</code>
	 * 
	 * @param child the child to be added
	 */
	public void addChild(PseudoLanguageChangeElement child) {
		doAddChild(child);
	}
	
	private void doAddChild(ChangeElement child) {
		if (fChildren == null)
			fChildren= new ArrayList(2);
		fChildren.add(child);
	}
	
	private DefaultChangeElement getDefaultChangeElement() {
		ChangeElement element= getParent();
		while(!(element instanceof DefaultChangeElement) && element != null) {
			element= element.getParent();
		}
		return (DefaultChangeElement)element;
	}
	
	private List collectTextEditChanges() {
		List result= new ArrayList(10);
		ChangeElement[] children= getChildren();
		for (int i= 0; i < children.length; i++) {
			ChangeElement child= children[i];
			if (child instanceof TextEditChangeElement) {
				result.add(((TextEditChangeElement)child).getTextEditChange());
			} else if (child instanceof PseudoLanguageChangeElement) {
				result.addAll(((PseudoLanguageChangeElement)child).collectTextEditChanges());
			}
		}
		return result;
	}
	
	/**
	 * Returns the source region the lanaguage element.
	 * 
	 * @return the source region of the language element.
	 * @throws CoreException if the source region can't be optained
	 */
	public abstract IRegion getTextRange() throws CoreException;
}
