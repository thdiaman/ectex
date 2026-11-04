package com.ectex.plugin.handlers;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;

import com.ectex.plugin.project.MainFileDecorator;
import com.ectex.plugin.views.PdfView;

/**
 * Base class of the commands in the explorer and editor context menus that act on a project or a
 * .tex file (main file, LaTeX project nature).
 */
abstract class ResourceCommandHandler extends AbstractHandler {

	/** The resource under the right-click: the selected file or project, else the file of the active editor. */
	protected static IResource target(ExecutionEvent event) {
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

	/** Re-decorates the explorers, reloads the PDF Preview if it shows a document of the project, reports the result. */
	protected static void finish(ExecutionEvent event, IProject project, String message, boolean error) {
		IWorkbench workbench = PlatformUI.getWorkbench();
		workbench.getDecoratorManager().update(MainFileDecorator.ID);
		if (project != null) {
			for (IWorkbenchWindow window : workbench.getWorkbenchWindows()) {
				for (IWorkbenchPage page : window.getPages()) {
					PdfView view = PdfView.find(page);
					if (view != null) {
						view.refreshProject(project);
					}
				}
			}
		}
		report(HandlerUtil.getActivePart(event), message, error);
	}

	/** Shows a message in the status line of the view or editor the command was started from. */
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
