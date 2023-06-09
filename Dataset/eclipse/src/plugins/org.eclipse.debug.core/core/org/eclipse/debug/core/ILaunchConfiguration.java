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
package org.eclipse.debug.core;


import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * A launch configuration describes how to launch an application.
 * Each launch configuration is an instance of a type of launch
 * configuration as described by a launch configuration type 
 * extension. Each launch configuration has a launch configuration
 * delegate which performs the actual launching of a
 * configuration.
 * <p>
 * A launch configuration may be shared in a repository via
 * standard VCM mechanisms, or may be stored locally, essentially
 * making the launch configuration private for a single user.
 * Thus, a launch configuration may stored as a file in the
 * workspace (shared), or as a file in the debug plug-in's state
 * location.
 * </p>
 * A launch configuration is a handle to its underlying storage.
 * Methods annotated as "handle-only" do not require a configuration
 * to exist. Methods that require an underlying configuration to exist
 * throw a <code>CoreException</code> when an underlying configuration
 * is missing.
 * </p>
 * <p>
 * A launch configuration is modified by obtaining a working copy
 * of a launch configuration, modifying the working copy, and then
 * saving the working copy.
 * </p>
 * <p>
 * This interface is not intended to be implemented by clients. Clients
 * that define a launch configuration delegate extension implement the
 * <code>ILaunchConfigurationDelegate</code> interface.
 * </p>
 * @see ILaunchConfigurationType
 * @see org.eclipse.debug.core.model.ILaunchConfigurationDelegate
 * @see ILaunchConfigurationWorkingCopy
 * @since 2.0
 */
public interface ILaunchConfiguration extends IAdaptable {
	
	/**
	 * The file extension for launch configuration files
	 * (value <code>"launch"</code>).
	 */
	public static final String LAUNCH_CONFIGURATION_FILE_EXTENSION = "launch"; //$NON-NLS-1$
	
	/**
	 * Launch configuration attribute storing an identifier of
	 * a persistable source locator extension. When this attribute is
	 * specified, a new source locator will be created automatically and
	 * associated with the launch for this configuration.
	 * 
	 * @see org.eclipse.debug.core.model.IPersistableSourceLocator
	 */
	public static final String ATTR_SOURCE_LOCATOR_ID = DebugPlugin.getUniqueIdentifier() + ".source_locator_id"; //$NON-NLS-1$
	
	/**
	 * Launch configuration attribute storing a memento of a 
	 * source locator. When this attribute is specified in
	 * conjunction with a source locator id, the source locator
	 * created for a launch will be initialized with this memento.
	 * When not specified, but a source locator id is specified,
	 * the source locator will be initialized to default values.
	 * 
	 * @see org.eclipse.debug.core.model.IPersistableSourceLocator
	 */
	public static final String ATTR_SOURCE_LOCATOR_MEMENTO = DebugPlugin.getUniqueIdentifier() + ".source_locator_memento"; //$NON-NLS-1$
	
	/**
	 * Launches this configuration in the specified mode by delegating to
	 * this configuration's launch configuration delegate, and returns the
	 * resulting launch.
	 * <p>
	 * Equivalent to calling <code>launch(String, IProgressMontitor, boolean)</code>
	 * with a <code>build</code> flag of <code>false</code>.
	 * </p>
	 * @param mode the mode in which to launch, one of the mode constants
	 *  defined by <code>ILaunchManager</code> - <code>RUN_MODE</code> or <code>DEBUG_MODE</code>.
	 * @param monitor progress monitor, or <code>null</code>. Since 3.0, this
	 *  parameter is ignored. A cancellable progress monitor is provided by the Job
	 *  framework.
	 * @return the resulting launch
	 * @exception CoreException if this method fails. Reasons include:<ul>
	 * <li>unable to instantiate the underlying launch configuration delegate</li>
	 * <li>the launch fails (in the delegate)</code>
	 * </ul>
	 */
	public ILaunch launch(String mode, IProgressMonitor monitor) throws CoreException;
	
