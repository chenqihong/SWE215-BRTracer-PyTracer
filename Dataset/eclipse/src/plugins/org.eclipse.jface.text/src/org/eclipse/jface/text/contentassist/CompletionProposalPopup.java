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
package org.eclipse.jface.text.contentassist;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.Platform;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.events.VerifyEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;

import org.eclipse.jface.contentassist.IContentAssistSubjectControl;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.IEditingSupport;
import org.eclipse.jface.text.IEditingSupportRegistry;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.IRewriteTarget;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension;
import org.eclipse.jface.text.TextUtilities;


/**
 * This class is used to present proposals to the user. If additional
 * information exists for a proposal, then selecting that proposal
 * will result in the information being displayed in a secondary
 * window.
 *
 * @see org.eclipse.jface.text.contentassist.ICompletionProposal
 * @see org.eclipse.jface.text.contentassist.AdditionalInfoController
 */
class CompletionProposalPopup implements IContentAssistListener {
	/**
	 * Set to <code>true</code> to use a Table with SWT.VIRTUAL.
	 * @since 3.1
	 */
	private static final boolean USE_VIRTUAL= !Platform.WS_MOTIF.equals(Platform.getWS());

	private final class ProposalSelectionListener implements KeyListener {
		public void keyPressed(KeyEvent e) {
			if (!Helper.okToUse(fProposalShell))
				return;

			if (e.character == 0 && e.keyCode == SWT.MOD1) {
				// http://dev.eclipse.org/bugs/show_bug.cgi?id=34754
				int index= fProposalTable.getSelectionIndex();
				if (index >= 0)
					selectProposal(index, true);
			}
		}

		public void keyReleased(KeyEvent e) {
			if (!Helper.okToUse(fProposalShell))
				return;

			if (e.character == 0 && e.keyCode == SWT.MOD1) {
				// http://dev.eclipse.org/bugs/show_bug.cgi?id=34754
				int index= fProposalTable.getSelectionIndex();
				if (index >= 0)
					selectProposal(index, false);
			}
		}
	}

	/** The associated text viewer. */
	private ITextViewer fViewer;
	/** The associated content assistant. */
	private ContentAssistant fContentAssistant;
	/** The used additional info controller. */
	private AdditionalInfoController fAdditionalInfoController;
	/** The closing strategy for this completion proposal popup. */
	private PopupCloser fPopupCloser= new PopupCloser();
	/** The popup shell. */
	private Shell fProposalShell;
	/** The proposal table. */
	private Table fProposalTable;
	/** Indicates whether a completion proposal is being inserted. */
	private boolean fInserting= false;
	/** The key listener to control navigation. */
	private ProposalSelectionListener fKeyListener;
	/** List of document events used for filtering proposals. */
	private List fDocumentEvents= new ArrayList();
	/** Listener filling the document event queue. */
	private IDocumentListener fDocumentListener;
	/** Reentrance count for filtered proposals. */
	private long fInvocationCounter= 0;
	/** The filter list of proposals. */
	private ICompletionProposal[] fFilteredProposals;
	/** The computed list of proposals. */
	private ICompletionProposal[] fComputedProposals;
	/** The offset for which the proposals have been computed. */
	private int fInvocationOffset;
	/** The offset for which the computed proposals have been filtered. */
	private int fFilterOffset;
	/**
	 * The most recently selected proposal.
	 * @since 3.0
	 */
	private ICompletionProposal fLastProposal;
	/**
	 * The content assist subject control.
	 * This replaces <code>fViewer</code>
	 *
	 * @since 3.0
	 */
	private IContentAssistSubjectControl fContentAssistSubjectControl;
	/**
	 * The content assist subject control adapter.
	 * This replaces <code>fViewer</code>
	 *
	 * @since 3.0
	 */
	private ContentAssistSubjectControlAdapter fContentAssistSubjectControlAdapter;
	/**
	 * Remembers the size for this completion proposal popup.
	 * @since 3.0
	 */
	private Point fSize;
	/**
	 * Editor helper that communicates that the completion proposal popup may
	 * have focus while the 'logical focus' is still with the editor.
	 * @since 3.1
	 */
	private IEditingSupport fFocusHelper;
	/**
	 * Set to true by {@link #computeFilteredProposals(int, DocumentEvent)} if
	 * the returned proposals are a subset of {@link #fFilteredProposals},
	 * <code>false</code> if not.
	 * @since 3.1
	 */
	private boolean fIsFilteredSubset;


	/**
	 * Creates a new completion proposal popup for the given elements.
	 *
	 * @param contentAssistant the content assistant feeding this popup
	 * @param viewer the viewer on top of which this popup appears
	 * @param infoController the information control collaborating with this popup
	 * @since 2.0
	 */
	public CompletionProposalPopup(ContentAssistant contentAssistant, ITextViewer viewer, AdditionalInfoController infoController) {
		fContentAssistant= contentAssistant;
		fViewer= viewer;
		fAdditionalInfoController= infoController;
		fContentAssistSubjectControlAdapter= new ContentAssistSubjectControlAdapter(fViewer);
	}

