/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Julien Ruaux: jruaux@octo.com see bug 25324 Ability to know when tests are finished [junit] 
 *     Vincent Massol: vmassol@octo.com 25324 Ability to know when tests are finished [junit]
 *     Sebastian Davids: sdavids@gmx.de 35762 JUnit View wasting a lot of screen space [JUnit]
 *******************************************************************************/
package org.eclipse.jdt.internal.junit.ui;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionPoint;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.MultiStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.core.runtime.jobs.Job;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.custom.ViewForm;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.ToolBar;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorActionBarContributor;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IPartListener2;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.part.EditorActionBarContributor;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.progress.IWorkbenchSiteProgressService;
import org.eclipse.ui.progress.UIJob;

import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;

import org.eclipse.debug.ui.DebugUITools;

import org.eclipse.jdt.core.ElementChangedEvent;
import org.eclipse.jdt.core.IElementChangedListener;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaElementDelta;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;

import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;

import org.eclipse.jdt.junit.ITestRunListener;

import org.eclipse.jdt.internal.junit.Messages;
import org.eclipse.jdt.internal.junit.launcher.JUnitBaseLaunchConfiguration;

/** 
 * A ViewPart that shows the results of a test run.
 */
public class TestRunnerViewPart extends ViewPart implements ITestRunListener3 {

	public static final String NAME= "org.eclipse.jdt.junit.ResultView"; //$NON-NLS-1$
	public static final String ID_EXTENSION_POINT_TESTRUN_TABS= JUnitPlugin.PLUGIN_ID + "." + "internal_testRunTabs"; //$NON-NLS-1$ //$NON-NLS-2$

	static final int REFRESH_INTERVAL= 200;
 	/**
 	 * Number of executed tests during a test run
 	 */
	protected volatile int fExecutedTests;
	/**
	 * Number of errors during this test run
	 */
	protected volatile int fErrorCount;
	/**
	 * Number of failures during this test run
	 */
	protected volatile int fFailureCount;
	/**
	 * Number of tests run
	 */
	protected volatile int fTestCount;
	/**
	 * Whether the output scrolls and reveals tests as they are executed.
	 */
	protected boolean fAutoScroll = true;
	/**
	 * The current orientation; either <code>VIEW_ORIENTATION_HORIZONTAL</code>
	 * <code>VIEW_ORIENTATION_VERTICAL</code>, or <code>VIEW_ORIENTATION_AUTOMATIC</code>.
	 */
	private int fOrientation= VIEW_ORIENTATION_AUTOMATIC;
	/**
	 * The current orientation; either <code>VIEW_ORIENTATION_HORIZONTAL</code>
	 * <code>VIEW_ORIENTATION_VERTICAL</code>.
	 */
	private int fCurrentOrientation;
	/**
	 * Map storing TestInfos for each executed test keyed by
	 * the test name.
	 */
	private Map fTestInfos= new HashMap();
	/**
	 * The first failure of a test run. Used to reveal the
	 * first failed tests at the end of a run.
	 */
	private List fFailures= new ArrayList();
	
	/** 
	 * Queue used for processing Tree Entries
	 */
	private List fTreeEntryQueue= new ArrayList();
	/**
	 * Indicates an instance of TreeEntryQueueDrainer is already running, or scheduled to
	 */
	private boolean fQueueDrainRequestOutstanding;

	private boolean fTestIsRunning= false;

	protected JUnitProgressBar fProgressBar;
	protected ProgressImages fProgressImages;
	protected Image fViewImage;
	protected CounterPanel fCounterPanel;
	protected boolean fShowOnErrorOnly= false;
	protected Clipboard fClipboard;
	protected volatile String fStatus;

	/** 
	 * The tab that shows the stack trace of a failure
	 */
	private FailureTrace fFailureTrace;
	/** 
	 * The collection of ITestRunTabs
	 */
	protected Vector fTestRunTabs = new Vector();
	/**
	 * The currently active run tab
	 */
	private TestRunTab fActiveRunTab;
	/**
	 * Is the UI disposed
	 */
	private boolean fIsDisposed= false;
	/**
	 * The launched project
	 */
	private IJavaProject fTestProject;
	/**
	 * The launcher that has started the test
	 */
	private String fLaunchMode;
	private ILaunch fLastLaunch;
	
	/**
	 * Actions
	 */
	private Action fRerunLastTestAction;
	private Action fRerunLastFailedFirstAction;
	private ScrollLockAction fScrollLockAction;
	private ToggleOrientationAction[] fToggleOrientationActions;
	private ActivateOnErrorAction fActivateOnErrorAction;
	private IMenuListener fViewMenuListener;
	
	/**
	 * The client side of the remote test runner
	 */
	private RemoteTestRunnerClient fTestRunnerClient;

	final Image fStackViewIcon= TestRunnerViewPart.createImage("eview16/stackframe.gif");//$NON-NLS-1$
	final Image fTestRunOKIcon= TestRunnerViewPart.createImage("eview16/junitsucc.gif"); //$NON-NLS-1$
	final Image fTestRunFailIcon= TestRunnerViewPart.createImage("eview16/juniterr.gif"); //$NON-NLS-1$
	final Image fTestRunOKDirtyIcon= TestRunnerViewPart.createImage("eview16/junitsuccq.gif"); //$NON-NLS-1$
	final Image fTestRunFailDirtyIcon= TestRunnerViewPart.createImage("eview16/juniterrq.gif"); //$NON-NLS-1$
	
	// Persistence tags.
	static final String TAG_PAGE= "page"; //$NON-NLS-1$
	static final String TAG_RATIO= "ratio"; //$NON-NLS-1$
	static final String TAG_TRACEFILTER= "tracefilter"; //$NON-NLS-1$ 
	static final String TAG_ORIENTATION= "orientation"; //$NON-NLS-1$
	static final String TAG_SCROLL= "scroll"; //$NON-NLS-1$
	
	//orientations
	static final int VIEW_ORIENTATION_VERTICAL= 0;
	static final int VIEW_ORIENTATION_HORIZONTAL= 1;
	static final int VIEW_ORIENTATION_AUTOMATIC= 2;
	
	private IMemento fMemento;	

	Image fOriginalViewImage;
	IElementChangedListener fDirtyListener;
	
	
	private CTabFolder fTabFolder;
	private SashForm fSashForm;
	
	private Action fNextAction;
	private Action fPreviousAction;
	private Composite fCounterComposite;
	private Composite fParent;
	private UpdateUIJob fUpdateJob;
	/**
	 * A Job that runs as long as a test run is
	 * running. It is used to get the progress feedback
	 * for running jobs in the view.
	 */
	private JUnitIsRunningJob fJUnitIsRunningJob;
	private ILock fJUnitIsRunningLock;

	public static final Object FAMILY_JUNIT_RUN = new Object();


	private StopAction fStopAction;
	protected boolean fPartIsVisible= false;


