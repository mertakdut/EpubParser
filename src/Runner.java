import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.DOMException;
import org.xml.sax.SAXException;

public class Runner {

	public static void main(String[] args) {
		try {
			Reader reader = new Reader("C:\\eBooks/shute-lonely-road.epub");
			Content epubContent = reader.getContent("C:\\EpubUtils/");
			
		} catch (IOException | ParserConfigurationException | SAXException | IllegalArgumentException | IllegalAccessException | DOMException e) {
			e.printStackTrace();
			System.out.println(e.toString());
		}

	}

}
