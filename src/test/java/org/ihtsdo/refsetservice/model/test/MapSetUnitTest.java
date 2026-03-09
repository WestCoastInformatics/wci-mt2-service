package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.MapSet;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.model.enums.WorkflowStatus;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.CopyConstructorTester;
import org.ihtsdo.refsetservice.test.EqualsHashcodeTester;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.test.ProxyTester;
import org.ihtsdo.refsetservice.test.SerializationTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Unit test for {@link MapSet}.
 */
@SpringBootTest
@ActiveProfiles("test")
public class MapSetUnitTest extends BaseTest {

    private MapSet object;

    private Project project;

    @BeforeEach
    public void setup() throws Exception {

        object = new MapSet();
        final ProxyTester projectTester = new ProxyTester(new Project());
        project = (Project) projectTester.createObject(1);
    }

    @Test
    public void testModelGetSet() throws Exception {

        final GetterSetterTester tester = new GetterSetterTester(object);
        tester.exclude("project");
        tester.exclude("mapBranchId");
        tester.exclude("feedbackVisible");
        tester.exclude("roles");
        tester.exclude("locked");
        tester.exclude("terminologyVersionDate");
        tester.exclude("basedOnLatestVersion");
        tester.exclude("upgradeWarning");
        tester.exclude("availableActions");
        tester.exclude("parentConceptId");
        tester.exclude("descriptions");
        tester.exclude("versionList");
        tester.exclude("openDiscussionCount");
        tester.exclude("resolvedDiscussionCount");
        tester.exclude("upgradeVersionsCandidate");
        tester.test();
    }

    @Test
    public void testModelEqualsHashcode() throws Exception {

        final EqualsHashcodeTester tester = new EqualsHashcodeTester(object);
        tester.exclude("created");
        tester.exclude("modified");
        tester.exclude("modifiedBy");
        tester.exclude("active");

        tester.include("id");

        tester.exclude("project");

        assertTrue(tester.testIdentityFieldEquals());
        assertTrue(tester.testNonIdentityFieldEquals());
        assertTrue(tester.testIdentityFieldNotEquals());
        assertTrue(tester.testIdentityFieldHashcode());
        assertTrue(tester.testNonIdentityFieldHashcode());
        assertTrue(tester.testIdentityFieldDifferentHashcode());
    }

    @Test
    public void testModelCopy() throws Exception {

        final CopyConstructorTester tester = new CopyConstructorTester(object);
        tester.proxy("project", 1, project);
        tester.exclude("versionDate");
        tester.exclude("versionStatus");
        tester.exclude("workflowStatus");
        tester.exclude("assignedUser");
        tester.exclude("mapProject");
        tester.exclude("project");
        tester.exclude("mapBranchId");
        tester.exclude("editBranchId");
        tester.exclude("version");
        tester.exclude("baseContentVersion");
        tester.exclude("internationalContentVersion");
        tester.exclude("latestPublishedVersion");
        tester.exclude("hasVersionInDevelopment");
        tester.exclude("basedOnLatestVersion");
        tester.exclude("upgradeWarning");
        tester.exclude("inUpgrade");
        tester.exclude("inInactivate");
        tester.exclude("created");
        tester.exclude("modified");
        tester.exclude("modifiedBy");
        tester.exclude("active");
        assertTrue(tester.testCopyConstructor(MapSet.class));
    }

    @Test
    public void testModelSerialization() throws Exception {

        final SerializationTester tester = new SerializationTester(object);
        tester.exclude("project");
        assertTrue(tester.testJsonSerialization());
    }

    @Test
    public void testPersistence() throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy("test");
            service.setModifiedFlag(true);

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
            final Project proj = (Project) projectTester.createObject(1);
            proj.setId(null);
            proj.setEdition(edition);
            service.add(proj);

            final MapSet mapSet = new MapSet();
            mapSet.setId(null);
            mapSet.setRefSetCode("test-mapset-001");
            mapSet.setRefSetName("Test MapSet");
            mapSet.setName("Test MapSet " + System.currentTimeMillis());
            mapSet.setVersionStatus(VersionStatus.IN_DEVELOPMENT);
            mapSet.setWorkflowStatus(WorkflowStatus.READY_FOR_EDIT);
            mapSet.setBranchPath("MAIN/TEST/2025-01-01/WCITEST");
            mapSet.setFromBranchPath("MAIN/TEST/2025-01-01");
            mapSet.setFromTerminology("TEST");
            mapSet.setFromVersion("2025-01-01");
            mapSet.setToBranchPath("TARGET/20250101");
            mapSet.setToTerminology("TARGET");
            mapSet.setToVersion("20250101");
            mapSet.setVersion("2025-01-01");
            mapSet.setModuleId("51000202101");
            mapSet.setBaseContentVersion("2025-01-01 TEST");
            mapSet.setInternationalContentVersion("2025-01-01 SNOMED CT core");
            mapSet.setProject(proj);
            mapSet.setAssignedUser("testuser");

            final MapSet saved = service.add(mapSet);
            assertTrue(saved.getId() != null);

            final MapSet retrieved = service.get(saved.getId(), MapSet.class);
            assertTrue(retrieved != null);
            assertTrue("testuser".equals(retrieved.getAssignedUser()));

            service.remove(saved);
            service.remove(proj);
            service.remove(edition);
            service.remove(organization);
        }
    }
}
