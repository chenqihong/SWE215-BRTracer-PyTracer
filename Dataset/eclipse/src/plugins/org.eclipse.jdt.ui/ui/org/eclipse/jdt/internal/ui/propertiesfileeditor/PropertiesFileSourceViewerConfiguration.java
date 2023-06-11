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
package org.eclipse.jdt.internal.ui.propertiesfileeditor;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Shell;

import org.eclipse.core.runtime.NullProgressMonitor;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.util.PropertyChangeEvent;

import org.eclipse.jface.text.DefaultInformationControl;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IInformationControl;
import org.eclipse.jface.text.IInformationControlCreator;
import org.eclipse.jface.text.ITextDoubleClickStrategy;
import org.eclipse.jface.text.ITextHover;
import org.eclipse.jface.text.ITextViewerExtension2;
import org.eclipse.jface.text.hyperlink.IHyperlinkDetector;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.reconciler.IReconciler;
import org.eclipse.jface.text.reconciler.IReconcilingStrategy;
import org.eclipse.jface.text.reconciler.MonoReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.rules.RuleBasedScanner;
import org.eclipse.jface.text.source.IAnnotationHover;
import org.eclipse.jface.text.source.ISourceViewer;

import org.eclipse.ui.editors.text.TextSourceViewerConfiguration;

import org.eclipse.ui.texteditor.AbstractDecoratedTextEditorPreferenceConstants;
import org.eclipse.ui.texteditor.ITextEditor;

import org.eclipse.jdt.ui.PreferenceConstants;
import org.eclipse.jdt.ui.text.IColorManager;

import org.eclipse.jdt.internal.ui.text.AbstractJavaScanner;
import org.eclipse.jdt.internal.ui.text.HTMLTextPresenter;
import org.eclipse.jdt.internal.ui.text.JavaAnnotationHover;
import org.eclipse.jdt.internal.ui.text.JavaPresentationReconciler;
import org.eclipse.jdt.internal.ui.text.SingleTokenJavaScanner;
import org.eclipse.jdt.internal.ui.text.java.JavaStringDoubleClickSelector;
import org.eclipse.jdt.internal.ui.text.java.hover.AnnotationHover;
import org.eclipse.jdt.internal.ui.text.spelling.PropertiesSpellingReconcileStrategy;

/**
 * Configuration for a source viewer which shows a properties file.
 * <p>
 * This class may be instantiated; it is not intended to be subclassed.
 * </p>
 *
 * @since 3.1
 */
public class PropertiesFileSourceViewerConfiguration extends TextSourceViewerConfiguration {

	/**
	 * The text editor.
	 */
	private ITextEditor fTextEditor;
	/**
	 * The document partitioning.
	 */
	private String fDocumentPartitioning;
	/**
	 * The property key scanner.
	 */
	private AbstractJavaScanner fPropertyKeyScanner;
	/**
	 * The comment scanner.
	 */
	private AbstractJavaScanner fCommentScanner;
	/**
	 * The property value scanner.
	 */
	private AbstractJavaScanner fPropertyValueScanner;
	/**
	 * The color manager.
	 */
	private IColorManager fColorManager;


	/**
	 * Creates a new properties file source viewer configuration for viewers in the given editor
	 * using the given preference store, the color manager and the specified document partitioning.
	 *
	 * @param colorManager the color manager
	 * @param preferenceStore the preference store, can be read-only
	 * @param editor the editor in which the configured viewer(s) will reside
	 * @param partitioning the document partitioning for this configuration
	 */
	public PropertiesFileSourceViewerConfiguration(IColorManager colorManager, IPreferenceStore preferenceStore, ITextEditor editor, String partitioning) {
		super(preferenceStore);
		fColorManager= colorManager;
		fTextEditor= editor;
		fDocumentPartitioning= partitioning;
		initializeScanners();
	}

	/**
	 * Returns the property key scanner for this configuration.
	 *
	 * @return the property key scanner
	 */
	protected RuleBasedScanner getPropertyKeyScanner() {
		return fPropertyKeyScanner;
	}

	/**
	 * Returns the comment scanner for this configuration.
	 *
	 * @return the comment scanner
	 */
	protected RuleBasedScanner getCommentScanner() {
		return fCommentScanner;
	}

