package com.ectex.plugin.preferences;

public final class PreferenceConstants {

	/**
	 * The LaTeX engine: pdflatex, xelatex, lualatex or any other command. (The key keeps its old name so that
	 * stored preferences of earlier versions still apply.)
	 */
	public static final String PDFLATEX_COMMAND = "pdflatexCommand";

	/** Arguments passed to the LaTeX engine before the file name (space separated). */
	public static final String PDFLATEX_ARGS = "pdflatexArgs";

	/**
	 * The bibliography tool run by "Run Bibliography Tool" and by "Build All": bibtex, biber or any other
	 * command. (The key keeps its old name so that stored preferences of earlier versions still apply.)
	 */
	public static final String BIBTEX_COMMAND = "bibtexCommand";

	/** Arguments passed to the bibliography tool before the base file name. */
	public static final String BIBTEX_ARGS = "bibtexArgs";

	/**
	 * The steps of "Build All": {@code BuildPlan.SEQUENCE_LATEX_TWICE} or
	 * {@code BuildPlan.SEQUENCE_WITH_BIBLIOGRAPHY}.
	 */
	public static final String BUILD_ALL_SEQUENCE = "buildAllSequence";

	/**
	 * Experimental: render the PDF Preview at the real pixel density of high-DPI screens, so text is sharp
	 * (uses more memory). On by default; falls back to normal rendering if memory runs out.
	 */
	public static final String SHARP_RENDERING = "sharpRendering";

	/** SyncTeX command line utility used for forward and inverse search. */
	public static final String SYNCTEX_COMMAND = "synctexCommand";

	private PreferenceConstants() {
	}
}