	/**
	 * Creates a new completion proposal popup for the given elements.
	 *
	 * @param contentAssistant the content assistant feeding this popup
	 * @param contentAssistSubjectControl the content assist subject control on top of which this popup appears
	 * @param infoController the information control collaborating with this popup
	 * @since 3.0
	 */
	public CompletionProposalPopup(ContentAssistant contentAssistant, IContentAssistSubjectControl contentAssistSubjectControl, AdditionalInfoController infoController) {
		fContentAssistant= contentAssistant;
		fContentAssistSubjectControl= contentAssistSubjectControl;
		fAdditionalInfoController= infoController;
		fContentAssistSubjectControlAdapter= new ContentAssistSubjectControlAdapter(fContentAssistSubjectControl);
	}

	/**
	 * Computes and presents completion proposals. The flag indicates whether this call has
	 * be made out of an auto activation context.
	 *
	 * @param autoActivated <code>true</code> if auto activation context
	 * @return an error message or <code>null</code> in case of no error
	 */
	public String showProposals(final boolean autoActivated) {

		if (fKeyListener == null)
			fKeyListener= new ProposalSelectionListener();

		final Control control= fContentAssistSubjectControlAdapter.getControl();

		if (!Helper.okToUse(fProposalShell) && control != null && !control.isDisposed()) {

			// add the listener before computing the proposals so we don't move the caret
			// when the user types fast.
			fContentAssistSubjectControlAdapter.addKeyListener(fKeyListener);

			BusyIndicator.showWhile(control.getDisplay(), new Runnable() {
				public void run() {

					fInvocationOffset= fContentAssistSubjectControlAdapter.getSelectedRange().x;
					fFilterOffset= fInvocationOffset;
					fComputedProposals= computeProposals(fInvocationOffset);

					int count= (fComputedProposals == null ? 0 : fComputedProposals.length);
					if (count == 0) {

						if (!autoActivated)
							control.getDisplay().beep();

						hide();

					} else {

						if (count == 1 && !autoActivated && canAutoInsert(fComputedProposals[0])) {

							insertProposal(fComputedProposals[0], (char) 0, 0, fInvocationOffset);
							hide();

						} else {
							createProposalSelector();
							setProposals(fComputedProposals, false);
							displayProposals();
						}
					}
				}
			});
		}

		return getErrorMessage();
	}

	/**
	 * Returns the completion proposal available at the given offset of the
	 * viewer's document. Delegates the work to the content assistant.
	 *
	 * @param offset the offset
	 * @return the completion proposals available at this offset
	 */
	private ICompletionProposal[] computeProposals(int offset) {
		if (fContentAssistSubjectControl != null)
			return fContentAssistant.computeCompletionProposals(fContentAssistSubjectControl, offset);
		return fContentAssistant.computeCompletionProposals(fViewer, offset);
	}

	/**
	 * Returns the error message.
	 *
	 * @return the error message
	 */
	private String getErrorMessage() {
		return fContentAssistant.getErrorMessage();
	}

	/**
	 * Creates the proposal selector.
	 */
	private void createProposalSelector() {
		if (Helper.okToUse(fProposalShell))
			return;

		Control control= fContentAssistSubjectControlAdapter.getControl();
		fProposalShell= new Shell(control.getShell(), SWT.ON_TOP | SWT.RESIZE );
		if (USE_VIRTUAL) {
			fProposalTable= new Table(fProposalShell, SWT.H_SCROLL | SWT.V_SCROLL | SWT.VIRTUAL);

			Listener listener= new Listener() {
				public void handleEvent(Event event) {
					handleSetData(event);
				}
			};
			fProposalTable.addListener(SWT.SetData, listener);
		} else {
			fProposalTable= new Table(fProposalShell, SWT.H_SCROLL | SWT.V_SCROLL);
		}

		fProposalTable.setLocation(0, 0);
		if (fAdditionalInfoController != null)
			fAdditionalInfoController.setSizeConstraints(50, 10, true, false);

		GridLayout layout= new GridLayout();
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		fProposalShell.setLayout(layout);

		GridData data= new GridData(GridData.FILL_BOTH);


		Point size= fContentAssistant.restoreCompletionProposalPopupSize();
		if (size != null) {
			fProposalTable.setLayoutData(data);
			fProposalShell.setSize(size);
		} else {
			data.heightHint= fProposalTable.getItemHeight() * 10;
			data.widthHint= 300;
			fProposalTable.setLayoutData(data);
			fProposalShell.pack();
		}

		fProposalShell.addControlListener(new ControlListener() {

			public void controlMoved(ControlEvent e) {}

			public void controlResized(ControlEvent e) {
				if (fAdditionalInfoController != null) {
					// reset the cached resize constraints
					fAdditionalInfoController.setSizeConstraints(50, 10, true, false);
				}

				fSize= fProposalShell.getSize();
			}
		});

		if (!"carbon".equals(SWT.getPlatform())) //$NON-NLS-1$
			fProposalShell.setBackground(control.getDisplay().getSystemColor(SWT.COLOR_BLACK));

		Color c= fContentAssistant.getProposalSelectorBackground();
		if (c == null)
			c= control.getDisplay().getSystemColor(SWT.COLOR_INFO_BACKGROUND);
		fProposalTable.setBackground(c);

		c= fContentAssistant.getProposalSelectorForeground();
		if (c == null)
			c= control.getDisplay().getSystemColor(SWT.COLOR_INFO_FOREGROUND);
		fProposalTable.setForeground(c);

		fProposalTable.addSelectionListener(new SelectionListener() {

			public void widgetSelected(SelectionEvent e) {}

			public void widgetDefaultSelected(SelectionEvent e) {
				selectProposalWithMask(e.stateMask);
			}
		});

		fPopupCloser.install(fContentAssistant, fProposalTable);

		fProposalShell.addDisposeListener(new DisposeListener() {
			public void widgetDisposed(DisposeEvent e) {
				unregister(); // but don't dispose the shell, since we're being called from its disposal event!
			}
		});

		fProposalTable.setHeaderVisible(false);
		fContentAssistant.addToLayout(this, fProposalShell, ContentAssistant.LayoutManager.LAYOUT_PROPOSAL_SELECTOR, fContentAssistant.getSelectionOffset());
	}

