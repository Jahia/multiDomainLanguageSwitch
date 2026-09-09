<%@ page language="java" contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="utility" uri="http://www.jahia.org/tags/utilityLib" %>
<%--@elvariable id="renderContext" type="org.jahia.services.render.RenderContext"--%>

<%-- utility:setBundle, not fmt:setBundle, and useUILocale="true".
     This panel is an administration screen: it must follow the administrator's
     preferred language, like the rest of jContent around it. fmt:setBundle
     resolves against the JSTL request locale, which here is the CONTENT locale
     taken from the URL (.../default/fr/sites/...), so the panel ended up in
     French inside an English administration.
     Jahia's own tag reads renderContext.getUILocale(), which resolves the user's
     preferredLanguage through UserPreferencesHelper, and sets the JSTL
     localizationContext so every fmt:message below inherits it. It also loads
     the bundle through Jahia's module-aware ResourceBundles lookup.
     The front-end menu view deliberately keeps fmt:setBundle: there the content
     locale is the right one, because the menu labels belong to the page being
     read, not to the reader's account. --%>
<utility:setBundle basename="resources.MultiDomainLanguageSwitch" useUILocale="true"/>

<%-- Jahia's admin stylesheet, so the panel inherits the surrounding font and
     form-control vocabulary instead of inventing its own. --%>
<template:addResources type="css" resources="admin-bootstrap.css"/>

<%-- Existing mapping, entries "lang=https://host" --%>
<c:set var="mappingEntries" value="${renderContext.site.hasProperty('lsm:languageUrls') ? renderContext.site.getProperty('lsm:languageUrls').values : null}"/>

<%-- <main>, not a <div>: inside the administration iframe this fragment is its
     own document, so a landmark-navigation keypress would otherwise find nothing.
     Exactly one landmark, and none around the field group or the status. --%>
<main class="lsm-settings">
    <h1 class="lsm-title"><fmt:message key="lsm.siteSettings.title"/></h1>
    <p class="lsm-lede" id="lsm-desc"><fmt:message key="lsm.siteSettings.description"/></p>

    <form id="lsm-form" novalidate>
        <fieldset class="lsm-fieldset">
            <legend class="lsm-legend"><fmt:message key="lsm.siteSettings.legend"/></legend>
            <p class="lsm-hint" id="lsm-help"><fmt:message key="lsm.siteSettings.help"/></p>

            <c:forEach items="${renderContext.site.languages}" var="lang">
                <c:set var="currentValue" value=""/>
                <c:forEach var="entry" items="${mappingEntries}">
                    <c:if test="${fn:substringBefore(entry.string, '=') eq lang}">
                        <c:set var="currentValue" value="${fn:substringAfter(entry.string, '=')}"/>
                    </c:if>
                </c:forEach>
                <c:set var="safeLang" value="${fn:escapeXml(lang)}"/>

                <div class="lsm-field control-group" data-lsm-lang="${safeLang}">
                    <label class="lsm-label control-label" for="lsm-url-${safeLang}">
                        <%-- Non-breaking space, and explicit: the JSP collapses the
                             newline between the two, which rendered "language:de". --%>
                        <fmt:message key="lsm.siteSettings.field.label"/>&#160;<span
                                class="lsm-lang-code" lang="${safeLang}">${safeLang}</span>
                    </label>
                    <div class="controls">
                        <%-- novalidate on the form, plus type=url without a pattern: the
                             server is the single source of truth on what a base URL may
                             be, and a browser bubble cannot name which language failed.
                             Rejections come back from the action and land on the field. --%>
                        <input class="lsm-input"
                               type="url"
                               id="lsm-url-${safeLang}"
                               name="url-${safeLang}"
                               value="${fn:escapeXml(currentValue)}"
                               placeholder="https://www.example.${safeLang}"
                               inputmode="url"
                               autocomplete="off"
                               spellcheck="false"
                               aria-describedby="lsm-help lsm-error-${safeLang}"/>
                        <p class="lsm-field-error" id="lsm-error-${safeLang}" hidden></p>
                    </div>
                </div>
            </c:forEach>
        </fieldset>

        <div class="lsm-actions">
            <button type="submit" class="lsm-submit btn btn-primary"
                    data-label="<fmt:message key='lsm.label.save'/>"
                    data-label-busy="<fmt:message key='lsm.siteSettings.saving'/>">
                <fmt:message key="lsm.label.save"/>
            </button>
            <%-- In-flow, next to the button that caused it. A fixed-position toast is
                 the wrong instrument here: this page is an iframe inside the
                 administration, so a viewport-pinned element can land outside what the
                 administrator is looking at. --%>
            <p class="lsm-status" id="lsm-status" role="status" aria-live="polite"
               data-msg-saved="<fmt:message key='lsm.siteSettings.saved'/>"
               data-msg-error="<fmt:message key='lsm.siteSettings.error'/>"
               data-msg-rejected="<fmt:message key='lsm.siteSettings.field.rejected'/>"></p>
        </div>
    </form>
