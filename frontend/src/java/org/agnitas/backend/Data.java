/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package org.agnitas.backend;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.TimeZone;

import org.agnitas.backend.dao.TagDAO;
import org.agnitas.backend.dao.TitleDAO;
import org.agnitas.dao.UserStatus;
import org.agnitas.preview.Page;
import org.agnitas.util.Bit;
import org.agnitas.util.Config;
import org.agnitas.util.Const;
import org.agnitas.util.DBConfig;
import org.agnitas.util.Log;
import org.agnitas.util.Substitute;
import org.agnitas.util.Title;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import com.agnitas.dao.DaoUpdateReturnValueCheck;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

/** Class holding most of central configuration and global database
 * information
 */
public class Data {
	/** the file to read the configuration from */
	private final static String	INI_FILE = "Mailout.ini";
	/** unspecified licence ID */
	private final static int	LICENCE_UNSPEC = 0;
	/** Constant for onepixellog: no automatic insertion */
	final public static int 	OPL_NONE = 0;
	/** Constant for onepixellog: insertion on top */
	final public static int 	OPL_TOP = 1;
	/** Constant for onepixellog: insertion at bottom */
	final public static int 	OPL_BOTTOM = 2;
	/** incarnation of invokation to create unique filenames */
	static long			incarnation = 0;

	/** all informations about the company of this mailing */
	public Company			company;
	/** all mailinglist related stuff of the mailinglist of this mailing */
	public Mailinglist		mailinglist;
	/** all about the maildrop_status for this mail generation */
	public MaildropStatus		maildropStatus;
	/** all about the mailing core information */
	public Mailing			mailing;
	/** all about the mailings target expression */
	public TargetExpression		targetExpression;
	/** Configuration from properties file */
	private Config			cfg = null;
	/** Loglevel */
	private int			logLevel = Log.ERROR;
	private int			licenceID = LICENCE_UNSPEC;
	/** for database access of large chunks, limit single statement */
	private long			limitBlockOperations = 0;
	private long			limitBlockOperationsMax = 0;
	/** database driver name */
	private String			dbDriver = null;
	/** database login */
	private String			dbLogin = null;
	/** database password */
	private String			dbPassword = null;
	/** database connect expression */
	private String			dbConnect = null;
	/** database pool size */
	private int			dbPoolsize = 12;
	/** database growable pool */
	private boolean			dbPoolgrow = true;
	/** name of program to execute meta files */
	private String			xmlBack = "xmlback";
	/** validate each generated block */
	private boolean			xmlValidate = false;
	/** Send samples of worldmailing to dedicated address(es) */
	private String			sampleEmails = null;
	/** write a DB record after creating that number of receiver */
	private int			mailLogNumber = 0;

	/** the user_status for this query */
	public long			defaultUserStatus = UserStatus.Active.getStatusCode();
	/** in case of campaing mailing, send mail only to this customer */
	public long			campaignCustomerID = 0;
	/** for campaign mailings, use this user status in the binding table */
	public long[]			campaignUserStatus = null;
	/** for campaign mailings, enforce sending ignoring mailinglist and user status */
	public boolean			campaignForceSending = false;
	/** for provider preview sending */
	public String			providerEmail = null;
	/** for preview mailings use this for matching the customer ID */
	public long			previewCustomerID = 0;
	/** for preview mailings store output */
	public Page			previewOutput = null;
	/** for preview of external text input */
	public String			previewInput = null;
	/** for preview mailings if preview should be anon */
	public boolean			previewAnon = false;
	/** for preview mailings if only partial preview requested */
	public String			previewSelector = null;
	/** for preview mailings if preview is cachable */
	public boolean			previewCachable = true;
	/** for preview mailings to convert to entities */
	public boolean			previewConvertEntities = false;
	/** for preview mailings for ECS */
	public boolean			previewEcsUIDs = false;
	/** for preview mailings to create all possible parts */
	public boolean			previewCreateAll = false;
	/** for preview mailings to create cachable image lings */
	public boolean			previewCacheImages = true;
	/** optional list of addresses to send bcc copy of mailing */
	public List <String>		bcc = null;
	/** overwtite existing database fields */
	public Map <String, String>	overwriteMap = null;
	/** virtual database fields */
	public Map <String, String>	virtualMap = null;
	/** static values */
	public Map <String, Object>	staticMap = null;
	/** optional infos for this mailing */
	public Map <String, String>	mailingInfo = null;
	/** keeps track of already read EMMTags from database */
	private TagDAO			tagDao = null;
	/** instance to write logs to */
	private Log			log = null;
	/** the ID to write as marker in the logfile */
	private String			lid = null;
	/** the connection to the database */
	public DBase			dbase = null;
	/** current send date, calculated from sendtimstamp and stepping */
	public Date			currentSendDate = null;
	/** the currentSendDate in epoch */
	public long			sendSeconds = 0;
	/** force start of new block */
	public boolean			forceNewBlock = false;
	/** EOL coding for spoolfiles */
	public String			eol = "\r\n";
	
	/** the base for the profile URL */
	public String			profileURL = null;
	public String			profileTag = "/p.html?";
	/** the base for the unsubscribe URL */
	public String			unsubscribeURL = null;
	public String			unsubscribeTag = "/uq.html?";
	/** the base for the auto URL */
	public String			autoURL = null;
	public String			autoTag = "/r.html?";
	/** the base for the onepixellog URL */
	public String			onePixelURL = null;
	public String			onePixelTag = "/g.html?";
	/** the largest mailtype to generate */
	public int			masterMailtype = Const.Mailtype.HTML_OFFLINE;
	/** default line length in text part */
	public int			lineLength = 72;
	/** where to automatically place the onepixellog */
	public int			onepixlog = OPL_NONE;
	/** the base domain to build the base URLs */
	public String			rdirDomain = null;
	/** the mailloop domain */
	public String			mailloopDomain = null;
	/** Collection of media information */
	public List <Media>		media = null;
	public Media			mediaEMail = null;
	/** Bitfield of available media types in mailing */
	public long			availableMedias = 0;
	/** colect blacklisted recipients */
	public Set <Long>		blacklisted = null;
	/** number of all subscriber of a mailing */
	public long			totalSubscribers = -1;
	public long			totalReceivers = -1;
	/** helper class for string substitution */
	private Substitute		substitute = null;
	/** number of all subscriber of a mailing */
	private BC			bigClause = null;
	/** all URLs from rdir url table */
	public List <URL>		URLlist = null;
	private Map <String, URL>	URLTable = null;
	/** information about URL extensions */
	public URLExtension		extendURL = null;
	/** number of entries in URLlist */
	public int			urlcount = 0;
	/** all title tags */
	private Map <Long, Title>	titles = null;
	/** all content blocks of the mailing */
	private BlockCollection		blocks = null;
	/** all content blocks in a map for access by name */
	private Map <String, BlockData>	blockTable = null;
	/** all code fragments for scripted tags */
	private Map <String, Code>	codes = new HashMap <> ();
	/** all informations about available images */
	private Imagepool		imagepool = null;
	/** the share with your network configuration */
	private SWYN			swyn = null;

	/** layout of the customer table */
	public List <Column>		layout = null;
	/** number of entries in layout */
	public int			lcount = 0;
	/** number of entries in layout used */
	public int			lusecount = 0;
	/** for housekeeping of created files */
	private List <String>		toRemove = null;
	/** direct path configuration */
	public boolean			directPath = false;
	/** for building anonymized URLs */
	public String			anonURL = null;
	public String			anonTag = "/v.html?";
	public int			mailingType = Const.Mailing.TYPE_NORMAL;
	public String			workStatus = null;
	/** mailing priority related */
	public boolean			isPriorityMailing = false;
	public int			mailingPriority = 0;
	public String			priorityTable = null;
	public int			priorityTimeID = 0;
	/** worklflow manager related */
	public boolean			isWorkflowMailing = false;
	/** reference to other tables */
	public Map <String, Reference>	references = null;
	/** image link template */
	private String			imageTemplate = null;
	private String			imageTemplateNoCache = null;
	private String			mediapoolTemplate = null;
	private String			mediapoolTemplateNoCache = null;
	private String			mediapoolBackgroundTemplate = null;
	private String			mediapoolBackgroundTemplateNoCache = null;
	/** media SQL restriction */
	private String			mediaRestrictSQL = null;
	/** optional referencing followup for non clickers */
	private long			followupReference = 0;
	private String			followupMethod = null;
	private String			followupRefSQL = null;
	/** optional remove duplicate addresses from output */
	private boolean			removeDuplicateEMails= false;
	/** optional external tracker information */
	public List <Tracker>		tracker = null;
	/** optional dkim in action */
	private boolean			dkimActive = false;
	/** path to database for storing envelope addresses */
	private String			mfromDatabasePath = "var/run/envelope.db";
	/** database id */
	private String			dbID = null;
	public String			dbMS = null;
	/** temp. tablespace */
	private String			dbTempTablespace = null;
	/** my incarnation */
	private long			inc = 0;

	/**
	 * Constructor for the class
	 * @param program the name of the program (for logging setup)
	 * @param status_id the status_id to read the mailing information from
	 * @param option output option
	 * @param conn optional opened database connection
	 */
	public Data (String program, String status_id, String option) throws Exception {
		setupLogging (program, (option == null || !option.equals ("silent")));
		configuration (true);

		logging (Log.DEBUG, "init", "Data read from " + cfg.getSource () + (status_id != null ? " for " + status_id : ""));
		setupDatabase ();
		logging (Log.DEBUG, "init", "Initial database connection established");
		if (status_id != null) {
			try {
				getMailingInformations (status_id);
			} catch (Exception e) {
				throw new Exception ("Database failure: " + e, e);
			}
			logging (Log.DEBUG, "init", "Initial data read from database");
			checkMailingData ();
			lid = "(" +
			      company.id () + "/" +
			      mailinglist.id () + "/" +
			      mailing.id () + "/" +
			      maildropStatus.id () +
			      ")";
			if (islog (Log.DEBUG)) {
				logSettings ();
			}
		}
	}
	/**
	 * Constructor for non mailing based instances
	 * @param program the program name for logging
	 */
	public Data (String program) throws Exception {
		this (program, null, null);
	}

	/**
	 * Cleanup all open resources and write mailing status before
	 */
	public void done () throws Exception {
		int	cnt;
		String	msg;

		cnt = 0;
		msg = "";
		if (bigClause != null) {
			bigClause = bigClause.done ();
		}
		if (dbase != null) {
			logging (Log.DEBUG, "deinit", "Shuting down database connection");
			try {
				closeDatabase ();
			} catch (Exception e) {
				++cnt;
				msg += "\t" + e + "\n";
			}
		}
		if (toRemove != null) {
			int fcnt = toRemove.size ();

			if (fcnt > 0) {
				logging (Log.DEBUG, "deinit", "Remove " + fcnt + " file" + Log.exts (fcnt) + " if existing");
				while (fcnt-- > 0) {
					String	fname = toRemove.remove (0);
					File	file = new File (fname);

					if (file.exists ())
						if (! file.delete ())
							msg += "\trm " + fname + "\n";
					file = null;
				}
			}
			toRemove = null;
		}
		if (company != null) {
			company = company.done ();
		}
		if (mailinglist != null) {
			mailinglist = mailinglist.done ();
		}
		if (maildropStatus != null) {
			maildropStatus = maildropStatus.done ();
		}
		if (mailing != null) {
			mailing = mailing.done ();
		}
		if (targetExpression != null) {
			targetExpression = targetExpression.done ();
		}
		if (cnt > 0)
			throw new Exception ("Unable to cleanup:\n" + msg);
		logging (Log.DEBUG, "deinit", "Cleanup done: " + msg);
	}

	/**
	 * Suspend call between setup and main execution
	 * @param conn optional database connection
	 */
	public void suspend () throws Exception {
		if (maildropStatus.isCampaignMailing () || maildropStatus.isPreviewMailing ()) {
			closeDatabase ();
		}
	}

	/**
	 * Resume before main execution
	 * @param conn optional database connection
	 */
	public void resume () throws Exception {
		if (maildropStatus.isCampaignMailing () || maildropStatus.isPreviewMailing ()) {
			if (dbase == null) {
				setupDatabase ();
			}
		}
	}

	/** Setup logging interface
	 * @param program to create the logging path
	 * @param setprinter if we should also log to stdout
	 */
	private void setupLogging (String program, boolean setprinter) {
		log = new Log (program, logLevel);
		if (setprinter) {
			log.setPrinter (System.out);
		}
	}

