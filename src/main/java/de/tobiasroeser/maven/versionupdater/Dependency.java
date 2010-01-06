package de.tobiasroeser.maven.versionupdater;

public class Dependency {

	private final Artifact dependencyArtifact;
	private final LocalArtifact project;
	private final boolean system;
	private final String classifier;
	private final String systemPath;

	public Dependency(Artifact dependencyArtifact, LocalArtifact project,
			String classifier, boolean system, String systemPath) {
		this.dependencyArtifact = dependencyArtifact;
		this.project = project;
		this.classifier = classifier;
		this.system = system;
		this.systemPath = systemPath;
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

	public boolean isSystem() {
		return system;
	}

	public String getSystemPath() {
		return systemPath;
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
