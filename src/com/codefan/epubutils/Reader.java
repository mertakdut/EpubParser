package com.codefan.epubutils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Reader {

	private int maxContentPerSection;
	private CssStatus cssStatus = CssStatus.INCLUDE;
	private boolean isIncludingTextContent;

	public Content getContent(String filePath) throws ReadingException {
		Content content = new Content();
		content.setZipFilePath(filePath);

		return getContent(filePath, content);
	}

	public Content getContent(File file) throws ReadingException {
		Content content = new Content();
		content.setZipFilePath(file.getPath());

		return getContent(file.getPath(), content);
	}

	private Content getContent(String zipFilePath, Content content) throws ReadingException {

		ZipFile epubFile = null;
		try {
			try {
				epubFile = new ZipFile(zipFilePath);
			} catch (IOException e) {
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

				if (isContainerXmlFound && isTocXmlFound) {
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
				} else if (currentEntryName.contains(".ncx")) {
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

			if (!isTocXmlFound) {
				throw new ReadingException("toc.ncx not found.");
			}

			// Debug
//			content.print();

			return content;

		} finally {
			try {
				epubFile.close();
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
		} catch (SAXException e) {
			e.printStackTrace();
			throw new ReadingException("Parse error while parsing " + fileName + " file: " + e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			throw new ReadingException("IO error while parsing/closing " + fileName + " file: " + e.getMessage());
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

	public BookSection readSection(Content content, int index) throws ReadingException {
		return content.getBookSection(index, this.maxContentPerSection, this.cssStatus, this.isIncludingTextContent);
	}

	public BookSection readSection(Content content, int index, int maxContentPerSection) throws ReadingException {
		return content.getBookSection(index, maxContentPerSection, this.cssStatus, this.isIncludingTextContent);
	}

	public void setMaxContentPerSection(int maxContentPerSection) {
		this.maxContentPerSection = maxContentPerSection;
	}
	
	public void setCssStatus(CssStatus cssStatus){
		this.cssStatus = cssStatus;
	}

	public boolean isIncludingTextContent() {
		return isIncludingTextContent;
	}

	public void setIsIncludingTextContent(boolean isIncludingTextContent) {
		this.isIncludingTextContent = isIncludingTextContent;
	}

}
