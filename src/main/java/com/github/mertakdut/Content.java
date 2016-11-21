package com.github.mertakdut;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

import com.github.mertakdut.BaseFindings.XmlItem;
import com.github.mertakdut.Package.Metadata;
import com.github.mertakdut.exception.OutOfPagesException;
import com.github.mertakdut.exception.ReadingException;

class Content {

	private String zipFilePath;

	private Container container;
	private Package opfPackage;
	private Toc toc;

	private List<String> entryNames;

	private Map<String, List<Tag>> entryTagPositions;
	private List<String> nonExistingHrefList;

	private int peakPage;

	private BookSection lastBookSectionInfo;

	public Content() {
		entryNames = new ArrayList<>();

		container = new Container();
		opfPackage = new Package();
		toc = new Toc();
	}

	// Debug
	void print() {
		System.out.println("Printing zipEntryNames...\n");

		for (int i = 0; i < entryNames.size(); i++) {
			System.out.println("(" + i + ")" + entryNames.get(i));
		}

		getContainer().print();
		getPackage().print();
		getToc().print();
	}

	BookSection maintainBookSections(int index) throws ReadingException, OutOfPagesException {

		if (peakPage == index) { // Moving in order.
			peakPage++;
		} else {
			while (peakPage < index) { // Trying to move forward. Calculate the ones before first.
				getBookSection(peakPage++);
			}
		}

		return getBookSection(index);

	}

	// TODO: A new method for only calculating book sections. That will also be useful for pre-loading the whole book.
	private BookSection getBookSection(int index) throws ReadingException, OutOfPagesException {

		BookSection bookSection = null;

		NavPoint navPoint = getNavPoint(index);

		if (Optionals.maxContentPerSection == 0 || navPoint.getTypeCode() == 0 || navPoint.getTypeCode() == 1) { // Real navPoint - actual file/anchor.
			bookSection = prepareBookSection(navPoint, index);
		} else { // Pseudo navPoint - trimmed file entry.
			bookSection = prepareTrimmedBookSection(navPoint, index);
		}

		getToc().setLastPageIndex(index);
		return bookSection;
	}

	private NavPoint getNavPoint(int index) throws ReadingException, OutOfPagesException {
		if (index >= 0) {
			if (getToc() != null) {
				List<NavPoint> navPoints = getToc().getNavMap().getNavPoints();

				if (navPoints != null) {
					if (index >= navPoints.size()) {
						throw new OutOfPagesException(index, navPoints.size());
					}

					return navPoints.get(index);
				}

			}

			throw new ReadingException("Term of Contents is null.");
		} else {
			throw new ReadingException("Index can't be less than 0");
		}
	}

	private BookSection prepareBookSection(NavPoint navPoint, int index) throws ReadingException, OutOfPagesException {

		BookSection bookSection = new BookSection();

		int trimStartPosition = navPoint.getBodyTrimStartPosition();
		int trimEndPosition = navPoint.getBodyTrimEndPosition();
		String entryName = navPoint.getEntryName();

		String fileContentStr = null;
		String htmlBody = null;

		if (!navPoint.isCalculated()) {

			String href = navPoint.getContentSrc();
			String label = navPoint.getNavLabel();

			boolean isSourceFileFound = false;

			for (int i = 0; i < getEntryNames().size(); i++) {
				String fileName = ContextHelper.encodeToUtf8(ContextHelper.getTextAfterCharacter(getEntryNames().get(i), Constants.SLASH));

				if (href.equals(fileName) || (href.startsWith(fileName) && href.replace(fileName, "").startsWith("%23"))) {

					isSourceFileFound = true;
					entryName = getEntryNames().get(i);

					fileContentStr = readFileContent(entryName);
					htmlBody = getHtmlBody(fileContentStr); // This must not be changed.

					if (!href.equals(fileName)) { // Anchored, e.g. #pgepubid00058
						Pair<Integer, Integer> bodyIntervals = getNextAvailableAnchorIndex2(index, entryName, htmlBody, href, fileName);

						if (bodyIntervals != null) {
							trimStartPosition = bodyIntervals.getFirst();
							trimEndPosition = bodyIntervals.getSecond();
						} else {
							return getBookSection(index);
						}
					}

					String extension = ContextHelper.getTextAfterCharacter(fileName, Constants.DOT);
					String mediaType = getMediaType(fileName);

					// If fileContentStr is too long; crop it by the maxContentPerSection.
					// Save the fileContent and position within a new navPoint, insert it after current index.
					if (Optionals.maxContentPerSection != 0) { // maxContentPerSection is given.
						int calculatedTrimEndPosition = calculateTrimEndPosition(entryName, htmlBody, trimStartPosition, trimEndPosition);

						if (calculatedTrimEndPosition != -1) {
							trimEndPosition = calculatedTrimEndPosition;

							NavPoint nextEntryNavPoint = new NavPoint();

							nextEntryNavPoint.setTypeCode(2);
							nextEntryNavPoint.setEntryName(entryName);
							nextEntryNavPoint.setBodyTrimStartPosition(trimEndPosition);

							getToc().getNavMap().getNavPoints().add(index + 1, nextEntryNavPoint);

							// Inserting calculated info to avoid calculating this navPoint again. In the future these data could be written to Term of Contents file.
							getToc().getNavMap().getNavPoints().get(index).setTypeCode(2); // To indicate that, this is a trimmed part. TODO: Change these with constants.

							if (lastBookSectionInfo == null) {
								lastBookSectionInfo = new BookSection();
							}

							lastBookSectionInfo.setExtension(extension);
							lastBookSectionInfo.setLabel(label);
							lastBookSectionInfo.setMediaType(mediaType);
						}
					}

					bookSection.setExtension(extension);
					bookSection.setLabel(label);
					bookSection.setMediaType(mediaType);

					break;
				}
			}

			if (!isSourceFileFound) {
				System.out.println("Source file not found!");
				getToc().getNavMap().getNavPoints().remove(index);
				return getBookSection(index);
			}

		} else { // Calculated before.
			fileContentStr = readFileContent(entryName);
			htmlBody = getHtmlBody(fileContentStr);
		}

		if (Optionals.isIncludingTextContent) {
			bookSection.setSectionTextContent(getOnlyTextContent(entryName, htmlBody, trimStartPosition, trimEndPosition));
		}

		if (Optionals.cssStatus == CssStatus.OMIT) {
			searchForTableTags(entryName, htmlBody, trimStartPosition, trimEndPosition);
		}

		String htmlBodyToReplace = appendIncompleteTags(htmlBody, entryName, index, trimStartPosition, trimEndPosition);

		htmlBodyToReplace = replaceImgTag(htmlBodyToReplace);
		fileContentStr = fileContentStr.replace(htmlBody, htmlBodyToReplace);

		if (Optionals.cssStatus == CssStatus.DISTRIBUTE) {
			fileContentStr = dissolveStyleTag(fileContentStr);
		}

		bookSection.setSectionContent(fileContentStr);
		return bookSection;
	}

