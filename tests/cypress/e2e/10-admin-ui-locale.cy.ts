import {HOST_WWW, SITE_KEY, adminUrl, setPreferredLanguage} from '../support/lsm';

/**
 * The settings panel must follow the administrator's preferred language, like
 * the rest of jContent around it — not the content locale carried by the URL.
 *
 * The regression it guards: the panel used fmt:setBundle, which resolves against
 * the JSTL request locale. On /cms/editframe/default/fr/sites/... that locale is
 * the CONTENT locale, so the panel rendered in French inside an English
 * administration. It now uses Jahia's utility:setBundle with useUILocale="true",
 * which reads RenderContext.getUILocale() and therefore the user's
 * preferredLanguage property.
 *
 * Every assertion below deliberately requests the panel with a content locale
 * that differs from the UI language: if the two ever agreed, the test could pass
 * while reading the wrong one.
 */
describe('The settings panel follows the administrator UI language', () => {
    const titleEn = 'Language URL mapping';
    const titleFr = 'Mapping URL par langue';
    const legendEn = 'Base URL per language';
    const legendFr = 'URL de base par langue';

    // Cypress clears cookies between tests, so the session has to be reopened —
    // and reopening it is also what picks up a changed preferredLanguage.
    const fetchPanel = (contentLocale: string) =>
        cy.request({url: `${HOST_WWW}${adminUrl(SITE_KEY, contentLocale)}`, failOnStatusCode: false});

    after(() => {
        cy.login();
        setPreferredLanguage('en');
    });

    it('renders in English for an English administrator, on a French content locale', () => {
        cy.login();
        setPreferredLanguage('en');
        cy.login();
        fetchPanel('fr').then(res => {
            expect(res.status).to.eq(200);
            const html = String(res.body);
            expect(html, 'the panel followed the URL locale instead of the UI language')
                .to.contain(titleEn);
            expect(html).to.contain(legendEn);
            expect(html).to.not.contain(titleFr);
        });
    });

    it('renders in French for a French administrator, on an English content locale', () => {
        cy.login();
        setPreferredLanguage('fr');
        cy.login();
        fetchPanel('en').then(res => {
            expect(res.status).to.eq(200);
            const html = String(res.body);
            expect(html, 'the UI language was ignored').to.contain(titleFr);
            expect(html).to.contain(legendFr);
            expect(html).to.not.contain(titleEn);
        });
    });

    it('translates the field labels and the hint, not only the title', () => {
        cy.login();
        setPreferredLanguage('fr');
        cy.login();
        fetchPanel('en').then(res => {
            const html = String(res.body);
            // Every string of the panel comes from the same bundle, so a single
            // localizationContext failure would leave some of them in English
            expect(html).to.contain('URL de base pour la langue');
            expect(html).to.contain('Format attendu');
            expect(html).to.contain('Enregistrer');
        });
    });

    it('keeps the mapping fields themselves untouched by the UI language', () => {
        cy.login();
        setPreferredLanguage('fr');
        cy.login();
        fetchPanel('en').then(res => {
            const html = String(res.body);
            // The site languages are content, not UI: a French administrator still
            // configures fr, en, de, fr_CH and it
            for (const lang of ['fr', 'en', 'de', 'fr_CH', 'it']) {
                expect(html, `field missing for ${lang}`).to.contain(`id="lsm-url-${lang}"`);
            }
        });
    });
});
