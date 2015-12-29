package com.codefan.epubutils;

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

	private class Metadata {
		// Required Terms
		private XmlItem title;
		private XmlItem language;
		private XmlItem identifier;

		// Optional Terms
		private XmlItem creator;
		private XmlItem contributor;
		private XmlItem publisher;
		private XmlItem subject;
		private XmlItem description;
		private XmlItem date;
		private XmlItem type;
		private XmlItem format;
		private XmlItem source;
		private XmlItem relation;
		private XmlItem coverage;
		private XmlItem rights;

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

		public XmlItem getDate() {
			return date;
		}

		public XmlItem getType() {
			return type;
		}

		public XmlItem getFormat() {
			return format;
		}

		public XmlItem getSource() {
			return source;
		}

		public XmlItem getRelation() {
			return relation;
		}

		public XmlItem getCoverage() {
			return coverage;
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

		public void print() {
			System.out.println("\n\nPrinting Metadata...\n");

			System.out.println("title: " + (getTitle() != null ? getTitle().getValue() : null));
			System.out.println("language: " + (getLanguage() != null ? getLanguage().getValue() : null));
			System.out.println("identifier: " + (getIdentifier() != null ? getIdentifier().getValue() : null));

			System.out.println("creator: " + (getCreator() != null ? getCreator().getValue() : null));
			System.out.println("contributor: " + (getContributor() != null ? getContributor().getValue() : null));
			System.out.println("publisher: " + (getPublisher() != null ? getPublisher().getValue() : null));
			System.out.println("subject: " + (getSubject() != null ? getSubject().getValue() : null));
			System.out.println("description: " + (getDescription() != null ? getDescription().getValue() : null));
			System.out.println("date: " + (getDate() != null ? getDate().getValue() : null));
			System.out.println("type: " + (getType() != null ? getType().getValue() : null));
			System.out.println("format: " + (getFormat() != null ? getFormat().getValue() : null));
			System.out.println("source: " + (getSource() != null ? getSource().getValue() : null));
			System.out.println("relation: " + (getRelation() != null ? getRelation().getValue() : null));
			System.out.println("coverage: " + (getCoverage() != null ? getCoverage().getValue() : null));
			System.out.println("rights: " + (getRights() != null ? getRights().getValue() : null));
		}
	}

	class Manifest {
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

		public void print() {
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
	class Spine {
		private List<XmlItem> xmlItemList;

		public Spine() {
			this.xmlItemList = new ArrayList<>();
		}

		public void fillXmlItemList(NodeList nodeList) {
			this.xmlItemList = nodeListToXmlItemList(nodeList);
		}

		public List<XmlItem> getXmlItemList() {
			return this.xmlItemList;
		}

		public void print() {
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

		// public List<XmlItem> getXmlItemList() {
		// return this.xmlItemList;
		// }

		public void print() {
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
			getSpine().fillXmlItemList(node.getChildNodes());
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

	public void print() throws IllegalArgumentException, IllegalAccessException {
		getMetadata().print();
		getManifest().print();
		getSpine().print();
		getGuide().print();
	}
}
