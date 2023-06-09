package org.eclipse.pde.internal.ui.launcher;

/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.TreeMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.internal.junit.launcher.JUnitBaseLaunchConfiguration;
import org.eclipse.jdt.launching.ExecutionArguments;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.SocketUtil;
import org.eclipse.jdt.launching.VMRunnerConfiguration;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.core.plugin.IFragment;
import org.eclipse.pde.core.plugin.IFragmentModel;
import org.eclipse.pde.core.plugin.IPluginExtension;
import org.eclipse.pde.core.plugin.IPluginImport;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.core.ClasspathHelper;
import org.eclipse.pde.internal.core.ModelEntry;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.core.PDEState;
import org.eclipse.pde.internal.core.PluginModelManager;
import org.eclipse.pde.internal.core.TargetPlatform;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.PDEUIMessages;
import org.eclipse.update.configurator.ConfiguratorUtils;

/**
 * Launch configuration delegate for a plain JUnit test.
 */
public class JUnitLaunchConfiguration extends JUnitBaseLaunchConfiguration implements ILauncherSettings {

	public static final String CORE_APPLICATION = "org.eclipse.pde.junit.runtime.coretestapplication"; //$NON-NLS-1$
	public static final String LEGACY_CORE_APPLICATION = "org.eclipse.pde.junit.runtime.legacyCoretestapplication"; //$NON-NLS-1$
	public static final String UI_APPLICATION = "org.eclipse.pde.junit.runtime.uitestapplication"; //$NON-NLS-1$
	public static final String LEGACY_UI_APPLICATION = "org.eclipse.pde.junit.runtime.legacyUItestapplication"; //$NON-NLS-1$
	
	protected static IPluginModelBase[] registryPlugins;
	protected File fConfigDir = null;

	public void launch(
		ILaunchConfiguration configuration,
		String mode,
		ILaunch launch,
		IProgressMonitor monitor)
		throws CoreException {
		try {
			fConfigDir = null;
			monitor.beginTask("", 6); //$NON-NLS-1$
			IType[] testTypes = getTestTypes(configuration, monitor);
			monitor.worked(1);
			
			String workspace = configuration.getAttribute(LOCATION + "0", getDefaultWorkspace(configuration)); //$NON-NLS-1$
			if (!LauncherUtils.clearWorkspace(configuration, workspace, new SubProgressMonitor(monitor, 1))) {
				monitor.setCanceled(true);
				return;
			}

			if (configuration.getAttribute(CONFIG_CLEAR_AREA, false))
				LauncherUtils.clearConfigArea(getConfigDir(configuration), new SubProgressMonitor(monitor, 1));
			launch.setAttribute(ILauncherSettings.CONFIG_LOCATION, getConfigDir(configuration).toString());
			
			IVMInstall launcher = LauncherUtils.createLauncher(configuration);
			monitor.worked(1);

			int port = SocketUtil.findFreePort();
			VMRunnerConfiguration runnerConfig =
				createVMRunner(configuration, testTypes, port, mode);
			if (runnerConfig == null) {
				monitor.setCanceled(true);
				return;
			} 
			monitor.worked(1);
			
			setDefaultSourceLocator(launch, configuration);
			LauncherUtils.synchronizeManifests(configuration, getConfigDir(configuration));
			launch.setAttribute(PORT_ATTR, Integer.toString(port));
			launch.setAttribute(TESTTYPE_ATTR, testTypes[0].getHandleIdentifier());
			PDEPlugin.getDefault().getLaunchListener().manage(launch);
			launcher.getVMRunner(mode).run(runnerConfig, launch, monitor);
			monitor.worked(1);
		} catch (CoreException e) {
			monitor.setCanceled(true);
			throw e;
		}
	}
	
