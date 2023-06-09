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
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.ArrayAccess;
import org.eclipse.jdt.core.dom.ArrayCreation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.ArrayType;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CastExpression;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.IPackageBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.Initializer;
import org.eclipse.jdt.core.dom.InstanceofExpression;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.PrimitiveType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.SuperMethodInvocation;
import org.eclipse.jdt.core.dom.SwitchCase;
import org.eclipse.jdt.core.dom.SwitchStatement;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TryStatement;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeLiteral;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;
import org.eclipse.jdt.core.dom.WildcardType;
import org.eclipse.jdt.core.dom.PrimitiveType.Code;

import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.GenericVisitor;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.dom.TypeBindingVisitor;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.viewsupport.BindingLabelProvider;

public class ASTResolving {

	public static ITypeBinding guessBindingForReference(ASTNode node) {
		return Bindings.normalizeTypeBinding(getPossibleReferenceBinding(node));
	}

	private static ITypeBinding getPossibleReferenceBinding(ASTNode node) {
		ASTNode parent= node.getParent();
		switch (parent.getNodeType()) {
		case ASTNode.ASSIGNMENT:
			Assignment assignment= (Assignment) parent;
			if (node.equals(assignment.getLeftHandSide())) {
				// field write access: xx= expression
				return assignment.getRightHandSide().resolveTypeBinding();
			}
			// read access
			return assignment.getLeftHandSide().resolveTypeBinding();
		case ASTNode.INFIX_EXPRESSION:
			InfixExpression infix= (InfixExpression) parent;
			InfixExpression.Operator op= infix.getOperator();
			if (op == InfixExpression.Operator.CONDITIONAL_AND || op == InfixExpression.Operator.CONDITIONAL_OR) {
				// boolean operation
				return infix.getAST().resolveWellKnownType("boolean"); //$NON-NLS-1$
			} else if (op == InfixExpression.Operator.LEFT_SHIFT || op == InfixExpression.Operator.RIGHT_SHIFT_UNSIGNED || op == InfixExpression.Operator.RIGHT_SHIFT_SIGNED) {
				// assymetric operation
				return infix.getAST().resolveWellKnownType("int"); //$NON-NLS-1$
			}
			if (node.equals(infix.getLeftOperand())) {
				//	xx op expression
				ITypeBinding rigthHandBinding= infix.getRightOperand().resolveTypeBinding();
				if (rigthHandBinding != null) {
					return rigthHandBinding;
				}
			} else {
				// expression op xx
				ITypeBinding leftHandBinding= infix.getLeftOperand().resolveTypeBinding();
				if (leftHandBinding != null) {
					return leftHandBinding;
				}
			}
			if (op != InfixExpression.Operator.EQUALS && op != InfixExpression.Operator.NOT_EQUALS) {
				return infix.getAST().resolveWellKnownType("int"); //$NON-NLS-1$
			}
			break;
		case ASTNode.INSTANCEOF_EXPRESSION:
			InstanceofExpression instanceofExpression= (InstanceofExpression) parent;
			return instanceofExpression.getRightOperand().resolveBinding();
		case ASTNode.VARIABLE_DECLARATION_FRAGMENT:
			VariableDeclarationFragment frag= (VariableDeclarationFragment) parent;
			if (frag.getInitializer().equals(node)) {
				return frag.getName().resolveTypeBinding();
			}
			break;
		case ASTNode.SUPER_METHOD_INVOCATION:
			SuperMethodInvocation superMethodInvocation= (SuperMethodInvocation) parent;
			IMethodBinding superMethodBinding= ASTNodes.getMethodBinding(superMethodInvocation.getName());
			if (superMethodBinding != null) {
				return getParameterTypeBinding(node, superMethodInvocation.arguments(), superMethodBinding);
			}
			break;
		case ASTNode.METHOD_INVOCATION:
			MethodInvocation methodInvocation= (MethodInvocation) parent;
			IMethodBinding methodBinding= methodInvocation.resolveMethodBinding();
			if (methodBinding != null) {
				return getParameterTypeBinding(node, methodInvocation.arguments(), methodBinding);
			}
			break;
		case ASTNode.SUPER_CONSTRUCTOR_INVOCATION: {
			SuperConstructorInvocation superInvocation= (SuperConstructorInvocation) parent;
			IMethodBinding superBinding= superInvocation.resolveConstructorBinding();
			if (superBinding == null) { // jdt.core does not guess contructors with problems in arguments
				ITypeBinding parentType= Bindings.getBindingOfParentType(parent);
				if (parentType != null && parentType.getSuperclass() != null) {
					superBinding= guessContructorBinding(parentType.getSuperclass(), superInvocation.arguments());
				}
			}
			if (superBinding != null) {
				return getParameterTypeBinding(node, superInvocation.arguments(), superBinding);
			}
			break;
		}
		case ASTNode.CONSTRUCTOR_INVOCATION: {
			ConstructorInvocation constrInvocation= (ConstructorInvocation) parent;
			IMethodBinding constrBinding= constrInvocation.resolveConstructorBinding();
			if (constrBinding == null) { // jdt.core does not guess contructors with problems in arguments
				ITypeBinding parentType= Bindings.getBindingOfParentType(parent);
				if (parentType != null) {
					constrBinding= guessContructorBinding(parentType, constrInvocation.arguments());
				}
			}
			if (constrBinding != null) {
				return getParameterTypeBinding(node, constrInvocation.arguments(), constrBinding);
			}
			break;
		}
		case ASTNode.CLASS_INSTANCE_CREATION: {
			ClassInstanceCreation creation= (ClassInstanceCreation) parent;
			IMethodBinding creationBinding= creation.resolveConstructorBinding();
			if (creationBinding == null) { // jdt.core does not guess contructors with problems in arguments
				ITypeBinding type= creation.getType().resolveBinding();
				if (type != null) {
					creationBinding= guessContructorBinding(type, creation.arguments());
				}
			}
			if (creationBinding != null) {
				return getParameterTypeBinding(node, creation.arguments(), creationBinding);
			}
			break;
		}
		case ASTNode.PARENTHESIZED_EXPRESSION:
			return guessBindingForReference(parent);
		case ASTNode.ARRAY_ACCESS:
			if (((ArrayAccess) parent).getIndex().equals(node)) {
				return parent.getAST().resolveWellKnownType("int"); //$NON-NLS-1$
			} else {
				return getPossibleReferenceBinding(parent);
			}
		case ASTNode.ARRAY_CREATION:
			if (((ArrayCreation) parent).dimensions().contains(node)) {
				return parent.getAST().resolveWellKnownType("int"); //$NON-NLS-1$
			}
			break;
		case ASTNode.ARRAY_INITIALIZER:
			ASTNode initializerParent= parent.getParent();
			int dim= 1;
			while (initializerParent instanceof ArrayInitializer) {
				initializerParent= initializerParent.getParent();
				dim++;
			}
			Type creationType= null;
			if (initializerParent instanceof ArrayCreation) {
				creationType= ((ArrayCreation) initializerParent).getType();
			} else if (initializerParent instanceof VariableDeclaration) {
				VariableDeclaration varDecl= (VariableDeclaration) initializerParent;
				creationType= ASTNodes.getType(varDecl);
				dim-= ASTNodes.getExtraDimensions(varDecl);
			}
			if (creationType != null) {
				while ((creationType instanceof ArrayType) && dim > 0) {
					creationType= ((ArrayType) creationType).getComponentType();
					dim--;
				}
				return creationType.resolveBinding();
			}
			break;
		case ASTNode.CONDITIONAL_EXPRESSION:
			ConditionalExpression expression= (ConditionalExpression) parent;
			if (node.equals(expression.getExpression())) {
				return parent.getAST().resolveWellKnownType("boolean"); //$NON-NLS-1$
			}
			if (node.equals(expression.getElseExpression())) {
				return expression.getThenExpression().resolveTypeBinding();
			}
			return expression.getElseExpression().resolveTypeBinding();
		case ASTNode.POSTFIX_EXPRESSION:
			return parent.getAST().resolveWellKnownType("int"); //$NON-NLS-1$
		case ASTNode.PREFIX_EXPRESSION:
			if (((PrefixExpression) parent).getOperator() == PrefixExpression.Operator.NOT) {
				return parent.getAST().resolveWellKnownType("boolean"); //$NON-NLS-1$
			}
			return parent.getAST().resolveWellKnownType("int"); //$NON-NLS-1$
		case ASTNode.IF_STATEMENT:
		case ASTNode.WHILE_STATEMENT:
		case ASTNode.DO_STATEMENT:
			if (node instanceof Expression) {
				return parent.getAST().resolveWellKnownType("boolean"); //$NON-NLS-1$
			}
			break;
		case ASTNode.SWITCH_STATEMENT:
			if (((SwitchStatement) parent).getExpression().equals(node)) {
				return parent.getAST().resolveWellKnownType("int"); //$NON-NLS-1$
			}
			break;
		case ASTNode.RETURN_STATEMENT:
			MethodDeclaration decl= ASTResolving.findParentMethodDeclaration(parent);
			if (decl != null && !decl.isConstructor()) {
				return decl.getReturnType2().resolveBinding();
			}
			break;
		case ASTNode.CAST_EXPRESSION:
			return ((CastExpression) parent).getType().resolveBinding();
		case ASTNode.THROW_STATEMENT:
		case ASTNode.CATCH_CLAUSE:
            return parent.getAST().resolveWellKnownType("java.lang.Exception"); //$NON-NLS-1$
		case ASTNode.FIELD_ACCESS:
			if (node.equals(((FieldAccess) parent).getName())) {
				return getPossibleReferenceBinding(parent);
			}
			break;
		case ASTNode.SUPER_FIELD_ACCESS:
			return getPossibleReferenceBinding(parent);
		case ASTNode.QUALIFIED_NAME:
			if (node.equals(((QualifiedName) parent).getName())) {
				return getPossibleReferenceBinding(parent);
			}
			break;
		case ASTNode.SWITCH_CASE:
			if (node.equals(((SwitchCase) parent).getExpression()) && parent.getParent() instanceof SwitchStatement) {
				return ((SwitchStatement) parent.getParent()).getExpression().resolveTypeBinding();
			}
			break;

		default:
			// do nothing
		}

		return null;
	}