	/**
	 * Write all settings to logfile
	 */
	private void logSettings () {
		logging (Log.DEBUG, "init", "Initial data valid");
		logging (Log.DEBUG, "init", "All set variables:");
		logging (Log.DEBUG, "init", "\tlogLevel = " + log.levelDescription () + " (" + log.level () + ")");
		logging (Log.DEBUG, "init", "\tdbDriver = " + dbDriver);
		logging (Log.DEBUG, "init", "\tdbLogin = " + dbLogin);
		logging (Log.DEBUG, "init", "\tdbPassword = ******");
		logging (Log.DEBUG, "init", "\tdbConnect = " + dbConnect);
		logging (Log.DEBUG, "init", "\tdbPoolsize = " + dbPoolsize);
		logging (Log.DEBUG, "init", "\tdbPoolgrow = " + dbPoolgrow);
		logging (Log.DEBUG, "init", "\txmlBack = " + xmlBack);
		logging (Log.DEBUG, "init", "\txmlValidate = " + xmlValidate);
		logging (Log.DEBUG, "init", "\tsampleEmails = " + sampleEmails);
		logging (Log.DEBUG, "init", "\tmailLogNumber = " + mailLogNumber);
		logging (Log.DEBUG, "init", "\tdefaultUserStatus = " + defaultUserStatus);
		logging (Log.DEBUG, "init", "\tdbase = " + dbase);
		logging (Log.DEBUG, "init", "\tsendSeconds = " + sendSeconds);
		logging (Log.DEBUG, "init", "\tprofileURL = " + profileURL);
		logging (Log.DEBUG, "init", "\tunsubscribeURL = " + unsubscribeURL);
		logging (Log.DEBUG, "init", "\tautoURL = " + autoURL);
		logging (Log.DEBUG, "init", "\tonePixelURL = " + onePixelURL);
		logging (Log.DEBUG, "init", "\tmasterMailtype = " + masterMailtype);
		logging (Log.DEBUG, "init", "\tlineLength = " + lineLength);
		logging (Log.DEBUG, "init", "\tonepixlog = " + onepixlog);
		logging (Log.DEBUG, "init", "\trdirDomain = " + rdirDomain);
		logging (Log.DEBUG, "init", "\tmailloopDomain = " + mailloopDomain);
		logging (Log.DEBUG, "init", "\tdbID = " + (dbID == null ? "*unset*" : dbID));
		logging (Log.DEBUG, "init", "\tdbMS = " + (dbMS == null ? "*unset*" : dbMS));
		logging (Log.DEBUG, "init", "\tdbTempTablespace = " + (dbTempTablespace == null ? "*unset*" : dbTempTablespace));
		logging (Log.DEBUG, "init", "\tdirectPath = " + directPath);
		logging (Log.DEBUG, "init", "\tanonURL = " + anonURL);
		logging (Log.DEBUG, "init", "\tlicenceID = " + licenceID);
		logging (Log.DEBUG, "init", "\tlimitBlockOperations = " + limitBlockOperations);
		logging (Log.DEBUG, "init", "\tlimitBlockOperationsMax = " + limitBlockOperationsMax);
		logging (Log.DEBUG, "init", "\tisPriorityMailing = " + isPriorityMailing);
		logging (Log.DEBUG, "init", "\tmailingPriority = " + mailingPriority);
		if (references != null) {
			logging (Log.DEBUG, "init", "\treferences = " + references
				 .values ()
				 .stream ()
				 .map ((r) -> r.table () + " as " + r.name ())
				 .reduce ((a, b) -> a + ", " + b)
				 .orElse ("*none*")
			);
		}
		logging (Log.DEBUG, "init", "\tfollowupReference = " + followupReference);
		logging (Log.DEBUG, "init", "\tfollowupMethod = " + (followupMethod == null ? "*non-opener*" : followupMethod));
		logging (Log.DEBUG, "init", "\tremoveDuplicateEMails = " + removeDuplicateEMails);
		company.logSettings ();
		mailinglist.logSettings ();
		maildropStatus.logSettings ();
		mailing.logSettings ();
		targetExpression.logSettings ();
	}

	/*
	 * Setup configuration
	 * @param checkRsc first check for resource bundle
	 */
	private void configuration (boolean checkRsc) throws Exception {
		boolean		done;

		cfg = new Config (log);
		done = false;
		if (checkRsc) {
			try {
				ResourceBundle	rsc;

				rsc = ResourceBundle.getBundle ("emm");
				if (rsc != null) {
					done = cfg.loadConfig (rsc, "mailout.ini", "mailgun.ini");
				}
			} catch (Exception e) {
				// do nothing
			}
		}
		if (! done) {
			cfg.loadConfig (INI_FILE);
		}
		configure ();
	}
	/*
	 * the main configuration
	 */
	private void configure () throws Exception {
		String	val;

		if ((val = cfg.cget ("LOGLEVEL")) != null) {
			try {
				logLevel = Log.matchLevel (val);
				if (log != null) {
					log.level (logLevel);
				}
			} catch (NumberFormatException e) {
				throw new Exception ("Loglevel must be a known string or a numerical value, not " + val, e);
			}
		}
		dbDriver = cfg.cget ("DB_DRIVER", dbDriver);
		dbLogin = cfg.cget ("DB_LOGIN", dbLogin);
		dbPassword = cfg.cget ("DB_PASSWORD", dbPassword);
		dbConnect = cfg.cget ("SQL_CONNECT", dbConnect);
		dbPoolsize = cfg.cget ("DB_POOLSIZE", dbPoolsize);
		dbPoolgrow = cfg.cget ("DB_POOLGROW", dbPoolgrow);
		xmlBack = cfg.cget ("XMLBACK", xmlBack);
		xmlValidate = cfg.cget ("XMLVALIDATE", xmlValidate);
		if (((sampleEmails = cfg.cget ("SAMPLE_EMAILS", sampleEmails)) != null) &&
			((sampleEmails.length () == 0) || sampleEmails.equals ("-"))) {
			sampleEmails = null;
		}
		if ((val = cfg.cget ("EOL")) != null) {
			if (val.equalsIgnoreCase ("CRLF")) {
				eol = "\r\n";
			} else if (val.equalsIgnoreCase ("LF")) {
				eol = "\n";
			} else {
				throw new Exception ("EOL must be either CRLF or LF, not " + val);
			}
		}
		mailLogNumber = cfg.cget ("MAIL_LOG_NUMBER", mailLogNumber);
		licenceID = cfg.cget ("LICENCE_ID", licenceID);
		limitBlockOperations = cfg.cget ("LIMIT_BLOCK_OPERATIONS", limitBlockOperations);
		limitBlockOperationsMax = cfg.cget ("LIMIT_BLOCK_OPERATIONS_MAX", limitBlockOperationsMax);
		mfromDatabasePath = cfg.cget ("ENVELOPE_DATABASE", mfromDatabasePath);
		dbID = cfg.cget ("DB_ID");
		dbTempTablespace = cfg.cget ("DB_TEMP_TABLESPACE");
		if (dbID != null) {
			DBConfig	dbcfg = new DBConfig ();
			String		configPath = System.getenv ("DBCFG_PATH");

			if (configPath != null) {
				dbcfg.setConfigPath (configPath);
			}
			if (dbcfg.selectRecord (dbID)) {
				dbMS = dbcfg.findInRecord ("dbms", dbMS);
				dbLogin = dbcfg.findInRecord ("user", dbLogin);
				dbPassword = dbcfg.findInRecord ("password", dbPassword);
				dbDriver = dbcfg.findInRecord ("jdbc-driver", dbDriver);
				dbConnect = dbcfg.findInRecord ("jdbc-connect", dbConnect);
			} else {
				logging (Log.WARNING, "cfg", "No entry for dbID " + dbID + " found.");
			}
		} else {
			logging (Log.DEBUG, "cfg", "No dbID found");
		}
		company = new Company (this);
		company.configure (cfg);
		mailinglist = new Mailinglist (this);
		mailinglist.configure (cfg);
		maildropStatus = new MaildropStatus (this);
		maildropStatus.configure (cfg);
		mailing = new Mailing (this);
		mailing.configure (cfg);
		targetExpression = new TargetExpression (this);
		targetExpression.configure (cfg);
	}
	
	/** check if database is available
	 */
	private void checkDatabase () throws Exception {
		if (dbase == null) {
			throw new Exception ("Database not available");
		}
	}

	/**
	 * setup database connection and retrieve a list of all available
	 * tables
	 * @param conn an optional existing database connection
	 */
	private void setupDatabase () throws Exception {
		try {
			dbase = new DBase (this);
			dbase.setup ();
			dbase.initialize ();
		} catch (Exception e) {
			throw new Exception ("Database setup failed: " + e.toString (), e);
		}
	}

	/** close a database and free all assigned data
	 */
	private void closeDatabase () throws Exception {
		if (dbase != null) {
			try {
				dbase = dbase.done ();
			} catch (Exception e) {
				throw new Exception ("Database close failed: " + e.toString (), e);
			}
		}
	}

	/**
	 * query all basic information about this mailing
	 * @param status_id the reference to the mailing
	 */
	@DaoUpdateReturnValueCheck
	private void getMailingInformations (String status_id) throws Exception {
		try {
			String[]	sdetail = status_id.split (":", 2);

			if (sdetail.length == 2) {
				setupMailingInformations (sdetail[0], sdetail[1]);
			} else {
				maildropStatus.id (Long.parseLong (status_id));
				maildropStatus.retrieveInformation ();
			}
			company.retrieveInformation ();
			if (dbase.tableExists ("reference_tbl")) {
				retrieveReferenceTableDefinitions ();
			}
			if (mailing.id () > 0) {
				retrieveMailingInformation ();
				retrieveMediaInformation ();
				targetExpression.retrieveInformation ();
			}
			targetExpression.handleMissingTargetExpression ();
			retrieveURLsForMeasurement ();
			retrieveCustomerTableLayout ();
			finalizeConfiguration ();
		} catch (Exception e) {
			logging (Log.ERROR, "init", "Error in quering initial data: " + e.toString (), e);
			throw new Exception ("Database error/initial query: " + e, e);
		}
	}
	
	private void setupMailingInformations (String prefix, String status) throws Exception {
		switch (prefix) {
		case "provider":
			setupMailingInformationsForProviderPreview (status);
			break;
		case "preview":
			setupMailingInformationsForPreview (status);
			break;
		default:
			throw new Exception ("Unknown status prefix \"" + prefix + "\" encountered");
		}
	}
	
	private void setupMailingInformationsForProviderPreview (String status) throws SQLException {
		maildropStatus.id (0);
		mailing.id (Long.parseLong (status));
		company.id (mailing.companyID ());
		maildropStatus.statusField ("V");
		maildropStatus.sendDate (null);
	}

	private void setupMailingInformationsForPreview (String status) throws SQLException {
		String[]	opts = status.split (",");

		if (opts.length > 0) {
			try {
				mailing.id (Long.parseLong (opts[0]));
			} catch (NumberFormatException e) {
				logging (Log.WARNING, "setup", "Unparseable input string for mailing_id: \"" + opts[0] + "\": " + e.toString ());
				mailing.id (0);
			}
		} else {
			mailing.id (0);
		}
		if (mailing.id () > 0) {
			company.id (mailing.companyID ());
		} else {
			mailing.id (0);
			company.id (-1);
			mailinglist.id  (-1);
			try {
				if (opts.length > 1) {
					company.id (Long.parseLong (opts[1]));
					if (opts.length > 2) {
						mailinglist.id (Long.parseLong (opts[2]));
					}
				}
			} catch (NumberFormatException e) {
				logging (Log.WARNING, "setup", "Unparseable input string for preview \"" + status + "\": " + e.toString ());
			}
		}
		maildropStatus.statusField ("P");
		maildropStatus.sendDate (null);
	}
		
	private void retrieveReferenceTableDefinitions () throws Exception {
		String	ref = company.info ("references", mailing.id ());
		
		if (ref != null) {
			String[]	refs = ref.split ("\\?");
			
			for (int n = 0; n < refs.length; ++n) {
				addReference (refs[n], true);
			}
		}
		
		List <Map <String, Object>>	rq;
		rq = dbase.query (
			"SELECT name, reftable, refsource, refcolumn, backref, joincondition, order_by, voucher " + 
			"FROM reference_tbl " + 
			"WHERE company_id = :companyID AND (deleted IS NULL OR deleted = 0)",
			"companyID", company.id ()
		);
		for (int n = 0; n < rq.size (); ++n) {
			Map <String, Object>	row =rq.get (n);
			
			addReference (dbase.asString (row.get ("name")),
				      dbase.asString (row.get ("reftable")),
				      dbase.asString (row.get ("refsource")),
				      dbase.asString (row.get ("refcolumn")),
				      dbase.asString (row.get ("backref")),
				      dbase.asString (row.get ("joincondition")),
				      dbase.asString (row.get ("order_by")),
				      dbase.asInt (row.get ("voucher")) == 1,
				      true);
		}
		resolveReferenceAliases ();
	}

