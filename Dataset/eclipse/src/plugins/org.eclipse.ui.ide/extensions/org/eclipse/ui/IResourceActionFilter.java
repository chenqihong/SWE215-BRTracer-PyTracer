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
package org.eclipse.ui;

/**
 * Describes the public attributes for a resource and the acceptable values
 * each may have.  
 * <p>
 * A popup menu extension may use these constants to describe its object target.  
 * Each identifies an attribute name or possible value.  
 * <p>
 * Clients are not expected to implement this interface.
 * </p>
 *
 * @see org.eclipse.ui.IActionFilter
 */
public interface IResourceActionFilter extends IActionFilter {
    /**
     * An attribute indicating the file name (value <code>"name"</code>).  
     * The attribute value in xml is unconstrained.  "*" may be used at the start or
     * the end to represent "one or more characters".
     */
    public static final String NAME = "name"; //$NON-NLS-1$

    /**
     * An attribute indicating the file extension (value <code>"extension"</code>).
     * The attribute value in xml is unconstrained.
     */
    public static final String EXTENSION = "extension"; //$NON-NLS-1$

    /**
     * An attribute indicating the file path (value <code>"path"</code>).
     * The attribute value in xml is unconstrained.  "*" may be used at the start or
     * the end to represent "one or more characters".
     */
    public static final String PATH = "path"; //$NON-NLS-1$

    /**
     * An attribute indicating whether the file is read only (value <code>"readOnly"</code>).
     * The attribute value in xml must be one of <code>"true" or "false"</code>.
     */
    public static final String READ_ONLY = "readOnly"; //$NON-NLS-1$

    /**
     * An attribute indicating the project nature (value <code>"projectNature"</code>).
     * The attribute value in xml is unconstrained.
     */
    public static final String PROJECT_NATURE = "projectNature"; //$NON-NLS-1$

    /**
     * An attribute indicating a persistent property on the selected resource 
     * (value <code>"persistentProperty"</code>).
     * If the value is a simple string, then this simply tests for existence of the property on the resource.
     * If it has the format <code>"propertyName=propertyValue" this obtains the value of the property
     * with the specified name and tests it for equality with the specified value.
     */
    public static final String PERSISTENT_PROPERTY = "persistentProperty"; //$NON-NLS-1$

    /**
     * An attribute indicating a persistent property on the selected resource's project. 
     * (value <code>"projectPersistentProperty"</code>).
     * If the value is a simple string, then this simply tests for existence of the property on the resource.
     * If it has the format <code>"propertyName=propertyValue" this obtains the value of the property
     * with the specified name and tests it for equality with the specified value.
     */
    public static final String PROJECT_PERSISTENT_PROPERTY = "projectPersistentProperty"; //$NON-NLS-1$

    /**
     * An attribute indicating a session property on the selected resource 
     * (value <code>"sessionProperty"</code>).
     * If the value is a simple string, then this simply tests for existence of the property on the resource.
     * If it has the format <code>"propertyName=propertyValue" this obtains the value of the property
     * with the specified name and tests it for equality with the specified value.
     */
    public static final String SESSION_PROPERTY = "sessionProperty"; //$NON-NLS-1$

    /**
     * An attribute indicating a session property on the selected resource's project. 
     * (value <code>"projectSessionProperty"</code>).
     * If the value is a simple string, then this simply tests for existence of the property on the resource.
     * If it has the format <code>"propertyName=propertyValue" this obtains the value of the property
     * with the specified name and tests it for equality with the specified value.
     */
    public static final String PROJECT_SESSION_PROPERTY = "projectSessionProperty"; //$NON-NLS-1$

    /**
     * An attribute indicating that this is an xml file
     * and we should ensure that the first tag (or top-level
     * tag) has this name.
     * @since 3.0
     * @deprecated Please use content types instead.
     */
    public static final String XML_FIRST_TAG = "xmlFirstTag"; //$NON-NLS-1$

    /**
     * An attribute indicating that this is an xml file and we should ensure that the DTD
     * definition in this xml file is the value supplied with this attribute.
     * @since 3.0
     * @deprecated Please use content types instead.
     */
    public static final String XML_DTD_NAME = "xmlDTDName"; //$NON-NLS-1$

    /**
     * An attribute indicating that this is a file, and we are looking to verify
     * that the file matches the content type matching the given identifier.
     * The identifier is provided in the value.
     * @since 3.0
     */
    public static final String CONTENT_TYPE_ID = "contentTypeId"; //$NON-NLS-1$
}
