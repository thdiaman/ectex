package com.ectex.plugin.project;

import org.eclipse.core.expressions.PropertyTester;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;

/**
 * Properties used by the visibility conditions of the "EcTeX" context menu (see plugin.xml):
 * <ul>
 * <li>{@code com.ectex.plugin.isMainFile}: the resource is the main file of its project
 * <li>{@code com.ectex.plugin.hasMainFile}: the project of the resource has a main file
 * </ul>
 */
public class MainFileTester extends PropertyTester {

	@Override
	public boolean test(Object receiver, String property, Object[] args, Object expectedValue) {
		if (!(receiver instanceof IResource resource)) {
			return false;
		}
		IProject project = resource.getProject();
		IFile main = MainFile.get(project);
		boolean actual = switch (property) {
		case "isMainFile" -> main != null && resource.getType() == IResource.FILE
				&& resource.getFullPath().equals(main.getFullPath());
		case "hasMainFile" -> main != null;
		default -> false;
		};
		boolean expected = expectedValue == null || Boolean.parseBoolean(expectedValue.toString());
		return actual == expected;
	}
}
