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
package org.eclipse.jdt.internal.ui.refactoring;

import org.eclipse.ltk.ui.refactoring.RefactoringWizard;

import org.eclipse.jdt.internal.corext.refactoring.code.InlineTempRefactoring;
import org.eclipse.jdt.internal.corext.util.Messages;

public class InlineTempWizard extends RefactoringWizard {

	public InlineTempWizard(InlineTempRefactoring ref) {
		super(ref, DIALOG_BASED_USER_INTERFACE | PREVIEW_EXPAND_FIRST_NODE | NO_BACK_BUTTON_ON_STATUS_DIALOG); 
		setDefaultPageTitle(RefactoringMessages.InlineTempWizard_defaultPageTitle); 
	}

	protected void addUserInputPages() {
		addPage(new InlineTempInputPage());
	}

	public int getMessageLineWidthInChars() {
		return 0;
	}
	
	private static class InlineTempInputPage extends MessageWizardPage {

		public static final String PAGE_NAME= "InlineTempInputPage"; //$NON-NLS-1$
	
		public InlineTempInputPage() {
			super(PAGE_NAME, true, MessageWizardPage.STYLE_QUESTION);
		}

		protected String getMessageString() {
			InlineTempRefactoring refactoring= (InlineTempRefactoring)getRefactoring();
			int occurences= refactoring.getReferenceOffsets().length;
			final String identifier= refactoring.getTempDeclaration().getName().getIdentifier();
			if (occurences == 1) 
				return Messages.format(RefactoringMessages.InlineTempInputPage_message_one,  identifier); 
			else
				return Messages.format(RefactoringMessages.InlineTempInputPage_message_multi,  
					new Object[] { new Integer(occurences),  identifier });
		}
	}
}