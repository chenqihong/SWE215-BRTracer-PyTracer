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
package org.eclipse.team.internal.ccvs.core.connection;


import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ILock;
import org.eclipse.core.runtime.preferences.DefaultScope;
import org.eclipse.osgi.util.NLS;
import org.eclipse.team.core.TeamException;
import org.eclipse.team.internal.ccvs.core.*;
import org.eclipse.team.internal.ccvs.core.client.*;
import org.eclipse.team.internal.ccvs.core.resources.*;
import org.eclipse.team.internal.ccvs.core.util.Assert;
import org.eclipse.team.internal.ccvs.core.util.KnownRepositories;
import org.osgi.service.prefs.BackingStoreException;
import org.osgi.service.prefs.Preferences;

/**
 * This class manages a CVS repository location.
 * 
 * It provides the mapping between connection method name and the
 * plugged in ICunnectionMethod.
 * 
 * It parses location strings into instances.
 * 
 * It provides a method to open a connection to the server along
 * with a method to validate that connections can be made.
 * 
 * It manages its user info using the plugged in IUserAuthenticator
 * (unless a username and password are provided as part of the creation
 * string, in which case, no authenticator is used).
 * 
 * Instances must be disposed of when no longer needed in order to 
 * notify the authenticator so cached properties can be cleared
 * 
 */
public class CVSRepositoryLocation extends PlatformObject implements ICVSRepositoryLocation, IUserInfo {

	/**
	 * The name of the preferences node in the CVS preferences that contains
	 * the known repositories as its children.
	 */
	public static final String PREF_REPOSITORIES_NODE = "repositories"; //$NON-NLS-1$
	
	/*
	 * The name of the node in the default scope that has the default settings
	 * for a repository.
	 */
	private static final String DEFAULT_REPOSITORY_SETTINGS_NODE = "default_repository_settings"; //$NON-NLS-1$

	// Preference keys used to persist the state of the location
	public static final String PREF_LOCATION = "location"; //$NON-NLS-1$
	public static final String PREF_SERVER_ENCODING = "encoding"; //$NON-NLS-1$
	
	// server platform constants
	public static final int UNDETERMINED_PLATFORM = 0;
	public static final int CVS_SERVER = 1;
	public static final int CVSNT_SERVER = 2;
	public static final int UNSUPPORTED_SERVER = 3;
	public static final int UNKNOWN_SERVER = 4;
	
	// static variables for extension points
	private static IUserAuthenticator authenticator;
	private static IConnectionMethod[] pluggedInConnectionMethods = null;
	
	// Locks for ensuring that authentication to a host is serialized
	// so that invalid passwords do not result in account lockout
	private static Map hostLocks = new HashMap(); 

	private IConnectionMethod method;
	private String user;
	private String password;
	private String host;
	private int port;
	private String root;
	private boolean userFixed;
	private boolean passwordFixed;
	private boolean allowCaching;
	
	private int serverPlatform = UNDETERMINED_PLATFORM;
	
	public static final char COLON = ':';
	public static final char HOST_SEPARATOR = '@';
	public static final char PORT_SEPARATOR = '#';
	public static final boolean STANDALONE_MODE = (System.getProperty("eclipse.cvs.standalone")==null) ? //$NON-NLS-1$ 
		false	:(new Boolean(System.getProperty("eclipse.cvs.standalone")).booleanValue()); //$NON-NLS-1$ 
	
	// command to start remote cvs in server mode
	private static final String INVOKE_SVR_CMD = "server"; //$NON-NLS-1$
	
	// fields needed for caching the password
	public static final String INFO_PASSWORD = "org.eclipse.team.cvs.core.password";//$NON-NLS-1$ 
	public static final String INFO_USERNAME = "org.eclipse.team.cvs.core.username";//$NON-NLS-1$ 
	public static final String AUTH_SCHEME = "";//$NON-NLS-1$ 
	public static final URL FAKE_URL;

	/*
	 * Fields used to create the EXT command invocation
	 */
	public static final String USER_VARIABLE = "{user}"; //$NON-NLS-1$
	public static final String PASSWORD_VARIABLE = "{password}"; //$NON-NLS-1$
	public static final String HOST_VARIABLE = "{host}"; //$NON-NLS-1$
	public static final String PORT_VARIABLE = "{port}"; //$NON-NLS-1$

	/*
	 * Field that indicates which connection method is to be used for 
	 * locations that use the EXT connection method.
	 */
	private static String extProxy;
	
	/*
	 * Field that indicates that the last connection attempt made to 
	 * this repository location failed due to an authentication failure. 
	 * When this is set, subsequent attempts should prompt before attempting to connect
	 */
	private boolean previousAuthenticationFailed = false;
	
	static {
		URL temp = null;
		try {
			temp = new URL("http://org.eclipse.team.cvs.core");//$NON-NLS-1$ 
		} catch (MalformedURLException e) {
			// Should never fail
		}
		FAKE_URL = temp;
	} 
	
	/**
	 * Return the preferences node whose child nodes are teh know repositories
	 * @return a preferences node
	 */
	public static Preferences getParentPreferences() {
		return CVSProviderPlugin.getPlugin().getInstancePreferences().node(PREF_REPOSITORIES_NODE);
	}
	
