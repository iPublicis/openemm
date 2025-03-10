<%@ page language="java" import="java.util.List, com.agnitas.web.ComMailingComponentsAction, com.agnitas.web.ShowImageServlet, org.agnitas.beans.MailingComponent"
         contentType="text/html; charset=utf-8"  errorPage="/error.do" %>
<%@ taglib uri="https://emm.agnitas.de/jsp/jstl/tags" prefix="agn" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="http://displaytag.sf.net" prefix="display" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>
<%@ taglib prefix="emm" uri="https://emm.agnitas.de/jsp/jsp/common" %>

<%--@elvariable id="mailingComponentsForm" type="com.agnitas.web.forms.ComMailingComponentsForm"--%>

<c:set var="MOBILE_IMAGE_PREFIX" value="<%=ShowImageServlet.MOBILE_IMAGE_PREFIX%>"/>
<c:set var="ACTION_SAVE_COMPONENTS" value="<%=ComMailingComponentsAction.ACTION_SAVE_COMPONENTS%>"/>
<c:set var="ACTION_UPLOAD_ARCHIVE" value="<%=ComMailingComponentsAction.ACTION_UPLOAD_ARCHIVE%>"/>
<c:set var="ACTION_BULK_DOWNLOAD_COMPONENT" value="<%=ComMailingComponentsAction.ACTION_BULK_DOWNLOAD_COMPONENT%>"/>
<c:set var="ACTION_CONFIRM_DELETE" value="<%=ComMailingComponentsAction.ACTION_CONFIRM_DELETE%>"/>
<c:set var="ACTION_UPLOAD_SFTP" value="<%=ComMailingComponentsAction.ACTION_UPLOAD_SFTP%>"/>
<c:set var="ACTION_UPDATE_HOST_IMAGE" value="<%=ComMailingComponentsAction.ACTION_UPDATE_HOST_IMAGE%>"/>

