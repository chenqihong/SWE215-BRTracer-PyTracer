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
package org.eclipse.jdt.internal.ui.wizards.buildpaths;

import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.IWorkspaceRunnable;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;

import org.eclipse.jdt.core.IAccessRule;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.wizards.BuildPathDialogAccess;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;
import org.eclipse.jdt.internal.ui.util.PixelConverter;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.CheckedListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ITreeListAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.ListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.TreeListDialogField;

public class LibrariesWorkbookPage extends BuildPathBasePage {
	
	private ListDialogField fClassPathList;
	private IJavaProject fCurrJProject;
	
	private TreeListDialogField fLibrariesList;
	
	private Control fSWTControl;
	private final IWorkbenchPreferenceContainer fPageContainer;

	private final int IDX_ADDJAR= 0;
	private final int IDX_ADDEXT= 1;
	private final int IDX_ADDVAR= 2;
	private final int IDX_ADDLIB= 3;
	private final int IDX_ADDFOL= 4;
	
	private final int IDX_EDIT= 6;
	private final int IDX_REMOVE= 7;

	
		
	public LibrariesWorkbookPage(CheckedListDialogField classPathList, IWorkbenchPreferenceContainer pageContainer) {
		fClassPathList= classPathList;
		fPageContainer= pageContainer;
		fSWTControl= null;
		
		String[] buttonLabels= new String[] { 
			NewWizardMessages.LibrariesWorkbookPage_libraries_addjar_button,	
			NewWizardMessages.LibrariesWorkbookPage_libraries_addextjar_button, 
			NewWizardMessages.LibrariesWorkbookPage_libraries_addvariable_button, 
			NewWizardMessages.LibrariesWorkbookPage_libraries_addlibrary_button, 
			NewWizardMessages.LibrariesWorkbookPage_libraries_addclassfolder_button, 
			/* */ null,  
			NewWizardMessages.LibrariesWorkbookPage_libraries_edit_button, 
			NewWizardMessages.LibrariesWorkbookPage_libraries_remove_button
		};		
				
		LibrariesAdapter adapter= new LibrariesAdapter();
				
		fLibrariesList= new TreeListDialogField(adapter, buttonLabels, new CPListLabelProvider());
		fLibrariesList.setDialogFieldListener(adapter);
		fLibrariesList.setLabelText(NewWizardMessages.LibrariesWorkbookPage_libraries_label); 

		fLibrariesList.enableButton(IDX_REMOVE, false);
		fLibrariesList.enableButton(IDX_EDIT, false);
		
		fLibrariesList.setViewerSorter(new CPListElementSorter());

	}
		
	public void init(IJavaProject jproject) {
		fCurrJProject= jproject;
		updateLibrariesList();
	}
	
	
	private void updateLibrariesList() {
		List cpelements= fClassPathList.getElements();
		List libelements= new ArrayList(cpelements.size());
		
		int nElements= cpelements.size();
		for (int i= 0; i < nElements; i++) {
			CPListElement cpe= (CPListElement)cpelements.get(i);
			if (isEntryKind(cpe.getEntryKind())) {
				libelements.add(cpe);
			}
		}
		fLibrariesList.setElements(libelements);
	}		
		
	// -------- UI creation
	
	public Control getControl(Composite parent) {
		PixelConverter converter= new PixelConverter(parent);
		
		Composite composite= new Composite(parent, SWT.NONE);
			
		LayoutUtil.doDefaultLayout(composite, new DialogField[] { fLibrariesList }, true, SWT.DEFAULT, SWT.DEFAULT);
		LayoutUtil.setHorizontalGrabbing(fLibrariesList.getTreeControl(null));
		
		int buttonBarWidth= converter.convertWidthInCharsToPixels(24);
		fLibrariesList.setButtonsMinWidth(buttonBarWidth);
		
		fLibrariesList.getTreeViewer().setSorter(new CPListElementSorter());
		
		fSWTControl= composite;
				
		return composite;
	}
	
