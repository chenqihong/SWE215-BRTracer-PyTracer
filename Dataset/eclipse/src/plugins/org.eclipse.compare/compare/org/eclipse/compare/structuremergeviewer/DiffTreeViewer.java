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
package org.eclipse.compare.structuremergeviewer;

import java.util.Iterator;
import java.util.ResourceBundle;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.*;

import org.eclipse.jface.util.*;
import org.eclipse.jface.action.*;
import org.eclipse.jface.viewers.*;

import org.eclipse.compare.internal.*;
import org.eclipse.compare.*;

/**
 * A tree viewer that works on objects implementing
 * the <code>IDiffContainer</code> and <code>IDiffElement</code> interfaces.
 * <p>
 * This class may be instantiated; it is not intended to be subclassed outside
 * this package.
 * </p>
 *
 * @see IDiffContainer
 * @see IDiffElement
 */
public class DiffTreeViewer extends TreeViewer {
	
	static class DiffViewerSorter extends ViewerSorter {
	
		public boolean isSorterProperty(Object element, Object property) {
			return false;
		}
	
		public int category(Object node) {
			if (node instanceof DiffNode) {
				Object o= ((DiffNode) node).getId();
				if (o instanceof DocumentRangeNode)
					return ((DocumentRangeNode) o).getTypeCode();
			}
			return 0;
		}
	}	

	class DiffViewerContentProvider implements ITreeContentProvider {
			
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput) {
			// empty implementation
		}
	
		public boolean isDeleted(Object element) {
			return false;
		}
			
		public void dispose() {
			inputChanged(DiffTreeViewer.this, getInput(), null);
		}
			
		public Object getParent(Object element) {
			if (element instanceof IDiffElement) 
				return ((IDiffElement)element).getParent();
			return null;
		}
		
		public final boolean hasChildren(Object element) {
			if (element instanceof IDiffContainer) 
				return ((IDiffContainer)element).hasChildren();
			return false;
		}
		
		public final Object[] getChildren(Object element) {
			if (element instanceof IDiffContainer)
				return ((IDiffContainer)element).getChildren();
			return new Object[0];
		}
		
