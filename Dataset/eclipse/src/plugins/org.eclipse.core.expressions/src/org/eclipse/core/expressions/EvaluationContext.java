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
package org.eclipse.core.expressions;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.CoreException;

import org.eclipse.core.internal.expressions.Assert;

/**
 * A default implementation of an evaluation context.
 * <p>
 * Clients may instantiate this default context. The class is
 * not intended to be subclassed by clients.
 * </p> 
 * 
 * @since 3.0
 */
public class EvaluationContext implements IEvaluationContext {

	private IEvaluationContext fParent;
	private Object fDefaultVariable;
	private Map/*<String, Object>*/ fVariables;
	private IVariableResolver[] fVariableResolvers;
	
	/**
	 * Create a new evaluation context with the given parent and default
	 * variable.
	 * 
	 * @param parent the parent context. Can be <code>null</code>.
	 * @param defaultVariable the default variable
	 */
	public EvaluationContext(IEvaluationContext parent, Object defaultVariable) {
		Assert.isNotNull(defaultVariable);
		fParent= parent;
		fDefaultVariable= defaultVariable;
	}
	
	/**
	 * Create a new evaluation context with the given parent and default
	 * variable.
	 * 
	 * @param parent the parent context. Can be <code>null</code>.
	 * @param defaultVariable the default variable
	 * @param resolvers an array of <code>IVariableResolvers</code> to
	 *  resolve additional variables.
	 * 
	 * @see #resolveVariable(String, Object[])
	 */
	public EvaluationContext(IEvaluationContext parent, Object defaultVariable, IVariableResolver[] resolvers) {
		Assert.isNotNull(defaultVariable);
		Assert.isNotNull(resolvers);
		fParent= parent;
		fDefaultVariable= defaultVariable;
		fVariableResolvers= resolvers;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public IEvaluationContext getParent() {
		return fParent;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public IEvaluationContext getRoot() {
		if (fParent == null)
			return this;
		return fParent.getRoot();
	}
	
	/**
	 * {@inheritDoc}
	 */
	public Object getDefaultVariable() {
		return fDefaultVariable;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public void addVariable(String name, Object value) {
		Assert.isNotNull(name);
		Assert.isNotNull(value);
		if (fVariables == null)
			fVariables= new HashMap();
		fVariables.put(name, value);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public Object removeVariable(String name) {
		Assert.isNotNull(name);
		if (fVariables == null)
			return null;
		return fVariables.remove(name);
	}
	
	/**
	 * {@inheritDoc}
	 */
	public Object getVariable(String name) {
		Assert.isNotNull(name);
		Object result= null;
		if (fVariables != null) {
			result= fVariables.get(name);
		}
		if (result != null)
			return result;
		if (fParent != null)
			return fParent.getVariable(name);
		return null;
	}
	
	/**
	 * {@inheritDoc}
	 */
	public Object resolveVariable(String name, Object[] args) throws CoreException {
		if (fVariableResolvers != null && fVariableResolvers.length > 0) {
			for (int i= 0; i < fVariableResolvers.length; i++) {
				IVariableResolver resolver= fVariableResolvers[i];
				Object variable= resolver.resolve(name, args);
				if (variable != null)
					return variable;
			}
		}
		if (fParent != null)
			return fParent.resolveVariable(name, args);
		return null;
	}
}