	private class StopAction extends Action {
		public StopAction() {
			setText(JUnitMessages.TestRunnerViewPart_stopaction_text);
			setToolTipText(JUnitMessages.TestRunnerViewPart_stopaction_tooltip);
			setDisabledImageDescriptor(JUnitPlugin.getImageDescriptor("dlcl16/stop.gif")); //$NON-NLS-1$
			setHoverImageDescriptor(JUnitPlugin.getImageDescriptor("elcl16/stop.gif")); //$NON-NLS-1$
			setImageDescriptor(JUnitPlugin.getImageDescriptor("elcl16/stop.gif")); //$NON-NLS-1$
		}

		public void run() {
			stopTest();
			setEnabled(false);
		}
	}

	private class RerunLastAction extends Action {
		public RerunLastAction() {
			setText(JUnitMessages.TestRunnerViewPart_rerunaction_label); 
			setToolTipText(JUnitMessages.TestRunnerViewPart_rerunaction_tooltip); 
			setDisabledImageDescriptor(JUnitPlugin.getImageDescriptor("dlcl16/relaunch.gif")); //$NON-NLS-1$
			setHoverImageDescriptor(JUnitPlugin.getImageDescriptor("elcl16/relaunch.gif")); //$NON-NLS-1$
			setImageDescriptor(JUnitPlugin.getImageDescriptor("elcl16/relaunch.gif")); //$NON-NLS-1$
			setEnabled(false);
		}
		
		public void run(){
			rerunTestRun();
		}
	}
	
	private class RerunLastFailedFirstAction extends Action {
		public RerunLastFailedFirstAction() {
			setText(JUnitMessages.TestRunnerViewPart_rerunfailuresaction_label);  
			setToolTipText(JUnitMessages.TestRunnerViewPart_rerunfailuresaction_tooltip);  
			setDisabledImageDescriptor(JUnitPlugin.getImageDescriptor("dlcl16/relaunchf.gif")); //$NON-NLS-1$
			setHoverImageDescriptor(JUnitPlugin.getImageDescriptor("elcl16/relaunchf.gif")); //$NON-NLS-1$
			setImageDescriptor(JUnitPlugin.getImageDescriptor("elcl16/relaunchf.gif")); //$NON-NLS-1$
			setEnabled(false);
		}
		
		public void run(){
			rerunTestFailedFirst();
		}
	}

	private class ToggleOrientationAction extends Action {
		private final int fActionOrientation;
		
		public ToggleOrientationAction(TestRunnerViewPart v, int orientation) {
			super("", AS_RADIO_BUTTON); //$NON-NLS-1$
			if (orientation == TestRunnerViewPart.VIEW_ORIENTATION_HORIZONTAL) {
				setText(JUnitMessages.TestRunnerViewPart_toggle_horizontal_label); 
				setImageDescriptor(JUnitPlugin.getImageDescriptor("elcl16/th_horizontal.gif")); //$NON-NLS-1$				
			} else if (orientation == TestRunnerViewPart.VIEW_ORIENTATION_VERTICAL) {
				setText(JUnitMessages.TestRunnerViewPart_toggle_vertical_label); 
				setImageDescriptor(JUnitPlugin.getImageDescriptor("elcl16/th_vertical.gif")); //$NON-NLS-1$				
			} else if (orientation == TestRunnerViewPart.VIEW_ORIENTATION_AUTOMATIC) {
				setText(JUnitMessages.TestRunnerViewPart_toggle_automatic_label);  
				setImageDescriptor(JUnitPlugin.getImageDescriptor("elcl16/th_automatic.gif")); //$NON-NLS-1$				
			}
			fActionOrientation= orientation;
			PlatformUI.getWorkbench().getHelpSystem().setHelp(this, IJUnitHelpContextIds.RESULTS_VIEW_TOGGLE_ORIENTATION_ACTION);
		}
		
		public int getOrientation() {
			return fActionOrientation;
		}
		
		public void run() {
			if (isChecked()) {
				fOrientation= fActionOrientation;
				computeOrientation();
			}
		}		
	}

	/**
	 * Listen for for modifications to Java elements
	 */
	private class DirtyListener implements IElementChangedListener {
		public void elementChanged(ElementChangedEvent event) {
			processDelta(event.getDelta());				
		}
		
		private boolean processDelta(IJavaElementDelta delta) {
			int kind= delta.getKind();
			int details= delta.getFlags();
			int type= delta.getElement().getElementType();
			
			switch (type) {
				// Consider containers for class files.
				case IJavaElement.JAVA_MODEL:
				case IJavaElement.JAVA_PROJECT:
				case IJavaElement.PACKAGE_FRAGMENT_ROOT:
				case IJavaElement.PACKAGE_FRAGMENT:
					// If we did some different than changing a child we flush the the undo / redo stack.
					if (kind != IJavaElementDelta.CHANGED || details != IJavaElementDelta.F_CHILDREN) {
						codeHasChanged();
						return false;
					}
					break;
				case IJavaElement.COMPILATION_UNIT:
					// if we have changed a primary working copy (e.g created, removed, ...)
					// then we do nothing.
					if ((details & IJavaElementDelta.F_PRIMARY_WORKING_COPY) != 0) 
						return true;
					codeHasChanged();
					return false;
					
				case IJavaElement.CLASS_FILE:
					// Don't examine children of a class file but keep on examining siblings.
					return true;
				default:
					codeHasChanged();
					return false;	
			}
				
			IJavaElementDelta[] affectedChildren= delta.getAffectedChildren();
			if (affectedChildren == null)
				return true;
	
			for (int i= 0; i < affectedChildren.length; i++) {
				if (!processDelta(affectedChildren[i]))
					return false;
			}
			return true;			
		}
	}
	
	private IPartListener2 fPartListener= new IPartListener2() {
		public void partActivated(IWorkbenchPartReference ref) {
		}
		public void partBroughtToTop(IWorkbenchPartReference ref) {
		}
	 	public void partInputChanged(IWorkbenchPartReference ref) {
	 	}
		public void partClosed(IWorkbenchPartReference ref) {
		}
		public void partDeactivated(IWorkbenchPartReference ref) {
		}
		public void partOpened(IWorkbenchPartReference ref) {
		}
		public void partVisible(IWorkbenchPartReference ref) {
			if (getSite().getId().equals(ref.getId())) {
				fPartIsVisible= true;
			}
		}
		public void partHidden(IWorkbenchPartReference ref) {
			if (getSite().getId().equals(ref.getId())) {
				fPartIsVisible= false;
			}
		}
	};
	private JUnitCopyAction fCopyAction;
	
	private class TreeEntryQueueDrainer implements Runnable {
		public void run() {
			while (true) {
				String treeEntry;
				synchronized (fTreeEntryQueue) {
					if (fTreeEntryQueue.isEmpty() || isDisposed()) {
						fQueueDrainRequestOutstanding= false;
						return;
					}
					treeEntry= (String)fTreeEntryQueue.remove(0);
				}
				for (Enumeration e= fTestRunTabs.elements(); e.hasMoreElements();) {
					TestRunTab v= (TestRunTab)e.nextElement();
					v.newTreeEntry(treeEntry);
				}
			}
		}
	}
		
