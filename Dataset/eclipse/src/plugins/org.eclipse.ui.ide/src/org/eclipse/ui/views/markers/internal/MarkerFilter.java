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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.internal.WorkbenchPlugin;

public class MarkerFilter {

	private static final String TAG_DIALOG_SECTION = "filter"; //$NON-NLS-1$

    private static final String TAG_ENABLED = "enabled"; //$NON-NLS-1$

    private static final String TAG_FILTER_ON_MARKER_LIMIT = "filterOnMarkerLimit"; //$NON-NLS-1$

    private static final String TAG_MARKER_LIMIT = "markerLimit"; //$NON-NLS-1$

    private static final String TAG_ON_RESOURCE = "onResource"; //$NON-NLS-1$

    private static final String TAG_SELECTED_TYPES = "selectedType"; //$NON-NLS-1$

    private static final String TAG_WORKING_SET = "workingSet"; //$NON-NLS-1$

    private static final String TAG_TYPES_DELIMITER = ":"; //$NON-NLS-1$
    
    /**
     * New attribute to handle the selection status of marker types.
     */
    private static final String TAG_SELECTION_STATUS = "selectionStatus"; //$NON-NLS-1$

    /**
     * Attribute status true.
     */
    private static final String SELECTED_FALSE = "false"; //$NON-NLS-1$

    /**
     * Attribute status false.
     */
	private static final String SELECTED_TRUE = "true"; //$NON-NLS-1$

    static final int ON_ANY_RESOURCE = 0;

    static final int ON_SELECTED_RESOURCE_ONLY = 1;

    static final int ON_SELECTED_RESOURCE_AND_CHILDREN = 2;

    static final int ON_ANY_RESOURCE_OF_SAME_PROJECT = 3;

    static final int ON_WORKING_SET = 4;

    static final int DEFAULT_MARKER_LIMIT = 100;

    static final boolean DEFAULT_FILTER_ON_MARKER_LIMIT = true;

    static final int DEFAULT_ON_RESOURCE = ON_ANY_RESOURCE;

    static final boolean DEFAULT_ACTIVATION_STATUS = true;

    protected List rootTypes = new ArrayList();

    protected List selectedTypes = new ArrayList();
    
    protected IWorkingSet workingSet;

    protected int onResource;

    protected boolean filterOnMarkerLimit;

    protected boolean enabled;

    protected int markerLimit;

    private MarkerTypesModel typesModel;

    private IResource[] focusResource;

    private Set cachedWorkingSet;

    MarkerFilter(String[] rootTypes) {
        typesModel = new MarkerTypesModel();

        for (int i = 0; i < rootTypes.length; i++) {
            MarkerType type = typesModel.getType(rootTypes[i]);

            if (!this.rootTypes.contains(type))
                this.rootTypes.add(type);
        }
    }

    /**
     * List all types known to this MarkerFilter.
     * 
     * @param types list to be filled in with types
     */
    public void addAllSubTypes(List types) {
        for (int i = 0; i < rootTypes.size(); i++) {
            MarkerType rootType = (MarkerType) rootTypes.get(i);
            addAllSubTypes(types, rootType);
        }
    }

    private void addAllSubTypes(List types, MarkerType type) {
        if (type == null)
            return;

        if (!types.contains(type))
        	types.add(type);

        MarkerType[] subTypes = type.getSubtypes();

        for (int i = 0; i < subTypes.length; i++)
            addAllSubTypes(types, subTypes[i]);
    }

