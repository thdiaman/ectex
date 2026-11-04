package com.ectex.plugin;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

/**
 * Plug-in life-cycle class of EcTeX.
 */
public class Activator extends AbstractUIPlugin {

	public static final String PLUGIN_ID = "com.ectex.plugin";

	private static Activator plugin;

	@Override
	public void start(BundleContext context) throws Exception {
		super.start(context);
		plugin = this;
		// PDFBox only needs java.awt.image to rasterise pages; keep AWT from starting
		// a second GUI toolkit next to SWT.
		System.setProperty("java.awt.headless", "true");
	}

	@Override
	public void stop(BundleContext context) throws Exception {
		plugin = null;
		super.stop(context);
	}

	public static Activator getDefault() {
		return plugin;
	}

	public static void log(String message, Throwable error) {
		log(IStatus.ERROR, message, error);
	}

	public static void log(int severity, String message, Throwable error) {
		Activator a = plugin;
		if (a != null) {
			a.getLog().log(new Status(severity, PLUGIN_ID, message, error));
		}
	}
}
