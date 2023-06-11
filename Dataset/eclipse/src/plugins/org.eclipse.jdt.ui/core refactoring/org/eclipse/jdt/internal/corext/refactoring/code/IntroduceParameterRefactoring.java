/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Felix Pahl (fpahl@web.de) - contributed fix for:
 *       o introduce parameter throws NPE if there are compiler errors
 *         (see https://bugs.eclipse.org/bugs/show_bug.cgi?id=48325)
 *******************************************************************************/

package org.eclipse.jdt.internal.corext.refactoring.code;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jface.text.TextSelection;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.ConditionalExpression;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NullLiteral;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.Corext;
import org.eclipse.jdt.internal.corext.SourceRange;
import org.eclipse.jdt.internal.corext.codemanipulation.StubUtility;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.dom.Bindings;
import org.eclipse.jdt.internal.corext.dom.NodeFinder;
import org.eclipse.jdt.internal.corext.dom.ScopeAnalyzer;
import org.eclipse.jdt.internal.corext.dom.fragments.ASTFragmentFactory;
import org.eclipse.jdt.internal.corext.dom.fragments.IASTFragment;
import org.eclipse.jdt.internal.corext.dom.fragments.IExpressionFragment;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.ParameterInfo;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.base.RefactoringStatusCodes;
import org.eclipse.jdt.internal.corext.refactoring.structure.BodyUpdater;
import org.eclipse.jdt.internal.corext.refactoring.structure.ChangeSignatureRefactoring;
import org.eclipse.jdt.internal.corext.refactoring.structure.CompilationUnitRewrite;
import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.internal.ui.actions.SelectionConverter;

public class IntroduceParameterRefactoring extends Refactoring {
	
	private static final String[] KNOWN_METHOD_NAME_PREFIXES= {"get", "is"}; //$NON-NLS-2$ //$NON-NLS-1$
	
	private ICompilationUnit fSourceCU;
	private int fSelectionStart;
	private int fSelectionLength;
	
	private IMethod fMethod;
	private ChangeSignatureRefactoring fChangeSignatureRefactoring;
	private ParameterInfo fParameter;

	private Expression fSelectedExpression;
	private String[] fExcludedParameterNames;
	
	private IntroduceParameterRefactoring(ICompilationUnit cu, int selectionStart, int selectionLength) {
		Assert.isTrue(cu != null && cu.exists());
		Assert.isTrue(selectionStart >= 0);
		Assert.isTrue(selectionLength >= 0);
		fSourceCU= cu;
		fSelectionStart= selectionStart;
		fSelectionLength= selectionLength;
	}
	
	public static IntroduceParameterRefactoring create(ICompilationUnit cu, int selectionStart, int selectionLength) {
		return new IntroduceParameterRefactoring(cu, selectionStart, selectionLength);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.internal.corext.refactoring.base.IRefactoring#getName()
	 */
	public String getName() {
		return RefactoringCoreMessages.IntroduceParameterRefactoring_name; 
	}

	//--- checkActivation
		
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException {
		try {
			pm.beginTask("", 7); //$NON-NLS-1$
			
			if (! fSourceCU.isStructureKnown())		
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.IntroduceParameterRefactoring_syntax_error); 
			
			IJavaElement enclosingElement= SelectionConverter.resolveEnclosingElement(fSourceCU, new TextSelection(fSelectionStart, fSelectionLength));
			if (! (enclosingElement instanceof IMethod))
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.IntroduceParameterRefactoring_expression_in_method); 
			
			fMethod= (IMethod) enclosingElement;
			pm.worked(1);
			
			// first try:
			fChangeSignatureRefactoring= ChangeSignatureRefactoring.create(fMethod);
			if (fChangeSignatureRefactoring == null)
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.IntroduceParameterRefactoring_expression_in_method); 
			fChangeSignatureRefactoring.setValidationContext(getValidationContext());
			RefactoringStatus result= fChangeSignatureRefactoring.checkInitialConditions(new SubProgressMonitor(pm, 1));
			
			if (result.hasFatalError()) {
				RefactoringStatusEntry entry= result.getEntryMatchingSeverity(RefactoringStatus.FATAL);
				if (entry.getCode() == RefactoringStatusCodes.OVERRIDES_ANOTHER_METHOD || entry.getCode() == RefactoringStatusCodes.METHOD_DECLARED_IN_INTERFACE) {
					// second try:
					fChangeSignatureRefactoring= ChangeSignatureRefactoring.create((IMethod) entry.getData());
					if (fChangeSignatureRefactoring == null) {
						String msg= Messages.format(RefactoringCoreMessages.IntroduceParameterRefactoring_cannot_introduce, entry.getMessage());
						return RefactoringStatus.createFatalErrorStatus(msg);
					} 
					result= fChangeSignatureRefactoring.checkInitialConditions(new SubProgressMonitor(pm, 1));
					if (result.hasFatalError())
						return result;
				} else {
					return result;
				}
			} else {
				pm.worked(1);
			}
			
			CompilationUnitRewrite cuRewrite= fChangeSignatureRefactoring.getBaseCuRewrite();
			if (! cuRewrite.getCu().equals(fSourceCU))
				cuRewrite= new CompilationUnitRewrite(fSourceCU); // TODO: should try to avoid throwing away this AST
			
			initializeSelectedExpression(cuRewrite);
			pm.worked(1);
		
			result.merge(checkSelection(cuRewrite, new SubProgressMonitor(pm, 3)));
			if (result.hasFatalError())
				return result;

			initializeExcludedParameterNames(cuRewrite);
			
			addParameterInfo(cuRewrite);
			
			fChangeSignatureRefactoring.setBodyUpdater(new BodyUpdater() {
				public void updateBody(MethodDeclaration methodDeclaration, CompilationUnitRewrite rewrite, RefactoringStatus updaterResult) {
					replaceSelectedExpression(rewrite);
				}
			});
			
			return result;
		} finally {
			pm.done();
			if (fChangeSignatureRefactoring != null)
				fChangeSignatureRefactoring.setValidationContext(null);
		}	
	}

	private void addParameterInfo(CompilationUnitRewrite cuRewrite) throws JavaModelException {
		ITypeBinding typeBinding= Bindings.normalizeForDeclarationUse( 
			fSelectedExpression.resolveTypeBinding(),
			fSelectedExpression.getAST());
		String typeName= cuRewrite.getImportRewrite().addImport(typeBinding);
		String name= guessedParameterName();
		fParameter= ParameterInfo.createInfoForAddedParameter(typeBinding, typeName, name);
		String defaultValue= fSourceCU.getBuffer().getText(fSelectedExpression.getStartPosition(), fSelectedExpression.getLength());
		fParameter.setDefaultValue(defaultValue);
		
		List parameterInfos= fChangeSignatureRefactoring.getParameterInfos();
		int parametersCount= parameterInfos.size();
		if (parametersCount > 0 &&
				((ParameterInfo) parameterInfos.get(parametersCount - 1)).isOldVarargs())
			parameterInfos.add(parametersCount - 1, fParameter);
		else
			parameterInfos.add(fParameter);
	}

	private void replaceSelectedExpression(CompilationUnitRewrite cuRewrite) {
		if (! fSourceCU.equals(cuRewrite.getCu()))
			return;
		// TODO: do for all methodDeclarations and replace matching fragments?
		
		// cannot use fSelectedExpression here, since it could be from another AST (if method was replaced by overridden):
		Expression expression= (Expression) NodeFinder.perform(cuRewrite.getRoot(), fSelectedExpression.getStartPosition(), fSelectedExpression.getLength());
		
		ASTNode newExpression= cuRewrite.getRoot().getAST().newSimpleName(fParameter.getNewName());
		String description= RefactoringCoreMessages.IntroduceParameterRefactoring_replace; 
		cuRewrite.getASTRewrite().replace(expression, newExpression, cuRewrite.createGroupDescription(description));
	}

	private void initializeSelectedExpression(CompilationUnitRewrite cuRewrite) throws JavaModelException {
		IASTFragment fragment= ASTFragmentFactory.createFragmentForSourceRange(
				new SourceRange(fSelectionStart, fSelectionLength), cuRewrite.getRoot(), cuRewrite.getCu());
		
		if (! (fragment instanceof IExpressionFragment))
			return;
		
		//TODO: doesn't handle selection of partial Expressions
		Expression expression= ((IExpressionFragment) fragment).getAssociatedExpression();
		if (fragment.getStartPosition() != expression.getStartPosition()
				|| fragment.getLength() != expression.getLength())
			return;
		
		if (Checks.isInsideJavadoc(expression))
			return;
		//TODO: exclude invalid selections
		if (Checks.isEnumCase(expression.getParent()))
			return;
		
		fSelectedExpression= expression;
	}
	
	private RefactoringStatus checkSelection(CompilationUnitRewrite cuRewrite, IProgressMonitor pm) {
		if (fSelectedExpression == null){
			String message= RefactoringCoreMessages.IntroduceParameterRefactoring_select;
			return CodeRefactoringUtil.checkMethodSyntaxErrors(fSelectionStart, fSelectionLength, cuRewrite.getRoot(), message);
		}	
		
		MethodDeclaration methodDeclaration= (MethodDeclaration) ASTNodes.getParent(fSelectedExpression, MethodDeclaration.class);
		if (methodDeclaration == null)
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.IntroduceParameterRefactoring_expression_in_method); 
		if (methodDeclaration.resolveBinding() == null)
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.IntroduceParameterRefactoring_no_binding); 
		//TODO: check for rippleMethods -> find matching fragments, consider callers of all rippleMethods
		
		RefactoringStatus result= new RefactoringStatus();
		result.merge(checkExpression());
		if (result.hasFatalError())
			return result;
		
		result.merge(checkExpressionBinding());
		if (result.hasFatalError())
			return result;				
		
