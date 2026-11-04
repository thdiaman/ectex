package com.ectex.plugin.project;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.preferences.IEclipsePreferences;
import org.osgi.service.prefs.BackingStoreException;

import com.ectex.plugin.Activator;

/**
 * The main file of a project: the document that pdflatex and bibtex run on, whichever
 * of the project's .tex files is being edited. Stored per project in its
 * {@code .settings/com.ectex.plugin.prefs} file, as a project-relative path.
 */
public final class MainFile {

	private static final String KEY = "mainFile";

	private MainFile() {
	}

	/** The main file configured for the project, or null. The file may not exist (any more). */
	public static IFile get(IProject project) {
		String path = node(project).get(KEY, null);
		return path == null || path.isBlank() ? null : project.getFile(new Path(path));
	}

	public static void set(IFile file) throws BackingStoreException {
		IEclipsePreferences node = node(file.getProject());
		node.put(KEY, file.getProjectRelativePath().toString());
		node.flush();
	}

	public static void clear(IProject project) throws BackingStoreException {
		IEclipsePreferences node = node(project);
		node.remove(KEY);
		node.flush();
	}

	private static IEclipsePreferences node(IProject project) {
		return new ProjectScope(project).getNode(Activator.PLUGIN_ID);
	}
}
