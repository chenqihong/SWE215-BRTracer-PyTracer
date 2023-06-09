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
package org.eclipse.jdt.internal.ui.text.java;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;

import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.jface.dialogs.MessageDialog;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.jface.text.contentassist.IContextInformationExtension;
import org.eclipse.jface.text.link.LinkedModeModel;
import org.eclipse.jface.text.link.LinkedModeUI;
import org.eclipse.jface.text.link.LinkedPosition;
import org.eclipse.jface.text.link.LinkedPositionGroup;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.link.EditorLinkedModeUI;

import org.eclipse.jdt.core.CompletionContext;
import org.eclipse.jdt.core.CompletionProposal;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTRequestor;
import org.eclipse.jdt.core.dom.IBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import org.eclipse.jdt.internal.corext.template.java.SignatureUtil;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.EditorHighlightingSynchronizer;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;

/**
 * An experimental proposal.
 */
public final class GenericJavaTypeProposal extends LazyJavaTypeCompletionProposal {

	/**
	 * Short-lived context information object for generic types. Currently, these
	 * are only created after inserting a type proposal, as core doesn't give us
	 * the correct type proposal from within SomeType<|>.
	 */
	private static class ContextInformation implements IContextInformation, IContextInformationExtension {
		private final String fInformationDisplayString;
		private final String fContextDisplayString;
		private final Image fImage;
		private final int fPosition;
		
		ContextInformation(GenericJavaTypeProposal proposal) {
			// don't cache the proposal as content assistant
			// might hang on to the context info
			fContextDisplayString= proposal.getDisplayString();
			fInformationDisplayString= computeContextString(proposal);
			fImage= proposal.getImage();
			fPosition= proposal.getReplacementOffset() + proposal.getReplacementString().indexOf('<') + 1;
		}
		
		/*
		 * @see org.eclipse.jface.text.contentassist.IContextInformation#getContextDisplayString()
		 */
		public String getContextDisplayString() {
			return fContextDisplayString;
		}

		/*
		 * @see org.eclipse.jface.text.contentassist.IContextInformation#getImage()
		 */
		public Image getImage() {
			return fImage;
		}

		/*
		 * @see org.eclipse.jface.text.contentassist.IContextInformation#getInformationDisplayString()
		 */
		public String getInformationDisplayString() {
			return fInformationDisplayString;
		}

		private String computeContextString(GenericJavaTypeProposal proposal) {
			try {
				TypeArgumentProposal[] proposals= proposal.computeTypeArgumentProposals();
				if (proposals.length == 0)
					return null;
				
				StringBuffer buf= new StringBuffer();
				for (int i= 0; i < proposals.length; i++) {
					buf.append(proposals[i].getDisplayName());
					if (i < proposals.length - 1)
						buf.append(", "); //$NON-NLS-1$
				}
				return buf.toString();
				
			} catch (JavaModelException e) {
				return null;
			}
		}

		/*
		 * @see org.eclipse.jface.text.contentassist.IContextInformationExtension#getContextInformationPosition()
		 */
		public int getContextInformationPosition() {
			return fPosition;
		}
		
		/*
		 * @see java.lang.Object#equals(java.lang.Object)
		 */
		public boolean equals(Object obj) {
			if (obj instanceof ContextInformation) {
				ContextInformation ci= (ContextInformation) obj;
				return getContextInformationPosition() == ci.getContextInformationPosition() && getInformationDisplayString().equals(ci.getInformationDisplayString());
			}
			return false;
		}
	}

	private static final class TypeArgumentProposal {
		private final boolean fIsAmbiguous;
		private final String fProposal;
		private final String fTypeDisplayName;

		TypeArgumentProposal(String proposal, boolean ambiguous, String typeDisplayName) {
			fIsAmbiguous= ambiguous;
			fProposal= proposal;
			fTypeDisplayName= typeDisplayName;
		}

		public String getDisplayName() {
			return fTypeDisplayName;
		}

