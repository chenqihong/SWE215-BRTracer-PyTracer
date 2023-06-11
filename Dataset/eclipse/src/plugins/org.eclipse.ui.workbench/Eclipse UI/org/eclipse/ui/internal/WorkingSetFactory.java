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
package org.eclipse.ui.internal;

import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.ui.IElementFactory;
import org.eclipse.ui.IMemento;

/**
 * A WorkingSetFactory is used to recreate a persisted WorkingSet 
 * object.
 */
public class WorkingSetFactory implements IElementFactory {

    /* (non-Javadoc)
     * @see org.eclipse.ui.IElementFactory
     */
    public IAdaptable createElement(IMemento memento) {
        String workingSetName = memento.getString(IWorkbenchConstants.TAG_NAME);
        String workingSetEditPageId = memento
                .getString(IWorkbenchConstants.TAG_EDIT_PAGE_ID);

        if (workingSetName == null)
            return null;

        WorkingSet workingSet = new WorkingSet(workingSetName, memento);
        if (workingSetEditPageId != null) {
            workingSet.setId(workingSetEditPageId);
        } else {
            // working sets created with builds 20020418 and 20020419 will not
            // have an edit page id. fix this automatically.
            workingSet.setId("org.eclipse.ui.resourceWorkingSetPage"); //$NON-NLS-1$
        }
        return workingSet;
    }
}