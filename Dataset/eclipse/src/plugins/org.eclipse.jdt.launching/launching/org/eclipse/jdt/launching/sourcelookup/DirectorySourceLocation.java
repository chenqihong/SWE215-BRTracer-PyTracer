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
package org.eclipse.jdt.launching.sourcelookup;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.text.MessageFormat;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.PlatformObject;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.IJavaModelStatusConstants;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.internal.launching.JavaLaunchConfigurationUtils;
import org.eclipse.jdt.internal.launching.LaunchingMessages;
import org.eclipse.jdt.internal.launching.LaunchingPlugin;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
 
/**
 * Locates source elements in a directory in the local
 * file system. Returns instances of <code>LocalFileStorage</code>.
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 * @see IJavaSourceLocation
 * @since 2.0
 * @deprecated In 3.0, the debug platform provides source lookup facilities that
 *  should be used in place of the Java source lookup support provided in 2.0.
 *  The new facilities provide a source lookup director that coordinates source
 *  lookup among a set of participants, searching a set of source containers.
 *  See the following packages: <code>org.eclipse.debug.core.sourcelookup</code>
 *  and <code>org.eclipse.debug.core.sourcelookup.containers</code>. This class
 *  has been replaced by
 *  <code>org.eclipse.debug.core.sourcelookup.containers.DirectorySourceContainer</code>.
 */
public class DirectorySourceLocation extends PlatformObject implements IJavaSourceLocation {

	/**
	 * The directory associated with this source location
	 */
	private File fDirectory;
	
	/**
	 * Constructs a new empty source location to be initialized from
	 * a memento.
	 */
	public DirectorySourceLocation() {
	}
		
	/**
	 * Constructs a new source location that will retrieve source
	 * elements from the given directory.
	 * 
	 * @param directory a directory
	 */
	public DirectorySourceLocation(File directory) {
		setDirectory(directory);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.launching.sourcelookup.IJavaSourceLocation#findSourceElement(java.lang.String)
	 */
	public Object findSourceElement(String name) throws CoreException {
		if (getDirectory() == null) {
			return null;
		}
		
		String pathStr= name.replace('.', '/');
		int lastSlash = pathStr.lastIndexOf('/');
		try {
			IPath root = new Path(getDirectory().getCanonicalPath());
			boolean possibleInnerType = false;
			String typeName = pathStr;
			do {
				IPath filePath = root.append(new Path(typeName + ".java")); //$NON-NLS-1$
				File file = filePath.toFile();
				if (file.exists()) {
					return new LocalFileStorage(file);
				}
				int index = typeName.lastIndexOf('$');
				if (index > lastSlash) {
					typeName = typeName.substring(0, index);
					possibleInnerType = true;
				} else {
					possibleInnerType = false;
				}						
			} while (possibleInnerType);			
		} catch (IOException e) {
			throw new JavaModelException(e, IJavaModelStatusConstants.IO_EXCEPTION);
		}
		return null;
	}

	/**
	 * Sets the directory in which source elements will
	 * be searched for.
	 * 
	 * @param directory a directory
	 */
	private void setDirectory(File directory) {
		fDirectory = directory;
	}
	
	/**
	 * Returns the directory associated with this source
	 * location.
	 * 
	 * @return directory
	 */
	public File getDirectory() {
		return fDirectory;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object object) {		
		return object instanceof DirectorySourceLocation &&
			 getDirectory().equals(((DirectorySourceLocation)object).getDirectory());
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		return getDirectory().hashCode();
	}	
	
	/* (non-Javadoc)
	 * @see org.eclipse.jdt.launching.sourcelookup.IJavaSourceLocation#getMemento()
	 */
	public String getMemento() throws CoreException {
		try {
			Document doc = LaunchingPlugin.getDocument();
			Element node = doc.createElement("directorySourceLocation"); //$NON-NLS-1$
			doc.appendChild(node);
			node.setAttribute("path", getDirectory().getAbsolutePath()); //$NON-NLS-1$
			return JavaLaunchConfigurationUtils.serializeDocument(doc);
		} catch (IOException e) {
			abort(MessageFormat.format(LaunchingMessages.DirectorySourceLocation_Unable_to_create_memento_for_directory_source_location__0__1, new String[] {getDirectory().getAbsolutePath()}), e); //$NON-NLS-1$
		} catch (ParserConfigurationException e) {
			abort(MessageFormat.format(LaunchingMessages.DirectorySourceLocation_Unable_to_create_memento_for_directory_source_location__0__1, new String[] {getDirectory().getAbsolutePath()}), e); //$NON-NLS-1$
		} catch (TransformerException e) {
			abort(MessageFormat.format(LaunchingMessages.DirectorySourceLocation_Unable_to_create_memento_for_directory_source_location__0__1, new String[] {getDirectory().getAbsolutePath()}), e); //$NON-NLS-1$
		}
		// execution will not reach here
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.launching.sourcelookup.IJavaSourceLocation#initializeFrom(java.lang.String)
	 */
	public void initializeFrom(String memento) throws CoreException {
		Exception ex = null;
		try {
			Element root = null;
			DocumentBuilder parser =
				DocumentBuilderFactory.newInstance().newDocumentBuilder();
			parser.setErrorHandler(new DefaultHandler());
			StringReader reader = new StringReader(memento);
			InputSource source = new InputSource(reader);
			root = parser.parse(source).getDocumentElement();
												
			String path = root.getAttribute("path"); //$NON-NLS-1$
			if (isEmpty(path)) {
				abort(LaunchingMessages.DirectorySourceLocation_Unable_to_initialize_source_location___missing_directory_path_3, null); //$NON-NLS-1$
			} else {
				File dir = new File(path);
				if (dir.exists() && dir.isDirectory()) {
					setDirectory(dir);
				} else {
					abort(MessageFormat.format(LaunchingMessages.DirectorySourceLocation_Unable_to_initialize_source_location___directory_does_not_exist___0__4, new String[] {path}), null); //$NON-NLS-1$
				}
			}
			return;
		} catch (ParserConfigurationException e) {
			ex = e;			
		} catch (SAXException e) {
			ex = e;
		} catch (IOException e) {
			ex = e;
		}
		abort(LaunchingMessages.DirectorySourceLocation_Exception_occurred_initializing_source_location__5, ex);		 //$NON-NLS-1$
	}

	private boolean isEmpty(String string) {
		return string == null || string.length() == 0;
	}
	
	/*
	 * Throws an internal error exception
	 */
	private void abort(String message, Throwable e)	throws CoreException {
		IStatus s = new Status(IStatus.ERROR, LaunchingPlugin.getUniqueIdentifier(), IJavaLaunchConfigurationConstants.ERR_INTERNAL_ERROR, message, e);
		throw new CoreException(s);		
	}
}
