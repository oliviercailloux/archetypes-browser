package io.github.oliviercailloux.archetypes_browser;

import static com.google.common.base.Preconditions.checkArgument;

import java.time.Instant;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Multimaps;

public class ArtifactWithReleases {
	public static ArtifactWithReleases given(ImmutableList<ArtifactRelease> releases) {
		return new ArtifactWithReleases(releases);
	}

	private final Artifact artifact;
	private final ImmutableList<ArtifactRelease> releases;

	/**
	 * Unfortunately, some artifacts in Maven Central have (unexplicably) several
	 * versions with the same release date. See e.g. com.astamuse:asta4d-archetype.
	 * Also, some releases have unknown release date, and it is convenient to use a
	 * single value for all those.
	 */
	private final ImmutableSortedMap<Instant, ImmutableSet<String>> versionsByDate;

	private ArtifactWithReleases(ImmutableList<ArtifactRelease> releases) {
		checkArgument(!releases.isEmpty());
		this.artifact = Objects.requireNonNull(releases.get(0).getArtifact());
		this.releases = Objects.requireNonNull(releases);
		for (ArtifactRelease release : releases) {
			checkArgument(release.getArtifact().equals(artifact));
		}
		final ImmutableSetMultimap.Builder<Instant, String> builder = ImmutableSetMultimap.builder();
		for (ArtifactRelease release : releases) {
			builder.put(release.getRelease(), release.getVersion());
		}
		final Map<Instant, Set<String>> versions = Multimaps.asMap(builder.build());
		final ImmutableSortedMap.Builder<Instant, ImmutableSet<String>> sortedBuilder = ImmutableSortedMap
				.naturalOrder();
		final Set<Entry<Instant, Set<String>>> versionsEntries = versions.entrySet();
		for (Entry<Instant, Set<String>> entry : versionsEntries) {
			sortedBuilder.put(entry.getKey(), ImmutableSet.copyOf(entry.getValue()));
		}
		versionsByDate = sortedBuilder.build();
	}

	public Artifact getArtifact() {
		return artifact;
	}

	public ImmutableList<ArtifactRelease> getReleases() {
		return releases;
	}

	public ImmutableSortedMap<Instant, ImmutableSet<String>> getVersionsByDate() {
		return versionsByDate;
	}
}
