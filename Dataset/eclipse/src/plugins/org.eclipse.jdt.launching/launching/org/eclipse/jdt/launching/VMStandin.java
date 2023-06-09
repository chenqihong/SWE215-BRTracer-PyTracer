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
package org.eclipse.jdt.launching;



/**
 * An implementation of IVMInstall that is used for manipulating VMs without necessarily 
 * committing changes.
 * <p>
 * Instances of this class act like wrappers.  All other instances of IVMInstall represent 
 * 'real live' VMs that may be used for building or launching.  Instances of this class
 * behave like 'temporary' VMs that are not visible and not available for building or launching.
 * </p>
 * <p>
 * Instances of this class may be constructed as a preliminary step to creating a 'live' VM
 * or as a preliminary step to making changes to a 'real' VM.
 * </p>
 * When <code>convertToRealVM</code> is called, a corresponding 'real' VM is created
 * if one did not previously exist, or the corresponding 'real' VM is updated.
 * </p>
 * <p>
 * Clients may instantiate this class; it is not intended to be subclassed.
 * </p>
 * 
 * @since 2.1
 */
public class VMStandin extends AbstractVMInstall {
    
    /**
     * <code>java.version</code> system property, or <code>null</code>
     * @since 3.1
     */
    private String fJavaVersion = null;

	/*
	 * @see org.eclipse.jdt.launching.AbstractVMInstall#AbstractVMInstall(org.eclipse.jdt.launching.IVMInstallType, java.lang.String)
	 */
	public VMStandin(IVMInstallType type, String id) {
		super(type, id);
		setNotify(false);
	}
	
	/**
	 * Construct a <code>VMStandin</code> instance based on the specified <code>IVMInstall</code>.
	 * Changes to this standin will not be reflected in the 'real' VM until <code>convertToRealVM</code>
	 * is called.
	 * 
	 * @param realVM the 'real' VM from which to construct this standin VM
	 */
	public VMStandin(IVMInstall realVM) {
		this (realVM.getVMInstallType(), realVM.getId());
		setName(realVM.getName());
		setInstallLocation(realVM.getInstallLocation());
		setLibraryLocations(realVM.getLibraryLocations());
		setJavadocLocation(realVM.getJavadocLocation());
		if (realVM instanceof IVMInstall2) {
			IVMInstall2 vm2 = (IVMInstall2) realVM;
			setVMArgs(vm2.getVMArgs());
	        fJavaVersion = vm2.getJavaVersion();			
		} else {
			setVMArguments(realVM.getVMArguments());
			fJavaVersion = null;
		}
	}
	
	/**
	 * If no corresponding 'real' VM exists, create one and populate it from this standin instance. 
	 * If a corresponding VM exists, update its attributes from this standin instance.
	 * 
	 * @return IVMInstall the 'real' corresponding to this standin VM
	 */
	public IVMInstall convertToRealVM() {
		IVMInstallType vmType= getVMInstallType();
		IVMInstall realVM= vmType.findVMInstall(getId());
		boolean notify = true;
		
		if (realVM == null) {
			realVM= vmType.createVMInstall(getId());
			notify = false;
		}
		// do not notify of property changes on new VMs
		if (realVM instanceof AbstractVMInstall) {
			 ((AbstractVMInstall)realVM).setNotify(notify);
		}
		realVM.setName(getName());
		realVM.setInstallLocation(getInstallLocation());
		realVM.setLibraryLocations(getLibraryLocations());
		realVM.setJavadocLocation(getJavadocLocation());
		if (realVM instanceof IVMInstall2) {
			IVMInstall2 vm2 = (IVMInstall2) realVM;
			vm2.setVMArgs(getVMArgs());
		} else {
			realVM.setVMArguments(getVMArguments());
		}
		
		if (realVM instanceof AbstractVMInstall) {
			 ((AbstractVMInstall)realVM).setNotify(true);
		}		
		if (!notify) {
			JavaRuntime.fireVMAdded(realVM);
		}
		return realVM;
	}
		
    /* (non-Javadoc)
     * @see org.eclipse.jdt.launching.IVMInstall#getJavaVersion()
     */
    public String getJavaVersion() {
        return fJavaVersion;
    }
}
