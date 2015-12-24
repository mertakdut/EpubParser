package com.codefan.epubutils.findings;

import java.io.InputStream;

public class BookSection {
	private String label;
	private InputStream fileContent;
	
	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public InputStream getFileContent() {
		return fileContent;
	}

	public void setFileContent(InputStream fileContent) {
		this.fileContent = fileContent;
	}

}
