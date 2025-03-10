/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package org.agnitas.backend.dao;

import	java.sql.SQLException;
import	java.sql.Timestamp;
import	java.util.Date;
import	java.util.List;
import	java.util.Map;

import	org.agnitas.backend.DBase;
import	org.agnitas.util.Log;

/**
 * Accesses all maildrop status relevant information from the database
 * from the table maildrop_status_tbl
 * 
 * No caching here as we have to ensure to always access the real data
 */
public class MaildropStatusDAO {
	private long		statusID;
	private long		companyID;
	private long		mailingID;
	private String		statusField;
	private Timestamp	sendDate;
	private int		step;
	private int		blockSize;
	private int		genStatus;
	private long		maxRecipients;
	private long		adminTestTargetID;
	private String		optimizeMailGeneration;
	private boolean		selectedTestRecipients;
	private long		realSendDateStatusID;
	private Date		realSendDate;
		
	public MaildropStatusDAO (DBase dbase, long forStatusID) throws SQLException {
		Map <String, Object>	row;
	
		try (DBase.With with = dbase.with ()) {
			row = dbase.querys (with.jdbc (),
					    "SELECT status_id, company_id, mailing_id, status_field, senddate, " +
					    "       step, blocksize, genstatus, max_recipients, " + 
					    "       admin_test_target_id, optimize_mail_generation, selected_test_recipients " +
					    "FROM maildrop_status_tbl " + 
					    "WHERE status_id = :statusID",
					    "statusID", forStatusID);
			if (row != null) {
				statusID = dbase.asLong (row.get ("status_id"));
				companyID = dbase.asLong (row.get ("company_id"));
				mailingID = dbase.asLong (row.get ("mailing_id"));
				statusField = dbase.asString (row.get ("status_field"));
				if ("C".equals (statusField)) {
					statusField = "E";
				}
				sendDate = dbase.asTimestamp (row.get ("senddate"));
				step = dbase.asInt (row.get ("step"));
				blockSize = dbase.asInt (row.get ("blocksize"));
				genStatus = dbase.asInt (row.get ("genstatus"));
				maxRecipients = dbase.asLong (row.get ("max_recipients"));
				adminTestTargetID = dbase.asLong (row.get ("admin_test_target_id"));
				optimizeMailGeneration = dbase.asString (row.get ("optimize_mail_generation"));
				selectedTestRecipients = dbase.asInt (row.get ("selected_test_recipients")) == 1;
				determinateRealSendDate (dbase);
			} else {
				statusID = 0;
			}
		}
	}

	public long statusID () {
		return statusID;
	}
	public long companyID () {
		return companyID;
	}
	public long mailingID () {
		return mailingID;
	}
	public String statusField () {
		return statusField;
	}
	public Timestamp sendDate () {
		return sendDate;
	}
	public int step () {
		return step;
	}
	public int blockSize () {
		return blockSize;
	}
	public int genStatus () {
		return genStatus;
	}
	public long maxRecipients () {
		return maxRecipients;
	}
	public long adminTestTargetID () {
		return adminTestTargetID;
	}
	public String optimizeMailGeneration () {
		return optimizeMailGeneration;
	}
	public boolean selectedTestRecipients () {
		return selectedTestRecipients;
	}
	public Date realSendDate () {
		return realSendDate;
	}

	/**
	 * get the senddate in a specific format
	 */
	public String formatRealSenddate (DBase dbase, String format) throws SQLException {
		String	rc = "";
		
		try (DBase.With with = dbase.with ()) {
			String				query = null;
			List <Map <String, Object>>	rq;
			Map <String, Object>		row;
			
			if (realSendDateStatusID > 0L) {
				if (dbase.isOracle ()) {
					query = "SELECT to_char (senddate, :fmt) fmt FROM maildrop_status_tbl WHERE status_id = :statusID";
				} else {
					query = "SELECT cast(date_format(senddate, :fmt) AS char) fmt FROM maildrop_status_tbl WHERE status_id = :statusID";
				}
				rq = dbase.query (with.jdbc(),
						  query,
						  "fmt", format, "statusID", realSendDateStatusID);
				if (rq.size () > 0) {
					row = rq.get (0);
					rc = dbase.asString (row.get ("fmt"));
				}
			}
			if (rc == null) {
				if (dbase.isOracle ()) {
					query = "SELECT to_char (sysdate, :fmt) fmt FROM dual";
				} else {
					query = "SELECT cast(date_format(current_date, :fmt) AS char) fmt";
				}
				rq = dbase.query (with.jdbc (),
						  query,
						  "fmt", format);
				if (rq.size () > 0) {
					row = rq.get (0);
					rc = dbase.asString (row.get ("fmt"));
				}
			}
		}
		return rc;
	}

