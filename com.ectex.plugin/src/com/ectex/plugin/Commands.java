package com.ectex.plugin;

import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.keys.IBindingService;

/**
 * Command ids declared in plugin.xml. Their key bindings are only defaults: users can change
 * them in Window > Preferences > General > Keys (category "EcTeX").
 */
public final class Commands {

	public static final String BUILD = "com.ectex.plugin.commands.build";
	public static final String BIBTEX = "com.ectex.plugin.commands.bibtex";
	public static final String SYNC = "com.ectex.plugin.commands.sync";
	public static final String SET_MAIN_FILE = "com.ectex.plugin.commands.setMainFile";
	public static final String CLEAR_MAIN_FILE = "com.ectex.plugin.commands.clearMainFile";

	private Commands() {
	}

	/**
	 * How to invoke a command, based on the key currently bound to it ("press Ctrl+F2"), so messages stay
	 * correct after the user rebinds it. Falls back to "use the EcTeX menu" when it is unbound.
	 */
	public static String howTo(String commandId) {
		try {
			IBindingService bindings = PlatformUI.getWorkbench().getService(IBindingService.class);
			String key = bindings == null ? null : bindings.getBestActiveBindingFormattedFor(commandId);
			if (key != null && !key.isEmpty()) {
				return "press " + key;
			}
		} catch (IllegalStateException e) {
			// workbench not available (shutting down)
		}
		return "use the EcTeX menu";
	}
}
