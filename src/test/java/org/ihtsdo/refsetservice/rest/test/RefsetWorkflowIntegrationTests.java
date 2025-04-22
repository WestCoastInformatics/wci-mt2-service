/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.ResultListConcept;
import org.ihtsdo.refsetservice.model.RefsetWorkflowHistory;
import org.ihtsdo.refsetservice.model.enums.WorkflowAction;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.rest.test.util.EditUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.ExportUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.GetUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.WorkflowUnitTestUtilities;
import org.ihtsdo.refsetservice.service.SecurityService;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.BranchService;
import org.ihtsdo.refsetservice.terminologyservice.RefsetWorkflowService;
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
public class RefsetWorkflowIntegrationTests extends AbstractRefsetTests {

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(RefsetWorkflowIntegrationTests.class);

    /** The AUTHOR_USER workflow user . */
    public static final String AUTHOR_USER = "AUTHOR_USER";

    /** The REVIEWER_USER workflow user . */
    public static final String REVIEWER_USER = "REVIEWER_USER";

    /** The ADMIN_USER workflow user . */
    public static final String ADMIN_USER = "ADMIN_USER";

    /** The VIEWER_USER workflow user . */
    public static final String VIEWER_USER = "VIEWER_USER";

    /** The statuses . */
    private static List<WorkflowStatus> statuses = WorkflowStatus.getValues();

    /** The users . */
    private static List<String> users = new ArrayList<>(Arrays.asList(AUTHOR_USER, REVIEWER_USER, ADMIN_USER));

    /** The first time setup. */
    private static boolean firstTimeSetup = true;

    /**
     * Sets the up.
     *
     * @param info the up
     * @throws Exception the exception
     */
    @BeforeEach
    public void setUp(final TestInfo info) throws Exception {

        final SimpleDateFormat sdf = new SimpleDateFormat(YYYYMMDD_FORMAT);

        if (getGetUtil() == null) {

            setGetUtil(new GetUnitTestUtilities(getMvc(), getBaseUrl(), sdf));
            setExportUtil(new ExportUnitTestUtilities(getMvc()));
            setWorkflowUtil(new WorkflowUnitTestUtilities(getMvc(), getBaseUrl(), REFSET_FILE_PATH));
        }

        setObjectMapper(new ObjectMapper());
        JacksonTester.initFields(this, getObjectMapper());
        setBaseUrl("/refset");

        if (firstTimeSetup) {

            // For read/write testing
            setWciTestingProjectId(getGetUtil().getInternalProjectId(WCI_TESTING_PROJECT_NAME));
            setWciTestingEditionId(getGetUtil().getInternalEditionId(WCI_TESTING_EDITION_NAME));

            setEditUtil(new EditUnitTestUtilities(getMvc(), getBaseUrl(), sdf, getWciTestingProjectId(), getWciTestingEditionId()));

            firstTimeSetup = false;
        }
    }

