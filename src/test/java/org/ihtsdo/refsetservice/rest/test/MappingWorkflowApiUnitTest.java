package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Collections;
import java.util.List;

import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.MappingWorkflow;
import org.ihtsdo.refsetservice.model.MappingWorkflowHistory;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;
import org.ihtsdo.refsetservice.rest.test.util.MappingWorkflowTestHandler;
import org.ihtsdo.refsetservice.rest.test.util.MappingWorkflowUnitTestUtilities;
import org.ihtsdo.refsetservice.terminologyservice.MappingWorkflowTestFixtures;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API tests for per-concept mapping workflow endpoints.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "auth.dev.bypass=false",
    "terminology.handler=MAPPING_WORKFLOW_TEST",
    "terminology.handler.MAPPING_WORKFLOW_TEST.class=org.ihtsdo.refsetservice.rest.test.util.MappingWorkflowTestHandler"
})
public class MappingWorkflowApiUnitTest extends BaseTest {

    @Autowired
    private MockMvc mvc;

    private MappingWorkflowUnitTestUtilities workflowUtil;

    @BeforeEach
    public void setup() {

        MappingWorkflowTestHandler.reset();
        workflowUtil = new MappingWorkflowUnitTestUtilities(mvc, "/mapset");
    }

    @Test
    public void testAssignReturnsUpdatedWorkflow() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final MappingWorkflow assigned = workflowUtil.updateWorkflow(
                context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE,
                MappingWorkflowAction.ASSIGN, "Assigned", context.getSpecialistUser());

            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, assigned.getWorkflowStatus());
            assertEquals(context.getSpecialistUser().getUserName(), assigned.getAssignedUser());

            final MappingWorkflow fetched = workflowUtil.getWorkflow(
                context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, context.getSpecialistUser());
            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, fetched.getWorkflowStatus());
            assertEquals(context.getSpecialistUser().getUserName(), fetched.getAssignedUser());
        }
    }

    @Test
    public void testInvalidTransition_acceptReviewFromNew() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final int historyBefore = context.historyCount();

            workflowUtil.updateWorkflowExpectUnauthorized(
                context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE,
                MappingWorkflowAction.APPROVE_FOR_PUBLICATION, "Approve", context.getLeadUser());

            final MappingWorkflow reloaded = context.reloadWorkflow();
            assertEquals(MapWorkflowStatus.NEW, reloaded.getWorkflowStatus());
            assertEquals(historyBefore, context.historyCount());
        }
    }

    @Test
    public void testSimplePathWithoutSnowstorm() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            workflowUtil.updateWorkflow(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE,
                MappingWorkflowAction.ASSIGN, "Assign", context.getSpecialistUser());
            workflowUtil.updateWorkflow(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE,
                MappingWorkflowAction.FINISH_EDITING, "Finish", context.getSpecialistUser());
            workflowUtil.updateWorkflow(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE,
                MappingWorkflowAction.APPROVE_FOR_PUBLICATION, "Approve", context.getLeadUser());

            assertEquals(MapWorkflowStatus.READY_FOR_PUBLICATION, context.reloadWorkflow().getWorkflowStatus());
            final List<MappingWorkflowHistory> history = workflowUtil.getWorkflowHistory(
                context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, context.getLeadUser());
            assertThat(history).hasSize(3);
            workflowUtil.validateRow(history.get(0), context.getSpecialistUser().getUserName(),
                MappingWorkflowAction.ASSIGN, MapWorkflowStatus.EDITING_IN_PROGRESS, "Assign", context.getWorkflow().getId());
            workflowUtil.validateRow(history.get(1), context.getSpecialistUser().getUserName(),
                MappingWorkflowAction.FINISH_EDITING, MapWorkflowStatus.EDITING_DONE, "Finish", context.getWorkflow().getId());
            workflowUtil.validateRow(history.get(2), context.getLeadUser().getUserName(),
                MappingWorkflowAction.APPROVE_FOR_PUBLICATION, MapWorkflowStatus.READY_FOR_PUBLICATION, "Approve", context.getWorkflow().getId());
        }
    }

    @Test
    public void testDoubleAssignRejected() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            workflowUtil.updateWorkflow(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE,
                MappingWorkflowAction.ASSIGN, "First assign", context.getSpecialistUser());

            workflowUtil.updateWorkflowExpectUnauthorized(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE,
                MappingWorkflowAction.ASSIGN, "Second assign", context.getOtherSpecialistUser());

            assertEquals(context.getSpecialistUser().getUserName(), context.reloadWorkflow().getAssignedUser());
        }
    }

    @Test
    public void testEditAllowedWhenAssignedAndMapsetInEdit() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final Mapping mapping = new Mapping();
            mapping.setCode(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE);
            mapping.setName("Updated mapping");

            final List<Mapping> updated = workflowUtil.updateMappings(
                context.getMapSet().getId(), Collections.singletonList(mapping), context.getSpecialistUser());

            assertThat(MappingWorkflowTestHandler.wasUpdateCalled()).isTrue();
            assertEquals("Updated mapping", updated.get(0).getName());
        }
    }

    @Test
    public void testEditBlockedWhenNotAssigned() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final Mapping mapping = new Mapping();
            mapping.setCode(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE);
            mapping.setName("Should not save");

            workflowUtil.updateMappingsExpectUnauthorized(
                context.getMapSet().getId(), Collections.singletonList(mapping), context.getSpecialistUser());

            assertThat(MappingWorkflowTestHandler.wasUpdateCalled()).isFalse();
        }
    }

    @Test
    public void testEditBlockedWhenWrongUser() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final Mapping mapping = new Mapping();
            mapping.setCode(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE);
            mapping.setName("Wrong user edit");

            workflowUtil.updateMappingsExpectUnauthorized(
                context.getMapSet().getId(), Collections.singletonList(mapping), context.getOtherSpecialistUser());

            assertEquals(context.getSpecialistUser().getUserName(), context.reloadWorkflow().getAssignedUser());
            assertThat(MappingWorkflowTestHandler.wasUpdateCalled()).isFalse();
        }
    }

    @Test
    public void testEditBlockedWhenMapsetNotInEdit() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReadyForEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final Mapping mapping = new Mapping();
            mapping.setCode(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE);
            mapping.setName("Blocked edit");

            workflowUtil.updateMappingsExpectUnauthorized(
                context.getMapSet().getId(), Collections.singletonList(mapping), context.getSpecialistUser());

            assertEquals(context.getSpecialistUser().getUserName(), context.reloadWorkflow().getAssignedUser());
            assertThat(MappingWorkflowTestHandler.wasUpdateCalled()).isFalse();
        }
    }

    @Test
    public void testEditBlockedWhenPhaseEditDone() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.EDITING_DONE);

            final Mapping mapping = new Mapping();
            mapping.setCode(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE);
            mapping.setName("Blocked edit");

            workflowUtil.updateMappingsExpectUnauthorized(
                context.getMapSet().getId(), Collections.singletonList(mapping), context.getSpecialistUser());

            assertEquals(MapWorkflowStatus.EDITING_DONE, context.reloadWorkflow().getWorkflowStatus());
            assertNull(context.reloadWorkflow().getAssignedUser());
            assertThat(MappingWorkflowTestHandler.wasUpdateCalled()).isFalse();
        }
    }
}
