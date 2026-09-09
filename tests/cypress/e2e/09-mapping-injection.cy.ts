import {HOST_WWW, MAPPING, fetchRaw, setMapping} from '../support/lsm';

/**
 * The mapping value is written by a site administrator and ends up inside the
 * href of every menu link and of the hreflang/canonical head elements. It must
 * not be possible to break out of the attribute: that would turn the
 * siteAdminLanguageUrlMapping permission into stored XSS against every visitor
 * of the site, and against editors browsing the live site with a session.
 *
 * The values used here are what the save action's validation accepts today: it
 * only checks the http(s) prefix, so everything after it is unconstrained.
 */
describe('Mapping values cannot break out of the href attribute', () => {
    after(() => {
        setMapping(MAPPING);
    });

    const injections = [
        'https://x" onmouseover="alert(1)',
        'https://x"><script>alert(1)</script>',
        'https://x\' onfocus=\'alert(1)'
    ];

    injections.forEach((payload, index) => {
        it(`payload ${index + 1} is neutralised in the rendered page`, () => {
            setMapping({...MAPPING, de: payload});
            cy.logout();
            fetchRaw(`${HOST_WWW}/accueil`).then(res => {
                const html = String(res.body);
                // Whatever the module decides to do — reject the value, escape it, or
                // drop the mapping — the raw payload must never reach the markup.
                expect(html, 'the mapping value was injected verbatim').to.not.contain(payload);
                expect(html).to.not.contain('onmouseover="alert(1)"');
                expect(html).to.not.contain('<script>alert(1)</script>');
                expect(html).to.not.contain('onfocus=\'alert(1)\'');
            });
        });
    });
});
