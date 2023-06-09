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
package org.eclipse.help.internal.workingset;

import java.io.*;
import java.util.*;

import javax.xml.parsers.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.*;
import javax.xml.transform.stream.*;

import org.eclipse.core.runtime.*;
import org.eclipse.help.internal.*;
import org.eclipse.help.internal.base.*;
import org.w3c.dom.*;
import org.xml.sax.*;

/**
 * The working set manager stores help working sets. Working sets are persisted
 * whenever one is added or removed.
 * 
 * @since 2.1
 */
public class WorkingSetManager implements IHelpWorkingSetManager,
		ITocsChangedListener {

	// Working set persistence
	private static final String WORKING_SET_STATE_FILENAME = "workingsets.xml"; //$NON-NLS-1$

	private SortedSet workingSets = new TreeSet(new WorkingSetComparator());

	private AdaptableTocsArray root;

	private static final DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory
			.newInstance();

	private static final TransformerFactory transformerFactory = TransformerFactory
			.newInstance();

	/**
	 * Constructor
	 */
	public WorkingSetManager() {
		restoreState();
		HelpPlugin.getDefault().addTocsChangedListener(this);
	}

	public AdaptableTocsArray getRoot() {
		if (root == null)
			root = new AdaptableTocsArray(HelpPlugin.getTocManager().getTocs(
					Platform.getNL()));
		return root;
	}

	/**
	 * Adds a new working set and saves it
	 */
	public void addWorkingSet(WorkingSet workingSet) {
		if (workingSet == null || workingSets.contains(workingSet))
			return;
		workingSets.add(workingSet);
		saveState();
	}

	/**
	 * Creates a new working set
	 */
	public WorkingSet createWorkingSet(String name,
			AdaptableHelpResource[] elements) {
		return new WorkingSet(name, elements);
	}

	/**
	 * Tests the receiver and the object for equality
	 * 
	 * @param object
	 *            object to compare the receiver to
	 * @return true=the object equals the receiver, it has the same working
	 *         sets. false otherwise
	 */
	public boolean equals(Object object) {
		if (this == object) {
			return true;
		}
		if (object instanceof WorkingSetManager) {
			WorkingSetManager workingSetManager = (WorkingSetManager) object;
			return workingSetManager.workingSets.equals(workingSets);
		}
		return false;
	}

	/**
	 * Returns a working set by name
	 *  
	 */
	public WorkingSet getWorkingSet(String name) {
		if (name == null || workingSets == null)
			return null;

		Iterator iter = workingSets.iterator();
		while (iter.hasNext()) {
			WorkingSet workingSet = (WorkingSet) iter.next();
			if (name.equals(workingSet.getName()))
				return workingSet;
		}
		return null;
	}

	/**
	 * Returns the hash code.
	 * 
	 * @return the hash code.
	 */
	public int hashCode() {
		return workingSets.hashCode();
	}

	/**
	 * Implements IWorkingSetManager.
	 * 
	 * @see org.eclipse.ui.IWorkingSetManager#getWorkingSets()
	 */
	public WorkingSet[] getWorkingSets() {
		return (WorkingSet[]) workingSets.toArray(new WorkingSet[workingSets
				.size()]);
	}

	/**
	 * Returns the file used as the persistence store
	 * 
	 * @return the file used as the persistence store
	 */
	private File getWorkingSetStateFile() {
		IPath path = HelpBasePlugin.getDefault().getStateLocation();
		path = path.append(WORKING_SET_STATE_FILENAME);
		return path.toFile();
	}

	/**
	 * Removes specified working set
	 */
	public void removeWorkingSet(WorkingSet workingSet) {
		workingSets.remove(workingSet);
		saveState();
	}

	/**
	 * Reads the persistence store and creates the working sets stored in it.
	 */
	public boolean restoreState() {
		File stateFile = getWorkingSetStateFile();

		if (stateFile.exists()) {
			try {
				FileInputStream input = new FileInputStream(stateFile);
				InputStreamReader reader = new InputStreamReader(input, "utf-8"); //$NON-NLS-1$

				InputSource inputSource = new InputSource(reader);
				inputSource.setSystemId(stateFile.toString());

				DocumentBuilder parser = documentBuilderFactory
						.newDocumentBuilder();
				Document d = parser.parse(inputSource);

				Element rootElement = d.getDocumentElement();
				restoreWorkingSetState(rootElement);
				input.close();

				return true;
			} catch (ParserConfigurationException pce) {
				HelpPlugin
						.logError(
								"DocumentBuilder implementation could not be loaded, to restore working set state.", pce); //$NON-NLS-1$
				return false;
			} catch (SAXException se) {
				HelpBasePlugin
						.logError(
								"Error occurred parsing file " + stateFile.toString() + ", while restoring working set state.", se); //$NON-NLS-1$ //$NON-NLS-2$
				return false;
			} catch (IOException ioe) {
				HelpBasePlugin
						.logError(
								"Error occurred parsing file " + stateFile.toString() + ", while restoring working set state.", ioe); //$NON-NLS-1$ //$NON-NLS-2$
				return false;
			}
		}
		return false;
	}

	/**
	 * Recreates all working sets from the persistence store and adds them to
	 * the receiver.
	 * 
	 * @param parent
	 *            the xml element containing serialized working sets
	 */
	private void restoreWorkingSetState(Element parent) {
		NodeList workingSets = parent.getChildNodes();

		for (int i = 0; i < workingSets.getLength(); i++) {
			if (workingSets.item(i).getNodeType() != Node.ELEMENT_NODE)
				continue;

			WorkingSet workingSet = restoreWorkingSet((Element) workingSets
					.item(i));
			if (workingSet != null) {
				this.workingSets.add(workingSet);
			}
		}
	}

	/**
	 * Recreates a working set from the persistence store.
	 * 
	 * @return the working set created from the memento or null if creation
	 *         failed.
	 */
	private WorkingSet restoreWorkingSet(Element workingSetNode) {

		String name = workingSetNode.getAttribute("name"); //$NON-NLS-1$
		NodeList items = workingSetNode.getElementsByTagName("item"); //$NON-NLS-1$
		List helpResources = new ArrayList(items.getLength());
		for (int i = 0; i < items.getLength(); i++) {
			Element item = (Element) items.item(i);
			String href = item.getAttribute("toc"); //$NON-NLS-1$
			if (href == null || href.length() == 0)
				continue;

			String child_pos = item.getAttribute("topic"); //$NON-NLS-1$
			int pos = -1;
			if (child_pos != null) {
				try {
					pos = Integer.parseInt(child_pos);
				} catch (Exception e) {
				}
			}

			AdaptableHelpResource toc = getAdaptableToc(href);

			if (toc == null)
				return null;

			if (pos == -1) {
				// Create the adaptable toc.
				helpResources.add(toc);
			} else {
				// Create the adaptable topic
				AdaptableTopic[] topics = (AdaptableTopic[]) toc.getChildren();
				if (pos >= 0 && topics.length > pos)
					helpResources.add(topics[pos]);
			}
		}

		AdaptableHelpResource[] elements = new AdaptableHelpResource[helpResources
				.size()];
		helpResources.toArray(elements);

		WorkingSet ws = createWorkingSet(name, elements);

		return ws;
	}

	/**
	 * Saves the working sets in the persistence store
	 */
	public synchronized boolean saveState() {
		File stateFile = null;
		try {
			DocumentBuilder docBuilder = documentBuilderFactory
					.newDocumentBuilder();
			Document doc = docBuilder.newDocument();
			Element rootElement = doc.createElement("workingSets"); //$NON-NLS-1$
			doc.appendChild(rootElement);

			saveWorkingSetState(rootElement);

			stateFile = getWorkingSetStateFile();
			stateFile.getParentFile().mkdir();
			FileOutputStream stream = new FileOutputStream(stateFile);

			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.METHOD, "xml"); //$NON-NLS-1$
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8"); //$NON-NLS-1$
			DOMSource source = new DOMSource(doc);
			StreamResult result = new StreamResult(stream);

			transformer.transform(source, result);
			stream.close();
			return true;
		} catch (ParserConfigurationException pce) {
			HelpPlugin.logError(
					"DocumentBuilder implementation could not be loaded.", pce); //$NON-NLS-1$
			return false;
		} catch (TransformerException e) {
			HelpPlugin.logError("Problems occurred while saving working sets.", //$NON-NLS-1$
					null);
			return false;
		} catch (IOException e) {
			stateFile.delete();
			HelpBasePlugin.logError(
					"Problems occurred while saving working set file.", null); //$NON-NLS-1$
			return false;
		}
	}

	/**
	 * Saves all persistable working sets in the persistence store.
	 * 
	 * @param parent:
	 *            the xml node to save to
	 */
	private void saveWorkingSetState(Element parent) {
		Iterator iterator = workingSets.iterator();

		while (iterator.hasNext()) {
			WorkingSet workingSet = (WorkingSet) iterator.next();
			workingSet.saveState(parent);
		}
	}

	/**
	 * Persists all working sets. Should only be called by the webapp working
	 * set dialog.
	 * 
	 * @param changedWorkingSet
	 *            the working set that has changed
	 */
	public void workingSetChanged(WorkingSet changedWorkingSet) {
		saveState();
	}

	public AdaptableToc getAdaptableToc(String href) {
		return getRoot().getAdaptableToc(href);
	}

	public AdaptableTopic getAdaptableTopic(String id) {

		if (id == null || id.length() == 0)
			return null;

		// toc id's are hrefs: /pluginId/path/to/toc.xml
		// topic id's are based on parent toc id and index of topic:
		// /pluginId/path/to/toc.xml_index_
		int len = id.length();
		if (id.charAt(len - 1) == '_') {
			// This is a first level topic
			String indexStr = id.substring(id.lastIndexOf('_', len - 2) + 1,
					len - 1);
			int index = 0;
			try {
				index = Integer.parseInt(indexStr);
			} catch (Exception e) {
			}

			String tocStr = id.substring(0, id.lastIndexOf('_', len - 2));
			AdaptableToc toc = getAdaptableToc(tocStr);
			if (toc == null)
				return null;
			IAdaptable[] topics = toc.getChildren();
			if (index < 0 || index >= topics.length)
				return null;
			return (AdaptableTopic) topics[index];
		}

		return null;
	}

	public String getCurrentWorkingSet() {
		return HelpBasePlugin.getDefault().getPluginPreferences().getString(
				BaseHelpSystem.WORKING_SET);
	}

	public void setCurrentWorkingSet(String workingSet) {
		HelpBasePlugin.getDefault().getPluginPreferences().setValue(
				BaseHelpSystem.WORKING_SET, workingSet);
		HelpBasePlugin.getDefault().savePluginPreferences();
	}

	public void tocsChanged() {
		saveState();
		root = null;
		workingSets = new TreeSet(new WorkingSetComparator());
		restoreState();
	}

}
