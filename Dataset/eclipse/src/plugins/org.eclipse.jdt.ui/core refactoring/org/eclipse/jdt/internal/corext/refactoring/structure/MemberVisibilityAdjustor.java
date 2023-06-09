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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.eclipse.text.edits.TextEditGroup;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.Type;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.codemanipulation.GetterSetterUtil;
import org.eclipse.jdt.internal.corext.dom.ModifierRewrite;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringCoreMessages;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringScopeFactory;
import org.eclipse.jdt.internal.corext.refactoring.RefactoringSearchEngine2;
import org.eclipse.jdt.internal.corext.refactoring.SearchResultGroup;
import org.eclipse.jdt.internal.corext.refactoring.base.JavaStatusContext;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.SearchUtils;

import org.eclipse.jdt.ui.JavaElementLabels;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Helper class to adjust the visibilities of members with respect to a reference element.
 * 
 * @since 3.1
 */
public final class MemberVisibilityAdjustor {

	/** Description of a member visibility adjustment */
	public static class IncomingMemberVisibilityAdjustment implements IVisibilityAdjustment {

		/** The keyword representing the adjusted visibility */
		protected final ModifierKeyword fKeyword;

		/** The member whose visibility has been adjusted */
		protected final IMember fMember;

		/** Does the visibility adjustment need rewriting? */
		protected boolean fNeedsRewriting= true;

		/** The associated refactoring status */
		protected final RefactoringStatus fRefactoringStatus;

		/**
		 * Creates a new incoming member visibility adjustment.
		 * 
		 * @param member the member which is adjusted
		 * @param keyword the keyword representing the adjusted visibility
		 * @param status the associated status, or <code>null</code>
		 */
		public IncomingMemberVisibilityAdjustment(final IMember member, final ModifierKeyword keyword, final RefactoringStatus status) {
			Assert.isNotNull(member);
			Assert.isTrue(!(member instanceof IInitializer));
			Assert.isTrue(isVisibilityKeyword(keyword));
			fMember= member;
			fKeyword= keyword;
			fRefactoringStatus= status;
		}

		/**
		 * Returns the visibility keyword.
		 * 
		 * @return the visibility keyword
		 */
		public final ModifierKeyword getKeyword() {
			return fKeyword;
		}

		/**
		 * Returns the adjusted member.
		 * 
		 * @return the adjusted member
		 */
		public final IMember getMember() {
			return fMember;
		}

		/**
		 * Returns the associated refactoring status.
		 * 
		 * @return the associated refactoring status
		 */
		public final RefactoringStatus getStatus() {
			return fRefactoringStatus;
		}

		/**
		 * Does the visibility adjustment need rewriting?
		 * 
		 * @return <code>true</code> if it needs rewriting, <code>false</code> otherwise
		 */
		public final boolean needsRewriting() {
			return fNeedsRewriting;
		}

		/**
		 * Rewrites the visibility adjustment.
		 * 
		 * @param adjustor the java element visibility adjustor
		 * @param rewrite the ast rewrite to use
		 * @param root the root of the ast used in the rewrite
		 * @param group the text edit group description to use, or <code>null</code>
		 * @param status the refactoring status, or <code>null</code>
		 * @throws JavaModelException if an error occurs
		 */
		protected final void rewriteVisibility(final MemberVisibilityAdjustor adjustor, final ASTRewrite rewrite, final CompilationUnit root, final TextEditGroup group, final RefactoringStatus status) throws JavaModelException {
			Assert.isNotNull(adjustor);
			Assert.isNotNull(rewrite);
			Assert.isNotNull(root);
			final int visibility= fKeyword != null ? fKeyword.toFlagValue() : Modifier.NONE;
			if (fMember instanceof IField && !Flags.isEnum(fMember.getFlags())) {
				final VariableDeclarationFragment fragment= ASTNodeSearchUtil.getFieldDeclarationFragmentNode((IField) fMember, root);
				final FieldDeclaration declaration= (FieldDeclaration) fragment.getParent();
				if (declaration.fragments().size() == 1)
					ModifierRewrite.create(rewrite, declaration).setVisibility(visibility, group);
				else {
					final VariableDeclarationFragment newFragment= rewrite.getAST().newVariableDeclarationFragment();
					newFragment.setName((SimpleName) rewrite.createCopyTarget(fragment.getName()));
					final FieldDeclaration newDeclaration= rewrite.getAST().newFieldDeclaration(newFragment);
					newDeclaration.setType((Type) rewrite.createCopyTarget(declaration.getType()));
					IExtendedModifier extended= null;
					for (final Iterator iterator= declaration.modifiers().iterator(); iterator.hasNext();) {
						extended= (IExtendedModifier) iterator.next();
						if (extended.isModifier()) {
							final Modifier modifier= (Modifier) extended;
							final int flag= modifier.getKeyword().toFlagValue();
							if ((flag & (Modifier.PUBLIC | Modifier.PROTECTED | Modifier.PRIVATE)) != 0)
								continue;
						}
						newDeclaration.modifiers().add(rewrite.createCopyTarget((ASTNode) extended));
					}
					ModifierRewrite.create(rewrite, newDeclaration).setVisibility(visibility, group);
					final AbstractTypeDeclaration type= (AbstractTypeDeclaration) declaration.getParent();
					rewrite.getListRewrite(type, type.getBodyDeclarationsProperty()).insertAfter(newDeclaration, declaration, null);
					final ListRewrite list= rewrite.getListRewrite(declaration, FieldDeclaration.FRAGMENTS_PROPERTY);
					list.remove(fragment, group);
					if (list.getRewrittenList().isEmpty())
						rewrite.remove(declaration, null);
				}
				if (status != null)
					adjustor.fStatus.merge(status);
			} else if (fMember != null) {
				final BodyDeclaration declaration= ASTNodeSearchUtil.getBodyDeclarationNode(fMember, root);
				if (declaration != null) {
					ModifierRewrite.create(rewrite, declaration).setVisibility(visibility, group);
					if (status != null)
						adjustor.fStatus.merge(status);
				}
			}
		}

		/*
		 * @see org.eclipse.jdt.internal.corext.refactoring.structure.MemberVisibilityAdjustor.IVisibilityAdjustment#rewriteVisibility(org.eclipse.jdt.internal.corext.refactoring.structure.MemberVisibilityAdjustor, org.eclipse.core.runtime.IProgressMonitor)
		 */
		public void rewriteVisibility(final MemberVisibilityAdjustor adjustor, final IProgressMonitor monitor) throws JavaModelException {
			Assert.isNotNull(adjustor);
			Assert.isNotNull(monitor);
			try {
				monitor.beginTask("", 1); //$NON-NLS-1$
				monitor.setTaskName(RefactoringCoreMessages.MemberVisibilityAdjustor_adjusting);
				if (fNeedsRewriting) {
					if (adjustor.fRewrite != null && adjustor.fRoot != null)
						rewriteVisibility(adjustor, adjustor.fRewrite, adjustor.fRoot, null, fRefactoringStatus);
					else {
						final CompilationUnitRewrite rewrite= adjustor.getCompilationUnitRewrite(fMember.getCompilationUnit());
						rewriteVisibility(adjustor, rewrite.getASTRewrite(), rewrite.getRoot(), rewrite.createGroupDescription(Messages.format(RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility, getLabel(getKeyword()))), fRefactoringStatus);
					}
				} else if (fRefactoringStatus != null)
					adjustor.fStatus.merge(fRefactoringStatus);
				monitor.worked(1);
			} finally {
				monitor.done();
			}
		}

