/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.agnitas.beans.BindingEntry;
import org.agnitas.beans.BindingEntry.UserType;
import org.agnitas.beans.Mailinglist;
import org.agnitas.beans.impl.BindingEntryImpl;
import org.agnitas.dao.BindingEntryDaoException;
import org.agnitas.dao.UserStatus;
import org.agnitas.dao.impl.BaseDaoImpl;
import org.agnitas.dao.impl.MailinglistDaoImpl;
import org.agnitas.emm.core.velocity.VelocityCheck;
import org.agnitas.util.DbUtilities;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;

import com.agnitas.beans.ComTarget;
import com.agnitas.dao.ComBindingEntryDao;
import com.agnitas.dao.ComRecipientDao;
import com.agnitas.dao.DaoUpdateReturnValueCheck;
import com.agnitas.emm.core.mediatypes.common.MediaTypes;
import com.agnitas.emm.core.report.bean.CompositeBindingEntry;
import com.agnitas.emm.core.report.bean.PlainBindingEntry;
import com.agnitas.emm.core.report.bean.impl.CompositeBindingEntryImpl;
import com.agnitas.emm.core.report.bean.impl.PlainBindingEntryImpl;

public class ComBindingEntryDaoImpl extends BaseDaoImpl implements ComBindingEntryDao {
	
	/** The logger. */
	private static final transient Logger logger = Logger.getLogger(ComBindingEntryDaoImpl.class);
	
	private ComRecipientDao recipientDao;
	
	@Required
	public final void setRecipientDao(final ComRecipientDao dao) {
		this.recipientDao = Objects.requireNonNull(dao, "Recipient DAO is null");
	}

	@Override
	public boolean getExistingRecipientIDByMailinglistID(Set<Integer> mailinglistIds, @VelocityCheck int companyId) {
		String sql = "SELECT COUNT(customer_id) FROM customer_" + companyId + "_binding_tbl WHERE mailinglist_id IN (" + StringUtils.join(mailinglistIds, ", ") + ")";
		return selectInt(logger, sql) > 0;
	}

	@Override
	@DaoUpdateReturnValueCheck
	public void deleteRecipientBindingsByMailinglistID(Set<Integer> mailinglistIds, @VelocityCheck int companyId) {
        if (mailinglistIds == null || mailinglistIds.isEmpty()) {
            return;
        }

		String sql = "DELETE FROM customer_" + companyId + "_binding_tbl WHERE mailinglist_id IN (" + StringUtils.join(mailinglistIds, ", ") + ")";
		update(logger, sql);
	}
	
