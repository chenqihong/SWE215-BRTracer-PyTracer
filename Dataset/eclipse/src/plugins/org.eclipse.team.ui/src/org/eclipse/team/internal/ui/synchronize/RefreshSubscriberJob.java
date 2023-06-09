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

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.*;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.core.subscribers.Subscriber;
import org.eclipse.team.core.synchronize.SyncInfo;
import org.eclipse.team.core.synchronize.SyncInfoTree;
import org.eclipse.team.internal.core.Assert;
import org.eclipse.team.internal.core.subscribers.SubscriberSyncInfoCollector;
import org.eclipse.team.internal.ui.*;
import org.eclipse.team.ui.synchronize.ISynchronizeManager;
import org.eclipse.team.ui.synchronize.SubscriberParticipant;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.progress.IProgressConstants;
import org.eclipse.ui.progress.UIJob;

/**
 * Job to refresh a {@link Subscriber} in the background. The job can be configured
 * to be re-scheduled and run at a specified interval.
 * <p>
 * The job supports a basic workflow for modal/non-modal usage. If the job is
 * run in the foreground (e.g. in a modal progress dialog) the refresh listeners
 * action is invoked immediately after the refresh is completed. Otherwise the refresh
 * listeners action is associated to the job as a <i>goto</i> action. This will
 * allow the user to select the action in the progress view and run it when they
 * choose.
 * </p>
 * @since 3.0
 */
public final class RefreshSubscriberJob extends Job {
	
    private final static boolean TEST_PROGRESS_VIEW = false;
	/**
	 * Uniquely identifies this type of job. This is used for cancellation.
	 */
	private final static Object FAMILY_ID = new Object();
	
	/**
	 * If true this job will be restarted when it completes 
	 */
	private boolean reschedule = false;
	
	/**
	 * If true a rescheduled refresh job should be retarted when cancelled
	 */
	private boolean restartOnCancel = true; 
	
	/**
	 * The schedule delay used when rescheduling a completed job 
	 */
	private static long scheduleDelay;
	
	/**
	 * The subscribers and resources to refresh.
	 */
	private IResource[] resources;

	/**
	 * The participant that is being refreshed.
	 */
	private SubscriberParticipant participant;
	
	/**
	 * The task name for this refresh. This is usually more descriptive than the
	 * job name.
	 */
	private String taskName;
	
	/**
	 * Refresh started/completed listener for every refresh
	 */
	private static List listeners = new ArrayList(1);
	private static final int STARTED = 1;
	private static final int DONE = 2;
	
	/*
	 * Lock used to sequence refresh jobs
	 */
	private static final ILock lock = Platform.getJobManager().newLock(); 
	
	/*
	 * Constant used for postponement
	 */
	private static final IStatus POSTPONED = new Status(IStatus.CANCEL, TeamUIPlugin.ID, 0, "Scheduled refresh postponed due to conflicting operation", null); //$NON-NLS-1$
	
	/*
	 * Action wrapper which allows the goto action
	 * to be set later. It also handles errors 
	 * that have occurred during the refresh
	 */
	private final class GotoActionWrapper extends WorkbenchAction {
        private ActionFactory.IWorkbenchAction gotoAction;
        private IStatus status;
        public void run() {
            if (status != null && !status.isOK()) {
                ErrorDialog.openError(Utils.getShell(null), null, TeamUIMessages.RefreshSubscriberJob_3, status); //$NON-NLS-1$
            } else if(gotoAction != null) {
        		gotoAction.run();
        	}
        }
        public boolean isEnabled() {
        	if(gotoAction != null) {
        		return gotoAction.isEnabled();
        	}
        	return true;
        }
        public String getText() {
        	if(gotoAction != null) {
        		return gotoAction.getText();
        	}
        	return null;
        }
        public String getToolTipText() {
            if (status != null && !status.isOK()) {
                return status.getMessage();
            }
        	if(gotoAction != null) {
        		return gotoAction.getToolTipText();
        	}
        	return Utils.shortenText(SynchronizeView.MAX_NAME_LENGTH, RefreshSubscriberJob.this.getName());
        }
        public void dispose() {
        	super.dispose();
        	if(gotoAction != null) {
        		gotoAction.dispose();
        	}
        }
        public void setGotoAction(ActionFactory.IWorkbenchAction gotoAction) {
            this.gotoAction = gotoAction;
			setEnabled(isEnabled());
			setToolTipText(getToolTipText());
			gotoAction.addPropertyChangeListener(new IPropertyChangeListener() {
				public void propertyChange(PropertyChangeEvent event) {
					if(event.getProperty().equals(IAction.ENABLED)) {
						Boolean bool = (Boolean) event.getNewValue();
						GotoActionWrapper.this.setEnabled(bool.booleanValue());
					}
				}
			});
        }
        public void setStatus(IStatus status) {
            this.status = status;
        }
    }

