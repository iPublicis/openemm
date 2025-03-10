/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.dao.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.agnitas.beans.ProfileField;
import org.agnitas.dao.impl.BaseDaoImpl;
import org.agnitas.dao.impl.mapper.StringRowMapper;
import org.agnitas.emm.core.commons.util.ConfigService;
import org.agnitas.emm.core.commons.util.ConfigValue;
import org.agnitas.emm.core.velocity.VelocityCheck;
import org.agnitas.util.AgnUtils;
import org.agnitas.util.DbColumnType;
import org.agnitas.util.DbUtilities;
import org.agnitas.util.SafeString;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.jdbc.core.RowMapper;

import com.agnitas.beans.ComAdmin;
import com.agnitas.beans.ComProfileField;
import com.agnitas.beans.ComProfileFieldPermission;
import com.agnitas.beans.impl.ComProfileFieldImpl;
import com.agnitas.dao.ComProfileFieldDao;
import com.agnitas.dao.DaoUpdateReturnValueCheck;
import com.agnitas.emm.core.profilefields.ProfileFieldException;
import com.agnitas.emm.core.recipient.RecipientProfileHistoryException;
import com.agnitas.emm.core.recipient.RecipientProfileHistoryUtil;
import com.agnitas.emm.core.recipient.service.RecipientProfileHistoryService;

import net.sf.json.JSONArray;
import net.sf.json.JSONException;

public class ComProfileFieldDaoImpl extends BaseDaoImpl implements ComProfileFieldDao {
	
	/** The logger. */
	private static final transient Logger logger = Logger.getLogger(ComProfileFieldDaoImpl.class);
	
	private static final String TABLE = "customer_field_tbl";

	private static final String FIELD_COMPANY_ID = "company_id";
	private static final String FIELD_ADMIN_ID = "admin_id";
	private static final String FIELD_COLUMN_NAME = "col_name";
	private static final String FIELD_SHORTNAME = "shortname";
	private static final String FIELD_DESCRIPTION = "description";
	private static final String FIELD_DEFAULT_VALUE = "default_value";
	private static final String FIELD_MODE_EDIT = "mode_edit";
	private static final String FIELD_MODE_INSERT = "mode_insert";
	private static final String FIELD_GROUP = "field_group";
	private static final String FIELD_SORT = "field_sort";
	private static final String FIELD_LINE = "line";
	private static final String FIELD_ISINTEREST = "isinterest";
	private static final String FIELD_CREATION_DATE = "creation_date";
	private static final String FIELD_CHANGE_DATE = "change_date";
	private static final String FIELD_HISTORIZE = "historize";
	private static final String FIELD_ALLOWED_VALUES = "allowed_values";

	private static final String[] FIELD_NAMES = new String[] { FIELD_COMPANY_ID, FIELD_COLUMN_NAME, FIELD_SHORTNAME, FIELD_DESCRIPTION, FIELD_DEFAULT_VALUE, FIELD_MODE_EDIT, FIELD_MODE_INSERT, FIELD_GROUP, FIELD_SORT, FIELD_LINE, FIELD_CREATION_DATE, FIELD_ISINTEREST, FIELD_CHANGE_DATE, FIELD_HISTORIZE, FIELD_ALLOWED_VALUES };

	private static final String SELECT_PROFILEFIELDS_BY_COMPANYID = "SELECT " + StringUtils.join(FIELD_NAMES, ", ") + " FROM " + TABLE + " WHERE " + FIELD_COMPANY_ID + " = ? ORDER BY " + FIELD_SORT + ", LOWER(" + FIELD_SHORTNAME + "), LOWER(" + FIELD_COLUMN_NAME + ")";
    private static final String SELECT_PROFILEFIELDS_BY_COMPANYID_HISTORIZEDONLY = "SELECT " + StringUtils.join(FIELD_NAMES, ", ") + " FROM " + TABLE + " WHERE " + FIELD_COMPANY_ID + " = ? AND " + FIELD_HISTORIZE + " = 1 ORDER BY " + FIELD_SORT + ", LOWER(" + FIELD_SHORTNAME + "), LOWER(" + FIELD_COLUMN_NAME + ")";
	private static final String SELECT_PROFILEFIELDS_BY_COMPANYID_HAVINGSORT = "SELECT " + StringUtils.join(FIELD_NAMES, ", ") + " FROM " + TABLE + " WHERE " + FIELD_COMPANY_ID + " = ? AND " + FIELD_SORT + " IS NOT NULL AND " + FIELD_SORT + " < " + MAX_SORT_INDEX + " ORDER BY " + FIELD_SORT + ", LOWER(" + FIELD_SHORTNAME + "), LOWER(" + FIELD_COLUMN_NAME + ")";
	private static final String SELECT_PROFILEFIELDS_BY_COMPANYID_HAVINGINTEREST = "SELECT " + StringUtils.join(FIELD_NAMES, ", ") + " FROM " + TABLE + " WHERE " + FIELD_COMPANY_ID + " = ? AND " + FIELD_ISINTEREST + " IS NOT NULL AND " + FIELD_ISINTEREST + " >= 1 ORDER BY " + FIELD_SORT + ", LOWER(" + FIELD_SHORTNAME + "), LOWER(" + FIELD_COLUMN_NAME + ")";
	private static final String SELECT_PROFILEFIELD_BY_COMPANYID_AND_COLUMNNAME = "SELECT " + StringUtils.join(FIELD_NAMES, ", ") + " FROM " + TABLE + " WHERE " + FIELD_COMPANY_ID + " = ? AND LOWER(" + FIELD_COLUMN_NAME + ") = LOWER(?)";
	private static final String SELECT_PROFILEFIELD_BY_COMPANYID_AND_SHORTNAME = "SELECT " + StringUtils.join(FIELD_NAMES, ", ") + " FROM " + TABLE + " WHERE " + FIELD_COMPANY_ID + " = ? AND " + FIELD_SHORTNAME + " = ?";
    
    protected static final String DELETE_PROFILEFIELD_BY_COLUMNNAME = "DELETE FROM " + TABLE + " WHERE " + FIELD_COMPANY_ID + " = ? AND UPPER(" + FIELD_COLUMN_NAME + ") = UPPER(?)";

	private static final String TABLE_PERMISSION = "customer_field_permission_tbl";
	
	private static final String FIELD_PERMISSION_COMPANY_ID = "company_id";
	private static final String FIELD_PERMISSION_COLUMN_NAME = "column_name";
	private static final String FIELD_PERMISSION_ADMIN_ID = "admin_id";
	private static final String FIELD_PERMISSION_MODE_EDIT = "mode_edit";
	
	private static final String[] FIELD_NAMES_PERMISSION = new String[] { FIELD_PERMISSION_COMPANY_ID, FIELD_PERMISSION_COLUMN_NAME, FIELD_PERMISSION_ADMIN_ID, FIELD_PERMISSION_MODE_EDIT };
	
	private static final String SELECT_PROFILEFIELDPERMISSION = "SELECT " + StringUtils.join(FIELD_NAMES_PERMISSION, ", ") + " FROM " + TABLE_PERMISSION + " WHERE " + FIELD_PERMISSION_COMPANY_ID + " = ? AND UPPER(" + FIELD_PERMISSION_COLUMN_NAME + ") = UPPER(?) AND " + FIELD_PERMISSION_ADMIN_ID + " = ?";

