package org.jahia.modules.lsm.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
import org.jahia.modules.lsm.mapping.LanguageUrlMapping;
import org.jahia.services.content.JCRNodeWrapper;
import org.jahia.services.content.JCRSessionWrapper;
import org.jahia.services.content.decorator.JCRSiteNode;
import org.jahia.services.render.RenderContext;
import org.jahia.services.render.Resource;
import org.jahia.services.render.URLResolver;
import org.json.JSONObject;
import org.osgi.service.component.annotations.Component;

/**
 * Saves the per-language base URL mapping on the site node
 * (multi-valued property {@code lsm:languageUrls}, entries formatted "lang=https://host").
 * <p>
 * Only http(s) URLs reduced to scheme, host and optional port are stored; see
 * {@link LanguageUrlMapping#normalize(String)} for why that is a security
 * boundary and not just input tidying.
 * <p>
 * Called from the site settings panel as {@code <site>.saveLanguageUrls.do}.
 * Expects one request parameter per site language, named {@code url-<lang>}.
 * Empty values remove the mapping for that language.
 */
@Component(service = Action.class, immediate = true)
public class SaveLanguageUrlsAction extends Action {

    public SaveLanguageUrlsAction() {
        setName("saveLanguageUrls");
        setRequireAuthenticatedUser(true);
        setRequiredPermission("siteAdminLanguageUrlMapping");
        setRequiredWorkspace("default");
    }

    @Override
    public ActionResult doExecute(HttpServletRequest req, RenderContext renderContext, Resource resource,
                                  JCRSessionWrapper session, Map<String, List<String>> parameters,
                                  URLResolver urlResolver) throws Exception {
        JCRSiteNode site = renderContext.getSite();
        JCRNodeWrapper siteNode = session.getNode(site.getPath());

        // The property is multi-valued and rewritten whole, so anything not put back
        // is deleted. A rejected value must therefore fall back to what is already
        // stored: otherwise a typo in one field silently destroys that language's
        // working mapping while the response still reports success.
        Map<String, String> stored = LanguageUrlMapping.read(siteNode);

        List<String> entries = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        List<String> kept = new ArrayList<>();
        int saved = 0;
        for (String lang : site.getLanguages()) {
            String url = getParameter(parameters, "url-" + lang);
            if (StringUtils.isBlank(url)) {
                // An emptied field is an explicit instruction to drop the mapping
                continue;
            }
            // These values are injected into href attributes of every live page, so
            // they are not merely prefix-checked: LanguageUrlMapping parses the URL
            // and rebuilds it from its scheme, host and port, which makes an attribute
            // break-out unrepresentable rather than filtered.
            String normalized = LanguageUrlMapping.normalize(url);
            if (normalized != null) {
                entries.add(lang + "=" + normalized);
                saved++;
            } else {
                rejected.add(lang);
                String previous = stored.get(lang);
                if (previous != null) {
                    entries.add(lang + "=" + previous);
                    kept.add(lang);
                }
            }
        }

        if (!siteNode.isNodeType(LanguageUrlMapping.MIXIN)) {
            siteNode.addMixin(LanguageUrlMapping.MIXIN);
        }
        siteNode.setProperty(LanguageUrlMapping.PROPERTY, entries.toArray(new String[0]));
        session.save();

        JSONObject result = new JSONObject();
        result.put("saved", saved);
        // Languages whose submitted value was refused but whose stored mapping was
        // left intact, so the caller can say "not changed" rather than "lost"
        result.put("kept", kept);
        result.put("rejected", rejected);
        return new ActionResult(HttpServletResponse.SC_OK, null, result);
    }
}
