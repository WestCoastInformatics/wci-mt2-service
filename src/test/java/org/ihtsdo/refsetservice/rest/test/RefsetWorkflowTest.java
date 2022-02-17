
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.WorkflowHistory;
import org.ihtsdo.refsetservice.rest.test.util.EditUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.ExportUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.GetUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.WorkflowUnitTestUtilities;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.WorkflowService;
import org.ihtsdo.refsetservice.util.ConceptResultList;
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

    /** The AUTHOR_USER workflow user . */
    public static final String AUTHOR_USER = "AUTHOR_USER";

    /** The REVIEWER_USER workflow user . */
    public static final String REVIEWER_USER = "REVIEWER_USER";

    /** The ADMIN_USER workflow user . */
    public static final String ADMIN_USER = "ADMIN_USER";

    /** The VIEWER_USER workflow user . */
    public static final String VIEWER_USER = "VIEWER_USER";

    /** The statuses . */
    private static List<String> statuses = WorkflowService.WORKFLOW_STATUSES;

    /** The users . */
    private static List<String> users = new ArrayList<>(Arrays.asList(AUTHOR_USER, REVIEWER_USER, ADMIN_USER));

    static private boolean firstTimeSetup = true;

    /**
     * Sets the up.
     */
    @BeforeEach
    public void setUp(TestInfo info) throws Exception {

        if (getUtil == null) {

            getUtil = new GetUnitTestUtilities(mvc, baseUrl, SIMPLE_DATE_FORMAT);
            exportUtil = new ExportUnitTestUtilities(mvc);
            workflowUtil = new WorkflowUnitTestUtilities(mvc, baseUrl, REFSET_FILE_PATH);
        }

        objectMapper = new ObjectMapper();
        JacksonTester.initFields(this, objectMapper);
        baseUrl = "/refset";

        try {

            if (firstTimeSetup) {

                testingProjectId = getUtil.getInternalProjectId(TESTING_PROJECT_NAME);
                testingEditionId = getUtil.getInternalEditionId(TESTING_EDITION_NAME);

                mainNrcTestingRefsetInternalId = getUtil.getInternalRefsetId(MAIN_NRC_TESTING_REFSET_ID, MAIN_NRC_TESTING_REFSET_VERSION);
                mainCoreTestingRefsetInternalId = getUtil.getInternalRefsetId(MAIN_CORE_TESTING_REFSET_ID, MAIN_CORE_TESTING_REFSET_VERSION);

                editUtil = new EditUnitTestUtilities(mvc, baseUrl, SIMPLE_DATE_FORMAT, testingProjectId, testingEditionId);

                firstTimeSetup = false;
            }

        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    /**
     * For each role/initial-state pair, ensure that each possible action results in the expected state. If the action is available to that pair, then verify the final-state
     * is as expected. If the action is not available to that pair, verify that the final-state is null
     *
     * @throws Exception the exception
     */
    @Test
    public void testAllRolesAndInitialStates() throws Exception {

        // Testing Ready_For_Edit state
        for (final String user : users) {

            final String userRole = workflowUtil.getUserRole(user);

            for (final String initialStatus : statuses) {

                final Map<String, String> results = workflowUtil.getWorkflowActionPaths(user, initialStatus);

                for (final String action : results.keySet()) {

                    if (workflowUtil.getPermissiblePaths().containsKey(userRole) && workflowUtil.getPermissiblePaths().get(userRole).containsKey(initialStatus)
                        && workflowUtil.getPermissiblePaths().get(userRole).get(initialStatus).containsKey(action)) {

                        assertThat(results.get(action)).isEqualTo(workflowUtil.getPermissiblePaths().get(userRole).get(initialStatus).get(action));
                    } else {

                        assertThat(results.get(action)).isNull();
                    }

                }

            }

        }

    }

    /**
     * Mimic running workflow on a refset (ignoring actual add/remove of members).
     * 
     * After each advancement, grab workflow history to ensure that the contents are filled out as expected.
     * @throws Exception the exception
     */
    @Test
    public void testWorkflowHistory() throws Exception {

        // Create a new Edit version of a published refset
        final String newRefsetVersionInternalId = editUtil.createNewRefsetVersion(MAIN_NRC_TESTING_REFSET_ID);

        int actionCount = 1;
        List<WorkflowHistory> lookedUpWorkflowHistory;
        String note;

        Refset refset = getUtil.getRefsetFromInternalId(newRefsetVersionInternalId);
        Refset updatedRefset = null;

        // ALREADY IN EDIT WHEN CREATED
        // Finish First Edit (with Note)
        actionCount++;
        note = "This is the first edit of this workflow";
        updatedRefset = workflowUtil.updateWorkflow(refset, AUTHOR_USER, WorkflowService.REQUEST_REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = workflowUtil.getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        workflowUtil.validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowService.REQUEST_REVIEW, WorkflowService.READY_FOR_REVIEW, note);

        // In Review
        actionCount++;
        note = "";
        updatedRefset = workflowUtil.updateWorkflow(refset, REVIEWER_USER, WorkflowService.REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = workflowUtil.getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        workflowUtil.validateRow(lookedUpWorkflowHistory.get(actionCount), REVIEWER_USER, WorkflowService.REVIEW, WorkflowService.IN_REVIEW, note);

        // Reject Review (with Note)
        actionCount++;
        note = "I rejected this because it's not right";
        updatedRefset = workflowUtil.updateWorkflow(refset, REVIEWER_USER, WorkflowService.REJECT_REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = workflowUtil.getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        workflowUtil.validateRow(lookedUpWorkflowHistory.get(actionCount), REVIEWER_USER, WorkflowService.REJECT_REVIEW, WorkflowService.READY_FOR_EDIT, note);

        // Second Edit
        actionCount++;
        note = "";
        updatedRefset = workflowUtil.updateWorkflow(refset, AUTHOR_USER, WorkflowService.EDIT, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = workflowUtil.getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        workflowUtil.validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowService.EDIT, WorkflowService.IN_EDIT, note);

        // Update the note
        note = "Started my edits";
        workflowUtil.updateWorkflowNote(refset, AUTHOR_USER, note);
        lookedUpWorkflowHistory = workflowUtil.getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        workflowUtil.validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowService.EDIT, WorkflowService.IN_EDIT, note);

        // Finish Second Edit (with Note)
        actionCount++;
        note = "Mistakes handled";
        updatedRefset = workflowUtil.updateWorkflow(refset, AUTHOR_USER, WorkflowService.REQUEST_REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = workflowUtil.getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        workflowUtil.validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowService.REQUEST_REVIEW, WorkflowService.READY_FOR_REVIEW, note);

        // Withdraw Review
        actionCount++;
        note = "Not Ready for review yet";
        updatedRefset = workflowUtil.updateWorkflow(refset, REVIEWER_USER, WorkflowService.WITHDRAW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = workflowUtil.getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        workflowUtil.validateRow(lookedUpWorkflowHistory.get(actionCount), REVIEWER_USER, WorkflowService.WITHDRAW, WorkflowService.READY_FOR_EDIT, note);

        // Resubmit for review
        actionCount++;
        note = "Refset is now ready.";
        updatedRefset = workflowUtil.updateWorkflow(refset, AUTHOR_USER, WorkflowService.REQUEST_REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = workflowUtil.getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        workflowUtil.validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowService.REQUEST_REVIEW, WorkflowService.READY_FOR_REVIEW, note);

        // In Review
        actionCount++;
        note = "";
        updatedRefset = workflowUtil.updateWorkflow(refset, REVIEWER_USER, WorkflowService.REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = workflowUtil.getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        workflowUtil.validateRow(lookedUpWorkflowHistory.get(actionCount), REVIEWER_USER, WorkflowService.REVIEW, WorkflowService.IN_REVIEW, note);

        // Pass Review (with Note)
        actionCount++;
        note = "This has been fixed as expected";
        updatedRefset = workflowUtil.updateWorkflow(refset, REVIEWER_USER, WorkflowService.ACCEPT_REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = workflowUtil.getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        workflowUtil.validateRow(lookedUpWorkflowHistory.get(actionCount), REVIEWER_USER, WorkflowService.ACCEPT_REVIEW, WorkflowService.REVIEW_COMPLETED, note);

        // Request Publication (with Note)
        actionCount++;
        note = "The refset should be published when the full extension is published";
        updatedRefset = workflowUtil.updateWorkflow(refset, AUTHOR_USER, WorkflowService.REQUEST_PUBLICATION, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = workflowUtil.getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        workflowUtil.validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowService.REQUEST_PUBLICATION, WorkflowService.READY_FOR_PUBLICATION, note);

        // Withdraw Publicaiton Request (with Note)
        actionCount++;
        note = "The refset failed RVF.";
        updatedRefset = workflowUtil.updateWorkflow(refset, AUTHOR_USER, WorkflowService.FAILS_RVF, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = workflowUtil.getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        workflowUtil.validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowService.FAILS_RVF, WorkflowService.READY_FOR_EDIT, note);

        // Start Edit by Mistake
        actionCount++;
        note = "";
        updatedRefset = workflowUtil.updateWorkflow(refset, AUTHOR_USER, WorkflowService.EDIT, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = workflowUtil.getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        workflowUtil.validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowService.EDIT, WorkflowService.IN_EDIT, note);

        // Cancel Edit (with Note)
        actionCount++;
        note = "That edit was a mistake";
        updatedRefset = workflowUtil.updateWorkflow(refset, AUTHOR_USER, WorkflowService.CANCEL_EDIT, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = workflowUtil.getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        workflowUtil.validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowService.CANCEL_EDIT, WorkflowService.READY_FOR_EDIT, note);

        // Request Publication Again (with Note)
        actionCount++;
        note = "Fixes made. The refset should be published when the full extension is published";
        updatedRefset = workflowUtil.updateWorkflow(refset, AUTHOR_USER, WorkflowService.REQUEST_PUBLICATION, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = workflowUtil.getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        workflowUtil.validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowService.REQUEST_PUBLICATION, WorkflowService.READY_FOR_PUBLICATION, note);

        // Fail Publication by API (with Note)
        actionCount++;
        note = "SNOMED has rejected this refset.";
        // include a fake refset ID
        String results = workflowUtil.failPublication(refset.getRefsetId() + ",11112222", note);
        assertThat(results).doesNotContain(refset.getRefsetId());
        updatedRefset = getUtil.getRefsetFromInternalId(newRefsetVersionInternalId);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = workflowUtil.getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        workflowUtil.validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowService.FAILS_RVF, WorkflowService.READY_FOR_EDIT, note);

        // Request Publication Before Completion
        updatedRefset = workflowUtil.updateWorkflow(refset, AUTHOR_USER, WorkflowService.REQUEST_PUBLICATION, "");
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;

        // !!!! DO NOT LEAVE UNCOMMENTED !!!!
        // Publication Complete
        // String publicationDate = "2000-04-14";
        // String results = completePublication(publicationDate,
        // refset.getEditionShortName());
        // assertThat(results).doesNotContain(refset.getRefsetId());
        // updatedRefset = getUtil.getRefsetFromInternalId(refsetInternalId);
        // assertThat(updatedRefset).isNotNull();
        //
        // // now that it is published make sure there is a version date on the
        // refset and the version status is PUBLISHED
        // assertThat(updatedRefset.getVersionDate()).isNotNull();
        // assertThat(updatedRefset.getVersionStatus()).isEqualTo(Refset.PUBLISHED);
        // assertThat(updatedRefset.getWorkflowStatus()).isEqualTo(Refset.PUBLISHED);

        // !!!! LEAVE THIS UNCOMMENTED EXCEPT WHEN TESTING PUBLICATION COMPLETE
        // STATUS !!!!
        // remove the refset version
        boolean success = editUtil.deleteRefsetVersion(newRefsetVersionInternalId);
        assertThat(success).isTrue();

    }

    /**
     * Simulate an editing-to-publication cycle as much as possible
     * 
     * TODO: Advance with Workflow status
     *
     * @throws Exception the exception
     */
    @Test
    public void testOnExistingRefsetBranch() throws Exception {

        // Refset - 500201000057102 (Address type reference set)
        final String refsetId = "500201000057102";
        final String versionDate = "20201130";
        final String publicationVersionDate = "20201201";
        String releaseBranchPath = null;
        String refsetBranchPath = null;
        String refsetInternalId = null;
        Refset refset = null;

        // verify the new version
        try (final TerminologyService service = new TerminologyService()) {

            refsetInternalId = getUtil.getInternalRefsetId(refsetId, versionDate);
            refset = service.get(refsetInternalId, Refset.class);
            assertThat(refset).isNotNull();
            releaseBranchPath = refset.getEdition() + "/" + refset.getVersionDate();

            refsetBranchPath = releaseBranchPath + "/" + WorkflowService.REFSET_BRANCH_PREFIX + refsetId;
            logger.debug("Have internalId: " + refsetInternalId + " and branch: " + releaseBranchPath);
        } catch (Exception e) {

            throw e;
        }

        // Validate that refset is at expected state
        List<String> allowedWorkflowStatuses = WorkflowService.getAllowedActions(SecurityService.getUserFromSession(), refset);
        assertThat(allowedWorkflowStatuses).contains(WorkflowService.EDIT);

        /* Mimic Start Editing */
        // Verify number of members at start
        ConceptResultList members = getUtil.getMembers(refsetInternalId);
        assertThat(members.size()).isEqualTo(5);

        // Create Edit branch
        // TODO: Remove this as it's unnecessary given bring refset into EDIT state automatically creates the branch
        // final String editBranchPath = snowUtil.createEditBranch(refsetBranchPath, refsetInternalId, refsetId);
        // assertThat(editBranchPath).isNotNull();

        String comment = "Starting testing editing cycle";
        refset = WorkflowService.setWorkflowStatusByAction(SecurityService.getUserFromSession(), WorkflowService.EDIT, refset, comment);

        // Verify still have the same 5 members in the refset found under the newly edit branch
        members = getUtil.getMembers(refsetInternalId);
        assertThat(members.size()).isEqualTo(5);

        // populate with one new member
        boolean success = editUtil.addMembers(refsetInternalId, Arrays.asList("404684003"));
        assertThat(success).isTrue();

        // Verify still have the same 6 members in the refset found under the newly create branch
        members = getUtil.getMembers(refsetInternalId);
        assertThat(members.size()).isEqualTo(6);

        // Update Workflow State to review
        comment = "The refset is at ready_for_review";
        refset = WorkflowService.setWorkflowStatusByAction(SecurityService.getUserFromSession(), WorkflowService.REQUEST_REVIEW, refset, comment);

        /* Merge/promote edit branch to refset branch */
        // Verify still have the same 5 members in the refset branch
        members = getUtil.getMembers(refsetInternalId);
        assertThat(members.size()).isEqualTo(5);

        // Merge Edit branch back to Refset branch
        success = WorkflowService.mergeEditIntoRefsetBranch(refset.getEdition().getBranch(), refsetId, refset.getEditBranchId(), "Merging after adding one member to refset");
        assertThat(success).isTrue();

        // Verify still have the same 6 members in the refset found under the newly create branch
        members = getUtil.getMembers(refsetInternalId);
        assertThat(members.size()).isEqualTo(6);

        // Update Workflow State to review
        comment = "The refset is at ready_for_publication";
        refset = WorkflowService.setWorkflowStatusByAction(SecurityService.getUserFromSession(), WorkflowService.REQUEST_PUBLICATION, refset, comment);

        try (final TerminologyService service = new TerminologyService()) {

            List<String> failedConcepts = WorkflowService.completeRefsetPublication(service, refset, versionDate);
            assertThat(failedConcepts).isEmpty();
        } catch (Exception e) {

            e.printStackTrace();
        }

        /* Mimic Ready for Pub */
        // Merge review branch to code system branch
        // Merge/promote edit branch to review branch

        // refset.getEdition().get

        // Delete refset branch - This is done once we are notified that
        comment = "The refset editing cycle is done. Delete refset branch from about-to-be-published release branch.";
        // WorkflowService.getdeleteBranch(publicationVersionDate);
    }

}
