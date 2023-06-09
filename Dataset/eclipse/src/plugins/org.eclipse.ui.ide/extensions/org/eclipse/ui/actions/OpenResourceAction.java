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
package org.eclipse.ui.actions;

import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceRuleFactory;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.MultiRule;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.ide.IDEWorkbenchMessages;
import org.eclipse.ui.internal.ide.IIDEHelpContextIds;

/**
 * Standard action for opening the currently selected project(s).
 * <p>
 * Note that there is a different action for opening an editor on file resources:
 * <code>OpenFileAction</code>.
 * </p>
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 */
public class OpenResourceAction extends WorkspaceAction implements
        IResourceChangeListener {

    /**
     * The id of this action.
     */
    public static final String ID = PlatformUI.PLUGIN_ID
            + ".OpenResourceAction"; //$NON-NLS-1$

    /**
     * Creates a new action.
     *
     * @param shell the shell for any dialogs
     */
    public OpenResourceAction(Shell shell) {
        super(shell, IDEWorkbenchMessages.OpenResourceAction_text);
        PlatformUI.getWorkbench().getHelpSystem().setHelp(this,
				IIDEHelpContextIds.OPEN_RESOURCE_ACTION);
        setToolTipText(IDEWorkbenchMessages.OpenResourceAction_toolTip);
        setId(ID);
    }

    /* (non-Javadoc)
     * Method declared on WorkspaceAction.
     */
    protected String getOperationMessage() {
        return IDEWorkbenchMessages.OpenResourceAction_operationMessage;
    }

    /* (non-Javadoc)
     * Method declared on WorkspaceAction.
     */
    protected String getProblemsMessage() {
        return IDEWorkbenchMessages.OpenResourceAction_problemMessage;
    }

    /* (non-Javadoc)
     * Method declared on WorkspaceAction.
     */
    protected String getProblemsTitle() {
        return IDEWorkbenchMessages.OpenResourceAction_dialogTitle;
    }

    protected void invokeOperation(IResource resource, IProgressMonitor monitor)
	        throws CoreException {
	    ((IProject) resource).open(monitor);
	}

    /* (non-Javadoc)
     * Method declared on WorkspaceAction.
     */
    protected boolean shouldPerformResourcePruning() {
        return false;
    }

    /**
     * The <code>OpenResourceAction</code> implementation of this
     * <code>SelectionListenerAction</code> method ensures that this action is
     * enabled only if one of the selections is a closed project.
     */
    protected boolean updateSelection(IStructuredSelection s) {
        // don't call super since we want to enable if closed project is selected.

        if (!selectionIsOfType(IResource.PROJECT))
            return false;

        Iterator resources = getSelectedResources().iterator();
        while (resources.hasNext()) {
            IProject currentResource = (IProject) resources.next();
            if (!currentResource.isOpen()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handles a resource changed event by updating the enablement
     * if one of the selected projects is opened or closed.
     */
    public void resourceChanged(IResourceChangeEvent event) {
        // Warning: code duplicated in CloseResourceAction
        List sel = getSelectedResources();
        // don't bother looking at delta if selection not applicable
        if (selectionIsOfType(IResource.PROJECT)) {
            IResourceDelta delta = event.getDelta();
            if (delta != null) {
                IResourceDelta[] projDeltas = delta
                        .getAffectedChildren(IResourceDelta.CHANGED);
                for (int i = 0; i < projDeltas.length; ++i) {
                    IResourceDelta projDelta = projDeltas[i];
                    if ((projDelta.getFlags() & IResourceDelta.OPEN) != 0) {
                        if (sel.contains(projDelta.getResource())) {
                            selectionChanged(getStructuredSelection());
                            return;
                        }
                    }
                }
            }
        }
    }

    /* (non-Javadoc)
     * Method declared on IAction; overrides method on WorkspaceAction.
     */
    public void run() {
    	ISchedulingRule rule = null;
        //be conservative and include all projects in the selection - projects
        //can change state between now and when the job starts
    	IResourceRuleFactory factory = ResourcesPlugin.getWorkspace().getRuleFactory();
        Iterator resources = getSelectedResources().iterator();
        while (resources.hasNext()) {
            IProject project = (IProject) resources.next();
       		rule = MultiRule.combine(rule, factory.modifyRule(project));
        }
        runInBackground(rule);
    }
}
