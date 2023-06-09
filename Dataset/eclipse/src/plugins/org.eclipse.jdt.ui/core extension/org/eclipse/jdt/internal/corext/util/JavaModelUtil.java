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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;

import org.eclipse.jdt.core.*;
import org.eclipse.jdt.core.compiler.CharOperation;
import org.eclipse.jdt.core.dom.PrimitiveType;

import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMInstall2;
import org.eclipse.jdt.launching.JavaRuntime;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Utility methods for the Java Model.
 */
public final class JavaModelUtil {
	
	/** 
	 * Finds a type by its qualified type name (dot separated).
	 * @param jproject The java project to search in
	 * @param fullyQualifiedName The fully qualified name (type name with enclosing type names and package (all separated by dots))
	 * @return The type found, or null if not existing
	 */	
	public static IType findType(IJavaProject jproject, String fullyQualifiedName) throws JavaModelException {
		//workaround for bug 22883
		IType type= jproject.findType(fullyQualifiedName);
		if (type != null)
			return type;
		IPackageFragmentRoot[] roots= jproject.getPackageFragmentRoots();
		for (int i= 0; i < roots.length; i++) {
			IPackageFragmentRoot root= roots[i];
			type= findType(root, fullyQualifiedName);
			if (type != null && type.exists())
				return type;
		}	
		return null;
	}
	
	/** 
	 * Finds a type by its qualified type name (dot separated).
	 * @param jproject The java project to search in
	 * @param fullyQualifiedName The fully qualified name (type name with enclosing type names and package (all separated by dots))
	 * @param owner the workingcopy owner
	 * @return The type found, or null if not existing
	 */	
	public static IType findType(IJavaProject jproject, String fullyQualifiedName, WorkingCopyOwner owner) throws JavaModelException {
		//workaround for bug 22883
		IType type= jproject.findType(fullyQualifiedName, owner);
		if (type != null)
			return type;
		IPackageFragmentRoot[] roots= jproject.getPackageFragmentRoots();
		for (int i= 0; i < roots.length; i++) {
			IPackageFragmentRoot root= roots[i];
			type= findType(root, fullyQualifiedName);
			if (type != null && type.exists())
				return type;
		}	
		return null;
	}
	
	/**
	 * Returns <code>true</code> if the given package fragment root is
	 * referenced. This means it is own by a different project but is referenced
	 * by the root's parent. Returns <code>false</code> if the given root
	 * doesn't have an underlying resource.
	 */
	public static boolean isReferenced(IPackageFragmentRoot root) {
		IResource resource= root.getResource();
		if (resource != null) {
			IProject jarProject= resource.getProject();
			IProject container= root.getJavaProject().getProject();
			return !container.equals(jarProject);
		}
		return false;
	}
	
	private static IType findType(IPackageFragmentRoot root, String fullyQualifiedName) throws JavaModelException{
		IJavaElement[] children= root.getChildren();
		for (int i= 0; i < children.length; i++) {
			IJavaElement element= children[i];
			if (element.getElementType() == IJavaElement.PACKAGE_FRAGMENT){
				IPackageFragment pack= (IPackageFragment)element;
				if (! fullyQualifiedName.startsWith(pack.getElementName()))
					continue;
				IType type= findType(pack, fullyQualifiedName);
				if (type != null && type.exists())
					return type;
			}
		}		
		return null;
	}
	
	private static IType findType(IPackageFragment pack, String fullyQualifiedName) throws JavaModelException{
		ICompilationUnit[] cus= pack.getCompilationUnits();
		for (int i= 0; i < cus.length; i++) {
			ICompilationUnit unit= cus[i];
			IType type= findType(unit, fullyQualifiedName);
			if (type != null && type.exists())
				return type;
		}
		return null;
	}
	
	private static IType findType(ICompilationUnit cu, String fullyQualifiedName) throws JavaModelException{
		IType[] types= cu.getAllTypes();
		for (int i= 0; i < types.length; i++) {
			IType type= types[i];
			if (getFullyQualifiedName(type).equals(fullyQualifiedName))
				return type;
		}
		return null;
	}
	
	/** 
	 * Finds a type by package and type name.
	 * @param jproject the java project to search in
	 * @param pack The package name
	 * @param typeQualifiedName the type qualified name (type name with enclosing type names (separated by dots))
	 * @return the type found, or null if not existing
	 * @deprecated Use IJavaProject.findType(String, String) instead
	 */	
	public static IType findType(IJavaProject jproject, String pack, String typeQualifiedName) throws JavaModelException {
		return jproject.findType(pack, typeQualifiedName);
	}

	/**
	 * Finds a type container by container name.
	 * The returned element will be of type <code>IType</code> or a <code>IPackageFragment</code>.
	 * <code>null</code> is returned if the type container could not be found.
	 * @param jproject The Java project defining the context to search
	 * @param typeContainerName A dot separated name of the type container
	 * @see #getTypeContainerName(IType)
	 */
	public static IJavaElement findTypeContainer(IJavaProject jproject, String typeContainerName) throws JavaModelException {
		// try to find it as type
		IJavaElement result= jproject.findType(typeContainerName);
		if (result == null) {
			// find it as package
			IPath path= new Path(typeContainerName.replace('.', '/'));
			result= jproject.findElement(path);
			if (!(result instanceof IPackageFragment)) {
				result= null;
			}
			
		}
		return result;
	}	
	
