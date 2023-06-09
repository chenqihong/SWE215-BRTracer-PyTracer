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
package org.eclipse.jdt.internal.corext.refactoring.rename;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.jdt.internal.corext.Assert;
import org.eclipse.jdt.internal.corext.refactoring.participants.ResourceModifications;
import org.eclipse.jdt.internal.corext.refactoring.tagging.INameUpdating;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.participants.ParticipantManager;
import org.eclipse.ltk.core.refactoring.participants.RefactoringParticipant;
import org.eclipse.ltk.core.refactoring.participants.RenameArguments;
import org.eclipse.ltk.core.refactoring.participants.RenameParticipant;
import org.eclipse.ltk.core.refactoring.participants.RenameProcessor;
import org.eclipse.ltk.core.refactoring.participants.SharableParticipants;

public abstract class JavaRenameProcessor extends RenameProcessor implements INameUpdating {
	
	private String fNewElementName;
	
	public final RefactoringParticipant[] loadParticipants(RefactoringStatus status, SharableParticipants sharedParticipants) throws CoreException {
		RenameArguments arguments= new RenameArguments(getNewElementName(), getUpdateReferences());
		String[] natures= getAffectedProjectNatures();
		List result= new ArrayList();
		loadElementParticipants(status, result, arguments, natures, sharedParticipants);
		loadDerivedParticipants(status, result, natures, sharedParticipants);
		return (RefactoringParticipant[])result.toArray(new RefactoringParticipant[result.size()]);
	}
	
	protected void loadElementParticipants(RefactoringStatus status, List result, RenameArguments arguments, String[] natures, SharableParticipants shared) throws CoreException {
		Object[] elements= getElements();
		for (int i= 0; i < elements.length; i++) {
			result.addAll(Arrays.asList(ParticipantManager.loadRenameParticipants(status, 
				this,  elements[i],
				arguments, natures, shared)));
		}
	}
	
	protected abstract void loadDerivedParticipants(RefactoringStatus status, List result, String[] natures, SharableParticipants shared) throws CoreException;
	
	protected void loadDerivedParticipants(RefactoringStatus status, List result, Object[] derivedElements, 
			RenameArguments arguments, ResourceModifications resourceModifications, String[] natures, SharableParticipants shared) throws CoreException {
		if (derivedElements != null) {
			for (int i= 0; i < derivedElements.length; i++) {
				RenameParticipant[] participants= ParticipantManager.loadRenameParticipants(status, 
					this, derivedElements[i], 
					arguments, natures, shared);
				result.addAll(Arrays.asList(participants));
			}
		}
		if (resourceModifications != null) {
			result.addAll(Arrays.asList(resourceModifications.getParticipants(status, this, natures, shared)));
		}
	}
	
	public void setNewElementName(String newName) {
		Assert.isNotNull(newName);
		fNewElementName= newName;
	}

	public String getNewElementName() {
		return fNewElementName;
	}
	
	protected abstract String[] getAffectedProjectNatures() throws CoreException;
	
	public abstract boolean getUpdateReferences();	
	
	/**
	 * <code>true</code> by default, subclasses may override.
	 * 
	 * @return <code>true</code> iff this refactoring needs all editors to be saved,
	 *  <code>false</code> otherwise
	 */
	public boolean needsSavedEditors() {
		return true;
	}
}
