package com.ectex.plugin.handlers;

import org.eclipse.core.commands.ExecutionEvent;

import com.ectex.plugin.Commands;
import com.ectex.plugin.editors.EcTeXEditor;
import com.ectex.plugin.views.PdfView;

/**
 * "Set as Main File" and "Clear Main File" (EcTeX menu, no default key): the main file is the
 * document that build, BibTeX, the PDF Preview and sync use for the whole project.
 */
public class MainFileHandler extends EcTeXHandler {

	@Override
	protected void execute(EcTeXEditor editor, PdfView view, ExecutionEvent event) {
		if (view != null) {
			view.showStatus("Select the LaTeX file in its editor first, then use the EcTeX menu.", true);
			return;
		}
		if (Commands.SET_MAIN_FILE.equals(event.getCommand().getId())) {
			editor.setAsMainFile();
		} else {
			editor.clearMainFile();
		}
	}
}
