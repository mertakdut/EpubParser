package com.codefan.epubutils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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
		private String uid;
		private String depth;
		private String totalPageCount;
		private String maxPageNumber;

		public void fillAttributes(NodeList nodeList) throws ReadingException {
			Field[] fields = Toc.Head.class.getDeclaredFields();

			for (int i = 0; i < nodeList.getLength(); i++) {
				Node possiblyMetaNode = nodeList.item(i);

				if (possiblyMetaNode.getNodeName().equals("meta")) {
					NamedNodeMap attributes = nodeList.item(i).getAttributes();

					for (int k = 0; k < attributes.getLength(); k++) {
						Node attribute = attributes.item(k);

						if (attribute.getNodeName().equals("name")) {

							for (int j = 0; j < fields.length; j++) {
								if (attribute.getNodeValue().contains(fields[j].getName())) {

									// Find content in attributes
									for (int l = 0; l < attributes.getLength(); l++) {
										if (attributes.item(l).getNodeName().equals("content")) {
											fields[j].setAccessible(true);
											try {
												fields[j].set(this, attributes.item(l).getNodeValue());
											} catch (IllegalArgumentException | IllegalAccessException | DOMException e) {
												e.printStackTrace();
												throw new ReadingException("Exception while parsing " + Constants.FILE_NAME_TOC_NCX + " content: " + e.getMessage());
											}
											break;
										}
									}
								}
							}
						}
					}
				}
			}
		}

		public String getUid() {
			return uid;
		}

		public String getDepth() {
			return depth;
		}

		public String getTotalPageCount() {
			return totalPageCount;
		}

		public String getMaxPageNumber() {
			return maxPageNumber;
		}

		public void print() {
			System.out.println("\n\nPrinting Head...\n");
			System.out.println("uid: " + getUid());
			System.out.println("depth: " + getDepth());
			System.out.println("totalPageCount: " + getTotalPageCount());
			System.out.println("maxPageNumber: " + getMaxPageNumber());
		}
	}

	class NavMap {
		private List<NavPoint> navPoints;

		public NavMap() {
			this.navPoints = new ArrayList<NavPoint>();
		}

		public List<NavPoint> getNavPoints() {
			return navPoints;
		}

		public void fillNavPoints(NodeList possiblyNavPoints) {

			for (int i = 0; i < possiblyNavPoints.getLength(); i++) {

				if (possiblyNavPoints.item(i).getNodeName().equals("navPoint") || possiblyNavPoints.item(i).getNodeName().equals("pageTarget")) {
					NavPoint navPoint = new NavPoint();

					NamedNodeMap nodeMap = possiblyNavPoints.item(i).getAttributes();

					for (int j = 0; j < nodeMap.getLength(); j++) {
						Node attribute = nodeMap.item(j);

						if (attribute.getNodeName().equals("id")) {
							navPoint.setId(attribute.getNodeValue());
						} else if (attribute.getNodeName().equals("playOrder")) {
							navPoint.setPlayOrder(Integer.parseInt(attribute.getNodeValue()));
						} else if (attribute.getNodeName().equals("type")) {
							navPoint.setType(attribute.getNodeValue());
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

			Collections.sort(this.navPoints, new Comparator<NavPoint>() {
				public int compare(NavPoint o1, NavPoint o2) {
					return o1.getPlayOrder() <= o2.getPlayOrder() ? -1 : 1; // if equals, first occurence should be sorted as first
				}
			});
		}

		public void print() {
			System.out.println("\n\nPrinting NavPoints...\n");

			for (int i = 0; i < this.navPoints.size(); i++) {
				NavPoint navPoint = this.navPoints.get(i);

				System.out.println("navPoint (" + i + ") id: " + navPoint.getId() + ", playOrder: " + navPoint.getPlayOrder() + ", navLabel(Text): " + navPoint.getNavLabel()
						+ ", content src: " + navPoint.getContentSrc());
			}
		}
	}

	@Override
	public void fillContent(Node node) throws ReadingException {
		if (node.getNodeName().equals("head")) {
			getHead().fillAttributes(node.getChildNodes());
		} else if (node.getNodeName().equals("navMap") || node.getNodeName().equals("pageList")) {
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
