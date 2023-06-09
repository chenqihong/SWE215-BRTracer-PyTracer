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
package org.eclipse.jdt.internal.corext.codemanipulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.WildcardType;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

import org.eclipse.jdt.ui.CodeGeneration;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Utilities for code generation based on ast rewrite.
 * 
 * @since 3.1
 */
public final class StubUtility2 {

	private static void addOverrideAnnotation(ASTRewrite rewrite, MethodDeclaration decl, IMethodBinding binding) {
		if (!binding.getDeclaringClass().isInterface()) {
			final Annotation marker= rewrite.getAST().newMarkerAnnotation();
			marker.setTypeName(rewrite.getAST().newSimpleName("Override")); //$NON-NLS-1$
			rewrite.getListRewrite(decl, MethodDeclaration.MODIFIERS2_PROPERTY).insertFirst(marker, null);
		}
	}

	public static MethodDeclaration createConstructorStub(ICompilationUnit unit, ASTRewrite rewrite, ImportRewrite imports, IMethodBinding binding, String type, int modifiers, boolean omitSuper, CodeGenerationSettings settings) throws CoreException {
		AST ast= rewrite.getAST();
		MethodDeclaration decl= ast.newMethodDeclaration();
		decl.modifiers().addAll(ASTNodeFactory.newModifiers(ast, modifiers & ~Modifier.ABSTRACT & ~Modifier.NATIVE));
		decl.setName(ast.newSimpleName(type));
		decl.setConstructor(true);

		ITypeBinding[] typeParams= binding.getTypeParameters();
		List typeParameters= decl.typeParameters();
		for (int i= 0; i < typeParams.length; i++) {
			ITypeBinding curr= typeParams[i];
			TypeParameter newTypeParam= ast.newTypeParameter();
			newTypeParam.setName(ast.newSimpleName(curr.getName()));
			ITypeBinding[] typeBounds= curr.getTypeBounds();
			if (typeBounds.length != 1 || !"java.lang.Object".equals(typeBounds[0].getQualifiedName())) {//$NON-NLS-1$
				List newTypeBounds= newTypeParam.typeBounds();
				for (int k= 0; k < typeBounds.length; k++) {
					newTypeBounds.add(imports.addImport(typeBounds[k], ast));
				}
			}
			typeParameters.add(newTypeParam);
		}

		List parameters= createParameters(unit, imports, ast, binding, decl);

		List thrownExceptions= decl.thrownExceptions();
		ITypeBinding[] excTypes= binding.getExceptionTypes();
		for (int i= 0; i < excTypes.length; i++) {
			String excTypeName= imports.addImport(excTypes[i]);
			thrownExceptions.add(ASTNodeFactory.newName(ast, excTypeName));
		}

		Block body= ast.newBlock();
		decl.setBody(body);

		String delimiter= StubUtility.getLineDelimiterUsed(unit);
		String bodyStatement= ""; //$NON-NLS-1$
		if (!omitSuper) {
			SuperConstructorInvocation invocation= ast.newSuperConstructorInvocation();
			SingleVariableDeclaration varDecl= null;
			for (Iterator iterator= parameters.iterator(); iterator.hasNext();) {
				varDecl= (SingleVariableDeclaration) iterator.next();
				invocation.arguments().add(ast.newSimpleName(varDecl.getName().getIdentifier()));
			}
			bodyStatement= ASTNodes.asFormattedString(invocation, 0, delimiter);
		}

		String placeHolder= CodeGeneration.getMethodBodyContent(unit, type, binding.getName(), true, bodyStatement, delimiter);
		if (placeHolder != null) {
			ASTNode todoNode= rewrite.createStringPlaceholder(placeHolder, ASTNode.RETURN_STATEMENT);
			body.statements().add(todoNode);
		}

		if (settings != null && settings.createComments) {
			String string= getMethodComment(unit, type, decl, binding, delimiter);
			if (string != null) {
				Javadoc javadoc= (Javadoc) rewrite.createStringPlaceholder(string, ASTNode.JAVADOC);
				decl.setJavadoc(javadoc);
			}
		}
		return decl;
	}

