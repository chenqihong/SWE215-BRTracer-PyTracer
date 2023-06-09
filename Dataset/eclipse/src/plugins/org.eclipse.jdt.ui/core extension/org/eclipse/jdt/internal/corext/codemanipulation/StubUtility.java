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
package org.eclipse.jdt.internal.corext.codemanipulation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;

import org.eclipse.text.edits.DeleteEdit;
import org.eclipse.text.edits.MalformedTreeException;
import org.eclipse.text.edits.MultiTextEdit;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.core.runtime.preferences.InstanceScope;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;

import org.eclipse.swt.SWT;

import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.templates.Template;
import org.eclipse.jface.text.templates.TemplateBuffer;
import org.eclipse.jface.text.templates.TemplateException;
import org.eclipse.jface.text.templates.TemplateVariable;
import org.eclipse.jface.text.templates.persistence.TemplatePersistenceData;
import org.eclipse.jface.text.templates.persistence.TemplateStore;

import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IParent;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeParameter;

import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.internal.corext.template.java.CodeTemplateContext;
import org.eclipse.jdt.internal.corext.template.java.CodeTemplateContextType;
import org.eclipse.jdt.internal.corext.util.JavaModelUtil;
import org.eclipse.jdt.internal.corext.util.Strings;

import org.eclipse.jdt.ui.PreferenceConstants;

import org.eclipse.jdt.internal.ui.JavaPlugin;
import org.eclipse.jdt.internal.ui.JavaUIStatus;
import org.eclipse.jdt.internal.ui.viewsupport.ProjectTemplateStore;

public class StubUtility {
	
	
	public static class GenStubSettings extends CodeGenerationSettings {
	
		public boolean callSuper;
		public boolean methodOverwrites;
		public boolean noBody;
		public int methodModifiers;
		
		public GenStubSettings(CodeGenerationSettings settings) {
			settings.setSettings(this);
			methodModifiers= -1;	
		}
		
	}
		
	private static final String[] EMPTY= new String[0];
	
	/**
	 * Generates a method stub including the method comment. Given a template
	 * method, a stub with the same signature will be constructed so it can be
	 * added to a type. The method body will be empty or contain a return or
	 * super call.
	 * @param cu The cu to create the source for
	 * @param destTypeName The name of the type to which the method will be
	 * added to
	 * @param method A method template (method belongs to different type than the parent)
	 * @param definingType The type that defines the method.
	 * @param settings Options as defined above (<code>GenStubSettings</code>)
	 * @param imports Imports required by the stub are added to the imports structure. If imports structure is <code>null</code>
	 * all type names are qualified.
	 * @return The generated code
	 * @throws CoreException
	 * @throws JavaModelException
	 */
	public static String genStub(ICompilationUnit cu, String destTypeName, IMethod method, IType definingType, GenStubSettings settings, IImportsStructure imports) throws CoreException {
		String methName= method.getElementName();
		String[] paramNames= suggestArgumentNames(method.getJavaProject(), method.getParameterNames());
		String returnType= method.isConstructor() ? null : method.getReturnType();
		String lineDelimiter= String.valueOf('\n'); // reformatting required
		
		
		StringBuffer buf= new StringBuffer();
		// add method comment
		if (settings.createComments && cu != null) {
			IMethod overridden= null;
			if (settings.methodOverwrites && returnType != null) {
				overridden= JavaModelUtil.findMethod(methName, method.getParameterTypes(), false, definingType.getMethods());
			}
			String[] typeParameterNames= getTypeParameterNames(method.getTypeParameters());
			String comment= getMethodComment(cu, destTypeName, methName, paramNames, method.getExceptionTypes(), returnType, typeParameterNames, overridden, lineDelimiter);
			if (comment != null) {
				buf.append(comment);
			} else {
				buf.append("/**").append(lineDelimiter); //$NON-NLS-1$
				buf.append(" *").append(lineDelimiter); //$NON-NLS-1$
				buf.append(" */").append(lineDelimiter); //$NON-NLS-1$							
			}
			buf.append(lineDelimiter);
		}
		// add method declaration
		String bodyContent= null;
		if (!settings.noBody) {
			String bodyStatement= getDefaultMethodBodyStatement(methName, paramNames, returnType, settings.callSuper);
			bodyContent= getMethodBodyContent(returnType == null, method.getJavaProject(), destTypeName, methName, bodyStatement, lineDelimiter);
			if (bodyContent == null) {
				bodyContent= ""; //$NON-NLS-1$
			}
		}
		int flags= settings.methodModifiers;
		if (flags == -1) {
			flags= method.getFlags();
		}
		
		genMethodDeclaration(destTypeName, method, flags, bodyContent, imports, buf);
		return buf.toString();
	}

	/**
	 * Generates a method stub not including the method comment. Given a
	 * template method and the body content, a stub with the same signature will
	 * be constructed so it can be added to a type.
	 * @param destTypeName The name of the type to which the method will be
	 * added to
	 * @param method A method template (method belongs to different type than the parent)
	 * @param flags
	 * @param bodyContent Content of the body
	 * @param imports Imports required by the stub are added to the imports
	 * structure. If imports structure is <code>null</code> all type names are
	 * qualified.
	 * @param buf The buffer to append the generated code.
	 * @throws CoreException
	 * @throws JavaModelException
	 */
	private static void genMethodDeclaration(String destTypeName, IMethod method, int flags, String bodyContent, IImportsStructure imports, StringBuffer buf) throws CoreException {
		IType parentType= method.getDeclaringType();	
		String methodName= method.getElementName();
		String[] paramTypes= method.getParameterTypes();
		String[] paramNames= suggestArgumentNames(parentType.getJavaProject(), method.getParameterNames());

		String[] excTypes= method.getExceptionTypes();

		boolean isConstructor= method.isConstructor();
		String retTypeSig= isConstructor ? null : method.getReturnType();
		
		int lastParam= paramTypes.length -1;
		
		if (Flags.isPublic(flags) || (parentType.isInterface() && bodyContent != null)) {
			buf.append("public "); //$NON-NLS-1$
		} else if (Flags.isProtected(flags)) {
			buf.append("protected "); //$NON-NLS-1$
		} else if (Flags.isPrivate(flags)) {
			buf.append("private "); //$NON-NLS-1$
		}
		if (Flags.isSynchronized(flags)) {
			buf.append("synchronized "); //$NON-NLS-1$
		}		
		if (Flags.isVolatile(flags)) {
			buf.append("volatile "); //$NON-NLS-1$
		}
		if (Flags.isStrictfp(flags)) {
			buf.append("strictfp "); //$NON-NLS-1$
		}
		if (Flags.isStatic(flags)) {
			buf.append("static "); //$NON-NLS-1$
		}		
			
		if (isConstructor) {
			buf.append(destTypeName);
		} else {
			String retTypeFrm;
			if (!isPrimitiveType(retTypeSig)) {
				retTypeFrm= resolveAndAdd(retTypeSig, parentType, imports);
			} else {
				retTypeFrm= Signature.toString(retTypeSig);
			}
			buf.append(retTypeFrm);
			buf.append(' ');
			buf.append(methodName);
		}
		buf.append('(');
		for (int i= 0; i <= lastParam; i++) {
			String paramTypeSig= paramTypes[i];
			String paramTypeFrm;
			
			if (!isPrimitiveType(paramTypeSig)) {
				paramTypeFrm= resolveAndAdd(paramTypeSig, parentType, imports);
			} else {
				paramTypeFrm= Signature.toString(paramTypeSig);
			}
			buf.append(paramTypeFrm);
			buf.append(' ');
			buf.append(paramNames[i]);
			if (i < lastParam) {
				buf.append(", "); //$NON-NLS-1$
			}
		}
		buf.append(')');
		
		int lastExc= excTypes.length - 1;
		if (lastExc >= 0) {
			buf.append(" throws "); //$NON-NLS-1$
			for (int i= 0; i <= lastExc; i++) {
				String excTypeSig= excTypes[i];
				String excTypeFrm= resolveAndAdd(excTypeSig, parentType, imports);
				buf.append(excTypeFrm);
				if (i < lastExc) {
					buf.append(", "); //$NON-NLS-1$
				}
			}
		}
		if (bodyContent == null) {
			buf.append(";\n\n"); //$NON-NLS-1$
		} else {
			buf.append(" {\n\t"); //$NON-NLS-1$
			if (bodyContent.length() > 0) {
				buf.append(bodyContent);
				buf.append('\n');
			}
			buf.append("}\n");			 //$NON-NLS-1$
		}
	}
	
