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
package org.eclipse.ltk.core.refactoring.participants;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;

import org.eclipse.core.expressions.EvaluationContext;

import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.internal.core.refactoring.Assert;
import org.eclipse.ltk.internal.core.refactoring.Messages;
import org.eclipse.ltk.internal.core.refactoring.ParticipantDescriptor;
import org.eclipse.ltk.internal.core.refactoring.RefactoringCoreMessages;
import org.eclipse.ltk.internal.core.refactoring.RefactoringCorePlugin;

/* package */ class ParticipantExtensionPoint {
	
	private String fName;
		
	private String fParticipantID;
	private List fParticipants;
	private Class fParticipantClass;
	
	//---- debugging ----------------------------------------
	/*
	private static final boolean EXIST_TRACING;
	static {
		String value= Platform.getDebugOption("org.eclipse.jdt.ui/processor/existTracing"); //$NON-NLS-1$
		EXIST_TRACING= value != null && value.equalsIgnoreCase("true"); //$NON-NLS-1$
	}
	
	private void printTime(long start) {
		System.out.println("[" + fName +  //$NON-NLS-1$
			" extension manager] - existing test: " +  //$NON-NLS-1$
			(System.currentTimeMillis() - start) + " ms"); //$NON-NLS-1$
	}
	*/
	
	public ParticipantExtensionPoint(String name, String participantId, Class clazz) {
		Assert.isNotNull(name);
		Assert.isNotNull(participantId);
		Assert.isNotNull(clazz);
		fName= name;
		fParticipantID= participantId;
		fParticipantClass= clazz;
	}
	
	public String getName() {
		return fName;
	}

	public RefactoringParticipant[] getParticipants(RefactoringStatus status, RefactoringProcessor processor, Object element, RefactoringArguments arguments, String[] affectedNatures, SharableParticipants shared) {
		if (fParticipants == null)
			init();
		
		EvaluationContext evalContext= createEvaluationContext(processor, element, affectedNatures);
		List result= new ArrayList();
		for (Iterator iter= fParticipants.iterator(); iter.hasNext();) {
			ParticipantDescriptor descriptor= (ParticipantDescriptor)iter.next();
			if (!descriptor.isEnabled()) {
				iter.remove();
			} else {
				try {
					if (descriptor.matches(evalContext)) {
						RefactoringParticipant participant= shared.get(descriptor);
						if (participant != null) {
							((ISharableParticipant)participant).addElement(element, arguments);
						} else {
							try {
								participant= descriptor.createParticipant();
								if (!fParticipantClass.isInstance(participant))
									throw new ClassCastException();
								if (participant.initialize(processor, element, arguments)) {
									participant.setDescriptor(descriptor);
									result.add(participant);
									if (participant instanceof ISharableParticipant)
										shared.put(descriptor, participant);
								}
							} catch(ClassCastException e) {
								status.addError(Messages.format(
									RefactoringCoreMessages.ParticipantExtensionPoint_participant_removed,  //$NON-NLS-1$
									descriptor.getName()));
								RefactoringCorePlugin.logErrorMessage(
									Messages.format(
										RefactoringCoreMessages.ParticipantExtensionPoint_wrong_type, //$NON-NLS-1$
										new String[] {descriptor.getName(), fParticipantClass.getName()}));
								iter.remove();
							}
						}
					}
				} catch (CoreException e) {
					status.addError(Messages.format(
						RefactoringCoreMessages.ParticipantExtensionPoint_participant_removed,  //$NON-NLS-1$
						descriptor.getName()));
					RefactoringCorePlugin.logRemovedParticipant(descriptor, e);
					iter.remove();
				}
			}
		}
		
		return (RefactoringParticipant[])result.toArray(new RefactoringParticipant[result.size()]);
	}
	
	private void init() {
		IExtensionRegistry registry= Platform.getExtensionRegistry();
		IConfigurationElement[] ces= registry.getConfigurationElementsFor(
			RefactoringCorePlugin.getPluginId(), 
			fParticipantID);
		fParticipants= new ArrayList(ces.length); 
		for (int i= 0; i < ces.length; i++) {
			ParticipantDescriptor descriptor= new ParticipantDescriptor(ces[i]);
			IStatus status= descriptor.checkSyntax();
			switch (status.getSeverity()) {
				case IStatus.ERROR:
					RefactoringCorePlugin.log(status);
					break;
				case IStatus.WARNING:
				case IStatus.INFO:
					RefactoringCorePlugin.log(status);
					// fall through
				default:
					fParticipants.add(descriptor);
			}
		}
	}
	
	//---- Helper methods ------------------------------------------------------------------
	
	private static EvaluationContext createEvaluationContext(RefactoringProcessor processor, Object element, String[] affectedNatures) {
		EvaluationContext result= new EvaluationContext(null, element);
		result.addVariable("element", element); //$NON-NLS-1$
		result.addVariable("affectedNatures", Arrays.asList(affectedNatures)); //$NON-NLS-1$
		result.addVariable("processorIdentifier", processor.getIdentifier()); //$NON-NLS-1$
		return result;
	}
}
