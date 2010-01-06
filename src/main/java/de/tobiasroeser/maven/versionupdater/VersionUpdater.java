package de.tobiasroeser.maven.versionupdater;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.maven.pom.x400.Model;
import org.apache.maven.pom.x400.Parent;
import org.apache.maven.pom.x400.ProjectDocument;
import org.apache.maven.pom.x400.Model.Dependencies;
import org.apache.maven.pom.x400.Model.Modules;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jackage.util.VariableExpander;

public class VersionUpdater {

	private String pomTemplateFileName = "pom.xml.template";
	private String pomFileName = "pom.xml";
	private final Log log = LogFactory.getLog(VersionUpdater.class);
	private XmlOptions xmlOptions;
	private boolean dryrun = false;
	/** List(directory) */
	final List<String> dirs = new LinkedList<String>();
	/** Map(artifact-key -> find-exact) */
	final Map<String, Boolean> artifactsToFindExact = new LinkedHashMap<String, Boolean>();
	/** Map(dependency-key -> find-exact) */
	final Map<String, Boolean> dependenciesToFindExact = new LinkedHashMap<String, Boolean>();
	/** List(artifact-key) */
	final List<String> alignLocalDepVersion = new LinkedList<String>();
	boolean listDepsAndDependants = false;
	boolean detectLocalVersionMismatch = false;
	/** Map(dependency-key -> version) */
	final Map<String, String> setDepVersions = new LinkedHashMap<String, String>();
	private boolean scanSystemDeps;
	private boolean scanLocalSystemDeps;
	private boolean scanLocalNonSystemDeps;
	private boolean scanLocalDeps;

	public static void main(String[] args) {
		try {
			int status = new VersionUpdater().run(Arrays.asList(args));
			System.exit(status);
		} catch (Throwable t) {
			LogFactory.getLog(VersionUpdater.class).error(
					"Caught an exception.", t);
			System.exit(1);
		}
	}