	private void retrieveMailingInformation () throws Exception {
		retrieveMailingStaticInformation ();
		if (licenceID == LICENCE_UNSPEC) {
			retrieveLicenceID ();
		}
		retrieveMailingDynamicInformation ();
	}
	private void retrieveMailingStaticInformation () throws Exception {
		Timestamp	creation;
		
		mailinglist.id (mailing.mailinglistID ());
		creation = mailing.creationDate ();
		company.secretTimestamp (creation != null ? creation.getTime () : 0);
		targetExpression.expression (mailing.targetExpression ());
		targetExpression.splitID (mailing.splitID ());
		if (maildropStatus.isPreviewMailing ()) {
			targetExpression.clear ();
		}
		
		mailingType = mailing.mailingType ();
		workStatus = mailing.workStatus ();
		mailingPriority = mailing.priority ();
		if (mailingPriority == -1) {
			mailingPriority = 0;
			if (company.priorityCount () > 0) {
				if (mailing.mailtemplateID () > 0) {
					findMailingPriorityFromTemplate ();
				}
			}
		}
		mailinglist.retrieveInformation ();

		rdirDomain = mailinglist.rdirDomain ();
		if (rdirDomain == null) {
			rdirDomain = company.rdirDomain ();
		}
		mailloopDomain = company.mailloopDomain ();
		isWorkflowMailing = mailing.isWorkflowMailing ();
		if (isWorkflowMailing) {
			logging (Log.DEBUG, "dryrun", "Dryrun mailing detected, apply target expression to test mailing");
		}
	}
	private void findMailingPriorityFromTemplate () throws Exception {
		mailingPriority = mailing.sourceTemplatePriority ();
		if (mailingPriority > 0) {
			logging (Log.DEBUG, "prio", "Found mailing priority " + mailingPriority + " from template");
		} else {
			logging (Log.DEBUG, "prio", "No mailing priority from template found");
		}
	}
	private void retrieveLicenceID () throws Exception {
		List <Map <String, Object>>	rq;
		Map <String, Object>		row;

		rq = dbase.query ("SELECT value FROM config_tbl WHERE class = :class AND name = :name", "class", "system", "name", "licence");
		for (int n = 0; n < rq.size (); ++n) {
			row = rq.get (n);

			String	lstr = dbase.asString (row.get ("value"));
			if (lstr != null) {
				licenceID = StringOps.atoi (lstr, licenceID);
			}
		}
	}
	private void retrieveMailingDynamicInformation () throws Exception {
		Map <String, String>	minfo = mailing.info ();
		
		if (minfo != null) {
			for (Map.Entry <String, String> kv : minfo.entrySet ()) {
				addMailingInfo (kv.getKey (), kv.getValue ());
			}
		}
	}
		
	private void retrieveMediaInformation () throws Exception {
		media = mailing.media ();

		parseMediaStaticInformation ();
		
		Dkim	dkim = new Dkim (this);
		
		dkimActive = dkim.check (mailing.fromEmail ());
	}
	private void parseMediaStaticInformation () throws Exception {
		int	usedMedia;
						
		availableMedias = 0;
		usedMedia = 0;
		for (Media tmp : media) {
			switch (tmp.type) {
			case Media.TYPE_EMAIL:
				mediaEMail = tmp;
				mailing.fromEmail (new EMail (findMediadata (tmp, "from")));
				mailing.replyTo (new EMail (findMediadata (tmp, "reply")));
				
				String	env = findMediadata (tmp, "envelope");
				
				if (env != null) {
					mailing.envelopeFrom (new EMail (env));
				}
				mailing.subject (findMediadata (tmp, "subject"));
				masterMailtype = ifindMediadata (tmp, "mailformat", masterMailtype);
				if (masterMailtype > Const.Mailtype.HTML_OFFLINE) {
					masterMailtype = Const.Mailtype.HTML_OFFLINE;
				}
				mailing.encoding (findMediadata (tmp, "encoding", mailing.defaultEncoding ()));
				mailing.charset (findMediadata (tmp, "charset", mailing.defaultCharset ()));
				lineLength = ifindMediadata (tmp, "linefeed", lineLength);
						
				String	opl = findMediadata (tmp, "onepixlog", "none");
					
				if (opl.equals ("top"))
					onepixlog = OPL_TOP;
				else if (opl.equals ("bottom"))
					onepixlog = OPL_BOTTOM;
				else
					onepixlog = OPL_NONE;
				
				followupReference = ifindMediadata (tmp, "followup_for", 0L);
				if (! maildropStatus.isWorldMailing ()) {
					followupReference = 0L;
				}
				if (followupReference > 0) {
					followupMethod = findMediadata (tmp, "followup_method", null);
				}
				removeDuplicateEMails = bfindMediadata (tmp, "remove_dups", false);
				if (bfindMediadata (tmp, "intelliad_enabled", false)) {
					String	id = findMediadata (tmp, "intelliad_string", null);
					
					if (id != null) {
						addTracker ("intelliAd", id);
					} else {
						logging (Log.WARNING, "init", "Missing intelliAd ID string even if tracking for intelliAd is enabled");
					}
				}
				break;
			}
			if (tmp.stat == Media.STAT_ACTIVE)
				if (! Bit.isset (availableMedias, tmp.type)) {
					availableMedias = Bit.set (availableMedias, tmp.type);
					++usedMedia;
				}
		}
		if (usedMedia > 0) {
			long	seen;
			
			if (usedMedia == 1)
				mediaRestrictSQL = "bind.mediatype = ";
			else
				mediaRestrictSQL = "bind.mediatype IN (";
			seen = 0;
			for (Media tmp : media) {
				if (! Bit.isset (seen, tmp.type)) {
					if (seen != 0)
						mediaRestrictSQL += ", ";
					mediaRestrictSQL += Integer.toString (tmp.type);
					seen = Bit.set (seen, tmp.type);
				}
			}
			if (usedMedia > 1)
				mediaRestrictSQL += ")";
		} else
			mediaRestrictSQL = null;
		mailing.setEnvelopeFrom ();
		company.infoAdd ("_envelope_from", mailing.getEnvelopeFrom ());
		mailing.setEncoding ();
		mailing.setCharset ();
	}

