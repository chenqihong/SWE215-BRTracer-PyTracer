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
package org.eclipse.debug.internal.ui;

 
import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.internal.ui.launchConfigurations.PersistableLaunchConfigurationFactory;
import org.eclipse.debug.internal.ui.launchConfigurations.PersistableLaunchConfigurationTypeFactory;
import org.eclipse.debug.internal.ui.views.breakpoints.BreakpointContainer;
import org.eclipse.debug.internal.ui.views.breakpoints.BreakpointContainerWorkbenchAdapter;
import org.eclipse.debug.internal.ui.views.breakpoints.BreakpointPersistableElementAdapter;
import org.eclipse.ui.IPersistableElement;
import org.eclipse.ui.model.IWorkbenchAdapter;
import org.eclipse.ui.model.IWorkbenchAdapter2;

public class DebugUIAdapterFactory implements IAdapterFactory {

	/**
	 * @see org.eclipse.core.runtime.IAdapterFactory#getAdapter(Object, Class)
	 */
	public Object getAdapter(Object obj, Class adapterType) {
		if (adapterType.isInstance(obj)) {
			return obj;
		}
		
		if (adapterType == IPersistableElement.class) {
			if (obj instanceof ILaunchConfiguration) {
				return new PersistableLaunchConfigurationFactory((ILaunchConfiguration)obj);
			} else if (obj instanceof ILaunchConfigurationType) {
				return new PersistableLaunchConfigurationTypeFactory((ILaunchConfigurationType)obj);
			} else if (obj instanceof IBreakpoint) {
			    return new BreakpointPersistableElementAdapter((IBreakpoint)obj);
            }
		}
        
        if (adapterType == IWorkbenchAdapter.class) {
        	if (obj instanceof IWorkbenchAdapter) {
        		return obj;
        	}
            if (obj instanceof BreakpointContainer) {
                return new BreakpointContainerWorkbenchAdapter();
            }
        }
		
        if (adapterType == IWorkbenchAdapter2.class) {
            if (obj instanceof IWorkbenchAdapter2) {
                return obj;
            }
            if (obj instanceof BreakpointContainer) {
                return new BreakpointContainerWorkbenchAdapter();
            }
        }
        
		return null;
	}

	/**
	 * @see org.eclipse.core.runtime.IAdapterFactory#getAdapterList()
	 */
	public Class[] getAdapterList() {
		return new Class[] {IPersistableElement.class, IWorkbenchAdapter.class, IWorkbenchAdapter2.class};
	}

}
