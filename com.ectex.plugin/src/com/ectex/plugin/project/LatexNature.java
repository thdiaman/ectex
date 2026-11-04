package com.ectex.plugin.project;

import java.util.Arrays;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.runtime.CoreException;
import org.osgi.service.prefs.BackingStoreException;

import com.ectex.plugin.Activator;

/**
 * The LaTeX project nature. A project with this nature is a "LaTeX project": it can have a main file,
 * and the EcTeX project commands (convert, remove, main file) are offered for it.
 * <p>
 * Everything else works without the nature: .tex files in any project still open in the EcTeX editor and
 * build on their own.
 */
public class LatexNature implements IProjectNature {

	/** Id of the nature: plug-in id + the extension id declared in plugin.xml. */
	public static final String ID = Activator.PLUGIN_ID + ".latexNature";

	private IProject project;

	@Override
	public void configure() {
		// nothing to set up yet
	}

	@Override
	public void deconfigure() {
		// nothing to clean up here; remove(IProject) also clears the main file
	}

	@Override
	public IProject getProject() {
		return project;
	}

	@Override
	public void setProject(IProject project) {
		this.project = project;
	}

	/** True if the project is open and is a LaTeX project. */
	public static boolean has(IProject project) {
		try {
			return project != null && project.isOpen() && project.hasNature(ID);
		} catch (CoreException e) {
			return false;
		}
	}

	/** Converts the project into a LaTeX project. */
	public static void add(IProject project) throws CoreException {
		if (has(project)) {
			return;
		}
		IProjectDescription description = project.getDescription();
		String[] ids = description.getNatureIds();
		String[] extended = Arrays.copyOf(ids, ids.length + 1);
		extended[ids.length] = ID;
		description.setNatureIds(extended);
		project.setDescription(description, null);
	}

	/** Turns the project back into a plain project; its main file setting is cleared as well. */
	public static void remove(IProject project) throws CoreException, BackingStoreException {
		if (!has(project)) {
			return;
		}
		MainFile.clear(project);
		IProjectDescription description = project.getDescription();
		String[] remaining = Arrays.stream(description.getNatureIds()).filter(id -> !ID.equals(id))
				.toArray(String[]::new);
		description.setNatureIds(remaining);
		project.setDescription(description, null);
	}
}
