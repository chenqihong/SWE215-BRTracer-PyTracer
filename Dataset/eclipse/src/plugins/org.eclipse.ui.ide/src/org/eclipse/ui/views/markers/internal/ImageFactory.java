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

package org.eclipse.ui.views.markers.internal;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.resource.ImageRegistry;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.internal.ide.IDEWorkbenchPlugin;

/**
 * Manages images and image descriptors.
 */
public class ImageFactory {

	private static ImageRegistry imageRegistry = new ImageRegistry();
	private static Map map = new HashMap();

	
	/**
	 * Returns an image for the given path in the ide plug-in
	 * or <code>null</code> if an image could not be created. 
	 * 
	 * @param path the path of the image relative to "org.eclipse.ui/icons/full"
	 * @return the image located at the specified path or <code>null</code> if 
	 * no image could be created.
	 */
	public static Image getImage(String path) {
		Image image = imageRegistry.get(path);

		if (image == null) {
			ImageDescriptor imageDescriptor = getImageDescriptor(path);

			if (imageDescriptor != null) {
				image = imageDescriptor.createImage(false);

				if (image == null)
					System.err.println(ImageFactory.class + ": error creating image for " + path); //$NON-NLS-1$

				imageRegistry.put(path, image);
			}
		}

		return image;
	}
	
	/**
	 * Returns an image descriptor for the given path in the ide
	 * plug-in or <code>null</code> if no
	 * image could be found.
	 * 
	 * @param path the path of the image relative to "org.eclipse.ui/icons/full"
	 * @return an image descriptor or <code>null</code> if no image was found at the
	 * specified path.
	 */
	public static ImageDescriptor getImageDescriptor(String path) {
		ImageDescriptor imageDescriptor = (ImageDescriptor) map.get(path);

		if (imageDescriptor == null) {
			imageDescriptor =IDEWorkbenchPlugin.getIDEImageDescriptor(path);
			map.put(path, imageDescriptor);
		}

		return imageDescriptor;
	}
	
}
