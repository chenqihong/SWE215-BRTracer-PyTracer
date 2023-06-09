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

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.FactoryConfigurationError;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jdt.core.*;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.core.plugin.*;
import org.eclipse.pde.internal.*;
import org.eclipse.pde.internal.core.*;
import org.eclipse.pde.internal.core.ischema.*;
import org.eclipse.pde.internal.core.schema.*;
import org.eclipse.pde.internal.core.util.IdUtil;
import org.w3c.dom.*;
import org.xml.sax.*;

public class ExtensionsErrorReporter extends ManifestErrorReporter {
	
	IPluginModelBase fModel;
	
	public ExtensionsErrorReporter(IFile file) {
		super(file);
		fModel = PDECore.getDefault().getModelManager().findModel(file.getProject());
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.builders.XMLErrorReporter#characters(char[], int, int)
	 */
	public void characters(char[] characters, int start, int length)
			throws SAXException {
	}

	public void validateContent(IProgressMonitor monitor) {
		Element element = getDocumentRoot();
		if (element == null)
			return;
		String elementName = element.getNodeName();
		if (!"plugin".equals(elementName) && !"fragment".equals(elementName)) { //$NON-NLS-1$ //$NON-NLS-2$
			reportIllegalElement(element, CompilerFlags.ERROR);
		} else {
			int severity = CompilerFlags.getFlag(fProject, CompilerFlags.P_DEPRECATED);
			if (severity != CompilerFlags.IGNORE) {
				NamedNodeMap attrs = element.getAttributes();
				for (int i = 0; i < attrs.getLength(); i++) {
					reportUnusedAttribute(element, attrs.item(i).getNodeName(), severity);
				}
			}
			
			NodeList children = element.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				if (monitor.isCanceled())
					break;
				Element child = (Element)children.item(i);
				String name = child.getNodeName();
				if (name.equals("extension")) { //$NON-NLS-1$
					validateExtension(child);
				} else if (name.equals("extension-point")) { //$NON-NLS-1$
					validateExtensionPoint(child);
				} else {
					if (!name.equals("runtime") && !name.equals("requires")) { //$NON-NLS-1$ //$NON-NLS-2$
						severity = CompilerFlags.getFlag(fProject, CompilerFlags.P_UNKNOWN_ELEMENT);
						if (severity != CompilerFlags.IGNORE)
							reportIllegalElement(child, severity);
					} else {
						severity = CompilerFlags.getFlag(fProject, CompilerFlags.P_DEPRECATED);
						if (severity != CompilerFlags.IGNORE)
							reportUnusedElement(child, severity);					
					}
				}
			}
		}
	}

	protected void validateExtension(Element element) {
		if (!assertAttributeDefined(element, "point", CompilerFlags.ERROR)) //$NON-NLS-1$
			return;
		String pointID = element.getAttribute("point"); //$NON-NLS-1$
		IPluginExtensionPoint point = PDECore.getDefault().findExtensionPoint(pointID);
		if (point == null) {
			int severity = CompilerFlags.getFlag(fProject, CompilerFlags.P_UNRESOLVED_EX_POINTS);
			if (severity != CompilerFlags.IGNORE) {
				report(NLS.bind(PDEMessages.Builders_Manifest_ex_point, pointID), //$NON-NLS-1$
					getLine(element, "point"), severity); //$NON-NLS-1$
			}
		} else {
			SchemaRegistry registry = PDECore.getDefault().getSchemaRegistry();
			ISchema schema = registry.getSchema(pointID);
			if (schema != null) {
				validateElement(element, schema);
			}
		}
	}
	
