import {HOST_CH, HOST_DE, HOST_WWW, fetchRaw} from '../support/lsm';

/**
 * The menu links must carry the hostname mapped to each language. This is what
 * LanguageLinkRewriteFilter does, and it is the half a front proxy cannot do:
 * the proxy has no idea which links in a page belong to which language.
 *
 * The menu sits on the home page, which is served at its French vanity URL.
 */
describe('Menu links carry the mapped domain', () => {
    before(() => {
        cy.logout();
    });

    it('the menu renders on the live home page', () => {
        fetchRaw(`${HOST_WWW}/accueil`).then(res => {
            expect(res.status).to.eq(200);
            expect(String(res.body), 'the menu component did not render').to.contain('lsm-language-menu');
        });
    });

    it('a language mapped to its own domain gets that host', () => {
        fetchRaw(`${HOST_WWW}/accueil`).then(res => {
            const html = String(res.body);
            expect(html).to.match(new RegExp(`class="lsm-item[^"]*" href="${HOST_DE}/`));
            expect(html).to.match(new RegExp(`class="lsm-item[^"]*" href="${HOST_CH}/`));
        });
    });

    it('a language sharing the current host gets that host too', () => {
        fetchRaw(`${HOST_WWW}/accueil`).then(res => {
            // En is mapped to www, like the default language
            expect(String(res.body)).to.contain(`href="${HOST_WWW}/en/`);
        });
    });

    it('an unmapped language keeps a host-relative link', () => {
        // Documented behavior: an empty mapping field leaves that language on the
        // site's default URL, so its menu link is not made absolute
        fetchRaw(`${HOST_WWW}/accueil`).then(res => {
            expect(String(res.body)).to.match(/class="lsm-item[^"]*" href="\/it\//);
        });
    });

    it('every mapped language link is absolute', () => {
        fetchRaw(`${HOST_WWW}/accueil`).then(res => {
            const html = String(res.body);
            for (const lang of ['de', 'fr-CH', 'fr', 'en']) {
                const match = html.match(new RegExp(`class="lsm-item[^"]*" href="([^"]*)" lang="${lang}"`));
                expect(match, `no menu link found for ${lang}`).to.not.be.null;
                expect(match[1], `${lang} link was not rewritten`).to.match(/^https?:\/\//);
            }
        });
    });

    it('the current language is marked, and only once', () => {
        fetchRaw(`${HOST_WWW}/accueil`).then(res => {
            const html = String(res.body);
            expect((html.match(/aria-current="page"/g) ?? []).length).to.eq(1);
            expect(html).to.contain('lsm-current');
        });
    });

    it('labels are endonyms and keep the country for a regional locale', () => {
        fetchRaw(`${HOST_WWW}/accueil`).then(res => {
            const html = String(res.body);
            expect(html).to.contain('>Deutsch<');
            expect(html).to.contain('>English<');
            // Fr and fr_CH must not carry the same label
            expect(html).to.match(/fran&#231;ais \(Suisse\)|français \(Suisse\)/);
        });
    });

    it('vanity URLs survive into the menu links', () => {
        fetchRaw(`${HOST_WWW}/accueil`).then(res => {
            const html = String(res.body);
            expect(html).to.contain(`href="${HOST_DE}/startseite"`);
            expect(html).to.contain(`href="${HOST_CH}/accueil-ch"`);
            expect(html, 'a raw render path leaked into the menu').to.not.match(
                /class="lsm-item[^"]*" href="[^"]*\/cms\/render\//
            );
        });
    });
});
