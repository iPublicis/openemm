/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.reporting.birt.external.dataset;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.agnitas.beans.BindingEntry.UserType;
import org.agnitas.beans.Mailing;
import org.agnitas.emm.core.commons.util.ConfigService;
import org.agnitas.emm.core.commons.util.ConfigValue;
import org.agnitas.emm.core.velocity.VelocityCheck;
import org.agnitas.util.DateUtilities;
import org.agnitas.util.DbUtilities;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.DateUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.incrementer.MySQLMaxValueIncrementer;

import com.agnitas.emm.core.JavaMailService;
import com.agnitas.reporting.birt.external.beans.LightMailingList;
import com.agnitas.reporting.birt.external.beans.LightTarget;
import com.agnitas.reporting.birt.external.dao.LightTargetDao;
import com.agnitas.reporting.birt.external.dao.impl.LightMailingListDaoImpl;
import com.agnitas.reporting.birt.external.dao.impl.LightTargetDaoImpl;
import com.agnitas.util.LongRunningSelectResultCacheDao;

public class BIRTDataSet extends LongRunningSelectResultCacheDao {
	private static final transient Logger logger = Logger.getLogger(BIRTDataSet.class);

	public static final String DATE_PARAMETER_FORMAT = "yyyy-MM-dd";
	public static final String DATE_PARAMETER_FORMAT_WITH_HOUR = "yyyy-MM-dd:H";
	public static final String DATE_PARAMETER_FORMAT_WITH_HOUR2 = "yyyy-MM-dd:HH";
	public static final String DATE_PARAMETER_FORMAT_WITH_SECOND = "yyyy-MM-dd HH:mm:ss.SSS";
	
	private JdbcTemplate embeddedJdbcTemplate;
	private DataSource embeddedDataSource;
 
	public Connection getConnection() throws SQLException {
		return getDataSource().getConnection();
	}
	
	@Override
	public DataSource getDataSource() {
		DataSource datasource = super.getDataSource();
		
		if (datasource != null) {
			return datasource;
		} else {
			try {
				datasource = (DataSource) new InitialContext().lookup("java:comp/env/jdbc/" + ConfigService.getInstance().getValue(ConfigValue.EmmDbJndiName));
				setDataSource(datasource);
				return datasource;
			} catch (Exception e) {
				logger.error("Cannot find datasource in JNDI context: " + e.getMessage(), e);
				throw new RuntimeException("Cannot find datasource in JNDI context: " + e.getMessage(), e);
			}
		}
	}
	
	/**
	 * Dependency injection method
	 * @param embeddedDataSource to be used by this dao object for temporary data storage
	 */
	@Required
	public final void setEmbeddedDatasource(DataSource embeddedDataSource) {
		/*
		 * Keep this method final to avoid problems with
		 * overriding in subclasses!
		 */

		this.embeddedDataSource = embeddedDataSource;
	}

	private DataSource getEmbeddedDatasource() throws Exception {
		if (embeddedDataSource != null) {
			return embeddedDataSource;
		} else {
			try {
				Context initialContext = new InitialContext();
				embeddedDataSource = (DataSource) initialContext.lookup("java:comp/env/jdbc/" + ConfigService.getInstance().getValue(ConfigValue.TempDbJndiName));
				return embeddedDataSource;
			} catch (NamingException e) {
				throw new Exception("Cannot create temporary database connection, check your JNDI-settings: " + e.getMessage());
			} catch (Exception e) {
				throw new Exception("Cannot create temporary database connection: " + e.getMessage());
			}
		}
	}
	
	private JdbcTemplate getEmbeddedJdbcTemplate() throws Exception {
    	if (embeddedJdbcTemplate == null) {
    		embeddedJdbcTemplate = new JdbcTemplate(getEmbeddedDatasource());
		}
		
		return embeddedJdbcTemplate;
    }
    
	protected ConfigService getConfigService() {
		return BIRTDataSetHelper.getInstance().getConfigService(getDataSource());
	}
	
