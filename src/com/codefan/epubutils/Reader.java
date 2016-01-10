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

	public Content getContent(String filePath) throws ReadingException {
		Content content = new Content();

		try {
			content.setEpubFile(new ZipFile(filePath));
		} catch (IOException e) {
			e.printStackTrace();
			throw new ReadingException("Error initializing ZipFile: " + e.getMessage());
		}

		return getContent(content);
	}

	public Content getContent(String filePath, int maxContentPerSection) throws ReadingException {
		Content content = new Content();

		try {
			content.setEpubFile(new ZipFile(filePath));
			content.setMaxContentPerSection(maxContentPerSection);
		} catch (IOException e) {
			e.printStackTrace();
			throw new ReadingException("Error initializing ZipFile: " + e.getMessage());
		}

		return getContent(content);
	}

	public Content getContent(File file) throws ReadingException {
		Content content = new Content();

		try {
			content.setEpubFile(new ZipFile(file.getPath()));
		} catch (IOException e) {
			e.printStackTrace();
			throw new ReadingException("Error initializing ZipFile: " + e.getMessage());
		}

		return getContent(content);
	}

	public Content getContent(File file, int maxContentPerSection) throws ReadingException {
		Content content = new Content();

		try {
			content.setEpubFile(new ZipFile(file.getPath()));
			content.setMaxContentPerSection(maxContentPerSection);
		} catch (IOException e) {
			e.printStackTrace();
			throw new ReadingException("Error initializing ZipFile: " + e.getMessage());
		}

		return getContent(content);
	}

	private Content getContent(Content content) throws ReadingException {

		Enumeration<? extends ZipEntry> files = content.getEpubFile().entries();

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

				ZipEntry container = content.getEpubFile().getEntry(currentEntryName);

				InputStream inputStream;
				try {
					inputStream = content.getEpubFile().getInputStream(container);
				} catch (IOException e) {
					e.printStackTrace();
					throw new ReadingException("IOException while reading " + Constants.FILE_NAME_CONTAINER_XML + " file: " + e.getMessage());
				}

				Document document = getDocument(docBuilder, inputStream, Constants.FILE_NAME_CONTAINER_XML);
				parseContainerXml(docBuilder, document, content);
			} else if (currentEntryName.contains(".ncx")) {
				isTocXmlFound = true;

				ZipEntry toc = content.getEpubFile().getEntry(currentEntryName);

				InputStream inputStream;
				try {
					inputStream = content.getEpubFile().getInputStream(toc);
				} catch (IOException e) {
					e.printStackTrace();
					throw new ReadingException("IOException while reading " + Constants.FILE_NAME_TOC_NCX + " file: " + e.getMessage());
				}

				Document document = getDocument(docBuilder, inputStream, Constants.FILE_NAME_TOC_NCX);
				parseTocFile(document, content);
			}
		}

		if (!isContainerXmlFound) {
			throw new ReadingException("container.xml not found.");
		}

		if (!isTocXmlFound) {
			throw new ReadingException("toc.ncx not found.");
		}

		// Debug
		content.print();

		return content;
	}

	private void parseContainerXml(DocumentBuilder docBuilder, Document document, Content content) throws ReadingException {
		if (document.hasChildNodes()) {
			traverseDocumentNodesAndFillContent(document.getChildNodes(), content.getContainer());
		}

		String opfFilePath = content.getContainer().getFullPathValue();
		ZipEntry opfFileEntry = content.getEpubFile().getEntry(opfFilePath);

		InputStream opfFileInputStream;
		try {
			opfFileInputStream = content.getEpubFile().getInputStream(opfFileEntry);
		} catch (IOException e) {
			e.printStackTrace();
			throw new ReadingException("IO error while reading " + Constants.FILE_NAME_PACKAGE_OPF + " inputstream: " + e.getMessage());
		}

		Document packageDocument = getDocument(docBuilder, opfFileInputStream, Constants.FILE_NAME_PACKAGE_OPF);
		parseOpfFile(packageDocument, content);
	}

	private void parseOpfFile(Document document, Content content) throws ReadingException {
		if (document.hasChildNodes()) {
			traverseDocumentNodesAndFillContent(document.getChildNodes(), content.getPackage());
		}
	}

	private void parseTocFile(Document document, Content content) throws ReadingException {
		if (document.hasChildNodes()) {
			traverseDocumentNodesAndFillContent(document.getChildNodes(), content.getToc());
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

}