	/**
	 * @since 3.1
	 */
	private void handleSetData(Event event) {
		TableItem item= (TableItem) event.item;
		int index= fProposalTable.indexOf(item);

		if (0 <= index && index < fFilteredProposals.length) {
			ICompletionProposal current= fFilteredProposals[index];

			item.setText(current.getDisplayString());
			item.setImage(current.getImage());
			item.setData(current);
		} else {
			// this should not happen, but does on win32
		}
	}

	/**
	 * Returns the proposal selected in the proposal selector.
	 *
	 * @return the selected proposal
	 * @since 2.0
	 */
	private ICompletionProposal getSelectedProposal() {
		int i= fProposalTable.getSelectionIndex();
		if (fFilteredProposals == null || i < 0 || i >= fFilteredProposals.length)
			return null;
		return fFilteredProposals[i];
	}

	/**
	 * Takes the selected proposal and applies it.
	 *
	 * @param stateMask the state mask
	 * @since 2.1
	 */
	private void selectProposalWithMask(int stateMask) {
		ICompletionProposal p= getSelectedProposal();
		hide();
		if (p != null)
			insertProposal(p, (char) 0, stateMask, fContentAssistSubjectControlAdapter.getSelectedRange().x);
	}

	/**
	 * Applies the given proposal at the given offset. The given character is the
	 * one that triggered the insertion of this proposal.
	 *
	 * @param p the completion proposal
	 * @param trigger the trigger character
	 * @param stateMask the state mask
	 * @param offset the offset
	 * @since 2.1
	 */
	private void insertProposal(ICompletionProposal p, char trigger, int stateMask, final int offset) {

		fInserting= true;
		IRewriteTarget target= null;
		IEditingSupport helper= new IEditingSupport() {

			public boolean isOriginator(DocumentEvent event, IRegion focus) {
				return focus.getOffset() <= offset && focus.getOffset() + focus.getLength() >= offset;
			}

			public boolean ownsFocusShell() {
				return false;
			}

		};

		try {

			IDocument document= fContentAssistSubjectControlAdapter.getDocument();

			if (fViewer instanceof ITextViewerExtension) {
				ITextViewerExtension extension= (ITextViewerExtension) fViewer;
				target= extension.getRewriteTarget();
			}

			if (target != null)
				target.beginCompoundChange();

			if (fViewer instanceof IEditingSupportRegistry) {
				IEditingSupportRegistry registry= (IEditingSupportRegistry) fViewer;
				registry.register(helper);
			}


			if (p instanceof ICompletionProposalExtension2 && fViewer != null) {
				ICompletionProposalExtension2 e= (ICompletionProposalExtension2) p;
				e.apply(fViewer, trigger, stateMask, offset);
			} else if (p instanceof ICompletionProposalExtension) {
				ICompletionProposalExtension e= (ICompletionProposalExtension) p;
				e.apply(document, trigger, offset);
			} else {
				p.apply(document);
			}

			Point selection= p.getSelection(document);
			if (selection != null) {
				fContentAssistSubjectControlAdapter.setSelectedRange(selection.x, selection.y);
				fContentAssistSubjectControlAdapter.revealRange(selection.x, selection.y);
			}

			IContextInformation info= p.getContextInformation();
			if (info != null) {

				int contextInformationOffset;
				if (p instanceof ICompletionProposalExtension) {
					ICompletionProposalExtension e= (ICompletionProposalExtension) p;
					contextInformationOffset= e.getContextInformationPosition();
				} else {
					if (selection == null)
						selection= fContentAssistSubjectControlAdapter.getSelectedRange();
					contextInformationOffset= selection.x + selection.y;
				}

				fContentAssistant.showContextInformation(info, contextInformationOffset);
			} else
				fContentAssistant.showContextInformation(null, -1);


		} finally {
			if (target != null)
				target.endCompoundChange();

			if (fViewer instanceof IEditingSupportRegistry) {
				IEditingSupportRegistry registry= (IEditingSupportRegistry) fViewer;
				registry.unregister(helper);
			}
			fInserting= false;
		}
	}

