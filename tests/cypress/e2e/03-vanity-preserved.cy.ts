import {HOST_CH, HOST_DE, HOST_WWW, fetchRaw} from '../support/lsm';

/**
 * The case that took the longest to pin down in EIFFAGE-316: a vanity URL asked
 * for on the wrong domain. The redirect must keep the vanity form, which only
 * works because the filter reads javax.servlet.forward.request_uri instead of
 * getRequestURI() — the latter returns the path Jahia forwarded to internally.
 */
describe('Vanity URLs survive the redirect', () => {
    before(() => {
        cy.logout();
    });

    it('the Swiss vanity URL asked on www redirects to the same vanity on the Swiss host', () => {
        fetchRaw(`${HOST_WWW}/accueil-ch`).then(res => {
            expect(res.status).to.eq(302);
            expect(res.headers.location).to.eq(`${HOST_CH}/accueil-ch`);
        });
    });

    it('the German vanity URL asked on www redirects to the German host', () => {
        fetchRaw(`${HOST_WWW}/startseite`).then(res => {
            expect(res.status).to.eq(302);
            expect(res.headers.location).to.eq(`${HOST_DE}/startseite`);
        });
    });

    it('the Location never falls back to the technical render path', () => {
        // Regression cover: with getRequestURI() the Location would carry
        // /cms/render/live/fr_CH/sites/lsmtest/home.html and lose the vanity URL
        fetchRaw(`${HOST_WWW}/accueil-ch`).then(res => {
            expect(String(res.headers.location)).to.not.contain('/cms/render/');
        });
    });

    it('the redirected vanity URL actually serves the page', () => {
        fetchRaw(`${HOST_WWW}/accueil-ch`).then(res => {
            fetchRaw(String(res.headers.location)).then(landing => {
                expect(landing.status).to.eq(200);
            });
        });
    });

    it('a vanity URL on its own domain is served without a redirect', () => {
        fetchRaw(`${HOST_CH}/accueil-ch`).then(res => {
            expect(res.status).to.eq(200);
        });
    });

    it('the default language vanity URL is served on www', () => {
        fetchRaw(`${HOST_WWW}/accueil`).then(res => {
            expect(res.status).to.eq(200);
        });
    });
});
