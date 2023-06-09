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

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.wizard.IWizardPage;
import org.eclipse.pde.core.build.IBuildEntry;
import org.eclipse.pde.internal.PDE;
import org.eclipse.pde.internal.build.IBuildPropertiesConstants;
import org.eclipse.pde.internal.core.build.WorkspaceBuildModel;
import org.eclipse.pde.internal.core.feature.FeatureImport;
import org.eclipse.pde.internal.core.feature.WorkspaceFeatureModel;
import org.eclipse.pde.internal.core.ifeature.IFeature;
import org.eclipse.pde.internal.core.ifeature.IFeatureImport;
import org.eclipse.pde.internal.core.ifeature.IFeatureInfo;
import org.eclipse.pde.internal.core.ifeature.IFeatureInstallHandler;
import org.eclipse.pde.internal.core.ifeature.IFeatureModel;
import org.eclipse.pde.internal.core.util.CoreUtility;
import org.eclipse.pde.internal.ui.IHelpContextIds;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEPluginImages;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.pde.internal.ui.wizards.IProjectProvider;
import org.eclipse.pde.internal.ui.wizards.NewWizard;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.WorkspaceModifyOperation;
import org.eclipse.ui.dialogs.WizardNewProjectCreationPage;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.part.ISetSelectionTarget;
import org.eclipse.ui.wizards.newresource.BasicNewProjectResourceWizard;

public class NewFeaturePatchWizard extends NewWizard implements IExecutableExtension {

	public static final String DEF_PROJECT_NAME = "project-name"; //$NON-NLS-1$
	public static final String DEF_FEATURE_ID = "feature-id"; //$NON-NLS-1$
	public static final String DEF_FEATURE_NAME = "feature-name"; //$NON-NLS-1$
	private WizardNewProjectCreationPage mainPage;
	private PatchSpecPage specPage;
	private IConfigurationElement config;
	private FeaturePatchProvider provider;

	public class FeaturePatchProvider implements IProjectProvider {

		public FeaturePatchProvider() {
			super();
		}

		public String getProjectName() {
			return mainPage.getProjectName();
		}

		public IProject getProject() {
			return mainPage.getProjectHandle();
		}

		public IPath getLocationPath() {
			return mainPage.getLocationPath();
		}

		public IFeatureModel getFeatureToPatch() {
			if (specPage != null)
				return specPage.getFeatureToPatch();
			return null;
		}

		public FeatureData getFeatureData() {
			return specPage.getFeatureData();
		}

	}

	public NewFeaturePatchWizard() {
		super();
		setDefaultPageImageDescriptor(PDEPluginImages.DESC_NEWFTRPTCH_WIZ);
		setDialogSettings(PDEPlugin.getDefault().getDialogSettings());
		setNeedsProgressMonitor(true);
		setWindowTitle(PDEUIMessages.FeaturePatch_wtitle);
	}

	public void addPages() {
		mainPage = new WizardNewProjectCreationPage("main") { //$NON-NLS-1$
			public void createControl(Composite parent) {
				super.createControl(parent);
				PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(), IHelpContextIds.NEW_PATCH_MAIN);
			}
		};
		mainPage.setTitle(PDEUIMessages.FeaturePatch_MainPage_title);
		mainPage.setDescription(PDEUIMessages.FeaturePatch_MainPage_desc);
		String pname = getDefaultValue(DEF_PROJECT_NAME);
		if (pname != null)
			mainPage.setInitialProjectName(pname);
		addPage(mainPage);
		provider = new FeaturePatchProvider();

