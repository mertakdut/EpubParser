import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class Reader {

	private String epubFilePath;

	// Needed files for .epub format, I might not need these, already giving the
	// user epub contents.
	// private File containerFile; // Xml
	// private File contentFile; // Opf

	private List<String> zipEntryNames;

	public Reader(String filePath) throws IOException {
		this.epubFilePath = filePath;
	}

	public Content getContent(String savingPath) throws IOException, ParserConfigurationException, SAXException,
			IllegalArgumentException, IllegalAccessException, DOMException {
		Content content = new Content();
		zipEntryNames = new ArrayList<>();

		ZipFile zipFile = new ZipFile(this.epubFilePath);

		Enumeration files = zipFile.entries();

		File directory = new File(savingPath);
		directory.mkdir();

		while (files.hasMoreElements()) {
			ZipEntry entry = (ZipEntry) files.nextElement();
			if (!entry.isDirectory()) {
				InputStream is = zipFile.getInputStream(entry);

				if (entry.getName().contains("container.xml")) {
					parseXml(is, content);
				} else if (entry.getName().endsWith(".opf")) {
					parseXml(is, content);
				}

				zipEntryNames.add(entry.getName());
			}
		}

		content.getMetadata().printFields();
		content.getManifest().printXmlItems();

		return content;
	}

	private ArrayList<Map<String, String>> parseXml(InputStream inputStream, Content content)
			throws ParserConfigurationException, IOException, SAXException, IllegalArgumentException,
			IllegalAccessException, DOMException {
		// InputStream inputStream = new FileInputStream(xmlFile);

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder docBuilder = factory.newDocumentBuilder();

		Document document = docBuilder.parse(inputStream);

		inputStream.close();

		if (document.hasChildNodes()) {
			traverseDocumentNodes(document.getChildNodes(), content);
		}

		return null;
	}

	private void traverseDocumentNodes(NodeList nodeList, Content content)
			throws IllegalArgumentException, IllegalAccessException, DOMException {

		for (int i = 0; i < nodeList.getLength(); i++) {

			Node tempNode = nodeList.item(i);

			// make sure it's element node.
			if (tempNode.getNodeType() == Node.ELEMENT_NODE) {

				if (tempNode.getNodeName().equals("metadata")) {
					content.getMetadata().fillAttributes(tempNode.getChildNodes());
				} else if (tempNode.getNodeName().equals("manifest")) {
					content.getManifest().fillXmlItemList(tempNode.getChildNodes());
				}

				// get node name and value
				// System.out.println("\nNode Name =" + tempNode.getNodeName() +
				// " [OPEN]");
				// System.out.println("Node Value =" +
				// tempNode.getTextContent());
				//
				// System.out.println("Node hasChildNodes? =" +
				// tempNode.hasChildNodes());
				// System.out.println("Node childNodes =" +
				// tempNode.getChildNodes() + "\n");

				if (tempNode.hasAttributes()) {
					// get attributes names and values
					NamedNodeMap nodeMap = tempNode.getAttributes();

					for (int j = 0; j < nodeMap.getLength(); j++) {
						Node node = nodeMap.item(j);
						// System.out.println("attr name : " +
						// node.getNodeName());
						// System.out.println("attr value : " +
						// node.getNodeValue());
					}
				}

				if (tempNode.hasChildNodes()) {
					// loop again if has child nodes
					traverseDocumentNodes(tempNode.getChildNodes(), content);
				}

				// System.out.println("Node Name =" + tempNode.getNodeName() + "
				// [CLOSE]");
			}
		}

	}

}
