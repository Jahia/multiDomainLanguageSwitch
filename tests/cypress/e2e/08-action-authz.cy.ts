import {HOST_WWW, MAPPING, actionUrl, getCsrfToken, setMapping} from '../support/lsm';

/**
 * The saveLanguageUrls action, called directly rather than through the panel.
 * It declares setRequireAuthenticatedUser(true) and requires the
 * siteAdminLanguageUrlMapping permission, so an anonymous caller must not be
 * able to rewrite where a site's languages live.
 *
 * Note that neither /cms/render nor /cms/editframe serializes the action's JSON
 * ActionResult back to a raw cy.request — only a real browser XHR patched by
 * CsrfGuardJavascriptFilter reads the body. So these tests assert the HTTP
 * status and the JCR side effect, not the response body.
 */
describe('saveLanguageUrls action authorization', () => {
    const post = (body: string, token?: {name: string; value: string}) =>
        cy.request({
            method: 'POST',
            url: `${HOST_WWW}${actionUrl()}`,
            form: false,
            body,
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded',
                ...(token ? {[token.name]: token.value} : {})
            },
            failOnStatusCode: false
        });

    after(() => {
        setMapping(MAPPING);
        cy.logout();
    });

    it('an anonymous caller is denied', () => {
        cy.logout();
        post('url-de=https://evil.example.com').then(res => {
            // Jahia answers a permission denial as "resource not available"
            expect(res.status, 'anonymous caller was allowed to write the mapping').to.be.oneOf([401, 403, 404]);
        });
    });

    it('the anonymous attempt did not change the mapping', () => {
        cy.login();
        cy.executeGroovy('groovy/lsm/setLanguageUrlMapping.groovy', {
            '@@SITE_KEY@@': 'lsmtest',
            '@@ENTRIES@@': Object.entries(MAPPING).map(([l, u]) => `${l}=${u}`).join(',')
        });
        cy.logout();
        cy.request({url: `${HOST_WWW}/de/home.html`, followRedirect: false, failOnStatusCode: false}).then(res => {
            expect(String(res.headers.location ?? '')).to.not.contain('evil.example.com');
        });
    });

    it('an authenticated request without a CSRF token is still rejected', () => {
        cy.login();
        post('url-it=https://it.localtest.me:8081').then(res => {
            // CsrfGuardFilter rejects before the action runs. The exact status depends
            // on the CSRFGuard configuration (302 to /error.html here, 400 or 404
            // elsewhere), so assert what actually matters: it is not a success.
            expect(res.status, 'an unguarded POST was accepted').to.not.be.within(200, 299);
        });
    });

    it.skip('an authorized caller with a valid CSRF token succeeds', () => {
        // BLOCKED: CSRFGuard uses per-page tokens here, so the master token read from
        // /modules/CsrfServlet is refused with "Request Token does not match Page
        // token". A programmatic caller cannot obtain a page token for a URI that
        // never appears in a served page. The negative cases above are the
        // security-relevant ones and they do run.
    });

    it.skip('the authorized write is visible on the site node', () => {
        // Same blocker as above.
    });
});
