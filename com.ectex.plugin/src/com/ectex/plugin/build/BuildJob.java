package com.ectex.plugin.build;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
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
 * Runs one or several build steps (see {@link BuildPlan}) for a .tex file in the background, mirrors the
 * output into the EcTeX console and reports the result. LaTeX errors ("file:line: message") become problem
 * markers on the files that contain them.
 */
public class BuildJob extends Job {

	/** Matches "chapters/ch2.tex:12: Undefined control sequence." (pdflatex -file-line-error). */
	private static final Pattern ERROR_LINE = Pattern.compile("^(.+?):(\\d+):\\s*(.*)$");

	private final File texFile;
	private final List<BuildPlan.Kind> kinds;
	private final Consumer<Boolean> onFinished;

	/**
	 * @param texFile    the file to build (the main file)
	 * @param kinds      the tools to run, in order; one LaTeX pass, one bibliography run, or the Build All sequence
	 * @param onFinished called in the UI thread with true if every step succeeded
	 */
	public BuildJob(File texFile, List<BuildPlan.Kind> kinds, Consumer<Boolean> onFinished) {
		super("EcTeX: " + (kinds.size() > 1 ? "Build All" : kinds.get(0).label()) + " " + texFile.getName());
		this.texFile = texFile;
		this.kinds = kinds;
		this.onFinished = onFinished;
		setUser(false);
		setPriority(BUILD);
	}

	@Override
	protected IStatus run(IProgressMonitor monitor) {
		MessageConsole console = EcTeXConsole.get();
		console.clearConsole();
		MessageConsoleStream out = console.newMessageStream();
		BuildRunner.Result result = null;

		try {
			List<BuildPlan.Step> steps = BuildPlan.steps(texFile, settings(), kinds);
			result = BuildRunner.run(texFile.getParentFile(), steps, monitor::isCanceled, out::println);
		} catch (IOException e) {
			out.println(e.getMessage());
			out.println("Check the commands in Window > Preferences > EcTeX.");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			try {
				out.close();
			} catch (IOException e) {
				Activator.log("Cannot close console stream", e);
			}
		}

		boolean success = result != null && result.success();
		// Markers come from the last LaTeX pass that ran (also when a later step failed).
		List<String> latexOutput = result == null ? null : result.lastLatexOutput();
		updateWorkspace(latexOutput == null ? List.of() : latexOutput, latexOutput != null);
		if (!success && !(result != null && result.cancelled())) {
			EcTeXConsole.show(console);
		}
		Display.getDefault().asyncExec(() -> onFinished.accept(success));
		return Status.OK_STATUS;
	}

	private static BuildPlan.Settings settings() {
		IPreferenceStore prefs = Activator.getDefault().getPreferenceStore();
		return new BuildPlan.Settings(prefs.getString(PreferenceConstants.PDFLATEX_COMMAND),
				prefs.getString(PreferenceConstants.PDFLATEX_ARGS), prefs.getString(PreferenceConstants.BIBTEX_COMMAND),
				prefs.getString(PreferenceConstants.BIBTEX_ARGS));
	}

	/**
	 * Refreshes the folder so new output files show up and, if requested, replaces the problem markers
	 * with the errors of the given LaTeX output.
	 */
	private void updateWorkspace(List<String> output, boolean updateMarkers) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IFile[] files = root.findFilesForLocationURI(texFile.toURI());
		if (files.length == 0) {
			return; // file lives outside the workspace
		}
		IFile mainFile = files[0];
		try {
			ResourcesPlugin.getWorkspace().run(monitor -> {
				if (updateMarkers) {
					// Errors can be in any file the main file includes: replace the markers of the whole project.
					for (IMarker marker : mainFile.getProject().findMarkers(IMarker.PROBLEM, false,
							IResource.DEPTH_INFINITE)) {
						if (Activator.PLUGIN_ID.equals(marker.getAttribute(IMarker.SOURCE_ID))) {
							marker.delete();
						}
					}
					for (String line : output) {
						Matcher m = ERROR_LINE.matcher(line);
						if (!m.matches()) {
							continue;
						}
						File source = sourceFile(texFile.getParentFile(), m.group(1));
						IFile[] sourceFiles = source == null ? new IFile[0] : root.findFilesForLocationURI(source.toURI());
						if (sourceFiles.length > 0) {
							IMarker marker = sourceFiles[0].createMarker(IMarker.PROBLEM);
							marker.setAttributes(Map.<String, Object>of(
									IMarker.MESSAGE, m.group(3),
									IMarker.LINE_NUMBER, Integer.parseInt(m.group(2)),
									IMarker.SEVERITY, IMarker.SEVERITY_ERROR,
									IMarker.SOURCE_ID, Activator.PLUGIN_ID));
						}
					}
				}
				mainFile.getParent().refreshLocal(IResource.DEPTH_ONE, monitor);
			}, null);
		} catch (CoreException e) {
			Activator.log("Cannot update workspace for " + mainFile, e);
		}
	}

	/**
	 * The file named in a "-file-line-error" message, which is relative to the folder pdflatex ran in
	 * ("chapters/ch2.tex") or absolute.
	 *
	 * @param dir folder of the main file
	 * @return the existing file, or null (e.g. for messages about packages outside the project)
	 */
	public static File sourceFile(File dir, String path) {
		File file = new File(path.replace('\\', '/'));
		if (!file.isAbsolute()) {
			file = new File(dir, path);
		}
		file = file.toPath().normalize().toFile();
		return file.isFile() ? file : null;
	}
}