	/**
	 * Launches this configuration in the specified mode by delegating to
	 * this configuration's launch configuration delegate, and returns the
	 * resulting launch.
	 * <p>
	 * If this configuration's launch delegate implements
	 * <code>ILaunchConfigurationDelegate2</code>, the launch delegate will
	 * be consulted to provide a launch object for the launch,
	 * perform pre-launch checks, and build before the launch.
	 * If <code>build</code> is <code>true</code> and the associated launch
	 * delegate does not implement <code>ILaunchConfigurationDelegate2</code>
	 * an incremental workspace build will be performed before the launch
	 * by the debug platform.
	 * </p>
	 * <p>
	 * The resulting launch object is registered with the launch manager
	 * before passing it to this configuration's delegate launch method, for
	 * contributions (debug targets and processes).
	 * </p>
	 * <p>
	 * If the delegate contributes a source locator to the launch, that
	 * source locator is used. Otherwise an appropriate source locator is
	 * contributed to the launch  based on the values of
	 * <code>ATTR_SOURCE_LOCATOR_ID</code> and
	 * <code>ATTR_SOURCE_LOCATOR_MEMENTO</code>. If the launch is cancelled (via
	 * the given progress monitor), the launch is removed from the launch
	 * manager. The launch is returned whether cancelled or not. Invoking this
	 * method causes the underlying launch configuration delegate to be
	 * instantiated (if not already).
	 * </p>
	 * @param mode the mode in which to launch, one of the mode constants
	 *  defined by <code>ILaunchManager</code> - <code>RUN_MODE</code> or <code>DEBUG_MODE</code>.
	 * @param monitor progress monitor, or <code>null</code>. Since 3.0, this
	 *  parameter is ignored. A cancellable progress monitor is provided by the Job
	 *  framework.
	 * @param build whether the workspace should be built before the launch
	 * @return resulting launch
	 * @throws CoreException if an exception occurrs during the launch sequence
	 * @since 3.0
	 */
	public ILaunch launch(String mode, IProgressMonitor monitor, boolean build) throws CoreException;
	
	/**
	 * Launches this configuration in the specified mode by delegating to
	 * this configuration's launch configuration delegate, and returns the
	 * resulting launch.
	 * <p>
	 * If this configuration's launch delegate implements
	 * <code>ILaunchConfigurationDelegate2</code>, the launch delegate will
	 * be consulted to provide a launch object for the launch,
	 * perform pre-launch checks, and build before the launch.
	 * If <code>build</code> is <code>true</code> and the associated launch
	 * delegate does not implement <code>ILaunchConfigurationDelegate2</code>
	 * an incremental workspace build will be performed before the launch
	 * by the debug platform.
	 * </p>
	 * <p>
	 * When <code>register</code> is <code>true</code>, the resulting launch object
	 * is registered with the launch manager before passing it to this configuration's delegate
	 * launch method, for contributions (debug targets and processes). When
	 * <code>register</code> is <code>false</code>, the launch is not registered with
	 * the launch manager. Clients that launch configurations without registering
	 * a launch should register appropiate debug event filters to intercept events
	 * from unregistered launches.
	 * </p>
	 * <p>
	 * If the delegate contributes a source locator to the launch, that
	 * source locator is used. Otherwise an appropriate source locator is
	 * contributed to the launch  based on the values of
	 * <code>ATTR_SOURCE_LOCATOR_ID</code> and
	 * <code>ATTR_SOURCE_LOCATOR_MEMENTO</code>. If the launch is cancelled (via
	 * the given progress monitor), the launch is removed from the launch
	 * manager. The launch is returned whether cancelled or not. Invoking this
	 * method causes the underlying launch configuration delegate to be
	 * instantiated (if not already).
	 * </p>
	 * @param mode the mode in which to launch, one of the mode constants
	 *  defined by <code>ILaunchManager</code> - <code>RUN_MODE</code> or <code>DEBUG_MODE</code>.
	 * @param monitor progress monitor, or <code>null</code>. Since 3.0, this
	 *  parameter is ignored. A cancellable progress monitor is provided by the Job
	 *  framework.
	 * @param build whether the workspace should be built before the launch
	 * @param register whether to register the resulting launch with the launch manager
	 * @return resulting launch
	 * @throws CoreException if an exception occurrs during the launch sequence
	 * @since 3.1
	 */
	public ILaunch launch(String mode, IProgressMonitor monitor, boolean build, boolean register) throws CoreException;
	
	/**
	 * Returns whether this launch configuration supports the
	 * specified mode. This is a handle-only method.
	 * 
	 * @param mode a mode in which a configuration can be launched, one of
	 *  the mode constants defined by <code>ILaunchManager</code> - <code>RUN_MODE</code> or
	 *  <code>DEBUG_MODE</code>.
	 * @return whether this launch configuration supports the
	 *  specified mode
	 * @exception CoreException if this method fails. Reasons include:
	 * <ul>
	 * <li>Unable to retrieve this launch configuration's type.</li>
	 * </ul>
	 */
	public boolean supportsMode(String mode) throws CoreException;
	
