import {createSite as jahiaCreateSite, deleteSite as jahiaDeleteSite, enableModule} from '@jahia/cypress';

export const SITE_KEY = 'lsmtest';

// The domain network. Values come from cypress.config.ts so a developer can move
// the whole test bed to another port with LSM_PORT without touching a spec.
export const HOST_WWW = Cypress.env('HOST_WWW') as string;
export const HOST_DE = Cypress.env('HOST_DE') as string;
export const HOST_IT = Cypress.env('HOST_IT') as string;
export const HOST_CH = Cypress.env('HOST_CH') as string;

/** Hostname only, as Jahia stores it in the server name and its aliases. */
export const hostname = (base: string) => new URL(base).hostname;

// Site languages. fr is the default, so its URLs carry no language segment.
// fr_CH is the point of the whole exercise: a regional locale whose language
// code alone (fr) must never be used as the mapping key.
export const LANG_DEFAULT = 'fr';
export const LANGUAGES = 'fr,en,de,fr_CH,it';

// The mapping under test. fr and en deliberately share www: the mapping is not
// required to be one domain per language, and languages sharing a host must not
// redirect between themselves.
export const MAPPING: Record<string, string> = {
    fr: HOST_WWW,
    en: HOST_WWW,
    de: HOST_DE,
    // Java locale form, not camel case: this is the key the module compares against
    // eslint-disable-next-line camelcase
    fr_CH: HOST_CH
};

export const HOME = `/sites/${SITE_KEY}/home`;

// A page WITHOUT vanity URLs. Jahia 301-redirects a technical short URL to the
// page's default vanity when one exists, so the home page — which carries a
// vanity per language — cannot be used to assert the module's own 302 on a plain
// /<lang>/<page>.html URL. This second page provides that case.
export const PLAIN_PAGE = 'plain';
export const PLAIN_PATH = `${HOME}/${PLAIN_PAGE}`;

// A second-level page, to prove the whole path survives both the host rewrite in
// the menu and the redirect — not just a single segment.
export const SUB_PAGE = 'sub';
export const SUB_PATH = `${PLAIN_PATH}/${SUB_PAGE}`;

/**
 * Render URL of the mapping panel. The site settings UI embeds this page in an
 * iframe inside the administration SPA, so driving it directly is both simpler
 * and closer to what the JSP does: its own script derives the action URL from
 * window.location, giving the real save flow.
 */
export const adminUrl = (siteKey: string = SITE_KEY, lang = LANG_DEFAULT) =>
    `/cms/editframe/default/${lang}/sites/${siteKey}.languageUrlSettings.html`;

/** URL of the saveLanguageUrls action, dispatched through Jahia's action + security filters. */
export const actionUrl = (siteKey: string = SITE_KEY, lang = LANG_DEFAULT) =>
    `/cms/render/default/${lang}/sites/${siteKey}.saveLanguageUrls.do`;

export const createTestSite = () => {
    jahiaCreateSite(SITE_KEY, {
        templateSet: 'empty-templates',
        serverName: hostname(HOST_WWW),
        locale: LANG_DEFAULT,
        languages: LANGUAGES
    });
    enableModule('multi-domain-language-switch', SITE_KEY);
    // Every other host of the network must be known to Jahia, or it cannot even
    // resolve the request to a site
    setServerNameAliases([hostname(HOST_DE), hostname(HOST_IT), hostname(HOST_CH)]);
};

export const deleteTestSite = () => {
    jahiaDeleteSite(SITE_KEY);
};

/**
 * Runs a setup Groovy script and fails the test if it did not succeed.
 *
 * cy.executeGroovy resolves to the provisioning result entry ('.installed' on
 * success, '.failed' otherwise) and asserts nothing by itself. Without this
 * check a script that throws — a wrong path, a missing node type — leaves the
 * setup silently incomplete and every later spec fails for the wrong reason.
 */
const runGroovy = (script: string, replacements: Record<string, string>) =>
    cy.executeGroovy(`groovy/lsm/${script}`, replacements).then(result => {
        expect(String(result), `groovy script ${script} failed`).to.not.contain('failed');
    });

const setServerNameAliases = (aliases: string[], siteKey: string = SITE_KEY) =>
    runGroovy('setServerNameAliases.groovy', {
        '@@SITE_KEY@@': siteKey,
        '@@ALIASES@@': aliases.join(',')
    });

/** Writes the mapping directly on the site node, bypassing the action. */
export const setMapping = (mapping: Record<string, string>, siteKey: string = SITE_KEY) => {
    const entries = Object.entries(mapping)
        .map(([lang, url]) => `${lang}=${url}`)
        .join(',');
    // Base64: the injection tests store values containing quotes, which would
    // otherwise break the Groovy string literal the substitution produces.
    return runGroovy('setLanguageUrlMapping.groovy', {
        '@@SITE_KEY@@': siteKey,
        '@@ENTRIES_B64@@': Buffer.from(entries, 'utf8').toString('base64')
    });
};

export const addLanguageMenu = (pagePath: string, area = 'pagecontent', nodeName = 'lsmMenu') =>
    runGroovy('addLanguageMenu.groovy', {
        '@@PAGE_PATH@@': pagePath,
        '@@AREA@@': area,
        '@@NODE_NAME@@': nodeName
    });

export const createVanityUrl = (pagePath: string, lang: string, url: string) =>
    runGroovy('createVanityUrl.groovy', {
        '@@PAGE_PATH@@': pagePath,
        '@@LANG@@': lang,
        '@@URL@@': url
    });

/** Creates a child page translated into every site language, with no vanity URL. */
export const createPage = (parentPath: string, pageName: string, titlePrefix = 'Page') =>
    runGroovy('createPage.groovy', {
        '@@PARENT_PATH@@': parentPath,
        '@@PAGE_NAME@@': pageName,
        '@@LANGUAGES@@': LANGUAGES,
        '@@TITLE_PREFIX@@': titlePrefix
    });

/**
 * Translates a page into every language of the test site. The empty-templates
 * home page ships with an English translation only, so without this a site whose
 * default language is not English cannot render at all.
 */
export const setPageTitles = (pagePath: string, titlePrefix = 'Home') =>
    runGroovy('setPageTitles.groovy', {
        '@@PAGE_PATH@@': pagePath,
        '@@LANGUAGES@@': LANGUAGES,
        '@@TITLE_PREFIX@@': titlePrefix
    });

/**
 * Sets a user's preferredLanguage, which is what RenderContext.getUILocale()
 * resolves. The administration panel follows it; the front-end menu does not,
 * because there the page's own language is the right one.
 */
export const setPreferredLanguage = (language: string, user = 'root') =>
    runGroovy('setUserPreferredLanguage.groovy', {
        '@@USER@@': user,
        '@@LANGUAGE@@': language
    });

/**
 * Fetches a URL without following redirects and without failing on a non-2xx
 * status, which is what every redirect assertion needs.
 */
export const fetchRaw = (url: string) =>
    cy.request({url, followRedirect: false, failOnStatusCode: false});

/** Live URL of a page for one language, in Jahia's default (non-vanity) short form. */
export const liveUrl = (host: string, lang: string, pagePath: string) => {
    const path = pagePath.replace(`/sites/${SITE_KEY}`, '');
    const prefix = lang === LANG_DEFAULT ? '' : `/${lang}`;
    return `${host}${prefix}${path}.html`;
};

