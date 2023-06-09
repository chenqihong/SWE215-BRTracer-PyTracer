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

package org.eclipse.ui.console;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.internal.console.ConsoleManager;
import org.eclipse.ui.internal.console.ConsolePluginImages;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * The console plug-in class.
 * 
 * @since 3.0
 */

public class ConsolePlugin extends AbstractUIPlugin {
	
	/**
	 * Singleton console manager
	 */
	private IConsoleManager fConsoleManager = null;

	/**
	 * The singleton console plugin instance
	 */
	private static ConsolePlugin fgPlugin= null;
	
	/**
	 * Unique identifier constant (value <code>"org.eclipse.ui.console"</code>)
	 * for the UI Console plug-in.
	 */
	private static final String PI_UI_CONSOLE = "org.eclipse.ui.console"; //$NON-NLS-1$
		
	/**
	 * Returns the singleton instance of the console plugin.
	 */
	public static ConsolePlugin getDefault() {
		return fgPlugin;
	}

	public ConsolePlugin() {
		super();
		fgPlugin = this;
	}
	
	/**
	 * Convenience method which returns the unique identifier of this plugin.
	 */
	public static String getUniqueIdentifier() {
		return PI_UI_CONSOLE;
	}

	/**
	 * Logs the specified status with this plug-in's log.
	 * 
	 * @param status status to log
	 */
	public static void log(IStatus status) {
		getDefault().getLog().log(status);
	}

	/**
	 * Logs the specified throwable with this plug-in's log.
	 * 
	 * @param t throwable to log 
	 */
	public static void log(Throwable t) {
		log(newErrorStatus("Error logged from Console plug-in: ", t)); //$NON-NLS-1$
	}
	
	/**
	 * Returns a new error status for this plugin with the given message
	 * @param message the message to be included in the status
	 * @param exception the exception to be included in the status or <code>null</code> if none
	 * @return a new error status
	 */
	public static IStatus newErrorStatus(String message, Throwable exception) {
		return new Status(IStatus.ERROR, getUniqueIdentifier(), IConsoleConstants.INTERNAL_ERROR, message, exception);
	}
	
	/**
	 * Returns the console manager. The manager will be created lazily on 
	 * the first access.
	 * 
	 * @return IConsoleManager
	 */
	public IConsoleManager getConsoleManager() {
		if (fConsoleManager == null) {
			fConsoleManager = new ConsoleManager();
		}
		return fConsoleManager;
	}

	/**
	 * Returns the standard display to be used. The method first checks, if
	 * the thread calling this method has an associated display. If so, this
	 * display is returned. Otherwise the method returns the default display.
	 */
	public static Display getStandardDisplay() {
		Display display= Display.getCurrent();
		if (display == null) {
			display= Display.getDefault();
		}
		return display;		
	}
	
	/**
	 * Utility method with conventions
	 */
	public static void errorDialog(Shell shell, String title, String message, Throwable t) {
		IStatus status;
		if (t instanceof CoreException) {
			status= ((CoreException)t).getStatus();
			// if the 'message' resource string and the IStatus' message are the same,
			// don't show both in the dialog
			if (status != null && message.equals(status.getMessage())) {
				message= null;
			}
		} else {
			status= new Status(IStatus.ERROR, getUniqueIdentifier(), IConsoleConstants.INTERNAL_ERROR, "Error within Debug UI: ", t); //$NON-NLS-1$
			log(status);	
		}
		ErrorDialog.openError(shell, title, message, status);
	}
    
    /**
     * Returns the <code>Image</code> identified by the given key,
     * or <code>null</code> if it does not exist.
     * 
     * @return the <code>Image</code> identified by the given key,
     * or <code>null</code> if it does not exist
     * @since 3.1
     */
    public static Image getImage(String key) {
        return ConsolePluginImages.getImage(key);
    }
    
    /**
     * Returns the <code>ImageDescriptor</code> identified by the given key,
     * or <code>null</code> if it does not exist.
     * 
     * @return the <code>ImageDescriptor</code> identified by the given key,
     * or <code>null</code> if it does not exist
     * @since 3.1
     */
    public static ImageDescriptor getImageDescriptor(String key) {
        return ConsolePluginImages.getImageDescriptor(key);
    }
    
    /* (non-Javadoc)
     * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
     */
    public void stop(BundleContext context) throws Exception {
    	if (fConsoleManager != null) {
	        IConsole[] consoles = fConsoleManager.getConsoles();
	        if (consoles != null) {
	            fConsoleManager.removeConsoles(consoles);
	        }
    	}
        super.stop(context);
    }    
    
    
}
