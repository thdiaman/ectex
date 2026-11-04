package com.ectex.plugin.handlers;

import org.eclipse.core.commands.ExecutionEvent;

import com.ectex.plugin.editors.EcTeXEditor;
import com.ectex.plugin.views.PdfView;

/** Run BibTeX (default Ctrl+F3): run bibtex for the active .tex file (needs the .aux file from a previous pdflatex run). */
public class BibtexHandler extends EcTeXHandler {

	@Override
	protected void execute(EcTeXEditor editor, PdfView view, ExecutionEvent event) {
		editor.runBibtex();
	}
}
