package de.tobiasroeser.maven.shared;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.maven.pom.x400.Exclusion;
import org.apache.maven.pom.x400.Model;
import org.apache.maven.pom.x400.Parent;
import org.apache.maven.pom.x400.Model.Dependencies;
import org.apache.xmlbeans.XmlOptions;
import org.jackage.util.VariableExpander;

import de.tobiasroeser.maven.versionupdater.Artifact;
import de.tobiasroeser.maven.versionupdater.Dependency;
import de.tobiasroeser.maven.versionupdater.LocalArtifact;

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

		return new LocalArtifact(vars.expand(groupId), vars.expand(artifactId),
				vars.expand(version), packaging, pomFile);
	}

	
	public Map<String, List<Dependency>> readDirectDependencyFromProject(
			Model project, File pomFile) {

		Map<String, List<Dependency>> depsAndNeeders = new LinkedHashMap<String, List<Dependency>>();

		LocalArtifact artifact = readLocalArtifactFromProject(project, pomFile);

		VariableExpander<String> vars = new VariableExpander<String>();
		vars.addVar("project.groupId", artifact.getGroup());
		vars.addVar("project.artifactId", artifact.getArtifact());
		vars.addVar("project.version", artifact.getVersion());

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
					problems.add("Variable used in groupId (" + groupId + ")");
				}
				if (artifactId.contains("$")) {
					log.debug("Found variable in artifactId: " + artifactId
							+ " -- project " + pomFile);
					problems.add("Variable used in artifactId (" + artifactId
							+ ")");
				}
				if (version.contains("$")) {
					log.debug("Found variable in version: " + version
							+ " -- project " + pomFile);
					problems.add("Variable used in version (" + version + ")");
				}

				List<String> exclusions = new LinkedList<String>();
				
				if(dep.getExclusions() != null && dep.getExclusions().getExclusionArray() != null) {
					for(Exclusion e : dep.getExclusions().getExclusionArray()) {
						exclusions.add(e.getGroupId()+ ":"+e.getArtifactId());
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
				if (depsAndNeeders.containsKey(depArtifact)) {
					dependants = depsAndNeeders.get(depArtifact);
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

		return depsAndNeeders;
	}
	
}
