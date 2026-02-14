/*
 * Copyright 2025 West Coast Informatics - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of West Coast Informatics
 * The intellectual and technical concepts contained herein are proprietary to
 * West Coast Informatics and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
package org.ihtsdo.refsetservice.handler.snowstorm;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.handler.ExportHandler;
import org.ihtsdo.refsetservice.handler.snowstorm.export.MapSetExportDispatcher;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapSetEditHistory;
import org.ihtsdo.refsetservice.model.MapSetExportRequest;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.UpgradeInactiveConcept;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.model.enums.WorkflowAction;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.BranchService;
import org.ihtsdo.refsetservice.terminologyservice.MapSetService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetMemberService;
import org.ihtsdo.refsetservice.terminologyservice.MapSetWorkflowService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Class SnowstormMapset.
 */
@Component
public class SnowstormMapSet extends SnowstormAbstract {

    /** A cache of the sorted branch versions. */
    private static final Map<String, List<String>> BRANCH_VERSION_CACHE = new HashMap<>();

    /** A cache of the branches to use for refset searches. */
    private static final Set<String> BRANCH_SEARCH_CACHE = new HashSet<>();

    /** A cache of the unique refset IDs in the system. */
    private static final Set<String> UNIQUE_REFSET_IDS = new HashSet<>();

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(SnowstormMapSet.class);

    /**
     * Static export map set.
     *
     * @param user the user
     * @param mapProject the map project
     * @param mapSetExportRequest the map set export request
     * @return the string
     * @throws Exception the exception
     */
    public static String exportMapSet(final User user, final MapProject mapProject, final MapSetExportRequest mapSetExportRequest) throws Exception {

        final MapSetExportDispatcher exportDispatcher = new MapSetExportDispatcher(new ExportHandler());
        return exportDispatcher.exportMapSet(user, mapProject, mapSetExportRequest);
    }

    /**
     * Sets the workflow status.
     *
     * @param service the service
     * @param user the user
     * @param mapSetInternalId the map set internal id
     * @param action the action
     * @param notes the notes
     * @return the map set
     * @throws Exception the exception
     */
    public static MapSet setWorkflowStatus(final TerminologyService service, final User user, final String mapSetInternalId, final WorkflowAction action,
        final String notes) throws Exception {

        MapSet mapSet = MapSetService.getMapSetForWorkflow(service, mapSetInternalId);
        MapSetWorkflowService.canUserPerformWorkflowAction(user, mapSet, action);
        final WorkflowStatus currentStatus = mapSet.getWorkflowStatus();

        if (action == WorkflowAction.FINISH_EDIT) {
            service.add(AuditEntryHelper.addEditingCycleEntry(mapSet, true));
        } else if (action == WorkflowAction.CANCEL_EDIT) {
            service.add(AuditEntryHelper.addEditingCycleEntry(mapSet, false));
        } else if (action == WorkflowAction.CANCEL_UPGRADE) {
            RefsetMemberService.REFSETS_UPDATED_MEMBERS.remove(mapSetInternalId);
        }

        if (currentStatus == null || currentStatus == WorkflowStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "MapSet is in final/published state. Create a new version before editing.");
        }

        mapSet = MapSetWorkflowService.setWorkflowStatusByAction(service, user, action, mapSet, notes);

        if (currentStatus == mapSet.getWorkflowStatus()) {
            LOG.info("setWorkflowStatus: did not update workflow status.");
            return null;
        }