	private static IMethodBinding guessContructorBinding(ITypeBinding superclass, List arguments) {
		IMethodBinding[] declaredMethods= superclass.getDeclaredMethods();
		for (int i= 0; i < declaredMethods.length; i++) {
			IMethodBinding curr= declaredMethods[i];
			if (curr.isConstructor() && curr.getParameterTypes().length == arguments.size()) {
				return curr;
			}
		}
		return null;
	}

	public static Type guessTypeForReference(AST ast, ASTNode node) {
		ASTNode parent= node.getParent();
		while (parent != null) {
			switch (parent.getNodeType()) {
				case ASTNode.VARIABLE_DECLARATION_FRAGMENT:
					if (((VariableDeclarationFragment) parent).getInitializer() == node) {
						return ASTNodeFactory.newType(ast, (VariableDeclaration) parent);
					}
					return null;
				case ASTNode.SINGLE_VARIABLE_DECLARATION:
					if (((VariableDeclarationFragment) parent).getInitializer() == node) {
						return ASTNodeFactory.newType(ast, (VariableDeclaration) parent);
					}
					return null;
				case ASTNode.ARRAY_ACCESS:
					if (!((ArrayAccess) parent).getIndex().equals(node)) {
						Type type= guessTypeForReference(ast, parent);
						if (type != null) {
							return ast.newArrayType(type);
						}
					}
					return null;
				case ASTNode.FIELD_ACCESS:
					if (node.equals(((FieldAccess) parent).getName())) {
						node= parent;
						parent= parent.getParent();
					} else {
						return null;
					}
					break;
				case ASTNode.SUPER_FIELD_ACCESS:
				case ASTNode.PARENTHESIZED_EXPRESSION:
					node= parent;
					parent= parent.getParent();
					break;
				case ASTNode.QUALIFIED_NAME:
					if (node.equals(((QualifiedName) parent).getName())) {
						node= parent;
						parent= parent.getParent();
					} else {
						return null;
					}
					break;
				default:
					return null;
			}
		}
		return null;
	}


