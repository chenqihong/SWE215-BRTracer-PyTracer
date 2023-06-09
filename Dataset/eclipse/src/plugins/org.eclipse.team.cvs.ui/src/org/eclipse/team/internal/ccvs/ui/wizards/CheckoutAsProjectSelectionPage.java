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
package org.eclipse.team.internal.ccvs.ui.wizards;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.*;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.ui.*;
import org.eclipse.ui.PlatformUI;

/**
 * This page allows the user to select the target parent container for
 * the folders being checked out.
 */
public class CheckoutAsProjectSelectionPage extends CVSWizardPage {
	
	public static final String NAME = "CheckoutAsProjectSelectionPage"; //$NON-NLS-1$
	
	private TreeViewer tree;
	private Text nameField;
	private Combo filterList;
	private Button recurseCheck;
	
	private IResource selection;
	private ICVSRemoteFolder[] remoteFolders;
	private String folderName;
	private boolean recurse;
	private int filter;

	/**
	 * Constructor for CheckoutIntoProjectSelectionPage.
	 * @param pageName
	 * @param title
	 * @param titleImage
	 */
	public CheckoutAsProjectSelectionPage(ImageDescriptor titleImage, ICVSRemoteFolder[] remoteFolders) {
		super(NAME, CVSUIMessages.CheckoutAsProjectSelectionPage_title, titleImage, CVSUIMessages.CheckoutAsProjectSelectionPage_description); //$NON-NLS-1$ //$NON-NLS-2$
		this.remoteFolders = remoteFolders;
	}

	/**
	 * @return
	 */
	private boolean isSingleFolder() {
		return remoteFolders.length == 1;
	}
	
	/*
	 * For the single folder case, return the name of the folder
	 */
	private String getInputFolderName() {
		return remoteFolders[0].getName();
	}
	
	private String getRepository() throws CVSException {
		return remoteFolders[0].getFolderSyncInfo().getRoot();
	}
	