	private void retrieveURLsForMeasurement () throws Exception {
		List <Map <String, Object>> rc;

		URLlist = new ArrayList <> ();
		if (mailing.id () > 0) {
			rc = dbase.query ("SELECT url_id, full_url, " + dbase.measureType + ", admin_link, original_url, static_value " +
					  "FROM rdir_url_tbl " +
					  "WHERE company_id = :companyID AND mailing_id = :mailingID AND (deleted IS NULL OR deleted = 0)",
					  "companyID", company.id (),
					  "mailingID", mailing.id ());
			for (int n = 0; n < rc.size (); ++n) {
				Map <String, Object>	row = rc.get (n);
				long			id = dbase.asLong (row.get ("url_id"));
				String			dest = dbase.asString (row.get ("full_url"));
				long			usage = dbase.asLong (row.get (dbase.measureRepr));

				if (usage != 0) {
					URL url = new URL (id, dest, usage);

					url.setAdminLink (dbase.asInt (row.get ("admin_link")) > 0);
					url.setOriginalURL (dbase.asString (row.get ("original_url")));
					url.setStaticValue (dbase.asInt (row.get ("static_value")) == 1);
					URLlist.add (url);
				}
			}
		}
		urlcount = URLlist.size ();
		getURLDetails ();
	}
	private void getURLDetails () {
		extendURL = new URLExtension (this);
		
		List <Map <String, Object>>	rq;
		String				query;
		
		query = "SELECT url_id, param_key, param_value " +
			"FROM rdir_url_param_tbl " + 
			"WHERE url_id IN (SELECT url_id FROM rdir_url_tbl WHERE mailing_id = :mailingID AND company_id = :companyID) AND param_type = :paramType " +
			"ORDER BY param_key";
		try {
			rq = dbase.query (query, "mailingID", mailing.id (), "companyID", company.id (), "paramType", "LinkExtension");
			for (int n = 0; n < rq.size (); ++n) {
				Map <String, Object>	row = rq.get (n);
				long			urlID = dbase.asLong (row.get ("url_id"));
				String			pKey = dbase.asString (row.get ("param_key"));
				String			pVal = dbase.asString (row.get ("param_value"));
			
				extendURL.add (urlID, pKey, pVal);
			}
		} catch (Exception e) {
			logging (Log.ERROR, "url-details", "Failed to retrieve url extension: " + e.toString (), e);
		}
	}
	private void retrieveCustomerTableLayout () throws Exception {
		Map <String, Column>		cmap = new HashMap <> ();

		getTableLayout ("customer_" + company.id () + "_tbl", null);
		for (Column c : layout) {
			cmap.put (c.getName (), c);
		}

		for (Map <String, Object> row : dbase.query ("SELECT col_name, shortname FROM customer_field_tbl WHERE company_id = :companyID", "companyID", company.id ())) {
			String	column = dbase.asString (row.get ("col_name"));

			if (column != null) {
				Column	c = cmap.get (column);

				if (c != null) {
					c.setAlias (dbase.asString (row.get ("shortname")));
				}
			}
		}
	}
	private void finalizeConfiguration () throws Exception {
		List <Map <String, Object>>	rq;
		Map <String, Object>		row;

		setupSubstitution ();
		
		if (StringOps.atob (company.info ("opt-in-mailing", mailing.id ()), false)) {
			defaultUserStatus = UserStatus.WaitForConfirm.getStatusCode();
		}
		
		imageTemplate = company.infoSubstituted ("imagelink-template", mailing.id (), "%(rdir-domain)/image/%(licence-id)/%(company-id)/%(mailing-id)/[name]");
		imageTemplateNoCache = company.infoSubstituted ("imagelink-template-no-cache", mailing.id (), "%(rdir-domain)/image/nc/%(licence-id)/%(company-id)/%(mailing-id)/[name]");
		mediapoolTemplate = company.infoSubstituted ("imagelink-mediapool-template", mailing.id (), "%(rdir-domain)/mediapool_element/%(licence-id)/%(company-id)/%(mailing-id)/[name]");
		mediapoolTemplateNoCache = company.infoSubstituted ("imagelink-mediapool-template-no-cache", mailing.id (), "%(rdir-domain)/mediapool_element/nc/%(licence-id)/%(company-id)/%(mailing-id)/[name]");
		mediapoolBackgroundTemplate = company.infoSubstituted ("imagelink-mediapool-background-template", mailing.id (), "%(rdir-domain)/bg_image/%(licence-id)/%(company-id)/%(mailing-id)/[name]");
		mediapoolBackgroundTemplateNoCache = company.infoSubstituted ("imagelink-mediapool-background-template-no-cache", mailing.id (), "%(rdir-domain)/bg_image/nc/%(licence-id)/%(company-id)/%(mailing-id)/[name]");

		if (followupReference > 0) {
			final int	METHOD_NON_OPENER = 0;
			final int	METHOD_OPENER = 1;
			final int	METHOD_NON_CLICKER = 2;
			final int	METHOD_CLICKER = 3;
			int		method = METHOD_NON_OPENER;
			
			if (followupMethod != null)
				if (followupMethod.equals (Const.Mailing.TYPE_FOLLOWUP_NON_OPENER)) {
					method = METHOD_NON_OPENER;
				} else if (followupMethod.equals (Const.Mailing.TYPE_FOLLOWUP_OPENER)) {
					method = METHOD_OPENER;
				} else if (followupMethod.equals (Const.Mailing.TYPE_FOLLOWUP_NON_CLICKER)) {
					method = METHOD_NON_CLICKER;
				} else if (followupMethod.equals (Const.Mailing.TYPE_FOLLOWUP_CLICKER)) {
					method = METHOD_CLICKER;
				}
			if ((! company.mailtracking ()) && ((method == METHOD_NON_OPENER) || (method == METHOD_NON_CLICKER))) {
				throw new Exception ("Request followupmail to " + followupReference + " for " + (followupMethod == null ? "non-opener" : followupMethod) + " requires active mailtracking");
			}
			
			long	statusID = maildropStatus.findLargestStatusIDForWorldMailing (followupReference);

			if (statusID > 0) {
				if ((method == METHOD_NON_OPENER) || (method == METHOD_NON_CLICKER)) {
					int	cnt;
					
					row = dbase.querys ("SELECT count(*) cnt FROM " + company.mailtrackingTable () + " WHERE maildrop_status_id = :statusID", "statusID", statusID);
					if (row == null) {
						throw new Exception ("Failed to query reference mailingID " + followupReference + ", failed to count");
					}
					cnt = dbase.asInt (row.get ("cnt"));
					if (cnt == 0) {
						throw new Exception ("Reference MailingID " + followupReference + " has no entry in mailtracktable " + company.mailtrackingTable () + " for StatusID " + statusID);
					}
					followupRefSQL = "(EXISTS (SELECT 1 FROM " + company.mailtrackingTable () + " fup_mt WHERE maildrop_status_id = " + statusID + " AND fup_mt.customer_id = bind.customer_id)" +
						" AND NOT EXISTS (SELECT 1 FROM rdirlog_" + company.id () + "_tbl fup_rd WHERE mailing_id = "  + followupReference + " AND fup_rd.customer_id = bind.customer_id)";
					if (method == METHOD_NON_OPENER) {
						followupRefSQL += " AND NOT EXISTS (SELECT 1 FROM onepixellog_" + company.id () + "_tbl fup_op WHERE mailing_id = " + followupReference + " AND fup_op.customer_id = bind.customer_id)";
					}
					followupRefSQL += ")";
				} else if ((method == METHOD_OPENER) || (method == METHOD_CLICKER)) {
					followupRefSQL = "(EXISTS (SELECT 1 FROM rdirlog_" + company.id () + "_tbl fup_rd WHERE mailing_id = " + followupReference + " AND fup_rd.customer_id = bind.customer_id)";
					if (method == METHOD_OPENER) {
						followupRefSQL += " OR EXISTS (SELECT 1 FROM onepixellog_" + company.id () + "_tbl fup_op WHERE mailing_id = " + followupReference + " AND fup_op.customer_id = bind.customer_id)";
					}
					followupRefSQL += ")";
				} else {
					throw new Exception ("Invalid followup method " + method + " found");
				}
			} else {
				throw new Exception ("Invalid reference MailingID " + followupReference + " found (no world mailing with this mailingID available)");
			}
		}
		
		String		temp;
		
		if ((temp = company.infoSubstituted ("message-domain")) != null)
			mailing.domain (temp);
		if ((temp = company.infoSubstituted ("message-mailer")) != null)
			mailing.mailer (temp);
		if ((temp = company.info ("block-size", mailing.id ())) != null) {
			mailing.blockSize (StringOps.atoi (temp, -1));
		}
		if ((temp = company.info ("block-step", mailing.id ())) != null) {
			mailing.stepping (StringOps.atoi (temp, -1));
		}
		if ((temp = company.info ("force-sending", mailing.id ())) != null) {
			campaignForceSending = StringOps.atob (temp, campaignForceSending);
		}
		if ((temp = company.info ("limit-block-operations", mailing.id ())) != null) {
			limitBlockOperations = StringOps.atoi (temp, 0);
		}
		if ((temp = company.info ("limit-block-operations-max", mailing.id ())) != null) {
			limitBlockOperationsMax = StringOps.atoi (temp, 0);
		}
		if ((temp = company.info ("create-uuid", mailing.id ())) != null) {
			mailing.createUUID (StringOps.atob (temp, false));
		}
		
		String		url, tag;
		if (rdirDomain == null)
			rdirDomain = company.infoSubstituted ("url-default");
		if ((url = company.infoSubstituted ("url-profile")) != null)
			profileURL = url;
		if ((url = company.infoSubstituted ("url-unsubscribe")) != null)
			unsubscribeURL = url;
		if ((url = company.infoSubstituted ("url-auto")) != null)
			autoURL = url;
		if ((url = company.infoSubstituted ("url-onepixel")) != null)
			onePixelURL = url;
		if ((url = company.infoSubstituted ("url-anon")) != null)
			anonURL = url;
		if ((tag = company.infoSubstituted ("url-profile-tag")) != null)
			profileTag = tag;
		if ((tag = company.infoSubstituted ("url-unsubscribe-tag")) != null)
			unsubscribeTag = tag;
		if ((tag = company.infoSubstituted ("url-auto-tag")) != null)
			autoTag = tag;
		if ((tag = company.infoSubstituted ("url-onepixel-tag")) != null)
			onePixelTag = tag;
		if ((tag = company.infoSubstituted ("url-anon-tag")) != null)
			anonTag = tag;
		if (rdirDomain != null)
			company.infoAdd ("_rdir_domain", rdirDomain);
		if (mailloopDomain != null)
			company.infoAdd ("_mailloop_domain", mailloopDomain);
		directPath = StringOps.atob (company.info ("direct-path", mailing.id ()), false);
		if (rdirDomain != null) {
			if (anonURL == null) {
				anonURL = rdirDomain + anonTag;
			}
		}
		
		isPriorityMailing = false;
		if ((maildropStatus.isWorldMailing () ||maildropStatus.isRuleMailing ()) &&
		    (company.priorityCount () > 0) &&
		    (mailingPriority > 0)) {
			logging (Log.DEBUG, "priority", "Company is enabled for priority mailings with a limit of " + company.priorityCount () + " and mailing priority of " + mailingPriority);

			String	priorityConfigTable = "priority_config_tbl";
			
			priorityTable = "priority_" + company.id () + "_tbl";
			if (dbase.tableExists (priorityConfigTable) && dbase.tableExists (priorityTable)) {
				Calendar	now = Calendar.getInstance ();
				String		configQuery = "SELECT value FROM " + priorityConfigTable + " WHERE company_id = :companyID AND variable = :variable";
				String		timeID;

				priorityTimeID = now.get (Calendar.YEAR) * 10000 + (now.get (Calendar.MONTH) + 1) * 100 + now.get (Calendar.DAY_OF_MONTH);
				timeID = Integer.toString (priorityTimeID);
					
				logging (Log.DEBUG, "priorty", "Looking for existing configuration for " + timeID);
					
				row = dbase.querys (configQuery, "companyID", company.id (), "variable", "last-run");
				if (row != null) {
					String	value = dbase.asString (row.get ("value"));
						
					if ((value != null) && value.equals (timeID)) {
						row = dbase.querys (configQuery, "companyID", company.id (), "variable", "set-priorities");
						if (row != null) {
							value = dbase.asString (row.get ("value"));
							if (value != null) {
								try {
									JsonFactory	factory = new JsonFactory ();
									try (JsonParser	parser  = factory.createParser (value)) {
										String		mailingID = Long.toString (mailing.id ());
											
										while (! parser.isClosed ()) {
											JsonToken	jsonToken = parser.nextToken ();
												
											if ((jsonToken == JsonToken.FIELD_NAME) && parser.getValueAsString ().equals (mailingID)) {
												isPriorityMailing = true;
												break;
											}
										}
										
										if (! isPriorityMailing) {
											logging (Log.DEBUG, "priority", "No entry in set priorities for mailing " + mailingID + " found");
										}
									}
								} catch (Exception e) {
									logging (Log.DEBUG, "priority", "Failed in parsing json: " + e.toString () + "\n" + value);
								}
							} else {
								logging (Log.DEBUG, "priority", "Empty value for setted priorities found");
							}
						} else {
							logging (Log.DEBUG, "priority", "No priority setting information found");
						}
					} else {
						logging (Log.DEBUG, "priority", "Priority for today (" + timeID + ") not (yet) calculated, last calculation had been " + (value != null ? value : "never"));
					}
				} else {
					logging (Log.DEBUG, "priority", "Priority had never been calculated for this mailing");
				}
				logging (Log.DEBUG, "priority", "Priotory handling is " + (isPriorityMailing ? "enabled" : "disabled"));
			} else {
				logging (Log.DEBUG, "priority", "Either " + priorityConfigTable + " or " + priorityTable + " is not existing");
			}
		} else if (company.priorityCount () > 0) {
			logging (Log.DEBUG, "priority", "No priority found for this mailing");
		}
		if (rdirDomain != null) {
			if (profileURL == null)
				profileURL = rdirDomain + profileTag;
			if (unsubscribeURL == null)
				unsubscribeURL = rdirDomain + unsubscribeTag;
			if (autoURL == null)
				autoURL = rdirDomain + autoTag;
			if (onePixelURL == null)
				onePixelURL = rdirDomain + onePixelTag;
		}
	}
	/**
	 * Validate all set variables and make a sanity check
	 * on the database to avoid double triggering of a
	 * mailing
	 */
	@DaoUpdateReturnValueCheck
	public void checkMailingData () throws Exception {
		int cnt;
		String	msg;

		cnt = 0;
		msg = "";
		if (maildropStatus.isWorldMailing ()) {
			try {
				List <Map <String, Object>>	rq;
				Map <String, Object>		row;
				long				nid;

				checkDatabase ();
				nid = maildropStatus.findSmallestStatusIDForWorldMailing (mailing.id ());
				if (nid == 0) {
					throw new Exception ("no entry at all for mailingID " + mailing.id () + " found");
				}
				if (nid != maildropStatus.id ()) {
					++cnt;
					msg += "\tlowest maildrop_status_id is not mine (" + maildropStatus.id () + ") but " + nid + "\n";
					maildropStatus.removeEntry ();
				}
			} catch (Exception e) {
				++cnt;
				msg += "\tunable to requery my status_id: " + e.toString () + "\n";
			}
		}
		if ((! maildropStatus.isPreviewMailing ()) && (maildropStatus.id () < 0)) {
			++cnt;
			msg += "\tmaildrop_status_id is less than 1 (" + maildropStatus.id ()+ ")\n";
		}
		if (company.id () <= 0) {
			++cnt;
			msg += "\tcompany_id is less than 1 (" + company.id () + ")\n";
		}
		if (mailinglist.id () <= 0) {
			++cnt;
			msg += "\tmailinglist_id is less than 1 (" + mailinglist.id () + ")\n";
		}
		if (mailing.id () < 0) {
			++cnt;
			msg += "\tmailing_id is less than 0 (" + mailing.id () + ")\n";
		}
		if ((! maildropStatus.isAdminMailing ()) &&
		    (! maildropStatus.isTestMailing ()) &&
		    (! maildropStatus.isCampaignMailing ()) &&
		    (! maildropStatus.isRuleMailing ()) &&
		    (! maildropStatus.isOnDemandMailing ()) &&
		    (! maildropStatus.isWorldMailing ()) &&
		    (! maildropStatus.isPreviewMailing ())) {
			++cnt;
			msg += "\tstatus_field must be one of A, V, T, E, R, D, W or P (" + maildropStatus.statusField () + ")\n";
		}

		long		now = System.currentTimeMillis () / 1000;
		Timestamp	sendDate = maildropStatus.sendDate ();

		if (sendDate != null) {
			sendSeconds = sendDate.getTime () / 1000;
			if (sendSeconds < now) {
				sendSeconds = now;
			}
		} else {
			sendSeconds = now;
		}
		currentSendDate = new Date (sendSeconds * 1000);
		if ((profileURL == null) || (profileURL.length () == 0)) {
			++cnt;
			msg += "\tmissing or empty profile_url\n";
		}
		if ((unsubscribeURL == null) || (unsubscribeURL.length () == 0)) {
			++cnt;
			msg += "\tmissing or empty unsubscribe_url\n";
		}
		if ((autoURL == null) || (autoURL.length () == 0)) {
			++cnt;
			msg += "\tmissing or empty auto_url\n";
		}
		if ((onePixelURL == null) || (onePixelURL.length () == 0)) {
//			++cnt;
			onePixelURL = "file://localhost/";
			msg += "\tmissing or empty onepixel_url\n";
		}
		if (lineLength < 0) {
			++cnt;
			msg += "\tlinelength is less than zero\n";
		}
		if (cnt > 0) {
			logging (Log.ERROR, "init", "Error configuration report:\n" + msg);
			throw new Exception (msg);
		}
		if (msg.length () > 0)
			logging (Log.INFO, "init", "Configuration report:\n" + msg);
	}
	
	private void addTracker (String name, String code) {
		if (tracker == null) {
			tracker = new ArrayList<>();
		}
		tracker.add (new Tracker (name, code));
	}

	private synchronized long getIncarnation () {
		if (++incarnation < 0) {
			incarnation = 1;
		}
		return incarnation;
	}
	
	/**
	 * find an entry from the media record for this mailing
	 * @param m instance of media record
	 * @param id the ID to look for
	 * @param dflt a default value if no entry is found
	 * @return the found entry or the default
	 */
	public String findMediadata (Media m, String id, String dflt) {
		return m.findParameterValue (id, dflt);
	}
	public String findMediadata (Media m, String id) {
		return findMediadata (m, id, null);
	}

	/**
	 * find a numeric entry from the media record for this mailing
	 * @param m instance of media record
	 * @param id the ID to look for
	 * @param dflt a default value if no entry is found
	 * @return the found entry or the default
	 */
	public long ifindMediadata (Media m, String id, long dflt) {
		String	tmp = findMediadata (m, id, null);

		if (tmp != null) {
			try {
				return Integer.parseInt (tmp);
			} catch (Exception e) {
				// do nothing
			}
		}
		return dflt;
	}
	public int ifindMediadata (Media m, String id, int dflt) {
		return (int) ifindMediadata (m, id, (long) dflt);
	}

