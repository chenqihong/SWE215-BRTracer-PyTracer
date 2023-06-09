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
package org.eclipse.pde.internal.ui.wizards.site;

import java.lang.reflect.*;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.operation.*;
import org.eclipse.pde.internal.ui.*;
import org.eclipse.pde.internal.ui.wizards.*;
import org.eclipse.ui.wizards.newresource.*;

public class NewSiteProjectWizard extends NewWizard implements IExecutableExtension {

	public static final String DEF_PROJECT_NAME = "project-name"; //$NON-NLS-1$

	private NewSiteProjectCreationPage fMainPage;
	private IConfigurationElement fConfig;
	

	public NewSiteProjectWizard() {
		setDefaultPageImageDescriptor(PDEPluginImages.DESC_NEWSITEPRJ_WIZ);
		setDialogSettings(PDEPlugin.getDefault().getDialogSettings());
		setNeedsProgressMonitor(true);
		setWindowTitle(PDEUIMessages.NewSiteWizard_wtitle); //$NON-NLS-1$
	}
	
	public void addPages() {
		fMainPage = new NewSiteProjectCreationPage("main"); //$NON-NLS-1$
		fMainPage.setTitle(PDEUIMessages.NewSiteWizard_MainPage_title); //$NON-NLS-1$
		fMainPage.setDescription(PDEUIMessages.NewSiteWizard_MainPage_desc); //$NON-NLS-1$
		String pname = getDefaultValue(DEF_PROJECT_NAME);
		if (pname!=null)
			fMainPage.setInitialProjectName(pname);
		addPage(fMainPage);
	}

	
	public boolean performFinish() {
		try {
			BasicNewProjectResourceWizard.updatePerspective(fConfig);
			final IProject project = fMainPage.getProjectHandle();
			final IPath location = fMainPage.getLocationPath();
			final String webLocation = fMainPage.getWebLocation();
			IRunnableWithProgress op = new NewSiteProjectCreationOperation(getShell().getDisplay(), project, location, webLocation);
			getContainer().run(false, true, op);
		} catch (InvocationTargetException e) {
			PDEPlugin.logException(e);
			return false;
		} catch (InterruptedException e) {
			return false;
		}
		return true;
	}

	public void setInitializationData(
		IConfigurationElement config,
		String property,
		Object data)
		throws CoreException {
		this.fConfig = config;
	}
}
