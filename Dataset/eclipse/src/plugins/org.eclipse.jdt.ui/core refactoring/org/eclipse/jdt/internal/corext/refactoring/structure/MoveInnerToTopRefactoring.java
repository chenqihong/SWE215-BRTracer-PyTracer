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
package org.eclipse.jdt.internal.corext.refactoring.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.IRefactoringStatusEntryComparator;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;
import org.eclipse.ltk.core.refactoring.TextChange;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.ConstructorInvocation;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Statement;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclarationStatement;
import org.eclipse.jdt.core.dom.TypeParameter;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.codemanipulation.CodeGenerationSettings;
import org.eclipse.jdt.internal.corext.codemanipulation.ImportRewrite;
import org.eclipse.jdt.internal.corext.codemanipulation.ImportsStructure;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.ModifierRewrite;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine2;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.changes.CompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationStateChange;
import org.eclipse.jdt.internal.corext.refactoring.nls.changes.CreateTextFileChange;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.JavadocUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.SearchUtils;
import org.eclipse.jdt.internal.corext.util.Strings;
import org.eclipse.jdt.internal.corext.util.WorkingCopyUtil;

import org.eclipse.jdt.ui.CodeGeneration;

import org.eclipse.jdt.internal.ui.viewsupport.BindingLabels;

public class MoveInnerToTopRefactoring extends Refactoring {

	private static class MemberAccessNodeCollector extends ASTVisitor {

		private final List fFieldAccesses= new ArrayList(0);

		private final ITypeHierarchy fHierarchy;

		private final List fMethodAccesses= new ArrayList(0);

		private final List fSimpleNames= new ArrayList(0);

		MemberAccessNodeCollector(ITypeHierarchy hierarchy) {
			Assert.isNotNull(hierarchy);
			fHierarchy= hierarchy;
		}

		FieldAccess[] getFieldAccesses() {
			return (FieldAccess[]) fFieldAccesses.toArray(new FieldAccess[fFieldAccesses.size()]);
		}

		MethodInvocation[] getMethodInvocations() {
			return (MethodInvocation[]) fMethodAccesses.toArray(new MethodInvocation[fMethodAccesses.size()]);
		}

		SimpleName[] getSimpleFieldNames() {
			return (SimpleName[]) fSimpleNames.toArray(new SimpleName[fSimpleNames.size()]);
		}

		public boolean visit(FieldAccess node) {
			final ITypeBinding declaring= MoveInnerToTopRefactoring.getDeclaringTypeBinding(node);
			if (declaring != null) {
				final IType type= (IType) declaring.getJavaElement();
				if (type != null && fHierarchy.contains(type))
					fFieldAccesses.add(node);
			}
			return super.visit(node);
		}

		public boolean visit(MethodInvocation node) {
			final ITypeBinding declaring= MoveInnerToTopRefactoring.getDeclaringTypeBinding(node);
			if (declaring != null) {
				final IType type= (IType) declaring.getJavaElement();
				if (type != null && fHierarchy.contains(type))
					fMethodAccesses.add(node);
			}
			return super.visit(node);
		}

		public boolean visit(SimpleName node) {
			if (node.getParent() instanceof QualifiedName)
				return super.visit(node);
			IBinding binding= node.resolveBinding();
			if (binding instanceof IVariableBinding) {
				IVariableBinding variable= (IVariableBinding) binding;
				ITypeBinding declaring= variable.getDeclaringClass();
				if (variable.isField() && declaring != null) {
					final IType type= (IType) declaring.getJavaElement();
					if (type != null && fHierarchy.contains(type)) {
						fSimpleNames.add(node);
						return false;
					}
				}
			}
			return super.visit(node);
		}

		public boolean visit(ThisExpression node) {
			final Name qualifier= node.getQualifier();
			if (qualifier != null) {
				final ITypeBinding binding= qualifier.resolveTypeBinding();
				if (binding != null) {
					final IType type= (IType) binding.getJavaElement();
					if (type != null && fHierarchy.contains(type)) {
						fSimpleNames.add(qualifier);
						return false;
					}
				}
			}
			return super.visit(node);
		}
	}

	private class TypeReferenceQualifier extends ASTVisitor {

		private final TextEditGroup fGroup;

		private final ITypeBinding fTypeBinding;

		public TypeReferenceQualifier(final ITypeBinding type, final TextEditGroup group) {
			Assert.isNotNull(type);
			Assert.isNotNull(type.getDeclaringClass());
			fTypeBinding= type;
			fGroup= group;
		}

		public boolean visit(final ClassInstanceCreation node) {
			Assert.isNotNull(node);
			if (fCreateInstanceField) {
				final AST ast= node.getAST();
				final Type type= node.getType();
				final ITypeBinding binding= type.resolveBinding();
				if (binding != null && binding.getDeclaringClass() != null && !Bindings.equals(binding, fTypeBinding) && fSourceRewrite.getRoot().findDeclaringNode(binding) != null) {
					if (!Modifier.isStatic(binding.getModifiers())) {
						Expression expression= null;
						if (fCodeGenerationSettings.useKeywordThis || fEnclosingInstanceFieldName.equals(fNameForEnclosingInstanceConstructorParameter)) {
							final FieldAccess access= ast.newFieldAccess();
							access.setExpression(ast.newThisExpression());
							access.setName(ast.newSimpleName(fEnclosingInstanceFieldName));
							expression= access;
						} else
							expression= ast.newSimpleName(fEnclosingInstanceFieldName);
						if (node.getExpression() != null)
							fSourceRewrite.getImportRemover().registerRemovedNode(node.getExpression());
						fSourceRewrite.getASTRewrite().set(node, ClassInstanceCreation.EXPRESSION_PROPERTY, expression, fGroup);
					} else
						addTypeQualification(type, fSourceRewrite, fGroup);
				}
			}
			return true;
		}

		public boolean visit(final QualifiedType node) {
			Assert.isNotNull(node);
			return false;
		}

		public boolean visit(final SimpleType node) {
			Assert.isNotNull(node);
			if (!(node.getParent() instanceof ClassInstanceCreation)) {
				final ITypeBinding binding= node.resolveBinding();
				if (binding != null) {
					final ITypeBinding declaring= binding.getDeclaringClass();
					if (declaring != null && !Bindings.equals(declaring, fTypeBinding.getDeclaringClass()) && !Bindings.equals(binding, fTypeBinding) && fSourceRewrite.getRoot().findDeclaringNode(binding) != null && Modifier.isStatic(binding.getModifiers()))
						addTypeQualification(node, fSourceRewrite, fGroup);
				}
			}
			return super.visit(node);
		}

		public boolean visit(final ThisExpression node) {
			Assert.isNotNull(node);
			final Name name= node.getQualifier();
			if (name != null && name.isSimpleName()) {
				final AST ast= node.getAST();
				Expression expression= null;
				if (fCodeGenerationSettings.useKeywordThis || fEnclosingInstanceFieldName.equals(fNameForEnclosingInstanceConstructorParameter)) {
					final FieldAccess access= ast.newFieldAccess();
					access.setExpression(ast.newThisExpression());
					access.setName(ast.newSimpleName(fEnclosingInstanceFieldName));
					expression= access;
				} else
					expression= ast.newSimpleName(fEnclosingInstanceFieldName);
				fSourceRewrite.getASTRewrite().replace(node, expression, null);
			}
			return super.visit(node);
		}
	}

	private class TypeVisibilityModifier extends ASTVisitor {

		private final ITypeBinding fBinding;

		private final TextEditGroup fGroup;

		private final RefactoringStatus fStatus;

		private final Set handledTypes= new HashSet();

		public TypeVisibilityModifier(final RefactoringStatus status, final ITypeBinding type, final TextEditGroup group) {
			Assert.isNotNull(status);
			Assert.isNotNull(type);
			Assert.isNotNull(type.getDeclaringClass());
			fStatus= status;
			fBinding= type;
			fGroup= group;
		}

		public boolean visit(final QualifiedType node) {
			visitType(node);
			return false;
		}

		public boolean visit(final SimpleType node) {
			visitType(node);
			return false;
		}

