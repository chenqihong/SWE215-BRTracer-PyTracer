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

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IMarkerDelta;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.help.HelpSystem;
import org.eclipse.help.IContext;
import org.eclipse.help.IContextProvider;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DragSourceAdapter;
import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DragSourceListener;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.HelpEvent;
import org.eclipse.swt.events.HelpListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewSite;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.actions.ActionFactory;
import org.eclipse.ui.actions.SelectionProviderAction;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.ide.ResourceUtil;
import org.eclipse.ui.part.MarkerTransfer;
import org.eclipse.ui.progress.IWorkbenchSiteProgressService;
import org.eclipse.ui.progress.WorkbenchJob;
import org.eclipse.ui.views.navigator.ShowInNavigatorAction;
import org.eclipse.ui.views.tasklist.ITaskListResourceAdapter;

public abstract class MarkerView extends TableView {

    private static final String WAITING_FOR_WORKSPACE_CHANGES_TO_FINISH = Messages
            .getString("MarkerView.waiting_on_changes"); //$NON-NLS-1$

    private static final String SEARCHING_FOR_MARKERS = Messages
            .getString("MarkerView.searching_for_markers"); //$NON-NLS-1$

    private static final String REFRESHING_MARKER_COUNTS = Messages
            .getString("MarkerView.refreshing_counts"); //$NON-NLS-1$

    private static final String QUEUEING_VIEWER_UPDATES = Messages
            .getString("MarkerView.queueing_updates"); //$NON-NLS-1$

    private static final String FILTERING_ON_MARKER_LIMIT = Messages
            .getString("MarkerView.18"); //$NON-NLS-1$

    private static final String TAG_SELECTION = "selection"; //$NON-NLS-1$

    private static final String TAG_MARKER = "marker"; //$NON-NLS-1$

    private static final String TAG_RESOURCE = "resource"; //$NON-NLS-1$

    private static final String TAG_ID = "id"; //$NON-NLS-1$

    //A private field for keeping track of the number of markers
    //before the busy testing started
    private int preBusyMarkers = 0;

    protected IResource[] focusResources;

    private Clipboard clipboard;

    IResourceChangeListener resourceListener = new IResourceChangeListener() {
        public void resourceChanged(IResourceChangeEvent event) {
            String[] markerTypes = getMarkerTypes();

            boolean refreshNeeded = false;

            for (int idx = 0; idx < markerTypes.length; idx++) {
                IMarkerDelta[] markerDeltas = event.findMarkerDeltas(
                        markerTypes[idx], true);
                List changes = new ArrayList(markerDeltas.length);

                examineDelta(markerDeltas, changes);

                if (markerDeltas.length != changes.size()) {
                    refreshNeeded = true;
                }

                MarkerList changed = currentMarkers.findMarkers(changes);
                changed.refresh();

                change(changed.asList());
            }

            // Refresh everything if markers were added or removed
            if (refreshNeeded) {
                markerCountDirty = true;
                refresh();
            }
        }
    };
    
    private class ContextProvider implements IContextProvider {
		public int getContextChangeMask() {
			return SELECTION;
		}

		public IContext getContext(Object target) {
            String contextId = null;
            // See if there is a context registered for the current selection
            ConcreteMarker marker = (ConcreteMarker) ((IStructuredSelection) getViewer()
                    .getSelection()).getFirstElement();
            if (marker != null) {
                contextId = IDE.getMarkerHelpRegistry().getHelp(
                        marker.getMarker());
            }

            if (contextId == null) {
                contextId = getStaticContextId();
            }
            return HelpSystem.getContext(contextId);
        }

		public String getSearchExpression(Object target) {
			return null;
		}
    }
    
    private ContextProvider contextProvider = new ContextProvider();

    protected ActionCopyMarker copyAction;

    protected ActionPasteMarker pasteAction;

    protected SelectionProviderAction revealAction;

    protected SelectionProviderAction openAction;

    protected SelectionProviderAction showInNavigatorAction;

    protected SelectionProviderAction deleteAction;

    protected SelectionProviderAction selectAllAction;

    protected SelectionProviderAction propertiesAction;

    private ISelectionListener focusListener = new ISelectionListener() {
        public void selectionChanged(IWorkbenchPart part, ISelection selection) {
            MarkerView.this.focusSelectionChanged(part, selection);
        }
    };

    private MarkerList currentMarkers = new MarkerList();

    private int totalMarkers = 0;

    private boolean markerCountDirty = true;

