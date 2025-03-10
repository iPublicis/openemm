/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package org.agnitas.dao.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.sql.Blob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import javax.sql.DataSource;

import com.agnitas.dao.DaoUpdateReturnValueCheck;
import com.agnitas.emm.core.JavaMailService;
import org.agnitas.dao.impl.mapper.StringRowMapper;
import org.agnitas.util.AgnUtils;
import org.agnitas.util.DateUtilities;
import org.agnitas.util.DbUtilities;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.support.AbstractLobCreatingPreparedStatementCallback;
import org.springframework.jdbc.object.BatchSqlUpdate;
import org.springframework.jdbc.object.SqlUpdate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.support.lob.DefaultLobHandler;
import org.springframework.jdbc.support.lob.LobCreator;

/**
 * Helper class which hides the dependency injection variables and eases some select and update actions and logging.
 * But still the datasource or the JdbcTemplate can be used directly if needed.
 * 
 * The logger of this class is not used for db actions to log, because it would hide the calling from the derived classes.
 * Therefore every simplified update and select method demands an logger delivered as parameter.
 */
public abstract class BaseDaoImpl {
	/**
	 * General logger of this class. This logger is not used for the select and update actions.
	 */
	private static final transient Logger baseDaoImplLogger = Logger.getLogger(BaseDaoImpl.class);

	private static Integer MYSQL_MAXPACKETSIZE = null;
	
	// ----------------------------------------------------------------------------------------------------------------
	// Dependency Injection

	/**
	 * Datasource to be used in this dao. JdbcTemplate uses this, too.
	 * This member variable should be set by dependency injectors like spring via the setDataSource method
	 */
	private DataSource dataSource;
	
	/**
	 * Cache variable for the dataSource vendor, so it must not be recalculated everytime.
	 * This variable may be uninitialized before the first execution of the isOracleDB method
	 */
	private static String dbVendor = null;
	
	/**
	 * Cache variable for the JdbcTemplate, so it must not be recreated everytime
	 */
	private JdbcTemplate jdbcTemplate = null;
	
	protected JavaMailService javaMailService;
	
	/**
	 * Dependency injection method
	 * @param dataSource to be used by this dao object
	 */
	@Required
	public final void setDataSource(DataSource dataSource) {
		/*
		 * Keep this method final to avoid problems with 
		 * overriding in subclasses! 
		 */

		this.dataSource = dataSource;
	}
	
	/**
	 * Auxiliary method to execute some actions on the dataSource without using JdbcTemplate
	 * @return dataSource used by this dao object
	 */
	public DataSource getDataSource() {
		return dataSource;
	}

	@Required
	public final void setJavaMailService(JavaMailService javaMailService) {
		this.javaMailService = javaMailService;
	}
	
	// ----------------------------------------------------------------------------------------------------------------
	// Business Logic

	/**
	 * Getter method for the JdbcTemplate, which uses the cache of this object
	 * @return the cached JdbcTemplate used by this object
	 */
	protected final JdbcTemplate getJdbcTemplate() {
		/*
		 * Keep this method final to avoid problems with 
		 * overriding in subclasses! 
		 */

		if (jdbcTemplate == null) {
			jdbcTemplate = new JdbcTemplate(getDataSource());
		}
		
		return jdbcTemplate;
	}
	
	/**
	 * Checks the db vendor of the dataSource and caches the result for further usage
	 * @return true if db vendor of dataSource is Oracle, false if any other vendor (e.g. mysql)
	 */
	public final boolean isOracleDB() {
		/*
		 * Keep this method final to avoid problems with 
		 * overriding in subclasses!
		 */
		
		if (dbVendor == null) {
			if (DbUtilities.checkDbVendorIsOracle(getDataSource())) {
				dbVendor = "oracle";
			} else if (DbUtilities.checkDbVendorIsMariaDB(getDataSource())) {
				dbVendor = "mariadb";
			} else {
				dbVendor = "mysql";
			}
		}
		return "oracle".equalsIgnoreCase(dbVendor);
	}
	
	public final boolean isMariaDB() {
		/*
		 * Keep this method final to avoid problems with 
		 * overriding in subclasses!
		 */
		
		if (dbVendor == null) {
			if (DbUtilities.checkDbVendorIsOracle(getDataSource())) {
				dbVendor = "oracle";
			} else if (DbUtilities.checkDbVendorIsMariaDB(getDataSource())) {
				dbVendor = "mariadb";
			} else {
				dbVendor = "mysql";
			}
		}
		return "mariadb".equalsIgnoreCase(dbVendor);
	}

