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
package org.eclipse.jdi.internal;


import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import org.eclipse.jdi.internal.jdwp.JdwpID;

import com.sun.jdi.BooleanValue;
import com.sun.jdi.Type;

/**
 * this class implements the corresponding interfaces
 * declared by the JDI specification. See the com.sun.jdi package
 * for more information.
 *
 */
public class BooleanValueImpl extends PrimitiveValueImpl implements BooleanValue {
	/** JDWP Tag. */
	public static final byte tag = JdwpID.BOOLEAN_TAG;

	/**
	 * Creates new instance.
	 */
	public BooleanValueImpl(VirtualMachineImpl vmImpl, Boolean value) {
		super("BooleanValue", vmImpl, value); //$NON-NLS-1$
	}
	
	/**
	 * @returns tag.
	 */
	public byte getTag() {
		return tag;
	}

	/**
	 * @returns type of value.
   	 */
	public Type type() {
		return virtualMachineImpl().getBooleanType();
	}

	/**
	 * @returns Value.
	 */
	public boolean value() {
		return booleanValue();
	}
	
	/**
	 * @return Reads and returns new instance.
	 */
	public static BooleanValueImpl read(MirrorImpl target, DataInputStream in) throws IOException {
		VirtualMachineImpl vmImpl = target.virtualMachineImpl();
		boolean value = target.readBoolean("booleanValue", in); //$NON-NLS-1$
		return new BooleanValueImpl(vmImpl, new Boolean(value));
	}

	/**
	 * Writes value without value tag.
	 */
	public void write(MirrorImpl target, DataOutputStream out) throws IOException {
		target.writeBoolean(((Boolean)fValue).booleanValue(), "booleanValue", out); //$NON-NLS-1$
	}
}
