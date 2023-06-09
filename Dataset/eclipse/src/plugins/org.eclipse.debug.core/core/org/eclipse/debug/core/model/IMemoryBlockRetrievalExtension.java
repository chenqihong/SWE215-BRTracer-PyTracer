/*******************************************************************************
 * Copyright (c) 2004, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.debug.core.model;

import org.eclipse.debug.core.DebugException;


/**
 * Extended capabilites for memory block retrieval. Supports the retrival
 * of memory blocks based on an expression and context.
 * 
 * @since 3.1
 */
public interface IMemoryBlockRetrievalExtension extends IMemoryBlockRetrieval {
	
	/**
	 * Retrieves and returns a memory block. 
	 * 
	 * @param expression expression to be evalutated to an address
	 * @param context context for evaluating the expression.  This is typically
	 *  a debug element.
	 * @return a memory block based on the given expression and context
	 * @throws DebugException if unable to retrieve the specified memory
	 */
	
	public IMemoryBlockExtension getExtendedMemoryBlock(String expression, Object context) throws DebugException;
}
