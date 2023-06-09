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

import java.util.List;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWTError;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.SelectionProviderAction;
import org.eclipse.ui.part.MarkerTransfer;

/**
 * Copies one or more marker to the clipboard.
 */
public class ActionCopyMarker extends SelectionProviderAction {

    private IWorkbenchPart part;

    private Clipboard clipboard;

    private IField[] properties;

    /**
     * Creates the action.
     */
    public ActionCopyMarker(IWorkbenchPart part, ISelectionProvider provider) {
        super(provider, Messages.getString("copyAction.title")); //$NON-NLS-1$
        this.part = part;
        setImageDescriptor(PlatformUI.getWorkbench().getSharedImages()
                .getImageDescriptor(ISharedImages.IMG_TOOL_COPY));
        setEnabled(false);
    }

    /**
     * Sets the clipboard that the marker(s) will be copied to.
     * @param clipboard the clipboard
     */
    void setClipboard(Clipboard clipboard) {
        this.clipboard = clipboard;
    }

    /**
     * Sets the properties to be added to the plain-text marker report that 
     * will be copied to the clipboard.
     * @param properties
     */
    void setProperties(IField[] properties) {
        this.properties = properties;
    }

    /**
     * Copies the selected IMarker objects to the clipboard. If properties have 
     * been set, also copies a plain-text report of the selected markers to the
     * clipboard.
     */
    public void run() {
        IStructuredSelection selection = getStructuredSelection();
        if (selection == null || selection.isEmpty() || clipboard == null)
            return;
        List list = selection.toList();
        IMarker[] markers = new IMarker[list.size()];
        list.toArray(markers);
        setClipboard(markers, createMarkerReport(markers));
    }

    /** 
     * Updates enablement based on the current selection
     */
    public void selectionChanged(IStructuredSelection selection) {
        setEnabled(selection != null && !selection.isEmpty());
    }

    private void setClipboard(IMarker[] markers, String markerReport) {
        try {
            // Place the markers on the clipboard
            Object[] data;
            Transfer[] transferTypes;
            if (markerReport == null) {
                data = new Object[] { markers };
                transferTypes = new Transfer[] { MarkerTransfer.getInstance() };
            } else {
                data = new Object[] { markers, markerReport };
                transferTypes = new Transfer[] { MarkerTransfer.getInstance(),
                        TextTransfer.getInstance() };
            }

            clipboard.setContents(data, transferTypes);
        } catch (SWTError e) {
            if (e.code != DND.ERROR_CANNOT_SET_CLIPBOARD)
                throw e;
            if (MessageDialog
                    .openQuestion(
                            part.getSite().getShell(),
                            Messages
                                    .getString("CopyToClipboardProblemDialog.title"), Messages.getString("CopyToClipboardProblemDialog.message"))) //$NON-NLS-1$ //$NON-NLS-2$
                setClipboard(markers, markerReport);
        }
    }

    /**
     * Creates a plain-text report of the selected markers based on predefined properties.
     * @param markers
     * @return the marker report
     */
    String createMarkerReport(IMarker[] rawMarkers) {
        ConcreteMarker[] markers;
        try {
            markers = MarkerList.createMarkers(rawMarkers);
        } catch (CoreException e) {
            ErrorDialog.openError(part.getSite().getShell(), Messages
                    .getString("Error"), //$NON-NLS-1$
                    null, e.getStatus());
            return ""; //$NON-NLS-1$
        }

        StringBuffer report = new StringBuffer();

        final String NEWLINE = System.getProperty("line.separator"); //$NON-NLS-1$
        final char DELIMITER = '\t';

        if (properties == null) {
            return null;
        }

        //create header
        for (int i = 0; i < properties.length; i++) {
            report.append(properties[i].getDescription());
            if (i == properties.length - 1)
                report.append(NEWLINE);
            else
                report.append(DELIMITER);
        }

        for (int i = 0; i < markers.length; i++) {
            ConcreteMarker marker = markers[i];
            for (int j = 0; j < properties.length; j++) {
                report.append(properties[j].getValue(marker));
                if (j == properties.length - 1)
                    report.append(NEWLINE);
                else
                    report.append(DELIMITER);
            }
        }

        return report.toString();
    }
}

