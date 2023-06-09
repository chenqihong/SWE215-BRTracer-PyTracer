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
package org.eclipse.update.internal.ui.wizards;
import java.io.*;
import java.util.*;

import org.eclipse.core.runtime.*;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.*;
import org.eclipse.swt.events.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.update.configuration.*;
import org.eclipse.update.core.*;
import org.eclipse.update.internal.operations.*;
import org.eclipse.update.internal.ui.*;
import org.eclipse.update.internal.ui.parts.*;
import org.eclipse.update.operations.*;

public class TargetSiteDialog extends Dialog {
	private TableViewer siteViewer;
	private IInstallConfiguration config;
    //private Label installLocation;
	private Button addButton;
	private Button deleteButton;
	private IInstallConfigurationChangedListener listener;
    //private IConfiguredSite targetSite;
    private IInstallFeatureOperation job;
    
    class SitesContentProvider
		extends DefaultContentProvider
		implements IStructuredContentProvider {

		public Object[] getElements(Object parent) {
			return config.getConfiguredSites();
		}
	}
	
	class SitesLabelProvider
	extends LabelProvider
	implements ITableLabelProvider {
		
		public Image getColumnImage(Object obj, int col) {
			UpdateLabelProvider provider = UpdateUI.getDefault().getLabelProvider();
			return provider.getLocalSiteImage((IConfiguredSite) obj);
		}
	
		public String getColumnText(Object obj, int col) {
			if (col == 0) {
				ISite site = ((IConfiguredSite) obj).getSite();
				return new File(site.getURL().getFile()).toString();
			}
			return null;
		}
	}


	/**
	 * Constructor for ReviewPage2
	 */
	public TargetSiteDialog(Shell parentShell, IInstallConfiguration config, IInstallFeatureOperation job, IInstallConfigurationChangedListener listener) {
        super(parentShell);
		this.config = config;
		UpdateUI.getDefault().getLabelProvider().connect(this);
		this.job = job;
        this.listener = listener;
        config.addInstallConfigurationChangedListener(listener);
	}

	public boolean close() {
		UpdateUI.getDefault().getLabelProvider().disconnect(this);
		config.removeInstallConfigurationChangedListener(listener);
		return super.close();
	}

