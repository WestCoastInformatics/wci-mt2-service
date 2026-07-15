package org.ihtsdo.refsetservice.rest.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.MappingWorkflow;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;
import org.ihtsdo.refsetservice.rest.test.util.MappingWorkflowTestHandler;
import org.ihtsdo.refsetservice.rest.test.util.MappingWorkflowUnitTestUtilities;
import org.ihtsdo.refsetservice.terminologyservice.MappingWorkflowService;
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
 * API tests for lazy initialization of per-concept mapping workflow rows.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "auth.dev.bypass=false",
    "mapping.workflow.concept.branch.enabled=false",
    "terminology.handler=MAPPING_WORKFLOW_TEST",
    "terminology.handler.MAPPING_WORKFLOW_TEST.class=org.ihtsdo.refsetservice.rest.test.util.MappingWorkflowTestHandler"
})
public class MappingWorkflowLazyInitTest extends BaseTest {

    private static final String LAZY_CONCEPT_CODE = "998877665";

    @Autowired
    private MockMvc mvc;

    private MappingWorkflowUnitTestUtilities workflowUtil;

    @BeforeEach
    public void setup() {

        MappingWorkflowTestHandler.reset();
        workflowUtil = new MappingWorkflowUnitTestUtilities(mvc, "/mapset");
    }

    @Test
    public void firstAccessCreatesRow() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.removePrimaryWorkflow();
            assertEquals(0, MappingWorkflowService.countWorkflowRows(
                context.getService(), context.getMapSet(), LAZY_CONCEPT_CODE, 1));

            final MappingWorkflow created = workflowUtil.getWorkflow(
                context.getMapSet().getId(), LAZY_CONCEPT_CODE, context.getSpecialistUser());

            assertEquals(MapWorkflowStatus.NEW, created.getWorkflowStatus());
            assertNull(created.getAssignedUser());
            assertEquals(1, MappingWorkflowService.countWorkflowRows(
                context.getService(), context.getMapSet(), LAZY_CONCEPT_CODE, 1));
        }
    }

    @Test
    public void secondAccessNoDuplicate() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.removePrimaryWorkflow();

            workflowUtil.getWorkflow(context.getMapSet().getId(), LAZY_CONCEPT_CODE, context.getSpecialistUser());
            workflowUtil.getWorkflow(context.getMapSet().getId(), LAZY_CONCEPT_CODE, context.getSpecialistUser());

            assertEquals(1, MappingWorkflowService.countWorkflowRows(
                context.getService(), context.getMapSet(), LAZY_CONCEPT_CODE, 1));
        }
    }

    @Test
    public void assignOnLazyRow() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.removePrimaryWorkflow();

            workflowUtil.getWorkflow(context.getMapSet().getId(), LAZY_CONCEPT_CODE, context.getSpecialistUser());
            final MappingWorkflow assigned = workflowUtil.updateWorkflow(
                context.getMapSet().getId(), LAZY_CONCEPT_CODE,
                MappingWorkflowAction.ASSIGN, "Assign", context.getSpecialistUser());

            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, assigned.getWorkflowStatus());
            assertEquals(context.getSpecialistUser().getUserName(), assigned.getAssignedUser());
        }
    }
}
