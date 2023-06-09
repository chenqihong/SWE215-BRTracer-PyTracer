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
package org.eclipse.pde.internal.core.build;

import java.io.*;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.pde.core.*;
import org.eclipse.pde.internal.core.*;

public class WorkspaceBuildModel extends BuildModel implements IEditableModel {
	private static final long serialVersionUID = 1L;
	private IFile fUnderlyingResource;
	private boolean fDirty;
	private boolean fEditable = true;

	public WorkspaceBuildModel(IFile file) {
		fUnderlyingResource = file;
	}

	public void fireModelChanged(IModelChangedEvent event) {
		setDirty(event.getChangeType() != IModelChangedEvent.WORLD_CHANGED);
		super.fireModelChanged(event);
	}

	public String getContents() {
		StringWriter swriter = new StringWriter();
		PrintWriter writer = new PrintWriter(swriter);
		save(writer);
		writer.flush();
		try {
			swriter.close();
			writer.close();
		} catch (IOException e) {
		}
		return swriter.toString();
	}

	public IResource getUnderlyingResource() {
		return fUnderlyingResource;
	}

	public boolean isDirty() {
		return fDirty;
	}

	public boolean isEditable() {
		return fEditable;
	}

	public void load() {
		if (fUnderlyingResource.exists()) {
			try {
				InputStream stream = fUnderlyingResource.getContents(true);
				load(stream, false);
				stream.close();
			} catch (Exception e) {
				PDECore.logException(e);
			}
		} else {
			fBuild = new Build();
			fBuild.setModel(this);
			setLoaded(true);
		}
	}

	public boolean isInSync() {
		return true;
	}

	protected void updateTimeStamp() {
		updateTimeStamp(fUnderlyingResource.getLocation().toFile());
	}

	public void save() {
		if (fUnderlyingResource == null)
			return;
		try {
			String contents = getContents();
			ByteArrayInputStream stream = new ByteArrayInputStream(contents
					.getBytes("8859_1")); //$NON-NLS-1$
			if (fUnderlyingResource.exists()) {
				fUnderlyingResource.setContents(stream, false, false, null);
			} else {
				fUnderlyingResource.create(stream, false, null);
			}
			stream.close();
		} catch (CoreException e) {
			PDECore.logException(e);
		} catch (IOException e) {
		}
	}

	public void save(PrintWriter writer) {
		getBuild().write("", writer); //$NON-NLS-1$
		fDirty = false;
	}

	public void setDirty(boolean dirty) {
		fDirty = dirty;
	}

	public void setEditable(boolean editable) {
		fEditable = editable;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.core.build.IBuildModel#getInstallLocation()
	 */
	public String getInstallLocation() {
		return fUnderlyingResource.getLocation().toOSString();
	}
}
