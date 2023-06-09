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
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnPixelData;
import org.eclipse.jface.viewers.ICellModifier;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Item;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.CellEditorActionHandler;
import org.eclipse.ui.plugin.AbstractUIPlugin;

public class BookmarkView extends MarkerView {

    private final ColumnPixelData[] DEFAULT_COLUMN_LAYOUTS = {
            new ColumnPixelData(200), new ColumnPixelData(75),
            new ColumnPixelData(150), new ColumnPixelData(60) };

    private final IField[] HIDDEN_FIELDS = { new FieldCreationTime() };

    private final static String[] ROOT_TYPES = { IMarker.BOOKMARK };

    private final static String[] TABLE_COLUMN_PROPERTIES = { IMarker.MESSAGE,
            "", //$NON-NLS-1$
            "", //$NON-NLS-1$
            "" //$NON-NLS-1$
    };

    private final static String TAG_DIALOG_SECTION = "org.eclipse.ui.views.bookmark"; //$NON-NLS-1$

    private final IField[] VISIBLE_FIELDS = { new FieldMessage(),
            new FieldResource(), new FieldFolder(), new FieldLineNumber() };

    private ICellModifier cellModifier = new ICellModifier() {
        public Object getValue(Object element, String property) {
            if (element instanceof ConcreteMarker
                    && IMarker.MESSAGE.equals(property))
                return ((ConcreteMarker) element).getDescription();
            else
                return null;
        }

        public boolean canModify(Object element, String property) {
            return true;
        }

        public void modify(Object element, String property, Object value) {
            if (element instanceof Item) {
                Item item = (Item) element;
                Object data = item.getData();

                if (data instanceof ConcreteMarker) {
                    IMarker marker = ((ConcreteMarker) data).getMarker();

                    try {
                        if (!marker.getAttribute(property).equals(value)) {
                            if (IMarker.MESSAGE.equals(property))
                                marker.setAttribute(IMarker.MESSAGE, value);
                        }
                    } catch (CoreException e) {
                        ErrorDialog
                                .openError(
                                        getSite().getShell(),
                                        Messages
                                                .getString("errorModifyingBookmark"), null, e.getStatus()); //$NON-NLS-1$
                    }
                }
            }
        }
    };

    private CellEditorActionHandler cellEditorActionHandler;

    private BookmarkFilter bookmarkFilter = new BookmarkFilter();

    public void createPartControl(Composite parent) {
        super.createPartControl(parent);

        // TODO: Check for possible reliance on IMarker
        TableViewer tableViewer = getViewer();
        CellEditor cellEditors[] = new CellEditor[tableViewer.getTable()
                .getColumnCount()];
        CellEditor descriptionCellEditor = new TextCellEditor(tableViewer
                .getTable());
        cellEditors[0] = descriptionCellEditor;
        tableViewer.setCellEditors(cellEditors);
        tableViewer.setCellModifier(cellModifier);
        tableViewer.setColumnProperties(TABLE_COLUMN_PROPERTIES);

        cellEditorActionHandler = new CellEditorActionHandler(getViewSite()
                .getActionBars());
        cellEditorActionHandler.addCellEditor(descriptionCellEditor);
        cellEditorActionHandler.setCopyAction(copyAction);
        cellEditorActionHandler.setPasteAction(pasteAction);
        cellEditorActionHandler.setDeleteAction(deleteAction);
        cellEditorActionHandler.setSelectAllAction(selectAllAction);
    }

    public void dispose() {
        if (cellEditorActionHandler != null)
            cellEditorActionHandler.dispose();

        super.dispose();
    }

    public void init(IViewSite viewSite, IMemento memento)
            throws PartInitException {
        super.init(viewSite, memento);
        IDialogSettings dialogSettings = getDialogSettings();
        bookmarkFilter.restoreState(dialogSettings);
    }

    public void saveState(IMemento memento) {
        IDialogSettings dialogSettings = getDialogSettings();

        bookmarkFilter.saveState(dialogSettings);

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

    protected IField[] getHiddenFields() {
        return HIDDEN_FIELDS;
    }

    protected String[] getRootTypes() {
        return ROOT_TYPES;
    }

    protected Object getViewerInput() {
        return ResourcesPlugin.getWorkspace().getRoot();
    }

    protected IField[] getVisibleFields() {
        return VISIBLE_FIELDS;
    }

    public void setSelection(IStructuredSelection structuredSelection,
            boolean reveal) {
        // TODO: added because nick doesn't like public API inherited from internal classes
        super.setSelection(structuredSelection, reveal);
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.markers.internal.MarkerView#getMarkerTypes()
     */
    protected String[] getMarkerTypes() {
        return new String[] { IMarker.BOOKMARK };
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.markers.internal.MarkerView#openFiltersDialog()
     */
    public void openFiltersDialog() {
        DialogBookmarkFilter dialog = new DialogBookmarkFilter(getSite()
                .getShell(), bookmarkFilter);

        if (dialog.open() == Window.OK) {
            bookmarkFilter = (BookmarkFilter) dialog.getFilter();
            bookmarkFilter.saveState(getDialogSettings());
            refresh();
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.markers.internal.MarkerView#getFilter()
     */
    protected MarkerFilter getFilter() {
        return bookmarkFilter;
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.markers.internal.MarkerView#updateFilterSelection(org.eclipse.core.resources.IResource[])
     */
    protected void updateFilterSelection(IResource[] resources) {
        bookmarkFilter.setFocusResource(resources);
    }

	protected String getStaticContextId() {
        return PlatformUI.PLUGIN_ID + ".bookmark_view_context"; //$NON-NLS-1$
	}
}
