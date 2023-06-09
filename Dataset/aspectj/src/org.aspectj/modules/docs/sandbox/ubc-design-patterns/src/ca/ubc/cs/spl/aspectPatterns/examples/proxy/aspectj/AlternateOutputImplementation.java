package ca.ubc.cs.spl.aspectPatterns.examples.proxy.aspectj;

/* -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*-
 *
 * This file is part of the design patterns project at UBC
 *
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * either http://www.mozilla.org/MPL/ or http://aspectj.org/MPL/.
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is ca.ubc.cs.spl.aspectPatterns.
 * 
 * For more details and the latest version of this code, please see:
 * http://www.cs.ubc.ca/labs/spl/projects/aodps.html
 *
 * Contributor(s):   
 */

/**
 * This class will serve as a Delegate for OutputImplementation. Note that no 
 * interfaces need to be implemented. This is not a <i>Subject</i> and does in
 * fact play no role in the pattern.
 *
 * @author  Jan Hannemann
 * @author  Gregor Kiczales
 * @version 1.1, 02/17/04
 */  

public class AlternateOutputImplementation {  

	/**
	 * Prints the argument string to 
	 * <code>System.out</code>
	 *
	 * @param s the string to print
	 */
     
	public void alternateRequest(String s) {
		System.out.println("[AlternateSubject.safeRequest()]: "+s);
	}
}