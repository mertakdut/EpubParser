package com.codefan.epubutils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.codefan.epubutils.BaseFindings.XmlItem;

public class Content {

	private ZipFile epubFile;

	private Container container;
	private Package opfPackage;
	private Toc toc;

	private List<String> entryNames;

	private Map<String, Map<Integer, Integer>> entryTagPositions;

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
					throw new ReadingException("Index is greater than (or equal) TOC (Term of Contents) size");
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

					// Calculate the tag positions of the current entry, if it hasn't done before.
					if (entryTagPositions == null || !entryTagPositions.containsKey(entryName)) {
						if (entryTagPositions == null) {
							entryTagPositions = new HashMap<>();
						}

						calculateEntryTagPositions(entryName, htmlBody);
					}

					if (htmlBody.length() > maxContentPerSection) {
						int calculatedTrimEndPosition = calculateTrimEndPosition(htmlBody, 0);

						fileContentStr = fileContentStr.replace(htmlBody, htmlBody.substring(0, calculatedTrimEndPosition));

						List<String> openedTags = getOpenedTags(htmlBody);

						NavPoint nextEntryNavPoint = new NavPoint();

						nextEntryNavPoint.setEntryName(entryName);
						nextEntryNavPoint.setBodyTrimStartPosition(calculatedTrimEndPosition);
						nextEntryNavPoint.setOpenTags(openedTags);

						getToc().getNavMap().getNavPoints().add(index + 1, nextEntryNavPoint);

						// Not sure if these are needed.
						getToc().getNavMap().getNavPoints().get(index).setEntryName(entryName);
						getToc().getNavMap().getNavPoints().get(index).setBodyTrimStartPosition(0);
						getToc().getNavMap().getNavPoints().get(index).setBodyTrimEndPosition(calculatedTrimEndPosition);

						lastBookSectionInfo = new BookSection();
						lastBookSectionInfo.setExtension(extension);
						lastBookSectionInfo.setLabel(label);
						lastBookSectionInfo.setMediaType(mediaType);

						/*
						 * nextEntryNavPoint.setEntryName(entryName); nextEntryNavPoint.setBodyTrimStartPosition(calculatedTrimEndPosition);
						 * nextEntryNavPoint.setOpenTags(openedTags);
						 * 
						 * getToc().getNavMap().getNavPoints().add(index + 1, nextEntryNavPoint);
						 * 
						 * getToc().getNavMap().getNavPoints().get(index).setBodyTrimEndPosition(calculatedTrimEndPosition); // Sets endPosition to avoid calculating again.
						 * getToc().getNavMap().getNavPoints().get(index).setClosingTags(closingTags);
						 * 
						 */
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

	/*
	 * This method calculates and keeps every tag indices of the given entry file. Later on, these calculations will be used when trimming the entry.
	 * 
	 * e.g. If the open-close tag indices are in the same trimmed part; tag will be closed there and won't disturb the next trimmed part.
	 * 
	 * If the open-close tag indices are not in the same trimmed part; tag will be closed at the end of the current trimmed part, and opened in the next trimmed part.
	 */
	private void calculateEntryTagPositions(String entryName, String htmlBody) {
		List<Tag> openedTags = null;
		ListIterator<Tag> listIterator = null;

		boolean isPossiblyTagOpened = false;
		StringBuilder possiblyTag = new StringBuilder();

		Pattern pattern = Pattern.compile(Constants.HTML_TAG_PATTERN);
		Matcher matcher;

		for (int i = 0; i < htmlBody.length(); i++) {
			if (htmlBody.charAt(i) == '<') { // Tag might have been opened.
				isPossiblyTagOpened = true;
			} else if (htmlBody.charAt(i) == '>') { // Tag might have been closed.
				if (htmlBody.charAt(i - 1) != '/') { // Empty tags are unnecessary.
					possiblyTag.append('>');

					String tagStr = possiblyTag.toString();

					matcher = pattern.matcher(tagStr);

					if (matcher.matches()) {
						if (tagStr.charAt(1) == '/') { // Closing tag. Match it with the last open tag with the same name.
							String tagName = getTagName(tagStr, false);

							listIterator = openedTags.listIterator(openedTags.size());

							while (listIterator.hasPrevious()) {
								Tag openedTag = listIterator.previous();

								if (openedTag.getTagName().equals(tagName)) { // Found the last open tag with the same name.
									if (this.entryTagPositions == null) {
										this.entryTagPositions = new HashMap<>();
									}

									Map<Integer, Integer> tagPositions = new HashMap<>();
									tagPositions.put(openedTag.getTagPosition(), i); // Tag startPos - endPos

									this.entryTagPositions.put(entryName, tagPositions);
								}
							}

						} else { // Opening tag.
							if (openedTags == null) {
								openedTags = new ArrayList<>();
							}

							String tagName = getTagName(tagStr, true);

							Tag tag = new Tag();
							tag.setTagName(tagName);
							tag.setTagPosition(i);

							openedTags.add(tag);
						}
					}
				}

				possiblyTag.setLength(0);
				isPossiblyTagOpened = false;
			}

			if (isPossiblyTagOpened) {
				possiblyTag.append(htmlBody.charAt(i));
			}
		}
	}

	private String getTagName(String tag, boolean isOpeningTag) {
		int closingBracletIndex = tag.indexOf('>');

		if (isOpeningTag) {
			return tag.substring(1, closingBracletIndex);
		} else {
			return tag.substring(2, closingBracletIndex);
		}
	}

	private List<String> getOpenedTags(String htmlBody) {
		List<String> openedTags = null;
		List<String> closedTags = null;

		boolean isPossiblyTagOpened = false;
		StringBuilder possiblyTag = new StringBuilder();

		for (int i = 0; i < htmlBody.length(); i++) {
			if (htmlBody.charAt(i) == '<') { // Tag might have been opened.
				isPossiblyTagOpened = true;
			} else if (htmlBody.charAt(i) == '>') { // Tag might have been closed.
				if (htmlBody.charAt(i - 1) != '/') {
					possiblyTag.append('>');

					String possiblyTagStr = possiblyTag.toString();

					if (possiblyTag.charAt(1) == '/') {
						if (closedTags == null) {
							closedTags = new ArrayList<>();
						}
					} else {
						if (openedTags == null) {
							openedTags = new ArrayList<>();
						}

						openedTags.add(possiblyTagStr);
					}
				}

				possiblyTag.setLength(0);
				isPossiblyTagOpened = false;
			}

			if (isPossiblyTagOpened) {
				possiblyTag.append(htmlBody.charAt(i));
			}
		}

		for (int i = 0; i < closedTags.size(); i++) {
			if (openedTags.contains(closedTags.get(i))) {
				openedTags.remove(closedTags.get(i));
			}
		}

		return openedTags;
	}

	private BookSection prepareTrimmedBookSection(NavPoint entryNavPoint, int index) throws ReadingException {
		String entryName = entryNavPoint.getEntryName();
		int bodyTrimStartPosition = entryNavPoint.getBodyTrimStartPosition();
		int bodyTrimEndPosition = entryNavPoint.getBodyTrimEndPosition(); // Will be calculated on the first attempt.
		String entryClosingTags = entryNavPoint.getClosingTags(); // Will be calculated on the first attempt.
		List<String> entryOpenedTags = entryNavPoint.getOpenTags();

		String fileContent = readFileContent(entryName);

		String htmlBody = getHtmlBody(fileContent);
		String htmlBodyToReplace = entryOpenedTags != null ? appendOpenedTags(entryOpenedTags) : "";

		if (bodyTrimEndPosition == 0) { // Not calculated before.
			String nextAnchor = getNextAnchor(index, entryName);

			if (nextAnchor != null) { // Next anchor is available in the same file. It may be the next stop for the content.
				String nextAnchorHtml = convertAnchorToHtml(nextAnchor);

				if (htmlBody.contains(nextAnchorHtml)) {
					int anchorIndex = htmlBody.indexOf(nextAnchorHtml);

					while (htmlBody.charAt(anchorIndex) != '<') { // Getting just before anchor html.
						anchorIndex--;
					}

					htmlBodyToReplace += htmlBody.substring(bodyTrimStartPosition, anchorIndex);
					getToc().getNavMap().getNavPoints().get(index).setBodyTrimEndPosition(anchorIndex); // Sets endPosition to avoid calculating again.
				} else { // NextAnchor not found in the htmlContent. Invalidate it by removing it from navPoints and search for the next one.
					int tmpIndex = index;
					getToc().getNavMap().getNavPoints().remove(++tmpIndex); // Removing the nextAnchor from navPoints.

					int markedNavPoints = 0;

					boolean isNextAnchorFound = false;
					// Next available anchor should be the next starting point.
					while (tmpIndex < getToc().getNavMap().getNavPoints().size()) { // Looping until next anchor is found.
						NavPoint possiblyNextNavPoint = getNavPoint(tmpIndex);
						String[] possiblyNextEntryNameLabel = findEntryNameAndLabel(possiblyNextNavPoint);

						String possiblyNextEntryName = possiblyNextEntryNameLabel[0];

						if (possiblyNextEntryName != null) {
							String fileName = getFileName(entryName);

							if (possiblyNextEntryName.contains(fileName)) {
								String anchor = possiblyNextEntryName.replace(fileName, "");
								String anchorHtml = convertAnchorToHtml(anchor);

								if (htmlBody.contains(anchorHtml)) {
									int anchorIndex = htmlBody.indexOf(anchorHtml);

									while (htmlBody.charAt(anchorIndex) != '<') { // Getting just before anchor html.
										anchorIndex--;
									}

									htmlBodyToReplace += htmlBody.substring(bodyTrimStartPosition, anchorIndex);
									getToc().getNavMap().getNavPoints().get(index).setBodyTrimEndPosition(tmpIndex); // Sets endPosition to avoid calculating again.
									isNextAnchorFound = true;
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

					if (!isNextAnchorFound) {
						htmlBodyToReplace += htmlBody.substring(bodyTrimStartPosition);
					}

				}
			} else {
				htmlBodyToReplace += htmlBody.substring(bodyTrimStartPosition);
			}

			if (htmlBodyToReplace.length() > maxContentPerSection) { // Trimming again if needed.
				int calculatedTrimEndPosition = calculateTrimEndPosition(htmlBody, bodyTrimStartPosition);

				htmlBodyToReplace += htmlBody.substring(bodyTrimStartPosition, calculatedTrimEndPosition);

				List<String> openedTags = getOpenedTags(htmlBodyToReplace);

				String closingTags = null;
				if (openedTags != null) {
					closingTags = getClosingTags(openedTags);
					htmlBodyToReplace += closingTags;
				}

				NavPoint nextEntryNavPoint = new NavPoint();

				nextEntryNavPoint.setEntryName(entryName);
				nextEntryNavPoint.setBodyTrimStartPosition(calculatedTrimEndPosition);
				nextEntryNavPoint.setOpenTags(openedTags);

				getToc().getNavMap().getNavPoints().add(index + 1, nextEntryNavPoint);

				getToc().getNavMap().getNavPoints().get(index).setBodyTrimEndPosition(calculatedTrimEndPosition); // Sets endPosition to avoid calculating again.
				getToc().getNavMap().getNavPoints().get(index).setClosingTags(closingTags);
			}
		} else { // Calculated before.
			htmlBodyToReplace += htmlBody.substring(bodyTrimStartPosition, bodyTrimEndPosition);

			if (entryClosingTags != null) {
				htmlBodyToReplace += entryClosingTags;
			}

			if (entryOpenedTags != null) {
				htmlBodyToReplace = appendOpenedTags(entryOpenedTags) + htmlBodyToReplace;
			}
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

	private String getClosingTags(List<String> openedTags) {
		StringBuilder closingTags = new StringBuilder();

		for (int i = 0; i < openedTags.size(); i++) {
			String openedTag = openedTags.get(i);
			String closingTag = openedTag.substring(0, 1) + "/" + openedTag.substring(1, openedTag.length());

			closingTags.append(closingTag);
		}

		return closingTags.toString();
	}

	private String appendOpenedTags(List<String> openedTags) {
		StringBuilder openingTags = new StringBuilder();

		for (int i = 0; i < openedTags.size(); i++) {
			openingTags.append(openedTags.get(i));
		}

		return openingTags.toString();
	}

	private int calculateTrimEndPosition(String htmlBody, int trimStartPosition) {
		int trimEndPosition = trimStartPosition + maxContentPerSection;

		while (htmlBody.charAt(trimEndPosition) != ' ') {
			trimEndPosition--;
		}

		return trimEndPosition;

	}

	private String getNextAnchor(int index, String entryName) throws ReadingException {
		if (getToc().getNavMap().getNavPoints().size() > (index + 1)) {
			NavPoint nextNavPoint = getNavPoint(index + 1);

			if (nextNavPoint.getEntryName() == null) { // Real navPoint. Only real navPoints are anchored.
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
			throw new ReadingException("IO Exception while reading entry " + entryName + e.getMessage());
		}
	}

	private String getRows(String content, int rowStartPos, int maxLength) {
		StringBuilder rows = new StringBuilder();

		int linePosition = 0;
		Scanner scanner = new Scanner(content);
		while (scanner.hasNextLine()) {
			if (rowStartPos <= linePosition) {
				String line = scanner.nextLine();
				rows.append(line);
			} else {
				scanner.nextLine();
			}

			if (rows.length() >= maxLength) {
				break;
			}
		}
		scanner.close();

		// Return linePosition as well; to use as endIndex of the current navPoint, and startIndex of the next navPoint.
		return rows.toString();
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