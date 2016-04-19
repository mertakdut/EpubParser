import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.github.mertakdut.BookSection;
import com.github.mertakdut.CssStatus;
import com.github.mertakdut.Reader;
import com.github.mertakdut.exception.OutOfPagesException;
import com.github.mertakdut.exception.ReadingException;

public class EpubTest {

	static final String TAG_BODY_START = "<body";
	static final String TAG_BODY_END = "</body>";
	static final char TAG_CLOSING = '>';

	@Test
	public void test() {

		File epubDirectory = new File("C:\\eBooks/test");
		File[] epubFiles = epubDirectory.listFiles();

		CssStatus[] cssStatuses = new CssStatus[] { CssStatus.INCLUDE }; // CssStatus.OMIT, CssStatus.DISTRIBUTE
		int[] maxContents = new int[] { 1250 };

		Reader reader = new Reader();

		if (epubFiles != null) {

			List<String> sectionContents = new ArrayList<>();
//			Set<String> subjects = new HashSet<>();

			for (CssStatus cssStatus : cssStatuses) {
				for (int maxContent : maxContents) {
					for (File epubFile : epubFiles) {

						if (!sectionContents.isEmpty()) {
							sectionContents.clear();
						}

						String epubFilePath = epubFile.getAbsolutePath();

						System.out.println("Reading file content (cssStatus: " + cssStatus + ", " + "maxContent: " + maxContent + " : " + epubFilePath);

						reader.setMaxContentPerSection(maxContent);
						reader.setCssStatus(cssStatus);
						reader.setIsIncludingTextContent(true);

						try {
							reader.setFullContent(epubFilePath);
						} catch (Exception e) {
							e.printStackTrace();
							System.out.println("---------------------\nContent Exception: " + e.getMessage() + "\nMaxContent: " + maxContent + ", CssStatus: " + cssStatus + "\nFilePath: "
									+ epubFilePath + "\n---------------------");
						}

						// String[] subjectArray = reader.getInfoPackage().getMetadata().getSubjects();
						//
						// if (subjectArray != null) {
						// for (int i = 0; i < subjectArray.length; i++) {
						// subjects.add(subjectArray[i]);
						// }
						// }

						// System.out.println("Reading book section (cssStatus: " + cssStatus + ", " + "maxContent: " + maxContent + " : " + epubFilePath);

						BookSection bookSection;

						int pageCount = -1;

						try {
							for (int i = 0; i < 50; i++) {
								bookSection = reader.readSection(i);
								// System.out.println("\n" + i + "st Book Section: \nlabel: " + bookSection.getLabel() + "; media-type: " + bookSection.getMediaType());

								String sectionContent = getHtmlBody(bookSection.getSectionContent());
								sectionContents.add(sectionContent);

								// System.out.println("content: " + sectionContent);
								pageCount++;
								
								Assert.assertFalse(sectionContent.endsWith("<*.") || sectionContent.startsWith("*.>"));
							}
						} catch (ReadingException e) {
							e.printStackTrace();
							System.out.println("---------------------\nBookSection Exception: " + e.getMessage() + "\nMaxContent: " + maxContent + ", CssStatus: " + cssStatus + "\nFilePath: "
									+ epubFilePath + "\n---------------------");
						} catch (OutOfPagesException e) {
							// e.printStackTrace();
						} catch (Exception e) {
							e.printStackTrace();
						}

						// System.out.println("\n-------------------------------Going backwards!-------------------------------------\n");

						try {
							for (int i = pageCount; i >= 0; i--) {
								bookSection = reader.readSection(i);
								// System.out.println("\n" + i + "st Book Section: \nlabel: " + bookSection.getLabel() + "; media-type: " + bookSection.getMediaType());

								String sectionContent = getHtmlBody(bookSection.getSectionContent());
								// System.out.println("content: " + sectionContent);

								Assert.assertEquals(sectionContents.get(i), sectionContent);
							}
						} catch (Exception e) {
							System.out.println("GoingBackwards Exception: " + e.getMessage() + "\n MaxContent: " + maxContent + ", CssStatus: " + cssStatus + "\n FilePath: " + epubFilePath);
							e.printStackTrace();
						}
					}
				}
			}

			// System.out.println("\n\n----------Collected Subjects----------\n");
			//
			// for (String subject : subjects) {
			// System.out.println(subject);
			// }

		}

		// Reader reader = new Reader();
		//
		// reader.setMaxContentPerSection(1250);
		// reader.setCssStatus(CssStatus.OMIT);
		// reader.setIsIncludingTextContent(true);
		//
		// reader.setFullContent("C:\\eBooks/Alice in Wonderland.epub"); // shute-lonely-road

		// Package infoPackage = reader.getInfoPackage();
		// infoPackage.getMetadata().getLanguage();
		// reader.getCoverImage();

		// int k = 0;
		// for (int i = 0; i < 50; i++) {
		//
		// bookSection = epubContent.getBookSection(k);
		// System.out.println("\n" + k + "st Book Section: \nlabel: " + bookSection.getLabel() + "; media-type: " + bookSection.getMediaType());
		//
		// System.out.println("content: " + getHtmlBody(bookSection.getSectionContent()));
		//
		// k += getRandomBoolean() ? 1 : -1;
		//
		// if (k < 0)
		// k += 2;
		// }

		// TODO: shute-lonely-road 219. index calculateTrimEndPosition yanlýþ hesaplanýyor.

		// BookSection bookSection;
		//
		// bookSection = reader.readSection(1);
		// System.out.println("\n" + 1 + "st Book Section: \nlabel: " + bookSection.getLabel() + "; media-type: " + bookSection.getMediaType());
		// System.out.println("content: " + getHtmlBody(bookSection.getSectionContent()));
		// bookSection = reader.readSection(0);
		// System.out.println("\n" + 0 + "st Book Section: \nlabel: " + bookSection.getLabel() + "; media-type: " + bookSection.getMediaType());
		// System.out.println("content: " + getHtmlBody(bookSection.getSectionContent()));

		// for (int i = 0; i < 550; i++) {
		// bookSection = reader.readSection(i);
		// System.out.println("\n" + i + "st Book Section: \nlabel: " + bookSection.getLabel() + "; media-type: " + bookSection.getMediaType());
		//
		// // System.out.println("content: " + bookSection.getSectionContent());
		// System.out.println("content: " + getHtmlBody(bookSection.getSectionContent()));
		// }
		//
		// System.out.println("\n-------------------------------Going backwards!-------------------------------------\n");
		//
		// for (int i = 548; i >= 0; i--) {
		// bookSection = reader.readSection(i);
		// System.out.println("\n" + i + "st Book Section: \nlabel: " + bookSection.getLabel() + "; media-type: " + bookSection.getMediaType());
		//
		// System.out.println("content: " + getHtmlBody(bookSection.getSectionContent()));
		// // System.out.println("content: " + bookSection.getSectionContent());
		// }

		// int x = 5;

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

}
