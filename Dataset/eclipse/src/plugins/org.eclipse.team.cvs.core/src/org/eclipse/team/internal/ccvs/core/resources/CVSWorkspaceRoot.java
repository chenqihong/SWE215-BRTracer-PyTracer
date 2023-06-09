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
package org.eclipse.team.internal.ccvs.core.resources;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.core.connection.CVSRepositoryLocation;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.util.KnownRepositories;
import org.eclipse.team.internal.ccvs.core.util.Util;

/**
 * This class provides static methods for checking out projects from a repository
 * into the local workspace and for converting IResources into CVSRespources
 * and sync trees.
 * Instances of this class represent a local workspace root (i.e. a project).
 */
public class CVSWorkspaceRoot {

	private ICVSFolder localRoot;
	
	public CVSWorkspaceRoot(IContainer resource){
		this.localRoot = getCVSFolderFor(resource);
	}
					
	/**
	 * Set the sharing for a project to enable it to be used with the CVSTeamProvider.
	 * This method ensure that the repository in the FolderSyncInfo is known and that
	 * the project is mapped to a CVS repository provider. It does not modify the sync
	 * info associated with the project's resources in any way.
	 */
	public static void setSharing(IProject project, FolderSyncInfo info, IProgressMonitor monitor) throws TeamException {
		
		// Ensure provided info matches that of the project
		ICVSFolder folder = (ICVSFolder)CVSWorkspaceRoot.getCVSResourceFor(project);
		FolderSyncInfo folderInfo = folder.getFolderSyncInfo();
		if ( ! info.equals(folderInfo)) {
			throw new CVSException(new CVSStatus(CVSStatus.ERROR, NLS.bind(CVSMessages.CVSProvider_infoMismatch, new String[] { project.getName() })));//$NON-NLS-1$
		}
        
        // Ensure that the repository location format is supported
        String root = info.getRoot();
        // This will try to create a repository location for the root.
        // If it fails, an exception is thrown.
        KnownRepositories.getInstance().getRepository(root);
		
		// Register the project with Team
		RepositoryProvider.map(project, CVSProviderPlugin.getTypeId());
	}
				
	public static ICVSFolder getCVSFolderFor(IContainer resource) {
		return new EclipseFolder(resource);
	}


	public static ICVSFile getCVSFileFor(IFile resource) {
		return new EclipseFile(resource);
	}


	public static ICVSResource getCVSResourceFor(IResource resource) {
		if (resource.getType() == IResource.FILE)
			return getCVSFileFor((IFile) resource);
		else
			return getCVSFolderFor((IContainer) resource);
	}
	
	public static ICVSRemoteResource getRemoteResourceFor(IResource resource) throws CVSException {
		ICVSResource managed = getCVSResourceFor(resource);
		return getRemoteResourceFor(managed);
	}
	
	public static ICVSRemoteResource getRemoteResourceFor(ICVSResource resource) throws CVSException {
		if (resource.isFolder()) {
			ICVSFolder folder = (ICVSFolder)resource;
			FolderSyncInfo syncInfo = folder.getFolderSyncInfo();
			if (syncInfo != null) {
				return new RemoteFolder(null, KnownRepositories.getInstance().getRepository(syncInfo.getRoot()), syncInfo.getRepository(), syncInfo.getTag());
			}
		} else {
			if (resource.isManaged()) {
				RemoteFolder parent = (RemoteFolder)getRemoteResourceFor(resource.getParent());
				if (parent == null) {
					// This could be caused by another thread changing the state in the
					// instant between when we did the managed check and we obtained the 
					// parent handle. If this is the case, isManaged should return false
					// now. If it doesn't, then we should log an error.
					if (resource.isManaged()) {
						CVSProviderPlugin.log(new CVSException(NLS.bind(CVSMessages.CVSWorkspaceRoot_11, new String[] { Util.getFullestPath(resource) }))); //$NON-NLS-1$
					}
				} else {
					return RemoteFile.getBase(parent, (ICVSFile)resource);
				}
			}
		}
		return null;
	}
		
	/*
	 * Helper method that uses the parent of a local resource that has no base to ensure that the resource
	 * wasn't added remotely by a third party
	 */
	private static ICVSRemoteResource getRemoteTreeFromParent(IResource resource, ICVSResource managed, CVSTag tag, IProgressMonitor progress) throws TeamException {
		// If the parent isn't mapped to CVS, there's nothing we can do
		ICVSFolder parent = managed.getParent();
		FolderSyncInfo syncInfo = parent.getFolderSyncInfo();
		if (syncInfo == null) {
			// The parent is managed so just indicate that there is no remote
			return null;
		}
		ICVSRepositoryLocation location = KnownRepositories.getInstance().getRepository(parent.getFolderSyncInfo().getRoot());
		RemoteFolder remoteParent = RemoteFolderTreeBuilder.buildRemoteTree((CVSRepositoryLocation)location, parent, tag, progress);
		ICVSRemoteResource remote = null;
		if (remoteParent != null) {
			try {
				remote = (ICVSRemoteResource)remoteParent.getChild(resource.getName());
			} catch (CVSException e) {
				remote = null;
			}
			// The types need to match or we're in trouble
			if (remote != null && !(remote.isContainer() == managed.isFolder()))
				throw new CVSException(new CVSStatus(CVSStatus.ERROR, NLS.bind(CVSMessages.CVSTeamProvider_typesDiffer, new String[] { resource.getFullPath().toString() }), null)); //$NON-NLS-1$
		}
		return remote;
	}

	public static ICVSRemoteResource getRemoteTree(IResource resource, CVSTag tag, IProgressMonitor progress) throws TeamException {
		return getRemoteTree(resource, tag, false /* cache file contents hint */, progress);
	}
	