	private static final Set<String> HIDDEN_COLUMNS = AgnUtils.getCaseInsensitiveSet(
		ComCompanyDaoImpl.STANDARD_FIELD_CREATION_DATE,
		ComCompanyDaoImpl.STANDARD_FIELD_TITLE,
		ComCompanyDaoImpl.STANDARD_FIELD_DATASOURCE_ID,
		ComCompanyDaoImpl.STANDARD_FIELD_EMAIL,
		ComCompanyDaoImpl.STANDARD_FIELD_FIRSTNAME,
		ComCompanyDaoImpl.STANDARD_FIELD_LASTNAME,
		ComCompanyDaoImpl.STANDARD_FIELD_GENDER,
		ComCompanyDaoImpl.STANDARD_FIELD_MAILTYPE,
		ComCompanyDaoImpl.STANDARD_FIELD_CUSTOMER_ID,
		ComCompanyDaoImpl.STANDARD_FIELD_TIMESTAMP,
		ComCompanyDaoImpl.STANDARD_FIELD_LATEST_DATASOURCE_ID,
		ComCompanyDaoImpl.STANDARD_FIELD_LASTOPEN_DATE,
		ComCompanyDaoImpl.STANDARD_FIELD_LASTCLICK_DATE,
		ComCompanyDaoImpl.STANDARD_FIELD_LASTSEND_DATE,
		ComCompanyDaoImpl.STANDARD_FIELD_DO_NOT_TRACK,
		ComCompanyDaoImpl.STANDARD_FIELD_CLEANED_DATE
	);

	/** Service accessing configuration data. */
	protected ConfigService configService;
	
	private RecipientProfileHistoryService profileHistoryService;

	/**
	 * Set service for accessing configuration data.
	 * 
	 * @param configService service for accessing configuration data.
	 */
	@Required
	public void setConfigService(ConfigService configService) {
		this.configService = configService;
	}
	
	/**
	 * Set service handling profile field history.
	 * 
	 * @param service service handling profile field history
	 */
	@Required
	public void setProfileHistoryService(final RecipientProfileHistoryService service) {
		this.profileHistoryService = service;
	}

	@Override
	public ComProfileField getProfileField(@VelocityCheck int companyID, String columnName) throws Exception {
		if (companyID <= 0) {
			throw new RuntimeException("Invalid companyId for getProfileField");
		} else if (StringUtils.isBlank(columnName)) {
			throw new RuntimeException("Invalid empty columnName for getProfileField");
		} else {
			DbColumnType columnType = DbUtilities.getColumnDataType(getDataSource(), "customer_" + companyID + "_tbl", columnName);
			if (columnType == null) {
				return null;
			} else {
				List<ComProfileField> profileFieldList = select(logger, SELECT_PROFILEFIELD_BY_COMPANYID_AND_COLUMNNAME, new ComProfileField_RowMapper(), companyID, columnName);
				if (profileFieldList == null || profileFieldList.size() < 1) {
					ComProfileField dbOnlyField = new ComProfileFieldImpl();
					dbOnlyField.setCompanyID(companyID);
					dbOnlyField.setColumn(columnName);
					dbOnlyField.setShortname(columnName);
					dbOnlyField.setDataType(columnType.getTypeName());
					dbOnlyField.setDataTypeLength(columnType.getCharacterLength());
					dbOnlyField.setNumericPrecision(columnType.getNumericPrecision());
					dbOnlyField.setNumericScale(columnType.getNumericScale());
					dbOnlyField.setNullable(columnType.isNullable());
					dbOnlyField.setDefaultValue(DbUtilities.getColumnDefaultValue(getDataSource(), "customer_" + companyID + "_tbl", columnName));
					dbOnlyField.setHiddenField(HIDDEN_COLUMNS.contains(columnName.trim()));
					return dbOnlyField;
				} else if (profileFieldList.size() > 1) {
					throw new RuntimeException("Invalid number of entries found in getProfileField: " + profileFieldList.size());
				} else {
					ComProfileField comProfileField = profileFieldList.get(0);
					comProfileField.setCompanyID(companyID);
					comProfileField.setDataType(columnType.getTypeName());
					comProfileField.setDataTypeLength(columnType.getCharacterLength());
					comProfileField.setNumericPrecision(columnType.getNumericPrecision());
					comProfileField.setNumericScale(columnType.getNumericScale());
					comProfileField.setNullable(columnType.isNullable());
					comProfileField.setCreationDate(profileFieldList.get(0).getCreationDate());
					comProfileField.setChangeDate(profileFieldList.get(0).getChangeDate());
					comProfileField.setHiddenField(HIDDEN_COLUMNS.contains(columnName.trim()));
					return comProfileField;
				}
			}
		}
	}

    @Override
    public ComProfileField getProfileField(@VelocityCheck int companyID, String columnName, int adminID) throws Exception {
    	if (companyID <= 0) {
			return null;
		} else {
            ComProfileField profileField = getProfileField(companyID, columnName);
            if (profileField == null) {
            	return null;
            } else {
            	List<ComProfileFieldPermission> profileFieldPermissionList = select(logger, SELECT_PROFILEFIELDPERMISSION, new ComProfileFieldPermission_RowMapper(), companyID, profileField.getColumn(), adminID);
            	if (profileFieldPermissionList == null || profileFieldPermissionList.size() < 1) {
    				return profileField;
    			} else if (profileFieldPermissionList.size() > 1) {
    				throw new RuntimeException("Invalid number of permission entries found in getProfileField: " + profileFieldPermissionList.size());
    			} else {
    				profileField.setAdminID(adminID);
    				profileField.setModeEdit(profileFieldPermissionList.get(0).getModeEdit());
    				return profileField;
    			}
            }
		}
    }
	
	@Override
	public ComProfileField getProfileFieldByShortname(@VelocityCheck int companyID, String shortName) throws Exception {
		if (companyID <= 0) {
			return null;
		} else {
			List<ComProfileField> profileFieldList = select(logger, SELECT_PROFILEFIELD_BY_COMPANYID_AND_SHORTNAME, new ComProfileField_RowMapper(), companyID, shortName);
			if (profileFieldList == null || profileFieldList.size() < 1) {
				return null;
			} else if (profileFieldList.size() > 1) {
				throw new RuntimeException("Invalid number of entries found in getProfileFieldByShortname: " + profileFieldList.size());
			} else {
				ComProfileField comProfileField = profileFieldList.get(0);
				DbColumnType columnType = DbUtilities.getColumnDataType(getDataSource(), "customer_" + companyID + "_tbl", comProfileField.getColumn());
				if (columnType == null) {
	            	return null;
	            } else {
					comProfileField.setDataType(columnType.getTypeName());
					comProfileField.setDataTypeLength(columnType.getCharacterLength());
					comProfileField.setNumericPrecision(columnType.getNumericPrecision());
					comProfileField.setNumericScale(columnType.getNumericScale());
					comProfileField.setNullable(columnType.isNullable());
					
					return comProfileField;
	            }
			}
		}
	}
	
