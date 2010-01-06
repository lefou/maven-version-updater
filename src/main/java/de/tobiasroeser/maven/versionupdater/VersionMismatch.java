/**
 * 
 */
package de.tobiasroeser.maven.versionupdater;

class VersionMismatch {
	private String artifactKey;
	private LocalArtifact localArtifact;
	private Dependency dependency;

	public VersionMismatch(String artifactKey, LocalArtifact localArtifact,
			Dependency dependency) {
		this.artifactKey = artifactKey;
		this.localArtifact = localArtifact;
		this.dependency = dependency;
	}

	public String getArtifactKey() {
		return artifactKey;
	}

	public LocalArtifact getArtifact() {
		return localArtifact;
	}

	public Dependency getDependency() {
		return dependency;
	}

	@Override
	public String toString() {
		return "Version mismatch: local available artifact "
				+ localArtifact + " does not match dependency "
				+ dependency;
	}

}