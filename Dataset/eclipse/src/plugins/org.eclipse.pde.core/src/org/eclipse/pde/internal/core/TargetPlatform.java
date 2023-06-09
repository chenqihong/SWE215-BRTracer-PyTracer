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
package org.eclipse.pde.internal.core;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.PluginVersionIdentifier;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.State;
import org.eclipse.pde.core.plugin.IPluginExtension;
import org.eclipse.pde.core.plugin.IPluginLibrary;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.core.plugin.IPluginObject;
import org.eclipse.pde.internal.core.ifeature.IFeature;
import org.eclipse.pde.internal.core.ifeature.IFeatureModel;
import org.eclipse.pde.internal.core.util.CoreUtility;
import org.eclipse.update.configurator.ConfiguratorUtils;
import org.eclipse.update.configurator.IPlatformConfiguration;

public class TargetPlatform implements IEnvironmentVariables {

	private static final String BOOT_ID = "org.eclipse.core.boot"; //$NON-NLS-1$

	static class LocalSite {
		private ArrayList plugins;
		private IPath path;

		public LocalSite(IPath path) {
			if (path.getDevice() != null)
				this.path = path.setDevice(path.getDevice().toUpperCase(Locale.ENGLISH));
			else
				this.path = path;
			plugins = new ArrayList();
		}

		public IPath getPath() {
			return path;
		}

		public URL getURL() throws MalformedURLException {
			return new URL("file:" + path.addTrailingSeparator().toString()); //$NON-NLS-1$
		}

		public void add(IPluginModelBase model) {
			plugins.add(model);
		}

		public String[] getRelativePluginList() {
			String[] list = new String[plugins.size()];
			for (int i = 0; i < plugins.size(); i++) {
				IPluginModelBase model = (IPluginModelBase) plugins.get(i);
				IPath location = new Path(model.getInstallLocation());
				// defect 37319
				if (location.segmentCount() > 2)
					location = location.removeFirstSegments(location.segmentCount() - 2);
				if (!PDECore.getDefault().getModelManager().isOSGiRuntime()) {
					location = location.append(model.isFragmentModel()
							? "fragment.xml" //$NON-NLS-1$
							: "plugin.xml"); //$NON-NLS-1$
			    }
				//31489 - entry must be relative
				list[i] = location.setDevice(null).makeRelative().toString();
			}
			return list;
		}
	}
	
	public static Properties getConfigIniProperties(String filename) {
		File iniFile = new File(ExternalModelManager.getEclipseHome().toOSString(), filename);
		if (!iniFile.exists())
			return null;
		Properties pini = new Properties();
		try {
			FileInputStream fis = new FileInputStream(iniFile);
			pini.load(fis);
			fis.close();
			return pini;
		} catch (IOException e) {
		}		
		return null;
	}

	public static String[] createPluginPath() throws CoreException {
		return createPluginPath(PDECore.getDefault().getModelManager().getPlugins());
	}

	public static String[] createPluginPath(IPluginModelBase[] models)
		throws CoreException {
		String paths[] = new String[models.length];
		for (int i = 0; i < models.length; i++) {
			paths[i] = models[i].getInstallLocation();
		}
		return paths;
	}


	public static void createPlatformConfigurationArea(
		Map pluginMap,
		File configDir,
		String brandingPluginID)
		throws CoreException {
		try {
			if (PDECore.getDefault().getModelManager().isOSGiRuntime()) {
				if (pluginMap.containsKey("org.eclipse.update.configurator")) {  //$NON-NLS-1$
					savePlatformConfiguration(ConfiguratorUtils.getPlatformConfiguration(null),configDir, pluginMap, brandingPluginID);
				}
				checkPluginPropertiesConsistency(pluginMap, configDir);
			} else {
				savePlatformConfiguration(new PlatformConfiguration(null), new File(configDir, "platform.cfg"), pluginMap, brandingPluginID); //$NON-NLS-1$
			} 			
		} catch (CoreException e) {
			// Rethrow
			throw e;
		} catch (Exception e) {
			// Wrap everything else in a core exception.
			String message = e.getMessage();
			if (message==null || message.length() == 0)
				message = PDECoreMessages.TargetPlatform_exceptionThrown; //$NON-NLS-1$
			throw new CoreException(
				new Status(
					IStatus.ERROR,
					PDECore.getPluginId(),
					IStatus.ERROR,
					message,
					e));
		}
	}
	
