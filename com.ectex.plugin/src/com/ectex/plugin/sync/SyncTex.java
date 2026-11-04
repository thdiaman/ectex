package com.ectex.plugin.sync;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.ectex.plugin.Activator;
import com.ectex.plugin.build.ProcessRunner;
import com.ectex.plugin.preferences.PreferenceConstants;

/**
 * Thin wrapper around the {@code synctex} command line utility that ships with
 * MiKTeX and TeX Live. All coordinates are PDF points measured from the top-left
 * corner of the page.
 */
public final class SyncTex {

	/** Location in the PDF for a source position. {@code page} is 1-based. */
	public record ForwardResult(int page, double x, double y, double width, double height) {
	}

	/** Location in the sources for a PDF position. {@code line} is 1-based. */
	public record InverseResult(File file, int line) {
	}

	private SyncTex() {
	}

	/**
	 * @return the PDF location, or null if SyncTeX has no entry for that line
	 */
	public static ForwardResult forward(File dir, File texFile, String pdfName, int line, int column)
			throws IOException {
		// MiKTeX records absolute input paths, TeX Live usually relative ones: try both.
		String[] candidates = { texFile.getAbsolutePath(), "./" + texFile.getName(), texFile.getName() };
		for (String candidate : candidates) {
			Map<String, String> result = run(dir, "view", "-i", line + ":" + column + ":" + candidate, "-o", pdfName);
			if (result != null && result.containsKey("Page")) {
				double h = number(result, "h", number(result, "x", 0));
				double v = number(result, "v", number(result, "y", 0));
				double w = number(result, "W", 0);
				double height = number(result, "H", 0);
				return new ForwardResult(Integer.parseInt(result.get("Page").trim()), h, v - height, w, height);
			}
		}
		return null;
	}

	/**
	 * @param page 1-based page number
	 * @return the source location, or null if nothing matches
	 */
	public static InverseResult inverse(File dir, String pdfName, int page, double x, double y) throws IOException {
		String query = String.format(Locale.ROOT, "%d:%.3f:%.3f:%s", page, x, y, pdfName);
		Map<String, String> result = run(dir, "edit", "-o", query);
		if (result == null || !result.containsKey("Input") || !result.containsKey("Line")) {
			return null;
		}
		File file = new File(result.get("Input").trim());
		if (!file.isAbsolute()) {
			file = new File(dir, file.getPath());
		}
		int line = Integer.parseInt(result.get("Line").trim());
		return new InverseResult(file, Math.max(line, 1));
	}

	/** Runs synctex and returns the key/value pairs of the first result block, or null if there is none. */
	private static Map<String, String> run(File dir, String... args) throws IOException {
		List<String> command = new ArrayList<>();
		command.add(Activator.getDefault().getPreferenceStore().getString(PreferenceConstants.SYNCTEX_COMMAND));
		command.addAll(List.of(args));

		StringBuilder raw = new StringBuilder();
		int exitCode;
		try {
			exitCode = ProcessRunner.run(dir, command, () -> false, line -> raw.append(line).append('\n'));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return null;
		} catch (IOException e) {
			throw new IOException("Cannot run synctex (" + e.getMessage() + "). Check Window > Preferences > EcTeX.", e);
		}

		Map<String, String> values = new HashMap<>();
		boolean inResult = false;
		for (String line : raw.toString().split("\\R")) {
			if (line.startsWith("SyncTeX result begin")) {
				inResult = true;
			} else if (line.startsWith("SyncTeX result end")) {
				break;
			} else if (inResult) {
				int colon = line.indexOf(':');
				if (colon > 0) {
					values.putIfAbsent(line.substring(0, colon), line.substring(colon + 1));
				}
			}
		}
		if (!inResult && exitCode != 0 && raw.length() > 0 && !raw.toString().contains("Warning")) {
			throw new IOException("synctex failed: " + raw.toString().trim());
		}
		return inResult ? values : null;
	}

	private static double number(Map<String, String> values, String key, double fallback) {
		String text = values.get(key);
		if (text == null) {
			return fallback;
		}
		try {
			return Double.parseDouble(text.trim());
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
