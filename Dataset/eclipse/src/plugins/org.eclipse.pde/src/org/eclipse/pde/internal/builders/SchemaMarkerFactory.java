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
package org.eclipse.pde.internal.builders;

import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;

public class SchemaMarkerFactory implements IMarkerFactory {
	public static final String MARKER_ID = "org.eclipse.pde.validation-marker"; //$NON-NLS-1$
	/**
	 * @see org.eclipse.pde.internal.builders.IMarkerFactory#createMarker(org.eclipse.core.resources.IFile)
	 */
	public IMarker createMarker(IFile file) throws CoreException {
		return file.createMarker(MARKER_ID);
	}
}