	private class ActivateOnErrorAction extends Action {
		public ActivateOnErrorAction() {
			super(JUnitMessages.TestRunnerViewPart_activate_on_failure_only, IAction.AS_CHECK_BOX);
			try {
				setImageDescriptor(ImageDescriptor.createFromURL(JUnitPlugin.makeIconFileURL("obj16/failures.gif"))); //$NON-NLS-1$
			} catch (MalformedURLException e) {
				// don't use any image
			}
			update();
		}
		public void update() {
			setChecked(getShowOnErrorOnly());
		}
		public void run() {
			boolean checked= isChecked();
			fShowOnErrorOnly= checked;
			IPreferenceStore store= JUnitPlugin.getDefault().getPreferenceStore();
			store.setValue(JUnitPreferencesConstants.SHOW_ON_ERROR_ONLY, checked);
		}
	}
	
	public void init(IViewSite site, IMemento memento) throws PartInitException {
		super.init(site, memento);
		fMemento= memento;
		IWorkbenchSiteProgressService progressService= getProgressService();
		if (progressService != null)
			progressService.showBusyForFamily(TestRunnerViewPart.FAMILY_JUNIT_RUN);
	}
	
	private IWorkbenchSiteProgressService getProgressService() {
		Object siteService= getSite().getAdapter(IWorkbenchSiteProgressService.class);
		if(siteService != null)
			return (IWorkbenchSiteProgressService) siteService;
		return null;
	}


	private void restoreLayoutState(IMemento memento) {
		Integer page= memento.getInteger(TAG_PAGE);
		if (page != null) {
			int p= page.intValue();
			fTabFolder.setSelection(p);
			fActiveRunTab= (TestRunTab)fTestRunTabs.get(p);
		}
		Integer ratio= memento.getInteger(TAG_RATIO);
		if (ratio != null) 
			fSashForm.setWeights(new int[] { ratio.intValue(), 1000 - ratio.intValue()} );
		Integer orientation= memento.getInteger(TAG_ORIENTATION);
		if (orientation != null)
			fOrientation= orientation.intValue();
		computeOrientation();
		String scrollLock= memento.getString(TAG_SCROLL);
		if (scrollLock != null) {
			fScrollLockAction.setChecked(scrollLock.equals("true")); //$NON-NLS-1$
			setAutoScroll(!fScrollLockAction.isChecked());
		}
	}
	
	/**
	 * Stops the currently running test and shuts down the RemoteTestRunner
	 */
	public void stopTest() {
		if (fTestRunnerClient != null)
			fTestRunnerClient.stopTest();
		stopUpdateJobs();
	}

	/**
	 * Stops the currently running test and shuts down the RemoteTestRunner
	 */
	public void rerunTestRun() {
		if (lastLaunchIsKeptAlive()) {
			// prompt for terminating the existing run
			if (MessageDialog.openQuestion(getSite().getShell(), JUnitMessages.TestRunnerViewPart_terminate_title, JUnitMessages.TestRunnerViewPart_terminate_message)) {  
				if (fTestRunnerClient != null)
					fTestRunnerClient.stopTest();
			}
		}
		if (fLastLaunch != null && fLastLaunch.getLaunchConfiguration() != null) {
			ILaunchConfiguration configuration= prepareLaunchConfigForRelaunch(fLastLaunch.getLaunchConfiguration());
			DebugUITools.launch(configuration, fLastLaunch.getLaunchMode());
		}
	}
	
	private ILaunchConfiguration prepareLaunchConfigForRelaunch(ILaunchConfiguration configuration) {
		try {
			String attribute= configuration.getAttribute(JUnitBaseLaunchConfiguration.FAILURES_FILENAME_ATTR, ""); //$NON-NLS-1$
			if (attribute.length() != 0) {
				String configName= Messages.format(JUnitMessages.TestRunnerViewPart_configName, configuration.getName()); 
				ILaunchConfigurationWorkingCopy tmp= configuration.copy(configName); 
				tmp.setAttribute(JUnitBaseLaunchConfiguration.FAILURES_FILENAME_ATTR, ""); //$NON-NLS-1$
				return tmp;
			}
		} catch (CoreException e) {
			// fall through
		}
		return configuration;
	}

	public void rerunTestFailedFirst() {
		if (lastLaunchIsKeptAlive()) {
			// prompt for terminating the existing run
			if (MessageDialog.openQuestion(getSite().getShell(), JUnitMessages.TestRunnerViewPart_terminate_title, JUnitMessages.TestRunnerViewPart_terminate_message)) {  
				if (fTestRunnerClient != null)
					fTestRunnerClient.stopTest();
			}
		}
		if (fLastLaunch != null && fLastLaunch.getLaunchConfiguration() != null) {
				ILaunchConfiguration launchConfiguration= fLastLaunch.getLaunchConfiguration();
				if (launchConfiguration != null) {
					try {
						String name= JUnitMessages.TestRunnerViewPart_rerunLaunchConfigName; 
						String configName= Messages.format(JUnitMessages.TestRunnerViewPart_configName, name); 
						ILaunchConfigurationWorkingCopy tmp= launchConfiguration.copy(configName); 
						tmp.setAttribute(JUnitBaseLaunchConfiguration.FAILURES_FILENAME_ATTR, createFailureNamesFile());
						tmp.launch(fLastLaunch.getLaunchMode(), null);	
						return;	
					} catch (CoreException e) {
						ErrorDialog.openError(getSite().getShell(), 
							JUnitMessages.TestRunnerViewPart_error_cannotrerun, e.getMessage(), e.getStatus() 
						);
					}
				}
				MessageDialog.openInformation(getSite().getShell(), 
					JUnitMessages.TestRunnerViewPart_cannotrerun_title,  
					JUnitMessages.TestRunnerViewPart_cannotrerurn_message
				); 
		}
	}	

	private String createFailureNamesFile() throws CoreException {
		try {
			File file= File.createTempFile("testFailures", ".txt"); //$NON-NLS-1$ //$NON-NLS-2$
			file.deleteOnExit();
			BufferedWriter bw= null;
			try {
				bw= new BufferedWriter(new FileWriter(file));
				for (int i= 0; i < fFailures.size(); i++) {
					TestRunInfo testInfo= (TestRunInfo)fFailures.get(i);
					bw.write(testInfo.getTestName());
					bw.newLine();
				}
			} finally {
				if (bw != null) {
					bw.close();
				}
			}
			return file.getAbsolutePath();
		} catch (IOException e) {
			throw new CoreException(new Status(IStatus.ERROR, JUnitPlugin.PLUGIN_ID, IStatus.ERROR, "", e)); //$NON-NLS-1$
		}
	}

