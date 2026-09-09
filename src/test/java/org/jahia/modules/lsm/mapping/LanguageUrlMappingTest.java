package org.jahia.modules.lsm.mapping;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LanguageUrlMapping}, the single place where a mapping
 * value is validated. The security-relevant part is
 * {@link LanguageUrlMapping#normalize(String)}: these values end up inside the
 * href of every menu link and head element of a live page.
 */
class LanguageUrlMappingTest {

    @Nested
    @DisplayName("normalize accepts")
    class Accepted {

        @Test
        @DisplayName("a plain https host")
        void httpsHost() {
            assertThat(LanguageUrlMapping.normalize("https://www.example.com"))
                    .isEqualTo("https://www.example.com");
        }

        @Test
        @DisplayName("an http host with an explicit port")
        void hostWithPort() {
            assertThat(LanguageUrlMapping.normalize("http://it.localtest.me:8080"))
                    .isEqualTo("http://it.localtest.me:8080");
        }

        @Test
        @DisplayName("surrounding whitespace, which is trimmed")
        void trimsWhitespace() {
            assertThat(LanguageUrlMapping.normalize("  https://www.example.com  "))
                    .isEqualTo("https://www.example.com");
        }

        @Test
        @DisplayName("a trailing slash, which is dropped")
        void dropsTrailingSlash() {
            assertThat(LanguageUrlMapping.normalize("https://www.example.com/"))
                    .isEqualTo("https://www.example.com");
        }

        @Test
        @DisplayName("a mixed-case scheme and host, normalized to lower case")
        void normalizesCase() {
            assertThat(LanguageUrlMapping.normalize("HTTPS://WWW.Example.COM"))
                    .isEqualTo("https://www.example.com");
        }

        @Test
        @DisplayName("the default port, which is dropped so comparisons match")
        void dropsDefaultPort() {
            assertThat(LanguageUrlMapping.normalize("https://www.example.com:443"))
                    .isEqualTo("https://www.example.com");
            assertThat(LanguageUrlMapping.normalize("http://www.example.com:80"))
                    .isEqualTo("http://www.example.com");
        }

        @Test
        @DisplayName("a path, which is dropped because the filters ignore it anyway")
        void dropsPath() {
            assertThat(LanguageUrlMapping.normalize("https://www.example.com/some/base"))
                    .isEqualTo("https://www.example.com");
        }
    }

    @Nested
    @DisplayName("normalize rejects")
    class Rejected {

        @ParameterizedTest
        @ValueSource(strings = {
            // Attribute break-out attempts: every one of these was injected verbatim
            // into the href of every live page before normalize() existed
            "https://x\" onmouseover=\"alert(1)",
            "https://x\"><script>alert(1)</script>",
            "https://x' onfocus='alert(1)",
            "https://x\nHeader: injected",
            "https://x\r\nLocation: https://evil.example.com"
        })
        @DisplayName("anything that could escape an href attribute or a header")
        void injectionAttempts(String payload) {
            assertThat(LanguageUrlMapping.normalize(payload)).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "javascript:alert(1)",
            "data:text/html;base64,PHNjcmlwdD4=",
            "file:///etc/passwd",
            "ftp://example.com",
            "//example.com",
            "www.example.com",
            "/relative/path"
        })
        @DisplayName("any scheme that is not http or https")
        void otherSchemes(String value) {
            assertThat(LanguageUrlMapping.normalize(value)).isNull();
        }

        @Test
        @DisplayName("embedded credentials")
        void credentials() {
            assertThat(LanguageUrlMapping.normalize("https://user:secret@example.com")).isNull();
        }

        @Test
        @DisplayName("a URL with no host")
        void noHost() {
            assertThat(LanguageUrlMapping.normalize("https://")).isNull();
            assertThat(LanguageUrlMapping.normalize("http:///path")).isNull();
        }

        @Test
        @DisplayName("blank and null")
        void blank() {
            assertThat(LanguageUrlMapping.normalize(null)).isNull();
            assertThat(LanguageUrlMapping.normalize("")).isNull();
            assertThat(LanguageUrlMapping.normalize("   ")).isNull();
        }
    }

    @Nested
    @DisplayName("lookup")
    class Lookup {

        private final Map<String, String> mapping = new HashMap<>();

        Lookup() {
            mapping.put("fr", "https://www.example.fr");
            mapping.put("fr_CH", "https://ch.example.com");
        }

        @Test
        @DisplayName("finds a Java-form key directly")
        void javaForm() {
            assertThat(LanguageUrlMapping.lookup(mapping, "fr_CH")).isEqualTo("https://ch.example.com");
        }

        @Test
        @DisplayName("finds a Java-form key from its BCP 47 form")
        void bcp47Form() {
            assertThat(LanguageUrlMapping.lookup(mapping, "fr-CH")).isEqualTo("https://ch.example.com");
        }

        @Test
        @DisplayName("falls back to the plain language when the variant is unmapped")
        void fallsBackToLanguage() {
            assertThat(LanguageUrlMapping.lookup(mapping, "fr-BE")).isEqualTo("https://www.example.fr");
        }

        @Test
        @DisplayName("returns null for an unmapped language")
        void unmapped() {
            assertThat(LanguageUrlMapping.lookup(mapping, "de")).isNull();
        }

        @Test
        @DisplayName("tolerates an empty mapping and an empty code")
        void empties() {
            assertThat(LanguageUrlMapping.lookup(new HashMap<>(), "fr")).isNull();
            assertThat(LanguageUrlMapping.lookup(mapping, "")).isNull();
            assertThat(LanguageUrlMapping.lookup(mapping, null)).isNull();
        }
    }
}
