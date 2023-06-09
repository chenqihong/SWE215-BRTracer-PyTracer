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

package org.eclipse.pde.internal.ui.wizards.feature;

import org.eclipse.jface.dialogs.IMessageProvider;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.core.util.IdUtil;
import org.eclipse.pde.internal.ui.IHelpContextIds;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.WizardNewProjectCreationPage;

public class PatchSpecPage extends BaseFeatureSpecPage {

	public PatchSpecPage(WizardNewProjectCreationPage mainPage) {
		super(mainPage, true);
		setTitle(PDEUIMessages.PatchSpec_title); //$NON-NLS-1$
		setDescription(PDEUIMessages.PatchSpec_desc); //$NON-NLS-1$
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.wizards.feature.BaseFeatureSpecPage#createControl(org.eclipse.swt.widgets.Composite)
	 */
	public void createControl(Composite parent) {
		super.createControl(parent);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IHelpContextIds.NEW_PATCH_REQUIRED_DATA);
	}

	protected void initialize() {
		if (isInitialized)
			return;
		String projectName = mainPage.getProjectName();
		if (initialId == null)
			patchIdText.setText(computeInitialId(projectName));
		if (initialName == null)
			patchNameText.setText(projectName);
		setErrorMessage(null);
		super.initialize();
	}

	protected void verifyComplete() {
		String message = verifyIdRules();
		if (message != null) {
			setPageComplete(false);
			setErrorMessage(message);
			return;
		}
		message = verifyVersion();
		if (message != null) {
			setPageComplete(false);
			setErrorMessage(message);
			return;
		}
		if (customChoice.getSelection() && libraryText.getText().length() == 0) {
			setPageComplete(false);
			setErrorMessage(PDEUIMessages.NewFeatureWizard_SpecPage_error_library);
			return;
		}
		
		fFeatureToPatch = PDECore.getDefault().getFeatureModelManager()
				.findFeatureModel(featureIdText.getText(),
						featureVersionText.getText());
		if (fFeatureToPatch != null) {
			setMessage(null);
			setPageComplete(true);
			setErrorMessage(null);
			return;
		}
		
		setMessage(NLS.bind(PDEUIMessages.NewFeaturePatch_SpecPage_notFound, featureIdText.getText()), IMessageProvider.WARNING); //$NON-NLS-1$
		setErrorMessage(null);
		getContainer().updateButtons();
		return;
	}
	
	/* (non-Javadoc)
     * @see org.eclipse.jface.wizard.WizardPage#getNextPage()
     */
    public IWizardPage getNextPage() {
        if (fFeatureToPatch == null)
            return null;
        return super.getNextPage();
    }

	private String getPatchId() {
		if (patchIdText == null)
			return ""; //$NON-NLS-1$
		return patchIdText.getText();
	}

	private String getPatchName() {
		if (patchNameText == null)
			return ""; //$NON-NLS-1$
		return patchNameText.getText();
	}

	private String getPatchProvider() {
		if (patchProviderText == null)
			return ""; //$NON-NLS-1$
		return patchProviderText.getText();
	}
	
	public FeatureData getFeatureData() {
		FeatureData data = new FeatureData();
		data.id = getPatchId();
		data.version = "1.0.0"; //$NON-NLS-1$
		data.provider = getPatchProvider();
		data.name = getPatchName();
		data.library = getInstallHandlerLibrary();
		data.hasCustomHandler = customChoice.getSelection();
		data.isPatch = true;
		data.featureToPatchId = featureIdText.getText();
		data.featureToPatchVersion = featureVersionText.getText();
		return data;
	}

	protected String verifyIdRules() {
		String id = patchIdText.getText();
		if (id == null || id.length() == 0)
			return PDEUIMessages.NewFeatureWizard_SpecPage_pmissing;
		if (!IdUtil.isValidPluginId(id)) {
			return PDEUIMessages.NewFeatureWizard_SpecPage_invalidId;
		}
		return super.verifyIdRules();
	}
	
	public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (visible) {
			initialize();
			isInitialized = true;
			patchIdText.setFocus();
		}
	}
}
