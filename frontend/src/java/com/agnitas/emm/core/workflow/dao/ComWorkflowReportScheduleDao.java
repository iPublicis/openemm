/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.emm.core.workflow.dao;

import java.util.Date;
import java.util.List;

import org.agnitas.beans.CompaniesConstraints;
import org.agnitas.emm.core.velocity.VelocityCheck;

import com.agnitas.emm.core.birtreport.bean.ComBirtReport;

public interface ComWorkflowReportScheduleDao {

	void scheduleWorkflowReport(int reportId, @VelocityCheck int companyId, Date sendTime);

	List<Integer> getAllWorkflowReportsToSend();

    List<ComBirtReport> getAllWorkflowBirtReportsToSend();

    List<Integer> getAllWorkflowBirtReportIdsToSend(CompaniesConstraints constraints);

	void markWorkflowReportsSent(List<Integer> reportIds);
}
