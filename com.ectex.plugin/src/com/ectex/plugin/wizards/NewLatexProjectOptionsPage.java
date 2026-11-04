package com.ectex.plugin.wizards;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

/** Second page of the New LaTeX Project wizard: name of the main file and an optional bibliography. */
class NewLatexProjectOptionsPage extends WizardPage {

	private Text mainFileText;
	private Button bibliographyButton;

	NewLatexProjectOptionsPage(String pageName) {
		super(pageName);
		setTitle("New LaTeX Project");
		setDescription("Choose the main file of the new project.");
	}

	@Override
	public void createControl(Composite parent) {
		Composite composite = new Composite(parent, SWT.NONE);
		composite.setLayout(new GridLayout(2, false));

		new Label(composite, SWT.NONE).setText("Main file:");
		mainFileText = new Text(composite, SWT.BORDER);
		mainFileText.setText("main.tex");
		mainFileText.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		mainFileText.addModifyListener(e -> validate());

		bibliographyButton = new Button(composite, SWT.CHECK);
		bibliographyButton.setText("Also create a bibliography file (references.bib)");
		bibliographyButton.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false, 2, 1));

		setControl(composite);
		validate();
	}

	private void validate() {
		String name = mainFileText.getText().trim();
		String error = null;
		if (name.isEmpty()) {
			error = "Enter a name for the main file.";
		} else if (!name.toLowerCase().endsWith(".tex")) {
			error = "The main file must have the extension .tex.";
		} else {
			IStatus status = ResourcesPlugin.getWorkspace().validateName(name, IResource.FILE);
			if (!status.isOK()) {
				error = status.getMessage();
			}
		}
		setErrorMessage(error);
		setPageComplete(error == null);
	}

	String getMainFileName() {
		return mainFileText.getText().trim();
	}

	boolean isBibliographyWanted() {
		return bibliographyButton.getSelection();
	}
}
