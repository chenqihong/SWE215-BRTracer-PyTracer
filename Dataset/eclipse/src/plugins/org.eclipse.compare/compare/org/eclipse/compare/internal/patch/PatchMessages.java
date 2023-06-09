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
package org.eclipse.compare.internal.patch;

import org.eclipse.osgi.util.NLS;

public final class PatchMessages extends NLS {

	private static final String BUNDLE_NAME = "org.eclipse.compare.internal.patch.PatchMessages";//$NON-NLS-1$

	private PatchMessages() {
		// Do not instantiate
	}

	public static String PatchAction_ExceptionTitle;
	public static String PatchAction_Exception;
	public static String PatchAction_SavingDirtyEditorsTask;
	public static String PatchAction_AlwaysSaveQuestion;
	public static String PatchAction_SaveAllQuestion;
	public static String PatchAction_SaveAllDescription;
	public static String PatchWizard_title;
	public static String PatchWizard_unexpectedException_message;
	public static String InputPatchPage_title;
	public static String InputPatchPage_message;
	public static String InputPatchPage_Clipboard;
	public static String InputPatchPage_SelectInput;
	public static String InputPatchPage_PatchErrorDialog_title;
	public static String InputPatchPage_SelectPatch_title;
	public static String InputPatchPage_FileButton_text;
	public static String InputPatchPage_ChooseFileButton_text;
	public static String InputPatchPage_UseClipboardButton_text;
	public static String InputPatchPage_NothingSelected_message;
	public static String InputPatchPage_ClipboardIsEmpty_message;
	public static String InputPatchPage_NoTextInClipboard_message;
	public static String InputPatchPage_CouldNotReadClipboard_message;
	public static String InputPatchPage_CannotLocatePatch_message;
	public static String InputPatchPage_NoFileName_message;
	public static String InputPatchPage_SelectPatchFileDialog_title;
	public static String InputPatchPage_PatchFileNotFound_message;
	public static String InputPatchPage_ParseError_message;
	public static String InputPatchPage_Clipboard_title;
	public static String InputPatchPage_PatchFile_title;
	public static String InputPatchPage_NoDiffsFound_format;
	public static String InputPatchPage_SingleFileError_format;
	public static String PreviewPatchPage_title;
	public static String PreviewPatchPage_message;
	public static String PreviewPatchPage_Left_title;
	public static String PreviewPatchPage_Right_title;
	public static String PreviewPatchPage_PatchOptions_title;
	public static String PreviewPatchPage_IgnoreSegments_text;
	public static String PreviewPatchPage_ReversePatch_text;
	public static String PreviewPatchPage_FuzzFactor_text;
	public static String PreviewPatchPage_FuzzFactor_tooltip;
	public static String PreviewPatchPage_IgnoreWhitespace_text;
	public static String PreviewPatchPage_NoName_text;
	public static String PreviewPatchPage_FileExists_error;
	public static String PreviewPatchPage_FileDoesNotExist_error;
	public static String PreviewPatchPage_NoMatch_error;
	public static String PreviewPatchPage_FileIsReadOnly_error;
	public static String PreviewPatchPage_GuessFuzz_text;
	public static String PreviewPatchPage_GuessFuzzProgress_text;
	public static String PreviewPatchPage_GuessFuzzProgress_format;
	public static String Patcher_Marker_message;
	public static String Patcher_Task_message;

	static {
		NLS.initializeMessages(BUNDLE_NAME, PatchMessages.class);
	}
}