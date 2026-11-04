package com.ectex.plugin.build;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Which external commands a build runs, in which order. No Eclipse dependencies, so it can be tested on its own.
 * <p>
 * The sequences are explicit: the user chooses the LaTeX engine, the bibliography tool (BibTeX or Biber) and
 * the sequence of "Build All" in the preferences; nothing is detected automatically.
 */
public final class BuildPlan {

	/** Preference value: LaTeX, LaTeX (documents without a bibliography). */
	public static final String SEQUENCE_LATEX_TWICE = "latex2";

	/** Preference value: LaTeX, bibliography tool, LaTeX, LaTeX (the default). */
	public static final String SEQUENCE_WITH_BIBLIOGRAPHY = "latexBibLatex2";

	/** The two kinds of tool EcTeX runs. */
	public enum Kind {
		LATEX("LaTeX"), BIBLIOGRAPHY("bibliography tool");

		private final String label;

		Kind(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	/** The commands from the preferences. Arguments are separated by spaces. */
	public record Settings(String latexCommand, String latexArguments, String bibliographyCommand,
			String bibliographyArguments) {
	}

	/** One command to run: the executable, its arguments and the file name (all in {@code command}). */
	public record Step(Kind kind, List<String> command) {
		/** The executable, e.g. "pdflatex" or "biber". */
		public String name() {
			return command.get(0);
		}
	}

	private BuildPlan() {
	}

	/**
	 * The tools "Build All" runs for the given preference value. A missing or unknown value gives the default,
	 * LaTeX, bibliography tool, LaTeX, LaTeX.
	 */
	public static List<Kind> sequence(String preference) {
		if (SEQUENCE_LATEX_TWICE.equals(preference)) {
			return List.of(Kind.LATEX, Kind.LATEX);
		}
		return List.of(Kind.LATEX, Kind.BIBLIOGRAPHY, Kind.LATEX, Kind.LATEX);
	}

	/**
	 * @param texFile the main file; LaTeX gets its name, the bibliography tool its name without ".tex"
	 *                (both bibtex and biber read the .aux / .bcf file of that base name)
	 */
	public static List<Step> steps(File texFile, Settings settings, List<Kind> kinds) {
		List<Step> steps = new ArrayList<>();
		for (Kind kind : kinds) {
			List<String> command = new ArrayList<>();
			String fileName = texFile.getName();
			if (kind == Kind.LATEX) {
				command.add(settings.latexCommand().trim());
				addArguments(command, settings.latexArguments());
			} else {
				command.add(settings.bibliographyCommand().trim());
				addArguments(command, settings.bibliographyArguments());
				int dot = fileName.lastIndexOf('.');
				fileName = dot > 0 ? fileName.substring(0, dot) : fileName;
			}
			command.add(fileName);
			steps.add(new Step(kind, List.copyOf(command)));
		}
		return steps;
	}

	/** "pdflatex, bibtex, pdflatex, pdflatex" - for messages. */
	public static String describe(List<Step> steps) {
		return steps.stream().map(Step::name).collect(Collectors.joining(", "));
	}

	private static void addArguments(List<String> command, String arguments) {
		String trimmed = arguments == null ? "" : arguments.trim();
		if (!trimmed.isEmpty()) {
			command.addAll(List.of(trimmed.split("\\s+")));
		}
	}
}
