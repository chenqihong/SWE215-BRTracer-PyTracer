/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.update.internal.core;

import java.io.*;
import java.net.*;
import java.util.*;

import org.eclipse.core.runtime.*;
import org.eclipse.osgi.util.NLS;
import org.eclipse.update.core.*;
import org.eclipse.update.configurator.*;


/**
 * singleton pattern.
 * manages the error/recover log file
 */
public class ErrorRecoveryLog {

	public static final boolean RECOVERY_ON = false;

	private static final String ERROR_RECOVERY_LOG = "error_recovery.log"; //$NON-NLS-1$
	private static final String LOG_ENTRY_KEY = "LogEntry."; //$NON-NLS-1$
	private static final String RETURN_CARRIAGE = "\r\n"; //$NON-NLS-1$
	private static final String END_OF_FILE = "eof=eof"; //$NON-NLS-1$

	//
	public static final String START_INSTALL_LOG = 	"START_INSTALL_LOG"; //$NON-NLS-1$
	public static final String PLUGIN_ENTRY = 		"PLUGIN"; //$NON-NLS-1$
	public static final String FRAGMENT_ENTRY = 		"FRAGMENT";	 //$NON-NLS-1$
	public static final String BUNDLE_MANIFEST_ENTRY = 		"BUNDLE_MANIFEST";	 //$NON-NLS-1$
	public static final String BUNDLE_JAR_ENTRY = 			"BUNDLE";	 //$NON-NLS-1$
	public static final String FEATURE_ENTRY = 		"FEATURE"; //$NON-NLS-1$
	public static final String ALL_INSTALLED = 		"ALL_FEATURES_INSTALLED"; //$NON-NLS-1$
	public static final String RENAME_ENTRY = 		"RENAME"; //$NON-NLS-1$
	public static final String END_INSTALL_LOG = 	"END_INSTALL_LOG"; //$NON-NLS-1$
	public static final String START_REMOVE_LOG = 	"REMOVE_LOG"; //$NON-NLS-1$
	public static final String END_ABOUT_REMOVE =	"END_ABOUT_TO_REMOVE"; //$NON-NLS-1$
	public static final String DELETE_ENTRY = 		"DELETE"; //$NON-NLS-1$
	public static final String END_REMOVE_LOG = 		"END_REMOVE_LOG"; //$NON-NLS-1$

	public static boolean forceRemove = false;

	private static ErrorRecoveryLog inst;
	private FileWriter out;
	private int index;
	private List paths;
	
	private boolean open = false;
	private int nbOfOpen = 0;
	

	/**
	 * Constructor for ErrorRecoveryLog.
	 */
	private ErrorRecoveryLog() {
		super();
	}

	/**
	 * Singleton
	 */
	public static ErrorRecoveryLog getLog() {
		if (inst == null){
			inst = new ErrorRecoveryLog();
		}
		return inst;
	}

	/**
	 * get a unique identifer for the file, ensure uniqueness up to now
	 */
	public static String getLocalRandomIdentifier(String path) {
		
		if (path==null) return null;
		
		// verify if it will be a directory without creating the file
		// as it doesn't exist yet
		if (path.endsWith(File.separator) || path.endsWith("/")) //$NON-NLS-1$
			return path;
		File file = new File(path);
		String newName =
			UpdateManagerUtils.getLocalRandomIdentifier(file.getName(), new Date());
		while (new File(newName).exists()) {
			newName =
				UpdateManagerUtils.getLocalRandomIdentifier(file.getName(), new Date());
		}
		File newFile = new File(file.getParentFile(),newName);
		return newFile.getAbsolutePath();
	}

	/**
	 * returns the log file 
	 * We do not check if the file exists
	 */
	public File getRecoveryLogFile() {
		IPlatformConfiguration configuration =
			ConfiguratorUtils.getCurrentPlatformConfiguration();
		URL location = configuration.getConfigurationLocation();
		String locationString = location.getFile();
		File platformConfiguration = new File(locationString);
		if (!platformConfiguration.isDirectory()) platformConfiguration = platformConfiguration.getParentFile();
		return new File(platformConfiguration, ERROR_RECOVERY_LOG);
	}


