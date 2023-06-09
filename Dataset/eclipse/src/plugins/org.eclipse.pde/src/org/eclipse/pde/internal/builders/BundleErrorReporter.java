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
package org.eclipse.pde.internal.builders;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.PluginVersionIdentifier;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IRegion;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.ExportPackageDescription;
import org.eclipse.osgi.service.resolver.VersionRange;
import org.eclipse.osgi.util.ManifestElement;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.core.plugin.IFragmentModel;
import org.eclipse.pde.core.plugin.IPluginBase;
import org.eclipse.pde.core.plugin.IPluginModel;
import org.eclipse.pde.core.plugin.IPluginModelBase;
import org.eclipse.pde.internal.PDE;
import org.eclipse.pde.internal.PDEMessages;
import org.eclipse.pde.internal.core.ICoreConstants;
import org.eclipse.pde.internal.core.PDECore;
import org.eclipse.pde.internal.core.search.PluginJavaSearchUtil;
import org.eclipse.pde.internal.core.util.IdUtil;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.Version;

public class BundleErrorReporter extends JarManifestErrorReporter {
	private static final String COMPATIBILITY_PLUGIN = "org.eclipse.core.runtime.compatibility"; //$NON-NLS-1$

	private static final String COMPATIBILITY_ACTIVATOR = "org.eclipse.core.internal.compatibility.PluginActivator"; //$NON-NLS-1$
	
	/**
	 * @param versionString
	 *            the version to be checked, null is allowed and will be treated
	 *            as 0.0.0
	 * @return IStatus
	 */
	protected static IStatus validateVersionString(String versionString) {
		if (versionString == null)
			return Status.OK_STATUS;
		return PluginVersionIdentifier.validateVersion(versionString);
	}

	protected static IStatus validateVersionRange(String versionRangeString) {
		try {
			new VersionRange(versionRangeString);
		} catch (IllegalArgumentException e) {
			return new Status(IStatus.ERROR, PDE.PLUGIN_ID, IStatus.ERROR, 
					PDEMessages.BundleErrorReporter_invalidVersionRangeFormat, e); //$NON-NLS-1$
		}

		// need to do our extra checks for each piece of the versionRange
		int comma = versionRangeString.indexOf(',');
		if (comma < 0) {
			return validateVersionString(versionRangeString);
		}

		IStatus status = validateVersionString(versionRangeString.substring(1, comma));
		if(!status.isOK()){
			return status;
		}
		return validateVersionString(versionRangeString
				.substring(comma + 1, versionRangeString.length() - 1));
	}

	private boolean fEclipse3_1;
	
	private boolean fHasExtensibleApi = false;

	private boolean fFragment;

	private Map fFragmentsPackagesMap = null;

	private Map fHostPackagesMap = null;

	private boolean fHasFragment_Xml;

	private boolean fHasExtensions;

	private String fHostBundleId;

	// private Vector fIimportPkgs;
	//
	private String fPluginId = ""; //$NON-NLS-1$

	private Map fProjectPackagesMap = null;
	
	private boolean fCompatibility = false;

	private boolean fCompatibilityActivator = false;

	public BundleErrorReporter(IFile file) {
		super(file);
	}

	/**
	 * Adds IPackageFragment from a project to a map
	 */
	private void addProjectPackages(Map map, IProject proj) {
		try {
			if (!proj.hasNature(JavaCore.NATURE_ID)) {
				return;
			}
		} catch (CoreException ce) {
			return;
		}
		IJavaProject jp = JavaCore.create(proj);
		try {
			IPackageFragmentRoot[] roots = jp.getPackageFragmentRoots();
			for (int i = 0; i < roots.length; i++) {
				if (roots[i].getKind() == IPackageFragmentRoot.K_SOURCE
						|| (roots[i].getKind() == IPackageFragmentRoot.K_BINARY && !roots[i]
								.isExternal())) {
					IJavaElement[] children = roots[i].getChildren();
					for (int j = 0; j < children.length; j++) {
						IPackageFragment f = (IPackageFragment) children[j];
						map.put(f.getElementName(), f);
					}
				}
			}
		} catch (JavaModelException e) {
		}
	}

	/**
	 * @return Map of IPluginModelBase not including current plug-in
	 */
	private HashMap getAvailableBundles() {
		HashMap map = new HashMap();
		IPluginModelBase[] plugins = PDECore.getDefault().getModelManager()
				.getPlugins();

		for (int i = 0; i < plugins.length; i++) {
			IPluginBase element = plugins[i].getPluginBase();
			if ((element.getId() != null) && !element.getId().equals(fPluginId)) {
				// filter out the current project
				map.put(element.getId(), plugins[i]);
			}
		}
		return map;
	}

	/**
	 * @return Map of ExportPackageDescription by String
	 */
	private HashMap getAvailableExportedPackages() {
		IPluginModelBase[] plugins = PDECore.getDefault().getModelManager()
				.getPlugins();

		HashMap map = new HashMap();
		for (int i = 0; i < plugins.length; i++) {
			if ((plugins[i].getPluginBase().getId() != null)) {
				BundleDescription bd = plugins[i].getBundleDescription();
				if (bd != null) {
					ExportPackageDescription[] elements = bd
							.getExportPackages();
					if (elements != null) {
						for (int j = 0; j < elements.length; j++) {
							map.put(elements[j].getName(), elements[j]);
						}
					}
				}
			}
		}
		return map;
	}

	/**
	 * @return Map of IPackageFragment from corresponding fragment projects
	 */
	private Map getFragmentsPackages() {
		if (fFragmentsPackagesMap == null) {
			Map map = new HashMap();
			IFragmentModel[] models = PDECore.getDefault().getModelManager()
					.getFragments();
			for (int i = 0; i < models.length; i++) {
				String hostId = models[i].getFragment().getPluginId();
				if (!fPluginId.equals(hostId)) {
					continue;
				}
				IResource resource = models[i].getUnderlyingResource();
				if (resource != null) {
					addProjectPackages(map, resource.getProject());

				}
			}
			fFragmentsPackagesMap = map;
		}
		return fFragmentsPackagesMap;
	}

