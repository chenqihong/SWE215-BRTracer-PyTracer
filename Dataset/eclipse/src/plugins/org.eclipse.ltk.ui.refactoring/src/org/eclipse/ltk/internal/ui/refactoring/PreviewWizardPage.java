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

import org.eclipse.compare.CompareUI;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ToolBarManager;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.CheckStateChangedEvent;
import org.eclipse.jface.viewers.ICheckStateListener;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.PageBook;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.internal.ui.refactoring.util.ViewerPane;
import org.eclipse.ltk.ui.refactoring.ChangePreviewViewerInput;
import org.eclipse.ltk.ui.refactoring.IChangePreviewViewer;
import org.eclipse.ltk.ui.refactoring.RefactoringWizard;
import org.eclipse.ltk.ui.refactoring.RefactoringWizardPage;

/**
 * Presents the changes made by the refactoring.
 * Consists of a tree of changes and a compare viewer that shows the differences. 
 */
public class PreviewWizardPage extends RefactoringWizardPage implements IPreviewWizardPage {
	
	private static class NullPreviewer implements IChangePreviewViewer {
		private Label fLabel;
		public void createControl(Composite parent) {
			fLabel= new Label(parent, SWT.CENTER | SWT.FLAT);
			fLabel.setText(RefactoringUIMessages.PreviewWizardPage_no_preview); 
		}
		public void refresh() {
		}
		public Control getControl() {
			return fLabel;
		}
		public void setInput(ChangePreviewViewerInput input) {
		}
	}
	
