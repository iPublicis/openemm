/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package org.agnitas.emm.core.commons.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URL;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.agnitas.emm.core.velocity.VelocityCheck;
import org.agnitas.util.AgnUtils;
import org.agnitas.util.DataEncryptor;
import org.agnitas.util.DateUtilities;
import org.agnitas.util.Systemconfig;
import org.agnitas.util.Tuple;
import org.agnitas.util.XmlUtilities;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Required;
import org.w3c.dom.Document;

import com.agnitas.dao.ComAdminDao;
import com.agnitas.dao.ComCompanyDao;
import com.agnitas.dao.ConfigTableDao;
import com.agnitas.dao.impl.ConfigTableDaoImpl;
import com.agnitas.emm.core.Permission;
import com.agnitas.emm.core.supervisor.dao.ComSupervisorDao;
import com.agnitas.emm.wsmanager.dao.WebserviceUserDao;
import com.agnitas.service.LicenseError;
import com.agnitas.util.CryptographicUtilities;

/**
 * ConfigurationService for EMM
 * This class uses buffering of the values of the config_tbl for better performance.
 * The value for refreshing period is also stored in config_tbl and can be changed
 * manually with no need for restarting the server.
 * For refreshing the values the very next time the old period value will be used.
 * Afterwards the new one will take effect.
 * The value 0 means, there will be no buffering.
 */
public class ConfigService {
	
	/** The logger. */
	private static final transient Logger logger = Logger.getLogger(ConfigService.class);

	private static final String PUBLIC_LICENSE_KEYSTRING = "-----BEGIN PUBLIC KEY-----\nMIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCcdArGIy/hseE9bz53siYnClOQ\nABrRFRVs/zdN8HpweXxpFqa4SUcp9SFIjqgQ5l/FRdEE9EFc865oGZI1H2RK9Jl1\nb7NxFBwu6S4kWFpy+0Xlp+FCLMXVkBDxLB3vv96VR714n2bFh11/UanlfptqMYPQ\nq7gZCmP5Bc06ORaxrQIDAQAB\n-----END PUBLIC KEY-----";

    public static final int MAX_GENDER_VALUE_BASIC = 2;
    public static final int MAX_GENDER_VALUE_EXTENDED = 5;
    
    public static final int MAXIMUM_ALLOWED_MAILTYPE = 5;
    
    /** Application startup timestamp **/
    private static final Date STARTUP_TIME = new Date();
    
    private static ConfigService instance;

	/** DAO for access config table. */
	protected ConfigTableDao configTableDao;
	
	/** DAO for access company_info_tbl. */
	protected CompanyInfoDao companyInfoDao;
	
	/** DAO for access company table. */
	protected ComCompanyDao companyDao;
	
	/** DAO for access admin table. */
	protected ComAdminDao adminDao;
	
	/** DAO for access webservice user table. */
	protected WebserviceUserDao webserviceUserDao;
	
	/** DAO for access supervisor table. */
	protected ComSupervisorDao supervisorDao;
	
	/** DataEncryptor. */
	protected DataEncryptor dataEncryptor;

	private static Boolean IS_ORACLE_DB = null;
	private static Map<String, String> LICENSE_VALUES = null;
	private static Map<String, String> EMM_PROPERTIES_VALUES = null;
	private static Map<String, String> CONFIGURATIONVALUES = null;
	private static Calendar EXPIRATIONTIME = null;
	
	/**
	 * Method for gathering the ConfigService within JSP-files or in environments without the spring context like "Listener" and "Filter" defined in web.xml
	 * All other access to ConfigService should be done via spring context or dependency injection
	 * 
	 * see also: singleton pattern
	 */
	public static synchronized ConfigService getInstance() {
		if (instance == null) {
			instance = new ConfigService();
		}
		return instance;
	}
	
	public static boolean isOracleDB() {
		return BooleanUtils.toBoolean(IS_ORACLE_DB);
	}
	
	/**
	 * This method may only be called in mock-test environments, where there is no real db to detect its vendor
	 * 
	 * @param isOracleDB
	 */
	public static void setDbVendorForMockTestingIsOracleDB(boolean isOracleDB) {
		IS_ORACLE_DB = isOracleDB;
	}
	public static Boolean getDbVendorForMockTestingIsOracleDB() {
		return IS_ORACLE_DB;
	}
	
	public ConfigService() {
		instance = this;
	}
	
	// ----------------------------------------------------------------------------------------------------------------
	// Dependency Injection
	
	/**
	 * Set DAO accessing configuration in DB.
	 * 
	 * @param configTableDao DAO accessing configuration in DB
	 */
	@Required
	public void setConfigTableDao(ConfigTableDao configTableDao) {
		this.configTableDao = configTableDao;
		invalidateCache();
		LICENSE_VALUES = null;
	}
	
