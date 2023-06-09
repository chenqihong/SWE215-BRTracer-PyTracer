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
package org.eclipse.jdt.internal.ui.search;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.IAdaptable;

import org.eclipse.core.resources.IProject;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.DialogPage;
import org.eclipse.jface.dialogs.IDialogSettings;
import org.eclipse.jface.util.Assert;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;

import org.eclipse.jface.text.ITextSelection;

import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.model.IWorkbenchAdapter;

import org.eclipse.search.ui.ISearchPage;
import org.eclipse.search.ui.ISearchPageContainer;
import org.eclipse.search.ui.NewSearchUI;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.Strings;

import org.eclipse.jdt.ui.search.ElementQuerySpecification;
import org.eclipse.jdt.ui.search.PatternQuerySpecification;
import org.eclipse.jdt.ui.search.QuerySpecification;

import org.eclipse.jdt.internal.ui.IJavaHelpContextIds;
import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.actions.SelectionConverter;
import org.eclipse.jdt.internal.ui.browsing.LogicalPackage;
import org.eclipse.jdt.internal.ui.javaeditor.JavaEditor;
import org.eclipse.jdt.internal.ui.util.ExceptionHandler;

public class JavaSearchPage extends DialogPage implements ISearchPage, IJavaSearchConstants {
	
	
	
	private static class SearchPatternData {
		private int searchFor;
		private int limitTo;
		private String pattern;
		private boolean isCaseSensitive;
		private IJavaElement javaElement;
		private int scope;
		private IWorkingSet[] workingSets;
		
		public SearchPatternData(int searchFor, int limitTo, boolean isCaseSensitive, String pattern, IJavaElement element) {
			this(searchFor, limitTo, pattern, isCaseSensitive, element, ISearchPageContainer.WORKSPACE_SCOPE, null);
		}
		
		public SearchPatternData(int searchFor, int limitTo, String pattern, boolean isCaseSensitive, IJavaElement element, int scope, IWorkingSet[] workingSets) {
			this.searchFor= searchFor;
			this.limitTo= limitTo;
			this.pattern= pattern;
			this.isCaseSensitive= isCaseSensitive;
			this.scope= scope;
			this.workingSets= workingSets;
			
			setJavaElement(element);
		}
		
		public void setJavaElement(IJavaElement javaElement) {
			this.javaElement= javaElement;
		}

		public boolean isCaseSensitive() {
			return isCaseSensitive;
		}

		public IJavaElement getJavaElement() {
			return javaElement;
		}

		public int getLimitTo() {
			return limitTo;
		}

		public String getPattern() {
			return pattern;
		}

		public int getScope() {
			return scope;
		}

		public int getSearchFor() {
			return searchFor;
		}

		public IWorkingSet[] getWorkingSets() {
			return workingSets;
		}
		
		public void store(IDialogSettings settings) {
			settings.put("searchFor", searchFor); //$NON-NLS-1$
			settings.put("scope", scope); //$NON-NLS-1$
			settings.put("pattern", pattern); //$NON-NLS-1$
			settings.put("limitTo", limitTo); //$NON-NLS-1$
			settings.put("javaElement", javaElement != null ? javaElement.getHandleIdentifier() : ""); //$NON-NLS-1$ //$NON-NLS-2$
			settings.put("isCaseSensitive", isCaseSensitive); //$NON-NLS-1$
			if (workingSets != null) {
				String[] wsIds= new String[workingSets.length];
				for (int i= 0; i < workingSets.length; i++) {
					wsIds[i]= workingSets[i].getId();
				}
				settings.put("workingSets", wsIds); //$NON-NLS-1$
			} else {
				settings.put("workingSets", new String[0]); //$NON-NLS-1$
			}

		}
		