	protected JavaMailService getJavaMailService() {
		return BIRTDataSetHelper.getInstance().getJavaMailService(getDataSource());
	}
	
	
	protected class DateFormats {
		private boolean hourScale;
		private String sqlFormatDate;
		private SimpleDateFormat formater;
		private String startDate;
		private String stopDate;
		private int period;
		
		public DateFormats(String startDate, String stopDate, Boolean hourScale) {
			this.hourScale = hourScale;
			if (isOracleDB()) {
				sqlFormatDate = this.hourScale ? "YYYY-MM-DD:HH24" : "YYYY-MM-DD";
			} else {
				sqlFormatDate = this.hourScale ? "%Y-%m-%d:%H" : "%Y-%m-%d";
			}
			if (this.hourScale) {
				formater = new SimpleDateFormat("yyyy-MM-dd:H");
			} else {
				formater = new SimpleDateFormat("yyyy-MM-dd");
			}
			if (startDate.indexOf(":") != -1  && !hourScale) {
				this.startDate = startDate.substring(0, startDate.indexOf(":"));
			} else {
				this.startDate = startDate;
			}
			if (stopDate.indexOf(":") != -1  && !hourScale) {
				this.stopDate = stopDate.substring(0, stopDate.indexOf(":"));
			} else {
				this.stopDate = stopDate;
			}
			if (isDateSlice()) {
				calcPeriod();
			}
		}
		
		public DateFormats() {
			this("", "", false);
		}
		
		private void calcPeriod() {
			try {
				Date sdt = formater.parse(startDate);
				Date edt = formater.parse(stopDate);
				if (hourScale) {
					period = 1 + BigDecimal.valueOf(TimeUnit.HOURS.convert(edt.getTime() - sdt.getTime(), TimeUnit.MILLISECONDS)).intValue();
				} else {
					period = 1 + BigDecimal.valueOf(TimeUnit.DAYS.convert(edt.getTime() - sdt.getTime(), TimeUnit.MILLISECONDS)).intValue();
				}
			} catch (ParseException e) {
				e.printStackTrace();
				period = 0;
			}
		}
		
		public boolean isHourScale() {
			return hourScale;
		}
		
		public String getSqlFormatDate() {
			return sqlFormatDate;
		}
		
		public SimpleDateFormat getFormater() {
			return formater;
		}
		
		public String getStartDate() {
			return startDate;
		}
		
		public String getStopDate() {
			return stopDate;
		}
		
		public int getPeriod() {
			return period;
		}
		
		public boolean isDateSlice() {
			return !startDate.isEmpty();
		}
		
		public Date getStartDateAsDate() {
			try {
				return formater.parse(startDate);
			} catch (ParseException e) {
				e.printStackTrace();
				return null;
			}
		}
		
		public Date getStopDateAsDate() {
			try {
				return formater.parse(stopDate);
			} catch (ParseException e) {
				e.printStackTrace();
				return null;
			}
		}
	}
	
	protected List<String> getTargetSql(String selectedTargets, @VelocityCheck Integer companyId) {
		List<String> targetSql = new ArrayList<>();
		try {
			if (!StringUtils.isEmpty(selectedTargets)) {
				LightTargetDao lightTargetDao = new LightTargetDaoImpl();
				((LightTargetDaoImpl) lightTargetDao).setDataSource(getDataSource());
				
				List<LightTarget> targets = lightTargetDao.getTargets(Arrays.asList(selectedTargets.split(",")), companyId);
				for (LightTarget target : targets) {
					if (!StringUtils.isEmpty(target.getTargetSQL())) {
						targetSql.add(target.getTargetSQL());
					}
				}
			}
		} catch (Exception e) {
			logger.error("Error occured: " + e.getMessage(), e);
		}
		return targetSql;
	}
	
	protected List<LightTarget> getTargets(String selectedTargets, @VelocityCheck Integer companyID) {
		try {
			if (!StringUtils.isEmpty(selectedTargets)) {
				LightTargetDao lightTargetDao = new LightTargetDaoImpl();
				((LightTargetDaoImpl) lightTargetDao).setDataSource(getDataSource());
				
				List<LightTarget> targets = lightTargetDao.getTargets(Arrays.asList(selectedTargets.split(",")), companyID);
				return targets;
			}
		} catch (Exception e) {
			logger.error("Error occured: " + e.getMessage(), e);
		}
		return null;
	}
	
