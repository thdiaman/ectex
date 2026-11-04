package com.ectex.plugin.handlers;

import org.eclipse.core.commands.ExecutionEvent;

import com.ectex.plugin.editors.EcTeXEditor;
import com.ectex.plugin.views.PdfView;

/**
 * Build All (default Ctrl+F5): LaTeX, bibliography tool, LaTeX, LaTeX - or LaTeX, LaTeX - as chosen in
 * Preferences > EcTeX.
 */
public class BuildAllHandler extends EcTeXHandler {

	@Override
	protected void execute(EcTeXEditor editor, PdfView view, ExecutionEvent event) {
		editor.buildAll();
	}
}