	private BookSection prepareTrimmedBookSection(NavPoint entryNavPoint, int index) throws ReadingException, OutOfPagesException {

		BookSection bookSection = new BookSection();

		String entryName = entryNavPoint.getEntryName();
		int bodyTrimStartPosition = entryNavPoint.getBodyTrimStartPosition();
		int bodyTrimEndPosition = entryNavPoint.getBodyTrimEndPosition(); // Will be calculated on the first attempt.

		String fileContent = readFileContent(entryName);
		String htmlBody = getHtmlBody(fileContent);
		String htmlBodyToReplace = null;

		if (!entryNavPoint.isCalculated()) { // Not calculated before.
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

					if (bodyTrimEndPosition == -1) {
						return getBookSection(index);
					}
				}
			}

			int calculatedTrimEndPosition = calculateTrimEndPosition(entryName, htmlBody, bodyTrimStartPosition, bodyTrimEndPosition);

			if (calculatedTrimEndPosition != -1) { // Trimming again if needed.
				bodyTrimEndPosition = calculatedTrimEndPosition;

				NavPoint nextEntryNavPoint = new NavPoint();

				nextEntryNavPoint.setTypeCode(2);
				nextEntryNavPoint.setEntryName(entryName);
				nextEntryNavPoint.setBodyTrimStartPosition(bodyTrimEndPosition);

				getToc().getNavMap().getNavPoints().add(index + 1, nextEntryNavPoint);
			}

		}

		if (Optionals.cssStatus == CssStatus.OMIT) {
			searchForTableTags(entryName, htmlBody, bodyTrimStartPosition, bodyTrimEndPosition);
		}

		htmlBodyToReplace = appendIncompleteTags(htmlBody, entryName, index, bodyTrimStartPosition, bodyTrimEndPosition);

		htmlBodyToReplace = replaceImgTag(htmlBodyToReplace);

		if (Optionals.isIncludingTextContent) {
			bookSection.setSectionTextContent(getOnlyTextContent(entryName, htmlBody, bodyTrimStartPosition, bodyTrimEndPosition));
		}

		fileContent = fileContent.replace(htmlBody, htmlBodyToReplace);

		if (Optionals.cssStatus == CssStatus.DISTRIBUTE) {
			fileContent = dissolveStyleTag(fileContent);
		}

		bookSection.setSectionContent(fileContent);

		if (this.lastBookSectionInfo != null) {
			bookSection.setExtension(this.lastBookSectionInfo.getExtension());
			bookSection.setLabel(this.lastBookSectionInfo.getLabel());
			bookSection.setMediaType(this.lastBookSectionInfo.getMediaType());
		}

		return bookSection;
	}

	/*
	 * This method calculates and keeps every tag indices of the given entry file. Later on, these calculations will be used when trimming the entry.
	 * 
	 * e.g. If the open-close tag indices are in the same trimmed part; tag will be closed there and won't disturb the next trimmed part.
	 * 
	 * If the open-close tag indices are not in the same trimmed part; tag will be closed at the end of the current trimmed part, and opened in the next trimmed part.
	 */
	private void calculateEntryTagPositions(String entryName, String htmlBody) {

		List<Tag> tagList = new ArrayList<>();
		this.entryTagPositions.put(entryName, tagList);

		List<Tag> openedTags = null;
		ListIterator<Tag> listIterator = null;

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

				// Warning: There may be looks to be opening tags but empty tags like <br>. Find a workaround for them. Or are they already skipped? Since the closing tag would never be found.
				if (htmlBody.charAt(i - 1) != '/') { // Not an empty tag.
					String tagStr = possiblyTag.toString();

					matcher = pattern.matcher(tagStr);

					if (matcher.matches()) {
						if (tagStr.charAt(1) == '/') { // Closing tag. Match it with the last open tag with the same name.
							String tagName = getFullTagName(tagStr, false);

							listIterator = openedTags.listIterator(openedTags.size());

							while (listIterator.hasPrevious()) {
								Tag openedTag = listIterator.previous();

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

							Tag tag = new Tag();
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

		Tag tag = new Tag();
		tag.setOpeningTagStartPosition(openingPosition);
		tag.setClosingTagStartPosition(closingPosition);
		tag.setFullTagName(fullTagName);
		tag.setTagName(getTagName(fullTagName));

		List<Tag> tagList = this.entryTagPositions.get(entryName);

		int index = tagList.size();
		while (index > 0 && tagList.get(index - 1).getOpeningTagStartPosition() > openingPosition) {
			index--;
		}

		tagList.add(index, tag);

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

	private Pair<Integer, Integer> getNextAvailableAnchorIndex2(int index, String entryName, String htmlBody, String href, String fileName) throws ReadingException, OutOfPagesException {

		boolean isNavigatingToNextFile = false;

		String currentAnchor = null;
		String nextAnchor = null;

		boolean isFileReadFirstTime = isFileReadFirstTime(index, entryName);

		if (isFileReadFirstTime) { // No previous anchor; so it should start from the beginning to the current anchor.

			NavPoint currentEntryNavPoint = new NavPoint();

			currentEntryNavPoint.setTypeCode(0);
			currentEntryNavPoint.setContentSrc(fileName); // href or fileName?

			getToc().getNavMap().getNavPoints().add(index, currentEntryNavPoint);

			nextAnchor = href.replace(fileName, "");
		} else {
			currentAnchor = href.replace(fileName, "");
			nextAnchor = getNextAnchor(index, entryName);
		}

		currentAnchor = convertAnchorToHtml(currentAnchor);
		nextAnchor = convertAnchorToHtml(nextAnchor);

		if (currentAnchor != null && nextAnchor != null) {

			int currentAnchorIndex = htmlBody.indexOf(currentAnchor);
			int nextAnchorIndex = htmlBody.indexOf(nextAnchor);

			// Abnormality in toc.ncx file. Its order is probably given wrong.
			// Warning: This may break the navPoints order if all the order is malformed.
			if (currentAnchorIndex > nextAnchorIndex) {
				int tmp = currentAnchorIndex;
				currentAnchorIndex = nextAnchorIndex;
				nextAnchorIndex = tmp;

				Collections.swap(getToc().getNavMap().getNavPoints(), index, index + 1);
			}

			if (currentAnchorIndex == -1 || nextAnchorIndex == -1) {

				int tmpIndex = index;

				if (currentAnchorIndex == -1 && nextAnchorIndex == -1) { // Both of the anchors not found.
					getToc().getNavMap().getNavPoints().get(tmpIndex++).setMarkedToDelete(true); // Delete the first one (current anchor)
					getToc().getNavMap().getNavPoints().get(tmpIndex++).setMarkedToDelete(true); // Delete the second one (next anchor)
					currentAnchor = null;
					nextAnchor = null;
				} else if (currentAnchorIndex == -1) { // Current anchor not found.
					getToc().getNavMap().getNavPoints().get(tmpIndex++).setMarkedToDelete(true); // Delete the first one (current anchor)
					currentAnchor = nextAnchor;
				} else if (nextAnchorIndex == -1) { // Next anchor not found.
					getToc().getNavMap().getNavPoints().get(++tmpIndex).setMarkedToDelete(true); // Delete the second one (next anchor)
					nextAnchor = null;
				}

				int markedNavPoints = tmpIndex - index;

				// Next available anchor should be the next starting point.
				while (tmpIndex < getToc().getNavMap().getNavPoints().size()) { // Looping until next anchor is found.

					boolean isCurrentNavPointMarked = true;

					String possiblyNextEntryName = getNavPoint(tmpIndex).getContentSrc();

					if (possiblyNextEntryName.startsWith(fileName) && possiblyNextEntryName.replace(fileName, "").startsWith("%23")) {

						String anchor = possiblyNextEntryName.replace(fileName, "");
						anchor = convertAnchorToHtml(anchor);

						if (htmlBody.contains(anchor)) {
							if (currentAnchor == null) { // If current anchor is not found, first set that.
								currentAnchor = anchor;
								isCurrentNavPointMarked = false;
							} else { // If current anchor is already defined set the next anchor and break.
								nextAnchor = anchor;
								break;
							}
						}
					} else { // TODO: Next content is not the same file as the current one. Anchors are broken. Navigate to the next file.
						isNavigatingToNextFile = true;
						break;
					}

					if (isCurrentNavPointMarked) {
						getToc().getNavMap().getNavPoints().get(tmpIndex).setMarkedToDelete(true);
						markedNavPoints++;
					}

					tmpIndex++;
				}

				if (markedNavPoints != 0) {

					if (markedNavPoints == getToc().getNavMap().getNavPoints().size()) {
						throw new ReadingException("There are no items left in TOC. Toc.ncx file is probably malformed.");
					}

					for (Iterator<NavPoint> iterator = getToc().getNavMap().getNavPoints().iterator(); iterator.hasNext();) {
						NavPoint navPointToDelete = iterator.next();
						if (navPointToDelete.isMarkedToDelete()) {
							iterator.remove();

							if (--markedNavPoints == 0) {
								break;
							}
						}
					}

					this.peakPage -= markedNavPoints;
				}

			}
		}

		if (isNavigatingToNextFile) {
			return null;
		} else {
			return getAnchorsInterval(htmlBody, currentAnchor, nextAnchor);
		}

	}

	// TODO: Similar functionality happens in the prepareBookSection method. Merge them into this.
	private int getNextAvailableAnchorIndex(int index, String entryName, int bodyTrimStartPosition, String htmlBody) throws ReadingException, OutOfPagesException {

		getToc().getNavMap().getNavPoints().remove(++index); // Removing the nextAnchor from navPoints; 'cause it's already not found.

		int markedNavPoints = 0;

		int anchorIndex = -1;

		boolean isNextAnchorFound = false;
		boolean isNavigatingToNextFile = false;

		// Next available anchor should be the next starting point.
		while (index < getToc().getNavMap().getNavPoints().size()) { // Looping until next anchor is found.

			String possiblyNextEntryName = getNavPoint(index).getContentSrc();

			String fileName = ContextHelper.encodeToUtf8(ContextHelper.getTextAfterCharacter(entryName, Constants.SLASH));

			if (possiblyNextEntryName.startsWith(fileName) && possiblyNextEntryName.replace(fileName, "").startsWith("%23")) {
				String anchor = possiblyNextEntryName.replace(fileName, "");
				String anchorHtml = convertAnchorToHtml(anchor);
				anchorIndex = htmlBody.indexOf(anchorHtml);

				if (anchorIndex != -1) {

					while (htmlBody.charAt(anchorIndex) != Constants.TAG_OPENING) { // Getting just before anchor html.
						anchorIndex--;
					}

					if (bodyTrimStartPosition <= anchorIndex) {
						isNextAnchorFound = true;
						break;
					}
				}
			} else { // TODO: Next content is not the same file as the current one. Anchors are broken. Navigate to the next file.
				isNavigatingToNextFile = true;
				break;
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

			this.peakPage -= markedNavPoints;
		}

		if (isNavigatingToNextFile) {
			return -1;
		} else if (isNextAnchorFound) {
			return anchorIndex;
		} else {
			return 0;
		}
	}

	private String prepareOpeningTags(List<Tag> openedTags) {

		StringBuilder openingTagsBuilder = new StringBuilder();

		for (ListIterator<Tag> iterator = openedTags.listIterator(); iterator.hasNext();) {
			openingTagsBuilder.append(Constants.TAG_OPENING).append(iterator.next().getFullTagName()).append(Constants.TAG_CLOSING);
		}

		return openingTagsBuilder.toString();
	}

	private String prepareClosingTags(List<Tag> openedTags) {

		StringBuilder closingTagsBuilder = new StringBuilder();

		for (ListIterator<Tag> iterator = openedTags.listIterator(openedTags.size()); iterator.hasPrevious();) {
			closingTagsBuilder.append(Constants.TAG_START).append(iterator.previous().getTagName()).append(Constants.TAG_CLOSING);
		}

		return closingTagsBuilder.toString();
	}

	private int calculateTrimEndPosition(String entryName, String htmlBody, int trimStartPosition, int trimEndPos) {

		int trimEndPosition = (trimEndPos != 0 && (trimEndPos - trimStartPosition) < Optionals.maxContentPerSection) ? trimEndPos : trimStartPosition + Optionals.maxContentPerSection;

		int htmlBodyLength = htmlBody.length();

		// Don't need to trim. HtmlBody with tags are already below limit.
		if (htmlBodyLength < trimEndPosition || (trimEndPosition - trimStartPosition) < Optionals.maxContentPerSection) {
			return -1;
		}

		List<Tag> tagStartEndPositions = getTagStartEndPositions(entryName, htmlBody);

		int loopCount = 0;
		int lastTagsLength = 0;

		while (true) {
			int tagsLength = 0;

			for (Tag tag : tagStartEndPositions) {

				if (tag.getOpeningTagStartPosition() > trimEndPosition) {
					break;
				}

				if (tag.getOpeningTagStartPosition() == tag.getClosingTagStartPosition()) {
					if (tag.getOpeningTagStartPosition() > trimStartPosition && tag.getOpeningTagStartPosition() < trimEndPosition) { // Empty Tag.
						tagsLength += tag.getFullTagName().length() + 3; // < />
					}
				} else {
					if (tag.getOpeningTagStartPosition() > trimStartPosition && tag.getOpeningTagStartPosition() < trimEndPosition) { // Opening tag.
						tagsLength += tag.getFullTagName().length() + 2; // < >
					}

					if (tag.getClosingTagStartPosition() > trimStartPosition && tag.getClosingTagStartPosition() < trimEndPosition) { // Closing tag.
						tagsLength += tag.getTagName().length() + 3; // < />
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

			if (((trimEndPosition - trimStartPosition) - tagsLength) >= Optionals.maxContentPerSection) {
				break;
			}

			lastTagsLength = tagsLength;
			loopCount++;
		}

		// TODO: Regex to find table tags like: <table(*.?)>[</table>|</>]
		// TODO: This may break the maxContentPerSection rule. Check if the table content will exceed the limit.
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

	// Checks if we are in an html tag. If so, move forward or backward until the tag is over. Else, move backwards until we hit the blank.
	private int findEligibleEndPosition(List<Tag> tagStartEndPositions, String htmlBody, int trimEndPosition) {

		boolean isMovedToEndOfTag = false;

		for (Tag tag : tagStartEndPositions) {

			if (tag.getOpeningTagStartPosition() > trimEndPosition) {
				break;
			}

			if (tag.getOpeningTagStartPosition() == tag.getClosingTagStartPosition()) { // Empty tag.
				// Inside an empty tag.
				if (tag.getOpeningTagStartPosition() < trimEndPosition && (tag.getOpeningTagStartPosition() + tag.getFullTagName().length() + 2) > trimEndPosition) {

					while (htmlBody.charAt(trimEndPosition) != Constants.TAG_CLOSING) {
						trimEndPosition++;
					}

					trimEndPosition++;
					isMovedToEndOfTag = true;
					break;
				}
			} else {
				// Inside an opening tag.
				if (tag.getOpeningTagStartPosition() < trimEndPosition && (tag.getOpeningTagStartPosition() + tag.getFullTagName().length() + 1) > trimEndPosition) {

					while (htmlBody.charAt(trimEndPosition) != Constants.TAG_OPENING) {
						trimEndPosition--;
					}

					// trimEndPosition--;
					isMovedToEndOfTag = true;
					break;
				}

				// Inside a closing tag.
				if (tag.getClosingTagStartPosition() < trimEndPosition && (tag.getClosingTagStartPosition() + tag.getTagName().length() + 2) > trimEndPosition) {

					while (htmlBody.charAt(trimEndPosition) != Constants.TAG_CLOSING) {
						trimEndPosition++;
					}

					trimEndPosition++;
					isMovedToEndOfTag = true;
					break;
				}
			}
		}

		if (!isMovedToEndOfTag) { // To avoid dividing the words in half.

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

	private String getNextAnchor(int index, String entryName) throws ReadingException, OutOfPagesException {

		if (getToc().getNavMap().getNavPoints().size() > (index + 1)) {
			NavPoint nextNavPoint = getNavPoint(index + 1);

			if (nextNavPoint.getTypeCode() != 2) { // Real navPoint. Only real navPoints are anchored. TODO: Change these with constants.

				String nextHref = nextNavPoint.getContentSrc();

				if (nextHref != null) {
					String fileName = ContextHelper.encodeToUtf8(ContextHelper.getTextAfterCharacter(entryName, Constants.SLASH));

					if (nextHref.startsWith(fileName) && nextHref.replace(fileName, "").startsWith("%23")) { // Both anchors are in the same file.
						return nextHref.replace(fileName, "");
					}
				}
			}
		}

		return null;
	}

	private boolean isFileReadFirstTime(int index, String entryName) throws ReadingException, OutOfPagesException {

		if ((index - 1) >= 0) {

			NavPoint prevNavPoint = getNavPoint(index - 1);

			if (prevNavPoint.getTypeCode() == 2) {
				return false;
			}

			String prevHref = prevNavPoint.getContentSrc();

			if (prevHref != null) {
				String fileName = ContextHelper.encodeToUtf8(ContextHelper.getTextAfterCharacter(entryName, Constants.SLASH));

				if (prevHref.startsWith(fileName)) { // Same content as previous, not reading for the first time. (&& prevHref.replace(fileName, "").startsWith("%23"))
					return false;
				}
			}

		}

		return true;
	}

	// TODO: This operation is getting expensive and expensive. fileContent could be held in cache; if the entry is same. Maybe a map with one element -> <entryName, fileContent>
	// If map doesn't contain that entryName -> then this method can be used.
	private String readFileContent(String entryName) throws ReadingException {

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

			if (Optionals.cssStatus != CssStatus.OMIT) {
				fileContentStr = replaceCssLinkWithActualCss(epubFile, fileContentStr);
			} else {
				fileContentStr = removeStyleTags(fileContentStr);
			}

			if (Optionals.isOmittingTitleTag) {
				fileContentStr = removeTitleTags(fileContentStr);
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

	private String getHtmlBody(String htmlContent) throws ReadingException {

		int startOfBody = htmlContent.lastIndexOf(Constants.TAG_BODY_START);
		int endOfBody = htmlContent.lastIndexOf(Constants.TAG_BODY_END);

		int bodyStartEndIndex = startOfBody + Constants.TAG_BODY_START.length();

		while (htmlContent.charAt(bodyStartEndIndex) != Constants.TAG_CLOSING) {
			bodyStartEndIndex++;
		}

		if (startOfBody != -1 && endOfBody != -1) {
			return htmlContent.substring(bodyStartEndIndex + 1, endOfBody);
		} else {
			throw new ReadingException("Exception while getting book section : Html body tags not found.");
		}
	}

	// Starts from current anchor, reads until the next anchor starts.
	private Pair<Integer, Integer> getAnchorsInterval(String htmlBody, String currentAnchor, String nextAnchor) throws ReadingException {

		int startOfCurrentAnchor = -1;
		int startOfNextAnchor = -1;

		if (currentAnchor != null && !currentAnchor.equals("")) {
			startOfCurrentAnchor = htmlBody.indexOf(currentAnchor);
		}

		if (nextAnchor != null && !nextAnchor.equals("")) {
			startOfNextAnchor = htmlBody.indexOf(nextAnchor);
		}

		if (startOfCurrentAnchor != -1) {
			while (htmlBody.charAt(startOfCurrentAnchor) != Constants.TAG_OPENING) {
				startOfCurrentAnchor--;
			}
		} else {
			startOfCurrentAnchor = 0;
		}

		if (startOfNextAnchor != -1) {
			while (htmlBody.charAt(startOfNextAnchor) != Constants.TAG_OPENING) {
				startOfNextAnchor--;
			}
		} else {
			startOfNextAnchor = 0;
		}

		return new Pair<>(startOfCurrentAnchor, startOfNextAnchor);
	}

	private String convertAnchorToHtml(String anchor) throws ReadingException { // #Page_1 to id="Page_1" converter

		if (anchor == null) {
			return null;
		}

		if (anchor.startsWith("%23")) { // Or UTF-8 equivalent of #
			return "id=\"" + anchor.substring(3) + "\"";
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

	// Distributing the css parts in the style tag to the belonging html tags.
	private String dissolveStyleTag(String trimmedFileContent) throws ReadingException {

		Pattern cssPattern = Pattern.compile("<style(.*?)>(.*?)</style>");

		Matcher matcher = cssPattern.matcher(trimmedFileContent);

		while (matcher.find()) { // There may be multiple style tags.
			String styleTagStr = matcher.group(2);

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

	private String replaceCssLinkWithActualCss(ZipFile epubFile, String htmlContent) throws IOException, ParserConfigurationException, ReadingException, SAXException, TransformerException {

		// <link rel="stylesheet" type="text/css" href="docbook-epub.css"/>

		Pattern linkTagPattern = Pattern.compile(ContextHelper.getTagsRegex("link", true));
		Pattern hrefPattern = Pattern.compile("href=\"(.*?)\"");

		Matcher linkMatcher = linkTagPattern.matcher(htmlContent);

		while (linkMatcher.find()) {
			String linkTag = linkMatcher.group(0);

			Matcher hrefMatcher = hrefPattern.matcher(linkTag);

			if (hrefMatcher.find()) {
				String cssHref = ContextHelper.getTextAfterCharacter(hrefMatcher.group(1), Constants.SLASH);

				if (cssHref.endsWith(".css")) { // Should we check for its type as well? text/css

					if (nonExistingHrefList != null && nonExistingHrefList.contains(cssHref)) {

						htmlContent = htmlContent.replace(linkTag, "");

					} else {

						boolean isCssFileFound = false;

						for (int i = 0; i < getEntryNames().size(); i++) {
							String entryName = getEntryNames().get(i);

							String fileName = ContextHelper.encodeToUtf8(ContextHelper.getTextAfterCharacter(entryName, Constants.SLASH));

							if (cssHref.equals(fileName)) { // css exists.
								isCssFileFound = true;

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

								htmlContent = htmlContent.replace(linkTag, fileContent.toString());

								break;
							}
						}

						if (!isCssFileFound) {
							System.out.println("Referenced css file not found!");

							if (nonExistingHrefList == null) {
								nonExistingHrefList = new ArrayList<>();
							}

							nonExistingHrefList.add(cssHref);

							htmlContent = htmlContent.replace(cssHref, "");
						}
					}

				}

			}

		}

		return htmlContent;
	}

	private String removeStyleTags(String fileContent) {
		return findAndRemove(fileContent, ContextHelper.getTagsRegex("style", false));
	}

	private String removeTitleTags(String fileContent) {
		return findAndRemove(fileContent, ContextHelper.getTagsRegex("title", false));
	}

	private String findAndRemove(String text, String regex) {

		Pattern titleTagPattern = Pattern.compile(regex);
		Matcher titleTagMatcher = titleTagPattern.matcher(text);

		StringBuffer stringBuffer = new StringBuffer();

		while (titleTagMatcher.find()) {
			titleTagMatcher.appendReplacement(stringBuffer, "");
		}

		if (stringBuffer.length() > 0) {
			titleTagMatcher.appendTail(stringBuffer);
			return stringBuffer.toString();
		}

		return text;
	}

	private String replaceImgTag(String htmlBody) throws ReadingException {

		Pattern imgTagPattern = Pattern.compile(ContextHelper.getTagsRegex("img", true));
		Pattern srcPattern = Pattern.compile("src=\"(.*?)\"");

		Matcher imgTagMatcher = imgTagPattern.matcher(htmlBody);

		while (imgTagMatcher.find()) {
			String imgPart = imgTagMatcher.group(0);

			Matcher srcMatcher = srcPattern.matcher(imgPart);

			if (srcMatcher.find()) {
				String srcHref = ContextHelper.getTextAfterCharacter(srcMatcher.group(1), Constants.SLASH);
				String encodedSrcHref = ContextHelper.encodeToUtf8(srcHref);

				if (nonExistingHrefList != null && nonExistingHrefList.contains(srcHref)) {
					htmlBody = htmlBody.replace(imgPart, "");
				} else {

					boolean isImageFileFound = false;

					for (int i = 0; i < getEntryNames().size(); i++) {
						String entryName = getEntryNames().get(i);

						String fileName = ContextHelper.encodeToUtf8(ContextHelper.getTextAfterCharacter(entryName, Constants.SLASH));

						if (encodedSrcHref.equals(fileName)) { // image exists.

							isImageFileFound = true;

							ZipFile epubFile = null;

							try {
								String extension = ContextHelper.getTextAfterCharacter(fileName, Constants.DOT);

								epubFile = new ZipFile(this.zipFilePath);
								ZipEntry zipEntry = epubFile.getEntry(entryName);
								InputStream zipEntryInputStream = epubFile.getInputStream(zipEntry); // Convert inputStream to Base64Binary.
								byte[] imageAsBytes = ContextHelper.convertIsToByteArray(zipEntryInputStream);

								byte[] imageAsBase64 = Base64.encodeBase64(imageAsBytes);
								String imageContent = new String(imageAsBase64);

								String src = "data:image/" + extension + ";base64," + imageContent;

								htmlBody = htmlBody.replace(srcHref, src);
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

					if (!isImageFileFound) {
						System.out.println("Referenced image file not found: " + srcHref);

						if (nonExistingHrefList == null) {
							nonExistingHrefList = new ArrayList<>();
						}

						nonExistingHrefList.add(srcHref);

						htmlBody = htmlBody.replace(imgPart, "");
					}

				}

			}
		}

		return htmlBody;
	}

	// Warning: May devour anchors.
	private void searchForTableTags(String entryName, String htmlBody, int trimStartPosition, int trimEndPosition) {

		String htmlBodyToReplace = null;

		if (trimEndPosition == 0) {
			htmlBodyToReplace = htmlBody.substring(trimStartPosition);
		} else {
			htmlBodyToReplace = htmlBody.substring(trimStartPosition, trimEndPosition);
		}

		Pattern tableTagPattern = Pattern.compile("<table.*?>", Pattern.DOTALL);
		Matcher tableTagMatcher = tableTagPattern.matcher(htmlBodyToReplace);

		if (tableTagMatcher.find()) {

			List<Tag> tagStartEndPositions = getTagStartEndPositions(entryName, htmlBody);

			List<Tag> tableTagList = new ArrayList<>();

			for (Tag tag : tagStartEndPositions) {

				if (tag.getOpeningTagStartPosition() > trimEndPosition) {
					break;
				}

				if (tag.getTagName().equals("table")) {

					if (tag.getOpeningTagStartPosition() != tag.getClosingTagStartPosition()) { // Not an empty table tag.

						if (tag.getOpeningTagStartPosition() > trimStartPosition && tag.getOpeningTagStartPosition() < trimEndPosition) { // Opening tag is within scope.

							tableTagList.add(tag);

							// if (tag.getClosingTagStartPosition() > trimStartPosition && tag.getClosingTagStartPosition() < trimEndPosition) { // Closing tag is also withing scope.
							//
							// }

						}

					}

				}

			}

			// Remove nested tables.
			List<Tag> smallerTableTagList = new ArrayList<>();

			for (int i = 0; i < tableTagList.size(); i++) {

				int tag1StartPosition = tableTagList.get(i).getOpeningTagStartPosition();
				int tag1EndPosition = tableTagList.get(i).getClosingTagStartPosition();

				for (int j = i + 1; j < tableTagList.size(); j++) {

					int tag2StartPosition = tableTagList.get(j).getOpeningTagStartPosition();
					int tag2EndPosition = tableTagList.get(j).getClosingTagStartPosition();

					if (tag1StartPosition > tag2StartPosition && tag1EndPosition < tag2EndPosition) {
						smallerTableTagList.add(tableTagList.get(i));
					} else if (tag2StartPosition > tag1StartPosition && tag2EndPosition < tag1EndPosition) {
						smallerTableTagList.add(tableTagList.get(j));
					}
				}
			}

			tableTagList.removeAll(smallerTableTagList);

			markTableTags(entryName, htmlBody, trimStartPosition, trimEndPosition, tableTagList);
		}
	}

	private void markTableTags(String entryName, String htmlBody, int trimStartPosition, int trimEndPosition, List<Tag> tableTagPositions) {

		List<Tag> tagStartEndPositions = getTagStartEndPositions(entryName, htmlBody);

		for (int i = 0; i < tableTagPositions.size(); i++) {

			int tableStartPosition = tableTagPositions.get(i).getOpeningTagStartPosition() - 1;
			int tableEndPosition = tableTagPositions.get(i).getClosingTagStartPosition() - 1;

			for (Tag tag : tagStartEndPositions) {

				if (tag.getOpeningTagStartPosition() > tableEndPosition) {
					break;
				}

				if (tag.getOpeningTagStartPosition() == tag.getClosingTagStartPosition()) { // Empty Tag

					if (tag.getTagName().equals("img")) {
						continue;
					}

					// TODO: We may have to break the row tabs with new lines (<br/>).

					if (tag.getOpeningTagStartPosition() > tableStartPosition && tag.getOpeningTagStartPosition() < tableEndPosition) {

						tag.setOmitted(true);
					}
				} else {
					if (tag.getOpeningTagStartPosition() > tableStartPosition && tag.getOpeningTagStartPosition() < tableEndPosition) { // Opening tag.

						tag.setOmitted(true);
					}

					if (tag.getClosingTagStartPosition() > tableStartPosition && tag.getClosingTagStartPosition() < tableEndPosition) { // Closing tag.

						tag.setOmitted(true);
					}
				}
			}
		}
	}

	// Removes all the tags from htmlBody and returns it.
	private String getOnlyTextContent(String entryName, String htmlBody, int trimStartPosition, int trimEndPosition) {

		List<Tag> tagStartEndPositions = getTagStartEndPositions(entryName, htmlBody);

		List<String> stringsToRemove = new ArrayList<>();

		if (trimEndPosition == 0) {
			trimEndPosition = htmlBody.length();
		}

		for (Tag tag : tagStartEndPositions) {

			if (tag.getOpeningTagStartPosition() > trimEndPosition) {
				break;
			}

			if (tag.getOpeningTagStartPosition() == tag.getClosingTagStartPosition()) { // Empty Tag
				if (tag.getOpeningTagStartPosition() > trimStartPosition && tag.getOpeningTagStartPosition() < trimEndPosition) {

					htmlBody = htmlBody.substring(0, tag.getOpeningTagStartPosition() - 1) + Constants.STRING_MARKER
							+ htmlBody.substring(tag.getOpeningTagStartPosition() - 1 + Constants.STRING_MARKER.length(),
									tag.getOpeningTagStartPosition() + tag.getFullTagName().length() + 2 - Constants.STRING_MARKER.length())
							+ Constants.STRING_MARKER + htmlBody.substring(tag.getOpeningTagStartPosition() + tag.getFullTagName().length() + 2, htmlBody.length());

					stringsToRemove.add(Constants.STRING_MARKER + htmlBody.substring(tag.getOpeningTagStartPosition() - 1 + Constants.STRING_MARKER.length(),
							tag.getOpeningTagStartPosition() + tag.getFullTagName().length() + 2 - Constants.STRING_MARKER.length()) + Constants.STRING_MARKER);
				}
			} else {
				if (tag.getOpeningTagStartPosition() > trimStartPosition && tag.getOpeningTagStartPosition() < trimEndPosition) { // Opening tag.

					htmlBody = htmlBody.substring(0, tag.getOpeningTagStartPosition() - 1) + Constants.STRING_MARKER
							+ htmlBody.substring(tag.getOpeningTagStartPosition() - 1 + Constants.STRING_MARKER.length(),
									tag.getOpeningTagStartPosition() + tag.getFullTagName().length() + 1 - Constants.STRING_MARKER.length())
							+ Constants.STRING_MARKER + htmlBody.substring(tag.getOpeningTagStartPosition() + tag.getFullTagName().length() + 1, htmlBody.length());

					stringsToRemove.add(Constants.STRING_MARKER + htmlBody.substring(tag.getOpeningTagStartPosition() - 1 + Constants.STRING_MARKER.length(),
							tag.getOpeningTagStartPosition() + tag.getFullTagName().length() + 1 - Constants.STRING_MARKER.length()) + Constants.STRING_MARKER);
				}

				if (tag.getClosingTagStartPosition() > trimStartPosition && tag.getClosingTagStartPosition() < trimEndPosition) { // Closing tag.

					htmlBody = htmlBody.substring(0, tag.getClosingTagStartPosition() - 1) + Constants.STRING_MARKER
							+ htmlBody.substring(tag.getClosingTagStartPosition() - 1 + Constants.STRING_MARKER.length(),
									tag.getClosingTagStartPosition() + tag.getTagName().length() + 2 - Constants.STRING_MARKER.length())
							+ Constants.STRING_MARKER + htmlBody.substring(tag.getClosingTagStartPosition() + tag.getTagName().length() + 2, htmlBody.length());

					stringsToRemove.add(Constants.STRING_MARKER + htmlBody.substring(tag.getClosingTagStartPosition() - 1 + Constants.STRING_MARKER.length(),
							tag.getClosingTagStartPosition() + tag.getTagName().length() + 2 - Constants.STRING_MARKER.length()) + Constants.STRING_MARKER);
				}
			}
		}

		htmlBody = htmlBody.substring(trimStartPosition, trimEndPosition);

		for (String stringToRemove : stringsToRemove) {
			htmlBody = htmlBody.replace(stringToRemove, "");
		}

		return htmlBody;
	}

	private String appendIncompleteTags(String htmlBody, String entryName, int index, int trimStartPosition, int trimEndPosition) throws ReadingException {

		if (!getToc().getNavMap().getNavPoints().get(index).isCalculated()) {
			getToc().getNavMap().getNavPoints().get(index).setBodyTrimStartPosition(trimStartPosition);
			getToc().getNavMap().getNavPoints().get(index).setBodyTrimEndPosition(trimEndPosition);
			getToc().getNavMap().getNavPoints().get(index).setEntryName(entryName);
			getToc().getNavMap().getNavPoints().get(index).setCalculated(true);
		}

		if (trimStartPosition == 0 && trimEndPosition == 0) {
			return htmlBody;
		}

		String htmlBodyToReplace = null;

		List<Tag> prevOpenedNotClosedYetTags = new ArrayList<>(); // Previously opened in this scope and not yet closed tags. Appending opening and closing tags.
		List<Tag> openedNotClosedYetTags = new ArrayList<>(); // Opened in this scope and not yet closed tags. Appending only closing tags.
		List<Tag> prevOpenedClosedTags = new ArrayList<>(); // Previously opened and closed in this scope. Appending only opening tags.

		List<Tag> currentEntryTags = getTagStartEndPositions(entryName, htmlBody);

		trimEndPosition = trimEndPosition == 0 ? htmlBody.length() : trimEndPosition;

		for (int i = 0; i < currentEntryTags.size(); i++) {
			Tag tag = currentEntryTags.get(i);

			if (tag.getOpeningTagStartPosition() > trimEndPosition) {
				break;
			}

			// Opened in the trimmed part, closed after the trimmed part.
			if (!tag.isOmitted() && tag.getOpeningTagStartPosition() > trimStartPosition && tag.getOpeningTagStartPosition() < trimEndPosition && tag.getClosingTagStartPosition() > trimEndPosition) {
				openedNotClosedYetTags.add(tag);
			}
		}

		List<Tag> prevOpenedTags = getToc().getNavMap().getNavPoints().get(index).getOpenTags();

		if (prevOpenedTags != null) {

			for (Tag prevOpenedTag : prevOpenedTags) {

				if (prevOpenedTag.getClosingTagStartPosition() > trimEndPosition) { // Previously opened and not yet closed in scope tags. Should have a place in the beginning.
					prevOpenedNotClosedYetTags.add(prevOpenedTag);
				} else { // Previously opened but closed in scope tags. // TODO: Find these tags a position :( Or just append them from the beginning. I don't think it would break anything, would it?
					prevOpenedClosedTags.add(prevOpenedTag);
				}

			}

		}

		Pair<String, List<String>> htmlBodyMarkingsPair = null;
		if (Optionals.cssStatus == CssStatus.OMIT) { // Tag omitting only happens in replaceTableTag function when css status is given Omit.
			htmlBodyMarkingsPair = markOmittedTags(currentEntryTags, htmlBody, trimStartPosition, trimEndPosition);

			if (htmlBodyMarkingsPair != null) {
				htmlBody = htmlBodyMarkingsPair.getFirst();
			}
		}

		// Warning: We shouldn't substring htmlBody before this method.
		if (trimEndPosition == htmlBody.length()) {
			htmlBodyToReplace = htmlBody.substring(trimStartPosition);
		} else {
			htmlBodyToReplace = htmlBody.substring(trimStartPosition, trimEndPosition);
		}

		if (htmlBodyMarkingsPair != null) {

			List<String> stringsToRemove = htmlBodyMarkingsPair.getSecond();

			if (stringsToRemove != null) {
				for (String stringToRemove : stringsToRemove) {

					if (stringToRemove.contains("|tr")) {
						htmlBodyToReplace = htmlBodyToReplace.replace(stringToRemove, "<br/>");
					} else {
						htmlBodyToReplace = htmlBodyToReplace.replace(stringToRemove, "");
					}

				}
			}

		}

		String openingTags = "";
		String closingTags = "";

		if (!openedNotClosedYetTags.isEmpty()) {
			closingTags += prepareClosingTags(openedNotClosedYetTags);
		}

		if (!prevOpenedNotClosedYetTags.isEmpty()) {
			openingTags += prepareOpeningTags(prevOpenedNotClosedYetTags);
			closingTags += prepareClosingTags(prevOpenedNotClosedYetTags);
		}

		if (!prevOpenedClosedTags.isEmpty()) {
			openingTags += prepareOpeningTags(prevOpenedClosedTags);
		}

		if (!openingTags.isEmpty() || !closingTags.isEmpty()) {
			htmlBodyToReplace = openingTags + htmlBodyToReplace + closingTags;
		}

		if (getToc().getNavMap().getNavPoints().size() > (index + 1)) { // If this is not the last page, next navPoint should start with not closed yet tags because they are not closed in this navPoint as well.
			openedNotClosedYetTags.addAll(prevOpenedNotClosedYetTags);
			getToc().getNavMap().getNavPoints().get(index + 1).setOpenTags(openedNotClosedYetTags.isEmpty() ? null : openedNotClosedYetTags);
		} else {
			openedNotClosedYetTags.addAll(prevOpenedNotClosedYetTags);
			if (!openedNotClosedYetTags.isEmpty()) { // openedTags should already be null if this is the last page.
				throw new ReadingException("Last Page has opened and not yet closed tags."); // For debugging purposes.
			}
		}

		return htmlBodyToReplace;
	}

	private Pair<String, List<String>> markOmittedTags(List<Tag> currentEntryTags, String htmlBody, int trimStartPosition, int trimEndPosition) {

		boolean isHtmlBodyModified = false;
		List<String> stringsToRemove = null;

		for (Tag tag : currentEntryTags) {

			if (tag.getOpeningTagStartPosition() > trimEndPosition) {
				break;
			}

			if (!tag.isOmitted()) {
				continue;
			}

			int fromIndex = -1;
			int toIndex = -1;

			if (tag.getOpeningTagStartPosition() == tag.getClosingTagStartPosition()) { // Empty Tag
				if (tag.getOpeningTagStartPosition() > trimStartPosition && tag.getOpeningTagStartPosition() < trimEndPosition) {

					fromIndex = tag.getOpeningTagStartPosition() - 1;
					toIndex = tag.getOpeningTagStartPosition() + tag.getFullTagName().length() + 2;
				}
			} else {
				if (tag.getOpeningTagStartPosition() > trimStartPosition && tag.getOpeningTagStartPosition() < trimEndPosition) { // Opening tag.

					fromIndex = tag.getOpeningTagStartPosition() - 1;
					toIndex = tag.getOpeningTagStartPosition() + tag.getFullTagName().length() + 1;

				}

				if (fromIndex != -1 && toIndex != -1) {

					htmlBody = htmlBody.substring(0, fromIndex) + Constants.STRING_MARKER + htmlBody.substring(fromIndex + Constants.STRING_MARKER.length(), toIndex - Constants.STRING_MARKER.length())
							+ Constants.STRING_MARKER + htmlBody.substring(toIndex, htmlBody.length());

					if (stringsToRemove == null) {
						stringsToRemove = new ArrayList<>();
					}

					stringsToRemove.add(Constants.STRING_MARKER + htmlBody.substring(fromIndex + Constants.STRING_MARKER.length(), toIndex - Constants.STRING_MARKER.length()) + Constants.STRING_MARKER);

					isHtmlBodyModified = true;
				}

				if (tag.getClosingTagStartPosition() > trimStartPosition && tag.getClosingTagStartPosition() < trimEndPosition) { // Closing tag.

					fromIndex = tag.getClosingTagStartPosition() - 1;
					toIndex = tag.getClosingTagStartPosition() + tag.getTagName().length() + 2;

				}
			}

			// If both opened and closed tags should be removed, skips the closing tag.
			if (fromIndex != -1 && toIndex != -1) {

				htmlBody = htmlBody.substring(0, fromIndex) + Constants.STRING_MARKER + htmlBody.substring(fromIndex + Constants.STRING_MARKER.length(), toIndex - Constants.STRING_MARKER.length())
						+ Constants.STRING_MARKER + htmlBody.substring(toIndex, htmlBody.length());

				if (stringsToRemove == null) {
					stringsToRemove = new ArrayList<>();
				}

				stringsToRemove.add(Constants.STRING_MARKER + htmlBody.substring(fromIndex + Constants.STRING_MARKER.length(), toIndex - Constants.STRING_MARKER.length()) + Constants.STRING_MARKER);

				isHtmlBodyModified = true;
			}
		}

		return isHtmlBodyModified ? new Pair<>(htmlBody, stringsToRemove) : null;
	}

	byte[] getCoverImage() throws ReadingException {
		Metadata metadata = this.opfPackage.getMetadata();

		if (this.opfPackage != null && metadata != null) {
			String coverImageId = metadata.getCoverImageId();

			if (coverImageId != null && !coverImageId.equals("")) {
				List<XmlItem> manifestXmlItems = this.opfPackage.getManifest().getXmlItemList();

				for (XmlItem xmlItem : manifestXmlItems) {
					if (xmlItem.getAttributes().get("id").equals(coverImageId)) {
						String coverImageEntryName = xmlItem.getAttributes().get("href");

						if (coverImageEntryName != null && !coverImageEntryName.equals("")) {
							ZipFile epubFile = null;
							try {
								try {
									epubFile = new ZipFile(this.getZipFilePath());
								} catch (IOException e) {
									e.printStackTrace();
									throw new ReadingException("Error initializing ZipFile: " + e.getMessage());
								}

								for (String entryName : this.getEntryNames()) {

									// TODO: I might have to change this contains with equals.
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
									if (epubFile != null) {
										epubFile.close();
									}
								} catch (IOException e) {
									e.printStackTrace();
									throw new ReadingException("Error closing ZipFile: " + e.getMessage());
								}
							}
						}
					}
				}
			}
		}

		return null;
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

	void setToc(Toc toc) {
		this.toc = toc;
	}

	void setZipFilePath(String zipFilePath) {
		this.zipFilePath = zipFilePath;
	}

	String getZipFilePath() {
		return this.zipFilePath;
	}

	List<Tag> getTagStartEndPositions(String entryName, String htmlBody) {
		if (entryTagPositions == null || !entryTagPositions.containsKey(entryName)) {
			if (entryTagPositions == null) {
				entryTagPositions = new HashMap<>();
			}

			calculateEntryTagPositions(entryName, htmlBody);
		}

		return entryTagPositions.get(entryName);
	}

}