	protected List<LightTarget> getTargets(List<String> selectedTargets, @VelocityCheck Integer companyID) {
		try {
			if (selectedTargets != null && !selectedTargets.isEmpty()) {
				LightTargetDao lightTargetDao = new LightTargetDaoImpl();
				((LightTargetDaoImpl) lightTargetDao).setDataSource(getDataSource());
				
				List<LightTarget> targets = lightTargetDao.getTargets(selectedTargets, companyID);
				return targets;
			}
		} catch (Exception e) {
			logger.error("Error occured: " + e.getMessage(), e);
		}
		return null;
	}
	
	protected LightTarget getTarget(int targetID, @VelocityCheck int companyID) {
		try {
			LightTargetDao lightTargetDao = new LightTargetDaoImpl();
			((LightTargetDaoImpl) lightTargetDao).setDataSource(getDataSource());
			
			return lightTargetDao.getTarget(targetID, companyID);
		} catch (Exception e) {
			logger.error("Error occured: " + e.getMessage(), e);
		}
		return null;
	}
	
	protected String getTargetSqlString(String selectedTargets, @VelocityCheck Integer companyId) {
		List<String> sqlList = getTargetSql(selectedTargets, companyId);
		StringBuffer targetSql = new StringBuffer();
		if (CollectionUtils.isNotEmpty(sqlList)) {
			targetSql.append("(");
			for (String sql : sqlList) {
				targetSql.append("(" + sql + ") OR ");
			}
			int len = targetSql.length();
			targetSql.delete(len - 4, len);
			targetSql.append(")");
		}
		return targetSql.toString();
	}
	
	protected int getNextTmpID() {
		try {
			if (isOracleDB()) {
				return selectInt(logger, "SELECT birt_report_tmp_tbl_seq.NEXTVAL FROM DUAL");
			} else {
				return new MySQLMaxValueIncrementer(getDataSource(), "birt_report_tmp_tbl_seq", "value").nextIntValue();
			}
		} catch (Exception e) {
			logger.error("Could not get next tmpID from database ! ", e);
			return -1;
		}
	}
	
	public boolean isMailingTrackingActivated(@VelocityCheck int companyID) {
		String query = "SELECT COALESCE(mailtracking, 0) FROM company_tbl WHERE company_id = ?";
		return select(logger, query, Integer.class, companyID) != 0 ? true : false;
	}
	
	public boolean isMailingTrackingDataAvailable(int mailingID, @VelocityCheck int companyID) {
		return isMailingTrackingActivated(companyID) && isTrackingExists(mailingID, companyID);
	}
	
	public boolean isTrackingExists(int mailingID, @VelocityCheck int companyID) {
		StringBuilder queryBuilder = new StringBuilder();
		if (getMailingType(mailingID) == Mailing.TYPE_INTERVAL) {
			queryBuilder
					.append("SELECT COUNT(*)")
					.append(" FROM interval_track_").append(companyID).append("_tbl mt")
					.append(" WHERE mt.mailing_id = ?");
		} else {
			queryBuilder
					.append("SELECT COUNT(*)")
					.append(" FROM success_").append(companyID).append("_tbl")
					.append(" WHERE mailing_id = ?");
		}
		return select(logger, queryBuilder.toString(), Integer.class, mailingID) > 0;
	}
	
	public boolean isMailingBouncesExpire(@VelocityCheck int companyId, int mailingId) {
		Map<Integer, Boolean> expireMap = getMailingBouncesExpire(companyId, mailingId + "");
		return expireMap.get(mailingId);
	}
	
