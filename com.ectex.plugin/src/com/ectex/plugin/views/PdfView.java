package com.ectex.plugin.views;

import java.io.File;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IPartListener;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.part.ViewPart;

import com.ectex.plugin.Activator;
import com.ectex.plugin.Commands;
import com.ectex.plugin.editors.EcTeXEditor;
import com.ectex.plugin.sync.SyncTex;

/**
 * "PDF Preview" view: shows the PDF that belongs to the active {@link EcTeXEditor}.
 * <p>
 * Ctrl+click or Ctrl+Space on a position jumps to the matching LaTeX source (inverse search).
 */
public class PdfView extends ViewPart {

	public static final String ID = "com.ectex.plugin.views.PdfView";

	/** Key binding context declared in plugin.xml; active while this view has the focus. */
	public static final String CONTEXT_ID = "com.ectex.plugin.contexts.pdfview";

	private PdfViewer viewer;
	private EcTeXEditor editor;
	private File loadedFile;
	private long loadedModified;

	private final IPartListener partListener = new IPartListener() {
		@Override
		public void partActivated(IWorkbenchPart part) {
			if (part instanceof EcTeXEditor tex) {
				File pdf = tex.getPdfFile();
				// Sub-files (\input) have no PDF of their own: keep showing the current document.
				if (editor == null || (pdf != null && pdf.isFile())) {
					setEditor(tex, false);
				}
			}
		}

		@Override
		public void partBroughtToTop(IWorkbenchPart part) {
		}

		@Override
		public void partClosed(IWorkbenchPart part) {
		}

		@Override
		public void partDeactivated(IWorkbenchPart part) {
		}

		@Override
		public void partOpened(IWorkbenchPart part) {
		}
	};

	// ---------------------------------------------------------------- static helpers

	/** The open PDF view of the page, or null. */
	public static PdfView find(IWorkbenchPage page) {
		if (page == null) {
			return null;
		}
		IViewPart view = page.findView(ID);
		return view instanceof PdfView pdfView ? pdfView : null;
	}

	/** Makes the PDF view visible (without activating it), opening it if necessary. */
	public static PdfView show(IWorkbenchPage page) {
		if (page == null) {
			return null;
		}
		try {
			IViewPart view = page.showView(ID, null, IWorkbenchPage.VIEW_VISIBLE);
			return view instanceof PdfView pdfView ? pdfView : null;
		} catch (PartInitException e) {
			Activator.log("Cannot open the PDF view", e);
			return null;
		}
	}

	// ---------------------------------------------------------------- view life-cycle

	@Override
	public void createPartControl(Composite parent) {
		viewer = new PdfViewer(parent);
		viewer.setInverseSearchHandler(this::inverseSearch);
		viewer.setPlaceholder(() -> "No PDF to show.\nOpen a .tex file and " + Commands.howTo(Commands.BUILD)
				+ " to build it.\nCtrl+click on the PDF jumps to the source.");
		getSite().getService(IContextService.class).activateContext(CONTEXT_ID);

		IWorkbenchPage page = getSite().getPage();
		page.addPartListener(partListener);
		IEditorPart active = page.getActiveEditor();
		if (active instanceof EcTeXEditor tex) {
			setEditor(tex, false);
		} else {
			for (IEditorReference ref : page.getEditorReferences()) {
				if (ref.getEditor(false) instanceof EcTeXEditor tex) {
					setEditor(tex, false);
					break;
				}
			}
		}
	}

	@Override
	public void setFocus() {
		viewer.setFocus();
	}

	@Override
	public void dispose() {
		getSite().getPage().removePartListener(partListener);
		super.dispose();
	}

	// ---------------------------------------------------------------- API used by the editor and handlers

	/** The editor whose PDF is shown, or null. */
	public EcTeXEditor getEditor() {
		return editor;
	}

	/**
	 * Shows the PDF of the given editor.
	 *
	 * @param force reload even if that PDF is already loaded and unchanged (e.g. right after a build)
	 */
	public void setEditor(EcTeXEditor tex, boolean force) {
		if (viewer == null || viewer.isDisposed()) {
			return;
		}
		File pdf = tex == null ? null : tex.getPdfFile();
		// The document is identified by its PDF, not by the editor: all files of a project that share
		// a main file show the same PDF, so switching between them must not reload it or reset the scroll position.
		boolean sameDocument = pdf != null && pdf.equals(loadedFile);
		editor = tex;
		if (pdf == null || !pdf.isFile()) {
			loadedFile = null;
			setContentDescription("");
			viewer.clear();
			return;
		}
		setContentDescription("Document: " + tex.getMainFile().getName());
		if (!sameDocument) {
			viewer.clear(); // different document: start at the top
		}
		if (!sameDocument || force || pdf.lastModified() != loadedModified) {
			loadedFile = pdf;
			loadedModified = pdf.lastModified();
			viewer.load(pdf);
		}
	}

	/** Reloads the view if it shows a document of the given project (its main file setting changed). */
	public void refreshProject(IProject project) {
		EcTeXEditor shown = editor;
		if (shown != null && project.equals(shown.getProject())) {
			setEditor(shown, true);
		}
	}

	/** Called by an editor that is being closed. */
	public void editorDisposed(EcTeXEditor tex) {
		if (tex == editor) {
			setEditor(null, false);
		}
	}

	/** Scrolls to a SyncTeX result and highlights it. */
	public void reveal(SyncTex.ForwardResult r) {
		viewer.reveal(r.page(), r.x(), r.y(), r.width(), r.height());
	}

	/** Ctrl+Space inside the view: inverse search at the mouse position (or the last click). */
	public void inverseSearchAtCursor() {
		PdfViewer.PdfPoint point = viewer.getPointAtCursor();
		if (point == null) {
			showStatus("Click on the PDF page first.", true);
		} else {
			inverseSearch(point);
		}
	}

	private void inverseSearch(PdfViewer.PdfPoint point) {
		if (editor == null) {
			showStatus("No EcTeX editor is associated with this PDF.", true);
			return;
		}
		editor.inverseSearch(point);
	}

	public void showStatus(String message, boolean error) {
		IStatusLineManager status = getViewSite().getActionBars().getStatusLineManager();
		if (error) {
			status.setErrorMessage(message);
		} else {
			status.setErrorMessage(null);
			status.setMessage(message);
		}
	}
}