		public Object[] getElements(Object element) {
			return getChildren(element);
		}				
	}
	
	/*
	 * Takes care of swapping left and right if fLeftIsLocal
	 * is true.
	 */
	class DiffViewerLabelProvider extends LabelProvider {
		
		public String getText(Object element) {
			
			if (element instanceof IDiffElement)
				return ((IDiffElement)element).getName();
						
			return Utilities.getString(fBundle, "defaultLabel"); //$NON-NLS-1$
		}
	
		public Image getImage(Object element) {
			if (element instanceof IDiffElement) {
				IDiffElement input= (IDiffElement) element;
				
				int kind= input.getKind();
				if (fLeftIsLocal) {
					switch (kind & Differencer.DIRECTION_MASK) {
					case Differencer.LEFT:
						kind= (kind &~ Differencer.LEFT) | Differencer.RIGHT;
						break;
					case Differencer.RIGHT:
						kind= (kind &~ Differencer.RIGHT) | Differencer.LEFT;
						break;
					}
				}
				
				return fCompareConfiguration.getImage(input.getImage(), kind);
			}
			return null;
		}
	}

	static class FilterSame extends ViewerFilter {
		public boolean select(Viewer viewer, Object parentElement, Object element) {
			if (element instanceof IDiffElement)
				return (((IDiffElement)element).getKind() & Differencer.PSEUDO_CONFLICT) == 0;
			return true;
		}
		public boolean isFilterProperty(Object element, Object property) {
			return false;
		}
	}
	
	private ResourceBundle fBundle;
	private CompareConfiguration fCompareConfiguration;
	/* package */ boolean fLeftIsLocal;
	private IPropertyChangeListener fPropertyChangeListener;

	private Action fCopyLeftToRightAction;
	private Action fCopyRightToLeftAction;
	private Action fEmptyMenuAction;
	private Action fExpandAllAction;
		
	/**
	 * Creates a new viewer for the given SWT tree control with the specified configuration.
	 *
	 * @param tree the tree control
	 * @param configuration the configuration for this viewer
	 */
	public DiffTreeViewer(Tree tree, CompareConfiguration configuration) {
		super(tree);
		initialize(configuration);
	}
	
	/**
	 * Creates a new viewer under the given SWT parent and with the specified configuration.
	 *
	 * @param parent the SWT control under which to create the viewer
	 * @param configuration the configuration for this viewer
	 */
	public DiffTreeViewer(Composite parent, CompareConfiguration configuration) {
		super(new Tree(parent, SWT.MULTI));
		initialize(configuration);
	}
	
	private void initialize(CompareConfiguration configuration) {
		
		Control tree= getControl();
		
		INavigatable nav= new INavigatable() {
			public boolean gotoDifference(boolean next) {
				// Fix for http://dev.eclipse.org/bugs/show_bug.cgi?id=20106
				return internalNavigate(next, true);
			}
		};
		tree.setData(INavigatable.NAVIGATOR_PROPERTY, nav);
		
		// Fix for http://dev.eclipse.org/bugs/show_bug.cgi?id=20106
		IOpenable openable= new IOpenable() {
			public void openSelected() {
				internalOpen();
			}
		};
		tree.setData(IOpenable.OPENABLE_PROPERTY, openable);
		
		fLeftIsLocal= Utilities.getBoolean(configuration, "LEFT_IS_LOCAL", false); //$NON-NLS-1$

		tree.setData(CompareUI.COMPARE_VIEWER_TITLE, getTitle());

		Composite parent= tree.getParent();
		
		fBundle= ResourceBundle.getBundle("org.eclipse.compare.structuremergeviewer.DiffTreeViewerResources"); //$NON-NLS-1$
		
		// register for notification with the CompareConfiguration 
		fCompareConfiguration= configuration;
		if (fCompareConfiguration != null) {
			fPropertyChangeListener= new IPropertyChangeListener() {
				public void propertyChange(PropertyChangeEvent event) {
					DiffTreeViewer.this.propertyChange(event);
				}
			};
			fCompareConfiguration.addPropertyChangeListener(fPropertyChangeListener);
		}				
	
		setContentProvider(new DiffViewerContentProvider());
		setLabelProvider(new DiffViewerLabelProvider());
		
		addSelectionChangedListener(
			new ISelectionChangedListener() {
				public void selectionChanged(SelectionChangedEvent se) {
					updateActions();
				}
			}
		);
										
		setSorter(new DiffViewerSorter());
		
		ToolBarManager tbm= CompareViewerPane.getToolBarManager(parent);
		if (tbm != null) {
			tbm.removeAll();
			
			tbm.add(new Separator("merge")); //$NON-NLS-1$
			tbm.add(new Separator("modes")); //$NON-NLS-1$
			tbm.add(new Separator("navigation")); //$NON-NLS-1$
			
			createToolItems(tbm);
			updateActions();
			
			tbm.update(true);
		}
		
		MenuManager mm= new MenuManager();
		mm.setRemoveAllWhenShown(true);
		mm.addMenuListener(
			new IMenuListener() {
				public void menuAboutToShow(IMenuManager mm2) {
					fillContextMenu(mm2);
					if (mm2.isEmpty()) {
						if (fEmptyMenuAction == null) {
							fEmptyMenuAction= new Action(Utilities.getString(fBundle, "emptyMenuItem")) {	//$NON-NLS-1$
								// left empty
							};
							fEmptyMenuAction.setEnabled(false);
						}
						mm2.add(fEmptyMenuAction);
					}
				}
			}
		);
		tree.setMenu(mm.createContextMenu(tree));
	}
			
	/**
	 * Returns the viewer's name.
	 *
	 * @return the viewer's name
	 */
	public String getTitle() {
		String title= Utilities.getString(fBundle, "title", null); //$NON-NLS-1$
		if (title == null)
			title= Utilities.getString("DiffTreeViewer.title"); //$NON-NLS-1$
		return title;
	}
	
	/**
	 * Returns the resource bundle.
	 *
	 * @return the viewer's resource bundle
	 */
	protected ResourceBundle getBundle() {
		return fBundle;
	}

	/**
	 * Returns the compare configuration of this viewer.
	 *
	 * @return the compare configuration of this viewer
	 */
	public CompareConfiguration getCompareConfiguration() {
		return fCompareConfiguration;
	}
			
	/**
	 * Called on the viewer disposal.
	 * Unregisters from the compare configuration.
	 * Clients may extend if they have to do additional cleanup.
	 * @param event dispose event that triggered call to this method
	 */
	protected void handleDispose(DisposeEvent event) {
		
		if (fCompareConfiguration != null) {
			if (fPropertyChangeListener != null)
				fCompareConfiguration.removePropertyChangeListener(fPropertyChangeListener);
			fCompareConfiguration= null;
		}
		fPropertyChangeListener= null;
		
		super.handleDispose(event);
	}
	
	/**
	 * Tracks property changes of the configuration object.
	 * Clients may extend to track their own property changes.
	 * @param event property change event that triggered call to this method
	 */
	protected void propertyChange(PropertyChangeEvent event) {
		// empty default implementation
	}
	
	protected void inputChanged(Object in, Object oldInput) {
		super.inputChanged(in, oldInput);
		
		if (in != oldInput) {
			initialSelection();
			updateActions();
		}
	}
	
	/**
	 * This hook method is called from within <code>inputChanged</code>
	 * after a new input has been set but before any controls are updated.
	 * This default implementation calls <code>navigate(true)</code>
	 * to select and expand the first leaf node.
	 * Clients can override this method and are free to decide whether
	 * they want to call the inherited method.
	 * 
	 * @since 2.0
	 */
	protected void initialSelection() {
		navigate(true);
	}

	/**
	 * Overridden to avoid expanding <code>DiffNode</code>s that shouldn't expand.
     * @param node the node to expand
     * @param level non-negative level, or <code>ALL_LEVELS</code> to collapse all levels of the tree
	 */
	protected void internalExpandToLevel(Widget node, int level) {
				
		Object data= node.getData();
		
		if (dontExpand(data))
			return;
		
		super.internalExpandToLevel(node, level);
	}
	
	/**
	 * This hook method is called from within <code>internalExpandToLevel</code>
	 * to control whether a given model node should be expanded or not.
	 * This default implementation checks whether the object is a <code>DiffNode</code> and
	 * calls <code>dontExpand()</code> on it.
	 * Clients can override this method and are free to decide whether
	 * they want to call the inherited method.
	 * 
	 * @param o the model object to be expanded
	 * @return <code>false</code> if a node should be expanded, <code>true</code> to prevent expanding
	 * @since 2.0
	 */
	protected boolean dontExpand(Object o) {
		return o instanceof DiffNode && ((DiffNode)o).dontExpand();
	}
	
	//---- merge action support

	/**
	 * This factory method is called after the viewer's controls have been created.
	 * It installs four actions in the given <code>ToolBarManager</code>. Two actions
	 * allow for copying one side of a <code>DiffNode</code> to the other side.
	 * Two other actions are for navigating from one node to the next (previous).
	 * <p>
	 * Clients can override this method and are free to decide whether they want to call
	 * the inherited method.
	 *
	 * @param toolbarManager the toolbar manager for which to add the actions
	 */
	protected void createToolItems(ToolBarManager toolbarManager) {
		
//		fCopyLeftToRightAction= new Action() {
//			public void run() {
//				copySelected(true);
//			}
//		};
//		Utilities.initAction(fCopyLeftToRightAction, fBundle, "action.TakeLeft.");
//		toolbarManager.appendToGroup("merge", fCopyLeftToRightAction);

//		fCopyRightToLeftAction= new Action() {
//			public void run() {
//				copySelected(false);
//			}
//		};
//		Utilities.initAction(fCopyRightToLeftAction, fBundle, "action.TakeRight.");
//		toolbarManager.appendToGroup("merge", fCopyRightToLeftAction);
		
//		fNextAction= new Action() {
//			public void run() {
//				navigate(true);
//			}
//		};
//		Utilities.initAction(fNextAction, fBundle, "action.NextDiff."); //$NON-NLS-1$
//		toolbarManager.appendToGroup("navigation", fNextAction); //$NON-NLS-1$

//		fPreviousAction= new Action() {
//			public void run() {
//				navigate(false);
//			}
//		};
//		Utilities.initAction(fPreviousAction, fBundle, "action.PrevDiff."); //$NON-NLS-1$
//		toolbarManager.appendToGroup("navigation", fPreviousAction); //$NON-NLS-1$
	}
	
	/**
	 * This method is called to add actions to the viewer's context menu.
	 * It installs actions for expanding tree nodes, copying one side of a <code>DiffNode</code> to the other side.
	 * Clients can override this method and are free to decide whether they want to call
	 * the inherited method.
	 *
	 * @param manager the menu manager for which to add the actions
	 */
	protected void fillContextMenu(IMenuManager manager) {
		if (fExpandAllAction == null) {
			fExpandAllAction= new Action() {
				public void run() {
					expandSelection();
				}
			};
			Utilities.initAction(fExpandAllAction, fBundle, "action.ExpandAll."); //$NON-NLS-1$
		}
		
		boolean enable= false;
		ISelection selection= getSelection();
		if (selection instanceof IStructuredSelection) {
			Iterator elements= ((IStructuredSelection)selection).iterator();
			while (elements.hasNext()) {
				Object element= elements.next();
				if (element instanceof IDiffContainer) {
					if (((IDiffContainer)element).hasChildren()) {
						enable= true;
						break;
					}
				}
			}
		}
		fExpandAllAction.setEnabled(enable);

		manager.add(fExpandAllAction);
		
		if (fCopyLeftToRightAction != null)
			manager.add(fCopyLeftToRightAction);
		if (fCopyRightToLeftAction != null)
			manager.add(fCopyRightToLeftAction);
	}

	/**
	 * Expands to infinity all items in the selection.
	 * 
	 * @since 2.0
	 */
	protected void expandSelection() {
		ISelection selection= getSelection();
		if (selection instanceof IStructuredSelection) {
			Iterator elements= ((IStructuredSelection)selection).iterator();
			while (elements.hasNext()) {
				Object next= elements.next();
				expandToLevel(next, ALL_LEVELS);
			}
		}
	}

	/**
	 * Copies one side of all <code>DiffNode</code>s in the current selection to the other side.
	 * Called from the (internal) actions for copying the sides of a <code>DiffNode</code>.
	 * Clients may override. 
	 * 
	 * @param leftToRight if <code>true</code> the left side is copied to the right side.
	 * If <code>false</code> the right side is copied to the left side
	 */
	protected void copySelected(boolean leftToRight) {
		ISelection selection= getSelection();
		if (selection instanceof IStructuredSelection) {
			Iterator e= ((IStructuredSelection) selection).iterator();
			while (e.hasNext()) {
				Object element= e.next();
				if (element instanceof ICompareInput)
					copyOne((ICompareInput) element, leftToRight);
			}
		}
	}
	
	/**
	 * Called to copy one side of the given node to the other.
	 * This default implementation delegates the call to <code>ICompareInput.copy(...)</code>.
	 * Clients may override. 
	 * @param node the node to copy
	 * @param leftToRight if <code>true</code> the left side is copied to the right side.
	 * If <code>false</code> the right side is copied to the left side
	 */
	protected void copyOne(ICompareInput node, boolean leftToRight) {
		
		node.copy(leftToRight);
		
		// update node's image
		update(new Object[] { node }, null);
	}
	
	/**
	 * Selects the next (or previous) node of the current selection.
	 * If there is no current selection the first (last) node in the tree is selected.
	 * Wraps around at end or beginning.
	 * Clients may override. 
	 *
	 * @param next if <code>true</code> the next node is selected, otherwise the previous node
	 */
	protected void navigate(boolean next) {	
		// Fix for http://dev.eclipse.org/bugs/show_bug.cgi?id=20106
		internalNavigate(next, false);
	}
	
	//---- private
	
	/**
	 * Selects the next (or previous) node of the current selection.
	 * If there is no current selection the first (last) node in the tree is selected.
	 * Wraps around at end or beginning.
	 * Clients may override. 
	 *
	 * @param next if <code>true</code> the next node is selected, otherwise the previous node
	 * @param fireOpen if <code>true</code> an open event is fired.
	 * @return <code>true</code> if at end (or beginning)
	 */
	private boolean internalNavigate(boolean next, boolean fireOpen) {
		
		Control c= getControl();
		if (!(c instanceof Tree))
			return false;
			
		Tree tree= (Tree) c;
		TreeItem item= null;
		TreeItem children[]= tree.getSelection();
		if (children != null && children.length > 0)
			item= children[0];
		if (item == null) {
			children= tree.getItems();
			if (children != null && children.length > 0) {
				item= children[0];
				if (item != null && item.getItemCount() <= 0) {
					internalSetSelection(item, fireOpen);				// Fix for http://dev.eclipse.org/bugs/show_bug.cgi?id=20106
					return false;
				}
			}
		}
			
		while (true) {
			item= findNextPrev(item, next);
			if (item == null)
				break;
			if (item.getItemCount() <= 0)
				break;
		}
		
		if (item != null) {
			internalSetSelection(item, fireOpen);	// Fix for http://dev.eclipse.org/bugs/show_bug.cgi?id=20106
			return false;
		}
		return true;
	}

	private TreeItem findNextPrev(TreeItem item, boolean next) {
		
		if (item == null)
			return null;
		
		TreeItem children[]= null;

		if (!next) {
		
			TreeItem parent= item.getParentItem();
			if (parent != null)
				children= parent.getItems();
			else
				children= item.getParent().getItems();
			
			if (children != null && children.length > 0) {
				// goto previous child
				int index= 0;
				for (; index < children.length; index++)
					if (children[index] == item)
						break;
				
				if (index > 0) {
					
					item= children[index-1];
					
					while (true) {
						createChildren(item);
						int n= item.getItemCount();
						if (n <= 0)
							break;
							
						item.setExpanded(true);
						item= item.getItems()[n-1];
					}

					// previous
					return item;
				}
			}
			
			// go up
			item= parent;
					
		} else {
			item.setExpanded(true);
			createChildren(item);
			
			if (item.getItemCount() > 0) {
				// has children: go down
				children= item.getItems();
				return children[0];
			}
			
			while (item != null) {
				children= null;
				TreeItem parent= item.getParentItem();
				if (parent != null)
					children= parent.getItems();
				else
					children= item.getParent().getItems();
				
				if (children != null && children.length > 0) {
					// goto next child
					int index= 0;
					for (; index < children.length; index++)
						if (children[index] == item)
							break;
					
					if (index < children.length-1) {
						// next
						return children[index+1];
					}
				}
				
				// go up
				item= parent;
			}
		}
				
		return item;
	}
	
	private void internalSetSelection(TreeItem ti, boolean fireOpen) {
		if (ti != null) {
			Object data= ti.getData();
			if (data != null) {
				// Fix for http://dev.eclipse.org/bugs/show_bug.cgi?id=20106
				ISelection selection= new StructuredSelection(data);
				setSelection(selection, true);
				ISelection currentSelection= getSelection();
				if (fireOpen && currentSelection != null && selection.equals(currentSelection)) {
					fireOpen(new OpenEvent(this, selection));
				}
			}
		}
	}
			
	private final boolean isEditable(Object element, boolean left) {
		if (element instanceof ICompareInput) {
			ICompareInput diff= (ICompareInput) element;
			Object side= left ? diff.getLeft() : diff.getRight();
			if (side == null && diff instanceof IDiffElement) {
				IDiffContainer container= ((IDiffElement)diff).getParent();
				if (container instanceof ICompareInput) {
					ICompareInput parent= (ICompareInput) container;
					side= left ? parent.getLeft() : parent.getRight();
				}
			}
			if (side instanceof IEditableContent)
				return ((IEditableContent) side).isEditable();
		}
		return false;
	}
		
	private void updateActions() {
		int leftToRight= 0;
		int rightToLeft= 0;
		ISelection selection= getSelection();
		if (selection instanceof IStructuredSelection) {
			IStructuredSelection ss= (IStructuredSelection) selection;
			Iterator e= ss.iterator();
			while (e.hasNext()) {
				Object element= e.next();
				if (element instanceof ICompareInput) {
					if (isEditable(element, false))
						leftToRight++;
					if (isEditable(element, true))
						rightToLeft++;
					if (leftToRight > 0 && rightToLeft > 0)
						break;
				}
			}
			if (fExpandAllAction != null)
				fExpandAllAction.setEnabled(selection.isEmpty());
		}
		if (fCopyLeftToRightAction != null)
			fCopyLeftToRightAction.setEnabled(leftToRight > 0);
		if (fCopyRightToLeftAction != null)
			fCopyRightToLeftAction.setEnabled(rightToLeft > 0);
	}
	
	/*
	 * Fix for http://dev.eclipse.org/bugs/show_bug.cgi?id=20106
	 */ 
	private void internalOpen()  {
		ISelection selection= getSelection();
		if (selection != null && !selection.isEmpty()) {
			fireOpen(new OpenEvent(this, selection));
		}
	}
}

