/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.debug.internal.ui.views.memory.renderings;

import org.eclipse.debug.core.model.MemoryByte;
import org.eclipse.debug.internal.ui.DebugUIMessages;
import org.eclipse.debug.ui.memory.AbstractTableRendering;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.IWorkbenchPropertyPage;
import org.eclipse.ui.dialogs.PropertyPage;

public class TableRenderingPropertiesPage extends PropertyPage implements
		IWorkbenchPropertyPage {

	public TableRenderingPropertiesPage() {
		super();
	}

	protected Control createContents(Composite parent) {
		noDefaultAndApplyButton();
		Composite composite = new Composite(parent, SWT.NONE);
		if (getElement() instanceof AbstractTableRendering)
		{
			AbstractTableRendering rendering = (AbstractTableRendering)getElement();
			GridLayout compositeLayout = new GridLayout();
			compositeLayout.numColumns = 2;
			compositeLayout.makeColumnsEqualWidth = false;
			composite.setLayout(compositeLayout);
			
			GridData comositeSpec= new GridData();
			comositeSpec.grabExcessVerticalSpace= true;
			comositeSpec.grabExcessHorizontalSpace= true;
			comositeSpec.horizontalAlignment= GridData.FILL;
			comositeSpec.verticalAlignment= GridData.CENTER;
			composite.setLayoutData(comositeSpec);
			
			String label = rendering.getLabel();
			if (label.startsWith("&&")) //$NON-NLS-1$
				label = label.replaceFirst("&&", "&");  //$NON-NLS-1$//$NON-NLS-2$
			addProperty(composite, DebugUIMessages.TableRenderingPropertiesPage_1, label);
						
			MemoryByte[] bytes = rendering.getSelectedAsBytes();
			if (bytes.length > 0) {
				
				String selectedAddress = "0x" + rendering.getSelectedAddress().toString(16).toUpperCase(); //$NON-NLS-1$
				StringBuffer content = new StringBuffer(selectedAddress);
				addProperty(composite, DebugUIMessages.TableRenderingPropertiesPage_2, content.toString());
				
				String length = String.valueOf(rendering.getAddressableUnitPerColumn()) + " " + DebugUIMessages.TableRenderingPropertiesPage_3; //$NON-NLS-1$
				addProperty(composite, DebugUIMessages.TableRenderingPropertiesPage_4, length);
				
				String selectedContent = rendering.getSelectedAsString();
				addProperty(composite, DebugUIMessages.TableRenderingPropertiesPage_5, selectedContent);
				
				int addressableSize = rendering.getAddressableSize() * 8;
				addProperty(composite, DebugUIMessages.TableRenderingPropertiesPage_6, String.valueOf(addressableSize) + " " + DebugUIMessages.TableRenderingPropertiesPage_8); //$NON-NLS-1$
				
				boolean endianessKnown = bytes[0].isEndianessKnown();
				int endianess = RenderingsUtil.ENDIANESS_UNKNOWN;
				if (endianessKnown)
					endianess = bytes[0].isBigEndian()?RenderingsUtil.BIG_ENDIAN:RenderingsUtil.LITTLE_ENDIAN;		
				
				boolean allBytesKnown = bytes[0].isHistoryKnown();
				boolean allBytesUnchanged = bytes[0].isChanged()?false:true;
				
				boolean allBytesReadable = bytes[0].isReadable();
				boolean allBytesWritable = bytes[0].isWritable();
				
				if (bytes.length > 1)
				{
					for (int i=1; i<bytes.length; i++)
					{
						if (endianessKnown)
						{
							int byteEndianess = bytes[i].isBigEndian()?RenderingsUtil.BIG_ENDIAN:RenderingsUtil.LITTLE_ENDIAN;
							if (endianess != RenderingsUtil.ENDIANESS_UNKNOWN && endianess != byteEndianess)
								endianess = RenderingsUtil.ENDIANESS_UNKNOWN;
						}
						
						if (!bytes[i].isHistoryKnown())
							allBytesKnown = false;
						if (bytes[i].isChanged())
							allBytesUnchanged = false;
						
						if (!bytes[i].isReadable())
							allBytesReadable = false;
						
						if (!bytes[i].isWritable())
							allBytesWritable = false;
					}
				}
				
				boolean isChanged = allBytesKnown && !allBytesUnchanged;
				if (allBytesKnown)
					addProperty(composite, DebugUIMessages.TableRenderingPropertiesPage_9, String.valueOf(isChanged));
				else
					addProperty(composite, DebugUIMessages.TableRenderingPropertiesPage_10, DebugUIMessages.TableRenderingPropertiesPage_11);
				
				String dataEndian = DebugUIMessages.TableRenderingPropertiesPage_12;
				if (endianessKnown)
				{	
					if (endianess == RenderingsUtil.BIG_ENDIAN)
						dataEndian = DebugUIMessages.TableRenderingPropertiesPage_13;
					else if (endianess == RenderingsUtil.LITTLE_ENDIAN)
						dataEndian = DebugUIMessages.TableRenderingPropertiesPage_14;
					else
						dataEndian = DebugUIMessages.TableRenderingPropertiesPage_15;
				}
				addProperty(composite, DebugUIMessages.TableRenderingPropertiesPage_16, dataEndian);
				
				
				if (rendering instanceof AbstractIntegerRendering)
				{
					
					AbstractIntegerRendering intRendering = (AbstractIntegerRendering)rendering;
					String displayEndian = DebugUIMessages.TableRenderingPropertiesPage_17;
					endianess = intRendering.getDisplayEndianess();
					
					if (endianess == RenderingsUtil.BIG_ENDIAN)
						displayEndian = DebugUIMessages.TableRenderingPropertiesPage_18;
					else if (endianess == RenderingsUtil.LITTLE_ENDIAN)
						displayEndian = DebugUIMessages.TableRenderingPropertiesPage_19;
					else
					{
						if (endianessKnown)
							displayEndian = dataEndian;
						else
							displayEndian = DebugUIMessages.TableRenderingPropertiesPage_20;
					}
					addProperty(composite, DebugUIMessages.TableRenderingPropertiesPage_21, displayEndian);
				}
				addProperty(composite, DebugUIMessages.TableRenderingPropertiesPage_22, String.valueOf(allBytesReadable));
				addProperty(composite, DebugUIMessages.TableRenderingPropertiesPage_23, String.valueOf(allBytesWritable));
			}
			else
			{
				String selectedAddress = "0x" + rendering.getSelectedAddress().toString(16).toUpperCase(); //$NON-NLS-1$
				addProperty(composite, DebugUIMessages.TableRenderingPropertiesPage_25, selectedAddress);
				
				int unitsPerLine = rendering.getAddressableUnitPerLine();
				addProperty(composite, DebugUIMessages.TableRenderingPropertiesPage_26, String.valueOf(unitsPerLine));
			}
		}
		
		return composite;
	}
	
	private void addProperty(Composite composite, String labelStr, String contentStr)
	{
		Label label = new Label(composite, SWT.NONE);
		label.setText(labelStr);
		Label text = new Label(composite, SWT.WRAP );
		text.setText(contentStr);
	}

}
