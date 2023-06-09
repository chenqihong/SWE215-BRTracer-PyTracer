/*******************************************************************************
 * Copyright (c)  2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.build;

import java.io.*;
import org.eclipse.swt.tools.internal.IconExe;

/**
 *
 */
public class BrandingIron implements IXMLConstants {

	private String[] icons = null;
	private String root;
	private String name;
	private String os = "win32"; //$NON-NLS-1$
	private boolean brandIcons = true;
	
	public void setName(String value) {
		name = value;
	}

	public void setIcons(String value) {
		icons = value.split(", "); //$NON-NLS-1$
	}

	public void setRoot(String value) {
		root = value;
	}

	public void brand() throws Exception {
		// if the name property is not set it will be ${launcher.name} so just bail.
		if (name.startsWith("${")) //$NON-NLS-1$
			return;

		// if the root does not exists (happens in some packaging cases) or 
		// there is already a file with target name, don't do anything
		String testName = os.equals("win32") ? name + ".exe" : name;
		if (!new File(root).exists() || new File(root, testName).exists())
			return;
		
		if (icons == null || icons[0].startsWith("${"))
			brandIcons = false;
		
		if ("win32".equals(os)) //$NON-NLS-1$
			brandWindows();
		if ("linux".equals(os)) //$NON-NLS-1$
			brandLinux();
		if ("solaris".equals(os)) //$NON-NLS-1$
			brandSolaris();
		if ("macosx".equals(os)) //$NON-NLS-1$
			brandMac();
		if ("aix".equals(os)) //$NON-NLS-1$
			brandAIX();
		if ("hpux".equals(os)) //$NON-NLS-1$
			brandHPUX();
	}

	private void brandAIX() {
		renameLauncher();
	}

	private void brandHPUX() {
		renameLauncher();
	}

	private void brandLinux() throws Exception {
		renameLauncher();
		if (brandIcons)
			copy(new File(icons[0]), new File(root, "icon.xpm"));
	}

	private void brandSolaris() throws Exception {
		renameLauncher();
		if (brandIcons == false)
			return;
		
		for (int i = 0; i < icons.length; i++) {
			String icon = icons[i];
			if (icon.endsWith(".l.pm")) //$NON-NLS-1$
				copy(new File(icon), new File(root, name + ".l.pm")); //$NON-NLS-1$
			if (icon.endsWith(".m.pm")) //$NON-NLS-1$
				copy(new File(icon), new File(root, name + ".m.pm"));
			if (icon.endsWith(".s.pm"))
				copy(new File(icon), new File(root, name + ".s.pm"));
			if (icon.endsWith(".t.pm"))
				copy(new File(icon), new File(root, name + ".t.pm"));
		}
	}

	private void brandMac() throws Exception {
		//Initially the files are in: <root>/Eclipse.app/ 
		//and they must appear in <root>/MyAppName.app/
		//Because java does not support the rename of a folder, files are copied.

		//Initialize the target folders
		String target = root + '/' + name + ".app/Contents"; //$NON-NLS-1$
		new File(target).mkdirs();
		new File(target + "/MacOS").mkdirs();
		new File(target + "/Resources").mkdirs();

		String initialRoot = root + "/Launcher.app/Contents"; //$NON-NLS-1$
		if (!new File(initialRoot).exists())
			initialRoot = root + "/Eclipse.app/Contents";  //$NON-NLS-1$
		copyMacLauncher(initialRoot, target);
		String iconName = "";
		if (brandIcons) {
			File icon = new File(icons[0]);
			iconName = icon.getName();
			copy(icon, new File(target + "/Resources/" + icon.getName())); //$NON-NLS-1$
			new File(initialRoot + "/Resources/Eclipse.icns").delete();
			new File(initialRoot + "/Resources/").delete();
		}
		modifyInfoPListFile(initialRoot, target, iconName);
		File rootFolder = new File(initialRoot);
		rootFolder.delete();
		rootFolder.getParentFile().delete();
	}

	private void brandWindows() throws Exception {
		File templateLauncher = new File(root, "launcher.exe");
		if (!templateLauncher.exists())
			templateLauncher = new File(root, "eclipse.exe");
		if (brandIcons) {
			String[] args = new String[icons.length + 1];
			args[0] = templateLauncher.getAbsolutePath();
			System.arraycopy(icons, 0, args, 1, icons.length);
			IconExe.main(args);
		}
		templateLauncher.renameTo(new File(root, name + ".exe"));
	}

	private void renameLauncher() {
		if (!new File(root, "launcher").renameTo(new File(root, name)))
			new File(root, "eclipse").renameTo(new File(root, name));
	}