	/**
	 * @see org.eclipse.jface.dialogs.IDialogPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		Composite composite= createComposite(parent, 2, false);
		setControl(composite);
		
        PlatformUI.getWorkbench().getHelpSystem().setHelp(composite, IHelpContextIds.CHECKOUT_PROJECT_SELECTION_PAGE);
		
		if (isSingleFolder()) {
			createLabel(composite, CVSUIMessages.CheckoutAsProjectSelectionPage_name); //$NON-NLS-1$
			nameField = createTextField(composite);
			nameField.addListener(SWT.Modify, new Listener() {
				public void handleEvent(Event event) {
					folderName = nameField.getText();
					updateWidgetEnablements();
				}
			});
		}
		
		createWrappingLabel(composite, CVSUIMessages.CheckoutAsProjectSelectionPage_treeLabel, 0, 2); //$NON-NLS-1$
		
		tree = createResourceSelectionTree(composite, IResource.PROJECT | IResource.FOLDER, 2 /* horizontal span */);
		tree.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				handleResourceSelection(event);
			}
		});

		Composite filterComposite = createComposite(composite, 2, false);
		GridData data = new GridData();
		data.verticalAlignment = GridData.FILL;
		data.horizontalAlignment = GridData.FILL;
		data.horizontalSpan = 2;
		filterComposite.setLayoutData(data);
		createLabel(filterComposite, CVSUIMessages.CheckoutAsProjectSelectionPage_showLabel); //$NON-NLS-1$
		filterList = createCombo(filterComposite);
		filterList.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				handleFilterSelection();
			}
		});
		
		createWrappingLabel(composite, "", 0, 2); //$NON-NLS-1$
				
		// Should subfolders of the folder be checked out?
		recurseCheck = createCheckBox(composite, CVSUIMessages.CheckoutAsProjectSelectionPage_recurse); //$NON-NLS-1$
		recurseCheck.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				recurse = recurseCheck.getSelection();
				updateWidgetEnablements();
			}
		});
		
		initializeValues();
		updateWidgetEnablements();
		tree.getControl().setFocus();
        Dialog.applyDialogFont(parent);
	}

	/**
	 * Method initializeValues.
	 */
	private void initializeValues() {
		if (isSingleFolder()) {
			nameField.setText(getInputFolderName());
		}
		tree.setInput(ResourcesPlugin.getWorkspace().getRoot());
		recurse = true;
		recurseCheck.setSelection(recurse);
		filter = 0;
		updateTreeContents(filter);
		filterList.add(CVSUIMessages.CheckoutAsProjectSelectionPage_showAll); //$NON-NLS-1$
		filterList.add(CVSUIMessages.CheckoutAsProjectSelectionPage_showUnshared); //$NON-NLS-1$
		filterList.add(CVSUIMessages.CheckoutAsProjectSelectionPage_showSameRepo); //$NON-NLS-1$
		filterList.select(filter);
	}

	private void handleResourceSelection(SelectionChangedEvent event) {
		ISelection sel = event.getSelection();
		if (sel.isEmpty()) {
			this.selection = null;
		} else if (sel instanceof IStructuredSelection) {
			this.selection = (IResource)((IStructuredSelection)sel).getFirstElement();
		}
		updateWidgetEnablements();
	}
	
	/**
	 * Method updateWidgetEnablement.
	 */
	private void updateWidgetEnablements() {
		if (isSingleFolder() && !Path.EMPTY.isValidSegment(folderName)) {
			setPageComplete(false);
			setErrorMessage(NLS.bind(CVSUIMessages.CheckoutAsProjectSelectionPage_invalidFolderName, new String[] { folderName })); //$NON-NLS-1$
			return;
		}
		boolean complete = selection != null && selection.getType() != IResource.FILE;
		setErrorMessage(null);
		setPageComplete(complete);
	}
	
	/**
	 * Returns the selection.
	 * @return IResource
	 */
	public IResource getSelection() {
		return selection;
	}

	private void updateTreeContents(int selected) {
		try {
			if (selected == 0) {
				tree.setInput(new AdaptableResourceList(getProjects(getRepository(), true)));
			} else if (selected == 1) {
				tree.setInput(new AdaptableResourceList(getProjects(null, true)));
			} else if (selected == 2) {
				tree.setInput(new AdaptableResourceList(getProjects(getRepository(), false)));
			}
		} catch (CVSException e) {
			CVSUIPlugin.log(e);
		}
	}
			
	/**
	 * Method getValidTargetProjects returns the set of projects that match the provided criteria.
	 * @return IResource
	 */
	private IProject[] getProjects(String root, boolean unshared) throws CVSException {
		List validTargets = new ArrayList();
		try {
			IResource[] projects = ResourcesPlugin.getWorkspace().getRoot().members();
			for (int i = 0; i < projects.length; i++) {
				IResource resource = projects[i];
				if (resource instanceof IProject) {
					IProject project = (IProject) resource;
					if (project.isAccessible()) {
						RepositoryProvider provider = RepositoryProvider.getProvider(project);
						if (provider == null && unshared) {
							validTargets.add(project);
						} else if (provider != null && provider.getID().equals(CVSProviderPlugin.getTypeId())) {
							ICVSFolder cvsFolder = CVSWorkspaceRoot.getCVSFolderFor(project);
							FolderSyncInfo info = cvsFolder.getFolderSyncInfo();
							if (root != null && info != null && root.equals(info.getRoot())) {
								validTargets.add(project);
							}
						}
					}
				}
			}
		} catch (CoreException e) {
			throw CVSException.wrapException(e);
		}
		return (IProject[]) validTargets.toArray(new IProject[validTargets.size()]);
	}
	
	public IContainer getLocalFolder() {
		if (Path.EMPTY.isValidSegment(folderName)) {
			return ((IContainer)getSelection()).getFolder(new Path(null, folderName));
		} else {
			return null;
		}
	}
	
	public IContainer getParentFolder() {
		return ((IContainer)getSelection());
	}
	
	/**
	 * Returns the recurse.
	 * @return boolean
	 */
	public boolean isRecurse() {
		return recurse;
	}
	
	private void handleFilterSelection() {
		filter = filterList.getSelectionIndex();
		updateTreeContents(filter);
	}

}
