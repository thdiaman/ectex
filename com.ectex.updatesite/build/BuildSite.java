import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;

/**
 * Builds the EcTeX p2 update site (no PDE or Maven needed, only a JDK 17+ and an Eclipse).
 * Start it through build-site.bat (Windows) or build-site.sh (Linux/macOS), or directly:
 * <pre>java -Dectex.build.dir=. BuildSite.java --eclipse-home /path/to/eclipse [--keep-old]</pre>
 *
 * <ol>
 * <li>compiles com.ectex.plugin against the plug-ins of the given Eclipse (Java 17 bytecode)
 * <li>packages the plug-in JAR and the com.ectex.feature feature, replacing ".qualifier" by a timestamp
 * <li>publishes both into com.ectex.updatesite with the p2 publisher application of that Eclipse
 * <li>adds the category from category.xml
 * <li>writes dist/ectex-updatesite-&lt;version&gt;.zip for installing from an archive
 * </ol>
 * Every build replaces the previous one, so the repository (and dist/) always holds a single version.
 * Pass --keep-old to add the new version next to the older ones instead.
 */
public class BuildSite {

	private static final String USAGE = String.join("\n",
			"Usage: build-site [--eclipse-home DIR] [--qualifier TEXT] [--keep-old]",
			"",
			"  --eclipse-home DIR  Eclipse that contains the p2 publisher tools (an Eclipse SDK / PDE install",
			"                      does). Default: the ECLIPSE_HOME environment variable.",
			"  --qualifier TEXT    replaces \".qualifier\" in the versions (default: timestamp yyyyMMddHHmm)",
			"  --keep-old          keep the versions already in the repository (and their zips in dist/).",
			"                      Default: the new build replaces them, so only one version remains.");

	private static class BuildException extends RuntimeException {
		BuildException(String message) {
			super(message);
		}
	}

	public static void main(String[] args) {
		try {
			new BuildSite().run(args);
		} catch (BuildException e) {
			System.err.println("ERROR: " + e.getMessage());
			System.exit(1);
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		}
	}

