/**
 * 
 */
package de.tobiasroeser.maven.versionupdater;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;

import de.tobiasroeser.maven.shared.Option;

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
			"Update the artifact with the given version in PAR", "PAR");

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

	static final Option SET_DEP_VER = new Option(
			"set-dep-version",
			null,
			"Set the version for all dependencies PAR1 to version PAR2 (supports --dryrun)",
			" PAR1", "PAR2");

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

	static List<Option> allOptions() {

		LinkedList<Option> options = new LinkedList<Option>();
		try {
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
}