	@Override
	public ComProfileField getProfileFieldByShortname(@VelocityCheck int companyID, String shortName, int adminID) throws Exception {
		if (companyID <= 0) {
			return null;
		} else {
			ComProfileField profileField = getProfileFieldByShortname(companyID, shortName);
            if (profileField == null) {
            	return null;
            } else {
            	List<ComProfileFieldPermission> profileFieldPermissionList = select(logger, SELECT_PROFILEFIELDPERMISSION, new ComProfileFieldPermission_RowMapper(), companyID, profileField.getColumn(), adminID);
            	if (profileFieldPermissionList == null || profileFieldPermissionList.size() < 1) {
    				return profileField;
    			} else if (profileFieldPermissionList.size() > 1) {
    				throw new Exception("Invalid number of permission entries found in getProfileFieldByShortname: " + profileFieldPermissionList.size());
    			} else {
    				profileField.setAdminID(adminID);
    				profileField.setModeEdit(profileFieldPermissionList.get(0).getModeEdit());
    				return profileField;
    			}
            }
		}
	}
	
	@Override
	public List<ProfileField> getProfileFields(@VelocityCheck int companyID) throws Exception {
		if (companyID <= 0) {
			return null;
		} else {
			CaseInsensitiveMap<String, ComProfileField> comProfileFieldMap = getComProfileFieldsMap(companyID, false);
			List<ComProfileField> comProfileFieldList = new ArrayList<>(comProfileFieldMap.values());
			
			// Sort by SortingIndex or shortname
			sortComProfileList(comProfileFieldList);

			// Convert from List<ComProfileField> to List<ProfileField>
			List<ProfileField> returnList = new ArrayList<>();
			returnList.addAll(comProfileFieldList);
			
			return returnList;
		}
	}
	
	@Override
	public List<ProfileField> getProfileFields(@VelocityCheck int companyID, int adminID) throws Exception {
		List<ComProfileField> comProfileFieldList = getComProfileFields(companyID, adminID);
		if (comProfileFieldList == null) {
			return null;
		} else {
			List<ProfileField> profileFieldList = new ArrayList<>();
			profileFieldList.addAll(comProfileFieldList);
			return profileFieldList;
		}
	}

	@Override
	public List<ComProfileField> getComProfileFields(int companyID) throws Exception {
		if (companyID <= 0) {
			return null;
		} else {
			CaseInsensitiveMap<String, ComProfileField> comProfileFieldMap = getComProfileFieldsMap(companyID);
			List<ComProfileField> comProfileFieldList = new ArrayList<>(comProfileFieldMap.values());
			
			// Sort by SortingIndex or shortname
			sortComProfileList(comProfileFieldList);
			
			return comProfileFieldList;
		}
	}
	
	@Override
	public List<ComProfileField> getComProfileFields(@VelocityCheck int companyID, int adminID) throws Exception {
		return getComProfileFields(companyID, adminID, false);
	}

	@Override
	public List<ComProfileField> getComProfileFields(@VelocityCheck int companyID, int adminID, boolean customSorting) throws Exception {
		return getComProfileFields(companyID, adminID, customSorting, false);
	}
	
    public List<ComProfileField> getComProfileFields(@VelocityCheck int companyID, int adminID, boolean customSorting, final boolean noNotNullConstraintCheck) throws Exception {
		if (companyID <= 0) {
			return null;
		} else {
			CaseInsensitiveMap<String, ComProfileField> comProfileFieldMap = getComProfileFieldsMap(companyID, adminID, noNotNullConstraintCheck);
			List<ComProfileField> comProfileFieldList = new ArrayList<>(comProfileFieldMap.values());

			// Sort by SortingIndex or shortname
            if (customSorting) {
			    sortCustomComProfileList(comProfileFieldList);
            }
            // Sort by shortname (or by column if shortname is empty)
            else {
                sortComProfileList(comProfileFieldList);
            }

			return comProfileFieldList;
		}
	}
	
	@Override
	public CaseInsensitiveMap<String, ProfileField> getProfileFieldsMap(@VelocityCheck int companyID) throws Exception {
		if (companyID <= 0) {
			return null;
		} else {
			CaseInsensitiveMap<String, ComProfileField> comProfileFieldMap = getComProfileFieldsMap(companyID);
			CaseInsensitiveMap<String, ProfileField> returnMap = new CaseInsensitiveMap<>();

			for (Entry<String, ComProfileField> entry : comProfileFieldMap.entrySet()) {
				returnMap.put(entry.getKey(), entry.getValue());
			}
			
			return returnMap;
		}
	}
	
	@Override
	public CaseInsensitiveMap<String, ComProfileField> getComProfileFieldsMap(@VelocityCheck int companyID) throws Exception {
		return getComProfileFieldsMap(companyID, true);
	}

	@Override
	public CaseInsensitiveMap<String, ComProfileField> getComProfileFieldsMap(@VelocityCheck int companyID, boolean determineDefaultValues) throws Exception {
		return getComProfileFieldsMap(companyID, determineDefaultValues, false);
	}

	private CaseInsensitiveMap<String, ComProfileField> getComProfileFieldsMap(@VelocityCheck int companyID, boolean determineDefaultValues, boolean excludeNonHistorized) throws Exception {
		if (companyID <= 0) {
			return null;
		} else {
            CaseInsensitiveMap<String, ComProfileField> returnMap = new CaseInsensitiveMap<>();

            String sqlSelectFields = excludeNonHistorized ? SELECT_PROFILEFIELDS_BY_COMPANYID_HISTORIZEDONLY : SELECT_PROFILEFIELDS_BY_COMPANYID;
			CaseInsensitiveMap<String, ComProfileField> customFieldsMap = new CaseInsensitiveMap<>();
            for (ComProfileField field : select(logger, sqlSelectFields, new ComProfileField_RowMapper(), companyID)) {
                customFieldsMap.put(field.getColumn(), field);
            }

            CaseInsensitiveMap<String, DbColumnType> dbDataTypes = DbUtilities.getColumnDataTypes(getDataSource(), "customer_" + companyID + "_tbl");
			// Exclude this one according to AGNEMM-1817, AGNEMM-1924 and AGNEMM-1925
			dbDataTypes.remove(ComCompanyDaoImpl.STANDARD_FIELD_BOUNCELOAD);

			for (Entry<String, DbColumnType> entry : dbDataTypes.entrySet()) {
				String columnName = entry.getKey();
				ComProfileField field = customFieldsMap.get(columnName);

				if (field == null) {
                    if (excludeNonHistorized && !RecipientProfileHistoryUtil.isDefaultColumn(columnName)) {
                        continue;
                    }

					field = new ComProfileFieldImpl();
					field.setCompanyID(companyID);
					field.setColumn(columnName);
					field.setShortname(columnName);

					if (determineDefaultValues) {
						field.setDefaultValue(DbUtilities.getColumnDefaultValue(getDataSource(), "customer_" + companyID + "_tbl", columnName));
					}
				}

				DbColumnType columnType = dbDataTypes.get(field.getColumn());
				field.setDataType(columnType.getTypeName());
				field.setDataTypeLength(columnType.getCharacterLength());
				field.setNumericPrecision(columnType.getNumericPrecision());
				field.setNumericScale(columnType.getNumericScale());
				field.setNullable(columnType.isNullable());
				
				//fields are shown as read only in recipient view
				if (field.getColumn().equalsIgnoreCase("creation_date") ||
						field.getColumn().equalsIgnoreCase("timestamp") ||
						field.getColumn().equalsIgnoreCase("datasource_id") ||
						field.getColumn().equalsIgnoreCase("lastclick_date") ||
						field.getColumn().equalsIgnoreCase("lastopen_date") ||
						field.getColumn().equalsIgnoreCase("lastsend_date") ||
						field.getColumn().equalsIgnoreCase("customer_id") ||
						field.getColumn().equalsIgnoreCase("latest_datasource_id")) {
					field.setModeEdit(ProfileField.MODE_EDIT_READONLY);
				}
				// determines not customer's fields (not hidden fields was created by customer)
				field.setHiddenField(HIDDEN_COLUMNS.contains(field.getColumn().trim()));

				returnMap.put(field.getColumn(), field);
			}
			return returnMap;
		}
	}

