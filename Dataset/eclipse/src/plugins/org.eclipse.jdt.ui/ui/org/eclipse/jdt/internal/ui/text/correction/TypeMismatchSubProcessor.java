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
package org.eclipse.jdt.internal.ui.text.correction;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.swt.graphics.Image;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.SuperFieldAccess;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.PrimitiveType.Code;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import org.eclipse.jdt.internal.corext.codemanipulation.ImportRewrite;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;

import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.text.correction.ChangeMethodSignatureProposal.ChangeDescription;
import org.eclipse.jdt.internal.ui.text.correction.ChangeMethodSignatureProposal.InsertDescription;
import org.eclipse.jdt.internal.ui.text.correction.ChangeMethodSignatureProposal.RemoveDescription;


public class TypeMismatchSubProcessor {

	private TypeMismatchSubProcessor() {
	}

	public static void addTypeMismatchProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) throws CoreException {
		String[] args= problem.getProblemArguments();
		if (args.length != 2) {
			return;
		}

		ICompilationUnit cu= context.getCompilationUnit();
		String castTypeName= args[1];

		CompilationUnit astRoot= context.getASTRoot();
		AST ast= astRoot.getAST();
		
		ASTNode selectedNode= problem.getCoveredNode(astRoot);
		if (!(selectedNode instanceof Expression)) {
			return;
		}
		Expression nodeToCast= (Expression) selectedNode;
		Name receiverNode= null;
		ITypeBinding castTypeBinding= null;

		int parentNodeType= selectedNode.getParent().getNodeType();
		if (parentNodeType == ASTNode.ASSIGNMENT) {
			Assignment assign= (Assignment) selectedNode.getParent();
			Expression leftHandSide= assign.getLeftHandSide();
			if (selectedNode.equals(leftHandSide)) {
				nodeToCast= assign.getRightHandSide();
			}
			castTypeBinding= assign.getLeftHandSide().resolveTypeBinding();
			if (leftHandSide instanceof Name) {
				receiverNode= (Name) leftHandSide;
			} else if (leftHandSide instanceof FieldAccess) {
				receiverNode= ((FieldAccess) leftHandSide).getName();
			}
		} else if (parentNodeType == ASTNode.VARIABLE_DECLARATION_FRAGMENT) {
			VariableDeclarationFragment frag= (VariableDeclarationFragment) selectedNode.getParent();
			if (selectedNode.equals(frag.getName())) {
				nodeToCast= frag.getInitializer();
				IVariableBinding varBinding= frag.resolveBinding();
				if (varBinding != null) {
					castTypeBinding= varBinding.getType();
				}
				receiverNode= frag.getName();
			}
		} else {
			// try to find the binding corresponding to 'castTypeName'
			ITypeBinding guessedCastTypeBinding= ASTResolving.guessBindingForReference(nodeToCast);
			if (guessedCastTypeBinding != null && castTypeName.equals(guessedCastTypeBinding.getQualifiedName())) {
				castTypeBinding= guessedCastTypeBinding;
			}
		}

		ITypeBinding binding= nodeToCast.resolveTypeBinding();
		if (binding == null || canCast(castTypeName, castTypeBinding, binding)) {
			proposals.add(createCastProposal(context, castTypeName, castTypeBinding, nodeToCast, 7));
		}

		ITypeBinding currBinding= nodeToCast.resolveTypeBinding();

		boolean nullOrVoid= currBinding == null || "void".equals(currBinding.getName()); //$NON-NLS-1$

		// change method return statement to actual type
		if (!nullOrVoid && parentNodeType == ASTNode.RETURN_STATEMENT) {
			BodyDeclaration decl= ASTResolving.findParentBodyDeclaration(selectedNode);
			if (decl instanceof MethodDeclaration) {
				MethodDeclaration methodDeclaration= (MethodDeclaration) decl;


				currBinding= Bindings.normalizeTypeBinding(currBinding);
				if (currBinding == null) {
					currBinding= ast.resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
				}
				if (currBinding.isWildcardType()) {
					currBinding= ASTResolving.normalizeWildcardType(currBinding, true, ast);
				}

				ASTRewrite rewrite= ASTRewrite.create(ast);
				ImportRewrite imports= new ImportRewrite(cu);

				Type newReturnType= imports.addImport(currBinding, ast);
				rewrite.replace(methodDeclaration.getReturnType2(), newReturnType, null);

				String label= Messages.format(CorrectionMessages.TypeMismatchSubProcessor_changereturntype_description, currBinding.getName());
				Image image= JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE);
				LinkedCorrectionProposal proposal= new LinkedCorrectionProposal(label, cu, rewrite, 6, image);
				proposal.setImportRewrite(imports);

				String returnKey= "return"; //$NON-NLS-1$
				proposal.addLinkedPosition(rewrite.track(newReturnType), true, returnKey);
				ITypeBinding[] typeSuggestions= ASTResolving.getRelaxingTypes(ast, currBinding);
				for (int i= 0; i < typeSuggestions.length; i++) {
					proposal.addLinkedPositionProposal(returnKey, typeSuggestions[i]);
				}
				proposals.add(proposal);
			}
		}

		if (!nullOrVoid && receiverNode != null) {
			currBinding= Bindings.normalizeTypeBinding(currBinding);
			if (currBinding == null) {
				currBinding= ast.resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
			}
			if (currBinding.isWildcardType()) {
				currBinding= ASTResolving.normalizeWildcardType(currBinding, true, ast);
			}
			addChangeSenderTypeProposals(context, receiverNode, currBinding, true, 6, proposals);
		}

		if (castTypeBinding != null) {
			addChangeSenderTypeProposals(context, nodeToCast, castTypeBinding, false, 5, proposals);
		}
	}

	public static void addChangeSenderTypeProposals(IInvocationContext context, Expression nodeToCast, ITypeBinding castTypeBinding, boolean isAssignedNode, int relevance, Collection proposals) throws JavaModelException {
		IBinding callerBinding= null;
		switch (nodeToCast.getNodeType()) {
			case ASTNode.METHOD_INVOCATION:
				callerBinding= ((MethodInvocation) nodeToCast).resolveMethodBinding();
				break;
			case ASTNode.SUPER_METHOD_INVOCATION:
				callerBinding= ((SuperMethodInvocation) nodeToCast).resolveMethodBinding();
				break;
			case ASTNode.FIELD_ACCESS:
				callerBinding= ((FieldAccess) nodeToCast).resolveFieldBinding();
				break;
			case ASTNode.SUPER_FIELD_ACCESS:
				callerBinding= ((SuperFieldAccess) nodeToCast).resolveFieldBinding();
				break;
			case ASTNode.SIMPLE_NAME:
			case ASTNode.QUALIFIED_NAME:
				callerBinding= ((Name) nodeToCast).resolveBinding();
				break;
		}

		ICompilationUnit cu= context.getCompilationUnit();
		CompilationUnit astRoot= context.getASTRoot();

		ICompilationUnit targetCu= null;
		ITypeBinding declaringType= null;
		IBinding callerBindingDecl= callerBinding;
		if (callerBinding instanceof IVariableBinding) {
			IVariableBinding variableBinding= (IVariableBinding) callerBinding;
			if (!variableBinding.isField()) {
				targetCu= cu;
			} else {
				callerBindingDecl= Bindings.getVariableDeclaration(variableBinding);
				declaringType= variableBinding.getDeclaringClass().getTypeDeclaration();
			}
		} else if (callerBinding instanceof IMethodBinding) {
			IMethodBinding methodBinding= (IMethodBinding) callerBinding;
			if (!methodBinding.isConstructor()) {
				declaringType= methodBinding.getDeclaringClass().getTypeDeclaration();
				callerBindingDecl= methodBinding.getMethodDeclaration();
			}
		}

		if (declaringType != null && declaringType.isFromSource()) {
			targetCu= ASTResolving.findCompilationUnitForBinding(cu, astRoot, declaringType);
		}
		if (targetCu != null && ASTResolving.isUseableTypeInContext(castTypeBinding, callerBindingDecl, false)) {
			proposals.add(new TypeChangeCompletionProposal(targetCu, callerBindingDecl, astRoot, castTypeBinding, isAssignedNode, relevance));
		}

		// add interface to resulting type
		if (!isAssignedNode) {
			ITypeBinding nodeType= nodeToCast.resolveTypeBinding();
			if (castTypeBinding.isInterface() && nodeType != null && nodeType.isClass() && !nodeType.isAnonymous() && nodeType.isFromSource()) {
				ITypeBinding typeDecl= nodeType.getTypeDeclaration();
				ICompilationUnit nodeCu= ASTResolving.findCompilationUnitForBinding(cu, astRoot, typeDecl);
				if (nodeCu != null && ASTResolving.isUseableTypeInContext(castTypeBinding, typeDecl, true)) {
					proposals.add(new ImplementInterfaceProposal(nodeCu, typeDecl, astRoot, castTypeBinding, relevance - 1));
				}
			}
		}
	}

	private static boolean canCast(String castTarget, ITypeBinding castTypeBinding, ITypeBinding bindingToCast) {
		if (castTypeBinding != null) {
			return bindingToCast.isCastCompatible(castTypeBinding);
		}

		bindingToCast= Bindings.normalizeTypeBinding(bindingToCast);
		if (bindingToCast == null) {
			return false;
		}

		int arrStart= castTarget.indexOf('[');
		if (arrStart != -1) {
			if (!bindingToCast.isArray()) {
				return "java.lang.Object".equals(bindingToCast.getQualifiedName()); //$NON-NLS-1$
			}
			castTarget= castTarget.substring(0, arrStart);
			bindingToCast= bindingToCast.getElementType();
			if (bindingToCast.isPrimitive() && !castTarget.equals(bindingToCast.getName())) {
				return false; // can't cast arrays of primitive types into each other
			}
		}

		Code targetCode= PrimitiveType.toCode(castTarget);
		if (bindingToCast.isPrimitive()) {
			Code castCode= PrimitiveType.toCode(bindingToCast.getName());
			if (castCode == targetCode) {
				return true;
			}
			return (targetCode != null && targetCode != PrimitiveType.BOOLEAN && castCode != PrimitiveType.BOOLEAN);
		} else {
			return targetCode == null; // can not check
		}
	}

	public static ASTRewriteCorrectionProposal createCastProposal(IInvocationContext context, String castType, ITypeBinding castTypeBinding, Expression nodeToCast, int relevance) {
		ICompilationUnit cu= context.getCompilationUnit();

		String label;
		if (nodeToCast.getNodeType() == ASTNode.CAST_EXPRESSION) {
			label= Messages.format(CorrectionMessages.TypeMismatchSubProcessor_changecast_description, castType);
		} else {
			label= Messages.format(CorrectionMessages.TypeMismatchSubProcessor_addcast_description, castType);
		}
		if (castTypeBinding != null) {
			return new CastCompletionProposal(label, cu, nodeToCast, castTypeBinding, relevance);
		}
		return new CastCompletionProposal(label, cu, nodeToCast, castType, relevance);
	}

	public static void addIncompatibleReturnTypeProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) throws JavaModelException {
		CompilationUnit astRoot= context.getASTRoot();
		ASTNode selectedNode= problem.getCoveringNode(astRoot);
		if (!(selectedNode instanceof MethodDeclaration)) {
			return;
		}
		MethodDeclaration decl= (MethodDeclaration) selectedNode;
		IMethodBinding methodDeclBinding= decl.resolveBinding();
		if (methodDeclBinding == null) {
			return;
		}

		IMethodBinding overridden= Bindings.findMethodDefininition(methodDeclBinding, false);
		if (overridden == null || overridden.getReturnType() == methodDeclBinding.getReturnType()) {
			return;
		}


		ICompilationUnit cu= context.getCompilationUnit();
		IMethodBinding methodDecl= methodDeclBinding.getMethodDeclaration();
		proposals.add(new TypeChangeCompletionProposal(cu, methodDecl, astRoot, overridden.getReturnType(), false, 8));

		ICompilationUnit targetCu= cu;

		IMethodBinding overriddenDecl= overridden.getMethodDeclaration();
		ITypeBinding overridenDeclType= overriddenDecl.getDeclaringClass();

		ITypeBinding returnType= methodDeclBinding.getReturnType();
		if (overridenDeclType.isFromSource()) {
			targetCu= ASTResolving.findCompilationUnitForBinding(cu, astRoot, overridenDeclType);
		}
		if (targetCu != null && ASTResolving.isUseableTypeInContext(returnType, overriddenDecl, false)) {
			TypeChangeCompletionProposal proposal= new TypeChangeCompletionProposal(targetCu, overriddenDecl, astRoot, returnType, false, 7);
			if (overridenDeclType.isInterface()) {
				proposal.setDisplayName(Messages.format(CorrectionMessages.TypeMismatchSubProcessor_changereturnofimplemented_description, overriddenDecl.getName()));
			} else {
				proposal.setDisplayName(Messages.format(CorrectionMessages.TypeMismatchSubProcessor_changereturnofoverridden_description, overriddenDecl.getName()));
			}
			proposals.add(proposal);
		}
	}

	public static void addIncompatibleThrowsProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) throws JavaModelException {
		CompilationUnit astRoot= context.getASTRoot();
		ASTNode selectedNode= problem.getCoveringNode(astRoot);
		if (!(selectedNode instanceof MethodDeclaration)) {
			return;
		}
		MethodDeclaration decl= (MethodDeclaration) selectedNode;
		IMethodBinding methodDeclBinding= decl.resolveBinding();
		if (methodDeclBinding == null) {
			return;
		}

		IMethodBinding overridden= Bindings.findMethodDefininition(methodDeclBinding, false);
		if (overridden == null) {
			return;
		}

		ICompilationUnit cu= context.getCompilationUnit();

		ITypeBinding[] methodExceptions= methodDeclBinding.getExceptionTypes();
		ITypeBinding[] definedExceptions= overridden.getExceptionTypes();

		ArrayList undeclaredExceptions= new ArrayList();
		{
			ChangeDescription[] changes= new ChangeDescription[methodExceptions.length];

			for (int i= 0; i < methodExceptions.length; i++) {
				if (!isDeclaredException(methodExceptions[i], definedExceptions)) {
					changes[i]= new RemoveDescription();
					undeclaredExceptions.add(methodExceptions[i]);
				}
			}
			String label= Messages.format(CorrectionMessages.TypeMismatchSubProcessor_removeexceptions_description, methodDeclBinding.getName());
			Image image= JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_REMOVE);
			proposals.add(new ChangeMethodSignatureProposal(label, cu, astRoot, methodDeclBinding, null, changes, 8, image));
		}

		ITypeBinding declaringType= overridden.getDeclaringClass();
		ICompilationUnit targetCu= cu;
		if (declaringType != null && declaringType.isFromSource()) {
			targetCu= ASTResolving.findCompilationUnitForBinding(cu, astRoot, declaringType);
		}
		if (targetCu != null) {
			ChangeDescription[] changes= new ChangeDescription[definedExceptions.length + undeclaredExceptions.size()];

			for (int i= 0; i < undeclaredExceptions.size(); i++) {
				changes[i + definedExceptions.length]= new InsertDescription((ITypeBinding) undeclaredExceptions.get(i), ""); //$NON-NLS-1$
			}
			IMethodBinding overriddenDecl= overridden.getMethodDeclaration();
			String[] args= {  declaringType.getName(), overridden.getName() };
			String label= Messages.format(CorrectionMessages.TypeMismatchSubProcessor_addexceptions_description, args);
			Image image= JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_ADD);
			proposals.add(new ChangeMethodSignatureProposal(label, targetCu, astRoot, overriddenDecl, null, changes, 7, image));
		}
	}

	private static boolean isDeclaredException(ITypeBinding curr, ITypeBinding[] declared) {
		for (int i= 0; i < declared.length; i++) {
			if (Bindings.isSuperType(declared[i], curr)) {
				return true;
			}
		}
		return false;
	}


}