	private static ITypeBinding getParameterTypeBinding(ASTNode node, List args, IMethodBinding binding) {
		ITypeBinding[] paramTypes= binding.getParameterTypes();
		int index= args.indexOf(node);
		if (index >= 0 && index < paramTypes.length) {
			return paramTypes[index];
		}
		return null;
	}

    public static ITypeBinding guessBindingForTypeReference(ASTNode node) {
    	ITypeBinding binding= Bindings.normalizeTypeBinding(getPossibleTypeBinding(node));
    	if (binding != null) {
    		if (binding.isWildcardType()) {
    			return normalizeWildcardType(binding, true, node.getAST());
    		}
    	}
    	return binding;
    }

    private static ITypeBinding getPossibleTypeBinding(ASTNode node) {
    	ASTNode parent= node.getParent();
    	while (parent instanceof Type) {
    		parent= parent.getParent();
    	}
    	switch (parent.getNodeType()) {
    	case ASTNode.VARIABLE_DECLARATION_STATEMENT:
    		return guessVariableType(((VariableDeclarationStatement) parent).fragments());
		case ASTNode.FIELD_DECLARATION:
			return guessVariableType(((FieldDeclaration) parent).fragments());
		case ASTNode.VARIABLE_DECLARATION_EXPRESSION:
			return guessVariableType(((VariableDeclarationExpression) parent).fragments());
		case ASTNode.SINGLE_VARIABLE_DECLARATION:
			SingleVariableDeclaration varDecl= (SingleVariableDeclaration) parent;
			if (varDecl.getInitializer() != null) {
				return varDecl.getInitializer().resolveTypeBinding();
			}
			break;
		case ASTNode.ARRAY_CREATION:
			ArrayCreation creation= (ArrayCreation) parent;
			if (creation.getInitializer() != null) {
				return creation.getInitializer().resolveTypeBinding();
			}
			return getPossibleReferenceBinding(parent);
        case ASTNode.TYPE_LITERAL:
        	return ((TypeLiteral) parent).getType().resolveBinding();
        case ASTNode.CLASS_INSTANCE_CREATION:
        	return getPossibleReferenceBinding(parent);
        case ASTNode.TAG_ELEMENT:
        	TagElement tagElement= (TagElement) parent;
        	if ("@throws".equals(tagElement.getTagName()) || "@exception".equals(tagElement.getTagName())) {  //$NON-NLS-1$//$NON-NLS-2$
        		ASTNode methNode= tagElement.getParent().getParent();
        		if (methNode instanceof MethodDeclaration) {
        			List thrownExcpetions= ((MethodDeclaration) methNode).thrownExceptions();
        			if (thrownExcpetions.size() == 1) {
        				return ((Name) thrownExcpetions.get(0)).resolveTypeBinding();
        			}
        		}
        	}
        	break;
     	}
    	return null;
    }

