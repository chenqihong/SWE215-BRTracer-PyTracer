/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Juan A. Hernandez - bug 89926
 *******************************************************************************/
package org.eclipse.ant.internal.ui.launchConfigurations;


import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.eclipse.ant.core.AntCorePlugin;
import org.eclipse.ant.core.AntCorePreferences;
import org.eclipse.ant.core.AntRunner;
import org.eclipse.ant.core.Property;
import org.eclipse.ant.core.Task;
import org.eclipse.ant.core.Type;
import org.eclipse.ant.internal.core.AbstractEclipseBuildLogger;
import org.eclipse.ant.internal.ui.AntUIPlugin;
import org.eclipse.ant.internal.ui.AntUtil;
import org.eclipse.ant.internal.ui.IAntUIConstants;
import org.eclipse.ant.internal.ui.IAntUIPreferenceConstants;
import org.eclipse.ant.internal.ui.debug.IAntDebugConstants;
import org.eclipse.ant.internal.ui.debug.model.RemoteAntDebugBuildListener;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IBreakpointManager;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;
import org.eclipse.debug.ui.CommonTab;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.debug.ui.RefreshTab;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.SocketUtil;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.ui.externaltools.internal.launchConfigurations.ExternalToolsUtil;
import org.eclipse.ui.externaltools.internal.program.launchConfigurations.BackgroundResourceRefresher;
import org.osgi.framework.Bundle;

/**
 * Launch delegate for Ant builds
 */
public class AntLaunchDelegate extends LaunchConfigurationDelegate  {
	
	private static final String ANT_LOGGER_CLASS = "org.eclipse.ant.internal.ui.antsupport.logger.AntProcessBuildLogger"; //$NON-NLS-1$
	private static final String ANT_DEBUG_LOGGER_CLASS = "org.eclipse.ant.internal.ui.antsupport.logger.AntProcessDebugBuildLogger"; //$NON-NLS-1$
	private static final String NULL_LOGGER_CLASS = "org.eclipse.ant.internal.ui.antsupport.logger.NullBuildLogger"; //$NON-NLS-1$
	private static final String REMOTE_ANT_LOGGER_CLASS = "org.eclipse.ant.internal.ui.antsupport.logger.RemoteAntBuildLogger"; //$NON-NLS-1$
	private static final String REMOTE_ANT_DEBUG_LOGGER_CLASS = "org.eclipse.ant.internal.ui.antsupport.logger.debug.RemoteAntDebugBuildLogger"; //$NON-NLS-1$
	private static final String BASE_DIR_PREFIX = "-Dbasedir="; //$NON-NLS-1$
	private static final String INPUT_HANDLER_CLASS = "org.eclipse.ant.internal.ui.antsupport.inputhandler.AntInputHandler"; //$NON-NLS-1$
	private static final String REMOTE_INPUT_HANDLER_CLASS = "org.eclipse.ant.internal.ui.antsupport.inputhandler.ProxyInputHandler"; //$NON-NLS-1$
    
    private static String fgSWTLibraryLocation;
	
	private String fMode;
    private boolean fUserSpecifiedLogger= false;
	