		boolean isAmbiguous() {
			return fIsAmbiguous;
		}

		String getProposals() {
			return fProposal;
		}

		public String toString() {
			return fProposal;
		}
	}

	private static final class FormatterPrefs {
		final boolean beforeOpeningBracket;
		final boolean afterOpeningBracket;
		final boolean beforeComma;
		final boolean afterComma;
		final boolean beforeClosingBracket;

		FormatterPrefs(IJavaProject project) {
			beforeOpeningBracket= getCoreOption(project, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_OPENING_ANGLE_BRACKET_IN_PARAMETERIZED_TYPE_REFERENCE, false);
			afterOpeningBracket= getCoreOption(project, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_OPENING_ANGLE_BRACKET_IN_PARAMETERIZED_TYPE_REFERENCE, false);
			beforeComma= getCoreOption(project, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_COMMA_IN_PARAMETERIZED_TYPE_REFERENCE, false);
			afterComma= getCoreOption(project, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_AFTER_COMMA_IN_PARAMETERIZED_TYPE_REFERENCE, true);
			beforeClosingBracket= getCoreOption(project, DefaultCodeFormatterConstants.FORMATTER_INSERT_SPACE_BEFORE_CLOSING_ANGLE_BRACKET_IN_PARAMETERIZED_TYPE_REFERENCE, false);
		}

		private boolean getCoreOption(IJavaProject project, String key, boolean def) {
			String option= getCoreOption(project, key);
			if (JavaCore.INSERT.equals(option))
				return true;
			if (JavaCore.DO_NOT_INSERT.equals(option))
				return false;
			return def;
		}

		private String getCoreOption(IJavaProject project, String key) {
			if (project == null)
				return JavaCore.getOption(key);
			return project.getOption(key, true);
		}
	}

	private IRegion fSelectedRegion; // initialized by apply()
	private final CompletionContext fContext;
	private TypeArgumentProposal[] fTypeArgumentProposals;

	public GenericJavaTypeProposal(CompletionProposal typeProposal, CompletionContext context, ICompilationUnit cu) {
		super(typeProposal, cu);
		fContext= context;
	}

	/*
	 * @see ICompletionProposalExtension#apply(IDocument, char)
	 */
	public void apply(IDocument document, char trigger, int offset) {

		if (shouldAppendArguments(document, offset)) {
			try {
				TypeArgumentProposal[] typeArgumentProposals= computeTypeArgumentProposals();
				if (typeArgumentProposals.length > 0) {

					int[] offsets= new int[typeArgumentProposals.length];
					int[] lengths= new int[typeArgumentProposals.length];
					StringBuffer buffer= createParameterList(typeArgumentProposals, offsets, lengths);

					// set the generic type as replacement string
					super.setReplacementString(buffer.toString());
					// add import & remove package, update replacement offset
					super.apply(document, trigger, offset);

					if (getTextViewer() != null) {
						if (hasAmbiguousProposals(typeArgumentProposals)) {
							adaptOffsets(offsets, buffer);
							installLinkedMode(document, offsets, lengths, typeArgumentProposals);
						} else {
							fSelectedRegion= new Region(getReplacementOffset() + getReplacementString().length(), 0);
						}
					}

					return;
				}
			} catch (JavaModelException e) {
				// log and continue
				JavaPlugin.log(e);
			}
		}

		// default is to use the super implementation
		// reasons:
		// - not a parameterized type,
		// - already followed by <type arguments>
		// - proposal type does not inherit from expected type
		super.apply(document, trigger, offset);
	}

	/**
	 * Adapt the parameter offsets to any modification of the replacement
	 * string done by <code>apply</code>. For example, applying the proposal
	 * may add an import instead of inserting the fully qualified name.
	 * <p>
	 * This assumes that modifications happen only at the beginning of the
	 * replacement string and do not touch the type arguments list.
	 * </p>
	 *
	 * @param offsets the offsets to modify
	 * @param buffer the original replacement string
	 */
	private void adaptOffsets(int[] offsets, StringBuffer buffer) {
		String replacementString= getReplacementString();
		int delta= buffer.length() - replacementString.length(); // due to using an import instead of package
		for (int i= 0; i < offsets.length; i++) {
			offsets[i]-= delta;
		}
	}

