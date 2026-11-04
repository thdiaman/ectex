package com.ectex.plugin.editors;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.presentation.IPresentationReconciler;
import org.eclipse.jface.text.presentation.PresentationReconciler;
import org.eclipse.jface.text.rules.DefaultDamagerRepairer;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.editors.text.TextSourceViewerConfiguration;

public class LatexSourceViewerConfiguration extends TextSourceViewerConfiguration {

	public LatexSourceViewerConfiguration() {
		super(EditorsUI.getPreferenceStore());
	}

	@Override
	public IPresentationReconciler getPresentationReconciler(ISourceViewer sourceViewer) {
		PresentationReconciler reconciler = new PresentationReconciler();
		reconciler.setDocumentPartitioning(getConfiguredDocumentPartitioning(sourceViewer));
		DefaultDamagerRepairer damagerRepairer = new DefaultDamagerRepairer(new LatexScanner());
		reconciler.setDamager(damagerRepairer, IDocument.DEFAULT_CONTENT_TYPE);
		reconciler.setRepairer(damagerRepairer, IDocument.DEFAULT_CONTENT_TYPE);
		return reconciler;
	}

	@Override
	public String[] getDefaultPrefixes(ISourceViewer sourceViewer, String contentType) {
		return new String[] { "%", "" };
	}
}