</main>

<style>
    /* Scoped to .lsm-settings so nothing leaks into the administration around it.
       Values that override admin-bootstrap.css do so for one reason each, noted
       inline: the stylesheet is a Jahia-customised Bootstrap 2.3.2 from 2012 and
       several of its defaults cannot reach WCAG 2.2 AAA. */
    main.lsm-settings, .lsm-settings {
        display: block;
        /* 11px/16px inherited from the admin stylesheet is a 1.45 line height,
           under the 1.5 that WCAG 1.4.8 (AAA) requires, and small for a form
           filled under pressure. Family and colour stay inherited. */
        font-size: 13px;
        line-height: 1.6;
        color: #1a1a1a;                 /* 18.9:1 on white */
        /* 1.4.8 (AAA): no more than 80 characters per line. */
        max-width: 72ch;
        padding: 16px 16px 24px;
    }

    .lsm-settings .lsm-title {
        margin: 0 0 4px;
        font-size: 1.4em;
        line-height: 1.3;
        font-weight: 700;
    }

    .lsm-settings .lsm-lede {
        margin: 0 0 20px;
        color: #444;                    /* 9.7:1 on white */
        text-wrap: pretty;
    }

    .lsm-settings .lsm-fieldset {
        margin: 0 0 20px;
        padding: 0;
        border: 0;                      /* Bootstrap 2 draws a rule under every legend */
    }

    .lsm-settings .lsm-legend {
        width: auto;
        margin: 0 0 4px;
        padding: 0;
        border: 0;
        font-size: 1em;
        line-height: 1.6;
        font-weight: 700;
        color: inherit;
    }

    .lsm-settings .lsm-hint {
        margin: 0 0 16px;
        color: #444;
        text-wrap: pretty;
    }

    .lsm-settings .lsm-field {
        margin: 0 0 14px;
    }

    .lsm-settings .lsm-label {
        display: block;
        float: none;                    /* admin-bootstrap floats labels into a 160px gutter */
        width: auto;
        margin: 0 0 4px;
        padding: 0;
        text-align: left;
        font-weight: 600;
    }

    .lsm-settings .lsm-lang-code {
        font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
        font-weight: 700;
    }

    .lsm-settings .controls {
        margin: 0;                      /* the 160px gutter goes with the floated label */
    }

    .lsm-settings .lsm-input {
        box-sizing: border-box;
        width: 100%;
        max-width: 44ch;
        /* 2.5.5 (AAA) asks for a 44px target; min-height rather than height so a
           user stylesheet increasing text spacing cannot clip it (1.4.12). */
        min-height: 44px;
        margin: 0;
        padding: 8px 10px;
        font: inherit;
        color: inherit;
        background-color: #fff;
        border: 1px solid #767676;      /* 4.5:1 against white: 1.4.11 non-text contrast */
        border-radius: 3px;
    }

    .lsm-settings .lsm-input::placeholder {
        color: #595959;                 /* 7:1 on white; the usual light grey fails */
        opacity: 1;                     /* Firefox dims placeholders by default */
    }

    .lsm-settings .lsm-input:focus-visible {
        outline: 3px solid #0f4c81;
        outline-offset: 1px;
        border-color: #0f4c81;
    }

    .lsm-settings .lsm-field.is-invalid .lsm-input {
        border-color: #7f1d1d;
        border-width: 2px;              /* not colour alone: 1.4.1 */
    }

    .lsm-settings .lsm-field-error {
        margin: 4px 0 0;
        max-width: 44ch;
        font-weight: 600;
        color: #7f1d1d;                 /* 9.0:1 on white */
    }

    .lsm-settings .lsm-field-error::before {
        content: "\26A0\FE0E\00A0";     /* a shape as well as a colour */
    }

    .lsm-settings .lsm-actions {
        display: flex;
        flex-wrap: wrap;
        align-items: center;
        gap: 12px 16px;
    }

    .lsm-settings .lsm-submit {
        /* Jahia's own .btn-primary is white on #39f — 2.9:1, which fails even AA.
           Same hue, darkened to 8.7:1, and the gradient plus text-shadow that
           Bootstrap 2 applies are flattened so the label keeps that ratio. */
        min-height: 44px;
        padding: 0 20px;
        font: inherit;
        font-weight: 600;
        color: #fff;
        background: #0f4c81;
        background-image: none;
        border: 1px solid #0f4c81;
        border-radius: 3px;
        text-shadow: none;
        cursor: pointer;
    }

    .lsm-settings .lsm-submit:hover {
        background: #0c3e6a;
        border-color: #0c3e6a;
    }

    .lsm-settings .lsm-submit:focus-visible {
        outline: 3px solid #1a1a1a;
        outline-offset: 2px;
    }

    .lsm-settings .lsm-submit[aria-busy="true"] {
        background: #4a5568;
        border-color: #4a5568;
        cursor: progress;
    }

    .lsm-settings .lsm-status {
        margin: 0;
        padding: 0;
        font-weight: 600;
    }

    /* Empty until something is announced, so the live region does not reserve a
       coloured band before it has anything to say. */
    .lsm-settings .lsm-status:empty {
        display: none;
    }

    .lsm-settings .lsm-status[data-state] {
        padding: 8px 12px;
        border-radius: 3px;
        border: 1px solid transparent;
    }

    .lsm-settings .lsm-status[data-state="saved"] {
        color: #14532d;                 /* 8.3:1 on its own background */
        background: #dcfce7;
        border-color: #14532d;
    }

    .lsm-settings .lsm-status[data-state="error"] {
        color: #7f1d1d;                 /* 8.2:1 on its own background */
        background: #fee2e2;
        border-color: #7f1d1d;
    }

    .lsm-settings .lsm-status[data-state]::before {
        content: "\2713\00A0";          /* success and failure differ in shape too */
    }

    .lsm-settings .lsm-status[data-state="error"]::before {
        content: "\26A0\FE0E\00A0";
    }

    @media (prefers-reduced-motion: no-preference) {
        .lsm-settings .lsm-status[data-state] {
            animation: lsm-status-in 160ms cubic-bezier(0.22, 1, 0.36, 1);
        }
    }

    @keyframes lsm-status-in {
        from { opacity: 0; }
        to { opacity: 1; }
    }
