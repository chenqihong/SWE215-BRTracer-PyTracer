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
package org.eclipse.jdt.internal.corext.refactoring.base;

import org.eclipse.jdt.core.compiler.IProblem;

import org.eclipse.jdt.internal.corext.SourceRange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.RefactoringStatusContext;
import org.eclipse.ltk.core.refactoring.RefactoringStatusEntry;

/**
 * Helper method to code Java refactorings
 */
public class JavaRefactorings {

	public static RefactoringStatusEntry createStatusEntry(IProblem problem, String newWcSource) {
		RefactoringStatusContext context= new JavaStringStatusContext(newWcSource, new SourceRange(problem));
		int severity= problem.isError() ? RefactoringStatus.ERROR: RefactoringStatus.WARNING;
		return new RefactoringStatusEntry(severity, problem.getMessage(), context);
	}
}
