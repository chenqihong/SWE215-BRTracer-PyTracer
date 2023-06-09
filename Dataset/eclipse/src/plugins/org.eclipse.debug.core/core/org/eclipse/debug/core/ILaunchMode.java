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

/**
 * A launch mode. The debug platform contributes launch modes
 * for run, debug, and profile. Clients may contribute additional launch
 * modes in plug-in XML via the <code>launchModes</code> extension point.
 * <p>
 * Following is an example launch mode contribution for profiling. A launch
 * mode has an unique identifer specified by the <code>mode</code> attribute
 * and a human readable label specified by the <code>label</code> attribute.
 * <pre>
 *  &lt;extension point=&quot;org.eclipse.debug.core.launchModes&quot;&gt;
 *   &lt;launchMode
 *    mode=&quot;profile&quot;
 *    label=&quot;Profile&quot;&gt;
 *   &lt;/launchMode&gt;
 *  &lt;/extension&gt;
 * </pre>
 * </p>
 * <p>
 * Clients are not intended to implement this interface.
 * </p>
 * @since 3.0
 */
public interface ILaunchMode {
	
	/**
	 * Returns the unique identifier for this launch mode.
	 * 
	 * @return the unique identifier for this launch mode
	 */
	public String getIdentifier();
	
	/**
	 * Returns a human readable label for this launch mode.
	 * 
	 * @return a human readable label for this launch mode
	 */
	public String getLabel();
}