	public void setAutoScroll(boolean scroll) {
		fAutoScroll = scroll;
	}
	
	public boolean isAutoScroll() {
		return fAutoScroll;
	}	

	/*
	 * @see ITestRunListener#testRunStarted(testCount)
	 */
	public void testRunStarted(final int testCount){
		reset(testCount);
		fShowOnErrorOnly= getShowOnErrorOnly();
		fExecutedTests++;
		stopUpdateJobs();
		fUpdateJob= new UpdateUIJob(JUnitMessages.TestRunnerViewPart_jobName); 
		fJUnitIsRunningJob= new JUnitIsRunningJob(JUnitMessages.TestRunnerViewPart_wrapperJobName);
		fJUnitIsRunningLock= Platform.getJobManager().newLock(); 
		// acquire lock while a test run is running
		// the lock is released when the test run terminates
		// the wrapper job will wait on this lock.
		fJUnitIsRunningLock.acquire();
		getProgressService().schedule(fJUnitIsRunningJob);
		fUpdateJob.schedule(REFRESH_INTERVAL);
		fRerunLastTestAction.setEnabled(true);
	}
	
	public void selectNextFailure() {
		fActiveRunTab.selectNext();
	}
	
	public void selectPreviousFailure() {
		fActiveRunTab.selectPrevious();
	}

	public void showTest(TestRunInfo test) {
		fActiveRunTab.setSelectedTest(test.getTestId());
		handleTestSelected(test.getTestId());
		new OpenTestAction(this, test.getClassName(), test.getTestMethodName(), false).run();
	}

	
	public void reset(){
		reset(0);
		setContentDescription(" "); //$NON-NLS-1$
		clearStatus();
		resetViewIcon();
	}

	/*
	 * @see ITestRunListener#testRunEnded
	 */
	public void testRunEnded(long elapsedTime){
		fExecutedTests--;
		String[] keys= {elapsedTimeAsString(elapsedTime)};
		String msg= Messages.format(JUnitMessages.TestRunnerViewPart_message_finish, keys); 
		if (hasErrorsOrFailures())
			postError(msg);
		else
			setInfoMessage(msg);
			
		postSyncRunnable(new Runnable() {				
			public void run() {
				if(isDisposed()) 
					return;	
				fStopAction.setEnabled(lastLaunchIsKeptAlive());
				fRerunLastFailedFirstAction.setEnabled(hasErrorsOrFailures());
				if (fFailures.size() > 0) {
					selectFirstFailure();
				}
				updateViewIcon();
				if (fDirtyListener == null) {
					fDirtyListener= new DirtyListener();
					JavaCore.addElementChangedListener(fDirtyListener);
				}
				for (Enumeration e= fTestRunTabs.elements(); e.hasMoreElements();) {
					TestRunTab v= (TestRunTab) e.nextElement();
					v.aboutToEnd();
				}
				warnOfContentChange();
			}
		});	
		stopUpdateJobs();
	}

	private void stopUpdateJobs() {
		if (fUpdateJob != null) {
			fUpdateJob.stop();
			fUpdateJob= null;
		}
		if (fJUnitIsRunningJob != null && fJUnitIsRunningLock != null) {
			fJUnitIsRunningLock.release();
			fJUnitIsRunningJob= null;
		}
	}

	protected void selectFirstFailure() {
		TestRunInfo firstFailure= (TestRunInfo)fFailures.get(0);
		if (firstFailure != null && fActiveRunTab.getSelectedTestId() == null) {
			fActiveRunTab.setSelectedTest(firstFailure.getTestId());
			handleTestSelected(firstFailure.getTestId());
		}
	}

	private void updateViewIcon() {
		if (hasErrorsOrFailures()) 
			fViewImage= fTestRunFailIcon;
		else 
			fViewImage= fTestRunOKIcon;
		firePropertyChange(IWorkbenchPart.PROP_TITLE);	
	}

	private boolean hasErrorsOrFailures() {
		return fErrorCount+fFailureCount > 0;
	}

	private String elapsedTimeAsString(long runTime) {
		return NumberFormat.getInstance().format((double)runTime/1000);
	}

	/*
	 * @see ITestRunListener#testRunStopped
	 */
	public void testRunStopped(final long elapsedTime) {
		String msg= JUnitMessages.TestRunnerViewPart_message_stopped; 
		setInfoMessage(msg);
		handleStopped();
	}

	private void handleStopped() {
		postSyncRunnable(new Runnable() {				
			public void run() {
				if(isDisposed()) 
					return;	
				resetViewIcon();
				fStopAction.setEnabled(false);
				fRerunLastFailedFirstAction.setEnabled(hasErrorsOrFailures());
				fProgressBar.stopped();
			}
		});	
		stopUpdateJobs();
	}

	private void resetViewIcon() {
		fViewImage= fOriginalViewImage;
		firePropertyChange(IWorkbenchPart.PROP_TITLE);
	}

	/*
	 * @see ITestRunListener#testRunTerminated
	 */
	public void testRunTerminated() {
		String msg= JUnitMessages.TestRunnerViewPart_message_terminated; 
		showMessage(msg);
		handleStopped(); 
	}

	private void showMessage(String msg) {
		//showInformation(msg);
		postError(msg);
	}

	/*
	 * @see ITestRunListener#testStarted
	 */
	public void testStarted(String testId, String testName) {
		fTestIsRunning= true;
		postStartTest(testId, testName);
		// reveal the part when the first test starts
		if (!fShowOnErrorOnly && fExecutedTests == 1) 
			postShowTestResultsView();
			
		TestRunInfo testInfo= getTestInfo(testId);
		if (testInfo == null) {
			testInfo= new TestRunInfo(testId, testName);
			fTestInfos.put(testId, testInfo);
		}
		String className= testInfo.getClassName();
		String method= testInfo.getTestMethodName();		
		String status= Messages.format(JUnitMessages.TestRunnerViewPart_message_started, new String[] { className, method }); 
		setInfoMessage(status); 
	}
	
	/*
	 * @see ITestRunListener#testEnded
	 */
	public void testEnded(String testId, String testName){
		postEndTest(testId, testName);
		fExecutedTests++;
	}

	/*
	 * @see ITestRunListener#testFailed
	 */
	public void testFailed(int status, String testId, String testName, String trace){
		testFailed(status, testId, testName, trace, null, null);
	}