	/**
	 * Return a preferences node that contains suitabel defaults for a
	 * repository location.
	 * @return  a preferences node
	 */
	public static Preferences getDefaultPreferences() {
		Preferences defaults = new DefaultScope().getNode(CVSProviderPlugin.ID).node(DEFAULT_REPOSITORY_SETTINGS_NODE);
		defaults.put(PREF_SERVER_ENCODING, getDefaultEncoding());
		return defaults;
	}
	
	private static String getDefaultEncoding() {
		return System.getProperty("file.encoding", "UTF-8"); //$NON-NLS-1$ //$NON-NLS-2$
	}
	
	/**
	 * Set the proxy connection method that is to be used when a 
	 * repository location has the ext connection method. This is
	 * usefull with the extssh connection method as it can be used to 
	 * kepp the sandbox compatible with the command line client.
	 * @param string
	 */
	public static void setExtConnectionMethodProxy(String string) {
		extProxy = string;
	}
	
	/**
	 * Create a repository location instance from the given properties.
	 * The supported properties are:
	 * 
	 *   connection The connection method to be used
	 *   user The username for the connection (optional)
	 *   password The password used for the connection (optional)
	 *   host The host where the repository resides
	 *   port The port to connect to (optional)
	 *   root The server directory where the repository is located
	 *   encoding The file system encoding of the server
	 */
	public static CVSRepositoryLocation fromProperties(Properties configuration) throws CVSException {
		// We build a string to allow validation of the components that are provided to us
		String connection = configuration.getProperty("connection");//$NON-NLS-1$ 
		if (connection == null)
			connection = "pserver";//$NON-NLS-1$ 
		IConnectionMethod method = getPluggedInConnectionMethod(connection);
		if (method == null)
			throw new CVSException(new Status(IStatus.ERROR, CVSProviderPlugin.ID, TeamException.UNABLE, NLS.bind(CVSMessages.CVSRepositoryLocation_methods, (new Object[] {getPluggedInConnectionMethodNames()})), null));//$NON-NLS-1$ 
		String user = configuration.getProperty("user");//$NON-NLS-1$ 
		if (user.length() == 0)
			user = null;
		String password = configuration.getProperty("password");//$NON-NLS-1$ 
		if (user == null)
			password = null;
		String host = configuration.getProperty("host");//$NON-NLS-1$ 
		if (host == null)
			throw new CVSException(new Status(IStatus.ERROR, CVSProviderPlugin.ID, TeamException.UNABLE, CVSMessages.CVSRepositoryLocation_hostRequired, null));//$NON-NLS-1$ 
		String portString = configuration.getProperty("port");//$NON-NLS-1$ 
		int port;
		if (portString == null)
			port = ICVSRepositoryLocation.USE_DEFAULT_PORT;
		else
			port = Integer.parseInt(portString);
		String root = configuration.getProperty("root");//$NON-NLS-1$ 
		if (root == null)
			throw new CVSException(new Status(IStatus.ERROR, CVSProviderPlugin.ID, TeamException.UNABLE, CVSMessages.CVSRepositoryLocation_rootRequired, null));//$NON-NLS-1$ 

		String encoding = configuration.getProperty("encoding"); //$NON-NLS-1$
		
		return new CVSRepositoryLocation(method, user, password, host, port, root, encoding, user != null, false);
	}
	
	/**
	 * Parse a location string and return a CVSRepositoryLocation.
	 * 
	 * On failure, the status of the exception will be a MultiStatus
	 * that includes the original parsing error and a general status
	 * displaying the passed location and proper form. This form is
	 * better for logging, etc.
	 */
	public static CVSRepositoryLocation fromString(String location) throws CVSException {	
		try {
			return fromString(location, false);
		} catch (CVSException e) {
			// Parsing failed. Include a status that
			// shows the passed location and the proper form
			MultiStatus error = new MultiStatus(CVSProviderPlugin.ID, IStatus.ERROR, NLS.bind(CVSMessages.CVSRepositoryLocation_invalidFormat, (new Object[] {location})), null);//$NON-NLS-1$ 
			error.merge(new CVSStatus(IStatus.ERROR, CVSMessages.CVSRepositoryLocation_locationForm));//$NON-NLS-1$ 
			error.merge(e.getStatus());
			throw new CVSException(error);
		}
	}
	
