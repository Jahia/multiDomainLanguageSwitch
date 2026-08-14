package org.example.modules.lsm.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.jahia.bin.Action;
import org.jahia.bin.ActionResult;
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
 * Called from the site settings panel as {@code <site>.saveLanguageUrls.do}.
 * Expects one request parameter per site language, named {@code url-<lang>}.
 * Empty values remove the mapping for that language. Only http(s) URLs are accepted.
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

        List<String> entries = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        for (String lang : site.getLanguages()) {
            String url = getParameter(parameters, "url-" + lang);
            if (StringUtils.isBlank(url)) {
                continue;
            }
            String trimmed = url.trim();
            // Restrict to http(s): the value ends up in a href attribute on the live site
            if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                entries.add(lang + "=" + StringUtils.stripEnd(trimmed, "/"));
            } else {
                rejected.add(lang);
            }
        }

        if (!siteNode.isNodeType("lsm:languageUrlSettings")) {
            siteNode.addMixin("lsm:languageUrlSettings");
        }
        siteNode.setProperty("lsm:languageUrls", entries.toArray(new String[0]));
        session.save();

        JSONObject result = new JSONObject();
        result.put("saved", entries.size());
        result.put("rejected", rejected);
        return new ActionResult(HttpServletResponse.SC_OK, null, result);
    }
}
