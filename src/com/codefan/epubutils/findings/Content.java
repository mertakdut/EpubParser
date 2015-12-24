package com.codefan.epubutils.findings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.codefan.epubutils.findings.BaseFindings.NavPoint;
import com.codefan.epubutils.findings.BaseFindings.XmlItem;

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

	public String getEntryName(int index) throws IOException {

		if (index > 0) {
			if (getToc() != null) {

				boolean isFoundInPlayOrder = false;

				NavPoint navPoint = null;

				List<NavPoint> navPoints = getToc().getNavMap().getNavPoints();

				for (int i = 0; i < navPoints.size(); i++) {
					navPoint = getToc().getNavMap().getNavPoints().get(i);

					if (navPoint.getPlayOrder() == index) {
						isFoundInPlayOrder = true;
						break;
					}
				}

				if (!isFoundInPlayOrder) {
					throw new IOException(index + " not found in playOrder");
				}

				if (navPoint.getContentSrc() != null) {
					return navPoint.getContentSrc();
				} else { // Find from id
					List<XmlItem> xmlItemList = getPackage().getManifest().getXmlItemList();
					for (int j = 0; j < xmlItemList.size(); j++) {
						Map<String, String> attributeMap = xmlItemList.get(j).getAttributes();

						String id = attributeMap.get("id");

						if (id.contains(navPoint.getId())) {
							return attributeMap.get("href");
						}
					}
				}

			} else { // Try spine instead
				index -= 1; // index will start from 1, it's okay with
							// playOrder.
				List<XmlItem> spineItemList = getPackage().getSpine().getXmlItemList();

				XmlItem spineItem = spineItemList.get(index);

				String idRef = spineItem.getAttributes().get("idref");

				List<XmlItem> manifestItemList = getPackage().getManifest().getXmlItemList();

				for (int j = 0; j < manifestItemList.size(); j++) {
					Map<String, String> attributeMap = manifestItemList.get(j).getAttributes();

					String id = attributeMap.get("id");

					if (id.contains(idRef)) {
						return attributeMap.get("href");
					}
				}

			}
		} else {
			throw new IOException("index should be greater than 0");
		}

		return null;

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