	/*
	 * @see ITestRunListener#testFailed
	 */
	public void testFailed(int status, String testId, String testName, String trace, String expected, String actual) {
	    TestRunInfo testInfo= getTestInfo(testId);
	    if (testInfo == null) {
	        testInfo= new TestRunInfo(testId, testName);
	        fTestInfos.put(testId, testInfo);
	    }
	    testInfo.setTrace(trace);
	    testInfo.setStatus(status);
	    if (expected != null && expected.length() != 0) {
			testInfo.setExpected(expected.substring(0, expected.length()-1));
		}
	    if (actual != null && actual.length() != 0)
	        testInfo.setActual(actual.substring(0, actual.length()-1));
	    
	    if (status == ITestRunListener.STATUS_ERROR)
	        fErrorCount++;
	    else
	        fFailureCount++;
	    fFailures.add(testInfo);
	    // show the view on the first error only
	    if (fShowOnErrorOnly && (fErrorCount + fFailureCount == 1)) 
	        postShowTestResultsView();
	    
	    // [Bug 35590] JUnit window doesn't report errors from junit.extensions.TestSetup [JUnit]
	    // when a failure occurs in test setup then no test is running
	    // to update the views we artificially signal the end of a test run
	    if (!fTestIsRunning) {
	    	fTestIsRunning= false;
	    	testEnded(testId, testName);
	    }
	}
	
	/*
	 * @see ITestRunListener#testReran
	 */
	public void testReran(final String testId, String className, String testName, int status, String trace) {
		if (status == ITestRunListener.STATUS_ERROR) {
			String msg= Messages.format(JUnitMessages.TestRunnerViewPart_message_error, new String[]{testName, className}); 
			postError(msg); 
		} else if (status == ITestRunListener.STATUS_FAILURE) {
			String msg= Messages.format(JUnitMessages.TestRunnerViewPart_message_failure, new String[]{testName, className}); 
			postError(msg);
		} else {
			String msg= Messages.format(JUnitMessages.TestRunnerViewPart_message_success, new String[]{testName, className}); 
			setInfoMessage(msg);
		}
		TestRunInfo info= getTestInfo(testId);
		updateTest(info, status);
		postSyncRunnable(new Runnable() {
			public void run() {
				refreshCounters();
				for (Enumeration e= fTestRunTabs.elements(); e.hasMoreElements();) {
					TestRunTab v= (TestRunTab) e.nextElement();
					v.endRerunTest(testId);
				}
			}
		});
		
		if (info.getTrace() == null || !info.getTrace().equals(trace)) {
			info.setTrace(trace);
			showFailure(info);
		}
	}

	public void testReran(String testId, String className, String testName, int statusCode, String trace, String expectedResult, String actualResult) {
		testReran(testId, className, testName, statusCode, trace);
		TestRunInfo info= getTestInfo(testId);
		info.setActual(actualResult);
		info.setExpected(expectedResult);
		fFailureTrace.updateEnablement(info);
	}
	
	private void updateTest(final TestRunInfo info, final int status) {
		if (status == info.getStatus())
			return;
		if (info.getStatus() == ITestRunListener.STATUS_OK) {
			if (status == ITestRunListener.STATUS_FAILURE) 
				fFailureCount++;
			else if (status == ITestRunListener.STATUS_ERROR)
				fErrorCount++;
		} else if (info.getStatus() == ITestRunListener.STATUS_ERROR) {
			if (status == ITestRunListener.STATUS_OK) 
				fErrorCount--;
			else if (status == ITestRunListener.STATUS_FAILURE) {
				fErrorCount--;
				fFailureCount++;
			}
		} else if (info.getStatus() == ITestRunListener.STATUS_FAILURE) {
			if (status == ITestRunListener.STATUS_OK) 
				fFailureCount--;
			else if (status == ITestRunListener.STATUS_ERROR) {
				fFailureCount--;
				fErrorCount++;
			}
		}			
		info.setStatus(status);	
		postSyncRunnable(new Runnable() {
			public void run() {
				//refreshCounters();
				for (Enumeration e= fTestRunTabs.elements(); e.hasMoreElements();) {
					TestRunTab v= (TestRunTab) e.nextElement();
					v.testStatusChanged(info);
				}
			}
		});
	}

	
	/*
	 * @see ITestRunListener#testTreeEntry
	 */
	public void testTreeEntry(final String treeEntry){
		synchronized(fTreeEntryQueue) {
			fTreeEntryQueue.add(treeEntry);
			if (!fQueueDrainRequestOutstanding) {
				fQueueDrainRequestOutstanding= true;
				if (!isDisposed())
					getDisplay().asyncExec(new TreeEntryQueueDrainer());
			}
		}
	}
	
	public void startTestRunListening(IJavaElement type, int port, ILaunch launch) {
		fTestProject= type.getJavaProject();
		fLaunchMode= launch.getLaunchMode();
		aboutToLaunch();
		
		if (fTestRunnerClient != null) {
			stopTest();
		}
		fTestRunnerClient= new RemoteTestRunnerClient();
		
		ITestRunListener[] listenerArray = getListenerArray();
		fTestRunnerClient.startListening(listenerArray, port);
		
		fLastLaunch= launch;
		setContentDescription(MessageFormat.format(JUnitMessages.TestRunnerViewPart_Launching, new Object[]{ launch.getLaunchConfiguration().getName() }));
		if (type instanceof IType)
			setTitleToolTip(((IType)type).getFullyQualifiedName('.'));
		else
			setTitleToolTip(type.getElementName());
			
	}

	protected ITestRunListener[] getListenerArray() {
		// add the TestRunnerViewPart to the list of registered listeners
		List listeners= JUnitPlugin.getDefault().getTestRunListeners();	
		ITestRunListener[] listenerArray= new ITestRunListener[listeners.size()+1];
		listeners.toArray(listenerArray);
		System.arraycopy(listenerArray, 0, listenerArray, 1, listenerArray.length-1);
		listenerArray[0]= this;
		return listenerArray;
	}

	protected void aboutToLaunch() {
		String msg= JUnitMessages.TestRunnerViewPart_message_launching; 
		//showInformation(msg);
		setInfoMessage(msg);
		fViewImage= fOriginalViewImage;
		firePropertyChange(IWorkbenchPart.PROP_TITLE);
	}

	public synchronized void dispose(){
		fIsDisposed= true;
		stopTest();
		if (fProgressImages != null)
			fProgressImages.dispose();
		getViewSite().getPage().removePartListener(fPartListener);
		fTestRunOKIcon.dispose();
		fTestRunFailIcon.dispose();
		fStackViewIcon.dispose();
		fTestRunOKDirtyIcon.dispose();
		fTestRunFailDirtyIcon.dispose();
		if (fClipboard != null) 
			fClipboard.dispose();
		if (fViewMenuListener != null) {
			getViewSite().getActionBars().getMenuManager().removeMenuListener(fViewMenuListener);
		}
	}

	protected void start(final int total) {
		resetProgressBar(total);
		fCounterPanel.setTotal(total);
		fCounterPanel.setRunValue(0);	
	}

	private void resetProgressBar(final int total) {
		fProgressBar.reset();
		fProgressBar.setMaximum(total);
	}

	private void postSyncRunnable(Runnable r) {
		if (!isDisposed())
			getDisplay().syncExec(r);
	}

