package io.github.oliviercailloux.archetypes_browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.github.oliviercailloux.http.Downloader;

class DownloadTests {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(DownloadTests.class);

	@Test
	void test() throws Exception {
		final Client client = ClientBuilder.newClient();
		final WebTarget target = client.target("https://repo1.maven.org/maven2/edu/byu/hbll/java-project/");
		{
			final Downloader downloader = Downloader.discarding();
			downloader.download(target, Instant.parse("2018-03-12T20:05:37Z"));
			assertTrue(downloader.getModified());
			assertEquals(Instant.parse("2018-03-13T20:05:37Z"), downloader.getLastModified().get());
		}
		{
			final Downloader downloader = Downloader.discarding();
			downloader.download(target, Instant.parse("2018-03-14T20:05:37Z"));
			assertFalse(downloader.getModified());
			assertTrue(downloader.getLastModified().isEmpty());
		}
		client.close();
	}

}
