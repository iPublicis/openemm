/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.emm.core.workflow.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Deque;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.mail.internet.InternetAddress;

import com.agnitas.emm.core.mailing.service.MailgunOptions;
import org.agnitas.beans.AdminEntry;
import org.agnitas.beans.Campaign;
import org.agnitas.beans.CompaniesConstraints;
import org.agnitas.beans.TrackableLink;
import org.agnitas.beans.impl.MaildropDeleteException;
import org.agnitas.dao.UserStatus;
import org.agnitas.emm.core.autoexport.bean.AutoExport;
import org.agnitas.emm.core.autoexport.service.AutoExportService;
import org.agnitas.emm.core.autoimport.bean.AutoImport;
import org.agnitas.emm.core.autoimport.service.AutoImportService;
import org.agnitas.emm.core.mailing.beans.LightweightMailing;
import org.agnitas.emm.core.velocity.VelocityCheck;
import org.agnitas.util.AgnUtils;
import org.agnitas.util.DateUtilities;
import org.agnitas.util.EmmCalendar;
import org.agnitas.util.SafeString;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.transaction.annotation.Transactional;

import com.agnitas.beans.ComAdmin;
import com.agnitas.beans.ComCompany;
import com.agnitas.beans.ComMailing;
import com.agnitas.beans.ComProfileField;
import com.agnitas.beans.ComTarget;
import com.agnitas.beans.ComTrackableLink;
import com.agnitas.beans.MaildropEntry;
import com.agnitas.beans.MediatypeEmail;
import com.agnitas.beans.TargetLight;
import com.agnitas.dao.ComAdminDao;
import com.agnitas.dao.ComCampaignDao;
import com.agnitas.dao.ComMailingDao;
import com.agnitas.dao.ComProfileFieldDao;
import com.agnitas.dao.ComTargetDao;
import com.agnitas.dao.ComTrackableLinkDao;
import com.agnitas.dao.UserFormDao;
import com.agnitas.emm.core.birtreport.bean.ComLightweightBirtReport;
import com.agnitas.emm.core.birtreport.dao.ComBirtReportDao;
import com.agnitas.emm.core.maildrop.MaildropStatus;
import com.agnitas.emm.core.mailing.service.SendActionbasedMailingException;
import com.agnitas.emm.core.mailing.service.SendActionbasedMailingService;
import com.agnitas.emm.core.reminder.service.ComReminderService;
import com.agnitas.emm.core.target.TargetExpressionUtils;
import com.agnitas.emm.core.target.beans.RawTargetGroup;
import com.agnitas.emm.core.target.eql.EqlFacade;
import com.agnitas.emm.core.target.eql.emm.legacy.TargetRepresentationToEqlConversionException;
import com.agnitas.emm.core.target.service.ComTargetService;
import com.agnitas.emm.core.workflow.beans.ComWorkflowReaction;
import com.agnitas.emm.core.workflow.beans.Workflow;
import com.agnitas.emm.core.workflow.beans.Workflow.WorkflowStatus;
import com.agnitas.emm.core.workflow.beans.WorkflowArchive;
import com.agnitas.emm.core.workflow.beans.WorkflowDeadline;
import com.agnitas.emm.core.workflow.beans.WorkflowDecision;
import com.agnitas.emm.core.workflow.beans.WorkflowDependency;
import com.agnitas.emm.core.workflow.beans.WorkflowDependencyType;
import com.agnitas.emm.core.workflow.beans.WorkflowExport;
import com.agnitas.emm.core.workflow.beans.WorkflowFollowupMailing;
import com.agnitas.emm.core.workflow.beans.WorkflowIcon;
import com.agnitas.emm.core.workflow.beans.WorkflowIconType;
import com.agnitas.emm.core.workflow.beans.WorkflowImport;
import com.agnitas.emm.core.workflow.beans.WorkflowMailing;
import com.agnitas.emm.core.workflow.beans.WorkflowMailingAware;
import com.agnitas.emm.core.workflow.beans.WorkflowParameter;
import com.agnitas.emm.core.workflow.beans.WorkflowReactionStep;
import com.agnitas.emm.core.workflow.beans.WorkflowReactionStepDeclaration;
import com.agnitas.emm.core.workflow.beans.WorkflowReactionType;
import com.agnitas.emm.core.workflow.beans.WorkflowRecipient;
import com.agnitas.emm.core.workflow.beans.WorkflowReminder;
import com.agnitas.emm.core.workflow.beans.WorkflowReport;
import com.agnitas.emm.core.workflow.beans.WorkflowRule;
import com.agnitas.emm.core.workflow.beans.WorkflowStart;
import com.agnitas.emm.core.workflow.beans.WorkflowStart.WorkflowStartEventType;
import com.agnitas.emm.core.workflow.beans.WorkflowStartStop;
import com.agnitas.emm.core.workflow.beans.WorkflowStop;
import com.agnitas.emm.core.workflow.beans.WorkflowStop.WorkflowEndType;
import com.agnitas.emm.core.workflow.beans.impl.WorkflowActionBasedMailingImpl;
import com.agnitas.emm.core.workflow.beans.impl.WorkflowArchiveImpl;
import com.agnitas.emm.core.workflow.beans.impl.WorkflowDateBasedMailingImpl;
import com.agnitas.emm.core.workflow.beans.impl.WorkflowDeadlineImpl;
import com.agnitas.emm.core.workflow.beans.impl.WorkflowDecisionImpl;
import com.agnitas.emm.core.workflow.beans.impl.WorkflowExportImpl;
import com.agnitas.emm.core.workflow.beans.impl.WorkflowFollowupMailingImpl;
import com.agnitas.emm.core.workflow.beans.impl.WorkflowFormImpl;
import com.agnitas.emm.core.workflow.beans.impl.WorkflowImportImpl;
import com.agnitas.emm.core.workflow.beans.impl.WorkflowMailingImpl;
import com.agnitas.emm.core.workflow.beans.impl.WorkflowParameterImpl;
import com.agnitas.emm.core.workflow.beans.impl.WorkflowRecipientImpl;
import com.agnitas.emm.core.workflow.beans.impl.WorkflowReportImpl;
import com.agnitas.emm.core.workflow.beans.impl.WorkflowStartImpl;
import com.agnitas.emm.core.workflow.beans.impl.WorkflowStopImpl;
import com.agnitas.emm.core.workflow.dao.ComWorkflowDao;
import com.agnitas.emm.core.workflow.dao.ComWorkflowReactionDao;
import com.agnitas.emm.core.workflow.dao.ComWorkflowStartStopReminderDao;
import com.agnitas.emm.core.workflow.dao.ComWorkflowStartStopReminderDao.ReminderType;
import com.agnitas.emm.core.workflow.graph.WorkflowGraph;
import com.agnitas.emm.core.workflow.graph.WorkflowNode;
import com.agnitas.emm.core.workflow.service.ComWorkflowActivationService;
import com.agnitas.emm.core.workflow.service.ComWorkflowDataParser;
import com.agnitas.emm.core.workflow.service.ComWorkflowService;
import com.agnitas.emm.core.workflow.service.ComWorkflowValidationService;
import com.agnitas.emm.core.workflow.service.util.WorkflowUtils;
import com.agnitas.emm.core.workflow.service.util.WorkflowUtils.Deadline;
import com.agnitas.emm.core.workflow.service.util.WorkflowUtils.StartType;
import com.agnitas.mailing.autooptimization.beans.ComOptimization;
import com.agnitas.mailing.autooptimization.service.ComOptimizationCommonService;
import com.agnitas.mailing.autooptimization.service.ComOptimizationService;
import com.agnitas.reporting.birt.external.dao.ComCompanyDao;
import com.agnitas.service.ComColumnInfoService;
import com.agnitas.service.ComMailingSendService;
import com.agnitas.userform.bean.UserForm;

public class ComWorkflowServiceImpl implements ComWorkflowService {

    //how many hours we will wait
    private static final int DELAY_FOR_SENDING_MAILING = 12; //hours

    private static final transient Logger logger = Logger.getLogger(ComWorkflowServiceImpl.class);

	private ComColumnInfoService columnInfoService;
	private ComMailingSendService mailingSendService;
    private ComWorkflowValidationService workflowValidationService;
    private AutoImportService autoImportService;
    private AutoExportService autoExportService;

    private ComProfileFieldDao profileFieldDao;
	private ComWorkflowDao workflowDao;
	private ComMailingDao mailingDao;
	private ComTrackableLinkDao linkDao;
	private ComAdminDao adminDao;
    private ComBirtReportDao birtReportDao;
	private ComTargetDao targetDao;
    private ComTargetService targetService;
    private UserFormDao userFormDao;
    private ComCompanyDao birtCompanyDao;
	private ComWorkflowReactionDao reactionDao;
	private ComWorkflowStartStopReminderDao reminderDao;
    private ComOptimizationService optimizationService;
    private ComOptimizationCommonService optimizationCommonService;
    private SendActionbasedMailingService sendActionbasedMailingService;
    private ComReminderService reminderService;
    private ComWorkflowDataParser workflowDataParser;
    private ComCampaignDao campaignDao;
    private EqlFacade eqlFacade;


    @Override
    @Transactional
    public void saveWorkflow(ComAdmin admin, Workflow workflow, List<WorkflowIcon> icons) {
        TimeZone timezone = TimeZone.getTimeZone(admin.getAdminTimezone());

        // Make some changes to mailing entities that are not used in the workflow anymore.
        releaseObsoleteMailings(workflow.getWorkflowId(), admin.getCompanyID(), icons);
        // Assign senderAdminId to some icons (if applicable).
        setSenderAdminId(icons, admin.getAdminID());

        // Check mailing types and make sure they're compatible with start icon (change mailing types if required).
        setCompatibleMailingTypes(icons);

        // Serialize the workflow structure (schema).
        workflow.setWorkflowSchema(workflowDataParser.serializeWorkflowIcons(icons));
        workflow.setWorkflowIcons(new ArrayList<>(icons));

        // Calculate derived data based on workflow structure.
        calculateDerivedData(workflow, timezone);
        // Store workflow and its structure (schema).
        saveWorkflow(workflow);

        // Generate and store (if required) workflow dependencies list.
        saveDependencies(admin.getCompanyID(), workflow.getWorkflowId(), icons);

        // Make some changes to external entities (mailings, auto-imports, auto-exports, etc) used in the workflow
        // (apply some changes defined by the workflow structure).
        updateEntities(workflow, admin);
    }

    /**
     * Generate and store workflow dependencies list — must be easily available to other EMM components without parsing
     * workflow schema. Managed and referenced mailings, archives, user forms, target groups, etc.
     *
     * @param companyId an admin who saves a workflow.
     * @param workflowId an identifier of a workflow to collect dependencies list for.
     * @param icons icons of a workflow to collect dependencies from.
     */
    private void saveDependencies(@VelocityCheck int companyId, int workflowId, List<WorkflowIcon> icons) {
        Set<WorkflowDependency> dependencies = new HashSet<>();

        for (WorkflowIcon icon : icons) {
            dependencies.addAll(icon.getDependencies());
        }

        Set<Integer> mailingIds = new HashSet<>();

        for (WorkflowDependency dependency : dependencies) {
            if (WorkflowDependencyType.MAILING_DELIVERY == dependency.getType()) {
                mailingIds.add(dependency.getEntityId());
            }
        }

        // Remove redundant dependencies -> mailing delivery overlaps mailing reference.
        dependencies.removeIf(dependency -> {
            // Referenced mailing may or may not be used in a mailing icon (to be delivered).
            return WorkflowDependencyType.MAILING_REFERENCE == dependency.getType() && mailingIds.contains(dependency.getEntityId());
        });

        workflowDao.setDependencies(companyId, workflowId, dependencies, false);
    }

    /**
     * Release mailing entities that are not part of the workflow anymore — reset some changes previously applied to those mailings.
     * In order to collect obsolete mailings the method compares given {@code icons} and structure stored in database.
     *
     * @param workflowId an identifier of the workflow being updated.
     * @param companyId an identifier of a company that owns the referenced workflow.
     * @param icons an object representation of the workflow icons to be stored.
     */
    private void releaseObsoleteMailings(int workflowId, int companyId, List<WorkflowIcon> icons) {
        List<WorkflowIcon> storedIcons = getIcons(workflowId, companyId);

        if (CollectionUtils.isNotEmpty(storedIcons)) {
            Set<Integer> oldMailingIds = collectMailingIds(storedIcons);
            Set<Integer> newMailingIds = collectMailingIds(icons);

            // Retain obsolete mailing ids (mailings that are not used anymore).
            oldMailingIds.removeAll(newMailingIds);

            for (int mailingId : oldMailingIds) {
                ComMailing mailing = getMailingForUpdate(mailingId, companyId);

                if (mailing != null) {
                    // Some hidden WM-driven target groups (list splits) could be assigned to a mailing.
                    // So we should reset related properties:
                    mailing.setSplitID(ComMailing.NONE_SPLIT_ID);
                    mailing.setTargetExpression(null);
                    mailingDao.saveMailing(mailing, false);
                }
            }
        }
    }

