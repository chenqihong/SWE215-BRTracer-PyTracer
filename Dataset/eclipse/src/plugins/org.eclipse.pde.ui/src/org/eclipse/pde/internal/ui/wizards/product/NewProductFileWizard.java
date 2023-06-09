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
package org.eclipse.pde.internal.ui.wizards.product;

import java.lang.reflect.*;

import org.eclipse.core.resources.*;
import org.eclipse.jface.operation.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.pde.internal.ui.*;
import org.eclipse.ui.*;
import org.eclipse.ui.wizards.newresource.*;


public class NewProductFileWizard extends BasicNewResourceWizard {
	
	private ProductFileWizadPage fMainPage;

	/* (non-Javadoc)
	 * @see org.eclipse.jface.wizard.Wizard#addPages()
	 */
	public void addPages() {
		fMainPage = new ProductFileWizadPage("product", getSelection()); //$NON-NLS-1$
		fMainPage.setTitle(PDEUIMessages.NewProductFileWizard_title); //$NON-NLS-1$
		addPage(fMainPage);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.wizard.IWizard#performFinish()
	 */
	public boolean performFinish() {
		try {
			getContainer().run(false, true, getOperation());
		} catch (InvocationTargetException e) {
			PDEPlugin.logException(e);
			return false;
		} catch (InterruptedException e) {
			return false;
		}
		return true;
	}
	
	private IRunnableWithProgress getOperation() {
        IFile file = fMainPage.createNewFile();
		int option = fMainPage.getInitializationOption();
		if (option == ProductFileWizadPage.USE_LAUNCH_CONFIG)
			return new ProductFromConfigOperation(file, fMainPage.getSelectedLaunchConfiguration());
		if (option == ProductFileWizadPage.USE_PRODUCT)
			return new ProductFromExtensionOperation(file, fMainPage.getSelectedProduct());
		return new BaseProductCreationOperation(file);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.wizards.newresource.BasicNewResourceWizard#init(org.eclipse.ui.IWorkbench, org.eclipse.jface.viewers.IStructuredSelection)
	 */
	public void init(IWorkbench workbench, IStructuredSelection currentSelection) {
		super.init(workbench, currentSelection);
		setWindowTitle(PDEUIMessages.NewProductFileWizard_windowTitle); //$NON-NLS-1$
		setNeedsProgressMonitor(true);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.wizards.newresource.BasicNewResourceWizard#initializeDefaultPageImageDescriptor()
	 */
	protected void initializeDefaultPageImageDescriptor() {
		setDefaultPageImageDescriptor(PDEPluginImages.DESC_PRODUCT_WIZ);
	}

}