	private void run(String[] args) throws Exception {
		String eclipseHomeArg = System.getenv("ECLIPSE_HOME");
		String qualifier = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
		boolean keepOld = false;
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
			case "--eclipse-home" -> eclipseHomeArg = value(args, ++i);
			case "--qualifier" -> qualifier = value(args, ++i);
			case "--keep-old" -> keepOld = true;
			case "-h", "--help" -> {
				System.out.println(USAGE);
				return;
			}
			default -> throw new BuildException("Unknown option: " + args[i] + "\n\n" + USAGE);
			}
		}
		if (eclipseHomeArg == null || eclipseHomeArg.isBlank()) {
			throw new BuildException("Eclipse location missing: pass --eclipse-home or set ECLIPSE_HOME.\n\n" + USAGE);
		}

		Path buildDir = Path.of(System.getProperty("ectex.build.dir", ".")).toAbsolutePath().normalize();
		if (!Files.exists(buildDir.resolve("BuildSite.java"))) {
			throw new BuildException("Run this through build-site.bat / build-site.sh (cannot locate " + buildDir + ").");
		}
		Path site = buildDir.getParent();
		Path repoRoot = site.getParent();
		Path plugin = repoRoot.resolve("com.ectex.plugin");
		Path feature = repoRoot.resolve("com.ectex.feature");

		Path eclipseHome = Path.of(eclipseHomeArg).toAbsolutePath();
		Path plugins = firstExisting(eclipseHome.resolve("plugins"), eclipseHome.resolve("Contents/Eclipse/plugins"));
		Path launcher = firstExisting(eclipseHome.resolve("eclipsec.exe"), eclipseHome.resolve("eclipse.exe"),
				eclipseHome.resolve("eclipse"), eclipseHome.resolve("Contents/MacOS/eclipse"));
		if (plugins == null || launcher == null) {
			throw new BuildException("'" + eclipseHome + "' does not look like an Eclipse installation.");
		}
		if (!Files.isRegularFile(plugin.resolve("META-INF/MANIFEST.MF"))) {
			throw new BuildException("Plug-in project not found: " + plugin);
		}
		if (!Files.isRegularFile(feature.resolve("feature.xml"))) {
			throw new BuildException("Feature project not found: " + feature);
		}

		// ---- version ------------------------------------------------------------------------
		String manifestText = read(plugin.resolve("META-INF/MANIFEST.MF"));
		Matcher m = Pattern.compile("(?m)^Bundle-Version:\\s*(\\d+\\.\\d+\\.\\d+)\\.qualifier\\s*$").matcher(manifestText);
		if (!m.find()) {
			throw new BuildException("Bundle-Version in MANIFEST.MF must look like 1.0.0.qualifier");
		}
		String baseVersion = m.group(1);
		String version = baseVersion + "." + qualifier;
		String featureText = read(feature.resolve("feature.xml"));
		if (!featureText.contains("version=\"" + baseVersion + ".qualifier\"")) {
			throw new BuildException("feature.xml version must be " + baseVersion + ".qualifier (same as the plug-in)");
		}
		System.out.println("Building EcTeX " + version);

		Path stage = Files.createTempDirectory("ectex-build-");
		try {
			Path classes = Files.createDirectories(stage.resolve("plugin"));
			Path sourceRepo = Files.createDirectories(stage.resolve("source"));
			Files.createDirectories(sourceRepo.resolve("plugins"));
			Path featureDir = Files.createDirectories(sourceRepo.resolve("features/com.ectex.feature_" + version));

			// ---- compile ---------------------------------------------------------------------
			step(1, "Compiling plug-in");
			compile(plugin, plugins, classes);

			// ---- package plug-in JAR -----------------------------------------------------------
			step(2, "Packaging plug-in and feature");
			Files.copy(plugin.resolve("plugin.xml"), classes.resolve("plugin.xml"));
			copyTree(plugin.resolve("lib"), classes.resolve("lib"));
			// The manifest stays outside the class folder so `jar` writes it first.
			Path manifestFile = stage.resolve("MANIFEST.MF");
			String stamped = manifestText.replaceAll("(?m)^(Bundle-Version:\\s*\\d+\\.\\d+\\.\\d+)\\.qualifier", "$1." + qualifier);
			Files.writeString(manifestFile, stamped.replace("\r\n", "\n").stripTrailing().replace("\n", "\r\n") + "\r\n",
					StandardCharsets.US_ASCII);
			Path pluginJar = sourceRepo.resolve("plugins/com.ectex.plugin_" + version + ".jar");
			jar("--create", "--file", pluginJar.toString(), "--manifest", manifestFile.toString(), "-C",
					classes.toString(), ".");

			// ---- feature -----------------------------------------------------------------------
			Files.writeString(featureDir.resolve("feature.xml"), featureText.replace(".qualifier\"", "." + qualifier + "\""),
					StandardCharsets.UTF_8);

			// ---- publish -----------------------------------------------------------------------
			if (!keepOld) {
				System.out.println("      replacing the previous repository content (use --keep-old to keep it)");
				for (String name : List.of("features", "plugins", "content.jar", "content.xml", "artifacts.jar",
						"artifacts.xml", "p2.index")) {
					deleteTree(site.resolve(name));
				}
			}
			boolean append = Files.exists(site.resolve("content.jar")) || Files.exists(site.resolve("content.xml"));
			String repoUri = site.toUri().toString();
			List<String> eclipseArgs = List.of("-nosplash", "-consoleLog", "-configuration",
					stage.resolve("configuration").toString(), "-data", stage.resolve("workspace").toString());

			step(3, "Publishing feature and plug-in to the p2 repository");
			List<String> publish = new ArrayList<>(List.of("-application",
					"org.eclipse.equinox.p2.publisher.FeaturesAndBundlesPublisher", "-metadataRepository", repoUri,
					"-artifactRepository", repoUri, "-source", sourceRepo.toString(), "-compress", "-publishArtifacts"));
			if (append) {
				publish.add("-append");
			}
			runEclipse(launcher, eclipseArgs, publish);

			step(4, "Adding the category");
			runEclipse(launcher, eclipseArgs,
					List.of("-application", "org.eclipse.equinox.p2.publisher.CategoryPublisher", "-metadataRepository",
							repoUri, "-categoryDefinition", site.resolve("category.xml").toUri().toString(),
							"-categoryQualifier", "com.ectex", "-compress"));

			// ---- zip for "Add... > Archive..." ---------------------------------------------------
			step(5, "Creating the archive");
			Path dist = Files.createDirectories(site.resolve("dist"));
			Path zip = dist.resolve("ectex-updatesite-" + version + ".zip");
			if (!keepOld) {
				try (Stream<Path> zips = Files.list(dist)) {
					for (Path old : (Iterable<Path>) zips.filter(p -> p.getFileName().toString().matches("ectex-updatesite-.*\\.zip"))::iterator) {
						Files.delete(old);
					}
				}
			}
			Files.deleteIfExists(zip);
			List<String> jarArgs = new ArrayList<>(List.of("--create", "--no-manifest", "--file", zip.toString()));
			for (String item : List.of("features", "plugins", "content.jar", "artifacts.jar")) {
				if (Files.exists(site.resolve(item))) {
					jarArgs.addAll(List.of("-C", site.toString(), item));
				}
			}
			jar(jarArgs.toArray(new String[0]));

			System.out.println();
			System.out.println("Done. Update site: " + site);
			System.out.println("  p2 repository : features/, plugins/, content.jar, artifacts.jar");
			System.out.println("  archive       : " + zip);
		} finally {
			deleteTree(stage);
		}
	}

	// ---------------------------------------------------------------------------- steps

	private static void compile(Path plugin, Path eclipsePlugins, Path out) throws IOException {
		JavaCompiler javac = javax.tools.ToolProvider.getSystemJavaCompiler();
		if (javac == null) {
			throw new BuildException("No Java compiler found - run this with a JDK 17+, not a JRE.");
		}
		List<Path> classpath = new ArrayList<>();
		for (Path dir : List.of(eclipsePlugins, plugin.resolve("lib"))) {
			try (Stream<Path> jars = Files.list(dir)) {
				jars.filter(p -> p.getFileName().toString().endsWith(".jar")).forEach(classpath::add);
			}
		}
		List<Path> sources;
		try (Stream<Path> walk = Files.walk(plugin.resolve("src"))) {
			sources = walk.filter(p -> p.toString().endsWith(".java")).toList();
		}
		List<String> options = new ArrayList<>(List.of("--release", "17", "-encoding", "UTF-8", "-nowarn", "-Xlint:-path",
				"-cp", String.join(java.io.File.pathSeparator, classpath.stream().map(Path::toString).toList()), "-d",
				out.toString()));
		try (StandardJavaFileManager files = javac.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
			boolean ok = javac.getTask(null, files, null, options, null, files.getJavaFileObjectsFromPaths(sources)).call();
			if (!ok) {
				throw new BuildException("Compilation failed.");
			}
		}
	}

	private static void jar(String... args) {
		ToolProvider tool = ToolProvider.findFirst("jar")
				.orElseThrow(() -> new BuildException("The 'jar' tool is not available - run this with a JDK."));
		int exit = tool.run(System.out, System.err, args);
		if (exit != 0) {
			throw new BuildException("jar failed with exit code " + exit);
		}
	}

	private static void runEclipse(Path launcher, List<String> common, List<String> application)
			throws IOException, InterruptedException {
		List<String> command = new ArrayList<>();
		command.add(launcher.toString());
		command.addAll(common);
		command.addAll(application);
		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		process.getOutputStream().close();
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				System.out.println("    " + line);
			}
		}
		int exit = process.waitFor();
		if (exit != 0) {
			throw new BuildException(launcher.getFileName() + " failed with exit code " + exit);
		}
	}

	// ---------------------------------------------------------------------------- helpers

	private static String value(String[] args, int index) {
		if (index >= args.length) {
			throw new BuildException("Missing value for " + args[index - 1]);
		}
		return args[index];
	}

	private static void step(int number, String text) {
		System.out.println("[" + number + "/5] " + text);
	}

	private static Path firstExisting(Path... candidates) {
		for (Path candidate : candidates) {
			if (Files.exists(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private static String read(Path file) throws IOException {
		return Files.readString(file, StandardCharsets.UTF_8);
	}

	private static void copyTree(Path from, Path to) throws IOException {
		try (Stream<Path> walk = Files.walk(from)) {
			for (Path source : (Iterable<Path>) walk::iterator) {
				Path target = to.resolve(from.relativize(source).toString());
				if (Files.isDirectory(source)) {
					Files.createDirectories(target);
				} else {
					Files.copy(source, target);
				}
			}
		}
	}

	private static void deleteTree(Path root) throws IOException {
		if (!Files.exists(root)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(root)) {
			for (Path path : (Iterable<Path>) walk.sorted(Comparator.reverseOrder())::iterator) {
				Files.deleteIfExists(path);
			}
		}
	}
}
