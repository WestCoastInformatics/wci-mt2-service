
package org.ihtsdo.refsetservice.terminologyservice;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.WorkflowHistory;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for workflow processes.
 */
public final class WorkflowService {

    /** The logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(WorkflowService.class);
    
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
    
    /** The path to workflow files. */
    private static final String FILE_PATH = "src/main/resources/refsetService/";
    
    /** The order of workflow steps . */
    public static final List<String> WORKFLOW_ORDER = new ArrayList<>(Arrays.asList(READY_FOR_EDIT, IN_EDIT, READY_FOR_REVIEW, IN_REVIEW, REVIEW_COMPLETED, READY_FOR_PUBLICATION, PUBLISHED));
    
    /** The file that contains workflow actions by user and step. */
    private static final String WORKFLOW_PERMUTATIONS_FILE_NAME =
            "workflowPermutationsToFinalAction.txt";
    
    /** The file that contains workflow actions. */
    private static final String WORKFLOW_ACTIONS_FILE_NAME =
            "workflowActions.txt";
    
    /** The file that contains workflow statuses. */
    private static final String WORKFLOW_STATUSES_FILE_NAME =
            "workflowStatuses.txt";
    
    /** The workflow actions. */
    private static enum WorkflowAction {
        REQUEST_REVIEW, REQUEST_PUBLICATION, EDIT, FINISH_EDIT, FAILS_RVF, REFSET_PUBLISHED, REVIEW, REJECT_REVIEW, PASS_REVIEW, UNASSIGN, WITHDRAW
    };
    
    /** The workflow actions by user and step. */
    private static Map<String, Map<String, Map<String, String>>> WORKFLOW_PERMUTATIONS;
    
    /** The workflow actions. */
    public static List<String> WORKFLOW_ACTIONS;
    
    /** The workflow statuses. */
    public static List<String> WORKFLOW_STATUSES;

