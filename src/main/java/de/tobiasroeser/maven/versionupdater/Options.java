/**
 * 
 */
package de.tobiasroeser.maven.versionupdater;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import de.tobiasroeser.maven.shared.Option;
import de.tobiasroeser.maven.versionupdater.VersionUpdater.Config;

public class Options {

	static final Option HELP = new Option("help", "h", "This help");

	static final Option DIR = new Option(
			"directory",
			"d",
			"Search maven project in directory PAR. If not given at least once, the current directory will be searched.",
			"PAR");

	static final Option DRYRUN = new Option("dryrun", null,
			"Do not modify any file (e.g. pom.xml)");

	static final Option PERSIST_ARTIFACTS = new Option("persist-artifact-list",
			null, "Write a list of found local artifacts (supports --dryrun)",
			"PAR");
	static final Option CHECK_ARTIFACT_LIST = new Option(
			"check-artifact-list",
			null,
			"Compare a list of artifacts PAR with the real existing artifacts locally found.",
			"PAR");
	static final Option TODO_APPLY_VERSIONS = new Option(
			"apply-version",
			null,
			"Apply the artifact versions read from artifact list PAR to local projects.",
			"PAR");

	static final Option UPDATE_ARITFACT_VERSION = new Option(
			"update-artifact-version", null,
			"Update the version of the matching artifact to artifact PAR (supports "
					+ DRYRUN + ")", "PAR");

	static final Option SET_DEP_VER = new Option(
			"set-dep-version",
			null,
			"Updates the versions of all matching dependencies to dependencies PAR (supports "
					+ DRYRUN + ")", "PAR");

	static final Option UPDATE_ARTIFACT_AND_DEP_VERSION = new Option(
			"update-artifact-and-dep-version",
			null,
			"Upadte the artifact and all dependencies to that artifact to version PAR (same as "
					+ UPDATE_ARITFACT_VERSION
					+ " and "
					+ SET_DEP_VER
					+ " used together)", "PAR");

	static final Option LIST_ARTIFACTS = new Option("list-local-artifacts",
			null, "List all found artifacts");

	static final Option LIST_DEPS = new Option("list-deps-and-dependants",
			null, "List all found dependencies and their dependants");
	static final Option DETECT_MISMATCH = new Option(
			"detect-local-version-mismatch",
			null,
			"Detect project that depedend on other local project but with wrong version number");

	static final Option FIND_ARTIFACT = new Option("find-artifact", null,
			"Find the artifact named PAR", "PAR");
	static final Option FIND_DEP = new Option("find-dependency", null,
			"Find the dependency named PAR", "PAR");
	static final Option SEARCH_ARTIFACT = new Option("search-artifacts", null,
			"Search for artifact with pattern PAR", "PAR");
	static final Option SEARCH_DEPS = new Option("search-dependencies", null,
			"Search for dependencies with pattern PAR", "PAR");
	static final Option ALLIGN = new Option(
			"align-local-dep-version",
			null,
			"Sync version of dependants to local project PAR (supports --dryrun)",
			"PAR");
	static final Option SCAN_SYS_DEPS = new Option("scan-system-deps", null,
			"Show all system dependencies");
	static final Option SCAN_LOCAL_DEPS = new Option("scan-local-deps", null,
			"Show all dependencies that are also available as a project");
	static final Option SCAN_LOCAL_SYS_DEPS = new Option(
			"scan-local-system-deps", null,
			"Show all system dependencies that are also available as a project");
	static final Option SCAN_LOCAL_NON_SYS_DEPS = new Option(
			"scan-local-non-system-deps", null,
			"Show all non-system dependencies that are also available as a project");
	static final Option REPLACE_DEP = new Option("replace-dependency", null,
			"Replace dependency PAR1 by dependency PAR2", "PAR1", "PAR2");
	static final Option TODO_MAKE_OPTIONAL_COMPILE_DEPS = new Option(
			"TODO-make-optional-compile-but-non-optional-runtime-deps-for",
			null, "Reorganize dependencies for project PAR", "PAR");

	static final Option EXTRACT_DEPS = new Option(
			"extract-project-deps",
			null,
			"Extract the project dependencies of the given project PAR1 and write them tofile PAR2",
			"PAR1", "PAR2");

	static final Option APPLY_DEPS = new Option("apply-project-deps", null,
			"Update the project PAR1 with the dependencies from file PAR2",
			"PAR1", "PAR2");

	static final Option GENERATE_EXCLUDES = new Option(
			"generate-excludes",
			null,
			"TODO-Generate dependency excludes in project PAR1 for dependency PAR2",
			"PAR1", "PAR2");

	static final Option GENERATE_EXCLUDES_ALL_DEPS = new Option(
			"TODO-generate-all-excludes", null,
			"Generate dependency excludes in project PAR for all dependencies",
			"PAR");

