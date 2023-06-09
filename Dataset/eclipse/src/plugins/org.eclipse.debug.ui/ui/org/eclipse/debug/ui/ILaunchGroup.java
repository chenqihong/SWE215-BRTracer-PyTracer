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
package org.eclipse.debug.ui;
 
import org.eclipse.jface.resource.ImageDescriptor;

/**
 * A launch group identifies a group of launch configurations by a launch
 * mode and category. The launch configuration dialog can be opened on
 * a launch group, and a launch history is maintained for each group.
 * A launch group is defined in plug-in XML via the <code>launchGroups</code>
 * extension point.
 * <p> 
 * Following is an example of a launch group contribution:
 * <pre>
 * 	&lt;extension point="org.eclipse.debug.ui.launchGroups"&gt;
 * 		&lt;launchGroup
 * 			  id="com.example.ExampleLaunchGroupId"
 * 			  mode="run"
 * 			  label="Run"
 * 			  image="icons\run.gif"
 * 		&lt;/launchGroup&gt;
 * 	&lt;/extension&gt;
 * </pre>
 * </p>
 * <p>
 * Clients are not intended to implement this interface.
 * </p>
 * @since 3.0
 */
public interface ILaunchGroup {
	
	/**
	 * Returns the image for this launch group, or <code>null</code>
	 * if none.
	 * 
	 * @return the image for this launch group, or <code>null</code> if none
	 */
	public ImageDescriptor getImageDescriptor();
	
	/**
	 * Returns the banner image for this launch group, or <code>null</code> if
	 * none
	 * 
	 * @return the banner image for this launch group, or <code>null</code> if
	 * none
	 */
	public ImageDescriptor getBannerImageDescriptor();
	
	/**
	 * Returns the label for this launch group
	 * 
	 * @return the label for this launch group
	 */
	public String getLabel();
		
	/**
	 * Returns the id for this launch group
	 * 
	 * @return the id for this launch group
	 */
	public String getIdentifier();
	
	/**
	 * Returns the category for this launch group, possibly <code>null</code>
	 * 
	 * @return the category for this launch group, possibly <code>null</code>
	 */
	public String getCategory();
	
	/**
	 * Returns the mode for this launch group
	 * 
	 * @return the mode for this launch group
	 */
	public String getMode();
	
	/**
	 * Returns whether this launch group is public
	 *  
	 * @return boolean
	 */
	public boolean isPublic();
	
}

