/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

DROP PROCEDURE IF EXISTS TEMP_PROCEDURE;

DELIMITER ;;
CREATE PROCEDURE TEMP_PROCEDURE()
BEGIN
  DECLARE done INT DEFAULT FALSE;
  DECLARE tablename VARCHAR(32);
  DECLARE table_cursor CURSOR FOR SELECT DISTINCT LOWER(table_name) FROM information_schema.columns WHERE table_schema = SCHEMA() AND LOWER(table_name) = LOWER('push_subscription_tbl');
  DECLARE column_cursor CURSOR FOR SELECT DISTINCT LOWER(table_name) FROM information_schema.columns WHERE table_schema = SCHEMA() AND LOWER(table_name) LIKE LOWER('customer_%_tbl') AND LOWER(column_name) = LOWER('sys_do_not_track_me');
  DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = true;

  SET done = false;
  OPEN table_cursor;
  read_loop: LOOP
    FETCH table_cursor INTO tablename;
    IF done THEN
      LEAVE read_loop;
    END IF;

    SET @ddl1 = CONCAT('DROP TABLE ', 'push_subscription_tbl');
    PREPARE stmt1 FROM @ddl1;
    EXECUTE stmt1;
    DEALLOCATE PREPARE stmt1;
  END LOOP;
  CLOSE table_cursor;
  
  SET done = false;
  OPEN column_cursor;
  read_loop: LOOP
    FETCH column_cursor INTO tablename;
    IF done THEN
      LEAVE read_loop;
    END IF;

    SET @ddl1 = CONCAT('ALTER TABLE ', tablename, ' DROP COLUMN sys_do_not_track_me');
    PREPARE stmt1 FROM @ddl1;
    EXECUTE stmt1;
    DEALLOCATE PREPARE stmt1;
  END LOOP;
  CLOSE column_cursor;
END;;
DELIMITER ;

CALL TEMP_PROCEDURE;
DROP PROCEDURE TEMP_PROCEDURE;

INSERT INTO agn_dbversioninfo_tbl (version_number, updating_user, update_timestamp)
	VALUES ('19.01.433', CURRENT_USER, CURRENT_TIMESTAMP);