	private int parseCmdline(List<String> args) {
		ArrayList<String> params = new ArrayList<String>(args);
		int lastSize = params.size();
		try {
			while (lastSize > 0) {
				int index;

				index = params.indexOf("--help");
				if (index == -1) {
					index = params.indexOf("-h");
				}
				if (index != -1) {
					params.remove(index);

					LinkedList<String[]> options = new LinkedList<String[]>();

					options.add(new String[] { "--help, -h", "This help" });
					options
							.add(new String[] {
									"-d PAR",
									"Search maven project in directory PAR. If not given at least once, the local directory will be searched." });
					options.add(new String[] { "--dryrun",
							"Do not modify any pom.xml." });
					options
							.add(new String[] { "--list-deps-and-dependants",
									"List all found dependencies and their dependants" });
					options
							.add(new String[] {
									"--detect-local-version-mismatch",
									"Detect project that depedend on other local project but with wrong version number" });
					options.add(new String[] { "--find-artifact PAR",
							"Find the artifact named PAR" });
					options.add(new String[] { "--find-dependency PAR",
							"Find the dependency named PAR" });
					options.add(new String[] { "--search-artifacts PAR",
							"Search for artifact with pattern PAR" });
					options.add(new String[] { "--search-dependencies PAR",
							"Search for dependencies with pattern PAR" });
					options
							.add(new String[] { "--align-local-dep-version PAR",
									"Sync version of dependants to local project PAR" });
					options
							.add(new String[] { "--set-dep-version PAR1 PAR2",
									"Set the version for all dependencies PAR1 to version PAR2" });
					options.add(new String[] { "--scan-system-deps",
							"Show all system dependencies" });
					options
							.add(new String[] { "--scan-local-system-deps",
									"Show all system dependencies that are also available as a project" });
					options
							.add(new String[] { "--scan-local-non-system-deps",
									"Show all non-system dependencies that are also available as a project" });

					options.add(new String[] { "--TODO-replace-dependency PAR1 PAR2", "Replace dependency PAR1 by dependency PAR2" });
					options.add(new String[] { "--TODO-make-optional-compile-but-non-optional-runtime-deps-for PAR", "Reorganize dependencies for project PAR" });

					int firstColSize = 8;
					for (String[] strings : options) {
						if (strings.length > 0) {
							firstColSize = Math.max(firstColSize, strings[0]
									.length());
						}
					}
					firstColSize += 2;
					String optionsString = "";
					optionsString += "Options:";
					for (String[] strings : options) {
						if (strings.length > 0) {
							optionsString += "\n" + strings[0];
						}
						if (strings.length > 1) {
							for (int count = firstColSize - strings[0].length(); count > 0; --count) {
								optionsString += " ";
							}
							optionsString += strings[1];
						}
					}

					System.out.println(optionsString);
					return -1;
				}

				index = params.indexOf("-d");
				if (index != -1) {
					params.remove(index);
					dirs.add(params.get(index));
					params.remove(index);
				}

				index = params.indexOf("--dryrun");
				if (index != -1) {
					params.remove(index);
					this.dryrun = true;
				}

				index = params.indexOf("--list-deps-and-dependants");
				if (index != -1) {
					params.remove(index);
					listDepsAndDependants = true;
				}

				index = params.indexOf("--detect-local-version-mismatch");
				if (index != -1) {
					params.remove(index);
					detectLocalVersionMismatch = true;
				}

				index = params.indexOf("--find-artifact");
				if (index != -1) {
					params.remove(index);
					artifactsToFindExact.put(params.get(index), true);
					params.remove(index);
				}

				index = params.indexOf("--find-dependency");
				if (index != -1) {
					params.remove(index);
					dependenciesToFindExact.put(params.get(index), true);
					params.remove(index);
				}

				index = params.indexOf("--search-artifacts");
				if (index != -1) {
					params.remove(index);
					artifactsToFindExact.put(params.get(index), false);
					params.remove(index);
				}

				index = params.indexOf("--search-dependencies");
				if (index != -1) {
					params.remove(index);
					dependenciesToFindExact.put(params.get(index), false);
					params.remove(index);
				}

				index = params.indexOf("--align-local-dep-version");
				if (index != -1) {
					params.remove(index);
					alignLocalDepVersion.add(params.get(index));
					params.remove(index);
				}

				index = params.indexOf("--set-dep-version");
				if (index != -1) {
					params.remove(index);
					setDepVersions
							.put(params.get(index), params.get(index + 1));
					params.remove(index);
					params.remove(index);
				}

				index = params.indexOf("--scan-system-deps");
				if (index != -1) {
					params.remove(index);
					scanSystemDeps = true;
				}

				index = params.indexOf("--scan-local-deps");
				if (index != -1) {
					params.remove(index);
					scanLocalDeps = true;
				}

				index = params.indexOf("--scan-local-system-deps");
				if (index != -1) {
					params.remove(index);
					scanLocalSystemDeps = true;
				}

				index = params.indexOf("--scan-local-non-system-deps");
				if (index != -1) {
					params.remove(index);
					scanLocalNonSystemDeps = true;
				}

				if (params.size() == lastSize) {
					throw new Error("Unsupported parameters: " + params);
				}
				lastSize = params.size();
			}

		} catch (IndexOutOfBoundsException e) {
			throw new Error("Missing parameter.", e);
		}

		if (dirs.size() == 0) {
			dirs.add(".");
		}

		return 0;
	}