		/**
		 * Determines whether the visibility adjustment needs rewriting.
		 * 
		 * @param rewriting <code>true</code> if it needs rewriting, <code>false</code> otherwise
		 */
		public final void setNeedsRewriting(final boolean rewriting) {
			fNeedsRewriting= rewriting;
		}
	}

	/** Interface for visibility adjustments */
	public interface IVisibilityAdjustment {

		/**
		 * Rewrites the visibility adjustment.
		 * 
		 * @param adjustor the java element visibility adjustor
		 * @param monitor the progress monitor to use
		 * @throws JavaModelException if an error occurs
		 */
		public void rewriteVisibility(MemberVisibilityAdjustor adjustor, IProgressMonitor monitor) throws JavaModelException;
	}

	/** Description of an outgoing accessor visibility adjustment */
	public static class OutgoingAccessorVisibilityAdjustment extends OutgoingMemberVisibilityAdjustment {

		/** The accessor method, or <code>null</code> */
		protected final IMethod fAccessor;

		/** Is the accessor method a getter method? */
		protected final boolean fGetter;

		/**
		 * Creates a new outgoing accessor visibility adjustment.
		 * 
		 * @param field the field which is adjusted
		 * @param accessor the used accessor method
		 * @param getter <code>true</code> if the accessor method is a getter, <code>false</code> otherwise
		 * @param keyword the keyword representing the adjusted visibility
		 * @param status the associated status
		 */
		public OutgoingAccessorVisibilityAdjustment(final IField field, final IMethod accessor, final boolean getter, final ModifierKeyword keyword, final RefactoringStatus status) {
			super(field, keyword, status);
			Assert.isNotNull(accessor);
			fAccessor= accessor;
			fGetter= getter;
		}

		/**
		 * Returns the accessor method.
		 * 
		 * @return the accessor method
		 */
		public final IMethod getAccessor() {
			return fAccessor;
		}

		/**
		 * Is the accessor method a getter method?
		 * 
		 * @return <code>true</code> if the accessor method is a getter, <code>false</code> if not or if no accessor is used
		 */
		public final boolean isGetter() {
			return fGetter;
		}
	}

	/** Description of an outgoing member visibility adjustment */
	public static class OutgoingMemberVisibilityAdjustment extends IncomingMemberVisibilityAdjustment {

		/**
		 * Creates a new outgoing member visibility adjustment.
		 * 
		 * @param member the member which is adjusted
		 * @param keyword the keyword representing the adjusted visibility
		 * @param status the associated status
		 */
		public OutgoingMemberVisibilityAdjustment(final IMember member, final ModifierKeyword keyword, final RefactoringStatus status) {
			super(member, keyword, status);
		}

		/*
		 * @see org.eclipse.jdt.internal.corext.refactoring.structure.MemberVisibilityAdjustor.IVisibilityAdjustment#rewriteVisibility(org.eclipse.jdt.internal.corext.refactoring.structure.MemberVisibilityAdjustor, org.eclipse.core.runtime.IProgressMonitor)
		 */
		public void rewriteVisibility(final MemberVisibilityAdjustor adjustor, final IProgressMonitor monitor) throws JavaModelException {
			Assert.isNotNull(adjustor);
			Assert.isNotNull(monitor);
			try {
				monitor.beginTask("", 1); //$NON-NLS-1$
				monitor.setTaskName(RefactoringCoreMessages.MemberVisibilityAdjustor_adjusting);
				if (fNeedsRewriting) {
					final CompilationUnitRewrite rewrite= adjustor.getCompilationUnitRewrite(fMember.getCompilationUnit());
					rewriteVisibility(adjustor, rewrite.getASTRewrite(), rewrite.getRoot(), rewrite.createGroupDescription(Messages.format(RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility, getLabel(getKeyword()))), fRefactoringStatus);
				}
				monitor.worked(1);
			} finally {
				monitor.done();
			}
		}
	}

	/**
	 * Returns the label for the specified java element.
	 * 
	 * @param element the element to get the label for
	 * @return the label for the element
	 */
	public static String getLabel(final IJavaElement element) {
		Assert.isNotNull(element);
		return JavaElementLabels.getElementLabel(element, JavaElementLabels.ALL_FULLY_QUALIFIED | JavaElementLabels.ALL_DEFAULT);
	}

	/**
	 * Returns the label for the specified visibility keyword.
	 * 
	 * @param keyword the keyword to get the label for, or <code>null</code> for default visibility
	 * @return the label for the keyword
	 */
	public static String getLabel(final ModifierKeyword keyword) {
		Assert.isTrue(isVisibilityKeyword(keyword));
		if (keyword == null)
			return RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_default;
		else if (ModifierKeyword.PUBLIC_KEYWORD.equals(keyword))
			return RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_public;
		else if (ModifierKeyword.PROTECTED_KEYWORD.equals(keyword))
			return RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_protected;
		else
			return RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_private;
	}

	/**
	 * Returns the message string for the specified member.
	 * 
	 * @param member the member to get the string for
	 * @return the string for the member
	 */
	public static String getMessage(final IMember member) {
		Assert.isTrue(member instanceof IType || member instanceof IMethod || member instanceof IField);
		if (member instanceof IType)
			return RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_type_warning;
		else if (member instanceof IMethod)
			return RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_method_warning;
		else
			return RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_field_warning;
	}

	/**
	 * Do the specified modifiers represent a lower visibility than the required threshold?
	 * 
	 * @param modifiers the modifiers to test
	 * @param threshold the visibility threshold to compare with
	 * @return <code>true</code> if the visibility is lower than required, <code>false</code> otherwise
	 */
	public static boolean hasLowerVisibility(final int modifiers, final int threshold) {
		if (Modifier.isPrivate(threshold))
			return false;
		else if (Modifier.isPublic(threshold))
			return !Modifier.isPublic(modifiers);
		else if (Modifier.isProtected(threshold))
			return !Modifier.isProtected(modifiers) && !Modifier.isPublic(modifiers);
		else
			return Modifier.isPrivate(modifiers);
	}

	/**
	 * Does the specified modifier keyword represent a lower visibility than the required threshold?
	 * 
	 * @param keyword the visibility keyword to test, or <code>null</code> for default visibility
	 * @param threshold the visibility threshold keyword to compare with, or <code>null</code> to compare with default visibility
	 * @return <code>true</code> if the visibility is lower than required, <code>false</code> otherwise
	 */
	private static boolean hasLowerVisibility(final ModifierKeyword keyword, final ModifierKeyword threshold) {
		Assert.isTrue(isVisibilityKeyword(keyword));
		Assert.isTrue(isVisibilityKeyword(threshold));
		return hasLowerVisibility(keyword != null ? keyword.toFlagValue() : Modifier.NONE, threshold != null ? threshold.toFlagValue() : Modifier.NONE);
	}

