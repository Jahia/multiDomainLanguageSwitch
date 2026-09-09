import org.jahia.services.content.*
import org.jahia.api.Constants

/**
 * Drops one lsm:domaineSwitchLanguage component into a page, so the rendered
 * output actually contains the menu the rewrite filter is supposed to touch.
 *
 * The component is created inside the page's main area, creating that area node
 * if it does not exist yet, which is what Jahia does when an editor drops the
 * first content into an empty area.
 *
 * Params: PAGE_PATH (absolute path of the page), AREA (area node name),
 * NODE_NAME (name of the component node).
 */
def pagePath = '@@PAGE_PATH@@'
def areaName = '@@AREA@@'
def nodeName = '@@NODE_NAME@@'

def session = JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.EDIT_WORKSPACE, null, null)
session.refresh(false)
def page = session.getNode(pagePath)

def area = page.hasNode(areaName) ? page.getNode(areaName) : page.addNode(areaName, 'jnt:contentList')
if (!area.hasNode(nodeName)) {
    area.addNode(nodeName, 'lsm:domaineSwitchLanguage')
}
session.save()
println "menu-added:${pagePath}/${areaName}/${nodeName}"
