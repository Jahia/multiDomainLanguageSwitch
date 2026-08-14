package org.example.modules.lsm.filters;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.RepositoryException;
import javax.jcr.Value;

import org.apache.commons.lang.StringUtils;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.AbstractFilter;
import org.jahia.services.render.filter.RenderChain;
import org.jahia.services.render.filter.RenderFilter;
import org.osgi.service.component.annotations.Component;

/**
 * Rewrites the language switch menu links (anchors carrying the {@code lsm-item}
 * class) so their href targets the base URL mapped to each language in the site
 * settings (property {@code lsm:languageUrls}).
 * <p>
 * This runs as an outer render filter (priority 5): after Jahia's outbound SEO URL
 * rewriting has produced the final localized link, and outside the aggregate cache
 * (priority &lt; 16 executes on every request), so cached fragments stay host-neutral.
 * Doing it earlier is not possible: the SEO rewriting normalizes any absolute URL
 * whose host belongs to the current site back to a local URL.
 */
@Component(service = RenderFilter.class, immediate = true)
public class LanguageLinkRewriteFilter extends AbstractFilter {

    private static final Pattern MENU_LINK = Pattern.compile(
            "(<a class=\"lsm-item[^\"]*\" href=\")([^\"]*)(\" lang=\"([A-Za-z0-9-]+)\")");

    public LanguageLinkRewriteFilter() {
        setPriority(5);
        setApplyOnModes("live");
        setApplyOnConfigurations("page");
        setApplyOnMainResource(true);
        setDescription("Rewrites language switch menu links to the domain mapped to each language");
    }

    @Override
    public String execute(String previousOut, RenderContext renderContext, Resource resource, RenderChain chain)
            throws Exception {
        if (previousOut == null || !previousOut.contains("lsm-item")) {
            return previousOut;
        }
        JCRSiteNode site = renderContext.getSite();
        if (site == null || !site.isNodeType("lsm:languageUrlSettings")) {
            return previousOut;
        }
        Map<String, String> mapping = readMapping(site);
        if (mapping.isEmpty()) {
            return previousOut;
        }

        Matcher matcher = MENU_LINK.matcher(previousOut);
        StringBuffer result = new StringBuffer(previousOut.length());
        while (matcher.find()) {
            String href = matcher.group(2);
            String lang = matcher.group(4);
            String base = lookupBase(mapping, lang);
            String newHref = href;
            // Always prefix the base URL registered for the language, so every menu
            // link carries its full per-language hostname
            if (base != null) {
                if (href.startsWith("/")) {
                    newHref = base + href;
                } else if (href.startsWith("http://") || href.startsWith("https://")) {
                    int pathStart = href.indexOf('/', href.indexOf("://") + 3);
                    newHref = pathStart >= 0 ? base + href.substring(pathStart) : base;
                }
            }
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(matcher.group(1) + newHref + matcher.group(3)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private Map<String, String> readMapping(JCRSiteNode site) throws RepositoryException {
        Map<String, String> mapping = new HashMap<>();
        if (!site.hasProperty("lsm:languageUrls")) {
            return mapping;
        }
        for (Value value : site.getProperty("lsm:languageUrls").getValues()) {
            String entry = value.getString();
            String lang = StringUtils.substringBefore(entry, "=");
            String url = StringUtils.substringAfter(entry, "=").trim();
            if (StringUtils.isNotBlank(lang) && (url.startsWith("http://") || url.startsWith("https://"))) {
                mapping.put(lang, StringUtils.stripEnd(url, "/"));
            }
        }
        return mapping;
    }

    private String lookupBase(Map<String, String> mapping, String htmlLang) {
        // The lang attribute is BCP 47 (en-US); site language codes use Java form (en_US)
        String base = mapping.get(htmlLang);
        if (base == null) {
            base = mapping.get(htmlLang.replace('-', '_'));
        }
        if (base == null) {
            base = mapping.get(StringUtils.substringBefore(htmlLang, "-"));
        }
        return base;
    }
}