	@Required
	public void setCompanyInfoDao(CompanyInfoDao companyInfoDao) {
		this.companyInfoDao = companyInfoDao;
		invalidateCache();
		LICENSE_VALUES = null;
	}
	
	/**
	 * Set DAO accessing company data.
	 * 
	 * @param companyDao DAO accessing company data
	 */
	@Required
	public void setCompanyDao(ComCompanyDao companyDao) {
		this.companyDao = companyDao;
		invalidateCache();
		LICENSE_VALUES = null;
	}
	
	/**
	 * Set DAO accessing admin data.
	 * 
	 * @param adminDao DAO accessing admin data
	 */
	@Required
	public void setAdminDao(ComAdminDao adminDao) {
		this.adminDao = adminDao;
		invalidateCache();
		LICENSE_VALUES = null;
	}
	
	/**
	 * Set DAO accessing admin data.
	 * 
	 * @param adminDao DAO accessing admin data
	 */
	@Required
	public void setWebserviceUserDao(WebserviceUserDao webserviceUserDao) {
		this.webserviceUserDao = webserviceUserDao;
		invalidateCache();
		LICENSE_VALUES = null;
	}
	
	/**
	 * Set DAO accessing supervisors.
	 * 
	 * @param supervisorDao DAO accessing supervisors
	 */
	@Required
	public void setSupervisorDao(ComSupervisorDao supervisorDao) {
		this.supervisorDao = supervisorDao;
		invalidateCache();
		LICENSE_VALUES = null;
	}
	
	/**
	 * Set data encryptor.
	 * 
	 * @param dataEncryptor data encryptor
	 */
	@Required
	public void setDataEncryptor(DataEncryptor dataEncryptor) {
		this.dataEncryptor = dataEncryptor;
	}
	
	// ----------------------------------------------------------------------------------------------------------------
	// Business Logic

	private void invalidateCache() {
		CONFIGURATIONVALUES = null;
	}
	
	protected void refreshValues() {
		try {
			if (IS_ORACLE_DB == null && configTableDao != null) {
				// On ConfigService startup check db vendor
				IS_ORACLE_DB = ((ConfigTableDaoImpl) configTableDao).isOracleDB();
			}
			
			if (LICENSE_VALUES == null) {
				// On ConfigService startup read license data and check licensefile signature
				LICENSE_VALUES = readLicenseData();

				// On ConfigService startup check licensefile data
				checkLicenseData();
			}
			
			if (EMM_PROPERTIES_VALUES == null) {
				// On ConfigService startup read emm.properties file
				EMM_PROPERTIES_VALUES = readEmmPropertiesFile();
			}
			
			if (CONFIGURATIONVALUES == null || EXPIRATIONTIME == null || GregorianCalendar.getInstance().after(EXPIRATIONTIME)) {
				Map<String, String> newValues = new HashMap<>(EMM_PROPERTIES_VALUES);

				if (configTableDao != null) {
					for (Entry<String, String> entry : configTableDao.getAllEntries().entrySet()) {
						newValues.put(entry.getKey(), AgnUtils.replaceHomeVariables(entry.getValue()));
					}
				}

				if (companyInfoDao != null) {
					for (Entry<String, String> entry : companyInfoDao.getAllEntries().entrySet()) {
						newValues.put(entry.getKey(), AgnUtils.replaceHomeVariables(entry.getValue()));
					}
				}

				int minutes;
				if (newValues.containsKey(ConfigValue.ConfigurationExpirationMinutes.toString())) {
					minutes = NumberUtils.toInt(newValues.get(ConfigValue.ConfigurationExpirationMinutes.toString()));
				} else {
					minutes = NumberUtils.toInt(ConfigValue.ConfigurationExpirationMinutes.getDefaultValue());
				}

				if (minutes > 0) {
					GregorianCalendar nextExpirationTime = new GregorianCalendar();
					nextExpirationTime.add(GregorianCalendar.MINUTE, minutes);
					EXPIRATIONTIME = nextExpirationTime;
				} else {
					EXPIRATIONTIME = null;
				}
				
				newValues.putAll(LICENSE_VALUES);
				
				if (CONFIGURATIONVALUES == null) {
					logger.info("Initialized ConfigService with " + newValues.size() + " values");
				}
				
				CONFIGURATIONVALUES = newValues;
			}
		} catch (LicenseError e) {
			IS_ORACLE_DB = null;
			LICENSE_VALUES = null;
			EMM_PROPERTIES_VALUES = null;
			CONFIGURATIONVALUES = null;
			EXPIRATIONTIME = null;
			
			logger.error("LicenseError: " + e.getMessage());
			throw e;
		} catch (Exception e) {
			logger.error("Cannot refresh config data from database", e);
		}
	}