		private void visitType(final Type type) {
			Assert.isNotNull(type);
			final ITypeBinding binding= type.resolveBinding();
			if (binding != null && !handledTypes.contains(binding.getKey()) && !Bindings.equals(fBinding, binding) && Modifier.isPrivate(binding.getModifiers())) {
				final ASTNode node= fSourceRewrite.getRoot().findDeclaringNode(binding);
				if (node instanceof AbstractTypeDeclaration) {
					final AbstractTypeDeclaration declaration= (AbstractTypeDeclaration) node;
					ModifierRewrite.create(fSourceRewrite.getASTRewrite(), declaration).setModifiers(0, Modifier.PRIVATE, fGroup);
					final RefactoringStatusEntry entry= new RefactoringStatusEntry(RefactoringStatus.WARNING, Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_change_visibility_type_warning, new String[] { BindingLabels.getFullyQualified(binding)}), JavaStatusContext.create(fSourceRewrite.getCu(), node)); 
					if (!containsStatusEntry(fStatus, entry))
						fStatus.addEntry(entry);
				}
			}
		}
	}

	private static void addTypeParameters(final CompilationUnit declaring, final IType type, final Map map) throws JavaModelException {
		Assert.isNotNull(declaring);
		Assert.isNotNull(type);
		Assert.isNotNull(map);
		final AbstractTypeDeclaration declaration= ASTNodeSearchUtil.getAbstractTypeDeclarationNode(type, declaring);
		if (declaration instanceof TypeDeclaration) {
			ITypeBinding binding= null;
			TypeParameter parameter= null;
			for (final Iterator iterator= ((TypeDeclaration) declaration).typeParameters().iterator(); iterator.hasNext();) {
				parameter= (TypeParameter) iterator.next();
				binding= parameter.resolveBinding();
				if (binding != null && !map.containsKey(binding.getKey()))
					map.put(binding.getKey(), binding);
			}
			if (type.getDeclaringType() != null)
				addTypeParameters(declaring, type.getDeclaringType(), map);
		}
	}

	private static boolean containsNonStatic(FieldAccess[] accesses) {
		for (int i= 0; i < accesses.length; i++) {
			if (!isStatic(accesses[i]))
				return true;
		}
		return false;
	}

	private static boolean containsNonStatic(MethodInvocation[] invocations) {
		for (int i= 0; i < invocations.length; i++) {
			if (!isStatic(invocations[i]))
				return true;
		}
		return false;
	}

	private static boolean containsNonStatic(SimpleName[] fieldNames) {
		for (int i= 0; i < fieldNames.length; i++) {
			if (!isStaticFieldName(fieldNames[i]))
				return true;
		}
		return false;
	}

	private static boolean containsStatusEntry(final RefactoringStatus status, final RefactoringStatusEntry other) {
		return status.getEntries(new IRefactoringStatusEntryComparator() {

			public final int compare(final RefactoringStatusEntry entry1, final RefactoringStatusEntry entry2) {
				return entry1.getMessage().compareTo(entry2.getMessage());
			}
		}, other).length > 0;
	}

	public static MoveInnerToTopRefactoring create(IType type, CodeGenerationSettings codeGenerationSettings) throws JavaModelException {
		if (!RefactoringAvailabilityTester.isMoveInnerAvailable(type))
			return null;
		return new MoveInnerToTopRefactoring(type, codeGenerationSettings);
	}

	private static AbstractTypeDeclaration findTypeDeclaration(IType enclosing, AbstractTypeDeclaration[] declarations) {
		String typeName= enclosing.getElementName();
		for (int i= 0; i < declarations.length; i++) {
			AbstractTypeDeclaration declaration= declarations[i];
			if (declaration.getName().getIdentifier().equals(typeName))
				return declaration;
		}
		return null;
	}

	private static AbstractTypeDeclaration findTypeDeclaration(IType type, CompilationUnit unit) {
		final List types= getDeclaringTypes(type);
		types.add(type);
		AbstractTypeDeclaration[] declarations= (AbstractTypeDeclaration[]) unit.types().toArray(new AbstractTypeDeclaration[unit.types().size()]);
		AbstractTypeDeclaration declaration= null;
		for (final Iterator iterator= types.iterator(); iterator.hasNext();) {
			IType enclosing= (IType) iterator.next();
			declaration= findTypeDeclaration(enclosing, declarations);
			Assert.isNotNull(declaration);
			declarations= getAbstractTypeDeclarations(declaration);
		}
		Assert.isNotNull(declaration);
		return declaration;
	}

	public static AbstractTypeDeclaration[] getAbstractTypeDeclarations(final AbstractTypeDeclaration declaration) {
		int typeCount= 0;
		for (Iterator iterator= declaration.bodyDeclarations().listIterator(); iterator.hasNext();) {
			if (iterator.next() instanceof AbstractTypeDeclaration) {
				typeCount++;
			}
		}
		AbstractTypeDeclaration[] declarations= new AbstractTypeDeclaration[typeCount];
		int next= 0;
		for (final Iterator iterator= declaration.bodyDeclarations().listIterator(); iterator.hasNext();) {
			Object object= iterator.next();
			if (object instanceof AbstractTypeDeclaration) {
				declarations[next++]= (AbstractTypeDeclaration) object;
			}
		}
		return declarations;
	}

	private static ITypeBinding getDeclaringTypeBinding(FieldAccess fieldAccess) {
		IVariableBinding varBinding= fieldAccess.resolveFieldBinding();
		if (varBinding == null)
			return null;
		return varBinding.getDeclaringClass();
	}

	private static ITypeBinding getDeclaringTypeBinding(MethodInvocation methodInvocation) {
		IMethodBinding binding= methodInvocation.resolveMethodBinding();
		if (binding == null)
			return null;
		return binding.getDeclaringClass();
	}

	// List of ITypes
	private static List getDeclaringTypes(IType type) {
		IType declaringType= type.getDeclaringType();
		if (declaringType == null)
			return new ArrayList(0);
		List result= getDeclaringTypes(declaringType);
		result.add(declaringType);
		return result;
	}

	private static String[] getFieldNames(IType type) {
		try {
			IField[] fields= type.getFields();
			List result= new ArrayList(fields.length);
			for (int i= 0; i < fields.length; i++) {
				result.add(fields[i].getElementName());
			}
			return (String[]) result.toArray(new String[result.size()]);
		} catch (JavaModelException e) {
			return null;
		}
	}

	private static Set getMergedSet(Set s1, Set s2) {
		Set result= new HashSet();
		result.addAll(s1);
		result.addAll(s2);
		return result;
	}

	private static String[] getParameterNamesOfAllConstructors(IType type) throws JavaModelException {
		IMethod[] constructors= JavaElementUtil.getAllConstructors(type);
		Set result= new HashSet();
		for (int i= 0; i < constructors.length; i++) {
			result.addAll(Arrays.asList(constructors[i].getParameterNames()));
		}
		return (String[]) result.toArray(new String[result.size()]);
	}

	private static ASTNode[] getReferenceNodesIn(CompilationUnit cuNode, Map references, ICompilationUnit cu) {
		SearchMatch[] results= (SearchMatch[]) references.get(cu);
		if (results == null)
			return new ASTNode[0];
		return ASTNodeSearchUtil.getAstNodes(results, cuNode);
	}

	private static boolean isCorrespondingTypeBinding(ITypeBinding binding, IType type) {
		if (binding == null)
			return false;
		return Bindings.getFullyQualifiedName(binding).equals(JavaElementUtil.createSignature(type));
	}

	private static boolean isStatic(FieldAccess access) {
		IVariableBinding fieldBinding= access.resolveFieldBinding();
		if (fieldBinding == null)
			return false;
		return JdtFlags.isStatic(fieldBinding);
	}

	private static boolean isStatic(MethodInvocation invocation) {
		IMethodBinding methodBinding= invocation.resolveMethodBinding();
		if (methodBinding == null)
			return false;
		return JdtFlags.isStatic(methodBinding);
	}

	private static boolean isStaticFieldName(SimpleName name) {
		IBinding binding= name.resolveBinding();
		if (!(binding instanceof IVariableBinding))
			return false;
		IVariableBinding variableBinding= (IVariableBinding) binding;
		if (!variableBinding.isField())
			return false;
		return JdtFlags.isStatic(variableBinding);
	}

	private TextChangeManager fChangeManager;

	private final CodeGenerationSettings fCodeGenerationSettings;

	private boolean fCreateInstanceField;

	private String fEnclosingInstanceFieldName;

	private boolean fIsInstanceFieldCreationMandatory;

	private boolean fIsInstanceFieldCreationPossible;

	private boolean fMarkInstanceFieldAsFinal;

	private String fNameForEnclosingInstanceConstructorParameter;

	private String fNewSourceOfInputType;

	private CompilationUnitRewrite fSourceRewrite;

	private Collection fStaticImports;

	private IType fType;

	private final String[] fTypeComponents;

	private Collection fTypeImports;

	private MoveInnerToTopRefactoring(IType type, CodeGenerationSettings codeGenerationSettings) throws JavaModelException {
		Assert.isNotNull(type);
		Assert.isNotNull(codeGenerationSettings);
		fType= type;
		fCodeGenerationSettings= codeGenerationSettings;
		fMarkInstanceFieldAsFinal= true; // default
		fTypeComponents= Strings.splitByToken((fType.getPackageFragment().getElementName() + '.' + fType.getElementName()), "."); //$NON-NLS-1$
	}

	private void addEnclosingInstanceDeclaration(final AbstractTypeDeclaration declaration, final ASTRewrite rewrite) throws CoreException {
		Assert.isNotNull(declaration);
		Assert.isNotNull(rewrite);
		final AST ast= declaration.getAST();
		final VariableDeclarationFragment fragment= ast.newVariableDeclarationFragment();
		fragment.setName(ast.newSimpleName(fEnclosingInstanceFieldName));
		final FieldDeclaration newField= ast.newFieldDeclaration(fragment);
		newField.modifiers().addAll(ASTNodeFactory.newModifiers(ast, getEnclosingInstanceAccessModifiers()));
		newField.setType(createEnclosingType(ast));
		final String comment= CodeGeneration.getFieldComment(fType.getCompilationUnit(), declaration.getName().getIdentifier(), fEnclosingInstanceFieldName, StubUtility.getLineDelimiterUsed(fType.getJavaProject()));
		if (comment != null && comment.length() > 0) {
			final Javadoc doc= (Javadoc) rewrite.createStringPlaceholder(comment, ASTNode.JAVADOC);
			newField.setJavadoc(doc);
		}
		rewrite.getListRewrite(declaration, declaration.getBodyDeclarationsProperty()).insertFirst(newField, null);
	}

	private void addEnclosingInstanceTypeParameters(final ITypeBinding[] parameters, final AbstractTypeDeclaration declaration, final ASTRewrite rewrite) {
		Assert.isNotNull(parameters);
		Assert.isNotNull(declaration);
		Assert.isNotNull(rewrite);
		if (declaration instanceof TypeDeclaration) {
			final TypeDeclaration type= (TypeDeclaration) declaration;
			final List existing= type.typeParameters();
			final Set names= new HashSet();
			TypeParameter parameter= null;
			for (final Iterator iterator= existing.iterator(); iterator.hasNext();) {
				parameter= (TypeParameter) iterator.next();
				names.add(parameter.getName().getIdentifier());
			}
			final ListRewrite rewriter= rewrite.getListRewrite(type, TypeDeclaration.TYPE_PARAMETERS_PROPERTY);
			String name= null;
			for (int index= 0; index < parameters.length; index++) {
				name= parameters[index].getName();
				if (!names.contains(name)) {
					parameter= type.getAST().newTypeParameter();
					parameter.setName(type.getAST().newSimpleName(name));
					rewriter.insertLast(parameter, null);
				}
			}
		}
	}

	private void addImportsToTargetUnit(final ICompilationUnit targetUnit, final IProgressMonitor monitor) throws CoreException, JavaModelException {
		final ImportsStructure structure= new ImportsStructure(targetUnit, fCodeGenerationSettings.importOrder, fCodeGenerationSettings.importThreshold, true);
		if (fTypeImports != null) {
			ITypeBinding type= null;
			for (final Iterator iterator= fTypeImports.iterator(); iterator.hasNext();) {
				type= (ITypeBinding) iterator.next();
				structure.addImport(type);
			}
		}
		if (fStaticImports != null) {
			IBinding binding= null;
			for (final Iterator iterator= fStaticImports.iterator(); iterator.hasNext();) {
				binding= (IBinding) iterator.next();
				structure.addStaticImport(binding);
			}
		}
		fTypeImports= null;
		fStaticImports= null;
		structure.create(false, monitor);
	}

	private void addInheritedTypeQualifications(final AbstractTypeDeclaration declaration, final CompilationUnitRewrite targetRewrite, final TextEditGroup group) {
		Assert.isNotNull(declaration);
		Assert.isNotNull(targetRewrite);
		final CompilationUnit unit= (CompilationUnit) declaration.getRoot();
		final ITypeBinding binding= declaration.resolveBinding();
		if (binding != null) {
			Type type= null;
			if (declaration instanceof TypeDeclaration) {
				type= ((TypeDeclaration) declaration).getSuperclassType();
				if (type != null && unit.findDeclaringNode(binding) != null)
					addTypeQualification(type, targetRewrite, group);
			}
			List types= null;
			if (declaration instanceof TypeDeclaration)
				types= ((TypeDeclaration) declaration).superInterfaceTypes();
			else if (declaration instanceof EnumDeclaration)
				types= ((EnumDeclaration) declaration).superInterfaceTypes();
			if (types != null) {
				for (final Iterator iterator= types.iterator(); iterator.hasNext();) {
					type= (Type) iterator.next();
					if (unit.findDeclaringNode(type.resolveBinding()) != null)
						addTypeQualification(type, targetRewrite, group);
				}
			}
		}
	}

	private void addParameterToConstructor(final ASTRewrite rewrite, final MethodDeclaration declaration) throws JavaModelException {
		Assert.isNotNull(rewrite);
		Assert.isNotNull(declaration);
		final AST ast= declaration.getAST();
		final String name= getNameForEnclosingInstanceConstructorParameter();
		final SingleVariableDeclaration variable= ast.newSingleVariableDeclaration();
		variable.setType(createEnclosingType(ast));
		variable.setName(ast.newSimpleName(name));
		rewrite.getListRewrite(declaration, MethodDeclaration.PARAMETERS_PROPERTY).insertFirst(variable, null);
		JavadocUtil.addParamJavadoc(name, declaration, rewrite, fType.getJavaProject(), null);
	}

	private void addSimpleTypeQualification(final CompilationUnitRewrite targetRewrite, final ITypeBinding declaring, final SimpleType simpleType, final TextEditGroup group) {
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(declaring);
		Assert.isNotNull(simpleType);
		final AST ast= targetRewrite.getRoot().getAST();
		if (!(simpleType.getName() instanceof QualifiedName)) {
			targetRewrite.getASTRewrite().replace(simpleType, ast.newQualifiedType(targetRewrite.getImportRewrite().addImport(declaring, ast), ast.newSimpleName(simpleType.getName().getFullyQualifiedName())), group);
			targetRewrite.getImportRemover().registerRemovedNode(simpleType);
		}
	}

	private void addTypeQualification(final Type type, final CompilationUnitRewrite targetRewrite, final TextEditGroup group) {
		Assert.isNotNull(type);
		Assert.isNotNull(targetRewrite);
		final ITypeBinding binding= type.resolveBinding();
		if (binding != null) {
			final ITypeBinding declaring= binding.getDeclaringClass();
			if (declaring != null) {
				if (type instanceof SimpleType) {
					final SimpleType simpleType= (SimpleType) type;
					addSimpleTypeQualification(targetRewrite, declaring, simpleType, group);
				} else if (type instanceof ParameterizedType) {
					final ParameterizedType parameterizedType= (ParameterizedType) type;
					final Type rawType= parameterizedType.getType();
					if (rawType instanceof SimpleType)
						addSimpleTypeQualification(targetRewrite, declaring, (SimpleType) rawType, group);
				}
			}
		}
	}

	private RefactoringStatus checkConstructorParameterNames() {
		RefactoringStatus result= new RefactoringStatus();
		CompilationUnit cuNode= new RefactoringASTParser(AST.JLS3).parse(fType.getCompilationUnit(), false);
		MethodDeclaration[] nodes= getConstructorDeclarationNodes(findTypeDeclaration(fType, cuNode));
		for (int i= 0; i < nodes.length; i++) {
			MethodDeclaration constructor= nodes[i];
			for (Iterator iter= constructor.parameters().iterator(); iter.hasNext();) {
				SingleVariableDeclaration param= (SingleVariableDeclaration) iter.next();
				if (fEnclosingInstanceFieldName.equals(param.getName().getIdentifier())) {
					String msg= Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_name_used, new String[] { param.getName().getIdentifier(), fType.getElementName()}); 
					result.addError(msg, JavaStatusContext.create(fType.getCompilationUnit(), param));
				}
			}
		}
		return result;
	}

	public RefactoringStatus checkEnclosingInstanceName(String name) {
		if (!fCreateInstanceField)
			return new RefactoringStatus();
		RefactoringStatus result= Checks.checkFieldName(name);
		if (!Checks.startsWithLowerCase(name))
			result.addWarning(RefactoringCoreMessages.MoveInnerToTopRefactoring_names_start_lowercase); 

		if (fType.getField(name).exists()) {
			Object[] keys= new String[] { name, fType.getElementName()};
			String msg= Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_already_declared, keys); 
			result.addError(msg, JavaStatusContext.create(fType.getField(name)));
		}
		return result;
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.base.Refactoring#checkInput(IProgressMonitor)
	 */
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException {
		pm.beginTask("", 2);//$NON-NLS-1$
		try {
			RefactoringStatus result= new RefactoringStatus();

			if (JdtFlags.isStatic(fType))
				result.merge(checkEnclosingInstanceName(fEnclosingInstanceFieldName));

			if (fType.getPackageFragment().getCompilationUnit((fType.getElementName() + ".java")).exists()) { //$NON-NLS-1$
				String message= Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_compilation_Unit_exists, new String[] { (fType.getElementName() + ".java"), fType.getPackageFragment().getElementName()});  //$NON-NLS-1$
				result.addFatalError(message);
			}
			result.merge(checkEnclosingInstanceName(fEnclosingInstanceFieldName));
			result.merge(Checks.checkCompilationUnitName((fType.getElementName() + ".java"))); //$NON-NLS-1$
			result.merge(checkConstructorParameterNames());
			result.merge(checkTypeNameInPackage());
			fChangeManager= createChangeManager(new SubProgressMonitor(pm, 1), result);
			result.merge(Checks.validateModifiesFiles(ResourceUtil.getFiles(fChangeManager.getAllCompilationUnits()), getValidationContext()));
			return result;
		} finally {
			pm.done();
		}
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.base.Refactoring#checkActivation(IProgressMonitor)
	 */
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException {

		fEnclosingInstanceFieldName= getInitialNameForEnclosingInstanceField();
		fSourceRewrite= new CompilationUnitRewrite(fType.getCompilationUnit());
		fIsInstanceFieldCreationPossible= !(JdtFlags.isStatic(fType) || fType.isAnnotation() || fType.isEnum());
		fIsInstanceFieldCreationMandatory= fIsInstanceFieldCreationPossible && isInstanceFieldCreationMandatory();
		fCreateInstanceField= fIsInstanceFieldCreationMandatory;

		IType orig= (IType) WorkingCopyUtil.getOriginal(fType);
		if (orig == null || !orig.exists()) {

			String message= Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_deleted, new String[] { fType.getCompilationUnit().getElementName()}); 
			return RefactoringStatus.createFatalErrorStatus(message);
		}
		fType= orig;

		return Checks.checkIfCuBroken(fType);
	}

	private RefactoringStatus checkTypeNameInPackage() throws JavaModelException {
		IType type= Checks.findTypeInPackage(fType.getPackageFragment(), fType.getElementName());
		if (type == null || !type.exists())
			return null;
		String message= Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_type_exists, new String[] { fType.getElementName(), fType.getPackageFragment().getElementName()}); 
		return RefactoringStatus.createErrorStatus(message);
	}

	private Expression createAccessExpressionToEnclosingInstanceFieldText(ASTNode node, IBinding binding, AbstractTypeDeclaration declaration) {
		if (Modifier.isStatic(binding.getModifiers()))
			return node.getAST().newName(Strings.splitByToken(JavaModelUtil.getTypeQualifiedName(fType.getDeclaringType()), ".")); //$NON-NLS-1$
		else if ((isInAnonymousTypeInsideInputType(node, declaration) || isInLocalTypeInsideInputType(node, declaration) || isInNonStaticMemberTypeInsideInputType(node, declaration)))
			return createQualifiedReadAccessExpressionForEnclosingInstance(node.getAST());
		else
			return createReadAccessExpressionForEnclosingInstance(node.getAST());
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.base.IRefactoring#createChange(IProgressMonitor)
	 */
	public Change createChange(final IProgressMonitor monitor) throws CoreException {
		monitor.beginTask(RefactoringCoreMessages.MoveInnerToTopRefactoring_creating_change, 1); 
		final DynamicValidationStateChange result= new DynamicValidationStateChange(RefactoringCoreMessages.MoveInnerToTopRefactoring_move_to_Top); 
		result.addAll(fChangeManager.getAllChanges());
		result.add(createCompilationUnitForMovedType(new SubProgressMonitor(monitor, 1)));
		return result;
	}

	private TextChangeManager createChangeManager(final IProgressMonitor monitor, final RefactoringStatus status) throws CoreException {
		Assert.isNotNull(monitor);
		Assert.isNotNull(status);
		final TextChangeManager manager= new TextChangeManager();
		try {
			monitor.beginTask(RefactoringCoreMessages.MoveInnerToTopRefactoring_creating_preview, 4); 
			final Map rewrites= new HashMap(2);
			rewrites.put(fSourceRewrite.getCu(), fSourceRewrite);
			final MemberVisibilityAdjustor adjustor= new MemberVisibilityAdjustor(fType.getPackageFragment(), fType);
			adjustor.setGetters(true);
			adjustor.setSetters(true);
			adjustor.setRewrites(rewrites);
			adjustor.setVisibilitySeverity(RefactoringStatus.WARNING);
			adjustor.setFailureSeverity(RefactoringStatus.WARNING);
			adjustor.setStatus(status);
			adjustor.adjustVisibility(new SubProgressMonitor(monitor, 1));
			final Map parameters= new LinkedHashMap();
			addTypeParameters(fSourceRewrite.getRoot(), fType, parameters);
			final ITypeBinding[] bindings= new ITypeBinding[parameters.values().size()];
			parameters.values().toArray(bindings);
			final Map typeReferences= createTypeReferencesMapping(new SubProgressMonitor(monitor, 1), status);
			Map constructorReferences= null;
			if (JdtFlags.isStatic(fType))
				constructorReferences= new HashMap(0);
			else
				constructorReferences= createConstructorReferencesMapping(new SubProgressMonitor(monitor, 1), status);
			monitor.worked(1);
			for (final Iterator iterator= getMergedSet(typeReferences.keySet(), constructorReferences.keySet()).iterator(); iterator.hasNext();) {
				final ICompilationUnit unit= (ICompilationUnit) iterator.next();
				final CompilationUnitRewrite targetRewrite= getCompilationUnitRewrite(unit);
				createCompilationUnitRewrite(bindings, targetRewrite, typeReferences, constructorReferences, fType.getCompilationUnit(), unit, false, status, monitor);
				if (unit.equals(fType.getCompilationUnit())) {
					try {
						adjustor.setStatus(new RefactoringStatus());
						adjustor.rewriteVisibility(targetRewrite.getCu(), new SubProgressMonitor(monitor, 1));
					} finally {
						adjustor.setStatus(status);
					}
					fNewSourceOfInputType= createNewSource(targetRewrite, unit);
					targetRewrite.clearASTAndImportRewrites();
					createCompilationUnitRewrite(bindings, targetRewrite, typeReferences, constructorReferences, fType.getCompilationUnit(), unit, true, status, monitor);
				}
				adjustor.rewriteVisibility(targetRewrite.getCu(), new SubProgressMonitor(monitor, 1));
				manager.manage(unit, targetRewrite.createChange());
			}
		} finally {
			monitor.done();
		}
		return manager;
	}

	private Change createCompilationUnitForMovedType(IProgressMonitor pm) throws CoreException {
		ICompilationUnit newCuWC= null;
		try {
			newCuWC= WorkingCopyUtil.getNewWorkingCopy(fType.getPackageFragment(), (fType.getElementName() + ".java")); //$NON-NLS-1$
			String source= createSourceForNewCu(newCuWC, pm);
			return new CreateTextFileChange(ResourceUtil.getFile(fType.getCompilationUnit()).getFullPath().removeLastSegments(1).append((fType.getElementName() + ".java")), source, null, "java"); //$NON-NLS-1$ //$NON-NLS-2$
		} finally {
			if (newCuWC != null)
				newCuWC.discardWorkingCopy();
		}
	}

	private void createCompilationUnitRewrite(final ITypeBinding[] parameters, final CompilationUnitRewrite targetRewrite, final Map typeReferences, final Map constructorReferences, final ICompilationUnit sourceUnit, final ICompilationUnit targetUnit, final boolean remove, final RefactoringStatus status, final IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(parameters);
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(typeReferences);
		Assert.isNotNull(constructorReferences);
		Assert.isNotNull(sourceUnit);
		Assert.isNotNull(targetUnit);
		final CompilationUnit root= targetRewrite.getRoot();
		final ASTRewrite rewrite= targetRewrite.getASTRewrite();
		if (targetUnit.equals(sourceUnit)) {
			final AbstractTypeDeclaration declaration= findTypeDeclaration(fType, root);
			final TextEditGroup qualifierGroup= fSourceRewrite.createGroupDescription(RefactoringCoreMessages.MoveInnerToTopRefactoring_change_qualifier); 
			if (!remove) {
				if (!JdtFlags.isStatic(fType) && fCreateInstanceField) {
					if (JavaElementUtil.getAllConstructors(fType).length == 0)
						createConstructor(declaration, rewrite);
					else
						modifyConstructors(declaration, rewrite);
					addInheritedTypeQualifications(declaration, targetRewrite, qualifierGroup);
					addEnclosingInstanceDeclaration(declaration, rewrite);
				}
				fTypeImports= new ArrayList();
				fStaticImports= new ArrayList();
				ImportRewriteUtil.collectImports(fType.getJavaProject(), declaration, fTypeImports, fStaticImports, false);
			}
			addEnclosingInstanceTypeParameters(parameters, declaration, rewrite);
			modifyAccessToEnclosingInstance(targetRewrite, declaration, status, monitor);
			final ITypeBinding binding= declaration.resolveBinding();
			if (binding != null) {
				modifyInterfaceMemberModifiers(binding);
				modifyEnclosingClassModifiers(status, binding, fSourceRewrite.createGroupDescription(RefactoringCoreMessages.MoveInnerToTopRefactoring_change_visibility)); 
				final ITypeBinding declaring= binding.getDeclaringClass();
				if (declaring != null)
					declaration.accept(new TypeReferenceQualifier(binding, null));
				declaration.accept(new TypeVisibilityModifier(status, binding, fSourceRewrite.createGroupDescription(RefactoringCoreMessages.MoveInnerToTopRefactoring_change_visibility))); 
			}
			final TextEditGroup groupMove= targetRewrite.createGroupDescription(RefactoringCoreMessages.MoveInnerToTopRefactoring_change_label); 
			if (remove) {
				rewrite.remove(declaration, groupMove);
				targetRewrite.getImportRemover().registerRemovedNode(declaration);
			} else
				ModifierRewrite.create(rewrite, declaration).setModifiers(JdtFlags.clearFlag(Modifier.STATIC | Modifier.PROTECTED | Modifier.PRIVATE, declaration.getModifiers()), groupMove);
		}
		ASTNode[] references= getReferenceNodesIn(root, typeReferences, targetUnit);
		for (int index= 0; index < references.length; index++)
			updateTypeReference(parameters, references[index], targetRewrite, targetUnit);
		references= getReferenceNodesIn(root, constructorReferences, targetUnit);
		for (int index= 0; index < references.length; index++)
			updateConstructorReference(parameters, references[index], targetRewrite, targetUnit);
	}

	private void createConstructor(final AbstractTypeDeclaration declaration, final ASTRewrite rewrite) throws CoreException {
		Assert.isNotNull(declaration);
		Assert.isNotNull(rewrite);
		final AST ast= declaration.getAST();
		final MethodDeclaration constructor= ast.newMethodDeclaration();
		constructor.setConstructor(true);
		constructor.setName(ast.newSimpleName(declaration.getName().getIdentifier()));
		final String comment= CodeGeneration.getMethodComment(fType.getCompilationUnit(), fType.getElementName(), fType.getElementName(), getNewConstructorParameterNames(), new String[0], null, null, StubUtility.getLineDelimiterUsed(fType.getJavaProject()));
		if (comment != null && comment.length() > 0) {
			final Javadoc doc= (Javadoc) rewrite.createStringPlaceholder(comment, ASTNode.JAVADOC);
			constructor.setJavadoc(doc);
		}
		if (fCreateInstanceField) {
			final SingleVariableDeclaration variable= ast.newSingleVariableDeclaration();
			final String name= getNameForEnclosingInstanceConstructorParameter();
			variable.setName(ast.newSimpleName(name));
			variable.setType(createEnclosingType(ast));
			constructor.parameters().add(variable);
			final Block body= ast.newBlock();
			final Assignment assignment= ast.newAssignment();
			if (fCodeGenerationSettings.useKeywordThis || fEnclosingInstanceFieldName.equals(fNameForEnclosingInstanceConstructorParameter)) {
				final FieldAccess access= ast.newFieldAccess();
				access.setExpression(ast.newThisExpression());
				access.setName(ast.newSimpleName(fEnclosingInstanceFieldName));
				assignment.setLeftHandSide(access);
			} else
				assignment.setLeftHandSide(ast.newSimpleName(fEnclosingInstanceFieldName));
			assignment.setRightHandSide(ast.newSimpleName(name));
			final Statement statement= ast.newExpressionStatement(assignment);
			body.statements().add(statement);
			constructor.setBody(body);
		} else
			constructor.setBody(ast.newBlock());
		rewrite.getListRewrite(declaration, declaration.getBodyDeclarationsProperty()).insertFirst(constructor, null);
	}

	// Map<ICompilationUnit, SearchMatch[]>
	private Map createConstructorReferencesMapping(IProgressMonitor pm, RefactoringStatus status) throws JavaModelException {
		SearchResultGroup[] groups= ConstructorReferenceFinder.getConstructorReferences(fType, pm, status);
		Map result= new HashMap();
		for (int i= 0; i < groups.length; i++) {
			SearchResultGroup group= groups[i];
			ICompilationUnit cu= group.getCompilationUnit();
			if (cu == null)
				continue;
			result.put(cu, group.getSearchResults());
		}
		return result;
	}

	private Expression createEnclosingInstanceCreationString(final ASTNode node, final ICompilationUnit cu) throws JavaModelException {
		Assert.isTrue((node instanceof ClassInstanceCreation) || (node instanceof SuperConstructorInvocation));
		Assert.isNotNull(cu);
		Expression expression= null;
		if (node instanceof ClassInstanceCreation)
			expression= ((ClassInstanceCreation) node).getExpression();
		else
			expression= ((SuperConstructorInvocation) node).getExpression();
		final AST ast= node.getAST();
		if (expression != null)
			return expression;
		else if (JdtFlags.isStatic(fType))
			return null;
		else if (isInsideSubclassOfDeclaringType(node))
			return ast.newThisExpression();
		else if ((node.getStartPosition() >= fType.getSourceRange().getOffset() && ASTNodes.getExclusiveEnd(node) <= fType.getSourceRange().getOffset() + fType.getSourceRange().getLength())) {
			if (fCodeGenerationSettings.useKeywordThis || fEnclosingInstanceFieldName.equals(fNameForEnclosingInstanceConstructorParameter)) {
				final FieldAccess access= ast.newFieldAccess();
				access.setExpression(ast.newThisExpression());
				access.setName(ast.newSimpleName(fEnclosingInstanceFieldName));
				return access;
			} else
				return ast.newSimpleName(fEnclosingInstanceFieldName);
		} else if (isInsideTypeNestedInDeclaringType(node)) {
			final ThisExpression qualified= ast.newThisExpression();
			qualified.setQualifier(ast.newSimpleName(fType.getDeclaringType().getElementName()));
			return qualified;
		}
		return null;
	}

	private Type createEnclosingType(final AST ast) throws JavaModelException {
		Assert.isNotNull(ast);
		final ITypeParameter[] parameters= fType.getDeclaringType().getTypeParameters();
		final Type type= ASTNodeFactory.newType(ast, JavaModelUtil.getTypeQualifiedName(fType.getDeclaringType()));
		if (parameters.length > 0) {
			final ParameterizedType parameterized= ast.newParameterizedType(type);
			for (int index= 0; index < parameters.length; index++)
				parameterized.typeArguments().add(ast.newSimpleType(ast.newSimpleName(parameters[index].getElementName())));
			return parameterized;
		}
		return type;
	}

	private String createNewSource(final CompilationUnitRewrite targetRewrite, final ICompilationUnit unit) throws CoreException, JavaModelException {
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(unit);
		TextChange change= targetRewrite.createChange();
		if (change == null)
			change= new CompilationUnitChange("", unit); //$NON-NLS-1$
		final String source= change.getPreviewContent(new NullProgressMonitor());
		final ASTParser parser= ASTParser.newParser(AST.JLS3);
		parser.setSource(source.toCharArray());
		final AbstractTypeDeclaration declaration= findTypeDeclaration(fType, (CompilationUnit) parser.createAST(null));
		return source.substring(declaration.getStartPosition(), ASTNodes.getExclusiveEnd(declaration));
	}

	private Expression createQualifiedReadAccessExpressionForEnclosingInstance(AST ast) {
		ThisExpression expression= ast.newThisExpression();
		expression.setQualifier(ast.newName(new String[] { fType.getElementName()}));
		FieldAccess access= ast.newFieldAccess();
		access.setExpression(expression);
		access.setName(ast.newSimpleName(fEnclosingInstanceFieldName));
		return access;
	}

	private Expression createReadAccessExpressionForEnclosingInstance(AST ast) {
		if (fCodeGenerationSettings.useKeywordThis || fEnclosingInstanceFieldName.equals(fNameForEnclosingInstanceConstructorParameter)) {
			final FieldAccess access= ast.newFieldAccess();
			access.setExpression(ast.newThisExpression());
			access.setName(ast.newSimpleName(fEnclosingInstanceFieldName));
			return access;
		}
		return ast.newSimpleName(fEnclosingInstanceFieldName);
	}

	private String createSourceForNewCu(final ICompilationUnit unit, final IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(unit);
		Assert.isNotNull(monitor);
		try {
			monitor.beginTask("", 2); //$NON-NLS-1$
			final String separator= StubUtility.getLineDelimiterUsed(fType.getJavaProject());
			final String block= getAlignedSourceBlock(unit, fNewSourceOfInputType);
			String content= CodeGeneration.getCompilationUnitContent(unit, null, block, separator);
			if (content == null || block.startsWith("/*") || block.startsWith("//")) { //$NON-NLS-1$//$NON-NLS-2$
				final StringBuffer buffer= new StringBuffer();
				if (!fType.getPackageFragment().isDefaultPackage()) {
					buffer.append("package ").append(fType.getPackageFragment().getElementName()).append(';'); //$NON-NLS-1$
				}
				buffer.append(separator).append(separator);
				buffer.append(block);
				content= buffer.toString();
			}
			unit.getBuffer().setContents(content);
			addImportsToTargetUnit(unit, new SubProgressMonitor(monitor, 1));
		} finally {
			monitor.done();
		}
		return unit.getSource();
	}

	// Map<ICompilationUnit, SearchMatch[]>
	private Map createTypeReferencesMapping(IProgressMonitor pm, RefactoringStatus status) throws JavaModelException {
		final RefactoringSearchEngine2 engine= new RefactoringSearchEngine2(SearchPattern.createPattern(fType, IJavaSearchConstants.ALL_OCCURRENCES, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE));
		engine.setFiltering(true, true);
		engine.setScope(RefactoringScopeFactory.create(fType));
		engine.setStatus(status);
		engine.searchPattern(new SubProgressMonitor(pm, 1));
		final SearchResultGroup[] groups= (SearchResultGroup[]) engine.getResults();
		Map result= new HashMap();
		for (int i= 0; i < groups.length; i++) {
			SearchResultGroup group= groups[i];
			ICompilationUnit cu= group.getCompilationUnit();
			if (cu == null)
				continue;
			result.put(cu, group.getSearchResults());
		}
		return result;
	}

	private String getAlignedSourceBlock(final ICompilationUnit unit, final String block) {
		Assert.isNotNull(block);
		final String[] lines= Strings.convertIntoLines(block);
		Strings.trimIndentation(lines, unit.getJavaProject(), false);
		return Strings.concatenate(lines, StubUtility.getLineDelimiterUsed(fType.getJavaProject()));
	}

	private CompilationUnitRewrite getCompilationUnitRewrite(final ICompilationUnit unit) {
		Assert.isNotNull(unit);
		if (unit.equals(fType.getCompilationUnit()))
			return fSourceRewrite;
		return new CompilationUnitRewrite(unit);
	}

	private MethodDeclaration[] getConstructorDeclarationNodes(final AbstractTypeDeclaration declaration) {
		if (declaration instanceof TypeDeclaration) {
			final MethodDeclaration[] declarations= ((TypeDeclaration) declaration).getMethods();
			final List result= new ArrayList(2);
			for (int index= 0; index < declarations.length; index++) {
				if (declarations[index].isConstructor())
					result.add(declarations[index]);
			}
			return (MethodDeclaration[]) result.toArray(new MethodDeclaration[result.size()]);
		}
		return new MethodDeclaration[] {};
	}

	public boolean getCreateInstanceField() {
		return fCreateInstanceField;
	}

	private int getEnclosingInstanceAccessModifiers() {
		if (fMarkInstanceFieldAsFinal)
			return Modifier.PRIVATE | Modifier.FINAL;
		else
			return Modifier.PRIVATE;
	}

	public String getEnclosingInstanceName() {
		return fEnclosingInstanceFieldName;
	}

	private String getInitialNameForEnclosingInstanceField() {
		IType enclosingType= fType.getDeclaringType();
		if (enclosingType == null)
			return ""; //$NON-NLS-1$
		String[] suggestedNames= NamingConventions.suggestFieldNames(enclosingType.getJavaProject(), enclosingType.getPackageFragment().getElementName(), JavaModelUtil.getTypeQualifiedName(fType.getDeclaringType()), 0, getEnclosingInstanceAccessModifiers(), getFieldNames(fType));
		if (suggestedNames.length > 0)
			return suggestedNames[0];
		String name= enclosingType.getElementName();
		if (name.equals("")) //$NON-NLS-1$
			return ""; //$NON-NLS-1$
		return Character.toLowerCase(name.charAt(0)) + name.substring(1);
	}

	public IType getInputType() {
		return fType;
	}

	/*
	 * @see org.eclipse.jdt.internal.corext.refactoring.base.IRefactoring#getName()
	 */
	public String getName() {
		return RefactoringCoreMessages.MoveInnerToTopRefactoring_name; 
	}

	private String getNameForEnclosingInstanceConstructorParameter() throws JavaModelException {
		if (fNameForEnclosingInstanceConstructorParameter != null)
			return fNameForEnclosingInstanceConstructorParameter;

		IType enclosingType= fType.getDeclaringType();
		String[] suggestedNames= NamingConventions.suggestArgumentNames(enclosingType.getJavaProject(), enclosingType.getPackageFragment().getElementName(), JavaModelUtil.getTypeQualifiedName(fType.getDeclaringType()), 0, getParameterNamesOfAllConstructors(fType));
		if (suggestedNames.length > 0)
			fNameForEnclosingInstanceConstructorParameter= suggestedNames[0];
		else
			fNameForEnclosingInstanceConstructorParameter= fEnclosingInstanceFieldName;
		return fNameForEnclosingInstanceConstructorParameter;
	}

	private String[] getNewConstructorParameterNames() throws JavaModelException {
		if (!fCreateInstanceField)
			return new String[0];
		return new String[] { getNameForEnclosingInstanceConstructorParameter()};
	}

	private ASTNode getNewQualifiedNameNode(ITypeBinding[] parameters, Name name) {
		final AST ast= name.getAST();
		boolean raw= false;
		final ITypeBinding binding= name.resolveTypeBinding();
		if (binding != null && binding.isRawType())
			raw= true;
		if (parameters != null && parameters.length > 0 && !raw) {
			final ParameterizedType type= ast.newParameterizedType(ast.newSimpleType(ast.newName(fTypeComponents)));
			for (int index= 0; index < parameters.length; index++)
				type.typeArguments().add(ast.newSimpleType(ast.newSimpleName(parameters[index].getName())));
			return type;
		}
		return ast.newName(fTypeComponents);
	}

	private ASTNode getNewUnqualifiedTypeNode(ITypeBinding[] parameters, Name name) {
		final AST ast= name.getAST();
		boolean raw= false;
		final ITypeBinding binding= name.resolveTypeBinding();
		if (binding != null && binding.isRawType())
			raw= true;
		if (parameters != null && parameters.length > 0 && !raw) {
			final ParameterizedType type= ast.newParameterizedType(ast.newSimpleType(ast.newSimpleName(fType.getElementName())));
			for (int index= 0; index < parameters.length; index++)
				type.typeArguments().add(ast.newSimpleType(ast.newSimpleName(parameters[index].getName())));
			return type;
		}
		return ast.newSimpleType(ast.newSimpleName(fType.getElementName()));
	}

	private boolean insertExpressionAsParameter(ClassInstanceCreation cic, ASTRewrite rewrite, ICompilationUnit cu, TextEditGroup group) throws JavaModelException {
		final Expression expression= createEnclosingInstanceCreationString(cic, cu);
		if (expression == null)
			return false;
		rewrite.getListRewrite(cic, ClassInstanceCreation.ARGUMENTS_PROPERTY).insertFirst(expression, group);
		return true;
	}

	private boolean insertExpressionAsParameter(SuperConstructorInvocation sci, ASTRewrite rewrite, ICompilationUnit cu, TextEditGroup group) throws JavaModelException {
		final Expression expression= createEnclosingInstanceCreationString(sci, cu);
		if (expression == null)
			return false;
		rewrite.getListRewrite(sci, SuperConstructorInvocation.ARGUMENTS_PROPERTY).insertFirst(expression, group);
		return true;
	}

	public boolean isCreatingInstanceFieldMandatory() {
		return fIsInstanceFieldCreationMandatory;
	}

	public boolean isCreatingInstanceFieldPossible() {
		return fIsInstanceFieldCreationPossible;
	}

	private boolean isInAnonymousTypeInsideInputType(ASTNode node, AbstractTypeDeclaration declaration) {
		final AnonymousClassDeclaration anonymous= (AnonymousClassDeclaration) ASTNodes.getParent(node, AnonymousClassDeclaration.class);
		return anonymous != null && ASTNodes.isParent(anonymous, declaration);
	}

	private boolean isInLocalTypeInsideInputType(ASTNode node, AbstractTypeDeclaration declaration) {
		final TypeDeclarationStatement statement= (TypeDeclarationStatement) ASTNodes.getParent(node, TypeDeclarationStatement.class);
		return statement != null && ASTNodes.isParent(statement, declaration);
	}

	private boolean isInNonStaticMemberTypeInsideInputType(ASTNode node, AbstractTypeDeclaration declaration) {
		final AbstractTypeDeclaration nested= (AbstractTypeDeclaration) ASTNodes.getParent(node, AbstractTypeDeclaration.class);
		return nested != null && !declaration.equals(nested) && !Modifier.isStatic(nested.getFlags()) && ASTNodes.isParent(nested, declaration);
	}

	private boolean isInsideSubclassOfDeclaringType(ASTNode node) {
		Assert.isTrue((node instanceof ClassInstanceCreation) || (node instanceof SuperConstructorInvocation));
		final AbstractTypeDeclaration declaration= (AbstractTypeDeclaration) ASTNodes.getParent(node, AbstractTypeDeclaration.class);
		Assert.isNotNull(declaration);

		final AnonymousClassDeclaration anonymous= (AnonymousClassDeclaration) ASTNodes.getParent(node, AnonymousClassDeclaration.class);
		boolean isAnonymous= anonymous != null && ASTNodes.isParent(anonymous, declaration);
		if (isAnonymous)
			return isSubclassBindingOfEnclosingType(anonymous.resolveBinding());
		return isSubclassBindingOfEnclosingType(declaration.resolveBinding());
	}

	private boolean isInsideTypeNestedInDeclaringType(ASTNode node) {
		Assert.isTrue((node instanceof ClassInstanceCreation) || (node instanceof SuperConstructorInvocation));
		final AbstractTypeDeclaration declaration= (AbstractTypeDeclaration) ASTNodes.getParent(node, AbstractTypeDeclaration.class);
		Assert.isNotNull(declaration);
		ITypeBinding enclosing= declaration.resolveBinding();
		while (enclosing != null) {
			if (isCorrespondingTypeBinding(enclosing, fType.getDeclaringType()))
				return true;
			enclosing= enclosing.getDeclaringClass();
		}
		return false;
	}

	private boolean isInstanceFieldCreationMandatory() throws JavaModelException {
		final MemberAccessNodeCollector collector= new MemberAccessNodeCollector(fType.getDeclaringType().newSupertypeHierarchy(new NullProgressMonitor()));
		findTypeDeclaration(fType, fSourceRewrite.getRoot()).accept(collector);
		return containsNonStatic(collector.getFieldAccesses()) || containsNonStatic(collector.getMethodInvocations()) || containsNonStatic(collector.getSimpleFieldNames());
	}

	public boolean isInstanceFieldMarkedFinal() {
		return fMarkInstanceFieldAsFinal;
	}

	private boolean isSubclassBindingOfEnclosingType(ITypeBinding binding) {
		while (binding != null) {
			if (isCorrespondingTypeBinding(binding, fType.getDeclaringType()))
				return true;
			binding= binding.getSuperclass();
		}
		return false;
	}

	private void modifyAccessToEnclosingInstance(final CompilationUnitRewrite targetRewrite, final AbstractTypeDeclaration declaration, final RefactoringStatus status, final IProgressMonitor monitor) throws JavaModelException {
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(declaration);
		Assert.isNotNull(monitor);
		final Set handledMethods= new HashSet();
		final Set handledFields= new HashSet();
		final MemberAccessNodeCollector collector= new MemberAccessNodeCollector(fType.getDeclaringType().newSupertypeHierarchy(new SubProgressMonitor(monitor, 1)));
		declaration.accept(collector);
		modifyAccessToMethodsFromEnclosingInstance(targetRewrite, handledMethods, collector.getMethodInvocations(), declaration, status);
		modifyAccessToFieldsFromEnclosingInstance(targetRewrite, handledFields, collector.getFieldAccesses(), declaration, status);
		modifyAccessToFieldsFromEnclosingInstance(targetRewrite, handledFields, collector.getSimpleFieldNames(), declaration, status);
	}

	private void modifyAccessToFieldsFromEnclosingInstance(CompilationUnitRewrite targetRewrite, Set handledFields, FieldAccess[] fieldAccesses, AbstractTypeDeclaration declaration, RefactoringStatus status) {
		FieldAccess access= null;
		for (int index= 0; index < fieldAccesses.length; index++) {
			access= fieldAccesses[index];
			Assert.isNotNull(access.getExpression());
			if (!(access.getExpression() instanceof ThisExpression) || (!(((ThisExpression) access.getExpression()).getQualifier() != null)))
				continue;

			final IVariableBinding binding= access.resolveFieldBinding();
			if (binding != null) {
				modifyFieldVisibility(targetRewrite, handledFields, binding, status);
				targetRewrite.getASTRewrite().replace(access.getExpression(), createAccessExpressionToEnclosingInstanceFieldText(access, binding, declaration), null);
				targetRewrite.getImportRemover().registerRemovedNode(access.getExpression());
			}
		}
	}

	private void modifyAccessToFieldsFromEnclosingInstance(CompilationUnitRewrite targetRewrite, Set handledFields, SimpleName[] simpleNames, AbstractTypeDeclaration declaration, RefactoringStatus status) {
		IBinding binding= null;
		SimpleName simpleName= null;
		IVariableBinding variable= null;
		for (int index= 0; index < simpleNames.length; index++) {
			simpleName= simpleNames[index];
			binding= simpleName.resolveBinding();
			if (binding != null && binding instanceof IVariableBinding && !(simpleName.getParent() instanceof FieldAccess)) {
				variable= (IVariableBinding) binding;
				modifyFieldVisibility(targetRewrite, handledFields, variable, status);
				final FieldAccess access= simpleName.getAST().newFieldAccess();
				access.setExpression(createAccessExpressionToEnclosingInstanceFieldText(simpleName, variable, declaration));
				access.setName(simpleName.getAST().newSimpleName(simpleName.getIdentifier()));
				targetRewrite.getASTRewrite().replace(simpleName, access, null);
				targetRewrite.getImportRemover().registerRemovedNode(simpleName);
			}
		}
	}

	private void modifyAccessToMethodsFromEnclosingInstance(CompilationUnitRewrite targetRewrite, Set handledMethods, MethodInvocation[] methodInvocations, AbstractTypeDeclaration declaration, RefactoringStatus status) {
		IMethodBinding binding= null;
		MethodInvocation invocation= null;
		for (int index= 0; index < methodInvocations.length; index++) {
			invocation= methodInvocations[index];
			binding= invocation.resolveMethodBinding();
			if (binding != null) {
				final ASTNode node= fSourceRewrite.getRoot().findDeclaringNode(binding);
				modifyMethodVisibility(handledMethods, node, status);
				final Expression target= invocation.getExpression();
				if (target == null) {
					final Expression expression= createAccessExpressionToEnclosingInstanceFieldText(invocation, binding, declaration);
					targetRewrite.getASTRewrite().set(invocation, MethodInvocation.EXPRESSION_PROPERTY, expression, null);
				} else {
					if (!(invocation.getExpression() instanceof ThisExpression) || !(((ThisExpression) invocation.getExpression()).getQualifier() != null))
						continue;
					targetRewrite.getASTRewrite().replace(target, createAccessExpressionToEnclosingInstanceFieldText(invocation, binding, declaration), null);
					targetRewrite.getImportRemover().registerRemovedNode(target);
				}
			}
		}
	}

	private void modifyConstructors(AbstractTypeDeclaration declaration, ASTRewrite rewrite) throws CoreException {
		final MethodDeclaration[] declarations= getConstructorDeclarationNodes(declaration);
		for (int index= 0; index < declarations.length; index++) {
			Assert.isTrue(declarations[index].isConstructor());
			addParameterToConstructor(rewrite, declarations[index]);
			setEnclosingInstanceFieldInConstructor(rewrite, declarations[index]);
		}
	}

	private void modifyEnclosingClassModifiers(final RefactoringStatus status, final ITypeBinding binding, final TextEditGroup group) {
		final ITypeBinding declaring= binding.getDeclaringClass();
		if (declaring != null && !declaring.isInterface() && Modifier.isStatic(binding.getModifiers()) && Modifier.isPrivate(binding.getModifiers())) {
			final ASTNode node= ASTNodes.findDeclaration(binding, fSourceRewrite.getRoot());
			if (node instanceof AbstractTypeDeclaration) {
				final AbstractTypeDeclaration declaration= (AbstractTypeDeclaration) node;
				ModifierRewrite.create(fSourceRewrite.getASTRewrite(), declaration).setModifiers(0, Modifier.PRIVATE, group);
				final RefactoringStatusEntry entry= new RefactoringStatusEntry(RefactoringStatus.WARNING, Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_change_visibility_type_warning, new String[] { BindingLabels.getFullyQualified(binding)}), JavaStatusContext.create(fSourceRewrite.getCu(), node)); 
				if (!containsStatusEntry(status, entry))
					status.addEntry(entry);
			}
		}
		if (declaring != null)
			modifyEnclosingClassModifiers(status, declaring, group);
	}

	private void modifyFieldVisibility(CompilationUnitRewrite targetRewrite, Set handledFields, IVariableBinding variable, RefactoringStatus status) {
		if (!handledFields.contains(variable.getKey()) && variable.isField() && Modifier.isPrivate(variable.getModifiers())) {
			final ASTNode node= fSourceRewrite.getRoot().findDeclaringNode(variable);
			if (node instanceof VariableDeclarationFragment) {
				final VariableDeclarationFragment fragment= (VariableDeclarationFragment) node;
				final FieldDeclaration declaration= (FieldDeclaration) fragment.getParent();
				final ASTRewrite rewrite= targetRewrite.getASTRewrite();
				if (declaration.fragments().size() <= 1)
					ModifierRewrite.create(rewrite, declaration).setModifiers(0, Modifier.PRIVATE, targetRewrite.createGroupDescription(RefactoringCoreMessages.MoveInnerToTopRefactoring_change_visibility)); 
				else {
					final AST ast= declaration.getAST();
					final Type newType= (Type) ASTNode.copySubtree(ast, declaration.getType());
					final VariableDeclarationFragment newFragment= ast.newVariableDeclarationFragment();
					newFragment.setName(ast.newSimpleName(variable.getName()));
					final FieldDeclaration newDeclaration= ast.newFieldDeclaration(newFragment);
					newDeclaration.setType(newType);
					final AbstractTypeDeclaration typeDeclaration= (AbstractTypeDeclaration) declaration.getParent();
					rewrite.getListRewrite(typeDeclaration, typeDeclaration.getBodyDeclarationsProperty()).insertAfter(newDeclaration, declaration, null);
					rewrite.getListRewrite(declaration, FieldDeclaration.FRAGMENTS_PROPERTY).remove(fragment, targetRewrite.createGroupDescription(RefactoringCoreMessages.MoveInnerToTopRefactoring_change_visibility)); 
				}
				final RefactoringStatusEntry entry= new RefactoringStatusEntry(RefactoringStatus.WARNING, Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_change_visibility_field_warning, new String[] { BindingLabels.getFullyQualified(variable)}), JavaStatusContext.create(fSourceRewrite.getCu(), node)); 
				if (!containsStatusEntry(status, entry))
					status.addEntry(entry);
			}
		}
	}

	private void modifyInterfaceMemberModifiers(final ITypeBinding binding) {
		Assert.isNotNull(binding);
		ITypeBinding declaring= binding.getDeclaringClass();
		while (declaring != null && !declaring.isInterface()) {
			declaring= declaring.getDeclaringClass();
		}
		if (declaring != null) {
			final ASTNode node= ASTNodes.findDeclaration(binding, fSourceRewrite.getRoot());
			if (node instanceof AbstractTypeDeclaration) {
				ModifierRewrite.create(fSourceRewrite.getASTRewrite(), node).setVisibility(Modifier.PUBLIC, null);
			}
		}
	}

	private void modifyMethodVisibility(final Set handledMethods, final ASTNode node, final RefactoringStatus status) {
		if (!handledMethods.contains(node) && node instanceof MethodDeclaration) {
			handledMethods.add(node);
			final MethodDeclaration declaration= (MethodDeclaration) node;
			if (Modifier.isPrivate(declaration.getModifiers())) {
				ModifierRewrite.create(fSourceRewrite.getASTRewrite(), declaration).setModifiers(0, Modifier.PRIVATE, fSourceRewrite.createGroupDescription(RefactoringCoreMessages.MoveInnerToTopRefactoring_change_visibility)); 
				final IMethodBinding binding= declaration.resolveBinding();
				if (binding != null) {
					final RefactoringStatusEntry entry= new RefactoringStatusEntry(RefactoringStatus.WARNING, Messages.format(RefactoringCoreMessages.MoveInnerToTopRefactoring_change_visibility_method_warning, new String[] { BindingLabels.getFullyQualified(binding)}), JavaStatusContext.create(fSourceRewrite.getCu(), declaration)); 
					if (!containsStatusEntry(status, entry))
						status.addEntry(entry);
				}
			}
		}
	}

	public void setCreateInstanceField(boolean create) {
		Assert.isTrue(fIsInstanceFieldCreationPossible);
		Assert.isTrue(!fIsInstanceFieldCreationMandatory);
		fCreateInstanceField= create;
	}

	private void setEnclosingInstanceFieldInConstructor(ASTRewrite rewrite, MethodDeclaration decl) throws JavaModelException {
		final AST ast= decl.getAST();
		final Block body= decl.getBody();
		final List statements= body.statements();
		if (statements.isEmpty()) {
			final Assignment assignment= ast.newAssignment();
			assignment.setLeftHandSide(createReadAccessExpressionForEnclosingInstance(ast));
			assignment.setRightHandSide(ast.newSimpleName(getNameForEnclosingInstanceConstructorParameter()));
			rewrite.getListRewrite(body, Block.STATEMENTS_PROPERTY).insertFirst(ast.newExpressionStatement(assignment), null);
		} else {
			final Statement first= (Statement) statements.get(0);
			if (first instanceof ConstructorInvocation) {
				rewrite.getListRewrite(first, ConstructorInvocation.ARGUMENTS_PROPERTY).insertFirst(ast.newSimpleName(fEnclosingInstanceFieldName), null);
			} else {
				int index= 0;
				if (first instanceof SuperConstructorInvocation)
					index++;
				final Assignment assignment= ast.newAssignment();
				assignment.setLeftHandSide(createReadAccessExpressionForEnclosingInstance(ast));
				assignment.setRightHandSide(ast.newSimpleName(getNameForEnclosingInstanceConstructorParameter()));
				rewrite.getListRewrite(body, Block.STATEMENTS_PROPERTY).insertAt(ast.newExpressionStatement(assignment), index, null);
			}
		}
	}

	public void setEnclosingInstanceName(String name) {
		Assert.isNotNull(name);
		fEnclosingInstanceFieldName= name;
	}

	public void setMarkInstanceFieldAsFinal(boolean mark) {
		fMarkInstanceFieldAsFinal= mark;
	}

	private void updateConstructorReference(final ClassInstanceCreation creation, final CompilationUnitRewrite targetRewrite, final ICompilationUnit unit, TextEditGroup group) throws JavaModelException {
		Assert.isNotNull(creation);
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(unit);
		final ASTRewrite rewrite= targetRewrite.getASTRewrite();
		if (fCreateInstanceField)
			insertExpressionAsParameter(creation, rewrite, unit, group);
		final Expression expression= creation.getExpression();
		if (expression != null) {
			rewrite.remove(expression, null);
			targetRewrite.getImportRemover().registerRemovedNode(expression);
		}
	}

	private void updateConstructorReference(ITypeBinding[] parameters, ASTNode reference, CompilationUnitRewrite targetRewrite, ICompilationUnit cu) throws CoreException {
		final TextEditGroup group= targetRewrite.createGroupDescription(RefactoringCoreMessages.MoveInnerToTopRefactoring_update_constructor_reference); 
		if (reference instanceof SuperConstructorInvocation)
			updateConstructorReference((SuperConstructorInvocation) reference, targetRewrite, cu, group);
		else if (reference instanceof ClassInstanceCreation)
			updateConstructorReference((ClassInstanceCreation) reference, targetRewrite, cu, group);
		else if (reference.getParent() instanceof ClassInstanceCreation)
			updateConstructorReference((ClassInstanceCreation) reference.getParent(), targetRewrite, cu, group);
		else if (reference.getParent() instanceof ParameterizedType && reference.getParent().getParent() instanceof ClassInstanceCreation)
			updateConstructorReference(parameters, (ParameterizedType) reference.getParent(), targetRewrite, cu, group);
	}

	private void updateConstructorReference(ITypeBinding[] parameters, ParameterizedType type, CompilationUnitRewrite targetRewrite, ICompilationUnit cu, TextEditGroup group) throws CoreException {
		final ListRewrite rewrite= targetRewrite.getASTRewrite().getListRewrite(type, ParameterizedType.TYPE_ARGUMENTS_PROPERTY);
		TypeParameter parameter= null;
		for (int index= type.typeArguments().size(); index < parameters.length; index++) {
			parameter= targetRewrite.getRoot().getAST().newTypeParameter();
			parameter.setName(targetRewrite.getRoot().getAST().newSimpleName(parameters[index].getName()));
			rewrite.insertLast(parameter, group);
		}
		if (type.getParent() instanceof ClassInstanceCreation)
			updateConstructorReference((ClassInstanceCreation) type.getParent(), targetRewrite, cu, group);
	}

	private void updateConstructorReference(final SuperConstructorInvocation invocation, final CompilationUnitRewrite targetRewrite, final ICompilationUnit unit, TextEditGroup group) throws CoreException {
		Assert.isNotNull(invocation);
		Assert.isNotNull(targetRewrite);
		Assert.isNotNull(unit);
		final ASTRewrite rewrite= targetRewrite.getASTRewrite();
		if (fCreateInstanceField)
			insertExpressionAsParameter(invocation, rewrite, unit, group);
		final Expression expression= invocation.getExpression();
		if (expression != null) {
			rewrite.remove(expression, null);
			targetRewrite.getImportRemover().registerRemovedNode(expression);
		}
	}

	private boolean updateNameReference(ITypeBinding[] parameters, Name name, CompilationUnitRewrite targetRewrite, TextEditGroup group) {
		if (ASTNodes.asString(name).equals(fType.getFullyQualifiedName('.'))) {
			targetRewrite.getASTRewrite().replace(name, getNewQualifiedNameNode(parameters, name), group);
			targetRewrite.getImportRemover().registerRemovedNode(name);
			return true;
		}
		targetRewrite.getASTRewrite().replace(name, getNewUnqualifiedTypeNode(parameters, name), group);
		targetRewrite.getImportRemover().registerRemovedNode(name);
		return true;
	}

	private boolean updateParameterizedTypeReference(ITypeBinding[] parameters, ParameterizedType type, CompilationUnitRewrite targetRewrite, TextEditGroup group) {
		if (!(type.getParent() instanceof ClassInstanceCreation)) {
			final ListRewrite rewrite= targetRewrite.getASTRewrite().getListRewrite(type, ParameterizedType.TYPE_ARGUMENTS_PROPERTY);
			final AST ast= targetRewrite.getRoot().getAST();
			Type simpleType= null;
			for (int index= type.typeArguments().size(); index < parameters.length; index++) {
				simpleType= ast.newSimpleType(ast.newSimpleName(parameters[index].getName()));
				rewrite.insertLast(simpleType, group);
			}
		}
		return true;
	}

	private boolean updateReference(ITypeBinding[] parameters, ASTNode node, CompilationUnitRewrite rewrite, TextEditGroup group) {
		if (node.getLocationInParent() == ParameterizedType.TYPE_PROPERTY) {
			updateParameterizedTypeReference(parameters, (ParameterizedType) node.getParent(), rewrite, group);
			return updateNameReference(new ITypeBinding[] {}, ((SimpleType) node).getName(), rewrite, group);
		} else if (node instanceof QualifiedName)
			return updateNameReference(parameters, (QualifiedName) node, rewrite, group);
		else if (node instanceof SimpleType)
			return updateNameReference(parameters, ((SimpleType) node).getName(), rewrite, group);
		else
			return false;
	}

	private void updateReferenceInImport(ImportDeclaration enclosingImport, ASTNode node, CompilationUnitRewrite rewrite) throws CoreException {
		final IBinding binding= enclosingImport.resolveBinding();
		if (binding instanceof ITypeBinding) {
			final ITypeBinding type= (ITypeBinding) binding;
			final ImportRewrite rewriter= rewrite.getImportRewrite();
			if (enclosingImport.isStatic()) {
				final String oldImport= ASTNodes.asString(node);
				final StringBuffer buffer= new StringBuffer(oldImport);
				final String typeName= fType.getDeclaringType().getElementName();
				final int index= buffer.indexOf(typeName);
				if (index >= 0) {
					buffer.delete(index, index + typeName.length() + 1);
					final String newImport= buffer.toString();
					if (enclosingImport.isOnDemand()) {
						rewriter.removeStaticImport(oldImport + ".*"); //$NON-NLS-1$
						rewriter.addStaticImport(newImport, "*", false); //$NON-NLS-1$
					} else {
						rewriter.removeStaticImport(oldImport);
						final int offset= newImport.lastIndexOf('.');
						if (offset >= 0 && offset < newImport.length() - 1) {
							rewriter.addStaticImport(newImport.substring(0, offset), newImport.substring(offset + 1), false);
						}
					}
				}
			} else
				rewriter.removeImport(type);
		}
	}

	private void updateTypeReference(ITypeBinding[] parameters, ASTNode node, CompilationUnitRewrite rewrite, ICompilationUnit cu) throws CoreException {
		ImportDeclaration enclosingImport= (ImportDeclaration) ASTNodes.getParent(node, ImportDeclaration.class);
		if (enclosingImport != null)
			updateReferenceInImport(enclosingImport, node, rewrite);
		else {
			final TextEditGroup group= rewrite.createGroupDescription(RefactoringCoreMessages.MoveInnerToTopRefactoring_update_type_reference); 
			updateReference(parameters, node, rewrite, group);
			if (!fType.getPackageFragment().equals(cu.getParent())) {
				final String name= fType.getPackageFragment().getElementName() + '.' + fType.getElementName();
				rewrite.getImportRemover().registerAddedImport(name);
				rewrite.getImportRewrite().addImport(name);
			}
		}
	}
}
