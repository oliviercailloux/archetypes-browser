package io.github.oliviercailloux.archetypes_browser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Set;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.archetypes_browser.formats.CsvArtifacts;
import io.github.oliviercailloux.archetypes_browser.formats.JsonArtifacts;

public class Analyzer {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Analyzer.class);

	public static void main(String[] args) throws Exception {
		LOGGER.info("Reading.");
		final Set<ArtifactWithReleases> artifacts = JsonArtifacts
				.withReleasesFromJson(Files.readString(Fetcher.OUTPUT_PATH));
		LOGGER.info("Read.");
		toCsv(artifacts);
		toCsvGrouped(artifacts);
		analyze(artifacts);
	}

	private static void toCsvGrouped(Set<ArtifactWithReleases> artifacts) throws IOException {
		final String csv = CsvArtifacts.toCsvGrouped(artifacts);
		Files.writeString(Path.of("Artifacts grouped.csv"), csv);
	}

	private static void toCsv(Set<ArtifactWithReleases> artifacts) throws IOException {
		final ImmutableSet<ArtifactRelease> all = artifacts.stream().flatMap(a -> a.getReleases().stream())
				.collect(ImmutableSet.toImmutableSet());
		final String csv = CsvArtifacts.toCsv(all);
		Files.writeString(Path.of("Artifacts.csv"), csv);
	}

	private static void analyze(Set<ArtifactWithReleases> allWithReleases) {
		final ImmutableSet<Instant> earliests = allWithReleases.stream()
				.map((a) -> a.getVersionsByDate().keySet().first()).sorted(Comparator.naturalOrder()).distinct()
				.limit(2).collect(ImmutableSet.toImmutableSet());
		Verify.verify(earliests.iterator().next().equals(Fetcher.EARLY));
		LOGGER.info("Earliest with date: {}.", earliests.asList().get(1));

		final Predicate<? super ArtifactWithReleases> isOld = (a) -> a.getVersionsByDate().firstEntry().getKey()
				.atZone(ZoneOffset.UTC).compareTo(Instant.now().atZone(ZoneOffset.UTC).minus(Period.ofYears(5))) < 0;
		final Predicate<? super ArtifactWithReleases> isMaintained = (a) -> a.getVersionsByDate().lastEntry().getKey()
				.atZone(ZoneOffset.UTC).compareTo(Instant.now().atZone(ZoneOffset.UTC).minus(Period.ofYears(1))) > 0;
		final ImmutableList<ArtifactWithReleases> chosen = allWithReleases.stream().filter(isOld).filter(isMaintained)
				.collect(ImmutableList.toImmutableList());
		for (ArtifactWithReleases artifact : chosen) {
			LOGGER.info("Winners: {}.", artifact.getArtifact());
		}
	}
}
