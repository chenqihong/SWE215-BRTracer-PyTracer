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

package org.eclipse.ui.views.markers.internal;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.plugin.AbstractUIPlugin;

public class ProblemView extends MarkerView {

    private final ColumnPixelData[] DEFAULT_COLUMN_LAYOUTS = {
            new ColumnPixelData(16, false, true), new ColumnPixelData(200),
            new ColumnPixelData(75), new ColumnPixelData(150),
            new ColumnPixelData(60) };

    // Direction constants - use the ones on TableSorter to stay sane
    private final static int ASCENDING = TableSorter.ASCENDING;

    private final static int DESCENDING = TableSorter.DESCENDING;

    private final IField[] VISIBLE_FIELDS = { new FieldSeverity(),
            new FieldMessage(), new FieldResource(), new FieldFolder(),
            new FieldLineNumber() };

    private final IField[] HIDDEN_FIELDS = { new FieldCreationTime() };

    // Field Tags
    // These tags MUST occur in the same order as the VISIBLE_FIELDS +
    // HIDDEN_FIELDS appear.  The TableSorter holds the priority and
    // direction order as a set of indices into an array of fields.  This
    // array of fields is set on instantiation of TableSorter (see method
    // getSorter() in this (i.e. ProblemView) class).  When we instantiate
    // TableSorter, we use the method TableView.getFields() as it is 
    // inherited and we don't override it.  TableView.getFields() will
    // return VISIBLE_FIELDS and then HIDDEN_FIELDS
    private final static int SEVERITY = 0;

    private final static int DESCRIPTION = 1;

    private final static int RESOURCE = 2;

    private final static int FOLDER = 3;

    private final static int LOCATION = 4;

    private final static int CREATION_TIME = 5;

    private final static int[] DEFAULT_PRIORITIES = { SEVERITY, FOLDER,
            RESOURCE, LOCATION, DESCRIPTION, CREATION_TIME };

    private final static int[] DEFAULT_DIRECTIONS = { DESCENDING, // severity
            ASCENDING, // folder
            ASCENDING, // resource
            ASCENDING, // location
            ASCENDING, // description
            ASCENDING, }; // creation time

    private final static String[] ROOT_TYPES = { IMarker.PROBLEM };

    private final static String TAG_DIALOG_SECTION = "org.eclipse.ui.views.problem"; //$NON-NLS-1$

    private ProblemFilter problemFilter = new ProblemFilter();

    private ActionResolveMarker resolveMarkerAction;

    private TableSorter sorter;

    public void dispose() {
        if (resolveMarkerAction != null)
            resolveMarkerAction.dispose();

        super.dispose();
    }

    public void init(IViewSite viewSite, IMemento memento)
            throws PartInitException {
        super.init(viewSite, memento);
        problemFilter.restoreState(getDialogSettings());
    }

    public void saveState(IMemento memento) {
        problemFilter.saveState(getDialogSettings());

        super.saveState(memento);
    }

    protected ColumnPixelData[] getDefaultColumnLayouts() {
        return DEFAULT_COLUMN_LAYOUTS;
    }

    protected IDialogSettings getDialogSettings() {
        AbstractUIPlugin plugin = (AbstractUIPlugin) Platform
                .getPlugin(PlatformUI.PLUGIN_ID);
        IDialogSettings workbenchSettings = plugin.getDialogSettings();
        IDialogSettings settings = workbenchSettings
                .getSection(TAG_DIALOG_SECTION);

        if (settings == null)
            settings = workbenchSettings.addNewSection(TAG_DIALOG_SECTION);

        return settings;
    }

    protected void createActions() {
        super.createActions();
        propertiesAction = new ActionProblemProperties(this,
                getSelectionProvider());
        resolveMarkerAction = new ActionResolveMarker(this,
                getSelectionProvider());
    }

    protected void createColumns(Table table) {
        super.createColumns(table);
        TableColumn[] columns = table.getColumns();

        if (columns != null && columns.length >= 1)
            columns[0].setResizable(false);
    }

    protected void fillContextMenuAdditions(IMenuManager manager) {
        manager.add(new Separator());
        manager.add(resolveMarkerAction);
    }

    protected DialogMarkerFilter getFiltersDialog() {
        return new DialogProblemFilter(getViewer().getControl().getShell(),
                problemFilter);
    }

    protected IField[] getHiddenFields() {
        return HIDDEN_FIELDS;
    }

    protected String[] getRootTypes() {
        return ROOT_TYPES;
    }

    protected TableSorter getSorter() {
        if (sorter == null)
            sorter = new TableSorter(getFields(), DEFAULT_PRIORITIES,
                    DEFAULT_DIRECTIONS);
        return sorter;
    }

    protected Object getViewerInput() {
        return ResourcesPlugin.getWorkspace().getRoot();
    }

    protected IField[] getVisibleFields() {
        return VISIBLE_FIELDS;
    }

    protected void initMenu(IMenuManager menu) {
        super.initMenu(menu);
    }

    void updateTitle() {
        MarkerList visibleMarkers = getVisibleMarkers();
        String breakdown = formatSummaryBreakDown(visibleMarkers);
        int filteredCount = visibleMarkers.getItemCount();
        int totalCount = getTotalMarkers();
        if (filteredCount != totalCount)
            breakdown = Messages.format("problem.filter.matchedMessage", //$NON-NLS-1$
                    new Object[] { breakdown, new Integer(filteredCount),
                            new Integer(totalCount) });
        setContentDescription(breakdown);
    }

    private String formatSummaryBreakDown(MarkerList visibleMarkers) {
        return Messages.format("problem.statusSummaryBreakdown", //$NON-NLS-1$
                new Object[] { new Integer(visibleMarkers.getErrors()),
                        new Integer(visibleMarkers.getWarnings()),
                        new Integer(visibleMarkers.getInfos()) });
    }

    private String getSummary(MarkerList markers, String messageKey) {
        String message = Messages.format(messageKey, new Object[] {
                new Integer(markers.getItemCount()),
                formatSummaryBreakDown(markers) });
        return message;
    }

    /**
     * Retrieves statistical information (the total number of markers with each
     * severity type) for the markers contained in the selection passed in.
     * This information is then massaged into a string which may be displayed
     * by the caller.
     * 
     * @param selection a valid selection or <code>null</code>
     * @return a message ready for display
     */
    protected String updateSummarySelected(IStructuredSelection selection) {
        return getSummary(new MarkerList(selection.toList()),
                "problem.statusSummarySelected"); //$NON-NLS-1$
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.markers.internal.MarkerView#getMarkerTypes()
     */
    protected String[] getMarkerTypes() {
        return new String[] { IMarker.PROBLEM };
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.markers.internal.MarkerView#getFilter()
     */
    protected MarkerFilter getFilter() {
        return problemFilter;
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.markers.internal.MarkerView#openFiltersDialog()
     */
    public void openFiltersDialog() {
        DialogProblemFilter dialog = new DialogProblemFilter(getViewer()
                .getControl().getShell(), problemFilter);

        if (dialog.open() == Window.OK) {
            problemFilter = (ProblemFilter) dialog.getFilter();
            problemFilter.saveState(getDialogSettings());
            refresh();
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.markers.internal.MarkerView#updateFilterSelection(org.eclipse.core.resources.IResource[])
     */
    protected void updateFilterSelection(IResource[] resources) {
        problemFilter.setFocusResource(resources);
    }

	protected String getStaticContextId() {
		// TODO this context is missing - add it
        return PlatformUI.PLUGIN_ID + ".problem_view_context"; //$NON-NLS-1$
	}
}
