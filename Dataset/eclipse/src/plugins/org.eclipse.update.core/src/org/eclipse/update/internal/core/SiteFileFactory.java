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
package org.eclipse.update.internal.core;
import java.io.*;
import java.net.*;

import org.eclipse.core.runtime.*;
import org.eclipse.osgi.util.NLS;
import org.eclipse.update.core.*;
import org.eclipse.update.core.model.*;
import org.eclipse.update.internal.model.*;
import org.xml.sax.*;

public class SiteFileFactory extends BaseSiteFactory {

	// private when parsing file system
	private SiteFile site;


	/*
	 * @see ISiteFactory#createSite(URL,boolean)
	 */
	public ISite createSite(URL url) throws CoreException, InvalidSiteTypeException {
	
		Site site = null;
		InputStream siteStream = null;
		SiteModelFactory factory = this;
	
		try {
			// if url points to a directory
			// attempt to parse site.xml
			String path = url.getFile();
			File siteLocation = new File(path);
			if (siteLocation.isDirectory()) {
				url = siteLocation.toURL();
				File siteXMLFile = new File(siteLocation, Site.SITE_XML);
				if (siteXMLFile.exists()) {
					siteStream = new FileInputStream(siteXMLFile);
					site = (Site) factory.parseSite(siteStream);
				} else {
					// parse siteLocation
					site = parseSite(siteLocation);
				}
			} else {
				// we are not pointing to a directory
				// attempt to parse the file
				try {
					URL resolvedURL = URLEncoder.encode(url);
					siteStream = openStream(resolvedURL);
					site = (Site) factory.parseSite(siteStream);
				} catch (IOException e) {
	
					// attempt to parse parent directory
					File file = new File(url.getFile());
					File parentDirectory = file.getParentFile();
	
					// do not create directory if it doesn't exist	[18318]
					// instead hrow error					
					if (parentDirectory != null && !parentDirectory.exists())
						throw Utilities.newCoreException(NLS.bind(Messages.SiteFileFactory_DirectoryDoesNotExist, (new String[] { file.getAbsolutePath() })), null);
	
					if (parentDirectory == null || !parentDirectory.isDirectory())
						throw Utilities.newCoreException(NLS.bind(Messages.SiteFileFactory_UnableToObtainParentDirectory, (new String[] { file.getAbsolutePath() })), null);
	
					site = parseSite(parentDirectory);
	
				}
			}
	
			SiteContentProvider contentProvider = new SiteFileContentProvider(url);
			site.setSiteContentProvider(contentProvider);
			contentProvider.setSite(site);
			site.resolve(url, url);
	
			// Do not set read only as may install in it
			//site.markReadOnly();
		} catch (MalformedURLException e) {
			throw Utilities.newCoreException(NLS.bind(Messages.SiteFileFactory_UnableToCreateURL, (new String[] { url == null ? "" : url.toExternalForm() })), e);	//$NON-NLS-1$
		} catch (IOException e) {
			throw Utilities.newCoreException(Messages.SiteFileFactory_UnableToAccessSite,ISite.SITE_ACCESS_EXCEPTION, e);
		} finally {
			try {
				if (siteStream != null)
					siteStream.close();
			} catch (IOException e) {
			}
		}
		return site;
	}
	/**
	 * Method parseSite.
	 */
	private Site parseSite(File directory) throws CoreException {

		this.site = (SiteFile) createSiteMapModel();

		if (!directory.exists())
			throw Utilities.newCoreException(NLS.bind(Messages.SiteFileFactory_FileDoesNotExist, (new String[] { directory.getAbsolutePath() })), null);

		File pluginPath = new File(directory, Site.DEFAULT_PLUGIN_PATH);

		//PACKAGED
		parsePackagedFeature(directory); // in case it contains JAR files

		parsePackagedPlugins(pluginPath);

		// INSTALLED	
		parseInstalledFeature(directory);

		parseInstalledPlugins(pluginPath);

		return site;

	}

