package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.ihtsdo.refsetservice.Application;
import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.MappingWorkflow;
import org.ihtsdo.refsetservice.model.MappingWorkflowBulkResult;
import org.ihtsdo.refsetservice.model.MappingWorkflowHistory;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;
import org.ihtsdo.refsetservice.rest.test.util.MappingWorkflowTestHandler;
import org.ihtsdo.refsetservice.rest.test.util.MappingWorkflowUnitTestUtilities;
import org.ihtsdo.refsetservice.terminologyservice.MappingWorkflowTestFixtures;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.util.ResultList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API tests for per-concept mapping workflow endpoints.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = Application.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "auth.dev.bypass=false", "mapping.workflow.concept.branch.enabled=false", "terminology.handler=MAPPING_WORKFLOW_TEST",
    "terminology.handler.MAPPING_WORKFLOW_TEST.class=org.ihtsdo.refsetservice.rest.test.util.MappingWorkflowTestHandler"
})
public class MappingWorkflowApiUnitTest extends BaseTest {

    /** The mvc. */
    @Autowired
    private MockMvc mvc;

    /** The workflow util. */
    private MappingWorkflowUnitTestUtilities workflowUtil;

    /**
     * Setup.
     */
    @BeforeEach
    public void setup() {

        MappingWorkflowTestHandler.reset();
        workflowUtil = new MappingWorkflowUnitTestUtilities(mvc, "/mapset");
    }

