/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.service.impl;

import java.util.List;

import org.agnitas.beans.AdminEntry;
import org.agnitas.beans.AdminGroup;
import org.springframework.beans.factory.annotation.Required;

import com.agnitas.dao.ComAdminGroupDao;
import com.agnitas.service.ComPDFService;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.pdf.PdfPTable;

public class ComPDFServiceImpl implements ComPDFService {
	/** DAO for accessing admin group data. */
	protected ComAdminGroupDao adminGroupDao;
	
    @Required
	public void setAdminGroupDao(ComAdminGroupDao adminGroupDao) {
		this.adminGroupDao = adminGroupDao;
	}
	
	@Override
    public void writeUsersPDF(List<AdminEntry> users, Document document) throws DocumentException {
        PdfPTable table = new PdfPTable(5);
        writeUserTableHeaders(table);
        writeUserTableRows(users, table);
        document.add(table);
    }

    private void writeUserTableHeaders(PdfPTable table) {
        table.addCell("Username");
        table.addCell("Firstname");
        table.addCell("Lastname");
        table.addCell("Email");
        table.addCell("UserGroup");
    }

    private void writeUserTableRows(List<AdminEntry> users, PdfPTable table) {
        for (AdminEntry user : users) {
            table.addCell(user.getUsername());
            table.addCell(user.getFirstname());
            table.addCell(user.getFullname());
            table.addCell(user.getEmail());
            List<AdminGroup> adminGroups = adminGroupDao.getAdminGroupByAdminID(user.getId());
        	for (AdminGroup adminGroup : adminGroups) {
        		table.addCell(adminGroup.getShortname());
        	}
        }
    }
}