	@Override
	public CaseInsensitiveMap<String, ProfileField> getProfileFieldsMap(@VelocityCheck int companyID, int adminID) throws Exception {
		return getProfileFieldsMap(companyID, adminID, false);
	}
	
	public CaseInsensitiveMap<String, ProfileField> getProfileFieldsMap(@VelocityCheck int companyID, int adminID, final boolean noNotNullConstraintCheck) throws Exception {
		if (companyID == 0) {
			return null;
		} else {
			CaseInsensitiveMap<String, ComProfileField> comProfileFieldMap = getComProfileFieldsMap(companyID, adminID, noNotNullConstraintCheck);
			CaseInsensitiveMap<String, ProfileField> returnMap = new CaseInsensitiveMap<>();

			for (Entry<String, ComProfileField> entry : comProfileFieldMap.entrySet()) {
				returnMap.put(entry.getKey(), entry.getValue());
			}
			return returnMap;
		}
	}

    @Override
    public CaseInsensitiveMap<String, ComProfileField> getComProfileFieldsMap(@VelocityCheck int companyID, int adminID) throws Exception {
    	return getComProfileFieldsMap(companyID, adminID, false);
    }
    
    public CaseInsensitiveMap<String, ComProfileField> getComProfileFieldsMap(@VelocityCheck int companyID, int adminID, final boolean noNotNullConstraintCheck) throws Exception {
        if (companyID <= 0) {
            return null;
        } else {
			CaseInsensitiveMap<String, ComProfileField> comProfileFieldMap = getComProfileFieldsMap(companyID, noNotNullConstraintCheck);
			CaseInsensitiveMap<String, ComProfileField> returnMap = new CaseInsensitiveMap<>();
			for (ComProfileField comProfileField : comProfileFieldMap.values()) {
				List<ComProfileFieldPermission> profileFieldPermissionList = select(logger, SELECT_PROFILEFIELDPERMISSION, new ComProfileFieldPermission_RowMapper(), companyID, comProfileField.getColumn(), adminID);
            	if (profileFieldPermissionList != null && profileFieldPermissionList.size() > 1) {
    				throw new RuntimeException("Invalid number of permission entries found in getProfileFields: " + profileFieldPermissionList.size());
    			} else if (profileFieldPermissionList != null && profileFieldPermissionList.size() == 1) {
    				comProfileField.setAdminID(adminID);
    				comProfileField.setModeEdit(profileFieldPermissionList.get(0).getModeEdit());
    				returnMap.put(comProfileField.getColumn(), comProfileField);
    			} else {
    				returnMap.put(comProfileField.getColumn(), comProfileField);
    			}
			}
			return returnMap;
        }
    }

    @Override
    public List<ComProfileField> getProfileFieldsWithIndividualSortOrder(@VelocityCheck int companyID, int adminID) throws Exception {
		if (companyID <= 0) {
			return null;
		} else {
			List<ComProfileField> comProfileFieldList = select(logger, SELECT_PROFILEFIELDS_BY_COMPANYID_HAVINGSORT, new ComProfileField_RowMapper(), companyID);
			CaseInsensitiveMap<String, DbColumnType> dbDataTypes = DbUtilities.getColumnDataTypes(getDataSource(), "customer_" + companyID + "_tbl");
			List<ComProfileField> returnList = new ArrayList<>();
			for (ComProfileField comProfileField : comProfileFieldList) {
				boolean found = false;
				for (String columnName : dbDataTypes.keySet()) {
					if (columnName.equalsIgnoreCase(comProfileField.getColumn())) {
						found = true;
						break;
					}
				}
				if (found) {
					DbColumnType columnType = dbDataTypes.get(comProfileField.getColumn());
					comProfileField.setDataType(columnType.getTypeName());
					comProfileField.setDataTypeLength(columnType.getCharacterLength());
					comProfileField.setNumericPrecision(columnType.getNumericPrecision());
					comProfileField.setNumericScale(columnType.getNumericScale());
					comProfileField.setNullable(columnType.isNullable());
					
					List<ComProfileFieldPermission> profileFieldPermissionList = select(logger, SELECT_PROFILEFIELDPERMISSION, new ComProfileFieldPermission_RowMapper(), companyID, comProfileField.getColumn(), adminID);
	            	if (profileFieldPermissionList != null && profileFieldPermissionList.size() > 1) {
	    				throw new RuntimeException("Invalid number of permission entries found in getProfileFieldsWithIndividualSortOrder: " + profileFieldPermissionList.size());
	    			} else if (profileFieldPermissionList != null && profileFieldPermissionList.size() == 1) {
	    				comProfileField.setAdminID(adminID);
	    				comProfileField.setModeEdit(profileFieldPermissionList.get(0).getModeEdit());
	    				returnList.add(comProfileField);
	    			} else {
	    				returnList.add(comProfileField);
	    			}
				}
			}

			// Sort by SortingIndex or shortname
			sortCustomComProfileList(returnList);
			
			return returnList;
		}
    }

    @Override
    public List<ComProfileField> getProfileFieldsWithInterest(@VelocityCheck int companyID, int adminID) throws Exception {
		if (companyID <= 0) {
			return null;
		} else {
			List<ComProfileField> comProfileFieldList = select(logger, SELECT_PROFILEFIELDS_BY_COMPANYID_HAVINGINTEREST, new ComProfileField_RowMapper(), companyID);
			CaseInsensitiveMap<String, DbColumnType> dbDataTypes = DbUtilities.getColumnDataTypes(getDataSource(), "customer_" + companyID + "_tbl");
			List<ComProfileField> returnList = new ArrayList<>();
			for (ComProfileField comProfileField : comProfileFieldList) {
				boolean found = false;
				for (String columnName : dbDataTypes.keySet()) {
					if (columnName.equalsIgnoreCase(comProfileField.getColumn())) {
						found = true;
						break;
					}
				}
				if (found) {
					DbColumnType columnType = dbDataTypes.get(comProfileField.getColumn());
					comProfileField.setDataType(columnType.getTypeName());
					comProfileField.setDataTypeLength(columnType.getCharacterLength());
					comProfileField.setNumericPrecision(columnType.getNumericPrecision());
					comProfileField.setNumericScale(columnType.getNumericScale());
					comProfileField.setNullable(columnType.isNullable());
					
					List<ComProfileFieldPermission> profileFieldPermissionList = select(logger, SELECT_PROFILEFIELDPERMISSION, new ComProfileFieldPermission_RowMapper(), companyID, comProfileField.getColumn(), adminID);
	            	if (profileFieldPermissionList != null && profileFieldPermissionList.size() > 1) {
	    				throw new RuntimeException("Invalid number of permission entries found in getProfileFieldsWithIndividualSortOrder: " + profileFieldPermissionList.size());
	    			} else if (profileFieldPermissionList != null && profileFieldPermissionList.size() == 1) {
	    				comProfileField.setAdminID(adminID);
	    				comProfileField.setModeEdit(profileFieldPermissionList.get(0).getModeEdit());
	    				returnList.add(comProfileField);
	    			} else {
	    				returnList.add(comProfileField);
	    			}
				}
			}

			// Sort by SortingIndex or shortname
			sortComProfileList(returnList);
			
			return returnList;
		}
    }