	/**
	 * Returns whether this popup has the focus.
	 *
	 * @return <code>true</code> if the popup has the focus
	 */
	public boolean hasFocus() {
		if (Helper.okToUse(fProposalShell))
			return (fProposalShell.isFocusControl() || fProposalTable.isFocusControl());

		return false;
	}

	/**
	 * Hides this popup.
	 */
	public void hide() {

		unregister();

		if (fViewer instanceof IEditingSupportRegistry) {
			IEditingSupportRegistry registry= (IEditingSupportRegistry) fViewer;
			registry.unregister(fFocusHelper);
		}

		if (Helper.okToUse(fProposalShell)) {

			fContentAssistant.removeContentAssistListener(this, ContentAssistant.PROPOSAL_SELECTOR);

			fPopupCloser.uninstall();
			fProposalShell.setVisible(false);
			fProposalShell.dispose();
			fProposalShell= null;
		}
	}

	/**
	 * Unregister this completion proposal popup.
	 *
	 * @since 3.0
	 */
	private void unregister() {
		if (fDocumentListener != null) {
			IDocument document= fContentAssistSubjectControlAdapter.getDocument();
			if (document != null)
				document.removeDocumentListener(fDocumentListener);
			fDocumentListener= null;
		}
		fDocumentEvents.clear();

		if (fKeyListener != null && fContentAssistSubjectControlAdapter.getControl() != null && !fContentAssistSubjectControlAdapter.getControl().isDisposed()) {
			fContentAssistSubjectControlAdapter.removeKeyListener(fKeyListener);
			fKeyListener= null;
		}

		if (fLastProposal != null) {
			if (fLastProposal instanceof ICompletionProposalExtension2 && fViewer != null) {
				ICompletionProposalExtension2 extension= (ICompletionProposalExtension2) fLastProposal;
				extension.unselected(fViewer);
			}
			fLastProposal= null;
		}

		fFilteredProposals= null;
		fComputedProposals= null;

		fContentAssistant.possibleCompletionsClosed();
	}

	/**
	 *Returns whether this popup is active. It is active if the proposal selector is visible.
	 *
	 * @return <code>true</code> if this popup is active
	 */
	public boolean isActive() {
		return fProposalShell != null && !fProposalShell.isDisposed();
	}

	/**
	 * Initializes the proposal selector with these given proposals.
	 * @param proposals the proposals
	 * @param isFilteredSubset if <code>true</code>, the proposal table is
	 *        not cleared, but the proposals that are not in the passed array
	 *        are removed from the displayed set
	 */
	private void setProposals(ICompletionProposal[] proposals, boolean isFilteredSubset) {
		if (Helper.okToUse(fProposalTable)) {

			ICompletionProposal oldProposal= getSelectedProposal();
			if (oldProposal instanceof ICompletionProposalExtension2 && fViewer != null)
				((ICompletionProposalExtension2) oldProposal).unselected(fViewer);

			fFilteredProposals= proposals;
			final int newLen= proposals.length;
			if (USE_VIRTUAL) {
				fProposalTable.setItemCount(newLen);
				fProposalTable.clearAll();
			} else {
				fProposalTable.setRedraw(false);
				fProposalTable.setItemCount(newLen);
				TableItem[] items= fProposalTable.getItems();
				for (int i= 0; i < items.length; i++) {
					TableItem item= items[i];
					ICompletionProposal proposal= proposals[i];
					item.setText(proposal.getDisplayString());
					item.setImage(proposal.getImage());
					item.setData(proposal);
				}
				fProposalTable.setRedraw(true);
			}

			Point currentLocation= fProposalShell.getLocation();
			Point newLocation= getLocation();
			if ((newLocation.x < currentLocation.x && newLocation.y == currentLocation.y) || newLocation.y < currentLocation.y)
				fProposalShell.setLocation(newLocation);

			selectProposal(0, false);
		}
	}

	/**
	 * Returns the graphical location at which this popup should be made visible.
	 *
	 * @return the location of this popup
	 */
	private Point getLocation() {
		int caret= fContentAssistSubjectControlAdapter.getCaretOffset();
		Point p= fContentAssistSubjectControlAdapter.getLocationAtOffset(caret);
		if (p.x < 0) p.x= 0;
		if (p.y < 0) p.y= 0;
		p= new Point(p.x, p.y + fContentAssistSubjectControlAdapter.getLineHeight());
		p= fContentAssistSubjectControlAdapter.getControl().toDisplay(p);
		if (p.x < 0) p.x= 0;
		if (p.y < 0) p.y= 0;
		return p;
	}

	/**
	 * Returns the size of this completion proposal popup.
	 *
	 * @return a Point containing the size
	 * @since 3.0
	 */
	Point getSize() {
		return fSize;
	}

