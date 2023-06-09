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
package org.eclipse.jdt.internal.ui.text.correction;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.jface.util.Assert;

import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;

import org.eclipse.jdt.internal.corext.util.Messages;

import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate;
import org.eclipse.jdt.launching.AbstractVMRunner;
import org.eclipse.jdt.launching.ExecutionArguments;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.IVMRunner;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.LibraryLocation;
import org.eclipse.jdt.launching.VMRunnerConfiguration;

import org.eclipse.jdt.internal.ui.JavaPlugin;

/**
 * Launch configuration delegate to launch the computation of the serial version ID.
 * 
 * @since 3.1
 */
public final class SerialVersionLaunchConfigurationDelegate extends AbstractJavaLaunchConfigurationDelegate {

	/**
	 * VM runner for the serial version ID computation.
	 */
	public final class SerialVersionRunner extends AbstractVMRunner {

		/** The vm install */
		private final IVMInstall fInstall;

		/**
		 * Creates a new serial version runner.
		 * 
		 * @param install The vm install to base on
		 */
		public SerialVersionRunner(final IVMInstall install) {
			Assert.isNotNull(install);

			fInstall= install;
		}

		/**
		 * Flattens the indicated class path to a string.
		 * 
		 * @param path the class path to flatten
		 * @return the flattened class path
		 */
		private String flattenClassPath(final String[] path) {
			Assert.isNotNull(path);
			int count= 0;
			if (path.length == 0)
				return ""; //$NON-NLS-1$
			final StringBuffer buffer= new StringBuffer();
			for (int index= 0; index < path.length; index++) {
				if (count > 0)
					buffer.append(File.pathSeparator);
				buffer.append(path[index]);
				count++;
			}
			return buffer.toString();
		}

		/**
		 * Construct and return a String containing the full path of a java executable command such as 'java' or 'javaw.exe'. If the configuration specifies an explicit executable, that is used.
		 * 
		 * @return full path to java executable
		 * @exception CoreException if unable to locate an executable
		 */
		private String getJavaExecutable(final VMRunnerConfiguration configuration) throws CoreException {
			Assert.isNotNull(configuration);
			String command= null;
			final Map map= configuration.getVMSpecificAttributesMap();
			if (map != null)
				command= (String) map.get(IJavaLaunchConfigurationConstants.ATTR_JAVA_COMMAND);
			if (command == null) {
				final File executable= findJavaExecutable(fInstall.getInstallLocation());
				if (executable == null)
					abort(Messages.format(CorrectionMessages.SerialVersionHashProposal_unable_locate_executable, new String[] { fInstall.getName()}), null, IJavaLaunchConfigurationConstants.ERR_INTERNAL_ERROR);
				return executable.getAbsolutePath();
			}
			final String location= fInstall.getInstallLocation().getAbsolutePath() + File.separatorChar;
			File executable= new File(location + "bin" + File.separatorChar + command); //$NON-NLS-1$ //$NON-NLS-2$
			if (executable.exists() && executable.isFile())
				return executable.getAbsolutePath();
			executable= new File(executable.getAbsolutePath() + ".exe"); //$NON-NLS-1$
			if (executable.exists() && executable.isFile())
				return executable.getAbsolutePath();
			executable= new File(location + "jre" + File.separatorChar + "bin" + File.separatorChar + command); //$NON-NLS-1$ //$NON-NLS-2$
			if (executable.exists() && executable.isFile())
				return executable.getAbsolutePath();
			executable= new File(executable.getAbsolutePath() + ".exe"); //$NON-NLS-1$
			if (executable.exists() && executable.isFile())
				return executable.getAbsolutePath();
			abort(Messages.format(CorrectionMessages.SerialVersionHashProposal_wrong_executable, new String[] { command, fInstall.getName()}), null, IJavaLaunchConfigurationConstants.ERR_INTERNAL_ERROR);
			return null;
		}

		/*
		 * @see AbstractVMRunner#getPluginIdentifier()
		 */
		protected final String getPluginIdentifier() {
			return JavaPlugin.getPluginId();
		}

