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
package org.eclipse.debug.internal.ui.launchConfigurations;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.IStatusHandler;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.IInternalDebugUIConstants;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.Shell;


public class CompileErrorPromptStatusHandler implements IStatusHandler {

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.IStatusHandler#handleStatus(org.eclipse.core.runtime.IStatus, java.lang.Object)
	 */
	public Object handleStatus(IStatus status, Object source) throws CoreException {
		if (source instanceof ILaunchConfiguration) {
			ILaunchConfiguration config = (ILaunchConfiguration)source;
			if (DebugUITools.isPrivate(config)) {
				return new Boolean(true);
			}
		}
		
		Shell shell = DebugUIPlugin.getShell();
		String title = LaunchConfigurationsMessages.CompileErrorPromptStatusHandler_0; //$NON-NLS-1$
		String message = LaunchConfigurationsMessages.CompileErrorPromptStatusHandler_1; //$NON-NLS-1$
		IPreferenceStore store = DebugUIPlugin.getDefault().getPreferenceStore(); 
		
		String pref = store.getString(IInternalDebugUIConstants.PREF_CONTINUE_WITH_COMPILE_ERROR);
		if (pref != null) {
			if (pref.equals(MessageDialogWithToggle.ALWAYS)) {
				return new Boolean(true);
			}
		}

		MessageDialogWithToggle dialog = new MessageDialogWithToggle(shell, title, null, message, MessageDialog.WARNING,
				new String[] { IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL}, 1, null, false);
		dialog.setPrefKey(IInternalDebugUIConstants.PREF_CONTINUE_WITH_COMPILE_ERROR);
		dialog.setPrefStore(store);
		dialog.open();
		
		int returnValue = dialog.getReturnCode();
		if (returnValue == IDialogConstants.OK_ID) {
			return new Boolean(true);
		}
		return new Boolean(false);
	}
}