	/**
	 * Open the log
	 */
	public void open(String logEntry) throws CoreException {
		if (open) {
			nbOfOpen++;			
			UpdateCore.warn("Open nested Error/Recovery log #"+nbOfOpen+":"+logEntry);				 //$NON-NLS-1$ //$NON-NLS-2$
			return;
		}
		
		File logFile = null;		
		try {
			logFile = getRecoveryLogFile();
			out = new FileWriter(logFile);
			index = 0;
			paths=null;
			open=true;
			nbOfOpen=0;
			UpdateCore.warn("Start new Error/Recovery log #"+nbOfOpen+":"+logEntry);							 //$NON-NLS-1$ //$NON-NLS-2$
		} catch (IOException e) {
			throw Utilities.newCoreException(
				NLS.bind(Messages.UpdateManagerUtils_UnableToLog, (new Object[] { logFile })),
				e);
		}
		
		append(logEntry);
	}

	/**
	 * Append the string to the log and flush
	 */
	public void append(String logEntry) throws CoreException {
		File logFile = null;
		try {
			if (!open) {
				UpdateCore.warn("Internal Error: The Error/Recovery log is not open:"+logEntry);				 //$NON-NLS-1$
				return;
			}

			StringBuffer buffer = new StringBuffer(LOG_ENTRY_KEY);
			buffer.append(index);
			buffer.append("="); //$NON-NLS-1$
			buffer.append(logEntry);
			buffer.append(RETURN_CARRIAGE);

			out.write(buffer.toString());
			out.flush();
			index++;
		} catch (IOException e) {
			throw Utilities.newCoreException(
				NLS.bind(Messages.UpdateManagerUtils_UnableToLog, (new Object[] { logFile })),
				e);
		}
	}

	/**
	 * Append the string to the log and flush
	 */
	public void appendPath(String logEntry, String path) throws CoreException {
		if (path == null)
			return;
		StringBuffer buffer = new StringBuffer(logEntry);
		buffer.append(" "); //$NON-NLS-1$
		buffer.append(path);
		append(buffer.toString());
		
		addPath(path);
	}

	/**
	 * Close any open recovery log
	 */
	public void close(String logEntry) throws CoreException {
		
		if (nbOfOpen>0){
			UpdateCore.warn("Close nested Error/Recovery log #"+nbOfOpen+":"+logEntry);			 //$NON-NLS-1$ //$NON-NLS-2$
			nbOfOpen--;			
			return;
		}			
		
		UpdateCore.warn("Close Error/Recovery log #"+nbOfOpen+":"+logEntry); //$NON-NLS-1$ //$NON-NLS-2$
		append(logEntry);
		if (out != null) {
			try {
				out.write(END_OF_FILE);
				out.flush();
				out.close();
			} catch (IOException e) { //eat the exception
			} finally {
				out = null;
				open=false;
			}
		}
	}

	/**
	 * Delete the file from the file system
	 */
	public void delete() {
		//File logFile = getRecoveryLogFile();
		getRecoveryLogFile();
		//if (logFile.exists())
			//logFile.delete();	
	}

	/**
	 * 
	 */
	private void addPath(String path){
		if (paths==null) paths = new ArrayList();
		paths.add(path);
	}
	