	public static String getDefaultMethodBodyStatement(String methodName, String[] paramNames, String retTypeSig, boolean callSuper) {
		StringBuffer buf= new StringBuffer();
		if (callSuper) {
			if (retTypeSig != null) {
				if (!Signature.SIG_VOID.equals(retTypeSig)) {
					buf.append("return "); //$NON-NLS-1$
				}
				buf.append("super."); //$NON-NLS-1$
				buf.append(methodName);
			} else {
				buf.append("super"); //$NON-NLS-1$
			}
			buf.append('(');			
			for (int i= 0; i < paramNames.length; i++) {
				if (i > 0) {
					buf.append(", "); //$NON-NLS-1$
				}
				buf.append(paramNames[i]);
			}
			buf.append(");"); //$NON-NLS-1$
		} else {
			if (retTypeSig != null && !retTypeSig.equals(Signature.SIG_VOID)) {
				if (!isPrimitiveType(retTypeSig) || Signature.getArrayCount(retTypeSig) > 0) {
					buf.append("return null;"); //$NON-NLS-1$
				} else if (retTypeSig.equals(Signature.SIG_BOOLEAN)) {
					buf.append("return false;"); //$NON-NLS-1$
				} else {
					buf.append("return 0;"); //$NON-NLS-1$
				}
			}			
		}
		return buf.toString();
	}	

	public static String getMethodBodyContent(boolean isConstructor, IJavaProject project, String destTypeName, String methodName, String bodyStatement, String lineDelimiter) throws CoreException {
		String templateName= isConstructor ? CodeTemplateContextType.CONSTRUCTORSTUB_ID : CodeTemplateContextType.METHODSTUB_ID;
		Template template= getCodeTemplate(templateName, project);
		if (template == null) {
			return bodyStatement;
		}
		CodeTemplateContext context= new CodeTemplateContext(template.getContextTypeId(), project, lineDelimiter);
		context.setVariable(CodeTemplateContextType.ENCLOSING_METHOD, methodName);
		context.setVariable(CodeTemplateContextType.ENCLOSING_TYPE, destTypeName);
		context.setVariable(CodeTemplateContextType.BODY_STATEMENT, bodyStatement);
		String str= evaluateTemplate(context, template, new String[] { CodeTemplateContextType.BODY_STATEMENT });
		if (str == null && !Strings.containsOnlyWhitespaces(bodyStatement)) {
			return bodyStatement;
		}
		return str;
	}
	
	public static String getGetterMethodBodyContent(IJavaProject project, String destTypeName, String methodName, String fieldName, String lineDelimiter) throws CoreException {
		String templateName= CodeTemplateContextType.GETTERSTUB_ID;
		Template template= getCodeTemplate(templateName, project);
		if (template == null) {
			return null;
		}
		CodeTemplateContext context= new CodeTemplateContext(template.getContextTypeId(), project, lineDelimiter);
		context.setVariable(CodeTemplateContextType.ENCLOSING_METHOD, methodName);
		context.setVariable(CodeTemplateContextType.ENCLOSING_TYPE, destTypeName);
		context.setVariable(CodeTemplateContextType.FIELD, fieldName);
		
		return evaluateTemplate(context, template);
	}
	
	public static String getSetterMethodBodyContent(IJavaProject project, String destTypeName, String methodName, String fieldName, String paramName, String lineDelimiter) throws CoreException {
		String templateName= CodeTemplateContextType.SETTERSTUB_ID;
		Template template= getCodeTemplate(templateName, project);
		if (template == null) {
			return null;
		}
		CodeTemplateContext context= new CodeTemplateContext(template.getContextTypeId(), project, lineDelimiter);
		context.setVariable(CodeTemplateContextType.ENCLOSING_METHOD, methodName);
		context.setVariable(CodeTemplateContextType.ENCLOSING_TYPE, destTypeName);
		context.setVariable(CodeTemplateContextType.FIELD, fieldName);
		context.setVariable(CodeTemplateContextType.FIELD_TYPE, fieldName);
		context.setVariable(CodeTemplateContextType.PARAM, paramName);
		
		return evaluateTemplate(context, template);
	}
	
	public static String getCatchBodyContent(ICompilationUnit cu, String exceptionType, String variableName, String lineDelimiter) throws CoreException {
		Template template= getCodeTemplate(CodeTemplateContextType.CATCHBLOCK_ID, cu.getJavaProject());
		if (template == null) {
			return null;
		}

		CodeTemplateContext context= new CodeTemplateContext(template.getContextTypeId(), cu.getJavaProject(), lineDelimiter);
		context.setVariable(CodeTemplateContextType.EXCEPTION_TYPE, exceptionType);
		context.setVariable(CodeTemplateContextType.EXCEPTION_VAR, variableName); //$NON-NLS-1$
		return evaluateTemplate(context, template);
	}
	
	/*
	 * @see org.eclipse.jdt.ui.CodeGeneration#getTypeComment(ICompilationUnit, String, String, String, String)
	 */	
	public static String getCompilationUnitContent(ICompilationUnit cu, String fileComment, String typeComment, String typeContent, String lineDelimiter) throws CoreException {
		IPackageFragment pack= (IPackageFragment) cu.getParent();
		String packDecl= pack.isDefaultPackage() ? "" : "package " + pack.getElementName() + ';'; //$NON-NLS-1$ //$NON-NLS-2$

		Template template= getCodeTemplate(CodeTemplateContextType.NEWTYPE_ID, cu.getJavaProject());
		if (template == null) {
			return null;
		}
		
		IJavaProject project= cu.getJavaProject();
		CodeTemplateContext context= new CodeTemplateContext(template.getContextTypeId(), project, lineDelimiter);
		context.setCompilationUnitVariables(cu);
		context.setVariable(CodeTemplateContextType.PACKAGE_DECLARATION, packDecl);
		context.setVariable(CodeTemplateContextType.TYPE_COMMENT, typeComment != null ? typeComment : ""); //$NON-NLS-1$
		context.setVariable(CodeTemplateContextType.FILE_COMMENT, fileComment != null ? fileComment : ""); //$NON-NLS-1$
		context.setVariable(CodeTemplateContextType.TYPE_DECLARATION, typeContent);
		context.setVariable(CodeTemplateContextType.TYPENAME, Signature.getQualifier(cu.getElementName()));
		
		String[] fullLine= { CodeTemplateContextType.PACKAGE_DECLARATION, CodeTemplateContextType.FILE_COMMENT, CodeTemplateContextType.TYPE_COMMENT };
		return evaluateTemplate(context, template, fullLine);
	}
	