	private static void checkPluginPropertiesConsistency(Map map, File configDir) {
		File runtimeDir = new File(configDir, "org.eclipse.core.runtime"); //$NON-NLS-1$
		if (runtimeDir.exists() && runtimeDir.isDirectory()) {
			long timestamp = runtimeDir.lastModified();
			Iterator iter = map.values().iterator();
			while (iter.hasNext()) {
				if (hasChanged((IPluginModelBase)iter.next(), timestamp)) {
                    CoreUtility.deleteContent(runtimeDir);
                    break;
                }
			}
 		}
	}
    
    private static boolean hasChanged(IPluginModelBase model, long timestamp) {
        if (model.getUnderlyingResource() != null) {
            File[] files = new File(model.getInstallLocation()).listFiles();
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory())
                    continue;
                String name = files[i].getName();
                if (name.startsWith("plugin") && name.endsWith(".properties") //$NON-NLS-1$ //$NON-NLS-2$
                        && files[i].lastModified() > timestamp) {
                     return true;
                }
            }
        }
        return false;
    }

	public static String getBundleURL(String id, Map pluginMap) {
		IPluginModelBase model = (IPluginModelBase)pluginMap.get(id);
		if (model == null)
			return null;
		
		return "file:" + new Path(model.getInstallLocation()).addTrailingSeparator().toString(); //$NON-NLS-1$
	}
	
	private static void savePlatformConfiguration(
		IPlatformConfiguration platformConfiguration,
		File configFile,
		Map pluginMap,
		String primaryFeatureId)
		throws IOException, CoreException, MalformedURLException {
		ArrayList sites = new ArrayList();

		// Compute local sites
		Iterator iter = pluginMap.values().iterator();
		while(iter.hasNext()) {
			IPluginModelBase model = (IPluginModelBase)iter.next();
			IPath sitePath = getTransientSitePath(model);
			addToSite(sitePath, model, sites);
		}

		IPluginModelBase bootModel = (IPluginModelBase)pluginMap.get(BOOT_ID);	
		URL configURL = new URL("file:" + configFile.getPath()); //$NON-NLS-1$
		createConfigurationEntries(platformConfiguration, bootModel, sites);
		if (primaryFeatureId != null)
			createFeatureEntries(platformConfiguration, pluginMap, primaryFeatureId);
		platformConfiguration.refresh();
		platformConfiguration.save(configURL);

		if (bootModel!=null) {
			String version = bootModel.getPluginBase().getVersion();
			if (version!=null) {
				PluginVersionIdentifier bootVid = new PluginVersionIdentifier(version);
				PluginVersionIdentifier breakVid = new PluginVersionIdentifier("2.0.3"); //$NON-NLS-1$
				if (breakVid.isGreaterThan(bootVid))
				// Platform configuration version changed in 2.1
				// but the same fix is in 2.0.3.
				// Must switch back to configuration 1.0 for 
				// older configurations.
				repairConfigurationVersion(configURL);
			}
		}
	}

	private static IPath getTransientSitePath(IPluginModelBase model) {
		return new Path(model.getInstallLocation()).removeLastSegments(2);		
	}
	
	private static void repairConfigurationVersion(URL url) throws IOException {
		File file = new File(url.getFile());
		if (file.exists()) {
			Properties p = new Properties();
			FileInputStream fis = new FileInputStream(file);
			p.load(fis);
			p.setProperty("version", "1.0"); //$NON-NLS-1$ //$NON-NLS-2$
			fis.close();
			FileOutputStream fos = new FileOutputStream(file);
			p.store(fos, (new Date()).toString());
			fos.close();
		}
	}

	private static void addToSite(
		IPath path,
		IPluginModelBase model,
		ArrayList sites) {
		if (path.getDevice() != null)
			path = path.setDevice(path.getDevice().toUpperCase(Locale.ENGLISH));
		for (int i = 0; i < sites.size(); i++) {
			LocalSite localSite = (LocalSite) sites.get(i);
			if (localSite.getPath().equals(path)) {
				localSite.add(model);
				return;
			}
		}
		// First time - add site
		LocalSite localSite = new LocalSite(path);
		localSite.add(model);
		sites.add(localSite);
	}

	private static void createConfigurationEntries(
		IPlatformConfiguration config,
		IPluginModelBase bootModel,
		ArrayList sites)
		throws CoreException, MalformedURLException {

		for (int i = 0; i < sites.size(); i++) {
			LocalSite localSite = (LocalSite) sites.get(i);
			String[] plugins = localSite.getRelativePluginList();

			int policy = IPlatformConfiguration.ISitePolicy.USER_INCLUDE;
			IPlatformConfiguration.ISitePolicy sitePolicy =
				config.createSitePolicy(policy, plugins);
			IPlatformConfiguration.ISiteEntry siteEntry =
				config.createSiteEntry(localSite.getURL(), sitePolicy);
			config.configureSite(siteEntry);
		}

		if (!PDECore.getDefault().getModelManager().isOSGiRuntime()) {
			// Set boot location
			URL bootURL = new URL("file:" + bootModel.getInstallLocation()); //$NON-NLS-1$
			config.setBootstrapPluginLocation(BOOT_ID, bootURL);
		}
		config.isTransient(true);
	}


	private static void createFeatureEntries(
		IPlatformConfiguration config,
		Map pluginMap,
		String brandingPluginID)
		throws MalformedURLException {

		// We have primary feature Id.
		IFeatureModel featureModel = PDECore.getDefault().getFeatureModelManager().findFeatureModel(brandingPluginID);
		if (featureModel == null)
			return;
		
		IFeature feature = featureModel.getFeature();
		String featureVersion = feature.getVersion();
		IPluginModelBase primaryPlugin = (IPluginModelBase)pluginMap.get(brandingPluginID);
		if (primaryPlugin == null)
			return;
		
		URL pluginURL = new URL("file:" + primaryPlugin.getInstallLocation()); //$NON-NLS-1$
		URL[] root = new URL[] { pluginURL };
		IPlatformConfiguration.IFeatureEntry featureEntry =
			config.createFeatureEntry(
				brandingPluginID,
				featureVersion,
				brandingPluginID,
				primaryPlugin.getPluginBase().getVersion(),
				true,
				null,
				root);
		config.configureFeatureEntry(featureEntry);
	}

	public static String getOS() {
		String value = getProperty(OS);
		return value.equals("") ? Platform.getOS() : value; //$NON-NLS-1$
	}

	public static String getWS() {
		String value = getProperty(WS);
		return value.equals("") ? Platform.getWS() : value; //$NON-NLS-1$
	}

	public static String getNL() {
		String value = getProperty(NL);
		return value.equals("") ? Platform.getNL() : value; //$NON-NLS-1$
	}

	public static String getOSArch() {
		String value = getProperty(ARCH);
		return value.equals("") ? Platform.getOSArch() : value; //$NON-NLS-1$
	}

	private static String getProperty(String key) {
		return PDECore.getDefault().getPluginPreferences().getString(key);
	}
	
	public static String[] getApplicationNames() {
		TreeSet result = new TreeSet();
		IPluginModelBase[] plugins = PDECore.getDefault().getModelManager().getPlugins();
		for (int i = 0; i < plugins.length; i++) {
			IPluginExtension[] extensions = plugins[i].getPluginBase().getExtensions();
			for (int j = 0; j < extensions.length; j++) {
				String point = extensions[j].getPoint();
				if (point != null && point.equals("org.eclipse.core.runtime.applications")) { //$NON-NLS-1$
					String id = extensions[j].getPluginBase().getId();
					if (id == null || id.trim().length() == 0 || id.startsWith("org.eclipse.pde.junit.runtime")) //$NON-NLS-1$
						continue;
					if (extensions[j].getId() != null)
						result.add(id+ "." + extensions[j].getId());					 //$NON-NLS-1$
				}
			}
		}
		return (String[])result.toArray(new String[result.size()]);
	}
	
	public static TreeSet getProductNameSet() {
		TreeSet result = new TreeSet();
		IPluginModelBase[] plugins = PDECore.getDefault().getModelManager().getPlugins();
		for (int i = 0; i < plugins.length; i++) {
			IPluginExtension[] extensions = plugins[i].getPluginBase().getExtensions();
			for (int j = 0; j < extensions.length; j++) {
				String point = extensions[j].getPoint();
				if (point != null && point.equals("org.eclipse.core.runtime.products")) {//$NON-NLS-1$
					IPluginObject[] children = extensions[j].getChildren();
					if (children.length != 1)
						continue;
					if (!"product".equals(children[0].getName())) //$NON-NLS-1$
						continue;
					String id = extensions[j].getPluginBase().getId();
					if (id == null || id.trim().length() == 0)
						continue;
					if (extensions[j].getId() != null)
						result.add(id+ "." + extensions[j].getId());					 //$NON-NLS-1$
				}
			}
		}
		return result;
	}
	
	public static String[] getProductNames() {
		TreeSet result = getProductNameSet();
		return (String[])result.toArray(new String[result.size()]);
	}
	
	public static Dictionary getTargetEnvironment() {
		Dictionary result = new Hashtable(4);
		result.put ("osgi.os", TargetPlatform.getOS()); //$NON-NLS-1$
		result.put ("osgi.ws", TargetPlatform.getWS()); //$NON-NLS-1$
		result.put ("osgi.nl", TargetPlatform.getNL()); //$NON-NLS-1$
		result.put ("osgi.arch", TargetPlatform.getOSArch()); //$NON-NLS-1$
		return result;
	}

	public static boolean isOSGi() {
		return PDECore.getDefault().getModelManager().isOSGiRuntime();
	}
	
	public static String getTargetVersion() {
		return PDECore.getDefault().getModelManager().getTargetVersion();
	}
	
	public static PDEState getPDEState() {
		return PDECore.getDefault().getModelManager().getState();
	}

	public static State getState() {
		return getPDEState().getState();
	}
	
	public static HashMap getBundleClasspaths(PDEState state) {
		HashMap properties = new HashMap();
		BundleDescription[] bundles = state.getState().getBundles();
		for (int i = 0; i < bundles.length; i++) {
			properties.put(new Long(bundles[i].getBundleId()), getValue(bundles[i], state));
		}		
		return properties;
	}
	
	private static String[] getValue(BundleDescription bundle, PDEState state) {
		IPluginModelBase model = PDECore.getDefault().getModelManager().findModel(bundle);
		String[] result = null;
		if (model != null) {
			IPluginLibrary[] libs = model.getPluginBase().getLibraries();
			result = new String[libs.length];
			for (int i = 0; i < libs.length; i++) {
				result[i] = libs[i].getName();
			}
		} else {
			String[] libs = state.getLibraryNames(bundle.getBundleId());
			result = new String[libs.length];
			for (int i = 0; i < libs.length; i++) {
				result[i] = libs[i];
			}			
		}
		if (result.length == 0)
			return new String[] {"."}; //$NON-NLS-1$
		return result;
	}
	
	public static String[] getFeaturePaths() {
		IFeatureModel[] models = PDECore.getDefault().getFeatureModelManager().getModels();
		String[] paths = new String[models.length];
		for (int i = 0; i < models.length; i++) {
			paths[i] = models[i].getInstallLocation() + IPath.SEPARATOR + "feature.xml"; //$NON-NLS-1$
		}
		return paths;
	}

	/**
	 * Obtains product ID
	 * 
	 * @return String or null
	 */
	public static String getDefaultProduct() {
		if (ICoreConstants.TARGET21.equals(TargetPlatform.getTargetVersion())) {
			return null;
		}
		Properties config = getConfigIniProperties("configuration/config.ini"); //$NON-NLS-1$
		if (config != null) {
			String product = (String) config.get("eclipse.product"); //$NON-NLS-1$
			if (product != null && getProductNameSet().contains(product)) {
				return product;
			}
		}
		if (getProductNameSet().contains("org.eclipse.platform.ide")) { //$NON-NLS-1$
			return "org.eclipse.platform.ide"; //$NON-NLS-1$
		}
		return null;
	}

}
