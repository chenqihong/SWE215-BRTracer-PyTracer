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
package org.eclipse.jdt.internal.ui.javaeditor;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.BadPositionCategoryException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.ISynchronizable;
import org.eclipse.jface.text.Position;


/**
 * Document that can also be used by a background reconciler.
 */
public class PartiallySynchronizedDocument extends Document implements ISynchronizable {
    
    private final Object fInternalLockObject= new Object();
    private Object fLockObject;
    
    /*
     * @see org.eclipse.jface.text.ISynchronizable#setLockObject(java.lang.Object)
     */
    public void setLockObject(Object lockObject) {
        fLockObject= lockObject;
    }

    /*
     * @see org.eclipse.jface.text.ISynchronizable#getLockObject()
     */
    public Object getLockObject() {
        return fLockObject == null ? fInternalLockObject : fLockObject;
    }
	
	/*
	 * @see IDocumentExtension#startSequentialRewrite(boolean)
	 */
	public void startSequentialRewrite(boolean normalized) {
	    synchronized (getLockObject()) {
	        super.startSequentialRewrite(normalized);
	    }
	}

	/*
	 * @see IDocumentExtension#stopSequentialRewrite()
	 */
	public void stopSequentialRewrite() {
		synchronized (getLockObject()) {
            super.stopSequentialRewrite();
        }
    }
	
	/*
	 * @see IDocument#get()
	 */
	public String get() {
		synchronized (getLockObject()) {
            return super.get();
        }
    }
	
	/*
	 * @see IDocument#get(int, int)
	 */
	public String get(int offset, int length) throws BadLocationException {
		synchronized (getLockObject()) {
            return super.get(offset, length);
        }
	}
	
	/*
	 * @see IDocument#getChar(int)
	 */
	public char getChar(int offset) throws BadLocationException {
		synchronized (getLockObject()) {
            return super.getChar(offset);
        }
	}
	
	/*
	 * @see org.eclipse.jface.text.IDocumentExtension4#getModificationStamp()
	 * @since 3.1
	 */
	public long getModificationStamp() {
		synchronized (getLockObject()) {
			return super.getModificationStamp();
		}
	}
	
	/*
	 * @see IDocument#replace(int, int, String)
	 */
	public void replace(int offset, int length, String text) throws BadLocationException {
		synchronized (getLockObject()) {
            super.replace(offset, length, text);
        }
	}
	
	/*
	 * @see IDocumentExtension4#replace(int, int, String, long)
	 */
	public void replace(int offset, int length, String text, long modificationStamp) throws BadLocationException {
		synchronized (getLockObject()) {
            super.replace(offset, length, text, modificationStamp);
        }
	}
	
	/*
	 * @see IDocument#set(String)
	 */
	public void set(String text) {
		synchronized (getLockObject()) {
            super.set(text);
        }
	}
	
	/*
	 * @see IDocumentExtension4#set(String, long)
	 */
	public void set(String text, long modificationStamp) {
		synchronized (getLockObject()) {
            super.set(text, modificationStamp);
        }
	}
	
	/*
	 * @see org.eclipse.jface.text.AbstractDocument#addPosition(java.lang.String, org.eclipse.jface.text.Position)
	 */
	public void addPosition(String category, Position position) throws BadLocationException, BadPositionCategoryException {
		synchronized (getLockObject()) {
            super.addPosition(category, position);
        }
	}
	
	/*
	 * @see org.eclipse.jface.text.AbstractDocument#removePosition(java.lang.String, org.eclipse.jface.text.Position)
	 */
	public void removePosition(String category, Position position) throws BadPositionCategoryException {
		synchronized (getLockObject()) {
            super.removePosition(category, position);
        }
	}
	
	/*
	 * @see org.eclipse.jface.text.AbstractDocument#getPositions(java.lang.String)
	 */
	public Position[] getPositions(String category) throws BadPositionCategoryException {
		synchronized (getLockObject()) {
            return super.getPositions(category);
        }
	}
}
