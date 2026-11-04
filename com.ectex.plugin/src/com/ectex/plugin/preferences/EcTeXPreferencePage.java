package com.ectex.plugin.preferences;

import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Link;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;

import com.ectex.plugin.Activator;
import com.ectex.plugin.build.BuildPlan;

public class EcTeXPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	private static final String KEYS_PAGE_ID = "org.eclipse.ui.preferencePages.Keys";

	public EcTeXPreferencePage() {
		super(GRID);
	}

	@Override
	public void init(IWorkbench workbench) {
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription("Tools used to build LaTeX documents. Pick one from the list or type your own command; use a "
				+ "full path if it is not on the PATH of the Eclipse process. Choose Biber instead of BibTeX for "
				+ "documents that use biblatex with backend=biber.");
	}

	@Override
	protected void createFieldEditors() {
		Composite parent = getFieldEditorParent();
		addField(new PresetCommandFieldEditor(PreferenceConstants.PDFLATEX_COMMAND, "LaTeX engine:",
				new String[] { "pdflatex", "xelatex", "lualatex" }, parent));
		addField(new StringFieldEditor(PreferenceConstants.PDFLATEX_ARGS, "LaTeX arguments:", parent));
		addField(new PresetCommandFieldEditor(PreferenceConstants.BIBTEX_COMMAND, "Bibliography tool:",
				new String[] { "bibtex", "biber" }, parent));
		addField(new StringFieldEditor(PreferenceConstants.BIBTEX_ARGS, "Bibliography arguments:", parent));
		addField(new ComboFieldEditor(PreferenceConstants.BUILD_ALL_SEQUENCE, "Build All runs:",
				new String[][] { { "LaTeX, LaTeX (document without a bibliography)", BuildPlan.SEQUENCE_LATEX_TWICE },
						{ "LaTeX, bibliography tool, LaTeX, LaTeX", BuildPlan.SEQUENCE_WITH_BIBLIOGRAPHY } },
				parent));
		addField(new StringFieldEditor(PreferenceConstants.SYNCTEX_COMMAND, "SyncTeX command:", parent));
	}

	@Override
	protected Control createContents(Composite parent) {
		Composite outer = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		layout.marginWidth = 0;
		layout.marginHeight = 0;
		outer.setLayout(layout);
		outer.setLayoutData(new GridData(GridData.FILL_BOTH));

		Control fields = super.createContents(outer);
		fields.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));

		Link keys = new Link(outer, SWT.NONE);
		keys.setText("The keyboard shortcuts (build, bibliography tool, build all, synchronize, bold, italic, ...) can be "
				+ "changed on the <a>Keys</a> preference page: filter by \"EcTeX\".");
		GridData data = new GridData(GridData.FILL_HORIZONTAL);
		data.widthHint = 400;
		data.verticalIndent = 10;
		keys.setLayoutData(data);
		keys.addListener(SWT.Selection, e -> {
			if (getContainer() instanceof IWorkbenchPreferenceContainer container) {
				container.openPage(KEYS_PAGE_ID, null);
			}
		});
		return outer;
	}
}
