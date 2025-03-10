/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.dao.impl;

import javax.sql.DataSource;

import org.agnitas.dao.ImportRecipientsDao;
import org.agnitas.dao.LoginTrackDao;
import org.agnitas.dao.MaildropStatusDao;
import org.agnitas.dao.MailinglistDao;
import org.agnitas.dao.RdirTrafficAmountDao;
import org.agnitas.service.ImportModeHandlerFactory;
import org.agnitas.web.ProfileImportReporter;

import com.agnitas.dao.AnonymizeStatisticsDao;
import com.agnitas.dao.ComAdminDao;
import com.agnitas.dao.ComCompanyDao;
import com.agnitas.dao.ComDatasourceDescriptionDao;
import com.agnitas.dao.ComMailingDao;
import com.agnitas.dao.ComRecipientDao;
import com.agnitas.dao.ComTargetDao;
import com.agnitas.dao.ComUndoDynContentDao;
import com.agnitas.dao.ComUndoMailingComponentDao;
import com.agnitas.dao.ComUndoMailingDao;
import com.agnitas.dao.DynamicTagDao;
import com.agnitas.emm.core.JavaMailService;
import com.agnitas.emm.core.action.service.EmmActionService;
import com.agnitas.emm.core.commons.encrypt.ProfileFieldEncryptor;
import com.agnitas.emm.core.mailing.dao.ComMailingParameterDao;
import com.agnitas.emm.core.workflow.dao.ComWorkflowReactionDao;

public abstract class DaoLookupFactory {
	
	abstract public DataSource getBeanDataSource();
	abstract public ComDatasourceDescriptionDao getBeanDatasourceDescriptionDao();
	abstract public ComCompanyDao getBeanCompanyDao();
	abstract public ComAdminDao getBeanAdminDao();
	abstract public DynamicTagDao getBeanDynamicTagDao();
	abstract public ComMailingDao getBeanMailingDao();
	abstract public MaildropStatusDao getBeanMaildropStatusDao();
	abstract public ComMailingParameterDao getBeanMailingParameterDao();
	abstract public LoginTrackDao getBeanLoginTrackDao();
	abstract public ComUndoMailingDao getBeanUndoMailingDao();
	abstract public ComUndoMailingComponentDao getBeanUndoMailingComponentDao();
	abstract public ComUndoDynContentDao getBeanUndoDynContentDao();
	abstract public ComTargetDao getBeanTargetDao();
	abstract public MailinglistDao getBeanMailinglistDao();
	abstract public ImportRecipientsDao getBeanImportRecipientsDao();
	abstract public ProfileFieldEncryptor getBeanProfileFieldEncryptor();
	abstract public ComWorkflowReactionDao getBeanWorkflowReactionDao();
	abstract public ComRecipientDao getBeanRecipientDao();
	abstract public EmmActionService getBeanEmmActionService();
	abstract public ProfileImportReporter getBeanProfileImportReporter();
	abstract public ImportModeHandlerFactory getBeanImportModeHandlerFactory();
	abstract public RdirTrafficAmountDao getBeanRdirTrafficAmountDao();
	abstract public JavaMailService getBeanJavaMailService();
	abstract public AnonymizeStatisticsDao getBeanAnonymizeStatisticsDao();
}