	public static MethodDeclaration createConstructorStub(ICompilationUnit unit, ASTRewrite rewrite, ImportRewrite imports, ITypeBinding typeBinding, AST ast, IMethodBinding superConstructor, IVariableBinding[] variableBindings, int modifiers, CodeGenerationSettings settings) throws CoreException {

		MethodDeclaration decl= ast.newMethodDeclaration();
		decl.modifiers().addAll(ASTNodeFactory.newModifiers(ast, modifiers & ~Modifier.ABSTRACT & ~Modifier.NATIVE));
		decl.setName(ast.newSimpleName(typeBinding.getName()));
		decl.setConstructor(true);

		List parameters= decl.parameters();
		if (superConstructor != null) {
			ITypeBinding[] typeParams= superConstructor.getTypeParameters();
			List typeParameters= decl.typeParameters();
			for (int i= 0; i < typeParams.length; i++) {
				ITypeBinding curr= typeParams[i];
				TypeParameter newTypeParam= ast.newTypeParameter();
				newTypeParam.setName(ast.newSimpleName(curr.getName()));
				ITypeBinding[] typeBounds= curr.getTypeBounds();
				if (typeBounds.length != 1 || !"java.lang.Object".equals(typeBounds[0].getQualifiedName())) {//$NON-NLS-1$
					List newTypeBounds= newTypeParam.typeBounds();
					for (int k= 0; k < typeBounds.length; k++) {
						newTypeBounds.add(imports.addImport(typeBounds[k], ast));
					}
				}
				typeParameters.add(newTypeParam);
			}

			createParameters(unit, imports, ast, superConstructor, decl);

			List thrownExceptions= decl.thrownExceptions();
			ITypeBinding[] excTypes= superConstructor.getExceptionTypes();
			for (int i= 0; i < excTypes.length; i++) {
				String excTypeName= imports.addImport(excTypes[i]);
				thrownExceptions.add(ASTNodeFactory.newName(ast, excTypeName));
			}
		}

		Block body= ast.newBlock();
		decl.setBody(body);

		String delimiter= StubUtility.getLineDelimiterUsed(unit);

		String bodyStatement= ""; //$NON-NLS-1$
		if (superConstructor != null) {
			SuperConstructorInvocation invocation= ast.newSuperConstructorInvocation();
			SingleVariableDeclaration varDecl= null;
			for (Iterator iterator= parameters.iterator(); iterator.hasNext();) {
				varDecl= (SingleVariableDeclaration) iterator.next();
				invocation.arguments().add(ast.newSimpleName(varDecl.getName().getIdentifier()));
			}
			bodyStatement= ASTNodes.asFormattedString(invocation, 0, delimiter);
		}

		List prohibited= new ArrayList();
		for (final Iterator iterator= parameters.iterator(); iterator.hasNext();)
			prohibited.add(((SingleVariableDeclaration) iterator.next()).getName().getIdentifier());
		String param= null;
		List list= new ArrayList(prohibited);
		String[] excluded= null;
		for (int i= 0; i < variableBindings.length; i++) {
			SingleVariableDeclaration var= ast.newSingleVariableDeclaration();
			var.setType(imports.addImport(variableBindings[i].getType(), ast));
			excluded= new String[list.size()];
			list.toArray(excluded);
			param= getParameterName(unit, variableBindings[i], excluded);
			list.add(param);
			var.setName(ast.newSimpleName(param));
			parameters.add(var);
		}

		String placeHolder= CodeGeneration.getMethodBodyContent(unit, typeBinding.getName(), typeBinding.getName(), true, bodyStatement, delimiter);
		if (placeHolder != null) {
			ASTNode todoNode= rewrite.createStringPlaceholder(placeHolder, ASTNode.RETURN_STATEMENT);
			body.statements().add(todoNode);
		}

		list= new ArrayList(prohibited);
		for (int i= 0; i < variableBindings.length; i++) {
			excluded= new String[list.size()];
			list.toArray(excluded);
			final String paramName= getParameterName(unit, variableBindings[i], excluded);
			list.add(paramName);
			final String fieldName= variableBindings[i].getName();
			Expression expression= null;
			if (paramName.equals(fieldName) || settings.useKeywordThis) {
				FieldAccess access= ast.newFieldAccess();
				access.setExpression(ast.newThisExpression());
				access.setName(ast.newSimpleName(fieldName));
				expression= access;
			} else
				expression= ast.newSimpleName(fieldName);
			Assignment assignment= ast.newAssignment();
			assignment.setLeftHandSide(expression);
			assignment.setRightHandSide(ast.newSimpleName(paramName));
			assignment.setOperator(Assignment.Operator.ASSIGN);
			body.statements().add(ast.newExpressionStatement(assignment));
		}

		if (settings != null && settings.createComments) {
			String string= getMethodComment(unit, typeBinding.getName(), decl, superConstructor, delimiter);
			if (string != null) {
				Javadoc javadoc= (Javadoc) rewrite.createStringPlaceholder(string, ASTNode.JAVADOC);
				decl.setJavadoc(javadoc);
			}
		}
		return decl;
	}