	/** 
	 * recover an install or remove that didn't finish
	 * Delete file for an unfinished delete
	 * Delete file for an unfinshed install if not all the files were installed
	 * Rename XML files for an install if all the files were installed but not renamed
	 */
	public IStatus recover(){
		
		IStatus mainStatus = createStatus(IStatus.OK,Messages.ErrorRecoveryLog_recoveringStatus,null); 
		MultiStatus multi = new MultiStatus(mainStatus.getPlugin(),mainStatus.getCode(),mainStatus.getMessage(),null);

		//check if recovery is on
		if (!RECOVERY_ON){
			UpdateCore.warn("Recovering is turned off. Abort recovery"); //$NON-NLS-1$
			return multi;
		}
		
		File logFile = getRecoveryLogFile();
		if (!logFile.exists()){
			multi.add(createStatus(IStatus.ERROR,Messages.ErrorRecoveryLog_cannotFindLogFile+logFile,null)); 
			return multi;
		}
		
		InputStream in = null;
		Properties prop = null;
		try {
			in = new FileInputStream(logFile);
			prop = new Properties();
			prop.load(in);
		} catch (IOException e){
			UpdateCore.warn("Unable to read:"+logFile,e); //$NON-NLS-1$
			multi.add(createStatus(IStatus.ERROR,Messages.ErrorRecoveryLog_noPropertyFile+logFile,e)); 
			return multi;
		} finally {
			if (in != null)
				try {
					in.close();
				} catch (IOException e1) {
				}
		}
		
		String eof = prop.getProperty("eof"); //$NON-NLS-1$
		if(eof!=null && eof.equals("eof")){ //$NON-NLS-1$
			// all is good
			delete();
			UpdateCore.warn("Found log file. Log file contains end-of-file. No need to process"); //$NON-NLS-1$
			multi.add(createStatus(IStatus.OK,null,null));
			return multi;
		}
		
		String recovery = prop.getProperty(LOG_ENTRY_KEY+"0"); //$NON-NLS-1$
		if (recovery==null){
			multi.add(createStatus(IStatus.ERROR,Messages.ErrorRecoveryLog_noLogEntry+logFile,null)); 
			return multi;			
		}
	
		if(recovery.equalsIgnoreCase(START_INSTALL_LOG)){
			multi.addAll(processRecoverInstall(prop));
			return multi;
		}
		
		if(recovery.equalsIgnoreCase(START_REMOVE_LOG)){
			multi.addAll(processRecoverRemove(prop));
			return multi;
		}

		multi.add(createStatus(IStatus.ERROR,Messages.ErrorRecoveryLog_noRecoveryToExecute+logFile,null)); 
		return multi;	
	}
	
	/*
	 * creates a Status
	 */
	private IStatus createStatus(int statusSeverity, String msg, Exception e){
		String id =
			UpdateCore.getPlugin().getBundle().getSymbolicName();
	
		StringBuffer completeString = new StringBuffer(""); //$NON-NLS-1$
		if (msg!=null)
			completeString.append(msg);
		if (e!=null){
			completeString.append("\r\n["); //$NON-NLS-1$
			completeString.append(e.toString());
			completeString.append("]\r\n"); //$NON-NLS-1$
		}
		return new Status(statusSeverity, id, IStatus.OK, completeString.toString(), e);
	}	
	
	/*
	 * 
	 */
	 private IStatus processRecoverInstall(Properties prop){
	 	
		IStatus mainStatus = createStatus(IStatus.OK,"",null); //$NON-NLS-1$
		MultiStatus multi = new MultiStatus(mainStatus.getPlugin(),mainStatus.getCode(),"",null); //$NON-NLS-1$
	 	
	 	Collection values = prop.values();
	 	
	 	if(values.contains(END_INSTALL_LOG)){
			// all is good
			delete();
			UpdateCore.warn("Found log file. Log file contains END_INSTALL_LOG. No need to process rename"); //$NON-NLS-1$
			multi.add(createStatus(IStatus.OK,null,null));
			return multi;
	 	}
	 	
	 	if (values.contains(ALL_INSTALLED) && !forceRemove){
	 		// finish install by renaming
	 		int index = 0;
	 		boolean found = false;
	 		String val = prop.getProperty(LOG_ENTRY_KEY+index);
	 		while(val!=null && !found){
	 			if(val.equalsIgnoreCase(ALL_INSTALLED)) found = true;
	 			IStatus renameStatus = processRename(val);
	 			UpdateCore.log(renameStatus);
	 			if(renameStatus.getSeverity()!=IStatus.OK){
	 				multi.add(renameStatus);
	 			}
	 			index++;
	 			val = prop.getProperty(LOG_ENTRY_KEY+index);	 			
	 		}
	 		if (val==null){
	 			UpdateCore.warn("Unable to find value for :"+LOG_ENTRY_KEY+index); //$NON-NLS-1$
	 			multi.add(createStatus(IStatus.ERROR,Messages.ErrorRecoveryLog_wrongLogFile+LOG_ENTRY_KEY+index,null)); 
				return multi;
	 		}
	 		// process recovery finished
	 		delete();
			UpdateCore.warn("Found log file. Successfully recovered by renaming. Feature is installed."); //$NON-NLS-1$
			multi.add(createStatus(IStatus.OK,null,null));
	 	} else {
	 		// remove all because install did not lay out all the files
	 		// or recovery is not allowed
	 		int index = 0;
	 		String val = prop.getProperty(LOG_ENTRY_KEY+index);
	 		while(val!=null){
	 			IStatus removeStatus = processRemove(val);
	 			UpdateCore.log(removeStatus);
	 			if(removeStatus.getSeverity()!=IStatus.OK){
	 				multi.addAll(removeStatus);
	 			}
	 			index++;
	 			val = prop.getProperty(LOG_ENTRY_KEY+index);	 			
	 		}
	 		// process recovery finished
	 		delete();
			UpdateCore.warn("Found log file. Successfully recovered by removing. Feature is removed."); //$NON-NLS-1$
			multi.add(createStatus(IStatus.OK,null,null));
	 	}
	 	return multi;
	 }
	 
