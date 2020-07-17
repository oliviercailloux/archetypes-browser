package io.github.oliviercailloux.archetypes_browser;

import static com.google.common.base.Preconditions.checkNotNull;

import java.time.Instant;

import com.google.common.base.MoreObjects;

public class ArtifactRelease {
	public static ArtifactRelease given(Artifact artifact, String version, String description, Instant releaseDate) {
		return new ArtifactRelease(artifact, version, description, releaseDate);
	}

	private final Artifact artifact;
	private final String version;
	private final String description;

	private final Instant releaseDate;

	private ArtifactRelease(Artifact artifact, String version, String description, Instant releaseDate) {
		this.artifact = checkNotNull(artifact);
		this.version = checkNotNull(version);
		this.description = checkNotNull(description);
		this.releaseDate = checkNotNull(releaseDate);
	}

	public Artifact getArtifact() {
		return artifact;
	}

	public String getVersion() {
		return version;
	}

	public String getDescription() {
		return description;
	}

	public Instant getReleaseDate() {
		return releaseDate;
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("artifact", artifact).add("version", version)
				.add("description", description).add("instant", releaseDate).toString();
	}
}
