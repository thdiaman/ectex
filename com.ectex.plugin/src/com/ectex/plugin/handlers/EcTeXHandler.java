package com.ectex.plugin.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.handlers.HandlerUtil;

import com.ectex.plugin.editors.EcTeXEditor;
import com.ectex.plugin.views.PdfView;

/**
 * Base class for handlers that run either from an {@link EcTeXEditor} or from the {@link PdfView}.
 * The target editor is the active EcTeX editor, or - when the PDF view has the focus - the editor
 * whose PDF the view shows.
 */
abstract class EcTeXHandler extends AbstractHandler {

	@Override
	public final Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchPart part = HandlerUtil.getActivePart(event);
		if (part instanceof PdfView view) {
			EcTeXEditor editor = view.getEditor();
			if (editor == null) {
				view.showStatus("Open a .tex file in the EcTeX editor first.", true);
			} else {
				execute(editor, view, event);
			}
		} else {
			IEditorPart editor = HandlerUtil.getActiveEditor(event);
			if (editor instanceof EcTeXEditor tex) {
				execute(tex, null, event);
			}
		}
		return null;
	}

	/**
	 * @param editor the target editor, never null
	 * @param view   the PDF view if the command was invoked from it, otherwise null
	 */
	protected abstract void execute(EcTeXEditor editor, PdfView view, ExecutionEvent event)
			throws ExecutionException;
}