	/**
	 * Return the remote tree that corresponds to the given local resource. Return
	 * <code>null</code> if the remote tree doesn't exist remotely or if the local
	 * resource is not mapped to a remote (i.e. is not managed by CVS).
	 * 
	 * @param resource the local resource
	 * @param tag the tag to be queried remotely
	 * @param cacheFileContentsHint hint which indicates whether file contents will be required
	 * @param progress
	 * @return the remote tree or <code>null</code>
	 * @throws TeamException
	 */
	public static ICVSRemoteResource getRemoteTree(IResource resource, CVSTag tag, boolean cacheFileContentsHint, IProgressMonitor progress) throws TeamException {
		ICVSResource managed = CVSWorkspaceRoot.getCVSResourceFor(resource);
		ICVSRemoteResource remote = CVSWorkspaceRoot.getRemoteResourceFor(resource);
		if (remote == null) {
			progress.beginTask(null, 100);
			remote = getRemoteTreeFromParent(resource, managed, tag, Policy.subMonitorFor(progress, 50));
			if (cacheFileContentsHint && remote != null && remote instanceof RemoteFile) {
				RemoteFile file = (RemoteFile)remote;
				// get the storage for the file to ensure that the contents are cached
				file.getStorage(Policy.subMonitorFor(progress, 50));
			}
			progress.done();
		} else if(resource.getType() == IResource.FILE) {
			ICVSRepositoryLocation location = remote.getRepository();
			if (cacheFileContentsHint) {
				remote = FileContentCachingService.buildRemoteTree((CVSRepositoryLocation)location, (ICVSFile)managed, tag, progress);
			} else {
				remote = RemoteFolderTreeBuilder.buildRemoteTree((CVSRepositoryLocation)location, (ICVSFile)managed, tag, progress);
			}
		} else {
			ICVSRepositoryLocation location = remote.getRepository();
			if (cacheFileContentsHint) {
				remote = FileContentCachingService.buildRemoteTree((CVSRepositoryLocation)location, (ICVSFolder)managed, tag, progress);
			} else {
				remote = RemoteFolderTreeBuilder.buildRemoteTree((CVSRepositoryLocation)location, (ICVSFolder)managed, tag, progress);
			}	
		}
		return remote;
	}
	
	public static boolean hasRemote(IResource resource) {
		try {
			ICVSResource cvsResource = getCVSResourceFor(resource);
			int type = resource.getType();
			if(type!=IResource.FILE) {
				if(type==IResource.PROJECT) {
					return ((ICVSFolder)cvsResource).isCVSFolder();
				} else {
					return cvsResource.isManaged();
				}
			} else {
				byte[] syncBytes = ((ICVSFile)cvsResource).getSyncBytes();
				if(syncBytes!=null) {
					return !ResourceSyncInfo.isAddition(syncBytes);
				} else {
					return false;
				}
			}					
		} catch(CVSException e) {
			return false;
		}
	}
	
	public ICVSRepositoryLocation getRemoteLocation() throws CVSException {
		FolderSyncInfo info = localRoot.getFolderSyncInfo();
		if (info == null) {
			throw new CVSException(NLS.bind(CVSMessages.CVSWorkspaceRoot_notCVSFolder, new String[] { localRoot.getName() }));  //$NON-NLS-1$
		}
		return KnownRepositories.getInstance().getRepository(info.getRoot());
	}

	public ICVSFolder getLocalRoot() {
		return localRoot;
	}
	
	
	/**
	 * Return true if the resource is part of a link (i.e. a linked resource or
	 * one of it's children.
	 * 
	 * @param container
	 * @return boolean
	 */
	public static boolean isLinkedResource(IResource resource) {
		// check the resource directly first
		if (resource.isLinked()) return true;
		// projects and root cannot be links
		if (resource.getType() == IResource.PROJECT || resource.getType() == IResource.ROOT) {
			return false;
		}
		// look one level under the project to see if the resource is part of a link
		String linkedParentName = resource.getProjectRelativePath().segment(0);
		IFolder linkedParent = resource.getProject().getFolder(linkedParentName);
		return linkedParent.isLinked();
	}
	
	/**
	 * A resource is considered shared 
	 * @param resource
	 * @return boolean
	 */
	public static boolean isSharedWithCVS(IResource resource) throws CVSException {
		if (!resource.isAccessible()) return false;
		if(isLinkedResource(resource)) return false;
	
		if(RepositoryProvider.getProvider(resource.getProject(), CVSProviderPlugin.getTypeId()) == null) {
			return false;
		}
	
		ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
		if (cvsResource.isManaged()) return true;
		if (!cvsResource.exists()) return false;
		if (cvsResource.isFolder() && ((ICVSFolder) cvsResource).isCVSFolder()) return true;
		if (cvsResource.isIgnored()) return false;
		return cvsResource.getParent().isCVSFolder();
	}
	
	/**
	 * Return whether the given container is an orphaned subtree. An orphaned subtree
	 * is folder (i.e. non-project) that is a CVS folder but is not managed and is not
	 * a linked resource. To know if the resource is a descendant of an orphaned subtree,
	 * the client must invoked this method for each ancestor of a resource.
	 * @param container the container being tested
	 * @return whether the container is an orphaned CVS folder
	 * @throws CVSException
	 */
	public static boolean isOrphanedSubtree(IContainer container) throws CVSException {
		ICVSFolder mFolder = CVSWorkspaceRoot.getCVSFolderFor(container);
		return (mFolder.isCVSFolder() 
				&& ! mFolder.isManaged() 
				&& mFolder.getIResource().getType() == IResource.FOLDER
				&& !isLinkedResource(container));
	}
}
