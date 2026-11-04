package com.ectex.plugin.editors;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
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
import com.ectex.plugin.build.BuildPlan;
import com.ectex.plugin.preferences.PreferenceConstants;
import com.ectex.plugin.project.MainFile;
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

	/**
	 * The file pdflatex and bibtex run on: the main file of this editor's project if one is set (and
	 * exists), otherwise this editor's own file. Null if the input is not a local file.
	 */
	public File getMainFile() {
		IFile configured = configuredMainFile();
		if (configured != null && configured.exists() && configured.getLocation() != null) {
			return configured.getLocation().toFile();
		}
		return getTexFile();
	}

	/** The PDF pdflatex produces for the main file (same folder, same base name). */
	public File getPdfFile() {
		File main = getMainFile();
		return main == null ? null : new File(main.getParentFile(), baseName(main) + ".pdf");
	}

	private static String baseName(File tex) {
		String name = tex.getName();
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	// ---------------------------------------------------------------- main file

	/** The main file configured for this editor's project, or null. It may not exist (any more). */
	private IFile configuredMainFile() {
		return getEditorInput() instanceof IFileEditorInput input ? MainFile.get(input.getFile().getProject()) : null;
	}

	/** Like {@link #getMainFile()}, but reports a problem in the status line and returns null for it. */
	private File resolveMainFile() {
		IFile configured = configuredMainFile();
		if (configured != null && (!configured.exists() || configured.getLocation() == null)) {
			showStatus("The main file " + configured.getProjectRelativePath() + " of project "
					+ configured.getProject().getName() + " does not exist. Right-click a .tex file or the project and"
					+ " use EcTeX > Set as Main File or Clear Main File.", true);
			return null;
		}
		File main = getMainFile();
		if (main == null) {
			showStatus("EcTeX can only build documents stored in the local file system.", true);
		}
		return main;
	}

	/** The workspace project of this editor's file, or null for files outside the workspace. */
	public IProject getProject() {
		return getEditorInput() instanceof IFileEditorInput input ? input.getFile().getProject() : null;
	}

	/** Extra advice for a failed build of a file that has no \documentclass and no main file is set. */
	private String missingDocumentClassHint() {
		if (configuredMainFile() != null || getEditorInput() == null || getDocumentProvider() == null) {
			return "";
		}
		IDocument document = getDocumentProvider().getDocument(getEditorInput());
		if (document == null || document.get().contains("\\documentclass")) {
			return "";
		}
		return " This file has no \\documentclass: if it is part of a larger document, make the project a LaTeX"
				+ " project (right-click it > Configure > Convert to LaTeX Project), then right-click the main file"
				+ " and choose EcTeX > Set as Main File.";
	}

	/** Saves this editor and the other dirty editors of the same project, so a build sees all edits. */
	private boolean saveProjectEditors() {
		IWorkbenchPage page = getSite().getPage();
		IProject project = getEditorInput() instanceof IFileEditorInput input ? input.getFile().getProject() : null;
		for (IEditorPart dirty : page.getDirtyEditors()) {
			boolean related = dirty == this || (project != null && dirty.getEditorInput() instanceof IFileEditorInput other
					&& project.equals(other.getFile().getProject()));
			if (related && !page.saveEditor(dirty, false)) {
				return false; // save failed or was cancelled
			}
		}
		return true;
	}

	// ---------------------------------------------------------------- build commands

	/**
	 * Saves the project's edited files, runs one LaTeX pass on the main file (this editor's file unless the
	 * project has a main file) and refreshes the PDF view when it succeeds.
	 */
	public void buildPdf() {
		File main = resolveMainFile();
		if (main == null || isBusy() || !saveProjectEditors()) {
			return;
		}
		boolean viaMainFile = !main.equals(getTexFile());
		showStatus("Running LaTeX on " + main.getName() + (viaMainFile ? " (main file)" : "") + " ...", false);
		IWorkbenchPage page = getSite().getPage();
		buildJob = new BuildJob(main, List.of(BuildPlan.Kind.LATEX), success -> {
			if (success) {
				showStatus("LaTeX finished: " + main.getName(), false);
				PdfView view = PdfView.show(page);
				if (view != null) {
					view.setEditor(this, true);
				}
			} else {
				showStatus("LaTeX failed - see the \"EcTeX Build\" console." + missingDocumentClassHint(), true);
			}
		});
		buildJob.schedule();
	}

	/**
	 * Build All: runs the sequence chosen in the preferences (LaTeX, bibliography tool, LaTeX, LaTeX - or just
	 * LaTeX, LaTeX) on the main file, stops at the first error and refreshes the PDF view when everything succeeded.
	 */
	public void buildAll() {
		File main = resolveMainFile();
		if (main == null || isBusy() || !saveProjectEditors()) {
			return;
		}
		List<BuildPlan.Kind> kinds = BuildPlan.sequence(
				Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.BUILD_ALL_SEQUENCE));
		boolean viaMainFile = !main.equals(getTexFile());
		showStatus("Build All on " + main.getName() + (viaMainFile ? " (main file)" : "") + ": " + kinds.size()
				+ " steps ...", false);
		IWorkbenchPage page = getSite().getPage();
		buildJob = new BuildJob(main, kinds, success -> {
			if (success) {
				showStatus("Build All finished: " + main.getName(), false);
				PdfView view = PdfView.show(page);
				if (view != null) {
					view.setEditor(this, true);
				}
			} else {
				showStatus("Build All failed - see the \"EcTeX Build\" console." + missingDocumentClassHint(), true);
			}
		});
		buildJob.schedule();
	}

	/**
	 * Runs the bibliography tool chosen in the preferences (BibTeX or Biber) on the .aux file that the last
	 * LaTeX run wrote for the main file.
	 */
	public void runBibliography() {
		File main = resolveMainFile();
		if (main == null || isBusy()) {
			return;
		}
		File aux = new File(main.getParentFile(), baseName(main) + ".aux");
		if (!aux.isFile()) {
			showStatus("No " + aux.getName() + " yet - run LaTeX first (" + Commands.howTo(Commands.BUILD)
					+ ") so the bibliography tool has citations to resolve.", true);
			return;
		}
		showStatus("Running the bibliography tool on " + main.getName() + " ...", false);
		buildJob = new BuildJob(main, List.of(BuildPlan.Kind.BIBLIOGRAPHY), success -> {
			if (success) {
				showStatus("Bibliography tool finished - run LaTeX twice (" + Commands.howTo(Commands.BUILD)
						+ ") or Build All (" + Commands.howTo(Commands.BUILD_ALL) + ") to update the citations in the PDF.",
						false);
			} else {
				showStatus("Bibliography tool failed - see the \"EcTeX Build\" console.", true);
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
		File main = resolveMainFile();
		if (main == null) {
			return;
		}
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
				SyncTex.ForwardResult r = SyncTex.forward(main.getParentFile(), tex, pdf.getName(), line, 0);
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
		File main = resolveMainFile();
		File pdf = getPdfFile();
		if (main == null || pdf == null) {
			return;
		}
		Display display = getSite().getShell().getDisplay();
		Job job = Job.create("EcTeX inverse search", monitor -> {
			try {
				SyncTex.InverseResult r = SyncTex.inverse(main.getParentFile(), pdf.getName(), point.page(), point.x(),
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