	/**
	 * Returns the name of this launch configuration. This is
	 * a handle-only method.
	 * 
	 * @return the name of this launch configuration
	 */
	public String getName();
		
	/**
	 * Returns the location of this launch configuration as a
	 * path. This is a handle-only method.
	 * 
	 * @return the location of this launch configuration as a
	 *  path
	 */
	public IPath getLocation();
	
	/**
	 * Returns whether this launch configuration's underlying
	 * storage exists. This is a handle-only method.
	 * 
	 * @return whether this launch configuration's underlying
	 *  storage exists
	 */
	public boolean exists();
	
	/**
	 * Returns the integer-valued attribute with the given name.  
	 * Returns the given default value if the attribute is undefined.
	 *
	 * @param attributeName the name of the attribute
	 * @param defaultValue the value to use if no value is found
	 * @return the value or the default value if no value was found.
	 * @exception CoreException if this method fails. Reasons include:
	 * <ul>
	 * <li>An exception occurs while retrieving the attribute from
	 *  underlying storage.</li>
	 * <li>An attribute with the given name exists, but does not
	 *  have an integer value</li>
	 * </ul>
	 */
	public int getAttribute(String attributeName, int defaultValue) throws CoreException;
	/**
	 * Returns the string-valued attribute with the given name.  
	 * Returns the given default value if the attribute is undefined.
	 *
	 * @param attributeName the name of the attribute
	 * @param defaultValue the value to use if no value is found
	 * @return the value or the default value if no value was found.
	 * @exception CoreException if this method fails. Reasons include:
	 * <ul>
	 * <li>An exception occurs while retrieving the attribute from
	 *  underlying storage.</li>
	 * <li>An attribute with the given name exists, but does not
	 *  have a String value</li>
	 * </ul>
	 */
	public String getAttribute(String attributeName, String defaultValue) throws CoreException;
	/**
	 * Returns the boolean-valued attribute with the given name.  
	 * Returns the given default value if the attribute is undefined.
	 *
	 * @param attributeName the name of the attribute
	 * @param defaultValue the value to use if no value is found
	 * @return the value or the default value if no value was found.
	 * @exception CoreException if this method fails. Reasons include:
	 * <ul>
	 * <li>An exception occurs while retrieving the attribute from
	 *  underlying storage.</li>
	 * <li>An attribute with the given name exists, but does not
	 *  have a boolean value</li>
	 * </ul>
	 */
	public boolean getAttribute(String attributeName, boolean defaultValue) throws CoreException;
	/**
	 * Returns the <code>java.util.List</code>-valued attribute with the given name.  
	 * Returns the given default value if the attribute is undefined.
	 *
	 * @param attributeName the name of the attribute
	 * @param defaultValue the value to use if no value is found
	 * @return the value or the default value if no value was found.
	 * @exception CoreException if this method fails. Reasons include:
	 * <ul>
	 * <li>An exception occurs while retrieving the attribute from
	 *  underlying storage.</li>
	 * <li>An attribute with the given name exists, but does not
	 *  have a List value</li>
	 * </ul>
	 */
	public List getAttribute(String attributeName, List defaultValue) throws CoreException;
	/**
	 * Returns the <code>java.util.Map</code>-valued attribute with the given name.  
	 * Returns the given default value if the attribute is undefined.
	 *
	 * @param attributeName the name of the attribute
	 * @param defaultValue the value to use if no value is found
	 * @return the value or the default value if no value was found.
	 * @exception CoreException if this method fails. Reasons include:
	 * <ul>
	 * <li>An exception occurs while retrieving the attribute from
	 *  underlying storage.</li>
	 * <li>An attribute with the given name exists, but does not
	 *  have a Map value</li>
	 * </ul>
	 */
	public Map getAttribute(String attributeName, Map defaultValue) throws CoreException;
		
	/**
	 * Returns the file this launch configuration is stored
	 * in, or <code>null</code> if this configuration is stored
	 * locally with the workspace. This is a handle-only method.
	 * 
	 * @return the file this launch configuration is stored
	 *  in, or <code>null</code> if this configuration is stored
	 *  locally with the workspace
	 */
	public IFile getFile();
	
	/**
	 * Returns the type of this launch configuration. This is a
	 * handle-only method.
	 * 
	 * @return the type of this launch configuration
	 * @exception CoreException if this method fails. Reasons include:
	 * <ul>
	 * <li>Unable to retrieve or instantiate this launch configuration's type.</li>
	 * </ul>
	 * @see ILaunchConfigurationType
	 */
	public ILaunchConfigurationType getType() throws CoreException;
		
