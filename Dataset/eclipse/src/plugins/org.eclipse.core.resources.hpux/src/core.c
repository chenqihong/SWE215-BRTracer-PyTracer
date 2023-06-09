/*
 * (c) Copyright IBM Corp. 2003.
 * All Rights Reserved.
 */
#include <jni.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <utime.h>
#include <stdlib.h>
#include <string.h>
#include "core.h"

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalIsUnicode
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalIsUnicode
  (JNIEnv *env, jclass clazz) {
  	// no specific support for Unicode-based file names on hpux
	return JNI_FALSE;
}

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalGetStatW
 * Signature: ([C)J
 */
JNIEXPORT jlong JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalGetStatW
   (JNIEnv *env, jclass clazz, jcharArray target) {
	// shouldn't ever be called - there is no Unicode-specific calls on hpux
	return JNI_FALSE;
}   

/*
 * Get a null-terminated byte array from a java byte array.
 * The returned bytearray needs to be freed whe not used
 * anymore. Use free(result) to do that.
 */
jbyte* getByteArray(JNIEnv *env, jbyteArray target) {
	jsize n;
	jbyte *temp, *result;
	
	temp = (*env)->GetByteArrayElements(env, target, 0);
	n = (*env)->GetArrayLength(env, target);
	result = malloc((n+1) * sizeof(jbyte));
	memcpy(result, temp, n);
	result[n] = '\0';
	(*env)->ReleaseByteArrayElements(env, target, temp, 0);
	return result;
}

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalGetStat
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalGetStat
   (JNIEnv *env, jclass clazz, jbyteArray target) {

	struct stat info;
	jlong result;
	jint code;
	jbyte *name;

	/* get stat */
	name = getByteArray(env, target);
	code = stat((const char*)name, &info);
	free(name);

	/* test if an error occurred */
	if (code == -1)
	  return 0;

	/* filter interesting bits */
	/* lastModified */
	result = ((jlong) info.st_mtime) * 1000; /* lower bits */
	/* valid stat */
	result |= STAT_VALID;
	/* is folder? */
	if ((info.st_mode & S_IFDIR) == S_IFDIR)
		result |= STAT_FOLDER;
	/* is read-only? */
	if ((info.st_mode & S_IWRITE) != S_IWRITE)
		result |= STAT_READ_ONLY;

	return result;
}

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalCopyAttributes
 * Signature: ([B[BZ)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalCopyAttributes
(JNIEnv *env, jclass clazz, jbyteArray source, jbyteArray destination, jboolean copyLastModified) {

  struct stat info;
  struct utimbuf ut;
  jbyte *sourceFile, *destinationFile;
  jint code;

  sourceFile = getByteArray(env, source);
  destinationFile = getByteArray(env, destination);

  code = stat((const char*)sourceFile, &info);
  if (code == 0) {
    code = chmod((const char*)destinationFile, info.st_mode);
    if (code == 0 && copyLastModified) {
      ut.actime = info.st_atime;
      ut.modtime = info.st_mtime;
      code = utime((const char*)destinationFile, &ut);
    }
  }

  free(sourceFile);
  free(destinationFile);
  return code != -1;
}

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalCopyAttributesW
 * Signature: ([C[CZ)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalCopyAttributesW
  (JNIEnv *env, jclass clazz, jcharArray source, jcharArray destination, jboolean copyLastModified) {
	// shouldn't ever be called - there is no Unicode-specific calls on hpux
	return JNI_FALSE;   
}


/*
 * Class:     org_eclipse_ant_core_EclipseProject
 * Method:    internalCopyAttributes
 * Signature: ([B[BZ)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_ant_core_EclipseFileUtils_internalCopyAttributes
   (JNIEnv *env, jclass clazz, jbyteArray source, jbyteArray destination, jboolean copyLastModified) {

  /* use the same implementation for both methods */
  return Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalCopyAttributes
    (env, clazz, source, destination, copyLastModified);
}

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalSetResourceAttributesW
 * Signature: ([CLorg/eclipse/core/resources/ResourceAttributes;)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalSetResourceAttributesW
  (JNIEnv *env, jclass clazz, jcharArray target, jobject obj) {
	// shouldn't ever be called - there is no Unicode-specific calls on hpux
	return JNI_FALSE;   
}

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalSetResourceAttributes
 * Signature: ([BLorg/eclipse/core/resources/ResourceAttributes;)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalSetResourceAttributes
  (JNIEnv *env, jclass clazz, jcharArray target, jobject obj) {

    int mask;
    struct stat info;
    jbyte *name;
    jint code;
    jmethodID mid;
    jboolean executable, readOnly;
    jclass cls;
    
    /* find out if we need to set the execute bit */
    cls = (*env)->GetObjectClass(env, obj);
    mid = (*env)->GetMethodID(env, cls, "isExecutable", "()Z");
    if (mid == 0)
		return JNI_FALSE;
    executable = (*env)->CallBooleanMethod(env, obj, mid);
    
    /* find out if we need to set the readonly bits */
    mid = (*env)->GetMethodID(env, cls, "isReadOnly", "()Z");
    if (mid == 0)
		return JNI_FALSE;
    readOnly = (*env)->CallBooleanMethod(env, obj, mid);
    
    /* get the current permissions */
    name = getByteArray(env, target);
    code = stat((const char*)name, &info);
    
    /* create the mask */
    mask = S_IRUSR |
	       S_IWUSR |
	       S_IXUSR |
           S_IRGRP |
           S_IWGRP |
           S_IXGRP |
           S_IROTH |
           S_IWOTH |
           S_IXOTH;
    mask &= info.st_mode;
    if (executable)
	    mask |= S_IXUSR;
    else
	    mask &= ~(S_IXUSR | S_IXGRP | S_IXOTH);
	if (readOnly)
	    mask &= ~(S_IWUSR | S_IWGRP | S_IWOTH);
	else
	    mask |= (S_IRUSR | S_IWUSR);
    
    /* write the permissions */
    code = chmod((const char*)name, mask);

    free(name);
    return code != -1;

}

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalGetResourceAttributesW
 * Signature: ([CLorg/eclipse/core/resources/ResourceAttributes;)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalGetResourceAttributesW
  (JNIEnv *env, jclass clazz, jcharArray target, jobject obj) {
	// shouldn't ever be called - there is no Unicode-specific calls on Linux
	return JNI_FALSE;   
}

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalGetResourceAttributes
 * Signature: ([BLorg/eclipse/core/resources/ResourceAttributes;)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalGetResourceAttributes
  (JNIEnv *env, jclass clazz, jcharArray target, jobject obj) {

    int mask;
    struct stat info;
    jbyte *name;
    jint code;
    jmethodID mid;
    jboolean executable, readOnly, success;
    jclass cls; 

	success = JNI_TRUE;    

    /* get the current permissions */
    name = getByteArray(env, target);
    code = stat((const char*)name, &info);	
    if (code == -1) {
    	free(name);
	    return JNI_FALSE;
   }

    /* is executable? */
    executable = JNI_FALSE;
    if ((info.st_mode & S_IXUSR) == S_IXUSR)
	    executable = JNI_TRUE;
	
	/* is read-only? */
	readOnly = JNI_FALSE;
	if ((info.st_mode & S_IWUSR) != S_IWUSR)
		readOnly = JNI_TRUE;
		
    /* set the values in ResourceAttribute */
    cls = (*env)->GetObjectClass(env, obj);
    mid = (*env)->GetMethodID(env, cls, "setExecutable", "(Z)V");
    if (mid == 0) {
	    success = JNI_FALSE;
	} else {
	    (*env)->CallVoidMethod(env, obj, mid, executable);
    }
    mid = (*env)->GetMethodID(env, cls, "setReadOnly", "(Z)V");
    if (mid == 0) {
	    success = JNI_FALSE;
	} else {
	    (*env)->CallVoidMethod(env, obj, mid, readOnly);
    }
    free(name);
    return success;
	  
}
