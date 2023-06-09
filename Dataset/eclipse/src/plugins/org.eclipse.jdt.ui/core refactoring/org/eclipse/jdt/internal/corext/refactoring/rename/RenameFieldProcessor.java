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
package org.eclipse.jdt.internal.corext.refactoring.rename;
import java.util.Arrays;
import java.util.List;

import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IFile;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.codemanipulation.GetterSetterUtil;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationStateChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextChangeCompatibility;
import org.eclipse.jdt.internal.corext.refactoring.participants.JavaProcessors;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IReferenceUpdating;
import org.eclipse.jdt.internal.corext.refactoring.tagging.ITextUpdating;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.SearchUtils;
import org.eclipse.jdt.internal.corext.util.WorkingCopyUtil;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.ParticipantManager;
import org.eclipse.ltk.core.refactoring.participants.RenameArguments;
import org.eclipse.ltk.core.refactoring.participants.RenameParticipant;
import org.eclipse.ltk.core.refactoring.participants.SharableParticipants;
import org.eclipse.ltk.core.refactoring.participants.ValidateEditChecker;

public class RenameFieldProcessor extends JavaRenameProcessor implements IReferenceUpdating, ITextUpdating {
	
	private static final String DECLARED_SUPERTYPE= RefactoringCoreMessages.RenameFieldRefactoring_declared_in_supertype; 
	private IField fField;
	private SearchResultGroup[] fReferences;
	private TextChangeManager fChangeManager;
	private ICompilationUnit[] fNewWorkingCopies;
	private boolean fUpdateReferences;
	
	private boolean fUpdateTextualMatches;
	
	private boolean fRenameGetter;
	private boolean fRenameSetter;

	public static final String IDENTIFIER= "org.eclipse.jdt.ui.renameFieldProcessor"; //$NON-NLS-1$
	
	public RenameFieldProcessor(IField field) {
		fField= field;
		setNewElementName(fField.getElementName());
		fUpdateReferences= true;
		fUpdateTextualMatches= false;
		
		fRenameGetter= false;
		fRenameSetter= false;
	}
	
	//---- IRefactoringProcessor --------------------------------

	public String getIdentifier() {
		return IDENTIFIER;
	}
	
	public boolean isApplicable() throws CoreException {
		return RefactoringAvailabilityTester.isRenameFieldAvailable(fField);
	}
	
	public String getProcessorName() {
		return Messages.format(
			RefactoringCoreMessages.RenameFieldRefactoring_name, //$NON-NLS-1$
			new String[]{fField.getElementName(), getNewElementName()});
	}
	
	protected String[] getAffectedProjectNatures() throws CoreException {
		return JavaProcessors.computeAffectedNatures(fField);
	}

	public IField getField() {
		return fField;
	}

	public Object[] getElements() {
		return new Object[] { fField};
	}
	
	protected void loadDerivedParticipants(RefactoringStatus status, List result, String[] natures, SharableParticipants shared) throws CoreException {
		if (fRenameGetter) {
			IMethod getter= getGetter();
			if (getter != null) {
				addParticipants(status, result, getter, getNewGetterName(), natures, shared);
			}
		}
		if (fRenameSetter) {
			IMethod setter= getSetter();
			if (setter != null) {
				addParticipants(status, result, setter, getNewSetterName(), natures, shared);
			}
		}
	}

	private void addParticipants(RefactoringStatus status, List result, IMethod method, String methodName, String[] natures, SharableParticipants shared) {
		RenameArguments args= new RenameArguments(methodName, getUpdateReferences());
		RenameParticipant[] participants= ParticipantManager.loadRenameParticipants(status, this, method, args, natures, shared);
		result.addAll(Arrays.asList(participants));
	}

	//---- IRenameProcessor -------------------------------------
	
	public final String getCurrentElementName(){
		return fField.getElementName();
	}
	
	public final String getCurrentElementQualifier(){
		return JavaModelUtil.getFullyQualifiedName(fField.getDeclaringType());
	}
	
