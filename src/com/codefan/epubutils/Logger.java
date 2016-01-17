package com.codefan.epubutils;

public class Logger {

	public enum Severity {
		info, warning, error
	};

	public void log(Severity severity, String message) {
		System.out.println(message);
	}

}
