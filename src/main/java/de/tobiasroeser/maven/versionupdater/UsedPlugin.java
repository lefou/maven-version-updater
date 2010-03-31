package de.tobiasroeser.maven.versionupdater;

public class UsedPlugin {

	private final String version;
	private final LocalArtifact usingProject;
	private final String groupId;
	private final String artifactId;

	public UsedPlugin(String groupId, String artifactId, String version,
			LocalArtifact usingProject) {
		this.groupId = groupId != null && !groupId.equals("") ? groupId
				: "org.apacge.maven.plugins";
		this.artifactId = artifactId;
		this.version = version;
		this.usingProject = usingProject;
	}

	@Override
	public String toString() {
		return groupId + ":" + artifactId
				+ (version != null ? ":" + version : "") + " @ " + usingProject;
	}

}
