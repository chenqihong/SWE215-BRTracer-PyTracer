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
 
import java.io.*;
import java.util.*;

import org.eclipse.core.resources.*;
import org.eclipse.core.resources.team.IMoveDeleteHook;
import org.eclipse.core.resources.team.ResourceRuleFactory;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.MultiRule;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.client.*;
import org.eclipse.team.internal.ccvs.core.client.Command.KSubstOption;
import org.eclipse.team.internal.ccvs.core.client.Command.LocalOption;
import org.eclipse.team.internal.ccvs.core.client.listeners.*;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.core.resources.EclipseSynchronizer;
import org.eclipse.team.internal.ccvs.core.syncinfo.*;
import org.eclipse.team.internal.ccvs.core.syncinfo.FolderSyncInfo;
import org.eclipse.team.internal.ccvs.core.syncinfo.ResourceSyncInfo;
import org.eclipse.team.internal.ccvs.core.util.*;
import org.eclipse.team.internal.core.streams.CRLFtoLFInputStream;
import org.eclipse.team.internal.core.streams.LFtoCRLFInputStream;

/**
 * This class acts as both the ITeamNature and the ITeamProvider instances
 * required by the Team core.
 * 
 * The current stat of this class and it's plugin is EXPERIMENTAL.
 * As such, it is subject to change except in it's conformance to the
 * TEAM API which it implements.
 * 
 * Questions:
 * 
 * How should a project/reource rename/move effect the provider?
 * 
 * Currently we always update with -P. Is this OK?
 *  - A way to allow customizable options would be nice
 * 
 * Is the -l option valid for commit and does it work properly for update and commit?
 * 
 * Do we need an IUserInteractionProvider in the CVS core
 * 	- prompt for user info (caching could be separate)
 * 	- get release comments
 * 	- prompt for overwrite of unmanaged files
 * 
 * Need a mechanism for communicating meta-information (provided by Team?)
 * 
 * Should pass null when there are no options for a cvs command
 * 
 * We currently write the files to disk and do a refreshLocal to
 * have them appear in Eclipse. This may be changed in the future.
 */
public class CVSTeamProvider extends RepositoryProvider {

	private static final ResourceRuleFactory RESOURCE_RULE_FACTORY = new ResourceRuleFactory() {
		public ISchedulingRule validateEditRule(IResource[] resources) {
			if (resources.length == 0)
				return null;
			//optimize rule for single file
			if (resources.length == 1)
				return resources[0].isReadOnly() ? parent(resources[0]) : null;
			//need a lock on the parents of all read-only files
			HashSet rules = new HashSet();
			for (int i = 0; i < resources.length; i++)
				if (resources[i].isReadOnly())
					rules.add(parent(resources[i]));
			if (rules.isEmpty())
				return null;
			if (rules.size() == 1)
				return (ISchedulingRule) rules.iterator().next();
			ISchedulingRule[] ruleArray = (ISchedulingRule[]) rules
					.toArray(new ISchedulingRule[rules.size()]);
			return new MultiRule(ruleArray);
		}
	};
	
	private static final boolean IS_CRLF_PLATFORM = Arrays.equals(
		System.getProperty("line.separator").getBytes(), new byte[] { '\r', '\n' }); //$NON-NLS-1$
	
	public static final IStatus OK = new Status(IStatus.OK, CVSProviderPlugin.ID, 0, CVSMessages.ok, null); //$NON-NLS-1$

	private static final int UNIFIED_FORMAT = 0;
	private static final int CONTEXT_FORMAT = 1;
	private static final int STANDARD_FORMAT = 2;
	
	private CVSWorkspaceRoot workspaceRoot;
	private IProject project;
	
	private static MoveDeleteHook moveDeleteHook= new MoveDeleteHook();
	private static IFileModificationValidator fileModificationValidator;
	
	// property used to indicate whether new directories should be discovered for the project
	private final static QualifiedName FETCH_ABSENT_DIRECTORIES_PROP_KEY = 
		new QualifiedName("org.eclipse.team.cvs.core", "fetch_absent_directories");  //$NON-NLS-1$  //$NON-NLS-2$
	// property used to indicate whether the project is configured to use Watch/edit
	private final static QualifiedName WATCH_EDIT_PROP_KEY = 
		new QualifiedName("org.eclipse.team.cvs.core", "watch_edit");  //$NON-NLS-1$  //$NON-NLS-2$
				
	/**
	 * No-arg Constructor for IProjectNature conformance
	 */
	public CVSTeamProvider() {
	}