        return mapSet;
    }

    /**
     * Clear all MapSet caches.
     *
     * @param branchPath the branch path
     */
    public static void clearAllRefsetCaches(final String branchPath) {

        if (branchPath != null) {

            LOG.debug("clearAllRefsetCaches: Clearing caches for branch path: {}", branchPath);
            BRANCH_VERSION_CACHE.remove(branchPath);

        } else {

            LOG.debug("clearAllRefsetCaches: Clearing caches for all branches");
            BRANCH_VERSION_CACHE.clear();
        }

        BRANCH_SEARCH_CACHE.clear();
        UNIQUE_REFSET_IDS.clear();
    }

    /**
     * Gets the branch path.
     *
     * @param mapSet the map set
     * @return the branch path
     * @throws Exception the exception
     */
    public static String getBranchPath(final MapSet mapSet) throws Exception {

        String branchPath = "";
        String pathDate = "";

        if (!mapSet.isLocalSet()) {

            if (mapSet.getVersionDate() != null) {
                // Published version
                final Date tmpDate = mapSet.getVersionDate();
                pathDate = "/" + DateUtility.formatDate(tmpDate, DateUtility.DATE_FORMAT_REVERSE, null);
                branchPath = mapSet.getEditionBranch() + pathDate;
            } else {
                // In Development version
                if (Arrays.asList(WorkflowStatus.IN_EDIT, WorkflowStatus.IN_UPGRADE).contains(mapSet.getWorkflowStatus())) {
                    branchPath = BranchService.getEditBranchPath(mapSet.toBranchDetails());
                } else {
                    branchPath = BranchService.getRefsetBranchPath(mapSet.toBranchDetails());
                }
            }
        } else {

            // for localsets if it isn't being edited always pull from the refset branch
            if (Arrays.asList(WorkflowStatus.IN_EDIT, WorkflowStatus.IN_UPGRADE).contains(mapSet.getWorkflowStatus())) {
                branchPath = BranchService.getEditBranchPath(mapSet.toBranchDetails());
            } else {
                branchPath = BranchService.getRefsetBranchPath(mapSet.toBranchDetails());
            }
        }

        return branchPath;
    }

    /**
     * Gets the refset date from formatted string.
     *
     * @param publicationDateString the publication date string
     * @return the refset date from formatted string
     * @throws Exception the exception
     */
    public static Date getRefsetDateFromFormattedString(final String publicationDateString) throws Exception {

        return DateUtility.getDateWithNoTime(publicationDateString, DateUtility.DATE_FORMAT_REVERSE);
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

        boolean giveViewerRole = false;

        if (user.doesUserHavePermission(User.ROLE_AUTHOR, project)) {

            roles.add(User.ROLE_AUTHOR);
            giveViewerRole = true;
        }

        if (user.doesUserHavePermission(User.ROLE_REVIEWER, project)) {

            roles.add(User.ROLE_REVIEWER);
            giveViewerRole = true;
        }

        if (user.doesUserHavePermission(User.ROLE_ADMIN, project)) {

            roles.add(User.ROLE_ADMIN);
            giveViewerRole = true;
        }

        if (user.doesUserHavePermission(User.ROLE_VIEWER, project) || giveViewerRole) {

            roles.add(User.ROLE_VIEWER);
        }

        return roles;
    }

    /**
     * Removes the map set edit history.
     *
     * @param service the service
     * @param user the user
     * @param refsetCode the refset code
     * @throws Exception the exception
     */
    public static void removeMapSetEditHistory(final TerminologyService service, final String refsetCode) throws Exception {

        final ResultList<MapSetEditHistory> mapSetEditHistory =
            service.find("refsetId:" + QueryParserBase.escape(refsetCode) + "", null, MapSetEditHistory.class, null);

        if (mapSetEditHistory == null || mapSetEditHistory.getItems().isEmpty()) {

            return;
        }

        for (final MapSetEditHistory history : mapSetEditHistory.getItems()) {

            // update an object
            service.remove(history);
        }

        LOG.info("Map set {} edit history removed.", refsetCode);
    }

    /**
     * Sets the map set member count.
     *
     * @param service the service
     * @param mapSet the map set
     * @param force the force
     * @return true, if successful
     * @throws Exception the exception
     */
    public static boolean setMapSetMemberCount(final TerminologyService service, final MapSet mapSet, final boolean force) throws Exception {

        // if (mapSet.getMemberCount() == -1 || force) {
        //
        // LOG.debug("setMapSetMemberCount Setting the member count for refset: {}", mapSet.getId());
        // mapSet.setMemberCount(RefsetMemberService.getMemberCount(mapSet));
        //
        // // save the refset
        // service.update(mapSet);
        // return true;
        // }

        // TODO: Will this be needed?

        return false;
    }

    /**
     * Removes the upgrade data.
     *
     * @param service the service
     * @param mapSet the map set
     * @throws Exception the exception
     */
    public static void removeUpgradeData(final TerminologyService service, final MapSet mapSet) throws Exception {

        final ResultList<UpgradeInactiveConcept> results = service.find("refsetId: " + mapSet.getRefSetCode(), null, UpgradeInactiveConcept.class, null);
        for (final UpgradeInactiveConcept upgradeInactiveConcept : results.getItems()) {
            service.removeObject(upgradeInactiveConcept);
        }
    }

    /**
     * Replace refset with edit history.
     *
     * @param service the service
     * @param mapSet the map set
     * @throws Exception the exception
     */
    public static void replaceMapSetWithEditHistory(final TerminologyService service, final MapSet mapSet) throws Exception {

        LOG.debug("replaceRefsetWithEditHistory: refsetInternalId: {}", mapSet.getRefSetCode());

        final PfsParameter pfsParameter = new PfsParameter();
        pfsParameter.getSortFields().add("modified DESC");

        final ResultList<MapSetEditHistory> mapSetEditHistory =
            service.find("refsetId:" + QueryParserBase.escape(mapSet.getRefSetCode()) + "", null, MapSetEditHistory.class, null);

        if (mapSetEditHistory == null || mapSetEditHistory.getItems().isEmpty()) {

            return;
        }

        // TODO: Add unique Audit Entry Helper for this case

        final MapSetEditHistory history = mapSetEditHistory.getItems().get(0);

        mapSet.setRefSetCode(history.getRefSetCode());
        mapSet.setName(history.getName());
        // mapSet.setType(history.getType());
        // mapSet.setNarrative(history.getNarrative());
        mapSet.setVersionDate(history.getVersionDate());
        // mapSet.setVersionNotes(history.getVersionNotes());
        mapSet.setVersionStatus(history.getVersionStatus());
        // mapSet.setExternalUrl(history.getExternalUrl());
        mapSet.setModuleId(history.getModuleId());
        mapSet.setEditBranchId(null);
        // mapSet.setPrivateRefset(history.isPrivateRefset());
        // TODO: need tags? mapSet.setTags(new HashSet<String>(history.getTags()));
        mapSet.setWorkflowStatus(WorkflowStatus.READY_FOR_EDIT);
        mapSet.setAssignedUser(null);
        // mapSet.setMemberCount(history.getMemberCount());

        service.update(mapSet);

        LOG.info("Map Set {} replaced with edit history", mapSet.getRefSetCode());
        LOG.debug("replaceRefsetWithEditHistory: Refset: {}", ModelUtility.toJson(mapSet));
    }

    /**
     * Sets the refset permissions.
     *
     * @param user the user
     * @param mapSet the map set
     * @return the map set
     * @throws Exception the exception
     */
    public static MapSet setMapSetPermissions(final User user, final MapSet mapSet) throws Exception {

        // TODO: permissions to be reviewed.


        // mapSet.setAvailableActions(MapSetWorkflowService.getAllowedActions(user, mapSet));
        //
        // final Project project = mapSet.getProject();
        // final List<String> roles = mapSet.getRoles();
        // boolean userCanView = true;
        //
        // setRoles(user, project, roles);
        // project.setRoles(roles);
        //
        // // make sure the user is allowed to view this refset
        // if (project.isPrivateProject() && !project.getRoles().contains(User.ROLE_VIEWER)) {
        // userCanView = false;
        //
        // } else if (!project.getRoles().contains(User.ROLE_VIEWER) && mapSet.getVersionStatus().equals(VersionStatus.IN_DEVELOPMENT)) {
        // userCanView = false;
        // }
        //
        // if (!userCanView) {
        //
        // final String message = "User does not have permission to view this reference set.";
        // LOG.error(message);
        // throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
        // }

        return mapSet;
    }
}
