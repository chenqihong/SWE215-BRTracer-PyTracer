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
package org.eclipse.pde.internal.ui.wizards.tools;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.operation.*;
import org.eclipse.jface.text.*;
import org.eclipse.jface.wizard.*;
import org.eclipse.pde.core.plugin.*;
import org.eclipse.pde.internal.ui.*;

public class MigratePluginWizard extends Wizard {
	private MigratePluginWizardPage page1;

	private IPluginModelBase[] fSelected;
	private IPluginModelBase[] fUnmigrated;

	private static final String STORE_SECTION = "MigrationWizard"; //$NON-NLS-1$

	public MigratePluginWizard(IPluginModelBase[] models,IPluginModelBase[] selected) {
		IDialogSettings masterSettings = PDEPlugin.getDefault()
				.getDialogSettings();
		setDialogSettings(getSettingsSection(masterSettings));
		setDefaultPageImageDescriptor(PDEPluginImages.DESC_MIGRATE_30_WIZ);
		setWindowTitle(PDEUIMessages.MigrationWizard_title); //$NON-NLS-1$
		setNeedsProgressMonitor(true);
		this.fSelected = selected;
		this.fUnmigrated = models;
	}

	public boolean performFinish() {
		final IPluginModelBase[] models = page1.getSelected();
		page1.storeSettings();
		final boolean doUpdateClasspath = page1.isUpdateClasspathRequested();
		final boolean doCleanProjects = page1.isCleanProjectsRequested();

		IRunnableWithProgress operation = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor)
					throws InvocationTargetException, InterruptedException {

				if (PDEPlugin.getWorkspace().validateEdit(
						getFilesToValidate(models), getContainer().getShell())
						.getSeverity() != IStatus.OK) {
					monitor.done();
					return;
				}

				int numUnits = doUpdateClasspath ? models.length * 2
						: models.length;
				monitor
						.beginTask(
								PDEUIMessages.MigrationWizard_progress, numUnits); //$NON-NLS-1$
				try {
					for (int i = 0; i < models.length; i++) {
						monitor.subTask(models[i].getPluginBase().getId());
						transform(models[i]);
						models[i].getUnderlyingResource().refreshLocal(
								IResource.DEPTH_ZERO, null);
						monitor.worked(1);
						if (doCleanProjects) {
							IProject project = models[i]
									.getUnderlyingResource().getProject();
							IProjectDescription desc = project.getDescription();
							desc.setReferencedProjects(new IProject[0]);
							project.setDescription(desc, null);
						}
						monitor.worked(1);
					}
					if (doUpdateClasspath) {
						Job j = new UpdateClasspathJob(models);
						j.setUser(true);
						j.schedule();
					}
				} catch (Exception e) {
					PDEPlugin.logException(e);
				} finally {
					monitor.done();
				}
			}
		};

		try {
			getContainer().run(true, false, operation);
		} catch (InvocationTargetException e) {
		} catch (InterruptedException e) {
		}
		return true;
	}

	private IFile[] getFilesToValidate(IPluginModelBase[] models) {
		ArrayList files = new ArrayList();
		for (int i = 0; i < models.length; i++) {
			IProject project = models[i].getUnderlyingResource().getProject();
			files.add(models[i].getUnderlyingResource());
			files.add(project.getFile(".project")); //$NON-NLS-1$
			files.add(project.getFile(".classpath")); //$NON-NLS-1$
		}
		return (IFile[]) files.toArray(new IFile[files.size()]);
	}

	private IDialogSettings getSettingsSection(IDialogSettings master) {
		IDialogSettings setting = master.getSection(STORE_SECTION);
		if (setting == null) {
			setting = master.addNewSection(STORE_SECTION);
		}
		return setting;
	}

	public void addPages() {
		page1 = new MigratePluginWizardPage(fUnmigrated, fSelected);
		addPage(page1);
	}

	private void transform(IPluginModelBase model) throws Exception {
		IResource file = model.getUnderlyingResource();
		IDocument document = createDocument(file);
		FindReplaceDocumentAdapter findAdapter = new FindReplaceDocumentAdapter(
				document);
		addEclipseProcessingInstruction(document, findAdapter);
		updateExtensions(document, findAdapter);
		if (model.getPluginBase().getImports().length > 0)
			addNewImports(document, findAdapter, getAdditionalImports(model));
		writeFile(document, file);
	}

	private IDocument createDocument(IResource file) throws Exception {
		BufferedReader reader = new BufferedReader(new FileReader(file
				.getLocation().toOSString()));
		StringBuffer buffer = new StringBuffer();
		while (reader.ready()) {
			buffer.append((char) reader.read());
		}
		reader.close();
		return new Document(buffer.toString());
	}

	private void writeFile(IDocument document, IResource file) throws Exception {
		PrintWriter writer = new PrintWriter(new FileWriter(file.getLocation()
				.toOSString()));
		writer.write(document.get());
		writer.close();
	}

	private void addEclipseProcessingInstruction(IDocument document,
			FindReplaceDocumentAdapter adapter) {
		try {
			IRegion region = adapter.find(-1,
					"<\\?xml.*\\?>", true, true, false, true); //$NON-NLS-1$
			if (region != null) {
				String text = document.get(region.getOffset(), region
						.getLength());
				adapter.replace(text + System.getProperty("line.separator") //$NON-NLS-1$
						+ "<?eclipse version=\"3.0\"?>", //$NON-NLS-1$
						false);
			}
		} catch (BadLocationException e) {
		}
	}

	private void updateExtensions(IDocument document,
			FindReplaceDocumentAdapter adapter) {
		int start = 0;
		for (;;) {
			try {
				IRegion region = findNextExtension(adapter, start);
				if (region == null)
					break;
				IRegion idRegion = findPointAttributeRegion(adapter, region);
				if (idRegion != null) {
					String point = document.get(idRegion.getOffset(), idRegion
							.getLength());
					if (ExtensionPointMappings.isDeprecated(point.trim())) {
						adapter.replace(ExtensionPointMappings.getNewId(point
								.trim()), false);
					}
				}
				start = region.getOffset() + region.getLength();
			} catch (BadLocationException e) {
			}
		}
	}

	private IRegion findPointAttributeRegion(
			FindReplaceDocumentAdapter adapter, IRegion parentRegion) {
		try {
			IRegion region = adapter.find(parentRegion.getOffset(),
					"\\s+point\\s*=\\s*\"", //$NON-NLS-1$
					true, true, false, true);
			if (region != null
					&& region.getOffset() + region.getLength() <= parentRegion
							.getOffset()
							+ parentRegion.getLength()) {
				region = adapter.find(region.getOffset() + region.getLength(),
						"[^\"]*", //$NON-NLS-1$
						true, true, false, true);
				if (region != null
						&& region.getOffset() + region.getLength() < parentRegion
								.getOffset()
								+ parentRegion.getLength()) {
					return region;
				}
			}
		} catch (BadLocationException e) {
		}
		return null;
	}

	private IRegion findNextExtension(FindReplaceDocumentAdapter adapter,
			int start) {
		int offset = -1;
		int length = -1;
		try {
			IRegion region = adapter.find(start,
					"<extension\\s+", true, true, false, true); //$NON-NLS-1$
			if (region != null) {
				offset = region.getOffset();
				region = adapter.find(offset, ">", true, true, false, false); //$NON-NLS-1$
				if (region != null) {
					length = region.getOffset() - offset + 1;
				}
			}
		} catch (BadLocationException e) {
		}
		return (offset != -1 && length != -1) ? new Region(offset, length)
				: null;
	}

	private String[] getAdditionalImports(IPluginModelBase model) {
		ArrayList result = new ArrayList();
		//TODO do no just add. If core.runtime exists, replace it.
		//if (findImport(model, "org.eclipse.core.runtime") == null)
		result
				.add("<import plugin=\"org.eclipse.core.runtime.compatibility\"/>"); //$NON-NLS-1$
		IPluginImport uiImport = findImport(model, "org.eclipse.ui"); //$NON-NLS-1$
		if (uiImport != null) {
			ArrayList list = new ArrayList();
			list.add("org.eclipse.ui.ide"); //$NON-NLS-1$
			list.add("org.eclipse.ui.views"); //$NON-NLS-1$
			list.add("org.eclipse.jface.text"); //$NON-NLS-1$
			list.add("org.eclipse.ui.workbench.texteditor"); //$NON-NLS-1$
			list.add("org.eclipse.ui.editors"); //$NON-NLS-1$
			IPluginImport[] imports = model.getPluginBase().getImports();
			for (int i = 0; i < imports.length; i++) {
				if (list.contains(imports[i].getId())) {
					list.remove(imports[i].getId());
				}
			}
			for (int i = 0; i < list.size(); i++) {
				StringBuffer buffer = new StringBuffer("<import plugin=\""); //$NON-NLS-1$
				buffer.append(list.get(i) + "\""); //$NON-NLS-1$
				if (uiImport.isReexported())
					buffer.append(" export=\"true\""); //$NON-NLS-1$
				if (uiImport.isOptional())
					buffer.append(" optional=\"true\""); //$NON-NLS-1$
				buffer.append("/>"); //$NON-NLS-1$
				result.add(buffer.toString());
			}
		} else if (needsAdditionalUIImport(model)) {
			result.add("<import plugin=\"org.eclipse.ui\"/>"); //$NON-NLS-1$
		}
		if (needsHelpBaseImport(model))
			result.add("<import plugin=\"org.eclipse.help.base\"/>"); //$NON-NLS-1$

		return (String[]) result.toArray(new String[result.size()]);
	}

	private void addNewImports(IDocument document,
			FindReplaceDocumentAdapter adapter, String[] imports) {
		try {
			if (imports.length == 0)
				return;

			String space = ""; //$NON-NLS-1$
			IRegion requiresRegion = adapter.find(0,
					"<requires>", true, false, false, false); //$NON-NLS-1$
			if (requiresRegion != null) {
				IRegion spacerRegion = adapter.find(requiresRegion.getOffset()
						+ requiresRegion.getLength(),
						"\\s*", true, true, false, true); //$NON-NLS-1$
				if (spacerRegion != null) {
					space = document.get(spacerRegion.getOffset(), spacerRegion
							.getLength());
				}
			}
			StringBuffer buffer = new StringBuffer(space);
			for (int i = 0; i < imports.length; i++) {
				buffer.append(imports[i] + space);
			}
			adapter.replace(buffer.toString(), false);
		} catch (BadLocationException e) {
		}
	}

	private boolean needsAdditionalUIImport(IPluginModelBase model) {
		IPluginExtension[] extensions = model.getPluginBase().getExtensions();
		for (int i = 0; i < extensions.length; i++) {
			if (ExtensionPointMappings.hasMovedFromHelpToUI(extensions[i]
					.getPoint())
					&& findImport(model, "org.eclipse.ui") == null) //$NON-NLS-1$
				return true;
		}
		return false;
	}

	private boolean needsHelpBaseImport(IPluginModelBase model) {
		IPluginExtension[] extensions = model.getPluginBase().getExtensions();
		for (int i = 0; i < extensions.length; i++) {
			if (ExtensionPointMappings.hasMovedFromHelpToBase(extensions[i]
					.getPoint())
					&& findImport(model, "org.eclipse.help.base") == null) { //$NON-NLS-1$
				return true;
			}
		}
		return false;
	}

	private IPluginImport findImport(IPluginModelBase model, String importID) {
		IPluginImport[] imports = model.getPluginBase().getImports();
		for (int i = 0; i < imports.length; i++) {
			if (imports[i].getId().equals(importID)) {
				return imports[i];
			}
		}
		return null;
	}

}