	@Override
	public List<ComProfileField> getHistorizedProfileFields(@VelocityCheck int companyID) throws Exception {
		CaseInsensitiveMap<String, ComProfileField> map = getComProfileFieldsMap(companyID, false, true);
		if (map == null) {
			return null;
		}

		// Sort by SortingIndex or shortname
		return sortComProfileList(map);
	}

	/**
	 * This method changes the ProfileField entry only. DB-ColumnChanges must be done previously.
	 */
	@Override
	@DaoUpdateReturnValueCheck
    public boolean saveProfileField(ProfileField field, ComAdmin admin) throws Exception {
		if (!(field instanceof ComProfileField)) {
			logger.error("Invalid type of ProfileField for storage. Expected type is ComProfileField");
			throw new RuntimeException("Invalid type of ProfileField for storage. Expected type is ComProfileField");
		} else {
			ComProfileField comProfileField = (ComProfileField) field;
			ComProfileField previousProfileField = getProfileField(comProfileField.getCompanyID(), comProfileField.getColumn());
			
			if (("NUMBER".equalsIgnoreCase(comProfileField.getDataType()) || "FLOAT".equalsIgnoreCase(comProfileField.getDataType()) || "DOUBLE".equalsIgnoreCase(comProfileField.getDataType()) || "INTEGER".equalsIgnoreCase(comProfileField.getDataType())) && StringUtils.isNotBlank(comProfileField.getDefaultValue())) {
				comProfileField.setDefaultValue(AgnUtils.normalizeNumber(admin.getLocale(), comProfileField.getDefaultValue()));
			}

			String[] allowedValues = comProfileField.getAllowedValues();
			String allowedValuesJson = null;
			if (allowedValues != null) {
				JSONArray array = new JSONArray();
				array.addAll(Arrays.asList(allowedValues));
				allowedValuesJson = array.toString();
			}

    		if (previousProfileField == null) {
    			// Check if new shortname already exists before a new column is added to dbtable
    			if (getProfileFieldByShortname(comProfileField.getCompanyID(), comProfileField.getShortname()) != null) {
    				throw new Exception("New shortname for customerprofilefield already exists");
    			}

    			// Change DB Structure if needed (throws an Exception if change is not possible)
    			boolean createdDbField = addColumnToDbTable(comProfileField.getCompanyID(), comProfileField.getColumn(), comProfileField.getDataType(), comProfileField.getDataTypeLength(), comProfileField.getDefaultValue(), !comProfileField.getNullable());
    			if (!createdDbField) {
    				throw new Exception("DB-field could not be created");
    			}
    			
    			// Shift other entries if needed
    			if (comProfileField.getSort() < MAX_SORT_INDEX) {
    				update(logger, "UPDATE " + TABLE + " SET " + FIELD_SORT + " = " + FIELD_SORT + " + 1 WHERE " + FIELD_SORT + " < " + MAX_SORT_INDEX + " AND " + FIELD_SORT + " >= ?", comProfileField.getSort());
    			}
    			
    			// Insert new entry
    			String statementString = "INSERT INTO " + TABLE + " (" + FIELD_COMPANY_ID + ", " + FIELD_COLUMN_NAME + ", " + FIELD_ADMIN_ID + ", " + FIELD_SHORTNAME + ", " + FIELD_DESCRIPTION + ", " + FIELD_DEFAULT_VALUE + ", " + FIELD_MODE_EDIT + ", " + FIELD_MODE_INSERT + ", " + FIELD_LINE + ", " + FIELD_SORT + ", " + FIELD_ISINTEREST + ", " + FIELD_CREATION_DATE + ", " + FIELD_CHANGE_DATE + ", " + FIELD_HISTORIZE + ", " + FIELD_ALLOWED_VALUES + ") VALUES (?, UPPER(?), ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?)";
    			update(logger, statementString, field.getCompanyID(), field.getColumn(), field.getAdminID(), field.getShortname().trim(), field.getDescription(), field.getDefaultValue(), field.getModeEdit(), field.getModeInsert(), comProfileField.getLine(), comProfileField.getSort(), comProfileField.getInterest(), comProfileField.getHistorize(), allowedValuesJson);
    		} else {
    			// Check if new shortname already exists before a new column is added to dbtable
    			if (!previousProfileField.getShortname().equals(comProfileField.getShortname())
    					&& getProfileFieldByShortname(comProfileField.getCompanyID(), comProfileField.getShortname()) != null) {
    				throw new Exception("New shortname for customerprofilefield already exists");
    			}

    			// Change DB Structure if needed (throws an Exception if change is not possible)
    			if (comProfileField.getDataType() != null) {
	    			boolean alteredDbField = alterColumnTypeInDbTable(comProfileField.getCompanyID(), comProfileField.getColumn(), comProfileField.getDataType(), comProfileField.getDataTypeLength(), comProfileField.getDefaultValue(), !comProfileField.getNullable());
	    			if (!alteredDbField) {
	    				throw new Exception("DB-field could not be changed");
	    			}
    			}
    			
    			// Shift other entries if needed
    			if (comProfileField.getSort() != previousProfileField.getSort()) {
	    			if (comProfileField.getSort() < MAX_SORT_INDEX) {
	    				if (comProfileField.getSort() < previousProfileField.getSort()) {
	    					update(logger, "UPDATE " + TABLE + " SET " + FIELD_SORT + " = " + FIELD_SORT + " + 1 WHERE " + FIELD_SORT + " < " + MAX_SORT_INDEX + " AND " + FIELD_SORT + " >= ? AND " + FIELD_SORT + " < ?", comProfileField.getSort(), previousProfileField.getSort());
	    				} else {
	    					update(logger, "UPDATE " + TABLE + " SET " + FIELD_SORT + " = " + FIELD_SORT + " - 1 WHERE " + FIELD_SORT + " < " + MAX_SORT_INDEX + " AND " + FIELD_SORT + " > ? AND " + FIELD_SORT + " <= ?", comProfileField.getSort(), previousProfileField.getSort());
	    				}
	    			} else if (previousProfileField.getSort() < MAX_SORT_INDEX) {
	    				update(logger, "UPDATE " + TABLE + " SET " + FIELD_SORT + " = " + FIELD_SORT + " - 1 WHERE " + FIELD_SORT + " < " + MAX_SORT_INDEX + " AND " + FIELD_SORT + " > ?", previousProfileField.getSort());
	    			}
    			}
    			
    			if (selectInt(logger, "SELECT COUNT(*) FROM " + TABLE + " WHERE " + FIELD_COMPANY_ID + " = ? AND LOWER(" + FIELD_COLUMN_NAME + ") = LOWER(?)", field.getCompanyID(), field.getColumn()) < 1) {
        			// Insert new entry for some manually by db-support in db added fields
    				String statementString = "INSERT INTO " + TABLE + " (" + FIELD_COMPANY_ID + ", " + FIELD_COLUMN_NAME + ", " + FIELD_ADMIN_ID + ", " + FIELD_SHORTNAME + ", " + FIELD_DESCRIPTION + ", " + FIELD_DEFAULT_VALUE + ", " + FIELD_MODE_EDIT + ", " + FIELD_MODE_INSERT + ", " + FIELD_LINE + ", " + FIELD_SORT + ", " + FIELD_ISINTEREST + ", " + FIELD_CREATION_DATE + ", " + FIELD_CHANGE_DATE + ", " + FIELD_HISTORIZE + ", " + FIELD_ALLOWED_VALUES + ") VALUES (?, UPPER(?), ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?, ?)";
        			update(logger, statementString, field.getCompanyID(), field.getColumn(), field.getAdminID(), field.getShortname().trim(), field.getDescription(), field.getDefaultValue(), field.getModeEdit(), field.getModeInsert(), comProfileField.getLine(), comProfileField.getSort(), comProfileField.getInterest(), comProfileField.getHistorize(), allowedValuesJson);
    			} else {
	    			// Update existing entry
	    			update(logger, "UPDATE " + TABLE + " SET " + FIELD_SHORTNAME + " = ?, " + FIELD_DESCRIPTION + " = ?, " + FIELD_DEFAULT_VALUE + " = ?, " + FIELD_MODE_EDIT + " = ?, " + FIELD_MODE_INSERT + " = ?, " + FIELD_LINE + " = ?, " + FIELD_SORT + " = ?, " + FIELD_ISINTEREST + " = ?, " + FIELD_CHANGE_DATE + " = CURRENT_TIMESTAMP, " + FIELD_HISTORIZE + " = ?, " + FIELD_ALLOWED_VALUES + " = ? WHERE " + FIELD_COMPANY_ID + " = ? AND UPPER(" + FIELD_COLUMN_NAME + ") = UPPER(?) AND " + FIELD_ADMIN_ID + " IN (0, ?)",
	   					field.getShortname().trim(), field.getDescription(), field.getDefaultValue(), field.getModeEdit(), field.getModeInsert(), comProfileField.getLine(), comProfileField.getSort(), comProfileField.getInterest(), comProfileField.getHistorize(), allowedValuesJson, field.getCompanyID(), field.getColumn(), field.getAdminID());
    			}
    		}

    		doPostProcessing(field.getCompanyID());
    		
    		return true;
    	}
    }
    