		/*
		 * @see org.eclipse.jdt.launching.IVMRunner#run(org.eclipse.jdt.launching.VMRunnerConfiguration, org.eclipse.debug.core.ILaunch, org.eclipse.core.runtime.IProgressMonitor)
		 */
		public final void run(final VMRunnerConfiguration configuration, final ILaunch launch, final IProgressMonitor monitor) throws CoreException {
			Assert.isNotNull(configuration);
			Assert.isNotNull(launch);
			Assert.isNotNull(monitor);
			final IProgressMonitor subMonitor= new SubProgressMonitor(monitor, 1);
			subMonitor.beginTask(CorrectionMessages.SerialVersionLaunchConfigurationDelegate_launching_vm, 2);
			try {
				subMonitor.subTask(CorrectionMessages.SerialVersionLaunchConfigurationDelegate_constructing_command_line);
				final List arguments= new ArrayList();
				arguments.add(getJavaExecutable(configuration));
				final String[] vmArguments= combineVmArgs(configuration, fInstall);
				for (int index= 0; index < vmArguments.length; index++)
					arguments.add(vmArguments[index]);
				String[] bootClassPath= configuration.getBootClassPath();
				final String[] classPath= configuration.getClassPath();
				String[] combinedClassPath= null;
				LibraryLocation[] locations= null;
				if (bootClassPath == null) {
					locations= JavaRuntime.getLibraryLocations(fInstall);
					bootClassPath= new String[locations.length];
					for (int index= 0; index < locations.length; index++)
						bootClassPath[index]= locations[index].getSystemLibraryPath().toOSString();
				}
				combinedClassPath= new String[bootClassPath.length + classPath.length];
				int offset= 0;
				for (int index= 0; index < bootClassPath.length; index++) {
					combinedClassPath[offset]= bootClassPath[index];
					offset++;
				}
				for (int index= 0; index < classPath.length; index++) {
					combinedClassPath[offset]= classPath[index];
					offset++;
				}
				if (combinedClassPath.length > 0) {
					arguments.add("-classpath"); //$NON-NLS-1$
					arguments.add(flattenClassPath(combinedClassPath));
				}
				arguments.add(configuration.getClassToLaunch());
				final String[] programArguments= configuration.getProgramArguments();
				for (int index= 0; index < programArguments.length; index++)
					arguments.add(programArguments[index]);
				final String[] commandLine= new String[arguments.size()];
				arguments.toArray(commandLine);
				if (monitor.isCanceled())
					return;
				subMonitor.worked(1);
				subMonitor.subTask(CorrectionMessages.SerialVersionLaunchConfigurationDelegate_starting_vm);
				final Process process= exec(commandLine, null);
				if (process != null) {
					try {
						process.waitFor();
					} catch (InterruptedException exception) {
						// Do nothing
					}
					StringBuffer buffer= new StringBuffer();
					BufferedReader reader= new BufferedReader(new InputStreamReader(process.getInputStream()));
					try {
						int result= 0;
						while (reader.ready()) {
							result= reader.read();
							if (result >= 0)
								buffer.append((char) result);
						}
						String string= buffer.toString();
						int start= string.indexOf(RESULT_PREFIX);
						if (start >= 0) {
							int end= string.indexOf(RESULT_POSTFIX, start);
							if (end >= 0)
								fSerialVersionID= Long.valueOf(string.substring(start + RESULT_PREFIX.length(), end)).longValue();
						}
					} catch (IOException exception) {
						JavaPlugin.log(exception);
						fErrorMessage= exception.getLocalizedMessage();
					}
					buffer= new StringBuffer();
					reader= new BufferedReader(new InputStreamReader(process.getErrorStream()));
					try {
						int result= 0;
						while (reader.ready()) {
							result= reader.read();
							if (result >= 0)
								buffer.append((char) result);
						}
						String string= buffer.toString();
						int start= string.indexOf(ERROR_PREFIX);
						if (start >= 0) {
							int end= string.indexOf(ERROR_POSTFIX, start);
							if (end >= 0)
								fErrorMessage= string.substring(start + ERROR_PREFIX.length(), end);
						}
					} catch (IOException exception) {
						JavaPlugin.log(exception);
						fErrorMessage= exception.getLocalizedMessage();
					}
					if (monitor.isCanceled())
						process.destroy();
				}
			} finally {
				subMonitor.done();
			}
		}
	}

	/** The serial version computation error postfix */
	public static final String ERROR_POSTFIX= "__SerialVersionComputationErrorPostfix__"; //$NON-NLS-1$

	/** The serial version computation error prefix */
	public static final String ERROR_PREFIX= "__SerialVersionComputationErrorPrefix__"; //$NON-NLS-1$

