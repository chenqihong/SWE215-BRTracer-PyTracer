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
package org.eclipse.pde.internal.ui.wizards.templates;

import org.eclipse.pde.internal.ui.wizards.extension.NewExtensionTemplateWizard;

public class BuilderNewExtensionWizard extends NewExtensionTemplateWizard {

	public BuilderNewExtensionWizard() {
		super(new BuilderTemplate());
	}

}
