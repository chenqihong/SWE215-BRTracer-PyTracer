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
package org.eclipse.update.configurator;

import java.io.IOException;
import java.net.URL;

/**
 * Factory for creating platform configurations.
 * <p>
 * <b>Note:</b> This class/interface is part of an interim API that is still under development and expected to
 * change significantly before reaching stability. It is being made available at this early stage to solicit feedback
 * from pioneering adopters on the understanding that any code that uses this API will almost certainly be broken
 * (repeatedly) as the API evolves.
 * </p>
 */
public interface IPlatformConfigurationFactory {
	/**
	 * Returns the current platform configuration.
	 * 
	 * @return platform configuration used in current instance of platform
	 */	
	public IPlatformConfiguration getCurrentPlatformConfiguration();
	/**
	 * Returns a platform configuration object, optionally initialized with previously saved
	 * configuration information.
	 * 
	 * @param url location of previously save configuration information. If <code>null</code>
	 * is specified, an empty configuration object is returned
	 * @return platform configuration used in current instance of platform
	 */	
	public IPlatformConfiguration getPlatformConfiguration(URL url) throws IOException;
}