		public static SearchPatternData create(IDialogSettings settings) {
			String pattern= settings.get("pattern"); //$NON-NLS-1$
			if (pattern.length() == 0) {
				return null;
			}
			IJavaElement elem= null;
			String handleId= settings.get("javaElement"); //$NON-NLS-1$
			if (handleId != null && handleId.length() > 0) {
				IJavaElement restored= JavaCore.create(handleId); //$NON-NLS-1$
				if (restored != null && isSearchableType(restored) && restored.exists()) {
					elem= restored;
				}
			}
			String[] wsIds= settings.getArray("workingSets"); //$NON-NLS-1$
			IWorkingSet[] workingSets= null;
			if (wsIds != null && wsIds.length > 0) {
				IWorkingSetManager workingSetManager= PlatformUI.getWorkbench().getWorkingSetManager();
				workingSets= new IWorkingSet[wsIds.length];
				for (int i= 0; workingSets != null && i < wsIds.length; i++) {
					workingSets[i]= workingSetManager.getWorkingSet(wsIds[i]);
					if (workingSets[i] == null) {
						workingSets= null;
					}
				}
			}

			try {
				int searchFor= settings.getInt("searchFor"); //$NON-NLS-1$
				int scope= settings.getInt("scope"); //$NON-NLS-1$
				int limitTo= settings.getInt("limitTo"); //$NON-NLS-1$
				boolean isCaseSensitive= settings.getBoolean("isCaseSensitive"); //$NON-NLS-1$

				return 	new SearchPatternData(searchFor, limitTo, pattern, isCaseSensitive, elem, scope, workingSets);
			} catch (NumberFormatException e) {
				return null;
			}
		}
		
	}
	
	public static final String PARTICIPANT_EXTENSION_POINT= "org.eclipse.jdt.ui.queryParticipants"; //$NON-NLS-1$

	public static final String EXTENSION_POINT_ID= "org.eclipse.jdt.ui.JavaSearchPage"; //$NON-NLS-1$

	public static final String PREF_SEARCH_JRE= "org.eclipse.jdt.ui.searchJRE"; //$NON-NLS-1$
	
	private static final int HISTORY_SIZE= 12;
	
	// Dialog store id constants
	private final static String PAGE_NAME= "JavaSearchPage"; //$NON-NLS-1$
	private final static String STORE_CASE_SENSITIVE= "CASE_SENSITIVE"; //$NON-NLS-1$
	private final static String STORE_HISTORY= "HISTORY"; //$NON-NLS-1$
	private final static String STORE_HISTORY_SIZE= "HISTORY_SIZE"; //$NON-NLS-1$
	
	private final List fPreviousSearchPatterns;
	
	private SearchPatternData fInitialData;
	private IJavaElement fJavaElement;
	private boolean fFirstTime= true;
	private IDialogSettings fDialogSettings;
	private boolean fIsCaseSensitive;
	
	private Combo fPattern;
	private ISearchPageContainer fContainer;
	private Button fCaseSensitive;
	
	private Button[] fSearchFor;
	private String[] fSearchForText= {
		SearchMessages.SearchPage_searchFor_type, 
		SearchMessages.SearchPage_searchFor_method, 
		SearchMessages.SearchPage_searchFor_package, 
		SearchMessages.SearchPage_searchFor_constructor, 
		SearchMessages.SearchPage_searchFor_field}; 

	private Button[] fLimitTo;
	private String[] fLimitToText= {
		SearchMessages.SearchPage_limitTo_declarations, 
		SearchMessages.SearchPage_limitTo_implementors, 
		SearchMessages.SearchPage_limitTo_references, 
		SearchMessages.SearchPage_limitTo_allOccurrences, 
		SearchMessages.SearchPage_limitTo_readReferences, 
		SearchMessages.SearchPage_limitTo_writeReferences};

	private Button fSearchJRE; 
	private static final int INDEX_REFERENCES= 2;
	private static final int INDEX_ALL= 3;

	/**
	 * 
	 */
	public JavaSearchPage() {
		fPreviousSearchPatterns= new ArrayList();
	}
	
	
	//---- Action Handling ------------------------------------------------
	
	public boolean performAction() {
		return performNewSearch();
	}
	
