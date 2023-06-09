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
package org.eclipse.pde.internal.ui.parts;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.forms.widgets.FormToolkit;
/**
 * @version 1.0
 * @author
 */
public abstract class StructuredViewerPart extends SharedPartWithButtons {
	private StructuredViewer viewer;
	private Point minSize = null;
	public StructuredViewerPart(String[] buttonLabels) {
		super(buttonLabels);
	}
	public StructuredViewer getViewer() {
		return viewer;
	}
	public Control getControl() {
		return viewer.getControl();
	}
	/*
	 * @see SharedPartWithButtons#createMainControl(Composite, int,
	 *      FormWidgetFactory)
	 */
	protected void createMainControl(Composite parent, int style, int span,
			FormToolkit toolkit) {
		viewer = createStructuredViewer(parent, style, toolkit);
		Control control = viewer.getControl();
		/*
		if (toolkit != null) {
			toolkit.hookDeleteListener(control);
		}
		*/
		GridData gd = new GridData(GridData.FILL_BOTH);
		gd.horizontalSpan = span;
		control.setLayoutData(gd);
		applyMinimumSize();
	}
	public void setMinimumSize(int width, int height) {
		minSize = new Point(width, height);
		if (viewer != null)
			applyMinimumSize();
	}
	private void applyMinimumSize() {
		if (minSize != null) {
			GridData gd = (GridData) viewer.getControl().getLayoutData();
			gd.widthHint = minSize.x;
			gd.heightHint = minSize.y;
		}
	}
	protected void updateEnabledState() {
		getControl().setEnabled(isEnabled());
		super.updateEnabledState();
	}
	protected abstract StructuredViewer createStructuredViewer(
			Composite parent, int style, FormToolkit toolkit);
}
