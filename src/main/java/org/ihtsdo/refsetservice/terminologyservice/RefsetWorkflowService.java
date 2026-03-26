/*
 * Copyright 2024 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
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
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.RefsetWorkflowHistory;
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
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
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
public final class RefsetWorkflowService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(RefsetWorkflowService.class);

    /** The order of workflow steps . */
    private static final List<WorkflowStatus> WORKFLOW_STATUSES = WorkflowStatus.getValues();

    /** The order of workflow actions . */
    private static final List<WorkflowAction> WORKFLOW_ACTIONS = WorkflowAction.getValues();

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
     * Instantiates an empty {@link RefsetWorkflowService}.
     */
    private RefsetWorkflowService() {

        // n/a
    }

    /**
     * Start the publication of all Ready for Publication refsets in a code system by promoting them to the REFSETS branch.
     *
     * @param service the Terminology Service
     * @param editionShortName an code system to limit the refset to
     * @return A list of concepts that were unable to be promoted
     * @throws Exception the exception
     */
    public static List<String> startRefsetPublications(final TerminologyService service, final String editionShortName, final User authUser) throws Exception {

        final List<String> refsetsNotUpdated = new ArrayList<>();
        final String query = "workflowStatus: " + WorkflowStatus.READY_FOR_PUBLICATION + " AND editionShortName: " + editionShortName + " AND localSet: false";

        final ResultList<Refset> results = service.find(query, null, Refset.class, null);

        if (results.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.EXPECTATION_FAILED,
                "There are no Reference sets in " + editionShortName + " that are ready to be published");
        }

        final String editionBranch = results.getItems().get(0).getEditionBranch();
        final String projectBranchPath = BranchService.getProjectBranchPath(editionBranch);

        // rebase the project branch from the edition branch
        BranchService.mergeBranch(editionBranch, projectBranchPath, "Rebasing project branch to latest changes", true);

        // see if there is an "In Development" version as that should be the latest.
        for (final Refset refset : results.getItems()) {

            try {

                final String refsetBranchPath =
                    BranchService.getRefsetBranchPath(refset.toBranchDetails());

                BranchService.mergeBranch(projectBranchPath, refsetBranchPath, "Rebasing refset branch to latest changes", true);
                refset.setEditionBranch(editionBranch);
                final boolean success = BranchService.promoteRefsetToProjectBranch(refset.toBranchDetails(),
                    "Preparing for publication - promoting refset to project branch");

                if (!success) {

                    final String message =
                        "Unable to promote Reference set into project branch for refset " + refset.getRefsetId() + " because the project branch doesn't exist.";
                    LOG.error(message);
                    throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
                }

                setWorkflowStatusByAction(service, authUser, WorkflowAction.START_PUBLISH, refset, "Preparing for publication.");

            } catch (final Exception e) {

                LOG.error("Unable to promote Reference Set " + refset.getRefsetId() + " into project branch because: " + e.getMessage(), e);
                refsetsNotUpdated.add(refset.getRefsetId());
            }
        }

        return refsetsNotUpdated;
    }

    /**
     * Complete the publication of all Ready for Publication refsets.
     *
     * @param service the Terminology Service
     * @param editionShortName an code system to limit the refset to
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

        // setup query
        final String branchPath = editionShortName.equals("SNOMEDCT") ? "MAIN/" : "MAIN/" + editionShortName;

        final String query = "workflowStatus: " + WorkflowStatus.IN_PUBLICATION + " AND editionShortName: " + editionShortName;

        // Find snowstorm's version date of the latest version of all refsets in ready_to_published state. Ensure all identical
        final List<String> refsetsNotUpdated = new ArrayList<>();

        final ResultList<Refset> results = service.find(query, null, Refset.class, null);
        if (results.getItems().isEmpty()) {

            throw new ResponseStatusException(HttpStatus.EXPECTATION_FAILED,
                "There are no reference sets in " + editionShortName + " that are ready to be published");
        }

        if (!BranchService.doesBranchExist(branchPath)) {
            throw new MissingArgumentException("The version branch '" + branchPath
                + "' does not exist. This must be created and populated with the reference sets to be versioned outside of this tool "
                + "before this publication completion process can be run.");
        }

        final Edition edition = service.findSingle("shortName:" + editionShortName, Edition.class, null);
        if (edition == null) {
            throw new MissingArgumentException("The code system '" + editionShortName + "' could not be found");
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
        for (final Refset refset : results.getItems()) {
            refsetsNotUpdated.addAll(completeRefsetPublication(service, refset, latestVersion));
        }

        String error = "";
        // see if there are any refsets that were unable to be updated and craft the error message
        if (!refsetsNotUpdated.isEmpty()) {

            error = "Unable to complete publication for reference sets in code system " + editionShortName + ": ";
            for (final String refsetNotUpdated : refsetsNotUpdated) {
                error += refsetNotUpdated + ", ";
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
     * Complete the publication of a refset.
     *
     * @param service the Terminology Service
     * @param refset the refset
     * @param publicationDateString the publication date of the refset in yyyy-MM-dd format
     * @return A list of refsets that were unable to have publication completed
     * @throws Exception the exception
     */
    public static List<String> completeRefsetPublication(final TerminologyService service, final Refset refset, final String publicationDateString)
        throws Exception {

        final List<String> refsetsNotUpdated = new ArrayList<>();

        try {

            // Update the previously published version to no long be latest
            final Refset previouslyPublishedVersion =
                service.findSingle("refsetId:" + QueryParserBase.escape(refset.getRefsetId()) + " AND latestPublishedVersion: true", Refset.class, null);

            LOG.info("1 {} ", !refset.isLocalSet() && refset.getWorkflowStatus() != WorkflowStatus.IN_PUBLICATION);
            LOG.info("2 {} ", refset.isLocalSet() && refset.getWorkflowStatus() != WorkflowStatus.READY_FOR_PUBLICATION);

            if (!refset.isLocalSet() && refset.getWorkflowStatus() != WorkflowStatus.IN_PUBLICATION
                || (refset.isLocalSet() && refset.getWorkflowStatus() != WorkflowStatus.READY_FOR_PUBLICATION)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Reference set is not in the proper status to have publication completed " + refset.getRefsetId());
            }

            final String refsetBranchPath = RefsetService.getBranchPath(refset);

            if (refset.isLocalSet()) {

                final String topLevelRefsetBranchPath = BranchService.getLocalsetTopLevelRefsetBranchPath(refset.getEditionBranch(), refset.getRefsetId());
                BranchService.mergeBranch(refsetBranchPath, topLevelRefsetBranchPath, "Promoting versioned local set", false);
                LOG.info("Publication (non-snomed versioning) of localset Reference Set: " + refset.getRefsetId());
            } else {
                LOG.info("Publication of refset: " + refset.getRefsetId());
            }

            final Date publicationDate = RefsetService.getRefsetDateFromFormattedString(publicationDateString);

            /*
             * if (refset.getVersionDate() != null && !refset.getVersionDate().before(publicationDate)) { throw new Exception( "Can't publish on " +
             * publicationDate + " when that's earlier than the refset's existing versionDate: " + refset.getVersionDate()); }
             */

            refset.setVersionDate(publicationDate);
            refset.setInternationalContentVersion(EditionService.getDependencyModuleNameFromBranchMetadata(refset.getEditionShortName(), refset.getModuleId(),
                publicationDate.getTime(), refsetBranchPath));

            if (!refset.getEdition().isDerivative()) {

                refset.setBaseContentVersion(publicationDateString + refset.getBaseContentVersion().substring(refset.getBaseContentVersion().indexOf(" ")));

            } else {
                // if the edition is a derivative (i.e. from Snomed International Edition, version date, content version and international content
                // version are the same.
                final String internationalContentVersionDate =
                    refset.getInternationalContentVersion().substring(0, refset.getInternationalContentVersion().indexOf(" "));
                refset.setBaseContentVersion(
                    internationalContentVersionDate + refset.getBaseContentVersion().substring(refset.getBaseContentVersion().indexOf(" ")));
            }

            refset.setWorkflowStatus(WorkflowStatus.PUBLISHED);
            refset.setVersionStatus(VersionStatus.PUBLISHED);
            refset.setLatestPublishedVersion(true);

            service.update(refset);
            service.add(AuditEntryHelper.completeRefsetPublicationEntry(refset));

            if (!refset.getWorkflowStatus().equals(WorkflowStatus.PUBLISHED)) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Reference set was not able to have publication completed " + refset.getRefsetId() + " on " + refset.getVersionDate());
            }

            if (previouslyPublishedVersion != null) {

                previouslyPublishedVersion.setLatestPublishedVersion(false);
                previouslyPublishedVersion.setHasVersionInDevelopment(false);

                service.update(previouslyPublishedVersion);

                LOG.info("Refset " + previouslyPublishedVersion.getId() + " version marked as not latest.");
            }

        } catch (final Exception e) {

            LOG.error("Completing Reference set Publication failed: " + e.getMessage());
            LOG.info("", e);
            refsetsNotUpdated.add(refset.getRefsetId());
        }

        return refsetsNotUpdated;
    }

    /**
     * Set workflow status for a number of refsets at once.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param refsetIds a comma separated list of refset IDs
     * @param action the action the user took
     * @param notes the workflow status notes
     * @return A list of concepts that were unable to have their status updated
     * @throws Exception the exception
     */
    public static List<String> setBatchWorkflowStatusByAction(final TerminologyService service, final User user, final String refsetIds,
        final WorkflowAction action, final String notes) throws Exception {

        final List<String> refsetsNotUpdated = new ArrayList<>();

        final ResultList<Refset> results =
            service.find("refsetId:(" + refsetIds.replace(",", " OR ") + ") AND versionStatus: (" + VersionStatus.IN_DEVELOPMENT.name() + ")",
                new PfsParameter(), Refset.class, null);

        for (final Refset refset : results.getItems()) {

            try {

                final WorkflowStatus currentStatus = refset.getWorkflowStatus();

                setWorkflowStatusByAction(service, user, action, refset, notes);

                if (currentStatus.equals(refset.getWorkflowStatus())) {

                    refsetsNotUpdated.add(refset.getRefsetId());
                } else {

                    RefsetService.clearAllRefsetCaches(refset.getEditionBranch());
                }

            } catch (final Exception e) {

                refsetsNotUpdated.add(refset.getRefsetId());
            }

        }

        return refsetsNotUpdated;
    }

    /**
     * Set the workflow status for the refset.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param action the action
     * @param refset the refset
     * @param notes the workflow status notes
     * @param nextStatus the new workflow status
     * @param assignedUser the user the refset is assigned to, or null
     * @return the updated refset
     * @throws Exception the exception
     */
    public static Refset setWorkflowStatus(final TerminologyService service, final User user, final WorkflowAction action, final Refset refset,
        final String notes, final WorkflowStatus nextStatus, final String assignedUser) throws Exception {

        final Refset updatedRefset = setRefsetWorkflowStatus(service, user, refset, nextStatus, assignedUser);
        addWorkflowHistory(service, user, action, refset, notes);
        return updatedRefset;

    }

    /**
     * Set the workflow status for the refset based on the action the user took.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param action the action the user took
     * @param refset the refset
     * @param notes the workflow status notes
     * @return the updated refset
     * @throws Exception the exception
     */
    public static Refset setWorkflowStatusByAction(final TerminologyService service, final User user, final WorkflowAction action, final Refset refset,
        final String notes) throws Exception {

        canUserPerformWorkflowAction(user, refset, action);

        final WorkflowStatus currentStatus = refset.getWorkflowStatus();
        boolean restoreHistory = false;
        final List<String> roles = RefsetService.setRoles(user, refset.getProject(), new ArrayList<>());

        // get the next status based on the user, current status, and supplied action
        // WHY IS THIS FAILING??? LOG.info("WORKFLOW_PERMUTATIONS: " + ModelUtility.toJson(WORKFLOW_PERMUTATIONS));

        WorkflowStatus nextStatus = null;
        String assignedUser = null;

        // loop thru the roles to find a match for the action and current status. !! This only works if any multiple matches between role, current status, and
        // action go to the
        // same next status !!
        for (final String role : roles) {

            if (WORKFLOW_PERMUTATIONS.containsKey(role) && WORKFLOW_PERMUTATIONS.get(role).containsKey(refset.getWorkflowStatus())) {

                final WorkflowStatus possibleStatus = WORKFLOW_PERMUTATIONS.get(role).get(refset.getWorkflowStatus()).get(action);

                if (possibleStatus != null) {

                    nextStatus = possibleStatus;
                    break;
                }

            }

        }

        if (nextStatus == null) {
            LOG.warn(
                "Workflow action requested but no permutation: refsetId={}, refsetDbId={}, workflowStatus={}, action={}, user={}, rolesTried={}, file={}",
                refset.getRefsetId(), refset.getId(), refset.getWorkflowStatus(), action, user.getUserName(), roles, WORKFLOW_PERMUTATIONS_FILE_NAME);
        }

        if (Arrays.asList(WorkflowAction.EDIT, WorkflowAction.UPGRADE, WorkflowAction.REVIEW).contains(action)) {

            assignedUser = user.getUserName();
        }

        final List<String> refsetBranchVersions = RefsetService.getBranchVersions(refset.getEditionBranch());

        LOG.info("currentStatus: " + currentStatus + " ; nextStatus: " + nextStatus);

        // if edits have just been completed then merge the edit branch into the refset branch and delete the edit branch
        if ((currentStatus.equals(WorkflowStatus.IN_EDIT)
            && Arrays.asList(WorkflowAction.FINISH_EDIT, WorkflowAction.REQUEST_REVIEW, WorkflowAction.REQUEST_PUBLICATION).contains(action))
            || (currentStatus.equals(WorkflowStatus.IN_UPGRADE) && Arrays.asList(WorkflowAction.FINISH_UPGRADE).contains(action))) {

            final boolean success = BranchService.promoteEditIntoRefsetBranch(refset.toBranchDetails(), notes);

            if (success) {

                refset.setEditBranchId(null);
                if (currentStatus.equals(WorkflowStatus.IN_UPGRADE) && Arrays.asList(WorkflowAction.FINISH_UPGRADE).contains(action)) {
                    RefsetMemberService.clearAllMemberCaches(
                        BranchService.getRefsetBranchPath(refset.toBranchDetails()));
                }

                RefsetService.removeRefsetEditHistory(service, user, refset.getRefsetId());

            } else {

                final String message =
                    "Unable to promote edit into Reference Set branch for " + refset.getRefsetId() + " because the edit branch doesn't exist.";
                LOG.error(message);
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, message);
            }

            // Set Upgrade Content Versions
            if (currentStatus.equals(WorkflowStatus.IN_UPGRADE) && Arrays.asList(WorkflowAction.FINISH_UPGRADE).contains(action)) {
                if (refset.isInUpgrade()) {

                    if (refsetBranchVersions != null && !refsetBranchVersions.isEmpty()) {
                        Collections.sort(refsetBranchVersions, Comparator.reverseOrder());

                        if ("Never Published".equals(refsetBranchVersions.get(0))) {

                            final String tempDate = EditionService.getEditionDependentVersion(refset.getEditionShortName(), user);
                            final String version = tempDate.substring(0, 4) + "-" + tempDate.substring(4, 6) + "-" + tempDate.substring(6, 8);
                            refset.setBaseContentVersion(version + " " + refset.getEditionShortName());
                            refset.setInternationalContentVersion(identifyInternationalContentVersionForUpgrade(refset));

                        } else {
                            // Update Base to last published version of refset
                            refset.setBaseContentVersion(refsetBranchVersions.get(0) + " " + refset.getEditionShortName());
                            // Update International to dependencyRelease version of refset
                            refset.setInternationalContentVersion(identifyInternationalContentVersionForUpgrade(refset));
                        }
                    }
                }

                // If finishing upgrade/inactivate process, reset process flag
                if (refset.isInInactivate()) {
                    refset.setInInactivate(false);
                } else if (refset.isInUpgrade()) {
                    refset.setInUpgrade(false);
                }
            }
        }

        else if ((currentStatus.equals(WorkflowStatus.IN_EDIT) && Arrays.asList(WorkflowAction.CANCEL_EDIT).contains(action))
            || (currentStatus.equals(WorkflowStatus.IN_UPGRADE) && Arrays.asList(WorkflowAction.CANCEL_UPGRADE).contains(action))) {

            RefsetMemberService.clearAllMemberCaches(BranchService.getEditBranchPath(refset.toBranchDetails()));

            refset.setEditBranchId(null);
            restoreHistory = true;

            if (refset.isInInactivate()) {
                refset.setInInactivate(false);
            } else if (refset.isInUpgrade()) {
                refset.setInUpgrade(false);
            }

        }

        // else if this is the start of edits create the refset edit branch
        else if (action == WorkflowAction.EDIT || action == WorkflowAction.UPGRADE) {

            if (currentStatus != WorkflowStatus.IN_UPGRADE) {

                // Upgrade creates or merges the edit branch separately
                if (StringUtils.isEmpty(refset.getEditBranchId())) {
                    final String branchId = BranchService.generateBranchId();
                    refset.setEditBranchId(branchId);
                    BranchService.createEditBranch(refset.toBranchDetails(), branchId);
                }

                BranchService.rebaseRefsetBranchContents(refset.toBranchDetails());
                BranchService.rebaseEditBranchContents(refset.toBranchDetails());
            }

            RefsetService.setRefsetMemberCount(service, refset, true);
        }

        // Clear upgrade and module dependency caches given changes that may have occured during process
        if (currentStatus == WorkflowStatus.IN_UPGRADE) {
            RefsetMemberService.removeUpgradeData(service, user, refset);
            EditionService.clearModuleDependencyCaches(refset.getModuleId());
        }

        setWorkflowStatus(service, user, action, refset, notes, nextStatus, assignedUser);

        if (restoreHistory) {

            RefsetService.replaceRefsetWithEditHistory(service, user, refset);
            RefsetService.removeRefsetEditHistory(service, user, refset.getRefsetId());
        }

        return refset;
    }

    /**
     * Update the refset to a new workflow status.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param refset the refset
     * @param status the new workflow status
     * @param assignedUser the user the refset is assigned to, or null
     * @return the updated refset
     * @throws Exception the exception
     */
    public static Refset setRefsetWorkflowStatus(final TerminologyService service, final User user, final Refset refset, final WorkflowStatus status,
        final String assignedUser) throws Exception {

        final long start = System.currentTimeMillis();

        refset.setWorkflowStatus(status);
        refset.setAssignedUser(assignedUser);

        // Published is the final status so set the version information
        if (status.equals(WorkflowStatus.PUBLISHED)) {

            // get the latest edition version branch
            final List<String> branchVersions = RefsetService.getBranchVersions(refset.getEditionBranch());

            if (branchVersions.isEmpty()) {

                final String message = "Could not retrieve branch versions for branch " + refset.getEditionBranch();
                LOG.error(message);
                throw new Exception(message);
            }

            final String newVersion = branchVersions.get(0);

            refset.setVersionDate(RefsetService.getRefsetDateFromFormattedString(newVersion));
            refset.setVersionStatus(VersionStatus.PUBLISHED);
        }

        // Update an object
        service.update(refset);
        LOG.info("Reference Set workflow status set to {} for Reference Set {}. Time {}", status, refset.getId(), (System.currentTimeMillis() - start));

        // update the refset permissions
        return RefsetService.setRefsetPermissions(user, refset);
    }

    /**
     * Add an entry in the workflow history table.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param action the action
     * @param refset the refset
     * @param notes the workflow status notes
     * @throws Exception the exception
     */
    public static void addWorkflowHistory(final TerminologyService service, final User user, final WorkflowAction action, final Refset refset,
        final String notes) throws Exception {

        final long start = System.currentTimeMillis();

        final RefsetWorkflowHistory workflow = new RefsetWorkflowHistory(user.getUserName(), refset.getWorkflowStatus(), action, notes, refset);

        // Add an object
        service.add(workflow);
        service.add(AuditEntryHelper.addWorkflowHistoryEntry(workflow, refset, workflow.getWorkflowStatus()));
        final String newWorkflowId = workflow.getId();

        if (newWorkflowId == null) {

            throw new Exception("Unable to create a new workflow history entry.");
        }

        LOG.info("New workflow history entry with status {} added for Reference Set  {}. Time:  {}", refset.getWorkflowStatus(), refset.getId(),
            (System.currentTimeMillis() - start));
    }

    /**
     * Get the current workflow for a refset.
     *
     * @param service the Terminology Service
     * @param refset the refset
     * @return the current workflow object
     * @throws Exception the exception
     */
    public static RefsetWorkflowHistory getCurrentWorkflow(final TerminologyService service, final Refset refset) throws Exception {

        final PfsParameter pfs = new PfsParameter();
        pfs.setSort("modified");
        pfs.setAscending(false);
        pfs.setLimit(1);

        final ResultList<RefsetWorkflowHistory> results = service.find("refsetId:" + QueryParserBase.escape(refset.getId()) + "", pfs, RefsetWorkflowHistory.class, null);

        if (results.getItems().size() == 0) {

            throw new Exception("Unable to retrieve worflow for Reference Set " + refset.getRefsetId() + " on " + refset.getVersionDate());
        }

        return results.getItems().get(0);
    }

    /**
     * Get the workflow history for a refset.
     *
     * @param service the Terminology Service
     * @param refset the refset
     * @param searchParameters the search parameters
     * @return the current workflow object
     * @throws Exception the exception
     */
    public static ResultList<RefsetWorkflowHistory> getWorkflowHistory(final TerminologyService service, final Refset refset, final SearchParameters searchParameters)
        throws Exception {

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

            query = " AND " + IndexUtility.addWildcardsToQuery(searchParameters.getQuery(), RefsetWorkflowHistory.class);
        }

        final ResultList<RefsetWorkflowHistory> results =
            service.find("refsetId:" + QueryParserBase.escape(refset.getId()) + query, pfs, RefsetWorkflowHistory.class, null);

        // LOG.info("getWorkflowHistory results: " + ModelUtility.toJson(results));

        return results;
    }

    /**
     * Update the notes for the current workflow status.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param refset the refset
     * @param notes the notes
     * @throws Exception the exception
     */
    public static void updateWorkflowNote(final TerminologyService service, final User user, final Refset refset, final String notes) throws Exception {

        final RefsetWorkflowHistory workflow = getCurrentWorkflow(service, refset);

        workflow.setNotes(notes);

        // Update an object
        service.update(workflow);
        service.add(AuditEntryHelper.updateWorkflowNoteEntry(workflow, refset));
        LOG.info("Note for workflow history entry with status " + refset.getWorkflowStatus() + " updated for refset " + refset.getId());
    }

    /**
     * Get the current assigned username.
     *
     * @param service the Terminology Service
     * @param refset the refset
     * @return the current assigned username
     * @throws Exception the exception
     */
    public static String getAssignedUserName(final TerminologyService service, final Refset refset) throws Exception {

        // if the refset isn't being edited or reviewed no one is assigned
        if (!Arrays.asList(WorkflowStatus.IN_EDIT, WorkflowStatus.IN_REVIEW).contains(refset.getWorkflowStatus())) {

            return "";
        }

        final RefsetWorkflowHistory workflow = getCurrentWorkflow(service, refset);
        LOG.info("getAssignedUserName: " + workflow.getUserName());
        return workflow.getUserName();
    }

    /**
     * In order to create a refset branch for a new refset the SCTID needs to get generated in a temp branch first.
     *
     * @param editionBranchPath the branch path of the temporary branch
     * @return the branch path of the new branch
     * @throws Exception the exception
     */
    public static String getNewRefsetId(final String editionBranchPath) throws Exception {

        String refsetConceptId = null;
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
        final String url = SnowstormConnection.getRestBaseUrl() + "browser/" + tempBranchPath + "/" + "concepts/";

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
            final ObjectMapper mapper = ThreadLocalMapper.get();
            final JsonNode root = mapper.readTree(resultString);
            final JsonNode conceptNode = root;

            if (conceptNode.has("conceptId")) {
                refsetConceptId = conceptNode.get("conceptId").asText();
            }

            BranchService.deleteBranch(tempBranchPath);
        }

        if (StringUtils.isEmpty(refsetConceptId)) {
            throw new Exception("Unable to create new refset concept.");
        }

        LOG.info("New Refset ID {}. Time: {}", refsetConceptId, (System.currentTimeMillis() - start));
        return refsetConceptId;
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
     * Get a list of workflow statuses that are allowed for the current user and state of the refset.
     *
     * @param user the user
     * @param refset the refset
     * @return the list of allowed statuses
     * @throws Exception the exception
     */
    public static List<WorkflowStatus> getAllowedStatuses(final User user, final Refset refset) throws Exception {

        final List<WorkflowStatus> allowedStatuses = new ArrayList<>();
        final WorkflowStatus currentStatus = refset.getWorkflowStatus();
        final Project project = refset.getProject();

        // Authors can start an edit cycle on Published refsets
        if (refset.getVersionStatus().equals(WorkflowStatus.PUBLISHED)) {

            if (user.doesUserHavePermission(User.ROLE_AUTHOR, project)) {

                allowedStatuses.add(WorkflowStatus.READY_FOR_EDIT);
                allowedStatuses.add(WorkflowStatus.IN_EDIT);
                allowedStatuses.add(WorkflowStatus.IN_UPGRADE);
            }

            return allowedStatuses;
        }

        // only the assigned user can edit or review
        if (!user.getUserName().equals(refset.getAssignedUser())
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
     * Get a list of workflow actions that are allowed for the current user and state of the refset.
     *
     * @param user the user
     * @param refset the refset
     * @return the list of allowed actions
     * @throws Exception the exception
     */
    public static Set<WorkflowAction> getAllowedActions(final User user, final Refset refset) throws Exception {

        final Set<WorkflowAction> allowedActions = new HashSet<>();
        final WorkflowStatus currentStatus = refset.getWorkflowStatus();
        final Project project = refset.getProject();

        // Authors can start an edit cycle on Published refsets
        if (refset.getVersionStatus() == VersionStatus.PUBLISHED && user.doesUserHavePermission(User.ROLE_AUTHOR, project)) {

            allowedActions.add(WorkflowAction.EDIT);
            allowedActions.add(WorkflowAction.UPGRADE);
            if (!refset.getWorkflowStatus().equals(WorkflowStatus.READY_FOR_PUBLICATION)) {
                allowedActions.add(WorkflowAction.REQUEST_PUBLICATION);
            }

        } else if (currentStatus == null) {

            return allowedActions;

        } else if (refset.getVersionStatus() == VersionStatus.IN_DEVELOPMENT) {

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
                if (user.doesUserHavePermission(User.ROLE_AUTHOR, project) && user.getUserName().equals(refset.getAssignedUser())) {
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
                if (user.doesUserHavePermission(User.ROLE_AUTHOR, project) && user.getUserName().equals(refset.getAssignedUser())) {
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
                if (user.doesUserHavePermission(User.ROLE_REVIEWER, project) && user.getUserName().equals(refset.getAssignedUser())) {
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

                final String organizationCrowdId = refset.getOrganizationCrowdId();
                final String editionName = refset.getEdition().getShortName();

                if (refset.isLocalSet() && user.checkPermission(User.ROLE_ADMIN, organizationCrowdId, editionName, null)) {
                    allowedActions.add(WorkflowAction.PUBLISH_REFSET);
                }
            }

            else if (currentStatus == WorkflowStatus.IN_PUBLICATION) {

                if (user.doesUserHavePermission(User.ROLE_AUTHOR, project) || user.doesUserHavePermission(User.ROLE_ADMIN, project)) {
                    allowedActions.add(WorkflowAction.FAILS_RVF);
                    allowedActions.add(WorkflowAction.REQUEST_PUBLICATION);
                    allowedActions.add(WorkflowAction.PUBLISH_REFSET);
                }

                final String organizationCrowdId = refset.getOrganizationCrowdId();
                final String editionName = refset.getEdition().getShortName();

                if (refset.isLocalSet() && user.checkPermission(User.ROLE_ADMIN, organizationCrowdId, editionName, null)) {
                    allowedActions.add(WorkflowAction.PUBLISH_REFSET);
                }
            }
        }

        return allowedActions;
    }

    /**
     * Test if a user can perform a workflow action on a refset.
     *
     * @param user the user
     * @param refset the refset
     * @param action the action
     * @throws Exception the exception
     */
    public static void canUserPerformWorkflowAction(final User user, final Refset refset, final WorkflowAction action) throws Exception {

        if (!getAllowedActions(user, refset).contains(action)) {
            LOG.error("Unsuccessful attempt to update workflow status for Reference Set " + refset.getRefsetId() + " on " + refset.getVersionDate()
                + " from status " + refset.getWorkflowStatus() + " with action " + action);

            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unsuccessful attempt to update workflow status for Reference Set "
                + refset.getRefsetId() + " on " + refset.getVersionDate() + " from status " + refset.getWorkflowStatus() + " with action " + action);
        }
    }

    /**
     * Test if a user can edit a refset.
     *
     * @param user the user
     * @param refset the refset
     * @throws Exception the exception
     */
    public static void canUserEditRefset(final User user, final Refset refset) throws Exception {

        if (!Arrays.asList(WorkflowStatus.IN_EDIT, WorkflowStatus.IN_UPGRADE).contains(refset.getWorkflowStatus())
            || !user.getUserName().equals(refset.getAssignedUser())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Reference Set is not in the proper state or user does not have permission to edit.");
        }

    }

    /**
     * Test if a user can perform In Development actions on the refset.
     *
     * @param user the user
     * @param refset the refset
     * @throws Exception the exception
     */
    public static void canUserPerformInDevelopmentActionsOnRefset(final User user, final Refset refset) throws Exception {

        if (!VersionStatus.IN_DEVELOPMENT.equals(refset.getVersionStatus()) || !user.doesUserHavePermission(User.ROLE_VIEWER, refset.getProject())) {
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
            final ObjectMapper mapper = ThreadLocalMapper.get();
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
     * @param refset the refset
     * @return the string
     */
    public static String identifyInternationalContentVersionForUpgrade(final Refset refset) {

        String moduleName = null;
        final String releaseBranch = refset.getBaseContentVersion().substring(0, refset.getBaseContentVersion().indexOf(" "));

        try {
            moduleName = EditionService.getDependencyModuleNameFromBranchMetadata(refset.getEditionShortName(), refset.getModuleId(),
                SnomedConstants.BRANCH_DATE_FORMAT.parse(releaseBranch).getTime(), refset.getEditionBranch());
        } catch (final Exception e) {
            LOG.error("Catching 'date must not be null' exception using refset properties: a) " + refset.getEditionShortName() + " b) " + refset.getModuleId()
                + " c) " + releaseBranch + " d) " + refset.getEditionBranch());
        }

        return moduleName;
    }
}
