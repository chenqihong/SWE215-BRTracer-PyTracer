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
package org.eclipse.pde.internal.ui.editor.product;

import java.util.ArrayList;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.pde.internal.core.iproduct.*;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.pde.internal.ui.editor.*;
import org.eclipse.pde.internal.ui.parts.FormEntry;
import org.eclipse.pde.internal.ui.util.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.*;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.*;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.model.*;


public class LauncherSection extends PDESection {

	private FormEntry fNameEntry;

	private ArrayList fIcons = new ArrayList();

	private Button fIcoButton;

	private Button fBmpButton;

	class IconEntry extends FormEntry {
		String fIconId;
		public IconEntry(Composite parent, FormToolkit toolkit, String labelText, String iconId) {
			super(parent, toolkit, labelText, PDEUIMessages.LauncherSection_browse, isEditable(), 20); //$NON-NLS-1$
			fIconId = iconId;
			addEntryFormListener();
			setEditable(isEditable());
		}		
		private void addEntryFormListener() {
			IActionBars actionBars = getPage().getPDEEditor().getEditorSite().getActionBars();
			setFormEntryListener(new FormEntryAdapter(LauncherSection.this, actionBars) {
				public void textValueChanged(FormEntry entry) {
					getLauncherInfo().setIconPath(fIconId, entry.getValue());
				}			
				public void browseButtonSelected(FormEntry entry) {
					handleBrowse((IconEntry)entry);
				}			
				public void linkActivated(HyperlinkEvent e) {
					openImage(IconEntry.this.getValue());
				}
			});
		}		
		public String getIconId() {
			return fIconId;
		}		
	}

