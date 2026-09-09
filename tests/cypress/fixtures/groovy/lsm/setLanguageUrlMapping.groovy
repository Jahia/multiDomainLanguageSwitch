import org.jahia.services.content.*
import org.jahia.api.Constants

/**
 * Writes the per-language mapping straight onto the site node, the same way the
 * saveLanguageUrls action does: mixin lsm:languageUrlSettings, multi-valued
 * property lsm:languageUrls holding "lang=url" entries.
 *
 * This is the setup shortcut. The action itself, with its permission and CSRF
 * handling, is exercised for real by 07-settings-action.cy.ts.
 *
 * ENTRIES arrives base64-encoded: the security tests deliberately store values
 * containing quotes, which would otherwise terminate the Groovy string literal
 * that the provisioning substitution builds.
 *
 * Params: SITE_KEY, ENTRIES_B64 (base64 of comma-separated "lang=url" pairs).
 */
def siteKey = '@@SITE_KEY@@'
def encoded = '@@ENTRIES_B64@@'
def decoded = encoded ? new String(encoded.decodeBase64(), 'UTF-8') : ''
def entries = decoded.split(',').collect { it.trim() }.findAll { it }

def session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null)
session.refresh(false)
def site = session.getNode('/sites/' + siteKey)
if (!site.isNodeType('lsm:languageUrlSettings')) {
    site.addMixin('lsm:languageUrlSettings')
}
site.setProperty('lsm:languageUrls', entries as String[])
session.save()
println "mapping-set:${siteKey}:${entries.size()}"
