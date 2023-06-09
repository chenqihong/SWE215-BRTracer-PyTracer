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
package org.eclipse.team.internal.ccvs.ui.actions;

import java.lang.reflect.InvocationTargetException;
import org.eclipse.jface.action.IAction;
import org.eclipse.team.internal.ccvs.core.client.Command;
import org.eclipse.team.internal.ccvs.ui.ICVSUIConstants;
import org.eclipse.team.internal.ccvs.ui.operations.UpdateOperation;

/**
 * Action that runs an update without prompting the user for a tag.
 * 
 * @since 3.1
 */
public class UpdateSilentAction extends WorkspaceTraversalAction {
    
    /*
     * @see org.eclipse.team.internal.ccvs.ui.actions.WorkspaceAction#isEnabledForAddedResources()
     */
    protected boolean isEnabledForAddedResources() {
        return false;
    }

    /* (non-Javadoc)
     * @see org.eclipse.team.internal.ccvs.ui.actions.WorkspaceAction#isEnabledForNonExistantResources()
     */
    protected boolean isEnabledForNonExistantResources() {
        return true;
    }
    
	public void execute(IAction action) throws InterruptedException, InvocationTargetException {
		new UpdateOperation(getTargetPart(), getCVSResourceMappings(), Command.NO_LOCAL_OPTIONS, null /* no tag */).run();
	}
	
	public String getId() {
		return ICVSUIConstants.CMD_UPDATE;
	}
}
