package com.ectex.plugin.wizards;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.wizard.Wizard;
import org.eclipse.ui.INewWizard;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.dialogs.WizardNewProjectCreationPage;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.wizards.newresource.BasicNewProjectResourceWizard;
import org.eclipse.ui.wizards.newresource.BasicNewResourceWizard;
import org.osgi.service.prefs.BackingStoreException;

import com.ectex.plugin.Activator;
import com.ectex.plugin.project.LatexNature;
import com.ectex.plugin.project.MainFile;

/**
 * File > New > Other > EcTeX > LaTeX Project: creates a project with the LaTeX nature, a starter main file
 * (which is set as the project's main file) and optionally a bibliography, then opens the LaTeX perspective.
 */
public class NewLatexProjectWizard extends Wizard implements INewWizard, IExecutableExtension {

	private static final String BIBLIOGRAPHY_FILE = "references.bib";

	private IWorkbench workbench;
	private IConfigurationElement configuration;
	private WizardNewProjectCreationPage namePage;
	private NewLatexProjectOptionsPage optionsPage;
	private IFile createdMainFile;

	public NewLatexProjectWizard() {
		setWindowTitle("New LaTeX Project");
		setNeedsProgressMonitor(true);
	}

	@Override
	public void init(IWorkbench workbench, IStructuredSelection selection) {
		this.workbench = workbench;
	}

	/** Remembers the wizard declaration, which names the perspective to switch to (finalPerspective). */
	@Override
	public void setInitializationData(IConfigurationElement config, String propertyName, Object data) {
		this.configuration = config;
	}

	@Override
	public void addPages() {
		namePage = new WizardNewProjectCreationPage("newLatexProjectName");
		namePage.setTitle("New LaTeX Project");
		namePage.setDescription("Create a LaTeX project with a main .tex file.");
		addPage(namePage);
		optionsPage = new NewLatexProjectOptionsPage("newLatexProjectOptions");
		addPage(optionsPage);
	}

	@Override
	public boolean performFinish() {
		IProject project = namePage.getProjectHandle();
		URI location = namePage.useDefaults() ? null : namePage.getLocationURI();
		String mainName = optionsPage.getMainFileName();
		boolean bibliography = optionsPage.isBibliographyWanted();
		try {
			getContainer().run(true, false, monitor -> {
				try {
					createProject(project, location, mainName, bibliography, monitor);
				} catch (CoreException | BackingStoreException e) {
					throw new InvocationTargetException(e);
				}
			});
		} catch (InvocationTargetException e) {
			Activator.log("Cannot create the LaTeX project " + project.getName(), e.getCause());
			ErrorDialog.openError(getShell(), "New LaTeX Project", "The project could not be created.",
					new org.eclipse.core.runtime.Status(org.eclipse.core.runtime.IStatus.ERROR, Activator.PLUGIN_ID,
							String.valueOf(e.getCause().getMessage()), e.getCause()));
			return false;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}

		BasicNewProjectResourceWizard.updatePerspective(configuration); // offers to switch to the LaTeX perspective
		IWorkbenchWindow window = workbench.getActiveWorkbenchWindow();
		BasicNewResourceWizard.selectAndReveal(createdMainFile, window);
		try {
			IDE.openEditor(window.getActivePage(), createdMainFile);
		} catch (PartInitException e) {
			Activator.log("Cannot open " + createdMainFile, e);
		}
		return true;
	}

	private void createProject(IProject project, URI location, String mainName, boolean bibliography,
			IProgressMonitor monitor) throws CoreException, BackingStoreException {
		IWorkspace workspace = ResourcesPlugin.getWorkspace();
		workspace.run(m -> {
			IProjectDescription description = workspace.newProjectDescription(project.getName());
			description.setLocationURI(location);
			project.create(description, m);
			project.open(m);
			LatexNature.add(project);
			createdMainFile = project.getFile(mainName);
			createdMainFile.create(stream(mainTemplate(project.getName(), bibliography)), true, m);
			if (bibliography) {
				project.getFile(BIBLIOGRAPHY_FILE).create(stream(bibliographyTemplate()), true, m);
			}
		}, monitor);
		MainFile.set(createdMainFile); // the starter file is the project's main file from the start
	}

	private static InputStream stream(String text) {
		return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
	}

	private static String mainTemplate(String title, boolean bibliography) {
		String citation = bibliography ? "\nAn example citation~\\cite{example}.\n" : "";
		String bibliographyCommands = bibliography ? "\n\\bibliographystyle{plain}\n\\bibliography{references}\n" : "";
		return "\\documentclass{article}\n"
				+ "\n"
				+ "\\title{" + title + "}\n"
				+ "\\author{}\n"
				+ "\\date{\\today}\n"
				+ "\n"
				+ "\\begin{document}\n"
				+ "\n"
				+ "\\maketitle\n"
				+ "\n"
				+ "\\section{Introduction}\n"
				+ "Write your text here.\n"
				+ citation
				+ bibliographyCommands
				+ "\n"
				+ "\\end{document}\n";
	}

	private static String bibliographyTemplate() {
		return "@misc{example,\n"
				+ "    author = {Author, Anne},\n"
				+ "    title  = {An Example Entry},\n"
				+ "    year   = {2026}\n"
				+ "}\n";
	}
}