   	private static ITypeBinding guessVariableType(List fragments) {
		for (Iterator iter= fragments.iterator(); iter.hasNext();) {
			VariableDeclarationFragment frag= (VariableDeclarationFragment) iter.next();
			if (frag.getInitializer() != null) {
				return frag.getInitializer().resolveTypeBinding();
			}
		}
		return null;
	}

   	/**
   	 *@return  Returns all types known in the AST that have a method with a given name
   	 */
	public static ITypeBinding[] getQualifierGuess(ASTNode searchRoot, final String selector, List arguments, final IBinding context) {
		final int nArgs= arguments.size();
		final ArrayList result= new ArrayList();
		
		// test if selector is a object method
		ITypeBinding binding= searchRoot.getAST().resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
		IMethodBinding[] objectMethods= binding.getDeclaredMethods();
		for (int i= 0; i < objectMethods.length; i++) {
			IMethodBinding meth= objectMethods[i];
			if (meth.getName().equals(selector) && meth.getParameterTypes().length == nArgs) {
				return new ITypeBinding[] { binding };
			}
		}

		visitAllBindings(searchRoot, new TypeBindingVisitor() {
			private HashSet fVisitedBindings= new HashSet(100);

			public boolean visit(ITypeBinding node) {
				node= Bindings.normalizeTypeBinding(node);
				if (node == null) {
					return true;
				}
				
				if (!fVisitedBindings.add(node.getKey())) {
					return true;
				}
				if (node.isGenericType()) {
					return true; // only look at  parametrized types
				}
				if (context != null && !isUseableTypeInContext(node, context, false)) {
					return true;
				}
				
				IMethodBinding[] methods= node.getDeclaredMethods();
				for (int i= 0; i < methods.length; i++) {
					IMethodBinding meth= methods[i];
					if (meth.getName().equals(selector) && meth.getParameterTypes().length == nArgs) {
						result.add(node);
					}
				}
				return true;
			}
		});
		return (ITypeBinding[]) result.toArray(new ITypeBinding[result.size()]);
	}
	
	public static void visitAllBindings(ASTNode astRoot, TypeBindingVisitor visitor) {
		try {
			astRoot.accept(new AllBindingsVisitor(visitor));
		} catch (AllBindingsVisitor.VisitCancelledException e) {
		}
	}
	
	private static class AllBindingsVisitor extends GenericVisitor {
		private final TypeBindingVisitor fVisitor;
		
		private static class VisitCancelledException extends RuntimeException {
			private static final long serialVersionUID= 1L;
		}
		public AllBindingsVisitor(TypeBindingVisitor visitor) {
			super(true);
			fVisitor= visitor;
		}
		public boolean visit(SimpleName node) {
			ITypeBinding binding= node.resolveTypeBinding();
			if (binding != null) {
				boolean res= fVisitor.visit(binding);
				if (res) {
					res= Bindings.visitHierarchy(binding, fVisitor);
				}
				if (!res) {
					throw new VisitCancelledException();
				}
			}
			return false;
		}
	}


	public static IBinding getParentMethodOrTypeBinding(ASTNode node) {
		do {
			if (node instanceof MethodDeclaration) {
				return ((MethodDeclaration) node).resolveBinding();
			} else if (node instanceof AbstractTypeDeclaration) {
				return ((AbstractTypeDeclaration) node).resolveBinding();
			} else if (node instanceof AnonymousClassDeclaration) {
				return ((AnonymousClassDeclaration) node).resolveBinding();
			}
			node= node.getParent();
		} while (node != null);
		
		return null;
	}
	
	public static BodyDeclaration findParentBodyDeclaration(ASTNode node) {
		while ((node != null) && (!(node instanceof BodyDeclaration))) {
			node= node.getParent();
		}
		return (BodyDeclaration) node;
	}
	

