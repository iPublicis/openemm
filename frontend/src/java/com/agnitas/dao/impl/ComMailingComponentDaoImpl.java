/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.dao.impl;

import java.io.InputStream;
import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.agnitas.dao.ComMailingComponentDao;
import com.agnitas.dao.DaoUpdateReturnValueCheck;
import com.agnitas.web.ShowImageServlet;
import org.agnitas.beans.MailingComponent;
import org.agnitas.beans.factory.MailingComponentFactory;
import org.agnitas.dao.impl.BaseDaoImpl;
import org.agnitas.emm.core.commons.util.ConfigService;
import org.agnitas.emm.core.velocity.VelocityCheck;
import org.agnitas.util.AgnUtils;
import org.agnitas.util.DbUtilities;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;

public class ComMailingComponentDaoImpl extends BaseDaoImpl implements ComMailingComponentDao {
	
	/**
	 * The logger.
	 */
	private static final transient Logger logger = Logger.getLogger(ComMailingComponentDaoImpl.class);
	
	// ----------------------------------------------------------------------------------------------------------------
	// Dependency Injection
	
	/**
	 * Factory to create new mailing components.
	 */
	protected MailingComponentFactory mailingComponentFactory;

	private ConfigService configService;

	/**
	 * Set factory to create new mailing components.
	 *
	 * @param mailingComponentFactory factory to create new mailing components
	 */
	public void setMailingComponentFactory(MailingComponentFactory mailingComponentFactory) {
		this.mailingComponentFactory = mailingComponentFactory;
	}

	@Required
	public void setConfigService(ConfigService configService) {
		this.configService = configService;
	}

	// ----------------------------------------------------------------------------------------------------------------
	// Business Logic

	@Override
	public List<MailingComponent> getMailingComponents(int mailingID, @VelocityCheck int companyID, int componentType) {
		return getMailingComponents(mailingID, companyID, componentType, true);
	}

	@Override
	public List<MailingComponent> getMailingComponents(int mailingID, @VelocityCheck int companyID, int componentType, boolean includeContent) {
		String sqlGetComponents = "SELECT company_id, mailing_id, component_id, compname, comptype, mtype, target_id, url_id, description, timestamp" +
				(includeContent ? ", emmblock, binblock " : " ") +
				"FROM component_tbl " +
				"WHERE company_id = ? AND mailing_id = ? AND comptype = ? " +
				"ORDER BY compname ASC";

		return select(logger, sqlGetComponents, new MailingComponentRowMapper(includeContent), companyID, mailingID, componentType);
	}

	@Override
	public List<MailingComponent> getMailingComponents(int mailingID, @VelocityCheck int companyID) {
		String componentSelect = "SELECT company_id, mailing_id, component_id, compname, comptype, emmblock, binblock, mtype, target_id, url_id, description, timestamp FROM component_tbl WHERE company_id = ? AND mailing_id = ? ORDER BY compname ASC";
		List<MailingComponent> mailingComponentList = select(logger, componentSelect, new MailingComponentRowMapper(), companyID, mailingID);

		// Sort results (mobile components after their base components)
		Collections.sort(mailingComponentList, (mailingComponent1, mailingComponent2) -> {
			String name1 = mailingComponent1.getComponentName();
			if (name1.startsWith(ShowImageServlet.MOBILE_IMAGE_PREFIX)) {
				name1 = name1.substring(ShowImageServlet.MOBILE_IMAGE_PREFIX.length());
			}
			String name2 = mailingComponent2.getComponentName();
			if (name2.startsWith(ShowImageServlet.MOBILE_IMAGE_PREFIX)) {
				name2 = name2.substring(ShowImageServlet.MOBILE_IMAGE_PREFIX.length());
			}
			if (name1.equals(name2)) {
				if (mailingComponent1.getComponentName().startsWith(ShowImageServlet.MOBILE_IMAGE_PREFIX)) {
					return mailingComponent2.getComponentName().startsWith(ShowImageServlet.MOBILE_IMAGE_PREFIX) ? 0 : 1;
				} else {
					return mailingComponent2.getComponentName().startsWith(ShowImageServlet.MOBILE_IMAGE_PREFIX) ? -1 : 0;
				}
			} else {
				return name1.compareTo(name2);
			}
		});

		return mailingComponentList;
	}

