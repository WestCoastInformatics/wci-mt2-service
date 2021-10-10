
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
import org.ihtsdo.refsetservice.util.FieldedStringTokenizer;
import org.ihtsdo.refsetservice.util.IndexUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
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
    
    /** The EDIT workflow action . */
    public static final String CREATE = "CREATE";
    
    /** The EDIT workflow action . */
    public static final String EDIT = "EDIT";
    
    /** The FINISH_EDIT workflow action . */
    public static final String FINISH_EDIT = "FINISH_EDIT";
    
    /** The REQUEST_REVIEW workflow action . */
    public static final String REQUEST_REVIEW = "REQUEST_REVIEW";
    
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
    public static final List<String> WORKFLOW_STATUSES = new ArrayList<>(Arrays.asList(READY_FOR_EDIT, IN_EDIT, READY_FOR_REVIEW, IN_REVIEW, REVIEW_COMPLETED, READY_FOR_PUBLICATION, PUBLISHED));
    
    /** The order of workflow actions . */
    public static final List<String> WORKFLOW_ACTIONS = new ArrayList<>(Arrays.asList(EDIT, FINISH_EDIT, REQUEST_REVIEW, REVIEW, REJECT_REVIEW, 
            ACCEPT_REVIEW, UNASSIGN, REQUEST_PUBLICATION, FAILS_RVF, REFSET_PUBLISHED));
    
    /** The path to workflow files. */
    private static final String FILE_PATH = "src/main/resources/workflow/";
    
    /** The file that contains workflow actions by user and step. */
    private static final String WORKFLOW_PERMUTATIONS_FILE_NAME =
            "workflowPermutationsToFinalAction.txt";
    
    /** The file that contains workflow actions. */
    private static final String WORKFLOW_ACTIONS_FILE_NAME =
            "workflowActions.txt";
    
    /** The file that contains workflow statuses. */
    private static final String WORKFLOW_STATUSES_FILE_NAME =
            "workflowStatuses.txt";
    
    /** The workflow actions by user and step. */
    private static Map<String, Map<String, Map<String, String>>> WORKFLOW_PERMUTATIONS = new HashMap<>();
    

    static {
        
        try {
            
//            WORKFLOW_ACTIONS = FileUtility.readFileToArray(WORKFLOW_ACTIONS_FILE_NAME);
//            WORKFLOW_STATUSES = FileUtility.readFileToArray(WORKFLOW_STATUSES_FILE_NAME);
//            
            // read in the actions by user and step
            try (BufferedReader bufferedReader = new BufferedReader(new FileReader(FILE_PATH + WORKFLOW_PERMUTATIONS_FILE_NAME))) {
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
            
            logger.error("Unsuccessful attempt to update workflow status for refset " + refset.getId() + " from status " + refset.getWorkflowStatus() + " with action " + action);
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
        
        // get the next status based on the user, current status, and supplied action
        final String nextStatus = WORKFLOW_PERMUTATIONS.get(role).get(refset.getWorkflowStatus()).get(action);
        return setWorkflowStatus(user, action, refset, notes, nextStatus);
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
            logger.info("Refset workflow status set to " + status + " for refset " + refset.getId());
            
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
    public static void addWorkflowHistory(final User user, final String action,
            final Refset refset, final String notes) throws Exception {
        
        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            
            final WorkflowHistory workflow = new WorkflowHistory(user.getUserName(), refset.getWorkflowStatus(), action, notes, refset);
            
            // Add an object
            service.add(workflow);
            final String newWorkflowId = workflow.getId();
            
            if (newWorkflowId == null) {
                throw new Exception("Unable to create a new workflow history entry.");
            } 
            
            logger.info("New workflow history entry with status " + refset.getWorkflowStatus() + " added for refset " + refset.getId());
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
            
            ResultList<WorkflowHistory> results = service.find(
                    "refsetId:" + QueryParserBase.escape(refset.getId()) + "", pfs, WorkflowHistory.class, null);
            
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
            
            ResultList<WorkflowHistory> results = service.find(
                    "refsetId:" + QueryParserBase.escape(refset.getId()) + query, pfs, WorkflowHistory.class, null);
            
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
    public static void updateWorkflowNote(final User user, final Refset refset, final String notes) throws Exception {
        
        WorkflowHistory workflow = getCurrentWorkflow(refset);
        
        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy(user.getUserName());
            service.setModifiedFlag(true);
            
            workflow.setNotes(notes);
            
            // Update an object
            service.update(workflow);
            logger.info("Note for workflow history entry with status " + refset.getWorkflowStatus() + " updated for refset " + refset.getId());
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
    public static List<String> getAllowedStatuses(final User user,
            final Refset refset) throws Exception {
        
        final List<String> allowedStatuses = new ArrayList<>();
        final String currentStatus = refset.getWorkflowStatus();
        
        // Published is the final status so no edits are allowed anymore,
        if (currentStatus == null || currentStatus.equals(PUBLISHED)) {
            return allowedStatuses;
        }
        
        // only the assigned user can edit or review
        if (!user.getUserName().equals(refset.getAssignedUser()) && Arrays.asList(IN_EDIT, IN_REVIEW).contains(currentStatus)) {
            return allowedStatuses;
        }
        
        // set status permissions for AUTHORS
        if (user.doesUserHavePermission(User.ROLE_AUTHOR, refset)) {
            
            if (Arrays.asList(REVIEW_COMPLETED, READY_FOR_PUBLICATION).contains(currentStatus)) {
                allowedStatuses.add(READY_FOR_EDIT); 
            }
            
            if (Arrays.asList(READY_FOR_EDIT, READY_FOR_REVIEW, REVIEW_COMPLETED).contains(currentStatus)) {
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
            
            allowedActions.add(EDIT);
            allowedActions.add(REQUEST_REVIEW);
            allowedActions.add(REQUEST_PUBLICATION);
            
        } else if (currentStatus.equals(IN_EDIT) && user.doesUserHavePermission(User.ROLE_AUTHOR, refset)) {
            
            // only the assigned user can edit
            if (user.getUserName().equals(refset.getAssignedUser())) {
                
                allowedActions.add(FINISH_EDIT);
                allowedActions.add(REQUEST_REVIEW);
                allowedActions.add(REQUEST_PUBLICATION);
            }
            
        } else if (currentStatus.equals(READY_FOR_REVIEW) && user.doesUserHavePermission(User.ROLE_REVIEWER, refset)) {
            
            allowedActions.add(REVIEW);
            
        } else if (currentStatus.equals(IN_REVIEW) && user.doesUserHavePermission(User.ROLE_REVIEWER, refset)) {
            
            // only the assigned user can review
            if (user.getUserName().equals(refset.getAssignedUser())) {

                allowedActions.add(REJECT_REVIEW);
                allowedActions.add(ACCEPT_REVIEW);
                allowedActions.add(UNASSIGN);
            }
            
        } else if (currentStatus.equals(REVIEW_COMPLETED) && user.doesUserHavePermission(User.ROLE_AUTHOR, refset)) {
            
            allowedActions.add(EDIT);
            allowedActions.add(REQUEST_REVIEW);
            allowedActions.add(REQUEST_PUBLICATION);
            
        } else if (currentStatus.equals(REQUEST_PUBLICATION) && (user.doesUserHavePermission(User.ROLE_AUTHOR, refset) || user.doesUserHavePermission(User.ROLE_ADMIN, refset))) {
            
            allowedActions.add(FAILS_RVF);
            allowedActions.add(REFSET_PUBLISHED);
        }
       
        return allowedActions;
    }
}
