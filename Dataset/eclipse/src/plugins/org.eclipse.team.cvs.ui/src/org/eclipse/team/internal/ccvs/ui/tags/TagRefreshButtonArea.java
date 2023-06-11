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
package org.eclipse.team.internal.ccvs.ui.tags;

import java.lang.reflect.InvocationTargetException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.operation.IRunnableContext;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.osgi.util.NLS;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.*;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.CVSTag;
import org.eclipse.team.internal.ccvs.core.util.Assert;
import org.eclipse.team.internal.ccvs.ui.*;
import org.eclipse.team.internal.ui.PixelConverter;
import org.eclipse.team.internal.ui.SWTUtils;
import org.eclipse.team.internal.ui.dialogs.DialogArea;
import org.eclipse.ui.PlatformUI;

/**
 * An area that displays the Refresh and Configure Tags buttons
 */
public class TagRefreshButtonArea extends DialogArea {
    
    private TagSource tagSource;
    private final Shell shell;
    private Button refreshButton;
    private IRunnableContext context;
	private Label fMessageLabel;
    private final Listener addDateTagListener;

    public TagRefreshButtonArea(Shell shell, TagSource tagSource, Listener addDateTagListener) {
        this.addDateTagListener = addDateTagListener;
        Assert.isNotNull(shell);
        Assert.isNotNull(tagSource);
        this.shell = shell;
        this.tagSource = tagSource;
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.team.internal.ui.dialogs.DialogArea#createArea(org.eclipse.swt.widgets.Composite)
     */
    public void createArea(Composite parent) {
    	
    	final PixelConverter converter= SWTUtils.createDialogPixelConverter(parent);
    	
    	final Composite buttonComp = new Composite(parent, SWT.NONE);
	 	buttonComp.setLayoutData(SWTUtils.createHFillGridData());//SWT.DEFAULT, SWT.DEFAULT, SWT.END, SWT.TOP, false, false));
	 	buttonComp.setLayout(SWTUtils.createGridLayout(4, converter, SWTUtils.MARGINS_NONE));
	 	
		fMessageLabel= SWTUtils.createLabel(buttonComp, null);
		refreshButton = new Button(buttonComp, SWT.PUSH);
		refreshButton.setText (CVSUIMessages.TagConfigurationDialog_20);
		
		final Button configureTagsButton = new Button(buttonComp, SWT.PUSH);
		configureTagsButton.setText (CVSUIMessages.TagConfigurationDialog_21);
        
        Button addDateTagButton = null;
        int buttonWidth;
        if (addDateTagListener != null) {
            addDateTagButton = new Button(buttonComp, SWT.PUSH);
            addDateTagButton.setText (CVSUIMessages.TagConfigurationDialog_AddDateTag);
            Dialog.applyDialogFont(buttonComp);
            buttonWidth= SWTUtils.calculateControlSize(converter, new Button [] { addDateTagButton, configureTagsButton, refreshButton });
            addDateTagButton.setLayoutData(SWTUtils.createGridData(buttonWidth, SWT.DEFAULT, SWT.END, SWT.CENTER, false, false));
            addDateTagButton.addListener(SWT.Selection, addDateTagListener);   
        } else {
            Dialog.applyDialogFont(buttonComp);
            buttonWidth= SWTUtils.calculateControlSize(converter, new Button [] { configureTagsButton, refreshButton });
        }
		
		refreshButton.setLayoutData(SWTUtils.createGridData(buttonWidth, SWT.DEFAULT, SWT.END, SWT.CENTER, false, false));
		configureTagsButton.setLayoutData(SWTUtils.createGridData(buttonWidth, SWT.DEFAULT, SWT.END, SWT.CENTER, false, false));
		
		refreshButton.addListener(SWT.Selection, new Listener() {
			public void handleEvent(Event event) {
				refresh(false);						
			}
		});
	 	
		configureTagsButton.addListener(SWT.Selection, new Listener() {
				public void handleEvent(Event event) {
					TagConfigurationDialog d = new TagConfigurationDialog(shell, tagSource);
					d.open();
				}
			});  
		
        PlatformUI.getWorkbench().getHelpSystem().setHelp(refreshButton, IHelpContextIds.TAG_CONFIGURATION_REFRESHACTION);
        PlatformUI.getWorkbench().getHelpSystem().setHelp(configureTagsButton, IHelpContextIds.TAG_CONFIGURATION_OVERVIEW);		
		Dialog.applyDialogFont(buttonComp);
    }
    
    
    public void refresh(final boolean background) {
		try {
			getRunnableContext().run(true, true, new IRunnableWithProgress() {
				public void run(IProgressMonitor monitor) throws InvocationTargetException, InterruptedException {
					try {
						setBusy(true);
						Display.getDefault().asyncExec(new Runnable() {
							public void run() {
								fMessageLabel.setText(CVSUIMessages.TagRefreshButtonArea_6); //$NON-NLS-1$
							}
						});
						monitor.beginTask(CVSUIMessages.TagRefreshButtonArea_5, 100); //$NON-NLS-1$
						final CVSTag[] tags = tagSource.refresh(false, Policy.subMonitorFor(monitor, 70));
						Display.getDefault().asyncExec(new Runnable() {
							public void run() {
								fMessageLabel.setText(background && tags.length == 0 ? CVSUIMessages.TagRefreshButtonArea_7 : ""); //$NON-NLS-1$ //$NON-NLS-2$
							}
						});
						if (!background && tags.length == 0 && promptForBestEffort()) {
							tagSource.refresh(true, Policy.subMonitorFor(monitor, 30));
						}
					} catch (TeamException e) {
						throw new InvocationTargetException(e);
					} finally {
						setBusy(false);
						monitor.done();
					}
				}
			});
		} catch (InterruptedException e) {
			// operation cancelled
		} catch (InvocationTargetException e) {
			CVSUIPlugin.openError(shell, CVSUIMessages.TagConfigurationDialog_14, null, e); //$NON-NLS-1$
		}
	}
    
    private void setBusy(final boolean busy) {
    	if (shell != null && !shell.isDisposed())
			shell.getDisplay().asyncExec(new Runnable() {
				public void run() {
					refreshButton.setEnabled(!busy);
				}
			});
    }
	
    private boolean promptForBestEffort() {
        final boolean[] prompt = new boolean[] { false };
        shell.getDisplay().syncExec(new Runnable() {
            public void run() {
		        MessageDialog dialog = new MessageDialog(shell, CVSUIMessages.TagRefreshButtonArea_0, null, //$NON-NLS-1$
		                getNoTagsFoundMessage(),
		                MessageDialog.INFORMATION,
		                new String[] {
		            		CVSUIMessages.TagRefreshButtonArea_1, //$NON-NLS-1$
		            		CVSUIMessages.TagRefreshButtonArea_2, //$NON-NLS-1$
		            		CVSUIMessages.TagRefreshButtonArea_3
		        		}, 1);
		        int code = dialog.open();
		        if (code == 0) {
		            prompt[0] = true;
		        } else if (code == 1) {
					TagConfigurationDialog d = new TagConfigurationDialog(shell, tagSource);
					d.open();
		        }

            }
        });
        return prompt[0];
    }
    
    private String getNoTagsFoundMessage() {
        return NLS.bind(CVSUIMessages.TagRefreshButtonArea_4, new String[] { tagSource.getShortDescription() }); //$NON-NLS-1$
    }
    
    public void setTagSource(TagSource tagSource) {
        Assert.isNotNull(tagSource);
        this.tagSource = tagSource;
    }

    public IRunnableContext getRunnableContext() {
        if (context == null)
            return PlatformUI.getWorkbench().getProgressService();
        return context;
    }
    
    public void setRunnableContext(IRunnableContext context) {
        this.context = context;
    }
}