	private void aboutToStart() {
		postSyncRunnable(new Runnable() {
			public void run() {
				if (!isDisposed()) {
					for (Enumeration e= fTestRunTabs.elements(); e.hasMoreElements();) {
						TestRunTab v= (TestRunTab) e.nextElement();
						v.aboutToStart();
					}
					fNextAction.setEnabled(false);
					fPreviousAction.setEnabled(false);
				}
			}
		});
	}
	
	private void postEndTest(final String testId, final String testName) {
		postSyncRunnable(new Runnable() {
			public void run() {
				if(isDisposed()) 
					return;
				handleEndTest();
				for (Enumeration e= fTestRunTabs.elements(); e.hasMoreElements();) {
					TestRunTab v= (TestRunTab) e.nextElement();
					v.endTest(testId);
				}
				
				if (fFailureCount + fErrorCount > 0) {
					fNextAction.setEnabled(true);
					fPreviousAction.setEnabled(true);
				}
			}
		});	
	}

	private void postStartTest(final String testId, final String testName) {
		postSyncRunnable(new Runnable() {
			public void run() {
				if(isDisposed()) 
					return;
				for (Enumeration e= fTestRunTabs.elements(); e.hasMoreElements();) {
					TestRunTab v= (TestRunTab) e.nextElement();
					v.startTest(testId);
				}
			}
		});	
	}

	private void handleEndTest() {
		fTestIsRunning= false;
		//refreshCounters();
		fProgressBar.step(fFailureCount+fErrorCount);
		if (!fPartIsVisible) 
			updateViewTitleProgress();
	}

	private void updateViewTitleProgress() {
		Image progress= fProgressImages.getImage(fExecutedTests, fTestCount, fErrorCount, fFailureCount);
		if (progress != fViewImage) {
			fViewImage= progress;
			firePropertyChange(IWorkbenchPart.PROP_TITLE);
		}
	}

	private void refreshCounters() {
		fCounterPanel.setErrorValue(fErrorCount);
		fCounterPanel.setFailureValue(fFailureCount);
		fCounterPanel.setRunValue(fExecutedTests);
		fProgressBar.refresh(fErrorCount+fFailureCount> 0);
	}

	protected void postShowTestResultsView() {
		postSyncRunnable(new Runnable() {
			public void run() {
				if (isDisposed()) 
					return;
				showTestResultsView();
			}
		});
	}

	public void showTestResultsView() {
		IWorkbenchWindow window= getSite().getWorkbenchWindow();
		IWorkbenchPage page= window.getActivePage();
		TestRunnerViewPart testRunner= null;
		
		if (page != null) {
			try { // show the result view
				testRunner= (TestRunnerViewPart)page.findView(TestRunnerViewPart.NAME);
				if(testRunner == null) {
					IWorkbenchPart activePart= page.getActivePart();
					testRunner= (TestRunnerViewPart)page.showView(TestRunnerViewPart.NAME);
					//restore focus 
					page.activate(activePart);
				} else {
					page.bringToTop(testRunner);
				}
			} catch (PartInitException pie) {
				JUnitPlugin.log(pie);
			}
		}
	}
	
	class UpdateUIJob extends UIJob {
		private boolean fRunning= true; 
		
		public UpdateUIJob(String name) {
			super(name);
			setSystem(true);
		}
		public IStatus runInUIThread(IProgressMonitor monitor) {
			if (!isDisposed()) { 
				doShowStatus();
				refreshCounters();
			}
			schedule(REFRESH_INTERVAL);
			return Status.OK_STATUS;
		}
		
		public void stop() {
			fRunning= false;
		}
		public boolean shouldSchedule() {
			return fRunning;
		}
	}

	class JUnitIsRunningJob extends Job {
		public JUnitIsRunningJob(String name) {
			super(name);
			setSystem(true);
		}
		public IStatus run(IProgressMonitor monitor) {
			// wait until the test run terminates
			fJUnitIsRunningLock.acquire();
			return Status.OK_STATUS;
		}
		public boolean belongsTo(Object family) {
			return family == TestRunnerViewPart.FAMILY_JUNIT_RUN;
		}
	}

	protected void doShowStatus() {
		setContentDescription(fStatus);
	}

	protected void setInfoMessage(final String message) {
		fStatus= message;
	}

	protected void postError(final String message) {
		fStatus= message;
	}

	protected void showInformation(final String info){
		postSyncRunnable(new Runnable() {
			public void run() {
				if (!isDisposed())
					fFailureTrace.setInformation(info);
			}
		});
	}

