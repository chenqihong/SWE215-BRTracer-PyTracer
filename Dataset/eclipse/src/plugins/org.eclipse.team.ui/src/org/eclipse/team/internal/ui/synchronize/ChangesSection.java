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
package org.eclipse.team.internal.ui.synchronize;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.team.core.ITeamStatus;
import org.eclipse.team.core.synchronize.*;
import org.eclipse.team.internal.ui.*;
import org.eclipse.team.ui.ISharedImages;
import org.eclipse.team.ui.synchronize.*;
import org.eclipse.ui.forms.HyperlinkGroup;
import org.eclipse.ui.forms.events.HyperlinkAdapter;
import org.eclipse.ui.forms.events.HyperlinkEvent;
import org.eclipse.ui.forms.widgets.FormToolkit;
import org.eclipse.ui.forms.widgets.Hyperlink;
import org.eclipse.ui.part.PageBook;

/**
 * Section shown in a participant page to show the changes for this participant. This
 * includes a diff viewer for browsing the changes.
 * 
 * @since 3.0
 */
public class ChangesSection extends Composite {
	
	private ISynchronizeParticipant participant;
	private SyncInfoSetSynchronizePage page;
	private FormToolkit forms;
			
	/**
	 * Page book either shows the diff tree viewer if there are changes or
	 * shows a message to the user if there are no changes that would be
	 * shown in the tree.
	 */
	private PageBook changesSectionContainer;
	
	/**
	 * Shows message to user is no changes are to be shown in the diff
	 * tree viewer.
	 */
	private Composite filteredContainer;
	
	/**
	 * Diff tree viewer that shows synchronization changes. This is created
	 * by the participant.
	 */
	private Viewer changesViewer;
	
	/**
	 * Boolean that indicates whether the error page is being shown.
	 * This is used to avoid redrawing the error page when new events come in
	 */
	private boolean showingError;

	/**
	 * Register an action contribution in order to receive model
	 * change notification so that we can update message to user and totals.
	 */
	private SynchronizePageActionGroup changedListener = new SynchronizePageActionGroup() {
		public void modelChanged(ISynchronizeModelElement root) {
			calculateDescription();
		}
	};
	
	/**
	 * Listener registered with the subscriber sync info set which contains
	 * all out-of-sync resources for the subscriber.
	 */
	private ISyncInfoSetChangeListener subscriberListener = new ISyncInfoSetChangeListener() {
		public void syncInfoSetReset(SyncInfoSet set, IProgressMonitor monitor) {
			// Handled by output set listener
		}
		public void syncInfoChanged(ISyncInfoSetChangeEvent event, IProgressMonitor monitor) {
			calculateDescription();
		}
		public void syncInfoSetErrors(SyncInfoSet set, ITeamStatus[] errors, IProgressMonitor monitor) {
			// Handled by output set listener
		}
	};
	
	/**
	 * Listener registered with the output sync info set which contains
	 * only the visible sync info. 
	 */
	private ISyncInfoSetChangeListener outputSetListener = new ISyncInfoSetChangeListener() {
		public void syncInfoSetReset(SyncInfoSet set, IProgressMonitor monitor) {
			calculateDescription();
		}
		public void syncInfoChanged(ISyncInfoSetChangeEvent event, IProgressMonitor monitor) {
			// Input changed listener will call calculateDescription()
			// The input will then react to output set changes
		}
		public void syncInfoSetErrors(SyncInfoSet set, ITeamStatus[] errors, IProgressMonitor monitor) {
			calculateDescription();
		}
	};
	private ISynchronizePageConfiguration configuration;
	
	/**
	 * Create a changes section on the following page.
	 * 
	 * @param parent the parent control 
	 * @param page the page showing this section
	 */
	public ChangesSection(Composite parent, SyncInfoSetSynchronizePage page, ISynchronizePageConfiguration configuration) {
		super(parent, SWT.NONE);
		this.page = page;
		this.configuration = configuration;
		this.participant = configuration.getParticipant();
		
		GridLayout layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		setLayout(layout);
		GridData data = new GridData(GridData.FILL_BOTH);
		data.grabExcessVerticalSpace = true;
		setLayoutData(data);
		
		forms = new FormToolkit(parent.getDisplay());
		forms.setBackground(getBackgroundColor());
		HyperlinkGroup group = forms.getHyperlinkGroup();
		group.setBackground(getBackgroundColor());
		
		changesSectionContainer = new PageBook(this, SWT.NONE);
		data = new GridData(GridData.FILL_BOTH);
		data.grabExcessHorizontalSpace = true;
		data.grabExcessVerticalSpace = true;
		changesSectionContainer.setLayoutData(data);
	}
	
	public Composite getComposite() {
		return changesSectionContainer;
	}
	
	public void setViewer(Viewer viewer) {
		this.changesViewer = viewer;
		calculateDescription();
		configuration.addActionContribution(changedListener);
		getParticipantSyncInfoSet().addSyncSetChangedListener(subscriberListener);
		getVisibleSyncInfoSet().addSyncSetChangedListener(outputSetListener);
	}
	
