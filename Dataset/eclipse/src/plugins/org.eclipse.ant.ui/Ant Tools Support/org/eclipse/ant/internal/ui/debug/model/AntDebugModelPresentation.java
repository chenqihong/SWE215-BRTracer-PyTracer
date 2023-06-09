/*******************************************************************************
 * Copyright (c) 2004, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.ant.internal.ui.debug.model;

import java.text.MessageFormat;

import org.eclipse.ant.internal.ui.preferences.AntObjectLabelProvider;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.debug.core.DebugException;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.ILineBreakpoint;
import org.eclipse.debug.core.model.IValue;
import org.eclipse.debug.core.sourcelookup.containers.LocalFileStorage;
import org.eclipse.debug.ui.IDebugModelPresentation;
import org.eclipse.debug.ui.IValueDetailListener;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.internal.editors.text.JavaFileEditorInput;
import org.eclipse.ui.part.FileEditorInput;

/**
 * Renders Ant debug elements
 */
public class AntDebugModelPresentation extends LabelProvider implements IDebugModelPresentation {
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.IDebugModelPresentation#setAttribute(java.lang.String, java.lang.Object)
	 */
	public void setAttribute(String attribute, Object value) {
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ILabelProvider#getImage(java.lang.Object)
	 */
	public Image getImage(Object element) {
		if (element instanceof AntProperty) {
			return AntObjectLabelProvider.getPropertyImage();
		} else if (element instanceof AntProperties) {
			return AntObjectLabelProvider.getPropertyImage();
        }
		return null;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.ILabelProvider#getText(java.lang.Object)
	 */
	public String getText(Object element) {
		if (element instanceof AntStackFrame) {
			AntStackFrame frame= (AntStackFrame) element;
			return getStackFrameText(frame);
		} else if (element instanceof AntThread) {
			AntThread thread= (AntThread) element;
			return getThreadText(thread);
		} else if (element instanceof AntProperty) {
		    AntProperty property= (AntProperty) element;
		    return property.getText();  
        } else if (element instanceof AntProperties) {
		   return ((AntProperties)element).getName();
        }
		
		return null;
	}

    private String getThreadText(AntThread thread) {
        String name= thread.getName();
        if (name != null) {
            StringBuffer text= new StringBuffer(name);
            if (thread.isSuspended()) {
                IBreakpoint[] breakpoints= thread.getBreakpoints();
                if (breakpoints.length > 0) {
                    AntLineBreakpoint breakpoint= (AntLineBreakpoint) breakpoints[0];
                    IMarker marker= breakpoint.getMarker();
                    String fileName= marker.getResource().getFullPath().lastSegment();
                    String lineNumber= Integer.toString(marker.getAttribute(IMarker.LINE_NUMBER, -1));
                    String breakpointString= null;
                    if (breakpoint.isRunToLine()) {
                        breakpointString= MessageFormat.format(DebugModelMessages.AntDebugModelPresentation_5, new String[] {lineNumber, fileName}); //$NON-NLS-1$
                    } else {
                        breakpointString= MessageFormat.format(DebugModelMessages.AntDebugModelPresentation_2, new String[]{lineNumber, fileName}); //$NON-NLS-1$                            
                    }
                    text.append(MessageFormat.format(DebugModelMessages.AntDebugModelPresentation_3, new String[]{breakpointString})); //$NON-NLS-1$
                } else {
                    text.append(DebugModelMessages.AntDebugModelPresentation_4); //$NON-NLS-1$
                }
            }
            
            return text.toString();
        }
        return null;
    }

    private String getStackFrameText(AntStackFrame frame) {
        String name= frame.getName();
        if (name != null) {
            StringBuffer text= new StringBuffer(name);
            int lineNumber= frame.getLineNumber();
            String lineNumberString= null;
            if (lineNumber == 0) {
                lineNumberString= DebugModelMessages.AntDebugModelPresentation_0; //$NON-NLS-1$
            } else {
                lineNumberString= Integer.toString(lineNumber);
            }
            text.append(MessageFormat.format(DebugModelMessages.AntDebugModelPresentation_1, new String[]{lineNumberString})); //$NON-NLS-1$
            return text.toString();
        }
        return null;
    }

    /* (non-Javadoc)
	 * @see org.eclipse.debug.ui.IDebugModelPresentation#computeDetail(org.eclipse.debug.core.model.IValue, org.eclipse.debug.ui.IValueDetailListener)
	 */
	public void computeDetail(IValue value, IValueDetailListener listener) {
		String detail = ""; //$NON-NLS-1$
		try {
			detail = value.getValueString();
		} catch (DebugException e) {
		}
		listener.detailComputed(value, detail);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ISourcePresentation#getEditorInput(java.lang.Object)
	 */
	public IEditorInput getEditorInput(Object element) {
		if (element instanceof IFile) {
			return new FileEditorInput((IFile)element);
		}
		if (element instanceof ILineBreakpoint) {
			return new FileEditorInput((IFile)((ILineBreakpoint)element).getMarker().getResource());
		}
        if (element instanceof LocalFileStorage) {
            return new JavaFileEditorInput(((LocalFileStorage)element).getFile());
        }
		return null;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ISourcePresentation#getEditorId(org.eclipse.ui.IEditorInput, java.lang.Object)
	 */
	public String getEditorId(IEditorInput input, Object element) {
		return "org.eclipse.ant.ui.internal.editor.AntEditor"; //$NON-NLS-1$
	}
}