//			if (isUsedInForInitializerOrUpdater(getSelectedExpression().getAssociatedExpression()))
//				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("ExtractTempRefactoring.for_initializer_updater")); //$NON-NLS-1$
//			pm.worked(1);				
//
//			if (isReferringToLocalVariableFromFor(getSelectedExpression().getAssociatedExpression()))
//				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.getString("ExtractTempRefactoring.refers_to_for_variable")); //$NON-NLS-1$
//			pm.worked(1);
		
		return result;		
	}

	private RefactoringStatus checkExpression() {
		//TODO: adjust error messages (or generalize for all refactorings on expression-selections?)
		Expression selectedExpression= fSelectedExpression;
		
		if (selectedExpression instanceof Name && selectedExpression.getParent() instanceof ClassInstanceCreation)
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_name_in_new); 
			//TODO: let's just take the CIC automatically (no ambiguity -> no problem -> no dialog ;-)
		
		if (selectedExpression instanceof NullLiteral) {
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_null_literals); 
		} else if (selectedExpression instanceof ArrayInitializer) {
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_array_initializer); 
		} else if (selectedExpression instanceof Assignment) {
			if (selectedExpression.getParent() instanceof Expression)
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_assignment); 
			else
				return null;
		
		} else if (selectedExpression instanceof ConditionalExpression) {
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_single_conditional_expression); 
		} else if (selectedExpression instanceof SimpleName){
			if ((((SimpleName)selectedExpression)).isDeclaration())
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_names_in_declarations); 
			if (selectedExpression.getParent() instanceof QualifiedName && selectedExpression.getLocationInParent() == QualifiedName.NAME_PROPERTY
					|| selectedExpression.getParent() instanceof FieldAccess && selectedExpression.getLocationInParent() == FieldAccess.NAME_PROPERTY)
				return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.ExtractTempRefactoring_select_expression);
		} 
		
		return null;
	}

	private RefactoringStatus checkExpressionBinding() {
		return checkExpressionFragmentIsRValue();
	}
	
	// !! +/- same as in ExtractConstantRefactoring & ExtractTempRefactoring
	private RefactoringStatus checkExpressionFragmentIsRValue() {
		switch(Checks.checkExpressionIsRValue(fSelectedExpression)) {
			case Checks.NOT_RVALUE_MISC:
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.IntroduceParameterRefactoring_select, null, Corext.getPluginId(), RefactoringStatusCodes.EXPRESSION_NOT_RVALUE, null); 
			case Checks.NOT_RVALUE_VOID:
				return RefactoringStatus.createStatus(RefactoringStatus.FATAL, RefactoringCoreMessages.IntroduceParameterRefactoring_no_void, null, Corext.getPluginId(), RefactoringStatusCodes.EXPRESSION_NOT_RVALUE_VOID, null); 
			case Checks.IS_RVALUE:
				return new RefactoringStatus();
			default:
				Assert.isTrue(false); return null;
		}		
	}	

	public List getParameterInfos() {
		return fChangeSignatureRefactoring.getParameterInfos();
	}
	
	public ParameterInfo getAddedParameterInfo() {
		return fParameter;
	}
	
	public String getMethodSignaturePreview() throws JavaModelException {
		return fChangeSignatureRefactoring.getMethodSignaturePreview();
	}
	
