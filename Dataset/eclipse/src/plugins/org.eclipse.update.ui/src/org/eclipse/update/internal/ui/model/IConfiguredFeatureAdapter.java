/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.update.internal.ui.model;


/**
 * @version 	1.0
 * @author
 */
public interface IConfiguredFeatureAdapter extends IFeatureAdapter, IConfiguredSiteContext {
	public boolean isConfigured();
	public boolean isUpdated();
}
