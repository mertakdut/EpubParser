package com.codefan.epubutils.findings;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.DOMException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

//package.opf
public class Package extends BaseFindings {

	private Metadata metadata;
	private Manifest manifest;
	private Spine spine;
	private Guide guide;

	public Package() {
		metadata = new Metadata();
		manifest = new Manifest();
		spine = new Spine();
		guide = new Guide();
	}

	public class Metadata {
		private XmlItem rights;
		private XmlItem identifier;
		private XmlItem contributor;
		private XmlItem creator;
		private XmlItem title;
		private XmlItem language;
		private XmlItem subject;
		private XmlItem description;
		private XmlItem publisher;

		public XmlItem getRights() {
			return rights;
		}

		public XmlItem getIdentifier() {
			return identifier;
		}

		public XmlItem getContributor() {
			return contributor;
		}

		public XmlItem getCreator() {
			return creator;
		}

		public XmlItem getTitle() {
			return title;
		}

		public XmlItem getLanguage() {
			return language;
		}

		public XmlItem getSubject() {
			return subject;
		}

		public XmlItem getDescription() {
			return description;
		}

		public XmlItem getPublisher() {
			return publisher;
		}

		public void fillAttributes(NodeList nodeList)
				throws IllegalArgumentException, IllegalAccessException, DOMException {
			Field[] fields = Package.Metadata.class.getDeclaredFields();

			for (int i = 0; i < nodeList.getLength(); i++) {
				for (int j = 0; j < fields.length; j++) {
					if (nodeList.item(i).getNodeName().contains(fields[j].getName())) {
						fields[j].setAccessible(true);
						fields[j].set(this, nodeToXmlItem(nodeList.item(i)));
					}
				}
			}

		}

		public void printFields() throws IllegalArgumentException, IllegalAccessException {
			System.out.println("\n\nPrinting Metadata...\n");

			System.out.println("rights: " + (getRights() != null ? getRights().getValue() : null));
			System.out.println("identifier: " + (getIdentifier() != null ? getIdentifier().getValue() : null));
			System.out.println("contributor: " + (getContributor() != null ? getContributor().getValue() : null));
			System.out.println("creator: " + (getCreator() != null ? getCreator().getValue() : null));
			System.out.println("title: " + (getTitle() != null ? getTitle().getValue() : null));
			System.out.println("language: " + (getLanguage() != null ? getLanguage().getValue() : null));
			System.out.println("subject: " + (getSubject() != null ? getSubject().getValue() : null));
			System.out.println("description: " + (getDescription() != null ? getDescription().getValue() : null));
			System.out.println("publisher: " + (getPublisher() != null ? getPublisher().getValue() : null));
		}
	}

	public class Manifest {
		private List<XmlItem> xmlItemList;

		public Manifest() {
			this.xmlItemList = new ArrayList<>();
		}

		public void fillXmlItemList(NodeList nodeList) {
			this.xmlItemList = nodeListToXmlItemList(nodeList);
		}

		public List<XmlItem> getXmlItemList() {
			return this.xmlItemList;
		}

		public void printXmlItems() {
			System.out.println("\n\nPrinting Manifest...\n");

			for (int i = 0; i < xmlItemList.size(); i++) {
				XmlItem xmlItem = xmlItemList.get(i);

				System.out.println("xmlItem(" + i + ")" + ": value:" + xmlItem.getValue() + " attributes: "
						+ xmlItem.getAttributes());
			}
		}
	}

	// <b>Ordered</b> Term of Contents, mostly filled with ids of
	// application/xhtml+xml files in manifest node.
	public class Spine {
		private List<XmlItem> xmlItemList;

		public Spine() {
			this.xmlItemList = new ArrayList<>();
		}

		public void fillXmlItemList(NodeList nodeList, Manifest manifest) {
			for (int i = 0; i < nodeList.getLength(); i++) {
				XmlItem xmlItem = nodeToXmlItem(nodeList.item(i));

				if (xmlItem.getAttributes() != null && xmlItem.getAttributes().containsKey("idref")) {
					String idRef = xmlItem.getAttributes().get("idref");

					// Find the references item inside manifest tag.
					List<XmlItem> manifestXmlItems = manifest.getXmlItemList();

					for (int j = 0; j < manifestXmlItems.size(); j++) {

						if (manifestXmlItems.get(j).getAttributes().containsValue(idRef)) {
							this.xmlItemList.add(manifestXmlItems.get(j));
						}

					}
				}
			}
		}

		public List<XmlItem> getXmlItemList() {
			return this.xmlItemList;
		}

		public void printXmlItems() {
			System.out.println("\n\nPrinting Spine...\n");

			for (int i = 0; i < xmlItemList.size(); i++) {
				XmlItem xmlItem = xmlItemList.get(i);

				System.out.println("xmlItem(" + i + ")" + ": value:" + xmlItem.getValue() + " attributes: "
						+ xmlItem.getAttributes());
			}
		}
	}

	private class Guide {
		private List<XmlItem> xmlItemList;

		public Guide() {
			this.xmlItemList = new ArrayList<>();
		}

		public void fillXmlItemList(NodeList nodeList) {
			this.xmlItemList = nodeListToXmlItemList(nodeList);
		}

		public List<XmlItem> getXmlItemList() {
			return this.xmlItemList;
		}

		public void printXmlItems() {
			System.out.println("\n\nPrinting Guide...\n");

			for (int i = 0; i < xmlItemList.size(); i++) {
				XmlItem xmlItem = xmlItemList.get(i);

				System.out.println("xmlItem(" + i + ")" + ": value:" + xmlItem.getValue() + " attributes: "
						+ xmlItem.getAttributes());
			}
		}
	}

	@Override
	public void fillContent(Node node) throws IllegalArgumentException, IllegalAccessException, DOMException {
		if (node.getNodeName().equals("metadata")) {
			getMetadata().fillAttributes(node.getChildNodes());
		} else if (node.getNodeName().equals("manifest")) {
			getManifest().fillXmlItemList(node.getChildNodes());
		} else if (node.getNodeName().equals("spine")) {
			getSpine().fillXmlItemList(node.getChildNodes(), getManifest());
		} else if (node.getNodeName().equals("guide")) {
			getGuide().fillXmlItemList(node.getChildNodes());
		}
	}

	public Metadata getMetadata() {
		return metadata;
	}

	public Manifest getManifest() {
		return manifest;
	}

	public Spine getSpine() {
		return spine;
	}

	public Guide getGuide() {
		return guide;
	}

	public void printAllContent() throws IllegalArgumentException, IllegalAccessException {
		getMetadata().printFields();
		getManifest().printXmlItems();
		getSpine().printXmlItems();
		getGuide().printXmlItems();
	}
}
