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

package org.eclipse.ui.views.bookmarkexplorer;

import org.eclipse.ui.actions.SelectionProviderAction;

/**
 * An abstract class for all bookmark view actions.
 */
abstract class BookmarkAction extends SelectionProviderAction {
    private BookmarkNavigator view;

    /**
     * Creates a bookmark action.
     */
    protected BookmarkAction(BookmarkNavigator view, String label) {
        super(view.getViewer(), label);
        this.view = view;
    }

    /**
     * Returns the bookmarks view.
     */
    public BookmarkNavigator getView() {
        return view;
    }
}
