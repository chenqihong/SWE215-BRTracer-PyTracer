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
package org.eclipse.debug.internal.ui.actions;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.model.IBreakpoint;
import org.eclipse.debug.core.model.IDebugElement;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;
import org.eclipse.debug.internal.ui.DebugPluginImages;
import org.eclipse.debug.internal.ui.DebugUIPlugin;
import org.eclipse.debug.internal.ui.IDebugHelpContextIds;
import org.eclipse.debug.internal.ui.views.breakpoints.BreakpointContainer;
import org.eclipse.debug.internal.ui.views.breakpoints.BreakpointsView;
import org.eclipse.debug.ui.IDebugUIConstants;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.StructuredViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.ui.ISelectionListener;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PlatformUI;

/**
 * An view filter action that filters showing breakpoints based on whether
 * the IDebugTarget of the selected debug element in the launch view supports
 * the breakpoints.
 * 
 * @see org.eclipse.debug.core.model.IDebugTarget#supportsBreakpoint(IBreakpoint)
 * 
 */
public class ShowSupportedBreakpointsAction extends ToggleFilterAction implements ISelectionListener {

	/**
	 * The view associated with this action
	 */
	private BreakpointsView fView;
	
	/**
	 * The list of identifiers for the current state
	 */
	private List fDebugTargets= new ArrayList(2);
	
	/**
	 * A viewer filter that selects breakpoints that have
	 * the same model identifier as the selected debug element
	 */
	class BreakpointFilter extends ViewerFilter {
		
		/**
		 * @see ViewerFilter#select(Viewer, Object, Object)
		 */
		public boolean select(Viewer viewer, Object parentElement, Object element) {
			if (element instanceof BreakpointContainer) {
				// Breakpoint containers are visible if any of their children are visible.
				IBreakpoint[] breakpoints = ((BreakpointContainer) element).getBreakpoints();
				for (int i = 0; i < breakpoints.length; i++) {
					if (select(viewer, element, breakpoints[i])) {
						return true;
					}
				}
				return false;
			}
			IBreakpoint breakpoint= (IBreakpoint)element;
			if (fDebugTargets.isEmpty()) {
				return true;
			}
			Iterator iterator= fDebugTargets.iterator();
			while (iterator.hasNext()) {
				IDebugTarget target = (IDebugTarget) iterator.next();
				if (target.supportsBreakpoint(breakpoint)) {
					return true;
				}
				
			}
			return false;
		}

	}

	public ShowSupportedBreakpointsAction(StructuredViewer viewer, IViewPart view) {
		super();
		setText(ActionMessages.ShowSupportedBreakpointsAction_Show_For_Selected); //$NON-NLS-1$
		setToolTipText(ActionMessages.ShowSupportedBreakpointsAction_tooltip); //$NON-NLS-1$
		setViewerFilter(new BreakpointFilter());
		setViewer(viewer);
		setImageDescriptor(DebugPluginImages.getImageDescriptor(IDebugUIConstants.IMG_OBJS_DEBUG_TARGET));
		setChecked(false);
		setId(DebugUIPlugin.getUniqueIdentifier() + ".ShowSupportedBreakpointsAction"); //$NON-NLS-1$
		
		setView(view);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(
			this,
			IDebugHelpContextIds.SHOW_BREAKPOINTS_FOR_MODEL_ACTION);
		
	}

	
		
	public void dispose() {
		if (isChecked()) {
			getView().getSite().getPage().removeSelectionListener(IDebugUIConstants.ID_DEBUG_VIEW, this);
		}
	}
	
	/**
	 * @see ISelectionListener#selectionChanged(IWorkbenchPart, ISelection)
	 */
	public void selectionChanged(IWorkbenchPart part, ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection ss= (IStructuredSelection)selection;
			List debugTargets= getDebugTargets(ss);
			if (!isChecked()) {
				fDebugTargets= debugTargets;
				return;
			}
			if (debugTargets.isEmpty()) {
				 if(fDebugTargets.isEmpty()) {
					return;
				 } 
				 reapplyFilters(debugTargets);
				 return;
			}
			if (fDebugTargets.isEmpty()) {
				reapplyFilters(debugTargets);
				return;
			}
			
			if (debugTargets.size() == fDebugTargets.size()) {
				List copy= new ArrayList(debugTargets.size());
				Iterator iter= fDebugTargets.iterator();
				while (iter.hasNext()) {
					IDebugTarget target = (IDebugTarget) iter.next();
					Iterator newDebugTargets= debugTargets.iterator();
					while (newDebugTargets.hasNext()) {
						IDebugTarget newTarget= (IDebugTarget)newDebugTargets.next();
						copy.add(newTarget);
						if (target.equals(newTarget)) {
							newDebugTargets.remove();
						}
					}
				}
				//check for real change
				if (debugTargets.isEmpty()) {
					return;
				}
				reapplyFilters(copy);
			} 
		}
	}

	
	/**
	 * Selection has changed in the debug view
	 * need to reapply the filters.
	 */
	protected void reapplyFilters(List debugTargets) {
		fDebugTargets= debugTargets;		
		getViewer().refresh();
		fView.initializeCheckedState();
	}
	
	protected IViewPart getView() {
		return fView;
	}

	protected void setView(IViewPart view) {
		fView = (BreakpointsView) view;
	}
	
	protected List getDebugTargets(IStructuredSelection ss) {
		List debugTargets= new ArrayList(2);
		Iterator i= ss.iterator();
		while (i.hasNext()) {
			Object next= i.next();
			if (next instanceof IDebugElement) {
				debugTargets.add(((IDebugElement)next).getDebugTarget());
			} else if (next instanceof ILaunch) {
				IDebugTarget[] targets= ((ILaunch)next).getDebugTargets();
				for (int j = 0; j < targets.length; j++) {
					debugTargets.add(targets[j]);
				}
			} else if (next instanceof IProcess) {
				IDebugTarget target= (IDebugTarget)((IProcess)next).getAdapter(IDebugTarget.class);
				if (target != null) {
					debugTargets.add(target);
				}
			}	
		}
		return debugTargets;
	}
	
	/**
	 * Adds or removes the viewer filter depending
	 * on the value of the parameter.
	 */
	protected void valueChanged(boolean on) {
		if (getViewer().getControl().isDisposed()) {
			return;
		}
		if (on) {
			getView().getSite().getPage().addSelectionListener(IDebugUIConstants.ID_DEBUG_VIEW, this);
			ISelection selection= getView().getSite().getPage().getSelection(IDebugUIConstants.ID_DEBUG_VIEW);
			selectionChanged(null, selection);
		} else {
			getView().getSite().getPage().removeSelectionListener(IDebugUIConstants.ID_DEBUG_VIEW, this);
		}
		super.valueChanged(on);
		fView.initializeCheckedState();
//		if (!on) {
//			((BreakpointsView)getView()).initializeCheckedState((CheckboxTreeViewer) getViewer());
//		}
	}
	
}