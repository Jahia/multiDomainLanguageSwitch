package org.jahia.modules.lsm.actions;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.jahia.bin.ActionResult;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRPropertyWrapper;
import org.jahia.services.content.JCRValueWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.render.RenderContext;
import org.json.JSONArray;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SaveLanguageUrlsAction}: what gets stored on the site
 * node, what is rejected, and the mixin handling.
 */
class SaveLanguageUrlsActionTest {

    private SaveLanguageUrlsAction action;
    private RenderContext renderContext;
    private JCRSiteNode site;
    private JCRNodeWrapper siteNode;
    private JCRSessionWrapper session;

    @BeforeEach
    void setUp() throws Exception {
        action = new SaveLanguageUrlsAction();
        renderContext = mock(RenderContext.class);
        site = mock(JCRSiteNode.class);
        siteNode = mock(JCRNodeWrapper.class);
        session = mock(JCRSessionWrapper.class);

        when(renderContext.getSite()).thenReturn(site);
        when(site.getPath()).thenReturn("/sites/mysite");
        when(session.getNode("/sites/mysite")).thenReturn(siteNode);
        languages("en", "fr", "fr_CH");
    }

    private void languages(String... languages) {
        Set<String> set = new LinkedHashSet<>(Arrays.asList(languages));
        when(site.getLanguages()).thenReturn(set);
    }

    /** Builds the action parameters, one url-<lang> entry per pair. */
    private Map<String, List<String>> params(String... langAndUrl) {
        Map<String, List<String>> parameters = new HashMap<>();
        for (int i = 0; i < langAndUrl.length; i += 2) {
            parameters.put("url-" + langAndUrl[i], Collections.singletonList(langAndUrl[i + 1]));
        }
        return parameters;
    }

    private ActionResult execute(Map<String, List<String>> parameters) throws Exception {
        return action.doExecute(mock(HttpServletRequest.class), renderContext, null, session, parameters, null);
    }

    /** The values written to lsm:languageUrls, in the order the action produced them. */
    private String[] storedEntries() throws Exception {
        ArgumentCaptor<String[]> stored = ArgumentCaptor.forClass(String[].class);
        verify(siteNode).setProperty(anyString(), stored.capture());
        return stored.getValue();
    }

    @Test
    @DisplayName("valid http and https URLs are stored as lang=url entries")
    void storesValidUrls() throws Exception {
        execute(params("en", "https://www.example.com", "fr_CH", "http://ch.example.com:8080"));
        assertThat(storedEntries()).containsExactlyInAnyOrder(
                "en=https://www.example.com", "fr_CH=http://ch.example.com:8080");
    }