	/**
	 * @see org.eclipse.debug.core.model.ILaunchConfigurationDelegate#launch(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String, org.eclipse.debug.core.ILaunch, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor) throws CoreException {
		if (monitor.isCanceled()) {
			return;
		}
		fUserSpecifiedLogger= false;
		fMode= mode;
		
		// migrate the config to the new classpath format if required
		AntUtil.migrateToNewClasspathFormat(configuration);
		
		boolean isSeparateJRE= AntUtil.isSeparateJREAntBuild(configuration);
		
		if (CommonTab.isLaunchInBackground(configuration)) {
			monitor.beginTask(MessageFormat.format(AntLaunchConfigurationMessages.AntLaunchDelegate_Launching__0__1, new String[] {configuration.getName()}), 10); //$NON-NLS-1$
		} else {
			monitor.beginTask(MessageFormat.format(AntLaunchConfigurationMessages.AntLaunchDelegate_Running__0__2, new String[] {configuration.getName()}), 100); //$NON-NLS-1$
		}
		
		// resolve location
		IPath location = ExternalToolsUtil.getLocation(configuration);
		monitor.worked(1);
		
		if (monitor.isCanceled()) {
			return;
		}
		
		if (!isSeparateJRE && AntRunner.isBuildRunning()) {
			IStatus status= new Status(IStatus.ERROR, IAntUIConstants.PLUGIN_ID, 1, MessageFormat.format(AntLaunchConfigurationMessages.AntLaunchDelegate_Build_In_Progress, new String[]{location.toOSString()}), null); //$NON-NLS-1$
			throw new CoreException(status);
		}		
		
		// resolve working directory
		IPath workingDirectory = ExternalToolsUtil.getWorkingDirectory(configuration);
		String basedir = null;
		if (workingDirectory != null) {
			basedir= workingDirectory.toOSString();
		}
		monitor.worked(1);
		
		if (monitor.isCanceled()) {
			return;
		}

		// link the process to its build logger via a timestamp
		long timeStamp = System.currentTimeMillis();
		String idStamp = Long.toString(timeStamp);
		StringBuffer idProperty = new StringBuffer("-D"); //$NON-NLS-1$
		idProperty.append(AbstractEclipseBuildLogger.ANT_PROCESS_ID);
		idProperty.append('=');
		idProperty.append(idStamp);
		
		// resolve arguments
		String[] arguments = ExternalToolsUtil.getArguments(configuration);
		
		Map userProperties= AntUtil.getProperties(configuration);
		if (userProperties != null) {//create a copy so as to not affect the configuration with transient properties
			userProperties= new HashMap(userProperties);
		}
		String[] propertyFiles= AntUtil.getPropertyFiles(configuration);
		String[] targets = AntUtil.getTargetNames(configuration);
		URL[] customClasspath= AntUtil.getCustomClasspath(configuration);
		String antHome= AntUtil.getAntHome(configuration);
		
		boolean setInputHandler= true;
		try {
			//check if set specify inputhandler
			setInputHandler = configuration.getAttribute(IAntUIConstants.SET_INPUTHANDLER, true);
		} catch (CoreException ce) {
			AntUIPlugin.log(ce);			
		}
		
		AntRunner runner= null;
		if (!isSeparateJRE) {
			runner = configureAntRunner(configuration, location, basedir, idProperty, arguments, userProperties, propertyFiles, targets, customClasspath, antHome, setInputHandler);
		}
		 
		monitor.worked(1);
								
		if (monitor.isCanceled()) {
			return;
		}
		boolean captureOutput= ExternalToolsUtil.getCaptureOutput(configuration);
		int port= -1;
		int requestPort= -1;
		if (isSeparateJRE && captureOutput) {
			if (userProperties == null) {
				userProperties= new HashMap();
			}
			port= SocketUtil.findFreePort();
			userProperties.put(AbstractEclipseBuildLogger.ANT_PROCESS_ID, idStamp);
			userProperties.put("eclipse.connect.port", Integer.toString(port)); //$NON-NLS-1$
			if (fMode.equals(ILaunchManager.DEBUG_MODE)) {
				requestPort= SocketUtil.findFreePort();
				userProperties.put("eclipse.connect.request_port", Integer.toString(requestPort)); //$NON-NLS-1$
			}
		}
		
		StringBuffer commandLine= generateCommandLine(location, arguments, userProperties, propertyFiles, targets, antHome, basedir, isSeparateJRE, captureOutput, setInputHandler);
		
		if (isSeparateJRE) {
			monitor.beginTask(MessageFormat.format(AntLaunchConfigurationMessages.AntLaunchDelegate_Launching__0__1, new String[] {configuration.getName()}), 10); //$NON-NLS-1$
			runInSeparateVM(configuration, launch, monitor, idStamp, antHome, port, requestPort, commandLine, captureOutput, setInputHandler);
		} else {
			runInSameVM(configuration, launch, monitor, location, idStamp, runner, commandLine, captureOutput);
		}
		
		monitor.done();	
	}
	
	private void runInSameVM(ILaunchConfiguration configuration, ILaunch launch, IProgressMonitor monitor, IPath location, String idStamp, AntRunner runner, StringBuffer commandLine, boolean captureOutput) throws CoreException {
		Map attributes= new HashMap(2);
		attributes.put(IProcess.ATTR_PROCESS_TYPE, IAntLaunchConfigurationConstants.ID_ANT_PROCESS_TYPE);
		attributes.put(AbstractEclipseBuildLogger.ANT_PROCESS_ID, idStamp);
				
		final AntProcess process = new AntProcess(location.toOSString(), launch, attributes);
		setProcessAttributes(process, idStamp, commandLine, captureOutput);
		boolean debug= fMode.equals(ILaunchManager.DEBUG_MODE);
		if (debug || CommonTab.isLaunchInBackground(configuration)) {
			final AntRunner finalRunner= runner;
			Runnable r = new Runnable() {
				public void run() {
					try {
						finalRunner.run(process);
					} catch (CoreException e) {
						handleException(e, AntLaunchConfigurationMessages.AntLaunchDelegate_Failure); //$NON-NLS-1$
					}
					process.terminated();
				}
			};
			Thread background = new Thread(r);
            background.setDaemon(true);
			background.start();
			monitor.worked(1);
			//refresh resources after process finishes
			if (RefreshTab.getRefreshScope(configuration) != null) {
				BackgroundResourceRefresher refresher = new BackgroundResourceRefresher(configuration, process);
				refresher.startBackgroundRefresh();
			}	
		} else {
			// execute the build 
			try {
				runner.run(monitor);
			} catch (CoreException e) {
				process.terminated();
				monitor.done();
				handleException(e, AntLaunchConfigurationMessages.AntLaunchDelegate_23); //$NON-NLS-1$
				return;
			}
			process.terminated();
			
			// refresh resources
			RefreshTab.refreshResources(configuration, monitor);
		}
	}

	private AntRunner configureAntRunner(ILaunchConfiguration configuration, IPath location, String baseDir, StringBuffer idProperty, String[] arguments, Map userProperties, String[] propertyFiles, String[] targets, URL[] customClasspath, String antHome, boolean setInputHandler) throws CoreException {
		int argLength = 1; // at least one user property - timestamp
		if (arguments != null) {
			argLength += arguments.length;
		}		
		if (baseDir != null && baseDir.length() > 0) {
			argLength++;
		}
		String[] runnerArgs = new String[argLength];
		if (arguments != null) {
			System.arraycopy(arguments, 0, runnerArgs, 0, arguments.length);
		}
		if (baseDir != null && baseDir.length() > 0) {
			runnerArgs[runnerArgs.length - 2] = BASE_DIR_PREFIX + baseDir;
		}
		runnerArgs[runnerArgs.length -1] = idProperty.toString();
		
		AntRunner runner= new AntRunner();
		runner.setBuildFileLocation(location.toOSString());
		boolean captureOutput= ExternalToolsUtil.getCaptureOutput(configuration);
		if (captureOutput) {
        if (fMode.equals(ILaunchManager.DEBUG_MODE)) {
            runner.addBuildLogger(ANT_DEBUG_LOGGER_CLASS);
			} else {
			runner.addBuildLogger(ANT_LOGGER_CLASS);
			}
		} else {
			runner.addBuildLogger(NULL_LOGGER_CLASS);
		}
		if (setInputHandler) {
			runner.setInputHandler(INPUT_HANDLER_CLASS);
		} else {
			runner.setInputHandler(""); //$NON-NLS-1$
		}
		runner.setArguments(runnerArgs);
		if (userProperties != null) {
			runner.addUserProperties(userProperties);
		}

		if (propertyFiles != null) {
			runner.setPropertyFiles(propertyFiles);
		}

		if (targets != null) {
			runner.setExecutionTargets(targets);
		}

		if (customClasspath != null) {
			runner.setCustomClasspath(customClasspath);
		}

		if (antHome != null) {
			runner.setAntHome(antHome);
		}
		return runner;
	}

	private void handleException(final CoreException e, final String title) {
		IPreferenceStore store= AntUIPlugin.getDefault().getPreferenceStore();
		if (store.getBoolean(IAntUIPreferenceConstants.ANT_ERROR_DIALOG)) {
			AntUIPlugin.getStandardDisplay().asyncExec(new Runnable() {
				public void run() {
					MessageDialogWithToggle.openError(null, title, e.getMessage(), AntLaunchConfigurationMessages.AntLaunchDelegate_22, false, AntUIPlugin.getDefault().getPreferenceStore(), IAntUIPreferenceConstants.ANT_ERROR_DIALOG); //$NON-NLS-1$
				}
			});
		}
	}

	private void setProcessAttributes(IProcess process, String idStamp, StringBuffer commandLine, boolean captureOutput) {
		// link the process to the Eclipse build logger via a timestamp
        if (!fUserSpecifiedLogger) {
            process.setAttribute(AbstractEclipseBuildLogger.ANT_PROCESS_ID, idStamp);
        }
		
		// create "fake" command line for the process
		if (commandLine != null) {
			process.setAttribute(IProcess.ATTR_CMDLINE, commandLine.toString());
		}
		if (captureOutput && !fUserSpecifiedLogger) {
			TaskLinkManager.registerAntBuild(process);
		}
	}

	private StringBuffer generateCommandLine(IPath location, String[] arguments, Map userProperties, String[] propertyFiles, String[] targets, String antHome, String basedir, boolean separateVM, boolean captureOutput, boolean setInputHandler) {
		StringBuffer commandLine= new StringBuffer();

		if (!separateVM) {
			commandLine.append("ant"); //$NON-NLS-1$
		}
		
		if (arguments != null) {
			for (int i = 0; i < arguments.length; i++) {
				commandLine.append(' ');
				commandLine.append(arguments[i]);
			}
		}
		
		AntCorePreferences prefs= AntCorePlugin.getPlugin().getPreferences();
		if (propertyFiles == null) { //global
			String[] files= prefs.getCustomPropertyFiles();
			for (int i = 0; i < files.length; i++) {
				String path = files[i];
				commandLine.append(" -propertyfile \""); //$NON-NLS-1$
				commandLine.append(path);
				commandLine.append('\"');
			}
		} else {//"local" configuration
			for (int i = 0; i < propertyFiles.length; i++) {
				String path = propertyFiles[i];
				commandLine.append(" -propertyfile \""); //$NON-NLS-1$
				commandLine.append(path);
				commandLine.append('\"');
			}
		}
		//"local" configuration
		if (userProperties != null) {
			Iterator keys = userProperties.keySet().iterator();
			String key;
			while (keys.hasNext()) {
				key= (String)keys.next();
				appendProperty(commandLine, key, (String)userProperties.get(key));
			}
		}
		
		//global
		List properties= null;
		if (!separateVM) {
			properties= prefs.getProperties();
		} else {
			properties= prefs.getRemoteAntProperties();
		}
		
		String key;
		//if we have user properties this means that the user has chosen to override the global properties
		//if in a separate VM and have only two user properties these are really only Eclipse generated properties
		//and the user is still using the global properties
		boolean useGlobalProperties = userProperties == null || (separateVM && userProperties.size() == 2);
		if (useGlobalProperties) {
			for (Iterator iter = properties.iterator(); iter.hasNext();) {
				Property property = (Property) iter.next();
				key= property.getName();
				String value= property.getValue(false);
				if (value != null) {
					appendProperty(commandLine, key, value);
				}
			}
		}
		
		if (basedir != null && basedir.length() > 0) {
			appendProperty(commandLine, "basedir", basedir); //$NON-NLS-1$
		}
		
		if (antHome != null) {
			commandLine.append(" \"-Dant.home="); //$NON-NLS-1$
			commandLine.append(antHome);
			commandLine.append('\"');
		}
		
		if (separateVM) { 
			if (commandLine.indexOf("-logger") == -1) { //$NON-NLS-1$
				if (captureOutput) {
				commandLine.append(" -logger "); //$NON-NLS-1$
				if (fMode.equals(ILaunchManager.DEBUG_MODE)) {
					commandLine.append(REMOTE_ANT_DEBUG_LOGGER_CLASS);
					} else {
					commandLine.append(REMOTE_ANT_LOGGER_CLASS);
				}
				}
			} else {
			    fUserSpecifiedLogger= true;
            }
			if (commandLine.indexOf("-inputhandler") == -1 && setInputHandler) { //$NON-NLS-1$
				commandLine.append(" -inputhandler "); //$NON-NLS-1$
				commandLine.append(REMOTE_INPUT_HANDLER_CLASS);
			}
		} else {
			if (commandLine.indexOf("-inputhandler") == -1 && setInputHandler) { //$NON-NLS-1$
				commandLine.append(" -inputhandler "); //$NON-NLS-1$
				commandLine.append(INPUT_HANDLER_CLASS);
			}
            if (commandLine.indexOf("-logger") == -1) { //$NON-NLS-1$
    			commandLine.append(" -logger "); //$NON-NLS-1$
    			if (fMode.equals(ILaunchManager.DEBUG_MODE)) {
    				commandLine.append(ANT_DEBUG_LOGGER_CLASS);
    			} else if (captureOutput) {
    				commandLine.append(ANT_LOGGER_CLASS);
    			} else {
    				commandLine.append(NULL_LOGGER_CLASS);
    			}
            }
		}
		
		if (separateVM) {
			appendTaskAndTypes(prefs, commandLine);
		}
		commandLine.append(" -buildfile \""); //$NON-NLS-1$
		commandLine.append(location.toOSString());
		commandLine.append('\"');
		
		if (targets != null) {
			for (int i = 0; i < targets.length; i++) {
				commandLine.append(" \""); //$NON-NLS-1$
				commandLine.append(targets[i]);
				commandLine.append('\"');
			}
		}
		return commandLine;
	}
	
	private void appendTaskAndTypes(AntCorePreferences prefs, StringBuffer commandLine) {
		List tasks= prefs.getRemoteTasks();
		Iterator itr= tasks.iterator();
		while (itr.hasNext()) {
			Task task = (Task) itr.next();
			commandLine.append(" -eclipseTask "); //$NON-NLS-1$
			commandLine.append(task.getTaskName());
			commandLine.append(',');
			commandLine.append(task.getClassName());
		}
		
		List types= prefs.getRemoteTypes();
		itr= types.iterator();
		while (itr.hasNext()) {
			Type type = (Type) itr.next();
			commandLine.append(" -eclipseType "); //$NON-NLS-1$
			commandLine.append(type.getTypeName());
			commandLine.append(',');
			commandLine.append(type.getClassName());
		}
	}

	private void appendProperty(StringBuffer commandLine, String name, String value) {
		commandLine.append(" \"-D"); //$NON-NLS-1$
		commandLine.append(name);
		commandLine.append('='); 
		commandLine.append(value);
		commandLine.append("\""); //$NON-NLS-1$
	}
	
	private void runInSeparateVM(ILaunchConfiguration configuration, ILaunch launch, IProgressMonitor monitor, String idStamp, String antHome, int port, int requestPort, StringBuffer commandLine, boolean captureOutput, boolean setInputHandler) throws CoreException {
        boolean debug= fMode.equals(ILaunchManager.DEBUG_MODE);
		if (captureOutput) {
			if (debug) {
				RemoteAntDebugBuildListener listener= new RemoteAntDebugBuildListener(launch);
				if (requestPort != -1) {
					listener.startListening(port, requestPort);
				}
			} else if (!fUserSpecifiedLogger) {
				RemoteAntBuildListener client= new RemoteAntBuildListener(launch);
				if (port != -1) {
					client.startListening(port);
				}
			}
		}
		
		ILaunchConfigurationWorkingCopy copy= configuration.getWorkingCopy();
		copy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROGRAM_ARGUMENTS, commandLine.toString());
		StringBuffer vmArgs= generateVMArguments(copy, setInputHandler, antHome);
		copy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, vmArgs.toString());
        copy.setAttribute(IDebugUIConstants.ATTR_PRIVATE, true);
        if (copy.getAttribute(IAntUIConstants.ATTR_DEFAULT_VM_INSTALL, false)) {
        	setDefaultVM(configuration, copy);
        }
        if (debug) { //do not allow launch in foreground bug 83254
            copy.setAttribute(IDebugUIConstants.ATTR_LAUNCH_IN_BACKGROUND, true);
        }
        
        //set the ANT_HOME environment variable
        Map vars = copy.getAttribute(ILaunchManager.ATTR_ENVIRONMENT_VARIABLES, new HashMap(1));
		vars.put("ANT_HOME", antHome); //$NON-NLS-1$
		copy.setAttribute(ILaunchManager.ATTR_ENVIRONMENT_VARIABLES, vars);

		//copy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, "-Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000"); //$NON-NLS-1$
		IProgressMonitor subMonitor= new SubProgressMonitor(monitor, 10);
		AntJavaLaunchDelegate delegate= new AntJavaLaunchDelegate();
		delegate.preLaunchCheck(copy, ILaunchManager.RUN_MODE, subMonitor);
		delegate.launch(copy, ILaunchManager.RUN_MODE, launch, subMonitor);
		final IProcess[] processes= launch.getProcesses();
		for (int i = 0; i < processes.length; i++) {
			setProcessAttributes(processes[i], idStamp, null, captureOutput);
		}

		if (CommonTab.isLaunchInBackground(copy)) {
			// refresh resources after process finishes
			if (RefreshTab.getRefreshScope(configuration) != null) {
				BackgroundResourceRefresher refresher = new BackgroundResourceRefresher(configuration, processes[0]);
				refresher.startBackgroundRefresh();
			}
		} else {
			final boolean[] terminated= new boolean[1];
			terminated[0]= launch.isTerminated();
			IDebugEventSetListener listener= new IDebugEventSetListener() {
				public void handleDebugEvents(DebugEvent[] events) {
					for (int i = 0; i < events.length; i++) {
						DebugEvent event = events[i];
						for (int j= 0, numProcesses= processes.length; j < numProcesses; j++) {
							if (event.getSource() == processes[j] && event.getKind() == DebugEvent.TERMINATE) {
								terminated[0]= true;
								break;
							}
						}
					}
				}
			};
			DebugPlugin.getDefault().addDebugEventListener(listener);
			monitor.subTask(AntLaunchConfigurationMessages.AntLaunchDelegate_28); //$NON-NLS-1$
			while (!monitor.isCanceled() && !terminated[0]) {
				try {
					Thread.sleep(50);
				} catch (InterruptedException e) {
				}
			}
			DebugPlugin.getDefault().removeDebugEventListener(listener);
			if (!monitor.isCanceled()) {
				// refresh resources
				RefreshTab.refreshResources(configuration, monitor);
			}
		}
	}

	private void setDefaultVM(ILaunchConfiguration configuration, ILaunchConfigurationWorkingCopy copy) {
		try {
			JavaRuntime.getJavaProject(configuration);
			//remove the vm name and install type for the Java launching concept of default VM
			copy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_INSTALL_NAME, (String)null);
			copy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_INSTALL_TYPE, (String)null);
		} catch (CoreException ce) {
			//not in a Java project
			IVMInstall defaultVMInstall= JavaRuntime.getDefaultVMInstall();
			copy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_INSTALL_NAME, defaultVMInstall.getName());
			copy.setAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_INSTALL_TYPE, defaultVMInstall.getVMInstallType().getId());
		}
	}
	
	private StringBuffer generateVMArguments(ILaunchConfiguration config, boolean setInputHandler, String antHome) {
		StringBuffer vmArgs= new StringBuffer();
		try {
			String configArgs= config.getAttribute(IJavaLaunchConfigurationConstants.ATTR_VM_ARGUMENTS, (String)null);
			if (configArgs != null) {
				vmArgs.append(configArgs);
				vmArgs.append(' ');
			}
		} catch (CoreException e) {
		}
	
		vmArgs.append("-Dant.home=\""); //$NON-NLS-1$
		vmArgs.append(antHome);
		vmArgs.append("\" "); //$NON-NLS-1$
		File antLibDir= new File(antHome, "lib"); //$NON-NLS-1$
		vmArgs.append("-Dant.library.dir=\""); //$NON-NLS-1$
		vmArgs.append(antLibDir.getAbsolutePath());
		vmArgs.append('\"');

		if (setInputHandler) {
			String swtLocation= getSWTLibraryLocation();
			if (swtLocation != null) {
				vmArgs.append(" -Djava.library.path=\""); //$NON-NLS-1$
				String javaLibPath= System.getProperty("java.library.path"); //$NON-NLS-1$
                javaLibPath= stripUnescapedQuotes(javaLibPath);
				if (javaLibPath != null) {
					vmArgs.append(javaLibPath);
					if (vmArgs.charAt(vmArgs.length() - 1) != File.pathSeparatorChar) {
						vmArgs.append(File.pathSeparatorChar);
					}
				}
				vmArgs.append(swtLocation);
				vmArgs.append('"');
			}
		}
		return vmArgs;
    }

    private String stripUnescapedQuotes(String javaLibPath) {
        StringBuffer buf = new StringBuffer(javaLibPath.length());
        for (int i = 0; i < javaLibPath.length(); i++) {
            char c = javaLibPath.charAt(i);
            switch (c) {
                case '"':
                    if (i != 0 && javaLibPath.charAt(i-1) == '\\') {
                        buf.append(c); //$NON-NLS-1$
                    }
                    break;
                default:
                    buf.append(c);
                    break;
            }
        }
        return buf.toString();
    }

    /* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.LaunchConfigurationDelegate#getBuildOrder(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String)
	 */
	protected IProject[] getBuildOrder(ILaunchConfiguration configuration, String mode) throws CoreException {
		String scope = null;
		try {
			scope = configuration.getAttribute(AntBuildTab.ATTR_BUILD_SCOPE, (String)null);
		} catch (CoreException e) {
			return null;
		}
		if (scope == null) {
			return null;
		}
		IProject[] projects = AntBuildTab.getBuildProjects(scope);
		boolean isRef = AntBuildTab.isIncludeReferencedProjects(configuration);
		if (isRef) {
			return computeReferencedBuildOrder(projects);
		}
		return computeBuildOrder(projects);
	}