	private Map<String, String> readEmmPropertiesFile() throws Exception {
		try {
			Map<String, String> emmPropertiesMap = new HashMap<>();
			Properties properties = new Properties();
			properties.load(getClass().getClassLoader().getResourceAsStream("emm.properties"));
			for (String key : properties.stringPropertyNames()) {
				emmPropertiesMap.put(key, AgnUtils.replaceHomeVariables(properties.getProperty(key)));
			}
			return emmPropertiesMap;
		} catch (Exception e) {
			throw new Exception("Cannot read emm.properties: " + e.getMessage(), e);
		}
	}
	
	private Map<String, String> readLicenseData() {
		ClassLoader classLoader = ConfigService.class.getClassLoader();
		
		int licenseID;
		
		Map<String, String> licenseData = null;
		try (InputStream licenseStream = classLoader.getResourceAsStream("emm.license.xml")) {
			if (licenseStream == null) {
				throw new Exception("LicenseData emm.license.xml is missing");
			}
			
			Document licenseDocument = XmlUtilities.parseXMLDataAndXSDVerifyByDOM(IOUtils.toByteArray(licenseStream), "UTF-8", null);
			licenseData = XmlUtilities.getSimpleValuesOfNode(licenseDocument.getElementsByTagName("emm.license").item(0));
	
			// Handle different names
			licenseData.put(ConfigValue.System_Licence.toString(), licenseData.get("licenseID"));
			licenseData.remove("licenseID");

			licenseID = Integer.parseInt(licenseData.get(ConfigValue.System_Licence.toString()));
		} catch (Exception e) {
			throw new LicenseError("Error while reading license data: " + e.getMessage(), e);
		}
		
		if (licenseID == 0) {
			// OpenEMM: No signature check, but remove all data for company_id > 1
			if (companyDao != null) {
				companyDao.deactivateExtendedCompanies();
			}
		} else {
			try (InputStream licenseStream = classLoader.getResourceAsStream("emm.license.xml")) {
				if (licenseStream == null) {
					throw new Exception("LicenseData emm.license.xml is missing");
				}
				
				try (InputStream signatureStream = classLoader.getResourceAsStream("emm.license.xml.sig")) {
					if (signatureStream == null) {
						throw new Exception("LicenseSignature emm.license.xml.sig is missing");
					}
				
					byte[] signatureData = IOUtils.toByteArray(signatureStream);
					PublicKey publicKey = CryptographicUtilities.getPublicKeyFromString(PUBLIC_LICENSE_KEYSTRING);
					boolean success = CryptographicUtilities.verifyStream(licenseStream, publicKey, signatureData);
	
					if (!success) {
						throw new Exception("LicenseSignature emm.license.xml.sig is invalid");
					}
				}
			} catch (Exception e) {
				throw new LicenseError("Error while checking license: " + e.getMessage(), e);
			}
		}
		
		return licenseData;
	}

