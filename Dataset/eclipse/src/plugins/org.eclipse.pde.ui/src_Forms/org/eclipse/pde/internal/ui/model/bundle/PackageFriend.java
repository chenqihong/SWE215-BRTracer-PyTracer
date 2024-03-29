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

import org.eclipse.pde.internal.core.bundle.BundleObject;

public class PackageFriend extends BundleObject {

    private static final long serialVersionUID = 1L;

    private String fName;

    private PackageObject fPackageObject;
    
    public PackageFriend(PackageObject object, String name) {
        fName = name;
        fPackageObject = object;
    }

    public String getName() {
        return fName;
    }
    
    public String toString() {
        return fName;
    }
    
    public ManifestHeader getHeader() {
        return fPackageObject.getHeader();
    }

}
