package com.ectex.plugin.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import com.ectex.plugin.Activator;

public class PreferenceInitializer extends AbstractPreferenceInitializer {

	@Override
	public void initializeDefaultPreferences() {
		IPreferenceStore store = Activator.getDefault().getPreferenceStore();
		store.setDefault(PreferenceConstants.PDFLATEX_COMMAND, "pdflatex");
		store.setDefault(PreferenceConstants.PDFLATEX_ARGS, "-interaction=nonstopmode -file-line-error -synctex=1");
		store.setDefault(PreferenceConstants.BIBTEX_COMMAND, "bibtex");
		store.setDefault(PreferenceConstants.BIBTEX_ARGS, "");
		store.setDefault(PreferenceConstants.SYNCTEX_COMMAND, "synctex");
	}
}
