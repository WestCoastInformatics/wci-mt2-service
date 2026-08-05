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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.handler.TerminologyServerHandler;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapSetExportRequest;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.model.enums.WorkflowAction;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.HandlerUtility;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
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
     * Gets the map set.
     *
     * @param service the service
     * @param user the user
     * @param mapsetInternalId the mapset internal id
     * @return the map set
     * @throws Exception the exception
     */
    public static MapSet getMapSet(final TerminologyService service, final User user, final String mapsetInternalId) throws Exception {

        return getMapSet(service, mapsetInternalId);

    }

    /**
     * Finds MapSet by refSetCode (for workflow/tracking data).
     * Uses search index; when multiple map_sets share the same refSetCode, returns the latest by version date.
     *
     * @param service the terminology service
     * @param refSetCode the ref set code
     * @return the map set, or null if not found
     * @throws Exception the exception
     */
    public static MapSet findMapSetByRefSetCode(final TerminologyService service, final String refSetCode) throws Exception {

        if (refSetCode == null || refSetCode.isBlank()) {
            return null;
        }
        //Look for IN DEVELOPMENT version first
        final ResultList<MapSet> results = service.find("refSetCode:" + QueryParserBase.escape(refSetCode) + " AND versionStatus:" + VersionStatus.IN_DEVELOPMENT.name(), null, MapSet.class, null);
        if (!results.getItems().isEmpty()) {
            return results.getItems().get(0);
        }
        //If no IN DEVELOPMENT version, look for latest published version
        final PfsParameter pfs = new PfsParameter();
        pfs.setSort("versionDate");
        pfs.setAscending(false);
        pfs.setLimit(1);
        final ResultList<MapSet> results2 = service.find("refSetCode:" + QueryParserBase.escape(refSetCode), pfs, MapSet.class, null);
        return results2.getItems().isEmpty() ? null : results2.getItems().get(0);
    }

    /**
     * Finds a map set by branch path (branchPath or fromBranchPath).
     * Used when only branch is known (e.g. import flow).
     * When multiple match, returns the latest by version date.
     *
     * @param service the terminology service
     * @param branchPath the branch path
     * @return the matching map set, or null
     * @throws Exception the exception
     */
    public static MapSet findMapSetByBranchPath(final TerminologyService service, final String branchPath) throws Exception {

        if (branchPath == null || branchPath.isBlank()) {
            return null;
        }
        final String escaped = QueryParserBase.escape(branchPath);
        final String query = "(branchPath:" + escaped + " OR fromBranchPath:" + escaped + ")";
        final PfsParameter pfs = new PfsParameter();
        pfs.setSort("versionDate");
        pfs.setAscending(false);
        pfs.setLimit(1);
        final ResultList<MapSet> results = service.find(query, pfs, MapSet.class, null);
        return results.getItems().isEmpty() ? null : results.getItems().get(0);
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
    public static MapSet getMapSet(final TerminologyService service, final String mapSetInternalId) throws Exception {

        if (mapSetInternalId == null || mapSetInternalId.isBlank()) {
            throw new Exception("MapSet identifier is required");
        }
        TerminologyService.ensureFactoryForClassLoader(MapSet.class);
        MapSet mapSet = null;
        try {
            mapSet = findMapSetById(service, mapSetInternalId);
        } catch (final Exception e) {
            LOG.debug("MapSet id lookup failed for {}: {}", mapSetInternalId, e.getMessage());
        }
        if (mapSet == null) {
            mapSet = findMapSetByRefSetCode(service, mapSetInternalId);
        }
        if (mapSet == null) {
            throw new Exception("Unable to retrieve map set " + mapSetInternalId);
        }
        return mapSet;
    }

    private static MapSet findMapSetById(final TerminologyService service, final String mapSetInternalId) throws Exception {

        final EntityManager manager = service.getEntityManager();
        final EntityTransaction transaction = manager.getTransaction();
        final boolean startedHere = !transaction.isActive();
        if (startedHere) {
            transaction.begin();
        }
        try {
            return manager.find(MapSet.class, mapSetInternalId);
        } finally {
            if (startedHere && transaction.isActive()) {
                transaction.commit();
            }
        }
    }

    /**
     * Resolves branch path for map set operations from map_sets table.
     * For a specific map set: uses that map set. For general operations: uses last modified active map set.
     * Branch path is resolved by BranchService (stored or computed from components).
     *
     * @param service the terminology service
     * @param mapSetCode optional ref set code; if provided, uses that map set's branch
     * @return the branch path, or null if no map_sets records exist
     * @throws Exception the exception
     */
    public static String resolveBranchFromMapSets(final TerminologyService service, final String mapSetCode) throws Exception {

        final MapSet mapSet;
        if (mapSetCode != null && !mapSetCode.isBlank()) {
            mapSet = findMapSetByRefSetCode(service, mapSetCode);
        } else {
            final PfsParameter pfs = new PfsParameter();
            pfs.setSort("modified");
            pfs.setAscending(false);
            pfs.setLimit(1);
            final ResultList<MapSet> results = service.find("active:true", pfs, MapSet.class, null);
            mapSet = results.getItems().isEmpty() ? null : results.getItems().get(0);
        }
        return BranchService.getMapSetBranchPath(mapSet);
    }

    /**
     * Returns unique terminology/version pairs from active map sets.
     *
     * @param service the terminology service
     * @return map of terminology to version
     * @throws Exception the exception
     */
    public static Map<String, String> getTerminologyVersionsFromMapSets(final TerminologyService service) throws Exception {

        final Map<String, String> terminologyToVersion = new LinkedHashMap<>();
        final ResultList<MapSet> results = service.find("active:true", null, MapSet.class, null);
        for (final MapSet m : results.getItems()) {
            if (m.getFromTerminology() != null && !m.getFromTerminology().isBlank()
                && m.getFromVersion() != null && !m.getFromVersion().isBlank()) {
                terminologyToVersion.putIfAbsent(m.getFromTerminology(), m.getFromVersion());
            }
            if (m.getToTerminology() != null && !m.getToTerminology().isBlank()
                && m.getToVersion() != null && !m.getToVersion().isBlank()) {
                terminologyToVersion.putIfAbsent(m.getToTerminology(), m.getToVersion());
            }
        }
        return terminologyToVersion;
    }

    /**
     * Caches concepts for terminologies used in active map sets.
     * Uses the configured terminology handler; may be no-op for handlers that do not support caching.
     *
     * @param service the terminology service
     * @throws Exception the exception
     */
    public static void cacheConceptsForActiveMapSets(final TerminologyService service) throws Exception {

        if (terminologyHandler == null) {
            LOG.warn("Terminology handler not configured; skipping concept cache");
            return;
        }
        final Map<String, String> terminologyToVersion = getTerminologyVersionsFromMapSets(service);
        if (!terminologyToVersion.isEmpty()) {
            terminologyHandler.cacheConcepts(terminologyToVersion);
        }
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
     * Search map sets in the database (Hibernate Search).
     * Supports Lucene field queries such as {@code versionStatus:IN_DEVELOPMENT},
     * {@code versionStatus:IN DEVELOPMENT} (JSON label form), and {@code workflowStatus:IN_EDIT}.
     *
     * @param service the terminology service
     * @param searchParameters the search parameters
     * @return matching map sets
     * @throws Exception the exception
     */
    public static ResultList<MapSet> searchMapSets(final TerminologyService service, final SearchParameters searchParameters) throws Exception {

        LOG.info("Searching for MapSet with parameters: [{}]", searchParameters);

        final long start = System.currentTimeMillis();
        String query = (searchParameters != null && StringUtils.isNotBlank(searchParameters.getQuery())) ? searchParameters.getQuery() : "";

        // API/JSON expose VersionStatus labels (e.g. "IN_DEVELOPMENT"); the Lucene index stores enum names.
        query = normalizeVersionStatusQueryValues(query);

        final PfsParameter pfs = new PfsParameter();
        if (searchParameters != null) {
            if (Boolean.TRUE.equals(searchParameters.getActiveOnly())) {
                query = query.isEmpty() ? "active:true" : query + " AND active:true";
            }
            if (searchParameters.getOffset() != null) {
                pfs.setOffset(searchParameters.getOffset());
            }
            if (searchParameters.getLimit() != null) {
                pfs.setLimit(searchParameters.getLimit());
            }
            if (searchParameters.getSortAscending() != null) {
                pfs.setAscending(searchParameters.getSortAscending());
            }
            if (searchParameters.getSort() != null) {
                pfs.setSort(searchParameters.getSort());
            }
        }

        if (StringUtils.isNotBlank(query)) {
            query = IndexUtility.addWildcardsToQuery(query, MapSet.class);
        }

        LOG.info("Searching for MapSets with query: [{}]", query);

        final ResultList<MapSet> results = service.find(query, pfs, MapSet.class, null);
        results.setTimeTaken(System.currentTimeMillis() - start);
        results.setTotalKnown(true);

        return results;
    }

    /**
     * Replaces VersionStatus JSON labels in a Lucene query with enum names used by the index.
     *
     * @param query the raw query
     * @return query with labels normalized to enum names
     */
    private static String normalizeVersionStatusQueryValues(final String query) {

        if (StringUtils.isBlank(query)) {
            return query;
        }
        String normalized = query;
        for (final VersionStatus status : VersionStatus.values()) {
            if (!status.name().equals(status.getLabel())) {
                normalized = normalized.replace(status.getLabel(), status.name());
            }
        }
        return normalized;
    }

    /**
     * Get the map set member concepts in RF2 format.
     *
     * @param user the user
     * @param mapProject the map project
     * @param mapSetExportRequest the map set export request
     * @param mapSet the map set (required; paths must come from DB)
     * @return the refset member concepts
     * @throws Exception the exception
     */
    public static String exportMapSet(final User user, final MapProject mapProject, final MapSetExportRequest mapSetExportRequest, final MapSet mapSet)
        throws Exception {

        if (mapSet == null) {
            throw new IllegalArgumentException("MapSet is required for export. No fallback.");
        }
        return terminologyHandler.exportMapSet(user, mapProject, mapSetExportRequest, mapSet);
    }

    /**
     * Sets the workflow status.
     *
     * @param service the service
     * @param user the user
     * @param mapsetInternalId the mapset internal id
     * @param action the action
     * @param notes the notes
     * @return the refset
     * @throws Exception the exception
     */
    public static MapSet setWorkflowStatus(final TerminologyService service, final User user, final String mapsetInternalId, final WorkflowAction action,
        final String notes) throws Exception {

        MapSet mapset = MapSetService.getMapSet(service, user, mapsetInternalId);
        MapSetWorkflowService.canUserPerformWorkflowAction(user, mapset, action);
        final WorkflowStatus currentStatus = mapset.getWorkflowStatus();

        if (action == WorkflowAction.FINISH_EDIT) {
            service.add(AuditEntryHelper.addEditingCycleEntry(mapset, true));
        } else if (action == WorkflowAction.CANCEL_EDIT) {
            service.add(AuditEntryHelper.addEditingCycleEntry(mapset, false));
        } else if (action == WorkflowAction.CANCEL_UPGRADE) {
            RefsetMemberService.REFSETS_UPDATED_MEMBERS.remove(mapsetInternalId);
        }

        // If starting EDIT on a published map set, create a new version ready to be edited
        if (action == WorkflowAction.EDIT && (currentStatus == null || currentStatus == WorkflowStatus.PUBLISHED)) {

            final MapSet newMapSetVersion = MapSetService.createNewMapSetVersion(service, user, mapset.getId(), true);
            // need to commit previous transaction before reading to fetch mapset.
            service.commitClearBegin();
            mapset = MapSetService.getMapSet(service, user, newMapSetVersion.getId());

            if (!mapset.isBasedOnLatestVersion()) {
                RefsetService.REFSETS_TO_SHOW_UPGRADE_WARNING.add(newMapSetVersion.getId());
                mapset.setUpgradeWarning(true);
            }

            return mapset;
        }

        // Make action change to workflow status
        mapset = MapSetWorkflowService.setWorkflowStatusByAction(service, user, action, mapset, notes);

        // if the status changed return the updated refset else return null
        if (currentStatus == mapset.getWorkflowStatus()) {
            LOG.info("setWorkflowStatus: did not update workflow status.");
            return null;
        }

        return mapset;
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
        final MapSet mapSet = getMapSet(service, mapSetInternalId);

        // Check no existing IN_DEVELOPMENT version
        final ResultList<MapSet> results =
            service.find("versionStatus: (" + VersionStatus.IN_DEVELOPMENT.name() + ") AND refSetCode: " + QueryParserBase.escape(mapSet.getRefSetCode()),
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
        newMapSetVersion.setMapProject(mapSet.getMapProject());

        // Persist new version
        service.add(newMapSetVersion);

        // Create the Snowstorm branches
        BranchService.createRefsetBranch(newMapSetVersion.toBranchDetails());
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
