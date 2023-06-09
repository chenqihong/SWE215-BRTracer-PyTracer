/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Sebastian Davids <sdavids@gmx.de> - Fix for bug 19346 - Dialog font
 *     should be activated and used by other components.
 *******************************************************************************/
package org.eclipse.ui.dialogs;

import java.io.File;
import java.util.ArrayList;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.ide.IDEWorkbenchMessages;
import org.eclipse.ui.internal.ide.IDEWorkbenchPlugin;
import org.eclipse.ui.internal.ide.IIDEHelpContextIds;

/**
 * The ProjectLocationSelectionDialog is the dialog used to select the name
 * and location of a project for copying.
 */
public class ProjectLocationSelectionDialog extends SelectionStatusDialog {
    // widgets
    private Text projectNameField;

    private Text locationPathField;

    private Label locationLabel;

    private IProject project;

    private Button browseButton;

    private static String PROJECT_NAME_LABEL = IDEWorkbenchMessages.ProjectLocationSelectionDialog_nameLabel;

    private static String LOCATION_LABEL = IDEWorkbenchMessages.ProjectLocationSelectionDialog_locationLabel;

    private static String BROWSE_LABEL = IDEWorkbenchMessages.ProjectLocationSelectionDialog_browseLabel;

    private static String DIRECTORY_DIALOG_LABEL = IDEWorkbenchMessages.ProjectLocationSelectionDialog_directoryLabel;

    private static String INVALID_LOCATION_MESSAGE = IDEWorkbenchMessages.ProjectLocationSelectionDialog_locationError;

    private static String PROJECT_LOCATION_SELECTION_TITLE = IDEWorkbenchMessages.ProjectLocationSelectionDialog_selectionTitle;

    // constants
    private static final int SIZING_TEXT_FIELD_WIDTH = 250;

    private boolean useDefaults = true;

    private boolean firstLocationCheck;

    /**
     * Create a ProjectLocationSelectionDialog on the supplied project parented by the parentShell.
     * @param parentShell
     * @param existingProject
     */
    public ProjectLocationSelectionDialog(Shell parentShell,
            IProject existingProject) {
        super(parentShell);
        setShellStyle(getShellStyle() | SWT.RESIZE);
        setTitle(PROJECT_LOCATION_SELECTION_TITLE);
        setStatusLineAboveButtons(true);
        this.project = existingProject;
        try {
            this.useDefaults = this.getProject().getDescription().getLocation() == null;
        } catch (CoreException exception) {
            //Leave it as the default if we get a selection.
        }
    }

    /**
     * Check the message. If it is null then continue otherwise inform the user via the
     * status value and disable the OK.
     * 
     * @param errorMsg the error message to show if it is not <code>null</code>
     */
    private void applyValidationResult(String errorMsg) {
        int code;
        boolean allowFinish = false;

        if (errorMsg == null) {
            code = IStatus.OK;
            errorMsg = ""; //$NON-NLS-1$
            allowFinish = true;
        } else if (firstLocationCheck)
            code = IStatus.OK;
        else
            code = IStatus.ERROR;

        updateStatus(new Status(code, IDEWorkbenchPlugin.getDefault()
                .getDescriptor().getUniqueIdentifier(), code, errorMsg, null));
        getOkButton().setEnabled(allowFinish);
    }

    /**
     * Check whether the entries are valid. If so return null. Otherwise
     * return a string that indicates the problem.
     */
    private String checkValid() {
        String valid = checkValidName();
        if (valid != null)
            return valid;
        return checkValidLocation();
    }

    /**
     * Check if the entry in the widget location is valid. If it is valid return null. Otherwise
     * return a string that indicates the problem.
     */
    private String checkValidLocation() {

        if (useDefaults)
            return null;
        String locationFieldContents = locationPathField.getText();
        if (locationFieldContents.equals("")) {//$NON-NLS-1$
            return (IDEWorkbenchMessages.WizardNewProjectCreationPage_projectLocationEmpty);
        }
        IPath path = new Path("");//$NON-NLS-1$
        if (!path.isValidPath(locationFieldContents)) {
            return INVALID_LOCATION_MESSAGE;
        }

        IStatus locationStatus = this.project.getWorkspace()
                .validateProjectLocation(this.project,
                        new Path(locationFieldContents));

        if (!locationStatus.isOK())
            return locationStatus.getMessage();

        return null;
    }

