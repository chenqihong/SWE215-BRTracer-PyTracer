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
 *     Tom Tromey (Red Hat, Inc.)
 *******************************************************************************/

#include "eclipseOS.h"
#include "eclipseUtil.h"

#include <signal.h>
#include <unistd.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/wait.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <locale.h>

#include <gtk/gtk.h>
#include <gdk-pixbuf/gdk-pixbuf.h>

/* Global Variables */
char   dirSeparator  = '/';
char   pathSeparator = ':';
char*  consoleVM     = "java";
char*  defaultVM     = "java";
char*  shippedVMDir  = "jre/bin/";

/* Define the special arguments for the various Java VMs. */
static char*  argVM_JAVA[]        = { NULL };
static char*  argVM_J9[]          = { "-jit", "-mca:1024", "-mco:1024", "-mn:256", "-mo:4096", 
									  "-moi:16384", "-mx:262144", "-ms:16", "-mr:16", NULL };


/* Define local variables . */
static int          saveArgc   = 0;
static char**       saveArgv   = 0;
static gboolean     gtkInitialized = FALSE;								

/* Local functions */
static gboolean splashTimeout(gpointer data);
#ifdef MOZILLA_FIX
static void fixEnvForMozilla();
#endif /* MOZILLA_FIX */

/* Display a Message */
void displayMessage(char* message)
{
	GtkWidget* dialog;
	
    /* If GTK has not been initialized yet, do it now. */
    if (!gtkInitialized) 
    {
		initWindowSystem( &saveArgc, saveArgv, 1 );
    }

  	dialog = gtk_message_dialog_new(NULL, GTK_DIALOG_DESTROY_WITH_PARENT,
				   					GTK_MESSAGE_ERROR, GTK_BUTTONS_CLOSE,
				   					"%s", message);
  	gtk_dialog_run(GTK_DIALOG (dialog));
  	gtk_widget_destroy(dialog);
}


/* Initialize the Window System */
void initWindowSystem(int* pArgc, char* argv[], int showSplash)
{
    
    /* Save the arguments in case displayMessage() is called in the main launcher. */ 
    if (saveArgv == 0)
    {
    	saveArgc = *pArgc;
    	saveArgv =  argv;
    }  

    
    /* If the splash screen is going to be displayed by this process */
    if (showSplash)
    {
    	/* Initialize GTK. */
  		gtk_set_locale();
  		gtk_init(pArgc, &argv);
  		gdk_set_program_class(officialName);
  		gtkInitialized = TRUE;
	}
}
	
/* Create and DIsplay the Splash Window */
int showSplash( char* timeoutString, char* featureImage )
{
	GdkPixbuf* imageData = NULL;
	GtkWidget* image;
  	GtkWindow* main;
  	int        timeout = 0;

	/* Determine the splash timeout value (in seconds). */
	if (timeoutString != NULL && strlen( timeoutString ) > 0)
	{
	    sscanf( timeoutString, "%d", &timeout );
	}

    /* Load the feature specific splash image data if defined. */
    if (featureImage != NULL)
    {
    	imageData = gdk_pixbuf_new_from_file(featureImage, NULL);
    }
   
    /* If the splash image data could not be loaded, return an error. */
    if (imageData == NULL)
    	return ENOENT;
    
    /* Create the image from its data. */
    image = gtk_image_new_from_pixbuf(imageData);

	/* Create a top level window for the image. */
 	main = GTK_WINDOW(gtk_window_new(GTK_WINDOW_TOPLEVEL));
	gtk_window_set_title(main, officialName);
  	gtk_container_add(GTK_CONTAINER(main), GTK_WIDGET(image));
  	
  	/* Remove window decorations and centre the window on the display. */
  	gtk_window_set_decorated(main, FALSE);
  	gtk_window_set_position(main, GTK_WIN_POS_CENTER);

    /* Set the background pixmap to NULL to avoid a gray flash when the image appears. */
    gtk_widget_realize(GTK_WIDGET(main));
    gdk_window_set_back_pixmap(GTK_WIDGET(main)->window, NULL, FALSE);

	/* If a timeout for the splash window was given */
	if (timeout != 0)
	{
		/* Add a timeout (in milliseconds) to bring down the splash screen. */
    	gtk_timeout_add((timeout * 1000), splashTimeout, (gpointer) main);
	}

	/* Show the window and wait for the timeout (or until the process is terminated). */
	gtk_widget_show_all(GTK_WIDGET (main));
	gtk_main ();

  	return 0;
}


