package de.tobiasroeser.maven.shared;

import static de.tototec.utils.functional.FList.filter;
import static de.tototec.utils.functional.FList.flatMap;
import static de.tototec.utils.functional.FList.map;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.maven.pom.x400.Build;
import org.apache.maven.pom.x400.Exclusion;
import org.apache.maven.pom.x400.Model;
import org.apache.maven.pom.x400.Model.Dependencies;
import org.apache.maven.pom.x400.Model.Profiles;
import org.apache.maven.pom.x400.Parent;
import org.apache.maven.pom.x400.Plugin;
import org.apache.maven.pom.x400.Profile;
import org.apache.xmlbeans.XmlOptions;
import org.jackage.util.VariableExpander;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.tobiasroeser.maven.versionupdater.Artifact;
import de.tobiasroeser.maven.versionupdater.Dependency;
import de.tobiasroeser.maven.versionupdater.LocalArtifact;
import de.tobiasroeser.maven.versionupdater.UsedPlugin;
import de.tototec.utils.functional.Optional;
import de.tototec.utils.functional.Tuple2;

public class MavenXmlSupport {

	private final Logger log = LoggerFactory.getLogger(MavenXmlSupport.class);

	private XmlOptions xmlOptions;

	public static MavenXmlSupport instance = new MavenXmlSupport();

	public XmlOptions createXmlOptions() {
		if (xmlOptions == null) {
			final XmlOptions opts = new XmlOptions();
			final Map<String, String> ns = new HashMap<String, String>();
			ns.put("", "http://maven.apache.org/POM/4.0.0");
			opts.setLoadSubstituteNamespaces(ns);
			xmlOptions = opts;
		}
		return xmlOptions;
	}

	public List<UsedPlugin> readUsedPluginsFromProject(final Model project,
			final File pomFile) {
		final List<UsedPlugin> plugins = new LinkedList<UsedPlugin>();

		final LocalArtifact artifact = readLocalArtifactFromProject(project, pomFile);

		final VariableExpander<String> vars = new VariableExpander<String>();
		vars.addVar("project.groupId", artifact.getGroup());
		vars.addVar("project.artifactId", artifact.getArtifact());
		vars.addVar("project.version", artifact.getVersion());

		final Build build = project.getBuild();
		if (build != null) {
			for (final Plugin plugin : build.getPlugins().getPluginArray()) {
				String pGroup = plugin.getGroupId();
				pGroup = pGroup != null ? vars.expand(pGroup.trim()) : null;
				final String pArtifact = vars.expand(plugin.getArtifactId().trim());
				String pVersion = plugin.getVersion();
				pVersion = pVersion != null ? vars.expand(pVersion) : null;

				plugins.add(new UsedPlugin(pGroup, pArtifact, pVersion,
						artifact));
			}
		}

		return plugins;
	}

	public LocalArtifact readLocalArtifactFromProject(final Model project,
			final File pomFile) {
		final VariableExpander<String> vars = new VariableExpander<String>();

		String groupId = project.getGroupId();
		String artifactId = project.getArtifactId();
		String version = project.getVersion();
		String packaging = project.getPackaging();
		packaging = packaging != null ? packaging : "jar";

		final Parent parent = project.getParent();
		if (parent != null) {
			log
					.warn("Maven projects with parents currently not fully supported!. Found one at: "
							+ pomFile);

			vars.addVar("parent.groupId", parent.getGroupId());
			vars.addVar("project.parent.groupId", parent.getGroupId());
			vars.addVar("parent.artifactId", parent.getArtifactId());
			vars.addVar("project.parent.artifactId", parent.getArtifactId());
			vars.addVar("parent.version", parent.getVersion());
			vars.addVar("project.parent.version", parent.getVersion());

			groupId = groupId != null ? groupId : parent.getGroupId();
			artifactId = artifactId != null ? artifactId : parent
					.getArtifactId();
			version = version != null ? version : parent.getVersion();
		}

		return new LocalArtifact(vars.expand(groupId.trim()), vars
				.expand(artifactId.trim()), vars.expand(version.trim()),
				packaging != null ? packaging.trim() : null, pomFile);
	}

	/**
	 * Map(dependency-key:list-of-dependencies).
	 */
	public Map<String, List<Dependency>> readDirectDependencyFromProject(
			final Model project, final File pomFile, final List<String> profiles) {

		final LocalArtifact artifact = readLocalArtifactFromProject(project, pomFile);
		return readDirectDependencyFromLocalArtifact(artifact, project, profiles);
	}