	private Shell getShell() {
		if (fSWTControl != null) {
			return fSWTControl.getShell();
		}
		return JavaPlugin.getActiveWorkbenchShell();
	}
	
	
	private class LibrariesAdapter implements IDialogFieldListener, ITreeListAdapter {
		
		private final Object[] EMPTY_ARR= new Object[0];
		
		// -------- IListAdapter --------
		public void customButtonPressed(TreeListDialogField field, int index) {
			libaryPageCustomButtonPressed(field, index);
		}
		
		public void selectionChanged(TreeListDialogField field) {
			libaryPageSelectionChanged(field);
		}
		
		public void doubleClicked(TreeListDialogField field) {
			libaryPageDoubleClicked(field);
		}
		
		public void keyPressed(TreeListDialogField field, KeyEvent event) {
			libaryPageKeyPressed(field, event);
		}

		public Object[] getChildren(TreeListDialogField field, Object element) {
			if (element instanceof CPListElement) {
				return ((CPListElement) element).getChildren(false);
			} else if (element instanceof CPListElementAttribute) {
				CPListElementAttribute attribute= (CPListElementAttribute) element;
				if (CPListElement.ACCESSRULES.equals(attribute.getKey())) {
					return (IAccessRule[]) attribute.getValue();
				}
			}
			return EMPTY_ARR;
		}

		public Object getParent(TreeListDialogField field, Object element) {
			if (element instanceof CPListElementAttribute) {
				return ((CPListElementAttribute) element).getParent();
			}
			return null;
		}

		public boolean hasChildren(TreeListDialogField field, Object element) {
			return getChildren(field, element).length > 0;
		}		
			
		// ---------- IDialogFieldListener --------
	
		public void dialogFieldChanged(DialogField field) {
			libaryPageDialogFieldChanged(field);
		}
	}
	
	private void libaryPageCustomButtonPressed(DialogField field, int index) {
		CPListElement[] libentries= null;
		switch (index) {
		case IDX_ADDJAR: /* add jar */
			libentries= openJarFileDialog(null);
			break;
		case IDX_ADDEXT: /* add external jar */
			libentries= openExtJarFileDialog(null);
			break;
		case IDX_ADDVAR: /* add variable */
			libentries= openVariableSelectionDialog(null);
			break;
		case IDX_ADDLIB: /* add library */
			libentries= openContainerSelectionDialog(null);
			break;
		case IDX_ADDFOL: /* add folder */
			libentries= openClassFolderDialog(null);
			break;			
		case IDX_EDIT: /* edit */
			editEntry();
			return;
		case IDX_REMOVE: /* remove */
			removeEntry();
			return;			
		}
		if (libentries != null) {
			int nElementsChosen= libentries.length;					
			// remove duplicates
			List cplist= fLibrariesList.getElements();
			List elementsToAdd= new ArrayList(nElementsChosen);
			
			for (int i= 0; i < nElementsChosen; i++) {
				CPListElement curr= libentries[i];
				if (!cplist.contains(curr) && !elementsToAdd.contains(curr)) {
					elementsToAdd.add(curr);
					curr.setAttribute(CPListElement.SOURCEATTACHMENT, BuildPathSupport.guessSourceAttachment(curr));
					curr.setAttribute(CPListElement.JAVADOC, BuildPathSupport.guessJavadocLocation(curr));
				}
			}
			if (!elementsToAdd.isEmpty() && (index == IDX_ADDFOL)) {
				askForAddingExclusionPatternsDialog(elementsToAdd);
			}
			
			fLibrariesList.addElements(elementsToAdd);
			if (index == IDX_ADDLIB) {
				fLibrariesList.refresh();
			}
			fLibrariesList.postSetSelection(new StructuredSelection(libentries));
		}
	}
	
	private void askForAddingExclusionPatternsDialog(List newEntries) {
		HashSet modified= new HashSet();
		fixNestingConflicts(newEntries, fClassPathList.getElements(), modified);
		if (!modified.isEmpty()) {
			String title= NewWizardMessages.LibrariesWorkbookPage_exclusion_added_title; 
			String message= NewWizardMessages.LibrariesWorkbookPage_exclusion_added_message; 
			MessageDialog.openInformation(getShell(), title, message);
		}
	}
	
