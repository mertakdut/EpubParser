package com.github.mertakdut;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;

import com.github.mertakdut.exception.ReadingException;

/**
 * 
 * @author Mert
 * 
 *         Includes commonly needed general methods.
 */
class ContextHelper {

	static String encodeToUtf8(String stringToEncode) throws ReadingException {

		String encodedString = null;

		try {
			encodedString = URLDecoder.decode(stringToEncode, "UTF-8"); // Charset.forName("UTF-8").name()
			encodedString = URLEncoder.encode(encodedString, "UTF-8").replace("+", "%20"); // Charset.forName("UTF-8").name()
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			throw new ReadingException("UnsupportedEncoding while encoding, " + stringToEncode + ", : " + e.getMessage());
		}

		return encodedString;
	}

	static byte[] convertIsToByteArray(InputStream inputStream) throws IOException {

		byte[] buffer = new byte[8192];
		int bytesRead;
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		while ((bytesRead = inputStream.read(buffer)) != -1) {
			output.write(buffer, 0, bytesRead);
		}

		return output.toByteArray();
	}

	static String getTextAfterCharacter(String text, char character) {

		int lastCharIndex = text.lastIndexOf(character);
		return text.substring(lastCharIndex + 1);

	}

	static String getTagsRegex(String tagName, boolean isIncludingEmptyTags) { // <style.*?</style> or <img.*?/>|<img.*?</img>

		if (isIncludingEmptyTags)
			return String.format("<%1$s.*?/>|<%1$s.*?</%1$s>", tagName);
		else
			return String.format("<%1$s.*?</%1$s>", tagName);

	}

	static void copy(InputStream input, OutputStream output) throws IOException {

		byte[] BUFFER = new byte[4096 * 1024];

		int bytesRead;
		while ((bytesRead = input.read(BUFFER)) != -1) {
			output.write(BUFFER, 0, bytesRead);
		}
	}

}
