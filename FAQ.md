# FAQ

Questions that come up when setting this module up. See [README.md](README.md)
for what the module does and [INTEGRATION.md](INTEGRATION.md) for using it from
your own module.

**Mapping**
- [Can several languages share one domain?](#can-several-languages-share-one-domain)
- [Does every language need a mapping?](#does-every-language-need-a-mapping)
- [Which port do I put in the mapping — the front-end one or Tomcat's?](#which-port-do-i-put-in-the-mapping--the-front-end-one-or-tomcats)
- [Can I map a language to a path instead of a host?](#can-i-map-a-language-to-a-path-instead-of-a-host)
- [Why is my `fr_CH` mapping ignored?](#why-is-my-fr_ch-mapping-ignored)
- [Which URLs does the mapping accept exactly?](#which-urls-does-the-mapping-accept-exactly)
- [Do the mapped hostnames need to be declared in Jahia?](#do-the-mapped-hostnames-need-to-be-declared-in-jahia)

**Scope and behavior**
- [Is there a per-site activation?](#is-there-a-per-site-activation)
- [Why does nothing happen in edit or preview mode?](#why-does-nothing-happen-in-edit-or-preview-mode)
- [Does it work with several sites on one Jahia?](#does-it-work-with-several-sites-on-one-jahia)
- [Does it work with vanity URLs?](#does-it-work-with-vanity-urls)
- [Why is a language missing from the menu?](#why-is-a-language-missing-from-the-menu)

**Infrastructure and SEO**
- [How does it behave behind a CDN?](#how-does-it-behave-behind-a-cdn)
- [Can I get a 301 instead of a 302?](#can-i-get-a-301-instead-of-a-302)
- [Does it produce `hreflang` alternates for search engines?](#does-it-produce-hreflang-alternates-for-search-engines)
- [Do I need the `site-settings-seo` module?](#do-i-need-the-site-settings-seo-module)
- [How do I test several domains locally?](#how-do-i-test-several-domains-locally)

---

## Mapping

### Can several languages share one domain?

Yes. The mapping is not one-to-one. Point as many languages as you like at the
same host; they are told apart by the language segment in the path, exactly as in
a standard single-domain Jahia site. The default language has no segment.

A common shape is a main domain carrying most languages plus a dedicated domain
for one market:

| Language | Base URL | Resulting URL for `/page.html` |
|---|---|---|
| `fr` (default) | `https://www.example.com` | `https://www.example.com/page.html` |
| `en` | `https://www.example.com` | `https://www.example.com/en/page.html` |
| `de` | `https://www.example.com` | `https://www.example.com/de/page.html` |
| `fr_CH` | `https://ch.example.com` | `https://ch.example.com/fr_CH/page.html` |

Only `fr_CH` triggers a domain change; the other three behave like an ordinary
site. The redirect filter compares host and port, so languages sharing a host
never redirect between themselves.

### Does every language need a mapping?

No. Leave a field empty and that language keeps the site's default URL: it is not
rewritten in the menu and never redirected.

One consequence to keep in mind: an unmapped language remains reachable on
*every* hostname declared on the site, including the hosts you dedicated to other
languages. If you want one entry domain per language, map them all — including
the default language, whose mapping is what tells the module which host is its
own.

### Which port do I put in the mapping — the front-end one or Tomcat's?

**The public one**, the one visitors type or that the front proxy listens on.
Never Tomcat's internal port. The value goes straight into `href` attributes and
`Location` headers, so it has to be what a browser can reach. Omit the port for
the default of the scheme (443 for `https`, 80 for `http`).

```
https://ch.example.com          ← production behind a proxy on 443
http://it.localtest.me:8080     ← local Jahia reached directly
```

Then make sure **Jahia sees that same scheme and port**. The redirect filter
compares the mapped host *and port* against what the servlet container reports
(`request.getServerName()`, `request.getServerPort()`, `request.getScheme()`), and
a mismatch is not harmless:

> **Redirect loop warning.** If TLS is terminated in front (proxy or CDN) and
> Tomcat still sees plain `http` on port `80`, then for a mapping of
> `https://ch.example.com` the host matches but the port does not (443 vs 80). The
> filter concludes the visitor is on the wrong domain and redirects to
> `https://ch.example.com/...` — the URL they already requested. The browser
> follows, the proxy hits the origin in clear again, and you get
> `ERR_TOO_MANY_REDIRECTS` on the whole site.

The fix belongs to the platform, not the module: Tomcat must report the
client-side scheme and port. Either `RemoteIpValve` with
`protocolHeader="x-forwarded-proto"`, or `scheme` / `proxyPort` / `secure` set on
the connector. On Jahia Cloud this is already the case. Verify with a JSP or a
log line that `request.getScheme()` returns `https` and `getServerPort()` returns
`443` for an external request.

Do **not** work around it by writing `http://` in the mapping to match what the
origin sees — the menu would then hand out `http://` links to visitors.

### Can I map a language to a path instead of a host?

No. The value is a scheme plus a host, optionally a port. Both filters compare
and rewrite on host and port only, so a path in the value is silently dropped —
you would see the mapping saved and apparently ignored.

If a language needs a distinct path rather than a distinct host, that is what
Jahia's language segment and vanity URLs already do, without this module.

### Why is my `fr_CH` mapping ignored?

Check the separator. Keys are **Java locale form with an underscore** (`fr_CH`),
matching `resource.getLocale().toString()`, which is what the filters compare
against. The BCP 47 form with a hyphen (`fr-CH`) is what appears in the HTML
`lang` and `hreflang` attributes; using it as a mapping key does not match.

The link rewrite filter is more forgiving — it accepts the key in either form,
converting the HTML `lang` value before lookup. The redirect filter is not. So a
`fr-CH` key produces the confusing combination of *correct menu links* and *no
redirect*.

### Which URLs does the mapping accept exactly?

A scheme, a host, and an optional non-default port. The value is parsed and
**rebuilt from those components**, so anything else is dropped or refused:

| Value | Stored as |
|---|---|
| `https://ch.example.com` | `https://ch.example.com` |
| `HTTPS://CH.Example.com/` | `https://ch.example.com` (lower-cased, slash dropped) |
| `https://ch.example.com:443` | `https://ch.example.com` (default port dropped) |
| `http://it.localtest.me:8080` | unchanged |
| `https://ch.example.com/base/path` | `https://ch.example.com` (path dropped) |
| `https://192.168.1.10:8080`, `https://[::1]:8080` | unchanged (IP literals are fine) |
| `javascript:…`, `data:…`, `ftp://…`, `//host`, `host.com` | **refused** |
| `https://user:secret@host` | **refused** (no credentials in a public base URL) |
| anything containing a quote, angle bracket, space or newline | **refused** |

That last row is a security boundary, not tidiness: the value is injected into
the `href` of every menu link and head element of a live page, so a quote would
let a site administrator inject script into pages seen by every visitor.
Rebuilding the URL from parsed components makes that unrepresentable. The check
runs both when saving and when reading, because the property can also be written
by an import, a migration or a Groovy script.

Two known limitations of that strictness, both from Java's URI parser:

- **An internationalized domain must be given in punycode.** `https://例え.jp` is
  refused; `https://xn--r8jz45g.jp` is accepted.
- **A hostname containing an underscore is refused** (`https://foo_bar.example.com`).
  Underscores are not valid in DNS hostnames, but they do occur in internal
  naming. Use a CNAME or an alias without one.

### Do the mapped hostnames need to be declared in Jahia?

Yes. Every mapped host must be the site's server name or one of its
`j:serverNameAliases` (site properties), and must of course resolve to the Jahia
instance through DNS or the front proxy.

A hostname Jahia does not know cannot be resolved to a site, so short and vanity
URLs return a 404 — before the module's filters get a chance to run.

## Scope and behavior

### Is there a per-site activation?

Not as a switch, no. Both filters are OSGi services registered globally: once the
bundle is active they sit in the rendering chain of every site. They do not use
`applyOnModules`, and deliberately so — the redirect must work on any page,
whatever module provides its template.

What acts as the per-site switch is the mixin. Each filter checks
`site.isNodeType("lsm:languageUrlSettings")` and bails out immediately when it is
absent, and that mixin is only added the first time someone saves the mapping in
the settings panel. A site that never uses the module pays two condition checks,
one node type check, and — for the link rewrite filter — one substring scan of
the rendered page looking for `lsm-item`. Measurable in principle, invisible in
practice.

The component itself, being a content type and a view, follows the normal Jahia
rule: it is only available on sites where the module is enabled.

### Why does nothing happen in edit or preview mode?

By design. Both filters declare `setApplyOnModes("live")`. Editors keep plain
local links and are never redirected to another domain, which would eject them
from their editing session and, on a domain not carrying their session cookie,
log them out.

Test the multi-domain behavior on the live site.

### Does it work with several sites on one Jahia?

Yes, and independently per site. The mapping is a property on the site node
(`lsm:languageUrls`), so each site has its own, and a site with no mapping is
untouched. Nothing is shared or global except the filters themselves.

### Does it work with vanity URLs?

Yes, in both directions. Vanity URLs appear as-is in the menu links, and a vanity
URL requested on the wrong domain for its language is redirected while keeping its
vanity form. See
[Vanity URLs and the redirect](README.md#vanity-urls-and-the-redirect) for the
resolution order that makes this work.

### Why is a language missing from the menu?

In live mode a language only shows up if:

- it is **active in live** for the site (site languages settings), and
- the current page has a **published translation** in it — the view skips
  languages listed in the page's `j:invalidLanguages`.

This is intentional: a language in the menu always leads to a real page, never to
a 404 or to an empty translation. It also means the menu can legitimately differ
from one page to another.

## Infrastructure and SEO

### How does it behave behind a CDN?

It works, with four things to get right. Three are configuration; the first is
the one that breaks sites.

1. **The client scheme and port must reach Jahia**, or you get the redirect loop
   described [above](#which-port-do-i-put-in-the-mapping--the-front-end-one-or-tomcats).
   This is the single most likely CDN failure.
2. **The `Host` header must reach Jahia unchanged.** Both filters read
   `request.getServerName()`. A CDN or proxy that replaces the Host with the
   origin's own hostname breaks site resolution *and* the mapping comparison at
   once, and every domain falls back to the default language. Apache needs
   `ProxyPreserveHost On`; Nginx needs `proxy_set_header Host $host;`.
3. **The cache key must include the `Host`.** Serving several domains from one
   distribution with a Host-insensitive cache key means a page — or a cached 302
   — produced for the French domain gets served on the Swiss one. Several CDNs
   cache redirects by default, which makes this worse. Either include Host in the
   cache key or use one distribution per domain.
4. **Changing the mapping requires a CDN purge.** The mapped hostnames are baked
   into the delivered HTML. Inside Jahia the change applies on the next request
   with no cache flush (the filters run outside the fragment cache), but a CDN in
   front will keep serving menus pointing at the old hosts until purged.

One more thing to know: the redirect **carries the query string over** to the
other host. Both hosts belong to the same site, so this is normally what you
want — but it does mean a value in the query string crosses hosts. Do not put
anything host-scoped there, such as a one-time token tied to a single origin.

One thing you do *not* need to worry about: because the redirect guarantees a
language is only ever served on its own host, per-host caching is correct by
construction. No `Vary` on language, no cache fragmentation by cookie.

### Can I get a 301 instead of a 302?

Not through configuration today — `LanguageDomainRedirectFilter` calls
`response.sendRedirect()`, which is a 302. Changing it is a two-line edit
(`setStatus(301)` plus the `Location` header).

The 302 is a deliberate default: a 301 is cached hard by browsers and
intermediaries, so a mapping mistake pushed to production would keep redirecting
visitors long after being fixed. Move to 301 once the mapping is stable and you
want search engines to consolidate on one domain per language.

### Does it produce `hreflang` alternates for search engines?

It does not *produce* them, it *fixes* them. Generating `hreflang` alternates and
the canonical link is the job of Jahia's `site-settings-seo` module, which
inserts them into the `<head>` with the host serving the current request. This
module rewrites each one to the host mapped to its language — the alternates on
their own `hreflang`, the canonical on the locale of the rendered page. See
[SEO head elements](README.md#seo-head-elements).

So:

- **With `site-settings-seo` deployed**, you get correct per-domain alternates
  and canonical with no extra work.
- **Without it**, no such elements exist and this module has nothing to rewrite.
  The menu links still carry their `hreflang` attribute, which search engines do
  read, but the head elements are the recommended signal. Install
  `site-settings-seo`, or build the tags in your template from the same mapping
  (`lsm:languageUrls`, readable from a view).

Note that the redirect answers 302, which does not by itself declare a canonical
domain (see above) — the canonical link is what does.

### Do I need the `site-settings-seo` module?

No, it is optional and there is no declared dependency on it. The menu, the
redirect and the mapping panel work without it.

Install it if you want `hreflang` alternates and a canonical link in the
`<head>`, which for a multi-domain multilingual site you probably do. This module
then rewrites those elements to the right domain per language, automatically. The
two are wired only by render filter priority — 16.2 for `SeoUrlFilter`, 5 here,
and `execute()` runs in decreasing priority order — so nothing to configure
either way.

### How do I test several domains locally?

Use a wildcard DNS service that resolves everything to `127.0.0.1`, so you need
no `/etc/hosts` entries and no DNS setup:

```
http://www.localtest.me:8080
http://de.localtest.me:8080
http://it.localtest.me:8080
```

Declare those hostnames on the site (server name plus
`j:serverNameAliases`), map them in the settings panel, and browse the live site.
`localtest.me` and `127.0.0.1.nip.io` both work. This is the setup used in the
[worked example](README.md#worked-example).