	/**
	 * Parse a location string and return a CVSRepositoryLocation.
	 * 
	 * The valid format (from the cederqvist) is:
	 * 
	 * :method:[[user][:password]@]hostname[:[port]]/path/to/repository
	 * 
	 * However, this does not work with CVS on NT so we use the format
	 * 
	 * :method:[user[:password]@]hostname[#port]:/path/to/repository
	 * 
	 * Some differences to note:
	 *    The : after the host/port is not optional because of NT naming including device
	 *    e.g. :pserver:username:password@hostname#port:D:\cvsroot
	 * 
	 * If validateOnly is true, this method will always throw an exception.
	 * The status of the exception indicates success or failure. The status
	 * of the exception contains a specific message suitable for displaying
	 * to a user who has knowledge of the provided location string.
	 * @see CVSRepositoryLocation.fromString(String)
	 */
	public static CVSRepositoryLocation fromString(String location, boolean validateOnly) throws CVSException {
		String errorMessage = null;
		try {
			// Get the connection method
			errorMessage = CVSMessages.CVSRepositoryLocation_parsingMethod;
			int start = location.indexOf(COLON);
			String methodName;
			int end;
			if (start == 0) {
				end = location.indexOf(COLON, start + 1);
				methodName = location.substring(start + 1, end);
				start = end + 1;
			} else {
				// this could be an alternate format for ext: username:password@host:path
				methodName = "ext"; //$NON-NLS-1$
				start = 0;
			}
			
			IConnectionMethod method = getPluggedInConnectionMethod(methodName);
			if (method == null)
				throw new CVSException(new CVSStatus(IStatus.ERROR, NLS.bind(CVSMessages.CVSRepositoryLocation_methods, (new Object[] {getPluggedInConnectionMethodNames()}))));//$NON-NLS-1$ 
			
			// Get the user name and password (if provided)
			errorMessage = CVSMessages.CVSRepositoryLocation_parsingUser;
			
			end = location.indexOf(HOST_SEPARATOR, start);
			String user = null;
			String password = null;
			// if end is -1 then there is no host separator meaning that the username is not present
			if (end != -1) {		
				// Get the optional user and password
				user = location.substring(start, end);
				// Separate the user and password (if there is a password)
				start = user.indexOf(COLON);
				if (start != -1) {
					errorMessage = CVSMessages.CVSRepositoryLocation_parsingPassword;
					password = user.substring(start+1);
					user = user.substring(0, start);	
				}
				// Set start to point after the host separator
				start = end + 1;
			}
			
			// Get the host (and port)
			errorMessage = CVSMessages.CVSRepositoryLocation_parsingHost;
			end= location.indexOf(COLON, start);
			if (end == -1) {
			    // The last colon is optional so look for the slash that starts the path
			    end = location.indexOf('/', start);
			    // Decrement the end since the slash is part of the path
			    if (end != -1) end--;
			}
			String host = location.substring(start, end);
			int port = USE_DEFAULT_PORT;
			// Separate the port and host if there is a port
			start = host.indexOf(PORT_SEPARATOR);
			boolean havePort = false;
			if (start != -1) {
				try {
					// Initially, we used a # between the host and port
					errorMessage = CVSMessages.CVSRepositoryLocation_parsingPort;
					port = Integer.parseInt(host.substring(start+1));
					host = host.substring(0, start);
					havePort = true;
				} catch (NumberFormatException e) {
					// Ignore this as the #1234 port could be part of a proxy host string
				}
			}
			if (!havePort) {
				// In the correct CVS format, the port follows the COLON
				errorMessage = CVSMessages.CVSRepositoryLocation_parsingPort;
				int index = end;
				char c = location.charAt(++index);
				String portString = new String();
				while (Character.isDigit(c)) {
					portString += c;
					c = location.charAt(++index);
				}
				if (portString.length() > 0) {
					end = index - 1;
					port = Integer.parseInt(portString);
				}
			}
			
			// Get the repository path (translating backslashes to slashes)
			errorMessage = CVSMessages.CVSRepositoryLocation_parsingRoot;
			start = end + 1;
			String root = location.substring(start);
			
			if (validateOnly)
				throw new CVSException(new CVSStatus(IStatus.OK, CVSMessages.ok));//$NON-NLS-1$ 		
			return new CVSRepositoryLocation(method, user, password, host, port, root, null /* encoding */, (user != null), (password != null));
		}
		catch (IndexOutOfBoundsException e) {
			// We'll get here if anything funny happened while extracting substrings
			throw new CVSException(errorMessage);
		}
		catch (NumberFormatException e) {
			// We'll get here if we couldn't parse a number
			throw new CVSException(errorMessage);
		}
	}
	
	/**
	 * Get the plugged-in user authenticator if there is one.
	 * @return the plugged-in user authenticator or <code>null</code>
	 */
	public static IUserAuthenticator getAuthenticator() {
		if (authenticator == null) {
			authenticator = getPluggedInAuthenticator();
		}
		return authenticator;
	}
	
	/**
	 * Return the list of plugged-in connection methods.
	 * @return the list of plugged-in connection methods
	 */
	public static IConnectionMethod[] getPluggedInConnectionMethods() {
		if(pluggedInConnectionMethods==null) {
			List connectionMethods = new ArrayList();
			
			if (STANDALONE_MODE) {				
				connectionMethods.add(new PServerConnectionMethod());
			} else {
				IExtension[] extensions = Platform.getExtensionRegistry().getExtensionPoint(CVSProviderPlugin.ID, CVSProviderPlugin.PT_CONNECTIONMETHODS).getExtensions();
				for(int i=0; i<extensions.length; i++) {
					IExtension extension = extensions[i];
					IConfigurationElement[] configs = extension.getConfigurationElements();
					if (configs.length == 0) {
						CVSProviderPlugin.log(IStatus.ERROR, NLS.bind("Connection method {0} is missing required fields", new Object[] {extension.getUniqueIdentifier()}), null);//$NON-NLS-1$ 
						continue;
					}
					try {
						IConfigurationElement config = configs[0];
						connectionMethods.add(config.createExecutableExtension("run"));//$NON-NLS-1$ 
					} catch (CoreException ex) {
						CVSProviderPlugin.log(IStatus.ERROR, NLS.bind("Could not instantiate connection method for  {0}", new Object[] {extension.getUniqueIdentifier()}), ex);//$NON-NLS-1$ 
					}
				}
			}
			pluggedInConnectionMethods = (IConnectionMethod[])connectionMethods.toArray(new IConnectionMethod[0]);
		}
		return pluggedInConnectionMethods;
	}
	
