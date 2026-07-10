package org.ihtsdo.refsetservice.terminologyservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.MappingWorkflow;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.server.ResponseStatusException;

/**
 * Branch side-effect tests for {@link MappingWorkflowService} with mocked concept-branch operations.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "auth.dev.bypass=false")
public class MappingWorkflowServiceBranchTest {

    private final RecordingConceptBranchOperations branchOperations = new RecordingConceptBranchOperations();

    @BeforeEach
    public void setup() {

        PropertyUtility.setProperty("mapping.workflow.concept.branch.enabled", "true");
        MappingWorkflowService.setConceptBranchOperationsForTests(branchOperations);
    }

    @AfterEach
    public void teardown() {

        PropertyUtility.setProperty("mapping.workflow.concept.branch.enabled", "false");
        MappingWorkflowService.setConceptBranchOperationsForTests(null);
    }

    @Test
    public void assign_createsConceptBranch() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final String expectedPath = BranchService.getConceptBranchPath(context.getMapSet(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE);

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.ASSIGN,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Assigned", null);

            assertEquals(1, branchOperations.createCount);
            assertEquals(0, branchOperations.mergeCount);
            assertEquals(0, branchOperations.deleteCount);
            assertEquals(expectedPath, branchOperations.lastCreatedPath);
            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, updated.getWorkflowStatus());
            assertEquals(context.getSpecialistUser().getUserName(), updated.getAssignedUser());
            assertNotNull(updated.getAssignedAt());
        }
    }

    @Test
    public void assign_whenCreateConceptBranchThrows() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            branchOperations.createThrows = true;
            final int historyBefore = context.historyCount();

            assertThrows(Exception.class, () -> MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.ASSIGN,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Assigned", null));

            final MappingWorkflow reloaded = context.reloadWorkflow();
            assertEquals(MapWorkflowStatus.NEW, reloaded.getWorkflowStatus());
            assertNull(reloaded.getAssignedUser());
            assertNull(reloaded.getAssignedAt());
            assertNull(reloaded.getLeaseExpiresAt());
            assertEquals(historyBefore, context.historyCount());
        }
    }

    @Test
    public void finishEditing_mergesConceptBranch() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.FINISH_EDITING,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Done", null);

            assertEquals(1, branchOperations.mergeCount);
            assertEquals(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, branchOperations.lastConceptCode);
            assertEquals(MapWorkflowStatus.EDITING_DONE, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());
        }
    }

    @Test
    public void finishEditing_whenMergeConceptBranchThrows() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());
            branchOperations.mergeThrows = true;
            final int historyBefore = context.historyCount();

            assertThrows(Exception.class, () -> MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.FINISH_EDITING,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Done", null));

            final MappingWorkflow reloaded = context.reloadWorkflow();
            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, reloaded.getWorkflowStatus());
            assertEquals(context.getSpecialistUser().getUserName(), reloaded.getAssignedUser());
            assertEquals(historyBefore, context.historyCount());
        }
    }

    @Test
    public void release_deletesConceptBranch() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getSpecialistUser(), MappingWorkflowAction.RELEASE,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Release", null);

            assertEquals(1, branchOperations.deleteCount);
            assertEquals(MapWorkflowStatus.NEW, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());
        }
    }

    @Test
    public void release_whenDeleteConceptBranchThrows() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());
            branchOperations.deleteThrows = true;

            final ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> MappingWorkflowService.setWorkflowStatusByAction(
                    context.getService(), context.getSpecialistUser(), MappingWorkflowAction.RELEASE,
                    context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Release", null));

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatus());
            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, context.reloadWorkflow().getWorkflowStatus());
            assertEquals(context.getSpecialistUser().getUserName(), context.reloadWorkflow().getAssignedUser());
        }
    }

    @Test
    public void forceRelease_deletesConceptBranch() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(
                context.getService(), context.getAdminUser(), MappingWorkflowAction.FORCE_RELEASE,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Force", null);

            assertEquals(1, branchOperations.deleteCount);
            assertEquals(MapWorkflowStatus.NEW, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());
        }
    }

    /**
     * Records concept-branch calls for assertions.
     */
    private static final class RecordingConceptBranchOperations implements MappingWorkflowService.ConceptBranchOperations {

        private int createCount;

        private int mergeCount;

        private int deleteCount;

        private String lastCreatedPath;

        private String lastConceptCode;

        private boolean createThrows;

        private boolean mergeThrows;

        private boolean deleteThrows;

        @Override
        public String createConceptBranch(final org.ihtsdo.refsetservice.model.MapSet mapSet, final String conceptCode) throws Exception {

            createCount++;
            lastConceptCode = conceptCode;
            if (createThrows) {
                throw new Exception("createConceptBranch failed");
            }
            lastCreatedPath = BranchService.getConceptBranchPath(mapSet, conceptCode);
            return lastCreatedPath;
        }

        @Override
        public void mergeConceptToEdit(final org.ihtsdo.refsetservice.model.MapSet mapSet, final String conceptCode) throws Exception {

            mergeCount++;
            lastConceptCode = conceptCode;
            if (mergeThrows) {
                throw new Exception("mergeConceptToEdit failed");
            }
        }

        @Override
        public void deleteConceptBranch(final org.ihtsdo.refsetservice.model.MapSet mapSet, final String conceptCode) throws Exception {

            deleteCount++;
            lastConceptCode = conceptCode;
            if (deleteThrows) {
                throw new Exception("deleteConceptBranch failed");
            }
        }
    }
}