	@Override
	public MailingComponent getMailingComponent(int compID, @VelocityCheck int companyID) {
		if (companyID == 0) {
			return null;
		}
		
		String componentSelect = "SELECT company_id, mailing_id, component_id, compname, comptype, emmblock, binblock, mtype, target_id, url_id, description, timestamp FROM component_tbl WHERE component_id = ? AND company_id = ? ORDER BY compname ASC";
		
		try {
			return selectObjectDefaultNull(logger, componentSelect, new MailingComponentRowMapper(), compID, companyID);
		} catch (Exception e) {
			logger.error("Cannot read MailingComponent: " + e.getMessage(), e);
			javaMailService.sendExceptionMail("SQL: " + componentSelect, e);
			return mailingComponentFactory.newMailingComponent();
		}
	}

	@Override
	public MailingComponent getMailingComponentByName(int mailingID, @VelocityCheck int companyID, String name) {
		if (companyID == 0) {
			return null;
		}
		
		String componentSelect = "SELECT company_id, mailing_id, component_id, compname, comptype, emmblock, binblock, mtype, target_id, url_id, description, timestamp FROM component_tbl WHERE (mailing_id = ? OR mailing_id = 0) AND company_id = ? AND compname = ? ORDER BY component_id DESC";
		
		try {
			List<MailingComponent> components = select(logger, componentSelect, new MailingComponentRowMapper(), mailingID, companyID, name);
			if (components.size() > 0) {
				// return the first of all results
				return components.get(0);
			} else {
				return null;
			}
		} catch (Exception e) {
			logger.error("Error getting component " + name + " for mailing " + mailingID, e);
			javaMailService.sendExceptionMail("SQL: " + componentSelect, e);
			return mailingComponentFactory.newMailingComponent();
		}
	}

	@Override
	@DaoUpdateReturnValueCheck
	public void saveMailingComponent(MailingComponent comp) throws Exception {
		// TODO: What are these defaultvalues for? They are only written to DB on the first insert and will not be read again
		int mailtemplateID = 0;
		int comppresent = 1;

		try {
			if (comp.getId() == 0 || !exists(comp.getMailingID(), comp.getCompanyID(), comp.getId())) {
                comp.setTimestamp(new Date());
                
                if (isOracleDB()) {
                	int newID = selectInt(logger, "SELECT component_tbl_seq.NEXTVAL FROM DUAL");
                	String sql = "INSERT INTO component_tbl (component_id, mailing_id, company_id, compname, comptype, mtype, target_id, url_id, mailtemplate_id, comppresent, timestamp, description) VALUES (" + AgnUtils.repeatString("?", 12, ", ") + ")";
                    int touchedLines = update(logger, sql, newID, comp.getMailingID(), comp.getCompanyID(), comp.getComponentName(), comp.getType(), comp.getMimeType(), comp.getTargetID(), comp.getUrlID(), mailtemplateID, comppresent, comp.getTimestamp(), comp.getDescription());
                    if (touchedLines != 1) {
                        throw new RuntimeException("Illegal insert result");
                    } else {
						try {
							updateBlob(logger, "UPDATE component_tbl SET binblock = ? WHERE component_id = ?", comp.getBinaryBlock(), newID);
							updateClob(logger, "UPDATE component_tbl SET emmblock = ? WHERE component_id = ?", comp.getEmmBlock(), newID);
						} catch (Exception e) {
							logger.error(String.format("Error saving mailing component %d (mailing ID %d, company ID %d)", comp.getId(), comp.getMailingID(), comp.getCompanyID()), e);
							
							update(logger, "DELETE FROM component_tbl WHERE component_id = ?", newID);
							throw e;
						}
					}

                    comp.setId(newID);
                } else {
                	String insertStatement = "INSERT INTO component_tbl (mailing_id, company_id, compname, comptype, mtype, target_id, url_id, mailtemplate_id, comppresent, timestamp, description) VALUES (" + AgnUtils.repeatString("?", 11, ", ") + ")";
                    int newID = insertIntoAutoincrementMysqlTable(logger, "component_id", insertStatement, comp.getMailingID(), comp.getCompanyID(), comp.getComponentName(), comp.getType(), comp.getMimeType(), comp.getTargetID(), comp.getUrlID(), mailtemplateID, comppresent, comp.getTimestamp(), comp.getDescription());
					try {
						updateBlob(logger, "UPDATE component_tbl SET binblock = ? WHERE component_id = ?", comp.getBinaryBlock(), newID);
						updateClob(logger, "UPDATE component_tbl SET emmblock = ? WHERE component_id = ?", comp.getEmmBlock(), newID);
					} catch (Exception e) {
						update(logger, "DELETE FROM component_tbl WHERE component_id = ?", newID);
						throw e;
					}
                    comp.setId(newID);
                }

			} else {
                comp.setTimestamp(new Date());
				
				String sql = "UPDATE component_tbl SET mailing_id = ?, company_id = ?, compname = ?, comptype = ?, mtype = ?, target_id = ?, url_id = ?, timestamp = ?, description = ? WHERE component_id = ?";
				int touchedLines = update(logger, sql, comp.getMailingID(), comp.getCompanyID(), comp.getComponentName(), comp.getType(), comp.getMimeType(), comp.getTargetID(), comp.getUrlID(), comp.getTimestamp(), comp.getDescription(), comp.getId());
				if (touchedLines != 1) {
					throw new RuntimeException("Illegal update result");
				} else {
					updateBlob(logger, "UPDATE component_tbl SET binblock = ? WHERE component_id = ?", comp.getBinaryBlock(), comp.getId());
					updateClob(logger, "UPDATE component_tbl SET emmblock = ? WHERE component_id = ?", comp.getEmmBlock(), comp.getId());
				}
			}
		} catch (Exception e) {
			logger.error("Error saving component " + comp.getId() + " for mailing " + comp.getMailingID(), e);
			throw e;
		}
	}

