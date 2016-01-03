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

import com.codefan.epubutils.BaseFindings.XmlItem;

public class Content {

	private ZipFile epubFile;

	private Container container;
	private Package opfPackage;
	private Toc toc;

	private List<String> entryNames;

	// private int playOrder;

	private int maxContentPerSection; // String length.
	private BookSection lastBookSectionInfo;

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

	// public BookSection getNextBookSection() throws ReadingException {
	// NavPoint navPoint = getNavPoint(this.playOrder++);
	// return prepareBookSection(navPoint, this.playOrder);
	// }
	//
	// public BookSection getPrevBookSection() throws ReadingException {
	// NavPoint navPoint = getNavPoint(this.playOrder--);
	// return prepareBookSection(navPoint, this.playOrder);
	// }

	public BookSection getBookSection(int index) throws ReadingException {
		NavPoint navPoint = getNavPoint(index);

		if (maxContentPerSection == 0 || navPoint.getEntryName() == null) { // Real navPoint - actual file/anchor.
			return prepareBookSection(navPoint, index);
		} else { // Pseudo navPoint - trimmed file entry.
			return prepareTrimmedBookSection(navPoint, index);
		}
	}

	private NavPoint getNavPoint(int index) throws ReadingException {
		if (index >= 0) {
			if (getToc() != null) {
				List<NavPoint> navPoints = getToc().getNavMap().getNavPoints();

				if (index >= navPoints.size()) {
					throw new ReadingException("Index is greater (or equal) than TOC (Term of Contents) size");
				}

				return navPoints.get(index);
			} else {
				throw new ReadingException("Term of Contents is null.");
			}
		} else {
			throw new ReadingException("Index can't be less than 0");
		}
	}

	private BookSection prepareBookSection(NavPoint navPoint, int index) throws ReadingException {
		BookSection bookSection = new BookSection();

		String[] entryNameAndLabel = findEntryNameAndLabel(navPoint);

		String href = entryNameAndLabel[0];
		String label = entryNameAndLabel[1];

		String currentAnchor = null;
		String nextAnchor = null;

		for (int i = 0; i < getEntryNames().size(); i++) {
			String entryName = getEntryNames().get(i);

			String fileName = getFileName(entryName);

			if (href.contains(fileName)) { // href actually exists.

				if (!href.equals(fileName)) { // Anchored, e.g. #pgepubid00058
					currentAnchor = href.replace(fileName, "");
					nextAnchor = getNextAnchor(index, entryName);
				}

				String fileContentStr = readFileContent(entryName);

				fileContentStr = replaceLinkedWithActualCss(fileContentStr);

				if (nextAnchor != null) { // Splitting the file by anchors.
					currentAnchor = convertAnchorToHtml(currentAnchor);
					nextAnchor = convertAnchorToHtml(nextAnchor);

					boolean containsCurrentAnchor = fileContentStr.contains(currentAnchor);
					boolean containsNextAnchor = fileContentStr.contains(nextAnchor);

					if (containsCurrentAnchor && containsNextAnchor) {
						fileContentStr = getAnchorsInterval(fileContentStr, currentAnchor, nextAnchor);
					} else {
						int tmpIndex = index;

						if (containsCurrentAnchor) { // Next anchor not found.
							getToc().getNavMap().getNavPoints().remove(++tmpIndex); // Delete the second one (next anchor)
						} else if (containsNextAnchor) { // Current anchor not found.
							getToc().getNavMap().getNavPoints().remove(tmpIndex++); // Delete the first one (current anchor)
							currentAnchor = nextAnchor;
						}

						int markedNavPoints = 0;

						// Next available anchor should be the next starting point.
						while (tmpIndex < getToc().getNavMap().getNavPoints().size()) { // Looping until next anchor is found.
							NavPoint possiblyNextNavPoint = getNavPoint(tmpIndex);
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

							getToc().getNavMap().getNavPoints().get(tmpIndex).setMarkedToDelete(true);
							markedNavPoints++;

							tmpIndex++;
						}

						if (markedNavPoints != 0) {
							for (Iterator<NavPoint> iterator = getToc().getNavMap().getNavPoints().iterator(); iterator.hasNext();) {
								NavPoint navPointToDelete = iterator.next();
								if (navPointToDelete.isMarkedToDelete()) {
									iterator.remove();

									if (--markedNavPoints == 0) {
										break;
									}
								}
							}
						}

						fileContentStr = getAnchorsInterval(fileContentStr, currentAnchor, nextAnchor);
					}
				}

				String extension = getFileExtension(fileName);
				String mediaType = getMediaType(fileName);

				// If fileContentStr is too long; crop it by the maxContentPerSection.
				// Save the fileContent and position within a new navPoint, insert it after current index.
				if (maxContentPerSection != 0) { // maxContentPerSection is given.
					String htmlBody = getHtmlBody(fileContentStr);

					if (htmlBody.length() > maxContentPerSection) {
						fileContentStr = fileContentStr.replace(htmlBody, htmlBody.substring(0, maxContentPerSection));

						NavPoint entryNavPoint = new NavPoint();

						// entryNavPoint = navPoint;
						entryNavPoint.setEntryName(entryName);
						entryNavPoint.setBodyTrimStartPosition(maxContentPerSection);

						getToc().getNavMap().getNavPoints().add(index + 1, entryNavPoint);

						lastBookSectionInfo = new BookSection();
						lastBookSectionInfo.setExtension(extension);
						lastBookSectionInfo.setLabel(label);
						lastBookSectionInfo.setMediaType(mediaType);
					}
				}

				bookSection.setSectionContent(fileContentStr);
				bookSection.setExtension(extension);
				bookSection.setLabel(label);
				bookSection.setMediaType(mediaType);
				return bookSection;
			}
		}

		throw new ReadingException("Referenced file not found!");
	}

