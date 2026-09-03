package org.example.modules.lsm.filters;

import java.net.URI;

import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.filter.AbstractFilter;
import org.jahia.services.render.filter.RenderChain;
import org.jahia.services.render.filter.RenderFilter;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Enforces the per-language domain mapping configured on the site
 * (mixin {@code lsm:languageUrlSettings}, property {@code lsm:languageUrls}).
 * <p>
 * In live mode, when the requested language is mapped to a base URL whose
 * host/port differs from the one serving the current request, the visitor is
 * redirected (302) to the same path on the mapped host. Combined with a
 * standard language switch menu (relative links), this moves visitors to the
 * domain dedicated to each language.
 */
@Component(service = RenderFilter.class, immediate = true)
public class LanguageDomainRedirectFilter extends AbstractFilter {

    private static final Logger logger = LoggerFactory.getLogger(LanguageDomainRedirectFilter.class);

    public LanguageDomainRedirectFilter() {
        // Priority below 16: executed on every request, before the aggregate cache
        setPriority(13);
        setApplyOnModes("live");
        setApplyOnConfigurations("page");
        setApplyOnMainResource(true);
        setDescription("Redirects to the domain mapped to the requested language (multi-domain-language-switch)");
    }

    @Override
    public String prepare(RenderContext renderContext, Resource resource, RenderChain chain) throws Exception {
        JCRSiteNode site = renderContext.getSite();
        if (site == null || !site.isNodeType("lsm:languageUrlSettings")) {
            return null;
        }
        // Full locale (fr_CH), not just the language (fr): getLanguage() would collapse
        // fr and fr_CH to the same key and always match the first one found.
        String mappedBase = getMappedBase(site, resource.getLocale().toString());
        if (mappedBase == null) {
            return null;
        }

        HttpServletRequest request = renderContext.getRequest();
        URI target = URI.create(mappedBase);
        String targetHost = target.getHost();
        int targetPort = normalizePort(target.getPort(), target.getScheme());
        int requestPort = normalizePort(request.getServerPort(), request.getScheme());

        if (targetHost == null || (targetHost.equalsIgnoreCase(request.getServerName()) && targetPort == requestPort)) {
            return null;
        }

        // Original client-facing URI: the SEO inbound rewrite forwards pretty URLs to
        // /cms/render/..., and getRequestURI() then returns the forwarded path
        String uri = (String) request.getAttribute("javax.servlet.forward.request_uri");
        if (uri == null) {
            uri = request.getRequestURI();
        }
        String query = request.getQueryString();
        String location = StringUtils.stripEnd(mappedBase, "/") + uri
                + (StringUtils.isNotEmpty(query) ? "?" + query : "");
        HttpServletResponse response = renderContext.getResponse();
        if (!response.isCommitted()) {
            logger.debug("Redirecting {} request on {} to {}", resource.getLocale(), request.getServerName(), location);
            response.sendRedirect(location);
        }
        return "";
    }

    private String getMappedBase(JCRSiteNode site, String language) throws RepositoryException {
        if (!site.hasProperty("lsm:languageUrls")) {
            return null;
        }
        for (Value value : site.getProperty("lsm:languageUrls").getValues()) {
            String entry = value.getString();
            String lang = StringUtils.substringBefore(entry, "=");
            String url = StringUtils.substringAfter(entry, "=");
            if (language.equals(lang) && (url.startsWith("http://") || url.startsWith("https://"))) {
                return url.trim();
            }
        }
        return null;
    }

    private int normalizePort(int port, String scheme) {
        if (port > 0) {
            return port;
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
