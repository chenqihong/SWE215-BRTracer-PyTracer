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
package org.eclipse.debug.internal.ui.actions;

import org.eclipse.debug.internal.ui.DebugPluginImages;
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.viewers.AbstractTreeViewer;

/**
 * CollapseAllAction
 */
public class CollapseAllAction extends Action {
	
	private AbstractTreeViewer fViewer;
	
	public CollapseAllAction(AbstractTreeViewer viewer) {
		super(ActionMessages.CollapseAllAction_0, DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_ELCL_COLLAPSE_ALL)); //$NON-NLS-1$
		setToolTipText(ActionMessages.CollapseAllAction_0); //$NON-NLS-1$
		setDisabledImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_DLCL_COLLAPSE_ALL));
		setHoverImageDescriptor(DebugPluginImages.getImageDescriptor(IInternalDebugUIConstants.IMG_LCL_COLLAPSE_ALL));
		fViewer = viewer;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.action.IAction#run()
	 */
	public void run() {
		fViewer.collapseAll();
	}

}