    /**
     * Test assign returns updated workflow.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAssignReturnsUpdatedWorkflow() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final MappingWorkflow assigned = workflowUtil.updateWorkflow(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE,
                MappingWorkflowAction.ASSIGN, "Assigned", context.getSpecialistUser());

            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, assigned.getWorkflowStatus());
            assertEquals(context.getSpecialistUser().getUserName(), assigned.getAssignedUser());

            final MappingWorkflow fetched =
                workflowUtil.getWorkflow(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, context.getSpecialistUser());
            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, fetched.getWorkflowStatus());
            assertEquals(context.getSpecialistUser().getUserName(), fetched.getAssignedUser());
        }
    }

    /**
     * Test lead ASSIGN with assignToUser assigns to the selected specialist.
     *
     * @throws Exception the exception
     */
    @Test
    public void testLeadAssignToOtherUser() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final MappingWorkflow assigned = workflowUtil.updateWorkflow(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE,
                MappingWorkflowAction.ASSIGN, "Assigning to someone else as a Lead", context.getLeadUser(),
                context.getOtherSpecialistMapUser().getId());

            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, assigned.getWorkflowStatus());
            assertEquals(context.getOtherSpecialistUser().getUserName(), assigned.getAssignedUser());
        }
    }

    /**
     * Test assign then release of a PUBLISHED mapping restores PUBLISHED.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAssignThenReleaseRestoresPublished() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.PUBLISHED);

            workflowUtil.updateWorkflow(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE,
                MappingWorkflowAction.ASSIGN, "Assigned", context.getSpecialistUser());
            final MappingWorkflow released = workflowUtil.updateWorkflow(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE,
                MappingWorkflowAction.RELEASE, "Unassigned", context.getSpecialistUser());

            assertEquals(MapWorkflowStatus.PUBLISHED, released.getWorkflowStatus());
            assertNull(released.getAssignedUser());
            assertNull(released.getPreviousWorkflowStatus());
        }
    }

    /**
     * Test invalid transition accept review from new.
     *
     * @throws Exception the exception
     */
    @Test
    public void testInvalidTransitionAcceptReviewFromNew() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final int historyBefore = context.historyCount();

            workflowUtil.updateWorkflowExpectConflict(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE,
                MappingWorkflowAction.APPROVE_FOR_PUBLICATION, "Approve", context.getLeadUser());

            final MappingWorkflow reloaded = context.reloadWorkflow();
            assertEquals(MapWorkflowStatus.NEW, reloaded.getWorkflowStatus());
            assertEquals(historyBefore, context.historyCount());
        }
    }

    /**
     * Test simple path without snowstorm.
     *
     * @throws Exception the exception
     */
    @Test
    public void testSimplePathWithoutSnowstorm() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            workflowUtil.updateWorkflow(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, MappingWorkflowAction.ASSIGN, "Assign",
                context.getSpecialistUser());
            workflowUtil.updateWorkflow(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, MappingWorkflowAction.FINISH_EDITING,
                "Finish", context.getSpecialistUser());
            workflowUtil.updateWorkflow(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE,
                MappingWorkflowAction.APPROVE_FOR_PUBLICATION, "Approve", context.getLeadUser());

            assertEquals(MapWorkflowStatus.READY_FOR_PUBLICATION, context.reloadWorkflow().getWorkflowStatus());
            final List<MappingWorkflowHistory> history =
                workflowUtil.getWorkflowHistory(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, context.getLeadUser());
            assertThat(history).hasSize(3);
            workflowUtil.validateRow(history.get(0), context.getSpecialistUser().getUserName(), MappingWorkflowAction.ASSIGN,
                MapWorkflowStatus.EDITING_IN_PROGRESS, "Assign", context.getWorkflow().getId());
            workflowUtil.validateRow(history.get(1), context.getSpecialistUser().getUserName(), MappingWorkflowAction.FINISH_EDITING,
                MapWorkflowStatus.EDITING_DONE, "Finish", context.getWorkflow().getId());
            workflowUtil.validateRow(history.get(2), context.getLeadUser().getUserName(), MappingWorkflowAction.APPROVE_FOR_PUBLICATION,
                MapWorkflowStatus.READY_FOR_PUBLICATION, "Approve", context.getWorkflow().getId());
        }
    }

    /**
     * Test double assign rejected.
     *
     * @throws Exception the exception
     */
    @Test
    public void testDoubleAssignRejected() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            workflowUtil.updateWorkflow(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, MappingWorkflowAction.ASSIGN,
                "First assign", context.getSpecialistUser());

            workflowUtil.updateWorkflowExpectConflict(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE,
                MappingWorkflowAction.ASSIGN, "Second assign", context.getOtherSpecialistUser());

            assertEquals(context.getSpecialistUser().getUserName(), context.reloadWorkflow().getAssignedUser());
        }
    }

    /**
     * Test edit allowed when assigned and mapset in edit.
     *
     * @throws Exception the exception
     */
    @Test
    public void testEditAllowedWhenAssignedAndMapsetInEdit() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final Mapping mapping = new Mapping();
            mapping.setCode(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE);
            mapping.setName("Updated mapping");

            final List<Mapping> updated =
                workflowUtil.updateMappings(context.getMapSet().getId(), Collections.singletonList(mapping), context.getSpecialistUser());

            assertThat(MappingWorkflowTestHandler.wasUpdateCalled()).isTrue();
            assertEquals("Updated mapping", updated.get(0).getName());
        }
    }

    /**
     * Test lead may save mapping data while assigned for review; phase stays REVIEW_IN_PROGRESS.
     *
     * @throws Exception the exception
     */
    @Test
    public void testEditAllowedWhenLeadAssignedForReview() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            context.setWorkflowAssignedTo(context.getLeadUser().getUserName(), MapWorkflowStatus.REVIEW_IN_PROGRESS);

            final Mapping mapping = new Mapping();
            mapping.setCode(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE);
            mapping.setName("Lead review edit");

            final List<Mapping> updated =
                workflowUtil.updateMappings(context.getMapSet().getId(), Collections.singletonList(mapping), context.getLeadUser());

            assertThat(MappingWorkflowTestHandler.wasUpdateCalled()).isTrue();
            assertEquals("Lead review edit", updated.get(0).getName());
            assertEquals(MapWorkflowStatus.REVIEW_IN_PROGRESS, context.reloadWorkflow().getWorkflowStatus());
            assertEquals(context.getLeadUser().getUserName(), context.reloadWorkflow().getAssignedUser());
        }
    }

    /**
     * Test specialist cannot save mapping data assigned to a lead for review.
     *
     * @throws Exception the exception
     */
    @Test
    public void testEditBlockedWhenSpecialistDuringLeadReview() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            context.setWorkflowAssignedTo(context.getLeadUser().getUserName(), MapWorkflowStatus.REVIEW_IN_PROGRESS);

            final Mapping mapping = new Mapping();
            mapping.setCode(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE);
            mapping.setName("Specialist review edit");

            workflowUtil.updateMappingsExpectForbidden(context.getMapSet().getId(), Collections.singletonList(mapping), context.getSpecialistUser());

            assertEquals(MapWorkflowStatus.REVIEW_IN_PROGRESS, context.reloadWorkflow().getWorkflowStatus());
            assertEquals(context.getLeadUser().getUserName(), context.reloadWorkflow().getAssignedUser());
            assertThat(MappingWorkflowTestHandler.wasUpdateCalled()).isFalse();
        }
    }

    /**
     * Test edit blocked when not assigned
     *
     * @throws Exception the exception
     */
    @Test
    public void testEditBlockedWhenNotAssigned() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final Mapping mapping = new Mapping();
            mapping.setCode(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE);
            mapping.setName("Should not save");

            workflowUtil.updateMappingsExpectConflict(context.getMapSet().getId(), Collections.singletonList(mapping), context.getSpecialistUser());

            assertThat(MappingWorkflowTestHandler.wasUpdateCalled()).isFalse();
        }
    }

    /**
     * Test edit blocked when wrong user.
     *
     * @throws Exception the exception
     */
    @Test
    public void testEditBlockedWhenWrongUser() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final Mapping mapping = new Mapping();
            mapping.setCode(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE);
            mapping.setName("Wrong user edit");

            workflowUtil.updateMappingsExpectForbidden(context.getMapSet().getId(), Collections.singletonList(mapping), context.getOtherSpecialistUser());

            assertEquals(context.getSpecialistUser().getUserName(), context.reloadWorkflow().getAssignedUser());
            assertThat(MappingWorkflowTestHandler.wasUpdateCalled()).isFalse();
        }
    }

    /**
     * Test edit blocked when mapset not in edit.
     *
     * @throws Exception the exception
     */
    @Test
    public void testEditBlockedWhenMapsetNotInEdit() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReadyForEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final Mapping mapping = new Mapping();
            mapping.setCode(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE);
            mapping.setName("Blocked edit");

            workflowUtil.updateMappingsExpectConflict(context.getMapSet().getId(), Collections.singletonList(mapping), context.getSpecialistUser());

            assertEquals(context.getSpecialistUser().getUserName(), context.reloadWorkflow().getAssignedUser());
            assertThat(MappingWorkflowTestHandler.wasUpdateCalled()).isFalse();
        }
    }

    /**
     * Test edit blocked when phase edit done.
     *
     * @throws Exception the exception
     */
    @Test
    public void testEditBlockedWhenPhaseEditDone() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.EDITING_DONE);

            final Mapping mapping = new Mapping();
            mapping.setCode(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE);
            mapping.setName("Blocked edit");

            workflowUtil.updateMappingsExpectConflict(context.getMapSet().getId(), Collections.singletonList(mapping), context.getSpecialistUser());

            assertEquals(MapWorkflowStatus.EDITING_DONE, context.reloadWorkflow().getWorkflowStatus());
            assertNull(context.reloadWorkflow().getAssignedUser());
            assertThat(MappingWorkflowTestHandler.wasUpdateCalled()).isFalse();
        }
    }

    /**
     * Test get assigned workflows for current user.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetAssignedWorkflowsForCurrentUser() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            workflowUtil.updateWorkflow(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, MappingWorkflowAction.ASSIGN, "Assigned",
                context.getSpecialistUser());

            final ResultList<MappingWorkflow> results =
                workflowUtil.getAssignedWorkflows(context.getSpecialistUser(), "limit=25&offset=0&sort=assignedAt&sortAscending=false");
            assertEquals(1, results.getTotal());
            assertEquals(25, results.getLimit());
            assertEquals(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, results.getItems().get(0).getSourceConceptCode());
            assertEquals(context.getSpecialistUser().getUserName(), results.getItems().get(0).getAssignedUser());

            final ResultList<MappingWorkflow> filtered = workflowUtil.getAssignedWorkflows(context.getSpecialistUser(),
                "mapSetId=" + context.getMapSet().getId() + "&workflowStatus=EDITING_IN_PROGRESS");
            assertEquals(1, filtered.getTotal());

            final ResultList<MappingWorkflow> otherUser = workflowUtil.getAssignedWorkflows(context.getOtherSpecialistUser(), null);
            assertEquals(0, otherUser.getTotal());
        }
    }

    /**
     * Test get assigned workflows rejects limit over 1000.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetAssignedWorkflowsRejectsLimitOver1000() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            workflowUtil.getAssignedWorkflowsExpectBadRequest(context.getSpecialistUser(), "limit=1001");
        }
    }

    /**
     * Test get recently modified workflows for current user.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetRecentlyModifiedWorkflowsForCurrentUser() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            workflowUtil.updateWorkflow(context.getMapSet().getId(), MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, MappingWorkflowAction.ASSIGN, "Assigned",
                context.getSpecialistUser());

            final ResultList<MappingWorkflow> results =
                workflowUtil.getRecentlyModifiedWorkflows(context.getSpecialistUser(), "limit=10&sort=modified&sortAscending=false");
            assertEquals(1, results.getTotal());
            assertEquals(10, results.getLimit());
            assertEquals(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, results.getItems().get(0).getSourceConceptCode());
            assertEquals(context.getSpecialistUser().getUserName(), results.getItems().get(0).getModifiedBy());

            final ResultList<MappingWorkflow> filtered =
                workflowUtil.getRecentlyModifiedWorkflows(context.getSpecialistUser(), "mapSetId=" + context.getMapSet().getId());
            assertEquals(1, filtered.getTotal());

            final ResultList<MappingWorkflow> otherUser = workflowUtil.getRecentlyModifiedWorkflows(context.getOtherSpecialistUser(), null);
            assertEquals(0, otherUser.getTotal());
        }
    }

    /**
     * Test get recently modified workflows rejects limit over 100.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetRecentlyModifiedWorkflowsRejectsLimitOver100() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            workflowUtil.getRecentlyModifiedWorkflowsExpectBadRequest(context.getSpecialistUser(), "limit=101");
        }
    }

    /**
     * Test bulk get mapping workflow status.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetWorkflowStatusBulk() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final MappingWorkflow second = context.addWorkflowForConcept("444555666", MapWorkflowStatus.NEW);

            final List<MappingWorkflow> workflows = workflowUtil.getWorkflowBulk(context.getMapSet().getId(),
                Arrays.asList(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, second.getSourceConceptCode()), context.getSpecialistUser());

            assertEquals(2, workflows.size());
            assertEquals(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, workflows.get(0).getSourceConceptCode());
            assertEquals(second.getSourceConceptCode(), workflows.get(1).getSourceConceptCode());
            assertEquals(MapWorkflowStatus.NEW, workflows.get(0).getWorkflowStatus());
            assertEquals(MapWorkflowStatus.NEW, workflows.get(1).getWorkflowStatus());
        }
    }

    /**
     * Bulk GET returns a transient PUBLISHED placeholder for concepts in the mapset with no workflow row.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetWorkflowStatusBulkReturnsTransientPublished() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final List<MappingWorkflow> workflows = workflowUtil.getWorkflowBulk(context.getMapSet().getId(),
                Arrays.asList(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, "998877665"), context.getSpecialistUser());

            assertEquals(2, workflows.size());
            assertEquals(MapWorkflowStatus.NEW, workflows.get(0).getWorkflowStatus());
            assertEquals(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, workflows.get(0).getSourceConceptCode());
            assertEquals(MapWorkflowStatus.PUBLISHED, workflows.get(1).getWorkflowStatus());
            assertEquals("998877665", workflows.get(1).getSourceConceptCode());
            assertNull(workflows.get(1).getId());
        }
    }

    /**
     * Bulk GET returns 404 when a concept is not in the mapset and has no workflow row.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetWorkflowStatusBulkNotInMapSetReturns404() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            MappingWorkflowTestHandler.markConceptMissingFromMapSet("998877665");
            workflowUtil.getWorkflowBulkExpectNotFound(context.getMapSet().getId(),
                Arrays.asList(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, "998877665"), context.getSpecialistUser());
        }
    }

    /**
     * Test bulk accept review with partial failure.
     *
     * @throws Exception the exception
     */
    @Test
    public void testBulkAcceptReviewPartialFailure() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReviewProjectInEdit()) {
            context.setWorkflowAssignedTo(context.getLeadUser().getUserName(), MapWorkflowStatus.REVIEW_IN_PROGRESS);
            final MappingWorkflow second = context.addWorkflowForConcept("444555666", MapWorkflowStatus.REVIEW_NEEDED);

            final MappingWorkflowBulkResult result = workflowUtil.updateWorkflowBulk(context.getMapSet().getId(), MappingWorkflowAction.ACCEPT_REVIEW,
                "BulkAccept", Arrays.asList(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, second.getSourceConceptCode()), context.getLeadUser());

            assertEquals(1, result.getSuccessCount());
            assertEquals(1, result.getFailureCount());
            assertEquals(MapWorkflowStatus.READY_FOR_PUBLICATION, result.getItems().get(0).getWorkflow().getWorkflowStatus());
            assertThat(result.getItems().get(1).isSuccess()).isFalse();
        }
    }

    /**
     * Unauthenticated GET workflow status returns 401 with the RestException payload, not a 500 error page.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetWorkflowStatusRequiresAuthentication() throws Exception {

        mvc.perform(get("/mapset/447562003/mappings/10007009/workflowStatus").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401)).andExpect(jsonPath("$.error").value("Unauthorized"))
            .andExpect(jsonPath("$.message").value("Unauthorized"));
    }
}
