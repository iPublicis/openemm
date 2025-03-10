/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.emm.core.recipient.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.agnitas.beans.ComProfileField;
import com.agnitas.beans.ComRecipientHistory;
import com.agnitas.dao.ComProfileFieldDao;
import com.agnitas.emm.core.recipient.ProfileFieldHistoryFeatureNotEnabledException;
import com.agnitas.emm.core.recipient.RecipientProfileHistoryException;
import com.agnitas.emm.core.recipient.RecipientProfileHistoryUtil;
import com.agnitas.emm.core.recipient.dao.RecipientProfileHistoryDao;
import com.agnitas.emm.core.recipient.dao.impl.RecipientProfileHistoryBasicDaoImpl;
import com.agnitas.emm.core.recipient.service.RecipientProfileHistoryService;
import org.agnitas.beans.ProfileField;
import org.agnitas.emm.core.commons.util.ConfigService;
import org.agnitas.emm.core.velocity.VelocityCheck;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Required;

/**
 * Implementation of {@link RecipientProfileHistoryService} interface.
 */
public class RecipientProfileHistoryBasicServiceImpl implements RecipientProfileHistoryService {

	/** The logger. */
	private static final transient Logger logger = Logger.getLogger(RecipientProfileHistoryBasicDaoImpl.class);

	/** Service accessing profile history data. */
	protected RecipientProfileHistoryDao profileHistoryDao;
	
	/** Service accessing configuration data. */
	protected ConfigService configService;
	
	/** DAO accessing profile fields data. */
	protected ComProfileFieldDao profileFieldDao;

	@Override
	public void afterProfileFieldStructureModification(@VelocityCheck int companyId) throws RecipientProfileHistoryException {
		try {
			List<ProfileField> profileFields = listProfileFieldsForHistory(companyId);
	
			profileHistoryDao.afterProfileFieldStructureModification(companyId, profileFields);
		} catch(Exception e) {
			logger.error(String.format("Error during post-processing for profile field history for company %d", companyId), e);
			
			throw new RecipientProfileHistoryException(String.format("Error during post-processing for profile field history for company %d", companyId), e);
		}
	}
	
	/**
	 * Determine list of profile fields to be included in history.
	 * 
	 * @param companyId company ID
	 * 
	 * @return  list of profile fields to be included in history
	 * 
	 * @throws Exception on errors during processing
	 */
	protected List<ProfileField> listProfileFieldsForHistory(@VelocityCheck int companyId) throws Exception {
		List<ComProfileField> allFields = profileFieldDao.getComProfileFields(companyId);

		return Optional.ofNullable(allFields).orElse(Collections.emptyList()).stream()
		.filter(field -> field.getHistorize() || RecipientProfileHistoryUtil.isDefaultColumn(field.getColumn()))
		.peek(field -> {
			if (logger.isDebugEnabled()) {
				logger.debug(String.format("Included profile field column '%s' in history", field.getColumn()));
			}
		}).collect(Collectors.toList());
	}

	@Override
	public boolean isProfileFieldHistoryEnabled(@VelocityCheck int companyId) {
		return configService.isRecipientProfileHistoryEnabled(companyId);
	}
	
	@Override
	public List<ComRecipientHistory> listProfileFieldHistory(int recipientID, @VelocityCheck int companyId) throws RecipientProfileHistoryException {
		if(!isProfileFieldHistoryEnabled(companyId)) {
			logger.error(String.format("Profile field history not enabled for company %d", companyId));
			
			throw new ProfileFieldHistoryFeatureNotEnabledException(companyId);
		}
		
		return profileHistoryDao.listProfileFieldHistory(recipientID, companyId);
	}
	
	
	/**
	 * Set DAO for writing profile field history data.
	 *
	 * @param profileHistoryDao DAO for writing profile field history data
	 */
	@Required
	public void setRecipientProfileHistoryDao(final RecipientProfileHistoryDao profileHistoryDao) {
		this.profileHistoryDao = profileHistoryDao;
	}

	/**
	 * Set service accessing configuration data.
	 *
	 * @param configService service accessing configuration data
	 */
	@Required
	public void setConfigService(final ConfigService configService) {
		this.configService = configService;
	}

	/**
	 * Set DAO accessing profile field data.
	 *
	 * @param profileFieldDao DAO accessing profile field data
	 */
	@Required
	public void setProfileFieldDao(final ComProfileFieldDao profileFieldDao) {
		this.profileFieldDao = profileFieldDao;
	}

	
}