	/**
	 * Computes the type argument proposals for this type proposals. If there is
	 * an expected type binding that is a super type of the proposed type, the
	 * wildcard type arguments of the proposed type that can be mapped through
	 * to type the arguments of the expected type binding are bound accordingly.
	 * <p>
	 * For type arguments that cannot be mapped to arguments in the expected
	 * type, or if there is no expected type, the upper bound of the type
	 * argument is proposed.
	 * </p>
	 * <p>
	 * The argument proposals have their <code>isAmbiguos</code> flag set to
	 * <code>false</code> if the argument can be mapped to a non-wildcard type
	 * argument in the expected type, otherwise the proposal is ambiguous.
	 * </p>
	 *
	 * @return the type argument proposals for the proposed type
	 * @throws JavaModelException if accessing the java model fails
	 */
	private TypeArgumentProposal[] computeTypeArgumentProposals() throws JavaModelException {
		if (fTypeArgumentProposals == null) {
			
			IType type= getProposedType();
			if (type == null)
				return new TypeArgumentProposal[0];
			
			ITypeParameter[] parameters= type.getTypeParameters();
			if (parameters.length == 0)
				return new TypeArgumentProposal[0];
			
			TypeArgumentProposal[] arguments= new TypeArgumentProposal[parameters.length];
			
			ITypeBinding expectedTypeBinding= getExpectedType();
			if (expectedTypeBinding != null && expectedTypeBinding.isParameterizedType()) {
				// in this case, the type arguments we propose need to be compatible
				// with the corresponding type parameters to declared type
				
				IType expectedType= (IType) expectedTypeBinding.getJavaElement();
				
				IType[] path= computeInheritancePath(type, expectedType);
				if (path == null)
					// proposed type does not inherit from expected type
					// the user might be looking for an inner type of proposed type
					// to instantiate -> do not add any type arguments
					return new TypeArgumentProposal[0];
				
				int[] indices= new int[parameters.length];
				for (int paramIdx= 0; paramIdx < parameters.length; paramIdx++) {
					indices[paramIdx]= mapTypeParameterIndex(path, path.length - 1, paramIdx);
				}
				
				// for type arguments that are mapped through to the expected type's
				// parameters, take the arguments of the expected type
				ITypeBinding[] typeArguments= expectedTypeBinding.getTypeArguments();
				for (int paramIdx= 0; paramIdx < parameters.length; paramIdx++) {
					if (indices[paramIdx] != -1) {
						// type argument is mapped through
						ITypeBinding binding= typeArguments[indices[paramIdx]];
						arguments[paramIdx]= computeTypeProposal(binding, parameters[paramIdx]);
					}
				}
			}
			
			// for type arguments that are not mapped through to the expected type,
			// take the lower bound of the type parameter
			for (int i= 0; i < arguments.length; i++) {
				if (arguments[i] == null) {
					arguments[i]= computeTypeProposal(type, parameters[i]);
				}
			}
			fTypeArgumentProposals= arguments;
		}
		return fTypeArgumentProposals;
	}

	/**
	 * Returns a type argument proposal for a given type binding. The proposal
	 * is the simple type name for unbounded types or the upper bound of
	 * wildcard types with a single bound.
	 *
	 * @param parameter the type parameter
	 * @return a type argument proposal for <code>parameter</code>
	 * @throws JavaModelException
	 */
	private TypeArgumentProposal computeTypeProposal(IType type, ITypeParameter parameter) throws JavaModelException {
		String[] bounds= parameter.getBounds();
		String elementName= parameter.getElementName();
		String displayName= computeTypeParameterDisplayName(parameter, bounds);
		if (bounds.length == 1 && !"java.lang.Object".equals(bounds[0])) //$NON-NLS-1$
			return new TypeArgumentProposal(Signature.getSimpleName(bounds[0]), true, displayName);
		else
			return new TypeArgumentProposal(elementName, true, displayName);
	}