	/*
	 * Return the connection method registered for the given name 
	 * or <code>null</code> if none is registered with the given name.
	 */
	private static IConnectionMethod getPluggedInConnectionMethod(String methodName) {
		Assert.isNotNull(methodName);
		IConnectionMethod[] methods = getPluggedInConnectionMethods();
		for(int i=0; i<methods.length; i++) {
			if(methodName.equals(methods[i].getName()))
				return methods[i];
		}
		return null;		
	}
	
	/*
	 * Return a string containing a list of all connection methods
	 * that is suitable for inclusion in an error message.
	 */
	private static String getPluggedInConnectionMethodNames() {
		IConnectionMethod[] methods = getPluggedInConnectionMethods();
		StringBuffer methodNames = new StringBuffer();
		for(int i=0; i<methods.length; i++) {
			String name = methods[i].getName();
			if (i>0)
				methodNames.append(", ");//$NON-NLS-1$ 
			methodNames.append(name);
		}		
		return methodNames.toString();
	}
	
	/*
	 * Get the pluged-in authenticator from the plugin manifest.
	 */
	private static IUserAuthenticator getPluggedInAuthenticator() {
		IExtension[] extensions = Platform.getExtensionRegistry().getExtensionPoint(CVSProviderPlugin.ID, CVSProviderPlugin.PT_AUTHENTICATOR).getExtensions();
		if (extensions.length == 0)
			return null;
		IExtension extension = extensions[0];
		IConfigurationElement[] configs = extension.getConfigurationElements();
		if (configs.length == 0) {
			CVSProviderPlugin.log(IStatus.ERROR, NLS.bind("User autheticator {0} is missing required fields", (new Object[] {extension.getUniqueIdentifier()})), null);//$NON-NLS-1$ 
			return null;
		}
		try {
			IConfigurationElement config = configs[0];
			return (IUserAuthenticator) config.createExecutableExtension("run");//$NON-NLS-1$ 
		} catch (CoreException ex) {
			CVSProviderPlugin.log(IStatus.ERROR, NLS.bind("Unable to instantiate user authenticator {0}", (new Object[] {extension.getUniqueIdentifier()})), ex);//$NON-NLS-1$ 
			return null;
		}
	}
	
	/*
	 * Create a CVSRepositoryLocation from its composite parts.
	 */
	private CVSRepositoryLocation(IConnectionMethod method, String user, String password, String host, int port, String root, String encoding, boolean userFixed, boolean passwordFixed) {
		this.method = method;
		this.user = user;
		this.password = password;
		this.host = host;
		this.port = port;
		this.root = root;
		// The username can be fixed only if one is provided
		if (userFixed && (user != null))
			this.userFixed = true;
		// The password can only be fixed if the username is and a password is provided
		if (userFixed && passwordFixed && (password != null))
			this.passwordFixed = true;
		if (encoding != null) {
			setEncoding(encoding);
		}
	}
	
	/*
	 * Create the connection to the remote server.
	 * If anything fails, an exception will be thrown and must
	 * be handled by the caller.
	 */
	private Connection createConnection(String password, IProgressMonitor monitor) throws CVSException {
		IConnectionMethod methodToUse = method;
		if (method.getName().equals("ext") && extProxy != null && !extProxy.equals(method.getName())) { //$NON-NLS-1$
			methodToUse = getPluggedInConnectionMethod(extProxy); 
		}
		Connection connection = new Connection(this, methodToUse.createConnection(this, password));
		connection.open(monitor);
		return connection;
	}
	
	/*
	 * Dispose of the receiver by clearing any cached authorization information.
	 * This method shold only be invoked when the corresponding adapter is shut
	 * down or a connection is being validated.
	 */
	public void dispose() {
		flushCache();
		try {
			if (hasPreferences()) {
				internalGetPreferences().removeNode();
				getParentPreferences().flush();
			}
		} catch (BackingStoreException e) {
			CVSProviderPlugin.log(IStatus.ERROR, NLS.bind(CVSMessages.CVSRepositoryLocation_73, new String[] { getLocation(true) }), e); //$NON-NLS-1$
		}
	}
	
	/*
	 * Flush the keyring entry associated with the receiver
	 */
	private void flushCache() {
		try {
			Platform.flushAuthorizationInfo(FAKE_URL, getLocation(), AUTH_SCHEME);
		} catch (CoreException e) {
			// No need to report this since the location is
			// most likely being disposed. 
			// Just fail silently and continue
			CVSProviderPlugin.log(e);
		}
	}
	
	/*
	 * @see ICVSRepositoryLocation#getHost()
	 */
	public String getHost() {
		return host;
	}

	/*
	 * @see IRepositoryLocation#getLocation()
	 * 
	 * The username is included if it is fixed.
	 * The password is never included even if it is fixed.
	 * The port is included if it is not the default port.
	 */
	public String getLocation() {
		return getLocation(false);
	}
	
	public String getLocation(boolean forDisplay) {
		return COLON + method.getName() + COLON + 
			(userFixed?(user +
				((passwordFixed && !forDisplay)?(COLON + password):"")//$NON-NLS-1$ 
					+ HOST_SEPARATOR):"") +//$NON-NLS-1$ 
			host + COLON +
			((port == USE_DEFAULT_PORT)?"":(new Integer(port).toString())) + //$NON-NLS-1$ 
			root;
	}
	
	/*
	 * @see ICVSRepositoryLocation#getMethod()
	 */
	public IConnectionMethod getMethod() {
		return method;
	}

