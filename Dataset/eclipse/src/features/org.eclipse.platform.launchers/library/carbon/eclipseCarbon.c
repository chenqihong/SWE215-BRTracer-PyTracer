/*
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at 
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *    IBM Corporation - initial API and implementation
 * 	  Andre Weinand (OTI Labs)
 */
 
/* MacOS X Carbon specific logic for displaying the splash screen. */

#include "eclipseOS.h"

#include <sys/stat.h>
#include <sys/types.h>
#include <fcntl.h>
#include <ctype.h>
#include <pwd.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <unistd.h>

#include <CoreServices/CoreServices.h>
#include <Carbon/Carbon.h>

#include "NgCommon.h"
#include "NgImageData.h"
#include "NgWinBMPFileFormat.h"

#define startupJarName "startup.jar"
#define APP_PACKAGE_PATTERN ".app/Contents/MacOS/"
#define APP_PACKAGE "APP_PACKAGE"
#define JAVAROOT "JAVAROOT"

#define DEBUG 0

char *findCommand(char *command);
char* getProgramDir();

static void debug(const char *fmt, ...);
static void dumpArgs(char *tag, int argc, char* argv[]);
static PixMapHandle loadBMPImage(const char *image);
static void init();
static char *append(char *buffer, const char *s);
static char *appendc(char *buffer, char c);
static char *expandShell(char *arg, const char *appPackage, const char *javaRoot);
static char *my_strcasestr(const char *big, const char *little);

/* Global Variables */
char   dirSeparator  = '/';
char   pathSeparator = ':';
char*  consoleVM     = "java";
char*  defaultVM     = "java";
char*  shippedVMDir  = "jre/bin/";

/* Define the window system arguments for the various Java VMs. */
static char*  argVM_JAVA[] = { "-XstartOnFirstThread", NULL };

static int fgPid;
static FILE *fgConsoleLog;
static char *fgAppPackagePath;

extern int original_main(int argc, char* argv[]);
int main( int argc, char* argv[] ) {

	SInt32 systemVersion= 0;
	if (Gestalt(gestaltSystemVersion, &systemVersion) == noErr) {
		systemVersion &= 0xffff;
		if (systemVersion < 0x1020) {
			displayMessage("Eclipse requires Jaguar (Mac OS X >= 10.2)");
			return 0;
		}
	}

	fgConsoleLog= fopen("/dev/console", "w");
	fgPid= getpid();

	dumpArgs("start", argc, argv);
	
	if (argc > 1 && strncmp(argv[1], "-psn_", 5) == 0) {
	
		/* find path to application bundle (ignoring case) */
		char *pos= my_strcasestr(argv[0], APP_PACKAGE_PATTERN);
		if (pos != NULL) {
			int l= pos-argv[0] + 4;	// reserve space for ".app"
			fgAppPackagePath= malloc(l+1);
			strncpy(fgAppPackagePath, argv[0], l);
			fgAppPackagePath[l]= '\0';	// terminate result
		}
		
		/* Get the main bundle for the app */
		CFBundleRef mainBundle= CFBundleGetMainBundle();
		if (mainBundle != NULL) {
		
			/* Get an instance of the info plist.*/
			CFDictionaryRef bundleInfoDict= CFBundleGetInfoDictionary(mainBundle);
						
			/* If we succeeded, look for our property. */
			if (bundleInfoDict != NULL) {
				CFArrayRef ar= CFDictionaryGetValue(bundleInfoDict, CFSTR("Eclipse"));
				if (ar) {
					CFIndex size= CFArrayGetCount(ar);
					if (size > 0) {
						int i;
						char **old_argv= argv;
						argv= (char**) calloc(size+2, sizeof(char*));
						argc= 0;
						argv[argc++]= old_argv[0];
						for (i= 0; i < size; i++) {
							CFStringRef sr= (CFStringRef) CFArrayGetValueAtIndex (ar, i);
							CFIndex argStringSize= CFStringGetMaximumSizeForEncoding(CFStringGetLength(sr), kCFStringEncodingUTF8);
							char *s= malloc(argStringSize);
							if (CFStringGetCString(sr, s, argStringSize, kCFStringEncodingUTF8)) {
								argv[argc++]= expandShell(s, fgAppPackagePath, NULL);
							} else {
								fprintf(fgConsoleLog, "can't extract bytes\n");
							}
							//free(s);
						}
					}
				} else {
					fprintf(fgConsoleLog, "no Eclipse dict found\n");
				}
			} else {
				fprintf(fgConsoleLog, "no bundle dict found\n");
			}
		} else {
			fprintf(fgConsoleLog, "no bundle found\n");
		}
	}
	int exitcode= original_main(argc, argv);
	debug("<<<< exit(%d)\n", exitcode);
	fclose(fgConsoleLog);
	return exitcode;
}

