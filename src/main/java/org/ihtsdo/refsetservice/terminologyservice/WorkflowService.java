
package org.ihtsdo.refsetservice.terminologyservice;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.WorkflowHistory;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.AuditEntryHelper;
import org.ihtsdo.refsetservice.util.FieldedStringTokenizer;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
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

    /** The name of a refset project branch . */
    public static final String PROJECT_BRANCH_NAME = "REFSETS";

    /** The prefix to use for a refset branch . */
    public static final String REFSET_BRANCH_PREFIX = "REFSET-";

    /** The name of a refset edit branch . */
    public static final String EDIT_BRANCH_NAME = "EDIT-";

    /**
     * The name of a temporary branch to create empty concepts in to generate concept IDs for new refsets.
     */
    public static final String TEMP_BRANCH_NAME = "TEMP";

    /** The PUBLISHED workflow status . */
    public static final String PUBLISHED = "PUBLISHED";

    /** The READY_FOR_EDIT workflow status . */
    public static final String READY_FOR_EDIT = "READY_FOR_EDIT";

    /** The IN_EDIT workflow status . */
    public static final String IN_EDIT = "IN_EDIT";

    /** The IN_UPGRADE workflow status . */
    public static final String IN_UPGRADE = "IN_UPGRADE";

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

    /** The CANCEL EDIT workflow action . */
    public static final String CANCEL_EDIT = "CANCEL_EDIT";

    /** The FINISH_EDIT workflow action . */
    public static final String FINISH_EDIT = "FINISH_EDIT";

    /** The UPGRADE workflow action . */
    public static final String UPGRADE = "UPGRADE";

    /** The CANCEL UPGRADE workflow action . */
    public static final String CANCEL_UPGRADE = "CANCEL_UPGRADE";

    /** The FINISH_UPGRADE workflow action . */
    public static final String FINISH_UPGRADE = "FINISH_UPGRADE";

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
        new ArrayList<>(Arrays.asList(READY_FOR_EDIT, IN_EDIT, IN_UPGRADE, READY_FOR_REVIEW, IN_REVIEW, REVIEW_COMPLETED, READY_FOR_PUBLICATION, PUBLISHED));

    /** The order of workflow actions . */
    public static final List<String> WORKFLOW_ACTIONS = new ArrayList<>(Arrays.asList(EDIT, CANCEL_EDIT, FINISH_EDIT, UPGRADE, CANCEL_UPGRADE, FINISH_UPGRADE, REQUEST_REVIEW, REVIEW, REJECT_REVIEW,
        ACCEPT_REVIEW, UNASSIGN, REQUEST_PUBLICATION, FAILS_RVF, REFSET_PUBLISHED));

    /** The file that contains workflow actions by user and step. */
    private static final String WORKFLOW_PERMUTATIONS_FILE_NAME = "workflow/workflowPermutationsToFinalAction.txt";

    /** The file that contains workflow actions. */
    private static final String WORKFLOW_ACTIONS_FILE_NAME = "workflow/workflowActions.txt";

    /** The file that contains workflow statuses. */
    private static final String WORKFLOW_STATUSES_FILE_NAME = "workflow/workflowStatuses.txt";

    /** The workflow actions by user and step. */
    private static Map<String, Map<String, Map<String, String>>> WORKFLOW_PERMUTATIONS = new HashMap<>();

    static {

        try {

            // WORKFLOW_ACTIONS =
            // FileUtility.readFileToArray(WORKFLOW_ACTIONS_FILE_NAME);
            // WORKFLOW_STATUSES =
            // FileUtility.readFileToArray(WORKFLOW_STATUSES_FILE_NAME);
            //
            // read in the actions by user and step
            ClassPathResource workflowPermutationsResource = new ClassPathResource(WORKFLOW_PERMUTATIONS_FILE_NAME);

            try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(workflowPermutationsResource.getInputStream()))) {

                String line;

                while ((line = bufferedReader.readLine()) != null) {

                    String[] tokens = FieldedStringTokenizer.split(line, ",");

                    if (tokens.length != 4) {

                        throw new Exception(WORKFLOW_PERMUTATIONS_FILE_NAME + " does not have 4 items per line");
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
     * Start the publication of all Ready for Publication refsets in a code system by promoting them to the REFSETS branch.
     *
     * @param service the Terminology Service
     * @param editionShortName an code system to limit the refset to
     * @return A list of concepts that were unable to be promoted
     * @throws Exception the exception
     */
    public static List<String> startAllRefsetPublications(final TerminologyService service, final String editionShortName) throws Exception {

        List<String> refsetsNotUpdated = new ArrayList<>();
        String query = "workflowStatus: " + READY_FOR_PUBLICATION + " AND editionShortName: " + QueryParserBase.escape(editionShortName);

        final ResultList<Refset> results = service.find(query, null, Refset.class, null);

        // see if there is an "In Development" version as that should be the latest.
        for (final Refset refset : results.getItems()) {
            
            try {
                
                final String refsetBranchPath = getRefsetBranchPath(refset.getEditionBranch(), refset.getRefsetId(), refset.getRefsetBranchId());
                final String editBranchPath = getEditBranchPath(refset.getEditionBranch(), refset.getRefsetId(), refset.getEditBranchId(), refset.getRefsetBranchId());
                
                mergeBranch(getProjectBranchPath(refset.getEditionBranch()), refsetBranchPath, "Updating branch to latest changes", true);

                final boolean merged = mergeRefsetIntoProjectBranch(refset.getEditionBranch(), refset.getRefsetId(), refset.getRefsetBranchId(), "Preparing for publication");
    
                if (!merged) {
    
                    final String message = "Unable to merge refset into project branch for refset " + refset.getRefsetId() + " because the project branch doesn't exist.";
                    logger.error(message);
                    throw new Exception(message);
                }
            } catch (Exception e) {
                
                logger.error("Unable to merge refset into project branch for refset " + refset.getRefsetId() + " because: " + e.getMessage(), e);
                refsetsNotUpdated.add(refset.getRefsetId());
            }
        }

        return refsetsNotUpdated;
    }

    /**
     * Complete the publication of all Ready for Publication refsets.
     *
     * @param service the Terminology Service
     * @param versionDate the publication date of the refset in YYYY/mm/dd format
     * @param editionShortName an code system to limit the refset to
     * @return A list of concepts that were unable to have publication completed
     * @throws Exception the exception
     */
    public static List<String> completeAllRefsetPublications(final TerminologyService service, final String versionDate, final String editionShortName) throws Exception {

        List<String> refsetsNotUpdated = new ArrayList<>();
        String query = "workflowStatus: " + READY_FOR_PUBLICATION + " AND editionShortName: " + QueryParserBase.escape(editionShortName);

        final ResultList<Refset> results = service.find(query, null, Refset.class, null);

        // see if there is an "In Development" version as that should be the latest.
        for (final Refset refset : results.getItems()) {

            refsetsNotUpdated.addAll(completeRefsetPublication(service, refset, versionDate));
        }

        return refsetsNotUpdated;
    }

    /**
     * Complete the publication of a refset.
     *
     * @param service the Terminology Service
     * @param refset the refset
     * @param versionDate the publication date of the refset in YYYY/mm/dd format
     * @return A list of concepts that were unable to have publication completed
     * @throws Exception the exception
     */
    public static List<String> completeRefsetPublication(final TerminologyService service, final Refset refset, final String versionDate) throws Exception {

        List<String> refsetsNotUpdated = new ArrayList<>();

        try {

            if (!refset.getWorkflowStatus().equals(READY_FOR_PUBLICATION)) {

                throw new Exception("Refset is not in the proper status to have publication completed " + refset.getRefsetId());
            }

            if (!refset.isLocalSet()) {

                throw new Exception("Refset can not be published because it is a local set " + refset.getRefsetId());
            }

            refset.setVersionDate(RefsetService.getRefsetDateFromFormattedString(versionDate));
            refset.setWorkflowStatus(PUBLISHED);
            refset.setVersionStatus(PUBLISHED);
            refset.setLatestPublishedVersion(true);

            service.update(refset);
            service.add(AuditEntryHelper.completeRefsetPublicationEntry(refset));

            if (!refset.getWorkflowStatus().equals(PUBLISHED)) {

                throw new Exception("Refset was not able to have publication completed " + refset.getId());
            }

            Refset oldLatestVersionRefset = service.findSingle("refsetId:" + QueryParserBase.escape(refset.getRefsetId()) + " AND latestPublishedVersion: true", Refset.class, null);

            if (oldLatestVersionRefset != null) {

                oldLatestVersionRefset.setLatestPublishedVersion(false);
                oldLatestVersionRefset.setHasVersionInDevelopment(false);
                service.update(oldLatestVersionRefset);
                logger.info("Refset " + oldLatestVersionRefset.getId() + " version marked as not latest.");
            }

        } catch (Exception e) {

            logger.error("Completing Refset Publication failed: " + e.getMessage());
            logger.debug("", e);
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
    public static List<String> setBatchWorkflowStatusByAction(final TerminologyService service, final User user, final String refsetIds, final String action, final String notes) throws Exception {

        List<String> refsetsNotUpdated = new ArrayList<>();

        final ResultList<Refset> results = service.find("refsetId:(" + refsetIds.replace(",", " OR ") + ") AND versionStatus: (" + Refset.IN_DEVELOPMENT + ")", new PfsParameter(), Refset.class, null);

        for (final Refset refset : results.getItems()) {

            try {

                final String currentStatus = refset.getWorkflowStatus();

                setWorkflowStatusByAction(service, user, action, refset, notes);

                if (currentStatus.equals(refset.getWorkflowStatus())) {

                    refsetsNotUpdated.add(refset.getRefsetId());
                } else {

                    RefsetService.clearAllRefsetCaches(refset.getEditionBranch());
                }

            } catch (Exception e) {

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
    public static Refset setWorkflowStatus(final TerminologyService service, final User user, final String action, final Refset refset, final String notes, final String nextStatus,
        final String assignedUser) throws Exception {

        if (WorkflowService.getAllowedActions(user, refset).contains(action)) {

            final Refset updatedRefset = setRefsetWorkflowStatus(service, user, refset, nextStatus, assignedUser);
            addWorkflowHistory(service, user, action, refset, notes);
            return updatedRefset;
        } else {

            logger.error("Unsuccessful attempt to update workflow status for refset " + refset.getId() + " from status " + refset.getWorkflowStatus() + " with action " + action);
            return refset;
        }

    }

    /**
     * Set the workflow status for the refset based on the action the user took.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param action the action
     * @param refset the refset
     * @param notes the workflow status notes
     * @param action the action the user took
     * @return the updated refset
     * @throws Exception the exception
     */
    public static Refset setWorkflowStatusByAction(final TerminologyService service, final User user, final String action, final Refset refset, final String notes) throws Exception {

        if (action.equals(REQUEST_PUBLICATION) && refset.isLocalSet()) {

            final String message = "Refset can not be published because it is a local set.";
            logger.error(message);
            throw new Exception(message);
        }

        final String currentStatus = refset.getWorkflowStatus();
        boolean restoreHistory = false;
        List<String> roles = RefsetService.setRoles(user, refset.getProject(), new ArrayList<>());

        // get the next status based on the user, current status, and supplied action
        logger.debug("WORKFLOW_PERMUTATIONS: " + ModelUtility.toJson(WORKFLOW_PERMUTATIONS));

        String nextStatus = null;
        String assignedUser = null;

        // loop thru the roles to find a match for the action and current status. !! This only works if any multiple matches between role, current status, and action go to the
        // same next status !!
        for (final String role : roles) {

            if (WORKFLOW_PERMUTATIONS.containsKey(role) && WORKFLOW_PERMUTATIONS.get(role).containsKey(refset.getWorkflowStatus())) {

                final String possibleStatus = WORKFLOW_PERMUTATIONS.get(role).get(refset.getWorkflowStatus()).get(action);

                if (possibleStatus != null) {

                    nextStatus = possibleStatus;
                    break;
                }

            }

        }

        if (Arrays.asList(EDIT, UPGRADE, REVIEW).contains(action)) {

            assignedUser = user.getUserName();
        }

        logger.debug("currentStatus: " + currentStatus + " ; nextStatus: " + nextStatus);

        // if edits have just been completed then merge the edit branch into the refset branch and delete the edit branch
        if ((currentStatus.equals(IN_EDIT) && Arrays.asList(FINISH_EDIT, REQUEST_REVIEW, REQUEST_PUBLICATION).contains(action))
            || (currentStatus.equals(IN_UPGRADE) && Arrays.asList(FINISH_UPGRADE).contains(action))) {

            final boolean merged = mergeEditIntoRefsetBranch(refset.getEditionBranch(), refset.getRefsetId(), refset.getEditBranchId(), refset.getRefsetBranchId(), notes);

            if (merged) {

                refset.setEditBranchId(null);
                RefsetService.removeRefsetEditHistory(service, user, refset.getRefsetId());

            } else {

                final String message = "Unable to merge edit into refset branch for refset " + refset.getRefsetId() + " because the edit branch doesn't exist.";
                logger.error(message);
                throw new Exception(message);
            }

        }

        else if ((currentStatus.equals(IN_EDIT) && Arrays.asList(CANCEL_EDIT).contains(action)) || (currentStatus.equals(IN_UPGRADE) && Arrays.asList(CANCEL_UPGRADE).contains(action))) {

            RefsetMemberService.clearAllMemberCaches(getEditBranchPath(refset.getEditionBranch(), refset.getRefsetId(), refset.getEditBranchId(), refset.getRefsetBranchId()));
            refset.setEditBranchId(null);
            restoreHistory = true;
        }

        // else if this is the start of edits create the refset edit branch
        else if (action.equals(EDIT) || action.equals(UPGRADE)) {

            final String branchId = generateBranchId();
            refset.setEditBranchId(branchId);
            String projectBranchPath = getProjectBranchPath(refset.getEditionBranch());
            String refsetBranchPath = getRefsetBranchPath(refset.getEditionBranch(), refset.getRefsetId(), refset.getRefsetBranchId());

            mergeBranch(refset.getEditionBranch(), projectBranchPath, "Updating branch to latest changes", true);
            mergeBranch(projectBranchPath, refsetBranchPath, "Updating branch to latest changes", true);
            createEditBranch(service, user, refset.getEditionBranch(), refset, refset.getRefsetId(), branchId, refset.getRefsetBranchId());
        }

        if (currentStatus.equals(IN_UPGRADE)) {

            RefsetMemberService.removeUpgradeData(service, user, refset.getId());
        }

        setWorkflowStatus(service, user, action, refset, notes, nextStatus, assignedUser);

        if (restoreHistory) {

            RefsetService.replaceRefsetWithEditHistory(service, user, refset.getId());
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
    public static Refset setRefsetWorkflowStatus(final TerminologyService service, final User user, final Refset refset, final String status, final String assignedUser) throws Exception {

        final long start = System.currentTimeMillis();

        refset.setWorkflowStatus(status);
        refset.setAssignedUser(assignedUser);

        // Published is the final status so set the version information
        if (status.equals(PUBLISHED)) {

            // get the latest edition version branch
            final List<String> branchVersions = RefsetService.getBranchVersions(refset.getEditionBranch());

            if (branchVersions.size() < 1) {

                final String message = "Could not retrieve branch versions for branch " + refset.getEditionBranch();
                logger.error(message);
                throw new Exception(message);
            }

            final String newVersion = branchVersions.get(0);

            refset.setVersionDate(RefsetService.getRefsetDateFromFormattedString(newVersion));
            refset.setVersionStatus(Refset.PUBLISHED);
        }

        // Update an object
        service.update(refset);
        logger.info("Refset workflow status set to " + status + " for refset " + refset.getId() + ". Time: " + (System.currentTimeMillis() - start));

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
    public static void addWorkflowHistory(final TerminologyService service, final User user, final String action, final Refset refset, final String notes) throws Exception {

        final long start = System.currentTimeMillis();

        final WorkflowHistory workflow = new WorkflowHistory(user.getUserName(), refset.getWorkflowStatus(), action, notes, refset);

        // Add an object
        service.add(workflow);
        service.add(AuditEntryHelper.addWorkflowHistoryEntry(workflow, refset, workflow.getWorkflowStatus()));
        final String newWorkflowId = workflow.getId();

        if (newWorkflowId == null) {

            throw new Exception("Unable to create a new workflow history entry.");
        }

        logger.info("New workflow history entry with status " + refset.getWorkflowStatus() + " added for refset " + refset.getId() + ". Time: " + (System.currentTimeMillis() - start));
    }

    /**
     * Get the current workflow for a refset.
     *
     * @param service the Terminology Service
     * @param refset the refset
     * @return the current workflow object
     * @throws Exception the exception
     */
    public static WorkflowHistory getCurrentWorkflow(final TerminologyService service, final Refset refset) throws Exception {

        final PfsParameter pfs = new PfsParameter();
        pfs.setSort("modified");
        pfs.setAscending(false);
        pfs.setLimit(1);

        ResultList<WorkflowHistory> results = service.find("refsetId:" + QueryParserBase.escape(refset.getId()) + "", pfs, WorkflowHistory.class, null);

        if (results.getItems().size() == 0) {

            throw new Exception("Unable to retrieve worflow for refset " + refset.getId());
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
    public static ResultList<WorkflowHistory> getWorkflowHistory(final TerminologyService service, final Refset refset, final SearchParameters searchParameters) throws Exception {

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

            query = " AND " + IndexUtility.addWildcardsToQuery(searchParameters.getQuery(), WorkflowHistory.class);
        }

        ResultList<WorkflowHistory> results = service.find("refsetId:" + QueryParserBase.escape(refset.getId()) + query, pfs, WorkflowHistory.class, null);

        //logger.debug("getWorkflowHistory results: " + ModelUtility.toJson(results));

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

        WorkflowHistory workflow = getCurrentWorkflow(service, refset);

        workflow.setNotes(notes);

        // Update an object
        service.update(workflow);
        service.add(AuditEntryHelper.updateWorkflowNoteEntry(workflow, refset));
        logger.info("Note for workflow history entry with status " + refset.getWorkflowStatus() + " updated for refset " + refset.getId());
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
        if (!Arrays.asList(IN_EDIT, IN_REVIEW).contains(refset.getWorkflowStatus())) {

            return "";
        }

        WorkflowHistory workflow = getCurrentWorkflow(service, refset);
        logger.debug("getAssignedUserName: " + workflow.getUserName());
        return workflow.getUserName();
    }

    /**
     * Get the project branch name for an edition.
     *
     * @param editionBranchPath the branch path of the edition the project belongs to
     * @return the project branch name
     * @throws Exception the exception
     */
    public static String getProjectBranchName(final String editionBranchPath) throws Exception {

        final int initialsLocationIndex = editionBranchPath.lastIndexOf("-");
        String projectBranchName = PROJECT_BRANCH_NAME;
            
        if (initialsLocationIndex > 0) { 
            projectBranchName += editionBranchPath.substring(initialsLocationIndex);
        }
        
        return projectBranchName;
    }
    
    /**
     * Get the project branch path for an edition.
     *
     * @param editionBranchPath the branch path of the edition the project belongs to
     * @return the branch path of the project branch
     * @throws Exception the exception
     */
    public static String getProjectBranchPath(final String editionBranchPath) throws Exception {
        
        return editionBranchPath + "/" + getProjectBranchName(editionBranchPath);
    }

    /**
     * Create the project branch for an edition.
     *
     * @param editionBranchPath the branch path of the edition to create the new branch in
     * @return the branch path of the new project branch
     * @throws Exception the exception
     */
    public static String createProjectBranch(final String editionBranchPath) throws Exception {

        String projectBranchPath = getProjectBranchPath(editionBranchPath);

        if (doesBranchExist(projectBranchPath)) {

            mergeBranch(editionBranchPath, projectBranchPath, "Updating branch to latest changes", true);
            return projectBranchPath;

        } else {

            projectBranchPath = createBranch(editionBranchPath, getProjectBranchName(editionBranchPath));
            return projectBranchPath;
        }

    }

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

        } else {

            return false;
        }

    }

    /**
     * Get the refset branch path for a refset.
     *
     * @param editionBranchPath the branch path of the edition the refset belongs to
     * @param refsetId the refset ID
     * @param branchId the ID for the refset branch
     * @return the branch path of the refset branch
     * @throws Exception the exception
     */
    public static String getRefsetBranchPath(final String editionBranchPath, final String refsetId, final String branchId) throws Exception {

        return getProjectBranchPath(editionBranchPath) + "/" + REFSET_BRANCH_PREFIX + refsetId + "-" + branchId;
    }

    /**
     * Create the refset branch for an IN DEVELOPMENT version.
     *
     * @param editionBranchPath the branch path of the edition to create the new branch in
     * @param refsetId the refset ID
     * @param branchId the ID for the refset branch
     * @return the branch path of the new refset branch
     * @throws Exception the exception
     */
    public static String createRefsetBranch(final String editionBranchPath, final String refsetId, final String branchId) throws Exception {

        final String branchName = REFSET_BRANCH_PREFIX + refsetId + "-" + branchId;
        final String projectBranchPath = getProjectBranchPath(editionBranchPath);
        String refsetBranchPath = getRefsetBranchPath(editionBranchPath, refsetId, branchId);

        if (doesBranchExist(refsetBranchPath)) {

            mergeBranch(projectBranchPath, refsetBranchPath, "Updating branch to latest changes", true);
            return refsetBranchPath;

        } else {

            createProjectBranch(editionBranchPath);
            refsetBranchPath = createBranch(projectBranchPath, branchName);
            return refsetBranchPath;
        }

    }

    /**
     * Merge the refset branch into the project branch.
     *
     * @param editionBranchPath the branch path of the edition the refset belongs to
     * @param refsetId the refset ID
     * @param branchId the ID for the refset branch
     * @param comment the merge comment
     * @return were the branches merged
     * @throws Exception the exception
     */
    public static boolean mergeRefsetIntoProjectBranch(final String editionBranchPath, final String refsetId, final String branchId, final String comment) throws Exception {

        final String projectBranchPath = getProjectBranchPath(editionBranchPath);
        final String refsetBranchPath = getRefsetBranchPath(editionBranchPath, refsetId, branchId);

        if (doesBranchExist(refsetBranchPath)) {

            mergeBranch(refsetBranchPath, projectBranchPath, comment, false);
            return true;
        } else {

            return false;
        }

    }

    /**
     * Delete the refset branch for a refset.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param editionBranchPath the branch path of the edition to create the new branch in
     * @param refsetId the refset ID
     * @param branchId the ID for the refset branch
     * @return was the branch deleted
     * @throws Exception the exception
     */
    public static boolean deleteRefsetBranch(final TerminologyService service, final User user, final String editionBranchPath, final String refsetId, final String branchId) throws Exception {

        RefsetService.removeRefsetEditHistory(service, user, refsetId);
        
        final String branchPath = getRefsetBranchPath(editionBranchPath, refsetId, branchId);
        final List<String> childBranchPaths = getBranchChildren(branchPath);
        
        for (final String childBranchPath : childBranchPaths) {
            
            final boolean deleted = deleteBranch(childBranchPath);
            
            if (!deleted) {
                break;
            }
        }
        
        return deleteBranch(branchPath);
    }

    /**
     * Generate a ID for a branch based on a millisecond unix timestamp.
     *
     * @return the ID for the branch
     * @throws Exception the exception
     */
    public static String generateBranchId() throws Exception {

        long unixTime = Instant.now().toEpochMilli();
        return unixTime + "";
    }

    /**
     * Get the edit branch path for a refset.
     *
     * @param editionBranchPath the branch path of the edition the refset belongs to
     * @param refsetId the refset ID
     * @param editBranchId the ID for the edit branch
     * @param refsetBranchId the ID for the refset branch
     * @return the branch path of the edit branch
     * @throws Exception the exception
     */
    public static String getEditBranchPath(final String editionBranchPath, final String refsetId, final String editBranchId, final String refsetBranchId) throws Exception {

        return getRefsetBranchPath(editionBranchPath, refsetId, refsetBranchId) + "/" + EDIT_BRANCH_NAME + editBranchId;
    }

    /**
     * Create the edit branch for a refset.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param editionBranchPath the branch path of the edition to create the new branch in
     * @param refset the refset to modify
     * @param refsetId the refset ID
     * @param editBranchId the ID for the edit branch
     * @param refsetBranchId the ID for the refset branch
     * @return the branch path of the new edit branch
     * @throws Exception the exception
     */
    public static String createEditBranch(final TerminologyService service, final User user, final String editionBranchPath, final Refset refset, final String refsetId, final String editBranchId, final String refsetBranchId)
        throws Exception {

        final String refsetBranchPath = getRefsetBranchPath(editionBranchPath, refsetId, refsetBranchId);

        if (refset != null) {

            RefsetService.createRefsetEditHistory(service, user, refset);
        }

        final String editBranchPath = createBranch(refsetBranchPath, EDIT_BRANCH_NAME + editBranchId);
        return editBranchPath;
    }

    /**
     * Merge the edit branch into the refset branch.
     *
     * @param editionBranchPath the branch path of the edition the refset belongs to
     * @param refsetId the refset ID
     * @param editBranchId the ID for the edit branch
     * @param refsetBranchId the ID for the refset branch
     * @param comment the merge comment
     * @return were the branches merged
     * @throws Exception the exception
     */
    public static boolean mergeEditIntoRefsetBranch(final String editionBranchPath, final String refsetId, final String editBranchId, final String refsetBranchId, final String comment) throws Exception {

        final String refsetBranchPath = getRefsetBranchPath(editionBranchPath, refsetId, refsetBranchId);
        final String editBranchPath = getEditBranchPath(editionBranchPath, refsetId, editBranchId, refsetBranchId);

        if (doesBranchExist(refsetBranchPath) && doesBranchExist(editBranchPath)) {

            mergeBranch(editBranchPath, refsetBranchPath, comment, false);

            RefsetMemberService.copyAllMemberCachesToBranch(editBranchPath, refsetBranchPath, "true");
            RefsetMemberService.clearAllMemberCaches(editBranchPath);
            return true;
        } else {

            return false;
        }

    }

    /**
     * Delete the edit branch for a refset.
     *
     * @param service the Terminology Service
     * @param user the user
     * @param editionBranchPath the branch path of the edition to create the new branch in
     * @param refsetId the refset ID
     * @param editBranchId the ID for the edit branch
     * @param refsetBranchId the ID for the refset branch
     * @return was the branch deleted
     * @throws Exception the exception
     */
    public static boolean deleteEditBranch(final TerminologyService service, final User user, final String editionBranchPath, final String refsetId, final String editBranchId, final String refsetBranchId) throws Exception {

        RefsetService.removeRefsetEditHistory(service, user, refsetId);

        final String branchPath = getEditBranchPath(editionBranchPath, refsetId, editBranchId, refsetBranchId);
        return deleteBranch(branchPath);
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

        final long start = System.currentTimeMillis();
        String refsetBranchPath = null;
        final String url = SnowstormConnection.BASE_URL + "branches";
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode body = mapper.createObjectNode().put("name", branchName).put("parent", parentBranchPath);

        logger.debug("createBranch URL: " + url + " ; body: " + body.toString());

        try (final Response response = SnowstormConnection.postResponse(url, body.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                final String error = "Could not create branch " + parentBranchPath + "/" + branchName;
                logger.error(error);
                throw new Exception(error);
            }

            final String resultString = response.readEntity(String.class);

            final JsonNode root = mapper.readTree(resultString.toString());
            JsonNode rootNode = root;

            if (rootNode.has("path")) {

                refsetBranchPath = rootNode.get("path").asText();
            }

            logger.info("Created branch " + refsetBranchPath + ". Time: " + (System.currentTimeMillis() - start));
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
        final String url = SnowstormConnection.BASE_URL + "admin/" + branchPath + "/actions/hard-delete";

        logger.debug("deleteBranch URL: " + url);

        try (final Response response = SnowstormConnection.deleteResponse(url, null)) {

            // Only process payload if Rest call is successful
            if (response.getStatus() == Response.Status.OK.getStatusCode()) {

                logger.info("Deleted branch " + branchPath + ". Time: " + (System.currentTimeMillis() - start));
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

        final long start = System.currentTimeMillis();
        final String url = SnowstormConnection.BASE_URL + "branches/" + branchPath;

        logger.debug("doesBranchExist URL: " + url);

        try (final Response response = SnowstormConnection.getResponse(url)) {

            // If Rest call is successful then branch exists
            if (response.getStatus() == Response.Status.OK.getStatusCode()) {

                logger.debug("doesBranchExist: true. Time: " + (System.currentTimeMillis() - start));
                return true;
            } else {

                logger.debug("doesBranchExist: false. Time: " + (System.currentTimeMillis() - start));
                return false;
            }

        }

    }
    
    /**
     * get the branch paths for all children of a branch.
     *
     * @param branchPath the branch path to get children for
     * @return a list of the child branch paths
     * @throws Exception the exception
     */
    public static List<String> getBranchChildren(final String branchPath) throws Exception {
        
        final String url = SnowstormConnection.BASE_URL + "branches/" + branchPath + "children?immediateChildren=true&page=0&size=9000";
        List<String> childBranchPaths = new ArrayList<>();
        
        logger.debug("getBranchChildren URL: " + url);
        
        try (final Response response = SnowstormConnection.getResponse(url)) {
            
            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception("call to url '" + url + "' wasn't successful. " + response.toString());
            }

            final String resultString = response.readEntity(String.class);

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                throw new Exception(Integer.toString(response.getStatus()));
            }

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());
            final Iterator<JsonNode> iterator = root.iterator();

            if (iterator.hasNext()) {

                final JsonNode childNode = iterator.next();
                childBranchPaths.add(childNode.get("path").asText());
            }
        }
        
        return childBranchPaths;
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

        final long start = System.currentTimeMillis();
        final String mergeUrl = SnowstormConnection.BASE_URL + "merges";
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode body = mapper.createObjectNode().put("source", sourceBranchPath).put("target", targetBranchPath);

        if (comment != null) {

            body.put("commitComment", comment);
        }

        if (rebase) {
            
            String jobStatusUrl = null;
            boolean jobDone = false;
            final String reviewUrl = SnowstormConnection.BASE_URL + "merge-reviews";
            logger.debug("mergeBranch reviewUrl: " + reviewUrl + " ; body: " + body.toString());

            try (final Response response = SnowstormConnection.postResponse(reviewUrl, body.toString())) {

                // Only process payload if Rest call is successful
                if (response.getStatus() != Response.Status.OK.getStatusCode() && response.getStatus() != Response.Status.CREATED.getStatusCode()) {

                    final String error = "Could not review merge branch " + sourceBranchPath + " into branch " + targetBranchPath;
                    logger.error(error);
                    throw new Exception(error);
                }
                
                jobStatusUrl = response.getHeaderString("Location");
                final String[] location = jobStatusUrl.split("/");
                final String reviewId = location[location.length - 1];
                body.put("reviewId", reviewId);
            }
            
            logger.debug("mergeBranch review job status URL: " + jobStatusUrl);

            while (!jobDone) {

                try (final Response response = SnowstormConnection.getResponse(jobStatusUrl)) {
                    
                    final String error = "Could not review merge branch " + sourceBranchPath + " into branch " + targetBranchPath + ". ";

                    // Only process payload if Rest call is successful
                    if (response.getStatus() != Response.Status.OK.getStatusCode()) {

                        logger.error(error + response.toString());
                    }

                    final String resultString = response.readEntity(String.class);
                    final JsonNode root = mapper.readTree(resultString.toString());

                    // logger.debug("addRefsetMembers job status response: " + root);
                    final String status = root.get("status").asText();
                    logger.debug("merge review status: " + status);
                    
                    if (status.equalsIgnoreCase("PENDING")) {

                        logger.debug("merge review hasn't finished yet...");

                        try {

                            Thread.sleep(300);
                        } catch (InterruptedException ex) {

                            Thread.currentThread().interrupt();
                        }

                    } else if (status.equalsIgnoreCase("failed")) {

                        jobDone = true;
                        logger.error(error + root.get("message").asText());
                    } else {
                        jobDone = true;
                    }
                }
            }
        }
        
        logger.debug("mergeBranch URL: " + mergeUrl + " ; body: " + body.toString());
        
        try (final Response response = SnowstormConnection.postResponse(mergeUrl, body.toString())) {

            // Only process payload if Rest call is successful
            if (response.getStatus() != Response.Status.OK.getStatusCode() && response.getStatus() != Response.Status.CREATED.getStatusCode()) {

                logger.error("mergeBranch response status: " + response.getStatus());
                logger.error("mergeBranch response status reason: " + response.getStatusInfo().getReasonPhrase());
                final String error = "Could not merge branch " + sourceBranchPath + " into branch " + targetBranchPath;
                logger.error(error);
                throw new Exception(error);
            }

            final String jobStatusUrl = response.getHeaderString("Location");
            
            logger.debug("Merge status info at " + jobStatusUrl);

            try (final Response mergeInfoResponse = SnowstormConnection.getResponse(jobStatusUrl)) {

                final String resultString = mergeInfoResponse.readEntity(String.class);
                final JsonNode root = mapper.readTree(resultString.toString());
                final String status = root.get("status").asText();
                
                if (status.equals("FAILED")) {
                    
                    final String message = root.get("message").asText();
                    
                    if (!message.contains("is not meaningful")) {
                        
                        final String error = "Could not merge branch " + sourceBranchPath + " into branch " + targetBranchPath + ". Error: " + message;
                        logger.error(error);
                        throw new Exception(error);
                        
                    } else {
                        logger.debug("Merge did not occurr. " + message);
                    }
                    
                } else {
                    logger.info("Merged branch " + sourceBranchPath + " into branch " + targetBranchPath + ". Time: " + (System.currentTimeMillis() - start));
                }
            }
        }
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
        final ObjectMapper mapper = new ObjectMapper();
        final ObjectNode body = mapper.createObjectNode();
        final String projectBranchPath = getProjectBranchPath(editionBranchPath);
        String tempBranchPath = null;
        
        if (!doesBranchExist(projectBranchPath)) {
            createBranch(editionBranchPath, getProjectBranchName(editionBranchPath));
        }

        if (doesBranchExist(projectBranchPath + "/" + TEMP_BRANCH_NAME)) {
            tempBranchPath = projectBranchPath + "/" + TEMP_BRANCH_NAME;
        } else {
            tempBranchPath = createBranch(projectBranchPath, TEMP_BRANCH_NAME);
        }

        final long start = System.currentTimeMillis();
        final String url = SnowstormConnection.BASE_URL + "browser/" + tempBranchPath + "/" + "concepts/";

        logger.debug("getNewRefsetId URL: " + url);
        logger.debug("getNewRefsetId URL Body: " + body.toString());

        try (final Response response = SnowstormConnection.postResponse(url, body.toString())) {

            if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {

                throw new Exception("call to url '" + url + "' wasn't successful. " + response.readEntity(String.class));
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

        logger.debug("New Refset ID " + refsetConceptId + ". Time: " + (System.currentTimeMillis() - start));
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
     * Get a list of workflow statuses that are allowed for the current user and state of the refset.
     *
     * @param user the user
     * @param refset the refset
     * @return the list of allowed statuses
     * @throws Exception the exception
     */
    public static List<String> getAllowedStatuses(final User user, final Refset refset) throws Exception {

        final List<String> allowedStatuses = new ArrayList<>();
        final String currentStatus = refset.getWorkflowStatus();
        final Project project = refset.getProject();

        // Authors can start an edit cycle on Published refsets
        if (refset.getVersionStatus().equals(PUBLISHED)) {

            if (user.doesUserHavePermission(User.ROLE_AUTHOR, project)) {

                allowedStatuses.add(READY_FOR_EDIT);
                allowedStatuses.add(IN_EDIT);
                allowedStatuses.add(UPGRADE);
            }

            return allowedStatuses;
        }

        // only the assigned user can edit or review
        if (!user.getUserName().equals(refset.getAssignedUser()) && Arrays.asList(IN_EDIT, IN_UPGRADE, IN_REVIEW).contains(currentStatus)) {

            return allowedStatuses;
        }

        // set status permissions for AUTHORS
        if (user.doesUserHavePermission(User.ROLE_AUTHOR, project)) {

            if (Arrays.asList(IN_EDIT, IN_UPGRADE, REVIEW_COMPLETED, READY_FOR_PUBLICATION).contains(currentStatus)) {

                allowedStatuses.add(READY_FOR_EDIT);
            }

            if (Arrays.asList(READY_FOR_EDIT, READY_FOR_REVIEW, REVIEW_COMPLETED).contains(currentStatus)) {

                allowedStatuses.add(IN_EDIT);
            }

            if (Arrays.asList(READY_FOR_EDIT).contains(currentStatus)) {

                allowedStatuses.add(IN_UPGRADE);
            }

            if (Arrays.asList(READY_FOR_EDIT, IN_EDIT, REVIEW_COMPLETED).contains(currentStatus)) {

                allowedStatuses.add(READY_FOR_REVIEW);
            }

            if (Arrays.asList(READY_FOR_EDIT, IN_EDIT, READY_FOR_REVIEW).contains(currentStatus)) {

                allowedStatuses.add(READY_FOR_PUBLICATION);
            }

        }

        // set status permissions for REVIEWERS
        if (user.doesUserHavePermission(User.ROLE_REVIEWER, project)) {

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
        if (user.doesUserHavePermission(User.ROLE_ADMIN, project)) {

            if (Arrays.asList(READY_FOR_PUBLICATION).contains(currentStatus)) {

                allowedStatuses.add(READY_FOR_EDIT);
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
    public static List<String> getAllowedActions(final User user, final Refset refset) throws Exception {

        final List<String> allowedActions = new ArrayList<>();
        final String currentStatus = refset.getWorkflowStatus();
        final Project project = refset.getProject();

        // Authors can start an edit cycle on Published refsets
        if (refset.getVersionStatus().equals(PUBLISHED) && user.doesUserHavePermission(User.ROLE_AUTHOR, project)) {

            allowedActions.add(EDIT);
            allowedActions.add(UPGRADE);

        } else if (currentStatus == null) {

            return allowedActions;

        } else if (refset.getVersionStatus().equals(Refset.IN_DEVELOPMENT)) {

            if (currentStatus.equals(READY_FOR_EDIT)) {

                if (user.doesUserHavePermission(User.ROLE_AUTHOR, project)) {

                    allowedActions.add(EDIT);
                    allowedActions.add(UPGRADE);
                    allowedActions.add(REQUEST_REVIEW);
                    allowedActions.add(REQUEST_PUBLICATION);
                }

            }

            else if (currentStatus.equals(IN_EDIT)) {

                // only the assigned user can edit
                if (user.doesUserHavePermission(User.ROLE_AUTHOR, project) && user.getUserName().equals(refset.getAssignedUser())) {

                    allowedActions.add(CANCEL_EDIT);
                    allowedActions.add(FINISH_EDIT);
                    allowedActions.add(REQUEST_REVIEW);
                    allowedActions.add(REQUEST_PUBLICATION);
                }

            }

            else if (currentStatus.equals(IN_UPGRADE)) {

                // only the assigned user can upgrade
                if (user.doesUserHavePermission(User.ROLE_AUTHOR, project) && user.getUserName().equals(refset.getAssignedUser())) {

                    allowedActions.add(CANCEL_UPGRADE);
                    allowedActions.add(FINISH_UPGRADE);
                }

            }

            else if (currentStatus.equals(READY_FOR_REVIEW)) {

                if (user.doesUserHavePermission(User.ROLE_AUTHOR, project)) {

                    allowedActions.add(WITHDRAW);
                }

                if (user.doesUserHavePermission(User.ROLE_REVIEWER, project)) {

                    allowedActions.add(REVIEW);
                }

            }

            else if (currentStatus.equals(IN_REVIEW)) {

                // only the assigned user can review
                if (user.doesUserHavePermission(User.ROLE_REVIEWER, project) && user.getUserName().equals(refset.getAssignedUser())) {

                    allowedActions.add(REJECT_REVIEW);
                    allowedActions.add(ACCEPT_REVIEW);
                    allowedActions.add(UNASSIGN);
                }

            }

            else if (currentStatus.equals(REVIEW_COMPLETED)) {

                if (user.doesUserHavePermission(User.ROLE_AUTHOR, project)) {

                    allowedActions.add(EDIT);
                    allowedActions.add(REQUEST_REVIEW);
                    allowedActions.add(REQUEST_PUBLICATION);
                }

            }

            else if (currentStatus.equals(READY_FOR_PUBLICATION)) {

                if (user.doesUserHavePermission(User.ROLE_AUTHOR, project) || user.doesUserHavePermission(User.ROLE_ADMIN, project)) {

                    allowedActions.add(FAILS_RVF);
                }

            }

        }

        return allowedActions;
    }

    /**
     * Test if a user can edit a refset.
     *
     * @param user the user
     * @param refset the refset
     * @throws Exception the exception
     */
    public static void canUserEditRefset(final User user, final Refset refset) throws Exception {

        if (!Arrays.asList(WorkflowService.IN_EDIT, WorkflowService.IN_UPGRADE).contains(refset.getWorkflowStatus()) || !user.getUserName().equals(refset.getAssignedUser())) {

            throw new Exception("Refset is not in the proper state or user does not have permission to edit.");
        }

    }
}
