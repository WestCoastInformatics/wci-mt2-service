package org.ihtsdo.refsetservice.terminologyservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.ihtsdo.refsetservice.Application;
import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.MappingWorkflow;
import org.ihtsdo.refsetservice.model.MappingWorkflowBulkResult;
import org.ihtsdo.refsetservice.model.MappingWorkflowHistory;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service tests for {@link org.ihtsdo.refsetservice.helpers.WorkflowType#REVIEW_PROJECT} mapping workflow.
 */
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "auth.dev.bypass=false", "mapping.workflow.concept.branch.enabled=false"
})
public class MappingWorkflowReviewProjectTest {

    /**
     * Disable concept branch side effects.
     */
    @BeforeEach
    public void disableConceptBranchSideEffects() {

        PropertyUtility.setProperty("mapping.workflow.concept.branch.enabled", "false");
    }

    /**
     * Finish editing routes to review needed.
     *
     * @throws Exception the exception
     */
    @Test
    public void finishEditingRoutesToReviewNeeded() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(),
                MappingWorkflowAction.FINISH_EDITING, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Done", null);

            assertEquals(MapWorkflowStatus.REVIEW_NEEDED, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());
        }
    }

    /**
     * Start review by lead.
     *
     * @throws Exception the exception
     */
    @Test
    public void startReviewByLead() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.REVIEW_NEEDED);

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getLeadUser(),
                MappingWorkflowAction.START_REVIEW, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Start review", null);

            assertEquals(MapWorkflowStatus.REVIEW_IN_PROGRESS, updated.getWorkflowStatus());
            assertEquals(context.getLeadUser().getUserName(), updated.getAssignedUser());
        }
    }

    /**
     * Lead START_REVIEW with assignToUser assigns to the selected user, not the lead.
     *
     * @throws Exception the exception
     */
    @Test
    public void startReviewToOtherByLead() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.REVIEW_NEEDED);

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getLeadUser(),
                MappingWorkflowAction.START_REVIEW, context.getWorkflow(), context.getMapSet(), context.getMapProject(),
                "Start review for another lead", context.getAdminMapUser().getId());

            assertEquals(MapWorkflowStatus.REVIEW_IN_PROGRESS, updated.getWorkflowStatus());
            assertEquals(context.getAdminUser().getUserName(), updated.getAssignedUser());
            assertEquals(MappingWorkflowAction.START_REVIEW, context.latestHistory().getWorkflowAction());
            assertEquals(context.getLeadUser().getUserName(), context.latestHistory().getUserName());
        }
    }

    /**
     * Lead START_REVIEW to a specialist is rejected.
     *
     * @throws Exception the exception
     */
    @Test
    public void startReviewToSpecialistRejected() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.REVIEW_NEEDED);

            final ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getLeadUser(), MappingWorkflowAction.START_REVIEW,
                    context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Start review", context.getOtherSpecialistMapUser().getId()));

            assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatus());
            assertEquals(MapWorkflowStatus.REVIEW_NEEDED, context.reloadWorkflow().getWorkflowStatus());
            assertNull(context.reloadWorkflow().getAssignedUser());
        }
    }

    /**
     * Start review by specialist rejected.
     *
     * @throws Exception the exception
     */
    @Test
    public void startReviewBySpecialistRejected() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.REVIEW_NEEDED);

            assertThrows(ResponseStatusException.class,
                () -> MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(), MappingWorkflowAction.START_REVIEW,
                    context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Start review", null));

            assertEquals(MapWorkflowStatus.REVIEW_NEEDED, context.reloadWorkflow().getWorkflowStatus());
            assertNull(context.reloadWorkflow().getAssignedUser());
        }
    }

    /**
     * Accept review.
     *
     * @throws Exception the exception
     */
    @Test
    public void acceptReview() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            context.setWorkflowAssignedTo(context.getLeadUser().getUserName(), MapWorkflowStatus.REVIEW_IN_PROGRESS);

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getLeadUser(),
                MappingWorkflowAction.ACCEPT_REVIEW, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Accepted", null);

            assertEquals(MapWorkflowStatus.READY_FOR_PUBLICATION, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());
        }
    }

    /**
     * Reject review.
     *
     * @throws Exception the exception
     */
    @Test
    public void rejectReview() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            context.setWorkflowAssignedTo(context.getLeadUser().getUserName(), MapWorkflowStatus.REVIEW_IN_PROGRESS);
            final int historyBefore = context.historyCount();

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getLeadUser(),
                MappingWorkflowAction.REJECT_REVIEW, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Rejected", null);

            assertEquals(MapWorkflowStatus.NEW, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());
            assertEquals(historyBefore + 1, context.historyCount());

            final MappingWorkflowHistory latest = context.latestHistory();
            assertEquals(MappingWorkflowAction.REJECT_REVIEW, latest.getWorkflowAction());
            assertEquals(MapWorkflowStatus.NEW, latest.getWorkflowStatus());
        }
    }

    /**
     * Approve after review.
     *
     * @throws Exception the exception
     */
    @Test
    public void approveAfterReview() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.REVIEW_RESOLVED);

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getLeadUser(),
                MappingWorkflowAction.APPROVE_FOR_PUBLICATION, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Approved", null);

            assertEquals(MapWorkflowStatus.READY_FOR_PUBLICATION, updated.getWorkflowStatus());
        }
    }

    /**
     * Review project end to end.
     *
     * @throws Exception the exception
     */
    @Test
    public void reviewProjectEndToEnd() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(), MappingWorkflowAction.ASSIGN,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Assign", null);
            MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(), MappingWorkflowAction.FINISH_EDITING,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Finish", null);
            MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getLeadUser(), MappingWorkflowAction.START_REVIEW,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Review", null);
            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getLeadUser(),
                MappingWorkflowAction.ACCEPT_REVIEW, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Accept", null);

            assertEquals(MapWorkflowStatus.READY_FOR_PUBLICATION, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());

            final ResultList<MappingWorkflowHistory> history =
                MappingWorkflowService.getWorkflowHistory(context.getService(), context.getWorkflow(), new SearchParameters());
            assertThat(history.getItems()).hasSizeGreaterThanOrEqualTo(4);

            for (final MappingWorkflowHistory row : history.getItems()) {
                if (row.getWorkflowAction() == MappingWorkflowAction.ASSIGN || row.getWorkflowAction() == MappingWorkflowAction.START_REVIEW) {
                    assertThat(row.getWorkflowStatus()).isIn(MapWorkflowStatus.EDITING_IN_PROGRESS, MapWorkflowStatus.REVIEW_IN_PROGRESS);
                }
            }
        }
    }

    /**
     * Bulk accept review succeeds for eligible concepts and reports failures for others.
     *
     * @throws Exception the exception
     */
    @Test
    public void bulkAcceptReviewReportsPartialFailures() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            context.setWorkflowAssignedTo(context.getLeadUser().getUserName(), MapWorkflowStatus.REVIEW_IN_PROGRESS);

            final MappingWorkflow second = context.addWorkflowForConcept("555666777", MapWorkflowStatus.REVIEW_NEEDED);

            final MappingWorkflowBulkResult result = MappingWorkflowService.setWorkflowStatusByActionBulk(context.getService(), context.getLeadUser(),
                MappingWorkflowAction.ACCEPT_REVIEW, context.getMapSet(), context.getMapProject(),
                Arrays.asList(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, second.getSourceConceptCode()), "Bulk accepted", null);

            assertEquals(1, result.getSuccessCount());
            assertEquals(1, result.getFailureCount());
            assertTrue(result.getItems().get(0).isSuccess());
            assertEquals(MapWorkflowStatus.READY_FOR_PUBLICATION, result.getItems().get(0).getWorkflow().getWorkflowStatus());
            assertFalse(result.getItems().get(1).isSuccess());
            assertEquals(HttpStatus.CONFLICT.value(), result.getItems().get(1).getStatus().intValue());
            assertEquals(MapWorkflowStatus.REVIEW_NEEDED, context.getService().get(second.getId(), MappingWorkflow.class).getWorkflowStatus());
        }
    }

    /**
     * Bulk request rejects an empty concept list.
     *
     * @throws Exception the exception
     */
    @Test
    public void bulkRejectsEmptyConceptCodes() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            final ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> MappingWorkflowService.setWorkflowStatusByActionBulk(context.getService(), context.getLeadUser(), MappingWorkflowAction.ACCEPT_REVIEW,
                    context.getMapSet(), context.getMapProject(), Collections.emptyList(), "notes", null));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }
    }
}
