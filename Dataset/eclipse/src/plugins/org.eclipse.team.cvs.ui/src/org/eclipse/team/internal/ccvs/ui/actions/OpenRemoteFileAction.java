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
package org.eclipse.team.internal.ccvs.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFile;
import org.eclipse.team.internal.ccvs.core.ILogEntry;
import org.eclipse.team.internal.ccvs.ui.CVSUIPlugin;
import org.eclipse.team.internal.ccvs.ui.RemoteFileEditorInput;
import org.eclipse.ui.*;

public class OpenRemoteFileAction extends CVSAction {
	/**
	 * Returns the selected remote files
	 */
	protected ICVSRemoteFile[] getSelectedRemoteFiles() {
		ArrayList resources = null;
		if (!selection.isEmpty()) {
			resources = new ArrayList();
			Iterator elements = selection.iterator();
			while (elements.hasNext()) {
				Object next = elements.next();
				if (next instanceof ICVSRemoteFile) {
					resources.add(next);
					continue;
				}
				if (next instanceof ILogEntry) {
					resources.add(((ILogEntry)next).getRemoteFile());
					continue;
				}
				if (next instanceof IAdaptable) {
					IAdaptable a = (IAdaptable) next;
					Object adapter = a.getAdapter(ICVSRemoteFile.class);
					if (adapter instanceof ICVSRemoteFile) {
						resources.add(adapter);
						continue;
					}
				}
			}
		}
		if (resources != null && !resources.isEmpty()) {
			ICVSRemoteFile[] result = new ICVSRemoteFile[resources.size()];
			resources.toArray(result);
			return result;
		}
		return new ICVSRemoteFile[0];
	}
	/*
	 * @see CVSAction#execute(IAction)
	 */
	public void execute(IAction action) throws InterruptedException, InvocationTargetException {
		run(new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException {
				IWorkbench workbench = CVSUIPlugin.getPlugin().getWorkbench();
				IEditorRegistry registry = workbench.getEditorRegistry();
				IWorkbenchPage page = workbench.getActiveWorkbenchWindow().getActivePage();
				ICVSRemoteFile[] files = getSelectedRemoteFiles();
				for (int i = 0; i < files.length; i++) {
					ICVSRemoteFile file = files[i];
					String filename = file.getName();
					IEditorDescriptor descriptor = registry.getDefaultEditor(filename);
					String id;
					if (descriptor == null) {
						id = "org.eclipse.ui.DefaultTextEditor"; //$NON-NLS-1$
					} else {
						id = descriptor.getId();
					}
					try {
						try {
							page.openEditor(new RemoteFileEditorInput(files[i], monitor), id);
						} catch (PartInitException e) {
							if (id.equals("org.eclipse.ui.DefaultTextEditor")) { //$NON-NLS-1$
								throw e;
							} else {
								page.openEditor(new RemoteFileEditorInput(files[i], monitor), "org.eclipse.ui.DefaultTextEditor"); //$NON-NLS-1$
							}
						}
					} catch (PartInitException e) {
						throw new InvocationTargetException(e);
					}
				}
			}
		}, false, PROGRESS_BUSYCURSOR); //$NON-NLS-1$
	}
	/*
	 * @see TeamAction#isEnabled()
	 */
	protected boolean isEnabled() throws TeamException {
		ICVSRemoteFile[] resources = getSelectedRemoteFiles();
		if (resources.length == 0) return false;
		return true;
	}
}
