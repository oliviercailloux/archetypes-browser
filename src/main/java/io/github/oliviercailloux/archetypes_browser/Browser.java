package io.github.oliviercailloux.archetypes_browser;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.apache.maven.plugins.maven_archetype_plugin.archetype_catalog._1_0.Archetype;
import org.apache.maven.plugins.maven_archetype_plugin.archetype_catalog._1_0.ArchetypeCatalog;
import org.apache.maven.plugins.maven_archetype_plugin.archetype_catalog._1_0.ObjectFactory;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.bootstrap.DOMImplementationRegistry;
import org.w3c.dom.ls.DOMImplementationLS;
import org.w3c.dom.ls.LSOutput;
import org.w3c.dom.ls.LSSerializer;

import com.google.common.base.Verify;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import io.github.oliviercailloux.http_utils.Downloader;
import io.github.oliviercailloux.xml_utils.XmlUtils;

public class Browser {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(Browser.class);

	public static String write(Document document) throws Exception {
		DOMImplementationRegistry registry = DOMImplementationRegistry.newInstance();
		DOMImplementationLS impl = (DOMImplementationLS) registry.getDOMImplementation("LS");
		LSSerializer ser = impl.createLSSerializer();
		ser.getDomConfig().setParameter("format-pretty-print", true);
		LSOutput output = impl.createLSOutput();
		StringWriter writer = new StringWriter();
		output.setCharacterStream(writer);
		ser.write(document, output);
		return writer.toString();
	}

	private static final Instant EARLY = Instant.parse("2000-01-01T00:00:00Z");

	public static void main(String[] args) throws Exception {
		final Browser browser = new Browser();
		browser.proceed();
	}

	public void proceed() throws Exception {
		refresh();
		LOGGER.info("Refreshed.");

		/**
		 * The server sends an xml instance document that is entirely unqualified;
		 * whereas the official schema (referenced here:
		 * http://maven.apache.org/archetype/maven-archetype-plugin/specification/archetype-catalog.html
		 * and found here: http://maven.apache.org/xsd/archetype-catalog-1.0.0.xsd)
		 * expects all elements to be qualified. We thus rename every element to qualify
		 * them.
		 */
		final Path catalogPath = Path.of("archetype-catalog.xml");
		final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		final DocumentBuilder builder = factory.newDocumentBuilder();
		final Document doc = builder.parse(Files.newInputStream(catalogPath));
		final String namespaceURI = "http://maven.apache.org/plugins/maven-archetype-plugin/archetype-catalog/1.0.0";
		final List<Node> elements = XmlUtils.asList(doc.getElementsByTagName("*"));
		for (Node element : elements) {
			doc.renameNode(element, namespaceURI, element.getNodeName());
		}
//		final Path outPath = Path.of("out.xml");
//		Files.writeString(outPath, write(doc));

		final JAXBContext context = JAXBContext.newInstance(ObjectFactory.class);
		final Unmarshaller unmarshaller = context.createUnmarshaller();
		final Schema schema = SchemaFactory.newDefaultInstance()
				.newSchema(getClass().getResource("archetype-catalog-1.0.0.xsd"));
		unmarshaller.setSchema(schema);
		final JAXBElement<ArchetypeCatalog> unmarshalled = unmarshaller.unmarshal(doc, ArchetypeCatalog.class);
		final ArchetypeCatalog unmarshalledValue = unmarshalled.getValue();
		final List<Archetype> archetypesReceived = unmarshalledValue.getArchetypes().getArchetype();
		final ImmutableList<Archetype> archetypes = archetypesReceived.stream()
				.collect(ImmutableList.toImmutableList());
		final ImmutableSet.Builder<Artifact> artifactsBuilder = ImmutableSet.builder();
		for (Archetype archetype : archetypes) {
			final String groupId = archetype.getGroupId();
			final String artifactId = archetype.getArtifactId();
			artifactsBuilder.add(Artifact.given(groupId, artifactId));
		}
		final ImmutableSet<Artifact> artifacts = artifactsBuilder.build();

		final ImmutableSet.Builder<ArtifactWithReleases> withReleasesBuilder = ImmutableSet.builder();
		final ImmutableSet.Builder<Artifact> noReleasesBuilder = ImmutableSet.builder();
		for (Artifact artifact : artifacts) {
			final ImmutableList<ArtifactRelease> releases;
			releases = getReleases(artifact);
			LOGGER.info("Releases: {}.", releases);
			if (releases.isEmpty()) {
				noReleasesBuilder.add(artifact);
			} else {
				final ArtifactWithReleases withReleases = ArtifactWithReleases.given(releases);
				withReleasesBuilder.add(withReleases);
			}
		}
		final ImmutableSet<ArtifactWithReleases> allWithReleases = withReleasesBuilder.build();
		final ImmutableSet<Artifact> noReleases = noReleasesBuilder.build();
		LOGGER.info("Have no releases: {}.", noReleases);

		final ImmutableSet<Instant> earliests = allWithReleases.stream()
				.map((a) -> a.getVersionsByDate().keySet().first()).sorted(Comparator.naturalOrder()).distinct()
				.limit(2).collect(ImmutableSet.toImmutableSet());
		Verify.verify(earliests.iterator().next().equals(EARLY));
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

	public void refresh() throws IOException {
		final Downloader downloader = Downloader.saving();
		final Client client = ClientBuilder.newClient();
		final WebTarget target = client.target("https://repo1.maven.org/maven2/archetype-catalog.xml");
		downloader.readAndDownload(target);
	}

	public ImmutableList<ArtifactRelease> getReleases(Artifact artifact) {
		final Client client = ClientBuilder.newClient();
		final WebTarget target = client.target("https://repo1.maven.org/maven2/")
				.path(artifact.getGroupIdSlashSeparated()).path("/").path(artifact.getArtifactId()).path("/");
		LOGGER.debug("Querying {}.", target.toString());
		Document doc;
		try (Response response = target.request(MediaType.TEXT_PLAIN).get()) {
			if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
				return ImmutableList.of();
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
		final ImmutableList.Builder<Node> aVersionNodesBuilder = ImmutableList.builder();
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
		 */
		final boolean oldMode = mavenNodes == 0;
		Verify.verify(oldMode || (mavenNodes >= 3 && mavenNodes <= 9));
		final ImmutableList<Node> aVersionNodes = aVersionNodesBuilder.build();
		final Pattern datePattern = Pattern.compile(" *([0-9-]+ [0-9][0-9]:[0-9][0-9]) *- *\n");
		final Pattern unknownDatePattern = Pattern.compile(" *- *- *\n[ \\t]*");
		final ImmutableList.Builder<ArtifactRelease> releasesBuilder = ImmutableList.builder();
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
			final ArtifactRelease release = ArtifactRelease.given(artifact, version, instant);
			releasesBuilder.add(release);
		}
		return releasesBuilder.build();
	}
}
