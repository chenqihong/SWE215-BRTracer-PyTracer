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
package org.eclipse.jdi.internal.request;


import org.eclipse.jdi.internal.VirtualMachineImpl;
import org.eclipse.jdi.internal.event.ClassPrepareEventImpl;

import com.sun.jdi.request.ClassPrepareRequest;

/**
 * this class implements the corresponding interfaces
 * declared by the JDI specification. See the com.sun.jdi package
 * for more information.
 *
 */
public class ClassPrepareRequestImpl extends EventRequestImpl implements ClassPrepareRequest {
	/**
	 * Creates new ClassPrepareRequest.
	 */
	public ClassPrepareRequestImpl(VirtualMachineImpl vmImpl) {
		super("ClassPrepareRequest", vmImpl); //$NON-NLS-1$
	}

	/**
	 * @return Returns JDWP EventKind.
	 */
	protected final byte eventKind() {
		return ClassPrepareEventImpl.EVENT_KIND;
	}
}
