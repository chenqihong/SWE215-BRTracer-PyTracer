/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.internal.forms.widgets;

import java.util.Hashtable;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Canvas;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;

public class ControlSegment extends ObjectSegment implements IFocusSelectable {
	private boolean fill;
	
	public ControlSegment() {
	}
	
	public void setFill(boolean fill) {
		this.fill = fill;
	}
	
	public Control getControl(Hashtable resourceTable) {
		Object obj = resourceTable.get(getObjectId());
		if (obj instanceof Control) {
			Control c = (Control)obj;
			if (!c.isDisposed())
				return c;
		}
		return null;
	}

	protected Point getObjectSize(Hashtable resourceTable, int wHint) {
		Control control = getControl(resourceTable);
		if (control==null)
			return new Point(0,0);
		Point size = control.computeSize(wHint, SWT.DEFAULT);
		if (wHint!=SWT.DEFAULT && fill)
			size.x = Math.max(size.x, wHint);
		return size;
	}
	
	public void layout(GC gc, int width, Locator loc, Hashtable resourceTable,
			boolean selected) {
		super.layout(gc, width, loc, resourceTable, selected);
		Control control = getControl(resourceTable);
		if (control!=null)
			control.setBounds(getBounds());
	}

	public boolean setFocus(Hashtable resourceTable, boolean next) {
		Control c = getControl(resourceTable);
		if (c!=null) {
			return setFocus(c, next);
		}
		return false;
	}
	
	private boolean setFocus(Control c, boolean direction) {
		if (c instanceof Composite) {
			Composite comp = (Composite)c;
			Control [] tabList = comp.getTabList();
			if (direction) {
				for (int i=0; i<tabList.length; i++) {
					if (setFocus(tabList[i], direction))
						return true;
				}
			}
			else {
				for (int i=tabList.length-1; i>=0; i--) {
					if (setFocus(tabList[i], direction))
						return true;
				}
			}
			if (!(c instanceof Canvas))
				return false;
		}
		return c.setFocus();
	}

	public boolean isFocusSelectable(Hashtable resourceTable) {
		Control c = getControl(resourceTable);
		if (c!=null)
			return true;
		return false;
	}
}