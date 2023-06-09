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
package org.eclipse.core.internal.resources;

import org.eclipse.core.internal.utils.Messages;
import org.eclipse.core.internal.utils.Policy;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceStatus;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.*;

/**
 * Used to track operation state for each thread that is involved in an
 * operation. This includes prepared and running operation depth, auto-build
 * strategy and cancel state.
 */
public class WorkManager implements IManager {
	/**
	 * Scheduling rule for use during resource change notification. This rule
	 * must always be allowed to nest within a resource rule of any granularity
	 * since it is used from within the scope of all resource changing
	 * operations. The purpose of this rule is two-fold: 1. To prevent other
	 * resource changing jobs from being scheduled while the notification is
	 * running 2. To cause an exception if a resource change listener tries to
	 * begin a resource rule during a notification. This also prevents
	 * deadlock, because the notification thread owns the workspace lock, and
	 * threads that own the workspace lock must never block trying to acquire a
	 * resource rule.
	 */
	class NotifyRule implements ISchedulingRule {
		public boolean contains(ISchedulingRule rule) {
			return (rule instanceof IResource) || rule.getClass().equals(NotifyRule.class);
		}

		public boolean isConflicting(ISchedulingRule rule) {
			return contains(rule);
		}
	}

	private NotifyRule notifyRule = new NotifyRule();
	/**
	 * Indicates that the last checkIn failed, either due to cancelation or due to the
	 * workspace tree being locked for modifications (during resource change events).
	 */
	private final ThreadLocal checkInFailed = new ThreadLocal();
	/**
	 * Indicates whether any operations have run that may require a build. 
	 */
	private boolean hasBuildChanges = false;
	private IJobManager jobManager;
	private final ILock lock;
	private int nestedOperations = 0;
	private boolean operationCanceled = false;
	private int preparedOperations = 0;
	private Workspace workspace;

	public WorkManager(Workspace workspace) {
		this.workspace = workspace;
		this.jobManager = Platform.getJobManager();
		this.lock = jobManager.newLock();
	}
	
	/**
	 * Releases the workspace lock without changing the nested operation depth.
	 * Must be followed eventually by endUnprotected. Any
	 * beginUnprotected/endUnprotected pair must be done entirely within the
	 * scope of a checkIn/checkOut pair. Returns the old lock depth.
	 * @see #endUnprotected(int)
	 */
	public int beginUnprotected() {
		int depth = lock.getDepth();
		for (int i = 0; i < depth; i++)
			lock.release();
		return depth;
	}

	/**
	 * An operation calls this method and it only returns when the operation is
	 * free to run.
	 */
	public void checkIn(ISchedulingRule rule, IProgressMonitor monitor) throws CoreException {
		boolean success = false;
		try {
			if (workspace.isTreeLocked()) {
				String msg = Messages.resources_cannotModify;
				throw new ResourceException(IResourceStatus.WORKSPACE_LOCKED, null, msg, null);
			}
			jobManager.beginRule(rule, monitor);
			lock.acquire();
			incrementPreparedOperations();
			success = true;
		} finally {
			//remember if we failed to check in, so we can avoid check out
			if (!success)
				checkInFailed.set(Boolean.TRUE);
		}
	}

	/**
	 * Inform that an operation has finished. 
	 */
	public synchronized void checkOut(ISchedulingRule rule) {
		decrementPreparedOperations();
		rebalanceNestedOperations();
		//reset state if this is the end of a top level operation
		if (preparedOperations == 0)
			operationCanceled = hasBuildChanges = false;
		try {
			lock.release();
		} finally {
			//end rule in finally in case lock.release throws an exception
			jobManager.endRule(rule);
		}
	}

