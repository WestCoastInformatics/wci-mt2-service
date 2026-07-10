package org.ihtsdo.refsetservice.terminologyservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.MappingWorkflow;
import org.ihtsdo.refsetservice.model.MappingWorkflowHistory;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service-level tests for {@link MappingWorkflowService}.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "auth.dev.bypass=false",
    "mapping.workflow.concept.branch.enabled=false"
})
public class MappingWorkflowServiceTest {

    @BeforeEach
    public void disableConceptBranchSideEffects() {

        PropertyUtility.setProperty("mapping.workflow.concept.branch.enabled", "false");
    }

    @Test
    public void assign_fromNew_bySpecialist() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final TerminologyService service = context.getService();
            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(
                service, context.getSpecialistUser(), MappingWorkflowAction.ASSIGN,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Assigned", null);

            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, updated.getWorkflowStatus());
            assertEquals(context.getSpecialistUser().getUserName(), updated.getAssignedUser());
            assertNotNull(updated.getAssignedAt());
            assertNotNull(updated.getLeaseExpiresAt());
            assertTrue(updated.getLeaseExpiresAt().after(updated.getAssignedAt()));

            final MappingWorkflowHistory history = context.latestHistory();
            assertEquals(MappingWorkflowAction.ASSIGN, history.getWorkflowAction());
            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, history.getWorkflowStatus());
            assertEquals(context.getSpecialistUser().getUserName(), history.getUserName());
        }
    }

    @Test
    public void assign_whenAlreadyAssigned() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getOtherSpecialistUser().getUserName());
            final int historyBefore = context.historyCount();

            assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.ASSIGN,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Assign again", null));

            final MappingWorkflow reloaded = context.reloadWorkflow();
            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, reloaded.getWorkflowStatus());
            assertEquals(context.getOtherSpecialistUser().getUserName(), reloaded.getAssignedUser());
            assertEquals(historyBefore, context.historyCount());
        }
    }

    @Test
    public void assign_byViewer() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getViewerUser(), MappingWorkflowAction.ASSIGN,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Viewer assign", null));

            final MappingWorkflow reloaded = context.reloadWorkflow();
            assertEquals(MapWorkflowStatus.NEW, reloaded.getWorkflowStatus());
            assertNull(reloaded.getAssignedUser());
            assertEquals(0, context.historyCount());
        }
    }

    @Test
    public void assign_whenMapsetNotInEdit() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReadyForEdit()) {
            assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.ASSIGN,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Assign", null));

            final MappingWorkflow reloaded = context.reloadWorkflow();
            assertEquals(MapWorkflowStatus.NEW, reloaded.getWorkflowStatus());
        }
    }

    @Test
    public void release_byAssignee() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.RELEASE,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Release", null);

            assertEquals(MapWorkflowStatus.NEW, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());
            assertNull(updated.getAssignedAt());
            assertNull(updated.getLeaseExpiresAt());
            assertEquals(MappingWorkflowAction.RELEASE, context.latestHistory().getWorkflowAction());
        }
    }

    @Test
    public void release_byNonHolder() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getOtherSpecialistUser().getUserName());
            final int historyBefore = context.historyCount();

            assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.RELEASE,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Release", null));

            final MappingWorkflow reloaded = context.reloadWorkflow();
            assertEquals(context.getOtherSpecialistUser().getUserName(), reloaded.getAssignedUser());
            assertEquals(historyBefore, context.historyCount());
        }
    }

    @Test
    public void release_fromNew() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.RELEASE,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Release", null));

            assertEquals(MapWorkflowStatus.NEW, context.reloadWorkflow().getWorkflowStatus());
        }
    }

    @Test
    public void finishEditing_byAssignee() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.FINISH_EDITING,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Done", null);

            assertEquals(MapWorkflowStatus.EDITING_DONE, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());
            assertEquals(MappingWorkflowAction.FINISH_EDITING, context.latestHistory().getWorkflowAction());
        }
    }

    @Test
    public void finishEditing_fromNew() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.FINISH_EDITING,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Done", null));

            assertEquals(MapWorkflowStatus.NEW, context.reloadWorkflow().getWorkflowStatus());
        }
    }

    @Test
    public void finishEditing_byNonHolder() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getOtherSpecialistUser().getUserName());

            assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.FINISH_EDITING,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Done", null));

            final MappingWorkflow reloaded = context.reloadWorkflow();
            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, reloaded.getWorkflowStatus());
            assertEquals(context.getOtherSpecialistUser().getUserName(), reloaded.getAssignedUser());
        }
    }

    @Test
    public void approve_byLead() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.EDITING_DONE);

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getLeadUser(), MappingWorkflowAction.APPROVE_FOR_PUBLICATION,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Approved", null);

            assertEquals(MapWorkflowStatus.READY_FOR_PUBLICATION, updated.getWorkflowStatus());
            assertEquals(MappingWorkflowAction.APPROVE_FOR_PUBLICATION, context.latestHistory().getWorkflowAction());
        }
    }

    @Test
    public void approve_bySpecialist() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.EDITING_DONE);

            assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.APPROVE_FOR_PUBLICATION,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Approve", null));

            assertEquals(MapWorkflowStatus.EDITING_DONE, context.reloadWorkflow().getWorkflowStatus());
        }
    }

    @Test
    public void forceRelease_byAdmin() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getOtherSpecialistUser().getUserName());

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getAdminUser(), MappingWorkflowAction.FORCE_RELEASE,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Force release", null);

            assertEquals(MapWorkflowStatus.NEW, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());
            assertEquals(MappingWorkflowAction.FORCE_RELEASE, context.latestHistory().getWorkflowAction());
        }
    }

    @Test
    public void forceRelease_bySpecialist() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.FORCE_RELEASE,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Force", null));
        }
    }

    @Test
    public void reassign_byLead() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getLeadUser(), MappingWorkflowAction.REASSIGN,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Reassign",
                context.getOtherSpecialistUser().getUserName());

            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, updated.getWorkflowStatus());
            assertEquals(context.getOtherSpecialistUser().getUserName(), updated.getAssignedUser());
            assertNotNull(updated.getAssignedAt());
            assertEquals(MappingWorkflowAction.REASSIGN, context.latestHistory().getWorkflowAction());
        }
    }

    @Test
    public void reassign_byNonAdmin() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getOtherSpecialistUser(), MappingWorkflowAction.REASSIGN,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Reassign",
                context.getOtherSpecialistUser().getUserName()));

            assertEquals(context.getSpecialistUser().getUserName(), context.reloadWorkflow().getAssignedUser());
        }
    }

    @Test
    public void allowedActions_new_specialist() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final Set<MappingWorkflowAction> allowed = MappingWorkflowService.getAllowedActions(
                context.getSpecialistUser(), context.getWorkflow(), context.getMapSet(), context.getMapProject());

            assertEquals(Set.of(MappingWorkflowAction.ASSIGN), allowed);
            assertTrue(!allowed.contains(MappingWorkflowAction.FINISH_EDITING));
            assertTrue(!allowed.contains(MappingWorkflowAction.APPROVE_FOR_PUBLICATION));
        }
    }

    @Test
    public void allowedActions_inProgress_nonHolder() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getOtherSpecialistUser().getUserName());

            final Set<MappingWorkflowAction> allowed = MappingWorkflowService.getAllowedActions(
                context.getSpecialistUser(), context.getWorkflow(), context.getMapSet(), context.getMapProject());

            assertTrue(allowed.isEmpty());
        }
    }

    @Test
    public void testSimplePathEndToEnd() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final TerminologyService service = context.getService();

            MappingWorkflowService.setWorkflowStatusByAction(
                service, context.getSpecialistUser(), MappingWorkflowAction.ASSIGN,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Assign", null);
            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, context.reloadWorkflow().getWorkflowStatus());

            MappingWorkflowService.setWorkflowStatusByAction(
                service, context.getSpecialistUser(), MappingWorkflowAction.FINISH_EDITING,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Finish", null);
            final MappingWorkflow afterFinish = context.reloadWorkflow();
            assertEquals(MapWorkflowStatus.EDITING_DONE, afterFinish.getWorkflowStatus());
            assertNull(afterFinish.getAssignedUser());

            MappingWorkflowService.setWorkflowStatusByAction(
                service, context.getLeadUser(), MappingWorkflowAction.APPROVE_FOR_PUBLICATION,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Approve", null);
            assertEquals(MapWorkflowStatus.READY_FOR_PUBLICATION, context.reloadWorkflow().getWorkflowStatus());
            assertEquals(3, context.historyCount());

            assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.setWorkflowStatusByAction(
                service, context.getSpecialistUser(), MappingWorkflowAction.ASSIGN,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Too late", null));
        }
    }

    @Test
    public void unauthorized_is401() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getViewerUser(), MappingWorkflowAction.ASSIGN,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "No", null));
            assertEquals(HttpStatus.UNAUTHORIZED, ex.getStatus());
        }
    }
}
