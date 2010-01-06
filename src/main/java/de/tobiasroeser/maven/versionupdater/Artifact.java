package de.tobiasroeser.maven.versionupdater;

public class Artifact {

	private final String group;
	private final String artifact;
	private final String version;
	private final String packaging;

	public Artifact(String group, String artifact, String version,
			String packaging) {
		if (group == null) {
			throw new IllegalArgumentException("GroupId must not be null");
		}
		if (artifact == null) {
			throw new IllegalArgumentException("ArtifactId must not be null");
		}
		if (version == null) {
			throw new IllegalArgumentException("Version must not be null");
		}
		this.group = group;
		this.artifact = artifact;
		this.version = version;
		this.packaging = packaging != null ? packaging : "jar";
	}

	public String getGroup() {
		return group;
	}

	public String getArtifact() {
		return artifact;
	}

	public String getVersion() {
		return version;
	}

	@Override
	public String toString() {
		return group + ":" + artifact + ":" + version;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((artifact == null) ? 0 : artifact.hashCode());
		result = prime * result + ((group == null) ? 0 : group.hashCode());
		result = prime * result
				+ ((packaging == null) ? 0 : packaging.hashCode());
		result = prime * result + ((version == null) ? 0 : version.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Artifact other = (Artifact) obj;
		if (artifact == null) {
			if (other.artifact != null)
				return false;
		} else if (!artifact.equals(other.artifact))
			return false;
		if (group == null) {
			if (other.group != null)
				return false;
		} else if (!group.equals(other.group))
			return false;
		if (packaging == null) {
			if (other.packaging != null)
				return false;
		} else if (!packaging.equals(other.packaging))
			return false;
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		return true;
	}

}