	 /*
	  * 
	  */
	  private IStatus processRename(String val){
	  	
		// get the path
		int index = -1;
		String newFileName = null;
	  	if (val.startsWith(PLUGIN_ENTRY)){
	  		index = PLUGIN_ENTRY.length();
	  		newFileName= "plugin.xml"; //$NON-NLS-1$
	  	} else if (val.startsWith(BUNDLE_MANIFEST_ENTRY)){
	  		index = BUNDLE_MANIFEST_ENTRY.length();
	  		newFileName= "META-INF/MANIFEST.MF"; //$NON-NLS-1$
	  	}else if (val.startsWith(FRAGMENT_ENTRY)){
	  		index = FRAGMENT_ENTRY.length();
	  		newFileName= "fragment.xml"; //$NON-NLS-1$
	  	} else if (val.startsWith(FEATURE_ENTRY)){
	  		index = FEATURE_ENTRY.length();
	  		newFileName= "feature.xml"; //$NON-NLS-1$
	  	} else if (val.startsWith(BUNDLE_JAR_ENTRY)){
	  		index = BUNDLE_JAR_ENTRY.length();
	  	}
	  	
	  	if (index==-1){
	  		return createStatus(IStatus.ERROR,Messages.ErrorRecoveryLog_noAction+val,null); 
	  	}
	  	
	  	String oldName = val.substring(index+1);
	  	// oldname is com.pid/plugin#####.xml
	  	// or oldname is com.pid/pid_pver.jar######.tmp
	  	File oldFile = new File(oldName);
	  	File newFile;
	  	if(val.startsWith(BUNDLE_JAR_ENTRY)){
	  		newFile = new File(oldFile.getAbsolutePath().substring(0, oldFile.getAbsolutePath().lastIndexOf(".jar")+".jar".length())); //$NON-NLS-1$ //$NON-NLS-2$
	  	}else{
	  		newFile = new File(oldFile.getParentFile(),newFileName);
	  	}
	  	if (!oldFile.exists()){
	  		if (newFile.exists()){
	  			// ok the file has been renamed apparently
			  	return createStatus(IStatus.OK,Messages.ErrorRecoveryLog_fileAlreadyRenamed+newFile,null);	  				 
	  		} else {
	  			// the file doesn't exist, log as problem, and force the removal of the feature
		  		return createStatus(IStatus.ERROR,Messages.ErrorRecoveryLog_cannotFindFile+oldFile,null);	  			 
	  		}
	  	} 	
	  	
		boolean sucess = false;
		if (newFile.exists()) {
			UpdateManagerUtils.removeFromFileSystem(newFile);
			UpdateCore.warn("Removing already existing file:"+newFile); //$NON-NLS-1$
		}
		sucess = oldFile.renameTo(newFile);
			
		if(!sucess){
			String msg =(Messages.ErrorRecoveryLog_oldToNew+oldFile+newFile); 
			return createStatus(IStatus.ERROR,msg,null);
		}
		return createStatus(IStatus.OK,Messages.ErrorRecoveryLog_renamed+oldFile+Messages.ErrorRecoveryLog_to+newFile,null); 
	  }
	  
