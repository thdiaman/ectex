package com.ectex.plugin.handlers;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.osgi.util.NLS;
import org.osgi.service.prefs.BackingStoreException;

import com.ectex.plugin.Activator;
import com.ectex.plugin.Commands;
import com.ectex.plugin.project.LatexNature;
import com.ectex.plugin.project.MainFile;

/**
 * "Set as Main File" and "Clear Main File", offered in the EcTeX submenu of the context menu of a
 * .tex file or a LaTeX project (Project Explorer, Navigator, Package Explorer) and of the EcTeX editor.
 * <p>
 * The main file is the document that build, BibTeX, the PDF Preview and sync use for the whole project.
 * It is stored per project and only exists in LaTeX projects (see {@link LatexNature}).
 */
public class MainFileHandler extends ResourceCommandHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		boolean set = Commands.SET_MAIN_FILE.equals(event.getCommand().getId());
		IResource target = target(event);
		IProject project = target == null ? null : target.getProject();
		String message;
		boolean error = false;
		try {
			if (project == null) {
				message = "Select a .tex file or a project first.";
				error = true;
			} else if (!LatexNature.has(project)) {
				message = NLS.bind("Project {0} is not a LaTeX project. Convert it first: right-click the project >"
						+ " Configure > Convert to LaTeX Project.", project.getName());
				error = true;
			} else if (set) {
				if (!(target instanceof IFile file) || !"tex".equals(file.getFileExtension())) {
					message = "Select a .tex file to make it the main file.";
					error = true;
				} else {
					MainFile.set(file);
					message = NLS.bind("Main file of project {0}: {1}. Build, BibTeX, the PDF Preview and sync of this"
							+ " project now use it.", project.getName(), file.getProjectRelativePath());
				}
			} else if (MainFile.get(project) == null) {
				message = "No main file is set for project " + project.getName() + ".";
			} else {
				MainFile.clear(project);
				message = "Main file of project " + project.getName() + " cleared: each file is built on its own again.";
			}
		} catch (BackingStoreException e) {
			Activator.log("Cannot save the main file setting", e);
			message = "Cannot save the main file setting: " + e.getMessage();
			error = true;
		}
		finish(event, project, message, error);
		return null;
	}
}