    /**
     * Check if the entries in the widget are valid. If they are return null otherwise
     * return a string that indicates the problem.
     */
    private String checkValidName() {

        String name = this.projectNameField.getText();
        IWorkspace workspace = getProject().getWorkspace();
        IStatus nameStatus = workspace.validateName(name, IResource.PROJECT);
        if (!nameStatus.isOK())
            return nameStatus.getMessage();
        IProject newProject = workspace.getRoot().getProject(name);
        if (newProject.exists()) {
            return NLS.bind(IDEWorkbenchMessages.CopyProjectAction_alreadyExists, name);
        }

        return null;
    }

    /**
     * The <code>ProjectLocationSelectionDialog</code> implementation of this 
     * <code>SelectionStatusDialog</code> method builds a two element list - 
     * the first element is the project name and the second one is the location.
     */
    protected void computeResult() {

        ArrayList list = new ArrayList();
        list.add(this.projectNameField.getText());
        if (useDefaults)
            list.add(Platform.getLocation().toString());
        else
            list.add(this.locationPathField.getText());
        setResult(list);
    }

    /* (non-Javadoc)
     * Method declared in Window.
     */
    protected void configureShell(Shell shell) {
        super.configureShell(shell);
        PlatformUI.getWorkbench().getHelpSystem().setHelp(shell,
                IIDEHelpContextIds.PROJECT_LOCATION_SELECTION_DIALOG);
    }

    /* (non-Javadoc)
     * Method declared on Dialog.
     */
    protected Control createDialogArea(Composite parent) {
        // page group
        Composite composite = (Composite) super.createDialogArea(parent);

        composite.setLayout(new GridLayout());
        composite.setLayoutData(new GridData(GridData.FILL_BOTH));

        createProjectNameGroup(composite);
        createProjectLocationGroup(composite);

        return composite;
    }

    /**
     * Create the listener that is used to validate the location entered by the iser
     */
    private void createLocationListener() {

        Listener listener = new Listener() {
            public void handleEvent(Event event) {
                firstLocationCheck = false;
                applyValidationResult(checkValid());
            }
        };

        this.locationPathField.addListener(SWT.Modify, listener);
    }

    /**
     * Create the listener that is used to validate the entries for the receiver
     */
    private void createNameListener() {

        Listener listener = new Listener() {
            public void handleEvent(Event event) {
                setLocationForSelection();
                applyValidationResult(checkValid());
            }
        };

        this.projectNameField.addListener(SWT.Modify, listener);
    }

