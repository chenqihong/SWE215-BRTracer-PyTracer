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
package org.eclipse.jdt.internal.debug.eval.ast.instructions;


import java.text.MessageFormat;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.debug.core.IJavaObject;
import org.eclipse.jdt.debug.core.IJavaVariable;
import org.eclipse.jdt.internal.debug.core.JDIDebugPlugin;
import org.eclipse.jdt.internal.debug.core.model.JDINullValue;
import org.eclipse.jdt.internal.debug.core.model.JDIObjectValue;
 
/**
 * Pops an object off the stack, and pushes the value
 * of one of its fields onto the stack.
 */
public class PushFieldVariable extends CompoundInstruction {
	
	private String fDeclaringTypeSignature;
	
	private String fName;
	
	private int  fSuperClassLevel;
	
	public PushFieldVariable(String name, int superClassLevel, int start) {
		super(start);
		fName= name;
		fSuperClassLevel= superClassLevel;
	}
	
	public PushFieldVariable(String name, String declaringTypeSignature, int start) {
		super(start);
		fName= name;
		fDeclaringTypeSignature= declaringTypeSignature;
	}
	
	public void execute() throws CoreException {
		Object value= popValue();
		if (value instanceof JDINullValue) {
			throw new CoreException(new Status(IStatus.ERROR, JDIDebugPlugin.getUniqueIdentifier(), IStatus.OK, InstructionsEvaluationMessages.PushFieldVariable_0, null)); //$NON-NLS-1$
		}
		IJavaObject receiver=(IJavaObject) value;
		
		IJavaVariable field= null;
		
		if (fDeclaringTypeSignature == null) {
			field= ((JDIObjectValue)receiver).getField(fName, fSuperClassLevel);
		} else {
			field= receiver.getField(fName, fDeclaringTypeSignature);
		}
		
		if (field == null) {
			throw new CoreException(new Status(IStatus.ERROR, JDIDebugPlugin.getUniqueIdentifier(), IStatus.OK, MessageFormat.format(InstructionsEvaluationMessages.PushFieldVariable_Cannot_find_the_field__0__for_the_object__1__1, new String[] {fName, receiver.toString()}), null)); //$NON-NLS-1$ //$NON-NLS-2$
		} 
		push(field);
	}
	
	public String toString() {
		return MessageFormat.format(InstructionsEvaluationMessages.PushFieldVariable_push_field__0__2, new String[] {fName}); //$NON-NLS-1$
	}
}