	/**
	 * @return Map of IPackageFragment from current project
	 */
	private Map getHostPackages() {
		if (fHostPackagesMap == null) {
			Map map = new HashMap();
			if (fHostBundleId != null) {
				IPluginModel model = PDECore.getDefault().getModelManager()
						.findPluginModel(fHostBundleId);
				if (model == null) {
					return map;
				}
				IResource resource = model.getUnderlyingResource();
				if (resource != null) {
                    addProjectPackages(map, resource.getProject());
                } else {
            		try {
						if (fProject.hasNature(JavaCore.NATURE_ID)) {
							IPackageFragment[] packages = PluginJavaSearchUtil
									.collectPackageFragments(
											new IPluginBase[] { model
													.getPluginBase() },
											JavaCore.create(fProject), false);
							for (int i = 0; i < packages.length; i++)
								map.put(packages[i].getElementName(),
										packages[i]);
						}
					} catch (JavaModelException jme) {
						PDE.log(jme);
					} catch (CoreException ce) {
					}
                }
			}
			fHostPackagesMap = map;
		}
		return fHostPackagesMap;
	}

	private int getPackageLine(IHeader header, ManifestElement element) {
		String packageName = element.getValue();
		if (element.getDirectiveKeys() != null || element.getKeys() != null)
			return getLine(header, packageName + ";"); //$NON-NLS-1$

		// check for this exact package on the last line
		try {
			IRegion lineRegion = fTextDocument.getLineInformation(header
					.getLineNumber()
					+ header.getLinesSpan() - 1);
			String lineStr = fTextDocument.get(lineRegion.getOffset(),
					lineRegion.getLength());
			if (lineStr.endsWith(packageName)) {
				return header.getLineNumber() + header.getLinesSpan();
			}
		} catch (BadLocationException ble) {
			PDECore.logException(ble);
		}

		// search all except last line
		return getLine(header, packageName + ","); //$NON-NLS-1$
	}

	/**
	 * @return Map of IPackageFragment from current project
	 */
	private Map getProjectPackages() {
		if (fProjectPackagesMap == null) {
			Map map = new HashMap();
			addProjectPackages(map, fProject);
			fProjectPackagesMap = map;
		}
		return fProjectPackagesMap;
	}

	protected boolean isCheckDeprecated() {
		return CompilerFlags.getFlag(fProject, CompilerFlags.P_DEPRECATED) != CompilerFlags.IGNORE;
	}

	protected boolean isCheckNoRequiredAttr() {
		return CompilerFlags.getFlag(fProject, CompilerFlags.P_NO_REQUIRED_ATT) != CompilerFlags.IGNORE;
	}

	protected boolean isCheckUnknownAttr() {
		return CompilerFlags.getFlag(fProject,
				CompilerFlags.P_UNKNOWN_ATTRIBUTE) != CompilerFlags.IGNORE;
	}

	protected boolean isCheckUnknownClass() {
		return CompilerFlags.getFlag(fProject, CompilerFlags.P_UNKNOWN_CLASS) != CompilerFlags.IGNORE;
	}

	protected boolean isCheckUnresolvedImports() {
		return CompilerFlags.getFlag(fProject,
				CompilerFlags.P_UNRESOLVED_IMPORTS) != CompilerFlags.IGNORE;
	}

//	 /**
//	 * @return true if the given file exists in the project
//	 */
//	 private boolean isFileExist(String fileName) {
//	 IResource member = fProject.findMember(fileName);
//	 if (member != null) {
//	 if ((member instanceof IFile) && (member.exists()))
//	 return true;
//	 }
//	
//	 return false;
//	 }

	private void readBundleManifestVersion() {
		IHeader header = (IHeader) fHeaders
				.get(Constants.BUNDLE_MANIFESTVERSION);
		if (header == null) {
			return;
		}
		try {
			Version v = new Version(header.getValue());
			if (v.getMajor() >= 2) {
				fEclipse3_1 = true;
			}
		} catch (NumberFormatException nfe) {
		}
	}

	private void validateBundleActivator() {
		IHeader header = (IHeader) fHeaders.get(Constants.BUNDLE_ACTIVATOR);
		if (header == null) {
			return;
		}
		String activator = header.getValue();
		fCompatibilityActivator = COMPATIBILITY_ACTIVATOR.equals(activator);
		String message;
		if (fFragment) {
			/* Fragment bundles must not specify a Bundle Activator */
			message = PDEMessages.BundleErrorReporter_fragmentActivator; //$NON-NLS-1$
			report(message, header.getLineNumber() + 1, CompilerFlags.ERROR);
			return;
		}
		if (isCheckUnknownClass()) {
			try {
				if (!fProject.hasNature(JavaCore.NATURE_ID)) {
					return;
				}
			} catch (CoreException ce) {
				return;
			}
			IJavaProject javaProject = JavaCore.create(fProject);
			try {
				// Look for this activator in the project's classpath
				IType type = javaProject.findType(activator);
	
				if (!fCompatibilityActivator) {
					/* Activator type does not exist */
					if (type == null || !type.exists()) {
						message = NLS.bind(
								PDEMessages.BundleErrorReporter_NoExist,
								activator); //$NON-NLS-1$
						report(message, getLine(header, activator),
								CompilerFlags.P_UNKNOWN_CLASS);
						return;
					}
	
					// activator must be a local class
					IPackageFragmentRoot pfroot = (IPackageFragmentRoot) type
							.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
					if (pfroot != null && pfroot.isExternal()) {
						message = NLS
								.bind(
										PDEMessages.BundleErrorReporter_externalClass,
										activator); //$NON-NLS-1$
						report(message, getLine(header, activator),
								CompilerFlags.P_UNKNOWN_CLASS);
						return;
					}
				} else {
					if (!fCompatibility) {
						message = NLS
								.bind(
										PDEMessages.BundleErrorReporter_unresolvedCompatibilityActivator,
										activator); //$NON-NLS-1$
						report(message, getLine(header, activator),
								CompilerFlags.P_UNKNOWN_CLASS);
						return;
					}
				}
			} catch (JavaModelException e) {
				PDECore.logException(e);
			}
		}
	}

