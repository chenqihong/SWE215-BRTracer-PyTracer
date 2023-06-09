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
package org.eclipse.pde.internal.ui.editor.schema;

import org.eclipse.pde.internal.core.ischema.*;
import org.eclipse.swt.widgets.*;

public interface IRestrictionPage {
	public Control createControl(Composite parent);
	public Class getCompatibleRestrictionClass();
	public Control getControl();
	public ISchemaRestriction getRestriction();
	public void initialize(ISchemaRestriction restriction);
}