	/* (non-Javadoc)
	 * @see org.eclipse.core.resources.IProjectNature#deconfigure()
	 */
	public void deconfigure() {
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.core.RepositoryProvider#deconfigured()
	 */
	public void deconfigured() {
		// when a nature is removed from the project, notify the synchronizer that
		// we no longer need the sync info cached. This does not affect the actual CVS
		// meta directories on disk, and will remain unless a client calls unmanage().
		try {
			EclipseSynchronizer.getInstance().deconfigure(getProject(), null);
			internalSetWatchEditEnabled(null);
			internalSetFetchAbsentDirectories(null);
		} catch(CVSException e) {
			// Log the exception and let the disconnect continue
			CVSProviderPlugin.log(e);
		}
		ResourceStateChangeListeners.getListener().projectDeconfigured(getProject());
	}
	/**
	 * @see IProjectNature#getProject()
	 */
	public IProject getProject() {
		return project;
	}

	/**
	 * @see IProjectNature#setProject(IProject)
	 */
	public void setProject(IProject project) {
		this.project = project;
		try {
			this.workspaceRoot = new CVSWorkspaceRoot(project);
			// Ensure that the project has CVS info
			if (workspaceRoot.getLocalRoot().getFolderSyncInfo() == null) {
				CVSProviderPlugin.log(new CVSException(new CVSStatus(CVSStatus.ERROR, NLS.bind(CVSMessages.CVSTeamProvider_noFolderInfo, new String[] { project.getName() })))); //$NON-NLS-1$
			}
		} catch (CVSException e) {
			// Ignore exceptions here. They will be surfaced elsewhere
		}
	}
	
	/** 
	 * Diff the resources with the repository and write the output to the provided 
	 * PrintStream in a form that is usable as a patch. The patch is rooted at the
	 * project.
	 */
	public void diff(IResource resource, LocalOption[] options, PrintStream stream,
		IProgressMonitor progress) throws TeamException {
		
		boolean includeNewFiles = false;
		boolean doNotRecurse = false;
		int format = STANDARD_FORMAT;
		
		// Determine the command root and arguments arguments list
		ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
		ICVSFolder commandRoot;
		String[] arguments;
		if (cvsResource.isFolder()) {
			commandRoot = (ICVSFolder)cvsResource;
			arguments = new String[] {Session.CURRENT_LOCAL_FOLDER};
		} else {
			commandRoot = cvsResource.getParent();
			arguments = new String[] {cvsResource.getName()};
		}

		Session s = new Session(workspaceRoot.getRemoteLocation(), commandRoot);
		progress.beginTask(null, 100);
		try {
			s.open(Policy.subMonitorFor(progress, 20), false /* read-only */);
			Command.DIFF.execute(s,
				Command.NO_GLOBAL_OPTIONS,
				options,
				arguments,
				new DiffListener(stream),
				Policy.subMonitorFor(progress, 80));
		} finally {
			s.close();
			progress.done();
		}
		
		// Append our diff output to the server diff output.
		// Our diff output includes new files and new files in new directories.
			
		for (int i = 0; i < options.length; i++)  {
			LocalOption option = options[i];
			if (option.equals(Diff.INCLUDE_NEWFILES))  {
				includeNewFiles = true;
			} else if (option.equals(Diff.DO_NOT_RECURSE))  {
				doNotRecurse = true;
			} else if (option.equals(Diff.UNIFIED_FORMAT))  {
				format = UNIFIED_FORMAT;
			} else if (option.equals(Diff.CONTEXT_FORMAT))  {
				format = CONTEXT_FORMAT;
			}
		}
		if (includeNewFiles)  {
			newFileDiff(cvsResource, stream, doNotRecurse, format);
		}
	}
	
	/**
	 * This diff adds new files and directories to the stream.
	 * @param resource
	 * @param stream
	 * @param doNotRecurse
	 * @param format
	 * @throws CVSException
	 */
	private void newFileDiff(final ICVSResource resource, final PrintStream stream, final boolean doNotRecurse, final int format) throws CVSException {
		final ICVSFolder rootFolder= resource instanceof ICVSFolder ? (ICVSFolder)resource : resource.getParent();
		resource.accept(new ICVSResourceVisitor() {
			public void visitFile(ICVSFile file) throws CVSException {
				if (!(file.isIgnored() || file.isManaged()))  {
					addFileToDiff(rootFolder, file, stream, format);
				}
			}
			public void visitFolder(ICVSFolder folder) throws CVSException {
				// Even if we are not supposed to recurse we still need to go into
				// the root directory.
				if (!folder.exists() || folder.isIgnored() || (doNotRecurse && !folder.equals(rootFolder)))  {
					return;
				} else  {
					folder.acceptChildren(this);
				}
			}
		});
	}

	private void addFileToDiff(ICVSFolder cmdRoot, ICVSFile file, PrintStream stream, int format) throws CVSException {
		
		String nullFilePrefix = ""; //$NON-NLS-1$
		String newFilePrefix = ""; //$NON-NLS-1$
		String positionInfo = ""; //$NON-NLS-1$
		String linePrefix = ""; //$NON-NLS-1$
		
		String pathString = file.getRelativePath(cmdRoot);

		int lines = 0;
		BufferedReader fileReader = new BufferedReader(new InputStreamReader(file.getContents()));
		try {
			while (fileReader.readLine() != null)  {
				lines++;
			}
		} catch (IOException e) {
			throw CVSException.wrapException(file.getIResource(), NLS.bind(CVSMessages.CVSTeamProvider_errorAddingFileToDiff, new String[] { pathString }), e); //$NON-NLS-1$
		} finally {
			try {
				fileReader.close();
			} catch (IOException e1) {
				//ignore
			}
		}
			
		// Ignore empty files
		if (lines == 0)
			return;
		
		switch (format) {
		case UNIFIED_FORMAT :
			nullFilePrefix = "--- ";	//$NON-NLS-1$
			newFilePrefix = "+++ "; 	//$NON-NLS-1$
			positionInfo = "@@ -0,0 +1," + lines + " @@" ;	//$NON-NLS-1$ //$NON-NLS-2$
			linePrefix = "+"; //$NON-NLS-1$
			break;
			
		case CONTEXT_FORMAT :
			nullFilePrefix = "*** ";	//$NON-NLS-1$
			newFilePrefix = "--- ";		//$NON-NLS-1$
			positionInfo = "--- 1," + lines + " ----";	//$NON-NLS-1$ //$NON-NLS-2$
			linePrefix = "+ ";	//$NON-NLS-1$
			break;
			
		default :
			positionInfo = "0a1," + lines;	//$NON-NLS-1$
		linePrefix = "> ";	//$NON-NLS-1$
					break;
		}
		
		fileReader = new BufferedReader(new InputStreamReader(file.getContents()));
		try {
				
			stream.println("Index: " + pathString);		//$NON-NLS-1$
			stream.println("===================================================================");	//$NON-NLS-1$
			stream.println("RCS file: " + pathString);	//$NON-NLS-1$
			stream.println("diff -N " + pathString);	//$NON-NLS-1$
			
			
			if (format != STANDARD_FORMAT)  {
				stream.println(nullFilePrefix + "/dev/null	1 Jan 1970 00:00:00 -0000");	//$NON-NLS-1$
				// Technically this date should be the local file date but nobody really cares.
				stream.println(newFilePrefix + pathString + "	1 Jan 1970 00:00:00 -0000");	//$NON-NLS-1$
			}
			
			if (format == CONTEXT_FORMAT)  {
				stream.println("***************");	//$NON-NLS-1$
				stream.println("*** 0 ****");		//$NON-NLS-1$
			}
			
			stream.println(positionInfo);
			
			for (int i = 0; i < lines; i++)  {
				stream.print(linePrefix);
				stream.println(fileReader.readLine());
			}
		} catch (IOException e) {
			throw CVSException.wrapException(file.getIResource(), NLS.bind(CVSMessages.CVSTeamProvider_errorAddingFileToDiff, new String[] { pathString }), e); //$NON-NLS-1$
		} finally  {
			try {
				fileReader.close();
			} catch (IOException e1) {
			}
		}
	}
	
	/**
	 * Return the remote location to which the receiver's project is mapped.
	 */
	public ICVSRepositoryLocation getRemoteLocation() throws CVSException {
		try {
			return workspaceRoot.getRemoteLocation();
		} catch (CVSException e) {
			// If we can't get the remote location, we should disconnect since nothing can be done with the provider
			try {
				RepositoryProvider.unmap(project);
			} catch (TeamException ex) {
				CVSProviderPlugin.log(ex);
			}
			// We need to trigger a decorator refresh					
			throw e;
		}
	}
	
	public CVSWorkspaceRoot getCVSWorkspaceRoot() {
		return workspaceRoot;
	}
	
	/*
	 * Generate an exception if the resource is not a child of the project
	 */
	 private void checkIsChild(IResource resource) throws CVSException {
	 	if (!isChildResource(resource))
	 		throw new CVSException(new Status(IStatus.ERROR, CVSProviderPlugin.ID, TeamException.UNABLE, 
	 			NLS.bind(CVSMessages.CVSTeamProvider_invalidResource, (new Object[] {resource.getFullPath().toString(), project.getName()})), 
	 			null));
	 }
	 
	/*
	 * Get the arguments to be passed to a commit or update
	 */
	private String[] getValidArguments(IResource[] resources, LocalOption[] options) throws CVSException {
		List arguments = new ArrayList(resources.length);
		for (int i=0;i<resources.length;i++) {
			checkIsChild(resources[i]);
			IPath cvsPath = resources[i].getFullPath().removeFirstSegments(1);
			if (cvsPath.segmentCount() == 0) {
				arguments.add(Session.CURRENT_LOCAL_FOLDER);
			} else {
				arguments.add(cvsPath.toString());
			}
		}
		return (String[])arguments.toArray(new String[arguments.size()]);
	}
	
	private ICVSResource[] getCVSArguments(IResource[] resources) {
		ICVSResource[] cvsResources = new ICVSResource[resources.length];
		for (int i = 0; i < cvsResources.length; i++) {
			cvsResources[i] = CVSWorkspaceRoot.getCVSResourceFor(resources[i]);
		}
		return cvsResources;
	}
	
	/*
	 * This method expects to be passed an InfiniteSubProgressMonitor
	 */
	public void setRemoteRoot(ICVSRepositoryLocation location, IProgressMonitor monitor) throws TeamException {

		// Check if there is a differnece between the new and old roots	
		final String root = location.getLocation(false);
		if (root.equals(workspaceRoot.getRemoteLocation())) 
			return;
	
		try {
			workspaceRoot.getLocalRoot().run(new ICVSRunnable() {
				public void run(IProgressMonitor progress) throws CVSException {
					try {
						// 256 ticks gives us a maximum of 1024 which seems reasonable for folders is a project
						progress.beginTask(null, 100);
						final IProgressMonitor monitor = Policy.infiniteSubMonitorFor(progress, 100);
						monitor.beginTask(null, 256);  //$NON-NLS-1$
		
						// Visit all the children folders in order to set the root in the folder sync info
						workspaceRoot.getLocalRoot().accept(new ICVSResourceVisitor() {
							public void visitFile(ICVSFile file) throws CVSException {}
							public void visitFolder(ICVSFolder folder) throws CVSException {
								monitor.worked(1);
								FolderSyncInfo info = folder.getFolderSyncInfo();
								if (info != null) {
									monitor.subTask(NLS.bind(CVSMessages.CVSTeamProvider_updatingFolder, new String[] { info.getRepository() })); //$NON-NLS-1$
                                    MutableFolderSyncInfo newInfo = info.cloneMutable();
                                    newInfo.setRoot(root);
									folder.setFolderSyncInfo(newInfo);
									folder.acceptChildren(this);
								}
							}
						});
					} finally {
						progress.done();
					}
				}
			}, monitor);
		} finally {
			monitor.done();
		}
	}
	
	/*
	 * Helper to indicate if the resource is a child of the receiver's project
	 */
	private boolean isChildResource(IResource resource) {
		return resource.getProject().getName().equals(project.getName());
	}
	
	public void configureProject() throws CoreException {
		ResourceStateChangeListeners.getListener().projectConfigured(getProject());
	}
	/**
	 * Sets the keyword substitution mode for the specified resources.
	 * <p>
	 * Applies the following rules in order:<br>
	 * <ul>
	 *   <li>If a file is not managed, skips it.</li>
	 *   <li>If a file is not changing modes, skips it.</li>
	 *   <li>If a file is being changed from binary to text, corrects line delimiters
	 *       then commits it, then admins it.</li>
	 *   <li>If a file is added, changes the resource sync information locally.</li>
	 *   <li>Otherwise commits the file (with FORCE to create a new revision), then admins it.</li>
	 * </ul>
	 * All files that are admin'd are committed with FORCE to prevent other developers from
	 * casually trying to commit pending changes to the repository without first checking out
	 * a new copy.  This is not a perfect solution, as they could just as easily do an UPDATE
	 * and not obtain the new keyword sync info.
	 * </p>
	 * 
	 * @param changeSet a map from IFile to KSubstOption
	 * @param monitor the progress monitor
	 * @return a status code indicating success or failure of the operation
	 * 
	 * @throws TeamException
	 */
	public IStatus setKeywordSubstitution(final Map /* from IFile to KSubstOption */ changeSet,
		final String comment,
		IProgressMonitor monitor) throws TeamException {
		final IStatus[] result = new IStatus[] { ICommandOutputListener.OK };
		workspaceRoot.getLocalRoot().run(new ICVSRunnable() {
			public void run(final IProgressMonitor monitor) throws CVSException {
				final Map /* from KSubstOption to List of String */ filesToAdmin = new HashMap();
				final Collection /* of ICVSFile */ filesToCommitAsText = new HashSet(); // need fast lookup
				final boolean useCRLF = IS_CRLF_PLATFORM && (CVSProviderPlugin.getPlugin().isUsePlatformLineend());
		
				/*** determine the resources to be committed and/or admin'd ***/
				for (Iterator it = changeSet.entrySet().iterator(); it.hasNext();) {
					Map.Entry entry = (Map.Entry) it.next();
					IFile file = (IFile) entry.getKey();
					KSubstOption toKSubst = (KSubstOption) entry.getValue();

					// only set keyword substitution if resource is a managed file
					checkIsChild(file);
					ICVSFile mFile = CVSWorkspaceRoot.getCVSFileFor(file);
					if (! mFile.isManaged()) continue;
					
					// only set keyword substitution if new differs from actual
					byte[] syncBytes = mFile.getSyncBytes();
					KSubstOption fromKSubst = ResourceSyncInfo.getKeywordMode(syncBytes);
					if (toKSubst.equals(fromKSubst)) continue;
					
					// change resource sync info immediately for an outgoing addition
					if (ResourceSyncInfo.isAddition(syncBytes)) {
						mFile.setSyncBytes(ResourceSyncInfo.setKeywordMode(syncBytes, toKSubst), ICVSFile.UNKNOWN);
						continue;
					}

					// nothing do to for deletions
					if (ResourceSyncInfo.isDeletion(syncBytes)) continue;

					// file exists remotely so we'll have to commit it
					if (fromKSubst.isBinary() && ! toKSubst.isBinary()) {
						// converting from binary to text
						cleanLineDelimiters(file, useCRLF, new NullProgressMonitor()); // XXX need better progress monitoring
						// remember to commit the cleaned resource as text before admin
						filesToCommitAsText.add(mFile);
					}
					// remember to admin the resource
					List list = (List) filesToAdmin.get(toKSubst);
					if (list == null) {
						list = new ArrayList();
						filesToAdmin.put(toKSubst, list);
					}
					list.add(mFile);
				}
			
				/*** commit then admin the resources ***/
				// compute the total work to be performed
				int totalWork = filesToCommitAsText.size() + 1;
				for (Iterator it = filesToAdmin.values().iterator(); it.hasNext();) {
					List list = (List) it.next();
					totalWork += list.size();
					totalWork += 1; // Add 1 for each connection that needs to be made
				}
				if (totalWork != 0) {
					monitor.beginTask(CVSMessages.CVSTeamProvider_settingKSubst, totalWork); //$NON-NLS-1$
					try {
						// commit files that changed from binary to text
						// NOTE: The files are committed as text with conversions even if the
						//       resource sync info still says "binary".
						if (filesToCommitAsText.size() != 0) {
							Session session = new Session(workspaceRoot.getRemoteLocation(), workspaceRoot.getLocalRoot(), true /* output to console */);
							session.open(Policy.subMonitorFor(monitor, 1), true /* open for modification */);
							try {
								String keywordChangeComment = comment;
								if (keywordChangeComment == null || keywordChangeComment.length() == 0)
									keywordChangeComment = CVSMessages.CVSTeamProvider_changingKeywordComment; //$NON-NLS-1$
								result[0] = Command.COMMIT.execute(
									session,
									Command.NO_GLOBAL_OPTIONS,
									new LocalOption[] { Commit.DO_NOT_RECURSE, Commit.FORCE,
										Commit.makeArgumentOption(Command.MESSAGE_OPTION, keywordChangeComment) },
									(ICVSResource[]) filesToCommitAsText.toArray(new ICVSResource[filesToCommitAsText.size()]),
									filesToCommitAsText,
									null, 
									Policy.subMonitorFor(monitor, filesToCommitAsText.size()));
							} finally {
								session.close();
							}

							// if errors were encountered, abort
							if (! result[0].isOK()) return;
						}
						
						// admin files that changed keyword substitution mode
						// NOTE: As confirmation of the completion of a command, the server replies
						//       with the RCS command output if a change took place.  Rather than
						//       assume that the command succeeded, we listen for these lines
						//       and update the local ResourceSyncInfo for the particular files that
						//       were actually changed remotely.
						for (Iterator it = filesToAdmin.entrySet().iterator(); it.hasNext();) {
							Map.Entry entry = (Map.Entry) it.next();
							final KSubstOption toKSubst = (KSubstOption) entry.getKey();
							final List list = (List) entry.getValue();
							// do it
							Session session = new Session(workspaceRoot.getRemoteLocation(), workspaceRoot.getLocalRoot(), true /* output to console */);
							session.open(Policy.subMonitorFor(monitor, 1), true /* open for modification */);
							try {
								result[0] = Command.ADMIN.execute(
									session,
									Command.NO_GLOBAL_OPTIONS,
									new LocalOption[] { toKSubst },
									(ICVSResource[]) list.toArray(new ICVSResource[list.size()]),
									new AdminKSubstListener(toKSubst),
									Policy.subMonitorFor(monitor, list.size()));
							} finally {
								session.close();
							}
							// if errors were encountered, abort
							if (! result[0].isOK()) return;
						}
					} finally {
						monitor.done();
					}
				}
			}
		}, Policy.monitorFor(monitor));
		return result[0];
	}
	
	/**
	 * This method translates the contents of a file from binary into text (ASCII).
	 * Fixes the line delimiters in the local file to reflect the platform's
	 * native encoding.  Performs CR/LF -> LF or LF -> CR/LF conversion
	 * depending on the platform but does not affect delimiters that are
	 * already correctly encoded.
	 */
	public static void cleanLineDelimiters(IFile file, boolean useCRLF, IProgressMonitor progress)
		throws CVSException {
		try {
			// convert delimiters in memory
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			InputStream is = new BufferedInputStream(file.getContents());
			try {
				// Always convert CR/LF into LFs
				is = new CRLFtoLFInputStream(is);
				if (useCRLF) {
					// For CR/LF platforms, translate LFs to CR/LFs
					is = new LFtoCRLFInputStream(is);
				}
				for (int b; (b = is.read()) != -1;) bos.write(b);
				bos.close();
			} finally {
				is.close();
			}
			// write file back to disk with corrected delimiters if changes were made
			ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
			file.setContents(bis, false /*force*/, false /*keepHistory*/, progress);
		} catch (CoreException e) {
			throw CVSException.wrapException(file, CVSMessages.CVSTeamProvider_cleanLineDelimitersException, e); //$NON-NLS-1$
		} catch (IOException e) {
			throw CVSException.wrapException(file, CVSMessages.CVSTeamProvider_cleanLineDelimitersException, e); //$NON-NLS-1$
		}
	}
	
	/*
	 * @see RepositoryProvider#getID()
	 */
	public String getID() {
		return CVSProviderPlugin.getTypeId();
	}
	
	/*
	 * @see RepositoryProvider#getMoveDeleteHook()
	 */
	public IMoveDeleteHook getMoveDeleteHook() {
		return moveDeleteHook;
	}
	 
	/**
	 * @see org.eclipse.team.core.RepositoryProvider#getFileModificationValidator()
	 */
	public IFileModificationValidator getFileModificationValidator() {
		if (CVSTeamProvider.fileModificationValidator == null) {
			CVSTeamProvider.fileModificationValidator = new CVSCoreFileModificationValidator();
		}
		return CVSTeamProvider.fileModificationValidator;
	}
	
	/**
	 * Checkout (cvs edit) the provided resources so they can be modified locally and committed.
	 * This will make any read-only resources in the list writable and will notify the server
	 * that the file is being edited. This notification may be done immediately or at some 
	 * later point depending on whether contact with the server is possble at the time of 
	 * invocation or the value of the notify server parameter.
	 * 
	 * The recurse parameter is equivalent to the cvs local options -l (<code>true</code>) and 
	 * -R (<code>false</code>). The notifyServer parameter can be used to defer server contact
	 * until the next command. This may be approrpiate if no shell or progress monitor is available
	 * to the caller. The notification bit field indicates what temporary watches are to be used while
	 * the file is being edited. The possible values that can be ORed together are ICVSFile.EDIT, 
	 * ICVSFile.UNEDIT and ICVSFile.COMMIT. There pre-ORed convenience values ICVSFile.NO_NOTIFICATION
	 * and ICVSFile.NOTIFY_ON_ALL are also available.
	 * 
	 * @param resources the resources to be edited
	 * @param recurse indicates whether to recurse (-R) or not (-l)
	 * @param notifyServer indicates whether to notify the server now, if possible,
	 *     or defer until the next command.
	 * @param notifyForWrittable 
	 * @param notification the temporary watches.
	 * @param progress progress monitor to provide progress indication/cancellation or <code>null</code>
	 * @exception CVSException if this method fails.
	 * @since 2.1
	 * 
	 * @see CVSTeamProvider#unedit
	 */
	public void edit(IResource[] resources, boolean recurse, boolean notifyServer, final boolean notifyForWritable, final int notification, IProgressMonitor progress) throws CVSException {
		notifyEditUnedit(resources, recurse, notifyServer, new ICVSResourceVisitor() {
			public void visitFile(ICVSFile file) throws CVSException {
				if (notifyForWritable || file.isReadOnly())
					file.edit(notification, notifyForWritable, Policy.monitorFor(null));
			}
			public void visitFolder(ICVSFolder folder) throws CVSException {
				// nothing needs to be done here as the recurse will handle the traversal
			}
		}, null /* no scheduling rule */, progress);
	}
	
	/**
	 * Unedit the given resources. Any writtable resources will be reverted to their base contents
	 * and made read-only and the server will be notified that the file is no longer being edited.
	 * This notification may be done immediately or at some 
	 * later point depending on whether contact with the server is possble at the time of 
	 * invocation or the value of the notify server parameter.
	 * 
	 * The recurse parameter is equivalent to the cvs local options -l (<code>true</code>) and 
	 * -R (<code>false</code>). The notifyServer parameter can be used to defer server contact
	 * until the next command. This may be approrpiate if no shell or progress monitor is available
	 * to the caller.
	 * 
	 * @param resources the resources to be unedited
	 * @param recurse indicates whether to recurse (-R) or not (-l)
	 * @param notifyServer indicates whether to notify the server now, if possible,
	 *     or defer until the next command.
	 * @param progress progress monitor to provide progress indication/cancellation or <code>null</code>
	 * @exception CVSException if this method fails.
	 * @since 2.1
	 * 
	 * @see CVSTeamProvider#edit
	 */
	public void unedit(IResource[] resources, boolean recurse, boolean notifyServer, IProgressMonitor progress) throws CVSException {
		notifyEditUnedit(resources, recurse, notifyServer, new ICVSResourceVisitor() {
			public void visitFile(ICVSFile file) throws CVSException {
				if (!file.isReadOnly())
					file.unedit(Policy.monitorFor(null));
			}
			public void visitFolder(ICVSFolder folder) throws CVSException {
				// nothing needs to be done here as the recurse will handle the traversal
			}
		}, getProject() /* project scheduling rule */, progress);
	}
	
	/*
	 * This method captures the common behavior between the edit and unedit methods.
	 */
	private void notifyEditUnedit(final IResource[] resources, final boolean recurse, final boolean notifyServer, final ICVSResourceVisitor editUneditVisitor, ISchedulingRule rule, IProgressMonitor monitor) throws CVSException {
		final CVSException[] exception = new CVSException[] { null };
		IWorkspaceRunnable workspaceRunnable = new IWorkspaceRunnable() {
			public void run(IProgressMonitor monitor) throws CoreException {
				final ICVSResource[] cvsResources = getCVSArguments(resources);
				
				// mark the files locally as being checked out
				try {
					for (int i = 0; i < cvsResources.length; i++) {
						cvsResources[i].accept(editUneditVisitor, recurse);
					}
				} catch (CVSException e) {
					exception[0] = e;
					return;
				}
				
				// send the noop command to the server in order to deliver the notifications
				if (notifyServer) {
					monitor.beginTask(null, 100);
					Session session = new Session(workspaceRoot.getRemoteLocation(), workspaceRoot.getLocalRoot(), true);
					try {
						try {
							session.open(Policy.subMonitorFor(monitor, 10), true /* open for modification */);
						} catch (CVSException e1) {
							// If the connection cannot be opened, just exit normally.
							// The notifications will be sent when a connection can be made
							return;
						}
						Command.NOOP.execute(
							session,
							Command.NO_GLOBAL_OPTIONS, 
							Command.NO_LOCAL_OPTIONS, 
							cvsResources, 
							null, 
							Policy.subMonitorFor(monitor, 90));
					} catch (CVSException e) {
						exception[0] = e;
					} finally {
						session.close();
						monitor.done();
					}
				}
			}
		};
		try {
			ResourcesPlugin.getWorkspace().run(workspaceRunnable, rule, 0, Policy.monitorFor(monitor));
		} catch (CoreException e) {
			if (exception[0] == null) {
				throw CVSException.wrapException(e);
			} else {
				CVSProviderPlugin.log(CVSException.wrapException(e));
			}
		}
		if (exception[0] != null) {
			throw exception[0];
		}
	}
	
	/**
	 * Gets the etchAbsentDirectories.
	 * @return Returns a boolean
	 */
	public boolean getFetchAbsentDirectories() throws CVSException {
		try {
			String property = getProject().getPersistentProperty(FETCH_ABSENT_DIRECTORIES_PROP_KEY);
			if (property == null) return CVSProviderPlugin.getPlugin().getFetchAbsentDirectories();
			return Boolean.valueOf(property).booleanValue();
		} catch (CoreException e) {
			throw new CVSException(new CVSStatus(IStatus.ERROR, NLS.bind(CVSMessages.CVSTeamProvider_errorGettingFetchProperty, new String[] { project.getName() }), e)); //$NON-NLS-1$
		}
	}
	
	/**
	 * Sets the fetchAbsentDirectories.
	 * @param etchAbsentDirectories The etchAbsentDirectories to set
	 */
	public void setFetchAbsentDirectories(boolean fetchAbsentDirectories) throws CVSException {
		internalSetFetchAbsentDirectories(fetchAbsentDirectories ? Boolean.TRUE.toString() : Boolean.FALSE.toString());
	}

	public void internalSetFetchAbsentDirectories(String fetchAbsentDirectories) throws CVSException {
		try {
			getProject().setPersistentProperty(FETCH_ABSENT_DIRECTORIES_PROP_KEY, fetchAbsentDirectories);
		} catch (CoreException e) {
			throw new CVSException(new CVSStatus(IStatus.ERROR, NLS.bind(CVSMessages.CVSTeamProvider_errorSettingFetchProperty, new String[] { project.getName() }), e)); //$NON-NLS-1$
		}
	}
	
	/**
	 * @see org.eclipse.team.core.RepositoryProvider#canHandleLinkedResources()
	 */
	public boolean canHandleLinkedResources() {
		return true;
	}

	/**
	 * @see org.eclipse.team.core.RepositoryProvider#validateCreateLink(org.eclipse.core.resources.IResource, int, org.eclipse.core.runtime.IPath)
	 */
	public IStatus validateCreateLink(IResource resource, int updateFlags, IPath location) {
		ICVSFolder cvsFolder = CVSWorkspaceRoot.getCVSFolderFor(resource.getParent().getFolder(new Path(resource.getName())));
		try {
			if (cvsFolder.isCVSFolder()) {
				// There is a remote folder that overlaps with the link so disallow
				return new CVSStatus(IStatus.ERROR, NLS.bind(CVSMessages.CVSTeamProvider_overlappingRemoteFolder, new String[] { resource.getFullPath().toString() })); //$NON-NLS-1$
			} else {
				ICVSFile cvsFile = CVSWorkspaceRoot.getCVSFileFor(resource.getParent().getFile(new Path(resource.getName())));
				if (cvsFile.isManaged()) {
					// there is an outgoing file deletion that overlaps the link so disallow
					return new CVSStatus(IStatus.ERROR, NLS.bind(CVSMessages.CVSTeamProvider_overlappingFileDeletion, new String[] { resource.getFullPath().toString() })); //$NON-NLS-1$
				}
			}
		} catch (CVSException e) {
			CVSProviderPlugin.log(e);
			return e.getStatus();
		}

		return super.validateCreateLink(resource, updateFlags, location);
	}
	
	/**
	 * Get the editors of the resources by calling the <code>cvs editors</code> command.
	 * 
	 * @author <a href="mailto:gregor.kohlwes@csc.com,kohlwes@gmx.net">Gregor Kohlwes</a>
	 * @param resources
	 * @param progress
	 * @return IEditorsInfo[]
	 * @throws CVSException
	 */
	public EditorsInfo[] editors(
		IResource[] resources,
		IProgressMonitor progress)
		throws CVSException {

		// Build the local options
		LocalOption[] commandOptions = new LocalOption[] {
		};
		progress.worked(10);
		// Build the arguments list
		String[] arguments = getValidArguments(resources, commandOptions);

		// Build the listener for the command
		EditorsListener listener = new EditorsListener();

		// Check if canceled
		if (progress.isCanceled()) {
			return new EditorsInfo[0];
		}
		// Build the session
		Session session =
			new Session(
				workspaceRoot.getRemoteLocation(),
				workspaceRoot.getLocalRoot());

		// Check if canceled
		if (progress.isCanceled()) {
			return new EditorsInfo[0];
		}
		progress.beginTask(null, 100);
		try {
			// Opening the session takes 20% of the time
			session.open(Policy.subMonitorFor(progress, 20), false /* read-only */);

			if (!progress.isCanceled()) {
				// Execute the editors command
				Command.EDITORS.execute(
					session,
					Command.NO_GLOBAL_OPTIONS,
					commandOptions,
					arguments,
					listener,
					Policy.subMonitorFor(progress, 80));
			}
		} finally {
			session.close();
			progress.done();
		}
		// Return the infos about the editors
		return listener.getEditorsInfos();
	}

	/**
	 * Return the commit comment template that was provided by the server.
	 * 
	 * @return String
	 * @throws CVSException
	 */
	public String getCommitTemplate() throws CVSException {
		ICVSFolder localFolder = getCVSWorkspaceRoot().getLocalRoot();
		ICVSFile templateFile = CVSWorkspaceRoot.getCVSFileFor(
			SyncFileWriter.getTemplateFile(
				(IContainer)localFolder.getIResource()));
		if (!templateFile.exists()) return null;
		InputStream in = new BufferedInputStream(templateFile.getContents());
		try {
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			int b;
			do {
				b = in.read();
				if (b != -1)
					out.write((byte)b);
			} while (b != -1);
			out.close();
			return new String(out.toString());
		} catch (IOException e) {
			throw CVSException.wrapException(e);
		} finally {
			try {
				in.close();
			} catch (IOException e) {
				// Since we already have the contents, just log this exception
				CVSProviderPlugin.log(CVSException.wrapException(e));
			}
		}
	}
	
	/**
	 * Return true if the project is configured to use watch/edit. A project will use 
	 * watch/edit if it was checked out when the global preference to use watch/edit is
	 * turned on.
	 * @return boolean
	 */
	public boolean isWatchEditEnabled() throws CVSException {
		IProject project = getProject();
		try {
			String property = (String)project.getSessionProperty(WATCH_EDIT_PROP_KEY);
			if (property == null) {
				property = project.getPersistentProperty(WATCH_EDIT_PROP_KEY);
				if (property == null) {
					// The persistant property for the project was never set (i.e. old project)
					// Use the global preference to determine if the project is using watch/edit
					return CVSProviderPlugin.getPlugin().isWatchEditEnabled();
				} else {
					project.setSessionProperty(WATCH_EDIT_PROP_KEY, property);
				}
			}
			return Boolean.valueOf(property).booleanValue();
		} catch (CoreException e) {
			if (project.isAccessible()) {
				// We only care if the project still exists
				throw new CVSException(new CVSStatus(IStatus.ERROR, NLS.bind(CVSMessages.CVSTeamProvider_errorGettingWatchEdit, new String[] { project.getName() }), e)); //$NON-NLS-1$
			}
		}
		return false;
	}
	
	public void setWatchEditEnabled(boolean enabled) throws CVSException {
		internalSetWatchEditEnabled(enabled ? Boolean.TRUE.toString() : Boolean.FALSE.toString());
	}
	
	private void internalSetWatchEditEnabled(String enabled) throws CVSException {
		try {
			IProject project = getProject();
			project.setPersistentProperty(WATCH_EDIT_PROP_KEY, enabled);
			project.setSessionProperty(WATCH_EDIT_PROP_KEY, enabled);
		} catch (CoreException e) {
			throw new CVSException(new CVSStatus(IStatus.ERROR, NLS.bind(CVSMessages.CVSTeamProvider_errorSettingWatchEdit, new String[] { project.getName() }), e)); //$NON-NLS-1$
		}
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.core.RepositoryProvider#getRuleFactory()
	 */
	public IResourceRuleFactory getRuleFactory() {
		return RESOURCE_RULE_FACTORY;
	}
}
