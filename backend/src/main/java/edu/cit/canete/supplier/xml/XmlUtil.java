package edu.cit.canete.supplier.xml;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal helpers for building/reading the small, flat XML documents
 * LegacySupply uses. No external XML library needed for shapes this
 * simple.
 */
public final class XmlUtil {

    private XmlUtil() {
    }

    /** Extracts the text content of the first <tag>...</tag> match, or null. */
    public static String extractTag(String xml, String tag) {
        Pattern p = Pattern.compile("<" + tag + "[^>]*>(.*?)</" + tag + ">", Pattern.DOTALL);
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(1).trim() : null;
    }

    /** True if the document's root element matches the given tag name. */
    public static boolean isRoot(String xml, String tag) {
        return xml != null && xml.contains("<" + tag + ">") || (xml != null && xml.contains("<" + tag + " "));
    }

    public static String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}