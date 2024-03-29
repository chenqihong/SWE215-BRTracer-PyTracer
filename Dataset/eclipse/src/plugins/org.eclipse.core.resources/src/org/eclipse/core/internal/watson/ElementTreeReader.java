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
package org.eclipse.core.internal.watson;

import java.io.*;
import org.eclipse.core.internal.dtree.DataTreeReader;
import org.eclipse.core.internal.dtree.IDataFlattener;
import org.eclipse.core.internal.utils.Assert;
import org.eclipse.core.internal.utils.Messages;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;

/** <code>ElementTreeReader</code> is the standard implementation
 * of an element tree serialization reader.
 *
 * <p>Subclasses of this reader read can handle current and various 
 * known old formats of a saved element tree, and dispatch internally
 * to an appropriate reader.
 *
 * <p>The reader has an <code>IElementInfoFactory</code>,
 * which it consults for the schema and for creating
 * and reading element infos.
 *
 * <p>Element tree readers are thread-safe; several
 * threads may share a single reader provided, of course,
 * that the <code>IElementInfoFactory</code> is thread-safe.
 */
public class ElementTreeReader {
	/** The element info factory.
	 */
	protected IElementInfoFlattener elementInfoFlattener;

	/**
	 * For reading and writing delta trees
	 */
	protected DataTreeReader dataTreeReader;

	/**
	 * Constructs a new element tree reader that works for
	 * the given element info flattener.
	 */
	public ElementTreeReader(final IElementInfoFlattener factory) {
		Assert.isNotNull(factory);
		elementInfoFlattener = factory;

		/* wrap the IElementInfoFlattener in an IDataFlattener */
		IDataFlattener f = new IDataFlattener() {
			public void writeData(IPath path, Object data, DataOutput output) {
				//not needed
			}

			public Object readData(IPath path, DataInput input) throws IOException {
				//never read the root node of an ElementTree
				//this node is reserved for the parent backpointer
				if (!Path.ROOT.equals(path))
					return factory.readElement(path, input);
				return null;
			}
		};
		dataTreeReader = new DataTreeReader(f);
	}

	/**
	 * Returns the appropriate reader for the given version.
	 */
	public ElementTreeReader getReader(int formatVersion) throws IOException {
		switch (formatVersion) {
			case 1 :
				return new ElementTreeReaderImpl_1(elementInfoFlattener);
			default :
				throw new IOException(Messages.watson_unknown);
		}
	}

	/**
	 * Reads an element tree delta from the input stream, and
	 * reconstructs it as a delta on the given tree.
	 */
	public ElementTree readDelta(ElementTree completeTree, DataInput input) throws IOException {
		/* Dispatch to the appropriate reader. */
		ElementTreeReader realReader = getReader(readNumber(input));
		return realReader.readDelta(completeTree, input);
	}

	/**
	 * Reads a chain of ElementTrees from the given input stream.
	 * @return A chain of ElementTrees, where the first tree in the list is
	 * complete, and all other trees are deltas on the previous tree in the list.
	 */
	public ElementTree[] readDeltaChain(DataInput input) throws IOException {
		/* Dispatch to the appropriate reader. */
		ElementTreeReader realReader = getReader(readNumber(input));
		return realReader.readDeltaChain(input);
	}

	/** 
	 * Reads an integer stored in compact format.  Numbers between
	 * 0 and 254 inclusive occupy 1 byte; other numbers occupy 5 bytes,
	 * the first byte being 0xff and the next 4 bytes being the standard
	 * representation of an int.
	 */
	protected static int readNumber(DataInput input) throws IOException {
		byte b = input.readByte();
		int number = (b & 0xff); // not a no-op! converts unsigned byte to int

		if (number == 0xff) { // magic escape value
			number = input.readInt();
		}
		return number;
	}

	/**
	 * Reads an element tree from the input stream and returns it.
	 * This method actually just dispatches to the appropriate reader
	 * depending on the stream version id.
	 */
	public ElementTree readTree(DataInput input) throws IOException {
		/* Dispatch to the appropriate reader. */
		ElementTreeReader realReader = getReader(readNumber(input));
		return realReader.readTree(input);
	}
}
