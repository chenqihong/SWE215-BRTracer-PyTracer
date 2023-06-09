/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.preferences;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.pde.internal.ui.IHelpContextIds;
import org.eclipse.pde.internal.ui.IPreferenceConstants;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;

public class MainPreferencePage
	extends PreferencePage
	implements IWorkbenchPreferencePage, IPreferenceConstants {
	private Button useID;
	private Button useName;
	
	public MainPreferencePage() {
		setPreferenceStore(PDEPlugin.getDefault().getPreferenceStore());
		setDescription(PDEUIMessages.Preferences_MainPage_Description);
	}

	protected Control createContents(Composite parent) {
		IPreferenceStore store = PDEPlugin.getDefault().getPreferenceStore();
		
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout());
		
		Group group = new Group(composite, SWT.NONE);
		group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));		
		group.setText(PDEUIMessages.Preferences_MainPage_showObjects);
		group.setLayout(new GridLayout());
		
		useID = new Button(group, SWT.RADIO);
		useID.setText(PDEUIMessages.Preferences_MainPage_useIds);
		
		useName = new Button(group, SWT.RADIO);
		useName.setText(PDEUIMessages.Preferences_MainPage_useFullNames);
		
		if (store.getString(PROP_SHOW_OBJECTS).equals(VALUE_USE_IDS)) {
			useID.setSelection(true);
		} else {
			useName.setSelection(true);
		}
		
		return composite;		
	}
	
	public void createControl(Composite parent) {
		super.createControl(parent);
		Dialog.applyDialogFont(getControl());
		PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IHelpContextIds.MAIN_PREFERENCE_PAGE);
	}	

	public boolean performOk() {
		IPreferenceStore store = PDEPlugin.getDefault().getPreferenceStore();
		if (useID.getSelection()) {
			store.setValue(PROP_SHOW_OBJECTS, VALUE_USE_IDS);
		} else {
			store.setValue(PROP_SHOW_OBJECTS, VALUE_USE_NAMES);
		}
		PDEPlugin.getDefault().savePluginPreferences();
		return super.performOk();
	}
	
	protected void performDefaults() {
		IPreferenceStore store = PDEPlugin.getDefault().getPreferenceStore();
		if (store.getDefaultString(PROP_SHOW_OBJECTS).equals(VALUE_USE_IDS)) {
			useID.setSelection(true);
			useName.setSelection(false);
		} else {
			useID.setSelection(false);
			useName.setSelection(true);
		}
	}

	/**
	 * Initializes this preference page using the passed desktop.
	 *
	 * @param desktop the current desktop
	 */
	public void init(IWorkbench workbench) {
	}
}
