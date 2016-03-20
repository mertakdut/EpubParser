package com.codefan.epubutils.test;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.codefan.epubutils.BookSection;
import com.codefan.epubutils.CssStatus;
import com.codefan.epubutils.OutOfPagesException;
import com.codefan.epubutils.Reader;
import com.codefan.epubutils.ReadingException;

public class EpubSingleTest {

	static final String TAG_BODY_START = "<body";
	static final String TAG_BODY_END = "</body>";
	static final char TAG_CLOSING = '>';

	@Test
	public void singleFileTest() throws ReadingException, OutOfPagesException {
		Reader reader = new Reader();

		reader.setMaxContentPerSection(1250);
		reader.setCssStatus(CssStatus.INCLUDE);
		reader.setIsIncludingTextContent(true);

		reader.setFullContent("C:\\eBooks/test/The Mockingbird Next Door_ Life With Har - Marja Mills.epub");

		// bookSection = reader.readSection(1);
		// System.out.println("\n" + 1 + "st Book Section: \nlabel: " + bookSection.getLabel() + "; media-type: " + bookSection.getMediaType());
		// System.out.println("content: " + getHtmlBody(bookSection.getSectionContent()));
		// bookSection = reader.readSection(0);
		// System.out.println("\n" + 0 + "st Book Section: \nlabel: " + bookSection.getLabel() + "; media-type: " + bookSection.getMediaType());
		// System.out.println("content: " + getHtmlBody(bookSection.getSectionContent()));

		BookSection bookSection;
		List<String> sectionContents = new ArrayList<>();
		int pageCount = -1;

		try {
			for (int i = 0; i < 550; i++) {
				bookSection = reader.readSection(i);
				System.out.println("\n" + i + "st Book Section: \nlabel: " + bookSection.getLabel() + "; media-type: " + bookSection.getMediaType());

				String sectionContent = getHtmlBody(bookSection.getSectionContent());
				sectionContents.add(sectionContent);

				System.out.println("content: " + sectionContent);
				pageCount++;
			}
		} catch (ReadingException e) {
			e.printStackTrace();
		} catch (OutOfPagesException e) {
			// e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// System.out.println("\n-------------------------------Going backwards!-------------------------------------\n");

		try {
			for (int i = pageCount; i >= 0; i--) {
				bookSection = reader.readSection(i);
				System.out.println("\n" + i + "st Book Section: \nlabel: " + bookSection.getLabel() + "; media-type: " + bookSection.getMediaType());

				String sectionContent = getHtmlBody(bookSection.getSectionContent());
				System.out.println("content: " + sectionContent);

				Assert.assertEquals(sectionContents.get(i), sectionContent);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String getHtmlBody(String htmlContent) throws ReadingException {
		int startOfBody = htmlContent.lastIndexOf(TAG_BODY_START);
		int endOfBody = htmlContent.lastIndexOf(TAG_BODY_END);

		int bodyStartEndIndex = startOfBody + TAG_BODY_START.length();

		while (htmlContent.charAt(bodyStartEndIndex) != TAG_CLOSING) {
			bodyStartEndIndex++;
		}

		if (startOfBody != -1 && endOfBody != -1) {
			return htmlContent.substring(bodyStartEndIndex + 1, endOfBody);
		} else {
			throw new ReadingException("Exception while getting book section : Html body tags not found.");
		}
	}

	private boolean getRandomBoolean() {
		return Math.random() < 0.5;
	}

}