    @Override
    public boolean mayAdd(@VelocityCheck int companyID) {
    	try {
			if (companyID <= 0) {
	    		return false;
	    	} else {
	    		int maxFields = getMaximumFieldCount(companyID);
				
				int currentFieldCount = DbUtilities.getColumnCount(getDataSource(), "customer_" + companyID + "_tbl");
				
				return currentFieldCount < maxFields;
	    	}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return false;
		}
	}

	@Override
	public boolean isNearLimit(@VelocityCheck int companyID) {
		try {
			if (companyID <= 0) {
	    		return false;
	    	} else {
	    		int maxFields = getMaximumFieldCount(companyID);
				
				int currentFieldCount = DbUtilities.getColumnCount(getDataSource(), "customer_" + companyID + "_tbl");
				
				return maxFields - 5 <= currentFieldCount && currentFieldCount < maxFields;
	    	}
		} catch (Exception e) {
			logger.error(e.getMessage(), e);
			return false;
		}
	}
	
	@Override
	public int getMaximumFieldCount(@VelocityCheck int companyID) throws Exception {
		int systemMaxFields = configService.getIntegerValue(ConfigValue.System_License_MaximumNumberOfProfileFields);
		int companyMaxFields = selectIntWithDefaultValue(logger, "SELECT max_fields FROM company_tbl WHERE company_id = ?", 0, companyID);
		if (companyMaxFields == 0) {
			companyMaxFields = configService.getIntegerValue(ConfigValue.MaxFields, companyID);
		}
		int maxFields;
		int standardFieldsCount = ComCompanyDaoImpl.STANDARD_CUSTOMER_FIELDS.length;
		
		// Socialmedia fields to be ignored in limit checks for profile field counts until they are removed entirely in all client tables
		for (String fieldName : ComCompanyDaoImpl.OLD_SOCIAL_MEDIA_FIELDS) {
			if (DbUtilities.checkTableAndColumnsExist(getDataSource(), "customer_" + companyID + "_tbl", fieldName)) {
				standardFieldsCount++;
			}
		}
		
		if (companyMaxFields < systemMaxFields || systemMaxFields < 0) {
			maxFields = companyMaxFields + standardFieldsCount;
		} else {
			maxFields = systemMaxFields + standardFieldsCount;
		}
		return maxFields;
	}
	
	@Override
	public int getCurrentFieldCount(@VelocityCheck int companyID) throws Exception {
		int currentFieldCount = DbUtilities.getColumnCount(getDataSource(), "customer_" + companyID + "_tbl");
		return currentFieldCount;
	}
	
	@Override
	public int getMaximumCompanySpecificFieldCount(@VelocityCheck int companyID) throws Exception {
		int systemMaxFields = configService.getIntegerValue(ConfigValue.System_License_MaximumNumberOfProfileFields);
		int companyMaxFields = selectIntWithDefaultValue(logger, "SELECT max_fields FROM company_tbl WHERE company_id = ?", 0, companyID);
		if (companyMaxFields == 0) {
			companyMaxFields = configService.getIntegerValue(ConfigValue.MaxFields, companyID);
		}
		int maxCompanySpecificFields;
		if (companyMaxFields < systemMaxFields || systemMaxFields < 0) {
			maxCompanySpecificFields = companyMaxFields;
		} else {
			maxCompanySpecificFields = systemMaxFields;
		}
		return maxCompanySpecificFields;
	}
	
	@Override
	public int getCurrentCompanySpecificFieldCount(@VelocityCheck int companyID) throws Exception {
		int currentFieldCount = DbUtilities.getColumnCount(getDataSource(), "customer_" + companyID + "_tbl");
		int companySpecificFieldCount = currentFieldCount - ComCompanyDaoImpl.STANDARD_CUSTOMER_FIELDS.length;
		
		// Socialmedia fields to be ignored in limit checks for profile field counts until they are removed entirely in all client tables
		for (String fieldName : ComCompanyDaoImpl.OLD_SOCIAL_MEDIA_FIELDS) {
			if (DbUtilities.checkTableAndColumnsExist(getDataSource(), "customer_" + companyID + "_tbl", fieldName)) {
				companySpecificFieldCount--;
			}
		}
		return companySpecificFieldCount;
	}

