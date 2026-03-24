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

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.BranchInformation;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.sync.SyncAgent;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.StringUtility;
import org.ihtsdo.refsetservice.util.ThreadLocalMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Utility class for workflow processes.
 */
public final class BranchService {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(BranchService.class);

    /** The name of a refset project branch . */
    private static final String PROJECT_BRANCH_NAME = "REFSETS";

    /** The prefix to use for a refset branch . */
    private static final String REFSET_BRANCH_PREFIX = "REFSET-";

    /** The name of a refset edit branch . */
    private static final String EDIT_BRANCH_NAME = "EDIT-";

    /** The name of a refset compare branch . */
    private static final String COMPARE_BRANCH_NAME = "COMPARE-";

    /**
     * The name of a temporary branch to create empty concepts in to generate concept IDs for new refsets.
     */
    public static final String TEMP_BRANCH_NAME = "TEMP";

    /** The Constant SNAPSHOT_BRANCH_NAME. */
    public static final String SNAPSHOT_BRANCH_NAME = "SNAPSHOT";

    /** The Constant PATH_DELIMITER. */
    private static final String PATH_DELIMITER = "/";

    /** The app url root. */
    private static final boolean USE_MANAGE_SERVICE_INITIALS =
        "true".equals(PropertyUtility.getProperties().getProperty("mapset.use.manage.service.initials"));

    /**
     * Merge the project branch into the edition branch.
     *
     * @param editionBranchPath the branch path of the edition the refset belongs to
     * @param comment the merge comment
     * @return were the branches merged
     * @throws Exception the exception
     */
    public static boolean mergeProjectIntoEditionBranch(final String editionBranchPath, final String comment) throws Exception {

        final String projectBranchPath = getProjectBranchPath(editionBranchPath);

        if (doesBranchExist(projectBranchPath)) {
            mergeBranch(projectBranchPath, editionBranchPath, comment, false);
            return true;
        }
        return false;
    }

    /**
     * Merge the refset branch into the project branch. Never for localsets
     *
     * @param editionBranchPath the branch path of the edition the refset belongs to
     * @param refsetId the refset ID
     * @param branchId the ID for the refset branch
     * @param comment the merge comment
     * @return were the branches merged
     * @throws Exception the exception
     */
    public static boolean promoteRefsetToProjectBranch(final BranchInformation branchInformation, final String comment)
        throws Exception {

        final String projectBranchPath = getProjectBranchPath(branchInformation.getEditionBranch());
        final String refsetBranchPath = getRefsetBranchPath(branchInformation);

        if (doesBranchExist(refsetBranchPath)) {
            mergeBranch(refsetBranchPath, projectBranchPath, comment, false);
            return true;
        }
        return false;
    }

    /**
     * Merge the edit branch into the refset branch.
     *
     * @param editionBranchPath the branch path of the edition the refset belongs to
     * @param refsetId the refset ID
     * @param editBranchId the ID for the edit branch
     * @param refsetBranchId the ID for the refset branch
     * @param comment the merge comment
     * @param localset is the refset a localset
     * @return were the branches merged
     * @throws Exception the exception
     */
    public static boolean promoteEditIntoRefsetBranch(final BranchInformation branchInformation, final String comment) throws Exception {

        final String refsetBranchPath = getRefsetBranchPath(branchInformation);
        final String editBranchPath = getEditBranchPath(branchInformation);

        if (doesBranchExist(refsetBranchPath) && doesBranchExist(editBranchPath)) {
            mergeBranch(editBranchPath, refsetBranchPath, comment, false);
            RefsetMemberService.copyAllMemberCachesToBranch(editBranchPath, refsetBranchPath, "true");
            RefsetMemberService.clearAllMemberCaches(editBranchPath);
            return true;
        }
        return false;
    }

    /**
     * Create a branch.
     *
     * @param parentBranchPath the branch path of the parent to create the new branch in
     * @param branchName the name the new branch
     * @return the branch path of the new branch
     * @throws Exception the exception
     */
    public static String createBranch(final String parentBranchPath, final String branchName) throws Exception {

        String refsetBranchPath = parentBranchPath + PATH_DELIMITER + branchName;
        if (doesBranchExist(refsetBranchPath)) {
            return refsetBranchPath;
        }

        final long start = System.currentTimeMillis();
        final String url = SnowstormConnection.getRestBaseUrl() + "branches";
        final ObjectMapper mapper = ThreadLocalMapper.get();
        final ObjectNode body = mapper.createObjectNode().put("name", branchName).put("parent", parentBranchPath);

        LOG.info("createBranch URL: {}; body: {}", url, body);

        try (final Response response = SnowstormConnection.postResponse(url, body.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                final String error = "Could not create branch " + parentBranchPath + PATH_DELIMITER + branchName + " Message: " + formatErrorMessage(response);
                LOG.error(error);
                throw new Exception(error);
            }

            final String resultString = SnowstormConnection.readEntityAsString(response);
            final JsonNode root = mapper.readTree(resultString);
            final JsonNode rootNode = root;

            if (rootNode.has("path")) {

                refsetBranchPath = rootNode.get("path").asText();
            }

            LOG.info("Created branch {}. Time: {}", refsetBranchPath, (System.currentTimeMillis() - start));
        }

        return refsetBranchPath;
    }

