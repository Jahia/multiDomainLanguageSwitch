# Cypress integration tests

End-to-end tests for `multi-domain-language-switch`, run against a real Jahia.
They cover what the unit tests in `src/test/java` cannot: actual HTTP responses,
real `Host` headers, real vanity URL resolution, and the real order of the render
filters.

## The domain network

The module's whole purpose is binding a language to a hostname, so a single
`localhost` is not a usable test bed. These tests use four hostnames:

| Host | Language | Role |
|---|---|---|
| `www.localtest.me` | `fr` (default), `en` | two languages sharing one host |
| `de.localtest.me` | `de` | dedicated host |
| `ch.localtest.me` | `fr_CH` | dedicated host, regional locale |
| `it.localtest.me` | *(unmapped)* | declared host, no mapping entry |

They resolve two different ways, with no `/etc/hosts` editing in either:

- **On a developer machine**, through the public `localtest.me` wildcard, which
  points every subdomain at `127.0.0.1`, where the container publishes port 8080.
- **In CI**, through docker network aliases on the `jahia` service (see
  `docker-compose.yml`), so the Cypress container's DNS resolves the same names
  to the Jahia container.

The specs are therefore identical in both modes. Hostnames come from
`cypress.config.ts` via `Cypress.env`, so `LSM_PORT` moves the whole test bed to
another port without touching a spec.

Jahia must also know these hosts: the site is created with `www.localtest.me` as
its server name and the three others as `j:serverNameAliases`. A hostname Jahia
does not know cannot be resolved to a site at all, and every short or vanity URL
answers 404 before the module's filters run — `00-setup` asserts the network is
sane so that failure mode cannot be mistaken for a module bug.

## Specs

| Spec | Covers |
|---|---|
| `00-setup` | module started, the four hosts reachable, site with 5 languages, home page translated into all of them, a second page with no vanity plus a second-level page under it, menu component dropped, one vanity URL per language, mapping registered, site published |
| `01-menu-hosts` | every *mapped* language link absolute and on its host, an unmapped language left host-relative, vanity URLs preserved in the menu, endonym labels distinguishing `fr` from `fr_CH`, exactly one `aria-current` |
| `02-redirect-wrong-domain` | 302 and exact `Location`, a second-level page keeping its full path, query string carried over, the redirect lands on a 200 (no loop), unmapped language not redirected |
| `03-vanity-preserved` | a vanity URL asked on the wrong domain redirects to the same vanity on the right one, and the `Location` never falls back to `/cms/render/...` |
| `04-shared-domain` | `fr` and `en` share `www` without redirecting between themselves, unmapped language keeps the default URL |
| `05-seo-head` | `hreflang` alternates and `canonical` rewritten per language, BCP 47 `fr-CH` resolving the `fr_CH` mapping key |
| `06-edit-mode` | no redirect and no host rewriting in edit or preview, while live still redirects |
| `07-settings-panel` | the mapping editor in the browser: one field per language, prefilled values, field validation, clearing a field, mapping change applying with no cache flush (the XHR save itself is pending, see Status) |
| `08-action-authz` | `saveLanguageUrls.do` called directly: anonymous denied, no unauthorized write, an authenticated request without a CSRF token still rejected (the authorized path is pending, see Status) |
| `09-mapping-injection` | a mapping value carrying quotes or a script tag never reaches the markup — the regression test for the stored XSS found during the audit |
| `99-teardown` | deletes the test site |

`00-setup` and `99-teardown` bracket the run, so specs execute in filename order.

## Provisioning

`assets/provisioning.yml` installs:

- `empty-templates`, the minimal template set the test site is built on;
- **`site-settings-seo`**, which generates the `hreflang` and `canonical` head
  elements that `05-seo-head` asserts on. The module under test rewrites what
  that module produces, so without it that spec has nothing to check — its first
  test fails explicitly with that message rather than passing vacuously.

## Setting up the docker environment