    /**
     * Collect distinct set of mailings managed by given {@code icons}.
     *
     * @param icons a list of icons representing the workflow structure.
     * @return a set of mailing identifiers.
     */
    private Set<Integer> collectMailingIds(List<WorkflowIcon> icons) {
        Set<Integer> ids = new HashSet<>();

        for (WorkflowIcon icon : icons) {
            switch (icon.getType()) {
                case WorkflowIconType.Constants.MAILING_ID:
                case WorkflowIconType.Constants.FOLLOWUP_MAILING_ID:
                case WorkflowIconType.Constants.ACTION_BASED_MAILING_ID:
                case WorkflowIconType.Constants.DATE_BASED_MAILING_ID:
                    int mailingId = WorkflowUtils.getMailingId(icon);
                    if (mailingId > 0 && icon.isFilled()) {
                        ids.add(mailingId);
                    }
                    break;
            }
        }

        return ids;
    }

    /**
     * Assign senderAdminId value to start and stop icons (required to provide proper notification delivery).
     *
     * @param icons a list of icons representing the workflow structure.
     * @param adminId an identifier of an admin to be treated as notification sender.
     */
    private void setSenderAdminId(List<WorkflowIcon> icons, int adminId) {
        for (WorkflowIcon icon : icons) {
            switch (icon.getType()) {
                case WorkflowIconType.Constants.START_ID:
                case WorkflowIconType.Constants.STOP_ID:
                    ((WorkflowStartStop) icon).setSenderAdminId(adminId);
                    break;
            }
        }
    }

    private void setCompatibleMailingTypes(List<WorkflowIcon> icons) {
        StartType startType = getStartType(icons);

        // Start icon is missing or unfilled so it's nothing to do.
        if (startType == StartType.UNKNOWN) {
            return;
        }

        ListIterator<WorkflowIcon> iterator = icons.listIterator();

        while (iterator.hasNext()) {
            WorkflowIcon icon = iterator.next();

            switch (icon.getType()) {
                case WorkflowIconType.Constants.MAILING_ID:
                case WorkflowIconType.Constants.FOLLOWUP_MAILING_ID:
                    if (startType != StartType.REGULAR) {
                        iterator.set(createReplacementMailingIcon(startType, icon));
                    }
                    break;

                case WorkflowIconType.Constants.ACTION_BASED_MAILING_ID:
                    if (startType != StartType.REACTION) {
                        iterator.set(createReplacementMailingIcon(startType, icon));
                    }
                    break;

                case WorkflowIconType.Constants.DATE_BASED_MAILING_ID:
                    if (startType != StartType.RULE) {
                        iterator.set(createReplacementMailingIcon(startType, icon));
                    }
                    break;
                default:
                	break;
            }
        }
    }

    private StartType getStartType(List<WorkflowIcon> icons) {
        StartType startType = StartType.UNKNOWN;

        for (WorkflowIcon icon : icons) {
            if (icon.getType() == WorkflowIconType.START.getId() && icon.isFilled()) {
                StartType type = StartType.of((WorkflowStart) icon);

                // Skip invalid or unfilled start icons.
                if (type != StartType.UNKNOWN) {
                    // Multiple start types are not allowed in the same campaign.
                    if (startType != StartType.UNKNOWN) {
                        return StartType.UNKNOWN;
                    }

                    startType = type;
                }
            }
        }

        return startType;
    }

    private WorkflowIcon createReplacementMailingIcon(StartType startType, WorkflowIcon sample) {
        WorkflowIcon icon = getEmptyIcon(createReplacementMailingIconType(startType));

        icon.setId(sample.getId());
        icon.setX(sample.getX());
        icon.setY(sample.getY());
        icon.setFilled(sample.isFilled());
        icon.setEditable(sample.isEditable());
        icon.setIconTitle(sample.getIconTitle());
        icon.setConnections(sample.getConnections());

        if (sample.isFilled()) {
            ((WorkflowMailingAware) icon).setMailingId(WorkflowUtils.getMailingId(sample));
        }

        return icon;
    }

    private WorkflowIconType createReplacementMailingIconType(StartType startType) {
        switch (startType) {
            case REGULAR:
                return WorkflowIconType.MAILING;

            case REACTION:
                return WorkflowIconType.ACTION_BASED_MAILING;

            case RULE:
                return WorkflowIconType.DATE_BASED_MAILING;

            default:
                throw new UnsupportedOperationException("Unexpected start type");
        }
    }

    @Override
	public void saveWorkflow(Workflow workflow) {
		if (workflow.getWorkflowId() > 0) {
		    // Create a new entity if updating failed (an entity seems to be deleted meanwhile).
            if (!workflowDao.updateWorkflow(workflow)) {
                workflowDao.createWorkflow(workflow);
            }
		} else {
            workflowDao.createWorkflow(workflow);
		}
    }

    /**
     * Calculate start/stop data — date range (if specified) and types. This data depends on workflow structure and
     * should be shown to user in the workflows overview list.
     *
     * @param workflow a workflow entity to update.
     * @param timezone a timezone to be used for local date processing.
     */
    private void calculateDerivedData(Workflow workflow, TimeZone timezone) {
        Date startDate = null;
        Date stopDate = null;
        WorkflowEndType stopType = null;
        WorkflowReactionType startReaction = null;
        WorkflowStartEventType startEvent = null;

        for (WorkflowIcon workflowIcon : workflow.getWorkflowIcons()) {
            if (!workflowIcon.isFilled()) {
                continue;
            }

            Date date;
            switch (workflowIcon.getType()) {
                case WorkflowIconType.Constants.START_ID:
                    WorkflowStart iconStart = (WorkflowStart) workflowIcon;
                    date = WorkflowUtils.getStartStopIconDate(iconStart, timezone);

                    // A start icon having the earliest date is the main one
                    if (startDate == null || (date != null && date.before(startDate))) {
                        startDate = date;
                        if (iconStart.getStartType() == WorkflowStart.WorkflowStartType.EVENT) {
                            startEvent = iconStart.getEvent();
                            if (iconStart.getEvent() == WorkflowStartEventType.EVENT_REACTION) {
                                startReaction = iconStart.getReaction();
                            }
                        }
                    }
                    break;

                case WorkflowIconType.Constants.STOP_ID:
                    WorkflowStop iconStop = (WorkflowStop) workflowIcon;
                    date = WorkflowUtils.getStartStopIconDate(iconStop, timezone);

                    // A stop icon having the latest date is the main one
                    if (stopDate == null || (date != null && date.after(stopDate))) {
                        stopType = iconStop.getEndType();
                        if (stopType == WorkflowEndType.DATE) {
                            stopDate = date;
                        }
                    }
                    break;
            }
        }

        workflow.setGeneralStartDate(startDate);
        workflow.setGeneralEndDate(stopDate);
        workflow.setEndType(stopType);
        workflow.setGeneralStartReaction(startReaction);
        workflow.setGeneralStartEvent(startEvent);
    }

    @Override
    public Workflow getWorkflow(int workflowId, int companyId) {
        Workflow workflow = workflowDao.getWorkflow(workflowId, companyId);

        if (workflow != null) {
            workflow.setWorkflowIcons(getIcons(workflow.getWorkflowSchema()));
        }

        return workflow;
    }

    @Override
    public List<WorkflowIcon> getIcons(int workflowId, @VelocityCheck int companyId) {
        return getIcons(workflowDao.getSchema(workflowId, companyId));
    }

    @Override
    public List<WorkflowIcon> getIconsForClone(ComAdmin admin, int workflowId, boolean isWithContent) {
        List<WorkflowIcon> icons = getIcons(workflowId, admin.getCompanyID());

        if (icons == null) {
            return null;
        }

        return cloneIcons(admin, icons, isWithContent);
    }

    private List<WorkflowIcon> getIcons(String schema) {
        if (StringUtils.isEmpty(schema)) {
            return new ArrayList<>();
        }

        return workflowDataParser.deSerializeWorkflowIconsList(schema);
    }

    @Override
    public boolean validateDependency(@VelocityCheck int companyId, int workflowId, WorkflowDependency dependency) {
        boolean strict = false;

        switch (dependency.getType()) {
            // Extend on demand.
            case AUTO_IMPORT:
            case AUTO_EXPORT:
                strict = true;
                break;
			default:
			    //nothing to do
        }

        return workflowDao.validateDependency(companyId, workflowId, dependency, strict);
    }

    @Override
    public void deleteWorkflow(int workflowId, int companyId) {
        reminderDao.deleteReminders(companyId, workflowId);
        reactionDao.deleteWorkflowReactions(workflowId, companyId);
        workflowDao.deleteTargetConditionDependencies(companyId, workflowId);
        workflowDao.deleteWorkflow(workflowId, companyId);
    }

    @Override
    public List<Workflow> getWorkflowsOverview(int companyId) {
        return workflowDao.getWorkflowsOverview(companyId);
    }

    @Override
	public List<LightweightMailing> getAllMailings(int companyId) {
		return mailingDao.getMailingsDateSorted(companyId);
	}

    @Override
    public List<LightweightMailing> getAllMailingsSorted(int companyId, String sortFiled, String sortDirection) {
        return mailingDao.getAllMailingsSorted(companyId, sortFiled, sortDirection);
    }

    @Override
	public List<Map<String, Object>> getAllMailings(int companyId, List<Integer> mailingTypes, String status,
                                                    String mailingStatus, boolean takeMailsForPeriod, String sort,
                                                    String order
    ) {
        if(StringUtils.equals(status, "all")){
            status = null;
        }
		return mailingDao.getMailingsNamesByStatus(companyId, mailingTypes, status, mailingStatus, takeMailsForPeriod, sort, order);
	}

    @Override
    public List<Map<String, Object>> getMailings(@VelocityCheck int companyId, String commaSeparatedMailingIds) {
        return mailingDao.getMailings(companyId, commaSeparatedMailingIds);
    }

    @Override
	public Map<Integer, String> getMailingLinks(int mailingId, int companyId) {
		List<ComTrackableLink> trackableLinks = linkDao.getTrackableLinks(companyId, mailingId);

        //productive links (A-Z), SWYN links (A-Z), administrative links (A-Z)"
        Comparator<ComTrackableLink> byAdministrative = (l1, l2) -> Boolean.compare(l1.isAdminLink(), l2.isAdminLink());
        Comparator<ComTrackableLink> bySWYN = Comparator.comparing(this::isLinkSWYN);
        Comparator<ComTrackableLink> byUrl = Comparator.comparing(TrackableLink::getFullUrl);
        trackableLinks.sort(byAdministrative.thenComparing(bySWYN).thenComparing(byUrl));

		Map<Integer, String> resultMap = new LinkedHashMap<>();
		for (ComTrackableLink trackableLink : trackableLinks) {
			resultMap.put(trackableLink.getId(), trackableLink.getFullUrl());
		}
		return resultMap;
	}

    private Boolean isLinkSWYN(ComTrackableLink link) {
        return StringUtils.startsWith(link.getShortname(), "SWYN");
    }

	@Override
	public Map<Integer, String> getAllReports(int companyId) {
		HashMap<Integer, String> reports = new LinkedHashMap<>();
        List<ComLightweightBirtReport> birtReports = birtReportDao.getLightweightBirtReportList(companyId);
        birtReports.forEach(report -> reports.put(report.getId(), report.getShortname()));
		return reports;
	}

    @Override
    public List<TargetLight> getAllTargets(int companyId) {
        return targetDao.getTargetLights(companyId, false);
    }

    @Override
	public List<ComProfileField> getHistorizedProfileFields(int companyId) throws Exception {
		return columnInfoService.getHistorizedComColumnInfos(companyId);
	}

    @Override
    public List<ComProfileField> getProfileFields(int companyId) throws Exception {
        return new ArrayList<>(profileFieldDao.getComProfileFieldsMap(companyId, false).values());
    }

    @Override
	public List<AdminEntry> getAdmins(int companyId) {
		return adminDao.getAllAdminsByCompanyIdOnly(companyId);
	}

	@Override
    public List<UserForm> getAllUserForms(int companyId) {
        return userFormDao.getUserForms(companyId);
    }

	@Override
    public LightweightMailing getMailing(int mailingId) {
        return mailingDao.getLightweightMailing(mailingId);
    }

    @Override
    public String getMailingName(int mailingId) {
        LightweightMailing mailing = mailingDao.getLightweightMailing(mailingId);

        if (mailing == null) {
            return null;
        }

        return mailing.getShortname();
    }

	@Override
    public ComMailing getMailing(int mailingId, int companyId) {
        return (ComMailing)mailingDao.getMailing(mailingId, companyId);
    }

