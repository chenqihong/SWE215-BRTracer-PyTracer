/*
 * Copyright (c) 2000, 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for core library */

#ifndef _Included_CORE_LIBRARY
#define _Included_CORE_LIBRARY
#ifdef __cplusplus
extern "C" {
#endif
#undef STAT_VALID
#define STAT_VALID 0x4000000000000000ll
#undef STAT_FOLDER
#define STAT_FOLDER 0x2000000000000000ll
#undef STAT_READ_ONLY
#define STAT_READ_ONLY 0x1000000000000000ll
/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalGetStatW
 * Signature: ([C)J
 */
JNIEXPORT jlong JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalGetStatW
  (JNIEnv *, jclass, jcharArray);

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalGetStat
 * Signature: ([B)J
 */
JNIEXPORT jlong JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalGetStat
  (JNIEnv *, jclass, jbyteArray);
  
/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalIsUnicode
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalIsUnicode
  (JNIEnv *, jclass);  

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalCopyAttributes
 * Signature: ([B[BZ)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalCopyAttributes
  (JNIEnv *, jclass, jbyteArray, jbyteArray, jboolean);
  
/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalCopyAttributesW
 * Signature: ([C[CZ)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalCopyAttributesW
  (JNIEnv *, jclass, jcharArray, jcharArray, jboolean);

/*
 * Class:     org_eclipse_ant_core_EclipseProject
 * Method:    internalCopyAttributes
 * Signature: ([B[BZ)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_ant_core_EclipseFileUtils_internalCopyAttributes
   (JNIEnv *, jclass, jbyteArray, jbyteArray, jboolean);

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalSetResourceAttributesW
 * Signature: ([CLorg/eclipse/core/internal/resources/ResourceAttributes;)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalSetResourceAttributesW
  (JNIEnv *, jclass, jcharArray, jobject);

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalSetResourceAttributes
 * Signature: ([BLorg/eclipse/core/internal/resources/ResourceAttributes;)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalSetResourceAttributes
  (JNIEnv *, jclass, jbyteArray, jobject);

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalGetResourceAttributesW
 * Signature: ([CLorg/eclipse/core/internal/resources/ResourceAttributes;)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalGetResourceAttributesW
  (JNIEnv *, jclass, jcharArray, jobject);

/*
 * Class:     org_eclipse_core_internal_localstore_CoreFileSystemLibrary
 * Method:    internalGetResourceAttributes
 * Signature: ([BLorg/eclipse/core/internal/resources/ResourceAttributes;)Z
 */
JNIEXPORT jboolean JNICALL Java_org_eclipse_core_internal_localstore_CoreFileSystemLibrary_internalGetResourceAttributes
  (JNIEnv *, jclass, jbyteArray, jobject);

#ifdef __cplusplus
}
#endif
#endif