<div class="row">
    <div class="tile">
        <div class="tile-header">
            <a href="#" class="headline" data-toggle-tile="#tile-imageUpload">
                <i class="tile-toggle icon icon-angle-up"></i>
                <bean:message key="mailing.Graphics_Component.imageUpload" />
            </a>
            <ul class="tile-header-nav">
            	<emm:ShowByPermission token="mailing.components.change">
                	<li class="active">
                    	<a href="#" data-toggle-tab="#tab-imageUploadSingleImage">
                            <bean:message key="mailing.files"/>
                    	</a>
                	</li>
                </emm:ShowByPermission>
                <%@include file="mailing-components-sftp-tab.jspf" %>
            </ul>
        </div>

        <div id="tile-imageUpload" class="tile-content">
            <emm:ShowByPermission token="mailing.components.change">
                <agn:agnForm action="/mcomponents"
                             enctype="multipart/form-data"
                             class="form-vertical"
                             data-form="upload">
                    <div id="tab-imageUploadSingleImage">
                        <div class="tile-content-forms">
                            <jsp:include page="../upload-file.jsp">
                                <jsp:param name="requestParams" value="mailingID:${mailingComponentsForm.mailingID}, action:${ACTION_SAVE_COMPONENTS}"/>
                                <jsp:param name="fileName" value="newFile[]"/>
                                <jsp:param name="tableHeaderTemplate" value="upload-image-template-header"/>
                                <jsp:param name="tableRowTemplate" value="upload-image-template-add"/>
                            </jsp:include>
                        </div>
                    </div>
                </agn:agnForm>
            </emm:ShowByPermission>

			<%@include file="/WEB-INF/jsp/mailing/mailing-components-sftp-content.jspf" %>
        </div>
    </div>


    <agn:agnForm action="/mcomponents" enctype="multipart/form-data" class="form-vertical" data-form="search">
        <html:hidden property="mailingID"/>
        <html:hidden property="action"/>
        <!-- Tile BEGIN -->
        <div class="tile">

            <!-- Tile Header BEGIN -->
            <div class="tile-header">
                <h2 class="headline">
                    <i class="icon icon-image"></i>
                    <bean:message key="mailing.Graphics_Components"/>
                </h2>

                <ul class="tile-header-actions">
                    <li class="dropdown">
                        <a href="#" class="dropdown-toggle" data-toggle="dropdown">
                            <i class="icon icon-pencil"></i>
                            <span class="text"><bean:message key="bulkAction"/></span>
                            <i class="icon icon-caret-down"></i>
                        </a>

                        <ul class="dropdown-menu">
                            <li>
                                <a href="#" data-prevent-load data-form-set="action: ${ACTION_BULK_DOWNLOAD_COMPONENT}" data-form-submit-static>
                                    <bean:message key="mailing.Graphics_Component.bulk.download"/>
                                </a>
                            </li>
                        </ul>
                    </li>

                </ul>

                <!-- Tile Header Actions END -->
            </div>
            <!-- Tile Header END -->

            <!-- Tile Content BEGIN -->
            <div class="tile-content" data-form-content>
                <!-- Table BEGIN -->
                <div class="table-wrapper">
                    <display:table class="table table-bordered table-striped js-table" id="component"
                                   name="components" requestURI="/mcomponents.do"
                                   pagesize="${mailingComponentsForm.numberOfRows}">

                        <display:column titleKey="mailing.Graphics_Component" sortable="false" class="align-center">
                            <a href="<html:rewrite page="/sc?compID=${component.id}"/>" data-modal="modal-preview-image"
                               data-modal-set="src: <html:rewrite page="/sc?compID=${component.id}" />, title: ${component.description}" class="inline-block">
                                <img data-display-dimensions="scope: tr" src="<html:rewrite page="/sc?compID=${component.id}" />" alt="${component.description}" border="1" style="max-width: 100px; max-height: 150px; width: auto; height: auto; display: block;" />
                            </a>
                        </display:column>

                        <display:column titleKey="settings.FileName" sortable="true" sortProperty="componentName" headerClass="js-table-sort">
                            <span class="multiline-auto">${component.componentName}</span>
                        </display:column>

                        <display:column titleKey="Description" sortable="true" sortProperty="description" headerClass="js-table-sort">
                            <span class="multiline-auto">${component.description}</span>
                        </display:column>

                        <display:column titleKey="htmled.link" sortable="false">
                            <c:set var="isShownAlready" value="false"/>
                            <logic:iterate id="url" name="componentLinks" scope="request">
                                <c:if test="${component.urlID eq url.id and url.fullUrl ne '' and isShownAlready ne true}">
                                    <a class="btn btn-regular" target="_blank" href="${url.fullUrl}" data-tooltip="${url.fullUrl}">
                                        <i class="icon icon-share-square-o"></i>
                                    </a>
                                    <c:set var="isShownAlready" value="true"/>
                                </c:if>
                            </logic:iterate>
                        </display:column>

                        <display:column titleKey="mailing.Graphics_Component.AddDate" sortable="true" sortProperty="timestamp" headerClass="js-table-sort">
                            <i class="icon icon-calendar"></i>
                            <fmt:formatDate value="${component.timestamp}" pattern="${adminDateFormat}" timeZone="${adminTimeZone}" />
                            &nbsp;
                            <i class="icon icon-clock-o"></i>
                            <fmt:formatDate value="${component.timestamp}" pattern="${adminTimeFormat}" timeZone="${adminTimeZone}" />
                        </display:column>

                        <display:column titleKey="default.Type" sortable="false">
                            <c:if test="${not fn:startsWith(component.componentName, MOBILE_IMAGE_PREFIX)}">
                                <bean:message key="predelivery.desktop" />
                            </c:if>
                            <c:if test="${fn:startsWith(component.componentName, MOBILE_IMAGE_PREFIX)}">
                                <bean:message key="Mobile" />
                            </c:if>
                        </display:column>

                        <display:column titleKey="mailing.Graphics_Component.Dimensions" sortable="false">
                            <i class="icon icon-expand"></i>
                            <span data-dimensions></span>
                        </display:column>

                        <display:column titleKey="default.Size" sortable="false">
                            <c:if test="${mailingComponentsForm.fileSizes[component.id] ne null}">
                                ${mailingComponentsForm.fileSizes[component.id]} kB
                            </c:if>
                        </display:column>

                        <display:column titleKey="report.data.type" sortable="true" sortProperty="mimeType" headerClass="js-table-sort">
                            <c:if test="${component.mimeType ne null and fn:startsWith(component.mimeType, 'image')}">
                                <span class="badge">
                                    ${fn:toUpperCase(fn:substring(component.mimeType, 6, -1))}
                                </span>
                            </c:if>
                        </display:column>

                        <display:column class="table-actions" titleKey="action.Action" sortable="false">
                            <c:if test="${component.type == MAILING_COMPONENT_TYPE_HOSTED_IMAGE}">
                                <a href="#" data-modal="image-editor-modal"
                                   data-tooltip="<bean:message key="button.Edit"/>"
                                   data-modal-set="src: <html:rewrite page="/sc?compID=${component.id}" />, componentId: ${component.id}"
                                   class="btn btn-regular btn-secondary	">
                                    <i class="icon icon-pencil"></i>
                                </a>
                            </c:if>

                            <c:if test="${component.type == MAILING_COMPONENT_TYPE_HOSTED_IMAGE}">
                                <c:set var="pictureDeleteMessage" scope="page">
                                    <bean:message key="mailing.Graphics_Component.delete"/>
                                </c:set>
                                <agn:agnLink styleClass="btn btn-regular btn-alert js-row-delete"
                                           page="/mcomponents.do?action=${ACTION_CONFIRM_DELETE}&componentId=${component.id}&mailingID=${mailingComponentsForm.mailingID}" data-tooltip="${pictureDeleteMessage}">
                                    <i class="icon icon-trash-o"></i>
                                </agn:agnLink>
                            </c:if>

                            <c:if test="${component.type == MAILING_COMPONENT_TYPE_IMAGE}">
                                <button type="button" class="btn btn-regular btn-info" data-tooltip="<bean:message key='mailing.Graphics_Component.Update' />" data-form-set="update${component.id}: update" data-form-submit="${ACTION_SAVE_COMPONENTS}">
                                    <i class="icon icon-refresh"></i>
                                </button>
                            </c:if>

                            <c:if test="${component.type == MAILING_COMPONENT_TYPE_HOSTED_IMAGE}">
                                <a href="<html:rewrite page="/dc?compID=${component.id}"/>"
                                   data-tooltip="<bean:message key='button.Download'/>" data-prevent-load class="btn btn-regular btn-info">
                                    <i class="icon icon-cloud-download"></i>
                                </a>
                            </c:if>

                        </display:column>
                    </display:table>
                </div>
            </div>
        </div>
    </agn:agnForm>