	/**
	 * Method parseFeature.
	 * @throws CoreException
	 */
	private void parseInstalledFeature(File directory) throws CoreException {

		File featureDir = new File(directory, Site.DEFAULT_INSTALLED_FEATURE_PATH);
		if (featureDir.exists()) {
			String[] dir;
			SiteFeatureReferenceModel featureRef;
			URL featureURL;
			File currentFeatureDir;
			String newFilePath = null;

			try {
				// handle the installed featuresConfigured under featuresConfigured subdirectory
				dir = featureDir.list();
				for (int index = 0; index < dir.length; index++) {

					// the URL must ends with '/' for the bundle to be resolved
					newFilePath = dir[index] + (dir[index].endsWith("/") ? "/" : ""); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
					currentFeatureDir = new File(featureDir, newFilePath);
					// check if feature.xml exists
					File featureXMLFile = new File(currentFeatureDir, Feature.FEATURE_XML);
					if (!featureXMLFile.exists()) {
						UpdateCore.warn("Unable to find feature.xml in directory:" + currentFeatureDir); //$NON-NLS-1$
					} else {
						// PERF: remove code
						//SiteFileFactory archiveFactory = new SiteFileFactory();
						featureURL = currentFeatureDir.toURL();
						featureRef = createFeatureReferenceModel();
						featureRef.setSiteModel(site);
						featureRef.setURLString(featureURL.toExternalForm());
						featureRef.setType(ISite.DEFAULT_INSTALLED_FEATURE_TYPE);
						((Site) site).addFeatureReferenceModel(featureRef);
					}
				}
			} catch (MalformedURLException e) {
				throw Utilities.newCoreException(NLS.bind(Messages.SiteFileFactory_UnableToCreateURLForFile, (new String[] { newFilePath })), e);
			}
		}
	}

	/**
	* Method parseFeature.
	* @throws CoreException
	*/
	private void parsePackagedFeature(File directory) throws CoreException {

		// FEATURES
		File featureDir = new File(directory, Site.DEFAULT_FEATURE_PATH);
		if (featureDir.exists()) {
			String[] dir;
			SiteFeatureReferenceModel featureRef;
			URL featureURL;
			File currentFeatureFile;
			String newFilePath = null;

			try {
				// only list JAR files
				dir = featureDir.list(FeaturePackagedContentProvider.filter);
				for (int index = 0; index < dir.length; index++) {

					// check if the JAR file contains a feature.xml
					currentFeatureFile = new File(featureDir, dir[index]);
					JarContentReference ref = new JarContentReference("", currentFeatureFile); //$NON-NLS-1$
					ContentReference result = null;
					try {
						result = ref.peek(Feature.FEATURE_XML, null, null);
					} catch (IOException e) {
						UpdateCore.warn("Exception retrieving feature.xml in file:" + currentFeatureFile, e); //$NON-NLS-1$
					}
					if (result == null) {
						UpdateCore.warn("Unable to find feature.xml in file:" + currentFeatureFile); //$NON-NLS-1$
					} else {
						featureURL = currentFeatureFile.toURL();
						// PERF: remove code
						//SiteFileFactory archiveFactory = new SiteFileFactory();
						featureRef = createFeatureReferenceModel();
						featureRef.setSiteModel(site);
						featureRef.setURLString(featureURL.toExternalForm());
						featureRef.setType(ISite.DEFAULT_PACKAGED_FEATURE_TYPE);
						site.addFeatureReferenceModel(featureRef);
					}
				}
			} catch (MalformedURLException e) {
				throw Utilities.newCoreException(NLS.bind(Messages.SiteFileFactory_UnableToCreateURLForFile, (new String[] { newFilePath })), e);
			}
		}
	}

	/**
	 * Method parsePlugins.
	 * 
	 * look into each plugin/fragment directory, crack the plugin.xml open (or
	 * fragment.xml ???) get id and version, calculate URL...
	 * 
	 * @throws CoreException
	 */
	private void parseInstalledPlugins(File pluginsDir) throws CoreException {
		if (!pluginsDir.exists() || !pluginsDir.isDirectory()) {
			return;
		}
		File[] dirs = pluginsDir.listFiles(new FileFilter() {
			public boolean accept(File f) {
				return f.isDirectory();
			}
		});
		DefaultPluginParser parser = new DefaultPluginParser();
		for (int i = 0; i < dirs.length; i++) {
			File pluginFile = new File(dirs[i], "META-INF/MANIFEST.MF"); //$NON-NLS-1$
			InputStream in = null;
			try {
				BundleManifest bundleManifest = new BundleManifest(pluginFile);
				if (bundleManifest.exists()) {
					PluginEntry entry = bundleManifest.getPluginEntry();
					addParsedPlugin(entry, dirs[i]);
				} else {
					if (!(pluginFile = new File(dirs[i], "plugin.xml")) //$NON-NLS-1$
							.exists()) { //$NON-NLS-1$
						pluginFile = new File(dirs[i], "fragment.xml"); //$NON-NLS-1$
					}
					if (pluginFile != null && pluginFile.exists()
							&& !pluginFile.isDirectory()) {
						in = new FileInputStream(pluginFile);
						PluginEntry entry = parser.parse(in);
						addParsedPlugin(entry, dirs[i]);
					}
				}
			} catch (IOException e) {
				String pluginFileString = (pluginFile == null)
						? null
						: pluginFile.getAbsolutePath();
				throw Utilities.newCoreException(NLS.bind(Messages.SiteFileFactory_ErrorAccessing, (new String[] { pluginFileString })), e);
			} catch (SAXException e) {
				String pluginFileString = (pluginFile == null)
						? null
						: pluginFile.getAbsolutePath();
				throw Utilities.newCoreException(NLS.bind(Messages.SiteFileFactory_ErrorParsingFile, (new String[] { pluginFileString })),
						e);
			} finally {
				if (in != null){
					try{
						in.close();
					} catch(IOException e){
					}
				}
			}
		}
	}

