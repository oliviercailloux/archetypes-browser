package io.github.oliviercailloux.http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.google.common.base.Verify;

public class Downloader {
	private static final DateTimeFormatter FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME.localizedBy(Locale.ENGLISH);

	/** Should be generalized, but ATM I use it only for one purpose. */
	private static final Path FILE_PATH = Path.of("archetype-catalog.xml");

	private static final Path LAST_MODIFIED_PATH = Path.of("archetype-catalog.last-modified.txt");

	public static Downloader discarding() {
		return new Downloader(true);
	}

	public static Downloader saving() {
		return new Downloader(false);
	}

	private Optional<Instant> lastModified;
	private boolean modified;
	private boolean discard;

	private Downloader(boolean discard) {
		lastModified = Optional.empty();
		modified = false;
		this.discard = discard;
	}

	public boolean download(WebTarget target, Instant ifModifiedSince) throws IOException {
		final Invocation.Builder request = target.request(MediaType.TEXT_PLAIN).header("if-modified-since",
				FORMATTER.format(ifModifiedSince.atZone(ZoneOffset.UTC)));
		try (Response response = request.get()) {
			Verify.verify(response.getStatus() == Response.Status.OK.getStatusCode()
					|| response.getStatus() == Response.Status.NOT_MODIFIED.getStatusCode());
			modified = (response.getStatus() == Response.Status.OK.getStatusCode());
			final String lastModifiedStr = response.getHeaderString("last-modified");
			lastModified = Optional.ofNullable(lastModifiedStr).map((r) -> FORMATTER.parse(r, Instant::from));
			if (modified) {
				Verify.verify(lastModified.isPresent() && !lastModified.get().isBefore(ifModifiedSince));
			} else {
				Verify.verify(!lastModified.isPresent() || !lastModified.get().isAfter(ifModifiedSince));
			}
			if (!discard && modified) {
				try (InputStream responseStream = response.readEntity(InputStream.class)) {
					responseStream.transferTo(Files.newOutputStream(FILE_PATH));
				}
				Files.writeString(LAST_MODIFIED_PATH, lastModified.get().toString());
			}
		}
		return modified;
	}

	public boolean download(WebTarget target) throws IOException {
		download(target, Instant.EPOCH);
		Verify.verify(modified);
		return modified;
	}

	public boolean readAndDownload(WebTarget target) throws IOException {
		final Instant ifModifiedSince;
		if (Files.exists(LAST_MODIFIED_PATH)) {
			final String ifModifiedSinceStr = Files.readString(LAST_MODIFIED_PATH);
			ifModifiedSince = Instant.parse(ifModifiedSinceStr);
		} else {
			ifModifiedSince = Instant.EPOCH;
		}
		return download(target, ifModifiedSince);
	}

	public Optional<Instant> getLastModified() {
		return lastModified;
	}

	public boolean getModified() {
		return modified;
	}

	public boolean discards() {
		return discard;
	}
}
