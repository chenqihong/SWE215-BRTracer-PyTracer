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
package org.eclipse.team.internal.ui.synchronize;

import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.team.core.synchronize.*;
import org.eclipse.team.internal.ui.*;
import org.eclipse.team.ui.TeamImages;
import org.eclipse.team.ui.synchronize.ISynchronizeModelElement;
import org.eclipse.team.ui.synchronize.ISynchronizePageConfiguration;

/**
 * An input that can be used with both {@link } and 
 * {@link }. The
 * job of this input is to create the logical model of the contents of the
 * sync set for displaying to the user. The created logical model must diff
 * nodes.
 * <p>
 * 1. First, prepareInput is called to initialize the model with the given sync
 * set. Building the model occurs in the ui thread.
 * 2. The input must react to changes in the sync set and adjust its diff node
 * model then update the viewer. In effect mediating between the sync set
 * changes and the model shown to the user. This happens in the ui thread.
 * </p>
 * NOT ON DEMAND - model is created then maintained!
 * @since 3.0
 */
public class HierarchicalModelProvider extends SynchronizeModelProvider {
	
	public static class HierarchicalModelProviderDescriptor implements ISynchronizeModelProviderDescriptor {
		public static final String ID = TeamUIPlugin.ID + ".modelprovider_hierarchical"; //$NON-NLS-1$
		public String getId() {
			return ID;
		}		
		public String getName() {
			return TeamUIMessages.HierarchicalModelProvider_0; //$NON-NLS-1$
		}		
		public ImageDescriptor getImageDescriptor() {
			return TeamImages.getImageDescriptor(ITeamUIImages.IMG_HIERARCHICAL);
		}
	}
	private static final HierarchicalModelProviderDescriptor hierarchicalDescriptor = new HierarchicalModelProviderDescriptor();
	
	/**
	 * Create an input based on the provide sync set. The input is not initialized
	 * until <code>prepareInput</code> is called. 
	 * 
	 * @param set the sync set used as the basis for the model created by this input.
	 */
	public HierarchicalModelProvider(ISynchronizePageConfiguration configuration, SyncInfoSet set) {
		super(configuration, set);
	}

    public HierarchicalModelProvider(
            AbstractSynchronizeModelProvider parentProvider,
            ISynchronizeModelElement modelRoot,
            ISynchronizePageConfiguration configuration, SyncInfoSet set) {
        super(parentProvider, modelRoot, configuration, set);
    }
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.synchronize.ISynchronizeModelProvider#getDescriptor()
	 */
	public ISynchronizeModelProviderDescriptor getDescriptor() {
		return hierarchicalDescriptor;
	}
	
	public ViewerSorter getViewerSorter() {
		return new SynchronizeModelElementSorter();
	}

	protected SyncInfoTree getSyncInfoTree() {
		return (SyncInfoTree)getSyncInfoSet();
	}

	/**
	 * Invoked by the <code>buildModelObject</code> method to create
	 * the childen of the given node. This method can be overriden
	 * by subclasses but subclasses should inv
	 * @param container
	 * @return
	 */
	protected IDiffElement[] createModelObjects(ISynchronizeModelElement container) {
		IResource resource = null;
		if (container == getModelRoot()) {
			resource = ResourcesPlugin.getWorkspace().getRoot();
		} else {
			resource = container.getResource();
		}
		if(resource != null) {
			SyncInfoTree infoTree = getSyncInfoTree();
			IResource[] children = infoTree.members(resource);
			ISynchronizeModelElement[] nodes = new ISynchronizeModelElement[children.length];
			for (int i = 0; i < children.length; i++) {
				nodes[i] = createModelObject(container, children[i]);
			}
			return nodes;	
		}
		return new IDiffElement[0];
	}

