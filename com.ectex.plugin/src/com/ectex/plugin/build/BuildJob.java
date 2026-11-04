package com.ectex.plugin.build;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;

import com.ectex.plugin.Activator;
import com.ectex.plugin.preferences.PreferenceConstants;

/**
 * Runs pdflatex or bibtex for a .tex file, mirrors the output into the EcTeX console and
 * reports the result. For pdflatex, "file:line: message" errors become problem markers.
 */
public class BuildJob extends Job {

	/** The external programs EcTeX can run. */
	public enum Tool {
		PDFLATEX("pdflatex", PreferenceConstants.PDFLATEX_COMMAND, PreferenceConstants.PDFLATEX_ARGS),
		/** bibtex works on the .aux file, so it receives the base name without ".tex". */
		BIBTEX("bibtex", PreferenceConstants.BIBTEX_COMMAND, PreferenceConstants.BIBTEX_ARGS);

		final String label;
		final String commandPreference;
		final String argsPreference;

		Tool(String label, String commandPreference, String argsPreference) {
			this.label = label;
			this.commandPreference = commandPreference;
			this.argsPreference = argsPreference;
		}

		public String label() {
			return label;
		}
	}

	/** Matches "./main.tex:12: Undefined control sequence." (pdflatex -file-line-error). */
	private static final Pattern ERROR_LINE = Pattern.compile("^(.+?):(\\d+):\\s*(.*)$");

	private final File texFile;
	private final Tool tool;
	private final Consumer<Boolean> onFinished;

	/**
	 * @param onFinished called in the UI thread with true if the tool exited successfully
	 */
	public BuildJob(File texFile, Tool tool, Consumer<Boolean> onFinished) {
		super("EcTeX: " + tool.label + " " + texFile.getName());
		this.texFile = texFile;
		this.tool = tool;
		this.onFinished = onFinished;
		setUser(false);
		setPriority(BUILD);
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		MessageConsole console = EcTeXConsole.get();
		console.clearConsole();
		MessageConsoleStream out = console.newMessageStream();
		List<String> output = new ArrayList<>();
		boolean success = false;

		try {
			List<String> command = buildCommand();
			File dir = texFile.getParentFile();
			out.println("> " + String.join(" ", command) + "    [" + dir + "]");
			int exitCode = ProcessRunner.run(dir, command, monitor::isCanceled, line -> {
				output.add(line);
				out.println(line);
			});
			success = exitCode == 0 && !monitor.isCanceled();
			out.println(success ? "\n=== " + tool.label + " succeeded ===" : "\n=== " + tool.label
					+ " FAILED (exit code " + exitCode + ") ===");
		} catch (IOException e) {
			out.println("Could not run " + tool.label + ": " + e.getMessage());
			out.println("Check the command in Window > Preferences > EcTeX.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			try {
				out.close();
			} catch (IOException e) {
				Activator.log("Cannot close console stream", e);
			}
		}

		updateWorkspace(output, tool == Tool.PDFLATEX);
		if (!success) {
			EcTeXConsole.show(console);
		}
		boolean result = success;
		Display.getDefault().asyncExec(() -> onFinished.accept(result));
		return Status.OK_STATUS;
	}

	private List<String> buildCommand() {
		IPreferenceStore prefs = Activator.getDefault().getPreferenceStore();
		List<String> command = new ArrayList<>();
		command.add(prefs.getString(tool.commandPreference));
		String args = prefs.getString(tool.argsPreference).trim();
		if (!args.isEmpty()) {
			command.addAll(List.of(args.split("\\s+")));
		}
		String name = texFile.getName();
		if (tool == Tool.BIBTEX) {
			int dot = name.lastIndexOf('.');
			name = dot > 0 ? name.substring(0, dot) : name;
		}
		command.add(name);
		return command;
	}

	/**
	 * Refreshes the folder so new output files show up and, for pdflatex, replaces the problem
	 * markers with the errors of this run.
	 */
	private void updateWorkspace(List<String> output, boolean updateMarkers) {
		IFile[] files = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(texFile.toURI());
		if (files.length == 0) {
			return; // file lives outside the workspace
		}
		IFile file = files[0];
		try {
			ResourcesPlugin.getWorkspace().run(monitor -> {
				if (updateMarkers) {
					for (IMarker marker : file.findMarkers(IMarker.PROBLEM, false, IResource.DEPTH_ZERO)) {
						if (Activator.PLUGIN_ID.equals(marker.getAttribute(IMarker.SOURCE_ID))) {
							marker.delete();
						}
					}
					for (String line : output) {
						Matcher m = ERROR_LINE.matcher(line);
						if (m.matches() && m.group(1).replace('\\', '/').endsWith(texFile.getName())) {
							IMarker marker = file.createMarker(IMarker.PROBLEM);
							marker.setAttributes(Map.<String, Object>of(
									IMarker.MESSAGE, m.group(3),
									IMarker.LINE_NUMBER, Integer.parseInt(m.group(2)),
									IMarker.SEVERITY, IMarker.SEVERITY_ERROR,
									IMarker.SOURCE_ID, Activator.PLUGIN_ID));
						}
					}
				}
				file.getParent().refreshLocal(IResource.DEPTH_ONE, monitor);
			}, null);
		} catch (CoreException e) {
			Activator.log("Cannot update workspace for " + file, e);
		}
	}
}
