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
package org.eclipse.jdt.ui.refactoring;

import org.eclipse.jdt.internal.corext.refactoring.rename.RenameCompilationUnitProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameEnumConstProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameFieldProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameJavaProjectProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameMethodProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenamePackageProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameResourceProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameSourceFolderProcessor;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameTypeProcessor;
import org.eclipse.jdt.internal.corext.refactoring.reorg.JavaDeleteProcessor;
import org.eclipse.jdt.internal.corext.refactoring.reorg.JavaMoveProcessor;

/**
 * Interface to define the processor IDs provided by the JDT refactoring.
 * 
 * <p>
 * This interface declares static final fields only; it is not intended to be 
 * implemented.
 * </p>
 * 
 * @since 3.0
 */
public interface IRefactoringProcessorIds {

	/**
	 * Processor ID of the rename Java project processor
	 * (value <code>"org.eclipse.jdt.ui.renameJavaProjectProcessor"</code>).
	 * 
	 * The rename Java project processor loads the following participants:
	 * <ul>
	 *   <li>participants registered for renaming <code>IJavaProject</code>.</li>
	 *   <li>participants registered for renaming <code>IProject</code>.</li>
	 * </ul>
	 */
	public static String RENAME_JAVA_PROJECT_PROCESSOR= RenameJavaProjectProcessor.IDENTIFIER;
	
	/**
	 * Processor ID of the rename source folder
	 * (value <code>"org.eclipse.jdt.ui.renameSourceFolderProcessor"</code>).
	 * 
	 * The rename package fragment root processor loads the following participants:
	 * <ul>
	 *   <li>participants registered for renaming <code>IPackageFragmentRoot</code>.</li>
	 *   <li>participants registered for renaming <code>IFolder</code>.</li>
	 * </ul>
	 */
	public static String RENAME_SOURCE_FOLDER_PROCESSOR= RenameSourceFolderProcessor.IDENTIFIER;
	
	/**
	 * Processor ID of the rename package fragment processor
	 * (value <code>"org.eclipse.jdt.ui.renamePackageProcessor"</code>).
	 * 
	 * The rename package fragment processor loads the following participants:
	 * <ul>
	 *   <li>participants registered for renaming <code>IPackageFragment</code>.</li>
	 *   <li>participants registered for moving <code>IFile</code> to participate in the
	 *       file moves caused by the package fragment rename.</li>
	 *   <li>participants registered for creating <code>IFolder</code> if the package
	 *       rename results in creating a new destination folder.</li>
	 *   <li>participants registered for deleting <code>IFolder</code> if the package
	 *       rename results in deleting the folder corresponding to the package
	 *       fragment to be renamed.</li>
	 * </ul>
	 */
	public static String RENAME_PACKAGE_FRAGMENT_PROCESSOR= RenamePackageProcessor.IDENTIFIER;
	
	/**
	 * Processor ID of the rename compilation unit processor
	 * (value <code>"org.eclipse.jdt.ui.renameCompilationUnitProcessor"</code>).
	 * 
	 * The rename compilation unit processor loads the following participants:
	 * <ul>
	 *   <li>participants registered for renaming <code>ICompilationUnit</code>.</li>
	 *   <li>participants registered for renaming <code>IFile</code>.</li>
	 *   <li>participants registered for renaming <code>IType</code> if the
	 *       compilation unit contains a top level type.</li>
	 * </ul>
	 */
	public static String RENAME_COMPILATION_UNIT_PROCESSOR= RenameCompilationUnitProcessor.IDENTIFIER;
	
	/**
	 * Processor ID of the rename type processor
	 * (value <code>"org.eclipse.jdt.ui.renameTypeProcessor"</code>).
	 * 
	 * The rename type processor loads the following participants:
	 * <ul>
	 *   <li>participants registered for renaming <code>IType</code>.</li>
	 *   <li>participants registered for renaming <code>ICompilationUnit</code> if the
	 *       type is a public top level type.</li>
	 *   <li>participants registered for renaming <code>IFile</code> if the compilation 
	 *       unit gets rename as well.</li>
	 * </ul>
	 */
	public static String RENAME_TYPE_PROCESSOR= RenameTypeProcessor.IDENTIFIER;
	
	/**
	 * Processor ID of the rename method processor
	 * (value <code>"org.eclipse.jdt.ui.renameMethodProcessor"</code>).
	 * 
	 * The rename method processor loads the following participants:
	 * <ul>
	 *   <li>participants registered for renaming <code>IMethod</code>. Renaming
	 *       virtual methods will rename methods with the same name in the type
	 *       hierarchy of the type declaring the method to be renamed as well.
	 *       For those derived methods participants will be loaded as well.</li>
	 * </ul>
	 */
	public static String RENAME_METHOD_PROCESSOR= RenameMethodProcessor.IDENTIFIER;
	
