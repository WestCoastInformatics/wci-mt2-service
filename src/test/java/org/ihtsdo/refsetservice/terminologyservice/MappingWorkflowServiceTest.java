package org.ihtsdo.refsetservice.terminologyservice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.ihtsdo.refsetservice.Application;
import org.ihtsdo.refsetservice.handler.EntraMapBootstrap;
import org.ihtsdo.refsetservice.helpers.MapUserRole;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapUser;
import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.Mapping;
import org.ihtsdo.refsetservice.model.MappingWorkflow;
import org.ihtsdo.refsetservice.model.MappingWorkflowHistory;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.ihtsdo.refsetservice.util.SearchParameters;
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
@SpringBootTest(classes = Application.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "auth.dev.bypass=false", "mapping.workflow.concept.branch.enabled=false"
})
public class MappingWorkflowServiceTest {

    /**
     * Disable concept branch side effects.
     */
    @BeforeEach
    public void disableConceptBranchSideEffects() {

        PropertyUtility.setProperty("mapping.workflow.concept.branch.enabled", "false");
    }

    /**
     * Assign from new by specialist.
     *
     * @throws Exception the exception
     */
    @Test
    public void assignFromNewBySpecialist() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final TerminologyService service = context.getService();
            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(service, context.getSpecialistUser(), MappingWorkflowAction.ASSIGN,
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

    /**
     * Assign when already assigned.
     *
     * @throws Exception the exception
     */
    @Test
    public void assignWhenAlreadyAssigned() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getOtherSpecialistUser().getUserName());
            final int historyBefore = context.historyCount();