	public int run(List<String> args) {

		int ok = parseCmdline(args);
		if (ok == -1 /* help */) {
			return 0;
		}
		if (ok != 0) {
			return ok;
		}

		log.info("Scanning for projects based on: " + dirs);
		List<LocalArtifact> reactorArtifacts = scanReactorArtifacts(dirs
				.toArray(new String[dirs.size()]));

		log.info("Analyzing dependencies...");
		Map<String, List<LocalArtifact>> artifactMultiMap = buildArtifactMultiMap(reactorArtifacts);
		Map<Artifact, List<Dependency>> reactorDependencies = evaluateDirectArtifactDependencies(reactorArtifacts);

		// Produce some output
		if (listDepsAndDependants) {
			log.info(MessageFormat.format(
					"Found {0} projects. Checking for duplicates...",
					reactorArtifacts.size()));
			checkForDuplicates(reactorArtifacts);
			log.info("Found the following artifacts: \n  "
					+ formatList(reactorArtifacts, "\n  "));

			Map<String, List<LocalArtifact>> localArtifacts = buildArtifactMultiMap(reactorArtifacts);
			Map<String, List<LocalArtifact>> depKeysToDependants = buildDependencyMultiMap(reactorDependencies);

			StringBuilder depResult = new StringBuilder();
			for (Boolean showLocal : new Boolean[] { false, true }) {
				for (Entry<String, List<LocalArtifact>> dep : depKeysToDependants
						.entrySet()) {
					boolean local = localArtifacts.containsKey(dep.getKey());
					if (local == showLocal) {
						depResult.append((MessageFormat.format(
								"\n  {2} {0}\n   - {1}", dep.getKey(),
								formatList(dep.getValue(), "\n   - "),
								local ? "LOCAL" : "")));
					}
				}
			}
			log.info(MessageFormat.format("Found {0} dependencies: {1}",
					reactorDependencies.size(), depResult));
		}

		if (artifactsToFindExact.size() > 0) {
			findOrSearchArtifacts(artifactsToFindExact, "artifact",
					artifactMultiMap);
		}

		if (dependenciesToFindExact.size() > 0) {
			for (Entry<String, Boolean> search : dependenciesToFindExact
					.entrySet()) {
				showDependencies(reactorArtifacts, search.getKey(), search
						.getValue(), null, null);
			}
		}

		if (detectLocalVersionMismatch) {
			reportVersionMismatch(reactorArtifacts, null);
		}

		if (alignLocalDepVersion.size() > 0) {
			List<VersionMismatch> mismatches = reportVersionMismatch(
					reactorArtifacts, alignLocalDepVersion);
			for (VersionMismatch vm : mismatches) {
				modifyDependency(vm.getDependency(), vm.getArtifact()
						.getVersion());
			}
		}

		if (setDepVersions.size() > 0) {
			Map<String, List<Dependency>> deps = findDirectArtifactDependencies(reactorArtifacts);
			for (Entry<String, String> dv : setDepVersions.entrySet()) {
				List<Dependency> depsToChange = deps.get(dv.getKey());
				for (Dependency dependency : depsToChange) {
					modifyDependency(dependency, dv.getValue());
				}
			}
		}

		if (scanSystemDeps) {
			showDependencies(reactorArtifacts, null, false, null, true);
		}

		if (scanLocalDeps) {
			showDependencies(reactorArtifacts, null, false, true, null);
		}

		if (scanLocalSystemDeps) {
			showDependencies(reactorArtifacts, null, false, true, true);
		}

		if (scanLocalNonSystemDeps) {
			showDependencies(reactorArtifacts, null, false, true, false);
		}

		return ok;
	}

	private void modifyDependency(Dependency dependency, String version) {
		if (!dependency.isChangeAllowed()) {
			log.info("Modifying project " + dependency.getProject()
					+ " is not allowed because: \""
					+ dependency.getChangeProtectBecause() + "\" in "
					+ dependency);
			return;
		}

		if (version != null) {
			if (dryrun) {
				log.info("(dryrun) I would change version of dependency: "
						+ dependency + "\n  - to version: " + version);
				return;
			}

			log.info("About to change version of dependency: " + dependency
					+ " to version: " + version);

			File pomFile = dependency.getProject().getLocation();

			ProjectDocument o;
			try {
				o = ProjectDocument.Factory.parse(pomFile, getXmlOptions());
				boolean neededSave = false;

				Model project = o.getProject();
				for (org.apache.maven.pom.x400.Dependency dep : project
						.getDependencies().getDependencyArray()) {
					if (dep.getGroupId().equals(
							dependency.getDependencyArtifact().getGroup())
							&& dep.getArtifactId().equals(
									dependency.getDependencyArtifact()
											.getArtifact())
							&& dep.getVersion().equals(
									dependency.getDependencyArtifact()
											.getVersion())) {

						if (!version.equals(dep.getVersion())) {
							dep.setVersion(version);
							neededSave = true;
						}
					}
				}

				if (neededSave) {
					log.info("Modifying file: " + pomFile);
					o.save(pomFile);
				}

			} catch (XmlException e) {
				log.error("Could not process file: " + pomFileName, e);
			} catch (IOException e) {
				log.error("Could not process file: " + pomFileName, e);
			}

		}
	}