	@Override
	public boolean addColumnToDbTable(@VelocityCheck int companyID, String fieldname, String fieldType, int length, String fieldDefault, boolean notNull) throws Exception {
		if (companyID <= 0) {
    		return false;
    	} else if (StringUtils.isBlank(fieldname)) {
    		return false;
    	} else if (StringUtils.isBlank(fieldType)) {
    		return false;
    	} else if (DbUtilities.containsColumnName(getDataSource(), "customer_" + companyID + "_tbl", fieldname)) {
			return false;
		} else if (!checkAllowedDefaultValue(companyID, fieldname, fieldDefault)) {
			throw new Exception("Table has too many entries to add a column with default value (>" + MAX_NUMBER_OF_ENTRIES_FOR_DEFAULT_CHANGE + ")");
		} else if (("FLOAT".equalsIgnoreCase(fieldType) || "DOUBLE".equalsIgnoreCase(fieldType) || "NUMBER".equalsIgnoreCase(fieldType) || "INTEGER".equalsIgnoreCase(fieldType)) && StringUtils.isNotBlank(fieldDefault) && !AgnUtils.isDouble(fieldDefault)) {
			// check for valid numerical default value failed
			throw new Exception("Invalid non-numerical default value");
		} else {
			boolean result = DbUtilities.addColumnToDbTable(getDataSource(), "customer_" + companyID + "_tbl", fieldname, fieldType, length, fieldDefault, notNull);
			
			doPostProcessing(companyID);
			
			return result;
		}
	}
	
	@Override
	public boolean alterColumnTypeInDbTable(@VelocityCheck int companyID, String fieldname, String fieldType, int length, String fieldDefault, boolean notNull) throws Exception {
		if (companyID <= 0) {
    		return false;
    	} else if (StringUtils.isBlank(fieldname)) {
    		return false;
    	} else if (StringUtils.isBlank(fieldType)) {
    		return false;
    	} else if (!DbUtilities.containsColumnName(getDataSource(), "customer_" + companyID + "_tbl", fieldname)) {
			return false;
		} else if (!checkAllowedDefaultValue(companyID, fieldname, fieldDefault)) {
			throw new Exception("Table has too many entries to add a column with default value (>" + MAX_NUMBER_OF_ENTRIES_FOR_DEFAULT_CHANGE + ")");
		} else if (("FLOAT".equalsIgnoreCase(fieldType) || "DOUBLE".equalsIgnoreCase(fieldType) || "NUMBER".equalsIgnoreCase(fieldType) || "INTEGER".equalsIgnoreCase(fieldType)) && StringUtils.isNotBlank(fieldDefault) && !AgnUtils.isDouble(fieldDefault)) {
			// check for valid numerical default value failed
			throw new Exception("Invalid non-numerical default value");
		} else {
			boolean result = DbUtilities.alterColumnDefaultValueInDbTable(getDataSource(), "customer_" + companyID + "_tbl", fieldname, fieldDefault, notNull);
			doPostProcessing(companyID);
			return result;
		}
	}

	@Override
	@DaoUpdateReturnValueCheck
	public void removeProfileField(@VelocityCheck int companyID, String fieldname) throws ProfileFieldException {
		if (companyID <= 0) {
			throw new RuntimeException("Invalid companyId for removeProfileField");
		} else if (StringUtils.isBlank(fieldname)) {
			throw new RuntimeException("Invalid columnName for removeProfileField");
		} else {
			for (String standardField : ComCompanyDaoImpl.STANDARD_CUSTOMER_FIELDS) {
				if (standardField.trim().equalsIgnoreCase(fieldname.trim())) {
					throw new RuntimeException("Invalid columnName for removeProfileField: Cannot remove standard columns");
				}
			}

			update(logger, "ALTER TABLE customer_" + companyID + "_tbl DROP COLUMN " + SafeString.getSafeDbColumnName(fieldname));
			
			update(logger, DELETE_PROFILEFIELD_BY_COLUMNNAME, companyID, fieldname);
			
			try {
				doPostProcessing(companyID);
			} catch(final Exception e) {
				final String msg = String.format("Post processing after deletion of profile field '%s' (company ID %d) failed", fieldname, companyID);
				
				logger.error(msg, e);
				
				throw new ProfileFieldException(msg, e);
			}
		}
	}
	
	@Override
	public boolean deleteByCompany(@VelocityCheck int companyID) {
		int touchedLines = update(logger, "DELETE FROM customer_field_tbl WHERE company_id= ?", companyID);
		if (touchedLines > 0) {
    		return true;
    	} else {
    		int remaining = selectInt(logger, "SELECT COUNT(*) FROM customer_field_tbl WHERE company_id = ?", companyID);
    		return remaining == 0;
    	}
	}

	private List<ComProfileField> sortComProfileList(CaseInsensitiveMap<String, ComProfileField> map) {
		List<ComProfileField> fields = new ArrayList<>(map.values());
		sortComProfileList(fields);
		return fields;
	}

	private void sortComProfileList(List<ComProfileField> listToSort) {
		Collections.sort(listToSort, new Comparator<ComProfileField>() {
			@Override
			public int compare(ComProfileField comProfileField1, ComProfileField comProfileField2) {
                String compareString1 = comProfileField1.getShortname();
                if (StringUtils.isEmpty(compareString1)) {
                    compareString1 = comProfileField1.getColumn();
                }
                String compareString2 = comProfileField2.getShortname();
                if (StringUtils.isEmpty(compareString2)) {
                    compareString2 = comProfileField2.getColumn();
                }
                if (compareString1 != null && compareString2 != null) {
                    return compareString1.toLowerCase().compareTo(compareString2.toLowerCase());
                } else if (compareString1 != null) {
                    return 1;
                } else {
                    return -1;
                }
			}
		});
	}

    private void sortCustomComProfileList(List<ComProfileField> listToSort) {
		Collections.sort(listToSort, new Comparator<ComProfileField>() {
			@Override
			public int compare(ComProfileField comProfileField1, ComProfileField comProfileField2) {
				if (comProfileField1.getSort() < MAX_SORT_INDEX && comProfileField2.getSort() < MAX_SORT_INDEX) {
					if (comProfileField1.getSort() < comProfileField2.getSort()) {
						return -1;
					} else if (comProfileField1.getSort() == comProfileField2.getSort()) {
						String shortname1 = comProfileField1.getShortname();
						String shortname2 = comProfileField2.getShortname();
						if (shortname1 != null && shortname2 != null) {
							return shortname1.toLowerCase().compareTo(shortname2.toLowerCase());
						} else if (shortname1 != null) {
							return 1;
						} else {
							return -1;
						}
					} else {
						return 1;
					}
				} else if (comProfileField1.getSort() < MAX_SORT_INDEX) {
					return -1;
				} else if (comProfileField2.getSort() < MAX_SORT_INDEX) {
					return 1;
				} else {
					String shortname1 = comProfileField1.getShortname();
					String shortname2 = comProfileField2.getShortname();
					if (shortname1 != null && shortname2 != null) {
						return shortname1.toLowerCase().compareTo(shortname2.toLowerCase());
					} else if (shortname1 != null) {
						return 1;
					} else {
						return -1;
					}
				}
			}
		});
	}
	
	protected String getDateDefaultValue(String fieldDefault) {
		if (fieldDefault == null) {
			return "null";
		} else if (fieldDefault.toLowerCase().equals("sysdate")) {
			return "CURRENT_TIMESTAMP";
		} else {
    		if (isOracleDB()) {
				// TODO: A fixed date format is not a good solution, should depend on language setting of the user
				/*
				 * Here raise a problem: The default value is not only used for the ALTER TABLE statement. It is also
				 * stored in customer_field_tbl.default_value as a string. A problem occurs, when two users with
				 * language settings with different date formats edit the profile field.
				 */
    			return "to_date('" + fieldDefault + "', 'DD.MM.YYYY HH24:MI:SS')";
    		} else {
    			return "'" + fieldDefault + "'";
    		}
		}
	}