	/**
	 * Logs the sql statement. This is typically used before db executions.
	 * This method is also included in select and update methods of this class, so they don't have to be called explicitly
	 * @param logger
	 * @param statement
	 * @param parameter
	 */
	protected void logSqlStatement(Logger logger, String statement, Object... parameter) {
		if (logger.isDebugEnabled()) {
			if (parameter != null && parameter.length > 0) {
				logger.debug("SQL: " + statement + "\nParameter: " + getParameterStringList(parameter));
			} else {
				logger.debug("SQL: " + statement);
			}
		}
	}

	/**
	 * Logs the sql statement and an occurred error. This is typically used after db executions.
	 * This method is also included in select and update methods of this class, so they don't have to be called explicitly
	 * 
	 * @param e
	 * @param logger
	 * @param statement
	 * @param parameter
	 */
	protected void logSqlError(Exception e, Logger logger, String statement, Object... parameter) {
		if (javaMailService != null) {
			javaMailService.sendExceptionMail("SQL: " + statement + "\nParameter: " + getParameterStringList(parameter), e);
		} else {
			logger.error("Missing javaMailService. So no erroremail was sent.");
		}
		if (parameter != null && parameter.length > 0) {
			logger.error("Error: " + e.getMessage() + "\nSQL: " + statement + "\nParameter: " + getParameterStringList(parameter), e);
		} else {
			logger.error("Error: " + e.getMessage() + "\nSQL: " + statement, e);
		}
	}
	
	protected String getParameterStringList(Object[] parameterArray) {
		if (parameterArray == null) {
			return null;
		} else {
			StringBuilder parameterStringList = new StringBuilder();
			for (Object parameter : parameterArray) {
				if (parameterStringList.length() > 0) {
					parameterStringList.append(", ");
				}
				if (parameter == null) {
					parameterStringList.append("NULL");
				} else if (parameter instanceof String) {
					parameterStringList.append("'");
					parameterStringList.append(parameter);
					parameterStringList.append("'");
				} else if (parameter instanceof Date) {
					parameterStringList.append(parameter.getClass().getName());
					parameterStringList.append(":'");
					parameterStringList.append(new SimpleDateFormat(DateUtilities.YYYY_MM_DD_HH_MM_SS_MS).format(parameter));
					parameterStringList.append("'");
				} else {
					parameterStringList.append(parameter.toString());
				}
			}
			return parameterStringList.toString();
		}
	}
	
	/**
	 * Closes SQL Statement object without throwing Exceptions.
	 * Exceptions are still loged as errors.
	 * @param statements
	 */
	protected void closeSilently(Statement... statements) {
		for (Statement statement : statements) {
			if (statement != null) {
				try {
					statement.close();
				} catch (SQLException e) {
					baseDaoImplLogger.error("Error occured: " + e.getMessage(), e);
				}
			}
		}
	}
	
	/**
	 * Closes SQL Statement object without throwing Exceptions.
	 * Exceptions are still logged as errors.
	 * @param connections JDBC connections to close
	 */
	protected void closeSilently(Connection... connections) {
		for (Connection connection : connections) {
			if (connection != null) {
				try {
					connection.close();
				} catch (SQLException e) {
					baseDaoImplLogger.error("Error occured: " + e.getMessage(), e);
				}
			}
		}
	}
	
	/**
	 * Logs the statement and parameter in debug-level, executes select and logs error.
	 * 
	 * @param logger
	 * @param statement
	 * @param parameter
	 * @return List of db entries represented as caseinsensitive maps
	 */
	protected List<Map<String, Object>> select(Logger logger, String statement, Object... parameter) {
		try {
			logSqlStatement(logger, statement, parameter);
			return getJdbcTemplate().queryForList(statement, parameter);
		} catch (DataAccessException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		}
	}
	
	/**
	 * Logs the statement and parameter in debug-level, executes select and logs error.
	 * 
	 * @param logger
	 * @param statement
	 * @param rowMapper
	 * @param parameter
	 * @return List of db entries represented as objects
	 */
	protected <T> List<T> select(Logger logger, String statement, RowMapper<T> rowMapper, Object... parameter) {
		try {
			logSqlStatement(logger, statement, parameter);
			return getJdbcTemplate().query(statement, rowMapper, parameter);
		} catch (DataAccessException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		}
	}

	/**
	 * Logs the statement and parameter in debug-level, executes select and logs error.
	 *
	 * @param logger a logger to write logs to.
	 * @param statement an sql-query to execute.
	 * @param rowHandler a callback object for results processing.
	 * @param parameter bound parameters for an sql-query.
	 */
	protected void query(Logger logger, String statement, RowCallbackHandler rowHandler, Object... parameter) {
		try {
			logSqlStatement(logger, statement, parameter);
			getJdbcTemplate().query(statement, rowHandler, parameter);
		} catch (DataAccessException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		}
	}

