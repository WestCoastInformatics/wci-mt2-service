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

    /** The promotion called. */
    private final AtomicBoolean promotionCalled = new AtomicBoolean(false);

    /**
     * Setup promotion mock.
     */
    @BeforeEach
    public void setupPromotionMock() {

        promotionCalled.set(false);
        MapSetWorkflowService.setEditPromotionOperationsForTests((final BranchInformation branchInformation, final String comment) -> {
            promotionCalled.set(true);
            return true;
        });
    }

    /**
     * Restore promotion mock.
     */
    @AfterEach
    public void restorePromotionMock() {

        MapSetWorkflowService.setEditPromotionOperationsForTests(null);
        PropertyUtility.setProperty("auth.dev.bypass", "true");
    }

    /**
     * Finish edit blocked by in progress mapping.
     *
     * @throws Exception the exception
     */
    @Test
    public void finishEditBlockedByInProgressMapping() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> MappingWorkflowService.assertMapsetTransitionAllowed(context.getService(), context.getMapSet(), WorkflowAction.FINISH_EDIT));

            assertEquals(HttpStatus.CONFLICT, exception.getStatus());
            assertTrue(exception.getReason().contains("blocked by mapping"));
            assertTrue(exception.getReason().contains(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE));
            assertEquals(WorkflowStatus.IN_EDIT, context.reloadMapSet().getWorkflowStatus());
            assertFalse(promotionCalled.get());
        }
    }

    /**
     * Finish edit allowed when all terminal.
     *
     * @throws Exception the exception
     */
    @Test
    public void finishEditAllowedWhenAllTerminal() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.EDITING_DONE);
            context.addWorkflowForConcept("concept-new", MapWorkflowStatus.NEW);
            context.addWorkflowForConcept("concept-ready", MapWorkflowStatus.READY_FOR_PUBLICATION);

            MappingWorkflowService.assertMapsetTransitionAllowed(context.getService(), context.getMapSet(), WorkflowAction.FINISH_EDIT);
            MapSetWorkflowService.setWorkflowStatus(context.getService(), context.getAdminUser(), WorkflowAction.FINISH_EDIT, context.getMapSet(),
                "Finish edit", WorkflowStatus.READY_FOR_EDIT, null);

            assertEquals(WorkflowStatus.READY_FOR_EDIT, context.reloadMapSet().getWorkflowStatus());
        }
    }

    /**
     * Finish edit allowed with untouched new.
     *
     * @throws Exception the exception
     */
    @Test
    public void finishEditAllowedWithUntouchedNew() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.NEW);
            for (int index = 0; index < 5; index++) {
                context.addWorkflowForConcept("untouched-" + index, MapWorkflowStatus.NEW);
            }
            context.addWorkflowForConcept("ready-1", MapWorkflowStatus.READY_FOR_PUBLICATION);
            context.addWorkflowForConcept("ready-2", MapWorkflowStatus.READY_FOR_PUBLICATION);

            MappingWorkflowService.assertMapsetTransitionAllowed(context.getService(), context.getMapSet(), WorkflowAction.FINISH_EDIT);
            MapSetWorkflowService.setWorkflowStatus(context.getService(), context.getAdminUser(), WorkflowAction.FINISH_EDIT, context.getMapSet(),
                "Finish edit", WorkflowStatus.READY_FOR_EDIT, null);

            assertEquals(WorkflowStatus.READY_FOR_EDIT, context.reloadMapSet().getWorkflowStatus());
        }
    }

    /**
     * Request publication blocked by review in progress.
     *
     * @throws Exception the exception
     */
    @Test
    public void requestPublicationBlockedByReviewInProgress() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getLeadUser().getUserName(), MapWorkflowStatus.REVIEW_IN_PROGRESS);

            final ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> MappingWorkflowService.assertMapsetTransitionAllowed(context.getService(), context.getMapSet(), WorkflowAction.REQUEST_PUBLICATION));

            assertEquals(HttpStatus.CONFLICT, exception.getStatus());
            assertTrue(exception.getReason().contains("blocked by mapping"));
            assertEquals(WorkflowStatus.IN_EDIT, context.reloadMapSet().getWorkflowStatus());
            assertFalse(promotionCalled.get());
        }
    }
}
