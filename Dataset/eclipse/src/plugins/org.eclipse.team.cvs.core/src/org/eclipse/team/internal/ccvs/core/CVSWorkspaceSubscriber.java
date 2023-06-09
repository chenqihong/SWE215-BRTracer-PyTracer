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
package org.eclipse.team.internal.ccvs.core;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.QualifiedName;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.core.ITeamStatus;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.TeamStatus;
import org.eclipse.team.core.subscribers.ISubscriberChangeEvent;
import org.eclipse.team.core.subscribers.SubscriberChangeEvent;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.core.synchronize.SyncInfoSet;
import org.eclipse.team.core.variants.IResourceVariant;
import org.eclipse.team.core.variants.IResourceVariantTree;
import org.eclipse.team.core.variants.PersistantResourceVariantByteStore;
import org.eclipse.team.core.variants.ResourceVariantByteStore;
import org.eclipse.team.internal.ccvs.core.connection.CVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.resources.EclipseSynchronizer;
import org.eclipse.team.internal.ccvs.core.resources.RemoteFolderTreeBuilder;
import org.eclipse.team.internal.ccvs.core.syncinfo.CVSBaseResourceVariantTree;
import org.eclipse.team.internal.ccvs.core.syncinfo.CVSDescendantResourceVariantByteStore;
import org.eclipse.team.internal.ccvs.core.syncinfo.CVSResourceVariantTree;
import org.eclipse.team.internal.ccvs.core.util.ResourceStateChangeListeners;

/**
 * CVSWorkspaceSubscriber
 */
public class CVSWorkspaceSubscriber extends CVSSyncTreeSubscriber implements IResourceStateChangeListener {
	
	private CVSResourceVariantTree baseTree, remoteTree;
	
	// qualified name for remote sync info
	private static final String REMOTE_RESOURCE_KEY = "remote-resource-key"; //$NON-NLS-1$

	CVSWorkspaceSubscriber(QualifiedName id, String name) {
		super(id, name);
		
		// install sync info participant
		ResourceVariantByteStore baseSynchronizer = new CVSBaseResourceVariantTree();
		baseTree = new CVSResourceVariantTree(baseSynchronizer, null, getCacheFileContentsHint()) {
			public IResource[] refresh(IResource[] resources, int depth, IProgressMonitor monitor) throws TeamException {
				// TODO Ensure that file contents are cached for modified local files
				try {
					monitor.beginTask(null, 100);
					return new IResource[0];
				} finally {
					monitor.done();
				}
			}
		};
		CVSDescendantResourceVariantByteStore remoteSynchronizer = new CVSDescendantResourceVariantByteStore(
				baseSynchronizer, 
				new PersistantResourceVariantByteStore(new QualifiedName(SYNC_KEY_QUALIFIER, REMOTE_RESOURCE_KEY)));
		remoteTree = new CVSResourceVariantTree(remoteSynchronizer, null, getCacheFileContentsHint());
		
		ResourceStateChangeListeners.getListener().addResourceStateChangeListener(this); 
	}