	public static MethodDeclaration createDelegationStub(ICompilationUnit unit, ASTRewrite rewrite, ImportRewrite imports, AST ast, IBinding[] bindings, CodeGenerationSettings settings) throws CoreException {
		Assert.isNotNull(bindings);
		Assert.isTrue(bindings.length == 2);
		Assert.isTrue(bindings[0] instanceof IVariableBinding);
		Assert.isTrue(bindings[1] instanceof IMethodBinding);

		IVariableBinding variableBinding= (IVariableBinding) bindings[0];
		IMethodBinding methodBinding= (IMethodBinding) bindings[1];

		MethodDeclaration decl= ast.newMethodDeclaration();
		decl.modifiers().addAll(ASTNodeFactory.newModifiers(ast, methodBinding.getModifiers() & ~Modifier.SYNCHRONIZED & ~Modifier.ABSTRACT & ~Modifier.NATIVE));

		decl.setName(ast.newSimpleName(methodBinding.getName()));
		decl.setConstructor(false);

		ITypeBinding[] typeParams= methodBinding.getTypeParameters();
		List typeParameters= decl.typeParameters();
		for (int i= 0; i < typeParams.length; i++) {
			ITypeBinding curr= typeParams[i];
			TypeParameter newTypeParam= ast.newTypeParameter();
			newTypeParam.setName(ast.newSimpleName(curr.getName()));
			ITypeBinding[] typeBounds= curr.getTypeBounds();
			if (typeBounds.length != 1 || !"java.lang.Object".equals(typeBounds[0].getQualifiedName())) {//$NON-NLS-1$
				List newTypeBounds= newTypeParam.typeBounds();
				for (int k= 0; k < typeBounds.length; k++) {
					newTypeBounds.add(imports.addImport(typeBounds[k], ast));
				}
			}
			typeParameters.add(newTypeParam);
		}

		decl.setReturnType2(imports.addImport(methodBinding.getReturnType(), ast));

		List parameters= decl.parameters();
		ITypeBinding[] params= methodBinding.getParameterTypes();
		String[] paramNames= suggestArgumentNames(unit.getJavaProject(), methodBinding);
		for (int i= 0; i < params.length; i++) {
			SingleVariableDeclaration varDecl= ast.newSingleVariableDeclaration();
			if (params[i].isWildcardType() && !params[i].isUpperbound())
				varDecl.setType(imports.addImport(params[i].getBound(), ast));
			else {
				if (methodBinding.isVarargs() && params[i].isArray() && i == params.length - 1) {
					StringBuffer buffer= new StringBuffer(imports.addImport(params[i].getElementType()));
					for (int dim= 1; dim < params[i].getDimensions(); dim++)
						buffer.append("[]"); //$NON-NLS-1$
					varDecl.setType(ASTNodeFactory.newType(ast, buffer.toString()));
					varDecl.setVarargs(true);
				} else
					varDecl.setType(imports.addImport(params[i], ast));
			}
			varDecl.setName(ast.newSimpleName(paramNames[i]));
			parameters.add(varDecl);
		}

		List thrownExceptions= decl.thrownExceptions();
		ITypeBinding[] excTypes= methodBinding.getExceptionTypes();
		for (int i= 0; i < excTypes.length; i++) {
			String excTypeName= imports.addImport(excTypes[i]);
			thrownExceptions.add(ASTNodeFactory.newName(ast, excTypeName));
		}

		Block body= ast.newBlock();
		decl.setBody(body);

		String delimiter= StubUtility.getLineDelimiterUsed(unit);

		Statement statement= null;
		MethodInvocation invocation= ast.newMethodInvocation();
		invocation.setName(ast.newSimpleName(methodBinding.getName()));
		List arguments= invocation.arguments();
		for (int i= 0; i < params.length; i++)
			arguments.add(ast.newSimpleName(paramNames[i]));
		if (settings.useKeywordThis) {
			FieldAccess access= ast.newFieldAccess();
			access.setExpression(ast.newThisExpression());
			access.setName(ast.newSimpleName(variableBinding.getName()));
			invocation.setExpression(access);
		} else
			invocation.setExpression(ast.newSimpleName(variableBinding.getName()));
		if (methodBinding.getReturnType().isPrimitive() && methodBinding.getReturnType().getName().equals("void")) {//$NON-NLS-1$
			statement= ast.newExpressionStatement(invocation);
		} else {
			ReturnStatement returnStatement= ast.newReturnStatement();
			returnStatement.setExpression(invocation);
			statement= returnStatement;
		}
		body.statements().add(statement);

		ITypeBinding declaringType= variableBinding.getDeclaringClass();
		String qualifiedName= declaringType.getQualifiedName();
		IPackageBinding packageBinding= declaringType.getPackage();
		if (packageBinding != null) {
			if (packageBinding.getName().length() > 0 && qualifiedName.startsWith(packageBinding.getName()))
				qualifiedName= qualifiedName.substring(packageBinding.getName().length());
		}

		if (settings != null && settings.createComments) {
			String string= getMethodComment(unit, qualifiedName, decl, methodBinding, delimiter);
			if (string != null) {
				Javadoc javadoc= (Javadoc) rewrite.createStringPlaceholder(string, ASTNode.JAVADOC);
				decl.setJavadoc(javadoc);
			}
		}
		return decl;
	}