	/**
	 * Returns whether this launch configuration is stored
	 * locally with the workspace. This is a handle-only method.
	 * 
	 * @return whether this launch configuration is stored
	 *  locally with the workspace
	 */
	public boolean isLocal();
	
	/**
	 * Returns a working copy of this launch configuration.
	 * Changes to the working copy will be applied to this
	 * launch configuration when saved. The working copy will
	 * refer to this launch configuration as its original
	 * launch configuration.
	 * 
	 * @return a working copy of this launch configuration
	 * @exception CoreException if this method fails. Reasons include:
	 * <ul>
	 * <li>An exception occurs while initializing the contents of the
	 * working copy from this configuration's underlying storage.</li>
	 * </ul>
	 * @see ILaunchConfigurationWorkingCopy#getOriginal()
	 */
	public ILaunchConfigurationWorkingCopy getWorkingCopy() throws CoreException;		
	
	/**
	 * Returns a copy of this launch configuration, as a
	 * working copy, with the specified name. The new
	 * working copy does not refer back to this configuration
	 * as its original launch configuration (the working copy
	 * will return <code>null</code> for <code>getOriginal()</code>).
	 * When the working copy is saved it will not effect this
	 * launch configuration.
	 * 
	 * @param name the name of the copy
	 * @return a copy of this launch configuration
	 * @exception CoreException if this method fails. Reasons include:
	 * <ul>
	 * <li>An exception occurs while initializing the contents of the
	 * working copy from this configuration's underlying storage.</li>
	 * </ul>
	 * @see ILaunchConfigurationWorkingCopy#getOriginal()
	 */
	public ILaunchConfigurationWorkingCopy copy(String name) throws CoreException;	
	
	/**
	 * Returns whether this launch configuration is a working
	 * copy. Launch configurations which return <code>true</code>
	 * to this method can be safely cast to 
	 * <code>org.eclipse.debug.core.ILaunchConfigurationWorkingCopy</code>.
	 * This is a handle-only method.
	 * 
	 * @return whether this launch configuration is a working
	 *  copy
	 */
	public boolean isWorkingCopy();
	
	/**
	 * Deletes this launch configuration. This configuration's underlying
	 * storage is deleted. Has no effect if this configuration
	 * does not exist.
	 * 
	 * @exception CoreException if this method fails. Reasons include:
	 * <ul>
	 * <li>An exception occurs while deleting this configuration's
	 *  underlying storage.</li>
	 * </ul>
	 */
	public void delete() throws CoreException;
	
	/**
	 * Returns a memento for this launch configuration, or <code>null</code>
	 * if unable to generate a memento for this configuration. A memento
	 * can be used to re-create a launch configuration, via the
	 * launch manager.
	 * 
	 * @return a memento for this configuration
	 * @see ILaunchManager#getLaunchConfiguration(String)
	 * @exception CoreException if an exception occurs generating this
	 *  launch configuration's memento 
	 */
	public String getMemento() throws CoreException;
	
	/**
	 * Returns whether the contents of this launch configuration are 
	 * equal to the contents of the given launch configuration.
	 * 
	 * @return whether the contents of this launch configuration are equal to the contents
	 * of the specified launch configuration.
	 */
	public boolean contentsEqual(ILaunchConfiguration configuration);
	
	/**
	 * Returns this launch configuration's type's category, or <code>null</code>
	 * if unspecified. This is a handle-only method.
	 * 
	 * @return this launch configuration's type's category, or <code>null</code>
	 * @exception CoreException if this method fails. Reasons include:
	 * <ul>
	 * <li>Unable to retrieve or instantiate this launch configuration's type.</li>
	 * </ul>
	 * @since 2.1
	 */
	public String getCategory() throws CoreException;	
	
	/**
	 * Returns a map containing the attributes in this launch configuration.
	 * Returns an empty map if this configuration has no attributes.
	 * <p>
	 * Modifying the map does not affect this launch configuration's attributes.
	 * A launch configuration is modified by obtaining a working copy of that
	 * launch configuration, modifying the working copy, and then saving the working
	 * copy.
	 * </p>
	 * @return a map of attribute keys and values
	 * @exception CoreException unable to generate/retrieve an attribute map
	 * @since 2.1
	 */
	public Map getAttributes() throws CoreException;
}
