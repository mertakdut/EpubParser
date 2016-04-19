package com.github.mertakdut;

class Logger {

	public enum Severity {
		info, warning, error
	};

	public void log(Severity severity, String message) {
		System.out.println(message);
	}

}