	protected void libaryPageDoubleClicked(TreeListDialogField field) {
		List selection= fLibrariesList.getSelectedElements();
		if (canEdit(selection)) {
			editEntry();
		}
	}

	protected void libaryPageKeyPressed(TreeListDialogField field, KeyEvent event) {
		if (field == fLibrariesList) {
			if (event.character == SWT.DEL && event.stateMask == 0) {
				List selection= field.getSelectedElements();
				if (canRemove(selection)) {
					removeEntry();
				}
			}
		}	
	}	

	private void removeEntry() {
		List selElements= fLibrariesList.getSelectedElements();
		HashSet containerEntriesToUpdate= new HashSet();
		for (int i= selElements.size() - 1; i >= 0 ; i--) {
			Object elem= selElements.get(i);
			if (elem instanceof CPListElementAttribute) {
				CPListElementAttribute attrib= (CPListElementAttribute) elem;
				String key= attrib.getKey();
				Object value= null;
				if (key.equals(CPListElement.ACCESSRULES)) {
					value= new IAccessRule[0];
				}
				attrib.getParent().setAttribute(key, value);
				selElements.remove(i);
				
				if (attrib.getParent().getParentContainer() instanceof CPListElement) { // inside a container: apply changes right away
					containerEntriesToUpdate.add(attrib.getParent());
				}
			}
		}
		if (selElements.isEmpty()) {
			fLibrariesList.refresh();
			fClassPathList.dialogFieldChanged(); // validate
		} else {
			fLibrariesList.removeElements(selElements);
		}
		for (Iterator iter= containerEntriesToUpdate.iterator(); iter.hasNext();) {
			CPListElement curr= (CPListElement) iter.next();
			IClasspathEntry updatedEntry= curr.getClasspathEntry();
			updateContainerEntry(updatedEntry, fCurrJProject, ((CPListElement) curr.getParentContainer()).getPath());
		}
	}
	
	private boolean canRemove(List selElements) {
		if (selElements.size() == 0) {
			return false;
		}
		for (int i= 0; i < selElements.size(); i++) {
			Object elem= selElements.get(i);
			if (elem instanceof CPListElementAttribute) {
				CPListElementAttribute attrib= (CPListElementAttribute) elem;
				if (attrib.isInNonModifiableContainer()) {
					return false;
				}
				if (attrib.getKey().equals(CPListElement.ACCESSRULES)) {
					return ((IAccessRule[]) attrib.getValue()).length > 0;
				}
				if (attrib.getValue() == null) {
					return false;
				}
			} else if (elem instanceof CPListElement) {
				CPListElement curr= (CPListElement) elem;
				if (curr.getParentContainer() != null) {
					return false;
				}
			} else { // unknown element
				return false;
			}
		}		
		return true;
	}	

	/**
	 * Method editEntry.
	 */
	private void editEntry() {
		List selElements= fLibrariesList.getSelectedElements();
		if (selElements.size() != 1) {
			return;
		}
		Object elem= selElements.get(0);
		if (fLibrariesList.getIndexOfElement(elem) != -1) {
			editElementEntry((CPListElement) elem);
		} else if (elem instanceof CPListElementAttribute) {
			editAttributeEntry((CPListElementAttribute) elem);
		}
	}
	