	/**
	 * Processor ID of the rename field processor
	 * (value <code>"org.eclipse.jdt.ui.renameFieldProcessor"</code>).
	 * 
	 * The rename filed processor loads the following participants:
	 * <ul>
	 *   <li>participants registered for renaming <code>IField</code>.</li>
	 *   <li>participants registered for renaming <code>IMethod</code> if 
	 *       corresponding setter and getter methods are renamed as well.</li>
	 * </ul>
	 */
	public static String RENAME_FIELD_PROCESSOR= RenameFieldProcessor.IDENTIFIER;

	/**
	 * Processor ID of the rename enum constant processor
	 * (value <code>"org.eclipse.jdt.ui.renameEnumConstProcessor"</code>).
	 * 
	 * The rename filed processor loads the following participants:
	 * <ul>
	 *   <li>participants registered for renaming <code>IField</code>.</li>
	 * </ul>
	 * @since 3.1
	 */
	public static String RENAME_ENUM_CONSTANT_PROCESSOR= RenameEnumConstProcessor.IDENTIFIER;

	/**
	 * Processor ID of the rename resource processor
	 * (value <code>"org.eclipse.jdt.ui.renameResourceProcessor"</code>).
	 * 
	 * The rename resource processor loads the following participants:
	 * <ul>
	 *   <li>participants registered for renaming <code>IResource</code>.</li>
	 * </ul>
	 */
	public static String RENAME_RESOURCE_PROCESSOR= RenameResourceProcessor.IDENTIFIER;
	
	/**
	 * Processor ID of the move resource processor
	 * (value <code>"org.eclipse.jdt.ui.MoveProcessor"</code>).
	 * 
	 * The move processor loads the following participants, depending on the type of
	 * element that gets moved:
	 * <ul>
	 *   <li><code>IPackageFragmentRoot</code>: participants registered for moving 
	 *       package fragment roots together with participants moving a <code>IFolder
	 *       </code>.</li>
	 *   <li><code>IPackageFragment</code>: participants registered for moving
	 *       package fragments. Additionally move file, create folder and delete
	 *       folder participants are loaded to reflect the resource changes
	 *       caused by a moving a package fragment.</li>
	 *   <li><code>ICompilationUnit</code>: participants registered for moving
	 *       compilation units and <code>IFile</code>. If the compilation unit 
	 *       contains top level types, participants for these types are loaded
	 *       as well.</li>
	 *   <li><code>IResource</code>: participants registered for moving resources.</li>
	 * </ul>
	 */
	public static String MOVE_PROCESSOR= JavaMoveProcessor.IDENTIFIER;
	
	/**
	 * Processor ID of the move static member processor
	 * (value <code>"org.eclipse.jdt.ui.MoveStaticMemberProcessor"</code>).
	 * 
	 * The move static members processor loads participants registered for the
	 * static Java element that gets moved. No support is available to participate
	 * in non static member moves.
	 */
	public static String MOVE_STATIC_MEMBERS_PROCESSOR= "org.eclipse.jdt.ui.MoveStaticMemberProcessor"; //$NON-NLS-1$
	
	/**
	 * Processor ID of the delete resource processor
	 * (value <code>"org.eclipse.jdt.ui.DeleteProcessor"</code>).
	 * 
	 * The delete processor loads the following participants, depending on the type of
	 * element that gets deleted:
	 * <ul>
	 *   <li><code>IJavaProject</code>: participants registered for deleting <code>IJavaProject
	 *       </code> and <code>IProject</code></li>.
	 *   <li><code>IPackageFragmentRoot</code>: participants registered for deleting 
	 *       <code>IPackageFragmentRoot</code> and <code>IFolder</code>.
	 *   <li><code>IPackageFragment</code>: participants registered for deleting 
	 *       <code>IPackageFragment</code>. Additionally delete file and  delete folder
	 *       participants are loaded to reflect the resource changes caused by 
	 *       deleting a package fragment.</li>
	 *   <li><code>ICompilationUnit</code>: participants registered for deleting compilation
	 *       units and files. Additionally type delete participants are loaded to reflect the
	 *       deletion of the top level types declared in the compilation unit.</li>
	 *   <li><code>IType</code>: participants registered for deleting types. Additional 
	 *       compilation unit and file delete participants are loaded if the type to be deleted 
	 *       is the only top level type of a compilation unit.</li>
	 *   <li><code>IMember</code>: participants registered for deleting members.</li>
	 *   <li><code>IResource</code>: participants registered for deleting resources.</li>
	 * </ul>
	 */
	public static String DELETE_PROCESSOR= JavaDeleteProcessor.IDENTIFIER;	
}
