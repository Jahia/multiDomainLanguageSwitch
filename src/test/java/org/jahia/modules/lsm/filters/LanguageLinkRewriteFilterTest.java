package org.jahia.modules.lsm.filters;

import java.util.Locale;

import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LanguageLinkRewriteFilter}: the three rewrite passes
 * (menu anchors, {@code hreflang} alternates, canonical link), the gates that
 * make the filter a no-op on sites that do not use the module, and the mapping
 * parsing rules.
 */
class LanguageLinkRewriteFilterTest {

    private LanguageLinkRewriteFilter filter;
    private RenderContext renderContext;
    private JCRSiteNode site;
    private Resource resource;

    @BeforeEach
    void setUp() throws Exception {
        filter = new LanguageLinkRewriteFilter();
        renderContext = mock(RenderContext.class);
        site = mock(JCRSiteNode.class);
        resource = mock(Resource.class);
        when(renderContext.getSite()).thenReturn(site);
        when(site.isNodeType("lsm:languageUrlSettings")).thenReturn(true);
        when(resource.getLocale()).thenReturn(Locale.ENGLISH);
    }

    /** Declares the site mapping, as the multi-valued lsm:languageUrls property would hold it. */
    private void mapping(String... entries) throws Exception {
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        JCRValueWrapper[] values = new JCRValueWrapper[entries.length];
        for (int i = 0; i < entries.length; i++) {
            JCRValueWrapper value = mock(JCRValueWrapper.class);
            when(value.getString()).thenReturn(entries[i]);
            values[i] = value;
        }
        when(property.getValues()).thenReturn(values);
        when(site.hasProperty("lsm:languageUrls")).thenReturn(true);
        when(site.getProperty("lsm:languageUrls")).thenReturn(property);
    }

    private String execute(String html) throws Exception {
        return filter.execute(html, renderContext, resource, null);
    }

    // =========================================================================
    // Gates: the filter must be a cheap no-op unless the site is configured
    // =========================================================================

    @Nested
    @DisplayName("gates")
    class Gates {

        @Test
        @DisplayName("null output stays null")
        void nullOutput() throws Exception {
            assertThat(execute(null)).isNull();
        }

        @Test
        @DisplayName("site without the mixin: output untouched, mapping never read")
        void noMixin() throws Exception {
            when(site.isNodeType("lsm:languageUrlSettings")).thenReturn(false);
            String html = "<a class=\"lsm-item\" href=\"/en/p.html\" lang=\"en\">English</a>";
            assertThat(execute(html)).isEqualTo(html);
        }

        @Test
        @DisplayName("no site in the render context: output untouched")
        void noSite() throws Exception {
            when(renderContext.getSite()).thenReturn(null);
            String html = "<a class=\"lsm-item\" href=\"/en/p.html\" lang=\"en\">English</a>";
            assertThat(execute(html)).isEqualTo(html);
        }

        @Test
        @DisplayName("mixin present but no mapping property: output untouched")
        void mixinWithoutProperty() throws Exception {
            when(site.hasProperty("lsm:languageUrls")).thenReturn(false);
            String html = "<a class=\"lsm-item\" href=\"/en/p.html\" lang=\"en\">English</a>";
            assertThat(execute(html)).isEqualTo(html);
        }

        @Test
        @DisplayName("mapping holding only invalid entries counts as empty")
        void onlyInvalidEntries() throws Exception {
            mapping("en=ftp://example.com", "=https://example.com", "de=/relative/path");
            String html = "<a class=\"lsm-item\" href=\"/en/p.html\" lang=\"en\">English</a>";
            assertThat(execute(html)).isEqualTo(html);
        }

        @Test
        @DisplayName("configured site, page with nothing to rewrite: output untouched")
        void nothingToRewrite() throws Exception {
            mapping("en=https://www.example.com");
            String html = "<p>No menu, no head links.</p>";
            assertThat(execute(html)).isEqualTo(html);
        }
    }

    // =========================================================================
    // Pass 1 — language switch menu anchors
    // =========================================================================

    @Nested
    @DisplayName("menu links")
    class MenuLinks {

        @Test
        @DisplayName("root-relative href gets the mapped host prefixed")
        void relativeHref() throws Exception {
            mapping("de=https://www.example.de");
            assertThat(execute("<a class=\"lsm-item\" href=\"/de/p.html\" lang=\"de\">Deutsch</a>"))
                    .isEqualTo("<a class=\"lsm-item\" href=\"https://www.example.de/de/p.html\" lang=\"de\">Deutsch</a>");
        }