/* Get the window system specific VM arguments */
char** getArgVM( char* vm ) 
{
    char** result;
    char*  version;

    if (isJ9VM( vm )) 
        return argVM_J9;
    
    /* Use the default arguments for a standard Java VM */
    result = argVM_JAVA;
    return result;
}


/* Start the Java VM 
 *
 * This method is called to start the Java virtual machine and to wait until it
 * terminates. The function returns the exit code from the JVM.
 */
int startJavaVM( char* args[] ) 
{
	int     jvmExitCode = 1;
  	pid_t   jvmProcess;
  	int     exitCode;

#ifdef MOZILLA_FIX
	fixEnvForMozilla();
#endif /* MOZILLA_FIX */

	jvmProcess = fork();
  	if (jvmProcess == 0) 
    {
    	/* Child process ... start the JVM */
      	execv(args[0], args);

      	/* The JVM would not start ... return error code to parent process. */
      	_exit(errno);
    }

	/* If the JVM is still running, wait for it to terminate. */
	if (jvmProcess != 0)
	{
		wait(&exitCode);
      	if (WIFEXITED(exitCode))
			jvmExitCode = WEXITSTATUS(exitCode);
    }

  return jvmExitCode;
}

/* Splash Timeout - Hide the main window and exit the main loop. */
static gboolean splashTimeout(gpointer data)
{
	GtkWidget* main = GTK_WIDGET(data);
  	gtk_widget_hide(main);
  	gtk_main_quit();
  	return FALSE;
}

/* Set the environmnent required by the SWT Browser widget to bind to Mozilla. 
 * The SWT Browser widget relies on Mozilla on Linux. The LD_LIBRARY_PATH
 * and the Mozilla environment variable MOZILLA_FIVE_HOME must point
 * to the installation directory of Mozilla.
 * 
 * 1. Use the location set by MOZILLA_FIVE_HOME if it is defined
 * 2. Parse the file /etc/gre.conf if it is defined. This file is
 *    set by the RedtHat RPM manager.
 */
#ifdef MOZILLA_FIX
void fixEnvForMozilla() {
	static int fixed = 0;
	if (fixed) return; 
	{
		char *ldPath = (char*)getenv("LD_LIBRARY_PATH");
		char *mozillaFiveHome = (char*)getenv("MOZILLA_FIVE_HOME");
		char *grePath = NULL; /* Gecko Runtime Environment Location */
		char *XPCOM_LIB = "libxpcom.so";
		fixed = 1;
		/* Always dup the string so we can free later */
		if (ldPath != NULL) ldPath = strdup(ldPath);
		else ldPath = strdup("");
		
		/* MOZILLA_FIVE_HOME (if defined) points to the Mozilla
		 * install directory. Don't look any further if it is set.
		 */
		if (mozillaFiveHome != NULL) 
		{
			grePath = strdup(mozillaFiveHome);
		}

		/* The file gre.conf (if available) points to the
		 * Mozilla install directory. Don't look any further if 
		 * it is set.
		 */
		if (grePath == NULL)
		{
			struct stat buf;
			FILE *file = NULL;
			if (stat("/etc/gre.conf", &buf) == 0)
			{
				file = fopen("/etc/gre.conf", "r");
			}
			else if (stat("/etc/gre.d/gre.conf", &buf) == 0)
			{
				file = fopen("/etc/gre.d/gre.conf", "r");
			}
			if (file != NULL)
			{
				char buffer[1024];
				char path[1024];
				while (fgets(buffer, 1024, file) != NULL)
				{
					if (sscanf(buffer, "GRE_PATH=%s", path) == 1)
					{
						grePath = strdup(path);
						break;
					}
				}
				fclose(file);
			}
		}
		
		if (grePath != NULL)
		{
			ldPath = (char*)realloc(ldPath, strlen(ldPath) + strlen(grePath) + 2);
			if (strlen(ldPath) > 0) strcat(ldPath, ":");
			strcat(ldPath, grePath);
			setenv("LD_LIBRARY_PATH", ldPath, 1);
			
			if (mozillaFiveHome == NULL) setenv("MOZILLA_FIVE_HOME", grePath, 1);
			free(grePath);
		}
		free(ldPath);
	}
}
#endif /* MOZILLA_FIX */
