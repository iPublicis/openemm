/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package org.agnitas.beans.impl;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;

import org.agnitas.beans.BindingEntry;
import org.agnitas.beans.BindingEntry.UserType;
import org.agnitas.beans.ProfileField;
import org.agnitas.beans.Recipient;
import org.agnitas.beans.factory.BindingEntryFactory;
import org.agnitas.beans.factory.RecipientFactory;
import org.agnitas.dao.UserStatus;
import org.agnitas.emm.core.blacklist.service.BlacklistService;
import org.agnitas.emm.core.velocity.VelocityCheck;
import org.agnitas.service.ColumnInfoService;
import org.agnitas.util.AgnUtils;
import org.agnitas.util.DateUtilities;
import org.agnitas.util.HttpUtils;
import org.apache.commons.collections4.map.CaseInsensitiveMap;
import org.apache.commons.lang.StringUtils;

import com.agnitas.dao.ComBindingEntryDao;
import com.agnitas.dao.ComRecipientDao;
import com.agnitas.dao.impl.ComCompanyDaoImpl;
import com.agnitas.emm.core.mediatypes.common.MediaTypes;

/**
 * Manually executed test
 * Needs a running RDIR on localhost and a special form with action in DB
 */
public class RecipientImpl implements Recipient {
	protected ColumnInfoService columnInfoService;
	protected ComRecipientDao recipientDao;
	protected BlacklistService blacklistService;
	protected BindingEntryFactory bindingEntryFactory;
	protected RecipientFactory recipientFactory;
	protected ComBindingEntryDao bindingEntryDao;

	protected int companyID;
	protected int customerID;
	protected Map<Integer, Map<Integer, BindingEntry>> listBindings;
	protected Map<String, String> custDBStructure;
	protected CaseInsensitiveMap<String, Object> custParameters = new CaseInsensitiveMap<>();
	protected boolean changeFlag = false;
	
	private DateFormat dateFormat = new SimpleDateFormat(DateUtilities.ISO_8601_DATETIME_FORMAT);
	
	/**
	 * Instantiates new (unconfigured) {@link RecipientImpl} object.
	 * 
	 * Do not use this anymore! Replace any instantiation by usage of {@link RecipientFactory}.
	 * 
	 * @Deprecated
	 * 
	 * @see RecipientFactory#newRecipient(int)
	 */
	public RecipientImpl() {
		// Nothing to do here
	}
	
	// ----------------------------------------------------------------------------------------------------------------
	// Dependency Injection

	public void setRecipientDao(ComRecipientDao recipientDao) {
		this.recipientDao = recipientDao;
	}
	
	public void setBindingEntryDao(ComBindingEntryDao bindingEntryDao) {
		this.bindingEntryDao = bindingEntryDao;
	}

	public void setColumnInfoService(ColumnInfoService columnInfoService) {
		this.columnInfoService = columnInfoService;
	}

	public void setBindingEntryFactory(BindingEntryFactory bindingEntryFactory) {
		this.bindingEntryFactory = bindingEntryFactory;
	}

	public void setRecipientFactory(RecipientFactory recipientFactory) {
		this.recipientFactory = recipientFactory;
	}
	
	public void setBlacklistService(BlacklistService blacklistService) {
		this.blacklistService = blacklistService;
	}
	
	// ----------------------------------------------------------------------------------------------------------------
	// Business Logic
	
	@Override
	public void setDateFormatForProfileFieldConversion(final DateFormat dateFormat) {
		if(dateFormat != null) {
			this.dateFormat = dateFormat;
		}
	}
	
	@Override
	public boolean blacklistCheck() {
		String email = (String) getCustParameters().get("email");
		if (email != null) {
			email = email.toLowerCase().trim();
		}
		return blacklistService.blacklistCheck(email, getCompanyID());
	}