	/*
	 * @see JUnitBaseLauncherDelegate#configureVM(IType[], int, String)
	 */
	protected VMRunnerConfiguration createVMRunner(
		ILaunchConfiguration configuration,
		IType[] testTypes,
		int port,
		String runMode)
		throws CoreException {
		String[] classpath = LauncherUtils.constructClasspath(configuration);
		if (classpath == null) {
			abort(PDEUIMessages.WorkbenchLauncherConfigurationDelegate_noStartup, null, IStatus.OK);
		}

		// Program arguments
		String[] programArgs =
			computeProgramArguments(configuration, testTypes, port, runMode);
		if (programArgs == null)
			return null;

		// Environment variables
		String[] envp =
			DebugPlugin.getDefault().getLaunchManager().getEnvironment(configuration);

		VMRunnerConfiguration runnerConfig =
			new VMRunnerConfiguration("org.eclipse.core.launcher.Main", classpath); //$NON-NLS-1$
		runnerConfig.setVMArguments(computeVMArguments(configuration));
		runnerConfig.setProgramArguments(programArgs);
		runnerConfig.setEnvironment(envp);
		runnerConfig.setVMSpecificAttributesMap(LauncherUtils.getVMSpecificAttributes(configuration));
		return runnerConfig;
	}

	protected String getTestPluginId(ILaunchConfiguration configuration)
		throws CoreException {
		IJavaProject javaProject = getJavaProject(configuration);
		IPluginModelBase model =
			PDECore.getDefault().getModelManager().findModel(javaProject.getProject());
		if (model == null)
			throw new CoreException(
				new Status(
					IStatus.ERROR,
					PDEPlugin.PLUGIN_ID,
					IStatus.ERROR,
					PDEUIMessages.JUnitLaunchConfiguration_error_notaplugin, //$NON-NLS-1$
					null));
		if (model instanceof IFragmentModel)
			return ((IFragmentModel)model).getFragment().getPluginId();

		return model.getPluginBase().getId();
	}
	