You need three things before anything runs: a Jahia EE image, a license, and a
free port.

**Image.** `docker images | grep jahia` tells you what you already have. Pin
`JAHIA_IMAGE` to a tag you hold locally; pulling `ghcr.io/jahia/jahia-ee-dev`
needs credentials for that registry.

**License.** `ci.startup.sh` refuses to start without one: it reads
`JAHIA_LICENSE` (base64 of the license XML) and, failing that, base64s
`/tmp/license.xml`. So either:

```bash
export JAHIA_LICENSE=$(base64 -i /path/to/license.xml)
# or simply
cp /path/to/license.xml /tmp/license.xml
```

**Port.** `JAHIA_PORT` (default 8080) is what the container publishes, so the
test bed can run next to a Jahia you already have on 8080.

### Option A — Jahia in docker, Cypress on the host

The quickest loop, and the one that lets you use `yarn e2e:debug` to watch the
specs run.

```bash
cd tests
yarn install

# 1. Build the module and put the JAR where the container can read it
(cd .. && mvn clean install)
cp ../target/multi-domain-language-switch-*.jar assets/

# 2. Start Jahia alone, on a port that is free
JAHIA_PORT=8081 docker compose up -d jahia
docker compose logs -f jahia     # wait for the startup to complete

# 3. Install the three modules: the one under test, empty-templates, site-settings-seo
curl -u root:<password> -X POST -H "Content-Type: application/yaml" \
  --data-binary @assets/provisioning-local.yml \
  http://localhost:8081/modules/api/provisioning

# 4. Run the specs. LSM_PORT must match the published port.
LSM_PORT=8081 CYPRESS_SUPER_USER_PASSWORD=<password> npx cypress run
LSM_PORT=8081 CYPRESS_SUPER_USER_PASSWORD=<password> npx cypress open   # interactive
```

`*.localtest.me` resolves to `127.0.0.1` through public DNS, so the four
hostnames reach the published port with no further setup. `LSM_PORT` flows into
the mapping the specs register, so the whole test bed moves with the port.

### Option B — everything in docker, as CI runs it

This is what a pipeline does: it also exercises the Cypress image build and the
docker network aliases, which Option A does not.

```bash
cd tests
./ci.build.sh      # copies ../target/*-SNAPSHOT.jar into artifacts/, builds the image
./ci.startup.sh    # starts Jahia, applies MANIFEST, then runs the specs
./ci.postrun.sh
```

Two things to know about these scripts, both inherited from `@jahia/cypress`:

- They `source .env` (or `.env.example`) **from the current directory**, so one
  of those files must exist in `tests/` — see below.
- `ci.build.sh` builds with `@jahia/cypress`'s own `env.Dockerfile`, not the
  local `Dockerfile`. The local one is only used if you run `docker build`
  yourself.

In this mode Cypress runs inside the network and reaches Jahia through the
`aliases` declared on the `jahia` service, so the hostnames work without
`localtest.me` resolving to anything useful — which is exactly why the aliases
are there.

## Against a Jahia you already run

If a Jahia is already up on `localhost:8080` with the three modules deployed,
skip docker entirely:

```bash
yarn install
CYPRESS_SUPER_USER_PASSWORD=<password> yarn e2e:debug   # interactive
CYPRESS_SUPER_USER_PASSWORD=<password> yarn e2e:ci      # headless
```

`cy.login()` reads `Cypress.env('SUPER_USER_PASSWORD')`, which
`cypress.config.ts` fills from `process.env.SUPER_USER_PASSWORD` and otherwise
defaults to `root1234`. On an instance whose root password is something else,
pass it as above or put it in `.env`.

## The .env file

`.env.example` is not committed. Create it in `tests/` with:

