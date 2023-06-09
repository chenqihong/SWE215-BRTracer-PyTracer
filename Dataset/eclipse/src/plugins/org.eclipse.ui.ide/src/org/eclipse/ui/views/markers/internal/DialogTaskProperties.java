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

import java.util.Map;

import org.eclipse.core.resources.IMarker;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

public class DialogTaskProperties extends DialogMarkerProperties {

    private static final String PRIORITY_HIGH = Messages
            .getString("propertiesDialog.priorityHigh"); //$NON-NLS-1$

    private static final String PRIORITY_NORMAL = Messages
            .getString("propertiesDialog.priorityNormal"); //$NON-NLS-1$

    private static final String PRIORITY_LOW = Messages
            .getString("propertiesDialog.priorityLow"); //$NON-NLS-1$

    protected Combo priorityCombo;

    protected Button completedCheckbox;

    /**
     * @param parentShell
     */
    public DialogTaskProperties(Shell parentShell) {
        super(parentShell);
        setType(IMarker.TASK);
    }

    /**
     * @param parentShell
     */
    DialogTaskProperties(Shell parentShell, String title) {
        super(parentShell, title);
        setType(IMarker.TASK);
    }

    /*
     *  (non-Javadoc)
     * @see org.eclipse.ui.views.markers.internal.DialogMarkerProperties#createAttributesArea(org.eclipse.swt.widgets.Composite)
     */
    protected void createAttributesArea(Composite parent) {
        super.createAttributesArea(parent);
        Composite composite = new Composite(parent, SWT.NONE);
        composite.setFont(parent.getFont());
        GridLayout layout = new GridLayout();
        layout.numColumns = 3;
        composite.setLayout(layout);

        Label label = new Label(composite, SWT.NONE);
        label.setFont(composite.getFont());
        label.setText(Messages.getString("propertiesDialog.priority")); //$NON-NLS-1$
        priorityCombo = new Combo(composite, SWT.READ_ONLY);
        priorityCombo.setItems(new String[] { PRIORITY_HIGH, PRIORITY_NORMAL,
                PRIORITY_LOW });
        // Prevent Esc and Return from closing the dialog when the combo is active.
        priorityCombo.addTraverseListener(new TraverseListener() {
            public void keyTraversed(TraverseEvent e) {
                if (e.detail == SWT.TRAVERSE_ESCAPE
                        || e.detail == SWT.TRAVERSE_RETURN) {
                    e.doit = false;
                }
            }
        });
        priorityCombo.setFont(composite.getFont());
        priorityCombo.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                if (getMarker() == null) {
                    Map initialAttributes = getInitialAttributes();
                    initialAttributes.put(IMarker.PRIORITY, new Integer(
                            getPriorityFromDialog()));
                }
                markDirty();
            }
        });

        completedCheckbox = new Button(composite, SWT.CHECK);
        completedCheckbox.setFont(composite.getFont());
        completedCheckbox.setText(Messages
                .getString("propertiesDialog.completed")); //$NON-NLS-1$
        GridData gridData = new GridData();
        gridData.horizontalIndent = convertHorizontalDLUsToPixels(20);
        completedCheckbox.setLayoutData(gridData);
        completedCheckbox.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                if (getMarker() == null) {
                    Map initialAttributes = getInitialAttributes();
                    initialAttributes.put(IMarker.DONE, new Boolean(
                            completedCheckbox.getSelection()));
                }
                markDirty();
            }
        });
    }

    protected boolean getCompleted() {
        IMarker marker = getMarker();
        if (marker == null) {
            Map attributes = getInitialAttributes();
            Object done = attributes.get(IMarker.DONE);
            return done != null && done instanceof Boolean
                    && ((Boolean) done).booleanValue();
        } else {
            return marker.getAttribute(IMarker.DONE, false);
        }
    }

    protected int getPriority() {
        IMarker marker = getMarker();
        int priority = IMarker.PRIORITY_NORMAL;
        if (marker == null) {
            Map attributes = getInitialAttributes();
            Object priorityObj = attributes.get(IMarker.PRIORITY);
            if (priorityObj != null && priorityObj instanceof Integer) {
                priority = ((Integer) priorityObj).intValue();
            }
        } else {
            priority = marker.getAttribute(IMarker.PRIORITY,
                    IMarker.PRIORITY_NORMAL);
        }
        return priority;
    }

    /*
     *  (non-Javadoc)
     * @see org.eclipse.ui.views.markers.internal.DialogMarkerProperties#updateEnablement()
     */
    protected void updateEnablement() {
        super.updateEnablement();
        priorityCombo.setEnabled(isEditable());
        completedCheckbox.setEnabled(isEditable());
    }

    /*
     * (non-Javadoc)
     * @see org.eclipse.ui.views.markers.internal.DialogMarkerProperties#updateDialogForNewMarker()
     */
    protected void updateDialogForNewMarker() {
        Map initialAttributes = getInitialAttributes();
        int priority = getPriority();
        initialAttributes.put(IMarker.PRIORITY, new Integer(priority));
        if (priority == IMarker.PRIORITY_HIGH) {
            priorityCombo.select(priorityCombo.indexOf(PRIORITY_HIGH));
        } else if (priority == IMarker.PRIORITY_LOW) {
            priorityCombo.select(priorityCombo.indexOf(PRIORITY_LOW));
        } else {
            priorityCombo.select(priorityCombo.indexOf(PRIORITY_NORMAL));
        }
        boolean completed = getCompleted();
        initialAttributes.put(IMarker.DONE, new Boolean(completed));
        completedCheckbox.setSelection(completed);
        super.updateDialogForNewMarker();
    }

    /*
     *  (non-Javadoc)
     * @see org.eclipse.ui.views.markers.internal.DialogMarkerProperties#updateDialogFromMarker()
     */
    protected void updateDialogFromMarker() {
        Map initialAttributes = getInitialAttributes();
        int priority = getPriority();
        initialAttributes.put(IMarker.PRIORITY, new Integer(priority));
        if (priority == IMarker.PRIORITY_HIGH) {
            priorityCombo.select(priorityCombo.indexOf(PRIORITY_HIGH));
        } else if (priority == IMarker.PRIORITY_LOW) {
            priorityCombo.select(priorityCombo.indexOf(PRIORITY_LOW));
        } else {
            priorityCombo.select(priorityCombo.indexOf(PRIORITY_NORMAL));
        }
        boolean completed = getCompleted();
        initialAttributes.put(IMarker.DONE, new Boolean(completed));
        completedCheckbox.setSelection(completed);
        super.updateDialogFromMarker();
    }

    private int getPriorityFromDialog() {
        int priority = IMarker.PRIORITY_NORMAL;
        if (priorityCombo.getSelectionIndex() == priorityCombo
                .indexOf(PRIORITY_HIGH)) {
            priority = IMarker.PRIORITY_HIGH;
        } else if (priorityCombo.getSelectionIndex() == priorityCombo
                .indexOf(PRIORITY_LOW)) {
            priority = IMarker.PRIORITY_LOW;
        }
        return priority;
    }

    /*
     *  (non-Javadoc)
     * @see org.eclipse.ui.views.markers.internal.DialogMarkerProperties#getMarkerAttributes()
     */
    protected Map getMarkerAttributes() {
        Map attrs = super.getMarkerAttributes();
        attrs.put(IMarker.PRIORITY, new Integer(getPriorityFromDialog()));
        attrs.put(IMarker.DONE, new Boolean(completedCheckbox.getSelection()));
        Object userEditable = attrs.get(IMarker.USER_EDITABLE);
        if (userEditable == null || !(userEditable instanceof Boolean)) {
            attrs.put(IMarker.USER_EDITABLE, new Boolean(true));
        }
        return attrs;
    }

}
