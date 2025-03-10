/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

DELETE FROM config_tbl WHERE class = 'logon' AND name LIKE 'iframe.url.%';
INSERT INTO config_tbl (class, name, value) VALUES ('logon', 'iframe.url.en', 'https://www.agnitas.de/en/openemm-login/');
INSERT INTO config_tbl (class, name, value) VALUES ('logon', 'iframe.url.de', 'https://www.agnitas.de/openemm-login/');

INSERT INTO agn_dbversioninfo_tbl (version_number, updating_user, update_timestamp)
	VALUES ('19.01.303', CURRENT_USER, CURRENT_TIMESTAMP);