	private BookSection prepareTrimmedBookSection(NavPoint entryNavPoint, int index) throws ReadingException {
		String entryName = entryNavPoint.getEntryName();
		int bodyTrimStartPosition = entryNavPoint.getBodyTrimStartPosition();
		int bodyTrimEndPosition = entryNavPoint.getBodyTrimEndPosition(); // Will be calculated on the first attempt.

		String fileContent = readFileContent(entryName);

		String htmlBody = getHtmlBody(fileContent);
		String htmlBodyToReplace;

		if (bodyTrimEndPosition == 0) { // Not calculated before.
			String nextAnchor = getNextAnchor(index, entryName);

			if (nextAnchor != null) { // Next anchor is available in the same file. It may be the next stop for the content.
				String anchorHtml = convertAnchorToHtml(nextAnchor);

				if (htmlBody.contains(anchorHtml)) {
					int anchorIndex = htmlBody.indexOf(anchorHtml);

					while (htmlBody.charAt(anchorIndex) != '<') { // Getting just before anchor html.
						anchorIndex--;
					}

					htmlBodyToReplace = htmlBody.substring(bodyTrimStartPosition, anchorIndex);
					getToc().getNavMap().getNavPoints().get(index).setBodyTrimEndPosition(anchorIndex);
				} else {
					htmlBodyToReplace = htmlBody.substring(bodyTrimStartPosition);
				}
			} else {
				htmlBodyToReplace = htmlBody.substring(bodyTrimStartPosition);
			}

			if (htmlBodyToReplace.length() > maxContentPerSection) {
				htmlBodyToReplace = htmlBody.substring(bodyTrimStartPosition, bodyTrimStartPosition + maxContentPerSection);

				NavPoint nextEntryNavPoint = new NavPoint();

				nextEntryNavPoint.setEntryName(entryName);
				nextEntryNavPoint.setBodyTrimStartPosition(bodyTrimStartPosition + maxContentPerSection);

				getToc().getNavMap().getNavPoints().get(index).setBodyTrimEndPosition(bodyTrimStartPosition + maxContentPerSection);

				getToc().getNavMap().getNavPoints().add(index + 1, nextEntryNavPoint);
			} else { // There is no need to trim anymore.
				this.lastBookSectionInfo = null;
			}
		} else {
			htmlBodyToReplace = htmlBody.substring(bodyTrimStartPosition, bodyTrimEndPosition);
		}

		fileContent = fileContent.replace(htmlBody, htmlBodyToReplace);

		BookSection bookSection = new BookSection();

		bookSection.setSectionContent(fileContent);

		if (this.lastBookSectionInfo != null) {
			bookSection.setExtension(this.lastBookSectionInfo.getExtension());
			bookSection.setLabel(this.lastBookSectionInfo.getLabel());
			bookSection.setMediaType(this.lastBookSectionInfo.getMediaType());
		}

		return bookSection;
	}