	private void editAttributeEntry(CPListElementAttribute elem) {
		String key= elem.getKey();
		CPListElement selElement= elem.getParent();
		
		if (key.equals(CPListElement.SOURCEATTACHMENT)) {
			IClasspathEntry result= BuildPathDialogAccess.configureSourceAttachment(getShell(), selElement.getClasspathEntry());
			if (result != null) {
				selElement.setAttribute(CPListElement.SOURCEATTACHMENT, result.getSourceAttachmentPath());
				attributeUpdated(selElement);
				fLibrariesList.refresh(elem);
				fLibrariesList.update(selElement); // image
				fClassPathList.refresh(); // images
			}
		} else if (key.equals(CPListElement.JAVADOC)) {
			String initialLocation= (String) selElement.getAttribute(CPListElement.JAVADOC);
			
			String elementName= new CPListLabelProvider().getText(selElement);
			try {
				URL url= initialLocation != null ? new URL(initialLocation) : null;
				URL[] result= BuildPathDialogAccess.configureJavadocLocation(getShell(), elementName, url);
				if (result != null) {
					URL newURL= result[0];
					selElement.setAttribute(CPListElement.JAVADOC, newURL != null ? newURL.toExternalForm() : null);
					attributeUpdated(selElement);

					fLibrariesList.refresh(elem);
					fClassPathList.dialogFieldChanged(); // validate
				}
			} catch (MalformedURLException e) {
				// ignore
			}
		} else if (key.equals(CPListElement.ACCESSRULES)) {
			AccessRulesDialog dialog= new AccessRulesDialog(getShell(), selElement, fCurrJProject, fPageContainer != null);
			int res= dialog.open();
			if (res == Window.OK || res == AccessRulesDialog.SWITCH_PAGE) {
				selElement.setAttribute(CPListElement.ACCESSRULES, dialog.getAccessRules());
				attributeUpdated(selElement);
				
				fLibrariesList.refresh(elem);
				fClassPathList.dialogFieldChanged(); // validate
				
				if (res == AccessRulesDialog.SWITCH_PAGE) { // switch after updates and validation
					dialog.performPageSwitch(fPageContainer);
				}
			}
		} else if (key.equals(CPListElement.NATIVE_LIB_PATH)) {
			NativeLibrariesDialog dialog= new NativeLibrariesDialog(getShell(), selElement);
			if (dialog.open() == Window.OK) {
				selElement.setAttribute(CPListElement.NATIVE_LIB_PATH, dialog.getNativeLibraryPath());
				attributeUpdated(selElement);
				
				fLibrariesList.refresh(elem);
				fClassPathList.dialogFieldChanged(); // validate
			}
		}
	}
	
	private void attributeUpdated(CPListElement selElement) {
		Object parentContainer= selElement.getParentContainer();
		if (parentContainer instanceof CPListElement) { // inside a container: apply changes right away
			IClasspathEntry updatedEntry= selElement.getClasspathEntry();
			updateContainerEntry(updatedEntry, fCurrJProject, ((CPListElement) parentContainer).getPath());
		}
	}

