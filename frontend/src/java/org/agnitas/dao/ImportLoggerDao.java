/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package org.agnitas.dao;

import org.agnitas.emm.core.velocity.VelocityCheck;

public interface ImportLoggerDao {

    /**
     *
     * Insert record into table import_log_tbl with given values of company id, admin id, datasource id,
     * imported lines, statistics, profile
     *
     * @param companyId The id of the company
     * @param adminId The id of the admin
     * @param datasource_id The id of the datasourse
     * @param importedLines The number of imported lines
     * @param statistics The value of statistics
     * @param profile The name of profile
     */
    void log( @VelocityCheck int companyId, int adminId, int datasource_id, int importedLines, String statistics, String profile);

}