	public Map<Integer, Boolean> getMailingBouncesExpire(@VelocityCheck int companyId, String mailingIds) {
		HashMap<Integer, Boolean> expireMap = new HashMap<>();
		String query = "SELECT expire_bounce FROM company_tbl WHERE company_id = ?";
		int bounceExpire =  selectInt(logger, query, companyId);
		if (bounceExpire == 0) {
			bounceExpire = getConfigService().getIntegerValue(ConfigValue.BounceCleanupAgeInDays, companyId);
		}
		String sql;
		if (isOracleDB()) {
			sql = "SELECT (sysdate - senddate) mail_age, mailing_id FROM maildrop_status_tbl " + "WHERE company_id = ? AND mailing_id IN (" + mailingIds
					+ ") ORDER BY DECODE(status_field, " + "'W', 1, 'R', 2, 'D', 2, 'E', 3, 'C', 3, 'T', 4, 'A', 4, 5), status_id DESC";
		} else {
			sql = "SELECT TIMESTAMPDIFF(DAY, senddate, CURRENT_TIMESTAMP) mail_age, mailing_id FROM maildrop_status_tbl " + "WHERE company_id = ? AND mailing_id IN ("
					+ mailingIds + ") ORDER BY  CASE status_field WHEN 'W' "
					+ "THEN 1 WHEN 'R' THEN 2 WHEN 'D' THEN 2 WHEN 'E' THEN 3 WHEN 'C' THEN 3 WHEN 'T' THEN 4 WHEN 'A' " + "THEN 4 ELSE 5 END, status_id DESC";
		}
		List<Map<String, Object>> result = select(logger, sql, companyId);
		for (Map<String, Object> row : result) {
			int mailingId = ((Number) row.get("mailing_id")).intValue();
			if (expireMap.get(mailingId) == null) {
				int mailAge = ((Number) row.get("mail_age")).intValue();
				expireMap.put(mailingId, bounceExpire < mailAge);
			}
		}
		String[] ids = mailingIds.split(",");
		for (String id : ids) {
			int mailingId = Integer.parseInt(id.trim());
			if (expireMap.get(mailingId) == null) {
				expireMap.put(mailingId, false);
			}
		}
		return expireMap;
	}
	
	public void dropTempTable(int tempTableID) throws Exception {
		try {
			String dropTableSQL = "DROP TABLE tmp_report_aggregation_" + tempTableID + "_tbl";
			getEmbeddedJdbcTemplate().update(dropTableSQL);
		} catch (NamingException e) {
			logger.error("Could not drop temporary table, check your JNDI-settings", e);
		}
	}
	
	public List<Integer> getAutoOptimizationMailings(int optimizationID, int companyID) {
        List<Integer> result = new ArrayList<>();
		String query = "SELECT group1_id, group2_id, group3_id, group4_id, group5_id, final_mailing_id FROM auto_optimization_tbl WHERE optimization_id=? and company_id=?";
        List<Map<String, Object>> optimizationElements = select(logger, query, optimizationID, companyID);
        if (optimizationElements.size() > 0) {
        	Map<String, Object> map = optimizationElements.get(0);
            for (int i = 1; i <= 5; i++ ){
                int groupId = ((Number) map.get("group" + i + "_id")).intValue();
                result.add(groupId);
            }
            result.add(map.get("final_mailing_id") == null ? 0 : ((Number) map.get("final_mailing_id")).intValue());
        }
        return result;
    }
	
    protected List<LightMailingList> getMailingLists(List<Integer> mailingListIds, @VelocityCheck Integer companyID) {
    	if (mailingListIds != null && mailingListIds.size() > 0) {
            List<LightMailingList> mailingLists = new LightMailingListDaoImpl(getDataSource()).getMailingLists(mailingListIds, companyID);
            return mailingLists;
        } else {
            return null;
        }
    }
	
    protected List<Integer> parseCommaSeparatedIds(String stringOfIds) {
        List<Integer> ids = new LinkedList<>();
        try {
            if (StringUtils.isNotEmpty(stringOfIds)) {
                for (String id : stringOfIds.split(",")) {
                    ids.add(new Integer(id.trim()));
                }
            }
        } catch (Exception e) {
            logger.error("Error occured: " + e.getMessage(), e);
        }
        return ids;
    }
	
    protected int getMailingType(int mailingId) {
        return select(logger, "SELECT mailing_type FROM mailing_tbl WHERE mailing_id = ?", Integer.class, mailingId);
    }
	