    /**
	 * Notification for safely notifying listeners of refresh lifecycle.
	 */
	private abstract class Notification implements ISafeRunnable {
		private IRefreshSubscriberListener listener;
		public void handleException(Throwable exception) {
			// don't log the exception....it is already being logged in Platform#run
		}
		public void run(IRefreshSubscriberListener listener) {
			this.listener = listener;
			Platform.run(this);
		}
		public void run() throws Exception {
			notify(listener);
		}
		/**
		 * Subsclasses overide this method to send an event safely to a lsistener
		 * @param listener
		 */
		protected abstract void notify(IRefreshSubscriberListener listener);
	}
	
	/**
	 * Monitor wrapper that will indicate that the job is cancelled 
	 * if the job is blocking another.
	 */
	private class NonblockingProgressMonitor extends ProgressMonitorWrapper {
		private final RefreshSubscriberJob job;
		private long blockTime;
		private static final int THRESHOLD = 250;
		private boolean wasBlocking = false;
		protected NonblockingProgressMonitor(IProgressMonitor monitor, RefreshSubscriberJob job) {
			super(monitor);
			this.job = job;
		}
		public boolean isCanceled() {
			if (super.isCanceled()) {
				return true;
			}
			if (job.shouldReschedule() && job.isBlocking()) {
				if (blockTime == 0) {
					blockTime = System.currentTimeMillis();
				} else if (System.currentTimeMillis() - blockTime > THRESHOLD) {
					// We've been blocking for too long
					wasBlocking = true;
					return true;
				}
			} else {
				blockTime = 0;
			}
			wasBlocking = false;
			return false;
		}
		public boolean wasBlocking() {
			return wasBlocking;
		}
	}
	
	/**
	 * Create a job to refresh the specified resources with the subscriber.
	 * 
	 * @param participant the subscriber participant 
	 * @param name
	 * @param resources
	 * @param subscriber
	 */
	public RefreshSubscriberJob(SubscriberParticipant participant, String jobName, String taskName, IResource[] resources, IRefreshSubscriberListener listener) {
		super(taskName);
		Assert.isNotNull(resources);
		Assert.isNotNull(participant);
		Assert.isNotNull(resources);
		this.resources = resources;
		this.participant = participant;
		this.taskName = jobName;
		setPriority(Job.DECORATE);
		setRefreshInterval(3600 /* 1 hour */);
		
		// Handle restarting of job if it is configured as a scheduled refresh job.
		addJobChangeListener(new JobChangeAdapter() {
			public void done(IJobChangeEvent event) {
				if(shouldReschedule()) {
					IStatus result = event.getResult();
					if(result.getSeverity() == IStatus.CANCEL && ! restartOnCancel) {					
						return;
					}
					long delay = scheduleDelay;
					if (result == POSTPONED) {
						// Restart in 5 seconds
						delay = 5000;
					}
					RefreshSubscriberJob.this.schedule(delay);
					restartOnCancel = true;
				}
			}
		});		
		if(listener != null)
			initialize(listener);
	}
	
	/**
	 * If a collector is available then run the refresh and the background event processing 
	 * within the same progess group.
	 */
	public boolean shouldRun() {
		// Ensure that any progress shown as a result of this refresh occurs hidden in a progress group.
		return getSubscriber() != null;
	}