	private void validatePluginClass() {
		IHeader header = (IHeader) fHeaders.get(ICoreConstants.PLUGIN_CLASS);
		if (header == null) {
			return;
		}
		String pluginClass = header.getValue();
		String message;
		if (fFragment) {
			/* Fragment bundles must not specify Plugin Class */
			message = PDEMessages.BundleErrorReporter_fragmentActivator; //$NON-NLS-1$
			report(message, header.getLineNumber() + 1, CompilerFlags.ERROR);
			return;
		}
		if (!fCompatibilityActivator) {
			if (!fCompatibility) {
				// rename Plugin-Class to Bundle-Activator
				message = PDEMessages.BundleErrorReporter_unusedPluginClass; //$NON-NLS-1$
				report(message, header.getLineNumber() + 1, CompilerFlags.WARNING);
			}
		}

		if (isCheckUnknownClass()) {
			try {
				if (!fProject.hasNature(JavaCore.NATURE_ID)) {
					return;
				}
			} catch (CoreException ce) {
				return;
			}
			IJavaProject javaProject = JavaCore.create(fProject);
			try {
				// Look for this plugin class in the project's classpath
				IType type = javaProject.findType(pluginClass);

				/* Plugin class type does not exist */
				if (type == null || !type.exists()) {
					message = NLS.bind(PDEMessages.BundleErrorReporter_NoExist,
							pluginClass); //$NON-NLS-1$
					report(message, getLine(header, pluginClass),
							CompilerFlags.P_UNKNOWN_CLASS);
					return;
				}

				// Plugin class must be a local class
				IPackageFragmentRoot pfroot = (IPackageFragmentRoot) type
						.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
				if (pfroot != null && pfroot.isExternal()) {
					message = NLS.bind(
							PDEMessages.BundleErrorReporter_externalClass,
							pluginClass); //$NON-NLS-1$
					report(message, getLine(header, pluginClass),
							CompilerFlags.P_UNKNOWN_CLASS);
					return;
				}
			} catch (JavaModelException e) {
				PDECore.logException(e);
			}
		}
	}

	private void validateBundleClasspath() {
		IHeader header = (IHeader) fHeaders.get(Constants.BUNDLE_CLASSPATH);
		if (header == null) {
			return;
		}
		String classpath = header.getValue();

		String message = null;
		if (classpath.trim().length() == 0) {
			/* It is defined but it is an empty string */
			message = PDEMessages.BundleErrorReporter_ClasspathNotEmpty; //$NON-NLS-1$
			report(message, header.getLineNumber() + 1, CompilerFlags.ERROR);
			return;
		}

		ManifestElement[] elements = header.getElements();
		if (elements.length == 0) {
			return;
		}
	}

	/**
	 * @return boolean false if fatal
	 */
	private boolean validateBundleSymbolicName() {
		IHeader header = (IHeader) fHeaders.get(Constants.BUNDLE_SYMBOLICNAME);
		String message;
		if (header == null) {
			report(
					NLS.bind(PDEMessages.BundleErrorReporter_headerMissing, Constants.BUNDLE_SYMBOLICNAME), 1, //$NON-NLS-1$
					CompilerFlags.ERROR);
			return false;
		}
		String symbolicName = header.getValue();
		if ((symbolicName.trim()).length() == 0) {
			message = PDEMessages.BundleErrorReporter_NoSymbolicName; //$NON-NLS-1$
			report(message, header.getLineNumber() + 1, CompilerFlags.ERROR);
			return false;
		}
		ManifestElement[] elements = header.getElements();
		if (elements.length == 0) {
			return false;
		}
		fPluginId = elements[0].getValue();

		validatePluginId(header, fPluginId);

		validateSingletonAttribute(header, elements[0]);
		validateSingletonDirective(header, elements[0]);

		return true;
	}

	private void validateBundleVersion() {
		IHeader header = (IHeader) fHeaders.get(Constants.BUNDLE_VERSION);
		if (header == null) {
			report(
					NLS.bind(PDEMessages.BundleErrorReporter_headerMissing, Constants.BUNDLE_VERSION), 1, //$NON-NLS-1$
					CompilerFlags.ERROR);
			return;
		}
		String version = header.getValue();
		IStatus status = validateVersionString(version);
		if(!status.isOK()){
			int line = getLine(header, version);
			report(status.getMessage(), line, CompilerFlags.ERROR);
		}
	}

	private void validateBundleVersionAttribute(IHeader header,
			ManifestElement element) {
		String versionRange = element
				.getAttribute(Constants.BUNDLE_VERSION_ATTRIBUTE);
		if (versionRange != null && !validateVersionRange(versionRange).isOK()) {
			String message = NLS.bind(PDEMessages.BundleErrorReporter_InvalidFormatInBundleVersion, element.getValue()); //$NON-NLS-1$
			report(message, getPackageLine(header, element),
					CompilerFlags.ERROR); //$NON-NLS-1$
		}
	}

