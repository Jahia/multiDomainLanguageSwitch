<%@ page language="java" contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="ui" uri="http://www.jahia.org/tags/uiComponentsLib" %>
<%@ taglib prefix="lsm" uri="http://www.jahia.org/tags/lsm" %>

<%--@elvariable id="currentNode" type="org.jahia.services.content.JCRNodeWrapper"--%>
<%--@elvariable id="renderContext" type="org.jahia.services.render.RenderContext"--%>

<fmt:setBundle basename="resources.MultiDomainLanguageSwitch"/>

<template:addResources type="css" resources="lsm.css"/>

<c:set var="mainResourceNode" value="${renderContext.mainResource.node}"/>

<c:catch var="errorLanguages">
    <ui:initLangBarAttributes activeLanguagesOnly="${renderContext.liveMode}"/>
    <c:set var="languageCodes" value="${requestScope.languageCodes}"/>
    <c:if test="${fn:length(languageCodes) > 1}">

        <%-- Languages flagged invalid on the current page --%>
        <c:set var="invalidLanguages" value=""/>
        <c:catch var="e">
            <c:if test="${! empty mainResourceNode.properties['j:invalidLanguages']}">
                <c:forEach items="${mainResourceNode.properties['j:invalidLanguages']}" var="invalidLanguage">
                    <c:set var="invalidLanguages" value="${invalidLanguages} ${invalidLanguage.string}"/>
                </c:forEach>
            </c:if>
        </c:catch>

        <nav class="lsm-language-menu" aria-label="<fmt:message key='lsm.menu.ariaLabel'/>">
            <ul>
                <c:forEach var="languageCode" items="${languageCodes}">
                    <c:if test="${! empty languageCode && ! fn:contains(invalidLanguages, languageCode)}">
                        <li><lsm:switchToLanguageUrlLink languageCode="${languageCode}"/></li>
                    </c:if>
                </c:forEach>
            </ul>
        </nav>
    </c:if>
</c:catch>