	@Override
	public boolean updateInDB() {
		Object gender = getCustParameters().get("gender");
		Object firstname = getCustParameters().get("firstname");
		Object lastname = getCustParameters().get("lastname");
		
		if (gender == null || (gender instanceof String && StringUtils.isBlank((String) gender))) {
			throw new ViciousFormDataException("Cannot create customer, because customer data is missing or invalid: gender is empty");
		} else if (firstname != null && firstname instanceof String && (((String) firstname).toLowerCase().contains("http:") || ((String) firstname).toLowerCase().contains("https:"))) {
			throw new ViciousFormDataException("Cannot create customer, because customer data field \"lastname\" contains http link data");
		} else if (lastname != null && lastname instanceof String && (((String) lastname).toLowerCase().contains("http:") || ((String) lastname).toLowerCase().contains("https:"))) {
			throw new ViciousFormDataException("Cannot create customer, because customer data field \"lastname\" contains http link data");
		}
		
		return recipientDao.updateInDB(this);
	}

	@Override
	public int findByColumn(String col, String value) {
		return recipientDao.findByColumn(companyID, col, value);
	}

	@Override
	public int findByKeyColumn(String col, String value) {
		setCustomerID(recipientDao.findByKeyColumn(this, col, value));
		return getCustomerID();
	}

	@Override
	public void deleteCustomerDataFromDb() {
		recipientDao.deleteCustomerDataFromDb(companyID, customerID);
	}

	@Override
	public int findByUserPassword(String userCol, String userValue, String passCol, String passValue) {
		setCustomerID(recipientDao.findByUserPassword(companyID, userCol, userValue, passCol, passValue));
		return getCustomerID();
	}

	@Override
	public Map<String, Object> getCustomerDataFromDb() {
		custParameters = recipientDao.getCustomerDataFromDb(companyID, customerID, this.dateFormat);
		return custParameters;
	}

	@Override
	public Map<Integer, Map<Integer, BindingEntry>> loadAllListBindings() {
		listBindings = recipientDao.loadAllListBindings(companyID, customerID);
		return listBindings;
	}

	@Override
	public int insertNewCust() {
		return recipientDao.insertNewCust(this);
	}

	@Override
	public int getCustomerID() {
		return customerID;
	}

	@Override
	public void setCustomerID(int customerID) {
		this.customerID = customerID;
	}

	@Override
	public int getCompanyID() {
		return companyID;
	}

	@Override
	public void setCompanyID(@VelocityCheck int companyID) {
		this.companyID = companyID;
	}

	@Override
	public Map<Integer, Map<Integer, BindingEntry>> getListBindings() {
		return listBindings;
	}

	@Override
	public void setListBindings(Map<Integer, Map<Integer, BindingEntry>> listBindings) {
		this.listBindings = listBindings;
	}
	
	@Override
	public Map<String, String> getCustDBStructure() {
		if (custDBStructure == null && companyID > 0) {
			loadCustDBStructure();
		}
		return custDBStructure;
	}

	@Override
	public void setCustDBStructure(Map<String, String> custDBStructure) {
		this.custDBStructure = custDBStructure;
	}

	@Override
	public String getCustParametersNotNull(String key) {
		Object value = custParameters.get(key);
		return value == null ? "" : value.toString();
	}

	@Override
	public String getCustParameters(String key) {
		return getCustParametersNotNull(key);
	}
	
	@Override
	public boolean hasCustParameter(String key) {
		return custParameters.get(key) != null;
	}

	@Override
	public boolean isChangeFlag() {
		return changeFlag;
	}

	@Override
	public void setChangeFlag(boolean changeFlag) {
		this.changeFlag = changeFlag;
	}

	@Override
	public Map<String, Object> getCustParameters() {
		return custParameters;
	}

	@Override
	public void resetCustParameters() {
		custParameters.clear();
	}

	@Override
	public Map<Integer, Map<Integer, BindingEntry>> getAllMailingLists() {
		return recipientDao.getAllMailingLists(customerID, companyID);
	}

	@Override
	public String getEmail() {
		return (String) custParameters.get("email");
	}

	@Override
	public String getFirstname() {
		return (String) custParameters.get("firstname");
	}

	@Override
	public int getGender() {
		return ((Number) custParameters.get("gender")).intValue();
	}

	@Override
	public String getLastname() {
		return (String) custParameters.get("lastname");
	}

	
	