	@Override
	@DaoUpdateReturnValueCheck
	public void deleteMailingComponent(MailingComponent comp) {
		String sql = "DELETE FROM component_tbl WHERE component_id = ?";
		try {
			update(logger, sql, comp.getId());
		} catch (Exception e) {
			logger.error("Error deleting component " + comp.getId(), e);

			javaMailService.sendExceptionMail("SQL: " + sql + ", " + comp.getId(), e);
		}
	}
	
	@Override
	@DaoUpdateReturnValueCheck
	public void deleteMailingComponents(List<MailingComponent> components) {
		String sql = "DELETE FROM component_tbl WHERE component_id IN (" + AgnUtils.repeatString("?", components.size(), ", ") + ")";
		List<Integer> componentsIds = components.stream().map(MailingComponent::getId).collect(Collectors.toList());
		try {
			update(logger, sql, componentsIds.toArray());
		} catch (Exception e) {
			String idsString = StringUtils.join(componentsIds, ", ");
			logger.error("Error deleting components WITH IDS: " + idsString, e);

			javaMailService.sendExceptionMail("SQL: " + sql + ", IDS: " + idsString, e);
		}
	}

	@Override
	public Map<Integer, Integer> getImageSizes(@VelocityCheck int companyID, int mailingID) {
		return getImageComponentsSizes(companyID, mailingID);
	}

	@Override
	public Map<Integer, String> getImageNames(@VelocityCheck int companyId, int mailingId, boolean includeExternalImages) {
		if (companyId <= 0 || mailingId <= 0) {
			return Collections.emptyMap();
		}

		String sqlGetNames = "SELECT component_id, compname FROM component_tbl WHERE company_id = ? AND mailing_id = ?";
		List<Object> sqlParameters = new ArrayList<>(Arrays.asList(companyId, mailingId));

		if (includeExternalImages) {
			sqlGetNames += " AND comptype IN (?, ?)";
			sqlParameters.add(MailingComponent.TYPE_IMAGE);
			sqlParameters.add(MailingComponent.TYPE_HOSTED_IMAGE);
		} else {
			sqlGetNames += " AND comptype = ?";
			sqlParameters.add(MailingComponent.TYPE_HOSTED_IMAGE);
		}

		Map<Integer, String> map = new HashMap<>();
		query(logger, sqlGetNames, rs -> map.put(rs.getInt("component_id"), rs.getString("compname")), sqlParameters.toArray());
		return map;
	}