	private void showDependencies(List<LocalArtifact> localArtifacts,
			String pattern, boolean exact, Boolean local, Boolean system) {
		StringBuilder depResult = new StringBuilder();

		Map<String, List<Dependency>> deps = findDirectArtifactDependencies(localArtifacts);

		int count = 0;

		for (Entry<String, List<Dependency>> dep : deps.entrySet()) {
			String key = dep.getKey();
			if (pattern == null || (!exact && key.contains(pattern))
					|| (exact && key.equals(pattern))) {
				List<Dependency> filteredDeps = dep.getValue();

				if (local != null) {
					boolean isLocal = false;
					for (LocalArtifact artifact : localArtifacts) {
						String artifactKey = artifact.getGroup() + ":"
								+ artifact.getArtifact();
						if (key.equals(artifactKey)) {
							isLocal = true;
							break;
						}
					}
					if (isLocal != local.booleanValue()) {
						filteredDeps = Collections.emptyList();
					}
				}

				if (system != null) {
					List<Dependency> systemFilter = new LinkedList<Dependency>();
					for (Dependency dependency : filteredDeps) {
						if (dependency.isSystem() == system.booleanValue()) {
							systemFilter.add(dependency);
						}
					}
					filteredDeps = systemFilter;
				}

				if (filteredDeps.size() > 0) {
					count += filteredDeps.size();
					depResult.append((MessageFormat.format("\n  {0}\n   - {1}",
							key, formatList(filteredDeps, "\n   - "))));
				}
			}
		}
		log.info(MessageFormat.format("Found {0} dependencies: {1}", count,
				depResult));
	}

	private List<VersionMismatch> reportVersionMismatch(
			List<LocalArtifact> reactorArtifacts, List<String> selectArtifacts) {
		List<VersionMismatch> report = new LinkedList<VersionMismatch>();

		Map<String, List<LocalArtifact>> artifactMap = buildArtifactMultiMap(reactorArtifacts);
		Map<Artifact, List<Dependency>> directDependencies = evaluateDirectArtifactDependencies(reactorArtifacts);
		Map<String, Boolean> selected = new LinkedHashMap<String, Boolean>();

		if (selectArtifacts != null) {
			for (String sa : selectArtifacts) {
				selected.put(sa, false);
			}
		}

		for (Entry<Artifact, List<Dependency>> depEntry : directDependencies
				.entrySet()) {

			String key = depEntry.getKey().getGroup() + ":"
					+ depEntry.getKey().getArtifact();
			if (selected.size() > 0) {
				if (selected.containsKey(key)) {
					selected.put(key, true);
				} else {
					log.debug("Skipping report for key (not selected): " + key);
					continue;
				}
			}

			if (artifactMap.containsKey(key)) {
				if (!equalsArtifacts(artifactMap.get(key).get(0), depEntry
						.getKey())) {

					log.info("Mismatch detected for: " + key
							+ "\n  Required is: " + depEntry.getKey()
							+ "\n  Local available is: " + artifactMap.get(key)
							+ "\n  Dependencies: " + depEntry.getValue());

					for (Dependency depSource : depEntry.getValue()) {
						final VersionMismatch versionMismatch = new VersionMismatch(
								key, artifactMap.get(key).get(0), depSource);
						report.add(versionMismatch);
					}
				}
			}
		}

		for (Entry<String, Boolean> s : selected.entrySet()) {
			if (!s.getValue()) {
				log.warn("Could not found selected dependency: " + s.getKey());
			}
		}

		return report;
	}

