package de.tobiasroeser.maven.versionupdater;

import java.util.Collections;
import java.util.List;

public class Dependency {

	private final Artifact dependencyArtifact;
	private final LocalArtifact project;
	private final boolean system;
	private final String scope;
	private final String classifier;
	private final String systemPath;
	private List<String> exclusions;

	public Dependency(Artifact dependencyArtifact, LocalArtifact project,
			String classifier, String scope, String systemPath,
			List<String> exclusions) {
		this.dependencyArtifact = dependencyArtifact;
		this.project = project;
		this.classifier = classifier;
		this.scope = scope;
		this.system = scope.trim().equals("system");
		this.systemPath = systemPath;
		this.exclusions = exclusions != null ? exclusions : Collections
				.<String>emptyList();
	}

	public Artifact getDependencyArtifact() {
		return dependencyArtifact;
	}

	public LocalArtifact getProject() {
		return project;
	}

	private String changeProtectBecause;

	public void addChangeProtectBecause(String changeProtectBecause) {
		if (this.changeProtectBecause == null) {
			this.changeProtectBecause = changeProtectBecause;
		} else {
			this.changeProtectBecause += ", " + changeProtectBecause;
		}
	}

	public boolean isChangeAllowed() {
		return changeProtectBecause == null;
	}

	public String getChangeProtectBecause() {
		return changeProtectBecause;
	}

	public String getClassifier() {
		return classifier;
	}

	public String getScope() {
		return scope;
	}

	public boolean isSystem() {
		return system;
	}

	public String getSystemPath() {
		return systemPath;
	}

	public List<String> getExclusions() {
		return exclusions;
	}

	@Override
	public String toString() {
		String additionalDepInfo = "";
		if (classifier != null) {
			additionalDepInfo += "classifier=" + classifier + ",";
		}
		if (system) {
			additionalDepInfo += "system=" + systemPath + ",";
		}
		if (additionalDepInfo.endsWith(",")) {
			additionalDepInfo = additionalDepInfo.substring(0,
					additionalDepInfo.length() - 1);
		}
		if (additionalDepInfo.length() > 0) {
			additionalDepInfo = "(" + additionalDepInfo + ")";
		}
		String string = dependencyArtifact + additionalDepInfo
				+ " (required by " + project + ")";
		if (Config.verbose()) {
			return string + " at " + project.getLocation();
		}
		return string;
	}

}