	private void determinateRealSendDate (DBase dbase) throws SQLException {
		char				currentStatus = '\0';
		List <Map <String, Object>>	rq;
		Map <String, Object>		row;

		realSendDateStatusID = statusID;
		realSendDate = null;
		try (DBase.With with = dbase.with ()) {
			rq = dbase.query (with.jdbc (),
					  "SELECT status_id, status_field, senddate " +
					  "FROM maildrop_status_tbl " +
					  "WHERE mailing_id = :mailingID",
					  "mailingID", mailingID);
			for (int n = 0; n < rq.size (); ++n) {
				row = rq.get (n);
			
				String	checkStatusField = dbase.asString (row.get ("status_field"));
			
				if ((checkStatusField != null) && (checkStatusField.length () > 0)) {
					char	status = statusField.charAt (0);
					boolean	hit = false;
					
					switch (status) {
					case 'W':
						hit = true;
						break;
					case 'R':
					case 'D':
						hit = currentStatus != 'W';
						break;
					case 'E':
						hit = currentStatus != 'W' && currentStatus != 'R' && currentStatus != 'D';
						break;
					case 'A':
					case 'T':
						hit = currentStatus == 'T' || currentStatus == 'A' || currentStatus == '\0';
						break;
					}
					if (hit) {
						currentStatus = status;
						realSendDateStatusID = dbase.asLong (row.get ("status_id"));
						realSendDate = dbase.asDate (row.get ("senddate"));
					}
				}
			}
		}
		if (realSendDate == null) {
			realSendDate = new Date ();
		}
	}

	static private long findStatusIDForWorldMailing (DBase dbase, long mailingID, String direction) throws SQLException {
		List <Map <String, Object>>	rq;

		try (DBase.With with = dbase.with ()) {
			rq = dbase.query (with.jdbc (),
					  "SELECT status_id " +
					  "FROM maildrop_status_tbl " + 
					  "WHERE mailing_id = :mailingID AND status_field = :statusField " +
					  "ORDER BY status_id " + direction,
					  "mailingID", mailingID,
					  "statusField", "W");
			if (rq.size () > 0) {
				return dbase.asLong (rq.get (0).get ("status_id"));
			}
		}
		return 0L;
	}
	/**
	 * find the largest status id for a world mailing
	 */
	static public long findLargestStatusIDForWorldMailing (DBase dbase, long mailingID) throws SQLException {
		return findStatusIDForWorldMailing (dbase, mailingID, "DESC");
	}
	/**
	 * find the smallest status id for a world mailing
	 */
	static public long findSmallestStatusIDForWorldMailing (DBase dbase, long mailingID) throws SQLException {
		return findStatusIDForWorldMailing (dbase, mailingID, "ASC");
	}

	/**
	 * update genstatus for statusID
	 */
	public boolean updateGenStatus (DBase dbase, int fromStatus, int toStatus) throws SQLException {
		boolean	rc = false;
		String	query =
			"UPDATE maildrop_status_tbl " + 
			"SET genchange = CURRENT_TIMESTAMP, genstatus = :toStatus " +
			"WHERE status_id = :statusID" + (fromStatus > 0 ? " AND genstatus = :fromStatus" : "");
		
		try (DBase.With with = dbase.with ()) {
			int	count;
			
			if (fromStatus > 0) {
				count = dbase.update (with.jdbc (),
						      query,
						      "toStatus", toStatus,
						      "fromStatus", fromStatus,
						      "statusID", statusID);
			} else {
				count = dbase.update (with.jdbc (),
						      query,
						      "toStatus", toStatus,
						      "statusID", statusID);
			}
			if (count == 1) {
				rc = true;
			} else {
				dbase.logging (Log.ERROR, "genstatus", "Update genstatus from " + fromStatus + " to " + toStatus + " for " + statusID + " affected " + count + " rows");
			}
		}
		dbase.logging (rc ? Log.INFO : Log.ERROR, "genstatus", (rc ? "" : "NOT ") + "Changed generation state" + (fromStatus > 0 ? " from " + fromStatus : "") + " to " + toStatus);
		return rc;
	}
	
	/**
	 * remove an entry in the table
	 */
	public boolean remove (DBase dbase) throws SQLException {
		try (DBase.With with = dbase.with ()) {
			return dbase.update (with.jdbc (),
					     "DELETE FROM maildrop_status_tbl " + 
					     "WHERE status_id = :statusID",
					     "statusID", statusID) == 1;
		}
	}
}