	/**
	 * Displays this popup and install the additional info controller, so that additional info
	 * is displayed when a proposal is selected and additional info is available.
	 */
	private void displayProposals() {

		if (!Helper.okToUse(fProposalShell) ||  !Helper.okToUse(fProposalTable))
			return;

		if (fContentAssistant.addContentAssistListener(this, ContentAssistant.PROPOSAL_SELECTOR)) {

			if (fDocumentListener == null)
				fDocumentListener=  new IDocumentListener()  {
					public void documentAboutToBeChanged(DocumentEvent event) {
						if (!fInserting)
							fDocumentEvents.add(event);
					}

					public void documentChanged(DocumentEvent event) {
						if (!fInserting)
							filterProposals();
					}
				};
			IDocument document= fContentAssistSubjectControlAdapter.getDocument();
			if (document != null)
				document.addDocumentListener(fDocumentListener);

			if (fFocusHelper == null) {
				fFocusHelper= new IEditingSupport() {

					public boolean isOriginator(DocumentEvent event, IRegion focus) {
						return false; // this helper just covers the focus change to the proposal shell, no remote editions
					}

					public boolean ownsFocusShell() {
						return true;
					}

				};
			}
			if (fViewer instanceof IEditingSupportRegistry) {
				IEditingSupportRegistry registry= (IEditingSupportRegistry) fViewer;
				registry.register(fFocusHelper);
			}


			/* https://bugs.eclipse.org/bugs/show_bug.cgi?id=52646
			 * on GTK, setVisible and such may run the event loop
			 * (see also https://bugs.eclipse.org/bugs/show_bug.cgi?id=47511)
			 * Since the user may have already canceled the popup or selected
			 * an entry (ESC or RETURN), we have to double check whether
			 * the table is still okToUse. See comments below
			 */
			fProposalShell.setVisible(true); // may run event loop on GTK
			// transfer focus since no verify key listener can be attached
			if (!fContentAssistSubjectControlAdapter.supportsVerifyKeyListener() && Helper.okToUse(fProposalShell))
				fProposalShell.setFocus(); // may run event loop on GTK ??

			if (fAdditionalInfoController != null && Helper.okToUse(fProposalTable)) {
				fAdditionalInfoController.install(fProposalTable);
				fAdditionalInfoController.handleTableSelectionChanged();
			}
		} else
			hide();
	}

	/*
	 * @see IContentAssistListener#verifyKey(VerifyEvent)
	 */
	public boolean verifyKey(VerifyEvent e) {
		if (!Helper.okToUse(fProposalShell))
			return true;

		char key= e.character;
		if (key == 0) {
			int newSelection= fProposalTable.getSelectionIndex();
			int visibleRows= (fProposalTable.getSize().y / fProposalTable.getItemHeight()) - 1;
			boolean smartToggle= false;
			switch (e.keyCode) {

				case SWT.ARROW_LEFT :
				case SWT.ARROW_RIGHT :
					filterProposals();
					return true;

				case SWT.ARROW_UP :
					newSelection -= 1;
					if (newSelection < 0)
						newSelection= fProposalTable.getItemCount() - 1;
					break;

				case SWT.ARROW_DOWN :
					newSelection += 1;
					if (newSelection > fProposalTable.getItemCount() - 1)
						newSelection= 0;
					break;

				case SWT.PAGE_DOWN :
					newSelection += visibleRows;
					if (newSelection >= fProposalTable.getItemCount())
						newSelection= fProposalTable.getItemCount() - 1;
					break;

				case SWT.PAGE_UP :
					newSelection -= visibleRows;
					if (newSelection < 0)
						newSelection= 0;
					break;

				case SWT.HOME :
					newSelection= 0;
					break;

				case SWT.END :
					newSelection= fProposalTable.getItemCount() - 1;
					break;

				default :
					if (e.keyCode != SWT.CAPS_LOCK && e.keyCode != SWT.MOD1 && e.keyCode != SWT.MOD2 && e.keyCode != SWT.MOD3 && e.keyCode != SWT.MOD4)
						hide();
					return true;
			}

			selectProposal(newSelection, smartToggle);

			e.doit= false;
			return false;

		}

		// key != 0
		switch (key) {
			case 0x1B: // Esc
				e.doit= false;
				hide();
				break;

			case '\n': // Ctrl-Enter on w2k
			case '\r': // Enter
				e.doit= false;
				selectProposalWithMask(e.stateMask);
				break;

			case '\t':
				e.doit= false;
				fProposalShell.setFocus();
				return false;

			default:
				ICompletionProposal p= getSelectedProposal();
				if (p instanceof ICompletionProposalExtension) {
					ICompletionProposalExtension t= (ICompletionProposalExtension) p;
					char[] triggers= t.getTriggerCharacters();
					if (contains(triggers, key)) {
						e.doit= false;
						hide();
						insertProposal(p, key, e.stateMask, fContentAssistSubjectControlAdapter.getSelectedRange().x);
					}
			}
		}

		return true;
	}