	/** 
	 * Finds a type in a compilation unit. Typical usage is to find the corresponding
	 * type in a working copy.
	 * @param cu the compilation unit to search in
	 * @param typeQualifiedName the type qualified name (type name with enclosing type names (separated by dots))
	 * @return the type found, or null if not existing
	 */		
	public static IType findTypeInCompilationUnit(ICompilationUnit cu, String typeQualifiedName) throws JavaModelException {
		IType[] types= cu.getAllTypes();
		for (int i= 0; i < types.length; i++) {
			String currName= getTypeQualifiedName(types[i]);
			if (typeQualifiedName.equals(currName)) {
				return types[i];
			}
		}
		return null;
	}
		
	/** 
	 * Finds a a member in a compilation unit. Typical usage is to find the corresponding
	 * member in a working copy.
	 * @param cu the compilation unit (eg. working copy) to search in
	 * @param member the member (eg. from the original)
	 * @return the member found, or null if not existing
	 */		
	public static IMember findMemberInCompilationUnit(ICompilationUnit cu, IMember member) {
		IJavaElement[] elements= cu.findElements(member);
		if (elements != null && elements.length > 0) {
			return (IMember) elements[0];
		}
		return null;
	}
	
	
	/** 
	 * Returns the element of the given compilation unit which is "equal" to the
	 * given element. Note that the given element usually has a parent different
	 * from the given compilation unit.
	 * 
	 * @param cu the cu to search in
	 * @param element the element to look for
	 * @return an element of the given cu "equal" to the given element
	 */		
	public static IJavaElement findInCompilationUnit(ICompilationUnit cu, IJavaElement element) {
		IJavaElement[] elements= cu.findElements(element);
		if (elements != null && elements.length > 0) {
			return elements[0];
		}
		return null;
	}
	
	/**
	 * Returns the qualified type name of the given type using '.' as separators.
	 * This is a replace for IType.getTypeQualifiedName()
	 * which uses '$' as separators. As '$' is also a valid character in an id
	 * this is ambiguous. JavaCore PR: 1GCFUNT
	 */
	public static String getTypeQualifiedName(IType type) {
		return type.getTypeQualifiedName('.');
	}
	
	/**
	 * Returns the fully qualified name of the given type using '.' as separators.
	 * This is a replace for IType.getFullyQualifiedTypeName
	 * which uses '$' as separators. As '$' is also a valid character in an id
	 * this is ambiguous. JavaCore PR: 1GCFUNT
	 */
	public static String getFullyQualifiedName(IType type) {
		return type.getFullyQualifiedName('.');
	}
	
	/**
	 * Returns the fully qualified name of a type's container. (package name or enclosing type name)
	 */
	public static String getTypeContainerName(IType type) {
		IType outerType= type.getDeclaringType();
		if (outerType != null) {
			return outerType.getFullyQualifiedName('.');
		} else {
			return type.getPackageFragment().getElementName();
		}
	}
	
	
	/**
	 * Concatenates two names. Uses a dot for separation.
	 * Both strings can be empty or <code>null</code>.
	 */
	public static String concatenateName(String name1, String name2) {
		StringBuffer buf= new StringBuffer();
		if (name1 != null && name1.length() > 0) {
			buf.append(name1);
		}
		if (name2 != null && name2.length() > 0) {
			if (buf.length() > 0) {
				buf.append('.');
			}
			buf.append(name2);
		}		
		return buf.toString();
	}
	
	/**
	 * Concatenates two names. Uses a dot for separation.
	 * Both strings can be empty or <code>null</code>.
	 */
	public static String concatenateName(char[] name1, char[] name2) {
		StringBuffer buf= new StringBuffer();
		if (name1 != null && name1.length > 0) {
			buf.append(name1);
		}
		if (name2 != null && name2.length > 0) {
			if (buf.length() > 0) {
				buf.append('.');
			}
			buf.append(name2);
		}		
		return buf.toString();
	}	
	
	/**
	 * Evaluates if a member (possible from another package) is visible from
	 * elements in a package.
	 * @param member The member to test the visibility for
	 * @param pack The package in focus
	 */
	public static boolean isVisible(IMember member, IPackageFragment pack) throws JavaModelException {
		
		int type= member.getElementType();
		if  (type == IJavaElement.INITIALIZER ||  (type == IJavaElement.METHOD && member.getElementName().startsWith("<"))) { //$NON-NLS-1$
			return false;
		}
		
		int otherflags= member.getFlags();
		IType declaringType= member.getDeclaringType();
		if (Flags.isPublic(otherflags) || (declaringType != null && isInterfaceOrAnnotation(declaringType))) {
			return true;
		} else if (Flags.isPrivate(otherflags)) {
			return false;
		}		
		
		IPackageFragment otherpack= (IPackageFragment) findParentOfKind(member, IJavaElement.PACKAGE_FRAGMENT);
		return (pack != null && otherpack != null && isSamePackage(pack, otherpack));
	}
	
