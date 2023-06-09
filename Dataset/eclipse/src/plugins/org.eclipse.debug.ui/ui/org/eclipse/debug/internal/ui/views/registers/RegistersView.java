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
package org.eclipse.debug.internal.ui.views.registers;

import org.eclipse.debug.internal.ui.IDebugHelpContextIds;
import org.eclipse.debug.internal.ui.preferences.IDebugPreferenceConstants;
import org.eclipse.debug.internal.ui.views.AbstractViewerState;
import org.eclipse.debug.internal.ui.views.RemoteTreeViewer;
import org.eclipse.debug.internal.ui.views.variables.RemoteVariablesContentProvider;
import org.eclipse.debug.internal.ui.views.variables.VariablesView;
import org.eclipse.debug.internal.ui.views.variables.VariablesViewEventHandler;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.Viewer;

/**
 * Displays registers and their values with a detail area.
 */
public class RegistersView extends VariablesView {
	
	/**
	 * @see org.eclipse.debug.internal.ui.views.variables.VariablesView#createContentProvider()
	 */
	protected RemoteVariablesContentProvider createContentProvider(Viewer viewer) {
		RemoteRegistersViewContentProvider cp = new RemoteRegistersViewContentProvider((RemoteTreeViewer) viewer, getSite(), this);
//		TODO
//		cp.setExceptionHandler(this);
		return cp;
	}

	/**
	 * @see org.eclipse.debug.ui.AbstractDebugView#getHelpContextId()
	 */
	protected String getHelpContextId() {
		return IDebugHelpContextIds.REGISTERS_VIEW;
	}

	/**
	 * @see org.eclipse.debug.ui.AbstractDebugView#configureToolBar(org.eclipse.jface.action.IToolBarManager)
	 */
	protected void configureToolBar(IToolBarManager tbm) {
		super.configureToolBar(tbm);
		tbm.add(new Separator(IDebugUIConstants.EMPTY_REGISTER_GROUP));		
		tbm.add(new Separator(IDebugUIConstants.REGISTER_GROUP));
	}

	/**
	 * @see org.eclipse.debug.internal.ui.views.variables.VariablesView#getDetailPanePreferenceKey()
	 */
	protected String getDetailPanePreferenceKey() {
		return IDebugPreferenceConstants.REGISTERS_DETAIL_PANE_ORIENTATION;
	}

	/**
	 * @see org.eclipse.debug.internal.ui.views.variables.VariablesView#getToggleActionLabel()
	 */
	protected String getToggleActionLabel() {
		return RegistersViewMessages.RegistersView_0; //$NON-NLS-1$
	}

	/**
	 * @see org.eclipse.debug.internal.ui.views.variables.VariablesView#getViewerState()
	 */
	protected AbstractViewerState getViewerState() {
		return new RegistersViewerState(getVariablesViewer());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.internal.ui.views.variables.VariablesView#createEventHandler()
	 */
	protected VariablesViewEventHandler createEventHandler() {
		return new RegistersViewEventHandler(this);
	}
}
