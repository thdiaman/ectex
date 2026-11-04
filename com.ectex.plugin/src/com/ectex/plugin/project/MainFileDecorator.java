package com.ectex.plugin.project;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.Adapters;
import org.eclipse.jface.viewers.IDecoration;
import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.ILightweightLabelDecorator;

/** Appends " [main file]" to the main file of a project in the Project Explorer and similar views. */
public class MainFileDecorator implements ILightweightLabelDecorator {

	/** Id of the decorator declared in plugin.xml. */
	public static final String ID = "com.ectex.plugin.decorators.mainFile";

	@Override
	public void decorate(Object element, IDecoration decoration) {
		IFile file = Adapters.adapt(element, IFile.class);
		if (file == null || !"tex".equals(file.getFileExtension())) {
			return;
		}
		IFile main = MainFile.get(file.getProject());
		if (main != null && main.getFullPath().equals(file.getFullPath())) {
			decoration.addSuffix(" [main file]");
		}
	}

	@Override
	public boolean isLabelProperty(Object element, String property) {
		return false;
	}

	@Override
	public void addListener(ILabelProviderListener listener) {
		// refreshes are requested through IDecoratorManager.update(ID)
	}

	@Override
	public void removeListener(ILabelProviderListener listener) {
	}

	@Override
	public void dispose() {
	}
}
