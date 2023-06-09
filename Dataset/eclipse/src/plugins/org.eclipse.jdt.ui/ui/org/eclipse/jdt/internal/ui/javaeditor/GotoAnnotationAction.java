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

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.TextEditorAction;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;


public class GotoAnnotationAction extends TextEditorAction {

	private boolean fForward;

	public GotoAnnotationAction(String prefix, boolean forward) {
		super(JavaEditorMessages.getBundleForConstructedKeys(), prefix, null);
		fForward= forward;
		if (forward)
			PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.GOTO_NEXT_ERROR_ACTION);
		else
			PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJavaHelpContextIds.GOTO_PREVIOUS_ERROR_ACTION);
	}

	public void run() {
		JavaEditor e= (JavaEditor) getTextEditor();
		e.gotoAnnotation(fForward);
	}

	public void setEditor(ITextEditor editor) {
		if (editor instanceof JavaEditor)
			super.setEditor(editor);
		update();
	}

	public void update() {
		setEnabled(getTextEditor() instanceof JavaEditor);
	}
}