	private void checkLicenseData() throws Exception {
		// Check validity of license data to current db data
		
		if (LICENSE_VALUES == null) {
			throw new LicenseError("Missing License data");
		}
		
		if (configTableDao != null) {
			if (NumberUtils.toInt(LICENSE_VALUES.get(ConfigValue.System_Licence.toString())) > 0) {
				// Check or set license ID in DB
				try {
					String storedLicenseID = configTableDao.getAllEntries().get(ConfigValue.System_Licence.toString());
					if (StringUtils.isBlank(storedLicenseID)) {
						configTableDao.storeEntry("system", "licence", LICENSE_VALUES.get(ConfigValue.System_Licence.toString()));
						logger.info("Writing new LicenseID: " + LICENSE_VALUES.get(ConfigValue.System_Licence.toString()));
					} else if (!storedLicenseID.equals(LICENSE_VALUES.get(ConfigValue.System_Licence.toString()))) {
						throw new LicenseError("Invalid LicenseID", LICENSE_VALUES.get(ConfigValue.System_Licence.toString()), storedLicenseID);
					}
				} catch (SQLException e) {
					throw new LicenseError("Error while checking license id: " + e.getMessage(), e);
				}
			
				// Check license ID in licence.cfg file, if exists
				String	licenceCfgLicenseId = (new Systemconfig ()).get ("licence");
				
				if ((licenceCfgLicenseId != null) && (!licenceCfgLicenseId.equals(LICENSE_VALUES.get(ConfigValue.System_Licence.toString())))) {
					throw new LicenseError("Invalid LicenseID in licence.cfg", LICENSE_VALUES.get(ConfigValue.System_Licence.toString()), licenceCfgLicenseId);
				}
			}
		}
		
		// Check validity time limit
		Date validUntil;
		if (StringUtils.isNotBlank(LICENSE_VALUES.get(ConfigValue.System_License_ExpirationDate.toString()))) {
			try {
				validUntil = new SimpleDateFormat(DateUtilities.DD_MM_YYYY_HH_MM_SS).parse(LICENSE_VALUES.get(ConfigValue.System_License_ExpirationDate.toString()) + " 23:59:59");
			} catch (ParseException e) {
				throw new LicenseError("Invalid validity data: " + e.getMessage(), e);
			}
			if (new Date().after(validUntil)) {
				throw new LicenseError("error.license.outdated", LICENSE_VALUES.get(ConfigValue.System_License_ExpirationDate.toString()), EMM_PROPERTIES_VALUES.get(ConfigValue.Mailaddress_Support.toString()));
			}
		}

		if (companyDao != null) {
			// Check maximum number of companies
			int maximumNumberOfCompanies = NumberUtils.toInt(LICENSE_VALUES.get(ConfigValue.System_License_MaximumNumberOfCompanies.toString()));
			if (maximumNumberOfCompanies >= 0) {
				int numberOfCompanies = companyDao.getNumberOfCompanies();
				if (numberOfCompanies > maximumNumberOfCompanies) {
					throw new LicenseError("Invalid Number of accounts", maximumNumberOfCompanies, numberOfCompanies);
				}
			}
		}

		if (adminDao != null) {
			// Check maximum number of admins
			int maximumNumberOfAdmins = NumberUtils.toInt(LICENSE_VALUES.get(ConfigValue.System_License_MaximumNumberOfAdmins.toString()));
			if (maximumNumberOfAdmins >= 0) {
				int numberOfAdmins = adminDao.getNumberOfAdmins();
				if (numberOfAdmins > maximumNumberOfAdmins) {
					throw new LicenseError("Invalid Number of admins", maximumNumberOfAdmins, numberOfAdmins);
				}
			}
		}

		if (webserviceUserDao != null) {
			// Check maximum number of admins
			int maximumNumberOfWebserviceUsers = NumberUtils.toInt(LICENSE_VALUES.get(ConfigValue.System_License_MaximumNumberOfWebserviceUsers.toString()));
			if (maximumNumberOfWebserviceUsers >= 0) {
				int numberOfWebserviceUsers = webserviceUserDao.getNumberOfWebserviceUsers();
				if (numberOfWebserviceUsers > maximumNumberOfWebserviceUsers) {
					throw new LicenseError("Invalid Number of Webservice Users", maximumNumberOfWebserviceUsers, numberOfWebserviceUsers);
				}
			}
		}

		if (supervisorDao != null) {
			// Check maximum number of supervisors
			int maximumNumberOfSupervisors = NumberUtils.toInt(LICENSE_VALUES.get(ConfigValue.System_License_MaximumNumberOfSupervisors.toString()));
			if (maximumNumberOfSupervisors >= 0) {
				int numberOfSupervisors = supervisorDao.getNumberOfSupervisors();
				if (numberOfSupervisors > maximumNumberOfSupervisors) {
					throw new LicenseError("Invalid Number of supervisors", maximumNumberOfSupervisors, numberOfSupervisors);
				}
			}
		}

		if (companyDao != null) {
			// Check maximum number of customers
			int maximumNumberOfCustomers = NumberUtils.toInt(LICENSE_VALUES.get(ConfigValue.System_License_MaximumNumberOfCustomers.toString()));
			if (maximumNumberOfCustomers >= 0) {
				int numberOfCustomers = companyDao.getMaximumNumberOfCustomers();
				if (numberOfCustomers > maximumNumberOfCustomers) {
					throw new LicenseError("Invalid Number of customers", maximumNumberOfCustomers, numberOfCustomers);
				}
			}
		
			// Check maximum number of profile fields
			int maximumNumberOfProfileFields = NumberUtils.toInt(LICENSE_VALUES.get(ConfigValue.System_License_MaximumNumberOfProfileFields.toString()));
			if (maximumNumberOfProfileFields >= 0) {
				int numberOfProfileFields;
				try {
					numberOfProfileFields = companyDao.getMaximumNumberOfProfileFields();
				} catch (Exception e) {
					throw new LicenseError("Cannot detect number of profileFields: " + e.getMessage(), e);
				}
			 	if (numberOfProfileFields > maximumNumberOfProfileFields) {
			 		throw new LicenseError("Invalid Number of profileFields", maximumNumberOfProfileFields, numberOfProfileFields);
			 	}
			}
		
			// Check allowed premium features
			String allowedPremiumFeaturesData = LICENSE_VALUES.get(ConfigValue.System_License_AllowedPremiumFeatures.toString());
			Set<String> allowedPremiumFeatures = new HashSet<>();
			Set<String> unAllowedPremiumFeatures = new HashSet<>();
			for (String allowedPremiumFeature : allowedPremiumFeaturesData.split(" |;|,|\\t|\\n")) {
				if (StringUtils.isNotBlank(allowedPremiumFeature)) {
					allowedPremiumFeatures.add(allowedPremiumFeature.trim());
				}
			}
			if (!allowedPremiumFeatures.contains("all") && !allowedPremiumFeatures.contains("ALL")) {
				List<String> premiumCategories = Arrays.asList(Permission.ORDERED_PREMIUM_RIGHT_CATEGORIES);
				for (Entry<Permission, String> permissionEntry : Permission.getAllPermissionsAndCategories().entrySet()) {
					String category = permissionEntry.getValue();
					if (premiumCategories.contains(category) && !allowedPremiumFeatures.contains(permissionEntry.getKey().toString())) {
						unAllowedPremiumFeatures.add(permissionEntry.getKey().toString());
					}
				}
				adminDao.deleteFeaturePermissions(unAllowedPremiumFeatures);
				companyDao.setupPremiumFeaturePermissions(allowedPremiumFeatures, unAllowedPremiumFeatures);
			} else {
				// Init the permission system anyway and assign categories to the rights etc.
				Permission.getAllPermissionsAndCategories();
			}
		}
	}
	