	public static CompilationUnit findParentCompilationUnit(ASTNode node) {
		return (CompilationUnit) findAncestor(node, ASTNode.COMPILATION_UNIT);
	}

	/**
	 * Returns either a AbstractTypeDeclaration or an AnonymousTypeDeclaration
	 * @param node
	 * @return CompilationUnit
	 */
	public static ASTNode findParentType(ASTNode node) {
		while ((node != null) && !(node instanceof AbstractTypeDeclaration) && !(node instanceof AnonymousClassDeclaration)) {
			node= node.getParent();
		}
		return node;
	}

	/**
	 * Returns the method binding of the node's parent method declaration or <code>null</code> if the node
	 * is not inside a metho
	 * @param node
	 * @return CompilationUnit
	 */
	public static MethodDeclaration findParentMethodDeclaration(ASTNode node) {
		while (node != null) {
			if (node.getNodeType() == ASTNode.METHOD_DECLARATION) {
				return (MethodDeclaration) node;
			}
			if (node instanceof AbstractTypeDeclaration || node instanceof AnonymousClassDeclaration) {
				return null;
			}
			node= node.getParent();
		}
		return null;
	}

	public static ASTNode findAncestor(ASTNode node, int nodeType) {
		while ((node != null) && (node.getNodeType() != nodeType)) {
			node= node.getParent();
		}
		return node;
	}

	public static Statement findParentStatement(ASTNode node) {
		while ((node != null) && (!(node instanceof Statement))) {
			node= node.getParent();
			if (node instanceof BodyDeclaration) {
				return null;
			}
		}
		return (Statement) node;
	}

	public static TryStatement findParentTryStatement(ASTNode node) {
		while ((node != null) && (!(node instanceof TryStatement))) {
			node= node.getParent();
			if (node instanceof BodyDeclaration) {
				return null;
			}
		}
		return (TryStatement) node;
	}

	public static boolean isInsideConstructorInvocation(MethodDeclaration methodDeclaration, ASTNode node) {
		if (methodDeclaration.isConstructor()) {
			Statement statement= ASTResolving.findParentStatement(node);
			if (statement instanceof ConstructorInvocation || statement instanceof SuperConstructorInvocation) {
				return true; // argument in a this or super call
			}
		}
		return false;
	}

	public static boolean isInStaticContext(ASTNode selectedNode) {
		BodyDeclaration decl= ASTResolving.findParentBodyDeclaration(selectedNode);
		if (decl instanceof MethodDeclaration) {
			if (isInsideConstructorInvocation((MethodDeclaration) decl, selectedNode)) {
				return true;
			}
			return Modifier.isStatic(decl.getModifiers());
		} else if (decl instanceof Initializer) {
			return Modifier.isStatic(((Initializer)decl).getModifiers());
		} else if (decl instanceof FieldDeclaration) {
			return Modifier.isStatic(((FieldDeclaration)decl).getModifiers());
		}
		return false;
	}

	public static boolean isWriteAccess(Name selectedNode) {
		ASTNode curr= selectedNode;
		ASTNode parent= curr.getParent();
		while (parent != null) {
			switch (parent.getNodeType()) {
				case ASTNode.QUALIFIED_NAME:
					if (((QualifiedName) parent).getQualifier() == curr) {
						return false;
					}
					break;
				case ASTNode.FIELD_ACCESS:
					if (((FieldAccess) parent).getExpression() == curr) {
						return false;
					}
					break;
				case ASTNode.SUPER_FIELD_ACCESS:
					break;
				case ASTNode.ASSIGNMENT:
					return ((Assignment) parent).getLeftHandSide() == curr;
				case ASTNode.VARIABLE_DECLARATION_FRAGMENT:
				case ASTNode.SINGLE_VARIABLE_DECLARATION:
					return ((VariableDeclaration) parent).getName() == curr;
				case ASTNode.POSTFIX_EXPRESSION:
				case ASTNode.PREFIX_EXPRESSION:
					return true;
				default:
					return false;
			}

			curr= parent;
			parent= curr.getParent();
		}
		return false;
	}

