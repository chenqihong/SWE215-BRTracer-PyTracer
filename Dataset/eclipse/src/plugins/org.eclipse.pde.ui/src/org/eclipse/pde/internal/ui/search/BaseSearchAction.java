/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.search;

import org.eclipse.jface.action.*;
import org.eclipse.search.ui.*;


public abstract class BaseSearchAction extends Action {

	public BaseSearchAction(String text) {
		setText(text);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.action.Action#run()
	 */
	public void run() {
		NewSearchUI.activateSearchResultView();
		NewSearchUI.runQueryInBackground(createSearchQuery());
	}
	
	protected abstract ISearchQuery createSearchQuery();

}