	/**
	 * Returns true if the check in for this thread failed, in which case the
	 * check out and other end of operation code should not run.
	 * <p>
	 * The failure flag is reset immediately after calling this method. Subsequent
	 * calls to this method will indicate no failure (unless a new failure has occurred).
	 * @return <code>true</code> if the checkIn failed, and <code>false</code> otherwise.
	 */
	public boolean checkInFailed(ISchedulingRule rule) {
		if (checkInFailed.get() != null) {
			//clear the failure flag for this thread
			checkInFailed.set(null);
			//must still end the rule even in the case of failure
			if (!workspace.isTreeLocked())
				jobManager.endRule(rule);
			return true;
		}
		return false;
	}

	/**
	 * This method can only be safelly called from inside a workspace
	 * operation. Should NOT be called from outside a
	 * prepareOperation/endOperation block.
	 */
	private void decrementPreparedOperations() {
		preparedOperations--;
	}

	/**
	 * Re-acquires the workspace lock that was temporarily released during an
	 * operation, and restores the old lock depth.
	 * @see #beginUnprotected()
	 */
	public void endUnprotected(int depth) {
		for (int i = 0; i < depth; i++)
			lock.acquire();
	}

	/**
	 * Returns the work manager's lock
	 */
	ILock getLock() {
		return lock;
	}
	
	/**
	 * This method can only be safelly called from inside a workspace
	 * operation. Should NOT be called from outside a
	 * prepareOperation/endOperation block.
	 */
	public synchronized int getPreparedOperationDepth() {
		return preparedOperations;
	}

	/**
	 * This method can only be safelly called from inside a workspace
	 * operation. Should NOT be called from outside a
	 * prepareOperation/endOperation block.
	 */
	void incrementNestedOperations() {
		nestedOperations++;
	}

	/**
	 * This method can only be safelly called from inside a workspace
	 * operation. Should NOT be called from outside a
	 * prepareOperation/endOperation block.
	 */
	private void incrementPreparedOperations() {
		preparedOperations++;
	}

	/**
	 * Returns true if the nested operation depth is the same as the prepared
	 * operation depth, and false otherwise. This method can only be safelly
	 * called from inside a workspace operation. Should NOT be called from
	 * outside a prepareOperation/endOperation block.
	 */
	boolean isBalanced() {
		return nestedOperations == preparedOperations;
	}

	/**
	 * This method can only be safelly called from inside a workspace
	 * operation. Should NOT be called from outside a
	 * prepareOperation/endOperation block.
	 */
	public void operationCanceled() {
		operationCanceled = true;
	}

	/**
	 * Used to make things stable again after an operation has failed between a
	 * workspace.prepareOperation() and workspace.beginOperation(). This method
	 * can only be safelly called from inside a workspace operation. Should NOT
	 * be called from outside a prepareOperation/endOperation block.
	 */
	public void rebalanceNestedOperations() {
		nestedOperations = preparedOperations;
	}

	/**
	 * Indicates if the operation that has just completed may potentially
	 * require a build.
	 */
	public void setBuild(boolean hasChanges) {
		hasBuildChanges = hasBuildChanges || hasChanges;
	}

	/**
	 * This method can only be safely called from inside a workspace operation.
	 * Should NOT be called from outside a prepareOperation/endOperation block.
	 */
	public boolean shouldBuild() {
		if (hasBuildChanges) {
			if (operationCanceled)
				return Policy.buildOnCancel;
			return true;
		}
		return false;
	}

	public void shutdown(IProgressMonitor monitor) {
		// do nothing
	}

	public void startup(IProgressMonitor monitor) {
		// do nothing
	}

	/**
	 * Returns true if the workspace lock has already been acquired by this
	 * thread, and false otherwise.
	 */
	public boolean isLockAlreadyAcquired() {
		boolean result = false;
		try {
			boolean success = lock.acquire(0L);
			if (success) {
				//if lock depth is greater than one, then we already owned it
				// before
				result = lock.getDepth() > 1;
				lock.release();
			}
		} catch (InterruptedException e) {
			// ignore
		}
		return result;
	}

	/**
	 * Returns the scheduling rule used during resource change notifications.
	 */
	public ISchedulingRule getNotifyRule() {
		return notifyRule;
	}
}
