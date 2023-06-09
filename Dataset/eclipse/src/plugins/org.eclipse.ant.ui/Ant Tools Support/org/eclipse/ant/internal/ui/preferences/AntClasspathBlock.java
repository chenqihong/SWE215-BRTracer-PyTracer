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
package org.eclipse.ant.internal.ui.preferences;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.eclipse.ant.core.IAntClasspathEntry;
import org.eclipse.ant.internal.ui.AntUIPlugin;
import org.eclipse.ant.internal.ui.IAntUIConstants;
import org.eclipse.ant.internal.ui.IAntUIPreferenceConstants;
import org.eclipse.ant.internal.ui.launchConfigurations.VariableInputDialog;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.jdt.internal.debug.ui.actions.ArchiveFilter;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.ui.dialogs.ElementTreeSelectionDialog;
import org.eclipse.ui.dialogs.ISelectionStatusValidator;
import org.eclipse.ui.model.WorkbenchContentProvider;
import org.eclipse.ui.model.WorkbenchLabelProvider;
import org.eclipse.ui.views.navigator.ResourceSorter;

public class AntClasspathBlock {

	private static final String[] TOOLS= new String[] {"tools.jar"}; //$NON-NLS-1$

	private TreeViewer treeViewer;
	private AntClasspathContentProvider antContentProvider;

	private Button upButton;
	private Button downButton;
	private Button removeButton;

	private AntClasspathLabelProvider labelProvider = new AntClasspathLabelProvider(this);
	private Button addFolderButton;
	private Button addJARButton;
	private Button addExternalJARButton;
	private Button addVariableButton;
	private Button antHomeButton;
	
	private String antHome;

	private IDialogSettings dialogSettings = AntUIPlugin.getDefault().getDialogSettings();
	
	private IAntBlockContainer container;
	
	private int validated= 2;
	
	private IClasspathEntry currentParent;
	
	private SelectionListener selectionListener= new SelectionAdapter() {
		public void widgetSelected(SelectionEvent e) {
			Object source = e.getSource();
			if (source == addJARButton) {
				addJars();
			} else if (source == addExternalJARButton) {
				addExternalJars();
			} else if (source == addFolderButton) {
				addFolder(); //$NON-NLS-1$
			} else if (upButton == source) {
					handleMoveUp();
			} else if (downButton == source) {
				handleMoveDown();
			} else if (removeButton == source) {
				remove();
			} else if (addVariableButton == source) {
				addVariable();
			} else if (antHomeButton == source) {
				browseAntHome();
			}
		}
	};
	
	public void setContainer(IAntBlockContainer container) {
		this.container= container; 
	}
	
	private void addButtonsToButtonGroup(Composite parent) {
	
		
		addJARButton = container.createPushButton(parent, AntPreferencesMessages.AntClasspathBlock_addJarButtonTitle); //$NON-NLS-1$;
		addJARButton.addSelectionListener(selectionListener);
	
		addExternalJARButton = container.createPushButton(parent, AntPreferencesMessages.AntClasspathBlock_42); //$NON-NLS-1$
		addExternalJARButton.addSelectionListener(selectionListener);
		addFolderButton = container.createPushButton(parent, AntPreferencesMessages.AntClasspathBlock_addFolderButtonTitle); //$NON-NLS-1$;
		addFolderButton.addSelectionListener(selectionListener);
		
		addVariableButton = container.createPushButton(parent, AntPreferencesMessages.AntClasspathBlock_2); //$NON-NLS-1$
		addVariableButton.addSelectionListener(selectionListener);
		
		antHomeButton= container.createPushButton(parent, AntPreferencesMessages.AntClasspathBlock_30); //$NON-NLS-1$
		antHomeButton.addSelectionListener(selectionListener);
	
		removeButton = container.createPushButton(parent, AntPreferencesMessages.AntClasspathBlock_removeButtonTitle); //$NON-NLS-1$;
		removeButton.addSelectionListener(selectionListener);
		
		upButton = container.createPushButton(parent, AntPreferencesMessages.AntClasspathBlock_upButtonTitle); //$NON-NLS-1$;
		upButton.addSelectionListener(selectionListener);
		downButton = container.createPushButton(parent, AntPreferencesMessages.AntClasspathBlock_downButtonTitle); //$NON-NLS-1$;
		downButton.addSelectionListener(selectionListener);
	}
	
