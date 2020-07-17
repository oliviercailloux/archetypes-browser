package io.github.oliviercailloux.archetypes_browser;

import static com.google.common.base.Verify.verify;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.maven.archetype.catalog.Archetype;
import org.apache.maven.archetype.catalog.ArchetypeCatalog;
import org.apache.maven.archetype.catalog.io.xpp3.ArchetypeCatalogXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import io.github.oliviercailloux.archetypes_browser.formats.JsonArtifacts;
import io.github.oliviercailloux.http.Downloader;

public class Fetcher {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Fetcher.class);

	public static final Instant EARLY = Instant.parse("2000-01-01T00:00:00Z");

	public static final Path OUTPUT_PATH = Path.of("Artifacts.json");

	public static void main(String[] args) throws Exception {
		final Fetcher browser = new Fetcher();
		browser.refresh();
		LOGGER.info("Refreshed.");

		final ImmutableSet<ArtifactWithReleases> allWithReleases = browser
				.getAllReleases(Path.of("archetype-catalog.xml"));
		Files.writeString(Fetcher.OUTPUT_PATH, JsonArtifacts.withReleasesToJson(allWithReleases).toString());
	}

	public ImmutableSet<ArtifactWithReleases> getAllReleases(Path catalogPath) {
		final List<Archetype> archetypes;
		try (InputStream is = Files.newInputStream(catalogPath)) {
			final ArchetypeCatalog cat = new ArchetypeCatalogXpp3Reader().read(is);
			archetypes = cat.getArchetypes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		} catch (XmlPullParserException e) {
			throw new IllegalStateException(e);
		}
		final int totalCount = archetypes.size();
		LOGGER.info("Found: {}.", totalCount);

		final ImmutableSet<Artifact> artifacts = archetypes.stream()
				.map(a -> Artifact.given(a.getGroupId(), a.getArtifactId())).collect(ImmutableSet.toImmutableSet());
		LOGGER.info("Read: {}.", artifacts.size());

		final ImmutableSet.Builder<ArtifactWithReleases> withReleasesBuilder = ImmutableSet.builder();
		final ImmutableSet.Builder<Artifact> noReleasesBuilder = ImmutableSet.builder();
		for (Artifact artifact : artifacts) {
			final ImmutableSet<ArtifactRelease> releases;
			releases = getReleases(artifact);
			LOGGER.info("Releases: {}.", releases);
			if (releases.isEmpty()) {
				/**
				 * Some have no release; seems like the catalog has some ghost entries.
				 *
				 * https://repo.maven.apache.org/maven2/com/github/lucarosellini/ lists six
				 * artifacts (not counting alexa and rJava/) but
				 * https://search.maven.org/search?q=g:com.github.lucarosellini lists seven
				 * entries.
				 *
				 * https://repo.maven.apache.org/maven2/com/github/fastcube/factory/tibco/bw/maven/
				 * does not exist, whereas the
				 * https://search.maven.org/search?q=com.github.fastcube.factory.tibco.bw.maven
				 * lists several entries, among which five are in the catalog.
				 */
				LOGGER.info("No release: {}.", artifact);
				noReleasesBuilder.add(artifact);
			} else {
				final ArtifactWithReleases withReleases = ArtifactWithReleases.given(releases);
				withReleasesBuilder.add(withReleases);
			}
		}
		final ImmutableSet<Artifact> noReleases = noReleasesBuilder.build();
		LOGGER.info("Have no releases: {}.", noReleases);

		final ImmutableSet<ArtifactWithReleases> allWithReleases = withReleasesBuilder.build();
		return allWithReleases;
	}

	private void refresh() throws IOException {
		final Downloader downloader = Downloader.saving();
		final Client client = ClientBuilder.newClient();
		final WebTarget target = client.target("https://repo.maven.apache.org/maven2/archetype-catalog.xml");
		downloader.readAndDownload(target);
	}

	private ImmutableSet<ArtifactRelease> getReleases(Artifact artifact) {
		final Client client = ClientBuilder.newClient();
		final WebTarget target = client.target("https://repo.maven.apache.org/maven2/")
				.path(artifact.getGroupIdSlashSeparated()).path("/").path(artifact.getArtifactId()).path("/");
		LOGGER.debug("Querying {}.", target.toString());
		Document doc;
		try (Response response = target.request(MediaType.TEXT_PLAIN).get()) {
			if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
				return ImmutableSet.of();
			}
			final String responseStr = response.readEntity(String.class);
			LOGGER.debug("Response {}.", responseStr);

			org.jsoup.nodes.Document jsoupDoc = Jsoup.parse(responseStr);
			doc = new W3CDom().fromJsoup(jsoupDoc);
		}

		Element docE = doc.getDocumentElement();
		Verify.verify(docE.getTagName().equals("html"));
		final NodeList aNodes = docE.getElementsByTagName("a");
		final Node a0 = aNodes.item(0);
		Verify.verify(a0.getChildNodes().item(0).getTextContent().equals("../"));
		final ImmutableSet.Builder<Node> aVersionNodesBuilder = ImmutableSet.builder();
		int mavenNodes = 0;
		for (int i = 1; i < aNodes.getLength(); ++i) {
			final Node aNode = aNodes.item(i);
			final String href = aNode.getAttributes().getNamedItem("href").getNodeValue();
			/**
			 * Usually, there’s a node maven-metadata.xml, then maven-metadata.xml.md5, then
			 * maven-metadata.xml.sha1, but occasionally there’s up to nine such nodes
			 * (example: org.mule.tools:mule-transport-archetype), and in oldMode, there’s
			 * zero.
			 */
			if (href.startsWith("maven-metadata")) {
				++mavenNodes;
			} else {
				Verify.verify(mavenNodes == 0);
				aVersionNodesBuilder.add(aNode);
			}
		}
		/**
		 * Example of oldMode: com.taobao.itest; dk.jacobve.maven.archetypes;
		 * org.appfuse.
		 *
		 * Example with some unknown dates:
		 * https://repo.maven.apache.org/maven2/com/vaadin/vaadin-archetype-application/.
		 */
		final boolean oldMode = mavenNodes == 0;
		Verify.verify(oldMode || (mavenNodes >= 3 && mavenNodes <= 9));
		final ImmutableSet<Node> aVersionNodes = aVersionNodesBuilder.build();
		final Pattern datePattern = Pattern.compile(" *([0-9-]+ [0-9][0-9]:[0-9][0-9]) *- *\n");
		final Pattern unknownDatePattern = Pattern.compile(" *- *- *\n[ \\t]*");
		final ImmutableSet.Builder<ArtifactRelease> releasesBuilder = ImmutableSet.builder();
		for (Node aVersion : aVersionNodes) {
			final NamedNodeMap attributes = aVersion.getAttributes();
			final String href = attributes.getNamedItem("href").getNodeValue();
			final Node titleItem = attributes.getNamedItem("title");
			if (titleItem == null) {
				Verify.verify(oldMode);
			} else {
				final String title = titleItem.getNodeValue();
				Verify.verify(href.equals(title), href);
			}
			final NodeList childNodes = aVersion.getChildNodes();
			Verify.verify(childNodes.getLength() == 1);
			final Node inner = childNodes.item(0);
			Verify.verify(inner.getNodeValue().equals(href));
			Verify.verify(href.endsWith("/"));
			final String version = href.substring(0, href.length() - 1);
			LOGGER.debug("Version: {}.", version);
			final String dateSpaced = aVersion.getNextSibling().getTextContent();
			final Matcher dateMatcher = datePattern.matcher(dateSpaced);
			final Matcher unknownDateMatcher = unknownDatePattern.matcher(dateSpaced);
			final boolean dateMatched = dateMatcher.matches();
			final boolean unknownDateMatched = unknownDateMatcher.matches();
			Verify.verify(dateMatched || unknownDateMatched, "" + artifact + ", " + dateSpaced);
			final Instant instant;
			if (unknownDateMatched) {
				instant = EARLY;
			} else {
				final String dateText = dateMatcher.group(1);
				final DateTimeFormatter formatter = new DateTimeFormatterBuilder().append(DateTimeFormatter.ISO_DATE)
						.appendLiteral(' ').append(DateTimeFormatter.ISO_LOCAL_TIME).toFormatter();
				final LocalDateTime date = formatter.parse(dateText, LocalDateTime::from);
				LOGGER.debug("Date: {}.", date);
				instant = date.toInstant(ZoneOffset.UTC);
				if (oldMode) {
					Verify.verify(instant.isBefore(Instant.parse("2012-01-01T00:00:00Z")));
				}
			}

			final String description = getDescription(client, artifact, version);

			final ArtifactRelease release = ArtifactRelease.given(artifact, version, description, instant);
			releasesBuilder.add(release);
		}
		return releasesBuilder.build();
	}

	private String getDescription(Client client, Artifact artifact, String version) {
		final WebTarget pomTarget = client.target("https://repo.maven.apache.org/maven2/")
				.path(artifact.getGroupIdSlashSeparated()).path("/").path(artifact.getArtifactId()).path("/")
				.path(version).path(artifact.getArtifactId() + "-" + version + ".pom");
		LOGGER.debug("Getting {}.", pomTarget);
		final String pomStr;
		try (Response response = pomTarget.request(MediaType.TEXT_PLAIN).get()) {
			final boolean notFound = response.getStatus() == Response.Status.NOT_FOUND.getStatusCode();
			/**
			 * noDate does not imply notFound:
			 * https://repo.maven.apache.org/maven2/com/agilejava/docbkx/docbkx-quickstart-archetype/2.0.10/docbkx-quickstart-archetype-2.0.10.pom
			 * exists though no date.
			 *
			 * notFound does not imply noDate:
			 * https://repo.maven.apache.org/maven2/com/github/adminfaces/admin-starter-archetype/1.0.0-RC20/admin-starter-archetype-1.0.0-RC20.pom
			 * does not exist though this release has a date.
			 */
			if (notFound) {
				return "";
			}
			pomStr = response.readEntity(String.class);
		}

		final String description;
		try {
			final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder;
			builder = factory.newDocumentBuilder();
			final Document document = builder.parse(new InputSource(new StringReader(pomStr)));
			final Node rootElement = document.getDocumentElement();
			final NodeList rootChildren = rootElement.getChildNodes();
			final ImmutableSet.Builder<Node> descriptionChildren = ImmutableSet.builder();
			for (int i = 0; i < rootChildren.getLength(); ++i) {
				final Node child = rootChildren.item(i);
				if (child.getNodeName().equalsIgnoreCase("description")) {
					descriptionChildren.add(child);
				}
			}
			final ImmutableSet<Node> descriptions = descriptionChildren.build();
			if (descriptions.isEmpty()) {
				return "";
			}
			verify(descriptions.size() == 1);
			final Node descriptionNode = Iterables.getOnlyElement(descriptions);
			final NodeList descriptionNodeChildren = descriptionNode.getChildNodes();
			verify(descriptionNodeChildren.getLength() == 1);
			final Node descriptionContent = descriptionNodeChildren.item(0);
			verify(descriptionContent.getChildNodes().getLength() == 0);
			verify(descriptionContent.getNodeType() == Node.TEXT_NODE);
			description = descriptionContent.getNodeValue();
		} catch (ParserConfigurationException | SAXException e) {
			throw new IllegalStateException(e);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return description;
	}
}