        @Test
        @DisplayName("absolute href keeps its path, only the host changes")
        void absoluteHref() throws Exception {
            mapping("de=https://www.example.de");
            assertThat(execute("<a class=\"lsm-item\" href=\"http://old.example.com:8080/de/p.html\" lang=\"de\">x</a>"))
                    .contains("href=\"https://www.example.de/de/p.html\"");
        }

        @Test
        @DisplayName("extra classes after lsm-item are preserved")
        void extraClasses() throws Exception {
            mapping("de=https://www.example.de");
            String out = execute("<a class=\"lsm-item lsm-current ds-link\" href=\"/de/p.html\" lang=\"de\">x</a>");
            assertThat(out).contains("class=\"lsm-item lsm-current ds-link\"")
                    .contains("href=\"https://www.example.de/de/p.html\"");
        }

        @Test
        @DisplayName("trailing slash on the mapped base is stripped, no double slash")
        void trailingSlashStripped() throws Exception {
            mapping("de=https://www.example.de/");
            assertThat(execute("<a class=\"lsm-item\" href=\"/de/p.html\" lang=\"de\">x</a>"))
                    .contains("href=\"https://www.example.de/de/p.html\"");
        }

        @Test
        @DisplayName("language with no mapping is left alone")
        void unmappedLanguage() throws Exception {
            mapping("de=https://www.example.de");
            String html = "<a class=\"lsm-item\" href=\"/it/p.html\" lang=\"it\">italiano</a>";
            assertThat(execute(html)).isEqualTo(html);
        }

        @Test
        @DisplayName("anchor without the lsm-item class is left alone")
        void foreignAnchor() throws Exception {
            mapping("de=https://www.example.de");
            String html = "<a class=\"nav-link\" href=\"/de/p.html\" lang=\"de\">Deutsch</a>";
            assertThat(execute(html)).isEqualTo(html);
        }

        @Test
        @DisplayName("several links in one menu are all rewritten")
        void severalLinks() throws Exception {
            mapping("de=https://www.example.de", "it=https://www.example.it");
            String out = execute("<a class=\"lsm-item\" href=\"/de/p.html\" lang=\"de\">d</a>"
                    + "<a class=\"lsm-item\" href=\"/it/p.html\" lang=\"it\">i</a>");
            assertThat(out).contains("https://www.example.de/de/p.html")
                    .contains("https://www.example.it/it/p.html");
        }

        @Test
        @DisplayName("attributes after lang, such as hreflang, survive the rewrite")
        void trailingAttributesPreserved() throws Exception {
            mapping("de=https://www.example.de");
            assertThat(execute("<a class=\"lsm-item\" href=\"/de/p.html\" lang=\"de\" hreflang=\"de\">d</a>"))
                    .isEqualTo("<a class=\"lsm-item\" href=\"https://www.example.de/de/p.html\" lang=\"de\" hreflang=\"de\">d</a>");
        }
    }

    // =========================================================================
    // Pass 2 — hreflang alternates emitted by site-settings-seo
    // =========================================================================

    @Nested
    @DisplayName("hreflang alternates")
    class Alternates {

        @Test
        @DisplayName("alternate href host is replaced with the host mapped to its hreflang")
        void alternateRewritten() throws Exception {
            mapping("de=https://www.example.de");
            assertThat(execute("<link rel=\"alternate\" hreflang=\"de\" href=\"https://www.example.com/de/p.html\" />"))
                    .isEqualTo("<link rel=\"alternate\" hreflang=\"de\" href=\"https://www.example.de/de/p.html\" />");
        }

        @Test
        @DisplayName("BCP 47 hreflang fr-CH resolves the Java-form mapping key fr_CH")
        void bcp47ResolvesJavaKey() throws Exception {
            mapping("fr_CH=https://ch.example.com");
            assertThat(execute("<link rel=\"alternate\" hreflang=\"fr-CH\" href=\"https://www.example.com/fr_CH/p.html\" />"))
                    .contains("href=\"https://ch.example.com/fr_CH/p.html\"");
        }

        @Test
        @DisplayName("regional hreflang falls back to the plain language mapping")
        void regionalFallsBackToLanguage() throws Exception {
            mapping("fr=https://www.example.fr");
            assertThat(execute("<link rel=\"alternate\" hreflang=\"fr-CH\" href=\"https://www.example.com/fr_CH/p.html\" />"))
                    .contains("href=\"https://www.example.fr/fr_CH/p.html\"");
        }

