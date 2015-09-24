package de.tobiasroeser.maven.versionupdater;

import static de.tototec.utils.functional.FList.map;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import de.tototec.cmdoption.CmdOption;

public class Config {
	@CmdOption(names = { "--help", "-h" }, description = "Show this help message.")
	public boolean help = false;

	@CmdOption(names = "--dryrun", description = "Do not modify any project file")
	public boolean dryrun = false;
	@CmdOption(names = "--list-deps-and-dependants", description = "List all found dependencies and their dependants")
	public boolean listDepsAndDependants;
	@CmdOption(names = "--detect-local-version-mismatch", description = "Detect project that depedend on other local project but with wrong version number")
	public boolean detectLocalVersionMismatch;
	@CmdOption(names = "--persist-artifact-list", args = { "FILE" }, description = "Write a list of found local artifacts (supports --dryrun)")
	public String persistArtifactListTo;
	@CmdOption(names = "--check-artifact-list", args = {
			"FILE" }, description = "Compare a list of artifacts {0} with the real existing artifacts locally found.")
	public String readArtifactListFrom;

	@CmdOption(names = "--list-artifacts", description = "List all found artifacts")
	public boolean listArtifacts;

	@CmdOption(names = "--list-dependencies", description = "List all found dependencies")
	public boolean listDependencies;

	/** List(directory) */
	@CmdOption(names = { "--directory", "-d" }, args = {
			"DIR" }, description = "Search maven project in directory {0}. If not given at least once, the current directory will be searched.")
	public final List<String> dirs = new LinkedList<String>();

	/** List(artifact-key) */
	@CmdOption(names = "--align-local-dep-version", args = {
			"PROJECT" }, maxCount = -1, description = "Sync version of dependants to local project {0} (supports --dryrun)")
	public final List<String> alignLocalDepVersion = new LinkedList<String>();
	/** Map(dependency-key-with-version) */
	@CmdOption(names = "--set-dep-version", args = {
			"PAR" }, maxCount = -1, description = "Updates the versions of all matching dependencies to dependencies {0} (supports --dryrun)")
	public final List<String> setDepVersions = new LinkedList<String>();

	@CmdOption(names = "--exact", description = "When searching, only match exactly the same artifact keys")
	public boolean exactMatch = false;

	@CmdOption(names = "--filter-local", description = "Filter (when given) search to include/exclude local dependencies")
	public Boolean filterLocal;

	@CmdOption(names = "--filter-system", description = "Filter (when given) search to include/exclude system dependencies")
	public Boolean filterSystem;

	/** Map(file-to-write -> project) */
	// TODO: write a converter
	@CmdOption(names = "--extract-project-deps", args = { "PROJECT",
			"FILE" }, maxCount = -1, description = "Extract the project dependencies of the given project {0} and write them to file {1}")
	public final Map<String, String> persistDeps = new LinkedHashMap<String, String>();

	/** Map(file-to-read -> project-to-update) */
	@CmdOption(names = "--apply-project-deps", args = { "PROJECT",
			"FILE" }, maxCount = -1, description = "Update the project {0} with the dependencies from file {1}")
	public final Map<String, String> applyDeps = new LinkedHashMap<String, String>();

	/** Map(old-dep -> new-dep) */
	@CmdOption(names = "--replace-dependency", args = { "OLD", "NEW" }, maxCount = -1, description = "Replace dependency {0} by dependency {1}")
	public final Map<String, String> replaceDeps = new LinkedHashMap<String, String>();

	/** Map(project -> dep-with-needs-excludes */
	public final Map<String, String> generateExcludes = new HashMap<String, String>();

	@CmdOption(names = "--update-artifact-version", args = {
			"ARTIFACT" }, maxCount = -1, description = "Update the version of the matching artifact to artifact {0} (supports --dryrun)")
	public List<String> updateArtifactVersion = new LinkedList<String>();

	@CmdOption(names = "--search-artifacts", args = {
			"PATTERN" }, maxCount = -1, description = "Search for artifact(s) with pattern {0} (supports --exact)")
	public List<String> searchArtifacts = new LinkedList<String>();

	@CmdOption(names = "--search-dependencies", args = {
			"PATTERN" }, maxCount = -1, description = "Search for dependency(s) with pattern {0} (supports --exact})")
	public List<String> searchDependencies = new LinkedList<String>();

	@CmdOption(names = "--update-artifact-and-dep-version", args = {
			"VERSION" }, maxCount = -1, description = "Update the artifact and all dependencies to that artifact to version {0} (same as --update-artifact-version and --set-dep-version used together)")
	public List<String> updateArtifactAndDepVersion = new LinkedList<String>();
	// (String key) {
	// updateArtifactVersion.add(key);
	// setDepVersions.add(key);
	// }

	@CmdOption(names = "--search-multi-version-deps", description = "Search dependecies, which are present with more to one version. (NOT IMPLEMENTED)")
	public boolean searchMultiVersionDeps;

	@CmdOption(names = "--search-plugins", args = { "PLUGIN" }, maxCount = -1, description = "Search Maven-plugin {0} and the using project.")
	public List<String> searchPlugins = new LinkedList<String>();

	@CmdOption(names = { "--verbose", "-v" }, description = "Verbose output")
	public boolean verbose = false;

	@CmdOption(names = { "--profile" }, args = { "PROFILE" }, description = "Enable a maven profile", maxCount = -1)
	public void addProfiles(final String profiles) {
		final List<String> ps = map(profiles.split(","), s -> s.trim());
		if (!ps.isEmpty()) {
			final List<String> newProfiles = new LinkedList<>(this._profiles);
			newProfiles.addAll(ps);
			this._profiles = Collections.unmodifiableList(newProfiles);
		}

	}

	private List<String> _profiles = Collections.unmodifiableList(new LinkedList<>());

	public List<String> profiles() {
		return _profiles;
	}

}