	public static MethodDeclaration createImplementationStub(ICompilationUnit unit, ASTRewrite rewrite, ImportRewrite imports, AST ast, IMethodBinding binding, String type, CodeGenerationSettings settings, boolean deferred) throws CoreException {

		MethodDeclaration decl= ast.newMethodDeclaration();
		decl.modifiers().addAll(getImplementationModifiers(ast, binding, deferred));

		decl.setName(ast.newSimpleName(binding.getName()));
		decl.setConstructor(false);

		ITypeBinding[] typeParams= binding.getTypeParameters();
		List typeParameters= decl.typeParameters();
		for (int i= 0; i < typeParams.length; i++) {
			ITypeBinding curr= typeParams[i];
			TypeParameter newTypeParam= ast.newTypeParameter();
			newTypeParam.setName(ast.newSimpleName(curr.getName()));
			ITypeBinding[] typeBounds= curr.getTypeBounds();
			if (typeBounds.length != 1 || !"java.lang.Object".equals(typeBounds[0].getQualifiedName())) {//$NON-NLS-1$
				List newTypeBounds= newTypeParam.typeBounds();
				for (int k= 0; k < typeBounds.length; k++) {
					newTypeBounds.add(imports.addImport(typeBounds[k], ast));
				}
			}
			typeParameters.add(newTypeParam);
		}

		decl.setReturnType2(imports.addImport(binding.getReturnType(), ast));

		List parameters= createParameters(unit, imports, ast, binding, decl);

		List thrownExceptions= decl.thrownExceptions();
		ITypeBinding[] excTypes= binding.getExceptionTypes();
		for (int i= 0; i < excTypes.length; i++) {
			String excTypeName= imports.addImport(excTypes[i]);
			thrownExceptions.add(ASTNodeFactory.newName(ast, excTypeName));
		}

		String delimiter= StubUtility.getLineDelimiterUsed(unit);
		if (!deferred) {

			Block body= ast.newBlock();
			decl.setBody(body);

			String bodyStatement= ""; //$NON-NLS-1$
			ITypeBinding declaringType= binding.getDeclaringClass();
			if (Modifier.isAbstract(binding.getModifiers()) || declaringType.isAnnotation() || declaringType.isInterface()) {
				Expression expression= ASTNodeFactory.newDefaultExpression(ast, decl.getReturnType2(), decl.getExtraDimensions());
				if (expression != null) {
					ReturnStatement returnStatement= ast.newReturnStatement();
					returnStatement.setExpression(expression);
					bodyStatement= ASTNodes.asFormattedString(returnStatement, 0, delimiter);
				}
			} else {
				SuperMethodInvocation invocation= ast.newSuperMethodInvocation();
				invocation.setName(ast.newSimpleName(binding.getName()));
				SingleVariableDeclaration varDecl= null;
				for (Iterator iterator= parameters.iterator(); iterator.hasNext();) {
					varDecl= (SingleVariableDeclaration) iterator.next();
					invocation.arguments().add(ast.newSimpleName(varDecl.getName().getIdentifier()));
				}
				Expression expression= invocation;
				Type returnType= decl.getReturnType2();
				if (returnType != null && (returnType instanceof PrimitiveType) && ((PrimitiveType) returnType).getPrimitiveTypeCode().equals(PrimitiveType.VOID)) {
					bodyStatement= ASTNodes.asFormattedString(ast.newExpressionStatement(expression), 0, delimiter);
				} else {
					ReturnStatement returnStatement= ast.newReturnStatement();
					returnStatement.setExpression(expression);
					bodyStatement= ASTNodes.asFormattedString(returnStatement, 0, delimiter);
				}
			}

			String placeHolder= CodeGeneration.getMethodBodyContent(unit, type, binding.getName(), false, bodyStatement, delimiter);
			if (placeHolder != null) {
				ASTNode todoNode= rewrite.createStringPlaceholder(placeHolder, ASTNode.RETURN_STATEMENT);
				body.statements().add(todoNode);
			}
		}

		if (settings.createComments) {
			String string= getMethodComment(unit, type, decl, binding, delimiter);
			if (string != null) {
				Javadoc javadoc= (Javadoc) rewrite.createStringPlaceholder(string, ASTNode.JAVADOC);
				decl.setJavadoc(javadoc);
			}
		}
		if (settings.overrideAnnotation && JavaModelUtil.is50OrHigher(unit.getJavaProject())) {
			addOverrideAnnotation(rewrite, decl, binding);
		}

		return decl;
	}

	public static MethodDeclaration createImplementationStub(ICompilationUnit unit, ASTRewrite rewrite, ImportsStructure structure, IMethodBinding binding, String type, boolean deferred, CodeGenerationSettings settings) throws CoreException {
		AST ast= rewrite.getAST();
		MethodDeclaration decl= ast.newMethodDeclaration();
		decl.modifiers().addAll(getImplementationModifiers(ast, binding, deferred));

		decl.setName(ast.newSimpleName(binding.getName()));
		decl.setConstructor(false);

		ITypeBinding[] typeParams= binding.getTypeParameters();
		List typeParameters= decl.typeParameters();
		for (int index= 0; index < typeParams.length; index++) {
			ITypeBinding curr= typeParams[index];
			TypeParameter newTypeParam= ast.newTypeParameter();
			newTypeParam.setName(ast.newSimpleName(curr.getName()));
			ITypeBinding[] typeBounds= curr.getTypeBounds();
			if (typeBounds.length != 1 || !"java.lang.Object".equals(typeBounds[0].getQualifiedName())) {//$NON-NLS-1$
				List newTypeBounds= newTypeParam.typeBounds();
				for (int offset= 0; offset < typeBounds.length; offset++) {
					newTypeBounds.add(createTypeNode(structure, typeBounds[offset], ast));
				}
			}
			typeParameters.add(newTypeParam);
		}

		decl.setReturnType2(createTypeNode(structure, binding.getReturnType(), ast));

		List parameters= createParameters(unit, structure, ast, binding, decl);

		List thrownExceptions= decl.thrownExceptions();
		ITypeBinding[] excTypes= binding.getExceptionTypes();
		for (int index= 0; index < excTypes.length; index++)
			thrownExceptions.add(ASTNodeFactory.newName(ast, structure != null ? structure.addImport(excTypes[index]) : excTypes[index].getQualifiedName()));

		String delimiter= StubUtility.getLineDelimiterUsed(unit);
		if (!deferred) {

			Block body= ast.newBlock();
			decl.setBody(body);

			String bodyStatement= ""; //$NON-NLS-1$
			ITypeBinding declaringType= binding.getDeclaringClass();
			if (Modifier.isAbstract(binding.getModifiers()) || declaringType.isAnnotation() || declaringType.isInterface()) {
				Expression expression= ASTNodeFactory.newDefaultExpression(ast, decl.getReturnType2(), decl.getExtraDimensions());
				if (expression != null) {
					ReturnStatement returnStatement= ast.newReturnStatement();
					returnStatement.setExpression(expression);
					bodyStatement= ASTNodes.asFormattedString(returnStatement, 0, delimiter);
				}
			} else {
				SuperMethodInvocation invocation= ast.newSuperMethodInvocation();
				invocation.setName(ast.newSimpleName(binding.getName()));
				SingleVariableDeclaration varDecl= null;
				for (Iterator iterator= parameters.iterator(); iterator.hasNext();) {
					varDecl= (SingleVariableDeclaration) iterator.next();
					invocation.arguments().add(ast.newSimpleName(varDecl.getName().getIdentifier()));
				}
				Expression expression= invocation;
				Type returnType= decl.getReturnType2();
				if (returnType instanceof PrimitiveType && ((PrimitiveType) returnType).getPrimitiveTypeCode().equals(PrimitiveType.VOID)) {
					bodyStatement= ASTNodes.asFormattedString(ast.newExpressionStatement(expression), 0, delimiter);
				} else {
					ReturnStatement returnStatement= ast.newReturnStatement();
					returnStatement.setExpression(expression);
					bodyStatement= ASTNodes.asFormattedString(returnStatement, 0, delimiter);
				}
			}

			String placeHolder= CodeGeneration.getMethodBodyContent(unit, type, binding.getName(), false, bodyStatement, delimiter);
			if (placeHolder != null) {
				ASTNode todoNode= rewrite.createStringPlaceholder(placeHolder, ASTNode.RETURN_STATEMENT);
				body.statements().add(todoNode);
			}
		}

		if (settings != null && settings.createComments) {
			String string= getMethodComment(unit, type, decl, binding, delimiter);
			if (string != null) {
				Javadoc javadoc= (Javadoc) rewrite.createStringPlaceholder(string, ASTNode.JAVADOC);
				decl.setJavadoc(javadoc);
			}
		}
		if (settings.overrideAnnotation && JavaModelUtil.is50OrHigher(unit.getJavaProject())) {
			addOverrideAnnotation(rewrite, decl, binding);
		}
		return decl;
	}

