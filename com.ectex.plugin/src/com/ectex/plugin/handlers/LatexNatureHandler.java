package com.ectex.plugin.handlers;

import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.handlers.HandlerUtil;
import org.osgi.service.prefs.BackingStoreException;

import com.ectex.plugin.Activator;
import com.ectex.plugin.Commands;
import com.ectex.plugin.project.LatexNature;
import com.ectex.plugin.project.MainFile;

/**
 * "Convert to LaTeX Project" and "Remove LaTeX Nature" (project context menu, Configure submenu, and the
 * EcTeX submenu). A LaTeX project can have a main file; removing the nature also clears it.
 */
public class LatexNatureHandler extends ResourceCommandHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		boolean convert = Commands.CONVERT_TO_LATEX.equals(event.getCommand().getId());
		IResource target = target(event);
		IProject project = target == null ? null : target.getProject();
		String message;
		boolean error = false;
		try {
			if (project == null || !project.isOpen()) {
				message = "Select an open project (or a file in it) first.";
				error = true;
			} else if (convert) {
				if (LatexNature.has(project)) {
					message = "Project " + project.getName() + " is already a LaTeX project.";
				} else {
					LatexNature.add(project);
					message = NLS.bind("Project {0} is now a LaTeX project. Right-click its main .tex file and choose"
							+ " EcTeX > Set as Main File if the document is split over several files.", project.getName());
				}
			} else if (!LatexNature.has(project)) {
				message = "Project " + project.getName() + " is not a LaTeX project.";
			} else if (MainFile.get(project) != null && !MessageDialog.openConfirm(HandlerUtil.getActiveShell(event),
					"Remove LaTeX Nature", NLS.bind("Removing the LaTeX nature from project {0} also clears its main file."
							+ " Continue?", project.getName()))) {
				message = "Nothing changed.";
			} else {
				LatexNature.remove(project);
				message = "Project " + project.getName() + " is a plain project again (main file cleared).";
			}
		} catch (CoreException | BackingStoreException e) {
			Activator.log("Cannot change the LaTeX nature of " + (project == null ? "?" : project.getName()), e);
			message = "Cannot change the LaTeX nature: " + e.getMessage();
			error = true;
		}
		finish(event, project, message, error);
		return null;
	}
}
