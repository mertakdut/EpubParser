package com.github.mertakdut;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.github.mertakdut.BaseFindings.XmlItem;
import com.github.mertakdut.exception.OutOfPagesException;
import com.github.mertakdut.exception.ReadingException;

public class Reader {

	private Content content;

	public void setInfoContent(String filePath) throws ReadingException {
		this.content = new Content();
		this.content.setZipFilePath(filePath);

		fillContent(filePath, false);
	}

	public void setFullContent(String filePath) throws ReadingException {
		this.content = new Content();
		this.content.setZipFilePath(filePath);

		fillContent(filePath, true);
	}

	public BookSection readSection(int index) throws ReadingException, OutOfPagesException {
		return content.getBookSection(index);
	}

	public BookSection readSection(int index, int maxContentPerSection) throws ReadingException, OutOfPagesException {
		Optionals.maxContentPerSection = maxContentPerSection;
		return content.getBookSection(index);
	}

	// Optionals
	public void setMaxContentPerSection(int maxContentPerSection) {
		Optionals.maxContentPerSection = maxContentPerSection;
	}

	public void setCssStatus(CssStatus cssStatus) {
		Optionals.cssStatus = cssStatus;
	}

	public void setIsIncludingTextContent(boolean isIncludingTextContent) {
		Optionals.isIncludingTextContent = isIncludingTextContent;
	}

	private Content fillContent(String zipFilePath, boolean isFullContent) throws ReadingException {

		ZipFile epubFile = null;
		try {
			try {
				epubFile = new ZipFile(zipFilePath);
			} catch (Exception e) {
				e.printStackTrace();
				throw new ReadingException("Error initializing ZipFile: " + e.getMessage());
			}

			Enumeration<? extends ZipEntry> files = epubFile.entries();

			while (files.hasMoreElements()) {
				ZipEntry entry = (ZipEntry) files.nextElement();
				if (!entry.isDirectory()) {
					String entryName = entry.getName();

					if (entryName != null) {
						content.addEntryName(entryName);
					}
				}
			}

			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setNamespaceAware(false);
			factory.setValidating(false);
			try {
				factory.setFeature("http://xml.org/sax/features/namespaces", false);
				factory.setFeature("http://xml.org/sax/features/validation", false);
				factory.setFeature("http://apache.org/xml/features/nonvalidating/load-dtd-grammar", false);
				factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
			} catch (ParserConfigurationException e) {
				e.printStackTrace();
				// throw new ReadingException("Error initializing DocumentBuilderFactory: " + e.getMessage());
			}

			DocumentBuilder docBuilder;

			try {
				docBuilder = factory.newDocumentBuilder();
			} catch (ParserConfigurationException e) {
				e.printStackTrace();
				throw new ReadingException("DocumentBuilder cannot be created: " + e.getMessage());
			}

			boolean isContainerXmlFound = false;
			boolean isTocXmlFound = false;

			for (int i = 0; i < content.getEntryNames().size(); i++) {

				if (isContainerXmlFound && (isTocXmlFound || !isFullContent)) {
					break;
				}

				String currentEntryName = content.getEntryNames().get(i);

				if (currentEntryName.contains("container.xml")) {
					isContainerXmlFound = true;

					ZipEntry container = epubFile.getEntry(currentEntryName);

					InputStream inputStream;
					try {
						inputStream = epubFile.getInputStream(container);
					} catch (IOException e) {
						e.printStackTrace();
						throw new ReadingException("IOException while reading " + Constants.FILE_NAME_CONTAINER_XML + " file: " + e.getMessage());
					}

					Document document = getDocument(docBuilder, inputStream, Constants.FILE_NAME_CONTAINER_XML);
					parseContainerXml(docBuilder, document, content, epubFile);
				} else if (isFullContent && currentEntryName.contains(".ncx")) {
					isTocXmlFound = true;

					ZipEntry toc = epubFile.getEntry(currentEntryName);

					InputStream inputStream;
					try {
						inputStream = epubFile.getInputStream(toc);
					} catch (IOException e) {
						e.printStackTrace();
						throw new ReadingException("IOException while reading " + Constants.FILE_NAME_TOC_NCX + " file: " + e.getMessage());
					}

					Document document = getDocument(docBuilder, inputStream, Constants.FILE_NAME_TOC_NCX);
					parseTocFile(document, content.getToc());
				}
			}

			if (!isContainerXmlFound) {
				throw new ReadingException("container.xml not found.");
			}

			if (!isTocXmlFound && isFullContent) {
				throw new ReadingException("toc.ncx not found.");
			}

			mergeTocElements();

			// Debug
			 content.print();

			return content;

		} finally {
			try {
				if (epubFile != null) {
					epubFile.close();
				}
			} catch (IOException e) {
				e.printStackTrace();
				throw new ReadingException("Error closing ZipFile: " + e.getMessage());
			}
		}
	}

