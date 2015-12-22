package com.codefan.epubutils.findings;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.w3c.dom.DOMException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

//toc.ncx
public class Toc extends BaseFindings {

	private Head head;
	private NavMap navMap;

	public Toc() {
		head = new Head();
		navMap = new NavMap();
	}

	private class Head {
		private XmlItem uid;
		private XmlItem depth;
		private XmlItem totalPageCount;
		private XmlItem maxPageNumber;

		public void fillAttributes(NodeList nodeList) throws IllegalArgumentException, IllegalAccessException {
			Field[] fields = Toc.Head.class.getDeclaredFields();

			// Burasý olmadý.

			for (int i = 0; i < nodeList.getLength(); i++) {
				for (int j = 0; j < fields.length; j++) {
					NamedNodeMap nodeMap = nodeList.item(i).getAttributes();

					for (int k = 0; k < nodeMap.getLength(); k++) {
						Node attribute = nodeMap.item(k);

						// if
						// (attribute.getNodeValue().contains(fields[j].getName()))
						// {
						// fields[j].setAccessible(true);
						// fields[j].set(this, nodeToXmlItem(nodeList.item(i)));
						// }
					}
				}
			}
		}

		public XmlItem getUid() {
			return uid;
		}

		public XmlItem getDepth() {
			return depth;
		}

		public XmlItem getTotalPageCount() {
			return totalPageCount;
		}

		public XmlItem getMaxPageNumber() {
			return maxPageNumber;
		}

		public void print() {
			System.out.println("\n\nPrinting Head...\n");
			System.out.println("uid: " + (getUid() != null ? getUid().getValue() : null));
			System.out.println("depth: " + (getDepth() != null ? getDepth().getValue() : null));
			System.out.println(
					"totalPageCount: " + (getTotalPageCount() != null ? getTotalPageCount().getValue() : null));
			System.out.println("maxPageNumber: " + (getMaxPageNumber() != null ? getMaxPageNumber().getValue() : null));
		}
	}

	private class NavMap {
		private List<NavPoint> navPoints;

		public NavMap() {
			this.navPoints = new ArrayList<NavPoint>();
		}

		public void fillNavPoints(NodeList possiblyNavPoints) {

			for (int i = 0; i < possiblyNavPoints.getLength(); i++) {

				if (possiblyNavPoints.item(i).getNodeName().equals("navPoint")) {
					NavPoint navPoint = new NavPoint();

					NamedNodeMap nodeMap = possiblyNavPoints.item(i).getAttributes();

					for (int j = 0; j < nodeMap.getLength(); j++) {
						Node attribute = nodeMap.item(j);

						if (attribute.getNodeName().equals("id")) {
							navPoint.setId(attribute.getNodeValue());
						} else if (attribute.getNodeName().equals("playOrder")) {
							navPoint.setPlayOrder(Integer.parseInt(attribute.getNodeValue()));
						}

					}

					NodeList navPointChildNodes = possiblyNavPoints.item(i).getChildNodes();

					for (int k = 0; k < navPointChildNodes.getLength(); k++) {

						Node navPointChild = navPointChildNodes.item(k);

						if (navPointChild.getNodeName().equals("navLabel")) {
							NodeList navLabelChildNodes = navPointChild.getChildNodes();

							for (int l = 0; l < navLabelChildNodes.getLength(); l++) {
								if (navLabelChildNodes.item(l).getNodeName().equals("text")) {
									navPoint.setNavLabel(navLabelChildNodes.item(l).getTextContent());
								}
							}

						} else if (navPointChild.getNodeName().equals("content")) {
							NamedNodeMap contentAttributes = navPointChild.getAttributes();

							for (int m = 0; m < contentAttributes.getLength(); m++) {
								Node contentAttribute = contentAttributes.item(m);

								if (contentAttribute.getNodeName().equals("src")) {
									navPoint.setContentSrc(contentAttribute.getNodeValue());
								}
							}

						}
					}

					this.navPoints.add(navPoint);
				}
			}
		}

		public void print() {
			System.out.println("\n\nPrinting NavPoints...\n");

			for (int i = 0; i < this.navPoints.size(); i++) {
				NavPoint navPoint = this.navPoints.get(i);

				System.out.println("navPoint (" + i + ") id: " + navPoint.getId() + ", playOrder: "
						+ navPoint.getPlayOrder() + ", navLabel(Text): " + navPoint.getNavLabel() + ", content src: "
						+ navPoint.getContentSrc());
			}
		}

	}

	@Override
	public void fillContent(Node node) throws IllegalArgumentException, IllegalAccessException, DOMException {
		if (node.getNodeName().equals("head")) {
			getHead().fillAttributes(node.getChildNodes());
		} else if (node.getNodeName().equals("navMap")) {
			getNavMap().fillNavPoints(node.getChildNodes());
		}
	}

	public Head getHead() {
		return head;
	}

	public NavMap getNavMap() {
		return navMap;
	}

	public void print() {
		getHead().print();
		getNavMap().print();
	}

}
