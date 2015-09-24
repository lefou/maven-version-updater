package de.tobiasroeser.maven.versionupdater;

import static de.tototec.utils.functional.FList.flatMap;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.LogFactory;
import org.apache.maven.pom.x400.Dependency.Exclusions;
import org.apache.maven.pom.x400.Exclusion;
import org.apache.maven.pom.x400.Model;
import org.apache.maven.pom.x400.Model.Dependencies;
import org.apache.maven.pom.x400.ProjectDocument;
import org.apache.xmlbeans.XmlException;
import org.jackage.util.VariableExpander;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tobiasroeser.maven.shared.MavenXmlSupport;
import de.tototec.cmdoption.CmdlineParser;
import de.tototec.cmdoption.CmdlineParserException;
import de.tototec.utils.functional.Optional;

public class VersionUpdater {

	// private String pomTemplateFileName = "pom.xml.template";
	private String pomFileName = "pom.xml";
	private final Logger log = LoggerFactory.getLogger(VersionUpdater.class);

	public static void main(final String[] args) {
		try {
			final int status = new VersionUpdater().run(args);
			System.exit(status);
		} catch (final Throwable t) {
			LogFactory.getLog(VersionUpdater.class).error("Caught an exception: " + t.getMessage() + "\n", t);
			System.exit(1);
		}
	}

	public int run(final String[] args) {
		final Config config = new Config();
		final CmdlineParser cp = new CmdlineParser(config);
		cp.setProgramName("mvu");
		try {
			cp.parse(args);
		} catch (final CmdlineParserException e) {
			System.err.println(e.getMessage());
			return 1;
		}
		if (config.verbose) {
			Dependency.setVerbose(config.verbose);
		}
		if (config.help) {
			final StringBuilder sb = new StringBuilder();
			sb.append("Maven Version Updater " + BuildConfig.mvuVersion() + " - " + BuildConfig.mvuCopyright() + "\n\n");
			cp.usage(sb);
			System.out.println(sb);
			return 0;
		}
		for (final String key : config.updateArtifactAndDepVersion) {
			config.updateArtifactVersion.add(key);
			config.setDepVersions.add(key);
		}

		return run(config);
	}

	public int run(final Config config) {
		try {
			final List<String> dirs = config.dirs;
			if (dirs.size() == 0) {
				dirs.add(".");
			}

			final List<String> profiles = config.profiles();
			log.debug("Profiles: {}", profiles);

			log.info("Scanning for projects based on: " + dirs);
			final List<LocalArtifact> reactorArtifacts = scanReactorArtifacts(dirs, profiles);

			if (config.listArtifacts) {
				log.info("Local artifacts:\n  - " + formatList(reactorArtifacts, "\n  - "));
			}

			if (config.listDependencies) {
				showDependencies(reactorArtifacts, null, config.exactMatch, config.filterLocal, config.filterSystem, profiles);
			}

			// Produce some output
			if (config.listDepsAndDependants) {
				log.info("Analyzing dependencies...");
				final Map<Artifact, List<Dependency>> reactorDependencies = evaluateDirectArtifactDependencies(reactorArtifacts);

				log.info(MessageFormat.format("Found {0} projects. Checking for duplicates...", reactorArtifacts.size()));
				checkForDuplicates(reactorArtifacts);
				log.info("Found the following artifacts: \n  " + formatList(reactorArtifacts, "\n  "));

				final Map<String, List<LocalArtifact>> localArtifacts = buildArtifactMultiMap(reactorArtifacts);
				final Map<String, List<LocalArtifact>> depKeysToDependants = buildDependencyMultiMap(reactorDependencies);

				final StringBuilder depResult = new StringBuilder();
				for (final Boolean showLocal : new Boolean[] { false, true }) {
					for (final Entry<String, List<LocalArtifact>> dep : depKeysToDependants.entrySet()) {
						final boolean local = localArtifacts.containsKey(dep.getKey());
						if (local == showLocal) {
							depResult.append((MessageFormat.format("\n  {2} {0}\n   - {1}", dep.getKey(), formatList(dep.getValue(), "\n   - "),
									local ? "LOCAL" : "")));
						}
					}
				}
				log.info(MessageFormat.format("Found {0} dependencies: {1}", reactorDependencies.size(), depResult));
			}

			if (config.searchArtifacts.size() > 0) {
				final Map<String, List<LocalArtifact>> artifactMultiMap = buildArtifactMultiMap(reactorArtifacts);
				findOrSearchArtifacts(config.searchArtifacts, "artifact", config.exactMatch, artifactMultiMap);
			}

			if (config.searchDependencies.size() > 0) {
				for (final String search : config.searchDependencies) {
					showDependencies(reactorArtifacts, search, config.exactMatch, config.filterLocal, null, profiles);
				}
			}

			if (config.searchMultiVersionDeps) {
				searchMultiVersionDeps(reactorArtifacts);
			}

			if (config.detectLocalVersionMismatch) {
				reportVersionMismatch(reactorArtifacts, null);
			}

			if (config.alignLocalDepVersion.size() > 0) {
				final List<VersionMismatch> mismatches = reportVersionMismatch(reactorArtifacts, config.alignLocalDepVersion);
				for (final VersionMismatch vm : mismatches) {
					modifyDependencyVersion(vm.getDependency(), vm.getArtifact().getVersion(), config.dryrun);
				}
			}

			if (config.setDepVersions.size() > 0) {
				final Map<String, List<Dependency>> deps = findDirectArtifactDependencies(reactorArtifacts, profiles);
				for (final String key : config.setDepVersions) {
					final String[] split = key.split(":", 3);
					if (split.length != 3) {
						throw new IllegalAccessException("Illegal dependency key given: " + key);
					}
					final List<Dependency> depsToChange = deps.get(split[0] + ":" + split[1]);
					if (depsToChange != null) {
						for (final Dependency dependency : depsToChange) {
							modifyDependencyVersion(dependency, split[2], config.dryrun);
						}
					}
				}
			}

			if (config.persistArtifactListTo != null) {
				persistArtifactListTo(reactorArtifacts, config.persistArtifactListTo, config.dryrun);
			}

			if (config.readArtifactListFrom != null) {
				readAndCheckArtifactList(reactorArtifacts, config.readArtifactListFrom);
			}

			if (config.persistDeps.size() > 0) {
				for (final Entry<String, String> e : config.persistDeps.entrySet()) {
					saveDepsToFile(e.getKey(), e.getValue(), reactorArtifacts, config.dryrun, profiles);
				}
			}

			if (config.applyDeps.size() > 0) {
				for (final Entry<String, String> e : config.applyDeps.entrySet()) {
					updateProjectDeps(e.getKey(), e.getValue(), reactorArtifacts, config.dryrun);
				}
			}

			if (config.replaceDeps.size() > 0) {
				for (final Entry<String, String> e : config.replaceDeps.entrySet()) {
					replaceDependency(e.getKey(), e.getValue(), reactorArtifacts, config.dryrun, profiles);
				}
			}

			if (config.updateArtifactVersion.size() > 0) {
				for (final String artifact : config.updateArtifactVersion) {
					updateProjectVersion(reactorArtifacts, artifact, config.dryrun);
				}
			}

			// if (config.generateExcludes.size() > 0) {
			// for (Entry<String, String> e : config.generateExcludes
			// .entrySet()) {
			// generateExcludes(e.getKey(), e.getValue(),
			// reactorArtifacts, config.dryrun);
			// }
			// }

			return 0;
		} catch (final Exception e) {
			log.error("Errors occured.", e);
			return 1;
		}
	}

