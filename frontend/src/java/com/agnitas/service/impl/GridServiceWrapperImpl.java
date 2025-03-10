/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/


package com.agnitas.service.impl;

import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.agnitas.beans.ComAdmin;
import com.agnitas.beans.ComMailing;
import com.agnitas.emm.core.mailing.service.ComMailingGridService;
import com.agnitas.emm.grid.grid.beans.ComGridTemplate;
import com.agnitas.emm.grid.grid.beans.ComTemplateSettings;
import com.agnitas.emm.grid.grid.service.ComGridTemplateService;
import com.agnitas.emm.grid.grid.service.MailingCreationOptions;
import com.agnitas.service.GridServiceWrapper;
import org.agnitas.emm.core.velocity.VelocityCheck;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component("GridServiceWrapper")
public class GridServiceWrapperImpl implements GridServiceWrapper {
    
    private static final Logger logger = Logger.getLogger(GridServiceWrapperImpl.class);
    
    @Autowired(required = false)
    private ComMailingGridService mailingGridService;

    @Autowired(required =  false)
    private ComGridTemplateService gridTemplateService;
    
    public int getGridTemplateIdByMailingId(int mailingId) {
        return checkAndReturn(mailingGridService, () -> mailingGridService.getGridTemplateId(mailingId), 0);
    }
   
    @Override
    public ComGridTemplate getGridTemplate(@VelocityCheck int companyId, int templateId) {
        return checkAndReturn(gridTemplateService, () -> gridTemplateService.getGridTemplate(templateId, companyId));
    }
    
    @Override
    public Map<String, Object> getMailingGridInfo(@VelocityCheck int companyId, int mailingId) {
        return checkAndReturn(mailingGridService, () -> mailingGridService.getMailingGridInfo(mailingId, companyId));
    }
    
    @Override
    public void saveMailingGridInfo(int mailingId, int companyId, Map<String, Object> data) {
        checkAndRun(mailingGridService, (dummy) -> mailingGridService.saveMailingGridInfo(mailingId, companyId, data));
    }
    
    @Override
    public ComTemplateSettings getGridTemplateSettings(int templateId) {
        return checkAndReturn(gridTemplateService, () -> gridTemplateService.getGridTemplateSettings(templateId));
    }
    
    @Override
    public ComMailing createGridMailing(ComAdmin admin, int templateId, MailingCreationOptions creationOptions) throws Exception {
        if (gridTemplateService != null) {
            return gridTemplateService.createMailing(admin, templateId, creationOptions);
        } else {
            logger.info("Grid service does not exist. Return default value: null");
            return null;
        }
    }
    
    @Override
    public void saveUndoGridMailing(int mailingId, int gridTemplateId, int adminId) {
        checkAndRun(mailingGridService, (dummy) -> mailingGridService.saveUndoGridMailing(mailingId, gridTemplateId, adminId));
    }
    
    @Override
    public void restoreGridMailingUndo(int undoId, ComMailing mailing) {
        checkAndRun(mailingGridService, (dummy) -> mailingGridService.restoreGridMailingUndo(undoId, mailing));
    }
    
    private <T> T checkAndReturn(Object service, Supplier<T> supplier) {
        return checkAndReturn(service, supplier, null);
    }
    
    private <T> T checkAndReturn(Object service, Supplier<T> supplier, T defaultValue) {
        if (service != null) {
            return supplier.get();
        } else {
            logger.info("Grid service does not exist. Return default value: " + defaultValue);
            return defaultValue;
        }
    }
    
    private <T> void checkAndRun(Object service, Consumer<T> function) {
        checkAndRun(service, function, null);
    }
    
    private <T> void checkAndRun(Object service, Consumer<T> function, T param) {
        if (service != null) {
            function.accept(param);
        } else {
            logger.info("Grid service does not exist.");
        }
    }
    
}
