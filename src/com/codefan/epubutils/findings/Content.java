package com.codefan.epubutils.findings;

import java.util.ArrayList;
import java.util.List;

public class Content {

	private Container container;
	private Package opfPackage;
	private Toc toc;

	private List<String> entryNames;

	public Content() {
		entryNames = new ArrayList<>();

		container = new Container();
		opfPackage = new Package();
		toc = new Toc();
	}

	// Debug
	public void print() throws IllegalArgumentException, IllegalAccessException {
		System.out.println("\n\nPrinting zipEntryNames...\n");

		for (int i = 0; i < entryNames.size(); i++) {
			System.out.println("(" + i + ")" + entryNames.get(i));
		}

		getContainer().print();
		getPackage().print();
		getToc().print();
	}

	public File getSourceFile(int index) {
		if (getToc() != null) {
		}

	}

	public List<String> getEntryNames() {
		return entryNames;
	}

	public void addEntryName(String zipEntryName) {
		entryNames.add(zipEntryName);
	}

	public Container getContainer() {
		return container;
	}

	public Package getPackage() {
		return opfPackage;
	}

	public Toc getToc() {
		return toc;
	}

}