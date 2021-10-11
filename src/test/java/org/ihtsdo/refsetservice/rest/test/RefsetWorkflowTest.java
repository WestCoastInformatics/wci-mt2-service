
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.User;
import org.ihtsdo.refsetservice.model.WorkflowHistory;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.WorkflowService;
import org.ihtsdo.refsetservice.util.FieldedStringTokenizer;
import org.ihtsdo.refsetservice.util.ResultList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Integration tests for MetadataController.
 */

@AutoConfigureMockMvc
public class RefsetWorkflowTest extends AbstractRefsetTests {
    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(RefsetWorkflowTest.class);

    private static final String WORKFLOW_PERMUTATIONS_FILE_NAME =
            "workflowPermutationsToFinalAction.txt";

    private static final String WORKFLOW_PERMUTATIONS_FILE =
            REFSET_FILE_PATH + WORKFLOW_PERMUTATIONS_FILE_NAME;
    
    /** The AUTHOR_USER workflow user . */
    public static final String AUTHOR_USER = "AUTHOR_USER";
    
    /** The REVIEWER_USER workflow user . */
    public static final String REVIEWER_USER = "REVIEWER_USER";
    
    /** The ADMIN_USER workflow user . */
    public static final String ADMIN_USER = "ADMIN_USER";
    
    /** The VIEWER_USER workflow user . */
    public static final String VIEWER_USER = "VIEWER_USER";

    /** ThepermissiblePaths . */
    private Map<String, Map<String, Map<String, String>>> permissiblePaths;

    /** The actions . */
    private static List<String> actions = WorkflowService.WORKFLOW_ACTIONS;

    /** The statuses . */
    private static List<String> statuses = WorkflowService.WORKFLOW_STATUSES;
    
    /** The users . */
    private static List<String> users = new ArrayList<>(Arrays.asList(AUTHOR_USER, REVIEWER_USER, ADMIN_USER));

    

    /**
     * Sets the up.
     */
    @BeforeEach
    public void setUp(TestInfo info) throws Exception{

        if (testingEditionId != null && testingEditionId.isEmpty()) {

            objectMapper = new ObjectMapper();
            JacksonTester.initFields(this, objectMapper);
            baseUrl = "/refset";

            try {
                testingEditionId = getEditionInternalId(TESTING_EDITION_NAME);
                testingProjectId = getProjectInternalId(TESTING_PROJECT_NAME);

            } catch (Exception e) {
                e.printStackTrace();
            }

            // Define the Source-Of-Truth (all role/state permutations including
            // all possible outcomes
            permissiblePaths = defineAllPermissiblePermutations();
        }

    }

    /**
     * For each role/initial-state pair, ensure that each possible action
     * results in the expected state. If the action is available to that pair,
     * then verify the final-state is as expected. If the action is not
     * available to that pair, verify that the final-state is null
     *
     * @throws Exception the exception
     */
    @Test
    public void testAllRolesAndInitialStates() throws Exception {
        
        // Testing Ready_For_Edit state
        for (final String user : users) {
            
            final String userRole = getUserRole(user);
            
            for (final String initialStatus : statuses) {
                
                final Map<String, String> results =
                        getWorkflowActionPaths(user, initialStatus);

                for (final String action : results.keySet()) {
                    
                    if (permissiblePaths.containsKey(userRole) && permissiblePaths.get(userRole).containsKey(initialStatus) && permissiblePaths.get(userRole).get(initialStatus).containsKey(action)) {
                        
                        assertThat(results.get(action)).isEqualTo(
                                permissiblePaths.get(userRole).get(initialStatus).get(action));
                    } else {
                        assertThat(results.get(action)).isNull();
                    }
                }
            }
        }
    }

