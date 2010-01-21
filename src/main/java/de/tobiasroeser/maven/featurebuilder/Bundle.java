package de.tobiasroeser.maven.featurebuilder;

import java.io.File;

public class Bundle implements Comparable<Bundle> {

	private final String symbolicName;
	private final String version;
	private final long sizeInB;
	private final File jar;

	public Bundle(String symbolicName, String version, long sizeInB, File jar) {
		this.symbolicName = symbolicName;
		this.version = version;
		this.sizeInB = sizeInB;
		this.jar= jar;
	}

	public String getSymbolicName() {
		return symbolicName;
	}

	public String getVersion() {
		return version;
	}

	public long getSizeInB() {
		return sizeInB;
	}

	public File getJarLocation() {
		return jar;
	}
	
	@Override
	public String toString() {
		return symbolicName + "-" + version;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((symbolicName == null) ? 0 : symbolicName.hashCode());
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
		Bundle other = (Bundle) obj;
		if (symbolicName == null) {
			if (other.symbolicName != null)
				return false;
		} else if (!symbolicName.equals(other.symbolicName))
			return false;
		if (version == null) {
			if (other.version != null)
				return false;
		} else if (!version.equals(other.version))
			return false;
		return true;
	}

	public int compareTo(Bundle o) {
		int diff = getSymbolicName().compareTo(o.getSymbolicName());
		if(diff == 0) {
			// FIXME: poor mans version sorter :-(
			return getVersion().compareTo(o.getVersion()); 
		}
		return diff;
	}
	
}