	/**
	 * Returns the selected items in the list, in the order they are
	 * displayed.
	 * 
	 * @return targets for an action
	 */
	private List getOrderedSelection(IClasspathEntry parent) {
		List targets = new ArrayList();
		List selection = ((IStructuredSelection)treeViewer.getSelection()).toList();
		IAntClasspathEntry[] entries = parent.getEntries();
		for (int i = 0; i < entries.length; i++) {
			IAntClasspathEntry target = entries[i];
			if (selection.contains(target)) {
				targets.add(target);
			}
		}
		return targets;		
	}
	
	private void handleMoveDown() {
		List targets = getOrderedSelection(currentParent);
		List list= new ArrayList(Arrays.asList(currentParent.getEntries()));
		int bottom = list.size() - 1;
		int index = 0;
		for (int i = targets.size() - 1; i >= 0; i--) {
			Object target = targets.get(i);
			index = list.indexOf(target);
			if (index < bottom) {
				bottom = index + 1;
				Object temp = list.get(bottom);
				list.set(bottom, target);
				list.set(index, temp);
			}
			bottom = index;
		} 
		finishMove(list);
	}
	
	private void finishMove(List list) {
		AntClasspathContentProvider viewerContentProvider = (AntClasspathContentProvider) treeViewer.getContentProvider();
		viewerContentProvider.setEntries(currentParent, list);
		treeViewer.refresh();
		treeViewer.setSelection(treeViewer.getSelection());
		updateContainer();
	}

	private void handleMoveUp() {
		List targets = getOrderedSelection(currentParent);
		int top = 0;
		int index = 0;
		List list= new ArrayList(Arrays.asList(currentParent.getEntries()));
		Iterator entries = targets.iterator();
		while (entries.hasNext()) {
			Object target = entries.next();
			index = list.indexOf(target);
			if (index > top) {
				top = index - 1;
				Object temp = list.get(top);
				list.set(top, target);
				list.set(index, temp);
			}
			top = index;
		}
		
		finishMove(list);
	}

	private void remove() {
		AntClasspathContentProvider viewerContentProvider = (AntClasspathContentProvider) treeViewer.getContentProvider();
		IStructuredSelection sel = (IStructuredSelection) treeViewer.getSelection();
		viewerContentProvider.remove(sel);
		updateContainer();
	}

	/**
	 * Allows the user to enter a folder as a classpath.
	 */
	private void addFolder() {
		String lastUsedPath = dialogSettings.get(IAntUIConstants.DIALOGSTORE_LASTFOLDER);
		if (lastUsedPath == null) {
			lastUsedPath = ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString();
		}
		DirectoryDialog dialog = new DirectoryDialog(treeViewer.getControl().getShell());
		dialog.setMessage(AntPreferencesMessages.AntClasspathBlock_1); //$NON-NLS-1$
		dialog.setFilterPath(lastUsedPath);
		String result = dialog.open();
		if (result != null) {
			try {
				URL url = new URL("file:" + result + "/"); //$NON-NLS-2$;//$NON-NLS-1$;
				((AntClasspathContentProvider)treeViewer.getContentProvider()).add(currentParent, url);
			} catch (MalformedURLException e) {
			}
		}
		treeViewer.setSelection(treeViewer.getSelection());
		dialogSettings.put(IAntUIConstants.DIALOGSTORE_LASTFOLDER, result);
		updateContainer();
	}

	private void addExternalJars() {
		String lastUsedPath = dialogSettings.get(IAntUIConstants.DIALOGSTORE_LASTEXTJAR);
		if (lastUsedPath == null) {
			lastUsedPath = ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString();
		}
		FileDialog dialog = new FileDialog(treeViewer.getControl().getShell(), SWT.MULTI);
		dialog.setFilterExtensions(new String[] { "*.jar;*.zip" }); //$NON-NLS-1$
		dialog.setFilterPath(lastUsedPath);

		String result = dialog.open();
		if (result == null) {
			return;
		}
		IPath filterPath = new Path(dialog.getFilterPath());
		String[] results = dialog.getFileNames();
		AntClasspathContentProvider contentProvider= (AntClasspathContentProvider)treeViewer.getContentProvider();
		contentProvider.setRefreshEnabled(false);
		for (int i = 0; i < results.length; i++) {
			String jarName = results[i];
			try {
				IPath path = filterPath.append(jarName).makeAbsolute();
				URL url = new URL("file:" + path.toOSString()); //$NON-NLS-1$;
				contentProvider.add(currentParent, url);
			} catch (MalformedURLException e) {
			}
		}
		contentProvider.setRefreshEnabled(true);

		treeViewer.setSelection(treeViewer.getSelection());
		dialogSettings.put(IAntUIConstants.DIALOGSTORE_LASTEXTJAR, filterPath.toOSString());
		updateContainer();
	}
	