	protected void validateElement(Element element, ISchema schema) {
		String elementName = element.getNodeName();
		ISchemaElement schemaElement = schema.findElement(elementName);
		
		ISchemaElement parentSchema = null;
		if (!"extension".equals(elementName)) { //$NON-NLS-1$
			Node parent = element.getParentNode();
			parentSchema = schema.findElement(parent.getNodeName());
		}
		
		if (parentSchema != null) {
			int severity = CompilerFlags.getFlag(fProject, CompilerFlags.P_UNKNOWN_ELEMENT);
			if (severity != CompilerFlags.IGNORE) {
				HashSet allowedElements = new HashSet();
				computeAllowedElements(parentSchema.getType(), allowedElements);
				if (!allowedElements.contains(elementName)) {
					reportIllegalElement(element, severity);
					return;
				}
			}
			
		}	
		if (schemaElement == null && parentSchema != null) {
			ISchemaAttribute attr = parentSchema.getAttribute(elementName);
			if (attr != null && attr.getKind() == IMetaAttribute.JAVA) {
				if (attr.isDeprecated())
					reportDeprecatedAttribute(element, element.getAttributeNode("class")); //$NON-NLS-1$
				validateJavaAttribute(element, element.getAttributeNode("class")); //$NON-NLS-1$				
			}
		} else {
			if (schemaElement != null) {
				validateRequiredExtensionAttributes(element, schemaElement);
				validateExistingExtensionAttributes(element, element.getAttributes(), schemaElement);
				if (schemaElement.isDeprecated())
					reportDeprecatedElement(element);
				if (schemaElement.hasTranslatableContent())
					validateTranslatableElementContent(element);
			}
			NodeList children = element.getChildNodes();
			for (int i = 0; i < children.getLength(); i++) {
				validateElement((Element)children.item(i), schema);
			}
		}
	}
	
	private void computeAllowedElements(ISchemaType type, HashSet elementSet) {
		if (type instanceof ISchemaComplexType) {
			ISchemaComplexType complexType = (ISchemaComplexType) type;
			ISchemaCompositor compositor = complexType.getCompositor();
			if (compositor != null)
				computeAllowedElements(compositor, elementSet);

			ISchemaAttribute[] attrs = complexType.getAttributes();
			for (int i = 0; i < attrs.length; i++) {
				if (attrs[i].getKind() == IMetaAttribute.JAVA)
					elementSet.add(attrs[i].getName());
			}
		}
	}

	private void computeAllowedElements(ISchemaCompositor compositor,
			HashSet elementSet) {
		ISchemaObject[] children = compositor.getChildren();
		for (int i = 0; i < children.length; i++) {
			ISchemaObject child = children[i];
			if (child instanceof ISchemaObjectReference) {
				ISchemaObjectReference ref = (ISchemaObjectReference) child;
				ISchemaElement refElement = (ISchemaElement) ref
						.getReferencedObject();
				if (refElement != null)
					elementSet.add(refElement.getName());
			} else if (child instanceof ISchemaCompositor) {
				computeAllowedElements((ISchemaCompositor) child, elementSet);
			}
		}
	}


	
	private void validateRequiredExtensionAttributes(Element element, ISchemaElement schemaElement) {
		int severity = CompilerFlags.getFlag(fProject, CompilerFlags.P_NO_REQUIRED_ATT);
		if (severity == CompilerFlags.IGNORE)
			return;
		
		ISchemaAttribute[] attInfos = schemaElement.getAttributes();
		for (int i = 0; i < attInfos.length; i++) {
			ISchemaAttribute attInfo = attInfos[i];
			if (attInfo.getUse() == ISchemaAttribute.REQUIRED) {
				boolean found = element.getAttributeNode(attInfo.getName()) != null;
				if (!found && attInfo.getKind() == IMetaAttribute.JAVA) {
					NodeList children = element.getChildNodes();
					for (int j = 0; j < children.getLength(); j++) {
						if (attInfo.getName().equals(children.item(j).getNodeName())) {
							found = true;
							break;
						}
					}
				}
				if (!found) {
					reportMissingRequiredAttribute(element, attInfo.getName(), severity);
				}
			}
		}
	}
	