    public Control createDialogArea(Composite parent) {
    	Composite client = new Composite(parent, SWT.NULL);
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		layout.marginWidth = layout.marginHeight = 10;
		client.setLayout(layout);
		client.setLayoutData(new GridData(GridData.FILL_BOTH));

		Composite leftPanel = new Composite(client, SWT.NULL);
		GridLayout centerLayout = new GridLayout();
		centerLayout.numColumns = 1;
		centerLayout.marginWidth = centerLayout.marginHeight = 0;
		leftPanel.setLayout(centerLayout);
		leftPanel.setLayoutData(new GridData(GridData.FILL_BOTH));
        
		Label label = new Label(leftPanel, SWT.NULL);
		label.setText(UpdateUIMessages.InstallWizard_TargetPage_siteLabel); 
		createSiteViewer(leftPanel);

		Composite rightPanel = new Composite(client, SWT.NULL);
		GridLayout rightLayout = new GridLayout();
		rightLayout.numColumns = 1;
		rightLayout.marginWidth = rightLayout.marginHeight = 0;
		rightPanel.setLayout(rightLayout);
		rightPanel.setLayoutData(new GridData(GridData.FILL_VERTICAL));

		new Label(rightPanel, SWT.NULL);
		Composite buttonContainer = new Composite(rightPanel, SWT.NULL);
		GridLayout blayout = new GridLayout();
		blayout.marginWidth = blayout.marginHeight = 0;
		buttonContainer.setLayout(blayout);
		buttonContainer.setLayoutData(new GridData(GridData.FILL_VERTICAL));
		
		addButton = new Button(buttonContainer, SWT.PUSH);
		addButton.setText(UpdateUIMessages.InstallWizard_TargetPage_new); 
		addButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				addTargetLocation();
			}
		});

		addButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
		SWTUtil.setButtonDimensionHint(addButton);
		
		deleteButton = new Button(buttonContainer, SWT.PUSH);
		deleteButton.setText(UpdateUIMessages.InstallWizard_TargetPage_delete); 
		deleteButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				try {
					removeSelection();
				}
				catch (CoreException ex) {
					UpdateUI.logException(ex);
				}
			}
		});
		deleteButton.setEnabled(false);
		deleteButton.setLayoutData(new GridData(GridData.HORIZONTAL_ALIGN_FILL));
		SWTUtil.setButtonDimensionHint(deleteButton);		

        PlatformUI.getWorkbench().getHelpSystem().setHelp(client, "org.eclipse.update.ui.MultiTargetPage2"); //$NON-NLS-1$
		Dialog.applyDialogFont(parent);
        
        siteViewer.setInput(job);
        IConfiguredSite affinitySite = UpdateUtils.getDefaultTargetSite(config, job, true);
        if (job.getTargetSite() != null) 
          siteViewer.setSelection(new StructuredSelection(job.getTargetSite()));
        addButton.setEnabled(affinitySite == null);
        
        return client;
	}

	private void createSiteViewer(Composite parent) {
		siteViewer = new TableViewer(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.BORDER);
		GridData gd = new GridData(GridData.FILL_BOTH);
		gd.widthHint = 200;
		siteViewer.getTable().setLayoutData(gd);
		siteViewer.setContentProvider(new SitesContentProvider());
		siteViewer.setLabelProvider(new SitesLabelProvider());
		siteViewer.addFilter(new ViewerFilter() {
			public boolean select(Viewer v, Object parent, Object obj) {
				IInstallFeatureOperation job = (IInstallFeatureOperation) siteViewer.getInput();
				return getSiteVisibility((IConfiguredSite) obj, job);
			}
		});
		siteViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				IStructuredSelection ssel = (IStructuredSelection) event.getSelection();
				selectTargetSite(ssel);
				updateDeleteButton(ssel);
			}
		});
	}
	
    protected void okPressed() {
        super.okPressed();
    }
    
	private void updateDeleteButton(IStructuredSelection selection) {
		boolean enable = TargetPage.added != null && !TargetPage.added.isEmpty();
		if (enable) {
			for (Iterator iter = selection.iterator(); iter.hasNext();) {
				if (!TargetPage.added.contains(iter.next())) {
					enable = false;
					break;
				}
			}
		}
		deleteButton.setEnabled(enable);
	}


	private void selectTargetSite(IStructuredSelection selection) {
		IConfiguredSite site = (IConfiguredSite) selection.getFirstElement();
		if (site != null)
			job.setTargetSite(site);
	}

	private void addTargetLocation() {
		DirectoryDialog dd = new DirectoryDialog(getShell());
		dd.setMessage(UpdateUIMessages.InstallWizard_TargetPage_location_message); 
		String path = dd.open();
		if (path != null) {
			addConfiguredSite(getShell(), config, new File(path));
		}
	}
	
	private void removeSelection() throws CoreException {
		IStructuredSelection selection = (IStructuredSelection) siteViewer.getSelection();
		for (Iterator iter = selection.iterator(); iter.hasNext();) {
			IConfiguredSite targetSite = (IConfiguredSite) iter.next();
			config.removeConfiguredSite(targetSite);

            if (TargetPage.added != null)
                TargetPage.added.remove(targetSite);
            siteViewer.remove(targetSite);
		}

//        siteViewer.getControl().setFocus();
	}

	private IConfiguredSite addConfiguredSite(
		Shell shell,
		IInstallConfiguration config,
		File file) {
		try {
			IConfiguredSite csite = config.createConfiguredSite(file);
			IStatus status = csite.verifyUpdatableStatus();
			if (status.isOK())
				config.addConfiguredSite(csite);
			else 
				throw new CoreException(status);
			
            if (TargetPage.added == null)
                TargetPage.added = new HashSet();
            TargetPage.added.add(csite);
            
            siteViewer.add(csite);         
            siteViewer.setSelection(new StructuredSelection(csite));
            siteViewer.getControl().setFocus();
            
			return csite;
		} catch (CoreException e) {
			String title = UpdateUIMessages.InstallWizard_TargetPage_location_error_title; 
			ErrorDialog.openError(shell, title, null, e.getStatus());
			UpdateUI.logException(e,false);
			return null;
		}
	}
	
	private  boolean getSiteVisibility(IConfiguredSite site, IInstallFeatureOperation job) {
		// Do not allow installing into a non-updateable site
		if (!site.isUpdatable())
			return false;
		
		// If affinity site is known, only it should be shown
		IConfiguredSite affinitySite = UpdateUtils.getAffinitySite(config, job.getFeature());
		if (affinitySite != null) {
			// Must compare referenced sites because
			// configured sites themselves may come from 
			// different configurations
			return site.getSite().equals(affinitySite.getSite());
		}
		
		// Co-locate updates with the old feature
		if (job.getOldFeature() != null) {
			IConfiguredSite oldSite = UpdateUtils.getSiteWithFeature(config, job.getOldFeature().getVersionedIdentifier().getIdentifier());
			return (site == oldSite);
		}

		// Allow installing into any site that is updateable and there is no affinity specified
		return true;
	}
}