    private class ComProfileField_RowMapper implements RowMapper<ComProfileField> {
		@Override
		public ComProfileField mapRow(ResultSet resultSet, int row) throws SQLException {
			ComProfileField readProfileField = new ComProfileFieldImpl();

			readProfileField.setCompanyID(resultSet.getInt(FIELD_COMPANY_ID));
			readProfileField.setShortname(resultSet.getString(FIELD_SHORTNAME));
			readProfileField.setDescription(resultSet.getString(FIELD_DESCRIPTION));
			readProfileField.setColumn(resultSet.getString(FIELD_COLUMN_NAME));
			readProfileField.setDefaultValue(resultSet.getString(FIELD_DEFAULT_VALUE));
			readProfileField.setModeEdit(resultSet.getInt(FIELD_MODE_EDIT));
			readProfileField.setModeInsert(resultSet.getInt(FIELD_MODE_INSERT));
			readProfileField.setCreationDate(resultSet.getTimestamp(FIELD_CREATION_DATE));
			readProfileField.setChangeDate(resultSet.getTimestamp(FIELD_CHANGE_DATE));
			readProfileField.setHistorize(resultSet.getBoolean(FIELD_HISTORIZE));

			Object sortObject = resultSet.getObject(FIELD_SORT);
			if (sortObject != null) {
				readProfileField.setSort(((Number)sortObject).intValue());
			} else {
				readProfileField.setSort(MAX_SORT_INDEX);
			}

			Object lineObject = resultSet.getObject(FIELD_LINE);
			if (lineObject != null) {
				readProfileField.setLine(((Number)lineObject).intValue());
			} else {
				readProfileField.setLine(0);
			}

			Object interestObject = resultSet.getObject(FIELD_ISINTEREST);
			if (interestObject != null) {
				readProfileField.setInterest(((Number)interestObject).intValue());
			} else {
				readProfileField.setInterest(0);
			}

			String allowedValuesJson = resultSet.getString(FIELD_ALLOWED_VALUES);
			String[] allowedValues = null;
			if (allowedValuesJson != null) {
				try {
					JSONArray array = JSONArray.fromObject(allowedValuesJson);
					allowedValues = new String[array.size()];
					for (int i = 0; i < array.size(); i++) {
						allowedValues[i] = array.getString(i);
					}
				} catch (JSONException e) {
					logger.error("Error occurred while parsing JSON: " + e.getMessage(), e);
				}
			}
			readProfileField.setAllowedValues(allowedValues);

			return readProfileField;
		}
	}

    private class ComProfileFieldPermission_RowMapper implements RowMapper<ComProfileFieldPermission> {
		@Override
		public ComProfileFieldPermission mapRow(ResultSet resultSet, int row) throws SQLException {
			ComProfileFieldPermission readProfileFieldPermission = new ComProfileFieldPermission();
			
			readProfileFieldPermission.setCompanyId(resultSet.getInt(FIELD_PERMISSION_COMPANY_ID));
			readProfileFieldPermission.setColumnName(resultSet.getString(FIELD_PERMISSION_COLUMN_NAME));
			readProfileFieldPermission.setAdminId(resultSet.getInt(FIELD_PERMISSION_ADMIN_ID));
			readProfileFieldPermission.setModeEdit(resultSet.getInt(FIELD_PERMISSION_MODE_EDIT));
			
			return readProfileFieldPermission;
		}
	}

	@Override
	public boolean checkAllowedDefaultValue(@VelocityCheck int companyID, String fieldname, String fieldDefault) throws Exception {
		if (DbUtilities.checkTableAndColumnsExist(getDataSource(), "customer_" + companyID + "_tbl", new String[] { fieldname })) {
			// Field already exists, so a new default value will only take effect on newly inserted entries, which should not take too much time
			return true;
		} else {
			// Field does not exist yet, so a default value which is not empty must be copied in every existing entry, which can take a lot of time
			return StringUtils.isEmpty(fieldDefault) || selectInt(logger, "SELECT COUNT(*) FROM customer_" + companyID + "_tbl") <= MAX_NUMBER_OF_ENTRIES_FOR_DEFAULT_CHANGE;
		}
	}
	
	@Override
	public final boolean checkProfileFieldExists(final int companyID, final String fieldNameOnDatabase) throws Exception {
		return DbUtilities.checkTableAndColumnsExist(getDataSource(), "customer_" + companyID + "_tbl", new String[] { fieldNameOnDatabase });
	}
	
	@Override
	public final int countCustomerEntries(final int companyID) {
		return selectInt(logger, "SELECT COUNT(*) FROM customer_" + companyID + "_tbl");
	}

	private void doPostProcessing(final int companyID) throws RecipientProfileHistoryException {
		if(this.configService.isRecipientProfileHistoryEnabled(companyID)) {
			if(logger.isInfoEnabled()) {
				logger.info(String.format("Profile field history is enabled form company %d - starting post-processing of profile field structure modification", companyID));
			}
			
			profileHistoryService.afterProfileFieldStructureModification(companyID);
		}

	}

	@Override
	public Set<String> listUserSelectedProfileFieldColumnsWithHistoryFlag(int companyID) {
		List<String> list = select(logger, "SELECT LOWER(col_name) FROM customer_field_tbl WHERE company_id = ? AND historize = 1", new StringRowMapper(), companyID);

		Set<String> set = new HashSet<>(list);
		set.removeAll(RecipientProfileHistoryUtil.DEFAULT_COLUMNS_FOR_HISTORY);
		
		return set;
	}

	@Override
	public boolean exists(String column, @VelocityCheck int companyId) {
		if (StringUtils.isBlank(column) || companyId <= 0) {
			return false;
		}

		if (ArrayUtils.contains(ComCompanyDaoImpl.STANDARD_CUSTOMER_FIELDS, column.toLowerCase())) {
			return true;
		}

		try {
			return DbUtilities.checkTableAndColumnsExist(getDataSource(), "customer_" + companyId + "_tbl", column);
		} catch (Exception e) {
			logger.fatal("Error occurred: " + e.getMessage(), e);
		}

		return false;
	}

	@Override
	public boolean isTrackableColumn(String column, @VelocityCheck int companyId) {
		String sqlCheckIsHistorized = "SELECT COUNT(*) FROM customer_field_tbl " +
				"WHERE company_id = ? AND LOWER(col_name) = ? AND historize = 1";

		if (StringUtils.isBlank(column) || companyId <= 0) {
			return false;
		}

		if (RecipientProfileHistoryUtil.isDefaultColumn(column)) {
			return true;
		}

		return selectInt(logger, sqlCheckIsHistorized, companyId, column.toLowerCase()) > 0;
	}

	private String getRecipientTableName(int companyId){
		return "customer_" + companyId + "_tbl";
	}

	@Override
	public DbColumnType getColumnType(@VelocityCheck int companyId, String columnName) {
		String recipientTable = getRecipientTableName(companyId);
		try {
			return DbUtilities.getColumnDataType(getDataSource(), recipientTable, columnName);
		} catch (Exception e) {
			return null;
		}
	}
}
