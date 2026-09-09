package org.jahia.modules.lsm.mapping;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.commons.lang.StringUtils;
import org.jahia.services.content.decorator.JCRSiteNode;

/**
 * Reads and validates the per-language base URL mapping stored on a site
 * (mixin {@code lsm:languageUrlSettings}, multi-valued property
 * {@code lsm:languageUrls}, entries formatted {@code lang=https://host}).
 * <p>
 * Every value goes through {@link #normalize(String)}, which accepts only a
 * scheme, a host and an optional port, and rebuilds the URL from those parsed
 * components. That matters for more than tidiness: these values are injected
 * into the {@code href} of every menu link and of the {@code hreflang} and
 * {@code canonical} head elements of a live page. A value carrying a quote would
 * otherwise break out of the attribute, turning the
 * {@code siteAdminLanguageUrlMapping} permission into stored XSS against every
 * visitor of the site. Rebuilding from parsed components makes that
 * unrepresentable rather than merely filtered.
 * <p>
 * Validating on read, and not only when the settings panel saves, is deliberate:
 * the property can also be written by an import, a migration or a Groovy script,
 * none of which go through the action.
 */
public final class LanguageUrlMapping {

    public static final String MIXIN = "lsm:languageUrlSettings";
    public static final String PROPERTY = "lsm:languageUrls";

    private LanguageUrlMapping() {
        // Utility class
    }

    /**
     * Validates a base URL and returns its canonical {@code scheme://host[:port]}
     * form, or {@code null} when the value is not usable.
     * <p>
     * Accepted: an absolute http or https URL with a host. Rejected: any other
     * scheme, a missing host, embedded credentials, and anything the URI syntax
     * refuses — which covers every character that could escape an HTML attribute,
     * since quotes, angle brackets and whitespace are all illegal in a URI.
     * A path, query or fragment is dropped: both filters compare and rewrite on
     * host and port only, so keeping it would promise something not delivered.
     */
    public static String normalize(String rawUrl) {
        if (StringUtils.isBlank(rawUrl)) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            return null;
        }
        String scheme = StringUtils.lowerCase(uri.getScheme(), Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            return null;
        }
        String host = StringUtils.lowerCase(uri.getHost(), Locale.ROOT);
        if (StringUtils.isEmpty(host)) {
            // A registry-based or percent-encoded authority yields no host
            return null;
        }
        if (uri.getUserInfo() != null) {
            // Credentials have no place in a public base URL
            return null;
        }
        int port = uri.getPort();
        boolean defaultPort = port < 0
                || ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);
        return scheme + "://" + host + (defaultPort ? "" : ":" + port);
    }

    /** The site's mapping, keyed on the Java locale code, with invalid entries dropped. */
    public static Map<String, String> read(JCRSiteNode site) throws RepositoryException {
        if (site == null || !site.isNodeType(MIXIN) || !site.hasProperty(PROPERTY)) {
            return Collections.emptyMap();
        }
        Map<String, String> mapping = new HashMap<>();
        for (Value value : site.getProperty(PROPERTY).getValues()) {
            String entry = value.getString();
            String language = StringUtils.trimToNull(StringUtils.substringBefore(entry, "="));
            String url = normalize(StringUtils.substringAfter(entry, "="));
            if (language != null && url != null) {
                mapping.put(language, url);
            }
        }
        return mapping;
    }

    /**
     * Looks up the base URL for a language code, accepting either the BCP 47 form
     * that appears in HTML attributes ({@code fr-CH}) or the Java locale form used
     * as the mapping key ({@code fr_CH}), and falling back to the plain language.
     */
    public static String lookup(Map<String, String> mapping, String languageCode) {
        if (mapping.isEmpty() || StringUtils.isEmpty(languageCode)) {
            return null;
        }
        String base = mapping.get(languageCode);
        if (base == null) {
            base = mapping.get(languageCode.replace('-', '_'));
        }
        if (base == null) {
            base = mapping.get(StringUtils.substringBefore(languageCode.replace('-', '_'), "_"));
        }
        return base;
    }
}