</div>

<script id="image-editor-modal" type="text/x-mustache-template">
    <div class="modal modal-adaptive">
       <div class="modal-dialog">
           <div class="modal-content" data-controller="image-editor">
               <div class="modal-header">
                   <button type="button" class="close-icon close" data-dismiss="modal">
                       <i aria-hidden="true" class="icon icon-times-circle"></i>
                   </button>
                   <h4 class="modal-title"><bean:message key="grid.mediapool.images.edit"/></h4>
               </div>
               <agn:agnForm action="/mcomponents" data-form="static" id="imgEditorForm" styleId="l-editor-form">
                   <div class="modal-body">
                        <html:hidden property="mailingID"/>
                        <html:hidden property="action" value="${ACTION_UPDATE_HOST_IMAGE}"/>
                        <html:hidden property="imageFile" styleId="editorResult"/>

                        <input class="hidden" name="componentId" value="{{= componentId }}"/>
                        <div id="l-img-editor" data-initializer="img-editor-init">
                            <ul id="l-img-editor-tools">
                                <li><label><bean:message key="default.image.width"/></label> </li>
                                <li><input id="l-editor-img-width" value="" class="form-control" data-action="changeSize" onkeypress="return event.charCode >= 48 && event.charCode <= 57" onfocus="$(this).select()"></li>
                                <li><label><bean:message key="grid.mediapool.image.sizes.height"/></label></li>
                                <li><input id="l-editor-img-height" value="" class="form-control" data-action="changeSize" onkeypress="return event.charCode >= 48 && event.charCode <= 57" onfocus="$(this).select()"></li>

                                <li><label><bean:message key="image.editor.inpercent"/></label></li>
                                <li><input id="l-editor-img-percent" value="" class="form-control" data-action="pcChangeSize" onkeypress="return event.charCode >= 48 && event.charCode <= 57" onfocus="$(this).select()"></li>
                                <li><div>
                                        <button type="button" class="btn" id="editor-lock-btn" data-tooltip="<bean:message key="image.editor.keep.proportions"/>" data-action="blockSizes">
                                            <i class="icon icon-lock"></i></button>
                                        <button type="button" class="btn" data-tooltip="<bean:message key="image.editor.rotate"/>" data-action="rotateImage">
                                            <i class="icon icon-rotate-right"></i></button>
                                        <button type="button" id="editor-crop-btn" class="btn" data-tooltip="<bean:message key="image.editor.crop"/>" data-action="cropImage">
                                            <i class="icon icon-crop"></i></button>
                                    </div>
                                </li>
                            </ul>
                                <div id="canvas-editor-area">
                                    <canvas id="editor-canvas"></canvas>
                                    <img class="hidden" id="editor-img" src="{{= src }}"/>
                                </div>
                        </div>
                   </div>
                   <div class="modal-footer">
                       <button type="button" class="btn btn-regular" data-action="saveCrop"><i class="icon icon-crop"></i><span class="text"><bean:message key="image.editor.crop"/></span></button>
                       <button type="button" class="btn btn-regular" data-dismiss="modal"><i class="icon icon-times"></i><span class="text"><bean:message key="button.Cancel"/></span></button>
                       <button type="button" class="btn btn-primary btn-regular" data-form-submit-event><i class="icon icon-save"></i><span class="text"><bean:message key="button.Save"/></span></button>
                   </div>
               </agn:agnForm>
           </div>
       </div>
    </div>
