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
package org.eclipse.pde.internal.ui.wizards.plugin;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.pde.internal.core.ICoreConstants;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.ui.IHelpContextIds;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.WizardNewProjectCreationPage;


public class NewProjectCreationPage extends WizardNewProjectCreationPage {
	protected Button fJavaButton;
	protected boolean fIsFragment;
	protected Button fBundleCheck;
	private Label fSourceLabel;
	private Text fSourceText;
	private Label fOutputlabel;
	private Text fOutputText;
	private AbstractFieldData fData;
	private Combo fTargetCombo;
	
	public NewProjectCreationPage(String pageName, AbstractFieldData data, boolean isFragment){
		super(pageName);
		fIsFragment = isFragment;
		fData = data;
	}
	
	public void createControl(Composite parent) {
		super.createControl(parent);
		Composite control = (Composite)getControl();
		GridLayout layout = new GridLayout();
		layout.verticalSpacing = 10;
		control.setLayout(layout);

		createProjectTypeGroup(control);
		createFormatGroup(control);
		
		Dialog.applyDialogFont(control);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(control,
				fIsFragment ? IHelpContextIds.NEW_FRAGMENT_STRUCTURE_PAGE
							: IHelpContextIds.NEW_PROJECT_STRUCTURE_PAGE);
		setControl(control);
	}
	
	private void createProjectTypeGroup(Composite container) {
		Group group = new Group(container, SWT.NONE);
		group.setText(PDEUIMessages.ProjectStructurePage_settings); //$NON-NLS-1$
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		group.setLayout(layout);
		group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	
		fJavaButton = createButton(group);
		fJavaButton.setText(PDEUIMessages.ProjectStructurePage_java); //$NON-NLS-1$
		fJavaButton.setSelection(true);
		fJavaButton.addSelectionListener(new SelectionAdapter(){
			public void widgetSelected(SelectionEvent e) {
				boolean enabled = fJavaButton.getSelection();
				fSourceLabel.setEnabled(enabled);
				fSourceText.setEnabled(enabled);
				fOutputlabel.setEnabled(enabled);
				fOutputText.setEnabled(enabled);
				validatePage();
			}
		});
		
		fSourceLabel = createLabel(group, PDEUIMessages.ProjectStructurePage_source); //$NON-NLS-1$
		fSourceText = createText(group);
		IPreferenceStore store = PreferenceConstants.getPreferenceStore();
		fSourceText.setText(store.getString(PreferenceConstants.SRCBIN_SRCNAME));
		
		fOutputlabel = createLabel(group, PDEUIMessages.ProjectStructurePage_output); //$NON-NLS-1$
		fOutputText = createText(group);		
		fOutputText.setText(store.getString(PreferenceConstants.SRCBIN_BINNAME));
	}
	private void createFormatGroup(Composite container) {
		Group group = new Group(container, SWT.NONE);
		if (fIsFragment)
			group.setText(PDEUIMessages.ProjectStructurePage_fformat); //$NON-NLS-1$
		else
			group.setText(PDEUIMessages.ProjectStructurePage_pformat); //$NON-NLS-1$			
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		group.setLayout(layout);
		group.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		Label label = new Label(group, SWT.NONE);
		if (fIsFragment) {
			label.setText(PDEUIMessages.ProjectStructurePage_fTarget); //$NON-NLS-1$
		} else {
			label.setText(PDEUIMessages.ProjectStructurePage_pTarget); //$NON-NLS-1$
		}
		fTargetCombo = new Combo(group, SWT.READ_ONLY|SWT.SINGLE);
		fTargetCombo.setItems(new String[] {ICoreConstants.TARGET31, ICoreConstants.TARGET30, ICoreConstants.TARGET21});
		GridData gd = new GridData();
		gd.minimumWidth = 50;
		fTargetCombo.setLayoutData(gd);
		fTargetCombo.setText(PDECore.getDefault().getTargetVersion());
		fTargetCombo.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				updateBundleCheck();
			}
		});		
		fBundleCheck = new Button(group, SWT.CHECK);
		fBundleCheck.setText(PDEUIMessages.ProjectStructurePage_bundle); //$NON-NLS-1$
		updateBundleCheck();
	}
	
	private void updateBundleCheck() {
		boolean legacy = fTargetCombo.getText().equals(ICoreConstants.TARGET21);
		fBundleCheck.setSelection(!legacy);
		fBundleCheck.setEnabled(!legacy);
	}
	
	private Button createButton(Composite container) {
		Button button = new Button(container, SWT.CHECK);
		GridData gd = new GridData();
		gd.horizontalSpan = 2;
		button.setLayoutData(gd);
		return button;		
	}
	
	public boolean hasBundleStructure(){
		if (fBundleCheck == null)
			return false;
		return fBundleCheck.getSelection();
	}

	private Label createLabel(Composite container, String text) {
		Label label = new Label(container, SWT.NONE);
		label.setText(text);
		GridData gd = new GridData();
		gd.horizontalIndent = 22;
		label.setLayoutData(gd);
		return label;
	}
	
	private Text createText(Composite container) {
		Text text = new Text(container, SWT.BORDER | SWT.SINGLE);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.widthHint = 300;
		text.setLayoutData(gd);
		text.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				validatePage();
			}
		});
		return text;
	}
	
	public void updateData() {
		fData.setSimple(!fJavaButton.getSelection());
		fData.setSourceFolderName(fSourceText.getText().trim());
		fData.setOutputFolderName(fOutputText.getText().trim());
		fData.setHasBundleStructure(fBundleCheck.isEnabled() && fBundleCheck.getSelection());
		fData.setLegacy(fTargetCombo.getText().equals("2.1")); //$NON-NLS-1$
		fData.setTargetVersion(fTargetCombo.getText());
	}
	
}
