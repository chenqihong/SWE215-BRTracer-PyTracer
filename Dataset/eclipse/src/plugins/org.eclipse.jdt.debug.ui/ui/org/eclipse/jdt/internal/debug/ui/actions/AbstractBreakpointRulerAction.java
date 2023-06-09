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
package org.eclipse.jdt.internal.debug.ui.actions;


import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.jdt.debug.core.IJavaLineBreakpoint;
import org.eclipse.jdt.internal.debug.core.JDIDebugPlugin;
import org.eclipse.jdt.internal.debug.ui.JDIDebugUIPlugin;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.source.IAnnotationModel;
import org.eclipse.jface.text.source.IVerticalRulerInfo;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.texteditor.AbstractMarkerAnnotationModel;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.IUpdate;

public abstract class AbstractBreakpointRulerAction extends Action implements IUpdate {

	private IVerticalRulerInfo fInfo;

	private ITextEditor fTextEditor;

	private IBreakpoint fBreakpoint;

	protected IBreakpoint determineBreakpoint() {
		IBreakpoint[] breakpoints= DebugPlugin.getDefault().getBreakpointManager().getBreakpoints(JDIDebugPlugin.getUniqueIdentifier());
		for (int i= 0; i < breakpoints.length; i++) {
			IBreakpoint breakpoint= breakpoints[i];
			if (breakpoint instanceof IJavaLineBreakpoint) {
				IJavaLineBreakpoint jBreakpoint= (IJavaLineBreakpoint)breakpoint;
				try {
					if (breakpointAtRulerLine(jBreakpoint)) {
						return jBreakpoint;
					}
				} catch (CoreException ce) {
					JDIDebugUIPlugin.log(ce);
					continue;
				}
			}
		}
		return null;
	}

	protected IVerticalRulerInfo getInfo() {
		return fInfo;
	}

	protected void setInfo(IVerticalRulerInfo info) {
		fInfo = info;
	}

	protected ITextEditor getTextEditor() {
		return fTextEditor;
	}

	protected void setTextEditor(ITextEditor textEditor) {
		fTextEditor = textEditor;
	}

	/** 
	 * Returns the resource for which to create the marker, 
	 * or <code>null</code> if there is no applicable resource.
	 *
	 * @return the resource for which to create the marker or <code>null</code>
	 */
	protected IResource getResource() {
		IEditorInput input= fTextEditor.getEditorInput();
		IResource resource= (IResource) input.getAdapter(IFile.class);
		if (resource == null) {
			resource= (IResource) input.getAdapter(IResource.class);
		}	
		return resource;
	}

	protected boolean breakpointAtRulerLine(IJavaLineBreakpoint jBreakpoint) throws CoreException {
		AbstractMarkerAnnotationModel model = getAnnotationModel();
		if (model != null) {
			Position position= model.getMarkerPosition(jBreakpoint.getMarker());
			if (position != null) {
				IDocumentProvider provider= getTextEditor().getDocumentProvider();
				IDocument doc=  provider.getDocument(getTextEditor().getEditorInput());
				try {
					int markerLineNumber= doc.getLineOfOffset(position.getOffset());
					int rulerLine= getInfo().getLineOfLastMouseButtonActivity();
					if (rulerLine == markerLineNumber) {
						if (getTextEditor().isDirty()) {
							return jBreakpoint.getLineNumber() == markerLineNumber + 1;
						}
						return true;
					}
				} catch (BadLocationException x) {
				}
			}
		}
		
		return false;
	}
		
	protected IBreakpoint getBreakpoint() {
		return fBreakpoint;
	}

	protected void setBreakpoint(IBreakpoint breakpoint) {
		fBreakpoint = breakpoint;
	}
	
	/**
	 * Returns the <code>AbstractMarkerAnnotationModel</code> of the editor's input.
	 *
	 * @return the marker annotation model
	 */
	protected AbstractMarkerAnnotationModel getAnnotationModel() {
		IDocumentProvider provider= fTextEditor.getDocumentProvider();
		IAnnotationModel model= provider.getAnnotationModel(getTextEditor().getEditorInput());
		if (model instanceof AbstractMarkerAnnotationModel) {
			return (AbstractMarkerAnnotationModel) model;
		}
		return null;
	}
}