	private void addJars() {
		List allEntries= new ArrayList();
		if (currentParent != null) {
			allEntries.addAll(Arrays.asList(currentParent.getEntries()));
		} else {
			Object[] entries= antContentProvider.getModel().getEntries(ClasspathModel.USER);
			if (entries != null) {
				allEntries.addAll(Arrays.asList(entries));
			}
		}
		
		ViewerFilter filter= new ArchiveFilter(allEntries);
		
		ILabelProvider lp= new WorkbenchLabelProvider();
		ITreeContentProvider cp= new WorkbenchContentProvider();

		ElementTreeSelectionDialog dialog= new ElementTreeSelectionDialog(treeViewer.getControl().getShell(), lp, cp);
		dialog.setTitle(AntPreferencesMessages.AntClasspathBlock_44);  //$NON-NLS-1$
		dialog.setMessage(AntPreferencesMessages.AntClasspathBlock_45); //$NON-NLS-1$
		dialog.addFilter(filter);
		dialog.setInput(ResourcesPlugin.getWorkspace().getRoot());	
		dialog.setSorter(new ResourceSorter(ResourceSorter.NAME));
		
		ISelectionStatusValidator validator= new ISelectionStatusValidator() {
			public IStatus validate(Object[] selection) {
				if (selection.length == 0) {
					return new Status(IStatus.ERROR, AntUIPlugin.getUniqueIdentifier(), 0, "", null); //$NON-NLS-1$
				}
				for (int i= 0; i < selection.length; i++) {
					if (!(selection[i] instanceof IFile)) {
						return new Status(IStatus.ERROR, AntUIPlugin.getUniqueIdentifier(), 0, "", null); //$NON-NLS-1$
					}					
				}
				return new Status(IStatus.OK, AntUIPlugin.getUniqueIdentifier(), 0, "", null); //$NON-NLS-1$
			}			
		};
		dialog.setValidator(validator);

		if (dialog.open() == Window.OK) {
			Object[] elements= dialog.getResult();
			AntClasspathContentProvider contentProvider= (AntClasspathContentProvider)treeViewer.getContentProvider();
			contentProvider.setRefreshEnabled(false);
			for (int i = 0; i < elements.length; i++) {
				IFile file = (IFile)elements[i];
				String varExpression= VariablesPlugin.getDefault().getStringVariableManager().generateVariableExpression("workspace_loc", file.getFullPath().toString()); //$NON-NLS-1$
				contentProvider.add(currentParent, varExpression);
			}
			contentProvider.setRefreshEnabled(true);
			updateContainer();
		}
	}
		
	private void updateContainer() {
		validated= 0;
		container.update();
	}