    /**
     * Delete a branch.
     *
     * @param branchPath the branch path to delete
     * @return was the branch deleted
     * @throws Exception the exception
     */
    public static boolean deleteBranch(final String branchPath) throws Exception {

        final long start = System.currentTimeMillis();
        final String url = SnowstormConnection.getRestBaseUrl() + "admin/" + branchPath + "/actions/hard-delete";

        LOG.info("deleteBranch URL: {}", url);

        try (final Response response = SnowstormConnection.deleteResponse(url, null)) {

            // Only process payload if Rest call is successful
            if (response.getStatus() == Response.Status.OK.getStatusCode()) {

                LOG.info("Deleted branch {}. Time: {}", branchPath, (System.currentTimeMillis() - start));
                return true;

            } else {

                LOG.error("Could not delete branch {} Message: {}", branchPath , formatErrorMessage(response));
                return false;
            }

        }

    }

    /**
     * Check if a branch exists.
     *
     * @param branchPath the branch path to check
     * @return the true if the branch exists, otherwise false
     * @throws Exception the exception
     */
    public static boolean doesBranchExist(final String branchPath) throws Exception {

        final long start = System.currentTimeMillis();
        final String url = SnowstormConnection.getRestBaseUrl() + "branches/" + branchPath;

        LOG.info("doesBranchExist URL: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            // If Rest call is successful then branch exists
            if (response.getStatus() == Response.Status.OK.getStatusCode()) {

                LOG.info("doesBranchExist: true for {}. Time: {}", branchPath, (System.currentTimeMillis() - start));
                return true;

            } else {
                LOG.info("doesBranchExist: false for {}. Time: {}", branchPath, (System.currentTimeMillis() - start));
                return false;
            }

        }

    }

    /**
     * Get the project branch path for an edition.
     *
     * @param editionBranchPath the branch path of the edition the project belongs to
     * @return the branch path of the project branch
     * @throws Exception the exception
     */
    public static String getProjectBranchPath(final String editionBranchPath) throws Exception {

        return editionBranchPath + PATH_DELIMITER + getProjectBranchName(editionBranchPath);
    }

    /**
     * Generate a ID for a branch based on a millisecond unix timestamp.
     *
     * @return the ID for the branch
     */
    public static String generateBranchId() {

        final long unixTime = Instant.now().toEpochMilli();
        return unixTime + "";
    }

    /**
     * Get the refset branch path for a refset.
     *
     * @param branch the branch
     * @return the branch path of the refset branch
     * @throws Exception the exception
     */
    public static String getRefsetBranchPath(final BranchInformation branchInformation) throws Exception {

        // editionBranchPath, refsetId, refsetBranchId, localset
        String projectBranchPath = getProjectBranchPath(branchInformation.getEditionBranch()) + PATH_DELIMITER;

        if (branchInformation.isLocalSet()) {
            projectBranchPath += getLocalsetRefsetTopLevelBranchName(branchInformation.getRefsetId()) + PATH_DELIMITER;
        }

        projectBranchPath += getRefsetBranchName(branchInformation.getRefsetId(), branchInformation.getRefsetBranchId());

        return projectBranchPath;
    }

    /**
     * Get the edit branch path for a refset.
     *
     * @param branch the branch
     * @return the branch path of the edit branch
     * @throws Exception the exception
     */
    public static String getEditBranchPath(final BranchInformation branchInformation) throws Exception {

        return getRefsetBranchPath(branchInformation) + PATH_DELIMITER + EDIT_BRANCH_NAME + branchInformation.getEditBranchId();
    }

    /**
     * Returns the branch path for a MapSet.
     * Uses stored branchPath if present; otherwise computes from edition + refSetCode + mapBranchId + editBranchId.
     *
     * @param mapSet the map set
     * @return the branch path, or null if mapSet is null or missing required components (mapBranchId)
     * @throws Exception the exception
     */
    public static String getMapSetBranchPath(final MapSet mapSet) throws Exception {

        if (mapSet == null || mapSet.getRefsetBranchId() == null || mapSet.getRefsetBranchId().isBlank()) {
            return null;
        }
        //final String stored = mapSet.getBranchPath();
//        if (isValidBranchPath(stored)) {
//            return stored;
//        }
        if (mapSet.getVersionDate() != null) {
            final Date tmpDate = mapSet.getVersionDate();
            final String pathDate = "/" + DateUtility.formatDate(tmpDate, DateUtility.DATE_FORMAT_REVERSE, null);
            final String path = mapSet.getEditionBranch() + pathDate;
            return path;
        }
        final BranchInformation branchInfo = mapSet.toBranchDetails();
        final String path = Arrays.asList(WorkflowStatus.IN_EDIT, WorkflowStatus.IN_UPGRADE).contains(mapSet.getWorkflowStatus())
            ? getEditBranchPath(branchInfo) : getRefsetBranchPath(branchInfo);
        return path;
    }

//    private static boolean isValidBranchPath(final String path) {
//
//        return path != null && !path.isBlank() && !"empty".equals(path) && !"none".equals(path);
//    }

    /**
     * Create the edit branch for a refset.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param branch the refset
     * @param editBranchId the ID for the edit branch
     * @return the branch path of the new edit branch
     * @throws Exception the exception
     */
    public static String createEditBranch(final BranchInformation branchInformation, final String editBranchId) throws Exception {

        final String refsetBranchPath = getRefsetBranchPath(branchInformation);
        final String editBranchPath = createBranch(refsetBranchPath, EDIT_BRANCH_NAME + editBranchId);

        // TODO: move this to where the method is called so that refset or mapset edit history is created.
        // RefsetService.createRefsetEditHistory(service, user, branchInformation);

        return editBranchPath;
    }

