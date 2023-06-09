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
package org.eclipse.ui.internal.console;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.Assert;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentAdapter;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.swt.custom.TextChangeListener;
import org.eclipse.swt.custom.TextChangedEvent;
import org.eclipse.swt.custom.TextChangingEvent;

/**
 * Adapts a Console's document to the viewer StyledText widget. Allows proper line
 * wrapping of fixed width consoles without having to add line delimeters to the StyledText.
 * 
 * By using this adapter, the offset of any character is the same in both the widget and the
 * document.
 * 
 * @since 3.1
 */
public class ConsoleDocumentAdapter implements IDocumentAdapter, IDocumentListener {
    
    private int consoleWidth = -1;
    private List textChangeListeners;
    private IDocument document;
    
    int[] offsets = new int[5000];
    int[] lengths = new int[5000];
    private int regionCount = 0;
    
    private Pattern pattern = Pattern.compile("^.*$", Pattern.MULTILINE); //$NON-NLS-1$
    
    
    public ConsoleDocumentAdapter(int width) {
        textChangeListeners = new ArrayList();
        consoleWidth = width;
    }
    
    /*
     * repairs lines list from the beginning of the line containing the offset of any 
     * DocumentEvent, to the end of the Document.
     */
    private void repairLines(int eventOffset) {
        if (document == null) {
            return;
        }
        try {
            int docLine = document.getLineOfOffset(eventOffset);
            int docLineStart = document.getLineOffset(docLine);
            int textLine = getLineAtOffset(docLineStart);
            
            for (int i=regionCount-1; i>=textLine; i--) {
                regionCount--;
            }
            
            int numLinesInDoc = document.getNumberOfLines();
            String line = null;
            int nextOffset =  document.getLineOffset(docLine);
            for (int i = docLine; i<numLinesInDoc; i++) {
                int offset = nextOffset;
                int length = document.getLineLength(i);
                nextOffset += length;
                
                if (length == 0) {
                    addRegion(offset, 0);
                } else {
                    while (length > 0) {
                        int trimmedLength = length;
                        String lineDelimiter = document.getLineDelimiter(i);
                        int lineDelimiterLength = 0;
                        if (lineDelimiter != null) {
                            lineDelimiterLength = lineDelimiter.length(); 
                            trimmedLength -= lineDelimiterLength;
                        }

                        if (consoleWidth > 0 && consoleWidth < trimmedLength) {
                            addRegion(offset, consoleWidth);
                            offset += consoleWidth;
                            length -= consoleWidth;
                        } else {
                            addRegion(offset, length);
                            offset += length;
                            length -= length;
                        }
                    }
                }
            }
            if (line != null && lineEndsWithDelimeter(line)) {
                addRegion(nextOffset, 0);
            }
            
        } catch (BadLocationException e) {
        }
        
        if (regionCount == 0) {
            addRegion(0, document.getLength());
        }
    }
    
    private void addRegion(int offset, int length) {
        if (regionCount == 0) {
            offsets[0] = offset;
            lengths[0] = length;
        } else {
            if (regionCount == offsets.length) {
                growRegionArray(regionCount * 2);
            }
            offsets[regionCount] = offset;
            lengths[regionCount] = length;
        }
        regionCount++;
    }
    
    /**
     * Returns <code>true</code> if the line ends with a legal line delimiter
     * 
     * @return <code>true</code> if the line ends with a legal line delimiter,
     *         <code>false</code> otherwise
     */
    private boolean lineEndsWithDelimeter(String line) {
        String[] lld = document.getLegalLineDelimiters();
        for (int i = 0; i < lld.length; i++) {
            if (line.endsWith(lld[i])) {
                return true;
            }
        }
        return false;
    }

