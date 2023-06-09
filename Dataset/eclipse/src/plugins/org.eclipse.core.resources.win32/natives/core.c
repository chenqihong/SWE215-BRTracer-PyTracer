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
#include <jni.h>
#include <io.h>
#include <sys/stat.h>
#include <windows.h>
#include <stdio.h>
#include "core.h"

/*
 * Converts a FILETIME in a java long (milliseconds).
 */
jlong fileTimeToMillis(FILETIME ft) {

	ULONGLONG millis = (((ULONGLONG) ft.dwHighDateTime) << 32) + ft.dwLowDateTime;
	millis = millis / 10000;
	// difference in milliseconds between
	// January 1, 1601 00:00:00 UTC (Windows FILETIME)
	// January 1, 1970 00:00:00 UTC (Java long)
	// = 11644473600000
	millis -= 11644473600000;
	return millis;
}

/*
 * Get a null-terminated byte array from a java byte array.
 * The returned bytearray needs to be freed when not used
 * anymore. Use free(result) to do that.
 */
jbyte* getByteArray(JNIEnv *env, jbyteArray target) {
	jsize n;
	jbyte *temp, *result;
	
	temp = (*env)->GetByteArrayElements(env, target, 0);
	n = (*env)->GetArrayLength(env, target);
	result = malloc((n+1) * sizeof(jbyte));
	memcpy(result, temp, n * sizeof(jbyte));
	result[n] = '\0';
	(*env)->ReleaseByteArrayElements(env, target, temp, 0);
	return result;
}

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalIsUnicode
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalIsUnicode
  (JNIEnv *env, jclass clazz) {
	HANDLE hModule;
  	OSVERSIONINFO osvi;
  	memset(&osvi, 0, sizeof(OSVERSIONINFO));
  	osvi.dwOSVersionInfoSize = sizeof(OSVERSIONINFO);
  	if (!GetVersionEx (&osvi)) 
    	return JNI_FALSE;
	// only Windows NT 4, Windows 2K and XP support Unicode API calls
    if (!(osvi.dwMajorVersion >= 5 || (osvi.dwPlatformId == VER_PLATFORM_WIN32_NT && osvi.dwMajorVersion == 4)))
		return JNI_FALSE;
	return JNI_TRUE;		
}

/*
 * Get a null-terminated short array from a java char array.
 * The returned short array needs to be freed when not used
 * anymore. Use free(result) to do that.
 */