	public void validateContent(IProgressMonitor monitor) {
		super.validateContent(monitor);
		if (fHeaders == null || getErrorCount() > 0) {
			return;
		}

		readBundleManifestVersion();
		fHasFragment_Xml = fProject.getFile("fragment.xml").exists(); //$NON-NLS-1$

		IPluginModelBase modelBase = PDECore.getDefault().getModelManager()
				.findModel(fProject);
		if (modelBase != null) {
			fHasExtensions = modelBase.getPluginBase().getExtensionPoints().length > 0
					|| modelBase.getPluginBase().getExtensions().length > 0;
		}

		// sets fPluginId
		if (!validateBundleSymbolicName()) {
			return;
		}
		validateBundleVersion();
		// sets fExtensibleApi
		validateExtensibleAPI();
		// sets fHostBundleId
		validateFragmentHost();
		validateBundleClasspath();
		validateRequireBundle(monitor);
		// sets fCompatibility
		// sets fCompatibilityActivator
		validateBundleActivator();
		validatePluginClass();
		validateExportPackage(monitor);
		validateProvidePackage(monitor);
		validateImportPackage(monitor);
		validateEclipsePlatformFilter();
		// validateNativeCode();
	}

	private void validateExportPackage(IProgressMonitor monitor) {
		IHeader header = (IHeader) fHeaders.get(Constants.EXPORT_PACKAGE);
		if (header == null) {
			return;
		}
		String message = null;
		ManifestElement[] exportPackageElements = header.getElements();

		for (int i = 0; i < exportPackageElements.length; i++) {
			checkCanceled(monitor);

			String exportPackageStmt = exportPackageElements[i].getValue();
			if (".".equals(exportPackageStmt.trim())) { //$NON-NLS-1$
				// workaround for manifest converter generating "."
				continue;
			}

			validateVersionAttribute(header, exportPackageElements[i], false);

			validateSpecificationVersionAttribute(header,
					exportPackageElements[i]);

			validateX_InternalDirective(header, exportPackageElements[i]);
			
			validateX_FriendsDirective(header, exportPackageElements[i]);
			
			if (!isCheckUnresolvedImports()) {
				continue;
			}
			IPackageFragment f = (IPackageFragment) getProjectPackages().get(
					exportPackageStmt);
			/* The exported package cannot be default package. */
			if (f != null && f.isDefaultPackage()) {
				message = PDEMessages.BundleErrorReporter_CannotExportDefaultPackage; //$NON-NLS-1$
				report(message, getPackageLine(header, exportPackageElements[i]),
						CompilerFlags.P_UNRESOLVED_IMPORTS); //$NON-NLS-1$
				continue;
			}

			/* The exported package does not exist in the bundle */
			if (!getProjectPackages().containsKey(exportPackageStmt)) {
				if (!(getHostPackages().containsKey(exportPackageStmt) || fHasExtensibleApi
						&& getFragmentsPackages()
								.containsKey(exportPackageStmt))) {
					message = NLS.bind(PDEMessages.BundleErrorReporter_NotExistInProject, exportPackageStmt); //$NON-NLS-1$
					report(message, getPackageLine(header, exportPackageElements[i]),
							CompilerFlags.P_UNRESOLVED_IMPORTS); //$NON-NLS-1$
					continue;
				}
			}

		}
	}

	private void validateExtensibleAPI(){
		IHeader header = (IHeader) fHeaders.get(ICoreConstants.EXTENSIBLE_API);
		if(header==null){
			return;
		}
		validateBooleanValue(header);
		
		fHasExtensibleApi = "true".equals(header.getValue()); //$NON-NLS-1$
	}
	
	private void validateFragmentHost() {
		IHeader header = (IHeader) fHeaders.get(Constants.FRAGMENT_HOST);
		String message;
		if (header == null) {
			if (isCheckNoRequiredAttr() && fHasFragment_Xml) { //$NON-NLS-1$
				message = PDEMessages.BundleErrorReporter_HostNeeded; //$NON-NLS-1$
				report(message, 1, CompilerFlags.P_NO_REQUIRED_ATT);
			}
			return;
		}

		fFragment = true;
		ManifestElement[] fragmentHostElements = header.getElements();
		if (isCheckNoRequiredAttr() && fragmentHostElements.length == 0) {
			message = PDEMessages.BundleErrorReporter_HostNeeded; //$NON-NLS-1$
			report(message, 1, CompilerFlags.P_NO_REQUIRED_ATT);
			return;
		}

		String fragmentHostStmt = fragmentHostElements[0].getValue();
		if (!validatePluginId(header, fragmentHostStmt)) {
			return;
		}

		validateBundleVersionAttribute(header, fragmentHostElements[0]);

		if (isCheckUnresolvedImports()) {
			HashMap availableBundlesMap = getAvailableBundles();
			IPluginModelBase availableModel = (IPluginModelBase) availableBundlesMap
					.get(fragmentHostStmt);
			if (availableModel == null || !availableModel.isEnabled()) {
				/*
				 * Host bundle does not exist in the PDE target platform.
				 */
				message = NLS.bind(PDEMessages.BundleErrorReporter_HostNotExistPDE, fragmentHostStmt); //$NON-NLS-1$
				report(message, getLine(header, fragmentHostStmt),
						CompilerFlags.P_UNRESOLVED_IMPORTS);
				return;
			}
			if (availableModel instanceof IFragmentModel) {
				/* The host is a fragment */
				message = NLS.bind(PDEMessages.BundleErrorReporter_HostIsFragment, fragmentHostStmt); //$NON-NLS-1$
				report(message, getLine(header, fragmentHostStmt),
						CompilerFlags.P_UNRESOLVED_IMPORTS);
				return;
			}
			String availableVersion = availableModel.getPluginBase()
					.getVersion();
			String requiredVersionRange = fragmentHostElements[0]
					.getAttribute(Constants.BUNDLE_VERSION_ATTRIBUTE);
			if (requiredVersionRange != null
					&& validateVersionRange(requiredVersionRange).isOK()) {
				VersionRange versionRange = new VersionRange(
						requiredVersionRange);
				if (!versionRange.isIncluded(new Version(availableVersion))) {
					message = NLS.bind(PDEMessages.BundleErrorReporter_BundleRangeInvalidInBundleVersion, fragmentHostStmt); //$NON-NLS-1$
					report(message, getLine(header, requiredVersionRange),
							CompilerFlags.P_UNRESOLVED_IMPORTS);
				}
			}
			// save for fragment host id
			fHostBundleId = fragmentHostStmt;

		}
	}

