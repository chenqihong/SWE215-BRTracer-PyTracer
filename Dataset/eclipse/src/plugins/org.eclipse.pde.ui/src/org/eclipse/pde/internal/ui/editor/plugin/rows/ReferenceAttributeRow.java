/*******************************************************************************
 * Copyright (c) 2003, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
/*
 * Created on Jan 30, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.eclipse.pde.internal.ui.editor.plugin.rows;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.core.ischema.ISchemaAttribute;
import org.eclipse.pde.internal.ui.*;
import org.eclipse.pde.internal.ui.editor.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.forms.events.*;
import org.eclipse.ui.forms.widgets.*;

public abstract class ReferenceAttributeRow extends TextAttributeRow {

	public ReferenceAttributeRow(IContextPart part, ISchemaAttribute att) {
		super(part, att);
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.pde.internal.ui.neweditor.plugin.ExtensionElementEditor#createContents(org.eclipse.swt.widgets.Composite,
	 *      org.eclipse.ui.forms.widgets.FormToolkit, int)
	 */
	protected void createLabel(Composite parent, FormToolkit toolkit) {
		Hyperlink link = toolkit.createHyperlink(parent, getPropertyLabel(),
				SWT.NULL);
		link.addHyperlinkListener(new HyperlinkAdapter() {
			public void linkActivated(HyperlinkEvent e) {
				if (!isReferenceModel())
					openReference();
			}
		});
		link.setToolTipText(getToolTipText(link));
	}
	protected boolean isReferenceModel() {
		return ((IPluginModelBase) part.getPage().getModel())
				.getUnderlyingResource() != null;
	}
	public void createContents(Composite parent, FormToolkit toolkit, int span) {
		super.createContents(parent, toolkit, span);
		Button button = toolkit.createButton(parent, PDEUIMessages.ReferenceAttributeRow_browse, SWT.PUSH); //$NON-NLS-1$
		button.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				if (!isReferenceModel())
					browse();
			}
		});
		button.setEnabled(part.isEditable());
	}
	protected GridData createGridData(int span) {
		GridData gd = new GridData(GridData.FILL_HORIZONTAL);
		gd.widthHint = 20;
		return gd;
	}
	protected abstract void openReference();
	protected abstract void browse();
}