    /**
     * For each role/initial-state pair, ensure that each possible action results in the expected state. If the action is available to that pair, then verify
     * the final-state is as expected. If the action is not available to that pair, verify that the final-state is null
     *
     * @throws Exception the exception
     */
    @Test
    public void testAllRolesAndInitialStates() throws Exception {

        // Testing Ready_For_Edit state
        for (final String user : users) {

            final String userRole = getWorkflowUtil().getUserRole(user);

            for (final WorkflowStatus initialStatus : statuses) {

                final Map<WorkflowAction, String> results = getWorkflowUtil().getWorkflowActionPaths(user, initialStatus);

                for (final WorkflowAction action : results.keySet()) {

                    if (getWorkflowUtil().getPermissiblePaths().containsKey(userRole)
                        && getWorkflowUtil().getPermissiblePaths().get(userRole).containsKey(initialStatus)
                        && getWorkflowUtil().getPermissiblePaths().get(userRole).get(initialStatus).containsKey(action)) {

                        assertThat(results.get(action)).isEqualTo(getWorkflowUtil().getPermissiblePaths().get(userRole).get(initialStatus).get(action));
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
        final String newRefsetVersionInternalId = getEditUtil().createNewRefsetVersion(MAIN_NRC_TESTING_REFSET_ID);

        int actionCount = 1;
        List<RefsetWorkflowHistory> lookedUpWorkflowHistory;
        String note;

        Refset refset = getGetUtil().getRefsetFromInternalId(newRefsetVersionInternalId);
        Refset updatedRefset = null;

        // ALREADY IN EDIT WHEN CREATED
        // Finish First Edit (with Note)
        actionCount++;
        note = "This is the first edit of this workflow";
        updatedRefset = getWorkflowUtil().updateWorkflow(refset, AUTHOR_USER, WorkflowAction.REQUEST_REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowUtil().getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        getWorkflowUtil().validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowAction.REQUEST_REVIEW, WorkflowStatus.READY_FOR_REVIEW,
            note);

        // In Review
        actionCount++;
        note = "";
        updatedRefset = getWorkflowUtil().updateWorkflow(refset, REVIEWER_USER, WorkflowAction.REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowUtil().getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        getWorkflowUtil().validateRow(lookedUpWorkflowHistory.get(actionCount), REVIEWER_USER, WorkflowAction.REVIEW, WorkflowStatus.IN_REVIEW, note);

        // Reject Review (with Note)
        actionCount++;
        note = "I rejected this because it's not right";
        updatedRefset = getWorkflowUtil().updateWorkflow(refset, REVIEWER_USER, WorkflowAction.REJECT_REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowUtil().getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        getWorkflowUtil().validateRow(lookedUpWorkflowHistory.get(actionCount), REVIEWER_USER, WorkflowAction.REJECT_REVIEW, WorkflowStatus.READY_FOR_EDIT,
            note);

        // Second Edit
        actionCount++;
        note = "";
        updatedRefset = getWorkflowUtil().updateWorkflow(refset, AUTHOR_USER, WorkflowAction.EDIT, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowUtil().getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        getWorkflowUtil().validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowAction.EDIT, WorkflowStatus.IN_EDIT, note);

        // Update the note
        note = "Started my edits";
        getWorkflowUtil().updateWorkflowNote(refset, AUTHOR_USER, note);
        lookedUpWorkflowHistory = getWorkflowUtil().getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        getWorkflowUtil().validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowAction.EDIT, WorkflowStatus.IN_EDIT, note);

        // Finish Second Edit (with Note)
        actionCount++;
        note = "Mistakes handled";
        updatedRefset = getWorkflowUtil().updateWorkflow(refset, AUTHOR_USER, WorkflowAction.REQUEST_REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowUtil().getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        getWorkflowUtil().validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowAction.REQUEST_REVIEW, WorkflowStatus.READY_FOR_REVIEW,
            note);

        // Withdraw Review
        actionCount++;
        note = "Not Ready for review yet";
        updatedRefset = getWorkflowUtil().updateWorkflow(refset, REVIEWER_USER, WorkflowAction.WITHDRAW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowUtil().getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        getWorkflowUtil().validateRow(lookedUpWorkflowHistory.get(actionCount), REVIEWER_USER, WorkflowAction.WITHDRAW, WorkflowStatus.READY_FOR_EDIT, note);

        // Resubmit for review
        actionCount++;
        note = "Refset is now ready.";
        updatedRefset = getWorkflowUtil().updateWorkflow(refset, AUTHOR_USER, WorkflowAction.REQUEST_REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowUtil().getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        getWorkflowUtil().validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowAction.REQUEST_REVIEW, WorkflowStatus.READY_FOR_REVIEW,
            note);

        // In Review
        actionCount++;
        note = "";
        updatedRefset = getWorkflowUtil().updateWorkflow(refset, REVIEWER_USER, WorkflowAction.REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowUtil().getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        getWorkflowUtil().validateRow(lookedUpWorkflowHistory.get(actionCount), REVIEWER_USER, WorkflowAction.REVIEW, WorkflowStatus.IN_REVIEW, note);

        // Pass Review (with Note)
        actionCount++;
        note = "This has been fixed as expected";
        updatedRefset = getWorkflowUtil().updateWorkflow(refset, REVIEWER_USER, WorkflowAction.ACCEPT_REVIEW, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowUtil().getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        getWorkflowUtil().validateRow(lookedUpWorkflowHistory.get(actionCount), REVIEWER_USER, WorkflowAction.ACCEPT_REVIEW, WorkflowStatus.REVIEW_COMPLETED,
            note);

        // Request Publication (with Note)
        actionCount++;
        note = "The refset should be published when the full extension is published";
        updatedRefset = getWorkflowUtil().updateWorkflow(refset, AUTHOR_USER, WorkflowAction.REQUEST_PUBLICATION, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowUtil().getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        getWorkflowUtil().validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowAction.REQUEST_PUBLICATION,
            WorkflowStatus.READY_FOR_PUBLICATION, note);

        // Withdraw Publicaiton Request (with Note)
        actionCount++;
        note = "The refset failed RVF.";
        updatedRefset = getWorkflowUtil().updateWorkflow(refset, AUTHOR_USER, WorkflowAction.FAILS_RVF, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowUtil().getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        getWorkflowUtil().validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowAction.FAILS_RVF, WorkflowStatus.READY_FOR_EDIT, note);

        // Start Edit by Mistake
        actionCount++;
        note = "";
        updatedRefset = getWorkflowUtil().updateWorkflow(refset, AUTHOR_USER, WorkflowAction.EDIT, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowUtil().getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        getWorkflowUtil().validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowAction.EDIT, WorkflowStatus.IN_EDIT, note);

        // Cancel Edit (with Note)
        actionCount++;
        note = "That edit was a mistake";
        updatedRefset = getWorkflowUtil().updateWorkflow(refset, AUTHOR_USER, WorkflowAction.CANCEL_EDIT, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowUtil().getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        getWorkflowUtil().validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowAction.CANCEL_EDIT, WorkflowStatus.READY_FOR_EDIT, note);

        // Request Publication Again (with Note)
        actionCount++;
        note = "Fixes made. The refset should be published when the full extension is published";
        updatedRefset = getWorkflowUtil().updateWorkflow(refset, AUTHOR_USER, WorkflowAction.REQUEST_PUBLICATION, note);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowUtil().getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        getWorkflowUtil().validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowAction.REQUEST_PUBLICATION,
            WorkflowStatus.READY_FOR_PUBLICATION, note);

        // Fail Publication by API (with Note)
        actionCount++;
        note = "SNOMED has rejected this refset.";
        // include a fake refset ID
        final String results = getWorkflowUtil().failRefsetPublication(refset.getRefsetId() + ",11112222", note);
        assertThat(results).doesNotContain(refset.getRefsetId());
        updatedRefset = getGetUtil().getRefsetFromInternalId(newRefsetVersionInternalId);
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;
        lookedUpWorkflowHistory = getWorkflowUtil().getWorkflowHistory(newRefsetVersionInternalId);
        assertThat(lookedUpWorkflowHistory.size()).isEqualTo(actionCount + 1);
        getWorkflowUtil().validateRow(lookedUpWorkflowHistory.get(actionCount), AUTHOR_USER, WorkflowAction.FAILS_RVF, WorkflowStatus.READY_FOR_EDIT, note);

        // Request Publication Before Completion
        updatedRefset = getWorkflowUtil().updateWorkflow(refset, AUTHOR_USER, WorkflowAction.REQUEST_PUBLICATION, "");
        assertThat(updatedRefset).isNotNull();
        refset = updatedRefset;

        // !!!! DO NOT LEAVE UNCOMMENTED !!!!
        // Publication Complete
        // String publicationDate = "2000-04-14";
        // String publicationResults = startPublication(refset.getEditionShortName());
        // assertThat(publicationResults).doesNotContain(refset.getRefsetId());
        // publicationResults = completePublication(publicationDate, refset.getEditionShortName());
        // assertThat(publicationResults).doesNotContain(refset.getRefsetId());
        // updatedRefset = getGetUtil().getRefsetFromInternalId(refsetInternalId);
        // assertThat(updatedRefset).isNotNull();
        //
        // // now that it is published make sure there is a version date on the
        // refset and the version status is PUBLISHED
        // assertThat(updatedRefset.getVersionDate()).isNotNull();
        // assertThat(updatedRefset.getVersionStatus()).isEqualTo(Refset.PUBLISHED);
        // assertThat(updatedRefset.getWorkflowStatus()).isEqualTo(Refset.PUBLISHED);

        // !!!! LEAVE THIS UNCOMMENTED EXCEPT WHEN TESTING PUBLICATION COMPLETE STATUS !!!!
        // remove the refset version
        final boolean success = getEditUtil().deleteRefsetVersion(newRefsetVersionInternalId);
        assertThat(success).isTrue();

    }

    /**
     * Simulate an editing-to-publication cycle as much as possible
     *
     * TODO: Advance with Workflow status.
     *
     * @throws Exception the exception
     */
    @Test
    public void testOnExistingRefsetBranch() throws Exception {

        // Refset - 500201000057102 (Address type reference set)
        final String refsetId = "500201000057102";
        final String versionDate = "20201130";
        // final String publicationVersionDate = "20201201";
        String releaseBranchPath = null;
        // String refsetBranchPath = null;
        String refsetInternalId = null;
        Refset refset = null;

        // verify the new version
        try (final TerminologyService service = new TerminologyService()) {

            refsetInternalId = getGetUtil().getInternalRefsetId(refsetId, versionDate);
            refset = service.get(refsetInternalId, Refset.class);
            assertThat(refset).isNotNull();
            releaseBranchPath = refset.getEdition() + "/" + refset.getVersionDate();

            // refsetBranchPath = releaseBranchPath + "/" + WorkflowService.REFSET_BRANCH_PREFIX + refsetId;
            LOG.debug("Have internalId: " + refsetInternalId + " and branch: " + releaseBranchPath);

            // Validate that refset is at expected state
            final Set<WorkflowAction> allowedWorkflowStatuses = RefsetWorkflowService.getAllowedActions(SecurityService.getUserFromSession(), refset);
            assertThat(allowedWorkflowStatuses).contains(WorkflowAction.EDIT);

            /* Mimic Start Editing */
            // Verify number of members at start
            ResultListConcept members = getGetUtil().getMembers(refsetInternalId);
            assertThat(members.size()).isEqualTo(5);

            // Create Edit branch
            // TODO: Remove this as it's unnecessary given bring refset into EDIT state automatically creates the branch
            // final String editBranchPath = snowUtil.createEditBranch(refsetBranchPath, refsetInternalId, refsetId);
            // assertThat(editBranchPath).isNotNull();

            String comment = "Starting testing editing cycle";
            refset = RefsetWorkflowService.setWorkflowStatusByAction(service, SecurityService.getUserFromSession(), WorkflowAction.EDIT, refset, comment);

            // Verify still have the same 5 members in the refset found under the newly edit branch
            members = getGetUtil().getMembers(refsetInternalId);
            assertThat(members.size()).isEqualTo(5);

            // populate with one new member
            boolean success = getEditUtil().addMembers(refsetInternalId, Arrays.asList("404684003"));
            assertThat(success).isTrue();

            // Verify still have the same 6 members in the refset found under the newly create branch
            members = getGetUtil().getMembers(refsetInternalId);
            assertThat(members.size()).isEqualTo(6);

            // Update Workflow State to review
            comment = "The refset is at ready_for_review";
            refset = RefsetWorkflowService.setWorkflowStatusByAction(service, SecurityService.getUserFromSession(), WorkflowAction.REQUEST_REVIEW, refset, comment);

            /* Merge/promote edit branch to refset branch */
            // Verify still have the same 5 members in the refset branch
            members = getGetUtil().getMembers(refsetInternalId);
            assertThat(members.size()).isEqualTo(5);

            // Merge Edit branch back to Refset branch
            success = BranchService.promoteEditIntoRefsetBranch(refset.toBranchDetails(), "Merging after adding one member to refset");
            
            assertThat(success).isTrue();

            // Verify still have the same 6 members in the refset found under the newly create branch
            members = getGetUtil().getMembers(refsetInternalId);
            assertThat(members.size()).isEqualTo(6);

            // Update Workflow State to review
            comment = "The refset is at ready_for_publication";
            refset =
                RefsetWorkflowService.setWorkflowStatusByAction(service, SecurityService.getUserFromSession(), WorkflowAction.REQUEST_PUBLICATION, refset, comment);

        } catch (final Exception e) {

            throw e;
        }

        try (final TerminologyService service = new TerminologyService()) {

            final List<String> failedConcepts = RefsetWorkflowService.completeRefsetPublication(service, refset, versionDate);
            assertThat(failedConcepts).isEmpty();
        } catch (final Exception e) {

            e.printStackTrace();
        }

        /* Mimic Ready for Pub */
        // Merge review branch to code system branch
        // Merge/promote edit branch to review branch

        // refset.getEdition().get

        // Delete refset branch - This is done once we are notified that
        // comment = "The refset editing cycle is done. Delete refset branch from about-to-be-published release branch.";
        // WorkflowService.getdeleteBranch(publicationVersionDate);
    }

}
