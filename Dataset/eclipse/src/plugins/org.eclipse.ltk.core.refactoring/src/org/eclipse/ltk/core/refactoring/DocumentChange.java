/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ltk.core.refactoring;

import org.eclipse.text.edits.UndoEdit;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.jface.text.IDocument;

import org.eclipse.ltk.internal.core.refactoring.Assert;
import org.eclipse.ltk.internal.core.refactoring.TextChanges;
import org.eclipse.ltk.internal.core.refactoring.UndoDocumentChange;

/**
 * A text change that operates directly on instances of {@link IDocument}.
 * The document change uses a simple length compare to check if it
 * is still valid. So as long as its length hasn't changed the text edits
 * managed have a valid range and can be applied to the document. The
 * same applies to the undo change returned from the perform method.
 * 
 * <p> 
 * Note: this class is not intended to be extended by clients.
 * </p>
 * 
 * @since 3.0
 */
public class DocumentChange extends TextChange {

	private IDocument fDocument;
	private int fLength;
	
	/**
	 * Creates a new <code>DocumentChange</code> for the given 
	 * {@link IDocument}.
	 * 
	 * @param name the change's name. Has to be a human readable name.
	 * @param document the document this change is working on
	 */
	public DocumentChange(String name, IDocument document) {
		super(name);
		Assert.isNotNull(document);
		fDocument= document;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public Object getModifiedElement(){
		return fDocument;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void initializeValidationData(IProgressMonitor pm) {
		// as long as we don't have modification stamps on documents
		// we can only remember its length.
		fLength= fDocument.getLength();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public RefactoringStatus isValid(IProgressMonitor pm) throws CoreException {
		pm.beginTask("", 1); //$NON-NLS-1$
		RefactoringStatus result= TextChanges.isValid(fDocument, fLength);
		pm.worked(1);
		return result;
	}

	/**
	 * {@inheritDoc}
	 */
	protected IDocument acquireDocument(IProgressMonitor pm) throws CoreException {
		return fDocument;
	}
	
	/**
	 * {@inheritDoc}
	 */
	protected void commit(IDocument document, IProgressMonitor pm) throws CoreException {
		// do nothing
	}
	
	/**
	 * {@inheritDoc}
	 */
	protected void releaseDocument(IDocument document, IProgressMonitor pm) throws CoreException {
		//do nothing
	}
	
	/**
	 * {@inheritDoc}
	 */
	protected Change createUndoChange(UndoEdit edit) {
		return new UndoDocumentChange(getName(), fDocument, edit);
	}	
}