	/** The list of java executable locations */
	private static final String[] fgExecutableLocations= { "bin" + File.separatorChar + "javaw", "bin" + File.separatorChar + "javaw.exe", "jre" + File.separatorChar + "bin" + File.separatorChar + "javaw", "jre" + File.separatorChar + "bin" + File.separatorChar + "javaw.exe", "bin" + File.separatorChar + "java", "bin" + File.separatorChar + "java.exe", "jre" + File.separatorChar + "bin" + File.separatorChar + "java", "jre" + File.separatorChar + "bin" + File.separatorChar + "java.exe"}; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$ //$NON-NLS-9$ //$NON-NLS-10$ //$NON-NLS-11$ //$NON-NLS-12$ //$NON-NLS-13$ //$NON-NLS-14$ //$NON-NLS-15$ //$NON-NLS-16$ //$NON-NLS-17$ //$NON-NLS-18$ //$NON-NLS-19$ //$NON-NLS-20$

	/** The serial version computation result postfix */
	public static final String RESULT_POSTFIX= "__SerialVersionComputationResultPostfix__"; //$NON-NLS-1$

	/** The serial version computation result prefix */
	public static final String RESULT_PREFIX= "__SerialVersionComputationResultPrefix__"; //$NON-NLS-1$

	/**
	 * Attempts to find the java executable in the specified location.
	 * 
	 * @param location the location of the vm installation
	 * @return the corresponding java executable, or <code>null</code>
	 */
	public static File findJavaExecutable(final File location) {
		Assert.isNotNull(location);

		File file= null;
		for (int index= 0; index < fgExecutableLocations.length; index++) {

			file= new File(location, fgExecutableLocations[index]);
			if (file.isFile())
				return file;
		}
		return null;
	}

	/** The error message */
	protected String fErrorMessage= null;

	/** The computed serial version id */
	protected long fSerialVersionID= AbstractSerialVersionProposal.SERIAL_VALUE;

	/**
	 * Returns any error message that occurred during the computation.
	 * 
	 * @return The error message, or <code>null</code>
	 */
	public final String getErrorMessage() {
		return fErrorMessage;
	}

	/**
	 * Returns the computed serial version ID.
	 * 
	 * @return The computed serial version ID
	 */
	public final long getSerialVersionID() {
		return fSerialVersionID;
	}

	/*
	 * @see org.eclipse.debug.core.model.ILaunchConfigurationDelegate#launch(org.eclipse.debug.core.ILaunchConfiguration,java.lang.String, org.eclipse.debug.core.ILaunch,org.eclipse.core.runtime.IProgressMonitor)
	 */
	public final void launch(final ILaunchConfiguration configuration, final String mode, final ILaunch launch, IProgressMonitor monitor) throws CoreException {
		Assert.isNotNull(configuration);
		Assert.isNotNull(monitor);
		try {
			monitor.beginTask(MessageFormat.format("{0}...", new String[] { configuration.getName()}), 4); //$NON-NLS-1$
			if (monitor.isCanceled())
				return;

			monitor.subTask(CorrectionMessages.SerialVersionLaunchConfigurationDelegate_verifying_launch_attributes);

			final String type= verifyMainTypeName(configuration);
			final IVMInstall install= verifyVMInstall(configuration);
			final IVMRunner runner= new SerialVersionRunner(install);
			monitor.worked(1);

			monitor.subTask(CorrectionMessages.SerialVersionLaunchConfigurationDelegate_setting_up);

			final String[] environment= DebugPlugin.getDefault().getLaunchManager().getEnvironment(configuration);
			final String programArguments= getProgramArguments(configuration);
			final String vmArguments= getVMArguments(configuration);
			final ExecutionArguments execArguments= new ExecutionArguments(vmArguments, programArguments);
			final Map attributes= getVMSpecificAttributesMap(configuration);
			final String[] classpath= getClasspath(configuration);

			monitor.worked(1);

			final VMRunnerConfiguration vmConfiguration= new VMRunnerConfiguration(type, classpath);
			vmConfiguration.setProgramArguments(execArguments.getProgramArgumentsArray());
			vmConfiguration.setEnvironment(environment);
			vmConfiguration.setVMArguments(execArguments.getVMArgumentsArray());
			vmConfiguration.setVMSpecificAttributesMap(attributes);
			vmConfiguration.setBootClassPath(getBootpath(configuration));

			if (monitor.isCanceled())
				return;

			monitor.subTask(CorrectionMessages.SerialVersionLaunchConfigurationDelegate_launching_computation);
			monitor.worked(1);

			runner.run(vmConfiguration, launch, monitor);

			if (monitor.isCanceled())
				return;

		} finally {
			monitor.done();
		}
	}
}
