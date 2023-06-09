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

import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchScope;

/**
 * A <tt>JarFileEntryTypeInfo</tt> represents a type in a Jar file.
 */
public class JarFileEntryTypeInfo extends TypeInfo {
	
	private final String fJar;
	private final String fFileName;
	private final String fExtension;
	
	public JarFileEntryTypeInfo(String pkg, String name, char[][] enclosingTypes, int modifiers, String jar, String fileName, String extension) {
		super(pkg, name, enclosingTypes, modifiers);
		fJar= jar;
		fFileName= fileName;
		fExtension= extension;
	}
	
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!JarFileEntryTypeInfo.class.equals(obj.getClass()))
			return false;
		JarFileEntryTypeInfo other= (JarFileEntryTypeInfo)obj;
		return doEquals(other) && fJar.equals(other.fJar) && 
			fFileName.equals(other.fFileName) && fExtension.equals(other.fExtension);
	}
	
	public int getElementType() {
		return TypeInfo.JAR_FILE_ENTRY_TYPE_INFO;
	}
	
	public String getJar() {
		return fJar;
	}
	
	public String getFileName() {
		return fFileName;
	}
	
	public String getExtension() {
		return fExtension;
	}
	
	protected IJavaElement getContainer(IJavaSearchScope scope) throws JavaModelException {
		IJavaModel jmodel= JavaCore.create(ResourcesPlugin.getWorkspace().getRoot());
		IPath[] enclosedPaths= scope.enclosingProjectsAndJars();

		for (int i= 0; i < enclosedPaths.length; i++) {
			IPath curr= enclosedPaths[i];
			if (curr.segmentCount() == 1) {
				IJavaProject jproject= jmodel.getJavaProject(curr.segment(0));
				IPackageFragmentRoot root= jproject.getPackageFragmentRoot(fJar);
				if (root.exists()) {
					return findElementInRoot(root);
				}
			}
		}
		List paths= Arrays.asList(enclosedPaths);
		IJavaProject[] projects= jmodel.getJavaProjects();
		for (int i= 0; i < projects.length; i++) {
			IJavaProject jproject= projects[i];
			if (!paths.contains(jproject.getPath())) {
				IPackageFragmentRoot root= jproject.getPackageFragmentRoot(fJar);
				if (root.exists()) {
					return findElementInRoot(root);
				}
			}
		}
		return null;
	}
	
	private IJavaElement findElementInRoot(IPackageFragmentRoot root) {
		IJavaElement res;
		IPackageFragment frag= root.getPackageFragment(getPackageName());
		String extension= getExtension();

		if ("class".equals(extension)) { //$NON-NLS-1$
			res=  frag.getClassFile(getFileName() + ".class"); //$NON-NLS-1$
		} else if ("java".equals(extension)) { //$NON-NLS-1$
			res=  frag.getCompilationUnit(getFileName() + ".java"); //$NON-NLS-1$
		} else {
			return null;
		}
		if (res.exists()) {
			return res;
		}
		return null;
	}
	
	public IPath getPackageFragmentRootPath() {
		return new Path(fJar);
	}
	
	public String getPackageFragmentRootName() {
		// we can't remove the '/' since the jar can be external.
		return fJar;
	}
		
	public String getPath() {
		StringBuffer result= new StringBuffer(fJar);
		result.append(IJavaSearchScope.JAR_FILE_ENTRY_SEPARATOR);
		getElementPath(result);
		return result.toString();
	}
		
	private void getElementPath(StringBuffer result) {
		String pack= getPackageName();
		if (pack != null && pack.length() > 0) {
			result.append(pack.replace(TypeInfo.PACKAGE_PART_SEPARATOR, TypeInfo.SEPARATOR));
			result.append(TypeInfo.SEPARATOR);
		}
		result.append(getFileName());
		result.append('.');
		result.append(getExtension());
	}
}
