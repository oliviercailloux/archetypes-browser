package io.github.oliviercailloux.archetypes_browser.formats;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.bind.adapter.JsonbAdapter;

import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.archetypes_browser.Artifact;
import io.github.oliviercailloux.archetypes_browser.ArtifactRelease;
import io.github.oliviercailloux.archetypes_browser.ArtifactWithReleases;
import io.github.oliviercailloux.json.JsonbUtils;
import io.github.oliviercailloux.json.PrintableJsonObject;

public class JsonArtifacts {
	public static class ArtifactReleaseAdapter implements JsonbAdapter<ArtifactRelease, JsonObject> {
		@Override
		public JsonObject adaptToJson(ArtifactRelease artifactRelease) throws Exception {
			final PrintableJsonObject obj = toJson(artifactRelease.getArtifact());
			final JsonObjectBuilder builder = Json.createObjectBuilder(obj);
			builder.add("version", artifactRelease.getVersion());
			builder.add("description", artifactRelease.getDescription().toString());
			builder.add("releaseDate", artifactRelease.getReleaseDate().toString());
			return builder.build();
		}

		@Override
		public ArtifactRelease adaptFromJson(JsonObject adapted) throws Exception {
			final Artifact artifact = toArtifact(adapted.toString());
			final String version = adapted.getString("version");
			final String description = adapted.getString("description");
			final Instant releaseDate = Instant.parse(adapted.getString("releaseDate"));
			return ArtifactRelease.given(artifact, version, description, releaseDate);
		}
	}

	public static Artifact toArtifact(String json) {
		return JsonbUtils.fromJson(json, Artifact.class);
	}

	public static PrintableJsonObject toJson(Artifact artifact) {
		return JsonbUtils.toJsonObject(artifact);
	}

	public static ArtifactRelease toArtifactRelease(String json) {
		return JsonbUtils.fromJson(json, ArtifactRelease.class, new ArtifactReleaseAdapter());
	}

	public static PrintableJsonObject toJson(ArtifactRelease artifactRelease) {
		return JsonbUtils.toJsonObject(artifactRelease, new ArtifactReleaseAdapter());
	}

	public static List<ArtifactRelease> toArtifactReleases(String json) {
		@SuppressWarnings("serial")
		final ArrayList<ArtifactRelease> l = new ArrayList<>() {
			// Empty.
		};
		return JsonbUtils.fromJson(json, l.getClass(), new ArtifactReleaseAdapter());
	}

	public static PrintableJsonObject releasesToJson(List<ArtifactRelease> artifactReleases) {
		return JsonbUtils.toJsonObject(artifactReleases, new ArtifactReleaseAdapter());
	}

	public static ImmutableSet<ArtifactWithReleases> toArtifactWithReleases(String json) {
		final List<ArtifactRelease> releases = toArtifactReleases(json);
		return ArtifactWithReleases.separated(ImmutableSet.copyOf(releases));
	}

	public static PrintableJsonObject withReleasesToJson(Set<ArtifactWithReleases> artifactWithReleases) {
		final ImmutableSet<ArtifactRelease> releases = artifactWithReleases.stream()
				.flatMap(a -> a.getReleases().stream()).collect(ImmutableSet.toImmutableSet());
		return JsonbUtils.toJsonObject(releases, new ArtifactReleaseAdapter());
	}

	public static Set<ArtifactWithReleases> withReleasesFromJson(String json) {
		@SuppressWarnings("all")
		final Set<ArtifactRelease> typeSet = new LinkedHashSet<>() {
		};

		final Set<ArtifactRelease> releases = JsonbUtils.fromJson(json, typeSet.getClass().getGenericSuperclass(),
				new ArtifactReleaseAdapter());
		return ArtifactWithReleases.separated(releases);
	}
}