	public RefactoringStatus checkNewElementName(String newName) throws CoreException {
		Assert.isNotNull(newName, "new name"); //$NON-NLS-1$
		RefactoringStatus result= Checks.checkFieldName(newName);
		
		if (isInstanceField(fField) && (! Checks.startsWithLowerCase(newName)))
			result.addWarning(RefactoringCoreMessages.RenameFieldRefactoring_should_start_lowercase); 
			
		if (Checks.isAlreadyNamed(fField, newName))
			result.addFatalError(RefactoringCoreMessages.RenameFieldRefactoring_another_name); 
		if (fField.getDeclaringType().getField(newName).exists())
			result.addFatalError(RefactoringCoreMessages.RenameFieldRefactoring_field_already_defined); 
		return result;
	}
	
	public Object getNewElement() {
		return fField.getDeclaringType().getField(getNewElementName());
	}
	
	//---- ITextUpdating2 ---------------------------------------------
	
	public boolean canEnableTextUpdating() {
		return true;
	}
	
	public boolean getUpdateTextualMatches() {
		return fUpdateTextualMatches;
	}
	
	public void setUpdateTextualMatches(boolean update) {
		fUpdateTextualMatches= update;
	}
	
	//---- IReferenceUpdating -----------------------------------

	public boolean canEnableUpdateReferences() {
		return true;
	}

	public void setUpdateReferences(boolean update) {
		fUpdateReferences= update;
	}
	
	public boolean getUpdateReferences(){
		return fUpdateReferences;
	}
		
	//-- getter/setter --------------------------------------------------
	
	/**
	 * @return Error message or <code>null</code> if getter can be renamed.
	 */
	public String canEnableGetterRenaming() throws CoreException{
		if (fField.getDeclaringType().isInterface())
			return getGetter() == null ? "": null; //$NON-NLS-1$
			
		IMethod getter= getGetter();
		if (getter == null) 
			return ""; //$NON-NLS-1$
		final NullProgressMonitor monitor= new NullProgressMonitor();
		if (MethodChecks.isVirtual(getter)) {
			final ITypeHierarchy hierarchy= getter.getDeclaringType().newTypeHierarchy(monitor);
			if (MethodChecks.isDeclaredInInterface(getter, hierarchy, monitor) != null || MethodChecks.overridesAnotherMethod(getter, hierarchy) != null)
				return DECLARED_SUPERTYPE;
		}
		return null;	
	}
	
	/**
	 * @return Error message or <code>null</code> if setter can be renamed.
	 */
	public String canEnableSetterRenaming() throws CoreException{
		if (fField.getDeclaringType().isInterface())
			return getSetter() == null ? "": null; //$NON-NLS-1$
			
		IMethod setter= getSetter();
		if (setter == null) 
			return "";	 //$NON-NLS-1$
		final NullProgressMonitor monitor= new NullProgressMonitor();
		if (MethodChecks.isVirtual(setter)) {
			final ITypeHierarchy hierarchy= setter.getDeclaringType().newTypeHierarchy(monitor);
			if (MethodChecks.isDeclaredInInterface(setter, hierarchy, monitor) != null || MethodChecks.overridesAnotherMethod(setter, hierarchy) != null)
				return DECLARED_SUPERTYPE;
		}
		return null;	
	}
	
	public boolean getRenameGetter() {
		return fRenameGetter;
	}

	public void setRenameGetter(boolean renameGetter) {
		fRenameGetter= renameGetter;
	}

	public boolean getRenameSetter() {
		return fRenameSetter;
	}

	public void setRenameSetter(boolean renameSetter) {
		fRenameSetter= renameSetter;
	}
	
	public IMethod getGetter() throws CoreException {
		return GetterSetterUtil.getGetter(fField);
	}
	
	public IMethod getSetter() throws CoreException {
		return GetterSetterUtil.getSetter(fField);
	}

