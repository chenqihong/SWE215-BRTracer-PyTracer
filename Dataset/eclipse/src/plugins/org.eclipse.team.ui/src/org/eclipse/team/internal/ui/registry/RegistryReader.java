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
package org.eclipse.team.internal.ui.registry;

import java.util.Hashtable;

import org.eclipse.core.runtime.*;
import org.eclipse.team.internal.ui.TeamUIPlugin;
import org.eclipse.team.internal.ui.Utils.Sorter;

public abstract class RegistryReader {
	protected static final String TAG_DESCRIPTION = "description"; //$NON-NLS-1$
	protected static Hashtable extensionPoints = new Hashtable();
	/**
	 * The constructor.
	 */
	protected RegistryReader() {
	}
	/**
	 * This method extracts description as a subelement of the given element.
	 * 
	 * @return description string if defined, or empty string if not.
	 */
	protected String getDescription(IConfigurationElement config) {
		IConfigurationElement[] children = config.getChildren(TAG_DESCRIPTION);
		if (children.length >= 1) {
			return children[0].getValue();
		}
		return ""; //$NON-NLS-1$
	}
	/**
	 * Logs the error in the workbench log using the provided text and the
	 * information in the configuration element.
	 */
	protected void logError(IConfigurationElement element, String text) {
		IExtension extension = element.getDeclaringExtension();
		StringBuffer buf = new StringBuffer();
		buf.append("Plugin " + extension.getNamespace() + ", extension " + extension.getExtensionPointUniqueIdentifier()); //$NON-NLS-2$//$NON-NLS-1$
		buf.append("\n" + text); //$NON-NLS-1$
		TeamUIPlugin.log(IStatus.ERROR, buf.toString(), null);
	}
	/**
	 * Logs a very common registry error when a required attribute is missing.
	 */
	protected void logMissingAttribute(IConfigurationElement element, String attributeName) {
		logError(element, "Required attribute '" + attributeName + "' not defined"); //$NON-NLS-2$//$NON-NLS-1$
	}

	/**
	 * Logs a very common registry error when a required child is missing.
	 */
	protected void logMissingElement(IConfigurationElement element, String elementName) {
		logError(element, "Required sub element '" + elementName + "' not defined"); //$NON-NLS-2$//$NON-NLS-1$
	}

	/**
	 * Logs a registry error when the configuration element is unknown.
	 */
	protected void logUnknownElement(IConfigurationElement element) {
		logError(element, "Unknown extension tag found: " + element.getName()); //$NON-NLS-1$
	}
	/**
	 * Apply a reproducable order to the list of extensions provided, such that
	 * the order will not change as extensions are added or removed.
	 */
	protected IExtension[] orderExtensions(IExtension[] extensions) {
		// By default, the order is based on plugin id sorted
		// in ascending order. The order for a plugin providing
		// more than one extension for an extension point is
		// dependent in the order listed in the XML file.
		Sorter sorter = new Sorter() {
			public boolean compare(Object extension1, Object extension2) {
				String s1 = ((IExtension) extension1).getNamespace();
				String s2 = ((IExtension) extension2).getNamespace();
				//Return true if elementTwo is 'greater than' elementOne
				return s2.compareToIgnoreCase(s1) > 0;
			}
		};

		Object[] sorted = sorter.sort(extensions);
		IExtension[] sortedExtension = new IExtension[sorted.length];
		System.arraycopy(sorted, 0, sortedExtension, 0, sorted.length);
		return sortedExtension;
	}
	/**
	 * Implement this method to read element's attributes. If children should
	 * also be read, then implementor is responsible for calling <code>readElementChildren</code>.
	 * Implementor is also responsible for logging missing attributes.
	 * 
	 * @return true if element was recognized, false if not.
	 */
	protected abstract boolean readElement(IConfigurationElement element);
	/**
	 * Read the element's children. This is called by the subclass' readElement
	 * method when it wants to read the children of the element.
	 */
	protected void readElementChildren(IConfigurationElement element) {
		readElements(element.getChildren());
	}
	/**
	 * Read each element one at a time by calling the subclass implementation
	 * of <code>readElement</code>.
	 * 
	 * Logs an error if the element was not recognized.
	 */
	protected void readElements(IConfigurationElement[] elements) {
		for (int i = 0; i < elements.length; i++) {
			if (!readElement(elements[i]))
				logUnknownElement(elements[i]);
		}
	}
	/**
	 * Read one extension by looping through its configuration elements.
	 */
	protected void readExtension(IExtension extension) {
		readElements(extension.getConfigurationElements());
	}
	/**
	 * Start the registry reading process using the supplied plugin ID and
	 * extension point.
	 */
	public void readRegistry(IExtensionRegistry registry, String pluginId, String extensionPoint) {
		String pointId = pluginId + "-" + extensionPoint; //$NON-NLS-1$
		IExtension[] extensions = (IExtension[]) extensionPoints.get(pointId);
		if (extensions == null) {
			IExtensionPoint point = registry.getExtensionPoint(pluginId, extensionPoint);
			if (point == null)
				return;
			extensions = point.getExtensions();
			extensionPoints.put(pointId, extensions);
		}
		for (int i = 0; i < extensions.length; i++)
			readExtension(extensions[i]);
	}
}