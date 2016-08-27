package com.github.mertakdut.exception;
public class ReadingException extends Exception {

	private static final long serialVersionUID = -3674458503294310650L;

	public ReadingException(String message){
		super(message);
	}
	
	public ReadingException(String message, Throwable throwable) {
        super(message, throwable);
    }

}