```bash
JAHIA_VERSION=${JAHIA_VERSION:-8.2.3.2}
JAHIA_IMAGE=${JAHIA_IMAGE:-ghcr.io/jahia/jahia-ee-dev:8.2.3.2}
TESTS_IMAGE=${TESTS_IMAGE:-jahia/multi-domain-language-switch:latest}
MODULE_ID=${MODULE_ID:-multi-domain-language-switch}
MANIFEST=${MANIFEST:-provisioning-manifest-snapshot.yml}
JAHIA_URL=${JAHIA_URL:-http://jahia:8080}
SUPER_USER_PASSWORD=${SUPER_USER_PASSWORD:-root1234}
JAHIA_LICENSE=${JAHIA_LICENSE:-""}
JAHIA_HOST=${JAHIA_HOST:-jahia}
```

## Setup helpers

`cypress/support/lsm.ts` holds the site, mapping and host constants plus the
helpers. Setup writes to the JCR through Groovy scripts in
`cypress/fixtures/groovy/lsm/` rather than through the module's own action, so a
broken action cannot silently disable the setup of every other spec — the action
is tested on its own in `08-action-authz`.

One of those scripts is worth reading: `createVanityUrl.groovy` adds
`jmix:vanityUrlMapped` before creating the `vanityUrlMapping` child node.
Omitting that mixin leaves an orphaned mapping that Jahia's resolver skips
silently, which is the exact data corruption diagnosed in EIFFAGE-316. The test
bed does it correctly on purpose, so that a vanity failure here means a module
bug and not a corrupt fixture.

## Status

Executed against Jahia **8.2.3.2** in docker: **60 passing, 4 pending, 0
failing**. `npx tsc --noEmit -p cypress/tsconfig.json` and `yarn lint` are clean.

Four tests are `it.skip` with the reason in the code, all for the same blocker:
Jahia's CSRFGuard here issues **per-page tokens**, and the panel builds its
action URL in JavaScript, so that URI never appears in a served page and gets no
page token. A programmatic caller cannot obtain one, and driving the panel
through the administration SPA needs a handle on its iframe that did not settle
within a workable timeout. What the save does is still covered: the mapping write
by *a mapping change applies on the next live request*, the authorization by the
negative cases in `08-action-authz`, and the toast markup by the JSP unit tests.

### Traps this harness had to work around

Each of these cost a debugging round on the first run; they are recorded so the
next person does not pay again.

| Trap | Consequence | Where |
|---|---|---|
| `@jahia/cypress`'s env plugin **overwrites `config.baseUrl`** with `process.env.JAHIA_URL`, or with `http://localhost:8080` when neither `JAHIA_URL` nor `SUPER_USER_PASSWORD` is set | A `baseUrl` in the config file, and even `--config baseUrl=...`, is silently discarded — every request went to the wrong Jahia | `cypress.config.ts` sets the three env vars before the plugin runs |
| The provisioning **replacement is a plain string substitution** | A key named `URL` rewrote parts of Jahia's own API names (`JAHIAMIX_VANITYURLMAPPED` became `JAHIAMIX_VANITY…MAPPED`) | keys are `@@NAME@@` in every Groovy script |
| `cy.executeGroovy` **asserts nothing** and resolves to the provisioning entry (`.installed` / `.failed`) | A failing setup script left the site half-built and every later spec failed for the wrong reason | `runGroovy` in `support/lsm.ts` fails the test on `.failed` |
| `empty-templates` ships its home page with an **English translation only** | A site whose default language is not English cannot render at all: 404 in live, languages missing from the menu | `setPageTitles.groovy`, called in setup |
| Jahia **301-redirects a technical short URL to the page's default vanity** when one exists | Asserting the module's 302 on `/de/home.html` saw Jahia's 301 first | the `plain` page carries no vanity; vanity cases use the home page |
| A child page keeps the **`home` segment** in its live URL (`/home/plain.html`) | Hand-built URLs 404 | `liveUrl()` derives the path from the JCR path |
| **Cypress clears cookies between tests** | `before(() => cy.login())` leaves every test but the first unauthenticated | `beforeEach` in the specs that need a session |
