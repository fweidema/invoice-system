package de.frank.invoice.worker.ui.vaadin;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlAssetReferencesTest {

    @Test
    void findsModuleScriptsIndependentOfAttributeOrderAndQuoteStyle() {
        final String html = """
                <script src="./ignored.js"></script>
                <script crossorigin src="./VAADIN/build/first.js" type="module"></script>
                <script TYPE='MODULE' SRC='/VAADIN/build/second.js'></script>
                """;

        assertThat(HtmlAssetReferences.findModuleScriptSources(html))
                .containsExactly(
                        "./VAADIN/build/first.js",
                        "/VAADIN/build/second.js");
    }

    @Test
    void findsStylesheetsIndependentOfAttributeOrderAndRelTokens() {
        final String html = """
                <link href="./icon.png" rel="icon">
                <link href="./VAADIN/build/first.css" rel="preload stylesheet">
                <link REL='stylesheet' HREF='/VAADIN/build/second.css'>
                """;

        assertThat(HtmlAssetReferences.findStylesheetReferences(html))
                .containsExactly(
                        "./VAADIN/build/first.css",
                        "/VAADIN/build/second.css");
    }
}