    /**
     * Create the compare branch for a refset.
     *
     * @param branch the branch
     * @return the branch path of the new edit branch
     * @throws Exception the exception
     */
    public static String createCompareBranch(final BranchInformation branchInformation) throws Exception {

        if (branchInformation == null || StringUtils.isEmpty(branchInformation.getRefsetBranchId())) {

            branchInformation.setBranchId(BranchService.generateBranchId());
            BranchService.createRefsetBranch(branchInformation);
        }

        final String refsetBranchPath = getRefsetBranchPath(branchInformation);
        return createBranch(refsetBranchPath, COMPARE_BRANCH_NAME + generateBranchId());
    }

    /**
     * Create the project branch for an edition.
     *
     * @param editionBranchPath the branch path of the edition to create the new branch in
     * @return the branch path of the new project branch
     * @throws Exception the exception
     */
    private static String createProjectBranch(final String editionBranchPath) throws Exception {

        String projectBranchPath = getProjectBranchPath(editionBranchPath);

        if (doesBranchExist(projectBranchPath)) {

            mergeBranch(editionBranchPath, projectBranchPath, "Updating project branch to latest changes", true);
            return projectBranchPath;

        } else {
            String branchName = getProjectBranchName(editionBranchPath);
            if (editionBranchPath.contains(SyncAgent.DEVELOPER_CODE_SYSTEM_SHORTNAME) && branchName.contains(PATH_DELIMITER)
                && !branchName.startsWith(PATH_DELIMITER) && !branchName.endsWith(PATH_DELIMITER)) {
                branchName = branchName.substring(0, branchName.indexOf(PATH_DELIMITER));
            }
            projectBranchPath = createBranch(editionBranchPath, getProjectBranchName(editionBranchPath));
            return projectBranchPath;
        }

    }

    /**
     * Get the project branch name for an edition.
     *
     * @param editionBranchPath the branch path of the edition the project belongs to
     * @return the project branch name
     * @throws Exception the exception
     */
    public static String getProjectBranchName(final String editionBranchPath) throws Exception {

        if (editionBranchPath.contains(SyncAgent.DEVELOPER_CODE_SYSTEM_SHORTNAME)) {
            return "WCI" + PROJECT_BRANCH_NAME;
        }

        if (USE_MANAGE_SERVICE_INITIALS) {
            final int initialsLocationIndex = editionBranchPath.lastIndexOf("-");
            if (initialsLocationIndex > 0) {
                return editionBranchPath.substring(initialsLocationIndex + 1, editionBranchPath.length()) + PROJECT_BRANCH_NAME;
            }
        }

        return PROJECT_BRANCH_NAME;
    }

    /**
     * Get the top level refset branch path for a localset refset.
     *
     * @param editionBranchPath the branch path of the edition the refset belongs to
     * @param refsetId the refset ID
     * @return the branch path of the refset branch
     * @throws Exception the exception
     */
    public static String getLocalsetTopLevelRefsetBranchPath(final String editionBranchPath, final String refsetId) throws Exception {

        return BranchService.getProjectBranchPath(editionBranchPath) + PATH_DELIMITER + getLocalsetRefsetTopLevelBranchName(refsetId);
    }

    /**
     * Get the refset branch name for a refset.
     *
     * @param refsetId the refset ID
     * @param branchId the ID for the refset branch
     * @return the branch path of the refset branch
     * @throws Exception the exception
     */
    private static String getRefsetBranchName(final String refsetId, final String branchId) {

        return REFSET_BRANCH_PREFIX + refsetId + "-" + branchId;
    }

    /**
     * Get the top level refset branch name for a localset refset.
     *
     * @param refsetId the refset ID
     * @return the branch path of the refset branch
     */
    private static String getLocalsetRefsetTopLevelBranchName(final String refsetId) {

        return REFSET_BRANCH_PREFIX + refsetId;
    }

    /**
     * Create the top level localset refset branch for an IN DEVELOPMENT version.
     *
     * @param branch the branch
     * @param projectBranchPath the project branch path
     * @return the branch path of the new refset branch
     * @throws Exception the exception
     */
    private static String createLocalsetRefsetBranch(final BranchInformation branchInformation, final String projectBranchPath) throws Exception {

        final String topLevelBranchName = getLocalsetRefsetTopLevelBranchName(branchInformation.getRefsetId());
        String topLevelRefsetBranchPath = projectBranchPath + PATH_DELIMITER + topLevelBranchName;

        if (BranchService.doesBranchExist(topLevelRefsetBranchPath)) {
            BranchService.mergeBranch(projectBranchPath, topLevelRefsetBranchPath, "Updating branch to latest changes", true);
        } else {

            BranchService.createProjectBranch(branchInformation.getEditionBranch());
            topLevelRefsetBranchPath = BranchService.createBranch(projectBranchPath, topLevelBranchName);
        }

        final String refsetBranchName = getRefsetBranchName(branchInformation.getRefsetId(), branchInformation.getRefsetBranchId());
        String refsetBranchPath = BranchService.getRefsetBranchPath(branchInformation);

        if (BranchService.doesBranchExist(refsetBranchPath)) {

            BranchService.mergeBranch(topLevelRefsetBranchPath, refsetBranchPath, "Updating branch to latest changes", true);
            return refsetBranchPath;

        } else {

            BranchService.createProjectBranch(branchInformation.getEditionBranch());
            refsetBranchPath = BranchService.createBranch(topLevelRefsetBranchPath, refsetBranchName);
            return refsetBranchPath;
        }
    }

