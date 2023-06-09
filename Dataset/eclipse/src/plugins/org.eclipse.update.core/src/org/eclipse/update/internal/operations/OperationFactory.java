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
package org.eclipse.update.internal.operations;

import org.eclipse.update.configuration.*;
import org.eclipse.update.core.*;
import org.eclipse.update.operations.*;

public class OperationFactory implements IOperationFactory {

	public OperationFactory() {
	}

	public IConfigFeatureOperation createConfigOperation(
		IConfiguredSite targetSite,
		IFeature feature) {
		return new ConfigOperation(targetSite, feature);
	}

	public IBatchOperation createBatchInstallOperation(IInstallFeatureOperation[] operations) {
		return new BatchInstallOperation(operations);
	}

	public IInstallFeatureOperation createInstallOperation(
		IConfiguredSite targetSite,
		IFeature feature,
		IFeatureReference[] optionalFeatures,
		IFeature[] unconfiguredOptionalFeatures,
		IVerificationListener verifier) {
		return new InstallOperation(
			targetSite,
			feature,
			optionalFeatures,
			unconfiguredOptionalFeatures,
			verifier);
	}

	public IUnconfigFeatureOperation createUnconfigOperation(
		IConfiguredSite targetSite,
		IFeature feature) {
		return new UnconfigOperation(targetSite, feature);
	}

	public IConfigFeatureOperation createReplaceFeatureVersionOperation(
		IFeature feature,
		IFeature anotherFeature) {
	
		return new ReplaceFeatureVersionOperation(feature, anotherFeature);		
	}
		
	public IUninstallFeatureOperation createUninstallOperation(
		IConfiguredSite targetSite,
		IFeature feature) {
		return new UninstallOperation(targetSite, feature);
	}

	public IRevertConfigurationOperation createRevertConfigurationOperation(
		IInstallConfiguration config,
		IProblemHandler problemHandler) {
		return new RevertConfigurationOperation(
			config,
			problemHandler);
	}

	public IToggleSiteOperation createToggleSiteOperation(
		IConfiguredSite site) {
		return new ToggleSiteOperation(site);
	}
}
