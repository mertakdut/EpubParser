package com.codefan.epubutils;

/*
 * https://wiki.eclipse.org/EGit/User_Guide
 * Starting from existing Git Repositories 
 * */

/*
 * http://www.hxa.name/articles/content/epub-guide_hxa7241_2007.html
 * http://www.idpf.org/epub/30/spec/epub30-overview.html
 */

public class Runner {

	public static void main(String[] args) {
		try {
			Reader reader = new Reader("C:\\eBooks/Alice in Wonderland.epub");
			Content epubContent = reader.getContent();

			BookSection bookSection = epubContent.getBookSection(0);
			System.out.println("\n1st Book Section: \nlabel: " + bookSection.getLabel() + "; media-type: " + bookSection.getMediaType());

			System.out.println("content: " + bookSection.getSectionContent());

//			bookSection = epubContent.getNextBookSection();
//			System.out.println("\n2nd Book Section: \nlabel: " + bookSection.getLabel() + "; media-type: " + bookSection.getMediaType());
			//
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

}
