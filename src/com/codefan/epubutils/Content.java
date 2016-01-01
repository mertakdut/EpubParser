package com.codefan.epubutils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.codefan.epubutils.BaseFindings.NavPoint;
import com.codefan.epubutils.BaseFindings.XmlItem;

public class Content {

	private ZipFile epubFile;

	private Container container;
	private Package opfPackage;
	private Toc toc;

	private List<String> entryNames;

	private int playOrder = 0;

	public Content() {
		entryNames = new ArrayList<>();

		container = new Container();
		opfPackage = new Package();
		toc = new Toc();
	}

	// Debug
	public void print() {
		System.out.println("Printing zipEntryNames...\n");

		for (int i = 0; i < entryNames.size(); i++) {
			System.out.println("(" + i + ")" + entryNames.get(i));
		}

		getContainer().print();
		getPackage().print();
		getToc().print();
	}

	public BookSection getNextBookSection() throws ReadingException {
		// String[] entryNameAndLabel = getEntryNameAndLabel(this.playOrder + 1);
		NavPoint navPoint = getNavPoint(this.playOrder++);

		if (navPoint != null) {
			return getBookSection(navPoint, this.playOrder);
		}

		throw new ReadingException("Referenced file not found!" + " (playOrder: " + (this.playOrder - 1) + ")");
	}

	public BookSection getPrevBookSection() throws ReadingException {
		// String[] entryNameAndLabel = getEntryNameAndLabel(this.playOrder - 1);
		NavPoint navPoint = getNavPoint(this.playOrder--);

		if (navPoint != null) {
			return getBookSection(navPoint, this.playOrder);
		}

		throw new ReadingException("Referenced file not found!" + " (playOrder: " + (this.playOrder - 1) + ")");
	}

	public BookSection getBookSection(int index) throws ReadingException {
		// String[] entryNameAndLabel = getEntryNameAndLabel(index);
		NavPoint navPoint = getNavPoint(index);

		if (navPoint != null) {
			return getBookSection(navPoint, index);
		}

		throw new ReadingException("Referenced file not found!" + " (playOrder: " + index + ")");
	}

	private String[] findEntryNameAndLabel(NavPoint navPoint) throws ReadingException {
		if (navPoint.getContentSrc() != null) {
			return new String[] { navPoint.getContentSrc(), navPoint.getNavLabel() };
		} else { // Find from id
			List<XmlItem> xmlItemList = getPackage().getManifest().getXmlItemList();
			for (int j = 0; j < xmlItemList.size(); j++) {
				Map<String, String> attributeMap = xmlItemList.get(j).getAttributes();

				String id = attributeMap.get("id");

				if (id.equals(navPoint.getId())) {
					return new String[] { attributeMap.get("href"), navPoint.getNavLabel() };
				}
			}
		}

		throw new ReadingException("NavPoint is not found in epub content.");
	}

	private NavPoint getNavPoint(int index) throws ReadingException {
		if (index >= 0) {
			if (getToc() != null) {
				List<NavPoint> navPoints = getToc().getNavMap().getNavPoints();

				if (index >= navPoints.size()) {
					throw new ReadingException("Index is greater (or equal) than TOC (Term of Contents) size");
				}

				return navPoints.get(index);
			}
		} else {
			throw new ReadingException("Index can't be less than 0");
		}

		return null;
	}