    /**
     * Mimic running workflow on a refset (ignoring actual add/remove of
     * members).
     * 
     * After each advancement, grab workflow history to ensure that the contents
     * are filled out as expected. Also perform one final one looking at all
     * history for the editing cycle to ensure no information loss as the
     * workflow goes through the full cycle *
     * @throws Exception the exception
     */
    @Test
    public void testWorkflowHistory() throws Exception {
        
        // Create a new Edit version of a published refset
        final String refsetInternalId = createNewRefsetVerion();
        int actionCount = 1;
        List<WorkflowHistory> lookedUpWorkflowHistory;
        String note;

        Refset refset = getRefset(refsetInternalId);
        Refset updatedRefset = null;

        // ALREADY IN EDIT WHEN CREATED
        // Finish First Edit (with Note)
        actionCount++;
        note = "This is the first edit of this workflow";
        updatedRefset = advanceWorkflow(refset, AUTHOR_USER, WorkflowService.REQUEST_REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowHistory(refsetInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowService.REQUEST_REVIEW, WorkflowService.READY_FOR_REVIEW, note);

        // In Review
        actionCount++;
        note = "";
        updatedRefset = advanceWorkflow(refset, REVIEWER_USER, WorkflowService.REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowHistory(refsetInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount), REVIEWER_USER, WorkflowService.REVIEW, WorkflowService.IN_REVIEW, note);

        // Reject Review (with Note)
        actionCount++;
        note = "I rejected this because it's not right";
        updatedRefset = advanceWorkflow(refset, REVIEWER_USER, WorkflowService.REJECT_REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowHistory(refsetInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount), REVIEWER_USER, WorkflowService.REJECT_REVIEW, WorkflowService.READY_FOR_EDIT, note);

        // Second Edit
        actionCount++;
        note = "";
        updatedRefset = advanceWorkflow(refset, AUTHOR_USER, WorkflowService.EDIT, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowHistory(refsetInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowService.EDIT, WorkflowService.IN_EDIT, note);

        // Update the note
        note = "Started my edits";
        updateWorkflowNote(refset, AUTHOR_USER, note);
        lookedUpWorkflowHistory = getWorkflowHistory(refsetInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowService.EDIT, WorkflowService.IN_EDIT, note);
        
        // Finish Second Edit (with Note)
        actionCount++;
        note = "Mistakes handled";
        updatedRefset = advanceWorkflow(refset, AUTHOR_USER, WorkflowService.REQUEST_REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowHistory(refsetInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowService.REQUEST_REVIEW, WorkflowService.READY_FOR_REVIEW, note);

        // Withdraw Review
        actionCount++;
        note = "Not Ready for review yet";
        updatedRefset = advanceWorkflow(refset, REVIEWER_USER, WorkflowService.WITHDRAW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowHistory(refsetInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount), REVIEWER_USER, WorkflowService.WITHDRAW, WorkflowService.READY_FOR_EDIT, note);
        
        // Resubmit for review
        actionCount++;
        note = "Refset is now ready.";
        updatedRefset = advanceWorkflow(refset, AUTHOR_USER, WorkflowService.REQUEST_REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowHistory(refsetInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowService.REQUEST_REVIEW, WorkflowService.READY_FOR_REVIEW, note);
        
        // In Review
        actionCount++;
        note = "";
        updatedRefset = advanceWorkflow(refset, REVIEWER_USER, WorkflowService.REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowHistory(refsetInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount), REVIEWER_USER, WorkflowService.REVIEW, WorkflowService.IN_REVIEW, note);

        // Pass Review (with Note)
        actionCount++;
        note = "This has been fixed as expected";
        updatedRefset = advanceWorkflow(refset, REVIEWER_USER, WorkflowService.ACCEPT_REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowHistory(refsetInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount), REVIEWER_USER, WorkflowService.ACCEPT_REVIEW, WorkflowService.REVIEW_COMPLETED, note);

        // Reject Review (with Note)
        actionCount++;
        note = "The refset should be published when the full extension is published";
        updatedRefset = advanceWorkflow(refset, AUTHOR_USER, WorkflowService.REQUEST_PUBLICATION, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowHistory(refsetInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowService.REQUEST_PUBLICATION, WorkflowService.READY_FOR_PUBLICATION, note);
        
        // remove the refset version
        deleteNewRefsetVerion(refsetInternalId);

    }

    /**
     * advance workflow.
     *
     * @throws Exception the exception
     */
    private Refset advanceWorkflow(final Refset refset, final String user, final String action, final String note) throws Exception{
        
        final String newStatus = getWorkflowStatusByAction(user, refset.getWorkflowStatus(), action);
        final String url = baseUrl + "/" + refset.getId() + "/workflowStatus?action=" + action + "&notes=" + note;
        
        final MvcResult result = mvc
                .perform(post(url)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        
        final String content = result.getResponse().getContentAsString();
        
        if (content == null || content.equals("")) {
            return null;
        }
        
        final Refset updatedRefset = new ObjectMapper().readValue(content, Refset.class);
        
        return updatedRefset;
    }
    
    /**
     * updateWorkflowNote.
     *
     * @throws Exception the exception
     */
    private void updateWorkflowNote(final Refset refset, final String user, final String note) throws Exception{
        
        final String url = baseUrl + "/" + refset.getId() + "/workflowNote?&notes=" + note;
        
        final MvcResult result = mvc
                .perform(put(url)
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        
        final String content = result.getResponse().getContentAsString();
        assertThat(content).isEqualTo("true");
    }
    
    /**
     * get workflow history.
     *
     * @throws Exception the exception
     */
    private List<WorkflowHistory> getWorkflowHistory(final String refsetInternalId) throws Exception{
        
        final String url = baseUrl + "/" + refsetInternalId + "/workflowHistory?limit=500&offset=0&sort=modified";

        final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        final String content = result.getResponse().getContentAsString();
        ResultList<WorkflowHistory> resultList = new ObjectMapper().readValue(content, (new TypeReference<ResultList<WorkflowHistory>>() {
                }));
        assertThat(resultList).isNotNull();

        return resultList.getItems();
    }

    /**
     * list all actions allowed.
     *
     * @throws Exception the exception
     */
    private List<String> listAvailableActions(final String user, final String initialStatus,
        final String action, final boolean assignedUser) {
        
        final List<String> allowedActions = new ArrayList<>();
        final String userRole = getUserRole(user);
        
        // Published is the final status so no edits are allowed anymore
        if (initialStatus.equals(WorkflowService.PUBLISHED)) {
            return allowedActions;
        }
        
        if (initialStatus.equals(WorkflowService.READY_FOR_EDIT) && userRole.equals(User.ROLE_AUTHOR)) {
            
            allowedActions.add(WorkflowService.EDIT);
            allowedActions.add(WorkflowService.REQUEST_REVIEW);
            allowedActions.add(WorkflowService.REQUEST_PUBLICATION);
            
        } else if (initialStatus.equals(WorkflowService.IN_EDIT) && userRole.equals(User.ROLE_AUTHOR)) {
            
            // only the assigned user can edit
            if (assignedUser) {
                
                allowedActions.add(WorkflowService.FINISH_EDIT);
                allowedActions.add(WorkflowService.REQUEST_REVIEW);
                allowedActions.add(WorkflowService.REQUEST_PUBLICATION);
            }
            
        } else if (initialStatus.equals(WorkflowService.READY_FOR_REVIEW) && userRole.equals(User.ROLE_REVIEWER)) {
            
            allowedActions.add(WorkflowService.REVIEW);
            
        } else if (initialStatus.equals(WorkflowService.IN_REVIEW) && userRole.equals(User.ROLE_REVIEWER)) {
            
            // only the assigned user can review
            if (assignedUser) {

                allowedActions.add(WorkflowService.REJECT_REVIEW);
                allowedActions.add(WorkflowService.ACCEPT_REVIEW);
                allowedActions.add(WorkflowService.UNASSIGN);
            }
            
        } else if (initialStatus.equals(WorkflowService.REVIEW_COMPLETED) && userRole.equals(User.ROLE_AUTHOR)) {
            
            allowedActions.add(WorkflowService.EDIT);
            allowedActions.add(WorkflowService.REQUEST_REVIEW);
            allowedActions.add(WorkflowService.REQUEST_PUBLICATION);
            
        }
       
        return allowedActions;
    }

    /**
     * Get status by action.
     *
     * @throws Exception the exception
     */
    private String getWorkflowStatusByAction(final String user, final String currentStatus, final String action) {
        
        final String userRole = getUserRole(user);
        final Map<String, Map<String, String>> rolePaths = permissiblePaths.get(userRole);
        
        if (rolePaths == null) {
            return null;
        }
        
        final Map<String, String> roleStatusActions = permissiblePaths.get(userRole).get(currentStatus);
        
        if (roleStatusActions == null) {
            return null;
        }
        
        return roleStatusActions.get(action);
    }
    
    /**
     * Get user role.
     *
     * @throws Exception the exception
     */
    private String getUserRole(final String user) {
        return user.replace("_USER", "");
    }
    
    /**
     * define all action paths.
     *
     * @throws Exception the exception
     */
    private Map<String, Map<String, Map<String, String>>> defineAllPermissiblePermutations() throws Exception{
        
        Map<String, Map<String, Map<String, String>>> returnMap = new HashMap<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(WORKFLOW_PERMUTATIONS_FILE))) {
            
            String line;

            while ((line = br.readLine()) != null) {
                
                String[] tokens = FieldedStringTokenizer.split(line, ",");
                assertThat(tokens.length).isEqualTo(4);

                final String user = tokens[0].toUpperCase().strip();
                final String currentState = tokens[1].toUpperCase().strip();
                final String action = tokens[2].toUpperCase().strip();
                final String resultingState = tokens[3].toUpperCase().strip();

                if (!returnMap.containsKey(user)) {
                    returnMap.put(user, new HashMap<String, Map<String, String>>());
                }

                if (!returnMap.get(user).containsKey(currentState)) {
                    returnMap.get(user).put(currentState, new HashMap<>());
                }

                returnMap.get(user).get(currentState).put(action, resultingState);
            }

            return returnMap;
        }
    }

    /**
     * test all advances.
     *
     * @throws Exception the exception
     */
    private Map<String, String> getWorkflowActionPaths(final String user,
        final String currentStatus) {
        
        final Map<String, String> results = new HashMap<>();

        for (String action : actions) {
            
            String resultingStatus = getWorkflowStatusByAction(user, currentStatus, action);
            results.put(action, resultingStatus);
        }

        return results;
    }

    /**
     * validate a row.
     *
     * @throws Exception the exception
     */
    private void validateRow(final WorkflowHistory workflowHistory, final String userName, final String action, final String status, final String note) {
        
        assertThat(workflowHistory.getUserName()).isEqualTo("testUser");
        assertThat(workflowHistory.getWorkflowAction()).isEqualTo(action);
        assertThat(workflowHistory.getWorkflowStatus()).isEqualTo(status);
        assertThat(workflowHistory.getNotes()).isEqualTo(note);
    }
    
    /**
     * Get a refset.
     *
     * @throws Exception the exception
     */
    private Refset getRefset(final String refsetInternalId) throws Exception {

        final String url = baseUrl + "/" + refsetInternalId;

        final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        final String content = result.getResponse().getContentAsString();

        return new ObjectMapper().readValue(content, Refset.class);
    }
    
    /**
     * createNewRefsetVerion
     *
     * @throws Exception the exception
     */
    private String createNewRefsetVerion() throws Exception {

        final String originalRefsetInternalId =
                getRefsetInternalId(TESTING_REFSET_ID, TESTING_REFSET_VERSION);
        final String url = baseUrl + "/" + originalRefsetInternalId + "/newVersion";
        logger.info("Testing url - " + url);

        // ADD NEW VERSION
        final ObjectNode newVersionBody = objectMapper.createObjectNode();// .put("readVersion", "");

        final MvcResult newVersionResult = mvc
                .perform(post(url).content(newVersionBody.toString())
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        final String newVersionContent = newVersionResult.getResponse().getContentAsString();
        logger.info(" content = " + newVersionContent);

        final JsonNode newVersionRoot = objectMapper.readTree(newVersionContent);
        final JsonNode newVersionNode = newVersionRoot;

        assertTrue(newVersionNode.has("refsetInternalId"));
        final String newRefsetInternalId = newVersionNode.get("refsetInternalId").asText();
        assertThat(newRefsetInternalId).isNotEqualTo(originalRefsetInternalId);
        logger.info("New Version Internal ID - " + newRefsetInternalId);

        // verify the new version
        try (final TerminologyService service = new TerminologyService()) {

            Refset refset = service.get(newRefsetInternalId, Refset.class);
            assertThat(refset).isNotNull();
            assertThat(refset.getRefsetId()).isEqualTo(TESTING_REFSET_ID);
            assertThat(refset.getVersionStatus()).isEqualTo(Refset.IN_DEVELOPMENT);
            assertThat(refset.getVersionDate()).isNull();
            assertTrue(refset.isLatestVersion());
        }
        
        return newRefsetInternalId;
    }
    
    /**
     * deleteNewRefsetVerion
     *
     * @throws Exception the exception
     */
    private void deleteNewRefsetVerion(final String newRefsetInternalId) throws Exception {

        final String originalRefsetInternalId =
                getRefsetInternalId(TESTING_REFSET_ID, TESTING_REFSET_VERSION);
        
        // DELETE NEW VERSION
        final String deleteUrl = baseUrl + "/" + newRefsetInternalId + "/editVersion";
        final MvcResult deleteResult =
                mvc.perform(delete(deleteUrl)).andExpect(status().isOk()).andReturn();
        final String deleteContent = deleteResult.getResponse().getContentAsString();
        final JsonNode deleteRoot = objectMapper.readTree(deleteContent);
        final JsonNode deleteNode = deleteRoot;

        assertTrue(deleteNode.has("status"));
        assertTrue(deleteNode.get("status").asText().equals("deleted"));

        // verify the original refset is back to the latest version
        try (final TerminologyService service = new TerminologyService()) {

            Refset refset = service.get(originalRefsetInternalId, Refset.class);
            assertThat(refset).isNotNull();
            assertTrue(refset.isLatestVersion());
        }
    }
}
