/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.builders;

import org.eclipse.core.resources.*;
import org.w3c.dom.*;


public class PluginErrorReporter extends PluginBaseErrorReporter {

	public PluginErrorReporter(IFile file) {
		super(file);
	}
		
	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.builders.PluginBaseErrorReporter#validateTopLevelAttributes(org.w3c.dom.Element)
	 */
	protected void validateTopLevelAttributes(Element element) {
		super.validateTopLevelAttributes(element);
		Attr attr = element.getAttributeNode("class"); //$NON-NLS-1$
		if (attr != null)
			validateJavaAttribute(element, attr);
	}
	
	protected String getRootElementName() {
		return "plugin"; //$NON-NLS-1$
	}

}