	/**
	 * Load structure of Customer-Table for the given Company-ID in member
	 * variable "companyID". Load profile data into map. Has to be done before
	 * working with customer-data in class instance
	 * 
	 * @return true on success
	 */
	@Override
	public boolean loadCustDBStructure() {
		custDBStructure = new CaseInsensitiveMap<>();

		try {
			for (ProfileField fieldDescription : columnInfoService.getColumnInfos(companyID)) {
				custDBStructure.put(fieldDescription.getColumn(), fieldDescription.getDataType());
			}
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * Indexed setter for property custParameters.
	 * 
	 * @param aKey
	 *            identifies field in customer-record, must be the same like in
	 *            Database
	 * @param custParameter
	 *            New value of the property at <CODE>aKey</CODE>.
	 */
	@Override
	public void setCustParameters(String aKey, String custParameter) {
		String key = aKey;
		String aValue = null;

		if (key.toUpperCase().endsWith(ComRecipientDao.SUPPLEMENTAL_DATECOLUMN_SUFFIX_DAY)) {
			key = key.substring(0, key.length() - ComRecipientDao.SUPPLEMENTAL_DATECOLUMN_SUFFIX_DAY.length());
		}
		if (key.toUpperCase().endsWith(ComRecipientDao.SUPPLEMENTAL_DATECOLUMN_SUFFIX_MONTH)) {
			key = key.substring(0, key.length() - ComRecipientDao.SUPPLEMENTAL_DATECOLUMN_SUFFIX_MONTH.length());
		}
		if (key.toUpperCase().endsWith(ComRecipientDao.SUPPLEMENTAL_DATECOLUMN_SUFFIX_YEAR)) {
			key = key.substring(0, key.length() - ComRecipientDao.SUPPLEMENTAL_DATECOLUMN_SUFFIX_YEAR.length());
		}
		if (key.toUpperCase().endsWith(ComRecipientDao.SUPPLEMENTAL_DATECOLUMN_SUFFIX_HOUR)) {
			key = key.substring(0, key.length() - ComRecipientDao.SUPPLEMENTAL_DATECOLUMN_SUFFIX_HOUR.length());
		}
		if (key.toUpperCase().endsWith(ComRecipientDao.SUPPLEMENTAL_DATECOLUMN_SUFFIX_MINUTE)) {
			key = key.substring(0, key.length() - ComRecipientDao.SUPPLEMENTAL_DATECOLUMN_SUFFIX_MINUTE.length());
		}
		if (key.toUpperCase().endsWith(ComRecipientDao.SUPPLEMENTAL_DATECOLUMN_SUFFIX_SECOND)) {
			key = key.substring(0, key.length() - ComRecipientDao.SUPPLEMENTAL_DATECOLUMN_SUFFIX_SECOND.length());
		}

		if (getCustDBStructure().containsKey(key)) {
			aValue = null;
			if (custParameters.get(aKey) != null) {
				aValue = (String) custParameters.get(aKey);
			}
			if (!StringUtils.equals(custParameter, aValue)) {
				changeFlag = true;
				custParameters.put(aKey, custParameter);
			}
		}
	}

	/**
	 * Setter for property custParameters.
	 * 
	 * @param custParameters
	 *            New value of property custParameters.
	 */
	@Override
	public void setCustParameters(Map<String, Object> custParameters) {
		if (custParameters instanceof CaseInsensitiveMap) {
			this.custParameters = (CaseInsensitiveMap<String, Object>) custParameters;
		} else {
			this.custParameters = new CaseInsensitiveMap<>(custParameters);
		}
		changeFlag = true;
	}

	/**
	 * Check security of a request parameter. Checks the given string for
	 * certain patterns that could be used for exploits.
	 */
	private boolean isSecure(String value) {
		return !value.contains("<");
	}

	/**
	 * Copy a date from reqest to database values.
	 * 
	 * @param req
	 *            a Map of request parameters (name/value pairs).
	 * @param name
	 *            the name of the field to copy.
	 * @param suffix
	 *            a suffix for the parameters in the map.
	 * @return true when the copying was successfull.
	 */
	private boolean copyDate(Map<String, Object> req, String name, String suffix) {
		String[] field = { ComRecipientDao.SUPPLEMENTAL_DATECOLUMN_SUFFIX_DAY, ComRecipientDao.SUPPLEMENTAL_DATECOLUMN_SUFFIX_MONTH, ComRecipientDao.SUPPLEMENTAL_DATECOLUMN_SUFFIX_YEAR, ComRecipientDao.SUPPLEMENTAL_DATECOLUMN_SUFFIX_HOUR, ComRecipientDao.SUPPLEMENTAL_DATECOLUMN_SUFFIX_MINUTE, ComRecipientDao.SUPPLEMENTAL_DATECOLUMN_SUFFIX_SECOND };
		String s = null;

		name = name.toUpperCase();
		for (int c = 0; c < field.length; c++) {
			if (req.get(name + field[c] + suffix) != null) {
				String fieldname = name + field[c] + suffix;
				Object o = req.get(fieldname);
				s = o.toString();
				setCustParameters(fieldname, s);
			}
		}
		
		String dateValueFieldname = name + suffix;
		if (req.get(dateValueFieldname) != null) {
			// Date field delivered as single value without explicit format
			setCustParameters(dateValueFieldname, (String) req.get(dateValueFieldname));
		}
		
		return true;
	}

	/**
	 * Check if the given name is allowed for requests. This is used to ensure
	 * that system columns are not changed by form requests.
	 * 
	 * @param name
	 *            the name to check for allowance.
	 * @return true when field may be writen.
	 */
	private boolean isAllowedName(String name) {
		name = name.toLowerCase();
		if (name.startsWith("agn")) {
			return false;
		}
		if (name.equals("customer_id") || name.equals("change_date")) {
			return false;
		}
		if (name.equals("timestamp") || name.equals("creation_date")) {
			return false;
		}
		return true;
	}

	/**
	 * Updates customer data by analyzing given HTTP-Request-Parameters
	 * 
	 * @return true on success
	 * @param suffix
	 *            Suffix appended to Database-Column-Names when searching for
	 *            corresponding request parameters
	 * @param requestParameters
	 *            Map containing all HTTP-Request-Parameters as key-value-pair.
	 */
	@Override
	public boolean importRequestParameters(Map<String, Object> requestParameters, String suffix) {
		CaseInsensitiveMap<String, Object> caseInsensitiveParameters = new CaseInsensitiveMap<>(requestParameters);
		
		if (suffix == null) {
			suffix = "";
		}
		
		for (Entry<String, String> entry : getCustDBStructure().entrySet()) {
			String colType = entry.getValue();
			String name = entry.getKey().toUpperCase();

			if (!isAllowedName(entry.getKey())) {
				continue;
			}
			if (colType.equalsIgnoreCase("DATE")) {
				copyDate(caseInsensitiveParameters, entry.getKey(), suffix);
			} else if (caseInsensitiveParameters.get(name + suffix) != null) {
				String aValue = (String) caseInsensitiveParameters.get(name + suffix);
				if (name.equalsIgnoreCase("EMAIL")) {
					if (aValue.length() == 0) {
						aValue = " ";
					}
					aValue = aValue.toLowerCase();
					aValue = aValue.trim();
				} else if (name.length() > 4) {
					if (name.substring(0, 4).equals("SEC_")
							|| name.equals("FIRSTNAME")
							|| name.equals("LASTNAME")) {
						if (!isSecure(aValue)) {
							return false;
						}
					}
				}
				if (name.equalsIgnoreCase("DATASOURCE_ID")) {
					if (!hasCustParameter(entry.getKey())) {
						setCustParameters(entry.getKey(), aValue);
					}
				} else {
					setCustParameters(entry.getKey(), aValue);
				}
			}
		}
		return true;
	}

	/**
	 * Updates internal Datastructure for Mailinglist-Bindings of this customer
	 * by analyzing HTTP-Request-Parameters
	 * 
	 * @return true on success
	 * @param tafWriteBack
	 *            if true, eventually existent TAF-Information will be written
	 *            back to source-customer
	 * @param params
	 *            Map containing all HTTP-Request-Parameters as key-value-pair.
	 * @param doubleOptIn
	 *            true means use Double-Opt-In
	 * @throws Exception
	 */
	
	
	/**
	 * function of tafWriteBack was removed with TellaFriend feature (EMM-5308)
	 * 
	 * @deprecated updateBindingsFromRequest(Map<String, Object> params, boolean doubleOptIn, String remoteAddr) instead.
	 */
	@Deprecated
	@Override
	public void updateBindingsFromRequest(Map<String, Object> params, boolean doubleOptIn, boolean tafWriteBack, String remoteAddr, String referrer) throws Exception {
		updateBindingsFromRequest(params, doubleOptIn, remoteAddr, referrer);
	}
	
	@Override
	public void updateBindingsFromRequest(Map<String, Object> params, boolean doubleOptIn, String remoteAddr, String referrer) throws Exception {
		@SuppressWarnings("unchecked")
		Map<String, Object> requestParameters = (Map<String, Object>) params.get("requestParameters");
		int mailingID;

		try {
			Integer tmpNum = (Integer) params.get("mailingID");
			mailingID = tmpNum.intValue();
		} catch (Exception e) {
			mailingID = 0;
		}
		
		// Requests without any "agnSUBSCRIBE"-parameter are still valid
		// Those are used for updating the users data only without any new subscription
		
		for (Entry<String, Object> entry : requestParameters.entrySet()) {
			if (entry.getKey().startsWith("agnSUBSCRIBE")) {
				String postfix = "";
				
				int mediatype;
				int subscribeStatus;
				BindingEntry aEntry = null;
				if (entry.getKey().length() > "agnSUBSCRIBE".length()) {
					postfix = entry.getKey().substring("agnSUBSCRIBE".length());
				}
				
				String agnSubscribeString = (String) entry.getValue();
				if (StringUtils.isBlank(agnSubscribeString)) {
					throw new Exception("Mandatory subscribeStatus (form-param: agnSUBSCRIBE) is missing: " + getRequestParameterString(params));
				}
				
				try {
					subscribeStatus = Integer.parseInt(agnSubscribeString);
				} catch (Exception e) {
					throw new Exception("Mandatory subscribeStatus (form-param: agnSUBSCRIBE) is missing or invalid: " + getRequestParameterString(params));
				}
				if (subscribeStatus != 0 && subscribeStatus != 1) {
					throw new Exception("Mandatory subscribeStatus (form-param: agnSUBSCRIBE) is invalid: " + getRequestParameterString(params));
				}

				String agnMailinglistString = (String) requestParameters.get("agnMAILINGLIST" + postfix);
				if (StringUtils.isBlank(agnMailinglistString)) {
					throw new Exception("Mandatory mailinglistID (form-param: agnMAILINGLIST) is missing: " + getRequestParameterString(params));
				}
				
				int mailinglistID;
				try {
					mailinglistID = Integer.parseInt(agnMailinglistString);
				} catch (NumberFormatException e) {
					throw new ViciousFormDataException("Mandatory mailinglistID (form-param: agnMAILINGLIST) is invalid: " + agnMailinglistString);
				} catch (Exception e) {
					throw new Exception("Mandatory mailinglistID (form-param: agnMAILINGLIST) is missing or invalid: " + getRequestParameterString(params));
				}
				if (mailinglistID <= 0) {
					throw new Exception("Mandatory mailinglistID (form-param: agnMAILINGLIST) is invalid: " + getRequestParameterString(params));
				}

				try {
					mediatype = Integer.parseInt((String) requestParameters.get("agnMEDIATYPE" + postfix));
				} catch (Exception e) {
					mediatype = MediaTypes.EMAIL.getMediaCode();
				}

				doUpdateBindings(doubleOptIn, mediatype, mailinglistID, mailingID, aEntry, subscribeStatus, remoteAddr, referrer);
			}
		}
	}
	
	private final void doUpdateBindings(final boolean doubleOptIn, final int mediatype, final int mailinglistID, final int mailingID, BindingEntry aEntry, final int subscribeStatus, final String remoteAddr, final String referrer) throws Exception {
		// find BindingEntry or create new one
		Map<Integer, BindingEntry> mList = listBindings.get(mailinglistID);
		if (mList != null) {
			aEntry = mList.get(mediatype);
		}

		if (aEntry != null) {
			boolean changeit = false;
			// put changes in db
			switch (UserStatus.getUserStatusByID(aEntry.getUserStatus())) {
				case AdminOut:
				case Bounce:
				case UserOut:
					if (subscribeStatus == 1) {
						changeit = true;
					}
					break;
				case WaitForConfirm:
				case Active:
					if (subscribeStatus == 0) {
						changeit = true;
					}
					break;
				case Blacklisted:
					break;
				case Suspend:
					break;
				default:
					break;
			}
			if (changeit) {
				switch (subscribeStatus) {
				case 0:
					aEntry.setUserStatus(UserStatus.UserOut.getStatusCode());
					if (mailingID != 0) {
						aEntry.setUserRemark("Opt-Out-Mailing: " + mailingID);
						aEntry.setExitMailingID(mailingID);
					} else {
						aEntry.setUserRemark("User-Opt-Out: " + remoteAddr);
						aEntry.setExitMailingID(0);
					}
					break;

				case 1:
					if (!doubleOptIn) {
						aEntry.setUserStatus(UserStatus.Active.getStatusCode());
						if (remoteAddr == null) {
							aEntry.setUserRemark("CSV Import");
						} else {
							aEntry.setUserRemark("Opt-In-IP: " + remoteAddr);
							aEntry.setReferrer(referrer);
						}
					} else {
						aEntry.setUserStatus(UserStatus.WaitForConfirm.getStatusCode());
						if (remoteAddr == null) {
							aEntry.setUserRemark("CSV Import");
						} else {
							aEntry.setUserRemark("Opt-In-IP: " + remoteAddr);
							aEntry.setReferrer(referrer);
						}
					}
					break;
				}
				bindingEntryDao.updateStatus(aEntry, companyID);
			}
		} else {
			if (subscribeStatus == 1) {
				aEntry = bindingEntryFactory.newBindingEntry();
				aEntry.setCustomerID(customerID);
				aEntry.setMediaType(mediatype);
				aEntry.setMailinglistID(mailinglistID);
				aEntry.setUserType(UserType.World.getTypeCode());

				if (!doubleOptIn) {
					aEntry.setUserStatus(UserStatus.Active.getStatusCode());
					if (remoteAddr == null) {
						aEntry.setUserRemark("CSV Import");
					} else {
						aEntry.setUserRemark("Opt-In-IP: " + remoteAddr);
						aEntry.setReferrer(referrer);
					}
				} else {
					aEntry.setUserStatus(UserStatus.WaitForConfirm.getStatusCode());
					if (remoteAddr == null) {
						aEntry.setUserRemark("CSV Import");
					} else {
						aEntry.setUserRemark("Opt-In-IP: " + remoteAddr);
						aEntry.setReferrer(referrer);
					}
				}

				aEntry.insertNewBindingInDB(companyID);
				if (mList == null) {
					mList = new HashMap<>();
					listBindings.put(mailinglistID, mList);
				}
				mList.put(mediatype, aEntry);
			}
		}
	}

	private String getRequestParameterString(Map<String, Object> params) {
		String requestData;
		HttpServletRequest request = (HttpServletRequest) params.get("_request");
		if (request != null) {
			requestData = "\n" + request.getRequestURL().toString() + " \nParams: \n" + AgnUtils.mapToString(request.getParameterMap());
			requestData += "\nIP: " + request.getRemoteAddr();
		} else {
			requestData = "";
		}
		return requestData;
	}

	/**
	 * function of tafWriteBack was removed with TellaFriend feature (EMM-5308)
	 * 
	 * @deprecated updateBindingsFromRequest(Map<String, Object> params, boolean doubleOptIn) instead.
	 */
	@Deprecated
	@Override
	public void updateBindingsFromRequest(Map<String, Object> params, boolean doubleOptIn, boolean tafWriteBack) throws Exception {
		updateBindingsFromRequest(params, doubleOptIn);
	}
	
	@Override
	public void updateBindingsFromRequest(Map<String, Object> params, boolean doubleOptIn) throws Exception {
		if (params.containsKey("_request") && params.get("_request") != null) {
			// If there is a request within the parameters, use its IP-address for logging etc.
			HttpServletRequest request = (HttpServletRequest) params.get("_request");
			String remoteAddr = request.getRemoteAddr();
			updateBindingsFromRequest(params, doubleOptIn, remoteAddr, HttpUtils.getReferrer(request));
		} else {
			updateBindingsFromRequest(params, doubleOptIn, null, null);
		}
	}

	/**
	 * Iterates through already loaded Mailinglist-Informations and checks if
	 * subscriber is active on at least one mailinglist
	 * 
	 * @return true if subscriber is active on a mailinglist
	 */
	@Override
	public boolean isActiveSubscriber() {
		if (listBindings != null) {
			for (Map<Integer, BindingEntry> listBindingItem : listBindings.values()) {
				for (BindingEntry bindingEntry : listBindingItem.values()) {
					if (bindingEntry.getUserStatus() == UserStatus.Active.getStatusCode()) {
						return true;
					}
				}
			}
		}

		return false;
	}

	/**
	 * Checks if E-Mail-Adress given in customerData-HashMap is valid
	 * 
	 * @return true if E-Mail-Adress is valid
	 */
	@Override
	public boolean emailValid() {
		String email = (String) custParameters.get("email");
        return AgnUtils.isEmailValid(email);
	}

	@Override
	public final boolean isDoNotTrackMe() {
		/*
		 * Implemented rules:
		 * 
		 * 1. If "sys_tracking_veto" unset (-> null) : Tracking allowed (method returns false)
		 * 2. If "sys_tracking_veto" set (-> not null) :
		 *    a) if set to 0 : Tracking allowed (method returns false)
		 *    b) if set to <> 0: Tracking not allowed (method returns true)
		 */
		final String value = getCustParameters(ComCompanyDaoImpl.STANDARD_FIELD_DO_NOT_TRACK);
		
		final int flagValue = StringUtils.isEmpty(value) ? 0 : Integer.parseInt(value);
		
		return flagValue != 0;
	}
	
	public static final boolean isDoNotTrackMe(final Map<String, Object> profileFields) {
		/*
		 * Implemented rules:
		 * 
		 * 1. If "sys_tracking_veto" unset (-> null) : Tracking allowed (method returns false)
		 * 2. If "sys_tracking_veto" set (-> not null) :
		 *    a) if set to 0 : Tracking allowed (method returns false)
		 *    b) if set to <> 0: Tracking not allowed (method returns true)
		 */

		final Object valueOrNull = profileFields.get(ComCompanyDaoImpl.STANDARD_FIELD_DO_NOT_TRACK);
		final String value = valueOrNull != null ? valueOrNull.toString() : "";
		
		final int flagValue = StringUtils.isEmpty(value) ? 0 : Integer.parseInt(value);
		
		return flagValue != 0;
	}
	
	@Override
	public final void setDoNotTrackMe(final boolean doNotTrack) {
		this.setCustParameters(ComCompanyDaoImpl.STANDARD_FIELD_DO_NOT_TRACK, doNotTrack ? "1" : "0");
	}
	
	/**
	 * String representation for easier debugging
	 */
	@Override
	public String toString() {
		return
			"(" + companyID + "/" + customerID + ")" +
			" Lastname: " + (custParameters == null ? "" : custParameters.get("lastname")) +
			" Firstname: " + (custParameters == null ? "" : custParameters.get("firstname")) +
			" Gender: " + (custParameters == null ? "" : custParameters.get("gender")) +
			" Email: " + (custParameters == null ? "" : custParameters.get("email")) +
			" Mailtype: " + (custParameters == null ? "" : custParameters.get("mailtype")) +
			" Bindings: " + (listBindings == null ? 0 : listBindings.size()) +
			" ChangeFlag: " + changeFlag;
	}
}