	private boolean equalsArtifacts(Artifact lhs, Artifact rhs) {
		return lhs.getGroup().equals(rhs.getGroup())
				&& lhs.getArtifact().equals(rhs.getArtifact())
				&& lhs.getVersion().equals(rhs.getVersion());
	}

	private void findOrSearchArtifacts(
			Map<String, Boolean> artifactsToFindExact, String typeName,
			Map<String, List<LocalArtifact>> artifactMultiMap) {
		for (Entry<String, Boolean> key : artifactsToFindExact.entrySet()) {
			log.info("Searching " + typeName + ": " + key.getKey());
			boolean found = false;
			if (key.getValue()) {
				if (artifactMultiMap.containsKey(key)) {
					for (LocalArtifact artifact : artifactMultiMap.get(key
							.getKey())) {
						log.info("  Found: " + artifact + " at "
								+ artifact.getLocation());
						found = true;
					}
				}
			} else {
				for (Entry<String, List<LocalArtifact>> entry : artifactMultiMap
						.entrySet()) {
					if (entry.getKey().contains(key.getKey())) {
						for (LocalArtifact artifact : entry.getValue()) {
							log.info("  Found: " + artifact + " at "
									+ artifact.getLocation());
							found = true;
						}
					}
				}
			}
			if (!found) {
				log
						.error("  Could not found " + typeName + ": "
								+ key.getKey());
			}
		}
	}

	private Map<Artifact, List<Dependency>> evaluateDirectArtifactDependencies(
			List<LocalArtifact> reactorArtifacts) {

		Map<Artifact, List<Dependency>> depsAndNeeders = new LinkedHashMap<Artifact, List<Dependency>>();

		for (LocalArtifact artifact : reactorArtifacts) {

			VariableExpander<String> vars = new VariableExpander<String>();
			vars.addVar("project.groupId", artifact.getGroup());
			vars.addVar("project.artifactId", artifact.getArtifact());
			vars.addVar("project.version", artifact.getVersion());

			final File pomFile = artifact.getLocation();

			try {
				ProjectDocument o = ProjectDocument.Factory.parse(pomFile,
						getXmlOptions());

				Model project = o.getProject();
				Dependencies dependencies = project.getDependencies();
				if (dependencies != null) {
					for (org.apache.maven.pom.x400.Dependency dep : dependencies
							.getDependencyArray()) {

						String groupId = dep.getGroupId();
						String artifactId = dep.getArtifactId();
						String version = dep.getVersion();
						String classifier = dep.getClassifier();
						String scope = dep.getScope();
						if (scope == null) {
							scope = "compile";
						}
						String systemPath = dep.getSystemPath();

						List<String> problems = new LinkedList<String>();

						if (groupId.contains("$")) {
							log.debug("Found variable in groupId: " + groupId
									+ " -- project " + pomFile);
							problems.add("Variable used in groupId (" + groupId
									+ ")");
						}
						if (artifactId.contains("$")) {
							log.debug("Found variable in artifactId: "
									+ artifactId + " -- project " + pomFile);
							problems.add("Variable used in artifactId ("
									+ artifactId + ")");
						}
						if (version.contains("$")) {
							log.debug("Found variable in version: " + version
									+ " -- project " + pomFile);
							problems.add("Variable used in version (" + version
									+ ")");
						}

						Artifact depArtifact = new Artifact(vars
								.expand(groupId), vars.expand(artifactId), vars
								.expand(version), "jar");
						Dependency dependency = new Dependency(depArtifact,
								artifact, classifier, scope.trim().equals(
										"system"), systemPath);

						for (String problem : problems) {
							dependency.addChangeProtectBecause(problem);
						}

						List<Dependency> dependants;
						if (depsAndNeeders.containsKey(depArtifact)) {
							dependants = depsAndNeeders.get(depArtifact);
						} else {
							dependants = new LinkedList<Dependency>();
							depsAndNeeders.put(depArtifact, dependants);
						}
						dependants.add(dependency);
					}
				}
				if (project.getParent() != null) {
					log
							.warn("Maven projects with parents currently not fully supported!. Found one at: "
									+ pomFile);
				}
			} catch (XmlException e) {
				log.error("Could not parse maven project: "
						+ pomFile.getAbsolutePath(), e);

			} catch (IOException e) {
				log.error("Could not parse maven project: "
						+ pomFile.getAbsolutePath(), e);

			}

		}

		return depsAndNeeders;
	}

