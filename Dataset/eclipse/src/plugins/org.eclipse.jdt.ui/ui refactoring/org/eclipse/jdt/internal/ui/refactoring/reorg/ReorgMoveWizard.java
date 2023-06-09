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
package org.eclipse.jdt.internal.ui.refactoring.reorg;

import org.eclipse.core.resources.IResource;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

import org.eclipse.jface.dialogs.Dialog;

import org.eclipse.jdt.internal.corext.refactoring.reorg.ICreateTargetQuery;
import org.eclipse.jdt.internal.corext.refactoring.reorg.IReorgDestinationValidator;
import org.eclipse.jdt.internal.corext.refactoring.reorg.JavaMoveProcessor;

import org.eclipse.jdt.internal.ui.refactoring.RefactoringMessages;
import org.eclipse.jdt.internal.ui.util.SWTUtil;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.MoveRefactoring;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;


public class ReorgMoveWizard extends RefactoringWizard {

	public ReorgMoveWizard(MoveRefactoring ref) {
		super(ref, DIALOG_BASED_USER_INTERFACE | computeHasPreviewPage(ref));
		if (isTextualMove(ref))
			setDefaultPageTitle(ReorgMessages.ReorgMoveWizard_textual_move); 
		else
			setDefaultPageTitle(ReorgMessages.ReorgMoveWizard_3); 
	}
	
	private static boolean isTextualMove(MoveRefactoring ref) {
		JavaMoveProcessor moveProcessor= (JavaMoveProcessor) ref.getAdapter(JavaMoveProcessor.class);
		return moveProcessor.isTextualMove();
	}