	private String computeTypeParameterDisplayName(ITypeParameter parameter, String[] bounds) {
		if (bounds.length == 0 || bounds.length == 1 && "java.lang.Object".equals(bounds[0])) //$NON-NLS-1$
			return parameter.getElementName();
		StringBuffer buf= new StringBuffer(parameter.getElementName());
		buf.append(" extends "); //$NON-NLS-1$
		for (int i= 0; i < bounds.length; i++) {
			buf.append(Signature.getSimpleName(bounds[i]));
			if (i < bounds.length - 1)
				buf.append(" & "); //$NON-NLS-1$
		}
		return buf.toString();
	}

	/**
	 * Returns a type argument proposal for a given type binding. The proposal
	 * is the simple type name for unbounded types or the upper bound of
	 * wildcard types or the the bound of type variables with a single bound.
	 *
	 * @param binding the type argument binding
	 * @return a type argument proposal for <code>binding</code>
	 * @throws JavaModelException 
	 */
	private TypeArgumentProposal computeTypeProposal(ITypeBinding binding, ITypeParameter parameter) throws JavaModelException {
		// TODO merge type bounds from parameter and binding
		
		final String name= binding.getName();
		if (binding.isWildcardType()) {
			String contextName= name.replaceFirst("\\?", parameter.getElementName()); //$NON-NLS-1$
			if (binding.isUpperbound())
				// upper bound - the upper bound is the bound itself
				return new TypeArgumentProposal(binding.getBound().getName(), true, contextName);
			// lower bound - the upper bound is always Object
			return new TypeArgumentProposal("Object", true, contextName); //$NON-NLS-1$
		}

		if (binding.isTypeVariable()) {
			final ITypeBinding[] bounds= binding.getTypeBounds();
			if (bounds.length == 1)
				return new TypeArgumentProposal(bounds[0].getName(), true, name);
			return new TypeArgumentProposal(name, true, name);
		}

		// not a wildcard or type variable
		return new TypeArgumentProposal(name, false, name);
	}

	/**
	 * Computes one inheritance path from <code>superType</code> to
	 * <code>subType</code> or <code>null</code> if <code>subType</code>
	 * does not inherit from <code>superType</code>. Note that there may be
	 * more than one inheritance path - this method simply returns one.
	 * <p>
	 * The returned array contains <code>superType</code> at its first index,
	 * and <code>subType</code> at its last index. If <code>subType</code>
	 * equals <code>superType</code>, an array of length 1 is returned
	 * containing that type.
	 * </p>
	 *
	 * @param subType the sub type
	 * @param superType the super type
	 * @return an inheritance path from <code>superType</code> to
	 *         <code>subType</code>, or <code>null</code> if
	 *         <code>subType</code> does not inherit from
	 *         <code>superType</code>
	 * @throws JavaModelException
	 */
	private IType[] computeInheritancePath(IType subType, IType superType) throws JavaModelException {
		if (superType == null)
			return null;

		// optimization: avoid building the type hierarchy for the identity case
		if (superType.equals(subType))
			return new IType[] { subType };

		ITypeHierarchy hierarchy= subType.newSupertypeHierarchy(getProgressMonitor());
		if (!hierarchy.contains(superType))
			return null; // no path

		List path= new LinkedList();
		path.add(superType);
		do {
			// any sub type must be on a hierarchy chain from superType to subType
			superType= hierarchy.getSubtypes(superType)[0];
			path.add(superType);
		} while (!superType.equals(subType)); // since the equality case is handled above, we can spare one check

		return (IType[]) path.toArray(new IType[path.size()]);
	}

	private NullProgressMonitor getProgressMonitor() {
		return new NullProgressMonitor();
	}

