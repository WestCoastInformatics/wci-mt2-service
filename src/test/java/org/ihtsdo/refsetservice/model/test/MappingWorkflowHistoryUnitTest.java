package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.MappingWorkflow;
import org.ihtsdo.refsetservice.model.MappingWorkflowHistory;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.enums.MappingWorkflowAction;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.ProxyTester;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit tests for {@link MappingWorkflowHistory}.
 */
@SpringBootTest
@ActiveProfiles("test")
public class MappingWorkflowHistoryUnitTest extends BaseTest {

    @Test
    public void testPersistHistory() throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy("test");
            service.setModifiedFlag(true);

            final MapSet mapSet = persistMapSet(service);
            final MappingWorkflow workflow = new MappingWorkflow();
            workflow.setSourceConceptCode("987654321");
            workflow.setWorkflowStatus(MapWorkflowStatus.EDITING_IN_PROGRESS);
            workflow.setAssignedUser("specialistUser");
            workflow.setSpecialistSlot(1);
            workflow.setMapSet(mapSet);
            service.add(workflow);

            final MappingWorkflowHistory history = new MappingWorkflowHistory();
            history.setUserName("specialistUser");
            history.setWorkflowStatus(MapWorkflowStatus.EDITING_IN_PROGRESS);
            history.setWorkflowAction(MappingWorkflowAction.ASSIGN);
            history.setNotes("Assigned for editing");
            history.setMappingWorkflow(workflow);
            service.add(history);

            final MappingWorkflowHistory reloaded = service.get(history.getId(), MappingWorkflowHistory.class);
            assertNotNull(reloaded);
            assertEquals(history.getUserName(), reloaded.getUserName());
            assertEquals(history.getWorkflowStatus(), reloaded.getWorkflowStatus());
            assertEquals(history.getWorkflowAction(), reloaded.getWorkflowAction());
            assertEquals(history.getNotes(), reloaded.getNotes());
            assertEquals(workflow.getId(), reloaded.getMappingWorkflow().getId());

            service.remove(history);
            service.remove(workflow);
            service.remove(mapSet);
            cleanupMapSetDependencies(service, mapSet);
        }
    }

    @Test
    public void testPersistHistoryRejectsMissingWorkflow() throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy("test");
            service.setModifiedFlag(true);

            final MappingWorkflowHistory history = new MappingWorkflowHistory();
            history.setUserName("specialistUser");
            history.setWorkflowStatus(MapWorkflowStatus.NEW);
            history.setWorkflowAction(MappingWorkflowAction.ASSIGN);
            history.setNotes("No workflow FK");
            history.setMappingWorkflow(null);

            assertThrows(Exception.class, () -> service.add(history));
        }
    }

    private MapSet persistMapSet(final TerminologyService service) throws Exception {

        final ProxyTester orgTester = new ProxyTester(new Organization());
        final Organization organization = (Organization) orgTester.createObject(1);
        organization.setId(null);
        service.add(organization);

        final ProxyTester editionTester = new ProxyTester(new Edition());
        final Edition edition = (Edition) editionTester.createObject(1);
        edition.setId(null);
        edition.setOrganization(organization);
        service.add(edition);

        final ProxyTester projectTester = new ProxyTester(new Project());
        final Project project = (Project) projectTester.createObject(1);
        project.setId(null);
        project.setEdition(edition);
        service.add(project);

        final MapSet persistedMapSet = new MapSet();
        persistedMapSet.setRefSetCode("mwh-test-" + System.currentTimeMillis());
        persistedMapSet.setRefSetName("Mapping Workflow History Test MapSet");
        persistedMapSet.setName("Mapping Workflow History Test MapSet " + System.currentTimeMillis());
        persistedMapSet.setVersionStatus(VersionStatus.IN_DEVELOPMENT);
        persistedMapSet.setWorkflowStatus(WorkflowStatus.IN_EDIT);
        persistedMapSet.setFromBranchPath("MAIN/TEST/2025-01-01");
        persistedMapSet.setFromTerminology("TEST");
        persistedMapSet.setFromVersion("2025-01-01");
        persistedMapSet.setToBranchPath("TARGET/20250101");
        persistedMapSet.setToTerminology("TARGET");
        persistedMapSet.setToVersion("20250101");
        persistedMapSet.setVersion("2025-01-01");
        persistedMapSet.setModuleId("51000202101");
        persistedMapSet.setBaseContentVersion("2025-01-01 TEST");
        persistedMapSet.setInternationalContentVersion("2025-01-01 SNOMED CT core");
        persistedMapSet.setProject(project);

        return service.add(persistedMapSet);
    }

    private void cleanupMapSetDependencies(final TerminologyService service, final MapSet mapSetToRemove) throws Exception {

        service.remove(mapSetToRemove.getProject());
        service.remove(mapSetToRemove.getProject().getEdition());
        service.remove(mapSetToRemove.getProject().getEdition().getOrganization());
    }
}
