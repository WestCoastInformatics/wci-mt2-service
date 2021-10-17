
package org.ihtsdo.refsetservice.terminologyservice;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.WorkflowHistory;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.DateUtility;
import org.ihtsdo.refsetservice.util.FieldedStringTokenizer;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Utility class for workflow processes.
 */
public final class WorkflowService {

    /** The logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(WorkflowService.class);

    /** The prefix to use for a refset branch . */
    public static final String REFSET_BRANCH_PREFIX = "refset-";

    /** The name of a refset edit branch . */
    public static final String EDIT_BRANCH_NAME = "edit";

    /**
     * The name of a temporary branch to create empty concepts in to generate
     * concept IDs for new refsets.
     */
    public static final String TEMP_BRANCH_NAME = "temp";

    /** The PUBLISHED workflow status . */
    public static final String PUBLISHED = "PUBLISHED";

    /** The READY_FOR_EDIT workflow status . */
    public static final String READY_FOR_EDIT = "READY_FOR_EDIT";

    /** The IN_EDIT workflow status . */
    public static final String IN_EDIT = "IN_EDIT";

    /** The READY_FOR_REVIEW workflow status . */
    public static final String READY_FOR_REVIEW = "READY_FOR_REVIEW";

    /** The IN_REVIEW workflow status . */
    public static final String IN_REVIEW = "IN_REVIEW";

    /** The REVIEW_COMPLETED workflow status . */
    public static final String REVIEW_COMPLETED = "REVIEW_COMPLETED";

    /** The READY_FOR_PUBLICATION workflow status . */
    public static final String READY_FOR_PUBLICATION = "READY_FOR_PUBLICATION";

    /** The EDIT workflow action . */
    public static final String CREATE = "CREATE";

    /** The EDIT workflow action . */
    public static final String EDIT = "EDIT";

    /** The FINISH_EDIT workflow action . */
    public static final String FINISH_EDIT = "FINISH_EDIT";

    /** The REQUEST_REVIEW workflow action . */
    public static final String REQUEST_REVIEW = "REQUEST_REVIEW";

    /** The WITHDRAW workflow action . */
    public static final String WITHDRAW = "WITHDRAW";

    /** The REVIEW workflow action . */
    public static final String REVIEW = "REVIEW";

    /** The REJECT_REVIEW workflow action . */
    public static final String REJECT_REVIEW = "REJECT_REVIEW";

    /** The ACCEPT_REVIEW workflow action . */
    public static final String ACCEPT_REVIEW = "ACCEPT_REVIEW";

    /** The UNASSIGN workflow action . */
    public static final String UNASSIGN = "UNASSIGN";

    /** The REQUEST_PUBLICATION workflow action . */
    public static final String REQUEST_PUBLICATION = "REQUEST_PUBLICATION";

    /** The FAILS_RVF workflow action . */
    public static final String FAILS_RVF = "FAILS_RVF";

    /** The REFSET_PUBLISHED workflow action . */
    public static final String REFSET_PUBLISHED = "REFSET_PUBLISHED";

    /** The order of workflow steps . */
    public static final List<String> WORKFLOW_STATUSES =
            new ArrayList<>(Arrays.asList(READY_FOR_EDIT, IN_EDIT, READY_FOR_REVIEW, IN_REVIEW,
                    REVIEW_COMPLETED, READY_FOR_PUBLICATION, PUBLISHED));

    /** The order of workflow actions . */
    public static final List<String> WORKFLOW_ACTIONS =
            new ArrayList<>(Arrays.asList(EDIT, FINISH_EDIT, REQUEST_REVIEW, REVIEW, REJECT_REVIEW,
                    ACCEPT_REVIEW, UNASSIGN, REQUEST_PUBLICATION, FAILS_RVF, REFSET_PUBLISHED));

    /** The file that contains workflow actions by user and step. */
    private static final String WORKFLOW_PERMUTATIONS_FILE_NAME =
            "workflow/workflowPermutationsToFinalAction.txt";

    /** The file that contains workflow actions. */
    private static final String WORKFLOW_ACTIONS_FILE_NAME = "workflow/workflowActions.txt";

