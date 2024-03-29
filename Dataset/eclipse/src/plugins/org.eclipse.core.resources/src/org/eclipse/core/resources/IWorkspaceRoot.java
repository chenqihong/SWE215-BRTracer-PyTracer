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
package org.eclipse.core.resources;

import org.eclipse.core.runtime.*;

/**
 * A root resource represents the top of the resource hierarchy in a workspace.
 * There is exactly one root in a workspace.  The root resource has the following
 * behavior: 
 * <ul>
 * <li>It cannot be moved or copied </li>
 * <li>It always exists.</li>
 * <li>Deleting the root deletes all of the children under the root but leaves the root itself</li>
 * <li>It is always local.</li>
 * <li>It is never a phantom.</li>
 * </ul>
 * <p>
 * This interface is not intended to be implemented by clients.
 * </p>
 * <p>
 * Workspace roots implement the <code>IAdaptable</code> interface;
 * extensions are managed by the platform's adapter manager.
 * </p>
 *
 * @see Platform#getAdapterManager()
 */
public interface IWorkspaceRoot extends IContainer, IAdaptable {

	/**
	 * Deletes everything in the workspace except the workspace root resource
	 * itself.
	 * <p>
	 * This is a convenience method, fully equivalent to:
	 * <pre>
	 *   delete(
	 *     (deleteContent ? IResource.ALWAYS_DELETE_PROJECT_CONTENT : IResource.NEVER_DELETE_PROJECT_CONTENT )
	 *        | (force ? FORCE : IResource.NONE),
	 *     monitor);
	 * </pre>
	 * </p>
	 * <p>
	 * This method changes resources; these changes will be reported
	 * in a subsequent resource change event.
	 * </p>
	 * <p>
	 * This method is long-running; progress and cancellation are provided
	 * by the given progress monitor.
	 * </p>
	 *
	 * @param deleteContent a flag controlling how whether content is
	 *    aggressively deleted
	 * @param force a flag controlling whether resources that are not
	 *    in sync with the local file system will be tolerated
	 * @param monitor a progress monitor, or <code>null</code> if progress
	 *    reporting is not desired
	 * @exception CoreException if this method fails. Reasons include:
	 * <ul>
	 * <li> A project could not be deleted.</li>
	 * <li> A project's contents could not be deleted.</li>
	 * <li> Resource changes are disallowed during certain types of resource change 
	 *       event notification. See <code>IResourceChangeEvent</code> for more details.</li>
	 * </ul>
	 * @exception OperationCanceledException if the operation is canceled. 
	 * Cancelation can occur even if no progress monitor is provided.
	 * @see IResource#delete(int,IProgressMonitor)
	 */
	public void delete(boolean deleteContent, boolean force, IProgressMonitor monitor) throws CoreException;

	/**
	 * Returns the handles to all the resources (workspace root, project, folder) in
	 * the workspace which are mapped to the given path in the local file system.
	 * Returns an empty array if there are none.
	 * <p>
	 * If the path maps to the platform working location, the returned object will
	 * be a single element array consisting of an object of type <code>ROOT</code>.
	 * <p>
	 *  If the path maps to a project, the resulting object will be a single
	 * element array consisting of an object of type <code>PROJECT</code>; 
	 * otherwise the resulting array will contain folders (type
	 * <code>FOLDER</code>). 
	 * <p>
	 * The path must be absolute; its segments need not be valid names; a
	 * trailing separator is ignored. The resulting resource(s) need not exist in the
	 * workspace.
	 * <p>
	 * @param location a path in the local file system
	 * @return the corresponding containers in the workspace, or an empty array if none
	 * @since 2.1
	 */
	public IContainer[] findContainersForLocation(IPath location);

	/**
	 * Returns the handles of all files that are mapped to the given path 
	 * in the local file system.  Returns an empty array if there are none.
	 * The path must be absolute; its segments need not be valid names.
	 * The resulting file(s) need not exist in the workspace.
	 * <p>
	 * @param location a path in the local file system
	 * @return the corresponding files in the workspace, or an empty array if none
	 * @since 2.1
	 */
	public IFile[] findFilesForLocation(IPath location);

	/**
	 * Returns a handle to the  workspace root, project or folder 
	 * which is mapped to the given path
	 * in the local file system, or <code>null</code> if none.
	 * If the path maps to the platform working location, the returned object
	 * will be of type <code>ROOT</code>.  If the path maps to a 
	 * project, the resulting object will be
	 * of type <code>PROJECT</code>; otherwise the resulting object 
	 * will be a folder (type <code>FOLDER</code>).
	 * The path must be absolute; its segments need not be valid names;
	 * a trailing separator is ignored.
	 * The resulting resource need not exist in the workspace.
	 * <p>
	 * This method returns null when the given file system location is not equal to 
	 * or under the location of any existing project in the workspace, or equal to the 
	 * location of the platform working location.
	 * </p>
	 * <p>
	 * Warning: This method ignores linked resources and their children.  Since
	 * linked resources may overlap other resources, a unique mapping from a
	 * file system location to a single resource is not guaranteed.  To find all 
	 * resources for a given location, including linked resources, use the method
	 * <code>findContainersForLocation</code>.
	 * </p>
	 * 
	 * @param location a path in the local file system
	 * @return the corresponding project or folder in the workspace,
	 *    or <code>null</code> if none
	 */
	public IContainer getContainerForLocation(IPath location);

	/**
	 * Returns a handle to the file which is mapped to the given path 
	 * in the local file system, or <code>null</code> if none.
	 * The path must be absolute; its segments need not be valid names.
	 * The resulting file need not exist in the workspace.
	 * <p>
	 * This method returns null when the given file system location is not under
	 * the location of any existing project in the workspace.
	 * </p>
	 * <p>
	 * Warning: This method ignores linked resources and their children.  Since
	 * linked resources may overlap other resources, a unique mapping from a
	 * file system location to a single resource is not guaranteed.  To find all 
	 * resources for a given location, including linked resources, use the method
	 * <code>findFilesForLocation</code>.
	 * </p>
	 *
	 * @param location a path in the local file system
	 * @return the corresponding file in the workspace,
	 *    or <code>null</code> if none
	 */
	public IFile getFileForLocation(IPath location);

	/**
	 * Returns a handle to the project resource with the given name
	 * which is a child of this root.
	 * <p>
	 * Note: This method deals exclusively with resource handles, 
	 * independent of whether the resources exist in the workspace.
	 * The validation check on the project name is not done
	 * when the project handle is constructed; rather, it is done
	 * automatically as the project is created.
	 * </p>
	 * 
	 * @param name the name of the project 
	 * @return a project resource handle
	 * @see #getProjects()
	 */
	public IProject getProject(String name);

	/**
	 * Returns the collection of projects which exist under this root.
	 * The projects can be open or closed.
	 * 
	 * @return an array of projects
	 * @see #getProject(String)
	 */
	public IProject[] getProjects();
}