	/**
	 * * @profiles List of included profiles. If the sprecial profile name
	 * <code>"*"</code> (Asterisk) is found, all profiles will be
	 * included.
	 */
	public List<Profile> getProfiles(final Model project, final List<String> filter) {

		final boolean includeAllProfiles = !filter.isEmpty() && filter.contains("*");

		final Profiles xProfiles = project.getProfiles();

		if (xProfiles != null) {
			return filter(xProfiles.getProfileArray(), p -> includeAllProfiles || filter.contains(p.getId()));
		} else {
			return Collections.emptyList();
		}

	}

	/**
	 * Map(dependency-key:list-of-dependencies).
	 *
	 * @profiles List of activated profiles. If the sprecial profile name
	 *           <code>"*"</code> (Asterisk) is found, all profiles will be
	 *           included.
	 */
	public Map<String, List<Dependency>> readDirectDependencyFromLocalArtifact(final LocalArtifact artifact, final Model project,
			final List<String> profiles) {

		final Map<String, List<Dependency>> depsAndNeeders = new LinkedHashMap<String, List<Dependency>>();

		final VariableExpander<String> vars = new VariableExpander<String>();
		vars.addVar("project.groupId", artifact.getGroup());
		vars.addVar("project.artifactId", artifact.getArtifact());
		vars.addVar("project.version", artifact.getVersion());

		final Dependencies dependencies = project.getDependencies();

		final List<Tuple2<Optional<String>, org.apache.maven.pom.x400.Dependency>> deps;
		if (dependencies != null) {
			deps = map(dependencies.getDependencyArray(), d -> Tuple2.of(Optional.<String> none(), d));
		} else {
			deps = Collections.emptyList();
		}

		final List<Tuple2<Optional<String>, org.apache.maven.pom.x400.Dependency>> profileDeps = flatMap(getProfiles(project, profiles), p -> {
			final org.apache.maven.pom.x400.Profile.Dependencies profDeps = p.getDependencies();
			if (profDeps != null) {
				return map(profDeps.getDependencyArray(), (d) -> Tuple2.of(Optional.some(p.getId()), d));
			} else {
				return Collections.emptyList();
			}
		});

		deps.addAll(profileDeps);

		for (final Tuple2<Optional<String>, org.apache.maven.pom.x400.Dependency> depTuple : deps) {

			final Optional<String> profile = depTuple.a();
			final org.apache.maven.pom.x400.Dependency dep = depTuple.b();

			final String groupId = dep.getGroupId().trim();
			final String artifactId = dep.getArtifactId().trim();
			// FIXME: also consult dependencyManagement
			final String version = Optional.lift(dep.getVersion()).map(d -> d.trim()).getOrElse(() -> "");
			String classifier = dep.getClassifier();
			if (classifier != null) {
				classifier = classifier.trim();
			}
			String scope = dep.getScope();
			if (scope == null) {
				scope = "compile";
			} else {
				scope = scope.trim();
			}
			String systemPath = dep.getSystemPath();
			if (systemPath != null) {
				systemPath = systemPath.trim();
			}

			final List<String> problems = new LinkedList<String>();

			if (groupId.contains("$")) {
				log.debug("Found variable in groupId: " + groupId
						+ " -- project " + artifact.getLocation());
				problems.add("Variable used in groupId (" + groupId + ")");
			}
			if (artifactId.contains("$")) {
				log.debug("Found variable in artifactId: " + artifactId
						+ " -- project " + artifact.getLocation());
				problems.add("Variable used in artifactId (" + artifactId
						+ ")");
			}
			if (version.contains("$")) {
				log.debug("Found variable in version: " + version
						+ " -- project " + artifact.getLocation());
				problems.add("Variable used in version (" + version + ")");
			}

			final List<String> exclusions = new LinkedList<String>();

			if (dep.getExclusions() != null
					&& dep.getExclusions().getExclusionArray() != null) {
				for (final Exclusion e : dep.getExclusions().getExclusionArray()) {
					exclusions.add(e.getGroupId().trim() + ":"
							+ e.getArtifactId().trim());
				}
			}

			final Artifact depArtifact = new Artifact(vars.expand(groupId), vars
					.expand(artifactId), vars.expand(version), "jar");
			final Dependency dependency = new Dependency(depArtifact, artifact, profile,
					classifier, scope, systemPath, exclusions);

			for (final String problem : problems) {
				dependency.addChangeProtectBecause(problem);
			}

			final String key = depArtifact.getGroup() + ":"
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

		if (project.getParent() != null) {
			log
					.warn("Maven projects with parents currently not fully supported!. Found one at: "
							+ artifact.getLocation());
		}

		return depsAndNeeders;
	}

}
