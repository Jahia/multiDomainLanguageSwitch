# Product

## Register

product

## Users

Two audiences, deliberately weighted equally, in two different contexts.

**Site administrators** configuring the mapping, once. They open
*Administration → Sites → Language URL mapping* inside jContent, fill one URL per
site language, save, and leave. They are not in this panel daily; they are in it
under pressure, usually because a domain is wrong in production. The panel is
rendered in an iframe in the middle of Jahia's own administration, so it is
never the only thing on screen.

**Integrators** consuming the language menu. They drop the component into a
template and then restyle it inside their own design system, or override the view
entirely. Their job to be done is *make this fit our site without fighting the
module*. They read the markup before they read the docs.

Visitors see the menu but never configure anything: for them the component must
simply be an unremarkable, operable language switcher.

## Product Purpose

Bind each language of a Jahia site to its own domain — something Jahia does not
support natively, because a host resolves to a *site* while the language lives in
the URL path. The module supplies the two halves that follow: a menu whose links
carry the right host per language, and a redirect that gives every language one
entry domain.

Success is measured by absence. No visitor lands on the wrong domain for their
language, no editor is thrown out of their session, no integrator has to undo the
module's styling before applying their own, and no search engine is told that
five languages live on one host.

## Brand Personality

**Restrained, legible, unsurprising.** The module is infrastructure. It should
read as part of Jahia rather than as a third-party bolt-on: an administrator
should not be able to tell which settings panel came from a community module, and
an integrator should find the markup boringly predictable.

Voice in UI copy and docs: plain, specific, no marketing. State the mechanism and
its limits. When something cannot work, say so and say why.

## Anti-references

- **A settings panel that announces itself.** Its own palette, its own card
  shadows, its own button shapes inside Jahia's admin. A panel that visually
  detonates in the middle of jContent signals a badly integrated module,
  regardless of how good it looks alone.
- **An opinionated front-end component.** Pills, segmented controls, flag icons,
  imposed fonts, imposed colors, transitions. Every one of those is something the
  integrator has to undo before applying their own design, and the anchor markup
  is already constrained by a render filter contract.
- **Flag icons for languages.** A flag is a country, not a language. `fr` and
  `fr_CH` break it immediately, which is exactly this module's central case.
- **Marketing tone in documentation.** No "seamlessly", no "powerful". This
  module exists because of a three-week production incident; the docs should read
  like the incident report it earned.

## Design Principles

1. **The markup is an API.** The anchor's attribute order is a contract with a
   render filter, and integrators override views by path. Treat class names,
   attribute order and view paths as public surface: stable, documented, changed
   only deliberately.
2. **Impose nothing you cannot justify.** For the menu, the module ships
   semantics, state and operability — never colors, fonts or spacing opinions.
   What it does not impose, it documents as the integrator's responsibility.
3. **Look native, not distinct.** The admin panel borrows Jahia's density,
   controls and spacing rather than competing with them.
4. **Accessibility is built in, not bolted on.** Endonym labels, non-colour state
   indicators, keyboard operability and live-region feedback are part of the
   component's definition, not a later audit fix.
5. **Fail loudly, degrade safely.** A rejected URL says which language was
   rejected; a missing translation removes a language from the menu rather than
   producing a dead link; an editor is never redirected off their host.

## Accessibility & Inclusion

**Target: WCAG 2.2 Level AAA**, with one honest boundary.

The menu imposes no colours, so the module cannot guarantee 1.4.3 or 1.4.6
contrast there — that is the integrator's responsibility, and it is documented as
such. Everything the module *can* guarantee, it must: semantic structure, an
accessible name on the navigation landmark, `aria-current` on the current
language, a non-colour indicator of that state, visible focus that survives a
restyle, adequate target size, `lang` and `hreflang` in BCP 47 form so
pronunciation switches correctly, and endonym labels so a user who cannot read
the current page's language still recognises their own.

The admin panel is fully in the module's control and is held to AAA outright,
including 7:1 text contrast, keyboard operability, per-field error association,
and feedback announced through a live region rather than colour alone.

Reduced motion is honoured everywhere; no animation is load-bearing.