	public void writeValue(final ConfigValue configurationValueID, final String value) {
		String[] parts = configurationValueID.toString().split("\\.", 2);
		
		configTableDao.storeEntry(parts[0], parts[1], value);
		
		invalidateCache();
	}

	public void writeValue(final ConfigValue configurationValueID, final int companyID, final String value) {
		writeValue(configurationValueID, companyID, value, null);
	}

	public void writeValue(final ConfigValue configurationValueID, final int companyID, final String value, final String description) {
		companyInfoDao.writeConfigValue(companyID, configurationValueID.toString(), value, description);
		
		invalidateCache();
	}

	public void writeOrDeleteIfDefaultValue(final ConfigValue configurationValueID, final int companyID, final String value){
		String defaultValue = getValue(configurationValueID, 0);
		if (StringUtils.equals(defaultValue, value) && companyID > 0){
			String[] parts = configurationValueID.toString().split("\\.", 2);
			if (parts.length > 1) {
				configTableDao.deleteEntry(parts[0], parts[1] + "." + companyID);
			}
			companyInfoDao.deleteValue(companyID, configurationValueID.toString());
			invalidateCache();
		} else {
			writeValue(configurationValueID, companyID, value);
		}
	}

	public void writeBooleanValue(final ConfigValue configurationValueID, final boolean value) {
		String[] parts = configurationValueID.toString().split("\\.", 2);
		
		configTableDao.storeEntry(parts[0], parts[1], value ? "true" : "false");
		
		invalidateCache();
	}

	public void writeBooleanValue(final ConfigValue configurationValueID, final int companyID, final boolean value) {
		writeBooleanValue(configurationValueID, companyID, value, null);
	}

	public void writeBooleanValue(final ConfigValue configurationValueID, final int companyID, final boolean value, String description) {
		companyInfoDao.writeConfigValue(companyID, configurationValueID.toString(), value ? "true" : "false", description);

		invalidateCache();
	}

	public void writeOrDeleteIfDefaultBooleanValue(final ConfigValue configurationValueID, final int companyID, final boolean value){
		boolean defaultValue = getBooleanValue(configurationValueID, 0);
		if (defaultValue == value && companyID > 0){
			String[] parts = configurationValueID.toString().split("\\.", 2);
			if (parts.length > 1) {
				configTableDao.deleteEntry(parts[0], parts[1] + "." + companyID);
			}
			companyInfoDao.deleteValue(companyID, configurationValueID.toString());
			invalidateCache();
		} else {
			writeBooleanValue(configurationValueID, companyID, value);
		}
	}
	
	public String getValue(ConfigValue configurationValueID) {
		try {
			refreshValues();
			String value = CONFIGURATIONVALUES.get(configurationValueID.toString());
			if (value == null) {
				value = configurationValueID.getDefaultValue();
			}
			return value;
		} catch (LicenseError e) {
			if (ConfigValue.SupportEmergencyUrl.equals(configurationValueID))  {
				try {
					return configTableDao.getAllEntries().get(ConfigValue.SupportEmergencyUrl.toString());
				} catch (SQLException e1) {
					throw new RuntimeException("Cannot load SupportEmergencyUrl from database", e1);
				}
			} else {
				throw e;
			}
		}
	}
	
	public String getValue(ConfigValue configurationValueID, String defaultValue) {
		String returnValue = getValue(configurationValueID);
		if (returnValue == null || returnValue.length() == 0) {
			return defaultValue;
		} else {
			return returnValue;
		}
	}
	
