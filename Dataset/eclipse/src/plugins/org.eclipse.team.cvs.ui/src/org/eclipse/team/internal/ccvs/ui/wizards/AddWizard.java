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
package org.eclipse.team.internal.ccvs.ui.wizards;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

import org.eclipse.core.internal.resources.mapping.ResourceTraversal;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.team.core.IFileContentManager;
import org.eclipse.team.core.Team;
import org.eclipse.team.internal.ccvs.core.ICVSFile;
import org.eclipse.team.internal.ccvs.core.resources.CVSWorkspaceRoot;
import org.eclipse.team.internal.ccvs.ui.*;
import org.eclipse.team.internal.ccvs.ui.operations.AddOperation;
import org.eclipse.ui.PlatformUI;

public class AddWizard extends ResizableWizard {

    private final AddOperation op;
    private final IFile[] unknowns;
    private CommitWizardFileTypePage fFileTypePage;

    public static void run(Shell shell, AddOperation op) throws InvocationTargetException, InterruptedException {
        try {
            // Prompt if there are files of unknown type being added
            IFile[] unknowns = getUnaddedWithUnknownFileType(op.getTraversals());
            if (unknowns.length == 0) {
                op.run();
            } else {
                AddWizard wizard = new AddWizard(op, unknowns);
                ResizableWizard.open(shell, wizard);
            }
        } catch (CoreException e) {
            throw new InvocationTargetException(e);
        }
    }
    
    private static IFile[] getUnaddedWithUnknownFileType(final ResourceTraversal[] traversals) throws InvocationTargetException, InterruptedException {
        final List unadded = new ArrayList();
        PlatformUI.getWorkbench().getProgressService().busyCursorWhile(new IRunnableWithProgress() {
            public void run(IProgressMonitor monitor) throws InvocationTargetException,
                    InterruptedException {
                final IFileContentManager manager= Team.getFileContentManager();
                for (int i = 0; i < traversals.length; i++) {
                    ResourceTraversal traversal = traversals[i];
                    IResource[] resources = traversal.getResources();
                    for (int j = 0; j < resources.length; j++) {
                        final IResource resource = resources[j];
                        try {
                            resource.accept(new IResourceVisitor() {       
                                public boolean visit(IResource resource) throws CoreException {
                                    if (resource.getType() == IResource.FILE) {
                                        ICVSFile file = CVSWorkspaceRoot.getCVSFileFor((IFile)resource);
                                        if (!file.isManaged()) {
                                            if (!file.isIgnored() || file.equals(resource)) {
                                                final String extension= ((IFile)resource).getFileExtension();
                                                if (manager.getType((IFile)resource) == Team.UNKNOWN) {
                                                    if (extension != null && !manager.isKnownExtension(extension)) {
                                                        unadded.add(resource);
                                                    } else {
                                                        final String name= file.getName();
                                                        if (extension == null && name != null && !manager.isKnownFilename(name))
                                                            unadded.add(resource);
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    return true;
                                }
                            }, traversal.getDepth(), false);
                        } catch (CoreException e) {
                            throw new InvocationTargetException(e);
                        }
                    }
                }
            }
        
        });
        return (IFile[]) unadded.toArray(new IFile[unadded.size()]);
    }
    
    public AddWizard(AddOperation op, IFile[] unknowns) {
        super("AddWizard", CVSUIPlugin.getPlugin().getDialogSettings()); //$NON-NLS-1$
        this.op = op;
        this.unknowns = unknowns;
        setWindowTitle(CVSUIMessages.AddWizard_0); //$NON-NLS-1$
        setDefaultPageImageDescriptor(CVSUIPlugin.getPlugin().getImageDescriptor(ICVSUIConstants.IMG_WIZBAN_NEW_LOCATION));
    }
    
    public void addPages() {
        
        final Collection names= new HashSet();
        final Collection extensions= new HashSet();
        getUnknownNamesAndExtension(unknowns, names, extensions);
        
        if (names.size() + extensions.size() > 0) {
            fFileTypePage= new CommitWizardFileTypePage(extensions, names); 
            addPage(fFileTypePage);
        }
        
        super.addPages();
    }
    
    private static void getUnknownNamesAndExtension(IFile[] files, Collection names, Collection extensions) {
        
        final IFileContentManager manager= Team.getFileContentManager();
        
        for (int i = 0; i < files.length; i++) {
            IFile file = files[i];
            
            final String extension= file.getFileExtension();
            if (extension != null && !manager.isKnownExtension(extension)) {
                extensions.add(extension);
            }
            
            final String name= file.getName();
            if (extension == null && name != null && !manager.isKnownFilename(name))
                names.add(name);
        }
    }
    
    public boolean performFinish() {
        final Map extensionsToSave= new HashMap();
        final Map extensionsNotToSave= new HashMap();
        
        fFileTypePage.getModesForExtensions(extensionsToSave, extensionsNotToSave);
        CommitWizardFileTypePage.saveExtensionMappings(extensionsToSave);
        op.addModesForExtensions(extensionsNotToSave);
        
        final Map namesToSave= new HashMap();
        final Map namesNotToSave= new HashMap();
        
        fFileTypePage.getModesForNames(namesToSave, namesNotToSave);
        CommitWizardFileTypePage.saveNameMappings(namesToSave);
        op.addModesForNames(namesNotToSave);
        
        try {
            op.run();
        } catch (InvocationTargetException e) {
            return false;
        } catch (InterruptedException e) {
            return false;
        }
        
        return super.performFinish();
    }
    
}