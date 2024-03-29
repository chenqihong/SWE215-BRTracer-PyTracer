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
package org.eclipse.team.internal.ccvs.ui.operations;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.team.internal.ccvs.core.CVSException;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder;
import org.eclipse.ui.IWorkbenchPart;

/**
 * This operation checks out a single remote folder into the workspace as
 * a project.
 */
public class CheckoutSingleProjectOperation extends CheckoutProjectOperation {

	private boolean preconfigured;
	private IProject targetProject;
	
	public CheckoutSingleProjectOperation(IWorkbenchPart part, ICVSRemoteFolder remoteFolder, IProject targetProject, String targetLocation, boolean preconfigured) {
		super(part, new ICVSRemoteFolder[] { remoteFolder }, targetLocation);
		this.targetProject = targetProject;
		this.preconfigured = preconfigured;
	}

	private boolean isPreconfigured() {
		return preconfigured;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.CheckoutOperation#needsPromptForOverwrite(org.eclipse.core.resources.IProject)
	 */
	public boolean needsPromptForOverwrite(IProject project) {
		// No need to prompt if the project was preconfigured
		if (isPreconfigured()) return false;
		return super.needsPromptForOverwrite(project);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.CheckoutProjectOperation#performScrubProjects()
	 */
	protected boolean performScrubProjects() {
		// Do not scrub the projects if they were preconfigured.
		return !isPreconfigured();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.ui.operations.CheckoutOperation#checkout(org.eclipse.team.internal.ccvs.core.ICVSRemoteFolder, org.eclipse.core.runtime.IProgressMonitor)
	 */
	protected IStatus checkout(ICVSRemoteFolder folder, IProgressMonitor monitor) throws CVSException {
		return checkout(folder, targetProject, monitor);
	}

}