	/*
	 * @see org.eclipse.jdt.ui.CodeGeneration#getFileComment(ICompilationUnit, String)
	 */	
	public static String getFileComment(ICompilationUnit cu, String lineDelimiter) throws CoreException {
		Template template= getCodeTemplate(CodeTemplateContextType.FILECOMMENT_ID, cu.getJavaProject());
		if (template == null) {
			return null;
		}
		
		IJavaProject project= cu.getJavaProject();
		CodeTemplateContext context= new CodeTemplateContext(template.getContextTypeId(), project, lineDelimiter);
		context.setCompilationUnitVariables(cu);
		context.setVariable(CodeTemplateContextType.TYPENAME, Signature.getQualifier(cu.getElementName()));
		return evaluateTemplate(context, template);
	}	

	/**
	 * @see org.eclipse.jdt.ui.CodeGeneration#getTypeComment(ICompilationUnit, String, String[], String)
	 */		
	public static String getTypeComment(ICompilationUnit cu, String typeQualifiedName, String[] typeParameterNames, String lineDelim) throws CoreException {
		Template template= getCodeTemplate(CodeTemplateContextType.TYPECOMMENT_ID, cu.getJavaProject());
		if (template == null) {
			return null;
		}
		CodeTemplateContext context= new CodeTemplateContext(template.getContextTypeId(), cu.getJavaProject(), lineDelim);
		context.setCompilationUnitVariables(cu);
		context.setVariable(CodeTemplateContextType.ENCLOSING_TYPE, Signature.getQualifier(typeQualifiedName));
		context.setVariable(CodeTemplateContextType.TYPENAME, Signature.getSimpleName(typeQualifiedName));

		TemplateBuffer buffer;
		try {
			buffer= context.evaluate(template);
		} catch (BadLocationException e) {
			throw new CoreException(Status.CANCEL_STATUS);
		} catch (TemplateException e) {
			throw new CoreException(Status.CANCEL_STATUS);
		}
		String str= buffer.getString();
		if (Strings.containsOnlyWhitespaces(str)) {
			return null;
		}
		
		TemplateVariable position= findVariable(buffer, CodeTemplateContextType.TAGS); // look if Javadoc tags have to be added
		if (position == null) {
			return str;
		}
		
		IDocument document= new Document(str);
		int[] tagOffsets= position.getOffsets();
		for (int i= tagOffsets.length - 1; i >= 0; i--) { // from last to first
			try {
				insertTag(document, tagOffsets[i], position.getLength(), EMPTY, EMPTY, null, typeParameterNames, false, lineDelim);
			} catch (BadLocationException e) {
				throw new CoreException(JavaUIStatus.createError(IStatus.ERROR, e));
			}
		}
		return document.get();
	}

	private static String[] getParameterTypesQualifiedNames(IMethodBinding binding) {
		ITypeBinding[] typeBindings= binding.getParameterTypes();
		String[] result= new String[typeBindings.length];
		for (int i= 0; i < result.length; i++) {
			if (typeBindings[i].isTypeVariable())
				result[i]= typeBindings[i].getName();
			else
				result[i]= typeBindings[i].getTypeDeclaration().getQualifiedName();
		}
		return result;
	}

	private static String getSeeTag(String declaringClassQualifiedName, String methodName, String[] parameterTypesQualifiedNames) {
		StringBuffer buf= new StringBuffer();
		buf.append("@see "); //$NON-NLS-1$
		buf.append(declaringClassQualifiedName);
		buf.append('#'); 
		buf.append(methodName);
		buf.append('(');
		for (int i= 0; i < parameterTypesQualifiedNames.length; i++) {
			if (i > 0) {
				buf.append(", "); //$NON-NLS-1$
			}
			buf.append(parameterTypesQualifiedNames[i]);
		}
		buf.append(')');
		return buf.toString();
	}
	
	private static String getSeeTag(IMethod overridden) throws JavaModelException {
		IType declaringType= overridden.getDeclaringType();
		StringBuffer buf= new StringBuffer();
		buf.append("@see "); //$NON-NLS-1$
		buf.append(declaringType.getFullyQualifiedName('.'));
		buf.append('#'); 
		buf.append(overridden.getElementName());
		buf.append('(');
		String[] paramTypes= overridden.getParameterTypes();
		for (int i= 0; i < paramTypes.length; i++) {
			if (i > 0) {
				buf.append(", "); //$NON-NLS-1$
			}
			String curr= Signature.getTypeErasure(paramTypes[i]);
			buf.append(JavaModelUtil.getResolvedTypeName(curr, declaringType));
			int arrayCount= Signature.getArrayCount(curr);
			while (arrayCount > 0) {
				buf.append("[]"); //$NON-NLS-1$
				arrayCount--;
			}
		}
		buf.append(')');
		return buf.toString();
	}
	
	public static String[] getTypeParameterNames(ITypeParameter[] typeParameters) {
		String[] typeParametersNames= new String[typeParameters.length];
		for (int i= 0; i < typeParameters.length; i++) {
			typeParametersNames[i]= typeParameters[i].getElementName();
		}
		return typeParametersNames;
	}
	
	/**
	 * @see org.eclipse.jdt.ui.CodeGeneration#getMethodComment(IMethod,IMethod,String)
	 */
	public static String getMethodComment(IMethod method, IMethod overridden, String lineDelimiter) throws CoreException {
		String retType= method.isConstructor() ? null : method.getReturnType();
		String[] paramNames= method.getParameterNames();
		String[] typeParameterNames= getTypeParameterNames(method.getTypeParameters());
		
		return getMethodComment(method.getCompilationUnit(), method.getDeclaringType().getElementName(),
			method.getElementName(), paramNames, method.getExceptionTypes(), retType, typeParameterNames, overridden, lineDelimiter);
	}

