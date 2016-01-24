package com.codefan.epubutils;

public class Runner {

	public static void main(String[] args) {
		try {

			Reader reader = new Reader();
			Content epubContent = reader.getContent("C:\\eBooks/The Man Who Would Be King.epub", 1000); // shute-lonely-road.epub

			BookSection bookSection;

			for (int i = 0; i < 100; i++) {
				bookSection = epubContent.getBookSection(i);
				System.out.println("\n" + i + "st Book Section: \nlabel: " + bookSection.getLabel() + "; media-type: " + bookSection.getMediaType());

				System.out.println("content: " + getHtmlBody(bookSection.getSectionContent()));
			}

			// bookSection = epubContent.getNextBookSection();
			// System.out.println("\n3rd Book Section: \nlabel: " + bookSection.getLabel() + "; media-type: " + bookSection.getMediaType());
			//
			// bookSection = epubContent.getPrevBookSection();
			// System.out.println("\n2nd Book Section: \nlabel: " + bookSection.getLabel() + "; media-type: " + bookSection.getMediaType());
			//
			// bookSection = epubContent.getBookSection(0);
			// System.out.println("\n1st Book Section: \nlabel: " + bookSection.getLabel() + "; media-type: " + bookSection.getMediaType());
			//
			// bookSection = epubContent.getBookSection(1);
			// System.out.println("\n2nd Book Section: \nlabel: " + bookSection.getLabel() + "; media-type: " + bookSection.getMediaType());

		} catch (ReadingException e) {
			e.printStackTrace();
			System.out.println(e.toString());
		}
	}

	private static String getHtmlBody(String htmlContent) throws ReadingException {
		int startOfBody = htmlContent.indexOf(Constants.TAG_BODY_START);
		int endOfBody = htmlContent.indexOf(Constants.TAG_BODY_END);

		if (startOfBody != -1 && endOfBody != -1) {
			return htmlContent.substring(startOfBody + Constants.TAG_BODY_START.length(), endOfBody);
		} else {
			throw new ReadingException("Exception while getting book section : Html body tags not found.");
		}
	}

}
