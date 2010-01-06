package de.tobiasroeser.maven.versionupdater;

import java.io.File;

public class LocalArtifact extends Artifact {

	private final File location;

	public LocalArtifact(String group, String artifact, String version,
			String packaging, File location) {
		super(group, artifact, version, packaging);
		if (location == null) {
			throw new IllegalArgumentException("Location must not be null");
		}
		this.location = location;
	}

	public File getLocation() {
		return location;
	}

}
