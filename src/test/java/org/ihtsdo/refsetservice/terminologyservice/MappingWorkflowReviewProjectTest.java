package org.ihtsdo.refsetservice.terminologyservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.MappingWorkflow;
import org.ihtsdo.refsetservice.model.MappingWorkflowHistory;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service tests for {@link org.ihtsdo.refsetservice.helpers.WorkflowType#REVIEW_PROJECT} mapping workflow.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "auth.dev.bypass=false",
    "mapping.workflow.concept.branch.enabled=false"
})
public class MappingWorkflowReviewProjectTest {

    @BeforeEach
    public void disableConceptBranchSideEffects() {

        PropertyUtility.setProperty("mapping.workflow.concept.branch.enabled", "false");
    }

    @Test
    public void finishEditing_routesToReviewNeeded() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.FINISH_EDITING,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Done", null);

            assertEquals(MapWorkflowStatus.REVIEW_NEEDED, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());
        }
    }

    @Test
    public void startReview_byLead() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.REVIEW_NEEDED);

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getLeadUser(), MappingWorkflowAction.START_REVIEW,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Start review", null);

            assertEquals(MapWorkflowStatus.REVIEW_IN_PROGRESS, updated.getWorkflowStatus());
            assertEquals(context.getLeadUser().getUserName(), updated.getAssignedUser());
        }
    }

    @Test
    public void startReview_bySpecialistRejected() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.REVIEW_NEEDED);

            assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.START_REVIEW,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Start review", null));

            assertEquals(MapWorkflowStatus.REVIEW_NEEDED, context.reloadWorkflow().getWorkflowStatus());
            assertNull(context.reloadWorkflow().getAssignedUser());
        }
    }

    @Test
    public void acceptReview() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            context.setWorkflowAssignedTo(context.getLeadUser().getUserName(), MapWorkflowStatus.REVIEW_IN_PROGRESS);

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getLeadUser(), MappingWorkflowAction.ACCEPT_REVIEW,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Accepted", null);

            assertEquals(MapWorkflowStatus.REVIEW_RESOLVED, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());
        }
    }

    @Test
    public void rejectReview() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            context.setWorkflowAssignedTo(context.getLeadUser().getUserName(), MapWorkflowStatus.REVIEW_IN_PROGRESS);
            final int historyBefore = context.historyCount();

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getLeadUser(), MappingWorkflowAction.REJECT_REVIEW,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Rejected", null);

            assertEquals(MapWorkflowStatus.NEW, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());
            assertEquals(historyBefore + 1, context.historyCount());

            final MappingWorkflowHistory latest = context.latestHistory();
            assertEquals(MappingWorkflowAction.REJECT_REVIEW, latest.getWorkflowAction());
            assertEquals(MapWorkflowStatus.NEW, latest.getWorkflowStatus());
        }
    }

    @Test
    public void approve_afterReview() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.REVIEW_RESOLVED);

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getLeadUser(), MappingWorkflowAction.APPROVE_FOR_PUBLICATION,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Approved", null);

            assertEquals(MapWorkflowStatus.READY_FOR_PUBLICATION, updated.getWorkflowStatus());
        }
    }

    @Test
    public void reviewProjectEndToEnd() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.ASSIGN,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Assign", null);
            MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.FINISH_EDITING,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Finish", null);
            MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getLeadUser(), MappingWorkflowAction.START_REVIEW,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Review", null);
            MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getLeadUser(), MappingWorkflowAction.ACCEPT_REVIEW,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Accept", null);
            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getLeadUser(), MappingWorkflowAction.APPROVE_FOR_PUBLICATION,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Approve", null);

            assertEquals(MapWorkflowStatus.READY_FOR_PUBLICATION, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());

            final ResultList<MappingWorkflowHistory> history =
                MappingWorkflowService.getWorkflowHistory(context.getService(), context.getWorkflow(), new SearchParameters());
            assertThat(history.getItems()).hasSizeGreaterThanOrEqualTo(5);

            for (final MappingWorkflowHistory row : history.getItems()) {
                if (row.getWorkflowAction() == MappingWorkflowAction.ASSIGN
                    || row.getWorkflowAction() == MappingWorkflowAction.START_REVIEW) {
                    assertThat(row.getWorkflowStatus()).isIn(MapWorkflowStatus.EDITING_IN_PROGRESS, MapWorkflowStatus.REVIEW_IN_PROGRESS);
                }
            }
        }
    }
}