	private void searchMultiVersionDeps(final List<LocalArtifact> reactorArtifacts) {
		log.warn("NOT IMPLEMENTED");
	}

	private void persistArtifactListTo(final List<LocalArtifact> reactorArtifacts, final String outputTo, final boolean dryrun) {

		final File file = new File(outputTo);
		if (dryrun) {
			log.info("I would write artifacts to " + file.getAbsolutePath());
			return;
		}

		try {
			log.info("Writing artifacts to " + file.getAbsolutePath());

			if (file.exists()) {
				log.error("File '" + file.getAbsolutePath() + "' already exists. Skipping.");
				return;
			}

			final PrintWriter printWriter = new PrintWriter(file);

			final ArrayList<LocalArtifact> sortedArtifacts = new ArrayList<LocalArtifact>(reactorArtifacts);
			Collections.sort(sortedArtifacts, new Comparator<LocalArtifact>() {
				@Override
				public int compare(final LocalArtifact o1, final LocalArtifact o2) {
					int comp = o1.getGroup().compareTo(o2.getGroup());
					if (comp == 0) {
						comp = o1.getArtifact().compareTo(o2.getArtifact());
					}
					if (comp == 0) {
						comp = o1.getVersion().compareTo(o2.getVersion());
					}
					return comp;
				}
			});

			for (final LocalArtifact artifact : reactorArtifacts) {
				final String line = artifact.getGroup() + ":" + artifact.getArtifact() + ":" + artifact.getVersion();
				printWriter.println(line);
			}

			printWriter.close();

		} catch (final FileNotFoundException e) {
			throw new Error("Cannot write file " + outputTo, e);
		}

	}

