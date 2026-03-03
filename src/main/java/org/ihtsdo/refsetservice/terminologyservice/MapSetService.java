/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.terminologyservice;

import java.util.Date;
import java.util.List;
import java.util.Properties;

import javax.persistence.TypedQuery;

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.handler.TerminologyServerHandler;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapSetExportRequest;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.model.enums.WorkflowAction;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service class to handle getting and modifying internal mapset information.
 */
public class MapSetService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MapSetService.class);

    /** The config properties. */
    protected static final Properties PROPERTIES = PropertyUtility.getProperties();

    /** The terminology handler. */
    private static TerminologyServerHandler terminologyHandler;

    static {

        // Instantiate terminology handler
        try {
            final String key = "terminology.handler";
            final String handlerName = PropertyUtility.getProperty(key);
            if (handlerName.isEmpty()) {
                throw new Exception("terminology.handler expected and does not exist.");
            }

            terminologyHandler = HandlerUtility.newStandardHandlerInstanceWithConfiguration(key, handlerName, TerminologyServerHandler.class);

        } catch (Exception e) {
            LOG.error("Failed to initialize terminology.handler - serious error", e);
            terminologyHandler = null;
        }
    }

    /**
     * Instantiates a new map set service.
     */
    private MapSetService() {

        // Utility class
    }

    /**
     * Returns the map set.
     *
     * @param branch the branch
     * @param code the code
     * @return the map set
     * @throws Exception the exception
     */
    public static MapSet getMapSet(final String branch, final String code) throws Exception {

        return terminologyHandler.getMapSet(branch, code);

    }

    /**
     * Finds MapSet from map_sets table by refSetCode (for workflow/tracking data).
     * Uses direct JPA query so it works regardless of search index state.
     * Returns null if not found.
     *
     * @param service the terminology service
     * @param refSetCode the ref set code
     * @return the map set from DB, or null
     * @throws Exception the exception
     */
    public static MapSet findMapSetByRefSetCode(final TerminologyService service, final String refSetCode) throws Exception {

        if (refSetCode == null || refSetCode.isBlank()) {
            return null;
        }
        final List<MapSet> results = service.getEntityManager()
            .createQuery("SELECT m FROM MapSet m WHERE m.refSetCode = :refSetCode", MapSet.class)
            .setParameter("refSetCode", refSetCode)
            .setMaxResults(1)
            .getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Finds a map set by branch path (branchPath or fromBranchPath).
     * Used when only branch is known (e.g. import flow).
     *
     * @param service the terminology service
     * @param branchPath the branch path
     * @return the first matching map set, or null
     * @throws Exception the exception
     */
    public static MapSet findMapSetByBranchPath(final TerminologyService service, final String branchPath) throws Exception {

        if (branchPath == null || branchPath.isBlank()) {
            return null;
        }
        final List<MapSet> results = service.getEntityManager()
            .createQuery("SELECT m FROM MapSet m WHERE m.branchPath = :branchPath OR m.fromBranchPath = :branchPath", MapSet.class)
            .setParameter("branchPath", branchPath)
            .setMaxResults(1)
            .getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Gets MapSet for workflow operations by id or refSetCode.
     * Tries id first (JPA get), then refSetCode lookup.
     *
     * @param service the terminology service
     * @param mapSetInternalId the map set id or ref set code
     * @return the map set from DB
     * @throws Exception if not found
     */
    public static MapSet getMapSetForWorkflow(final TerminologyService service, final String mapSetInternalId) throws Exception {

        if (mapSetInternalId == null || mapSetInternalId.isBlank()) {
            throw new Exception("MapSet identifier is required");
        }
        MapSet mapSet = null;
        try {
            mapSet = service.get(mapSetInternalId, MapSet.class);
        } catch (final Exception e) {
            // id lookup failed, try refSetCode
        }
        if (mapSet == null) {
            mapSet = findMapSetByRefSetCode(service, mapSetInternalId);
        }
        if (mapSet == null) {
            throw new Exception("Unable to retrieve map set " + mapSetInternalId);
        }
        return mapSet;
    }

    /**
     * Resolves branch path for map set operations from map_sets table.
     * For a specific map set: uses its branchPath. For general operations: uses branch from first map_sets record.
     *
     * @param service the terminology service
     * @param mapSetCode optional ref set code; if provided, uses that map set's branch
     * @return the branch path, or null if no map_sets records exist
     * @throws Exception the exception
     */
    public static String resolveBranchFromMapSets(final TerminologyService service, final String mapSetCode) throws Exception {

        if (mapSetCode != null && !mapSetCode.isBlank()) {
            final MapSet mapSet = findMapSetByRefSetCode(service, mapSetCode);
            if (mapSet != null && mapSet.getBranchPath() != null) {
                return mapSet.getBranchPath();
            }
        }
        final TypedQuery<String> query = service.getEntityManager()
            .createQuery("SELECT m.branchPath FROM MapSet m WHERE m.branchPath IS NOT NULL ORDER BY m.modified DESC", String.class)
            .setMaxResults(1);
        final List<String> results = query.getResultList();
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Returns the map sets.
     *
     * @param branch the branch
     * @return the map sets
     * @throws Exception the exception
     */
    public static List<MapSet> getMapSets(final String branch) throws Exception {

        return terminologyHandler.getMapSets(branch);

    }

    /**
     * Get the map set member concepts in RF2 format.
     *
     * @param user the user
     * @param mapProject the map project
     * @param mapSetExportRequest the map set export request
     * @return the refset member concepts
     * @throws Exception the exception
     */
    public static String exportMapSet(final User user, final MapProject mapProject, final MapSetExportRequest mapSetExportRequest) throws Exception {

        return terminologyHandler.exportMapSet(user, mapProject, mapSetExportRequest);
    }

    /**
     * Sets the workflow status.
     *
     * @param service the service
     * @param user the user
     * @param mapSetInternalId the map set internal id
     * @param action the action
     * @param notes the notes
     * @return the refset
     * @throws Exception the exception
     */
    public static MapSet setWorkflowStatus(final TerminologyService service, final User user, final String mapSetInternalId, final WorkflowAction action,
        final String notes) throws Exception {

        return terminologyHandler.setWorkflowStatus(service, user, mapSetInternalId, action, notes);
    }

    /**
     * Create a new version of a map set.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param mapSetInternalId the internal map set ID to base the new version on
     * @param inEdit should the map set be set into IN_EDIT status, if false it will be in READY_FOR_EDIT
     * @return the new map set version
     * @throws Exception the exception
     */
    public static MapSet createNewMapSetVersion(final TerminologyService service, final User user, final String mapSetInternalId, final boolean inEdit)
        throws Exception {

        MapSet oldLatestVersionMapSet = null;
        final MapSet mapSet = getMapSetForWorkflow(service, mapSetInternalId);

        // Check no existing IN_DEVELOPMENT version
        final ResultList<MapSet> results =
            service.find("versionStatus: (" + VersionStatus.IN_DEVELOPMENT.toString() + ") AND refSetCode: " + QueryParserBase.escape(mapSet.getRefSetCode()),
                null, MapSet.class, null);

        if (!results.getItems().isEmpty()) {
            throw new Exception("There is already a version of this map set that is 'In Development', and there can only be one");
        }

        final MapSet newMapSetVersion = new MapSet(mapSet);

        // set automatic changed fields
        final String editBranchId = BranchService.generateBranchId();
        final String mapBranchId = BranchService.generateBranchId();
        newMapSetVersion.setVersionDate(null);
        newMapSetVersion.setId(null);
        newMapSetVersion.setVersionStatus(VersionStatus.IN_DEVELOPMENT);
        newMapSetVersion.setLatestPublishedVersion(false);
        newMapSetVersion.setWorkflowStatus(WorkflowStatus.READY_FOR_EDIT);
        newMapSetVersion.setEditBranchId(editBranchId);
        newMapSetVersion.setMapBranchId(mapBranchId);
        newMapSetVersion.setBaseContentVersion(mapSet.getBaseContentVersion());
        newMapSetVersion.setInternationalContentVersion(mapSet.getInternationalContentVersion());
        newMapSetVersion.setVersion(mapSet.getVersion());
        newMapSetVersion.setProject(mapSet.getProject());

        mapSet.setMapBranchId(mapBranchId);

        // Persist new version
        service.add(newMapSetVersion);

        // Create the Snowstorm branches
        BranchService.createRefsetBranch(mapSet.toBranchDetails());
        BranchService.createEditBranch(newMapSetVersion.toBranchDetails(), editBranchId);

        // Add a workflow history entry for CREATE
        MapSetWorkflowService.addWorkflowHistory(service, user, WorkflowAction.CREATE, newMapSetVersion, "");

        // Update the workflow to IN_EDIT if required
        if (inEdit) {
            MapSetWorkflowService.setWorkflowStatus(service, user, WorkflowAction.EDIT, newMapSetVersion, "", WorkflowStatus.IN_EDIT, user.getUserName());
        }

        // Find the previous latest version
        if (mapSet.isLatestPublishedVersion()) {
            oldLatestVersionMapSet = mapSet;
        } else {
            oldLatestVersionMapSet =
                service.findSingle("refSetCode:" + QueryParserBase.escape(mapSet.getRefSetCode()) + " AND latestPublishedVersion: true", MapSet.class, null);
        }

        // Update the previous latest version so it is marked as having a version in development
        if (oldLatestVersionMapSet != null) {
            oldLatestVersionMapSet.setHasVersionInDevelopment(true);
            service.update(oldLatestVersionMapSet);
            LOG.info("MapSet {} version marked as having in development version.", oldLatestVersionMapSet.getId());
        }

        return newMapSetVersion;
    }

    /**
     * Clear all refset caches.
     *
     * @param branch the branch
     * @throws Exception the exception
     */
    public static void clearAllRefsetCaches(final String branch) throws Exception {

        terminologyHandler.clearAllRefsetCaches(branch);
    }

    /**
     * Gets the branch path.
     *
     * @param mapSet the map set
     * @return the branch path
     * @throws Exception the exception
     */
    public static String getBranchPath(final MapSet mapSet) throws Exception {

        return terminologyHandler.getBranchPath(mapSet);
    }

    /**
     * Gets the refset date from formatted string.
     *
     * @param publicationDateString the publication date string
     * @return the refset date from formatted string
     * @throws Exception the exception
     */
    public static Date getRefsetDateFromFormattedString(final String publicationDateString) throws Exception {

        return terminologyHandler.getRefsetDateFromFormattedString(publicationDateString);
    }

    /**
     * Sets the roles.
     *
     * @param user the user
     * @param project the project
     * @param roles the roles
     * @return the list
     * @throws Exception the exception
     */
    public static List<String> setRoles(final User user, final Project project, final List<String> roles) throws Exception {

        return terminologyHandler.setRoles(user, project, roles);
    }

    /**
     * Gets the branch versions.
     *
     * @param branch the branch
     * @return the branch versions
     * @throws Exception the exception
     */
    public static List<String> getBranchVersions(final String branch) throws Exception {

        return terminologyHandler.getBranchVersions(branch);
    }

    /**
     * Removes the map set edit history.
     *
     * @param service the service
     * @param refsetCode the refset code
     * @throws Exception the exception
     */
    public static void removeMapSetEditHistory(final TerminologyService service, final String refsetCode) throws Exception {

        terminologyHandler.removeMapSetEditHistory(service, refsetCode);
    }

    /**
     * Sets the map set member count.
     *
     * @param service the service
     * @param mapSet the map set
     * @param force the force
     * @throws Exception the exception
     */
    public static void setMapSetMemberCount(final TerminologyService service, final MapSet mapSet, final boolean force) throws Exception {

        terminologyHandler.setMapSetMemberCount(service, mapSet, force);
    }

    /**
     * Removes the upgrade data.
     *
     * @param service the service
     * @param mapSet the map set
     * @throws Exception the exception
     */
    public static void removeUpgradeData(final TerminologyService service, final MapSet mapSet) throws Exception {

        terminologyHandler.removeUpgradeData(service, mapSet);
    }

    /**
     * Replace map set with edit history.
     *
     * @param service the service
     * @param mapSet the map set
     * @throws Exception the exception
     */
    public static void replaceMapSetWithEditHistory(final TerminologyService service, final MapSet mapSet) throws Exception {

        terminologyHandler.replaceMapSetWithEditHistory(service, mapSet);
    }

    /**
     * Sets the map set permissions.
     *
     * @param user the user
     * @param mapSet the map set
     * @return the map set
     * @throws Exception the exception
     */
    public static MapSet setMapSetPermissions(final User user, final MapSet mapSet) throws Exception {

        return terminologyHandler.setMapSetPermissions(user, mapSet);
    }

}