	public static int getPossibleTypeKinds(ASTNode node, boolean is50OrHigher) {
		int kinds= internalGetPossibleTypeKinds(node);
		if (!is50OrHigher) {
			kinds &= (SimilarElementsRequestor.INTERFACES | SimilarElementsRequestor.CLASSES);
		}
		return kinds;
	}
	
	
	private static int internalGetPossibleTypeKinds(ASTNode node) {
		int kind= SimilarElementsRequestor.ALL_TYPES;

		ASTNode parent= node.getParent();
		while (parent instanceof QualifiedName) {
			if (node.getLocationInParent() == QualifiedName.QUALIFIER_PROPERTY) {
				return SimilarElementsRequestor.REF_TYPES;
			}
			node= parent;
			parent= parent.getParent();
		}
		while (parent instanceof Type) {
			if (parent instanceof QualifiedType) {
				if (node.getLocationInParent() == QualifiedType.QUALIFIER_PROPERTY) {
					return SimilarElementsRequestor.REF_TYPES;
				}
			} else if (parent instanceof ParameterizedType) {
				if (node.getLocationInParent() == ParameterizedType.TYPE_ARGUMENTS_PROPERTY) {
					return SimilarElementsRequestor.REF_TYPES;
				}
			} else if (parent instanceof WildcardType) {
				if (node.getLocationInParent() == WildcardType.BOUND_PROPERTY) {
					return SimilarElementsRequestor.REF_TYPES;
				}
			}
			node= parent;
			parent= parent.getParent();
		}

		switch (parent.getNodeType()) {
			case ASTNode.TYPE_DECLARATION:
				if (node.getLocationInParent() == TypeDeclaration.SUPER_INTERFACE_TYPES_PROPERTY) {
					kind= SimilarElementsRequestor.INTERFACES;
				} else if (node.getLocationInParent() == TypeDeclaration.SUPERCLASS_TYPE_PROPERTY) {
					kind= SimilarElementsRequestor.CLASSES;
				}
				break;
			case ASTNode.ENUM_DECLARATION:
				kind= SimilarElementsRequestor.INTERFACES;
				break;
			case ASTNode.METHOD_DECLARATION:
				if (node.getLocationInParent() == MethodDeclaration.THROWN_EXCEPTIONS_PROPERTY) {
					kind= SimilarElementsRequestor.CLASSES;
				} else if (node.getLocationInParent() == MethodDeclaration.RETURN_TYPE2_PROPERTY) {
					kind= SimilarElementsRequestor.ALL_TYPES | SimilarElementsRequestor.VOIDTYPE;
				}
				break;
			case ASTNode.INSTANCEOF_EXPRESSION:
				kind= SimilarElementsRequestor.REF_TYPES  & ~SimilarElementsRequestor.VARIABLES;
				break;
			case ASTNode.THROW_STATEMENT:
				kind= SimilarElementsRequestor.CLASSES;
				break;
			case ASTNode.CLASS_INSTANCE_CREATION:
				if (((ClassInstanceCreation) parent).getAnonymousClassDeclaration() == null) {
					kind= SimilarElementsRequestor.CLASSES;
				} else {
					kind= SimilarElementsRequestor.CLASSES | SimilarElementsRequestor.INTERFACES;
				}
				break;
			case ASTNode.SINGLE_VARIABLE_DECLARATION:
				int superParent= parent.getParent().getNodeType();
				if (superParent == ASTNode.CATCH_CLAUSE) {
					kind= SimilarElementsRequestor.CLASSES;
				}
				break;
			case ASTNode.TAG_ELEMENT:
				kind= SimilarElementsRequestor.REF_TYPES & ~SimilarElementsRequestor.VARIABLES;
				break;
			case ASTNode.MARKER_ANNOTATION:
			case ASTNode.SINGLE_MEMBER_ANNOTATION:
			case ASTNode.NORMAL_ANNOTATION:
				kind= SimilarElementsRequestor.ANNOTATIONS;
				break;
			case ASTNode.TYPE_PARAMETER:
				if (((TypeParameter) parent).typeBounds().indexOf(node) > 0) {
					kind= SimilarElementsRequestor.INTERFACES;
				} else {
					kind= SimilarElementsRequestor.REF_TYPES;
				}
				break;
			case ASTNode.TYPE_LITERAL:
				kind= SimilarElementsRequestor.REF_TYPES & ~SimilarElementsRequestor.VARIABLES;
				break;
			default:
		}
		return kind;
	}

	public static String getFullName(Name name) {
		return name.getFullyQualifiedName();
	}

	public static ICompilationUnit findCompilationUnitForBinding(ICompilationUnit cu, CompilationUnit astRoot, ITypeBinding binding) throws JavaModelException {
		if (binding == null || !binding.isFromSource() || binding.isTypeVariable() || binding.isWildcardType()) {
			return null;
		}
		ASTNode node= astRoot.findDeclaringNode(binding.getTypeDeclaration());
		if (node == null) {
			ICompilationUnit targetCU= Bindings.findCompilationUnit(binding, cu.getJavaProject());
			if (targetCU != null) {
				return targetCU;
			}
			return null;
		} else if (node instanceof AbstractTypeDeclaration || node instanceof AnonymousClassDeclaration) {
			return cu;
		}
		return null;
	}


	private static final Code[] CODE_ORDER= { PrimitiveType.CHAR, PrimitiveType.SHORT, PrimitiveType.INT, PrimitiveType.LONG, PrimitiveType.FLOAT, PrimitiveType.DOUBLE };