	/*
	 * @see ICVSRepositoryLocation#getPort()
	 */
	public int getPort() {
		return port;
	}
	
	/*
	 * @see ICVSRepositoryLocation#getEncoding()
	 */
	public String getEncoding() {
		if (hasPreferences()) {
			return internalGetPreferences().get(PREF_SERVER_ENCODING, getDefaultEncoding());
		} else {
			return getDefaultEncoding();
		}
	}

	/*
	 * @see ICVSRepositoryLocation#setEncoding()
	 */
	public void setEncoding(String encoding) {
		if (encoding == null || encoding == getDefaultEncoding()) {
			if (hasPreferences()) {
				internalGetPreferences().remove(PREF_SERVER_ENCODING);
			}
		} else {
			ensurePreferencesStored();
			internalGetPreferences().put(PREF_SERVER_ENCODING, encoding);
			flushPreferences();
		}
	}	

	/*
	 * @see ICVSRepositoryLocation#members(CVSTag, boolean, IProgressMonitor)
	 */
	public ICVSRemoteResource[] members(CVSTag tag, boolean modules, IProgressMonitor progress) throws CVSException {
		try {
			if (modules) {
				return RemoteModule.getRemoteModules(this, tag, progress);
			} else {
				RemoteFolder root = new RemoteFolder(null, this, ICVSRemoteFolder.REPOSITORY_ROOT_FOLDER_NAME, tag);
				ICVSRemoteResource[] resources = root.members(progress);
				// There is the off chance that there is a file in the root of the repository.
				// This is not supported by cvs so we need to make sure there are no files
				List folders = new ArrayList(resources.length);
				for (int i = 0; i < resources.length; i++) {
					ICVSRemoteResource remoteResource = resources[i];
					if (remoteResource.isContainer()) {
						folders.add(remoteResource);
					}
				}
				return (ICVSRemoteResource[]) folders.toArray(new ICVSRemoteResource[folders.size()]);
			}
		} catch(TeamException e) {
			throw new CVSException(e.getStatus());
		}
	}
	
	/*
	 * @see ICVSRepositoryLocation#getRemoteFolder(String, CVSTag)
	 */
	public ICVSRemoteFolder getRemoteFolder(String remotePath, CVSTag tag) {
		return new RemoteFolder(null, this, remotePath, tag);		
	}
	
	/*
	 * @see ICVSRepositoryLocation#getRemoteFile(String, CVSTag)
	 */
	public ICVSRemoteFile getRemoteFile(String remotePath, CVSTag tag) {
		IPath path = new Path(null, remotePath);
		RemoteFolderTree remoteFolder = new RemoteFolderTree(null, this, path.removeLastSegments(1).toString(), tag);
		RemoteFile remoteFile = new RemoteFile(remoteFolder, Update.STATE_ADDED_LOCAL, path.lastSegment(), null, null, tag);
		remoteFolder.setChildren(new ICVSRemoteResource[] { remoteFile });
		return remoteFile;
	}
	
	/*
	 * @see ICVSRepositoryLocation#getRootDirectory()
	 */
	public String getRootDirectory() {
		return root;
	}
	
	/*
	 * @see ICVSRepositoryLocation#getTimeout()
	 * 
	 * For the time being, the timeout value is a system wide value
	 * associated with the CVSPlugin singleton.
	 */
	public int getTimeout() {
		return CVSProviderPlugin.getPlugin().getTimeout();
	}
	
	/*
	 * @see ICVSRepositoryLocation#getUserInfo()
	 */
	public IUserInfo getUserInfo(boolean makeUsernameMutable) {
		return new UserInfo(getUsername(), password, makeUsernameMutable ? true : isUsernameMutable());
	}
	
	/*
	 * @see ICVSRepositoryLocation#getUsername()
	 * @see IUserInfo#getUsername()
	 */
	public String getUsername() {
		// If the username is mutable, get it from the cache if it's there
		if (user == null && isUsernameMutable()) {
			retrievePassword();
		}
		return user == null ? "" : user; //$NON-NLS-1$
	}
	
	/*
	 * @see IUserInfo#isUsernameMutable()
	 */
	public boolean isUsernameMutable() {
		return !userFixed;
	}