	/**
	 * @param reactorArtifacts
	 * @return Map(dependency-key:List(Dependency))
	 */
	private Map<String, List<Dependency>> findDirectArtifactDependencies(
			List<LocalArtifact> reactorArtifacts) {

		Map<String, List<Dependency>> depsAndNeeders = new LinkedHashMap<String, List<Dependency>>();

		for (LocalArtifact artifact : reactorArtifacts) {

			VariableExpander<String> vars = new VariableExpander<String>();
			vars.addVar("project.groupId", artifact.getGroup());
			vars.addVar("project.artifactId", artifact.getArtifact());
			vars.addVar("project.version", artifact.getVersion());

			final File pomFile = artifact.getLocation();

			try {
				ProjectDocument o = ProjectDocument.Factory.parse(pomFile,
						getXmlOptions());

				Model project = o.getProject();
				Dependencies dependencies = project.getDependencies();
				if (dependencies != null) {
					for (org.apache.maven.pom.x400.Dependency dep : dependencies
							.getDependencyArray()) {

						String groupId = dep.getGroupId();
						String artifactId = dep.getArtifactId();
						String version = dep.getVersion();
						String classifier = dep.getClassifier();
						String scope = dep.getScope();
						if (scope == null) {
							scope = "compile";
						}
						String systemPath = dep.getSystemPath();

						List<String> problems = new LinkedList<String>();

						if (groupId.contains("$")) {
							log.debug("Found variable in groupId: " + groupId
									+ " -- project " + pomFile);
							problems.add("Variable used in groupId (" + groupId
									+ ")");
						}
						if (artifactId.contains("$")) {
							log.debug("Found variable in artifactId: "
									+ artifactId + " -- project " + pomFile);
							problems.add("Variable used in artifactId ("
									+ artifactId + ")");
						}
						if (version.contains("$")) {
							log.debug("Found variable in version: " + version
									+ " -- project " + pomFile);
							problems.add("Variable used in version (" + version
									+ ")");
						}

						Artifact depArtifact = new Artifact(vars
								.expand(groupId), vars.expand(artifactId), vars
								.expand(version), "jar");
						Dependency dependency = new Dependency(depArtifact,
								artifact, classifier, scope.trim().equals(
										"system"), systemPath);

						for (String problem : problems) {
							dependency.addChangeProtectBecause(problem);
						}

						String key = depArtifact.getGroup() + ":"
								+ depArtifact.getArtifact();

						List<Dependency> dependants;
						if (depsAndNeeders.containsKey(key)) {
							dependants = depsAndNeeders.get(key);
						} else {
							dependants = new LinkedList<Dependency>();
							depsAndNeeders.put(key, dependants);
						}
						dependants.add(dependency);
					}
				}
				if (project.getParent() != null) {
					log
							.warn("Maven projects with parents currently not fully supported!. Found one at: "
									+ pomFile);
				}
			} catch (XmlException e) {
				log.error("Could not parse maven project: "
						+ pomFile.getAbsolutePath(), e);

			} catch (IOException e) {
				log.error("Could not parse maven project: "
						+ pomFile.getAbsolutePath(), e);

			}

		}

		return depsAndNeeders;
	}

	private Map<String, List<LocalArtifact>> buildArtifactMultiMap(
			Collection<LocalArtifact> artifacts) {
		LinkedHashMap<String, List<LocalArtifact>> map = new LinkedHashMap<String, List<LocalArtifact>>();
		for (LocalArtifact artifact : artifacts) {
			String key = artifact.getGroup() + ":" + artifact.getArtifact();
			List<LocalArtifact> values;
			if (map.containsKey(key)) {
				values = map.get(key);
			} else {
				values = new LinkedList<LocalArtifact>();
				map.put(key, values);
			}
			values.add(artifact);
		}
		return map;
	}