    /**
     * Adds all markers in the given set of resources to the given list
     * 
     * @param resultList
     * @param resources
     * @param markerTypeId
     * @param depth
     * @throws CoreException
     */
    private List findMarkers(IResource[] resources, int depth, int limit,
            IProgressMonitor mon, boolean ignoreExceptions)
            throws CoreException {
        if (resources == null) {
            return Collections.EMPTY_LIST;
        }

        List resultList = new ArrayList(resources.length * 2);

        // Optimization: if a type appears in the selectedTypes list along with all of its
        // subtypes, then combine these in a single search.

        // List of types that haven't been replaced by one of their supertypes
        HashSet typesToSearch = new HashSet(selectedTypes.size());

        // List of types that appeared in selectedTypes along with all of their subtypes
        HashSet includeAllSubtypes = new HashSet(selectedTypes.size());

        typesToSearch.addAll(selectedTypes);

        Iterator iter = selectedTypes.iterator();

        while (iter.hasNext()) {
            MarkerType type = (MarkerType) iter.next();

            Collection subtypes = Arrays.asList(type.getAllSubTypes());

            if (selectedTypes.containsAll(subtypes)) {
                typesToSearch.removeAll(subtypes);

                includeAllSubtypes.add(type);
            }
        }

        mon
                .beginTask(
                        Messages.getString("MarkerFilter.searching"), typesToSearch.size() * resources.length); //$NON-NLS-1$

        // Use this hash set to determine if there are any resources in the
        // list that appear along with their parent.
        HashSet resourcesToSearch = new HashSet();

        // Insert all the resources into the hashset
        for (int idx = 0; idx < resources.length; idx++) {
            IResource next = resources[idx];

            if (!next.exists()) {
                continue;
            }

            if (resourcesToSearch.contains(next)) {
                mon.worked(typesToSearch.size());
            } else {
                resourcesToSearch.add(next);
            }
        }

        // Iterate through all the selected resources
        for (int resourceIdx = 0; resourceIdx < resources.length; resourceIdx++) {
            iter = typesToSearch.iterator();

            IResource resource = resources[resourceIdx];

            // Skip resources that don't exist
            if (!resource.isAccessible()) {
                continue;
            }

            if (depth == IResource.DEPTH_INFINITE) {
                // Determine if any parent of this resource is also in our filter 
                IResource parent = resource.getParent();
                boolean found = false;
                while (parent != null) {
                    if (resourcesToSearch.contains(parent)) {
                        found = true;
                    }

                    parent = parent.getParent();
                }

                // If a parent of this resource is also in the filter, we can skip it
                // because we'll pick up its markers when we search the parent.
                if (found) {
                    continue;
                }
            }

            // Iterate through all the marker types
            while (iter.hasNext()) {
                MarkerType markerType = (MarkerType) iter.next();

                // Only search for subtypes of the marker if we found all of its
                // subtypes in the filter criteria.
                IMarker[] markers = resource.findMarkers(markerType.getId(),
                        includeAllSubtypes.contains(markerType), depth);

                mon.worked(1);

                for (int idx = 0; idx < markers.length; idx++) {
                    ConcreteMarker marker;
                    try {
                        marker = MarkerList.createMarker(markers[idx]);
                    } catch (CoreException e) {
                        if (ignoreExceptions) {
                            continue;
                        } else {
                            throw e;
                        }
                    }

                    if (limit != -1 && resultList.size() >= limit) {
                        return resultList;
                    }

                    if (selectMarker(marker)) {
                        resultList.add(marker);
                    }
                }
            }
        }

        mon.done();

        return resultList;
    }

    /**
     * Subclasses should override to determine if the given marker passes the filter.
     * 
     * @param marker
     * @return <code>true</code> if the marker passes the filter and <code>false</code> otherwise
     */
    protected boolean selectMarker(ConcreteMarker marker) {
        return true;
    }

