<%@ page language="java"
         import="org.agnitas.beans.Recipient"
         contentType="text/html; charset=utf-8"  errorPage="/error.do" %>
<%@ taglib uri="http://struts.apache.org/tags-bean" prefix="bean" %>
<%@ taglib uri="http://struts.apache.org/tags-html" prefix="html" %>
<%@ taglib uri="http://struts.apache.org/tags-logic" prefix="logic" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="emm" uri="https://emm.agnitas.de/jsp/jsp/common" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>

<% pageContext.setAttribute("MAILTYPE_TEXT", Recipient.MAILTYPE_TEXT); %>
<% pageContext.setAttribute("MAILTYPE_HTML", Recipient.MAILTYPE_HTML); %>
<% pageContext.setAttribute("MAILTYPE_HTML_OFFLINE", Recipient.MAILTYPE_HTML_OFFLINE); %>

<%--@elvariable id="importProfileForm" type="org.agnitas.web.forms.ImportProfileForm"--%>
<%--@elvariable id="isCustomerIdImportNotAllowed" type="java.lang.Boolean"--%>

<html:hidden property="numberOfRowsChanged"/>

<div class="tile">
    <div class="tile-header">
        <a href="#" class="headline" data-toggle-tile="#recipient-import-process-settings">
            <i class="tile-toggle icon icon-angle-up"></i>
            <bean:message key="import.profile.process.settings"/>
        </a>
    </div>
    <div id="recipient-import-process-settings" class="tile-content tile-content-forms">
        <div class="form-group">
            <div class="col-sm-4">
                <label class="control-label">
                    <label for="import_mode_select"><bean:message key="settings.Mode"/></label>
                    <button class="icon icon-help" data-help="help_${helplanguage}/importwizard/step_2/Mode.xml" tabindex="-1" type="button"></button>
                </label>
            </div>
            <div class="col-sm-8">
                <html:select styleId="import_mode_select" styleClass="form-control" property="profile.importMode">
                    <c:forEach var="importMode" items="${importProfileForm.importModes}">
                        <html:option value="${importMode.intValue}">
                            <bean:message key="${importMode.messageKey}"/>
                        </html:option>
                    </c:forEach>
                </html:select>
            </div>
        </div>

        <div class="form-group">
            <div class="col-sm-4">
                <label class="control-label">
                    <label for="import_null_values"><bean:message key="import.null_value_handling"/></label>
                    <button class="icon icon-help" data-help="help_${helplanguage}/importwizard/step_2/NullValueHandling.xml" tabindex="-1" type="button"></button>
                </label>
            </div>
            <div class="col-sm-8">
                <html:select styleId="import_null_values" styleClass="form-control" property="profile.nullValuesAction">
                    <c:forEach var="nullValuesAction" items="${importProfileForm.nullValuesActions}">
                        <html:option value="${nullValuesAction.intValue}">
                            <bean:message key="${nullValuesAction.messageKey}"/>
                        </html:option>
                    </c:forEach>
                </html:select>
            </div>
        </div>

        <div class="form-group">
            <div class="col-sm-4">
                <label class="control-label">
                    <label for="import_key_column"><bean:message key="import.keycolumn"/></label>
                    <button class="icon icon-help" data-help="help_${helplanguage}/importwizard/step_2/KeyColumn.xml" tabindex="-1" type="button"></button>
                </label>
            </div>
            <div class="col-sm-8">
				<c:choose>
					<c:when test="${importProfileForm.profile.firstKeyColumn eq 'customer_id' and isCustomerIdImportNotAllowed}">
						<div class="list-group-item disabled">${importProfileForm.profile.firstKeyColumn}</div>
					</c:when>
					<c:otherwise>
		                <html:select styleId="import_key_column" styleClass="form-control" property="profile.firstKeyColumn" >
		                	<c:forEach var="availableImportProfileField" items="${importProfileForm.availableImportProfileFields}">
		                		<html:option value='${availableImportProfileField.column}'>
		                            ${availableImportProfileField.shortname}
		                        </html:option>
		                    </c:forEach>
		                </html:select>
					</c:otherwise>
	            </c:choose>
            </div>
        </div>


        <div class="form-group">
            <div class="col-sm-4">
                <label class="control-label">
                    <label for="import_doublecheking"><bean:message key="import.doublechecking"/></label>
                    <button class="icon icon-help" data-help="help_${helplanguage}/importwizard/step_2/Doublechecking.xml" tabindex="-1" type="button"></button>
                </label>
            </div>
            <div class="col-sm-8">
                <html:select styleId="import_doublecheking" styleClass="form-control" property="profile.checkForDuplicates">
                    <c:forEach var="checkForDuplicatesValue" items="${importProfileForm.checkForDuplicatesValues}">
                        <html:option value="${checkForDuplicatesValue.intValue}">
                            <bean:message key="${checkForDuplicatesValue.messageKey}"/>
                        </html:option>
                    </c:forEach>
                </html:select>
            </div>
        </div>

        <div class="form-group">
            <div class="col-sm-4">
                <label class="control-label">
                    <label for="import_mailingtype"><bean:message key="recipient.mailingtype"/></label>
                    <button class="icon icon-help" data-help="help_${helplanguage}/importwizard/step_2/MailType.xml" tabindex="-1" type="button"></button>
                </label>
            </div>
            <div class="col-sm-8">
                <html:select styleId="import_mailingtype" styleClass="form-control" property="profile.defaultMailType">
                    <html:option value="${MAILTYPE_TEXT}">
                        <bean:message key="recipient.mailingtype.text"/>
                    </html:option>
                    <html:option value="${MAILTYPE_HTML}">
                        <bean:message key="HTML"/>
                    </html:option>
                    <html:option value="${MAILTYPE_HTML_OFFLINE}">
                        <bean:message key="recipient.mailingtype.htmloffline"/>
                    </html:option>
                </html:select>
            </div>
        </div>

        <div class="form-group">
            <div class="col-sm-4">
                <label class="control-label">
                    <label for="import_processaction"><bean:message key="import.processPreImportAction"/></label>
                    <button class="icon icon-help" data-help="help_${helplanguage}/importwizard/step_2/ProcessImportAction.xml" tabindex="-1" type="button"></button>
                </label>
            </div>
            <div class="col-sm-8">
				<emm:ShowByPermission token="import.preprocessing">
	                <html:select styleId="import_processaction" styleClass="form-control" property="profile.importProcessActionID">
	                	<html:option value="0">
	                        <bean:message key="none"/>
	                    </html:option>

						<c:if test="${importProfileForm.importProcessActions.size() > 0}">
		                    <c:forEach var="importProcessAction" items="${importProfileForm.importProcessActions}">
		                        <html:option value="${importProcessAction.importactionID}">
		                            ${importProcessAction.name}
		                        </html:option>
		                    </c:forEach>
	        			</c:if>
	                </html:select>
				</emm:ShowByPermission>
				<emm:HideByPermission token="import.preprocessing">
	                <html:select styleId="import_processaction" styleClass="form-control" property="profile.importProcessActionID" disabled="true">
	                	<html:option value="0">
	                        <bean:message key="none"/>
	                    </html:option>
	                </html:select>
				</emm:HideByPermission>
            </div>
        </div>

        <div class="form-group <logic:messagesPresent property="mailForReport">has-alert has-feedback</logic:messagesPresent>">
            <div class="col-sm-4">
                <label class="control-label">
                    <label for="import_email"><bean:message key="import.profile.report.email"/></label>
                    <button class="icon icon-help" data-help="help_${helplanguage}/importwizard/step_2/ReportEmail.xml" tabindex="-1" type="button"></button>
                </label>
            </div>
            <div class="col-sm-8">
                <html:text styleId="import_email" styleClass="form-control" property="profile.mailForReport"/>
                <logic:messagesPresent property="mailForReport">
                    <html:messages id="msg" property="mailForReport" >
                        <span class="icon icon-state-alert form-control-feedback"></span>
                        <div class="form-control-feedback-message">${msg}</div>
                    </html:messages>
                </logic:messagesPresent>
            </div>
        </div>

        <div class="form-group <logic:messagesPresent property="mailForError">has-alert has-feedback</logic:messagesPresent>">
            <div class="col-sm-4">
                <label class="control-label">
                    <label for="import_email"><bean:message key="error.import.profile.email"/></label>
                    <button class="icon icon-help" data-help="help_${helplanguage}/importwizard/step_2/ReportEmail.xml" tabindex="-1" type="button"></button>
                </label>
            </div>
            <div class="col-sm-8">
                <html:text styleId="import_email" styleClass="form-control" property="profile.mailForError"/>
                <logic:messagesPresent property="mailForError">
                    <html:messages id="msg" property="mailForError" >
                        <span class="icon icon-state-alert form-control-feedback"></span>
                        <div class="form-control-feedback-message">${msg}</div>
                    </html:messages>
                </logic:messagesPresent>
            </div>
        </div>

        <div class="form-group">
            <div class="col-sm-4">
                <label class="control-label">
                    <label for="import_duplicates"><bean:message key="import.profile.updateAllDuplicates" /></label>
                    <button class="icon icon-help" data-help="help_${helplanguage}/importwizard/step_2/UpdateAllDuplicates.xml" tabindex="-1" type="button"></button>
                </label>
            </div>
            <div class="col-sm-8">
                <html:hidden property="__STRUTS_CHECKBOX_profile.updateAllDuplicates" value="false"/>
                <label data-form-change class="toggle">
                    <html:checkbox styleId="import_duplicates" property="profile.updateAllDuplicates" />
                    <div class="toggle-control"></div>
                </label>
            </div>
        </div>


		<%@include file="/WEB-INF/jsp/importwizard/profile/action_settings-extended_actions.jspf" %>

    </div>
</div>
