/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Jesper Kamstrup Linnet (eclipse@kamstrup-linnet.dk) - initial API and implementation 
 * 			(report 36180: Callers/Callees view)
 *******************************************************************************/
package org.eclipse.jdt.internal.ui.callhierarchy;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionProvider;

import org.eclipse.ui.PlatformUI;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.util.SelectionUtil;

import org.eclipse.jdt.internal.corext.callhierarchy.MethodWrapper;
import org.eclipse.jdt.internal.corext.util.Messages;

class FocusOnSelectionAction extends Action {
    private CallHierarchyViewPart fPart;

    public FocusOnSelectionAction(CallHierarchyViewPart part) {
        super(CallHierarchyMessages.FocusOnSelectionAction_focusOnSelection_text); 
        fPart= part;
        setDescription(CallHierarchyMessages.FocusOnSelectionAction_focusOnSelection_description); 
        setToolTipText(CallHierarchyMessages.FocusOnSelectionAction_focusOnSelection_tooltip); 
        PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.CALL_HIERARCHY_FOCUS_ON_SELECTION_ACTION);
    }

    public boolean canActionBeAdded() {
        Object element = SelectionUtil.getSingleElement(getSelection());

        IMethod method = getSelectedMethod(element);
        
        if (method != null) {
            setText(Messages.format(CallHierarchyMessages.FocusOnSelectionAction_focusOn_text, method.getElementName())); 

            return true;
        }

        return false;
    }

    private IMethod getSelectedMethod(Object element) {
		IMethod method = null;
        
        if (element instanceof IMethod) {
            method= (IMethod) element;
        } else if (element instanceof MethodWrapper) {
            IMember member= ((MethodWrapper) element).getMember();
            if (member.getElementType() == IJavaElement.METHOD) {
                method= (IMethod) member;
            }
        }
		return method;
	}

	/*
     * @see Action#run
     */
    public void run() {
        Object element = SelectionUtil.getSingleElement(getSelection());

        IMethod method= getSelectedMethod(element);
        if (method != null) {
                fPart.setMethod(method);
        }
    }

    private ISelection getSelection() {
        ISelectionProvider provider = fPart.getSite().getSelectionProvider();

        if (provider != null) {
            return provider.getSelection();
        }

        return null;
    }
}
