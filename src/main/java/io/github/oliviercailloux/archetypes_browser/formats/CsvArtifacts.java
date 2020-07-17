package io.github.oliviercailloux.archetypes_browser.formats;

import java.io.StringWriter;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.univocity.parsers.csv.CsvWriter;
import com.univocity.parsers.csv.CsvWriterSettings;

import io.github.oliviercailloux.archetypes_browser.ArtifactRelease;
import io.github.oliviercailloux.archetypes_browser.ArtifactWithReleases;

public class CsvArtifacts {
	public static String toCsv(Set<ArtifactRelease> releases) {
		final StringWriter stringWriter = new StringWriter();
		final CsvWriter writer = new CsvWriter(stringWriter, new CsvWriterSettings());
		writer.writeHeaders("groupId", "artifactId", "version", "release date");
		for (ArtifactRelease release : releases) {
			writer.addValue("groupId", release.getArtifact().getGroupId());
			writer.addValue("artifactId", release.getArtifact().getArtifactId());
			writer.addValue("version", release.getVersion());
			writer.addValue("release date",
					DateTimeFormatter.BASIC_ISO_DATE.format(release.getReleaseDate().atZone(ZoneOffset.UTC)));
			writer.writeValuesToRow();
		}
		writer.close();
		return stringWriter.toString();
	}

	public static String toCsvGrouped(Set<ArtifactWithReleases> artifacts) {
		final StringWriter stringWriter = new StringWriter();
		final CsvWriter writer = new CsvWriter(stringWriter, new CsvWriterSettings());
		writer.writeHeaders("groupId", "artifactId", "latest description", "earliest release (real) date",
				"latest release (real) date");
		for (ArtifactWithReleases artifact : artifacts) {
			if (artifact.getVersionsByRealDate().isEmpty()) {
				continue;
			}
			writer.addValue("groupId", artifact.getArtifact().getGroupId());
			writer.addValue("artifactId", artifact.getArtifact().getArtifactId());
			final ImmutableSet<ArtifactRelease> latestReleases = artifact.getReleasesByDate().lastEntry().getValue();
			final ImmutableSet<String> latestDescriptions = latestReleases.stream().map(ArtifactRelease::getDescription)
					.collect(ImmutableSet.toImmutableSet());
			final String latestDescription = latestDescriptions.size() == 1
					? Iterables.getOnlyElement(latestDescriptions)
					: latestDescriptions.toString();
			writer.addValue("latest description", latestDescription);
			writer.addValue("earliest release (real) date", DateTimeFormatter.BASIC_ISO_DATE
					.format(artifact.getVersionsByRealDate().firstKey().atZone(ZoneOffset.UTC)));
			writer.addValue("latest release (real) date", DateTimeFormatter.BASIC_ISO_DATE
					.format(artifact.getVersionsByRealDate().lastKey().atZone(ZoneOffset.UTC)));
			writer.writeValuesToRow();
		}
		writer.close();
		return stringWriter.toString();
	}
}
