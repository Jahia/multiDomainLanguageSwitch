# Security Policy

## Reporting a Vulnerability

Security information can be found in our [security.txt file](https://academy.jahia.com/.well-known/security.txt).

Please do not open a public issue for a suspected vulnerability in this module.

## What this module touches

Two things worth knowing when assessing it:

- The per-language base URLs stored on the site node (`lsm:languageUrls`) end up
  in the `href` of every menu link and of the `hreflang` and `canonical` head
  elements of a live page. They are parsed and rebuilt from their scheme, host
  and port — on write *and* on read, since the property can also be set by an
  import, a migration or a Groovy script. See
  `org.jahia.modules.lsm.mapping.LanguageUrlMapping`.
- The settings panel writes through the `saveLanguageUrls` action, which requires
  an authenticated user and the `siteAdminLanguageUrlMapping` permission, is
  POST-only, and saves under the caller's own JCR session so repository ACLs
  apply.