	/**
	 * Evaluates if a member in the focus' element hierarchy is visible from
	 * elements in a package.
	 * @param member The member to test the visibility for
	 * @param pack The package of the focus element focus
	 */
	public static boolean isVisibleInHierarchy(IMember member, IPackageFragment pack) throws JavaModelException {
		int type= member.getElementType();
		if  (type == IJavaElement.INITIALIZER ||  (type == IJavaElement.METHOD && member.getElementName().startsWith("<"))) { //$NON-NLS-1$
			return false;
		}
		
		int otherflags= member.getFlags();
		
		IType declaringType= member.getDeclaringType();
		if (Flags.isPublic(otherflags) || Flags.isProtected(otherflags) || (declaringType != null && isInterfaceOrAnnotation(declaringType))) {
			return true;
		} else if (Flags.isPrivate(otherflags)) {
			return false;
		}		
		
		IPackageFragment otherpack= (IPackageFragment) findParentOfKind(member, IJavaElement.PACKAGE_FRAGMENT);
		return (pack != null && pack.equals(otherpack));
	}
			
		
	/**
	 * Returns the package fragment root of <code>IJavaElement</code>. If the given
	 * element is already a package fragment root, the element itself is returned.
	 */
	public static IPackageFragmentRoot getPackageFragmentRoot(IJavaElement element) {
		return (IPackageFragmentRoot) element.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
	}

	/**
	 * Returns the parent of the supplied java element that conforms to the given 
	 * parent type or <code>null</code>, if such a parent doesn't exit.
	 * @deprecated Use element.getParent().getAncestor(kind);
	 */
	public static IJavaElement findParentOfKind(IJavaElement element, int kind) {
		if (element != null && element.getParent() != null) {
			return element.getParent().getAncestor(kind);
		}
		return null;
	}
	
	/**
	 * Finds a method in a type.
	 * This searches for a method with the same name and signature. Parameter types are only
	 * compared by the simple name, no resolving for the fully qualified type name is done.
	 * Constructors are only compared by parameters, not the name.
	 * @param name The name of the method to find
	 * @param paramTypes The type signatures of the parameters e.g. <code>{"QString;","I"}</code>
	 * @param isConstructor If the method is a constructor
	 * @return The first found method or <code>null</code>, if nothing found
	 */
	public static IMethod findMethod(String name, String[] paramTypes, boolean isConstructor, IType type) throws JavaModelException {
		return findMethod(name, paramTypes, isConstructor, type.getMethods());
	}

	/**
	 * Finds a method by name.
	 * This searches for a method with a name and signature. Parameter types are only
	 * compared by the simple name, no resolving for the fully qualified type name is done.
	 * Constructors are only compared by parameters, not the name.
	 * @param name The name of the method to find
	 * @param paramTypes The type signatures of the parameters e.g. <code>{"QString;","I"}</code>
	 * @param isConstructor If the method is a constructor
	 * @param methods The methods to search in
	 * @return The found method or <code>null</code>, if nothing found
	 */
	public static IMethod findMethod(String name, String[] paramTypes, boolean isConstructor, IMethod[] methods) throws JavaModelException {
		for (int i= methods.length - 1; i >= 0; i--) {
			if (isSameMethodSignature(name, paramTypes, isConstructor, methods[i])) {
				return methods[i];
			}
		}
		return null;
	}
	
	/**
	 * Finds a method. This searches for a method with a name and signature. Parameter types are only
	 * compared by the simple name, no resolving for the fully qualified type name is done.
	 * Constructors are only compared by parameters, not the name.
	 * @param curr The method to find
	 * @param methods The methods to search in
	 * @return The found method or <code>null</code>, if nothing found
	 */
	public static IMethod findMethod2(IMethod curr, IMethod[] methods) throws JavaModelException {
		for (int i= methods.length - 1; i >= 0; i--) {
			if (isSameMethodSignature2(curr, methods[i])) {
				return methods[i];
			}
		}
		return null;
	}
	

	/**
	 * Finds a method declaration in a type's hierarchy. The search is top down, so this
	 * returns the first declaration of the method in the hierarchy.
	 * This searches for a method with a name and signature. Parameter types are only
	 * compared by the simple name, no resolving for the fully qualified type name is done.
	 * Constructors are only compared by parameters, not the name.
	 * @param type Searches in this type's supertypes.
	 * @param name The name of the method to find
	 * @param paramTypes The type signatures of the parameters e.g. <code>{"QString;","I"}</code>
	 * @param isConstructor If the method is a constructor
	 * @return The first method found or null, if nothing found
	 */
	public static IMethod findMethodDeclarationInHierarchy(ITypeHierarchy hierarchy, IType type, String name, String[] paramTypes, boolean isConstructor) throws JavaModelException {
		IType[] superTypes= hierarchy.getAllSupertypes(type);
		for (int i= superTypes.length - 1; i >= 0; i--) {
			IMethod first= findMethod(name, paramTypes, isConstructor, superTypes[i]);
			if (first != null && !Flags.isPrivate(first.getFlags())) {
				// the order getAllSupertypes does make assumptions of the order of inner elements -> search recursively
				IMethod res= findMethodDeclarationInHierarchy(hierarchy, first.getDeclaringType(), name, paramTypes, isConstructor);
				if (res != null) {
					return res;
				}
				return first;
			}
		}
		return null;
	}
	
	/**
	 * Finds a method declaration in a type's hierarchy. The search is top down, so this
	 * returns the first declaration of the method in the hierarchy.
	 * This searches for a method with a name and signature. Parameter types are only
	 * compared by the simple name, no resolving for the fully qualified type name is done.
	 * Constructors are only compared by parameters, not the name.
	 * @param type Searches in this type's supertypes.
	 * @param method The method to find
	 * @return The first method found or null, if nothing found
	 */
	public static IMethod findMethodDeclarationInHierarchy2(ITypeHierarchy hierarchy, IType type, IMethod method) throws JavaModelException {
		IType[] superTypes= hierarchy.getAllSupertypes(type);
		for (int i= superTypes.length - 1; i >= 0; i--) {
			IMethod first= findMethod2(method, superTypes[i].getMethods());
			if (first != null && !Flags.isPrivate(first.getFlags())) {
				// the order getAllSupertypes does make assumptions of the order of inner elements -> search recursively
				IMethod res= findMethodDeclarationInHierarchy2(hierarchy, first.getDeclaringType(), method);
				if (res != null) {
					return res;
				}
				return first;
			}
		}
		return null;
	}
	
