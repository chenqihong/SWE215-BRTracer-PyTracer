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

package org.eclipse.core.variables;

import org.eclipse.core.internal.variables.StringVariableManager;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Plugin;
import org.eclipse.core.runtime.Status;

/**
 * The plug-in runtime class for the Core Variables plug-in.
 * @since 3.0
 */
public class VariablesPlugin extends Plugin {

	/**
	 * Status code indicating an unexpected internal error.
	 */
	public static final int INTERNAL_ERROR = 120;		
	
	/**
	 * Status code indicating a variable reference cycle error.
	 */
	public static final int REFERENCE_CYCLE_ERROR = 130;
	
	/**
	 * The single instance of this plug-in runtime class.
	 */
	private static VariablesPlugin plugin;

	/**
	 * Unique identifier constant (value <code>"org.eclipse.core.variables"</code>)
	 * for the Core Variables plug-in.
	 */
	public static final String PI_CORE_VARIABLES = "org.eclipse.core.variables"; //$NON-NLS-1$


	/** 
	 * Constructs an instance of this plug-in runtime class.
	 * <p>
	 * An instance of this plug-in runtime class is automatically created 
	 * when the facilities provided by the Variables plug-in are required.
	 * <b>Clients must never explicitly instantiate a plug-in runtime class.</b>
	 * </p>
	 */
	public VariablesPlugin() {
		super();
		plugin = this;
	}

	/**
	 * Returns this plug-in instance.
	 *
	 * @return the single instance of this plug-in runtime class
	 */
	public static VariablesPlugin getDefault() {
		return plugin;
	}
	
	/**
	 * Logs the specified throwable with this plug-in's log.
	 * 
	 * @param t throwable to log 
	 */
	public static void log(Throwable t) {
		log(new Status(IStatus.ERROR, PI_CORE_VARIABLES, INTERNAL_ERROR, "Error logged from Core Variables: ", t)); //$NON-NLS-1$
	}
	
	/**
	 * Logs the given message with this plug-in's log and the given
	 * throwable or <code>null</code> if none.
	 * @param message the message to log
	 * @param throwable the exception that occurred or <code>null</code> if none
	 */
	public static void logMessage(String message, Throwable throwable) {
		log(new Status(IStatus.ERROR, getUniqueIdentifier(), INTERNAL_ERROR, message, throwable));
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
	 * Convenience method which returns the unique identifier of this plugin.
	 */
	public static String getUniqueIdentifier() {
		return PI_CORE_VARIABLES;
	}
	
	/**
	 * Returns the string variable manager.
	 * 
	 * @return the string variable manager
	 */
	public IStringVariableManager getStringVariableManager() {
		return StringVariableManager.getDefault();
	}
}
