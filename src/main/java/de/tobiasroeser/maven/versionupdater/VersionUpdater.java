package de.tobiasroeser.maven.versionupdater;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.maven.pom.x400.Exclusion;
import org.apache.maven.pom.x400.Model;
import org.apache.maven.pom.x400.ProjectDocument;
import org.apache.maven.pom.x400.Dependency.Exclusions;
import org.apache.maven.pom.x400.Model.Dependencies;
import org.apache.maven.pom.x400.Model.Modules;
import org.apache.xmlbeans.XmlException;
import org.jackage.util.VariableExpander;

import de.tobiasroeser.cmdoption.AddToCollectionHandler;
import de.tobiasroeser.cmdoption.CmdOption;
import de.tobiasroeser.cmdoption.CmdOptionsParser;
import de.tobiasroeser.cmdoption.CmdOptionsParser.Result;
import de.tobiasroeser.maven.shared.MavenXmlSupport;

public class VersionUpdater {

	// private String pomTemplateFileName = "pom.xml.template";
	private String pomFileName = "pom.xml";
	private final Log log = LogFactory.getLog(VersionUpdater.class);

	private static String VERSION() {
		return "0.1.0";
	}

	public static void main(String[] args) {
		try {
			int status = new VersionUpdater().run(Arrays.asList(args));
			System.exit(status);
		} catch (Throwable t) {
			LogFactory.getLog(VersionUpdater.class).error(
					"Caught an exception: " + t.getMessage() + "\n", t);
			System.exit(1);
		}
	}

	public static class Config {
		@CmdOption(description = "Do not modify any project file")
		public boolean dryrun = false;
		@CmdOption(description = "List all found dependencies and their dependants")
		public boolean listDepsAndDependants;

		@CmdOption(description = "Detect project that depedend on other local project but with wrong version number")
		public boolean detectLocalVersionMismatch;

		@CmdOption(longName = "persist-artifact-list", description = "Write a list of found local artifacts (supports ${dryrun})", args = "PAR")
		public String persistArtifactListTo;
		@CmdOption(longName = "check-artifact-list", description = "Compare a list of artifacts PAR with the real existing artifacts locally found.", args = "PAR")
		public String readArtifactListFrom;

		@CmdOption(description = "List all found artifacts")
		public boolean listArtifacts;

		@CmdOption(description = "List all found dependencies")
		public boolean listDependencies;

		/** List(directory) */
		@CmdOption(longName = "directory", shortName = "d", description = "Search maven project in directory PAR. If not given at least once, the current directory will be searched.", args = "PAR", maxCount = -1)
		public final List<String> dirs = new LinkedList<String>();

		/** List(artifact-key) */
		@CmdOption(description = "Sync version of dependants to local project PAR (supports ${dryrun})", args = "PAR")
		public final List<String> alignLocalDepVersion = new LinkedList<String>();
		/** Map(dependency-key-with-version) */
		@CmdOption(longName = "set-dep-version", //
		description = "Updates the versions of all matching dependencies to dependencies PAR (supports ${dryrun})", //
		args = "PAR", //
		maxCount = -1, //
		handler = AddToCollectionHandler.class)
		public final List<String> setDepVersions = new LinkedList<String>();

		@CmdOption(longName = "exact", description = "When searching, only match exactly the same artifact keys")
		public boolean exactMatch = false;

		@CmdOption(description = "Filter (when given) search to include/exclude local dependencies", args = "true|false")
		public Boolean filterLocal;

		@CmdOption(description = "Filter (when given) search to include/exclude system dependencies", args = "true|false")
		public Boolean filterSystem;

		/** Map(file-to-write -> project) */
		@CmdOption(longName = "extract-project-deps", description = "Extract the project dependencies of the given project PAR1 and write them to file PAR2", args = {
				"PAR1", "PAR2" })
		public final Map<String, String> persistDeps = new HashMap<String, String>();

