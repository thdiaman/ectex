package com.ectex.plugin.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import com.ectex.plugin.Activator;
import com.ectex.plugin.build.BuildPlan;

public class PreferenceInitializer extends AbstractPreferenceInitializer {

	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		store.setDefault(PreferenceConstants.PDFLATEX_COMMAND, "pdflatex");
		store.setDefault(PreferenceConstants.PDFLATEX_ARGS, "-interaction=nonstopmode -file-line-error -synctex=1");
		store.setDefault(PreferenceConstants.BIBTEX_COMMAND, "bibtex");
		store.setDefault(PreferenceConstants.BIBTEX_ARGS, "");
		store.setDefault(PreferenceConstants.BUILD_ALL_SEQUENCE, BuildPlan.SEQUENCE_WITH_BIBLIOGRAPHY);
		store.setDefault(PreferenceConstants.SYNCTEX_COMMAND, "synctex");
		store.setDefault(PreferenceConstants.SHARP_RENDERING, true);
	}
}
