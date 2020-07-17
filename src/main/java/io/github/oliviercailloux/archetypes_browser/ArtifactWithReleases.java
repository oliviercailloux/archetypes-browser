package io.github.oliviercailloux.archetypes_browser;

import static com.google.common.base.Preconditions.checkArgument;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Multimaps;

public class ArtifactWithReleases {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(ArtifactWithReleases.class);

	public static ArtifactWithReleases given(Set<ArtifactRelease> releases) {
		return new ArtifactWithReleases(releases);
	}

	public static ImmutableSet<ArtifactWithReleases> separated(Collection<ArtifactRelease> releases) {
		final ImmutableSetMultimap<Artifact, ArtifactRelease> separated = releases.stream()
				.collect(ImmutableSetMultimap.toImmutableSetMultimap(ArtifactRelease::getArtifact, r -> r));
		return Multimaps.asMap(separated).values().stream().map(ArtifactWithReleases::given)
				.collect(ImmutableSet.toImmutableSet());
	}

	private final Artifact artifact;
	private final ImmutableSet<ArtifactRelease> releases;

	/**
	 * Unfortunately, some artifacts in Maven Central have (unexplicably) several
	 * versions with the same release date. See e.g. com.astamuse:asta4d-archetype.
	 * Also, some releases have unknown release date, and it is convenient to use a
	 * single value for all those.
	 */
	private final ImmutableSortedMap<Instant, ImmutableSet<String>> versionsByDate;

	private ArtifactWithReleases(Set<ArtifactRelease> releases) {
		checkArgument(!releases.isEmpty());
		this.artifact = releases.stream().map(ArtifactRelease::getArtifact).distinct()
				.collect(MoreCollectors.onlyElement());
		this.releases = ImmutableSet.copyOf(releases);

		final ImmutableSetMultimap.Builder<Instant, String> builder = ImmutableSetMultimap.builder();
		for (ArtifactRelease release : releases) {
			builder.put(release.getReleaseDate(), release.getVersion());
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

	public ImmutableSet<ArtifactRelease> getReleases() {
		return releases;
	}

	public ImmutableSortedMap<Instant, ImmutableSet<ArtifactRelease>> getReleasesByDate() {
		final ImmutableSetMultimap<Instant, ArtifactRelease> multi = releases.stream()
				.collect(ImmutableSetMultimap.toImmutableSetMultimap(ArtifactRelease::getReleaseDate, a -> a));
		final Map<Instant, Set<ArtifactRelease>> asMap = Multimaps.asMap(multi);
		return multi.keySet().stream().collect(ImmutableSortedMap.toImmutableSortedMap(Comparator.naturalOrder(),
				i -> i, i -> ImmutableSet.copyOf(asMap.get(i))));
	}

	public ImmutableSortedMap<Instant, ImmutableSet<String>> getVersionsByDate() {
		return versionsByDate;
	}

	public ImmutableSortedMap<Instant, ImmutableSet<String>> getVersionsByRealDate() {
		/**
		 * Can’t make it an ImmutableSortedMap<Instant, ImmutableSet<String>>, because
		 * some versions are published at exactly the same date, it seems.
		 *
		 * Examples:
		 * https://repo.maven.apache.org/maven2/org/pustefixframework/pustefix-archetype-basic/,
		 * versions 0.15.24 and 0.16.9 have both been released at 2011-04-18 10:19;
		 * https://repo.maven.apache.org/maven2/org/scala-tools/archetypes/scala-archetype-simple/
		 * has versions 1.0, 1.1 and 1.2 released at the same moment.
		 */
		return ImmutableSortedMap.copyOf(Maps.filterKeys(versionsByDate, i -> !i.equals(Fetcher.EARLY)));
	}
}
