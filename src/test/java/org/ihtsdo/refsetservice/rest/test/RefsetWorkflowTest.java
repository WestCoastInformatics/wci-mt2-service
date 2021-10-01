
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.refsetservice.util.FieldedStringTokenizer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.json.JacksonTester;

import com.fasterxml.jackson.databind.ObjectMapper;

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

    private Map<WorkflowUser, Map<WorkflowState, Map<WorkflowAction, WorkflowState>>> permissiblePaths;

    private static enum WorkflowAction {
        REQUEST_REVIEW, REQUEST_PUBLICATION, EDIT, FINISH_EDIT, FAILS_RVF, REFSET_PUBLISHED, REVIEW, REJECT_REVIEW, PASS_REVIEW, UNASSIGN
    };

    private static enum WorkflowState {
        READY_FOR_EDIT, IN_EDIT, READY_FOR_REVIEW, IN_REVIEW, REVIEW_COMPLETED, READY_FOR_PUBLICATION
    }

    private static enum WorkflowUser {
        AUTHOR_USER, REVIEWER_USER, AUTHOR_AND_REVIEWER_ROLE, ADMIN_USER, VIEWER_USER
    }

    /**
     * Sets the up.
     */
    @BeforeEach
    public void setUp(TestInfo info) {

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
        for (final WorkflowUser user : WorkflowUser.values()) {
            for (final WorkflowState initialState : WorkflowState.values()) {
                final Map<WorkflowAction, WorkflowState> results =
                        testAllAdvances(user, initialState);

                for (final WorkflowAction action : results.keySet()) {
                    if (permissiblePaths.get(user).get(initialState).containsKey(action)) {
                        assertThat(results.get(action)).isEqualTo(
                                permissiblePaths.get(user).get(initialState).get(action));
                    } else {
                        assertThat(results.get(action)).isNull();
                    }
                }
            }
        }
    }

    @Test
    public void testWorkflowHistory() throws Exception {
        // Testing Ready_For_Edit state
        String refsetId = ""; // TODO: Define Refset Id
        int actionCount = 0;

        // TODO: Do we create a new Refset Concept and delete it per execution?

        // First Edit
        Date advanceTimestamp = new Date();
        String note = "";
        advanceWorkflow(refsetId, WorkflowUser.AUTHOR_USER, WorkflowAction.EDIT, advanceTimestamp,
                note);
        List<List<String>> lookedUpWorkflowHistory = getWorkflowHistory();
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount++), "Author_User", "Edit",
                advanceTimestamp.toString(), "");

        // Finish First Edit (with Note)
        advanceTimestamp = new Date();
        note = "This is the first edit of this workflow";
        advanceWorkflow(refsetId, WorkflowUser.AUTHOR_USER, WorkflowAction.REQUEST_REVIEW,
                advanceTimestamp, note);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount++), "Author_User", "Request_Review",
                advanceTimestamp.toString(), note);

        // In Review
        advanceTimestamp = new Date();
        note = "";
        advanceWorkflow(refsetId, WorkflowUser.REVIEWER_USER, WorkflowAction.REVIEW,
                advanceTimestamp, note);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount++), "Reviewer_User", "Review",
                advanceTimestamp.toString(), note);

        // Reject Review (with Note)
        advanceTimestamp = new Date();
        note = "I rejected this because it's not right";
        advanceWorkflow(refsetId, WorkflowUser.REVIEWER_USER, WorkflowAction.REJECT_REVIEW,
                advanceTimestamp, note);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount++), "Reviewer_User", "Reject_Review",
                advanceTimestamp.toString(), note);

        // Second Edit
        advanceTimestamp = new Date();
        note = "";
        advanceWorkflow(refsetId, WorkflowUser.AUTHOR_USER, WorkflowAction.EDIT, advanceTimestamp,
                note);
        lookedUpWorkflowHistory = getWorkflowHistory();
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount++), "Author_User", "Edit",
                advanceTimestamp.toString(), "");

        // Finish Second Edit (with Note)
        advanceTimestamp = new Date();
        note = "Mistakes handled";
        advanceWorkflow(refsetId, WorkflowUser.AUTHOR_USER, WorkflowAction.REQUEST_REVIEW,
                advanceTimestamp, note);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount++), "Author_User", "Request_Review",
                advanceTimestamp.toString(), note);

        // In Review
        advanceTimestamp = new Date();
        note = "";
        advanceWorkflow(refsetId, WorkflowUser.REVIEWER_USER, WorkflowAction.REVIEW,
                advanceTimestamp, note);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount++), "Reviewer_User", "Review",
                advanceTimestamp.toString(), note);

        // Pass Review (with Note)
        advanceTimestamp = new Date();
        note = "This has been fixed as expected";
        advanceWorkflow(refsetId, WorkflowUser.REVIEWER_USER, WorkflowAction.PASS_REVIEW,
                advanceTimestamp, note);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        validateRow(lookedUpWorkflowHistory.get(actionCount++), "Reviewer_User", "Pass_Review",
                advanceTimestamp.toString(), note);

        // Reject Review (with Note)
        advanceTimestamp = new Date();
        note = "The refset should be published when the full extension is published";
        advanceWorkflow(refsetId, WorkflowUser.AUTHOR_USER, WorkflowAction.REQUEST_PUBLICATION,
                advanceTimestamp, note);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);

        // Revalidate all rows to insure no data loss
        validateRow(lookedUpWorkflowHistory.get(actionCount++), "Author_User", "Edit",
                advanceTimestamp.toString(), "");
        validateRow(lookedUpWorkflowHistory.get(actionCount++), "Author_User", "Request_Review",
                advanceTimestamp.toString(), note);
        validateRow(lookedUpWorkflowHistory.get(actionCount++), "Reviewer_User", "Review",
                advanceTimestamp.toString(), note);
        validateRow(lookedUpWorkflowHistory.get(actionCount++), "Reviewer_User", "Reject_Review",
                advanceTimestamp.toString(), note);
        validateRow(lookedUpWorkflowHistory.get(actionCount++), "Author_User", "Edit",
                advanceTimestamp.toString(), "");
        validateRow(lookedUpWorkflowHistory.get(actionCount++), "Author_User", "Request_Review",
                advanceTimestamp.toString(), note);
        validateRow(lookedUpWorkflowHistory.get(actionCount++), "Reviewer_User", "Review",
                advanceTimestamp.toString(), note);
        validateRow(lookedUpWorkflowHistory.get(actionCount++), "Reviewer_User", "Pass_Review",
                advanceTimestamp.toString(), note);
        validateRow(lookedUpWorkflowHistory.get(actionCount++), "Author_User",
                "Request_Publication", advanceTimestamp.toString(), note);
    }

    private List<List<String>> getWorkflowHistory() {
        // TODO TIM: This is to return the workflow history for a given refset.
        //
        // The return object would be a list of advancements (rows in table).
        // Within each advancement would be a list string values (for the 4
        // columns per row)
        return null;
    }

    private WorkflowState listAvailableActions(WorkflowUser user, WorkflowState initialState,
        WorkflowAction action) {
        // TODO TIM: This is to return the resultingState for a given action
        // taken upon an initialState and a user (from where the available roles
        // are defined)
        //
        // Returns:
        // - the resultingState pair (if a valid action)
        // - Null (if action isn't available to the currentState/availableRoles)
        //

        return null;
    }

    private WorkflowState advanceWorkflow(String refsetId, WorkflowUser user, WorkflowAction action,
        Date advanceTimestamp, String note) {
        // TODO TIM: This is to actually advance workflow
        //
        // You have:
        // - refsetId is the refset to advance workflow upon
        // - user to a) get the roles are available to the user and b) add for
        // WfHx
        // - action to a) identify the action the user has selected and b) add
        // for WfHx
        // - timestamp to add for WfHx
        // - note to add for WfHx (only available at 3 points)
        //
        return null;
    }

    private Map<WorkflowUser, Map<WorkflowState, Map<WorkflowAction, WorkflowState>>> defineAllPermissiblePermutations() {
        Map<WorkflowUser, Map<WorkflowState, Map<WorkflowAction, WorkflowState>>> retMap =
                new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(WORKFLOW_PERMUTATIONS_FILE))) {
            String line;

            while ((line = br.readLine()) != null) {
                String[] tokens = FieldedStringTokenizer.split(line, ",");
                assertThat(tokens.length).isEqualTo(3);

                final WorkflowUser user = WorkflowUser.valueOf(tokens[0]);
                final WorkflowState currentState = WorkflowState.valueOf(tokens[1]);
                final WorkflowAction action = WorkflowAction.valueOf(tokens[2]);
                final WorkflowState resultingState = WorkflowState.valueOf(tokens[3]);

                if (!retMap.containsKey(user)) {
                    retMap.put(user,
                            new HashMap<WorkflowState, Map<WorkflowAction, WorkflowState>>());
                }

                if (!retMap.get(user).containsKey(currentState)) {
                    retMap.get(user).put(currentState, new HashMap<>());
                }

                retMap.get(user).get(currentState).put(action, resultingState);
            }

            return retMap;
        } catch (Exception e) {

        }

        return null;
    }

    private Map<WorkflowAction, WorkflowState> testAllAdvances(WorkflowUser user,
        WorkflowState currentState) {
        Map<WorkflowAction, WorkflowState> results = new HashMap<>();

        for (WorkflowAction action : WorkflowAction.values()) {
            WorkflowState resultingState = listAvailableActions(user, currentState, action);

            results.put(action, resultingState);
        }

        return results;
    }

    private void validateRow(List<String> rowColumns, String userName, String action,
        String timestamp, String note) {
        assertThat(rowColumns.get(0)).isEqualTo(userName);
        assertThat(rowColumns.get(1)).isEqualTo(action);
        assertThat(rowColumns.get(2)).isEqualTo(timestamp);
        assertThat(rowColumns.get(3)).isEqualTo(note);
    }

}
