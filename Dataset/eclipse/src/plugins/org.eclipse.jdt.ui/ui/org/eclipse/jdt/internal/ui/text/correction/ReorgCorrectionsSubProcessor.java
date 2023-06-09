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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRunnable;

import org.eclipse.swt.graphics.Image;

import org.eclipse.jface.dialogs.MessageDialog;

import org.eclipse.jface.text.IDocument;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.PreferencesUtil;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.progress.IProgressService;

import org.eclipse.ui.ide.IDE;

import org.eclipse.ltk.core.refactoring.CompositeChange;

import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaConventions;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ImportDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameRequestor;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.refactoring.changes.AddToClasspathChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.CreatePackageChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.MoveCompilationUnitChange;
import org.eclipse.jdt.internal.corext.refactoring.changes.RenameCompilationUnitChange;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.TypeInfo;
import org.eclipse.jdt.internal.corext.util.TypeInfoRequestor;

import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;

import org.eclipse.jdt.ui.JavaElementLabels;
import org.eclipse.jdt.ui.actions.OrganizeImportsAction;
import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaPluginImages;
import org.eclipse.jdt.internal.ui.actions.WorkbenchRunnableAdapter;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.preferences.BuildPathsPropertyPage;
import org.eclipse.jdt.internal.ui.util.CoreUtility;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.BuildPathSupport;
import org.eclipse.jdt.internal.ui.wizards.buildpaths.CPListElement;

public class ReorgCorrectionsSubProcessor {

