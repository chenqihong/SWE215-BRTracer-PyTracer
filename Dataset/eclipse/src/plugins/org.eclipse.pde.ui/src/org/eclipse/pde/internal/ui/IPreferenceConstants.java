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
package org.eclipse.pde.internal.ui;

public interface IPreferenceConstants {
	
	// editor preference page
	public static final String P_USE_SOURCE_PAGE = "useSourcePage"; //$NON-NLS-1$

	// Main preference page
	public static final String PROP_SHOW_OBJECTS =
		"Preferences.MainPage.showObjects"; //$NON-NLS-1$
	public static final String VALUE_USE_IDS = "useIds"; //$NON-NLS-1$
	public static final String VALUE_USE_NAMES = "useNames"; //$NON-NLS-1$
	
	// Editor Outline
	public static final String PROP_OUTLINE_SORTING = "PDEMultiPageContentOutline.SortingAction.isChecked"; //$NON-NLS-1$

	// Dependencies view
	public static final String DEPS_VIEW_SHOW_CALLERS = "DependenciesView.show.callers"; //$NON-NLS-1$
	public static final String DEPS_VIEW_SHOW_LIST = "DependenciesView.show.list"; //$NON-NLS-1$

}