	/**
	 * For the type parameter at <code>paramIndex</code> in the type at
	 * <code>path[pathIndex]</code>, this method computes the corresponding
	 * type parameter index in the type at <code>path[0]</code>. If the type
	 * parameter does not map to a type parameter of the super type,
	 * <code>-1</code> is returned.
	 *
	 * @param path the type inheritance path, a non-empty array of consecutive
	 *        sub types
	 * @param pathIndex an index into <code>path</code> specifying the type to
	 *        start with
	 * @param paramIndex the index of the type parameter to map -
	 *        <code>path[pathIndex]</code> must have a type parameter at that
	 *        index, lest an <code>ArrayIndexOutOfBoundsException</code> is
	 *        thrown
	 * @return the index of the type parameter in <code>path[0]</code>
	 *         corresponding to the type parameter at <code>paramIndex</code>
	 *         in <code>path[pathIndex]</code>, or -1 if there is no
	 *         corresponding type parameter
	 * @throws JavaModelException
	 * @throws ArrayIndexOutOfBoundsException if <code>path[pathIndex]</code>
	 *         has &lt;= <code>paramIndex</code> parameters
	 */
	private int mapTypeParameterIndex(IType[] path, int pathIndex, int paramIndex) throws JavaModelException, ArrayIndexOutOfBoundsException {
		if (pathIndex == 0)
			// break condition: we've reached the top of the hierarchy
			return paramIndex;

		IType subType= path[pathIndex];
		IType superType= path[pathIndex - 1];

		String superSignature= findMatchingSuperTypeSignature(subType, superType);
		ITypeParameter param= subType.getTypeParameters()[paramIndex];
		int index= findMatchingTypeArgumentIndex(superSignature, param.getElementName());
		if (index == -1) {
			// not mapped through
			return -1;
		}

		return mapTypeParameterIndex(path, pathIndex - 1, index);
	}

	/**
	 * Finds and returns the super type signature in the
	 * <code>extends</code> or <code>implements</code> clause of
	 * <code>subType</code> that corresponds to <code>superType</code>.
	 *
	 * @param subType a direct and true sub type of <code>superType</code>
	 * @param superType a direct super type (super class or interface) of
	 *        <code>subType</code>
	 * @return the super type signature of <code>subType</code> referring
	 *         to <code>superType</code>
	 * @throws JavaModelException if extracting the super type signatures
	 *         fails, or if <code>subType</code> contains no super type
	 *         signature to <code>superType</code>
	 */
	private String findMatchingSuperTypeSignature(IType subType, IType superType) throws JavaModelException {
		String[] signatures= getSuperTypeSignatures(subType, superType);
		for (int i= 0; i < signatures.length; i++) {
			String signature= signatures[i];
			String qualified= SignatureUtil.qualifySignature(signature, subType);
			String subFQN= SignatureUtil.stripSignatureToFQN(qualified);

			String superFQN= superType.getFullyQualifiedName();
			if (subFQN.equals(superFQN)) {
				return signature;
			}

			// TODO handle local types
		}

		throw new JavaModelException(new CoreException(new Status(IStatus.ERROR, JavaPlugin.getPluginId(), IStatus.OK, "Illegal hierarchy", null))); //$NON-NLS-1$
	}

	/**
	 * Finds and returns the index of the type argument named
	 * <code>argument</code> in the given super type signature.
	 * <p>
	 * If <code>signature</code> does not contain a corresponding type
	 * argument, or if <code>signature</code> has no type parameters (i.e. is
	 * a reference to a non-parameterized type or a raw type), -1 is returned.
	 * </p>
	 *
	 * @param signature the super type signature from a type's
	 *        <code>extends</code> or <code>implements</code> clause
	 * @param argument the name of the type argument to find
	 * @return the index of the given type argument, or -1 if there is none
	 */
	private int findMatchingTypeArgumentIndex(String signature, String argument) {
		String[] typeArguments= Signature.getTypeArguments(signature);
		for (int i= 0; i < typeArguments.length; i++) {
			if (Signature.getSignatureSimpleName(typeArguments[i]).equals(argument))
				return i;
		}
		return -1;
	}