	private boolean performNewSearch() {
		SearchPatternData data= getPatternData();

		// Setup search scope
		IJavaSearchScope scope= null;
		String scopeDescription= ""; //$NON-NLS-1$
		
		boolean includeJRE= getSearchJRE() || !mayExcludeJRE();
		JavaSearchScopeFactory factory= JavaSearchScopeFactory.getInstance();
		
		switch (getContainer().getSelectedScope()) {
			case ISearchPageContainer.WORKSPACE_SCOPE:
				scopeDescription= SearchMessages.WorkspaceScope; 
				scope= factory.createWorkspaceScope(includeJRE);
				break;
			case ISearchPageContainer.SELECTION_SCOPE:
				scopeDescription= SearchMessages.SelectionScope; 
				scope= factory.createJavaSearchScope(getContainer().getSelection(), includeJRE);
				break;
			case ISearchPageContainer.SELECTED_PROJECTS_SCOPE:
				scope= factory.createJavaProjectSearchScope(getContainer().getSelection(), includeJRE);
				IProject[] projects= JavaSearchScopeFactory.getInstance().getProjects(scope);
				if (projects.length >= 1) {
					if (projects.length == 1)
						scopeDescription= Messages.format(SearchMessages.EnclosingProjectScope, projects[0].getName()); 
					else
						scopeDescription= Messages.format(SearchMessages.EnclosingProjectsScope, projects[0].getName()); 
				} else 
					scopeDescription= Messages.format(SearchMessages.EnclosingProjectScope, "");  //$NON-NLS-1$
				break;
			case ISearchPageContainer.WORKING_SET_SCOPE:
				IWorkingSet[] workingSets= getContainer().getSelectedWorkingSets();
				// should not happen - just to be sure
				if (workingSets == null || workingSets.length < 1)
					return false;
				scopeDescription= Messages.format(SearchMessages.WorkingSetScope, SearchUtil.toString(workingSets)); 
				scope= factory.createJavaSearchScope(getContainer().getSelectedWorkingSets(), includeJRE);
				SearchUtil.updateLRUWorkingSets(getContainer().getSelectedWorkingSets());
		
		}
		
		QuerySpecification querySpec= null;
		if (data.getJavaElement() != null && getPattern().equals(fInitialData.getPattern())) {
			if (data.getLimitTo() == IJavaSearchConstants.REFERENCES)
				SearchUtil.warnIfBinaryConstant(data.getJavaElement(), getShell());
			querySpec= new ElementQuerySpecification(data.getJavaElement(), data.getLimitTo(), scope, scopeDescription);
		} else {
			querySpec= new PatternQuerySpecification(data.getPattern(), data.getSearchFor(), data.isCaseSensitive(), data.getLimitTo(), scope, scopeDescription);
			data.setJavaElement(null);
		} 
		
		JavaSearchQuery textSearchJob= new JavaSearchQuery(querySpec);
		NewSearchUI.activateSearchResultView();
		NewSearchUI.runQueryInBackground(textSearchJob);
		return true;
	}

	
	
	private int getLimitTo() {
		for (int i= 0; i < fLimitTo.length; i++) {
			if (fLimitTo[i].getSelection())
				return i;
		}
		return -1;
	}

	private void setLimitTo(int searchFor, int limitTo) {
		if (!(searchFor == TYPE || searchFor == INTERFACE) && limitTo == IMPLEMENTORS) {
			limitTo= REFERENCES;
		}

		if (!(searchFor == FIELD) && (limitTo == READ_ACCESSES || limitTo == WRITE_ACCESSES)) {
			limitTo= REFERENCES;
		}
		
		for (int i= 0; i < fLimitTo.length; i++) {
			fLimitTo[i].setSelection(limitTo == i);
		}
		
		fLimitTo[DECLARATIONS].setEnabled(true);
		fLimitTo[IMPLEMENTORS].setEnabled(searchFor == INTERFACE || searchFor == TYPE);
		fLimitTo[REFERENCES].setEnabled(true);			
		fLimitTo[ALL_OCCURRENCES].setEnabled(true);
		fLimitTo[READ_ACCESSES].setEnabled(searchFor == FIELD);
		fLimitTo[WRITE_ACCESSES].setEnabled(searchFor == FIELD);
		
	}

