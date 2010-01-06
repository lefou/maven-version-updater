package de.tobiasroeser.maven.versionupdater;

import java.io.File;

public class LocalJarArtifact extends LocalArtifact {

	private final File jarLocation;

	public LocalJarArtifact(String group, String artifact, String version,
			File location, File jarLocation) {
		super(group, artifact, version, "jar", location);
		if (jarLocation == null) {
			throw new IllegalArgumentException("JarLocations must not be null");
		}
		this.jarLocation = jarLocation;
	}

	public File getJarLocation() {
		return jarLocation;
	}

}
