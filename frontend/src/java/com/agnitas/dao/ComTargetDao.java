/*

    Copyright (C) 2019 AGNITAS AG (https://www.agnitas.org)

    This program is free software: you can redistribute it and/or modify it under the terms of the GNU Affero General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more details.
    You should have received a copy of the GNU Affero General Public License along with this program. If not, see <https://www.gnu.org/licenses/>.

*/

package com.agnitas.dao;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.agnitas.dao.exception.target.TargetGroupPersistenceException;
import org.agnitas.emm.core.velocity.VelocityCheck;
import org.agnitas.target.TargetRepresentation;

import com.agnitas.beans.ComTarget;
import com.agnitas.beans.TargetLight;
import com.agnitas.emm.core.target.beans.RawTargetGroup;

public interface ComTargetDao {
    /**
     * Marks target group as "deleted" in the database.
     *
     * @param targetID
     *          The id of the target group to mark.
     * @param companyID
     *          The id of company.
     * @return  true on success.
     * @throws TargetGroupPersistenceException on errors saving target group
     */
    boolean deleteTarget(int targetID, @VelocityCheck int companyID) throws TargetGroupPersistenceException;

    /**
     *  Loads target group identified by target id and company id.
     *
     * @param targetID
     *           The id of the target group that should be loaded.
     * @param companyID
     *          The companyID for the target group.
     * @return The Target or null on failure.
     */
    ComTarget getTarget(int targetID, @VelocityCheck int companyID);

    /**
     *  Loads target group identified by target name and company id.
     *
     * @param targetName
     *           The name of the target group that should be loaded.
     * @param companyID
     *          The companyID for the target group.
     * @return The Target or null on failure.
     */
	/*
	 * IMPORTANT NOTE: Target group names are not unique!
	 *
	 * The only exception to this are list split target groups. (Names of these target groups are unique.)
	 * This method MUST NOT be used for other target groups than list split target groups.
	 *
	 * TODO: Replace this method by a new method that takes list split data as parameters to avoid misuse of this method.
	 */
    ComTarget getTargetByName(String targetName, @VelocityCheck int companyID);

    /**
     *  Loads target group identified by list split parameters and company id.
     *
     * @param splitType
     * @param index
     * @param companyID
     *          The companyID for the target group.
     * @return The Target or null on failure.
     */
    ComTarget getListSplitTarget(String splitType, int index, @VelocityCheck int companyID);

    /**
     *  Loads target group identified by list split parameters and company id.
     *
     * @param prefix
     * @param splitType
     * @param index
     * @param companyID
     *          The companyID for the target group.
     * @return The Target or null on failure.
     */
    ComTarget getListSplitTarget(String prefix, String splitType, int index, @VelocityCheck int companyID);

    /**
     * Loads all target groups marked as "deleted" for company id.
     * Uses JdbcTemplate.
     *
     * @param companyID
     *          The companyID for the target groups.
     *
     * @return List of Targets or empty list.
     */
    List<Integer> getDeletedTargets( @VelocityCheck int companyID);

    /**
     * Saves or updates target group in database. Target group is visible in lists.
     *
     * @param target
     *          The target group to save.
     * @return Saved target group id.
     * @throws TargetGroupPersistenceException on errors saving target group
     */
    int saveTarget(ComTarget target) throws TargetGroupPersistenceException;
    
    /**
     * Saves or updates target group in database. Target group is hidden in lists.
     *
     * @param target
     *          The target group to save.
     * @return Saved target group id.
     * @throws TargetGroupPersistenceException on errors saving target group
     */
    int saveHiddenTarget(ComTarget target) throws TargetGroupPersistenceException;
    
    /**
     * Loads all target groups allowed for given company.
     * Uses JdbcTemplate.
     *
     * @param companyID
     *      The companyID for the target groups.
     * @return List of Targets or empty list.
     */
	Map<Integer, ComTarget>	getAllowedTargets( @VelocityCheck int companyID);

    /**
     * Get a shortname of the target referenced by {@code targetId}.
     * @param targetId an identifier of a target.
     * @param companyId an identifier of a company.
     * @param includeDeleted whether ({@code true}) treat a deleted (marked as deleted) target as existing or not.
     * @return a target name or {@code null} if a {@code targetId} doesn't exist
     * or a referenced target belongs to another company.
     */
    String getTargetName(int targetId, @VelocityCheck int companyId, boolean includeDeleted);

	/**
	 * Load list of Target groups names by IDs.
     * Uses JdbcTemplate.
     *
	 * @param companyId company ID
	 * @param targetIds the IDs of target groups
	 * @return the list of names
	 */
	List<String> getTargetNamesByIds( @VelocityCheck int companyId, Set<Integer> targetIds);
	
