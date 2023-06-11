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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.TabFolder;
import org.eclipse.swt.widgets.TabItem;
import org.eclipse.swt.widgets.Widget;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;

import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;

import org.eclipse.ui.views.navigator.ResourceSorter;

import org.eclipse.ui.ide.IDE;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaModelStatus;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.dialogs.StatusInfo;
import org.eclipse.jdt.internal.ui.dialogs.StatusUtil;
import org.eclipse.jdt.internal.ui.util.CoreUtility;
import org.eclipse.jdt.internal.ui.util.PixelConverter;
import org.eclipse.jdt.internal.ui.viewsupport.ImageDisposer;
import org.eclipse.jdt.internal.ui.wizards.IStatusChangeListener;
import org.eclipse.jdt.internal.ui.wizards.NewWizardMessages;
import org.eclipse.jdt.internal.ui.wizards.TypedElementSelectionValidator;
import org.eclipse.jdt.internal.ui.wizards.TypedViewerFilter;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.newsourcepage.NewSourceContainerWorkbookPage;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.CheckedListDialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.DialogField;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IDialogFieldListener;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.IStringButtonAdapter;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.LayoutUtil;
import org.eclipse.jdt.internal.ui.wizards.dialogfields.StringButtonDialogField;

public class BuildPathsBlock {

	public static interface IRemoveOldBinariesQuery {
		
		/**
		 * Do the callback. Returns <code>true</code> if .class files should be removed from the
		 * old output location.
		 * @param oldOutputLocation The old output location
		 * @return Returns true if .class files should be removed.
		 * @throws OperationCanceledException
		 */
		boolean doQuery(IPath oldOutputLocation) throws OperationCanceledException;
		
	}


	private IWorkspaceRoot fWorkspaceRoot;

	private CheckedListDialogField fClassPathList;
	private StringButtonDialogField fBuildPathDialogField;
	
	private StatusInfo fClassPathStatus;
	private StatusInfo fOutputFolderStatus;	
	private StatusInfo fBuildPathStatus;

	private IJavaProject fCurrJProject;
		
	private IPath fOutputLocationPath;
	
	private IStatusChangeListener fContext;
	private Control fSWTWidget;	
	private TabFolder fTabFolder;
	
	private int fPageIndex;
	
	private BuildPathBasePage fSourceContainerPage;
	private ProjectsWorkbookPage fProjectsPage;
	private LibrariesWorkbookPage fLibrariesPage;
	
	private BuildPathBasePage fCurrPage;
	
	private String fUserSettingsTimeStamp;
	private long fFileTimeStamp;
    
    private IRunnableContext fRunnableContext;
    private boolean fUseNewPage;

	private final IWorkbenchPreferenceContainer fPageContainer; // null when invoked from a non-property page context
		
	public BuildPathsBlock(IRunnableContext runnableContext, IStatusChangeListener context, int pageToShow, boolean useNewPage, IWorkbenchPreferenceContainer pageContainer) {
		fPageContainer= pageContainer;
		fWorkspaceRoot= JavaPlugin.getWorkspace().getRoot();
		fContext= context;
		fUseNewPage= useNewPage;
		
		fPageIndex= pageToShow;
		
		fSourceContainerPage= null;
		fLibrariesPage= null;
		fProjectsPage= null;
		fCurrPage= null;
        fRunnableContext= runnableContext;
				
		BuildPathAdapter adapter= new BuildPathAdapter();			
	
		String[] buttonLabels= new String[] {
			NewWizardMessages.BuildPathsBlock_classpath_up_button, 
			NewWizardMessages.BuildPathsBlock_classpath_down_button, 
			/* 2 */ null,
			NewWizardMessages.BuildPathsBlock_classpath_checkall_button, 
			NewWizardMessages.BuildPathsBlock_classpath_uncheckall_button
		
		};
		
		fClassPathList= new CheckedListDialogField(null, buttonLabels, new CPListLabelProvider());
		fClassPathList.setDialogFieldListener(adapter);
		fClassPathList.setLabelText(NewWizardMessages.BuildPathsBlock_classpath_label);  
		fClassPathList.setUpButtonIndex(0);
		fClassPathList.setDownButtonIndex(1);
		fClassPathList.setCheckAllButtonIndex(3);
		fClassPathList.setUncheckAllButtonIndex(4);		
			
		fBuildPathDialogField= new StringButtonDialogField(adapter);
		fBuildPathDialogField.setButtonLabel(NewWizardMessages.BuildPathsBlock_buildpath_button); 
		fBuildPathDialogField.setDialogFieldListener(adapter);
		fBuildPathDialogField.setLabelText(NewWizardMessages.BuildPathsBlock_buildpath_label); 

		fBuildPathStatus= new StatusInfo();
		fClassPathStatus= new StatusInfo();
		fOutputFolderStatus= new StatusInfo();
		
		fCurrJProject= null;
	}
	