	private static int computeHasPreviewPage(MoveRefactoring refactoring) {
		JavaMoveProcessor processor= (JavaMoveProcessor)refactoring.getAdapter(JavaMoveProcessor.class);
		if (processor.canUpdateReferences() || processor.canEnableQualifiedNameUpdating())
			return NONE;
		return NO_PREVIEW_PAGE;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.refactoring.RefactoringWizard#addUserInputPages()
	 */
	protected void addUserInputPages() {
		addPage(new MoveInputPage());
	}
	
	private static class MoveInputPage extends ReorgUserInputPage{

		private static final String PAGE_NAME= "MoveInputPage"; //$NON-NLS-1$
		private Button fReferenceCheckbox;
		private Button fQualifiedNameCheckbox;
		private QualifiedNameComponent fQualifiedNameComponent;
		
		private Object fDestination;
		
		public MoveInputPage() {
			super(PAGE_NAME);
		}

		private JavaMoveProcessor getJavaMoveProcessor(){
			return (JavaMoveProcessor)getRefactoring().getAdapter(JavaMoveProcessor.class);
		}

		protected Object getInitiallySelectedElement() {
			return getJavaMoveProcessor().getCommonParentForInputElements();
		}
		
		protected IJavaElement[] getJavaElements() {
			return getJavaMoveProcessor().getJavaElements();
		}

		protected IResource[] getResources() {
			return getJavaMoveProcessor().getResources();
		}

		protected IReorgDestinationValidator getDestinationValidator() {
			return getJavaMoveProcessor();
		}
		
		/* (non-Javadoc)
		 * @see org.eclipse.jdt.internal.ui.refactoring.RefactoringWizardPage#performFinish()
		 */
		protected boolean performFinish() {
			return super.performFinish() || getJavaMoveProcessor().wasCanceled(); //close the dialog if canceled
		}
		
		protected RefactoringStatus verifyDestination(Object selected) throws JavaModelException{
			JavaMoveProcessor processor= getJavaMoveProcessor();
			final RefactoringStatus refactoringStatus;
			if (selected instanceof IJavaElement)
				refactoringStatus= processor.setDestination((IJavaElement)selected);
			else if (selected instanceof IResource)
				refactoringStatus= processor.setDestination((IResource)selected);
			else refactoringStatus= RefactoringStatus.createFatalErrorStatus(ReorgMessages.ReorgMoveWizard_4); 
			
			updateUIStatus();
			fDestination= selected;
			return refactoringStatus;
		}
	
		private void updateUIStatus() {
			getRefactoringWizard().setForcePreviewReview(false);
			JavaMoveProcessor processor= getJavaMoveProcessor();
			if (fReferenceCheckbox != null){
				fReferenceCheckbox.setEnabled(canUpdateReferences());
				processor.setUpdateReferences(fReferenceCheckbox.getEnabled() && fReferenceCheckbox.getSelection());
			}
			if (fQualifiedNameCheckbox != null){
				boolean enabled= processor.canEnableQualifiedNameUpdating();
				fQualifiedNameCheckbox.setEnabled(enabled);
				if (enabled) {
					fQualifiedNameComponent.setEnabled(processor.getUpdateQualifiedNames());
					if (processor.getUpdateQualifiedNames())
						getRefactoringWizard().setForcePreviewReview(true);
				} else {
					fQualifiedNameComponent.setEnabled(false);
				}
				processor.setUpdateQualifiedNames(fQualifiedNameCheckbox.getEnabled() && fQualifiedNameCheckbox.getSelection());
			}
		}

		private void addUpdateReferenceComponent(Composite result) {
			final JavaMoveProcessor processor= getJavaMoveProcessor();
			if (! processor.canUpdateReferences())
				return;
			fReferenceCheckbox= new Button(result, SWT.CHECK);
			fReferenceCheckbox.setText(ReorgMessages.JdtMoveAction_update_references); 
			fReferenceCheckbox.setSelection(processor.getUpdateReferences());
			fReferenceCheckbox.setEnabled(canUpdateReferences());
			
			fReferenceCheckbox.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					processor.setUpdateReferences(((Button)e.widget).getSelection());
					updateUIStatus();
				}
			});
		}

		private void addUpdateQualifiedNameComponent(Composite parent, int marginWidth) {
			final JavaMoveProcessor processor= getJavaMoveProcessor();
			if (!processor.canEnableQualifiedNameUpdating() || !processor.canUpdateQualifiedNames())
				return;
			fQualifiedNameCheckbox= new Button(parent, SWT.CHECK);
			int indent= marginWidth + fQualifiedNameCheckbox.computeSize(SWT.DEFAULT, SWT.DEFAULT).x;
			fQualifiedNameCheckbox.setText(RefactoringMessages.RenameInputWizardPage_update_qualified_names); 
			fQualifiedNameCheckbox.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			fQualifiedNameCheckbox.setSelection(processor.getUpdateQualifiedNames());
		
			fQualifiedNameComponent= new QualifiedNameComponent(parent, SWT.NONE, processor, getRefactoringSettings());
			fQualifiedNameComponent.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			GridData gd= (GridData)fQualifiedNameComponent.getLayoutData();
			gd.horizontalAlignment= GridData.FILL;
			gd.horizontalIndent= indent;
			updateQualifiedNameUpdating(processor, processor.getUpdateQualifiedNames());

			fQualifiedNameCheckbox.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					boolean enabled= ((Button)e.widget).getSelection();
					updateQualifiedNameUpdating(processor, enabled);
				}

			});
		}
		
		private void updateQualifiedNameUpdating(final JavaMoveProcessor processor, boolean enabled) {
			fQualifiedNameComponent.setEnabled(enabled);
			processor.setUpdateQualifiedNames(enabled);
			updateUIStatus();
		}
		
		public void createControl(Composite parent) {
			Composite result;
			
			boolean showDestinationTree= ! getJavaMoveProcessor().hasDestinationSet();
			if (showDestinationTree) {
				super.createControl(parent);
				result= (Composite)super.getControl();
			} else  {
				initializeDialogUnits(parent);
				result= new Composite(parent, SWT.NONE);
				setControl(result);
				result.setLayout(new GridLayout());
				Dialog.applyDialogFont(result);
			}
			if (showDestinationTree && getJavaMoveProcessor().getCreateTargetQuery() != null) {
				addUpdateArea(result);
			} else {
				addUpdateReferenceComponent(result);
				addUpdateQualifiedNameComponent(result, ((GridLayout)result.getLayout()).marginWidth);
			}
			setControl(result);
			Dialog.applyDialogFont(result);
		}
		
		protected void addUpdateArea(Composite parent) {
			Composite firstLine= new Composite(parent, SWT.NONE);
			GridLayout layout= new GridLayout(2, false);
			layout.marginHeight= layout.marginWidth= 0;
			firstLine.setLayout(layout);
			firstLine.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
			
			if (getJavaMoveProcessor().canUpdateReferences()) {
				addUpdateReferenceComponent(firstLine);
				addUpdateQualifiedNameComponent(parent, layout.marginWidth);
			} else if (getJavaMoveProcessor().canUpdateQualifiedNames()) {
				addUpdateQualifiedNameComponent(firstLine, layout.marginWidth);
			} else {
				Composite filler= new Composite(firstLine, SWT.NONE);
				filler.setLayoutData(new GridData(GridData.FILL_BOTH));
			}
			
			Button newButton= new Button(firstLine, SWT.PUSH);
			newButton.setText(ReorgMessages.ReorgMoveWizard_new); 
			GridData gd= new GridData(GridData.HORIZONTAL_ALIGN_END | GridData.GRAB_HORIZONTAL | GridData.VERTICAL_ALIGN_BEGINNING);
			gd.widthHint = SWTUtil.getButtonWidthHint(newButton);
			newButton.setLayoutData(gd);
			newButton.addSelectionListener(new SelectionAdapter() {
				public void widgetSelected(SelectionEvent e) {
					doNewButtonPressed();
				}
			});
		}
		
		private boolean canUpdateReferences() {
			return getJavaMoveProcessor().canUpdateReferences();
		}

		private void doNewButtonPressed() {
			ICreateTargetQuery createTargetQuery= getJavaMoveProcessor().getCreateTargetQuery();
			Object newElement= createTargetQuery.getCreatedTarget(fDestination);
			if (newElement != null)
				addElementToTree(newElement);
		}
	}
}