		/** Map(file-to-read -> project-to-update) */
		@CmdOption(longName = "apply-project-deps", description = "Update the project PAR1 with the dependencies from file PAR2", args = {
				"PAR1", "PAR2" }, maxCount = -1)
		public final Map<String, String> applyDeps = new HashMap<String, String>();
		/** Map(old-dep -> new-dep) */
		@CmdOption(longName = "replace-dependency", description = "Replace dependency PAR1 by dependency PAR2", args = {
				"PAR1", "PAR2" }, maxCount = -1)
		public final Map<String, String> replaceDeps = new HashMap<String, String>();

		/** Map(project -> dep-with-needs-excludes */
		public final Map<String, String> generateExcludes = new HashMap<String, String>();

		@CmdOption(description = "Update the version of the matching artifact to artifact PAR (supports ${dryrun})", //
		args = "PAR", //
		maxCount = -1, //
		handler = AddToCollectionHandler.class)
		public List<String> updateArtifactVersion = new LinkedList<String>();

		@CmdOption(description = "Search for artifact(s) with pattern PAR (supports ${exactMatch})", args = "PAR")
		public List<String> searchArtifacts = new LinkedList<String>();

		@CmdOption(description = "Search for dependency(s) with pattern PAR (supports ${exactMatch})", args = "PAR")
		public List<String> searchDependencies = new LinkedList<String>();

		@CmdOption(description = "Update the artifact and all dependencies to that artifact to version PAR (same as ${updateArtifactVersion} and ${setDepVersion} used together)", args = "PAR")
		public void updateArtifactAndDepVersion(String key) {
			updateArtifactVersion.add(key);
			setDepVersions.add(key);
		}

		@CmdOption(description = "Search dependecies, which are present with more to one version.")
		public boolean searchMultiVersionDeps;

		@CmdOption(description = "Search Maven-plugin PAR and the using project.", args = "PAR", maxCount = -1)
		public List<String> searchPlugins = new LinkedList<String>();

		@CmdOption(longName = "verbose", shortName = "v", description = "Verbose output")
		public void verbose() {
			Dependency.setVerbose(true);
		}
	}

	public int run(List<String> args) {
		Config config = new Config();
		CmdOptionsParser parser = new CmdOptionsParser(Config.class);
		Result ok = parser.parseCmdline(args, config);

		if (ok.isOk()) {
			return run(config);
		}

		if (ok.isHelp()) {
			System.out
					.println(parser
							.formatOptions(
									"Maven Version Updater "
											+ VERSION()
											+ " - (c) 2009-2010 by Tobias Roeser, All Rights Reserved.\nOptions: ",
									true));
			return 0;
		}

		System.out.println(ok.message());
		return ok.code();

	}

