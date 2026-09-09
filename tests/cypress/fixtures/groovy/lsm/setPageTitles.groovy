import org.jahia.services.content.*
import org.jahia.api.Constants
import org.jahia.utils.LanguageCodeConverters

/**
 * Gives a page a title in every language under test.
 *
 * The empty-templates template set ships its home page with an English
 * translation only, so a site whose default language is not English gets a page
 * that cannot render: in live, a language with no translation answers 404, and
 * the language menu hides it. Every language of the test site therefore needs a
 * translation before anything can be asserted.
 *
 * Params: PAGE_PATH, LANGUAGES (comma-separated Java locale codes),
 * TITLE_PREFIX (the title becomes "<prefix> <lang>").
 */
def pagePath = '@@PAGE_PATH@@'
def languages = '@@LANGUAGES@@'.split(',').collect { it.trim() }.findAll { it }
def titlePrefix = '@@TITLE_PREFIX@@'

languages.each { lang ->
    def locale = LanguageCodeConverters.languageCodeToLocale(lang)
    JCRTemplate.instance.doExecuteWithSystemSession(null, Constants.EDIT_WORKSPACE, locale, { session ->
        def page = session.getNode(pagePath)
        if (!page.isNodeType('mix:title')) {
            page.addMixin('mix:title')
        }
        page.setProperty('jcr:title', titlePrefix + ' ' + lang)
        session.save()
        return null
    })
}
println "titles-set:${pagePath}:${languages.join(',')}"