	/**
	 * find a boolean entry from the media record for this mailing
	 * @param m instance of media record
	 * @param id the ID to look for
	 * @param dflt a default value if no entry is found
	 * @return the found entry or the default
	 */
	public boolean bfindMediadata (Media m, String id, boolean dflt) {
		String	tmp = findMediadata (m, id, null);
		boolean rc = dflt;

		if (tmp != null)
			if (tmp.length () == 0)
				rc = true;
			else {
				String tok = tmp.substring (0, 1).toLowerCase ();

				if (tok.equals ("t") || tok.equals ("y") ||
					tok.equals ("+") || tok.equals ("1"))
					rc = true;
			} else
				rc = false;
		return rc;
	}

	private void setupSubstitution () {
		substitute = new Substitute (
			"status-id", maildropStatus.id (),
			"licence-id", licenceID,
			"company-id", company.id (),
			"base-company-id", company.baseID (),
			"mailinglist-id", mailinglist.id (),
			"mailtemplate-id", mailing.mailtemplateID (),
			"mailing-id", mailing.id (),
			"status-field", maildropStatus.statusField (),
			"mailing-name", mailing.name (),
			"company-name", company.name (),
			"rdir-domain", rdirDomain,
			"mailloop-domain", mailloopDomain,
			"total-subscribers", totalSubscribers,
			"total-receivers", totalReceivers
		);
		if (mailingInfo != null) {
			for (Map.Entry <String, String> kv : mailingInfo.entrySet ()) {
				substitute.put ("mailing." + kv.getKey (), kv.getValue ());
			}
		}
	}

	public String substituteString (String str, Map <String, String> extra, List <String> missing, String defaultValue) {
		return substitute.replace (str != null ? str : defaultValue, extra, missing);
	}
	public String substituteString (String str, Map <String, String> extra, List <String> missing) {
		return substituteString (str, extra, missing, null);
	}
	public String substituteString (String str, Map <String, String> extra, String defaultValue) {
		return substituteString (str, extra, null, defaultValue);
	}
	public String substituteString (String str, Map <String, String> extra) {
		return substituteString (str, extra, null, null);
	}
	public String substituteString (String str, String defaultValue) {
		return substituteString (str, null, null, defaultValue);
	}
	public String substituteString (String str) {
		return substituteString (str, null, null, null);
	}
	
	public void addMailingInfo (String name, String value) {
		if (mailingInfo == null)
			mailingInfo = new HashMap <> ();
		mailingInfo.put (name, value == null ? "" : value);
	}
	
	private boolean doAddReference (Reference r, boolean overwrite) {
		if (r.valid ()) {
			if (references == null) {
				references = new HashMap <> ();
			}
			if (overwrite || (! references.containsKey (r.name ()))) {
				references.put (r.name (), r);
			}
			return true;
		}
		return false;
	}
	public boolean addReference (String name, String table, String customerExpression, String keyColumn, String backRef, String joinCondition, String orderBy, boolean isVoucher, boolean overwrite) {
		return doAddReference (new Reference (name, table, customerExpression, keyColumn, backRef, joinCondition, orderBy, isVoucher), overwrite);
	}
	public boolean addReference (String ref, boolean overwrite) {
		boolean	rc = false;
		
		if (ref != null) {
			Reference	r = new Reference (ref.trim ());
			
			if (r.valid ()) {
				rc = doAddReference (r, overwrite);
			} else {
				logging (Log.WARNING, "ref", "Reference expression \"" + ref + "\" is invalid");
			}
		}
		return rc;
	}
	
	private void resolveReferenceAliases () {
		String	enableReferenceAliases;
		String	refAliases;

		if (((enableReferenceAliases = company.info ("ref:enable-reference-aliases")) != null) && StringOps.atob (enableReferenceAliases, false) &&
		    (references != null) && (mailingInfo != null) && ((refAliases = mailingInfo.get ("ref:aliases")) != null)) {
			Map <String, String>	aliases = new HashMap<>();
			
 			for (String elem : refAliases.split (", *")) {
				String[]	pair = elem.split (" *= *", 2);
				
				if (pair.length == 2) {
					aliases.put (pair[1], pair[0]);
				}
			}
			for (Reference r : references.values ()) {
				String	alias = aliases.get (r.name ());
				
				if (alias != null) {
					if (r.validName (alias)) {
						logging (Log.DEBUG, "resolve-ref", "Rename reference table " + r.name () + " to " + alias);
						r.setName (alias);
						references.put (r.name (), r);
					} else {
						logging (Log.DEBUG, "resolve-ref", "Failed to rename reference table " + r.name () + " to " + alias + " due to invalid alias name");
					}
				}
			}
		}
	}

	public boolean fullfillReference (Reference r) {
		boolean	rc;
		
		if (! r.isFullfilled ()) {
			r.fullfill ();
			try {
				getTableLayout (r.table (), r.name ());
				rc = true;
			} catch (Exception e) {
				logging (Log.ERROR, "layout", "Failed to get layout for reference table \"" + r.table () + "\"", e);
				rc = false;
			}
			if (r.backReference () != null) {
				Reference	br = references.get (r.backReference ());
				
				if (br != null) {
					if (! fullfillReference (br)) {
						rc = false;
					}
				} else {
					logging (Log.ERROR, "layout", "Failed to find back reference \"" + r.backReference () + "\" for \"" + r.name () + "\"");
					rc = false;
				}
			}
		} else {
			rc = true;
		}
		return rc;
	}
	
	public void markBlacklisted (Long cid) {
		if (blacklisted == null) {
			blacklisted = new HashSet<>();
		}
		if (! blacklisted.contains (cid)) {
			blacklisted.add (cid);
			totalReceivers--;
		}
	}
	
	public void correctReceiver () {
		if (blacklisted != null) {
			for (Long cid : blacklisted) {
				bigClause.removeReceiver (cid);
			}
		}
	}
	
	public int getNoOfBlacklisted () {
		return blacklisted == null ? 0 : blacklisted.size ();
	}

	static class Layout implements ResultSetExtractor <Object> {
		private List <Column>	layout;
		private String		ref;

		public Layout (List <Column> nLayout, String nRef) {
			layout = nLayout;
			ref = nRef;
		}
		public List <Column> getLayout () {
			return layout;
		}
		@Override
		public Object extractData (ResultSet rset) throws SQLException, org.springframework.dao.DataAccessException {
			ResultSetMetaData	meta = rset.getMetaData ();
			int			ccnt = meta.getColumnCount ();

			for (int n = 0; n < ccnt; ++n) {
				String	cname = meta.getColumnName (n + 1);
				int	ctype = meta.getColumnType (n + 1);

				if (ctype == -1) {
					String	tname = meta.getColumnTypeName (n + 1);

					if (tname != null) {
						tname = tname.toLowerCase ();
						if (tname.equals ("varchar")) {
							ctype = Types.VARCHAR;
						}
					}
				}
				if (Column.typeStr (ctype) != null) {
					Column	c = new Column (cname, ctype);

					c.setRef (ref);
					if (layout == null) {
						layout = new ArrayList <> ();
					}
					layout.add (c);
				}
			}
			return this;
		}
	}
	private void getTableLayout (String table, String ref) throws Exception {
		DBase.Retry <List <Column>>	r = dbase.new Retry <List <Column>> ("layout", dbase, dbase.jdbc ()) {
			@Override
			public void execute () throws SQLException {
				String	query = "SELECT * FROM " + table + " WHERE 1 = 0";
				Layout	temp = new Layout (layout, ref);
				
				jdbc.query (query, temp);
				priv = temp.getLayout ();
			}
		};
		if (dbase.retry (r)) {
			layout = r.priv;
			if (layout != null) {
				lcount = layout.size ();
				lusecount = lcount;
			}
			return;
		}
		throw r.error;
	}

	public Locale getLocale (String language, String country) {
		if (language == null) {
			language = company.info ("locale-language", mailing.id ());
		}
		if (country == null) {
			country = company.info ("locale-country", mailing.id ());
		}
		if (language == null) {
			return null;
		}
		if (country == null) {
			return new Locale (language);
		}
		return new Locale (language, country);
	}
	
	public TimeZone getTimeZone (String timezone) {
		if (timezone == null) {
			timezone = company.info ("locale-timezone", mailing.id ());
		}
		if (timezone == null) {
			return null;
		}
		return TimeZone.getTimeZone (timezone);
	}

	public Title getTitle (Long tid) throws SQLException {
		if (titles == null) {
			titles = (new TitleDAO (dbase, company.id ())).titles ();
		}
		return titles.get (tid);
	}

	public TagDAO.Entry getTag (String tagName) throws SQLException {
		setupTagDao ();
		return tagDao.get (tagName);
	}
	public TagDAO.Function getTagFunction (String functionName) throws SQLException {
		setupTagDao ();
		return tagDao.getFunction (dbase, functionName);
	}
	private void setupTagDao () throws SQLException {
		if (tagDao == null) {
			tagDao = new TagDAO (dbase, company.id ());
		}
	}
	/**
	 * Fill already sent recipient in seen hashset for
	 * recovery prupose
	 * @param seen the hashset to fill with seen customerIDs
	 */
	public void prefillRecipients (Set <Long>  seen) throws IOException {
		if (maildropStatus.isWorldMailing () || maildropStatus.isRuleMailing () || maildropStatus.isOnDemandMailing ()) {
			File	recovery = new File (mailing.outputDirectory ("meta"), "recover-" + maildropStatus.id () + ".list");

			if (recovery.exists ()) {
				logging (Log.INFO, "recover", "Found recovery file " + recovery.getAbsolutePath ());
				markToRemove (recovery.getAbsolutePath ());
				try (FileInputStream in = new FileInputStream (recovery)) {
					byte[]		content = new byte[(int) recovery.length ()];
					in.read (content);
					String[]	data = (new String (content, "US-ASCII")).split ("\n");
					for (String line : data) {
						if (line.length () > 0) {
							seen.add (Long.decode (line));
						}
					}
				}
			}
		}
	}

	/** modify sending timestamp for next block
	 * @param timestamp current calculated send timestamp in seconds
	 * @return new timestamp
	 */
	public long modifyNextBlockTimestamp (long timestamp) {
		if (omgEnabled ()) {
			if (omgTimediff == null) {
				if (maildropStatus.optimizeForDay ()) {
					Calendar	temp = Calendar.getInstance ();
				
					temp.setTimeInMillis (sendSeconds * 1000);
					temp.set (Calendar.HOUR_OF_DAY, 0);
					temp.set (Calendar.MINUTE, 0);
					temp.set (Calendar.SECOND, 0);
					temp.set (Calendar.MILLISECOND, 0);
				
					omgTimediff = (temp.getTimeInMillis () / 1000 - sendSeconds) / 60;
					if (omgTimediff > 0) {
						omgTimediff = 0L;
					}
				} else {
					omgTimediff = 0L;
				}
			}
			long	ntimestamp = sendSeconds + (omgTimediff - omgOffset) * 60;

			if (ntimestamp != timestamp) {
				timestamp = ntimestamp;
			}
		}
		return timestamp;
	}

	/**
	 * Sanity check for mismatch company_id and perhaps deleted
	 * mailing
	 */
	public void sanityCheck () throws Exception {
		if (! maildropStatus.isPreviewMailing ()) {
			if (! mailing.exists ()) {
				throw new Exception ("No entry for mailingID " + mailing.id () + " in mailing table found");
			}
			if (mailing.companyID () != company.id ()) {
				throw new Exception ("Original companyID " + company.id () + " for mailing " + mailing.id () + " does not match current company_id " + mailing.companyID ());
			}
			if (mailing.deleted ()) {
				try {
					maildropStatus.setGenerationStatus (0, 4);
				} catch (Exception e) {
					logging (Log.ERROR, "sanity", "Failed to set generation status: " + e.toString (), e);
				}
				throw new Exception ("Mailing " + mailing.id () + " marked as deleted");
			}
		}
	}

	public Code findCode (String codeName) {
		Code	rc;
		
		try {
			rc = codes.get (codeName);
			if (rc == null) {
				rc = new Code (this, codeName);
				if (! rc.retrieveCode ())
					logging (Log.WARNING, "data", "Failed to retrieve code for block " + codeName);
				codes.put (codeName, rc);
			}
		} catch (SQLException e) {
			rc = null;
			logging (Log.ERROR, "data", "Failed to retrieve code for " + codeName + ": " + e.toString (), e);
		}
		return rc != null && rc.isValid () ? rc : null;
	}
	/**
	 * Executed at start of mail generation
	 */
	public void startExecution () throws Exception {
		bigClause = new BC (this);
		if (! bigClause.prepareQueryParts ()) {
			throw new Exception ("Failed to setup the query parts");
		}
		if (blacklisted != null) {
			blacklisted.clear ();
		}
		totalSubscribers = bigClause.subscriber ();
		totalReceivers = bigClause.receiver ();
		logging (Log.DEBUG, "start", "\ttotalSubscribers = " + totalSubscribers);
		logging (Log.DEBUG, "start", "\ttotalReceivers = " + totalReceivers);
		if (maildropStatus.isWorldMailing () || maildropStatus.isRuleMailing () || maildropStatus.isOnDemandMailing ()) {
			logging ("generate", "company=" + company.id () + "\tmailinglist=" + mailinglist.id () + "\tmailing=" + mailing.id () + "\tcount=" + totalReceivers + "\tstatus_field=" + maildropStatus.statusField ());
		}
	}