	// -------- UI creation ---------
	
	public Control createControl(Composite parent) {
		fSWTWidget= parent;
		
		PixelConverter converter= new PixelConverter(parent);
		
		Composite composite= new Composite(parent, SWT.NONE);	
		composite.setFont(parent.getFont());
		
		GridLayout layout= new GridLayout();
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		layout.numColumns= 1;		
		composite.setLayout(layout);
		
		TabFolder folder= new TabFolder(composite, SWT.NONE);
		folder.setLayoutData(new GridData(GridData.FILL_BOTH));
		folder.setFont(composite.getFont());
		
		TabItem item;
        item= new TabItem(folder, SWT.NONE);
        item.setText(NewWizardMessages.BuildPathsBlock_tab_source); 
        item.setImage(JavaPluginImages.get(JavaPluginImages.IMG_OBJS_PACKFRAG_ROOT));
		
        if (fUseNewPage) {
			fSourceContainerPage= new NewSourceContainerWorkbookPage(fClassPathList, fBuildPathDialogField, fRunnableContext);
        } else {
			fSourceContainerPage= new SourceContainerWorkbookPage(fClassPathList, fBuildPathDialogField);
        }
        item.setData(fSourceContainerPage);     
        item.setControl(fSourceContainerPage.getControl(folder));
		
		IWorkbench workbench= JavaPlugin.getDefault().getWorkbench();	
		Image projectImage= workbench.getSharedImages().getImage(IDE.SharedImages.IMG_OBJ_PROJECT);
		
		fProjectsPage= new ProjectsWorkbookPage(fClassPathList, fPageContainer);		
		item= new TabItem(folder, SWT.NONE);
		item.setText(NewWizardMessages.BuildPathsBlock_tab_projects); 
		item.setImage(projectImage);
		item.setData(fProjectsPage);
		item.setControl(fProjectsPage.getControl(folder));
		
		fLibrariesPage= new LibrariesWorkbookPage(fClassPathList, fPageContainer);		
		item= new TabItem(folder, SWT.NONE);
		item.setText(NewWizardMessages.BuildPathsBlock_tab_libraries); 
		item.setImage(JavaPluginImages.get(JavaPluginImages.IMG_OBJS_LIBRARY));
		item.setData(fLibrariesPage);
		item.setControl(fLibrariesPage.getControl(folder));
		
		// a non shared image
		Image cpoImage= JavaPluginImages.DESC_TOOL_CLASSPATH_ORDER.createImage();
		composite.addDisposeListener(new ImageDisposer(cpoImage));	
		
		ClasspathOrderingWorkbookPage ordpage= new ClasspathOrderingWorkbookPage(fClassPathList);		
		item= new TabItem(folder, SWT.NONE);
		item.setText(NewWizardMessages.BuildPathsBlock_tab_order); 
		item.setImage(cpoImage);
		item.setData(ordpage);
		item.setControl(ordpage.getControl(folder));
				
		if (fCurrJProject != null) {
			fSourceContainerPage.init(fCurrJProject);
			fLibrariesPage.init(fCurrJProject);
			fProjectsPage.init(fCurrJProject);
		}		
						
		Composite editorcomp= new Composite(composite, SWT.NONE);	
	
		DialogField[] editors= new DialogField[] { fBuildPathDialogField };
		LayoutUtil.doDefaultLayout(editorcomp, editors, true, 0, 0);
		
		int maxFieldWidth= converter.convertWidthInCharsToPixels(40);
		LayoutUtil.setWidthHint(fBuildPathDialogField.getTextControl(null), maxFieldWidth);
		LayoutUtil.setHorizontalGrabbing(fBuildPathDialogField.getTextControl(null));
	
		editorcomp.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
		
		folder.setSelection(fPageIndex);
		fCurrPage= (BuildPathBasePage) folder.getItem(fPageIndex).getData();
		folder.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				tabChanged(e.item);
			}	
		});
		fTabFolder= folder;

		PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, IJavaHelpContextIds.BUILD_PATH_BLOCK);				
		Dialog.applyDialogFont(composite);
		return composite;
	}
	
	private Shell getShell() {
		if (fSWTWidget != null) {
			return fSWTWidget.getShell();
		}
		return JavaPlugin.getActiveWorkbenchShell();
	}
	
	/**
	 * Initializes the classpath for the given project. Multiple calls to init are allowed,
	 * but all existing settings will be cleared and replace by the given or default paths.
	 * @param jproject The java project to configure. Does not have to exist.
	 * @param outputLocation The output location to be set in the page. If <code>null</code>
	 * is passed, jdt default settings are used, or - if the project is an existing Java project- the
	 * output location of the existing project 
	 * @param classpathEntries The classpath entries to be set in the page. If <code>null</code>
	 * is passed, jdt default settings are used, or - if the project is an existing Java project - the
	 * classpath entries of the existing project
	 */	
	public void init(IJavaProject jproject, IPath outputLocation, IClasspathEntry[] classpathEntries) {
		fCurrJProject= jproject;
		boolean projectExists= false;
		List newClassPath= null;
		IProject project= fCurrJProject.getProject();
		projectExists= (project.exists() && project.getFile(".classpath").exists()); //$NON-NLS-1$
		if  (projectExists) {
			if (outputLocation == null) {
				outputLocation=  fCurrJProject.readOutputLocation();
			}
			if (classpathEntries == null) {
				classpathEntries=  fCurrJProject.readRawClasspath();
			}
		}
		if (outputLocation == null) {
			outputLocation= getDefaultBuildPath(jproject);
		}			

		if (classpathEntries != null) {
			newClassPath= getExistingEntries(classpathEntries);
		}
		if (newClassPath == null) {
			newClassPath= getDefaultClassPath(jproject);
		}
		
		List exportedEntries = new ArrayList();
		for (int i= 0; i < newClassPath.size(); i++) {
			CPListElement curr= (CPListElement) newClassPath.get(i);
			if (curr.isExported() || curr.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				exportedEntries.add(curr);
			}
		}
		
		// inits the dialog field
		fBuildPathDialogField.setText(outputLocation.makeRelative().toString());
		fBuildPathDialogField.enableButton(project.exists());
		fClassPathList.setElements(newClassPath);
		fClassPathList.setCheckedElements(exportedEntries);
		
		initializeTimeStamps();
		updateUI();
	}
	
	protected void updateUI() {
		if (fSWTWidget == null || fSWTWidget.isDisposed()) {
			return;
		}
		
		if (Display.getCurrent() != null) {
			doUpdateUI();
		} else {
			Display.getDefault().asyncExec(new Runnable() {
				public void run() {
					if (fSWTWidget == null || fSWTWidget.isDisposed()) {
						return;
					}
					doUpdateUI();
				}
			});
		}
	}

	protected void doUpdateUI() {
		fBuildPathDialogField.refresh();
		fClassPathList.refresh();
	
		if (fSourceContainerPage != null) {
			fSourceContainerPage.init(fCurrJProject);
			fProjectsPage.init(fCurrJProject);
			fLibrariesPage.init(fCurrJProject);
		}
		doStatusLineUpdate();
	}
	
	private String getEncodedSettings() {
		StringBuffer buf= new StringBuffer();	
		CPListElement.appendEncodePath(fOutputLocationPath, buf).append(';');

		int nElements= fClassPathList.getSize();
		buf.append('[').append(nElements).append(']');
		for (int i= 0; i < nElements; i++) {
			CPListElement elem= (CPListElement) fClassPathList.getElement(i);
			elem.appendEncodedSettings(buf);
		}
		return buf.toString();
	}
	
	public boolean hasChangesInDialog() {
		String currSettings= getEncodedSettings();
		return !currSettings.equals(fUserSettingsTimeStamp);
	}
	
	public boolean hasChangesInClasspathFile() {
		IFile file= fCurrJProject.getProject().getFile(".classpath"); //$NON-NLS-1$
		return fFileTimeStamp != file.getModificationStamp();
	}
	
	public void initializeTimeStamps() {
		IFile file= fCurrJProject.getProject().getFile(".classpath"); //$NON-NLS-1$
		fFileTimeStamp= file.getModificationStamp();
		fUserSettingsTimeStamp= getEncodedSettings();
	}
	
	

	private ArrayList getExistingEntries(IClasspathEntry[] classpathEntries) {
		ArrayList newClassPath= new ArrayList();
		for (int i= 0; i < classpathEntries.length; i++) {
			IClasspathEntry curr= classpathEntries[i];
			newClassPath.add(CPListElement.createFromExisting(curr, fCurrJProject));
		}
		return newClassPath;
	}
	
	// -------- public api --------
	
	/**
	 * @return Returns the Java project. Can return <code>null<code> if the page has not
	 * been initialized.
	 */
	public IJavaProject getJavaProject() {
		return fCurrJProject;
	}
	
	/**
	 *  @return Returns the current output location. Note that the path returned must not be valid.
	 */	
	public IPath getOutputLocation() {
		return new Path(fBuildPathDialogField.getText()).makeAbsolute();
	}
	
	/**
	 *  @return Returns the current class path (raw). Note that the entries returned must not be valid.
	 */	
	public IClasspathEntry[] getRawClassPath() {
		List elements=  fClassPathList.getElements();
		int nElements= elements.size();
		IClasspathEntry[] entries= new IClasspathEntry[elements.size()];

		for (int i= 0; i < nElements; i++) {
			CPListElement currElement= (CPListElement) elements.get(i);
			entries[i]= currElement.getClasspathEntry();
		}
		return entries;
	}
	
	public int getPageIndex() {
		return fPageIndex;
	}
	
	
	// -------- evaluate default settings --------
	
	private List getDefaultClassPath(IJavaProject jproj) {
		List list= new ArrayList();
		IResource srcFolder;
		IPreferenceStore store= PreferenceConstants.getPreferenceStore();
		String sourceFolderName= store.getString(PreferenceConstants.SRCBIN_SRCNAME);
		if (store.getBoolean(PreferenceConstants.SRCBIN_FOLDERS_IN_NEWPROJ) && sourceFolderName.length() > 0) {
			srcFolder= jproj.getProject().getFolder(sourceFolderName);
		} else {
			srcFolder= jproj.getProject();
		}

		list.add(new CPListElement(jproj, IClasspathEntry.CPE_SOURCE, srcFolder.getFullPath(), srcFolder));

		IClasspathEntry[] jreEntries= PreferenceConstants.getDefaultJRELibrary();
		list.addAll(getExistingEntries(jreEntries));
		return list;
	}
	
	private IPath getDefaultBuildPath(IJavaProject jproj) {
		IPreferenceStore store= PreferenceConstants.getPreferenceStore();
		if (store.getBoolean(PreferenceConstants.SRCBIN_FOLDERS_IN_NEWPROJ)) {
			String outputLocationName= store.getString(PreferenceConstants.SRCBIN_BINNAME);
			return jproj.getProject().getFullPath().append(outputLocationName);
		} else {
			return jproj.getProject().getFullPath();
		}
	}	
		
	private class BuildPathAdapter implements IStringButtonAdapter, IDialogFieldListener {

		// -------- IStringButtonAdapter --------
		public void changeControlPressed(DialogField field) {
			buildPathChangeControlPressed(field);
		}
		
		// ---------- IDialogFieldListener --------
		public void dialogFieldChanged(DialogField field) {
			buildPathDialogFieldChanged(field);
		}
	}
	
	private void buildPathChangeControlPressed(DialogField field) {
		if (field == fBuildPathDialogField) {
			IContainer container= chooseContainer();
			if (container != null) {
				fBuildPathDialogField.setText(container.getFullPath().toString());
			}
		}
	}
	
	private void buildPathDialogFieldChanged(DialogField field) {
		if (field == fClassPathList) {
			updateClassPathStatus();
		} else if (field == fBuildPathDialogField) {
			updateOutputLocationStatus();
		}
		doStatusLineUpdate();
	}	
	

	
	// -------- verification -------------------------------
	
	private void doStatusLineUpdate() {
		if (Display.getCurrent() != null) {
			IStatus res= findMostSevereStatus();
			fContext.statusChanged(res);
		}
	}
	
	private IStatus findMostSevereStatus() {
		return StatusUtil.getMostSevere(new IStatus[] { fClassPathStatus, fOutputFolderStatus, fBuildPathStatus });
	}
	
	
	/**
	 * Validates the build path.
	 */
	public void updateClassPathStatus() {
		fClassPathStatus.setOK();
		
		List elements= fClassPathList.getElements();
	
		CPListElement entryMissing= null;
		int nEntriesMissing= 0;
		IClasspathEntry[] entries= new IClasspathEntry[elements.size()];

		for (int i= elements.size()-1 ; i >= 0 ; i--) {
			CPListElement currElement= (CPListElement)elements.get(i);
			boolean isChecked= fClassPathList.isChecked(currElement);
			if (currElement.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				if (!isChecked) {
					fClassPathList.setCheckedWithoutUpdate(currElement, true);
				}
			} else {
				currElement.setExported(isChecked);
			}

			entries[i]= currElement.getClasspathEntry();
			if (currElement.isMissing()) {
				nEntriesMissing++;
				if (entryMissing == null) {
					entryMissing= currElement;
				}
			}
		}
				
		if (nEntriesMissing > 0) {
			if (nEntriesMissing == 1) {
				fClassPathStatus.setWarning(Messages.format(NewWizardMessages.BuildPathsBlock_warning_EntryMissing, entryMissing.getPath().toString())); 
			} else {
				fClassPathStatus.setWarning(Messages.format(NewWizardMessages.BuildPathsBlock_warning_EntriesMissing, String.valueOf(nEntriesMissing))); 
			}
		}
				
/*		if (fCurrJProject.hasClasspathCycle(entries)) {
			fClassPathStatus.setWarning(NewWizardMessages.getString("BuildPathsBlock.warning.CycleInClassPath")); //$NON-NLS-1$
		}
*/		
		updateBuildPathStatus();
	}

	/**
	 * Validates output location & build path.
	 */	
	private void updateOutputLocationStatus() {
		fOutputLocationPath= null;
		
		String text= fBuildPathDialogField.getText();
		if ("".equals(text)) { //$NON-NLS-1$
			fOutputFolderStatus.setError(NewWizardMessages.BuildPathsBlock_error_EnterBuildPath); 
			return;
		}
		IPath path= getOutputLocation();
		fOutputLocationPath= path;
		
		IResource res= fWorkspaceRoot.findMember(path);
		if (res != null) {
			// if exists, must be a folder or project
			if (res.getType() == IResource.FILE) {
				fOutputFolderStatus.setError(NewWizardMessages.BuildPathsBlock_error_InvalidBuildPath); 
				return;
			}
		}	
		fOutputFolderStatus.setOK();
		updateBuildPathStatus();
	}
		
	private void updateBuildPathStatus() {
		List elements= fClassPathList.getElements();
		IClasspathEntry[] entries= new IClasspathEntry[elements.size()];
	
		for (int i= elements.size()-1 ; i >= 0 ; i--) {
			CPListElement currElement= (CPListElement)elements.get(i);
			entries[i]= currElement.getClasspathEntry();
		}
		
		IJavaModelStatus status= JavaConventions.validateClasspath(fCurrJProject, entries, fOutputLocationPath);
		if (!status.isOK()) {
			fBuildPathStatus.setError(status.getMessage());
			return;
		}
		fBuildPathStatus.setOK();
	}
	
	// -------- creation -------------------------------
	
	public static void createProject(IProject project, IPath locationPath, IProgressMonitor monitor) throws CoreException {
		if (monitor == null) {
			monitor= new NullProgressMonitor();
		}				
		monitor.beginTask(NewWizardMessages.BuildPathsBlock_operationdesc_project, 10); 

		// create the project
		try {
			if (!project.exists()) {
				IProjectDescription desc= project.getWorkspace().newProjectDescription(project.getName());
				if (Platform.getLocation().equals(locationPath)) {
					locationPath= null;
				}
				desc.setLocation(locationPath);
				project.create(desc, monitor);
				monitor= null;
			}
			if (!project.isOpen()) {
				project.open(monitor);
				monitor= null;
			}
		} finally {
			if (monitor != null) {
				monitor.done();
			}
		}
	}

	public static void addJavaNature(IProject project, IProgressMonitor monitor) throws CoreException {
		if (monitor != null && monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
		if (!project.hasNature(JavaCore.NATURE_ID)) {
			IProjectDescription description = project.getDescription();
			String[] prevNatures= description.getNatureIds();
			String[] newNatures= new String[prevNatures.length + 1];
			System.arraycopy(prevNatures, 0, newNatures, 0, prevNatures.length);
			newNatures[prevNatures.length]= JavaCore.NATURE_ID;
			description.setNatureIds(newNatures);
			project.setDescription(description, monitor);
		} else {
			monitor.worked(1);
		}
	}
	
	public void configureJavaProject(IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		if (monitor == null) {
			monitor= new NullProgressMonitor();
		}
		monitor.setTaskName(NewWizardMessages.BuildPathsBlock_operationdesc_java); 
		monitor.beginTask("", 10); //$NON-NLS-1$
		try {
			IRemoveOldBinariesQuery reorgQuery= getRemoveOldBinariesQuery(null);
			// remove old .class files
			if (reorgQuery != null) {
				IPath oldOutputLocation= fCurrJProject.getOutputLocation();
				if (!getOutputLocation().equals(oldOutputLocation)) {
					IResource res= fWorkspaceRoot.findMember(oldOutputLocation);
					if (res instanceof IContainer && hasClassfiles(res)) {
						if (reorgQuery.doQuery(oldOutputLocation)) {
							removeOldClassfiles(res);
						}
					}
				}		
			}
			
			internalConfigureJavaProject(fClassPathList.getElements(), getOutputLocation(), reorgQuery, monitor);
		} finally {
			monitor.done();
		}
		updateUI();
	}
    	
	/*
	 * Creates the Java project and sets the configured build path and output location.
	 * If the project already exists only build paths are updated.
	 */
	private void internalConfigureJavaProject(List classPathEntries, IPath outputLocation, IRemoveOldBinariesQuery reorgQuery, IProgressMonitor monitor) throws CoreException, OperationCanceledException {
		// 10 monitor steps to go
		
		// create and set the output path first
		if (!fWorkspaceRoot.exists(outputLocation)) {
			IFolder folder= fWorkspaceRoot.getFolder(outputLocation);
			CoreUtility.createFolder(folder, true, true, null);
			folder.setDerived(true);		
		}
		

		if (monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
		monitor.worked(2);
				
		int nEntries= classPathEntries.size();
		IClasspathEntry[] classpath= new IClasspathEntry[nEntries];
		
		// create and set the class path
		for (int i= 0; i < nEntries; i++) {
			CPListElement entry= ((CPListElement)classPathEntries.get(i));
			IResource res= entry.getResource();
			if ((res instanceof IFolder) && !res.exists()) {
				CoreUtility.createFolder((IFolder)res, true, true, null);
			}
			if (entry.getEntryKind() == IClasspathEntry.CPE_SOURCE) {
				IPath folderOutput= (IPath) entry.getAttribute(CPListElement.OUTPUT);
				if (folderOutput != null && folderOutput.segmentCount() > 1) {
					IFolder folder= fWorkspaceRoot.getFolder(folderOutput);
					CoreUtility.createFolder(folder, true, true, null);
				}
			}
			
			classpath[i]= entry.getClasspathEntry();
		}	
		
		if (monitor.isCanceled()) {
			throw new OperationCanceledException();
		}
		monitor.worked(1);
				
		fCurrJProject.setRawClasspath(classpath, outputLocation, new SubProgressMonitor(monitor, 7));
		initializeTimeStamps();
	}
	
	public static boolean hasClassfiles(IResource resource) throws CoreException {
		if (resource.isDerived()) { //$NON-NLS-1$
			return true;
		}		
		if (resource instanceof IContainer) {
			IResource[] members= ((IContainer) resource).members();
			for (int i= 0; i < members.length; i++) {
				if (hasClassfiles(members[i])) {
					return true;
				}
			}
		}
		return false;
	}
	

	public static void removeOldClassfiles(IResource resource) throws CoreException {
		if (resource.isDerived()) {
			resource.delete(false, null);
		} else if (resource instanceof IContainer) {
			IResource[] members= ((IContainer) resource).members();
			for (int i= 0; i < members.length; i++) {
				removeOldClassfiles(members[i]);
			}
		}
	}
	
	public static IRemoveOldBinariesQuery getRemoveOldBinariesQuery(final Shell shell) {
		return new IRemoveOldBinariesQuery() {
			public boolean doQuery(final IPath oldOutputLocation) throws OperationCanceledException {
				final int[] res= new int[] { 1 };
				Display.getDefault().syncExec(new Runnable() {
					public void run() {
						Shell sh= shell != null ? shell : JavaPlugin.getActiveWorkbenchShell();
						String title= NewWizardMessages.BuildPathsBlock_RemoveBinariesDialog_title; 
						String message= Messages.format(NewWizardMessages.BuildPathsBlock_RemoveBinariesDialog_description, oldOutputLocation.toString()); 
						MessageDialog dialog= new MessageDialog(sh, title, null, message, MessageDialog.QUESTION, new String[] { IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL, IDialogConstants.CANCEL_LABEL }, 0);
						res[0]= dialog.open();
					}
				});
				if (res[0] == 0) {
					return true;
				} else if (res[0] == 1) {
					return false;
				}
				throw new OperationCanceledException();
			}
		};
	}	

	
	// ---------- util method ------------
		
	private IContainer chooseContainer() {
		Class[] acceptedClasses= new Class[] { IProject.class, IFolder.class };
		ISelectionStatusValidator validator= new TypedElementSelectionValidator(acceptedClasses, false);
		IProject[] allProjects= fWorkspaceRoot.getProjects();
		ArrayList rejectedElements= new ArrayList(allProjects.length);
		IProject currProject= fCurrJProject.getProject();
		for (int i= 0; i < allProjects.length; i++) {
			if (!allProjects[i].equals(currProject)) {
				rejectedElements.add(allProjects[i]);
			}
		}
		ViewerFilter filter= new TypedViewerFilter(acceptedClasses, rejectedElements.toArray());

		ILabelProvider lp= new WorkbenchLabelProvider();
		ITreeContentProvider cp= new WorkbenchContentProvider();

		IResource initSelection= null;
		if (fOutputLocationPath != null) {
			initSelection= fWorkspaceRoot.findMember(fOutputLocationPath);
		}
		
		FolderSelectionDialog dialog= new FolderSelectionDialog(getShell(), lp, cp);
		dialog.setTitle(NewWizardMessages.BuildPathsBlock_ChooseOutputFolderDialog_title); 
		dialog.setValidator(validator);
		dialog.setMessage(NewWizardMessages.BuildPathsBlock_ChooseOutputFolderDialog_description); 
		dialog.addFilter(filter);
		dialog.setInput(fWorkspaceRoot);
		dialog.setInitialSelection(initSelection);
		dialog.setSorter(new ResourceSorter(ResourceSorter.NAME));
		
		if (dialog.open() == Window.OK) {
			return (IContainer)dialog.getFirstResult();
		}
		return null;
	}
	
	// -------- tab switching ----------
	
	private void tabChanged(Widget widget) {
		if (widget instanceof TabItem) {
			TabItem tabItem= (TabItem) widget;
			BuildPathBasePage newPage= (BuildPathBasePage) tabItem.getData();
			if (fCurrPage != null) {
				List selection= fCurrPage.getSelection();
				if (!selection.isEmpty()) {
					newPage.setSelection(selection, false);
				}
			}
			fCurrPage= newPage;
			fPageIndex= tabItem.getParent().getSelectionIndex();
		}
	}
	
	private int getPageIndex(int entryKind) {
		switch (entryKind) {
			case IClasspathEntry.CPE_CONTAINER:
			case IClasspathEntry.CPE_LIBRARY:
			case IClasspathEntry.CPE_VARIABLE:
				return 2;
			case IClasspathEntry.CPE_PROJECT:
				return 1;
			case IClasspathEntry.CPE_SOURCE:
				return 0;
		}
		return 0;
	}
	
	private CPListElement findElement(IClasspathEntry entry) {
		for (int i= 0, len= fClassPathList.getSize(); i < len; i++) {
			CPListElement curr= (CPListElement) fClassPathList.getElement(i);
			if (curr.getEntryKind() == entry.getEntryKind() && curr.getPath().equals(entry.getPath())) {
				return curr;
			}
		}
		return null;
	}
	
	public void setElementToReveal(IClasspathEntry entry, String attributeKey) {
		int pageIndex= getPageIndex(entry.getEntryKind());
		if (fTabFolder == null) {
			fPageIndex= pageIndex;
		} else {
			fTabFolder.setSelection(pageIndex);
			CPListElement element= findElement(entry);
			if (element != null) {
				Object elementToSelect= element;
				
				if (attributeKey != null) {
					Object attrib= element.findAttributeElement(attributeKey);
					if (attrib != null) {
						elementToSelect= attrib;
					}
				}
				BuildPathBasePage page= (BuildPathBasePage) fTabFolder.getItem(pageIndex).getData();
				List selection= new ArrayList(1);
				selection.add(elementToSelect);
				page.setSelection(selection, true);
			}	
		}
	}
}