	private void validateImportPackage(IProgressMonitor monitor) {
		IHeader header = (IHeader) fHeaders.get(Constants.IMPORT_PACKAGE);
		if (header == null) {
			return;
		}
		String message = null;
		HashMap availableExportedPackagesMap = getAvailableExportedPackages();

		ManifestElement[] importPackageElements = header.getElements();
		for (int i = 0; i < importPackageElements.length; i++) {
			checkCanceled(monitor);

			validateSpecificationVersionAttribute(header,
					importPackageElements[i]);
			validateVersionAttribute(header, importPackageElements[i], true);

			validateResolutionDirective(header, importPackageElements[i]);

			String importPackageStmt = importPackageElements[i].getValue();

			if (!isCheckUnresolvedImports()) {
				continue;
			}
			if (!availableExportedPackagesMap.containsKey(importPackageStmt)) {
				/* No bundle exports this package */
				message = NLS.bind(PDEMessages.BundleErrorReporter_PackageNotExported, importPackageStmt); //$NON-NLS-1$
				report(message, getPackageLine(header, importPackageElements[i]),
						CompilerFlags.P_UNRESOLVED_IMPORTS);
				continue;
			}

			String requiredVersion = importPackageElements[i]
					.getAttribute(Constants.VERSION_ATTRIBUTE);
			if (requiredVersion != null && validateVersionRange(requiredVersion).isOK()) {
				VersionRange range = new VersionRange(requiredVersion);
				ExportPackageDescription epd = (ExportPackageDescription) availableExportedPackagesMap
						.get(importPackageStmt);
				if (epd.getVersion() != null
						&& !range.isIncluded(epd.getVersion())) {
					message = NLS.bind(PDEMessages.BundleErrorReporter_VersionNotInRange, (new String[] { importPackageStmt, requiredVersion })); //$NON-NLS-1$
					report(message, getPackageLine(header, importPackageElements[i]),
							CompilerFlags.P_UNRESOLVED_IMPORTS);
					continue;
				}
			}
		}
	}

//	 private void validateNativeCode() {
//		IHeader header = (IHeader) fHeaders.get(Constants.BUNDLE_NATIVECODE);
//		if (header == null) {
//			return;
//		}
//		String nativeCode = header.getValue();
//		if (nativeCode == null) {
//			return;
//		}
//		String message = null;
//
//		ManifestElement[] nativeCodeElements = header.getElements();
//		for (int i = 0; i < nativeCodeElements.length; i++) {
//			String fileNames = nativeCodeElements[i].getValue();
//			// Parse the file names
//			StringTokenizer st = new StringTokenizer(fileNames, ";"); //$NON-NLS-1$
//			String filesErrorMsg = ""; //$NON-NLS-1$
//			while (st.hasMoreTokens()) {
//				String name = st.nextToken();
//				if (!filesErrorMsg.equals("")) //$NON-NLS-1$
//					filesErrorMsg += ","; //$NON-NLS-1$
//				filesErrorMsg += name;
//
//				if (!isFileExist(name)) {
//					// File does not exist.
//					message = PDE.getFormattedMessage(
//							"BundleErrorReporter.FileNotExist", name); //$NON-NLS-1$
//					report(message, getLine(header, name),
//							CompilerFlags.P_UNKNOWN_RESOURCE);
//				}
//			}
//
//			String[] processors = nativeCodeElements[i]
//					.getAttributes(Constants.BUNDLE_NATIVECODE_PROCESSOR);
//			if ((processors == null) || (processors.length == 0)) {
//				// No processor settings
//				message = PDE.getFormattedMessage(
//						"BundleErrorReporter.NativeNoProcessor", filesErrorMsg); //$NON-NLS-1$
//				report(message, header.getLineNumber() + 1,
//						CompilerFlags.P_NO_REQUIRED_ATT);
//			} else {
//				HashSet set = new HashSet(Arrays
//						.asList(NativeCodeAttributeValues.PROCESSOR_TYPES));
//				set
//						.addAll(Arrays
//								.asList(NativeCodeAttributeValues.ADDITIONAL_PROCESSOR_ALIASES));
//				for (int j = 0; j < processors.length; j++) {
//					if (!set.contains(processors[j])) {
//						// Processor is unrecognized
//						message = PDE
//								.getFormattedMessage(
//										"BundleErrorReporter.NativeInvalidProcessor", processors[j]); //$NON-NLS-1$
//						report(message, getLine(header, processors[j]),
//								CompilerFlags.P_UNKNOWN_ATTRIBUTE);
//					}
//				}
//			}
//
//			String[] osNames = nativeCodeElements[i]
//					.getAttributes(Constants.BUNDLE_NATIVECODE_OSNAME);
//			if ((osNames == null) || (osNames.length == 0)) {
//				// No OS settings
//				message = PDE.getFormattedMessage(
//						"BundleErrorReporter.NativeNoOSName", filesErrorMsg); //$NON-NLS-1$
//				report(message, header.getLineNumber() + 1,
//						CompilerFlags.P_NO_REQUIRED_ATT);
//			} else {
//				HashSet set = new HashSet(Arrays
//						.asList(NativeCodeAttributeValues.OS_TYPES));
//				set
//						.addAll(Arrays
//								.asList(NativeCodeAttributeValues.ADDITIONAL_OS_ALIASES));
//				for (int j = 0; j < osNames.length; j++) {
//					if (!set.contains(osNames[j])) {
//						// OS name is unrecognized
//						message = PDE
//								.getFormattedMessage(
//										"BundleErrorReporter.NativeInvalidOSName", osNames[j]); //$NON-NLS-1$
//						report(message, getLine(header, osNames[j]),
//								CompilerFlags.P_UNKNOWN_ATTRIBUTE);
//
//					}
//				}
//			}
//
//			String osVersion = nativeCodeElements[i]
//					.getAttribute(Constants.BUNDLE_NATIVECODE_OSVERSION);
//			if (osVersion != null) {
//				// version is in wrong format
//				if (!isValidVersionRange(osVersion)) {
//					message = PDE
//							.getFormattedMessage(
//									"BundleErrorReporter.NativeInvalidOSVersion", osVersion); //$NON-NLS-1$
//					report(message, getLine(header, osVersion),
//							CompilerFlags.P_UNKNOWN_ATTRIBUTE);
//				}
//			}
//
//			String filter = nativeCodeElements[i]
//					.getAttribute(Constants.SELECTION_FILTER_ATTRIBUTE);
//			if (filter != null) {
//				BundleContext context = PDE.getDefault().getBundleContext();
//				try {
//					context.createFilter(filter);
//				} catch (InvalidSyntaxException e) {
//					// selection filter is in a wrong format
//					String[] msg = new String[2];
//					msg[0] = filter;
//					msg[1] = e.getMessage();
//					message = PDE.getFormattedMessage(
//							"BundleErrorReporter.NativeInvalidFilter", msg); //$NON-NLS-1$
//					report(message, getLine(header, filter),
//							CompilerFlags.P_UNKNOWN_ATTRIBUTE);
//				}
//			}
//
//			String[] lang = nativeCodeElements[i]
//					.getAttributes(Constants.BUNDLE_NATIVECODE_LANGUAGE);
//			if ((lang != null) && (lang.length > 0)) {
//				HashSet set = new HashSet();
//				for (int k = 0; k < NativeCodeAttributeValues.LANGUAGES.length; k++) {
//					set.add(NativeCodeAttributeValues.LANGUAGES[k][1]);
//				}
//				for (int j = 0; j < lang.length; j++) {
//					if (!set.contains(lang[j])) {
//						// Language is unrecognized
//						message = PDE
//								.getFormattedMessage(
//										"BundleErrorReporter.NativeInvalidLanguage", lang[j]); //$NON-NLS-1$
//						report(message, getLine(header, lang[i]),
//								CompilerFlags.P_UNKNOWN_ATTRIBUTE);
//					}
//				}
//			}
//		}
//	}