	@Override
    public Map<String, Object> getMailingWithWorkStatus(int mailingId, int companyId) {
        return mailingDao.getMailingWithWorkStatus(mailingId, companyId);
    }

	@Override
    public String getTargetSplitName(int splitId) {
        return targetDao.getTargetSplitName(splitId);
    }

    private void updateEntities(Workflow workflow, ComAdmin admin) {
        List<WorkflowIcon> icons = workflow.getWorkflowIcons();

        // Make sure that a basic structure is ok so it can be processed to collect data and apply settings to managed entities.
        if (!workflowValidationService.validateBasicStructure(icons)) {
            // Don't even try to collect the data if schema is inconsistent (contains loops, detached icons, etc).
            return;
        }

        WorkflowGraph graph = new WorkflowGraph(icons);

        try (CachingEntitiesSupplier entitiesSupplier = new CachingEntitiesSupplier(admin.getCompanyID())) {
            for (WorkflowNode node : graph.getAllNodesByType(WorkflowIconType.START.getId())) {
                Map<WorkflowIcon, List<Chain>> chainMap = new HashMap<>();

                // Collect chains for icons that refer external WM-driven entities.
                collectChains(chainMap, node);

                // Assign collected data to referenced entities.
                chainMap.forEach((icon, chains) -> updateEntities(icon, chains, admin, entitiesSupplier));
            }
        } catch (Exception e) {
            logger.error("Error occurred: " + e.getMessage(), e);
        }
    }

    private void updateEntities(WorkflowIcon icon, List<Chain> chains, ComAdmin admin, EntitiesSupplier entitiesSupplier) {
        if (icon.isFilled() && icon.isEditable()) {
            switch (icon.getType()) {
                // Extend the following list on demand.
                case WorkflowIconType.Constants.MAILING_ID:
                case WorkflowIconType.Constants.FOLLOWUP_MAILING_ID:
                case WorkflowIconType.Constants.ACTION_BASED_MAILING_ID:
                case WorkflowIconType.Constants.DATE_BASED_MAILING_ID:
                    WorkflowMailingAware mailingIcon = (WorkflowMailingAware) icon;
                    int mailingId = mailingIcon.getMailingId();
                    if (mailingId > 0) {
                        ComMailing mailing = entitiesSupplier.getMailing(mailingId);
                        if (mailing != null) {
                            assignWorkflowDrivenSettings(mailing, chains, mailingIcon, admin);
                        }
                    }
                    break;

                case WorkflowIconType.Constants.IMPORT_ID:
                    int autoImportId = ((WorkflowImport) icon).getImportexportId();
                    if (autoImportId > 0) {
                        AutoImport autoImport = entitiesSupplier.getAutoImport(autoImportId);
                        if (autoImport != null && !autoImport.isActive()) {
                            assignWorkflowDrivenSettings(autoImport, chains, admin);
                        }
                    }
                    break;
            }
        }
    }

    private void assignWorkflowDrivenSettings(ComMailing mailing, List<Chain> chains, WorkflowMailingAware mailingIcon, ComAdmin admin) {
        TimeZone timezone = AgnUtils.getTimeZone(admin);
        Date date = getMinDate(chains, timezone);

        mailing.setMailingType(getMailingType(mailingIcon, mailing.getMailingType()));
        // Assign a planning date or null (if schema is incomplete).
        mailing.setPlanDate(DateUtilities.midnight(date, timezone));

        chains.stream()
            .map(Chain::getArchive)
            .filter(Objects::nonNull)
            .findFirst()
            .ifPresent(archive -> {
                mailing.setCampaignID(archive.getCampaignId());
                mailing.setArchived(archive.isArchived() ? 1 : 0);
            });

        chains.stream()
            .map(Chain::getMailingListId)
            .filter(id -> id > 0)
            .findFirst()
            .ifPresent(mailing::setMailinglistID);

        String targetExpression = chains.stream()
            .map(Chain::getTargetExpression)
            .filter(StringUtils::isNotBlank)
            .collect(Collectors.joining("|"));

        mailing.setSplitID(ComMailing.NONE_SPLIT_ID);
        mailing.setTargetExpression(targetExpression);
    }

    private void assignWorkflowDrivenSettings(AutoImport autoImport, List<Chain> chains, ComAdmin admin) {
        TimeZone timezone = AgnUtils.getTimeZone(admin);

        autoImport.setDeactivateByCampaign(true);
        autoImport.setAutoActivationDate(getMinDate(chains, timezone));

        List<Integer> mailingLists = Collections.emptyList();
        for (Chain chain : chains) {
            // Find first valid mailing list.
            int mailingListId = chain.getMailingListId();
            if (mailingListId > 0) {
                mailingLists = Collections.singletonList(mailingListId);
                break;
            }
        }

        autoImport.setMailinglists(mailingLists);
    }

    private Date getMinDate(List<Chain> chains, TimeZone timezone) {
        Date minDate = null;

        for (Chain chain : chains) {
            Date date = chain.getDate(timezone);

            if (minDate == null || date != null && date.before(minDate)) {
                minDate = date;
            }
        }

        return minDate;
    }

    private int getMailingType(WorkflowMailingAware mailingIcon, int defaultType) {
        switch (mailingIcon.getType()) {
            case WorkflowIconType.Constants.MAILING_ID:
                return ComMailing.TYPE_NORMAL;

            case WorkflowIconType.Constants.FOLLOWUP_MAILING_ID:
                return ComMailing.TYPE_FOLLOWUP;

            case WorkflowIconType.Constants.DATE_BASED_MAILING_ID:
                return ComMailing.TYPE_DATEBASED;

            case WorkflowIconType.Constants.ACTION_BASED_MAILING_ID:
                return ComMailing.TYPE_ACTIONBASED;
        }

        return defaultType;
    }

    private void collectChains(Map<WorkflowIcon, List<Chain>> chainMap, WorkflowNode node) {
        collectChains(chainMap, new Chain(), node);
    }

    private void collectChains(Map<WorkflowIcon, List<Chain>> chainMap, Chain chain, WorkflowNode node) {
        List<WorkflowNode> nextNodes = node.getNextNodes();

        while (true) {
            WorkflowIcon icon = node.getNodeIcon();

            switch (icon.getType()) {
                case WorkflowIconType.Constants.START_ID:
                    chain.append((WorkflowStart) icon);
                    break;

                case WorkflowIconType.Constants.RECIPIENT_ID:
                    chain.append((WorkflowRecipient) icon);
                    break;

                case WorkflowIconType.Constants.DEADLINE_ID:
                    chain.append((WorkflowDeadline) icon);
                    break;

                case WorkflowIconType.Constants.PARAMETER_ID:
                    chain.append((WorkflowParameter) icon);
                    break;

                case WorkflowIconType.Constants.ARCHIVE_ID:
                    chain.append((WorkflowArchive) icon);
                    break;

                // Extend the following list on demand.
                case WorkflowIconType.Constants.MAILING_ID:
                case WorkflowIconType.Constants.FOLLOWUP_MAILING_ID:
                case WorkflowIconType.Constants.ACTION_BASED_MAILING_ID:
                case WorkflowIconType.Constants.DATE_BASED_MAILING_ID:
                case WorkflowIconType.Constants.IMPORT_ID:
                case WorkflowIconType.Constants.EXPORT_ID:
                    chainMap.computeIfAbsent(icon, i -> new ArrayList<>()).add(new Chain(chain));
                    break;

                case WorkflowIconType.Constants.STOP_ID:
                    // It's the end.
                    return;
            }

            if (nextNodes.size() == 1) {
                node = nextNodes.get(0);
                nextNodes = node.getNextNodes();
            } else {
                break;
            }
        }

        for (WorkflowNode next : nextNodes) {
            collectChains(chainMap, new Chain(chain), next);
        }
    }

	@Override
    public void updateMailings(Workflow workflow, ComAdmin admin) {
        Map<Integer, ComMailing> mailingCacheMap = new HashMap<>();

        for (WorkflowIcon icon : workflow.getWorkflowIcons()) {
            if (WorkflowUtils.isMailingIcon(icon) && icon.isFilled()) {
                WorkflowMailingAware mailingIcon = (WorkflowMailingAware) icon;
                ComMailing mailing = mailingCacheMap.computeIfAbsent(mailingIcon.getMailingId(), id -> getMailingForUpdate(id, admin));

                if (mailing != null) {
                    updateMailing(admin, mailing, workflow.getWorkflowIcons(), mailingIcon);
                }
            }
        }

        // Store all the changes.
        for (ComMailing mailing : mailingCacheMap.values()) {
            if (mailing != null) {
                mailingDao.saveMailing(mailing, false);
            }
        }
    }

    /**
     * Assign some mailing's properties using data taken from corresponding workflow icon.
     *
     * @param admin an admin who makes the changes (or the one who created a workflow).
     * @param mailing a mailing entity to make changes to.
     * @param icons a list of icons of a workflow that is being saved.
     * @param icon a workflow mailing icon that the data should be taken from.
     */
    private void updateMailing(ComAdmin admin, ComMailing mailing, List<WorkflowIcon> icons, WorkflowMailingAware icon) {
        assignWorkflowDrivenSettings(admin, mailing, icons, icon);

        mailing.setMailingType(getMailingType(icon, mailing.getMailingType()));

        if (mailing.getSplitID() < 0) {
            mailing.setSplitID(0);
        }

        if (icon.getType() == WorkflowIconType.FOLLOWUP_MAILING.getId()) {
            WorkflowFollowupMailing followUpMailing = (WorkflowFollowupMailing) icon;
            String followUpMethod = WorkflowUtils.getFollowUpMethod(followUpMailing.getDecisionCriterion());

            if (followUpMethod != null) {
                MediatypeEmail paramEmail = mailing.getEmailParam();
                int followupBaseMailingId = followUpMailing.getBaseMailingId();
                if (mailing.getId() == followupBaseMailingId) {
                    throw new RuntimeException("Cannot create cyclic followup mailing structure");
                }
                paramEmail.setFollowupFor(Integer.toString(followupBaseMailingId));
                paramEmail.setFollowUpMethod(followUpMethod);
            }
        }
    }

    private ComMailing getMailingForUpdate(int mailingId, ComAdmin admin) {
        return getMailingForUpdate(mailingId, admin.getCompanyID());
    }

    private ComMailing getMailingForUpdate(int mailingId, @VelocityCheck int companyId) {
        if (mailingId > 0) {
            ComMailing mailing = getMailing(mailingId, companyId);
            if (mailing != null && mailing.getId() > 0) {
                return mailing;
            }
        }
        return null;
    }

    private void populateMailingByDataFromFoundChain(String timeZone, boolean withOwnNodes, List<WorkflowNode> foundNodes, ComMailing mailing) {
        TimeZone aZone = TimeZone.getTimeZone(timeZone);
        Calendar aCalendar = Calendar.getInstance();
        boolean haveStartingTime = false;
        boolean isOwnIconProcessed = false;

        for (WorkflowNode workflowNode : foundNodes) {
            WorkflowIcon workflowIcon = workflowNode.getNodeIcon();
            if (!workflowIcon.isFilled()) {
                continue;
            }

            switch (workflowIcon.getType()) {
                case WorkflowIconType.Constants.PARAMETER_ID:
                    WorkflowParameter parameterIcon = (WorkflowParameter) workflowIcon;
                    if (parameterIcon.getValue() > 0) {
                        mailing.setSplitID(ComMailing.YES_SPLIT_ID);
                    } else {
                        mailing.setSplitID(ComMailing.NONE_SPLIT_ID);
                    }
                    break;

                case WorkflowIconType.Constants.DEADLINE_ID:
                    WorkflowDeadline deadlineIcon = (WorkflowDeadline) workflowIcon;

                    switch (deadlineIcon.getDeadlineType()) {
                        case TYPE_FIXED_DEADLINE:
                            aCalendar.setTime(deadlineIcon.getDate());
                            aCalendar.add(Calendar.HOUR_OF_DAY, deadlineIcon.getHour());
                            aCalendar.add(Calendar.MINUTE, deadlineIcon.getMinute());
                            aCalendar.setTimeZone(aZone);
                            mailing.setPlanDate(aCalendar.getTime());
                            haveStartingTime = true;
                            break;

                        case TYPE_DELAY:
                            if (haveStartingTime) {
                                switch (deadlineIcon.getTimeUnit()) {
                                    case TIME_UNIT_MINUTE:
                                        aCalendar.add(Calendar.MINUTE, deadlineIcon.getDelayValue());
                                        break;
                                    case TIME_UNIT_HOUR:
                                        aCalendar.add(Calendar.HOUR_OF_DAY, deadlineIcon.getDelayValue());
                                        break;
                                    case TIME_UNIT_DAY:
                                        aCalendar.add(Calendar.DAY_OF_YEAR, deadlineIcon.getDelayValue());
                                        break;
									case TIME_UNIT_MONTH:
										break;
									case TIME_UNIT_WEEK:
										break;
									default:
										break;
                                }
                                if (deadlineIcon.isUseTime()) {
                                    aCalendar.add(Calendar.HOUR_OF_DAY, deadlineIcon.getHour());
                                    aCalendar.add(Calendar.MINUTE, deadlineIcon.getMinute());
                                }
                                aCalendar.setTimeZone(aZone);
                                mailing.setPlanDate(aCalendar.getTime());
                            }
                            break;
                    }
                    isOwnIconProcessed = true;
                    break;

                case WorkflowIconType.Constants.ARCHIVE_ID:
                    if (!isOwnIconProcessed || !withOwnNodes) {
                        WorkflowArchive archiveIcon = (WorkflowArchive) workflowIcon;
                        mailing.setCampaignID(archiveIcon.getCampaignId());
                        mailing.setArchived(archiveIcon.isArchived() ? 1 : 0);
                    }
                    break;

                case WorkflowIconType.Constants.RECIPIENT_ID:
                    if (!isOwnIconProcessed || !withOwnNodes) {
                        WorkflowRecipient recipientIcon = (WorkflowRecipient) workflowIcon;
                        mailing.setMailinglistID(recipientIcon.getMailinglistId());
                        mailing.setTargetExpression(TargetExpressionUtils.makeTargetExpression(recipientIcon.getTargets(), recipientIcon.getTargetsOption()));
                    }
                    break;

                case WorkflowIconType.Constants.START_ID:
                    WorkflowStart startIcon = (WorkflowStart) workflowIcon;
                    aCalendar.setTime(startIcon.getDate());
                    aCalendar.set(Calendar.HOUR_OF_DAY, startIcon.getHour());
                    aCalendar.set(Calendar.MINUTE, startIcon.getMinute());
                    aCalendar.setTimeZone(aZone);
                    mailing.setPlanDate(aCalendar.getTime());
                    haveStartingTime = true;
                    break;

                default:
                    break;
            }
        }
    }