	/**
	 * Returns the property value scanner for this configuration.
	 *
	 * @return the property value scanner
	 */
	protected RuleBasedScanner getPropertyValueScanner() {
		return fPropertyValueScanner;
	}

	/**
	 * Returns the color manager for this configuration.
	 *
	 * @return the color manager
	 */
	protected IColorManager getColorManager() {
		return fColorManager;
	}

	/**
	 * Returns the editor in which the configured viewer(s) will reside.
	 *
	 * @return the enclosing editor
	 */
	protected ITextEditor getEditor() {
		return fTextEditor;
	}

	/**
	 * Initializes the scanners.
	 */
	private void initializeScanners() {
		fPropertyKeyScanner= new SingleTokenJavaScanner(getColorManager(), fPreferenceStore, PreferenceConstants.PROPERTIES_FILE_COLORING_KEY);
		fPropertyValueScanner= new PropertyValueScanner(getColorManager(), fPreferenceStore);
		fCommentScanner= new SingleTokenJavaScanner(getColorManager(), fPreferenceStore, PreferenceConstants.PROPERTIES_FILE_COLORING_COMMENT);
	}

	/*
	 * @see SourceViewerConfiguration#getPresentationReconciler(ISourceViewer)
	 */
	public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {

		PresentationReconciler reconciler= new JavaPresentationReconciler();
		reconciler.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));

		DefaultDamagerRepairer dr= new DefaultDamagerRepairer(getPropertyKeyScanner());
		reconciler.setDamager(dr, IDocument.DEFAULT_CONTENT_TYPE);
		reconciler.setRepairer(dr, IDocument.DEFAULT_CONTENT_TYPE);

		dr= new DefaultDamagerRepairer(getCommentScanner());
		reconciler.setDamager(dr, IPropertiesFilePartitions.COMMENT);
		reconciler.setRepairer(dr, IPropertiesFilePartitions.COMMENT);

		dr= new DefaultDamagerRepairer(getPropertyValueScanner());
		reconciler.setDamager(dr, IPropertiesFilePartitions.PROPERTY_VALUE);
		reconciler.setRepairer(dr, IPropertiesFilePartitions.PROPERTY_VALUE);

		return reconciler;
	}

	/*
	 * @see SourceViewerConfiguration#getDoubleClickStrategy(ISourceViewer, String)
	 */
	public ITextDoubleClickStrategy getDoubleClickStrategy(ISourceViewer sourceViewer, String contentType) {
		if (IDocument.DEFAULT_CONTENT_TYPE.equals(contentType))
			return new JavaStringDoubleClickSelector(getConfiguredDocumentPartitioning(sourceViewer));

		return super.getDoubleClickStrategy(sourceViewer, contentType);
	}

	/*
	 * @see SourceViewerConfiguration#getConfiguredContentTypes(ISourceViewer)
	 */
	public String[] getConfiguredContentTypes(ISourceViewer sourceViewer) {
		int length= IPropertiesFilePartitions.PARTITIONS.length;
		String[] contentTypes= new String[length + 1];
		contentTypes[0]= IDocument.DEFAULT_CONTENT_TYPE;
		for (int i= 0; i < length; i++)
			contentTypes[i+1]= IPropertiesFilePartitions.PARTITIONS[i];

		return contentTypes;
	}

	/*
	 * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getConfiguredDocumentPartitioning(org.eclipse.jface.text.source.ISourceViewer)
	 */
	public String getConfiguredDocumentPartitioning(ISourceViewer sourceViewer) {
		if (fDocumentPartitioning != null)
			return fDocumentPartitioning;
		return super.getConfiguredDocumentPartitioning(sourceViewer);
	}

	/**
	 * Determines whether the preference change encoded by the given event
	 * changes the behavior of one of its contained components.
	 *
	 * @param event the event to be investigated
	 * @return <code>true</code> if event causes a behavioral change
	 */
	public boolean affectsTextPresentation(PropertyChangeEvent event) {
		return  fPropertyKeyScanner.affectsBehavior(event)
			|| fCommentScanner.affectsBehavior(event)
			|| fPropertyValueScanner.affectsBehavior(event);
	}

	/**
	 * Adapts the behavior of the contained components to the change
	 * encoded in the given event.
	 *
	 * @param event the event to which to adapt
	 * @see PropertiesFileSourceViewerConfiguration#PropertiesFileSourceViewerConfiguration(IColorManager, IPreferenceStore, ITextEditor, String)
	 */
	public void handlePropertyChangeEvent(PropertyChangeEvent event) {
		if (fPropertyKeyScanner.affectsBehavior(event))
			fPropertyKeyScanner.adaptToPreferenceChange(event);
		if (fCommentScanner.affectsBehavior(event))
			fCommentScanner.adaptToPreferenceChange(event);
		if (fPropertyValueScanner.affectsBehavior(event))
			fPropertyValueScanner.adaptToPreferenceChange(event);
	}

	/*
	 * @see org.eclipse.jface.text.source.SourceViewerConfiguration#getHyperlinkDetectors(org.eclipse.jface.text.source.ISourceViewer)
	 */
	public IHyperlinkDetector[] getHyperlinkDetectors(ISourceViewer sourceViewer) {
		if (!fPreferenceStore.getBoolean(AbstractDecoratedTextEditorPreferenceConstants.EDITOR_HYPERLINKS_ENABLED))
			return null;

		IHyperlinkDetector[] inheritedDetectors= super.getHyperlinkDetectors(sourceViewer);

		if (fTextEditor == null)
			return inheritedDetectors;

		int inheritedDetectorsLength= inheritedDetectors != null ? inheritedDetectors.length : 0;
		IHyperlinkDetector[] detectors= new IHyperlinkDetector[inheritedDetectorsLength + 2];
		detectors[0]= new PropertyKeyHyperlinkDetector(fTextEditor);
		for (int i= 0; i < inheritedDetectorsLength; i++)
			detectors[i+1]= inheritedDetectors[i];

		return detectors;
	}

	/*
	 * @see SourceViewerConfiguration#getReconciler(ISourceViewer)
	 */
	public IReconciler getReconciler(ISourceViewer sourceViewer) {
		if (getEditor() != null && getEditor().isEditable() && fPreferenceStore != null) {
			IReconcilingStrategy strategy= new PropertiesSpellingReconcileStrategy(getEditor());
			MonoReconciler reconciler= new MonoReconciler(strategy, false);
			reconciler.setIsIncrementalReconciler(false);
			reconciler.setProgressMonitor(new NullProgressMonitor());
			reconciler.setDelay(500);
			return reconciler;
		}
		return null;
	}

	/*
	 * @see SourceViewerConfiguration#getConfiguredTextHoverStateMasks(ISourceViewer, String)
	 */
	public int[] getConfiguredTextHoverStateMasks(ISourceViewer sourceViewer, String contentType) {
		return new int[] { ITextViewerExtension2.DEFAULT_HOVER_STATE_MASK };
	}

	/*
	 * @see SourceViewerConfiguration#getTextHover(ISourceViewer, String, int)
	 */
	public ITextHover getTextHover(ISourceViewer sourceViewer, String contentType, int stateMask) {
		AnnotationHover hover= new AnnotationHover();
		hover.setEditor(getEditor());
		return hover;
	}

	/*
	 * @see SourceViewerConfiguration#getTextHover(ISourceViewer, String)
	 */
	public ITextHover getTextHover(ISourceViewer sourceViewer, String contentType) {
		return getTextHover(sourceViewer, contentType, ITextViewerExtension2.DEFAULT_HOVER_STATE_MASK);
	}

	/*
	 * @see SourceViewerConfiguration#getOverviewRulerAnnotationHover(ISourceViewer)
	 */
	public IAnnotationHover getOverviewRulerAnnotationHover(ISourceViewer sourceViewer) {
		return new JavaAnnotationHover(JavaAnnotationHover.OVERVIEW_RULER_HOVER);
	}


	/*
	 * @see SourceViewerConfiguration#getAnnotationHover(ISourceViewer)
	 */
	public IAnnotationHover getAnnotationHover(ISourceViewer sourceViewer) {
		return new JavaAnnotationHover(JavaAnnotationHover.VERTICAL_RULER_HOVER);
	}

	/*
	 * @see SourceViewerConfiguration#getInformationControlCreator(ISourceViewer)
	 */
	public IInformationControlCreator getInformationControlCreator(ISourceViewer sourceViewer) {
		return new IInformationControlCreator() {
			public IInformationControl createInformationControl(Shell parent) {
				return new DefaultInformationControl(parent, SWT.NONE, new HTMLTextPresenter(true));
			}
		};
	}
}