    protected int getNumberSentMailings(@VelocityCheck int companyID, int mailingID, String recipientsType, String targetSql, String startDateString, String endDateString) throws Exception {
    	Date startDate = null;
    	Date endDate = null;
        if (StringUtils.isNotBlank(startDateString) && StringUtils.isNotBlank(endDateString)) {
			if (startDateString.contains(":")) {
				startDate = new SimpleDateFormat(DATE_PARAMETER_FORMAT_WITH_HOUR).parse(startDateString);
			} else {
				startDate = new SimpleDateFormat(DATE_PARAMETER_FORMAT).parse(startDateString);
			}
			if (endDateString.contains(":")) {
				endDate = DateUtils.addHours(new SimpleDateFormat(DATE_PARAMETER_FORMAT_WITH_HOUR).parse(endDateString), 1);
			} else {
				endDate = DateUtils.addDays(new SimpleDateFormat(DATE_PARAMETER_FORMAT).parse(endDateString), 1);
			}
		}
        
        int mailingType = getMailingType(mailingID);
        if (mailingType == Mailing.TYPE_INTERVAL) {
            StringBuilder queryBuilder = new StringBuilder("SELECT COUNT(DISTINCT track.customer_id) FROM");
    		List<Object> parameters = new ArrayList<>();
            
            if (targetSql != null && targetSql.contains("cust.")) {
    			queryBuilder.append(" customer_" + companyID + "_tbl cust,");
    		}
         
        	queryBuilder.append(" interval_track_" + companyID + "_tbl track");
        	queryBuilder.append(" WHERE track.mailing_id = ?");
    		parameters.add(mailingID);
        	if (startDate != null && endDate != null) {
    			queryBuilder.append(" AND (? <= track.send_date AND track.send_date < ?)");
				parameters.add(startDate);
				parameters.add(endDate);
    		}
            
            if (targetSql != null && targetSql.contains("cust.")) {
    			queryBuilder.append(" AND cust.customer_id = track.customer_id");
    		}
    		
    		if (targetSql != null && StringUtils.isNotBlank(targetSql) && !targetSql.replace(" ", "").equals("1=1")) {
    			queryBuilder.append(" AND (").append(targetSql).append(")");
    		}
            
            return selectIntWithDefaultValue(logger, queryBuilder.toString(), 0, parameters.toArray(new Object[0]));
        } else {
        	if (DbUtilities.checkIfTableExists(getDataSource(), "mailtrack_" + companyID + "_tbl") && isMailingTrackingActivated(companyID) && !isMailTrackingExpired(companyID, mailingID)) {
                StringBuilder queryBuilder = new StringBuilder("SELECT COUNT(DISTINCT track.customer_id) FROM");
        		List<Object> parameters = new ArrayList<>();
                
                if (targetSql != null && targetSql.contains("cust.")) {
        			queryBuilder.append(" customer_" + companyID + "_tbl cust,");
        		}
             
            	queryBuilder.append(" mailtrack_" + companyID + "_tbl track");
            	queryBuilder.append(" WHERE track.mailing_id = ?");
        		parameters.add(mailingID);

            	if (startDate != null && endDate != null) {
        			queryBuilder.append(" AND (? <= track.timestamp AND track.timestamp < ?)");
    				parameters.add(startDate);
    				parameters.add(endDate);
        		}
                
                if (targetSql != null && targetSql.contains("cust.")) {
        			queryBuilder.append(" AND cust.customer_id = track.customer_id");
        		}
        		
        		if (targetSql != null && StringUtils.isNotBlank(targetSql) && !targetSql.replace(" ", "").equals("1=1")) {
        			queryBuilder.append(" AND (").append(targetSql).append(")");
        		}
                
                return selectIntWithDefaultValue(logger, queryBuilder.toString(), 0, parameters.toArray(new Object[0]));
        	} else if (targetSql == null || StringUtils.isBlank(targetSql) || targetSql.replace(" ", "").equals("1=1")) {
        		// mailing_account_tbl has no customerids and therefor cannot be used for targetgroup specific numbers
                StringBuilder queryBuilder = new StringBuilder("SELECT SUM(no_of_mailings) FROM mailing_account_tbl WHERE mailing_id = ?");
        		List<Object> parameters = new ArrayList<>();
        		parameters.add(mailingID);
                
                if (mailingType == Mailing.TYPE_INTERVAL) {
                    queryBuilder.append(" AND status_field = 'D'");
                } else if (CommonKeys.TYPE_ADMIN_AND_TEST.equals(recipientsType)) {
                    queryBuilder.append(" AND status_field IN ('A', 'T')");
                } else {
            		queryBuilder.append(" AND status_field NOT IN ('A', 'T')");
                }

            	if (startDate != null && endDate != null) {
        			queryBuilder.append(" AND (? <= timestamp AND timestamp < ?)");
    				parameters.add(startDate);
    				parameters.add(endDate);
        		}
          
        		return selectIntWithDefaultValue(logger, queryBuilder.toString(), 0, parameters.toArray(new Object[0]));
        	} else {
        		return -1;
        	}
        }
	}
	
