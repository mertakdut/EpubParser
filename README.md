# EpubParser

Epublib is a java library for parsing epub files.

EpubParser lets you read the epub content page by page. It aims to reduce memory consumption. It is useful for large epub files.

# Usage
Usage is fairly simple. Just instantiate a reader object, input the epub file path, set the optional values and start parsing the file by <i>readSection</i> method.

Example usage:

	Reader reader = new Reader();
	reader.setMaxContentPerSection(1000); // Max string length for the current page.
	reader.setFullContent(epubFilePath); // Setting content once is enough.
	reader.setIsIncludingTextContent(true); // Optional, to return the tags-excluded version.

	Book bookSection = reader.readSection(pageIndex);
	String sectionContent = bookSection.getSectionContent(); // Returns content as html.
	String sectionTextContent = bookSection.getSectionTextContent(); // Excludes html tags.

Check out <a href="https://github.com/mertakdut/EpubParser-Sample-Android-Application">EpubParser-Sample-Android-Application</a> for more info.
<a href="https://play.google.com/store/apps/details?id=com.github.epubparsersampleandroidapplication"> Google Play Link</a>

# Setup

Add dependency in your project build.gradle

	compile 'com.github.mertakdut:EpubParser:1.0.85'

Or grab it via maven

	<dependency>
    		<groupId>com.github.mertakdut</groupId>
    		<artifactId>EpubParser</artifactId>
    		<version>1.0.85</version>
	</dependency>

# License
See the <a href="https://github.com/mertakdut/EpubParser/blob/master/LICENSE.txt">LICENSE</a> file for license rights and limitations (Apache License 2.0).
