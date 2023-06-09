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
package org.eclipse.ui.internal.wizards.datatransfer;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Group;
import org.eclipse.ui.PlatformUI;


/**
 *	Page 1 of the base resource export-to-zip Wizard
 */
public class WizardZipFileResourceExportPage1 extends
        WizardFileSystemResourceExportPage1 {

    // widgets
    protected Button compressContentsCheckbox;

    // dialog store id constants
    private final static String STORE_DESTINATION_NAMES_ID = "WizardZipFileResourceExportPage1.STORE_DESTINATION_NAMES_ID"; //$NON-NLS-1$

    private final static String STORE_CREATE_STRUCTURE_ID = "WizardZipFileResourceExportPage1.STORE_CREATE_STRUCTURE_ID"; //$NON-NLS-1$

    private final static String STORE_COMPRESS_CONTENTS_ID = "WizardZipFileResourceExportPage1.STORE_COMPRESS_CONTENTS_ID"; //$NON-NLS-1$

    /**
     *	Create an instance of this class. 
     *
     *	@param name java.lang.String
     */
    protected WizardZipFileResourceExportPage1(String name,
            IStructuredSelection selection) {
        super(name, selection);
    }

    /**
     * Create an instance of this class.
     * 
     * @param selection the selection
     */
    public WizardZipFileResourceExportPage1(IStructuredSelection selection) {
        this("zipFileExportPage1", selection); //$NON-NLS-1$
        setTitle(DataTransferMessages.ZipExport_exportTitle);
        setDescription(DataTransferMessages.ZipExport_description);
    }

    /** (non-Javadoc)
     * Method declared on IDialogPage.
     */
    public void createControl(Composite parent) {
        super.createControl(parent);
        PlatformUI.getWorkbench().getHelpSystem().setHelp(getControl(),
                IDataTransferHelpContextIds.ZIP_FILE_EXPORT_WIZARD_PAGE);
    }

    /**
     *	Create the export options specification widgets.
     *
     */
    protected void createOptionsGroupButtons(Group optionsGroup) {

        Font font = optionsGroup.getFont();
        // compress... checkbox
        compressContentsCheckbox = new Button(optionsGroup, SWT.CHECK
                | SWT.LEFT);
        compressContentsCheckbox.setText(DataTransferMessages.ZipExport_compressContents);
        compressContentsCheckbox.setFont(font);

        createDirectoryStructureOptions(optionsGroup, font);

        // initial setup
        createDirectoryStructureButton.setSelection(true);
        createSelectionOnlyButton.setSelection(false);
        compressContentsCheckbox.setSelection(true);
    }

    /**
     * Returns a boolean indicating whether the directory portion of the
     * passed pathname is valid and available for use.
     */
    protected boolean ensureTargetDirectoryIsValid(String fullPathname) {
        int separatorIndex = fullPathname.lastIndexOf(File.separator);

        if (separatorIndex == -1) // ie.- default dir, which is fine
            return true;

        return ensureTargetIsValid(new File(fullPathname.substring(0,
                separatorIndex)));
    }

    /**
     * Returns a boolean indicating whether the passed File handle is
     * is valid and available for use.
     */
    protected boolean ensureTargetFileIsValid(File targetFile) {
        if (targetFile.exists() && targetFile.isDirectory()) {
            displayErrorDialog(DataTransferMessages.ZipExport_mustBeFile);
            giveFocusToDestination();
            return false;
        }

        if (targetFile.exists()) {
            if (targetFile.canWrite()) {
                if (!queryYesNoQuestion(DataTransferMessages.ZipExport_alreadyExists))
                    return false;
            } else {
                displayErrorDialog(DataTransferMessages.ZipExport_alreadyExistsError);
                giveFocusToDestination();
                return false;
            }
        }

        return true;
    }

    /**
     * Ensures that the target output file and its containing directory are
     * both valid and able to be used.  Answer a boolean indicating validity.
     */
    protected boolean ensureTargetIsValid() {
        String targetPath = getDestinationValue();

        if (!ensureTargetDirectoryIsValid(targetPath))
            return false;

        if (!ensureTargetFileIsValid(new File(targetPath)))
            return false;

        return true;
    }

    /**
     *  Export the passed resource and recursively export all of its child resources
     *  (iff it's a container).  Answer a boolean indicating success.
     */
    protected boolean executeExportOperation(ArchiveFileExportOperation op) {
        op.setCreateLeadupStructure(createDirectoryStructureButton
                .getSelection());
        op.setUseCompression(compressContentsCheckbox.getSelection());

        try {
            getContainer().run(true, true, op);
        } catch (InterruptedException e) {
            return false;
        } catch (InvocationTargetException e) {
            displayErrorDialog(e.getTargetException());
            return false;
        }

        IStatus status = op.getStatus();
        if (!status.isOK()) {
            ErrorDialog.openError(getContainer().getShell(),
                    DataTransferMessages.DataTransfer_exportProblems,
                    null, // no special message
                    status);
            return false;
        }

        return true;
    }

    /**
     * The Finish button was pressed.  Try to do the required work now and answer
     * a boolean indicating success.  If false is returned then the wizard will
     * not close.
     * @returns boolean
     */
    public boolean finish() {
        if (!ensureTargetIsValid())
            return false;

        List resourcesToExport = getWhiteCheckedResources();

        //Save dirty editors if possible but do not stop if not all are saved
        saveDirtyEditors();
        // about to invoke the operation so save our state
        saveWidgetValues();

        if (resourcesToExport.size() > 0)
            return executeExportOperation(new ArchiveFileExportOperation(null,
                    resourcesToExport, getDestinationValue()));

        MessageDialog.openInformation(getContainer().getShell(),
                DataTransferMessages.DataTransfer_information,
                DataTransferMessages.FileExport_noneSelected);

        return false;
    }

    /**
     *	Answer the string to display in the receiver as the destination type
     */
    protected String getDestinationLabel() {
        return DataTransferMessages.ZipExport_destinationLabel;
    }

    /**
     *	Answer the contents of self's destination specification widget.  If this
     *	value does not have a suffix then add it first.
     */
    protected String getDestinationValue() {
        String idealSuffix = getOutputSuffix();
        String destinationText = super.getDestinationValue();

        // only append a suffix if the destination doesn't already have a . in 
        // its last path segment.  
        // Also prevent the user from selecting a directory.  Allowing this will 
        // create a ".zip" file in the directory
        if (destinationText.length() != 0
                && !destinationText.endsWith(File.separator)) {
            int dotIndex = destinationText.lastIndexOf('.');
            if (dotIndex != -1) {
                // the last path seperator index
                int pathSepIndex = destinationText.lastIndexOf(File.separator);
                if (pathSepIndex != -1 && dotIndex < pathSepIndex) {
                    destinationText += idealSuffix;
                }
            } else {
                destinationText += idealSuffix;
            }
        }

        return destinationText;
    }

    /**
     *	Answer the suffix that files exported from this wizard should have.
     *	If this suffix is a file extension (which is typically the case)
     *	then it must include the leading period character.
     *
     */
    protected String getOutputSuffix() {
        return ".zip"; //$NON-NLS-1$
    }

    /**
     *	Open an appropriate destination browser so that the user can specify a source
     *	to import from
     */
    protected void handleDestinationBrowseButtonPressed() {
        FileDialog dialog = new FileDialog(getContainer().getShell(), SWT.SAVE);
        dialog.setFilterExtensions(new String[] { "*.zip", "*.*" }); //$NON-NLS-1$ //$NON-NLS-2$
        dialog.setText(DataTransferMessages.ZipExport_selectDestinationTitle);
        String currentSourceString = getDestinationValue();
        int lastSeparatorIndex = currentSourceString
                .lastIndexOf(File.separator);
        if (lastSeparatorIndex != -1)
            dialog.setFilterPath(currentSourceString.substring(0,
                    lastSeparatorIndex));
        String selectedFileName = dialog.open();

        if (selectedFileName != null) {
            setErrorMessage(null);
            setDestinationValue(selectedFileName);
        }
    }

    /**
     *	Hook method for saving widget values for restoration by the next instance
     *	of this class.
     */
    protected void internalSaveWidgetValues() {
        // update directory names history
        IDialogSettings settings = getDialogSettings();
        if (settings != null) {
            String[] directoryNames = settings
                    .getArray(STORE_DESTINATION_NAMES_ID);
            if (directoryNames == null)
                directoryNames = new String[0];

            directoryNames = addToHistory(directoryNames, getDestinationValue());
            settings.put(STORE_DESTINATION_NAMES_ID, directoryNames);

            settings.put(STORE_CREATE_STRUCTURE_ID,
                    createDirectoryStructureButton.getSelection());

            settings.put(STORE_COMPRESS_CONTENTS_ID, compressContentsCheckbox
                    .getSelection());
        }
    }

    /**
     *	Hook method for restoring widget values to the values that they held
     *	last time this wizard was used to completion.
     */
    protected void restoreWidgetValues() {
        IDialogSettings settings = getDialogSettings();
        if (settings != null) {
            String[] directoryNames = settings
                    .getArray(STORE_DESTINATION_NAMES_ID);
            if (directoryNames == null || directoryNames.length == 0)
                return; // ie.- no settings stored

            // destination
            setDestinationValue(directoryNames[0]);
            for (int i = 0; i < directoryNames.length; i++)
                addDestinationItem(directoryNames[i]);

            boolean setStructure = settings
                    .getBoolean(STORE_CREATE_STRUCTURE_ID);

            createDirectoryStructureButton.setSelection(setStructure);
            createSelectionOnlyButton.setSelection(!setStructure);

            compressContentsCheckbox.setSelection(settings
                    .getBoolean(STORE_COMPRESS_CONTENTS_ID));
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.wizards.datatransfer.WizardFileSystemResourceExportPage1#destinationEmptyMessage()
     */
    protected String destinationEmptyMessage() {
        return DataTransferMessages.ZipExport_destinationEmpty;
    }

}