	private String[] getPreviousSearchPatterns() {
		// Search results are not persistent
		int patternCount= fPreviousSearchPatterns.size();
		String [] patterns= new String[patternCount];
		for (int i= 0; i < patternCount; i++)
			patterns[i]= ((SearchPatternData) fPreviousSearchPatterns.get(i)).getPattern();
		return patterns;
	}
	
	private int getSearchFor() {
		for (int i= 0; i < fSearchFor.length; i++) {
			if (fSearchFor[i].getSelection())
				return i;
		}
		Assert.isTrue(false, "shouldNeverHappen"); //$NON-NLS-1$
		return -1;
	}
	
	private String getPattern() {
		return fPattern.getText();
	}

	
	private SearchPatternData findInPrevious(String pattern) {
		for (Iterator iter= fPreviousSearchPatterns.iterator(); iter.hasNext();) {
			SearchPatternData element= (SearchPatternData) iter.next();
			if (pattern.equals(element.getPattern())) {
				return element;
			}
		}
		return null;
	}
	
	/**
	 * Return search pattern data and update previous searches.
	 * An existing entry will be updated.
	 */
	private SearchPatternData getPatternData() {
		String pattern= getPattern();
		SearchPatternData match= findInPrevious(pattern);
		if (match != null) {
			fPreviousSearchPatterns.remove(match);
		}
		match= new SearchPatternData(
				getSearchFor(),
				getLimitTo(),
				pattern,
				fCaseSensitive.getSelection(),
				fJavaElement,
				getContainer().getSelectedScope(),
				getContainer().getSelectedWorkingSets());
			
		fPreviousSearchPatterns.add(0, match); // insert on top
		return match;
	}

	/*
	 * Implements method from IDialogPage
	 */
	public void setVisible(boolean visible) {
		if (visible && fPattern != null) {
			if (fFirstTime) {
				fFirstTime= false;
				// Set item and text here to prevent page from resizing
				fPattern.setItems(getPreviousSearchPatterns());
				initSelections();
			}
			fPattern.setFocus();
		}
		updateOKStatus();
		super.setVisible(visible);
	}
	
	public boolean isValid() {
		return true;
	}

	//---- Widget creation ------------------------------------------------

