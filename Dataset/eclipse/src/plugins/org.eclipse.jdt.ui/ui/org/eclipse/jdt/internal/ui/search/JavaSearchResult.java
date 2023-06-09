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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;

import org.eclipse.jface.resource.ImageDescriptor;

import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;

import org.eclipse.search.ui.ISearchQuery;
import org.eclipse.search.ui.ISearchResultListener;
import org.eclipse.search.ui.SearchResultEvent;
import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.IEditorMatchAdapter;
import org.eclipse.search.ui.text.IFileMatchAdapter;
import org.eclipse.search.ui.text.Match;

import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import org.eclipse.jdt.ui.search.IMatchPresentation;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.javaeditor.IClassFileEditorInput;

public class JavaSearchResult extends AbstractTextSearchResult implements IEditorMatchAdapter, IFileMatchAdapter {
	private JavaSearchQuery fQuery;
	private Map fElementsToParticipants;
	private static final Match[] NO_MATCHES= new Match[0];
	private ISearchResultListener fFilterListener;
	
	public JavaSearchResult(JavaSearchQuery query) {
		fQuery= query;
		fElementsToParticipants= new HashMap();
	}

	public ImageDescriptor getImageDescriptor() {
		return fQuery.getImageDescriptor();
	}

	public String getLabel() {
		return fQuery.getResultLabel(getMatchCount());
	}

	public String getTooltip() {
		return getLabel();
	}
	
	public void setFilterListner(ISearchResultListener listener) {
		fFilterListener= listener;
	}
	
	protected void fireChange(SearchResultEvent e) {
		if (fFilterListener != null)
			fFilterListener.searchResultChanged(e);
		super.fireChange(e);
	}

	public Match[] computeContainedMatches(AbstractTextSearchResult result, IEditorPart editor) {
		IEditorInput editorInput= editor.getEditorInput();
		if (editorInput instanceof IFileEditorInput)  {
			IFileEditorInput fileEditorInput= (IFileEditorInput) editorInput;
			return computeContainedMatches(result, fileEditorInput.getFile());
		} else if (editorInput instanceof IClassFileEditorInput) {
			IClassFileEditorInput classFileEditorInput= (IClassFileEditorInput) editorInput;
			Set matches= new HashSet();
			collectMatches(matches, classFileEditorInput.getClassFile());
			return (Match[]) matches.toArray(new Match[matches.size()]);
		}
		return null;
	}

	public Match[] computeContainedMatches(AbstractTextSearchResult result, IFile file) {
		IJavaElement javaElement= JavaCore.create(file);
		if (!(javaElement instanceof ICompilationUnit || javaElement instanceof IClassFile))
			return NO_MATCHES;
		Set matches= new HashSet();
		collectMatches(matches, javaElement);
		return (Match[]) matches.toArray(new Match[matches.size()]);
	}
	
	private void collectMatches(Set matches, IJavaElement element) {
		Match[] m= getMatches(element);
		if (m.length != 0) {
			for (int i= 0; i < m.length; i++) {
				matches.add(m[i]);
			}
		}
		if (element instanceof IParent) {
			IParent parent= (IParent) element;
			try {
				IJavaElement[] children= parent.getChildren();
				for (int i= 0; i < children.length; i++) {
					collectMatches(matches, children[i]);
				}
			} catch (JavaModelException e) {
				// we will not be tracking these results
			}
		}
	}
	/* (non-Javadoc)
	 * @see org.eclipse.search.ui.ISearchResultCategory#getFile(java.lang.Object)
	 */
	public IFile getFile(Object element) {
		if (element instanceof IJavaElement) {
			IJavaElement javaElement= (IJavaElement) element;
			IFile matchFile= null;
			ICompilationUnit cu= (ICompilationUnit) javaElement.getAncestor(IJavaElement.COMPILATION_UNIT);
			if (cu != null) {
				matchFile= (IFile) cu.getResource();
			} else {
				IClassFile cf= (IClassFile) javaElement.getAncestor(IJavaElement.CLASS_FILE);
				if (cf != null)
					matchFile= (IFile) cf.getResource();
			}
			return matchFile;
		}
		if (element instanceof IFile)
			return (IFile) element;
		return null;
	}
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.eclipse.search2.ui.text.IStructureProvider#isShownInEditor(org.eclipse.search2.ui.text.Match,
	 *      org.eclipse.ui.IEditorPart)
	 */
	public boolean isShownInEditor(Match match, IEditorPart editor) {
		IEditorInput editorInput= editor.getEditorInput();
		if (match.getElement() instanceof IJavaElement) {
			IJavaElement je= (IJavaElement) match.getElement();
			if (editorInput instanceof IFileEditorInput) {
				IPackageFragmentRoot root= (IPackageFragmentRoot) je.getAncestor(IJavaElement.PACKAGE_FRAGMENT_ROOT);
				if (root.isArchive())
					return false;
				IFile inputFile= ((IFileEditorInput)editorInput).getFile();
				IResource matchFile= null;
				ICompilationUnit cu= (ICompilationUnit) je.getAncestor(IJavaElement.COMPILATION_UNIT);
				if (cu != null) {
					matchFile= cu.getResource();
				} else {
					IClassFile cf= (IClassFile) je.getAncestor(IJavaElement.CLASS_FILE);
					if (cf != null)
						matchFile= cf.getResource();
				}
				if (matchFile != null)
					return inputFile.equals(matchFile);
				else
					return false;
			} else if (editorInput instanceof IClassFileEditorInput) {
				return ((IClassFileEditorInput)editorInput).getClassFile().equals(je.getAncestor(IJavaElement.CLASS_FILE));
			}
		} else if (match.getElement() instanceof IFile) {
			if (editorInput instanceof IFileEditorInput) {
				return ((IFileEditorInput)editorInput).getFile().equals(match.getElement());
			}
		}
		return false;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.search.ui.ISearchResult#getQuery()
	 */
	public ISearchQuery getQuery() {
		return fQuery;
	}
	
	synchronized IMatchPresentation getSearchParticpant(Object element) {
		return (IMatchPresentation) fElementsToParticipants.get(element);
	}

	boolean addMatch(Match match, IMatchPresentation participant) {
		Object element= match.getElement();
		if (fElementsToParticipants.get(element) != null) {
			// TODO must access the participant id / label to properly report the error.
			JavaPlugin.log(new Status(IStatus.WARNING, JavaPlugin.getPluginId(), 0, "A second search participant was found for an element", null)); //$NON-NLS-1$
			return false;
		}
		fElementsToParticipants.put(element, participant);
		addMatch(match);
		return true;
	}
	
	public void removeAll() {
		synchronized(this) {
			fElementsToParticipants.clear();
		}
		super.removeAll();
	}
	
	public void removeMatch(Match match) {
		synchronized(this) {
			if (getMatchCount(match.getElement()) == 1)
				fElementsToParticipants.remove(match.getElement());
		}
		super.removeMatch(match);
	}
	public IFileMatchAdapter getFileMatchAdapter() {
		return this;
	}
	
	public IEditorMatchAdapter getEditorMatchAdapter() {
		return this;
	}

}
