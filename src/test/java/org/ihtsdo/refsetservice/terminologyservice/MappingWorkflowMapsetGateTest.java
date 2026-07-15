package org.ihtsdo.refsetservice.terminologyservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.ihtsdo.refsetservice.model.BranchInformation;
import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.enums.WorkflowAction;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service tests for mapset workflow gates driven by per-mapping workflow state.
 */
@SpringBootTest
@ActiveProfiles("test")
public class MappingWorkflowMapsetGateTest {

    private final AtomicBoolean promotionCalled = new AtomicBoolean(false);

    @BeforeEach
    public void setupPromotionMock() {

        promotionCalled.set(false);
        MapSetWorkflowService.setEditPromotionOperationsForTests((final BranchInformation branchInformation, final String comment) -> {
            promotionCalled.set(true);
            return true;
        });
    }

    @AfterEach
    public void restorePromotionMock() {

        MapSetWorkflowService.setEditPromotionOperationsForTests(null);
        PropertyUtility.setProperty("auth.dev.bypass", "true");
    }

    @Test
    public void finishEdit_blockedByInProgressMapping() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.assertMapsetTransitionAllowed(
                context.getService(), context.getMapSet(), WorkflowAction.FINISH_EDIT));

            assertEquals(HttpStatus.CONFLICT, exception.getStatus());
            assertTrue(exception.getReason().contains("blocked by mapping"));
            assertTrue(exception.getReason().contains(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE));
            assertEquals(WorkflowStatus.IN_EDIT, context.reloadMapSet().getWorkflowStatus());
            assertFalse(promotionCalled.get());
        }
    }

    @Test
    public void finishEdit_allowedWhenAllTerminal() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.EDITING_DONE);
            context.addWorkflowForConcept("concept-new", MapWorkflowStatus.NEW);
            context.addWorkflowForConcept("concept-ready", MapWorkflowStatus.READY_FOR_PUBLICATION);

            MappingWorkflowService.assertMapsetTransitionAllowed(
                context.getService(), context.getMapSet(), WorkflowAction.FINISH_EDIT);
            MapSetWorkflowService.setWorkflowStatus(context.getService(), context.getAdminUser(), WorkflowAction.FINISH_EDIT,
                context.getMapSet(), "Finish edit", WorkflowStatus.READY_FOR_EDIT, null);

            assertEquals(WorkflowStatus.READY_FOR_EDIT, context.reloadMapSet().getWorkflowStatus());
        }
    }

    @Test
    public void finishEdit_allowedWithUntouchedNew() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.NEW);
            for (int index = 0; index < 5; index++) {
                context.addWorkflowForConcept("untouched-" + index, MapWorkflowStatus.NEW);
            }
            context.addWorkflowForConcept("ready-1", MapWorkflowStatus.READY_FOR_PUBLICATION);
            context.addWorkflowForConcept("ready-2", MapWorkflowStatus.READY_FOR_PUBLICATION);

            MappingWorkflowService.assertMapsetTransitionAllowed(
                context.getService(), context.getMapSet(), WorkflowAction.FINISH_EDIT);
            MapSetWorkflowService.setWorkflowStatus(context.getService(), context.getAdminUser(), WorkflowAction.FINISH_EDIT,
                context.getMapSet(), "Finish edit", WorkflowStatus.READY_FOR_EDIT, null);

            assertEquals(WorkflowStatus.READY_FOR_EDIT, context.reloadMapSet().getWorkflowStatus());
        }
    }

    @Test
    public void requestPublication_blockedByReviewInProgress() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getLeadUser().getUserName(), MapWorkflowStatus.REVIEW_IN_PROGRESS);

            final ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.assertMapsetTransitionAllowed(
                context.getService(), context.getMapSet(), WorkflowAction.REQUEST_PUBLICATION));

            assertEquals(HttpStatus.CONFLICT, exception.getStatus());
            assertTrue(exception.getReason().contains("blocked by mapping"));
            assertEquals(WorkflowStatus.IN_EDIT, context.reloadMapSet().getWorkflowStatus());
            assertFalse(promotionCalled.get());
        }
    }
}