</style>

<%-- OWASP CSRFGuard dynamic script: injects the CSRF token into XHR requests --%>
<script src="${pageContext.request.contextPath}/CsrfServlet"></script>
<script>
    (function () {
        var form = document.getElementById('lsm-form');
        var status = document.getElementById('lsm-status');
        var submit = form.querySelector('.lsm-submit');
        var messages = {
            saved: status.getAttribute('data-msg-saved'),
            error: status.getAttribute('data-msg-error'),
            rejected: status.getAttribute('data-msg-rejected')
        };

        function announce(state, message) {
            // Clear first: re-announcing the same text into a live region that
            // already holds it produces no announcement at all.
            status.textContent = '';
            status.removeAttribute('data-state');
            window.setTimeout(function () {
                status.setAttribute('data-state', state);
                status.textContent = message;
            }, 50);
        }

        function clearFieldErrors() {
            var fields = form.querySelectorAll('.lsm-field');
            for (var i = 0; i < fields.length; i++) {
                var error = fields[i].querySelector('.lsm-field-error');
                var input = fields[i].querySelector('.lsm-input');
                fields[i].classList.remove('is-invalid');
                input.removeAttribute('aria-invalid');
                error.textContent = '';
                error.hidden = true;
            }
        }

        function markRejected(languages) {
            var first = null;
            for (var i = 0; i < languages.length; i++) {
                var field = form.querySelector('.lsm-field[data-lsm-lang="' + languages[i] + '"]');
                if (!field) {
                    continue;
                }
                var input = field.querySelector('.lsm-input');
                var error = field.querySelector('.lsm-field-error');
                field.classList.add('is-invalid');
                input.setAttribute('aria-invalid', 'true');
                error.textContent = messages.rejected;
                error.hidden = false;
                first = first || input;
            }
            // Move the caret to the first offending field: the message is useless if
            // the field is below the fold of a long language list.
            if (first) {
                first.focus();
            }
            return first !== null;
        }

        function setBusy(busy) {
            submit.setAttribute('aria-busy', busy ? 'true' : 'false');
            submit.disabled = busy;
            submit.textContent = busy
                ? submit.getAttribute('data-label-busy')
                : submit.getAttribute('data-label');
        }

        var actionUrl = window.location.pathname.replace(/\.[^/]+\.html$/, '') + '.saveLanguageUrls.do';

        form.addEventListener('submit', function (event) {
            event.preventDefault();
            if (submit.disabled) {
                return;
            }
            clearFieldErrors();
            setBusy(true);

            // XMLHttpRequest (not fetch): CSRFGuard's injected script only wraps XHR
            var xhr = new XMLHttpRequest();
            xhr.open('POST', actionUrl);
            xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
            xhr.onload = function () {
                setBusy(false);
                var ok = xhr.status >= 200 && xhr.status < 300;
                var rejected = [];
                try {
                    var body = JSON.parse(xhr.responseText);
                    rejected = (body && body.rejected) || [];
                } catch (e) {
                    rejected = [];
                }
                if (ok && rejected.length > 0) {
                    // Partly saved. The valid entries were stored; the refused ones kept
                    // whatever was already there, so this is "unchanged", not "lost".
                    markRejected(rejected);
                    announce('error', messages.rejected);
                } else if (ok) {
                    announce('saved', messages.saved);
                } else {
                    announce('error', messages.error);
                }
            };
            xhr.onerror = function () {
                setBusy(false);
                announce('error', messages.error);
            };
            xhr.send(new URLSearchParams(new FormData(form)).toString());
        });
    }());
</script>
