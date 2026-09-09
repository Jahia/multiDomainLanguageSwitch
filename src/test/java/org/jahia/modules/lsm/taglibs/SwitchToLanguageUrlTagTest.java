package org.jahia.modules.lsm.taglibs;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.Tag;

import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SwitchToLanguageUrlTag}, the tag that renders one
 * language switch link. Both {@code getCurrentResource()} and
 * {@code getRenderContext()} of {@code AbstractJahiaTag} resolve through
 * {@code pageContext.getAttribute(name, REQUEST_SCOPE)}, so a mocked
 * {@link PageContext} is enough to drive the tag.
 */
class SwitchToLanguageUrlTagTest {

    private SwitchToLanguageUrlTag tag;
    private PageContext pageContext;
    private JspWriter writer;
    private Resource resource;
    private final Map<String, String> languageLinks = new HashMap<>();

    @BeforeEach
    void setUp() {
        tag = new SwitchToLanguageUrlTag();
        pageContext = mock(PageContext.class);
        writer = mock(JspWriter.class);
        resource = mock(Resource.class);

        RenderContext renderContext = mock(RenderContext.class);
        URLGenerator urlGenerator = mock(URLGenerator.class);
        HttpServletResponse response = mock(HttpServletResponse.class);

        when(pageContext.getAttribute("currentResource", PageContext.REQUEST_SCOPE)).thenReturn(resource);
        when(pageContext.getAttribute("renderContext", PageContext.REQUEST_SCOPE)).thenReturn(renderContext);
        when(pageContext.getOut()).thenReturn(writer);
        when(pageContext.getResponse()).thenReturn(response);
        when(renderContext.getURLGenerator()).thenReturn(urlGenerator);
        when(urlGenerator.getLanguages()).thenReturn(languageLinks);
        // encodeURL is where Jahia's outbound rewriting happens; identity here
        when(response.encodeURL(anyString())).thenAnswer(call -> call.getArgument(0));

        when(resource.getLocale()).thenReturn(Locale.FRENCH);
        tag.setPageContext(pageContext);
    }

    /** Renders the tag for one language and returns the HTML it printed. */
    private String render(String languageCode) throws Exception {
        languageLinks.putIfAbsent(languageCode, "/cms/render/live/" + languageCode + "/sites/s/home.html");
        tag.setLanguageCode(languageCode);
        assertThat(tag.doStartTag()).isEqualTo(Tag.SKIP_BODY);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        verify(writer).print(html.capture());
        return html.getValue();
    }

    @Test
    @DisplayName("renders an anchor with the class the rewrite filter matches on")
    void rendersRewritableAnchor() throws Exception {
        // The filter's regex requires class first, then href, then lang
        assertThat(render("de")).startsWith("<a class=\"lsm-item\" href=\"")
                .contains("\" lang=\"de\"");
    }

    @Test
    @DisplayName("lang and hreflang are in BCP 47 form")
    void bcp47Attributes() throws Exception {
        assertThat(render("fr_CH")).contains("lang=\"fr-CH\"").contains("hreflang=\"fr-CH\"");
    }

    @Test
    @DisplayName("the label is the endonym, and keeps the country for a regional locale")
    void endonymLabelKeepsCountry() throws Exception {
        String html = render("fr_CH");
        // getDisplayLanguage() would return plain "français" for both fr and fr_CH
        assertThat(html).contains("(");
        assertThat(html).doesNotContain(">français<");
    }

    @Test
    @DisplayName("the current language is marked with aria-current and the current class")
    void currentLanguageMarked() throws Exception {
        when(resource.getLocale()).thenReturn(Locale.FRENCH);
        String html = render("fr");
        assertThat(html).contains("lsm-current").contains("aria-current=\"page\"");
    }

    @Test
    @DisplayName("another language is not marked as current")
    void otherLanguageNotMarked() throws Exception {
        when(resource.getLocale()).thenReturn(Locale.FRENCH);
        String html = render("de");
        assertThat(html).doesNotContain("lsm-current").doesNotContain("aria-current");
    }

    @Test
    @DisplayName("fr_CH is not marked current while rendering an fr page")
    void regionalLocaleIsNotTheSameAsItsLanguage() throws Exception {
        // Regression cover: comparing Locale.getLanguage() made fr and fr_CH equal
        when(resource.getLocale()).thenReturn(Locale.FRENCH);
        assertThat(render("fr_CH")).doesNotContain("aria-current");
    }

    @Test
    @DisplayName("fr_CH is marked current while rendering an fr_CH page")
    void regionalLocaleMatchesItself() throws Exception {
        when(resource.getLocale()).thenReturn(new Locale("fr", "CH"));
        assertThat(render("fr_CH")).contains("aria-current=\"page\"");
    }

    @Test
    @DisplayName("the link goes through encodeURL, which is what triggers vanity URL rewriting")
    void linkGoesThroughEncodeUrl() throws Exception {
        HttpServletResponse response = (HttpServletResponse) pageContext.getResponse();
        when(response.encodeURL(anyString())).thenReturn("/accueil");
        assertThat(render("fr")).contains("href=\"/accueil\"");
    }

    @Test
    @DisplayName("the href is escaped for XML, so a query string cannot break out of the attribute")
    void hrefIsEscaped() throws Exception {
        HttpServletResponse response = (HttpServletResponse) pageContext.getResponse();
        when(response.encodeURL(anyString())).thenReturn("/p.html?a=1&b=2");
        String html = render("fr");
        assertThat(html).contains("href=\"/p.html?a=1&amp;b=2\"");
    }
}
