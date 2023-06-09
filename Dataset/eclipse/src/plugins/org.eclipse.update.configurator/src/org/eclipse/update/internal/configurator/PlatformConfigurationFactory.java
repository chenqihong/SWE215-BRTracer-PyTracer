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
package org.eclipse.update.internal.configurator;

import java.io.IOException;
import java.net.URL;

import org.eclipse.update.configurator.*;



public class PlatformConfigurationFactory implements IPlatformConfigurationFactory {
	public IPlatformConfiguration getCurrentPlatformConfiguration() {
		return PlatformConfiguration.getCurrent();
	}
	public IPlatformConfiguration getPlatformConfiguration(URL url) throws IOException {
		try {
			return new PlatformConfiguration(url);
		} catch (Exception e) {
			if(e instanceof IOException)
				throw (IOException)e;
			else
				throw new IOException(e.getMessage());
		}
	}
}
