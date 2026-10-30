package com.firestick.aura.launcher.manager;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

/** Parses only YouTube video IDs from public Atom feeds. No external entities are resolved. */
public final class PreviewFeedParser {
    private PreviewFeedParser() { }
    public static List<String> parse(byte[] xml, boolean preferTrailers) throws Exception {
        List<String> all = new ArrayList<>(), trailers = new ArrayList<>();
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        org.xml.sax.XMLReader reader = factory.newSAXParser().getXMLReader();
        reader.setFeature("http://xml.org/sax/features/external-general-entities", false);
        reader.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        reader.setEntityResolver((publicId, systemId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));
        reader.setContentHandler(new DefaultHandler() {
            boolean entry;
            String id = "", title = "";
            StringBuilder text = new StringBuilder();
            @Override public void startElement(String uri, String local, String q, Attributes attributes) {
                text.setLength(0);
                if ("entry".equals(local)) { entry = true; id = ""; title = ""; }
            }
            @Override public void characters(char[] chars, int start, int length) { text.append(chars, start, length); }
            @Override public void endElement(String uri, String local, String q) {
                if (entry && "videoId".equals(local) && "http://www.youtube.com/xml/schemas/2015".equals(uri)) id = text.toString().trim();
                if (entry && "title".equals(local)) title = text.toString().toLowerCase(java.util.Locale.ROOT);
                if ("entry".equals(local)) {
                    if (id.matches("[A-Za-z0-9_-]{11}")) {
                        all.add(id);
                        if (title.contains("trailer") || title.contains("teaser") || title.contains("sneak peek")) trailers.add(id);
                    }
                    entry = false;
                }
            }
        });
        reader.parse(new org.xml.sax.InputSource(new ByteArrayInputStream(xml)));
        Set<String> unique = new LinkedHashSet<>();
        if (preferTrailers) unique.addAll(trailers);
        unique.addAll(all);
        return new ArrayList<>(unique).subList(0, Math.min(12, unique.size()));
    }
}
