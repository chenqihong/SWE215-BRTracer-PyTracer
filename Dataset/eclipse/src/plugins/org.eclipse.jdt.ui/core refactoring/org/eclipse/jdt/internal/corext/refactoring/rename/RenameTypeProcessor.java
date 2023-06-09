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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.text.edits.ReplaceEdit;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;

import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusContext;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.RenameArguments;
import org.eclipse.ltk.core.refactoring.participants.SharableParticipants;
import org.eclipse.ltk.core.refactoring.participants.ValidateEditChecker;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationStateChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.RenameCompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.RenameResourceChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextChangeCompatibility;
import org.eclipse.jdt.internal.corext.refactoring.participants.JavaProcessors;
import org.eclipse.jdt.internal.corext.refactoring.participants.ResourceModifications;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IQualifiedNameUpdating;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IReferenceUpdating;
import org.eclipse.jdt.internal.corext.refactoring.tagging.ITextUpdating;
import org.eclipse.jdt.internal.corext.refactoring.util.Changes;
import org.eclipse.jdt.internal.corext.refactoring.util.JavaElementUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.QualifiedNameFinder;
import org.eclipse.jdt.internal.corext.refactoring.util.QualifiedNameSearchResult;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringASTParser;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.JdtFlags;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.SearchUtils;
import org.eclipse.jdt.internal.corext.util.WorkingCopyUtil;

public class RenameTypeProcessor extends JavaRenameProcessor implements ITextUpdating, IReferenceUpdating, IQualifiedNameUpdating {
	
	private IType fType;
	private SearchResultGroup[] fReferences;
	private TextChangeManager fChangeManager;
	private QualifiedNameSearchResult fQualifiedNameSearchResult;
	
	private boolean fUpdateReferences;
	
	private boolean fUpdateTextualMatches;

	private boolean fUpdateQualifiedNames;
	private String fFilePatterns;

	public static final String IDENTIFIER= "org.eclipse.jdt.ui.renameTypeProcessor"; //$NON-NLS-1$
	
	public RenameTypeProcessor(IType type) {
		fType= type;
		setNewElementName(fType.getElementName());
		fUpdateReferences= true; //default is yes
		fUpdateTextualMatches= false;
	}
	
	public IType getType() {
		return fType;
	}

	//---- IRefactoringProcessor ---------------------------------------------------

	public String getIdentifier() {
		return IDENTIFIER;
	}
	
	public boolean isApplicable() throws CoreException {
		return RefactoringAvailabilityTester.isRenameAvailable(fType);
	}
	 
	public String getProcessorName() {
		return Messages.format(
			RefactoringCoreMessages.RenameTypeRefactoring_name,  //$NON-NLS-1$
			new String[]{JavaModelUtil.getFullyQualifiedName(fType), getNewElementName()});
	}
	
	protected String[] getAffectedProjectNatures() throws CoreException {
		return JavaProcessors.computeAffectedNatures(fType);
	}

	public Object[] getElements() {
		return new Object[] {fType};
	}
	
	protected void loadDerivedParticipants(RefactoringStatus status, List result, String[] natures, SharableParticipants shared) throws CoreException {
		String newCUName= getNewElementName() + ".java"; //$NON-NLS-1$
		RenameArguments arguments= new RenameArguments(newCUName, getUpdateReferences());
		loadDerivedParticipants(status, result, 
			computeDerivedElements(), arguments, 
			computeResourceModifications(), natures, shared);
	}
	
	private Object[] computeDerivedElements() {
		if (!(Checks.isTopLevel(fType) && fType.getCompilationUnit().getElementName().equals(fType.getElementName() + ".java"))) //$NON-NLS-1$
			return new Object[0];
		return new Object[] { fType.getCompilationUnit() };
	}
	
	private ResourceModifications computeResourceModifications() {
		if (!(Checks.isTopLevel(fType) && fType.getCompilationUnit().getElementName().equals(fType.getElementName() + ".java"))) //$NON-NLS-1$
			return null;
		IResource resource= fType.getCompilationUnit().getResource();
		if (resource == null)
			return null;
		ResourceModifications result= new ResourceModifications();
		result.setRename(resource, new RenameArguments(getNewElementName() + ".java", getUpdateReferences())); //$NON-NLS-1$
		return result;		
	}
		