	/**
	 * Is the specified severity a refactoring status severity?
	 * 
	 * @param severity the severity to test
	 * @return <code>true</code> if it is a refactoring status severity, <code>false</code> otherwise
	 */
	private static boolean isStatusSeverity(final int severity) {
		return severity == RefactoringStatus.ERROR || severity == RefactoringStatus.FATAL || severity == RefactoringStatus.INFO || severity == RefactoringStatus.OK || severity == RefactoringStatus.WARNING;
	}

	/**
	 * Is the specified modifier keyword a visibility keyword?
	 * 
	 * @param keyword the keyword to test, or <code>null</code>
	 * @return <code>true</code> if it is a visibility keyword, <code>false</code> otherwise
	 */
	private static boolean isVisibilityKeyword(final ModifierKeyword keyword) {
		return keyword == null || ModifierKeyword.PUBLIC_KEYWORD.equals(keyword) || ModifierKeyword.PROTECTED_KEYWORD.equals(keyword) || ModifierKeyword.PRIVATE_KEYWORD.equals(keyword);
	}

	/**
	 * Is the specified modifier a visibility modifier?
	 * 
	 * @param modifier the keyword to test
	 * @return <code>true</code> if it is a visibility modifier, <code>false</code> otherwise
	 */
	private static boolean isVisibilityModifier(final int modifier) {
		return modifier == Modifier.NONE || modifier == Modifier.PUBLIC || modifier == Modifier.PROTECTED || modifier == Modifier.PRIVATE;
	}

	/**
	 * Converts a given modifier kevword into a visibility flag.
	 * 
	 * @param keyword the keyword to convert
	 * @return the visibility flag
	 */
	private static int keywordToVisibility(final ModifierKeyword keyword) {
		int visibility= 0;
		if (keyword == ModifierKeyword.PUBLIC_KEYWORD)
			visibility= Flags.AccPublic;
		else if (keyword == ModifierKeyword.PRIVATE_KEYWORD)
			visibility= Flags.AccPrivate;
		else if (keyword == ModifierKeyword.PROTECTED_KEYWORD)
			visibility= Flags.AccProtected;
		return visibility;
	}

	/**
	 * Does the specified member need further visibility adjustment?
	 * 
	 * @param member the member to test
	 * @param threshold the visibility threshold to test for
	 * @param adjustments the map of members to visibility adjustments
	 * @return <code>true</code> if the member needs further adjustment, <code>false</code> otherwise
	 */
	public static boolean needsVisibilityAdjustments(final IMember member, final int threshold, final Map adjustments) {
		Assert.isNotNull(member);
		Assert.isTrue(isVisibilityModifier(threshold));
		Assert.isNotNull(adjustments);
		final IncomingMemberVisibilityAdjustment adjustment= (IncomingMemberVisibilityAdjustment) adjustments.get(member);
		if (adjustment != null) {
			final ModifierKeyword keyword= adjustment.getKeyword();
			return hasLowerVisibility(keyword == null ? Modifier.NONE : keyword.toFlagValue(), threshold);
		}
		return true;
	}

	/**
	 * Does the specified member need further visibility adjustment?
	 * 
	 * @param member the member to test
	 * @param threshold the visibility threshold to test for, or <code>null</code> for default visibility
	 * @param adjustments the map of members to visibility adjustments
	 * @return <code>true</code> if the member needs further adjustment, <code>false</code> otherwise
	 */
	public static boolean needsVisibilityAdjustments(final IMember member, final ModifierKeyword threshold, final Map adjustments) {
		Assert.isNotNull(member);
		Assert.isNotNull(adjustments);
		final IncomingMemberVisibilityAdjustment adjustment= (IncomingMemberVisibilityAdjustment) adjustments.get(member);
		if (adjustment != null)
			return hasLowerVisibility(adjustment.getKeyword(), threshold);
		return true;
	}

	/** The map of members to visibility adjustments */
	private Map fAdjustments= new HashMap();

	/** The failure message severity */
	private int fFailureSeverity= RefactoringStatus.ERROR;

	/** Should getters be used to resolve visibility issues? */
	private boolean fGetters= true;

	/** Should incoming references be adjusted? */
	private boolean fIncoming= true;

	/** Should outgoing references be adjusted? */
	private boolean fOutgoing= true;

	/** The referenced element causing the visibility adjustment */
	private final IMember fReferenced;

	/** The referencing java element */
	private final IJavaElement fReferencing;

	/** The ast rewrite to use for reference visibility adjustments, or <code>null</code> to use a compilation unit rewrite */
	private ASTRewrite fRewrite= null;

	/** The map of compilation units to compilation unit rewrites */
	private Map fRewrites= new HashMap(3);

	/** The root node of the ast rewrite for reference visibility adjustments, or <code>null</code> to use a compilation unit rewrite */
	private CompilationUnit fRoot= null;

	/** The incoming search scope */
	private IJavaSearchScope fScope;

	/** Should setters be used to resolve visibility issues? */
	private boolean fSetters= true;

	/** The status of the visibility adjustment */
	private RefactoringStatus fStatus= new RefactoringStatus();

	/** The type hierarchy cache */
	private final Map fTypeHierarchies= new HashMap();

	/** The visibility message severity */
	private int fVisibilitySeverity= RefactoringStatus.WARNING;

	/**
	 * Creates a new java element visibility adjustor.
	 * 
	 * @param referencing the referencing element used to compute the visibility
	 * @param referenced the referenced member which causes the visibility changes
	 */
	public MemberVisibilityAdjustor(final IJavaElement referencing, final IMember referenced) {
		Assert.isTrue(!(referenced instanceof IInitializer));
		Assert.isTrue(referencing instanceof ICompilationUnit || referencing instanceof IType || referencing instanceof IPackageFragment);
		fScope= RefactoringScopeFactory.createReferencedScope(new IJavaElement[] { referenced}, IJavaSearchScope.REFERENCED_PROJECTS | IJavaSearchScope.SOURCES | IJavaSearchScope.APPLICATION_LIBRARIES);
		fReferencing= referencing;
		fReferenced= referenced;
	}

	/**
	 * Adjusts the visibility of the specified member.
	 * 
	 * @param member the member to adjust its visibility
	 * @param monitor the progress monitor to use
	 * @throws JavaModelException if the visibility adjustment could not be computed
	 */
	private void adjustIncomingVisibility(final IMember member, final IProgressMonitor monitor) throws JavaModelException {
		final ModifierKeyword threshold= computeIncomingVisibilityThreshold(member, fReferenced, monitor);
		if (hasLowerVisibility(fReferenced.getFlags(), threshold == null ? Modifier.NONE : threshold.toFlagValue()) && needsVisibilityAdjustment(fReferenced, threshold))
			fAdjustments.put(fReferenced, new IncomingMemberVisibilityAdjustment(fReferenced, threshold, RefactoringStatus.createStatus(fVisibilitySeverity, Messages.format(getMessage(fReferenced), new String[] { getLabel(fReferenced), getLabel(threshold)}), JavaStatusContext.create(fReferenced), null, RefactoringStatusEntry.NO_CODE, null)));
	}

