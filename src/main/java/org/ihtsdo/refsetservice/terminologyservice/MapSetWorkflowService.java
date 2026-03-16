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

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.NotAuthorizedException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.commons.cli.MissingArgumentException;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.handler.snowstorm.SnomedConstants;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.model.BranchInformation;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapSetWorkflowHistory;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.model.enums.WorkflowAction;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.model.snowstorm.CodeSystem;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.FieldedStringTokenizer;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class for workflow processes.
 */
public final class MapSetWorkflowService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(MapSetWorkflowService.class);

    /** The order of workflow steps . */
    private static final List<WorkflowStatus> WORKFLOW_STATUSES = WorkflowStatus.getValues();

    /** The file that contains workflow actions by user and step. */
    private static final String WORKFLOW_PERMUTATIONS_FILE_NAME = "workflow/workflowPermutationsToFinalAction.txt";

    /** The workflow actions by user and step. */
    // Map<user, Map<currentStatus, Map<action, resultingState>>>
    private static final Map<String, Map<WorkflowStatus, Map<WorkflowAction, WorkflowStatus>>> WORKFLOW_PERMUTATIONS = new HashMap<>();

    static {
        try {
            // read in the actions by user and step
            final ClassPathResource workflowPermutationsResource = new ClassPathResource(WORKFLOW_PERMUTATIONS_FILE_NAME);

            try (final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(workflowPermutationsResource.getInputStream()))) {

                String line;
                while ((line = bufferedReader.readLine()) != null) {

                    if (line.startsWith("#") || StringUtils.isBlank(line)) {
                        continue;
                    }

                    final String[] tokens = FieldedStringTokenizer.split(line, ",");

                    if (tokens.length != 4) {
                        throw new Exception(WORKFLOW_PERMUTATIONS_FILE_NAME + " does not have 4 items per line");
                    }

                    final String user = tokens[0].toUpperCase().strip();
                    final WorkflowStatus currentState = WorkflowStatus.fromString(tokens[1].toUpperCase().strip());
                    final WorkflowAction action = WorkflowAction.fromString(tokens[2].toUpperCase().strip());
                    final WorkflowStatus resultingState = WorkflowStatus.fromString(tokens[3].toUpperCase().strip());

                    if (!WORKFLOW_PERMUTATIONS.containsKey(user)) {
                        WORKFLOW_PERMUTATIONS.put(user, new HashMap<WorkflowStatus, Map<WorkflowAction, WorkflowStatus>>());
                    }

                    if (!WORKFLOW_PERMUTATIONS.get(user).containsKey(currentState)) {
                        WORKFLOW_PERMUTATIONS.get(user).put(currentState, new HashMap<>());
                    }

                    WORKFLOW_PERMUTATIONS.get(user).get(currentState).put(action, resultingState);
                }
            }

        } catch (final Exception e) {
            throw new RuntimeException(" Unable to read worflow file: " + e.getMessage());
        }

    }

    /**
     * Instantiates an empty {@link MapSetWorkflowService}.
     */
    private MapSetWorkflowService() {

        // n/a
    }

    /**
     * Start the publication of all Ready for Publication mapSets in a code system by promoting them to the REFSETS branch.
     *
     * @param service the Terminology Service
     * @param editionShortName an code system to limit the mapSet to
     * @param authUser the auth user
     * @return A list of concepts that were unable to be promoted
     * @throws Exception the exception
     */
    public static List<String> startMapSetPublications(final TerminologyService service, final String editionShortName, final User authUser) throws Exception {

        final List<String> mapSetsNotUpdated = new ArrayList<>();
        final String query = "workflowStatus: " + WorkflowStatus.READY_FOR_PUBLICATION + " AND editionShortName: " + editionShortName + " AND localSet: false";

        final ResultList<MapSet> results = service.find(query, null, MapSet.class, null);

        if (results.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.EXPECTATION_FAILED,
                "There are no Reference sets in " + editionShortName + " that are ready to be published");
        }

        final String editionBranch = results.getItems().get(0).getEditionBranch();
        final String projectBranchPath = BranchService.getProjectBranchPath(editionBranch);

        // rebase the project branch from the edition branch
        BranchService.mergeBranch(editionBranch, projectBranchPath, "Rebasing project branch to latest changes", true);

        // see if there is an "In Development" version as that should be the latest.
        for (final MapSet mapSet : results.getItems()) {

            try {

                final String mapSetBranchPath = BranchService.getRefsetBranchPath(mapSet.toBranchDetails());

                BranchService.mergeBranch(projectBranchPath, mapSetBranchPath, "Rebasing mapSet branch to latest changes", true);

                final boolean success =
                    BranchService.promoteRefsetToProjectBranch(mapSet.toBranchDetails(), "Preparing for publication - promoting mapSet to project branch");

                if (!success) {

                    final String message = "Unable to promote Reference set into project branch for mapSet " + mapSet.getRefSetCode()
                        + " because the project branch doesn't exist.";
                    LOG.error(message);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
                }

                setWorkflowStatusByAction(service, authUser, WorkflowAction.START_PUBLISH, mapSet, "Preparing for publication.");

            } catch (final Exception e) {

                LOG.error("Unable to promote Reference Set " + mapSet.getRefSetCode() + " into project branch because: " + e.getMessage(), e);
                mapSetsNotUpdated.add(mapSet.getRefSetCode());
            }
        }

        return mapSetsNotUpdated;
    }

    /**
     * Complete the publication of all Ready for Publication mapSets.
     *
     * @param service the Terminology Service
     * @param editionShortName an code system to limit the mapSet to
     * @param user the user
     * @return A list of concepts that were unable to have publication completed
     * @throws Exception the exception
     */
    public static String completeEditionPublication(final TerminologyService service, final String editionShortName, final User user) throws Exception {

        if (!user.checkPermission(User.ROLE_ADMIN, "all", null, null)) {
            throw new NotAuthorizedException("This user does not have permission to perform this action");
        }

        if (StringUtility.isEmpty(editionShortName)) {
            throw new MissingArgumentException("A Code System must be specified.");
        }

        final Edition edition = service.findSingle("shortName:" + editionShortName, Edition.class, null);
        if (edition == null) {
            throw new MissingArgumentException("The code system '" + editionShortName + "' could not be found");
        }
        if (StringUtils.isBlank(edition.getBranch())) {
            throw new MissingArgumentException("editions.branch is required for edition " + editionShortName + ". Database is missing required path data.");
        }
        final String branchPath = edition.getBranch();

        final String query = "workflowStatus: " + WorkflowStatus.IN_PUBLICATION + " AND editionShortName: " + editionShortName;

        // Find snowstorm's version date of the latest version of all mapSets in ready_to_published state. Ensure all identical
        final List<String> mapSetsNotUpdated = new ArrayList<>();

        final ResultList<MapSet> results = service.find(query, null, MapSet.class, null);
        if (results.getItems().isEmpty()) {

            throw new ResponseStatusException(HttpStatus.EXPECTATION_FAILED,
                "There are no reference sets in " + editionShortName + " that are ready to be published");
        }

        if (!BranchService.doesBranchExist(branchPath)) {
            throw new MissingArgumentException("The version branch '" + branchPath
                + "' does not exist. This must be created and populated with the reference sets to be versioned outside of this tool "
                + "before this publication completion process can be run.");
        }

        final CodeSystem editionCodeSystem = EditionService.getCodeSystem(editionShortName);
        if (editionCodeSystem == null || editionCodeSystem.getLatestVersion() == null) {
            LOG.error("completeEditionPublication: The code system '{}' is not complete in the terminology server.", editionShortName);
            LOG.error("completeEditionPublication {}", ModelUtility.toJson(editionCodeSystem));
            throw new MissingArgumentException("The code system '" + editionShortName + "' is not complete in the terminology server.");
        }
        final int dateAsInt = editionCodeSystem.getLatestVersion().getEffectiveDate();
        final String dateAsString = String.valueOf(dateAsInt);

        // Parse the string to a Date object
        final SimpleDateFormat inputFormat = new SimpleDateFormat(DateUtility.DATE_FORMAT_REVERSE_ONLY_NUMBERS);
        final Date date = inputFormat.parse(dateAsString);

        // Format the Date object to the desired string format
        final SimpleDateFormat outputFormat = new SimpleDateFormat(DateUtility.DATE_FORMAT_REVERSE);
        final String latestVersion = outputFormat.format(date);

        LOG.info("completeRefsetPublications: versionDate: " + latestVersion + " ; editionShortName (editionShortName): " + editionShortName);

        // see if there is an "In Development" version as that should be the latest.
        for (final MapSet mapSet : results.getItems()) {
            mapSetsNotUpdated.addAll(completeRefsetPublication(service, mapSet, latestVersion));
        }

        String error = "";
        // see if there are any mapSets that were unable to be updated and craft the error message
        if (!mapSetsNotUpdated.isEmpty()) {

            error = "Unable to complete publication for reference sets in code system " + editionShortName + ": ";
            for (final String mapSetNotUpdated : mapSetsNotUpdated) {
                error += mapSetNotUpdated + ", ";
            }

            error = StringUtils.removeEnd(error, ", ");
        }

        if (!error.isEmpty()) {
            throw new RuntimeException(error);
        }

        final String message = "All reference set publications completed in code system " + editionShortName;

        return message;
    }

    /**
     * Complete the publication of a mapSet.
     *
     * @param service the Terminology Service
     * @param mapSet the mapSet
     * @param publicationDateString the publication date of the mapSet in yyyy-MM-dd format
     * @return A list of mapSets that were unable to have publication completed
     * @throws Exception the exception
     */
    public static List<String> completeRefsetPublication(final TerminologyService service, final MapSet mapSet, final String publicationDateString)
        throws Exception {

        final List<String> mapSetsNotUpdated = new ArrayList<>();

        try {

            // Update the previously published version to no long be latest
            final MapSet previouslyPublishedVersion =
                service.findSingle("mapsetId:" + QueryParserBase.escape(mapSet.getRefSetCode()) + " AND latestPublishedVersion: true", MapSet.class, null);

            LOG.info("1 {} ", !mapSet.isLocalSet() && mapSet.getWorkflowStatus() != WorkflowStatus.IN_PUBLICATION);
            LOG.info("2 {} ", mapSet.isLocalSet() && mapSet.getWorkflowStatus() != WorkflowStatus.READY_FOR_PUBLICATION);

            if (!mapSet.isLocalSet() && mapSet.getWorkflowStatus() != WorkflowStatus.IN_PUBLICATION
                || (mapSet.isLocalSet() && mapSet.getWorkflowStatus() != WorkflowStatus.READY_FOR_PUBLICATION)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reference set is not in the proper status to have publication completed " + mapSet.getRefSetCode());
            }

            final String mapSetBranchPath = MapSetService.getBranchPath(mapSet);

            if (mapSet.isLocalSet()) {

                final String topLevelRefsetBranchPath = BranchService.getLocalsetTopLevelRefsetBranchPath(mapSet.getEditionBranch(), mapSet.getRefSetCode());
                BranchService.mergeBranch(mapSetBranchPath, topLevelRefsetBranchPath, "Promoting versioned local set", false);
                LOG.info("Publication (non-snomed versioning) of localset Reference Set: " + mapSet.getRefSetCode());
            } else {
                LOG.info("Publication of mapSet: {}", mapSet.getRefSetCode());
            }

            final Date publicationDate = MapSetService.getRefsetDateFromFormattedString(publicationDateString);

            /*
             * if (mapSet.getVersionDate() != null && !mapSet.getVersionDate().before(publicationDate)) { throw new Exception( "Can't publish on " +
             * publicationDate + " when that's earlier than the mapSet's existing versionDate: " + mapSet.getVersionDate()); }
             */

            mapSet.setVersionDate(publicationDate);
            mapSet.setInternationalContentVersion(EditionService.getDependencyModuleNameFromBranchMetadata(mapSet.getEditionShortName(), mapSet.getModuleId(),
                publicationDate.getTime(), mapSetBranchPath));

            // if (!mapSet.getEdition().isDerivative()) {
            //
            // mapSet.setBaseContentVersion(publicationDateString + mapSet.getBaseContentVersion().substring(mapSet.getBaseContentVersion().indexOf(" ")));
            //
            // } else {
            // if the edition is a derivative (i.e. from Snomed International Edition, version date, content version and international content
            // version are the same.
            final String internationalContentVersionDate =
                mapSet.getInternationalContentVersion().substring(0, mapSet.getInternationalContentVersion().indexOf(" "));
            mapSet
                .setBaseContentVersion(internationalContentVersionDate + mapSet.getBaseContentVersion().substring(mapSet.getBaseContentVersion().indexOf(" ")));
            // }

            mapSet.setWorkflowStatus(WorkflowStatus.PUBLISHED);
            mapSet.setVersionStatus(VersionStatus.PUBLISHED);
            mapSet.setLatestPublishedVersion(true);

            service.update(mapSet);
            service.add(AuditEntryHelper.completeMapSetPublicationEntry(mapSet));

            if (!mapSet.getWorkflowStatus().equals(WorkflowStatus.PUBLISHED)) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Reference set was not able to have publication completed " + mapSet.getRefSetCode() + " on " + mapSet.getVersionDate());
            }

            if (previouslyPublishedVersion != null) {

                previouslyPublishedVersion.setLatestPublishedVersion(false);
                previouslyPublishedVersion.setHasVersionInDevelopment(false);

                service.update(previouslyPublishedVersion);

                LOG.info("Refset {} version marked as not latest.", previouslyPublishedVersion.getId());
            }

        } catch (final Exception e) {

            LOG.error("Completing Reference set Publication failed: {}", e.getMessage(), e);
            mapSetsNotUpdated.add(mapSet.getRefSetCode());
        }

        return mapSetsNotUpdated;
    }

    /**
     * Set workflow status for a number of mapSets at once.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param mapSetIds a comma separated list of mapSet IDs
     * @param action the action the user took
     * @param notes the workflow status notes
     * @return A list of concepts that were unable to have their status updated
     * @throws Exception the exception
     */
    public static List<String> setBatchWorkflowStatusByAction(final TerminologyService service, final User user, final String mapSetIds,
        final WorkflowAction action, final String notes) throws Exception {

        final List<String> mapSetsNotUpdated = new ArrayList<>();

        final ResultList<MapSet> results =
            service.find("mapsetId:(" + mapSetIds.replace(",", " OR ") + ") AND versionStatus: (" + VersionStatus.IN_DEVELOPMENT.toString() + ")",
                new PfsParameter(), MapSet.class, null);

        for (final MapSet mapSet : results.getItems()) {

            try {

                final WorkflowStatus currentStatus = mapSet.getWorkflowStatus();
                setWorkflowStatusByAction(service, user, action, mapSet, notes);

                if (currentStatus == mapSet.getWorkflowStatus()) {
                    mapSetsNotUpdated.add(mapSet.getRefSetCode());
                } else {
                    MapSetService.clearAllRefsetCaches(mapSet.getEditionBranch());
                }

            } catch (final Exception e) {

                mapSetsNotUpdated.add(mapSet.getRefSetCode());
            }

        }

        return mapSetsNotUpdated;
    }

    /**
     * Set the workflow status for the mapSet.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param action the action
     * @param mapSet the mapSet
     * @param notes the workflow status notes
     * @param nextStatus the new workflow status
     * @param assignedUser the user the mapSet is assigned to, or null
     * @return the updated mapSet
     * @throws Exception the exception
     */
    public static MapSet setWorkflowStatus(final TerminologyService service, final User user, final WorkflowAction action, final MapSet mapSet,
        final String notes, final WorkflowStatus nextStatus, final String assignedUser) throws Exception {

        final MapSet updatedMapSet = setRefsetWorkflowStatus(service, user, mapSet, nextStatus, assignedUser);
        addWorkflowHistory(service, user, action, mapSet, notes);
        return updatedMapSet;

    }

    /**
     * Set the workflow status for the mapSet based on the action the user took.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param action the action the user took
     * @param mapSet the mapSet
     * @param notes the workflow status notes
     * @return the updated mapSet
     * @throws Exception the exception
     */
    public static MapSet setWorkflowStatusByAction(final TerminologyService service, final User user, final WorkflowAction action, final MapSet mapSet,
        final String notes) throws Exception {

        canUserPerformWorkflowAction(user, mapSet, action);

        final WorkflowStatus currentStatus = mapSet.getWorkflowStatus();
        boolean restoreHistory = false;
        final List<String> roles = MapSetService.setRoles(user, mapSet.getProject(), new ArrayList<>());

        // get the next status based on the user, current status, and supplied action
        // WHY IS THIS FAILING??? LOG.info("WORKFLOW_PERMUTATIONS: " + ModelUtility.toJson(WORKFLOW_PERMUTATIONS));

        WorkflowStatus nextStatus = null;
        String assignedUser = null;

        // loop thru the roles to find a match for the action and current status. !! This only works if any multiple matches between role, current status, and
        // action go to the
        // same next status !!
        for (final String role : roles) {

            if (WORKFLOW_PERMUTATIONS.containsKey(role) && WORKFLOW_PERMUTATIONS.get(role).containsKey(mapSet.getWorkflowStatus())) {

                final WorkflowStatus possibleStatus = WORKFLOW_PERMUTATIONS.get(role).get(mapSet.getWorkflowStatus()).get(action);

                if (possibleStatus != null) {

                    nextStatus = possibleStatus;
                    break;
                }

            }

        }

        if (Arrays.asList(WorkflowAction.EDIT, WorkflowAction.UPGRADE, WorkflowAction.REVIEW).contains(action)) {

            assignedUser = user.getUserName();
        }

        final List<String> mapSetBranchVersions = MapSetService.getBranchVersions(mapSet.getEditionBranch());

        LOG.info("currentStatus: " + currentStatus + " ; nextStatus: " + nextStatus);

        // if edits have just been completed then merge the edit branch into the mapSet branch and delete the edit branch
        if ((currentStatus.equals(WorkflowStatus.IN_EDIT)
            && Arrays.asList(WorkflowAction.FINISH_EDIT, WorkflowAction.REQUEST_REVIEW, WorkflowAction.REQUEST_PUBLICATION).contains(action))
            || (currentStatus.equals(WorkflowStatus.IN_UPGRADE) && Arrays.asList(WorkflowAction.FINISH_UPGRADE).contains(action))) {

            final boolean success = BranchService.promoteEditIntoRefsetBranch(mapSet.toBranchDetails(), notes);

            if (success) {

                mapSet.setEditBranchId(null);
                if (currentStatus.equals(WorkflowStatus.IN_UPGRADE) && Arrays.asList(WorkflowAction.FINISH_UPGRADE).contains(action)) {
                    RefsetMemberService.clearAllMemberCaches(BranchService.getRefsetBranchPath(mapSet.toBranchDetails()));
                }

                MapSetService.removeMapSetEditHistory(service, mapSet.getRefSetCode());

            } else {

                final String message =
                    "Unable to promote edit into Reference Set branch for " + mapSet.getRefSetCode() + " because the edit branch doesn't exist.";
                LOG.error(message);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
            }

            // Set Upgrade Content Versions
            if (currentStatus.equals(WorkflowStatus.IN_UPGRADE) && Arrays.asList(WorkflowAction.FINISH_UPGRADE).contains(action)) {
                if (mapSet.isInUpgrade()) {

                    if (mapSetBranchVersions != null && !mapSetBranchVersions.isEmpty()) {
                        Collections.sort(mapSetBranchVersions, Comparator.reverseOrder());

                        if ("Never Published".equals(mapSetBranchVersions.get(0))) {

                            final String tempDate = EditionService.getEditionDependentVersion(mapSet.getEditionShortName(), user);
                            final String version = tempDate.substring(0, 4) + "-" + tempDate.substring(4, 6) + "-" + tempDate.substring(6, 8);
                            mapSet.setBaseContentVersion(version + " " + mapSet.getEditionShortName());
                            mapSet.setInternationalContentVersion(identifyInternationalContentVersionForUpgrade(mapSet));

                        } else {
                            // Update Base to last published version of mapSet
                            mapSet.setBaseContentVersion(mapSetBranchVersions.get(0) + " " + mapSet.getEditionShortName());
                            // Update International to dependencyRelease version of mapSet
                            mapSet.setInternationalContentVersion(identifyInternationalContentVersionForUpgrade(mapSet));
                        }
                    }
                }

                // If finishing upgrade/inactivate process, reset process flag
                if (mapSet.isInInactivate()) {
                    mapSet.setInInactivate(false);
                } else if (mapSet.isInUpgrade()) {
                    mapSet.setInUpgrade(false);
                }
            }
        }

        else if ((currentStatus.equals(WorkflowStatus.IN_EDIT) && Arrays.asList(WorkflowAction.CANCEL_EDIT).contains(action))
            || (currentStatus.equals(WorkflowStatus.IN_UPGRADE) && Arrays.asList(WorkflowAction.CANCEL_UPGRADE).contains(action))) {

            RefsetMemberService.clearAllMemberCaches(BranchService.getEditBranchPath(mapSet.toBranchDetails()));

            mapSet.setEditBranchId(null);
            restoreHistory = true;

            if (mapSet.isInInactivate()) {
                mapSet.setInInactivate(false);
            } else if (mapSet.isInUpgrade()) {
                mapSet.setInUpgrade(false);
            }

        }

        // else if this is the start of edits create the mapSet edit branch
        else if (action == WorkflowAction.EDIT || action == WorkflowAction.UPGRADE) {

            if (currentStatus != WorkflowStatus.IN_UPGRADE) {

                // Ensure the mapSet branch exists on Snowstorm before creating the edit branch
                if (StringUtils.isEmpty(mapSet.getRefsetBranchId())) {
                    mapSet.setMapBranchId(BranchService.generateBranchId());
                }
                final BranchInformation branchInfo = mapSet.toBranchDetails();
                branchInfo.setBranchId(mapSet.getRefsetBranchId());
                final String refsetBranchPath = BranchService.getRefsetBranchPath(branchInfo);
                if (!BranchService.doesBranchExist(refsetBranchPath)) {
                    BranchService.createRefsetBranch(branchInfo);
                }

                // Upgrade creates or merges the edit branch separately
                if (StringUtils.isEmpty(mapSet.getEditBranchId())) {
                    final String branchId = BranchService.generateBranchId();
                    mapSet.setEditBranchId(branchId);
                    BranchService.createEditBranch(mapSet.toBranchDetails(), branchId);
                }

                BranchService.rebaseRefsetBranchContents(mapSet.toBranchDetails());
                BranchService.rebaseEditBranchContents(mapSet.toBranchDetails());
            }

            MapSetService.setMapSetMemberCount(service, mapSet, true);
        }

        // Clear upgrade and module dependency caches given changes that may have occured during process
        if (currentStatus == WorkflowStatus.IN_UPGRADE) {
            // TODO: DOES MapSet require this?
            // RefsetMemberService.removeUpgradeData(service, user, mapSet);
            EditionService.clearModuleDependencyCaches(mapSet.getModuleId());
        }

        setWorkflowStatus(service, user, action, mapSet, notes, nextStatus, assignedUser);

        if (restoreHistory) {

            MapSetService.replaceMapSetWithEditHistory(service, mapSet);
            MapSetService.removeMapSetEditHistory(service, mapSet.getRefSetCode());
        }

        return mapSet;
    }

    /**
     * Update the mapSet to a new workflow status.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param mapSet the mapSet
     * @param status the new workflow status
     * @param assignedUser the user the mapSet is assigned to, or null
     * @return the updated mapSet
     * @throws Exception the exception
     */
    public static MapSet setRefsetWorkflowStatus(final TerminologyService service, final User user, final MapSet mapSet, final WorkflowStatus status,
        final String assignedUser) throws Exception {

        final long start = System.currentTimeMillis();

        mapSet.setWorkflowStatus(status);
        mapSet.setAssignedUser(assignedUser);

        // Published is the final status so set the version information
        if (status.equals(WorkflowStatus.PUBLISHED)) {

            // get the latest edition version branch
            final List<String> branchVersions = MapSetService.getBranchVersions(mapSet.getEditionBranch());

            if (branchVersions.isEmpty()) {

                final String message = "Could not retrieve branch versions for branch " + mapSet.getEditionBranch();
                LOG.error(message);
                throw new Exception(message);
            }

            final String newVersion = branchVersions.get(0);

            mapSet.setVersionDate(MapSetService.getRefsetDateFromFormattedString(newVersion));
            mapSet.setVersionStatus(VersionStatus.PUBLISHED);
        }

        // Update an object
        service.update(mapSet);
        LOG.info("Reference Set workflow status set to {} for Reference Set {}. Time {}", status, mapSet.getId(), (System.currentTimeMillis() - start));

        // update the mapSet permissions
        return MapSetService.setMapSetPermissions(user, mapSet);
    }

    /**
     * Add an entry in the workflow history table.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param action the action
     * @param mapSet the mapSet
     * @param notes the workflow status notes
     * @throws Exception the exception
     */
    public static void addWorkflowHistory(final TerminologyService service, final User user, final WorkflowAction action, final MapSet mapSet,
        final String notes) throws Exception {

        final long start = System.currentTimeMillis();

        final MapSetWorkflowHistory workflow = new MapSetWorkflowHistory(user.getUserName(), mapSet.getWorkflowStatus(), action, notes, mapSet);

        // Add an object
        service.add(workflow);
        service.add(AuditEntryHelper.addWorkflowHistoryEntry(workflow, mapSet, workflow.getWorkflowStatus()));
        final String newWorkflowId = workflow.getId();

        if (newWorkflowId == null) {

            throw new Exception("Unable to create a new workflow history entry.");
        }

        LOG.info("New workflow history entry with status {} added for Reference Set  {}. Time:  {}", mapSet.getWorkflowStatus(), mapSet.getId(),
            (System.currentTimeMillis() - start));
    }

    /**
     * Get the current workflow for a mapSet.
     *
     * @param service the Terminology Service
     * @param mapSet the mapSet
     * @return the current workflow object
     * @throws Exception the exception
     */
    public static MapSetWorkflowHistory getCurrentWorkflow(final TerminologyService service, final MapSet mapSet) throws Exception {

        final PfsParameter pfs = new PfsParameter();
        pfs.setSort("modified");
        pfs.setAscending(false);
        pfs.setLimit(1);

        final ResultList<MapSetWorkflowHistory> results =
            service.find("mapsetId:" + QueryParserBase.escape(mapSet.getId()) + "", pfs, MapSetWorkflowHistory.class, null);

        if (results.getItems().isEmpty()) {

            throw new Exception("Unable to retrieve worflow for Reference Set " + mapSet.getRefSetCode() + " on " + mapSet.getVersionDate());
        }

        return results.getItems().get(0);
    }

    /**
     * Get the workflow history for a mapSet.
     *
     * @param service the Terminology Service
     * @param mapSet the mapSet
     * @param searchParameters the search parameters
     * @return the current workflow object
     * @throws Exception the exception
     */
    public static ResultList<MapSetWorkflowHistory> getWorkflowHistory(final TerminologyService service, final MapSet mapSet,
        final SearchParameters searchParameters) throws Exception {

        final PfsParameter pfs = new PfsParameter();
        String query = "";

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

        if (searchParameters.getQuery() != null) {
            query = " AND " + IndexUtility.addWildcardsToQuery(searchParameters.getQuery(), MapSetWorkflowHistory.class);
        }

        final ResultList<MapSetWorkflowHistory> results =
            service.find("mapsetId:" + QueryParserBase.escape(mapSet.getId()) + query, pfs, MapSetWorkflowHistory.class, null);

        // LOG.info("getWorkflowHistory results: " + ModelUtility.toJson(results));

        return results;
    }

    /**
     * Update the notes for the current workflow status.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param mapSet the mapSet
     * @param notes the notes
     * @throws Exception the exception
     */
    public static void updateWorkflowNote(final TerminologyService service, final User user, final MapSet mapSet, final String notes) throws Exception {

        final MapSetWorkflowHistory workflow = getCurrentWorkflow(service, mapSet);

        workflow.setNotes(notes);

        // Update an object
        service.update(workflow);
        service.add(AuditEntryHelper.updateWorkflowNoteEntry(workflow, mapSet));
        LOG.info("Note for workflow history entry with status {} updated for mapSet {}", mapSet.getWorkflowStatus(), mapSet.getId());
    }

    /**
     * Get the current assigned username.
     *
     * @param service the Terminology Service
     * @param mapSet the mapSet
     * @return the current assigned username
     * @throws Exception the exception
     */
    public static String getAssignedUserName(final TerminologyService service, final MapSet mapSet) throws Exception {

        // if the mapSet isn't being edited or reviewed no one is assigned
        if (!Arrays.asList(WorkflowStatus.IN_EDIT, WorkflowStatus.IN_REVIEW).contains(mapSet.getWorkflowStatus())) {

            return "";
        }

        final MapSetWorkflowHistory workflow = getCurrentWorkflow(service, mapSet);
        LOG.info("getAssignedUserName: {}", workflow.getUserName());
        return workflow.getUserName();
    }

    /**
     * In order to create a mapSet branch for a new mapSet the SCTID needs to get generated in a temp branch first.
     *
     * @param editionBranchPath the branch path of the temporary branch
     * @return the branch path of the new branch
     * @throws Exception the exception
     */
    public static String getNewRefsetId(final String editionBranchPath) throws Exception {

        String mapSetConceptId = null;
        final String projectBranchPath = BranchService.getProjectBranchPath(editionBranchPath);
        String tempBranchPath = null;

        if (!BranchService.doesBranchExist(projectBranchPath)) {
            BranchService.createBranch(editionBranchPath, BranchService.getProjectBranchName(editionBranchPath));
        }

        if (BranchService.doesBranchExist(projectBranchPath + "/" + BranchService.TEMP_BRANCH_NAME)) {
            tempBranchPath = projectBranchPath + "/" + BranchService.TEMP_BRANCH_NAME;
        } else {
            tempBranchPath = BranchService.createBranch(projectBranchPath, BranchService.TEMP_BRANCH_NAME);
        }

        final long start = System.currentTimeMillis();
        final String url = SnowstormConnection.getBaseUrl() + "browser/" + tempBranchPath + "/" + "concepts/";

        LOG.info("getNewRefsetId URL: " + url);

        try (final Response response = SnowstormConnection.postResponse(url, "{}")) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception("Call to URL '" + url + "' wasn't successful. Message: " + formatErrorMessage(response));
            }

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                throw new Exception(Integer.toString(response.getStatus()));
            }

            final String resultString = SnowstormConnection.readEntityAsString(response);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString);
            final JsonNode conceptNode = root;

            if (conceptNode.has("conceptId")) {
                mapSetConceptId = conceptNode.get("conceptId").asText();
            }

            BranchService.deleteBranch(tempBranchPath);
        }

        if (StringUtils.isEmpty(mapSetConceptId)) {
            throw new Exception("Unable to create new mapSet concept.");
        }

        LOG.info("New Refset ID {}. Time: {}", mapSetConceptId, (System.currentTimeMillis() - start));
        return mapSetConceptId;
    }

    /**
     * Get the next workflow status from the current one.
     *
     * @param currentStatus the current workflow status
     * @return if next workflow status, or null if at final status
     */
    public static WorkflowStatus getNextWorkflowStatus(final WorkflowStatus currentStatus) {

        // Published is the final status
        if (currentStatus.equals(WorkflowStatus.PUBLISHED)) {

            return null;
        }

        final int currentStep = WORKFLOW_STATUSES.indexOf(currentStatus);
        return WORKFLOW_STATUSES.get(currentStep + 1);
    }

    /**
     * Get a list of workflow statuses that are allowed for the current user and state of the mapSet.
     *
     * @param user the user
     * @param mapSet the mapSet
     * @return the list of allowed statuses
     * @throws Exception the exception
     */
    public static List<WorkflowStatus> getAllowedStatuses(final User user, final MapSet mapSet) throws Exception {

        final List<WorkflowStatus> allowedStatuses = new ArrayList<>();
        final WorkflowStatus currentStatus = mapSet.getWorkflowStatus();
        final Project project = mapSet.getProject();

        // Authors can start an edit cycle on Published mapSets
        if (mapSet.getVersionStatus() == VersionStatus.PUBLISHED) {

            if (user.doesUserHavePermission(User.ROLE_AUTHOR, project)) {

                allowedStatuses.add(WorkflowStatus.READY_FOR_EDIT);
                allowedStatuses.add(WorkflowStatus.IN_EDIT);
                allowedStatuses.add(WorkflowStatus.IN_UPGRADE);
            }

            return allowedStatuses;
        }

        // only the assigned user can edit or review
        if (!user.getUserName().equals(mapSet.getAssignedUser())
            && Arrays.asList(WorkflowStatus.IN_EDIT, WorkflowStatus.IN_UPGRADE, WorkflowStatus.IN_REVIEW).contains(currentStatus)) {

            return allowedStatuses;
        }

        // set status permissions for AUTHORS
        if (user.doesUserHavePermission(User.ROLE_AUTHOR, project)) {

            if (Arrays.asList(WorkflowStatus.IN_EDIT, WorkflowStatus.IN_UPGRADE, WorkflowStatus.REVIEW_COMPLETED, WorkflowStatus.READY_FOR_PUBLICATION)
                .contains(currentStatus)) {

                allowedStatuses.add(WorkflowStatus.READY_FOR_EDIT);
            }

            if (Arrays.asList(WorkflowStatus.READY_FOR_EDIT, WorkflowStatus.READY_FOR_REVIEW, WorkflowStatus.REVIEW_COMPLETED).contains(currentStatus)) {

                allowedStatuses.add(WorkflowStatus.IN_EDIT);
            }

            if (Arrays.asList(WorkflowStatus.READY_FOR_EDIT).contains(currentStatus)) {

                allowedStatuses.add(WorkflowStatus.IN_UPGRADE);
            }

            if (Arrays.asList(WorkflowStatus.READY_FOR_EDIT, WorkflowStatus.IN_EDIT, WorkflowStatus.REVIEW_COMPLETED).contains(currentStatus)) {

                allowedStatuses.add(WorkflowStatus.READY_FOR_REVIEW);
            }

            if (Arrays.asList(WorkflowStatus.READY_FOR_EDIT, WorkflowStatus.IN_EDIT, WorkflowStatus.READY_FOR_REVIEW).contains(currentStatus)) {

                allowedStatuses.add(WorkflowStatus.READY_FOR_PUBLICATION);
            }

        }

        // set status permissions for REVIEWERS
        if (user.doesUserHavePermission(User.ROLE_REVIEWER, project)) {

            if (Arrays.asList(WorkflowStatus.IN_REVIEW).contains(currentStatus)) {

                allowedStatuses.add(WorkflowStatus.READY_FOR_EDIT);
            }

            if (Arrays.asList(WorkflowStatus.READY_FOR_REVIEW).contains(currentStatus)) {

                allowedStatuses.add(WorkflowStatus.IN_REVIEW);
            }

            if (Arrays.asList(WorkflowStatus.IN_REVIEW).contains(currentStatus)) {

                allowedStatuses.add(WorkflowStatus.REVIEW_COMPLETED);
            }

        }

        // set status permissions for ADMINS
        if (user.doesUserHavePermission(User.ROLE_ADMIN, project)) {

            if (Arrays.asList(WorkflowStatus.READY_FOR_PUBLICATION).contains(currentStatus)) {

                allowedStatuses.add(WorkflowStatus.READY_FOR_EDIT);
            }

        }

        return allowedStatuses;
    }

    /**
     * Get a list of workflow actions that are allowed for the current user and state of the mapSet.
     *
     * @param user the user
     * @param mapSet the mapSet
     * @return the list of allowed actions
     * @throws Exception the exception
     */
    public static Set<WorkflowAction> getAllowedActions(final User user, final MapSet mapSet) throws Exception {

        final Set<WorkflowAction> allowedActions = new HashSet<>();
        final WorkflowStatus currentStatus = mapSet.getWorkflowStatus();
        final Project project = mapSet.getProject();

        // Authors can start an edit cycle on Published mapSets
        if (mapSet.getVersionStatus() == VersionStatus.PUBLISHED && user.doesUserHavePermission(User.ROLE_AUTHOR, project)) {

            allowedActions.add(WorkflowAction.EDIT);
            allowedActions.add(WorkflowAction.UPGRADE);
            if (!mapSet.getWorkflowStatus().equals(WorkflowStatus.READY_FOR_PUBLICATION)) {
                allowedActions.add(WorkflowAction.REQUEST_PUBLICATION);
            }

        } else if (currentStatus == null) {

            return allowedActions;

        } else if (mapSet.getVersionStatus() == VersionStatus.IN_DEVELOPMENT) {

            if (currentStatus.equals(WorkflowStatus.READY_FOR_EDIT)) {

                if (user.doesUserHavePermission(User.ROLE_AUTHOR, project)) {
                    allowedActions.add(WorkflowAction.EDIT);
                    allowedActions.add(WorkflowAction.UPGRADE);
                    allowedActions.add(WorkflowAction.REQUEST_REVIEW);
                    allowedActions.add(WorkflowAction.REQUEST_PUBLICATION);
                }
            }

            else if (currentStatus == WorkflowStatus.IN_EDIT) {

                // only the assigned user can edit
                if (user.doesUserHavePermission(User.ROLE_AUTHOR, project) && user.getUserName().equals(mapSet.getAssignedUser())) {
                    allowedActions.add(WorkflowAction.CANCEL_EDIT);
                    allowedActions.add(WorkflowAction.FINISH_EDIT);
                    allowedActions.add(WorkflowAction.REQUEST_REVIEW);
                    allowedActions.add(WorkflowAction.REQUEST_PUBLICATION);
                }

                if (user.doesUserHavePermission(User.ROLE_ADMIN, project)) {
                    allowedActions.add(WorkflowAction.CANCEL_EDIT);
                    allowedActions.add(WorkflowAction.FINISH_EDIT);
                }
            }

            else if (currentStatus == WorkflowStatus.IN_UPGRADE) {

                // only the assigned user can upgrade
                if (user.doesUserHavePermission(User.ROLE_AUTHOR, project) && user.getUserName().equals(mapSet.getAssignedUser())) {
                    allowedActions.add(WorkflowAction.CANCEL_UPGRADE);
                    allowedActions.add(WorkflowAction.FINISH_UPGRADE);
                    allowedActions.add(WorkflowAction.REQUEST_PUBLICATION);
                }

                // only the assigned user can upgrade
                if (user.doesUserHavePermission(User.ROLE_ADMIN, project)) {
                    allowedActions.add(WorkflowAction.CANCEL_UPGRADE);
                    allowedActions.add(WorkflowAction.FINISH_UPGRADE);
                }

            }

            else if (currentStatus == WorkflowStatus.READY_FOR_REVIEW) {

                if (user.doesUserHavePermission(User.ROLE_AUTHOR, project)) {
                    allowedActions.add(WorkflowAction.WITHDRAW);
                    allowedActions.add(WorkflowAction.REQUEST_PUBLICATION);
                }

                if (user.doesUserHavePermission(User.ROLE_REVIEWER, project)) {
                    allowedActions.add(WorkflowAction.REVIEW);
                }

            }

            else if (currentStatus == WorkflowStatus.IN_REVIEW) {

                // only the assigned user can review
                if (user.doesUserHavePermission(User.ROLE_REVIEWER, project) && user.getUserName().equals(mapSet.getAssignedUser())) {
                    allowedActions.add(WorkflowAction.REJECT_REVIEW);
                    allowedActions.add(WorkflowAction.ACCEPT_REVIEW);
                    allowedActions.add(WorkflowAction.UNASSIGN);
                    allowedActions.add(WorkflowAction.REQUEST_PUBLICATION);
                }

                if (user.doesUserHavePermission(User.ROLE_ADMIN, project)) {
                    allowedActions.add(WorkflowAction.UNASSIGN);
                }

            }

            else if (currentStatus == WorkflowStatus.REVIEW_COMPLETED) {

                if (user.doesUserHavePermission(User.ROLE_AUTHOR, project)) {
                    allowedActions.add(WorkflowAction.EDIT);
                    allowedActions.add(WorkflowAction.REQUEST_REVIEW);
                    allowedActions.add(WorkflowAction.REQUEST_PUBLICATION);
                }

            }

            else if (currentStatus == WorkflowStatus.READY_FOR_PUBLICATION) {

                if (user.doesUserHavePermission(User.ROLE_AUTHOR, project) || user.doesUserHavePermission(User.ROLE_ADMIN, project)) {
                    allowedActions.add(WorkflowAction.FAILS_RVF);
                    allowedActions.add(WorkflowAction.START_PUBLISH);
                }

                final String organizationCrowdId = ""; // mapSet.getOrganizationCrowdId();
                final String editionName = ""; // mapSet.getEdition().getShortName();

                if (mapSet.isLocalSet() && user.checkPermission(User.ROLE_ADMIN, organizationCrowdId, editionName, null)) {
                    allowedActions.add(WorkflowAction.PUBLISH_REFSET);
                }
            }

            else if (currentStatus == WorkflowStatus.IN_PUBLICATION) {

                if (user.doesUserHavePermission(User.ROLE_AUTHOR, project) || user.doesUserHavePermission(User.ROLE_ADMIN, project)) {
                    allowedActions.add(WorkflowAction.FAILS_RVF);
                    allowedActions.add(WorkflowAction.REQUEST_PUBLICATION);
                    allowedActions.add(WorkflowAction.PUBLISH_REFSET);
                }

                final String organizationCrowdId = ""; // mapSet.getOrganizationCrowdId();
                final String editionName = ""; // mapSet.getEdition().getShortName();

                if (mapSet.isLocalSet() && user.checkPermission(User.ROLE_ADMIN, organizationCrowdId, editionName, null)) {
                    allowedActions.add(WorkflowAction.PUBLISH_REFSET);
                }
            }
        }

        return allowedActions;
    }

    /**
     * Test if a user can perform a workflow action on a mapSet.
     *
     * @param user the user
     * @param mapSet the mapSet
     * @param action the action
     * @throws Exception the exception
     */
    public static void canUserPerformWorkflowAction(final User user, final MapSet mapSet, final WorkflowAction action) throws Exception {

        if (!MapSetWorkflowService.getAllowedActions(user, mapSet).contains(action)) {
            LOG.error("Unsuccessful attempt to update workflow status for Reference Set " + mapSet.getRefSetCode() + " on " + mapSet.getVersionDate()
                + " from status " + mapSet.getWorkflowStatus() + " with action " + action);

            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unsuccessful attempt to update workflow status for Reference Set "
                + mapSet.getRefSetCode() + " on " + mapSet.getVersionDate() + " from status " + mapSet.getWorkflowStatus() + " with action " + action);
        }
    }

    /**
     * Test if a user can edit a mapSet.
     *
     * @param user the user
     * @param mapSet the mapSet
     * @throws Exception the exception
     */
    public static void canUserEditRefset(final User user, final MapSet mapSet) throws Exception {

        if (!Arrays.asList(WorkflowStatus.IN_EDIT, WorkflowStatus.IN_UPGRADE).contains(mapSet.getWorkflowStatus())
            || !user.getUserName().equals(mapSet.getAssignedUser())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Reference Set is not in the proper state or user does not have permission to edit.");
        }

    }

    /**
     * Test if a user can perform In Development actions on the mapSet.
     *
     * @param user the user
     * @param mapSet the mapSet
     * @throws Exception the exception
     */
    public static void canUserPerformInDevelopmentActionsOnRefset(final User user, final MapSet mapSet) throws Exception {

        if (!VersionStatus.IN_DEVELOPMENT.equals(mapSet.getVersionStatus()) || !user.doesUserHavePermission(User.ROLE_VIEWER, mapSet.getProject())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                "Reference Set is not in the proper state or user does not have permission to perform this action.");
        }
    }

    /**
     * Format error message.
     *
     * @param response the response
     * @return the string
     */
    private static String formatErrorMessage(final Response response) {

        String snowstormErrorMessage;
        try {
            snowstormErrorMessage = SnowstormConnection.readEntityAsString(response);
        } catch (final Exception e) {
            LOG.warn("Could not read response entity: {}", e.getMessage());
            return "";
        }
        if (StringUtils.isEmpty(snowstormErrorMessage)) {
            return "";
        }
        if (StringUtility.isJson(snowstormErrorMessage)) {
            final ObjectMapper mapper = new ObjectMapper();
            try {
                final JsonNode json = mapper.readTree(snowstormErrorMessage);
                snowstormErrorMessage = json.has("message") ? json.get("message").asText() : "";
            } catch (final Exception e) {
                LOG.error("formatErrorMessage snowstormErrorMessage:{}", snowstormErrorMessage, e);
            }
        }
        return snowstormErrorMessage.replaceAll("[\\r\\n]+", " ");
    }

    /**
     * Identify international content version for upgrade.
     *
     * @param mapSet the mapSet
     * @return the string
     */
    public static String identifyInternationalContentVersionForUpgrade(final MapSet mapSet) {

        String moduleName = null;
        final String releaseBranch = mapSet.getBaseContentVersion().substring(0, mapSet.getBaseContentVersion().indexOf(" "));

        try {
            moduleName = EditionService.getDependencyModuleNameFromBranchMetadata(mapSet.getEditionShortName(), mapSet.getModuleId(),
                SnomedConstants.BRANCH_DATE_FORMAT.parse(releaseBranch).getTime(), mapSet.getEditionBranch());
        } catch (final Exception e) {
            LOG.error("Catching 'date must not be null' exception using mapSet properties: a) " + mapSet.getEditionShortName() + " b) " + mapSet.getModuleId()
                + " c) " + releaseBranch + " d) " + mapSet.getEditionBranch());
        }

        return moduleName;
    }
}
