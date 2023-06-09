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
package org.eclipse.compare.rangedifferencer;

/* package */ class LinkedRangeDifference extends RangeDifference {

	static final int INSERT= 0;
	static final int DELETE= 1;

	LinkedRangeDifference fNext;

	/*
	 * Creates a LinkedRangeDifference an initializes it to the error state
	 */
	LinkedRangeDifference() {
		super(ERROR);
		fNext= null;
	}

	/*
	 * Constructs and links a LinkeRangeDifference to another LinkedRangeDifference
	 */
	LinkedRangeDifference(LinkedRangeDifference next, int operation) {
		super(operation);
		fNext= next;
	}

	/*
	 * Follows the next link
	 */
	LinkedRangeDifference getNext() {
		return fNext;
	}

	boolean isDelete() {
		return kind() == DELETE;
	}

	boolean isInsert() {
		return kind() == INSERT;
	}

	/*
	 * Sets the next link of this LinkedRangeDifference
	 */
	void setNext(LinkedRangeDifference next) {
		fNext= next;
	}
}
