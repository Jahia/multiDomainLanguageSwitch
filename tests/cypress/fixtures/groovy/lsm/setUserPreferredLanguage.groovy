import org.jahia.services.usermanager.JahiaUserManagerService
import org.jahia.services.content.JCRSessionFactory
import org.jahia.api.Constants

/**
 * Sets the preferredLanguage property of a user, which is what
 * RenderContext.getUILocale() resolves through UserPreferencesHelper.
 *
 * The administration panel must follow this, not the content locale in the URL:
 * that mismatch is what made the panel render in French inside an English
 * administration.
 *
 * Params: USER (user name, e.g. root), LANGUAGE (a locale code, e.g. en or fr).
 */
def userName = '@@USER@@'
def language = '@@LANGUAGE@@'

def session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null)
session.refresh(false)
def user = JahiaUserManagerService.getInstance().lookupUser(userName, session)
if (user == null) {
    throw new IllegalStateException("user not found: " + userName)
}
user.setProperty('preferredLanguage', language)
session.save()
println "preferred-language-set:${userName}:${language}"