	public static ITypeBinding[] getRelaxingTypes(AST ast, ITypeBinding type) {
		ArrayList res= new ArrayList();
		res.add(type);
		if (type.isArray()) {
			res.add(ast.resolveWellKnownType("java.lang.Object")); //$NON-NLS-1$
			res.add(ast.resolveWellKnownType("java.io.Serializable")); //$NON-NLS-1$
			res.add(ast.resolveWellKnownType("java.lang.Cloneable")); //$NON-NLS-1$
		} else if (type.isPrimitive()) {
			Code code= PrimitiveType.toCode(type.getName());
			boolean found= false;
			for (int i= 0; i < CODE_ORDER.length; i++) {
				if (found) {
					String typeName= CODE_ORDER[i].toString();
					res.add(ast.resolveWellKnownType(typeName));
				}
				if (code == CODE_ORDER[i]) {
					found= true;
				}
			}
		} else {
			collectRelaxingTypes(res, type);
		}
		return (ITypeBinding[]) res.toArray(new ITypeBinding[res.size()]);
	}

	private static void collectRelaxingTypes(Collection res, ITypeBinding type) {
		ITypeBinding[] interfaces= type.getInterfaces();
		for (int i= 0; i < interfaces.length; i++) {
			ITypeBinding curr= interfaces[i];
			if (!res.contains(curr)) {
				res.add(curr);
			}
			collectRelaxingTypes(res, curr);
		}
		ITypeBinding binding= type.getSuperclass();
		if (binding != null) {
			if (!res.contains(binding)) {
				res.add(binding);
			}
			collectRelaxingTypes(res, binding);
		}
	}

	public static String getBaseNameFromExpression(IJavaProject project, Expression assignedExpression) {
		String name= null;
		if (assignedExpression instanceof Name) {
			Name simpleNode= (Name) assignedExpression;
			IBinding binding= simpleNode.resolveBinding();
			String varName= ASTNodes.getSimpleNameIdentifier(simpleNode);
			if (binding instanceof IVariableBinding) {
				if (((IVariableBinding) binding).isField()) {
					varName= NamingConventions.removePrefixAndSuffixForFieldName(project, varName, binding.getModifiers());
				} else {
					CompilationUnit astRoot= (CompilationUnit) assignedExpression.getRoot();
					if (astRoot.findDeclaringNode(binding) instanceof SingleVariableDeclaration) {
						varName= NamingConventions.removePrefixAndSuffixForArgumentName(project, varName);
					} else {
						varName= NamingConventions.removePrefixAndSuffixForLocalVariableName(project, varName);
					}
				}
			}
			return varName;
		} else if (assignedExpression instanceof MethodInvocation) {
			name= ((MethodInvocation) assignedExpression).getName().getIdentifier();
		} else if (assignedExpression instanceof SuperMethodInvocation) {
			name= ((SuperMethodInvocation) assignedExpression).getName().getIdentifier();
		}
		if (name != null && name.length() > 3) {
			if (name.startsWith("get")) { //$NON-NLS-1$
				return name.substring(3);
			}
		}
		return null;
	}
	
	public static String[] suggestLocalVariableNames(IJavaProject project, ITypeBinding binding, Expression expression, String[] excludedNames) {
		ArrayList res= new ArrayList();

		ITypeBinding base= binding.isArray() ? binding.getElementType() : binding;
		IPackageBinding packBinding= base.getPackage();
		String packName= packBinding != null ? packBinding.getName() : ""; //$NON-NLS-1$

		String name= ASTResolving.getBaseNameFromExpression(project, expression);
		if (name != null) {
			String[] argname= StubUtility.getLocalNameSuggestions(project, name, 0, excludedNames); // pass 0 as dimension, base name already contains plural.
			for (int i= 0; i < argname.length; i++) {
				String curr= argname[i];
				if (!res.contains(curr)) {
					res.add(curr);
				}
			}
		}

		String typeName= base.getName();
		String[] names= NamingConventions.suggestLocalVariableNames(project, packName, typeName, binding.getDimensions(), excludedNames);
		for (int i= 0; i < names.length; i++) {
			String curr= names[i];
			if (!res.contains(curr)) {
				res.add(curr);
			}
		}
		return (String[]) res.toArray(new String[res.size()]);
	}
	
	public static String[] getUsedVariableNames(ASTNode node) {
		CompilationUnit root= (CompilationUnit) node.getRoot();
		IBinding[] varsBefore= (new ScopeAnalyzer(root)).getDeclarationsInScope(node.getStartPosition(), ScopeAnalyzer.VARIABLES);
		IBinding[] varsAfter= (new ScopeAnalyzer(root)).getDeclarationsAfter(node.getStartPosition() + node.getLength(), ScopeAnalyzer.VARIABLES);

		String[] names= new String[varsBefore.length + varsAfter.length];
		for (int i= 0; i < varsBefore.length; i++) {
			names[i]= varsBefore[i].getName();
		}
		for (int i= 0; i < varsAfter.length; i++) {
			names[i + varsBefore.length]= varsAfter[i].getName();
		}
		return names;
	}
	
	