	private String getSWTLibraryLocation() {
       if (fgSWTLibraryLocation == null) {
            Bundle bundle= Platform.getBundle("org.eclipse.swt"); //$NON-NLS-1$
            BundleDescription description= Platform.getPlatformAdmin().getState(false).getBundle(bundle.getBundleId());
            BundleDescription[] fragments= description.getFragments();
            if (fragments == null || fragments.length == 0) {
                return null;
            }
            Bundle fragBundle= Platform.getBundle(fragments[0].getSymbolicName()); //$NON-NLS-1$
            try {
                URL url= Platform.asLocalURL(fragBundle.getEntry("/")); //$NON-NLS-1$
                IPath path= new Path(url.getPath());
                path= path.removeTrailingSeparator();
                fgSWTLibraryLocation= path.toOSString();
            } catch (IOException e) {
            }
		}
        return fgSWTLibraryLocation;
	}
    
     /* (non-Javadoc)
     * @see org.eclipse.debug.core.model.LaunchConfigurationDelegate#getBreakpoints(org.eclipse.debug.core.ILaunchConfiguration)
     */
    protected IBreakpoint[] getBreakpoints(ILaunchConfiguration configuration) {
         IBreakpointManager breakpointManager = DebugPlugin.getDefault().getBreakpointManager();
         if (!breakpointManager.isEnabled()) {
             // no need to check breakpoints individually.
             return null;
         }
         return breakpointManager.getBreakpoints(IAntDebugConstants.ID_ANT_DEBUG_MODEL);
     }
}
