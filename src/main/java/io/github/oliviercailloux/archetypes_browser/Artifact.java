package io.github.oliviercailloux.archetypes_browser;

import java.util.Objects;

import com.google.common.base.MoreObjects;

public class Artifact {
	public static Artifact given(String groupId, String artifactId) {
		return new Artifact(groupId, artifactId);
	}

	private final String groupId;
	private final String artifactId;

	private Artifact(String groupId, String artifactId) {
		this.groupId = Objects.requireNonNull(groupId);
		this.artifactId = Objects.requireNonNull(artifactId);
	}

	public String getGroupId() {
		return groupId;
	}

	public String getGroupIdSlashSeparated() {
		return groupId.replace('.', '/');
	}

	public String getArtifactId() {
		return artifactId;
	}

	@Override
	public boolean equals(Object o2) {
		if (!(o2 instanceof Artifact)) {
			return false;
		}
		final Artifact a2 = (Artifact) o2;
		return groupId.equals(a2.groupId) && artifactId.equals(a2.artifactId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(groupId, artifactId);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("groupId", groupId).add("artifactId", artifactId).toString();
	}
}