	public String getEncryptedValue(ConfigValue configurationValueID, @VelocityCheck int companyID) throws GeneralSecurityException, UnsupportedEncodingException {
		String encryptedDataBase64 = getValue(configurationValueID, companyID);
		
		if (StringUtils.isNotEmpty(encryptedDataBase64)) {
			return dataEncryptor.decrypt(encryptedDataBase64);
		} else {
			return null;
		}
	}
	
	/**
	 * Does a chained search for specified configuration. Search stops at first match:
	 * 
	 * <ol>
	 *   <li>Search configuration value for given company ID.</li>
	 *   <li>Search configuration value for company ID 0.</li>
	 *   <li>Search configuration value without company ID</li>
	 * </ol>
	 * 
	 * If there is no matching configuration, {@code null} is returned.
	 * 
	 * @param configurationValueID configuration value ID
	 * @param companyID company ID

	 * @return configuration value as String or {@code null}
	 */
	public String getValue(ConfigValue configurationValueID, @VelocityCheck int companyID) {
		refreshValues();
		
		String value = CONFIGURATIONVALUES.get(configurationValueID.toString() + "." + companyID);
		
		if (value == null) {
			value = CONFIGURATIONVALUES.get(configurationValueID.toString() + "." + 0);
		}

		if (value == null) {
			value = getValue(configurationValueID);
		}
		
		return value;
	}
	
	/**
	 * Same as {@link #getValue(ConfigValue, int)} with additional default value.
	 * 
	 * If no matching configuration is found, the specified default value is returned.
	 * 
	 * @param configurationValueID configuration value ID
	 * @param companyID company ID
	 * @param defaultValue default value
	 * 
	 * @return configuration value or specified default value
	 */
	public String getValue(ConfigValue configurationValueID, @VelocityCheck int companyID, String defaultValue) {
		String value = getValue(configurationValueID, companyID);
		
		if (value == null) {
			return defaultValue;
		} else {
			return value;
		}
	}
	
	public boolean getBooleanValue(ConfigValue configurationValueID) {
		String value = getValue(configurationValueID);
		
		return AgnUtils.interpretAsBoolean(value);
	}
	
	public boolean getBooleanValue(ConfigValue configurationValueID, @VelocityCheck int companyID) {
		String value = getValue(configurationValueID, companyID);
		
		return AgnUtils.interpretAsBoolean(value);
	}
	
	/**
	 * Returns an integer value using logic with fallback and default value.
	 * See {@link #getValue(ConfigValue, int, String)} for details on fallback.
	 * 
	 * @param configurationValueID ID of configuration value
	 * @param companyID company ID
	 * @param defaultValue default value
	 * 
	 * @return integer configuration value of default value
	 */
	public int getIntegerValue(ConfigValue configurationValueID, @VelocityCheck int companyID, int defaultValue) {
		String value = getValue(configurationValueID, companyID, Integer.toString(defaultValue));
		
		return Integer.parseInt(value);
	}
	
	public int getIntegerValue(ConfigValue configurationValueID, @VelocityCheck int companyID) {
		String value = getValue(configurationValueID, companyID);
		
		return Integer.parseInt(value);
	}

	public float getFloatValue(ConfigValue configurationValueID, @VelocityCheck int companyID){
		String value = getValue(configurationValueID, companyID);

		return Float.parseFloat(value);
	}
	
	public int getIntegerValue(ConfigValue configurationValueID) {
		String value = getValue(configurationValueID);
		if (StringUtils.isNotEmpty(value)) {
			return Integer.parseInt(value);
		} else {
			return 0;
		}
	}

	public float getFloatValue(ConfigValue configurationValueID) {
		String value = getValue(configurationValueID);
		if (StringUtils.isNotEmpty(value)) {
			return Float.parseFloat(value);
		} else {
			return 0;
		}
	}
	
	public List<String> getListValue(ConfigValue configurationValueID) {
		String value = getValue(configurationValueID);
		if (StringUtils.isNotEmpty(value)) {
			return Arrays.asList(value.split(";"));
		} else {
			return Collections.emptyList();
		}
	}
	
	public String getDescription(ConfigValue configurationValueID, @VelocityCheck int companyID) {
		String description = companyInfoDao.getDescription(configurationValueID.toString(), companyID);
		if (description == null) {
			description = companyInfoDao.getDescription(configurationValueID.toString(), 0);
		}
		return description;
	}
	
