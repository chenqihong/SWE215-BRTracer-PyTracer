/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.update.internal.core;
import java.net.*;

import org.eclipse.core.runtime.*;
import org.eclipse.update.core.*;

/**
 * Site on the File System
 */
public class SiteFileContentProvider extends SiteContentProvider {
	
	public static final String SITE_TYPE = "org.eclipse.update.core.file";	 //$NON-NLS-1$

	/**
	 * Constructor for FileSite
	 */
	public SiteFileContentProvider(URL url) {
		super(url);
	}

			
	/*
	 * @see ISiteContentProvider#getArchiveReference(String)
	 */
	public URL getArchiveReference(String archiveId)  throws CoreException {
		URL contentURL = null;
		
		contentURL = getArchiveURLfor(archiveId);
		// if there is no mapping in the site.xml
		// for this archiveId, use the default one
		if (contentURL==null) {
			return super.getArchiveReference(archiveId);
		}
		
		return contentURL;
	}

	/**
	 * return the URL associated with the id of the archive for this site
	 * return null if the archiveId is null, empty or 
	 * if teh list of archives on the site is null or empty
	 * of if there is no URL associated with the archiveID for this site
	 */
	private URL getArchiveURLfor(String archiveId) {
		URL result = null;
		boolean found = false;

		IArchiveReference[] siteArchives = getSite().getArchives();
		if (siteArchives.length > 0) {
			for (int i = 0; i < siteArchives.length && !found; i++) {
				if (archiveId.trim().equalsIgnoreCase(siteArchives[i].getPath())) {
					result = siteArchives[i].getURL();
					found = true;
					break;
				}
			}
		}
		return result;
	}
}


