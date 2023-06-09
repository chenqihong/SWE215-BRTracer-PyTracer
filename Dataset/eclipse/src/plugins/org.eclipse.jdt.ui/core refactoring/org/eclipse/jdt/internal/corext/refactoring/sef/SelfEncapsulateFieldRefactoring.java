/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     jens.lukowski@gmx.de - contributed code to convert prefix and postfix 
 *       expressions into a combination of setter and getter calls.
 *     Dmitry Stalnov (dstalnov@fusionone.com) - contributed fix for
 *       bug Encapsulate field can fail when two variables in one variable declaration (see
 *       https://bugs.eclipse.org/bugs/show_bug.cgi?id=51540).
 *******************************************************************************/
package org.eclipse.jdt.internal.corext.refactoring.sef;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IFile;

import org.eclipse.core.filebuffers.ITextFileBuffer;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.jface.text.TextUtilities;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnonymousClassDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.ChildListPropertyDescriptor;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.IVariableBinding;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.Message;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.jdt.ui.CodeGeneration;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.codemanipulation.GetterSetterUtil;
import org.eclipse.jdt.internal.corext.dom.ASTNodeFactory;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.ModifierRewrite;
import org.eclipse.jdt.internal.corext.dom.NodeFinder;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationStateChange;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringFileBuffers;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.viewsupport.BindingLabels;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextChange;

/**
 * Encapsulates a field into getter and setter calls.
 */
public class SelfEncapsulateFieldRefactoring extends Refactoring {

	private IField fField;
	private TextChangeManager fChangeManager;
	
	private CompilationUnit fRoot;
	private VariableDeclarationFragment fFieldDeclaration;
	private ASTRewrite fRewriter;

	private int fVisibility;
	private String fGetterName;
	private String fSetterName;
	private String fArgName;
	private boolean fSetterMustReturnValue;
	private int fInsertionIndex;	// -1 represents as first method.
	private boolean fEncapsulateDeclaringClass;
	private boolean fGenerateJavadoc;
	
	private List fUsedReadNames;
	private List fUsedModifyNames;
	
	private static final String NO_NAME= ""; //$NON-NLS-1$
	
	private SelfEncapsulateFieldRefactoring(IField field) throws JavaModelException {
		Assert.isNotNull(field);
		fField= field;
		fChangeManager= new TextChangeManager();
		fGetterName= GetterSetterUtil.getGetterName(field, null);
		fSetterName= GetterSetterUtil.getSetterName(field, null);
		fEncapsulateDeclaringClass= true;
		fArgName= NamingConventions.removePrefixAndSuffixForFieldName(field.getJavaProject(), field.getElementName(), field.getFlags());
		checkArgName();
	}
	
	public static SelfEncapsulateFieldRefactoring create(IField field) throws JavaModelException {
		if (Checks.checkAvailability(field).hasFatalError() || !RefactoringAvailabilityTester.isSelfEncapsulateAvailable(field))
			return null;
		return new SelfEncapsulateFieldRefactoring(field);
	}
	
	//---- Setter and Getter methods ----------------------------------------------------------
	
	public IField getField() {
		return fField;
	}

	public String getGetterName() {
		return fGetterName;
	}
		
	public void setGetterName(String name) {
		fGetterName= name;
		Assert.isNotNull(fGetterName);
	}

	public String getSetterName() {
		return fSetterName;
	}
	
	public void setSetterName(String name) {
		fSetterName= name;
		Assert.isNotNull(fSetterName);
	}
	
	public void setInsertionIndex(int index) {
		fInsertionIndex= index;
	}
	
	public int getVisibility() {
		return fVisibility;
	}
	
	public void setVisibility(int visibility) {
		fVisibility= visibility;
	}
	
	public void setEncapsulateDeclaringClass(boolean encapsulateDeclaringClass) {
		fEncapsulateDeclaringClass= encapsulateDeclaringClass;
	}

	public boolean getEncapsulateDeclaringClass() {
		return fEncapsulateDeclaringClass;
	}
	
	public boolean getGenerateJavadoc() {
		return fGenerateJavadoc;
	}
	
	public void setGenerateJavadoc(boolean value) {
		fGenerateJavadoc= value;
	}

	//----activation checking ----------------------------------------------------------
	
	/*
	 * @see Refactoring#checkActivation(IProgressMonitor)
	 */
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException {
		fVisibility= (fField.getFlags() & (Flags.AccPublic | Flags.AccProtected | Flags.AccPrivate));
		RefactoringStatus result=  new RefactoringStatus();
		
		fRoot= new RefactoringASTParser(AST.JLS3).parse(fField.getCompilationUnit(), true, pm);
		ISourceRange sourceRange= fField.getNameRange();
		ASTNode node= NodeFinder.perform(fRoot, sourceRange.getOffset(), sourceRange.getLength());
		if (node == null) {
			return mappingErrorFound(result, node);
		}
		fFieldDeclaration= (VariableDeclarationFragment)ASTNodes.getParent(node, VariableDeclarationFragment.class);
		if (fFieldDeclaration == null) {
			return mappingErrorFound(result, node);
		}
		if (fFieldDeclaration.resolveBinding() == null) {
			if (!processCompilerError(result, node))
				result.addFatalError(RefactoringCoreMessages.SelfEncapsulateField_type_not_resolveable); 
			return result;
		}
		computeUsedNames();
		fRewriter= ASTRewrite.create(fRoot.getAST());
		return result;
	}

	private RefactoringStatus mappingErrorFound(RefactoringStatus result, ASTNode node) {
		if (node != null && (node.getFlags() & ASTNode.MALFORMED) != 0 && processCompilerError(result, node))
			return result;
		result.addFatalError(getMappingErrorMessage());
		return result;
	}

	private boolean processCompilerError(RefactoringStatus result, ASTNode node) {
		Message[] messages= ASTNodes.getMessages(node, ASTNodes.INCLUDE_ALL_PARENTS);
		if (messages.length == 0)
			return false;
		result.addFatalError(Messages.format(
			RefactoringCoreMessages.SelfEncapsulateField_compiler_errors_field,  //$NON-NLS-1$
			new String[] { fField.getElementName(), messages[0].getMessage()}));
		return true;
	}

	private String getMappingErrorMessage() {
		return Messages.format(
			RefactoringCoreMessages.SelfEncapsulateField_cannot_analyze_selected_field, //$NON-NLS-1$
			new String[] {fField.getElementName()});
	}

	//---- Input checking ----------------------------------------------------------
	
	public RefactoringStatus checkMethodNames() {
		RefactoringStatus result= new RefactoringStatus();
		IType declaringType= fField.getDeclaringType();
		checkName(result, fGetterName, fUsedReadNames, declaringType);
		checkName(result, fSetterName, fUsedModifyNames, declaringType);
		return result;
	}
	
	private static void checkName(RefactoringStatus status, String name, List usedNames, IType type) {
		if ("".equals(name)) { //$NON-NLS-1$
			status.addFatalError(RefactoringCoreMessages.Checks_Choose_name); 
			return;
	    }
		status.merge(Checks.checkMethodName(name));
		for (Iterator iter= usedNames.iterator(); iter.hasNext(); ) {
			IMethodBinding method= (IMethodBinding)iter.next();
			String selector= method.getName();
			if (selector.equals(name))
				status.addFatalError(Messages.format(
					RefactoringCoreMessages.SelfEncapsulateField_method_exists, //$NON-NLS-1$
					new String[] {BindingLabels.getFullyQualified(method), type.getElementName()}));
		}
	}	
	
	/*
	 * @see Refactoring#checkInput(IProgressMonitor)
	 */
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException {
		RefactoringStatus result= new RefactoringStatus();
		fChangeManager.clear();
		pm.beginTask(NO_NAME, 11);
		pm.setTaskName(RefactoringCoreMessages.SelfEncapsulateField_checking_preconditions); 
		result.merge(checkMethodNames());
		pm.worked(1);
		if (result.hasFatalError())
			return result;
		pm.setTaskName(RefactoringCoreMessages.SelfEncapsulateField_searching_for_cunits); 
		ICompilationUnit[] affectedCUs= RefactoringSearchEngine.findAffectedCompilationUnits(
			SearchPattern.createPattern(fField, IJavaSearchConstants.REFERENCES),
			RefactoringScopeFactory.create(fField),
			new SubProgressMonitor(pm, 5),
			result);
		
		checkInHierarchy(result);
		if (result.hasFatalError())
			return result;
			
		pm.setTaskName(RefactoringCoreMessages.SelfEncapsulateField_analyzing);	 
		IProgressMonitor sub= new SubProgressMonitor(pm, 5);
		sub.beginTask(NO_NAME, affectedCUs.length);
		IVariableBinding fieldIdentifier= fFieldDeclaration.resolveBinding();
		ITypeBinding declaringClass= 
			((AbstractTypeDeclaration)ASTNodes.getParent(fFieldDeclaration, AbstractTypeDeclaration.class)).resolveBinding();
		List ownerDescriptions= new ArrayList();
		ICompilationUnit owner= fField.getCompilationUnit();
		for (int i= 0; i < affectedCUs.length; i++) {
			ICompilationUnit unit= affectedCUs[i];
			sub.subTask(unit.getElementName());
			CompilationUnit root= null;
			ASTRewrite rewriter= null;
			List descriptions;
			if (owner.equals(unit)) {
				root= fRoot;
				rewriter= fRewriter;
				descriptions= ownerDescriptions;
			} else {
				root= new RefactoringASTParser(AST.JLS3).parse(unit, true);
				rewriter= ASTRewrite.create(root.getAST());
				descriptions= new ArrayList();
			}
			checkCompileErrors(result, root, unit);
			AccessAnalyzer analyzer= new AccessAnalyzer(this, unit, fieldIdentifier, declaringClass, rewriter);
			root.accept(analyzer);
			result.merge(analyzer.getStatus());
			if (!fSetterMustReturnValue) 
				fSetterMustReturnValue= analyzer.getSetterMustReturnValue();
			if (result.hasFatalError()) {
				fChangeManager.clear();
				return result;
			}
			descriptions.addAll(analyzer.getGroupDescriptions());
			if (!owner.equals(unit))
				createEdits(unit, rewriter, descriptions);
			sub.worked(1);
			if (pm.isCanceled())
				throw new OperationCanceledException();
		}
		try {
			ITextFileBuffer buffer= RefactoringFileBuffers.acquire(owner);
			ownerDescriptions.addAll(addGetterSetterChanges(fRoot, fRewriter, TextUtilities.getDefaultLineDelimiter(buffer.getDocument())));
			createEdits(owner, fRewriter, ownerDescriptions, buffer);
		} finally {
			RefactoringFileBuffers.release(owner);
		}
		sub.done();
		result.merge(validateModifiesFiles());
		return result;
	}

	private void createEdits(ICompilationUnit unit, ASTRewrite rewriter, List groups) throws CoreException {
		try {
		ITextFileBuffer buffer= RefactoringFileBuffers.acquire(unit);
			createEdits(unit, rewriter, groups, buffer);
		} finally {
			RefactoringFileBuffers.release(unit);
		}
	}

	private void createEdits(ICompilationUnit unit, ASTRewrite rewriter, List groups, ITextFileBuffer buffer) {
		TextChange change= fChangeManager.get(unit);
		change.setEdit(rewriter.rewriteAST(buffer.getDocument(), fField.getJavaProject().getOptions(true)));
		for (Iterator iter= groups.iterator(); iter.hasNext();) {
			change.addTextEditGroup((TextEditGroup)iter.next());
		}
	}

	/*
	 * @see IRefactoring#createChange(IProgressMonitor)
	 */
	public Change createChange(IProgressMonitor pm) throws CoreException {
		final DynamicValidationStateChange result= new DynamicValidationStateChange(getName());
		TextChange[] changes= fChangeManager.getAllChanges();
		pm.beginTask(NO_NAME, changes.length);
		pm.setTaskName(RefactoringCoreMessages.SelfEncapsulateField_create_changes); 
		for (int i= 0; i < changes.length; i++) {
			result.add(changes[i]);
			pm.worked(1);
		}
		pm.done();
		return result;
	}

	/*
	 * @see IRefactoring#getName()
	 */
	public String getName() {
		return RefactoringCoreMessages.SelfEncapsulateField_name; 
	}
	
	//---- Helper methods -------------------------------------------------------------
	
	private void checkCompileErrors(RefactoringStatus result, CompilationUnit root, ICompilationUnit element) {
		Message[] messages= root.getMessages();
		if (messages.length != 0) {
			result.addError(Messages.format(
				RefactoringCoreMessages.SelfEncapsulateField_compiler_errors_update, //$NON-NLS-1$
				element.getElementName()), JavaStatusContext.create(element));
		}
	}
	
	private void checkInHierarchy(RefactoringStatus status) {
		AbstractTypeDeclaration declaration= (AbstractTypeDeclaration)ASTNodes.getParent(fFieldDeclaration, AbstractTypeDeclaration.class);
		ITypeBinding type= declaration.resolveBinding();
		if (type != null) {
			ITypeBinding fieldType= fFieldDeclaration.resolveBinding().getType();
			status.merge(Checks.checkMethodInHierarchy(type, fGetterName, fieldType, new ITypeBinding[0], fField.getJavaProject()));
			status.merge(Checks.checkMethodInHierarchy(type, fSetterName, fFieldDeclaration.getAST().resolveWellKnownType("void"), //$NON-NLS-1$
				new ITypeBinding[] {fieldType}, 
				fField.getJavaProject()));
		}
	}
	
	private void computeUsedNames() {
		fUsedReadNames= new ArrayList(0);
		fUsedModifyNames= new ArrayList(0);
		IVariableBinding binding= fFieldDeclaration.resolveBinding();
		ITypeBinding type= binding.getType();
		IMethodBinding[] methods= binding.getDeclaringClass().getDeclaredMethods();
		for (int i= 0; i < methods.length; i++) {
			IMethodBinding method= methods[i];
			ITypeBinding[] parameters= methods[i].getParameterTypes();
			if (parameters == null || parameters.length == 0) {
				fUsedReadNames.add(method);
			} else if (parameters.length == 1 && parameters[0] == type) {
				fUsedModifyNames.add(method);
			} 
		}
	}

	private List addGetterSetterChanges(CompilationUnit root, ASTRewrite rewriter, String lineDelimiter) throws CoreException {
		List result= new ArrayList(2);
		AST ast= root.getAST();
		FieldDeclaration decl= (FieldDeclaration)ASTNodes.getParent(fFieldDeclaration, ASTNode.FIELD_DECLARATION);
		int position= 0;
		int numberOfMethods= 0;
		List members= ASTNodes.getBodyDeclarations(decl.getParent());
		for (Iterator iter= members.iterator(); iter.hasNext();) {
			BodyDeclaration element= (BodyDeclaration)iter.next();
			if (element.getNodeType() == ASTNode.METHOD_DECLARATION) {
				if (fInsertionIndex == -1) {
					break;
				} else if (fInsertionIndex == numberOfMethods) {
					position++;
					break;
				}
				numberOfMethods++;	
			}
			position++;
		}
		TextEditGroup description;
		ListRewrite rewrite= fRewriter.getListRewrite(decl.getParent(), getBodyDeclarationsProperty(decl.getParent()));
		if (!JdtFlags.isFinal(fField)) {
			description= new TextEditGroup(RefactoringCoreMessages.SelfEncapsulateField_add_setter); 
			result.add(description);
			rewrite.insertAt(createSetterMethod(ast, rewriter, lineDelimiter), position++, description);
		}
		description= new TextEditGroup(RefactoringCoreMessages.SelfEncapsulateField_add_getter); 
		result.add(description);
		rewrite.insertAt(createGetterMethod(ast, rewriter, lineDelimiter), position, description);
		if (!JdtFlags.isPrivate(fField))
			result.add(makeDeclarationPrivate(rewriter, decl));
		return result;
	}

	private TextEditGroup makeDeclarationPrivate(ASTRewrite rewriter, FieldDeclaration decl) {
		AST ast= rewriter.getAST();
		TextEditGroup description= new TextEditGroup(RefactoringCoreMessages.SelfEncapsulateField_change_visibility); 
		if (decl.fragments().size() > 1) {
			//TODO: doesn't work for cases like this:  int field1, field2= field1, field3= field2; // keeping refs to field
			rewriter.remove(fFieldDeclaration, description);
			ChildListPropertyDescriptor descriptor= getBodyDeclarationsProperty(decl.getParent());
			VariableDeclarationFragment newField= (VariableDeclarationFragment) rewriter.createCopyTarget(fFieldDeclaration);
			FieldDeclaration fieldDecl= ast.newFieldDeclaration(newField);
			fieldDecl.setType((Type)rewriter.createCopyTarget(decl.getType()));
			fieldDecl.modifiers().addAll(ASTNodeFactory.newModifiers(ast, Modifier.PRIVATE));
			rewriter.getListRewrite(decl.getParent(), descriptor).insertAfter(fieldDecl, decl, description);
		} else {
			ModifierRewrite.create(rewriter, decl).setVisibility(Modifier.PRIVATE, description);
		}
		return description;
	}

	private ChildListPropertyDescriptor getBodyDeclarationsProperty(ASTNode declaration) {
		if (declaration instanceof AnonymousClassDeclaration)
			return AnonymousClassDeclaration.BODY_DECLARATIONS_PROPERTY;
		else if (declaration instanceof AbstractTypeDeclaration)
			return ((AbstractTypeDeclaration) declaration).getBodyDeclarationsProperty();
		Assert.isTrue(false);
		return null;
	}

	private MethodDeclaration createSetterMethod(AST ast, ASTRewrite rewriter, String lineDelimiter) throws CoreException {
		FieldDeclaration field= (FieldDeclaration)ASTNodes.getParent(fFieldDeclaration, FieldDeclaration.class);
		Type type= field.getType();
		MethodDeclaration result= ast.newMethodDeclaration();
		result.setName(ast.newSimpleName(fSetterName));
		result.modifiers().addAll(ASTNodeFactory.newModifiers(ast, createModifiers()));
		if (fSetterMustReturnValue) {
			result.setReturnType2((Type)rewriter.createCopyTarget(type));
		}
		SingleVariableDeclaration param= ast.newSingleVariableDeclaration();
		result.parameters().add(param);
		param.setName(ast.newSimpleName(fArgName));
		param.setType((Type)rewriter.createCopyTarget(type));
		
		Block block= ast.newBlock();
		result.setBody(block);
		Assignment ass= ast.newAssignment();
		ass.setLeftHandSide(createFieldAccess(ast));
		ass.setRightHandSide(ast.newSimpleName(fArgName));
		if (fSetterMustReturnValue) {
			ReturnStatement rs= ast.newReturnStatement();
			rs.setExpression(ass);
			block.statements().add(rs);
		} else {
			block.statements().add(ast.newExpressionStatement(ass));
		}
		
		if (fGenerateJavadoc) {
			String string= CodeGeneration.getSetterComment(
				fField.getCompilationUnit() , getTypeName(field.getParent()), fSetterName, 
				fField.getElementName(), ASTNodes.asString(type), fArgName, 
				NamingConventions.removePrefixAndSuffixForFieldName(fField.getJavaProject(), fField.getElementName(), fField.getFlags()),
				lineDelimiter);
			if (string != null) {
				Javadoc javadoc= (Javadoc)fRewriter.createStringPlaceholder(string, ASTNode.JAVADOC);
				result.setJavadoc(javadoc);
			}
		}
		return result;
	}
	
	private MethodDeclaration createGetterMethod(AST ast, ASTRewrite rewriter, String lineDelimiter) throws CoreException {
		FieldDeclaration field= (FieldDeclaration)ASTNodes.getParent(fFieldDeclaration, FieldDeclaration.class);
		Type type= field.getType();
		MethodDeclaration result= ast.newMethodDeclaration();
		result.setName(ast.newSimpleName(fGetterName));
		result.modifiers().addAll(ASTNodeFactory.newModifiers(ast, createModifiers()));
		result.setReturnType2((Type)rewriter.createCopyTarget(type));
		
		Block block= ast.newBlock();
		result.setBody(block);
		ReturnStatement rs= ast.newReturnStatement();
		rs.setExpression(ast.newSimpleName(fField.getElementName()));
		block.statements().add(rs);
		if (fGenerateJavadoc) {
			String string= CodeGeneration.getGetterComment(
				fField.getCompilationUnit() , getTypeName(field.getParent()), fGetterName,
				fField.getElementName(), ASTNodes.asString(type), 
				NamingConventions.removePrefixAndSuffixForFieldName(fField.getJavaProject(), fField.getElementName(), fField.getFlags()),
				lineDelimiter);
			if (string != null) {
				Javadoc javadoc= (Javadoc)fRewriter.createStringPlaceholder(string, ASTNode.JAVADOC);
				result.setJavadoc(javadoc);
			}
		}
		return result;
	}

	private int createModifiers() throws JavaModelException {
		int result= 0;
		if (Flags.isPublic(fVisibility)) 
			result |= Modifier.PUBLIC;
		else if (Flags.isProtected(fVisibility)) 
			result |= Modifier.PROTECTED;
		else if (Flags.isPrivate(fVisibility))
			result |= Modifier.PRIVATE;
		if (JdtFlags.isStatic(fField)) 
			result |= Modifier.STATIC; 
		return result;
	}
	
	private Expression createFieldAccess(AST ast) throws JavaModelException {
		String fieldName= fField.getElementName();
		if (fArgName.equals(fieldName)) {
			if (JdtFlags.isStatic(fField)) {
				return ast.newQualifiedName(
					ast.newSimpleName(fField.getDeclaringType().getElementName()), 
					ast.newSimpleName(fieldName));
			} else {
				FieldAccess result= ast.newFieldAccess();
				result.setExpression(ast.newThisExpression());
				result.setName(ast.newSimpleName(fieldName));
				return result;
			}
		} else {
			return ast.newSimpleName(fieldName);
		}
	}
	
	private void checkArgName() {
		String fieldName= fField.getElementName();
		boolean isStatic= true;
		try {
			isStatic= JdtFlags.isStatic(fField);
		} catch(JavaModelException e) {
		}
		if ((isStatic && fArgName.equals(fieldName) 
			&& fieldName.equals(fField.getDeclaringType().getElementName()))
			|| JavaConventions.validateIdentifier(fArgName).getSeverity() == IStatus.ERROR)
			fArgName= "_" + fArgName; //$NON-NLS-1$
	}
	
	private RefactoringStatus validateModifiesFiles(){
		return Checks.validateModifiesFiles(getAllFilesToModify(), getValidationContext());
	}			

	private IFile[] getAllFilesToModify(){
		return ResourceUtil.getFiles(fChangeManager.getAllCompilationUnits());
	}
	
	private String getTypeName(ASTNode type) {
		if (type instanceof AbstractTypeDeclaration) {
			return ((AbstractTypeDeclaration)type).getName().getIdentifier();
		} else if (type instanceof AnonymousClassDeclaration) {
			ClassInstanceCreation node= (ClassInstanceCreation)ASTNodes.getParent(type, ClassInstanceCreation.class);
			return ASTNodes.asString(node.getType());
		}
		Assert.isTrue(false, "Should not happen"); //$NON-NLS-1$
		return null;
	}
}

