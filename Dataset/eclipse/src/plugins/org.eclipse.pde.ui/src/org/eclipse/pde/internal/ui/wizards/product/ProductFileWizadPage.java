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
package org.eclipse.pde.internal.ui.wizards.product;

import java.util.*;

import org.eclipse.core.runtime.*;
import org.eclipse.debug.core.*;
import org.eclipse.debug.ui.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.*;
import org.eclipse.pde.internal.core.*;
import org.eclipse.pde.internal.ui.*;
import org.eclipse.swt.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.dialogs.*;

public class ProductFileWizadPage extends WizardNewFileCreationPage {
	
	public final static int USE_DEFAULT = 0;
	public final static int USE_PRODUCT = 1;
	public final static int USE_LAUNCH_CONFIG = 2;
	
	private Button fBasicButton;
	private Button fProductButton;
	private Combo fProductCombo;
	private Button fLaunchConfigButton;
	private Combo fLaunchConfigCombo;
	private Group fGroup;
	
	public ProductFileWizadPage(String pageName, IStructuredSelection selection) {
		super(pageName, selection);
		setDescription(PDEUIMessages.ProductFileWizadPage_title); //$NON-NLS-1$
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.dialogs.WizardNewFileCreationPage#createAdvancedControls(org.eclipse.swt.widgets.Composite)
	 */
	protected void createAdvancedControls(Composite parent) {
		fGroup = new Group(parent, SWT.NONE);
		fGroup.setText(PDEUIMessages.ProductFileWizadPage_groupTitle); //$NON-NLS-1$
		fGroup.setLayout(new GridLayout(2, false));
		fGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		fBasicButton = new Button(fGroup, SWT.RADIO);
		GridData gd = new GridData();
		gd.horizontalSpan = 2;
		fBasicButton.setLayoutData(gd);
		fBasicButton.setText(PDEUIMessages.ProductFileWizadPage_basic); //$NON-NLS-1$
		
		fProductButton = new Button(fGroup, SWT.RADIO);
		fProductButton.setText(PDEUIMessages.ProductFileWizadPage_existingProduct); //$NON-NLS-1$
		fProductButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				fProductCombo.setEnabled(fProductButton.getSelection());
			}
		});
		
		fProductCombo = new Combo(fGroup, SWT.SINGLE|SWT.READ_ONLY);
		fProductCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		fProductCombo.setItems(TargetPlatform.getProductNames());
		
		fLaunchConfigButton = new Button(fGroup, SWT.RADIO);
		fLaunchConfigButton.setText(PDEUIMessages.ProductFileWizadPage_existingLaunchConfig); //$NON-NLS-1$
		fLaunchConfigButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				fLaunchConfigCombo.setEnabled(fLaunchConfigButton.getSelection());
			}
		});
		
		fLaunchConfigCombo = new Combo(fGroup, SWT.SINGLE|SWT.READ_ONLY);
		fLaunchConfigCombo.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		fLaunchConfigCombo.setItems(getLaunchConfigurations());
		
		initializeState();
	}
	
	private void initializeState() {
		fBasicButton.setSelection(true);
		
		int count = fProductCombo.getItemCount();
		fProductButton.setEnabled(count > 0);
		fProductCombo.setEnabled(count > 0 && fProductButton.getSelection());
		if (count > 0)
			fProductCombo.setText(fProductCombo.getItem(0));
		
		count = fLaunchConfigCombo.getItemCount();
		fLaunchConfigButton.setEnabled(count > 0);
		fLaunchConfigCombo.setEnabled(count > 0 && fLaunchConfigButton.getSelection());
		if (count > 0)
			fLaunchConfigCombo.setText(fLaunchConfigCombo.getItem(0));
		
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.dialogs.WizardNewFileCreationPage#validatePage()
	 */
	protected boolean validatePage() {
		if (!getFileName().trim().endsWith(".product")) { //$NON-NLS-1$
			setErrorMessage(PDEUIMessages.ProductFileWizadPage_error); //$NON-NLS-1$
			return false;
		}
		if (getFileName().trim().length() <= 8) {
			return false;
		}
		return super.validatePage();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.dialogs.WizardNewFileCreationPage#validateLinkedResource()
	 */
	protected IStatus validateLinkedResource() {
		return new Status(IStatus.OK, PDEPlugin.getPluginId(), IStatus.OK, "", null); //$NON-NLS-1$
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.dialogs.WizardNewFileCreationPage#createLinkTarget()
	 */
	protected void createLinkTarget() {
	}
	
	private String[] getLaunchConfigurations() {
		ArrayList list = new ArrayList();
		try {
			ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
			ILaunchConfigurationType type = manager.getLaunchConfigurationType("org.eclipse.pde.ui.RuntimeWorkbench"); //$NON-NLS-1$
			ILaunchConfiguration[] configs = manager.getLaunchConfigurations(type);
			for (int i = 0; i < configs.length; i++) {
				if (!DebugUITools.isPrivate(configs[i]))
					list.add(configs[i].getName());
			}
		} catch (CoreException e) {
		}
		return (String[])list.toArray(new String[list.size()]);
	}
	
	public ILaunchConfiguration getSelectedLaunchConfiguration() {
		if (!fLaunchConfigButton.getSelection())
			return null;
		
		String configName = fLaunchConfigCombo.getText();
		try {
			ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();
			ILaunchConfigurationType type = manager.getLaunchConfigurationType("org.eclipse.pde.ui.RuntimeWorkbench"); //$NON-NLS-1$
			ILaunchConfiguration[] configs = manager.getLaunchConfigurations(type);
			for (int i = 0; i < configs.length; i++) {
				if (configs[i].getName().equals(configName) && !DebugUITools.isPrivate(configs[i]))
					return configs[i];
			}
		} catch (CoreException e) {
		}
		return null;
	}
	
	public String getSelectedProduct() {
		return fProductButton.getSelection() ? fProductCombo.getText() : null;
	}
	
	public int getInitializationOption() {
		if (fBasicButton.getSelection())
			return USE_DEFAULT;
		if (fProductButton.getSelection())
			return USE_PRODUCT;
		return USE_LAUNCH_CONFIG;
	}
	
	public void createControl(Composite parent) {
		super.createControl(parent);
		Dialog.applyDialogFont(fGroup);
		setFileName(".product"); //$NON-NLS-1$
	}
	
}
