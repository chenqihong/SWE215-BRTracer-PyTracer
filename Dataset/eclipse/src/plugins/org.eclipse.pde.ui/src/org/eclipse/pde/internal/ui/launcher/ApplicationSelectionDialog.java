/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.launcher;

import org.eclipse.debug.core.*;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.pde.internal.ui.*;
import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;


public class ApplicationSelectionDialog extends Dialog {
	
	private String fMode;
	private Combo applicationCombo;
	private String[] fApplicationNames;
	private String fSelectedApplication;

	public ApplicationSelectionDialog(Shell parentShell, String[] applicationNames,  String mode) {
		super(parentShell);
		fMode = mode;
		fApplicationNames = applicationNames;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.Dialog#createButtonsForButtonBar(org.eclipse.swt.widgets.Composite)
	 */
	protected void createButtonsForButtonBar(Composite parent) {
		createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, true);
	}
	
	protected Control createDialogArea(Composite parent) {
		Composite container = (Composite) super.createDialogArea(parent);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginWidth = layout.marginHeight = 9;
		container.setLayout(layout);
		GridData gd = new GridData(GridData.FILL_BOTH);
		gd.heightHint = 100;
		container.setLayoutData(gd);

		Label label = new Label(container, SWT.NONE);
		if (fMode.equals(ILaunchManager.DEBUG_MODE))
			label.setText(PDEUIMessages.ApplicationSelectionDialog_debug); //$NON-NLS-1$
		else 
			label.setText(PDEUIMessages.ApplicationSelectionDialog_run); //$NON-NLS-1$
				
		applicationCombo = new Combo(container, SWT.READ_ONLY|SWT.DROP_DOWN);
		applicationCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		applicationCombo.setItems(fApplicationNames);
		
		String defaultApp = LauncherUtils.getDefaultApplicationName();
		if (applicationCombo.indexOf(defaultApp) == -1)
			applicationCombo.add(defaultApp);
		
		applicationCombo.setText(applicationCombo.getItem(0));
		
		getShell().setText(fMode.equals(ILaunchManager.DEBUG_MODE) ? PDEUIMessages.ApplicationSelectionDialog_dtitle : PDEUIMessages.ApplicationSelectionDialog_rtitle); //$NON-NLS-1$ //$NON-NLS-2$
		Dialog.applyDialogFont(container);
		return container;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.Dialog#okPressed()
	 */
	protected void okPressed() {
		fSelectedApplication = applicationCombo.getText();
		super.okPressed();
	}
	
	public String getSelectedApplication() {
		if (fSelectedApplication.equals(LauncherUtils.getDefaultApplicationName()))
			return null;
		return fSelectedApplication;
	}
	
}
