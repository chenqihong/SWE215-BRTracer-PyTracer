/*******************************************************************************
 * Copyright (c) 2003, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui;

/**
 * This interface defines an editor input for in-place editors.
 * <p>
 * Clients implementing this editor input interface should override
 * <code>Object.equals(Object)</code> to answer true for two inputs
 * that are the same. The <code>IWorkbenchPage.openEditor</code> APIs
 * are dependent on this to find an editor with the same input.
 * </p><p>
 * Path-oriented editors should support this as a valid input type, and
 * can allow full read-write editing of its content.
 * </p><p>
 * All editor inputs must implement the <code>IAdaptable</code> interface;
 * extensions are managed by the platform's adapter manager.
 * </p>
 *
 * @see org.eclipse.ui.IInPlaceEditor
 * @since 3.0
 */
public interface IInPlaceEditorInput extends IPathEditorInput {
    /**
     * Sets the in-place editor this input is associated with.
     * 
     * @param editor the in-place editor associated with this input
     * 		or <code>null</code> to disassociate.
     */
    public void setInPlaceEditor(IInPlaceEditor editor);
}
