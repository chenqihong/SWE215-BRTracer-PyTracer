/*******************************************************************************
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Kevin Cornell (Rational Software Corporation)
 *******************************************************************************/
import java.io.*;

/**
 * This is a sample program to test the eclipse launcher.
 * 
 * If you are running this test program on Windows, the launcher
 * should use java.exe and not javaw.exe. This can be done using
 * either the -vm or -debug switch.
 */

public class Main {

	/* User args */
	static final String DEBUG = "-debug";
	static final String VM = "-vm";
	static final String VMARGS = "-vmargs";
	
	/* Internal args */
	static final String LAUNCHER = "-launcher";
	static final String NAME = "-name";
	static final String SHOWSPLASH = "-showsplash";
	static final String EXITDATA = "-exitdata";

	static boolean debug = false;
	static String vm = null;
	static String[] vmArgs = null;

	static String launcher = null;
	static String name = null;
	static String splashTimeOut = null;
	static String exitData  = null;
	static Process splashProcess = null;


static void setSharedData(String data) {
	String[] cmdArray = new String[] {launcher, NAME, name, EXITDATA, exitData, data};
	try {
		System.out.println("STARTUP: launching Eclipse to write shared data");
		for (int i = 0; i < cmdArray.length; i++) System.out.println(""+i+"] <"+cmdArray[i]+">");
		Runtime.getRuntime().exec(cmdArray).waitFor();
		System.out.println("STARTUP: done");
	} 
	catch (Exception e) {
		System.out.println("Eclipse: Exception in showSplash\n" + e);
	}
}

public static void main(String args[]) {	
	System.out.println("STARTUP: arguments received from launcher");
	for (int i = 0; i < args.length; i++) {
		System.out.println(""+i+"] <"+args[i]+">");
	}
	
	int index = 0;
	while (index < args.length) {
		if (args[index].equals(DEBUG)) debug = true;
		else if (args[index].equals(VM)) vm = args[++index];
		else if (args[index].equals(VMARGS)) {
			index++;
			vmArgs = new String[args.length - index]; 
			System.arraycopy(args, index, vmArgs, 0, vmArgs.length); 
			break; 
		}
		else if (args[index].equals(LAUNCHER)) launcher = args[++index];
		else if (args[index].equals(NAME)) name = args[++index];
		else if (args[index].equals(SHOWSPLASH)) splashTimeOut = args[++index];
		else if (args[index].equals(EXITDATA)) exitData = args[++index];
		index++;
	}
    
	/* Bring up the splash screen */
	String splashPath = new File("splash.bmp").getAbsoluteFile().toString();
	String[] cmdArray = new String[] {launcher, NAME, name, SHOWSPLASH, splashTimeOut, splashPath};  
	try {
		System.out.println( "STARTUP: launching Eclipse to show splash screen");
		for (int i = 0; i < cmdArray.length; i++) System.out.println(cmdArray[i]);
		splashProcess = Runtime.getRuntime().exec(cmdArray);
	} 
	catch (Exception e) {
		System.out.println("STARTUP: exception in launch\n" + e);
	}

	System.out.print("STARTUP: Enter return to remove splash screen");
	try {
   		System.in.read();
    } catch (Exception e) { System.out.println(e);}
	
    /* Bring down the splash screen */
    System.out.println("STARTUP: Removing splash screen");
	if (splashProcess != null) splashProcess.destroy();
    
    /* Exit - simple exit, restart, or report an error situation */
	System.out.println("STARTUP: Special exit codes are:");
	System.out.println("STARTUP: 0     - normal exit");
	System.out.println("STARTUP: 23    - restart launcher with same arguments");
	System.out.println("STARTUP: 24    - restart launcher with extra argument -newData=newValue in shared data");
	System.out.println("STARTUP: other - error occured, error message set in shared data");
	System.out.println("STARTUP: enter desired exit value:");
	int exitCode = 0;
	BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
	boolean done = false;
	while (!done) {
		try {
			String str = br.readLine();
			exitCode = Integer.parseInt(str);
			done = true;
		} catch (Exception e) {}
	}
    switch (exitCode) {
    	case 0: 
    	case 23: break;
    	case 24: {
    		/* Build a list of \n terminated arguments */
    		/* In our case, restart the VM with the startup jar */
    		String data = vm + '\n';
    		for (int i = 0; i < vmArgs.length; i++) data += vmArgs[i] + '\n';
    		for (int i = 0; i < args.length; i++) data += args[i] + '\n';
    		/* append new arguments */
    		data += "-DNEW_ARGUMENT=NEW_VALUE" + '\n';
    		setSharedData(data);
    		break;
    	}
    	default: setSharedData("Test Error Message for exit code "+exitCode);
    }
    
	System.out.print("STARTUP: Press a key to exit with value <"+exitCode+">");
	try {
   		System.in.read();
    } catch (Exception e) {}
    
	System.exit(exitCode);
}
}
