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
package org.eclipse.swt.graphics;

import org.eclipse.swt.*;
import org.eclipse.swt.internal.cairo.*;

/**
 * Instances of this class represent patterns to use while drawing. Patterns
 * can be specified either as bitmaps or gradients.
 * <p>
 * Application code must explicitly invoke the <code>Pattern.dispose()</code> 
 * method to release the operating system resources managed by each instance
 * when those instances are no longer required.
 * </p>
 * 
 * @since 3.1
 */
public class Pattern extends Resource {

	/**
	 * the OS resource for the Pattern
	 * (Warning: This field is platform dependent)
	 * <p>
	 * <b>IMPORTANT:</b> This field is <em>not</em> part of the SWT
	 * public API. It is marked public only so that it can be shared
	 * within the packages provided by SWT. It is not available on all
	 * platforms and should never be accessed from application code.
	 * </p>
	 */
	public int /*long*/ handle;

/**
 * Constructs a new Pattern given an image. Drawing with the resulting
 * pattern will cause the image to be tiled over the resulting area.
 * 
 * @param device the device on which to allocate the pattern
 * @param image the image that the pattern will draw
 * 
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the device is null and there is no current device, or the image is null</li>
 *    <li>ERROR_INVALID_ARGUMENT - if the image has been disposed</li>
 * </ul>
 * @exception SWTError <ul>
 *    <li>ERROR_NO_HANDLES if a handle for the pattern could not be obtained/li>
 * </ul>
 * 
 * @see #dispose()
 */
public Pattern(Device device, Image image) {
	if (device == null) device = Device.getDevice();
	if (device == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	if (image == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	if (image.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	this.device = device;
	device.checkCairo();
	image.createSurface();
	handle = Cairo.cairo_pattern_create_for_surface(image.surface);
	if (handle == 0) SWT.error(SWT.ERROR_NO_HANDLES);
	Cairo.cairo_pattern_set_extend(handle, Cairo.CAIRO_EXTEND_REPEAT);
	if (device.tracking) device.new_Object(this);
}

/**
 * Constructs a new Pattern that represents a linear, two color
 * gradient. Drawing with the pattern will cause the resulting area to be
 * tiled with the gradient specified by the arguments.
 * 
 * @param device the device on which to allocate the pattern
 * @param x1 the x coordinate of the starting corner of the gradient
 * @param y1 the y coordinate of the starting corner of the gradient
 * @param x2 the x coordinate of the ending corner of the gradient
 * @param y2 the y coordinate of the ending corner of the gradient
 * @param color1 the starting color of the gradient
 * @param color2 the ending color of the gradient
 * 
 * @exception IllegalArgumentException <ul>
 *    <li>ERROR_NULL_ARGUMENT - if the device is null and there is no current device, 
 *                              or if either color1 or color2 is null</li>
 *    <li>ERROR_INVALID_ARGUMENT - if either color1 or color2 has been disposed</li>
 * </ul>
 * @exception SWTError <ul>
 *    <li>ERROR_NO_HANDLES if a handle for the pattern could not be obtained/li>
 * </ul>
 * 
 * @see #dispose()
 */
public Pattern(Device device, float x1, float y1, float x2, float y2, Color color1, Color color2) {
	if (device == null) device = Device.getDevice();
	if (device == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	if (color1 == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	if (color1.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	if (color2 == null) SWT.error(SWT.ERROR_NULL_ARGUMENT);
	if (color2.isDisposed()) SWT.error(SWT.ERROR_INVALID_ARGUMENT);
	this.device = device;
	device.checkCairo();
	handle = Cairo.cairo_pattern_create_linear(x1, y1, x2, y2);
	if (handle == 0) SWT.error(SWT.ERROR_NO_HANDLES);
	//TODO - how about alpha?
	GC.setCairoPatternColor(handle, 0, color1);
	GC.setCairoPatternColor(handle, 1, color2);
	Cairo.cairo_pattern_set_extend(handle, Cairo.CAIRO_EXTEND_REPEAT);
	if (device.tracking) device.new_Object(this);
}
	
/**
 * Disposes of the operating system resources associated with
 * the Pattern. Applications must dispose of all Patterns that
 * they allocate.
 */
public void dispose() {
	if (handle == 0) return;
	if (device.isDisposed()) return;
	Cairo.cairo_pattern_destroy(handle);
	handle = 0;
	if (device.tracking) device.dispose_Object(this);
	device = null;
}

/**
 * Returns <code>true</code> if the Pattern has been disposed,
 * and <code>false</code> otherwise.
 * <p>
 * This method gets the dispose state for the Pattern.
 * When a Pattern has been disposed, it is an error to
 * invoke any other method using the Pattern.
 *
 * @return <code>true</code> when the Pattern is disposed, and <code>false</code> otherwise
 */
public boolean isDisposed() {
	return handle == 0;
}

/**
 * Returns a string containing a concise, human-readable
 * description of the receiver.
 *
 * @return a string representation of the receiver
 */
public String toString() {
	if (isDisposed()) return "Pattern {*DISPOSED*}";
	return "Pattern {" + handle + "}";
}
	
}