	protected ISynchronizeModelElement createModelObject(ISynchronizeModelElement parent, IResource resource) {
		SyncInfo info = getSyncInfoTree().getSyncInfo(resource);
		SynchronizeModelElement newNode;
		if(info != null) {
			newNode = new SyncInfoModelElement(parent, info);
		} else {
			newNode = new UnchangedResourceModelElement(parent, resource);
		}
		addToViewer(newNode);
		return newNode;
	}

	/**
	 * Invokes <code>getModelObject(Object)</code> on an array of resources.
	 * @param resources
	 *            the resources
	 * @return the model objects for the resources
	 */
	protected Object[] getModelObjects(IResource[] resources) {
		Object[] result = new Object[resources.length];
		for (int i = 0; i < resources.length; i++) {
			result[i] = getModelObject(resources[i]);
		}
		return result;
	}

	protected void addResources(IResource[] added) {
		for (int i = 0; i < added.length; i++) {
			IResource resource = added[i];
            addResource(resource);
		}
	}

    private void addResource(IResource resource) {
        ISynchronizeModelElement node = getModelObject(resource);
        if (node != null) {
        	// Somehow the node exists. Remove it and read it to ensure
        	// what is shown matches the contents of the sync set
        	removeFromViewer(resource);
        }
        // Build the sub-tree rooted at this node
        ISynchronizeModelElement parent = getModelObject(resource.getParent());
        if (parent != null) {
        	node = createModelObject(parent, resource);
        	buildModelObjects(node);
        }
    }

    /* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.viewers.SynchronizeModelProvider#buildModelObjects(org.eclipse.team.ui.synchronize.viewers.SynchronizeModelElement)
	 */
	protected IDiffElement[] buildModelObjects(ISynchronizeModelElement node) {
		IDiffElement[] children = createModelObjects(node);
		for (int i = 0; i < children.length; i++) {
			IDiffElement element = children[i];
			if (element instanceof ISynchronizeModelElement) {
				buildModelObjects((ISynchronizeModelElement) element);
			}
		}
		return children;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.viewers.SynchronizeModelProvider#handleResourceAdditions(org.eclipse.team.core.synchronize.ISyncInfoTreeChangeEvent)
	 */
	protected void handleResourceAdditions(ISyncInfoTreeChangeEvent event) {
		IResource[] added = event.getAddedSubtreeRoots();
		addResources(added);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.ui.synchronize.viewers.SynchronizeModelProvider#handleResourceRemovals(org.eclipse.team.core.synchronize.ISyncInfoTreeChangeEvent)
	 */
	protected void handleResourceRemovals(ISyncInfoTreeChangeEvent event) {
		// Remove the removed subtrees
		IResource[] removedRoots = event.getRemovedSubtreeRoots();
		removeFromViewer(removedRoots);
		// We have to look for folders that may no longer be in the set
		// (i.e. are in-sync) but still have descendants in the set
		IResource[] removedResources = event.getRemovedResources();
		for (int i = 0; i < removedResources.length; i++) {
			IResource resource = removedResources[i];
			if (resource.getType() != IResource.FILE) {
				ISynchronizeModelElement node = getModelObject(resource);
				if (node != null) {
					removeFromViewer(resource);
					addResources(new IResource[] {resource});
				}
			}
		}
	}

    /* (non-Javadoc)
     * @see org.eclipse.team.internal.ui.synchronize.SynchronizeModelProvider#createModelObject(org.eclipse.team.ui.synchronize.ISynchronizeModelElement, org.eclipse.team.core.synchronize.SyncInfo)
     */
    protected ISynchronizeModelElement createModelObject(ISynchronizeModelElement parent, SyncInfo info) {
        return createModelObject(parent, info.getLocal());
    }

    /* (non-Javadoc)
     * @see org.eclipse.team.internal.ui.synchronize.SynchronizeModelProvider#addResource(org.eclipse.team.core.synchronize.SyncInfo)
     */
    protected void addResource(SyncInfo info) {
        addResource(info.getLocal());
    }
}