            assertThrows(ResponseStatusException.class,
                () -> MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(), MappingWorkflowAction.ASSIGN,
                    context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Assign again", null));

            final MappingWorkflow reloaded = context.reloadWorkflow();
            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, reloaded.getWorkflowStatus());
            assertEquals(context.getOtherSpecialistUser().getUserName(), reloaded.getAssignedUser());
            assertEquals(historyBefore, context.historyCount());
        }
    }

    /**
     * Assign by viewer.
     *
     * @throws Exception the exception
     */
    @Test
    public void assignByViewer() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getViewerUser(),
                MappingWorkflowAction.ASSIGN, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Viewer assign", null));

            final MappingWorkflow reloaded = context.reloadWorkflow();
            assertEquals(MapWorkflowStatus.NEW, reloaded.getWorkflowStatus());
            assertNull(reloaded.getAssignedUser());
            assertEquals(0, context.historyCount());
        }
    }

    /**
     * Assign from new by Entra specialist without join-table membership.
     *
     * @throws Exception the exception
     */
    @Test
    public void assignFromNewByEntraSpecialistWithoutMembership() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.getViewerUser().getRoles().add(EntraMapBootstrap.ROLE_SPEC);
            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getViewerUser(),
                MappingWorkflowAction.ASSIGN, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Assigned", null);

            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, updated.getWorkflowStatus());
            assertEquals(context.getViewerUser().getUserName(), updated.getAssignedUser());
        }
    }

    /**
     * Assign when mapset not in edit.
     *
     * @throws Exception the exception
     */
    @Test
    public void assignWhenMapsetNotInEdit() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createReadyForEdit()) {
            assertThrows(ResponseStatusException.class,
                () -> MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(), MappingWorkflowAction.ASSIGN,
                    context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Assign", null));

            final MappingWorkflow reloaded = context.reloadWorkflow();
            assertEquals(MapWorkflowStatus.NEW, reloaded.getWorkflowStatus());
        }
    }

    /**
     * Release by assignee.
     *
     * @throws Exception the exception
     */
    @Test
    public void releaseByAssignee() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(),
                MappingWorkflowAction.RELEASE, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Release", null);

            assertEquals(MapWorkflowStatus.NEW, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());
            assertNull(updated.getAssignedAt());
            assertNull(updated.getLeaseExpiresAt());
            assertEquals(MappingWorkflowAction.RELEASE, context.latestHistory().getWorkflowAction());
        }
    }

    /**
     * Release by non holder.
     *
     * @throws Exception the exception
     */
    @Test
    public void releaseByNonHolder() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getOtherSpecialistUser().getUserName());
            final int historyBefore = context.historyCount();

            assertThrows(ResponseStatusException.class,
                () -> MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(), MappingWorkflowAction.RELEASE,
                    context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Release", null));

            final MappingWorkflow reloaded = context.reloadWorkflow();
            assertEquals(context.getOtherSpecialistUser().getUserName(), reloaded.getAssignedUser());
            assertEquals(historyBefore, context.historyCount());
        }
    }

    /**
     * Release from new.
     *
     * @throws Exception the exception
     */
    @Test
    public void releaseFromNew() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            assertThrows(ResponseStatusException.class,
                () -> MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(), MappingWorkflowAction.RELEASE,
                    context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Release", null));

            assertEquals(MapWorkflowStatus.NEW, context.reloadWorkflow().getWorkflowStatus());
        }
    }

    /**
     * Finish editing by assignee.
     *
     * @throws Exception the exception
     */
    @Test
    public void finishEditingByAssignee() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(),
                MappingWorkflowAction.FINISH_EDITING, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Done", null);

            assertEquals(MapWorkflowStatus.EDITING_DONE, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());
            assertEquals(MappingWorkflowAction.FINISH_EDITING, context.latestHistory().getWorkflowAction());
        }
    }

    /**
     * Finish editing from new.
     *
     * @throws Exception the exception
     */
    @Test
    public void finishEditingFromNew() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(), MappingWorkflowAction.FINISH_EDITING,
                    context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Done", null));
            assertEquals(HttpStatus.CONFLICT, ex.getStatus());

            assertEquals(MapWorkflowStatus.NEW, context.reloadWorkflow().getWorkflowStatus());
        }
    }

    /**
     * Finish editing by non holder.
     *
     * @throws Exception the exception
     */
    @Test
    public void finishEditingByNonHolder() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getOtherSpecialistUser().getUserName());

            final ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(), MappingWorkflowAction.FINISH_EDITING,
                    context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Done", null));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());

            final MappingWorkflow reloaded = context.reloadWorkflow();
            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, reloaded.getWorkflowStatus());
            assertEquals(context.getOtherSpecialistUser().getUserName(), reloaded.getAssignedUser());
        }
    }

    /**
     * Approve by lead.
     *
     * @throws Exception the exception
     */
    @Test
    public void approveByLead() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.EDITING_DONE);

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getLeadUser(),
                MappingWorkflowAction.APPROVE_FOR_PUBLICATION, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Approved", null);

            assertEquals(MapWorkflowStatus.READY_FOR_PUBLICATION, updated.getWorkflowStatus());
            assertEquals(MappingWorkflowAction.APPROVE_FOR_PUBLICATION, context.latestHistory().getWorkflowAction());
        }
    }

    /**
     * Approve by specialist.
     *
     * @throws Exception the exception
     */
    @Test
    public void approveBySpecialist() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowStatus(MapWorkflowStatus.EDITING_DONE);

            assertThrows(ResponseStatusException.class,
                () -> MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(),
                    MappingWorkflowAction.APPROVE_FOR_PUBLICATION, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Approve", null));

            assertEquals(MapWorkflowStatus.EDITING_DONE, context.reloadWorkflow().getWorkflowStatus());
        }
    }

    /**
     * Force release by admin.
     *
     * @throws Exception the exception
     */
    @Test
    public void forceReleaseByAdmin() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getOtherSpecialistUser().getUserName());

            final MappingWorkflow updated = MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getAdminUser(),
                MappingWorkflowAction.FORCE_RELEASE, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Force release", null);

            assertEquals(MapWorkflowStatus.NEW, updated.getWorkflowStatus());
            assertNull(updated.getAssignedUser());
            assertEquals(MappingWorkflowAction.FORCE_RELEASE, context.latestHistory().getWorkflowAction());
        }
    }

    /**
     * Force release by specialist.
     *
     * @throws Exception the exception
     */
    @Test
    public void forceReleaseBySpecialist() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            assertThrows(ResponseStatusException.class,
                () -> MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getSpecialistUser(), MappingWorkflowAction.FORCE_RELEASE,
                    context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Force", null));
        }
    }

    /**
     * Reassign by lead.
     *
     * @throws Exception the exception
     */
    @Test
    public void reassignByLead() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            final MappingWorkflow updated =
                MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getLeadUser(), MappingWorkflowAction.REASSIGN,
                    context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Reassign", context.getOtherSpecialistUser().getUserName());

            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, updated.getWorkflowStatus());
            assertEquals(context.getOtherSpecialistUser().getUserName(), updated.getAssignedUser());
            assertNotNull(updated.getAssignedAt());
            assertEquals(MappingWorkflowAction.REASSIGN, context.latestHistory().getWorkflowAction());
        }
    }

    /**
     * Reassign by non admin.
     *
     * @throws Exception the exception
     */
    @Test
    public void reassignByNonAdmin() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName());

            assertThrows(ResponseStatusException.class,
                () -> MappingWorkflowService.setWorkflowStatusByAction(context.getService(), context.getOtherSpecialistUser(), MappingWorkflowAction.REASSIGN,
                    context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Reassign", context.getOtherSpecialistUser().getUserName()));

            assertEquals(context.getSpecialistUser().getUserName(), context.reloadWorkflow().getAssignedUser());
        }
    }

    /**
     * Allowed actions new specialist.
     *
     * @throws Exception the exception
     */
    @Test
    public void allowedActionsNewSpecialist() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final Set<MappingWorkflowAction> allowed =
                MappingWorkflowService.getAllowedActions(context.getSpecialistUser(), context.getWorkflow(), context.getMapSet(), context.getMapProject());

            assertEquals(Set.of(MappingWorkflowAction.ASSIGN), allowed);
            assertTrue(!allowed.contains(MappingWorkflowAction.FINISH_EDITING));
            assertTrue(!allowed.contains(MappingWorkflowAction.APPROVE_FOR_PUBLICATION));
        }
    }

    /**
     * Allowed actions in progress non holder.
     *
     * @throws Exception the exception
     */
    @Test
    public void allowedActionsInProgressNonHolder() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getOtherSpecialistUser().getUserName());

            final Set<MappingWorkflowAction> allowed =
                MappingWorkflowService.getAllowedActions(context.getSpecialistUser(), context.getWorkflow(), context.getMapSet(), context.getMapProject());

            assertTrue(allowed.isEmpty());
        }
    }

    /**
     * Test simple path end to end.
     *
     * @throws Exception the exception
     */
    @Test
    public void testSimplePathEndToEnd() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final TerminologyService service = context.getService();

            MappingWorkflowService.setWorkflowStatusByAction(service, context.getSpecialistUser(), MappingWorkflowAction.ASSIGN, context.getWorkflow(),
                context.getMapSet(), context.getMapProject(), "Assign", null);
            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, context.reloadWorkflow().getWorkflowStatus());

            MappingWorkflowService.setWorkflowStatusByAction(service, context.getSpecialistUser(), MappingWorkflowAction.FINISH_EDITING, context.getWorkflow(),
                context.getMapSet(), context.getMapProject(), "Finish", null);
            final MappingWorkflow afterFinish = context.reloadWorkflow();
            assertEquals(MapWorkflowStatus.EDITING_DONE, afterFinish.getWorkflowStatus());
            assertNull(afterFinish.getAssignedUser());

            MappingWorkflowService.setWorkflowStatusByAction(service, context.getLeadUser(), MappingWorkflowAction.APPROVE_FOR_PUBLICATION,
                context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Approve", null);
            assertEquals(MapWorkflowStatus.READY_FOR_PUBLICATION, context.reloadWorkflow().getWorkflowStatus());
            assertEquals(3, context.historyCount());

            assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.setWorkflowStatusByAction(service, context.getSpecialistUser(),
                MappingWorkflowAction.ASSIGN, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "Too late", null));
        }
    }

    /**
     * Forbidden is 403 when the user lacks a project workflow role.
     *
     * @throws Exception the exception
     */
    @Test
    public void forbiddenIs403() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final ResponseStatusException ex =
                assertThrows(ResponseStatusException.class, () -> MappingWorkflowService.setWorkflowStatusByAction(context.getService(),
                    context.getViewerUser(), MappingWorkflowAction.ASSIGN, context.getWorkflow(), context.getMapSet(), context.getMapProject(), "No", null));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        }
    }

    /**
     * Find assigned workflows returns current assignments only.
     *
     * @throws Exception the exception
     */
    @Test
    public void findAssignedWorkflowsReturnsCurrentAssignmentsOnly() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final TerminologyService service = context.getService();
            final SearchParameters searchParameters = new SearchParameters();
            searchParameters.setLimit(25);
            searchParameters.setSort("assignedAt");
            searchParameters.setSortAscending(false);

            ResultList<MappingWorkflow> empty =
                MappingWorkflowService.findAssignedWorkflows(service, context.getSpecialistUser().getUserName(), null, null, null, searchParameters);
            assertEquals(0, empty.getTotal());

            MappingWorkflowService.setWorkflowStatusByAction(service, context.getSpecialistUser(), MappingWorkflowAction.ASSIGN, context.getWorkflow(),
                context.getMapSet(), context.getMapProject(), "Assigned", null);

            final MappingWorkflow other = context.addWorkflowForConcept("999888777", MapWorkflowStatus.NEW);
            other.setAssignedUser(context.getOtherSpecialistUser().getUserName());
            other.setAssignedAt(new Date(System.currentTimeMillis() - 60000L));
            other.setWorkflowStatus(MapWorkflowStatus.EDITING_IN_PROGRESS);
            service.update(other);

            ResultList<MappingWorkflow> mine =
                MappingWorkflowService.findAssignedWorkflows(service, context.getSpecialistUser().getUserName(), null, null, null, searchParameters);
            assertEquals(1, mine.getTotal());
            assertEquals(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, mine.getItems().get(0).getSourceConceptCode());
            assertEquals(context.getSpecialistUser().getUserName(), mine.getItems().get(0).getAssignedUser());

            ResultList<MappingWorkflow> filtered = MappingWorkflowService.findAssignedWorkflows(service, context.getSpecialistUser().getUserName(),
                context.getMapProject().getId(), context.getMapSet().getId(), MapWorkflowStatus.EDITING_IN_PROGRESS, searchParameters);
            assertEquals(1, filtered.getTotal());

            MappingWorkflowService.setWorkflowStatusByAction(service, context.getSpecialistUser(), MappingWorkflowAction.FINISH_EDITING, context.getWorkflow(),
                context.getMapSet(), context.getMapProject(), "Finished", null);

            ResultList<MappingWorkflow> afterFinish =
                MappingWorkflowService.findAssignedWorkflows(service, context.getSpecialistUser().getUserName(), null, null, null, searchParameters);
            assertEquals(0, afterFinish.getTotal());
        }
    }

    /**
     * Find assigned workflows rejects limit over max.
     *
     * @throws Exception the exception
     */
    @Test
    public void findAssignedWorkflowsRejectsLimitOverMax() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final SearchParameters searchParameters = new SearchParameters();
            searchParameters.setLimit(1001);
            final ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> MappingWorkflowService
                .findAssignedWorkflows(context.getService(), context.getSpecialistUser().getUserName(), null, null, null, searchParameters));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }
    }

    /**
     * Find recently modified workflows returns rows last touched by the user.
     *
     * @throws Exception the exception
     */
    @Test
    public void findRecentlyModifiedWorkflowsReturnsRowsModifiedByUser() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final TerminologyService service = context.getService();
            final SearchParameters searchParameters = new SearchParameters();
            searchParameters.setLimit(10);
            searchParameters.setSort("modified");
            searchParameters.setSortAscending(false);

            service.setModifiedBy(context.getSpecialistUser().getUserName());
            MappingWorkflowService.setWorkflowStatusByAction(service, context.getSpecialistUser(), MappingWorkflowAction.ASSIGN, context.getWorkflow(),
                context.getMapSet(), context.getMapProject(), "Assigned", null);

            final MappingWorkflow other = context.addWorkflowForConcept("888777666", MapWorkflowStatus.NEW);
            service.setModifiedBy(context.getOtherSpecialistUser().getUserName());
            other.setWorkflowStatus(MapWorkflowStatus.EDITING_IN_PROGRESS);
            other.setAssignedUser(context.getOtherSpecialistUser().getUserName());
            other.setAssignedAt(new Date());
            service.update(other);

            final ResultList<MappingWorkflow> mine =
                MappingWorkflowService.findRecentlyModifiedWorkflows(service, context.getSpecialistUser().getUserName(), null, null, searchParameters);
            assertEquals(1, mine.getTotal());
            assertEquals(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE, mine.getItems().get(0).getSourceConceptCode());
            assertEquals(context.getSpecialistUser().getUserName(), mine.getItems().get(0).getModifiedBy());

            final ResultList<MappingWorkflow> filtered = MappingWorkflowService.findRecentlyModifiedWorkflows(service,
                context.getSpecialistUser().getUserName(), context.getMapProject().getId(), context.getMapSet().getId(), searchParameters);
            assertEquals(1, filtered.getTotal());
        }
    }

    /**
     * Find recently modified workflows rejects limit over max.
     *
     * @throws Exception the exception
     */
    @Test
    public void findRecentlyModifiedWorkflowsRejectsLimitOverMax() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            final SearchParameters searchParameters = new SearchParameters();
            searchParameters.setLimit(101);
            final ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> MappingWorkflowService
                .findRecentlyModifiedWorkflows(context.getService(), context.getSpecialistUser().getUserName(), null, null, searchParameters));
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        }
    }

    /**
     * Attach workflows hydrates existing rows only and does not create missing ones.
     *
     * @throws Exception the exception
     */
    @Test
    public void attachWorkflowsHydratesExistingOnly() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            context.setWorkflowAssignedTo(context.getSpecialistUser().getUserName(), MapWorkflowStatus.EDITING_IN_PROGRESS);

            final Mapping withWorkflow = new Mapping();
            withWorkflow.setCode(MappingWorkflowTestFixtures.SOURCE_CONCEPT_CODE);

            final Mapping withoutWorkflow = new Mapping();
            withoutWorkflow.setCode("999888777");

            final List<Mapping> mappings = Arrays.asList(withWorkflow, withoutWorkflow);
            MappingWorkflowService.attachWorkflows(context.getService(), context.getMapSet(), mappings);

            assertNotNull(withWorkflow.getMappingWorkflow());
            assertEquals(MapWorkflowStatus.EDITING_IN_PROGRESS, withWorkflow.getMappingWorkflow().getWorkflowStatus());
            assertEquals(context.getSpecialistUser().getUserName(), withWorkflow.getMappingWorkflow().getAssignedUser());
            assertNull(withoutWorkflow.getMappingWorkflow());
            assertEquals(0, MappingWorkflowService.countWorkflowRows(context.getService(), context.getMapSet(), "999888777", 1));
        }
    }

    /**
     * Search map sets initializes nested map project membership collections and includes
     * global all-org/all-edition/all-project map users.
     *
     * @throws Exception the exception
     */
    @Test
    public void searchMapSetsInitializesMapProjectMemberships() throws Exception {

        try (MappingWorkflowTestFixtures.Context context = MappingWorkflowTestFixtures.Context.createInEdit()) {
            PropertyUtility.setProperty("security.handler.ENTRAID.users.admin",
                context.getAdminUser().getUserName() + "," + context.getSpecialistUser().getUserName());
            PropertyUtility.setProperty("security.handler.ENTRAID.users.lead", context.getLeadUser().getUserName());
            PropertyUtility.setProperty("security.handler.ENTRAID.users.spec",
                context.getSpecialistUser().getUserName() + "," + context.getOtherSpecialistUser().getUserName());

            context.getMapProject().getMapLeads().clear();
            context.getService().update(context.getMapProject());

            final SearchParameters searchParameters = new SearchParameters();
            searchParameters.setQuery("id:" + context.getMapSet().getId());

            final ResultList<MapSet> results = MapSetService.searchMapSets(context.getService(), searchParameters);
            assertEquals(1, results.getTotal());

            final MapProject mapProject = results.getItems().get(0).getMapProject();
            assertNotNull(mapProject);
            assertTrue(Hibernate.isInitialized(mapProject.getMapLeads()));
            assertTrue(Hibernate.isInitialized(mapProject.getMapSpecialists()));
            assertTrue(Hibernate.isInitialized(mapProject.getMapPrinciples()));

            final Set<String> leadNames = mapProject.getMapLeads().stream().map(MapUser::getUserName).collect(Collectors.toSet());
            final Set<String> specialistNames = mapProject.getMapSpecialists().stream().map(MapUser::getUserName).collect(Collectors.toSet());
            assertTrue(leadNames.contains(context.getLeadUser().getUserName()));
            assertTrue(leadNames.contains(context.getAdminUser().getUserName()));
            assertTrue(leadNames.contains(context.getSpecialistUser().getUserName()));
            assertTrue(!specialistNames.contains(context.getSpecialistUser().getUserName()));
            assertTrue(specialistNames.contains(context.getOtherSpecialistUser().getUserName()));
            assertEquals(MapUserRole.ADMINISTRATOR, mapProject.getMapLeads().stream()
                .filter(u -> context.getSpecialistUser().getUserName().equals(u.getUserName())).findFirst().get().getApplicationRole());
        }
    }
}
