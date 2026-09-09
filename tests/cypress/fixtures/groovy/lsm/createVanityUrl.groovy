import org.jahia.services.content.*
import org.jahia.services.seo.jcr.VanityUrlManager
import org.jahia.api.Constants

/**
 * Adds an active, default vanity URL for one language on a page.
 *
 * Note the addMixin(jmix:vanityUrlMapped) before creating the child node: without
 * that mixin the vanityUrlMapping subtree is orphaned and Jahia's resolver skips
 * it entirely, silently. Omitting it is the exact data corruption that broke
 * EIFFAGE-316, so the test bed does it right on purpose.
 *
 * Params: PAGE_PATH, LANG (Java locale form, e.g. fr_CH), URL (starting with /).
 */
def pagePath = '@@PAGE_PATH@@'
def lang = '@@LANG@@'
def url = '@@URL@@'

def session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null)
session.refresh(false)
def page = session.getNode(pagePath)

if (!page.isNodeType(VanityUrlManager.JAHIAMIX_VANITYURLMAPPED)) {
    page.addMixin(VanityUrlManager.JAHIAMIX_VANITYURLMAPPED)
}
def mappings = page.hasNode(VanityUrlManager.VANITYURLMAPPINGS_NODE)
        ? page.getNode(VanityUrlManager.VANITYURLMAPPINGS_NODE)
        : page.addNode(VanityUrlManager.VANITYURLMAPPINGS_NODE, VanityUrlManager.JAHIANT_VANITYURLS)

def nodeName = lang + '_' + url.replaceAll('[^a-zA-Z0-9]', '_')
if (!mappings.hasNode(nodeName)) {
    def vanity = mappings.addNode(nodeName, VanityUrlManager.JAHIANT_VANITYURL)
    vanity.setProperty(VanityUrlManager.PROPERTY_URL, url)
    vanity.setProperty(VanityUrlManager.PROPERTY_DEFAULT, true)
    vanity.setProperty(VanityUrlManager.PROPERTY_ACTIVE, true)
    vanity.setProperty(Constants.JCR_LANGUAGE, lang)
}
session.save()
println "vanity-created:${pagePath}:${lang}:${url}"
