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

package org.eclipse.ltk.internal.ui.refactoring;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.DialogSettings;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.jface.wizard.WizardDialog;

import org.eclipse.ltk.internal.ui.refactoring.RefactoringUIPlugin;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

/**
 * A dialog to host refactoring wizards.
 */
public class RefactoringWizardDialog extends WizardDialog {

	private static final String DIALOG_SETTINGS= "RefactoringWizard"; //$NON-NLS-1$
	private static final String WIDTH= "width"; //$NON-NLS-1$
	private static final String HEIGHT= "height"; //$NON-NLS-1$

	private IDialogSettings fSettings;
	
	/*
	 * note: this field must not be initialized - setter is called in the call to super
	 * and java initializes fields 'after' the call to super is made. So initializing 
	 * would override setting.
	 */
	private boolean fMakeNextButtonDefault; 

	/**
	 * Creates a new refactoring wizard dialog with the given wizard.
	 * 
	 * @param parent the parent shell
	 * @param wizard the refactoring wizard
	 */
	public RefactoringWizardDialog(Shell parent, RefactoringWizard wizard) {
		super(parent, wizard);
		setShellStyle(getShellStyle() | SWT.RESIZE);
		IDialogSettings settings= RefactoringUIPlugin.getDefault().getDialogSettings();
		wizard.setDialogSettings(settings);
		fSettings= settings.getSection(DIALOG_SETTINGS);
		if (fSettings == null) {
			fSettings= new DialogSettings(DIALOG_SETTINGS);
			settings.addSection(fSettings);
			fSettings.put(WIDTH, 600);
			fSettings.put(HEIGHT, 400);
		}
		int width= 600;
		int height= 400;
		try {
			width= fSettings.getInt(WIDTH);
			height= fSettings.getInt(HEIGHT);
		} catch (NumberFormatException e) {
		}
		setMinimumPageSize(width, height);
	}
	
	/**
	 * {@inheritDoc}
	 */
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		getRefactoringWizard().getRefactoring().setValidationContext(newShell);
	}
	
	/*
	 * @see WizardDialog#finishPressed()
	 */
	protected void finishPressed() {
		IWizardPage page= getCurrentPage();
		Control control= page.getControl().getParent();
		Point size = control.getSize();
		fSettings.put(WIDTH, size.x);
		fSettings.put(HEIGHT, size.y);
		super.finishPressed();
	}	

	/*
	 * @see IWizardContainer#updateButtons()
	 */
	public void updateButtons() {
		super.updateButtons();
		if (! fMakeNextButtonDefault)
			return;
		if (getShell() == null)
			return;
		Button next= getButton(IDialogConstants.NEXT_ID);
		if (next.isEnabled())
			getShell().setDefaultButton(next);
	}

	/* usually called in the IWizard#setContainer(IWizardContainer) method
	 */
	public void makeNextButtonDefault() {
		fMakeNextButtonDefault= true;
	}
	
	public Button getCancelButton() {
		return getButton(IDialogConstants.CANCEL_ID);
	}
	
	private RefactoringWizard getRefactoringWizard() {
		return (RefactoringWizard)getWizard();
	}
}