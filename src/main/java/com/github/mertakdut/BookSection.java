package com.github.mertakdut;

public class BookSection {
	
	private String label;
	private String extension;
	private String sectionContent;
	private String mediaType;
	private String sectionTextContent;

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

	public String getSectionTextContent() {
		return sectionTextContent;
	}

	public void setSectionTextContent(String sectionTextContent) {
		this.sectionTextContent = sectionTextContent;
	}

}