    WorkbenchJob uiJob;

    /**
     * This job is scheduled whenever a filter or resource change occurs. It computes the new
     * set of markers and schedules a UI Job to cause the changes to be reflected in the UI.
     */

    private RestartableJob refreshJob = null;

    private void internalRefresh(IProgressMonitor monitor)
            throws InvocationTargetException, InterruptedException {
        int markerLimit = getMarkerLimit();
        monitor
                .beginTask(
                        Messages.getString("MarkerView.19"), markerLimit == -1 ? 60 : 100); //$NON-NLS-1$

        haltTableUpdates();
        IJobManager jobMan = Platform.getJobManager();
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();

        try {
            monitor.subTask(WAITING_FOR_WORKSPACE_CHANGES_TO_FINISH);

            jobMan.beginRule(root, monitor);

            if (monitor.isCanceled()) {
                return;
            }

            monitor.subTask(SEARCHING_FOR_MARKERS);
            SubProgressMonitor subMonitor = new SubProgressMonitor(monitor, 10);
            MarkerList markerList = MarkerList.compute(getFilter(), subMonitor, true);

            if (monitor.isCanceled()) {
                return;
            }
            if (markerCountDirty) {
                monitor.subTask(REFRESHING_MARKER_COUNTS);
                totalMarkers = MarkerList.compute(getMarkerTypes()).length;
                markerCountDirty = false;
            }
            
            currentMarkers = markerList;

        } catch (CoreException e) {
            throw new InvocationTargetException(e);
        } finally {
            jobMan.endRule(root);
        }

        if (monitor.isCanceled()) {
            return;
        }

        // Exit immediately if the markers have changed in the meantime.

        Collection markers = Arrays.asList(currentMarkers.toArray());

        if (markerLimit != -1) {

            monitor.subTask(FILTERING_ON_MARKER_LIMIT);
            SubProgressMonitor mon = new SubProgressMonitor(monitor, 40);

            markers = SortUtil.getFirst(markers, getSorter(), markerLimit, mon);
            if (monitor.isCanceled())
                return;
            currentMarkers = new MarkerList(markers);
        }

        monitor.subTask(QUEUEING_VIEWER_UPDATES);

        SubProgressMonitor sub = new SubProgressMonitor(monitor, 50);
        setContents(markers, sub);
        if (monitor.isCanceled())
            return;

        uiJob.schedule();
        try {
            uiJob.join();
        } catch (InterruptedException e) {
            uiJob.cancel();
            monitor.done();
        } finally {
            if (monitor.isCanceled()) {
                uiJob.cancel();
            }
        }

        monitor.done();
    }