	/**
	 * Creates the page's content.
	 */
	public void createControl(Composite parent) {
		initializeDialogUnits(parent);
		readConfiguration();
		
		Composite result= new Composite(parent, SWT.NONE);
		
		GridLayout layout= new GridLayout(2, false);
		layout.horizontalSpacing= 10;
		result.setLayout(layout);
		
		Control expressionComposite= createExpression(result);
		expressionComposite.setLayoutData(new GridData(GridData.FILL, GridData.CENTER, true, false, 2, 1));
		
		Label separator= new Label(result, SWT.NONE);
		separator.setVisible(false);
		GridData data= new GridData(GridData.FILL, GridData.FILL, false, false, 2, 1);
		data.heightHint= convertHeightInCharsToPixels(1) / 3;
		separator.setLayoutData(data);
		
		Control searchFor= createSearchFor(result);
		searchFor.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false, 1, 1));

		Control limitTo= createLimitTo(result);
		limitTo.setLayoutData(new GridData(GridData.FILL, GridData.FILL, true, false, 1, 1));

		fSearchJRE= new Button(result, SWT.CHECK);
		fSearchJRE.setText(SearchMessages.SearchPage_searchJRE_label); 
		fSearchJRE.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				setSearchJRE(fSearchJRE.getSelection());
			}
		});
		fSearchJRE.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 2, 1));
		
		//createParticipants(result);
		
		SelectionAdapter javaElementInitializer= new SelectionAdapter() {
			public void widgetSelected(SelectionEvent event) {
				if (getSearchFor() == fInitialData.getSearchFor())
					fJavaElement= fInitialData.getJavaElement();
				else
					fJavaElement= null;
				setLimitTo(getSearchFor(), getLimitTo());
				doPatternModified();
			}
		};

		fSearchFor[TYPE].addSelectionListener(javaElementInitializer);
		fSearchFor[METHOD].addSelectionListener(javaElementInitializer);
		fSearchFor[FIELD].addSelectionListener(javaElementInitializer);
		fSearchFor[CONSTRUCTOR].addSelectionListener(javaElementInitializer);
		fSearchFor[PACKAGE].addSelectionListener(javaElementInitializer);

		setControl(result);

		Dialog.applyDialogFont(result);
		PlatformUI.getWorkbench().getHelpSystem().setHelp(result, IJavaHelpContextIds.JAVA_SEARCH_PAGE);	
	}
	
	public static boolean getSearchJRE() {
		return JavaPlugin.getDefault().getPreferenceStore().getBoolean(PREF_SEARCH_JRE);
	}

	public static void setSearchJRE(boolean value) {
		JavaPlugin.getDefault().getPreferenceStore().setValue(PREF_SEARCH_JRE, value);
	}
	
	/*private Control createParticipants(Composite result) {
		if (!SearchParticipantsExtensionPoint.hasAnyParticipants())
			return new Composite(result, SWT.NULL);
		Button selectParticipants= new Button(result, SWT.PUSH);
		selectParticipants.setText(SearchMessages.getString("SearchPage.select_participants.label")); //$NON-NLS-1$
		GridData gd= new GridData();
		gd.verticalAlignment= GridData.VERTICAL_ALIGN_BEGINNING;
		gd.horizontalAlignment= GridData.HORIZONTAL_ALIGN_END;
		gd.grabExcessHorizontalSpace= false;
		gd.horizontalAlignment= GridData.END;
		gd.horizontalSpan= 2;
		selectParticipants.setLayoutData(gd);
		selectParticipants.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				PreferencePageSupport.showPreferencePage(getShell(), "org.eclipse.jdt.ui.preferences.SearchParticipantsExtensionPoint", new SearchParticipantsExtensionPoint()); //$NON-NLS-1$
			}

		});
		return selectParticipants;
	}*/


	private Control createExpression(Composite parent) {
		Composite result= new Composite(parent, SWT.NONE);
		GridLayout layout= new GridLayout(2, false);
		layout.marginWidth= 0;
		layout.marginHeight= 0;
		result.setLayout(layout);

		// Pattern text + info
		Label label= new Label(result, SWT.LEFT);
		label.setText(SearchMessages.SearchPage_expression_label); 
		label.setLayoutData(new GridData(GridData.FILL, GridData.FILL, false, false, 2, 1));

		// Pattern combo
		fPattern= new Combo(result, SWT.SINGLE | SWT.BORDER);
		fPattern.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				handlePatternSelected();
				updateOKStatus();
			}
		});
		fPattern.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				doPatternModified();
				updateOKStatus();

			}
		});
		GridData data= new GridData(GridData.FILL, GridData.FILL, true, false, 1, 1);
		data.widthHint= convertWidthInCharsToPixels(50);
		fPattern.setLayoutData(data);

		// Ignore case checkbox		
		fCaseSensitive= new Button(result, SWT.CHECK);
		fCaseSensitive.setText(SearchMessages.SearchPage_expression_caseSensitive); 
		fCaseSensitive.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				fIsCaseSensitive= fCaseSensitive.getSelection();
			}
		});
		fCaseSensitive.setLayoutData(new GridData(GridData.FILL, GridData.FILL, false, false, 1, 1));
		
		return result;
	}
	
	final void updateOKStatus() {
		boolean isValid= isValidSearchPattern();
		getContainer().setPerformActionEnabled(isValid);
	}
	
	private boolean isValidSearchPattern() {
		if (getPattern().length() == 0) {
			return false;
		}
		if (fJavaElement != null) {
			return true;
		}
		return SearchPattern.createPattern(getPattern(), getSearchFor(), getLimitTo(), SearchPattern.R_EXACT_MATCH) != null;		
	}
	
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.dialogs.DialogPage#dispose()
	 */
	public void dispose() {
		writeConfiguration();
		super.dispose();
	}

	private void doPatternModified() {
		if (fInitialData != null && getPattern().equals(fInitialData.getPattern()) && fInitialData.getJavaElement() != null && fInitialData.getSearchFor() == getSearchFor()) {
			fCaseSensitive.setEnabled(false);
			fCaseSensitive.setSelection(true);
			fJavaElement= fInitialData.getJavaElement();
		} else {
			fCaseSensitive.setEnabled(true);
			fCaseSensitive.setSelection(fIsCaseSensitive);
			fJavaElement= null;
		}
	}

	private void handlePatternSelected() {
		int selectionIndex= fPattern.getSelectionIndex();
		if (selectionIndex < 0 || selectionIndex >= fPreviousSearchPatterns.size())
			return;
		
		SearchPatternData initialData= (SearchPatternData) fPreviousSearchPatterns.get(selectionIndex);

		setSearchFor(initialData.getSearchFor());
		setLimitTo(initialData.getSearchFor(), initialData.getLimitTo());

		fPattern.setText(initialData.getPattern());
		fIsCaseSensitive= initialData.isCaseSensitive();
		fJavaElement= initialData.getJavaElement();
		fCaseSensitive.setEnabled(fJavaElement == null);
		fCaseSensitive.setSelection(initialData.isCaseSensitive());

		
		if (initialData.getWorkingSets() != null)
			getContainer().setSelectedWorkingSets(initialData.getWorkingSets());
		else
			getContainer().setSelectedScope(initialData.getScope());
		
		fInitialData= initialData;
	}
	
	private void setSearchFor(int searchFor) {
		for (int i= 0; i < fSearchFor.length; i++) {
			fSearchFor[i].setSelection(searchFor == i);
		}
	}
	

	private Control createSearchFor(Composite parent) {
		Group result= new Group(parent, SWT.NONE);
		result.setText(SearchMessages.SearchPage_searchFor_label); 
		result.setLayout(new GridLayout(2, true));

		fSearchFor= new Button[fSearchForText.length];
		for (int i= 0; i < fSearchForText.length; i++) {
			Button button= new Button(result, SWT.RADIO);
			button.setText(fSearchForText[i]);
			button.setSelection(i == TYPE);
			button.setLayoutData(new GridData());
			fSearchFor[i]= button;
		}

		// Fill with dummy radio buttons
		Label filler= new Label(result, SWT.NONE);
		filler.setVisible(false);
		filler.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1));

		return result;		
	}
	
	private Control createLimitTo(Composite parent) {
		Group result= new Group(parent, SWT.NONE);
		result.setText(SearchMessages.SearchPage_limitTo_label); 
		result.setLayout(new GridLayout(2, true));
		
		SelectionAdapter listener= new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e) {
				updateUseJRE();
			}
		};

		fLimitTo= new Button[fLimitToText.length];
		for (int i= 0; i < fLimitToText.length; i++) {
			Button button= new Button(result, SWT.RADIO);
			button.setText(fLimitToText[i]);
			fLimitTo[i]= button;
			button.setSelection(i == REFERENCES);
			button.addSelectionListener(listener);
			button.setLayoutData(new GridData());
		}
		
		return result;		
	}	
	
	private void initSelections() {
		ISelection sel= getContainer().getSelection();
		SearchPatternData initData= null;

		if (sel instanceof IStructuredSelection) {
			initData= tryStructuredSelection((IStructuredSelection) sel);
		} else if (sel instanceof ITextSelection) {
			IEditorPart activePart= getActiveEditor();
			if (activePart instanceof JavaEditor) {
				try {
					IJavaElement[] elements= SelectionConverter.codeResolve((JavaEditor) activePart);
					if (elements != null && elements.length > 0) {
						initData= determineInitValuesFrom(elements[0]);
					}
				} catch (JavaModelException e) {
					// ignore
				}
			}
			if (initData == null) {
				initData= trySimpleTextSelection((ITextSelection) sel);
			}
		}
		if (initData == null) {
			initData= getDefaultInitValues();
		}
		
		fInitialData= initData;
		fJavaElement= initData.getJavaElement();
		fCaseSensitive.setSelection(initData.isCaseSensitive());
		fCaseSensitive.setEnabled(fJavaElement == null);
		
		setSearchFor(initData.getSearchFor());
		setLimitTo(initData.getSearchFor(), initData.getLimitTo());

		fPattern.setText(initData.getPattern());
		updateUseJRE();
	}

	private void updateUseJRE() {
		boolean shouldEnable= mayExcludeJRE();
		if (shouldEnable) {
			fSearchJRE.setSelection(getSearchJRE());
			fSearchJRE.setEnabled(true);
		} else {
			fSearchJRE.setEnabled(false);
			fSearchJRE.setSelection(true);
		}
	}

	private boolean mayExcludeJRE() {
		return getLimitTo() == INDEX_REFERENCES || getLimitTo() == INDEX_ALL;
	}

	private SearchPatternData tryStructuredSelection(IStructuredSelection selection) {
		if (selection == null || selection.size() > 1)
			return null;

		Object o= selection.getFirstElement();
		SearchPatternData res= null;
		if (o instanceof IJavaElement) {
			res= determineInitValuesFrom((IJavaElement) o);
		} else if (o instanceof LogicalPackage) {
			LogicalPackage lp= (LogicalPackage)o;
			return new SearchPatternData(PACKAGE, REFERENCES, fIsCaseSensitive, lp.getElementName(), null);
		} else if (o instanceof IAdaptable) {
			IJavaElement element= (IJavaElement) ((IAdaptable) o).getAdapter(IJavaElement.class);
			if (element != null) {
				res= determineInitValuesFrom(element);
			}
		}
		if (res == null && o instanceof IAdaptable) {
			IWorkbenchAdapter adapter= (IWorkbenchAdapter)((IAdaptable)o).getAdapter(IWorkbenchAdapter.class);
			if (adapter != null) {
				return new SearchPatternData(TYPE, REFERENCES, fIsCaseSensitive, adapter.getLabel(o), null);
			}
		}
		return res;
	}
	
	final static boolean isSearchableType(IJavaElement element) {
		switch (element.getElementType()) {
			case IJavaElement.PACKAGE_FRAGMENT:
			case IJavaElement.PACKAGE_DECLARATION:
			case IJavaElement.IMPORT_DECLARATION:
			case IJavaElement.TYPE:
			case IJavaElement.FIELD:
			case IJavaElement.METHOD:
				return true;
		}
		return false;
	}

	private SearchPatternData determineInitValuesFrom(IJavaElement element) {
		try {
			switch (element.getElementType()) {
				case IJavaElement.PACKAGE_FRAGMENT:
				case IJavaElement.PACKAGE_DECLARATION:
					return new SearchPatternData(PACKAGE, REFERENCES, true, element.getElementName(), element);
				case IJavaElement.IMPORT_DECLARATION: {
					IImportDeclaration declaration= (IImportDeclaration) element;
					if (declaration.isOnDemand()) {
						String name= Signature.getQualifier(declaration.getElementName());
						return new SearchPatternData(PACKAGE, DECLARATIONS, true, name, element);
					}
					return new SearchPatternData(TYPE, DECLARATIONS, true, element.getElementName(), element);
				}
				case IJavaElement.TYPE:
					return new SearchPatternData(TYPE, REFERENCES, true, PatternStrings.getTypeSignature((IType) element), element);
				case IJavaElement.COMPILATION_UNIT: {
					IType mainType= ((ICompilationUnit) element).findPrimaryType();
					if (mainType != null) {
						return new SearchPatternData(TYPE, REFERENCES, true, PatternStrings.getTypeSignature(mainType), mainType);
					}
					break;
				}
				case IJavaElement.CLASS_FILE: {
					IType mainType= ((IClassFile) element).getType();
					if (mainType.exists()) {
						return new SearchPatternData(TYPE, REFERENCES, true, PatternStrings.getTypeSignature(mainType), mainType);
					}
					break;
				}
				case IJavaElement.FIELD:
					return new SearchPatternData(FIELD, REFERENCES, true, PatternStrings.getFieldSignature((IField) element), element);
				case IJavaElement.METHOD:
					IMethod method= (IMethod) element;
					int searchFor= method.isConstructor() ? CONSTRUCTOR : METHOD;
					return new SearchPatternData(searchFor, REFERENCES, true, PatternStrings.getMethodSignature(method), element);
			}
			
		} catch (JavaModelException e) {
			if (!e.isDoesNotExist()) {
				ExceptionHandler.handle(e, SearchMessages.Search_Error_javaElementAccess_title, SearchMessages.Search_Error_javaElementAccess_message); 
			}
			// element might not exist
		}
		return null;	
	}
	
	private SearchPatternData trySimpleTextSelection(ITextSelection selection) {
		String selectedText= selection.getText();
		if (selectedText != null && selectedText.length() > 0) {
			int i= 0;
			while (i < selectedText.length() && !Strings.isLineDelimiterChar(selectedText.charAt(i))) {
				i++;
			}
			if (i > 0) {
				return new SearchPatternData(TYPE, REFERENCES, fIsCaseSensitive, selectedText.substring(0, i), null);
			}
		}
		return null;
	}
	
	private SearchPatternData getDefaultInitValues() {
		if (!fPreviousSearchPatterns.isEmpty()) {
			return (SearchPatternData) fPreviousSearchPatterns.get(0);
		}
		return new SearchPatternData(TYPE, REFERENCES, fIsCaseSensitive, "", null); //$NON-NLS-1$
	}	

	/*
	 * Implements method from ISearchPage
	 */
	public void setContainer(ISearchPageContainer container) {
		fContainer= container;
	}
	
	/**
	 * Returns the search page's container.
	 */
	private ISearchPageContainer getContainer() {
		return fContainer;
	}
		
	private IEditorPart getActiveEditor() {
		IWorkbenchPage activePage= JavaPlugin.getActivePage();
		if (activePage != null) {
			return activePage.getActiveEditor();
		}
		return null;
	}
	
	//--------------- Configuration handling --------------
	
	/**
	 * Returns the page settings for this Java search page.
	 * 
	 * @return the page settings to be used
	 */
	private IDialogSettings getDialogSettings() {
		IDialogSettings settings= JavaPlugin.getDefault().getDialogSettings();
		fDialogSettings= settings.getSection(PAGE_NAME);
		if (fDialogSettings == null)
			fDialogSettings= settings.addNewSection(PAGE_NAME);
		return fDialogSettings;
	}
	
	/**
	 * Initializes itself from the stored page settings.
	 */
	private void readConfiguration() {
		IDialogSettings s= getDialogSettings();
		fIsCaseSensitive= s.getBoolean(STORE_CASE_SENSITIVE);
		
		try {
			int historySize= s.getInt(STORE_HISTORY_SIZE);
			for (int i= 0; i < historySize; i++) {
				IDialogSettings histSettings= s.getSection(STORE_HISTORY + i);
				if (histSettings != null) {
					SearchPatternData data= SearchPatternData.create(histSettings);
					if (data != null) {
						fPreviousSearchPatterns.add(data);
					}
				}
			}
		} catch (NumberFormatException e) {
			// ignore
		}
	}
	
	/**
	 * Stores it current configuration in the dialog store.
	 */
	private void writeConfiguration() {
		IDialogSettings s= getDialogSettings();
		s.put(STORE_CASE_SENSITIVE, fIsCaseSensitive);
		
		int historySize= Math.min(fPreviousSearchPatterns.size(), HISTORY_SIZE);
		s.put(STORE_HISTORY_SIZE, historySize);
		for (int i= 0; i < historySize; i++) {
			IDialogSettings histSettings= s.addNewSection(STORE_HISTORY + i);
			SearchPatternData data= ((SearchPatternData) fPreviousSearchPatterns.get(i));
			data.store(histSettings);
		}
	}
}