	public boolean belongsTo(Object family) {	
		if(family instanceof RefreshSubscriberJob) {
			return ((RefreshSubscriberJob)family).getSubscriber() == getSubscriber();
		} else if (family instanceof SubscriberParticipant) {
			return family == participant;
		} else {
			return (family == getFamily() || family == ISynchronizeManager.FAMILY_SYNCHRONIZE_OPERATION);
		}
	}
	
	public static Object getFamily() {
		return FAMILY_ID;
	}
	
	/**
	 * This is run by the job scheduler. A list of subscribers will be refreshed, errors will not stop the job 
	 * and it will continue to refresh the other subscribers.
	 */
	public IStatus run(IProgressMonitor monitor) {
		// Perform a pre-check for auto-build or manual build jobs
		// when auto-refreshing
		if (shouldReschedule() &&
				(isJobInFamilyRunning(ResourcesPlugin.FAMILY_AUTO_BUILD)
				|| isJobInFamilyRunning(ResourcesPlugin.FAMILY_MANUAL_BUILD))) {
			return POSTPONED;		
		}
		// Only allow one refresh job at a time
		// NOTE: It would be cleaner if this was done by a scheduling
		// rule but at the time of writting, it is not possible due to
		// the scheduling rule containment rules.
		// Acquiring lock to ensure only one refresh job is running at a particular time
		boolean acquired = false;
		try {
			while (!acquired) {
				try {
					acquired = lock.acquire(1000);
				} catch (InterruptedException e1) {
					acquired = false;
				}
				Policy.checkCanceled(monitor);
			}
			Subscriber subscriber = getSubscriber();
			IResource[] roots = getResources();
			
			// if there are no resources to refresh, just return
			if(subscriber == null || roots == null) {
				return Status.OK_STATUS;
			}
			SubscriberSyncInfoCollector collector = getCollector();
			RefreshEvent event = new RefreshEvent(reschedule ? IRefreshEvent.SCHEDULED_REFRESH : IRefreshEvent.USER_REFRESH, roots, collector.getSubscriber());
			RefreshChangeListener changeListener = new RefreshChangeListener(collector);
			IStatus status = null;
			NonblockingProgressMonitor wrappedMonitor = null;
			try {
				event.setStartTime(System.currentTimeMillis());
				if(monitor.isCanceled()) {
					return Status.CANCEL_STATUS;
				}
				// Set-up change listener so that we can determine the changes found
				// during this refresh.						
				subscriber.addListener(changeListener);
				// Pre-Notify
				notifyListeners(STARTED, event);
				// Perform the refresh		
				monitor.setTaskName(getName());
				wrappedMonitor = new NonblockingProgressMonitor(monitor, this);
				subscriber.refresh(roots, IResource.DEPTH_INFINITE, wrappedMonitor);
				// Prepare the results
				setProperty(IProgressConstants.KEEPONE_PROPERTY, Boolean.valueOf(! isJobModal()));
			} catch(OperationCanceledException e2) {
				if (monitor.isCanceled()) {
					// The refresh was cancelled by the user
					status = Status.CANCEL_STATUS;
				} else {
					// The refresh was cancelled due to a blockage or a cancelled authentication
					if (wrappedMonitor != null && wrappedMonitor.wasBlocking()) {
						status = POSTPONED;
					} else {
						status = Status.CANCEL_STATUS;
					}
				}
			} catch(TeamException e) {
			    // Determine the status to be returned and the GOTO action
			    status = e.getStatus();
			    if (!isUser()) {
			        if (!TEST_PROGRESS_VIEW) {
			            // Use the GOTO action to show the error and return OK
			            Object prop = getProperty(IProgressConstants.ACTION_PROPERTY);
			            if (prop instanceof GotoActionWrapper) {
			                GotoActionWrapper wrapper = (GotoActionWrapper)prop;
			                wrapper.setStatus(e.getStatus());
			                status = new Status(IStatus.OK, TeamUIPlugin.ID, IStatus.OK, e.getStatus().getMessage(), e);
			            }
			        }
			    }
// TODO: Code that can be added when new error handling gets released (see bug 76726)
//		        if (!isUser() && status.getSeverity() == IStatus.ERROR) {
//		            // Never prompt for errors on non-user jobs
//		            setProperty(IProgressConstants.NO_IMMEDIATE_ERROR_PROMPT_PROPERTY, Boolean.TRUE);
//		        }
			} finally {
				event.setStopTime(System.currentTimeMillis());
				subscriber.removeListener(changeListener);
			}
			
			// Post-Notify
			event.setChanges(changeListener.getChanges(monitor));
			if (status == null) {
				status = calculateStatus(event);
			}
			event.setStatus(status);
			notifyListeners(DONE, event);
			return event.getStatus();
		} finally {
			if (acquired) lock.release();
            monitor.done();
		}
	}
	