    /**
     * Causes the view to re-sync its contents with the workspace. Note that
     * changes will be scheduled in a background job, and may not take effect
     * immediately. 
     */
    protected void refresh() {

        if (uiJob == null)
            createUIJob();

        if (refreshJob == null) {

            refreshJob = new RestartableJob(Messages.format(
                    "MarkerView.refreshTitle", new Object[] { getTitle() }),//$NON-NLS-1$
                    new IRunnableWithProgress() {
                        public void run(IProgressMonitor monitor)
                                throws InvocationTargetException,
                                InterruptedException {
                            internalRefresh(monitor);
                        }
                    }, getProgressService());
        }

        refreshJob.restart();
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.internal.tableview.TableView#init(org.eclipse.ui.IViewSite, org.eclipse.ui.IMemento)
     */
    public void init(IViewSite site, IMemento memento) throws PartInitException {
        super.init(site, memento);
        IWorkbenchSiteProgressService progressService = getProgressService();
        if (progressService != null) {
	        getProgressService().showBusyForFamily(
	                ResourcesPlugin.FAMILY_MANUAL_BUILD);
	        getProgressService().showBusyForFamily(
	                ResourcesPlugin.FAMILY_AUTO_BUILD);
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.internal.tableview.TableView#createPartControl(org.eclipse.swt.widgets.Composite)
     */
    public void createPartControl(Composite parent) {
        clipboard = new Clipboard(parent.getDisplay());

        super.createPartControl(parent);

        initDragAndDrop();

        getSite().getPage().addSelectionListener(focusListener);
        focusSelectionChanged(getSite().getPage().getActivePart(), getSite()
                .getPage().getSelection());
        ResourcesPlugin.getWorkspace().addResourceChangeListener(
                resourceListener);
        refresh();

        // Set help on the view itself
        getViewer().getControl().addHelpListener(new HelpListener() {
            /*
             *  (non-Javadoc)
             * @see org.eclipse.swt.events.HelpListener#helpRequested(org.eclipse.swt.events.HelpEvent)
             */
            public void helpRequested(HelpEvent e) {
            	IContext context = contextProvider.getContext(getViewer().getControl());
                PlatformUI.getWorkbench().getHelpSystem()
						.displayHelp(context);
            }
        });
    }
    
    public Object getAdapter(Class adaptable) {
    	if (adaptable.equals(IContextProvider.class))
    		return contextProvider;
    	return super.getAdapter(adaptable);
    }

    protected void viewerSelectionChanged(IStructuredSelection selection) {

        Object[] rawSelection = selection.toArray();

        IMarker[] markers = new IMarker[rawSelection.length];

        for (int idx = 0; idx < rawSelection.length; idx++) {
            markers[idx] = ((ConcreteMarker) rawSelection[idx]).getMarker();
        }

        setSelection(new StructuredSelection(markers));

        updateStatusMessage(selection);
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.internal.tableview.TableView#dispose()
     */
    public void dispose() {
        super.dispose();
        ResourcesPlugin.getWorkspace().removeResourceChangeListener(
                resourceListener);
        getSite().getPage().removeSelectionListener(focusListener);

        //dispose of selection provider actions (may not have been created yet if
        //createPartControls was never called)
        if (openAction != null) {
            openAction.dispose();
            copyAction.dispose();
            selectAllAction.dispose();
            deleteAction.dispose();
            revealAction.dispose();
            showInNavigatorAction.dispose();
            propertiesAction.dispose();
            clipboard.dispose();            
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.internal.tableview.TableView#createActions()
     */
    protected void createActions() {
        TableViewer viewer = getViewer();
        revealAction = new ActionRevealMarker(this, getSelectionProvider());
        openAction = new ActionOpenMarker(this, getSelectionProvider());
        copyAction = new ActionCopyMarker(this, getSelectionProvider());
        copyAction.setClipboard(clipboard);
        copyAction.setProperties(getFields());
        pasteAction = new ActionPasteMarker(this, getSelectionProvider());
        pasteAction.setClipboard(clipboard);
        pasteAction.setPastableTypes(getMarkerTypes());
        deleteAction = new ActionRemoveMarker(this, getSelectionProvider());
        selectAllAction = new ActionSelectAll(viewer);
        showInNavigatorAction = new ShowInNavigatorAction(getViewSite()
                .getPage(), getSelectionProvider());
        propertiesAction = new ActionMarkerProperties(this,
                getSelectionProvider());

        super.createActions();

        putAction(FILTERS_ACTION_ID, new FiltersAction(this));
    }

    protected abstract String[] getMarkerTypes();

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.internal.tableview.TableView#initToolBar(org.eclipse.jface.action.IToolBarManager)
     */
    protected void initToolBar(IToolBarManager tbm) {
        tbm.add(deleteAction);
        tbm.add(getAction(TableView.FILTERS_ACTION_ID));
        tbm.update(false);
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.internal.tableview.TableView#registerGlobalActions(org.eclipse.ui.IActionBars)
     */
    protected void registerGlobalActions(IActionBars actionBars) {
        actionBars.setGlobalActionHandler(ActionFactory.COPY.getId(),
                copyAction);
        actionBars.setGlobalActionHandler(ActionFactory.PASTE.getId(),
                pasteAction);
        actionBars.setGlobalActionHandler(ActionFactory.DELETE.getId(),
                deleteAction);
        actionBars.setGlobalActionHandler(ActionFactory.SELECT_ALL.getId(),
                selectAllAction);
        actionBars.setGlobalActionHandler(ActionFactory.PROPERTIES.getId(),
                propertiesAction);
    }

    protected void initDragAndDrop() {
        int operations = DND.DROP_COPY;
        Transfer[] transferTypes = new Transfer[] {
                MarkerTransfer.getInstance(), TextTransfer.getInstance() };
        DragSourceListener listener = new DragSourceAdapter() {
            public void dragSetData(DragSourceEvent event) {
                performDragSetData(event);
            }

            public void dragFinished(DragSourceEvent event) {
            }
        };

        getViewer().addDragSupport(operations, transferTypes, listener);
    }

    /**
     * The user is attempting to drag marker data.  Add the appropriate
     * data to the event depending on the transfer type.
     */
    private void performDragSetData(DragSourceEvent event) {
        if (MarkerTransfer.getInstance().isSupportedType(event.dataType)) {
            event.data = ((IStructuredSelection)getSelectionProvider().getSelection())
                    .toArray();
            return;
        }
        if (TextTransfer.getInstance().isSupportedType(event.dataType)) {
            List selection = ((IStructuredSelection)getSelectionProvider().getSelection())
                    .toList();
            try {
                IMarker[] markers = new IMarker[selection.size()];
                selection.toArray(markers);
                if (markers != null) {
                    event.data = copyAction.createMarkerReport(markers);
                }
            } catch (ArrayStoreException e) {
            }
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.internal.tableview.TableView#fillContextMenu(org.eclipse.jface.action.IMenuManager)
     */
    protected void fillContextMenu(IMenuManager manager) {
        if (manager == null)
            return;
        manager.add(openAction);
        manager.add(showInNavigatorAction);
        manager.add(new Separator());
        manager.add(copyAction);
        pasteAction.updateEnablement();
        manager.add(pasteAction);
        manager.add(deleteAction);
        manager.add(selectAllAction);
        fillContextMenuAdditions(manager);
        manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
        manager.add(new Separator());
        manager.add(propertiesAction);
    }

    protected void fillContextMenuAdditions(IMenuManager manager) {
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.internal.tableview.TableView#getFilter()
     */
    protected abstract MarkerFilter getFilter();

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.internal.tableview.TableView#handleKeyPressed(org.eclipse.swt.events.KeyEvent)
     */
    protected void handleKeyPressed(KeyEvent event) {
        if (event.character == SWT.DEL && event.stateMask == 0
                && deleteAction.isEnabled()) {
            deleteAction.run();
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.internal.tableview.TableView#handleOpenEvent(org.eclipse.jface.viewers.OpenEvent)
     */
    protected void handleOpenEvent(OpenEvent event) {
        openAction.run();
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.internal.tableview.TableView#saveSelection(org.eclipse.ui.IMemento)
     */
    protected void saveSelection(IMemento memento) {
        IStructuredSelection selection = (IStructuredSelection) getViewer()
                .getSelection();
        IMemento selectionMem = memento.createChild(TAG_SELECTION);
        for (Iterator iterator = selection.iterator(); iterator.hasNext();) {
            ConcreteMarker marker = (ConcreteMarker) iterator.next();
            IMemento elementMem = selectionMem.createChild(TAG_MARKER);
            elementMem.putString(TAG_RESOURCE, marker.getMarker().getResource()
                    .getFullPath().toString());
            elementMem.putString(TAG_ID, String.valueOf(marker.getMarker()
                    .getId()));
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.internal.tableview.TableView#restoreSelection(org.eclipse.ui.IMemento)
     */
    protected IStructuredSelection restoreSelection(IMemento memento) {
        if (memento == null) {
            return new StructuredSelection();
        }
        IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
        IMemento selectionMemento = memento.getChild(TAG_SELECTION);
        if (selectionMemento == null) {
            return new StructuredSelection();
        }
        ArrayList selectionList = new ArrayList();
        IMemento[] markerMems = selectionMemento.getChildren(TAG_MARKER);
        for (int i = 0; i < markerMems.length; i++) {
            try {
                long id = new Long(markerMems[i].getString(TAG_ID)).longValue();
                IResource resource = root.findMember(markerMems[i]
                        .getString(TAG_RESOURCE));
                if (resource != null) {
                    IMarker marker = resource.findMarker(id);
                    if (marker != null)
                        selectionList.add(currentMarkers.getMarker(marker));
                }
            } catch (CoreException e) {
            }
        }
        return new StructuredSelection(selectionList);
    }

    protected abstract String[] getRootTypes();

    /**
     * @param part
     * @param selection
     */
    protected void focusSelectionChanged(IWorkbenchPart part,
            ISelection selection) {

        List resources = new ArrayList();
        if (part instanceof IEditorPart) {
            IEditorPart editor = (IEditorPart) part;
            IFile file = ResourceUtil.getFile(editor.getEditorInput());
            if (file != null) {
                resources.add(file);
            }
        } else {
            if (selection instanceof IStructuredSelection) {
                for (Iterator iterator = ((IStructuredSelection) selection)
                        .iterator(); iterator.hasNext();) {
                    Object object = iterator.next();
                    if (object instanceof IAdaptable) {
                        ITaskListResourceAdapter taskListResourceAdapter;
                        Object adapter = ((IAdaptable) object)
                                .getAdapter(ITaskListResourceAdapter.class);
                        if (adapter != null
                                && adapter instanceof ITaskListResourceAdapter) {
                            taskListResourceAdapter = (ITaskListResourceAdapter) adapter;
                        } else {
                            taskListResourceAdapter = DefaultMarkerResourceAdapter
                                    .getDefault();
                        }

                        IResource resource = taskListResourceAdapter
                                .getAffectedResource((IAdaptable) object);
                        if (resource != null) {
                            resources.add(resource);
                        }
                    }
                }
            }
        }

        IResource[] focus = new IResource[resources.size()];
        resources.toArray(focus);
        updateFocusResource(focus);
    }

    /**
     * 
     * @param resources
     */
    protected abstract void updateFilterSelection(IResource[] resources);
    
    protected abstract String getStaticContextId();

    void updateFocusResource(IResource[] resources) {
        boolean updateNeeded = updateNeeded(focusResources, resources);
        if (updateNeeded) {
            focusResources = resources;
            updateFilterSelection(resources);
            refresh();
        }
    }

    private boolean updateNeeded(IResource[] oldResources,
            IResource[] newResources) {
        //determine if an update if refiltering is required
        MarkerFilter filter = getFilter();
        if (!filter.isEnabled()) {
            return false;
        }

        int onResource = filter.getOnResource();
        if (onResource == MarkerFilter.ON_ANY_RESOURCE
                || onResource == MarkerFilter.ON_WORKING_SET) {
            return false;
        }
        if (newResources == null || newResources.length < 1) {
            return false;
        }
        if (oldResources == null || oldResources.length < 1) {
            return true;
        }
        if (Arrays.equals(oldResources, newResources)) {
            return false;
        }
        if (onResource == MarkerFilter.ON_ANY_RESOURCE_OF_SAME_PROJECT) {
            Collection oldProjects = MarkerFilter
                    .getProjectsAsCollection(oldResources);
            Collection newProjects = MarkerFilter
                    .getProjectsAsCollection(newResources);

            if (oldProjects.size() == newProjects.size()) {
                return !newProjects.containsAll(oldProjects);
            } else {
                return true;
            }
        }

        return true;
    }

    /**
     * Returns the marker limit or -1 if unlimited
     *  
     * @return
     */
    private int getMarkerLimit() {
        MarkerFilter filter = getFilter();

        if (!filter.isEnabled() || !filter.getFilterOnMarkerLimit()) {
            return -1;
        }

        return filter.getMarkerLimit();
    }

    private boolean withinMarkerLimit(int toTest) {
        int limit = getMarkerLimit();

        return (limit == -1 || toTest <= limit);
    }

    void updateTitle() {
        String status = ""; //$NON-NLS-1$
        int filteredCount = currentMarkers.getItemCount();
        int totalCount = getTotalMarkers();
        if (filteredCount == totalCount) {
            status = Messages
                    .format(
                            "filter.itemsMessage", new Object[] { new Integer(totalCount) }); //$NON-NLS-1$
        } else {
            status = Messages
                    .format(
                            "filter.matchedMessage", new Object[] { new Integer(filteredCount), new Integer(totalCount) }); //$NON-NLS-1$
        }
        setContentDescription(status);
    }

    /**
     * Updates the message displayed in the status line.  This method is
     * invoked in the following cases:
     * <ul>
     * <li>when this view is first created</li>
     * <li>when new elements are added</li>
     * <li>when something is deleted</li>
     * <li>when the filters change</li>
     * </ul>
     * <p>
     * By default, this method calls <code>updateStatusMessage(IStructuredSelection)</code>
     * with the current selection or <code>null</code>.  Classes wishing to override
     * this functionality, should just override the method
     * <code>updateStatusMessage(IStructuredSelection)</code>.
     * </p>
     */
    protected void updateStatusMessage() {
        ISelection selection = getViewer().getSelection();

        if (selection instanceof IStructuredSelection)
            updateStatusMessage((IStructuredSelection) selection);
        else
            updateStatusMessage(null);
    }

    /**
     * Updates that message displayed in the status line.  If the
     * selection parameter is <code>null</code> or its size is 0, the status 
     * area is blanked out.  If only 1 marker is selected, the
     * status area is updated with the contents of the message
     * attribute of this marker.  In other cases (more than one marker
     * is selected) the status area indicates how many items have
     * been selected.
     * <p>
     * This method may be overwritten.
     * </p><p>
     * This method is called whenever a selection changes in this view.
     * </p>
     * @param selection a valid selection or <code>null</code>
     */
    protected void updateStatusMessage(IStructuredSelection selection) {
        String message = ""; //$NON-NLS-1$

        if (selection == null || selection.size() == 0) {
            // Show stats on all items in the view
            message = updateSummaryVisible();
        } else if (selection.size() == 1) {
            // Use the Message attribute of the marker
            ConcreteMarker marker = (ConcreteMarker) selection
                    .getFirstElement();
            message = marker.getDescription(); //$NON-NLS-1$
        } else if (selection.size() > 1) {
            // Show stats on only those items in the selection
            message = updateSummarySelected(selection);
        }
        getViewSite().getActionBars().getStatusLineManager()
                .setMessage(message);
    }

    /**
     * @param selection
     * @return the summary status message
     */
    protected String updateSummarySelected(IStructuredSelection selection) {
        // Show how many items selected
        return Messages
                .format(
                        "marker.statusSummarySelected", new Object[] { new Integer(selection.size()), "" }); //$NON-NLS-1$ //$NON-NLS-2$
    }

    /**
     * @return the update summary 
     */
    protected String updateSummaryVisible() {
        return ""; //$NON-NLS-1$
    }

    public abstract void openFiltersDialog();

    /**
     * Given a selection of IMarker, reveals the corresponding elements in the viewer
     * 
     * @param structuredSelection
     * @param reveal
     */
    public void setSelection(IStructuredSelection structuredSelection,
            boolean reveal) {
        TableViewer viewer = getViewer();

        List newSelection = new ArrayList(structuredSelection.size());

        for (Iterator i = structuredSelection.iterator(); i.hasNext();) {
            Object next = i.next();
            if (next instanceof IMarker) {
                ConcreteMarker marker = currentMarkers
                        .getMarker((IMarker) next);
                if (marker != null) {
                    newSelection.add(marker);
                }
            }
        }

        if (viewer != null)
            viewer.setSelection(new StructuredSelection(newSelection), reveal);
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.markers.internal.TableView#setContents(java.util.Collection)
     */
    void setContents(Collection contents, IProgressMonitor mon) {
        if (withinMarkerLimit(contents.size())) {
            super.setContents(contents, mon);
        } else {
            super.setContents(Collections.EMPTY_LIST, mon);
        }
    }

    protected MarkerList getVisibleMarkers() {
        return currentMarkers;
    }

    /**
     * Returns the total number of markers. Should not be called while the marker
     * list is still updating.
     * 
     * @return the total number of markers in the workspace (including everything that doesn't pass the filters)
     */
    int getTotalMarkers() {
        // The number of visible markers should never exceed the total number of markers in
        // the workspace. If this assertation fails, it probably indicates some sort of concurrency problem
        // (most likely, getTotalMarkers was called while we were still computing the marker lists)
        //Assert.isTrue(totalMarkers >= currentMarkers.getItemCount());

        return totalMarkers;
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.views.markers.internal.TableView#sorterChanged()
     */
    protected void sorterChanged() {
        refresh();
    }

    private static void examineDelta(IMarkerDelta[] deltas, List changes) {
        for (int idx = 0; idx < deltas.length; idx++) {
            IMarkerDelta delta = deltas[idx];
            int kind = delta.getKind();

            if (kind == IResourceDelta.CHANGED) {
                changes.add(deltas[idx].getMarker());
            }
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.ui.part.WorkbenchPart#showBusy(boolean)
     */
    public void showBusy(boolean busy) {
        super.showBusy(busy);

        if (busy) {
            preBusyMarkers = totalMarkers;
        } else {//Only bold if there has been a change in count
            if (totalMarkers != preBusyMarkers)
                getProgressService().warnOfContentChange();
        }

    }

    /**
     * Create the UIJob used in the receiver for updates.
     *
     */
    private void createUIJob() {
        uiJob = new WorkbenchJob(Messages
                .getString("MarkerView.refreshProgress")) { //$NON-NLS-1$

            public IStatus runInUIThread(IProgressMonitor monitor) {
                // Ensure that the view hasn't been disposed
                Table table = getTable();
                
                if (table != null && !table.isDisposed()) {
                    updateStatusMessage();
                    updateTitle();
                }
                return Status.OK_STATUS;
            }
        };
        uiJob.setPriority(Job.INTERACTIVE);
        uiJob.setSystem(true);
    }

}