	private void validateOptionalAttribute(IHeader header,
			ManifestElement requireBundleElements) {
		String message;
		String rexport = requireBundleElements
				.getAttribute(ICoreConstants.OPTIONAL_ATTRIBUTE);
		if (rexport != null) {
			validateBooleanAttributeValue(header, requireBundleElements,
					ICoreConstants.OPTIONAL_ATTRIBUTE);
			if (fEclipse3_1 && isCheckDeprecated()) {
				message = NLS
						.bind(
								PDEMessages.BundleErrorReporter_deprecated_attribute_optional,
								ICoreConstants.OPTIONAL_ATTRIBUTE); //$NON-NLS-1$
				report(
						message,
						getLine(header, ICoreConstants.OPTIONAL_ATTRIBUTE + "="), CompilerFlags.P_DEPRECATED); //$NON-NLS-1$
			}
		}
	}

	private boolean validatePluginId(IHeader header, String value) {
		String message;
		if (!IdUtil.isValidPluginId(value)) {
			message = PDEMessages.BundleErrorReporter_InvalidSymbolicName; //$NON-NLS-1$
			report(message, header.getLineNumber() + 1, CompilerFlags.WARNING);
			return false;
		}
		return true;
	}

	private void validateProvidePackage(IProgressMonitor monitor) {
		IHeader header = (IHeader) fHeaders.get(ICoreConstants.PROVIDE_PACKAGE);
		if (header == null) {
			return;
		}
		String message = null;
		if (fEclipse3_1 && isCheckDeprecated()) {
			message = NLS
					.bind(
							PDEMessages.BundleErrorReporter_deprecated_header_Provide_Package,
							ICoreConstants.PROVIDE_PACKAGE); //$NON-NLS-1$
			report(message, header.getLineNumber() + 1,
					CompilerFlags.P_DEPRECATED);
		}
		ManifestElement[] exportPackageElements = header.getElements();

		for (int i = 0; i < exportPackageElements.length; i++) {
			checkCanceled(monitor);

			String exportPackageStmt = exportPackageElements[i].getValue();
			if (".".equals(exportPackageStmt.trim())) { //$NON-NLS-1$
				// workaround for manifest converter generating "."
				continue;
			}

			validateSpecificationVersionAttribute(header,
					exportPackageElements[i]);

			if (!isCheckUnresolvedImports()) {
				continue;
			}
			IPackageFragment f = (IPackageFragment) getProjectPackages().get(
					exportPackageStmt);
			/* The exported package cannot be default package. */
			if (f != null && f.isDefaultPackage()) {
				message = PDEMessages.BundleErrorReporter_CannotExportDefaultPackage; //$NON-NLS-1$
				report(message, getPackageLine(header, exportPackageElements[i]),
						CompilerFlags.P_UNRESOLVED_IMPORTS); //$NON-NLS-1$
				continue;
			}

			/* The exported package does not exist in the bundle */
			if (!getProjectPackages().containsKey(exportPackageStmt)) {
				if (!(getHostPackages().containsKey(exportPackageStmt) || fHasExtensibleApi
						&& getFragmentsPackages()
								.containsKey(exportPackageStmt))) {
					message = NLS.bind(PDEMessages.BundleErrorReporter_NotExistInProject, exportPackageStmt); //$NON-NLS-1$
					report(message, getPackageLine(header, exportPackageElements[i]),
							CompilerFlags.P_UNRESOLVED_IMPORTS); //$NON-NLS-1$
					continue;
				}
			}

		}
	}

