package com.ectex.plugin.handlers;

import org.eclipse.core.commands.ExecutionEvent;

import com.ectex.plugin.editors.EcTeXEditor;
import com.ectex.plugin.views.PdfView;

/**
 * Synchronize (default Ctrl+Space): forward search when the LaTeX editor has the focus, inverse search when the
 * PDF view has the focus.
 */
public class SyncHandler extends EcTeXHandler {

	@Override
	protected void execute(EcTeXEditor editor, PdfView view, ExecutionEvent event) {
		if (view != null) {
			view.inverseSearchAtCursor();
		} else {
			editor.forwardSearch();
		}
	}
}
