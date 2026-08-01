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

    /** The branch operations. */
    private final RecordingConceptBranchOperations branchOperations = new RecordingConceptBranchOperations();

    /**
     * Setup.
     */
    @BeforeEach
    public void setup() {

        PropertyUtility.setProperty("mapping.workflow.concept.branch.enabled", "true");
        MappingWorkflowService.setConceptBranchOperationsForTests(branchOperations);
    }

    /**
     * Teardown.
     */
    @AfterEach
    public void teardown() {

        PropertyUtility.setProperty("mapping.workflow.concept.branch.enabled", "false");
        MappingWorkflowService.setConceptBranchOperationsForTests(null);
    }

    /**
     * Assign creates concept branch.
     *
     * @throws Exception the exception
     */
    @Test
    public void assignCreatesConceptBranch() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final String expectedPath = BranchService.getConceptBranchPath(context.getMapSet(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE);

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(),
                MappingWorkflowAction.ASSIGN, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Assigned", null);

            assertEquals(1, branchOperations.createCount);
            assertEquals(0, branchOperations.mergeCount);
            assertEquals(0, branchOperations.deleteCount);
            assertEquals(expectedPath, branchOperations.lastCreatedPath);
            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, updated.getWorkflowStatus());
            assertEquals(context.getSpecialistUser().getUserName(), updated.getAssignedUser());
            assertNotNull(updated.getAssignedAt());
        }
    }

    /**
     * Assign when create concept branch throws.
     *
     * @throws Exception the exception
     */
    @Test
    public void assignWhenCreateConceptBranchThrows() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            branchOperations.createThrows = true;
            final int historyBefore = context.historyCount();

            assertThrows(Exception.class, () -> MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(),
                MappingWorkflowAction.ASSIGN, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Assigned", null));

            final MappingWorkflow reloaded = context.reloadWorkflow();
            assertEquals(MapWorkflowStatus.NEW, reloaded.getWorkflowStatus());
            assertNull(reloaded.getAssignedUser());
            assertNull(reloaded.getAssignedAt());
            assertNull(reloaded.getLeaseExpiresAt());
            assertEquals(historyBefore, context.historyCount());
        }
    }

    /**
     * Finish editing merges concept branch.
     *
     * @throws Exception the exception
     */
    @Test
    public void finishEditingMergesConceptBranch() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(),
                MappingWorkflowAction.FINISH_EDITING, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Done", null);

            assertEquals(1, branchOperations.mergeCount);
            assertEquals(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, branchOperations.lastConceptCode);
            assertEquals(MapWorkflowStatus.EDITING_DONE, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());
        }
    }

    /**
     * Finish editing when merge concept branch throws.
     *
     * @throws Exception the exception
     */
    @Test
    public void finishEditingWhenMergeConceptBranchThrows() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());
            branchOperations.mergeThrows = true;
            final int historyBefore = context.historyCount();

            assertThrows(Exception.class, () -> MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(),
                MappingWorkflowAction.FINISH_EDITING, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Done", null));

            final MappingWorkflow reloaded = context.reloadWorkflow();
            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, reloaded.getWorkflowStatus());
            assertEquals(context.getSpecialistUser().getUserName(), reloaded.getAssignedUser());
            assertEquals(historyBefore, context.historyCount());
        }
    }

    /**
     * Release deletes concept branch.
     *
     * @throws Exception the exception
     */
    @Test
    public void releaseDeletesConceptBranch() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(),
                MappingWorkflowAction.RELEASE, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Release", null);

            assertEquals(1, branchOperations.deleteCount);
            assertEquals(MapWorkflowStatus.NEW, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());
        }
    }

    /**
     * Release when delete concept branch throws.
     *
     * @throws Exception the exception
     */
    @Test
    public void releaseWhenDeleteConceptBranchThrows() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());
            branchOperations.deleteThrows = true;

            final ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(), MappingWorkflowAction.RELEASE,
                    context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Release", null));

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, exception.getStatus());
            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, context.reloadWorkflow().getWorkflowStatus());
            assertEquals(context.getSpecialistUser().getUserName(), context.reloadWorkflow().getAssignedUser());
        }
    }

    /**
     * Force release deletes concept branch.
     *
     * @throws Exception the exception
     */
    @Test
    public void forceReleaseDeletesConceptBranch() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getAdminUser(),
                MappingWorkflowAction.FORCE_RELEASE, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Force", null);

            assertEquals(1, branchOperations.deleteCount);
            assertEquals(MapWorkflowStatus.NEW, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());
        }
    }

    /**
     * Records concept-branch calls for assertions.
     */
    private static final class RecordingConceptBranchOperations implements MappingWorkflowService.ConceptBranchOperations {

        /** The create count. */
        private int createCount;

        /** The merge count. */
        private int mergeCount;

        /** The delete count. */
        private int deleteCount;

        /** The last created path. */
        private String lastCreatedPath;

        /** The last concept code. */
        private String lastConceptCode;

        /** The create throws. */
        private boolean createThrows;

        /** The merge throws. */
        private boolean mergeThrows;

        /** The delete throws. */
        private boolean deleteThrows;

        /**
         * Creates the concept branch.
         *
         * @param mapSet the map set
         * @param conceptCode the concept code
         * @return the string
         * @throws Exception the exception
         */
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

        /**
         * Merge concept to edit.
         *
         * @param mapSet the map set
         * @param conceptCode the concept code
         * @throws Exception the exception
         */
        @Override
        public void mergeConceptToEdit(final org.ihtsdo.refsetservice.model.MapSet mapSet, final String conceptCode) throws Exception {

            mergeCount++;
            lastConceptCode = conceptCode;
            if (mergeThrows) {
                throw new Exception("mergeConceptToEdit failed");
            }
        }

        /**
         * Delete concept branch.
         *
         * @param mapSet the map set
         * @param conceptCode the concept code
         * @throws Exception the exception
         */
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