    /** The file that contains workflow statuses. */
    private static final String WORKFLOW_STATUSES_FILE_NAME = "workflow/workflowStatuses.txt";

    /** The workflow actions by user and step. */
    private static Map<String, Map<String, Map<String, String>>> WORKFLOW_PERMUTATIONS =
            new HashMap<>();

    static {

        try {

            // WORKFLOW_ACTIONS =
            // FileUtility.readFileToArray(WORKFLOW_ACTIONS_FILE_NAME);
            // WORKFLOW_STATUSES =
            // FileUtility.readFileToArray(WORKFLOW_STATUSES_FILE_NAME);
            //
            // read in the actions by user and step
            ClassPathResource workflowPermutationsResource =
                    new ClassPathResource(WORKFLOW_PERMUTATIONS_FILE_NAME);
            try (BufferedReader bufferedReader = new BufferedReader(
                    new InputStreamReader(workflowPermutationsResource.getInputStream()))) {
                String line;

                while ((line = bufferedReader.readLine()) != null) {

                    String[] tokens = FieldedStringTokenizer.split(line, ",");

                    if (tokens.length != 4) {
                        throw new Exception(WORKFLOW_PERMUTATIONS_FILE_NAME
                                + " does not have 4 items per line");
                    }

                    final String user = tokens[0].toUpperCase().strip();
                    final String currentState = tokens[1].toUpperCase().strip();
                    final String action = tokens[2].toUpperCase().strip();
                    final String resultingState = tokens[3].toUpperCase().strip();

                    if (!WORKFLOW_PERMUTATIONS.containsKey(user)) {
                        WORKFLOW_PERMUTATIONS.put(user, new HashMap<String, Map<String, String>>());
                    }

                    if (!WORKFLOW_PERMUTATIONS.get(user).containsKey(currentState)) {
                        WORKFLOW_PERMUTATIONS.get(user).put(currentState, new HashMap<>());
                    }

                    WORKFLOW_PERMUTATIONS.get(user).get(currentState).put(action, resultingState);
                }
            }

        } catch (Exception e) {
            throw new RuntimeException(" Unable to read worflow file: " + e.getMessage());
        }
    }

    /**
     * Instantiates an empty {@link WorkflowService}.
     */
    private WorkflowService() {
        // n/a
    }

    /**
     * Set the workflow status for the refset.
     *
     * @param user the user
     * @param action the action
     * @param refset the refset
     * @param notes the workflow status notes
     * @param status the new workflow status
     * @return the updated refset
     * @throws Exception the exception
     */
    public static Refset setWorkflowStatus(final User user, final String action,
        final Refset refset, final String notes, final String status) throws Exception {

        if (WorkflowService.getAllowedActions(user, refset).contains(action)) {

            final Refset updatedRefset = setRefsetWorkflowStatus(user, refset, status);
            addWorkflowHistory(user, action, refset, notes);
            return updatedRefset;
        } else {

            logger.error("Unsuccessful attempt to update workflow status for refset "
                    + refset.getId() + " from status " + refset.getWorkflowStatus()
                    + " with action " + action);
            return refset;
        }

    }