	/**
	 * Checks, if runtime checks for Velocity are enabled. If no settings for
	 * given company ID are found, checks for company ID 0. If no settings for
	 * company ID 0 are found, runtime checks are enabled globally.
	 * 
	 * @param companyId company ID to check
	 * 
	 * @return true, if runtime checks are enabled
	 */
	public boolean isVelocityRuntimeCheckEnabled( int companyId) {
		String value = getValue( ConfigValue.VelocityRuntimeCheck, companyId);
		
		if (value != null) {
			return AgnUtils.interpretAsBoolean( value);
		} else {
			if (companyId != 0) {
				value = getValue(ConfigValue.VelocityRuntimeCheck);
				
				if (value != null) {
					return AgnUtils.interpretAsBoolean( value);
				} else {
					return true;
				}
			} else {
				return true;
			}
		}
	}

	/**
	 * Checks, if invalid Velocity scripts are to be aborted. If no settings for
	 * given company ID are found, checks for company ID 0. If no settings for
	 * company ID 0 are found, abortion of scripts is globally enabled.
	 * 
	 * @param companyId company ID to check
	 * 
	 * @return true if scripts are to be aborted
	 */
	public boolean isVelocityScriptAbortEnabled(int companyId) {
		String value = getValue(ConfigValue.VelocityScriptAbort, companyId);
		
		if (value != null) {
			return AgnUtils.interpretAsBoolean(value);
		} else {
			if (companyId != 0) {
				value = getValue( ConfigValue.VelocityScriptAbort);
				
				if (value != null) {
					return AgnUtils.interpretAsBoolean(value);
				} else {
					return true;
				}
			} else {
				return true;
			}
		}
	}
	
	/**
	 * Checks, if company is allowed to use webservice &quot;&SendServiceMailing&quot;.
	 * 
	 * @param companyID company ID
	 * 
	 * @return <code>true</code> if company is allowed to use webservice
	 */
	public boolean isWebserviceSendServiceMailingEnabled(int companyID) {
		return getBooleanValue(ConfigValue.WebserviceEnableSendServiceMailing, companyID);
	}
	
	public boolean isActionbasedMailloopAutoresponderInUiEnabled(final int companyID) {	// TODO: Remove after transition phase (EMM-3645)
		return getBooleanValue(ConfigValue.MailloopActionbasedAutoreponderUI, companyID);
	}

	/**
	 * Returns the size limit for bulk webservices.
	 * 
	 * @param companyID company ID
	 * 
	 * @return size limit
	 */
	public int getWebserviceBulkSizeLimit(final int companyID) {
		return getIntegerValue(ConfigValue.WebserviceBulkSizeLimit, companyID, 1000);
	}
	
	/**
	 * Returns <code>true</code>, if history on profile fields is enabled.
	 * 
	 * @param companyID company ID
	 * 
	 * @return <code>true</code> if history on profile fields is enabled
	 */
	public boolean isRecipientProfileHistoryEnabled(final int companyID) {
		return getBooleanValue(ConfigValue.RecipientProfileFieldHistory, companyID);
	}

	/**
	 * Returns the maximum allowed number of user-selected profile fields in history.
	 * 
	 * @param companyID company ID
	 * 
	 * @return maximum allowed number of user-selected profile fields in history
	 */
	public int getMaximumNumberOfUserDefinedHistoryProfileFields(final int companyID) {
		return getIntegerValue(ConfigValue.MaximumNumberOfUserSelectedProfileFieldsInHistory, companyID, 5);
	}

	public boolean useUnsharpRecipientQuery(int companyID) {
		return getBooleanValue(ConfigValue.UseUnsharpRecipientQuery, companyID);
	}

	/**
	 * Returns <code>true</code> if push notifications are enabled for given company ID.
	 * 
	 * @param companyID company ID
	 * 
	 * @return <code>true</code> if push notifications are enabled
	 */
	public final boolean isPushNotificationEnabled(final int companyID) {
		return getBooleanValue(ConfigValue.PushNotificationsEnabled, companyID);
	}

	/**
	 * Returns the provider credentials for push server implementation.
	 * The content is JSON encoded.
	 * 
	 * @param companyID company ID
	 * 
	 * @return JSON-encoded provider credentials
	 */
	public final String pushNotificationProviderCredentials(final int companyID) {
		// "{}" represents empty map in JSON
		return getValue(ConfigValue.PushNotificationProviderCredentials, companyID, "{}");
	}

	public String getPushNotificationFileSinkBaseDirectory(int companyID) {
		return getValue(ConfigValue.PushNotificationFileSinkBaseDirectory, companyID, "/tmp");
	}
	
	public final String getPushNotificationResultBaseDirectory() {
		return getValue(ConfigValue.PushNotificationResultBaseDirectory, "/tmp");
	}
	
	public final int getLicenseID() {
		return getIntegerValue(ConfigValue.System_Licence);
	}

	public final String getPushNotificationSftpHost() {
		return getValue(ConfigValue.PushNotificationSftpHost);
	}

	public String getPushNotificationSftpUser() {
		return getValue(ConfigValue.PushNotificationSftpUser);
	}

