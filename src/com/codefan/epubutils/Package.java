package com.codefan.epubutils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

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

		public String getRights() {
			return rights != null ? rights.getValue() : null;
		}

		public String getIdentifier() {
			return identifier != null ? identifier.getValue() : null;
		}

		public String getContributor() {
			return contributor != null ? contributor.getValue() : null;
		}

		public String getCreator() {
			return creator != null ? creator.getValue() : null;
		}

		public String getTitle() {
			return title != null ? title.getValue() : null;
		}

		public String getLanguage() {
			return language != null ? language.getValue() : null;
		}

		public String getSubject() {
			return subject != null ? subject.getValue() : null;
		}

		public String getDescription() {
			return description != null ? description.getValue() : null;
		}

		public String getPublisher() {
			return publisher != null ? publisher.getValue() : null;
		}

		public String getDate() {
			return date != null ? date.getValue() : null;
		}

		public String getType() {
			return type != null ? type.getValue() : null;
		}

		public String getFormat() {
			return format != null ? format.getValue() : null;
		}

		public String getSource() {
			return source != null ? source.getValue() : null;
		}

		public String getRelation() {
			return relation != null ? relation.getValue() : null;
		}

		public String getCoverage() {
			return coverage != null ? coverage.getValue() : null;
		}

		public void fillAttributes(NodeList nodeList) throws ReadingException {
			Field[] fields = Package.Metadata.class.getDeclaredFields();

			for (int i = 0; i < nodeList.getLength(); i++) {
				for (int j = 0; j < fields.length; j++) {
					if (nodeList.item(i).getNodeName().contains(fields[j].getName())) {
						fields[j].setAccessible(true);
						try {
							fields[j].set(this, nodeToXmlItem(nodeList.item(i)));
						} catch (IllegalArgumentException | IllegalAccessException e) {
							e.printStackTrace();
							throw new ReadingException("Exception while parsing " + Constants.FILE_NAME_PACKAGE_OPF + " content: " + e.getMessage());
						}
					}
				}
			}
		}

		void print() {
			System.out.println("\n\nPrinting Metadata...\n");

			System.out.println("title: " + getTitle());
			System.out.println("language: " + getLanguage());
			System.out.println("identifier: " + getIdentifier());

			System.out.println("creator: " + getCreator());
			System.out.println("contributor: " + getContributor());
			System.out.println("publisher: " + getPublisher());
			System.out.println("subject: " + getSubject());
			System.out.println("description: " + getDescription());
			System.out.println("date: " + getDate());
			System.out.println("type: " + getType());
			System.out.println("format: " + getFormat());
			System.out.println("source: " + getSource());
			System.out.println("relation: " + getRelation());
			System.out.println("coverage: " + getCoverage());
			System.out.println("rights: " + getRights());
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

				System.out.println("xmlItem(" + i + ")" + ": value:" + xmlItem.getValue() + " attributes: " + xmlItem.getAttributes());
			}
		}
	}

	// <b>Ordered</b> Term of Contents, mostly filled with ids of application/xhtml+xml files in manifest node.
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

				System.out.println("xmlItem(" + i + ")" + ": value:" + xmlItem.getValue() + " attributes: " + xmlItem.getAttributes());
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

		void print() {
			System.out.println("\n\nPrinting Guide...\n");

			for (int i = 0; i < xmlItemList.size(); i++) {
				XmlItem xmlItem = xmlItemList.get(i);

				System.out.println("xmlItem(" + i + ")" + ": value:" + xmlItem.getValue() + " attributes: " + xmlItem.getAttributes());
			}
		}
	}

	@Override
	public void fillContent(Node node) throws ReadingException {
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

	public void print() {
		getMetadata().print();
		getManifest().print();
		getSpine().print();
		getGuide().print();
	}
}
