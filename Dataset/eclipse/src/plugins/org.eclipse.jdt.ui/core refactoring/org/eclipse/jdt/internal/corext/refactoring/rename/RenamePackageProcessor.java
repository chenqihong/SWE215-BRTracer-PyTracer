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
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipse.core.filebuffers.ITextFileBuffer;

import org.eclipse.text.edits.ReplaceEdit;
import org.eclipse.text.edits.TextEdit;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.codemanipulation.ImportRewrite;
import org.eclipse.jdt.internal.corext.refactoring.Checks;
import org.eclipse.jdt.internal.corext.refactoring.CollectingSearchRequestor;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringAvailabilityTester;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.refactoring.changes.DynamicValidationStateChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.RenamePackageChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.TextChangeCompatibility;
import org.eclipse.jdt.internal.corext.refactoring.participants.JavaProcessors;
import org.eclipse.jdt.internal.corext.refactoring.participants.ResourceModifications;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IQualifiedNameUpdating;
import org.eclipse.jdt.internal.corext.refactoring.tagging.IReferenceUpdating;
import org.eclipse.jdt.internal.corext.refactoring.tagging.ITextUpdating;
import org.eclipse.jdt.internal.corext.refactoring.util.Changes;
import org.eclipse.jdt.internal.corext.refactoring.util.CommentAnalyzer;
import org.eclipse.jdt.internal.corext.refactoring.util.QualifiedNameFinder;
import org.eclipse.jdt.internal.corext.refactoring.util.QualifiedNameSearchResult;
import org.eclipse.jdt.internal.corext.refactoring.util.RefactoringFileBuffers;
import org.eclipse.jdt.internal.corext.refactoring.util.ResourceUtil;
import org.eclipse.jdt.internal.corext.refactoring.util.TextChangeManager;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.Resources;
import org.eclipse.jdt.internal.corext.util.SearchUtils;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusContext;
import org.eclipse.ltk.core.refactoring.participants.CheckConditionsContext;
import org.eclipse.ltk.core.refactoring.participants.MoveArguments;
import org.eclipse.ltk.core.refactoring.participants.SharableParticipants;
import org.eclipse.ltk.core.refactoring.participants.ValidateEditChecker;

public class RenamePackageProcessor extends JavaRenameProcessor implements IReferenceUpdating, ITextUpdating, IQualifiedNameUpdating {
	
	private IPackageFragment fPackage;
	
	/** references to fPackage (can include star imports which also import namesake package fragments) */
	private SearchResultGroup[] fOccurrences;
	
	/** References in CUs from fOccurrences and fPackage to types in namesake packages.
	 * <p>These need an import with the old package name.
	 * <p>- from fOccurrences (without namesakes): may have shared star import
	 * 		(star-import not updated here, but for fOccurrences)
	 * <p>- from fPackage: may have unimported references to types of namesake packages
	 * <p>- both: may have unused imports of namesake packages.
	 * <p>Mutable List of SearchResultGroup. */
	private List fReferencesToTypesInNamesakes;

	/** References in CUs from namesake packages to types in fPackage.
	 * <p>These need an import with the new package name.
	 * <p>Mutable List of SearchResultGroup. */
	private List fReferencesToTypesInPackage;

	private TextChangeManager fChangeManager;
	private QualifiedNameSearchResult fQualifiedNameSearchResult;
	
	private boolean fUpdateReferences;
	private boolean fUpdateTextualMatches;
	private boolean fUpdateQualifiedNames;
	private String fFilePatterns;
	
	public static final String IDENTIFIER= "org.eclipse.jdt.ui.renamePackageProcessor"; //$NON-NLS-1$
	
	//---- IRefactoringProcessor ---------------------------------------------------
	
	public RenamePackageProcessor(IPackageFragment fragment) {
		fPackage= fragment;
		setNewElementName(fPackage.getElementName());
		fUpdateReferences= true;
		fUpdateTextualMatches= false;
	}

	public String getIdentifier() {
		return IDENTIFIER;
	}
	
	public boolean isApplicable() throws CoreException {
		return RefactoringAvailabilityTester.isRenameAvailable(fPackage);
	}
	