    @Override
    public Workflow copyWorkflow(ComAdmin admin, int workflowId, boolean isWithContent) {
        Workflow workflow = getWorkflow(workflowId, admin.getCompanyID());

        // First clone a workflow entity itself, stored workflow schema is invalid at this point.
        workflow.setWorkflowId(0);
        workflow.setShortname(SafeString.getLocaleString("CopyOf", admin.getLocale()) + " " + workflow.getShortname());
        workflow.setStatus(Workflow.WorkflowStatus.STATUS_OPEN);

        // Migrate structure (icons and connections) to a clone - reset identifiers, clone referenced mailings (if required).
        List<WorkflowIcon> icons = cloneIcons(admin, workflow.getWorkflowIcons(), isWithContent);

        // Store workflow and its structure.
        saveWorkflow(admin, workflow, icons);

        return workflow;
    }

    private List<WorkflowIcon> cloneIcons(ComAdmin admin, List<WorkflowIcon> icons, boolean isWithContent) {
        List<WorkflowIcon> newIcons = new ArrayList<>(icons.size());

        if (isWithContent) {
            Map<Integer, Integer> newMailingsMap = cloneMailings(admin, collectMailingIds(icons));

            for (WorkflowIcon icon : icons) {
                if (icon.isFilled()) {
                    assignCloneMailingId(icon, newMailingsMap);
                }
                newIcons.add(icon);
            }
        } else {
            for (WorkflowIcon icon : icons) {
                WorkflowIcon newIcon = getEmptyIcon(icon);
                newIcon.setId(icon.getId());
                newIcon.setConnections(icon.getConnections());
                newIcons.add(newIcon);
            }
        }

        return newIcons;
    }

    private void assignCloneMailingId(WorkflowIcon icon, Map<Integer, Integer> map) {
        switch (icon.getType()) {
            case WorkflowIconType.Constants.MAILING_ID:
            case WorkflowIconType.Constants.ACTION_BASED_MAILING_ID:
            case WorkflowIconType.Constants.DATE_BASED_MAILING_ID: {
                WorkflowMailingAware mailing = (WorkflowMailingAware) icon;

                // Migrate the mailing (if any) referenced from an icon.
                if (!migrateId(mailing::setMailingId, mailing::getMailingId, map)) {
                    mailing.setFilled(false);
                }

                mailing.setIconTitle("");
                break;
            }

            case WorkflowIconType.Constants.FOLLOWUP_MAILING_ID: {
                WorkflowFollowupMailing mailing = (WorkflowFollowupMailing) icon;

                // Migrate the follow-up mailing (if any) referenced from an icon.
                if (!migrateId(mailing::setMailingId, mailing::getMailingId, map)) {
                    mailing.setFilled(false);
                }

                // Migrate the base mailing (if any) referenced from an icon.
                if (!migrateId(mailing::setBaseMailingId, mailing::getBaseMailingId, map)) {
                    mailing.setFilled(false);
                }

                mailing.setIconTitle("");
                break;
            }

            case WorkflowIconType.Constants.DECISION_ID: {
                WorkflowDecision decision = (WorkflowDecision) icon;

                // Migrate the mailing (if any) referenced from an icon.
                if (!migrateId(decision::setMailingId, decision::getMailingId, map)) {
                    decision.setFilled(false);
                }

                decision.setIconTitle("");
                break;
            }
        }
    }

    private boolean migrateId(Consumer<Integer> consumer, Supplier<Integer> supplier, Map<Integer, Integer> map) {
        int oldMailingId = supplier.get();
        int newMailingId = map.getOrDefault(oldMailingId, 0);

        consumer.accept(newMailingId);

        return newMailingId > 0 || oldMailingId <= 0;
    }

    private Map<Integer, Integer> cloneMailings(ComAdmin admin, Set<Integer> mailingIds) {
        Map<Integer, Integer> map = new HashMap<>();

        for (int mailingId : mailingIds) {
            map.put(mailingId, cloneMailing(admin, mailingId));
        }

        return map;
    }

    private int cloneMailing(ComAdmin admin, int mailingId) {
        String newMailingNamePrefix = SafeString.getLocaleString("mailing.CopyOf", admin.getLocale()) + " ";
        try {
            return mailingDao.copyMailing(mailingId, admin.getCompanyID(), newMailingNamePrefix);
        } catch (Exception e) {
            logger.error("Error while copying mailing for workflow", e);
            return 0;
        }
    }

