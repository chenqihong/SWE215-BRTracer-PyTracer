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
package org.eclipse.team.internal.ccvs.ui;

import java.io.IOException;
import java.io.InputStream;

import org.eclipse.core.resources.IEncodedStorage;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.runtime.*;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.core.TeamPlugin;

public class RemoteAnnotationStorage extends PlatformObject implements IEncodedStorage {

	private InputStream contents;
	private ICVSRemoteFile file;
	
	public RemoteAnnotationStorage(ICVSRemoteFile file, InputStream contents) {
		this.file = file;
		this.contents = contents;
	}

	public InputStream getContents() throws CoreException {
		try {
			// Contents are a ByteArrayInputStream which can be reset to the beginning
			contents.reset();
		} catch (IOException e) {
			CVSUIPlugin.log(CVSException.wrapException(e));
		}
		return contents;
	}

	public String getCharset() throws CoreException {
		InputStream contents = getContents();
		try {
			String charSet = TeamPlugin.getCharset(getName(), contents);
			return charSet;
		} catch (IOException e) {
			throw new CVSException(new Status(IStatus.ERROR, CVSUIPlugin.ID, IResourceStatus.FAILED_DESCRIBING_CONTENTS, NLS.bind(CVSUIMessages.RemoteAnnotationStorage_1, (new String[] { getFullPath().toString() })), e)); //$NON-NLS-1$
		} finally {
			try {
				contents.close();
			} catch (IOException e1) {
				// Ignore
			}
		}
	}

	public IPath getFullPath() {
		ICVSRepositoryLocation location = file.getRepository();
		IPath path = new Path(null, location.getRootDirectory());
		path = path.setDevice(location.getHost() + Path.DEVICE_SEPARATOR);
		path = path.append(file.getRepositoryRelativePath());
		return path;
	}
	public String getName() {
		return file.getName();
	}
	public boolean isReadOnly() {
		return true;
	}
}
