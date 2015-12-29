package com.codefan.epubutils;

public class BookSection {
	private String label;
	private String extension;
	private String sectionContent;
	private String mediaType;

	public String getLabel() {
		return label;
	}

	void setLabel(String label) {
		this.label = label;
	}

	public String getSectionContent() {
		return sectionContent;
	}

	void setSectionContent(String sectionContent) {
		this.sectionContent = sectionContent;
	}

	public String getExtension() {
		return extension;
	}

	void setExtension(String extension) {
		this.extension = extension;
	}

	public String getMediaType() {
		return mediaType;
	}

	void setMediaType(String mediaType) {
		this.mediaType = mediaType;
	}

}
