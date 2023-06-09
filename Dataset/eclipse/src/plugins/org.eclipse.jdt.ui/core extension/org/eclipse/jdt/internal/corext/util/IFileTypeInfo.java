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
package org.eclipse.jdt.internal.corext.util;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchScope;

/**
 * A <tt>IFileTypeInfo</tt> represents a type in a class or java file.
 */
public class IFileTypeInfo extends TypeInfo {
	
	private final String fProject;
	private final String fFolder;
	private final String fFile;
	private final String fExtension;
	
	public IFileTypeInfo(String pkg, String name, char[][] enclosingTypes, int modifiers, String project, String sourceFolder, String file, String extension) {
		super(pkg, name, enclosingTypes, modifiers);
		fProject= project;
		fFolder= sourceFolder;
		fFile= file;
		fExtension= extension;
	}
	
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!IFileTypeInfo.class.equals(obj.getClass()))
			return false;
		IFileTypeInfo other= (IFileTypeInfo)obj;
		return doEquals(other) && fProject.equals(other.fProject) && equals(fFolder, other.fFolder) &&
			fFile.equals(other.fFile) && fExtension.equals(other.fExtension);
	}
	
	public int getElementType() {
		return TypeInfo.IFILE_TYPE_INFO;
	}
	
	protected IJavaElement getContainer(IJavaSearchScope scope) {
		IWorkspaceRoot root= ResourcesPlugin.getWorkspace().getRoot();
		IPath path= new Path(getPath());
		IResource resource= root.findMember(path);
		if (resource != null) {
			IJavaElement elem= JavaCore.create(resource);
			if (elem.exists()) {
				return elem;
			}
		}
		return null;
	}
	
	public IPath getPackageFragmentRootPath() {
		StringBuffer buffer= new StringBuffer();
		buffer.append(TypeInfo.SEPARATOR);
		buffer.append(fProject);
		if (fFolder != null && fFolder.length() > 0) {
			buffer.append(TypeInfo.SEPARATOR);
			buffer.append(fFolder);
		}
		return new Path(buffer.toString());
	}
	
	public String getPackageFragmentRootName() {
		StringBuffer buffer= new StringBuffer();
		buffer.append(fProject);
		if (fFolder != null && fFolder.length() > 0) {
			buffer.append(TypeInfo.SEPARATOR);
			buffer.append(fFolder);
		}
		return buffer.toString();
	}
		
	public String getPath() {
		StringBuffer result= new StringBuffer();
		result.append(TypeInfo.SEPARATOR);
		result.append(fProject);
		result.append(TypeInfo.SEPARATOR);
		if (fFolder != null && fFolder.length() > 0) {
				result.append(fFolder);
				result.append(TypeInfo.SEPARATOR);
		}
		if (fPackage != null && fPackage.length() > 0) {
			result.append(fPackage.replace(TypeInfo.PACKAGE_PART_SEPARATOR, TypeInfo.SEPARATOR));
			result.append(TypeInfo.SEPARATOR);
		}
		result.append(fFile);
		result.append('.');
		result.append(fExtension);
		return result.toString();
	}
	
	public String getProject() {
		return fProject;
	}
	
	public String getFolder() {
		return fFolder;
	}
	
	public String getFileName() {
		return fFile;
	}
	
	public String getExtension() {
		return fExtension;
	}
}
