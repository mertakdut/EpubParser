import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.codefan.epubutils.findings.BaseFindings;
import com.codefan.epubutils.findings.Content;

public class Reader {

	private ZipFile zipFile;

	public Reader(String filePath) throws IOException {
		this.zipFile = new ZipFile(filePath);
	}

	public Reader(File file) throws IOException {
		this.zipFile = new ZipFile(file.getPath());
	}

	public Content getContent() throws IOException, ParserConfigurationException, SAXException,
			IllegalArgumentException, IllegalAccessException, DOMException {
		Content content = new Content();

		Enumeration files = zipFile.entries();

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
		DocumentBuilder docBuilder = factory.newDocumentBuilder();

		boolean isContainerXmlFound = false;
		boolean isTocXmlFound = false;

		for (int i = 0; i < content.getEntryNames().size(); i++) {
			String currentEntryName = content.getEntryNames().get(i);

			if (currentEntryName.contains("container.xml")) {
				isContainerXmlFound = true;

				ZipEntry container = zipFile.getEntry(currentEntryName);
				InputStream inputStream = zipFile.getInputStream(container);

				parseContainerXml(inputStream, content, docBuilder);

				break;
			}
		}

		for (int i = 0; i < content.getEntryNames().size(); i++) {
			String currentEntryName = content.getEntryNames().get(i);

			if (currentEntryName.contains("toc.ncx")) {
				isTocXmlFound = true;

				ZipEntry container = zipFile.getEntry(currentEntryName);
				InputStream inputStream = zipFile.getInputStream(container);

				parseTocFile(inputStream, content, docBuilder);

				break;
			}
		}

		if (!isContainerXmlFound) {
			throw new IOException("container.xml not found.");
		}

		if (!isTocXmlFound) {
			throw new IOException("toc.ncx not found.");
		}

		content.printZipEntryNames();
		content.getPackage().print();
		content.getToc().print();

		return content;
	}

	private void parseContainerXml(InputStream inputStream, Content content, DocumentBuilder docBuilder)
			throws IOException, IllegalArgumentException, IllegalAccessException, DOMException, SAXException {
		Document document = docBuilder.parse(inputStream);

		inputStream.close();

		if (document.hasChildNodes()) {
			traverseDocumentNodes(document.getChildNodes(), content.getContainer());
		}

		String opfFilePath = content.getContainer().getFullPathValue();
		ZipEntry entry = zipFile.getEntry(opfFilePath);

		parseOpfFile(zipFile.getInputStream(entry), content, docBuilder);
	}

	private void parseOpfFile(InputStream inputStream, Content content, DocumentBuilder docBuilder)
			throws IOException, IllegalArgumentException, IllegalAccessException, DOMException, SAXException {
		Document document = docBuilder.parse(inputStream);

		inputStream.close();

		if (document.hasChildNodes()) {
			traverseDocumentNodes(document.getChildNodes(), content.getPackage());
		}
	}

	private void parseTocFile(InputStream inputStream, Content content, DocumentBuilder docBuilder)
			throws IOException, SAXException, IllegalArgumentException, IllegalAccessException, DOMException {
		Document document = docBuilder.parse(inputStream);

		inputStream.close();

		if (document.hasChildNodes()) {
			traverseDocumentNodes(document.getChildNodes(), content.getToc());
		}
	}

	private void traverseDocumentNodes(NodeList nodeList, BaseFindings findings)
			throws IllegalArgumentException, IllegalAccessException, DOMException {

		for (int i = 0; i < nodeList.getLength(); i++) {

			Node tempNode = nodeList.item(i);

			// make sure it's element node.
			if (tempNode.getNodeType() == Node.ELEMENT_NODE) {
				findings.fillContent(tempNode);

				if (tempNode.hasChildNodes()) {
					// loop again if has child nodes
					traverseDocumentNodes(tempNode.getChildNodes(), findings);
				}
			}
		}
	}

}
