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


import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.ui.IDebugUIConstants;

/**
 * Relaunches the last profile-mode launch
 */
public class ProfileLastAction extends RelaunchLastAction {

	/**
	 * @see RelaunchLastAction#getMode()
	 */
	public String getMode() {
		return ILaunchManager.PROFILE_MODE;
	}	
	
	/**
	 * @see org.eclipse.debug.internal.ui.actions.LaunchDropDownAction#getLaunchGroupId()
	 */
	public String getLaunchGroupId() {
		return IDebugUIConstants.ID_PROFILE_LAUNCH_GROUP;
	}	
}
