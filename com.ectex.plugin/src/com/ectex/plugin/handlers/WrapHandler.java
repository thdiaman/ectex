package com.ectex.plugin.handlers;

import java.util.Map;

import org.eclipse.core.commands.ExecutionEvent;

import com.ectex.plugin.editors.EcTeXEditor;
import com.ectex.plugin.views.PdfView;

/**
 * Productivity shortcuts that wrap the selection (or place the caret inside an empty pair):
 * Ctrl+B (textbf), Ctrl+I (textit), Ctrl+U (underline), Ctrl+Shift+E (emph) and
 * Ctrl+Shift+M (inline math).
 * <p>
 * One class serves all of them; the command id selects the template.
 */
public class WrapHandler extends EcTeXHandler {

	private static final Map<String, String[]> TEMPLATES = Map.of(
			"com.ectex.plugin.commands.bold", new String[] { "\\textbf{", "}" },
			"com.ectex.plugin.commands.italic", new String[] { "\\textit{", "}" },
			"com.ectex.plugin.commands.underline", new String[] { "\\underline{", "}" },
			"com.ectex.plugin.commands.emph", new String[] { "\\emph{", "}" },
			"com.ectex.plugin.commands.math", new String[] { "$", "$" });

	@Override
	protected void execute(EcTeXEditor editor, PdfView view, ExecutionEvent event) {
		String[] template = TEMPLATES.get(event.getCommand().getId());
		// The PDF view shares the editor's key bindings; typing shortcuts only make sense in the editor.
		if (template != null && view == null) {
			editor.wrapSelection(template[0], template[1]);
		}
	}
}