    /**
     * Searches the workspace for markers that pass this filter.
     * 
     * @return
     */
    ConcreteMarker[] findMarkers(IProgressMonitor mon, boolean ignoreExceptions)
            throws CoreException {

        List unfiltered = Collections.EMPTY_LIST;

        if (!isEnabled()) {
            unfiltered = findMarkers(new IResource[] { ResourcesPlugin
                    .getWorkspace().getRoot() }, IResource.DEPTH_INFINITE, -1,
                    mon, ignoreExceptions);
        } else {
            //int limit = getFilterOnMarkerLimit() ? getMarkerLimit() + 1 : -1;
            int limit = -1;

            switch (getOnResource()) {
            case ON_ANY_RESOURCE: {
                unfiltered = findMarkers(new IResource[] { ResourcesPlugin
                        .getWorkspace().getRoot() }, IResource.DEPTH_INFINITE,
                        limit, mon, ignoreExceptions);
                break;
            }
            case ON_SELECTED_RESOURCE_ONLY: {
                unfiltered = findMarkers(focusResource, IResource.DEPTH_ZERO,
                        limit, mon, ignoreExceptions);
                break;
            }
            case ON_SELECTED_RESOURCE_AND_CHILDREN: {
                unfiltered = findMarkers(focusResource,
                        IResource.DEPTH_INFINITE, limit, mon, ignoreExceptions);
                break;
            }
            case ON_ANY_RESOURCE_OF_SAME_PROJECT: {
                unfiltered = findMarkers(getProjects(focusResource),
                        IResource.DEPTH_INFINITE, limit, mon, ignoreExceptions);
                break;
            }
            case ON_WORKING_SET: {
                unfiltered = findMarkers(getResourcesInWorkingSet(),
                        IResource.DEPTH_INFINITE, limit, mon, ignoreExceptions);
            }
            }
        }

        if (unfiltered == null) {
            unfiltered = Collections.EMPTY_LIST;
        }

        return (ConcreteMarker[]) unfiltered
                .toArray(new ConcreteMarker[unfiltered.size()]);
    }

    IResource[] getResourcesInWorkingSet() {
        if (workingSet == null) {
            return new IResource[0];
        }

        IAdaptable[] elements = workingSet.getElements();
        List result = new ArrayList(elements.length);

        for (int idx = 0; idx < elements.length; idx++) {
            IResource next = (IResource) elements[idx]
                    .getAdapter(IResource.class);

            if (next != null) {
                result.add(next);
            }
        }

        return (IResource[]) result.toArray(new IResource[result.size()]);
    }

    /**
     * Returns a set of strings representing the full pathnames to every resource directly
     * or indirectly contained in the working set. A resource is in the working set iff its
     * path name can be found in this set.
     * 
     * @return
     */
    private Set getWorkingSetAsSetOfPaths() {
        if (cachedWorkingSet == null) {
            HashSet result = new HashSet();

            addResourcesAndChildren(result, getResourcesInWorkingSet());

            cachedWorkingSet = result;
        }

        return cachedWorkingSet;
    }

    /***
     * Adds the paths of all resources in the given array to the given set. 
     */
    private void addResourcesAndChildren(HashSet result, IResource[] resources) {
        for (int idx = 0; idx < resources.length; idx++) {

            IResource currentResource = resources[idx];

            result.add(currentResource.getFullPath().toString());

            if (currentResource instanceof IContainer) {
                IContainer cont = (IContainer) currentResource;

                try {
                    addResourcesAndChildren(result, cont.members());
                } catch (CoreException e) {
                    // Ignore errors
                }
            }

        }
    }

    /**
     * Returns the set of projects that contain the given set of resources.
     * 
     * @param resources
     * @return
     */
    static IProject[] getProjects(IResource[] resources) {
        if (resources == null) {
            return new IProject[0];
        }

        Collection projects = getProjectsAsCollection(resources);

        return (IProject[]) projects.toArray(new IProject[projects.size()]);
    }

    static Collection getProjectsAsCollection(IResource[] resources) {
        HashSet projects = new HashSet();

        for (int idx = 0; idx < resources.length; idx++) {
            projects.add(resources[idx].getProject());
        }

        return projects;
    }

    public boolean select(ConcreteMarker marker) {
        if (!isEnabled()) {
            return true;
        }

        return selectByType(marker) && selectBySelection(marker)
                && selectMarker(marker);
    }