    @Test
    @DisplayName("the property written is lsm:languageUrls")
    void writesTheExpectedProperty() throws Exception {
        execute(params("en", "https://www.example.com"));
        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        verify(siteNode).setProperty(name.capture(), ArgumentCaptor.forClass(String[].class).capture());
        assertThat(name.getValue()).isEqualTo("lsm:languageUrls");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://www.example.com/",
        "  https://www.example.com  ",
        "HTTPS://WWW.Example.com",
        "https://www.example.com:443",
        "https://www.example.com/some/base/path"
    })
    @DisplayName("the submitted value is normalised before being stored")
    void normalisesBeforeStoring(String submitted) throws Exception {
        // The action delegates to LanguageUrlMapping.normalize, whose own rules are
        // covered exhaustively by LanguageUrlMappingTest; what matters here is that
        // the action stores the normalised form and not the raw input.
        execute(params("en", submitted));
        assertThat(storedEntries()).containsExactly("en=https://www.example.com");
    }

    @Test
    @DisplayName("blank values are skipped, which clears the mapping for that language")
    void skipsBlankValues() throws Exception {
        execute(params("en", "https://www.example.com", "fr", "   "));
        assertThat(storedEntries()).containsExactly("en=https://www.example.com");
    }

    @Test
    @DisplayName("a language absent from the parameters is simply not stored")
    void missingParameter() throws Exception {
        execute(params("en", "https://www.example.com"));
        assertThat(storedEntries()).containsExactly("en=https://www.example.com");
    }

    /** Declares what is already stored on the site node, as read back before saving. */
    private void alreadyStored(String... entries) throws Exception {
        JCRPropertyWrapper property = mock(JCRPropertyWrapper.class);
        JCRValueWrapper[] values = new JCRValueWrapper[entries.length];
        for (int i = 0; i < entries.length; i++) {
            JCRValueWrapper value = mock(JCRValueWrapper.class);
            when(value.getString()).thenReturn(entries[i]);
            values[i] = value;
        }
        when(property.getValues()).thenReturn(values);
        when(siteNode.isNodeType("lsm:languageUrlSettings")).thenReturn(true);
        when(siteNode.hasProperty("lsm:languageUrls")).thenReturn(true);
        when(siteNode.getProperty("lsm:languageUrls")).thenReturn(property);
    }

    @Test
    @DisplayName("a rejected value keeps the mapping already stored for that language")
    void rejectedValueKeepsStoredMapping() throws Exception {
        languages("de", "fr");
        // The property is rewritten whole, so a language left out of the new value
        // is deleted. A typo must not destroy a working mapping.
        alreadyStored("de=https://www.example.de", "fr=https://www.example.fr");

        ActionResult result = execute(params("de", "htp://typo.example.de", "fr", "https://www.example.fr"));

        assertThat(storedEntries()).containsExactlyInAnyOrder(
                "fr=https://www.example.fr", "de=https://www.example.de");
        assertThat(result.getJson().getJSONArray("rejected").toString()).contains("de");
        assertThat(result.getJson().getJSONArray("kept").toString()).contains("de");
        assertThat(result.getJson().getInt("saved")).isEqualTo(1);
    }

    @Test
    @DisplayName("a rejected value for a language that had none stores nothing for it")
    void rejectedValueWithNothingStored() throws Exception {
        languages("de", "fr");
        alreadyStored("fr=https://www.example.fr");

        ActionResult result = execute(params("de", "javascript:alert(1)", "fr", "https://www.example.fr"));

        assertThat(storedEntries()).containsExactly("fr=https://www.example.fr");
        assertThat(result.getJson().getJSONArray("kept").length()).isZero();
    }

    @Test
    @DisplayName("clearing a field still drops the mapping, rejection does not")
    void blankStillClears() throws Exception {
        languages("de", "fr");
        alreadyStored("de=https://www.example.de");

        execute(params("de", "", "fr", "https://www.example.fr"));

        assertThat(storedEntries()).containsExactly("fr=https://www.example.fr");
    }

    @Test
    @DisplayName("non-http schemes are rejected and reported, never stored")
    void rejectsNonHttpSchemes() throws Exception {
        ActionResult result = execute(params(
                "en", "javascript:alert(1)",
                "fr", "ftp://example.com",
                "fr_CH", "https://ch.example.com"));

        assertThat(storedEntries()).containsExactly("fr_CH=https://ch.example.com");
        JSONArray rejected = result.getJson().getJSONArray("rejected");
        assertThat(rejected.length()).isEqualTo(2);
        assertThat(rejected.toString()).contains("en").contains("fr");
    }

    @Test
    @DisplayName("a host without a scheme is rejected")
    void rejectsSchemelessHost() throws Exception {
        ActionResult result = execute(params("en", "www.example.com"));
        assertThat(storedEntries()).isEmpty();
        assertThat(result.getJson().getJSONArray("rejected").toString()).contains("en");
    }

    @Test
    @DisplayName("only the site's own languages are considered")
    void ignoresUnknownLanguages() throws Exception {
        languages("en");
        execute(params("en", "https://www.example.com", "de", "https://www.example.de"));
        assertThat(storedEntries()).containsExactly("en=https://www.example.com");
    }

    @Test
    @DisplayName("the mixin is added when the site does not carry it yet")
    void addsMixinWhenAbsent() throws Exception {
        when(siteNode.isNodeType("lsm:languageUrlSettings")).thenReturn(false);
        execute(params("en", "https://www.example.com"));
        verify(siteNode).addMixin("lsm:languageUrlSettings");
    }

    @Test
    @DisplayName("the mixin is not added twice")
    void doesNotReAddMixin() throws Exception {
        when(siteNode.isNodeType("lsm:languageUrlSettings")).thenReturn(true);
        execute(params("en", "https://www.example.com"));
        verify(siteNode, never()).addMixin(anyString());
    }

    @Test
    @DisplayName("the session is saved once")
    void savesSession() throws Exception {
        execute(params("en", "https://www.example.com"));
        verify(session).save();
    }

    @Test
    @DisplayName("the response reports how many entries were saved, with HTTP 200")
    void reportsSavedCount() throws Exception {
        ActionResult result = execute(params("en", "https://www.example.com", "fr", "https://www.example.fr"));
        assertThat(result.getResultCode()).isEqualTo(HttpServletResponse.SC_OK);
        assertThat(result.getJson().getInt("saved")).isEqualTo(2);
    }

    @Test
    @DisplayName("clearing every field stores an empty mapping rather than failing")
    void clearingEverything() throws Exception {
        ActionResult result = execute(params("en", "", "fr", "", "fr_CH", ""));
        assertThat(storedEntries()).isEmpty();
        assertThat(result.getJson().getInt("saved")).isZero();
    }

    @Test
    @DisplayName("guarded by the site admin permission, authenticated, on the default workspace")
    void actionConfiguration() {
        assertThat(action.getName()).isEqualTo("saveLanguageUrls");
        assertThat(action.getRequiredPermission()).isEqualTo("siteAdminLanguageUrlMapping");
        assertThat(action.getRequiredWorkspace()).isEqualTo("default");
        assertThat(action.isRequireAuthenticatedUser()).isTrue();
    }
}