	/**
	 * Finds a method implementation in a type's class hierarchy. The search is bottom-up, so this
	 * returns the nearest overridden method. Does not find methods in interfaces or abstract methods.
	 * This searches for a method with a name and signature. Parameter types are only
	 * compared by the simple name, no resolving for the fully qualified type name is done.
	 * Constructors are only compared by parameters, not the name.
	 * @param type Type to search the superclasses
	 * @param name The name of the method to find
	 * @param paramTypes The type signatures of the parameters e.g. <code>{"QString;","I"}</code>
	 * @param isConstructor If the method is a constructor
	 * @return The first method found or null, if nothing found
	 */
	public static IMethod findMethodImplementationInHierarchy(ITypeHierarchy hierarchy, IType type, String name, String[] paramTypes, boolean isConstructor) throws JavaModelException {
		IType[] superTypes= hierarchy.getAllSuperclasses(type);
		for (int i= 0; i < superTypes.length; i++) {
			IMethod found= findMethod(name, paramTypes, isConstructor, superTypes[i]);
			if (found != null) {
				if (Flags.isAbstract(found.getFlags())) {
					return null;
				}
				return found;
			}
		}
		return null;
	}
	
	/**
	 * Finds a method implementation in a type's class hierarchy. The search is bottom-up, so this
	 * returns the nearest overridden method. Does not find methods in interfaces or abstract methods.
	 * This searches for a method with a name and signature. Parameter types are only
	 * compared by the simple name, no resolving for the fully qualified type name is done.
	 * Constructors are only compared by parameters, not the name.
	 * @param type Type to search the superclasses
	 * @param method The method to find
	 * @return The first method found or null, if nothing found
	 */
	public static IMethod findMethodImplementationInHierarchy2(ITypeHierarchy hierarchy, IType type, IMethod method) throws JavaModelException {
		IType[] superTypes= hierarchy.getAllSuperclasses(type);
		for (int i= 0; i < superTypes.length; i++) {
			IMethod found= findMethod2(method, superTypes[i].getMethods());
			if (found != null) {
				if (Flags.isAbstract(found.getFlags())) {
					return null;
				}
				return found;
			}
		}
		return null;
	}
	
	private static IMethod findMethodInHierarchy(ITypeHierarchy hierarchy, IType type, String name, String[] paramTypes, boolean isConstructor) throws JavaModelException {
		IMethod method= findMethod(name, paramTypes, isConstructor, type);
		if (method != null) {
			return method;
		}
		IType superClass= hierarchy.getSuperclass(type);
		if (superClass != null) {
			IMethod res=  findMethodInHierarchy(hierarchy, superClass, name, paramTypes, isConstructor);
			if (res != null) {
				return res;
			}
		}
		if (!isConstructor) {
			IType[] superInterfaces= hierarchy.getSuperInterfaces(type);
			for (int i= 0; i < superInterfaces.length; i++) {
				IMethod res= findMethodInHierarchy(hierarchy, superInterfaces[i], name, paramTypes, false);
				if (res != null) {
					return res;
				}
			}
		}
		return method;		
	}
	
	private static IMethod findMethodInHierarchy2(ITypeHierarchy hierarchy, IType type, IMethod curr) throws JavaModelException {
		IMethod method= findMethod2(curr, type.getMethods());
		if (method != null) {
			return method;
		}
		IType superClass= hierarchy.getSuperclass(type);
		if (superClass != null) {
			IMethod res=  findMethodInHierarchy2(hierarchy, superClass, curr);
			if (res != null) {
				return res;
			}
		}
		if (!curr.isConstructor()) {
			IType[] superInterfaces= hierarchy.getSuperInterfaces(type);
			for (int i= 0; i < superInterfaces.length; i++) {
				IMethod res= findMethodInHierarchy2(hierarchy, superInterfaces[i], curr);
				if (res != null) {
					return res;
				}
			}
		}
		return method;		
	}
	
		
	/**
	 * Finds the method that is defines/declares the given method. The search is bottom-up, so this
	 * returns the nearest defining/declaring method.
	 * @param testVisibility If true the result is tested on visibility. Null is returned if the method is not visible.
	 * @throws JavaModelException
	 */
	public static IMethod findMethodDefininition(ITypeHierarchy typeHierarchy, IType type, String methodName, String[] paramTypes, boolean isConstructor, boolean testVisibility) throws JavaModelException {		
		IType superClass= typeHierarchy.getSuperclass(type);
		if (superClass != null) {
			IMethod res= findMethodInHierarchy(typeHierarchy, superClass, methodName, paramTypes, isConstructor);
			if (res != null && !Flags.isPrivate(res.getFlags())) {
				if (!testVisibility || isVisibleInHierarchy(res, type.getPackageFragment())) {
					return res;
				}
			}
		}
		if (!isConstructor) {
			IType[] interfaces= typeHierarchy.getSuperInterfaces(type);
			for (int i= 0; i < interfaces.length; i++) {
				IMethod res= findMethodInHierarchy(typeHierarchy, interfaces[i], methodName, paramTypes, false);
				if (res != null) {
					return res; // methods from interfaces are always public and therefore visible
				}
			}
		}
		return null;
	}
	
