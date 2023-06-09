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
package org.eclipse.pde.internal.ui.wizards.imports;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.internal.core.ifeature.IFeatureModel;
import org.eclipse.pde.internal.ui.*;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.*;
import org.eclipse.pde.internal.ui.wizards.imports.FeatureImportOperation.IReplaceQuery;

public class FeatureImportWizard extends Wizard implements IImportWizard {

	private static final String STORE_SECTION = "FeatureImportWizard"; //$NON-NLS-1$
	private FeatureImportWizardFirstPage fPage1;
	private FeatureImportWizardDetailedPage fPage2;

	public FeatureImportWizard() {
		IDialogSettings masterSettings = PDEPlugin.getDefault().getDialogSettings();
		setDialogSettings(getSettingsSection(masterSettings));
		setDefaultPageImageDescriptor(PDEPluginImages.DESC_FEATURE_IMPORT_WIZ);
		setWindowTitle(PDEUIMessages.FeatureImportWizard_title); //$NON-NLS-1$
	}

	/*
	 * @see IWorkbenchWizard#init(IWorkbench, IStructuredSelection)
	 */
	public void init(IWorkbench workbench, IStructuredSelection selection) {
	}

	/*
	 * @see org.eclipse.jface.wizard.IWizard#addPages
	 */
	public void addPages() {
		setNeedsProgressMonitor(true);

		fPage1 = new FeatureImportWizardFirstPage();
		addPage(fPage1);
		fPage2 = new FeatureImportWizardDetailedPage(fPage1);
		addPage(fPage2);
	}

	private IDialogSettings getSettingsSection(IDialogSettings master) {
		IDialogSettings setting = master.getSection(STORE_SECTION);
		if (setting == null) {
			setting = master.addNewSection(STORE_SECTION);
		}
		return setting;
	}

	/*
	 * @see Wizard#performFinish()
	 */
	public boolean performFinish() {
		try {
			final IFeatureModel[] models = fPage2.getSelectedModels();
			fPage1.storeSettings(true);
			IPath targetPath = computeTargetPath();

			IRunnableWithProgress op = getImportOperation(getShell(), fPage1
					.isBinary(), models, targetPath);
			getContainer().run(true, true, op);

		} catch (InterruptedException e) {
			return false;
		} catch (InvocationTargetException e) {
			PDEPlugin.logException(e);
			return true; // exception handled
		}
		return true;
	}

	/**
	 * 
	 * @return IPath or null
	 */private IPath computeTargetPath() {
		IPath pluginsLocation = PDEPlugin.getWorkspace().getRoot().getLocation();
		if("plugins".equals(pluginsLocation.lastSegment())) //$NON-NLS-1$
			return pluginsLocation.removeLastSegments(1).append("features"); //$NON-NLS-1$
		return null;
	}

	/**
	 * 
	 * @param shell
	 * @param models
	 * @param targetPath null to use default workspace location
	 * @return
	 */
	public static IRunnableWithProgress getImportOperation(
		final Shell shell,
		final boolean binary,
		final IFeatureModel[] models,
		final IPath targetPath) {
		return new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor)
				throws InvocationTargetException, InterruptedException {
				try {
					IReplaceQuery query = new ReplaceQuery(shell);
					FeatureImportOperation op =
						new FeatureImportOperation(models, binary, targetPath, query);
					PDEPlugin.getWorkspace().run(op, monitor);
				} catch (CoreException e) {
					throw new InvocationTargetException(e);
				} catch (OperationCanceledException e) {
					throw new InterruptedException(e.getMessage());
				} finally {
					monitor.done();
				}
			}
		};
	}

	private static class ReplaceDialog extends MessageDialog {
		public ReplaceDialog(Shell parentShell, String dialogMessage) {
			super(
				parentShell,
				PDEUIMessages.FeatureImportWizard_messages_title,
				null,
				dialogMessage,
				MessageDialog.QUESTION,
				new String[] {
					IDialogConstants.YES_LABEL,
					IDialogConstants.YES_TO_ALL_LABEL,
					IDialogConstants.NO_LABEL,
					PDEUIMessages.FeatureImportWizard_noToAll, //$NON-NLS-1$
					IDialogConstants.CANCEL_LABEL },
				0);
		}
	}

	private static class ReplaceQuery implements IReplaceQuery {
		private Shell shell;
		public ReplaceQuery(Shell shell) {
			this.shell = shell;
		}

		private int yesToAll = 0;
		private int[] RETURNCODES =
			{
				IReplaceQuery.YES,
				IReplaceQuery.YES,
				IReplaceQuery.NO,
				IReplaceQuery.NO,
				IReplaceQuery.CANCEL };

		public int doQuery(IProject project) {
			if (yesToAll != 0) {
				return yesToAll > 0 ? IReplaceQuery.YES : IReplaceQuery.NO;
			}

			final String message =
				NLS.bind(PDEUIMessages.FeatureImportWizard_messages_exists, project.getName());
			final int[] result = { IReplaceQuery.CANCEL };
			shell.getDisplay().syncExec(new Runnable() {
				public void run() {
					ReplaceDialog dialog = new ReplaceDialog(shell, message);
					int retVal = dialog.open();
					if (retVal >= 0) {
						result[0] = RETURNCODES[retVal];
						if (retVal == 1) {
							yesToAll = 1;
						} else if (retVal == 3) {
							yesToAll = -1;
						}
					}
				}
			});
			return result[0];
		}
	}
}
