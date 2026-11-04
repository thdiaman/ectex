package com.ectex.plugin.preferences;

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

public class EcTeXPreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

	private static final String KEYS_PAGE_ID = "org.eclipse.ui.preferencePages.Keys";

	public EcTeXPreferencePage() {
		super(GRID);
	}

	@Override
	public void init(IWorkbench workbench) {
		setPreferenceStore(Activator.getDefault().getPreferenceStore());
		setDescription("Commands used by the EcTeX LaTeX editor. Use a full path if the command is not on the PATH "
				+ "of the Eclipse process.");
	}

	@Override
	protected void createFieldEditors() {
		addField(new StringFieldEditor(PreferenceConstants.PDFLATEX_COMMAND, "pdflatex command:", getFieldEditorParent()));
		addField(new StringFieldEditor(PreferenceConstants.PDFLATEX_ARGS, "pdflatex arguments:", getFieldEditorParent()));
		addField(new StringFieldEditor(PreferenceConstants.BIBTEX_COMMAND, "bibtex command:", getFieldEditorParent()));
		addField(new StringFieldEditor(PreferenceConstants.BIBTEX_ARGS, "bibtex arguments:", getFieldEditorParent()));
		addField(new StringFieldEditor(PreferenceConstants.SYNCTEX_COMMAND, "synctex command:", getFieldEditorParent()));
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
		keys.setText("The keyboard shortcuts (build, bibtex, synchronize, bold, italic, ...) can be changed on the "
				+ "<a>Keys</a> preference page: filter by \"EcTeX\".");
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