	/**
	 * @see org.eclipse.jdt.ui.CodeGeneration#getMethodComment(ICompilationUnit, String, String, String[], String[], String, String[], IMethod, String)
	 */
	public static String getMethodComment(ICompilationUnit cu, String typeName, String methodName, String[] paramNames, String[] excTypeSig, String retTypeSig, String[] typeParameterNames, IMethod overridden, String lineDelimiter) throws CoreException {
		String templateName= CodeTemplateContextType.METHODCOMMENT_ID;
		if (retTypeSig == null) {
			templateName= CodeTemplateContextType.CONSTRUCTORCOMMENT_ID;
		} else if (overridden != null) {
			templateName= CodeTemplateContextType.OVERRIDECOMMENT_ID;
		}
		Template template= getCodeTemplate(templateName, cu.getJavaProject());
		if (template == null) {
			return null;
		}		
		CodeTemplateContext context= new CodeTemplateContext(template.getContextTypeId(), cu.getJavaProject(), lineDelimiter);
		context.setCompilationUnitVariables(cu);
		context.setVariable(CodeTemplateContextType.ENCLOSING_TYPE, typeName);
		context.setVariable(CodeTemplateContextType.ENCLOSING_METHOD, methodName);
				
		if (retTypeSig != null) {
			context.setVariable(CodeTemplateContextType.RETURN_TYPE, Signature.toString(retTypeSig));
		}
		if (overridden != null) {
			context.setVariable(CodeTemplateContextType.SEE_TAG, getSeeTag(overridden));
		}
		TemplateBuffer buffer;
		try {
			buffer= context.evaluate(template);
		} catch (BadLocationException e) {
			throw new CoreException(Status.CANCEL_STATUS);
		} catch (TemplateException e) {
			throw new CoreException(Status.CANCEL_STATUS);
		}
		if (buffer == null) {
			return null;
		}
		
		String str= buffer.getString();
		if (Strings.containsOnlyWhitespaces(str)) {
			return null;
		}
		TemplateVariable position= findVariable(buffer, CodeTemplateContextType.TAGS); // look if Javadoc tags have to be added
		if (position == null) {
			return str;
		}
			
		IDocument document= new Document(str);
		String[] exceptionNames= new String[excTypeSig.length];
		for (int i= 0; i < excTypeSig.length; i++) {
			exceptionNames[i]= Signature.toString(excTypeSig[i]);
		}
		String returnType= retTypeSig != null ? Signature.toString(retTypeSig) : null;
		int[] tagOffsets= position.getOffsets();
		for (int i= tagOffsets.length - 1; i >= 0; i--) { // from last to first
			try {
				insertTag(document, tagOffsets[i], position.getLength(), paramNames, exceptionNames, returnType, typeParameterNames, false, lineDelimiter);
			} catch (BadLocationException e) {
				throw new CoreException(JavaUIStatus.createError(IStatus.ERROR, e));
			}
		}
		return document.get();
	}
	
	// remove lines for empty variables
	private static String fixEmptyVariables(TemplateBuffer buffer, String[] variables) throws MalformedTreeException, BadLocationException {
		IDocument doc= new Document(buffer.getString());
		int nLines= doc.getNumberOfLines();
		MultiTextEdit edit= new MultiTextEdit();
		HashSet removedLines= new HashSet();
		for (int i= 0; i < variables.length; i++) {
			TemplateVariable position= findVariable(buffer, variables[i]); // look if Javadoc tags have to be added
			if (position == null || position.getLength() > 0) {
				continue;
			}
			int[] offsets= position.getOffsets();
			for (int k= 0; k < offsets.length; k++) {
				int line= doc.getLineOfOffset(offsets[k]);
				IRegion lineInfo= doc.getLineInformation(line);
				int offset= lineInfo.getOffset();
				String str= doc.get(offset, lineInfo.getLength());
				if (Strings.containsOnlyWhitespaces(str) && nLines > line + 1 && removedLines.add(new Integer(line))) {
					int nextStart= doc.getLineOffset(line + 1);
					edit.addChild(new DeleteEdit(offset, nextStart - offset));
				}
			}
		}
		edit.apply(doc, 0);
		return doc.get();
	}
	

	public static String getFieldComment(ICompilationUnit cu, String typeName, String fieldName, String lineDelimiter) throws CoreException {
		Template template= getCodeTemplate(CodeTemplateContextType.FIELDCOMMENT_ID, cu.getJavaProject());
		if (template == null) {
			return null;
		}
		CodeTemplateContext context= new CodeTemplateContext(template.getContextTypeId(), cu.getJavaProject(), lineDelimiter);
		context.setCompilationUnitVariables(cu);
		context.setVariable(CodeTemplateContextType.FIELD_TYPE, typeName);
		context.setVariable(CodeTemplateContextType.FIELD, fieldName);
		
		return evaluateTemplate(context, template);
	}	
	
	
	/**
	 * @see org.eclipse.jdt.ui.CodeGeneration#getSetterComment(ICompilationUnit, String, String, String, String, String, String, String)
	 */
	public static String getSetterComment(ICompilationUnit cu, String typeName, String methodName, String fieldName, String fieldType, String paramName, String bareFieldName, String lineDelimiter) throws CoreException {
		String templateName= CodeTemplateContextType.SETTERCOMMENT_ID;
		Template template= getCodeTemplate(templateName, cu.getJavaProject());
		if (template == null) {
			return null;
		}
		
		CodeTemplateContext context= new CodeTemplateContext(template.getContextTypeId(), cu.getJavaProject(), lineDelimiter);
		context.setCompilationUnitVariables(cu);
		context.setVariable(CodeTemplateContextType.ENCLOSING_TYPE, typeName);
		context.setVariable(CodeTemplateContextType.ENCLOSING_METHOD, methodName);
		context.setVariable(CodeTemplateContextType.FIELD, fieldName);
		context.setVariable(CodeTemplateContextType.FIELD_TYPE, fieldType);
		context.setVariable(CodeTemplateContextType.BARE_FIELD_NAME, bareFieldName);
		context.setVariable(CodeTemplateContextType.PARAM, paramName);

		return evaluateTemplate(context, template);
	}	
	
	/**
	 * @see org.eclipse.jdt.ui.CodeGeneration#getGetterComment(ICompilationUnit, String, String, String, String, String, String)
	 */
	public static String getGetterComment(ICompilationUnit cu, String typeName, String methodName, String fieldName, String fieldType, String bareFieldName, String lineDelimiter) throws CoreException {
		String templateName= CodeTemplateContextType.GETTERCOMMENT_ID;
		Template template= getCodeTemplate(templateName, cu.getJavaProject());
		if (template == null) {
			return null;
		}		
		CodeTemplateContext context= new CodeTemplateContext(template.getContextTypeId(), cu.getJavaProject(), lineDelimiter);
		context.setCompilationUnitVariables(cu);
		context.setVariable(CodeTemplateContextType.ENCLOSING_TYPE, typeName);
		context.setVariable(CodeTemplateContextType.ENCLOSING_METHOD, methodName);
		context.setVariable(CodeTemplateContextType.FIELD, fieldName);
		context.setVariable(CodeTemplateContextType.FIELD_TYPE, fieldType);
		context.setVariable(CodeTemplateContextType.BARE_FIELD_NAME, bareFieldName);

		return evaluateTemplate(context, template);
	}
	
	public static String evaluateTemplate(CodeTemplateContext context, Template template) throws CoreException {
		TemplateBuffer buffer;
		try {
			buffer= context.evaluate(template);
		} catch (BadLocationException e) {
			throw new CoreException(Status.CANCEL_STATUS);
		} catch (TemplateException e) {
			throw new CoreException(Status.CANCEL_STATUS);
		}
		if (buffer == null)
			return null;
		String str= buffer.getString();
		if (Strings.containsOnlyWhitespaces(str)) {
			return null;
		}
		return str;
	}
	
	public static String evaluateTemplate(CodeTemplateContext context, Template template, String[] fullLineVariables) throws CoreException {
		TemplateBuffer buffer;
		try {
			buffer= context.evaluate(template);
			if (buffer == null)
				return null;
			String str= fixEmptyVariables(buffer, fullLineVariables);
			if (Strings.containsOnlyWhitespaces(str)) {
				return null;
			}
			return str;
		} catch (BadLocationException e) {
			throw new CoreException(Status.CANCEL_STATUS);
		} catch (TemplateException e) {
			throw new CoreException(Status.CANCEL_STATUS);
		}
	}