	public int run(Config config) {
		try {
			List<String> dirs = config.dirs;
			if (dirs.size() == 0) {
				dirs.add(".");
			}

			log.info("Scanning for projects based on: " + dirs);
			List<LocalArtifact> reactorArtifacts = scanReactorArtifacts(dirs
					.toArray(new String[dirs.size()]));

			if (config.listArtifacts) {
				log.info("Local artifacts:\n  - "
						+ formatList(reactorArtifacts, "\n  - "));
			}

			if (config.listDependencies) {
				showDependencies(reactorArtifacts, null, config.exactMatch,
						config.filterLocal, config.filterSystem);
			}

			// Produce some output
			if (config.listDepsAndDependants) {
				log.info("Analyzing dependencies...");
				Map<Artifact, List<Dependency>> reactorDependencies = evaluateDirectArtifactDependencies(reactorArtifacts);

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
						boolean local = localArtifacts
								.containsKey(dep.getKey());
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

			if (config.searchArtifacts.size() > 0) {
				Map<String, List<LocalArtifact>> artifactMultiMap = buildArtifactMultiMap(reactorArtifacts);
				findOrSearchArtifacts(config.searchArtifacts, "artifact",
						config.exactMatch, artifactMultiMap);
			}

			if (config.searchDependencies.size() > 0) {
				for (String search : config.searchDependencies) {
					showDependencies(reactorArtifacts, search,
							config.exactMatch, config.filterLocal, null);
				}
			}

			if (config.searchMultiVersionDeps) {
				searchMultiVersionDeps(reactorArtifacts);
			}

			if (config.detectLocalVersionMismatch) {
				reportVersionMismatch(reactorArtifacts, null);
			}

			if (config.alignLocalDepVersion.size() > 0) {
				List<VersionMismatch> mismatches = reportVersionMismatch(
						reactorArtifacts, config.alignLocalDepVersion);
				for (VersionMismatch vm : mismatches) {
					modifyDependencyVersion(vm.getDependency(), vm
							.getArtifact().getVersion(), config.dryrun);
				}
			}

			if (config.setDepVersions.size() > 0) {
				Map<String, List<Dependency>> deps = findDirectArtifactDependencies(reactorArtifacts);
				for (String key : config.setDepVersions) {
					String[] split = key.split(":", 3);
					if (split.length != 3) {
						throw new IllegalAccessException(
								"Illegal dependency key given: " + key);
					}
					List<Dependency> depsToChange = deps.get(split[0] + ":"
							+ split[1]);
					if (depsToChange != null) {
						for (Dependency dependency : depsToChange) {
							modifyDependencyVersion(dependency, split[2],
									config.dryrun);
						}
					}
				}
			}

			if (config.persistArtifactListTo != null) {
				persistArtifactListTo(reactorArtifacts,
						config.persistArtifactListTo, config.dryrun);
			}

			if (config.readArtifactListFrom != null) {
				readAndCheckArtifactList(reactorArtifacts,
						config.readArtifactListFrom);
			}

			if (config.persistDeps.size() > 0) {
				for (Entry<String, String> e : config.persistDeps.entrySet()) {
					saveDepsToFile(e.getKey(), e.getValue(), reactorArtifacts,
							config.dryrun);
				}
			}

			if (config.applyDeps.size() > 0) {
				for (Entry<String, String> e : config.applyDeps.entrySet()) {
					updateProjectDeps(e.getKey(), e.getValue(),
							reactorArtifacts, config.dryrun);
				}
			}

			if (config.replaceDeps.size() > 0) {
				for (Entry<String, String> e : config.replaceDeps.entrySet()) {
					replaceDependency(e.getKey(), e.getValue(),
							reactorArtifacts, config.dryrun);
				}
			}

			if (config.updateArtifactVersion.size() > 0) {
				for (String artifact : config.updateArtifactVersion) {
					updateProjectVersion(reactorArtifacts, artifact,
							config.dryrun);
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
		} catch (Exception e) {
			log.error("Errors occured.", e);
			return 1;
		}
	}

	private void searchMultiVersionDeps(List<LocalArtifact> reactorArtifacts) {
	}

	private void persistArtifactListTo(List<LocalArtifact> reactorArtifacts,
			String outputTo, boolean dryrun) {

		File file = new File(outputTo);
		if (dryrun) {
			log.info("I would write artifacts to " + file.getAbsolutePath());
			return;
		}

		try {
			log.info("Writing artifacts to " + file.getAbsolutePath());

			if (file.exists()) {
				log.error("File '" + file.getAbsolutePath()
						+ "' already exists. Skipping.");
				return;
			}

			PrintWriter printWriter = new PrintWriter(file);

			ArrayList<LocalArtifact> sortedArtifacts = new ArrayList<LocalArtifact>(
					reactorArtifacts);
			Collections.sort(sortedArtifacts, new Comparator<LocalArtifact>() {
				public int compare(LocalArtifact o1, LocalArtifact o2) {
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

			for (LocalArtifact artifact : reactorArtifacts) {
				String line = artifact.getGroup() + ":"
						+ artifact.getArtifact() + ":" + artifact.getVersion();
				printWriter.println(line);
			}

			printWriter.close();

		} catch (FileNotFoundException e) {
			throw new Error("Cannot write file " + outputTo, e);
		}

	}

	private void updateProjectVersion(List<LocalArtifact> reactorArtifacts,
			String artifact, boolean dryrun) {

		String[] split = artifact.split(":", 3);
		if (split.length != 3) {
			log.error("Could not parse artifact key: " + artifact);
			return;
		}

		LocalArtifact candidate = null;

		for (LocalArtifact localArtifact : reactorArtifacts) {
			if (localArtifact.getGroup().equals(split[0])
					&& localArtifact.getArtifact().equals(split[1])) {
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

		log.info("Updating version for project: " + candidate + " to "
				+ split[2]);

		ProjectDocument o;
		try {
			o = ProjectDocument.Factory.parse(candidate.getLocation(),
					MavenXmlSupport.instance.createXmlOptions());

			Model project = o.getProject();
			project.setVersion(split[2]);

			o.save(candidate.getLocation());

		} catch (XmlException e) {
			log.error("Could not process pom file: " + candidate.getLocation(),
					e);
		} catch (IOException e) {
			log.error("Could not process pom file: " + candidate.getLocation(),
					e);
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

	private void replaceDependency(String oldDependencyKey,
			String newDependencyKey, List<LocalArtifact> reactorArtifacts,
			boolean dryrun) {

		Map<String, List<Dependency>> depMap = findDirectArtifactDependencies(reactorArtifacts);

		for (List<Dependency> depList : depMap.values()) {
			for (Dependency dep : depList) {
				if (dep.getDependencyArtifact().toString().equals(
						oldDependencyKey)) {

					String[] split = newDependencyKey.split(":", 3);
					if (split.length != 3) {
						log.warn("Incorrect dependency key given: "
								+ newDependencyKey);
						continue;
					}
					Artifact artifact = new Artifact(split[0], split[1],
							split[2], "jar");
					modifyDependency(dep, artifact, dryrun);
				}
			}
		}

	}

	private void updateProjectDeps(String readDepsFromFile,
			String projectToUpate, List<LocalArtifact> localArtifacts,
			boolean dryrun) {

		LocalArtifact candidate = null;

		for (LocalArtifact artifact : localArtifacts) {
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

		List<Dependency> deps = readDependenciesFromTextFile(readDepsFromFile,
				candidate, dryrun);
		if (deps == null) {
			return;
		}

		addDependencies(candidate, deps, dryrun);

	}

	private List<Dependency> readDependenciesFromTextFile(
			String readDepsFromFile, LocalArtifact candidate, boolean dryrun) {

		List<Dependency> deps = new LinkedList<Dependency>();

		File file = new File(readDepsFromFile);

		if (!file.exists() || !file.isFile()) {
			log.error("Cannot read not-existing file: "
					+ file.getAbsolutePath());
			return null;
		}

		try {
			LineNumberReader reader = new LineNumberReader(new FileReader(file));
			String line = null;
			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.equals("") || line.startsWith("#")) {
					// comment
					continue;
				}
				String[] split = line.split("\\(", 2);
				if (split.length == 0) {
					// something wrong here
					log.warn("Could not parse line: " + line);
					continue;
				}
				String[] part1 = split[0].split(":", 3);
				if (part1.length < 3) {
					log
							.warn("Incorrect line found! Could not parse Maven artifact coordiantes. Line: "
									+ line);
					continue;
				}
				String groupId = part1[0];
				String artifactId = part1[1];
				String version = part1[2];

				String scope = "compile";
				String classifier = null;
				String systemPath = null;
				List<String> exclusions = new LinkedList<String>();

				if (split.length == 2) {
					// read additional stuff
					String part2 = split[1].substring(0, split[1].length() - 1);
					String[] adds = part2.split(",");
					for (String add : adds) {
						if (add.startsWith("scope=")) {
							scope = add.substring(add.indexOf("=") + 1);
						} else if (add.startsWith("classifier=")) {
							classifier = add.substring(add.indexOf("=") + 1);
						} else if (add.startsWith("systemPath=")) {
							systemPath = add.substring(add.indexOf("=") + 1);
						} else if (add.startsWith("exclusions=")) {
							String[] excls = add
									.substring(add.indexOf("=") + 1).split(";");
							exclusions.addAll(Arrays.asList(excls));
						} else {
							log.warn("Could not parse line: " + line);
						}
					}
				}

				Dependency dep = new Dependency(new Artifact(groupId,
						artifactId, version, "jar"), candidate, classifier,
						scope, systemPath, exclusions);
				deps.add(dep);

			}

			reader.close();
			return deps;

		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return null;
	}

	private void saveDepsToFile(String saveToFile, String project,
			List<LocalArtifact> localArtifacts, boolean dryrun) {
		File file = new File(saveToFile);
		if (file.exists()) {
			log.error("File already exists: " + file.getAbsolutePath());
			return;
		}

		LocalArtifact candidate = null;

		for (LocalArtifact artifact : localArtifacts) {
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
			log.info("I would save dependencies of project '" + candidate
					+ "' to file: " + file.getAbsolutePath());
			return;
		}
		log.info("Saving dependencies of project '" + candidate + "' to file: "
				+ file.getAbsolutePath());

		List<Dependency> deps = readDepsOfPom(candidate.getLocation()
				.getAbsolutePath());

		try {
			PrintStream stream = new PrintStream(new BufferedOutputStream(
					new FileOutputStream(file)));
			stream.println("# Dependencies of: " + candidate);
			for (Dependency dep : deps) {
				String key = dep.getDependencyArtifact().toString();
				List<String> adds = new LinkedList<String>();
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
					adds.add("exclusions="
							+ formatList(dep.getExclusions(), ";"));
				}
				if (adds.size() > 0) {
					key += "(" + formatList(adds, ",") + ")";
				}
				stream.println(key);
			}
			stream.close();

		} catch (FileNotFoundException e) {
			log.error("Could not write file: " + file.getAbsolutePath(), e);
		}

	}

	private void readAndCheckArtifactList(List<LocalArtifact> reactorArtifacts,
			String listToReadAndParse) {

		File file = new File(listToReadAndParse);
		if (!file.exists() || !file.isFile()) {
			log.error("Cannot read artifact list from file "
					+ file.getAbsolutePath());
			return;
		}

		List<Artifact> readArtifacts = new LinkedList<Artifact>();

		try {
			LineNumberReader reader = new LineNumberReader(new BufferedReader(
					new FileReader(file)));
			String line = null;

			while ((line = reader.readLine()) != null) {
				line = line.trim();
				if (line.equals("") || line.startsWith("#")) {
					// comment or empty line
					continue;
				}
				String[] split = line.split(":", 4);
				if (split.length < 3) {
					log.warn("Could not parse line: " + line);
					continue;
				}

				readArtifacts.add(new Artifact(split[0], split[1], split[2],
						null));
			}

		} catch (FileNotFoundException e) {
			log.error("Could not found artifact list at "
					+ file.getAbsolutePath(), e);
		} catch (IOException e) {
			log.error("Could not parse artifact list at "
					+ file.getAbsolutePath(), e);
		}

		// Convert LocalArtifacts to Artifacts (to match the equals() contract)
		List<Artifact> existingArtifacts = new LinkedList<Artifact>();
		for (LocalArtifact a : reactorArtifacts) {
			existingArtifacts.add(new Artifact(a));
		}

		log.debug("Read the following artifacts from file: " + readArtifacts);
		log.debug("Existing artifacts: " + existingArtifacts);

		// Find and filter equal artifacts
		List<Artifact> equalArtifacts = new LinkedList<Artifact>();
		List<Artifact> equalExistingArtifacts = new LinkedList<Artifact>();
		for (Artifact readArtifact : readArtifacts) {
			for (Artifact existingArtifact : existingArtifacts) {
				if (readArtifact
						.equalsByProjectNameAndVersion(existingArtifact)) {
					equalArtifacts.add(readArtifact);
					equalExistingArtifacts.add(existingArtifact);
				}
			}
		}
		readArtifacts.removeAll(equalArtifacts);
		existingArtifacts.removeAll(equalExistingArtifacts);

		// Detect version mismatches
		Map<Artifact, Artifact> versionMismatches = new LinkedHashMap<Artifact, Artifact>();
		for (Artifact readArtifact : readArtifacts) {
			for (Artifact existingArtifact : existingArtifacts) {
				if (readArtifact.equalsByProjectName(existingArtifact)
						&& !readArtifact.getVersion().equals(
								existingArtifact.getVersion())) {
					versionMismatches.put(readArtifact, existingArtifact);
				}
			}
		}
		readArtifacts.removeAll(versionMismatches.keySet());
		existingArtifacts.removeAll(versionMismatches.values());

		// local missing = readArtifacts

		// local additional = existingArtifacts

		// Output
		boolean ok = readArtifacts.size() == 0 && existingArtifacts.size() == 0
				&& versionMismatches.size() == 0;
		if (ok) {
			log.info("The list of artifacts matches the existing artifacts.");
		}
		if (equalArtifacts.size() > 0) {
			log.debug("The following artifacts matches perfectly:\n  - "
					+ formatList(equalArtifacts, "\n  - "));
		}
		if (versionMismatches.size() > 0) {
			log
					.info("The following artifacts versions do not match (file=local):\n  - "
							+ formatList(versionMismatches.entrySet(), "\n  - "));
		}
		if (readArtifacts.size() > 0) {
			log.info("The following artifacts are locally missing:\n  - "
					+ formatList(readArtifacts, "\n  - "));
		}
		if (existingArtifacts.size() > 0) {
			log
					.info("The following artifacts are only locally available:\n  - "
							+ formatList(existingArtifacts, "\n  - "));
		}
	}

	private void modifyDependencyVersion(Dependency dependency, String version,
			boolean dryrun) {

		Artifact versionArtifact = new Artifact(dependency
				.getDependencyArtifact().getGroup(), dependency
				.getDependencyArtifact().getArtifact(), version, dependency
				.getDependencyArtifact().getPackaging());

		modifyDependency(dependency, versionArtifact, dryrun);
	}

	private void modifyDependency(Dependency dependency,
			Artifact newDependencyArtifact, boolean dryrun) {
		if (!dependency.isChangeAllowed()) {
			log.info("Modifying project " + dependency.getProject()
					+ " is not allowed because: \""
					+ dependency.getChangeProtectBecause() + "\" in "
					+ dependency);
			return;
		}

		if (newDependencyArtifact != null) {
			if (dryrun) {
				log.info("(dryrun) I would change dependency: " + dependency
						+ "\n  - to: " + newDependencyArtifact);
				return;
			}

			log.info("About to change dependency: " + dependency + " to: "
					+ newDependencyArtifact);

			File pomFile = dependency.getProject().getLocation();

			ProjectDocument o;
			try {
				o = ProjectDocument.Factory.parse(pomFile,
						MavenXmlSupport.instance.createXmlOptions());
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

						if (!newDependencyArtifact.getGroup().equals(
								dep.getGroupId())) {
							dep.setGroupId(newDependencyArtifact.getGroup());
							neededSave = true;
						}

						if (!newDependencyArtifact.getArtifact().equals(
								dep.getArtifactId())) {
							dep.setArtifactId(newDependencyArtifact
									.getArtifact());
							neededSave = true;
						}

						if (!newDependencyArtifact.getVersion().equals(
								dep.getVersion())) {
							dep.setVersion(newDependencyArtifact.getVersion());
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

	private void addDependencies(LocalArtifact projectToChange,
			List<Dependency> dependencies, boolean dryrun) {
		if (dryrun) {
			log.info("(dryrun) I would add dependencies to " + projectToChange);
			return;
		}

		log.info("About to add dependencies to " + projectToChange);

		File pomFile = projectToChange.getLocation();

		ProjectDocument o;
		try {
			o = ProjectDocument.Factory.parse(pomFile, MavenXmlSupport.instance
					.createXmlOptions());

			Model project = o.getProject();

			project.setDependencies(Dependencies.Factory
					.newInstance(MavenXmlSupport.instance.createXmlOptions()));

			for (Dependency dep : dependencies) {

				org.apache.maven.pom.x400.Dependency mvnDep = project
						.getDependencies().addNewDependency();

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
						mvnDep.setExclusions(Exclusions.Factory
								.newInstance(MavenXmlSupport.instance
										.createXmlOptions()));
					}
					Exclusions mvnExes = mvnDep.getExclusions();
					for (String e : dep.getExclusions()) {
						Exclusion mvnEx = mvnExes.addNewExclusion();
						String[] split = e.split(":");
						mvnEx.setGroupId(split[0]);
						mvnEx.setArtifactId(split[1]);
					}
				}

			}

			log.info("Modifying file: " + pomFile);
			o.save(pomFile);

		} catch (XmlException e) {
			log.error("Could not process file: " + pomFileName, e);
		} catch (IOException e) {
			log.error("Could not process file: " + pomFileName, e);
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

	private void findOrSearchArtifacts(List<String> artifactsToFind,
			String typeName, boolean exact,
			Map<String, List<LocalArtifact>> artifactMultiMap) {
		for (String search : artifactsToFind) {
			log.info("Searching " + typeName + ": " + search);
			boolean found = false;
			if (exact) {
				if (artifactMultiMap.containsKey(search)) {
					for (LocalArtifact artifact : artifactMultiMap.get(search)) {
						log.info("  Found: " + artifact + " at "
								+ artifact.getLocation());
						found = true;
					}
				}
			} else {
				for (Entry<String, List<LocalArtifact>> entry : artifactMultiMap
						.entrySet()) {
					if (entry.getKey().contains(search)) {
						for (LocalArtifact artifact : entry.getValue()) {
							log.info("  Found: " + artifact + " at "
									+ artifact.getLocation());
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
						MavenXmlSupport.instance.createXmlOptions());

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

						List<String> exclusions = new LinkedList<String>();

						if (dep.getExclusions() != null
								&& dep.getExclusions().getExclusionArray() != null) {
							for (Exclusion e : dep.getExclusions()
									.getExclusionArray()) {
								exclusions.add(e.getGroupId() + ":"
										+ e.getArtifactId());
							}
						}

						Artifact depArtifact = new Artifact(vars
								.expand(groupId), vars.expand(artifactId), vars
								.expand(version), "jar");
						Dependency dependency = new Dependency(depArtifact,
								artifact, classifier, scope, systemPath,
								exclusions);

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

			try {
				ProjectDocument o = ProjectDocument.Factory.parse(artifact
						.getLocation(), MavenXmlSupport.instance
						.createXmlOptions());

				Model project = o.getProject();
				Map<String, List<Dependency>> result = MavenXmlSupport.instance
						.readDirectDependencyFromLocalArtifact(artifact,
								project);
				for (Entry<String, List<Dependency>> e : result.entrySet()) {
					if (depsAndNeeders.containsKey(e.getKey())) {
						// All our deps to the existing list
						List<Dependency> deps = depsAndNeeders.get(e.getKey());
						deps.addAll(e.getValue());
					} else {
						depsAndNeeders.put(e.getKey(),
								new LinkedList<Dependency>(e.getValue()));
					}
				}

			} catch (XmlException e) {
				log.error("Could not parse maven project: "
						+ artifact.getLocation().getAbsolutePath(), e);

			} catch (IOException e) {
				log.error("Could not parse maven project: "
						+ artifact.getLocation().getAbsolutePath(), e);
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

	private String formatList(Iterable<?> list, String separator) {
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
						MavenXmlSupport.instance.createXmlOptions());

				Model project = o.getProject();
				LocalArtifact artifact = MavenXmlSupport.instance
						.readLocalArtifactFromProject(project, pomFile);
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

	public List<Dependency> readDepsOfPom(String pomFile) {

		List<Dependency> deps = new LinkedList<Dependency>();

		File file = new File(pomFile);
		if (!file.exists() || !file.isFile()) {
			log.error("Maven project file does not exists: "
					+ file.getAbsolutePath());
			return deps;
		}

		try {
			ProjectDocument o = ProjectDocument.Factory.parse(file,
					MavenXmlSupport.instance.createXmlOptions());

			Model project = o.getProject();
			Map<String, List<Dependency>> depsAndDependants = MavenXmlSupport.instance
					.readDirectDependencyFromProject(project, file);

			for (Entry<String, List<Dependency>> e : depsAndDependants
					.entrySet()) {
				for (Dependency dep : e.getValue()) {
					if (!deps.contains(dep.getDependencyArtifact())) {
						deps.add(dep);
					}
				}
			}

			return deps;

		} catch (XmlException e) {
			log.error("Could not read pom file: " + file.getAbsolutePath(), e);
			return deps;
		} catch (IOException e) {
			log.error("Could not read pom file: " + file.getAbsolutePath(), e);
			return deps;
		}

	}

}