/* Display a Message */
void displayMessage(char *message)
{
	CFStringRef inError, inDescription= NULL;

	/* try to break the message into a first sentence and the rest */
	char *pos= strstr(message, ". ");
	if (pos != NULL) {	
		char *to, *from, *buffer= calloc(pos-message+2, sizeof(char));
		/* copy and replace line separators with blanks */
		for (to= buffer, from= message; from <= pos; from++, to++) {
			char c= *from;
			if (c == '\n') c= ' ';
			*to= c;
		}
		inError= CFStringCreateWithCString(kCFAllocatorDefault, buffer, kCFStringEncodingASCII);
		free(buffer);
		inDescription= CFStringCreateWithCString(kCFAllocatorDefault, pos+2, kCFStringEncodingASCII);
	} else {
		inError= CFStringCreateWithCString(kCFAllocatorDefault, message, kCFStringEncodingASCII);
	}
	
	init();
	
	DialogRef outAlert;
	OSStatus status= CreateStandardAlert(kAlertStopAlert, inError, inDescription, NULL, &outAlert);
	if (status == noErr) {
		DialogItemIndex outItemHit;
		RunStandardAlert(outAlert, NULL, &outItemHit);
	} else {
		debug("eclipse: displayMessage: %s\n", message);
	}
	CFRelease(inError);
	if (inDescription != NULL)
		CFRelease(inDescription);
}

static void debug(const char *fmt, ...) {
#if DEBUG
	char buffer[200];
	va_list ap;
	va_start(ap, fmt);
	fprintf(fgConsoleLog, "%05d: ", fgPid);
	vfprintf(fgConsoleLog, fmt, ap);
	va_end(ap);
#endif
}

static void dumpArgs(char *tag, int argc, char* argv[]) {
#if DEBUG
	int i;
	if (argc < 0) {
		argc= 0;
		for (i= 0; argv[i] != NULL; i++)
			 argc++;
	}
	debug(">>>> %s:", tag);
	for (i= 0; i < argc && argv[i] != NULL; i++)
		fprintf(fgConsoleLog, " <%s>", argv[i]);
	fprintf(fgConsoleLog, "\n");
#endif
}

static void init() {
	static int initialized= 0;
	
	if (!initialized) {
		ProcessSerialNumber psn;
		if (GetCurrentProcess(&psn) == noErr)
			SetFrontProcess(&psn);
		ClearMenuBar();
		initialized= true;
	}
}

/* Initialize Window System
 *
 * Initialize Carbon.
 */
void initWindowSystem( int* pArgc, char* argv[], int showSplash )
{
	char *homeDir = getProgramDir();
	debug("install dir: %s\n", homeDir);
	if (homeDir != NULL)
		chdir(homeDir);
    
	if (showSplash)
		init();
}

static void eventLoopTimerProc(EventLoopTimerRef inTimer, void *inUserData) {
	QuitApplicationEventLoop();
}

/* Show the Splash Window
 *
 * Create the splash window, load the bitmap and display the splash window.
 */
