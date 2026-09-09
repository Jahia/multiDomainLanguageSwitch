import {
    HOME,
    HOST_CH,
    HOST_DE,
    HOST_IT,
    HOST_WWW,
    MAPPING,
    SITE_KEY,
    addLanguageMenu,
    createTestSite,
    createVanityUrl,
    hostname,
    setMapping,
    setPageTitles,
    createPage,
    PLAIN_PAGE,
    PLAIN_PATH,
    SUB_PAGE
} from '../support/lsm';
import {publishAndWaitJobEnding} from '@jahia/cypress';

describe('Setup', () => {
    before(() => {
        cy.login();
    });

    it('the module is deployed and started', () => {
        cy.apollo({queryFile: 'graphql/jcr/query/getStartedModulesVersion.graphql'}).then((resp: any) => {
            const modules: any[] = resp?.data?.dashboard?.modules ?? [];
            const found = modules.find((m: any) => m.id === 'multi-domain-language-switch');
            expect(found, 'multi-domain-language-switch not started — deploy the OSGi bundle first').to.exist;
        });
    });

    it('the four hostnames of the domain network resolve to this Jahia', () => {
        // Guards the test bed itself: a DNS or docker alias problem must fail here
        // and not surface later as a puzzling 404 in a redirect assertion.
        for (const host of [HOST_WWW, HOST_DE, HOST_IT, HOST_CH]) {
            cy.request({url: `${host}/cms/login`, failOnStatusCode: false}).then(res => {
                expect(res.status, `${host} is not reachable`).to.be.lessThan(500);
            });
        }
    });

    it('creates the test site on www with the other hosts as aliases', () => {
        createTestSite();
        // Read the property back rather than the Groovy stdout: executeGroovy resolves
        // to the provisioning response entry, not to what the script printed.
        cy.apollo({
            queryFile: 'graphql/jcr/query/getProperty.graphql',
            variables: {path: `/sites/${SITE_KEY}`, property: 'j:serverNameAliases'}
        }).then((resp: any) => {
            const values: string[] = resp?.data?.jcr?.nodeByPath?.property?.values ?? [];
            expect(values, 'server name aliases were not applied').to.include.members([
                hostname(HOST_DE),
                hostname(HOST_IT),
                hostname(HOST_CH)
            ]);
        });
    });

    it('translates the home page into every site language', () => {
        // Empty-templates ships its home page with an English translation only:
        // without this, live rendering 404s in fr, de, fr_CH and it
        setPageTitles(HOME);
    });

    it('creates a second page with no vanity URL', () => {
        // Assertions on plain /<lang>/<page>.html URLs need a page Jahia will not
        // first 301-redirect to a vanity URL
        createPage(HOME, PLAIN_PAGE);
    });

    it('creates a second-level page under it', () => {
        // Deeper path: proves the full path is preserved, not just one segment
        createPage(PLAIN_PATH, SUB_PAGE, 'Sub');
    });

    it('drops the language menu on the home page', () => {
        addLanguageMenu(HOME);
    });

    it('gives the home page a vanity URL per language', () => {
        // One page with vanities, to prove they survive both the menu rewrite and
        // the redirect. Language codes are Java form, as stored on jcr:language.
        createVanityUrl(HOME, 'fr', '/accueil');
        createVanityUrl(HOME, 'de', '/startseite');
        createVanityUrl(HOME, 'fr_CH', '/accueil-ch');
    });

    it('registers the language to domain mapping', () => {
        setMapping(MAPPING);
    });

    it('publishes the site so the live filters have something to work on', () => {
        // Both filters are live-mode only by design
        publishAndWaitJobEnding(`/sites/${SITE_KEY}`, ['fr', 'en', 'de', 'fr_CH', 'it']);
    });
});