	/**
	 * tranform each Plugin and Fragment into an ArchiveReferenceModel
	 * and a PluginEntry for the Site	 
	 */
	// PERF: removed intermediate Plugin object
	private void addParsedPlugin(PluginEntry entry,File file) throws CoreException {

		String location = null;
		try {
			if (entry != null) {

				// create the plugin Entry
				((Site) site).addPluginEntry(entry);

				// Create the Site mapping ArchiveRef->PluginEntry
				// the id of the archiveRef is plugins\<pluginid>_<ver>.jar as per the specs
				// PERF: remove code
				//SiteFileFactory archiveFactory = new SiteFileFactory();				
				ArchiveReferenceModel archive = createArchiveReferenceModel();
				String id = (entry.getVersionedIdentifier().toString());
				String pluginID = Site.DEFAULT_PLUGIN_PATH + id + FeaturePackagedContentProvider.JAR_EXTENSION;
				archive.setPath(pluginID);
				location = file.toURL().toExternalForm();
				archive.setURLString(location);
				((Site) site).addArchiveReferenceModel(archive);

				// TRACE				
				if (UpdateCore.DEBUG && UpdateCore.DEBUG_SHOW_PARSING) {
					UpdateCore.debug("Added archive to site:" + pluginID + " pointing to: " + location); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
		} catch (MalformedURLException e) {
			throw Utilities.newCoreException(NLS.bind(Messages.SiteFileFactory_UnableToCreateURLForFile, (new String[] { location })), e);
		}
	}

	/**
	 *  
	 */
	private void parsePackagedPlugins(File pluginDir) throws CoreException {
		if (!pluginDir.exists()) {
			return;
		}
		String[] dir = pluginDir.list(FeaturePackagedContentProvider.filter);
		for (int i = 0; i < dir.length; i++) {
			ContentReference ref = null;
			String refString = null;
			InputStream in = null;
			try {
				File file = new File(pluginDir, dir[i]);
				JarContentReference jarReference = new JarContentReference(
						null, file);
				ref = jarReference.peek("META-INF/MANIFEST.MF", null, null); //$NON-NLS-1$
				if (ref != null) {
					in = ref.getInputStream();
					BundleManifest manifest = new BundleManifest(in);
					if (manifest.exists()) {
						addParsedPlugin(manifest.getPluginEntry(), file);
						continue;
					}
				}
				ref = jarReference.peek("plugin.xml", null, null); //$NON-NLS-1$
				if (ref == null) {
					ref = jarReference.peek("fragment.xml", null, null); //$NON-NLS-1$
				}
				if (ref != null) {
					in = ref.getInputStream();
					PluginEntry entry = new DefaultPluginParser().parse(in);
					addParsedPlugin(entry, file);
				}
			} catch (IOException e) {
				try {
					refString = (ref == null) ? null : ref.asURL()
							.toExternalForm();
				} catch (IOException ioe) {
				}
				throw Utilities.newCoreException(NLS.bind(Messages.SiteFileFactory_ErrorAccessing, (new String[] { refString })), e);
			} catch (SAXException e) {
				try {
					refString = (ref == null) ? null : ref.asURL()
							.toExternalForm();
				} catch (IOException ioe) {
				}
				throw Utilities.newCoreException(NLS.bind(Messages.SiteFileFactory_ErrorParsingFile, (new String[] { refString })), e);
			} finally {
				if(in != null){
					try{
						in.close();
					}catch(IOException ce){
					}
				}
			}
		}
	}

	/*
	 * @see SiteModelFactory#createSiteMapModel()
	 */
	public SiteModel createSiteMapModel() {
		return new SiteFile();
	}

	/*
	 * @see SiteModelFactory#canParseSiteType(String)
	 */
	public boolean canParseSiteType(String type) {
		return (super.canParseSiteType(type) || SiteFileContentProvider.SITE_TYPE.equalsIgnoreCase(type));
	}

}