	private BookSection getBookSection(NavPoint navPoint, int index) throws ReadingException {
		BookSection bookSection = new BookSection();

		String[] entryNameAndLabel = findEntryNameAndLabel(navPoint);

		String href = entryNameAndLabel[0];
		String label = entryNameAndLabel[1];

		String currentAnchor = null;
		String nextAnchor = null;

		for (int i = 0; i < getEntryNames().size(); i++) {
			String entryName = getEntryNames().get(i);

			int lastSlashIndex = entryName.lastIndexOf("/");
			String fileName = entryName.substring(lastSlashIndex + 1);

			if (href.contains(fileName)) { // href actually exists.

				if (!href.equals(fileName)) { // Anchored, e.g. www.gutenberg.org@files@19033@19033-h@19033-h-0.htm#pgepubid00058
					currentAnchor = href.replace(fileName, "");

					if (getToc().getNavMap().getNavPoints().size() > index + 1) {
						NavPoint nextNavPoint = getNavPoint(index + 1);
						String[] nextEntryLabel = findEntryNameAndLabel(nextNavPoint);

						String nextHref = nextEntryLabel[0];

						if (nextHref != null) {
							if (nextHref.contains(fileName)) { // Both anchors are in the same file.
								nextAnchor = nextHref.replace(fileName, "");
							}
						}
					}
				}

				ZipEntry zipEntry = epubFile.getEntry(entryName);

				InputStream inputStream;
				try {
					inputStream = epubFile.getInputStream(zipEntry);

					BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
					StringBuilder fileContent = new StringBuilder();

					String line;
					while ((line = bufferedReader.readLine()) != null) {
						fileContent.append(line);
					}

					// epubFile.close();
					bufferedReader.close();

					String fileContentStr = replaceLinkedWithActualCss(fileContent.toString());

					if (nextAnchor != null) { // Trying to split them by anchors, kinda risky.
						currentAnchor = convertAnchorToHtml(currentAnchor);
						nextAnchor = convertAnchorToHtml(nextAnchor);

						boolean containsCurrentAnchor = fileContentStr.contains(currentAnchor);
						boolean containsNextAnchor = fileContentStr.contains(nextAnchor);

						if (containsCurrentAnchor && containsNextAnchor) {
							fileContentStr = getAnchoredPart(fileContentStr, currentAnchor, nextAnchor);
						} else {
							if (containsCurrentAnchor) { // Next anchor not found.
								getToc().getNavMap().getNavPoints().remove(++index); // Delete the second one (next anchor)
							} else if (containsNextAnchor) { // Current anchor not found.
								getToc().getNavMap().getNavPoints().remove(index++); // Delete the first one (current anchor)
								currentAnchor = nextAnchor;
							}

							// Containining anchor should be next starting point.
							while (index < getToc().getNavMap().getNavPoints().size()) { // Looping untill next anchor is found.
								NavPoint possiblyNextNavPoint = getNavPoint(index);
								String[] possiblyNextEntryNameLabel = findEntryNameAndLabel(possiblyNextNavPoint);

								String possiblyNextEntryName = possiblyNextEntryNameLabel[0];

								if (possiblyNextEntryName != null) {
									if (possiblyNextEntryName.contains(fileName)) {
										String anchor = possiblyNextEntryName.replace(fileName, "");
										anchor = convertAnchorToHtml(anchor);

										if (fileContentStr.contains(anchor)) {
											nextAnchor = anchor;
											break;
										}
									}
								}

								getToc().getNavMap().getNavPoints().get(index).setMarkedToDelete(true);

								index++;
							}

							for (Iterator<NavPoint> iterator = getToc().getNavMap().getNavPoints().iterator(); iterator.hasNext();) {
								NavPoint navPointToDelete = iterator.next();
								if (navPointToDelete.isMarkedToDelete()) {
									iterator.remove();
								}
							}

							fileContentStr = getAnchoredPart(fileContentStr, currentAnchor, nextAnchor);
						}
					}

					String extension = null;

					int dotIndex = fileName.lastIndexOf('.');
					if (dotIndex != -1) {
						extension = fileName.substring(0, dotIndex);
					}

					String mediaType = getMediaType(fileName);

					bookSection.setSectionContent(fileContentStr);
					bookSection.setExtension(extension);
					bookSection.setLabel(label);
					bookSection.setMediaType(mediaType);
				} catch (IOException e) {
					e.printStackTrace();
					throw new ReadingException("IOException while reading " + zipEntry + ": " + e.getMessage());
				}

				return bookSection;
			}
		}

		throw new ReadingException("Referenced file not found!");
	}

	// starts from current anchor, reads until the next anchor starts.
	private String getAnchoredPart(String htmlContent, String currentAnchor, String nextAnchor) throws ReadingException {

		int startOfBody = htmlContent.indexOf(Constants.TAG_BODY_START);
		int endOfBody = htmlContent.indexOf(Constants.TAG_BODY_END);

		if (startOfBody != -1 && endOfBody != -1) {
			String htmlBody = htmlContent.substring(startOfBody, endOfBody + Constants.TAG_BODY_END.length());

			int startOfCurrentAnchor = htmlBody.indexOf(currentAnchor);
			int startOfNextAnchor = htmlBody.indexOf(nextAnchor);

			if (startOfCurrentAnchor != -1 && startOfNextAnchor != -1) {

				while (htmlBody.charAt(startOfCurrentAnchor) != '<') {
					startOfCurrentAnchor--;
				}

				while (htmlBody.charAt(startOfNextAnchor) != '<') {
					startOfNextAnchor--;
				}

				String trimmedPart = htmlBody.substring(startOfCurrentAnchor, startOfNextAnchor);

				htmlContent.replace(htmlBody, trimmedPart);
				return htmlContent;
			} else {
				throw new ReadingException("Exception while trimming anchored parts : Defined Anchors not found.");
			}
		} else {
			throw new ReadingException("Exception while trimming anchored parts : Html body tags not found.");
		}
	}

