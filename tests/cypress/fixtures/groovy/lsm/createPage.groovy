import org.jahia.services.content.*
import org.jahia.api.Constants
import org.jahia.utils.LanguageCodeConverters

/**
 * Creates a child page with a title in every language under test, and no vanity
 * URL. Needed because Jahia 301-redirects a technical short URL to the page's
 * default vanity URL when one exists, so a page carrying vanities cannot be used
 * to assert the module's own 302 on a plain /<lang>/<page>.html URL.
 *
 * Params: PARENT_PATH, PAGE_NAME, LANGUAGES (comma-separated), TITLE_PREFIX.
 */
def parentPath = '@@PARENT_PATH@@'
def pageName = '@@PAGE_NAME@@'
def languages = '@@LANGUAGES@@'.split(',').collect { it.trim() }.findAll { it }
def titlePrefix = '@@TITLE_PREFIX@@'

def session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null)
session.refresh(false)
def parent = session.getNode(parentPath)
if (!parent.hasNode(pageName)) {
    def page = parent.addNode(pageName, 'jnt:page')
    page.setProperty('j:templateName', 'empty')
    session.save()
}

languages.each { lang ->
    def locale = LanguageCodeConverters.languageCodeToLocale(lang)
    JCRTemplate.instance.doExecuteWithSystemSession(null, Constants.EDIT_WORKSPACE, locale, { s ->
        def page = s.getNode(parentPath + '/' + pageName)
        if (!page.isNodeType('mix:title')) {
            page.addMixin('mix:title')
        }
        page.setProperty('jcr:title', titlePrefix + ' ' + lang)
        s.save()
        return null
    })
}
println "page-created:${parentPath}/${pageName}"
