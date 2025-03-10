/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.emm.core.admin.service;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.agnitas.beans.AdminEntry;
import org.agnitas.beans.AdminGroup;
import org.agnitas.beans.impl.PaginatedListImpl;
import org.agnitas.emm.core.velocity.VelocityCheck;
import org.agnitas.util.Tuple;

import com.agnitas.beans.ComAdmin;
import com.agnitas.beans.ComCompany;
import com.agnitas.emm.core.admin.AdminException;
import com.agnitas.emm.core.commons.password.PasswordState;
import com.agnitas.emm.core.supervisor.beans.Supervisor;
import com.agnitas.emm.core.supervisor.common.SupervisorException;
import com.agnitas.web.ComAdminForm;

public interface AdminService {
    
    /**
     * Returns admin by it's user name.
     * 
     * @param username user name
     * @param supervisorName supervisor name
     * @param password password of supervisor
     * 
     * @return {@link ComAdmin} for given user name
     * @throws SupervisorException 
     * @throws AdminException 
     */
	ComAdmin getAdminByNameForSupervisor(String username, String supervisorName, String password) throws AdminException, SupervisorException;

	Map<String, String> mapIdToUsernameByCompanyAndEmail(int companyId);

	Supervisor getSupervisor(String supervisorName);

    List<Tuple<Integer, String>> getAdminsUsernames(int companyID);

    Map<Integer, String> getAdminsNamesMap(@VelocityCheck int companyId);

    /**
     * Removes admin and his preferences
     * @param deletingAdminID this admin will be deleted
     * @param editorAdminID who performs this action
     */
    void deleteAdmin(int companyID, int deletingAdminID, int editorAdminID);

    AdminSavingResult saveAdmin(ComAdminForm form, ComAdmin editorAdmin);

    /**
     * Save the permissions for an admin.
     *
     * Rules for changing admin rights:
     * - Rights granted by the admingroup cannot be changed in anyway (Change admin's group itself if needed to do so)
     * - Standard rights can be changed in anyway by any GUI user, who has the right to change admin rights
     * - Premium rights can only be changed, if the GUI user has the specific premium right himself and has the right to change admin rights
     * - "Others" rights and rights of unknown categories can only be changed by emm-master
     *
     * For information on rules for changing user rights, see also:
     *  http://wiki.agnitas.local/doku.php?id=abteilung:allgemein:premiumfeatures&s[]=rechtevergabe#rechtevergabe-moeglichkeiten_in_der_emm-gui
     *
     * @return tuple of added and removed permission tokens or {@code null} if something went wrong.
     */
    Tuple<List<String>, List<String>> saveAdminPermissions(int companyID, int savingAdminID, Collection<String> tokens, int editorAdminID);

    ComAdmin getAdmin(int adminID, int companyID);

    int getNumberOfAdmins();

    boolean adminExists(String username);

    List<AdminGroup> getAdminGroups(@VelocityCheck int companyID);

    List<ComCompany> getCreatedCompanies(@VelocityCheck int companyID);

    int adminGroupExists(@VelocityCheck int companyId, String groupname);

    PaginatedListImpl<AdminEntry> getAdminList(
            int companyID,
            String searchFirstName,
            String searchLastName,
            String searchEmail,
            String searchCompanyName,
            Integer filterCompanyId,
            Integer filterAdminGroupId,
            Integer filterMailinglistId,
            String filterLanguage,
            String sort,
            String direction,
            int pageNumber,
            int pageSize);

    List<AdminEntry> getAdminEntriesForUserActivityLog(ComAdmin admin);

    PasswordState getPasswordState(ComAdmin admin);

    Date computePasswordExpireDate(ComAdmin admin);

    boolean setPassword(int adminId, @VelocityCheck int companyId, String password);
}
