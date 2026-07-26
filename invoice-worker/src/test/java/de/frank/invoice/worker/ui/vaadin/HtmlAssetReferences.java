package de.frank.invoice.worker.ui.vaadin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HtmlAssetReferences {

    private static final Pattern SCRIPT_TAG = Pattern.compile("(?is)<script\\b[^>]*>");
    private static final Pattern LINK_TAG = Pattern.compile("(?is)<link\\b[^>]*>");
    private static final Pattern ATTRIBUTE = Pattern.compile(
            "(?is)\\b([a-z][a-z0-9:-]*)\\s*=\\s*([\"'])(.*?)\\2");

    private HtmlAssetReferences() {
    }

    static List<String> findModuleScriptSources(final String html) {
        return findReferences(html, SCRIPT_TAG, "src", attributes ->
                "module".equalsIgnoreCase(attributes.value("type")));
    }

    static List<String> findStylesheetReferences(final String html) {
        return findReferences(html, LINK_TAG, "href", attributes ->
                containsToken(attributes.value("rel"), "stylesheet"));
    }

    private static List<String> findReferences(
            final String html,
            final Pattern tagPattern,
            final String referenceAttribute,
            final AttributePredicate predicate) {
        final List<String> references = new ArrayList<>();
        final Matcher tagMatcher = tagPattern.matcher(html);
        while (tagMatcher.find()) {
            final Attributes attributes = parseAttributes(tagMatcher.group());
            final String reference = attributes.value(referenceAttribute);
            if (predicate.test(attributes) && reference != null && !reference.isBlank()) {
                references.add(reference);
            }
        }
        return List.copyOf(references);
    }

    private static Attributes parseAttributes(final String tag) {
        final List<Attribute> attributes = new ArrayList<>();
        final Matcher attributeMatcher = ATTRIBUTE.matcher(tag);
        while (attributeMatcher.find()) {
            attributes.add(new Attribute(
                    attributeMatcher.group(1).toLowerCase(Locale.ROOT),
                    attributeMatcher.group(3)));
        }
        return new Attributes(attributes);
    }

    private static boolean containsToken(final String value, final String expectedToken) {
        if (value == null) {
            return false;
        }
        for (String token : value.split("\\s+")) {
            if (expectedToken.equalsIgnoreCase(token)) {
                return true;
            }
        }
        return false;
    }

    private record Attribute(String name, String value) {
    }

    private record Attributes(List<Attribute> values) {

        private String value(final String name) {
            for (Attribute attribute : values) {
                if (attribute.name().equals(name)) {
                    return attribute.value();
                }
            }
            return null;
        }
    }

    @FunctionalInterface
    private interface AttributePredicate {

        boolean test(Attributes attributes);
    }
}