	/**
	 * Executed at end of mail generation
	 */
	public void endExecution () {
		if (bigClause != null) {
			bigClause.done ();
			bigClause = null;
		}
	}

	/**
	 * Change generation state for the current mailing
	 */
	public void updateGenerationState (int newstatus) {
		if (maildropStatus.isAdminMailing () ||
		    maildropStatus.isTestMailing () ||
		    maildropStatus.isWorldMailing () ||
		    maildropStatus.isRuleMailing () ||
		    maildropStatus.isOnDemandMailing ()) {
			try {
				if (newstatus == 0) {
					if (maildropStatus.isRuleMailing () || maildropStatus.isOnDemandMailing ())
						newstatus = 1;
					else
						newstatus = 3;
				}
				maildropStatus.setGenerationStatus (0, newstatus);
			} catch (Exception e) {
				logging (Log.ERROR, "genstate", "Unable to update generation state: " + e.toString (), e);
			}
		}
	}
	public void updateGenerationState () {
		updateGenerationState (0);
	}

	/**
	 * Called when main generation starts
	 */
	public List <String> generationClauses () {
		return bigClause.createClauses ();
	}
	
	public String getSelectExpression (boolean fullExpression) {
		return bigClause.createSelect (fullExpression);
	}

	/**
	 * Save receivers to mailtracking table
	 */
	@DaoUpdateReturnValueCheck
	public void toMailtrack () {
		if (company.mailtracking ()) {
//			long	chunks = limitBlockChunks ();		// not active due to performance issues
			long	chunks = 1;
			String	query = bigClause.mailtrackStatement (company.mailtrackingTable ());

			if (query != null) {
				for (long chunk = 0; chunk < chunks; ++chunk) {
					String	cquery = chunks == 1 ? query : query + " WHERE mod(customer_id, " + chunks + ") = " + chunk;
					boolean	success= false;
					int	retry = 3;

					while ((! success) && (retry-- > 0)) {
						try {
							synchronized (company.mailtrackingTable ()) {
								dbase.execute (cquery);
							}
							success= true;
						} catch (Exception e) {
							logging (Log.ERROR, "mailtrack", "Failed to write mailtracking using " + cquery + ": " + e.toString (), e);
						}
					}
				}
			}
		}
		if ((mailingType == Const.Mailing.TYPE_INTERVAL) && (! maildropStatus.isAdminMailing ()) && (! maildropStatus.isTestMailing ())) {
			String	intervalTrackTable = "interval_track_" + company.id () + "_tbl";
			String	query = bigClause.intervalStatement (intervalTrackTable);
			
			try {
				dbase.update (query, "mailingID", mailing.id (), "sendDate", (maildropStatus.sendDate () == null ? new Date () : maildropStatus.sendDate ()));
			} catch (Exception e) {
				logging (Log.ERROR, "mailtrack", "Failed to save interval mailing information: " + e.toString (), e);
			}
		}
	}
	
	private void typeerr (Object o, String what) throws Exception {
		throw new Exception (what + ": unknown data type:  " + o.getClass ().toString ());
	}
		

	/**
	 * Convert a given object to an integer
	 * @param o the input object
	 * @param what for logging purpose
	 * @return the converted value
	 */
	private int obj2int (Object o, String what) throws Exception {
		int	rc = 0;

		if (o.getClass () == Integer.class)
			rc = ((Integer) o).intValue ();
		else if (o.getClass () == Long.class)
			rc = ((Long) o).intValue ();
		else if (o.getClass () == String.class)
			rc = Integer.parseInt ((String) o);
		else
			typeerr (o, what);
		return rc;
	}

	/**
	 * Convert a given object to a long
	 * @param o the input object
	 * @param what for logging purpose
	 * @return the converted value
	 */
	private long obj2long (Object o, String what) throws Exception {
		long	rc = 0;

		if (o.getClass () == Integer.class)
			rc = ((Integer) o).longValue ();
		else if (o.getClass () == Long.class)
			rc = ((Long) o).longValue ();
		else if (o.getClass () == String.class)
			rc = Long.parseLong ((String) o);
		else
			typeerr (o, what);
		return rc;
	}

	/**
	 * Convert a given object to a boolean
	 * @param o the input object
	 * @param what for logging purpose
	 * @return the converted value
	 */
	private boolean obj2bool (Object o, String what) throws Exception {
		boolean rc = false;

		if (o.getClass () == Boolean.class)
			rc = ((Boolean) o).booleanValue ();
		else if (o.getClass () == String.class)
			rc = Boolean.parseBoolean ((String) o);
		else
			typeerr (o, what);
		return rc;
	}

	/**
	 * Convert a given object to a date
	 * @param o the input object
	 * @param what for logging purpose
	 * @return the converted value
	 */
	private Date obj2date (Object o, String what) throws Exception {
		Date	rc = null;

		if (o.getClass () == Date.class)
			rc = (Date) o;
		else
			typeerr (o, what);
		return rc;
	}
	
	/**
	 * Convert a given object to an array
	 * @param o the input object
	 * @return the extracted list
	 */
	@SuppressWarnings ({ "unchecked", "rawtypes" })
	private List <Object> obj2list (Object o) {
		for (Class <List> cls : (Class<List>[]) o.getClass ().getInterfaces ()) {
			if (cls == List.class) {
				return ((List <Object>) o);
			}
		}
		ArrayList <Object>	rc = new ArrayList<>(1);
		
		rc.add (o);
		return rc;
	}
	private long[] obj2longarray (Object o, String what) throws Exception {
		List <Object>	in = obj2list (o);
		long[]		rc = new long[in.size ()];
		int		idx = 0;		

		for (Object e : in) {
			rc[idx++] = obj2long (e, what);
		}
		return rc;
	}

	/**
	 * Parse options passed during runtime
	 * @param opts the options to use
	 * @param state if 1, the before initialization pass, 2 on execution pass
	 */
	@SuppressWarnings ("unchecked")
	public void options (Map <String, Object> opts, int state) throws Exception {
		Object tmp;

		if (opts == null) {
			return;
		}
		if (state == 1) {
			if ((previewInput = (String) opts.get ("preview-input")) != null) {
				logging (Log.DEBUG, "options1", "--> preview-input = " + previewInput);
			}
			if ((tmp = opts.get ("preview-create-all")) != null) {
				previewCreateAll = obj2bool (tmp, "preview-create-all");
				logging (Log.DEBUG, "options1", "--> preview-create-all = " + previewCreateAll);
			}
			if ((tmp = opts.get ("preview-cache-images")) != null) {
				previewCacheImages = obj2bool (tmp, "preview-cache-images");
				logging (Log.DEBUG, "options1", "--> preview-cache-images = " + previewCacheImages);
			}
		} else if (state == 2) {
			if ((tmp = opts.get ("customer-id")) != null) {
				campaignCustomerID = obj2long (tmp, "customer-id");
				logging (Log.DEBUG, "options2", "--> customer-id = " + campaignCustomerID);
			}
			if ((tmp = opts.get ("user-status")) != null) {
				campaignUserStatus = obj2longarray (tmp, "user-status");
				logging (Log.DEBUG, "options2", "--> user-status = " + campaignUserStatus);
			}
			if ((tmp = opts.get ("force-sending")) != null) {
				campaignForceSending = obj2bool (tmp, "force-sending");
				logging (Log.DEBUG, "options2", "--> force-sending = " + campaignForceSending);
			}
			if ((providerEmail = (String) opts.get ("provider-email")) != null) {
				logging (Log.DEBUG, "options2", "--> provider-email = " + providerEmail);
			}
			if ((tmp = opts.get ("preview-for")) != null) {
				previewCustomerID = obj2long (tmp, "preview-for");
				logging (Log.DEBUG, "options2", "--> preview-for = " + previewCustomerID);
			}
			if ((previewOutput = (Page) opts.get ("preview-output")) != null) {
				logging (Log.DEBUG, "options2", "--> preview-output = " + previewOutput);
			}
			if ((tmp = opts.get ("preview-anon")) != null) {
				previewAnon = obj2bool (tmp, "preview-anon");
				logging (Log.DEBUG, "options2", "--> preview-anon = " + previewAnon);
			}
			if ((previewSelector = (String) opts.get ("preview-selector")) != null) {
				logging (Log.DEBUG, "options2", "--> preview-selector= " + previewSelector);
			}
			if ((tmp = opts.get ("preview-cachable")) != null) {
				previewCachable = obj2bool (tmp, "preview-cachable");
				logging (Log.DEBUG, "options2", "--> preview-cachable = " + previewCachable);
			}
			if ((tmp = opts.get ("preview-convert-entities")) != null) {
				previewConvertEntities = obj2bool (tmp, "preview-convert-entities");
				logging (Log.DEBUG, "options2", "--> preview-convert-entities = " + previewConvertEntities);
			}
			if ((tmp = opts.get ("preview-ecs-uids")) != null) {
				previewEcsUIDs = obj2bool (tmp, "preview-ecs-uids");
				logging (Log.DEBUG, "options2", "--> preview-ecs-uids = " + previewEcsUIDs);
			}
			if ((tmp = opts.get ("send-date")) != null) {
				currentSendDate = obj2date (tmp, "send-date");
				sendSeconds = currentSendDate.getTime () / 1000;

				long	now = System.currentTimeMillis () / 1000;
				if (sendSeconds < now) {
					sendSeconds = now;
				}
				logging (Log.DEBUG, "options2", "--> send-date = " + currentSendDate);
			}

			if ((tmp = opts.get ("step")) != null) {
				mailing.stepping (obj2int (tmp, "step"));
				logging (Log.DEBUG, "options2", "--> step = " + tmp);
			}
			if ((tmp = opts.get ("block-size")) != null) {
				mailing.blockSize (obj2int (tmp, "block-size"));
				logging (Log.DEBUG, "options2", "--> block-size = " + tmp);
			}
			if ((tmp = opts.get ("bcc")) != null) {
				bcc = (List <String>) tmp;
				if (bcc.size () == 0) {
					bcc = null;
				} else {
					for (String email : bcc) {
						logging (Log.DEBUG, "options2", "--> = " + email);
					}
				}
			}
			if ((overwriteMap = mormalizeReplacementMap ((Map <String, String>) opts.get ("overwrite"))) != null) {
				logging (Log.DEBUG, "options2", "--> overwrite = " + overwriteMap);
			}
			if ((virtualMap = mormalizeReplacementMap ((Map <String, String>) opts.get ("virtual"))) != null) {
				logging (Log.DEBUG, "options2", "--> virtual = " + virtualMap);
			}
			if ((staticMap = (Map <String, Object>) opts.get ("static")) != null) {
				logging (Log.DEBUG, "options2", "--> static = " + staticMap);
			}
			
			String	targetInformation;
			if ((staticMap != null) && ((targetInformation = (String) staticMap.get ("_tg")) != null)) {
				targetExpression.setEvaluatedValues (targetInformation);
			} else {
				targetExpression.clearEvaluatedValues ();
			}
		}
	}
	
	private Map <String, String> mormalizeReplacementMap (Map <String, String> source) {
		if (source == null) {
			return null;
		}
		
		Map <String, String>	result = new HashMap <> (source.size ());
		
		for (Map.Entry <String, String> entry : source.entrySet ()) {
			result.put (entry.getKey ().toLowerCase (), entry.getValue ());
		}
		return result;
	}
	