	/**
	 * Selects the entry with the given index in the proposal selector and feeds
	 * the selection to the additional info controller.
	 *
	 * @param index the index in the list
	 * @param smartToggle <code>true</code> if the smart toggle key has been pressed
	 * @since 2.1
	 */
	private void selectProposal(int index, boolean smartToggle) {

		if (fFilteredProposals == null)
			return;

		ICompletionProposal oldProposal= getSelectedProposal();
		if (oldProposal instanceof ICompletionProposalExtension2 && fViewer != null)
			((ICompletionProposalExtension2) oldProposal).unselected(fViewer);

		ICompletionProposal proposal= fFilteredProposals[index];
		if (proposal instanceof ICompletionProposalExtension2 && fViewer != null)
			((ICompletionProposalExtension2) proposal).selected(fViewer, smartToggle);

		fLastProposal= proposal;

		fProposalTable.setSelection(index);
		fProposalTable.showSelection();
		if (fAdditionalInfoController != null)
			fAdditionalInfoController.handleTableSelectionChanged();
	}

	/**
	 * Returns whether the given character is contained in the given array of
	 * characters.
	 *
	 * @param characters the list of characters
	 * @param c the character to look for in the list
	 * @return <code>true</code> if character belongs to the list
	 * @since 2.0
	 */
	private boolean contains(char[] characters, char c) {

		if (characters == null)
			return false;

		for (int i= 0; i < characters.length; i++) {
			if (c == characters[i])
				return true;
		}

		return false;
	}

	/*
	 * @see IEventConsumer#processEvent(VerifyEvent)
	 */
	public void processEvent(VerifyEvent e) {
	}

	/**
	 * Filters the displayed proposal based on the given cursor position and the
	 * offset of the original invocation of the content assistant.
	 */
	private void filterProposals() {
		++ fInvocationCounter;
		final Control control= fContentAssistSubjectControlAdapter.getControl();
		control.getDisplay().asyncExec(new Runnable() {
			final long fCounter= fInvocationCounter;
			public void run() {

				if (fCounter != fInvocationCounter)
					return;

				if (control.isDisposed())
					return;

				int offset= fContentAssistSubjectControlAdapter.getSelectedRange().x;
				ICompletionProposal[] proposals= null;
				try  {
					if (offset > -1) {
						DocumentEvent event= TextUtilities.mergeProcessedDocumentEvents(fDocumentEvents);
						proposals= computeFilteredProposals(offset, event);
					}
				} catch (BadLocationException x)  {
				} finally  {
					fDocumentEvents.clear();
				}
				fFilterOffset= offset;

				if (proposals != null && proposals.length > 0)
					setProposals(proposals, fIsFilteredSubset);
				else
					hide();
			}
		});
	}

	/**
	 * Computes the subset of already computed proposals that are still valid for
	 * the given offset.
	 *
	 * @param offset the offset
	 * @param event the merged document event
	 * @return the set of filtered proposals
	 * @since 3.0
	 */
	private ICompletionProposal[] computeFilteredProposals(int offset, DocumentEvent event) {

		if (offset == fInvocationOffset && event == null) {
			fIsFilteredSubset= false;
			return fComputedProposals;
		}

		if (offset < fInvocationOffset) {
			fIsFilteredSubset= false;
			fInvocationOffset= offset;
			fComputedProposals= computeProposals(fInvocationOffset);
			return fComputedProposals;
		}

		ICompletionProposal[] proposals;
		if (offset < fFilterOffset) {
			proposals= fComputedProposals;
			fIsFilteredSubset= false;
		} else {
			proposals= fFilteredProposals;
			fIsFilteredSubset= true;
		}

		if (proposals == null) {
			fIsFilteredSubset= false;
			return null;
		}

		IDocument document= fContentAssistSubjectControlAdapter.getDocument();
		int length= proposals.length;
		List filtered= new ArrayList(length);
		for (int i= 0; i < length; i++) {

			if (proposals[i] instanceof ICompletionProposalExtension2) {

				ICompletionProposalExtension2 p= (ICompletionProposalExtension2) proposals[i];
				if (p.validate(document, offset, event))
					filtered.add(p);

			} else if (proposals[i] instanceof ICompletionProposalExtension) {

				ICompletionProposalExtension p= (ICompletionProposalExtension) proposals[i];
				if (p.isValidFor(document, offset))
					filtered.add(p);

			} else {
				// restore original behavior
				fIsFilteredSubset= false;
				fInvocationOffset= offset;
				fComputedProposals= computeProposals(fInvocationOffset);
				return fComputedProposals;
			}
		}

		return (ICompletionProposal[]) filtered.toArray(new ICompletionProposal[filtered.size()]);
	}

	/**
	 * Requests the proposal shell to take focus.
	 *
	 * @since 3.0
	 */
	public void setFocus() {
		if (Helper.okToUse(fProposalShell)) {
			fProposalShell.setFocus();
		}
	}
	
	/**
	 * Returns <code>true</code> if <code>proposal</code> should be auto-inserted,
	 * <code>false</code> otherwise.
	 * 
	 * @param proposal the single proposal that might be automatically inserted
	 * @return <code>true</code> if <code>proposal</code> can be inserted automatically,
	 *         <code>false</code> otherwise
	 * @since 3.1
	 */
	private boolean canAutoInsert(ICompletionProposal proposal) {
		if (fContentAssistant.isAutoInserting()) {
			if (proposal instanceof ICompletionProposalExtension4) {
				ICompletionProposalExtension4 ext= (ICompletionProposalExtension4) proposal;
				return ext.isAutoInsertable();
			}
			return true; // default behavior before ICompletionProposalExtension4 was introduced
		}
		return false;
	}