	private static List createParameters(ICompilationUnit unit, ImportRewrite imports, AST ast, IMethodBinding binding, MethodDeclaration decl) {
		List parameters= decl.parameters();
		ITypeBinding[] params= binding.getParameterTypes();
		String[] paramNames= suggestArgumentNames(unit.getJavaProject(), binding);
		for (int i= 0; i < params.length; i++) {
			SingleVariableDeclaration var= ast.newSingleVariableDeclaration();
			if (binding.isVarargs() && params[i].isArray() && i == params.length - 1) {
				StringBuffer buffer= new StringBuffer(imports.addImport(params[i].getElementType()));
				for (int dim= 1; dim < params[i].getDimensions(); dim++)
					buffer.append("[]"); //$NON-NLS-1$
				var.setType(ASTNodeFactory.newType(ast, buffer.toString()));
				var.setVarargs(true);
			} else
				var.setType(imports.addImport(params[i], ast));
			var.setName(ast.newSimpleName(paramNames[i]));
			parameters.add(var);
		}
		return parameters;
	}

	private static List createParameters(ICompilationUnit unit, ImportsStructure structure, AST ast, IMethodBinding binding, MethodDeclaration decl) {
		List parameters= decl.parameters();
		ITypeBinding[] params= binding.getParameterTypes();
		String[] paramNames= suggestArgumentNames(unit.getJavaProject(), binding);
		for (int i= 0; i < params.length; i++) {
			SingleVariableDeclaration var= ast.newSingleVariableDeclaration();
			if (binding.isVarargs() && params[i].isArray() && i == params.length - 1) {
				final ITypeBinding elementType= params[i].getElementType();
				StringBuffer buffer= new StringBuffer(structure != null ? structure.addImport(elementType) : elementType.getQualifiedName());
				for (int dim= 1; dim < params[i].getDimensions(); dim++)
					buffer.append("[]"); //$NON-NLS-1$
				var.setType(ASTNodeFactory.newType(ast, buffer.toString()));
				var.setVarargs(true);
			} else
				var.setType(createTypeNode(structure, params[i], ast));
			var.setName(ast.newSimpleName(paramNames[i]));
			parameters.add(var);
		}
		return parameters;
	}

	private static Type createTypeNode(ImportsStructure structure, ITypeBinding binding, AST ast) {
		if (structure != null)
			return structure.addImport(binding, ast);
		return createTypeNode(binding, ast);
	}

	private static Type createTypeNode(ITypeBinding binding, AST ast) {
		if (binding.isPrimitive())
			return ast.newPrimitiveType(PrimitiveType.toCode(binding.getName()));
		ITypeBinding normalized= Bindings.normalizeTypeBinding(binding);
		if (normalized == null)
			return ast.newSimpleType(ast.newSimpleName("invalid")); //$NON-NLS-1$
		else if (normalized.isTypeVariable())
			return ast.newSimpleType(ast.newSimpleName(binding.getName()));
		else if (normalized.isWildcardType()) {
			WildcardType type= ast.newWildcardType();
			ITypeBinding bound= normalized.getBound();
			if (bound != null)
				type.setBound(createTypeNode(bound, ast), normalized.isUpperbound());
			return type;
		} else if (normalized.isArray())
			return ast.newArrayType(createTypeNode(normalized.getElementType(), ast), normalized.getDimensions());
		String qualified= Bindings.getRawQualifiedName(normalized);
		if (qualified.length() > 0) {
			ITypeBinding[] typeArguments= normalized.getTypeArguments();
			if (typeArguments.length > 0) {
				ParameterizedType type= ast.newParameterizedType(ast.newSimpleType(ASTNodeFactory.newName(ast, qualified)));
				List arguments= type.typeArguments();
				for (int index= 0; index < typeArguments.length; index++)
					arguments.add(createTypeNode(typeArguments[index], ast));
				return type;
			}
			return ast.newSimpleType(ASTNodeFactory.newName(ast, qualified));
		}
		return ast.newSimpleType(ASTNodeFactory.newName(ast, Bindings.getRawName(normalized)));
	}