	private void validateExistingExtensionAttributes(Element element, NamedNodeMap attrs,
			ISchemaElement schemaElement) {
		for (int i = 0; i < attrs.getLength(); i++) {
			Attr attr = (Attr)attrs.item(i);
			ISchemaAttribute attInfo = schemaElement.getAttribute(attr.getName());
			if (attInfo == null) {
				HashSet allowedElements = new HashSet();
				computeAllowedElements(schemaElement.getType(), allowedElements);
				if (allowedElements.contains(attr.getName())) {
					validateJavaAttribute(element, attr);
				} else {
					int flag = CompilerFlags.getFlag(fProject, CompilerFlags.P_UNKNOWN_ATTRIBUTE);
					if (flag != CompilerFlags.IGNORE)
						reportUnknownAttribute(element, attr.getName(), flag);
				}
			} else {
				validateExtensionAttribute(element, attr, attInfo);
			}
		}
	}

	private void validateExtensionAttribute(Element element, Attr attr, ISchemaAttribute attInfo) {
		ISchemaSimpleType type = attInfo.getType();
		ISchemaRestriction restriction = type.getRestriction();
		if (restriction != null) {
			validateRestrictionAttribute(element, attr, restriction);
		}
		
		int kind = attInfo.getKind();
		if (kind == IMetaAttribute.JAVA) {
			validateJavaAttribute(element, attr);
		} else if (kind == IMetaAttribute.RESOURCE) {
			validateResourceAttribute(element, attr);
		} else if (type.getName().equals("boolean")) { //$NON-NLS-1$
			validateBoolean(element, attr);
		} 
		
		validateTranslatableString(element, attr, attInfo.isTranslatable());
		
		if (attInfo.isDeprecated()) {
			reportDeprecatedAttribute(element, attr);
		}
	}

	protected void validateExtensionPoint(Element element) {
		if (assertAttributeDefined(element, "id", CompilerFlags.ERROR)) { //$NON-NLS-1$
            Attr idAttr = element.getAttributeNode("id"); //$NON-NLS-1$
            if (!IdUtil.isValidExtensionPointId(idAttr.getValue())) {
                String message = NLS.bind(PDEMessages.Builders_Manifest_extensionPointId_value, idAttr.getValue());
                report(message, getLine(element, idAttr.getName()), CompilerFlags.WARNING);
            }
        }

		assertAttributeDefined(element, "name", CompilerFlags.ERROR); //$NON-NLS-1$
		
		int severity = CompilerFlags.getFlag(fProject, CompilerFlags.P_UNKNOWN_ATTRIBUTE);
		NamedNodeMap attrs = element.getAttributes();
		for (int i = 0; i < attrs.getLength(); i++) {
			Attr attr = (Attr)attrs.item(i);
			String name = attr.getName();
			if ("name".equals(name)) { //$NON-NLS-1$
				validateTranslatableString(element, attr, true);
			} else if (!"id".equals(name) && !"schema".equals(name) && severity != CompilerFlags.IGNORE) { //$NON-NLS-1$ //$NON-NLS-2$
				reportUnknownAttribute(element, name, severity);
			}
		}
		
		severity = CompilerFlags.getFlag(fProject, CompilerFlags.P_UNKNOWN_ELEMENT);
		if (severity != CompilerFlags.IGNORE) {
			NodeList children = element.getChildNodes();
			for (int i = 0; i < children.getLength(); i++)
				reportIllegalElement((Element)children.item(i), severity);
		}
	}
		
	protected void validateTranslatableString(Element element, Attr attr, boolean shouldTranslate) {
		int severity = CompilerFlags.getFlag(fProject, CompilerFlags.P_NOT_EXTERNALIZED);
		if (severity == CompilerFlags.IGNORE)
			return;
		String value = attr.getValue();
		if (shouldTranslate) {
			if (!value.startsWith("%")) { //$NON-NLS-1$
				report(NLS.bind(PDEMessages.Builders_Manifest_non_ext_attribute, attr.getName()), getLine(element, attr.getName()), severity); //$NON-NLS-1$
			} else if (fModel != null && fModel instanceof AbstractModel) {
				NLResourceHelper helper = ((AbstractModel)fModel).getNLResourceHelper();
				if (helper == null || !helper.resourceExists(value)) {
					report(NLS.bind(PDEMessages.Builders_Manifest_key_not_found, value.substring(1)), getLine(element, attr.getName()), severity); //$NON-NLS-1$
				}
			}
		} 
	}
	