	//---- IRenameProcessor ----------------------------------------------
	
	public String getCurrentElementName(){
		return fType.getElementName();
	}
	
	public String getCurrentElementQualifier(){
		return JavaModelUtil.getTypeContainerName(fType);
	}
	
	public RefactoringStatus checkNewElementName(String newName){
		Assert.isNotNull(newName, "new name"); //$NON-NLS-1$
		RefactoringStatus result= Checks.checkTypeName(newName);
		if (Checks.isAlreadyNamed(fType, newName))
			result.addFatalError(RefactoringCoreMessages.RenameTypeRefactoring_choose_another_name);	 
		return result;
	}
	
	public Object getNewElement() {
		IPackageFragment parent= fType.getPackageFragment();
		ICompilationUnit cu;
		if (Checks.isTopLevel(fType) && fType.getCompilationUnit().getElementName().equals(fType.getElementName() + ".java")) //$NON-NLS-1$
			cu= parent.getCompilationUnit(getNewElementName() + ".java"); //$NON-NLS-1$
		else
			cu= fType.getCompilationUnit();	
		return cu.getType(getNewElementName());
	}

	//---- ITextUpdating -------------------------------------------------

	public boolean canEnableTextUpdating() {
		return true;
	}
	
	public boolean getUpdateTextualMatches() {
		return fUpdateTextualMatches;
	}
	public void setUpdateTextualMatches(boolean update) {
		fUpdateTextualMatches= update;
	}

	//---- IReferenceUpdating --------------------------------------
		
	public void setUpdateReferences(boolean update){
		fUpdateReferences= update;
	}
	
	public boolean canEnableUpdateReferences(){
		return true;
	}
	
	public boolean getUpdateReferences(){
		return fUpdateReferences;
	}

	//---- IQualifiedNameUpdating ----------------------------------

	public boolean canEnableQualifiedNameUpdating() {
		return !fType.getPackageFragment().isDefaultPackage() && !(fType.getParent() instanceof IType);
	}
	
	public boolean getUpdateQualifiedNames() {
		return fUpdateQualifiedNames;
	}
	
	public void setUpdateQualifiedNames(boolean update) {
		fUpdateQualifiedNames= update;
	}
	
	public String getFilePatterns() {
		return fFilePatterns;
	}
	
	public void setFilePatterns(String patterns) {
		Assert.isNotNull(patterns);
		fFilePatterns= patterns;
	}
	
	//------------- Conditions -----------------
	
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException {
		IType orig= (IType)WorkingCopyUtil.getOriginal(fType);
		if (orig == null || ! orig.exists()){
			String message= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_does_not_exist, 
						new String[]{JavaModelUtil.getFullyQualifiedName(fType), fType.getCompilationUnit().getElementName()});
			return RefactoringStatus.createFatalErrorStatus(message);
		}	
		fType= orig;
		