    private boolean selectByType(ConcreteMarker marker) {
        return selectedTypes.contains(typesModel.getType(marker.getType()));
    }

    /**
     * Returns whether the specified marker should be filter out or not.
     * 
     * @param marker the marker to test
     * @return 
     * 	true=the marker should not be filtered out
     * 	false=the marker should be filtered out
     */
    private boolean selectBySelection(ConcreteMarker marker) {
        if (onResource == ON_ANY_RESOURCE || marker == null)
            return true;

        if (focusResource == null)
            return true;

        IResource resource = marker.getResource();

        if (onResource == ON_WORKING_SET) {
            if (workingSet == null)
                return true;

            if (resource != null)
                return isEnclosed(resource);

        } else if (onResource == ON_ANY_RESOURCE_OF_SAME_PROJECT) {
            IProject project = resource.getProject();

            if (project == null) {
                return false;
            }

            for (int i = 0; i < focusResource.length; i++) {
                IProject selectedProject = focusResource[i].getProject();

                if (selectedProject == null) {
                    continue;
                }

                if (project.equals(selectedProject))
                    return true;
            }
        } else if (onResource == ON_SELECTED_RESOURCE_ONLY) {
            for (int i = 0; i < focusResource.length; i++) {
                if (resource.equals(focusResource[i]))
                    return true;
            }
        } else if (onResource == ON_SELECTED_RESOURCE_AND_CHILDREN) {
            for (int i = 0; i < focusResource.length; i++) {
                IResource parentResource = resource;

                while (parentResource != null) {
                    if (parentResource.equals(focusResource[i]))
                        return true;

                    parentResource = parentResource.getParent();
                }
            }
        }

        return false;
    }

    /**
     * Returns if the given resource is enclosed by a working set element.
     * Previous versions of this method used IContainmentAdapter for 
     * containment tests. For performance reasons, this is no longer possible.
     * Code that relies on this behavior should be updated appropriately.
     * 
     * @param element resource to test for enclosure by a working set
     * 	element 
     * @return true if element is enclosed by a working set element and 
     * 	false otherwise. 
     */
    private boolean isEnclosed(IResource element) {
        if (workingSet == null) {
            return false;
        }
        Set workingSetPaths = getWorkingSetAsSetOfPaths();

        return workingSetPaths.contains(element.getFullPath().toString());
    }

    /**
     * @return the defined limit on the number of markers to be displayed.
     */
    int getMarkerLimit() {
        return markerLimit;
    }

    /**
     * Sets the limit on the number of markers to be displayed.
     * 
     * @param the new limit
     */
    void setMarkerLimit(int markerLimit) {
        this.markerLimit = markerLimit;
    }

    /**
     * @return <ul>
     * <li><code>MarkerFilter.ON_ANY_RESOURCE</code> if showing items associated with any resource.</li>
     * <li><code>MarkerFilter.ON_SELECTED_RESOURCE_ONLY</code> if showing items associated with
     * the selected resource within the workbench.</li>
     * <li><code>MarkerFilter.ON_SELECTED_RESOURCE_AND_CHILDREN</code> if showing items associated with
     * the selected resource within the workbench and its children.</li>
     * <li><code>MarkerFilter.ON_ANY_RESOURCE_OF_SAME_PROJECT</code> if showing items in the same project
     * as the selected resource within the workbench.</li>
     * <li><code>MarkerFilter.ON_WORKING_SET</code> if showing items in some working set.</li>
     * </ul>
     */
    int getOnResource() {
        return onResource;
    }