	protected void validateTranslatableElementContent(Element element) {
		int severity = CompilerFlags.getFlag(fProject, CompilerFlags.P_NOT_EXTERNALIZED);
		if (severity == CompilerFlags.IGNORE)
			return;
		String value = getTextContent(element);
		if (value == null)
			return;
		if (!value.startsWith("%")) { //$NON-NLS-1$
			report(NLS.bind(PDEMessages.Builders_Manifest_non_ext_element, element.getNodeName()), getLine(element), severity); //$NON-NLS-1$
		} else if (fModel != null && fModel instanceof AbstractModel) {
			NLResourceHelper helper = ((AbstractModel)fModel).getNLResourceHelper();
			if (helper == null || !helper.resourceExists(value)) {
				report(NLS.bind(PDEMessages.Builders_Manifest_key_not_found, value.substring(1)), getLine(element), severity); //$NON-NLS-1$
			}
		}
	}

	protected void validateResourceAttribute(Element element, Attr attr) {
		int severity = CompilerFlags.getFlag(fProject, CompilerFlags.P_UNKNOWN_RESOURCE);
		if (severity != CompilerFlags.IGNORE && !resourceExists(attr.getValue())) {
			report(NLS.bind(PDEMessages.Builders_Manifest_resource, (new String[] { attr.getValue(), attr.getName() })),  //$NON-NLS-1$
							getLine(element,
							attr.getName()), 
							severity);
		}
	}
	
	private boolean resourceExists(String location) {
		String bundleJar=null;
		IPath path = new Path(location);
		if ("platform:".equals(path.getDevice()) && path.segmentCount() > 2) { //$NON-NLS-1$
			if ("plugin".equals(path.segment(0))) { //$NON-NLS-1$
				String id = path.segment(1);
				IPluginModelBase model = PDECore.getDefault().getModelManager().findModel(id);
				if (model != null && model.isEnabled()) {
					path = path.setDevice(null).removeFirstSegments(2);
					String bundleLocation = model.getInstallLocation();
					if(bundleLocation.endsWith(".jar")){ //$NON-NLS-1$
						bundleJar=bundleLocation;
					} else {
						path = new Path(model.getInstallLocation()).append(path);
					}
					location = path.toString();
				}
			}
		} else if (path.getDevice()==null && path.segmentCount() > 3 && "platform:".equals(path.segment(0))){ //$NON-NLS-1$
			if ("plugin".equals(path.segment(1))) { //$NON-NLS-1$
				String id = path.segment(2);
				IPluginModelBase model = PDECore.getDefault().getModelManager().findModel(id);
				if (model != null && model.isEnabled()) {
					path = path.removeFirstSegments(3);
					String bundleLocation = model.getInstallLocation();
					if(bundleLocation.endsWith(".jar")){ //$NON-NLS-1$
						bundleJar=bundleLocation;
					} else {
						path = new Path(model.getInstallLocation()).append(path);
					}
					location = path.toString();
				}
			}
		}

		ArrayList paths = new ArrayList();		
		if (location.indexOf("$nl$") != -1) { //$NON-NLS-1$
			StringTokenizer tokenizer = new StringTokenizer(TargetPlatform.getNL(), "_");	 //$NON-NLS-1$
			String language = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : null;
			String country = tokenizer.hasMoreTokens() ? tokenizer.nextToken() : null;
			if (language != null && country != null)
				paths.add(location
						.replaceAll(
								"\\$nl\\$", "nl" + IPath.SEPARATOR + language + IPath.SEPARATOR + country)); //$NON-NLS-1$ //$NON-NLS-2$
			if (language != null)
				paths.add(location.replaceAll(
						"\\$nl\\$", "nl" + IPath.SEPARATOR + language)); //$NON-NLS-1$ //$NON-NLS-2$
			paths.add(location.replaceAll("\\$nl\\$", "")); //$NON-NLS-1$ //$NON-NLS-2$
		} else {
			paths.add(location);
		}
		
		for (int i = 0; i < paths.size(); i++) {
			if (bundleJar == null) {
				IPath currPath = new Path(paths.get(i).toString());
				if (currPath.isAbsolute() && currPath.toFile().exists())
					return true;
				if (fFile.getProject().findMember(currPath) != null)
					return true;
			} else {
				if (jarContainsResource(bundleJar, paths.get(i).toString()))
					return true;
			}
		}
		
		return false;
	}

