package org.jahia.modules.lsm.filters;

import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LanguageDomainRedirectFilter}: when a redirect is
 * issued, where it points, and the gates that keep it silent.
 */
class LanguageDomainRedirectFilterTest {

    private LanguageDomainRedirectFilter filter;
    private RenderContext renderContext;
    private JCRSiteNode site;
    private Resource resource;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() throws Exception {
        filter = new LanguageDomainRedirectFilter();
        renderContext = mock(RenderContext.class);
        site = mock(JCRSiteNode.class);
        resource = mock(Resource.class);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);

        when(renderContext.getSite()).thenReturn(site);
        when(renderContext.getRequest()).thenReturn(request);
        when(renderContext.getResponse()).thenReturn(response);
        when(site.isNodeType("lsm:languageUrlSettings")).thenReturn(true);
        when(resource.getLocale()).thenReturn(new Locale("fr", "CH"));

        // Default: an external request on the French domain, TLS terminated correctly
        when(request.getServerName()).thenReturn("www.example.fr");
        when(request.getScheme()).thenReturn("https");
        when(request.getServerPort()).thenReturn(443);
        when(request.getRequestURI()).thenReturn("/fr_CH/page.html");
    }

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

    private String prepare() throws Exception {
        return filter.prepare(renderContext, resource, null);
    }

    /** Asserts a redirect was sent and returns its Location. */
    private String capturedLocation() throws Exception {
        ArgumentCaptor<String> location = ArgumentCaptor.forClass(String.class);
        verify(response).sendRedirect(location.capture());
        return location.getValue();
    }

    // =========================================================================
    // No redirect: the visitor is already where they belong, or nothing is configured
    // =========================================================================

    @Nested
    @DisplayName("no redirect")
    class NoRedirect {

        @Test
        @DisplayName("site without the mixin")
        void noMixin() throws Exception {
            when(site.isNodeType("lsm:languageUrlSettings")).thenReturn(false);
            assertThat(prepare()).isNull();
            verify(response, never()).sendRedirect(anyString());
        }

        @Test
        @DisplayName("no site in the render context")
        void noSite() throws Exception {
            when(renderContext.getSite()).thenReturn(null);
            assertThat(prepare()).isNull();
            verify(response, never()).sendRedirect(anyString());
        }

        @Test
        @DisplayName("requested language has no mapping")
        void unmappedLanguage() throws Exception {
            mapping("de=https://www.example.de");
            assertThat(prepare()).isNull();
            verify(response, never()).sendRedirect(anyString());
        }

        @Test
        @DisplayName("already on the mapped host and port")
        void alreadyOnTargetHost() throws Exception {
            when(request.getServerName()).thenReturn("ch.example.com");
            mapping("fr_CH=https://ch.example.com");
            assertThat(prepare()).isNull();
            verify(response, never()).sendRedirect(anyString());
        }

        @Test
        @DisplayName("host comparison is case-insensitive")
        void hostCaseInsensitive() throws Exception {
            when(request.getServerName()).thenReturn("CH.Example.COM");
            mapping("fr_CH=https://ch.example.com");
            assertThat(prepare()).isNull();
            verify(response, never()).sendRedirect(anyString());
        }

        @Test
        @DisplayName("mapping entry with a non-http scheme is ignored")
        void nonHttpMapping() throws Exception {
            mapping("fr_CH=ftp://ch.example.com");
            assertThat(prepare()).isNull();
            verify(response, never()).sendRedirect(anyString());
        }

        @Test
        @DisplayName("response already committed: nothing is sent")
        void responseCommitted() throws Exception {
            mapping("fr_CH=https://ch.example.com");
            when(response.isCommitted()).thenReturn(true);
            // Rendering is still short-circuited, the filter just cannot redirect
            assertThat(prepare()).isEmpty();
            verify(response, never()).sendRedirect(anyString());
        }

        @Test
        @DisplayName("two languages sharing a host never redirect between themselves")
        void languagesSharingAHost() throws Exception {
            when(request.getServerName()).thenReturn("www.example.com");
            when(resource.getLocale()).thenReturn(Locale.ENGLISH);
            mapping("en=https://www.example.com", "fr=https://www.example.com");
            assertThat(prepare()).isNull();
            verify(response, never()).sendRedirect(anyString());
        }
    }

    // =========================================================================
    // Redirect issued
    // =========================================================================

    @Nested
    @DisplayName("redirect")
    class Redirect {

        @Test
        @DisplayName("wrong host for the language: 302 to the same path on the mapped host")
        void wrongHost() throws Exception {
            mapping("fr_CH=https://ch.example.com");
            assertThat(prepare()).isEmpty();
            assertThat(capturedLocation()).isEqualTo("https://ch.example.com/fr_CH/page.html");
        }

        @Test
        @DisplayName("the client URI is taken from the forward attribute, so vanity URLs survive")
        void vanityUrlPreserved() throws Exception {
            // Jahia's inbound rewriting forwarded /accueil-ch to the technical path
            when(request.getAttribute("javax.servlet.forward.request_uri")).thenReturn("/accueil-ch");
            when(request.getRequestURI()).thenReturn("/cms/render/live/fr_CH/sites/goyer/home.html");
            mapping("fr_CH=https://ch.example.com");
            prepare();
            assertThat(capturedLocation()).isEqualTo("https://ch.example.com/accueil-ch");
        }

        @Test
        @DisplayName("without a forward attribute, the request URI is used")
        void noForwardAttribute() throws Exception {
            when(request.getAttribute("javax.servlet.forward.request_uri")).thenReturn(null);
            mapping("fr_CH=https://ch.example.com");
            prepare();
            assertThat(capturedLocation()).isEqualTo("https://ch.example.com/fr_CH/page.html");
        }

        @Test
        @DisplayName("query string is carried over")
        void queryStringPreserved() throws Exception {
            when(request.getQueryString()).thenReturn("page=2&sort=asc");
            mapping("fr_CH=https://ch.example.com");
            prepare();
            assertThat(capturedLocation()).isEqualTo("https://ch.example.com/fr_CH/page.html?page=2&sort=asc");
        }

        @Test
        @DisplayName("trailing slash on the mapped base does not produce a double slash")
        void trailingSlashStripped() throws Exception {
            mapping("fr_CH=https://ch.example.com/");
            prepare();
            assertThat(capturedLocation()).isEqualTo("https://ch.example.com/fr_CH/page.html");
        }

        @Test
        @DisplayName("an explicit port in the mapping is kept in the Location")
        void explicitPort() throws Exception {
            when(request.getServerName()).thenReturn("www.localtest.me");
            when(request.getScheme()).thenReturn("http");
            when(request.getServerPort()).thenReturn(8080);
            when(request.getRequestURI()).thenReturn("/fr_CH/page.html");
            mapping("fr_CH=http://ch.localtest.me:8080");
            prepare();
            assertThat(capturedLocation()).isEqualTo("http://ch.localtest.me:8080/fr_CH/page.html");
        }
    }

    // =========================================================================
    // Locale handling — regression cover for the fr/fr_CH collapse
    // =========================================================================

    @Nested
    @DisplayName("locale keys")
    class LocaleKeys {

        @Test
        @DisplayName("fr_CH matches its own entry, not the fr one declared first")
        void regionalLocaleNotCollapsedToLanguage() throws Exception {
            // Locale.getLanguage() would return "fr" for both and match the first entry
            mapping("fr=https://www.example.fr", "fr_CH=https://ch.example.com");
            prepare();
            assertThat(capturedLocation()).startsWith("https://ch.example.com");
        }

        @Test
        @DisplayName("a plain fr page is not sent to the fr_CH host")
        void plainLanguageNotMatchedByRegionalEntry() throws Exception {
            when(resource.getLocale()).thenReturn(Locale.FRENCH);
            when(request.getServerName()).thenReturn("www.example.fr");
            mapping("fr=https://www.example.fr", "fr_CH=https://ch.example.com");
            assertThat(prepare()).isNull();
            verify(response, never()).sendRedirect(anyString());
        }

        @Test
        @DisplayName("mapping keys are not accepted in BCP 47 form here")
        void bcp47KeyDoesNotMatch() throws Exception {
            // Documents the asymmetry with LanguageLinkRewriteFilter, which does accept fr-CH:
            // a fr-CH key yields correct menu links but no redirect.
            mapping("fr-CH=https://ch.example.com");
            assertThat(prepare()).isNull();
            verify(response, never()).sendRedirect(anyString());
        }
    }

    // =========================================================================
    // Known hazard: a proxy hiding the client scheme turns this into a loop
    // =========================================================================

    @Test
    @DisplayName("TLS terminated in front without X-Forwarded-Proto redirects to the current URL")
    void redirectLoopWhenSchemeIsHidden() throws Exception {
        // Same host as the mapping, but the container reports http/80 instead of https/443,
        // so the port comparison fails and the filter redirects to the URL already requested.
        when(request.getServerName()).thenReturn("ch.example.com");
        when(request.getScheme()).thenReturn("http");
        when(request.getServerPort()).thenReturn(80);
        mapping("fr_CH=https://ch.example.com");
        prepare();
        assertThat(capturedLocation()).isEqualTo("https://ch.example.com/fr_CH/page.html");
    }

    @Test
    @DisplayName("registered as a live-only, main-resource page filter, outside the cache")
    void filterConfiguration() {
        assertThat(filter.getPriority()).isEqualTo(13f);
    }
}