	/**
	 * Adjusts the visibility of all members declared in the specified type.
	 * 
	 * @param types the types to adjust its visibility
	 * @param methods the methods to adjust its visibility
	 * @param fields the fields to adjust its visibility
	 * @param monitor the progress monitor to use
	 * @throws JavaModelException if an error occurs
	 */
	private void adjustIncomingVisibility(final IType[] types, final IMethod[] methods, final IField[] fields, final IProgressMonitor monitor) throws JavaModelException {
		try {
			monitor.beginTask("", fields.length + methods.length + types.length); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MemberVisibilityAdjustor_checking);
			IField field= null;
			for (int index= 0; index < fields.length; index++) {
				field= fields[index];
				if (!field.isBinary() && !field.isReadOnly())
					adjustIncomingVisibility(field, new SubProgressMonitor(monitor, 1));
			}
			IMethod method= null;
			for (int index= 0; index < methods.length; index++) {
				method= methods[index];
				if (!method.isBinary() && !method.isReadOnly() && !method.isMainMethod())
					adjustIncomingVisibility(method, new SubProgressMonitor(monitor, 1));
			}
			IType type= null;
			for (int index= 0; index < types.length; index++) {
				type= types[index];
				if (!type.isBinary() && !type.isReadOnly())
					adjustIncomingVisibility(type, new SubProgressMonitor(monitor, 1));
			}
		} finally {
			monitor.done();
		}
	}

	/**
	 * Adjusts the visibility of the specified search match found in a compilation unit.
	 * 
	 * @param match the search match that has been found
	 * @param monitor the progress monitor to use
	 * @throws JavaModelException if the visibility adjustment could not be computed
	 */
	private void adjustIncomingVisibility(final SearchMatch match, final IProgressMonitor monitor) throws JavaModelException {
		final Object element= match.getElement();
		if (element instanceof IMember) {
			IMember member= (IMember) element;
			if (member instanceof IInitializer)
				member= member.getDeclaringType();
			if (member != null)
				adjustIncomingVisibility(member, monitor);
		}
	}

	/**
	 * Adjusts the visibility of the member based on the incoming references represented by the specified search result groups.
	 * 
	 * @param groups the search result groups representing the references
	 * @param monitor the progress monitor to use
	 * @throws JavaModelException if the java elements could not be accessed
	 */
	private void adjustIncomingVisibility(final SearchResultGroup[] groups, final IProgressMonitor monitor) throws JavaModelException {
		try {
			monitor.beginTask("", groups.length); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MemberVisibilityAdjustor_checking);
			SearchMatch[] matches= null;
			SearchResultGroup group= null;
			for (int index= 0; index < groups.length; index++) {
				group= groups[index];
				matches= group.getSearchResults();
				for (int offset= 0; offset < matches.length; offset++)
					adjustIncomingVisibility(matches[offset], new SubProgressMonitor(monitor, 1));
				monitor.worked(1);
			}
		} finally {
			monitor.done();
		}
	}

	/**
	 * Adjusts the visibility of the referenced field found in a compilation unit.
	 * 
	 * @param unit the compilation unit
	 * @param field the referenced field to adjust
	 * @param threshold the visibility threshold, or <code>null</code> for default visibility
	 * @throws JavaModelException if an error occurs
	 */
	private void adjustOutgoingVisibility(final ICompilationUnit unit, final IField field, final ModifierKeyword threshold) throws JavaModelException {
		Assert.isTrue(!field.isBinary() && !field.isReadOnly());
		if (hasLowerVisibility(field.getFlags(), keywordToVisibility(threshold)) && needsVisibilityAdjustment(field, threshold)) {
			if (fGetters) {
				try {
					final IMethod getter= GetterSetterUtil.getGetter(field);
					if (getter != null && getter.exists()) {
						adjustOutgoingVisibility(unit, getter, threshold, RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_method_warning);
						fAdjustments.put(field, new OutgoingAccessorVisibilityAdjustment(field, getter, true, threshold, new RefactoringStatus()));
					}
				} catch (JavaModelException exception) {
					JavaPlugin.log(exception);
				}
			} else if (fSetters) {
				try {
					final IMethod setter= GetterSetterUtil.getSetter(field);
					if (setter != null && setter.exists()) {
						adjustOutgoingVisibility(unit, setter, threshold, RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_method_warning);
						fAdjustments.put(field, new OutgoingAccessorVisibilityAdjustment(field, setter, false, threshold, new RefactoringStatus()));
					}
				} catch (JavaModelException exception) {
					JavaPlugin.log(exception);
				}
			}
			adjustOutgoingVisibility(unit, field, threshold, RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_field_warning);
		}
	}

	/**
	 * Adjusts the visibility of the referenced body declaration.
	 * 
	 * @param unit the compilation unit
	 * @param member the member where to adjust the visibility
	 * @param threshold the visibility keyword representing the required visibility, or <code>null</code> for default visibility
	 * @param template the message template to use
	 * @throws JavaModelException if an error occurs
	 */
	private void adjustOutgoingVisibility(final ICompilationUnit unit, final IMember member, final ModifierKeyword threshold, final String template) throws JavaModelException {
		Assert.isTrue(!member.isBinary() && !member.isReadOnly());
		boolean adjust= true;
		final IType declaring= member.getDeclaringType();
		if (declaring != null && (JavaModelUtil.isInterfaceOrAnnotation(declaring) || declaring.equals(fReferenced)))
			adjust= false;
		if (adjust && hasLowerVisibility(member.getFlags(), keywordToVisibility(threshold)) && needsVisibilityAdjustment(member, threshold))
			fAdjustments.put(member, new OutgoingMemberVisibilityAdjustment(member, threshold, RefactoringStatus.createStatus(fVisibilitySeverity, Messages.format(template, new String[] { JavaElementLabels.getTextLabel(member, JavaElementLabels.M_PARAMETER_TYPES), getLabel(threshold)}), JavaStatusContext.create(member), null, RefactoringStatusEntry.NO_CODE, null)));
	}

	/**
	 * Adjusts the visibilities of the referenced element from the search match found in a compilation unit.
	 * 
	 * @param unit the compilation unit
	 * @param match the search match representing the element declaration
	 * @param monitor the progress monitor to use
	 * @throws JavaModelException if the visibility could not be determined
	 */
	private void adjustOutgoingVisibility(final ICompilationUnit unit, final SearchMatch match, final IProgressMonitor monitor) throws JavaModelException {
		final Object element= match.getElement();
		if (element instanceof IMember) {
			final IMember member= (IMember) element;
			if (!member.isBinary() && !member.isReadOnly()) {
				final ModifierKeyword threshold= computeOutgoingVisibilityThreshold(fReferencing, member, monitor);
				if (element instanceof IMethod) {
					adjustOutgoingVisibility(unit, member, threshold, RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_method_warning);
				} else if (element instanceof IField) {
					adjustOutgoingVisibility(unit, (IField) member, threshold);
				} else if (element instanceof IType) {
					adjustOutgoingVisibility(unit, member, threshold, RefactoringCoreMessages.MemberVisibilityAdjustor_change_visibility_type_warning);
				}
			}
		}
	}

	/**
	 * Adjusts the visibilities of the outgoing references from the member represented by the specified search result groups.
	 * 
	 * @param groups the search result groups representing the references
	 * @param monitor the progress monitor to us
	 * @throws JavaModelException if the visibility could not be determined
	 */
	private void adjustOutgoingVisibility(final SearchResultGroup[] groups, final IProgressMonitor monitor) throws JavaModelException {
		try {
			monitor.beginTask("", groups.length); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MemberVisibilityAdjustor_checking);
			IJavaElement element= null;
			ICompilationUnit unit= null;
			SearchMatch[] matches= null;
			SearchResultGroup group= null;
			for (int index= 0; index < groups.length; index++) {
				group= groups[index];
				element= JavaCore.create(group.getResource());
				if (element instanceof ICompilationUnit) {
					unit= (ICompilationUnit) element;
					matches= group.getSearchResults();
					for (int offset= 0; offset < matches.length; offset++)
						adjustOutgoingVisibility(unit, matches[offset], new SubProgressMonitor(monitor, 1));
				} // else if (element != null)
				// fStatus.merge(RefactoringStatus.createStatus(fFailureSeverity, RefactoringCoreMessages.getFormattedString("MemberVisibilityAdjustor.binary.outgoing.project", new String[] { element.getJavaProject().getElementName(), getLabel(fReferenced)}), null, null, RefactoringStatusEntry.NO_CODE, null)); //$NON-NLS-1$
				// else if (group.getResource() != null)
				// fStatus.merge(RefactoringStatus.createStatus(fFailureSeverity, RefactoringCoreMessages.getFormattedString("MemberVisibilityAdjustor.binary.outgoing.resource", new String[] { group.getResource().getName(), getLabel(fReferenced)}), null, null, RefactoringStatusEntry.NO_CODE, null)); //$NON-NLS-1$

				// TW: enable when bug 78387 is fixed

				monitor.worked(1);
			}
		} finally {
			monitor.done();
		}
	}

	/**
	 * Adjusts the visibilities of the referenced and referencing elements.
	 * 
	 * @param monitor the progress monitor to use
	 * @throws JavaModelException if an error occurs during search
	 */
	public final void adjustVisibility(final IProgressMonitor monitor) throws JavaModelException {
		try {
			monitor.beginTask("", 7); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MemberVisibilityAdjustor_checking);
			final RefactoringSearchEngine2 engine= new RefactoringSearchEngine2(SearchPattern.createPattern(fReferenced, IJavaSearchConstants.REFERENCES, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE));
			engine.setScope(fScope);
			engine.setStatus(fStatus);
			if (fIncoming) {
				engine.searchPattern(new SubProgressMonitor(monitor, 1));
				adjustIncomingVisibility((SearchResultGroup[]) engine.getResults(), new SubProgressMonitor(monitor, 1));
				engine.clearResults();
				if (fReferenced instanceof IType) {
					final IType type= (IType) fReferenced;
					adjustIncomingVisibility(type.getTypes(), type.getMethods(), type.getFields(), new SubProgressMonitor(monitor, 1));
				}
			}
			if (fOutgoing) {
				engine.searchReferencedTypes(fReferenced, new SubProgressMonitor(monitor, 1, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
				engine.searchReferencedFields(fReferenced, new SubProgressMonitor(monitor, 1, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
				engine.searchReferencedMethods(fReferenced, new SubProgressMonitor(monitor, 1, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
				adjustOutgoingVisibility((SearchResultGroup[]) engine.getResults(), new SubProgressMonitor(monitor, 1));
			}
		} finally {
			monitor.done();
		}
	}

	/**
	 * Computes the visibility threshold for the referenced element.
	 * 
	 * @param referencing the referencing element
	 * @param referenced the referenced element
	 * @param monitor the progress monitor to use
	 * @return the visibility keyword corresponding to the threshold, or <code>null</code> for default visibility
	 * @throws JavaModelException if the java elements could not be accessed
	 */
	private ModifierKeyword computeIncomingVisibilityThreshold(final IMember referencing, final IMember referenced, final IProgressMonitor monitor) throws JavaModelException {
		Assert.isTrue(!(referencing instanceof IInitializer));
		Assert.isTrue(!(referenced instanceof IInitializer));
		ModifierKeyword keyword= ModifierKeyword.PUBLIC_KEYWORD;
		try {
			monitor.beginTask("", 1); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MemberVisibilityAdjustor_checking);
			final int referencingType= referencing.getElementType();
			final int referencedType= referenced.getElementType();
			switch (referencedType) {
				case IJavaElement.TYPE: {
					final IType typeReferenced= (IType) referenced;
					final ICompilationUnit referencedUnit= typeReferenced.getCompilationUnit();
					switch (referencingType) {
						case IJavaElement.TYPE: {
							keyword= thresholdTypeToType((IType) referencing, typeReferenced, monitor);
							break;
						}
						case IJavaElement.FIELD: {
							final IField field= (IField) referencing;
							if (typeReferenced.equals(field.getDeclaringType()))
								keyword= ModifierKeyword.PRIVATE_KEYWORD;
							else if (referencedUnit != null && referencedUnit.equals(field.getCompilationUnit()))
								keyword= ModifierKeyword.PRIVATE_KEYWORD;
							else if (typeReferenced.getPackageFragment().equals(field.getDeclaringType().getPackageFragment()))
								keyword= null;
							break;
						}
						case IJavaElement.METHOD: {
							final IMethod method= (IMethod) referencing;
							if (typeReferenced.equals(method.getDeclaringType()))
								keyword= ModifierKeyword.PRIVATE_KEYWORD;
							else if (referencedUnit != null && referencedUnit.equals(method.getCompilationUnit()))
								keyword= ModifierKeyword.PRIVATE_KEYWORD;
							else if (typeReferenced.getPackageFragment().equals(method.getDeclaringType().getPackageFragment()))
								keyword= null;
							break;
						}
						default:
							Assert.isTrue(false);
					}
					break;
				}
				case IJavaElement.FIELD: {
					final IField fieldReferenced= (IField) referenced;
					final ICompilationUnit referencedUnit= fieldReferenced.getCompilationUnit();
					switch (referencingType) {
						case IJavaElement.TYPE: {
							keyword= thresholdTypeToField((IType) referencing, fieldReferenced, monitor);
							break;
						}
						case IJavaElement.FIELD: {
							final IField field= (IField) referencing;
							if (fieldReferenced.getDeclaringType().equals(field.getDeclaringType()))
								keyword= ModifierKeyword.PRIVATE_KEYWORD;
							else if (referencedUnit != null && referencedUnit.equals(field.getCompilationUnit()))
								keyword= ModifierKeyword.PRIVATE_KEYWORD;
							else if (fieldReferenced.getDeclaringType().getPackageFragment().equals(field.getDeclaringType().getPackageFragment()))
								keyword= null;
							break;
						}
						case IJavaElement.METHOD: {
							final IMethod method= (IMethod) referencing;
							if (fieldReferenced.getDeclaringType().equals(method.getDeclaringType()))
								keyword= ModifierKeyword.PRIVATE_KEYWORD;
							else if (referencedUnit != null && referencedUnit.equals(method.getCompilationUnit()))
								keyword= ModifierKeyword.PRIVATE_KEYWORD;
							else if (fieldReferenced.getDeclaringType().getPackageFragment().equals(method.getDeclaringType().getPackageFragment()))
								keyword= null;
							break;
						}
						default:
							Assert.isTrue(false);
					}
					break;
				}
				case IJavaElement.METHOD: {
					final IMethod methodReferenced= (IMethod) referenced;
					final ICompilationUnit referencedUnit= methodReferenced.getCompilationUnit();
					switch (referencingType) {
						case IJavaElement.TYPE: {
							keyword= thresholdTypeToMethod((IType) referencing, methodReferenced, monitor);
							break;
						}
						case IJavaElement.FIELD: {
							final IField field= (IField) referencing;
							if (methodReferenced.getDeclaringType().equals(field.getDeclaringType()))
								keyword= ModifierKeyword.PRIVATE_KEYWORD;
							else if (referencedUnit != null && referencedUnit.equals(field.getCompilationUnit()))
								keyword= ModifierKeyword.PRIVATE_KEYWORD;
							else if (methodReferenced.getDeclaringType().getPackageFragment().equals(field.getDeclaringType().getPackageFragment()))
								keyword= null;
							break;
						}
						case IJavaElement.METHOD: {
							final IMethod method= (IMethod) referencing;
							if (methodReferenced.getDeclaringType().equals(method.getDeclaringType()))
								keyword= ModifierKeyword.PRIVATE_KEYWORD;
							else if (referencedUnit != null && referencedUnit.equals(method.getCompilationUnit()))
								keyword= ModifierKeyword.PRIVATE_KEYWORD;
							else if (methodReferenced.getDeclaringType().getPackageFragment().equals(method.getDeclaringType().getPackageFragment()))
								keyword= null;
							break;
						}
						default:
							Assert.isTrue(false);
					}
					break;
				}
				default:
					Assert.isTrue(false);
			}
		} finally {
			monitor.done();
		}
		return keyword;
	}

	/**
	 * Computes the visibility threshold for the referenced element.
	 * 
	 * @param referencing the referencing element
	 * @param referenced the referenced element
	 * @param monitor the progress monitor to use
	 * @return the visibility keyword corresponding to the threshold, or <code>null</code> for default visibility
	 * @throws JavaModelException if the java elements could not be accessed
	 */
	private ModifierKeyword computeOutgoingVisibilityThreshold(final IJavaElement referencing, final IMember referenced, final IProgressMonitor monitor) throws JavaModelException {
		Assert.isTrue(referencing instanceof ICompilationUnit || referencing instanceof IType || referencing instanceof IPackageFragment);
		Assert.isTrue(referenced instanceof IType || referenced instanceof IField || referenced instanceof IMethod);
		ModifierKeyword keyword= ModifierKeyword.PUBLIC_KEYWORD;
		try {
			monitor.beginTask("", 1); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MemberVisibilityAdjustor_checking);
			final int referencingType= referencing.getElementType();
			final int referencedType= referenced.getElementType();
			switch (referencedType) {
				case IJavaElement.TYPE: {
					final IType typeReferenced= (IType) referenced;
					switch (referencingType) {
						case IJavaElement.COMPILATION_UNIT: {
							final ICompilationUnit unit= (ICompilationUnit) referencing;
							final ICompilationUnit referencedUnit= typeReferenced.getCompilationUnit();
							if (referencedUnit != null && referencedUnit.equals(unit))
								keyword= ModifierKeyword.PRIVATE_KEYWORD;
							else if (referencedUnit != null && referencedUnit.getParent().equals(unit.getParent()))
								keyword= null;
							break;
						}
						case IJavaElement.TYPE: {
							keyword= thresholdTypeToType((IType) referencing, typeReferenced, monitor);
							break;
						}
						case IJavaElement.PACKAGE_FRAGMENT: {
							final IPackageFragment fragment= (IPackageFragment) referencing;
							if (typeReferenced.getPackageFragment().equals(fragment))
								keyword= null;
							break;
						}
						default:
							Assert.isTrue(false);
					}
					break;
				}
				case IJavaElement.FIELD: {
					final IField fieldReferenced= (IField) referenced;
					final ICompilationUnit referencedUnit= fieldReferenced.getCompilationUnit();
					switch (referencingType) {
						case IJavaElement.COMPILATION_UNIT: {
							final ICompilationUnit unit= (ICompilationUnit) referencing;
							if (referencedUnit != null && referencedUnit.equals(unit))
								keyword= ModifierKeyword.PRIVATE_KEYWORD;
							else if (referencedUnit != null && referencedUnit.getParent().equals(unit.getParent()))
								keyword= null;
							break;
						}
						case IJavaElement.TYPE: {
							keyword= thresholdTypeToField((IType) referencing, fieldReferenced, monitor);
							break;
						}
						case IJavaElement.PACKAGE_FRAGMENT: {
							final IPackageFragment fragment= (IPackageFragment) referencing;
							if (fieldReferenced.getDeclaringType().getPackageFragment().equals(fragment))
								keyword= null;
							break;
						}
						default:
							Assert.isTrue(false);
					}
					break;
				}
				case IJavaElement.METHOD: {
					final IMethod methodReferenced= (IMethod) referenced;
					final ICompilationUnit referencedUnit= methodReferenced.getCompilationUnit();
					switch (referencingType) {
						case IJavaElement.COMPILATION_UNIT: {
							final ICompilationUnit unit= (ICompilationUnit) referencing;
							if (referencedUnit != null && referencedUnit.equals(unit))
								keyword= ModifierKeyword.PRIVATE_KEYWORD;
							else if (referencedUnit != null && referencedUnit.getParent().equals(unit.getParent()))
								keyword= null;
							break;
						}
						case IJavaElement.TYPE: {
							keyword= thresholdTypeToMethod((IType) referencing, methodReferenced, monitor);
							break;
						}
						case IJavaElement.PACKAGE_FRAGMENT: {
							final IPackageFragment fragment= (IPackageFragment) referencing;
							if (methodReferenced.getDeclaringType().getPackageFragment().equals(fragment))
								keyword= null;
							break;
						}
						default:
							Assert.isTrue(false);
					}
					break;
				}
				default:
					Assert.isTrue(false);
			}
		} finally {
			monitor.done();
		}
		return keyword;
	}

	/**
	 * Returns the existing visibility adjustments (element type: Map <IMember, IVisibilityAdjustment>).
	 * 
	 * @return the visibility adjustments
	 */
	public final Map getAdjustments() {
		return fAdjustments;
	}

	/**
	 * Returns a compilation unit rewrite for the specified compilation unit.
	 * 
	 * @param unit the compilation unit to get the rewrite for
	 * @return the rewrite for the compilation unit
	 */
	private CompilationUnitRewrite getCompilationUnitRewrite(final ICompilationUnit unit) {
		CompilationUnitRewrite rewrite= (CompilationUnitRewrite) fRewrites.get(unit);
		if (rewrite == null)
			rewrite= new CompilationUnitRewrite(unit);
		return rewrite;
	}

	/**
	 * Returns a cached type hierarchy for the specified type.
	 * 
	 * @param type the type to get the hierarchy for
	 * @param monitor the progress monitor to use
	 * @return the type hierarchy
	 * @throws JavaModelException if the type hierarchy could not be created
	 */
	private ITypeHierarchy getTypeHierarchy(final IType type, final IProgressMonitor monitor) throws JavaModelException {
		ITypeHierarchy hierarchy= null;
		try {
			monitor.beginTask("", 1); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MemberVisibilityAdjustor_checking);
			try {
				hierarchy= (ITypeHierarchy) fTypeHierarchies.get(type);
				if (hierarchy == null)
					hierarchy= type.newSupertypeHierarchy(new SubProgressMonitor(monitor, 1, SubProgressMonitor.SUPPRESS_SUBTASK_LABEL));
			} finally {
				monitor.done();
			}
		} finally {
			monitor.done();
		}
		return hierarchy;
	}

	/**
	 * Does the specified member need further visibility adjustment?
	 * 
	 * @param member the member to test
	 * @param threshold the visibility threshold to test for
	 * @return <code>true</code> if the member needs further adjustment, <code>false</code> otherwise
	 */
	private boolean needsVisibilityAdjustment(final IMember member, final ModifierKeyword threshold) {
		Assert.isNotNull(member);
		return needsVisibilityAdjustments(member, threshold, fAdjustments);
	}

	/**
	 * Rewrites the computed adjustments for the specified compilation unit.
	 * 
	 * @param unit the compilation unit to rewrite the adjustments
	 * @param monitor the progress monitor to use
	 * @throws JavaModelException if an error occurs during search
	 */
	public final void rewriteVisibility(final ICompilationUnit unit, final IProgressMonitor monitor) throws JavaModelException {
		try {
			monitor.beginTask("", fAdjustments.keySet().size()); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MemberVisibilityAdjustor_adjusting);
			IMember member= null;
			IVisibilityAdjustment adjustment= null;
			for (final Iterator iterator= fAdjustments.keySet().iterator(); iterator.hasNext();) {
				member= (IMember) iterator.next();
				if (unit.equals(member.getCompilationUnit())) {
					adjustment= (IVisibilityAdjustment) fAdjustments.get(member);
					if (adjustment != null)
						adjustment.rewriteVisibility(this, new SubProgressMonitor(monitor, 1));
				}
			}
		} finally {
			fTypeHierarchies.clear();
			monitor.done();
		}
	}

	/**
	 * Rewrites the computed adjustments.
	 * 
	 * @param monitor the progress monitor to use
	 * @throws JavaModelException if an error occurs during search
	 */
	public final void rewriteVisibility(final IProgressMonitor monitor) throws JavaModelException {
		try {
			monitor.beginTask("", fAdjustments.keySet().size()); //$NON-NLS-1$
			monitor.setTaskName(RefactoringCoreMessages.MemberVisibilityAdjustor_adjusting);
			IMember member= null;
			IVisibilityAdjustment adjustment= null;
			for (final Iterator iterator= fAdjustments.keySet().iterator(); iterator.hasNext();) {
				member= (IMember) iterator.next();
				adjustment= (IVisibilityAdjustment) fAdjustments.get(member);
				if (adjustment != null)
					adjustment.rewriteVisibility(this, new SubProgressMonitor(monitor, 1));
			}
		} finally {
			fTypeHierarchies.clear();
			monitor.done();
		}
	}

	/**
	 * Sets the existing visibility adjustments to be taken into account (element type: Map <IMember, IVisibilityAdjustment>).
	 * <p>
	 * This method must be called before calling {@link MemberVisibilityAdjustor#adjustVisibility(IProgressMonitor)}. The default is to take no existing adjustments into account.
	 * 
	 * @param adjustments the existing adjustments to set
	 */
	public final void setAdjustments(final Map adjustments) {
		Assert.isNotNull(adjustments);
		fAdjustments= adjustments;
	}

	/**
	 * Sets the severity of failure messages.
	 * <p>
	 * This method must be called before calling {@link MemberVisibilityAdjustor#adjustVisibility(IProgressMonitor)}. The default is a status with value {@link RefactoringStatus#ERROR}.
	 * 
	 * @param severity the severity of failure messages
	 */
	public final void setFailureSeverity(final int severity) {
		Assert.isTrue(isStatusSeverity(severity));
		fFailureSeverity= severity;
	}

	/**
	 * Determines whether getters should be preferred to resolve visibility issues.
	 * <p>
	 * This method must be called before calling {@link MemberVisibilityAdjustor#adjustVisibility(IProgressMonitor)}. The default is to use getters where possible.
	 * 
	 * @param use <code>true</code> if getters should be used, <code>false</code> otherwise
	 */
	public final void setGetters(final boolean use) {
		fGetters= use;
	}

	/**
	 * Determines whether incoming references should be adjusted.
	 * <p>
	 * This method must be called before calling {@link MemberVisibilityAdjustor#adjustVisibility(IProgressMonitor)}. The default is to adjust incoming references.
	 * 
	 * @param incoming <code>true</code> if incoming references should be adjusted, <code>false</code> otherwise
	 */
	public final void setIncoming(final boolean incoming) {
		fIncoming= incoming;
	}

	/**
	 * Determines whether outgoing references should be adjusted.
	 * <p>
	 * This method must be called before calling {@link MemberVisibilityAdjustor#adjustVisibility(IProgressMonitor)}. The default is to adjust outgoing references.
	 * 
	 * @param outgoing <code>true</code> if outgoing references should be adjusted, <code>false</code> otherwise
	 */
	public final void setOutgoing(final boolean outgoing) {
		fOutgoing= outgoing;
	}

	/**
	 * Sets the ast rewrite to use for member visibility adjustments.
	 * <p>
	 * This method must be called before calling {@link MemberVisibilityAdjustor#adjustVisibility(IProgressMonitor)}. The default is to use a compilation unit rewrite.
	 * 
	 * @param rewrite the ast rewrite to set
	 * @param root the root of the ast used in the rewrite
	 */
	public final void setRewrite(final ASTRewrite rewrite, final CompilationUnit root) {
		Assert.isTrue(rewrite == null || root != null);
		fRewrite= rewrite;
		fRoot= root;
	}

	/**
	 * Sets the compilation unit rewrites used by this adjustor (element type: Map <ICompilationUnit, CompilationUnitRewrite>).
	 * <p>
	 * This method must be called before calling {@link MemberVisibilityAdjustor#adjustVisibility(IProgressMonitor)}. The default is to use no existing rewrites.
	 * 
	 * @param rewrites the map of compilation units to compilation unit rewrites to set
	 */
	public final void setRewrites(final Map rewrites) {
		Assert.isNotNull(rewrites);
		fRewrites= rewrites;
	}

	/**
	 * Sets the incoming search scope used by this adjustor.
	 * <p>
	 * This method must be called before calling {@link MemberVisibilityAdjustor#adjustVisibility(IProgressMonitor)}. The default is the whole workspace as scope.
	 * 
	 * @param scope the search scope to set
	 */
	public final void setScope(final IJavaSearchScope scope) {
		Assert.isNotNull(scope);
		fScope= scope;
	}

	/**
	 * Determines whether getters should be preferred to resolve visibility issues.
	 * <p>
	 * This method must be called before calling {@link MemberVisibilityAdjustor#adjustVisibility(IProgressMonitor)}. The default is to use setters where possible.
	 * 
	 * @param use <code>true</code> if setters should be used, <code>false</code> otherwise
	 */
	public final void setSetters(final boolean use) {
		fSetters= use;
	}

	/**
	 * Sets the refactoring status used by this adjustor.
	 * <p>
	 * This method must be called before calling {@link MemberVisibilityAdjustor#adjustVisibility(IProgressMonitor)}. The default is a fresh status with status {@link RefactoringStatus#OK}.
	 * 
	 * @param status the refactoring status to set
	 */
	public final void setStatus(final RefactoringStatus status) {
		Assert.isNotNull(status);
		fStatus= status;
	}

	/**
	 * Sets the severity of visibility messages.
	 * <p>
	 * This method must be called before calling {@link MemberVisibilityAdjustor#adjustVisibility(IProgressMonitor)}. The default is a status with value {@link RefactoringStatus#WARNING}.
	 * 
	 * @param severity the severity of visibility messages
	 */
	public final void setVisibilitySeverity(final int severity) {
		Assert.isTrue(isStatusSeverity(severity));
		fVisibilitySeverity= severity;
	}

	/**
	 * Returns the visibility threshold from a type to a field.
	 * 
	 * @param referencing the referencing type
	 * @param referenced the referenced field
	 * @param monitor the progress monitor to use
	 * @return the visibility keyword corresponding to the threshold, or <code>null</code> for default visibility
	 * @throws JavaModelException if the java elements could not be accessed
	 */
	private ModifierKeyword thresholdTypeToField(final IType referencing, final IField referenced, final IProgressMonitor monitor) throws JavaModelException {
		ModifierKeyword keyword= ModifierKeyword.PUBLIC_KEYWORD;
		final ICompilationUnit referencedUnit= referenced.getCompilationUnit();
		if (referenced.getDeclaringType().equals(referencing))
			keyword= ModifierKeyword.PRIVATE_KEYWORD;
		else {
			final ITypeHierarchy hierarchy= getTypeHierarchy(referencing, new SubProgressMonitor(monitor, 1));
			final IType[] types= hierarchy.getSupertypes(referencing);
			IType superType= null;
			for (int index= 0; index < types.length; index++) {
				superType= types[index];
				if (superType.equals(referenced.getDeclaringType())) {
					keyword= ModifierKeyword.PROTECTED_KEYWORD;
					return keyword;
				}
			}
		}
		final ICompilationUnit typeUnit= referencing.getCompilationUnit();
		if (referencedUnit != null && referencedUnit.equals(typeUnit))
			keyword= ModifierKeyword.PRIVATE_KEYWORD;
		else if (referencedUnit != null && typeUnit != null && referencedUnit.getParent().equals(typeUnit.getParent()))
			keyword= null;
		return keyword;
	}

	/**
	 * Returns the visibility threshold from a type to a method.
	 * 
	 * @param referencing the referencing type
	 * @param referenced the referenced method
	 * @param monitor the progress monitor to use
	 * @return the visibility keyword corresponding to the threshold, or <code>null</code> for default visibility
	 * @throws JavaModelException if the java elements could not be accessed
	 */
	private ModifierKeyword thresholdTypeToMethod(final IType referencing, final IMethod referenced, final IProgressMonitor monitor) throws JavaModelException {
		final ICompilationUnit referencedUnit= referenced.getCompilationUnit();
		ModifierKeyword keyword= null;
		if (referenced.getDeclaringType().equals(referencing))
			keyword= ModifierKeyword.PRIVATE_KEYWORD;
		else {
			final ITypeHierarchy hierarchy= getTypeHierarchy(referencing, new SubProgressMonitor(monitor, 1));
			final IType[] types= hierarchy.getSupertypes(referencing);
			IType superType= null;
			for (int index= 0; index < types.length; index++) {
				superType= types[index];
				if (superType.equals(referenced.getDeclaringType())) {
					keyword= ModifierKeyword.PROTECTED_KEYWORD;
					return keyword;
				}
			}
		}
		final ICompilationUnit typeUnit= referencing.getCompilationUnit();
		if (referencedUnit != null && referencedUnit.equals(typeUnit)) {
			if (referenced.getDeclaringType().getDeclaringType() != null)
				keyword= null;
			else
				keyword= ModifierKeyword.PRIVATE_KEYWORD;
		} else if (referencedUnit != null && referencedUnit.getParent().equals(typeUnit.getParent()))
			keyword= null;
		return keyword;
	}

	/**
	 * Returns the visibility threshold from a type to another type.
	 * 
	 * @param referencing the referencing type
	 * @param referenced the referenced type
	 * @param monitor the progress monitor to use
	 * @return the visibility keyword corresponding to the threshold, or <code>null</code> for default visibility
	 * @throws JavaModelException if the java elements could not be accessed
	 */
	private ModifierKeyword thresholdTypeToType(final IType referencing, final IType referenced, final IProgressMonitor monitor) throws JavaModelException {
		ModifierKeyword keyword= ModifierKeyword.PUBLIC_KEYWORD;
		final ICompilationUnit referencedUnit= referenced.getCompilationUnit();
		if (referencing.equals(referenced.getDeclaringType()))
			keyword= ModifierKeyword.PRIVATE_KEYWORD;
		else {
			final ITypeHierarchy hierarchy= getTypeHierarchy(referencing, new SubProgressMonitor(monitor, 1));
			final IType[] types= hierarchy.getSupertypes(referencing);
			IType superType= null;
			for (int index= 0; index < types.length; index++) {
				superType= types[index];
				if (superType.equals(referenced)) {
					keyword= null;
					return keyword;
				}
			}
		}
		final ICompilationUnit typeUnit= referencing.getCompilationUnit();
		if (referencedUnit != null && referencedUnit.equals(typeUnit)) {
			if (referenced.getDeclaringType() != null)
				keyword= null;
			else
				keyword= ModifierKeyword.PRIVATE_KEYWORD;
		} else if (referencedUnit != null && typeUnit != null && referencedUnit.getParent().equals(typeUnit.getParent()))
			keyword= null;
		return keyword;
	}
}