    protected int selectNumberOfDeliveredMails(@VelocityCheck int companyID, int mailingID, String recipientsType, String targetSql, String startDateString, String endDateString) throws Exception {
    	// Do not count by "distinct customer_id", because event based mailings (birthday mailings etc.) might be delivered multiple times
        StringBuilder queryBuilder = new StringBuilder("SELECT COUNT(s.customer_id) AS counter FROM success_" + companyID + "_tbl s");
		List<Object> parameters = new ArrayList<>();
        
        if (targetSql != null && targetSql.contains("cust.")) {
			queryBuilder.append(", customer_" + companyID + "_tbl cust");
			queryBuilder.append(" WHERE s.mailing_id = ? AND s.customer_id = cust.customer_id");
		} else {
			queryBuilder.append(" WHERE s.mailing_id = ?");
		}
		parameters.add(mailingID);
  
		if (StringUtils.isNotBlank(startDateString) && StringUtils.isNotBlank(endDateString)) {
			queryBuilder.append(" AND (? <= s.timestamp AND s.timestamp < ?)");
			if (startDateString.contains(":")) {
				parameters.add(new SimpleDateFormat(DATE_PARAMETER_FORMAT_WITH_HOUR).parse(startDateString));
			} else {
				parameters.add(new SimpleDateFormat(DATE_PARAMETER_FORMAT).parse(startDateString));
			}
			if (endDateString.contains(":")) {
				parameters.add(DateUtils.addHours(new SimpleDateFormat(DATE_PARAMETER_FORMAT_WITH_HOUR).parse(endDateString), 1));
			} else {
				parameters.add(DateUtils.addDays(new SimpleDateFormat(DATE_PARAMETER_FORMAT).parse(endDateString), 1));
			}
		}
		
		if (targetSql != null && StringUtils.isNotBlank(targetSql) && !targetSql.replace(" ", "").equals("1=1")) {
			queryBuilder.append(" AND (").append(targetSql).append(")");
		}

		if (CommonKeys.TYPE_WORLDMAILING.equals(recipientsType)) {
			queryBuilder.append(" AND EXISTS (SELECT 1 FROM customer_" + companyID + "_binding_tbl bind WHERE bind.user_type IN ('" + UserType.World.getTypeCode() + "', '" + UserType.WorldVIP.getTypeCode() + "') AND bind.mailinglist_id = (SELECT mtbl.mailinglist_id FROM mailing_tbl mtbl WHERE mtbl.mailing_id = s.mailing_id) AND bind.customer_id = b.customer_id)");
        } else if(CommonKeys.TYPE_ADMIN_AND_TEST.equals(recipientsType)) {
        	queryBuilder.append(" AND EXISTS (SELECT 1 FROM customer_" + companyID + "_binding_tbl bind WHERE bind.user_type IN ('" + UserType.Admin.getTypeCode() + "', '" + UserType.TestUser.getTypeCode() + "', '" + UserType.TestVIP.getTypeCode() + "') AND bind.mailinglist_id = (SELECT mtbl.mailinglist_id FROM mailing_tbl mtbl WHERE mtbl.mailing_id = s.mailing_id) AND bind.customer_id = b.customer_id)");
        }

		List<Map<String, Object>> result = selectLongRunning(logger, queryBuilder.toString(), parameters.toArray(new Object[0]));

		return ((Number) result.get(0).get("counter")).intValue();
    }
	
