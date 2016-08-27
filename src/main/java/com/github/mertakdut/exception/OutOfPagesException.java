package com.github.mertakdut.exception;
public class OutOfPagesException extends Exception {
	
	private static final long serialVersionUID = 2607084451614265004L;

	public OutOfPagesException(String message){
		super(message);
	}
	
	public OutOfPagesException(String message, Throwable throwable) {
        super(message, throwable);
    }
}
