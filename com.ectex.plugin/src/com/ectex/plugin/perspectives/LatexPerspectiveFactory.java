package com.ectex.plugin.perspectives;

import org.eclipse.ui.IFolderLayout;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IPerspectiveFactory;
import org.eclipse.ui.console.IConsoleConstants;

import com.ectex.plugin.views.PdfView;

/**
 * The LaTeX perspective: Project Explorer on the left, the editors in the middle, Problems and the
 * EcTeX Build console below. The PDF Preview opens to the right of the editors when a .tex file is opened
 * (its position is declared for all perspectives in plugin.xml).
 */
public class LatexPerspectiveFactory implements IPerspectiveFactory {

	public static final String ID = "com.ectex.plugin.perspectives.latex";

	@Override
	public void createInitialLayout(IPageLayout layout) {
		String editorArea = layout.getEditorArea();

		IFolderLayout left = layout.createFolder("com.ectex.plugin.perspective.left", IPageLayout.LEFT, 0.2f, editorArea);
		left.addView(IPageLayout.ID_PROJECT_EXPLORER);

		IFolderLayout bottom = layout.createFolder("com.ectex.plugin.perspective.bottom", IPageLayout.BOTTOM, 0.75f,
				editorArea);
		bottom.addView(IPageLayout.ID_PROBLEM_VIEW);
		bottom.addView(IConsoleConstants.ID_CONSOLE_VIEW);

		layout.addShowViewShortcut(PdfView.ID);
		layout.addShowViewShortcut(IPageLayout.ID_PROJECT_EXPLORER);
		layout.addShowViewShortcut(IPageLayout.ID_PROBLEM_VIEW);
		layout.addShowViewShortcut(IConsoleConstants.ID_CONSOLE_VIEW);

		layout.addNewWizardShortcut("com.ectex.plugin.wizards.newProject");
		layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.file");
		layout.addNewWizardShortcut("org.eclipse.ui.wizards.new.folder");
	}
}
