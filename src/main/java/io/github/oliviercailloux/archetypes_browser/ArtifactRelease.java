package io.github.oliviercailloux.archetypes_browser;

import java.time.Instant;

import com.google.common.base.MoreObjects;

public class ArtifactRelease {
	public static ArtifactRelease given(Artifact artifact, String version, Instant instant) {
		return new ArtifactRelease(artifact, version, instant);
	}

	private final Artifact artifact;
	private final String version;
	private final Instant instant;

	private ArtifactRelease(Artifact artifact, String version, Instant instant) {
		this.artifact = artifact;
		this.version = version;
		this.instant = instant;
	}

	public Artifact getArtifact() {
		return artifact;
	}

	public String getVersion() {
		return version;
	}

	public Instant getRelease() {
		return instant;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("artifact", artifact).add("version", version)
				.add("instant", instant).toString();
	}
}