int showSplash( char* timeoutString, char* featureImage )
{
	Rect wRect;
	WindowRef myWindow= NULL;
	int w, h, deviceWidth, deviceHeight;
	PixMap *pm;
	PixMapHandle pixmap;

	debug("featureImage: %s\n", featureImage);

	init();
    
	/* Determine the splash timeout value (in seconds). */
	if (timeoutString != NULL && strlen(timeoutString) > 0) {
		int timeout;
		if (sscanf(timeoutString, "%d", &timeout) == 1) {
			EventLoopTimerUPP upp= NewEventLoopTimerUPP(eventLoopTimerProc);
			InstallEventLoopTimer(GetMainEventLoop(), (EventTimerInterval) timeout, 0.0, upp, NULL, NULL);
		}
	}

	pixmap= loadBMPImage(featureImage);
	/* If the splash image data could not be loaded, return an error. */
	if (pixmap == NULL)
		return ENOENT;
		
	pm= *pixmap;
	w= pm->bounds.right;
	h= pm->bounds.bottom;

	GetAvailableWindowPositioningBounds(GetMainDevice(), &wRect);

	deviceWidth= wRect.right - wRect.left;
	deviceHeight= wRect.bottom - wRect.top;

	wRect.left+= (deviceWidth-w)/2;
	wRect.top+= (deviceHeight-h)/3;
	wRect.right= wRect.left + w;
	wRect.bottom= wRect.top + h;

	CreateNewWindow(kModalWindowClass, kWindowNoAttributes, &wRect, &myWindow);	
	if (myWindow != nil) {
		GrafPtr port= GetWindowPort(myWindow);
		SetPort(port);  /* set port to new window */
		SetRect(&wRect, 0, 0, w, h);
		ShowWindow(myWindow);
		CopyBits((BitMap*)pm, GetPortBitMapForCopyBits(port), &wRect, &wRect, srcCopy, NULL);

		RunApplicationEventLoop();

		DisposeWindow(myWindow);
	}

	return 0;
}


/* Get the window system specific VM arguments */
char** getArgVM( char* vm ) 
{
	char** result;

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

	/* Create a child process for the JVM. */	
	pid_t pid= fork();
	if (pid == 0) {

		dumpArgs("execv", -1, args);

		/* Child process ... start the JVM */
		execv(args[0], args);

		/* The JVM would not start ... return error code to parent process. */
		_exit(errno);
	}

	if (pid == -1)
		return errno;
	
	/* wait for it to terminate. */
	int exitCode;
	wait(&exitCode);

	exitCode= ((exitCode & 0x00ff) == 0 ? (exitCode >> 8) : exitCode);

	return exitCode;
}

/**
 * loadBMPImage
 * Create a QuickDraw PixMap representing the given BMP file.
 *
 * bmpPathname: absolute path and name to the bmp file
 *
 * returned value: the PixMapHandle newly created if successful. 0 otherwise.
 */