//--- Input setting/validation

	public void setParameterName(String name) {
		Assert.isNotNull(name);
		fParameter.setNewName(name);
	}
	
	/** 
	 * must only be called <i>after</i> checkActivation() 
	 * @return guessed parameter name
	 */
	public String guessedParameterName() {
		String[] proposals= guessParameterNames();
		if (proposals.length == 0)
			return ""; //$NON-NLS-1$
		else
			return proposals[0];
	}
	
// --- TODO: copied from ExtractTempRefactoring - should extract ------------------------------
	
	/**
	 * Must only be called <i>after</i> checkActivation().
	 * The first proposal should be used as "best guess" (if it exists).
	 * @return proposed variable names (may be empty, but not null).
	 */
	public String[] guessParameterNames() {
		LinkedHashSet proposals= new LinkedHashSet(); //retain ordering, but prevent duplicates
		if (fSelectedExpression instanceof MethodInvocation){
			proposals.addAll(guessTempNamesFromMethodInvocation((MethodInvocation) fSelectedExpression, fExcludedParameterNames));
		}
		proposals.addAll(guessTempNamesFromExpression(fSelectedExpression, fExcludedParameterNames));
		return (String[]) proposals.toArray(new String[proposals.size()]);
	}
	
	private List/*<String>*/ guessTempNamesFromMethodInvocation(MethodInvocation selectedMethodInvocation, String[] excludedVariableNames) {
		String methodName= selectedMethodInvocation.getName().getIdentifier();
		for (int i= 0; i < KNOWN_METHOD_NAME_PREFIXES.length; i++) {
			String prefix= KNOWN_METHOD_NAME_PREFIXES[i];
			if (! methodName.startsWith(prefix))
				continue; //not this prefix
			if (methodName.length() == prefix.length())
				return Collections.EMPTY_LIST; // prefix alone -> don't take method name
			char firstAfterPrefix= methodName.charAt(prefix.length());
			if (! Character.isUpperCase(firstAfterPrefix))
				continue; //not uppercase after prefix
			//found matching prefix
			String proposal= Character.toLowerCase(firstAfterPrefix) + methodName.substring(prefix.length() + 1);
			methodName= proposal;
			break;
		}
		String[] proposals= StubUtility.getLocalNameSuggestions(fSourceCU.getJavaProject(), methodName, 0, excludedVariableNames);
		return Arrays.asList(proposals);
	}
	
	private List/*<String>*/ guessTempNamesFromExpression(Expression selectedExpression, String[] excluded) {
		ITypeBinding expressionBinding= Bindings.normalizeForDeclarationUse(
			selectedExpression.resolveTypeBinding(),
			selectedExpression.getAST());
		String typeName= getQualifiedName(expressionBinding);
		if (typeName.length() == 0)
			typeName= expressionBinding.getName();
		if (typeName.length() == 0)			
			return Collections.EMPTY_LIST;
		int typeParamStart= typeName.indexOf("<"); //$NON-NLS-1$
		if (typeParamStart != -1)
			typeName= typeName.substring(0, typeParamStart);
		String[] proposals= StubUtility.getLocalNameSuggestions(fSourceCU.getJavaProject(), typeName, expressionBinding.getDimensions(), excluded);
		return Arrays.asList(proposals);
	}
	