	private void updateContainerEntry(final IClasspathEntry newEntry, final IJavaProject jproject, final IPath containerPath) {
		try {
			IWorkspaceRunnable runnable= new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) throws CoreException {				
					BuildPathSupport.modifyClasspathEntry(null, newEntry, jproject, containerPath, monitor);
				}
			};
			PlatformUI.getWorkbench().getProgressService().run(true, true, new WorkbenchRunnableAdapter(runnable));

		} catch (InvocationTargetException e) {
			String title= NewWizardMessages.LibrariesWorkbookPage_configurecontainer_error_title; 
			String message= NewWizardMessages.LibrariesWorkbookPage_configurecontainer_error_message; 
			ExceptionHandler.handle(e, getShell(), title, message);
		} catch (InterruptedException e) {
			// 
		}
	}
		
	private void editElementEntry(CPListElement elem) {
		CPListElement[] res= null;
		
		switch (elem.getEntryKind()) {
		case IClasspathEntry.CPE_CONTAINER:
			res= openContainerSelectionDialog(elem);
			break;
		case IClasspathEntry.CPE_LIBRARY:
			IResource resource= elem.getResource();
			if (resource == null) {
				res= openExtJarFileDialog(elem);
			} else if (resource.getType() == IResource.FOLDER) {
				if (resource.exists()) {
					res= openClassFolderDialog(elem);
				} else {
					res= openNewClassFolderDialog(elem);
				} 
			} else if (resource.getType() == IResource.FILE) {
				res= openJarFileDialog(elem);			
			}
			break;
		case IClasspathEntry.CPE_VARIABLE:
			res= openVariableSelectionDialog(elem);
			break;
		}
		if (res != null && res.length > 0) {
			CPListElement curr= res[0];
			curr.setExported(elem.isExported());
			fLibrariesList.replaceElement(elem, curr);
		}		
			
	}

	private void libaryPageSelectionChanged(DialogField field) {
		List selElements= fLibrariesList.getSelectedElements();
		fLibrariesList.enableButton(IDX_EDIT, canEdit(selElements));
		fLibrariesList.enableButton(IDX_REMOVE, canRemove(selElements));
		
		boolean noAttributes= containsOnlyTopLevelEntries(selElements);
		fLibrariesList.enableButton(IDX_ADDEXT, noAttributes);
		fLibrariesList.enableButton(IDX_ADDFOL, noAttributes);
		fLibrariesList.enableButton(IDX_ADDJAR, noAttributes);
		fLibrariesList.enableButton(IDX_ADDLIB, noAttributes);
		fLibrariesList.enableButton(IDX_ADDVAR, noAttributes);
	}
	
	private boolean canEdit(List selElements) {
		if (selElements.size() != 1) {
			return false;
		}
		Object elem= selElements.get(0);
		if (elem instanceof CPListElement) {
			CPListElement curr= (CPListElement) elem;
			return !(curr.getResource() instanceof IFolder) && curr.getParentContainer() == null;
		}
		if (elem instanceof CPListElementAttribute) {
			CPListElementAttribute attrib= (CPListElementAttribute) elem;
			if (attrib.isInNonModifiableContainer()) {
				return false;
			}
			return true;
		}
		return false;
	}
	
	private void libaryPageDialogFieldChanged(DialogField field) {
		if (fCurrJProject != null) {
			// already initialized
			updateClasspathList();
		}
	}	
		
	private void updateClasspathList() {
		List projelements= fLibrariesList.getElements();
		
		List cpelements= fClassPathList.getElements();
		int nEntries= cpelements.size();
		// backwards, as entries will be deleted
		int lastRemovePos= nEntries;
		for (int i= nEntries - 1; i >= 0; i--) {
			CPListElement cpe= (CPListElement)cpelements.get(i);
			int kind= cpe.getEntryKind();
			if (isEntryKind(kind)) {
				if (!projelements.remove(cpe)) {
					cpelements.remove(i);
					lastRemovePos= i;
				}	
			}
		}
		
		cpelements.addAll(lastRemovePos, projelements);

		if (lastRemovePos != nEntries || !projelements.isEmpty()) {
			fClassPathList.setElements(cpelements);
		}
	}
	
		
	private CPListElement[] openNewClassFolderDialog(CPListElement existing) {
		String title= (existing == null) ? NewWizardMessages.LibrariesWorkbookPage_NewClassFolderDialog_new_title : NewWizardMessages.LibrariesWorkbookPage_NewClassFolderDialog_edit_title; 
		IProject currProject= fCurrJProject.getProject();
		
		NewContainerDialog dialog= new NewContainerDialog(getShell(), title, currProject, getUsedContainers(existing), existing);
		IPath projpath= currProject.getFullPath();
		dialog.setMessage(Messages.format(NewWizardMessages.LibrariesWorkbookPage_NewClassFolderDialog_description, projpath.toString())); 
		if (dialog.open() == Window.OK) {
			IFolder folder= dialog.getFolder();
			return new CPListElement[] { newCPLibraryElement(folder) };
		}
		return null;
	}
			
			
	private CPListElement[] openClassFolderDialog(CPListElement existing) {
		if (existing == null) {
			IPath[] selected= BuildPathDialogAccess.chooseClassFolderEntries(getShell(), fCurrJProject.getPath(), getUsedContainers(existing));
			if (selected != null) {
				IWorkspaceRoot root= fCurrJProject.getProject().getWorkspace().getRoot();
				ArrayList res= new ArrayList();
				for (int i= 0; i < selected.length; i++) {
					IPath curr= selected[i];
					IResource resource= root.findMember(curr);
					if (resource instanceof IContainer) {
						res.add(newCPLibraryElement(resource));
					}
				}
				return (CPListElement[]) res.toArray(new CPListElement[res.size()]);
			}
		} else {
			// disabled
		}		
		return null;
	}
	
	private CPListElement[] openJarFileDialog(CPListElement existing) {
		IWorkspaceRoot root= fCurrJProject.getProject().getWorkspace().getRoot();
		
		if (existing == null) {
			IPath[] selected= BuildPathDialogAccess.chooseJAREntries(getShell(), fCurrJProject.getPath(), getUsedJARFiles(existing));
			if (selected != null) {
				ArrayList res= new ArrayList();
				
				for (int i= 0; i < selected.length; i++) {
					IPath curr= selected[i];
					IResource resource= root.findMember(curr);
					if (resource instanceof IFile) {
						res.add(newCPLibraryElement(resource));
					}
				}
				return (CPListElement[]) res.toArray(new CPListElement[res.size()]);
			}
		} else {
			IPath configured= BuildPathDialogAccess.configureJAREntry(getShell(), existing.getPath(), getUsedJARFiles(existing));
			if (configured != null) {
				IResource resource= root.findMember(configured);
				if (resource instanceof IFile) {
					return new CPListElement[] { newCPLibraryElement(resource) }; 
				}
			}
		}		
		return null;
	}
	
	private IPath[] getUsedContainers(CPListElement existing) {
		ArrayList res= new ArrayList();
		if (fCurrJProject.exists()) {
			try {
				IPath outputLocation= fCurrJProject.getOutputLocation();
				if (outputLocation != null && outputLocation.segmentCount() > 1) { // != Project
					res.add(outputLocation);
				}
			} catch (JavaModelException e) {
				// ignore it here, just log
				JavaPlugin.log(e.getStatus());
			}
		}	
			
		List cplist= fLibrariesList.getElements();
		for (int i= 0; i < cplist.size(); i++) {
			CPListElement elem= (CPListElement)cplist.get(i);
			if (elem.getEntryKind() == IClasspathEntry.CPE_LIBRARY && (elem != existing)) {
				IResource resource= elem.getResource();
				if (resource instanceof IContainer && !resource.equals(existing)) {
					res.add(resource.getFullPath());
				}
			}
		}
		return (IPath[]) res.toArray(new IPath[res.size()]);
	}
	
	private IPath[] getUsedJARFiles(CPListElement existing) {
		List res= new ArrayList();
		List cplist= fLibrariesList.getElements();
		for (int i= 0; i < cplist.size(); i++) {
			CPListElement elem= (CPListElement)cplist.get(i);
			if (elem.getEntryKind() == IClasspathEntry.CPE_LIBRARY && (elem != existing)) {
				IResource resource= elem.getResource();
				if (resource instanceof IFile) {
					res.add(resource.getFullPath());
				}
			}
		}
		return (IPath[]) res.toArray(new IPath[res.size()]);
	}	
	
	private CPListElement newCPLibraryElement(IResource res) {
		return new CPListElement(fCurrJProject, IClasspathEntry.CPE_LIBRARY, res.getFullPath(), res);
	}

	private CPListElement[] openExtJarFileDialog(CPListElement existing) {
		if (existing == null) {
			IPath[] selected= BuildPathDialogAccess.chooseExternalJAREntries(getShell());
			if (selected != null) {
				ArrayList res= new ArrayList();
				for (int i= 0; i < selected.length; i++) {
					res.add(new CPListElement(fCurrJProject, IClasspathEntry.CPE_LIBRARY, selected[i], null));
				}
				return (CPListElement[]) res.toArray(new CPListElement[res.size()]);
			}
		} else {
			IPath configured= BuildPathDialogAccess.configureExternalJAREntry(getShell(), existing.getPath());
			if (configured != null) {
				return new CPListElement[] { new CPListElement(fCurrJProject, IClasspathEntry.CPE_LIBRARY, configured, null) };
			}
		}		
		return null;
	}
		
	private CPListElement[] openVariableSelectionDialog(CPListElement existing) {
		List existingElements= fLibrariesList.getElements();
		ArrayList existingPaths= new ArrayList(existingElements.size());
		for (int i= 0; i < existingElements.size(); i++) {
			CPListElement elem= (CPListElement) existingElements.get(i);
			if (elem.getEntryKind() == IClasspathEntry.CPE_VARIABLE) {
				existingPaths.add(elem.getPath());
			}
		}
		IPath[] existingPathsArray= (IPath[]) existingPaths.toArray(new IPath[existingPaths.size()]);
		
		if (existing == null) {
			IPath[] paths= BuildPathDialogAccess.chooseVariableEntries(getShell(), existingPathsArray);
			if (paths != null) {
				ArrayList result= new ArrayList();
				for (int i = 0; i < paths.length; i++) {
					CPListElement elem= new CPListElement(fCurrJProject, IClasspathEntry.CPE_VARIABLE, paths[i], null);
					IPath resolvedPath= JavaCore.getResolvedVariablePath(paths[i]);
					elem.setIsMissing((resolvedPath == null) || !resolvedPath.toFile().exists());
					if (!existingElements.contains(elem)) {
						result.add(elem);
					}
				}
				return (CPListElement[]) result.toArray(new CPListElement[result.size()]);
			}
		} else {
			IPath path= BuildPathDialogAccess.configureVariableEntry(getShell(), existing.getPath(), existingPathsArray);
			if (path != null) {
				CPListElement elem= new CPListElement(fCurrJProject, IClasspathEntry.CPE_VARIABLE, path, null);
				return new CPListElement[] { elem };
			}
		}
		return null;
	}

	private CPListElement[] openContainerSelectionDialog(CPListElement existing) {
		if (existing == null) {
			IClasspathEntry[] created= BuildPathDialogAccess.chooseContainerEntries(getShell(), fCurrJProject, getRawClasspath());
			if (created != null) {
				CPListElement[] res= new CPListElement[created.length];
				for (int i= 0; i < res.length; i++) {
					res[i]= new CPListElement(fCurrJProject, IClasspathEntry.CPE_CONTAINER, created[i].getPath(), null);
				}
				return res;
			}
		} else {
			IClasspathEntry created= BuildPathDialogAccess.configureContainerEntry(getShell(), existing.getClasspathEntry(), fCurrJProject, getRawClasspath());
			if (created != null) {
				CPListElement elem= new CPListElement(fCurrJProject, IClasspathEntry.CPE_CONTAINER, created.getPath(), null);
				return new CPListElement[] { elem };
			}
		}		
		return null;
	}
		
	private IClasspathEntry[] getRawClasspath() {
		IClasspathEntry[] currEntries= new IClasspathEntry[fClassPathList.getSize()];
		for (int i= 0; i < currEntries.length; i++) {
			CPListElement curr= (CPListElement) fClassPathList.getElement(i);
			currEntries[i]= curr.getClasspathEntry();
		}
		return currEntries;
	}
		
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathBasePage#isEntryKind(int)
	 */
	public boolean isEntryKind(int kind) {
		return kind == IClasspathEntry.CPE_LIBRARY || kind == IClasspathEntry.CPE_VARIABLE || kind == IClasspathEntry.CPE_CONTAINER;
	}
	
	/*
	 * @see BuildPathBasePage#getSelection
	 */
	public List getSelection() {
		return fLibrariesList.getSelectedElements();
	}

	/*
	 * @see BuildPathBasePage#setSelection
	 */	
	public void setSelection(List selElements, boolean expand) {
		fLibrariesList.selectElements(new StructuredSelection(selElements));
		if (expand) {
			for (int i= 0; i < selElements.size(); i++) {
				fLibrariesList.expandElement(selElements.get(i), 1);
			}
		}
	}	


}