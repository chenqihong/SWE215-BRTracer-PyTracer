/**********************************************************************
 * Copyright (c) 2003, 2005 IBM Corporation and others. All rights reserved.   This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.debug.internal.ui.sourcelookup;

import org.eclipse.osgi.util.NLS;

public class SourceLookupUIMessages extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.debug.internal.ui.sourcelookup.SourceLookupUIMessages";//$NON-NLS-1$

	// Source search launch configuration tab/errors/dialogs
	public static String EditContainerAction_0;
	public static String sourceTab_lookupLabel;
	public static String sourceTab_searchDuplicateLabel;
	public static String sourceTab_upButton;
	public static String sourceTab_downButton;
	public static String sourceTab_removeButton;
	public static String sourceTab_addButton;
	public static String sourceTab_tabTitle;
	public static String sourceTab_defaultButton;

	public static String addSourceLocation_title;
	public static String addSourceLocation_description;

	public static String addSourceLocation_addButton2;
	public static String addSourceLocation_editorMessage;

	public static String sourceSearch_folderSelectionError;
	public static String sourceSearch_initError;

	public static String projectSelection_chooseLabel;
	public static String projectSelection_requiredLabel;

	public static String folderSelection_title;
	public static String folderSelection_label;

	public static String manageSourceDialog_title;
	public static String manageSourceDialog_description;

	public static String sourceLookupPanel_1;
	public static String sourceLookupPanel_2;
	public static String ResolveDuplicatesHandler_0;
	public static String ResolveDuplicatesHandler_1;
	public static String EditSourceLookupPathAction_0;
	public static String LookupSourceAction_0;
	public static String ExternalArchiveSourceContainerBrowser_2;
	public static String ArchiveSourceContainerBrowser_3;
	public static String ArchiveSourceContainerBrowser_4;
	public static String DirectorySourceContainerDialog_0;
	public static String DirectorySourceContainerDialog_1;

	static {
		// load message values from bundle file
		NLS.initializeMessages(BUNDLE_NAME, SourceLookupUIMessages.class);
	}
}