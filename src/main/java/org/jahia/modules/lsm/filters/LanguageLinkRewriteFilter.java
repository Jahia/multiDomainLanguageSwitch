package org.jahia.modules.lsm.filters;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringEscapeUtils;
import org.jahia.modules.lsm.mapping.LanguageUrlMapping;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.AbstractFilter;
import org.jahia.services.render.filter.RenderChain;
import org.jahia.services.render.filter.RenderFilter;
import org.osgi.service.component.annotations.Component;

/**
 * Puts the per-language hostname registered in the site settings (property
 * {@code lsm:languageUrls}) into the URLs of the rendered page that are
 * language-specific. Three kinds are handled:
 * <ul>
 * <li>the language switch menu links, that is anchors carrying the
 * {@code lsm-item} class, keyed on their {@code lang} attribute;</li>
 * <li>{@code <link rel="alternate" hreflang="…">} elements, keyed on their
 * {@code hreflang} attribute;</li>
 * <li>the {@code <link rel="canonical">} element, keyed on the locale of the
 * rendered resource.</li>
 * </ul>
 * The last two are emitted by the {@code site-settings-seo} module
 * ({@code SeoUrlFilter}, priority 16.2) and are absent when that module is not
 * deployed, in which case those two passes simply find nothing.
 * <p>
 * This runs as an outer render filter (priority 5). In Jahia's render chain
 * {@code execute()} runs in decreasing priority order, so priority 5 executes
 * last: after Jahia's outbound SEO URL rewriting has produced the final
 * localized links, and after {@code SeoUrlFilter} has inserted its head
 * elements. Being below 16 also means running outside the aggregate cache, on
 * every request, so cached fragments stay host-neutral.
 * <p>
 * Doing it earlier is not possible: the SEO rewriting normalizes any absolute
 * URL whose host belongs to the current site back to a local URL.
 */
@Component(service = RenderFilter.class, immediate = true)
public class LanguageLinkRewriteFilter extends AbstractFilter {

    // Emitted by SwitchToLanguageUrlTag. Groups are kept flat (never nested) so the
    // generic rewrite below can append every group but the href one verbatim.
    private static final Pattern MENU_LINK = Pattern.compile(
            "(<a class=\"lsm-item[^\"]*\" href=\")([^\"]*)(\" lang=\")([A-Za-z0-9-]+)(\")");

    // Emitted by site-settings-seo as: <link rel="alternate" hreflang="%s" href="%s" />
    private static final Pattern ALTERNATE_LINK = Pattern.compile(
            "(<link\\s+rel=\"alternate\"\\s+hreflang=\")([A-Za-z0-9-]+)(\"\\s+href=\")([^\"]*)(\")");

    // Emitted by site-settings-seo as: <link rel="canonical" href="%s" />
    private static final Pattern CANONICAL_LINK = Pattern.compile(
            "(<link\\s+rel=\"canonical\"\\s+href=\")([^\"]*)(\")");

    public LanguageLinkRewriteFilter() {
        setPriority(5);
        setApplyOnModes("live");
        setApplyOnConfigurations("page");
        setApplyOnMainResource(true);
        setDescription("Rewrites language switch menu links, hreflang alternates and the canonical link "
                + "to the domain mapped to each language");
    }

    @Override
    public String execute(String previousOut, RenderContext renderContext, Resource resource, RenderChain chain)
            throws Exception {
        if (previousOut == null) {
            return null;
        }
        // Cheapest gates first, so a site that does not use the module never pays
        // for scanning the rendered page
        Map<String, String> mapping = LanguageUrlMapping.read(renderContext.getSite());
        if (mapping.isEmpty()) {
            return previousOut;
        }

        String out = previousOut;
        if (out.contains("lsm-item")) {
            out = rewriteByLanguageAttribute(out, MENU_LINK, mapping, 2, 4);
        }
        if (out.contains("rel=\"alternate\"")) {
            out = rewriteByLanguageAttribute(out, ALTERNATE_LINK, mapping, 4, 2);
        }
        if (out.contains("rel=\"canonical\"")) {
            out = rewriteCanonical(out, mapping, resource);
        }
        return out;
    }

    /**
     * Rewrites the host of every match of {@code pattern}, taking the target
     * language from one of its groups. Every group of the match is preserved
     * except the href one, so the surrounding markup is untouched.
     *
     * @param hrefGroup index of the group holding the URL to rewrite
     * @param langGroup index of the group holding the language code (BCP 47 or Java form)
     */
    private String rewriteByLanguageAttribute(String html, Pattern pattern, Map<String, String> mapping,
                                              int hrefGroup, int langGroup) {
        Matcher matcher = pattern.matcher(html);
        StringBuffer result = new StringBuffer(html.length());
        while (matcher.find()) {
            String base = LanguageUrlMapping.lookup(mapping, matcher.group(langGroup));
            StringBuilder replacement = new StringBuilder();
            for (int group = 1; group <= matcher.groupCount(); group++) {
                String value = matcher.group(group);
                if (group == hrefGroup) {
                    replacement.append(base != null ? applyBase(value, base) : value);
                } else {
                    replacement.append(value);
                }
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement.toString()));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * The canonical link carries no language of its own: it describes the page
     * being rendered, so it is keyed on the locale of the resource.
     */
    private String rewriteCanonical(String html, Map<String, String> mapping, Resource resource) {
        String base = LanguageUrlMapping.lookup(mapping, resource.getLocale().toString());
        if (base == null) {
            return html;
        }
        Matcher matcher = CANONICAL_LINK.matcher(html);
        StringBuffer result = new StringBuffer(html.length());
        while (matcher.find()) {
            matcher.appendReplacement(result, Matcher.quoteReplacement(
                    matcher.group(1) + applyBase(matcher.group(2), base) + matcher.group(3)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Puts {@code base} in front of a root-relative URL, or replaces the
     * scheme/host/port of an absolute one, keeping its path and query.
     * <p>
     * Only the base is escaped, never the href: the href was captured from
     * already-rendered HTML and is escaped, so escaping it again would turn a
     * query separator {@code &amp;} into {@code &amp;amp;}. The base comes from
     * the JCR unescaped. {@code LanguageUrlMapping} already rebuilds it from
     * parsed URI components, so nothing escapable can reach here; escaping keeps
     * that true if the mapping is ever read from a looser source.
     */
    private String applyBase(String href, String base) {
        String safeBase = StringEscapeUtils.escapeXml(base);
        if (href.startsWith("/")) {
            return safeBase + href;
        }
        if (href.startsWith("http://") || href.startsWith("https://")) {
            int pathStart = href.indexOf('/', href.indexOf("://") + 3);
            return pathStart >= 0 ? safeBase + href.substring(pathStart) : safeBase;
        }
        // Anything else (protocol-relative, mailto:, fragment only) is left alone
        return href;
    }
}
