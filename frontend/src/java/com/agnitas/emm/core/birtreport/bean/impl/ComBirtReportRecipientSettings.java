/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.emm.core.birtreport.bean.impl;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.agnitas.emm.core.birtreport.bean.ComBirtReport;
import com.agnitas.emm.core.birtreport.util.BirtReportSettingsUtils;
import com.agnitas.emm.core.birtreport.dto.ReportSettingsType;
import com.agnitas.reporting.birt.external.utils.BirtReporUtils;
import org.agnitas.util.EmmCalendar;
import org.apache.log4j.Logger;

public class ComBirtReportRecipientSettings extends ComBirtReportSettings {
    @SuppressWarnings("unused")
	private static final transient Logger logger = Logger.getLogger(ComBirtReportRecipientSettings.class);

    public static final String DATE_RANGE_KEY = "dateRangeType";
    public static final String DATE_RANGE_PREDEFINED_KEY = "predefinedDateRange";
    public static final int DATE_RANGE_PREDEFINED = 1;
    public static final int DATE_RANGE_CUSTOM = 2;

    public static final int DATE_RANGE_PREDEFINED_WEEK = 1;
    public static final int DATE_RANGE_PREDEFINED_MONTH = 2;
    public static final int DATE_RANGE_PREDEFINED_THREE_MONTHS = 3;

    public static final String MAILING_LISTS_KEY = "mailingLists";
    
    @Override
	public void loadDefaults() {
        super.loadDefaults();
        setReportSetting(DATE_RANGE_KEY, DATE_RANGE_PREDEFINED);
    }

    @Override
    public ReportSettingsType getReportSettingsType() {
        return ReportSettingsType.RECIPIENT;
    }

    @Override
    public String getReportName(String reportFormat) {
        if (ComBirtReport.FORMAT_CSV.equals(reportFormat)) {
            return "recipients_statistic_csv.rptdesign";
        } else {
            return "recipients_statistic.rptdesign";
        }
    }
    
    @Override
    public Map<String, String> getReportUrlParameters() {
        Map<String, String> parameters = new HashMap<>();
        String targetGroupsExpression = generateExpression(getTargetGroups());
        if (!targetGroupsExpression.isEmpty()) {
            parameters.put(TARGET_GROUPS_KEY, generateExpression(getTargetGroups()));
        }
        parameters.put(MAILING_LISTS_KEY, generateExpression(getMailinglists()));
        parameters.putAll(getDateRange());
        parameters.put(FIGURES_KEY, BirtReporUtils.packFigures(getSettingsMap()));

        return parameters;
    }

    public void setMailinglists(List<String> mailinglists) {
        setReportSetting(MAILINGLISTS_KEY, generateExpression(mailinglists));
    }

    public List<String> getMailinglists() {
        return getReportSettingAsList(MAILINGLISTS_KEY);
    }

    public int getDateRangeType() {
        return getReportSettingAsInt(DATE_RANGE_KEY);
    }

    public int getPredefinedDateRange() {
        return getReportSettingAsInt(DATE_RANGE_PREDEFINED_KEY);
    }

    private Map<String, String> getDateRange() {
        Map<String, String> dateRange =  new HashMap<>();
        String startDate = "";
        String stopDate = "";

        switch (getDateRangeType()) {
            case DATE_RANGE_CUSTOM:
                startDate = getReportSettingAsString(BirtReportSettingsUtils.START_DATE);
                stopDate = getReportSettingAsString(BirtReportSettingsUtils.END_DATE);
                break;
            case DATE_RANGE_PREDEFINED:
                final SimpleDateFormat reportDateFormat = BirtReportSettingsUtils.REPORT_DATE_FORMAT;
                final Calendar calendar = new GregorianCalendar();
                calendar.setTime(new Date());
                stopDate = reportDateFormat.format(calendar.getTime());
                switch (getPredefinedDateRange()) {
                    case DATE_RANGE_PREDEFINED_WEEK:
                        calendar.add(EmmCalendar.DAY_OF_YEAR, -7);
                        calendar.add(EmmCalendar.SECOND, -1);
                        break;
                    case DATE_RANGE_PREDEFINED_MONTH:
                        calendar.add(EmmCalendar.DAY_OF_YEAR, -30);
                        calendar.add(EmmCalendar.SECOND, -1);
                        break;
                    case DATE_RANGE_PREDEFINED_THREE_MONTHS:
                        calendar.add(EmmCalendar.MONTH, -3);
                        calendar.add(EmmCalendar.SECOND, -1);
                        break;
    				default:
    					throw new RuntimeException("Invalid date range type");
                }
                startDate = reportDateFormat.format(calendar.getTime());
                break;
			default:
				throw new RuntimeException("Invalid date range type");
        }
        dateRange.put(BirtReportSettingsUtils.START_DATE, startDate);
        dateRange.put(BirtReportSettingsUtils.END_DATE, stopDate);

        return dateRange;
    }
}
