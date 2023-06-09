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
package org.eclipse.team.internal.ccvs.ui.model;


import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.ICVSRemoteFile;
import org.eclipse.team.internal.ccvs.ui.CVSUIMessages;
import org.eclipse.ui.PlatformUI;

public class RemoteFileElement extends RemoteResourceElement {
	/**
	 * Initial implementation: return null;
	 */
	public Object[] fetchChildren(Object o, IProgressMonitor monitor) {
		return new Object[0];
	}
	/**
	 * Initial implementation: return null.
	 */
	public ImageDescriptor getImageDescriptor(Object object) {
		if (!(object instanceof ICVSRemoteFile)) return null;
		return PlatformUI.getWorkbench().getEditorRegistry().getImageDescriptor(((ICVSRemoteFile)object).getName());
	}
	/**
	 * Initial implementation: return the file's name and version
	 */
	public String getLabel(Object o) {
		if (!(o instanceof ICVSRemoteFile)) return null;
		ICVSRemoteFile file = (ICVSRemoteFile)o;
		try {
			return NLS.bind(CVSUIMessages.nameAndRevision, new String[] { file.getName(), file.getRevision() }); //$NON-NLS-1$
		} catch (TeamException e) {
		    handle(null, null, e);
			return null;
		}
	}
}
