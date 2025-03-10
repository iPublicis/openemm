<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib prefix="tiles" uri="http://struts.apache.org/tags-tiles" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib prefix="emm" uri="https://emm.agnitas.de/jsp/jsp/common" %>

<%--@elvariable id="agnRefresh" type="java.lang.String"--%>
<%--@elvariable id="agnPluginId" type="java.lang.String"--%>
<%--@elvariable id="agnTitleKey" type="java.lang.String"--%>
<%--@elvariable id="agnHighlightKey" type="java.lang.String"--%>

<c:set var="originUri" value="${emm:originUri(pageContext.request)}" scope="page"/>
<c:if test="${not empty pageContext.request.queryString}">
    <c:set var="originUri" value="${originUri}?${pageContext.request.queryString}" scope="page"/>
</c:if>

<head data-origin-uri="${originUri}">
    <meta charset="UTF-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1">

    <c:if test="${not empty agnRefresh}">
        <meta http-equiv="refresh" content="${agnRefresh}">
        <meta http-equiv="Page-Exit" content="RevealTrans(Duration=1,Transition=1)">
    </c:if>

    <meta http-equiv="cache-control" content="no-cache">
    <meta http-equiv="pragma" content="no-cache">
    <meta http-equiv="expires" content="0">

    <tiles:insert attribute="head-extra-meta-tags"/>

    <c:set var="title" scope="page"><bean:message key="default.A_EMM"/></c:set>
    <c:set var="subtitle" value="" scope="page"/>

    <c:choose>
        <c:when test="${not empty agnPluginId}">
            <c:if test="${not empty agnTitleKey}">
                <c:set var="title"><emm:message key="${agnTitleKey}" plugin="${agnPluginId}"/></c:set>
            </c:if>
            <c:if test="${not empty agnHighlightKey}">
                <c:set var="subtitle"><emm:message key="${agnHighlightKey}" plugin="${agnPluginId}"/></c:set>
            </c:if>
        </c:when>
        <c:otherwise>
            <c:if test="${not empty agnTitleKey}">
                <c:set var="title"><bean:message key="${agnTitleKey}"/></c:set>
            </c:if>
            <c:if test="${not empty agnHighlightKey}">
                <c:set var="subtitle"><bean:message key="${agnHighlightKey}"/></c:set>
            </c:if>
        </c:otherwise>
    </c:choose>

    <c:choose>
        <c:when test="${not empty subtitle}">
            <title>${title} - ${subtitle}</title>
        </c:when>
        <c:otherwise>
            <title>${title}</title>
        </c:otherwise>
    </c:choose>

    <link rel="shortcut icon" href="<c:url value="/favicon.ico"/>">

    <tiles:insert page="/WEB-INF/jsp/assets.jsp"/>

    <%--here you should put extra css/js links needed for your page--%>
    <tiles:insert attribute="head-extra-links"/>
</head>