    public String getAccountName(String accountId) {
        String accountName = select(logger, "SELECT shortname FROM company_tbl WHERE company_id = ?", String.class, accountId);
        accountName = accountName == null ? "" : accountName.trim();
        return accountName;
    }
	
    public String getReportName(String reportId) {
        String reportName = select(logger, "SELECT shortname FROM birtreport_tbl WHERE report_id = ?", String.class, reportId);
        reportName = reportName == null ? "" : reportName.trim();
        return reportName;
    }
    
    public boolean successTableActivated(@VelocityCheck int companyId) {
    	return select(logger, "SELECT COALESCE(mailtracking, 0) FROM company_tbl WHERE company_id = ?", Integer.class, companyId) != 0 ? true : false;
    }
	
	@Override
	protected void logSqlError(Exception e, Logger logger, String statement, Object... parameter) {
		getJavaMailService().sendExceptionMail("SQL: " + statement + "\nParameter: " + getParameterStringList(parameter), e);
    	if (parameter != null && parameter.length > 0) {
    		logger.error("Error: " + e.getMessage() + "\nSQL:" + statement + "\nParameter: " + getParameterStringList(parameter), e);
    	} else {
    		logger.error("Error: " + e.getMessage() + "\nSQL:" + statement, e);
    	}
	}
	