	/*
	 * Open a connection to the repository represented by the receiver.
	 * If the username or password are not fixed, openConnection will
	 * use the plugged-in authenticator to prompt for the username and/or
	 * password if one has not previously been provided or if the previously
	 * supplied username and password are invalid.
	 * 
	 * This method is synchronized to ensure that authentication with the 
	 * remote server is serialized. This is needed to avoid the situation where
	 * multiple failed authentications occur and result in the remote account
	 * being locked. The CVSProviderPlugin enforces that there is one instance
	 * of a CVSRepositoryLocation per remote location thus this method is called
	 * for any connection made to this remote location.
	 */
	public Connection openConnection(IProgressMonitor monitor) throws CVSException {
        // Get the lock for the host to ensure that we are not connecting to the same host concurrently.
        Policy.checkCanceled(monitor);
		ILock hostLock;
		synchronized(hostLocks) {
			hostLock = (ILock)hostLocks.get(getHost());
			if (hostLock == null) {
				hostLock = Platform.getJobManager().newLock();
				hostLocks.put(getHost(), hostLock);
			}
		}
		try {
		    hostLock.acquire();
			// Allow two ticks in case of a retry
			monitor.beginTask(NLS.bind(CVSMessages.CVSRepositoryLocation_openingConnection, new String[] { getHost() }), 2);//$NON-NLS-1$
			ensureLocationCached();
			boolean cacheNeedsUpdate = false;
			// If the previous connection failed, prompt before attempting to connect
			if (previousAuthenticationFailed) {
				promptForUserInfo(null);
				// The authentication information has been change so update the cache
				cacheNeedsUpdate = true;
			}
			while (true) {
				try {
					// The following will throw an exception if authentication fails
					String password = this.password;
					if (password == null) {
						// If the instance has no password, obtain it from the cache
						password = retrievePassword();
					}
					if (user == null) {
						// This is possible if the cache was cleared somehow for a location with a mutable username
						throw new CVSAuthenticationException(new CVSStatus(IStatus.ERROR, CVSAuthenticationException.RETRY, CVSMessages.CVSRepositoryLocation_usernameRequired)); //$NON-NLS-1$
					}
					if (password == null)
						password = "";//$NON-NLS-1$ 
					Connection connection = createConnection(password, monitor);
					if (cacheNeedsUpdate)
					    updateCachedLocation();
					previousAuthenticationFailed = false;
                    return connection;
				} catch (CVSAuthenticationException ex) {
					previousAuthenticationFailed = true;
					if (ex.getStatus().getCode() == CVSAuthenticationException.RETRY) {
						String message = ex.getMessage();
						promptForUserInfo(message);
						// The authentication information has been change so update the cache
						cacheNeedsUpdate = true;
					} else {
						throw ex;
					}
				}
			}
		} finally {
            hostLock.release();
			monitor.done();
		}
	}

	/*
	 * Prompt for the user authentication information (i.e. user name and password).
	 */
	private void promptForUserInfo(String message) throws CVSException {
		IUserAuthenticator authenticator = getAuthenticator();
		if (authenticator == null) {
			throw new CVSAuthenticationException(CVSMessages.CVSRepositoryLocation_noAuthenticator, CVSAuthenticationException.NO_RETRY);//$NON-NLS-1$ 
		}
		authenticator.promptForUserInfo(this, this, message);
	}

    /*
	 * Ensure that this location is in the known repositories list
	 * and that the authentication information matches what is in the
	 * cache, if this instance is not the instance in the cache.
     */
    private void ensureLocationCached() {
        String location = getLocation();
        KnownRepositories repositories = KnownRepositories.getInstance();
        if (repositories.isKnownRepository(location)) {
            try {
                // The repository is already known.
                // Ensure that the authentication information of this 
                // location matches that of the known location
                setAuthenticationInformation((CVSRepositoryLocation)repositories.getRepository(location));
            } catch (CVSException e) {
                // Log the exception and continue
                CVSProviderPlugin.log(e);
            }
        } else {
            // The repository is not known so record it so any authentication
            // information the user may provide is remembered
        	repositories.addRepository(this, true /* broadcast */);
        }
    }

	/*
	 * Set the authentication information of this instance such that it matches the
	 * provided instances.
     */
    private void setAuthenticationInformation(CVSRepositoryLocation other) {
        if (other != this) {
            // The instances differ so copy from the other location to this one
            if (other.getUserInfoCached()) {
                // The user info is cached for the other instance
                // so null all the values in this instance so the 
                // information is obtained from the cache
                this.allowCaching = true;
                if (!userFixed) this.user = null;
                if (!passwordFixed) this.password = null;
            } else {
                // The user info is not cached for the other instance so
                // copy the authentication information into this instance
                setAllowCaching(false); /* this will clear any cahced values */
                // Only copy the username and password if they are not fixed.
                // (If they are fixed, they would be included in the location
                // identifier and therefore must already match)
                if (!other.userFixed)
                    this.user = other.user;
                if (!other.passwordFixed)
                    this.password = other.password;
            }
        }
    }

    /*
     * The connection was sucessfully made. Update the cached
     * repository location if it is a differnet instance than
     * this location.
     */
    private void updateCachedLocation() {
        try {
            CVSRepositoryLocation known = (CVSRepositoryLocation)KnownRepositories.getInstance().getRepository(getLocation());
            known.setAuthenticationInformation(this);
        } catch (CVSException e) {
            // Log the exception and continue
            CVSProviderPlugin.log(e);
        }
    }
    
    /*
	 * Implementation of inherited toString()
	 */
	public String toString() {
		return getLocation(true);
	}
	
	public boolean equals(Object o) {
		if (this == o) return true;
		if (!(o instanceof CVSRepositoryLocation)) return false;
		return getLocation().equals(((CVSRepositoryLocation)o).getLocation());
	}
	public int hashCode() {
		return getLocation().hashCode();
	}
	
	/*
	 * Return the cached password from the keyring. 
	 * Also, set the username of the receiver if the username is mutable
	 */
	private String retrievePassword() {
		Map map = Platform.getAuthorizationInfo(FAKE_URL, getLocation(), AUTH_SCHEME);
		if (map != null) {
			String username = (String) map.get(INFO_USERNAME);
			if (username != null && isUsernameMutable())
				setUsername(username);
			String password = (String) map.get(INFO_PASSWORD);
			if (password != null) {
				return password;
			}
		}
		return null;
	}
	/*
	 * @see IUserInfo#setPassword(String)
	 */
	public void setPassword(String password) {
		if (passwordFixed)
			throw new UnsupportedOperationException();
		// We set the password here but it will be cleared 
		// if the user info is cached using updateCache()
		this.password = password;
	}
	