	private boolean jarContainsResource(String path, String resource) {
		ZipFile jarFile = null;
		try {
			File file = new File(path);
			jarFile = new ZipFile(file, ZipFile.OPEN_READ);
			ZipEntry resourceEntry = jarFile.getEntry(resource); //$NON-NLS-1$
			if (resourceEntry != null)
				return true;
		} catch (IOException e) {
		} catch (FactoryConfigurationError e) {
		} finally {
			try {
				if (jarFile != null)
					jarFile.close();
			} catch (IOException e2) {
			}
		}
		return false;
	}

	protected void validateJavaAttribute(Element element, Attr attr) {
		int severity = CompilerFlags.getFlag(fProject, CompilerFlags.P_UNKNOWN_CLASS);
		if (severity == CompilerFlags.IGNORE)
			return;

		String value = attr.getValue();
		IJavaProject javaProject = JavaCore.create(fFile.getProject());
		try {
			// be careful: people have the option to use the format:
			// fullqualifiedName:staticMethod
			int index = value.indexOf(":"); //$NON-NLS-1$
			if (index != -1)
				value = value.substring(0, index);

			IType javaType = javaProject.findType(value);
			if (javaType == null && value.indexOf('$') != -1)
				javaType = javaProject.findType(value.replace('$', '.'));
		
			if (javaType == null) {
				report(NLS.bind(PDEMessages.Builders_Manifest_class, (new String[] { value, attr.getName() })), getLine(
						element, attr.getName()), severity);
			} 
		} catch (JavaModelException e) {
		}
	}
	
	protected void validateRestrictionAttribute(Element element, Attr attr, ISchemaRestriction restriction) {
		Object[] children = restriction.getChildren();
		String value = attr.getValue();
		for (int i = 0; i < children.length; i++) {
			Object child = children[i];
			if (child instanceof ISchemaEnumeration) {
				ISchemaEnumeration enumeration = (ISchemaEnumeration) child;
				if (enumeration.getName().equals(value)) {
					return;
				}
			}
		}
		reportIllegalAttributeValue(element, attr);
	}
	
	protected void reportUnusedAttribute(Element element, String attName, int severity) {
		String message = NLS.bind(PDEMessages.Builders_Manifest_unused_attribute, attName);
		report(message, getLine(element, attName), severity);
	}
	
	protected void reportUnusedElement(Element element, int severity) {
		Node parent = element.getParentNode();
			report(NLS.bind(PDEMessages.Builders_Manifest_unused_element, (new String[] { //$NON-NLS-1$
			element.getNodeName(), parent.getNodeName() })),
					getLine(element), severity);
	}
	
	protected void reportDeprecatedElement(Element element) {
		int severity = CompilerFlags.getFlag(fProject, CompilerFlags.P_DEPRECATED);
		if (severity != CompilerFlags.IGNORE) {
			report(NLS.bind(PDEMessages.Builders_Manifest_deprecated_element, element.getNodeName()), getLine(element), severity);
		}	
	}

}
