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

package org.eclipse.jdt.internal.ui.refactoring.nls.search;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;

import org.eclipse.core.resources.IFile;

import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;

import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.ISearchResult;
import org.eclipse.search.ui.text.AbstractTextSearchResult;

import org.eclipse.jdt.internal.corext.util.Messages;
import org.eclipse.jdt.internal.corext.util.SearchUtils;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaUIStatus;


public class NLSSearchQuery implements ISearchQuery {
	private NLSSearchResult fResult;
	private IJavaElement fWrapperClass;
	private IFile fPropertiesFile;
	private IJavaSearchScope fScope;
	private String fScopeDescription;
	
	public NLSSearchQuery(IJavaElement wrapperClass, IFile propertiesFile, IJavaSearchScope scope, String scopeDescription) {
		fWrapperClass= wrapperClass;
		fPropertiesFile= propertiesFile;
		fScope= scope;
		fScopeDescription= scopeDescription;
	}
	
	IFile getPropertiesFile() {
		return fPropertiesFile;
	}
	
	/*
	 * @see org.eclipse.search.ui.ISearchQuery#run(org.eclipse.core.runtime.IProgressMonitor)
	 */
	public IStatus run(IProgressMonitor monitor) {
		monitor.beginTask("", 5); //$NON-NLS-1$
		if (! fWrapperClass.exists())
			return JavaUIStatus.createError(0, Messages.format(NLSSearchMessages.NLSSearchQuery_wrapperNotExists, fWrapperClass.getElementName()), null); 
		if (! fPropertiesFile.exists())
			return JavaUIStatus.createError(0, Messages.format(NLSSearchMessages.NLSSearchQuery_propertiesNotExists, fPropertiesFile.getName()), null); 
		
		final AbstractTextSearchResult textResult= (AbstractTextSearchResult) getSearchResult();
		textResult.removeAll();
		
		SearchPattern pattern= SearchPattern.createPattern(fWrapperClass, IJavaSearchConstants.REFERENCES, SearchUtils.GENERICS_AGNOSTIC_MATCH_RULE);
		SearchParticipant[] participants= new SearchParticipant[] {SearchEngine.getDefaultSearchParticipant()};
		NLSSearchResultRequestor requestor= new NLSSearchResultRequestor(fPropertiesFile, fResult);
		try {
			SearchEngine engine= new SearchEngine();
			engine.search(pattern, participants, fScope, requestor, new SubProgressMonitor(monitor, 4));
			requestor.reportUnusedPropertyNames(new SubProgressMonitor(monitor, 1));
		} catch (CoreException e) {
			JavaPlugin.log(e);
		}
		monitor.done();
		return 	Status.OK_STATUS;
	}

	/*
	 * @see org.eclipse.search.ui.ISearchQuery#getLabel()
	 */
	public String getLabel() {
		return NLSSearchMessages.NLSSearchQuery_label; 
	}

	public String getResultLabel(int nMatches) {
		if (nMatches == 1) {
			String[] args= new String[] {fWrapperClass.getElementName(), fScopeDescription};	
			return Messages.format(NLSSearchMessages.SearchOperation_singularLabelPostfix, args); 
		}
		String[] args= new String[] {fWrapperClass.getElementName(), String.valueOf(nMatches), fScopeDescription};
		return Messages.format(NLSSearchMessages.SearchOperation_pluralLabelPatternPostfix, args); 
	}
	
	/*
	 * @see org.eclipse.search.ui.ISearchQuery#canRerun()
	 */
	public boolean canRerun() {
		return true;
	}

	/*
	 * @see org.eclipse.search.ui.ISearchQuery#canRunInBackground()
	 */
	public boolean canRunInBackground() {
		return true;
	}

	/*
	 * @see org.eclipse.search.ui.ISearchQuery#getSearchResult()
	 */
	public ISearchResult getSearchResult() {
		if (fResult == null)
			fResult= new NLSSearchResult(this);
		return fResult;
	}
}
