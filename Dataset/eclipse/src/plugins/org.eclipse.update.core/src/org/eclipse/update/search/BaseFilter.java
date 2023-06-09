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
package org.eclipse.update.search;

import org.eclipse.update.core.*;

/**
 * Base filter class
 * 
 * <p>
 * <b>Note:</b> This class/interface is part of an interim API that is still under development and expected to
 * change significantly before reaching stability. It is being made available at this early stage to solicit feedback
 * from pioneering adopters on the understanding that any code that uses this API will almost certainly be broken
 * (repeatedly) as the API evolves.
 * </p>
 * @see UpdateSearchRequest
 * @see IUpdateSearchFilter
 * @since 3.0
 */
public class BaseFilter implements IUpdateSearchFilter {
    /**
     * @deprecated In 3.1 only the accept (IFeatureReference) will be used
     */
	public boolean accept(IFeature match) {
		return true;
	}
	public boolean accept(IFeatureReference match) {
		return true;
	}	
}
