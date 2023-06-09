/**********************************************************************
 * Copyright (c) 2005 IBM Corporation and others. All rights reserved.   This
 * program and the accompanying materials are made available under the terms of
 * the Eclipse Public License v1.0 which accompanies this distribution, and is
 * available at http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors: 
 * IBM - Initial API and implementation
 **********************************************************************/
package org.eclipse.pde.internal;

import org.eclipse.osgi.util.NLS;

public class PDEMessages extends NLS {
	private static final String BUNDLE_NAME = "org.eclipse.pde.internal.pderesources";//$NON-NLS-1$

	public static String Builders_updating;
	public static String Builders_verifying;

	public static String Builders_DependencyLoopFinder_loopName;

	public static String Builders_Feature_reference;
	public static String Builders_Feature_freference;
	public static String Builders_Feature_multiplicity;
	public static String Builders_Feature_empty;
	public static String Builders_Feature_badURL;
	public static String Builders_Feature_exclusiveAttributes;
	public static String Builders_Feature_patchPlugin;
	public static String Builders_Feature_patchedVersion;
	public static String Builders_Feature_patchedMatch;
	public static String Builders_Feature_missingUnpackFalse;
	public static String Builders_Schema_compiling;
	public static String Builders_Schema_compilingSchemas;
	public static String Builders_Schema_removing;

	public static String Builders_Schema_noMatchingEndTag;
	public static String Builders_Schema_noMatchingStartTag;
	public static String Builders_Schema_forbiddenEndTag;
	public static String Builders_Schema_valueRequired;
	public static String Builders_Schema_valueNotRequired;

	public static String Builders_Manifest_missingRequired;
	public static String Builders_Manifest_dependency;
	public static String Builders_Manifest_ex_point;
	public static String Builders_Manifest_child;
	public static String Builders_Manifest_illegalRoot;
	public static String Builders_Manifest_attribute;
	public static String Builders_Manifest_att_value;
	public static String Builders_Manifest_pluginId_value;
	public static String Builders_Manifest_extensionPointId_value;
	public static String Builders_Manifest_non_ext_attribute;
	public static String Builders_Manifest_dont_translate_att;
	public static String Builders_Manifest_non_ext_element;
	public static String Builders_Manifest_deprecated_attribute;
	public static String Builders_Manifest_deprecated_element;
	public static String Builders_Manifest_unused_element;
	public static String Builders_Manifest_unused_attribute;
	public static String Builders_Manifest_class;
	public static String Builders_Manifest_resource;
	public static String Builders_Manifest_deprecated_3_0;
	public static String Builders_Manifest_key_not_found;

	public static String Builders_Convert_missingAttribute;
	public static String Builders_Convert_illegalValue;

	public static String BundleErrorReporter_lineTooLong;
	public static String BundleErrorReporter_noMainSection;
	public static String BundleErrorReporter_duplicateHeader;
	public static String BundleErrorReporter_noColon;
	public static String BundleErrorReporter_noSpaceValue;
	public static String BundleErrorReporter_nameHeaderInMain;
	public static String BundleErrorReporter_noNameHeader;
	public static String BundleErrorReporter_invalidHeaderName;
	public static String BundleErrorReporter_noLineTermination;
	public static String BundleErrorReporter_parseHeader;
	public static String BundleErrorReporter_att_value;
	public static String BundleErrorReporter_dir_value;
	public static String BundleErrorReporter_illegal_value;
	public static String BundleErrorReporter_deprecated_header_Provide_Package;
	public static String BundleErrorReporter_deprecated_attribute_optional;
	public static String BundleErrorReporter_deprecated_attribute_reprovide;
	public static String BundleErrorReporter_deprecated_attribute_singleton;
	public static String BundleErrorReporter_deprecated_attribute_specification_version;
	public static String BundleErrorReporter_directive_hasNoEffectWith_;
	public static String BundleErrorReporter_singletonAttrRequired;
	public static String BundleErrorReporter_singletonRequired;
	public static String BundleErrorReporter_headerMissing;
	public static String BundleErrorReporter_NoSymbolicName;
	public static String BundleErrorReporter_ClasspathNotEmpty;
	public static String BundleErrorReporter_fragmentActivator;
	public static String BundleErrorReporter_NoExist;
	public static String BundleErrorReporter_externalClass;
	public static String BundleErrorReporter_unusedPluginClass;
	public static String BundleErrorReporter_unresolvedCompatibilityActivator;
	public static String BundleErrorReporter_InvalidFormatInBundleVersion;
	public static String BundleErrorReporter_NotExistInProject;
	public static String BundleErrorReporter_CannotExportDefaultPackage;
	public static String BundleErrorReporter_BundleRangeInvalidInBundleVersion;
	public static String BundleErrorReporter_invalidVersionRangeFormat;
	public static String BundleErrorReporter_VersionNotInRange;
	public static String BundleErrorReporter_NotExistPDE;
	public static String BundleErrorReporter_IsFragment;
	public static String BundleErrorReporter_HostNotExistPDE;
	public static String BundleErrorReporter_HostIsFragment;
	public static String BundleErrorReporter_HostNeeded;
	public static String BundleErrorReporter_PackageNotExported;
	public static String BundleErrorReporter_UnknownDirective;
	public static String BundleErrorReporter_InvalidSymbolicName;
	public static String BundleErrorReporter_FileNotExist;
	public static String BundleErrorReporter_NativeNoProcessor;
	public static String BundleErrorReporter_NativeNoOSName;
	public static String BundleErrorReporter_NativeInvalidFilter;
	public static String BundleErrorReporter_NativeInvalidOSName;
	public static String BundleErrorReporter_NativeInvalidProcessor;
	public static String BundleErrorReporter_NativeInvalidLanguage;
	public static String BundleErrorReporter_NativeInvalidOSVersion;
	public static String BundleErrorReporter_invalidFilterSyntax;
	public static String FeatureConsistencyTrigger_JobName;
	static {
		// load message values from bundle file
		NLS.initializeMessages(BUNDLE_NAME, PDEMessages.class);
	}
}