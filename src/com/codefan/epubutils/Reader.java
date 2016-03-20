package com.codefan.epubutils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.codefan.epubutils.BaseFindings.XmlItem;
import com.codefan.epubutils.Package.Metadata;

public class Reader {

	private Content content;

	public void setFullContent(String filePath) throws ReadingException {
		this.content = new Content();
		this.content.setZipFilePath(filePath);

		fillContent(filePath, true);
	}

	public void setFullContent(File file) throws ReadingException {
		content = new Content();
		content.setZipFilePath(file.getPath());

		fillContent(file.getPath(), true);
	}

	public void setInfoContent(String filePath) throws ReadingException {
		this.content = new Content();
		this.content.setZipFilePath(filePath);

		fillContent(filePath, false);
	}

	public void setInfoContent(File file) throws ReadingException {
		content = new Content();
		content.setZipFilePath(file.getPath());

		fillContent(file.getPath(), false);
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
				throw new ReadingException("Error initializing DocumentBuilderFactory: " + e.getMessage());
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

			// Debug
			// content.print();

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

	public Package getInfoPackage() {
		return content.getPackage();
	}

	// TODO: More aggressive searching.
	public byte[] getCoverImage() throws ReadingException {

		if (content != null) {
			Package pckage = content.getPackage();
			Metadata metadata = pckage.getMetadata();

			if (pckage != null && metadata != null) {
				String coverImageId = metadata.getCoverImageId();

				if (coverImageId != null && !coverImageId.equals("")) {
					List<XmlItem> manifestXmlItems = pckage.getManifest().getXmlItemList();

					for (XmlItem xmlItem : manifestXmlItems) {
						if (xmlItem.getAttributes().get("id").equals(coverImageId)) {
							String coverImageEntryName = xmlItem.getAttributes().get("href");

							if (coverImageEntryName != null && !coverImageEntryName.equals("")) {
								ZipFile epubFile = null;
								try {
									try {
										epubFile = new ZipFile(content.getZipFilePath());
									} catch (IOException e) {
										e.printStackTrace();
										throw new ReadingException("Error initializing ZipFile: " + e.getMessage());
									}

									for (String entryName : content.getEntryNames()) {

										if (entryName.contains(coverImageEntryName)) {
											ZipEntry coverImageEntry = epubFile.getEntry(entryName);

											InputStream inputStream;
											try {
												inputStream = epubFile.getInputStream(coverImageEntry);
											} catch (IOException e) {
												e.printStackTrace();
												throw new ReadingException("IOException while reading " + entryName + " file: " + e.getMessage());
											}

											try {
												return ContextHelper.convertIsToByteArray(inputStream);
											} catch (IOException e) {
												e.printStackTrace();
												throw new ReadingException("IOException while converting inputStream to byte array: " + e.getMessage());
											}
										}
									}

								} finally {
									try {
										epubFile.close();
									} catch (IOException e) {
										e.printStackTrace();
										throw new ReadingException("Error closing ZipFile: " + e.getMessage());
									}
								}
							}
						}
					}
				} else {
					throw new ReadingException("Cover image not found in ebook metadata.");
				}

			} else {
				throw new ReadingException("Content is empty. Call setInfoContent or setFullContent methods first.");
			}
		} else {
			throw new ReadingException("Content is empty. Call setInfoContent or setFullContent methods first.");
		}

		throw new ReadingException("Cover image not found.");
	}

	public BookSection readSection(int index) throws ReadingException, OutOfPagesException {
		return content.getBookSection(index);
	}

	public BookSection readSection(int index, int maxContentPerSection) throws ReadingException, OutOfPagesException {
		Optionals.maxContentPerSection = maxContentPerSection;
		return content.getBookSection(index);
	}

	public void setMaxContentPerSection(int maxContentPerSection) {
		Optionals.maxContentPerSection = maxContentPerSection;
	}

	public void setCssStatus(CssStatus cssStatus) {
		Optionals.cssStatus = cssStatus;
	}

	public void setIsIncludingTextContent(boolean isIncludingTextContent) {
		Optionals.isIncludingTextContent = isIncludingTextContent;
	}

}
