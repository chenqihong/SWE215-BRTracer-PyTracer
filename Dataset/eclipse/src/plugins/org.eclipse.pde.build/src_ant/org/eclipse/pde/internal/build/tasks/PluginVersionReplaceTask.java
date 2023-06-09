/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM - Initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.build.tasks;

import java.io.*;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/** 
 * Internal task.
 * This task aims at replacing the generic ids used into a plugin.xml by another value.
 * @since 3.0
 */
public class PluginVersionReplaceTask extends Task {
	private static final String PLUGIN = "plugin"; //$NON-NLS-1$
	private static final String FRAGMENT = "fragment"; //$NON-NLS-1$
	private static final String VERSION = "version";//$NON-NLS-1$
	private static final String BACKSLASH = "\""; //$NON-NLS-1$

	//Path of the file where we are replacing the values
	private String pluginFilePath;
	private boolean plugin = true;
	private String newVersion;

	/**
	 * The location of a fragment.xml or plugin.xml file 
	 * @param path
	 */
	public void setPluginFilePath(String path) {
		pluginFilePath = path;
	}

	/**
	 * Set the new version.
	 * @param qualifier the version that will be set in the manifest file.
	 */
	public void setVersionNumber(String qualifier) {
		newVersion = qualifier;
	}

	/**
	 * Set the type of the file.
	 * @param input 
	 */
	public void setInput(String input) {
		if (input.equalsIgnoreCase("fragment.xml")) //$NON-NLS-1$
			plugin = false;
	}

	public void execute() {
		StringBuffer buffer = null;
		try {
			buffer = readFile(new File(pluginFilePath));
		} catch (IOException e) {
			throw new BuildException(e);
		}

		//Find the word plugin or fragment
		int startPlugin;
		if (plugin)
			startPlugin = scan(buffer, 0, PLUGIN);
		else
			startPlugin = scan(buffer, 0, FRAGMENT);

		if (startPlugin == -1)
			return;

		int endPlugin = scan(buffer, startPlugin + 1, ">"); //$NON-NLS-1$

		//Find the version tag in the plugin header
		boolean versionFound = false;
		while (!versionFound) {
			int versionAttr = scan(buffer, startPlugin, VERSION);
			if (versionAttr == -1 || versionAttr > endPlugin)
				return;
			if (!Character.isWhitespace(buffer.charAt(versionAttr - 1))) {
				startPlugin = versionAttr + VERSION.length();
				continue;
			}
			//Verify that the word version found is the actual attribute
			int endVersionWord = versionAttr + VERSION.length();
			while (Character.isWhitespace(buffer.charAt(endVersionWord)) && endVersionWord < endPlugin) {
				endVersionWord++;
			}
			if (endVersionWord > endPlugin) //version has not been found 
				return;

			if (buffer.charAt(endVersionWord) != '=') {
				startPlugin = endVersionWord;
				continue;
			}

			//Version has been found, extract the version id and replace it
			int startVersionId = scan(buffer, versionAttr + 1, BACKSLASH);
			int endVersionId = scan(buffer, startVersionId + 1, BACKSLASH);

			buffer.replace(startVersionId + 1, endVersionId, newVersion);
			versionFound = true;
		}
		try {
			OutputStreamWriter w = new OutputStreamWriter(new BufferedOutputStream(new FileOutputStream(pluginFilePath)), "UTF-8"); //$NON-NLS-1$
			w.write(buffer.toString());
			w.close();
		} catch (FileNotFoundException e) {
			// ignore
		} catch (IOException e) {
			throw new BuildException(e);
		}
	}

	private int scan(StringBuffer buf, int start, String targetName) {
		return scan(buf, start, new String[] {targetName});
	}

	private int scan(StringBuffer buf, int start, String[] targets) {
		for (int i = start; i < buf.length(); i++) {
			for (int j = 0; j < targets.length; j++) {
				if (i < buf.length() - targets[j].length()) {
					String match = buf.substring(i, i + targets[j].length());
					if (targets[j].equalsIgnoreCase(match))
						return i;
				}
			}
		}
		return -1;
	}

	private StringBuffer readFile(File targetName) throws IOException {
		InputStreamReader reader = new InputStreamReader(new BufferedInputStream(new FileInputStream(targetName)), "UTF-8"); //$NON-NLS-1$
		StringBuffer result = new StringBuffer();
		char[] buf = new char[4096];
		int count;
		try {
			count = reader.read(buf, 0, buf.length);
			while (count != -1) {
				result.append(buf, 0, count);
				count = reader.read(buf, 0, buf.length);
			}
		} finally {
			try {
				reader.close();
			} catch (IOException e) {
				// ignore exceptions here
			}
		}
		return result;
	}

	private static void transferStreams(InputStream source, OutputStream destination) throws IOException {
		source = new BufferedInputStream(source);
		destination = new BufferedOutputStream(destination);
		try {
			byte[] buffer = new byte[8192];
			while (true) {
				int bytesRead = -1;
				if ((bytesRead = source.read(buffer)) == -1)
					break;
				destination.write(buffer, 0, bytesRead);
			}
		} finally {
			try {
				source.close();
			} catch (IOException e) {
				// ignore
			}
			try {
				destination.close();
			} catch (IOException e) {
				// ignore
			}
		}
	}
}