	/**
	 * Completes the common prefix of all proposals directly in the code. If no
	 * common prefix can be found, the proposal popup is shown.
	 *
	 * @return an error message if completion failed.
	 * @since 3.0
	 */
	public String incrementalComplete() {
		if (Helper.okToUse(fProposalShell) && fFilteredProposals != null) {
			completeCommonPrefix();
		} else {
			final Control control= fContentAssistSubjectControlAdapter.getControl();

			if (fKeyListener == null)
				fKeyListener= new ProposalSelectionListener();

			if (!Helper.okToUse(fProposalShell) && !control.isDisposed())
				fContentAssistSubjectControlAdapter.addKeyListener(fKeyListener);

			BusyIndicator.showWhile(control.getDisplay(), new Runnable() {
				public void run() {

					fInvocationOffset= fContentAssistSubjectControlAdapter.getSelectedRange().x;
					fFilterOffset= fInvocationOffset;
					fFilteredProposals= computeProposals(fInvocationOffset);

					int count= (fFilteredProposals == null ? 0 : fFilteredProposals.length);
					if (count == 0) {
						control.getDisplay().beep();
						hide();
					} else if (count == 1 && canAutoInsert(fFilteredProposals[0])) {
						insertProposal(fFilteredProposals[0], (char) 0, 0, fInvocationOffset);
						hide();
					} else {
						if (completeCommonPrefix())
							hide();
						else {
							fComputedProposals= fFilteredProposals;
							createProposalSelector();
							setProposals(fComputedProposals, false);
							displayProposals();
						}
					}
				}
			});
		}
		return getErrorMessage();
	}

	/**
	 * Acts upon <code>fFilteredProposals</code>: if there is just one valid
	 * proposal, it is inserted, otherwise, the common prefix of all proposals
	 * is inserted into the document. If there is no common prefix, nothing
	 * happens and <code>false</code> is returned.
	 * 
	 * @return <code>true</code> if a single proposal was inserted and the
	 *         selector can be closed, <code>false</code> if more than once
	 *         choice remain
	 * @since 3.0
	 */
	private boolean completeCommonPrefix() {

		// 0: insert single proposals
		if (fFilteredProposals.length == 1) {
			if (canAutoInsert(fFilteredProposals[0])) {
				insertProposal(fFilteredProposals[0], (char) 0, 0, fFilterOffset);
				hide();
				return true;
			}
			return false;
		}

		// 1: extract pre- and postfix from all remaining proposals
		IDocument document= fContentAssistSubjectControlAdapter.getDocument();

		// contains the common postfix in the case that there are any proposals matching our LHS
		StringBuffer rightCasePostfix= null;
		List rightCase= new ArrayList();

		// whether to check for non-case compatible matches. This is initially true, and stays so
		// as long as there are i) no case-sensitive matches and ii) all proposals share the same
		// (although not corresponding with the document contents) common prefix.
		boolean checkWrongCase= true;
		// the prefix of all case insensitive matches. This differs from the document
		// contents and will be replaced.
		CharSequence wrongCasePrefix= null;
		int wrongCasePrefixStart= 0;
		// contains the common postfix of all case-insensitive matches
		StringBuffer wrongCasePostfix= null;
		List wrongCase= new ArrayList();

		for (int i= 0; i < fFilteredProposals.length; i++) {
			ICompletionProposal proposal= fFilteredProposals[i];
			CharSequence insertion= getPrefixCompletion(proposal);
			int start= getPrefixCompletionOffset(proposal);
			try {
				int prefixLength= fFilterOffset - start;
				int relativeCompletionOffset= Math.min(insertion.length(), prefixLength);
				String prefix= document.get(start, prefixLength);
				if (insertion.toString().startsWith(prefix)) {
					checkWrongCase= false;
					rightCase.add(proposal);
					CharSequence newPostfix= insertion.subSequence(relativeCompletionOffset, insertion.length());
					if (rightCasePostfix == null)
						rightCasePostfix= new StringBuffer(newPostfix.toString());
					else
						truncatePostfix(rightCasePostfix, newPostfix);
				} else if (checkWrongCase) {
					CharSequence newPrefix= insertion.subSequence(0, relativeCompletionOffset);
					if (isPrefixCompatible(wrongCasePrefix, wrongCasePrefixStart, newPrefix, start, document)) {
						wrongCasePrefix= newPrefix;
						wrongCasePrefixStart= start;
						CharSequence newPostfix= insertion.subSequence(relativeCompletionOffset, insertion.length());
						if (wrongCasePostfix == null)
							wrongCasePostfix= new StringBuffer(newPostfix.toString());
						else
							truncatePostfix(wrongCasePostfix, newPostfix);
						wrongCase.add(proposal);
					} else {
						checkWrongCase= false;
					}
				}
			} catch (BadLocationException e2) {
				// bail out silently
				return false;
			}

			if (rightCasePostfix != null && rightCasePostfix.length() == 0 && rightCase.size() > 1)
				return false;
		}

		// 2: replace single proposals

		if (rightCase.size() == 1) {
			ICompletionProposal proposal= (ICompletionProposal) rightCase.get(0);
			if (canAutoInsert(proposal)) {
				insertProposal(proposal, (char) 0, 0, fInvocationOffset);
				hide();
				return true;
			}
			return false;
		} else if (checkWrongCase && wrongCase.size() == 1) {
			ICompletionProposal proposal= (ICompletionProposal) wrongCase.get(0);
			if (canAutoInsert(proposal)) {
				insertProposal(proposal, (char) 0, 0, fInvocationOffset);
				hide();
			return true;
			}
			return false;
		}

		// 3: replace post- / prefixes

		CharSequence prefix;
		if (checkWrongCase)
			prefix= wrongCasePrefix;
		else
			prefix= "";  //$NON-NLS-1$

		CharSequence postfix;
		if (checkWrongCase)
			postfix= wrongCasePostfix;
		else
			postfix= rightCasePostfix;

		if (prefix == null || postfix == null)
			return false;

		try {
			// 4: check if parts of the postfix are already in the document
			int to= Math.min(document.getLength(), fFilterOffset + postfix.length());
			StringBuffer inDocument= new StringBuffer(document.get(fFilterOffset, to - fFilterOffset));
			truncatePostfix(inDocument, postfix);

			// 5: replace and reveal
			document.replace(fFilterOffset - prefix.length(), prefix.length() + inDocument.length(), prefix.toString() + postfix.toString());

			fContentAssistSubjectControlAdapter.setSelectedRange(fFilterOffset + postfix.length(), 0);
			fContentAssistSubjectControlAdapter.revealRange(fFilterOffset + postfix.length(), 0);

			return false;
		} catch (BadLocationException e) {
			// ignore and return false
			return false;
		}
	}