    /**
     * Create the refset branch for an IN DEVELOPMENT version.
     *
     * @param branch the branch
     * @return the branch path of the new refset branch
     * @throws Exception the exception
     */
    public static String createRefsetBranch(final BranchInformation branchInformation) throws Exception {

        // final String editionBranchPath, final String refsetId, final String branchId, final boolean localse
        final String projectBranchPath = getProjectBranchPath(branchInformation.getEditionBranch());

        if (branchInformation.isLocalSet()) {
            return createLocalsetRefsetBranch(branchInformation, projectBranchPath);
        }

        final String branchName = getRefsetBranchName(branchInformation.getRefsetId(), branchInformation.getRefsetBranchId());
        String refsetBranchPath = getRefsetBranchPath(branchInformation);

        if (doesBranchExist(refsetBranchPath)) {

            mergeBranch(projectBranchPath, refsetBranchPath, "Updating refset branch to latest changes", true);
            return refsetBranchPath;

        } else {

            createProjectBranch(branchInformation.getEditionBranch());
            refsetBranchPath = createBranch(projectBranchPath, branchName);
            return refsetBranchPath;
        }

    }

    /**
     * Creates the refset snapshot branch.
     *
     * @param branch the refset
     * @return the string
     * @throws Exception the exception
     */
    public static String createRefsetSnapshotBranch(final BranchInformation branchInformation) throws Exception {

        final String refsetProjectBranchPath = getProjectBranchPath(branchInformation.getEditionBranch());
        final String refsetBranchPath = getRefsetBranchPath(branchInformation);

        // Rebase from main to project branch
        mergeBranch(branchInformation.getEditionBranch(), refsetProjectBranchPath, "Updating project branch to latest changes", true);
        mergeBranch(refsetProjectBranchPath, refsetBranchPath, "Updating refset branch to latest changes", true);

        String snapshotBranchPath = refsetBranchPath + PATH_DELIMITER + SNAPSHOT_BRANCH_NAME;

        if (doesBranchExist(snapshotBranchPath)) {
            mergeBranch(refsetBranchPath, snapshotBranchPath, "Updating snapshot branch to latest changes", true);
            return snapshotBranchPath;
        } else {
            snapshotBranchPath = createBranch(refsetBranchPath, SNAPSHOT_BRANCH_NAME);
            return snapshotBranchPath;
        }
    }

    /**
     * Format error message.
     *
     * @param response the response
     * @return the string
     */
    private static String formatErrorMessage(final Response response) {

        try {
            final String resultString = SnowstormConnection.readEntityAsString(response);
            return formatErrorMessage(resultString);
        } catch (final Exception e) {
            LOG.warn("Could not read response entity: {}", e.getMessage());
            return "";
        }
    }

    /**
     * Format error message.
     *
     * @param jsonResponse the response in json
     * @return the string
     */
    private static String formatErrorMessage(final String jsonResponse) {

        if (StringUtils.isEmpty(jsonResponse)) {
            return "";
        }

        if (StringUtility.isJson(jsonResponse)) {
            final ObjectMapper mapper = ThreadLocalMapper.get();
            String updatedJsonResponse = null;

            try {
                final JsonNode json = mapper.readTree(jsonResponse);
                updatedJsonResponse = json.has("message") ? json.get("message").asText() : "";
            } catch (final Exception e) {
                LOG.error("formatErrorMessage jsonResponse:{}", updatedJsonResponse, e);
            }
        }

        return jsonResponse.replaceAll("[\\r\\n]+", " ");
    }

    /**
     * Merge one branch into another.
     *
     * @param sourceBranchPath the branch path with the content to merge
     * @param targetBranchPath the branch path to merge content into
     * @param comment the merge comment
     * @param isRebase the is rebase
     * @throws Exception the exception
     */
    public static void mergeBranchNew(final String sourceBranchPath, final String targetBranchPath, final String comment, final boolean isRebase)
        throws Exception {
        // For more information, see:
        // https://github.com/IHTSDO/snowstorm/blob/master/src/test/java/org/snomed/snowstorm/core/data/services/BranchMergeServiceTest.java

        /*-
        1)        So for the authoring platform specifically - we call https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/swagger-ui/index.html#/Branching/retrieveBranch - to ascertain the branch status - a branch needs to be DIVERGED, BEHIND or STALE in order to be rebased, and we only allow promotion in the case that the branch is FORWARD.
        2)            For a rebase we then call: https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/swagger-ui/index.html#/Branching/createMergeReview - with the source being the parent branch, and the target the branch we want to rebase -- for DIVERGED OR STALE
        3)            We then poll: https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/swagger-ui/index.html#/Branching/getMergeReview to get the status of the review, using the id provided by the response on the prior post - looking for status ‘CURRENT’  -- for DIVERGED OR STALE
        4)            Next we call https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/swagger-ui/index.html#/Branching/getMergeReviewConflictingConcepts - and hope that it’s empty - I presume for RT2 it almost always (or always) is -- for DIVERGED OR STALE
        5)            Then finally we call: https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/swagger-ui/index.html#/Branching/mergeBranch with the same source and target as the merge-review, and the ID of the above current merge-review
         */

        final long start = System.currentTimeMillis();
        String reviewId = null;

        // SnowstormConnection.checkConnection();

        // First, determine if need to merge at all.
        final String initialBranchStatus = determineBranchStatus(targetBranchPath);

        if (mergeNeeded(initialBranchStatus, isRebase)) {
            // Merge review should only be used during rebase, not promotion (on snowstorm)
            if (isRebase) {

                if (!"BEHIND".equals(initialBranchStatus.toUpperCase())) {
                    // Create Merge review which compares branches and creates a change report
                    reviewId = createMergeReview(sourceBranchPath, targetBranchPath);

                    // Poll for change report completion
                    while (!pollResultsAvailable(reviewId, sourceBranchPath, targetBranchPath)) {

                        try {
                            Thread.sleep(1_000);
                        } catch (final InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }
                    }

                    if (conflictsExist(reviewId)) {
                        throw new Exception("Shouldn't have excpetions here and we are not equipped to handle it. See merge report.");
                    }
                }
            }

            performMerge(sourceBranchPath, targetBranchPath, comment, reviewId, isRebase);

            LOG.info("Merged branch " + sourceBranchPath + " into branch " + targetBranchPath + ". Time: " + (System.currentTimeMillis() - start));
        } else {
            LOG.info("No Merge needed from branch " + sourceBranchPath + " into branch " + targetBranchPath + ". Took " + (System.currentTimeMillis() - start)
                + " to determine");
        }
    }

