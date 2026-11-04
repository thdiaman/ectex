package com.ectex.plugin.handlers;

import org.eclipse.core.commands.ExecutionEvent;

import com.ectex.plugin.editors.EcTeXEditor;
import com.ectex.plugin.views.PdfView;

/**
 * Run Bibliography Tool (default Ctrl+F3): runs BibTeX or Biber, as chosen in Preferences > EcTeX, on the
 * .aux file of the last LaTeX run.
 */
public class BibliographyHandler extends EcTeXHandler {

	@Override
	protected void execute(EcTeXEditor editor, PdfView view, ExecutionEvent event) {
		editor.runBibliography();
	}
}