	public LauncherSection(PDEFormPage page, Composite parent) {
		super(page, parent, Section.DESCRIPTION);
		createClient(getSection(), page.getEditor().getToolkit());
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.PDESection#createClient(org.eclipse.ui.forms.widgets.Section, org.eclipse.ui.forms.widgets.FormToolkit)
	 */
	protected void createClient(Section section, FormToolkit toolkit) {
		section.setText(PDEUIMessages.LauncherSection_title); //$NON-NLS-1$
		section.setDescription(PDEUIMessages.LauncherSection_desc); //$NON-NLS-1$

		Composite client = toolkit.createComposite(section);
		TableWrapLayout layout = new TableWrapLayout();
		layout.numColumns = 2;
		client.setLayout(layout);
		
		IActionBars actionBars = getPage().getPDEEditor().getEditorSite().getActionBars();
		fNameEntry = new FormEntry(client, toolkit, PDEUIMessages.LauncherSection_launcherName, null, false); //$NON-NLS-1$
		fNameEntry.setFormEntryListener(new FormEntryAdapter(this, actionBars) {
			public void textValueChanged(FormEntry entry) {
				getLauncherInfo().setLauncherName(entry.getValue());
			}
		});
		fNameEntry.setEditable(isEditable());
		
		createLabel(client, toolkit, "", 2);	 //$NON-NLS-1$
		createLabel(client, toolkit, PDEUIMessages.LauncherSection_label, 2); //$NON-NLS-1$
		
		addLinuxSection(client, toolkit);
		addMacSection(client, toolkit);
		addSolarisSection(client, toolkit);
		addWin32Section(client, toolkit);
		
		toolkit.paintBordersFor(client);
		section.setClient(client);
		GridData gd = new GridData(GridData.FILL_HORIZONTAL|GridData.VERTICAL_ALIGN_BEGINNING);
		gd.verticalSpan = 3;
		section.setLayoutData(gd);
	}
	
	private void addWin32Section(Composite parent, FormToolkit toolkit) {
		Composite comp = createComposite(parent, toolkit, "win32"); //$NON-NLS-1$
		
		fIcoButton = toolkit.createButton(comp, PDEUIMessages.LauncherSection_ico, SWT.RADIO); //$NON-NLS-1$
		TableWrapData gd = new TableWrapData();
		gd.colspan = 3;
		fIcoButton.setLayoutData(gd);
		fIcoButton.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				boolean selected = fIcoButton.getSelection();
				getLauncherInfo().setUseWinIcoFile(selected);
				updateWinEntries(selected);
			}
		});
		fIcoButton.setEnabled(isEditable());
		
		fIcons.add(new IconEntry(comp, toolkit, PDEUIMessages.LauncherSection_file, ILauncherInfo.P_ICO_PATH)); //$NON-NLS-1$
		
		fBmpButton = toolkit.createButton(comp, PDEUIMessages.LauncherSection_bmpImages, SWT.RADIO); //$NON-NLS-1$
		gd = new TableWrapData();
		gd.colspan = 3;
		fBmpButton.setLayoutData(gd);
		fBmpButton.setEnabled(isEditable());
		
		final Label label = toolkit.createLabel(comp, PDEUIMessages.LauncherSection_bmpImagesText, SWT.WRAP); //$NON-NLS-1$
		gd = new TableWrapData();
		gd.colspan = 3;
		label.setLayoutData(gd);

		fIcons.add(new IconEntry(comp, toolkit, PDEUIMessages.LauncherSection_Low16, ILauncherInfo.WIN32_16_LOW)); //$NON-NLS-1$
		fIcons.add(new IconEntry(comp, toolkit, PDEUIMessages.LauncherSection_High16, ILauncherInfo.WIN32_16_HIGH)); //$NON-NLS-1$
		fIcons.add(new IconEntry(comp, toolkit, PDEUIMessages.LauncherSection_32Low, ILauncherInfo.WIN32_32_LOW)); //$NON-NLS-1$
		fIcons.add(new IconEntry(comp, toolkit, PDEUIMessages.LauncherSection_32High, ILauncherInfo.WIN32_32_HIGH)); //$NON-NLS-1$
		fIcons.add(new IconEntry(comp, toolkit, PDEUIMessages.LauncherSection_48Low, ILauncherInfo.WIN32_48_LOW)); //$NON-NLS-1$
		fIcons.add(new IconEntry(comp, toolkit, PDEUIMessages.LauncherSection_48High, ILauncherInfo.WIN32_48_HIGH)); //$NON-NLS-1$

		toolkit.paintBordersFor(comp);
	}
	
	private void createLabel(Composite parent, FormToolkit toolkit, String text, int span) {
		Label label = toolkit.createLabel(parent, text, SWT.WRAP);
		Layout layout = parent.getLayout();
		if (layout instanceof GridLayout) {
			GridData gd = new GridData();
			gd.horizontalSpan = span;
			label.setLayoutData(gd);				
		}
		else if (layout instanceof TableWrapLayout) {
			TableWrapData td = new TableWrapData();
			td.colspan = span;
			label.setLayoutData(td);			
		}
	}
	
	private void addLinuxSection(Composite parent, FormToolkit toolkit) {
		Composite comp = createComposite(parent, toolkit, "linux"); //$NON-NLS-1$
		createLabel(comp, toolkit, PDEUIMessages.LauncherSection_linuxLabel, 3);	 //$NON-NLS-1$
		fIcons.add(new IconEntry(comp, toolkit, PDEUIMessages.LauncherSection_icon, ILauncherInfo.LINUX_ICON)); //$NON-NLS-1$
		toolkit.paintBordersFor(comp);
	}

	private void addSolarisSection(Composite parent, FormToolkit toolkit) {
		Composite comp = createComposite(parent, toolkit, "solaris"); //$NON-NLS-1$
		createLabel(comp, toolkit, PDEUIMessages.LauncherSection_solarisLabel, 3); //$NON-NLS-1$

		fIcons.add(new IconEntry(comp, toolkit, PDEUIMessages.LauncherSection_large, ILauncherInfo.SOLARIS_LARGE)); //$NON-NLS-1$
		fIcons.add(new IconEntry(comp, toolkit, PDEUIMessages.LauncherSection_medium, ILauncherInfo.SOLARIS_MEDIUM)); //$NON-NLS-1$
		fIcons.add(new IconEntry(comp, toolkit, PDEUIMessages.LauncherSection_small, ILauncherInfo.SOLARIS_SMALL)); //$NON-NLS-1$
		fIcons.add(new IconEntry(comp, toolkit, PDEUIMessages.LauncherSection_tiny, ILauncherInfo.SOLARIS_TINY)); //$NON-NLS-1$
		
		toolkit.paintBordersFor(comp);
	}
	
	private void addMacSection(Composite parent, FormToolkit toolkit) {
		Composite comp = createComposite(parent, toolkit, "macosx");		 //$NON-NLS-1$
		createLabel(comp, toolkit, PDEUIMessages.LauncherSection_macLabel, 3);		 //$NON-NLS-1$
		fIcons.add(new IconEntry(comp, toolkit, PDEUIMessages.LauncherSection_file, ILauncherInfo.MACOSX_ICON)); //$NON-NLS-1$
		toolkit.paintBordersFor(comp);
	}
	
	private Composite createComposite(Composite parent, FormToolkit toolkit, String text) {
		ExpandableComposite ec = toolkit.createSection(parent, ExpandableComposite.TWISTIE|ExpandableComposite.COMPACT);
		ec.setText(text);
		
		TableWrapData gd = new TableWrapData(TableWrapData.FILL_GRAB);
		gd.colspan = 2;
		ec.setLayoutData(gd);
		Composite comp = toolkit.createComposite(ec);
		TableWrapLayout layout = new TableWrapLayout();
		layout.leftMargin = layout.rightMargin = 0;
		layout.numColumns = 3;
		comp.setLayout(layout);
		ec.setClient(comp);
		return comp;
	}
	
	public void refresh() {
		ILauncherInfo info = getLauncherInfo();
		fNameEntry.setValue(info.getLauncherName(), true);
		boolean useIco = info.usesWinIcoFile();
		fIcoButton.setSelection(useIco);
		fBmpButton.setSelection(!useIco);
		
		for (int i = 0; i < fIcons.size(); i++) {
			IconEntry entry = (IconEntry)fIcons.get(i);
			entry.setValue(info.getIconPath(entry.getIconId()), true);
		}
		updateWinEntries(useIco);
		super.refresh();
	}
	
	private void updateWinEntries(boolean useIco) {
		for (int i = 0; i < fIcons.size(); i++) {
			IconEntry entry = (IconEntry)fIcons.get(i);
			String id = entry.getIconId();
			if (id.equals(ILauncherInfo.P_ICO_PATH)) {
				entry.setEditable(isEditable()&& useIco);
			} else if (id.equals(ILauncherInfo.WIN32_16_HIGH) 
					|| id.equals(ILauncherInfo.WIN32_16_LOW)
					|| id.equals(ILauncherInfo.WIN32_32_HIGH)
					|| id.equals(ILauncherInfo.WIN32_32_LOW)
					|| id.equals(ILauncherInfo.WIN32_48_HIGH)
					|| id.equals(ILauncherInfo.WIN32_48_LOW)) {
				entry.setEditable(isEditable() && !useIco);
			}
		}
	}
	
	private ILauncherInfo getLauncherInfo() {
		ILauncherInfo info = getProduct().getLauncherInfo();
		if (info == null) {
			info = getModel().getFactory().createLauncherInfo();
			getProduct().setLauncherInfo(info);
		}
		return info;
	}
	
	private IProduct getProduct() {
		return getModel().getProduct();
	}
	
	private IProductModel getModel() {
		return (IProductModel)getPage().getPDEEditor().getAggregateModel();
	}
	
	public void commit(boolean onSave) {
		fNameEntry.commit();
		for (int i = 0; i < fIcons.size(); i++)
			((FormEntry)fIcons.get(i)).commit();
		super.commit(onSave);
	}
	
	public void cancelEdit() {
		fNameEntry.cancelEdit();
		for (int i = 0; i < fIcons.size(); i++)
			((FormEntry)fIcons.get(i)).commit();
		super.cancelEdit();
	}
	
	private void handleBrowse(IconEntry entry) {
		ElementTreeSelectionDialog dialog =
			new ElementTreeSelectionDialog(
				getSection().getShell(),
				new WorkbenchLabelProvider(),
				new WorkbenchContentProvider());
				
		dialog.setValidator(new FileValidator());
		dialog.setAllowMultiple(false);
		dialog.setTitle(PDEUIMessages.LauncherSection_dialogTitle);  //$NON-NLS-1$
		String extension = getExtension(entry.getIconId());
		dialog.setMessage(PDEUIMessages.LauncherSection_dialogMessage); //$NON-NLS-1$
		dialog.addFilter(new FileExtensionFilter(extension)); 
		dialog.setInput(PDEPlugin.getWorkspace().getRoot());

		if (dialog.open() == ElementTreeSelectionDialog.OK) {
			IFile file = (IFile)dialog.getFirstResult();
			entry.setValue(file.getFullPath().toString());
		}
	}
	
	private void openImage(String value) {
		IWorkspaceRoot root = PDEPlugin.getWorkspace().getRoot();
		Path path = new Path(value);
		if(path.isEmpty()){
			MessageDialog.openWarning(PDEPlugin.getActiveWorkbenchShell(), PDEUIMessages.WindowImagesSection_open, PDEUIMessages.WindowImagesSection_emptyPath); //$NON-NLS-1$ //$NON-NLS-2$
			return;
		}
		IResource resource = root.findMember(new Path(value));
		try {
			if (resource != null && resource instanceof IFile)
				IDE.openEditor(PDEPlugin.getActivePage(), (IFile)resource, true);
			else
				MessageDialog.openWarning(PDEPlugin.getActiveWorkbenchShell(), PDEUIMessages.WindowImagesSection_open, PDEUIMessages.WindowImagesSection_warning); //$NON-NLS-1$ //$NON-NLS-2$
		} catch (PartInitException e) {
		}			
	}

	private String getExtension(String iconId) {
		if (iconId.equals(ILauncherInfo.LINUX_ICON))
			return "xpm"; //$NON-NLS-1$
		if (iconId.equals(ILauncherInfo.MACOSX_ICON))
			return "icns"; //$NON-NLS-1$
		if (iconId.equals(ILauncherInfo.SOLARIS_LARGE))
			return "l.pm"; //$NON-NLS-1$
		if (iconId.equals(ILauncherInfo.SOLARIS_MEDIUM))
			return "m.pm"; //$NON-NLS-1$
		if (iconId.equals(ILauncherInfo.SOLARIS_SMALL))
			return "s.pm"; //$NON-NLS-1$
		if (iconId.equals(ILauncherInfo.SOLARIS_TINY))
			return "t.pm"; //$NON-NLS-1$
		if (iconId.equals(ILauncherInfo.P_ICO_PATH))
			return "ico"; //$NON-NLS-1$
		return "bmp";	 //$NON-NLS-1$
	}
	
	public boolean canPaste(Clipboard clipboard) {
		Display d = getSection().getDisplay();
		Control c = d.getFocusControl();
		if (c instanceof Text)
			return true;
		return false;
	}

}
