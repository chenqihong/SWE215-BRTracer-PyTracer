/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.builders;

import java.net.*;
import java.util.ArrayList;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.internal.*;
import org.eclipse.pde.internal.core.util.IdUtil;
import org.w3c.dom.*;


public class ManifestErrorReporter extends XMLErrorReporter {

	/**
	 * @param file
	 */
	public ManifestErrorReporter(IFile file) {
		super(file);
	}
	
	protected void reportIllegalElement(Element element, int severity) {
		Node parent = element.getParentNode();
		if (parent == null || parent instanceof org.w3c.dom.Document) {
			report(PDEMessages.Builders_Manifest_illegalRoot, getLine(element), severity); //$NON-NLS-1$
		} else {
			report(NLS.bind(PDEMessages.Builders_Manifest_child, (new String[] { //$NON-NLS-1$
			element.getNodeName(), parent.getNodeName() })),
					getLine(element), severity);
		}
	}
	
	protected void reportMissingRequiredAttribute(Element element, String attName, int severity) {
		String message = NLS.bind(PDEMessages.Builders_Manifest_missingRequired, (new String[] { attName, element.getNodeName() })); //$NON-NLS-1$			
		report(message, getLine(element), severity);
	}

	protected boolean assertAttributeDefined(Element element, String attrName, int severity) {
		Attr attr = element.getAttributeNode(attrName);
		if (attr == null) {
			reportMissingRequiredAttribute(element, attrName, severity);
			return false;
		}
		return true;
	}

	protected void reportUnknownAttribute(Element element, String attName, int severity) {
		String message = NLS.bind(PDEMessages.Builders_Manifest_attribute, attName);
		report(message, getLine(element, attName), severity);
	}
	
	protected void reportIllegalAttributeValue(Element element, Attr attr) {
		String message = NLS.bind(PDEMessages.Builders_Manifest_att_value, (new String[] { attr.getValue(), attr.getName() }));
		report(message, getLine(element, attr.getName()), CompilerFlags.ERROR);
	}
	
	protected void validateVersionAttribute(Element element, Attr attr) {
		IStatus status = PluginVersionIdentifier.validateVersion(attr.getValue());
		if (status.getSeverity() != IStatus.OK)
			report(status.getMessage(), getLine(element, attr.getName()), CompilerFlags.ERROR);
	}
	
	protected void validateMatch(Element element, Attr attr) {
		String value = attr.getValue();
		if (!"perfect".equals(value) && !"equivalent".equals(value) //$NON-NLS-1$ //$NON-NLS-2$
			&& !"greaterOrEqual".equals(value) && !"compatible".equals(value)) //$NON-NLS-1$ //$NON-NLS-2$
			reportIllegalAttributeValue(element, attr);
	}

	protected void validateElementWithContent(Element element, boolean hasContent) {
		NodeList children = element.getChildNodes();
		boolean textFound = false;
		for (int i = 0; i < children.getLength(); i++) {
			Node child = children.item(i);
			if (child instanceof Text) {
				textFound = ((Text)child).getNodeValue().trim().length() > 0;
			} else if (child instanceof Element) {
				reportIllegalElement((Element)child, CompilerFlags.ERROR);
			}
		}
		if (!textFound)
			reportMissingElementContent(element);
	}

	private void reportMissingElementContent(Element element) {
		report(NLS.bind(PDEMessages.Builders_Feature_empty, element //$NON-NLS-1$
		.getNodeName()), getLine(element), CompilerFlags.ERROR);
	}
	
	protected void reportExtraneousElements(NodeList elements, int maximum) {
		if (elements.getLength() > maximum) {
			for (int i = maximum; i < elements.getLength(); i++) {
				Element element = (Element) elements.item(i);
				report(NLS.bind(PDEMessages.Builders_Feature_multiplicity, element.getNodeName()), getLine(element),
						CompilerFlags.ERROR);
			}
		}
	}

	protected void validateURL(Element element, String attName) {
		String value = element.getAttribute(attName);
		try {
			if (!value.startsWith("http:") && !value.startsWith("file:")) //$NON-NLS-1$ //$NON-NLS-2$
				value = "file:" + value; //$NON-NLS-1$
			new URL(value);
		} catch (MalformedURLException e) {
			report(NLS.bind(PDEMessages.Builders_Feature_badURL, attName), getLine(element, attName), CompilerFlags.ERROR); //$NON-NLS-1$
		}
	}
	
	/**
	 * @param element
	 * @param attr
	 * @return false if failed
	 */
	protected boolean validatePluginID(Element element, Attr attr) {
        if (!IdUtil.isValidPluginId(attr.getValue())) {
            String message = NLS.bind(PDEMessages.Builders_Manifest_pluginId_value, attr.getValue(), attr.getName());
            report(message, getLine(element, attr.getName()),
                    CompilerFlags.WARNING);
            return false;
        }
        return true;
	}

	protected void validateBoolean(Element element, Attr attr) {
		String value = attr.getValue();
		if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) //$NON-NLS-1$ //$NON-NLS-2$
			reportIllegalAttributeValue(element, attr);
	}	

	protected NodeList getChildrenByName(Element element, String name) {
		class NodeListImpl implements NodeList {
			ArrayList nodes = new ArrayList();

			public int getLength() {
				return nodes.size();
			}

			public Node item(int index) {
				return (Node) nodes.get(index);
			}

			protected void add(Node node) {
				nodes.add(node);
			}
		}
		NodeListImpl list = new NodeListImpl();
		NodeList allChildren = element.getChildNodes();
		for (int i = 0; i < allChildren.getLength(); i++) {
			Node node = allChildren.item(i);
			if (name.equals(node.getNodeName())) {
				list.add(node);
			}
		}
		return list;
	}

	protected void reportDeprecatedAttribute(Element element, Attr attr) {
		int severity = CompilerFlags.getFlag(fProject, CompilerFlags.P_DEPRECATED);
		if (severity != CompilerFlags.IGNORE) {
			report(NLS.bind(PDEMessages.Builders_Manifest_deprecated_attribute, attr.getName()), getLine(element, attr.getName()), severity);
		}	
	}
}
