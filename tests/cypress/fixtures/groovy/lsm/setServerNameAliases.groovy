import org.jahia.services.content.*
import org.jahia.api.Constants

/**
 * Declares the extra hostnames of the domain network on the site, as server name
 * aliases. Without this Jahia cannot resolve a request arriving on de/it/ch to a
 * site at all, and every short or vanity URL answers 404 before the module's
 * filters ever run.
 *
 * Params: SITE_KEY, ALIASES (comma-separated hostnames).
 */
def siteKey = '@@SITE_KEY@@'
def aliases = '@@ALIASES@@'.split(',').collect { it.trim() }.findAll { it }

def session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null)
session.refresh(false)
def site = session.getNode('/sites/' + siteKey)
site.setProperty('j:serverNameAliases', aliases as String[])
session.save()
println "aliases-set:${siteKey}:${aliases.join(',')}"
