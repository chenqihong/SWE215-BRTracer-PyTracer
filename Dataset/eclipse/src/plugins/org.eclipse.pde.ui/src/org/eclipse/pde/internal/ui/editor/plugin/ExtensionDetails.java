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
package org.eclipse.pde.internal.ui.editor.plugin;

import org.eclipse.core.runtime.*;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.viewers.*;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.core.IModelChangedEvent;
import org.eclipse.pde.core.plugin.*;
import org.eclipse.pde.internal.core.*;
import org.eclipse.pde.internal.ui.*;
import org.eclipse.pde.internal.ui.editor.*;
import org.eclipse.pde.internal.ui.parts.*;
import org.eclipse.pde.internal.ui.search.*;
import org.eclipse.swt.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.forms.*;
import org.eclipse.ui.forms.events.*;
import org.eclipse.ui.forms.widgets.*;

public class ExtensionDetails extends PDEDetails {
	private IPluginExtension input;
	private FormEntry id;
	private FormEntry name;
	private FormEntry point;
	private FormText rtext;

	private static final String RTEXT_DATA =
		PDEUIMessages.ExtensionDetails_extensionPointLinks; //$NON-NLS-1$
	/**
	 * 
	 */
	public ExtensionDetails() {
	}
	/* (non-Javadoc)
	 * @see org.eclipse.ui.forms.IDetailsPage#createContents(org.eclipse.swt.widgets.Composite)
	 */
	public void createContents(Composite parent) {
		TableWrapLayout layout = new TableWrapLayout();
		FormToolkit toolkit = getManagedForm().getToolkit();
		boolean paintedBorder = toolkit.getBorderStyle()!=SWT.BORDER;
		layout.topMargin = 0;
		layout.leftMargin = 5;
		layout.rightMargin = 0;
		layout.bottomMargin = 0;
		parent.setLayout(layout);


		Section section = toolkit.createSection(parent, Section.TITLE_BAR|Section.DESCRIPTION);
		section.clientVerticalSpacing = PDESection.CLIENT_VSPACING;
		section.marginHeight = 5;		
		section.marginWidth = 5;
		section.setText(PDEUIMessages.ExtensionDetails_title); //$NON-NLS-1$
		section.setDescription(PDEUIMessages.ExtensionDetails_desc); //$NON-NLS-1$
		TableWrapData td = new TableWrapData(TableWrapData.FILL, TableWrapData.TOP);
		td.grabHorizontal = true;
		section.setLayoutData(td);
		//toolkit.createCompositeSeparator(section);
		Composite client = toolkit.createComposite(section);
		GridLayout glayout = new GridLayout();
		glayout.marginWidth = glayout.marginHeight = 2;//paintedBorder?2:0;
		glayout.numColumns = 2;
		if (paintedBorder) glayout.verticalSpacing = 7;
		client.setLayout(glayout);
		
		GridData gd = new GridData();
		gd.horizontalSpan = 2;

		id = new FormEntry(client, toolkit, PDEUIMessages.ExtensionDetails_id, null, false); //$NON-NLS-1$
		id.setFormEntryListener(new FormEntryAdapter(this) {
			public void textValueChanged(FormEntry entry) {
				if (input!=null)
					try {
						input.setId(id.getValue());
					} catch (CoreException e) {
						PDEPlugin.logException(e);
					}
			}
		});
		
		name = new FormEntry(client, toolkit, PDEUIMessages.ExtensionDetails_name, null, false); //$NON-NLS-1$
		name.setFormEntryListener(new FormEntryAdapter(this) {
			public void textValueChanged(FormEntry entry) {
				if (input!=null)
					try {
						input.setName(name.getValue());
					} catch (CoreException e) {
						PDEPlugin.logException(e);
					}
			}
		});
		
		point = new FormEntry(client, toolkit, PDEUIMessages.ExtensionDetails_point, null, false); //$NON-NLS-1$
		point.setFormEntryListener(new FormEntryAdapter(this) {
			public void textValueChanged(FormEntry entry) {
				if (input!=null)
					try {
						input.setPoint(point.getValue());
					} catch (CoreException e) {
						PDEPlugin.logException(e);
					}
			}
		});
		
		createSpacer(toolkit, client, 2);
		
		rtext = toolkit.createFormText(parent, true);
		td = new TableWrapData(TableWrapData.FILL, TableWrapData.TOP);
		td.grabHorizontal = true;
		td.indent = 10;
		rtext.setLayoutData(td);
		rtext.setImage("desc", PDEPlugin.getDefault().getLabelProvider().get(PDEPluginImages.DESC_DOC_SECTION_OBJ)); //$NON-NLS-1$
		rtext.setImage("search", PDEPlugin.getDefault().getLabelProvider().get(PDEPluginImages.DESC_PSEARCH_OBJ));		 //$NON-NLS-1$
		rtext.addHyperlinkListener(new HyperlinkAdapter() {
			public void linkActivated(HyperlinkEvent e) {
				if (e.getHref().equals("search")){ //$NON-NLS-1$
					FindDeclarationsAction findDeclarationsAction = new FindDeclarationsAction(input);
					findDeclarationsAction.run();
				} else {
					if (input == null || input.getPoint() == null)
						return;
					IPluginExtensionPoint point = PDECore.getDefault().findExtensionPoint(input.getPoint());
					if (point != null){
						ShowDescriptionAction showDescAction = new ShowDescriptionAction(point);
						showDescAction.run();
					} else {
						showNoExtensionPointMessage();
					}
				}
			}
		});
		rtext.setText(RTEXT_DATA, true, false);
		id.setEditable(isEditable());
		name.setEditable(isEditable());
		point.setEditable(isEditable());
		
		toolkit.paintBordersFor(client);
		section.setClient(client);
		IPluginModelBase model = (IPluginModelBase)getPage().getModel();
		model.addModelChangedListener(this);
		markDetailsPart(section);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.forms.IDetailsPage#inputChanged(org.eclipse.jface.viewers.IStructuredSelection)
	 */
	public void selectionChanged(IFormPart part, ISelection selection) {
		IStructuredSelection ssel = (IStructuredSelection)selection;
		if (ssel.size()==1) {
			input = (IPluginExtension)ssel.getFirstElement();
		}
		else
			input = null;
		update();
	}
	
	private void update() {
		id.setValue(input!=null?input.getId():null, true);
		name.setValue(input!=null?input.getName():null, true);
		point.setValue(input!=null?input.getPoint():null, true);
	}
	public void cancelEdit() {
		id.cancelEdit();
		name.cancelEdit();
		point.cancelEdit();
		super.cancelEdit();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.ui.forms.IDetailsPage#commit()
	 */
	public void commit(boolean onSave) {
		id.commit();
		name.commit();
		point.commit();
		super.commit(onSave);
	}
	/* (non-Javadoc)
	 * @see org.eclipse.ui.forms.IDetailsPage#setFocus()
	 */
	public void setFocus() {
		id.getText().setFocus();
	}
	
	public void dispose() {
		IPluginModelBase model = (IPluginModelBase)getPage().getModel();
		if (model!=null)
			model.removeModelChangedListener(this);
		super.dispose();
	}

	public void modelChanged(IModelChangedEvent e) {
		if (e.getChangeType()==IModelChangedEvent.CHANGE) {
			Object obj = e.getChangedObjects()[0];
			if (obj.equals(input))
				refresh();
		}
	}
	/* (non-Javadoc)
	 * @see org.eclipse.ui.forms.IDetailsPage#refresh()
	 */
	public void refresh() {
		update();
		super.refresh();
	}
	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.neweditor.IContextPart#fireSaveNeeded()
	 */
	public void fireSaveNeeded() {
		markDirty();
		PDEFormPage page = (PDEFormPage)getManagedForm().getContainer();
		page.getPDEEditor().fireSaveNeeded(getContextId(), false);
	}
	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.neweditor.IContextPart#getContextId()
	 */
	public String getContextId() {
		return PluginInputContext.CONTEXT_ID;
	}
	public PDEFormPage getPage() {
		return (PDEFormPage)getManagedForm().getContainer();
	}
	public boolean isEditable() {
		return getPage().getPDEEditor().getAggregateModel().isEditable();
	}
	private void showNoExtensionPointMessage() {
		String title = PDEUIMessages.ExtensionDetails_noPoint_title; //$NON-NLS-1$
		String message = NLS.bind(PDEUIMessages.ShowDescriptionAction_noPoint_desc, input.getPoint()); //$NON-NLS-1$
		
		MessageDialog.openWarning(PDEPlugin.getActiveWorkbenchShell(), title, message);
	}
}
