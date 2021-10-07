
package org.ihtsdo.refsetservice.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.WorkflowHistory;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for workflow processes.
 */
public final class WorkflowUtility {

    /** The logger. */
    @SuppressWarnings("unused")
    private static Logger logger = LoggerFactory.getLogger(WorkflowUtility.class);
    
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
    
    /** The oder of workflow steps . */
    public static final List<String> WORKFLOW_ORDER = new ArrayList<>(Arrays.asList(READY_FOR_EDIT, IN_EDIT, READY_FOR_REVIEW, IN_REVIEW, REVIEW_COMPLETED, READY_FOR_PUBLICATION, PUBLISHED));

    /**
     * Instantiates an empty {@link WorkflowUtility}.
     */
    private WorkflowUtility() {
        // n/a
    }

    /**
     * Advance the workflow state for the refset.
     *
     * @param user the user
     * @param refset the refset
     * @param notes the workflow status notes
     * @throws Exception the exception
     */
    public static void advanceWorkflow(final User user,
            final Refset refset, final String notes) throws Exception {
        
        addWorkflowHistory(user, refset, notes);
        setRefsetWorkflowStatus(user, refset, getNextWorkflowStatus(refset.getWorkflowStatus()));
    }
    
    /**
     * Set the workflow status for the refset.
     *
     * @param user the user
     * @param refset the refset
     * @param notes the workflow status notes
     * @param status the new workflow status
     * @throws Exception the exception
     */
    public static void setWorkflowStatus(final User user,
            final Refset refset, final String notes, final String status) throws Exception {
        
        addWorkflowHistory(user, refset, notes);
        setRefsetWorkflowStatus(user, refset, status);
    }
    
    /**
     * Update the refset to a new workflow status.
     *
     * @param user the user
     * @param refset the refset
     * @param status the new workflow status
     * @throws Exception the exception
     */
    public static void setRefsetWorkflowStatus(final User user,
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
        
        if (Arrays.asList(READY_FOR_REVIEW, READY_FOR_PUBLICATION).contains(currentStatus) && user.doesUserHavePermission(IN_EDIT, refset)) {
            allowedStatuses.add(IN_EDIT);
        }
        
        if (Arrays.asList(READY_FOR_PUBLICATION).contains(currentStatus) && user.doesUserHavePermission(IN_REVIEW, refset)) {
            allowedStatuses.add(IN_REVIEW);
        }
        
        // set the next step
        if (user.doesUserHavePermission(nextStatus, refset)) {
            allowedStatuses.add(nextStatus);
        }
        
        
        return allowedStatuses;
    }
    
}
