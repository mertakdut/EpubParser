package com.github.mertakdut;

class Tag {
	private String tagName;
	private String fullTagName;
	private int openingTagStartPosition;
	private int closingTagStartPosition;
	private boolean isOmitted;

	public String getTagName() {
		return tagName;
	}

	public void setTagName(String tagName) {
		this.tagName = tagName;
	}

	public String getFullTagName() {
		return fullTagName;
	}

	public void setFullTagName(String fullTagName) {
		this.fullTagName = fullTagName;
	}

	public int getOpeningTagStartPosition() {
		return openingTagStartPosition;
	}

	public void setOpeningTagStartPosition(int openingTagStartPosition) {
		this.openingTagStartPosition = openingTagStartPosition;
	}

	public int getClosingTagStartPosition() {
		return closingTagStartPosition;
	}

	public void setClosingTagStartPosition(int closingTagStartPosition) {
		this.closingTagStartPosition = closingTagStartPosition;
	}

	public boolean isOmitted() {
		return isOmitted;
	}

	public void setOmitted(boolean isOmitted) {
		this.isOmitted = isOmitted;
	}

}
