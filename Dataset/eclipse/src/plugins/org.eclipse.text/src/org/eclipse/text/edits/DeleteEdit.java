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
package org.eclipse.text.edits;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;

/**
 * Text edit to delete a range in a document.
 * <p>
 * A delete edit is equivalent to <code>ReplaceEdit(
 * offset, length, "")</code>.
 *
 * @since 3.0
 */
public final class DeleteEdit extends TextEdit {

	/**
	 * Constructs a new delete edit.
	 *
	 * @param offset the offset of the range to replace
	 * @param length the length of the range to replace
	 */
	public DeleteEdit(int offset, int length) {
		super(offset, length);
	}

	/*
	 * Copy constructor
	 */
	private DeleteEdit(DeleteEdit other) {
		super(other);
	}

	/* non Java-doc
	 * @see TextEdit#doCopy
	 */
	protected TextEdit doCopy() {
		return new DeleteEdit(this);
	}

	/* (non-Javadoc)
	 * @see TextEdit#accept0
	 */
	protected void accept0(TextEditVisitor visitor) {
		boolean visitChildren = visitor.visit(this);
		if (visitChildren) {
			acceptChildren(visitor);
		}
	}

	/* non Java-doc
	 * @see TextEdit#performDocumentUpdating
	 */
	/* package */ int performDocumentUpdating(IDocument document) throws BadLocationException {
		document.replace(getOffset(), getLength(), ""); //$NON-NLS-1$
		fDelta= -getLength();
		return fDelta;
	}

	/* non Java-doc
	 * @see TextEdit#deleteChildren
	 */
	/* package */ boolean deleteChildren() {
		return true;
	}
}
