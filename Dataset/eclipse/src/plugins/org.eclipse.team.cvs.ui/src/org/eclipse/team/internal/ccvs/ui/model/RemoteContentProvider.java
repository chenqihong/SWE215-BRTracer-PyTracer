/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.internal.ccvs.ui.model;

import org.eclipse.jface.viewers.AbstractTreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.core.resources.RemoteResource;
import org.eclipse.team.internal.ccvs.ui.repo.RepositoryRoot;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.progress.DeferredTreeContentManager;

/**
 * Extension to the generic workbench content provider mechanism
 * to lazily determine whether an element has children.  That is,
 * children for an element aren't fetched until the user clicks
 * on the tree expansion box.
 */
public class RemoteContentProvider extends WorkbenchContentProvider {

	IWorkingSet workingSet;
	DeferredTreeContentManager manager;

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.IContentProvider#inputChanged(org.eclipse.jface.viewers.Viewer, java.lang.Object, java.lang.Object)
	 */
	public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
		if (viewer instanceof AbstractTreeViewer) {
			manager = new DeferredTreeContentManager(this, (AbstractTreeViewer) viewer);
		}
		super.inputChanged(viewer, oldInput, newInput);
	}

	public boolean hasChildren(Object element) {
		// the + box will always appear, but then disappear
		// if not needed after you first click on it.
		if (element instanceof ICVSRemoteResource) {
			if (element instanceof ICVSRemoteFolder) {
				return ((ICVSRemoteFolder) element).isExpandable();
			}
			return ((ICVSRemoteResource) element).isContainer();
		} else if (element instanceof CVSResourceElement) {
			ICVSResource r = ((CVSResourceElement) element).getCVSResource();
			if (r instanceof RemoteResource) {
				return r.isFolder();
			}
		} else if (element instanceof VersionCategory) {
			return true;
		} else if (element instanceof BranchCategory) {
			return true;
		} else if (element instanceof CVSTagElement) {
			return true;
		} else if (element instanceof RemoteModule) {
			return true;
		}
		if (manager != null) {
			if (manager.isDeferredAdapter(element))
				return manager.mayHaveChildren(element);
		}

		return super.hasChildren(element);
	}

	/**
	 * Sets the workingSet.
	 * @param workingSet The workingSet to set
	 */
	public void setWorkingSet(IWorkingSet workingSet) {
		this.workingSet = workingSet;
	}

	/**
	 * Returns the workingSet.
	 * @return IWorkingSet
	 */
	public IWorkingSet getWorkingSet() {
		return workingSet;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.model.WorkbenchContentProvider#getChildren(java.lang.Object)
	 */
	public Object[] getChildren(Object element) {
		if (manager != null) {
			Object[] children = manager.getChildren(element);
			if (children != null) {
				// This will be a placeholder to indicate 
				// that the real children are being fetched
				return children;
			}
		}
		Object[] children = super.getChildren(element);
		for (int i = 0; i < children.length; i++) {
			Object object = children[i];
			if (object instanceof CVSModelElement) 
				((CVSModelElement)object).setWorkingSet(getWorkingSet());
		}
		return children;
	}

	public void cancelJobs(RepositoryRoot[] roots) {
		if (manager != null) {
			for (int i = 0; i < roots.length; i++) {
				RepositoryRoot root = roots[i];
				cancelJobs(root.getRoot());
			}
		}
	}
	
	/**
	 * Cancel any jobs that are fetching content from the given location.
	 * @param location
	 */
	public void cancelJobs(ICVSRepositoryLocation location) {
		if (manager != null) {
			manager.cancel(location);
		}
	}
}
