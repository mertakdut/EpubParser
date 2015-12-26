package com.codefan.epubutils;

public class BookSection {
	private String label;
	private String extension;
	private String sectionContent;

	public String getLabel() {
		return label;
	}

	protected void setLabel(String label) {
		this.label = label;
	}

	public String getSectionContent() {
		return sectionContent;
	}

	protected void setSectionContent(String sectionContent) {
		this.sectionContent = sectionContent;
	}

	public String getExtension() {
		return extension;
	}

	protected void setExtension(String extension) {
		this.extension = extension;
	}

}
