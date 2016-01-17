package com.codefan.epubutils;

class TagInfo {
	private String tagName;
	private String fullTagName;
	private int openingTagPosition;
	private int closingTagPosition;

	public String getTagName() {
		return tagName;
	}

	public void setTagName(String tagName) {
		this.tagName = tagName;
	}

	public int getOpeningTagPosition() {
		return openingTagPosition;
	}

	public void setOpeningTagPosition(int openingTagPosition) {
		this.openingTagPosition = openingTagPosition;
	}

	public int getClosingTagPosition() {
		return closingTagPosition;
	}

	public void setClosingTagPosition(int closingTagPosition) {
		this.closingTagPosition = closingTagPosition;
	}

	public String getFullTagName() {
		return fullTagName;
	}

	public void setFullTagName(String fullTagName) {
		this.fullTagName = fullTagName;
	}

}
