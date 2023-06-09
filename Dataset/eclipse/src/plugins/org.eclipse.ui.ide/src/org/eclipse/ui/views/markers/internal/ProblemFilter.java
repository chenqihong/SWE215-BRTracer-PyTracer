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

package org.eclipse.ui.views.markers.internal;

import org.eclipse.core.resources.IMarker;
import org.eclipse.jface.dialogs.IDialogSettings;

public class ProblemFilter extends MarkerFilter {

    private static final String TAG_CONTAINS = "contains"; //$NON-NLS-1$

    private static final String TAG_DESCRIPTION = "description"; //$NON-NLS-1$

    private static final String TAG_DIALOG_SECTION = "filter"; //$NON-NLS-1$

    private static final String TAG_SELECT_BY_SEVERITY = "selectBySeverity"; //$NON-NLS-1$

    private static final String TAG_SEVERITY = "severity"; //$NON-NLS-1$

    final static boolean DEFAULT_CONTAINS = true;

    final static String DEFAULT_DESCRIPTION = ""; //$NON-NLS-1$	

    final static boolean DEFAULT_SELECT_BY_SEVERITY = false;

    final static int DEFAULT_SEVERITY = 0;

    final static int SEVERITY_ERROR = 1 << 2;

    final static int SEVERITY_WARNING = 1 << 1;

    final static int SEVERITY_INFO = 1 << 0;

    private boolean contains;

    private String description;

    private boolean selectBySeverity;

    private int severity;

    public ProblemFilter() {
        super(new String[] { IMarker.PROBLEM });
    }

    public boolean selectMarker(ConcreteMarker marker) {
        if (!(marker instanceof ProblemMarker)) {
            return false;
        }

        ProblemMarker problemMarker = (ProblemMarker) marker;

        return !isEnabled()
                || (super.selectMarker(problemMarker)
                        && selectByDescription(problemMarker) && selectBySeverity(problemMarker));
    }

    private boolean selectByDescription(ConcreteMarker item) {
        if (description == null || description.equals("")) //$NON-NLS-1$
            return true;

        String markerDescription = item.getDescription();
        int index = markerDescription.indexOf(description);
        return contains ? (index >= 0) : (index < 0);
    }

    private boolean selectBySeverity(ProblemMarker item) {
        if (selectBySeverity) {
            int markerSeverity = item.getSeverity();

            if (markerSeverity == IMarker.SEVERITY_ERROR)
                return (severity & SEVERITY_ERROR) > 0;
            else if (markerSeverity == IMarker.SEVERITY_WARNING)
                return (severity & SEVERITY_WARNING) > 0;
            else if (markerSeverity == IMarker.SEVERITY_INFO)
                return (severity & SEVERITY_INFO) > 0;
        }

        return true;
    }

    public boolean getContains() {
        return contains;
    }

    public String getDescription() {
        return description;
    }

    public boolean getSelectBySeverity() {
        return selectBySeverity;
    }

    public int getSeverity() {
        return severity;
    }

    public void setContains(boolean contains) {
        this.contains = contains;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setSelectBySeverity(boolean selectBySeverity) {
        this.selectBySeverity = selectBySeverity;
    }

    public void setSeverity(int severity) {
        this.severity = severity;
    }

    public void resetState() {
        super.resetState();
        contains = DEFAULT_CONTAINS;
        description = DEFAULT_DESCRIPTION;
        selectBySeverity = DEFAULT_SELECT_BY_SEVERITY;
        severity = DEFAULT_SEVERITY;
    }

    public void restoreState(IDialogSettings dialogSettings) {
        super.restoreState(dialogSettings);
        IDialogSettings settings = dialogSettings
                .getSection(TAG_DIALOG_SECTION);

        if (settings != null) {
            String setting = settings.get(TAG_CONTAINS);

            if (setting != null)
                contains = Boolean.valueOf(setting).booleanValue();

            setting = settings.get(TAG_DESCRIPTION);

            if (setting != null)
                description = new String(setting);

            setting = settings.get(TAG_SELECT_BY_SEVERITY);

            if (setting != null)
                selectBySeverity = Boolean.valueOf(setting).booleanValue();

            setting = settings.get(TAG_SEVERITY);

            if (setting != null)
                try {
                    severity = Integer.parseInt(setting);
                } catch (NumberFormatException eNumberFormat) {
                }
        }
    }

    public void saveState(IDialogSettings dialogSettings) {
        super.saveState(dialogSettings);

        if (dialogSettings != null) {
            IDialogSettings settings = dialogSettings
                    .getSection(TAG_DIALOG_SECTION);

            if (settings == null)
                settings = dialogSettings.addNewSection(TAG_DIALOG_SECTION);

            settings.put(TAG_CONTAINS, contains);
            settings.put(TAG_DESCRIPTION, description);
            settings.put(TAG_SELECT_BY_SEVERITY, selectBySeverity);
            settings.put(TAG_SEVERITY, severity);
        }
    }
}