    /**
     * Set the workflow status for the refset based on the action the user took.
     *
     * @param user the user
     * @param action the action
     * @param refset the refset
     * @param notes the workflow status notes
     * @param action the action the user took
     * @return the updated refset
     * @throws Exception the exception
     */
    public static Refset setWorkflowStatusByAction(final User user, final String action,
        final Refset refset, final String notes) throws Exception {

        final String currentStatus = refset.getWorkflowStatus();
        String role = User.ROLE_AUTHOR;

        if (Arrays.asList(READY_FOR_REVIEW, IN_REVIEW).contains(currentStatus)) {
            role = User.ROLE_REVIEWER;
        }

        // get the next status based on the user, current status, and supplied
        // action
        final String nextStatus =
                WORKFLOW_PERMUTATIONS.get(role).get(refset.getWorkflowStatus()).get(action);
        final Refset updatedRefset = setWorkflowStatus(user, action, refset, notes, nextStatus);

        // if edits have just been completed then merge the edit branch into the
        // refset branch and delete the edit branch
        if (currentStatus.equals(IN_EDIT)
                && (Arrays.asList(REQUEST_REVIEW, REQUEST_PUBLICATION).contains(action))) {

            final boolean merged = mergeEditIntoRefsetBranch(refset.getEditionBranch(),
                    refset.getRefsetId(), notes);

            if (merged) {
                deleteEditBranch(refset.getEditionBranch(), refset.getRefsetId());
            } else {

                final String message = "Unable to merge edit into refset branch for refset "
                        + refset.getRefsetId() + " because the edit branch doesn't exist.";
                logger.error(message);
                throw new Exception(message);
            }

        }

        // else if this is the start of edits create the refset edit branch
        else if (action.equals(EDIT)) {
            createEditBranch(refset.getEditionBranch(), refset.getRefsetId());
        }

        // if publication is being requested merge the refset branch into the
        // edition branch
        if (action.equals(REQUEST_PUBLICATION)) {

            final boolean merged = mergeRefsetIntoEditionBranch(refset.getEditionBranch(),
                    refset.getRefsetId(), notes);

            if (!merged) {
                final String message = "Unable to merge refset into edition branch for refset "
                        + refset.getRefsetId() + " because the refset branch doesn't exist.";
                logger.error(message);
                throw new Exception(message);
            }
        }

        return updatedRefset;
    }