    private WorkflowIcon getEmptyIcon(WorkflowIcon icon) {
        try {
            WorkflowIcon newIcon = icon.getClass().newInstance();
            newIcon.setX(icon.getX());
            newIcon.setY(icon.getY());
            return newIcon;
        } catch (InstantiationException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private WorkflowIcon getEmptyIcon(WorkflowIconType type) {
        switch (type) {
            case START: return new WorkflowStartImpl();
            case STOP: return new WorkflowStopImpl();
            case DECISION: return new WorkflowDecisionImpl();
            case DEADLINE: return new WorkflowDeadlineImpl();
            case PARAMETER: return new WorkflowParameterImpl();
            case REPORT: return new WorkflowReportImpl();
            case RECIPIENT: return new WorkflowRecipientImpl();
            case ARCHIVE: return new WorkflowArchiveImpl();
            case FORM: return new WorkflowFormImpl();
            case MAILING: return new WorkflowMailingImpl();
            case ACTION_BASED_MAILING: return new WorkflowActionBasedMailingImpl();
            case DATE_BASED_MAILING: return new WorkflowDateBasedMailingImpl();
            case FOLLOWUP_MAILING: return new WorkflowFollowupMailingImpl();
            case IMPORT: return new WorkflowImportImpl();
            case EXPORT: return new WorkflowExportImpl();

            default:
                // Add any new type when required.
                throw new UnsupportedOperationException("Not supported yet");
        }
    }

    @Override
    public boolean assignWorkflowDrivenSettings(ComAdmin admin, ComMailing mailing, int workflowId, int iconId) {
        if (mailing == null || workflowId <= 0 || iconId <= 0) {
            return false;
        }

        Workflow workflow = getWorkflow(workflowId, admin.getCompanyID());

        if (workflow == null) {
            return false;
        }

        WorkflowIcon mailingIcon = null;

        for (WorkflowIcon icon : workflow.getWorkflowIcons()) {
            if (iconId == icon.getId()) {
                mailingIcon = icon;
                break;
            }
        }

        if (mailingIcon == null) {
            return false;
        }

        return assignWorkflowDrivenSettings(admin, mailing, workflow.getWorkflowIcons(), mailingIcon);
    }

    private boolean assignWorkflowDrivenSettings(ComAdmin admin, ComMailing mailing, List<WorkflowIcon> icons, WorkflowIcon mailingIcon) {
        if (!WorkflowUtils.isMailingIcon(mailingIcon) || !workflowValidationService.noLoops(icons)) {
            return false;
        }

        WorkflowGraph graph = new WorkflowGraph();

        if (!graph.build(icons)) {
            return false;
        }

        List<WorkflowNode> foundNodes = new ArrayList<>();
        HashSet<Integer> terminateTypes = new HashSet<>();

        // found recipient
        WorkflowIcon prevRecipientIcon = graph.getPreviousIconByType(mailingIcon, WorkflowIconType.RECIPIENT.getId(), terminateTypes);
        if (prevRecipientIcon != null) {
            foundNodes.add(graph.getNodeByIcon(prevRecipientIcon));
        }

        // found archive
        WorkflowIcon prevArchiveIcon = graph.getPreviousIconByType(mailingIcon, WorkflowIconType.ARCHIVE.getId(), terminateTypes);
        if (prevArchiveIcon != null) {
            foundNodes.add(graph.getNodeByIcon(prevArchiveIcon));
        }

        // found start
        // ensure that start icon is added before a deadline icons (that could break a planned date calculation unless a deadline is fixed)
        WorkflowIcon prevStartIcon = graph.getPreviousIconByType(mailingIcon, WorkflowIconType.START.getId(), terminateTypes);
        if (prevStartIcon != null) {
            foundNodes.add(graph.getNodeByIcon(prevStartIcon));
        }

        // found deadline
        WorkflowIcon prevDeadlineIcon = graph.getPreviousIconByType(mailingIcon, WorkflowIconType.DEADLINE.getId(), terminateTypes);
        Deque<WorkflowIcon> prevDeadlineIcons = new LinkedList<>();
        while (prevDeadlineIcon != null) {
            prevDeadlineIcons.addFirst(prevDeadlineIcon);
            if (((WorkflowDeadline)prevDeadlineIcon).getDeadlineType() == WorkflowDeadline.WorkflowDeadlineType.TYPE_FIXED_DEADLINE) {
                // We have to sum delays unless a deadline is fixed
                break;
            }
            prevDeadlineIcon = graph.getPreviousIconByType(prevDeadlineIcon, WorkflowIconType.DEADLINE.getId(), terminateTypes);
        }

        for (WorkflowIcon icon : prevDeadlineIcons) {
            foundNodes.add(graph.getNodeByIcon(icon));
        }

        // found parameter
        WorkflowIcon parameterIcon = graph.getPreviousIconByType(mailingIcon, WorkflowIconType.PARAMETER.getId(), terminateTypes);
        if (parameterIcon != null) {
            foundNodes.add(graph.getNodeByIcon(parameterIcon));
        }

        populateMailingByDataFromFoundChain(admin.getAdminTimezone(), false, foundNodes, mailing);

        return true;
    }

	@Override
    public boolean hasCompanyDeepTrackingTables(int companyId) {
        return birtCompanyDao.hasDeepTrackingTables(companyId);
    }

    @Override
    public void bulkDelete(Set<Integer> workflowIds, @VelocityCheck int companyId) {
        for (int workflowId : workflowIds) {
            deleteWorkflow(workflowId, companyId);
        }
    }

    @Override
    public void bulkDeactivate(Set<Integer> workflowIds, @VelocityCheck int companyId) throws Exception {
        for (int workflowId : workflowIds) {
            Workflow workflow = getWorkflow(workflowId, companyId);

            switch (workflow.getStatus()) {
                case STATUS_ACTIVE:
                    changeWorkflowStatus(workflow, WorkflowStatus.STATUS_INACTIVE);
                    break;

                case STATUS_TESTING:
                    changeWorkflowStatus(workflow, WorkflowStatus.STATUS_OPEN);
                    break;

                default: break;
            }
        }
    }

	@Override
	public void changeWorkflowStatus(int workflowId, @VelocityCheck int companyId, WorkflowStatus newStatus) throws Exception {
        Workflow workflow = getWorkflow(workflowId, companyId);
        changeWorkflowStatus(workflow, newStatus);
	}

    private void changeWorkflowStatus(Workflow workflow, WorkflowStatus newStatus) throws Exception {
        int workflowId = workflow.getWorkflowId();
        int companyId = workflow.getCompanyId();

        List<WorkflowIcon> workflowIcons = workflow.getWorkflowIcons();

        WorkflowGraph workflowGraph = new WorkflowGraph();
        if (!workflowGraph.build(workflowIcons)) {
            return;
        }

        // Retrieve active (scheduled/working/finished) auto-optimizations
        final List<ComOptimization> optimizations = optimizationService.listWorkflowManaged(workflowId, companyId)
                .stream()
                .filter(optimization -> optimization.getStatus() != ComOptimization.STATUS_NOT_STARTED)
                .collect(Collectors.toList());

        switch (newStatus) {
            case STATUS_COMPLETE:
                if (workflow.getEndType() == WorkflowEndType.AUTOMATIC) {
                    for (WorkflowIcon icon : workflowIcons) {
                        final int type = icon.getType();

                        // if workflow with automatic end contains action/date based mailing, import or export we can not update status to completed
                        if (type == WorkflowIconType.ACTION_BASED_MAILING.getId()
                                || type == WorkflowIconType.DATE_BASED_MAILING.getId()
                                || type == WorkflowIconType.IMPORT.getId()
                                || type == WorkflowIconType.EXPORT.getId()
                                ) {
                            return;
                        }

                        List<Integer> mailingsToCheck = new ArrayList<>();

                        for (ComOptimization optimization : optimizations) {
                            if (optimization.getStatus() == ComOptimization.STATUS_FINISHED) {
                                mailingsToCheck.add(optimization.getFinalMailingId());
                            } else {
                                return;
                            }
                        }

                        // if either normal or followup mailing haven't sent yet we can not update status to completed
                        if (type == WorkflowIconType.MAILING.getId() || type == WorkflowIconType.FOLLOWUP_MAILING.getId()) {
                            int mailingId = ((WorkflowMailingAware) icon).getMailingId();

                            // AO-driven final mailings have no mailingId (icon's mailingId = 0)
                            if (mailingId != 0) {
                                mailingsToCheck.add(mailingId);
                            }
                        }

                        for (Integer mailingId : mailingsToCheck) {
                            Map<String, Object> mailingData = getMailingWithWorkStatus(mailingId, companyId);
                            Date sendDate = mailingDao.getLastSendDate(mailingId);

                            EmmCalendar sendCalendar = new EmmCalendar(TimeZone.getDefault());
                            if (sendDate != null) {
                                sendCalendar.setTime(sendDate);
                                sendCalendar.add(EmmCalendar.HOUR, DELAY_FOR_SENDING_MAILING);
                            }
                            // get current date and time
                            GregorianCalendar nowCal = new GregorianCalendar(TimeZone.getDefault());
                            if (!("mailing.status.sent".equals(mailingData.get("work_status"))
                                    || "mailing.status.norecipients".equals(mailingData.get("work_status"))
                                    || (sendDate != null && nowCal.after(sendCalendar)))) {
                                return;
                            }
                        }
                    }
                }
                break;

            case STATUS_TESTED:
                List<Integer> mailingTypes = Arrays.asList(
                        WorkflowIconType.MAILING.getId(),
                        WorkflowIconType.DATE_BASED_MAILING.getId(),
                        WorkflowIconType.ACTION_BASED_MAILING.getId(),
                        WorkflowIconType.FOLLOWUP_MAILING.getId()
                );

                List<WorkflowNode> mailingNodes = workflowGraph.getAllNodesByTypes(mailingTypes);
                Map<Integer, Boolean> sentMailings = new HashMap<>();

                // Build relations map connecting AO-test mailings and AO-final mailings
                final Map<Integer, Integer> aoTestToFinalMailingMap = new HashMap<>();
                for (ComOptimization optimization : optimizations) {
                    if (optimization.getStatus() == ComOptimization.STATUS_FINISHED) {
                        Integer finalMailingId = optimization.getFinalMailingId();
                        for (Integer testMailingId : optimization.getTestmailingIDs()) {
                        	aoTestToFinalMailingMap.put(testMailingId, finalMailingId);
                        }
                    }
                }

                Map<Integer, ComMailing> mailings = new HashMap<>();
                Map<Integer, WorkflowMailingAware> mailingIcons = new HashMap<>();
                for (WorkflowNode workflowNode : mailingNodes) {
                    WorkflowMailingAware icon = (WorkflowMailingAware) workflowNode.getNodeIcon();
                    int mailingId = icon.getMailingId();
                    mailingIcons.put(mailingId, icon);

                    // AO-driven final mailings have no mailingId (icon's mailingId = 0)
                    if (mailingId == 0) {
                        WorkflowMailing testMailing = getPreviousOptimizationTestMailing(workflowGraph, icon);
                        if (testMailing != null) {
                            Integer id = aoTestToFinalMailingMap.get(testMailing.getMailingId());
                            if (id != null) {
                                mailingId = id;
                            }
                        }
                    }

                    boolean sent = false;
                    if (mailingId != 0) {
                        ComMailing mailing = (ComMailing) mailingDao.getMailing(mailingId, companyId);
                        mailings.put(mailingId, mailing);
                        MaildropEntry maildropEntry = null;
                        Date maxSendDate = null;

                        for (MaildropEntry entry : mailing.getMaildropStatus()) {
                            if (entry.getStatus() == MaildropStatus.TEST.getCode()) {
                                if (maildropEntry == null || entry.getSendDate().after(maxSendDate)) {
                                    maildropEntry = entry;
                                    maxSendDate = entry.getSendDate();
                                }
                            }
                        }

                        if (maildropEntry != null && maildropEntry.getGenStatus() == MaildropEntry.GEN_FINISHED) {
                            sent = true;
                        } else if (maxSendDate != null) {
                            TimeZone timezone = TimeZone.getDefault();

                            Calendar sendAttemptsDeadline = Calendar.getInstance(timezone);
                            sendAttemptsDeadline.setTime(maxSendDate);
                            sendAttemptsDeadline.add(Calendar.HOUR_OF_DAY, DELAY_FOR_SENDING_MAILING);

                            Calendar calendarNow = Calendar.getInstance(timezone);
                            if (sendAttemptsDeadline.before(calendarNow)) {
                                sent = true;
                            }
                        }
                    } else {
                        logger.debug("Unable to retrieve a mailingId for the mailing icon #" + icon.getId());
                    }
                    sentMailings.put(mailingId, sent);
                }

                List<WorkflowNode> startNodes = workflowGraph.getAllNodesByType(WorkflowIconType.START.getId());
                // The campaign should have exactly one start icon
                if (startNodes.size() != 1) {
                    return;
                }
                if (!isCampaignTestFinished(workflowGraph, mailingTypes, mailingIcons, mailings, sentMailings, companyId)) {
                    return;
                }
                break;
            default:break;
        }

        if (isDeactivationOrCompletion(workflow.getStatus(), newStatus)) {
            boolean isTestRun = newStatus == WorkflowStatus.STATUS_TESTED;

            for (ComOptimization optimization : optimizations) {
                // Un-schedule not finished optimizations, delete finished ones
                try {
                    if (optimization.getStatus() == ComOptimization.STATUS_FINISHED) {
                        optimizationService.delete(optimization);
                    } else {
                        optimizationCommonService.unscheduleOptimization(optimization, isTestRun);
                    }
                } catch (MaildropDeleteException e) {
                    logger.error("Error occurred during optimization un-scheduling: " + optimization.getId(), e);
                }
            }

            for (WorkflowIcon icon : workflowIcons) {
                switch (icon.getType()) {
                    case WorkflowIconType.Constants.MAILING_ID:
                    case WorkflowIconType.Constants.FOLLOWUP_MAILING_ID:
                    case WorkflowIconType.Constants.ACTION_BASED_MAILING_ID:
                    case WorkflowIconType.Constants.DATE_BASED_MAILING_ID:
                        deactivateMailing((WorkflowMailingAware) icon, isTestRun, companyId);
                        break;

                    case WorkflowIconType.Constants.IMPORT_ID:
                        deactivateAutoImport((WorkflowImport) icon, companyId);
                        break;

                    case WorkflowIconType.Constants.EXPORT_ID:
                        deactivateAutoExport((WorkflowExport) icon, companyId);
                        break;
                }
            }

            // disable all triggers of workflow
            reactionDao.deactivateWorkflowReactions(workflowId, companyId);

            // When campaign ends make sure that all scheduled reminders are sent.
            if (newStatus == WorkflowStatus.STATUS_COMPLETE) {
                // Send scheduled reminders (if any) before actual status change.
                reminderService.send();
            }
    
            if (newStatus == WorkflowStatus.STATUS_FAILED) {
                newStatus = WorkflowStatus.STATUS_INACTIVE;
            }
    
            if (newStatus == WorkflowStatus.STATUS_TESTING_FAILED) {
                newStatus = WorkflowStatus.STATUS_OPEN ;
                
            }

        }

        // Create/schedule or remove reminders depending on status.
        updateReminders(companyId, workflowId, newStatus, workflowIcons);

        // set workflow to new state
        workflowDao.changeWorkflowStatus(workflowId, companyId, newStatus);
    }

    private boolean isDeactivationOrCompletion(WorkflowStatus oldStatus, WorkflowStatus newStatus) {
        if(WorkflowStatus.STATUS_FAILED == newStatus) {
            return true;
        }
        
        switch (oldStatus) {
            case STATUS_ACTIVE:
            case STATUS_TESTING:
                return oldStatus != newStatus;

            default:
                return false;
        }
    }

    /**
     * Generate and store workflow reminders (if any) configured in start and stop icons — once scheduled must be
     * triggered by {@link com.agnitas.emm.core.workflow.service.ComWorkflowReminderServiceJobWorker} when it's time.
     *
     * @param companyId an identifier of a company that a referenced workflow belongs to.
     * @param workflowId an identifier of a workflow to generate and store reminders for.
     * @param newStatus a workflow status.
     * @param icons icons of a workflow to generate reminders for.
     */
    private void updateReminders(@VelocityCheck int companyId, int workflowId, WorkflowStatus newStatus, List<WorkflowIcon> icons) {
        AdminTimezoneSupplier adminTimezoneSupplier = new AdminTimezoneCachingSupplier(companyId);
        List<WorkflowReminder> reminders;

        switch (newStatus) {
            case STATUS_ACTIVE:
                reminders = getReminders(icons, true, adminTimezoneSupplier);
                break;

            case STATUS_INACTIVE:
                reminders = getReminders(icons, false, adminTimezoneSupplier);
                break;

            default:
                // Remove existing reminders.
                reminders = Collections.emptyList();
                break;
        }

        reminderDao.setReminders(companyId, workflowId, reminders);
    }

    private List<WorkflowReminder> getReminders(List<WorkflowIcon> icons, boolean isWorkflowActive, AdminTimezoneSupplier adminTimezoneSupplier) {
        List<WorkflowReminder> reminders = new ArrayList<>();

        for (WorkflowIcon icon : icons) {
            if (WorkflowUtils.isStartStopIcon(icon) && icon.isFilled()) {
                WorkflowReminder reminder = getReminder((WorkflowStartStop) icon, isWorkflowActive, adminTimezoneSupplier);

                if (reminder != null) {
                    reminders.add(reminder);
                }
            }
        }

        return reminders;
    }

    private WorkflowReminder getReminder(WorkflowStartStop icon, boolean isWorkflowActive, AdminTimezoneSupplier adminTimezoneSupplier) {
        if (icon.isScheduleReminder()) {
            switch (icon.getType()) {
                case WorkflowIconType.Constants.START_ID:
                    if (isWorkflowActive) {
                        return getReminder(icon, ReminderType.START, adminTimezoneSupplier);
                    } else {
                        return getReminder(icon, ReminderType.MISSING_START, adminTimezoneSupplier);
                    }

                case WorkflowIconType.Constants.STOP_ID:
                    if (isWorkflowActive) {
                        return getReminder(icon, ReminderType.STOP, adminTimezoneSupplier);
                    } else {
                        return null;
                    }

                default:
                    return null;
            }
        }

        return null;
    }

    private WorkflowReminder getReminder(WorkflowStartStop icon, ReminderType reminderType, AdminTimezoneSupplier adminTimezoneSupplier) {
        try {
            Date date = getReminderDate(icon, reminderType, adminTimezoneSupplier);

            // Never generate MISSING_START reminders in the past.
            if (reminderType == ReminderType.MISSING_START && DateUtilities.isPast(date)) {
                return null;
            }

            return WorkflowReminder.builder()
                    .type(reminderType)
                    .sender(icon.getSenderAdminId())
                    // For MISSING_START reminder a messages is always generated automatically.
                    .message(reminderType == ReminderType.MISSING_START ? null : icon.getComment())
                    .recipients(recipientsBuilder -> setReminderRecipients(recipientsBuilder, icon))
                    .date(date)
                    .build();
        } catch (Exception e) {
            logger.error("Cannot create reminder: " + e.getMessage(), e);
        }

        return null;
    }

    private Date getReminderDate(WorkflowStartStop icon, ReminderType reminderType, AdminTimezoneSupplier adminTimezoneSupplier) {
        TimeZone timezone = adminTimezoneSupplier.getTimezone(icon.getSenderAdminId());

        if (reminderType == ReminderType.MISSING_START || !icon.isRemindSpecificDate()) {
            return WorkflowUtils.getStartStopIconDate(icon, timezone);
        } else {
            return WorkflowUtils.getReminderSpecificDate(icon, timezone);
        }
    }

    private void setReminderRecipients(WorkflowReminder.RecipientsBuilder recipientsBuilder, WorkflowStartStop startStop) {
        String recipients = startStop.getRecipients();

        if (StringUtils.isEmpty(recipients)) {
            recipientsBuilder.recipient(startStop.getRemindAdminId()).end();
        } else {
            try {
                for (InternetAddress address : AgnUtils.getEmailAddressesFromList(recipients)) {
                    recipientsBuilder.recipient(address.getAddress());
                }
            } catch (Exception e) {
                logger.error("Error occurred: " + e.getMessage(), e);
            }
            recipientsBuilder.end();
        }
    }

    private boolean isCampaignTestFinished(WorkflowGraph workflowGraph, List<Integer> mailingTypes, Map<Integer, WorkflowMailingAware> mailingIconsByMailingId, Map<Integer, ComMailing> mailings, Map<Integer, Boolean> sentMailings, int companyId) {
        Date now = new Date();
        Date startDate = getCampaignTestStartDate(workflowGraph, mailingTypes, mailings);

        if (startDate == null) {
            return false;
        }

        for (ComMailing mailing: mailings.values()) {
            Date maxPossibleDateOfMailing = getMaxPossibleDateForTestRun(workflowGraph.findChains(mailingIconsByMailingId.get(mailing.getId()), false), startDate);
            if (now.before(maxPossibleDateOfMailing)) {
                return false;
            }

            if (!sentMailings.getOrDefault(mailing.getId(), false)) {
                String targetSQL = targetService.getSQLFromTargetExpression(mailing, false);
                int customersForMailing = workflowDao.countCustomers(companyId, mailing.getMailinglistID(), targetSQL);
                Date maxWaitForSendDate = DateUtils.addMinutes(maxPossibleDateOfMailing, ComWorkflowActivationService.TESTING_MODE_DEADLINE_DURATION);

                if (customersForMailing > 0 && now.before(maxWaitForSendDate)) {
                    return false;
                }
            }
        }

        return true;
    }

    private Date getCampaignTestStartDate(WorkflowGraph workflowGraph, List<Integer> mailingTypes, Map<Integer, ComMailing> mailings) {
        WorkflowNode start = workflowGraph.getAllNodesByType(WorkflowIconType.START.getId()).get(0);
        WorkflowMailingAware mailingIcon = (WorkflowMailingAware) workflowGraph.getNextIconByType(start.getNodeIcon(), mailingTypes, Collections.emptySet(), false);
        ComMailing mailing = mailings.get(mailingIcon.getMailingId());

        if (mailing == null) {
            return null;
        }

        return mailing.getPlanDate();
    }

    private Date getMaxPossibleDateForTestRun(List<List<WorkflowNode>> chains, Date testStartDate) {
        Date result = new Date(testStartDate.getTime());
        for (List<WorkflowNode> chain: chains) {
            Date chainMaxDate = getChainDateForTestRun(chain, testStartDate);
            if (chainMaxDate.after(result)) {
                result = chainMaxDate;
            }
        }
        return result;
    }

    private Date getChainDateForTestRun(List<WorkflowNode> chain, Date testStartDate) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(testStartDate);
        if (CollectionUtils.isNotEmpty(chain)) {
            chain = new ArrayList<>(chain);
            Collections.reverse(chain);
            WorkflowIcon icon = chain.get(0).getNodeIcon();
            if (icon.getType() == WorkflowIconType.START.getId()) {
                for (WorkflowNode node: chain) {
                    if (node.getNodeIcon().getType() == WorkflowIconType.DEADLINE.getId()) {
                        calendar.add(Calendar.MINUTE, ComWorkflowActivationService.TESTING_MODE_DEADLINE_DURATION);
                    }
                }
            }
        }
        return calendar.getTime();
    }

    private WorkflowMailing getPreviousOptimizationTestMailing(WorkflowGraph graph, WorkflowIcon currentIcon) {
        final int decisionTypeId = WorkflowIconType.DECISION.getId();

        WorkflowDecision decision = (WorkflowDecision) graph.getPreviousIconByType(currentIcon, decisionTypeId, Collections.singleton(decisionTypeId));
        if (decision == null) {
            return null;
        }
        if (decision.getDecisionType() != WorkflowDecision.WorkflowDecisionType.TYPE_AUTO_OPTIMIZATION) {
            return null;
        }
        return (WorkflowMailing) graph.getPreviousIconByType(decision, WorkflowIconType.MAILING.getId(), Collections.singleton(decisionTypeId));
    }

    @Override
    public List<List<WorkflowNode>> getChains(WorkflowIcon icon, List<WorkflowIcon> icons, boolean isForwardDirection) {
        WorkflowGraph graph = new WorkflowGraph();

        if (!graph.build(icons)) {
            return new ArrayList<>();
        }

        List<List<WorkflowNode>> chains = graph.findChains(icon, isForwardDirection);
        if (chains.size() == 0) {
            chains.add(Collections.singletonList(graph.getNodeByIcon(icon)));
        } else {
            for (List<WorkflowNode> chain : chains) {
                chain.add(0, graph.getNodeByIcon(icon));
            }
        }
        return chains;
    }

    @Override
    public Date getMaxPossibleDate(WorkflowIcon icon, List<WorkflowIcon> workflowIcons) {
        List<List<WorkflowNode>> chains = getChains(icon, workflowIcons, false);
        return getMaxPossibleDate(chains);
    }

    @Override
    public Date getMaxPossibleDate(List<List<WorkflowNode>> chains) {
        Date maxDate = null;
        if (CollectionUtils.isNotEmpty(chains)) {
            for (List<WorkflowNode> chain : chains) {
                if (CollectionUtils.isNotEmpty(chain)) {
                    Date date = getChainDate(chain);
                    if (date != null && (maxDate == null || date.after(maxDate))) {
                        maxDate = date;
                    }
                }
            }
        }
        return maxDate;
    }

    @Override
    public Date getChainDate(List<WorkflowNode> chain) {
        return getChainDate(chain, null);
    }

    @Override
    public Date getChainDate(List<WorkflowNode> chain, WorkflowIcon terminatingIcon) {
        Calendar calendar = null;
        if (CollectionUtils.isNotEmpty(chain)) {
            chain = new ArrayList<>(chain);
            Collections.reverse(chain);

            WorkflowIcon firstIcon = chain.get(0).getNodeIcon();
            // Required a filled start icon
            if (firstIcon.getType() != WorkflowIconType.START.getId() || !firstIcon.isFilled()) {
                return null;
            }

            WorkflowStart startIcon = (WorkflowStart) firstIcon;

            calendar = Calendar.getInstance();
            calendar.setTime(startIcon.getDate());
            calendar.set(Calendar.HOUR_OF_DAY, startIcon.getHour());
            calendar.set(Calendar.MINUTE, startIcon.getMinute());

            for (WorkflowNode node : chain) {
                WorkflowIcon icon = node.getNodeIcon();

                if (icon == terminatingIcon) {
                    break;
                }

                if (icon.getType() == WorkflowIconType.DEADLINE.getId()) {
                    WorkflowDeadline deadline = (WorkflowDeadline) icon;

                    // Every deadline icon have to be filled
                    if (!icon.isFilled()) {
                        return null;
                    }

                    switch (deadline.getDeadlineType()) {
                        case TYPE_FIXED_DEADLINE:
                            Calendar deadlineCalendar = Calendar.getInstance();
                            deadlineCalendar.setTime(deadline.getDate());
                            deadlineCalendar.set(Calendar.HOUR_OF_DAY, deadline.getHour());
                            deadlineCalendar.set(Calendar.MINUTE, deadline.getMinute());

                            // Ignore a deadline if it's earlier than we already are
                            if (deadlineCalendar.after(calendar)) {
                                calendar = deadlineCalendar;
                            }
                            break;

                        case TYPE_DELAY:
                            switch (deadline.getTimeUnit()) {
                                case TIME_UNIT_MINUTE:
                                    calendar.add(Calendar.MINUTE, deadline.getDelayValue());
                                    break;
                                case TIME_UNIT_HOUR:
                                    calendar.add(Calendar.HOUR_OF_DAY, deadline.getDelayValue());
                                    break;
                                case TIME_UNIT_DAY:
                                    calendar.add(Calendar.DATE, deadline.getDelayValue());
                                    break;
								case TIME_UNIT_MONTH:
									break;
								case TIME_UNIT_WEEK:
									break;
								default:
									break;
                            }
                            break;
                    }
                } else if (icon.getType() == WorkflowIconType.DECISION.getId()) {
                    WorkflowDecision decision = (WorkflowDecision) icon;

                    if (icon.isFilled() && decision.getDecisionType() == WorkflowDecision.WorkflowDecisionType.TYPE_AUTO_OPTIMIZATION) {
                        Calendar decisionCalendar = Calendar.getInstance();
                        decisionCalendar.setTime(decision.getDecisionDate());

                        // Ignore a decision if it's earlier than we already are
                        if (decisionCalendar.after(calendar)) {
                            calendar = decisionCalendar;
                        }
                    }
                }
            }
        }
        return calendar == null ? null : calendar.getTime();
    }

	@Override
	public List<Workflow> getWorkflowsToDeactivate(CompaniesConstraints constraints) {
		return workflowDao.getWorkflowsToDeactivate(constraints);
	}

    @Override
    public List<Workflow> getWorkflowsByIds(Set<Integer> workflowIds, @VelocityCheck int companyId) {
        return workflowDao.getWorkflows(workflowIds, companyId);
    }

    @Override
    public List<Integer> getWorkflowIdsByAssignedMailingId(@VelocityCheck int companyId, int mailingId) {
        return workflowDao.getWorkflowIdsByAssignedMailingId(companyId, mailingId);
    }

    @Override
    public boolean hasDeletedMailings(List<WorkflowIcon> icons, @VelocityCheck int companyId) {
        boolean hasDeleted = false;

        for (WorkflowIcon icon : icons) {
            if (WorkflowUtils.isMailingIcon(icon) || WorkflowUtils.isBranchingDecisionIcon(icon)) {
                WorkflowMailingAware mailingIcon = (WorkflowMailingAware) icon;
                int mailingId = mailingIcon.getMailingId();
                if (mailingId != 0) {
                    if (mailingDao.isMailingMarkedDeleted(mailingId, companyId)) {
                        mailingIcon.setMailingId(0);
                        mailingIcon.setIconTitle("");
                        mailingIcon.setFilled(false);
                        hasDeleted = true;
                    }
                }
            }
        }

        return hasDeleted;
    }

    @Override
    public List<WorkflowFollowupMailing> getFollowupMailingIcon(List<WorkflowIcon> workflowIcons) {
        List<WorkflowFollowupMailing> list = new ArrayList<>();
        for (WorkflowIcon icon : workflowIcons) {
            if (icon.getType() == WorkflowIconType.FOLLOWUP_MAILING.getId()) {
                list.add((WorkflowFollowupMailing) icon);
            }
        }
        return list;
    }

    @Override
    public boolean isAdditionalRuleDefined(@VelocityCheck int companyId, int mailingId, int workflowId) {
        boolean result = false;
        Workflow workflow = getWorkflow(workflowId, companyId);
        List<WorkflowIcon> icons = workflow.getWorkflowIcons();

        for (WorkflowIcon icon : icons) {
            if (icon.getType() == WorkflowIconType.DATE_BASED_MAILING.getId()) {
                WorkflowGraph workflowGraph = new WorkflowGraph();
                if (workflowGraph.build(icons) && workflowValidationService.noLoops(icons)) {
                    HashSet<Integer> terminateTypes = new HashSet<>();

                    // found start
                    WorkflowIcon prevStartIcon = workflowGraph.getPreviousIconByType(icon, WorkflowIconType.START.getId(), terminateTypes);
                    if (prevStartIcon != null) {
                        WorkflowStart start = (WorkflowStart) prevStartIcon;
                        if (start.getStartType() == WorkflowStart.WorkflowStartType.EVENT && start.getEvent() == WorkflowStartEventType.EVENT_DATE) {
                            result = true;
                        }
                    }
                }
            }
        }
        return result;
    }

    @Override
    public boolean checkReactionNeedsActiveBinding(ComWorkflowReaction reaction) {
        switch (reaction.getReactionType()) {
            case OPT_OUT:
            case WAITING_FOR_CONFIRM:
                return false;
            default: return true;
        }
    }

    @Override
    public List<Integer> getProperUserStatusList(ComWorkflowReaction reaction) {
        switch (reaction.getReactionType()) {
            case OPT_OUT:
                return new ArrayList<>(Arrays.asList(UserStatus.UserOut.getStatusCode(), UserStatus.AdminOut.getStatusCode()));

            case WAITING_FOR_CONFIRM:
                return new ArrayList<>(Collections.singletonList(UserStatus.WaitForConfirm.getStatusCode()));

            default:
                return null;
        }
    }

    @Override
    public ComWorkflowReaction getWorkflowReaction(int workflowId, @VelocityCheck int companyId) {
        int reactionId = reactionDao.getReactionId(workflowId, companyId);
        if (reactionId > 0) {
            return reactionDao.getReaction(reactionId, companyId);
        }
        return null;
    }

    @Override
    public List<Integer> getReactedRecipients(ComWorkflowReaction reaction, boolean excludeLoggedReactions) {
        switch (reaction.getReactionType()) {
            case CLICKED:
            case CLICKED_LINK:
                return reactionDao.getClickedRecipients(reaction, excludeLoggedReactions);

            case OPENED:
                return reactionDao.getOpenedRecipients(reaction, excludeLoggedReactions);

            case CHANGE_OF_PROFILE:
                return reactionDao.getRecipientsWithChangedProfile(reaction, excludeLoggedReactions);

            case OPT_IN:
            case OPT_OUT:
            case WAITING_FOR_CONFIRM:
                return reactionDao.getRecipientsWithChangedBinding(reaction, excludeLoggedReactions);
            default:
                return Collections.emptyList();
        }
    }

    @Override
    public void processPendingReactionSteps(CompaniesConstraints constraints) {
        List<WorkflowReactionStep> stepsToMake = reactionDao.getStepsToMake(constraints);

        stepsToMake.stream()
            .collect(Collectors.groupingBy(ReactionId::new))
            .forEach((rc, steps) -> processReactionSteps(rc.getCompanyId(), rc.getReactionId(), steps));

        if (stepsToMake.size() > 0) {
            // Useless steps couldn't appear unless some steps has been processed.
            reactionDao.setUselessStepsDone(constraints);
        }
    }

    @Override
    public List<Workflow> getActiveWorkflowsTrackingProfileField(String column, @VelocityCheck int companyId) {
        return workflowDao.getActiveWorkflowsTrackingProfileField(column, companyId);
    }

    @Override
    public List<Workflow> getActiveWorkflowsDependentOnProfileField(String column, @VelocityCheck int companyId) {
        return workflowDao.getActiveWorkflowsUsingProfileField(column, companyId);
    }

    @Override
    public List<Workflow> getActiveWorkflowsDrivenByProfileChange(@VelocityCheck int companyId, int mailingListId, String column, List<WorkflowRule> rules) {
        boolean isUseRules = CollectionUtils.isNotEmpty(rules);

        List<Workflow> workflows = workflowDao.getActiveWorkflowsDrivenByProfileChange(companyId, mailingListId, column, isUseRules);

        if (isUseRules) {
            // Exclude workflows using different rules.
            workflows.removeIf(w -> !compareRules(w, rules));
        }

        // Order by id, descending
        workflows.sort((w1, w2) -> Integer.compare(w2.getWorkflowId(), w1.getWorkflowId()));

        return workflows;
    }

    private boolean loadIcons(Workflow workflow) {
        if (workflow.getWorkflowIcons() == null) {
            List<WorkflowIcon> icons = getIcons(workflow.getWorkflowSchema());
            workflow.setWorkflowIcons(icons);
            return icons.size() > 0;
        }

        return true;
    }

    private boolean compareRules(Workflow workflow, List<WorkflowRule> rules) {
        if (loadIcons(workflow)) {
            for (WorkflowIcon icon : workflow.getWorkflowIcons()) {
                if (icon.getType() == WorkflowIconType.START.getId()) {
                    WorkflowStart start = (WorkflowStart) icon;

                    if (WorkflowUtils.is(start, WorkflowReactionType.CHANGE_OF_PROFILE) && rules.equals(start.getRules())) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    @Transactional
    @Override
    public boolean addReportToIcon(ComAdmin admin, int workflowId, int iconId, int reportId) {
        if (workflowId <= 0 || iconId <= 0 || reportId <= 0) {
            return false;
        }

        Workflow workflow = getWorkflow(workflowId, admin.getCompanyID());

        if (workflow == null) {
            return false;
        }

        // Prevent changing running or complete workflow.
        if (!workflow.getStatus().isChangeable()) {
            return false;
        }

        WorkflowReport report = getReportIcon(workflow.getWorkflowIcons(), iconId);

        if (report == null) {
            return false;
        }

        addReportToIcon(report, reportId);
        // Save changes, update dependencies.

        saveWorkflow(admin, workflow, workflow.getWorkflowIcons());

        return true;
    }

    @Override
    public List<Campaign> getCampaignList(int companyId, String sort, int order) {
        return campaignDao.getCampaignList(companyId, sort, order);
    }
    
    @Override
    public void setTargetConditionDependency(ComTarget target, @VelocityCheck int companyId, int workflowId) {
        int targetId = target.getId();
        if(targetId > 0) {
            workflowDao.addDependency(companyId, workflowId,
                    WorkflowDependency.from(WorkflowDependencyType.TARGET_GROUP_CONDITION, targetId));
        }
    }
    
    @Override
    public void deleteWorkflowTargetConditions(@VelocityCheck int companyId, int workflowId) {
		targetDao.deleteWorkflowTargetConditions(companyId, workflowId);
		workflowDao.deleteTargetConditionDependencies(companyId, workflowId);
	}
    
    @Override
    public void convertTargetRepresentationToEQL(@VelocityCheck int companyId) {
        try {
            Map<Integer, String> targetEQLForUpdate = new HashMap<>();
            List<RawTargetGroup> targetGroups = targetDao.getTargetsCreatedByWorkflow(companyId, true);
        
            for (RawTargetGroup targetGroup : targetGroups) {
                try {
                    boolean disableThreeValueLogic = StringUtils.endsWith(targetGroup.getName(), "decision]");
    
                    String convertedEQL = eqlFacade.convertTargetRepresentationToEql(targetGroup.getRepresentation(),
                            targetGroup.getCompanyId(), disableThreeValueLogic);
                    
                    targetEQLForUpdate.put(targetGroup.getId(), convertedEQL);
                } catch (TargetRepresentationToEqlConversionException e) {
                    logger.error("Cannot convert target representation for target group id: " + targetGroup.getId(), e);
                }
            }
            targetDao.updateTargetGroupEQL(targetEQLForUpdate);
            logger.warn("Converted " + targetEQLForUpdate.size() + " target groups for company id: " + companyId);
		} catch (Exception e) {
			logger.error("Cannot convert target representation of CM target group for companyId: " + companyId, e);
		}
    
    }
    
    @Override
    public void cleanWorkflowUnusedTargetConditions(ComCompany company) {
        int companyId = company.getId();
        try {
            Set<Integer> targetConditionsInUse = new LinkedHashSet<>();
        
            // get just hidden target groups with name pattern '[campaign target: %]'
            List<Integer> targetsCreatedByWorkflow = targetDao.getTargetIdsCreatedByWorkflow(companyId);
            
            targetConditionsInUse.addAll(getMailingUsedTargetIds(companyId));
            targetConditionsInUse.addAll(workflowDao.getAllWorkflowUsedTargets(companyId));
            
            List<Integer> unusedTargets = ListUtils.removeAll(targetsCreatedByWorkflow, targetConditionsInUse);
            int affected = workflowDao.bulkDeleteTargetCondition(unusedTargets, companyId);
        
            logger.warn(String.format("Deleted %d unused target groups created by campaigns in company \"%s\" (ID: %d)",
                    affected, company.getShortname(), companyId));
        } catch (Exception e) {
            logger.error(String.format("Cleaning target groups failed for company \"%s\" (ID: %d)", company.getShortname(), companyId), e);
        }
    }
    
    private Set<Integer> getMailingUsedTargetIds(int companyId) {
        return mailingDao.getMailingIds(companyId).stream()
                .map(mailingId -> mailingDao.getTargetExpression(mailingId, companyId))
                .parallel()
                .map(TargetExpressionUtils::getTargetIds)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }
    
    private WorkflowReport getReportIcon(List<WorkflowIcon> icons, int iconId) {
        // Search for referenced icon, make sure it has proper type.
        for (WorkflowIcon icon : icons) {
            if (iconId == icon.getId()) {
                if (icon.getType() == WorkflowIconType.REPORT.getId()) {
                    return (WorkflowReport) icon;
                } else {
                    return null;
                }
            }
        }

        return null;
    }

    private void addReportToIcon(WorkflowReport report, int reportId) {
        List<Integer> reports = report.getReports();

        if (reports == null) {
            reports = new ArrayList<>();
            report.setReports(reports);
        }

        // Prevent duplication.
        if (!reports.contains(reportId)) {
            reports.add(reportId);
        }
    }

    private void processReactionSteps(int companyId, int reactionId, List<WorkflowReactionStep> steps) {
        ComWorkflowReaction reaction = reactionDao.getReaction(reactionId, companyId);

        // Process steps, apply filters (decisions + mailing target expression + mailing list + list split) and collect mailings (and recipients) to send.
        Map<Integer, List<Integer>> schedule = processReactionSteps(reaction, steps);

        if (schedule.size() > 0) {
            // List of binding statuses (or empty list for default behavior defined by back-end) that the recipient binding
            // should have (otherwise recipient won't receive mails).
            List<Integer> userStatuses = getProperUserStatusList(reaction);

            // Send mails to recipients according to processed reaction steps.
            schedule.forEach((mailingId, recipientIds) -> send(reaction, mailingId, recipientIds, userStatuses));
        }
    }

    private Map<Integer, List<Integer>> processReactionSteps(ComWorkflowReaction reaction, List<WorkflowReactionStep> steps) {
        // Maps mailingId -> recipientIds[]
        Map<Integer, List<Integer>> schedule = new HashMap<>();

        // Whether or not inactive recipients (having inactive binding status) should be excluded.
        boolean ensureActive = checkReactionNeedsActiveBinding(reaction);

        // Follow pre-determined execution order (sort by stepId) to make sure that dependent step will be processed
        // after the step it depends on.
        // Keep in mind that all these steps belong to the same reaction but they may have different caseId.
        steps.sort(Comparator.comparingInt(WorkflowReactionStepDeclaration::getStepId));

        MailingTargetSupplier targetSupplier = new MailingTargetCachingSupplier();

        for (WorkflowReactionStep step : steps) {
            String sqlStepTargetExpression = null;

            // Target group assigned to step refers to decisions and sequence control (to make sure that previous mailing is sent).
            if (step.getTargetId() > 0) {
                sqlStepTargetExpression = targetService.getTargetSQL(step.getTargetId(), step.getCompanyId(), step.isTargetPositive());
                // If specified target group doesn't exist then campaign "stops" at this step (no recipients).
                if (sqlStepTargetExpression == null) {
                    logger.error("The target group #" + step.getTargetId() + " is invalid or missing, required at step #" + step.getStepId() + " (reactionId:" + step.getReactionId() + ")");
                    sqlStepTargetExpression = "1=0";
                }
            }

            if (step.getMailingId() > 0) {
                // If mailing should be sent at this step than we need to filter recipients first (mailing list + target expression + list split).
                String sqlMailingTargetExpression = targetSupplier.getTargetAndListSplitSqlCode(step.getMailingId(), step.getCompanyId());
                int mailingListId = targetSupplier.getMailingListId(step.getMailingId(), step.getCompanyId());

                reactionDao.setStepDone(step, mailingListId, ensureActive, combineTargetExpressions(sqlStepTargetExpression, sqlMailingTargetExpression));

                // Retrieve all the recipients who reached this step.
                List<Integer> recipientIds = reactionDao.getStepRecipients(step);

                if (recipientIds.size() > 0) {
                    // Schedule mailing to be sent.
                    schedule.computeIfAbsent(step.getMailingId(), mailingId -> new ArrayList<>())
                        .addAll(recipientIds);
                }
            } else {
                if (sqlStepTargetExpression == null) {
                    reactionDao.setStepDone(step);
                } else {
                    reactionDao.setStepDone(step, sqlStepTargetExpression);
                }
            }
        }

        return schedule;
    }

    private String combineTargetExpressions(String sqlStepTargetExpression, String sqlMailingTargetExpression) {
        if (sqlStepTargetExpression == null && sqlMailingTargetExpression == null) {
            return null;
        }

        if (sqlStepTargetExpression == null) {
            return sqlMailingTargetExpression;
        } else if (sqlMailingTargetExpression == null) {
            return sqlStepTargetExpression;
        } else {
            return String.format("(%s) AND (%s)", sqlStepTargetExpression, sqlMailingTargetExpression);
        }
    }

    private void send(ComWorkflowReaction reaction, int mailingId, List<Integer> recipientIds, List<Integer> allowedUserStatuses) {
        for (int recipientId : recipientIds) {
            send(reaction, mailingId, recipientId, allowedUserStatuses);
        }
    }

    private void send(ComWorkflowReaction reaction, int mailingId, int recipientId, List<Integer> allowedUserStatuses) {
        try {
            MailgunOptions options = new MailgunOptions();
            options.withAllowedUserStatus(allowedUserStatuses);
        	sendActionbasedMailingService.sendActionbasedMailing(reaction.getCompanyId(), mailingId, recipientId, 0, options);
        } catch (SendActionbasedMailingException e) {
            // todo #monitor?
            logger.error("WM Reaction: error (reactionId: " + reaction.getReactionId() + ", workflowId: " + reaction.getWorkflowId() + "): " + e.getMessage(), e);
        }
    }

    private void deactivateMailing(WorkflowMailingAware icon, boolean testing, @VelocityCheck int companyId) {
        int mailingId = WorkflowUtils.getMailingId(icon);

        if (mailingId > 0) {
            switch (WorkflowIconType.fromId(icon.getType(), false)) {
                case MAILING:
                case FOLLOWUP_MAILING:
                    mailingSendService.deactivateMailing(mailingId, companyId);

                    // Leave status "test" for mailing if that was the test run.
                    if (!testing) {
                        mailingDao.updateStatus(mailingId, "canceled");
                    }
                    break;

                case ACTION_BASED_MAILING:
                case DATE_BASED_MAILING:
                    mailingSendService.deactivateMailing(mailingId, companyId);

                    // Leave status "test" for mailing if that was the test run.
                    if (!testing) {
                        mailingDao.updateStatus(mailingId, "disable");
                    }
                    break;
				case ARCHIVE:
					break;
				case DEADLINE:
					break;
				case DECISION:
					break;
				case EXPORT:
					break;
				case FORM:
					break;
				case IMPORT:
					break;
				case PARAMETER:
					break;
				case RECIPIENT:
					break;
				case REPORT:
					break;
				case START:
					break;
				case STOP:
					break;
				default:
					break;
            }
        }
    }

    private void deactivateAutoImport(WorkflowImport icon, @VelocityCheck int companyId) throws Exception {
        AutoImport autoImport = autoImportService.getAutoImport(icon.getImportexportId(), companyId);
        if (autoImport.isDeactivateByCampaign()) {
            autoImportService.deactivateAutoImport(companyId, icon.getImportexportId());
        }
    }

    private void deactivateAutoExport(WorkflowExport icon, @VelocityCheck int companyId) throws Exception {
        AutoExport autoExport = autoExportService.getAutoExport(icon.getImportexportId(), companyId);
        if (autoExport.isDeactivateByCampaign()) {
            autoExportService.deactivateAutoExport(companyId, icon.getImportexportId());
        }
    }

    @Required
    public void setWorkflowDao(ComWorkflowDao workflowDao) {
        this.workflowDao = workflowDao;
    }

    @Required
	public void setMailingDao(ComMailingDao mailingDao) {
		this.mailingDao = mailingDao;
	}

    @Required
	public void setColumnInfoService(ComColumnInfoService columnInfoService) {
		this.columnInfoService = columnInfoService;
	}

    @Required
	public void setAdminDao(ComAdminDao adminDao) {
		this.adminDao = adminDao;
	}

    @Required
	public void setLinkDao(ComTrackableLinkDao linkDao) {
		this.linkDao = linkDao;
	}

    @Required
	public void setTargetDao(ComTargetDao targetDao) {
		this.targetDao = targetDao;
	}

    @Required
    public void setTargetService(ComTargetService targetService) {
        this.targetService = targetService;
    }

    @Required
    public void setUserFormDao(UserFormDao userFormDao) {
        this.userFormDao = userFormDao;
    }

    @Required
    public void setBirtCompanyDao(ComCompanyDao birtCompanyDao) {
        this.birtCompanyDao = birtCompanyDao;
    }

    @Required
	public void setMailingSendService(ComMailingSendService mailingSendService) {
		this.mailingSendService = mailingSendService;
	}

    @Required
	public void setReactionDao(ComWorkflowReactionDao reactionDao) {
		this.reactionDao = reactionDao;
	}

    @Required
    public void setWorkflowValidationService(ComWorkflowValidationService workflowValidationService) {
        this.workflowValidationService = workflowValidationService;
    }

    public void setAutoImportService(AutoImportService autoImportService) {
        this.autoImportService = autoImportService;
    }

    public void setAutoExportService(AutoExportService autoExportService) {
        this.autoExportService = autoExportService;
    }

    @Required
    public void setOptimizationService(ComOptimizationService optimizationService) {
        this.optimizationService = optimizationService;
    }

    @Required
    public void setOptimizationCommonService(ComOptimizationCommonService optimizationCommonService) {
        this.optimizationCommonService = optimizationCommonService;
    }

    @Required
    public void setBirtReportDao(ComBirtReportDao birtReportDao) {
        this.birtReportDao = birtReportDao;
    }

    @Required
    public void setSendActionbasedMailingService(SendActionbasedMailingService sendActionbasedMailingService) {
        this.sendActionbasedMailingService = sendActionbasedMailingService;
    }

    @Required
    public void setProfileFieldDao(ComProfileFieldDao profileFieldDao) {
        this.profileFieldDao = profileFieldDao;
    }

    @Required
    public void setWorkflowDataParser(ComWorkflowDataParser workflowDataParser) {
        this.workflowDataParser = workflowDataParser;
    }

    @Required
    public void setWorkflowStartStopReminderDao(ComWorkflowStartStopReminderDao reminderDao) {
        this.reminderDao = reminderDao;
    }

    @Required
    public void setReminderService(ComReminderService reminderService) {
        this.reminderService = reminderService;
    }
    
    @Required
    public void setReminderDao(ComWorkflowStartStopReminderDao reminderDao) {
        this.reminderDao = reminderDao;
    }
    
    @Required
    public void setCampaignDao(ComCampaignDao campaignDao) {
        this.campaignDao = campaignDao;
    }
    
    @Required
    public void setEqlFacade(EqlFacade eqlFacade) {
        this.eqlFacade = eqlFacade;
    }
    
    private static class ReactionId {
        private int companyId;
        private int reactionId;

        public ReactionId(WorkflowReactionStep step) {
            this.companyId = step.getCompanyId();
            this.reactionId = step.getReactionId();
        }

        public int getCompanyId() {
            return companyId;
        }

        public int getReactionId() {
            return reactionId;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null) {
                return false;
            }

            if (ReactionId.class == o.getClass()) {
                ReactionId other = (ReactionId) o;
                return companyId == other.companyId && reactionId == other.reactionId;
            }

            return false;
        }

        @Override
        public int hashCode() {
            return (companyId + "@" + reactionId).hashCode();
        }
    }

    private static class Chain {
        private WorkflowStart start;
        private List<WorkflowRecipient> recipients;
        private List<WorkflowDeadline> deadlines;
        private List<WorkflowParameter> parameters;
        private WorkflowArchive archive;

        public Chain() {
            this.recipients = new ArrayList<>();
            this.deadlines = new ArrayList<>();
            this.parameters = new ArrayList<>();
        }

        public Chain(Chain chain) {
            this.start = chain.start;
            this.recipients = new ArrayList<>(chain.recipients);
            this.deadlines = new ArrayList<>(chain.deadlines);
            this.parameters = new ArrayList<>(chain.parameters);
            this.archive = chain.archive;
        }

        public void append(WorkflowStart start) {
            this.start = start.isFilled() ? start : null;
            this.recipients.clear();
            this.deadlines.clear();
            this.parameters.clear();
            this.archive = null;
        }

        public void append(WorkflowRecipient recipient) {
            if (recipient.isFilled()) {
                recipients.add(recipient);
            }
        }

        public void append(WorkflowDeadline deadline) {
            if (deadline.isFilled()) {
                deadlines.add(deadline);
            }
        }

        public void append(WorkflowParameter parameter) {
            if (parameter.isFilled()) {
                parameters.add(parameter);
            }
        }

        public void append(WorkflowArchive archive) {
            if (archive.isFilled()) {
                this.archive = archive;
            }
        }

        public Date getDate(TimeZone timezone) {
            Deadline deadline = null;

            if (start != null) {
                deadline = WorkflowUtils.asDeadline(start, timezone);
            }

            for (WorkflowDeadline icon : deadlines) {
                Deadline nextDeadline = WorkflowUtils.asDeadline(icon, timezone);

                if (deadline == null) {
                    deadline = nextDeadline;
                } else {
                    deadline = deadline.add(nextDeadline);
                }
            }

            if (deadline == null || deadline.isRelative()) {
                return null;
            }

            return new Date(deadline.getValue());
        }

        public int getMailingListId() {
            int mailingListId = 0;

            for (WorkflowRecipient recipient : recipients) {
                if (recipient.getMailinglistId() > 0) {
                    mailingListId = recipient.getMailinglistId();
                    break;
                }
            }

            return mailingListId;
        }

        public String getTargetExpression() {
            List<String> expressions = new ArrayList<>(recipients.size());

            for (WorkflowRecipient recipient : recipients) {
                List<Integer> targetIds = recipient.getTargets();
                if (CollectionUtils.isNotEmpty(targetIds)) {
                    expressions.add(TargetExpressionUtils.makeTargetExpression(targetIds, recipient.getTargetsOption()));
                }
            }

            if (expressions.size() > 1) {
                // Wrap separate expressions with brackets if they use OR operator.
                expressions.replaceAll(e -> e.contains("|") ? ("(" + e + ")") : (e));
            }

            return StringUtils.join(expressions, '&');
        }

        public WorkflowArchive getArchive() {
            return archive;
        }
    }

    private interface MailingTargetSupplier {
        String getTargetAndListSplitSqlCode(int mailingId, int companyId);
        int getMailingListId(int mailingId, int companyId);
    }

    private class MailingTargetCachingSupplier implements MailingTargetSupplier {
        private Map<Integer, String> sqlCodeMap = new HashMap<>();
        private Map<Integer, Integer> mailingListMap = new HashMap<>();

        @Override
        public String getTargetAndListSplitSqlCode(int mailingId, int companyId) {
            return sqlCodeMap.computeIfAbsent(mailingId, id -> targetService.getMailingSqlTargetExpression(id, companyId, true));
        }

        @Override
        public int getMailingListId(int mailingId, int companyId) {
            return mailingListMap.computeIfAbsent(mailingId, id -> mailingDao.getMailinglistId(id, companyId));
        }
    }

    private interface AdminTimezoneSupplier {
        TimeZone getTimezone(int adminId);
    }

    private class AdminTimezoneCachingSupplier implements AdminTimezoneSupplier {
        private final int companyId;
        private final Map<Integer, TimeZone> timezoneMap = new HashMap<>();

        public AdminTimezoneCachingSupplier(int companyId) {
            if (companyId <= 0) {
                throw new IllegalArgumentException("companyId <= 0");
            }

            this.companyId = companyId;
        }

        @Override
        public TimeZone getTimezone(int adminId) {
            return timezoneMap.computeIfAbsent(adminId, id -> {
                String timezone = adminDao.getAdminTimezone(id, companyId);
                return TimeZone.getTimeZone(timezone);
            });
        }
    }

    private interface EntitiesSupplier {
        ComMailing getMailing(int mailingId);
        AutoImport getAutoImport(int autoImportId);
    }

    private class CachingEntitiesSupplier implements EntitiesSupplier, AutoCloseable {
        private final int companyId;
        private Map<Integer, ComMailing> mailingMap = new HashMap<>();
        private Map<Integer, AutoImport> autoImportMap = new HashMap<>();

        public CachingEntitiesSupplier(@VelocityCheck int companyId) {
            this.companyId = companyId;
        }

        @Override
        public ComMailing getMailing(int mailingId) {
            return mailingMap.computeIfAbsent(mailingId, this::loadMailing);
        }

        private ComMailing loadMailing(int mailingId) {
            ComMailing mailing = (ComMailing) mailingDao.getMailing(mailingId, companyId);

            if (mailing != null && mailing.getId() > 0) {
                return mailing;
            }

            return null;
        }

        @Override
        public AutoImport getAutoImport(int autoImportId) {
            return autoImportMap.computeIfAbsent(autoImportId, this::loadAutoImport);
        }

        private AutoImport loadAutoImport(int autoImportId) {
            return autoImportService.getAutoImport(autoImportId, companyId);
        }

        @Override
        public void close() throws Exception {
            for (ComMailing mailing : mailingMap.values()) {
                if (mailing != null) {
                    mailingDao.saveMailing(mailing, false);
                }
            }

            for (AutoImport autoImport : autoImportMap.values()) {
                if (autoImport != null) {
                    autoImportService.saveAutoImport(autoImport, autoImport.getCompanyId());
                }
            }

            mailingMap.clear();
            autoImportMap.clear();
        }
    }
}