    /**
     * Sets the type of filtering by selection.
     * 
     * @param onResource must be one of:
     * <ul>
     * <li><code>MarkerFilter.ON_ANY_RESOURCE</code></li>
     * <li><code>MarkerFilter.ON_SELECTED_RESOURCE_ONLY</code></li>
     * <li><code>MarkerFilter.ON_SELECTED_RESOURCE_AND_CHILDREN</code></li>
     * <li><code>MarkerFilter.ON_ANY_RESOURCE_OF_SAME_PROJECT</code></li>
     * <li><code>MarkerFilter.ON_WORKING_SET</code></li>
     * </ul>
     */
    void setOnResource(int onResource) {
        if (onResource >= ON_ANY_RESOURCE && onResource <= ON_WORKING_SET)
            this.onResource = onResource;
    }

    /**
     * @return the selected resource(s) withing the workbench.
     */
    IResource[] getFocusResource() {
        return focusResource;
    }

    /**
     * Sets the focused resources.
     */
    public void setFocusResource(IResource[] resources) {
        focusResource = resources;
    }

    /**
     * @return
     * <ul>
     * <li><code>true</code> if the filter is enabled.</li>
     * <li><code>false</code> if the filter is not enabled.</li>
     * </ul>
     */
    boolean isEnabled() {
        return enabled;
    }

    /**
     * @return
     * <ul>
     * <li><code>true</code> if filtering by marker limit is enabled.</li>
     * <li><code>false</code> if filtering by marker limit is not enabled.</li>
     * </ul>
     */
    boolean getFilterOnMarkerLimit() {
        return filterOnMarkerLimit;
    }

    /**
     * <b>Warning:</b> for internal package use only.  Return the root
     * marker types.
     * 
     * @return the root marker types.
     */
    public List getRootTypes() {
        return rootTypes;
    }

    /**
     * <b>Warning:</b> for internal package use only.  Return the
     * selected types.
     * 
     * @return the selected marker types to be displayed.
     */
    public List getSelectedTypes() {
        return selectedTypes;
    }
        
    /**
     * <b>Warning:</b> for internal private use only. Test based on the
     * types model.
     *  
     * @param id the ID for a marker type
     * @return the type for this id
     */
    public MarkerType getMarkerType(String id) {
    	return typesModel.getType(id);
    }

    /**
     * @return the current working set or <code>null</code> if no working set is defined.
     */
    IWorkingSet getWorkingSet() {
        return workingSet;
    }

    /**
     * Sets the enablement state of the filter.
     */
    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Sets the enablement state of filtering by marker limit.
     */
    void setFilterOnMarkerLimit(boolean filterOnMarkerLimit) {
        this.filterOnMarkerLimit = filterOnMarkerLimit;
    }

    /**
     * Sets the selected marker types to be displayed. The List <b>MUST ONLY</b> contain 
     * <code>MarkerType</code> objects.
     */
    void setSelectedTypes(List selectedTypes) {
        this.selectedTypes = selectedTypes;
    }

    /**
     * Sets the current working set.
     */
    void setWorkingSet(IWorkingSet workingSet) {
        this.workingSet = workingSet;
        cachedWorkingSet = null;
    }

    void resetState() {
        enabled = DEFAULT_ACTIVATION_STATUS;
        filterOnMarkerLimit = DEFAULT_FILTER_ON_MARKER_LIMIT;
        markerLimit = DEFAULT_MARKER_LIMIT;
        onResource = DEFAULT_ON_RESOURCE;
        selectedTypes.clear();
        addAllSubTypes(selectedTypes);
        setWorkingSet(null);
    }

