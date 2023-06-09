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
package org.eclipse.compare.internal;

import org.eclipse.osgi.util.NLS;

public final class CompareMessages extends NLS {

	private static final String BUNDLE_NAME = "org.eclipse.compare.internal.CompareMessages";//$NON-NLS-1$

	private CompareMessages() {
		// Do not instantiate
	}

	public static String ComparePlugin_internal_error;
	public static String ExceptionDialog_seeErrorLogMessage;
	public static String CompareViewerSwitchingPane_Titleformat;
	public static String StructureDiffViewer_NoStructuralDifferences;
	public static String StructureDiffViewer_StructureError;
	public static String TextMergeViewer_cursorPosition_format;
	public static String TextMergeViewer_beforeLine_format;
	public static String TextMergeViewer_range_format;
	public static String TextMergeViewer_changeType_addition;
	public static String TextMergeViewer_changeType_deletion;
	public static String TextMergeViewer_changeType_change;
	public static String TextMergeViewer_direction_outgoing;
	public static String TextMergeViewer_direction_incoming;
	public static String TextMergeViewer_direction_conflicting;
	public static String TextMergeViewer_diffType_format;
	public static String TextMergeViewer_diffDescription_noDiff_format;
	public static String TextMergeViewer_diffDescription_diff_format;
	public static String TextMergeViewer_statusLine_format;
	public static String TextMergeViewer_atEnd_title;
	public static String TextMergeViewer_atEnd_message;
	public static String TextMergeViewer_atBeginning_title;
	public static String TextMergeViewer_atBeginning_message;
	public static String CompareNavigator_atEnd_title;
	public static String CompareNavigator_atEnd_message;
	public static String CompareNavigator_atBeginning_title;
	public static String CompareNavigator_atBeginning_message;

	static {
		NLS.initializeMessages(BUNDLE_NAME, CompareMessages.class);
	}
}