		return Checks.checkIfCuBroken(fType);
	}

	/* non java-doc
	 * @see Refactoring#checkInput
	 */		
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm, CheckConditionsContext context) throws CoreException {
		Assert.isNotNull(fType, "type"); //$NON-NLS-1$
		Assert.isNotNull(getNewElementName(), "newName"); //$NON-NLS-1$
		RefactoringStatus result= new RefactoringStatus();
		try{
			pm.beginTask("", 120); //$NON-NLS-1$
			pm.setTaskName(RefactoringCoreMessages.RenameTypeRefactoring_checking); 
			result.merge(checkNewElementName(getNewElementName()));
			if (result.hasFatalError())
				return result;
			result.merge(Checks.checkIfCuBroken(fType));
			if (result.hasFatalError())
				return result;
			pm.worked(2);
		
			result.merge(checkTypesInCompilationUnit());
			pm.worked(1);
		
			result.merge(checkForMethodsWithConstructorNames());
			pm.worked(1);
		
			result.merge(checkImportedTypes());	
			pm.worked(1);
		
			if (Checks.isTopLevel(fType) && (JdtFlags.isPublic(fType)))
				result.merge(Checks.checkCompilationUnitNewName(fType.getCompilationUnit(), getNewElementName()));
			pm.worked(1);	
			
			if (Checks.isTopLevel(fType) && fType.getCompilationUnit().getElementName().equals(fType.getElementName() + ".java")) //$NON-NLS-1$
				result.merge(checkNewPathValidity());
			pm.worked(1);	
			
			result.merge(checkEnclosingTypes());
			pm.worked(1);	
			
			result.merge(checkEnclosedTypes());
			pm.worked(1);	
			
			result.merge(checkTypesInPackage());
			pm.worked(1);	
			
			result.merge(checkTypesImportedInCu());
			pm.worked(1);	
		
			result.merge(Checks.checkForNativeMethods(fType));
			pm.worked(1);	
		
			result.merge(Checks.checkForMainMethod(fType));
			pm.worked(1);	
			
			// before doing any expensive analysis
			if (result.hasFatalError())
				return result;
							
			result.merge(analyseEnclosedTypes());
			pm.worked(1);
			// before doing _the really_ expensive analysis
			if (result.hasFatalError())
				return result;
			
			if (fUpdateReferences){
				pm.setTaskName(RefactoringCoreMessages.RenameTypeRefactoring_searching);	 
				fReferences= RefactoringSearchEngine.search(SearchPattern.createPattern(fType, IJavaSearchConstants.REFERENCES, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE), RefactoringScopeFactory.create(fType), new SubProgressMonitor(pm, 35), result);
			} else
				fReferences= new SearchResultGroup[0];
				pm.worked(35);
	
			pm.setTaskName(RefactoringCoreMessages.RenameTypeRefactoring_checking); 
			if (pm.isCanceled())
				throw new OperationCanceledException();
			
			if (fUpdateReferences) {
				result.merge(analyzeAffectedCompilationUnits(new SubProgressMonitor(pm, 25)));
			} else {
				Checks.checkCompileErrorsInAffectedFile(result, fType.getResource());
				pm.worked(25);
			}
			
			if (result.hasFatalError())
				return result;
			
			fChangeManager= createChangeManager(new SubProgressMonitor(pm, 35));
	
			if (fUpdateQualifiedNames)			
				computeQualifiedNameMatches(new SubProgressMonitor(pm, 10));
			else
				pm.worked(10);
	
			ValidateEditChecker checker= (ValidateEditChecker)context.getChecker(ValidateEditChecker.class);
			checker.addFiles(getAllFilesToModify());
			return result;
		} finally {
			pm.done();
		}	
	}
	
	private RefactoringStatus checkNewPathValidity() {
		IContainer c= ResourceUtil.getResource(fType).getParent();
		
		String notRename= RefactoringCoreMessages.RenameTypeRefactoring_will_not_rename; 
		IStatus status= c.getWorkspace().validateName(getNewElementName(), IResource.FILE);
		if (status.getSeverity() == IStatus.ERROR)
			return RefactoringStatus.createWarningStatus(status.getMessage() + ". " + notRename); //$NON-NLS-1$
		
		status= c.getWorkspace().validatePath(createNewPath(getNewElementName()), IResource.FILE);
		if (status.getSeverity() == IStatus.ERROR)
			return RefactoringStatus.createWarningStatus(status.getMessage() + ". " + notRename); //$NON-NLS-1$

		return new RefactoringStatus();
	}
	
	private String createNewPath(String newName) {
		return ResourceUtil.getResource(fType).getFullPath().removeLastSegments(1).append(newName).toString();
	}
	
	private RefactoringStatus checkTypesImportedInCu() throws CoreException {
		IImportDeclaration imp= getImportedType(fType.getCompilationUnit(), getNewElementName());
		
		if (imp == null)
			return null;	
			
		String msg= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_imported, 
											new Object[]{getNewElementName(), ResourceUtil.getResource(fType).getFullPath()});
		IJavaElement grandParent= imp.getParent().getParent();
		if (grandParent instanceof ICompilationUnit)
			return RefactoringStatus.createErrorStatus(msg, JavaStatusContext.create(imp));

		return null;	
	}
	
	private RefactoringStatus checkTypesInPackage() throws CoreException {
		IType type= Checks.findTypeInPackage(fType.getPackageFragment(), getNewElementName());
		if (type == null || ! type.exists())
			return null;
		String msg= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_exists, 
																	new String[]{getNewElementName(), fType.getPackageFragment().getElementName()});
		return RefactoringStatus.createErrorStatus(msg, JavaStatusContext.create(type));
	}
	
	private RefactoringStatus checkEnclosedTypes() throws CoreException {
		IType enclosedType= findEnclosedType(fType, getNewElementName());
		if (enclosedType == null)
			return null;
		String msg= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_encloses,  
																		new String[]{JavaModelUtil.getFullyQualifiedName(fType), getNewElementName()});
		return RefactoringStatus.createErrorStatus(msg, JavaStatusContext.create(enclosedType));
	}
	
	private RefactoringStatus checkEnclosingTypes() {
		IType enclosingType= findEnclosingType(fType, getNewElementName());
		if (enclosingType == null)
			return null;
			
		String msg= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_enclosed,
								new String[]{JavaModelUtil.getFullyQualifiedName(fType), getNewElementName()});
		return RefactoringStatus.createErrorStatus(msg, JavaStatusContext.create(enclosingType));
	}
	
	private static IType findEnclosedType(IType type, String newName) throws CoreException {
		IType[] enclosedTypes= type.getTypes();
		for (int i= 0; i < enclosedTypes.length; i++){
			if (newName.equals(enclosedTypes[i].getElementName()) || findEnclosedType(enclosedTypes[i], newName) != null)
				return enclosedTypes[i];
		}
		return null;
	}
		
	private static IType findEnclosingType(IType type, String newName) {
		IType enclosing= type.getDeclaringType();
		while (enclosing != null){
			if (newName.equals(enclosing.getElementName()))
				return enclosing;
			else 
				enclosing= enclosing.getDeclaringType();	
		}
		return null;
	}
	
	private static IImportDeclaration getImportedType(ICompilationUnit cu, String typeName) throws CoreException {
		IImportDeclaration[] imports= cu.getImports();
		String dotTypeName= "." + typeName; //$NON-NLS-1$
		for (int i= 0; i < imports.length; i++){
			if (imports[i].getElementName().endsWith(dotTypeName))
				return imports[i];
		}
		return null;
	}
	
	private RefactoringStatus checkForMethodsWithConstructorNames()  throws CoreException{
		IMethod[] methods= fType.getMethods();
		for (int i= 0; i < methods.length; i++){
			if (methods[i].isConstructor())
				continue;
			RefactoringStatus check= Checks.checkIfConstructorName(methods[i], methods[i].getElementName(), getNewElementName());	
			if (check != null)
				return check;
		}
		return null;
	}	
	
	private RefactoringStatus checkImportedTypes() throws CoreException {
		RefactoringStatus result= new RefactoringStatus();
		IImportDeclaration[] imports= fType.getCompilationUnit().getImports();	
		for (int i= 0; i < imports.length; i++)
			analyzeImportDeclaration(imports[i], result);
		return result;
	}
	
	private RefactoringStatus checkTypesInCompilationUnit() {
		RefactoringStatus result= new RefactoringStatus();
		if (! Checks.isTopLevel(fType)){ //the other case checked in checkTypesInPackage
			IType siblingType= fType.getDeclaringType().getType(getNewElementName());
			if (siblingType.exists()){
				String msg= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_member_type_exists, 
																		new String[]{getNewElementName(), JavaModelUtil.getFullyQualifiedName(fType.getDeclaringType())});
				result.addError(msg, JavaStatusContext.create(siblingType));
			}
		}
		return result;
	}
	
	private RefactoringStatus analyseEnclosedTypes() throws CoreException {
		final ISourceRange typeRange= fType.getSourceRange();
		final RefactoringStatus result= new RefactoringStatus();
		CompilationUnit cuNode= new RefactoringASTParser(AST.JLS3).parse(fType.getCompilationUnit(), false);
		cuNode.accept(new ASTVisitor(){
			
			public boolean visit(TypeDeclaration node){ // enums and annotations can't be local
				if (node.getStartPosition() <= typeRange.getOffset())
					return true;
				if (node.getStartPosition() > typeRange.getOffset() + typeRange.getLength())
					return true;
		
				if (getNewElementName().equals(node.getName().getIdentifier())){
					RefactoringStatusContext	context= JavaStatusContext.create(fType.getCompilationUnit(), node);
					String msg= null;
					if (node.isLocalTypeDeclaration()){
						msg= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_local_type, 
									new String[]{JavaElementUtil.createSignature(fType), getNewElementName()});
					}	
					else if (node.isMemberTypeDeclaration()){
						msg= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_member_type, 
								new String[]{JavaElementUtil.createSignature(fType), getNewElementName()});
					}	
					if (msg != null)	
						result.addError(msg, context);
				}
		
				MethodDeclaration[] methods= node.getMethods();
				for (int i= 0; i < methods.length; i++) {
					if (Modifier.isNative(methods[i].getModifiers())){
						RefactoringStatusContext	context= JavaStatusContext.create(fType.getCompilationUnit(), methods[i]);
						String msg= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_enclosed_type_native, node.getName().getIdentifier());
						result.addWarning(msg, context); 
					}	
				}
				return true;
			}
		});
		return result;
	}
	
	private static ICompilationUnit getCompilationUnit(IImportDeclaration imp) {
		return (ICompilationUnit)imp.getParent().getParent();
	}
	
	private void analyzeImportedTypes(IType[] types, RefactoringStatus result, IImportDeclaration imp) throws CoreException {
		for (int i= 0; i < types.length; i++) {
			//could this be a problem (same package imports)?
			if (JdtFlags.isPublic(types[i]) && types[i].getElementName().equals(getNewElementName())){
				String msg= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_name_conflict1, 
																			new Object[]{JavaModelUtil.getFullyQualifiedName(types[i]), getFullPath(getCompilationUnit(imp))});
				result.addError(msg, JavaStatusContext.create(imp));
			}
		}
	}
	
	private static IJavaElement convertFromImportDeclaration(IImportDeclaration declaration) throws CoreException {
			if (declaration.isOnDemand()){ 
				String packageName= declaration.getElementName().substring(0, declaration.getElementName().length() - 2);
				return JavaModelUtil.findTypeContainer(declaration.getJavaProject(), packageName);
			} else 
				return JavaModelUtil.findTypeContainer(declaration.getJavaProject(), declaration.getElementName());
	}

	private void analyzeImportDeclaration(IImportDeclaration imp, RefactoringStatus result) throws CoreException{
		if (!imp.isOnDemand())
			return; //analyzed earlier
		
		IJavaElement imported= convertFromImportDeclaration(imp);
		if (imported == null)
			return;
			
		if (imported instanceof IPackageFragment){
			ICompilationUnit[] cus= ((IPackageFragment)imported).getCompilationUnits();
			for (int i= 0; i < cus.length; i++) {
				analyzeImportedTypes(cus[i].getTypes(), result, imp);
			}	
		} else {
			//cast safe: see JavaModelUtility.convertFromImportDeclaration
			analyzeImportedTypes(((IType)imported).getTypes(), result, imp);
		}
	}
	
	private IFile[] getAllFilesToModify() throws CoreException {
		List result= new ArrayList();
		result.addAll(Arrays.asList(ResourceUtil.getFiles(fChangeManager.getAllCompilationUnits())));
		if (fQualifiedNameSearchResult != null)
			result.addAll(Arrays.asList(fQualifiedNameSearchResult.getAllFiles()));
		if (willRenameCU())
			result.add(ResourceUtil.getFile(fType.getCompilationUnit()));
		return (IFile[]) result.toArray(new IFile[result.size()]);
	}
	
	/*
	 * Analyzes all compilation units in which type is referenced
	 */
	private RefactoringStatus analyzeAffectedCompilationUnits(IProgressMonitor pm) throws CoreException {
		RefactoringStatus result= new RefactoringStatus();
		fReferences= Checks.excludeCompilationUnits(fReferences, result);
		if (result.hasFatalError())
			return result;
			
		result.merge(Checks.checkCompileErrorsInAffectedFiles(fReferences, fType.getResource()));	
		
		pm.beginTask("", fReferences.length); //$NON-NLS-1$
		result.merge(checkConflictingTypes(pm));
		return result;
	}
	
	private RefactoringStatus checkConflictingTypes(IProgressMonitor pm) throws CoreException {
		RefactoringStatus result= new RefactoringStatus();
		IJavaSearchScope scope= RefactoringScopeFactory.create(fType);
		SearchPattern pattern= SearchPattern.createPattern(getNewElementName(),
				IJavaSearchConstants.TYPE, IJavaSearchConstants.ALL_OCCURRENCES, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE);
		ICompilationUnit[] cusWithReferencesToConflictingTypes= RefactoringSearchEngine.findAffectedCompilationUnits(pattern, scope, pm, result);
		if (cusWithReferencesToConflictingTypes.length == 0)
			return result;
		ICompilationUnit[] 	cusWithReferencesToRenamedType= getCus(fReferences);

		ICompilationUnit[] intersection= isIntersectionEmpty(cusWithReferencesToRenamedType, cusWithReferencesToConflictingTypes);
		if (intersection.length == 0)
			return result;
		
		for (int i= 0; i < intersection.length; i++) {
			RefactoringStatusContext context= JavaStatusContext.create(intersection[i]);
			String message= Messages.format(RefactoringCoreMessages.RenameTypeRefactoring_another_type, 
				new String[]{getNewElementName(), intersection[i].getElementName()});
			result.addError(message, context);
		}	
		return result;
	}
	
	private static ICompilationUnit[] isIntersectionEmpty(ICompilationUnit[] a1, ICompilationUnit[] a2){
		Set set1= new HashSet(Arrays.asList(a1));
		Set set2= new HashSet(Arrays.asList(a2));
		set1.retainAll(set2);
		return (ICompilationUnit[]) set1.toArray(new ICompilationUnit[set1.size()]);
	}
	
	private static ICompilationUnit[] getCus(SearchResultGroup[] searchResultGroups){
		List cus= new ArrayList(searchResultGroups.length);
		for (int i= 0; i < searchResultGroups.length; i++) {
			ICompilationUnit cu= searchResultGroups[i].getCompilationUnit();
			if (cu != null)
				cus.add(cu);
		}
		return (ICompilationUnit[]) cus.toArray(new ICompilationUnit[cus.size()]);
	}
	
	private static String getFullPath(ICompilationUnit cu) {
		Assert.isTrue(cu.exists());
		return ResourceUtil.getResource(cu).getFullPath().toString();
	}
	
	//------------- Changes ---------------
	
	/*
	 * non java-doc
	 * @see IRefactoring#createChange
	 */
	public Change createChange(IProgressMonitor pm) throws CoreException{
		pm.beginTask(RefactoringCoreMessages.RenameTypeRefactoring_creating_change, 4); 
		final DynamicValidationStateChange result= new DynamicValidationStateChange(
			RefactoringCoreMessages.Change_javaChanges); 
		result.addAll(fChangeManager.getAllChanges());
		if (willRenameCU()) {
			IResource resource= ResourceUtil.getResource(fType);
			if (resource != null && resource.isLinked()) {
				result.add(new RenameResourceChange(ResourceUtil.getResource(fType), getNewElementName() + ".java")); //$NON-NLS-1$)
			} else {
				result.add(new RenameCompilationUnitChange(fType.getCompilationUnit(), getNewElementName() + ".java")); //$NON-NLS-1$
			}
		}
		pm.worked(1);	
		return result;	
	}
	
	public Change postCreateChange(Change[] participantChanges, IProgressMonitor pm) throws CoreException {
		if (fQualifiedNameSearchResult != null) {	
			return fQualifiedNameSearchResult.getSingleChange(Changes.getModifiedFiles(participantChanges));
		} else {
			return null;
		}
	}
	
	private boolean willRenameCU() throws CoreException{
		if (! (Checks.isTopLevel(fType) && fType.getCompilationUnit().getElementName().equals(fType.getElementName() + ".java"))) //$NON-NLS-1$
			return false;
		if (! checkNewPathValidity().isOK())
			return false;
		if (! Checks.checkCompilationUnitNewName(fType.getCompilationUnit(), getNewElementName()).isOK())
			return false;
		return true;	
	}
	
	private TextChangeManager createChangeManager(IProgressMonitor pm) throws CoreException {
		try{
			pm.beginTask("", 7); //$NON-NLS-1$
			TextChangeManager manager= new TextChangeManager();
					
			if (fUpdateReferences)
				addReferenceUpdates(manager, new SubProgressMonitor(pm, 3));
	
			pm.worked(1);
			
			IResource resource= ResourceUtil.getResource(fType);
			// if we have a linked resource then we don't use CU renaming 
			// directly. So we have to update the code by ourselves.
			if ((resource != null && resource.isLinked()) || !willRenameCU()) {
				addTypeDeclarationUpdate(manager);
				pm.worked(1);
				
				addConstructorRenames(manager);
				pm.worked(1);
			} else {
				pm.worked(2);
			}
			
			if (fUpdateTextualMatches) {
				pm.subTask(RefactoringCoreMessages.RenameTypeRefactoring_searching_text); 
				TextMatchUpdater.perform(new SubProgressMonitor(pm, 1), RefactoringScopeFactory.create(fType), this, manager, fReferences);
			}
			
			return manager;
		} finally{
			pm.done();
		}	
	}
	
	private void addTypeDeclarationUpdate(TextChangeManager manager) throws CoreException {
		String name= RefactoringCoreMessages.RenameTypeRefactoring_update; 
		int typeNameLength= fType.getElementName().length();
		ICompilationUnit cu= fType.getCompilationUnit();
		TextChangeCompatibility.addTextEdit(manager.get(cu), name, new ReplaceEdit(fType.getNameRange().getOffset(), typeNameLength, getNewElementName()));
	}
	
	private void addConstructorRenames(TextChangeManager manager) throws CoreException {
		ICompilationUnit cu= fType.getCompilationUnit();
		IMethod[] methods= fType.getMethods();
		int typeNameLength= fType.getElementName().length();
		for (int i= 0; i < methods.length; i++){
			if (methods[i].isConstructor()) {
				/*
				 * constructor declarations cannot be fully qualified so we can use simple replace here
				 *
				 * if (methods[i].getNameRange() == null), then it's a binary file so it's wrong anyway 
				 * (checked as a precondition)
				 */				
				String name= RefactoringCoreMessages.RenameTypeRefactoring_rename_constructor; 
				TextChangeCompatibility.addTextEdit(manager.get(cu), name, new ReplaceEdit(methods[i].getNameRange().getOffset(), typeNameLength, getNewElementName()));
			}
		}
	}
	
	private void addReferenceUpdates(TextChangeManager manager, IProgressMonitor pm) {
		pm.beginTask("", fReferences.length); //$NON-NLS-1$
		for (int i= 0; i < fReferences.length; i++){
			ICompilationUnit cu= fReferences[i].getCompilationUnit();
			if (cu == null)
				continue;
					
			String name= RefactoringCoreMessages.RenameTypeRefactoring_update_reference; 
			SearchMatch[] results= fReferences[i].getSearchResults();

			for (int j= 0; j < results.length; j++){
				SearchMatch match= results[j];
				String oldName= fType.getElementName();
				int offset= match.getOffset() + match.getLength() - oldName.length(); // reference may be qualified
				TextChangeCompatibility.addTextEdit(manager.get(cu), name, new ReplaceEdit(offset, oldName.length(), getNewElementName()));
			}
			pm.worked(1);
		}
	}
	
	private void computeQualifiedNameMatches(IProgressMonitor pm) throws CoreException {
		IPackageFragment fragment= fType.getPackageFragment();
		if (fQualifiedNameSearchResult == null)
			fQualifiedNameSearchResult= new QualifiedNameSearchResult();
		QualifiedNameFinder.process(fQualifiedNameSearchResult, fType.getFullyQualifiedName(),  
			fragment.getElementName() + "." + getNewElementName(), //$NON-NLS-1$
			fFilePatterns, fType.getJavaProject().getProject(), pm);
	}	
}
