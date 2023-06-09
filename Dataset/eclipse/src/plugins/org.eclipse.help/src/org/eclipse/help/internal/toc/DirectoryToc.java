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
package org.eclipse.help.internal.toc;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.*;

import org.eclipse.core.runtime.*;
import org.eclipse.help.*;
import org.eclipse.help.internal.*;
import org.eclipse.help.internal.util.*;
import org.osgi.framework.*;

/**
 * Toc created from files in a extra directory in a plugin.
 */
public class DirectoryToc {
	private String dir;

	/**
	 * Map of ITopic by href String;
	 */
	private Map extraTopics;

	private String locale;

	/**
	 * Constructor.
	 */
	protected DirectoryToc(TocFile tocFile) {
		this(tocFile.getPluginID(), tocFile.getLocale(), tocFile.getExtraDir());
	}

	private DirectoryToc(String pluginID, String locale, String directory) {
		this.locale = locale;
		// Obtain extra search directory if provided
		this.dir = HrefUtil.normalizeDirectoryHref(pluginID, directory);

	}

	/**
	 * This public method is to be used after the build of TOCs is finished.
	 * With assumption that TOC model is not modifiable after the build, this
	 * method caches topics in an array and releases objects used only during
	 * build.
	 * 
	 * @return Map of ITopic
	 */
	public Map getExtraTopics() {
		if (extraTopics == null) {
			extraTopics = createExtraTopics();
			// for memory foot print, release TocFile and dir
			dir = null;
		}

		return extraTopics;
	}

	/**
	 * Obtains URLs of all documents inside given directory.
	 * 
	 * @return Map of ITopic by href
	 */
	private Map createExtraTopics() {
		Map ret = new HashMap();
		String pluginID = HrefUtil.getPluginIDFromHref(dir);
		if (pluginID == null) {
			return ret;
		}
		Bundle pluginDesc = Platform.getBundle(pluginID);
		if (pluginDesc == null || pluginDesc.getState() == Bundle.INSTALLED
				|| pluginDesc.getState() == Bundle.UNINSTALLED)
			return ret;
		String directory = HrefUtil.getResourcePathFromHref(dir);
		if (directory == null) {
			// the root - all files in a zip should be indexed
			directory = ""; //$NON-NLS-1$
		}
		// Find doc.zip file
		IPath iPath = new Path("$nl$/doc.zip"); //$NON-NLS-1$
		Map override = new HashMap(1);
		override.put("$nl$", locale); //$NON-NLS-1$
		URL url = Platform.find(pluginDesc, iPath, override);
		if (url == null) {
			url = Platform.find(pluginDesc, new Path("doc.zip")); //$NON-NLS-1$
		}
		if (url != null) {
			// collect topics from doc.zip file
			ret.putAll(createExtraTopicsFromZip(pluginID, directory, url));
		}
		
		// Find topics in plugin
		Set paths = ResourceLocator.findTopicPaths(pluginDesc, directory,
				locale);
		for (Iterator it = paths.iterator(); it.hasNext();) {
			String href = "/" + pluginID + "/" + (String) it.next();  //$NON-NLS-1$//$NON-NLS-2$
			ret.put(href, new ExtraTopic(href));
		}
		return ret;
	}

	/**
	 * @param directory
	 *            path in the form "segment1/segment2...", "" will return names
	 *            of all files in a zip
	 * @return Map of ITopic by href String
	 */
	private Map createExtraTopicsFromZip(String pluginID, String directory,
			URL url) {
		Map ret = new HashMap(0);
		URL realZipURL;
		try {
			realZipURL = Platform.asLocalURL(Platform.resolve(url));
			if (realZipURL.toExternalForm().startsWith("jar:")) { //$NON-NLS-1$
				// doc.zip not allowed in jarred plug-ins.
				return ret;
			}
		} catch (IOException ioe) {
			HelpPlugin.logError("IOException occurred, when resolving URL " //$NON-NLS-1$
					+ url.toString() + ".", ioe); //$NON-NLS-1$
			return ret;
		}
		ZipFile zipFile;
		try {
			zipFile = new ZipFile(realZipURL.getFile());
			ret = createExtraTopicsFromZipFile(pluginID, zipFile, directory);
			zipFile.close();
		} catch (IOException ioe) {
			HelpPlugin.logError(
					"IOException occurred, when accessing Zip file " //$NON-NLS-1$
							+ realZipURL.getFile()
							+ ".  File might not be locally available.", ioe); //$NON-NLS-1$
			return new HashMap(0);
		}

		return ret;

	}

	/**
	 * Obtains names of files in a zip file that given directory in their path.
	 * Files in subdirectories are included as well.
	 * 
	 * @param directory
	 *            path in the form "segment1/segment2...", "" will return names
	 *            of all files in a zip
	 * @return Map of ITopic by href String
	 */
	private Map createExtraTopicsFromZipFile(String pluginID, ZipFile zipFile,
			String directory) {
		String constantHrefSegment = "/" + pluginID + "/"; //$NON-NLS-1$ //$NON-NLS-2$
		Map ret = new HashMap();
		for (Enumeration entriesEnum = zipFile.entries(); entriesEnum.hasMoreElements();) {
			ZipEntry zEntry = (ZipEntry) entriesEnum.nextElement();
			if (zEntry.isDirectory()) {
				continue;
			}
			String docName = zEntry.getName();
			int l = directory.length();
			if (l == 0 || docName.length() > l && docName.charAt(l) == '/'
					&& directory.equals(docName.substring(0, l))) {
				String href = constantHrefSegment + docName;
				ret.put(href, new ExtraTopic(href));
			}
		}
		return ret;
	}

	class ExtraTopic implements ITopic {
		private String topicHref;

		public ExtraTopic(String href) {
			this.topicHref = href;
		}

		public String getHref() {
			return topicHref;
		}

		public String getLabel() {
			return topicHref;
		}

		public ITopic[] getSubtopics() {
			return new ITopic[0];
		}
	}
}