	/**
	 * @see org.eclipse.jdt.ui.CodeGeneration#getMethodComment(ICompilationUnit, String, MethodDeclaration, IMethodBinding, String)
	 */
	public static String getMethodComment(ICompilationUnit cu, String typeName, MethodDeclaration decl, IMethodBinding overridden, String lineDelimiter) throws CoreException {
		if (overridden != null) {
			overridden= overridden.getMethodDeclaration();
			String declaringClassQualifiedName= overridden.getDeclaringClass().getQualifiedName();
			String[] parameterTypesQualifiedNames= getParameterTypesQualifiedNames(overridden);			
			return getMethodComment(cu, typeName, decl, true, overridden.isDeprecated(), declaringClassQualifiedName, parameterTypesQualifiedNames, lineDelimiter);
		} else {
			return getMethodComment(cu, typeName, decl, false, false, null, null, lineDelimiter);
		}
	}
	
	/**
	 * Returns the comment for a method using the comment code templates.
	 * <code>null</code> is returned if the template is empty.
	 * @param cu The compilation unit to which the method belongs
	 * @param typeName Name of the type to which the method belongs.
	 * @param decl The AST MethodDeclaration node that will be added as new
	 * method.
	 * @param isOverridden <code>true</code> iff decl overrides another method
	 * @param isDeprecated <code>true</code> iff the method that decl overrides is deprecated.
	 * Note: it must not be <code>true</code> if isOverridden is <code>false</code>.
	 * @param declaringClassQualifiedName Fully qualified name of the type in which the overriddden 
	 * method (if any exists) in declared. If isOverridden is <code>false</code>, this is ignored.
	 * @param parameterTypesQualifiedNames Fully qualified names of parameter types of the type in which the overriddden 
	 * method (if any exists) in declared. If isOverridden is <code>false</code>, this is ignored.
	 * @param lineDelimiter The line delimiter to use
	 * @return String Returns the method comment or <code>null</code> if the
	 * configured template is empty. 
	 * (formatting required)
	 * @throws CoreException
	 */
	public static String getMethodComment(ICompilationUnit cu, String typeName, MethodDeclaration decl, boolean isOverridden, boolean isDeprecated, String declaringClassQualifiedName, String[] parameterTypesQualifiedNames, String lineDelimiter) throws CoreException {
		String templateName= CodeTemplateContextType.METHODCOMMENT_ID;
		if (decl.isConstructor()) {
			templateName= CodeTemplateContextType.CONSTRUCTORCOMMENT_ID;
		} else if (isOverridden) {
			templateName= CodeTemplateContextType.OVERRIDECOMMENT_ID;
		}
		Template template= getCodeTemplate(templateName, cu.getJavaProject());
		if (template == null) {
			return null;
		}		
		CodeTemplateContext context= new CodeTemplateContext(template.getContextTypeId(), cu.getJavaProject(), lineDelimiter);
		context.setCompilationUnitVariables(cu);
		context.setVariable(CodeTemplateContextType.ENCLOSING_TYPE, typeName);
		context.setVariable(CodeTemplateContextType.ENCLOSING_METHOD, decl.getName().getIdentifier());
		if (!decl.isConstructor()) {
			ASTNode returnType= (decl.getAST().apiLevel() == AST.JLS2) ? decl.getReturnType() : decl.getReturnType2();
			context.setVariable(CodeTemplateContextType.RETURN_TYPE, ASTNodes.asString(returnType));
		}
		if (isOverridden) {
			String methodName= decl.getName().getIdentifier();
			context.setVariable(CodeTemplateContextType.SEE_TAG, getSeeTag(declaringClassQualifiedName, methodName, parameterTypesQualifiedNames));
		}
		
		TemplateBuffer buffer;
		try {
			buffer= context.evaluate(template);
		} catch (BadLocationException e) {
			throw new CoreException(Status.CANCEL_STATUS);
		} catch (TemplateException e) {
			throw new CoreException(Status.CANCEL_STATUS);
		}
		if (buffer == null)
			return null;
		String str= buffer.getString();
		if (Strings.containsOnlyWhitespaces(str)) {
			return null;
		}
		TemplateVariable position= findVariable(buffer, CodeTemplateContextType.TAGS);  // look if Javadoc tags have to be added
		if (position == null) {
			return str;
		}
			
		IDocument textBuffer= new Document(str);
		List typeParams= decl.typeParameters();
		String[] typeParamNames= new String[typeParams.size()];
		for (int i= 0; i < typeParamNames.length; i++) {
			TypeParameter elem= (TypeParameter) typeParams.get(i);
			typeParamNames[i]= elem.getName().getIdentifier();
		}
		List params= decl.parameters();
		String[] paramNames= new String[params.size()];
		for (int i= 0; i < paramNames.length; i++) {
			SingleVariableDeclaration elem= (SingleVariableDeclaration) params.get(i);
			paramNames[i]= elem.getName().getIdentifier();
		}
		List exceptions= decl.thrownExceptions();
		String[] exceptionNames= new String[exceptions.size()];
		for (int i= 0; i < exceptionNames.length; i++) {
			exceptionNames[i]= ASTNodes.getSimpleNameIdentifier((Name) exceptions.get(i));
		}
		
		String returnType= null;
		if (!decl.isConstructor()) {
			returnType= ASTNodes.asString((decl.getAST().apiLevel() == AST.JLS2) ? decl.getReturnType() : decl.getReturnType2());
		}
		int[] tagOffsets= position.getOffsets();
		for (int i= tagOffsets.length - 1; i >= 0; i--) { // from last to first
			try {
				insertTag(textBuffer, tagOffsets[i], position.getLength(), paramNames, exceptionNames, returnType, typeParamNames, isDeprecated, lineDelimiter);
			} catch (BadLocationException e) {
				throw new CoreException(JavaUIStatus.createError(IStatus.ERROR, e));
			}
		}
		return textBuffer.get();
	}
	
	private static TemplateVariable findVariable(TemplateBuffer buffer, String variable) {
		TemplateVariable[] positions= buffer.getVariables();
		for (int i= 0; i < positions.length; i++) {
			TemplateVariable curr= positions[i];
			if (variable.equals(curr.getType())) {
				return curr;
			}
		}
		return null;		
	}	
	
	private static void insertTag(IDocument textBuffer, int offset, int length, String[] paramNames, String[] exceptionNames, String returnType, String[] typeParameterNames, boolean isDeprecated, String lineDelimiter) throws BadLocationException {
		IRegion region= textBuffer.getLineInformationOfOffset(offset);
		if (region == null) {
			return;
		}
		String lineStart= textBuffer.get(region.getOffset(), offset - region.getOffset());
		
		StringBuffer buf= new StringBuffer();
		for (int i= 0; i < typeParameterNames.length; i++) {
			if (buf.length() > 0) {
				buf.append(lineDelimiter).append(lineStart);
			}
			buf.append("@param <").append(typeParameterNames[i]).append('>'); //$NON-NLS-1$
		}
		for (int i= 0; i < paramNames.length; i++) {
			if (buf.length() > 0) {
				buf.append(lineDelimiter).append(lineStart);
			}
			buf.append("@param ").append(paramNames[i]); //$NON-NLS-1$
		}
		if (returnType != null && !returnType.equals("void")) { //$NON-NLS-1$
			if (buf.length() > 0) {
				buf.append(lineDelimiter).append(lineStart);
			}			
			buf.append("@return"); //$NON-NLS-1$
		}
		if (exceptionNames != null) {
			for (int i= 0; i < exceptionNames.length; i++) {
				if (buf.length() > 0) {
					buf.append(lineDelimiter).append(lineStart);
				}
				buf.append("@throws ").append(exceptionNames[i]); //$NON-NLS-1$
			}
		}		
		if (isDeprecated) {
			if (buf.length() > 0) {
				buf.append(lineDelimiter).append(lineStart);
			}
			buf.append("@deprecated"); //$NON-NLS-1$
		}
		if (buf.length() == 0 && isAllCommentWhitespace(lineStart)) {
			int prevLine= textBuffer.getLineOfOffset(offset) -1;
			if (prevLine > 0) {
				IRegion prevRegion= textBuffer.getLineInformation(prevLine);
				int prevLineEnd= prevRegion.getOffset() + prevRegion.getLength();
				// clear full line
				textBuffer.replace(prevLineEnd, offset + length - prevLineEnd, ""); //$NON-NLS-1$
				return;
			}
		}
		textBuffer.replace(offset, length, buf.toString());
	}
	
