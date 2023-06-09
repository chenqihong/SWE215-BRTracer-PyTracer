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
package org.eclipse.team.internal.ccvs.ui;


import java.util.Map;

public class CVSDecoratorConfiguration {

	// bindings for 
	public static final String RESOURCE_NAME = "name"; //$NON-NLS-1$
	public static final String RESOURCE_TAG = "tag"; //$NON-NLS-1$
	public static final String FILE_REVISION = "revision"; //$NON-NLS-1$
	public static final String FILE_KEYWORD = "keyword"; //$NON-NLS-1$
	
	// bindings for repository location
	public static final String REMOTELOCATION_METHOD = "method"; //$NON-NLS-1$
	public static final String REMOTELOCATION_USER = "user"; //$NON-NLS-1$
	public static final String REMOTELOCATION_HOST = "host"; //$NON-NLS-1$
	public static final String REMOTELOCATION_ROOT = "root"; //$NON-NLS-1$
	public static final String REMOTELOCATION_REPOSITORY = "repository"; //$NON-NLS-1$
	
	// bindings for resource states
	public static final String DIRTY_FLAG = "dirty_flag"; //$NON-NLS-1$
	public static final String ADDED_FLAG = "added_flag"; //$NON-NLS-1$
	public static final String DEFAULT_DIRTY_FLAG = ">"; //$NON-NLS-1$
	public static final String DEFAULT_ADDED_FLAG = "*"; //$NON-NLS-1$
	
	// default text decoration formats
	public static final String DEFAULT_FILETEXTFORMAT = "{dirty_flag}{name}  {revision} {tag} ({keyword})"; //$NON-NLS-1$
	public static final String DEFAULT_FOLDERTEXTFORMAT = "{dirty_flag}{name}  {tag}"; //$NON-NLS-1$
	public static final String DEFAULT_PROJECTTEXTFORMAT = "{dirty_flag}{name}  {tag} [{host}]"; //$NON-NLS-1$

	// prefix characters that can be removed if the following binding is not found
	private static final char KEYWORD_SEPCOLON = ':';
	private static final char KEYWORD_SEPAT = '@';
	
	// font and color definition ids
	public static final String OUTGOING_CHANGE_FOREGROUND_COLOR = "org.eclipse.team.cvs.ui.fontsandcolors.outgoing_change_foreground_color"; //$NON-NLS-1$
	public static final String OUTGOING_CHANGE_BACKGROUND_COLOR = "org.eclipse.team.cvs.ui.fontsandcolors.outgoing_change_background_color"; //$NON-NLS-1$
	public static final String OUTGOING_CHANGE_FONT = "org.eclipse.team.cvs.ui.fontsandcolors.outgoing_change_font"; //$NON-NLS-1$
	public static final String IGNORED_FOREGROUND_COLOR = "org.eclipse.team.cvs.ui.fontsandcolors.ignored_resource_foreground_color"; //$NON-NLS-1$
	public static final String IGNORED_BACKGROUND_COLOR = "org.eclipse.team.cvs.ui.fontsandcolors.ignored_resource_background_color"; //$NON-NLS-1$
	public static final String IGNORED_FONT = "org.eclipse.team.cvs.ui.fontsandcolors.ignored_resource_font"; //$NON-NLS-1$
	
	public static void decorate(CVSDecoration decoration, String format, Map bindings) {
		StringBuffer prefix = new StringBuffer(80);
		StringBuffer suffix = new StringBuffer(80);
		StringBuffer output = prefix;
		
		int length = format.length();
		int start = -1;
		int end = length;
		while (true) {
			if ((end = format.indexOf('{', start)) > -1) {
				output.append(format.substring(start + 1, end));
				if ((start = format.indexOf('}', end)) > -1) {
					String key = format.substring(end + 1, start);
					String s;

					//We use the RESOURCE_NAME key to determine if we are doing the prefix or suffix.  The name isn't actually part of either.					
					if(key.equals(RESOURCE_NAME)) {
						output = suffix;
						s = null;
					} else {
						s = (String) bindings.get(key);
					}

					if(s!=null) {
						output.append(s);
					} else {
						// support for removing prefix character if binding is null
						int curLength = output.length();
						if(curLength>0) {
							char c = output.charAt(curLength - 1);
							if(c == KEYWORD_SEPCOLON || c == KEYWORD_SEPAT) {
								output.deleteCharAt(curLength - 1);							
							}
						}
					}
				} else {
					output.append(format.substring(end, length));
					break;
				}
			} else {
				output.append(format.substring(start + 1, length));
				break;
			}
		}
		
		if (prefix.length() != 0) {
			decoration.addPrefix(prefix.toString());
		}
		if (suffix.length() != 0) {
			decoration.addSuffix(suffix.toString());
		}
	}
}