    /**
     * Update the refset to a new workflow status.
     *
     * @param user the user
     * @param refset the refset
     * @param status the new workflow status
     * @return the updated refset
     * @throws Exception the exception
     */
    public static Refset setRefsetWorkflowStatus(final User user, final Refset refset,
        final String status) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);

            refset.setWorkflowStatus(status);

            // Published is the final status so set the version information
            if (status.equals(PUBLISHED)) {

                // get the latest edition version branch
                final ResultList<String> branchVersions =
                        RefsetService.getBranchVersions(refset.getEditionBranch());

                if (branchVersions.getItems().size() < 1) {

                    final String message = "Could not retrieve branch versions for branch "
                            + refset.getEditionBranch();
                    logger.error(message);
                    throw new Exception(message);
                }

                final String newVersion = branchVersions.getItems().get(0);

                refset.setVersionDate(RefsetService.getRefsetDateFromFormattedString(newVersion));
                refset.setVersionStatus(Refset.PUBLISHED);
            }

            // Update an object
            service.update(refset);
            logger.info(
                    "Refset workflow status set to " + status + " for refset " + refset.getId());

            // update the refset permissions
            return RefsetService.setRefsetPermissions(user, refset);
        }
    }

    /**
     * Add an entry in the workflow history table.
     *
     * @param user the user
     * @param action the action
     * @param refset the refset
     * @param notes the workflow status notes
     * @throws Exception the exception
     */
    public static void addWorkflowHistory(final User user, final String action, final Refset refset,
        final String notes) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);

            final WorkflowHistory workflow = new WorkflowHistory(user.getUserName(),
                    refset.getWorkflowStatus(), action, notes, refset);

            // Add an object
            service.add(workflow);
            final String newWorkflowId = workflow.getId();

            if (newWorkflowId == null) {
                throw new Exception("Unable to create a new workflow history entry.");
            }

            logger.info("New workflow history entry with status " + refset.getWorkflowStatus()
                    + " added for refset " + refset.getId());
        }
    }

    /**
     * Get the current workflow for a refset.
     *
     * @param refset the refset
     * @return the current workflow object
     * @throws Exception the exception
     */
    public static WorkflowHistory getCurrentWorkflow(final Refset refset) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();
            pfs.setSort("modified");
            pfs.setAscending(false);
            pfs.setLimit(1);

            ResultList<WorkflowHistory> results =
                    service.find("refsetId:" + QueryParserBase.escape(refset.getId()) + "", pfs,
                            WorkflowHistory.class, null);

            if (results.getItems().size() == 0) {
                throw new Exception("Unable to retrieve worflow for refset " + refset.getId());
            }

            return results.getItems().get(0);
        }
    }

    /**
     * Get the workflow history for a refset.
     *
     * @param refset the refset
     * @param searchParameters the search parameters
     * @return the current workflow object
     * @throws Exception the exception
     */
    public static ResultList<WorkflowHistory> getWorkflowHistory(final Refset refset,
        final SearchParameters searchParameters) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

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
                query = " AND " + IndexUtility.addWildcardsToQuery(searchParameters.getQuery(),
                        WorkflowHistory.class);
            }

            ResultList<WorkflowHistory> results =
                    service.find("refsetId:" + QueryParserBase.escape(refset.getId()) + query, pfs,
                            WorkflowHistory.class, null);

            logger.debug("******** getWorkflowHistory results: " + ModelUtility.toJson(results));

            return results;
        }
    }

    /**
     * Update the notes for the current workflow status.
     *
     * @param user the user
     * @param refset the refset
     * @param notes the notes
     * @return the current workflow object
     * @throws Exception the exception
     */
    public static void updateWorkflowNote(final User user, final Refset refset, final String notes)
        throws Exception {

        WorkflowHistory workflow = getCurrentWorkflow(refset);

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);

            workflow.setNotes(notes);

            // Update an object
            service.update(workflow);
            logger.info("Note for workflow history entry with status " + refset.getWorkflowStatus()
                    + " updated for refset " + refset.getId());
        }
    }

    /**
     * Get the current assigned username.
     *
     * @param refset the refset
     * @return the current assigned username
     * @throws Exception the exception
     */
    public static String getAssignedUserName(final Refset refset) throws Exception {

        // if the refset isn't being edited or reviewed no one is assigned
        if (!Arrays.asList(IN_EDIT, IN_REVIEW).contains(refset.getWorkflowStatus())) {
            return "";
        }

        WorkflowHistory workflow = getCurrentWorkflow(refset);
        return workflow.getUserName();

    }

    /**
     * Get the refset branch path for a refset.
     *
     * @param editionBranchPath the branch path of the edition the refset
     *            belongs to
     * @param refsetId the refset ID
     * @return the branch path of the refset branch
     * @throws Exception the exception
     */
    public static String getRefsetBranchPath(final String editionBranchPath, final String refsetId)
        throws Exception {
        return editionBranchPath + "/" + REFSET_BRANCH_PREFIX + refsetId;
    }

    /**
     * Create the refset branch for an IN DEVELOPMENT version.
     *
     * @param editionBranchPath the branch path of the edition to create the new
     *            branch in
     * @param refsetId the refset ID
     * @return the branch path of the new refset branch
     * @throws Exception the exception
     */
    public static String createRefsetBranch(final String editionBranchPath, final String refsetId)
        throws Exception {

        final String branchName = REFSET_BRANCH_PREFIX + refsetId;
        final String refsetBranchPath = getRefsetBranchPath(editionBranchPath, refsetId);

        if (doesBranchExist(refsetBranchPath)) {
            return refsetBranchPath;
        } else {
            return createBranch(editionBranchPath, branchName);
        }
    }

    /**
     * Merge the refset branch into the edition branch.
     *
     * @param editionBranchPath the branch path of the edition the refset
     *            belongs to
     * @param refsetId the refset ID
     * @param comment the merge comment
     * @return were the branches merged
     * @throws Exception the exception
     */
    public static boolean mergeRefsetIntoEditionBranch(final String editionBranchPath,
        final String refsetId, final String comment) throws Exception {

        final String refsetBranchPath = getRefsetBranchPath(editionBranchPath, refsetId);

        if (doesBranchExist(refsetBranchPath)) {

            mergeBranch(refsetBranchPath, editionBranchPath, comment);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Delete the refset branch for a refset.
     *
     * @param editionBranchPath the branch path of the edition to create the new
     *            branch in
     * @param refsetId the refset ID
     * @return was the branch deleted
     * @throws Exception the exception
     */
    public static boolean deleteRefsetBranch(final String editionBranchPath, final String refsetId)
        throws Exception {

        final String branchPath = getRefsetBranchPath(editionBranchPath, refsetId);
        return deleteBranch(branchPath);
    }

    /**
     * Get the edit branch path for a refset.
     *
     * @param editionBranchPath the branch path of the edition the refset
     *            belongs to
     * @param refsetId the refset ID
     * @return the branch path of the edit branch
     * @throws Exception the exception
     */
    public static String getEditBranchPath(final String editionBranchPath, final String refsetId)
        throws Exception {
        return editionBranchPath + "/" + REFSET_BRANCH_PREFIX + refsetId + "/" + EDIT_BRANCH_NAME;
    }

    /**
     * Create the edit branch for a refset.
     *
     * @param editionBranchPath the branch path of the edition to create the new
     *            branch in
     * @param refsetId the refset ID
     * @return the branch path of the new edit branch
     * @throws Exception the exception
     */
    public static String createEditBranch(final String editionBranchPath, final String refsetId)
        throws Exception {

        final String refsetBranchPath = getRefsetBranchPath(editionBranchPath, refsetId);
        final String editBranchPath = getEditBranchPath(editionBranchPath, refsetId);

        if (doesBranchExist(editBranchPath)) {
            return editBranchPath;
        } else {
            return createBranch(refsetBranchPath, EDIT_BRANCH_NAME);
        }
    }

    /**
     * Merge the edit branch into the refset branch.
     *
     * @param editionBranchPath the branch path of the edition the refset
     *            belongs to
     * @param refsetId the refset ID
     * @param comment the merge comment
     * @return were the branches merged
     * @throws Exception the exception
     */
    public static boolean mergeEditIntoRefsetBranch(final String editionBranchPath,
        final String refsetId, final String comment) throws Exception {

        final String refsetBranchPath = getRefsetBranchPath(editionBranchPath, refsetId);
        final String editBranchPath = getEditBranchPath(editionBranchPath, refsetId);

        if (doesBranchExist(refsetBranchPath) && doesBranchExist(editBranchPath)) {

            mergeBranch(editBranchPath, refsetBranchPath, comment);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Delete the edit branch for a refset.
     *
     * @param editionBranchPath the branch path of the edition to create the new
     *            branch in
     * @param refsetId the refset ID
     * @return was the branch deleted
     * @throws Exception the exception
     */
    public static boolean deleteEditBranch(final String editionBranchPath, final String refsetId)
        throws Exception {

        final String branchPath = getEditBranchPath(editionBranchPath, refsetId);
        return deleteBranch(branchPath);
    }

    /**
     * Create a branch.
     *
     * @param parentBranchPath the branch path of the parent to create the new
     *            branch in
     * @param branchName the name the new branch
     * @return the branch path of the new branch
     * @throws Exception the exception
     */
    public static String createBranch(final String parentBranchPath, final String branchName)
        throws Exception {

        String refsetBranchPath = null;
        final String url = SnowstormConnection.BASE_URL + "branches";
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode body =
                mapper.createObjectNode().put("name", branchName).put("parent", parentBranchPath);

        logger.debug("createBranch URL: " + url + " ; body: " + body.toString());

        try (final Response response = SnowstormConnection.postResponse(url, body.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                final String error =
                        "Could not create branch " + parentBranchPath + "/" + branchName;
                logger.error(error);
                throw new Exception(error);
            }

            final String resultString = response.readEntity(String.class);

            final JsonNode root = mapper.readTree(resultString.toString());
            JsonNode rootNode = root;

            if (rootNode.has("path")) {
                refsetBranchPath = rootNode.get("path").asText();
            }
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

        String refsetBranchPath = null;
        final String url =
                SnowstormConnection.BASE_URL + "admin/" + branchPath + "/actions/hard-delete";

        logger.debug("deleteBranch URL: " + url);

        try (final Response response = SnowstormConnection.deleteResponse(url)) {

            // Only process payload if Rest call is successful
            if (response.getStatus() == Response.Status.OK.getStatusCode()) {

                logger.info("Deleted branch " + branchPath);
                return true;
            } else {

                logger.error("Could not delete branch " + branchPath);
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

        final String url = SnowstormConnection.BASE_URL + "branches/" + branchPath;

        logger.debug("doesBranchExist URL: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            // If Rest call is successful then branch exists
            if (response.getStatus() == Response.Status.OK.getStatusCode()) {
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Merge one branch into another.
     *
     * @param sourceBranchPath the branch path with the content to merge
     * @param targetBranchPath the branch path to merge content into
     * @param comment the merge comment
     * @return the branch path of the new branch
     * @throws Exception the exception
     */
    public static void mergeBranch(final String sourceBranchPath, final String targetBranchPath,
        final String comment) throws Exception {

        String refsetBranchPath = null;
        final String url = SnowstormConnection.BASE_URL + "merges";
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode body = mapper.createObjectNode().put("source", sourceBranchPath)
                .put("target", targetBranchPath);

        if (comment != null) {
            body.put("commitComment", comment);
        }

        logger.debug("mergeBranch URL: " + url + " ; body: " + body.toString());

        try (final Response response = SnowstormConnection.postResponse(url, body.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()
                    && response.getStatus() != Response.Status.CREATED.getStatusCode()) {

                final String error = "Could not merge branch " + sourceBranchPath + " into branch "
                        + targetBranchPath;
                logger.error(error);
                throw new Exception(error);
            }
        }
    }

    /**
     * In order to create a refset branch for a new refset the SCTID needs to
     * get generated in a temp branch first.
     *
     * @param editionBranchPath the branch path of the temporary branch
     * @param branchName the name the new branch
     * @return the branch path of the new branch
     * @throws Exception the exception
     */
    public static String getNewRefsetId(final String editionBranchPath) throws Exception {

        String refsetConceptId = null;
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode body = mapper.createObjectNode();
        String tempBranchPath = null;

        if (doesBranchExist(editionBranchPath + "/" + TEMP_BRANCH_NAME)) {
            tempBranchPath = editionBranchPath + "/" + TEMP_BRANCH_NAME;
        } else {
            tempBranchPath = createBranch(editionBranchPath, TEMP_BRANCH_NAME);
        }

        final String url =
                SnowstormConnection.BASE_URL + "browser/" + tempBranchPath + "/" + "concepts/";

        logger.debug("getNewRefsetId URL: " + url);

        try (final Response response = SnowstormConnection.postResponse(url, body.toString())) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                throw new Exception(
                        "call to url '" + url + "' wasn't successful. " + response.toString());
            }

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                throw new Exception(Integer.toString(response.getStatus()));
            }

            final String resultString = response.readEntity(String.class);

            final JsonNode root = mapper.readTree(resultString.toString());
            JsonNode conceptNode = root;

            if (conceptNode.has("conceptId")) {
                refsetConceptId = conceptNode.get("conceptId").asText();
            } else {
                throw new Exception("Unable to create new refset concept.");
            }
        }

        return refsetConceptId;
    }

    /**
     * Get the next workflow status from the current one.
     *
     * @param currentStatus the current workflow status
     * @return if next workflow status, or null if at final status
     */
    public static String getNextWorkflowStatus(final String currentStatus) {

        // Published is the final status
        if (currentStatus.equals(PUBLISHED)) {
            return null;
        }

        final int currentStep = WORKFLOW_STATUSES.indexOf(currentStatus);
        return WORKFLOW_STATUSES.get(currentStep + 1);
    }

    /**
     * Get a list of workflow statuses that are allowed for the current user and
     * state of the refset.
     *
     * @param user the user
     * @param refset the refset
     * @return the list of allowed statuses
     * @throws Exception the exception
     */
    public static List<String> getAllowedStatuses(final User user, final Refset refset)
        throws Exception {

        final List<String> allowedStatuses = new ArrayList<>();
        final String currentStatus = refset.getWorkflowStatus();

        // Published is the final status so no edits are allowed anymore,
        if (currentStatus == null || currentStatus.equals(PUBLISHED)) {
            return allowedStatuses;
        }

        // only the assigned user can edit or review
        if (!user.getUserName().equals(refset.getAssignedUser())
                && Arrays.asList(IN_EDIT, IN_REVIEW).contains(currentStatus)) {
            return allowedStatuses;
        }

        // set status permissions for AUTHORS
        if (user.doesUserHavePermission(User.ROLE_AUTHOR, refset)) {

            if (Arrays.asList(REVIEW_COMPLETED, READY_FOR_PUBLICATION).contains(currentStatus)) {
                allowedStatuses.add(READY_FOR_EDIT);
            }

            if (Arrays.asList(READY_FOR_EDIT, READY_FOR_REVIEW, REVIEW_COMPLETED)
                    .contains(currentStatus)) {
                allowedStatuses.add(IN_EDIT);
            }

            if (Arrays.asList(READY_FOR_EDIT, IN_EDIT, REVIEW_COMPLETED).contains(currentStatus)) {
                allowedStatuses.add(READY_FOR_REVIEW);
            }

            if (Arrays.asList(READY_FOR_EDIT, IN_EDIT, READY_FOR_REVIEW).contains(currentStatus)) {
                allowedStatuses.add(READY_FOR_PUBLICATION);
            }

            if (Arrays.asList(READY_FOR_PUBLICATION).contains(currentStatus)) {
                allowedStatuses.add(PUBLISHED);
            }
        }

        // set status permissions for REVIEWERS
        if (user.doesUserHavePermission(User.ROLE_REVIEWER, refset)) {

            if (Arrays.asList(IN_REVIEW).contains(currentStatus)) {
                allowedStatuses.add(READY_FOR_EDIT);
            }

            if (Arrays.asList(READY_FOR_REVIEW).contains(currentStatus)) {
                allowedStatuses.add(IN_REVIEW);
            }

            if (Arrays.asList(IN_REVIEW).contains(currentStatus)) {
                allowedStatuses.add(REVIEW_COMPLETED);
            }
        }

        // set status permissions for ADMINS
        if (user.doesUserHavePermission(User.ROLE_ADMIN, refset)) {

            if (Arrays.asList(READY_FOR_PUBLICATION).contains(currentStatus)) {
                allowedStatuses.add(READY_FOR_EDIT);
            }

            if (Arrays.asList(READY_FOR_PUBLICATION).contains(currentStatus)) {
                allowedStatuses.add(PUBLISHED);
            }
        }

        return allowedStatuses;
    }

    /**
     * Get a list of workflow actions that are allowed for the current user and
     * state of the refset.
     *
     * @param user the user
     * @param refset the refset
     * @return the list of allowed actions
     * @throws Exception the exception
     */
    public static List<String> getAllowedActions(final User user, final Refset refset)
        throws Exception {

        final List<String> allowedActions = new ArrayList<>();
        final String currentStatus = refset.getWorkflowStatus();

        // Published is the final status so no edits are allowed anymore
        if (currentStatus.equals(PUBLISHED)) {
            return allowedActions;
        }

        if (currentStatus.equals(READY_FOR_EDIT)
                && user.doesUserHavePermission(User.ROLE_AUTHOR, refset)) {

            allowedActions.add(EDIT);
            allowedActions.add(REQUEST_REVIEW);
            allowedActions.add(REQUEST_PUBLICATION);

        } else if (currentStatus.equals(IN_EDIT)
                && user.doesUserHavePermission(User.ROLE_AUTHOR, refset)) {

            // only the assigned user can edit
            if (user.getUserName().equals(refset.getAssignedUser())) {

                allowedActions.add(FINISH_EDIT);
                allowedActions.add(REQUEST_REVIEW);
                allowedActions.add(REQUEST_PUBLICATION);
            }

        } else if (currentStatus.equals(READY_FOR_REVIEW)
                && user.doesUserHavePermission(User.ROLE_REVIEWER, refset)) {

            allowedActions.add(WITHDRAW);
            allowedActions.add(REVIEW);

        } else if (currentStatus.equals(IN_REVIEW)
                && user.doesUserHavePermission(User.ROLE_REVIEWER, refset)) {

            // only the assigned user can review
            if (user.getUserName().equals(refset.getAssignedUser())) {

                allowedActions.add(REJECT_REVIEW);
                allowedActions.add(ACCEPT_REVIEW);
                allowedActions.add(UNASSIGN);
            }

        } else if (currentStatus.equals(REVIEW_COMPLETED)
                && user.doesUserHavePermission(User.ROLE_AUTHOR, refset)) {

            allowedActions.add(EDIT);
            allowedActions.add(REQUEST_REVIEW);
            allowedActions.add(REQUEST_PUBLICATION);

        } else if (currentStatus.equals(READY_FOR_PUBLICATION)
                && (user.doesUserHavePermission(User.ROLE_AUTHOR, refset)
                        || user.doesUserHavePermission(User.ROLE_ADMIN, refset))) {

            allowedActions.add(FAILS_RVF);
            allowedActions.add(REFSET_PUBLISHED);
        }

        return allowedActions;
    }
}