	private void copyMacLauncher(String initialRoot, String target) {
		String targetLauncher = target + "/MacOS/";
		File launcher = new File(initialRoot + "/MacOS/launcher");
		if (! launcher.exists())
			launcher = new File(initialRoot +  "/MacOS/eclipse");
		File targetFile = new File(targetLauncher, name);
		try {
			if (targetFile.getCanonicalFile().equals(launcher.getCanonicalFile())) {
				try {
					//Force the executable bit on the exe because it has been lost when copying the file
					Runtime.getRuntime().exec("chmod 755 " + targetFile.getAbsolutePath());
				} catch (IOException e) {
					//ignore
				}
				return;
			}
			copy(launcher, targetFile);
		} catch (IOException e) {
			System.out.println("Could not copy macosx launcher");
			return;
		}
		try {
			//Force the executable bit on the exe because it has been lost when copying the file
			Runtime.getRuntime().exec("chmod 755 " + targetFile.getAbsolutePath());
		} catch (IOException e) {
			//ignore
		}
		launcher.delete();
		launcher.getParentFile().delete();
	}

	private void modifyInfoPListFile(String initialRoot, String targetRoot, String iconName) {
		final String MARKER_NAME = "%EXECUTABLE_NAME%"; //$NON-NLS-1$
		final String BUNDLE_NAME = "%BUNDLE_NAME%"; //$NON-NLS-1$
		final String ICON_NAME = "%ICON_NAME%"; //$NON-NLS-1$

		File infoPList = new File(initialRoot, "Info.plist");
		StringBuffer buffer;
		try {
			buffer = readFile(infoPList);
		} catch (IOException e) {
			System.out.println("Impossible to brand info.plist file"); //$NON-NLS-1$
			return;
		}
		int exePos = scan(buffer, 0, MARKER_NAME);
		if (exePos != -1)
			buffer.replace(exePos, exePos + MARKER_NAME.length(), name);

		int bundlePos = scan(buffer, 0, BUNDLE_NAME);
		if (bundlePos != -1)
			buffer.replace(bundlePos, bundlePos + BUNDLE_NAME.length(), name);

		int iconPos = scan(buffer, 0, ICON_NAME);
		if (iconPos != -1)
			buffer.replace(iconPos, iconPos + ICON_NAME.length(), iconName);
		File target = null;
		try {
			target = new File(targetRoot, "Info.plist");
			transferStreams(new ByteArrayInputStream(buffer.toString().getBytes()), new FileOutputStream(target));
		} catch (FileNotFoundException e) {
			System.out.println("Impossible to brand info.plist file"); //$NON-NLS-1$
			return;
		} catch (IOException e) {
			System.out.println("Impossible to brand info.plist file"); //$NON-NLS-1$
			return;
		}
		try {
			if (!infoPList.getCanonicalFile().equals(target.getCanonicalFile()))
				infoPList.delete();
		} catch (IOException e) {
			//ignore
		}
	}

	/**
	 * Transfers all available bytes from the given input stream to the given output stream. 
	 * Regardless of failure, this method closes both streams.
	 * @throws IOException 
	 */
	public void copy(File source, File destination) throws IOException {
		InputStream in = null;
		OutputStream out = null;
		try {
			in = new FileInputStream(source);
			out = new FileOutputStream(destination);
			final byte[] buffer = new byte[8192];
			while (true) {
				int bytesRead = -1;
				bytesRead = in.read(buffer);
				if (bytesRead == -1)
					break;
				out.write(buffer, 0, bytesRead);
			}
		} finally {
			try {
				if (in != null)
					in.close();
			} finally {
				if (out != null)
					out.close();
			}
		}
	}

	private int scan(StringBuffer buf, int start, String targetName) {
		return scan(buf, start, new String[] {targetName});
	}

	private int scan(StringBuffer buf, int start, String[] targets) {
		for (int i = start; i < buf.length(); i++) {
			for (int j = 0; j < targets.length; j++) {
				if (i < buf.length() - targets[j].length()) {
					String match = buf.substring(i, i + targets[j].length());
					if (targets[j].equalsIgnoreCase(match))
						return i;
				}
			}
		}
		return -1;
	}

	private StringBuffer readFile(File targetName) throws IOException {
		InputStreamReader reader = new InputStreamReader(new BufferedInputStream(new FileInputStream(targetName)));
		StringBuffer result = new StringBuffer();
		char[] buf = new char[4096];
		int count;
		try {
			count = reader.read(buf, 0, buf.length);
			while (count != -1) {
				result.append(buf, 0, count);
				count = reader.read(buf, 0, buf.length);
			}
		} finally {
			try {
				reader.close();
			} catch (IOException e) {
				// ignore exceptions here
			}
		}
		return result;
	}

	private void transferStreams(InputStream source, OutputStream destination) throws IOException {
		source = new BufferedInputStream(source);
		destination = new BufferedOutputStream(destination);
		try {
			byte[] buffer = new byte[8192];
			while (true) {
				int bytesRead = -1;
				if ((bytesRead = source.read(buffer)) == -1)
					break;
				destination.write(buffer, 0, bytesRead);
			}
		} finally {
			try {
				source.close();
			} catch (IOException e) {
				// ignore
			}
			try {
				destination.close();
			} catch (IOException e) {
				// ignore
			}
		}
	}

	public void setOS(String value) {
		os = value;
	}
}