	/**
	 * Creates the group which will contain the buttons.
	 */
	private void createButtonGroup(Composite top) {
		Composite buttonGroup = new Composite(top, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginHeight = 0;
		layout.marginWidth = 0;
		buttonGroup.setLayout(layout);
		buttonGroup.setLayoutData(new GridData(GridData.FILL_VERTICAL));
		buttonGroup.setFont(top.getFont());

		addButtonsToButtonGroup(buttonGroup);
	}
	
	private void createClasspathTree(Composite parent) {
		Tree tree = new Tree(parent, SWT.MULTI | SWT.FULL_SELECTION | SWT.BORDER);
		GridData data = new GridData(GridData.FILL_BOTH);
		data.widthHint = IDialogConstants.ENTRY_FIELD_WIDTH;
		data.heightHint = tree.getItemHeight();
		tree.setLayoutData(data);
		tree.setFont(parent.getFont());
		
		tree.addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent event) {
				if (event.character == SWT.DEL && event.stateMask == 0) {
					remove();
				}
			}
		});	

		antContentProvider = new AntClasspathContentProvider();
		treeViewer = new TreeViewer(tree);
		treeViewer.setContentProvider(antContentProvider);
		treeViewer.setLabelProvider(labelProvider);
		treeViewer.addSelectionChangedListener(new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				tableSelectionChanged((IStructuredSelection) event.getSelection(),
					(AntClasspathContentProvider) treeViewer.getContentProvider());
			}
		});
	}
			
	public void createContents(Composite parent) {
		createClasspathTree(parent);
		createButtonGroup(parent);
		
		tableSelectionChanged((IStructuredSelection)treeViewer.getSelection(), antContentProvider);
	}
	
	private void tableSelectionChanged(IStructuredSelection selection, AntClasspathContentProvider contentProvider) {
		
		boolean notEmpty= !selection.isEmpty();
		boolean first= !notEmpty;
		boolean last= !notEmpty;
		boolean canRemove= true;
		boolean canAdd= true;
        boolean canMove= true;
        if (!resolveCurrentParent(selection)) {
            //selection contains elements from multiple parents
            canAdd= false;
            canMove= false;
            canRemove= false;
        } else {
            Iterator selected = selection.iterator();
    		while (selected.hasNext()) {
    			IClasspathEntry element = (IClasspathEntry) selected.next();
    			
    			if (element instanceof GlobalClasspathEntries) {
    				GlobalClasspathEntries global= (GlobalClasspathEntries)element;
    				canRemove= global.canBeRemoved();
    				canAdd= global.getType() != ClasspathModel.CONTRIBUTED;
                    canMove= false;
    			}
    			IClasspathEntry parent= element.getParent();
    			if (parent instanceof GlobalClasspathEntries) {
    				canAdd= ((GlobalClasspathEntries)parent).getType() != ClasspathModel.CONTRIBUTED;
    				canRemove= canAdd;
                    canMove= canAdd;
    			}
    			Object[] childEntries = contentProvider.getChildren(parent);
    			List entries = Arrays.asList(childEntries);
    			int lastEntryIndex = entries.size() - 1;
    			if (!first && entries.indexOf(element) == 0) {
    				first= true;
    			}
    			if (!last && entries.indexOf(element) == lastEntryIndex) {
    				last= true;
    			}
    		}
        }
		
		addJARButton.setEnabled(canAdd);
		addExternalJARButton.setEnabled(canAdd);
		addFolderButton.setEnabled(canAdd);
		addVariableButton.setEnabled(canAdd);
		removeButton.setEnabled(notEmpty && canRemove);
		upButton.setEnabled(canMove && !first);
		downButton.setEnabled(canMove && !last);
	}
	
	private boolean resolveCurrentParent(IStructuredSelection selection) {
		currentParent= null;
		Iterator selected= selection.iterator();
		
		while (selected.hasNext()) {
			Object element = selected.next();
			if (element instanceof ClasspathEntry) {
				IClasspathEntry parent= ((IClasspathEntry)element).getParent();
				if (currentParent != null) {
					if (!currentParent.equals(parent)) {
						return false;
					}
				} else {
					currentParent= parent;
				}
			} else {
				if (currentParent != null) {
					if (!currentParent.equals(element)) {
						return false;
					}
				} else {
					currentParent= (IClasspathEntry)element;
				}
			}
		}
		return true;
	}

	private File validateAntHome(String path) {
		File rootDir = null;
		boolean invalid= true;
		if (path.length() > 0) {
			rootDir= new File(path, "lib"); //$NON-NLS-1$
			File parentDir= rootDir.getParentFile();
			if (parentDir == null || !parentDir.exists()) {
				container.setErrorMessage(AntPreferencesMessages.AntClasspathBlock_56); //$NON-NLS-1$
			} else if (!rootDir.exists()) {
				container.setErrorMessage(AntPreferencesMessages.AntClasspathBlock_7); //$NON-NLS-1$
			} else {
				invalid= false;
			}
		} else {			
			container.setErrorMessage(AntPreferencesMessages.AntClasspathBlock_57); //$NON-NLS-1$
		}
		if (invalid) {
			setValidated();
			return null;
		} 
		container.setErrorMessage(null);
		return rootDir;
	}
	
	private void browseAntHome() {
		String lastUsedPath= dialogSettings.get(IAntUIConstants.DIALOGSTORE_LASTANTHOME);
		if (lastUsedPath == null) {
			lastUsedPath= ResourcesPlugin.getWorkspace().getRoot().getLocation().toOSString();
		}
		DirectoryDialog dialog = new DirectoryDialog(treeViewer.getControl().getShell());
		dialog.setMessage(AntPreferencesMessages.AntClasspathBlock_3); //$NON-NLS-1$
		dialog.setFilterPath(lastUsedPath);
		String path = dialog.open();
		if (path == null) {
			return;
		}
		antHome= path;
		if (path.length() > 0) {
			File rootDir = new File(path, "lib"); //$NON-NLS-1$
			setAntHome(rootDir);
		} else {
			updateContainer();
		}
		dialogSettings.put(IAntUIConstants.DIALOGSTORE_LASTANTHOME, path);
	}
		
	private void setAntHome(File rootDir) {
		AntClasspathContentProvider contentProvider = (AntClasspathContentProvider) treeViewer.getContentProvider();
		contentProvider.setRefreshEnabled(false);
		contentProvider.removeAllGlobalAntClasspathEntries();
		String[] names = rootDir.list();
		if (names != null) {
			Arrays.sort(names);
			for (int i = 0; i < names.length; i++) {
				File file = new File(rootDir, names[i]);
				if (file.isFile() && file.getPath().endsWith(".jar")) { //$NON-NLS-1$
					try {
						URL url = new URL("file:" +  file.getAbsolutePath()); //$NON-NLS-1$
						contentProvider.add(ClasspathModel.ANT_HOME, url);
					} catch (MalformedURLException e) {
					}
				}
			}
		}
		
		contentProvider.setRefreshEnabled(true);
		updateContainer();
	}
	
	public String getAntHome() {
		return antHome;
	}
	
	public void initializeAntHome(String antHomeString) {
		antHome= antHomeString;
	}
	
	public void setInput(ClasspathModel model) {
		treeViewer.setInput(model);
		validated= 0;
	}
	
	public boolean validateAntHome() {
		validated++;
		return validateAntHome(antHome) != null;
	}
	
	public Image getClasspathImage() {
		return labelProvider.getClasspathImage();
	}
	
	public boolean validateToolsJAR() {
		validated++;
		boolean check= AntUIPlugin.getDefault().getPreferenceStore().getBoolean(IAntUIPreferenceConstants.ANT_TOOLS_JAR_WARNING);
		if (check && !AntUIPlugin.isMacOS()) {
			Object[] entries= antContentProvider.getModel().getEntries(ClasspathModel.ANT_HOME);
			boolean valid= !JARPresent(entries, TOOLS).isEmpty();
			if (!valid) {
				entries= antContentProvider.getModel().getEntries(ClasspathModel.GLOBAL_USER);
				valid= !JARPresent(entries, TOOLS).isEmpty();
				if (!valid) {
					entries= antContentProvider.getModel().getEntries(ClasspathModel.USER);
					valid= !JARPresent(entries, TOOLS).isEmpty();
					if (!valid) {
						MessageDialogWithToggle dialog= MessageDialogWithToggle.openYesNoQuestion(AntUIPlugin.getActiveWorkbenchWindow().getShell(), AntPreferencesMessages.AntClasspathBlock_31, AntPreferencesMessages.AntClasspathBlock_32, AntPreferencesMessages.AntClasspathBlock_33, false, AntUIPlugin.getDefault().getPreferenceStore(), IAntUIPreferenceConstants.ANT_TOOLS_JAR_WARNING); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
						valid= dialog.getReturnCode() == IDialogConstants.YES_ID;
					}
				}
			}
			if (!valid) {
				container.setErrorMessage(AntPreferencesMessages.AntClasspathBlock_34); //$NON-NLS-1$
				setValidated();
			}
			return valid;
		}
		return true;
	}

	private List JARPresent(Object[] classpathEntries, String[] suffixes) {
		if (classpathEntries == null) {
			return Collections.EMPTY_LIST;
		}
		List found= new ArrayList(2);
		for (int i = 0; i < classpathEntries.length; i++) {
			String file;
			Object entry = classpathEntries[i];
			if (entry instanceof URL) {
				file= ((URL)entry).getFile();
			} else {
				file= entry.toString();
			}
			for (int j = 0; j < suffixes.length; j++) {
				String suffix = suffixes[j];
				if (file.endsWith(suffix)) {
					found.add(suffix);
				}
			}
		}
		return found;
	}
	
	public boolean isValidated() {
		return validated >= 2;
	}
	
	public void setValidated() {
		validated= 2;
	}
	
	private void addVariable() {
		VariableInputDialog inputDialog = new VariableInputDialog(treeViewer.getControl().getShell());
		inputDialog.open();
		String variableString= inputDialog.getVariableString();
		if (variableString != null && variableString.trim().length() > 0) {
			((AntClasspathContentProvider)treeViewer.getContentProvider()).add(currentParent, variableString);
			treeViewer.setSelection(treeViewer.getSelection());
			updateContainer();
		}
	}
}
