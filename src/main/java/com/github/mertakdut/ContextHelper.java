package com.github.mertakdut;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import com.github.mertakdut.exception.ReadingException;

/**
 * 
 * @author Mert
 * 
 *         Includes commonly needed general methods.
 */
public class ContextHelper {

	public static String encodeToUtf8(String stringToEncode) throws ReadingException {

		String encodedString = null;

		try {
			encodedString = URLDecoder.decode(stringToEncode, StandardCharsets.UTF_8.name());
			encodedString = URLEncoder.encode(encodedString, StandardCharsets.UTF_8.name()).replace("+", "%20");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			throw new ReadingException("UnsupportedEncoding while encoding, " + stringToEncode + ", : " + e.getMessage());
		}

		return encodedString;
	}

}
