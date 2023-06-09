/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Dmitry Stalnov (dstalnov@fusionone.com) - contributed fix for
 *       bug "inline method - doesn't handle implicit cast" (see
 *       https://bugs.eclipse.org/bugs/show_bug.cgi?id=24941).
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.dom;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SuperFieldAccess;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.internal.ui.JavaPlugin;

public class Bindings {
	
	public static final String ARRAY_LENGTH_FIELD_BINDING_STRING= "(array type):length";//$NON-NLS-1$
	private Bindings() {
		// No instance
	}
	
	private static final boolean CHECK_CORE_BINDING_IS_EQUAL_TO;
	static {
		String value= Platform.getDebugOption("org.eclipse.jdt.ui/debug/checkCoreBindingIsEqualTo"); //$NON-NLS-1$
		CHECK_CORE_BINDING_IS_EQUAL_TO= value != null && value.equalsIgnoreCase("true"); //$NON-NLS-1$
	}
	private static final boolean CHECK_CORE_BINDING_GET_JAVA_ELEMENT;
	static {
		String value= Platform.getDebugOption("org.eclipse.jdt.ui/debug/checkCoreBindingGetJavaElement"); //$NON-NLS-1$
		CHECK_CORE_BINDING_GET_JAVA_ELEMENT= value != null && value.equalsIgnoreCase("true"); //$NON-NLS-1$
	}
	private static final boolean USE_UI_BINDING_GET_JAVA_ELEMENT;
	static {
		String value= Platform.getDebugOption("org.eclipse.jdt.ui/debug/useUIBindingGetJavaElement"); //$NON-NLS-1$
		USE_UI_BINDING_GET_JAVA_ELEMENT= value != null && value.equalsIgnoreCase("true"); //$NON-NLS-1$
	}
	
	/**
	 * Checks if the two bindings are equals. First an identity check is
	 * made an then the key of the bindings are compared. 
	 * @param b1 first binding treated as <code>this</code>. So it must
	 *  not be <code>null</code>
	 * @param b2 the second binding.
	 * @return boolean
	 */
	public static boolean equals(IBinding b1, IBinding b2) {
		boolean isEqualTo= b1.isEqualTo(b2);
		if (CHECK_CORE_BINDING_IS_EQUAL_TO) {
			boolean originalEquals= originalEquals(b1, b2);
			if (originalEquals != isEqualTo) {
				String message= "Unexpected difference between Bindings.equals(..) and IBinding#isEqualTo(..)"; //$NON-NLS-1$
				String detail= "\nb1 == " + b1.getKey() + ",\nb2 == " + (b2 == null ? "null binding" : b2.getKey()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				try {
					detail+= "\nb1.getJavaElement() == " + b1.getJavaElement() + ",\nb2.getJavaElement() == " + (b2 == null ? "null binding" : b2.getJavaElement().toString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
				} catch (Exception e) {
					detail += "\nException in getJavaElement():\n" + e; //$NON-NLS-1$
				}
				JavaPlugin.logRepeatedMessage(message, detail);
			}
		}
		return isEqualTo;
	}
	
	private static boolean originalEquals(IBinding b1, IBinding b2) {
		Assert.isNotNull(b1);
		if (b1 == b2)
			return true;
		if (b2 == null)
			return false;		
		String k1= b1.getKey();
		String k2= b2.getKey();
		if (k1 == null || k2 == null)
			return false;
		return k1.equals(k2);
	}
	
	/**
	 * Checks if the two arrays of bindings have the same length and
	 * their elements are equal. Uses
	 * <code>Bindings.equals(IBinding, IBinding)</code> to compare.
	 * @param b1 the first array of bindings. Must not be <code>null</code>.
	 * @param b2 the second array of bindings.
	 * @return boolean
	 */
	public static boolean equals(IBinding[] b1, IBinding[] b2) {
		Assert.isNotNull(b1);
		if (b1 == b2)
			return true;
		if (b2 == null)
			return false;		
		if (b1.length != b2.length)
			return false;
		for (int i= 0; i < b1.length; i++) {
			if (! Bindings.equals(b1[i], b2[i]))
				return false;
		}
		return true;
	}
	
	public static int hashCode(IBinding binding){
		Assert.isNotNull(binding);
		String key= binding.getKey();
		if (key == null)
			return binding.hashCode();
		return key.hashCode();
	}
	
	/*
	 * Note: this method is for debugging and testing purposes only.
	 * There are tests whose precomputed test results rely on the returned String's format.
	 * @see org.eclipse.jdt.internal.ui.viewsupport.BindingLabels
	 */
	public static String asString(IBinding binding) {
		if (binding instanceof IMethodBinding)
			return asString((IMethodBinding)binding);
		else if (binding instanceof ITypeBinding)
			return asString((ITypeBinding)binding);
		else if (binding instanceof IVariableBinding)
			return asString((IVariableBinding)binding);
		return binding.toString();
	}

	private static String asString(IVariableBinding variableBinding) {
		if (! variableBinding.isField())
			return variableBinding.toString();
		if (variableBinding.getDeclaringClass() == null) {
			Assert.isTrue(variableBinding.getName().equals("length"));//$NON-NLS-1$
			return ARRAY_LENGTH_FIELD_BINDING_STRING;
		}
		StringBuffer result= new StringBuffer();
		result.append(variableBinding.getDeclaringClass().getName());
		result.append(':');
		result.append(variableBinding.getName());				
		return result.toString();		
	}

	private static String asString(ITypeBinding type) {
		return type.getQualifiedName();
	}
		
	private static String asString(IMethodBinding method) {
		StringBuffer result= new StringBuffer();
		result.append(method.getDeclaringClass().getName());
		result.append(':');
		result.append(method.getName());
		result.append('(');
		ITypeBinding[] parameters= method.getParameterTypes();
		int lastComma= parameters.length - 1;
		for (int i= 0; i < parameters.length; i++) {
			ITypeBinding parameter= parameters[i];
			result.append(parameter.getName());
			if (i < lastComma)
				result.append(", "); //$NON-NLS-1$
		}
		result.append(')');
		return result.toString();
	}
	
	public static String getTypeQualifiedName(ITypeBinding type) {
		List result= new ArrayList(5);
		createName(type, false, result);
		
		StringBuffer buffer= new StringBuffer();
		for (int i= 0; i < result.size(); i++) {
			if (i > 0) {
				buffer.append('.');
			}
			buffer.append(((String) result.get(i)));
		}
		return buffer.toString();
	}

	/**
	 * Returns the fully qualified name of the specified type binding.
	 * <p>
	 * If the binding resolves to a generic type, the fully qualified name of the raw type is returned.
	 * 
	 * @param type the type binding to get its fully qualified name
	 * @return the fully qualified name
	 */
	public static String getFullyQualifiedName(ITypeBinding type) {

		// TW: replace by call to type.getJavaElement().getFullyQualifiedName (see 78087)

		String name= type.getQualifiedName();
		final int index= name.indexOf('<');
		if (index > 0)
			name= name.substring(0, index);
		return name;
	}	

	public static String getImportName(IBinding binding) {
		ITypeBinding declaring= null;
		switch (binding.getKind()) {
			case IBinding.TYPE:
				return getRawQualifiedName((ITypeBinding) binding);
			case IBinding.PACKAGE:
				return binding.getName() + ".*"; //$NON-NLS-1$
			case IBinding.METHOD:
				declaring= ((IMethodBinding) binding).getDeclaringClass();
				break;
			case IBinding.VARIABLE:
				declaring= ((IVariableBinding) binding).getDeclaringClass();
				break;
			default:
				return binding.getName();
		}
		return JavaModelUtil.concatenateName(getRawQualifiedName(declaring), binding.getName());
	}	
	
	
	private static void createName(ITypeBinding type, boolean includePackage, List list) {
		ITypeBinding baseType= type;
		if (type.isArray()) {
			baseType= type.getElementType();
		}
		if (!baseType.isPrimitive() && !baseType.isNullType()) {
			ITypeBinding declaringType= baseType.getDeclaringClass();
			if (declaringType != null) {
				createName(declaringType, includePackage, list);
			} else if (includePackage && !baseType.getPackage().isUnnamed()) {
				String[] components= baseType.getPackage().getNameComponents();
				for (int i= 0; i < components.length; i++) {
					list.add(components[i]);
				}
			}
		}
		if (!baseType.isAnonymous()) {
			list.add(type.getName());
		} else {
			list.add("$local$"); //$NON-NLS-1$
		}		
	}	
	
	
	public static String[] getNameComponents(ITypeBinding type) {
		List result= new ArrayList(5);
		createName(type, false, result);
		return (String[]) result.toArray(new String[result.size()]);
	}
	
	public static String[] getAllNameComponents(ITypeBinding type) {
		List result= new ArrayList(5);
		createName(type, true, result);
		return (String[]) result.toArray(new String[result.size()]);
	}
	
	public static ITypeBinding getTopLevelType(ITypeBinding type) {
		ITypeBinding parent= type.getDeclaringClass();
		while (parent != null) {
			type= parent;
			parent= type.getDeclaringClass();
		}
		return type;
	}
	
	/**
	 * Checks whether the passed type binding is a runtime exception.
	 * 
	 * @param thrownException the type binding
	 * 
	 * @return <code>true</code> if the passed type binding is a runtime exception;
	 * 	otherwise <code>false</code> is returned
	 */
	public static boolean isRuntimeException(ITypeBinding thrownException) {
		if (thrownException == null || thrownException.isPrimitive() || thrownException.isArray())
			return false;
		return findTypeInHierarchy(thrownException, "java.lang.RuntimeException") != null; //$NON-NLS-1$
	}
	
	/**
	 * Finds the field specified by <code>fieldName<code> in
	 * the given <code>type</code>. Returns <code>null</code> if no such field exits.
	 * @param type the type to search the field in
	 * @param fieldName the field name
	 * @return the binding representing the field or <code>null</code>
	 */
	public static IVariableBinding findFieldInType(ITypeBinding type, String fieldName) {
		if (type.isPrimitive())
			return null;
		IVariableBinding[] fields= type.getDeclaredFields();
		for (int i= 0; i < fields.length; i++) {
			IVariableBinding field= fields[i];
			if (field.getName().equals(fieldName))
				return field;
		}
		return null;
	}
		
	/**
	 * Finds the method specified by <code>methodName<code> and </code>parameters</code> in
	 * the given <code>type</code>. Returns <code>null</code> if no such method exits.
	 * @param type The type to search the method in
	 * @param methodName The name of the method to find
	 * @param parameters The parameter types of the method to find. If <code>null</code> is passed, only 
	 *  the name is matched and parameters are ignored.
	 * @return the method binding representing the method
	 */
	public static IMethodBinding findMethodInType(ITypeBinding type, String methodName, ITypeBinding[] parameters) {
		if (type.isPrimitive())
			return null;
		IMethodBinding[] methods= type.getDeclaredMethods();
		for (int i= 0; i < methods.length; i++) {
			if (parameters == null) {
				if (methodName.equals(methods[i].getName()))
					return methods[i];
			} else {
				if (isEqualMethod(methods[i], methodName, parameters))
					return methods[i];
			}
		}
		return null;
	}
	
	/**
	 * Finds the method specified by <code>methodName<code> and </code>parameters</code> in
	 * the given <code>type</code>. Returns <code>null</code> if no such method exits.
	 * @param type The type to search the method in
	 * @param methodName The name of the method to find
	 * @param parameters The parameter types of the method to find. If <code>null</code> is passed, only the name is matched and parameters are ignored.
	 * @return the method binding representing the method
	 */
	public static IMethodBinding findMethodInType(ITypeBinding type, String methodName, String[] parameters) {
		if (type.isPrimitive())
			return null;
		IMethodBinding[] methods= type.getDeclaredMethods();
		for (int i= 0; i < methods.length; i++) {
			if (parameters == null) {
				if (methodName.equals(methods[i].getName()))
					return methods[i];
			} else {
				if (isEqualMethod(methods[i], methodName, parameters))
					return methods[i];
			}
		}
		return null;
	}
	
	/**
	 * Finds the method in the given <code>type</code> that is overrideen by the specified <code>method<code> . Returns <code>null</code> if no such method exits.
	 * @param type The type to search the method in
	 * @param method The specified method that would override the result
	 * @return the method binding representing the method oevrriding the specified <code>method<code>
	 */
	public static IMethodBinding findOverriddenMethodInType(ITypeBinding type, IMethodBinding method) {
		return findMethodInType(type, method.getName(), method.getParameterTypes());
//		String methodName= method.getName();
//		IMethodBinding[] methods= type.getDeclaredMethods();
//		for (int i= 0; i < methods.length; i++) {
//			IMethodBinding curr= methods[i];
//			if (curr.getName().equals(methodName) && method.overrides(curr)) { // name check: see bug 98483
//				return curr;
//			}
//		}
//		return null;
	}

	/**
	 * Finds the field specified by <code>fieldName</code> in
	 * the type hierarchy denoted by the given type. Returns <code>null</code> if no such field
	 * exists. If the field is defined in more than one super type only the first match is 
	 * returned. First the super class is examined and than the implemented interfaces.
	 * @param type The type to search the field in
	 * @param fieldName The name of the field to find
	 * @return the variable binding representing the field
	 */
	public static IVariableBinding findFieldInHierarchy(ITypeBinding type, String fieldName) {
		IVariableBinding field= findFieldInType(type, fieldName);
		if (field != null)
			return field;
		ITypeBinding superClass= type.getSuperclass();
		if (superClass != null) {
			field= findFieldInType(type, fieldName);
			if (field != null)
				return field;			
		}
		ITypeBinding[] interfaces= type.getInterfaces();
		for (int i= 0; i < interfaces.length; i++) {
			field= findFieldInType(type, fieldName);
			if (field != null) // no private fields in interfaces
				return field;
		}
		return null;
	}


	/**
	 * Finds the method specified by <code>methodName</code> and </code>parameters</code> in
	 * the type hierarchy denoted by the given type. Returns <code>null</code> if no such method
	 * exists. If the method is defined in more than one super type only the first match is 
	 * returned. First the super class is examined and than the implemented interfaces.
	 * @param type The type to search the method in
	 * @param methodName The name of the method to find
	 * @param parameters The parameter types of the method to find. If <code>null</code> is passed, only the name is matched and parameters are ignored.
	 * @return the method binding representing the method
	 */
	public static IMethodBinding findMethodInHierarchy(ITypeBinding type, String methodName, ITypeBinding parameters[]) {
		IMethodBinding method= findMethodInType(type, methodName, parameters);
		if (method != null)
			return method;
		ITypeBinding superClass= type.getSuperclass();
		if (superClass != null) {
			method= findMethodInHierarchy(superClass, methodName, parameters);
			if (method != null)
				return method;			
		}
		ITypeBinding[] interfaces= type.getInterfaces();
		for (int i= 0; i < interfaces.length; i++) {
			method= findMethodInHierarchy(interfaces[i], methodName, parameters);
			if (method != null)
				return method;
		}
		return null;
	}


	/**
	 * Finds the method specified by <code>methodName</code> and </code>parameters</code> in
	 * the type hierarchy denoted by the given type. Returns <code>null</code> if no such method
	 * exists. If the method is defined in more than one super type only the first match is 
	 * returned. First the super class is examined and than the implemented interfaces.
	 * @param typeObject the type binding for <code>java.lang.Object</code>.
	 * @param type the type to search the method in
	 * @param methodName The name of the method to find
	 * @param parameters The parameter types of the method to find. If <code>null</code> is passed, only the name is matched and parameters are ignored.
	 * @return the method binding representing the method
	 */
	public static IMethodBinding findMethodInHierarchy(ITypeBinding typeObject, ITypeBinding type, String methodName, String parameters[]) {
		IMethodBinding method= findMethodInType(type, methodName, parameters);
		if (method != null)
			return method;
		ITypeBinding superClass= type.getSuperclass();
		if (superClass == null && type.isInterface())
			superClass= typeObject;
		if (superClass != null) {
			method= findMethodInHierarchy(typeObject, superClass, methodName, parameters);
			if (method != null)
				return method;			
		}
		ITypeBinding[] interfaces= type.getInterfaces();
		for (int i= 0; i < interfaces.length; i++) {
			method= findMethodInHierarchy(typeObject, interfaces[i], methodName, parameters);
			if (method != null)
				return method;
		}
		return null;
	}
	
	/**
	 * Finds a method in the hierarchy of <code>type</code> that is overridden by </code>binding</code>.
	 * Returns <code>null</code> if no such method exists. If the method is defined in more than one super type only the first match is 
	 * returned. First the super class is examined and than the implemented interfaces.
	 * @param type The type to search the method in
	 * @param binding The method that overrrides
	 * @return the method binding overridden the method
	 */
	public static IMethodBinding findOverriddenMethodInHierarchy(ITypeBinding type, IMethodBinding binding) {
		IMethodBinding method= findOverriddenMethodInType(type, binding);
		if (method != null)
			return method;
		ITypeBinding superClass= type.getSuperclass();
		if (superClass != null) {
			method= findOverriddenMethodInHierarchy(superClass, binding);
			if (method != null)
				return method;			
		}
		ITypeBinding[] interfaces= type.getInterfaces();
		for (int i= 0; i < interfaces.length; i++) {
			method= findOverriddenMethodInHierarchy(interfaces[i], binding);
			if (method != null)
				return method;
		}
		return null;
	}
	
	
	/**
	 * Finds the method that is defines the given method. The returned method might not be visible.
	 * @param method The method to find
	 * @param testVisibility If true the result is tested on visibility. Null is returned if the method is not visible.
	 * @return the method binding representing the method
	 */
	public static IMethodBinding findMethodDefininition(IMethodBinding method, boolean testVisibility) {
		int modifiers= method.getModifiers();
		if (Modifier.isPrivate(modifiers) || Modifier.isStatic(modifiers) || method.isConstructor()) {
			return null;
		}
		
		ITypeBinding type= method.getDeclaringClass();
		if (type.isInterface()) {
			return null;
		}
		
		if (type.getSuperclass() != null) {
			IMethodBinding res= findOverriddenMethodInHierarchy(type.getSuperclass(), method);
			if (res != null && !Modifier.isPrivate(res.getModifiers())) {
				if (!testVisibility || isVisibleInHierarchy(res, method.getDeclaringClass().getPackage())) {
					return res;
				}
			}
		}
		ITypeBinding[] interfaces= type.getInterfaces();
		for (int i= 0; i < interfaces.length; i++) {
			IMethodBinding res= findOverriddenMethodInHierarchy(interfaces[i], method);
			if (res != null) {
				return res; // methods from interfaces are always public and therefore visible
			}
		}
		return null;
	}
	
	/**
	 * Finds the method that is implemented by the given method.
	 * @param method The method to find
 	 * @param testVisibility If true the result is tested on visibility. Null is returned if the method is not visible.
	 * @return the method binding representing the method
	 */
	public static IMethodBinding findMethodImplementation(IMethodBinding method, boolean testVisibility) {
		ITypeBinding superClass= method.getDeclaringClass().getSuperclass();
		
		String methodName= method.getName();
		ITypeBinding[] parameters= method.getParameterTypes();
		while (superClass != null) {
			IMethodBinding res= findMethodInType(superClass, methodName, parameters);
			if (res != null) {
				if (isVisibleInHierarchy(res, method.getDeclaringClass().getPackage())) {
					return res;
				}
				return null;
			}
			superClass= superClass.getSuperclass();
		}
		return null;
	}
	
	public static boolean isVisibleInHierarchy(IMethodBinding member, IPackageBinding pack) {
		int otherflags= member.getModifiers();
		ITypeBinding declaringType= member.getDeclaringClass();
		if (Modifier.isPublic(otherflags) || Modifier.isProtected(otherflags) || (declaringType != null && declaringType.isInterface())) {
			return true;
		} else if (Modifier.isPrivate(otherflags)) {
			return false;
		}		
		return pack == declaringType.getPackage();
	}
	
	/**
	 * Finds the declaration of a method specified by <code>methodName</code> and </code>parameters</code> in
	 * the type hierarchy denoted by the given type. Returns <code>null</code> if no such method
	 * exists. If the method is defined in more than one super type only the first match is 
	 * returned. First the super class is examined and than the implemented interfaces.
	 * @param type The type to search the method in
	 * @param methodName The name of the method to find
	 * @param parameters The parameter types of the method to find. If <code>null</code> is passed, only the name is matched and parameters are ignored.
	 * @return the method binding representing the method
	 */
	public static IMethodBinding findDeclarationInHierarchy(ITypeBinding type, String methodName, ITypeBinding[] parameters) {
		ITypeBinding[] interfaces= type.getInterfaces();
		for (int i= 0; i < interfaces.length; i++) {
			ITypeBinding curr= interfaces[i];
			IMethodBinding method= findMethodInType(curr, methodName, parameters);
			if (method != null)
				return method;
			method= findDeclarationInHierarchy(interfaces[i], methodName, parameters);
			if (method != null)
				return method;
		}
		ITypeBinding superClass= type.getSuperclass();
		if (superClass != null) {
			IMethodBinding method= findMethodInType(superClass, methodName, parameters);
			if (method != null)
				return method;
			
			method= findDeclarationInHierarchy(superClass, methodName, parameters);
			if (method != null)
				return method;			
		}
		return null;
	}
	
	/**
	 * Returns all super types (classes and interfaces) for the given type.
	 * @param type The type to get the supertypes of.
	 * @return all super types (excluding <code>type</code>)
	 */
	public static ITypeBinding[] getAllSuperTypes(ITypeBinding type) {
		Set result= new HashSet();
		collectSuperTypes(type, result);
		result.remove(type);
		return (ITypeBinding[]) result.toArray(new ITypeBinding[result.size()]);
	}
	
	private static void collectSuperTypes(ITypeBinding curr, Set collection) {
		if (collection.add(curr)) {
			ITypeBinding[] interfaces= curr.getInterfaces();
			for (int i= 0; i < interfaces.length; i++) {
				collectSuperTypes(interfaces[i], collection);
			}
			ITypeBinding superClass= curr.getSuperclass();
			if (superClass != null) {
				collectSuperTypes(superClass, collection);
			}
		}
	}

	/**
	 * Method to visit a type hierarchy defined by a given type.
	 * 
	 * @param type the type which hierarchy is to be visited
	 * @param visitor the visitor
	 * @return <code>false</code> if the visiting got interrupted
	 */
	public static boolean visitHierarchy(ITypeBinding type, TypeBindingVisitor visitor) {
		boolean result= visitSuperclasses(type, visitor);
		if(result) {
			result= visitInterfaces(type, visitor);
		}
		return result;
	}

	/**
	 * Method to visit a interface hierarchy defined by a given type.
	 * 
	 * @param type the type which interface hierarchy is to be visited
	 * @param visitor the visitor
	 * @return <code>false</code> if the visiting got interrupted
	 */
	public static boolean visitInterfaces(ITypeBinding type, TypeBindingVisitor visitor) {
		ITypeBinding[] interfaces= type.getInterfaces();
		for (int i= 0; i < interfaces.length; i++) {
			if (!visitor.visit(interfaces[i])) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Method to visit a super class hierarchy defined by a given type.
	 * 
	 * @param type the type which super class hierarchy is to be visited
	 * @param visitor the visitor
	 * @return <code>false</code> if the visiting got interrupted
	 */
	public static boolean visitSuperclasses(ITypeBinding type, TypeBindingVisitor visitor) {
		while ((type= type.getSuperclass()) != null) {
			if (!visitor.visit(type)) {
				return false;
			}
		}
		return true;
	}

	public static boolean isEqualMethod(IMethodBinding method, String methodName, ITypeBinding[] parameters) {
		if (!method.getName().equals(methodName))
			return false;
			
		ITypeBinding[] methodParameters= method.getParameterTypes();
		if (methodParameters.length != parameters.length)
			return false;
		for (int i= 0; i < parameters.length; i++) {
			if (!equals(methodParameters[i].getErasure(), parameters[i].getErasure()))
				return false;
		}
		return true;
	}

	public static boolean isEqualMethod(IMethodBinding method, String methodName, String[] parameters) {
		if (!method.getName().equals(methodName))
			return false;

		ITypeBinding[] methodParameters= method.getParameterTypes();
		if (methodParameters.length != parameters.length)
			return false;
		String first, second;
		int index;
		for (int i= 0; i < parameters.length; i++) {
			first= parameters[i];
			index= first.indexOf('<');
			if (index > 0)
				first= first.substring(0, index);
			second= methodParameters[i].getErasure().getQualifiedName();
			index= second.indexOf('<');
			if (index > 0)
				second= second.substring(0, index);
			if (!first.equals(second))
				return false;
		}
		return true;
	}

	/**
	 * Finds a type binding for a given fully qualified type in the hierarchy of a type.
	 * Returns <code>null</code> if no type binding is found.
	 * @param hierarchyType the binding representing the hierarchy
	 * @param fullyQualifiedTypeName the fully qualified name to search for
	 * @return the type binding
	 */
	public static ITypeBinding findTypeInHierarchy(ITypeBinding hierarchyType, String fullyQualifiedTypeName) {
		if (hierarchyType.isArray() || hierarchyType.isPrimitive()) {
			return null;
		}
		if (fullyQualifiedTypeName.equals(hierarchyType.getQualifiedName())) {
			return hierarchyType;
		}
		ITypeBinding superClass= hierarchyType.getSuperclass();
		if (superClass != null) {
			ITypeBinding res= findTypeInHierarchy(superClass, fullyQualifiedTypeName);
			if (res != null) {
				return res;
			}
		}
		ITypeBinding[] superInterfaces= hierarchyType.getInterfaces();
		for (int i= 0; i < superInterfaces.length; i++) {
			ITypeBinding res= findTypeInHierarchy(superInterfaces[i], fullyQualifiedTypeName);
			if (res != null) {
				return res;
			}			
		}
		return null;
	}
	
	/**
	 * Returns the binding of the variable written in an Assignment.
	 * @param assignment The assignment 
	 * @return The binding or <code>null</code> if no bindings are available.
	 */
	public static IVariableBinding getAssignedVariable(Assignment assignment) {
		Expression leftHand = assignment.getLeftHandSide();
		switch (leftHand.getNodeType()) {
			case ASTNode.SIMPLE_NAME:
				return (IVariableBinding) ((SimpleName) leftHand).resolveBinding();
			case ASTNode.QUALIFIED_NAME:
				return (IVariableBinding) ((QualifiedName) leftHand).getName().resolveBinding();				
			case ASTNode.FIELD_ACCESS:
				return ((FieldAccess) leftHand).resolveFieldBinding();
			case ASTNode.SUPER_FIELD_ACCESS:
				return ((SuperFieldAccess) leftHand).resolveFieldBinding();
			default:
				return null;
		}
	}
	
	/**
	 * Returns <code>true</code> if the given type is a super type of a candidate.
	 * <code>true</code> is returned if the two type bindings are identical (TODO)
	 * @param type the type to inspect
	 * @param candidate the candidates
	 * @return <code>true</code> is a super type of one of the candidates; otherwise
	 *  <code>false</code>
	 */
	public static boolean isSuperType(ITypeBinding type, ITypeBinding candidate) {
		if (candidate.isArray() || candidate.isPrimitive()) {
			return false;
		}
		if (Bindings.equals(candidate, type)) {
			return true;
		}
		ITypeBinding superClass= candidate.getSuperclass();
		if (superClass != null) {
			if (isSuperType(type, superClass)) {
				return true;
			}
		}
		
		if (type.isInterface()) {
			ITypeBinding[] superInterfaces= candidate.getInterfaces();
			for (int i= 0; i < superInterfaces.length; i++) {
				if (isSuperType(type, superInterfaces[i])) {
					return true;
				}			
			}
		}
		return false;
	}
	

	// find IJavaElements for bindings

	/**
	 * Finds the compilation unit where the type of the given <code>ITypeBinding</code> is defined,
	 * using the class path defined by the given Java project. Returns <code>null</code>
	 * if no compilation unit is found (e.g. type binding is from a binary type)
	 * @param typeBinding the type binding to search for
	 * @param project the project used as a scope
	 * @return the compilation unit containing the type
	 * @throws JavaModelException if an errors occurs in the Java model
	 */
	public static ICompilationUnit findCompilationUnit(ITypeBinding typeBinding, IJavaProject project) throws JavaModelException {
		ICompilationUnit coreFindCompilationUnit= coreFindCompilationUnit(typeBinding);
		if (CHECK_CORE_BINDING_GET_JAVA_ELEMENT) {
			ICompilationUnit originalFindCompilationUnit= originalFindCompilationUnit(typeBinding, project);
			if (coreFindCompilationUnit == null) {
				if (originalFindCompilationUnit != null) {
					JavaPlugin.logRepeatedMessage("ITypeBinding#getJavaElement() is not supposed to be null: ", //$NON-NLS-1$
							"typeBinding == " + typeBinding.getKey() + ", project == " + project.getElementName()  //$NON-NLS-1$//$NON-NLS-2$
							+ ", coreFindCompilationUnit == " + coreFindCompilationUnit + ", originalFindCompilationUnit == " + originalFindCompilationUnit);  //$NON-NLS-1$//$NON-NLS-2$
				}
			} else {
				if (! coreFindCompilationUnit.equals(originalFindCompilationUnit)) {
					JavaPlugin.logRepeatedMessage("ITypeBinding#getJavaElement() is not correct element: ", //$NON-NLS-1$
							"typeBinding == " + typeBinding.getKey() + ", project == " + project.getElementName()  //$NON-NLS-1$//$NON-NLS-2$
							+ ", coreFindCompilationUnit == " + coreFindCompilationUnit + ", originalFindCompilationUnit == " + originalFindCompilationUnit);  //$NON-NLS-1$//$NON-NLS-2$
				}
			}
		}
		return coreFindCompilationUnit;
	}

	private static ICompilationUnit coreFindCompilationUnit(ITypeBinding typeBinding) {
		IJavaElement type= typeBinding.getJavaElement();
		if (type instanceof IType)
			return ((IType) type).getCompilationUnit();
		else
			return null;
	}

	private static ICompilationUnit originalFindCompilationUnit(ITypeBinding typeBinding, IJavaProject project) throws JavaModelException {
		if (!typeBinding.isFromSource()) {
			return null;
		}
		while (typeBinding != null && !typeBinding.isTopLevel()) {
			typeBinding= typeBinding.getDeclaringClass();
		}
		if (typeBinding != null) {
			typeBinding= typeBinding.getTypeDeclaration();
			IPackageBinding pack= typeBinding.getPackage();
			String packageName= pack.isUnnamed() ? "" : pack.getName(); //$NON-NLS-1$
			IType type= project.findType(packageName, typeBinding.getName());
			if (type != null) {
				return type.getCompilationUnit();
			}
		}
		return null;
	}

	/**
	 * Finds a field for the given <code>IVariableBinding</code>
	 * using the class path defined by the given Java project. Returns <code>null</code>
	 * if the field could not be found.
	 * @param field the field to search for
	 * @param in the project defining the scope
	 * @return the corresponding IField
	 * @throws JavaModelException if an error occurs in the Java model
	 */
	public static IField findField(IVariableBinding field, IJavaProject in) throws JavaModelException {
		Assert.isTrue(field.isField());
		IField iField= (IField) field.getJavaElement();
		if (CHECK_CORE_BINDING_GET_JAVA_ELEMENT) {
			IField originalFindField= originalFindField(field, in);
			if (iField != null && ! iField.equals(originalFindField)
					|| iField == null && originalFindField != null) {
				JavaPlugin.logRepeatedMessage("IVariableBinding#getJavaElement() is not correct element: ", //$NON-NLS-1$
						"field == " + field.getKey() + ", project == " + in.getElementName()  //$NON-NLS-1$//$NON-NLS-2$
						+ ", iField == " + iField + ", originalFindField == " + originalFindField);  //$NON-NLS-1$//$NON-NLS-2$
			}
		}
		return iField;
	}

	private static IField originalFindField(IVariableBinding field, IJavaProject in) throws JavaModelException {
		ITypeBinding declaringClassBinding = field.getDeclaringClass();
		if (declaringClassBinding == null)
			return null;
		IType declaringClass = findType(declaringClassBinding, in);
		if (declaringClass == null)
			return null;
		IField foundField= declaringClass.getField(field.getName());
		if (! foundField.exists())
	    	return null;
		return foundField;
	}

	/**
	 * Finds a type for the given <code>ITypeBinding</code>
	 * using the class path defined by the given Java project. Returns <code>null</code>
	 * if the type could not be found.
	 * @param type the type to find
	 * @param scope the project scope
	 * @return the corresponding IType or <code>null</code>
	 * @throws JavaModelException if an error occurs in the Java model
	 */
	public static IType findType(ITypeBinding type, IJavaProject scope) throws JavaModelException {
		IType iType= (IType) type.getJavaElement();
		if (CHECK_CORE_BINDING_GET_JAVA_ELEMENT) {
			IType originalFindType= originalFindType(type, scope);
			if (iType != null && ! iType.equals(originalFindType)
					|| iType == null && originalFindType != null) {
				JavaPlugin.logRepeatedMessage("ITypeBinding#getJavaElement() is not correct element: ", //$NON-NLS-1$
						"type == " + type.getKey() + ", project == " + scope.getElementName()  //$NON-NLS-1$//$NON-NLS-2$
						+ ", iType == " + iType + ", originalFindType == " + originalFindType);  //$NON-NLS-1$//$NON-NLS-2$
			}
		}
		return iType;
	}

	private static IType originalFindType(ITypeBinding type, IJavaProject scope) throws JavaModelException {
		if (type.isPrimitive() || type.isAnonymous() || type.isNullType())
			return null;
		if (type.isArray())
			return findType(type.getElementType(), scope);
			
		// TODO: Bug 36032: JavaCore should allow to find secondary top level types.
		
		String[] typeElements= Bindings.getNameComponents(type);
		IJavaElement element= scope.findElement(getPathToCompilationUnit(type.getPackage(), typeElements[0]));
		IType candidate= null;
		if (element instanceof ICompilationUnit) {
			candidate= ((ICompilationUnit)element).getType(typeElements[0]);
		} else if (element instanceof IClassFile) {
			candidate= ((IClassFile)element).getType();
		} else if (element == null) {
			if (type.isMember())
				candidate= JavaModelUtil.findType(scope, Bindings.getFullyQualifiedName(type.getDeclaringClass()));
			else
				candidate= JavaModelUtil.findType(scope, Bindings.getFullyQualifiedName(type));
		}
		
		if (candidate == null || typeElements.length == 1)
			return candidate;
			
		return findTypeInType(typeElements, candidate);
	}

	/**
	 * Finds a method for the given <code>IMethodBinding</code>. Returns
	 * <code>null</code> if the method can not be found in the declaring type of the method binding.
	 * @param method the method to find
	 * @param scope the project scope 
	 * @return the corresponding IMethod or <code>null</code> 
	 * @throws JavaModelException if an error occurs in the Java model
	 */
	public static IMethod findMethod(IMethodBinding method, IJavaProject scope) throws JavaModelException {
		if (! USE_UI_BINDING_GET_JAVA_ELEMENT) {
			IMethod iMethod= (IMethod) method.getJavaElement();
			if (CHECK_CORE_BINDING_GET_JAVA_ELEMENT) {
				IMethod originalFindMethod= originalFindMethod(method, scope);
				if (iMethod != null && ! iMethod.equals(originalFindMethod)
						|| iMethod == null && originalFindMethod != null) {
					JavaPlugin.logRepeatedMessage("IMethodBinding#getJavaElement() is not correct element: ", //$NON-NLS-1$
							"method == " + method.getKey() + ", project == " + scope.getElementName()  //$NON-NLS-1$//$NON-NLS-2$
							+ ", iMethod == " + iMethod + ", originalFindMethod == " + originalFindMethod);  //$NON-NLS-1$//$NON-NLS-2$
				}
			}
			return iMethod;
		}
		
		IMethod originalFindMethod= originalFindMethod(method, scope);
		if (CHECK_CORE_BINDING_GET_JAVA_ELEMENT) {
			IMethod iMethod= (IMethod) method.getJavaElement();
			if (iMethod != null && ! iMethod.equals(originalFindMethod)
					|| iMethod == null && originalFindMethod != null) {
				JavaPlugin.logRepeatedMessage("IMethodBinding#getJavaElement() is not correct element: ", //$NON-NLS-1$
						"method == " + method.getKey() + ", project == " + scope.getElementName()  //$NON-NLS-1$//$NON-NLS-2$
						+ ", iMethod == " + iMethod + ", originalFindMethod == " + originalFindMethod);  //$NON-NLS-1$//$NON-NLS-2$
			}
		}
		return originalFindMethod;	
	}

	private static IMethod originalFindMethod(IMethodBinding method, IJavaProject scope) throws JavaModelException {
		IType type= findType(method.getDeclaringClass(), scope);
		if (type == null)
			return null;
		return findMethod(method, type);
	}
	
	/**
	 * Finds a method for the given <code>IMethodBinding</code>. Returns
	 * <code>null</code> if the type doesn't contain a corresponding method.
	 * @param method the method to find
	 * @param type the type to look in
	 * @return the corresponding IMethod or <code>null</code>
	 * @throws JavaModelException if an error occurs in the Java model
	 */
	public static IMethod findMethod(IMethodBinding method, IType type) throws JavaModelException {
		method= method.getMethodDeclaration();
		
		IMethod[] candidates= type.getMethods();
		for (int i= 0; i < candidates.length; i++) {
			IMethod candidate= candidates[i];
			if (candidate.getElementName().equals(method.getName()) && sameParameters(method, candidate)) {
				return candidate;
			}
		}
		return null;
	}			

	//---- Helper methods to convert a type --------------------------------------------
	
	private static IPath getPathToCompilationUnit(IPackageBinding packageBinding, String topLevelTypeName) {
		IPath result= Path.EMPTY;
		String[] packageNames= packageBinding.getNameComponents();
		for (int i= 0; i < packageNames.length; i++) {
			result= result.append(packageNames[i]);
		}
		return result.append(topLevelTypeName + ".java"); //$NON-NLS-1$
	}

	private static IType findTypeInType(String[] typeElements, IType jmType) {
		IType result= jmType;
		for (int i= 1; i < typeElements.length; i++) {
			result= result.getType(typeElements[i]);
			if (!result.exists())
				return null;
		}
		return result == jmType ? null : result;
	}

	//---- Helper methods to convert a method ---------------------------------------------
	
	private static boolean sameParameters(IMethodBinding method, IMethod candidate) throws JavaModelException {
		ITypeBinding[] methodParamters= method.getParameterTypes();
		String[] candidateParameters= candidate.getParameterTypes();
		if (methodParamters.length != candidateParameters.length)
			return false;
		IType scope= candidate.getDeclaringType();
		for (int i= 0; i < methodParamters.length; i++) {
			ITypeBinding methodParameter= methodParamters[i];
			String candidateParameter= candidateParameters[i];
			if (!sameParameter(methodParameter, candidateParameter, scope))
				return false;
		}
		return true;
	}

	private static boolean sameParameter(ITypeBinding type, String candidate, IType scope) throws JavaModelException {
		if (type.getDimensions() != Signature.getArrayCount(candidate))
			return false;
			
		// Normalizes types
		if (type.isArray())
			type= type.getElementType();
		candidate= Signature.getElementType(candidate);
		
		if (isPrimitiveType(candidate) != type.isPrimitive()) {
			return false;
		}
			
		if (type.isPrimitive() || type.isTypeVariable()) {
			return type.getName().equals(Signature.toString(candidate));
		} else {
			// normalize (quick hack until binding.getJavaElement works)
			candidate= Signature.getTypeErasure(candidate);
			type= type.getErasure();
			
			if (isResolvedType(candidate)) {
				return Signature.toString(candidate).equals(Bindings.getFullyQualifiedName(type));
			} else {
				String[][] qualifiedCandidates= scope.resolveType(Signature.toString(candidate));
				if (qualifiedCandidates == null || qualifiedCandidates.length == 0)
					return false;
				String packageName= type.getPackage().isUnnamed() ? "" : type.getPackage().getName(); //$NON-NLS-1$
				String typeName= getTypeQualifiedName(type);
				for (int i= 0; i < qualifiedCandidates.length; i++) {
					String[] qualifiedCandidate= qualifiedCandidates[i];
					if (	qualifiedCandidate[0].equals(packageName) &&
							qualifiedCandidate[1].equals(typeName))
						return true;
				}
			}
		}
		return false;
	}

	private static boolean isPrimitiveType(String s) {
		return Signature.getTypeSignatureKind(s) == Signature.BASE_TYPE_SIGNATURE;
	}
	
	private static boolean isResolvedType(String s) {
		int arrayCount= Signature.getArrayCount(s);
		return s.charAt(arrayCount) == Signature.C_RESOLVED;
	}

	/**
	 * Normalizes a type binding received from an expression to a type binding that can be used in a declaration signature. 
	 * Anonymous types are normalized, to the super class or interface. For null or void bindings
	 * <code>null</code> is returned. 
	 * @param binding the binding to normalize
	 * @return the normalized binding
	 */
	public static ITypeBinding normalizeTypeBinding(ITypeBinding binding) {
		if (binding != null && !binding.isNullType() && !isVoidType(binding)) {
			if (binding.isAnonymous()) {
				ITypeBinding[] baseBindings= binding.getInterfaces();
				if (baseBindings.length > 0) {
					return baseBindings[0];
				}
				return binding.getSuperclass();
			}
			if (binding.isCapture()) {
				return binding.getWildcard();
			}
			return binding;
		}
		return null;
	}
	
	public static boolean isVoidType(ITypeBinding binding) {
		return "void".equals(binding.getName()); //$NON-NLS-1$
	}
	
	
	/**
	 * Normalizes the binding so that it can be used as a type inside a declaration
	 * (e.g. variable declaration, method return type, parameter type, ...). For
	 * null bindings Object is returned.
	 * 
	 * @return the normalized type to be used in declarations
	 */
	public static ITypeBinding normalizeForDeclarationUse(ITypeBinding binding, AST ast) {
		if (binding.isNullType())
			return ast.resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
		if (binding.isPrimitive())
			return binding;
		binding= normalizeTypeBinding(binding);
		if (binding == null || !binding.isWildcardType())
			return binding;
		if (binding.isUpperbound()) {
			return binding.getBound();
		} else {
			return ast.resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
		}
	}

	/**
	 * Returns the type binding of the node's parent type declaration
	 * @param node
	 * @return CompilationUnit
	 */
	public static ITypeBinding getBindingOfParentType(ASTNode node) {
		while (node != null) {
			if (node instanceof AbstractTypeDeclaration) {
				return ((AbstractTypeDeclaration) node).resolveBinding();
			} else if (node instanceof AnonymousClassDeclaration) {
				return ((AnonymousClassDeclaration) node).resolveBinding();
			}
			node= node.getParent();
		}
		return null;
	}				

	
	public static String getRawName(ITypeBinding binding) {
		String name= binding.getName();
		if (binding.isParameterizedType() || binding.isGenericType()) {
			int idx= name.indexOf('<');
			if (idx != -1) {
				return name.substring(0, idx);
			}
		}
		return name;
	}
	

	public static String getRawQualifiedName(ITypeBinding binding) {
		final String EMPTY= ""; //$NON-NLS-1$
		
		if (binding.isAnonymous() || binding.isLocal()) {
			return EMPTY; //$NON-NLS-1$
		}
		
		if (binding.isPrimitive() || binding.isNullType() || binding.isTypeVariable()) {
			return binding.getName();
		}
		
		if (binding.isArray()) {
			String elementTypeQualifiedName = getRawQualifiedName(binding.getElementType());
			if (elementTypeQualifiedName.length() != 0) {
				StringBuffer stringBuffer= new StringBuffer(elementTypeQualifiedName);
				stringBuffer.append('[').append(']');
				return stringBuffer.toString();
			} else {
				return EMPTY;
			}
		}
		if (binding.isMember()) {
			String outerName= getRawQualifiedName(binding.getDeclaringClass());
			if (outerName.length() > 0) {
				StringBuffer buffer= new StringBuffer();
				buffer.append(outerName);
				buffer.append('.');
				buffer.append(getRawName(binding));
				return buffer.toString();
			} else {
				return EMPTY;
			}

		} else if (binding.isTopLevel()) {
			IPackageBinding packageBinding= binding.getPackage();
			StringBuffer buffer= new StringBuffer();
			if (packageBinding != null && packageBinding.getName().length() > 0) {
				buffer.append(packageBinding.getName()).append('.');
			}
			buffer.append(getRawName(binding));
			return buffer.toString();
		}
		return EMPTY;
	}
	

	/**
	 * Get field declaration. See bug 83100
	 */
	public static IVariableBinding getVariableDeclaration(IVariableBinding var) {
		ITypeBinding declaringClass= var.getDeclaringClass();
		if (declaringClass == null) {
			return var;
		}
		if (declaringClass.getTypeDeclaration() == declaringClass) { // test if type is already declaration
			return var;
		}
		IVariableBinding[] genericFields= declaringClass.getTypeDeclaration().getDeclaredFields();
		String name= var.getName();
		for (int i= 0; i < genericFields.length; i++) {
			if (name.equals(genericFields[i].getName())) {
				return genericFields[i];
			}
		}
		Assert.isTrue(false, "field does not exist in generic type"); //$NON-NLS-1$
		return var;
	}

	/**
	 * Tests if the given node is a declaration, not a instance of a generic type, method or field.
	 * Declarations can be found in AST with CompilationUnit.findDeclaringNode
	 */
	public static boolean isDeclarationBinding(IBinding binding) {
		switch (binding.getKind()) {
			case IBinding.TYPE:
				return ((ITypeBinding) binding).getTypeDeclaration() == binding;
			case IBinding.VARIABLE:
				IVariableBinding var= (IVariableBinding) binding;
				return !var.isField() || isDeclarationBinding(var.getDeclaringClass());
			case IBinding.METHOD:
				return ((IMethodBinding) binding).getMethodDeclaration() == binding;
		}
		return true;
	}

	public static boolean containsOverridingMethod(IMethodBinding[] candidates, IMethodBinding overridable) {
		for (int index= 0; index < candidates.length; index++) {
			if (areOverriddenMethods(candidates[index], overridable))
				return true;
		}
		return false;
	}

	public static boolean containsSignatureEquivalentConstructor(IMethodBinding[] candidates, IMethodBinding overridable) {
		for (int index= 0; index < candidates.length; index++) {
			if (isSignatureEquivalentConstructor(candidates[index], overridable))
				return true;
		}
		return false;
	}

	public static boolean isSignatureEquivalentConstructor(IMethodBinding overridden, IMethodBinding overridable) {

		if (!overridden.isConstructor() || !overridable.isConstructor())
			return false;
		
		if (overridden.isDefaultConstructor())
			return false;
		
		return areSubTypeCompatible(overridden, overridable);
	}
	
	public static boolean areOverriddenMethods(IMethodBinding overridden, IMethodBinding overridable) {

		if (!overridden.getName().equals(overridable.getName()))
			return false;

		return areSubTypeCompatible(overridden, overridable);
	}

	private static boolean areSubTypeCompatible(IMethodBinding overridden, IMethodBinding overridable) {
		
		if (overridden.getParameterTypes().length != overridable.getParameterTypes().length)
			return false;
		
		ITypeBinding overriddenReturn= overridden.getReturnType();
		ITypeBinding overridableReturn= overridable.getReturnType();
		if (overriddenReturn == null || overridableReturn == null)
			return false;
		
		if (!overriddenReturn.getErasure().isSubTypeCompatible(overridableReturn.getErasure()))
			return false;
		
		ITypeBinding[] overriddenTypes= overridden.getParameterTypes();
		ITypeBinding[] overridableTypes= overridable.getParameterTypes();
		Assert.isTrue(overriddenTypes.length == overridableTypes.length);
		for (int index= 0; index < overriddenTypes.length; index++) {
			final ITypeBinding overridableErasure= overridableTypes[index].getErasure();
			final ITypeBinding overriddenErasure= overriddenTypes[index].getErasure();
			if (!overridableErasure.isSubTypeCompatible(overriddenErasure) || !overridableErasure.getKey().equals(overriddenErasure.getKey()))
				return false;
		}
		ITypeBinding[] overriddenExceptions= overridden.getExceptionTypes();
		ITypeBinding[] overridableExceptions= overridable.getExceptionTypes();
		boolean checked= false;
		for (int index= 0; index < overriddenExceptions.length; index++) {
			checked= false;
			for (int offset= 0; offset < overridableExceptions.length; offset++) {
				if (overriddenExceptions[index].isSubTypeCompatible(overridableExceptions[offset]))
					checked= true;
			}
			if (!checked)
				return false;
		}
		return true;
	}
	
}
