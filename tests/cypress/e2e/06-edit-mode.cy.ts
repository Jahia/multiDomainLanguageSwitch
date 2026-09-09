import {HOME, HOST_DE, HOST_WWW, PLAIN_PATH, SITE_KEY, adminUrl, fetchRaw, liveUrl} from '../support/lsm';

/**
 * Both filters declare setApplyOnModes("live") on purpose. An editor must keep
 * local links and must never be redirected to another domain, which would eject
 * them from their editing session — and, on a host not carrying their session
 * cookie, log them out.
 */
describe('Edit and preview modes are left alone', () => {
    // Use beforeEach, not before: Cypress clears cookies between tests, so a session
    // opened in a before hook is gone by the second test of the block.
    beforeEach(() => {
        cy.login();
    });

    after(() => {
        cy.logout();
    });

    it('an edit-mode page in a mapped language is not redirected', () => {
        cy.request({
            url: `${HOST_WWW}/cms/edit/default/de${HOME}.html`,
            followRedirect: false,
            failOnStatusCode: false
        }).then(res => {
            // Jahia legitimately 302s an edit URL towards its authoring UI. What must
            // never happen is a redirect to ANOTHER host: that is the module's doing.
            const location = String(res.headers.location ?? '');
            expect(location, 'an editor was redirected to another domain').to.not.contain(HOST_DE);
        });
    });

    it('a preview-mode page in a mapped language is not redirected', () => {
        cy.request({
            url: `${HOST_WWW}/cms/preview/default/de${HOME}.html`,
            followRedirect: false,
            failOnStatusCode: false
        }).then(res => {
            expect(res.status).to.not.eq(302);
        });
    });

    it('menu links stay local in preview, so the editor stays on their host', () => {
        cy.request({
            url: `${HOST_WWW}/cms/preview/default/fr${HOME}.html`,
            failOnStatusCode: false
        }).then(res => {
            const html = String(res.body);
            if (html.includes('lsm-item')) {
                expect(html, 'a mapped host leaked into a preview link').to.not.match(
                    /class="lsm-item[^"]*" href="https?:\/\/(de|ch)\.localtest\.me/
                );
            }
        });
    });

    it('live mode on the same site still redirects, proving the mode gate is the difference', () => {
        fetchRaw(liveUrl(HOST_WWW, 'de', PLAIN_PATH)).then(res => {
            expect(res.status).to.eq(302);
            expect(res.headers.location).to.contain(HOST_DE);
        });
    });

    it('the mapping panel renders for a site administrator', () => {
        // The panel's own render URL, which is what the administration SPA embeds.
        // The SPA route itself is an app-shell concern, not this module's.
        cy.request({url: `${HOST_WWW}${adminUrl(SITE_KEY)}`, failOnStatusCode: false}).then(res => {
            expect(res.status).to.eq(200);
            expect(String(res.body)).to.contain('lsm-form');
        });
    });
});
