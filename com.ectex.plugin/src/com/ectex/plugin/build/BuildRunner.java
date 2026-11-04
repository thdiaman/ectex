package com.ectex.plugin.build;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Runs the steps of a {@link BuildPlan} one after the other and stops at the first failure.
 * No Eclipse dependencies, so it can be tested on its own.
 */
public final class BuildRunner {

	private static final String BIBLIOGRAPHY_HINT = "Hint: check Window > Preferences > EcTeX. \"Bibliography tool\" "
			+ "must match the document (BibTeX for \\bibliography, Biber for biblatex with backend=biber) and the "
			+ "document needs a bibliography. For documents without one, set \"Build All runs\" to \"LaTeX, LaTeX\".";

	/**
	 * @param success         every step exited with code 0
	 * @param cancelled       the build was cancelled
	 * @param failedStep      1-based number of the step that failed or was cancelled, 0 if none
	 * @param exitCode        exit code of that step
	 * @param lastLatexOutput output of the last LaTeX step that ran (source of the error markers), or null
	 */
	public record Result(boolean success, boolean cancelled, int failedStep, int exitCode, List<String> lastLatexOutput) {
	}

	private BuildRunner() {
	}

	/**
	 * @param dir       working directory (the folder of the main file)
	 * @param cancelled polled while a step runs; the step is killed once it returns true
	 * @param sink      receives the console text: headers, the command lines, the output of the tools and a summary
	 * @throws IOException if a tool cannot be started (e.g. not found); the message names the tool
	 */
	public static Result run(File dir, List<BuildPlan.Step> steps, BooleanSupplier cancelled, Consumer<String> sink)
			throws IOException, InterruptedException {
		List<String> lastLatexOutput = null;
		boolean several = steps.size() > 1;
		for (int i = 0; i < steps.size(); i++) {
			BuildPlan.Step step = steps.get(i);
			int number = i + 1;
			if (cancelled.getAsBoolean()) {
				sink.accept("=== Cancelled before step " + number + " ===");
				return new Result(false, true, number, 0, lastLatexOutput);
			}
			if (several) {
				sink.accept((i > 0 ? "\n" : "") + "=== Step " + number + " of " + steps.size() + ": "
						+ step.kind().label() + " (" + step.name() + ") ===");
			}
			sink.accept("> " + String.join(" ", step.command()) + "    [" + dir + "]");

			List<String> output = new ArrayList<>();
			int exitCode;
			try {
				exitCode = ProcessRunner.run(dir, step.command(), cancelled, line -> {
					output.add(line);
					sink.accept(line);
				});
			} catch (IOException e) {
				throw new IOException("Could not run " + step.name() + " (" + step.kind().label() + "): " + e.getMessage(),
						e);
			}
			if (step.kind() == BuildPlan.Kind.LATEX) {
				lastLatexOutput = output;
			}
			if (cancelled.getAsBoolean()) {
				sink.accept("\n=== Cancelled in step " + number + " (" + step.name() + ") ===");
				return new Result(false, true, number, exitCode, lastLatexOutput);
			}
			if (exitCode != 0) {
				sink.accept("\n=== " + step.name() + " FAILED (exit code " + exitCode + ")"
						+ (several ? ", stopped in step " + number + " of " + steps.size() : "") + " ===");
				if (step.kind() == BuildPlan.Kind.BIBLIOGRAPHY) {
					sink.accept(BIBLIOGRAPHY_HINT);
				}
				return new Result(false, false, number, exitCode, lastLatexOutput);
			}
		}
		sink.accept(several ? "\n=== Build All succeeded (" + BuildPlan.describe(steps) + ") ==="
				: "\n=== " + steps.get(0).name() + " succeeded ===");
		return new Result(true, false, 0, 0, lastLatexOutput);
	}
}
