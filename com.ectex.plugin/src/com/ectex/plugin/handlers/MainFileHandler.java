package com.ectex.plugin.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.osgi.service.prefs.BackingStoreException;

import com.ectex.plugin.Activator;
import com.ectex.plugin.Commands;
import com.ectex.plugin.project.MainFile;
import com.ectex.plugin.project.MainFileDecorator;
import com.ectex.plugin.views.PdfView;

/**
 * "Set as Main File" and "Clear Main File", offered in the EcTeX submenu of the context menu of a
 * .tex file or a project (Project Explorer, Navigator, Package Explorer) and of the EcTeX editor.
 * <p>
 * The main file is the document that build, BibTeX, the PDF Preview and sync use for the whole project.
 * It is stored per project.
 */
public class MainFileHandler extends AbstractHandler {

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
		refresh(project);
		report(HandlerUtil.getActivePart(event), message, error);
		return null;
	}

	/** The resource under the right-click: the selected file or project, else the file of the active editor. */
	private static IResource target(ExecutionEvent event) {
		ISelection selection = HandlerUtil.getCurrentSelection(event);
		if (selection instanceof IStructuredSelection structured && structured.size() == 1) {
			IResource resource = Adapters.adapt(structured.getFirstElement(), IResource.class);
			if (resource != null) {
				return resource;
			}
		}
		IEditorPart editor = HandlerUtil.getActiveEditor(event);
		return editor != null && editor.getEditorInput() instanceof IFileEditorInput input ? input.getFile() : null;
	}

	/** Re-decorates the explorers and reloads the PDF Preview if it shows a document of that project. */
	private static void refresh(IProject project) {
		IWorkbench workbench = PlatformUI.getWorkbench();
		workbench.getDecoratorManager().update(MainFileDecorator.ID);
		if (project == null) {
			return;
		}
		for (IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
			for (IWorkbenchPage page : window.getPages()) {
				PdfView view = PdfView.find(page);
				if (view != null) {
					view.refreshProject(project);
				}
			}
		}
	}

	/** Shows the result in the status line of the view or editor the command was started from. */
	private static void report(IWorkbenchPart part, String message, boolean error) {
		IStatusLineManager status = null;
		if (part instanceof IViewPart view) {
			status = view.getViewSite().getActionBars().getStatusLineManager();
		} else if (part instanceof IEditorPart editor) {
			status = editor.getEditorSite().getActionBars().getStatusLineManager();
		}
		if (status == null) {
			return;
		}
		if (error) {
			status.setErrorMessage(message);
		} else {
			status.setErrorMessage(null);
			status.setMessage(message);
		}
	}
}