	/**
	 * Gets data from embedded derby database.
	 * Logs the statement and parameter in debug-level, executes select and logs error.
	 * 
	 * @param logger
	 * @param statement
	 * @param parameter
	 * @return List of db entries represented as caseinsensitive maps
	 * @throws Exception
	 */
	protected List<Map<String, Object>> selectEmbedded(Logger logger, String statement, Object... parameter) throws Exception {
		try {
			logSqlStatement(logger, "EMBEDDED: " + statement, parameter);
			return getEmbeddedJdbcTemplate().queryForList(statement, parameter);
		} catch (DataAccessException e) {
			logSqlError(e, logger, "EMBEDDED: " + statement, parameter);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, "EMBEDDED: " + statement, parameter);
			throw e;
		}
	}
	
	/**
	 * Gets data from embedded derby database.
	 * Logs the statement and parameter in debug-level, executes select and logs error.
	 * 
	 * @param logger
	 * @param statement
	 * @param rowMapper
	 * @param parameter
	 * @return List of db entries represented as objects
	 * @throws Exception
	 */
	protected <T> List<T> selectEmbedded(Logger logger, String statement, RowMapper<T> rowMapper, Object... parameter) throws Exception {
		try {
			logSqlStatement(logger, "EMBEDDED: " + statement, parameter);
			return getEmbeddedJdbcTemplate().query(statement, rowMapper, parameter);
		} catch (DataAccessException e) {
			logSqlError(e, logger, "EMBEDDED: " + statement, parameter);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, "EMBEDDED: " + statement, parameter);
			throw e;
		}
	}
	
	/**
	 * Gets data from embedded derby database.
	 * Logs the statement and parameter in debug-level, executes select and logs error.
	 * 
	 * @param logger
	 * @param statement
	 * @param requiredType
	 * @param parameter
	 * @return single db entry as object
	 * @throws Exception
	 */
	protected <T> T selectEmbedded(Logger logger, String statement, Class<T> requiredType, Object... parameter) throws Exception {
		try {
			logSqlStatement(logger, "EMBEDDED: " + statement, parameter);
			return getEmbeddedJdbcTemplate().queryForObject(statement, requiredType, parameter);
		} catch (DataAccessException e) {
			logSqlError(e, logger, "EMBEDDED: " + statement, parameter);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, "EMBEDDED: " + statement, parameter);
			throw e;
		}
	}
	
	/**
	 * Updates data in embedded derby database.
	 * Logs the statement and parameter in debug-level, executes update and logs error.
	 * 
	 * @param logger
	 * @param statement
	 * @param parameter
	 * @return number of touched lines in db
	 * @throws Exception
	 */
	protected int updateEmbedded(Logger logger, String statement, Object... parameter) throws Exception {
		try {
			logSqlStatement(logger, "EMBEDDED: " + statement, parameter);
			int touchedLines = getEmbeddedJdbcTemplate().update(statement, parameter);
			if (logger.isDebugEnabled()) {
				logger.debug("lines changed by update: " + touchedLines);
			}
			return touchedLines;
		} catch (DataAccessException e) {
			logSqlError(e, logger, "EMBEDDED: " + statement, parameter);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, "EMBEDDED: " + statement, parameter);
			throw e;
		}
	}
	
	/**
	 * Execute ddl-statement in embedded derby database.
	 * Logs the statement and parameter in debug-level, executes a DDL SQL Statement.
	 * 
	 * @param logger
	 * @param statement
	 * @return number of touched lines in db
	 * @throws Exception
	 */
	protected void executeEmbedded(Logger logger, String statement) throws Exception {
		try {
			logSqlStatement(logger, "EMBEDDED: " + statement);
			getEmbeddedJdbcTemplate().execute(statement);
		} catch (DataAccessException e) {
			logSqlError(e, logger, "EMBEDDED: " + statement);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, "EMBEDDED: " + statement);
			throw e;
		}
	}

	/**
	 * Method to update multiple data entries at once.<br />
	 * Logs the statement and parameter in debug-level, executes update and logs error.<br />
	 * Watch out: Oracle returns value -2 (= Statement.SUCCESS_NO_INFO) per line for success with no "lines touched" info<br />
	 * 
	 * @param logger
	 * @param statement
	 * @param values
	 * @return
	 * @throws Exception
	 */
	public int[] batchupdateEmbedded(Logger logger, String statement, List<Object[]> values) throws Exception {
		try {
			logSqlStatement(logger, "EMBEDDED: " + statement, "BatchUpdateParameterList(Size: " + values.size() + ")");
			int[] touchedLines = getEmbeddedJdbcTemplate().batchUpdate(statement, values);
			if (logger.isDebugEnabled()) {
				logger.debug("lines changed by update: " + Arrays.toString(touchedLines));
			}
			return touchedLines;
		} catch (RuntimeException e) {
			logSqlError(e, logger, "EMBEDDED: " + statement, "BatchUpdateParameterList(Size: " + values.size() + ")");
			throw e;
		}
	}
    
	public boolean isMailTrackingExpired(@VelocityCheck int companyID, int mailingID) {
        int periodicallySendEntries = selectInt(logger, "SELECT COUNT(mst.mailing_id) AS count FROM maildrop_status_tbl mst JOIN mailing_tbl mt ON mst.mailing_id = mt.mailing_id WHERE mst.mailing_id = ? AND mst.status_field IN ('C', 'E', 'R', 'D') AND mt.work_status = 'mailing.status.active' AND mst.senddate < CURRENT_TIMESTAMP", mailingID);
        if (periodicallySendEntries > 0) {
        	return false;
        } else {
    		int expirePeriod = selectInt(logger, "SELECT expire_success FROM company_tbl WHERE company_id = ?", companyID);
    		if (expirePeriod == 180 && new Date().before(new Date(2019, 4, 20))) {
    			// Special hack for EMM-6366, to allow db cleanup keep the data for 180 days but do not calculate the statistics util the data is filled up
    			expirePeriod = 90;
    		}
    		if (expirePeriod <= 0) {
    			return false;
    		} else {
		        int countOfOnceSending = selectInt(logger, "SELECT COUNT(mst.mailing_id) AS count FROM maildrop_status_tbl mst WHERE mst.mailing_id = ? AND mst.status_field IN ('W') AND mst.senddate < CURRENT_TIMESTAMP AND mst.senddate >= ?", mailingID, DateUtilities.getDateOfDaysAgo(expirePeriod));
		        if (countOfOnceSending > 0) {
		        	return false;
		        } else {
		        	return true;
		        }
	        }
		}
    }
}
