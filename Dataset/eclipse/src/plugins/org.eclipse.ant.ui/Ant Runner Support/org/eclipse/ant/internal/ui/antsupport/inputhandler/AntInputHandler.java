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
package org.eclipse.ant.internal.ui.antsupport.inputhandler;


import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.input.DefaultInputHandler;
import org.apache.tools.ant.input.InputRequest;
import org.eclipse.ant.internal.ui.antsupport.AntSupportMessages;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.widgets.Display;

/**
 * The default input handler when using Ant within Eclipse.
 * This is the class that will respond to <input> requests from
 * within an Ant build file.
 * If the build is occurring in Ant 1.6.0 and the -noinput option has been specified
 * this input handler will fail.
 */
public class AntInputHandler extends DefaultInputHandler {
	
	/* (non-Javadoc)
	 * @see org.apache.tools.ant.input.InputHandler#handleInput(org.apache.tools.ant.input.InputRequest)
	 */
	public void handleInput(InputRequest request) throws BuildException {
		if (System.getProperty("eclipse.ant.noInput") != null) { //$NON-NLS-1$
			throw new BuildException(AntSupportMessages.AntInputHandler_5); //$NON-NLS-1$
		}
		BuildException[] problem= new BuildException[1];
		Runnable runnable= getHandleInputRunnable(request, problem);
		Display.getDefault().syncExec(runnable);
		if (problem[0] != null) {
			throw problem[0];
		}
	}
	
	protected Runnable getHandleInputRunnable(final InputRequest request, final BuildException[] problem) {
		return new Runnable() {
			public void run() {
				String prompt = getPrompt(request);
		       	String title= AntSupportMessages.AntInputHandler_Ant_Input_Request_1; //$NON-NLS-1$
				IInputValidator validator= new IInputValidator() {
					private boolean fFirstValidation =true;
                    public String isValid(String value) {
						request.setInput(value);
						if (request.isInputValid()) {
							return null;
						} 
						if (fFirstValidation) {
						    fFirstValidation= false;
						    return ""; //$NON-NLS-1$
						}
						return AntSupportMessages.AntInputHandler_Invalid_input_2; //$NON-NLS-1$
					}
				};
		
				InputDialog dialog= new InputDialog(null, title, prompt, null, validator);
				if (dialog.open() != Window.OK) {
					problem[0]= new BuildException(AntSupportMessages.AntInputHandler_Unable_to_respond_to__input__request_4); //$NON-NLS-1$
				}
			}
		};
	}
}
