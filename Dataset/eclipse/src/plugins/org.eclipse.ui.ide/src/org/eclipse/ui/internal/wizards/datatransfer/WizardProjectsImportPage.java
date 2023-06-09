/*******************************************************************************
 * Copyright (c) 2004, 2005 IBM Corporation and others.
 * Copyright (c) 2005 Red Hat, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Red Hat, Inc - extensive changes to allow importing of Archive Files
 *******************************************************************************/
package org.eclipse.ui.internal.wizards.datatransfer;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.dialogs.IOverwriteQuery;
import org.eclipse.ui.internal.ide.IDEWorkbenchMessages;
import org.eclipse.ui.internal.ide.IDEWorkbenchPlugin;
import org.eclipse.ui.wizards.datatransfer.IImportStructureProvider;
import org.eclipse.ui.wizards.datatransfer.ImportOperation;

/**
 * The WizardProjectsImportPage is the page that allows the user to import
 * projects from a particular location.
 */
public class WizardProjectsImportPage extends WizardPage implements
		IOverwriteQuery {

	private class ProjectRecord {
		File projectSystemFile;

		Object projectArchiveFile;

		String projectName;

		Object parent;

		int level;

		IProjectDescription description;

		ILeveledImportStructureProvider provider;

		/**
		 * Create a record for a project based on the info in the file.
		 * 
		 * @param file
		 */
		ProjectRecord(File file) {
			projectSystemFile = file;
			setProjectName();
		}

		/**
		 * @param file
		 *            The Object representing the .project file
		 * @param parent
		 *            The parent folder of the .project file
		 * @param level
		 *            The number of levels deep in the provider the file is
		 * @param entryProvider
		 *            The provider for the archive file that contains it
		 */
		ProjectRecord(Object file, Object parent, int level,
				ILeveledImportStructureProvider entryProvider) {
			this.projectArchiveFile = file;
			this.parent = parent;
			this.level = level;
			this.provider = entryProvider;
			setProjectName();
		}

		/**
		 * Set the name of the project based on the projectFile.
		 */
		private void setProjectName() {
			IProjectDescription newDescription = null;
			try {
				if (projectArchiveFile != null) {
					InputStream stream = provider
							.getContents(projectArchiveFile);
					newDescription = IDEWorkbenchPlugin.getPluginWorkspace()
							.loadProjectDescription(stream);
					stream.close();
				} else {
					IPath path = new Path(projectSystemFile.getPath());
					newDescription = IDEWorkbenchPlugin.getPluginWorkspace()
							.loadProjectDescription(path);
				}
			} catch (CoreException e) {
				// no good couldn't get the name
			} catch (IOException e) {
				// no good couldn't get the name
			}

			if (newDescription == null) {
				this.description = null;
				projectName = ""; //$NON-NLS-1$
			} else {
				this.description = newDescription;
				projectName = this.description.getName();
			}
		}

		/**
		 * Get the name of the project
		 * 
		 * @return String
		 */
		public String getProjectName() {
			return projectName;
		}
	}

	private Text directoryPathField;

	private CheckboxTreeViewer projectsList;

	private ProjectRecord[] selectedProjects = new ProjectRecord[0];

	// Keep track of the directory that we browsed to last time
	// the wizard was invoked.
	private static String previouslyBrowsedDirectory = ""; //$NON-NLS-1$

	// Keep track of the archive that we browsed to last time
	// the wizard was invoked.
	private static String previouslyBrowsedArchive = ""; //$NON-NLS-1$

	private Button projectFromDirectoryRadio;

	private Button projectFromArchiveRadio;

	private Text archivePathField;

	private Button browseDirectoriesButton;

	private Button browseArchivesButton;

	// constant from WizardArchiveFileResourceImportPage1
	private static final String[] FILE_IMPORT_MASK = {
			"*.jar;*.zip;*.tar;*.tar.gz;*.tgz", "*.*" }; //$NON-NLS-1$ //$NON-NLS-2$

	//The last selected path to mimize searches
	private String lastPath;

	/**
	 * Creates a new project creation wizard page.
	 * 
	 */
	public WizardProjectsImportPage() {
		this("wizardExternalProjectsPage"); //$NON-NLS-1$
	}

	/**
	 * Create a new instance of the receiver.
	 * 
	 * @param pageName
	 */
	public WizardProjectsImportPage(String pageName) {
		super(pageName);
		setPageComplete(false);
		setTitle(DataTransferMessages.WizardProjectsImportPage_ImportProjectsTitle);
		setDescription(DataTransferMessages.WizardProjectsImportPage_ImportProjectsDescription);
	}

	/**
	 * Create a new instance of the receiver.
	 * 
	 * @param pageName
	 * @param title
	 * @param titleImage
	 */
	public WizardProjectsImportPage(String pageName, String title,
			ImageDescriptor titleImage) {
		super(pageName, title, titleImage);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {

		initializeDialogUnits(parent);

		Composite workArea = new Composite(parent, SWT.NONE);
		setControl(workArea);

		workArea.setLayout(new GridLayout());
		workArea.setLayoutData(new GridData(GridData.FILL_BOTH
				| GridData.GRAB_HORIZONTAL | GridData.GRAB_VERTICAL));

		createProjectsRoot(workArea);
		createProjectsList(workArea);
		Dialog.applyDialogFont(workArea);

	}

	/**
	 * Create the checkbox list for the found projects.
	 * 
	 * @param workArea
	 */
	private void createProjectsList(Composite workArea) {

		Label title = new Label(workArea, SWT.NONE);
		title
				.setText(DataTransferMessages.WizardProjectsImportPage_ProjectsListTitle);

		Composite listComposite = new Composite(workArea, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginWidth = 0;
		layout.makeColumnsEqualWidth = false;
		listComposite.setLayout(layout);

		listComposite.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL
				| GridData.GRAB_VERTICAL | GridData.FILL_BOTH));

		projectsList = new CheckboxTreeViewer(listComposite, SWT.BORDER);
		GridData listData = new GridData(GridData.GRAB_HORIZONTAL
				| GridData.GRAB_VERTICAL | GridData.FILL_BOTH);
		projectsList.getControl().setLayoutData(listData);

		projectsList.setContentProvider(new ITreeContentProvider() {

			/*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.jface.viewers.ITreeContentProvider#getChildren(java.lang.Object)
			 */
			public Object[] getChildren(Object parentElement) {
				return null;
			}

			/*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.jface.viewers.IStructuredContentProvider#getElements(java.lang.Object)
			 */
			public Object[] getElements(Object inputElement) {
				return selectedProjects;
			}

			/*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.jface.viewers.ITreeContentProvider#hasChildren(java.lang.Object)
			 */
			public boolean hasChildren(Object element) {
				return false;
			}

			/*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.jface.viewers.ITreeContentProvider#getParent(java.lang.Object)
			 */
			public Object getParent(Object element) {
				return null;
			}

			/*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.jface.viewers.IContentProvider#dispose()
			 */
			public void dispose() {

			}

			/*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.jface.viewers.IContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer,
			 *      java.lang.Object, java.lang.Object)
			 */
			public void inputChanged(Viewer viewer, Object oldInput,
					Object newInput) {
			}

		});

		projectsList.setLabelProvider(new LabelProvider() {
			/*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.jface.viewers.LabelProvider#getText(java.lang.Object)
			 */
			public String getText(Object element) {
				return ((ProjectRecord) element).getProjectName();
			}
		});

		projectsList.setInput(this);
		createSelectionButtons(listComposite);

	}

	/**
	 * Create the selection buttons in the listComposite.
	 * 
	 * @param listComposite
	 */
	private void createSelectionButtons(Composite listComposite) {
		Composite buttonsComposite = new Composite(listComposite, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		buttonsComposite.setLayout(layout);

		buttonsComposite.setLayoutData(new GridData(
				GridData.VERTICAL_ALIGN_BEGINNING));

		Button selectAll = new Button(buttonsComposite, SWT.PUSH);
		selectAll.setText(DataTransferMessages.DataTransfer_selectAll);
		selectAll.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				projectsList.setCheckedElements(selectedProjects);
			}
		});

		setButtonLayoutData(selectAll);

		Button deselectAll = new Button(buttonsComposite, SWT.PUSH);
		deselectAll.setText(DataTransferMessages.DataTransfer_deselectAll);
		deselectAll.addSelectionListener(new SelectionAdapter() {
			/*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.swt.events.SelectionAdapter#widgetSelected(org.eclipse.swt.events.SelectionEvent)
			 */
			public void widgetSelected(SelectionEvent e) {

				projectsList.setCheckedElements(new Object[0]);

			}
		});
		setButtonLayoutData(deselectAll);

		Button refresh = new Button(buttonsComposite, SWT.PUSH);
		refresh.setText(DataTransferMessages.DataTransfer_refresh);
		refresh.addSelectionListener(new SelectionAdapter() {
			/*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.swt.events.SelectionAdapter#widgetSelected(org.eclipse.swt.events.SelectionEvent)
			 */
			public void widgetSelected(SelectionEvent e) {
				if (projectFromDirectoryRadio.getSelection())
					updateProjectsList(directoryPathField.getText().trim());
				else
					updateProjectsList(archivePathField.getText().trim());
			}
		});
		setButtonLayoutData(refresh);
	}

	/**
	 * Create the area where you select the root directory for the projects.
	 * 
	 * @param workArea
	 *            Composite
	 */
	private void createProjectsRoot(Composite workArea) {

		// project specification group
		Composite projectGroup = new Composite(workArea, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.numColumns = 3;
		layout.makeColumnsEqualWidth = false;
		layout.marginWidth = 0;
		projectGroup.setLayout(layout);
		projectGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		// new project from directory radio button
		projectFromDirectoryRadio = new Button(projectGroup, SWT.RADIO);
		projectFromDirectoryRadio
				.setText(DataTransferMessages.WizardProjectsImportPage_RootSelectTitle);

		// project location entry field
		this.directoryPathField = new Text(projectGroup, SWT.BORDER);

		this.directoryPathField.setLayoutData(new GridData(
				GridData.FILL_HORIZONTAL | GridData.GRAB_HORIZONTAL));

		// browse button
		browseDirectoriesButton = new Button(projectGroup, SWT.PUSH);
		browseDirectoriesButton
				.setText(DataTransferMessages.DataTransfer_browse);
		setButtonLayoutData(browseDirectoriesButton);

		// new project from archive radio button
		projectFromArchiveRadio = new Button(projectGroup, SWT.RADIO);
		projectFromArchiveRadio
				.setText(DataTransferMessages.WizardProjectsImportPage_ArchiveSelectTitle);

		// project location entry field
		archivePathField = new Text(projectGroup, SWT.BORDER);

		archivePathField.setLayoutData(new GridData(GridData.FILL_HORIZONTAL
				| GridData.GRAB_HORIZONTAL));
		// browse button
		browseArchivesButton = new Button(projectGroup, SWT.PUSH);
		browseArchivesButton.setText(DataTransferMessages.DataTransfer_browse);
		setButtonLayoutData(browseArchivesButton);

		projectFromDirectoryRadio.setSelection(true);
		archivePathField.setEnabled(false);
		browseArchivesButton.setEnabled(false);

		browseDirectoriesButton.addSelectionListener(new SelectionAdapter() {
			/*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.swt.events.SelectionAdapter#widgetS
			 *      elected(org.eclipse.swt.events.SelectionEvent)
			 */
			public void widgetSelected(SelectionEvent e) {
				handleLocationDirectoryButtonPressed();
			}

		});

		browseArchivesButton.addSelectionListener(new SelectionAdapter() {
			/*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.swt.events.SelectionAdapter#widgetSelected(org.eclipse.swt.events.SelectionEvent)
			 */
			public void widgetSelected(SelectionEvent e) {
				handleLocationArchiveButtonPressed();
			}

		});

		directoryPathField.addTraverseListener(new TraverseListener() {

			/*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.swt.events.TraverseListener#keyTraversed(org.eclipse.swt.events.TraverseEvent)
			 */
			public void keyTraversed(TraverseEvent e) {
				if (e.detail == SWT.TRAVERSE_RETURN) {
					e.doit = false;
					updateProjectsList(directoryPathField.getText().trim());
				}
			}

		});

		directoryPathField.addFocusListener(new FocusAdapter() {
			
			/*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.swt.events.FocusListener#focusLost(org.eclipse.swt.events.FocusEvent)
			 */
			public void focusLost(org.eclipse.swt.events.FocusEvent e) {
				updateProjectsList(directoryPathField.getText().trim());
			}
			
		});

		archivePathField.addTraverseListener(new TraverseListener() {

			/*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.swt.events.TraverseListener#keyTraversed(org.eclipse.swt.events.TraverseEvent)
			 */
			public void keyTraversed(TraverseEvent e) {
				if (e.detail == SWT.TRAVERSE_RETURN) {
					e.doit = false;
					updateProjectsList(archivePathField.getText().trim());
				}
			}

		});

		archivePathField.addFocusListener(new FocusAdapter() {
			/* (non-Javadoc)
			 * @see org.eclipse.swt.events.FocusListener#focusLost(org.eclipse.swt.events.FocusEvent)
			 */
			public void focusLost(org.eclipse.swt.events.FocusEvent e) {
				updateProjectsList(archivePathField.getText().trim());
			}
		});

		projectFromDirectoryRadio.addSelectionListener(new SelectionAdapter() {
			/*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.swt.events.SelectionListener#widgetSelected(org.eclipse.swt.events.SelectionEvent)
			 */
			public void widgetSelected(SelectionEvent e) {
				if (projectFromDirectoryRadio.getSelection()) {
					directoryPathField.setEnabled(true);
					browseDirectoriesButton.setEnabled(true);
					archivePathField.setEnabled(false);
					browseArchivesButton.setEnabled(false);
					updateProjectsList(directoryPathField.getText());
				}
			}
		});

		projectFromArchiveRadio.addSelectionListener(new SelectionAdapter() {
			/*
			 * (non-Javadoc)
			 * 
			 * @see org.eclipse.swt.events.SelectionListener#widgetSelected(org.eclipse.swt.events.SelectionEvent)
			 */
			public void widgetSelected(SelectionEvent e) {
				if (projectFromArchiveRadio.getSelection()) {
					directoryPathField.setEnabled(false);
					browseDirectoriesButton.setEnabled(false);
					archivePathField.setEnabled(true);
					browseArchivesButton.setEnabled(true);
					updateProjectsList(archivePathField.getText());
				}
			}
		});
	}

	/**
	 * Update the list of projects based on path
	 * 
	 * @param path
	 */
	protected void updateProjectsList(final String path) {
		
		if(path.equals(lastPath))//Do not select the same path again
			return;
		
		lastPath = path;
		
		// on an empty path empty selectedProjects
		if (path == null || path.length() == 0) {
			selectedProjects = new ProjectRecord[0];
			projectsList.refresh(true);
			projectsList.setCheckedElements(selectedProjects);
			setPageComplete(selectedProjects.length > 0);
			return;
		}
		// We can't access the radio button from the inner class so get the
		// status beforehand
		final boolean dirSelected = this.projectFromDirectoryRadio
				.getSelection();
		try {
			getContainer().run(true, true, new IRunnableWithProgress() {

				ZipLeveledStructureProvider zipCurrentProvider;

				TarLeveledStructureProvider tarCurrentProvider;

				/*
				 * (non-Javadoc)
				 * 
				 * @see org.eclipse.jface.operation.IRunnableWithProgress#run(org.eclipse.core.runtime.IProgressMonitor)
				 */
				public void run(IProgressMonitor monitor) {

					monitor
							.beginTask(
									DataTransferMessages.WizardProjectsImportPage_SearchingMessage,
									100);
					File directory = new File(path);
					selectedProjects = new ProjectRecord[0];
					Collection files = new ArrayList();
					monitor.worked(10);
					if (!dirSelected
							&& ArchiveFileManipulations.isTarFile(path)) {
						TarFile sourceTarFile = getSpecifiedTarSourceFile(path);
						if (sourceTarFile == null) {
							// Clear out the provider as well
							this.zipCurrentProvider = null;
							this.tarCurrentProvider = null;
							return;
						}

						TarLeveledStructureProvider provider = ArchiveFileManipulations
								.getTarStructureProvider(sourceTarFile,
										getContainer().getShell());
						this.tarCurrentProvider = provider;
						this.zipCurrentProvider = null;
						Object child = provider.getRoot();

						if (!collectProjectFilesFromProvider(files, provider,
								child, 0, monitor))
							return;
						Iterator filesIterator = files.iterator();
						selectedProjects = new ProjectRecord[files.size()];
						int index = 0;
						monitor.worked(50);
						monitor
								.subTask(DataTransferMessages.WizardProjectsImportPage_ProcessingMessage);
						while (filesIterator.hasNext())
							selectedProjects[index++] = (ProjectRecord) filesIterator
									.next();
					} else if (!dirSelected
							&& ArchiveFileManipulations.isZipFile(path)) {
						ZipFile sourceFile = getSpecifiedZipSourceFile(path);
						if (sourceFile == null) {
							// Clear out the provider as well
							this.zipCurrentProvider = null;
							this.tarCurrentProvider = null;
							return;
						}
						ZipLeveledStructureProvider provider = ArchiveFileManipulations
								.getZipStructureProvider(sourceFile,
										getContainer().getShell());

						this.zipCurrentProvider = provider;
						this.tarCurrentProvider = null;
						Object child = provider.getRoot();

						if (!collectProjectFilesFromProvider(files, provider,
								child, 0, monitor))
							return;
						Iterator filesIterator = files.iterator();
						selectedProjects = new ProjectRecord[files.size()];
						int index = 0;
						monitor.worked(50);
						monitor
								.subTask(DataTransferMessages.WizardProjectsImportPage_ProcessingMessage);
						while (filesIterator.hasNext())
							selectedProjects[index++] = (ProjectRecord) filesIterator
									.next();
					}

					else if (dirSelected && directory.isDirectory()) {

						if (!collectProjectFilesFromDirectory(files, directory,
								monitor))
							return;
						Iterator filesIterator = files.iterator();
						selectedProjects = new ProjectRecord[files.size()];
						int index = 0;
						monitor.worked(50);
						monitor
								.subTask(DataTransferMessages.WizardProjectsImportPage_ProcessingMessage);
						while (filesIterator.hasNext()) {
							File file = (File) filesIterator.next();
							selectedProjects[index] = new ProjectRecord(file);
							index++;
						}
					} else
						monitor.worked(60);
					monitor.done();
				}

			});
		} catch (InvocationTargetException e) {
			IDEWorkbenchPlugin.log(e.getMessage(), e);
		} catch (InterruptedException e) {
			// Nothing to do if the user interrupts.
		}

		projectsList.refresh(true);
		projectsList.setCheckedElements(selectedProjects);
		setPageComplete(selectedProjects.length > 0);
	}

	/**
	 * Answer a handle to the zip file currently specified as being the source.
	 * Return null if this file does not exist or is not of valid format.
	 */
	protected ZipFile getSpecifiedZipSourceFile() {
		return getSpecifiedZipSourceFile(archivePathField.getText());
	}

	/**
	 * Answer a handle to the zip file currently specified as being the source.
	 * Return null if this file does not exist or is not of valid format.
	 */
	private ZipFile getSpecifiedZipSourceFile(String fileName) {
		if (fileName.length() == 0)
			return null;

		try {
			return new ZipFile(fileName);
		} catch (ZipException e) {
			displayErrorDialog(DataTransferMessages.ZipImport_badFormat);
		} catch (IOException e) {
			displayErrorDialog(DataTransferMessages.ZipImport_couldNotRead);
		}

		archivePathField.setFocus();
		return null;
	}

	/**
	 * Answer a handle to the zip file currently specified as being the source.
	 * Return null if this file does not exist or is not of valid format.
	 */
	protected TarFile getSpecifiedTarSourceFile() {
		return getSpecifiedTarSourceFile(archivePathField.getText());
	}

	/**
	 * Answer a handle to the zip file currently specified as being the source.
	 * Return null if this file does not exist or is not of valid format.
	 */
	private TarFile getSpecifiedTarSourceFile(String fileName) {
		if (fileName.length() == 0)
			return null;

		try {
			return new TarFile(fileName);
		} catch (TarException e) {
			displayErrorDialog(DataTransferMessages.TarImport_badFormat);
		} catch (IOException e) {
			displayErrorDialog(DataTransferMessages.ZipImport_couldNotRead);
		}

		archivePathField.setFocus();
		return null;
	}

	/**
	 * Display an error dialog with the specified message.
	 * 
	 * @param message
	 *            the error message
	 */
	protected void displayErrorDialog(String message) {
		MessageDialog.openError(getContainer().getShell(),
				getErrorDialogTitle(), message); //$NON-NLS-1$
	}

	/**
	 * Get the title for an error dialog. Subclasses should override.
	 */
	protected String getErrorDialogTitle() {
		return IDEWorkbenchMessages.WizardExportPage_internalErrorTitle;
	}

	/**
	 * Collect the list of .project files that are under directory into files.
	 * 
	 * @param files
	 * @param directory
	 * @param monitor
	 *            The monitor to report to
	 * @return boolean <code>true</code> if the operation was completed.
	 */
	private boolean collectProjectFilesFromDirectory(Collection files,
			File directory, IProgressMonitor monitor) {

		if (monitor.isCanceled())
			return false;
		monitor.subTask(NLS.bind(
				DataTransferMessages.WizardProjectsImportPage_CheckingMessage,
				directory.getPath()));
		File[] contents = directory.listFiles();
		// first look for project description files
		final String dotProject = IProjectDescription.DESCRIPTION_FILE_NAME;
		for (int i = 0; i < contents.length; i++) {
			File file = contents[i];
			if (file.isFile() && file.getName().equals(dotProject)) {
				files.add(file);
				// don't search sub-directories since we can't have nested
				// projects
				return true;
			}
		}
		// no project description found, so recurse into sub-directories
		for (int i = 0; i < contents.length; i++) {
			if (contents[i].isDirectory())
				collectProjectFilesFromDirectory(files, contents[i], monitor);
		}
		return true;
	}

	/**
	 * Collect the list of .project files that are under directory into files.
	 * 
	 * @param files
	 * @param monitor
	 *            The monitor to report to
	 * @return boolean <code>true</code> if the operation was completed.
	 */
	private boolean collectProjectFilesFromProvider(Collection files,
			ILeveledImportStructureProvider provider, Object entry, int level,
			IProgressMonitor monitor) {

		if (monitor.isCanceled())
			return false;
		monitor.subTask(NLS.bind(
				DataTransferMessages.WizardProjectsImportPage_CheckingMessage,
				provider.getLabel(entry)));
		List children = provider.getChildren(entry); //$NON-NLS-1$
		if (children == null) {
			children = new ArrayList(1);
		}
		Iterator childrenEnum = children.iterator();
		while (childrenEnum.hasNext()) {
			Object child = childrenEnum.next();
			if (provider.isFolder(child))
				collectProjectFilesFromProvider(files, provider, child,
						level + 1, monitor);
			String elementLabel = provider.getLabel(child);
			if (elementLabel.equals(IProjectDescription.DESCRIPTION_FILE_NAME))
				files.add(new ProjectRecord(child, entry, level, provider));
		}
		return true;
	}

	/**
	 * The browse button has been selected. Select the location.
	 */
	protected void handleLocationDirectoryButtonPressed() {

		DirectoryDialog dialog = new DirectoryDialog(directoryPathField
				.getShell());
		dialog
				.setMessage(DataTransferMessages.WizardProjectsImportPage_SelectDialogTitle);

		String dirName = directoryPathField.getText().trim();
		if (dirName.length() == 0)
			dirName = previouslyBrowsedDirectory;

		if (dirName.length() == 0)
			dialog.setFilterPath(IDEWorkbenchPlugin.getPluginWorkspace()
					.getRoot().getLocation().toOSString());
		else {
			File path = new File(dirName);
			if (path.exists())
				dialog.setFilterPath(new Path(dirName).toOSString());
		}

		String selectedDirectory = dialog.open();
		if (selectedDirectory != null) {
			previouslyBrowsedDirectory = selectedDirectory;
			directoryPathField.setText(previouslyBrowsedDirectory);
			updateProjectsList(selectedDirectory);
		}

	}

	/**
	 * The browse button has been selected. Select the location.
	 */
	protected void handleLocationArchiveButtonPressed() {

		FileDialog dialog = new FileDialog(archivePathField.getShell());
		dialog.setFilterExtensions(FILE_IMPORT_MASK);
		dialog
				.setText(DataTransferMessages.WizardProjectsImportPage_SelectArchiveDialogTitle);

		String fileName = archivePathField.getText().trim();
		if (fileName.length() == 0)
			fileName = previouslyBrowsedArchive;

		if (fileName.length() == 0)
			dialog.setFilterPath(IDEWorkbenchPlugin.getPluginWorkspace()
					.getRoot().getLocation().toOSString());
		else {
			File path = new File(fileName);
			if (path.exists())
				dialog.setFilterPath(new Path(fileName).toOSString());
		}

		String selectedArchive = dialog.open();
		if (selectedArchive != null) {
			previouslyBrowsedArchive = selectedArchive;
			archivePathField.setText(previouslyBrowsedArchive);
			updateProjectsList(selectedArchive);
		}

	}

	/**
	 * Create the selected projects
	 * 
	 * @return boolean <code>true</code> if all project creations were
	 *         successful.
	 */
	public boolean createProjects() {
		// create the new project operation
		Object[] selected = projectsList.getCheckedElements();
		for (int i = 0; i < selected.length; i++) {
			ProjectRecord record = (ProjectRecord) selected[i];
			if (!createExistingProject(record))
				return false;
		}
		return true;
	}

	/**
	 * Create the project described in record. If it is successful return true.
	 * 
	 * @param record
	 * @return boolean <code>true</code> of successult
	 */
	private boolean createExistingProject(final ProjectRecord record) {

		String projectName = record.getProjectName();
		final IWorkspace workspace = ResourcesPlugin.getWorkspace();
		final IProject project = workspace.getRoot().getProject(projectName);
		if (record.description == null) {
			record.description = workspace.newProjectDescription(projectName);
			IPath locationPath = new Path(record.projectSystemFile
					.getAbsolutePath());
			// IPath locationPath = new
			// Path(record.projectFile.getFullPath(record.projectFile.getRoot()));

			// If it is under the root use the default location
			if (Platform.getLocation().isPrefixOf(locationPath))
				record.description.setLocation(null);
			else
				record.description.setLocation(locationPath);
		} else
			record.description.setName(projectName);
		if (record.projectArchiveFile != null) {
			ArrayList fileSystemObjects = new ArrayList();
			getFilesForProject(fileSystemObjects, record.provider,
					record.parent);
			record.provider.setStrip(record.level);
			ImportOperation operation = new ImportOperation(project
					.getFullPath(), record.provider.getRoot(), record.provider,
					this, fileSystemObjects);
			operation.setContext(getShell());
			return executeImportOperation(operation);
		}
		WorkspaceModifyOperation op = new WorkspaceModifyOperation() {
			protected void execute(IProgressMonitor monitor)
					throws CoreException {
				monitor.beginTask("", 2000); //$NON-NLS-1$
				project.create(record.description, new SubProgressMonitor(
						monitor, 1000));
				if (monitor.isCanceled())
					throw new OperationCanceledException();
				project.open(IResource.BACKGROUND_REFRESH,
						new SubProgressMonitor(monitor, 1000));
			}
		};
		// run the new project creation operation
		try {
			getContainer().run(true, true, op);
		} catch (InterruptedException e) {
			return false;
		} catch (InvocationTargetException e) {
			// ie.- one of the steps resulted in a core exception
			Throwable t = e.getTargetException();
			if (((CoreException) t).getStatus().getCode() == IResourceStatus.CASE_VARIANT_EXISTS) {
				MessageDialog.openError(getShell(), 
						DataTransferMessages.WizardExternalProjectImportPage_errorMessage,
						NLS.bind(
								DataTransferMessages.WizardExternalProjectImportPage_caseVariantExistsError,
								record.description.getName())
				);
			} else {
				ErrorDialog.openError(getShell(), 
						DataTransferMessages.WizardExternalProjectImportPage_errorMessage,
						((CoreException) t).getLocalizedMessage(), 
						((CoreException) t).getStatus());
			}
			return false;
		}
		return true;
	}

	/**
	 * Return a list of all files in the project
	 * 
	 * @param provider
	 *            The provider for the parent file
	 * @param entry
	 *            The root directory of the project
	 * @return A list of all files in the project
	 */
	protected boolean getFilesForProject(Collection files,
			IImportStructureProvider provider, Object entry) {
		List children = provider.getChildren(entry);
		Iterator childrenEnum = children.iterator();

		while (childrenEnum.hasNext()) {
			Object child = childrenEnum.next();
			// Add the child, this way we get every files except the project
			// folder itself which we don't want
			files.add(child);
			// We don't have isDirectory for tar so must check for children
			// instead
			if (provider.isFolder(child))
				getFilesForProject(files, provider, child);
		}
		return true;
	}

	/**
	 * Execute the passed import operation. Answer a boolean indicating success.
	 */
	protected boolean executeImportOperation(ImportOperation op) {
		// initializeOperation(op);
		try {
			getContainer().run(true, true, op);
		} catch (InterruptedException e) {
			return false;
		} catch (InvocationTargetException e) {
			// displayErrorDialog(e.getTargetException());
			return false;
		}

		IStatus status = op.getStatus();
		if (!status.isOK()) {
			ErrorDialog.openError(getContainer().getShell(),
					DataTransferMessages.FileImport_importProblems, null, // no
					// special
					// message
					status);
			return false;
		}

		return true;
	}

	/**
	 * The <code>WizardDataTransfer</code> implementation of this
	 * <code>IOverwriteQuery</code> method asks the user whether the existing
	 * resource at the given path should be overwritten.
	 * 
	 * @param pathString
	 * @return the user's reply: one of <code>"YES"</code>, <code>"NO"</code>,
	 *         <code>"ALL"</code>, or <code>"CANCEL"</code>
	 */
	public String queryOverwrite(String pathString) {

		Path path = new Path(pathString);

		String messageString;
		// Break the message up if there is a file name and a directory
		// and there are at least 2 segments.
		if (path.getFileExtension() == null || path.segmentCount() < 2)
			messageString = NLS.bind(
					IDEWorkbenchMessages.WizardDataTransfer_existsQuestion,
					pathString);

		else
			messageString = NLS
					.bind(
							IDEWorkbenchMessages.WizardDataTransfer_overwriteNameAndPathQuestion,
							path.lastSegment(), path.removeLastSegments(1)
									.toOSString());

		final MessageDialog dialog = new MessageDialog(getContainer()
				.getShell(), IDEWorkbenchMessages.Question, null,
				messageString, MessageDialog.QUESTION, new String[] {
						IDialogConstants.YES_LABEL,
						IDialogConstants.YES_TO_ALL_LABEL,
						IDialogConstants.NO_LABEL,
						IDialogConstants.NO_TO_ALL_LABEL,
						IDialogConstants.CANCEL_LABEL }, 0);
		String[] response = new String[] { YES, ALL, NO, NO_ALL, CANCEL };
		// run in syncExec because callback is from an operation,
		// which is probably not running in the UI thread.
		getControl().getDisplay().syncExec(new Runnable() {
			public void run() {
				dialog.open();
			}
		});
		return dialog.getReturnCode() < 0 ? CANCEL : response[dialog
				.getReturnCode()];
	}

}