	private static IMethodBinding findMethodBinding(IMethodBinding method, List allMethods) {
		for (int i= 0; i < allMethods.size(); i++) {
			IMethodBinding curr= (IMethodBinding) allMethods.get(i);
			if (Bindings.isEqualMethod(method, curr.getName(), curr.getParameterTypes())) {
				return curr;
			}
		}
		return null;
	}

	private static IMethodBinding findOverridingMethod(IMethodBinding method, List allMethods) {
		for (int i= 0; i < allMethods.size(); i++) {
			IMethodBinding curr= (IMethodBinding) allMethods.get(i);
			if (Bindings.areOverriddenMethods(curr, method) || Bindings.isEqualMethod(curr, method.getName(), method.getParameterTypes()))
				return curr;
		}
		return null;
	}

	private static void findUnimplementedInterfaceMethods(ITypeBinding typeBinding, HashSet visited, ArrayList allMethods, IPackageBinding currPack, ArrayList toImplement) {
		if (visited.add(typeBinding)) {
			IMethodBinding[] typeMethods= typeBinding.getDeclaredMethods();
			for (int i= 0; i < typeMethods.length; i++) {
				IMethodBinding curr= typeMethods[i];
				IMethodBinding impl= findMethodBinding(curr, allMethods);
				if (impl == null || !Bindings.isVisibleInHierarchy(impl, currPack) || ((curr.getExceptionTypes().length < impl.getExceptionTypes().length) && !Modifier.isFinal(impl.getModifiers()))) {
					if (impl != null)
						allMethods.remove(impl);
					toImplement.add(curr);
					allMethods.add(curr);
				}
			}
			ITypeBinding[] superInterfaces= typeBinding.getInterfaces();
			for (int i= 0; i < superInterfaces.length; i++)
				findUnimplementedInterfaceMethods(superInterfaces[i], visited, allMethods, currPack, toImplement);
		}
	}

	public static IBinding[][] getDelegatableMethods(AST ast, ITypeBinding binding) {
		final List tuples= new ArrayList();
		final List declared= new ArrayList();
		IMethodBinding[] typeMethods= binding.getDeclaredMethods();
		for (int index= 0; index < typeMethods.length; index++)
			declared.add(typeMethods[index]);
		IVariableBinding[] typeFields= binding.getDeclaredFields();
		for (int index= 0; index < typeFields.length; index++) {
			IVariableBinding fieldBinding= typeFields[index];
			if (fieldBinding.isField() && !fieldBinding.isEnumConstant() && !fieldBinding.isSynthetic())
				getDelegatableMethods(ast, tuples, new ArrayList(declared), fieldBinding, fieldBinding.getType(), binding);
		}
		// list of tuple<IVariableBinding, IMethodBinding>
		return (IBinding[][]) tuples.toArray(new IBinding[tuples.size()][2]);
	}

	private static void getDelegatableMethods(AST ast, List tuples, List methods, IVariableBinding fieldBinding, ITypeBinding typeBinding, ITypeBinding binding) {
		boolean match= false;
		if (typeBinding.isTypeVariable()) {
			ITypeBinding[] typeBounds= typeBinding.getTypeBounds();
			if (typeBounds == null || typeBounds.length == 0)
				typeBounds= new ITypeBinding[] { ast.resolveWellKnownType("java.lang.Object")}; //$NON-NLS-1$
			for (int index= 0; index < typeBounds.length; index++) {
				IMethodBinding[] candidates= getDelegateCandidates(typeBounds[index], binding);
				for (int candidate= 0; candidate < candidates.length; candidate++) {
					match= false;
					final IMethodBinding methodBinding= candidates[candidate];
					for (int offset= 0; offset < methods.size() && !match; offset++) {
						if (Bindings.areOverriddenMethods((IMethodBinding) methods.get(offset), methodBinding))
							match= true;
					}
					if (!match) {
						tuples.add(new IBinding[] { fieldBinding, methodBinding});
						methods.add(methodBinding);
					}
				}
				final ITypeBinding superclass= typeBounds[index].getSuperclass();
				if (superclass != null)
					getDelegatableMethods(ast, tuples, methods, fieldBinding, superclass, binding);
				ITypeBinding[] superInterfaces= typeBounds[index].getInterfaces();
				for (int offset= 0; offset < superInterfaces.length; offset++)
					getDelegatableMethods(ast, tuples, methods, fieldBinding, superInterfaces[offset], binding);
			}
		} else {
			IMethodBinding[] candidates= getDelegateCandidates(typeBinding, binding);
			for (int index= 0; index < candidates.length; index++) {
				match= false;
				final IMethodBinding methodBinding= candidates[index];
				for (int offset= 0; offset < methods.size() && !match; offset++) {
					if (Bindings.areOverriddenMethods((IMethodBinding) methods.get(offset), methodBinding))
						match= true;
				}
				if (!match) {
					tuples.add(new IBinding[] { fieldBinding, methodBinding});
					methods.add(methodBinding);
				}
			}
			final ITypeBinding superclass= typeBinding.getSuperclass();
			if (superclass != null)
				getDelegatableMethods(ast, tuples, methods, fieldBinding, superclass, binding);
			ITypeBinding[] superInterfaces= typeBinding.getInterfaces();
			for (int offset= 0; offset < superInterfaces.length; offset++)
				getDelegatableMethods(ast, tuples, methods, fieldBinding, superInterfaces[offset], binding);
		}
	}