	public boolean useMultipleRecords () {
		if (references != null) {
			for (Reference r : references.values ()) {
				if (r.isMulti ()) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Should we use this record, according to our virtual data?
	 * @return true if we should
	 */
	public boolean useRecord (Long cid) {
		return true;
	}

	/**
	 * Optional initialization for virtual data
	 * @param column the column to initialize
	 */
	public void initializeVirtualData (String column) {
		// nothing to do
	}

	/**
	 * Do we have data available to overwrite columns?
	 * @return true in this case
	 */
	public boolean overwriteData () {
		return overwriteMap != null;
	}

	/**
	 * Find entry in map for overwrite/virtual records
	 * @param map optional simple hash table
	 * @param colname the name of the column
	 * @return the found string or null
	 */
	private String findInMap (Map <String, String> map, String colname) {
		if ((map != null) && map.containsKey (colname))
			return map.get (colname);
		return null;
	}

	/**
	 * Find an overwrite column
	 * @param colname the name of the column
	 * @return the found string or null
	 */
	public String overwriteData (String colname) {
		return findInMap (overwriteMap, colname);
	}

	/**
	 * Find a virtual column
	 * @param colname the name of the column
	 * @return the found string or null
	 */
	public String virtualData (String colname) {
		return findInMap (virtualMap, colname);
	}

	public String getBindingTable () {
		return bigClause.getBindingTable ();
	}
	
	public String getBindingQuery () {
		return bigClause.getBindingQuery ();
	}

	public String createSimpleClause (String reduction) {
		return bigClause.createSimpleClause ("cust", reduction);
	}
	public String createSimpleClause () {
		return createSimpleClause (null);
	}
	
	/** If we have further restrictions due to reference mailing
	 * @return extra subsulect or null
	 */
	public String getReferenceSubselect () {
		return followupRefSQL;
	}

	/** If we have further restrictions due to selected media
	 * @return extra subsulect or null
	 */
	public String getMediaSubselect () {
		return mediaRestrictSQL;
	}

	/** Returns a default image link for a generic picture
	 * @param name   the image name
	 * @param source the source to get the image from
	 * @param use    marks if this request comes for an actual usage of the image
	 * @return       the created link
	 */
	public String defaultImageLink (String name, String source, boolean use) {
		boolean	nocache = maildropStatus.isPreviewMailing () && (! previewCacheImages);
		Image	image = null;
		String	template = null;
		
		try {
			name = URLEncoder.encode (name, "UTF-8");
		} catch (java.io.UnsupportedEncodingException e) {
			// should never happen
		}
		setupImagepool ();
		if (source == null) {
			source = imagepool.findSourceFor (name);
		} else {
			source = source.toLowerCase ();
		}
		switch (source) {
		default:
			break;
		case Imagepool.MAILING:
			template = nocache ? imageTemplateNoCache : imageTemplate;
			image = new Image (0, name, name, null);
			break;
		case Imagepool.MEDIAPOOL:
			template = nocache ? mediapoolTemplateNoCache : mediapoolTemplate;
			image = imagepool.findMediapoolImage (name);
			break;
		case Imagepool.MEDIAPOOL_BACKGROUND:
			template = nocache ? mediapoolBackgroundTemplateNoCache : mediapoolBackgroundTemplate;
			image = imagepool.findMediapoolBackground (name);
			break;
		}
		if ((image != null) && (template != null)) {
			String	link = template.replace ("[name]", image.filename ());

			if (use) {
				imagepool.markInUse (name, source, image, link);
			}
			return link;
		}
		return name;	
	}
	
	public List <BlockData> getUsedMediapoolImages () throws SQLException {
		setupImagepool ();
		return imagepool.getMediapoolImagesUsed();
	}
	
	public Set <String> getUsedComponentImages () {
		setupImagepool ();
		return imagepool.getComponentsUsed ();
	}

	/**
	 * Mark a filename to be removed during cleanup phase
	 * @param fname the filename
	 */
	public void markToRemove (String fname) {
		if (toRemove == null)
			toRemove = new ArrayList <> ();
		if (! toRemove.contains (fname))
			toRemove.add (fname);
	}

	/**
	 * Mark a file to be removed during cleanup
	 * @param file a File instance for the file to be removed
	 */
	public void markToRemove (File file) {
		markToRemove (file.getAbsolutePath ());
	}

	/**
	 * Unmark a filename to be removed, if we already removed
	 * it by hand
	 * @param fname the filename
	 */
	public void unmarkToRemove (String fname) {
		if ((toRemove != null) && toRemove.contains (fname))
			toRemove.remove (fname);
	}

	/**
	 * Unmark a file to be removed
	 * @param file a File instance
	 */
	public void unmarkToRemove (File file) {
		unmarkToRemove (file.getAbsolutePath ());
	}

	/**
	 * Check if we have to write logging for a given loglevel
	 * @param loglvl the loglevel to check against
	 * @return true if we should log
	 */
	public boolean islog (int loglvl) {
		return log.islog (loglvl);
	}

	/**
	 * Write entry to logfile
	 * @param loglvl the level to report
	 * @param mid the ID of the message
	 * @param msg the message itself
	 */
	public void logging (int loglvl, String mid, String msg, Throwable th) {
		if (log != null) {
			if (lid != null) {
				if (mid != null) {
					mid = mid + "/" + lid;
				} else {
					mid = lid;
				}
			}
			if (msg != null) {
				log.out (loglvl, mid, msg);
			}
		
			Set <Throwable> seen = new HashSet<>();
			while (th != null) {
				StackTraceElement[] elements = th.getStackTrace ();

				if (elements != null) {
					log.out (loglvl, mid, "Stacktrace for " + th.toString () + ":");
					for (StackTraceElement element : elements) {
						log.out (loglvl, mid, " at " + element.toString ());
					}
				}
				seen.add (th);
				th = th.getCause ();
				if ((th != null) && seen.contains (th)) {
					log.out (loglvl, mid, " ... recursive stack trace detected, aborting");
				}
			}
		}
	}
	public void logging (int loglvl, String mid, String msg) {
		logging (loglvl, mid, msg, null);
	}
	public void logging (String name, String msg) {
		if (log != null) {
			log.out (name, msg);
		}
	}

	/**
	 * Returns currently active logger
	 * @return active logger instance
	 */
	public Log getLogger () {
		return log;
	}

	public boolean shouldRemoveDuplicateEMails () {
		return removeDuplicateEMails && (! maildropStatus.isAdminMailing ()) && (! maildropStatus.isTestMailing ()) && (! maildropStatus.isPreviewMailing ()) && (! maildropStatus.isVerificationMailing ());
	}
	
	public String mfromDatabase () {
		return mfromDatabasePath;
	}
	
	public String tempTablespace () {
		return dbTempTablespace;
	}

	public int licenceID () {
		return licenceID;
	}
	
	private void iniValidate (String value, String name) {
		if (value == null) {
			logging (Log.ERROR, "config", "mailout.ini." + name + " is unset");
		}
	}
	public String dbDriver () {
		iniValidate (dbDriver, "db_driver");
		return dbDriver;
	}

	/** returns the database login
	 * @return login string
	 */
	public String dbLogin () {
		iniValidate (dbLogin, "db_login");
		return dbLogin;
	}

	/** returns the database password
	 * @return password string
	 */
	public String dbPassword () {
		iniValidate (dbPassword, "db_password");
		return dbPassword;
	}

	/** returns the connection string for the database
	 * @return connection string
	 */
	public String dbConnect () {
		iniValidate (dbConnect, "db_connect");
		return dbConnect;
	}

	public int dbPoolsize () {
		return dbPoolsize;
	}

	public boolean dbPoolgrow () {
		return dbPoolgrow;
	}
	
	public boolean isDirect () {
		return directPath && 
			(mailing.outputDirectory ("direct") != null) &&
			maildropStatus.isCampaignMailing () &&
			(mailing.stepping () == 0) &&
			(sendSeconds <= System.currentTimeMillis () / 1000);
	}

	/** returns the directory to write meta files to
	 * @return path to meta
	 */
	public String targetPath () throws Exception {
		if (isDirect ()) {
			return mailing.outputDirectory ("direct");
		}
		return mailing.outputDirectory ("meta");
	}

	/** returns the path to xmlback program
	 * @return path to xmlback
	 */
	public String xmlBack () {
		return xmlBack;
	}

	/** returns wether we should validate generated XML files
	 * @return true if validation should take place
	 */
	public boolean xmlValidate () {
		return xmlValidate;
	}

	/** returns the optional used sample receivers
	 * @return receiver list
	 */
	public String sampleEmails () {
		return sampleEmails;
	}

	/** returns the number of generate mails to write log entries for
	 * @return number of mails
	 */
	public int mailLogNumber () {
		return mailLogNumber;
	}

	/** if this is a dryrun test run
	 * @return true, if it is a dryrun
	 */
	public boolean isDryRun () {
		if (maildropStatus.isTestMailing () && (workStatus != null) && workStatus.equals (Const.Workstatus.MAILING_STATUS_TEST) && isWorkflowMailing) {
			return true;
		}
		return false;
	}

	public String getDefaultMediaType () {
		return maildropStatus.isCampaignMailing () ? Media.typeName (Media.TYPE_EMAIL) : null;
	}
	
	public long limitBlockOperations () {
		return limitBlockOperations;
	}
	public long limitBlockOperationsMax () {
		return limitBlockOperationsMax;
	}
	public long limitBlockChunks () {
		if (maildropStatus.isWorldMailing () || maildropStatus.isRuleMailing () || maildropStatus.isOnDemandMailing ()) {
			long	limit = limitBlockOperations ();
			long	limitMax = limitBlockOperationsMax ();
			long	chunks = limit > 0 ? totalReceivers /  limit + 1 : 1;

			return (limitMax > 0 && chunks > limitMax) ? limitMax : chunks;
		}
		return 1;
	}

	/**
	 * Set standard field to be retrieved from database
	 * @param predef the hashset to store field name to
	 */
	public void setStandardFields (Set <String> predef, Map <String, EMMTag> tags) {
		for (Media m : media) {
			predef.add (m.profileField ());
		}
		predef.add ("sys_tracking_veto");
		targetExpression.requestFields (predef);
		for (EMMTag tag : tags.values ()) {
			try {
				tag.requestFields (this, predef);
			} catch (Exception ex) {
				logging (Log.ERROR, "tag", "Failed to get required fields for tag " + tag.mTagFullname + ": " + ex.toString (), ex);
			}
		}
		String	custom = company.info ("preselect-columns", mailing.id ());
		
		if (custom != null) {
			for (String c : custom.split (", *")) {
				if (c.length () > 0) {
					predef.add (c.toLowerCase ());
				}
			}
		}

		if (dkimActive) {
			String	dkimColumn = company.info ("dkim-select-column");
			if ((dkimColumn != null) && (columnByName (dkimColumn) != null)) {
				company.infoAdd ("_dkim_column", dkimColumn);
				predef.add (dkimColumn);
			}
		}

		extendURL.requestFields (this, predef);

		if (references != null) {
			for (Reference r : references.values ()) {
				Column	c = columnByName (r.name (), Reference.multiID);
			
				if (c != null) {
					predef.add (c.getQname ());
					r.multi ();
				}
			}
		}
		if (URLlist != null) {
			for (URL url : URLlist) {
				StringOps.findColumnsInHashtags (url.getUrl ())
					.stream ()
					.forEach ((e) -> {
						Column	c = columnByName (e);
						
						if (c != null) {
							url.addColumn (c);
						}
					});
				url.requestFields (this, predef);
			}
		}
		predef.stream ()
			.map ((s) -> s.startsWith ("cust.") ? s.substring (5) : s)
			.filter ((s) -> columnByName (s) == null)
			.forEach ((s) -> logging (Log.WARNING, "fields", "Unable to resolve \"" + s + "\""));
	}

	/**
	 * Set standard columns, if they are not already found in database
	 * @param use already used column names
	 */
	public void setUsedFieldsInLayout (Set <String> use, Map <String, EMMTag> tags) {
		int		sanity = 0;
		Set <String>	predef;

		if (use != null) {
			predef = new HashSet<>(use);
		} else {
			predef = new HashSet<>();
		}
		setStandardFields (predef, tags);
		for (Column c : layout) {
			if (predef.contains (c.getQname ())) {
				if (! c.getInuse ()) {
					c.setInuse (true);
					++lusecount;
				}
				++sanity;
			} else {
				if (c.getInuse ()) {
					c.setInuse (false);
					--lusecount;
				}
			}
		}
		if (sanity != lusecount) {
			logging (Log.ERROR, "layout", "Sanity check failed in setUsedFieldsInLayout");
		}
	}

	/** find a column by its alias
	 * @param alias
	 * @return the column on success, null otherwise
	 */
	public Column columnByAlias (String alias) {
		for (Column c : layout) {
			String	calias = c.getAlias ();
			
			if ((calias != null) && calias.equalsIgnoreCase (alias)) {
				return c;
			}
		}
		return null;
	}

	/** find a column by its name
	 * @param name
	 * @return the column on success, null otherwise
	 */
	public Column columnByName (String ref, String name) {
		int	n;

		if ((ref == null) && ((n = name.indexOf ('.')) != -1)) {
			if (references == null) {
				return null;
			}
			ref = name.substring (0, n);
			name = name.substring (n + 1);
			
			Reference	r = references.get (ref);
			
			if ((r == null) || (! fullfillReference (r))) {
				return null;
			}
		}
		for (Column c : layout) {
			if (c.match (ref, name)) {
				return c;
			}
		}
		return null;
	}
	public Column columnByName (String name) {
		return columnByName (null, name);
	}
	public Column columnByIndex (int idx) {
		return (idx >= 0) && (idx < lcount) ? layout.get (idx) : null;
	}

	/** return the name of the column at a given position
	 * @param col the position in the column layout
	 * @return the column name
	 */
	public String columnName (int col) {
		return layout.get (col).getName ();
	}

	/** return the type as string of the column at a given position
	 * @param col the position in the column layout
	 * @return the column type as string
	 */
	public String columnTypeStr (int col) {
		return layout.get (col).typeStr ();
	}

	/** Set a column from a result set
	 * @param col the position in the column layout
	 * @param rset the result set
	 * @param index position in the result set
	 */
	public void columnSet (int col, ResultSet rset, int index) {
		layout.get (col).set (rset, index);
	}

	/** Get a value from a column
	 * @param col the position in the column layout
	 * @return the contents of that column
	 */
	public String columnGetStr (int col) {
		return layout.get (col).get ();
	}

	/** Check wether a columns value is NULL
	 * @param col the position in the column layout
	 * @return true of column value is NULL
	 */
	public boolean columnIsNull (int col) {
		return layout.get (col).getIsnull ();
	}

	/** Check wether a column is in use
	 * @param col the position in the column layout
	 * @return true if column is in use
	 */
	public boolean columnUse (int col) {
		return layout.get (col).getInuse ();
	}
	
	public void columnUpdateQueryIndex (Column c, int index) {
		String		ref = c.getRef ();
		Reference	r;

		if ((ref != null) && ((r = references.get (ref)) != null)) {
			r.setIdIndex (c.getName (), index);
		}
	}

	/** create a RFC compatible Date: line
	 * @param ts the input time
	 * @return the RFC representation
	 */
	public String RFCDate (Date ts) {
		SimpleDateFormat	fmt = new SimpleDateFormat ("EEE, d MMM yyyy HH:mm:ss z",
									new Locale ("en"));
		fmt.setTimeZone (TimeZone.getTimeZone ("GMT"));
		if (ts == null)
			ts = new Date ();
		return fmt.format (ts);
	}

	/** Optional string to add to filename generation
	 * @return optional string
	 */
	public String getFilenameDetail () {
		return "-" + licenceID;
	}

	public String getFilenameCompanyID () {
		return Long.toString (company.id ());
	}

	public String getFilenameMailingID () {
		if (maildropStatus.isOnDemandMailing ()) {
			if (inc == 0) {
				inc = getIncarnation ();
			}
			return Long.toString (inc) + "D" + Long.toString (mailing.id ());
		}
		return Long.toString (mailing.id ());
	}

	public String ahvTable () {
		return  "ahv_" + company.id () + "_tbl";
	}
	
	private Boolean ahvEnabled = null;
	private Date	ahvOldest = null;
	public boolean ahvEnabled () {
		if (ahvEnabled == null) {
			try {
				if ((maildropStatus.isCampaignMailing () ||
				     maildropStatus.isRuleMailing () ||
				     maildropStatus.isOnDemandMailing () ||
				     maildropStatus.isWorldMailing ()) &&
				    StringOps.atob (company.info ("ahv:is-enabled", mailing.id ()), company.allowed ("recipient.bounce.ahv")) &&
				    dbase.tableExists (ahvTable ())) {
					ahvEnabled = true;
					
					long	maxAge = StringOps.atol (company.info ("ahv:max-age"), 180);
					
					if (maxAge > 0) {
						long	ref = (currentSendDate != null ? currentSendDate : new Date ()).getTime ();
						
						ahvOldest = new Date (ref - maxAge * 24 * 60 * 60 * 1000);
						logging (Log.DEBUG, "ahv", "Oldest entries to reactivate: " + ahvOldest.toString () + " due to maximum age of " + maxAge + " days");
					}
				}
			} catch (Exception e) {
				logging (Log.ERROR, "ahv", "Failed in setup AHV: " + e.toString (), e);
			}
			if (ahvEnabled == null) {
				ahvEnabled = false;
			}
		}
		return ahvEnabled;
	}
	public Date ahvOldestEntryToReactivate () {
		return ahvOldest;
	}
	
	public String omgTable () {
		return "omg_" + company.id () + "_tbl";
	}
	private Boolean omgEnabled = null;
	private Long	omgTimediff = null;
	private int	omgOffset = 15;
	public boolean omgEnabled () {
		if (omgEnabled == null) {
			omgEnabled = false;
			try {
				if ((maildropStatus.isRuleMailing () || maildropStatus.isWorldMailing ()) &&
				    (maildropStatus.optimizeMailGeneration () != null) &&
				    StringOps.atob (company.info ("omg:is-enabled", mailing.id ()), company.allowed ("mailing.send.optimizedMailGeneration")) &&
				    dbase.tableExists (omgTable ())) {
					omgEnabled = true;
					omgOffset = StringOps.atoi (company.info ("omg:offset", mailing.id ()), omgOffset);
				}
			} catch (Exception e) {
				logging (Log.ERROR, "omg", "Failed to setup omg: " + e.toString (), e);
			}
		}
		return omgEnabled;
	}

	public void getControlColumns (StringBuffer selectString) {
		if (omgEnabled ()) {
			selectString.append (", bind.omg");
		}
		for (Target t : targetExpression.resolveByDatabase ()) {
			if (t.hasEvaluatedValue ()) {
				selectString.append (", " + (t.evaluatedValue () ? "1" : "0") + " AS agn_tg_" + t.getID ());
			} else {
				selectString.append (", agn_tg_" + t.getID () + ".value AS agn_tg_" + t.getID ());
			}
		}
	}
	
	public int useControlColumns (Custinfo cinfo, ResultSet rset, int startIndex) throws Exception {
		if (omgEnabled ()) {
			long		timediff = rset.getLong (startIndex++);
			String		usertype = cinfo.getUserType ();

			if (((omgTimediff == null) || (timediff > omgTimediff)) && (usertype != null) && usertype.equals ("W")) {
				forceNewBlock = true;
				omgTimediff = timediff;
			}
		}
		return startIndex;
	}
	
	public Date sendDate () {
		return maildropStatus.genericSendDate ();
	}
	
	public List <String> getReduction () {
		List <String>	rc = new ArrayList <> ();
		
		if (maildropStatus.isAdminMailing () || maildropStatus.isTestMailing ()) {
			String	sql = maildropStatus.getAdminTestSQL ();
			
			if (sql != null) {
				rc.add (sql);
			}
		}
		if (maildropStatus.isWorldMailing () || maildropStatus.isRuleMailing () || maildropStatus.isOnDemandMailing ()) {
			String	expr = company.infoSubstituted ("fixed-target-clause", mailing.id ());
			
			if ((expr != null) && (! expr.equals ("-"))) {
				rc.add (expr);
			}
		}
		if (maildropStatus.isWorldMailing ()) {
			String	column = company.infoSubstituted ("column-restrict-sending-per-day", mailing.id ());
			
			if ((column != null) && (! column.equals ("-"))) {
				if (column.indexOf ('.') == -1) {
					column = "cust." + column;
				}

				StringBuffer	buf = new StringBuffer ();
				Calendar	now = Calendar.getInstance ();
				int		wday = now.get (Calendar.DAY_OF_WEEK);
	
				buf.append (column + " IS NULL OR " + column + " LIKE '");
				for (int day = 0; day < 7; ++day) {
					int	cmp = -1;
		
					switch (day) {
					case 0:	cmp = Calendar.MONDAY;		break;
					case 1:	cmp = Calendar.TUESDAY;		break;
					case 2:	cmp = Calendar.WEDNESDAY;	break;
					case 3:	cmp = Calendar.THURSDAY;	break;
					case 4:	cmp = Calendar.FRIDAY;		break;
					case 5:	cmp = Calendar.SATURDAY;	break;
					case 6:	cmp = Calendar.SUNDAY;		break;
					}
					buf.append (cmp == wday ? '1' : '_');
				}
				buf.append ("'");
				rc.add (buf.toString ());
			}
		}
		return rc;
	}

	public void setBlocks (BlockCollection bc) {
		blocks = bc;
	}
	
	public BlockCollection getBlocks () {
		return blocks;
	}

	public void setupImagepool () {
		if (imagepool == null) {
			imagepool = new Imagepool (this);
			if (blocks != null) {
				for (int n = 0; n < blocks.blockCount (); ++n) {
					imagepool.addImage (blocks.getBlock (n));
				}
			}
		}
	}
	
	public BlockData requestImage (String imageName, byte[] imageDump) {
		BlockData	rc = null;

		if (blocks != null) {
			setupImagepool ();
			if (blockTable == null) {
				blockTable = new HashMap <> ();

				for (int n = 0; n < blocks.blockCount (); ++n) {
					BlockData	b = blocks.getBlock (n);
					
					if (b.type == BlockData.RELATED_BINARY) {
						blockTable.put (b.cid, b);
					}
				}
			}
			rc = blockTable.get (imageName);
			if (rc == null) {
				try {
					rc = blocks.newImage (imageName, imageDump);
					if (rc != null) {
						blockTable.put (imageName, rc);
						imagepool.addImage (rc);
					}
				} catch (Exception e) {
					logging (Log.ERROR, "rqimg", "Failed to create new image: " + e.toString (), e);
				}
			}
		}
		return rc;
	}

	private static Object	lock = new Object ();
	
	@DaoUpdateReturnValueCheck
	public URL requestURL (String rqurl, String name, boolean isAdminLink) {
		URL	url;

		if (URLTable == null) {
			URLTable = new HashMap <> ();
			
			for (int n = 0; n < urlcount; ++n) {
				url = URLlist.get (n);
				URLTable.put (url.getUrl (), url);
			}
		}
		url = URLTable.get (rqurl);
		if (url == null) synchronized (lock) {
			NamedParameterJdbcTemplate	jdbc = null;
			String				query = null;
			List <Map <String, Object>>	rq;
			Map <String, Object>		row;
			
			try {
				long	urlID;
				
				urlID = 0;
				jdbc = dbase.request ();
				query = "SELECT url_id FROM rdir_url_tbl WHERE mailing_id = :mailingID AND company_id = :companyID AND full_url = :fullURL AND (deleted IS NULL OR deleted = 0)";
				rq = dbase.query (jdbc, query, "mailingID", mailing.id (), "companyID", company.id (), "fullURL", rqurl);
				if (rq.size () > 0) {
					row = rq.get (0);
					
					urlID = dbase.asLong (row.get ("url_id"));
				}
				if (urlID == 0) {
					if (dbase.isOracle ()) {
						query = "SELECT rdir_url_tbl_seq.nextval FROM dual";
						urlID = dbase.queryLong (jdbc, query);
						if (urlID > 0) {
							query = "INSERT INTO rdir_url_tbl (url_id, full_url, mailing_id, company_id, " + dbase.measureType +", action_id, shortname, deep_tracking, relevance, alt_text, extend_url, admin_link) " +
								"VALUES (:urlID, :fullURL, :mailingID, :companyID, :measure, :actionID, :shortname, :deepTracking, :relevance, :altText, :extendURL, :adminLink)";
							dbase.update (jdbc, query,
								      "urlID", urlID,
								      "fullURL", rqurl,
								      "mailingID", mailing.id (),
								      "companyID", company.id (),
								      "measure", 3,
								      "actionID", 0,
								      "shortname", (name == null ? "auto generated" : name),
								      "deepTracking", 0,
								      "relevance", 0,
								      "altText", null,
								      "extendURL", 0,
								      "adminLink", (isAdminLink ? 1 : 0));
						}
					} else {
						query = "INSERT INTO rdir_url_tbl (full_url, mailing_id, company_id, " + dbase.measureType +", action_id, shortname, deep_tracking, relevance, alt_text, extend_url, admin_link) " +
							"VALUES (:fullURL, :mailingID, :companyID, :measure, :actionID, :shortname, :deepTracking, :relevance, :altText, :extendURL, :adminLink)";
						dbase.update (jdbc, query,
							      "urlID", urlID,
							      "fullURL", rqurl,
							      "mailingID", mailing.id (),
							      "companyID", company.id (),
							      "measure", 3,
							      "actionID", 0,
							      "shortname", (name == null ? "auto generated" : name),
							      "deepTracking", 0,
							      "relevance", 0,
							      "altText", null,
							      "extendURL", 0,
							      "adminLink", (isAdminLink ? 1 : 0));
						query = "SELECT last_insert_id()";
						urlID = dbase.queryLong (jdbc, query);
					}
				}
				if (urlID > 0) {
					url = new URL (urlID, rqurl, 3);
					url.setAdminLink (isAdminLink);
					URLlist.add (url);
					URLTable.put (rqurl, url);
					++urlcount;
					logging (Log.VERBOSE, "rqurl", "Added missing URL " + rqurl);
				}
			} catch (Exception e) {
				logging (Log.ERROR, "rqurl", "Failed to insert new URL " + rqurl + " into database: " + e.toString () + (query != null ? " (query " + query + ")" : ""), e);
			} finally {
				dbase.release (jdbc);
			}
		}
		return url;
	}
	
	public SWYN getSWYN () {
		if (swyn == null) {
			swyn = new SWYN (this);
			swyn.setup ();
		}
		return swyn;
	}
	
}
