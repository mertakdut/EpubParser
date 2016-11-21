package com.github.mertakdut.exception;

public class OutOfPagesException extends Exception {

	private int pageCount;

	private static final long serialVersionUID = 2607084451614265004L;

	public OutOfPagesException(int index, int pageCount) {
		super("Out of bounds at position: " + index + ", max length is: " + (pageCount - 1));
		this.pageCount = pageCount;
	}

	public int getPageCount() {
		return pageCount;
	}

}
