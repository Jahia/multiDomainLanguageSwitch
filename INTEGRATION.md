# Integrating and customizing the language menu

How to use `lsm:domaineSwitchLanguage` from your own module (a site factory, a
template set, a design module) and how to replace its markup with your own.

The module ships with a working view. You only need this guide if you want the
component to render your own markup, or if you want to call the switch link tag
from a view of yours.

- [What you can and cannot change](#what-you-can-and-cannot-change)
- [Option A — override the view (recommended)](#option-a--override-the-view-recommended)
- [Option B — copy the tag into your module](#option-b--copy-the-tag-into-your-module)
- [The contract the filters rely on](#the-contract-the-filters-rely-on)
- [Accessibility features to keep](#accessibility-features-to-keep)
- [Restyling without touching the view](#restyling-without-touching-the-view)
- [Troubleshooting](#troubleshooting)

## What you can and cannot change

| Layer | Where it lives | Yours to change? |
|---|---|---|
| Menu markup (`<nav>`, `<ul>`, wrappers, ordering, extra labels) | `domaineSwitchLanguage.jsp` view | Yes — override it |
| Each link's markup (`<a>` attributes, label) | `SwitchToLanguageUrlTag` | Only by copying the tag (option B) |
| CSS | `lsm.css` | Yes — override or replace |
| Host rewriting, per-language redirect, mapping storage, settings panel | `LanguageLinkRewriteFilter`, `LanguageDomainRedirectFilter`, `SaveLanguageUrlsAction` | No — leave in this module |

The last row is the reason to keep this module as a dependency rather than
copying it wholesale: those three are OSGi Declarative Services, registered in
Jahia's rendering chain. They activate on their own as soon as the module is
deployed and enabled on the site — no wiring on your side, no package visibility
question.

## Option A — override the view (recommended)

### 1. Declare the dependency

In your module's `pom.xml`:

```xml
<properties>
    <jahia-depends>default,multi-domain-language-switch</jahia-depends>
</properties>
```

**If your module also declares `maven-bundle-plugin` instructions explicitly,
check them.** A hardcoded `<Jahia-Depends>` instruction overrides the
`jahia-depends` property silently — the property never reaches the manifest, and
the taglib stays invisible at runtime with no error at build time:

```xml
<!-- WRONG: overrides the property above, module is not an effective dependency -->
<Jahia-Depends>default,search,myOtherModule</Jahia-Depends>

<!-- RIGHT -->
<Jahia-Depends>default,search,myOtherModule,multi-domain-language-switch</Jahia-Depends>
```

Verify with `unzip -p target/your-module-*.jar META-INF/MANIFEST.MF | grep Jahia-Depends`.

### 2. Import the taglib package

`Jahia-Depends` orders module startup and makes views, CND and resources
visible. It does **not** grant OSGi package visibility — that is a separate
mechanism, and both sides are needed. This module exports
`org.jahia.modules.lsm.taglibs`; your module must import it:

```xml
<plugin>
    <groupId>org.apache.felix</groupId>
    <artifactId>maven-bundle-plugin</artifactId>
    <extensions>true</extensions>
    <configuration>
        <instructions>
            <!-- bnd only derives imports from bytecode. The tag class is referenced
                 from a JSP, compiled at runtime, so this import must be explicit.
                 Keep the trailing * or you lose every inferred import. -->
            <Import-Package>org.jahia.modules.lsm.taglibs,*</Import-Package>
        </instructions>
    </configuration>
</plugin>
```

No Maven `<dependency>` on this module is needed for a JSP-only override: the JSP
is compiled by Jahia at request time, not by your build. Add one (scope
`provided`) only if your own **Java** code references the tag class.

### 3. Drop your view in

Same view path as the module's, in your own module. Jahia picks the view from the
module with the highest priority, so yours wins:

```
src/main/resources/lsm_domaineSwitchLanguage/html/domaineSwitchLanguage.jsp
src/main/resources/lsm_domaineSwitchLanguage/html/domaineSwitchLanguage.properties
```

Or ship it as a named variant an editor can pick, leaving the default in place:

```
src/main/resources/lsm_domaineSwitchLanguage/html/domaineSwitchLanguage.footer.jsp
```

Keep `cache.mainResource=true` in the `.properties` file. The links target the
current main resource, so the fragment must be cached per page, not per
component.

### 4. Worked example

A minimal view that produces a `<select>`-free inline list matching a design
system, keeping the tag for each link:

```jsp
<%@ page language="java" contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="ui" uri="http://www.jahia.org/tags/uiComponentsLib" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%-- The module's taglib. URI must match the .tld exactly. --%>
<%@ taglib prefix="lsm" uri="http://www.jahia.org/tags/lsm" %>

<%--@elvariable id="renderContext" type="org.jahia.services.render.RenderContext"--%>

<fmt:setBundle basename="resources.MyDesignModule"/>
<template:addResources type="css" resources="my-language-menu.css"/>

<c:set var="mainResourceNode" value="${renderContext.mainResource.node}"/>

<ui:initLangBarAttributes activeLanguagesOnly="${renderContext.liveMode}"/>
<c:set var="languageCodes" value="${requestScope.languageCodes}"/>

<c:if test="${fn:length(languageCodes) > 1}">
    <%-- Languages flagged invalid on the current page must be skipped --%>
    <c:set var="invalidLanguages" value=""/>
    <c:catch var="e">
        <c:if test="${! empty mainResourceNode.properties['j:invalidLanguages']}">
            <c:forEach items="${mainResourceNode.properties['j:invalidLanguages']}" var="invalidLanguage">
                <c:set var="invalidLanguages" value="${invalidLanguages} ${invalidLanguage.string}"/>
            </c:forEach>
        </c:if>
    </c:catch>

    <nav class="ds-langbar" aria-label="<fmt:message key='mydesign.langbar.label'/>">
        <ul class="ds-langbar__list">
            <c:forEach var="languageCode" items="${languageCodes}">
                <c:if test="${! empty languageCode && ! fn:contains(invalidLanguages, languageCode)}">
                    <li class="ds-langbar__item">
                        <lsm:switchToLanguageUrlLink languageCode="${languageCode}"/>
                    </li>
                </c:if>
            </c:forEach>
        </ul>
    </nav>
</c:if>
```

Everything outside the tag is yours. `ui:initLangBarAttributes` is the Jahia core
tag that fills `requestScope.languageCodes`; `activeLanguagesOnly` restricted to
live mode is what keeps inactive languages out of the public site while still
showing them to editors.

Two things this example deliberately keeps, and you should too:

- the `j:invalidLanguages` filtering, so a language with no published translation
  of the current page does not produce a dead link;
- `fn:length(languageCodes) > 1`, so a single-language site renders nothing
  rather than a one-item menu.

## Option B — copy the tag into your module

If you need control over the `<a>` element itself — different label source, extra
data attributes, a flag icon, a `<button>` instead of a link — copy
`SwitchToLanguageUrlTag.java` and its `.tld` entry into your module and use your
own taglib URI. The class only produces HTML: no OSGi service, no JCR mixin, no
dependency on the rest of this module. Copying it locally is legitimate and
removes the `Export-Package`/`Import-Package` question entirely.

You still keep `<jahia-depends>multi-domain-language-switch</jahia-depends>`,
because the two render filters and the settings panel must stay in this module.

If you go this way, read [the contract](#the-contract-the-filters-rely-on) below
before changing the generated markup.

## The contract the filters rely on

`LanguageLinkRewriteFilter` finds the menu links in the rendered page with a
regular expression, and rewrites the host in each one. It matches on this exact
shape:

```
<a class="lsm-item…" href="…" lang="…"
```

Concretely:

1. `class` must be the **first** attribute, and its value must **start** with
   `lsm-item` (extra classes after it are fine: `class="lsm-item ds-langbar__link"`).
2. `href` must come **immediately after** `class`.
3. `lang` must come **immediately after** `href`, and carry a BCP 47 code
   (`fr`, `fr-CH`).

Change the attribute order or drop the `lsm-item` class and the filter silently
stops matching: the menu still renders, the links still work, but they stay on
the current host and the multi-domain behavior is gone. Nothing is logged.

This constraint applies only if you write the anchors yourself (option B, or a
view that hand-builds links instead of calling the tag). Using
`lsm:switchToLanguageUrlLink` guarantees the contract.

Note that `LanguageDomainRedirectFilter` has no such requirement — it works off
the resolved locale of the request, so direct links and search-engine traffic
still land on the right domain even if your markup breaks the rewrite contract.
That is why a broken contract shows up as "the menu links have no domain" rather
than "multi-domain is dead".

The same filter also rewrites the `<link rel="alternate" hreflang>` and
`<link rel="canonical">` elements produced by the `site-settings-seo` module.
Those are matched on their own markup, which the core module generates, so
nothing on your side affects them — and they keep working even if you replace the
menu entirely. If you generate such elements yourself in your template instead of
using `site-settings-seo`, match its exact markup to get them rewritten too:

```html
<link rel="canonical" href="…" />
<link rel="alternate" hreflang="…" href="…" />
```

Attribute order matters here as well: `rel` before `hreflang` before `href`.

## Accessibility features to keep

The default view and tag produce an accessible menu. If you rewrite either,
carry these over:

- **`<nav>` with an accessible name** (`aria-label`), taken from a resource
  bundle so it follows the page language. Several `<nav>` landmarks on one page
  need distinct names.
- **`aria-current="page"`** on the current language, so screen reader users know
  which one is active.
- **A non-color indicator for the current language.** The default CSS uses bold
  plus underline. Color alone fails WCAG 1.4.1.
- **`lang` and `hreflang`** on each link, in BCP 47 form (hyphen, `fr-CH`, not
  `fr_CH`). `lang` tells the screen reader to switch pronunciation for the link
  text; `hreflang` describes the target document.
- **Endonyms as labels** — "Deutsch", not "German". A user who does not read the
  current page's language must still recognize their own.
- **Real links.** Keep `<a href>`, not a `<div>` with a click handler: keyboard
  operability, focus order and "open in new tab" come for free.
- **No `title` attribute as the only label**, and no truncation of the label to
  a two-letter code, which is not a language name.

## Restyling without touching the view

If the default markup is fine and only the styling is wrong, do not override the
view. Ship your own CSS and target the stable classes:

```css
.lsm-language-menu { /* the nav */ }
.lsm-language-menu ul { /* the list */ }
.lsm-language-menu a.lsm-item { /* every link */ }
.lsm-language-menu a.lsm-current { /* the current language */ }
```

Add it from your template with
`<template:addResources type="css" resources="my-overrides.css"/>`. Your module's
CSS is added after the module's own `lsm.css`, so equal-specificity rules win.

If you replace the current-language style, keep a non-color cue (see above).

## Troubleshooting

| Symptom | Cause | Fix |
|---|---|---|
| `Unable to find taglib` / `Cannot find the tag library descriptor` at render | Package not imported, or `Jahia-Depends` overridden by a hardcoded bundle-plugin instruction | Steps 1 and 2, then check the built manifest |
| Menu renders, links have the right paths but **no domain prefix** | Rewrite contract broken (attribute order or missing `lsm-item`), or no mapping saved for that language | [The contract](#the-contract-the-filters-rely-on); check the settings panel |
| Links are `/cms/render/live/fr/sites/…` instead of `/fr/page.html` or a vanity URL | The link was built by hand without going through `response.encodeURL()`, which is what triggers Jahia's outbound rewriting | Use the tag, or replicate its `encodeURL()` call |
| No domain prefix and no redirect at all, in any language | Mixin `lsm:languageUrlSettings` not on the site node — nothing was ever saved | Save the mapping once from the settings panel |
| Works in edit mode, nothing happens in live (or the reverse) | Both filters are live-mode only, by design | Test on the live site |
| Redirect loops | Two languages mapped to hosts that redirect to each other at the proxy level | Check the front proxy, not the module |
| A mapped host gives a 404 on short URLs | Host not declared on the site | Add it to the site's server name or `j:serverNameAliases` |
