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

import org.eclipse.debug.internal.ui.DialogSettingsHelper;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

/**
 * A simple input dialog for soliciting an input string
 * from the user.
 * <p>
 * This concete dialog class can be instantiated as is, 
 * or further subclassed as required.
 * </p>
 */
public class ChangeVariableValueInputDialog extends Dialog {
	
	
	/**
	 * The title of the dialog.
	 */
	private String title;
	
	/**
	 * The message to display, or <code>null</code> if none.
	 */
	private String message;
	
	/**
	 * The input value; the empty string by default.
	 */
	private String value= "";//$NON-NLS-1$
	
	/**
	 * The input validator, or <code>null</code> if none.
	 */
	private IInputValidator validator;
	
	/**
	 * Ok button widget.
	 */
	private Button okButton;
	
	/**
	 * Input text widget.
	 */
	private Text text;
	
	/**
	 * Error message label widget.
	 */
	private Label errorMessageLabel;
	/**
	 * Creates an input dialog with OK and Cancel buttons.
	 * Note that the dialog will have no visual representation (no widgets)
	 * until it is told to open.
	 * <p>
	 * Note that the <code>open</code> method blocks for input dialogs.
	 * </p>
	 *
	 * @param parentShell the parent shell
	 * @param dialogTitle the dialog title, or <code>null</code> if none
	 * @param dialogMessage the dialog message, or <code>null</code> if none
	 * @param initialValue the initial input value, or <code>null</code> if none
	 *  (equivalent to the empty string)
	 * @param validator an input validator, or <code>null</code> if none
	 */
	public ChangeVariableValueInputDialog(Shell parentShell, String dialogTitle, String dialogMessage, String initialValue, IInputValidator validator) {
		super(parentShell);
		this.title = dialogTitle;
		message = dialogMessage;
		if (initialValue == null)
			value = "";//$NON-NLS-1$
		else
			value = initialValue;
		this.validator = validator;
		
		setShellStyle(getShellStyle() | SWT.RESIZE);
	}
	/* (non-Javadoc)
	 * Method declared on Dialog.
	 */
	protected void buttonPressed(int buttonId) {
		if (buttonId == IDialogConstants.OK_ID) {
			value= text.getText();
		} else {
			value= null;
		}
		super.buttonPressed(buttonId);
	}
	/* (non-Javadoc)
	 * Method declared in Window.
	 */
	protected void configureShell(Shell shell) {
		super.configureShell(shell);
		if (title != null)
			shell.setText(title);
	}
	/* (non-Javadoc)
	 * Method declared on Dialog.
	 */
	protected void createButtonsForButtonBar(Composite parent) {
		// create OK and Cancel buttons by default
		okButton = createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
		createButton(parent, IDialogConstants.CANCEL_ID, IDialogConstants.CANCEL_LABEL, false);
		
		//do this here because setting the text will set enablement on the ok button
		text.setFocus();
		if (value != null) {
			text.setText(value);
			text.selectAll();
		}
	}
	/* (non-Javadoc)
	 * Method declared on Dialog.
	 */
	protected Control createDialogArea(Composite parent) {
		Font font = parent.getFont();
		// create composite
		Composite composite = (Composite)super.createDialogArea(parent);
		
		// create message
		if (message != null) {
			Label label = new Label(composite, SWT.WRAP);
			label.setText(message);
			GridData data = new GridData(
					GridData.GRAB_HORIZONTAL |
					GridData.HORIZONTAL_ALIGN_FILL |
					GridData.VERTICAL_ALIGN_CENTER);
			data.widthHint = convertHorizontalDLUsToPixels(IDialogConstants.MINIMUM_MESSAGE_AREA_WIDTH);
			label.setLayoutData(data);
			label.setFont(font);
		}
		
		text= new Text(composite, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL| SWT.H_SCROLL);
		
		GridData gridData= new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL | GridData.GRAB_VERTICAL | GridData.VERTICAL_ALIGN_FILL);
		gridData.heightHint = 50;
		gridData.widthHint = 100;
		text.setLayoutData(gridData);
		text.setFont(font);
		text.addModifyListener(
				new ModifyListener() {
					public void modifyText(ModifyEvent e) {
						if (okButton.isEnabled()) {
							return;
						}
						errorMessageLabel.setText(""); //$NON-NLS-1$
						errorMessageLabel.getParent().update();
						okButton.setEnabled(true);
					}
				}
		);
		
		errorMessageLabel = new Label(composite, SWT.NONE);
		errorMessageLabel.setLayoutData(new GridData(
				GridData.GRAB_HORIZONTAL |
				GridData.HORIZONTAL_ALIGN_FILL));
		errorMessageLabel.setFont(font);
		
		
		return composite;
	}
	/**
	 * Returns the error message label.
	 *
	 * @return the error message label
	 */
	protected Label getErrorMessageLabel() {
		return errorMessageLabel;
	}
	/**
	 * Returns the ok button.
	 *
	 * @return the ok button
	 */
	protected Button getOkButton() {
		return okButton;
	}
	/**
	 * Returns the text area.
	 *
	 * @return the text area
	 */
	protected Text getText() {
		return text;
	}
	/**
	 * Returns the validator.
	 *
	 * @return the validator
	 */
	protected IInputValidator getValidator() {
		return validator;
	}
	/**
	 * Returns the string typed into this input dialog.
	 *
	 * @return the input string
	 */
	public String getValue() {
		return value;
	}
	/**
	 * Validates the input.
	 * <p>
	 * The default implementation of this framework method
	 * delegates the request to the supplied input validator object;
	 * if it finds the input invalid, the error message is displayed
	 * in the dialog's message line.
	 * This hook method is called whenever the text changes in the
	 * input field.
	 * </p>
	 */
	protected void validateInput() {
		
		String errorMessage = null;
		
		if (validator != null) {
			errorMessage = validator.isValid(text.getText());
		}
		
		// Bug 16256: important not to treat "" (blank error) the same as null (no error)
		errorMessageLabel.setText(errorMessage == null ? "" : errorMessage); //$NON-NLS-1$
		okButton.setEnabled(errorMessage == null);
		
		errorMessageLabel.getParent().update();
	}
	protected void okPressed() {
		validateInput();
		if (okButton.isEnabled()) {
			super.okPressed();
		}
	}
	
	/**
	 * Returns the name of the section that this dialog stores its settings in
	 * 
	 * @return String
	 */
	protected String getDialogSettingsSectionName() {
		return IDebugUIConstants.PLUGIN_ID + ".CHANGE_VARIABLE_VALUE_DIALOG_SECTION"; //$NON-NLS-1$
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.window.Window#close()
	 */
	public boolean close() {
		DialogSettingsHelper.persistShellGeometry(getShell(), getDialogSettingsSectionName());
		return super.close();
	}	
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.window.Window#getInitialLocation(org.eclipse.swt.graphics.Point)
	 */
	protected Point getInitialLocation(Point initialSize) {
		Point initialLocation= DialogSettingsHelper.getInitialLocation(getDialogSettingsSectionName());
		if (initialLocation != null) {
			return initialLocation;
		}
		return super.getInitialLocation(initialSize);
	}
	
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.window.Window#getInitialSize()
	 */
	protected Point getInitialSize() {
		Point size = super.getInitialSize();
		return DialogSettingsHelper.getInitialSize(getDialogSettingsSectionName(), size);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.window.Window#handleShellCloseEvent()
	 */
	protected void handleShellCloseEvent() {
		value= null;
		super.handleShellCloseEvent();
	}
}
