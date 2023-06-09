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
package org.eclipse.help.internal.base;

import java.util.*;

import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.preferences.*;

/**
 */
public class HelpBasePreferenceInitializer extends
		AbstractPreferenceInitializer {

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#initializeDefaultPreferences()
	 */
	public void initializeDefaultPreferences() {
		Preferences prefs = HelpBasePlugin.getDefault().getPluginPreferences();

		String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH); //$NON-NLS-1$

		if (os.indexOf("windows") != -1) { //$NON-NLS-1$
			prefs
					.setDefault("custom_browser_path", //$NON-NLS-1$
							"\"C:\\Program Files\\Internet Explorer\\IEXPLORE.EXE\" %1"); //$NON-NLS-1$
		} else if (os.indexOf("linux") != -1) { //$NON-NLS-1$
			prefs.setDefault("custom_browser_path", //$NON-NLS-1$
					"konqueror %1"); //$NON-NLS-1$
		} else {
			prefs.setDefault("custom_browser_path", "mozilla %1"); //$NON-NLS-1$ //$NON-NLS-2$
		}
	}

}
