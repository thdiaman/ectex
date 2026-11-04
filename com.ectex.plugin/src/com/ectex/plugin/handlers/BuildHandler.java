package com.ectex.plugin.handlers;

import org.eclipse.core.commands.ExecutionEvent;

import com.ectex.plugin.editors.EcTeXEditor;
import com.ectex.plugin.views.PdfView;

/** Build PDF (default Ctrl+F1): compile the active .tex file with pdflatex and refresh the PDF view. */
public class BuildHandler extends EcTeXHandler {

	@Override
	protected void execute(EcTeXEditor editor, PdfView view, ExecutionEvent event) {
		editor.buildPdf();
	}
}