	public String getNewGetterName() throws CoreException {
		IMethod primaryGetterCandidate= JavaModelUtil.findMethod(GetterSetterUtil.getGetterName(fField, new String[0]), new String[0], false, fField.getDeclaringType());
		if (! JavaModelUtil.isBoolean(fField) || (primaryGetterCandidate != null && primaryGetterCandidate.exists()))
			return GetterSetterUtil.getGetterName(fField.getJavaProject(), getNewElementName(), fField.getFlags(), JavaModelUtil.isBoolean(fField), null);
		//bug 30906 describes why we need to look for other alternatives here	
		return GetterSetterUtil.getGetterName(fField.getJavaProject(), getNewElementName(), fField.getFlags(), false, null);
	}

	public String getNewSetterName() throws CoreException {
		return GetterSetterUtil.getSetterName(fField.getJavaProject(), getNewElementName(), fField.getFlags(), JavaModelUtil.isBoolean(fField), null);
	}

	// -------------- Preconditions -----------------------
	
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException{
		IField orig= (IField)WorkingCopyUtil.getOriginal(fField);
		if (orig == null || ! orig.exists()){
			String message= Messages.format(RefactoringCoreMessages.RenameFieldRefactoring_deleted, 
								fField.getCompilationUnit().getElementName());
			return RefactoringStatus.createFatalErrorStatus(message);
		}	
		fField= orig;
		
		return Checks.checkIfCuBroken(fField);
	}
	
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm, CheckConditionsContext context) throws CoreException {
		try{
			pm.beginTask("", 18); //$NON-NLS-1$
			pm.setTaskName(RefactoringCoreMessages.RenameFieldRefactoring_checking); 
			RefactoringStatus result= new RefactoringStatus();
			result.merge(Checks.checkIfCuBroken(fField));
			if (result.hasFatalError())
				return result;
			result.merge(checkNewElementName(getNewElementName()));
			pm.worked(1);
			result.merge(checkEnclosingHierarchy());
			pm.worked(1);
			result.merge(checkNestedHierarchy(fField.getDeclaringType()));
			pm.worked(1);
			
			if (fUpdateReferences){
				pm.setTaskName(RefactoringCoreMessages.RenameFieldRefactoring_searching);	 
				fReferences= getReferences(new SubProgressMonitor(pm, 3), result);
				pm.setTaskName(RefactoringCoreMessages.RenameFieldRefactoring_checking); 
			} else {
				fReferences= new SearchResultGroup[0];
				pm.worked(3);
			}	
			
			if (fUpdateReferences)
				result.merge(analyzeAffectedCompilationUnits());
			else
				Checks.checkCompileErrorsInAffectedFile(result, fField.getResource());
				
			if (getGetter() != null && fRenameGetter){
				result.merge(checkAccessor(new SubProgressMonitor(pm, 1), getGetter(), getNewGetterName()));
				result.merge(Checks.checkIfConstructorName(getGetter(), getNewGetterName(), fField.getDeclaringType().getElementName()));
			} else {
				pm.worked(1);
			}
				
			if (getSetter() != null && fRenameSetter){
				result.merge(checkAccessor(new SubProgressMonitor(pm, 1), getSetter(), getNewSetterName()));
				result.merge(Checks.checkIfConstructorName(getSetter(), getNewSetterName(), fField.getDeclaringType().getElementName()));
			} else {
				pm.worked(1);
			}
			
			result.merge(createChanges(new SubProgressMonitor(pm, 10)));
			if (result.hasFatalError())
				return result;
			
			ValidateEditChecker checker= (ValidateEditChecker)context.getChecker(ValidateEditChecker.class);
			checker.addFiles(getAllFilesToModify());
			return result;
		} finally{
			pm.done();
		}
	}
	
	//----------
	private RefactoringStatus checkAccessor(IProgressMonitor pm, IMethod existingAccessor, String newAccessorName) throws CoreException{
		RefactoringStatus result= new RefactoringStatus();
		result.merge(checkAccessorDeclarations(pm, existingAccessor));
		result.merge(checkNewAccessor(existingAccessor, newAccessorName));
		return result;
	}
	
	private RefactoringStatus checkNewAccessor(IMethod existingAccessor, String newAccessorName) throws CoreException{
		RefactoringStatus result= new RefactoringStatus();
		IMethod accessor= JavaModelUtil.findMethod(newAccessorName, existingAccessor.getParameterTypes(), false, fField.getDeclaringType());
		if (accessor == null || !accessor.exists())
			return null;
	
		String message= Messages.format(RefactoringCoreMessages.RenameFieldRefactoring_already_exists, 
				new String[]{JavaElementUtil.createMethodSignature(accessor), JavaModelUtil.getFullyQualifiedName(fField.getDeclaringType())});
		result.addError(message, JavaStatusContext.create(accessor));
		return result;
	}
	
	private RefactoringStatus checkAccessorDeclarations(IProgressMonitor pm, IMethod existingAccessor) throws CoreException{
		RefactoringStatus result= new RefactoringStatus();
		SearchPattern pattern= SearchPattern.createPattern(existingAccessor, IJavaSearchConstants.DECLARATIONS, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE);
		IJavaSearchScope scope= SearchEngine.createHierarchyScope(fField.getDeclaringType());
		SearchResultGroup[] groupDeclarations= RefactoringSearchEngine.search(pattern, scope, pm, result);
		Assert.isTrue(groupDeclarations.length > 0);
		if (groupDeclarations.length != 1){
			String message= Messages.format(RefactoringCoreMessages.RenameFieldRefactoring_overridden, 
								JavaElementUtil.createMethodSignature(existingAccessor));
			result.addError(message);
		} else {
			SearchResultGroup group= groupDeclarations[0];
			Assert.isTrue(group.getSearchResults().length > 0);
			if (group.getSearchResults().length != 1){
				String message= Messages.format(RefactoringCoreMessages.RenameFieldRefactoring_overridden_or_overrides, 
									JavaElementUtil.createMethodSignature(existingAccessor));
				result.addError(message);
			}	
		}	
		return result;
	}
	
	private static boolean isInstanceField(IField field) throws CoreException{
		if (JavaModelUtil.isInterfaceOrAnnotation(field.getDeclaringType()))
			return false;
		else 
			return ! JdtFlags.isStatic(field);
	}
	
	private RefactoringStatus checkNestedHierarchy(IType type) throws CoreException {
		IType[] nestedTypes= type.getTypes();
		if (nestedTypes == null)
			return null;
		RefactoringStatus result= new RefactoringStatus();	
		for (int i= 0; i < nestedTypes.length; i++){
			IField otherField= nestedTypes[i].getField(getNewElementName());
			if (otherField.exists()){
				String msg= Messages.format(
					RefactoringCoreMessages.RenameFieldRefactoring_hiding, //$NON-NLS-1$
					new String[]{fField.getElementName(), getNewElementName(), JavaModelUtil.getFullyQualifiedName(nestedTypes[i])});
				result.addWarning(msg, JavaStatusContext.create(otherField));
			}									
			result.merge(checkNestedHierarchy(nestedTypes[i]));	
		}	
		return result;
	}
	
	private RefactoringStatus checkEnclosingHierarchy() {
		IType current= fField.getDeclaringType();
		if (Checks.isTopLevel(current))
			return null;
		RefactoringStatus result= new RefactoringStatus();
		while (current != null){
			IField otherField= current.getField(getNewElementName());
			if (otherField.exists()){
				String msg= Messages.format(RefactoringCoreMessages.RenameFieldRefactoring_hiding2, 
				 															new String[]{getNewElementName(), JavaModelUtil.getFullyQualifiedName(current)});
				result.addWarning(msg, JavaStatusContext.create(otherField));
			}									
			current= current.getDeclaringType();
		}
		return result;
	}
	
	/*
	 * (non java-doc)
	 * Analyzes all compilation units in which type is referenced
	 */
	private RefactoringStatus analyzeAffectedCompilationUnits() throws CoreException{
		RefactoringStatus result= new RefactoringStatus();
		fReferences= Checks.excludeCompilationUnits(fReferences, result);
		if (result.hasFatalError())
			return result;
		
		result.merge(Checks.checkCompileErrorsInAffectedFiles(fReferences));	
		return result;
	}
	
	private IFile[] getAllFilesToModify() {
		return ResourceUtil.getFiles(fChangeManager.getAllCompilationUnits());
	}
	
	private SearchPattern createSearchPattern(){
		return SearchPattern.createPattern(fField, IJavaSearchConstants.REFERENCES);
	}
	
	private IJavaSearchScope createRefactoringScope() throws CoreException{
		return RefactoringScopeFactory.create(fField);
	}
	
	private SearchResultGroup[] getReferences(IProgressMonitor pm, RefactoringStatus status) throws CoreException{
		return RefactoringSearchEngine.search(createSearchPattern(), createRefactoringScope(), pm, status);
	}
	
	// ---------- Changes -----------------

	/* non java-doc
	 * IRefactoring#createChange
	 */
	public Change createChange(IProgressMonitor pm) throws CoreException {
		try{
			return new DynamicValidationStateChange(RefactoringCoreMessages.Change_javaChanges, fChangeManager.getAllChanges()); 
		} finally{
			pm.done();
		}	
	}
	
	//----------
	
	private RefactoringStatus createChanges(IProgressMonitor pm) throws CoreException {
		pm.beginTask(RefactoringCoreMessages.RenameFieldRefactoring_checking, 10); 
		RefactoringStatus result= new RefactoringStatus();
		fChangeManager= new TextChangeManager(true);

		addDeclarationUpdate();
		
		if (fUpdateReferences) {
			addReferenceUpdates(new SubProgressMonitor(pm, 1));
			result.merge(analyzeRenameChanges(new SubProgressMonitor(pm, 2)));
			if (result.hasFatalError())
				return result;
		} else {
			pm.worked(3);
		}
		
		if (getGetter() != null && fRenameGetter) {
			addGetterOccurrences(new SubProgressMonitor(pm, 1), result);
		} else {
			pm.worked(1);
		}
					
		if (getSetter() != null && fRenameSetter) {
			addSetterOccurrences(new SubProgressMonitor(pm, 1), result);
		} else {
			pm.worked(1);
		}

		if (fUpdateTextualMatches) {
			addTextMatches(new SubProgressMonitor(pm, 5));
		} else {
			pm.worked(5);
		}
		pm.done();
		return result;
	}

	private void addDeclarationUpdate() throws CoreException { 
		TextEdit textEdit= new ReplaceEdit(fField.getNameRange().getOffset(), fField.getElementName().length(), getNewElementName());
		ICompilationUnit cu= fField.getCompilationUnit();
		String groupName= RefactoringCoreMessages.RenameFieldRefactoring_Update_field_declaration; 
		TextChangeCompatibility.addTextEdit(fChangeManager.get(cu), groupName, textEdit);
	}
	
	private void addReferenceUpdates(IProgressMonitor pm) {
		pm.beginTask("", fReferences.length); //$NON-NLS-1$
		String editName= RefactoringCoreMessages.RenameFieldRefactoring_Update_field_reference; 
		for (int i= 0; i < fReferences.length; i++){
			ICompilationUnit cu= fReferences[i].getCompilationUnit();
			if (cu == null)
				continue;
			SearchMatch[] results= fReferences[i].getSearchResults();
			for (int j= 0; j < results.length; j++){
				TextChangeCompatibility.addTextEdit(fChangeManager.get(cu), editName, createTextChange(results[j]));
			}
			pm.worked(1);			
		}
	}
	
	private TextEdit createTextChange(SearchMatch match) {
		String oldName= fField.getElementName();
		int offset= match.getOffset() + match.getLength() - oldName.length(); // could be qualified
		return new ReplaceEdit(offset, oldName.length(), getNewElementName());
	}
	
	private void addGetterOccurrences(IProgressMonitor pm, RefactoringStatus status) throws CoreException {
		addAccessorOccurrences(pm, getGetter(), RefactoringCoreMessages.RenameFieldRefactoring_Update_getter_occurrence, getNewGetterName(), status); 
	}
	
	private void addSetterOccurrences(IProgressMonitor pm, RefactoringStatus status) throws CoreException {
		addAccessorOccurrences(pm, getSetter(), RefactoringCoreMessages.RenameFieldRefactoring_Update_setter_occurrence, getNewSetterName(), status); 
	}

	private void addAccessorOccurrences(IProgressMonitor pm, IMethod accessor, String editName, String newAccessorName, RefactoringStatus status) throws CoreException {
		Assert.isTrue(accessor.exists());
		
		IJavaSearchScope scope= RefactoringScopeFactory.create(accessor);
		SearchPattern pattern= SearchPattern.createPattern(accessor, IJavaSearchConstants.ALL_OCCURRENCES, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE);
		SearchResultGroup[] groupedResults= RefactoringSearchEngine.search(
			pattern, scope, new MethodOccurenceCollector(accessor.getElementName()), pm, status);
		
		for (int i= 0; i < groupedResults.length; i++) {
			ICompilationUnit cu= groupedResults[i].getCompilationUnit();
			if (cu == null)
				continue;
			SearchMatch[] results= groupedResults[i].getSearchResults();
			for (int j= 0; j < results.length; j++){
				SearchMatch searchResult= results[j];
				TextEdit edit= new ReplaceEdit(searchResult.getOffset(), searchResult.getLength(), newAccessorName);
				TextChangeCompatibility.addTextEdit(fChangeManager.get(cu), editName, edit);
			}
		}
	}
	
	private void addTextMatches(IProgressMonitor pm) throws CoreException {
		TextMatchUpdater.perform(pm, createRefactoringScope(), this, fChangeManager, fReferences);
	}	
	
	//----------------
	private RefactoringStatus analyzeRenameChanges(IProgressMonitor pm) throws CoreException {
		try {
			pm.beginTask("", 2); //$NON-NLS-1$
			RefactoringStatus result= new RefactoringStatus();
			SearchResultGroup[] oldReferences= fReferences;
			SearchResultGroup[] newReferences= getNewReferences(new SubProgressMonitor(pm, 1), fChangeManager, result);
			result.merge(RenameAnalyzeUtil.analyzeRenameChanges2(fChangeManager, oldReferences, newReferences, getNewElementName()));
			return result;
		} finally{
			pm.done();
			if (fNewWorkingCopies != null){
				for (int i= 0; i < fNewWorkingCopies.length; i++) {
					fNewWorkingCopies[i].destroy();
				}
			}	
		}
	}

	private SearchResultGroup[] getNewReferences(IProgressMonitor pm, TextChangeManager manager, RefactoringStatus status) throws CoreException {
		pm.beginTask("", 2); //$NON-NLS-1$
		ICompilationUnit[] compilationUnitsToModify= manager.getAllCompilationUnits();
		fNewWorkingCopies= RenameAnalyzeUtil.getNewWorkingCopies(compilationUnitsToModify, manager, new SubProgressMonitor(pm, 1));
		
		ICompilationUnit declaringCuWorkingCopy= RenameAnalyzeUtil.findWorkingCopyForCu(fNewWorkingCopies, fField.getCompilationUnit());
		if (declaringCuWorkingCopy == null)
			return new SearchResultGroup[0];
		
		IField field= getNewField(declaringCuWorkingCopy);
		if (field == null || ! field.exists())
			return new SearchResultGroup[0];
		
		SearchPattern newPattern= SearchPattern.createPattern(field, IJavaSearchConstants.REFERENCES);			
		return RefactoringSearchEngine.search(newPattern, createRefactoringScope(), new SubProgressMonitor(pm, 1), fNewWorkingCopies, status);
	}

	private IField getNewField(ICompilationUnit newWorkingCopyOfDeclaringCu) throws CoreException{
		IType type= fField.getDeclaringType();
		IType typeWc= (IType) JavaModelUtil.findInCompilationUnit(newWorkingCopyOfDeclaringCu, type);
		if (typeWc == null)
			return null;
		
		return typeWc.getField(getNewElementName());
	}
	
}
