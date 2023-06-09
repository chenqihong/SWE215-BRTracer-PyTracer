/*******************************************************************************
 * Copyright (c) 2002, 2005 Object Factory Inc.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *		Object Factory Inc. - Initial implementation
 *******************************************************************************/
package org.eclipse.ant.internal.ui.dtd.util;

import java.util.Comparator;

/**
 * Can be inherited or used statically.
 * @author Bob Foster
 */
public class SortedMapFactory {
	private static class StringComparator implements Comparator {
		/**
		 * @see java.util.Comparator#compare(Object, Object)
		 */
		public int compare(Object o1, Object o2) {
			return ((String)o1).compareTo(o2);
		}
	}
	private static class IndirectStringComparator implements Comparator {
		/**
		 * @see java.util.Comparator#compare(Object, Object)
		 */
		public int compare(Object o1, Object o2) {
			return o1.toString().compareTo(o2.toString());
		}
	}
	private static final StringComparator fStringComp = new StringComparator();
	private static final IndirectStringComparator fIndirectStringComp = new IndirectStringComparator();
	private static final Factory fFactory = new Factory();

	public static SortedMap getMap(IMapHolder holder, Comparator comp) {
		SortedMap map = (SortedMap) fFactory.getFree();
		if (map == null)
			map = new SortedMap();
		map.setMapHolder(holder);
		map.setComparator(comp);
		return map;
	}
	
	public static SortedMap getStringMap(IMapHolder holder) {
		return getMap(holder, fStringComp);
	}
	
	public static SortedMap getIndirectStringMap(IMapHolder holder) {
		return getMap(holder, fIndirectStringComp);
	}
	
	public static SortedMap getCaseInsensitiveStringMap(IMapHolder holder) {
		return getMap(holder, String.CASE_INSENSITIVE_ORDER);
	}
	
	public static void freeMap(SortedMap map) {
		map.setComparator(null);
		map.setMapHolder(null);
		fFactory.setFree(map);
	}
}