	private void parseContainerXml(DocumentBuilder docBuilder, Document document, Content content, ZipFile epubFile) throws ReadingException {
		if (document.hasChildNodes()) {
			traverseDocumentNodesAndFillContent(document.getChildNodes(), content.getContainer());
		}

		String opfFilePath = content.getContainer().getFullPathValue();
		ZipEntry opfFileEntry = epubFile.getEntry(opfFilePath);

		InputStream opfFileInputStream;
		try {
			opfFileInputStream = epubFile.getInputStream(opfFileEntry);
		} catch (IOException e) {
			e.printStackTrace();
			throw new ReadingException("IO error while reading " + Constants.FILE_NAME_PACKAGE_OPF + " inputstream: " + e.getMessage());
		}

		Document packageDocument = getDocument(docBuilder, opfFileInputStream, Constants.FILE_NAME_PACKAGE_OPF);
		parseOpfFile(packageDocument, content.getPackage());
	}

	private void parseOpfFile(Document document, Package pckage) throws ReadingException {
		if (document.hasChildNodes()) {
			traverseDocumentNodesAndFillContent(document.getChildNodes(), pckage);
		}
	}

	private void parseTocFile(Document document, Toc toc) throws ReadingException {
		if (document.hasChildNodes()) {
			traverseDocumentNodesAndFillContent(document.getChildNodes(), toc);
		}
	}

	private Document getDocument(DocumentBuilder docBuilder, InputStream inputStream, String fileName) throws ReadingException {
		Document document;
		try {
			document = docBuilder.parse(inputStream);
			inputStream.close();
			return document;
		} catch (Exception e) {
			e.printStackTrace();
			throw new ReadingException("Parse error while parsing " + fileName + " file: " + e.getMessage());
		}
	}

	private void traverseDocumentNodesAndFillContent(NodeList nodeList, BaseFindings findings) throws ReadingException {

		for (int i = 0; i < nodeList.getLength(); i++) {
			Node tempNode = nodeList.item(i);

			// make sure it's element node.
			if (tempNode.getNodeType() == Node.ELEMENT_NODE) {
				findings.fillContent(tempNode);

				if (tempNode.hasChildNodes()) {
					// loop again if has child nodes
					traverseDocumentNodesAndFillContent(tempNode.getChildNodes(), findings);
				}
			}
		}
	}

	private void mergeTocElements() throws ReadingException {

		List<NavPoint> currentNavPoints = new ArrayList<>(content.getToc().getNavMap().getNavPoints());

		int navPointIndex = 0; // Holds the last duplicate content position, when the new content found insertion is done from that position.
		int insertedNavPointCount = 0;

		for (XmlItem spine : content.getPackage().getSpine().getXmlItemList()) {

			Map<String, String> spineAttributes = spine.getAttributes();

			String idRef = spineAttributes.get("idref");

			for (XmlItem manifest : content.getPackage().getManifest().getXmlItemList()) {

				Map<String, String> manifestAttributes = manifest.getAttributes();

				String manifestElementId = manifestAttributes.get("id");

				if (idRef.equals(manifestElementId)) {

					NavPoint navPoint = new NavPoint();
					// navPoint.setPlayOrder(currentNavPoints.size() + spineNavPoints.size() + 1); // Is playOrder needed? I think not because we've already sorted the navPoints with playOrder before
					// merging.
					navPoint.setContentSrc(ContextHelper.encodeToUtf8(ContextHelper.getTextAfterCharacter(manifestAttributes.get("href"), Constants.SLASH)));

					boolean duplicateContentSrc = false;
					boolean isAnchoredFound = false;

					for (int j = 0; j < currentNavPoints.size(); j++) {

						NavPoint navPointItem = currentNavPoints.get(j);

						if (navPoint.getContentSrc().equals(navPointItem.getContentSrc())) {
							duplicateContentSrc = true;
							navPointIndex = j;
							break;
						} else if (!isAnchoredFound && navPoint.getContentSrc().startsWith(navPointItem.getContentSrc())
								&& navPoint.getContentSrc().replace(navPointItem.getContentSrc(), "").startsWith("%23")) {
							isAnchoredFound = true;
							navPointIndex = j;
						} else if (!isAnchoredFound && navPointItem.getContentSrc().startsWith(navPoint.getContentSrc())
								&& navPointItem.getContentSrc().replace(navPoint.getContentSrc(), "").startsWith("%23")) {
							isAnchoredFound = true;
							navPointIndex = j;
						}

					}

					if (!duplicateContentSrc) {
						content.getToc().getNavMap().getNavPoints().add(navPointIndex + insertedNavPointCount++, navPoint);
					}

				}

			}
		}

	}

	public Package getInfoPackage() {
		return content.getPackage();
	}

	public byte[] getCoverImage() throws ReadingException {

		if (content != null) {
			return content.getCoverImage();
		}

		throw new ReadingException("Content info is not set.");
	}

}
