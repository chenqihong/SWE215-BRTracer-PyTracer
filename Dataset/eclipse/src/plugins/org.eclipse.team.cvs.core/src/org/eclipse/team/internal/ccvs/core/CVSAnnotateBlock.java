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
package org.eclipse.team.internal.ccvs.core;

import org.eclipse.osgi.util.NLS;

/**
 * Model for a CVS Annotate block.
 */
public class CVSAnnotateBlock {

	String revision = ""; //$NON-NLS-1$
	String user = ""; //$NON-NLS-1$
	int startLine = 0;
	int endLine = 0;
	int sourceOffset = 0;
	boolean valid = false;

	/**
	 * @return
	 */
	public boolean isValid() {
		return valid;
	}

	/**
	 * @return index of line where source starts.
	 */
	public int getSourceOffset() {
		return sourceOffset;
	}

	/**
	 * @return int the last source line of the receiver
	 */
	public int getEndLine() {
		return endLine;
	}

	/**
	 * @param line
	 */
	public void setEndLine(int line) {
		endLine = line;
	}

	/**
	 * @return the revision the receiver occured in.
	 */
	public String getRevision() {
		return revision;
	}

	/**
	 * @return the first source line number of the receiver
	 */
	public int getStartLine() {
		return startLine;
	}


	/**
	 * Parase a CVS Annotate output line and instantiate the receiver
	 * @param line a CVS Annotate output line
	 */
	public CVSAnnotateBlock(String line, int lineNumber) {
		super();
		
		startLine = lineNumber;
		endLine = lineNumber;
		
		int index = line.indexOf(' ');
		if (index == -1) {
			return;
		}
		revision = line.substring(0, index);
		
		index = line.indexOf("(", index); //$NON-NLS-1$
		if (index == -1) {
			return;
		}
		
		int index2 = line.indexOf(' ', index);
		if (index2 == -1) {
			return;
		}
		
		user = line.substring(index + 1, index2);
		
		index = line.indexOf(":", index2); //$NON-NLS-1$
		if (index == -1) {
			return;
		}
		
		sourceOffset = index + 2;
		valid = true;
	}

	/**
	 * Used by the default LabelProvider to display objects in a List View
	 */
	public String toString() {
		int delta = endLine - startLine + 1;
		String line = CVSMessages.CVSAnnotateBlock_4; //$NON-NLS-1$
		if (delta == 1) {
			line = CVSMessages.CVSAnnotateBlock_5; //$NON-NLS-1$
		}
		return NLS.bind(CVSMessages.CVSAnnotateBlock_6, (new Object[] { //$NON-NLS-1$
        	user,
        	revision,
        	String.valueOf(delta),
        	line
        }));
	}

	/**
	 * Answer true if the receiver contains the given line number, false otherwse.
	 * @param i a line number
	 * @return true if receiver contains a line number.
	 */
	public boolean contains(int i) {
		return (i >= startLine && i <= endLine);
	}
}