    /**
     * Creates the project location specification controls.
     *
     * @param parent the parent composite
     */
    private final void createProjectLocationGroup(Composite parent) {
        Font font = parent.getFont();

        // project specification group
        Composite projectGroup = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.numColumns = 3;
        projectGroup.setLayout(layout);
        projectGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
        projectGroup.setFont(font);

        final Button useDefaultsButton = new Button(projectGroup, SWT.CHECK
                | SWT.RIGHT);
        useDefaultsButton.setText(IDEWorkbenchMessages.ProjectLocationSelectionDialog_useDefaultLabel);
        useDefaultsButton.setSelection(this.useDefaults);
        GridData buttonData = new GridData();
        buttonData.horizontalSpan = 3;
        useDefaultsButton.setLayoutData(buttonData);
        useDefaultsButton.setFont(font);

        createUserSpecifiedProjectLocationGroup(projectGroup, !this.useDefaults);

        SelectionListener listener = new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                useDefaults = useDefaultsButton.getSelection();
                browseButton.setEnabled(!useDefaults);
                locationPathField.setEnabled(!useDefaults);
                locationLabel.setEnabled(!useDefaults);
                setLocationForSelection();
                if (!useDefaults) {
                    firstLocationCheck = true;
                    applyValidationResult(checkValid());
                }
            }
        };
        useDefaultsButton.addSelectionListener(listener);
    }

    /**
     * Creates the project name specification controls.
     *
     * @param parent the parent composite
     */
    private void createProjectNameGroup(Composite parent) {
        Font font = parent.getFont();
        // project specification group
        Composite projectGroup = new Composite(parent, SWT.NONE);
        GridLayout layout = new GridLayout();
        layout.numColumns = 2;
        projectGroup.setLayout(layout);
        projectGroup.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

        // new project label
        Label projectLabel = new Label(projectGroup, SWT.NONE);
        projectLabel.setFont(font);
        projectLabel.setText(PROJECT_NAME_LABEL);

        // new project name entry field
        projectNameField = new Text(projectGroup, SWT.BORDER);
        GridData data = new GridData(GridData.FILL_HORIZONTAL);
        data.widthHint = SIZING_TEXT_FIELD_WIDTH;
        projectNameField.setLayoutData(data);
        projectNameField.setFont(font);

        // Set the initial value first before listener
        // to avoid handling an event during the creation.
        projectNameField.setText(getCopyNameFor(getProject().getName()));
        projectNameField.selectAll();

        createNameListener();

    }

    /**
     * Creates the project location specification controls.
     *
     * @return the parent of the widgets created
     * @param projectGroup the parent composite
     * @param enabled - sets the initial enabled state of the widgets
     */
    private Composite createUserSpecifiedProjectLocationGroup(
            Composite projectGroup, boolean enabled) {

        Font font = projectGroup.getFont();

        // location label
        locationLabel = new Label(projectGroup, SWT.NONE);
        locationLabel.setFont(font);
        locationLabel.setText(LOCATION_LABEL);
        locationLabel.setEnabled(enabled);

        // project location entry field
        locationPathField = new Text(projectGroup, SWT.BORDER);
        GridData data = new GridData(GridData.FILL_HORIZONTAL);
        data.widthHint = SIZING_TEXT_FIELD_WIDTH;
        locationPathField.setLayoutData(data);
        locationPathField.setFont(font);
        locationPathField.setEnabled(enabled);

        // browse button
        this.browseButton = new Button(projectGroup, SWT.PUSH);
        this.browseButton.setFont(font);
        this.browseButton.setText(BROWSE_LABEL);
        this.browseButton.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent event) {
                handleLocationBrowseButtonPressed();
            }
        });
        this.browseButton.setEnabled(enabled);
        setButtonLayoutData(this.browseButton);

        // Set the initial value first before listener
        // to avoid handling an event during the creation.
        try {
            IPath location = this.getProject().getDescription().getLocation();
            if (location == null)
                setLocationForSelection();
            else
                locationPathField.setText(location.toString());
        } catch (CoreException exception) {
            //Set it to the default if possible as there is no info yet
            setLocationForSelection();
        }

        createLocationListener();
        return projectGroup;

    }

    /**
     * Generates a new name for the project that does not have any collisions.
     */
    private String getCopyNameFor(String projectName) {

        IWorkspace workspace = getProject().getWorkspace();
        if (!workspace.getRoot().getProject(projectName).exists())
            return projectName;

        int counter = 1;
        while (true) {
            String nameSegment;
            if (counter > 1) {
                nameSegment = NLS.bind(IDEWorkbenchMessages.CopyProjectAction_copyNameTwoArgs, new Integer(counter), projectName);
            } else {
                nameSegment = NLS.bind(IDEWorkbenchMessages.CopyProjectAction_copyNameOneArg, projectName);
            }

            if (!workspace.getRoot().getProject(nameSegment).exists())
                return nameSegment;

            counter++;
        }

    }

    /**
     * Get the project being manipulated.
     */
    private IProject getProject() {
        return this.project;
    }

    /**
     *	Open an appropriate directory browser
     */
    private void handleLocationBrowseButtonPressed() {
        DirectoryDialog dialog = new DirectoryDialog(locationPathField
                .getShell());
        dialog.setMessage(DIRECTORY_DIALOG_LABEL);

        String dirName = locationPathField.getText();
        if (!dirName.equals("")) {//$NON-NLS-1$
            File path = new File(dirName);
            if (path.exists())
                dialog.setFilterPath(dirName);
        }

        String selectedDirectory = dialog.open();
        if (selectedDirectory != null)
            locationPathField.setText(selectedDirectory);
    }

    /**
     * Set the location to the default location if we are set to useDefaults.
     */
    private void setLocationForSelection() {
        if (useDefaults) {
            IPath defaultPath = Platform.getLocation().append(
                    projectNameField.getText());
            locationPathField.setText(defaultPath.toOSString());
        }
    }
}