	private Map<String, List<LocalArtifact>> buildDependencyMultiMap(
			Map<Artifact, List<Dependency>> dependencies) {
		LinkedHashMap<String, List<LocalArtifact>> map = new LinkedHashMap<String, List<LocalArtifact>>();
		for (Entry<Artifact, List<Dependency>> depEntry : dependencies
				.entrySet()) {
			String key = depEntry.getKey().getGroup() + ":"
					+ depEntry.getKey().getArtifact();
			for (Dependency artifact : depEntry.getValue()) {
				List<LocalArtifact> values;
				if (map.containsKey(key)) {
					values = map.get(key);
				} else {
					values = new LinkedList<LocalArtifact>();
					map.put(key, values);
				}
				values.add(artifact.getProject());
			}
		}
		return map;
	}

	private void checkForDuplicates(Collection<LocalArtifact> artifacts) {
		HashSet<String> packageNames = new HashSet<String>();

		for (LocalArtifact artifact : artifacts) {
			String key = artifact.getGroup() + ":" + artifact.getArtifact();
			if (packageNames.contains(key)) {
				throw new RuntimeException(
						"Duplicate group:artifact pair found in reactor: "
								+ key);
			}
			packageNames.add(key);
		}
	}

	private String formatList(List<?> list, String separator) {
		StringBuilder format = new StringBuilder();
		for (Object o : list) {
			if (format.length() != 0) {
				format.append(separator);
			}
			format.append(o);
		}
		return format.toString();
	}

	private List<LocalArtifact> scanReactorArtifacts(String... dirs) {

		List<LocalArtifact> artifacts = new LinkedList<LocalArtifact>();

		for (String dir : dirs) {

			File pomFile = new File(dir, pomFileName);
			if (!pomFile.exists() || !pomFile.isFile()) {
				continue;
			}

			try {
				ProjectDocument o = ProjectDocument.Factory.parse(pomFile,
						getXmlOptions());

				Model project = o.getProject();

				VariableExpander<String> vars = new VariableExpander<String>();

				String groupId = project.getGroupId();
				String artifactId = project.getArtifactId();
				String version = project.getVersion();
				String packaging = project.getPackaging();
				packaging = packaging != null ? packaging : "jar";

				Parent parent = project.getParent();
				if (parent != null) {
					log
							.warn("Maven projects with parents currently not fully supported!. Found one at: "
									+ pomFile);

					vars.addVar("parent.groupId", parent.getGroupId());
					vars.addVar("project.parent.groupId", parent.getGroupId());
					vars.addVar("parent.artifactId", parent.getArtifactId());
					vars.addVar("project.parent.artifactId", parent
							.getArtifactId());
					vars.addVar("parent.version", parent.getVersion());
					vars.addVar("project.parent.version", parent.getVersion());

					groupId = groupId != null ? groupId : parent.getGroupId();
					artifactId = artifactId != null ? artifactId : parent
							.getArtifactId();
					version = version != null ? version : parent.getVersion();
				}

				LocalArtifact artifact = new LocalArtifact(
						vars.expand(groupId), vars.expand(artifactId), vars
								.expand(version), packaging, pomFile);
				artifacts.add(artifact);

				Modules modules = project.getModules();
				if (modules != null) {
					for (String module : modules.getModuleArray()) {
						artifacts.addAll(scanReactorArtifacts(new File(dir,
								module).getPath()));
					}
				}

			} catch (XmlException e) {
				log.error("Could not parse maven project: "
						+ pomFile.getAbsolutePath(), e);
			} catch (IOException e) {
				log.error("Could not parse maven project: "
						+ pomFile.getAbsolutePath(), e);
			}
		}

		return artifacts;
	}

	private XmlOptions getXmlOptions() {
		if (xmlOptions == null) {
			XmlOptions opts = new XmlOptions();
			Map<String, String> ns = new HashMap<String, String>();
			ns.put("", "http://maven.apache.org/POM/4.0.0");
			opts.setLoadSubstituteNamespaces(ns);
			xmlOptions = opts;
		}
		return xmlOptions;
	}
}
