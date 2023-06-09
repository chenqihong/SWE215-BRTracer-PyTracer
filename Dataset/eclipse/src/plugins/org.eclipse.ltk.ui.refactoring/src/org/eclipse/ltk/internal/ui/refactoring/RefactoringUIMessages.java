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
package org.eclipse.ltk.internal.ui.refactoring;

import org.eclipse.osgi.util.NLS;

public final class RefactoringUIMessages extends NLS {

	private static final String BUNDLE_NAME= "org.eclipse.ltk.internal.ui.refactoring.RefactoringUIMessages";//$NON-NLS-1$

	private RefactoringUIMessages() {
		// Do not instantiate
	}

	public static String RefactoringUIPlugin_internal_error;
	public static String RefactoringUIPlugin_listener_removed;
	public static String ExceptionHandler_seeErrorLogMessage;
	public static String UndoManagerAction_internal_error_title;
	public static String UndoManagerAction_internal_error_message;
	public static String UndoRefactoringAction_label;
	public static String UndoRefactoringAction_extendedLabel;
	public static String UndoRefactoringAction_name;
	public static String UndoRefactoringAction_error_title;
	public static String UndoRefactoringAction_error_message;
	public static String RedoRefactoringAction_label;
	public static String RedoRefactoringAction_extendedLabel;
	public static String RedoRefactoringAction_name;
	public static String RedoRefactoringAction_error_title;
	public static String RedoRefactoringAction_error_message;
	public static String RefactoringWizard_title;
	public static String RefactoringWizard_refactoring;
	public static String RefactoringWizard_see_log;
	public static String RefactoringWizard_Internal_error;
	public static String RefactoringWizard_internal_error_1;
	public static String RefactoringWizard_unexpected_exception;
	public static String RefactoringWizard_unexpected_exception_1;
	public static String ErrorWizardPage_no_context_information_available;
	public static String ErrorWizardPage_cannot_proceed;
	public static String ErrorWizardPage_confirm;
	public static String ErrorWizardPage_next_Change;
	public static String ErrorWizardPage_previous_Change;
	public static String PreviewWizardPage_no_preview;
	public static String PreviewWizardPage_next_Change;
	public static String PreviewWizardPage_previous_Change;
	public static String PreviewWizardPage_changes;
	public static String PreviewWizardPage_refactoring;
	public static String PreviewWizardPage_Internal_error;
	public static String PreviewWizardPage_description;
	public static String PreviewWizardPage_changeElementLabelProvider_textFormat;
	public static String PreviewWizardPage_no_source_code_change;
	public static String ComparePreviewer_element_name;
	public static String ComparePreviewer_original_source;
	public static String ComparePreviewer_refactored_source;
	public static String ChangeExceptionHandler_abort;
	public static String ChangeExceptionHandler_refactoring;
	public static String ChangeExceptionHandler_undo;
	public static String ChangeExceptionHandler_unexpected_exception;
	public static String ChangeExceptionHandler_button_explanation;
	public static String ChangeExceptionHandler_no_details;
	public static String ChangeExceptionHandler_rollback_message;
	public static String ChangeExceptionHandler_rollback_title;
	public static String RefactoringStatusDialog_Cannot_proceed;
	public static String RefactoringStatusDialog_Please_look;
	public static String RefactoringStatusDialog_Continue;
	public static String RefactoringWizardDialog2_buttons_preview_label;
	public static String RefactoringStatusViewer_Found_problems;
	public static String RefactoringStatusViewer_Problem_context;
	public static String RefactoringStatusViewer_error_title;
	public static String RefactoringStatusViewer_error_message;
	public static String FileStatusContextViewer_error_reading_file;
	public static String RefactoringUI_open_unexpected_exception;
	public static String RefactoringUI_cannot_execute;
	public static String ValidationCheckResultQuery_error_message;

	static {
		NLS.initializeMessages(BUNDLE_NAME, RefactoringUIMessages.class);
	}
}