	private void updateProjectVersion(final List<LocalArtifact> reactorArtifacts, final String artifact, final boolean dryrun) {

		final String[] split = artifact.split(":", 3);
		if (split.length != 3) {
			log.error("Could not parse artifact key: " + artifact);
			return;
		}

		LocalArtifact candidate = null;

		for (final LocalArtifact localArtifact : reactorArtifacts) {
			if (localArtifact.getGroup().equals(split[0]) && localArtifact.getArtifact().equals(split[1])) {
				candidate = localArtifact;
				break;
			}
		}

		if (candidate == null) {
			log.error("Could to found project: " + split[0] + ":" + split[1]);
			return;
		}

		if (dryrun) {
			log.info("I would change project version: " + artifact);
			return;
		}

		log.info("Updating version for project: " + candidate + " to " + split[2]);

		ProjectDocument o;
		try {
			o = ProjectDocument.Factory.parse(candidate.getLocation(), MavenXmlSupport.instance.createXmlOptions());

			final Model project = o.getProject();
			project.setVersion(split[2]);

			o.save(candidate.getLocation());

		} catch (final XmlException e) {
			log.error("Could not process pom file: " + candidate.getLocation(), e);
		} catch (final IOException e) {
			log.error("Could not process pom file: " + candidate.getLocation(), e);
		}
	}

	// private void generateExcludes(String project, String dependency,
	// List<LocalArtifact> reactorArtifacts, boolean dryrun) {
	//
	// LocalArtifact candidate = null;
	//
	// for (LocalArtifact artifact : reactorArtifacts) {
	// if(artifact.toString().equals(key)) {
	// candidate = artifact;
	// }
	// }
	//
	// if(candidate == null) {
	// log.error("Could not found project: "+key);
	// return;
	// }
	//
	// Map<String, List<Dependency>> depMap =
	// findDirectArtifactDependencies(Arrays.asList(candidate));
	// for(List<Dependency> depList:depMap.values()) {
	// for(Dependency dep : depList) {
	//
	// }
	// }
	//
	// }

	private void replaceDependency(final String oldDependencyKey, final String newDependencyKey, final List<LocalArtifact> reactorArtifacts,
			final boolean dryrun, final List<String> profiles) {

		final Map<String, List<Dependency>> depMap = findDirectArtifactDependencies(reactorArtifacts, profiles);

		for (final List<Dependency> depList : depMap.values()) {
			for (final Dependency dep : depList) {
				if (dep.getDependencyArtifact().toString().equals(oldDependencyKey)) {

					final String[] split = newDependencyKey.split(":", 3);
					if (split.length != 3) {
						log.warn("Incorrect dependency key given: " + newDependencyKey);
						continue;
					}
					final Artifact artifact = new Artifact(split[0], split[1], split[2], "jar");
					modifyDependency(dep, artifact, dryrun);
				}
			}
		}

	}

	private void updateProjectDeps(final String readDepsFromFile, final String projectToUpate, final List<LocalArtifact> localArtifacts,
			final boolean dryrun) {

		LocalArtifact candidate = null;

		for (final LocalArtifact artifact : localArtifacts) {
			String key = artifact.getGroup() + ":" + artifact.getArtifact();
			if (key.equals(projectToUpate)) {
				candidate = artifact;
			} else {
				key += ":" + artifact.getVersion();
				if (key.equals(projectToUpate)) {
					candidate = artifact;
				}
			}
		}

		if (candidate == null) {
			log.error("Could not found project: " + projectToUpate);
			return;
		}

		final List<Dependency> deps = readDependenciesFromTextFile(readDepsFromFile, candidate);
		if (deps == null) {
			return;
		}

		addDependencies(candidate, deps, dryrun);

	}

	private List<Dependency> readDependenciesFromTextFile(final String readDepsFromFile, final LocalArtifact candidate) {

		final List<Dependency> deps = new LinkedList<Dependency>();

		final File file = new File(readDepsFromFile);

		if (!file.exists() || !file.isFile()) {
			log.error("Cannot read not-existing file: " + file.getAbsolutePath());
			return null;
		}

		try {
			final LineNumberReader reader = new LineNumberReader(new FileReader(file));
			String line = null;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.equals("") || line.startsWith("#")) {
					// comment
					continue;
				}
				final String[] split = line.split("\\(", 2);
				if (split.length == 0) {
					// something wrong here
					log.warn("Could not parse line: " + line);
					continue;
				}
				final String[] part1 = split[0].split(":", 3);
				if (part1.length < 3) {
					log.warn("Incorrect line found! Could not parse Maven artifact coordiantes. Line: " + line);
					continue;
				}
				final String groupId = part1[0];
				final String artifactId = part1[1];
				final String version = part1[2];

				String scope = "compile";
				String classifier = null;
				String systemPath = null;
				final List<String> exclusions = new LinkedList<String>();

				if (split.length == 2) {
					// read additional stuff
					final String part2 = split[1].substring(0, split[1].length() - 1);
					final String[] adds = part2.split(",");
					for (final String add : adds) {
						if (add.startsWith("scope=")) {
							scope = add.substring(add.indexOf("=") + 1);
						} else if (add.startsWith("classifier=")) {
							classifier = add.substring(add.indexOf("=") + 1);
						} else if (add.startsWith("systemPath=")) {
							systemPath = add.substring(add.indexOf("=") + 1);
						} else if (add.startsWith("exclusions=")) {
							final String[] excls = add.substring(add.indexOf("=") + 1).split(";");
							exclusions.addAll(Arrays.asList(excls));
						} else {
							log.warn("Could not parse line: " + line);
						}
					}
				}

				final Optional<String> profile = Optional.none();
				final Dependency dep = new Dependency(new Artifact(groupId, artifactId, version, "jar"), candidate, profile, classifier, scope,
						systemPath,
						exclusions);
				deps.add(dep);

			}