	private String getNextAnchor(int index, String entryName) throws ReadingException {
		if (getToc().getNavMap().getNavPoints().size() > (index + 1)) {
			NavPoint nextNavPoint = getNavPoint(index + 1);

			if (nextNavPoint.getEntryName() == null) { // Not a trimmed section.
				String[] nextEntryLabel = findEntryNameAndLabel(nextNavPoint);

				String nextHref = nextEntryLabel[0];

				if (nextHref != null) {
					String fileName = getFileName(entryName);

					if (nextHref.contains(fileName)) { // Both anchors are in the same file.
						return nextHref.replace(fileName, "");
					}
				}
			}
		}

		return null;
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

		throw new ReadingException("NavPoint content is not found in epub content.");
	}

	private String getFileExtension(String fileName) {
		int dotIndex = fileName.lastIndexOf('.');
		if (dotIndex != -1) {
			return fileName.substring(0, dotIndex);
		}

		return null;
	}

	private String readFileContent(String entryName) throws ReadingException {

		try {
			ZipEntry zipEntry = epubFile.getEntry(entryName);
			InputStream inputStream = epubFile.getInputStream(zipEntry);

			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
			StringBuilder fileContent = new StringBuilder();

			String line;
			while ((line = bufferedReader.readLine()) != null) {
				fileContent.append(line);
			}

			// epubFile.close();
			bufferedReader.close();

			return fileContent.toString();
		} catch (IOException e) {
			e.printStackTrace();
			throw new ReadingException("IO Exception while reading entry " + entryName + "\n" + e.getMessage());
		}

	}

	private String getFileName(String entryName) {
		int lastSlashIndex = entryName.lastIndexOf("/");
		return entryName.substring(lastSlashIndex + 1);
	}

	private String getHtmlBody(String htmlContent) throws ReadingException {
		int startOfBody = htmlContent.indexOf(Constants.TAG_BODY_START);
		int endOfBody = htmlContent.indexOf(Constants.TAG_BODY_END);

		if (startOfBody != -1 && endOfBody != -1) {
			return htmlContent.substring(startOfBody + Constants.TAG_BODY_START.length(), endOfBody);
		} else {
			throw new ReadingException("Exception while getting book section : Html body tags not found.");
		}
	}

	// Starts from current anchor, reads until the next anchor starts.
	private String getAnchorsInterval(String htmlContent, String currentAnchor, String nextAnchor) throws ReadingException {
		String htmlBody = getHtmlBody(htmlContent);

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

			htmlContent = htmlContent.replace(htmlBody, trimmedPart);
			return htmlContent;
		} else {
			throw new ReadingException("Exception while trimming anchored parts : Defined Anchors not found.");
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

		while (cssHrefAndLinkPart != null) { // There may be multiple css links.

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

						break;
					} catch (IOException e) {
						e.printStackTrace();
						throw new ReadingException("IOException while reading " + cssHref + " file: " + e.getMessage());
					}
				}
			}
		}

		return htmlContent;
	}

	List<String> getEntryNames() {
		return entryNames;
	}

	void addEntryName(String zipEntryName) {
		entryNames.add(zipEntryName);
	}

	Container getContainer() {
		return container;
	}

	Package getPackage() {
		return opfPackage;
	}

	Toc getToc() {
		return toc;
	}

	ZipFile getEpubFile() {
		return epubFile;
	}

	void setEpubFile(ZipFile epubFile) {
		this.epubFile = epubFile;
	}

	public void setMaxContentPerSection(int maxContentPerSection) {
		this.maxContentPerSection = maxContentPerSection;
	}

}