	private String convertAnchorToHtml(String anchor) throws ReadingException { // #Page_1 to id="Page_1" converter
		if (anchor.startsWith("#")) { // Anchors should start with #
			return "id=\"" + anchor.substring(1) + "\"";
		} else {
			throw new ReadingException("Anchor does not start with #");
		}
	}

	private String getMediaType(String fileName) {

		List<XmlItem> manifestItems = getPackage().getManifest().getXmlItemList();

		for (int i = 0; i < manifestItems.size(); i++) {
			if (manifestItems.get(i).getAttributes().containsValue(fileName)) {
				if (manifestItems.get(i).getAttributes().containsKey("media-type")) {
					return manifestItems.get(i).getAttributes().get("media-type");
				}
			}
		}

		return null;
	}

	private String[] getCssHrefAndLinkPart(String htmlContent) {
		int indexOfLinkStart = htmlContent.indexOf("<link");

		if (indexOfLinkStart != -1) {
			int indexOfLinkEnd = htmlContent.indexOf("/>", indexOfLinkStart);

			String linkStr = htmlContent.substring(indexOfLinkStart, indexOfLinkEnd + 2);

			int indexOfHrefStart = linkStr.indexOf("href=\"");
			int indexOfHrefEnd = linkStr.indexOf("\"", indexOfHrefStart + 6);

			String cssHref = linkStr.substring(indexOfHrefStart + 6, indexOfHrefEnd);

			if (cssHref.endsWith(".css")) {
				return new String[] { cssHref, linkStr };
			}
		}

		return null;
	}

	private String replaceLinkedWithActualCss(String htmlContent) throws ReadingException {

		// <link rel="stylesheet" type="text/css" href="docbook-epub.css"/>

		String[] cssHrefAndLinkPart = getCssHrefAndLinkPart(htmlContent);

		while (cssHrefAndLinkPart != null) {

			String cssHref = cssHrefAndLinkPart[0];
			String linkPart = cssHrefAndLinkPart[1];

			for (int i = 0; i < getEntryNames().size(); i++) {
				String entryName = getEntryNames().get(i);

				int lastSlashIndex = entryName.lastIndexOf("/");
				String fileName = entryName.substring(lastSlashIndex + 1);

				if (cssHref.contains(fileName)) { // css exists.
					ZipEntry zipEntry = epubFile.getEntry(entryName);
					InputStream zipEntryInputStream;
					try {
						zipEntryInputStream = epubFile.getInputStream(zipEntry);

						BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(zipEntryInputStream));
						StringBuilder fileContent = new StringBuilder();

						fileContent.append("<style type=\"text/css\">");

						String line;
						while ((line = bufferedReader.readLine()) != null) {
							fileContent.append(line);
						}

						bufferedReader.close();

						fileContent.append("</style>");

						htmlContent = htmlContent.replace(linkPart, fileContent.toString());

						cssHrefAndLinkPart = getCssHrefAndLinkPart(htmlContent);

					} catch (IOException e) {
						e.printStackTrace();
						throw new ReadingException("IOException while reading " + cssHref + " file: " + e.getMessage());
					}
					break;
				}
			}
		}

		return htmlContent;
	}

	public List<String> getEntryNames() {
		return entryNames;
	}

	public void addEntryName(String zipEntryName) {
		entryNames.add(zipEntryName);
	}

	public Container getContainer() {
		return container;
	}

	public Package getPackage() {
		return opfPackage;
	}

	public Toc getToc() {
		return toc;
	}

	public ZipFile getEpubFile() {
		return epubFile;
	}

	public void setEpubFile(ZipFile epubFile) {
		this.epubFile = epubFile;
	}

}