	private static boolean isVariableDefinedInContext(IBinding binding, ITypeBinding typeVariable) {
		if (binding.getKind() == IBinding.VARIABLE) {
			IVariableBinding var= (IVariableBinding) binding;
			binding= var.getDeclaringMethod();
			if (binding == null) {
				binding= var.getDeclaringClass();
			}
		}
		if (binding instanceof IMethodBinding) {
			if (binding == typeVariable.getDeclaringMethod()) {
				return true;
			}
			binding= ((IMethodBinding) binding).getDeclaringClass();
		}

		while (binding instanceof ITypeBinding) {
			if (binding == typeVariable.getDeclaringClass()) {
				return true;
			}
			if (Modifier.isStatic(binding.getModifiers())) {
				break;
			}
			binding= ((ITypeBinding) binding).getDeclaringClass();
		}
		return false;
	}

	public static boolean isUseableTypeInContext(ITypeBinding[] binding, IBinding context, boolean noWildcards) {
		for (int i= 0; i < binding.length; i++) {
			if (!isUseableTypeInContext(binding[i], context, noWildcards)) {
				return false;
			}
		}
		return true;
	}


	public static boolean isUseableTypeInContext(ITypeBinding type, IBinding context, boolean noWildcards) {
		if (type.isArray()) {
			type= type.getElementType();
		}
		if (type.isAnonymous()) {
			return false;
		}
		if (type.isRawType() || type.isPrimitive()) {
			return true;
		}
		if (type.isTypeVariable()) {
			return isVariableDefinedInContext(context, type);
		}
		if (type.isGenericType()) {
			ITypeBinding[] typeParameters= type.getTypeParameters();
			for (int i= 0; i < typeParameters.length; i++) {
				if (!isUseableTypeInContext(typeParameters[i], context, noWildcards)) {
					return false;
				}
			}
			return true;
		}
		if (type.isParameterizedType()) {
			ITypeBinding[] typeArguments= type.getTypeArguments();
			for (int i= 0; i < typeArguments.length; i++) {
				if (!isUseableTypeInContext(typeArguments[i], context, noWildcards)) {
					return false;
				}
			}
			return true;
		}
		if (type.isCapture()) {
			type= type.getWildcard();
		}
		
		if (type.isWildcardType()) {
			if (noWildcards) {
				return false;
			}
			if (type.getBound() != null) {
				return isUseableTypeInContext(type.getBound(), context, noWildcards);
			}
		}
		return true;
	}
		
	/**
	 * Use this method before creating a type for a wildcard. Either to assign a wildcard to a new type or for a type to be assigned.
	 * @param wildcardType the wildcard type to normalize
	 * @param isBindingToAssign If true, then a new receiver type is searched (X x= s), else the type of a sender (R r= x)
	 * @return Returns the normalized binding or null when only the 'null' binding 
	 */
	public static ITypeBinding normalizeWildcardType(ITypeBinding wildcardType, boolean isBindingToAssign, AST ast) {
		ITypeBinding bound= wildcardType.getBound();
		if (isBindingToAssign) {
			if (bound == null || !wildcardType.isUpperbound()) {
				return ast.resolveWellKnownType("java.lang.Object"); //$NON-NLS-1$
			}
		} else {
			if (bound == null || wildcardType.isUpperbound()) {
				return null;
			}
		}			
		return bound;
	}

	// pretty signatures

	public static String getTypeSignature(ITypeBinding type) {
		return BindingLabelProvider.getBindingLabel(type, BindingLabelProvider.DEFAULT_TEXTFLAGS);
	}

	public static String getMethodSignature(IMethodBinding binding, boolean inOtherCU) {
		StringBuffer buf= new StringBuffer();
		if (inOtherCU && !binding.isConstructor()) {
			buf.append(binding.getDeclaringClass().getTypeDeclaration().getName()).append('.'); // simple type name
		}
		return BindingLabelProvider.getBindingLabel(binding, BindingLabelProvider.DEFAULT_TEXTFLAGS);
	}

	public static String getMethodSignature(String name, ITypeBinding[] params) {
		StringBuffer buf= new StringBuffer();
		buf.append(name).append('(');
		for (int i= 0; i < params.length; i++) {
			if (i > 0) {
				buf.append(JavaElementLabels.COMMA_STRING);
			}
			buf.append(getTypeSignature(params[i]));
		}
		buf.append(')');
		return buf.toString();
	}

}
