package com.alastorkaneki.ebookreaderyou;

import android.net.Uri;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilderFactory;

public final class EpubBook {
    public final List<File> chapters = new ArrayList<>();
    public final List<String> titles = new ArrayList<>();

    public static EpubBook parse(File root) throws Exception {
        File container = new File(root, "META-INF/container.xml");
        if (!container.exists()) throw new IllegalArgumentException("This EPUB has no container.xml");
        Document containerDocument = parseXml(container);
        NodeList rootFiles = containerDocument.getElementsByTagNameNS("*", "rootfile");
        if (rootFiles.getLength() == 0) rootFiles = containerDocument.getElementsByTagName("rootfile");
        if (rootFiles.getLength() == 0) throw new IllegalArgumentException("This EPUB has no package document");
        String packagePath = ((Element) rootFiles.item(0)).getAttribute("full-path");
        File packageFile = new File(root, packagePath);
        if (!packageFile.getCanonicalPath().startsWith(root.getCanonicalPath())) throw new SecurityException("Unsafe EPUB package path");
        Document packageDocument = parseXml(packageFile);
        File packageDir = packageFile.getParentFile();
        Map<String, String> hrefById = new HashMap<>();
        Map<String, String> mediaById = new HashMap<>();
        NodeList items = packageDocument.getElementsByTagNameNS("*", "item");
        if (items.getLength() == 0) items = packageDocument.getElementsByTagName("item");
        for (int i = 0; i < items.getLength(); i++) {
            Element element = (Element) items.item(i);
            hrefById.put(element.getAttribute("id"), element.getAttribute("href"));
            mediaById.put(element.getAttribute("id"), element.getAttribute("media-type"));
        }
        EpubBook result = new EpubBook();
        NodeList refs = packageDocument.getElementsByTagNameNS("*", "itemref");
        if (refs.getLength() == 0) refs = packageDocument.getElementsByTagName("itemref");
        for (int i = 0; i < refs.getLength(); i++) {
            Element element = (Element) refs.item(i);
            String id = element.getAttribute("idref");
            String href = hrefById.get(id);
            String media = mediaById.get(id);
            if (href == null || !("application/xhtml+xml".equals(media) || "text/html".equals(media))) continue;
            File chapter = new File(packageDir, Uri.decode(href.split("#", 2)[0]));
            if (!chapter.getCanonicalPath().startsWith(root.getCanonicalPath())) continue;
            if (chapter.exists()) {
                result.chapters.add(chapter);
                result.titles.add(titleFromFile(chapter));
            }
        }
        if (result.chapters.isEmpty()) collectHtml(packageDir, result);
        if (result.chapters.isEmpty()) throw new IllegalArgumentException("No readable chapters were found in this EPUB");
        return result;
    }

    private static Document parseXml(File file) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        try (FileInputStream input = new FileInputStream(file)) {
            return factory.newDocumentBuilder().parse(input);
        }
    }

    private static void collectHtml(File file, EpubBook result) {
        if (file == null || !file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) collectHtml(child, result);
            return;
        }
        String lower = file.getName().toLowerCase();
        if (lower.endsWith(".xhtml") || lower.endsWith(".html") || lower.endsWith(".htm")) {
            result.chapters.add(file);
            result.titles.add(titleFromFile(file));
        }
    }

    private static String titleFromFile(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.replace('_', ' ').replace('-', ' ');
    }
}
