package de.tobiasroeser.maven.versionupdater;

import de.tobiasroeser.cmdoption.Option;

public class Options {

	// TODO: not migrated to Config

	private static Option option(String longOption, String shortOption, String description,
			String... args) {
		return new Option(longOption, shortOption, description, null, null, args, 0, 1);
	}
	
	static final Option TODO_APPLY_VERSIONS = option(
			"apply-version",
			null,
			"Apply the artifact versions read from artifact list PAR to local projects.",
			"PAR");

	static final Option TODO_MAKE_OPTIONAL_COMPILE_DEPS = option(
			"TODO-make-optional-compile-but-non-optional-runtime-deps-for",
			null, "Reorganize dependencies for project PAR", "PAR");

	static final Option TODO_GENERATE_EXCLUDES = option(
			"generate-excludes",
			null,
			"TODO-Generate dependency excludes in project PAR1 for dependency PAR2",
			"PAR1", "PAR2");

	static final Option TODO_GENERATE_EXCLUDES_ALL_DEPS = option(
			"TODO-generate-all-excludes", null,
			"Generate dependency excludes in project PAR for all dependencies",
			"PAR");

}