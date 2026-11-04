package com.ectex.plugin.editors;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.URIUtil;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.IStatusLineManager;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IURIEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.texteditor.ITextEditor;

import com.ectex.plugin.Activator;
import com.ectex.plugin.Commands;
import com.ectex.plugin.build.BuildJob;
import com.ectex.plugin.sync.SyncTex;
import com.ectex.plugin.views.PdfView;
import com.ectex.plugin.views.PdfViewer;

/**
 * LaTeX source editor. The PDF is shown in the separate {@link PdfView}, which is opened
 * to the right of the editor area and follows the active EcTeX editor.
 */
public class EcTeXEditor extends TextEditor {

	public static final String ID = "com.ectex.plugin.editors.EcTeXEditor";

	/** Key binding context declared in plugin.xml (child of the text editor scope). */
	public static final String CONTEXT_ID = "com.ectex.plugin.contexts.editor";

	private Job buildJob;

	@Override
	protected void initializeEditor() {
		super.initializeEditor();
		setSourceViewerConfiguration(new LatexSourceViewerConfiguration());
	}

	@Override
	protected void initializeKeyBindingScopes() {
		setKeyBindingScopes(new String[] { CONTEXT_ID });
	}

	@Override
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		// Open the PDF view next to the editor (without taking the focus).
		IWorkbenchPage page = getSite().getPage();
		parent.getDisplay().asyncExec(() -> {
			if (!parent.isDisposed()) {
				PdfView view = PdfView.show(page);
				if (view != null && view.getEditor() == null) {
					view.setEditor(this, false);
				}
			}
		});
	}

	@Override
	protected void doSetInput(IEditorInput input) throws CoreException {
		super.doSetInput(input);
		PdfView view = findPdfView();
		if (view != null && view.getEditor() == this) {
			view.setEditor(this, true); // re-targeted (e.g. "Save As")
		}
	}

	@Override
	public void dispose() {
		PdfView view = findPdfView();
		if (view != null) {
			view.editorDisposed(this);
		}
		super.dispose();
	}

	private PdfView findPdfView() {
		return getSite() == null ? null : PdfView.find(getSite().getPage());
	}

	// ---------------------------------------------------------------- files

	/** The .tex file on disk, or null if the input is not a local file. */
	public File getTexFile() {
		IEditorInput input = getEditorInput();
		if (input instanceof IFileEditorInput fileInput) {
			IPath location = fileInput.getFile().getLocation();
			return location == null ? null : location.toFile();
		}
		if (input instanceof IURIEditorInput uriInput) {
			return URIUtil.toFile(uriInput.getURI());
		}
		return null;
	}

	/** The PDF pdflatex produces for this document (same folder, same base name). */
	public File getPdfFile() {
		File tex = getTexFile();
		if (tex == null) {
			return null;
		}
		return new File(tex.getParentFile(), baseName(tex) + ".pdf");
	}

	private static String baseName(File tex) {
		String name = tex.getName();
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	// ---------------------------------------------------------------- build and bibtex commands

	/** Saves the document, runs pdflatex and refreshes the PDF view when it succeeds. */
	public void buildPdf() {
		File tex = getTexFile();
		if (tex == null) {
			showStatus("EcTeX can only build documents stored in the local file system.", true);
			return;
		}
		if (isBusy()) {
			return;
		}
		if (isDirty() && !getSite().getPage().saveEditor(this, false)) {
			return; // save failed or was cancelled
		}
		showStatus("Running pdflatex on " + tex.getName() + " ...", false);
		IWorkbenchPage page = getSite().getPage();
		buildJob = new BuildJob(tex, BuildJob.Tool.PDFLATEX, success -> {
			if (success) {
				showStatus("pdflatex finished: " + tex.getName(), false);
				PdfView view = PdfView.show(page);
				if (view != null) {
					view.setEditor(this, true);
				}
			} else {
				showStatus("pdflatex failed - see the \"EcTeX Build\" console.", true);
			}
		});
		buildJob.schedule();
	}

	/** Runs bibtex on the .aux file written by the last pdflatex run. */
	public void runBibtex() {
		File tex = getTexFile();
		if (tex == null) {
			showStatus("EcTeX can only run bibtex for documents stored in the local file system.", true);
			return;
		}
		if (isBusy()) {
			return;
		}
		File aux = new File(tex.getParentFile(), baseName(tex) + ".aux");
		if (!aux.isFile()) {
			showStatus("No " + aux.getName() + " yet - run pdflatex first (" + Commands.howTo(Commands.BUILD)
					+ ") so bibtex has citations to resolve.", true);
			return;
		}
		showStatus("Running bibtex on " + aux.getName() + " ...", false);
		buildJob = new BuildJob(tex, BuildJob.Tool.BIBTEX, success -> {
			if (success) {
				showStatus("bibtex finished - run pdflatex twice (" + Commands.howTo(Commands.BUILD)
						+ ") to update the citations in the PDF.", false);
			} else {
				showStatus("bibtex failed - see the \"EcTeX Build\" console.", true);
			}
		});
		buildJob.schedule();
	}

	private boolean isBusy() {
		if (buildJob != null && buildJob.getState() != Job.NONE) {
			showStatus("A build is already running.", true);
			return true;
		}
		return false;
	}

	// ---------------------------------------------------------------- synchronisation

	/** Source -> PDF: highlights the PDF location that corresponds to the caret line. */
	public void forwardSearch() {
		File tex = getTexFile();
		File pdf = getPdfFile();
		if (tex == null || pdf == null || !pdf.isFile()) {
			showStatus("No PDF yet - build the document first (" + Commands.howTo(Commands.BUILD) + ").", true);
			return;
		}
		PdfView view = PdfView.show(getSite().getPage());
		if (view == null) {
			return;
		}
		view.setEditor(this, false); // loads the PDF if the view shows something else or an older build
		int line = ((ITextSelection) getSelectionProvider().getSelection()).getStartLine() + 1;
		Display display = getSite().getShell().getDisplay();
		Job job = Job.create("EcTeX forward search", monitor -> {
			try {
				SyncTex.ForwardResult r = SyncTex.forward(tex.getParentFile(), tex, pdf.getName(), line, 0);
				display.asyncExec(() -> {
					if (r == null) {
						showStatus("SyncTeX has no PDF location for line " + line + " (rebuild: "
								+ Commands.howTo(Commands.BUILD) + ").", true);
					} else {
						view.reveal(r);
						showStatus("PDF page " + r.page(), false);
					}
				});
			} catch (IOException e) {
				display.asyncExec(() -> showStatus(e.getMessage(), true));
			}
		});
		job.setSystem(true);
		job.schedule();
	}

	/** PDF -> source: jumps to the source line that produced the text at the given PDF position. */
	public void inverseSearch(PdfViewer.PdfPoint point) {
		File tex = getTexFile();
		File pdf = getPdfFile();
		if (tex == null || pdf == null) {
			showStatus("EcTeX can only synchronise documents stored in the local file system.", true);
			return;
		}
		Display display = getSite().getShell().getDisplay();
		Job job = Job.create("EcTeX inverse search", monitor -> {
			try {
				SyncTex.InverseResult r = SyncTex.inverse(tex.getParentFile(), pdf.getName(), point.page(), point.x(),
						point.y());
				display.asyncExec(() -> {
					if (getSite().getShell().isDisposed()) {
						return;
					}
					if (r == null) {
						showStatus("SyncTeX found no source for that position (rebuild: "
								+ Commands.howTo(Commands.BUILD) + ").", true);
					} else {
						openAtLine(r.file(), r.line());
					}
				});
			} catch (IOException e) {
				display.asyncExec(() -> showStatus(e.getMessage(), true));
			}
		});
		job.setSystem(true);
		job.schedule();
	}

	private void openAtLine(File file, int line) {
		try {
			ITextEditor target = this;
			if (!sameFile(file, getTexFile())) {
				target = null;
				IFile[] workspaceFiles = ResourcesPlugin.getWorkspace().getRoot().findFilesForLocationURI(file.toURI());
				IEditorPart part;
				if (workspaceFiles.length > 0) {
					part = IDE.openEditor(getSite().getPage(), workspaceFiles[0]);
				} else {
					IFileStore store = EFS.getLocalFileSystem().getStore(file.toURI());
					part = IDE.openEditorOnFileStore(getSite().getPage(), store);
				}
				if (part instanceof ITextEditor textEditor) {
					target = textEditor;
				}
			}
			if (target == null) {
				return;
			}
			IDocument document = target.getDocumentProvider().getDocument(target.getEditorInput());
			IRegion region = document.getLineInformation(Math.min(line, document.getNumberOfLines()) - 1);
			target.selectAndReveal(region.getOffset(), region.getLength());
			if (target == this) {
				// The focus may be in the PDF view: bring the editor back.
				getSite().getPage().activate(this);
				getSourceViewer().getTextWidget().setFocus();
			}
			showStatus("Source line " + line, false);
		} catch (CoreException | BadLocationException e) {
			Activator.log("Cannot open " + file + " at line " + line, e);
			showStatus("Cannot open " + file.getName() + ": " + e.getMessage(), true);
		}
	}

	private static boolean sameFile(File a, File b) {
		if (a == null || b == null) {
			return false;
		}
		try {
			return a.getCanonicalFile().equals(b.getCanonicalFile());
		} catch (IOException e) {
			return a.getAbsoluteFile().equals(b.getAbsoluteFile());
		}
	}

	// ---------------------------------------------------------------- Ctrl+B & co.

	/**
	 * Wraps the selection in {@code prefix ... suffix}, leaving the caret behind the suffix. Without a
	 * selection it inserts {@code prefix + suffix} and puts the caret between them.
	 */
	public void wrapSelection(String prefix, String suffix) {
		if (!validateEditorInputState()) {
			return;
		}
		ITextSelection selection = (ITextSelection) getSelectionProvider().getSelection();
		IDocument document = getDocumentProvider().getDocument(getEditorInput());
		int offset = selection.getOffset();
		int length = selection.getLength();
		try {
			if (length > 0) {
				String selected = document.get(offset, length);
				document.replace(offset, length, prefix + selected + suffix);
				selectAndReveal(offset + prefix.length() + selected.length() + suffix.length(), 0);
			} else {
				document.replace(offset, 0, prefix + suffix);
				selectAndReveal(offset + prefix.length(), 0);
			}
		} catch (BadLocationException e) {
			Activator.log("Cannot insert " + prefix + suffix, e);
		}
	}

	// ---------------------------------------------------------------- misc

	/** Shows a message in the status line of the editor and of the PDF view (whichever has the focus). */
	private void showStatus(String message, boolean error) {
		if (getEditorSite() != null) {
			IStatusLineManager status = getEditorSite().getActionBars().getStatusLineManager();
			if (error) {
				status.setErrorMessage(message);
			} else {
				status.setErrorMessage(null);
				status.setMessage(message);
			}
		}
		PdfView view = findPdfView();
		if (view != null) {
			view.showStatus(message, error);
		}
	}
}