	public static void getWrongTypeNameProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) throws CoreException {
		String[] args= problem.getProblemArguments();
		if (args.length == 2) {
			ICompilationUnit cu= context.getCompilationUnit();
			boolean isLinked= cu.getResource().isLinked();

			// rename type
			proposals.add(new CorrectMainTypeNameProposal(cu, context.getASTRoot(), args[1], 5));

			String newCUName= args[1] + ".java"; //$NON-NLS-1$
			ICompilationUnit newCU= ((IPackageFragment) (cu.getParent())).getCompilationUnit(newCUName);
			if (!newCU.exists() && !isLinked && !JavaConventions.validateCompilationUnitName(newCUName).matches(IStatus.ERROR)) {
				RenameCompilationUnitChange change= new RenameCompilationUnitChange(cu, newCUName);

				// rename cu
				String label= Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_renamecu_description, newCUName);
				proposals.add(new ChangeCorrectionProposal(label, change, 6, JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_RENAME)));
			}
		}
	}

	public static void getWrongPackageDeclNameProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) throws CoreException {
		String[] args= problem.getProblemArguments();
		if (args.length == 1) {
			ICompilationUnit cu= context.getCompilationUnit();
			boolean isLinked= cu.getResource().isLinked();

			// correct pack decl
			int relevance= cu.getPackageDeclarations().length == 0 ? 7 : 5; // bug 38357
			proposals.add(new CorrectPackageDeclarationProposal(cu, problem, relevance));

			// move to pack
			IPackageDeclaration[] packDecls= cu.getPackageDeclarations();
			String newPackName= packDecls.length > 0 ? packDecls[0].getElementName() : ""; //$NON-NLS-1$

			IPackageFragmentRoot root= JavaModelUtil.getPackageFragmentRoot(cu);
			IPackageFragment newPack= root.getPackageFragment(newPackName);

			ICompilationUnit newCU= newPack.getCompilationUnit(cu.getElementName());
			if (!newCU.exists() && !isLinked) {
				String label;
				if (newPack.isDefaultPackage()) {
					label= Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_movecu_default_description, cu.getElementName());
				} else {
					label= Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_movecu_description, new Object[] { cu.getElementName(), newPack.getElementName() });
				}
				CompositeChange composite= new CompositeChange(label);
				composite.add(new CreatePackageChange(newPack));
				composite.add(new MoveCompilationUnitChange(cu, newPack));

				proposals.add(new ChangeCorrectionProposal(label, composite, 6, JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_MOVE)));
			}
		}
	}

	public static void removeImportStatementProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) {
		final ICompilationUnit cu= context.getCompilationUnit();

		ASTNode selectedNode= problem.getCoveringNode(context.getASTRoot());
		if (selectedNode != null) {
			ASTNode node= ASTNodes.getParent(selectedNode, ASTNode.IMPORT_DECLARATION);
			if (node instanceof ImportDeclaration) {
				ASTRewrite rewrite= ASTRewrite.create(node.getAST());

				rewrite.remove(node, null);

				String label= CorrectionMessages.ReorgCorrectionsSubProcessor_unusedimport_description;
				Image image= JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_DELETE_IMPORT);

				ASTRewriteCorrectionProposal proposal= new ASTRewriteCorrectionProposal(label, cu, rewrite, 6, image);
				proposals.add(proposal);
			}
		}

		String name= CorrectionMessages.ReorgCorrectionsSubProcessor_organizeimports_description;
		ChangeCorrectionProposal proposal= new ChangeCorrectionProposal(name, null, 5, JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE)) {
			public void apply(IDocument document) {
				IEditorInput input= new FileEditorInput((IFile) cu.getResource());
				IWorkbenchPage p= JavaPlugin.getActivePage();
				if (p == null) {
					return;
				}
				IEditorPart part= p.findEditor(input);
				if (part instanceof JavaEditor) {
					OrganizeImportsAction action= new OrganizeImportsAction((JavaEditor) part);
					action.run(cu);
				}
			}
		};
		proposals.add(proposal);
	}

	public static void importNotFoundProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) throws CoreException {
		ICompilationUnit cu= context.getCompilationUnit();
		IJavaProject project= cu.getJavaProject();

		ASTNode selectedNode= problem.getCoveringNode(context.getASTRoot());
		if (selectedNode != null) {
			ImportDeclaration importDeclaration= (ImportDeclaration) ASTNodes.getParent(selectedNode, ASTNode.IMPORT_DECLARATION);
			if (importDeclaration == null) {
				return;
			}
			String name= ASTNodes.asString(importDeclaration.getName());
			char[] packageName;
			char[] typeName= null;
			if (importDeclaration.isOnDemand()) {
				packageName= name.toCharArray();
			} else {
				packageName= Signature.getQualifier(name).toCharArray();
				typeName= Signature.getSimpleName(name).toCharArray();
			}
			IJavaSearchScope scope= SearchEngine.createWorkspaceScope();
			ArrayList res= new ArrayList();
			TypeNameRequestor requestor= new TypeInfoRequestor(res);
			new SearchEngine().searchAllTypeNames(packageName, typeName,
					SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE, IJavaSearchConstants.TYPE, scope, requestor,
					IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH, null);

			if (res.isEmpty()) {
				return;
			}
			HashSet addedClaspaths= new HashSet();
			for (int i= 0; i < res.size(); i++) {
				TypeInfo curr= (TypeInfo) res.get(i);
				IType type= curr.resolveType(scope);
				if (type != null) {
					IPackageFragmentRoot root= (IPackageFragmentRoot) type.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
					IClasspathEntry entry= root.getRawClasspathEntry();
					if (entry == null) {
						continue;
					}
					IJavaProject other= root.getJavaProject();
					int entryKind= entry.getEntryKind();
					if ((entry.isExported() || entryKind == IClasspathEntry.CPE_SOURCE) && addedClaspaths.add(other)) {
						String[] args= { other.getElementName(), project.getElementName() };
						String label= Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_addcp_project_description, args);
						IClasspathEntry newEntry= JavaCore.newProjectEntry(other.getPath());
						AddToClasspathChange change= new AddToClasspathChange(project, newEntry);
						if (!change.entryAlreadyExists()) {
							ChangeCorrectionProposal proposal= new ChangeCorrectionProposal(label, change, 8, JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE));
							proposals.add(proposal);
						}
					}
					if ((entryKind == IClasspathEntry.CPE_LIBRARY || entryKind == IClasspathEntry.CPE_VARIABLE || entryKind == IClasspathEntry.CPE_CONTAINER) && addedClaspaths.add(entry)) {
						String label= getAddClasspathLabel(entry, root, project);
						if (label != null) {
							AddToClasspathChange change= new AddToClasspathChange(project, entry);
							if (!change.entryAlreadyExists()) {
								ChangeCorrectionProposal proposal= new ChangeCorrectionProposal(label, change, 7, JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE));
								proposals.add(proposal);
							}
						}
					}
				}
			}
		}
	}

	private static String getAddClasspathLabel(IClasspathEntry entry, IPackageFragmentRoot root, IJavaProject project) {
		switch (entry.getEntryKind()) {
			case IClasspathEntry.CPE_LIBRARY:
				if (root.isArchive()) {
					String[] args= { JavaElementLabels.getElementLabel(root, 0), project.getElementName() };
					return Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_addcp_archive_description, args);
				} else {
					String[] args= { JavaElementLabels.getElementLabel(root, 0), project.getElementName() };
					return Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_addcp_classfolder_description, args);
				}
			case IClasspathEntry.CPE_VARIABLE: {
					String[] args= { JavaElementLabels.getElementLabel(root, 0), project.getElementName() };
					return Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_addcp_variable_description, args);
				}
			case IClasspathEntry.CPE_CONTAINER:
				try {
					String[] args= { JavaElementLabels.getContainerEntryLabel(entry.getPath(), root.getJavaProject()), project.getElementName() };
					return Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_addcp_library_description, args);
				} catch (JavaModelException e) {
					// ignore
				}
				break;
			}
		return null;
	}

	private static final class OpenBuildPathCorrectionProposal extends ChangeCorrectionProposal {
		private final IProject fProject;
		private final IBinding fReferencedType;
		private OpenBuildPathCorrectionProposal(IProject project, String label, int relevance, IBinding referencedType) {
			super(label, null, relevance, null);
			fProject= project;
			fReferencedType= referencedType;
			ISharedImages images= JavaPlugin.getDefault().getWorkbench().getSharedImages();
			setImage(images.getImage(IDE.SharedImages.IMG_OBJ_PROJECT));
		}
		public void apply(IDocument document) {
			Map data= null;
			if (fReferencedType != null) {
				IJavaElement elem= fReferencedType.getJavaElement();
				if (elem != null) {
					IPackageFragmentRoot root= (IPackageFragmentRoot) elem.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
					if (root != null) {
						try {
							IClasspathEntry entry= root.getRawClasspathEntry();
							if (entry != null) {
								data= new HashMap(1);
								data.put(BuildPathsPropertyPage.DATA_REVEAL_ENTRY, entry);
								if (entry.getEntryKind() != IClasspathEntry.CPE_CONTAINER) {
									data.put(BuildPathsPropertyPage.DATA_REVEAL_ATTRIBUTE_KEY, CPListElement.ACCESSRULES);
								}
							}
						} catch (JavaModelException e) {
							// ignore
						}
					}
				}
			}
			PreferencesUtil.createPropertyDialogOn(JavaPlugin.getActiveWorkbenchShell(), fProject, BuildPathsPropertyPage.PROP_ID, null, data).open();
		}
		public String getAdditionalProposalInfo() {
			return Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_configure_buildpath_description, fProject.getName());
		}
	}

	private static final class ChangeTo50Compliance extends ChangeCorrectionProposal implements IWorkspaceRunnable {
		
		private final IJavaProject fProject;
		private final boolean fChangeOnWorkspace;
		
		private Job fUpdateJob;
		private boolean f50JREFound;
		
		public ChangeTo50Compliance(String name, IJavaProject project, boolean changeOnWorkspace, int relevance) {
			super(name, null, relevance, JavaPluginImages.get(JavaPluginImages.IMG_CORRECTION_CHANGE));
			fProject= project;
			fChangeOnWorkspace= changeOnWorkspace;
			fUpdateJob= null;
			f50JREFound= false;
		}
		
		private boolean is50VMInstall(IVMInstall install) {
			return BuildPathSupport.hasMatchingCompliance(install, JavaCore.VERSION_1_5);
		}
		
		private IVMInstall find50VMInstall() {
			return BuildPathSupport.findMatchingJREInstall(JavaCore.VERSION_1_5);
		}
		
		public void run(IProgressMonitor monitor) throws CoreException {
			boolean needsBuild= updateJRE(monitor);
			if (needsBuild) {
				fUpdateJob= CoreUtility.getBuildJob(fChangeOnWorkspace ? null : fProject.getProject());
			}
		}
		
		private boolean updateJRE( IProgressMonitor monitor) throws CoreException, JavaModelException {
			try {
				IVMInstall vm50Install= find50VMInstall();
				f50JREFound= vm50Install != null;
				if (vm50Install != null) {
					IVMInstall install= JavaRuntime.getVMInstall(fProject); // can be null
					if (fChangeOnWorkspace) {
						monitor.beginTask(CorrectionMessages.ReorgCorrectionsSubProcessor_50_compliance_operation, 4);
						IVMInstall defaultVM= JavaRuntime.getDefaultVMInstall(); // can be null
						if (defaultVM != null && !defaultVM.equals(install)) {
							IPath newPath= new Path(JavaRuntime.JRE_CONTAINER);
							updateClasspath(newPath, new SubProgressMonitor(monitor, 1));
						} else {
							monitor.worked(1);
						}
						if (defaultVM == null || !is50VMInstall(defaultVM)) {
							JavaRuntime.setDefaultVMInstall(vm50Install, new SubProgressMonitor(monitor, 3), true);
							return false;
						}
						return true;
					} else {
						if (install == null || !is50VMInstall(install)) {
							IPath newPath= new Path(JavaRuntime.JRE_CONTAINER).append(vm50Install.getVMInstallType().getId()).append(vm50Install.getName());
							updateClasspath(newPath, monitor);
							return false;
						}
					}
				}
			} finally {
				monitor.done();
			}
			return true;
		}

		private void updateClasspath(IPath newPath, IProgressMonitor monitor) throws JavaModelException {
			IClasspathEntry[] classpath= fProject.getRawClasspath();
			IPath jreContainerPath= new Path(JavaRuntime.JRE_CONTAINER);
			for (int i= 0; i < classpath.length; i++) {
				IClasspathEntry curr= classpath[i];
				if (curr.getEntryKind() == IClasspathEntry.CPE_CONTAINER && curr.getPath().matchingFirstSegments(jreContainerPath) > 0) {
					classpath[i]= JavaCore.newContainerEntry(newPath, curr.getAccessRules(), curr.getExtraAttributes(), curr.isExported());
				}
			}
			fProject.setRawClasspath(classpath, monitor);
		}
		
		/* (non-Javadoc)
		 * @see org.eclipse.jface.text.contentassist.ICompletionProposal#getAdditionalProposalInfo()
		 */
		public String getAdditionalProposalInfo() {
			StringBuffer message= new StringBuffer();
			if (fChangeOnWorkspace) {
				message.append(CorrectionMessages.ReorgCorrectionsSubProcessor_50_compliance_changeworkspace_description);
			} else {
				message.append(CorrectionMessages.ReorgCorrectionsSubProcessor_50_compliance_changeproject_description);
			}
			
			IVMInstall vm50Install= find50VMInstall();
			if (vm50Install != null) {
				try {
					IVMInstall install= JavaRuntime.getVMInstall(fProject); // can be null
					if (fChangeOnWorkspace) {
						IVMInstall defaultVM= JavaRuntime.getDefaultVMInstall(); // can be null
						if (defaultVM != null && !defaultVM.equals(install)) {
							message.append(CorrectionMessages.ReorgCorrectionsSubProcessor_50_compliance_changeProjectJREToDefault_description);
						}
						if (defaultVM == null || !is50VMInstall(defaultVM)) {
							message.append(Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_50_compliance_changeWorkspaceJRE_description, vm50Install.getName()));
						}
					} else {
						if (install == null || !is50VMInstall(install)) {
							message.append(Messages.format(CorrectionMessages.ReorgCorrectionsSubProcessor_50_compliance_changeProjectJRE_description, vm50Install.getName()));
						}
					}
				} catch (CoreException e) {
					// ignore
				}
			}
			return message.toString();
		}
		
		/* (non-Javadoc)
		 * @see org.eclipse.jface.text.contentassist.ICompletionProposal#apply(IDocument)
		 */
		public void apply(IDocument document) {
			if (fChangeOnWorkspace) {
				Hashtable map= JavaCore.getOptions();
				JavaModelUtil.set50CompilanceOptions(map);
				JavaCore.setOptions(map);
			} else {
				Map map= fProject.getOptions(false);
				JavaModelUtil.set50CompilanceOptions(map);
				fProject.setOptions(map);
			}
			try {
				IProgressService progressService= PlatformUI.getWorkbench().getProgressService();
				progressService.run(true, true, new WorkbenchRunnableAdapter(this));
			} catch (InvocationTargetException e) {
				JavaPlugin.log(e);
			} catch (InterruptedException e) {
				return;
			}
			
			if (fUpdateJob != null) {
				fUpdateJob.schedule();
			}
			
			if (!f50JREFound) {
				MessageDialog.openInformation(JavaPlugin.getActiveWorkbenchShell(), CorrectionMessages.ReorgCorrectionsSubProcessor_no_50jre_title, CorrectionMessages.ReorgCorrectionsSubProcessor_no_50jre_message);
			}
		}
	}

	public static void getNeed50ComplianceProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) {
		IJavaProject project= context.getCompilationUnit().getJavaProject();
		
		String label1= CorrectionMessages.ReorgCorrectionsSubProcessor_50_project_compliance_description;
		proposals.add(new ChangeTo50Compliance(label1, project, false, 5));

		if (project.getOption(JavaCore.COMPILER_COMPLIANCE, false) == null) {
			String label2= CorrectionMessages.ReorgCorrectionsSubProcessor_50_workspace_compliance_description;
			proposals.add(new ChangeTo50Compliance(label2, project, true, 6));
		}
	}

	public static void getIncorrectBuildPathProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) {
		IProject project= context.getCompilationUnit().getJavaProject().getProject();
		String label= CorrectionMessages.ReorgCorrectionsSubProcessor_configure_buildpath_label;
		OpenBuildPathCorrectionProposal proposal= new OpenBuildPathCorrectionProposal(project, label, 5, null);
		proposals.add(proposal);
	}

	public static void getAccessRulesProposals(IInvocationContext context, IProblemLocation problem, Collection proposals) {
		IBinding referencedElement= null;
		ASTNode node= problem.getCoveredNode(context.getASTRoot());
		if (node instanceof Type) {
			referencedElement= ((Type) node).resolveBinding();
		} else if (node instanceof Name) {
			referencedElement= ((Name) node).resolveBinding();
		}
		IProject project= context.getCompilationUnit().getJavaProject().getProject();
		String label= CorrectionMessages.ReorgCorrectionsSubProcessor_accessrules_description;
		OpenBuildPathCorrectionProposal proposal= new OpenBuildPathCorrectionProposal(project, label, 5, referencedElement);
		proposals.add(proposal);
	}
}