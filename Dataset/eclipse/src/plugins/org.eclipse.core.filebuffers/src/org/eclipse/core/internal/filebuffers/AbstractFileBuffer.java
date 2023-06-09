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
package org.eclipse.core.internal.filebuffers;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;

import org.eclipse.core.filebuffers.FileBuffers;
import org.eclipse.core.filebuffers.IFileBuffer;
import org.eclipse.core.filebuffers.IStateValidationSupport;
import org.eclipse.core.filebuffers.ITextFileBufferManager;

/**
 * @since 3.0
 */
public abstract class AbstractFileBuffer implements IFileBuffer, IStateValidationSupport {


	public abstract void create(IPath location, IProgressMonitor monitor) throws CoreException;

	public abstract void connect();

	public abstract void disconnect() throws CoreException;

	/**
	 * Returns whether this file buffer has been disconnected.
	 *
	 * @return <code>true</code> if already disposed, <code>false</code> otherwise
	 * @since 3.1
	 */
	protected abstract boolean isDisconnected();

	/**
	 * Disposes this file buffer.
	 * This implementation is always empty.
	 * <p>
	 * Subclasses may extend but must call <code>super.dispose()</code>.
	 * <p>
	 *
	 * @since 3.1
	 */
	protected void dispose() {
	}

	/*
	 * @see org.eclipse.core.filebuffers.IStateValidationSupport#validationStateAboutToBeChanged()
	 */
	public void validationStateAboutToBeChanged() {
		ITextFileBufferManager fileBufferManager= FileBuffers.getTextFileBufferManager();
		if (fileBufferManager instanceof TextFileBufferManager) {
			TextFileBufferManager manager= (TextFileBufferManager) fileBufferManager;
			manager.fireStateChanging(this);
		}
	}

	/*
	 * @see org.eclipse.core.filebuffers.IStateValidationSupport#validationStateChangeFailed()
	 */
	public void validationStateChangeFailed() {
		ITextFileBufferManager fileBufferManager= FileBuffers.getTextFileBufferManager();
		if (fileBufferManager instanceof TextFileBufferManager) {
			TextFileBufferManager manager= (TextFileBufferManager) fileBufferManager;
			manager.fireStateChangeFailed(this);
		}
	}
}