	/*
	 * @see IUserInfo#setUsername(String)
	 */
	public void setUsername(String user) {
		if (userFixed)
			throw new UnsupportedOperationException();
		this.user = user;
	}
	
	public void setUserMuteable(boolean muteable) {
		userFixed = !muteable;
	}
	
	public void setAllowCaching(boolean value) {
		allowCaching = value;
        if (allowCaching)
            updateCache();
        else
            flushCache();
	}
	
	public void updateCache() {
		// Nothing to cache if the password is fixed
		if (passwordFixed || ! allowCaching) return;
		// Nothing to cache if the password is null and the user is fixed
		if (password == null && userFixed) return;
		if (updateCache(user, password)) {
			// If the cache was updated, null the password field
			// so we will obtain the password from the cache when needed
			password = null;
		}
		ensurePreferencesStored();
	}

	/*
	 * Cache the user info in the keyring. Return true if the operation
	 * succeeded and false otherwise. If an error occurs, it will be logged.
	 */
	private boolean updateCache(String username, String password) {
		// put the password into the Platform map
		Map map = Platform.getAuthorizationInfo(FAKE_URL, getLocation(), AUTH_SCHEME);
		if (map == null) {
			map = new java.util.HashMap(10);
		}
		if (username != null)
			map.put(INFO_USERNAME, username);
		if (password != null)
			map.put(INFO_PASSWORD, password);
		try {
			Platform.addAuthorizationInfo(FAKE_URL, getLocation(), AUTH_SCHEME, map);
		} catch (CoreException e) {
			// We should probably wrap the CoreException here!
			CVSProviderPlugin.log(e);
			return false;
		}
		return true;
	}
	
	/*
	 * Validate that the receiver contains valid information for
	 * making a connection. If the receiver contains valid
	 * information, the method returns. Otherwise, an exception
	 * indicating the problem is throw.
	 */
	public void validateConnection(IProgressMonitor monitor) throws CVSException {
		try {
			monitor = Policy.monitorFor(monitor);
			monitor.beginTask(null, 100);
			ICVSFolder root = CVSWorkspaceRoot.getCVSFolderFor(ResourcesPlugin.getWorkspace().getRoot());
			Session session = new Session(this, root, false /* output to console */);
			session.open(Policy.subMonitorFor(monitor, 50), false /* read-only */);
			try {
				IStatus status = Command.VERSION.execute(session, this, Policy.subMonitorFor(monitor, 50));
				// Log any non-ok status
				if (! status.isOK()) {
					CVSProviderPlugin.log(status);
				}
			} finally {
				session.close();
				monitor.done();
			}
		} catch (CVSException e) {
			// If the validation failed, dispose of any cached info
			dispose();
			throw e;
		}
	}
	
	/**
	 * Return the server platform type. It will be one of the following:
	 *		UNDETERMINED_PLATFORM: The platform has not been determined
	 *		CVS_SERVER: The platform is regular CVS server
	 *		CVSNT_SERVER: The platform in CVSNT
	 * If UNDETERMINED_PLATFORM is returned, the platform can be determined
	 * using the Command.VERSION command.
	 */
	public int getServerPlatform() {
		return serverPlatform;
	}
	
	/**
	 * This method is called from Command.VERSION to set the platform type.
	 */
	public void setServerPlaform(int serverType) {
		// Second, check the code of the status itself to see if it is NT
		switch (serverType) {
			case CVS_SERVER:
			case CVSNT_SERVER:
			case UNKNOWN_SERVER:
			case UNSUPPORTED_SERVER:
				serverPlatform = serverType;
				break;
			default:
				// We had an error status with no info about the server.
				// Mark it as undetermined.
				serverPlatform = UNDETERMINED_PLATFORM;
		}
	}
	
	/**
	 * @see ICVSRepositoryLocation#flushUserInfo()
	 */
	public void flushUserInfo() {
		flushCache();
	}
	
	/*
	 * Return the command string that is to be used by the EXT connection method.
	 */
	String[] getExtCommand(String password) throws IOException {
		// Get the user specified connection parameters
		String CVS_RSH = CVSProviderPlugin.getPlugin().getCvsRshCommand();
		String CVS_RSH_PARAMETERS = CVSProviderPlugin.getPlugin().getCvsRshParameters();
		String CVS_SERVER = CVSProviderPlugin.getPlugin().getCvsServer();
		if(CVS_RSH == null || CVS_SERVER == null) {
			throw new IOException(CVSMessages.EXTServerConnection_varsNotSet); //$NON-NLS-1$
		}
		
		// If there is only one token, assume it is the command and use the default parameters and order
		if (CVS_RSH_PARAMETERS == null || CVS_RSH_PARAMETERS.length() == 0) {
			if (port != USE_DEFAULT_PORT)
				throw new IOException(CVSMessages.EXTServerConnection_invalidPort); //$NON-NLS-1$
			return new String[] {CVS_RSH, host, "-l", user, CVS_SERVER, INVOKE_SVR_CMD}; //$NON-NLS-1$
		}

		// Substitute any variables for their appropriate values
		CVS_RSH_PARAMETERS = stringReplace(CVS_RSH_PARAMETERS, USER_VARIABLE, user);
		CVS_RSH_PARAMETERS = stringReplace(CVS_RSH_PARAMETERS, PASSWORD_VARIABLE, password);
		CVS_RSH_PARAMETERS = stringReplace(CVS_RSH_PARAMETERS, HOST_VARIABLE, host);
		CVS_RSH_PARAMETERS = stringReplace(CVS_RSH_PARAMETERS, PORT_VARIABLE, new Integer(port).toString());

		// Build the command list to be sent to the OS.
		List commands = new ArrayList();
		commands.add(CVS_RSH);
		StringTokenizer tokenizer = new StringTokenizer(CVS_RSH_PARAMETERS);
		while (tokenizer.hasMoreTokens()) {
			String next = tokenizer.nextToken();
			commands.add(next);
		}
		commands.add(CVS_SERVER);
		commands.add(INVOKE_SVR_CMD);
		return (String[]) commands.toArray(new String[commands.size()]);
	}

