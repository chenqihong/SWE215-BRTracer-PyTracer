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
package org.eclipse.help.ui.internal.views;

import org.eclipse.help.ui.internal.HelpUIResources;
import org.eclipse.help.ui.internal.IHelpUIConstants;
import org.eclipse.help.ui.internal.Messages;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.forms.AbstractFormPart;
import org.eclipse.ui.forms.FormColors;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.events.IHyperlinkListener;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.ImageHyperlink;

public class SeeAlsoPart extends AbstractFormPart implements IHelpPart {
	private Composite container;
	private Composite linkContainer;
	private ReusableHelpPart helpPart;
	private String id;

	/**
	 * @param parent
	 * @param toolkit
	 * @param style
	 */
	public SeeAlsoPart(Composite parent, FormToolkit toolkit) {
		container = toolkit.createComposite(parent);
		GridLayout layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		layout.verticalSpacing = 0;
		container.setLayout(layout);
		Composite sep = toolkit.createCompositeSeparator(container);
		GridData gd = new GridData(GridData.HORIZONTAL_ALIGN_FILL);
		gd.heightHint = 1;
		sep.setLayoutData(gd);
		
		Composite innerContainer = toolkit.createComposite(container);
		innerContainer.setLayoutData(new GridData(GridData.FILL_BOTH));
		layout = new GridLayout();
		layout.marginHeight = 0;
		innerContainer.setLayout(layout);
		Label label = toolkit.createLabel(innerContainer, Messages.SeeAlsoPart_goto);
		label.setForeground(toolkit.getColors().getColor(FormColors.TITLE));
		linkContainer = toolkit.createComposite(innerContainer);
		linkContainer.setLayoutData(new GridData(GridData.FILL_BOTH));
		RowLayout rlayout = new RowLayout();
		rlayout.marginBottom = 0;
		rlayout.marginTop = 0;
		rlayout.marginLeft = 0;
		rlayout.marginRight = 0;
		rlayout.justify = false;
		rlayout.wrap = true;
		linkContainer.setLayout(rlayout);
	}
	
	private void updateLinks(String href) {
		Control [] children = linkContainer.getChildren();
		for (int i=0; i<children.length; i++) {
			ImageHyperlink link = (ImageHyperlink)children[i];
			RowData data = (RowData)link.getLayoutData();
			data.exclude = link.getHref().equals(href);
			link.setVisible(!data.exclude);
		}
		linkContainer.layout();
		helpPart.reflow();
	}

	private void addLinks(final Composite container, FormToolkit toolkit) {
		IHyperlinkListener listener = new HyperlinkAdapter() {
			public void linkActivated(final HyperlinkEvent e) {
				container.getDisplay().asyncExec(new Runnable() {
					public void run() {
						SeeAlsoPart.this.helpPart.showPage((String) e.getHref(), true);
					}
				});
			}
		};
		if ((helpPart.getStyle() & ReusableHelpPart.ALL_TOPICS) != 0)
			addPageLink(container, toolkit, Messages.SeeAlsoPart_allTopics, IHelpUIConstants.HV_ALL_TOPICS_PAGE, 
				IHelpUIConstants.IMAGE_ALL_TOPICS, listener);
		if ((helpPart.getStyle() & ReusableHelpPart.SEARCH) != 0) {
			addPageLink(container, toolkit, Messages.SeeAlsoPart_search, IHelpUIConstants.HV_FSEARCH_PAGE, 
				IHelpUIConstants.IMAGE_HELP_SEARCH, listener);
		}
		if ((helpPart.getStyle() & ReusableHelpPart.CONTEXT_HELP) != 0) {
			addPageLink(container, toolkit, Messages.SeeAlsoPart_contextHelp, 
				IHelpUIConstants.HV_CONTEXT_HELP_PAGE,
				IHelpUIConstants.IMAGE_RELATED_TOPICS, listener);
		}
		if ((helpPart.getStyle() & ReusableHelpPart.BOOKMARKS) != 0) {
			addPageLink(container, toolkit, Messages.SeeAlsoPart_bookmarks, 
				IHelpUIConstants.HV_BOOKMARKS_PAGE,
				IHelpUIConstants.IMAGE_BOOKMARKS, listener);
		}
	}

	private void addPageLink(Composite container, FormToolkit toolkit, String text, String id,
			String imgRef, IHyperlinkListener listener) {
		String cid = helpPart.getCurrentPageId();
		if (cid!=null && cid.equals(id))
			return;
		ImageHyperlink link = toolkit.createImageHyperlink(container, SWT.WRAP);
		link.setImage(HelpUIResources.getImage(imgRef));
		link.setText(text);
		link.setHref(id);
		link.addHyperlinkListener(listener);
		RowData data = new RowData();
		data.exclude = false;
		link.setLayoutData(data);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.help.ui.internal.views.IHelpPart#getControl()
	 */
	public Control getControl() {
		return container;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.help.ui.internal.views.IHelpPart#init(org.eclipse.help.ui.internal.views.NewReusableHelpPart)
	 */
	public void init(ReusableHelpPart parent, String id, IMemento memento) {
		this.helpPart = parent;
		this.id = id;
		addLinks(linkContainer, helpPart.getForm().getToolkit());
	}

	public String getId() {
		return id;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.help.ui.internal.views.IHelpPart#setVisible(boolean)
	 */
	public void setVisible(boolean visible) {
		container.setVisible(visible);
		if (visible)
			markStale();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.help.ui.internal.views.IHelpPart#fillContextMenu(org.eclipse.jface.action.IMenuManager)
	 */
	public boolean fillContextMenu(IMenuManager manager) {
		return false;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.help.ui.internal.views.IHelpPart#hasFocusControl(org.eclipse.swt.widgets.Control)
	 */
	public boolean hasFocusControl(Control control) {
		return control.getParent() == linkContainer;
	}

	public IAction getGlobalAction(String id) {
		if (id.equals(ActionFactory.COPY.getId()))
			return helpPart.getCopyAction();
		return null;
	}
	public void stop() {
	}
	public void refresh() {
		if (linkContainer!=null && helpPart.getCurrentPageId()!=null)
			updateLinks(helpPart.getCurrentPageId());
		super.refresh();
	}

	public void toggleRoleFilter() {
	}

	public void refilter() {
	}

	public void saveState(IMemento memento) {
	}	
	
	public void setFocus() {
		if (linkContainer!=null)
			linkContainer.setFocus();
	}
}