        @Test
        @DisplayName("hreflang with no mapping keeps the host it was generated with")
        void unmappedHreflang() throws Exception {
            mapping("de=https://www.example.de");
            String html = "<link rel=\"alternate\" hreflang=\"it\" href=\"https://www.example.com/it/p.html\" />";
            assertThat(execute(html)).isEqualTo(html);
        }

        @Test
        @DisplayName("every alternate of the head is rewritten independently")
        void severalAlternates() throws Exception {
            mapping("de=https://www.example.de", "fr_CH=https://ch.example.com");
            String out = execute(
                    "<link rel=\"alternate\" hreflang=\"de\" href=\"https://www.example.com/de/p.html\" />"
                            + "<link rel=\"alternate\" hreflang=\"fr-CH\" href=\"https://www.example.com/fr_CH/p.html\" />"
                            + "<link rel=\"alternate\" hreflang=\"it\" href=\"https://www.example.com/it/p.html\" />");
            assertThat(out).contains("hreflang=\"de\" href=\"https://www.example.de/de/p.html\"")
                    .contains("hreflang=\"fr-CH\" href=\"https://ch.example.com/fr_CH/p.html\"")
                    .contains("hreflang=\"it\" href=\"https://www.example.com/it/p.html\"");
        }
    }

    // =========================================================================
    // Pass 3 — canonical link, keyed on the locale of the rendered page
    // =========================================================================

    @Nested
    @DisplayName("canonical link")
    class Canonical {

        @Test
        @DisplayName("canonical takes the host mapped to the locale being rendered")
        void canonicalRewritten() throws Exception {
            when(resource.getLocale()).thenReturn(new Locale("fr", "CH"));
            mapping("fr_CH=https://ch.example.com", "fr=https://www.example.fr");
            assertThat(execute("<link rel=\"canonical\" href=\"https://www.example.com/fr_CH/p.html\" />"))
                    .isEqualTo("<link rel=\"canonical\" href=\"https://ch.example.com/fr_CH/p.html\" />");
        }

        @Test
        @DisplayName("fr_CH page is not given the fr host: the full locale is used as key")
        void fullLocaleNotTruncated() throws Exception {
            when(resource.getLocale()).thenReturn(new Locale("fr", "CH"));
            mapping("fr_CH=https://ch.example.com", "fr=https://www.example.fr");
            assertThat(execute("<link rel=\"canonical\" href=\"https://www.example.com/fr_CH/p.html\" />"))
                    .doesNotContain("www.example.fr");
        }

        @Test
        @DisplayName("locale with no mapping leaves the canonical untouched")
        void unmappedLocale() throws Exception {
            when(resource.getLocale()).thenReturn(Locale.ITALIAN);
            mapping("de=https://www.example.de");
            String html = "<link rel=\"canonical\" href=\"https://www.example.com/it/p.html\" />";
            assertThat(execute(html)).isEqualTo(html);
        }
    }

    // =========================================================================
    // Passes are independent and compose on one page
    // =========================================================================

    @Test
    @DisplayName("a full page gets its menu, alternates and canonical rewritten in one go")
    void fullPage() throws Exception {
        when(resource.getLocale()).thenReturn(new Locale("fr", "CH"));
        mapping("fr_CH=https://ch.example.com", "de=https://www.example.de");
        String out = execute("<head>"
                + "<link rel=\"canonical\" href=\"https://www.example.com/fr_CH/p.html\" />"
                + "<link rel=\"alternate\" hreflang=\"de\" href=\"https://www.example.com/de/p.html\" />"
                + "</head><body>"
                + "<a class=\"lsm-item\" href=\"/de/p.html\" lang=\"de\">d</a>"
                + "</body>");
        assertThat(out)
                .contains("rel=\"canonical\" href=\"https://ch.example.com/fr_CH/p.html\"")
                .contains("hreflang=\"de\" href=\"https://www.example.de/de/p.html\"")
                .contains("class=\"lsm-item\" href=\"https://www.example.de/de/p.html\"");
    }

    // =========================================================================
    // Filter registration: these values are the contract with the render chain
    // =========================================================================

    @Test
    @DisplayName("registered as an outer, live-only, main-resource page filter")
    void filterConfiguration() {
        // Priority below 16 keeps it outside the aggregate cache; below site-settings-seo's
        // 16.2 makes execute() run after it, since execute() runs in decreasing priority order.
        assertThat(filter.getPriority()).isEqualTo(5f);
        assertThat(filter.getDescription()).contains("canonical");
    }
}