// ----------------------------------------------------------------------
	
	private static String getQualifiedName(ITypeBinding typeBinding) {
		if (typeBinding.isAnonymous())
			return getQualifiedName(typeBinding.getSuperclass());
		if (! typeBinding.isArray())
			return typeBinding.getQualifiedName();
		else
			return typeBinding.getElementType().getQualifiedName();
	}

	private void initializeExcludedParameterNames(CompilationUnitRewrite cuRewrite) {
		IBinding[] bindings= new ScopeAnalyzer(cuRewrite.getRoot()).getDeclarationsInScope(
				fSelectedExpression.getStartPosition(), ScopeAnalyzer.VARIABLES);
		fExcludedParameterNames= new String[bindings.length];
		for (int i= 0; i < fExcludedParameterNames.length; i++) {
			fExcludedParameterNames[i]= bindings[i].getName();
		}
	}
	
	public RefactoringStatus validateInput() {
		return fChangeSignatureRefactoring.checkSignature();
	}
	
	private RefactoringStatus checkExcludedParameterNames() {
//		for (int i= 0; i < fExcludedParameterNames.length; i++) {
//			if (fParameter.getNewName().equals(fExcludedParameterNames[i]))
//			return RefactoringStatus.createErrorStatus(RefactoringCoreMessages.getString("IntroduceParameterRefactoring.duplicate_name")); //$NON-NLS-1$
//		}
		return new RefactoringStatus();
	}
	
//--- checkInput
	
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm) throws CoreException {
		fChangeSignatureRefactoring.setValidationContext(getValidationContext());
		RefactoringStatus result;
		try {
			result= fChangeSignatureRefactoring.checkFinalConditions(pm);
		} finally {
			fChangeSignatureRefactoring.setValidationContext(null);
		}
		return result;
	}
	
	public Change createChange(IProgressMonitor pm) throws CoreException {
		fChangeSignatureRefactoring.setValidationContext(getValidationContext());
		Change result;
		try {
			result= fChangeSignatureRefactoring.createChange(pm);
		} finally {
			fChangeSignatureRefactoring.setValidationContext(null);
		}
		return result;
	}
}