	private static boolean isAllCommentWhitespace(String lineStart) {
		for (int i= 0; i < lineStart.length(); i++) {
			char ch= lineStart.charAt(i);
			if (!Character.isWhitespace(ch) && ch != '*') {
				return false;
			}
		}
		return true;
	}

	private static boolean isPrimitiveType(String typeName) {
		char first= Signature.getElementType(typeName).charAt(0);
		return (first != Signature.C_RESOLVED && first != Signature.C_UNRESOLVED && first != Signature.C_TYPE_VARIABLE);
	}

	private static String resolveAndAdd(String refTypeSig, IType declaringType, IImportsStructure imports) throws JavaModelException {
		int genericStart = refTypeSig.indexOf(Signature.C_GENERIC_START);
		if (genericStart != -1) {
			// generic type
			StringBuffer buf= new StringBuffer();
			
			String erasure= resolveAndAdd(Signature.getTypeErasure(refTypeSig), declaringType, imports);
			buf.append(erasure);
			buf.append('<');
			String[] typeArguments= Signature.getTypeArguments(refTypeSig);
			if (typeArguments.length > 0) {
				for (int i= 0; i < typeArguments.length; i++) {
					if (i > 0) {
						buf.append(", "); //$NON-NLS-1$
					}
					buf.append(resolveAndAdd(typeArguments[i], declaringType, imports));
				}
			}
			buf.append('>');
			return buf.toString();
		}
		if (refTypeSig.length() > 0) {
			switch (refTypeSig.charAt(0)) {
				case Signature.C_ARRAY :
					int arrayCount= Signature.getArrayCount(refTypeSig);
					StringBuffer buf= new StringBuffer();
					buf.append(resolveAndAdd(Signature.getElementType(refTypeSig), declaringType, imports));
					for (int i= 0; i < arrayCount; i++) {
						buf.append("[]"); //$NON-NLS-1$
					}
					return buf.toString();
				case Signature.C_RESOLVED:
				case Signature.C_UNRESOLVED:
					String resolvedTypeName= JavaModelUtil.getResolvedTypeName(refTypeSig, declaringType);
					if (resolvedTypeName != null) {
						if (imports == null) {
							return resolvedTypeName;
						} else {
							return imports.addImport(resolvedTypeName);
						}
					}
					break;
				case Signature.C_EXTENDS:
					return "? extends " + resolveAndAdd(refTypeSig.substring(1), declaringType, imports); //$NON-NLS-1$
				case Signature.C_SUPER:
					return "? super " + resolveAndAdd(refTypeSig.substring(1), declaringType, imports); //$NON-NLS-1$
			}
		}
		return Signature.toString(refTypeSig);
	}
		
	/**
	 * Finds a method in a list of methods.
	 * @param method
	 * @param allMethods
	 * @return The found method or null, if nothing found
	 * @throws JavaModelException
	 */
	private static IMethod findMethod(IMethod method, List allMethods) throws JavaModelException {
		String name= method.getElementName();
		String[] paramTypes= method.getParameterTypes();
		boolean isConstructor= method.isConstructor();

		for (int i= allMethods.size() - 1; i >= 0; i--) {
			IMethod curr= (IMethod) allMethods.get(i);
			if (JavaModelUtil.isSameMethodSignature(name, paramTypes, isConstructor, curr)) {
				return curr;
			}			
		}
		return null;
	}

	/**
	 * Returns all unimplemented constructors of a type including root type default 
	 * constructors if there are no other superclass constructors unimplemented. 
	 * @param type The type to create constructors for
	 * @return Returns the generated stubs or <code>null</code> if the creation has been canceled
	 * @throws CoreException
	 */
	public static IMethod[] getOverridableConstructors(IType type) throws CoreException {
		List constructorMethods= new ArrayList();
		ITypeHierarchy hierarchy= type.newSupertypeHierarchy(null);				
		IType supertype= hierarchy.getSuperclass(type);
		if (supertype == null)
			return (new IMethod[0]);

		IMethod[] superMethods= supertype.getMethods();
		boolean constuctorFound= false;
		String typeName= type.getElementName();
		IMethod[] methods= type.getMethods();
		for (int i= 0; i < superMethods.length; i++) {
				IMethod curr= superMethods[i];
				if (curr.isConstructor())  {
					constuctorFound= true;
					if (JavaModelUtil.isVisibleInHierarchy(curr, type.getPackageFragment()))
						if (JavaModelUtil.findMethod(typeName, curr.getParameterTypes(), true, methods) == null)
							constructorMethods.add(curr);
		
				}
		}
		
		// http://bugs.eclipse.org/bugs/show_bug.cgi?id=38487
		if (!constuctorFound)  {
			IType objectType= type.getJavaProject().findType("java.lang.Object"); //$NON-NLS-1$
			IMethod curr= objectType.getMethod("Object", EMPTY);  //$NON-NLS-1$
			if (JavaModelUtil.findMethod(typeName, curr.getParameterTypes(), true, methods) == null) {
				constructorMethods.add(curr);
			}
		}
		return (IMethod[]) constructorMethods.toArray(new IMethod[constructorMethods.size()]);
	}

