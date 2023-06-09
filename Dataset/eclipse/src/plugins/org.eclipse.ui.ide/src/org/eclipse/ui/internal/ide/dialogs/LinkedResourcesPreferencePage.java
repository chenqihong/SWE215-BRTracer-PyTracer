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

package org.eclipse.ui.internal.ide.dialogs;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Preferences;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.PreferencePage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.internal.ide.IDEWorkbenchMessages;
import org.eclipse.ui.internal.ide.IIDEHelpContextIds;

/**
 * Preference page for linked resources. 
 * This preference page allows enabling and disabling the workbench linked 
 * resource support.
 * It also shows all path variables currently defined in the workspace's path 
 * variable manager. The user may add, edit and remove path variables. 
 *  
 * @see org.eclipse.ui.internal.ide.dialogs.PathVariableDialog
 */
public class LinkedResourcesPreferencePage extends PreferencePage implements
        IWorkbenchPreferencePage {
    private Label topLabel;

    private PathVariablesGroup pathVariablesGroup;

    /**
     * Constructs a preference page of path variables.
     * Omits "Restore Defaults"/"Apply Changes" buttons.
     */
    public LinkedResourcesPreferencePage() {
        pathVariablesGroup = new PathVariablesGroup(true, IResource.FILE
                | IResource.FOLDER);

        this.noDefaultAndApplyButton();
    }

    /**
     * Resets this page's internal state and creates its UI contents.
     * 
     * @see PreferencePage#createContents(org.eclipse.swt.widgets.Composite)
     */
    protected Control createContents(Composite parent) {
        Font font = parent.getFont();

        PlatformUI.getWorkbench().getHelpSystem().setHelp(parent,
                IIDEHelpContextIds.LINKED_RESOURCE_PREFERENCE_PAGE);
        // define container & its gridding
        Composite pageComponent = new Composite(parent, SWT.NULL);
        GridLayout layout = new GridLayout();
        layout.marginWidth = 0;
        layout.marginHeight = 0;
        pageComponent.setLayout(layout);
        GridData data = new GridData();
        data.verticalAlignment = GridData.FILL;
        data.horizontalAlignment = GridData.FILL;
        pageComponent.setLayoutData(data);
        pageComponent.setFont(font);

        final Button enableLinkedResourcesButton = new Button(pageComponent,
                SWT.CHECK);
        enableLinkedResourcesButton.setText(IDEWorkbenchMessages.LinkedResourcesPreference_enableLinkedResources);
        enableLinkedResourcesButton.setFont(font);
        enableLinkedResourcesButton
                .addSelectionListener(new SelectionAdapter() {
                    public void widgetSelected(SelectionEvent e) {
                        boolean enabled = enableLinkedResourcesButton
                                .getSelection();
                        Preferences preferences = ResourcesPlugin.getPlugin()
                                .getPluginPreferences();
                        preferences.setValue(
                                ResourcesPlugin.PREF_DISABLE_LINKING, !enabled);

                        updateWidgetState(enabled);
                        if (enabled) {
                            MessageDialog
                                    .openWarning(
                                            getShell(),
                                            IDEWorkbenchMessages.LinkedResourcesPreference_linkedResourcesWarningTitle,
                                            IDEWorkbenchMessages.LinkedResourcesPreference_linkedResourcesWarningMessage);
                        }
                    }
                });

        createSpace(pageComponent);

        topLabel = new Label(pageComponent, SWT.NONE);
        topLabel.setText(IDEWorkbenchMessages.LinkedResourcesPreference_explanation);
        data = new GridData();
        data.verticalAlignment = GridData.FILL;
        data.horizontalAlignment = GridData.FILL;
        topLabel.setLayoutData(data);
        topLabel.setFont(font);

        pathVariablesGroup.createContents(pageComponent);

        Preferences preferences = ResourcesPlugin.getPlugin()
                .getPluginPreferences();
        boolean enableLinking = !preferences
                .getBoolean(ResourcesPlugin.PREF_DISABLE_LINKING);
        enableLinkedResourcesButton.setSelection(enableLinking);
        updateWidgetState(enableLinking);
        return pageComponent;
    }

    /**
     * Creates a tab of one horizontal spans.
     *
     * @param parent  the parent in which the tab should be created
     */
    protected static void createSpace(Composite parent) {
        Label vfiller = new Label(parent, SWT.LEFT);
        GridData gridData = new GridData();
        gridData = new GridData();
        gridData.horizontalAlignment = GridData.BEGINNING;
        gridData.grabExcessHorizontalSpace = false;
        gridData.verticalAlignment = GridData.CENTER;
        gridData.grabExcessVerticalSpace = false;
        vfiller.setLayoutData(gridData);
    }

    /**
     * Disposes the path variables group.
     * @see org.eclipse.jface.dialogs.IDialogPage#dispose()
     */
    public void dispose() {
        pathVariablesGroup.dispose();
        super.dispose();
    }

    /**
     * Empty implementation. This page does not use the workbench.
     * 
     * @see IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
     */
    public void init(IWorkbench workbench) {
    }

    /**
     * Commits the temporary state to the path variable manager in response to user
     * confirmation.
     * 
     * @see PreferencePage#performOk()
     * @see PathVariablesGroup#performOk()
     */
    public boolean performOk() {
        return pathVariablesGroup.performOk();
    }

    /**
     * Set the widget enabled state
     * 
     * @param enableLinking the new widget enabled state
     */
    protected void updateWidgetState(boolean enableLinking) {
        topLabel.setEnabled(enableLinking);
        pathVariablesGroup.setEnabled(enableLinking);
    }
}