	protected void abort(String message, Throwable exception, int code)
		throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, PDEPlugin.PLUGIN_ID, code, message, exception));
	}
	
	protected String[] computeProgramArguments(
		ILaunchConfiguration configuration,
		IType[] testTypes,
		int port,
		String runMode)
		throws CoreException {
		ArrayList programArgs = new ArrayList();
		
		// Get the list of plug-ins to run
		TreeMap pluginMap = LauncherUtils.getPluginsToRun(configuration);
		if (pluginMap == null)
			return null;		
		addRequiredPlugins(pluginMap);
		
		programArgs.add("-version"); //$NON-NLS-1$
		programArgs.add("3"); //$NON-NLS-1$
		
		// Specify the application to launch based on the list of plug-ins to run.
		programArgs.add("-application"); //$NON-NLS-1$
		programArgs.add(getApplicationName(pluginMap, configuration));
		
		// If a product is specified, then add it to the program args
		if (configuration.getAttribute(USE_PRODUCT, false)) {
			programArgs.add("-product"); //$NON-NLS-1$
			programArgs.add(configuration.getAttribute(PRODUCT, "")); //$NON-NLS-1$
		} else {
			// Specify the application to test
			String testApplication = configuration.getAttribute(APP_TO_TEST, (String)null);
			if (testApplication != null && testApplication.length() > 0) {
				programArgs.add("-testApplication"); //$NON-NLS-1$
				programArgs.add(testApplication);
			}
		}
		
		// Specify the location of the runtime workbench
		String targetWorkspace =
			configuration.getAttribute(LOCATION + "0", getDefaultWorkspace(configuration)); //$NON-NLS-1$
		programArgs.add("-data"); //$NON-NLS-1$
		programArgs.add(targetWorkspace);
		
		// Create the platform configuration for the runtime workbench
		if (PDECore.getDefault().getModelManager().isOSGiRuntime()) {
			String productID = LauncherUtils.getProductID(configuration);
			LauncherUtils.createConfigIniFile(configuration,
					productID, pluginMap, getConfigDir(configuration));
			TargetPlatform.createPlatformConfigurationArea(
					pluginMap,
					getConfigDir(configuration),
					LauncherUtils.getContributingPlugin(productID));
		} else {
			TargetPlatform.createPlatformConfigurationArea(
					pluginMap,
					getConfigDir(configuration),
					LauncherUtils.getPrimaryPlugin());
			// Pre-OSGi platforms need the location of org.eclipse.core.boot specified
			IPluginModelBase bootModel = (IPluginModelBase)pluginMap.get("org.eclipse.core.boot"); //$NON-NLS-1$
			String bootPath = LauncherUtils.getBootPath(bootModel);
			if (bootPath != null && !bootPath.endsWith(".jar")) { //$NON-NLS-1$
				programArgs.add("-boot"); //$NON-NLS-1$
				programArgs.add("file:" + bootPath); //$NON-NLS-1$
			}			
		}
		
		programArgs.add("-configuration"); //$NON-NLS-1$
		if (PDECore.getDefault().getModelManager().isOSGiRuntime())
			programArgs.add("file:" + new Path(getConfigDir(configuration).getPath()).addTrailingSeparator().toString()); //$NON-NLS-1$
		else
			programArgs.add("file:" + new Path(getConfigDir(configuration).getPath()).append("platform.cfg").toString()); //$NON-NLS-1$ //$NON-NLS-2$
		
		// Specify the output folder names
		programArgs.add("-dev"); //$NON-NLS-1$
		if (PDECore.getDefault().getModelManager().isOSGiRuntime())
			programArgs.add(ClasspathHelper.getDevEntriesProperties(getConfigDir(configuration).toString() + "/dev.properties", pluginMap)); //$NON-NLS-1$
		else
			programArgs.add(ClasspathHelper.getDevEntries(true));
		
		// necessary for PDE to know how to load plugins when target platform = host platform
		// see PluginPathFinder.getPluginPaths()
		programArgs.add("-pdelaunch"); //$NON-NLS-1$

		// Create the .options file if tracing is turned on
		if (configuration.getAttribute(TRACING, false)
				&& !TRACING_NONE.equals(configuration.getAttribute(
						TRACING_CHECKED, (String) null))) {
			programArgs.add("-debug"); //$NON-NLS-1$
			String path = getConfigDir(configuration).getPath() + Path.SEPARATOR + ".options"; //$NON-NLS-1$
			programArgs.add(LauncherUtils.getTracingFileArgument(configuration, path));
		}
		
		// Add the program args entered by the user
		StringTokenizer tokenizer =
			new StringTokenizer(configuration.getAttribute(PROGARGS, "")); //$NON-NLS-1$
		while (tokenizer.hasMoreTokens()) {
			String token = tokenizer.nextToken();
			// be forgiving if people have tracing turned on and forgot
			// to remove the -debug from the program args field.
			if (token.equals("-debug") && programArgs.contains("-debug")) //$NON-NLS-1$ //$NON-NLS-2$
				continue;
			programArgs.add(token);
		}
		
		if (!programArgs.contains("-os")) { //$NON-NLS-1$
			programArgs.add("-os"); //$NON-NLS-1$
			programArgs.add(TargetPlatform.getOS());
		}
		if (!programArgs.contains("-ws")) { //$NON-NLS-1$
			programArgs.add("-ws"); //$NON-NLS-1$
			programArgs.add(TargetPlatform.getWS());
		}
		if (!programArgs.contains("-arch")) { //$NON-NLS-1$
			programArgs.add("-arch"); //$NON-NLS-1$
			programArgs.add(TargetPlatform.getOSArch());
		}
			
		if (keepAlive(configuration) && runMode.equals(ILaunchManager.DEBUG_MODE))
			programArgs.add("-keepalive"); //$NON-NLS-1$
		programArgs.add("-port"); //$NON-NLS-1$
		programArgs.add(Integer.toString(port));
		programArgs.add("-testpluginname"); //$NON-NLS-1$
		programArgs.add(getTestPluginId(configuration));
		String testFailureNames = configuration.getAttribute(JUnitBaseLaunchConfiguration.FAILURES_FILENAME_ATTR, ""); //$NON-NLS-1$
		if (testFailureNames.length() > 0) {
			programArgs.add("-testfailures"); //$NON-NLS-1$
			programArgs.add(testFailureNames);			
		}

		// a testname was specified just run the single test
		String testName =
			configuration.getAttribute(JUnitBaseLaunchConfiguration.TESTNAME_ATTR, ""); //$NON-NLS-1$
		if (testName.length() > 0) {
			programArgs.add("-test"); //$NON-NLS-1$
			programArgs.add(testTypes[0].getFullyQualifiedName() + ":" + testName); //$NON-NLS-1$
		} else {
			programArgs.add("-classnames"); //$NON-NLS-1$
			for (int i = 0; i < testTypes.length; i++)
			programArgs.add(testTypes[i].getFullyQualifiedName());
		}
		return (String[]) programArgs.toArray(new String[programArgs.size()]);
	}
	
	protected IPluginModelBase[] addRequiredPlugins(TreeMap pluginMap)
		throws CoreException {
		if (!pluginMap.containsKey("org.eclipse.pde.junit.runtime")) { //$NON-NLS-1$
			pluginMap.put(
				"org.eclipse.pde.junit.runtime", //$NON-NLS-1$
				findPlugin("org.eclipse.pde.junit.runtime")); //$NON-NLS-1$
		}
		if (!pluginMap.containsKey("org.eclipse.jdt.junit.runtime")) { //$NON-NLS-1$
			pluginMap.put(
				"org.eclipse.jdt.junit.runtime", //$NON-NLS-1$
				findPlugin("org.eclipse.jdt.junit.runtime")); //$NON-NLS-1$
		}
		if (!pluginMap.containsKey("org.junit")) { //$NON-NLS-1$
			pluginMap.put("org.junit", findPlugin("org.junit")); //$NON-NLS-1$ //$NON-NLS-2$
		}
		return (IPluginModelBase[]) pluginMap.values().toArray(
			new IPluginModelBase[pluginMap.size()]);
	}
	
	protected IPluginModelBase findPlugin(String id) throws CoreException {
		PluginModelManager manager = PDECore.getDefault().getModelManager();
		IPluginModelBase model = manager.findModel(id);
		if (model != null)
			return model;

		if (registryPlugins == null) {
			URL[] pluginPaths = ConfiguratorUtils.getCurrentPlatformConfiguration().getPluginPath();
			PDEState state = new PDEState(pluginPaths, false, new NullProgressMonitor());
			registryPlugins = state.getTargetModels();
		}

		for (int i = 0; i < registryPlugins.length; i++) {
			if (registryPlugins[i].getPluginBase().getId().equals(id))
				return registryPlugins[i];
		}
		abort(
			NLS.bind(PDEUIMessages.JUnitLaunchConfiguration_error_missingPlugin, id),
			null,
			IStatus.OK);
		return null;
	}
	
	protected String[] computeVMArguments(ILaunchConfiguration configuration) throws CoreException {
		return new ExecutionArguments(getVMArguments(configuration),"").getVMArgumentsArray();		 //$NON-NLS-1$
	}
	
	public String getProgramArguments(ILaunchConfiguration configuration)
		throws CoreException {
		return configuration.getAttribute(ILauncherSettings.PROGARGS, ""); //$NON-NLS-1$
	}
	
	public String getVMArguments(ILaunchConfiguration configuration)
		throws CoreException {
		return configuration.getAttribute(ILauncherSettings.VMARGS, ""); //$NON-NLS-1$
	}

	protected void setDefaultSourceLocator(ILaunch launch, ILaunchConfiguration configuration) throws CoreException {
		LauncherUtils.setDefaultSourceLocator(configuration, launch);
	}
	
	protected String getDefaultWorkspace(ILaunchConfiguration config) throws CoreException {
		if (config.getAttribute(APPLICATION, UI_APPLICATION).equals(UI_APPLICATION))
			return LauncherUtils.getDefaultPath().append("junit-workbench-workspace").toOSString(); //$NON-NLS-1$
		return LauncherUtils.getDefaultPath().append("junit-core-workspace").toOSString();				 //$NON-NLS-1$
	}
	
	protected String getApplicationName(TreeMap pluginMap, ILaunchConfiguration configuration) {
		try {
			String application = configuration.getAttribute(APPLICATION, (String)null);
			if (CORE_APPLICATION.equals(application)) {
				if (PDECore.getDefault().getModelManager().isOSGiRuntime())
					return CORE_APPLICATION;
				return LEGACY_CORE_APPLICATION;
			}			
		} catch (CoreException e) {
		}
				
		IPluginModelBase model = (IPluginModelBase)pluginMap.get("org.eclipse.ui"); //$NON-NLS-1$
		if (model != null) {
			IPluginExtension[] extensions = model.getPluginBase().getExtensions();
			for (int i = 0; i < extensions.length; i++) {
				String point = extensions[i].getPoint();
				if (point != null && point.equals("org.eclipse.core.runtime.applications")) { //$NON-NLS-1$
					if ("workbench".equals(extensions[i].getId())){ //$NON-NLS-1$
						return LEGACY_UI_APPLICATION;
					}
				}
			}
		}
		return UI_APPLICATION;
	}
	
	public static String getPluginID(ILaunchConfiguration configuration) {
		try {
			String projectID = configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, ""); //$NON-NLS-1$
			if (projectID.length() > 0) {
				IResource project = PDEPlugin.getWorkspace().getRoot().findMember(projectID);
				if (project != null && project instanceof IProject) {
					IPluginModelBase model = PDECore.getDefault().getModelManager().findModel((IProject)project);
					if (model != null) {
						return model.getPluginBase().getId();
					}
				}
			}
		} catch (CoreException e) {
		}
		return null;
	}
	
	public static boolean requiresUI(ILaunchConfiguration configuration) {
		String id = getPluginID(configuration);
		if (id != null) {
			IPluginModelBase[] models = getPluginAndPrereqs(id);
			for (int i = 0; i < models.length; i++) {
				if ("org.eclipse.swt".equals(models[i].getPluginBase().getId())) //$NON-NLS-1$
					return true;
			}
			return false;
		}
		return true;
	}
	
	public static IPluginModelBase[] getPluginAndPrereqs(String id) {
		TreeMap map = new TreeMap();
		addPluginAndPrereqs(id, map);
		if (!PDECore.getDefault().getModelManager().isOSGiRuntime()) {
			addPluginAndPrereqs("org.eclipse.core.boot", map); //$NON-NLS-1$
			addPluginAndPrereqs("org.eclipse.core.runtime", map); //$NON-NLS-1$
		}
		
		return (IPluginModelBase[])map.values().toArray(new IPluginModelBase[map.size()]);
	}
	
	protected static void addPluginAndPrereqs(String id, TreeMap map) {
		if (map.containsKey(id))
			return;
		
		ModelEntry entry = PDECore.getDefault().getModelManager().findEntry(id);
		if (entry == null)
			return;
		
		IPluginModelBase model = entry.getActiveModel();
		
		map.put(id, model);
		
		IPluginImport[] imports = model.getPluginBase().getImports();
		for (int i = 0; i < imports.length; i++) {
			addPluginAndPrereqs(imports[i].getId(), map);
		}
		
		if (model instanceof IFragmentModel) {
			addPluginAndPrereqs(((IFragmentModel) model).getFragment().getPluginId(), map);
		} else {
			IFragment[] fragments = PDECore.getDefault().findFragmentsFor(id, model.getPluginBase().getVersion());
			for (int i = 0; i < fragments.length; i++) {
				addPluginAndPrereqs(fragments[i].getId(), map);
			}
		}
	}

	
	protected File getConfigDir(ILaunchConfiguration config) {
		if (fConfigDir == null)
			fConfigDir = LauncherUtils.createConfigArea(config);
	
		if (!fConfigDir.exists())
			fConfigDir.mkdirs();
		return fConfigDir;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.LaunchConfigurationDelegate#getBuildOrder(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String)
	 */
	protected IProject[] getBuildOrder(ILaunchConfiguration configuration,
			String mode) throws CoreException {
		return computeBuildOrder(LauncherUtils.getAffectedProjects(configuration));
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.LaunchConfigurationDelegate#getProjectsForProblemSearch(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String)
	 */
	protected IProject[] getProjectsForProblemSearch(
			ILaunchConfiguration configuration, String mode)
			throws CoreException {
		return LauncherUtils.getAffectedProjects(configuration);
	}


}