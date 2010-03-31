package de.tobiasroeser.maven.shared;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.maven.pom.x400.Build;
import org.apache.maven.pom.x400.Exclusion;
import org.apache.maven.pom.x400.Model;
import org.apache.maven.pom.x400.Parent;
import org.apache.maven.pom.x400.Plugin;
import org.apache.maven.pom.x400.Model.Dependencies;
import org.apache.xmlbeans.XmlOptions;
import org.jackage.util.VariableExpander;

import de.tobiasroeser.maven.versionupdater.Artifact;
import de.tobiasroeser.maven.versionupdater.Dependency;
import de.tobiasroeser.maven.versionupdater.LocalArtifact;
import de.tobiasroeser.maven.versionupdater.UsedPlugin;

public class MavenXmlSupport {

	private final Log log = LogFactory.getLog(MavenXmlSupport.class);

	private XmlOptions xmlOptions;

	public static MavenXmlSupport instance = new MavenXmlSupport();

	public XmlOptions createXmlOptions() {
		if (xmlOptions == null) {
			XmlOptions opts = new XmlOptions();
			Map<String, String> ns = new HashMap<String, String>();
			ns.put("", "http://maven.apache.org/POM/4.0.0");
			opts.setLoadSubstituteNamespaces(ns);
			xmlOptions = opts;
		}
		return xmlOptions;
	}

	public List<UsedPlugin> readUsedPluginsFromProject(Model project,
			File pomFile) {
		List<UsedPlugin> plugins = new LinkedList<UsedPlugin>();

		LocalArtifact artifact = readLocalArtifactFromProject(project, pomFile);

		VariableExpander<String> vars = new VariableExpander<String>();
		vars.addVar("project.groupId", artifact.getGroup());
		vars.addVar("project.artifactId", artifact.getArtifact());
		vars.addVar("project.version", artifact.getVersion());

		Build build = project.getBuild();
		if (build != null) {
			for (Plugin plugin : build.getPlugins().getPluginArray()) {
				String pGroup = plugin.getGroupId();
				pGroup = pGroup != null ? vars.expand(pGroup.trim()) : null;
				String pArtifact = vars.expand(plugin.getArtifactId().trim());
				String pVersion = plugin.getVersion();
				pVersion = pVersion != null ? vars.expand(pVersion) : null;

				plugins.add(new UsedPlugin(pGroup, pArtifact, pVersion,
						artifact));
			}
		}

		return plugins;
	}

	public LocalArtifact readLocalArtifactFromProject(Model project,
			File pomFile) {
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
			Model project, File pomFile) {

		LocalArtifact artifact = readLocalArtifactFromProject(project, pomFile);
		return readDirectDependencyFromLocalArtifact(artifact, project);
	}

	/**
	 * Map(dependency-key:list-of-dependencies).
	 */
	public Map<String, List<Dependency>> readDirectDependencyFromLocalArtifact(LocalArtifact artifact, Model project) {

		Map<String, List<Dependency>> depsAndNeeders = new LinkedHashMap<String, List<Dependency>>();

		VariableExpander<String> vars = new VariableExpander<String>();
		vars.addVar("project.groupId", artifact.getGroup());
		vars.addVar("project.artifactId", artifact.getArtifact());
		vars.addVar("project.version", artifact.getVersion());

		Dependencies dependencies = project.getDependencies();
		if (dependencies != null) {
			for (org.apache.maven.pom.x400.Dependency dep : dependencies
					.getDependencyArray()) {

				String groupId = dep.getGroupId().trim();
				String artifactId = dep.getArtifactId().trim();
				String version = dep.getVersion().trim();
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

				List<String> problems = new LinkedList<String>();

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

				List<String> exclusions = new LinkedList<String>();

				if (dep.getExclusions() != null
						&& dep.getExclusions().getExclusionArray() != null) {
					for (Exclusion e : dep.getExclusions().getExclusionArray()) {
						exclusions.add(e.getGroupId().trim() + ":"
								+ e.getArtifactId().trim());
					}
				}

				Artifact depArtifact = new Artifact(vars.expand(groupId), vars
						.expand(artifactId), vars.expand(version), "jar");
				Dependency dependency = new Dependency(depArtifact, artifact,
						classifier, scope, systemPath, exclusions);

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
							+ artifact.getLocation());
		}

		return depsAndNeeders;
	}

}
