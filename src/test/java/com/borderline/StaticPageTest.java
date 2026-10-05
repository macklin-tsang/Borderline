package com.borderline;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Ship names are untrusted radio text. The page must only ever put them into the DOM as text,
 * so this fails the build if anyone adds an API that parses a string as HTML.
 */
class StaticPageTest {

    private static final String[] HTML_SINKS = {
        "innerHTML", "outerHTML", "insertAdjacentHTML", "document.write", "eval(", "new Function("
    };

    @Test
    void appJs_neverTurnsAStringIntoHtml() {
        assertThat(read("/static/app.js")).doesNotContain(HTML_SINKS);
    }

    @Test
    void indexHtml_hasNoInlineScriptOrInlineEventHandlers() {
        String html = read("/static/index.html");

        assertThat(html).contains("Content-Security-Policy");
        assertThat(html).doesNotContainPattern("(?is)<script(?![^>]*\\bsrc=)");  // every <script> must have a src
        assertThat(html).doesNotContainPattern("(?i)\\son[a-z]+\\s*=");          // onclick=, onerror=, ...
    }

    private static String read(String path) {
        try (var in = StaticPageTest.class.getResourceAsStream(path)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