	/*
	 * Replace all occurances of oldString with newString
	 */
	private String stringReplace(String string, String oldString, String newString) {
		int index = string.toLowerCase().indexOf(oldString);
		if (index == -1) return string;
		return stringReplace(
			string.substring(0, index) + newString + string.substring(index + oldString.length()),
			oldString, newString);
	}

	/**
	 * Return the server message with the prefix removed.
	 * Server aborted messages typically start with 
	 *    "cvs server: ..."
	 *    "cvs [server aborted]: ..."
	 *    "cvs rtag: ..."
	 */
	public String getServerMessageWithoutPrefix(String errorLine, String prefix) {
		String message = errorLine;
		int firstSpace = message.indexOf(' ');
		if(firstSpace != -1) {						
			// remove the program name and the space
			message = message.substring(firstSpace + 1);
			// Quick fix to handle changes in server message format (see Bug 45138)
			if (prefix.startsWith("[")) { //$NON-NLS-1$
				// This is the server aborted message
				// Remove the pattern "[command_name aborted]: "
				int closingBracket = message.indexOf("]: "); //$NON-NLS-1$
				if (closingBracket == -1) return null;
				// get what is inside the brackets
				String realPrefix = message.substring(1, closingBracket);
				// check that there is two words and the second word is "aborted"
				int space = realPrefix.indexOf(' ');
				if (space == -1) return null;
				if (realPrefix.indexOf(' ', space +1) != -1) return null;
				if (!realPrefix.substring(space +1).equals("aborted")) return null; //$NON-NLS-1$
				// It's a match, return the rest of the line
				message = message.substring(closingBracket + 2);
				if (message.charAt(0) == ' ') {
					message = message.substring(1);
				}
				return message;
			} else {
				// This is the server command message
				// Remove the pattern "command_name: "
				int colon = message.indexOf(": "); //$NON-NLS-1$
				if (colon == -1) return null;
				// get what is before the colon
				String realPrefix = message.substring(0, colon);
				// ensure that it is a single word
				if (realPrefix.indexOf(' ') != -1) return null;
				message = message.substring(colon + 1);
				if (message.charAt(0) == ' ') {
					message = message.substring(1);
				}
				return message;
			}
		}
		// This is not a server message with the desired prefix
		return null;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation#getUserAuthenticator()
	 */
	public IUserAuthenticator getUserAuthenticator() {
		return getAuthenticator();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation#setUserAuthenticator()
	 */
	public void setUserAuthenticator(IUserAuthenticator authenticator) {
		CVSRepositoryLocation.authenticator = authenticator;
	}
	
	/*
	 * Return the preferences node for this repository
	 */
	public Preferences getPreferences() {
		if (!hasPreferences()) {
			ensurePreferencesStored();
		}
		return internalGetPreferences();
	}
	
	private Preferences internalGetPreferences() {
		return getParentPreferences().node(getPreferenceName());
	}
	
	private boolean hasPreferences() {
		try {
			return getParentPreferences().nodeExists(getPreferenceName());
		} catch (BackingStoreException e) {
			CVSProviderPlugin.log(IStatus.ERROR, NLS.bind(CVSMessages.CVSRepositoryLocation_74, new String[] { getLocation(true) }), e); //$NON-NLS-1$
			return false;
		}
	}
	
	/**
	 * Return a unique name that identifies this location but
	 * does not contain any slashes (/). Also, do not use ':'.
	 * Although a valid path character, the initial core implementation
	 * didn't handle it well.
	 */
	private String getPreferenceName() {
		return getLocation().replace('/', '%').replace(':', '%');
	}

	public void storePreferences() {
		Preferences prefs = internalGetPreferences();
		// Must store at least one preference in the node
		prefs.put(PREF_LOCATION, getLocation());
		flushPreferences();
	}
	
	private void flushPreferences() {
		try {
			internalGetPreferences().flush();
		} catch (BackingStoreException e) {
			CVSProviderPlugin.log(IStatus.ERROR, NLS.bind(CVSMessages.CVSRepositoryLocation_75, new String[] { getLocation(true) }), e); //$NON-NLS-1$
		}
	}

	private void ensurePreferencesStored() {
		if (!hasPreferences()) {
			storePreferences();
		}
	}

	/* (non-Javadoc)
	 * @see org.eclipse.team.internal.ccvs.core.ICVSRepositoryLocation#getUserInfoCached()
	 */
	public boolean getUserInfoCached() {
		Map map = Platform.getAuthorizationInfo(FAKE_URL, getLocation(), AUTH_SCHEME);
		if (map != null) {
			String password = (String) map.get(INFO_PASSWORD);
			return (password != null);
		}
		return false;
	}
}
