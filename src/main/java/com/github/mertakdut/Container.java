package com.github.mertakdut;

import org.w3c.dom.Node;

import com.github.mertakdut.exception.ReadingException;

class Container extends BaseFindings {

	private XmlItem rootFile;

	public XmlItem getRootFile() {
		return rootFile;
	}

	public void setRootFile(XmlItem rootFile) {
		this.rootFile = rootFile;
	}

	public String getFullPathValue() throws ReadingException {
		if (getRootFile() != null && getRootFile().getAttributes() != null && getRootFile().getAttributes().containsKey("full-path") && getRootFile().getAttributes().get("full-path") != null
				&& !getRootFile().getAttributes().get("full-path").equals("")) {
			return getRootFile().getAttributes().get("full-path");
		} else {
			throw new ReadingException(Constants.EXTENSION_OPF + " file not found.");
		}
	}

	@Override
	public boolean fillContent(Node node) {
		if (node.getNodeName() != null && node.getNodeName().equals("rootfile")) {
			this.rootFile = nodeToXmlItem(node);
			return true;
		}

		return false;
	}

	// debug
	public void print() {
		System.out.println("\n\nPrinting Container...\n");
		System.out.println("title: " + (getRootFile() != null ? getRootFile().getValue() : null));
	}

}
