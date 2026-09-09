import {HOST_IT, HOST_WWW, PLAIN_PATH, fetchRaw, liveUrl} from '../support/lsm';

/**
 * The mapping is not required to be one domain per language. fr and en both live
 * on www, told apart by the language segment, while de and fr_CH have their own
 * host. Two languages sharing a host must never redirect between themselves —
 * the filter compares host and port, so an equal host means no redirect.
 */
describe('Several languages on one domain', () => {
    before(() => {
        cy.logout();
    });

    it('the default language is served on its host with no language segment', () => {
        fetchRaw(liveUrl(HOST_WWW, 'fr', PLAIN_PATH)).then(res => {
            expect(res.status).to.eq(200);
        });
    });

    it('a language sharing that host is served under its segment, with no redirect', () => {
        fetchRaw(liveUrl(HOST_WWW, 'en', PLAIN_PATH)).then(res => {
            expect(res.status, 'en and fr share www: no redirect expected').to.eq(200);
        });
    });

    it('an unmapped language keeps the default site URL', () => {
        fetchRaw(liveUrl(HOST_WWW, 'it', PLAIN_PATH)).then(res => {
            expect(res.status).to.eq(200);
        });
    });

    it('an unmapped language is reachable on any declared host, which is expected', () => {
        // Documents the consequence spelled out in the FAQ: only a mapped language
        // gets one entry domain
        fetchRaw(liveUrl(HOST_IT, 'it', PLAIN_PATH)).then(res => {
            expect(res.status).to.eq(200);
        });
    });

    it('the default language vanity URL is served on the shared host', () => {
        fetchRaw(`${HOST_WWW}/accueil`).then(res => {
            expect(res.status).to.eq(200);
        });
    });
});