	/**
	 * @since 3.1
	 */
	private boolean isPrefixCompatible(CharSequence oneSequence, int oneOffset, CharSequence twoSequence, int twoOffset, IDocument document) throws BadLocationException {
		if (oneSequence == null || twoSequence == null)
			return true;

		int min= Math.min(oneOffset, twoOffset);
		int oneEnd= oneOffset + oneSequence.length();
		int twoEnd= twoOffset + twoSequence.length();

		String one= document.get(oneOffset, min - oneOffset) + oneSequence + document.get(oneEnd, Math.min(fFilterOffset, fFilterOffset - oneEnd));
		String two= document.get(twoOffset, min - twoOffset) + twoSequence + document.get(twoEnd, Math.min(fFilterOffset, fFilterOffset - twoEnd));

		return one.equals(two);
	}

	/**
	 * Truncates <code>buffer</code> to the common prefix of <code>buffer</code>
	 * and <code>sequence</code>.
	 *
	 * @param buffer the common postfix to truncate
	 * @param sequence the characters to truncate with
	 */
	private void truncatePostfix(StringBuffer buffer, CharSequence sequence) {
		// find common prefix
		int min= Math.min(buffer.length(), sequence.length());
		for (int c= 0; c < min; c++) {
			if (sequence.charAt(c) != buffer.charAt(c)) {
				buffer.delete(c, buffer.length());
				return;
			}
		}

		// all equal up to minimum
		buffer.delete(min, buffer.length());
	}

	/**
	 * Extracts the completion offset of an <code>ICompletionProposal</code>. If
	 * <code>proposal</code> is a <code>ICompletionProposalExtension3</code>, its
	 * <code>getCompletionOffset</code> method is called, otherwise, the invocation
	 * offset of this popup is shown.
	 *
	 * @param proposal the proposal to extract the offset from
	 * @return the proposals completion offset, or <code>fInvocationOffset</code>
	 * @since 3.1
	 */
	private int getPrefixCompletionOffset(ICompletionProposal proposal) {
		if (proposal instanceof ICompletionProposalExtension3)
			return ((ICompletionProposalExtension3) proposal).getPrefixCompletionStart(fContentAssistSubjectControlAdapter.getDocument(), fFilterOffset);
		return fInvocationOffset;
	}

	/**
	 * Extracts the replacement string from an <code>ICompletionProposal</code>.
	 *  If <code>proposal</code> is a <code>ICompletionProposalExtension3</code>, its
	 * <code>getCompletionText</code> method is called, otherwise, the display
	 * string is used.
	 *
	 * @param proposal the proposal to extract the text from
	 * @return the proposals completion text
	 * @since 3.1
	 */
	private CharSequence getPrefixCompletion(ICompletionProposal proposal) {
		CharSequence insertion= null;
		if (proposal instanceof ICompletionProposalExtension3)
			insertion= ((ICompletionProposalExtension3) proposal).getPrefixCompletionText(fContentAssistSubjectControlAdapter.getDocument(), fFilterOffset);

		if (insertion == null)
			insertion= proposal.getDisplayString();

		return insertion;
	}
}