	private void validateReprovideAttribute(IHeader header,
			ManifestElement requireBundleElements) {
		String message;
		String rexport = requireBundleElements
				.getAttribute(ICoreConstants.REPROVIDE_ATTRIBUTE);
		if (rexport != null) {
			validateBooleanAttributeValue(header, requireBundleElements,
					ICoreConstants.REPROVIDE_ATTRIBUTE);
			if (fEclipse3_1 && isCheckDeprecated()) {
				message = NLS
						.bind(
								PDEMessages.BundleErrorReporter_deprecated_attribute_reprovide,
								ICoreConstants.REPROVIDE_ATTRIBUTE); //$NON-NLS-1$
				report(message,
						getLine(header, ICoreConstants.REPROVIDE_ATTRIBUTE
								+ "="), CompilerFlags.P_DEPRECATED); //$NON-NLS-1$
			}
		}
	}

	private void validateRequireBundle(IProgressMonitor monitor) {
		IHeader header = (IHeader) fHeaders.get(Constants.REQUIRE_BUNDLE);
		if (header == null) {
			return;
		}
		String message = null;
		HashMap availableBundlesMap = getAvailableBundles();

		ManifestElement[] requireBundleElements = header.getElements();
		for (int i = 0; i < requireBundleElements.length; i++) {
			checkCanceled(monitor);

			String requireBundleStmt = requireBundleElements[i].getValue();
			if (COMPATIBILITY_PLUGIN.equals(requireBundleStmt)) {
				fCompatibility = true;
			}

			validateBundleVersionAttribute(header, requireBundleElements[i]);

			validateVisibilityDirective(header, requireBundleElements[i]);

			validateReprovideAttribute(header, requireBundleElements[i]);

			validateResolutionDirective(header, requireBundleElements[i]);

			validateOptionalAttribute(header, requireBundleElements[i]);

			if (!isCheckUnresolvedImports()) {
				return;
			}
			
			int severity = getRequireBundleSeverity(requireBundleElements[i]);

			/* This id does not exist in the PDE target platform */
			if (!availableBundlesMap.containsKey(requireBundleStmt)) {
				message = NLS.bind(PDEMessages.BundleErrorReporter_NotExistPDE, requireBundleStmt); //$NON-NLS-1$
				report(message, getPackageLine(header, requireBundleElements[i]),
						severity);
				continue;
			}
			IPluginModelBase availableModel = (IPluginModelBase) availableBundlesMap
					.get(requireBundleStmt);
			if (!(availableModel instanceof IPluginModel)) {
				/* This is a fragment */
				message = NLS.bind(PDEMessages.BundleErrorReporter_IsFragment, requireBundleStmt); //$NON-NLS-1$
				report(message, getPackageLine(header, requireBundleElements[i]),
						severity);
				continue;
			}
			String requiredVersionRange = requireBundleElements[i]
					.getAttribute(Constants.BUNDLE_VERSION_ATTRIBUTE);
			if (requiredVersionRange != null
					&& validateVersionRange(requiredVersionRange).isOK()) {
				VersionRange versionRange = new VersionRange(
						requiredVersionRange);
				String availableVersion = availableModel.getPluginBase()
						.getVersion();
				if (!versionRange.isIncluded(new Version(availableVersion))) {
					message = NLS.bind(PDEMessages.BundleErrorReporter_BundleRangeInvalidInBundleVersion, requireBundleStmt); //$NON-NLS-1$
					report(message, getPackageLine(header, requireBundleElements[i]),
							severity);
				}

			}
		}
	}

	private int getRequireBundleSeverity(ManifestElement requireBundleElement) {
		boolean optional = Constants.RESOLUTION_OPTIONAL
				.equals(requireBundleElement
						.getDirective(Constants.RESOLUTION_DIRECTIVE))
				|| "true".equals(requireBundleElement //$NON-NLS-1$
						.getAttribute(ICoreConstants.OPTIONAL_ATTRIBUTE));
		int severity = CompilerFlags.getFlag(fProject,
				CompilerFlags.P_UNRESOLVED_IMPORTS);
		if (optional && severity == CompilerFlags.ERROR) //$NON-NLS-1$ //$NON-NLS-2$
			severity = CompilerFlags.WARNING;
		return severity;
	}

	private void validateResolutionDirective(IHeader header,
			ManifestElement requireBundleElement) {
		String resolution = requireBundleElement
				.getDirective(Constants.RESOLUTION_DIRECTIVE);
		if (resolution != null) {
			validateDirectiveValue(header, requireBundleElement,
					Constants.RESOLUTION_DIRECTIVE, new String[] {
							Constants.RESOLUTION_MANDATORY,
							Constants.RESOLUTION_OPTIONAL });
		}
	}

