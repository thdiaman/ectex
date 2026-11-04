package com.ectex.plugin.preferences;

public final class PreferenceConstants {

	/** Executable used to compile documents. */
	public static final String PDFLATEX_COMMAND = "pdflatexCommand";

	/** Extra arguments passed to pdflatex before the file name (space separated). */
	public static final String PDFLATEX_ARGS = "pdflatexArgs";

	/** Executable run by the "Run BibTeX" command (use "biber" for biblatex projects). */
	public static final String BIBTEX_COMMAND = "bibtexCommand";

	/** Extra arguments passed to the bibtex command before the base file name. */
	public static final String BIBTEX_ARGS = "bibtexArgs";

	/** SyncTeX command line utility used for forward and inverse search. */
	public static final String SYNCTEX_COMMAND = "synctexCommand";

	private PreferenceConstants() {
	}
}
