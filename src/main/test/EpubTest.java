import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

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

	private int booksRead;
	private long averageReadDuration;
	private long maxReadDuration;

	@Test
	public void test() {

		File epubDirectory = new File("C:\\eBooks/test");
		File[] epubFiles = epubDirectory.listFiles();

		CssStatus[] cssStatuses = new CssStatus[] { CssStatus.OMIT, CssStatus.INCLUDE }; // CssStatus.OMIT, CssStatus.DISTRIBUTE
		int[] maxContents = new int[] { 1250, 1000, 1500 };

		Reader reader = new Reader();

		if (epubFiles != null) {

			List<String> sectionContents = new ArrayList<>();
			long readDuration;

			for (CssStatus cssStatus : cssStatuses) {
				for (int maxContent : maxContents) {
					for (File epubFile : epubFiles) {

						if (!sectionContents.isEmpty()) {
							sectionContents.clear();
						}

						try {
							String epubFilePath = epubFile.getAbsolutePath();

							System.out.println("Reading file content (cssStatus: " + cssStatus + ", " + "maxContent: " + maxContent + " : " + epubFilePath);
							readDuration = System.nanoTime();

							reader.setMaxContentPerSection(maxContent);
							reader.setCssStatus(cssStatus);
							reader.setIsIncludingTextContent(true);

							try {
								reader.setFullContent(epubFilePath);
							} catch (Exception e) {
								e.printStackTrace();
								System.out.println("---------------------\nContent Exception: " + e.getMessage() + "\nMaxContent: " + maxContent + ", CssStatus: " + cssStatus + "\nFilePath: "
										+ epubFilePath + "\n---------------------");
								throw new Exception();
							}

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
								throw new Exception();
							} catch (OutOfPagesException e) {
								// e.printStackTrace();
							} catch (Exception e) {
								e.printStackTrace();
								throw new Exception();
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

								readDuration = System.nanoTime() - readDuration;
								booksRead++;

								averageReadDuration = ((averageReadDuration * (booksRead - 1)) + readDuration) / booksRead;

								if (readDuration > maxReadDuration) {
									maxReadDuration = readDuration;
								}
							} catch (Exception e) {
								System.out.println("GoingBackwards Exception: " + e.getMessage() + "\n MaxContent: " + maxContent + ", CssStatus: " + cssStatus + "\n FilePath: " + epubFilePath);
								e.printStackTrace();
								throw new Exception();
							}
						} catch (Exception e) {
						}
					}
				}
			}

			System.out.println("BooksRead: " + booksRead);
			System.out.println("MaxReadTime: " + (double) TimeUnit.MILLISECONDS.convert(maxReadDuration, TimeUnit.NANOSECONDS) / 1000 + " seconds");
			System.out.print("AverageReadTime: " + (double) TimeUnit.MILLISECONDS.convert(averageReadDuration, TimeUnit.NANOSECONDS) / 1000 + " seconds");
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

}