	@Override
	public Map<Integer, Integer> getImageComponentsSizes(@VelocityCheck int companyID, int mailingID) {
		Object[] sqlParameters = new Object[] {companyID, mailingID, MailingComponent.TYPE_IMAGE, MailingComponent.TYPE_HOSTED_IMAGE};
		String sql;
		if (isOracleDB()) {
			sql = "SELECT COALESCE(DBMS_LOB.GETLENGTH(binblock), 0) image_size, component_id FROM component_tbl WHERE company_id = ? AND mailing_id = ? AND comptype IN (?, ?)";
		} else {
			sql = "SELECT COALESCE(OCTET_LENGTH(binblock), 0) image_size, component_id FROM component_tbl WHERE company_id = ? AND mailing_id = ? AND comptype IN (?, ?)";
		}

		Map<Integer, Integer> map = new HashMap<>();
		query(logger, sql, rs -> map.put(rs.getInt("component_id"), rs.getInt("image_size")), sqlParameters);
		return map;
	}

	@Override
	public Map<Integer, Date> getImageComponentsTimestamps(@VelocityCheck int companyID, int mailingID) {
		String sql = "SELECT timestamp, component_id FROM component_tbl WHERE company_id = ? AND mailing_id = ? AND comptype IN (?, ?)";
		Object[] sqlParameters = new Object[]{companyID, mailingID, MailingComponent.TYPE_IMAGE, MailingComponent.TYPE_HOSTED_IMAGE};

		Map<Integer, Date> map = new HashMap<>();
		query(logger, sql, rs -> map.put(rs.getInt("component_id"), rs.getTimestamp("timestamp")), sqlParameters);
		return map;
	}

	@Override
	public Date getComponentTime(@VelocityCheck int companyID, int mailingID, String name) {
		String sql = "SELECT timestamp FROM component_tbl WHERE company_id = ? AND mailing_id = ? AND compname = ?";
		try {
			return select(logger, sql, Date.class, companyID, mailingID, name);
		} catch (Exception e) {
			logger.error("Error getting time of component " + name, e);
			javaMailService.sendExceptionMail("SQL: " + sql + ", " + companyID + ", " + mailingID + ", " + name, e);
			return null;
		}
	}

	@Override
	public List<MailingComponent> getMailingComponentsByType(int componentType, @VelocityCheck int companyID) {
		String componentSelect = "SELECT company_id, mailing_id, component_id, compname, comptype, emmblock, binblock, mtype, target_id, url_id, description, timestamp FROM component_tbl WHERE company_id = ? AND comptype = ? ORDER BY compname ASC";
		return select(logger, componentSelect, new MailingComponentRowMapper(), companyID, componentType);
	}

	@Override
	public boolean exists(int mailingID, int companyID, int componentID) {
		String sql = "SELECT COUNT(component_id) FROM component_tbl WHERE mailing_id = ? AND company_id = ? AND component_id = ?";
		int total = selectInt(logger, sql, mailingID, companyID, componentID);
		return total > 0;
	}
	
	@Override
	@DaoUpdateReturnValueCheck
	public boolean deleteMailingComponentsByCompanyID(int companyID) {
		String deleteSQL = "DELETE FROM component_tbl WHERE company_id = ?";
		int affectedRows = update(logger, deleteSQL, companyID);
		if(affectedRows > 0) {
			return true;
		} else {
			int remainingComponents = selectInt(logger, "SELECT COUNT(*) FROM component_tbl WHERE company_id = ?", companyID);
			return remainingComponents == 0;
		}
	}
	
	@Override
	public void deleteMailingComponentsByMailing(int mailingID) {
		String deleteSQL = "DELETE FROM component_tbl WHERE mailing_id = ?";
		update(logger, deleteSQL, mailingID);
	}

    @Override
    public List<MailingComponent> getMailingComponentsByType(@VelocityCheck int companyID, int mailingID, List<Integer> types) {
		if (CollectionUtils.isEmpty(types)) {
			return new ArrayList<>();
		}
        String componentSelect = "SELECT company_id, mailing_id, component_id, compname, comptype, emmblock, binblock, mtype, target_id, url_id, description, timestamp FROM component_tbl WHERE company_id = ? AND mailing_id = ? AND comptype IN (" + StringUtils.join(types, ", ") + ") ORDER BY comptype DESC, compname ASC";
        List<MailingComponent> mailingComponentList = select(logger, componentSelect, new MailingComponentRowMapper(), companyID, mailingID);

        return mailingComponentList;
    }

	protected class MailingComponentRowMapper implements RowMapper<MailingComponent> {
		private boolean includeContent;

		public MailingComponentRowMapper() {
			this(true);
		}

		public MailingComponentRowMapper(boolean includeContent) {
			this.includeContent = includeContent;
		}

