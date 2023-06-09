/*******************************************************************************
 * Copyright (c) 2003, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.internal.core.sourcelookup;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.zip.ZipFile;
import org.eclipse.core.resources.IResourceChangeEvent;
import org.eclipse.core.resources.IResourceChangeListener;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.debug.core.DebugEvent;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.IDebugEventSetListener;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchesListener;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.core.model.IProcess;

/**
 * Utility and supporting methods for source location. Most of these
 * utilities should be migrated to the DebugPlugin and LanuchManager
 * when this facility becomes public API.
 * <p>
 * This class is experimental and temporary.
 * </p>
 * @see org.eclipse.debug.core.sourcelookup.AbstractSourceLookupDirector
 * @since 3.0
 */
public class SourceLookupUtils {
		
	/**
	 * Cache of shared zip files. Zip files are closed
	 * when this class's plug-in is shutdown, when a project
	 * is about to be closed or deleted, when a launch is
	 * removed, and when a debug target or process terminates. 
	 */
	private static HashMap fgZipFileCache = new HashMap(5);
	private static ArchiveCleaner fgCleaner = null;
	
	/**
	 * Returns a zip file with the given name
	 * 
	 * @param name zip file name
	 * @return The zip file with the given name
	 * @exception IOException if unable to create the specified zip
	 * 	file
	 */
	public static ZipFile getZipFile(String name) throws IOException {
		synchronized (fgZipFileCache) {
			if (fgCleaner == null) {
				fgCleaner = new ArchiveCleaner();
				DebugPlugin.getDefault().addDebugEventListener(fgCleaner);
				DebugPlugin.getDefault().getLaunchManager().addLaunchListener(fgCleaner);
				ResourcesPlugin.getWorkspace().addResourceChangeListener(fgCleaner, IResourceChangeEvent.PRE_DELETE | IResourceChangeEvent.PRE_CLOSE);
			}
			ZipFile zip = (ZipFile)fgZipFileCache.get(name);
			if (zip == null) {
				zip = new ZipFile(name);
				fgZipFileCache.put(name, zip);
			}
			return zip;
		}
	}
	
	/**
	 * Closes all zip files that have been opened,
	 * and removes them from the zip file cache.
	 * This method is only to be called by the debug
	 * plug-in.
	 */
	public static void closeArchives() {
		synchronized (fgZipFileCache) {
			Iterator iter = fgZipFileCache.values().iterator();
			while (iter.hasNext()) {
				ZipFile file = (ZipFile)iter.next();
				synchronized (file) {
					try {
						file.close();
					} catch (IOException e) {
						DebugPlugin.log(e);
					}
				}
			}
			fgZipFileCache.clear();
		}
	}	
	
	/**
	 * Called when the debug plug-in shuts down.
	 */
	public static void shutdown() {
		closeArchives();
		if (fgCleaner != null) {
			DebugPlugin.getDefault().removeDebugEventListener(fgCleaner);
			DebugPlugin.getDefault().getLaunchManager().removeLaunchListener(fgCleaner);
			ResourcesPlugin.getWorkspace().removeResourceChangeListener(fgCleaner);			
		}
	}
	
	/**
	 * Clears the cache of open zip files when a debug target or process
	 * terminates, when a launch is removed, or when a project is about
	 * to be deleted or closed.
	 */
	static class ArchiveCleaner implements IDebugEventSetListener, ILaunchesListener, IResourceChangeListener {

		/* (non-Javadoc)
		 * @see org.eclipse.debug.core.IDebugEventSetListener#handleDebugEvents(org.eclipse.debug.core.DebugEvent[])
		 */
		public void handleDebugEvents(DebugEvent[] events) {
			for (int i = 0; i < events.length; i++) {
				DebugEvent event = events[i];
				if (event.getKind() == DebugEvent.TERMINATE) {
					Object source = event.getSource();
					if (source instanceof IDebugTarget || source instanceof IProcess) {
						SourceLookupUtils.closeArchives();
					}
				}
			}
		}

		/* (non-Javadoc)
		 * @see org.eclipse.debug.core.ILaunchesListener#launchesRemoved(org.eclipse.debug.core.ILaunch[])
		 */
		public void launchesRemoved(ILaunch[] launches) {
			SourceLookupUtils.closeArchives();
		}

		/* (non-Javadoc)
		 * @see org.eclipse.debug.core.ILaunchesListener#launchesAdded(org.eclipse.debug.core.ILaunch[])
		 */
		public void launchesAdded(ILaunch[] launches) {			
		}

		/* (non-Javadoc)
		 * @see org.eclipse.debug.core.ILaunchesListener#launchesChanged(org.eclipse.debug.core.ILaunch[])
		 */
		public void launchesChanged(ILaunch[] launches) {
		}

		/* (non-Javadoc)
		 * @see org.eclipse.core.resources.IResourceChangeListener#resourceChanged(org.eclipse.core.resources.IResourceChangeEvent)
		 */
		public void resourceChanged(IResourceChangeEvent event) {
			SourceLookupUtils.closeArchives();
		}
		
	}	
}
