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
package org.eclipse.jdt.internal.debug.ui.launcher;

 
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.internal.debug.ui.IJavaDebugHelpContextIds;
import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jface.util.Assert;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.TwoPaneElementSelector;

/**
 * A dialog to select a type from a list of types. The dialog allows
 * multiple selections.
 * 
 * @since 2.1
 */
public class MainTypeSelectionDialog extends TwoPaneElementSelector {

	/** The main types. */
	private final IType[] fTypes;
	
	private static class PackageRenderer extends JavaElementLabelProvider {
		public PackageRenderer() {
			super(JavaElementLabelProvider.SHOW_PARAMETERS | JavaElementLabelProvider.SHOW_POST_QUALIFIED | JavaElementLabelProvider.SHOW_ROOT);	
		}

		public Image getImage(Object element) {
			return super.getImage(((IType)element).getPackageFragment());
		}
		
		public String getText(Object element) {
			return super.getText(((IType)element).getPackageFragment());
		}
	}

	public MainTypeSelectionDialog(Shell shell, IType[] types) {

		super(shell, new JavaElementLabelProvider(JavaElementLabelProvider.SHOW_BASICS | JavaElementLabelProvider.SHOW_OVERLAY_ICONS), 
			new PackageRenderer());

		Assert.isNotNull(types);
		fTypes= types;
		setMessage(LauncherMessages.MainTypeSelectionDialog_Choose_a_type); //$NON-NLS-1$		
		setUpperListLabel(LauncherMessages.MainTypeSelectionDialog_Matching_types); //$NON-NLS-1$
		setLowerListLabel(LauncherMessages.MainTypeSelectionDialog_Qualifier); //$NON-NLS-1$
	}

	/**
	 * Returns the main types.
	 */
	public IType[] getTypes() {
		return fTypes;
	}
	
	/*
	 * @see Windows#configureShell
	 */
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(newShell, IJavaDebugHelpContextIds.MAIN_TYPE_SELECTION_DIALOG);
	}

	/*
	 * @see Window#open()
	 */
	public int open() {

		if (fTypes == null)
			return CANCEL;
		
		setElements(fTypes);
		return super.open();
	}
	
	/**
	 * @see org.eclipse.jface.dialogs.Dialog#createDialogArea(org.eclipse.swt.widgets.Composite)
	 */
	public Control createDialogArea(Composite parent) {
		Control control= super.createDialogArea(parent);
		applyDialogFont(control);
		return control;
	}
}