	private static IMethodBinding[] getDelegateCandidates(ITypeBinding binding, ITypeBinding hierarchy) {
		List allMethods= new ArrayList();
		boolean isInterface= binding.isInterface();
		IMethodBinding[] typeMethods= binding.getDeclaredMethods();
		for (int index= 0; index < typeMethods.length; index++) {
			final int modifiers= typeMethods[index].getModifiers();
			if (!typeMethods[index].isConstructor() && !Modifier.isStatic(modifiers) && !Modifier.isFinal((modifiers)) && (isInterface || Modifier.isPublic(modifiers))) {
				ITypeBinding[] parameterBindings= typeMethods[index].getParameterTypes();
				boolean upper= false;
				for (int offset= 0; offset < parameterBindings.length; offset++) {
					if (parameterBindings[offset].isWildcardType() && parameterBindings[offset].isUpperbound())
						upper= true;
				}
				if (!upper)
					allMethods.add(typeMethods[index]);
			}
		}
		return (IMethodBinding[]) allMethods.toArray(new IMethodBinding[allMethods.size()]);
	}

	private static List getImplementationModifiers(AST ast, IMethodBinding method, boolean deferred) {
		int modifiers= method.getModifiers() & ~Modifier.ABSTRACT & ~Modifier.NATIVE & ~Modifier.PRIVATE;
		if (deferred) {
			modifiers= modifiers & ~Modifier.PROTECTED;
			modifiers= modifiers | Modifier.PUBLIC;
		}
		return ASTNodeFactory.newModifiers(ast, modifiers);
	}

	private static String getMethodComment(ICompilationUnit cu, String typeName, MethodDeclaration decl, IMethodBinding overridden, String lineDelimiter) throws CoreException {
		if (overridden != null) {
			overridden= overridden.getMethodDeclaration();
			String declaringClassQualifiedName= overridden.getDeclaringClass().getQualifiedName();
			String[] parameterTypesQualifiedNames= getParameterTypesQualifiedNames(overridden);
			return StubUtility.getMethodComment(cu, typeName, decl, true, overridden.isDeprecated(), declaringClassQualifiedName, parameterTypesQualifiedNames, lineDelimiter);
		} else {
			return StubUtility.getMethodComment(cu, typeName, decl, false, false, null, null, lineDelimiter);
		}
	}