</script>

<script id="modal-preview-image" type="text/x-mustache-template">
    <div class="modal modal-adaptive">
        <div class="modal-dialog">
            <div class="modal-content">
                <div class="modal-header">
                    <button type="button" class="close-icon close" data-dismiss="modal">
                        <i aria-hidden="true" class="icon icon-times-circle"></i>
                    </button>
                    <h4 class="modal-title">{{= title }}</h4>
                </div>
                <div class="modal-body">
                    <img src="{{= src }}">
                </div>
            </div>
        </div>
    </div>
</script>

<script id="upload-file" type="text/x-mustache-template">
    <div class="col-sm-6">
        <div class="form-group">
            <label for="newFile{{= count }}" class="control-label"><bean:message key="button.file.choose"/></label>
            <input type="file" class="form-control" id="newFile{{= count }}" name="newFile[{{= count }}]">
        </div>
    </div>
    <div class="col-sm-6">
        <div class="form-group">
            <label class="control-label" for="mobileComponentBase{{= count }}">
                <bean:message key="mailing.Graphics_Component.sourceForMobile"/>
            </label>
            <select id="mobileComponentBase{{= count }}" name="mobileComponentBaseComponent[{{= count }}]" class="form-control js-select">
                <option value="" selected="selected"><bean:message key="default.none"/></option>
            <%
            	if (request.getAttribute("components") != null) {
	                for (MailingComponent component : (List<MailingComponent>) request.getAttribute("components")) {
	                    if ((component.getType() == MailingComponent.TYPE_IMAGE || component.getType() == MailingComponent.TYPE_HOSTED_IMAGE) && !component.getComponentName().startsWith(ShowImageServlet.MOBILE_IMAGE_PREFIX)) {
	            %>
                <option value="<%= component.getComponentName() %>"><%= component.getComponentName() %></option>
            <%
	                    }
	                }
                }
            %>
            </select>
        </div>
    </div>

    <div class="col-sm-12">
        <div class="form-group">
            <label class="control-label">
                <label for="link{{= count }}"><bean:message key="ComponentLink"/></label>
                <button class="icon icon-help" data-help="help_${helplanguage}/mailing/picture_components/LinkTargetForPicture.xml"></button>
            </label>
            <input type="text" id="link{{= count }}" name="link[{{= count }}]" value="" class="form-control">
        </div>
    </div>

    <div class="col-sm-12">
        <div class="form-group">
            <label class="control-label" for="descriptionByIndex{{= count }}">
                <bean:message key="Description"/>
            </label>
            <input type="text" id="descriptionByIndex{{= count }}" name="descriptionByIndex[{{= count }}]" value="" class="form-control">
        </div>
    </div>
</script>

<script id="upload-image-template-header" type="text/x-mustache-template">
    <tr>
        <th><bean:message key="mailing.files"/></th>
        <th><bean:message key="Description"/></th>
        <th><bean:message key="ComponentLink"/></th>
        <th><bean:message key="mailing.Graphics_Component.sourceForMobile"/></th>
    </tr>
</script>

<script id="upload-image-template-add" type="text/x-mustache-template">
{{ var isArchive = filename && filename.toLowerCase().endsWith('.zip'); }}
<tr>
    <td {{= isArchive ? 'colspan="4"' : '' }}>
        {{ if (preview) { }}
            <img data-display-dimensions="scope: tr" src="{{= preview }}" style="max-width: 100px; max-height: 150px; width: auto; height: auto; display: block;" />
        {{ } else { }}
            {{- filename }}
        {{ } }}
    </td>

    {{ if (!isArchive) { }}
    <td>
        <input type="text" id="descriptionByIndex{{= count }}" name="descriptionByIndex[{{= count }}]" value="" class="form-control">
    </td>
    <td>
        <input type="text" id="link{{= count }}" name="link[{{= count }}]" value="" class="form-control">
    </td>
    <td>
        <select id="mobileComponentBase{{= count }}" name="mobileComponentBaseComponent[{{= count }}]" class="form-control js-select">
            <option value="" selected="selected"><bean:message key="default.none"/></option>
        <%
	        if (request.getAttribute("components") != null) {
	            for (MailingComponent component : (List<MailingComponent>) request.getAttribute("components")) {
	                if ((component.getType() == MailingComponent.TYPE_IMAGE || component.getType() == MailingComponent.TYPE_HOSTED_IMAGE) && !component.getComponentName().startsWith(ShowImageServlet.MOBILE_IMAGE_PREFIX)) {
	        %>
            <option value="<%= component.getComponentName() %>"><%= component.getComponentName() %></option>
        <%
	                }
	            }
            }
        %>
        </select>
    </td>
    {{ } }}
</tr>
</script>
