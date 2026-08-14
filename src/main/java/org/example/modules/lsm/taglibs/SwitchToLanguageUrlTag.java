package org.example.modules.lsm.taglibs;

import java.io.IOException;
import java.util.Locale;

import javax.servlet.jsp.tagext.Tag;

import org.apache.commons.lang.StringEscapeUtils;
import org.jahia.taglibs.AbstractJahiaTag;
import org.jahia.utils.LanguageCodeConverters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JSP tag that renders an accessible language switch link for the current main
 * resource (endonym label, BCP 47 lang/hreflang, aria-current on the current
 * language). The per-language domain itself is enforced at request time by
 * {@link org.example.modules.lsm.filters.LanguageDomainRedirectFilter}.
 */
public class SwitchToLanguageUrlTag extends AbstractJahiaTag {

    private static final Logger logger = LoggerFactory.getLogger(SwitchToLanguageUrlTag.class);

    @Override
    public int doStartTag() {
        try {
            final String currentLanguage = getCurrentResource().getLocale().getLanguage();
            final String code = getLanguageCode();

            final Locale locale = LanguageCodeConverters.languageCodeToLocale(code);
            final String displayLanguage = locale.getDisplayLanguage(locale);
            // BCP 47 form (hyphen), valid for HTML lang/hreflang attributes
            final String bcp47 = locale.toLanguageTag();
            final boolean isCurrent = currentLanguage.equals(locale.getLanguage());

            // Standard switch link to the main resource. Jahia's outbound URL rewriting
            // localizes it; the per-language domain is then enforced at request time by
            // LanguageDomainRedirectFilter.
            String link = generateCurrentNodeLangSwitchLink(code);

            final StringBuilder buff = new StringBuilder(300);
            buff.append("<a class=\"lsm-item").append(isCurrent ? " lsm-current" : "").append('"')
                    .append(" href=\"").append(StringEscapeUtils.escapeXml(link)).append('"')
                    .append(" lang=\"").append(StringEscapeUtils.escapeXml(bcp47)).append('"')
                    .append(" hreflang=\"").append(StringEscapeUtils.escapeXml(bcp47)).append('"');
            if (isCurrent) {
                buff.append(" aria-current=\"page\"");
            }
            buff.append('>')
                    .append(StringEscapeUtils.escapeXml(displayLanguage))
                    .append("</a>");

            pageContext.getOut().print(buff.toString());
        } catch (IOException e) {
            logger.error("Error while generating language switch link", e);
        }
        return Tag.SKIP_BODY;
    }

}