	/**
	 * Finds the method that is defines/declares the given method. The search is bottom-up, so this
	 * returns the nearest defining/declaring method.
	 * @param testVisibility If true the result is tested on visibility. Null is returned if the method is not visible.
	 * @throws JavaModelException
	 */
	public static IMethod findMethodDefininition2(ITypeHierarchy typeHierarchy, IType type, IMethod method, boolean testVisibility) throws JavaModelException {		
		IType superClass= typeHierarchy.getSuperclass(type);
		if (superClass != null) {
			IMethod res= findMethodInHierarchy2(typeHierarchy, superClass, method);
			if (res != null && !Flags.isPrivate(res.getFlags())) {
				if (!testVisibility || isVisibleInHierarchy(res, type.getPackageFragment())) {
					return res;
				}
			}
		}
		if (!method.isConstructor()) {
			IType[] interfaces= typeHierarchy.getSuperInterfaces(type);
			for (int i= 0; i < interfaces.length; i++) {
				IMethod res= findMethodInHierarchy2(typeHierarchy, interfaces[i], method);
				if (res != null) {
					return res; // methods from interfaces are always public and therefore visible
				}
			}
		}
		return null;
	}
	
	/**
	 * Tests if a method equals to the given signature.
	 * Parameter types are only compared by the simple name, no resolving for
	 * the fully qualified type name is done. Constructors are only compared by
	 * parameters, not the name.
	 * @param name Name of the method
	 * @param paramTypes The type signatures of the parameters e.g. <code>{"QString;","I"}</code>
	 * @param isConstructor Specifies if the method is a constructor
	 * @return Returns <code>true</code> if the method has the given name and parameter types and constructor state.
	 */
	public static boolean isSameMethodSignature(String name, String[] paramTypes, boolean isConstructor, IMethod curr) throws JavaModelException {
		if (isConstructor || name.equals(curr.getElementName())) {
			if (isConstructor == curr.isConstructor()) {
				String[] currParamTypes= curr.getParameterTypes();
				if (paramTypes.length == currParamTypes.length) {
					for (int i= 0; i < paramTypes.length; i++) {
						String t1= Signature.getSimpleName(Signature.toString(paramTypes[i]));
						String t2= Signature.getSimpleName(Signature.toString(currParamTypes[i]));
						if (!t1.equals(t2)) {
							return false;
						}
					}
					return true;
				}
			}
		}
		return false;
	}
	
	
	/**
	 * Tests if two method have the same signature.
	 * Parameter types are only compared by the simple name, no resolving for
	 * the fully qualified type name is done. Erasures are compared. Type variables always match.
	 * Constructors are only compared by parameters, not the name.
	 * @param method
	 * @param other
	 * @return Returns <code>true</code> if the methods override
	 * @throws JavaModelException
	 */
	public static boolean isSameMethodSignature2(IMethod method, IMethod other) throws JavaModelException {
		boolean isConstructor= method.isConstructor();
		if (isConstructor != other.isConstructor()) {
			return false;
		}
		if (!isConstructor && !method.getElementName().equals(other.getElementName())) {
			return false;
		}
		int nParameters= method.getNumberOfParameters();
		if (nParameters != other.getNumberOfParameters()) {
			return false;
		}
		if (nParameters > 0) {
			String[] currParamTypes= getParameterTypes(method);
			String[] otherParamTypes= getParameterTypes(other);
			for (int i= 0; i < currParamTypes.length; i++) {
				String p1= currParamTypes[i];
				String p2= otherParamTypes[i];
				if (p1 == null) {
					return p2 == null || PrimitiveType.toCode(p2) == null;
				} else if (p2 == null) {
					return p1 == null || PrimitiveType.toCode(p1) == null;
				} else {
					return p1.equals(p2);
				}
			}
		}
		return true;
	}
		
	private static String[] getParameterTypes(IMethod curr) throws JavaModelException {
		String[] paramTypeSigs= curr.getParameterTypes();
		String[] paramTypes= new String[paramTypeSigs.length];
		if (is50OrHigher(curr.getJavaProject())) {
			// for methods in 5.0 project test erasure
			Set variables= getVariables(curr);
			for (int i= 0; i < paramTypeSigs.length; i++) {
				paramTypes[i]= getErasure(paramTypeSigs[i], variables);
			}
		} else {
			for (int i= 0; i < paramTypeSigs.length; i++) {
				paramTypes[i]= Signature.getSimpleName(Signature.toString(paramTypeSigs[i]));
			}
		}
		return paramTypes;
	}
	
	private static Set getVariables(IMethod curr) throws JavaModelException {
		Set res= new HashSet();
		ITypeParameter[] typeParameters= curr.getTypeParameters();
		for (int i= 0; i < typeParameters.length; i++) {
			res.add(typeParameters[i].getElementName());
		}
		typeParameters= curr.getDeclaringType().getTypeParameters();
		for (int i= 0; i < typeParameters.length; i++) {
			res.add(typeParameters[i].getElementName());
		}
		
		return res;
	}

	private static String getErasure(String str, Set variables) {
		switch (Signature.getTypeSignatureKind(str)) {
			case Signature.TYPE_VARIABLE_SIGNATURE:
				return null;
			case Signature.CLASS_TYPE_SIGNATURE:
				String s= Signature.toString(str);
				if (variables.contains(s)) {
					return null;
				}
				return Signature.getSimpleName(Signature.getTypeErasure(s));
			case Signature.ARRAY_TYPE_SIGNATURE:
				int dim= Signature.getArrayCount(str);
				String erasure= getErasure(Signature.getElementType(str), variables);
				if (erasure == null)
					return null;
				
				StringBuffer buf= new StringBuffer(erasure);
				for (int i= 0; i < dim; i++) {
					buf.append('[').append(']');
				}
				return buf.toString();
		}
		return Signature.toString(str);
	}



