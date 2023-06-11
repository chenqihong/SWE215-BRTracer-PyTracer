/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.runtime.registry;

public abstract class ParentAdapter extends PluginObjectAdapter {
	Object [] children;

public ParentAdapter(Object object) {
	super(object);
}
protected abstract Object[] createChildren();
public Object[] getChildren() {
	if (children==null) children = createChildren();
	return children;
}
}