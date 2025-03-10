package com.agnitas.emm.core.supervisor.dao.impl;

import java.util.List;

import com.agnitas.emm.core.supervisor.beans.Supervisor;
import com.agnitas.emm.core.supervisor.common.SortCriterion;
import com.agnitas.emm.core.supervisor.common.SortDirection;
import com.agnitas.emm.core.supervisor.common.SupervisorException;
import com.agnitas.emm.core.supervisor.dao.ComSupervisorDao;

/**
 * Dummy implementation of {@link ComSupervisorDao }
 */
public class DefaultSupervisorDaoImpl implements ComSupervisorDao {
	
	 @Override
    public Supervisor getSupervisor(String supervisorName, String password) throws SupervisorException {
        return null;
    }
    
    @Override
    public List<Supervisor> listAllSupervisors(SortCriterion criterion, SortDirection direction) throws SupervisorException {
        return null;
    }
    
    @Override
    public Supervisor getSupervisor(int id) throws SupervisorException {
        return null;
    }
    
    @Override
    public void setSupervisorPassword(int id, String password) throws SupervisorException {
    	// default implementation
    }
    
    @Override
    public boolean isCurrentPassword(int id, String pwd) throws SupervisorException {
        return false;
    }
    
    @Override
    public Supervisor getSupervisor(String supervisorName) {
        return null;
    }
    
    @Override
    public int getNumberOfSupervisors() {
        return 0;
    }
    
    @Override
    public int createSupervisor(Supervisor supervisor) {
        return 0;
    }
    
    @Override
    public List<Integer> getAllowedCompanyIDs(int supervisorId) {
        return null;
    }
    
    @Override
    public void setAllowedCompanyIds(int id, List<Integer> allowedCompanyIds) throws SupervisorException {
    	// default implementation
    }
    
    @Override
    public Supervisor updateSupervisor(Supervisor supervisor) {
        return null;
    }
    
    @Override
    public boolean logSupervisorLogin(int supervisorId, int companyId) {
        return false;
    }
    
    @Override
    public void cleanupUnusedSupervisorBindings(int daysBeforeInactive) {
    	// default implementation
    }
    
    @Override
    public boolean deleteSupervisor(int supervisorId) {
        return false;
    }
}
