package org.ihtsdo.refsetservice.terminologyservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service tests for {@link org.ihtsdo.refsetservice.helpers.WorkflowType#CONFLICT_PROJECT} mapping workflow.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "auth.dev.bypass=false",
    "mapping.workflow.concept.branch.enabled=false"
})
public class MappingWorkflowConflictProjectTest {

    private static final String TARGET_CODE_A = "TARGET:111111";

    private static final String TARGET_CODE_B = "TARGET:222222";

    @BeforeEach
    public void disableConceptBranchSideEffects() {

        PropertyUtility.setProperty("mapping.workflow.concept.branch.enabled", "false");
    }

    @Test
    public void slot1AssignIndependentOfSlot2() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createConflictProjectInEdit()) {
            MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.ASSIGN,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Assign slot 1", null);

            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, context.reloadWorkflow().getWorkflowStatus());
            assertEquals(MapWorkflowStatus.NEW, context.reloadWorkflowSlot2().getWorkflowStatus());
            assertNull(context.getWorkflowSlot2().getAssignedUser());
        }
    }

    @Test
    public void bothFinishAgree() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createConflictProjectInEdit()) {
            finishBothSlots(context, TARGET_CODE_A, TARGET_CODE_A);

            assertEquals(MapWorkflowStatus.REVIEW_NEEDED, context.reloadWorkflow().getWorkflowStatus());
            assertEquals(MapWorkflowStatus.REVIEW_NEEDED, context.reloadWorkflowSlot2().getWorkflowStatus());
        }
    }

    @Test
    public void bothFinishDiffer() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createConflictProjectInEdit()) {
            finishBothSlots(context, TARGET_CODE_A, TARGET_CODE_B);

            assertEquals(MapWorkflowStatus.CONFLICT_DETECTED, context.reloadWorkflow().getWorkflowStatus());
            assertEquals(MapWorkflowStatus.CONFLICT_DETECTED, context.reloadWorkflowSlot2().getWorkflowStatus());
        }
    }

    @Test
    public void oneSlotFinishedOtherOpen() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createConflictProjectInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());
            MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.FINISH_EDITING,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), TARGET_CODE_A, null);

            assertEquals(MapWorkflowStatus.EDITING_DONE, context.reloadWorkflow().getWorkflowStatus());
            assertEquals(MapWorkflowStatus.NEW, context.reloadWorkflowSlot2().getWorkflowStatus());
        }
    }

    @Test
    public void resolveConflict() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createConflictProjectInEdit()) {
            finishBothSlots(context, TARGET_CODE_A, TARGET_CODE_B);
            context.setWorkflowStatus(MapWorkflowStatus.CONFLICT_DETECTED);
            context.setWorkflowSlot2Status(MapWorkflowStatus.CONFLICT_DETECTED);

            assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getLeadUser(), MappingWorkflowAction.START_CONFLICT_RESOLUTION,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Start resolution", null));

            assertEquals(MapWorkflowStatus.CONFLICT_DETECTED, context.reloadWorkflow().getWorkflowStatus());
            assertEquals(MapWorkflowStatus.CONFLICT_DETECTED, context.reloadWorkflowSlot2().getWorkflowStatus());
        }
    }

    private static void finishBothSlots(final MappingWorkflowTestFixtures.Context context, final String slot1Target, final String slot2Target)
        throws Exception {

        context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());
        MappingWorkflowService.setWorkflowStatusByAction(
            context.getService(), context.getSpecialistUser(), MappingWorkflowAction.FINISH_EDITING,
            context.getWorkflow(), context.getMapSet(), context.getMapProject(), slot1Target, null);

        context.setWorkflowSlot2AssignedTo(context.getOtherSpecialistUser().getUserName());
        MappingWorkflowService.setWorkflowStatusByAction(
            context.getService(), context.getOtherSpecialistUser(), MappingWorkflowAction.FINISH_EDITING,
            context.getWorkflowSlot2(), context.getMapSet(), context.getMapProject(), slot2Target, null);
    }
}