	String getTargetSplitName(int targetId);
	
	int getTargetSplitID(String name);

	String getTargetSQL(int targetId, @VelocityCheck int companyId);

	List<String> getSplitNames(@VelocityCheck int companyID);
	
	int getSplits(@VelocityCheck int companyID, String shortName);

	/**
	 * Safely copy sample target groups from company 1 to selected company.
	 * @param companyID more than 1
	 * @return number of copied target groups
	 */
	int createSampleTargetGroups(int companyID);

	void updateTargetLockState(int targetID, @VelocityCheck int companyID, boolean locked);

    boolean deleteTargetReally(int targetID, @VelocityCheck int companyId);

    boolean deleteTargetsReally(@VelocityCheck int companyID);

    List<TargetLight> getTargetLights(@VelocityCheck int companyID);
    
    List<TargetLight> getTargetLights(int companyID, boolean includeDeleted);
    
    List<TargetLight> getTargetLights(int companyID, boolean includeDeleted, boolean worldDelivery, boolean adminTestDelivery);
    
	List<TargetLight> getTargetLights(int companyID, boolean includeDeleted, boolean worldDelivery, boolean adminTestDelivery, boolean content);

    List<TargetLight> getTargetLightsBySearchParameters(@VelocityCheck int companyId, boolean includeDeleted, boolean worldDelivery, boolean adminTestDelivery,
            boolean content, boolean isSearchName, boolean isSearchDescription, String searchQueryText);

	List<TargetLight> getTargetLights(int companyID, Collection<Integer> targetIds, boolean includeDeleted);
	
	List<TargetLight> getUnchoosenTargetLights(@VelocityCheck int companyID, Collection<Integer> targetIds);
	
	List<TargetLight> getChoosenTargetLights(String targetExpression, final int companyID);
	
	List<TargetLight> getTestAndAdminTargetLights(int companyId);
	
    List<TargetLight> getSplitTargetLights(@VelocityCheck int companyID, String splitType);

    boolean isBasicFullTextSearchSupported();
    
	boolean deleteWorkflowTargetConditions(@VelocityCheck int companyId, int workflowId);
	
	void deleteWorkflowTargetConditions(@VelocityCheck int companyId);
	
    boolean updateTargetGroupEQL(Map<Integer, String> targetEQLForUpdate);
    
    // -------------------------------------------------------------------------------------- Deprecated API

    /**
     * Loads all target groups for company id. Target groups marked as "deleted" are ignored.
     * @param companyID
     *          The companyID for the target groups.
     * @return List of Targets or empty list.
     * 
     * @see #getTargetLights(int)
     */
    @Deprecated
    List<ComTarget> getTargets( @VelocityCheck int companyID);

    /**
     * Loads all target groups for company id.
     *
     * @param companyID
     *          The companyID for the target groups.
     * @param includeDeleted
     *          If true - target groups marked as "deleted" will be loaded as well.
     *
     * @return List of Targets or empty list.
     * 
     * @see #getTargetLights(int, boolean)
     */
    @Deprecated
    List<ComTarget> getTargets( @VelocityCheck int companyID, boolean includeDeleted);
	
	/**
	 * Load list of Target groups by IDs.
     *
	 * @param companyID
     *          The company ID for target groups.
	 * @param targetIds
     *          The IDs of target groups to load.
     * @param includeDeleted if true, deleted target groups are included
	 * @return List of Targets or empty list.
	 * 
	 * @see #getTargetLights(int, Collection, boolean)
	 */
	@Deprecated
    List<ComTarget> getTargetGroup( @VelocityCheck int companyID, Collection<Integer> targetIds, boolean includeDeleted);

	Map<Integer, TargetLight> getAllowedTargetLights(int companyID);

	/**
	 * Target group names are not unique
	 */
	List<TargetLight> getTargetLightsByName(String targetName, @VelocityCheck int companyID, boolean allowDeleted);
	
//	List<Map<String, Object>> select(Logger logger, String statement, Object... parameter);
	
	boolean isOracle();

	/**
	 * Lists all (non-deleted) of a company with ID, name, EQL and/or {@link TargetRepresentation}.
	 * @param companyId company ID
	 * @return list of target groups
	 */
	List<RawTargetGroup> listRawTargetGroups(@VelocityCheck int companyId);
	
	List<RawTargetGroup> getTargetsCreatedByWorkflow(@VelocityCheck int companyId, boolean onlyEmptyEQL);
	
	List<Integer> getTargetIdsCreatedByWorkflow(@VelocityCheck int companyId);
	
}