	/**
	 * Returns the super interface signatures of <code>subType</code> if
	 * <code>superType</code> is an interface, otherwise returns the super
	 * type signature.
	 *
	 * @param subType the sub type signature
	 * @param superType the super type signature
	 * @return the super type signatures of <code>subType</code>
	 * @throws JavaModelException if any java model operation fails
	 */
	private String[] getSuperTypeSignatures(IType subType, IType superType) throws JavaModelException {
		if (superType.isInterface())
			return subType.getSuperInterfaceTypeSignatures();
		else
			return new String[] {subType.getSuperclassTypeSignature()};
	}

	/**
	 * Returns the type binding of the expected type as it is contained in the
	 * code completion context.
	 *
	 * @return the binding of the expected type
	 */
	private ITypeBinding getExpectedType() {
		char[][] chKeys= fContext.getExpectedTypesKeys();
		if (chKeys == null || chKeys.length == 0)
			return null;

		String[] keys= new String[chKeys.length];
		for (int i= 0; i < keys.length; i++) {
			keys[i]= String.valueOf(chKeys[0]);
		}

		final ASTParser parser= ASTParser.newParser(AST.JLS3);
		parser.setProject(fCompilationUnit.getJavaProject());
		parser.setResolveBindings(true);

		final Map bindings= new HashMap();
		ASTRequestor requestor= new ASTRequestor() {
			public void acceptBinding(String bindingKey, IBinding binding) {
				bindings.put(bindingKey, binding);
			}
		};
		parser.createASTs(new ICompilationUnit[0], keys, requestor, null);

		if (bindings.size() > 0)
			return (ITypeBinding) bindings.get(keys[0]);

		return null;
	}

	/**
	 * Returns the java mode type of this type proposal.
	 *
	 * @return the java mode type of this type proposal
	 * @throws JavaModelException
	 */
	private IType getProposedType() throws JavaModelException {
		if (fCompilationUnit != null) {
			String fullType= SignatureUtil.stripSignatureToFQN(String.valueOf(fProposal.getSignature()));
			return fCompilationUnit.getJavaProject().findType(fullType);
		}
		return null;
	}

	/**
	 * Returns <code>true</code> if type arguments should be appended when
	 * applying this proposal, <code>false</code> if not (for example if the
	 * document already contains a type argument list after the insertion point.
	 *
	 * @param document the document
	 * @param offset the insertion offset
	 * @return <code>true</code> if arguments should be appended
	 */
	private boolean shouldAppendArguments(IDocument document, int offset) {
		try {
			IRegion region= document.getLineInformationOfOffset(offset);
			String line= document.get(region.getOffset(), region.getLength());

			int index= offset - region.getOffset();
			while (index != line.length() && Character.isUnicodeIdentifierPart(line.charAt(index)))
				++index;

			if (index == line.length())
				return true;

			char ch= line.charAt(index);
			return ch != '<';

		} catch (BadLocationException e) {
			return true;
		}
	}

	private StringBuffer createParameterList(TypeArgumentProposal[] typeArguments, int[] offsets, int[] lengths) {
		StringBuffer buffer= new StringBuffer();
		buffer.append(getReplacementString());

		FormatterPrefs prefs= new FormatterPrefs(fCompilationUnit == null ? null : fCompilationUnit.getJavaProject());
		final char SPACE= ' ';
		final char LESS= '<';
		final char COMMA= ',';
		final char GREATER= '>';
		if (prefs.beforeOpeningBracket)
			buffer.append(SPACE);
		buffer.append(LESS);
		if (prefs.afterOpeningBracket)
			buffer.append(SPACE);
		StringBuffer separator= new StringBuffer(3);
		if (prefs.beforeComma)
			separator.append(SPACE);
		separator.append(COMMA);
		if (prefs.afterComma)
			separator.append(SPACE);

		for (int i= 0; i != typeArguments.length; i++) {
			if (i != 0)
				buffer.append(separator);

			offsets[i]= buffer.length();
			buffer.append(typeArguments[i]);
			lengths[i]= buffer.length() - offsets[i];
		}
		if (prefs.beforeClosingBracket)
			buffer.append(SPACE);
		buffer.append(GREATER);

		return buffer;
	}