	private void calculateDescription() {
		SyncInfoTree syncInfoTree = getVisibleSyncInfoSet();
		if (syncInfoTree.getErrors().length > 0) {
			if (!showingError) {
				TeamUIPlugin.getStandardDisplay().asyncExec(new Runnable() {
					public void run() {
						if (changesSectionContainer.isDisposed()) return;
						if(filteredContainer != null) {
							filteredContainer.dispose();
							filteredContainer = null;
						}
						filteredContainer = getErrorComposite(changesSectionContainer);
						changesSectionContainer.showPage(filteredContainer);
						showingError = true;
					}
				});
			}
			return;
		}
		
		showingError = false;
		if(syncInfoTree.size() == 0) {
			TeamUIPlugin.getStandardDisplay().asyncExec(new Runnable() {
				public void run() {
					if (changesSectionContainer.isDisposed()) return;
					if(filteredContainer != null) {
						filteredContainer.dispose();
						filteredContainer = null;
					}
					filteredContainer = getEmptyChangesComposite(changesSectionContainer);
					changesSectionContainer.showPage(filteredContainer);
				}
			});
		} else {
			TeamUIPlugin.getStandardDisplay().asyncExec(new Runnable() {
				public void run() {
					if(filteredContainer != null) {
						filteredContainer.dispose();
						filteredContainer = null;
					}
					Control control = changesViewer.getControl();
					if (!changesSectionContainer.isDisposed() && !control.isDisposed()) {
						changesSectionContainer.showPage(control);
					}
				}
			});
		}
	}
	
	private boolean isThreeWay() {
		return ISynchronizePageConfiguration.THREE_WAY.equals(configuration.getComparisonType());
	}
	
	private Composite getEmptyChangesComposite(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setBackground(getBackgroundColor());
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		composite.setLayout(layout);
		GridData data = new GridData(GridData.FILL_BOTH);
		data.grabExcessVerticalSpace = true;
		composite.setLayoutData(data);
		
		if(! isThreeWay()) {
			createDescriptionLabel(composite,NLS.bind(TeamUIMessages.ChangesSection_noChanges, new String[] { participant.getName() }));	 //$NON-NLS-1$
			return composite;
		}
		
		SyncInfoSet participantSet = getParticipantSyncInfoSet();
		
		int allChanges = participantSet.size();
		int visibleChanges = getVisibleSyncInfoSet().size();
		
		if(visibleChanges == 0 && allChanges != 0) {
			final int candidateMode = getCandidateMode(participantSet);
			int currentMode = page.getConfiguration().getMode();
			if (candidateMode != currentMode) {
				long numChanges = getChangesInMode(participantSet, candidateMode);
				if (numChanges > 0) {
					String message;
					if(numChanges > 1) {
                        message = NLS.bind(TeamUIMessages.ChangesSection_filterHidesPlural, new String[] { Long.toString(numChanges), Utils.modeToString(candidateMode) });
					} else {
                        message = NLS.bind(TeamUIMessages.ChangesSection_filterHidesSingular, new String[] { Long.toString(numChanges), Utils.modeToString(candidateMode) });
					}
					message = NLS.bind(TeamUIMessages.ChangesSection_filterHides, new String[] { Utils.modeToString(configuration.getMode()), message });
					
					Label warning = new Label(composite, SWT.NONE);
					warning.setImage(TeamUIPlugin.getPlugin().getImage(ISharedImages.IMG_WARNING_OVR));
					
					Hyperlink link = forms.createHyperlink(composite, NLS.bind(TeamUIMessages.ChangesSection_filterChange, new String[] { Utils.modeToString(candidateMode) }), SWT.WRAP); //$NON-NLS-1$
					link.addHyperlinkListener(new HyperlinkAdapter() {
						public void linkActivated(HyperlinkEvent e) {
							configuration.setMode(candidateMode);
						}
					});
					forms.getHyperlinkGroup().add(link);
					createDescriptionLabel(composite, message);
					return composite;
				}
			}
		}
		// There is no other mode that can be shown so just indicate that there are no changes
		createDescriptionLabel(composite,NLS.bind(TeamUIMessages.ChangesSection_noChanges, new String[] { participant.getName() }));	 //$NON-NLS-1$	
		return composite;
	}

	private long getChangesInMode(SyncInfoSet participantSet, final int candidateMode) {
		long numChanges;
		switch (candidateMode) {
		case ISynchronizePageConfiguration.OUTGOING_MODE:
			numChanges = participantSet.countFor(SyncInfo.OUTGOING, SyncInfo.DIRECTION_MASK);
			break;
		case ISynchronizePageConfiguration.INCOMING_MODE:
			numChanges = participantSet.countFor(SyncInfo.INCOMING, SyncInfo.DIRECTION_MASK);
			break;
		case ISynchronizePageConfiguration.BOTH_MODE:
			numChanges = participantSet.countFor(SyncInfo.INCOMING, SyncInfo.DIRECTION_MASK) 
				+ participantSet.countFor(SyncInfo.OUTGOING, SyncInfo.DIRECTION_MASK);
			break;
		default:
			numChanges = 0;
			break;
		}
		return numChanges;
	}
	
