package org.jahia.modules.lsm.filters;

import java.net.URI;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.jahia.modules.lsm.mapping.LanguageUrlMapping;
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
        Map<String, String> mapping = LanguageUrlMapping.read(renderContext.getSite());
        if (mapping.isEmpty()) {
            return null;
        }
        // Full locale (fr_CH), not just the language (fr): getLanguage() would collapse
        // fr and fr_CH to the same key and always match the first one found.
        String mappedBase = mapping.get(resource.getLocale().toString());
        if (mappedBase == null) {
            return null;
        }

        HttpServletRequest request = renderContext.getRequest();
        // Safe by construction: LanguageUrlMapping rebuilds every base from parsed
        // URI components, so this cannot throw and carries no path, query or credentials
        URI target = URI.create(mappedBase);
        String targetHost = target.getHost();
        int targetPort = normalizePort(target.getPort(), target.getScheme());
        int requestPort = normalizePort(request.getServerPort(), request.getScheme());

        if (targetHost.equalsIgnoreCase(request.getServerName()) && targetPort == requestPort) {
            return null;
        }

        // Original client-facing URI: the SEO inbound rewrite forwards pretty URLs to
        // /cms/render/..., and getRequestURI() then returns the forwarded path
        String uri = (String) request.getAttribute("javax.servlet.forward.request_uri");
        if (uri == null) {
            uri = request.getRequestURI();
        }
        String query = request.getQueryString();
        String location = mappedBase + uri + (StringUtils.isNotEmpty(query) ? "?" + query : "");
        HttpServletResponse response = renderContext.getResponse();
        if (!response.isCommitted()) {
            logger.debug("Redirecting {} request on {} to {}", resource.getLocale(), request.getServerName(), location);
            response.sendRedirect(location);
        }
        return "";
    }

    private int normalizePort(int port, String scheme) {
        if (port > 0) {
            return port;
        }
        return "https".equalsIgnoreCase(scheme) ? 443 : 80;
    }
}
