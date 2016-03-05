package com.codefan.epubutils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.NamedNodeMap;
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
		// Required Terms
		private String title;
		private String language;
		private String identifier;

		// Optional Terms
		private String creator;
		private String contributor;
		private String publisher;
		private String[] subject;
		private String description;
		private String date;
		private String type;
		private String format;
		private String source;
		private String relation;
		private String coverage;
		private String rights;
		private String coverImageId;

		public String getRights() {
			return rights;
		}

		public String getIdentifier() {
			return identifier;
		}

		public String getContributor() {
			return contributor;
		}

		public String getCreator() {
			return creator;
		}

		public String getTitle() {
			return title;
		}

		public String getLanguage() {
			return language;
		}

		public String[] getSubjects() {
			return subject;
		}

		public String getDescription() {
			return description;
		}

		public String getPublisher() {
			return publisher;
		}

		public String getDate() {
			return date;
		}

		public String getType() {
			return type;
		}

		public String getFormat() {
			return format;
		}

		public String getSource() {
			return source;
		}

		public String getRelation() {
			return relation;
		}

		public String getCoverage() {
			return coverage;
		}

		public String getCoverImageId() {
			return coverImageId;
		}

		void setCoverImageId(String coverImageId) {
			this.coverImageId = coverImageId;
		}

		public void fillAttributes(NodeList nodeList) throws ReadingException {
			Field[] fields = Package.Metadata.class.getDeclaredFields();

			List<String> subjectList = null;

			for (int i = 0; i < nodeList.getLength(); i++) {
				Node node = nodeList.item(i);

				if (node.getNodeValue() != null && node.getNodeValue().matches("\\s+")) {
					continue;
				}

				if (node.getNodeName().equals("meta")) {
					if (node.hasAttributes()) {
						NamedNodeMap nodeMap = node.getAttributes();

						boolean isCoverImageNodeFound = false;
						for (int j = 0; j < nodeMap.getLength(); j++) {
							Node attribute = nodeMap.item(j);

							if (!isCoverImageNodeFound && attribute.getNodeName().equals("name") && attribute.getNodeValue().equals("cover")) { // This node states cover-image id.
								isCoverImageNodeFound = true;
								j = -1; // Start the search from the beginng to find 'content' value.
							} else if (isCoverImageNodeFound && attribute.getNodeName().equals("content")) {
								this.coverImageId = attribute.getNodeValue();
								break;
							}

						}
					}
				}

				for (int j = 0; j < fields.length; j++) {
					if (nodeList.item(i).getNodeName().contains(fields[j].getName())) {

						if (fields[j].getName().equals("subject")) {
							if (subjectList == null) {
								subjectList = new ArrayList<>();
							}
							subjectList.add(nodeList.item(i).getTextContent());
						} else {
							fields[j].setAccessible(true);

							try {
								fields[j].set(this, nodeList.item(i).getTextContent());
								break;
							} catch (IllegalArgumentException | IllegalAccessException e) {
								e.printStackTrace();
								throw new ReadingException("Exception while parsing " + Constants.FILE_NAME_PACKAGE_OPF + " content: " + e.getMessage());
							}
						}
					}
				}
			}

			if (subjectList != null) {
				Field field;
				try {
					field = Package.Metadata.class.getDeclaredField("subject");
					field.setAccessible(true);
					field.set(this, subjectList.toArray(new String[subjectList.size()]));
				} catch (IllegalArgumentException | IllegalAccessException | NegativeArraySizeException | NoSuchFieldException | SecurityException e) {
					e.printStackTrace();
					throw new ReadingException("Exception while parsing subjects " + Constants.FILE_NAME_PACKAGE_OPF + " content: " + e.getMessage());
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
			System.out.println("subject: " + getSubjects());
			System.out.println("description: " + getDescription());
			System.out.println("date: " + getDate());
			System.out.println("type: " + getType());
			System.out.println("format: " + getFormat());
			System.out.println("source: " + getSource());
			System.out.println("relation: " + getRelation());
			System.out.println("coverage: " + getCoverage());
			System.out.println("rights: " + getRights());

			System.out.println("coverImageHref: " + coverImageId);
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

		public void print() {
			System.out.println("\n\nPrinting Manifest...\n");

			for (int i = 0; i < xmlItemList.size(); i++) {
				XmlItem xmlItem = xmlItemList.get(i);

				System.out.println("xmlItem(" + i + ")" + ": value:" + xmlItem.getValue() + " attributes: " + xmlItem.getAttributes());
			}
		}
	}

	// <b>Ordered</b> Term of Contents, mostly filled with ids of application/xhtml+xml files in manifest node.
	public class Spine {
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

	public class Guide {
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