	/* 
	 * Return the list of projects shared with a CVS team provider.
	 * 
	 * [Issue : this will have to change when folders can be shared with
	 * a team provider instead of the current project restriction]
	 * (non-Javadoc)
	 * @see org.eclipse.team.core.sync.ISyncTreeSubscriber#roots()
	 */
	public IResource[] roots() {
		List result = new ArrayList();
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (int i = 0; i < projects.length; i++) {
			IProject project = projects[i];
			if(project.isOpen()) {
				RepositoryProvider provider = RepositoryProvider.getProvider(project, CVSProviderPlugin.getTypeId());
				if(provider != null) {
					result.add(project);
				}
			}
		}
		return (IProject[]) result.toArray(new IProject[result.size()]);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#resourceSyncInfoChanged(org.eclipse.core.resources.IResource[])
	 */
	public void resourceSyncInfoChanged(IResource[] changedResources) {
		internalResourceSyncInfoChanged(changedResources, true); 
	}

	private void internalResourceSyncInfoChanged(IResource[] changedResources, boolean canModifyWorkspace) {
		getRemoteByteStore().handleResourceChanges(changedResources, canModifyWorkspace);	
		fireTeamResourceChange(SubscriberChangeEvent.asSyncChangedDeltas(this, changedResources));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#externalSyncInfoChange(org.eclipse.core.resources.IResource[])
	 */
	public void externalSyncInfoChange(IResource[] changedResources) {
		internalResourceSyncInfoChanged(changedResources, false);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#resourceModified(org.eclipse.core.resources.IResource[])
	 */
	public void resourceModified(IResource[] changedResources) {
		// This is only ever called from a delta POST_CHANGE
		// which causes problems since the workspace tree is closed
		// for modification and we flush the sync info in resourceSyncInfoChanged
		
		// Since the listeners of the Subscriber will also listen to deltas
		// we don't need to propogate this.
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#projectConfigured(org.eclipse.core.resources.IProject)
	 */
	public void projectConfigured(IProject project) {
		SubscriberChangeEvent delta = new SubscriberChangeEvent(this, ISubscriberChangeEvent.ROOT_ADDED, project);
		fireTeamResourceChange(new SubscriberChangeEvent[] {delta});
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.IResourceStateChangeListener#projectDeconfigured(org.eclipse.core.resources.IProject)
	 */
	public void projectDeconfigured(IProject project) {
		try {
			getRemoteTree().flushVariants(project, IResource.DEPTH_INFINITE);
		} catch (TeamException e) {
			CVSProviderPlugin.log(e);
		}
		SubscriberChangeEvent delta = new SubscriberChangeEvent(this, ISubscriberChangeEvent.ROOT_REMOVED, project);
		fireTeamResourceChange(new SubscriberChangeEvent[] {delta});
	}

	public void setRemote(IResource resource, IResourceVariant remote, IProgressMonitor monitor) throws TeamException {
		// TODO: This exposes internal behavior to much
		IResource[] changedResources = 
			((CVSResourceVariantTree)getRemoteTree()).collectChanges(resource, remote, IResource.DEPTH_INFINITE, monitor);
		if (changedResources.length != 0) {
			fireTeamResourceChange(SubscriberChangeEvent.asSyncChangedDeltas(this, changedResources));
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.CVSSyncTreeSubscriber#getBaseSynchronizationCache()
	 */
	protected IResourceVariantTree getBaseTree() {
		return baseTree;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.CVSSyncTreeSubscriber#getRemoteSynchronizationCache()
	 */
	protected IResourceVariantTree getRemoteTree() {
		return remoteTree;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.core.subscribers.Subscriber#collectOutOfSync(org.eclipse.core.resources.IResource[], int, org.eclipse.team.core.synchronize.SyncInfoSet, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void collectOutOfSync(IResource[] resources, int depth, final SyncInfoSet set, final IProgressMonitor monitor) {
		monitor.beginTask(null, IProgressMonitor.UNKNOWN);
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			try {
				if (!isSupervised(resource)) {
					return;
				}
			} catch (TeamException e) {
				// fallthrough and try to collect sync info
				CVSProviderPlugin.log(e);
			}
			try {
				resource.accept(new IResourceVisitor() {
					public boolean visit(IResource innerResource) throws CoreException {
						try {
							Policy.checkCanceled(monitor);
							if (innerResource.getType() != IResource.FILE) {
								monitor.subTask(NLS.bind(CVSMessages.CVSWorkspaceSubscriber_1, new String[] { innerResource.getFullPath().toString() })); //$NON-NLS-1$
							}
							if (isOutOfSync(innerResource, monitor)) {
								SyncInfo info = getSyncInfo(innerResource);
								if (info != null && info.getKind() != 0) {
									set.add(info);
								}
							}
						} catch (TeamException e) {
							set.addError(new TeamStatus(
									IStatus.ERROR, CVSProviderPlugin.ID, ITeamStatus.RESOURCE_SYNC_INFO_ERROR,
									NLS.bind(CVSMessages.CVSWorkspaceSubscriber_2, new String[] { innerResource.getFullPath().toString(), e.getMessage() }), e, innerResource)); //$NON-NLS-1$
						}
						return true;
					}
				}, depth, true /* include phantoms */);
			} catch (CoreException e) {
				set.addError(new TeamStatus(
						IStatus.ERROR, CVSProviderPlugin.ID, ITeamStatus.SYNC_INFO_SET_ERROR,
						e.getMessage(), e, ResourcesPlugin.getWorkspace().getRoot()));
			}
		}
		monitor.done();
	}
	
	/* internal use only */ boolean isOutOfSync(IResource resource, IProgressMonitor monitor) throws TeamException {
		return (hasIncomingChange(resource) || hasOutgoingChange(resource, monitor));
	}
	
	private boolean hasIncomingChange(IResource resource) throws TeamException {
		return getRemoteByteStore().isVariantKnown(resource);
	}
	
	private boolean hasOutgoingChange(IResource resource, IProgressMonitor monitor) throws CVSException {
		if (resource.getType() == IResource.PROJECT || resource.getType() == IResource.ROOT) {
			// a project (or the workspace root) cannot have outgoing changes
			return false;
		}
		int state = EclipseSynchronizer.getInstance().getModificationState(resource.getParent());
		if (state == ICVSFile.CLEAN) {
			// if the parent is known to be clean then the resource must also be clean
			return false;
		}
		if (resource.getType() == IResource.FILE) {
			// A file is an outgoing change if it is modified
			ICVSFile file = CVSWorkspaceRoot.getCVSFileFor((IFile)resource);
			return file.isModified(monitor);
		} else {
			// A folder is an outgoing change if it is not a CVS folder and not ignored
			ICVSFolder folder = CVSWorkspaceRoot.getCVSFolderFor((IContainer)resource);
			return !folder.isCVSFolder() && !folder.isIgnored();
		}
	}
	
	/*
	 * TODO: Should not need to access this here
	 */
	private CVSDescendantResourceVariantByteStore getRemoteByteStore() {
		return (CVSDescendantResourceVariantByteStore)((CVSResourceVariantTree)getRemoteTree()).getByteStore();
	}

	/**
	 * Update the remote tree to the base
	 * @param folder
	 * @param recurse 
	 */
	public void updateRemote(CVSTeamProvider provider, ICVSFolder folder, boolean recurse, IProgressMonitor monitor) throws TeamException {
		try {
			monitor.beginTask(null, 100);
			IResource resource = folder.getIResource();
			if (resource != null) {
				ICVSResource tree = RemoteFolderTreeBuilder.buildBaseTree(
						(CVSRepositoryLocation)provider.getRemoteLocation(), 
						folder, 
						null, 
						Policy.subMonitorFor(monitor, 50));
				setRemote(resource, (IResourceVariant)tree, Policy.subMonitorFor(monitor, 50));
			}
		} finally {
			monitor.done();
		}
	}

}