	public static final int EXIT_HELP = -1;
	public static final int EXIT_OK = 0;

	static List<Option> allOptions() {

		LinkedList<Option> options = new LinkedList<Option>();
		try {
			List<Option> annotatedOptions = Option
					.scanCmdOpions(VersionUpdater.Config.class);
			if (annotatedOptions != null && annotatedOptions.size() > 0) {
				options.addAll(options);
			}
			for (Field field : Options.class.getDeclaredFields()) {
				if (field.getType().equals(Option.class)) {
					options.add((Option) field.get(null));
				}
			}
		} catch (IllegalArgumentException e) {
			throw new Error("Could not retrieve all options", e);
		} catch (IllegalAccessException e) {
			throw new Error("Could not retrieve all options", e);
		}

		return options;
	}

	public static int parseCmdline(Config config, List<String> args) {
		ArrayList<String> params = new ArrayList<String>(args);
		int lastSize = params.size();
		try {
			while (lastSize > 0) {
				int index = -1;

				index = Options.HELP.scanPosition(params);
				if (index != -1) {
					params.remove(index);

					String formatOptions = Option.formatOptions(Options
							.allOptions(), null, true);
					System.out.println(formatOptions);
					return EXIT_HELP;
				}

				index = Options.DIR.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.dirs.add(params.get(index));
					params.remove(index);
				}

				index = Options.DRYRUN.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.dryrun = true;
				}

				index = Options.LIST_ARTIFACTS.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.listArtifacts = true;
				}

				index = Options.LIST_DEPS.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.listDepsAndDependants = true;
				}

				index = Options.DETECT_MISMATCH.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.detectLocalVersionMismatch = true;
				}

				index = Options.FIND_ARTIFACT.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.artifactsToFindExact.put(params.get(index), true);
					params.remove(index);
				}

				index = Options.FIND_DEP.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.dependenciesToFindExact.put(params.get(index), true);
					params.remove(index);
				}

				index = Options.SEARCH_ARTIFACT.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.artifactsToFindExact.put(params.get(index), false);
					params.remove(index);
				}

				index = Options.SEARCH_DEPS.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.dependenciesToFindExact
							.put(params.get(index), false);
					params.remove(index);
				}

				index = Options.ALLIGN.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.alignLocalDepVersion.add(params.get(index));
					params.remove(index);
				}

				index = Options.SET_DEP_VER.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.setDepVersions.add(params.get(index));
					params.remove(index);
				}

				index = Options.SCAN_SYS_DEPS.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.scanSystemDeps = true;
				}

				index = Options.SCAN_LOCAL_DEPS.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.scanLocalDeps = true;
				}

				index = Options.SCAN_LOCAL_SYS_DEPS.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.scanLocalSystemDeps = true;
				}

				index = Options.SCAN_LOCAL_NON_SYS_DEPS.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.scanLocalNonSystemDeps = true;
				}

				index = Options.PERSIST_ARTIFACTS.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.persistArtifactListTo = params.get(index);
					params.remove(index);
				}
				index = Options.CHECK_ARTIFACT_LIST.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.readArtifactListFrom = params.get(index);
					params.remove(index);
				}

				index = Options.EXTRACT_DEPS.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.persistDeps.put(params.get(index + 1), params
							.get(index));
					params.remove(index);
					params.remove(index);
				}

				index = Options.APPLY_DEPS.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.applyDeps.put(params.get(index + 1), params
							.get(index));
					params.remove(index);
					params.remove(index);
				}

				index = Options.REPLACE_DEP.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.replaceDeps.put(params.get(index), params
							.get(index + 1));
					params.remove(index);
					params.remove(index);
				}

				index = Options.GENERATE_EXCLUDES.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.generateExcludes.put(params.get(index), params
							.get(index + 1));
					params.remove(index);
					params.remove(index);
				}
				index = Options.GENERATE_EXCLUDES_ALL_DEPS.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.generateExcludes.put(params.get(index), null);
					params.remove(index);
				}

				index = Options.UPDATE_ARITFACT_VERSION.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.updateArtifactVersion.add(params.get(index));
					params.remove(index);
				}

				index = Options.UPDATE_ARTIFACT_AND_DEP_VERSION
						.scanPosition(params);
				if (index != -1) {
					params.remove(index);
					config.updateArtifactVersion.add(params.get(index));
					config.setDepVersions.add(params.get(index));
					params.remove(index);
				}

				if (params.size() == lastSize) {
					throw new Error("Unsupported parameters: " + params);
				}
				lastSize = params.size();
			}

		} catch (IndexOutOfBoundsException e) {
			throw new Error("Missing parameter.", e);
		}

		if (config.dirs.size() == 0) {
			config.dirs.add(".");
		}

		return EXIT_OK;
	}
}