	/**
	 * Returns all overridable methods of a type
	 * @param type The type to search the overridable methods for 
	 * @param hierarchy The type hierarchy of the type
 	 * @param isSubType If set, the result can include methods of the passed type, if not only methods from super
	 * types are considered
	 * @return Returns the all methods that can be overridden
	 * @throws JavaModelException
	 */
	public static IMethod[] getOverridableMethods(IType type, ITypeHierarchy hierarchy, boolean isSubType) throws JavaModelException {
		List allMethods= new ArrayList();

		IMethod[] typeMethods= type.getMethods();
		for (int i= 0; i < typeMethods.length; i++) {
			IMethod curr= typeMethods[i];
			if (!curr.isConstructor() && !Flags.isStatic(curr.getFlags()) && !Flags.isPrivate(curr.getFlags())) {
				allMethods.add(curr);
			}
		}

		IType[] superTypes= hierarchy.getAllSuperclasses(type);
		for (int i= 0; i < superTypes.length; i++) {
			IMethod[] methods= superTypes[i].getMethods();
			for (int k= 0; k < methods.length; k++) {
				IMethod curr= methods[k];
				if (!curr.isConstructor() && !Flags.isStatic(curr.getFlags()) && !Flags.isPrivate(curr.getFlags())) {
					if (findMethod(curr, allMethods) == null) {
						allMethods.add(curr);
					}
				}
			}
		}

		IType[] superInterfaces= hierarchy.getAllSuperInterfaces(type);
		for (int i= 0; i < superInterfaces.length; i++) {
			IMethod[] methods= superInterfaces[i].getMethods();
			for (int k= 0; k < methods.length; k++) {
				IMethod curr= methods[k];

				// binary interfaces can contain static initializers (variable intializations)
				// 1G4CKUS
				if (!Flags.isStatic(curr.getFlags())) {
					IMethod impl= findMethod(curr, allMethods);
					if (impl == null || !JavaModelUtil.isVisibleInHierarchy(impl, type.getPackageFragment()) || prefereInterfaceMethod(hierarchy, curr, impl)) {
						if (impl != null) {
							allMethods.remove(impl);
						}
						// implement an interface method when it does not exist in the hierarchy
						// or when it throws less exceptions that the implemented
						allMethods.add(curr);
					}
				}
			}
		}
		if (!isSubType) {
			allMethods.removeAll(Arrays.asList(typeMethods));
		}
		// remove finals
		for (int i= allMethods.size() - 1; i >= 0; i--) {
			IMethod curr= (IMethod) allMethods.get(i);
			if (Flags.isFinal(curr.getFlags())) {
				allMethods.remove(i);
			}
		}
		return (IMethod[]) allMethods.toArray(new IMethod[allMethods.size()]);
	}
	
	private static boolean prefereInterfaceMethod(ITypeHierarchy hierarchy, IMethod interfaceMethod, IMethod curr) throws JavaModelException {
		if (Flags.isFinal(curr.getFlags())) {
			return false;
		}
		IType interfaceType= interfaceMethod.getDeclaringType();
		IType[] interfaces= hierarchy.getAllSuperInterfaces(curr.getDeclaringType());
		for (int i= 0; i < interfaces.length; i++) {
			if (interfaces[i] == interfaceType) {
				return false;
			}
		}
		return curr.getExceptionTypes().length > interfaceMethod.getExceptionTypes().length;
	}	

	/**
	 * Returns the line delimiter which is used in the specified project.
	 * 
	 * @param project the java project, or <code>null</code>
	 * @return the used line delimiter
	 */
	public static String getLineDelimiterUsed(IJavaProject project) {
		return getProjectLineDelimiter(project);
	}

