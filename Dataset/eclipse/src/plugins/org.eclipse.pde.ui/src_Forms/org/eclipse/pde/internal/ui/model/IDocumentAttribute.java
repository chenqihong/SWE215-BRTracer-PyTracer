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
package org.eclipse.pde.internal.ui.model;

import java.io.*;

public interface IDocumentAttribute extends Serializable {
	
	void setEnclosingElement(IDocumentNode node);	
	IDocumentNode getEnclosingElement();
	
	void setNameOffset(int offset);
	int getNameOffset();
	
	void setNameLength(int length);
	int getNameLength();
	
	void setValueOffset(int offset);
	int getValueOffset();
	
	void setValueLength(int length);
	int getValueLength();
	
	String getAttributeName();
	String getAttributeValue();
	
	String write();
	
}