    static {
        
        try {
            
//            WORKFLOW_ACTIONS = FileUtility.readFileToArray(WORKFLOW_ACTIONS_FILE_NAME);
//            WORKFLOW_STATUSES = FileUtility.readFileToArray(WORKFLOW_STATUSES_FILE_NAME);
//            
//            // read in the actions by user and step
//            try (BufferedReader bufferedReader = new BufferedReader(new FileReader(WORKFLOW_PERMUTATIONS_FILE_NAME))) {
//                String line;
//
//                while ((line = bufferedReader.readLine()) != null) {
//                    
//                    String[] tokens = FieldedStringTokenizer.split(line, ",");
//                    
//                    if (tokens.length != 3) {
//                        throw new Exception(WORKFLOW_PERMUTATIONS_FILE_NAME + " has more than 3 items per line");
//                    }
//
//                    final String user = tokens[0];
//                    final String currentState = tokens[1];
//                    final String action = tokens[2];
//                    final String resultingState = tokens[3];
//
//                    if (!WORKFLOW_PERMUTATIONS.containsKey(user)) {
//                        WORKFLOW_PERMUTATIONS.put(user, new HashMap<String, Map<String, String>>());
//                    }
//
//                    if (!WORKFLOW_PERMUTATIONS.get(user).containsKey(currentState)) {
//                        WORKFLOW_PERMUTATIONS.get(user).put(currentState, new HashMap<>());
//                    }
//
//                    WORKFLOW_PERMUTATIONS.get(user).get(currentState).put(action, resultingState);
//                }
//            }
            
        } catch(Exception e){
            throw new RuntimeException(e.getMessage());
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
     * @param refset the refset
     * @param notes the workflow status notes
     * @param status the new workflow status
     * @return the updated refset
     * @throws Exception the exception
     */
    public static Refset setWorkflowStatus(final User user,
            final Refset refset, final String notes, final String status) throws Exception {
        
        if (WorkflowService.getAllowedStatuses(user, refset).contains(status)) {
            
            final Refset updatedRefset = setRefsetWorkflowStatus(user, refset, status);
            addWorkflowHistory(user, refset, notes);
            return updatedRefset;
        } else {
            
            logger.error("Unsuccessful attempt to update workflow status for refset " + refset.getId() + " from status " + refset.getWorkflowStatus() + " to status " + status);
            return refset;
        }
        
    }
    
    /**
     * Set the workflow status for the refset based on the action the user took.
     *
     * @param user the user
     * @param refset the refset
     * @param notes the workflow status notes
     * @param action the action the user took
     * @return the updated refset
     * @throws Exception the exception
     */
    public static Refset setWorkflowStatusByAction(final User user,
            final Refset refset, final String notes, final String action) throws Exception {
        
        // get the next status based on the user, current status, and supplied action
        final String nextStatus = WORKFLOW_PERMUTATIONS.get(user).get(refset.getWorkflowStatus()).get(action);
        return setWorkflowStatus(user, refset, notes, nextStatus);
    }
    
    /**
     * Advance the workflow state for the refset.
     *
     * @param user the user
     * @param refset the refset
     * @param notes the workflow status notes
     * @return the updated refset
     * @throws Exception the exception
     */
    public static Refset advanceWorkflow(final User user,
            final Refset refset, final String notes) throws Exception {
        
        final String nextStatus = getNextWorkflowStatus(refset.getWorkflowStatus());
        return setWorkflowStatus(user, refset, notes, nextStatus);
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
    public static Refset setRefsetWorkflowStatus(final User user,
            final Refset refset, final String status) throws Exception {
        
        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            
            refset.setWorkflowStatus(status);
            
            // Published is the final status
            if (status.equals(PUBLISHED)) {
                
                refset.setVersionStatus(Refset.PUBLISHED);
            }
            
            // Update an object
            service.update(refset);
            
            // update the refset permissions
            return RefsetService.setRefsetPermissions(user, refset);
        }
    }
    
    /**
     * Add an entry in the workflow history table.
     *
     * @param user the user
     * @param refset the refset
     * @param notes the workflow status notes
     * @throws Exception the exception
     */
    public static void addWorkflowHistory(final User user,
            final Refset refset, final String notes) throws Exception {
        
        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            
            final WorkflowHistory workflow = new WorkflowHistory(user.getUserName(), refset.getWorkflowStatus(), notes, refset);
            
            // Add an object
            service.add(workflow);
            final String newWorkflowId = workflow.getId();
            
            if (newWorkflowId == null) {
                throw new Exception("Unable to create a new workflow history entry.");
            } 
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
            pfs.setSort("created");
            pfs.setAscending(false);
            pfs.setLimit(1);
            
            ResultList<WorkflowHistory> results = service.find(
                    "refset_id:" + QueryParserBase.escape(refset.getId()) + "", pfs, WorkflowHistory.class, null);
            
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
    public static ResultList<WorkflowHistory> getWorkflowHistory(final Refset refset, final SearchParameters searchParameters) throws Exception {
        
        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();
            String query = searchParameters.getQuery();

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
            
            if (query != null) {
                query = " AND " + IndexUtility.addWildcardsToQuery(query, WorkflowHistory.class);
            }
            
            ResultList<WorkflowHistory> results = service.find(
                    "refset_id:" + QueryParserBase.escape(refset.getId()) + query, pfs, WorkflowHistory.class, null);
            
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
    public static void updateWorkflowNote(final User user, final Refset refset, final String notes) throws Exception {
        
        WorkflowHistory workflow = getCurrentWorkflow(refset);
        
        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            
            workflow.setNotes(notes);
            
            // Update an object
            service.update(workflow);
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
        
        final int currentStep = WORKFLOW_ORDER.indexOf(currentStatus);
        return WORKFLOW_ORDER.get(currentStep + 1);
    }
    
    /**
     * Get a list of workflow statuses that are allowed for the current user and state of the refset.
     *
     * @param user the user
     * @param refset the refset
     * @return the list of allowed statuses
     * @throws Exception the exception
     */
    public static List<String> getAllowedStatuses(final User user,
            final Refset refset) throws Exception {
        
        final List<String> allowedStatuses = new ArrayList<>();
        final String currentStatus = refset.getWorkflowStatus();
        final String nextStatus = getNextWorkflowStatus(currentStatus);
        
        // Published is the final status so no edits are allowed anymore
        if (currentStatus.equals(PUBLISHED)) {
            return allowedStatuses;
        }
        
        // only the assigned user can edit or review
        if (!user.getUserName().equals(refset.getAssignedUser()) && Arrays.asList(IN_EDIT, IN_REVIEW).contains(currentStatus)) {
            return allowedStatuses;
        }
        
        // set permissions that aren't the next step for AUTHORS
        if (user.doesUserHavePermission(User.ROLE_AUTHOR, refset)) {
            
            if (Arrays.asList(REVIEW_COMPLETED).contains(currentStatus)) {
                allowedStatuses.add(READY_FOR_EDIT); 
            }
            
            if (Arrays.asList(READY_FOR_REVIEW, REVIEW_COMPLETED).contains(currentStatus)) {
                allowedStatuses.add(IN_EDIT); 
            }
            
            if (Arrays.asList(READY_FOR_EDIT, REVIEW_COMPLETED).contains(currentStatus)) {
                allowedStatuses.add(READY_FOR_REVIEW); 
            }
            
            if (Arrays.asList(READY_FOR_EDIT, IN_EDIT).contains(currentStatus)) {
                allowedStatuses.add(READY_FOR_PUBLICATION); 
            }
        }
        
        // set permissions that aren't the next step for REVIEWERS
        if (user.doesUserHavePermission(User.ROLE_REVIEWER, refset)) {
            
            if (Arrays.asList(IN_REVIEW).contains(currentStatus)) {
                allowedStatuses.add(READY_FOR_EDIT); 
            }
        }
        
        // set the next step
        if (user.doesUserHavePermission(nextStatus, refset)) {
            allowedStatuses.add(nextStatus);
        }
        
       Arrays.asList(READY_FOR_EDIT, IN_EDIT, READY_FOR_REVIEW, IN_REVIEW, REVIEW_COMPLETED, READY_FOR_PUBLICATION, PUBLISHED);
        
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
    public static List<String> getAllowedActions(final User user,
            final Refset refset) throws Exception {
        
        final List<String> allowedActions = new ArrayList<>();
        final String currentStatus = refset.getWorkflowStatus();
        
        // Published is the final status so no edits are allowed anymore
        if (currentStatus.equals(PUBLISHED)) {
            return allowedActions;
        }
        
        if (currentStatus.equals(READY_FOR_EDIT) && user.doesUserHavePermission(User.ROLE_AUTHOR, refset)) {
            
            allowedActions.add("EDIT");
            allowedActions.add("REQUEST_REVIEW");
            allowedActions.add("REQUEST_PUBLICATION");
            
        } else if (currentStatus.equals(IN_EDIT) && user.doesUserHavePermission(User.ROLE_AUTHOR, refset)) {
            
            // only the assigned user can edit
            if (user.getUserName().equals(refset.getAssignedUser())) {
                
                allowedActions.add("FINISH_EDIT");
                allowedActions.add("REQUEST_REVIEW");
                allowedActions.add("REQUEST_PUBLICATION");
            }
            
        } else if (currentStatus.equals(READY_FOR_REVIEW) && user.doesUserHavePermission(User.ROLE_REVIEWER, refset)) {
            
            allowedActions.add("REVIEW");
            
        } else if (currentStatus.equals(IN_REVIEW) && user.doesUserHavePermission(User.ROLE_REVIEWER, refset)) {
            
            // only the assigned user can review
            if (user.getUserName().equals(refset.getAssignedUser())) {

                allowedActions.add("REJECT_REVIEW");
                allowedActions.add("PASS_REVIEW");
                allowedActions.add("UNASSIGN");
            }
            
        } else if (currentStatus.equals(REVIEW_COMPLETED) && user.doesUserHavePermission(User.ROLE_AUTHOR, refset)) {
            
            allowedActions.add("EDIT");
            allowedActions.add("REQUEST_REVIEW");
            allowedActions.add("REQUEST_PUBLICATION");
            
        }
       
        return allowedActions;
    }
}
