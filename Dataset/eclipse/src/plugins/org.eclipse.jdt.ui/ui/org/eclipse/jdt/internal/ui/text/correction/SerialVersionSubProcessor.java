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

import java.util.Collection;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.ParameterizedType;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.QualifiedType;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.Type;

import org.eclipse.jdt.ui.text.java.IInvocationContext;
import org.eclipse.jdt.ui.text.java.IProblemLocation;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;

/**
 * Subprocessor for serial version quickfix proposals.
 *
 * @since 3.1
 */
public final class SerialVersionSubProcessor {

	/**
	 * Determines the serial version quickfix proposals.
	 *
	 * @param context
	 *        the invocation context
	 * @param location
	 *        the problem location
	 * @param proposals
	 *        the proposal collection to extend
	 */
	public static final void getSerialVersionProposals(final IInvocationContext context, final IProblemLocation location, final Collection proposals) {

		Assert.isNotNull(context);
		Assert.isNotNull(location);
		Assert.isNotNull(proposals);

		final CompilationUnit root= context.getASTRoot();

		final ASTNode selection= location.getCoveredNode(root);
		if (selection != null) {
			Name name= null;
			if (selection instanceof SimpleType) {
				final SimpleType type= (SimpleType) selection;
				name= type.getName();
			} else if (selection instanceof ParameterizedType) {
				final ParameterizedType type= (ParameterizedType) selection;
				final Type raw= type.getType();
				if (raw instanceof SimpleType)
					name= ((SimpleType) raw).getName();
				else if (raw instanceof QualifiedType)
					name= ((QualifiedType) raw).getName();
			} else if (selection instanceof Name) {
				name= (Name) selection;
			}
			if (name != null) {
				final SimpleName simple= name.isSimpleName() ? (SimpleName) name : ((QualifiedName) name).getName();
				final ICompilationUnit unit= context.getCompilationUnit();
				if (JavaModelUtil.isEditable(unit)) {
					proposals.add(new SerialVersionDefaultProposal(unit, simple));
					proposals.add(new SerialVersionHashProposal(unit, simple));
				}
			}
		}
	}
}