	private class NextChange extends Action {
		public NextChange() {
			setImageDescriptor(CompareUI.DESC_ETOOL_NEXT);
			setDisabledImageDescriptor(CompareUI.DESC_DTOOL_NEXT);
			setHoverImageDescriptor(CompareUI.DESC_CTOOL_NEXT);
			setToolTipText(RefactoringUIMessages.PreviewWizardPage_next_Change); 
			PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IRefactoringHelpContextIds.NEXT_CHANGE_ACTION);			
		}
		public void run() {
			fTreeViewer.revealNext();	
		}
	}
	
	private class PreviousChange extends Action {
		public PreviousChange() {
			setImageDescriptor(CompareUI.DESC_ETOOL_PREV);
			setDisabledImageDescriptor(CompareUI.DESC_DTOOL_PREV);
			setHoverImageDescriptor(CompareUI.DESC_CTOOL_PREV);
			setToolTipText(RefactoringUIMessages.PreviewWizardPage_previous_Change); 
			PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IRefactoringHelpContextIds.PREVIOUS_CHANGE_ACTION);			
		}	
		public void run() {
			fTreeViewer.revealPrevious();
		}
	}
	
	private Change fChange;
	private CompositeChange fTreeViewerInputChange;
	private ChangeElement fCurrentSelection;
	private PageBook fPageContainer;
	private Control fStandardPage;
	private Control fNullPage;
	private ChangeElementTreeViewer fTreeViewer;
	private PageBook fPreviewContainer;
	private ChangePreviewViewerDescriptor fCurrentDescriptor;
	private IChangePreviewViewer fCurrentPreviewViewer;
	private IChangePreviewViewer fNullPreviewer;
	
	/**
	 * Creates a new proposed changes wizard page.
	 */
	public PreviewWizardPage() {
		super(PAGE_NAME);
		setDescription(RefactoringUIMessages.PreviewWizardPage_description); 
	}

	/**
	 * Sets the given change. Setting the change initializes the tree viewer with
	 * the given change.
	 * @param change the new change.
	 */
	public void setChange(Change change) {
		if (fChange == change)
			return;
		
		fChange= change;
		if (fChange instanceof CompositeChange) {
			fTreeViewerInputChange= (CompositeChange)fChange;
		} else {
			fTreeViewerInputChange= new CompositeChange("Dummy Change"); //$NON-NLS-1$
			fTreeViewerInputChange.add(fChange);
		}
		setTreeViewerInput();
	}

	/**
	 * Creates the tree viewer to present the hierarchy of changes. Subclasses may override
	 * to create their own custom tree viewer.
	 * 
	 * @param parent the tree viewer's parent
	 * 
	 * @return the tree viewer to present the hierarchy of changes
	 */
	protected ChangeElementTreeViewer createTreeViewer(Composite parent) {
		return new ChangeElementTreeViewer(parent);
	}
	
	/**
	 * Creates the content provider used to fill the tree of changes. Subclasses may override
	 * to create their own custom tree content provider.
	 *
	 * @return the tree content provider used to fill the tree of changes
	 */
	protected ITreeContentProvider createTreeContentProvider() {
		return new ChangeElementContentProvider();
	}
	
	/**
	 * Creates the label provider used to render the tree of changes. Subclasses may override
	 * to create their own custom label provider.
	 *
	 * @return the label provider used to render the tree of changes
	 */
	protected ILabelProvider createTreeLabelProvider() {
		// return new ChangeElementLabelProvider(JavaElementLabelProvider.SHOW_DEFAULT | JavaElementLabelProvider.SHOW_SMALL_ICONS);
		return new ChangeElementLabelProvider();
	}
	
	/* (non-JavaDoc)
	 * Method defined in RefactoringWizardPage
	 */
	protected boolean performFinish() {
		UIPerformChangeOperation operation= new UIPerformChangeOperation(getShell().getDisplay(), fChange, getContainer());
		FinishResult result= getRefactoringWizard().internalPerformFinish(InternalAPI.INSTANCE, operation);
		if (result.isException())
			return true;
		if (result.isInterrupted())
			return false;
		RefactoringStatus fValidationStatus= operation.getValidationStatus();
		if (fValidationStatus != null && fValidationStatus.hasFatalError()) {
			RefactoringWizard wizard= getRefactoringWizard();
			MessageDialog.openError(wizard.getShell(), wizard.getWindowTitle(), 
				Messages.format(
					RefactoringUIMessages.RefactoringUI_cannot_execute, //$NON-NLS-1$
					fValidationStatus.getMessageMatchingSeverity(RefactoringStatus.FATAL)));
			return true;
		}
		return true;
	} 
	
	/* (non-JavaDoc)
	 * Method defined in IWizardPage
	 */
	public boolean canFlipToNextPage() {
		return false;
	}
	
	/* (Non-JavaDoc)
	 * Method defined in IWizardPage
	 */
	public void createControl(Composite parent) {
		initializeDialogUnits(parent);
		fPageContainer= new PageBook(parent, SWT.NONE);
		fStandardPage= createStandardPreviewPage(fPageContainer);
		fNullPage= createNullPage(fPageContainer);
		setControl(fPageContainer);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IRefactoringHelpContextIds.REFACTORING_PREVIEW_WIZARD_PAGE);
	}

	private Composite createStandardPreviewPage(Composite parent) {
		// XXX The composite is needed to limit the width of the SashForm. See http://bugs.eclipse.org/bugs/show_bug.cgi?id=6854
		Composite result= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.marginHeight= 0; layout.marginWidth= 0;
		result.setLayout(layout);
		
		SashForm sashForm= new SashForm(result, SWT.VERTICAL);
		
		ViewerPane pane= new ViewerPane(sashForm, SWT.BORDER | SWT.FLAT);
		pane.setText(RefactoringUIMessages.PreviewWizardPage_changes); 
		ToolBarManager tbm= pane.getToolBarManager();
		tbm.add(new NextChange());
		tbm.add(new PreviousChange());
		tbm.update(true);
		
		fTreeViewer= createTreeViewer(pane);
		fTreeViewer.setContentProvider(createTreeContentProvider());
		fTreeViewer.setLabelProvider(createTreeLabelProvider());
		fTreeViewer.addSelectionChangedListener(createSelectionChangedListener());
		fTreeViewer.addCheckStateListener(createCheckStateListener());
		pane.setContent(fTreeViewer.getControl());
		setTreeViewerInput();
		
		fPreviewContainer= new PageBook(sashForm, SWT.NONE);
		fNullPreviewer= new NullPreviewer();
		fNullPreviewer.createControl(fPreviewContainer);
		fPreviewContainer.showPage(fNullPreviewer.getControl());
		fCurrentPreviewViewer= fNullPreviewer;
		fCurrentDescriptor= null;
		
		sashForm.setWeights(new int[]{33, 67});
		GridData gd= new GridData(GridData.FILL_BOTH);
		gd.widthHint= convertWidthInCharsToPixels(80);
		sashForm.setLayoutData(gd);
		Dialog.applyDialogFont(result);
		return result;
	}
	
	private Control createNullPage(Composite parent) {
		Composite result= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		layout.marginWidth = convertHorizontalDLUsToPixels(IDialogConstants.HORIZONTAL_MARGIN);
		layout.marginHeight = convertVerticalDLUsToPixels(IDialogConstants.VERTICAL_MARGIN);
		result.setLayout(layout);
		Label label= new Label(result, SWT.CENTER);
		label.setText(RefactoringUIMessages.PreviewWizardPage_no_source_code_change); 
		label.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		Dialog.applyDialogFont(result);
		return result;
	}
	
	/* (Non-JavaDoc)
	 * Method defined in IWizardPage
	 */
	public void setVisible(boolean visible) {
		fCurrentSelection= null;
		if (hasChanges()) {
			fPageContainer.showPage(fStandardPage);
			ChangeElement treeViewerInput= (ChangeElement)fTreeViewer.getInput();
			if (visible && treeViewerInput != null) {
				IStructuredSelection selection= (IStructuredSelection)fTreeViewer.getSelection();
				if (selection.isEmpty()) {
					ITreeContentProvider provider= (ITreeContentProvider)fTreeViewer.getContentProvider();
					ChangeElement element= getFirstNonCompositeChange(provider, treeViewerInput);
					if (element != null) {
						if (getRefactoringWizard().internalGetExpandFirstNode(InternalAPI.INSTANCE)) {
							Object[] subElements= provider.getElements(element);
							if (subElements != null && subElements.length > 0) {
								fTreeViewer.expandToLevel(element, 999);
							}
						}
						fTreeViewer.setSelection(new StructuredSelection(element));
					}
				}
			}
			super.setVisible(visible);
			fTreeViewer.getControl().setFocus();
		} else {
			fPageContainer.showPage(fNullPage);
			super.setVisible(visible);
		}
		getRefactoringWizard().internalSetPreviewShown(InternalAPI.INSTANCE, visible);
	}
	
	private ChangeElement getFirstNonCompositeChange(ITreeContentProvider provider, ChangeElement input) {
		ChangeElement focus= input;
		Change change= input.getChange();
		while (change != null && change instanceof CompositeChange) {
			ChangeElement[] children= (ChangeElement[])provider.getElements(focus);
			if (children == null || children.length == 0)
				return null;
			focus= children[0];
			change= focus.getChange();
		}
		return focus;
	}
	
	private void setTreeViewerInput() {
		if (fTreeViewer == null)
			return;
		ChangeElement input= null;
		if (fTreeViewerInputChange != null) {
			input= new DefaultChangeElement(null, fTreeViewerInputChange);
		}
		fTreeViewer.setInput(input);
	}
	
	private ICheckStateListener createCheckStateListener() {
		return new ICheckStateListener() {
			public void checkStateChanged(CheckStateChangedEvent event){
				ChangeElement element= (ChangeElement)event.getElement();
				if (isChild(fCurrentSelection, element) || isChild(element, fCurrentSelection)) {
					showPreview(fCurrentSelection);
				}
			}
			private boolean isChild(ChangeElement element, ChangeElement child) {
				while (child != null) {
					if (child == element)
						return true;
					child= child.getParent();
				}
				return false;
			}
		};
	}
		
	private ISelectionChangedListener createSelectionChangedListener() {
		return new ISelectionChangedListener(){
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection sel= (IStructuredSelection) event.getSelection();
				if (sel.size() == 1) {
					ChangeElement newSelection= (ChangeElement)sel.getFirstElement();
					if (newSelection != fCurrentSelection) {
						fCurrentSelection= newSelection;
						showPreview(newSelection);
					}
				} else {
					showPreview(null);
				}
			}
		};
	}	

	private void showPreview(ChangeElement element) {
		try {
			if (element == null) {
				showNullPreviewer();
			} else {
				ChangePreviewViewerDescriptor descriptor= element.getChangePreviewViewerDescriptor();
				if (fCurrentDescriptor != descriptor) {
					IChangePreviewViewer newViewer;
					if (descriptor != null) {
						newViewer= descriptor.createViewer();
						newViewer.createControl(fPreviewContainer);
					} else {
						newViewer= fNullPreviewer;
					}
					fCurrentDescriptor= descriptor;
					element.feedInput(newViewer);
					if (fCurrentPreviewViewer != null && fCurrentPreviewViewer != fNullPreviewer)
						fCurrentPreviewViewer.getControl().dispose();
					fCurrentPreviewViewer= newViewer;				
					fPreviewContainer.showPage(fCurrentPreviewViewer.getControl());
				} else {
					element.feedInput(fCurrentPreviewViewer);
				}
			}
		} catch (CoreException e) {
			showNullPreviewer();
			ExceptionHandler.handle(e, getShell(),
						RefactoringUIMessages.PreviewWizardPage_refactoring, 
						RefactoringUIMessages.PreviewWizardPage_Internal_error); 
		}
	}
	
	private void showNullPreviewer() {
		fCurrentDescriptor= null;
		fCurrentPreviewViewer= fNullPreviewer;
		fPreviewContainer.showPage(fCurrentPreviewViewer.getControl());
	}

	/**
	 * Returns <code>true</code> if the preview page will show any changes when
	 * it becomes visibile. Otherwise <code>false</code> is returned.
	 * 
	 * @return whether the preview has changes or not
	 */
	public boolean hasChanges() {
		if (fChange == null)
			return false;
		if (fChange instanceof CompositeChange)
			return ((CompositeChange)fChange).getChildren().length > 0;
		return true;
	}
}
