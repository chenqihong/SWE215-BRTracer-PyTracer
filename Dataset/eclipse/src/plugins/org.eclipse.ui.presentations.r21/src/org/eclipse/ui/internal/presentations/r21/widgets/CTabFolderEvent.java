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
package org.eclipse.ui.internal.presentations.r21.widgets;

import org.eclipse.swt.events.TypedEvent;
import org.eclipse.swt.widgets.Widget;

public class CTabFolderEvent extends TypedEvent {

    /**
     * Generated serial version UID for this class.
     * @since 3.1
     */
    private static final long serialVersionUID = 3258688793199719730L;

    public Widget item;

    public boolean doit;

    CTabFolderEvent(Widget w) {
        super(w);
    }
}