	public String getPushNotificationSftpBasePath() {
		return getValue(ConfigValue.PushNotificationSftpBasePath);
	}

	public String getPushNotificationSftpKeyFileName() {
		return getValue(ConfigValue.PushNotificationSftpSshKeyFile);
	}

	public String getPushNotificationEncryptedSftpPassphrase() {
		return getValue(ConfigValue.PushNotificationSftpEncryptedSshKeyPassphrase);
	}
	
	public String getPushNotificationClickTrackingUrl(final int companyID) {
		return getValue(ConfigValue.PushNotificationClickTrackingUrl, companyID);
	}
	
	public String getPushNotificationOpenTrackingUrl(final int companyID) {
		return getValue(ConfigValue.PushNotificationOpenTrackingUrl, companyID);
	}
	
	public final int getPushNotificationMaxRedirectTokenGenerationAttempts(final int companyID) {
		return getIntegerValue(ConfigValue.PushNotificationMaxRedirectTokenGenerationAttempts, companyID);
	}
	
	public final int getPushNotificationMaxRedirectTokenAge() {
		return getIntegerValue(ConfigValue.PushNotificationMaxRedirectTokenAge);
	}
	
	public final int getPushNotificationMaxTrackingIdGenerationAttempts(final int companyID) {
		return getIntegerValue(ConfigValue.PushNotificationMaxTrackingIdGenerationAttempts, companyID);
	}
	
	public static Date getBuildTime() {
		try {
	        URL resource = ConfigService.class.getResource(ConfigService.class.getSimpleName() + ".class");
	        if (resource == null) {
	            throw new IllegalStateException("Failed to find class file for class: " + ConfigService.class.getName());
	        } else if (resource.getProtocol().equals("file")) {
	        	return new Date(new File(resource.toURI()).lastModified());
	        } else if (resource.getProtocol().equals("jar")) {
	            String path = resource.getPath();
	            return new Date(new File(path.substring(5, path.indexOf("!"))).lastModified());
	        } else {
	            throw new IllegalArgumentException("Unhandled url protocol: " + resource.getProtocol() + " for class: " + ConfigService.class.getName() + " resource: " + resource.toString());
	        }
	    } catch (Exception e) {
	    	logger.error("Error in getDeploymentTime: " + e.getMessage(), e);
	        return null;
	    }
	}

	public Date getStartupTime() {
		return STARTUP_TIME == null ? null : (Date) STARTUP_TIME.clone();
	}
	
	public Date getConfigurationExpirationTime() {
		return EXPIRATIONTIME == null ? null : EXPIRATIONTIME.getTime();
	}

	public final String getHostAuthenticationHostIdCookieName() {
		return getValue(ConfigValue.HostAuthenticationHostIdCookieName);
	}
	
	public final int getMaxPendingHostAuthenticationsAgeMinutes() {
		return getIntegerValue(ConfigValue.PendingHostAuthenticationMaxAgeMinutes);
	}

	public final String getLitmusStatusURL(final int companyID) {
		return getValue(ConfigValue.Predelivery_LitmusStatusUrl);
	}

	public final String getWhatsbroadcastAttachmentHost(final int companyID) {
		return getValue(ConfigValue.WhatsBroadcastAttachmentHost, companyID);
	}

	public final String getFullviewFormName(final int companyID) {
		return getValue(ConfigValue.FullviewFormName, companyID);
	}
	
	public final Map<String, String> getHostSystemProperties() {
		Map<String, String> hostProperties = new HashMap<>();
		Map<String, String> systemProperties = new Systemconfig().get();
		
		if(MapUtils.isNotEmpty(systemProperties)) {
			Map<String, String> hostNames = systemProperties.entrySet().stream()
					.filter(pair -> StringUtils.startsWith(pair.getKey(), "hostname-"))
					.map(ConfigService::createHostProperty)
					.collect(Collectors.toMap(Tuple::getFirst, Tuple::getSecond));
			hostProperties.putAll(hostNames);
		} else {
			hostProperties.put("host.nosystemcfg", "true");
		}
		
		return hostProperties;
	}
	
	private static Tuple<String, String> createHostProperty(Entry<String, String> propertyEntry) {
		String hostFunction = StringUtils.removeStart(propertyEntry.getKey(), "hostname-");
		String hostName = propertyEntry.getValue();
		String propertyName = String.format("host.%s.%s.ping", hostFunction, hostName);
		String reachable = "ERROR";
		try {
			InetAddress address = InetAddress.getByName(hostName);
			reachable = address.isReachable(1000) ? "OK" : "ERROR";
		} catch (IOException e) {
			logger.error("Cannot reach to host " + hostName, e);
		}
		return new Tuple<>(propertyName, reachable);
	}
}
