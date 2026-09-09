import {HOST_CH, HOST_DE, HOST_WWW, fetchRaw} from '../support/lsm';

/**
 * Site-settings-seo generates the hreflang alternates and the canonical link
 * with the host serving the request. This module rewrites them: each alternate
 * to the host mapped to its own hreflang, the canonical to the host mapped to
 * the locale of the page being rendered.
 *
 * Ordering is not configured anywhere: execute() runs in decreasing priority
 * order, so this module's filter (5) runs after SeoUrlFilter (16.2).
 */
describe('SEO head elements carry the mapped domain', () => {
    before(() => {
        cy.logout();
    });

    it('site-settings-seo is deployed, otherwise this spec proves nothing', () => {
        fetchRaw(`${HOST_WWW}/accueil`).then(res => {
            expect(String(res.body), 'no canonical link in the head — is site-settings-seo installed?')
                .to.contain('rel="canonical"');
        });
    });

    it('the canonical of the default language page points at its own host', () => {
        fetchRaw(`${HOST_WWW}/accueil`).then(res => {
            expect(String(res.body)).to.contain(`rel="canonical" href="${HOST_WWW}`);
        });
    });

    it('the canonical of the Swiss page points at the Swiss host, not the one that served it', () => {
        fetchRaw(`${HOST_CH}/accueil-ch`).then(res => {
            expect(String(res.body)).to.contain(`rel="canonical" href="${HOST_CH}`);
        });
    });

    it('each hreflang alternate points at the host mapped to that language', () => {
        fetchRaw(`${HOST_WWW}/accueil`).then(res => {
            const html = String(res.body);
            expect(html).to.match(new RegExp(`hreflang="de" href="${HOST_DE}/`));
            expect(html).to.match(new RegExp(`hreflang="fr-CH" href="${HOST_CH}/`));
            expect(html).to.match(new RegExp(`hreflang="en" href="${HOST_WWW}/`));
        });
    });

    it('the BCP 47 hreflang fr-CH resolves the Java-form mapping key fr_CH', () => {
        fetchRaw(`${HOST_WWW}/accueil`).then(res => {
            // The mapping key is fr_CH while the attribute is fr-CH: the lookup converts
            expect(String(res.body)).to.contain(`hreflang="fr-CH" href="${HOST_CH}`);
        });
    });

    it('an unmapped language keeps the host it was generated with', () => {
        fetchRaw(`${HOST_WWW}/accueil`).then(res => {
            const alternate = String(res.body).match(/hreflang="it" href="([^"]*)"/);
            if (alternate) {
                expect(alternate[1]).to.contain(HOST_WWW);
            }
        });
    });

    it('no head element is left pointing at a foreign host for its language', () => {
        fetchRaw(`${HOST_CH}/accueil-ch`).then(res => {
            const html = String(res.body);
            // The Swiss page is served by ch, so a de alternate must not carry ch
            expect(html).to.not.match(new RegExp(`hreflang="de" href="${HOST_CH}/`));
        });
    });
});
