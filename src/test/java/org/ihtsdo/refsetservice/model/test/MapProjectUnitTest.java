package org.ihtsdo.refsetservice.model.test;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.MapProject;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Team;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.test.CopyConstructorTester;
import org.ihtsdo.refsetservice.test.EqualsHashcodeTester;
import org.ihtsdo.refsetservice.test.GetterSetterTester;
import org.ihtsdo.refsetservice.test.ProxyTester;
import org.ihtsdo.refsetservice.test.SerializationTester;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class MapProjectUnitTest extends BaseTest {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(MapProjectUnitTest.class);

    /** The model object to test. */
    private MapProject object;

    /**
     * Setup.
     *
     * @throws Exception the exception
     */
    @BeforeEach
    public void setup() throws Exception {

        // MapProject
        object = new MapProject();

    }

    /**
     * Test getter and setter methods of model object.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelGetSet() throws Exception {

        final GetterSetterTester tester = new GetterSetterTester(object);
        tester.test();
    }

    /**
     * Test equals and hashcode methods.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelEqualsHashcode() throws Exception {

        final EqualsHashcodeTester tester = new EqualsHashcodeTester(object);
        // from AbstractHasModified
        tester.exclude("id");
        tester.exclude("created");
        tester.exclude("modified");
        tester.exclude("modifiedBy");
        tester.exclude("active");

        tester.include("name");
        tester.include("description");
        tester.include("privateProject");
        tester.include("primaryContactEmail");
        tester.include("mapNotesPublic");
        tester.include("groupStructure");
        tester.include("published");
        tester.include("useTags");
        tester.include("workflowType");
        tester.include("refSetId");
        tester.include("moduleId");
        tester.include("refSetName");
        tester.include("editingCycleBeginDate");
        tester.include("latestPublicationDate");
        tester.include("sourceTerminology");
        tester.include("sourceTerminologyVersion");
        tester.include("destinationTerminology");
        tester.include("destinationTerminologyVersion");
        tester.include("mapRefsetPattern");
        tester.include("reverseMapPattern");
        tester.include("mapRelationStyle");
        tester.include("mapPrincipleSourceDocumentName");
        tester.include("mapPrincipleSourceDocument");
        tester.include("ruleBased");
        tester.include("scopeDescendantsFlag");
        tester.include("scopeExcludedDescendantsFlag");
        tester.include("propagatedFlag");
        tester.include("propagationDescendantThreshold");
        tester.include("teamBased");

        tester.exclude("presetAgeRanges");
        tester.exclude("mapLeads");
        tester.exclude("mapSpecialists");
        tester.exclude("mapAdmins");
        tester.exclude("mapPrinciples");
        tester.exclude("mapAdvices");
        tester.exclude("additionalMapEntryInfos");
        tester.exclude("mapRelations");
        tester.exclude("mapReportDefinitions");
        tester.exclude("scopeConcepts");
        tester.exclude("scopeExcludedConcepts");
        tester.exclude("errorMessages");
        tester.exclude("edition");
        tester.exclude("roles");
        tester.exclude("memberList");
        tester.exclude("teamDetails");

        assertTrue(tester.testIdentityFieldEquals());
        assertTrue(tester.testNonIdentityFieldEquals());
        assertTrue(tester.testIdentityFieldNotEquals());
        assertTrue(tester.testIdentityFieldHashcode());
        assertTrue(tester.testNonIdentityFieldHashcode());
        assertTrue(tester.testIdentityFieldDifferentHashcode());
    }

    /**
     * Test model copy.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelCopy() throws Exception {

        final CopyConstructorTester tester = new CopyConstructorTester(object);
        assertTrue(tester.testCopyConstructor(MapProject.class));
    }

    /**
     * Test model serialization.
     *
     * @throws Exception the exception
     */
    @Test
    public void testModelSerialization() throws Exception {

        final SerializationTester tester = new SerializationTester(object);
        tester.exclude("presetAgeRanges");
        tester.exclude("mapLeads");
        tester.exclude("mapSpecialists");
        tester.exclude("mapAdmins");
        tester.exclude("mapPrinciples");
        tester.exclude("mapAdvices");
        tester.exclude("additionalMapEntryInfos");
        tester.exclude("mapRelations");
        tester.exclude("mapReportDefinitions");
        tester.exclude("scopeConcepts");
        tester.exclude("scopeExcludedConcepts");
        tester.exclude("errorMessages");
        tester.exclude("edition");
        tester.exclude("roles");
        tester.exclude("memberList");
        tester.exclude("teamDetails");
        assertTrue(tester.testJsonSerialization());
    }

    /**
     * Test persistence.
     *
     * @throws Exception the exception
     */
    @Test
    public void testPersistence() throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            service.setModifiedBy("test");
            service.setModifiedFlag(true);

            // organization
            final ProxyTester tester1 = new ProxyTester(new Organization());
            final Organization organization = (Organization) tester1.createObject(1);
            LOG.info("************ Organization: {}", organization);
            organization.setId(null);

            service.add(organization);

            // edition
            final ProxyTester tester2 = new ProxyTester(new Edition());
            final Edition edition = (Edition) tester2.createObject(1);
            LOG.info("************ Edition: {}", edition);
            edition.setId(null);
            edition.setOrganization(organization);

            service.add(edition);

            // teams
            final ProxyTester tester3 = new ProxyTester(new Team());
            final Team team1 = (Team) tester3.createObject(1);
            team1.setId(null);
            team1.setOrganization(organization);
            LOG.info("************ Team1: {}", team1);
            final Team team2 = (Team) tester3.createObject(2);
            team2.setId(null);
            team2.setOrganization(organization);
            LOG.info("************ Team2: {}", team2);

            service.add(team1);
            service.add(team2);

            // mapProject
            final ProxyTester tester4 = new ProxyTester(new MapProject());
            final MapProject object = (MapProject) tester4.createObject(1);
            LOG.info("************ Object: {}", object);
            object.setId(null);
            object.setEdition(edition);
            // object.getTeams().add(team1.getId());
            // object.getTeams().add(team2.getId());

            service.add(object);
            service.update(object);

            MapProject retrievedObject = service.get(object.getId(), object.getClass());

            // test that the MapProject can be retrieved.
            if (!object.getId().equals(retrievedObject.getId())) {
                throw new Exception("Original id unexpectedly does not match retrieved object id = " + object.getId() + ", " + retrievedObject.getId());
            }

            // // test that the correct number of members are present.
            // if (retrievedObject.getTeams().size() != 2) {
            // throw new Exception("Expected 2 members (users), found = " + retrievedObject.getTeams().size());
            // }

            service.remove(object);

            retrievedObject = service.get(object.getId(), object.getClass());

            if (retrievedObject != null) {
                throw new Exception("Search results size is unexpectedly not empty = " + retrievedObject.getId());
            }
        }

    }

}
