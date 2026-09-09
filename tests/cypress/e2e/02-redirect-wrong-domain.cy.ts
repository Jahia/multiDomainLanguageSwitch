import {HOST_CH, HOST_DE, HOST_WWW, PLAIN_PATH, SUB_PATH, fetchRaw, liveUrl} from '../support/lsm';

/**
 * The other half: whatever link or search engine result brought the visitor in,
 * a page in a mapped language answers on its own domain. This is what makes the
 * per-language domain an entry point rather than a decoration.
 *
 * These assertions use the pages that carry NO vanity URL. Jahia 301-redirects a
 * technical short URL to the page's default vanity when one exists, so a page
 * with vanities would answer 301 before this module's 302 — that chain is
 * covered by 03-vanity-preserved instead.
 */
describe('Requesting a language on the wrong domain redirects', () => {
    before(() => {
        cy.logout();
    });

    it('a German page on www redirects to the German host, same path', () => {
        fetchRaw(liveUrl(HOST_WWW, 'de', PLAIN_PATH)).then(res => {
            expect(res.status).to.eq(302);
            expect(res.headers.location).to.eq(liveUrl(HOST_DE, 'de', PLAIN_PATH));
        });
    });

    it('a Swiss French page on www redirects to the Swiss host', () => {
        fetchRaw(liveUrl(HOST_WWW, 'fr_CH', PLAIN_PATH)).then(res => {
            expect(res.status).to.eq(302);
            expect(res.headers.location).to.eq(liveUrl(HOST_CH, 'fr_CH', PLAIN_PATH));
        });
    });

    it('a second-level page keeps its full path across the redirect', () => {
        fetchRaw(liveUrl(HOST_WWW, 'de', SUB_PATH)).then(res => {
            expect(res.status).to.eq(302);
            expect(res.headers.location).to.eq(liveUrl(HOST_DE, 'de', SUB_PATH));
        });
    });

    it('the redirect lands on a page, not another redirect', () => {
        fetchRaw(liveUrl(HOST_WWW, 'de', PLAIN_PATH)).then(res => {
            fetchRaw(String(res.headers.location)).then(landing => {
                expect(landing.status, 'redirect loop or dead end').to.eq(200);
            });
        });
    });

    it('a query string is carried over', () => {
        fetchRaw(`${liveUrl(HOST_WWW, 'de', PLAIN_PATH)}?debug=1&page=2`).then(res => {
            expect(res.status).to.eq(302);
            expect(res.headers.location).to.eq(`${liveUrl(HOST_DE, 'de', PLAIN_PATH)}?debug=1&page=2`);
        });
    });

    it('a page already on its own domain is served directly', () => {
        fetchRaw(liveUrl(HOST_DE, 'de', PLAIN_PATH)).then(res => {
            expect(res.status).to.eq(200);
        });
    });

    it('an unmapped language is not redirected anywhere', () => {
        // It is a site language with no mapping entry
        fetchRaw(liveUrl(HOST_WWW, 'it', PLAIN_PATH)).then(res => {
            expect(res.status).to.eq(200);
        });
    });
});
