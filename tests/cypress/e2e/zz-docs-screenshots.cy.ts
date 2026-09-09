import {HOST_WWW, SITE_KEY, adminUrl, setPreferredLanguage} from '../support/lsm';

/**
 * Regenerates the screenshots used in README.md. Skipped unless asked for, so a
 * normal run stays fast and does not rewrite images:
 *
 *   LSM_PORT=8081 CYPRESS_DOCS=1 npx cypress run --spec cypress/e2e/zz-docs-screenshots.cy.ts
 *
 * Run 00-setup first (the site has to exist), then copy the results:
 *   cp results/screenshots/zz-docs-screenshots.cy.ts/*.png ../docs/images/
 *
 * Runs late in the alphabet so the suite reaches it with the site still up, and
 * before 99-teardown removes it.
 */
const enabled = Boolean(Cypress.env('DOCS'));

describe('Documentation screenshots', () => {
    (enabled ? it : it.skip)('captures the settings panel', () => {
        cy.login();
        // The panel follows the administrator's language; the docs are in English
        setPreferredLanguage('en');
        cy.login();
        cy.visit(`${HOST_WWW}${adminUrl(SITE_KEY, 'en')}`);
        cy.get('#lsm-form').should('be.visible');
        cy.get('#lsm-url-fr').should('exist');
        // The whole panel, not the viewport: the page is a fragment served in an
        // iframe, so its own bounds are the meaningful frame.
        cy.get('main.lsm-settings').screenshot('site-settings-panel', {overwrite: true});
    });

    (enabled ? it : it.skip)('captures the settings panel with a rejected value', () => {
        cy.login();
        cy.visit(`${HOST_WWW}${adminUrl(SITE_KEY, 'en')}`);
        // Drive the error state through the DOM rather than a save: the save path
        // needs a CSRF page token the panel cannot obtain outside the admin SPA
        // (see 07-settings-panel), and this screenshot documents the state, not
        // the round trip.
        cy.get('#lsm-url-it').clear();
        cy.get('#lsm-url-it').type('htp://typo.example.it');
        cy.window().then(win => {
            const doc = win.document;
            const field = doc.querySelector('.lsm-field[data-lsm-lang="it"]');
            const input = field.querySelector('.lsm-input');
            const error = field.querySelector('.lsm-field-error') as HTMLElement;
            const status = doc.getElementById('lsm-status');
            field.classList.add('is-invalid');
            input.setAttribute('aria-invalid', 'true');
            error.textContent = status.getAttribute('data-msg-rejected');
            error.hidden = false;
            status.setAttribute('data-state', 'error');
            status.textContent = status.getAttribute('data-msg-rejected');
        });
        cy.get('.lsm-field-error').should('be.visible');
        cy.get('main.lsm-settings').screenshot('site-settings-panel-error', {overwrite: true});
    });

    (enabled ? it : it.skip)('captures the language menu', () => {
        cy.logout();
        cy.visit(`${HOST_WWW}/accueil`);
        cy.get('.lsm-language-menu').should('be.visible');
        // Padding: outline-offset draws the focus ring outside the element box,
        // and an element screenshot would clip it.
        cy.get('.lsm-language-menu').screenshot('language-menu', {overwrite: true, padding: 10});
    });

    (enabled ? it : it.skip)('captures the language menu with focus on an item', () => {
        cy.logout();
        cy.visit(`${HOST_WWW}/accueil`);
        // The focus indicator is drawn in currentColor so it survives any palette;
        // showing it is the point of the image.
        cy.get('.lsm-language-menu a.lsm-item').first().focus();
        cy.get('.lsm-language-menu').screenshot('language-menu-focus', {overwrite: true, padding: 10});
    });
});
