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
package org.eclipse.pde.internal.ui.model.bundle;

import org.eclipse.osgi.util.*;
import org.eclipse.pde.internal.core.ibundle.IBundle;
import org.osgi.framework.*;

public class ExportPackageHeader extends BasePackageHeader {
    
    private static final long serialVersionUID = 1L;
   
    public ExportPackageHeader(String name, String value, IBundle bundle,
			String lineDelimiter) {
		super(name, value, bundle, lineDelimiter);
	}
    
    protected void processValue() {
        try {
            if (fValue != null) {
                ManifestElement[] elements = ManifestElement.parseHeader(fName, fValue);
                for (int i = 0; i < elements.length; i++) {
                    ExportPackageObject p = new ExportPackageObject(this, elements[i], getVersionAttribute());
                    fPackages.put(p.getName(), p);
                }
            }
        } catch (BundleException e) {
        }
    }
    
    public ExportPackageObject[] getPackages() {
        return (ExportPackageObject[])fPackages.values().toArray(new ExportPackageObject[fPackages.size()]);
    }
    
}
