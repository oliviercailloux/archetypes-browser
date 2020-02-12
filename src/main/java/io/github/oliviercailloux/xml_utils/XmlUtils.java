package io.github.oliviercailloux.xml_utils;

import java.util.AbstractList;
import java.util.List;
import java.util.RandomAccess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class XmlUtils {
	@SuppressWarnings("unused")
	private static final Logger LOGGER = LoggerFactory.getLogger(XmlUtils.class);

	private XmlUtils() {
	}

	public static List<Node> asList(NodeList n) {
		return new NodeListWrapper(n);
	}

	public static List<Element> asElements(NodeList n) {
		return new NodeListToElementsWrapper(n);
	}

	public static void show(NodeList childNodes) {
		for (int i = 0; i < childNodes.getLength(); ++i) {
			final Node node = childNodes.item(i);
			LOGGER.info("Node type {}, Local {}, NS {}, Value {}, Name {}.", node.getNodeType(), node.getLocalName(),
					node.getNamespaceURI(), node.getNodeValue(), node.getNodeName());
		}
	}

	private static class NodeListWrapper extends AbstractList<Node> implements RandomAccess {
		private final NodeList delegate;

		NodeListWrapper(NodeList l) {
			delegate = l;
		}

		@Override
		public Node get(int index) {
			return delegate.item(index);
		}

		@Override
		public int size() {
			return delegate.getLength();
		}
	}

	private static class NodeListToElementsWrapper extends AbstractList<Element> implements RandomAccess {
		private final NodeList delegate;

		NodeListToElementsWrapper(NodeList l) {
			delegate = l;
		}

		@Override
		public Element get(int index) {
			return (Element) delegate.item(index);
		}

		@Override
		public int size() {
			return delegate.getLength();
		}
	}
}