	private void installLinkedMode(IDocument document, int[] offsets, int[] lengths, TypeArgumentProposal[] typeArgumentProposals) {
		int replacementOffset= getReplacementOffset();
		String replacementString= getReplacementString();

		try {
			LinkedModeModel model= new LinkedModeModel();
			for (int i= 0; i != offsets.length; i++) {
				if (typeArgumentProposals[i].isAmbiguous()) {
					LinkedPositionGroup group= new LinkedPositionGroup();
					group.addPosition(new LinkedPosition(document, replacementOffset + offsets[i], lengths[i], LinkedPositionGroup.NO_STOP));
					model.addGroup(group);
				}
			}

			model.forceInstall();
			JavaEditor editor= getJavaEditor();
			if (editor != null) {
				model.addLinkingListener(new EditorHighlightingSynchronizer(editor));
			}

			LinkedModeUI ui= new EditorLinkedModeUI(model, getTextViewer());
			ui.setExitPosition(getTextViewer(), replacementOffset + replacementString.length(), 0, Integer.MAX_VALUE);
			ui.setDoContextInfo(true);
			ui.enter();

			fSelectedRegion= ui.getSelectedRegion();

		} catch (BadLocationException e) {
			JavaPlugin.log(e);
			openErrorDialog(e);
		}
	}

	private boolean hasAmbiguousProposals(TypeArgumentProposal[] typeArgumentProposals) {
		boolean hasAmbiguousProposals= false;
		for (int i= 0; i < typeArgumentProposals.length; i++) {
			if (typeArgumentProposals[i].isAmbiguous()) {
				hasAmbiguousProposals= true;
				break;
			}
		}
		return hasAmbiguousProposals;
	}

	/**
	 * Returns the currently active java editor, or <code>null</code> if it
	 * cannot be determined.
	 *
	 * @return  the currently active java editor, or <code>null</code>
	 */
	private JavaEditor getJavaEditor() {
		IEditorPart part= JavaPlugin.getActivePage().getActiveEditor();
		if (part instanceof JavaEditor)
			return (JavaEditor) part;
		else
			return null;
	}

	/*
	 * @see ICompletionProposal#getSelection(IDocument)
	 */
	public Point getSelection(IDocument document) {
		if (fSelectedRegion == null)
			return super.getSelection(document);

		return new Point(fSelectedRegion.getOffset(), fSelectedRegion.getLength());
	}

	private void openErrorDialog(BadLocationException e) {
		Shell shell= getTextViewer().getTextWidget().getShell();
		MessageDialog.openError(shell, JavaTextMessages.ExperimentalProposal_error_msg, e.getMessage());
	}

	/*
	 * @see org.eclipse.jdt.internal.ui.text.java.LazyJavaCompletionProposal#computeContextInformation()
	 */
	protected IContextInformation computeContextInformation() {
		// only return information if we're already computed
		// -> avoids creating context information for invalid proposals
		if (fTypeArgumentProposals != null) {
			try {
				if (hasParameters()) {
					TypeArgumentProposal[] proposals= computeTypeArgumentProposals();
					if (hasAmbiguousProposals(proposals))
						return new ContextInformation(this);
				}
			} catch (JavaModelException e) {
			}
		}
		return super.computeContextInformation();
	}
	
	protected int computeCursorPosition() {
		if (fSelectedRegion != null)
			return fSelectedRegion.getOffset() - getReplacementOffset();
		return super.computeCursorPosition();
	}
	
	private boolean hasParameters() {
		try {
			IType type= getProposedType();
			if (type == null)
				return false;

			return type.getTypeParameters().length > 0;
		} catch (JavaModelException e) {
			return false;
		}
	}
}
