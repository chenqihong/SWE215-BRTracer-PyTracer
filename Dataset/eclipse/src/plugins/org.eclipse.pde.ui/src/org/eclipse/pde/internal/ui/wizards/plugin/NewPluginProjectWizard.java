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
package org.eclipse.pde.internal.ui.wizards.plugin;


import java.lang.reflect.*;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.pde.internal.ui.*;
import org.eclipse.pde.internal.ui.elements.*;
import org.eclipse.pde.internal.ui.wizards.*;
import org.eclipse.pde.ui.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.ui.wizards.newresource.*;

public class NewPluginProjectWizard extends NewWizard implements IExecutableExtension {
	public static final String PLUGIN_POINT = "pluginContent"; //$NON-NLS-1$
	public static final String TAG_WIZARD = "wizard"; //$NON-NLS-1$
	public static final String DEF_PROJECT_NAME = "project_name"; //$NON-NLS-1$
	public static final String DEF_TEMPLATE_ID = "template-id"; //$NON-NLS-1$

	private IConfigurationElement fConfig;
	private PluginFieldData fPluginData;
	private IProjectProvider fProjectProvider;
	private NewProjectCreationPage fMainPage;
	private ContentPage fContentPage;
	private TemplateListSelectionPage fWizardListPage;

	public NewPluginProjectWizard() {
		setDefaultPageImageDescriptor(PDEPluginImages.DESC_NEWPPRJ_WIZ);
		setDialogSettings(PDEPlugin.getDefault().getDialogSettings());
		setWindowTitle(PDEUIMessages.NewProjectWizard_title); //$NON-NLS-1$
		setNeedsProgressMonitor(true);
		PDEPlugin.getDefault().getLabelProvider().connect(this);
		fPluginData = new PluginFieldData();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.wizard.Wizard#addPages()
	 */
	public void addPages() {
		fMainPage = new NewProjectCreationPage("main", fPluginData, false); //$NON-NLS-1$
		fMainPage.setTitle(PDEUIMessages.NewProjectWizard_MainPage_title); //$NON-NLS-1$
		fMainPage.setDescription(PDEUIMessages.NewProjectWizard_MainPage_desc); //$NON-NLS-1$
		String pname = getDefaultValue(DEF_PROJECT_NAME);
		if (pname!=null)
			fMainPage.setInitialProjectName(pname);
		addPage(fMainPage);
		
		fProjectProvider = new IProjectProvider() {
			public String getProjectName() {
				return fMainPage.getProjectName();
			}
			public IProject getProject() {
				return fMainPage.getProjectHandle();
			}
			public IPath getLocationPath() {
				return fMainPage.getLocationPath();
			}
		};
		
		fContentPage = new PluginContentPage("page2", fProjectProvider, fMainPage, fPluginData); //$NON-NLS-1$
        
		fWizardListPage = new TemplateListSelectionPage(getAvailableCodegenWizards(), fContentPage, PDEUIMessages.WizardListSelectionPage_templates); //$NON-NLS-1$
		String tid = getDefaultValue(DEF_TEMPLATE_ID);
		if (tid!=null)
			fWizardListPage.setInitialTemplateId(tid);

		addPage(fContentPage);
		addPage(fWizardListPage);
	}
	
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.wizard.Wizard#canFinish()
	 */
	public boolean canFinish() {
		IWizardPage page = getContainer().getCurrentPage();
		return super.canFinish() && page != fMainPage;
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.pde.internal.ui.wizards.NewWizard#performFinish()
	 */
	public boolean performFinish() {
		try {
			fMainPage.updateData();
			fContentPage.updateData();
			BasicNewProjectResourceWizard.updatePerspective(fConfig);
			IPluginContentWizard contentWizard = fWizardListPage.getSelectedWizard();
			getContainer().run(false, true,
					new NewProjectCreationOperation(fPluginData, fProjectProvider, contentWizard));
			return true;
		} catch (InvocationTargetException e) {
			PDEPlugin.logException(e);
		} catch (InterruptedException e) {
		}
		return false;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.wizard.Wizard#dispose()
	 */
	public void dispose() {
		super.dispose();
		PDEPlugin.getDefault().getLabelProvider().disconnect(this);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.runtime.IExecutableExtension#setInitializationData(org.eclipse.core.runtime.IConfigurationElement, java.lang.String, java.lang.Object)
	 */
	public void setInitializationData(IConfigurationElement config,
			String propertyName, Object data) throws CoreException {
		fConfig = config;
	}
	
	protected WizardElement createWizardElement(IConfigurationElement config) {
		String name = config.getAttribute(WizardElement.ATT_NAME);
		String id = config.getAttribute(WizardElement.ATT_ID);
		String className = config.getAttribute(WizardElement.ATT_CLASS);
		if (name == null || id == null || className == null)
			return null;
		WizardElement element = new WizardElement(config);
		String imageName = config.getAttribute(WizardElement.ATT_ICON);
		if (imageName != null) {
			String pluginID = config.getNamespace();
			Image image =
				PDEPlugin.getDefault().getLabelProvider().getImageFromPlugin(pluginID, imageName);
			element.setImage(image);
		}
		return element;
	}

	public ElementList getAvailableCodegenWizards() {
		ElementList wizards = new ElementList("CodegenWizards"); //$NON-NLS-1$
		IExtensionRegistry registry = Platform.getExtensionRegistry();
		IExtensionPoint point = registry.getExtensionPoint(PDEPlugin.getPluginId(), PLUGIN_POINT);
		if (point == null)
			return wizards;
		IExtension[] extensions = point.getExtensions();
		for (int i = 0; i < extensions.length; i++) {
			IConfigurationElement[] elements =
				extensions[i].getConfigurationElements();
			for (int j = 0; j < elements.length; j++) {
				if (elements[j].getName().equals(TAG_WIZARD)) {
					WizardElement element = createWizardElement(elements[j]);
					if (element != null) {
						wizards.add(element);
					}
				}
			}
		}
		return wizards;
	}
	
	public String getPluginId() {
		return fPluginData.getId();
	}
	
}
