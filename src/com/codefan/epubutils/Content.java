package com.codefan.epubutils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.commons.codec.binary.Base64;
import org.xml.sax.SAXException;

import com.codefan.epubutils.BaseFindings.XmlItem;

public class Content {

	private Logger logger;

	private String zipFilePath;

	private Container container;
	private Package opfPackage;
	private Toc toc;

	private List<String> entryNames;

	private Map<String, List<TagInfo>> entryTagPositions;

	// private int playOrder;

	// private int maxContentPerSection; // String length.
	private BookSection lastBookSectionInfo;

	public Content() {
		logger = new Logger();

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

	BookSection getBookSection(int index, int maxContentPerSection, CssStatus cssStatus, boolean isIncludingTextContent) throws ReadingException {
		NavPoint navPoint = getNavPoint(index);

		if (maxContentPerSection == 0 || navPoint.getTypeCode() == 0 || navPoint.getTypeCode() == 1) { // Real navPoint - actual file/anchor.
			// logger.log(Severity.info, "\nindex: " + index + ", Real(at least for now...) navPoint");
			return prepareBookSection(navPoint, index, maxContentPerSection, cssStatus, isIncludingTextContent);
		} else { // Pseudo navPoint - trimmed file entry.
			// logger.log(Severity.info, "\nindex: " + index + ", Pseudo navPoint");
			return prepareTrimmedBookSection(navPoint, index, maxContentPerSection, cssStatus, isIncludingTextContent);
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

	private BookSection prepareBookSection(NavPoint navPoint, int index, int maxContentPerSection, CssStatus cssStatus, boolean isIncludingTextContent) throws ReadingException {
		BookSection bookSection = new BookSection();

		int entryStartPosition = navPoint.getBodyTrimStartPosition();
		int entryEndPosition = navPoint.getBodyTrimEndPosition();
		String entryEntryName = navPoint.getEntryName();

		String fileContentStr = null;
		String htmlBody = null;
		String htmlBodyToReplace = null;

		if (entryStartPosition == 0 && entryEndPosition == 0) { // Not calculated before.
			String[] entryNameAndLabel = findEntryNameAndLabel(navPoint);

			String href = entryNameAndLabel[0];
			String label = entryNameAndLabel[1];

			String currentAnchor = null;
			String nextAnchor = null;

			int trimStartPosition = 0;
			int trimEndPosition = 0;

			for (int i = 0; i < getEntryNames().size(); i++) {
				String entryName = getEntryNames().get(i);

				String fileName = getFileName(entryName);

				if (href.contains(fileName)) { // href actually exists.

					if (!href.equals(fileName)) { // Anchored, e.g. #pgepubid00058
						currentAnchor = href.replace(fileName, "");
						nextAnchor = getNextAnchor(index, entryName);
					}

					fileContentStr = readFileContent(entryName, cssStatus);
					htmlBody = getHtmlBody(fileContentStr);

					// entryTagPositions only used in either in trimming or including text content.
					if ((maxContentPerSection != 0 && maxContentPerSection < htmlBody.length()) || isIncludingTextContent) {
						// Calculate the tag positions of the current entry, if it hasn't done before.
						if (entryTagPositions == null || !entryTagPositions.containsKey(entryName)) {
							if (entryTagPositions == null) {
								entryTagPositions = new HashMap<>();
							}

							calculateEntryTagPositions(entryName, htmlBody);
						}
					}

					if (nextAnchor != null) { // Splitting the file by anchors.
						currentAnchor = convertAnchorToHtml(currentAnchor);
						nextAnchor = convertAnchorToHtml(nextAnchor);

						boolean containsCurrentAnchor = fileContentStr.contains(currentAnchor);
						boolean containsNextAnchor = fileContentStr.contains(nextAnchor);

						if (containsCurrentAnchor && containsNextAnchor) {
							int[] bodyIntervals = getAnchorsInterval(htmlBody, currentAnchor, nextAnchor);

							trimStartPosition = bodyIntervals[0];
							trimEndPosition = bodyIntervals[1];
						} else {
							int tmpIndex = index;

							if (!containsCurrentAnchor && !containsNextAnchor) { // Both of the anchors not found.
								getToc().getNavMap().getNavPoints().remove(tmpIndex++); // Delete the first one (current anchor)
								getToc().getNavMap().getNavPoints().remove(tmpIndex++); // Delete the second one (next anchor)
								currentAnchor = null;
							} else if (containsCurrentAnchor) { // Next anchor not found.
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
											if (currentAnchor == null) { // If current anchor is not found, first set that.
												currentAnchor = anchor;
											} else { // If current anchor is already defined set the next anchor and break.
												nextAnchor = anchor;
												break;
											}
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

							int[] bodyIntervals = getAnchorsInterval(htmlBody, currentAnchor, nextAnchor);

							trimStartPosition = bodyIntervals[0];
							trimEndPosition = bodyIntervals[1];
						}
					}

					String extension = getFileExtension(fileName);
					String mediaType = getMediaType(fileName);

					// If fileContentStr is too long; crop it by the maxContentPerSection.
					// Save the fileContent and position within a new navPoint, insert it after current index.
					if (maxContentPerSection != 0) { // maxContentPerSection is given.
						int calculatedTrimEndPosition = calculateTrimEndPosition(entryName, htmlBody, trimStartPosition, trimEndPosition, maxContentPerSection);

						if (calculatedTrimEndPosition != -1) {
							trimEndPosition = calculatedTrimEndPosition;

							List<String> openedTags = getOpenedTags(entryName, trimStartPosition, trimEndPosition);

							htmlBodyToReplace = htmlBody.substring(trimStartPosition, trimEndPosition);

							String closingTags = null;
							if (openedTags != null) {
								closingTags = prepareClosingTags(openedTags);
								htmlBodyToReplace += closingTags;
							}

							NavPoint nextEntryNavPoint = new NavPoint();

							nextEntryNavPoint.setTypeCode(2);
							nextEntryNavPoint.setEntryName(entryName);
							nextEntryNavPoint.setBodyTrimStartPosition(trimEndPosition);
							nextEntryNavPoint.setOpenTags(openedTags);

							getToc().getNavMap().getNavPoints().add(index + 1, nextEntryNavPoint);

							// Inserting calculated info to avoid calculating this navPoint again. In the future these data could be written to Term of Contents file.
							getToc().getNavMap().getNavPoints().get(index).setTypeCode(2); // To indicate that, this is a trimmed part.
							getToc().getNavMap().getNavPoints().get(index).setEntryName(entryName);
							getToc().getNavMap().getNavPoints().get(index).setBodyTrimStartPosition(trimStartPosition);
							getToc().getNavMap().getNavPoints().get(index).setBodyTrimEndPosition(trimEndPosition);
							getToc().getNavMap().getNavPoints().get(index).setClosingTags(closingTags);

							if (lastBookSectionInfo == null) {
								lastBookSectionInfo = new BookSection();
							}

							lastBookSectionInfo.setExtension(extension);
							lastBookSectionInfo.setLabel(label);
							lastBookSectionInfo.setMediaType(mediaType);
						} else {
							htmlBodyToReplace = getNonTrimmedHtmlBody(index, htmlBody, trimStartPosition, trimEndPosition, entryName);
						}
					} else {
						htmlBodyToReplace = getNonTrimmedHtmlBody(index, htmlBody, trimStartPosition, trimEndPosition, entryName);
					}

					bookSection.setExtension(extension);
					bookSection.setLabel(label);
					bookSection.setMediaType(mediaType);

					if (isIncludingTextContent) {
						bookSection.setSectionTextContent(getOnlyTextContent(entryName, htmlBody, trimStartPosition, trimEndPosition));
					}
				}
			}
		} else { // Calculated before.
			fileContentStr = readFileContent(entryEntryName, cssStatus);
			htmlBody = getHtmlBody(fileContentStr);

			if (entryEndPosition != 0) {
				htmlBodyToReplace = htmlBody.substring(entryStartPosition, entryEndPosition);
			} else {
				htmlBodyToReplace = htmlBody.substring(entryStartPosition);
			}

			if (isIncludingTextContent) {
				bookSection.setSectionTextContent(getOnlyTextContent(entryEntryName, htmlBody, entryStartPosition, entryEndPosition));
			}
		}

		if (cssStatus == CssStatus.OMIT) {
			htmlBodyToReplace = replaceTableTag(htmlBodyToReplace);
		}

		htmlBodyToReplace = replaceImgTag(htmlBodyToReplace, cssStatus);
		fileContentStr = fileContentStr.replace(htmlBody, htmlBodyToReplace);

		if (cssStatus == CssStatus.DISTRIBUTE) {
			fileContentStr = dissolveStyleTag(fileContentStr);
		}

		bookSection.setSectionContent(fileContentStr);
		return bookSection;
	}

	private BookSection prepareTrimmedBookSection(NavPoint entryNavPoint, int index, int maxContentPerSection, CssStatus cssStatus, boolean isIncludingTextContent) throws ReadingException {
		String entryName = entryNavPoint.getEntryName();
		int bodyTrimStartPosition = entryNavPoint.getBodyTrimStartPosition();
		int bodyTrimEndPosition = entryNavPoint.getBodyTrimEndPosition(); // Will be calculated on the first attempt.
		String entryClosingTags = entryNavPoint.getClosingTags(); // Will be calculated on the first attempt.
		List<String> entryOpenedTags = entryNavPoint.getOpenTags();

		// logger.log(Severity.info, "index: " + index + ", entryName: " + entryName + ", bodyTrimStartPosition: " + bodyTrimStartPosition + ", bodyTrimEndPosition: "
		// + bodyTrimEndPosition + ", entryOpenedTags: " + entryOpenedTags + ", entryClosingTags: " + entryClosingTags);

		String fileContent = readFileContent(entryName, cssStatus);
		String htmlBody = getHtmlBody(fileContent);
		String htmlBodyToReplace = null;

		if (bodyTrimEndPosition == 0) { // Not calculated before.
			String nextAnchor = getNextAnchor(index, entryName);

			if (nextAnchor != null) { // Next anchor is available in the same file. It may be the next stop for the content.
				String nextAnchorHtml = convertAnchorToHtml(nextAnchor);
				int anchorIndex = htmlBody.indexOf(nextAnchorHtml);

				if (anchorIndex != -1 && bodyTrimStartPosition <= anchorIndex) {

					while (htmlBody.charAt(anchorIndex) != Constants.TAG_OPENING) { // Getting just before anchor html.
						anchorIndex--;
					}

					bodyTrimEndPosition = anchorIndex;
				} else { // NextAnchor not found in the htmlContent. Invalidate it by removing it from navPoints and search for the next one.
					bodyTrimEndPosition = getNextAvailableAnchorIndex(index, entryName, bodyTrimStartPosition, htmlBody);
				}
			}

			int calculatedTrimEndPosition = calculateTrimEndPosition(entryName, htmlBody, bodyTrimStartPosition, bodyTrimEndPosition, maxContentPerSection);

			if (calculatedTrimEndPosition != -1) { // Trimming again if needed.
				bodyTrimEndPosition = calculatedTrimEndPosition;

				htmlBodyToReplace = htmlBody.substring(bodyTrimStartPosition, bodyTrimEndPosition);

				List<String> openedTags = getOpenedTags(entryName, bodyTrimStartPosition, bodyTrimEndPosition);

				String closingTags = null;
				if (openedTags != null) {
					closingTags = prepareClosingTags(openedTags);
					htmlBodyToReplace += closingTags;
				}

				NavPoint nextEntryNavPoint = new NavPoint();

				nextEntryNavPoint.setTypeCode(2);
				nextEntryNavPoint.setEntryName(entryName);
				nextEntryNavPoint.setBodyTrimStartPosition(bodyTrimEndPosition);
				nextEntryNavPoint.setOpenTags(openedTags); // Next navPoint should start with these open tags because they are not closed in this navPoint yet.

				getToc().getNavMap().getNavPoints().add(index + 1, nextEntryNavPoint);

				getToc().getNavMap().getNavPoints().get(index).setBodyTrimEndPosition(bodyTrimEndPosition); // Sets endPosition to avoid calculating again.
				getToc().getNavMap().getNavPoints().get(index).setClosingTags(closingTags);
			} else {
				htmlBodyToReplace = getNonTrimmedHtmlBody(index, htmlBody, bodyTrimStartPosition, bodyTrimEndPosition, entryName);
			}
		} else { // Calculated before.
			htmlBodyToReplace = htmlBody.substring(bodyTrimStartPosition, bodyTrimEndPosition);

			if (entryClosingTags != null) {
				htmlBodyToReplace += entryClosingTags;
			}
		}

		if (entryOpenedTags != null) {
			htmlBodyToReplace = prepareOpenedTags(entryOpenedTags) + htmlBodyToReplace;

			String closingTags = prepareClosingTags(entryOpenedTags);
			htmlBodyToReplace += closingTags;
		}

		if (cssStatus == CssStatus.OMIT) {
			htmlBodyToReplace = replaceTableTag(htmlBodyToReplace);
		}

		htmlBodyToReplace = replaceImgTag(htmlBodyToReplace, cssStatus);
		fileContent = fileContent.replace(htmlBody, htmlBodyToReplace);

		if (cssStatus == CssStatus.DISTRIBUTE) {
			fileContent = dissolveStyleTag(fileContent);
		}

		BookSection bookSection = new BookSection();

		bookSection.setSectionContent(fileContent);

		if (isIncludingTextContent) {
			bookSection.setSectionTextContent(getOnlyTextContent(entryName, htmlBody, bodyTrimStartPosition, bodyTrimEndPosition));
		}

		if (this.lastBookSectionInfo != null) {
			bookSection.setExtension(this.lastBookSectionInfo.getExtension());
			bookSection.setLabel(this.lastBookSectionInfo.getLabel());
			bookSection.setMediaType(this.lastBookSectionInfo.getMediaType());
		}

		return bookSection;
	}

	private String getNonTrimmedHtmlBody(int index, String htmlBody, int trimStartPosition, int trimEndPosition, String entryName) {
		String htmlBodyToReplace = null;

		if (trimEndPosition == 0) {
			htmlBodyToReplace = htmlBody.substring(trimStartPosition);
		} else {
			htmlBodyToReplace = htmlBody.substring(trimStartPosition, trimEndPosition);
		}

		getToc().getNavMap().getNavPoints().get(index).setBodyTrimStartPosition(trimStartPosition);
		getToc().getNavMap().getNavPoints().get(index).setBodyTrimEndPosition(trimEndPosition);
		getToc().getNavMap().getNavPoints().get(index).setEntryName(entryName);

		return htmlBodyToReplace;
	}

	/*
	 * This method calculates and keeps every tag indices of the given entry file. Later on, these calculations will be used when trimming the entry.
	 * 
	 * e.g. If the open-close tag indices are in the same trimmed part; tag will be closed there and won't disturb the next trimmed part.
	 * 
	 * If the open-close tag indices are not in the same trimmed part; tag will be closed at the end of the current trimmed part, and opened in the next trimmed part.
	 */
	private void calculateEntryTagPositions(String entryName, String htmlBody) {
		List<TagInfo> openedTags = null;
		ListIterator<TagInfo> listIterator = null;

		boolean isPossiblyTagOpened = false;
		StringBuilder possiblyTag = new StringBuilder();

		Pattern pattern = Pattern.compile(Constants.HTML_TAG_PATTERN);
		Matcher matcher;

		for (int i = 0; i < htmlBody.length(); i++) {
			if (htmlBody.charAt(i) == Constants.TAG_OPENING) { // Tag might have been opened.
				isPossiblyTagOpened = true;
				possiblyTag.setLength(0); // In case of double occurence of '<' start from the next found tag opening; e.g. '< <p>'.
			} else if (htmlBody.charAt(i) == Constants.TAG_CLOSING) { // Tag might have been closed.
				possiblyTag.append(Constants.TAG_CLOSING);
				if (htmlBody.charAt(i - 1) != '/') { // Not an empty tag.
					String tagStr = possiblyTag.toString();

					matcher = pattern.matcher(tagStr);

					if (matcher.matches()) {
						if (tagStr.charAt(1) == '/') { // Closing tag. Match it with the last open tag with the same name.
							String tagName = getFullTagName(tagStr, false);

							listIterator = openedTags.listIterator(openedTags.size());

							while (listIterator.hasPrevious()) {
								TagInfo openedTag = listIterator.previous();

								if (openedTag.getTagName().equals(tagName)) { // Found the last open tag with the same name.
									addEntryTagPosition(entryName, openedTag.getFullTagName(), openedTag.getOpeningTagStartPosition(), i - tagName.length() - 1);
									listIterator.remove();
									break;
								}
							}
						} else { // Opening tag.
							if (openedTags == null) {
								openedTags = new ArrayList<>();
							}

							String fullTagName = getFullTagName(tagStr, true);
							String tagName = getTagName(fullTagName);

							TagInfo tag = new TagInfo();
							tag.setTagName(tagName);
							tag.setFullTagName(fullTagName);
							tag.setOpeningTagStartPosition(i - fullTagName.length());

							openedTags.add(tag);
						}
					}
				} else { // Empty tag.
					String tagStr = possiblyTag.toString();

					matcher = pattern.matcher(tagStr);

					if (matcher.matches()) {
						int closingBracletIndex = tagStr.indexOf(Constants.TAG_CLOSING);
						String tagName = tagStr.substring(1, closingBracletIndex - 1);

						addEntryTagPosition(entryName, tagName, i - tagName.length() - 1, i - tagName.length() - 1);
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

	private void addEntryTagPosition(String entryName, String fullTagName, int openingPosition, int closingPosition) {
		TagInfo tagInfo = new TagInfo();
		tagInfo.setOpeningTagStartPosition(openingPosition);
		tagInfo.setClosingTagStartPosition(closingPosition);
		tagInfo.setFullTagName(fullTagName);
		tagInfo.setTagName(getTagName(fullTagName));

		if (this.entryTagPositions.containsKey(entryName)) {
			this.entryTagPositions.get(entryName).add(tagInfo);
		} else {
			List<TagInfo> tagInfoList = new ArrayList<>();
			tagInfoList.add(tagInfo);
			this.entryTagPositions.put(entryName, tagInfoList);
		}
	}

	private String getFullTagName(String tag, boolean isOpeningTag) {
		int closingBracletIndex = tag.indexOf(Constants.TAG_CLOSING);
		if (isOpeningTag) {
			return tag.substring(1, closingBracletIndex);
		} else {
			return tag.substring(2, closingBracletIndex);
		}
	}

	private String getTagName(String fullTagName) {

		if (fullTagName.contains(" ")) {
			fullTagName = fullTagName.trim();

			int endIndex = 1;
			while (fullTagName.length() > endIndex && fullTagName.charAt(endIndex) != ' ') {
				endIndex++;
			}

			return fullTagName.substring(0, endIndex);
		} else {
			return fullTagName;
		}
	}

	// TODO: Similar functionality happens in the prepareBookSection method. Merge them into this.
	private int getNextAvailableAnchorIndex(int index, String entryName, int bodyTrimStartPosition, String htmlBody) throws ReadingException {
		getToc().getNavMap().getNavPoints().remove(++index); // Removing the nextAnchor from navPoints; 'cause it's already not found.

		int markedNavPoints = 0;

		int anchorIndex = -1;

		boolean isNextAnchorFound = false;

		// Next available anchor should be the next starting point.
		while (index < getToc().getNavMap().getNavPoints().size()) { // Looping until next anchor is found.
			NavPoint possiblyNextNavPoint = getNavPoint(index);
			String[] possiblyNextEntryNameLabel = findEntryNameAndLabel(possiblyNextNavPoint);

			String possiblyNextEntryName = possiblyNextEntryNameLabel[0];

			if (possiblyNextEntryName != null) {
				String fileName = getFileName(entryName);

				if (possiblyNextEntryName.contains(fileName)) {
					String anchor = possiblyNextEntryName.replace(fileName, "");
					String anchorHtml = convertAnchorToHtml(anchor);
					anchorIndex = htmlBody.indexOf(anchorHtml);

					if (anchorIndex != -1) {

						while (htmlBody.charAt(anchorIndex) != Constants.TAG_OPENING) { // Getting just before anchor html.
							anchorIndex--;
						}

						if (bodyTrimStartPosition <= anchorIndex) {
							// getToc().getNavMap().getNavPoints().get(index).setBodyTrimEndPosition(anchorIndex); // Sets endPosition to avoid calculating again.
							isNextAnchorFound = true;
							break;
						}
					}
				}
			}

			getToc().getNavMap().getNavPoints().get(index).setMarkedToDelete(true);
			markedNavPoints++;

			index++;
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

		if (isNextAnchorFound) {
			return anchorIndex;
		} else {
			return 0;
		}
	}

	private String prepareClosingTags(List<String> openedTags) {
		StringBuilder closingTagsBuilder = new StringBuilder();

		for (int i = 0; i < openedTags.size(); i++) {
			closingTagsBuilder.append(Constants.TAG_START + getTagName(openedTags.get(i)) + Constants.TAG_CLOSING);
		}

		return closingTagsBuilder.toString();
	}

	private String prepareOpenedTags(List<String> openedTags) {
		StringBuilder openingTags = new StringBuilder();

		for (int i = 0; i < openedTags.size(); i++) {
			openingTags.append(Constants.TAG_OPENING).append(openedTags.get(i)).append(Constants.TAG_CLOSING);
		}

		return openingTags.toString();
	}

	private int calculateTrimEndPosition(String entryName, String htmlBody, int trimStartPosition, int trimEndPos, int maxContentPerSection) {
		int trimEndPosition = (trimEndPos != 0 && (trimEndPos - trimStartPosition) < maxContentPerSection) ? trimEndPos : trimStartPosition + maxContentPerSection;

		int htmlBodyLength = htmlBody.length();

		// Don't need to trim. HtmlBody with tags are already below limit.
		if (htmlBodyLength < trimEndPosition || (trimEndPosition - trimStartPosition) < maxContentPerSection) {
			return -1;
		}

		List<TagInfo> tagStartEndPositions = this.entryTagPositions.get(entryName);

		int loopCount = 0;
		int lastTagsLength = 0;

		while (true) {
			int tagsLength = 0;

			for (TagInfo tagInfo : tagStartEndPositions) {
				// This may not work correctly. Opening and closing tags may differ from one another. We should only check for whichever is inserted first? So; opening only?
				if (tagInfo.getOpeningTagStartPosition() > trimEndPosition) { // (tagInfo.getClosingTagStartPosition() > trimEndPosition)
					break;
				}

				if (tagInfo.getOpeningTagStartPosition() == tagInfo.getClosingTagStartPosition()) {
					if (tagInfo.getOpeningTagStartPosition() > trimStartPosition && tagInfo.getOpeningTagStartPosition() < trimEndPosition) { // Empty Tag
						tagsLength += tagInfo.getFullTagName().length() + 3; // < />
					}
				} else {
					if (tagInfo.getOpeningTagStartPosition() > trimStartPosition && tagInfo.getOpeningTagStartPosition() < trimEndPosition) { // Opening tag.
						tagsLength += tagInfo.getFullTagName().length() + 2; // < >
					}

					if (tagInfo.getClosingTagStartPosition() > trimStartPosition && tagInfo.getClosingTagStartPosition() < trimEndPosition) { // Closing tag.
						tagsLength += tagInfo.getTagName().length() + 3; // < />
					}
				}
			}

			if (lastTagsLength == tagsLength) { // Tags length isn't greater than the last one. No need to keep going.
				if (loopCount == 0) { // Returned on the first try. Don't need to trim. HtmlBody without tags are already below limit.
					if (tagsLength == 0 && htmlBodyLength > trimEndPosition) { // If there are no tags in the trimmed part.
						break;
					}

					return -1;
				} else {
					break;
				}
			}

			trimEndPosition += tagsLength;

			// If trimEndPosition is over the htmlBody's index; then htmlBody is already within limits. No need to trim.
			if (trimEndPosition >= htmlBodyLength) {
				return -1;
			}

			if (((trimEndPosition - trimStartPosition) - tagsLength) >= maxContentPerSection) {
				break;
			}

			lastTagsLength = tagsLength;
			loopCount++;
		}

		int tableStartIndex = htmlBody.indexOf(Constants.TAG_TABLE_START, trimStartPosition);

		// If interval has table, don't break the table.
		if (tableStartIndex != -1 && tableStartIndex < trimEndPosition) {
			int tableEndIndex = htmlBody.indexOf(Constants.TAG_TABLE_END, tableStartIndex);

			if (tableEndIndex != -1) {
				trimEndPosition = tableEndIndex + Constants.TAG_TABLE_END.length();
			} else {
				trimEndPosition = findEligibleEndPosition(tagStartEndPositions, htmlBody, trimEndPosition);
			}
		} else {
			trimEndPosition = findEligibleEndPosition(tagStartEndPositions, htmlBody, trimEndPosition);
		}

		return trimEndPosition;
	}

	private int findEligibleEndPosition(List<TagInfo> tagStartEndPositions, String htmlBody, int trimEndPosition) {
		// Check here if we are in an html tag. If so, move forward or backward until the tag is over.
		// Else, move backwards until we hit the blank.
		boolean isMovedToEndOfTag = false;

		for (TagInfo tagInfo : tagStartEndPositions) {

			// This may not work correctly. Opening and closing tags may differ from one another. We should only check for whichever is inserted first? So; opening only?
			if (tagInfo.getOpeningTagStartPosition() > trimEndPosition) { // (tagInfo.getClosingTagStartPosition() > trimEndPosition)
				break;
			}

			if (tagInfo.getOpeningTagStartPosition() == tagInfo.getClosingTagStartPosition()) { // Empty tag.
				// Inside an empty tag.
				if (tagInfo.getOpeningTagStartPosition() < trimEndPosition && (tagInfo.getOpeningTagStartPosition() + tagInfo.getFullTagName().length() + 3) > trimEndPosition) {

					while (htmlBody.charAt(trimEndPosition) != Constants.TAG_CLOSING) {
						trimEndPosition++;
					}

					trimEndPosition++;
					isMovedToEndOfTag = true;
					break;
				}
			} else {
				// Inside opening tag.
				if (tagInfo.getOpeningTagStartPosition() < trimEndPosition && (tagInfo.getOpeningTagStartPosition() + tagInfo.getFullTagName().length() + 2) > trimEndPosition) {

					while (htmlBody.charAt(trimEndPosition) != Constants.TAG_OPENING) {
						trimEndPosition--;
					}

					trimEndPosition--;
					isMovedToEndOfTag = true;
					break;
				}

				if (tagInfo.getClosingTagStartPosition() < trimEndPosition && (tagInfo.getClosingTagStartPosition() + tagInfo.getTagName().length() + 3) > trimEndPosition) {

					while (htmlBody.charAt(trimEndPosition) != Constants.TAG_CLOSING) {
						trimEndPosition++;
					}

					trimEndPosition++;
					isMovedToEndOfTag = true;
					break;
				}
			}
		}

		// !This may move into a tag!
		if (!isMovedToEndOfTag) {

			while (htmlBody.charAt(trimEndPosition) != ' ') {
				trimEndPosition--;

				// We may have hit a tag.
				if (htmlBody.charAt(trimEndPosition) == Constants.TAG_CLOSING) {
					trimEndPosition++;
					break;
				} else if (htmlBody.charAt(trimEndPosition) == Constants.TAG_OPENING) {
					break;
				}
			}
		}

		return trimEndPosition;
	}

	// Retrieves 'opened and not closed' tags within the trimmed part.
	private List<String> getOpenedTags(String entryName, int trimStartIndex, int trimEndIndex) {
		List<String> openedTags = null;

		List<TagInfo> tagStartEndPositions = this.entryTagPositions.get(entryName);

		for (int i = 0; i < tagStartEndPositions.size(); i++) {
			TagInfo tagInfo = tagStartEndPositions.get(i);

			// Opened in the trimmed part, closed after the trimmed part.
			if (tagInfo.getOpeningTagStartPosition() > trimStartIndex && tagInfo.getOpeningTagStartPosition() < trimEndIndex && tagInfo.getClosingTagStartPosition() > trimEndIndex) {
				if (openedTags == null) {
					openedTags = new ArrayList<>();
				}

				openedTags.add(tagInfo.getFullTagName());
			}
		}

		return openedTags;
	}

	private String getNextAnchor(int index, String entryName) throws ReadingException {
		if (getToc().getNavMap().getNavPoints().size() > (index + 1)) {
			NavPoint nextNavPoint = getNavPoint(index + 1);

			if (nextNavPoint.getTypeCode() != 2) { // Real navPoint. Only real navPoints are anchored.
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
		int lastDotIndex = fileName.lastIndexOf(Constants.DOT);
		if (lastDotIndex != -1) {
			return fileName.substring(lastDotIndex + 1); // +1 to exclude dot from extension.
		}

		return null;
	}

	// This operation is getting expensive and expensive. fileContent could be held in cache; if the entry is same. Maybe a map with one element -> <entryName, fileContent>
	// If map doesn't contain that entryName -> then this method can be used.
	private String readFileContent(String entryName, CssStatus cssStatus) throws ReadingException {

		ZipFile epubFile = null;

		try {
			epubFile = new ZipFile(zipFilePath);

			ZipEntry zipEntry = epubFile.getEntry(entryName);
			InputStream inputStream = epubFile.getInputStream(zipEntry);

			BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));

			StringBuilder fileContent = new StringBuilder();

			try {
				String line;
				while ((line = bufferedReader.readLine()) != null) {
					fileContent.append(line).append(" ");
				}
			} finally {
				bufferedReader.close();
			}

			String fileContentStr = fileContent.toString();

			if (cssStatus != CssStatus.OMIT) {
				fileContentStr = replaceCssLinkWithActualCss(epubFile, fileContentStr);
			}

			return fileContentStr;
		} catch (IOException e) {
			e.printStackTrace();
			throw new ReadingException("IOException while reading content " + entryName + e.getMessage());
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
			throw new ReadingException("ParserConfigurationException while reading content " + entryName + e.getMessage());
		} catch (SAXException e) {
			e.printStackTrace();
			throw new ReadingException("SAXException while reading content " + entryName + e.getMessage());
		} catch (TransformerException e) {
			e.printStackTrace();
			throw new ReadingException("TransformerException while reading content " + entryName + e.getMessage());
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
	private int[] getAnchorsInterval(String htmlBody, String currentAnchor, String nextAnchor) throws ReadingException {
		int startOfCurrentAnchor = htmlBody.indexOf(currentAnchor);
		int startOfNextAnchor = htmlBody.indexOf(nextAnchor);

		if (startOfCurrentAnchor != -1 && startOfNextAnchor != -1) {

			while (htmlBody.charAt(startOfCurrentAnchor) != Constants.TAG_OPENING) {
				startOfCurrentAnchor--;
			}

			while (htmlBody.charAt(startOfNextAnchor) != Constants.TAG_OPENING) {
				startOfNextAnchor--;
			}

			return new int[] { startOfCurrentAnchor, startOfNextAnchor };
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

	private String replaceCssLinkWithActualCss(ZipFile epubFile, String htmlContent) throws IOException, ParserConfigurationException, ReadingException, SAXException, TransformerException {

		// <link rel="stylesheet" type="text/css" href="docbook-epub.css"/>

		String[] cssHrefAndLinkPart = getCssHrefAndLinkPart(htmlContent);

		while (cssHrefAndLinkPart != null) { // There may be multiple css links.

			String cssHref = cssHrefAndLinkPart[0];
			String linkPart = cssHrefAndLinkPart[1];

			for (int i = 0; i < getEntryNames().size(); i++) {
				String entryName = getEntryNames().get(i);

				int lastSlashIndex = entryName.lastIndexOf("/");
				String fileName = entryName.substring(lastSlashIndex + 1);
				fileName = encodeToHtml(fileName);

				if (cssHref.contains(fileName)) { // css exists.
					ZipEntry zipEntry = epubFile.getEntry(entryName);

					InputStream zipEntryInputStream = epubFile.getInputStream(zipEntry);

					BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(zipEntryInputStream));

					StringBuilder fileContent = new StringBuilder();

					fileContent.append("<style type=\"text/css\">");

					try {
						String line;
						while ((line = bufferedReader.readLine()) != null) {
							fileContent.append(line);
						}
					} finally {
						bufferedReader.close();
					}

					fileContent.append("</style>");

					htmlContent = htmlContent.replace(linkPart, fileContent.toString());

					cssHrefAndLinkPart = getCssHrefAndLinkPart(htmlContent);

					break;
				}
			}
		}

		return htmlContent;
	}

	// Distributing the css parts in the style tag to the belonging html tags.
	private String dissolveStyleTag(String trimmedFileContent) throws ReadingException {

		Pattern cssPattern = Pattern.compile("<style(.*?)</style>");

		Matcher matcher = cssPattern.matcher(trimmedFileContent);

		while (matcher.find()) { // There may be multiple style tags.
			String styleTagStr = matcher.group(1);

			Map<String, String> cssMap = getCssMap(styleTagStr);

			String htmlBody = getHtmlBody(trimmedFileContent);
			String htmlBodyToReplace = putCssIntoTags(cssMap, htmlBody);

			trimmedFileContent = trimmedFileContent.replace(htmlBody, htmlBodyToReplace);
			trimmedFileContent = trimmedFileContent.replace("<style" + styleTagStr + "</style>", "");
		}

		return trimmedFileContent;
	}

	private Map<String, String> getCssMap(String cssfileContent) {
		Map<String, String> cssMap = new HashMap<>();

		Pattern cssPattern = Pattern.compile("\\{(.*?)\\}");

		Matcher matcher = cssPattern.matcher(cssfileContent);
		while (matcher.find()) {
			String cssValue = matcher.group(1);

			int indexOfCurlyStart = matcher.start();
			int indexOfCssNameStart = indexOfCurlyStart - 1;

			StringBuilder cssNameBuilder = new StringBuilder();
			String cssName = null;
			while (indexOfCssNameStart >= 0) {

				// TODO: There may be multiple css names pointing to one cssValue e.g. .legalnotice p { text-align: left; } OR .legalnotice, p { text-align: left; }
				if (cssfileContent.charAt(indexOfCssNameStart) == '}' || cssfileContent.charAt(indexOfCssNameStart) == '/') {

					String builtCssName = cssNameBuilder.toString().trim();

					if (builtCssName.length() > 0) {
						cssName = cssNameBuilder.reverse().toString().trim();
						break;
					}
				}

				cssNameBuilder.append(cssfileContent.charAt(indexOfCssNameStart));
				indexOfCssNameStart--;
			}

			List<String> cssNameList = null;

			// Seperate them here by ' ', ',' '>' (known seperators)
			String seperator = null;
			if (cssName.contains(",")) {
				seperator = ",";
			} else if (cssName.contains(">")) {
				seperator = ">";
			} else if (cssName.contains(" ")) {
				seperator = " ";
			}

			if (seperator != null) {
				cssNameList = Arrays.asList(cssName.split(seperator));
			}

			if (cssNameList == null) { // Has one css name
				if (cssMap.containsKey(cssName)) {
					cssMap.put(cssName, cssMap.get(cssName) + " " + cssValue);
				} else {
					cssMap.put(cssName, cssValue);
				}
			} else { // Has multiple css names
				for (String cssNameItem : cssNameList) {
					if (cssMap.containsKey(cssNameItem)) {
						cssMap.put(cssNameItem, cssMap.get(cssNameItem) + " " + cssValue);
					} else {
						cssMap.put(cssNameItem, cssValue);
					}
				}
			}
		}

		return cssMap;
	}

	// TODO: Search htmlBody tags by cssName and put cssValues where they found.
	// e.g. div.mert, "margin-left:30px; padding-top:25px"
	// <div class="mert"> -> <div style="margin-left:30px; padding-top:25px">
	private String putCssIntoTags(Map<String, String> cssMap, String trimmedHtmlBody) {
		for (Map.Entry<String, String> cssEntry : cssMap.entrySet()) {

			String tagName = cssEntry.getKey();
			String className = null;
			int classNameLength = 0;

			int dotIndex = cssEntry.getKey().indexOf(".");
			if (dotIndex > 0) { // e.g. div.mert
				className = cssEntry.getKey().substring(dotIndex + 1);
				classNameLength = className.length();
				tagName = cssEntry.getKey().substring(0, dotIndex);
			}

			int startTagIndex = trimmedHtmlBody.indexOf("<" + tagName);

			while (startTagIndex != -1) {

				int endTagIndex = startTagIndex;

				while (trimmedHtmlBody.charAt(endTagIndex) != '>') {
					endTagIndex++;
				}
				endTagIndex++;

				// Not an empty tag and big enough for class attribute.
				if (trimmedHtmlBody.charAt(endTagIndex - 1) != '/' && (endTagIndex - startTagIndex) > (5 + classNameLength)) {

					String tag = trimmedHtmlBody.substring(startTagIndex, endTagIndex);

					if (className == null || tag.contains(className)) {

						// Remove redundant class.
						if (className != null) {
							int classEndIndex = tag.indexOf(className);
							int classStartIndex = classEndIndex - 1;

							while (tag.charAt(classStartIndex) != 'c') {
								classStartIndex--;
							}

							tag = tag.substring(0, classStartIndex) + tag.substring(classEndIndex + classNameLength + 1, tag.length());
						}

						int styleIndex = tag.indexOf("style=\"");

						String tagToReplace = null;
						if (styleIndex != -1) { // Already has a style tag. Put the value into it.
							tagToReplace = tag.substring(0, styleIndex + 6) + cssEntry.getValue() + tag.substring(styleIndex + 6, tag.length());
						} else {
							int insertStyleIndex = 1 + tagName.length() + 1; // '<' and ' '
							tagToReplace = tag.substring(0, insertStyleIndex) + "style=\"" + cssEntry.getValue() + "\" " + tag.substring(insertStyleIndex, tag.length());
						}

						trimmedHtmlBody = trimmedHtmlBody.replaceFirst(tag, tagToReplace);
					}
				}

				startTagIndex = trimmedHtmlBody.indexOf("<" + tagName, startTagIndex + 1);
			}
		}

		return trimmedHtmlBody;
	}

	private String replaceImgTag(String htmlBody, CssStatus cssStatus) {

		String srcHref = getImgSrcHref(htmlBody);

		while (srcHref != null) { // There may be multiple img tags.

			for (int i = 0; i < getEntryNames().size(); i++) {
				String entryName = getEntryNames().get(i);

				int lastSlashIndex = entryName.lastIndexOf("/");
				String fileName = entryName.substring(lastSlashIndex + 1);
				fileName = encodeToHtml(fileName);

				if (srcHref.contains(fileName)) { // image exists.
					ZipFile epubFile = null;

					try {
						String extension = getFileExtension(fileName);

						epubFile = new ZipFile(this.zipFilePath);
						ZipEntry zipEntry = epubFile.getEntry(entryName);
						InputStream zipEntryInputStream = epubFile.getInputStream(zipEntry); // Convert inputStream to Base64Binary.
						byte[] imageAsBytes = convertIsToByteArray(zipEntryInputStream);

						byte[] imageAsBase64 = Base64.encodeBase64(imageAsBytes);
						String imageContent = new String(imageAsBase64);

						String src = "data:image/" + extension + ";base64," + imageContent;

						htmlBody = htmlBody.replace(srcHref, src);
						srcHref = getImgSrcHref(htmlBody);

						break;
					} catch (IOException e) {
						e.printStackTrace();
					} finally {
						if (epubFile != null) {
							try {
								epubFile.close();
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
					}
				}
			}

		}

		return htmlBody;
	}

	private String replaceTableTag(String htmlBodyToReplace) {
		int tableStartIndex = htmlBodyToReplace.indexOf(Constants.TAG_TABLE_START);

		while (tableStartIndex != -1) {
			int tableEndIndex = htmlBodyToReplace.indexOf(Constants.TAG_TABLE_END, tableStartIndex);

			if (tableEndIndex != -1) {
				Pattern rowPattern = Pattern.compile("<tr>(.*?)</tr>");
				Pattern cellPattern = Pattern.compile("<td>(.*?)</td>");

				String table = htmlBodyToReplace.substring(tableStartIndex, tableEndIndex);
				StringBuilder tableToReplace = null;

				Matcher rowMatcher = rowPattern.matcher(table);

				while (rowMatcher.find()) {
					String row = rowMatcher.group(1);

					if (tableToReplace == null) {
						tableToReplace = new StringBuilder();
					} else {
						tableToReplace.append("<br>");
						// tableToReplace.substring(0, tableToReplace.length() - 4); // Remove the last \t code.
					}

					Matcher cellMatcher = cellPattern.matcher(row);

					while (cellMatcher.find()) {
						String cell = cellMatcher.group(1);
						String strippedCell = cell.replaceAll("<[^>]*>", "");

						tableToReplace.append(strippedCell); // &#9; &nbsp;
					}
				}

				htmlBodyToReplace = htmlBodyToReplace.replace(table, tableToReplace.toString());
				tableStartIndex = htmlBodyToReplace.indexOf(Constants.TAG_TABLE_START);
			}
		}

		return htmlBodyToReplace;
	}

	private byte[] convertIsToByteArray(InputStream inputStream) throws IOException {
		byte[] buffer = new byte[8192];
		int bytesRead;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		while ((bytesRead = inputStream.read(buffer)) != -1) {
			output.write(buffer, 0, bytesRead);
		}

		return output.toByteArray();
	}

	private String[] getCssHrefAndLinkPart(String htmlContent) {
		int indexOfLinkStart = htmlContent.indexOf("<link");

		if (indexOfLinkStart != -1) {
			int indexOfLinkEnd = htmlContent.indexOf(Constants.TAG_END, indexOfLinkStart);

			String linkStr = htmlContent.substring(indexOfLinkStart, indexOfLinkEnd + 2);

			int indexOfHrefStart = linkStr.indexOf("href=\"");
			int indexOfHrefEnd = linkStr.indexOf("\"", indexOfHrefStart + 6);

			String cssHref = linkStr.substring(indexOfHrefStart + 6, indexOfHrefEnd);

			if (cssHref.endsWith(Constants.EXTENSION_CSS)) {
				return new String[] { cssHref, linkStr };
			}
		}

		return null;
	}

	private String getImgSrcHref(String htmlBody) {
		int indexOfImgStart = htmlBody.indexOf("<img");

		if (indexOfImgStart != -1) {
			int indexOfImgEnd = htmlBody.indexOf(Constants.TAG_END, indexOfImgStart);

			String imgPart = htmlBody.substring(indexOfImgStart, indexOfImgEnd + 2);

			int indexOfSrcStart = imgPart.indexOf("src=\"");
			int indexOfSrcEnd = imgPart.indexOf("\"", indexOfSrcStart + 5);

			String srcHref = imgPart.substring(indexOfSrcStart + 5, indexOfSrcEnd);

			if (!srcHref.contains("data:image")) { // Not replaced before.
				return srcHref;
			}
		}

		return null;
	}

	// Removes all the tags from htmlBody and returns it.
	private String getOnlyTextContent(String entryName, String htmlBody, int trimStartPosition, int trimEndPosition) {
		List<TagInfo> tagStartEndPositions = this.entryTagPositions.get(entryName);

		List<String> stringsToRemove = new ArrayList<>();

		if (trimEndPosition == 0) {
			trimEndPosition = htmlBody.length();
		}

		// TODO: Sort these lists (or insert in order) to be able to break when greater than the endPositions. This way, we won't have to traverse all the list.
		// Or are they already sorted?
		for (TagInfo tagInfo : tagStartEndPositions) {

			// This may not work correctly. Opening and closing tags may differ from one another. We should only check for whichever is inserted first? So; opening only?
			if (tagInfo.getOpeningTagStartPosition() > trimEndPosition) {
				break;
			}

			if (tagInfo.getOpeningTagStartPosition() == tagInfo.getClosingTagStartPosition()) {
				if (tagInfo.getOpeningTagStartPosition() > trimStartPosition && tagInfo.getOpeningTagStartPosition() < trimEndPosition) { // Empty Tag
					stringsToRemove.add(htmlBody.substring(tagInfo.getOpeningTagStartPosition() - 1, tagInfo.getOpeningTagStartPosition() + tagInfo.getTagName().length() + 2));
				}
			} else {
				if (tagInfo.getOpeningTagStartPosition() > trimStartPosition && tagInfo.getOpeningTagStartPosition() < trimEndPosition) { // Opening tag.
					stringsToRemove.add(htmlBody.substring(tagInfo.getOpeningTagStartPosition() - 1, tagInfo.getOpeningTagStartPosition() + tagInfo.getFullTagName().length() + 1));
				}

				if (tagInfo.getClosingTagStartPosition() > trimStartPosition && tagInfo.getClosingTagStartPosition() < trimEndPosition) { // Closing tag.
					stringsToRemove.add(htmlBody.substring(tagInfo.getClosingTagStartPosition() - 1, tagInfo.getClosingTagStartPosition() + tagInfo.getTagName().length() + 2));
				}
			}
		}

		htmlBody = htmlBody.substring(trimStartPosition, trimEndPosition);

		for (String stringToRemove : stringsToRemove) {
			htmlBody = htmlBody.replace(stringToRemove, "");
		}

		return htmlBody;
	}

	private String encodeToHtml(String fileName) {
		return fileName.replace("&", "&amp;").replace("Œ", "&OElig;").replace("œ", "&oelig;").replace("Ÿ", "&Yuml;");
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

	void setZipFilePath(String zipFilePath) {
		this.zipFilePath = zipFilePath;
	}

	String getZipFilePath() {
		return this.zipFilePath;
	}

}