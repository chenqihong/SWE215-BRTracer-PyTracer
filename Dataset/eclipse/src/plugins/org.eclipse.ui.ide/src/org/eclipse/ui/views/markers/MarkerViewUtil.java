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

package org.eclipse.ui.views.markers;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.views.markers.internal.MarkerView;

/**
 * Utility class for showing markers in the marker views.
 */
public class MarkerViewUtil {

    /**
     * Returns the id of the view used to show markers of the
     * same type as the given marker.
     * 
     * @param marker the marker
     * @return the view id or <code>null</code> if no appropriate view could be determined
     * @throws CoreException if an exception occurs testing the type of the marker
     */
    public static String getViewId(IMarker marker) throws CoreException {
        if (marker.isSubtypeOf(IMarker.TASK)) {
            return IPageLayout.ID_TASK_LIST;
        } else if (marker.isSubtypeOf(IMarker.PROBLEM)) {
            return IPageLayout.ID_PROBLEM_VIEW;
        } else if (marker.isSubtypeOf(IMarker.BOOKMARK)) {
            return IPageLayout.ID_BOOKMARKS;
        }
        return null;
    }

    /**
     * Shows the given marker in the appropriate view in the given page.
     * This must be called from the UI thread.
     * 
     * @param page the workbench page in which to show the marker
     * @param marker the marker to show
     * @param showView <code>true</code> if the view should be shown first
     *   <code>false</code> to only show the marker if the view is already showing 
     * @return <code>true</code> if the marker was successfully shown,
     *   <code>false</code> if not
     */
    public static boolean showMarker(IWorkbenchPage page, IMarker marker,
            boolean showView) {
        try {
            String viewId = getViewId(marker);
            if (viewId != null) {
                IViewPart view = showView ? page.showView(viewId) : page
                        .findView(viewId);
                if (view instanceof MarkerView) {
                    StructuredSelection selection = new StructuredSelection(
                            marker);
                    MarkerView markerView = (MarkerView) view;
                    markerView.setSelection(selection, true);
                    return true;

                    //return markerView.getSelection().equals(selection); 
                }
            }
        } catch (CoreException e) {
            // ignore
        }
        return false;
    }

}