		@Override
		public MailingComponent mapRow(ResultSet resultSet, int index) throws SQLException {
			MailingComponent component = mailingComponentFactory.newMailingComponent();

			component.setCompanyID(resultSet.getInt("company_id"));
			component.setMailingID(resultSet.getInt("mailing_id"));
			component.setId(resultSet.getInt("component_id"));
			component.setComponentName(resultSet.getString("compname"));
			component.setType(resultSet.getInt("comptype"));
			component.setTargetID(resultSet.getInt("target_id"));
			component.setUrlID(resultSet.getInt("url_id"));
			component.setDescription(resultSet.getString("description"));
			component.setTimestamp(resultSet.getTimestamp("timestamp"));

			if (includeContent) {
				Blob blob = resultSet.getBlob("binblock");
				// binblock sometimes contains an array "byte[1] = {0}", which also signals empty binary data
				
				if (blob != null && blob.length() > 1) {
					try (InputStream dataStream = blob.getBinaryStream()) {
						byte[] data = IOUtils.toByteArray(dataStream);
						component.setBinaryBlock(data, resultSet.getString("mtype"));
					} catch (Exception ex) {
						logger.error("Error:" + ex, ex);
					}
				} else {
					component.setEmmBlock(resultSet.getString("emmblock"), resultSet.getString("mtype"));
				}
			}

			return component;
		}
	}

	@Override
    public int getImageComponent(@VelocityCheck int companyId, int mailingId, int componentType) {
		String sqlGetComponentId = "SELECT component_id FROM component_tbl " +
				"WHERE company_id = ? AND mailing_id = ? AND comptype = ? " +
				"ORDER BY timestamp DESC";

		if (isOracleDB()) {
			sqlGetComponentId = "SELECT component_id FROM (" + sqlGetComponentId + ") WHERE rownum = 1";
		} else {
			sqlGetComponentId += " LIMIT 1";
		}

        return selectInt(logger, sqlGetComponentId, companyId, mailingId, componentType);
    }

	@Override
	public List<MailingComponent> getPreviewHeaderComponents(int mailingID, @VelocityCheck int companyID) {
		return select(logger, "SELECT * FROM component_tbl WHERE (comptype = ? OR comptype = ?) AND mailing_id = ? AND company_id = ? ORDER BY component_id", new MailingComponentRowMapper(),
				MailingComponent.TYPE_ATTACHMENT, MailingComponent.TYPE_PERSONALIZED_ATTACHMENT, mailingID, companyID);
	}

	@Override
	public void updateHostImage(int mailingID, @VelocityCheck int companyID, int componentID, byte[] imageBytes) {
		try {
			String sql = "UPDATE component_tbl SET timestamp = ? WHERE component_id = ?";
			int touchedLines = update(logger, sql, new Date(), componentID);
			if (touchedLines != 1) {
				throw new RuntimeException("Illegal insert result");
			} else {
				updateBlob(logger, "UPDATE component_tbl SET binblock = ? WHERE component_id = ?", imageBytes, componentID);
			}
		} catch (Exception e) {
			logger.error("Error saving component " + componentID, e);
		}
	}

    @Override
    public boolean updateBinBlockBulk(@VelocityCheck int companyId, Collection<Integer> mailingIds, int componentType, Collection<String> namePatterns, byte[] value) {
        if (companyId < 0 || CollectionUtils.isEmpty(mailingIds) || CollectionUtils.isEmpty(namePatterns)) {
            return false;
        }

        List<Object> sqlParameters = new ArrayList<>();

        sqlParameters.add(value);
        sqlParameters.add(companyId);
        sqlParameters.add(componentType);
        sqlParameters.addAll(namePatterns);

        String sqlFilterByMailingId = DbUtilities.makeBulkInClauseWithDelimiter(isOracleDB(), "mailing_id", mailingIds, null);
        String sqlFilterByName = AgnUtils.repeatString("compname LIKE ?", namePatterns.size(), " OR ");

        String sqlSetBinBlock = "UPDATE component_tbl SET binblock = ?, timestamp = CURRENT_TIMESTAMP " +
                "WHERE company_id = ? AND comptype = ? AND " + sqlFilterByMailingId + " AND (" + sqlFilterByName + ")";

        return update(logger, sqlSetBinBlock, sqlParameters.toArray()) > 0;
    }
}