	protected CTabFolder createTestRunTabs(Composite parent) {
		CTabFolder tabFolder= new CTabFolder(parent, SWT.TOP);
		tabFolder.setLayoutData(new GridData(GridData.FILL_BOTH | GridData.GRAB_VERTICAL));

		loadTestRunTabs(tabFolder);
		tabFolder.setSelection(0);				
		fActiveRunTab= (TestRunTab)fTestRunTabs.firstElement();		
				
		tabFolder.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				testTabChanged(event);
			}
		});
		return tabFolder;
	}

	private void loadTestRunTabs(CTabFolder tabFolder) {
		IExtensionPoint extensionPoint= Platform.getExtensionRegistry().getExtensionPoint(ID_EXTENSION_POINT_TESTRUN_TABS);
		if (extensionPoint == null) {
			return;
		}
		IConfigurationElement[] configs= extensionPoint.getConfigurationElements();
		MultiStatus status= new MultiStatus(JUnitPlugin.PLUGIN_ID, IStatus.OK, "Could not load some testRunTabs extension points", null); //$NON-NLS-1$ 	

		for (int i= 0; i < configs.length; i++) {
			try {
				TestRunTab testRunTab= (TestRunTab) configs[i].createExecutableExtension("class"); //$NON-NLS-1$
				testRunTab.createTabControl(tabFolder, fClipboard, this);
				fTestRunTabs.addElement(testRunTab);
			} catch (CoreException e) {
				status.add(e.getStatus());
			}
		}
		if (!status.isOK()) {
			JUnitPlugin.log(status);
		}
	}

	private void testTabChanged(SelectionEvent event) {
		for (Enumeration e= fTestRunTabs.elements(); e.hasMoreElements();) {
			TestRunTab v= (TestRunTab) e.nextElement();
			if (((CTabFolder) event.widget).getSelection().getText() == v.getName()){
				v.setSelectedTest(fActiveRunTab.getSelectedTestId());
				fActiveRunTab= v;
				fActiveRunTab.activate();
			}
		}
	}

	private SashForm createSashForm(Composite parent) {
		fSashForm= new SashForm(parent, SWT.VERTICAL);
		ViewForm top= new ViewForm(fSashForm, SWT.NONE);
		fTabFolder= createTestRunTabs(top);
		fTabFolder.setLayoutData(new TabFolderLayout());
		top.setContent(fTabFolder);
		
		ViewForm bottom= new ViewForm(fSashForm, SWT.NONE);
		CLabel label= new CLabel(bottom, SWT.NONE);
		label.setText(JUnitMessages.TestRunnerViewPart_label_failure); 
		label.setImage(fStackViewIcon);
		bottom.setTopLeft(label);

		ToolBar failureToolBar= new ToolBar(bottom, SWT.FLAT | SWT.WRAP);
		bottom.setTopCenter(failureToolBar);
		fFailureTrace= new FailureTrace(bottom, fClipboard, this, failureToolBar);
		bottom.setContent(fFailureTrace.getComposite()); 
		
		fSashForm.setWeights(new int[]{50, 50});
		return fSashForm;
	}

	private void reset(final int testCount) {
		postSyncRunnable(new Runnable() {
			public void run() {
				if (isDisposed()) 
					return;
				fCounterPanel.reset();
				fFailureTrace.clear();
				fProgressBar.reset();
				fStopAction.setEnabled(true);
				clearStatus();
				start(testCount);
			}
		});
		fExecutedTests= 0;
		fFailureCount= 0;
		fErrorCount= 0;
		fTestCount= testCount;
		aboutToStart();
		fTestInfos.clear();
		fFailures= new ArrayList();
	}

	private void clearStatus() {
		getStatusLine().setMessage(null);
		getStatusLine().setErrorMessage(null);
	}

    public void setFocus() {
    	if (fActiveRunTab != null)
    		fActiveRunTab.setFocus();
    }

    public void createPartControl(Composite parent) {	
    	fParent= parent;
    	addResizeListener(parent);
		fClipboard= new Clipboard(parent.getDisplay());

		GridLayout gridLayout= new GridLayout(); 
		gridLayout.marginWidth= 0;
		gridLayout.marginHeight= 0;
		parent.setLayout(gridLayout);

		configureToolBar();
		
		fCounterComposite= createProgressCountPanel(parent);
		fCounterComposite.setLayoutData(new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));
		SashForm sashForm= createSashForm(parent);
		sashForm.setLayoutData(new GridData(GridData.FILL_BOTH));
		IActionBars actionBars= getViewSite().getActionBars();
		fCopyAction = new JUnitCopyAction(fFailureTrace, fClipboard);
		actionBars.setGlobalActionHandler(
			ActionFactory.COPY.getId(),
			fCopyAction);
		
		fOriginalViewImage= getTitleImage();
		fProgressImages= new ProgressImages();
		PlatformUI.getWorkbench().getHelpSystem().setHelp(parent, IJUnitHelpContextIds.RESULTS_VIEW);
		
		getViewSite().getPage().addPartListener(fPartListener);

		if (fMemento != null) {
			restoreLayoutState(fMemento);
		}
		fMemento= null;
	}

	private void addResizeListener(Composite parent) {
		parent.addControlListener(new ControlListener() {
			public void controlMoved(ControlEvent e) {
			}
			public void controlResized(ControlEvent e) {
				computeOrientation();
			}
		});
	}

	void computeOrientation() {
		if (fOrientation != VIEW_ORIENTATION_AUTOMATIC) {
			fCurrentOrientation= fOrientation;
			setOrientation(fCurrentOrientation);
		}
		else {
			Point size= fParent.getSize();
			if (size.x != 0 && size.y != 0) {
				if (size.x > size.y) 
					setOrientation(VIEW_ORIENTATION_HORIZONTAL);
				else 
					setOrientation(VIEW_ORIENTATION_VERTICAL);
			}
		}
	}

	public void saveState(IMemento memento) {
		if (fSashForm == null) {
			// part has not been created
			if (fMemento != null) //Keep the old state;
				memento.putMemento(fMemento);
			return;
		}
		
		int activePage= fTabFolder.getSelectionIndex();
		memento.putInteger(TAG_PAGE, activePage);
		memento.putString(TAG_SCROLL, fScrollLockAction.isChecked() ? "true" : "false"); //$NON-NLS-1$ //$NON-NLS-2$
		int weigths[]= fSashForm.getWeights();
		int ratio= (weigths[0] * 1000) / (weigths[0] + weigths[1]);
		memento.putInteger(TAG_RATIO, ratio);
		memento.putInteger(TAG_ORIENTATION, fOrientation);
	}
	
	private void configureToolBar() {
		IActionBars actionBars= getViewSite().getActionBars();
		IToolBarManager toolBar= actionBars.getToolBarManager();
		IMenuManager viewMenu = actionBars.getMenuManager();
		fRerunLastTestAction= new RerunLastAction();
		fRerunLastFailedFirstAction= new RerunLastFailedFirstAction();
		fScrollLockAction= new ScrollLockAction(this);
		fToggleOrientationActions =
			new ToggleOrientationAction[] {
				new ToggleOrientationAction(this, VIEW_ORIENTATION_VERTICAL),
				new ToggleOrientationAction(this, VIEW_ORIENTATION_HORIZONTAL),
				new ToggleOrientationAction(this, VIEW_ORIENTATION_AUTOMATIC)};
		fNextAction= new ShowNextFailureAction(this);
		fPreviousAction= new ShowPreviousFailureAction(this);
		fStopAction= new StopAction();
		fNextAction.setEnabled(false);
		fPreviousAction.setEnabled(false);
		fStopAction.setEnabled(false);
		actionBars.setGlobalActionHandler(ActionFactory.NEXT.getId(), fNextAction);
		actionBars.setGlobalActionHandler(ActionFactory.PREVIOUS.getId(), fPreviousAction);
		
		toolBar.add(fNextAction);
		toolBar.add(fPreviousAction);
		toolBar.add(fStopAction);
		toolBar.add(new Separator());
		toolBar.add(fRerunLastTestAction);
		toolBar.add(fRerunLastFailedFirstAction);
		toolBar.add(fScrollLockAction);

		for (int i = 0; i < fToggleOrientationActions.length; ++i)
			viewMenu.add(fToggleOrientationActions[i]);
		viewMenu.add(new Separator());
		fActivateOnErrorAction= new ActivateOnErrorAction();
		viewMenu.add(fActivateOnErrorAction);
		fViewMenuListener= new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager) {
				fActivateOnErrorAction.update();
			}
		};
		viewMenu.addMenuListener(fViewMenuListener);
		
		fScrollLockAction.setChecked(!fAutoScroll);

		actionBars.updateActionBars();
	}

	private IStatusLineManager getStatusLine() {
		// we want to show messages globally hence we
		// have to go through the active part
		IViewSite site= getViewSite();
		IWorkbenchPage page= site.getPage();
		IWorkbenchPart activePart= page.getActivePart();
	
		if (activePart instanceof IViewPart) {
			IViewPart activeViewPart= (IViewPart)activePart;
			IViewSite activeViewSite= activeViewPart.getViewSite();
			return activeViewSite.getActionBars().getStatusLineManager();
		}
		
		if (activePart instanceof IEditorPart) {
			IEditorPart activeEditorPart= (IEditorPart)activePart;
			IEditorActionBarContributor contributor= activeEditorPart.getEditorSite().getActionBarContributor();
			if (contributor instanceof EditorActionBarContributor) 
				return ((EditorActionBarContributor) contributor).getActionBars().getStatusLineManager();
		}
		// no active part
		return getViewSite().getActionBars().getStatusLineManager();
	}

	protected Composite createProgressCountPanel(Composite parent) {
		Composite composite= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout();
		composite.setLayout(layout);
		setCounterColumns(layout); 
		
		fCounterPanel = new CounterPanel(composite);
		fCounterPanel.setLayoutData(
			new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));
		fProgressBar = new JUnitProgressBar(composite);
		fProgressBar.setLayoutData(
				new GridData(GridData.GRAB_HORIZONTAL | GridData.HORIZONTAL_ALIGN_FILL));
		return composite;
	}

	public TestRunInfo getTestInfo(String testId) {
		if (testId == null)
			return null;
		return (TestRunInfo) fTestInfos.get(testId);
	}

	public void handleTestSelected(String testId) {
		handleTestSelected(getTestInfo(testId));
	}

	public void handleTestSelected(TestRunInfo testInfo) {
		if (testInfo == null) {
			showFailure(null); //$NON-NLS-1$
		} else {
			showFailure(testInfo);
		}
		
		fCopyAction.handleTestSelected(testInfo);
	}

	private void showFailure(final TestRunInfo failure) {
		postSyncRunnable(new Runnable() {
			public void run() {
				if (!isDisposed())
					fFailureTrace.showFailure(failure);
			}
		});		
	}

	public IJavaProject getLaunchedProject() {
		return fTestProject;
	}
	
	public ILaunch getLastLaunch() {
		return fLastLaunch;
	}
	
	public static Image createImage(String path) {
		try {
			ImageDescriptor id= ImageDescriptor.createFromURL(JUnitPlugin.makeIconFileURL(path));
			return id.createImage();
		} catch (MalformedURLException e) {
			// fall through
		}  
		return null;
	}

	private boolean isDisposed() {
		return fIsDisposed || fCounterPanel.isDisposed();
	}

	private Display getDisplay() {
		return getViewSite().getShell().getDisplay();
	}
	/**
	 * @see IWorkbenchPart#getTitleImage()
	 */
	public Image getTitleImage() {
		if (fOriginalViewImage == null)
			fOriginalViewImage= super.getTitleImage();
			
		if (fViewImage == null)
			return super.getTitleImage();
		return fViewImage;
	}

	void codeHasChanged() {
		if (fDirtyListener != null) {
			JavaCore.removeElementChangedListener(fDirtyListener);
			fDirtyListener= null;
		}
		if (fViewImage == fTestRunOKIcon) 
			fViewImage= fTestRunOKDirtyIcon;
		else if (fViewImage == fTestRunFailIcon)
			fViewImage= fTestRunFailDirtyIcon;
		
		Runnable r= new Runnable() {
			public void run() {
				if (isDisposed())
					return;
				firePropertyChange(IWorkbenchPart.PROP_TITLE);
			}
		};
		if (!isDisposed())
			getDisplay().asyncExec(r);
	}
	
	boolean isCreated() {
		return fCounterPanel != null;
	}

	public void rerunTest(String testId, String className, String testName, String launchMode) {
		DebugUITools.saveAndBuildBeforeLaunch();
		postRerunTest(testId);
		if (lastLaunchIsKeptAlive())
			fTestRunnerClient.rerunTest(testId, className, testName);
		else if (fLastLaunch != null) {
			// run the selected test using the previous launch configuration
			ILaunchConfiguration launchConfiguration= fLastLaunch.getLaunchConfiguration();
			if (launchConfiguration != null) {
				try {
					String name= className;
					if (testName != null) 
						name+= "."+testName; //$NON-NLS-1$
					String configName= Messages.format(JUnitMessages.TestRunnerViewPart_configName, name); 
					ILaunchConfigurationWorkingCopy tmp= launchConfiguration.copy(configName); 
					// fix for bug: 64838  junit view run single test does not use correct class [JUnit] 
					tmp.setAttribute(IJavaLaunchConfigurationConstants.ATTR_MAIN_TYPE_NAME, className);
					// reset the container
					tmp.setAttribute(JUnitBaseLaunchConfiguration.LAUNCH_CONTAINER_ATTR, ""); //$NON-NLS-1$
					if (testName != null) {
						tmp.setAttribute(JUnitBaseLaunchConfiguration.TESTNAME_ATTR, testName);
						//	String args= "-rerun "+testId;
						//	tmp.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, args);
					}
					tmp.launch(launchMode, null);	
					return;	
				} catch (CoreException e) {
					ErrorDialog.openError(getSite().getShell(), 
						JUnitMessages.TestRunnerViewPart_error_cannotrerun, e.getMessage(), e.getStatus() 
					);
				}
			}
			MessageDialog.openInformation(getSite().getShell(), 
				JUnitMessages.TestRunnerViewPart_cannotrerun_title,  
				JUnitMessages.TestRunnerViewPart_cannotrerurn_message
			); 
		}
	}

	private void postRerunTest(final String testId) {
		postSyncRunnable(new Runnable() {
			public void run() {
				if(isDisposed()) 
					return;
				for (Enumeration e= fTestRunTabs.elements(); e.hasMoreElements();) {
					TestRunTab v= (TestRunTab) e.nextElement();
					v.rerunTest(testId);
				}
			}
		});	
	}
	
	public void warnOfContentChange() {
		IWorkbenchSiteProgressService service= getProgressService();
		if (service != null) 
			service.warnOfContentChange();
	}

	public boolean lastLaunchIsKeptAlive() {
		return fTestRunnerClient != null && fTestRunnerClient.isRunning() && ILaunchManager.DEBUG_MODE.equals(fLaunchMode);
	}

	private void setOrientation(int orientation) {
		if ((fSashForm == null) || fSashForm.isDisposed())
			return;
		boolean horizontal = orientation == VIEW_ORIENTATION_HORIZONTAL;
		fSashForm.setOrientation(horizontal ? SWT.HORIZONTAL : SWT.VERTICAL);
		for (int i = 0; i < fToggleOrientationActions.length; ++i)
			fToggleOrientationActions[i].setChecked(fOrientation == fToggleOrientationActions[i].getOrientation());
		fCurrentOrientation = orientation;
		GridLayout layout= (GridLayout) fCounterComposite.getLayout();
		setCounterColumns(layout); 
		fParent.layout();
	}

	private void setCounterColumns(GridLayout layout) {
		if (fCurrentOrientation == VIEW_ORIENTATION_HORIZONTAL)
			layout.numColumns= 2; 
		else
			layout.numColumns= 1;
	}

	private static boolean getShowOnErrorOnly() {
		IPreferenceStore store= JUnitPlugin.getDefault().getPreferenceStore();
		return store.getBoolean(JUnitPreferencesConstants.SHOW_ON_ERROR_ONLY);
	}
}