	public static IMethodBinding[] getOverridableMethods(AST ast, ITypeBinding typeBinding, boolean isSubType) {
		List allMethods= new ArrayList();
		IMethodBinding[] typeMethods= typeBinding.getDeclaredMethods();
		for (int index= 0; index < typeMethods.length; index++) {
			final int modifiers= typeMethods[index].getModifiers();
			if (!typeMethods[index].isConstructor() && !Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers))
				allMethods.add(typeMethods[index]);
		}
		ITypeBinding clazz= typeBinding.getSuperclass();
		while (clazz != null) {
			IMethodBinding[] methods= clazz.getDeclaredMethods();
			for (int offset= 0; offset < methods.length; offset++) {
				final int modifiers= methods[offset].getModifiers();
				if (!methods[offset].isConstructor() && !Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers)) {
					if (findOverridingMethod(methods[offset], allMethods) == null)
						allMethods.add(methods[offset]);
				}
			}
			clazz= clazz.getSuperclass();
		}
		clazz= typeBinding;
		while (clazz != null) {
			ITypeBinding[] superInterfaces= clazz.getInterfaces();
			for (int index= 0; index < superInterfaces.length; index++) {
				getOverridableMethods(ast, superInterfaces[index], allMethods);
			}
			clazz= clazz.getSuperclass();
		}
		if (typeBinding.isInterface())
			getOverridableMethods(ast, ast.resolveWellKnownType("java.lang.Object"), allMethods); //$NON-NLS-1$
		if (!isSubType)
			allMethods.removeAll(Arrays.asList(typeMethods));
		int modifiers= 0;
		if (!typeBinding.isInterface()) {
			for (int index= allMethods.size() - 1; index >= 0; index--) {
				IMethodBinding method= (IMethodBinding) allMethods.get(index);
				modifiers= method.getModifiers();
				if (Modifier.isFinal(modifiers))
					allMethods.remove(index);
			}
		}
		return (IMethodBinding[]) allMethods.toArray(new IMethodBinding[allMethods.size()]);
	}

	private static void getOverridableMethods(AST ast, ITypeBinding superBinding, List allMethods) {
		IMethodBinding[] methods= superBinding.getDeclaredMethods();
		for (int offset= 0; offset < methods.length; offset++) {
			final int modifiers= methods[offset].getModifiers();
			if (!methods[offset].isConstructor() && !Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers)) {
				if (findOverridingMethod(methods[offset], allMethods) == null && !Modifier.isStatic(modifiers))
					allMethods.add(methods[offset]);
			}
		}
		ITypeBinding[] superInterfaces= superBinding.getInterfaces();
		for (int index= 0; index < superInterfaces.length; index++) {
			getOverridableMethods(ast, superInterfaces[index], allMethods);
		}
	}

	private static String getParameterName(ICompilationUnit unit, IVariableBinding binding, String[] excluded) {
		final String name= NamingConventions.removePrefixAndSuffixForFieldName(unit.getJavaProject(), binding.getName(), binding.getModifiers());
		return NamingConventions.suggestArgumentNames(unit.getJavaProject(), "", name, 0, excluded)[0]; //$NON-NLS-1$
	}

	private static String[] getParameterTypesQualifiedNames(IMethodBinding binding) {
		ITypeBinding[] typeBindings= binding.getParameterTypes();
		String[] result= new String[typeBindings.length];
		for (int i= 0; i < result.length; i++) {
			if (typeBindings[i].isTypeVariable())
				result[i]= typeBindings[i].getName();
			else {
				if (binding.isVarargs() && typeBindings[i].isArray() && i == typeBindings.length - 1) {
					StringBuffer buffer= new StringBuffer(typeBindings[i].getElementType().getQualifiedName());
					for (int dim= 1; dim < typeBindings[i].getDimensions(); dim++)
						buffer.append("[]"); //$NON-NLS-1$
					buffer.append("..."); //$NON-NLS-1$
					result[i]= buffer.toString();
				} else
					result[i]= typeBindings[i].getTypeDeclaration().getQualifiedName();
			}
		}
		return result;
	}

	public static IMethodBinding[] getUnimplementedMethods(ITypeBinding typeBinding) {
		ArrayList allMethods= new ArrayList();
		ArrayList toImplement= new ArrayList();

		IMethodBinding[] typeMethods= typeBinding.getDeclaredMethods();
		for (int i= 0; i < typeMethods.length; i++) {
			IMethodBinding curr= typeMethods[i];
			int modifiers= curr.getModifiers();
			if (!curr.isConstructor() && !Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers)) {
				allMethods.add(curr);
			}
		}

		ITypeBinding superClass= typeBinding.getSuperclass();
		while (superClass != null) {
			typeMethods= superClass.getDeclaredMethods();
			for (int i= 0; i < typeMethods.length; i++) {
				IMethodBinding curr= typeMethods[i];
				int modifiers= curr.getModifiers();
				if (!curr.isConstructor() && !Modifier.isStatic(modifiers) && !Modifier.isPrivate(modifiers)) {
					if (findMethodBinding(curr, allMethods) == null) {
						allMethods.add(curr);
					}
				}
			}
			superClass= superClass.getSuperclass();
		}

		for (int i= 0; i < allMethods.size(); i++) {
			IMethodBinding curr= (IMethodBinding) allMethods.get(i);
			int modifiers= curr.getModifiers();
			if ((Modifier.isAbstract(modifiers) || curr.getDeclaringClass().isInterface()) && (typeBinding != curr.getDeclaringClass())) {
				// implement all abstract methods
				toImplement.add(curr);
			}
		}

		HashSet visited= new HashSet();
		ITypeBinding curr= typeBinding;
		while (curr != null) {
			ITypeBinding[] superInterfaces= curr.getInterfaces();
			for (int i= 0; i < superInterfaces.length; i++) {
				findUnimplementedInterfaceMethods(superInterfaces[i], visited, allMethods, typeBinding.getPackage(), toImplement);
			}
			curr= curr.getSuperclass();
		}

		return (IMethodBinding[]) toImplement.toArray(new IMethodBinding[toImplement.size()]);
	}

	public static IMethodBinding[] getVisibleConstructors(ITypeBinding binding, boolean accountExisting, boolean proposeDefault) {
		List constructorMethods= new ArrayList();
		List existingConstructors= null;
		ITypeBinding superType= binding.getSuperclass();
		if (superType == null)
			return new IMethodBinding[0];
		if (accountExisting) {
			IMethodBinding[] methods= binding.getDeclaredMethods();
			existingConstructors= new ArrayList(methods.length);
			for (int index= 0; index < methods.length; index++) {
				IMethodBinding method= methods[index];
				if (method.isConstructor() && !method.isDefaultConstructor())
					existingConstructors.add(method);
			}
		}
		if (existingConstructors != null)
			constructorMethods.addAll(existingConstructors);
		IMethodBinding[] methods= binding.getDeclaredMethods();
		IMethodBinding[] superMethods= superType.getDeclaredMethods();
		for (int index= 0; index < superMethods.length; index++) {
			IMethodBinding method= superMethods[index];
			if (method.isConstructor()) {
				if (Bindings.isVisibleInHierarchy(method, binding.getPackage()) && (!accountExisting || !Bindings.containsSignatureEquivalentConstructor(methods, method)))
					constructorMethods.add(method);
			}
		}
		if (existingConstructors != null)
			constructorMethods.removeAll(existingConstructors);
		if (constructorMethods.isEmpty()) {
			superType= binding;
			while (superType.getSuperclass() != null)
				superType= superType.getSuperclass();
			IMethodBinding method= Bindings.findMethodInType(superType, "Object", new ITypeBinding[0]); //$NON-NLS-1$
			if ((proposeDefault || (!accountExisting || existingConstructors.isEmpty())) && (!accountExisting || !Bindings.containsSignatureEquivalentConstructor(methods, method)))
				constructorMethods.add(method);
		}
		return (IMethodBinding[]) constructorMethods.toArray(new IMethodBinding[constructorMethods.size()]);
	}

	private static String[] suggestArgumentNames(IJavaProject project, IMethodBinding binding) {
		int nParams= binding.getParameterTypes().length;
		if (nParams > 0) {
			try {
				IMethod method= (IMethod) binding.getMethodDeclaration().getJavaElement();
				if (method != null) {
					return StubUtility.suggestArgumentNames(project, method.getParameterNames());
				}
			} catch (JavaModelException e) {
				JavaPlugin.log(e);
			}
		}
		String[] names= new String[nParams];
		for (int i= 0; i < names.length; i++) {
			names[i]= "arg" + i; //$NON-NLS-1$
		}
		return names;
	}

	/**
	 * Creates a new stub utility.
	 */
	private StubUtility2() {
		// Not for instantiation
	}
}