			reader.close();
			return deps;

		} catch (final FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	private void saveDepsToFile(final String saveToFile, final String project, final List<LocalArtifact> localArtifacts, final boolean dryrun,
			final List<String> profiles) {
		final File file = new File(saveToFile);
		if (file.exists()) {
			log.error("File already exists: " + file.getAbsolutePath());
			return;
		}

		LocalArtifact candidate = null;

		for (final LocalArtifact artifact : localArtifacts) {
			String key = artifact.getGroup() + ":" + artifact.getArtifact();
			if (key.equals(project)) {
				candidate = artifact;
			} else {
				key += ":" + artifact.getVersion();
				if (key.equals(project)) {
					candidate = artifact;
				}
			}
		}

		if (candidate == null) {
			log.error("Could not found project: " + project);
			return;
		}

		if (dryrun) {
			log.info("I would save dependencies of project '" + candidate + "' to file: " + file.getAbsolutePath());
			return;
		}
		log.info("Saving dependencies of project '" + candidate + "' to file: " + file.getAbsolutePath());

		final List<Dependency> deps = readDepsOfPom(candidate.getLocation().getAbsolutePath(), profiles);

		try {
			final PrintStream stream = new PrintStream(new BufferedOutputStream(new FileOutputStream(file)));
			stream.println("# Dependencies of: " + candidate);
			for (final Dependency dep : deps) {
				String key = dep.getDependencyArtifact().toString();
				final List<String> adds = new LinkedList<String>();
				if (dep.getClassifier() != null) {
					adds.add("classifier=" + dep.getClassifier());
				}
				if (dep.getScope() != null) {
					adds.add("scope=" + dep.getScope());
				}
				if (dep.isSystem() && dep.getSystemPath() != null) {
					adds.add("systemPath=" + dep.getSystemPath());
				}
				if (dep.getExclusions().size() > 0) {
					adds.add("exclusions=" + formatList(dep.getExclusions(), ";"));
				}
				if (adds.size() > 0) {
					key += "(" + formatList(adds, ",") + ")";
				}
				stream.println(key);
			}
			stream.close();

		} catch (final FileNotFoundException e) {
			log.error("Could not write file: " + file.getAbsolutePath(), e);
		}

	}

	private void readAndCheckArtifactList(final List<LocalArtifact> reactorArtifacts, final String listToReadAndParse) {

		final File file = new File(listToReadAndParse);
		if (!file.exists() || !file.isFile()) {
			log.error("Cannot read artifact list from file " + file.getAbsolutePath());
			return;
		}

		final List<Artifact> readArtifacts = new LinkedList<Artifact>();

		try {
			final LineNumberReader reader = new LineNumberReader(new BufferedReader(new FileReader(file)));
			try {
				String line = null;

				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (line.equals("") || line.startsWith("#")) {
						// comment or empty line
						continue;
					}
					final String[] split = line.split(":", 4);
					if (split.length < 3) {
						log.warn("Could not parse line: " + line);
						continue;
					}

					readArtifacts.add(new Artifact(split[0], split[1], split[2], null));
				}
			} finally {
				reader.close();
			}

		} catch (final FileNotFoundException e) {
			log.error("Could not found artifact list at " + file.getAbsolutePath(), e);
		} catch (final IOException e) {
			log.error("Could not parse artifact list at " + file.getAbsolutePath(), e);
		}

		// Convert LocalArtifacts to Artifacts (to match the equals() contract)
		final List<Artifact> existingArtifacts = new LinkedList<Artifact>();
		for (final LocalArtifact a : reactorArtifacts) {
			existingArtifacts.add(new Artifact(a));
		}

		log.debug("Read the following artifacts from file: " + readArtifacts);
		log.debug("Existing artifacts: " + existingArtifacts);

		// Find and filter equal artifacts
		final List<Artifact> equalArtifacts = new LinkedList<Artifact>();
		final List<Artifact> equalExistingArtifacts = new LinkedList<Artifact>();
		for (final Artifact readArtifact : readArtifacts) {
			for (final Artifact existingArtifact : existingArtifacts) {
				if (readArtifact.equalsByProjectNameAndVersion(existingArtifact)) {
					equalArtifacts.add(readArtifact);
					equalExistingArtifacts.add(existingArtifact);
				}
			}
		}
		readArtifacts.removeAll(equalArtifacts);
		existingArtifacts.removeAll(equalExistingArtifacts);

		// Detect version mismatches
		final Map<Artifact, Artifact> versionMismatches = new LinkedHashMap<Artifact, Artifact>();
		for (final Artifact readArtifact : readArtifacts) {
			for (final Artifact existingArtifact : existingArtifacts) {
				if (readArtifact.equalsByProjectName(existingArtifact) && !readArtifact.getVersion().equals(existingArtifact.getVersion())) {
					versionMismatches.put(readArtifact, existingArtifact);
				}
			}
		}
		readArtifacts.removeAll(versionMismatches.keySet());
		existingArtifacts.removeAll(versionMismatches.values());

		// local missing = readArtifacts

		// local additional = existingArtifacts

		// Output
		final boolean ok = readArtifacts.size() == 0 && existingArtifacts.size() == 0 && versionMismatches.size() == 0;
		if (ok) {
			log.info("The list of artifacts matches the existing artifacts.");
		}
		if (equalArtifacts.size() > 0) {
			log.debug("The following artifacts matches perfectly:\n  - " + formatList(equalArtifacts, "\n  - "));
		}
		if (versionMismatches.size() > 0) {
			log.info("The following artifacts versions do not match (file=local):\n  - " + formatList(versionMismatches.entrySet(), "\n  - "));
		}
		if (readArtifacts.size() > 0) {
			log.info("The following artifacts are locally missing:\n  - " + formatList(readArtifacts, "\n  - "));
		}
		if (existingArtifacts.size() > 0) {
			log.info("The following artifacts are only locally available:\n  - " + formatList(existingArtifacts, "\n  - "));
		}
	}

	private void modifyDependencyVersion(final Dependency dependency, final String version, final boolean dryrun) {

		final Artifact versionArtifact = new Artifact(dependency.getDependencyArtifact().getGroup(),
				dependency.getDependencyArtifact().getArtifact(), version, dependency.getDependencyArtifact().getPackaging());

		modifyDependency(dependency, versionArtifact, dryrun);
	}

	private void modifyDependency(final Dependency dependency, final Artifact newDependencyArtifact, final boolean dryrun) {
		if (!dependency.isChangeAllowed()) {
			log.info("Modifying project " + dependency.getProject() + " is not allowed because: \"" + dependency.getChangeProtectBecause() + "\" in "
					+ dependency);
			return;
		}

		if (newDependencyArtifact != null) {
			if (dryrun) {
				log.info("(dryrun) I would change dependency: " + dependency + "\n  - to: " + newDependencyArtifact);
				return;
			}

			log.info("About to change dependency: " + dependency + " to: " + newDependencyArtifact);

			final File pomFile = dependency.getProject().getLocation();

			ProjectDocument o;
			try {
				o = ProjectDocument.Factory.parse(pomFile, MavenXmlSupport.instance.createXmlOptions());
				boolean neededSave = false;

				final Model project = o.getProject();
				for (final org.apache.maven.pom.x400.Dependency dep : project.getDependencies().getDependencyArray()) {
					if (dep.getGroupId().equals(dependency.getDependencyArtifact().getGroup())
							&& dep.getArtifactId().equals(dependency.getDependencyArtifact().getArtifact())
							&& dep.getVersion().equals(dependency.getDependencyArtifact().getVersion())) {

						if (!newDependencyArtifact.getGroup().equals(dep.getGroupId())) {
							dep.setGroupId(newDependencyArtifact.getGroup());
							neededSave = true;
						}

						if (!newDependencyArtifact.getArtifact().equals(dep.getArtifactId())) {
							dep.setArtifactId(newDependencyArtifact.getArtifact());
							neededSave = true;
						}

						if (!newDependencyArtifact.getVersion().equals(dep.getVersion())) {
							dep.setVersion(newDependencyArtifact.getVersion());
							neededSave = true;
						}
					}
				}

				if (neededSave) {
					log.info("Modifying file: " + pomFile);
					o.save(pomFile);
				}

			} catch (final XmlException e) {
				log.error("Could not process file: " + pomFileName, e);
			} catch (final IOException e) {
				log.error("Could not process file: " + pomFileName, e);
			}

		}
	}

	private void addDependencies(final LocalArtifact projectToChange, final List<Dependency> dependencies, final boolean dryrun) {
		if (dryrun) {
			log.info("(dryrun) I would add dependencies to " + projectToChange);
			return;
		}

		log.info("About to add dependencies to " + projectToChange);

		final File pomFile = projectToChange.getLocation();

		ProjectDocument o;
		try {
			o = ProjectDocument.Factory.parse(pomFile, MavenXmlSupport.instance.createXmlOptions());

			final Model project = o.getProject();

			project.setDependencies(Dependencies.Factory.newInstance(MavenXmlSupport.instance.createXmlOptions()));

			for (final Dependency dep : dependencies) {

				final org.apache.maven.pom.x400.Dependency mvnDep = project.getDependencies().addNewDependency();

				mvnDep.setGroupId(dep.getDependencyArtifact().getGroup());
				mvnDep.setArtifactId(dep.getDependencyArtifact().getArtifact());
				mvnDep.setVersion(dep.getDependencyArtifact().getVersion());
				if (dep.getScope() != null) {
					mvnDep.setScope(dep.getScope());
				}
				if (dep.getClassifier() != null) {
					mvnDep.setClassifier(dep.getClassifier());
				}
				if (dep.getSystemPath() != null) {
					mvnDep.setSystemPath(dep.getSystemPath());
				}
				if (dep.getExclusions().size() > 0) {
					if (!mvnDep.isSetExclusions()) {
						mvnDep.setExclusions(Exclusions.Factory.newInstance(MavenXmlSupport.instance.createXmlOptions()));
					}
					final Exclusions mvnExes = mvnDep.getExclusions();
					for (final String e : dep.getExclusions()) {
						final Exclusion mvnEx = mvnExes.addNewExclusion();
						final String[] split = e.split(":");
						mvnEx.setGroupId(split[0]);
						mvnEx.setArtifactId(split[1]);
					}
				}

			}

			log.info("Modifying file: " + pomFile);
			o.save(pomFile);

		} catch (final XmlException e) {
			log.error("Could not process file: " + pomFileName, e);
		} catch (final IOException e) {
			log.error("Could not process file: " + pomFileName, e);
		}

	}

	// FIXME: get rid of tri-state Boolean's
	private void showDependencies(final List<LocalArtifact> localArtifacts, final String pattern, final boolean exact, final Boolean local,
			final Boolean system, final List<String> profiles) {
		final StringBuilder depResult = new StringBuilder();

		final Map<String, List<Dependency>> deps = findDirectArtifactDependencies(localArtifacts, profiles);

		int count = 0;

		for (final Entry<String, List<Dependency>> dep : deps.entrySet()) {
			final String key = dep.getKey();
			if (pattern == null || (!exact && key.contains(pattern)) || (exact && key.equals(pattern))) {
				List<Dependency> filteredDeps = dep.getValue();

				if (local != null) {
					boolean isLocal = false;
					for (final LocalArtifact artifact : localArtifacts) {
						final String artifactKey = artifact.getGroup() + ":" + artifact.getArtifact();
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
					final List<Dependency> systemFilter = new LinkedList<Dependency>();
					for (final Dependency dependency : filteredDeps) {
						if (dependency.isSystem() == system.booleanValue()) {
							systemFilter.add(dependency);
						}
					}
					filteredDeps = systemFilter;
				}

				if (filteredDeps.size() > 0) {
					count += filteredDeps.size();
					depResult.append((MessageFormat.format("\n  {0}\n   - {1}", key, formatList(filteredDeps, "\n   - "))));
				}
			}
		}
		log.info(MessageFormat.format("Found {0} dependencies: {1}", count, depResult));
	}

	private List<VersionMismatch> reportVersionMismatch(final List<LocalArtifact> reactorArtifacts, final List<String> selectArtifacts) {
		final List<VersionMismatch> report = new LinkedList<VersionMismatch>();

		final Map<String, List<LocalArtifact>> artifactMap = buildArtifactMultiMap(reactorArtifacts);
		final Map<Artifact, List<Dependency>> directDependencies = evaluateDirectArtifactDependencies(reactorArtifacts);
		final Map<String, Boolean> selected = new LinkedHashMap<String, Boolean>();

		if (selectArtifacts != null) {
			for (final String sa : selectArtifacts) {
				selected.put(sa, false);
			}
		}

		for (final Entry<Artifact, List<Dependency>> depEntry : directDependencies.entrySet()) {

			final String key = depEntry.getKey().getGroup() + ":" + depEntry.getKey().getArtifact();
			if (selected.size() > 0) {
				if (selected.containsKey(key)) {
					selected.put(key, true);
				} else {
					log.debug("Skipping report for key (not selected): " + key);
					continue;
				}
			}

			if (artifactMap.containsKey(key)) {
				if (!equalsArtifacts(artifactMap.get(key).get(0), depEntry.getKey())) {

					log.info("Mismatch detected for: " + key + "\n  Required is: " + depEntry.getKey() + "\n  Local available is: "
							+ artifactMap.get(key) + "\n  Dependencies: " + depEntry.getValue());

					for (final Dependency depSource : depEntry.getValue()) {
						final VersionMismatch versionMismatch = new VersionMismatch(key, artifactMap.get(key).get(0), depSource);
						report.add(versionMismatch);
					}
				}
			}
		}

		for (final Entry<String, Boolean> s : selected.entrySet()) {
			if (!s.getValue()) {
				log.warn("Could not found selected dependency: " + s.getKey());
			}
		}

		return report;
	}

	private boolean equalsArtifacts(final Artifact lhs, final Artifact rhs) {
		return lhs.getGroup().equals(rhs.getGroup()) && lhs.getArtifact().equals(rhs.getArtifact()) && lhs.getVersion().equals(rhs.getVersion());
	}

	private void findOrSearchArtifacts(final List<String> artifactsToFind, final String typeName, final boolean exact,
			final Map<String, List<LocalArtifact>> artifactMultiMap) {
		for (final String search : artifactsToFind) {
			log.info("Searching " + typeName + ": " + search);
			boolean found = false;
			if (exact) {
				if (artifactMultiMap.containsKey(search)) {
					for (final LocalArtifact artifact : artifactMultiMap.get(search)) {
						log.info("  Found: " + artifact + " at " + artifact.getLocation());
						found = true;
					}
				}
			} else {
				for (final Entry<String, List<LocalArtifact>> entry : artifactMultiMap.entrySet()) {
					if (entry.getKey().contains(search)) {
						for (final LocalArtifact artifact : entry.getValue()) {
							log.info("  Found: " + artifact + " at " + artifact.getLocation());
							found = true;
						}
					}
				}
			}
			if (!found) {
				log.error("  Could not found " + typeName + ": " + search);
			}
		}
	}

	private Map<Artifact, List<Dependency>> evaluateDirectArtifactDependencies(final List<LocalArtifact> reactorArtifacts) {

		final Map<Artifact, List<Dependency>> depsAndNeeders = new LinkedHashMap<Artifact, List<Dependency>>();

		for (final LocalArtifact artifact : reactorArtifacts) {

			final VariableExpander<String> vars = new VariableExpander<String>();
			vars.addVar("project.groupId", artifact.getGroup());
			vars.addVar("project.artifactId", artifact.getArtifact());
			vars.addVar("project.version", artifact.getVersion());

			final File pomFile = artifact.getLocation();

			try {
				final ProjectDocument o = ProjectDocument.Factory.parse(pomFile, MavenXmlSupport.instance.createXmlOptions());

				final Model project = o.getProject();
				final Optional<String> profile = Optional.none();
				final Dependencies dependencies = project.getDependencies();
				if (dependencies != null) {
					for (final org.apache.maven.pom.x400.Dependency dep : dependencies.getDependencyArray()) {

						final String groupId = dep.getGroupId();
						final String artifactId = dep.getArtifactId();
						final String version = dep.getVersion();
						final String classifier = dep.getClassifier();
						String scope = dep.getScope();
						if (scope == null) {
							scope = "compile";
						}
						final String systemPath = dep.getSystemPath();

						final List<String> problems = new LinkedList<String>();

						if (groupId.contains("$")) {
							log.debug("Found variable in groupId: " + groupId + " -- project " + pomFile);
							problems.add("Variable used in groupId (" + groupId + ")");
						}
						if (artifactId.contains("$")) {
							log.debug("Found variable in artifactId: " + artifactId + " -- project " + pomFile);
							problems.add("Variable used in artifactId (" + artifactId + ")");
						}
						if (version.contains("$")) {
							log.debug("Found variable in version: " + version + " -- project " + pomFile);
							problems.add("Variable used in version (" + version + ")");
						}

						final List<String> exclusions = new LinkedList<String>();

						if (dep.getExclusions() != null && dep.getExclusions().getExclusionArray() != null) {
							for (final Exclusion e : dep.getExclusions().getExclusionArray()) {
								exclusions.add(e.getGroupId() + ":" + e.getArtifactId());
							}
						}

						final Artifact depArtifact = new Artifact(vars.expand(groupId), vars.expand(artifactId), vars.expand(version), "jar");
						final Dependency dependency = new Dependency(depArtifact, artifact, profile, classifier, scope, systemPath, exclusions);

						for (final String problem : problems) {
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
					log.warn("Maven projects with parents currently not fully supported!. Found one at: " + pomFile);
				}
			} catch (final XmlException e) {
				log.error("Could not parse maven project: " + pomFile.getAbsolutePath(), e);

			} catch (final IOException e) {
				log.error("Could not parse maven project: " + pomFile.getAbsolutePath(), e);

			}

		}

		return depsAndNeeders;
	}

	/**
	 * @param reactorArtifacts
	 * @param profiles
	 * @return Map(dependency-key:List(Dependency))
	 */
	private Map<String, List<Dependency>> findDirectArtifactDependencies(final List<LocalArtifact> reactorArtifacts, final List<String> profiles) {

		final Map<String, List<Dependency>> depsAndNeeders = new LinkedHashMap<String, List<Dependency>>();

		for (final LocalArtifact artifact : reactorArtifacts) {

			final VariableExpander<String> vars = new VariableExpander<String>();
			vars.addVar("project.groupId", artifact.getGroup());
			vars.addVar("project.artifactId", artifact.getArtifact());
			vars.addVar("project.version", artifact.getVersion());

			try {
				final ProjectDocument o = ProjectDocument.Factory.parse(artifact.getLocation(), MavenXmlSupport.instance.createXmlOptions());

				final Model project = o.getProject();
				final Map<String, List<Dependency>> result = MavenXmlSupport.instance.readDirectDependencyFromLocalArtifact(artifact, project,
						profiles);
				for (final Entry<String, List<Dependency>> e : result.entrySet()) {
					if (depsAndNeeders.containsKey(e.getKey())) {
						// All our deps to the existing list
						final List<Dependency> deps = depsAndNeeders.get(e.getKey());
						deps.addAll(e.getValue());
					} else {
						depsAndNeeders.put(e.getKey(), new LinkedList<Dependency>(e.getValue()));
					}
				}

			} catch (final XmlException e) {
				log.error("Could not parse maven project: " + artifact.getLocation().getAbsolutePath(), e);

			} catch (final IOException e) {
				log.error("Could not parse maven project: " + artifact.getLocation().getAbsolutePath(), e);
			}

		}

		return depsAndNeeders;
	}

	private Map<String, List<LocalArtifact>> buildArtifactMultiMap(final Collection<LocalArtifact> artifacts) {
		final LinkedHashMap<String, List<LocalArtifact>> map = new LinkedHashMap<String, List<LocalArtifact>>();
		for (final LocalArtifact artifact : artifacts) {
			final String key = artifact.getGroup() + ":" + artifact.getArtifact();
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

	private Map<String, List<LocalArtifact>> buildDependencyMultiMap(final Map<Artifact, List<Dependency>> dependencies) {
		final LinkedHashMap<String, List<LocalArtifact>> map = new LinkedHashMap<String, List<LocalArtifact>>();
		for (final Entry<Artifact, List<Dependency>> depEntry : dependencies.entrySet()) {
			final String key = depEntry.getKey().getGroup() + ":" + depEntry.getKey().getArtifact();
			for (final Dependency artifact : depEntry.getValue()) {
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

	private void checkForDuplicates(final Collection<LocalArtifact> artifacts) {
		final HashSet<String> packageNames = new HashSet<String>();

		for (final LocalArtifact artifact : artifacts) {
			final String key = artifact.getGroup() + ":" + artifact.getArtifact();
			if (packageNames.contains(key)) {
				throw new RuntimeException("Duplicate group:artifact pair found in reactor: " + key);
			}
			packageNames.add(key);
		}
	}

	private String formatList(final Iterable<?> list, final String separator) {
		final StringBuilder format = new StringBuilder();
		for (final Object o : list) {
			if (format.length() != 0) {
				format.append(separator);
			}
			format.append(o);
		}
		return format.toString();
	}

	private List<LocalArtifact> scanReactorArtifacts(final Collection<String> dirs, final List<String> profiles) {

		final List<LocalArtifact> artifacts = new LinkedList<LocalArtifact>();

		for (final String dir : dirs) {

			final File pomFile = new File(dir, pomFileName);
			if (!pomFile.exists() || !pomFile.isFile()) {
				continue;
			}

			try {
				final MavenXmlSupport mavenXmlSupport = MavenXmlSupport.instance;
				final ProjectDocument o = ProjectDocument.Factory.parse(pomFile, mavenXmlSupport.createXmlOptions());

				final Model project = o.getProject();
				final LocalArtifact artifact = mavenXmlSupport.readLocalArtifactFromProject(project, pomFile);
				artifacts.add(artifact);

				final List<String> modules = flatMap(mavenXmlSupport.getProfiles(project, profiles), p -> {
					final org.apache.maven.pom.x400.Profile.Modules ms = p.getModules();
					if (ms != null) {
						return Arrays.asList(ms.getModuleArray());
					} else {
						return Collections.emptyList();
					}
				});
				for (final String module : modules) {
					artifacts.addAll(scanReactorArtifacts(Arrays.asList(new File(dir, module).getPath()), profiles));
				}

			} catch (final XmlException e) {
				log.error("Could not parse maven project: " + pomFile.getAbsolutePath(), e);
			} catch (final IOException e) {
				log.error("Could not parse maven project: " + pomFile.getAbsolutePath(), e);
			}
		}

		return artifacts;
	}

	public List<Dependency> readDepsOfPom(final String pomFile, final List<String> profiles) {

		final List<Dependency> deps = new LinkedList<Dependency>();

		final File file = new File(pomFile);
		if (!file.exists() || !file.isFile()) {
			log.error("Maven project file does not exists: " + file.getAbsolutePath());
			return deps;
		}

		try {
			final ProjectDocument o = ProjectDocument.Factory.parse(file, MavenXmlSupport.instance.createXmlOptions());

			final Model project = o.getProject();
			final Map<String, List<Dependency>> depsAndDependants = MavenXmlSupport.instance.readDirectDependencyFromProject(project, file, profiles);

			for (final Entry<String, List<Dependency>> e : depsAndDependants.entrySet()) {
				for (final Dependency dep : e.getValue()) {
					if (!deps.contains(dep.getDependencyArtifact())) {
						deps.add(dep);
					}
				}
			}

			return deps;

		} catch (final XmlException e) {
			log.error("Could not read pom file: " + file.getAbsolutePath(), e);
			return deps;
		} catch (final IOException e) {
			log.error("Could not read pom file: " + file.getAbsolutePath(), e);
			return deps;
		}

	}

}
