package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Date;

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.MapWorkflowStatus;
import org.ihtsdo.refsetservice.model.MappingWorkflow;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.test.ProxyTester;
import org.ihtsdo.refsetservice.util.ResultList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit tests for {@link MappingWorkflow}.
 */
@SpringBootTest
@ActiveProfiles("test")
public class MappingWorkflowUnitTest extends BaseTest {

    private MappingWorkflow object;

    private MapSet mapSet;

    private MapProject mapProject;

    @BeforeEach
    public void setup() throws Exception {

        object = new MappingWorkflow();
        final ProxyTester mapSetTester = new ProxyTester(new MapSet());
        mapSet = (MapSet) mapSetTester.createObject(1);
        final ProxyTester mapProjectTester = new ProxyTester(new MapProject());
        mapProject = (MapProject) mapProjectTester.createObject(1);
    }

    @Test
    public void testModelGetSet() throws Exception {

        final GetterSetterTester tester = new GetterSetterTester(object);
        tester.exclude("mapSet");
        tester.exclude("mapProject");
        tester.exclude("mapSetId");
        tester.exclude("mapProjectId");
        tester.test();
    }

    @Test
    public void testPersistAndReload() throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy("test");
            service.setModifiedFlag(true);

            final MapSet persistedMapSet = persistMapSet(service);
            final MapProject persistedMapProject = persistMapProject(service, persistedMapSet.getProject().getEdition());

            final Date assignedAt = new Date();
            final Date leaseExpiresAt = new Date(assignedAt.getTime() + 3600000L);

            final MappingWorkflow workflow = new MappingWorkflow();
            workflow.setSourceConceptCode("123456789");
            workflow.setWorkflowStatus(MapWorkflowStatus.EDITING_IN_PROGRESS);
            workflow.setAssignedUser("specialistUser");
            workflow.setAssignedAt(assignedAt);
            workflow.setLeaseExpiresAt(leaseExpiresAt);
            workflow.setSpecialistSlot(1);
            workflow.setMapSet(persistedMapSet);
            workflow.setMapProject(persistedMapProject);

            service.add(workflow);

            final MappingWorkflow reloaded = service.get(workflow.getId(), MappingWorkflow.class);
            assertNotNull(reloaded);
            assertMappingWorkflowFieldsEqual(workflow, reloaded);

            final MappingWorkflow invalid = new MappingWorkflow();
            invalid.setSourceConceptCode(null);
            invalid.setWorkflowStatus(MapWorkflowStatus.NEW);
            invalid.setMapSet(persistedMapSet);
            assertThrows(Exception.class, () -> service.add(invalid));

            service.remove(workflow);
            service.remove(persistedMapProject);
            service.remove(persistedMapSet);
            cleanupMapSetDependencies(service, persistedMapSet);
        }
    }

    @Test
    public void testFindByConceptAndStatus() throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy("test");
            service.setModifiedFlag(true);

            final MapSet persistedMapSet = persistMapSet(service);

            final MappingWorkflow workflowNew = new MappingWorkflow();
            workflowNew.setSourceConceptCode("123");
            workflowNew.setWorkflowStatus(MapWorkflowStatus.NEW);
            workflowNew.setSpecialistSlot(1);
            workflowNew.setMapSet(persistedMapSet);
            service.add(workflowNew);

            final MappingWorkflow workflowDone = new MappingWorkflow();
            workflowDone.setSourceConceptCode("456");
            workflowDone.setWorkflowStatus(MapWorkflowStatus.EDITING_DONE);
            workflowDone.setSpecialistSlot(1);
            workflowDone.setMapSet(persistedMapSet);
            service.add(workflowDone);

            final String query = "sourceConceptCode:" + QueryParserBase.escape("123")
                + " AND workflowStatus:" + MapWorkflowStatus.NEW.name();
            final ResultList<MappingWorkflow> results = service.find(query, null, MappingWorkflow.class, null);

            assertEquals(1, results.getItems().size());
            assertEquals(workflowNew.getId(), results.getItems().get(0).getId());

            service.remove(workflowNew);
            service.remove(workflowDone);
            service.remove(persistedMapSet);
            cleanupMapSetDependencies(service, persistedMapSet);
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
        persistedMapSet.setRefSetCode("mw-test-" + System.currentTimeMillis());
        persistedMapSet.setRefSetName("Mapping Workflow Test MapSet");
        persistedMapSet.setName("Mapping Workflow Test MapSet " + System.currentTimeMillis());
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

    private MapProject persistMapProject(final TerminologyService service, final Edition edition) throws Exception {

        final ProxyTester mapProjectTester = new ProxyTester(new MapProject());
        final MapProject persistedMapProject = (MapProject) mapProjectTester.createObject(1);
        persistedMapProject.setId(null);
        persistedMapProject.setName("Mapping Workflow Test Project " + System.currentTimeMillis());
        persistedMapProject.setEdition(edition);
        return service.add(persistedMapProject);
    }

    private void cleanupMapSetDependencies(final TerminologyService service, final MapSet mapSetToRemove) throws Exception {

        service.remove(mapSetToRemove.getProject());
        service.remove(mapSetToRemove.getProject().getEdition());
        service.remove(mapSetToRemove.getProject().getEdition().getOrganization());
    }

    private void assertMappingWorkflowFieldsEqual(final MappingWorkflow expected, final MappingWorkflow actual) {

        assertEquals(expected.getSourceConceptCode(), actual.getSourceConceptCode());
        assertEquals(expected.getWorkflowStatus(), actual.getWorkflowStatus());
        assertEquals(expected.getAssignedUser(), actual.getAssignedUser());
        assertEquals(expected.getAssignedAt().getTime(), actual.getAssignedAt().getTime());
        assertEquals(expected.getLeaseExpiresAt().getTime(), actual.getLeaseExpiresAt().getTime());
        assertEquals(expected.getSpecialistSlot(), actual.getSpecialistSlot());
        assertEquals(expected.getMapSet().getId(), actual.getMapSet().getId());
        assertEquals(expected.getMapProject().getId(), actual.getMapProject().getId());
    }
}