	/**
	 * Logs the statement and parameter in debug-level, executes select and logs error.
	 * 
	 * @param logger
	 * @param statement
	 * @param parameter
	 * @return List of db entries represented as caseinsensitive maps
	 */
	protected Map<String, Object> selectSingleRow(Logger logger, String statement, Object... parameter) {
		try {
			logSqlStatement(logger, statement, parameter);
			return getJdbcTemplate().queryForMap(statement, parameter);
		} catch (DataAccessException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		}
	}
	
	/**
	 * Logs the statement and parameter in debug-level, executes select and logs error.
	 * 
	 * @param logger
	 * @param statement
	 * @param requiredType
	 * @param parameter
	 * @return single db entry as object
	 */
	protected <T> T select(Logger logger, String statement, Class<T> requiredType, Object... parameter) {
		try {
			logSqlStatement(logger, statement, parameter);
			return getJdbcTemplate().queryForObject(statement, requiredType, parameter);
		} catch (DataAccessException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		}
	}
	
	/**
	 * Logs the statement and parameter in debug-level, executes select and logs error.
	 * If the searched entry does not exist an DataAccessException is thrown.
	 * 
	 * @param logger
	 * @param statement
	 * @param rowMapper
	 * @param parameter
	 * @return single db entry as object
	 */
	protected <T> T selectObject(Logger logger, String statement, RowMapper<T> rowMapper, Object... parameter) {
		try {
			logSqlStatement(logger, statement, parameter);
			return getJdbcTemplate().queryForObject(statement, rowMapper, parameter);
		} catch (DataAccessException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		}
	}
	
	/**
	 * Logs the statement and parameter in debug-level, executes select for an object and logs error.
	 * If the searched entry does not exist, then null is returned as default value. 
	 * If more than one item is found it throws an error saying so.
	 * 
	 * @param logger
	 * @param statement
	 * @param rowMapper
	 * @param parameter
	 * @return single db entry as object, null if no entry was found
	 */
	protected <T> T selectObjectDefaultNull(Logger logger, String statement, RowMapper<T> rowMapper, Object... parameter) {
		List<T> list = select(logger, statement, rowMapper, parameter);
		if (list.size() == 1) {
			return list.get(0);
		} else if (list.size() == 0) {
			return null;
		} else {
			throw new RuntimeException("Found invalid number of items: " + list.size());
		}
	}
	
	/**
	 * Logs the statement and parameter in debug-level, executes select and logs error.
	 * 
	 * @param logger
	 * @param statement
	 * @param parameter
	 * @return single db entry as int value or 0 in case of nothing was found
	 */
	protected int selectInt(Logger logger, String statement, Object... parameter) {
		return selectIntWithDefaultValue(logger, statement, 0, parameter);
	}
	
	/**
	 * Logs the statement and parameter in debug-level, executes select and logs error.
	 * The given default value is returned, if the statement return an EmptyResultDataAccessException,
	 * which indicates that the selected value is missing and no rows are returned by DB.
	 * All other Exceptions are not touched and will be thrown in the usual way.
	 * 
	 * @param logger
	 * @param statement
	 * @param defaultValue
	 * @param parameter
	 * @return single db entry as int value, default value if not found
	 */
	protected int selectIntWithDefaultValue(Logger logger, String statement, int defaultValue, Object... parameter) {
		try {
			logSqlStatement(logger, statement, parameter);
			Integer value = getJdbcTemplate().queryForObject(statement, parameter, Integer.class);
			
			return value != null ? value : defaultValue;
		} catch (EmptyResultDataAccessException e) {
			if (logger.isDebugEnabled()) {
				logger.debug("Empty result, using default value: " + defaultValue);
			}
			return defaultValue;
		} catch (DataAccessException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		}
	}
	
	/**
	 * Logs the statement and parameter in debug-level, executes select and logs error.
	 * 
	 * @param logger
	 * @param statement
	 * @param requiredType
	 * @param parameter
	 * @return single db entry as object
	 */
	protected <T> T selectWithDefaultValue(Logger logger, String statement, Class<T> requiredType, T defaultValue, Object... parameter) {
		try {
			logSqlStatement(logger, statement, parameter);
			return getJdbcTemplate().queryForObject(statement, requiredType, parameter);
		} catch (EmptyResultDataAccessException e) {
			if (logger.isDebugEnabled()) {
				logger.debug("Empty result, using default value: " + defaultValue);
			}
			return defaultValue;
		} catch (DataAccessException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		}
	}
	
