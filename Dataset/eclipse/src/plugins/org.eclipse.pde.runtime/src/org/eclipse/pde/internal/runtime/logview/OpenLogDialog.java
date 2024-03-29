/*******************************************************************************
 * Copyright (c) 2003, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.pde.internal.runtime.logview;

import java.io.*;
import java.lang.reflect.*;
import org.eclipse.core.runtime.*;
import org.eclipse.jface.dialogs.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.operation.*;
import org.eclipse.pde.internal.runtime.*;
import org.eclipse.swt.*;
import org.eclipse.swt.graphics.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;

/**
 * Displays the error log in non-Win32 platforms - see bug 55314.
 */
public final class OpenLogDialog extends Dialog {
    // input log file
    private File logFile;
    // location/size configuration
    private IDialogSettings dialogSettings;
    private Point dialogLocation;
    private Point dialogSize;
    private int DEFAULT_WIDTH = 750;
    private int DEFAULT_HEIGHT = 800;

    public OpenLogDialog(Shell parentShell, File logFile) {
        super(parentShell);
        this.logFile = logFile;
        setShellStyle(SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MAX | SWT.MIN | SWT.MODELESS);

    }

    /*
     * (non-Javadoc) Method declared on Window.
     */
    protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText(PDERuntimeMessages.OpenLogDialog_title); //$NON-NLS-1$
        readConfiguration();
    }

    /*
     * (non-Javadoc) Method declared on Dialog.
     */
    protected void createButtonsForButtonBar(Composite parent) {
        createButton(parent, IDialogConstants.CLOSE_ID, IDialogConstants.CLOSE_LABEL,
                true);
    }

    public void create() {
        super.create();
        // dialog location
        if (dialogLocation != null)
            getShell().setLocation(dialogLocation);
        // dialog size
        if (dialogSize != null)
            getShell().setSize(dialogSize);
        else
            getShell().setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        getButton(IDialogConstants.CLOSE_ID).setFocus();
    }

    /*
     * (non-Javadoc) Method declared on Dialog.
     */
    protected Control createDialogArea(Composite parent) {
        Composite outer = (Composite) super.createDialogArea(parent);
        Text text = new Text(outer, SWT.MULTI | SWT.BORDER | SWT.READ_ONLY | SWT.V_SCROLL
                | SWT.NO_FOCUS | SWT.H_SCROLL);
        text.setBackground(parent.getDisplay().getSystemColor(SWT.COLOR_LIST_BACKGROUND));
        GridData gridData = new GridData(GridData.HORIZONTAL_ALIGN_FILL
                | GridData.VERTICAL_ALIGN_FILL);
        gridData.grabExcessVerticalSpace = true;
        gridData.grabExcessHorizontalSpace = true;
        text.setLayoutData(gridData);
        text.setText(getLogSummary());
        return outer;
    }

    private String getLogSummary() {
        StringWriter out = new StringWriter();
        PrintWriter writer = new PrintWriter(out);
        if (logFile.length() > LogReader.MAX_FILE_LENGTH) {
            readLargeFileWithMonitor(writer);
        } else {
            readFileWithMonitor(writer);
        }
        writer.close();
        return out.toString();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.eclipse.jface.dialogs.Dialog#buttonPressed(int)
     */
    protected void buttonPressed(int buttonId) {
        if (buttonId == IDialogConstants.CLOSE_ID) {
            storeSettings();
            close();
        }
        super.buttonPressed(buttonId);
    }

    //--------------- configuration handling --------------
    /**
     * Stores the current state in the dialog settings.
     * 
     * @since 2.0
     */
    private void storeSettings() {
        writeConfiguration();
    }

    /**
     * Returns the dialog settings object used to share state between several
     * event detail dialogs.
     * 
     * @return the dialog settings to be used
     */
    private IDialogSettings getDialogSettings() {
        IDialogSettings settings = PDERuntimePlugin.getDefault().getDialogSettings();
        dialogSettings = settings.getSection(getClass().getName());
        if (dialogSettings == null)
            dialogSettings = settings.addNewSection(getClass().getName());
        return dialogSettings;
    }

    /**
     * Initializes itself from the dialog settings with the same state as at the
     * previous invocation.
     */
    private void readConfiguration() {
        IDialogSettings s = getDialogSettings();
        try {
            int x = s.getInt("x"); //$NON-NLS-1$
            int y = s.getInt("y"); //$NON-NLS-1$
            dialogLocation = new Point(x, y);
            x = s.getInt("width"); //$NON-NLS-1$
            y = s.getInt("height"); //$NON-NLS-1$
            dialogSize = new Point(x, y);
        } catch (NumberFormatException e) {
            dialogLocation = null;
            dialogSize = null;
        }
    }

    private void writeConfiguration() {
        IDialogSettings s = getDialogSettings();
        Point location = getShell().getLocation();
        s.put("x", location.x); //$NON-NLS-1$
        s.put("y", location.y); //$NON-NLS-1$
        Point size = getShell().getSize();
        s.put("width", size.x); //$NON-NLS-1$
        s.put("height", size.y); //$NON-NLS-1$
    }

    // reading file within MAX_FILE_LENGTH size
    private void readFile(PrintWriter writer) throws FileNotFoundException, IOException {
        BufferedReader bReader = new BufferedReader(new FileReader(logFile));
        while (bReader.ready())
            writer.println(bReader.readLine());
    }

    // reading large files
    private void readLargeFile(PrintWriter writer) throws FileNotFoundException,
            IOException {
        RandomAccessFile random = null;
        boolean hasStarted = false;
        try {
            random = new RandomAccessFile(logFile, "r"); //$NON-NLS-1$
            random.seek(logFile.length() - LogReader.MAX_FILE_LENGTH);
            for (;;) {
                String line = random.readLine();
                if (line == null)
                    break;
                line = line.trim();
                if (line.length() == 0)
                    continue;
                if (!hasStarted
                        && (line.startsWith("!ENTRY") || line.startsWith("!SESSION"))) //$NON-NLS-1$ //$NON-NLS-2$
                    hasStarted = true;
                if (hasStarted)
                    writer.println(line);
                continue;
            }
        } finally {
            try {
                if (random != null)
                    random.close();
            } catch (IOException e1) {
            }
        }
    }

    private void readLargeFileWithMonitor(final PrintWriter writer) {
        IRunnableWithProgress runnable = new IRunnableWithProgress() {
            public void run(IProgressMonitor monitor) throws InvocationTargetException,
                    InterruptedException {
                monitor
                        .beginTask(
                                PDERuntimeMessages.OpenLogDialog_message, IProgressMonitor.UNKNOWN); //$NON-NLS-1$
                try {
                    readLargeFile(writer);
                } catch (IOException e) {
                    writer.println(PDERuntimeMessages.OpenLogDialog_cannotDisplay); //$NON-NLS-1$
                }
            }
        };
        ProgressMonitorDialog dialog = new ProgressMonitorDialog(getParentShell());
        try {
            dialog.run(true, true, runnable);
        } catch (InvocationTargetException e) {
        } catch (InterruptedException e) {
        }
    }

    private void readFileWithMonitor(final PrintWriter writer) {
        IRunnableWithProgress runnable = new IRunnableWithProgress() {
            public void run(IProgressMonitor monitor) throws InvocationTargetException,
                    InterruptedException {
                monitor
                        .beginTask(
                                PDERuntimeMessages.OpenLogDialog_message, IProgressMonitor.UNKNOWN); //$NON-NLS-1$
                try {
                    readFile(writer);
                } catch (IOException e) {
                    writer.println(PDERuntimeMessages.OpenLogDialog_cannotDisplay); //$NON-NLS-1$
                }
            }
        };
        ProgressMonitorDialog dialog = new ProgressMonitorDialog(getParentShell());
        try {
            dialog.run(true, true, runnable);
        } catch (InvocationTargetException e) {
        } catch (InterruptedException e) {
        }
    }
}
