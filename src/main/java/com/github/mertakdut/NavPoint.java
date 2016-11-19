package com.github.mertakdut;

import java.io.Serializable;
import java.util.List;

public class NavPoint implements Serializable {

	private static final long serialVersionUID = -5558515239198872045L;

	private String id;
	private int playOrder;
	private String navLabel;
	private String contentSrc;

	private String type;
	private String value;

	private boolean markedToDelete;

	// Additional cropped file navPoint variables.
	private int typeCode; // 0 - realNavPoint, 1 - anchoredPart, 2 - trimmedPart
	private String entryName;
	private int bodyTrimStartPosition;
	private int bodyTrimEndPosition;
	private List<Tag> openTags;
	private boolean isCalculated;

	String getId() {
		return id;
	}

	void setId(String id) {
		this.id = id;
	}

	int getPlayOrder() {
		return playOrder;
	}

	void setPlayOrder(int playOrder) {
		this.playOrder = playOrder;
	}

	public String getNavLabel() {
		return navLabel;
	}

	void setNavLabel(String navLabel) {
		this.navLabel = navLabel;
	}

	public String getContentSrc() {
		return contentSrc;
	}

	void setContentSrc(String contentSrc) {
		this.contentSrc = contentSrc;
	}

	String getType() {
		return type;
	}

	void setType(String type) {
		this.type = type;
	}

	String getValue() {
		return value;
	}

	void setValue(String value) {
		this.value = value;
	}

	boolean isMarkedToDelete() {
		return markedToDelete;
	}

	void setMarkedToDelete(boolean markedToDelete) {
		this.markedToDelete = markedToDelete;
	}

	int getBodyTrimStartPosition() {
		return bodyTrimStartPosition;
	}

	void setBodyTrimStartPosition(int bodyTrimStartPosition) {
		this.bodyTrimStartPosition = bodyTrimStartPosition;
	}

	int getBodyTrimEndPosition() {
		return bodyTrimEndPosition;
	}

	void setBodyTrimEndPosition(int bodyTrimEndPosition) {
		this.bodyTrimEndPosition = bodyTrimEndPosition;
	}

	List<Tag> getOpenTags() {
		return openTags;
	}

	void setOpenTags(List<Tag> openTags) {
		this.openTags = openTags;
	}

	int getTypeCode() {
		return typeCode;
	}

	void setTypeCode(int typeCode) {
		this.typeCode = typeCode;
	}

	String getEntryName() {
		return entryName;
	}

	void setEntryName(String entryName) {
		this.entryName = entryName;
	}

	boolean isCalculated() {
		return isCalculated;
	}

	void setCalculated(boolean isCalculated) {
		this.isCalculated = isCalculated;
	}

	@Override
	public boolean equals(Object navPoint) {

		if (this.contentSrc != null) {
			return this.contentSrc.equals(((NavPoint) navPoint).getContentSrc());
		} else if (this.entryName != null) {
			return this.entryName.equals(((NavPoint) navPoint).getEntryName());
		} else {
			return false;
		}

	}
}