	private boolean isJobInFamilyRunning(Object family) {
		Job[] jobs = Platform.getJobManager().find(family);
		if (jobs != null && jobs.length > 0) {
			for (int i = 0; i < jobs.length; i++) {
				Job job = jobs[i];
				if (job.getState() != Job.NONE) {
					return true;
				}
			}
		}
		return false;
	}

	private IStatus calculateStatus(IRefreshEvent event) {
		StringBuffer text = new StringBuffer();
		int code = IStatus.OK;
		SyncInfo[] changes = event.getChanges();
		SubscriberSyncInfoCollector collector = getCollector();
		if (collector != null) {
			int numChanges = refreshedResourcesContainChanges(event);
			if (numChanges > 0) {
				code = IRefreshEvent.STATUS_CHANGES;
				if (changes.length > 0) {
				// New changes found
					String numNewChanges = Integer.toString(event.getChanges().length);
					if (event.getChanges().length == 1) {
							text.append(NLS.bind(TeamUIMessages.RefreshCompleteDialog_newChangesSingular, (new Object[]{getName(), numNewChanges}))); //$NON-NLS-1$
					} else {
							text.append(NLS.bind(TeamUIMessages.RefreshCompleteDialog_newChangesPlural, (new Object[]{getName(), numNewChanges}))); //$NON-NLS-1$
						}
				} else {
					// Refreshed resources contain changes
					if (numChanges == 1) {
						text.append(NLS.bind(TeamUIMessages.RefreshCompleteDialog_changesSingular, (new Object[]{getName(), new Integer(numChanges)}))); //$NON-NLS-1$
					} else {
						text.append(NLS.bind(TeamUIMessages.RefreshCompleteDialog_changesPlural, (new Object[]{getName(), new Integer(numChanges)}))); //$NON-NLS-1$
					}
				}
			} else {
				// No changes found
				code = IRefreshEvent.STATUS_NO_CHANGES;
				text.append(NLS.bind(TeamUIMessages.RefreshCompleteDialog_6, new String[] { getName() })); //$NON-NLS-1$
			}
			return new Status(IStatus.OK, TeamUIPlugin.ID, code, text.toString(), null);
		}
		return Status.OK_STATUS;
	}
	
	private int refreshedResourcesContainChanges(IRefreshEvent event) {
		int numChanges = 0;
		SubscriberSyncInfoCollector collector = getCollector();
		if (collector != null) {
			SyncInfoTree set = collector.getSyncInfoSet();
			IResource[] resources = event.getResources();
			for (int i = 0; i < resources.length; i++) {
				IResource resource = resources[i];
				SyncInfo[] infos = set.getSyncInfos(resource, IResource.DEPTH_INFINITE);
				if(infos != null && infos.length > 0) {
					numChanges += infos.length;
				}
			}
		}
		return numChanges;
	}
	
