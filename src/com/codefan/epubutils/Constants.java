package com.codefan.epubutils;

class Constants {

	// Core file names
	static final String FILE_NAME_CONTAINER_XML = "container.xml";
	static final String FILE_NAME_TOC_NCX = ".ncx";
	static final String FILE_NAME_PACKAGE_OPF = ".opf";

	// Keywords
	static final String TAG_BODY_START = "<body>";
	static final String TAG_BODY_END = "</body>";
	static final String TAG_TABLE_START = "<table";
	static final String TAG_TABLE_END = "</table>";
	
	static final char TAG_OPENING = '<';
	static final char TAG_CLOSING = '>';
	static final String TAG_END = "/>";
	static final String TAG_START = "</";
	static final char DOT = '.';
	
	static final String EXTENSION_CSS = ".css";

	static final String HTML_TAG_PATTERN = "<(\"[^\"]*\"|'[^']*'|[^'\">])*>";

}
