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
package org.eclipse.help.internal.util;
import java.io.*;

public class URLCoder {
	public static String encode(String s) {
		try {
			return urlEncode(s.getBytes("UTF8")); //$NON-NLS-1$
		} catch (UnsupportedEncodingException uee) {
			return null;
		}
	}
	public static String decode(String s) {
		try {
			return new String(urlDecode(s), "UTF8"); //$NON-NLS-1$
		} catch (UnsupportedEncodingException uee) {
			return null;
		}
	}
	private static String urlEncode(byte[] data) {
		StringBuffer buf = new StringBuffer(data.length);
		for (int i = 0; i < data.length; i++) {
			buf.append('%');
			buf.append(Character.forDigit((data[i] & 240) >>> 4, 16));
			buf.append(Character.forDigit(data[i] & 15, 16));
		}
		return buf.toString();
	}
	private static byte[] urlDecode(String encodedURL) {
		int len = encodedURL.length();
		ByteArrayOutputStream os = new ByteArrayOutputStream(len);
		for (int i = 0; i < len;) {
			switch (encodedURL.charAt(i)) {
				case '%' :
					if (len >= i + 3) {
						os.write(Integer.parseInt(encodedURL.substring(i + 1,
								i + 3), 16));
					}
					i += 3;
					break;
				case '+' : //exception from standard
					os.write(' ');
					i++;
					break;
				default :
					os.write(encodedURL.charAt(i++));
					break;
			}

		}
		return os.toByteArray();
	}
}