    /**
     * Poll results available.
     *
     * @param reviewId the review id
     * @param sourceBranch the source branch
     * @param targetBranch the target branch
     * @return true, if successful
     * @throws Exception the exception
     */
    private static boolean pollResultsAvailable(final String reviewId, final String sourceBranch, final String targetBranch) throws Exception {
        // https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/swagger-ui/index.html#/Branching/getMergeReview to get the status of the review, using the
        // id provided by the response on the prior post

        // GET: https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/merge-reviews/reviewid
        // If CURRENT, results are available
        final String reviewUrl = SnowstormConnection.getRestBaseUrl() + "merge-reviews/" + reviewId;

        LOG.info("merge polling change review URL: " + reviewUrl);

        try (final Response response = SnowstormConnection.getResponse(reviewUrl)) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                throw new Exception("Could not review branch change report for " + reviewId + "  merge from source: " + sourceBranch + " to target: "
                    + targetBranch + ". Status: " + Integer.toString(response.getStatus()) + ". Error: " + response.getStatusInfo().getReasonPhrase());
            }

            // create the body entity for the update call from the retrieved concept
            final ObjectMapper mapper = ThreadLocalMapper.get();
            final String resultString = SnowstormConnection.readEntityAsString(response);
            final JsonNode root = mapper.readTree(resultString);
            final String status = root.get("status").asText();

            // in a rebase that has changes clear all the caches for the target branch

            LOG.info("merge polling change review status: {} for reviewId: {}, on source: {} to target:{}", status, reviewId, sourceBranch, targetBranch);