		specPage = new PatchSpecPage(mainPage);
		specPage.setInitialId(getDefaultValue(DEF_FEATURE_ID));
		specPage.setInitialName(getDefaultValue(DEF_FEATURE_NAME));
		addPage(specPage);
	}

	public boolean canFinish() {
		IWizardPage page = getContainer().getCurrentPage();
		return ((page == specPage && page.isPageComplete()));
	}

	public boolean performFinish() {
		final IProject project = provider.getProject();
		final IPath location = provider.getLocationPath();
		final IFeatureModel featureModel = provider.getFeatureToPatch();
		final FeatureData data = provider.getFeatureData();
		IRunnableWithProgress operation = new WorkspaceModifyOperation() {

			public void execute(IProgressMonitor monitor) {
				try {
					createFeatureProject(project, location, featureModel, data,
							monitor);
				} catch (CoreException e) {
					PDEPlugin.logException(e);
				} finally {
					monitor.done();
				}
			}
		};
		try {
			getContainer().run(false, true, operation);
			BasicNewProjectResourceWizard.updatePerspective(config);
		} catch (InvocationTargetException e) {
			PDEPlugin.logException(e);
			return false;
		} catch (InterruptedException e) {
			return false;
		}
		return true;

	}

	public void setInitializationData(IConfigurationElement config, String property,
			Object data) throws CoreException {
		this.config = config;
	}

	/* finish methods */

	private void createFeatureProject(IProject project, IPath location, IFeatureModel featureModel, FeatureData data,
			IProgressMonitor monitor) throws CoreException {

		monitor.beginTask(PDEUIMessages.NewFeatureWizard_creatingProject, 3);
		boolean overwrite = true;
		if (location.append(project.getName()).toFile().exists()) {
			overwrite = MessageDialog.openQuestion(PDEPlugin.getActiveWorkbenchShell(),
					PDEUIMessages.FeaturePatch_wtitle, PDEUIMessages.NewFeatureWizard_overwriteFeature);
		}
		if (overwrite) {
			CoreUtility.createProject(project, location, monitor);
			project.open(monitor);
			IProjectDescription desc = project.getWorkspace().newProjectDescription(
					project.getName());
			desc.setLocation(provider.getLocationPath());
			if (!project.hasNature(PDE.FEATURE_NATURE))
				CoreUtility.addNatureToProject(project, PDE.FEATURE_NATURE, monitor);

			if (!project.hasNature(JavaCore.NATURE_ID) && data.hasCustomHandler()) {
				CoreUtility.addNatureToProject(project, JavaCore.NATURE_ID, monitor);
				JavaCore.create(project).setOutputLocation(
						project.getFullPath().append(data.getJavaBuildFolderName()),
						monitor);
				JavaCore.create(project).setRawClasspath(
						new IClasspathEntry[]{
								JavaCore.newContainerEntry(new Path(
										JavaRuntime.JRE_CONTAINER)),
								JavaCore.newSourceEntry(project.getFullPath().append(
										data.getSourceFolderName()))}, monitor);
				addSourceFolder(data.getSourceFolderName(), project, monitor);
			}

			monitor.subTask(PDEUIMessages.NewFeatureWizard_creatingManifest);
			monitor.worked(1);
			createBuildProperties(project, data);
			monitor.worked(1);
			// create feature.xml
			IFile file = createFeatureManifest(project, featureModel, data);
			monitor.worked(1);
			// open manifest for editing
			openFeatureManifest(file);
		} else {
			project.create(monitor);
			project.open(monitor);
			IFile featureFile = project.getFile("feature.xml"); //$NON-NLS-1$
			if (featureFile.exists())
				openFeatureManifest(featureFile);
			monitor.worked(3);
		}

	}

	protected static void addSourceFolder(String name, IProject project,
			IProgressMonitor monitor) throws CoreException {
		IPath path = project.getFullPath().append(name);
		ensureFolderExists(project, path, monitor);
		monitor.worked(1);
	}

	private void createBuildProperties(IProject project, FeatureData data)
			throws CoreException {
		String fileName = "build.properties"; //$NON-NLS-1$
		IPath path = project.getFullPath().append(fileName);
		IFile file = project.getWorkspace().getRoot().getFile(path);
		if (!file.exists()) {
			WorkspaceBuildModel model = new WorkspaceBuildModel(file);
			IBuildEntry ientry = model.getFactory().createEntry("bin.includes"); //$NON-NLS-1$
			ientry.addToken("feature.xml"); //$NON-NLS-1$
			String library = specPage.getInstallHandlerLibrary();
			if (library != null) {
				String source = data.getSourceFolderName();
				if (source != null) {
					IBuildEntry entry = model.getFactory().createEntry(
							IBuildEntry.JAR_PREFIX + library);
					if (!source.endsWith("/")) //$NON-NLS-1$
						source += "/"; //$NON-NLS-1$
					entry.addToken(source);
					ientry.addToken(library);
					model.getBuild().add(entry);
				}
				String output = data.getJavaBuildFolderName();
				if (output != null) {
					IBuildEntry entry = model.getFactory().createEntry(
							IBuildPropertiesConstants.PROPERTY_OUTPUT_PREFIX + library);
					if (!output.endsWith("/")) //$NON-NLS-1$
						output += "/"; //$NON-NLS-1$
					entry.addToken(output);
					model.getBuild().add(entry);
				}
			}

			model.getBuild().add(ientry);
			model.save();
		}
		IDE.setDefaultEditor(file, PDEPlugin.BUILD_EDITOR_ID);
	}

	private IFile createFeatureManifest(IProject project,
			IFeatureModel featureModel, FeatureData data) throws CoreException {
		IFile file = project.getFile("feature.xml"); //$NON-NLS-1$
		WorkspaceFeatureModel model = new WorkspaceFeatureModel();
		model.setFile(file);
		IFeature feature = model.getFeature();
		feature.setLabel(data.name);
		feature.setId(data.id);
		feature.setVersion("1.0.0"); //$NON-NLS-1$
		feature.setProviderName(data.provider);
		if(data.hasCustomHandler){
			feature.setInstallHandler(model.getFactory().createInstallHandler());
		}

		FeatureImport featureImport = (FeatureImport) model.getFactory().createImport();
		if (featureModel != null){
		    featureImport.loadFrom(featureModel.getFeature());
		    featureImport.setPatch(true);
		    featureImport.setVersion(featureModel.getFeature().getVersion());
		    featureImport.setId(featureModel.getFeature().getId());
		} else if (data.isPatch()){
			featureImport.setType(IFeatureImport.FEATURE);
		    featureImport.setPatch(true);
		    featureImport.setVersion(data.featureToPatchVersion);
		    featureImport.setId(data.featureToPatchId);
		}
		
		feature.addImports(new IFeatureImport[]{featureImport});
		IFeatureInstallHandler handler = feature.getInstallHandler();
		if (handler != null) {
			handler.setLibrary(specPage.getInstallHandlerLibrary());
		}

		IFeatureInfo info = model.getFactory().createInfo(IFeature.INFO_COPYRIGHT);
		feature.setFeatureInfo(info, IFeature.INFO_COPYRIGHT);

		info.setURL("http://www.example.com/copyright"); //$NON-NLS-1$
		info.setDescription(PDEUIMessages.NewFeatureWizard_sampleCopyrightDesc); //$NON-NLS-1$

		info = model.getFactory().createInfo(IFeature.INFO_LICENSE);
		feature.setFeatureInfo(info, IFeature.INFO_LICENSE);

		info.setURL("http://www.example.com/license"); //$NON-NLS-1$
		info.setDescription(PDEUIMessages.NewFeatureWizard_sampleLicenseDesc); //$NON-NLS-1$

		info = model.getFactory().createInfo(IFeature.INFO_DESCRIPTION);
		feature.setFeatureInfo(info, IFeature.INFO_DESCRIPTION);

		info.setURL("http://www.example.com/description"); //$NON-NLS-1$
		info.setDescription(PDEUIMessages.NewFeatureWizard_sampleDescriptionDesc); //$NON-NLS-1$

		// Save the model
		model.save();
		model.dispose();
		IDE.setDefaultEditor(file, PDEPlugin.FEATURE_EDITOR_ID);
		return file;
	}

	private void openFeatureManifest(IFile manifestFile) {
		IWorkbenchPage page = PDEPlugin.getActivePage();
		// Reveal the file first
		final ISelection selection = new StructuredSelection(manifestFile);
		final IWorkbenchPart activePart = page.getActivePart();

		if (activePart instanceof ISetSelectionTarget) {
			getShell().getDisplay().asyncExec(new Runnable() {

				public void run() {
					((ISetSelectionTarget) activePart).selectReveal(selection);
				}
			});
		}
		// Open the editor

		FileEditorInput input = new FileEditorInput(manifestFile);
		String id = PDEPlugin.FEATURE_EDITOR_ID;
		try {
			page.openEditor(input, id);
		} catch (PartInitException e) {
			PDEPlugin.logException(e);
		}
	}

	private static void ensureFolderExists(IProject project, IPath folderPath,
			IProgressMonitor monitor) throws CoreException {
		IWorkspace workspace = project.getWorkspace();

		for (int i = 1; i <= folderPath.segmentCount(); i++) {
			IPath partialPath = folderPath.uptoSegment(i);
			if (!workspace.getRoot().exists(partialPath)) {
				IFolder folder = workspace.getRoot().getFolder(partialPath);
				folder.create(true, true, null);
			}
			monitor.worked(1);
		}

	}
}