	@Override
	public BindingEntry get(int recipientID, @VelocityCheck int companyID, int mailinglistID, int mediaType) {
		try {
			String sql = "SELECT customer_id, mailinglist_id, mediatype, user_type, user_status, timestamp, exit_mailing_id, user_remark, creation_date FROM customer_" + companyID + "_binding_tbl WHERE customer_id = ? AND mailinglist_id = ? AND mediatype = ?";
			List<BindingEntry> list = select(logger, sql, new BindingEntry_RowMapper(this), recipientID, mailinglistID, mediaType);
			if (list.size() > 0) {
				return list.get(0);
			} else {
				return null;
			}
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	public List<PlainBindingEntry> get(@VelocityCheck int companyId, int recipientId, int mailingId) {
		try {
			String selectMailingListId = "SELECT mailinglist_id FROM mailing_tbl WHERE mailing_id = ?";
			String query = String.format("SELECT * FROM %s", getBindingTableName(companyId)) +
					" WHERE customer_id = ?" +
					String.format(" AND mailinglist_id = (%s)", selectMailingListId);
			return select(logger, query, new PlainBindingEntryRowMapper(), recipientId, mailingId);
		} catch (Exception e) {
			return null;
		}
	}


	@Override
	public void save(@VelocityCheck int companyID, BindingEntry entry) {
		if (companyID <= 0) {
			return;
		}
		String existsSql = "SELECT * FROM customer_" + companyID + "_binding_tbl WHERE customer_id = ? AND mailinglist_id = ? AND mediatype = ?";
		List<BindingEntry> list = select(logger, existsSql, new BindingEntry_RowMapper(this), entry.getCustomerID(), entry.getMailinglistID(), entry.getMediaType());
		if (list.size() > 0) {
			updateBinding(entry, companyID);
		} else {
			insertNewBinding(entry, companyID);
		}
	}

	/**
	 * Updates this Binding in the Database
	 * 
	 * @return True: Sucess False: Failure
	 * @param companyID
	 *            The company ID of the Binding
	 */
	@Override
	@DaoUpdateReturnValueCheck
	public boolean updateBinding(BindingEntry entry, @VelocityCheck int companyID) {
		try {
			if (companyID <= 0) {
				return false;
			}
			
			int touchedLines;
			
			if (DbUtilities.containsColumnName(getDataSource(), "customer_" + companyID + "_binding_tbl", "referrer")) {
				String sql = "UPDATE customer_" + companyID + "_binding_tbl SET user_status = ?, user_remark = ?, referrer = ?, exit_mailing_id = ?, user_type = ?, timestamp = CURRENT_TIMESTAMP WHERE customer_id = ? AND mailinglist_id = ? AND mediatype = ?";
				touchedLines = update(logger,
					sql,
					entry.getUserStatus(),
					entry.getUserRemark(),
					entry.getReferrer(),
					entry.getExitMailingID(),
					entry.getUserType(),
					entry.getCustomerID(),
					entry.getMailinglistID(),
					entry.getMediaType());
			} else {
				String sql = "UPDATE customer_" + companyID + "_binding_tbl SET user_status = ?, user_remark = ?, exit_mailing_id = ?, user_type = ?, timestamp = CURRENT_TIMESTAMP WHERE customer_id = ? AND mailinglist_id = ? AND mediatype = ?";
				touchedLines = update(logger,
					sql,
					entry.getUserStatus(),
					entry.getUserRemark(),
					entry.getExitMailingID(),
					entry.getUserType(),
					entry.getCustomerID(),
					entry.getMailinglistID(),
					entry.getMediaType());
			}

			return touchedLines >= 1;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	@DaoUpdateReturnValueCheck
	public boolean insertNewBinding(BindingEntry entry, @VelocityCheck int companyID) {
		try {
			if (companyID <= 0) {
				return false;
			} else if (entry.getCustomerID() <= 0) {
				return false;
			} else {
				if(checkAssignedProfileFieldIsSet(entry, companyID)) {
					if (DbUtilities.containsColumnName(getDataSource(), "customer_" + companyID + "_binding_tbl", "referrer")) {
						String insertSql = "INSERT INTO customer_" + companyID + "_binding_tbl (mailinglist_id, customer_id, user_type, user_status, timestamp, user_remark, referrer, creation_date, exit_mailing_id, mediatype) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, CURRENT_TIMESTAMP, ?, ?)";
						update(logger,
							insertSql,
							entry.getMailinglistID(),
							entry.getCustomerID(),
							entry.getUserType(),
							entry.getUserStatus(),
							entry.getUserRemark(),
							entry.getReferrer(),
							entry.getExitMailingID(),
							entry.getMediaType());
						return true;
					} else {
						String insertSql = "INSERT INTO customer_" + companyID + "_binding_tbl (mailinglist_id, customer_id, user_type, user_status, timestamp, user_remark, creation_date, exit_mailing_id, mediatype) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP, ?, ?)";
						update(logger,
							insertSql,
							entry.getMailinglistID(),
							entry.getCustomerID(),
							entry.getUserType(),
							entry.getUserStatus(),
							entry.getUserRemark(),
							entry.getExitMailingID(),
							entry.getMediaType());
						return true;
					}
				} else {
					return false;
				}
			}
		} catch (Exception e) {
			return false;
		}
	}

	private final boolean checkAssignedProfileFieldIsSet(final BindingEntry entry, final int companyID) {
		final MediaTypes mediaType = MediaTypes.getMediaTypeForCode(entry.getMediaType());
		
		if(mediaType == null) {
			return true;
		} else {
			if(mediaType.getAssignedProfileField() == null) {
				return true;
			} else {
				final Map<String, Object> map = this.recipientDao.getCustomerDataFromDb(companyID, entry.getCustomerID()); 
				
				final Object value = map.get(mediaType.getAssignedProfileField());
				
				return value != null && StringUtils.isNotEmpty(value.toString());
			}
		}
	}
	
	@Override
	@DaoUpdateReturnValueCheck
	public boolean updateStatus(BindingEntry entry, @VelocityCheck int companyID) {
		try {
			if (companyID <= 0) {
				return false;
			}
			
			int touchedLines;
			
			if (DbUtilities.containsColumnName(getDataSource(), "customer_" + companyID + "_binding_tbl", "referrer")) {
				String sqlUpdateStatus = "UPDATE customer_" + companyID + "_binding_tbl SET user_status = ?, exit_mailing_id = ?, user_remark = ?, referrer = ?, timestamp = CURRENT_TIMESTAMP WHERE customer_id = ? AND mailinglist_id = ? AND mediatype = ?";
				touchedLines = update(logger,
					sqlUpdateStatus,
					entry.getUserStatus(),
					entry.getExitMailingID(),
					entry.getUserRemark(),
					entry.getReferrer(),
					entry.getCustomerID(),
					entry.getMailinglistID(),
					entry.getMediaType());
			} else {
				String sqlUpdateStatus = "UPDATE customer_" + companyID + "_binding_tbl SET user_status = ?, exit_mailing_id = ?, user_remark = ?, timestamp = CURRENT_TIMESTAMP WHERE customer_id = ? AND mailinglist_id = ? AND mediatype = ?";
				touchedLines = update(logger,
					sqlUpdateStatus,
					entry.getUserStatus(),
					entry.getExitMailingID(),
					entry.getUserRemark(),
					entry.getCustomerID(),
					entry.getMailinglistID(),
					entry.getMediaType());
			}
			
			return touchedLines >= 1;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	@DaoUpdateReturnValueCheck
	public boolean optOutEmailAdr(String email, @VelocityCheck int companyID) {
		String operator;
		if (companyID <= 0) {
			return false;
		}
		if (email.contains("%") || email.contains("_")) {
			operator = "LIKE";
		} else {
			operator = "=";
		}

		try {
			String sql = "UPDATE customer_" + companyID + "_binding_tbl SET user_status = ? WHERE customer_id IN (SELECT customer_id FROM customer_" + companyID + "_tbl WHERE email " + operator + " ?)";
			int touchedLines = update(logger, sql, UserStatus.AdminOut.getStatusCode(), email);
			return touchedLines >= 1;
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	@DaoUpdateReturnValueCheck
	public boolean addTargetsToMailinglist(@VelocityCheck int companyID, int mailinglistID, ComTarget target) {
		try {
			if (companyID <= 0) {
				return false;
			}
			String sql = "INSERT INTO customer_" + companyID + "_binding_tbl (customer_id, mailinglist_id, user_type, user_status, user_remark, timestamp, exit_mailing_id, creation_date, mediatype) (SELECT cust.customer_id, " + mailinglistID + ", '" + UserType.World.getTypeCode() + "', 1, " + "'From Target " + target.getId() + "', CURRENT_TIMESTAMP, 0, CURRENT_TIMESTAMP, 0 FROM customer_" + companyID + "_tbl cust WHERE " + target.getTargetSQL() + ")";
			update(logger, sql);
			return true;
		} catch (Exception e3) {
			return false;
		}
	}

	@Override
	public boolean getUserBindingFromDB(BindingEntry entry, @VelocityCheck int companyID) {
		String sqlGetBinding = "SELECT * FROM customer_" + companyID + "_binding_tbl WHERE mailinglist_id = ? AND customer_id = ? AND mediatype = ?";
		List<BindingEntry> list = select(logger, sqlGetBinding, new BindingEntry_RowMapper(this), entry.getMailinglistID(), entry.getCustomerID(), entry.getMediaType());
		if (list.size() > 0) {
			BindingEntry foundEntry = list.get(0);
			entry.setUserType(foundEntry.getUserType());
            entry.setUserStatus(foundEntry.getUserStatus());
            entry.setUserRemark(foundEntry.getUserRemark());
            entry.setReferrer(foundEntry.getReferrer());
            entry.setChangeDate(foundEntry.getChangeDate());
            entry.setExitMailingID(foundEntry.getExitMailingID());
            entry.setCreationDate(foundEntry.getCreationDate());
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean exist(int customerId, @VelocityCheck int companyId, int mailinglistId, int mediatype) {
		String sql = "SELECT COUNT(*) FROM customer_" + companyId + "_binding_tbl WHERE customer_id = ? AND mailinglist_id = ? AND mediatype = ?";
		return selectInt(logger, sql, customerId, mailinglistId, mediatype) > 0;
	}

	@Override
	public boolean exist(@VelocityCheck int companyId, int mailinglistId) {
		String sql = "SELECT COUNT(*) FROM customer_" + companyId + "_binding_tbl WHERE mailinglist_id = ?";
		return selectInt(logger, sql, mailinglistId) > 0;
	}

	@Override
	@DaoUpdateReturnValueCheck
	public void delete(int customerId, @VelocityCheck int companyId, int mailinglistId, int mediatype) {
		String sql = "DELETE FROM customer_" + companyId + "_binding_tbl WHERE customer_id = ? AND mailinglist_id = ? AND mediatype = ?";
		update(logger, sql, customerId, mailinglistId, mediatype);
	}

	@Override
	public List<BindingEntry> getBindings(@VelocityCheck int companyID, int recipientID) {
		String sql = "SELECT customer_id, mailinglist_id, mediatype, user_type, user_status, timestamp, exit_mailing_id, user_remark, creation_date FROM customer_" + companyID + "_binding_tbl WHERE customer_id = ?";
		return select(logger, sql, new BindingEntry_RowMapper(this), recipientID);
	}

	@Override
	public List<CompositeBindingEntry> getCompositeBindings(@VelocityCheck int companyID, int recipientID) {
		String bindingTable = "customer_" + companyID + "_binding_tbl";
		String recipientTable = "customer_" + companyID + "_tbl";
		String mailinglistTable = "mailinglist_tbl";

		StringBuilder compositeBindingsQuery = new StringBuilder("SELECT bin.*,");
		compositeBindingsQuery.append(" ml.mailinglist_id AS ml_mailinglist_id, ml.company_id AS ml_company_id,");
		compositeBindingsQuery.append(" ml.shortname AS ml_shortname, ml.description AS ml_description,");
		compositeBindingsQuery.append(" ml.change_date AS ml_change_date, ml.creation_date AS ml_creation_date,");
		compositeBindingsQuery.append(" ml.deleted AS ml_deleted");
		compositeBindingsQuery.append(" FROM ").append(bindingTable).append(" bin");
		compositeBindingsQuery.append(" INNER JOIN ").append(recipientTable).append(" rec");
		compositeBindingsQuery.append(" ON bin.customer_id = rec.customer_id");
		compositeBindingsQuery.append(" LEFT JOIN ").append(mailinglistTable).append(" ml");
		compositeBindingsQuery.append(" ON bin.mailinglist_id = ml.mailinglist_id");
		compositeBindingsQuery.append(" WHERE bin.customer_id = ?");

		CompositeBindingEntryRowMapperWithMailinglist compositeBindingEntryRowMapper =
				new CompositeBindingEntryRowMapperWithMailinglist("ml_");

		return select(logger, compositeBindingsQuery.toString(), compositeBindingEntryRowMapper, recipientID);
	}

	@Override
	@DaoUpdateReturnValueCheck
	public void updateBindingStatusByEmailPattern(@VelocityCheck int companyId, String emailPattern, int userStatus, String remark) throws BindingEntryDaoException {
		String sql = "UPDATE customer_" + companyId + "_binding_tbl " + "SET user_status = ?, user_remark = ?, timestamp = CURRENT_TIMESTAMP WHERE customer_id IN (SELECT customer_id FROM customer_" + companyId + "_tbl WHERE email LIKE ?)";
		update(logger, sql, userStatus, remark, emailPattern);
	}
	
	@Override
	public void lockBindings(int companyId, List<SimpleEntry<Integer, Integer>> cmPairs) {
		StringBuffer selForUpd = new StringBuffer("select * from customer_" + companyId + "_binding_tbl where (customer_id,mailinglist_id) in (");
		boolean first = true;
		for (SimpleEntry<Integer, Integer> entry : cmPairs) {
			if (!first) {
				selForUpd.append(",");
			}
			selForUpd.append("("+entry.getKey()+","+entry.getValue()+")");
			first = false;
		}
		selForUpd.append(") for update");
		select(logger, selForUpd.toString());
	}

	private String getBindingTableName(int companyId) {
		return String.format("customer_%d_binding_tbl", companyId);
	}

	private class PlainBindingEntryRowMapper implements RowMapper<PlainBindingEntry> {

		@Override
		public PlainBindingEntry mapRow(ResultSet resultSet, int i) throws SQLException {
			PlainBindingEntry plainBindingEntry = new PlainBindingEntryImpl();

			plainBindingEntry.setCustomerId(resultSet.getInt("customer_id"));
			plainBindingEntry.setMailingListId(resultSet.getInt("mailinglist_id"));
			plainBindingEntry.setMediaType(resultSet.getInt("mediatype"));
			plainBindingEntry.setUserType(resultSet.getString("user_type"));
			plainBindingEntry.setUserStatus(resultSet.getInt("user_status"));
			plainBindingEntry.setTimestamp(resultSet.getTimestamp("timestamp"));
			plainBindingEntry.setExitMailingId(resultSet.getInt("exit_mailing_id"));
			plainBindingEntry.setUserRemark(resultSet.getString("user_remark"));
			plainBindingEntry.setCreationDate(resultSet.getTimestamp("creation_date"));

			return plainBindingEntry;
		}
	}

	protected class BindingEntry_RowMapper implements RowMapper<BindingEntry> {
		private ComBindingEntryDao bindingEntryDao;
		
		public BindingEntry_RowMapper(ComBindingEntryDao bindingEntryDao) {
			this.bindingEntryDao = bindingEntryDao;
		}
		
		@Override
		public BindingEntry mapRow(ResultSet resultSet, int row) throws SQLException {
			BindingEntry readEntry = new BindingEntryImpl();
			readEntry.setBindingEntryDao(bindingEntryDao);
			
			readEntry.setCustomerID(resultSet.getInt("customer_id"));
			readEntry.setMailinglistID(resultSet.getInt("mailinglist_id"));
			readEntry.setMediaType(resultSet.getInt("mediatype"));
			readEntry.setUserType(resultSet.getString("user_type"));
			readEntry.setUserStatus(resultSet.getInt("user_status"));
			readEntry.setChangeDate(resultSet.getTimestamp("timestamp"));
			readEntry.setExitMailingID(resultSet.getInt("exit_mailing_id"));
			if (resultSet.wasNull()) {
				readEntry.setExitMailingID(0);
			}
			readEntry.setUserRemark(resultSet.getString("user_remark"));
			if (DbUtilities.resultsetHasColumn(resultSet, "referrer")) {
				readEntry.setReferrer(resultSet.getString("referrer"));
			}
			readEntry.setCreationDate(resultSet.getTimestamp("creation_date"));

			return readEntry;
		}
	}

	public static class CompositeBindingEntryRowMapperWithMailinglist implements RowMapper<CompositeBindingEntry> {

		private static final String DEFAULT_MAILING_LIST_PREFIX = "ml_";

		private final String columnPrefix;
		private final MailinglistDaoImpl.MailinglistRowMapper mailinglistRowMapper;

		public CompositeBindingEntryRowMapperWithMailinglist(){
			columnPrefix = StringUtils.EMPTY;
			mailinglistRowMapper = new MailinglistDaoImpl.MailinglistRowMapper(DEFAULT_MAILING_LIST_PREFIX);
		}

		public CompositeBindingEntryRowMapperWithMailinglist(String mailinglistColumnPrefix) {
			columnPrefix = StringUtils.EMPTY;
			mailinglistRowMapper = new MailinglistDaoImpl.MailinglistRowMapper(mailinglistColumnPrefix);
		}

		@Override
		public CompositeBindingEntry mapRow(ResultSet resultSet, int rowNum) throws SQLException {
			CompositeBindingEntry compositeBindingEntry = new CompositeBindingEntryImpl();

			// mailinglist mapping
			Mailinglist mailinglist = mailinglistRowMapper.mapRow(resultSet, rowNum);
			compositeBindingEntry.setMailingList(mailinglist.getId() > 0 ? mailinglist : null);

			// recipient mapping. This RowMapper adds just Mailinglist entity.
			compositeBindingEntry.setRecipient(null);

			// bindingEntry mapping
			compositeBindingEntry.setCustomerId(resultSet.getInt(columnPrefix + "customer_id"));
			compositeBindingEntry.setMailingListId(resultSet.getInt(columnPrefix + "mailinglist_id"));
			compositeBindingEntry.setMediaType(resultSet.getInt(columnPrefix + "mediatype"));
			compositeBindingEntry.setUserType(resultSet.getString(columnPrefix + "user_type"));
			compositeBindingEntry.setUserStatus(resultSet.getInt(columnPrefix + "user_status"));
			compositeBindingEntry.setTimestamp(resultSet.getTimestamp(columnPrefix + "timestamp"));
			compositeBindingEntry.setExitMailingId(resultSet.getInt(columnPrefix + "exit_mailing_id"));
			compositeBindingEntry.setUserRemark(resultSet.getString(columnPrefix + "user_remark"));
			compositeBindingEntry.setCreationDate(resultSet.getTimestamp(columnPrefix + "creation_date"));

			return compositeBindingEntry;
		}
	}
}