	private static String getProjectLineDelimiter(IJavaProject javaProject) {
		IProject project= null;
		if (javaProject != null)
			project= javaProject.getProject();
		
		String lineDelimiter= getLineDelimiterPreference(project);
		if (lineDelimiter != null)
			return lineDelimiter;
		
		return System.getProperty("line.separator", "\n"); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	public static String getLineDelimiterPreference(IProject project) {
		IScopeContext[] scopeContext;
		if (project != null) {
			// project preference
			scopeContext= new IScopeContext[] { new ProjectScope(project) };
			String lineDelimiter= Platform.getPreferencesService().getString(Platform.PI_RUNTIME, Platform.PREF_LINE_SEPARATOR, null, scopeContext);
			if (lineDelimiter != null)
				return lineDelimiter;
		}
		// workspace preference
		scopeContext= new IScopeContext[] { new InstanceScope() };
		String platformDefault= System.getProperty("line.separator", "\n"); //$NON-NLS-1$ //$NON-NLS-2$
		return Platform.getPreferencesService().getString(Platform.PI_RUNTIME, Platform.PREF_LINE_SEPARATOR, platformDefault, scopeContext);
	}
	

	/**
	 * Examines a string and returns the first line delimiter found.
	 */
	public static String getLineDelimiterUsed(IJavaElement elem) {
		ICompilationUnit cu= null;
		if (elem != null)
			cu= (ICompilationUnit)elem.getAncestor(IJavaElement.COMPILATION_UNIT);
		if (cu != null && cu.exists()) {
			try {
				IBuffer buf= cu.getBuffer();
				int length= buf.getLength();
				for (int i= 0; i < length; i++) {
					char ch= buf.getChar(i);
					if (ch == SWT.CR) {
						if (i + 1 < length) {
							if (buf.getChar(i + 1) == SWT.LF) {
								return "\r\n"; //$NON-NLS-1$
							}
						}
						return "\r"; //$NON-NLS-1$
					} else if (ch == SWT.LF) {
						return "\n"; //$NON-NLS-1$
					}
				}
			} catch (JavaModelException exception) {
				// Use project setting
			}
			return getProjectLineDelimiter(cu.getJavaProject());
		}
		return getProjectLineDelimiter(null);
	}

	/**
	 * Evaluates the indentation used by a Java element. (in tabulators)
	 */	
	public static int getIndentUsed(IJavaElement elem) throws JavaModelException {
		if (elem instanceof ISourceReference) {
			ICompilationUnit cu= (ICompilationUnit) elem.getAncestor(IJavaElement.COMPILATION_UNIT);
			if (cu != null) {
				IBuffer buf= cu.getBuffer();
				int offset= ((ISourceReference)elem).getSourceRange().getOffset();
				int i= offset;
				// find beginning of line
				while (i > 0 && !Strings.isLineDelimiterChar(buf.getChar(i - 1)) ){
					i--;
				}
				return Strings.computeIndentUnits(buf.getText(i, offset - i), elem.getJavaProject());
			}
		}
		return 0;
	}
		
	/**
	 * Returns the element after the give element.
	 */
	public static IJavaElement findNextSibling(IJavaElement member) throws JavaModelException {
		IJavaElement parent= member.getParent();
		if (parent instanceof IParent) {
			IJavaElement[] elements= ((IParent)parent).getChildren();
			for (int i= elements.length - 2; i >= 0 ; i--) {
				if (member.equals(elements[i])) {
					return elements[i+1];
				}
			}
		}
		return null;
	}
	
	public static String getTodoTaskTag(IJavaProject project) {
		String markers= null;
		if (project == null) {
			markers= JavaCore.getOption(JavaCore.COMPILER_TASK_TAGS);
		} else {
			markers= project.getOption(JavaCore.COMPILER_TASK_TAGS, true);
		}
		
		if (markers != null && markers.length() > 0) {
			int idx= markers.indexOf(',');
			if (idx == -1) {
				return markers;
			} else {
				return markers.substring(0, idx);
			}
		}
		return null;
	}
	
	/*
	 * Workarounds for bug 38111
	 */
	public static String[] getArgumentNameSuggestions(IJavaProject project, String baseName, int dimensions, String[] excluded) {
		String name= workaround38111(baseName);
		String[] res= NamingConventions.suggestArgumentNames(project, "", name, dimensions, excluded); //$NON-NLS-1$
		return sortByLength(res); // longest first
	}
		 
	public static String[] getFieldNameSuggestions(IJavaProject project, String baseName, int dimensions, int modifiers, String[] excluded) {
		String name= workaround38111(baseName);
		String[] res;
		if (modifiers == (Flags.AccStatic | Flags.AccFinal)) {
			//TODO: workaround JDT/Core bug 85946:
			List excludedList= Arrays.asList(excluded);
			String[] camelCase= NamingConventions.suggestLocalVariableNames(project, "", name, dimensions, new String[0]); //$NON-NLS-1$
			ArrayList result= new ArrayList(camelCase.length);
			for (int i= 0; i < camelCase.length; i++) {
				String upper= getUpperFromCamelCase(camelCase[i]);
				if (! excludedList.contains(upper))
					result.add(upper);
			}
			res= (String[]) result.toArray(new String[result.size()]);
		} else {
			res= NamingConventions.suggestFieldNames(project, "", name, dimensions, modifiers, excluded); //$NON-NLS-1$
		}
		return sortByLength(res); // longest first
	}
	
	private static String getUpperFromCamelCase(String string) {
		StringBuffer result= new StringBuffer();
		boolean lastWasLowerCase= false;
		for (int i= 0; i < string.length(); i++) {
			char ch= string.charAt(i);
			if (Character.isUpperCase(ch)) {
				if (lastWasLowerCase)
					result.append('_');
				result.append(ch);
			} else {
				result.append(Character.toUpperCase(ch));
			}
			lastWasLowerCase= Character.isLowerCase(ch);
		}
		return result.toString();
	}
	
	public static String[] getLocalNameSuggestions(IJavaProject project, String baseName, int dimensions, String[] excluded) {
		String name= workaround38111(baseName);
		String[] res= NamingConventions.suggestLocalVariableNames(project, "", name, dimensions, excluded); //$NON-NLS-1$
		return sortByLength(res); // longest first
	}
	
	private static String[] sortByLength(String[] proposals) {
		Arrays.sort(proposals, new Comparator() {
			public int compare(Object o1, Object o2) {
				return ((String) o2).length() - ((String) o1).length();
			}
		});
		return proposals;
	}
	
	private static String workaround38111(String baseName) {
		if (BASE_TYPES.contains(baseName))
			return baseName;
		return Character.toUpperCase(baseName.charAt(0)) + baseName.substring(1);
	}
	
	private static final List BASE_TYPES= Arrays.asList(
			new String[] {"boolean", "byte", "char", "double", "float", "int", "long", "short"});  //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$ //$NON-NLS-6$ //$NON-NLS-7$ //$NON-NLS-8$

	public static String suggestArgumentName(IJavaProject project, String baseName, String[] excluded) {
		String[] argnames= getArgumentNameSuggestions(project, baseName, 0, excluded);
		if (argnames.length > 0) {
			return argnames[0];
		}
		return baseName;
	}
	
	public static String[] suggestArgumentNames(IJavaProject project, String[] paramNames) {
		String prefixes= project.getOption(JavaCore.CODEASSIST_ARGUMENT_PREFIXES, true);
		if (prefixes == null) {
			prefixes= ""; //$NON-NLS-1$
		}
		String suffixes= project.getOption(JavaCore.CODEASSIST_ARGUMENT_SUFFIXES, true);
		if (suffixes == null) {
			suffixes= ""; //$NON-NLS-1$
		}
		if (prefixes.length() + suffixes.length() == 0) {
			return paramNames;
		}
		
		String[] newNames= new String[paramNames.length];
		// Ensure that the code generation preferences are respected
		for (int i= 0; i < paramNames.length; i++) {
			String curr= paramNames[i];
			if (!hasPrefixOrSuffix(prefixes, suffixes, curr)) {
				newNames[i]= suggestArgumentName(project, paramNames[i], null);
			} else {
				newNames[i]= curr;
			}
		}
		return newNames;
	}
	
	public static boolean hasFieldName(IJavaProject project, String name) {
		String prefixes= project.getOption(JavaCore.CODEASSIST_FIELD_PREFIXES, true);
		String suffixes= project.getOption(JavaCore.CODEASSIST_FIELD_SUFFIXES, true);
		String staticPrefixes= project.getOption(JavaCore.CODEASSIST_STATIC_FIELD_PREFIXES, true);
		String staticSuffixes= project.getOption(JavaCore.CODEASSIST_STATIC_FIELD_SUFFIXES, true);
		
		
		return hasPrefixOrSuffix(prefixes, suffixes, name) 
			|| hasPrefixOrSuffix(staticPrefixes, staticSuffixes, name);
	}
	
	public static boolean hasParameterName(IJavaProject project, String name) {
		String prefixes= project.getOption(JavaCore.CODEASSIST_ARGUMENT_PREFIXES, true);
		String suffixes= project.getOption(JavaCore.CODEASSIST_ARGUMENT_SUFFIXES, true);
		return hasPrefixOrSuffix(prefixes, suffixes, name);
	}
	
	public static boolean hasLocalVariableName(IJavaProject project, String name) {
		String prefixes= project.getOption(JavaCore.CODEASSIST_LOCAL_PREFIXES, true);
		String suffixes= project.getOption(JavaCore.CODEASSIST_LOCAL_SUFFIXES, true);
		return hasPrefixOrSuffix(prefixes, suffixes, name);
	}
	
	public static boolean hasConstantName(String name) {
		return Character.isUpperCase(name.charAt(0));
	}
	
	
	private static boolean hasPrefixOrSuffix(String prefixes, String suffixes, String name) {
		final String listSeparartor= ","; //$NON-NLS-1$

		StringTokenizer tok= new StringTokenizer(prefixes, listSeparartor);
		while (tok.hasMoreTokens()) {
			String curr= tok.nextToken();
			if (name.startsWith(curr)) {
				return true;
			}
		}

		tok= new StringTokenizer(suffixes, listSeparartor);
		while (tok.hasMoreTokens()) {
			String curr= tok.nextToken();
			if (name.endsWith(curr)) {
				return true;
			}
		}
		return false;
	}
	
	public static boolean useThisForFieldAccess(IJavaProject project) {
		return Boolean.valueOf(PreferenceConstants.getPreference(PreferenceConstants.CODEGEN_KEYWORD_THIS, project)).booleanValue(); 
	}
	
	public static boolean useIsForBooleanGetters(IJavaProject project) {
		return Boolean.valueOf(PreferenceConstants.getPreference(PreferenceConstants.CODEGEN_IS_FOR_GETTERS, project)).booleanValue(); 
	}
	
	public static String getExceptionVariableName(IJavaProject project) {
		return PreferenceConstants.getPreference(PreferenceConstants.CODEGEN_EXCEPTION_VAR_NAME, project); 
	}
	
	public static boolean doAddComments(IJavaProject project) {
		return Boolean.valueOf(PreferenceConstants.getPreference(PreferenceConstants.CODEGEN_ADD_COMMENTS, project)).booleanValue(); 
	}
	
	public static void setCodeTemplate(String templateId, String pattern, IJavaProject project) {
		TemplateStore codeTemplateStore= JavaPlugin.getDefault().getCodeTemplateStore();
		TemplatePersistenceData data= codeTemplateStore.getTemplateData(templateId);
		Template orig= data.getTemplate();
		Template copy= new Template(orig.getName(), orig.getDescription(), orig.getContextTypeId(), pattern, true);
		data.setTemplate(copy);
	}
	
	private static Template getCodeTemplate(String id, IJavaProject project) {
		if (project == null)
			return JavaPlugin.getDefault().getCodeTemplateStore().findTemplateById(id);
		ProjectTemplateStore projectStore= new ProjectTemplateStore(project.getProject());
		try {
			projectStore.load();
		} catch (IOException e) {
			JavaPlugin.log(e);
		}
		return projectStore.findTemplateById(id);
	}
	
}