jchar* getCharArray(JNIEnv *env, jcharArray target) {
	jsize n;
	jchar *temp, *result;
	
	temp = (*env)->GetCharArrayElements(env, target, 0);
	n = (*env)->GetArrayLength(env, target);
	result = malloc((n+1) * sizeof(jchar));
	memcpy(result, temp, n * sizeof(jchar));
	result[n] = 0;
	(*env)->ReleaseCharArrayElements(env, target, temp, 0);
	return result;
}

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalGetStatW
 * Signature: ([C)J
 */
JNIEXPORT jlong JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalGetStatW
   (JNIEnv *env, jclass clazz, jcharArray target) {
	int i;
	jsize size;
	HANDLE handle;
	WIN32_FIND_DATAW info;
	jlong result = 0; // 0 = failed
	jchar *name;
	
	name = getCharArray(env, target);	
	size = (*env)->GetArrayLength(env, target);
	// FindFirstFile does not work at the root level. However, we 
	// don't need it because the root will never change timestamp
	if (size == 3 && name[1] == ':' && name[2] == '\\') {
		free(name);
		return STAT_FOLDER | STAT_VALID;
	}
	handle = FindFirstFileW(name, &info);
	
	if (handle != INVALID_HANDLE_VALUE) {
		// select interesting information
		// lastModified
		result = fileTimeToMillis(info.ftLastWriteTime); // lower bits
		// valid stat
		result |= STAT_VALID;
		// folder or file?
		if (info.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)
			result |= STAT_FOLDER;
		// read-only?
		if (info.dwFileAttributes & FILE_ATTRIBUTE_READONLY)
			result |= STAT_READ_ONLY;
	}

	free(name);
	FindClose(handle);
	return result;
}
/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalGetStat
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalGetStat
   (JNIEnv *env, jclass clazz, jbyteArray target) {

	HANDLE handle;
	WIN32_FIND_DATA info;
	jlong result = 0; // 0 = failed
	jbyte *name;
	jsize size;

	name = getByteArray(env, target);
	size = (*env)->GetArrayLength(env, target);
	// FindFirstFile does not work at the root level. However, we 
	// don't need it because the root will never change timestamp
	if (size == 3 && name[1] == ':' && name[2] == '\\') {
		free(name);
		return STAT_FOLDER | STAT_VALID;
	}
	handle = FindFirstFile(name, &info);

	if (handle != INVALID_HANDLE_VALUE) {
		// select interesting information
		// lastModified
		result = fileTimeToMillis(info.ftLastWriteTime); // lower bits
		// valid stat
		result |= STAT_VALID;
		// folder or file?
		if (info.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY)
			result |= STAT_FOLDER;
		// read-only?
		if (info.dwFileAttributes & FILE_ATTRIBUTE_READONLY)
			result |= STAT_READ_ONLY;
	}

	free(name);
	FindClose(handle);
	return result;
}

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalCopyAttributes
 * Signature: ([B[BZ)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalCopyAttributes
   (JNIEnv *env, jclass clazz, jbyteArray source, jbyteArray destination, jboolean copyLastModified) {

	HANDLE handle;
	WIN32_FIND_DATA info;
	jbyte *sourceFile, *destinationFile;
	int success = 1;

	sourceFile = getByteArray(env, source);
	destinationFile = getByteArray(env, destination);

	handle = FindFirstFile(sourceFile, &info);
	if (handle != INVALID_HANDLE_VALUE) {
		success = SetFileAttributes(destinationFile, info.dwFileAttributes);
		if (success != 0 && copyLastModified) {
			// does not honor copyLastModified
			// call to SetFileTime should pass file handle instead of file name
			// success = SetFileTime(destinationFile, &info.ftCreationTime, &info.ftLastAccessTime, &info.ftLastWriteTime);
		}
	} else {
		success = 0;
	}

	free(sourceFile);
	free(destinationFile);
	FindClose(handle);
	return success;
}
/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalCopyAttributesW
 * Signature: ([C[CZ)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalCopyAttributesW
  (JNIEnv *env, jclass clazz, jcharArray source, jcharArray destination, jboolean copyLastModified) {

	HANDLE handle;
	WIN32_FIND_DATAW info;
	jchar *sourceFile, *destinationFile;
	int success = 1;

	sourceFile = getCharArray(env, source);
	destinationFile = getCharArray(env, destination);

	handle = FindFirstFileW(sourceFile, &info);
	
	if (handle != INVALID_HANDLE_VALUE) {
		success = SetFileAttributesW(destinationFile, info.dwFileAttributes);
		if (success != 0 && copyLastModified) {
			// does not honor copyLastModified
			// call to SetFileTime should pass file handle instead of file name
			// success = SetFileTime(destinationFile, &info.ftCreationTime, &info.ftLastAccessTime, &info.ftLastWriteTime);
		}
	} else {
		success = 0;
	}

	free(sourceFile);
	free(destinationFile);
	FindClose(handle);
	return success;
}  

/*
 * Class:     org_eclipse_ant_core_EclipseProject
 * Method:    internalCopyAttributes
 * Signature: ([B[BZ)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_ant_core_EclipseFileUtils_internalCopyAttributes
   (JNIEnv *env, jclass clazz, jbyteArray source, jbyteArray destination, jboolean copyLastModified) {

	// use the same implementation for both methods
	return Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalCopyAttributes
			(env, clazz, source, destination, copyLastModified);
}

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalGetResourceAttributes
 * Signature: ([BLorg/eclipse/core/internal/resources/ResourceAttributes;)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalGetResourceAttributes
  (JNIEnv *env, jclass clazz, jcharArray target, jobject obj) {

    jmethodID mid;
    jboolean archive, readOnly, success;
    jclass cls; 
	HANDLE handle;
	WIN32_FIND_DATA info;
	jbyte *name;

	name = getByteArray(env, target);
	handle = FindFirstFile(name, &info);
	readOnly = JNI_FALSE;
	archive = JNI_FALSE;
	success = JNI_TRUE;

	if (handle != INVALID_HANDLE_VALUE) {
		// select interesting information
		/* is read-only? */
		if (info.dwFileAttributes & FILE_ATTRIBUTE_READONLY)
			readOnly = JNI_TRUE;
		// archive?
		if (info.dwFileAttributes & FILE_ATTRIBUTE_ARCHIVE)
			archive = JNI_TRUE;
	}
		
    /* set the values in ResourceAttribute */
    cls = (*env)->GetObjectClass(env, obj);
    mid = (*env)->GetMethodID(env, cls, "setArchive", "(Z)V");
    if (mid != 0) {
	    (*env)->CallVoidMethod(env, obj, mid, archive);
	} else {
		success = JNI_FALSE;
	}
    mid = (*env)->GetMethodID(env, cls, "setReadOnly", "(Z)V");
    if (mid != 0) {
	    (*env)->CallVoidMethod(env, obj, mid, readOnly);
	} else {
		success = JNI_FALSE;
	}
  
	free(name);
	FindClose(handle);
    return success;
}

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalGetResourceAttributesW
 * Signature: ([BLorg/eclipse/core/internal/resources/ResourceAttributes;)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalGetResourceAttributesW
  (JNIEnv *env, jclass clazz, jcharArray target, jobject obj) {

    jmethodID mid;
    jboolean archive, readOnly, success;
    jclass cls; 
    DWORD dwAttrs;
	HANDLE handle;
	WIN32_FIND_DATAW FileData;
	jchar *name;

	name = getCharArray(env, target);
	handle = FindFirstFileW(name, &FileData);
	readOnly = JNI_FALSE;
	archive = JNI_FALSE;
	success = JNI_TRUE;

	if (handle != INVALID_HANDLE_VALUE) {
		// select interesting information
		if (FileData.dwFileAttributes == (DWORD)-1)
			return JNI_FALSE;
		/* is read-only? */
		if (FileData.dwFileAttributes & FILE_ATTRIBUTE_READONLY) {
			readOnly = JNI_TRUE;
		}
		// archive?
		if (FileData.dwFileAttributes & FILE_ATTRIBUTE_ARCHIVE) {
			archive = JNI_TRUE;
		}
	} else {
		success = JNI_FALSE;
	}
		
    /* set the values in ResourceAttribute */
    cls = (*env)->GetObjectClass(env, obj);
    mid = (*env)->GetMethodID(env, cls, "setArchive", "(Z)V");
    if (mid != 0) {
	    (*env)->CallVoidMethod(env, obj, mid, archive);
	} else {
		success = JNI_FALSE;
	}
    mid = (*env)->GetMethodID(env, cls, "setReadOnly", "(Z)V");
    if (mid != 0) {
	    (*env)->CallVoidMethod(env, obj, mid, readOnly);
	} else {
		success = JNI_FALSE;
	}
	free(name);
	FindClose(handle);
    return success;
}

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalSetResourceAttributes
 * Signature: ([BLorg/eclipse/core/resources/ResourceAttributes;)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalSetResourceAttributes
  (JNIEnv *env, jclass clazz, jcharArray target, jobject obj) {

	HANDLE handle;
	jbyte *targetFile;
    jmethodID mid;
	int success = JNI_TRUE;
	DWORD attributes;
    jboolean readOnly, hidden, archive;
    jclass cls;

    /* find out if we need to set the readonly bit */
    cls = (*env)->GetObjectClass(env, obj);
    mid = (*env)->GetMethodID(env, cls, "isReadOnly", "()Z");
    if (mid == 0)
    	return JNI_FALSE;
    readOnly = (*env)->CallBooleanMethod(env, obj, mid);

    /* find out if we need to set the archive bit */
    mid = (*env)->GetMethodID(env, cls, "isArchive", "()Z");
    if (mid == 0)
		return JNI_FALSE;
    archive = (*env)->CallBooleanMethod(env, obj, mid);

	targetFile = getByteArray(env, target);
	attributes = GetFileAttributes(targetFile);
	if (attributes == (DWORD)-1) {
		free(targetFile);
		return JNI_FALSE;
	}

	if (readOnly)
		attributes = attributes | FILE_ATTRIBUTE_READONLY;
	else
		attributes = attributes & ~FILE_ATTRIBUTE_READONLY;
	if (archive)
		attributes = attributes | FILE_ATTRIBUTE_ARCHIVE;
	else
		attributes = attributes & ~FILE_ATTRIBUTE_ARCHIVE;
	
	success = SetFileAttributes(targetFile, attributes);

	free(targetFile);
	return success;
}

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalSetResourceAttributesW
 * Signature: ([CLorg/eclipse/core/resources/ResourceAttributes;)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalSetResourceAttributesW
  (JNIEnv *env, jclass clazz, jcharArray target, jobject obj) {

	HANDLE handle;
    jmethodID mid;
	jchar *targetFile;
	int success = JNI_TRUE;
	DWORD attributes;
    jboolean readOnly, hidden, archive;
    jclass cls;

    /* find out if we need to set the readonly bit */
    cls = (*env)->GetObjectClass(env, obj);
    mid = (*env)->GetMethodID(env, cls, "isReadOnly", "()Z");
    if (mid == 0)
    	return JNI_FALSE;
    readOnly = (*env)->CallBooleanMethod(env, obj, mid);

    /* find out if we need to set the archive bit */
    mid = (*env)->GetMethodID(env, cls, "isArchive", "()Z");
    if (mid == 0)
		return JNI_FALSE;
    archive = (*env)->CallBooleanMethod(env, obj, mid);

	targetFile = getCharArray(env, target);
	attributes = GetFileAttributesW(targetFile);
	if (attributes == (DWORD)-1) {
		free(targetFile);
		return JNI_FALSE;
	}

	if (readOnly)
		attributes = attributes | FILE_ATTRIBUTE_READONLY;
	else
		attributes = attributes & ~FILE_ATTRIBUTE_READONLY;
	if (archive)
		attributes = attributes | FILE_ATTRIBUTE_ARCHIVE;
	else
		attributes = attributes & ~FILE_ATTRIBUTE_ARCHIVE;
	
	success = SetFileAttributesW(targetFile, attributes);

	free(targetFile);
	return success;
}