    /* (non-Javadoc)
     * @see org.eclipse.jface.text.IDocumentAdapter#setDocument(org.eclipse.jface.text.IDocument)
     */
    public void setDocument(IDocument doc) {
        if (document != null) {
            document.removeDocumentListener(this);
        }
        
        document = doc;
        
        if (document != null) {
            document.addDocumentListener(this);
            repairLines(0);
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.swt.custom.StyledTextContent#addTextChangeListener(org.eclipse.swt.custom.TextChangeListener)
     */
    public synchronized void addTextChangeListener(TextChangeListener listener) {
		Assert.isNotNull(listener);
		if (!textChangeListeners.contains(listener)) {
			textChangeListeners.add(listener);
		}
    }
    
    /* (non-Javadoc)
     * @see org.eclipse.swt.custom.StyledTextContent#removeTextChangeListener(org.eclipse.swt.custom.TextChangeListener)
     */
    public synchronized void removeTextChangeListener(TextChangeListener listener) {
        if(textChangeListeners != null) {
            Assert.isNotNull(listener);
            textChangeListeners.remove(listener);
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.swt.custom.StyledTextContent#getCharCount()
     */
    public int getCharCount() {
        return document.getLength();
    }

    /* (non-Javadoc)
     * @see org.eclipse.swt.custom.StyledTextContent#getLine(int)
     */
    public String getLine(int lineIndex) {
        try {
            StringBuffer line = new StringBuffer(document.get(offsets[lineIndex], lengths[lineIndex]));
            int index = line.length() - 1;
            while(index > -1 && (line.charAt(index)=='\n' || line.charAt(index)=='\r')) {
                index--;
            }
            return new String(line.substring(0, index+1));
        } catch (BadLocationException e) {
        }
        return ""; //$NON-NLS-1$    
    }

    /* (non-Javadoc)
     * @see org.eclipse.swt.custom.StyledTextContent#getLineAtOffset(int)
     */
    public int getLineAtOffset(int offset) {
        if (offset == 0 || regionCount <= 1) {
            return 0;
        }
        
        if (offset == document.getLength()) {
            return regionCount-1;
        }
        
		int left= 0;
		int right= regionCount-1;
		int midIndex = 0;
		
		while (left <= right) {
		    midIndex= (left + right) / 2;
		    
		    if (offset < offsets[midIndex]) {
		        right = midIndex;
		    } else if (offset >= offsets[midIndex] + lengths[midIndex]) {
		        left = midIndex + 1;
		    } else {
		        return midIndex;
		    }
		}
		
		return midIndex;
    }

    /* (non-Javadoc)
     * @see org.eclipse.swt.custom.StyledTextContent#getLineCount()
     */
    public int getLineCount() {
        return regionCount;
    }

    /* (non-Javadoc)
     * @see org.eclipse.swt.custom.StyledTextContent#getLineDelimiter()
     */
    public String getLineDelimiter() {
        return System.getProperty("line.separator"); //$NON-NLS-1$
    }

    /* (non-Javadoc)
     * @see org.eclipse.swt.custom.StyledTextContent#getOffsetAtLine(int)
     */
    public int getOffsetAtLine(int lineIndex) {
        return offsets[lineIndex];
    }

    /* (non-Javadoc)
     * @see org.eclipse.swt.custom.StyledTextContent#getTextRange(int, int)
     */
    public String getTextRange(int start, int length) {
        try {
            return document.get(start, length);
        } catch (BadLocationException e) {
        }
        return null;
    }

    /* (non-Javadoc)
     * @see org.eclipse.swt.custom.StyledTextContent#replaceTextRange(int, int, java.lang.String)
     */
    public void replaceTextRange(int start, int replaceLength, String text) {
        try {
            document.replace(start, replaceLength, text);
        } catch (BadLocationException e) {
        }
    }

    /* (non-Javadoc)
     * @see org.eclipse.swt.custom.StyledTextContent#setText(java.lang.String)
     */
    public synchronized void setText(String text) {
        TextChangedEvent changeEvent = new TextChangedEvent(this);
        for (Iterator iter = textChangeListeners.iterator(); iter.hasNext();) {
            TextChangeListener element = (TextChangeListener) iter.next();
            element.textSet(changeEvent);
        }    
    }

    /* (non-Javadoc)
     * @see org.eclipse.jface.text.IDocumentListener#documentAboutToBeChanged(org.eclipse.jface.text.DocumentEvent)
     */
    public synchronized void documentAboutToBeChanged(DocumentEvent event) {
        if (document == null) {
            return;
        }
        
        TextChangingEvent changeEvent = new TextChangingEvent(this);
        changeEvent.start = event.fOffset;
        changeEvent.newText = (event.fText == null ? "" : event.fText); //$NON-NLS-1$
        changeEvent.replaceCharCount = event.fLength;
        changeEvent.newCharCount = (event.fText == null ? 0 : event.fText.length());
        
        int first = getLineAtOffset(event.fOffset);
        int last = getLineAtOffset(event.fOffset + event.fLength);
        changeEvent.replaceLineCount= last - first;
        
        changeEvent.newLineCount = countLines(event.fText);
        
        if (changeEvent.newLineCount > offsets.length-regionCount) {
            growRegionArray(changeEvent.newLineCount);
        }
        
        for (Iterator iter = textChangeListeners.iterator(); iter.hasNext();) {
            TextChangeListener element = (TextChangeListener) iter.next();
            element.textChanging(changeEvent);
        }
    }

    private void growRegionArray(int minSize) {
        int size = Math.max(offsets.length*2, minSize*2);
        int[] newOffsets = new int[size];
        System.arraycopy(offsets, 0, newOffsets, 0, regionCount);
        offsets = newOffsets;
        int[] newLengths = new int[size];
        System.arraycopy(lengths, 0, newLengths, 0, regionCount);
        lengths = newLengths;
    }

    /**
     * Counts the number of lines the viewer's text widget will need to use to 
     * display the String
     * @return The number of lines necessary to display the string in the viewer.
     */
    private int countLines(String string) {
        int count = 0;
        if (lineEndsWithDelimeter(string)) {
            count++;
        }
        
//        work around to http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4994840
//        see bug 84641
        if (string.endsWith("\r")) { //$NON-NLS-1$
            int len = string.length();
            int index = len >= 2 ? len-2 : 0;
            string = string.substring(0, index);
        }
        
        Matcher matcher = pattern.matcher(string);
        while (matcher.find()) {
            count++;
            if (consoleWidth > 0) {
                String line = matcher.group();
                count += (line.length() / consoleWidth);
            } 
        }
        

        
        return count;
    }


    /* (non-Javadoc)
     * @see org.eclipse.jface.text.IDocumentListener#documentChanged(org.eclipse.jface.text.DocumentEvent)
     */
    public synchronized void documentChanged(DocumentEvent event) {
        if (document == null) {
            return;
        }
        
        repairLines(event.fOffset);
        
        TextChangedEvent changeEvent = new TextChangedEvent(this);

        for (Iterator iter = textChangeListeners.iterator(); iter.hasNext();) {
            TextChangeListener element = (TextChangeListener) iter.next();
            element.textChanged(changeEvent);
        }
    }

    /**
     * sets consoleWidth, repairs line information, then fires event to the viewer text widget.
     * @param width The console's width
     */
    public void setWidth(int width) {
        if (width != consoleWidth) {
            consoleWidth = width;
            repairLines(0);
            TextChangedEvent changeEvent = new TextChangedEvent(this);
            for (Iterator iter = textChangeListeners.iterator(); iter.hasNext();) {
                TextChangeListener element = (TextChangeListener) iter.next();
                element.textSet(changeEvent);
            }
        }
    }
}