    public void restoreState(IDialogSettings dialogSettings) {
        resetState();
        IDialogSettings settings = dialogSettings
                .getSection(TAG_DIALOG_SECTION);

        if (settings != null) {
            String setting = settings.get(TAG_ENABLED);

            if (setting != null)
                enabled = Boolean.valueOf(setting).booleanValue();

            setting = settings.get(TAG_FILTER_ON_MARKER_LIMIT);

            if (setting != null)
                filterOnMarkerLimit = Boolean.valueOf(setting).booleanValue();

            setting = settings.get(TAG_MARKER_LIMIT);

            if (setting != null)
                try {
                    markerLimit = Integer.parseInt(setting);
                } catch (NumberFormatException eNumberFormat) {
                }

            setting = settings.get(TAG_ON_RESOURCE);

            if (setting != null) {
                try {
                    onResource = Integer.parseInt(setting);
                } catch (NumberFormatException eNumberFormat) {
                }
            }
            
            // new selection list attribute
            // format is "id:(true|false):"
            setting = settings.get(TAG_SELECTION_STATUS);

            if (setting != null) {
				selectedTypes.clear();

				// get the complete list of types
				List newTypes = new ArrayList();
				addAllSubTypes(newTypes);

				StringTokenizer stringTokenizer = new StringTokenizer(setting);

				while (stringTokenizer.hasMoreTokens()) {
					String id = stringTokenizer.nextToken(TAG_TYPES_DELIMITER);
					String status = null;
					if (stringTokenizer.hasMoreTokens()) {
						status = stringTokenizer.nextToken(TAG_TYPES_DELIMITER);
					}

					MarkerType markerType = typesModel.getType(id);
					if (markerType != null) {
						newTypes.remove(markerType);

						// add the type to the selected list
						if (!SELECTED_FALSE.equals(status)
								&& !selectedTypes.contains(markerType)) {
							selectedTypes.add(markerType);
						}
					}
				}
				
				// any types we know about that weren't either true or
				// false in the selection attribute are new. By default,
				// new marker types will be selected=true
				for (int i = 0; i < newTypes.size(); ++i) {
					selectedTypes.add(newTypes.get(i));
				}
			} else {
				// the settings didn't contain the new selection attribute
				// so check for the old selection attribute.
				// format is just "id:"
				setting = settings.get(TAG_SELECTED_TYPES);

				if (setting != null) {
					selectedTypes.clear();
					StringTokenizer stringTokenizer = new StringTokenizer(
							setting);

					while (stringTokenizer.hasMoreTokens()) {
						MarkerType markerType = typesModel
								.getType(stringTokenizer
										.nextToken(TAG_TYPES_DELIMITER));

						if (markerType != null
								&& !selectedTypes.contains(markerType))
							selectedTypes.add(markerType);
					}
				}

			}

            setting = settings.get(TAG_WORKING_SET);

            if (setting != null)
                setWorkingSet(WorkbenchPlugin.getDefault()
                        .getWorkingSetManager().getWorkingSet(setting));
        }
    }

    public void saveState(IDialogSettings dialogSettings) {
        if (dialogSettings != null) {
            IDialogSettings settings = dialogSettings
                    .getSection(TAG_DIALOG_SECTION);

            if (settings == null)
                settings = dialogSettings.addNewSection(TAG_DIALOG_SECTION);

            settings.put(TAG_ENABLED, enabled);
            settings.put(TAG_FILTER_ON_MARKER_LIMIT, filterOnMarkerLimit);
            settings.put(TAG_MARKER_LIMIT, markerLimit);
            settings.put(TAG_ON_RESOURCE, onResource);

            String markerTypeIds = ""; //$NON-NLS-1$

            List includedTypes = new ArrayList();
            addAllSubTypes(includedTypes);
            for (int i = 0; i < includedTypes.size(); i++) {
				MarkerType markerType = (MarkerType) includedTypes.get(i);
				markerTypeIds += markerType.getId() + TAG_TYPES_DELIMITER;
				if (selectedTypes.contains(markerType)) {
						markerTypeIds += SELECTED_TRUE + TAG_TYPES_DELIMITER; 
				} else {
					markerTypeIds += SELECTED_FALSE + TAG_TYPES_DELIMITER; 
				}
			}

            settings.put(TAG_SELECTION_STATUS, markerTypeIds);

            if (workingSet != null)
                settings.put(TAG_WORKING_SET, workingSet.getName());
        }
    }
}