	/**
	 * Logs the statement and parameter in debug-level, executes update and logs error.
	 * 
	 * @param logger
	 * @param statement
	 * @param parameter
	 * @return number of touched lines in db
	 */
	protected int update(Logger logger, String statement, Object... parameter) {
		try {
			logSqlStatement(logger, statement, parameter);
			int touchedLines = getJdbcTemplate().update(statement, parameter);
			if (logger.isDebugEnabled()) {
				logger.debug("lines changed by update: " + touchedLines);
			}
			return touchedLines;
		} catch (DataAccessException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		}
	}
	
	/**
	 * Logs the statement and parameter in debug-level, executes a DDL SQL Statement.
	 * 
	 * @param logger
	 * @param statement
	 * @return number of touched lines in db
	 */
	protected void execute(Logger logger, String statement) {
		try {
			logSqlStatement(logger, statement);
			getJdbcTemplate().execute(statement);
		} catch (DataAccessException e) {
			logSqlError(e, logger, statement);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, statement);
			throw e;
		}
	}

	/**
	 * Logs the statement and parameter in debug-level, executes update, retrieves generated keys and logs error.
	 *
	 * @param logger a logger to write logs to.
	 * @param statement an sql-query to execute.
	 * @param keys a holder for retrieved generated keys.
	 * @param keyColumns columns to retrieve as a generated keys.
	 * @param parameter bound parameters for an sql-query.
	 * @return number of touched lines in db.
	 */
	protected int update(Logger logger, String statement, KeyHolder keys, String[] keyColumns, Object... parameter) {
		try {
			logSqlStatement(logger, statement, parameter);
			int touchedLines = getJdbcTemplate().update(connection -> {
				PreparedStatement ps = connection.prepareStatement(statement, keyColumns);
				for (int i = 0; i < parameter.length; i++) {
					ps.setObject(i + 1, parameter[i]);
				}
				return ps;
			}, keys);
			if (logger.isDebugEnabled()) {
				logger.debug("lines changed by update: " + touchedLines);
			}
			return touchedLines;
		} catch (DataAccessException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, statement, parameter);
			throw e;
		}
	}

	public void checkMaximumDataSize(long datasize) throws Exception {
		if (!isOracleDB() && getMysqlMaxPacketSize() < datasize) {
			// If MariaDB/MySQL's MaxPacketSize is to low for uploading this blob
			throw new Exception("Data is to big for storage in your database. Please rise db configuration value 'max_allowed_packet'. Current value: " + getMysqlMaxPacketSize() + " bytes Datasize: " + datasize + " bytes");
		}
	}

	public void updateClob(Logger logger, String statement, String clobData, final Object... parameter) throws Exception {
		if (clobData == null) {
			try {
				logSqlStatement(logger, statement + " clobDataLength: NULL-Clob", parameter);
				Object[] parameterInclNull = new Object[parameter.length + 1];
				parameterInclNull[0] = null;
				for (int i = 0; i < parameter.length; i++) {
					parameterInclNull[i + 1] = parameter[i];
				}
				new JdbcTemplate(dataSource).update(statement, parameterInclNull);
			} catch(Exception e) {
				logSqlError(e, logger, statement, "clobDataLength: NULL-Clob", parameter);
				throw e;
			}
		} else {
			logSqlStatement(logger, statement + " clobDataLength:" + clobData.length(), parameter);
			
			checkMaximumDataSize(clobData.getBytes("UTF-8").length);
			
			try (Reader clobDataReader = new StringReader(clobData)) {
				new JdbcTemplate(dataSource).execute(statement, new AbstractLobCreatingPreparedStatementCallback(new DefaultLobHandler()) {
					@Override
					protected void setValues(PreparedStatement preparedStatement, LobCreator lobCreator) throws SQLException {
						lobCreator.setClobAsCharacterStream(preparedStatement, 1, clobDataReader, clobData.length());
						int parameterIndex = 2;
						for (Object parameterObject : parameter) {
							preparedStatement.setObject(parameterIndex++, parameterObject);
						}
					}
				});
			} catch(Exception e) {
				logSqlError(e, logger, statement, "clobDataLength:" + clobData.length(), parameter);
				throw e;
			}
		}
	}

	/**
	 * Method to update the data of an blob.
	 * This method should be DB-Vendor independent.
	 * The update statement must contain at least one parameter for the blob data and this must be the first parameter within the statement.
	 * 
	 * Example: updateBlob(logger, "UPDATE tableName SET blobField = ? WHERE idField1 = ? AND idField2 = ?", blobDataArray, id1Object, id2Object);
	 * 
	 * @param logger
	 * @param statement
	 * @param blobData
	 * @param parameter
	 * @throws Exception
	 */
	public void updateBlob(Logger logger, String statement, final byte[] blobData, final Object... parameter) throws Exception {
		if (blobData == null) {
			updateBlob(logger, statement, (InputStream) null, parameter);
		} else {
			updateBlob(logger, statement, new ByteArrayInputStream(blobData), parameter);
		}
	}
	
	/**
	 * Method to update the data of an blob.
	 * This method should be DB-Vendor independent.
	 * The update statement must contain at least one parameter for the blob data and this must be the first parameter within the statement.
	 * 
	 * Example: updateBlob(logger, "UPDATE tableName SET blobField = ? WHERE idField1 = ? AND idField2 = ?", blobDataInputSTream, id1Object, id2Object);
	 *
	 * @param logger
	 * @param statement
	 * @param blobDataInputStream
	 * @param parameter
	 * @throws Exception
	 */
	public void updateBlob(Logger logger, String statement, final InputStream blobDataInputStream, final Object... parameter) throws Exception {
		if (blobDataInputStream == null) {
			try {
				logSqlStatement(logger, statement + " blobDataLength: NULL-Blob", parameter);
				Object[] parameterInclNull = new Object[parameter.length + 1];
				parameterInclNull[0] = null;
				for (int i = 0; i < parameter.length; i++) {
					parameterInclNull[i + 1] = parameter[i];
				}
				new JdbcTemplate(dataSource).update(statement, parameterInclNull);
			} catch(Exception e) {
				logSqlError(e, logger, statement, "blobDataLength: NULL-Blob", parameter);
				throw e;
			}
		} else {
			logSqlStatement(logger, statement + " blobDataLength:" + blobDataInputStream.available(), parameter);
			
			checkMaximumDataSize(blobDataInputStream.available());
			
			try {
				new JdbcTemplate(dataSource).execute(statement, new AbstractLobCreatingPreparedStatementCallback(new DefaultLobHandler()) {
					@Override
					protected void setValues(PreparedStatement preparedStatement, LobCreator lobCreator) throws SQLException {
						try {
							lobCreator.setBlobAsBinaryStream(preparedStatement, 1, blobDataInputStream, blobDataInputStream.available());
						} catch (IOException e) {
							throw new SQLException("Cannot update blob data: " + e.getMessage(), e);
						}
						int parameterIndex = 2;
						for (Object parameterObject : parameter) {
							preparedStatement.setObject(parameterIndex++, parameterObject);
						}
					}
				});
			} catch(Exception e) {
				logSqlError(e, logger, statement, "blobDataLength:" + blobDataInputStream.available(), parameter);
				throw e;
			} finally {
				IOUtils.closeQuietly(blobDataInputStream);
			}
		}
	}
	
	/**
	 * Write the data of a blob into an outputstream.
	 * The selectBlobStatement must return a single column of type Blob.
	 * 
	 * @param logger
	 * @param selectBlobStatement
	 * @param outputStream
	 * @param parameter
	 * @throws SQLException 
	 * @throws IOException 
	 */
	public void writeBlobInStream(Logger logger, String selectBlobStatement, OutputStream outputStream, final Object... parameter) throws Exception {
		try {
			if (outputStream == null) {
				throw new RuntimeException("outputStream is null");
			}
			logSqlStatement(logger, selectBlobStatement, parameter);
			Blob blob = getJdbcTemplate().queryForObject(selectBlobStatement, Blob.class, parameter);
			try (InputStream inputStream = blob.getBinaryStream()) {
				IOUtils.copy(inputStream, outputStream);
			}
		} catch (DataAccessException e) {
			logSqlError(e, logger, selectBlobStatement, parameter);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, selectBlobStatement, parameter);
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
	 */
	public int[] batchupdate(Logger logger, String statement, List<Object[]> values) {
		try {
			logSqlStatement(logger, statement, "BatchUpdateParameterList(Size: " + values.size() + ")");
			int[] touchedLines = getJdbcTemplate().batchUpdate(statement, values);
			if (logger.isDebugEnabled()) {
				logger.debug("lines changed by update: " + Arrays.toString(touchedLines));
			}
			return touchedLines;
		} catch (RuntimeException e) {
			logSqlError(e, logger, statement, "BatchUpdateParameterList(Size: " + values.size() + ")");
			throw e;
		}
	}
	
	public int[] batchInsertIntoAutoincrementMysqlTable(Logger logger, String insertStatement, List<Object[]> listOfValueArrays) throws Exception {
		try {
			logSqlStatement(logger, insertStatement, "BatchInsertParameterList(Size: " + listOfValueArrays.size() + ")");
			
			List<Integer> generatedKeys = new ArrayList<>();

			try (final Connection connection = getDataSource().getConnection()) {
				try (final PreparedStatement preparedStatement = connection.prepareStatement(insertStatement, Statement.RETURN_GENERATED_KEYS)) {
					for (Object[] valueArray : listOfValueArrays) {
						int parameterIndex = 1;
						for (Object value : valueArray) {
							preparedStatement.setObject(parameterIndex++, value);
						}
						preparedStatement.addBatch();
					}
					preparedStatement.executeBatch();
					
					try (ResultSet resultSet = preparedStatement.getGeneratedKeys()) {
						while (resultSet.next()) {
							generatedKeys.add(resultSet.getInt(1));
						}
					}
				}
			}
			if (logger.isDebugEnabled()) {
				logger.debug("keys inserted by batch insert: " + StringUtils.join(generatedKeys, ", "));
			}
			return generatedKeys.stream().mapToInt(i->i).toArray();
		} catch (RuntimeException e) {
			logSqlError(e, logger, insertStatement, "BatchInsertParameterList(Size: " + listOfValueArrays.size() + ")");
			throw e;
		}
	}
	
	public int[] batchInsertIntoAutoincrementMysqlTable(Logger logger, String autoincrementColumn, String insertStatement, List<Object[]> parametersList) throws Exception {
		try {
			logSqlStatement(logger, insertStatement, "BatchInsertParameterList(Size: " + parametersList.size() + ")");
			
			Object[] parameter = parametersList.isEmpty() ? new Object[0] : parametersList.get(0);
			int[] insertParameterTypes = new int[parameter.length];
			for (int i = 0; i < parameter.length; i++) {
				if (parameter[i] == null) {
					insertParameterTypes[i] = Types.NULL;
				} else if (parameter[i] instanceof Integer) {
					insertParameterTypes[i] = Types.INTEGER;
				} else if (parameter[i] instanceof Double) {
					insertParameterTypes[i] = Types.DOUBLE;
				} else if (parameter[i] instanceof Float) {
					insertParameterTypes[i] = Types.FLOAT;
				} else if (parameter[i] instanceof String) {
					insertParameterTypes[i] = Types.VARCHAR;
				} else if (parameter[i] instanceof Date) {
					insertParameterTypes[i] = Types.TIMESTAMP;
				} else if (parameter[i] instanceof byte[]) {
					if (isMariaDB()) {
						insertParameterTypes[i] = Types.BINARY;
					} else {
						insertParameterTypes[i] = Types.BLOB;
					}
				} else {
					throw new RuntimeException("Invalid parameter type for insert in autoincrement table (Value: \"" + parameter[i] + "\", " + parameter[i].getClass().getSimpleName() + ")");
				}
			}
			
			BatchSqlUpdate batchSqlUpdate = new BatchSqlUpdate(getDataSource(), insertStatement, insertParameterTypes);
			batchSqlUpdate.setGeneratedKeysColumnNames(autoincrementColumn);
			batchSqlUpdate.setReturnGeneratedKeys(true);
			batchSqlUpdate.compile();
			GeneratedKeyHolder generatedKeyHolder = new GeneratedKeyHolder();
			int touchedLines = 0;
			int size = parametersList.size();
			batchSqlUpdate.setBatchSize(size);
			int[] generatedKeys = new int[size];
			for (int i = 0; i < size; i++) {
				int touchedLine = batchSqlUpdate.update(parametersList.get(i), generatedKeyHolder);
				touchedLines += touchedLine;
				generatedKeys[i] = generatedKeyHolder.getKey().intValue();
			}
			batchSqlUpdate.flush();
			
			if (touchedLines != parametersList.size()) {
				throw new RuntimeException("Illegal insert result");
			}
			
			return generatedKeys;
		} catch (RuntimeException e) {
			logSqlError(e, logger, insertStatement, "BatchInsertParameterList(Size: " + parametersList.size() + ")");
			throw e;
		}
	}

	public static int getValueOrDefaultFromNumberField(Number numberField, int defaultValue) {
		if (numberField == null) {
			return defaultValue;
		} else {
			return numberField.intValue();
		}
	}

	/**
	 * This method makes an insert into a mysql table with an autoincrement column.
	 * Logs the statement and parameter in debug-level, executes insert and logs error.
	 * 
	 * @param logger
	 * @param autoincrementColumn
	 * @param insertStatement
	 * @param parameter
	 * @return inserted id
	 */
	@DaoUpdateReturnValueCheck
	protected int insertIntoAutoincrementMysqlTable(Logger logger, String autoincrementColumn, String insertStatement, Object... parameter) {
		try {
			logSqlStatement(logger, insertStatement + " autoincrement: " + autoincrementColumn, parameter);

			int[] insertParameterTypes = new int[parameter.length];
			for (int i = 0; i < parameter.length; i++) {
				if (parameter[i] == null) {
					insertParameterTypes[i] = Types.NULL;
				} else if (parameter[i] instanceof Integer) {
					insertParameterTypes[i] = Types.INTEGER;
				} else if (parameter[i] instanceof Double) {
					insertParameterTypes[i] = Types.DOUBLE;
				} else if (parameter[i] instanceof Float) {
					insertParameterTypes[i] = Types.FLOAT;
				} else if (parameter[i] instanceof String) {
					insertParameterTypes[i] = Types.VARCHAR;
				} else if (parameter[i] instanceof Date) {
					insertParameterTypes[i] = Types.TIMESTAMP;
				} else if (parameter[i] instanceof byte[]) {
					if (isMariaDB()) {
						insertParameterTypes[i] = Types.BINARY;
					} else {
						insertParameterTypes[i] = Types.BLOB;
					}
				} else {
					throw new RuntimeException("Invalid parameter type for insert in autoincrement table (Value: \"" + parameter[i] + "\", " + parameter[i].getClass().getSimpleName() + ")");
				}
			}

			SqlUpdate sqlUpdate = new SqlUpdate(getDataSource(), insertStatement, insertParameterTypes);
			sqlUpdate.setReturnGeneratedKeys(true);
			sqlUpdate.setGeneratedKeysColumnNames(new String[] { autoincrementColumn });
			sqlUpdate.compile();
			GeneratedKeyHolder generatedKeyHolder = new GeneratedKeyHolder();

			int touchedLines = sqlUpdate.update(parameter, generatedKeyHolder);
			int autoincrementedValue = generatedKeyHolder.getKey().intValue();
			
			if (touchedLines != 1) {
				throw new RuntimeException("Illegal insert result");
			}
			
			return autoincrementedValue;
		} catch (DataAccessException e) {
			logSqlError(e, logger, insertStatement + " autoincrement: " + autoincrementColumn, parameter);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, insertStatement + " autoincrement: " + autoincrementColumn, parameter);
			throw e;
		}
	}

	/**
	 * This method makes multiple inserts into a mysql table with an autoincrement column.
	 * Logs the statement and parameter in debug-level, executes insert and logs error.
	 * 
	 * @param logger
	 * @param autoincrementColumn
	 * @param insertStatement
	 * @param parameter
	 * @return number of touched lines in db
	 */
	@DaoUpdateReturnValueCheck
	protected int insertMultipleIntoAutoincrementMysqlTable(Logger logger, String autoincrementColumn, String insertStatement, Object... parameter) {
		try {
			logSqlStatement(logger, insertStatement + " autoincrement: " + autoincrementColumn, parameter);

			int[] insertParameterTypes = new int[parameter.length];
			for (int i = 0; i < parameter.length; i++) {
				if (parameter[i] == null) {
					insertParameterTypes[i] = Types.NULL;
				} else if (parameter[i] instanceof Integer) {
					insertParameterTypes[i] = Types.INTEGER;
				} else if (parameter[i] instanceof String) {
					insertParameterTypes[i] = Types.VARCHAR;
				} else if (parameter[i] instanceof Date) {
					insertParameterTypes[i] = Types.TIMESTAMP;
				} else if (parameter[i] instanceof byte[]) {
					if (isMariaDB()) {
						insertParameterTypes[i] = Types.BINARY;
					} else {
						insertParameterTypes[i] = Types.BLOB;
					}
				} else {
					throw new RuntimeException("Invalid parameter type for insert in autoincrement table (Value: \"" + parameter[i] + "\", " + parameter[i].getClass().getSimpleName() + ")");
				}
			}

			SqlUpdate sqlUpdate = new SqlUpdate(getDataSource(), insertStatement, insertParameterTypes);
			sqlUpdate.setReturnGeneratedKeys(true);
			sqlUpdate.setGeneratedKeysColumnNames(new String[] { autoincrementColumn });
			sqlUpdate.compile();
			GeneratedKeyHolder generatedKeyHolder = new GeneratedKeyHolder();
			
			return sqlUpdate.update(parameter, generatedKeyHolder);
		} catch (DataAccessException e) {
			logSqlError(e, logger, insertStatement + " autoincrement: " + autoincrementColumn, parameter);
			throw e;
		} catch (RuntimeException e) {
			logSqlError(e, logger, insertStatement + " autoincrement: " + autoincrementColumn, parameter);
			throw e;
		}
	}

	protected String makeTimestampTruncateClause(String timestampExpression) {
		return (isOracleDB() ? "TRUNC(" : "DATE(") + timestampExpression + ")";
	}

	protected boolean checkIndicesAvailable(Logger logger, String... indicesToCheck) {
		if (indicesToCheck == null || indicesToCheck.length == 0) {
			return false;
		} else {
			String sqlCheckIndices;

			if (isOracleDB()) {
				sqlCheckIndices = "SELECT COUNT(*) FROM user_indexes WHERE LOWER(index_name) IN ('" + StringUtils.join(indicesToCheck, "','") + "')";
			} else {
				sqlCheckIndices = "SELECT COUNT(*) FROM INFORMATION_SCHEMA.STATISTICS WHERE LOWER(index_name) IN ('" + StringUtils.join(indicesToCheck, "','") + "')";
			}

			int existingIndicesCount = selectInt(logger, sqlCheckIndices);

			// All indices are required
			return existingIndicesCount == indicesToCheck.length;
		}
	}
	
	protected String makeBulkInClauseForString(final String columnName, Collection<String> values) {
		return makeBulkInClauseWithDelimiter(columnName, values, "'");
	}
	
	protected String makeBulkInClauseForInteger(final String columnName, Collection<Integer> values) {
		return makeBulkInClauseWithDelimiter(columnName, values, "");
	}
	
	private <T> String makeBulkInClauseWithDelimiter(String columnName, Collection<T> values, String delimiter) {
		return DbUtilities.makeBulkInClauseWithDelimiter(isOracleDB(), columnName, values, delimiter);
	}

	protected String getIfNull() {
		return isOracleDB() ? "NVL" : "IFNULL";
	}

	protected String getIsEmpty(String column) {
		return getIsEmpty(column, true);
	}

	protected String getIsNotEmpty(String column) {
		return getIsEmpty(column, false);
	}

	protected String getIsEmpty(String column, boolean positive) {
		if (isOracleDB()) {
			if (positive) {
				return column + " IS NULL";
			} else {
				return column + " IS NOT NULL";
			}
		} else {
			if (positive) {
				return "(" + column + " IS NULL OR " + column + " = '')";
			} else {
				return column + " > ''";
			}
		}
	}

	/**
	 * Check existence on rows with good performance
	 * @param query like "SELECT 1 FROM ..."
	 */
	protected boolean existsAtLeastOneRow(Logger logger, String query, Object... params){
		String existsQuery;
		if(isOracleDB()){
			existsQuery =
					"SELECT CASE" +
					" WHEN EXISTS (" + query + ")" +
					"  THEN 1" +
					" ELSE 0" +
					" END AS any_row_exists " +
					"FROM dual";
		} else {
			existsQuery = "SELECT EXISTS(" + query + ")";
		}
		return selectObject(logger, existsQuery, (resultSet, i) -> resultSet.getBoolean(1), params);
	}
	
	private int getMysqlMaxPacketSize() {
		if (MYSQL_MAXPACKETSIZE == null) {
			MYSQL_MAXPACKETSIZE = DbUtilities.getMysqlMaxAllowedPacketSize(getDataSource());
		}
		
		return MYSQL_MAXPACKETSIZE;
	}

	protected String replaceWildCardCharacters(String searchQuery) {
		return searchQuery.replaceAll("\\*", "%").replaceAll("\\?", "_");
	}

	/**
	 * Replaces some sql control characters to escape sequence.
	 *
	 * @param value some string which should be escaped for sql
	 * @return escaped string
	 */
	protected String getEscapedValue(String value) {
		final Pattern regex = Pattern.compile("(?<filteredCharacter>[!%_])");
		final String replacement = "!${filteredCharacter}";

		return value.replaceAll(regex.pattern(), replacement);
	}

	protected boolean deleteByCompany(Logger logger, String tableName, int companyId) {
		String selectByCompanyQuery = String.format("SELECT COUNT(*) FROM %s WHERE company_id = ?", tableName);
		String deleteByCompanyQuery = String.format("DELETE FROM %s WHERE company_id = ?", tableName);
		update(logger, deleteByCompanyQuery, companyId);
		return selectInt(logger, selectByCompanyQuery, companyId) == 0;
	}
	
	protected String getUniqueCloneNameByNamePrefix(Logger logger, String tableName, String fieldName, String namePrefix, String condition, Object... parameters) {
		String prefix = DbUtilities.escapeLikeExpression(namePrefix, '\\');
	    String sqlGetMaxIndex;

	    if (StringUtils.isBlank(condition)) {
	    	condition = "";
		} else {
	    	condition = "(" + condition + ") AND ";
		}

        sqlGetMaxIndex = String.format("SELECT %s FROM %s WHERE %s %s like ?", fieldName, tableName, condition, fieldName);
		
		List<String> nameList = select(logger, sqlGetMaxIndex, new StringRowMapper(), ArrayUtils.add(parameters, prefix + "%"));
		
		return AgnUtils.findUniqueCloneName(nameList, prefix);
	}
}
