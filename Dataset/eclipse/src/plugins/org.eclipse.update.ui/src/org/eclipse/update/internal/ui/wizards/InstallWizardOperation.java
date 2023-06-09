package org.eclipse.update.internal.ui.wizards;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.custom.BusyIndicator;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.update.internal.ui.UpdateUI;
import org.eclipse.update.internal.ui.UpdateUIMessages;
import org.eclipse.update.ui.UpdateJob;

public class InstallWizardOperation {
	private UpdateJob job;
	private IJobChangeListener jobListener;
	private Shell shell;
	private Shell parentShell;

	public InstallWizardOperation() {
	}

	public void run(Shell parent, UpdateJob task) {
		shell = parent;
		if (shell.getParent()!=null && shell.getParent() instanceof Shell)
			parentShell = (Shell)shell.getParent();
		// cancel any existing jobs and remove listeners
		if (jobListener != null)
			Platform.getJobManager().removeJobChangeListener(jobListener);
		if (job != null)
			Platform.getJobManager().cancel(job);
		
		// then setup the new job and listener and schedule the job
		job = task;
		jobListener = new UpdateJobChangeListener();
		Platform.getJobManager().addJobChangeListener(jobListener);
		job.schedule();
	}
	
	private Shell getValidShell() {
		if (shell.isDisposed())
			return parentShell;
		return shell;
	}

	private class UpdateJobChangeListener extends JobChangeAdapter {
		public void done(final IJobChangeEvent event) {
			final Shell validShell = getValidShell();
			// the job listener is triggered when the search job is done, and proceeds to next wizard
			if (event.getJob() == job) {
				Platform.getJobManager().removeJobChangeListener(this);
				Platform.getJobManager().cancel(job);
				if (job.getStatus() == Status.CANCEL_STATUS)
					return;
				if (job.getStatus() != Status.OK_STATUS)
					getValidShell().getDisplay().syncExec(new Runnable() {
						public void run() {
							UpdateUI.log(job.getStatus(), true);
						}
					});

				validShell.getDisplay().asyncExec(new Runnable() {
					public void run() {
						validShell.getDisplay().beep();
						BusyIndicator.showWhile(validShell.getDisplay(), new Runnable() {
							public void run() {
								openInstallWizard2();
							}
						});
					}
				});
			}
		}

		private void openInstallWizard2() {
			if (InstallWizard2.isRunning()) {
				MessageDialog.openInformation(getValidShell(), UpdateUIMessages.InstallWizard_isRunningTitle, UpdateUIMessages.InstallWizard_isRunningInfo);
				return;
			}
            if (job.getUpdates() == null || job.getUpdates().length == 0) {
                if (job.isUpdate())
                    MessageDialog.openInformation(getValidShell(), UpdateUIMessages.InstallWizard_ReviewPage_zeroUpdates, UpdateUIMessages.InstallWizard_ReviewPage_zeroUpdates); 
                else
                    MessageDialog.openInformation(getValidShell(), UpdateUIMessages.InstallWizard_ReviewPage_zeroFeatures, UpdateUIMessages.InstallWizard_ReviewPage_zeroFeatures); 
                return;
            }
			InstallWizard2 wizard = new InstallWizard2(job.getSearchRequest(), job.getUpdates(), job.isUpdate());
			WizardDialog dialog = new ResizableInstallWizardDialog(getValidShell(), wizard, UpdateUIMessages.AutomaticUpdatesJob_Updates); 
			dialog.create();
			dialog.open();
		}
	}
}
