/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.wizards.templates;

import org.eclipse.core.runtime.*;
import org.eclipse.jface.wizard.*;
import org.eclipse.pde.core.plugin.*;
import org.eclipse.pde.internal.core.plugin.PluginBase;
import org.eclipse.pde.internal.ui.*;
import org.eclipse.pde.ui.*;
import org.eclipse.pde.ui.templates.*;


public class MailTemplate extends PDETemplateSection {
	
	public static final String KEY_PRODUCT_NAME = "productName"; //$NON-NLS-1$
	public static final String KEY_PRODUCT_ID = "productID"; //$NON-NLS-1$
	public static final String KEY_PERSPECTIVE_NAME = "perspectiveName"; //$NON-NLS-1$
	public static final String KEY_WORKBENCH_ADVISOR = "advisor"; //$NON-NLS-1$
	public static final String KEY_APPLICATION_CLASS = "applicationClass"; //$NON-NLS-1$
	public static final String KEY_APPLICATION_ID = "applicationID"; //$NON-NLS-1$
	
	public MailTemplate() {
		setPageCount(1);
		createOptions();
	}
	
	public void addPages(Wizard wizard) {
		WizardPage page = createPage(0, IHelpContextIds.TEMPLATE_RCP_MAIL);
		page.setTitle(PDEUIMessages.MailTemplate_title); //$NON-NLS-1$
		page.setDescription(PDEUIMessages.MailTemplate_desc); //$NON-NLS-1$
		wizard.addPage(page);
		markPagesAdded();
	}

	
	private void createOptions() {	
		addOption(KEY_PRODUCT_NAME, PDEUIMessages.MailTemplate_productName, "RCP Product", 0); //$NON-NLS-1$ //$NON-NLS-2$
		
		addOption(KEY_PRODUCT_ID, PDEUIMessages.MailTemplate_productID, "product", 0); //$NON-NLS-1$ //$NON-NLS-2$
		
		addOption(KEY_APPLICATION_ID, PDEUIMessages.MailTemplate_appId, "application", 0); //$NON-NLS-1$ //$NON-NLS-2$
		
		addOption(KEY_PERSPECTIVE_NAME, PDEUIMessages.MailTemplate_perspectiveName, (String)null, 0); //$NON-NLS-1$ //$NON-NLS-2$
		
		addOption(KEY_PACKAGE_NAME, PDEUIMessages.MailTemplate_packageName, (String) null, 0); //$NON-NLS-1$		

		addOption(KEY_APPLICATION_CLASS, PDEUIMessages.MailTemplate_appClass, "Application", 0); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	protected void initializeFields(IFieldData data) {
		// In a new project wizard, we don't know this yet - the
		// model has not been created
		String packageName = getFormattedPackageName(data.getId());
		initializeOption(KEY_PACKAGE_NAME, packageName);

		int index = packageName.lastIndexOf('.');
		String name = packageName.substring(index + 1) + " Perspective"; //$NON-NLS-1$
		initializeOption(KEY_PERSPECTIVE_NAME, Character.toUpperCase(name.charAt(0)) + name.substring(1));
	}
	
	public void initializeFields(IPluginModelBase model) {
		String packageName = getFormattedPackageName(model.getPluginBase().getId());
		initializeOption(KEY_PACKAGE_NAME, packageName);

		int index = packageName.lastIndexOf('.');
		String name = packageName.substring(index + 1) + " Perspective"; //$NON-NLS-1$
		initializeOption(KEY_PERSPECTIVE_NAME, Character.toUpperCase(name.charAt(0)) + name.substring(1));
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.pde.ui.templates.OptionTemplateSection#getSectionId()
	 */
	public String getSectionId() {
		return "mail"; //$NON-NLS-1$
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.ui.templates.BaseOptionTemplateSection#validateOptions(org.eclipse.pde.ui.templates.TemplateOption)
	 */
	public void validateOptions(TemplateOption source) {
		if (source.isRequired() && source.isEmpty()) {
			flagMissingRequiredOption(source);
		} else {
			validateContainerPage(source);
		}
	}
	
	private void validateContainerPage(TemplateOption source) {
		TemplateOption[] allPageOptions = getOptions(0);
		for (int i = 0; i < allPageOptions.length; i++) {
			TemplateOption nextOption = allPageOptions[i];
			if (nextOption.isRequired() && nextOption.isEmpty()) {
				flagMissingRequiredOption(nextOption);
				return;
			}
		}
		resetPageState();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.ui.templates.AbstractTemplateSection#updateModel(org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected void updateModel(IProgressMonitor monitor) throws CoreException {
		createApplicationExtension();
		createPerspectiveExtension();
		createViewExtension();
        if ("3.1".compareTo(((PluginBase) model.getPluginBase()).getTargetVersion()) <= 0) { //$NON-NLS-1$
            createCommandExtension(false);
            createBindingsExtension();
        } else {
            createCommandExtension(true);
        }
		createProductExtension();
	}
	
	private void createApplicationExtension() throws CoreException {
		IPluginBase plugin = model.getPluginBase();
		
		IPluginExtension extension = createExtension("org.eclipse.core.runtime.applications", true); //$NON-NLS-1$
		extension.setId(getStringOption(KEY_APPLICATION_ID));
		
		IPluginElement element = model.getPluginFactory().createElement(extension);
		element.setName("application"); //$NON-NLS-1$
		extension.add(element);
		
		IPluginElement run = model.getPluginFactory().createElement(element);
		run.setName("run"); //$NON-NLS-1$
		run.setAttribute("class", getStringOption(KEY_PACKAGE_NAME) + "." + getStringOption(KEY_APPLICATION_CLASS)); //$NON-NLS-1$ //$NON-NLS-2$
		element.add(run);
		
		if (!extension.isInTheModel())
			plugin.add(extension);
	}

	
	private void createPerspectiveExtension() throws CoreException {
		IPluginBase plugin = model.getPluginBase();
		
		IPluginExtension extension = createExtension("org.eclipse.ui.perspectives", true); //$NON-NLS-1$
		IPluginElement element = model.getPluginFactory().createElement(extension);
		element.setName("perspective"); //$NON-NLS-1$
		element.setAttribute("class", getStringOption(KEY_PACKAGE_NAME) + ".Perspective"); //$NON-NLS-1$ //$NON-NLS-2$
		element.setAttribute("name", getStringOption(KEY_PERSPECTIVE_NAME)); //$NON-NLS-1$
		element.setAttribute("id", plugin.getId() + ".perspective"); //$NON-NLS-1$ //$NON-NLS-2$
		extension.add(element);
		
		if (!extension.isInTheModel())
			plugin.add(extension);
	}
	
	private void createViewExtension() throws CoreException {
		IPluginBase plugin = model.getPluginBase();
		String id = plugin.getId();
		IPluginExtension extension = createExtension("org.eclipse.ui.views", true); //$NON-NLS-1$
		
		IPluginElement view = model.getPluginFactory().createElement(extension);
		view.setName("view"); //$NON-NLS-1$
		view.setAttribute("allowMultiple", "true"); //$NON-NLS-1$ //$NON-NLS-2$
		view.setAttribute("icon", "icons/sample2.gif"); //$NON-NLS-1$ //$NON-NLS-2$
		view.setAttribute("class", getStringOption(KEY_PACKAGE_NAME) + ".View" ); //$NON-NLS-1$ //$NON-NLS-2$
		view.setAttribute("name", "Message"); //$NON-NLS-1$ //$NON-NLS-2$
		view.setAttribute("id", id + ".view"); //$NON-NLS-1$ //$NON-NLS-2$
		extension.add(view);
		
		view = model.getPluginFactory().createElement(extension);
		view.setName("view"); //$NON-NLS-1$
		view.setAttribute("allowMultiple", "true"); //$NON-NLS-1$ //$NON-NLS-2$
		view.setAttribute("icon", "icons/sample3.gif"); //$NON-NLS-1$ //$NON-NLS-2$
		view.setAttribute("class", getStringOption(KEY_PACKAGE_NAME) + ".NavigationView" ); //$NON-NLS-1$ //$NON-NLS-2$
		view.setAttribute("name", "Mailboxes"); //$NON-NLS-1$ //$NON-NLS-2$
		view.setAttribute("id", id + ".navigationView"); //$NON-NLS-1$ //$NON-NLS-2$
		extension.add(view);
		
		if (!extension.isInTheModel())
			plugin.add(extension);
	}
	
    private void createCommandExtension(boolean generateKeyBindings) throws CoreException {
        IPluginBase plugin = model.getPluginBase(); 
        String id = plugin.getId();
        IPluginExtension extension = createExtension("org.eclipse.ui.commands", true); //$NON-NLS-1$

        IPluginElement element = model.getPluginFactory().createElement(extension);
        element.setName("category"); //$NON-NLS-1$
        element.setAttribute("id", id + ".category"); //$NON-NLS-1$ //$NON-NLS-2$
        element.setAttribute("name", "Mail"); //$NON-NLS-1$ //$NON-NLS-2$
        extension.add(element);

        element = model.getPluginFactory().createElement(extension);
        element.setName("command"); //$NON-NLS-1$
        element.setAttribute("description", "Opens a mailbox"); //$NON-NLS-1$ //$NON-NLS-2$
        element.setAttribute("name", "Open Mailbox"); //$NON-NLS-1$ //$NON-NLS-2$
        element.setAttribute("id", id + ".open"); //$NON-NLS-1$ //$NON-NLS-2$
        element.setAttribute("categoryId", id + ".category"); //$NON-NLS-1$ //$NON-NLS-2$
        extension.add(element);
        
        element = model.getPluginFactory().createElement(extension);
        element.setName("command"); //$NON-NLS-1$
        element.setAttribute("description", "Open a message dialog"); //$NON-NLS-1$ //$NON-NLS-2$
        element.setAttribute("name", "Open Message Dialog"); //$NON-NLS-1$ //$NON-NLS-2$
        element.setAttribute("id", id + ".openMessage");     //$NON-NLS-1$ //$NON-NLS-2$
        element.setAttribute("categoryId", id + ".category"); //$NON-NLS-1$ //$NON-NLS-2$
        extension.add(element);
        
        if(generateKeyBindings){
            element = model.getPluginFactory().createElement(extension);
            element.setName("keyConfiguration"); //$NON-NLS-1$
            element.setAttribute("description", "The key configuration for this sample"); //$NON-NLS-1$ //$NON-NLS-2$
            element.setAttribute("name", id + ".keyConfiguration"); //$NON-NLS-1$ //$NON-NLS-2$
            element.setAttribute("id", id + ".keyConfiguration");    //$NON-NLS-1$ //$NON-NLS-2$
            extension.add(element);
            
            element = model.getPluginFactory().createElement(extension);
            element.setName("keyBinding"); //$NON-NLS-1$
            element.setAttribute("commandId", id + ".open"); //$NON-NLS-1$ //$NON-NLS-2$
            element.setAttribute("keySequence", "CTRL+2"); //$NON-NLS-1$ //$NON-NLS-2$
            element.setAttribute("keyConfigurationId", "org.eclipse.ui.defaultAcceleratorConfiguration");    //$NON-NLS-1$ //$NON-NLS-2$
            extension.add(element);
            
            element = model.getPluginFactory().createElement(extension);
            element.setName("keyBinding"); //$NON-NLS-1$
            element.setAttribute("commandId", id + ".openMessage"); //$NON-NLS-1$ //$NON-NLS-2$
            element.setAttribute("keySequence", "CTRL+3"); //$NON-NLS-1$ //$NON-NLS-2$
            element.setAttribute("keyConfigurationId", "org.eclipse.ui.defaultAcceleratorConfiguration");    //$NON-NLS-1$ //$NON-NLS-2$
            extension.add(element);
            
            element = model.getPluginFactory().createElement(extension);
            element.setName("keyBinding"); //$NON-NLS-1$
            element.setAttribute("commandId", "org.eclipse.ui.file.exit"); //$NON-NLS-1$ //$NON-NLS-2$
            element.setAttribute("keySequence", "CTRL+X"); //$NON-NLS-1$ //$NON-NLS-2$
            element.setAttribute("keyConfigurationId", "org.eclipse.ui.defaultAcceleratorConfiguration");    //$NON-NLS-1$ //$NON-NLS-2$
            extension.add(element);
        }
        
        if (!extension.isInTheModel())
            plugin.add(extension);
    }
    
    private void createBindingsExtension() throws CoreException {
        IPluginBase plugin = model.getPluginBase(); 
        String id = plugin.getId();
        IPluginExtension extension = createExtension("org.eclipse.ui.bindings", true); //$NON-NLS-1$

        IPluginElement element = model.getPluginFactory().createElement(extension);
        element.setName("key"); //$NON-NLS-1$
        element.setAttribute("commandId", id + ".open"); //$NON-NLS-1$ //$NON-NLS-2$
        element.setAttribute("sequence", "CTRL+2"); //$NON-NLS-1$ //$NON-NLS-2$
        element.setAttribute("schemeId", "org.eclipse.ui.defaultAcceleratorConfiguration");    //$NON-NLS-1$ //$NON-NLS-2$
        extension.add(element);
        
        element = model.getPluginFactory().createElement(extension);
        element.setName("key"); //$NON-NLS-1$
        element.setAttribute("commandId", id + ".openMessage"); //$NON-NLS-1$ //$NON-NLS-2$
        element.setAttribute("sequence", "CTRL+3"); //$NON-NLS-1$ //$NON-NLS-2$
        element.setAttribute("schemeId", "org.eclipse.ui.defaultAcceleratorConfiguration");    //$NON-NLS-1$ //$NON-NLS-2$
        extension.add(element);
        
        element = model.getPluginFactory().createElement(extension);
        element.setName("key"); //$NON-NLS-1$
        element.setAttribute("commandId", "org.eclipse.ui.file.exit"); //$NON-NLS-1$ //$NON-NLS-2$
        element.setAttribute("sequence", "CTRL+X"); //$NON-NLS-1$ //$NON-NLS-2$
        element.setAttribute("schemeId", "org.eclipse.ui.defaultAcceleratorConfiguration");    //$NON-NLS-1$ //$NON-NLS-2$
        extension.add(element);
        
        if (!extension.isInTheModel())
            plugin.add(extension);
    }
    
	private void createProductExtension() throws CoreException {
		IPluginBase plugin = model.getPluginBase();
		IPluginExtension extension = createExtension("org.eclipse.core.runtime.products", true); //$NON-NLS-1$
		extension.setId(getStringOption(KEY_PRODUCT_ID));
		
		IPluginElement element = model.getFactory().createElement(extension);
		element.setName("product"); //$NON-NLS-1$
		element.setAttribute("name", getStringOption(KEY_PRODUCT_NAME)); //$NON-NLS-1$
		element.setAttribute("application", plugin.getId() + "." + getStringOption(KEY_APPLICATION_ID)); //$NON-NLS-1$ //$NON-NLS-2$

		IPluginElement property = model.getFactory().createElement(element);
		property.setName("property"); //$NON-NLS-1$
		property.setAttribute("name", "aboutText"); //$NON-NLS-1$ //$NON-NLS-2$
		property.setAttribute("value", "%aboutText"); //$NON-NLS-1$ //$NON-NLS-2$
		element.add(property);
		
		property = model.getFactory().createElement(element);
		property.setName("property"); //$NON-NLS-1$
		property.setAttribute("name", "windowImages"); //$NON-NLS-1$ //$NON-NLS-2$
		property.setAttribute("value", "icons/sample2.gif"); //$NON-NLS-1$ //$NON-NLS-2$
		element.add(property);

		property = model.getFactory().createElement(element);
		property.setName("property"); //$NON-NLS-1$
		property.setAttribute("name", "aboutImage"); //$NON-NLS-1$ //$NON-NLS-2$
		property.setAttribute("value", "product_lg.gif"); //$NON-NLS-1$ //$NON-NLS-2$
		element.add(property);
		
		extension.add(element);
		
		if (!extension.isInTheModel()) {
			plugin.add(extension);
		}

	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.ui.templates.ITemplateSection#getUsedExtensionPoint()
	 */
	public String getUsedExtensionPoint() {
		return null;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.ui.templates.BaseOptionTemplateSection#isDependentOnParentWizard()
	 */
	public boolean isDependentOnParentWizard() {
		return true;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.ui.templates.AbstractTemplateSection#getNumberOfWorkUnits()
	 */
	public int getNumberOfWorkUnits() {
		return super.getNumberOfWorkUnits() + 1;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.ui.templates.AbstractTemplateSection#getDependencies(java.lang.String)
	 */
	public IPluginReference[] getDependencies(String schemaVersion) {
		IPluginReference[] dep = new IPluginReference[2];
		dep[0] = new PluginReference("org.eclipse.core.runtime", null, 0); //$NON-NLS-1$
		dep[1] = new PluginReference("org.eclipse.ui", null, 0); //$NON-NLS-1$
		return dep;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.wizards.templates.PDETemplateSection#getNewFiles()
	 */
	public String[] getNewFiles() {
		return new String[] {"icons/", "plugin.properties", "product_lg.gif", "splash.bmp"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
	}

}