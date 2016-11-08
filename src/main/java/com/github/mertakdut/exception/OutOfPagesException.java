package com.github.mertakdut.exception;

public class OutOfPagesException extends Exception {

	private int pageCount;

	private static final long serialVersionUID = 2607084451614265004L;

	public OutOfPagesException(String message, int pageCount) {
		super(message);
		this.pageCount = pageCount;
	}

	public int getPageCount() {
		return pageCount;
	}

}