	public String getProcessorName(){
		return Messages.format(RefactoringCoreMessages.RenamePackageRefactoring_name,  
						new String[]{fPackage.getElementName(), getNewElementName()});
	}
	
	protected String[] getAffectedProjectNatures() throws CoreException {
		return JavaProcessors.computeAffectedNatures(fPackage);
	}
	
	public Object[] getElements() {
		return new Object[] {fPackage};
	}
	
	protected void loadDerivedParticipants(RefactoringStatus status, List result, String[] natures, SharableParticipants shared) throws CoreException {
		loadDerivedParticipants(status, result, 
			null, null, 
			computeResourceModifications(), natures, shared);
	}
	
	private ResourceModifications computeResourceModifications() throws CoreException {
		ResourceModifications result= new ResourceModifications();
		IContainer container= (IContainer)fPackage.getResource();
		if (container == null)
			return null;
		IPath path= fPackage.getParent().getPath();
		path= path.append(getNewElementName().replace('.', '/'));
		IFolder target= ResourcesPlugin.getWorkspace().getRoot().getFolder(path);
		if (!target.exists()) {
			result.addCreate(target);
		}
		MoveArguments arguments= new MoveArguments(target, getUpdateReferences());
		IResource[] members= container.members();
		int files= 0;
		for (int i= 0; i < members.length; i++) {
			IResource member= members[i];
			if (member instanceof IFile) {
				files++;
				IFile file= (IFile)member;
				if ("class".equals(file.getFileExtension()) && file.isDerived()) //$NON-NLS-1$
					continue;
				result.addMove(member, arguments);
			}
		}
		if (files == members.length) {
			result.addDelete(container);
		}
		return result;		
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
		
	public boolean canEnableUpdateReferences() {
		return true;
	}

	public void setUpdateReferences(boolean update) {
		fUpdateReferences= update;
	}	
	
	public boolean getUpdateReferences(){
		return fUpdateReferences;
	}
	
	//---- IQualifiedNameUpdating ----------------------------------

	public boolean canEnableQualifiedNameUpdating() {
		return !fPackage.isDefaultPackage();
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
	
	//---- IRenameProcessor ----------------------------------------------
	
	public final String getCurrentElementName(){
		return fPackage.getElementName();
	}
	
	public String getCurrentElementQualifier() {
		return ""; //$NON-NLS-1$
	}
	
	public RefactoringStatus checkNewElementName(String newName) throws CoreException {
		Assert.isNotNull(newName, "new name"); //$NON-NLS-1$
		RefactoringStatus result= Checks.checkPackageName(newName);
		if (Checks.isAlreadyNamed(fPackage, newName))
			result.addFatalError(RefactoringCoreMessages.RenamePackageRefactoring_another_name); 
		result.merge(checkPackageInCurrentRoot(newName));
		return result;
	}
	
	public Object getNewElement(){
		IJavaElement parent= fPackage.getParent();
		if (!(parent instanceof IPackageFragmentRoot))
			return fPackage;//??
			
		IPackageFragmentRoot root= (IPackageFragmentRoot)parent;
		return root.getPackageFragment(getNewElementName());
	}
	
	public RefactoringStatus checkInitialConditions(IProgressMonitor pm) throws CoreException {
		return new RefactoringStatus();
	}
	
	public RefactoringStatus checkFinalConditions(IProgressMonitor pm, CheckConditionsContext context) throws CoreException {
		try{
			pm.beginTask("", 20); //$NON-NLS-1$
			pm.setTaskName(RefactoringCoreMessages.RenamePackageRefactoring_checking); 
			RefactoringStatus result= new RefactoringStatus();
			result.merge(checkNewElementName(getNewElementName()));
			pm.worked(1);
			result.merge(checkForNativeMethods());
			pm.worked(1);
			result.merge(checkForMainMethods());
			pm.worked(1);
			
			if (fPackage.isReadOnly()){
				String message= Messages.format(RefactoringCoreMessages.RenamePackageRefactoring_Packagered_only,  
									fPackage.getElementName()); 
				result.addFatalError(message);
			} else if (Resources.isReadOnly(fPackage.getResource())) {
				String message= Messages.format(RefactoringCoreMessages.RenamePackageRefactoring_resource_read_only, 
						fPackage.getElementName());
				result.addError(message);
			}				
				
			if (fUpdateReferences){
				pm.setTaskName(RefactoringCoreMessages.RenamePackageRefactoring_searching);	 
				fOccurrences= getReferences(new SubProgressMonitor(pm, 4), result);	
				fReferencesToTypesInNamesakes= getReferencesToTypesInNamesakes(new SubProgressMonitor(pm, 4), result);
				fReferencesToTypesInPackage= getReferencesToTypesInPackage(new SubProgressMonitor(pm, 4), result);
				pm.setTaskName(RefactoringCoreMessages.RenamePackageRefactoring_checking); 
				result.merge(analyzeAffectedCompilationUnits());
				pm.worked(1);
			} else {
				fOccurrences= new SearchResultGroup[0];
				pm.worked(13);
			}
			result.merge(checkPackageName(getNewElementName()));
			if (result.hasFatalError())
				return result;
				
			fChangeManager= createChangeManager(new SubProgressMonitor(pm, 3));
			
			if (fUpdateQualifiedNames)			
				computeQualifiedNameMatches(new SubProgressMonitor(pm, 1));
			else
				pm.worked(1);
			
			ValidateEditChecker checker= (ValidateEditChecker)context.getChecker(ValidateEditChecker.class);
			checker.addFiles(getAllFilesToModify());
			return result;
		} finally{
			pm.done();
		}	
	}
	
	private SearchResultGroup[] getReferences(IProgressMonitor pm, RefactoringStatus status) throws CoreException {
		IJavaSearchScope scope= RefactoringScopeFactory.create(fPackage);
		SearchPattern pattern= SearchPattern.createPattern(fPackage, IJavaSearchConstants.REFERENCES);
		return RefactoringSearchEngine.search(pattern, scope, pm, status);
	}
		
	private List getReferencesToTypesInNamesakes(IProgressMonitor pm, RefactoringStatus status) throws CoreException {
		pm.beginTask("", 2); //$NON-NLS-1$
		// e.g. renaming B-p.p; project C requires B, X and has ref to B-p.p and X-p.p;
		// goal: find refs to X-p.p in CUs from fOccurrences
		
		// (1) find namesake packages (scope: all packages referenced by CUs in fOccurrences and fPackage)
		IJavaElement[] elements= new IJavaElement[fOccurrences.length + 1]; 
		for (int i= 0; i < fOccurrences.length; i++) {
			elements[i]= fOccurrences[i].getCompilationUnit();
		}
		elements[fOccurrences.length]= fPackage;
		IJavaSearchScope namesakePackagesScope= RefactoringScopeFactory.createReferencedScope(elements);
		IPackageFragment[] namesakePackages= getNamesakePackages(namesakePackagesScope, new SubProgressMonitor(pm, 1));
		if (namesakePackages.length == 0) {
			pm.done();
			return new ArrayList(0);
		}
		
		// (2) find refs in fOccurrences and fPackage to namesake packages
		// (from fOccurrences (without namesakes): may have shared star import)
		// (from fPackage: may have unimported references to types of namesake packages)
		IType[] typesToSearch= getTypesInPackages(namesakePackages);
		if (typesToSearch.length == 0) {
			pm.done();
			return new ArrayList(0);
		}
		SearchPattern pattern= RefactoringSearchEngine.createOrPattern(typesToSearch, IJavaSearchConstants.REFERENCES);
		IJavaSearchScope scope= getPackageAndOccurrencesWithoutNamesakesScope();
		SearchResultGroup[] results= RefactoringSearchEngine.search(pattern, scope, new SubProgressMonitor(pm, 1), status);
		pm.done();
		return new ArrayList(Arrays.asList(results));
	}

	/**
	 * @return all package fragments in <code>scope</code> with same name as <code>fPackage</code>, excluding fPackage
	 */
	private IPackageFragment[] getNamesakePackages(IJavaSearchScope scope, IProgressMonitor pm) throws CoreException {
		SearchPattern pattern= SearchPattern.createPattern(fPackage.getElementName(), IJavaSearchConstants.PACKAGE, IJavaSearchConstants.DECLARATIONS, SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE);
		
		//TODO: put filter logic into requestor
		CollectingSearchRequestor requestor= new CollectingSearchRequestor();
		new SearchEngine().search(pattern, SearchUtils.getDefaultSearchParticipants(), scope, requestor, pm);
		
		List results= requestor.getResults();
		List packageFragments= new ArrayList();
		for (Iterator iter= results.iterator(); iter.hasNext();) {
			SearchMatch result= (SearchMatch) iter.next();
			IJavaElement enclosingElement= SearchUtils.getEnclosingJavaElement(result);
			if (enclosingElement instanceof IPackageFragment) {
				IPackageFragment pack= (IPackageFragment) enclosingElement;
				if (! fPackage.equals(pack))
					packageFragments.add(pack);
			}
		}
		return (IPackageFragment[]) packageFragments.toArray(new IPackageFragment[packageFragments.size()]);
	}

	private IType[] getTypesInPackages(IPackageFragment[] packageFragments) throws JavaModelException {
		List types= new ArrayList();
		for (int i= 0; i < packageFragments.length; i++) {
			IPackageFragment pack= packageFragments[i];
			addContainedTypes(pack, types);
		}
		return (IType[]) types.toArray(new IType[types.size()]);
	}

	private IType[] getTypesInPackage(IPackageFragment packageFragment) throws JavaModelException {
		List types= new ArrayList();
		addContainedTypes(packageFragment, types);
		return (IType[]) types.toArray(new IType[types.size()]);
	}
	
	private void addContainedTypes(IPackageFragment pack, List typesCollector) throws JavaModelException {
		IJavaElement[] children= pack.getChildren();
		for (int c= 0; c < children.length; c++) {
			IJavaElement child= children[c];
			if (child instanceof ICompilationUnit) {
				typesCollector.addAll(Arrays.asList(((ICompilationUnit) child).getTypes()));
			}
		}
	}
	
	/**
	 * @return search scope with
	 * <p>- fPackage and
	 * <p>- all CUs from fOccurrences which are not in a namesake package
	 */
	private IJavaSearchScope getPackageAndOccurrencesWithoutNamesakesScope() {
		List scopeList= new ArrayList();
		scopeList.add(fPackage);
		for (int i= 0; i < fOccurrences.length; i++) {
			ICompilationUnit cu= fOccurrences[i].getCompilationUnit();
			if (cu == null)
				continue;
			IPackageFragment pack= (IPackageFragment) cu.getParent();
			if (! pack.getElementName().equals(fPackage.getElementName()))
				scopeList.add(cu);
		}
		return SearchEngine.createJavaSearchScope((IJavaElement[]) scopeList.toArray(new IJavaElement[scopeList.size()])); 
	}

	private List getReferencesToTypesInPackage(IProgressMonitor pm, RefactoringStatus status) throws CoreException {
		pm.beginTask("", 2); //$NON-NLS-1$
		IJavaSearchScope referencedFromNamesakesScope= RefactoringScopeFactory.create(fPackage);
		IPackageFragment[] namesakePackages= getNamesakePackages(referencedFromNamesakesScope, new SubProgressMonitor(pm, 1));
		if (namesakePackages.length == 0) {
			pm.done();
			return new ArrayList(0);
		}

		IJavaSearchScope scope= SearchEngine.createJavaSearchScope(namesakePackages);
		IType[] typesToSearch= getTypesInPackage(fPackage);
		if (typesToSearch.length == 0) {
			pm.done();
			return new ArrayList(0);
		}
		SearchPattern pattern= RefactoringSearchEngine.createOrPattern(typesToSearch, IJavaSearchConstants.REFERENCES);
		SearchResultGroup[] results= RefactoringSearchEngine.search(pattern, scope, new SubProgressMonitor(pm, 1), status);
		pm.done();
		return new ArrayList(Arrays.asList(results));
	}

	private RefactoringStatus checkForMainMethods() throws CoreException{
		ICompilationUnit[] cus= fPackage.getCompilationUnits();
		RefactoringStatus result= new RefactoringStatus();
		for (int i= 0; i < cus.length; i++)
			result.merge(Checks.checkForMainMethods(cus[i]));
		return result;
	}
	
	private RefactoringStatus checkForNativeMethods() throws CoreException {
		ICompilationUnit[] cus= fPackage.getCompilationUnits();
		RefactoringStatus result= new RefactoringStatus();
		for (int i= 0; i < cus.length; i++)
			result.merge(Checks.checkForNativeMethods(cus[i]));
		return result;
	}
	
	/*
	 * returns true if the new name is ok if the specified root.
	 * if a package fragment with this name exists and has java resources,
	 * then the name is not ok.
	 */
	public static boolean isPackageNameOkInRoot(String newName, IPackageFragmentRoot root) throws CoreException {
		IPackageFragment pack= root.getPackageFragment(newName);
		if (! pack.exists())
			return true;
		else if (! pack.hasSubpackages()) //leaves are no good
			return false;			
		else if (pack.containsJavaResources())
			return false;
		else if (pack.getNonJavaResources().length != 0)
			return false;
		else 
			return true;	
	}
	
	private RefactoringStatus checkPackageInCurrentRoot(String newName) throws CoreException {
		if (isPackageNameOkInRoot(newName, getPackageFragmentRoot()))
			return null;
		else
			return RefactoringStatus.createFatalErrorStatus(RefactoringCoreMessages.RenamePackageRefactoring_package_exists);
	}

	private IPackageFragmentRoot getPackageFragmentRoot() {
		return ((IPackageFragmentRoot)fPackage.getParent());
	}
	
	private RefactoringStatus checkPackageName(String newName) throws CoreException {		
		RefactoringStatus status= new RefactoringStatus();
		IPackageFragmentRoot[] roots= fPackage.getJavaProject().getPackageFragmentRoots();
		Set topLevelTypeNames= getTopLevelTypeNames();
		for (int i= 0; i < roots.length; i++) {
			if (! isPackageNameOkInRoot(newName, roots[i])){
				String message= Messages.format(RefactoringCoreMessages.RenamePackageRefactoring_aleady_exists, new Object[]{getNewElementName(), roots[i].getElementName()});
				status.merge(RefactoringStatus.createWarningStatus(message));
				status.merge(checkTypeNameConflicts(roots[i], newName, topLevelTypeNames)); 
			}
		}
		return status;
	}
	
	private Set getTopLevelTypeNames() throws CoreException {
		ICompilationUnit[] cus= fPackage.getCompilationUnits();
		Set result= new HashSet(2 * cus.length); 
		for (int i= 0; i < cus.length; i++) {
			result.addAll(getTopLevelTypeNames(cus[i]));
		}
		return result;
	}
	
	private static Collection getTopLevelTypeNames(ICompilationUnit iCompilationUnit) throws CoreException {
		IType[] types= iCompilationUnit.getTypes();
		List result= new ArrayList(types.length);
		for (int i= 0; i < types.length; i++) {
			result.add(types[i].getElementName());
		}
		return result;
	}
	
	private RefactoringStatus checkTypeNameConflicts(IPackageFragmentRoot root, String newName, Set topLevelTypeNames) throws CoreException {
		IPackageFragment otherPack= root.getPackageFragment(newName);
		if (fPackage.equals(otherPack))
			return null;
		ICompilationUnit[] cus= otherPack.getCompilationUnits();
		RefactoringStatus result= new RefactoringStatus();
		for (int i= 0; i < cus.length; i++) {
			result.merge(checkTypeNameConflicts(cus[i], topLevelTypeNames));
		}
		return result;
	}
	
	private RefactoringStatus checkTypeNameConflicts(ICompilationUnit iCompilationUnit, Set topLevelTypeNames) throws CoreException {
		RefactoringStatus result= new RefactoringStatus();
		IType[] types= iCompilationUnit.getTypes();
		String packageName= iCompilationUnit.getParent().getElementName();
		for (int i= 0; i < types.length; i++) {
			String name= types[i].getElementName();
			if (topLevelTypeNames.contains(name)){
				String[] keys= {packageName, name};
				String msg= Messages.format(RefactoringCoreMessages.RenamePackageRefactoring_contains_type, keys); 
				RefactoringStatusContext context= JavaStatusContext.create(types[i]);
				result.addError(msg, context);
			}	
		}
		return result;
	}
		
	private RefactoringStatus analyzeAffectedCompilationUnits() throws CoreException {
		//TODO: also for both fReferencesTo...; only check each CU once!
		RefactoringStatus result= new RefactoringStatus();
		fOccurrences= Checks.excludeCompilationUnits(fOccurrences, result);
		if (result.hasFatalError())
			return result;
		
		result.merge(Checks.checkCompileErrorsInAffectedFiles(fOccurrences));	
		return result;
	}

	private IFile[] getAllCusInPackageAsFiles() throws CoreException {
		ICompilationUnit[] cus= fPackage.getCompilationUnits();
		List files= new ArrayList(cus.length);
		for (int i= 0; i < cus.length; i++) {
            IResource res= ResourceUtil.getResource(cus[i]);
            if (res != null && res.getType() == IResource.FILE)
            	files.add(res);
        }
		return (IFile[]) files.toArray(new IFile[files.size()]);
	}
	
	private IFile[] getAllFilesToModify() throws CoreException {
		List combined= new ArrayList();
		combined.addAll(Arrays.asList(ResourceUtil.getFiles(fChangeManager.getAllCompilationUnits())));
		combined.addAll(Arrays.asList(getAllCusInPackageAsFiles()));
		if (fQualifiedNameSearchResult != null)
			combined.addAll(Arrays.asList(fQualifiedNameSearchResult.getAllFiles()));
		return (IFile[]) combined.toArray(new IFile[combined.size()]);
	}
	
	// ----------- Changes ---------------
	
	public Change createChange(IProgressMonitor pm) throws CoreException {
		try{
			pm.beginTask(RefactoringCoreMessages.RenamePackageRefactoring_creating_change, 1); 
			final DynamicValidationStateChange result= new DynamicValidationStateChange(
				RefactoringCoreMessages.Change_javaChanges); 
	
			result.addAll(fChangeManager.getAllChanges());
							
			result.add(new RenamePackageChange(fPackage, getNewElementName()));
			pm.worked(1);
			return result;
		} finally{
			pm.done();
		}	
	}
	
	public Change postCreateChange(Change[] participantChanges, IProgressMonitor pm) throws CoreException {
		if (fQualifiedNameSearchResult != null) {	
			return fQualifiedNameSearchResult.getSingleChange(Changes.getModifiedFiles(participantChanges));
		} else {
			return null;
		}
	}
	
	private void addTextMatches(TextChangeManager manager, IProgressMonitor pm) throws CoreException {
		//fOccurrences is enough; the others are only import statements
		TextMatchUpdater.perform(pm, RefactoringScopeFactory.create(fPackage), this, manager, fOccurrences);
	}
	
	private TextEdit createTextChange(SearchMatch searchResult) {
		return new ReplaceEdit(searchResult.getOffset(), searchResult.getLength(), getNewElementName());
	}
	
	private TextChangeManager createChangeManager(IProgressMonitor pm) throws CoreException {
		pm.beginTask("", 2); //$NON-NLS-1$
		TextChangeManager manager= new TextChangeManager();
		
		if (fUpdateReferences)
			addReferenceUpdates(manager, new SubProgressMonitor(pm, 1));
		
		if (fUpdateTextualMatches) {
			pm.subTask(RefactoringCoreMessages.RenamePackageRefactoring_searching_text); 
			addTextMatches(manager, new SubProgressMonitor(pm, 1));
		}
		
		pm.done();
		return manager;
	}
	
	private void addReferenceUpdates(TextChangeManager manager, IProgressMonitor pm) throws CoreException {
		pm.beginTask("", fOccurrences.length + fReferencesToTypesInPackage.size() + fReferencesToTypesInNamesakes.size()); //$NON-NLS-1$
		for (int i= 0; i < fOccurrences.length; i++){
			ICompilationUnit cu= fOccurrences[i].getCompilationUnit();
			if (cu == null)
				continue;
			String name= RefactoringCoreMessages.RenamePackageRefactoring_update_reference; 
			ImportRewrite importRewrite= null;
			SearchMatch[] results= fOccurrences[i].getSearchResults();
			for (int j= 0; j < results.length; j++){
				SearchMatch result= results[j];
				if (isImport(result)) {
					if (importRewrite == null)
						importRewrite= newImportRewrite(cu);
					IImportDeclaration importDeclaration= (IImportDeclaration) SearchUtils.getEnclosingJavaElement(result);
					String updatedImport= getUpdatedImport(importDeclaration);
					updateImport(importRewrite, importDeclaration, updatedImport);
				} else { // is reference 
					TextChangeCompatibility.addTextEdit(manager.get(cu), name, createTextChange(result));
				}
			}
			if (fReferencesToTypesInNamesakes.size() != 0) {
				SearchResultGroup typeRefsRequiringOldNameImport= extractGroupFor(cu, fReferencesToTypesInNamesakes);
				if (typeRefsRequiringOldNameImport != null)
					importRewrite= addTypeImports(importRewrite, typeRefsRequiringOldNameImport);
			}
			if (fReferencesToTypesInPackage.size() != 0) {
				SearchResultGroup typeRefsRequiringNewNameImport= extractGroupFor(cu, fReferencesToTypesInPackage);
				if (typeRefsRequiringNewNameImport != null)
					importRewrite= updateTypeImports(importRewrite, typeRefsRequiringNewNameImport);
			}
			if (importRewrite != null)
				addImportEdits(manager, importRewrite);
			
			pm.worked(1);
		}	

		if (fReferencesToTypesInNamesakes.size() != 0) {
			for (Iterator iter= fReferencesToTypesInNamesakes.iterator(); iter.hasNext();) {
				SearchResultGroup referencesToTypesInNamesakes= (SearchResultGroup) iter.next();
				ImportRewrite importRewrite= addTypeImports(null, referencesToTypesInNamesakes);
				if (importRewrite != null)
					addImportEdits(manager, importRewrite);
				pm.worked(1);
			}
		}
		if (fReferencesToTypesInPackage.size() != 0) {
			for (Iterator iter= fReferencesToTypesInPackage.iterator(); iter.hasNext();) {
				SearchResultGroup namesakeReferencesToPackage= (SearchResultGroup) iter.next();
				ImportRewrite importRewrite= updateTypeImports(null, namesakeReferencesToPackage);
				if (importRewrite != null)
					addImportEdits(manager, importRewrite);
				pm.worked(1);
			}
		} 
		pm.done();
	}
	
	private static void updateImport(ImportRewrite importRewrite, IImportDeclaration importDeclaration, String updatedImport) throws JavaModelException {
		if (Flags.isStatic(importDeclaration.getFlags())) {
			importRewrite.removeStaticImport(importDeclaration.getElementName());
			importRewrite.addStaticImport(Signature.getQualifier(updatedImport), Signature.getSimpleName(updatedImport), true);
		} else {
			importRewrite.removeImport(importDeclaration.getElementName());
			importRewrite.addImport(updatedImport);
		}
	}

	private ImportRewrite newImportRewrite(ICompilationUnit cu) throws CoreException {
		ImportRewrite rewrite= new ImportRewrite(cu);
		rewrite.setFilterImplicitImports(false);
		return rewrite;
	}

	private void addImportEdits(TextChangeManager manager, ImportRewrite importRewrite) throws CoreException {
		if (!importRewrite.isEmpty()) {
			ICompilationUnit cu= importRewrite.getCompilationUnit();
			try {
				ITextFileBuffer buffer= RefactoringFileBuffers.acquire(cu);
				TextEdit importEdit= importRewrite.createEdit(buffer.getDocument(), null);
				String name= RefactoringCoreMessages.RenamePackageRefactoring_update_imports; 
				TextChangeCompatibility.addTextEdit(manager.get(cu), name, importEdit);
			} finally {
				RefactoringFileBuffers.release(cu);
			}
		}
	}

	/**
	 * Add new imports to types in <code>typeReferences</code> with package <code>fPackage</code>.
	 * @return the ImportRewrite or null
	 */
	private ImportRewrite addTypeImports(ImportRewrite importRewrite, SearchResultGroup typeReferences) throws CoreException {
		SearchMatch[] searchResults= typeReferences.getSearchResults();
		for (int i= 0; i < searchResults.length; i++) {
			SearchMatch result= searchResults[i];
			IJavaElement enclosingElement= SearchUtils.getEnclosingJavaElement(result);
			if (! (enclosingElement instanceof IImportDeclaration)) {
				String reference= getNormalizedTypeReference(result);
				if (! reference.startsWith(fPackage.getElementName())) {
					// is unqualified
					reference= cutOffInnerTypes(reference);
					if (importRewrite == null)
						importRewrite= newImportRewrite(typeReferences.getCompilationUnit());
					importRewrite.addImport(fPackage.getElementName() + '.' + reference);
				}
			}
		}
		return importRewrite;
	}

	/**
	 * Add new imports to types in <code>typeReferences</code> with package <code>fNewElementName</code>
	 * and remove old import with <code>fPackage</code>.
	 * @return the ImportRewrite or null
	 */
	private ImportRewrite updateTypeImports(ImportRewrite importRewrite, SearchResultGroup typeReferences) throws CoreException {
		SearchMatch[] searchResults= typeReferences.getSearchResults();
		for (int i= 0; i < searchResults.length; i++) {
			SearchMatch result= searchResults[i];
			IJavaElement enclosingElement= SearchUtils.getEnclosingJavaElement(result);
			if (enclosingElement instanceof IImportDeclaration) {
				IImportDeclaration importDeclaration= (IImportDeclaration) enclosingElement;
				if (importRewrite == null)
					importRewrite= newImportRewrite(typeReferences.getCompilationUnit());
				updateImport(importRewrite, importDeclaration, getUpdatedImport(importDeclaration));
			} else {
				String reference= getNormalizedTypeReference(result);
				if (! reference.startsWith(fPackage.getElementName())) {
					reference= cutOffInnerTypes(reference);
					if (importRewrite == null)
						importRewrite= newImportRewrite(typeReferences.getCompilationUnit());
					importRewrite.removeImport(fPackage.getElementName() + '.' + reference);
					importRewrite.addImport(getNewElementName() + '.' + reference);
				}
			}
		}
		return importRewrite;
	}
	
	private static String getNormalizedTypeReference(SearchMatch searchResult) throws JavaModelException {
		ICompilationUnit cu= SearchUtils.getCompilationUnit(searchResult);
		String reference= cu.getBuffer().getText(searchResult.getOffset(), searchResult.getLength());
		//reference may be package-qualified -> normalize (remove comments, etc.):
		return CommentAnalyzer.normalizeReference(reference);
	}
	
	private static String cutOffInnerTypes(String reference) {
		int dotPos= reference.indexOf('.'); // cut off inner types
		if (dotPos != -1)
			reference= reference.substring(0, dotPos);
		return reference;
	}
	
	private String getUpdatedImport(IImportDeclaration importDeclaration) {
		String fullyQualifiedImportType= importDeclaration.getElementName();
		int offsetOfDotBeforeTypeName= fPackage.getElementName().length();
		String result= getNewElementName() + fullyQualifiedImportType.substring(offsetOfDotBeforeTypeName);
		return result;
	}

	private static boolean isImport(SearchMatch searchResult) {
		return SearchUtils.getEnclosingJavaElement(searchResult) instanceof IImportDeclaration;
	}
	
	/** Removes the found SearchResultGroup from the list iff found.
	 *  @param searchResultGroups List of SearchResultGroup
	 *  @return the SearchResultGroup for cu, or null iff not found */
	private static SearchResultGroup extractGroupFor(ICompilationUnit cu, List searchResultGroups) {
		for (Iterator iter= searchResultGroups.iterator(); iter.hasNext();) {
			SearchResultGroup group= (SearchResultGroup) iter.next();
			if (cu.equals(group.getCompilationUnit())) {
				iter.remove();
				return group;
			}
		}
		return null;
	}

	private void computeQualifiedNameMatches(IProgressMonitor pm) throws CoreException {
		if (fQualifiedNameSearchResult == null)
			fQualifiedNameSearchResult= new QualifiedNameSearchResult();
		QualifiedNameFinder.process(fQualifiedNameSearchResult, fPackage.getElementName(), getNewElementName(), 
			fFilePatterns, fPackage.getJavaProject().getProject(), pm);
	}	
}
