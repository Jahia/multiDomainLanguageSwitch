<%@ page language="java" contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%--@elvariable id="renderContext" type="org.jahia.services.render.RenderContext"--%>
<fmt:setBundle basename="resources.MultiDomainLanguageSwitch"/>

<template:addResources type="css" resources="admin-bootstrap.css"/>

<%-- Existing mapping, entries "lang=https://host" --%>
<c:set var="mappingEntries" value="${renderContext.site.hasProperty('lsm:languageUrls') ? renderContext.site.getProperty('lsm:languageUrls').values : null}"/>

<div class="lsm-settings" style="max-width: 640px; padding: 16px;">
    <h1><fmt:message key="lsm.siteSettings.title"/></h1>
    <p id="lsm-desc"><fmt:message key="lsm.siteSettings.description"/></p>

    <form id="lsm-form" aria-describedby="lsm-desc">
        <c:forEach items="${renderContext.site.languages}" var="lang">
            <c:set var="currentValue" value=""/>
            <c:forEach var="entry" items="${mappingEntries}">
                <c:if test="${fn:substringBefore(entry.string, '=') eq lang}">
                    <c:set var="currentValue" value="${fn:substringAfter(entry.string, '=')}"/>
                </c:if>
            </c:forEach>
            <div style="margin-bottom: 12px;">
                <label for="lsm-url-${fn:escapeXml(lang)}" style="display:block; font-weight:600;">
                    <fmt:message key="lsm.siteSettings.field.label"/> <strong>${fn:escapeXml(lang)}</strong>
                </label>
                <input type="url"
                       id="lsm-url-${fn:escapeXml(lang)}"
                       name="url-${fn:escapeXml(lang)}"
                       value="${fn:escapeXml(currentValue)}"
                       placeholder="https://www.example.${fn:escapeXml(lang)}"
                       pattern="https?://.*"
                       aria-describedby="lsm-help"
                       style="width:100%; max-width:480px;"/>
            </div>
        </c:forEach>
        <p id="lsm-help"><fmt:message key="lsm.siteSettings.help"/></p>
        <button type="submit" class="btn btn-primary"><fmt:message key="lsm.label.save"/></button>
    </form>

    <div id="lsm-toast" class="lsm-toast" role="status" aria-live="polite"
         data-msg-saved="<fmt:message key='lsm.siteSettings.saved'/>"
         data-msg-error="<fmt:message key='lsm.siteSettings.error'/>"></div>
</div>

<style>
    .lsm-toast {
        position: fixed;
        top: 16px;
        right: 16px;
        max-width: 320px;
        padding: 12px 20px;
        border-radius: 4px;
        color: #fff;
        background: #2e7d32;
        box-shadow: 0 2px 8px rgba(0, 0, 0, 0.3);
        z-index: 1000;
        opacity: 0;
        pointer-events: none;
        transition: opacity 0.2s ease;
    }
    .lsm-toast.lsm-toast-visible {
        opacity: 1;
    }
    .lsm-toast.lsm-toast-error {
        background: #c62828;
    }
</style>

<%-- OWASP CSRFGuard dynamic script: injects the CSRF token into XHR requests --%>
<script src="${pageContext.request.contextPath}/CsrfServlet"></script>
<script>
    (function () {
        var form = document.getElementById('lsm-form');
        var toast = document.getElementById('lsm-toast');
        var messages = {
            saved: toast.getAttribute('data-msg-saved'),
            error: toast.getAttribute('data-msg-error')
        };
        var hideTimer = null;

        function showToast(message, isError) {
            toast.textContent = message;
            toast.classList.toggle('lsm-toast-error', isError);
            toast.classList.add('lsm-toast-visible');
            if (hideTimer) {
                clearTimeout(hideTimer);
            }
            hideTimer = setTimeout(function () {
                toast.classList.remove('lsm-toast-visible');
            }, 4000);
        }

        var actionUrl = window.location.pathname.replace(/\.[^/]+\.html$/, '') + '.saveLanguageUrls.do';

        form.addEventListener('submit', function (event) {
            event.preventDefault();
            // XMLHttpRequest (not fetch): CSRFGuard's injected script only wraps XHR
            var xhr = new XMLHttpRequest();
            xhr.open('POST', actionUrl);
            xhr.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded');
            xhr.onload = function () {
                var ok = xhr.status >= 200 && xhr.status < 300;
                showToast(ok ? messages.saved : messages.error, !ok);
            };
            xhr.onerror = function () {
                showToast(messages.error, true);
            };
            xhr.send(new URLSearchParams(new FormData(form)).toString());
        });
    }());
</script>