static PixMapHandle loadBMPImage (const char *bmpPathname) { 
	ng_stream_t in;
	ng_bitmap_image_t image;
	ng_err_t err= ERR_OK;
	PixMapHandle pixmap;
	PixMap *pm;

	NgInit();

	if (NgStreamInit(&in, (char*) bmpPathname) != ERR_OK) {
		NgError(ERR_NG, "Error can't open BMP file");
		return 0;
	}

	NgBitmapImageInit(&image);
	err= NgBmpDecoderReadImage (&in, &image);
	NgStreamClose(&in);

	if (err != ERR_OK) {
		NgBitmapImageFree(&image);
		return 0;
	}

	UBYTE4 srcDepth= NgBitmapImageBitCount(&image);
	if (srcDepth != 24) {	/* We only support image depth of 24 bits */
		NgBitmapImageFree(&image);
		NgError (ERR_NG, "Error unsupported depth - only support 24 bit");
		return 0;
	}

	pixmap= NewPixMap();	
	if (pixmap == 0) {
		NgBitmapImageFree(&image);
		NgError(ERR_NG, "Error XCreatePixmap failed");
		return 0;
	}

	pm= *pixmap;

	int width= (int)NgBitmapImageWidth(&image);
	int height= (int)NgBitmapImageHeight(&image);
	int rowBytes= width * 4;
	
	pm->bounds.right= width;
	pm->bounds.bottom= height;
	pm->rowBytes= rowBytes + 0x8000; 
	pm->baseAddr= NewPtr(rowBytes * height);
	pm->pixelType= RGBDirect;
	pm->pixelSize= 32;
	pm->cmpCount= 3;
	pm->cmpSize= 8;

	/* 24 bit source to direct screen destination */
	NgBitmapImageBlitDirectToDirect(NgBitmapImageImageData(&image), NgBitmapImageBytesPerRow(&image), width, height,
		(UBYTE1*)pm->baseAddr, pm->pixelSize, rowBytes, NgIsMSB(),
			0xff0000, 0x00ff00, 0x0000ff);

	NgBitmapImageFree(&image);

	return pixmap;
}

/*
 * Expand $APP_PACKAGE, $JAVA_HOME, and does tilde expansion.
 
	A word beginning with an unquoted tilde character (~) is
	subject to tilde expansion. All the characters up to a
	slash (/) or the end of the word are treated as a username
	and are replaced with the user's home directory. If the
	username is missing (as in ~/foobar), the tilde is
	replaced with the value of the HOME variable (the current
	user's home directory).
 */
static char *expandShell(char *arg, const char *appPackage, const char *javaRoot) {
	
	if (index(arg, '~') == NULL && index(arg, '$') == NULL)
		return arg;
	
	char *buffer= strdup("");
	char c, lastChar= ' ';
	const char *cp= arg;
	while ((c = *cp++) != NULL) {
		if (isspace(lastChar) && c == '~') {
			char name[100], *dir= NULL;
			int j= 0;
			for (; (c = *cp) != NULL; cp++) {
				if (! isalnum(c))
					break;
				name[j++]= c;
				lastChar= c;
			}
			name[j]= '\0';
			if (j > 0) {
				struct passwd *pw= getpwnam(name);
				if (pw != NULL)
					dir= pw->pw_dir;
			} else {
				dir= getenv("HOME");
			}
			if (dir != NULL)
				buffer= append(buffer, dir);
				
		} else if (c == '$') {
			int l= strlen(APP_PACKAGE);
			if (appPackage != NULL && strncmp(cp, APP_PACKAGE, l) == 0) {
				cp+= l;
				buffer= append(buffer, appPackage);
			} else {
				int l= strlen(JAVAROOT);
				if (javaRoot != NULL && strncmp(cp, JAVAROOT, l) == 0) {
					cp+= l;
					buffer= append(buffer, javaRoot);
				} else {
					buffer= appendc(buffer, c);
				}
			}
		} else
			buffer= appendc(buffer, c);
		lastChar= c;
	}
	return buffer;
}

static char *my_strcasestr(const char *big, const char *little) {
    char *cp, *s, *t;
    for (cp= (char*) big; *cp; cp++) {
        for (s= cp, t= (char*) little; *s && *t; s++, t++)
            if (toupper(*s) != toupper(*t))
                break;
        if (*t == '\0')
            return cp;
    }
    return NULL;
}

static char *append(char *buffer, const char *s) {
	int bl= strlen(buffer);
	int sl= strlen(s);
	buffer= realloc(buffer, bl+sl+1);
	strcpy(&buffer[bl], s);
	return buffer;
}

static char *appendc(char *buffer, char c) {
	int bl= strlen(buffer);
	buffer= realloc(buffer, bl+2);
	buffer[bl++]= c;
	buffer[bl]= '\0';
	return buffer;
}