	 /*
	  * 
	  */
	  private IStatus processRemove(String val){
	  	
		IStatus mainStatus = createStatus(IStatus.OK,"",null); //$NON-NLS-1$
		MultiStatus multi = new MultiStatus(mainStatus.getPlugin(),mainStatus.getCode(),"",null);	  	 //$NON-NLS-1$
	  	
		// get the path
		int index = -1;
		if (val.startsWith(BUNDLE_JAR_ENTRY)){
	  		index = BUNDLE_JAR_ENTRY.length();
	  	}
	  	
	  	if (index==-1){
	  		return createStatus(IStatus.ERROR,Messages.ErrorRecoveryLog_noAction+val,null); 
	  	}
	  	
	  	String oldName = val.substring(index+1);
	  	File oldFile = new File(oldName);
	  	if (!oldFile.exists()){
  			// the jar or directory doesn't exist, log as problem, and force the removal of the feature
	  		multi.add(createStatus(IStatus.ERROR,Messages.ErrorRecoveryLog_cannotFindFile+oldFile,null));	  			 
	  		return multi;
	  	} 		  	
		multi.addAll(removeFromFileSystem(oldFile));

		return multi;
	  }	
	  
	/**
	 * return a multi status, 
	 * the children are the file that couldn't be removed
	 */
	public IStatus removeFromFileSystem(File file) {
		
		IStatus mainStatus = createStatus(IStatus.OK,"",null); //$NON-NLS-1$
		MultiStatus multi = new MultiStatus(mainStatus.getPlugin(),mainStatus.getCode(),"",null);		 //$NON-NLS-1$
		
		if (!file.exists()){
			multi.add(createStatus(IStatus.ERROR,Messages.ErrorRecoveryLog_noFiletoRemove+file,null)); 
			return multi;
		}
			
		if (file.isDirectory()) {
			String[] files = file.list();
			if (files != null) // be careful since file.list() can return null
				for (int i = 0; i < files.length; ++i){
					multi.addAll(removeFromFileSystem(new File(file, files[i])));
				}
		}
		
		if (!file.delete()) {
			String msg = "Unable to remove file" +file.getAbsolutePath(); //$NON-NLS-1$ 
			multi.add(createStatus(IStatus.ERROR,msg,null));
		}
		return multi;
	}	
	
	/*
	 * 
	 */
	 private IStatus processRecoverRemove(Properties prop){
	 	
		IStatus mainStatus = createStatus(IStatus.OK,"",null); //$NON-NLS-1$
		MultiStatus multi = new MultiStatus(mainStatus.getPlugin(),mainStatus.getCode(),"",null); //$NON-NLS-1$
	 	
	 	Collection values = prop.values();
	 	
	 	if(values.contains(END_REMOVE_LOG)){
			// all is good
			delete();
			UpdateCore.warn("Found log file. Log file contains END_REMOVE_LOG. No need to process rename"); //$NON-NLS-1$
			multi.add(createStatus(IStatus.OK,null,null));
			return multi;
	 	}
	 	
	 	if (!values.contains(END_ABOUT_REMOVE)){
	 		// finish install by renaming
 			multi.add(createStatus(IStatus.ERROR,Messages.ErrorRecoveryLog_removeFeature,null)); 
				return multi;
	 	} else {
	 		// finish install by renaming
	 		int index = 0;
	 		boolean found = false;
	 		String val = prop.getProperty(LOG_ENTRY_KEY+index);
	 		while(val!=null && !found){
	 			if(val.equalsIgnoreCase(END_ABOUT_REMOVE)) found = true;
	 			IStatus renameStatus = processRemove(val);
	 			UpdateCore.log(renameStatus);
	 			if(renameStatus.getSeverity()!=IStatus.OK){
	 				multi.add(renameStatus);
	 			}
	 			index++;
	 			val = prop.getProperty(LOG_ENTRY_KEY+index);	 			
	 		}
	 		if (val==null){
	 			UpdateCore.warn("Unable to find value for :"+LOG_ENTRY_KEY+index); //$NON-NLS-1$
	 			multi.add(createStatus(IStatus.ERROR,Messages.ErrorRecoveryLog_wrongLogFile+LOG_ENTRY_KEY+index,null)); 
				return multi;
	 		}
	 		// process recovery finished
	 		delete();
			UpdateCore.warn("Found log file. Successfully recovered by deleting. Feature is removed."); //$NON-NLS-1$
			multi.add(createStatus(IStatus.OK,null,null));
	 	}
	 	return multi;
	 }	    
}
