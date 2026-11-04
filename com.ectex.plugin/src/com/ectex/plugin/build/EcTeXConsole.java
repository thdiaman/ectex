package com.ectex.plugin.build;

import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.MessageConsole;

/**
 * The "EcTeX Build" console that shows the pdflatex output.
 */
public final class EcTeXConsole {

	private static final String NAME = "EcTeX Build";

	private EcTeXConsole() {
	}

	public static synchronized MessageConsole get() {
		IConsoleManager manager = ConsolePlugin.getDefault().getConsoleManager();
		for (IConsole console : manager.getConsoles()) {
			if (NAME.equals(console.getName()) && console instanceof MessageConsole messageConsole) {
				return messageConsole;
			}
		}
		MessageConsole console = new MessageConsole(NAME, null);
		manager.addConsoles(new IConsole[] { console });
		return console;
	}

	/** Brings the console view to the front without stealing focus from the editor. */
	public static void show(MessageConsole console) {
		Display.getDefault().asyncExec(() -> ConsolePlugin.getDefault().getConsoleManager().showConsoleView(console));
	}
}
