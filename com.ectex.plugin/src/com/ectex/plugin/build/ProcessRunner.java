package com.ectex.plugin.build;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Runs an external command, streaming its merged stdout/stderr line by line.
 */
public final class ProcessRunner {

	private ProcessRunner() {
	}

	/**
	 * @param dir       working directory
	 * @param command   executable followed by its arguments
	 * @param cancelled polled while the process runs; the process is killed once it returns true
	 * @param sink      receives each output line
	 * @return the exit code
	 * @throws IOException if the process cannot be started (e.g. executable not found)
	 */
	public static int run(File dir, List<String> command, BooleanSupplier cancelled, Consumer<String> sink)
			throws IOException, InterruptedException {
		ProcessBuilder pb = new ProcessBuilder(command);
		pb.directory(dir);
		pb.redirectErrorStream(true);
		Process process = pb.start();
		// TeX must never wait for console input.
		process.getOutputStream().close();

		Thread watchdog = new Thread(() -> {
			while (process.isAlive()) {
				if (cancelled.getAsBoolean()) {
					process.destroyForcibly();
					return;
				}
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					return;
				}
			}
		}, "EcTeX process watchdog");
		watchdog.setDaemon(true);
		watchdog.start();

		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				sink.accept(line);
			}
		}
		int exitCode = process.waitFor();
		watchdog.interrupt();
		return exitCode;
	}
}