	private void validateSingletonAttribute(IHeader header,
			ManifestElement element) {
		String message;
		String singletonAttr = element
				.getAttribute(ICoreConstants.SINGLETON_ATTRIBUTE);
		if (fHasExtensions) {
			if (!fEclipse3_1) {
				if (!"true".equals(singletonAttr)) { //$NON-NLS-1$
					message = NLS.bind(PDEMessages.BundleErrorReporter_singletonAttrRequired, ICoreConstants.SINGLETON_ATTRIBUTE); //$NON-NLS-1$
					report(message, header.getLineNumber() + 1,
							CompilerFlags.ERROR);
				}
			}
		}
		if (isCheckDeprecated()) {
			if (fEclipse3_1 && singletonAttr != null) {
				message = NLS
						.bind(
								PDEMessages.BundleErrorReporter_deprecated_attribute_singleton,
								ICoreConstants.SINGLETON_ATTRIBUTE); //$NON-NLS-1$
				report(message,
						getLine(header, ICoreConstants.SINGLETON_ATTRIBUTE
								+ "="), CompilerFlags.P_DEPRECATED); //$NON-NLS-1$
			}
		}
		validateBooleanAttributeValue(header, element,
				ICoreConstants.SINGLETON_ATTRIBUTE);
	}

	private void validateSingletonDirective(IHeader header,
			ManifestElement element) {
		String singletonDir = element
				.getDirective(Constants.SINGLETON_DIRECTIVE);
		if (fHasExtensions) {
			if (fEclipse3_1) {
				if (!"true".equals(singletonDir)) { //$NON-NLS-1$
					String message = NLS.bind(PDEMessages.BundleErrorReporter_singletonRequired, Constants.SINGLETON_DIRECTIVE); //$NON-NLS-1$
					report(message, header.getLineNumber() + 1,
							CompilerFlags.ERROR);
				}

			}
		}
		if (isCheckUnknownAttr()) {
			if (!fEclipse3_1 && singletonDir != null) {
				String message = NLS.bind(PDEMessages.BundleErrorReporter_UnknownDirective, Constants.SINGLETON_DIRECTIVE); //$NON-NLS-1$
				report(message, getLine(header, Constants.SINGLETON_DIRECTIVE
						+ ":="), //$NON-NLS-1$
						CompilerFlags.P_UNKNOWN_ATTRIBUTE);
			}
		}
		validateBooleanDirectiveValue(header, element,
				Constants.SINGLETON_DIRECTIVE);
	}

	private void validateSpecificationVersionAttribute(IHeader header,
			ManifestElement element) {
		String version = element
				.getAttribute(ICoreConstants.PACKAGE_SPECIFICATION_VERSION);
		IStatus status = validateVersionString(version);
		if(!status.isOK()){
			report(status.getMessage(), getPackageLine(header, element),
					CompilerFlags.ERROR); //$NON-NLS-1$
		}
		if (isCheckDeprecated()) {
			if (fEclipse3_1 && version != null) {
				String message = NLS
						.bind(
								PDEMessages.BundleErrorReporter_deprecated_attribute_specification_version,
								ICoreConstants.PACKAGE_SPECIFICATION_VERSION); //$NON-NLS-1$
				report(message,
						getPackageLine(header,
								element), CompilerFlags.P_DEPRECATED); //$NON-NLS-1$
			}
		}
	}

	private void validateVersionAttribute(IHeader header,
			ManifestElement element, boolean range) {
		String version = element.getAttribute(Constants.VERSION_ATTRIBUTE);
		if (version == null)
			return;
		IStatus status = range ? validateVersionRange(version)
				: validateVersionString(version);
		if(!status.isOK()) {
			report(status.getMessage(), getPackageLine(header, element),
					CompilerFlags.ERROR); //$NON-NLS-1$
		}
	}

	private void validateVisibilityDirective(IHeader header,
			ManifestElement requireBundleElement) {
		String visibility = requireBundleElement
				.getDirective(Constants.VISIBILITY_DIRECTIVE);
		if (visibility != null) {
			validateDirectiveValue(header, requireBundleElement,
					Constants.VISIBILITY_DIRECTIVE, new String[] {
							Constants.VISIBILITY_PRIVATE,
							Constants.VISIBILITY_REEXPORT });
		}
	}

		private void validateX_InternalDirective(IHeader header,
			ManifestElement element) {
		String internal = element
				.getDirective(ICoreConstants.INTERNAL_DIRECTIVE);
		if (internal == null) {
			return;
		}
		for (int i = 0; i < BOOLEAN_VALUES.length; i++) {
			if (BOOLEAN_VALUES[i].equals(internal)) {
				return;
			}
		}
		String message = NLS.bind(PDEMessages.BundleErrorReporter_dir_value,
				(new String[] { internal, ICoreConstants.INTERNAL_DIRECTIVE })); //$NON-NLS-1$
		report(message, getPackageLine(header, element),
				CompilerFlags.ERROR); //$NON-NLS-1$
	}

	private void validateX_FriendsDirective(IHeader header,
			ManifestElement element) {
		String friends = element.getDirective(ICoreConstants.FRIENDS_DIRECTIVE);
		String internal = element
				.getDirective(ICoreConstants.INTERNAL_DIRECTIVE);
		if (friends != null && internal != null) {
			String message = NLS.bind(
					PDEMessages.BundleErrorReporter_directive_hasNoEffectWith_,
					new String[] { ICoreConstants.FRIENDS_DIRECTIVE,
							ICoreConstants.INTERNAL_DIRECTIVE }); //$NON-NLS-1$
			report(message, getPackageLine(header, element),
					CompilerFlags.WARNING); //$NON-NLS-1$
		}
	}

	private void validateEclipsePlatformFilter() {
		IHeader header = (IHeader) fHeaders.get(ICoreConstants.PLATFORM_FILTER);
		if (header == null) {
			return;
		}
		String filter = header.getValue();
		try {
			PDE.getDefault().getBundleContext().createFilter(filter);
		} catch (InvalidSyntaxException ise) {
			report(PDEMessages.BundleErrorReporter_invalidFilterSyntax, header
					.getLineNumber() + 1, CompilerFlags.ERROR);
		}
	}

}
