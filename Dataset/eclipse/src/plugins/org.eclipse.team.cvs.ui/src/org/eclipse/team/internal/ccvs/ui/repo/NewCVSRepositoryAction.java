/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.repo;


import org.eclipse.jface.action.Action;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.wizards.NewLocationWizard;
import org.eclipse.ui.IWorkbenchWindow;

/**
 * Called from Welcome page only.
 */
public class NewCVSRepositoryAction extends Action {
	public void run() {
		Shell shell;
		IWorkbenchWindow window = CVSUIPlugin.getPlugin().getWorkbench().getActiveWorkbenchWindow();
		if (window != null) {
			shell = window.getShell();
		} else {
			Display display = Display.getCurrent();
			shell = new Shell(display);
		}
		NewLocationWizard wizard = new NewLocationWizard();
		WizardDialog dialog = new WizardDialog(shell, wizard);
		dialog.open();
	}
}