	/*
	 * Return the candidate mode based on the presence of unfiltered changes
	 * and the modes supported by the page.
	 */
	private int getCandidateMode(SyncInfoSet participantSet) {
		SynchronizePageConfiguration configuration = (SynchronizePageConfiguration)page.getConfiguration();
		long outgoingChanges = participantSet.countFor(SyncInfo.OUTGOING, SyncInfo.DIRECTION_MASK);
		if (outgoingChanges > 0) {
			if (configuration.isModeSupported(ISynchronizePageConfiguration.OUTGOING_MODE)) {
				return ISynchronizePageConfiguration.OUTGOING_MODE;
			}
			if (configuration.isModeSupported(ISynchronizePageConfiguration.BOTH_MODE)) {
				return ISynchronizePageConfiguration.BOTH_MODE;
			}
		}
		long incomingChanges = participantSet.countFor(SyncInfo.INCOMING, SyncInfo.DIRECTION_MASK);
		if (incomingChanges > 0) {
			if (configuration.isModeSupported(ISynchronizePageConfiguration.INCOMING_MODE)) {
				return ISynchronizePageConfiguration.INCOMING_MODE;
			}
			if (configuration.isModeSupported(ISynchronizePageConfiguration.BOTH_MODE)) {
				return ISynchronizePageConfiguration.BOTH_MODE;
			}
		}
		return configuration.getMode();
	}
	
	private Label createDescriptionLabel(Composite parent, String text) {
		Label description = new Label(parent, SWT.WRAP);
		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		data.horizontalSpan = 2;
		data.widthHint = 100;
		description.setLayoutData(data);
		description.setText(text);
		description.setBackground(getBackgroundColor());
		return description;
	}
	
	public void dispose() {
		super.dispose();
		forms.dispose();
		configuration.removeActionContribution(changedListener);
		getParticipantSyncInfoSet().removeSyncSetChangedListener(subscriberListener);
		getVisibleSyncInfoSet().removeSyncSetChangedListener(outputSetListener);
	}
	
	private Composite getErrorComposite(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setBackground(getBackgroundColor());
		GridLayout layout = new GridLayout();
		layout.numColumns = 2;
		composite.setLayout(layout);
		GridData data = new GridData(GridData.FILL_BOTH);
		data.grabExcessVerticalSpace = true;
		composite.setLayoutData(data);	

		Hyperlink link = new Hyperlink(composite, SWT.WRAP);
		link.setText(TeamUIMessages.ChangesSection_8); //$NON-NLS-1$
		link.addHyperlinkListener(new HyperlinkAdapter() {
			public void linkActivated(HyperlinkEvent e) {
				showErrors();
			}
		});
		link.setBackground(getBackgroundColor());
		link.setUnderlined(true);
		
		link = new Hyperlink(composite, SWT.WRAP);
		link.setText(TeamUIMessages.ChangesSection_9); //$NON-NLS-1$
		link.addHyperlinkListener(new HyperlinkAdapter() {
			public void linkActivated(HyperlinkEvent e) {
				page.reset();
			}
		});
		link.setBackground(getBackgroundColor());
		link.setUnderlined(true);
		
		createDescriptionLabel(composite, NLS.bind(TeamUIMessages.ChangesSection_10, new String[] { participant.getName() })); //$NON-NLS-1$

		return composite;
	}
	
	/* private */ void showErrors() {
		ITeamStatus[] status = getVisibleSyncInfoSet().getErrors();
		String title = TeamUIMessages.ChangesSection_11; //$NON-NLS-1$
		if (status.length == 1) {
			ErrorDialog.openError(getShell(), title, status[0].getMessage(), status[0]);
		} else {
			MultiStatus multi = new MultiStatus(TeamUIPlugin.ID, 0, status, TeamUIMessages.ChangesSection_12, null); //$NON-NLS-1$
			ErrorDialog.openError(getShell(), title, null, multi);
		}
	}
	
	protected Color getBackgroundColor() {
		return getShell().getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND);
	}
	
	/*
	 * Return the sync info set that contains the visible resources
	 */
	private SyncInfoTree getVisibleSyncInfoSet() {
		return (SyncInfoTree)configuration.getProperty(ISynchronizePageConfiguration.P_SYNC_INFO_SET);
	}
	
	/*
	 * Return the sync info set for the participant that contains all the resources
	 * including those that may not be visible due to filters (e.g. mode)
	 */
	private SyncInfoSet getParticipantSyncInfoSet() {
		return (SyncInfoSet)configuration.getProperty(SynchronizePageConfiguration.P_WORKING_SET_SYNC_INFO_SET);
	}
}
