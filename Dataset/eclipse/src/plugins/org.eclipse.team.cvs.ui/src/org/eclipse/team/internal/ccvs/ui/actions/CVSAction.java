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
package org.eclipse.team.internal.ccvs.ui.actions;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.*;
import org.eclipse.team.internal.ccvs.ui.repo.RepositoryManager;
import org.eclipse.team.internal.ui.Utils;
import org.eclipse.team.internal.ui.actions.TeamAction;
import org.eclipse.team.internal.ui.dialogs.IPromptCondition;
import org.eclipse.ui.*;
import org.eclipse.ui.actions.RetargetAction;
import org.eclipse.ui.commands.*;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.ide.ResourceUtil;

/**
 * CVSAction is the common superclass for all CVS actions. It provides
 * facilities for enablement handling, standard error handling, selection
 * retrieval and prompting.
 */
abstract public class CVSAction extends TeamAction implements IEditorActionDelegate, IHandler {
	
	private List accumulatedStatus = new ArrayList();
	private RetargetAction retargetAction;
	private IAction action;
	
	public CVSAction() {
		super();
	}
	
	/**
	 * Initializes a retarget action that will listen to part changes and allow parts to
	 * override this action's behavior. The retarget action is used if this
	 * action is shown in a top-level menu or toolbar.
	 * @param window the workbench window showing this action
	 * @since 3.1
	 */
	private void initializeRetargetAction(IWorkbenchWindow window) {
		// Don't need to specify a the title because it will use this actions
		// title instead.
		retargetAction = new RetargetAction(getId(), ""); //$NON-NLS-1$
		retargetAction.addPropertyChangeListener(new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				if (event.getProperty().equals(IAction.ENABLED)) {
					Object val = event.getNewValue();
					if (val instanceof Boolean && action != null) {
						action.setEnabled(((Boolean) val).booleanValue());
					}
				} else if (event.getProperty().equals(IAction.CHECKED)) {
					Object val = event.getNewValue();
					if (val instanceof Boolean && action != null) {
						action.setChecked(((Boolean) val).booleanValue());
					}
				} else if (event.getProperty().equals(IAction.TEXT)) {
					Object val = event.getNewValue();
					if (val instanceof String && action != null) {
						action.setText((String) val);
					}
				} else if (event.getProperty().equals(IAction.TOOL_TIP_TEXT)) {
					Object val = event.getNewValue();
					if (val instanceof String && action != null) {
						action.setToolTipText((String) val);
					}
				} else if (event.getProperty().equals(SubActionBars.P_ACTION_HANDLERS)) {
					if(action != null && retargetAction != null) {
						action.setEnabled(retargetAction.isEnabled());
					}
				}
			}
		});
		window.getPartService().addPartListener(retargetAction);
		IWorkbenchPart activePart = window.getPartService().getActivePart();
		if (activePart != null)
			retargetAction.partActivated(activePart);
	}

	/**
	 * Common run method for all CVS actions.
	 */
	final public void run(IAction action) {
		try {
			if (!beginExecution(action)) return;			
			// If the action has been replaced by another handler, then
			// call that one instead.
			if(retargetAction != null && retargetAction.getActionHandler() != null) {
				retargetAction.run();
			} else {
				execute(action);
			}
			endExecution();
		} catch (InvocationTargetException e) {
			// Handle the exception and any accumulated errors
			handle(e);
		} catch (InterruptedException e) {
			// Show any problems that have occured so far
			handle(null);
		}  catch (TeamException e) {
			// Handle the exception and any accumulated errors
			handle(e);
		}
	}
	
	/**
	 * Return the command and retarget action id for this action. This is used to
	 *match retargetable actions and allow keybindings.
	 *
	 * @return the id for this action
	 * @since 3.1
	 */
	public String getId() {
		return ""; //$NON-NLS-1$
	}
	
	/**
	 * Called when this action is added to a top-level menu or toolbar (e.g. IWorkbenchWindowDelegate)
	 * @since 3.1
	 */
	public void init(IWorkbenchWindow window) {
		super.init(window);
		initializeRetargetAction(window);
	}
	
	protected boolean isEnabled() throws TeamException {
		if(retargetAction != null && retargetAction.getActionHandler() != null) {
			return retargetAction.isEnabled();
		}
		// don't know so let subclasses decide
		return false;
	}
	
	public void dispose() {
		super.dispose();
        IWorkbenchWindow window = getWindow();
        if (window != null) {
            IPartService partService = window.getPartService();
            if (partService != null)
                partService.removePartListener(retargetAction);
        }
        
        if(retargetAction != null) {
        	retargetAction.dispose();
        	retargetAction = null;
        }
	}
	
	public void selectionChanged(final IAction action, ISelection selection) {
		super.selectionChanged(action, selection);
		this.action = action;
	}
	
	protected void setActionEnablement(IAction action) {
		if(retargetAction != null && retargetAction.getActionHandler() != null) {
			action.setEnabled(retargetAction.isEnabled());
		} else {
			super.setActionEnablement(action);
		}
	}

	/**
	 * This method gets invoked before the <code>CVSAction#execute(IAction)</code>
	 * method. It can preform any prechecking and initialization required before 
	 * the action is executed. Sunclasses may override but must invoke this
	 * inherited method to ensure proper initialization of this superclass is performed.
	 * These included prepartion to accumulate IStatus and checking for dirty editors.
	 */
	protected boolean beginExecution(IAction action) throws TeamException {
		accumulatedStatus.clear();
		if(needsToSaveDirtyEditors()) {
			if(!saveAllEditors()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Actions must override to do their work.
	 */
	abstract protected void execute(IAction action) throws InvocationTargetException, InterruptedException;

	/**
	 * This method gets invoked after <code>CVSAction#execute(IAction)</code>
	 * if no exception occured. Sunclasses may override but should invoke this
	 * inherited method to ensure proper handling oy any accumulated IStatus.
	 */
	protected void endExecution() throws TeamException {
		if ( ! accumulatedStatus.isEmpty()) {
			handle(null);
		}
	}
	
	/**
	 * Add a status to the list of accumulated status. 
	 * These will be provided to method handle(Exception, IStatus[])
	 * when the action completes.
	 */
	protected void addStatus(IStatus status) {
		accumulatedStatus.add(status);
	}
	
	/**
	 * Return the list of status accumulated so far by the action. This
	 * will include any OK status that were added using addStatus(IStatus)
	 */
	protected IStatus[] getAccumulatedStatus() {
		return (IStatus[]) accumulatedStatus.toArray(new IStatus[accumulatedStatus.size()]);
	}
	
	/**
	 * Return the title to be displayed on error dialogs.
	 * Sunclasses should override to present a custon message.
	 */
	protected String getErrorTitle() {
		return CVSUIMessages.CVSAction_errorTitle; //$NON-NLS-1$
	}
	
	/**
	 * Return the title to be displayed on error dialogs when warnigns occur.
	 * Sunclasses should override to present a custon message.
	 */
	protected String getWarningTitle() {
		return CVSUIMessages.CVSAction_warningTitle; //$NON-NLS-1$
	}

	/**
	 * Return the message to be used for the parent MultiStatus when 
	 * mulitple errors occur during an action.
	 * Sunclasses should override to present a custon message.
	 */
	protected String getMultiStatusMessage() {
		return CVSUIMessages.CVSAction_multipleProblemsMessage; //$NON-NLS-1$
	}
	
	/**
	 * Return the status to be displayed in an error dialog for the given list
	 * of non-OK status.
	 * 
	 * This method can be overridden bu subclasses. Returning an OK status will 
	 * prevent the error dialog from being shown.
	 */
	protected IStatus getStatusToDisplay(IStatus[] problems) {
		if (problems.length == 1) {
			return problems[0];
		}
		MultiStatus combinedStatus = new MultiStatus(CVSUIPlugin.ID, 0, getMultiStatusMessage(), null); //$NON-NLS-1$
		for (int i = 0; i < problems.length; i++) {
			combinedStatus.merge(problems[i]);
		}
		return combinedStatus;
	}
	
	/**
	 * Method that implements generic handling of an exception. 
	 * 
	 * Thsi method will also use any accumulated status when determining what
	 * information (if any) to show the user.
	 * 
	 * @param exception the exception that occured (or null if none occured)
	 * @param status any status accumulated by the action before the end of 
	 * the action or the exception occured.
	 */
	protected void handle(Exception exception) {
		// Get the non-OK statii
		List problems = new ArrayList();
		IStatus[] status = getAccumulatedStatus();
		if (status != null) {
			for (int i = 0; i < status.length; i++) {
				IStatus iStatus = status[i];
				if ( ! iStatus.isOK() || iStatus.getCode() == CVSStatus.SERVER_ERROR) {
					problems.add(iStatus);
				}
			}
		}
		// Handle the case where there are no problem statii
		if (problems.size() == 0) {
			if (exception == null) return;
			handle(exception, getErrorTitle(), null);
			return;
		}

		// For now, display both the exception and the problem status
		// Later, we can determine how to display both together
		if (exception != null) {
			handle(exception, getErrorTitle(), null);
		}
		
		String message = null;
		IStatus statusToDisplay = getStatusToDisplay((IStatus[]) problems.toArray(new IStatus[problems.size()]));
		if (statusToDisplay.isOK()) return;
		if (statusToDisplay.isMultiStatus() && statusToDisplay.getChildren().length == 1) {
			message = statusToDisplay.getMessage();
			statusToDisplay = statusToDisplay.getChildren()[0];
		}
		String title;
		if (statusToDisplay.getSeverity() == IStatus.ERROR) {
			title = getErrorTitle();
		} else {
			title = getWarningTitle();
		}
		CVSUIPlugin.openError(getShell(), title, message, new CVSException(statusToDisplay));
	}

	/**
	 * Convenience method for running an operation with the appropriate progress.
	 * Any exceptions are propogated so they can be handled by the
	 * <code>CVSAction#run(IAction)</code> error handling code.
	 * 
	 * @param runnable  the runnable which executes the operation
	 * @param cancelable  indicate if a progress monitor should be cancelable
	 * @param progressKind  one of PROGRESS_BUSYCURSOR or PROGRESS_DIALOG
	 */
	final protected void run(final IRunnableWithProgress runnable, boolean cancelable, int progressKind) throws InvocationTargetException, InterruptedException {
		final Exception[] exceptions = new Exception[] {null};
		
		// Ensure that no repository view refresh happens until after the action
		final IRunnableWithProgress innerRunnable = new IRunnableWithProgress() {
			public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
				getRepositoryManager().run(runnable, monitor);
			}
		};
		
		switch (progressKind) {
			case PROGRESS_BUSYCURSOR :
				BusyIndicator.showWhile(Display.getCurrent(), new Runnable() {
					public void run() {
						try {
							innerRunnable.run(new NullProgressMonitor());
						} catch (InvocationTargetException e) {
							exceptions[0] = e;
						} catch (InterruptedException e) {
							exceptions[0] = e;
						}
					}
				});
				break;
			case PROGRESS_DIALOG :
			default :
				new ProgressMonitorDialog(getShell()).run(cancelable, cancelable, innerRunnable);
				break;
		}
		if (exceptions[0] != null) {
			if (exceptions[0] instanceof InvocationTargetException)
				throw (InvocationTargetException)exceptions[0];
			else
				throw (InterruptedException)exceptions[0];
		}
	}
	
	/**
	 * Answers if the action would like dirty editors to saved
	 * based on the CVS preference before running the action. By
	 * default, CVSActions do not save dirty editors.
	 */
	protected boolean needsToSaveDirtyEditors() {
		return false;
	}
	
	/**
	 * Returns the selected CVS resources
	 */
	protected ICVSResource[] getSelectedCVSResources() {
		ArrayList resources = null;
		if (!selection.isEmpty()) {
			resources = new ArrayList();
			Iterator elements = selection.iterator();
			while (elements.hasNext()) {
				Object next = elements.next();
				if (next instanceof ICVSResource) {
					resources.add(next);
					continue;
				}
				if (next instanceof IAdaptable) {
					IAdaptable a = (IAdaptable) next;
					Object adapter = a.getAdapter(ICVSResource.class);
					if (adapter instanceof ICVSResource) {
						resources.add(adapter);
						continue;
					}
				}
			}
		}
		if (resources != null && !resources.isEmpty()) {
			return (ICVSResource[])resources.toArray(new ICVSResource[resources.size()]);
		}
		return new ICVSResource[0];
	}

	/**
	 * Get selected CVS remote folders
	 */
	protected ICVSRemoteFolder[] getSelectedRemoteFolders() {
		ArrayList resources = null;
		if (!selection.isEmpty()) {
			resources = new ArrayList();
			Iterator elements = selection.iterator();
			while (elements.hasNext()) {
				Object next = elements.next();
				if (next instanceof ICVSRemoteFolder) {
					resources.add(next);
					continue;
				}
				if (next instanceof IAdaptable) {
					IAdaptable a = (IAdaptable) next;
					Object adapter = a.getAdapter(ICVSRemoteFolder.class);
					if (adapter instanceof ICVSRemoteFolder) {
						resources.add(adapter);
						continue;
					}
				}
			}
		}
		if (resources != null && !resources.isEmpty()) {
			return (ICVSRemoteFolder[])resources.toArray(new ICVSRemoteFolder[resources.size()]);
		}
		return new ICVSRemoteFolder[0];
	}

	/**
	 * Returns the selected remote resources
	 */
	protected ICVSRemoteResource[] getSelectedRemoteResources() {
		ArrayList resources = null;
		if (!selection.isEmpty()) {
			resources = new ArrayList();
			Iterator elements = selection.iterator();
			while (elements.hasNext()) {
				Object next = elements.next();
				if (next instanceof ICVSRemoteResource) {
					resources.add(next);
					continue;
				}
				if (next instanceof ILogEntry) {
					resources.add(((ILogEntry)next).getRemoteFile());
					continue;
				}
				if (next instanceof IAdaptable) {
					IAdaptable a = (IAdaptable) next;
					Object adapter = a.getAdapter(ICVSRemoteResource.class);
					if (adapter instanceof ICVSRemoteResource) {
						resources.add(adapter);
						continue;
					}
				}
			}
		}
		if (resources != null && !resources.isEmpty()) {
			ICVSRemoteResource[] result = new ICVSRemoteResource[resources.size()];
			resources.toArray(result);
			return result;
		}
		return new ICVSRemoteResource[0];
	}
		
	/**
	 * A helper prompt condition for prompting for CVS dirty state.
	 */
	public static IPromptCondition getOverwriteLocalChangesPrompt(final IResource[] dirtyResources) {
		return new IPromptCondition() {
			List resources = Arrays.asList(dirtyResources);
			public boolean needsPrompt(IResource resource) {
				return resources.contains(resource);
			}
			public String promptMessage(IResource resource) {
				return NLS.bind(CVSUIMessages.ReplaceWithAction_localChanges, new String[] { resource.getName() });//$NON-NLS-1$
			}
		};
	}
		
	/**
	 * Checks if a the resources' parent's tags are different then the given tag. 
	 * Prompts the user that they are adding mixed tags and returns <code>true</code> if 
	 * the user wants to continue or <code>false</code> otherwise.
	 */
	public static boolean checkForMixingTags(final Shell shell, IResource[] resources, final CVSTag tag) throws CVSException {
		final IPreferenceStore store = CVSUIPlugin.getPlugin().getPreferenceStore();
		if(!store.getBoolean(ICVSUIConstants.PREF_PROMPT_ON_MIXED_TAGS)) {
			return true;
		};
		
		final boolean[] result = new boolean[] { true };
		
		for (int i = 0; i < resources.length; i++) {
			IResource resource = resources[i];
			if (resource.getType() != IResource.PROJECT) {
				ICVSResource cvsResource = CVSWorkspaceRoot.getCVSResourceFor(resource);
				CVSTag parentTag = cvsResource.getParent().getFolderSyncInfo().getTag();
				// prompt if the tags are not equal
				// consider BASE to be equal the parent tag since we don't make BASE sticky on replace
				if (!CVSTag.equalTags(tag, parentTag) && !CVSTag.equalTags(tag, CVSTag.BASE)) {
					shell.getDisplay().syncExec(new Runnable() {
						public void run() {							
							AvoidableMessageDialog dialog = new AvoidableMessageDialog(
									shell,
									CVSUIMessages.CVSAction_mixingTagsTitle,  //$NON-NLS-1$
									null,	// accept the default window icon
									NLS.bind(CVSUIMessages.CVSAction_mixingTags, new String[] { tag.getName() }),  //$NON-NLS-1$
									MessageDialog.QUESTION, 
									new String[] {IDialogConstants.OK_LABEL, IDialogConstants.CANCEL_LABEL}, 
									0);
									
							result[0] = dialog.open() == 0;
							if(result[0] && dialog.isDontShowAgain()) {
								store.setValue(ICVSUIConstants.PREF_PROMPT_ON_MIXED_TAGS, false);
							}																				
						}
					});
					// only prompt once
					break;										
				}
			}
		}
		return result[0];
	}
	
	/**
	 * Based on the CVS preference for saving dirty editors this method will either
	 * ignore dirty editors, save them automatically, or prompt the user to save them.
	 * 
	 * @return <code>true</code> if the command succeeded, and <code>false</code>
	 * if at least one editor with unsaved changes was not saved
	 */
	private boolean saveAllEditors() {
		final int option = CVSUIPlugin.getPlugin().getPreferenceStore().getInt(ICVSUIConstants.PREF_SAVE_DIRTY_EDITORS);
		final boolean[] okToContinue = new boolean[] {true};
		if (option != ICVSUIConstants.OPTION_NEVER) {		
			Display.getDefault().syncExec(new Runnable() {
				public void run() {
					boolean confirm = option == ICVSUIConstants.OPTION_PROMPT;
					IResource[] selectedResources = getSelectedResources();
					if (selectedResources != null) {
						okToContinue[0] = IDE.saveAllEditors(selectedResources, confirm);
					}
				}
			});
		} 
		return okToContinue[0];
	}
	/**
	 * @see org.eclipse.team.internal.ui.actions.TeamAction#handle(java.lang.Exception, java.lang.String, java.lang.String)
	 */
	protected void handle(Exception exception, String title, String message) {
		CVSUIPlugin.openError(getShell(), title, message, exception, CVSUIPlugin.LOG_NONTEAM_EXCEPTIONS);
	}
	
	protected RepositoryManager getRepositoryManager() {
		return CVSUIPlugin.getPlugin().getRepositoryManager();
	}

    /* (non-Javadoc)
     * @see org.eclipse.team.internal.ui.actions.TeamAction#getSelectedResources()
     */
    protected final IResource[] getSelectedResourcesWithOverlap() {
        CVSActionSelectionProperties props = CVSActionSelectionProperties.getProperties(getSelection());
        if (props == null) {
            return Utils.getContributedResources(selection.toArray());
        }
        return props.getAllSelectedResources();
    }
    
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ui.actions.TeamAction#getSelectedResources()
	 */
	protected final IResource[] getSelectedResources() {
        if (selection == null) return new IResource[0];
        CVSActionSelectionProperties props = CVSActionSelectionProperties.getProperties(getSelection());
        if (props == null) {
            return CVSActionSelectionProperties.getNonOverlapping(Utils.getContributedResources(selection.toArray()));
        }
        return props.getNonoverlappingSelectedResources();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.IEditorActionDelegate#setActiveEditor(org.eclipse.jface.action.IAction, org.eclipse.ui.IEditorPart)
	 */
	public void setActiveEditor(IAction action, IEditorPart targetEditor) {
	}
	
	/**
	 * This method is called by the platform UI framework when a command is run for
	 * which this action is the handler. The handler doesn't have an explicit context, for
	 * example unlike a view, editor, or workenchwindow actions, they are not initialized
	 * with a part. As a result when the action is run it will use the selection service
	 * to determine to elements on which to perform the action.
	 * <p>
	 * CVS actions should ensure that they can run without a proxy action. Meaning that
	 * <code>selectionChanged</code> and <code>run</code> should support passing
	 * <code>null</code> as the IAction parameter.
	 * </p>
	 * @param parameterValuesByName
	 * @return
	 * @throws ExecutionException
	 */
	public Object execute(Map parameterValuesByName) throws ExecutionException {
		try {
			IWorkbenchWindow activeWorkbenchWindow = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
			if(activeWorkbenchWindow!= null) {
				IWorkbenchPage activePage = activeWorkbenchWindow.getActivePage();
				if(activePage!= null) {
					IWorkbenchPart part = activePage.getActivePart();
					// If the action is run from within an editor, try and find the 
					// file for the given editor.
					if(part != null && part instanceof IEditorPart) {
						IEditorInput input = ((IEditorPart)part).getEditorInput();
                        IFile file = ResourceUtil.getFile(input);
						if(file != null) {
							selectionChanged((IAction)null, new StructuredSelection(file));
						}
					} else {						
						// Fallback is to prime the action with the selection
						selectionChanged((IAction)null, activePage.getSelection());
					}
					// Safe guard to ensure that the action is only run when enabled. 
					if(isEnabled()) {
						execute((IAction)null);
					} else {
						MessageDialog.openInformation(activeWorkbenchWindow.getShell(), 
								CVSUIMessages.CVSAction_handlerNotEnabledTitle, //$NON-NLS-1$
								CVSUIMessages.CVSAction_handlerNotEnabledMessage); //$NON-NLS-1$
					}
				}
			}
		} catch (InvocationTargetException e) {
			throw new ExecutionException(CVSUIMessages.CVSAction_errorTitle, e); //$NON-NLS-1$
		} catch (InterruptedException e) {
			// Operation was cancelled. Ignore
		} catch (TeamException e) {
			throw new ExecutionException(CVSUIMessages.CVSAction_errorTitle, e); //$NON-NLS-1$
		}
		return null;
	}

	/**
	 * No-op. These handlers don't have any interesting properties.
	 * @return an empty attribute map
	 * @since 3.1
	 */
	public Map getAttributeValuesByName() {
		return new HashMap();
	}

	/**
	 * No-op. These handlers won't have any interesting property changes. There is
	 * no need to notify listeners.
	 * @param handlerListener
	 * @since 3.1
	 */
	public void removeHandlerListener(IHandlerListener handlerListener) {
	}
	public void addHandlerListener(IHandlerListener handlerListener) {
	}
    
    protected final ICVSResource getCVSResourceFor(IResource resource) {
        CVSActionSelectionProperties props = CVSActionSelectionProperties.getProperties(getSelection());
        if (props == null) {
            return CVSWorkspaceRoot.getCVSResourceFor(resource);
        }
        return props.getCVSResourceFor(resource);
    }
}