	private void initialize(final IRefreshSubscriberListener listener) {
		final GotoActionWrapper actionWrapper = new GotoActionWrapper();
		
		IProgressMonitor group = Platform.getJobManager().createProgressGroup();
		group.beginTask(taskName, 100);
		setProgressGroup(group, 80);
		getCollector().setProgressGroup(group, 20);
		setProperty(IProgressConstants.ICON_PROPERTY, participant.getImageDescriptor());
		setProperty(IProgressConstants.ACTION_PROPERTY, actionWrapper);
		setProperty(IProgressConstants.KEEPONE_PROPERTY, Boolean.valueOf(! isJobModal()));
		// Listener delagate
		IRefreshSubscriberListener autoListener = new IRefreshSubscriberListener() {
			public void refreshStarted(IRefreshEvent event) {
				if(listener != null) {
					listener.refreshStarted(event);
				}
			}
			public ActionFactory.IWorkbenchAction refreshDone(IRefreshEvent event) {
				if(listener != null) {
					boolean isModal = isJobModal();
					final ActionFactory.IWorkbenchAction runnable = listener.refreshDone(event);
					if(runnable != null) {
						// If the job is being run modally then simply prompt the user immediatly
						if(isModal) {
							if(runnable != null) {
								Job update = new UIJob("") { //$NON-NLS-1$
									public IStatus runInUIThread(IProgressMonitor monitor) {
									    runnable.run();
										return Status.OK_STATUS;
									}
								};
								update.setSystem(true);
								update.schedule();
							}
						} else {
							// If the job is being run in the background, don't interrupt the user and simply update the goto action
							// to perform the results.
							actionWrapper.setGotoAction(runnable);
						}
					}
					RefreshSubscriberJob.removeRefreshListener(this);
				}
				return null;
			}
		};
		
		if (listener != null) {
			RefreshSubscriberJob.addRefreshListener(autoListener);
		}	
	}
	
	protected IResource[] getResources() {
		return resources;
	}
	
	protected Subscriber getSubscriber() {
		return participant.getSubscriber();
	}
	
	protected SubscriberSyncInfoCollector getCollector() {
		return participant.getSubscriberSyncInfoCollector();
	}
	
	public long getScheduleDelay() {
		return scheduleDelay;
	}
	
	protected void start() {
		if(getState() == Job.NONE) {
			if(shouldReschedule()) {
				schedule(getScheduleDelay());
			}
		}
	}
	
	/**
	 * Specify the interval in seconds at which this job is scheduled.
	 * @param seconds delay specified in seconds
	 */
	public void setRefreshInterval(long seconds) {
		boolean restart = false;
		if(getState() == Job.SLEEPING) {
			restart = true;
			cancel();
		}
		scheduleDelay = seconds * 1000;
		if(restart) {
			start();
		}
	}
	
	public void setRestartOnCancel(boolean restartOnCancel) {
		this.restartOnCancel = restartOnCancel;
	}
	
	public void setReschedule(boolean reschedule) {
		this.reschedule = reschedule;
	}
	
	public boolean shouldReschedule() {
		return reschedule;
	}
	
	public static void addRefreshListener(IRefreshSubscriberListener listener) {
		synchronized(listeners) {
			if(! listeners.contains(listener)) {
				listeners.add(listener);
			}
		}
	}
	
	public static void removeRefreshListener(IRefreshSubscriberListener listener) {
		synchronized(listeners) {
			listeners.remove(listener);
		}
	}
	
	protected void notifyListeners(final int state, final IRefreshEvent event) {
		// Get a snapshot of the listeners so the list doesn't change while we're firing
		IRefreshSubscriberListener[] listenerArray;
		synchronized (listeners) {
			listenerArray = (IRefreshSubscriberListener[]) listeners.toArray(new IRefreshSubscriberListener[listeners.size()]);
		}
		// Notify each listener in a safe manner (i.e. so their exceptions don't kill us)
		for (int i = 0; i < listenerArray.length; i++) {
			IRefreshSubscriberListener listener = listenerArray[i];
			Notification notification = new Notification() {
				protected void notify(IRefreshSubscriberListener listener) {
					switch (state) {
						case STARTED:
							listener.refreshStarted(event);
							break;
						case DONE:
							listener.refreshDone(event);
							break;
						default:
							break;
					}
				}
			};
			notification.run(listener);
		}
	}
	
	private boolean isJobModal() {
		Boolean isModal = (Boolean)getProperty(IProgressConstants.PROPERTY_IN_DIALOG);
		if(isModal == null) return false;
		return isModal.booleanValue();
	}
}