            if (status.equalsIgnoreCase("PENDING")) {

                LOG.info("merge polling change review hasn't finished yet...  for reviewId: {}, on source: {} to target:{}", reviewId, sourceBranch,
                    targetBranch);

                return false;
            } else if (status.equalsIgnoreCase("failed")) {

                final String error = "Could not review branch change report for " + reviewId + "merge from source: " + sourceBranch + " to target: "
                    + targetBranch + ". Message: " + formatErrorMessage(resultString) + "Job failed with: " + root.get("message").asText();
                LOG.error(error);
                throw new Exception(error);

            } else if (status.equalsIgnoreCase("stale")) {

                // TODO: Test Stale based on approach discussed
                throw new Exception("ERROR - Missed handling STALE use case");
            } else if (status.equalsIgnoreCase("CURRENT")) {

                LOG.info("merge polling change review finished for reviewId: {}, on source: {} to target:{}", status, sourceBranch, targetBranch);
                return true;

            } else {

                final String error =
                    "review branch change report process not recognizing status: " + status + " for " + reviewId + "merge from source: " + sourceBranch
                        + " to target: " + targetBranch + ". Message: " + formatErrorMessage(resultString) + "Job failed with: " + root.get("message").asText();
                LOG.error(error);
                throw new Exception(error);
            }
        }

    }

    /**
     * Merge needed.
     *
     * @param mergeStatus the merge status
     * @param isRebase the is rebase
     * @return true, if successful
     */
    private static boolean mergeNeeded(final String mergeStatus, final boolean isRebase) {

        /*-
         *
         *  To ascertain the branch status - merge for:
         *   -- Rebase: a branch needs to be DIVERGED, BEHIND or STALE
         *   -- Promotion: a branch is FORWARD.
         */
        if (isRebase) {
            return List.of("DIVERGED", "BEHIND", "STALE").contains(mergeStatus.toUpperCase());
        } else {
            return List.of("FORWARD").contains(mergeStatus.toUpperCase());
        }
    }

    /**
     * Conflicts exist.
     *
     * @param reviewId the review id
     * @return true, if successful
     * @throws Exception the exception
     */
    private static boolean conflictsExist(final String reviewId) throws Exception {

        // https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/merge-reviews/asdf/details
        final String url = SnowstormConnection.getRestBaseUrl() + "merge-reviews/" + reviewId + "/details";

        LOG.info("conflictsExist url: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = SnowstormConnection.readEntityAsString(response);

            final ObjectMapper mapper = ThreadLocalMapper.get();
            final JsonNode organizationJsonRootNode = mapper.readTree(resultString);

            LOG.info("conflict exists result: " + !organizationJsonRootNode.isEmpty());

            return !organizationJsonRootNode.isEmpty();
        }
    }

    /**
     * Determine branch status.
     *
     * @param branch the branch
     * @return the string
     * @throws Exception the exception
     */
    private static String determineBranchStatus(final String branch) throws Exception {

        // https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/branches/MAIN/SNOMEDCT-BE?includeInheritedMetadata=true
        final String url = SnowstormConnection.getRestBaseUrl() + "branches/" + branch + "?includeInheritedMetadata=true";

        LOG.info("branch merge necessitated status url: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = SnowstormConnection.readEntityAsString(response);

            final ObjectMapper mapper = ThreadLocalMapper.get();
            final JsonNode organizationJsonRootNode = mapper.readTree(resultString);

            if (!organizationJsonRootNode.has("state")) {
                throw new Exception("determineBranchStatus - Branch doesn't exist: " + branch);
            }

            final String branchStatus = organizationJsonRootNode.get("state").asText();

            LOG.info("branch merge necessitated status result: " + branchStatus);

            return branchStatus;
        }
    }

    /**
     * Perform merge.
     *
     * @param sourceBranch the source branch
     * @param targetBranch the target branch
     * @param comment the comment
     * @param reviewId the review id
     * @param isRebase the is rebase
     * @throws Exception the exception
     */
    private static void performMerge(final String sourceBranch, final String targetBranch, final String comment, final String reviewId, final boolean isRebase)
        throws Exception {
        // Then finally we call: https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/swagger-ui/index.html#/Branching/mergeBranch with the same source and
        // target as the merge-review, and the ID of the above current merge-review

        final String mergeUrl = SnowstormConnection.getRestBaseUrl() + "merges";
        final ObjectMapper mapper = ThreadLocalMapper.get();
        final ObjectNode body = mapper.createObjectNode();

        // Populate body of POST
        body.put("source", sourceBranch).put("target", targetBranch);
        if (comment != null) {

            body.put("commitComment", comment);
        }

        if (isRebase && reviewId != null) {
            body.put("reviewId", reviewId);
        }

        // Post Call to snowstorm
        LOG.info("perform branch merge URL: {}; body: {}", mergeUrl , body);

        try (final Response response = SnowstormConnection.postResponse(mergeUrl, body.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode() && response.getStatus() != Response.Status.CREATED.getStatusCode()) {

                final String error =
                    "Could not review branch rebase of " + sourceBranch + " into branch " + targetBranch + ". Message: " + formatErrorMessage(response);
                LOG.error(error);
                throw new Exception(error);
            }

            final String resultString = SnowstormConnection.readEntityAsString(response);

            LOG.info("perform branch merge result: " + resultString);
        }
    }

    /**
     * Creates the merge review.
     *
     * @param sourceBranch the source branch
     * @param targetBranch the target branch
     * @return the string
     * @throws Exception the exception
     */
    private static String createMergeReview(final String sourceBranch, final String targetBranch) throws Exception {

        // POST such as: https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/merge-reviews
        // with body: { "source":"MAIN/SNOMEDCT-BE", "target":"MAIN/SNOMEDCT-BE/BEREFSETS" }

        final ObjectMapper mapper = ThreadLocalMapper.get();
        final ObjectNode body = mapper.createObjectNode().put("source", sourceBranch).put("target", targetBranch);
        final String reviewUrl = SnowstormConnection.getRestBaseUrl() + "merge-reviews";
        String jobStatusUrl = null;
        String reviewId = "";

        LOG.info("create merge review request URL: {}; body: {}" , reviewUrl , body);

        try (final Response response = SnowstormConnection.postResponse(reviewUrl, body.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode() && response.getStatus() != Response.Status.CREATED.getStatusCode()) {

                final String error =
                    "Could not review branch rebase of " + sourceBranch + " into branch " + targetBranch + ". Message: " + formatErrorMessage(response);
                LOG.error(error);
                throw new Exception(error);
            }

            jobStatusUrl = response.getHeaderString("Location");
            final String[] location = jobStatusUrl.split(PATH_DELIMITER);
            reviewId = location[location.length - 1];

            LOG.info("create merge review request reviewId: {}", reviewId);

            return reviewId;
        }
    }

    /**
     * Merge one branch into another.
     *
     * @param sourceBranchPath the branch path with the content to merge
     * @param targetBranchPath the branch path to merge content into
     * @param comment the merge comment
     * @param rebase is this a rebase or a promotion
     * @throws Exception the exception
     */
    public static void mergeBranch(final String sourceBranchPath, final String targetBranchPath, final String comment, final boolean rebase) throws Exception {

        /*-
        1) So for the authoring platform specifically - we call https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/swagger-ui/index.html#/Branching/retrieveBranch - to ascertain the branch status - a branch needs to be DIVERGED, BEHIND or STALE in order to be rebased, and we only allow promotion in the case that the branch is FORWARD.
        2) For a rebase we then call: https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/swagger-ui/index.html#/Branching/createMergeReview - with the source being the parent branch, and the target the branch we want to rebase -- for DIVERGED OR STALE
        3) We then poll: https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/swagger-ui/index.html#/Branching/getMergeReview to get the status of the review, using the id provided by the response on the prior post - looking for status ‘CURRENT’  -- for DIVERGED OR STALE
        4) Next we call https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/swagger-ui/index.html#/Branching/getMergeReviewConflictingConcepts - and hope that it’s empty - I presume for RT2 it almost always (or always) is -- for DIVERGED OR STALE
        5) Then finally we call: https://dev-snowstorm.ihtsdotools.org/snowstorm/snomed-ct/swagger-ui/index.html#/Branching/mergeBranch with the same source and target as the merge-review, and the ID of the above current merge-review
         */

        final long start = System.currentTimeMillis();
        final String mergeUrl = SnowstormConnection.getRestBaseUrl() + "merges";
        final ObjectMapper mapper = ThreadLocalMapper.get();
        final ObjectNode body = mapper.createObjectNode().put("source", sourceBranchPath).put("target", targetBranchPath);
        boolean jobDone = false;

        final String branchToTest = rebase ? targetBranchPath : sourceBranchPath;
        final String initialBranchStatus = determineBranchStatus(branchToTest);

        if (!mergeNeeded(initialBranchStatus, rebase)) {
            return;
        }

        if (comment != null) {

            body.put("commitComment", comment);
        }

        if (rebase && !"BEHIND".equalsIgnoreCase(initialBranchStatus)) {

            final String reviewId = mergeRebaseReview(sourceBranchPath, targetBranchPath);
            body.put("reviewId", reviewId);
        }

        LOG.debug("mergeBranch URL: {}; body: {}", mergeUrl, body);

        try (final Response response = SnowstormConnection.postResponse(mergeUrl, body.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode() && response.getStatus() != Response.Status.CREATED.getStatusCode()) {

                LOG.error("mergeBranch response status: {}, reason: {}", response.getStatus(), formatErrorMessage(response));
                final String error =
                    "Could not merge branch " + sourceBranchPath + " into branch " + targetBranchPath + ". Message: " + formatErrorMessage(response);
                LOG.error(error);
                throw new Exception(error);
            }

            final String jobStatusUrl = response.getHeaderString("Location");

            LOG.debug("Merge status info at {}", jobStatusUrl);

            while (!jobDone) {

                try (final Response mergeInfoResponse = SnowstormConnection.getResponse(jobStatusUrl)) {

                    final String resultString = SnowstormConnection.readEntityAsString(mergeInfoResponse);
                    final JsonNode root = mapper.readTree(resultString);
                    final String status = root.get("status").asText();

                    LOG.info("Merge status for source: {} to target: {} is: {}", sourceBranchPath, targetBranchPath, status);

                    if (status.equals("FAILED")) {

                        final String message = root.get("message").asText();
                        jobDone = true;

                        if (!message.contains("is not meaningful")) {

                            final String error = "Could not merge branch " + sourceBranchPath + " into branch " + targetBranchPath + ". Message: "
                                + formatErrorMessage(response);
                            LOG.error(error);
                            throw new Exception(error);

                        } else {
                            LOG.debug("Merge did not occurr. {}", message);
                        }

                    } else if (status.equals("PENDING") || status.equals("IN_PROGRESS") || status.equals("SCHEDULED")) {

                        LOG.debug("Merge hasn't finished yet for source: {} to target: {}", sourceBranchPath, targetBranchPath);

                        try {
                            Thread.sleep(1_000);
                        } catch (final InterruptedException ex) {
                            Thread.currentThread().interrupt();
                        }

                    } else {

                        // Merge is done at this point as teh status is not PENDING/IN_PROGRESS/SCHEDULED or error
                        jobDone = true;

                        if (rebase) {

                            // in a rebase that has changes clear all the caches for the target branch
                            RefsetService.clearAllRefsetCaches(targetBranchPath);
                            RefsetMemberService.clearAllMemberCaches(targetBranchPath);
                        } else {

                            try {

                                LOG.debug("Merge promotion sleep 1000ms to let snowstorm caches update.");
                                Thread.sleep(1_000);
                            } catch (final InterruptedException ex) {
                                Thread.currentThread().interrupt();
                            }

                            // final check that the promotion has finished.
                            boolean stateGood = false;
                            final String stateUrl = SnowstormConnection.getRestBaseUrl() + "branches/" + targetBranchPath;
                            LOG.debug("Promoted branch state info at {}", stateUrl);

                            while (!stateGood) {

                                try (final Response stateResponse = SnowstormConnection.getResponse(stateUrl)) {

                                    final String stateResultString = SnowstormConnection.readEntityAsString(stateResponse);
                                    final JsonNode stateRoot = mapper.readTree(stateResultString);
                                    final String state = stateRoot.get("state").asText();

                                    LOG.info("Promoted branch state is: {}", state);

                                    if (state.equals("FORWARD") || state.equals("CURRENT") || state.equals("UP_TO_DATE")) {
                                        stateGood = true;

                                    } else {

                                        try {

                                            LOG.debug("Merge promotion sleep 300ms to let snowstorm caches update.");
                                            Thread.sleep(1_000);
                                        } catch (final InterruptedException ex) {
                                            Thread.currentThread().interrupt();
                                        }
                                    }
                                }
                            }
                        }

                        LOG.info("Merged branch {} into branch {}. Time: {}", sourceBranchPath , targetBranchPath , (System.currentTimeMillis() - start));
                    }
                }
            }
        }
    }

    /**
     * Perform a merge rebase review.
     *
     * @param sourceBranchPath the branch path with the content to merge
     * @param targetBranchPath the branch path to merge content into
     * @return the review ID
     * @throws Exception the exception
     */
    public static String mergeRebaseReview(final String sourceBranchPath, final String targetBranchPath) throws Exception {

        final ObjectMapper mapper = ThreadLocalMapper.get();
        final ObjectNode body = mapper.createObjectNode().put("source", sourceBranchPath).put("target", targetBranchPath);
        final String reviewUrl = SnowstormConnection.getRestBaseUrl() + "merge-reviews";
        String jobStatusUrl = null;
        boolean jobDone = false;
        String reviewId = "";
        LOG.debug("mergeRebaseReview review URL: {}; body: {}", reviewUrl, body);

        try (final Response response = SnowstormConnection.postResponse(reviewUrl, body.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode() && response.getStatus() != Response.Status.CREATED.getStatusCode()) {

                final String error =
                    "Could not review branch rebase of " + sourceBranchPath + " into branch " + targetBranchPath + ". Message: " + formatErrorMessage(response);
                LOG.error(error);
                throw new Exception(error);
            }

            jobStatusUrl = response.getHeaderString("Location");
            final String[] location = jobStatusUrl.split(PATH_DELIMITER);
            reviewId = location[location.length - 1];
        }

        LOG.debug("mergeRebaseReview review job status URL: {}", jobStatusUrl);

        while (!jobDone) {

            try (final Response response = SnowstormConnection.getResponse(jobStatusUrl)) {

                String error = "Could not review merge branch " + sourceBranchPath + " into branch " + targetBranchPath + ". ";

                // Only process payload if Rest call is successful
                if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                    LOG.error("{} Status: {}. Message: {}", error , response.getStatus(), formatErrorMessage(response));
                }

                final String resultString = SnowstormConnection.readEntityAsString(response);
                final JsonNode root = mapper.readTree(resultString);
                final String status = root.get("status").asText();
                LOG.info("merge review status: {}, source:{}, target:{}", status, sourceBranchPath, targetBranchPath);

                if ("pending".equalsIgnoreCase(status)) {

                    LOG.debug("Merge review hasn't finished yet...");

                    try {
                        Thread.sleep(1_000);
                    } catch (final InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }

                } else if ("failed".equalsIgnoreCase(status)) {

                    error += "Job failed with: " + root.get("message").asText();
                    LOG.error(error);
                    throw new Exception(error);

                } else if ("stale".equalsIgnoreCase(status)) {

                    reviewId = mergeRebaseReview(sourceBranchPath, targetBranchPath);
                    jobDone = true;

                } else {
                    jobDone = true;
                }
            }
        }

        return reviewId;
    }

    /**
     * Rebase edit branch contents.
     *
     * @param branch the refset
     * @return the string
     * @throws Exception the exception
     */
    public static String rebaseEditBranchContents(final BranchInformation branchInformation) throws Exception {

        final String refsetBranchPath = getRefsetBranchPath(branchInformation);

        if (!StringUtils.isEmpty(branchInformation.getEditBranchId())) {

            final String editBranchPath = getEditBranchPath(branchInformation);

            if (doesBranchExist(editBranchPath)) {
                LOG.info("rebase Edit Branch for refset: {}", branchInformation.getRefsetId());

                mergeBranch(refsetBranchPath, editBranchPath, "Updating editBranchPath branch to latest changes", true);
                return editBranchPath;
            }

            throw new Exception("Expected edit branch: " + editBranchPath + " not found under refset branch: " + refsetBranchPath);
        }

        LOG.info("No Edit Branch to Rebase for refset: {}", branchInformation.getRefsetId());

        return null;
    }

    /**
     * Rebase refset branch contents.
     *
     * @param branch the refset
     * @return the string
     * @throws Exception the exception
     */
    public static String rebaseRefsetBranchContents(final BranchInformation branchInformation) throws Exception {

        // Rebase Edition branch to Refset Project branch
        final String refsetProjectBranchPath = getProjectBranchPath(branchInformation.getEditionBranch());
        mergeBranch(branchInformation.getEditionBranch(), refsetProjectBranchPath, "Rebasing Edition to refsetProject to latest changes", true);

        // Rebase Refset Project branch to Refset branch (creating it if first time in development)
        if (StringUtils.isEmpty(branchInformation.getRefsetBranchId())) {
            branchInformation.setRefsetBranchId(generateBranchId());
            createRefsetBranch(branchInformation);
        }

        final String refsetBranchPath = getRefsetBranchPath(branchInformation);

        if (branchInformation.isLocalSet()) {
            // TODO: Should this be the first step of the project?
            final String topLevelRefsetBranchPath = getLocalsetTopLevelRefsetBranchPath(branchInformation.getEditionBranch(), branchInformation.getRefsetId());
            mergeBranch(refsetProjectBranchPath, topLevelRefsetBranchPath, "Updating topLevelRefsetBranchPathbranch to latest changes", true);
            mergeBranch(topLevelRefsetBranchPath, refsetBranchPath, "Updating refsetBranchPath branch to latest changes", true);
        } else {
            mergeBranch(refsetProjectBranchPath, refsetBranchPath, "Updating refsetBranchPath branch to latest changes", true);
        }

        return refsetBranchPath;
    }
}