	/**
	 * Tests if two <code>IPackageFragment</code>s represent the same logical java package.
	 * @return <code>true</code> if the package fragments' names are equal.
	 */
	public static boolean isSamePackage(IPackageFragment pack1, IPackageFragment pack2) {
		return pack1.getElementName().equals(pack2.getElementName());
	}
	
	/**
	 * Checks whether the given type has a valid main method or not.
	 */
	public static boolean hasMainMethod(IType type) throws JavaModelException {
		IMethod[] methods= type.getMethods();
		for (int i= 0; i < methods.length; i++) {
			if (methods[i].isMainMethod()) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Checks if the field is boolean.
	 */
	public static boolean isBoolean(IField field) throws JavaModelException{
		return field.getTypeSignature().equals(Signature.SIG_BOOLEAN);
	}
	
	/**
	 * @return <code>true</code> iff the type is an interface or an annotation
	 */
	public static boolean isInterfaceOrAnnotation(IType type) throws JavaModelException {
		return type.isInterface();
	}
	
	/**
	 * Returns true if the element is on the build path of the given project
	 * @deprecated Use jproject.isOnClasspath(element);
	 */	
	public static boolean isOnBuildPath(IJavaProject jproject, IJavaElement element) {
		return jproject.isOnClasspath(element);
	}
	
	/**
	 * Tests if the given element is on the class path of its containing project. Handles the case
	 * that the containing project isn't a Java project.
	 */
	public static boolean isOnClasspath(IJavaElement element) {
		IJavaProject project= element.getJavaProject();
		if (!project.exists())
			return false;
		return project.isOnClasspath(element);
	}

	/**
	 * Resolves a type name in the context of the declaring type.
	 * 
	 * @param refTypeSig the type name in signature notation (for example 'QVector') this can also be an array type, but dimensions will be ignored.
	 * @param declaringType the context for resolving (type where the reference was made in)
	 * @return returns the fully qualified type name or build-in-type name. if a unresolved type couldn't be resolved null is returned
	 */
	public static String getResolvedTypeName(String refTypeSig, IType declaringType) throws JavaModelException {
		int arrayCount= Signature.getArrayCount(refTypeSig);
		char type= refTypeSig.charAt(arrayCount);
		if (type == Signature.C_UNRESOLVED) {
			String name= ""; //$NON-NLS-1$
			int bracket= refTypeSig.indexOf(Signature.C_GENERIC_START, arrayCount + 1);
			if (bracket > 0)
				name= refTypeSig.substring(arrayCount + 1, bracket);
			else {
				int semi= refTypeSig.indexOf(Signature.C_SEMICOLON, arrayCount + 1);
				if (semi == -1) {
					throw new IllegalArgumentException();
				}
				name= refTypeSig.substring(arrayCount + 1, semi);
			}
			String[][] resolvedNames= declaringType.resolveType(name);
			if (resolvedNames != null && resolvedNames.length > 0) {
				return JavaModelUtil.concatenateName(resolvedNames[0][0], resolvedNames[0][1]);
			}
			return null;
		} else {
			return Signature.toString(refTypeSig.substring(arrayCount));
		}
	}
	
	/**
	 * Returns if a CU can be edited.
	 */
	public static boolean isEditable(ICompilationUnit cu)  {
		IResource resource= toOriginal(cu).getResource();
		return (resource.exists() && !resource.getResourceAttributes().isReadOnly());
	}

	/**
	 * Returns the original if the given member. If the member is already
	 * an original the input is returned. The returned member might not exist
	 */
	public static IMember toOriginal(IMember member) {
		if (PRIMARY_ONLY) {
			testCompilationUnitOwner("toOriginal", member.getCompilationUnit()); //$NON-NLS-1$
		}
		if (member instanceof IMethod)
			return toOriginalMethod((IMethod)member);
		
		return (IMember) member.getPrimaryElement();
		/*ICompilationUnit cu= member.getCompilationUnit();
		if (cu != null && cu.isWorkingCopy())
			return (IMember)cu.getOriginal(member);
		return member;*/
	}
	
	/*
	 * XXX workaround for bug 18568
	 * http://bugs.eclipse.org/bugs/show_bug.cgi?id=18568
	 * to be removed once the bug is fixed
	 */
	private static IMethod toOriginalMethod(IMethod method) {
		ICompilationUnit cu= method.getCompilationUnit();
		if (cu == null || isPrimary(cu)) {
			return method;
		}
		try{
			//use the workaround only if needed	
			if (! method.getElementName().equals(method.getDeclaringType().getElementName()))
				return (IMethod) method.getPrimaryElement();
			
			IType originalType = (IType) toOriginal(method.getDeclaringType());
			IMethod[] methods = originalType.findMethods(method);
			boolean isConstructor = method.isConstructor();
			for (int i=0; i < methods.length; i++) {
			  if (methods[i].isConstructor() == isConstructor) 
				return methods[i];
			}
			return null;
		} catch (JavaModelException e){
			return null;
		}	
	}
	
	private static boolean PRIMARY_ONLY= false;

	/**
	 * Returns the original cu if the given cu is a working copy. If the cu is already
	 * an original the input cu is returned. The returned cu might not exist
	 */
	public static ICompilationUnit toOriginal(ICompilationUnit cu) {
		if (PRIMARY_ONLY) {
			testCompilationUnitOwner("toOriginal", cu); //$NON-NLS-1$
		}
		// To stay compatible with old version returned null
		// if cu is null
		if (cu == null)
			return cu;
		return cu.getPrimary();
	}
	
	/**
	 * Returns the original element if the given element is a working copy. If the cu is already
	 * an original the input element is returned. The returned element might not exist
	 */
	public static IJavaElement toOriginal(IJavaElement element) {
		return element.getPrimaryElement();
	}	
		
	private static void testCompilationUnitOwner(String methodName, ICompilationUnit cu) {
		if (cu == null) {
			return;
		}
		if (!isPrimary(cu))  {
			JavaPlugin.logErrorMessage(methodName + ": operating with non-primary cu"); //$NON-NLS-1$
		}
	}
	
	
	/**
	 * @deprecated Inline this method.
	 */
	public static IMember toWorkingCopy(IMember member) {
		return member;
	}

	/**
	 * @deprecated Inline this method.
	 */
	public static IPackageDeclaration toWorkingCopy(IPackageDeclaration declaration) {
		return declaration;
	}
	
	/**
	 * @deprecated Inline this method.
	 */
	public static IJavaElement toWorkingCopy(IJavaElement elem) {
		return elem;
	}	

	/**
	 * @deprecated Inline this method.
	 */
	public static IImportContainer toWorkingCopy(IImportContainer container) {
		return container;

	}

	/**
	 * @deprecated Inline this method.
	 */
	public static IImportDeclaration toWorkingCopy(IImportDeclaration importDeclaration) {
		return importDeclaration;
	}


	/**
	 * @deprecated Inline this method.
	 */
	public static ICompilationUnit toWorkingCopy(ICompilationUnit cu) {
		return cu;
	}
	
	/**
	 * Returns true if a cu is a primary cu (original or shared working copy)
	 */
	public static boolean isPrimary(ICompilationUnit cu) {
		return cu.getOwner() == null;
	}
	
	
	/*
	 * http://bugs.eclipse.org/bugs/show_bug.cgi?id=19253
	 * 
	 * Reconciling happens in a separate thread. This can cause a situation where the
	 * Java element gets disposed after an exists test has been done. So we should not
	 * log not present exceptions when they happen in working copies.
	 */
	public static boolean isExceptionToBeLogged(CoreException exception) {
		if (!(exception instanceof JavaModelException))
			return true;
		JavaModelException je= (JavaModelException)exception;
		if (!je.isDoesNotExist())
			return true;
		IJavaElement[] elements= je.getJavaModelStatus().getElements();
		for (int i= 0; i < elements.length; i++) {
			IJavaElement element= elements[i];
			// if the element is already a compilation unit don't log
			// does not exist exceptions. See bug 
			// https://bugs.eclipse.org/bugs/show_bug.cgi?id=75894
			// for more details
			if (element.getElementType() == IJavaElement.COMPILATION_UNIT)
				continue;
			ICompilationUnit unit= (ICompilationUnit)element.getAncestor(IJavaElement.COMPILATION_UNIT);
			if (unit == null)
				return true;
			if (!unit.isWorkingCopy())
				return true;
		}
		return false;		
	}

	public static IType[] getAllSuperTypes(IType type, IProgressMonitor pm) throws JavaModelException {
		//workaround for 23656
		Set types= new HashSet(Arrays.asList(type.newSupertypeHierarchy(pm).getAllSupertypes(type)));
		IType objekt= type.getJavaProject().findType("java.lang.Object");//$NON-NLS-1$
		if (objekt != null)
			types.add(objekt);
		return (IType[]) types.toArray(new IType[types.size()]);
	}
	
	public static boolean isExcludedPath(IPath resourcePath, IPath[] exclusionPatterns) {
		char[] path = resourcePath.toString().toCharArray();
		for (int i = 0, length = exclusionPatterns.length; i < length; i++) {
			char[] pattern= exclusionPatterns[i].toString().toCharArray();
			if (CharOperation.pathMatch(pattern, path, true, '/')) {
				return true;
			}
		}
		return false;	
	}


	/*
	 * Returns whether the given resource path matches one of the exclusion
	 * patterns.
	 * 
	 * @see IClasspathEntry#getExclusionPatterns
	 */
	public final static boolean isExcluded(IPath resourcePath, char[][] exclusionPatterns) {
		if (exclusionPatterns == null) return false;
		char[] path = resourcePath.toString().toCharArray();
		for (int i = 0, length = exclusionPatterns.length; i < length; i++)
			if (CharOperation.pathMatch(exclusionPatterns[i], path, true, '/'))
				return true;
		return false;
	}	
		

	/**
	 * Force a reconcile of a compilation unit.
	 * @param unit
	 */
	public static void reconcile(ICompilationUnit unit) throws JavaModelException {
		synchronized(unit)  {
			unit.reconcile(
				ICompilationUnit.NO_AST, 
				false /* don't force problem detection */, 
				null /* use primary owner */, 
				null /* no progress monitor */);
		}
	}
	
	/**
	 * Helper method that tests if an classpath entry can be found in a
	 * container. <code>null</code> is returned if the entry can not be found
	 * or if the container does not allows the configuration of source
	 * attachments
	 * @param jproject The container's parent project
	 * @param containerPath The path of the container
	 * @param libPath The path of the library to be found
	 * @return IClasspathEntry A classpath entry from the container of
	 * <code>null</code> if the container can not be modified.
	 */
	public static IClasspathEntry getClasspathEntryToEdit(IJavaProject jproject, IPath containerPath, IPath libPath) throws JavaModelException {
		IClasspathContainer container= JavaCore.getClasspathContainer(containerPath, jproject);
		ClasspathContainerInitializer initializer= JavaCore.getClasspathContainerInitializer(containerPath.segment(0));
		if (container != null && initializer != null && initializer.canUpdateClasspathContainer(containerPath, jproject)) {
			IClasspathEntry[] entries= container.getClasspathEntries();
			for (int i= 0; i < entries.length; i++) {
				IClasspathEntry curr= entries[i];
				IClasspathEntry resolved= JavaCore.getResolvedClasspathEntry(curr);
				if (resolved != null && libPath.equals(resolved.getPath())) {
					return curr; // return the real entry
				}
			}
		}
		return null; // attachment not possible
	}
	
	/**
	 * Get all compilation units of a selection.
	 * @param javaElements the selected java elements
	 * @return all compilation units containing and contained in elements from javaElements
	 * @throws JavaModelException
	 */
	public static ICompilationUnit[] getAllCompilationUnits(IJavaElement[] javaElements) throws JavaModelException {
		HashSet result= new HashSet();
		for (int i= 0; i < javaElements.length; i++) {
			addAllCus(result, javaElements[i]);
		}
		return (ICompilationUnit[]) result.toArray(new ICompilationUnit[result.size()]);
	}

	private static void addAllCus(HashSet/*<ICompilationUnit>*/ collector, IJavaElement javaElement) throws JavaModelException {
		switch (javaElement.getElementType()) {
			case IJavaElement.JAVA_PROJECT:
				IJavaProject javaProject= (IJavaProject) javaElement;
				IPackageFragmentRoot[] packageFragmentRoots= javaProject.getPackageFragmentRoots();
				for (int i= 0; i < packageFragmentRoots.length; i++)
					addAllCus(collector, packageFragmentRoots[i]);
				return;
		
			case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				IPackageFragmentRoot packageFragmentRoot= (IPackageFragmentRoot) javaElement;
				if (packageFragmentRoot.getKind() != IPackageFragmentRoot.K_SOURCE)
					return;
				IJavaElement[] packageFragments= packageFragmentRoot.getChildren();
				for (int j= 0; j < packageFragments.length; j++)
					addAllCus(collector, packageFragments[j]);
				return;
		
			case IJavaElement.PACKAGE_FRAGMENT:
				IPackageFragment packageFragment= (IPackageFragment) javaElement;
				collector.addAll(Arrays.asList(packageFragment.getCompilationUnits()));
				return;
			
			case IJavaElement.COMPILATION_UNIT:
				collector.add(javaElement);
				return;
				
			default:
				IJavaElement cu= javaElement.getAncestor(IJavaElement.COMPILATION_UNIT);
				if (cu != null)
					collector.add(cu);
		}
	}

	
	/**
	 * Sets all compliance settings in the given map to 5.0
	 */
	public static void set50CompilanceOptions(Map map) {
		setCompilanceOptions(map, JavaCore.VERSION_1_5);
	}
	
	public static void setCompilanceOptions(Map map, String compliance) {
		if (JavaCore.VERSION_1_5.equals(compliance)) {
			map.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_5);
			map.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_5);
			map.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_5);
			map.put(JavaCore.COMPILER_PB_ASSERT_IDENTIFIER, JavaCore.ERROR);
			map.put(JavaCore.COMPILER_PB_ENUM_IDENTIFIER, JavaCore.ERROR);
		} else if (JavaCore.VERSION_1_4.equals(compliance)) {
			map.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_4);
			map.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_3);
			map.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_2);
			map.put(JavaCore.COMPILER_PB_ASSERT_IDENTIFIER, JavaCore.WARNING);
			map.put(JavaCore.COMPILER_PB_ENUM_IDENTIFIER, JavaCore.WARNING);
		} else if (JavaCore.VERSION_1_3.equals(compliance)) {
			map.put(JavaCore.COMPILER_COMPLIANCE, JavaCore.VERSION_1_3);
			map.put(JavaCore.COMPILER_SOURCE, JavaCore.VERSION_1_3);
			map.put(JavaCore.COMPILER_CODEGEN_TARGET_PLATFORM, JavaCore.VERSION_1_1);
			map.put(JavaCore.COMPILER_PB_ASSERT_IDENTIFIER, JavaCore.IGNORE);
			map.put(JavaCore.COMPILER_PB_ENUM_IDENTIFIER, JavaCore.IGNORE);
		} else {
			throw new IllegalArgumentException("Unsupported compliance: " + compliance); //$NON-NLS-1$
		}
	}
	
	
	public static boolean is50OrHigher(IJavaProject project) {
		return JavaCore.VERSION_1_5.equals(project.getOption(JavaCore.COMPILER_COMPLIANCE, true));
	}
	
	public static boolean is50OrHigherJRE(IJavaProject project) throws CoreException {
		IVMInstall vmInstall= JavaRuntime.getVMInstall(project);
		if (!(vmInstall instanceof IVMInstall2))
			return true; // assume 5.0.
		String javaVersion = ((IVMInstall2)vmInstall).getJavaVersion();
		if (javaVersion == null)
			return true; // assume 5.0
		return javaVersion.startsWith(JavaCore.VERSION_1_5);
	}
}
