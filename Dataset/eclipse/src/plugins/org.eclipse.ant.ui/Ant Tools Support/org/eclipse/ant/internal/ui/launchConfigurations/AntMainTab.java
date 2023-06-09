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
package org.eclipse.ant.internal.ui.launchConfigurations;

import org.eclipse.ant.internal.ui.AntUIPlugin;
import org.eclipse.ant.internal.ui.IAntUIConstants;
import org.eclipse.ant.internal.ui.IAntUIHelpContextIds;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.ui.ILaunchConfigurationTab;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.externaltools.internal.launchConfigurations.ExternalToolsMainTab;
import org.eclipse.ui.externaltools.internal.model.IExternalToolConstants;
import org.eclipse.ui.externaltools.internal.ui.FileSelectionDialog;

public class AntMainTab extends ExternalToolsMainTab {

	private String fCurrentLocation= null;
	private Button fSetInputHandlerButton;

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#initializeFrom(org.eclipse.debug.core.ILaunchConfiguration)
	 */
	public void initializeFrom(ILaunchConfiguration configuration) {
		super.initializeFrom(configuration);
		try {
			fCurrentLocation= configuration.getAttribute(IExternalToolConstants.ATTR_LOCATION, (String)null);
		} catch (CoreException e) {
		}
		updateCheckButtons(configuration);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#performApply(org.eclipse.debug.core.ILaunchConfigurationWorkingCopy)
	 */
	public void performApply(ILaunchConfigurationWorkingCopy configuration) {
		super.performApply(configuration);
		try {
			//has the location changed
			String newLocation= configuration.getAttribute(IExternalToolConstants.ATTR_LOCATION, (String)null);
			if (newLocation != null) {
				if (!newLocation.equals(fCurrentLocation)) {
					updateTargetsTab();
					fCurrentLocation= newLocation;
				}
			} else if (fCurrentLocation != null){
				updateTargetsTab();
				fCurrentLocation= newLocation;
			}
		} catch (CoreException e) {
		}
		setAttribute(IAntUIConstants.SET_INPUTHANDLER, configuration, fSetInputHandlerButton.getSelection(), true);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.ui.ILaunchConfigurationTab#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		Composite mainComposite = new Composite(parent, SWT.NONE);
		setControl(mainComposite);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(mainComposite, IAntUIHelpContextIds.ANT_MAIN_TAB);
		GridLayout layout = new GridLayout();
		layout.numColumns = 1;
		GridData gridData = new GridData(GridData.FILL_HORIZONTAL);
		mainComposite.setLayout(layout);
		mainComposite.setLayoutData(gridData);
		mainComposite.setFont(parent.getFont());
		createLocationComponent(mainComposite);
		createWorkDirectoryComponent(mainComposite);
		createArgumentComponent(mainComposite);
		createVerticalSpacer(mainComposite, 2);
		createSetInputHandlerComponent(mainComposite);
		Dialog.applyDialogFont(parent);
	}
	
	/**
	 * Creates the controls needed to edit the set input handler attribute of an
	 * Ant build
	 *
	 * @param parent the composite to create the controls in
	 */
	private void createSetInputHandlerComponent(Composite parent) {
		fSetInputHandlerButton = createCheckButton(parent, AntLaunchConfigurationMessages.AntMainTab_0); //$NON-NLS-1$
		GridData data = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		data.horizontalSpan = 2;
		fSetInputHandlerButton.setLayoutData(data);
		fSetInputHandlerButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				updateLaunchConfigurationDialog();
			}
		});
	}
	
	private void updateCheckButtons(ILaunchConfiguration configuration) {
		boolean setInputHandler= true;
		try {
			setInputHandler= configuration.getAttribute(IAntUIConstants.SET_INPUTHANDLER, true);
		} catch (CoreException ce) {
			AntUIPlugin.log(AntLaunchConfigurationMessages.AntMainTab_1, ce); //$NON-NLS-1$
		}
		fSetInputHandlerButton.setSelection(setInputHandler);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.externaltools.internal.launchConfigurations.ExternalToolsMainTab#handleWorkspaceLocationButtonSelected()
	 */
	protected void handleWorkspaceLocationButtonSelected() {
		FileSelectionDialog dialog;
		dialog = new FileSelectionDialog(getShell(), ResourcesPlugin.getWorkspace().getRoot(), AntLaunchConfigurationMessages.AntMainTab__Select_a_build_file__1); //$NON-NLS-1$
		dialog.setFileFilter(".*\\.xml", true); //$NON-NLS-1$
		dialog.open();
		IStructuredSelection result = dialog.getResult();
		if (result == null) {
			return;
		}
		Object file= result.getFirstElement();
		if (file instanceof IFile) {
			locationField.setText(VariablesPlugin.getDefault().getStringVariableManager().generateVariableExpression("workspace_loc", ((IFile)file).getFullPath().toString())); //$NON-NLS-1$
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.externaltools.internal.launchConfigurations.ExternalToolsMainTab#getWorkingDirectoryLabel()
	 */
	protected String getWorkingDirectoryLabel() {
		return AntLaunchConfigurationMessages.AntMainTab_3; //$NON-NLS-1$
	}
	
	private void updateTargetsTab() {
		//the location has changed...set the targets tab to 
		//need to be recomputed
		ILaunchConfigurationTab[] tabs=  getLaunchConfigurationDialog().getTabs();
		for (int i = 0; i < tabs.length; i++) {
			ILaunchConfigurationTab tab = tabs[i];
			if (tab instanceof AntTargetsTab) {
				((AntTargetsTab)tab).setDirty(true);
				break;
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.externaltools.internal.launchConfigurations.ExternalToolsMainTab#getLocationLabel()
	 */
	protected String getLocationLabel() {
		return AntLaunchConfigurationMessages.AntMainTab_6; //$NON-NLS-1$
	}
}
