
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.TypeKeyValue;
import org.ihtsdo.refsetservice.model.VersionStatus;
import org.ihtsdo.refsetservice.rest.test.util.ExportUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.GetUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.RefsetConceptsType;
import org.ihtsdo.refsetservice.rest.test.util.WorkflowUnitTestUtilities;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for MetadataController.
 */

@AutoConfigureMockMvc
public class RefsetControllerTests extends AbstractRefsetTests {

    public enum RefsetConceptStatus {
        EXISTS, NOT_FOUND

    }

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(RefsetControllerTests.class);

    /** The Constant MAIN_TESTING_REFSET_ID. */
    // Belgian simple reference set for translated animal materials w/101
    // members
    // Resides with 101 members on wci-snowstorm, dev-integration, UAT, and
    // production
    private static final String GPS_REFSET_ID = "787778008";

    // With 2 parents, 6 children and 0 defing rels
    private static final String FIRST_MAIN_NRC_REFSET_CONCEPT_ID = "37663002";

    // With 1 parents, 0 children and 0 defing rels
    private static final String SECOND_MAIN_NRC_REFSET_CONCEPT_ID = "276310004";

    private static final String FIRST_MAIN_CORE_REFSET_CONCEPT_ID = "118690002";

    private static String inactiveRefsetVersionInternalId;

    private static String earlierInactiveRefsetInternalId;

    private static String deltaExportRefsetVersionInternalId;

    private static final List<String> firstConceptDescList = new ArrayList<>();

    private static final List<String> firstConceptParentDescList = new ArrayList<>();

    private static final List<String> inactiveConceptDescList = new ArrayList<>();

    private static final List<String> secondConceptAllDescTypeList = new ArrayList<>();

    private static final List<String> secondConceptPtAndFsnOnlyDescList = new ArrayList<>();

    private static final List<String> conceptSearchDescList = new ArrayList<>();

    private static final String TWO_VERSION_DELTA_FILE = REFSET_FILE_PATH + "Lateralizable Delta 201801 to 201807.txt";

    private static final String THREE_VERSION_DELTA_FILE = REFSET_FILE_PATH + "Lateralizable Delta 201801 to 201901.txt";

    private static final String SNAPSHOT_FILE = REFSET_FILE_PATH + "561000172108 Snapshot 20200315.txt";

    private static final String LIST_OF_SCTIDS_FILE = REFSET_FILE_PATH + "561000172108 ListOfSctIds 20200315.txt";

    private static final String REFSET_SNAPSHOT_EXPORT_VERSION = "20200315";

    private static final String REFSET_DELTA_TO_EXPORT_REFSET_ID = "723264001";

    private static final String REFSET_DELTA_TO_EXPORT_VERSION = "2021-07-31";

    private static final String REFSET_DELTA_FROM_EXPORT_VERSION = "20180131";

    private static final String REFSET_DELTA_TO_EXPORT_TWO_VERSIONS = "20180731";

    private static final String REFSET_DELTA_TO_EXPORT_THREE_VERSIONS = "20190131";

    // Test by term per language
    // Test by concept Id
    // by term id
    // by refset id
    // By narrative
    private static final String membersSearchQueryList[] = new String[] {
        "human", "Animal", "HAIR", "Non", "niet", "menselijk", "dierenhaar", "poil", "dierlijk", "haar", "276310004", "412393015", "1495334015"
    };

    private static final String invalidSearchTerms[] = new String[] {
        "138875005", "SNOMED Clinical Terms version"
    };

    private static boolean firstTimeSetup = true;

    private static boolean skipEarlierInactiveVersionTests = false;

    /**
     * Sets the up.
     */
    @BeforeEach
    public void setUp(TestInfo info) {

        if (getUtil == null) {

            getUtil = new GetUnitTestUtilities(mvc, baseUrl, SIMPLE_DATE_FORMAT);
            exportUtil = new ExportUnitTestUtilities(mvc);
            workflowUtil = new WorkflowUnitTestUtilities(mvc, baseUrl, REFSET_FILE_PATH);
        }

        // Setup Utility classes
        objectMapper = new ObjectMapper();
        JacksonTester.initFields(this, objectMapper);
        baseUrl = "/refset";

        try {

            if (firstTimeSetup) {

                readOnlyTestingProjectId = getUtil.getInternalProjectId(READ_ONLY_TESTING_PROJECT_NAME);
                mainNrcTestingRefsetInternalId = getUtil.getInternalRefsetId(MAIN_NRC_TESTING_REFSET_ID, MAIN_NRC_TESTING_REFSET_VERSION);
                mainCoreTestingRefsetInternalId = getUtil.getInternalRefsetId(MAIN_CORE_TESTING_REFSET_ID, MAIN_CORE_TESTING_REFSET_VERSION);

                // Ensure have the export directory created on testing system

                String exportFileDir = PropertyUtility.getProperty("export.fileDir") + File.separator;
                File f = new File(exportFileDir);

                if (!f.exists()) {

                    throw new Exception("Tests with Export because the expected export directory doesn't exist: " + exportFileDir);
                }

                mainNrcTestingRefsetInternalId = getUtil.getInternalRefsetId(MAIN_NRC_TESTING_REFSET_ID, MAIN_NRC_TESTING_REFSET_VERSION);

                inactiveRefsetVersionInternalId = getUtil.getInternalRefsetId(REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_ID, REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_VERSION);
                deltaExportRefsetVersionInternalId = getUtil.getInternalRefsetId(REFSET_DELTA_TO_EXPORT_REFSET_ID, REFSET_DELTA_TO_EXPORT_VERSION);

                try {

                    earlierInactiveRefsetInternalId =
                        getUtil.getInternalRefsetId(REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_ID, REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_EARLIER_VERSION);
                } catch (Exception e) {

                    logger.info("Snowstorm instance we are running against doesn't have " + REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_EARLIER_VERSION + " of the refset "
                        + REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_ID + ", thus skip related tests");
                    skipEarlierInactiveVersionTests = true;
                }

                refsetWithInactiveConceptAsActiveMemberInternalId = getUtil.getInternalRefsetId(REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_ID, REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_VERSION);

                firstConceptDescList.add("Venom (substance)");
                firstConceptDescList.add("Venom");
                firstConceptDescList.add("venin");
                firstConceptDescList.add("gif");

                firstConceptParentDescList.add("Animal agent (substance)");
                firstConceptParentDescList.add("Animal agent");
                firstConceptParentDescList.add("produit animal");
                firstConceptParentDescList.add("dierlijk product");

                inactiveConceptDescList.add("Rotator cuff syndrome (disorder)");
                inactiveConceptDescList.add("Rotator cuff syndrome");
                inactiveConceptDescList.add("Rotator cuff rupture");
                inactiveConceptDescList.add("Rotator cuff tear");
                inactiveConceptDescList.add("Rupture of rotator cuff of shoulder");

                secondConceptAllDescTypeList.add("Non-human hair - material (substance)");
                secondConceptAllDescTypeList.add("Animal hair");
                secondConceptAllDescTypeList.add("Non-human hair - material");
                secondConceptAllDescTypeList.add("poil animal");
                secondConceptAllDescTypeList.add("dierlijk haar");
                secondConceptAllDescTypeList.add("dierenhaar");
                secondConceptAllDescTypeList.add("niet-menselijk haar");

                secondConceptPtAndFsnOnlyDescList.add("Non-human hair - material (substance)");
                secondConceptPtAndFsnOnlyDescList.add("Animal hair");
                secondConceptPtAndFsnOnlyDescList.add("poil animal");
                secondConceptPtAndFsnOnlyDescList.add("dierlijk haar");

                conceptSearchDescList.add("Brazilian pemphigus foliaceus");

                firstTimeSetup = false;
            }

        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    /**
     * Test getting a project.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetProject() {

        final Project project = getUtil.getProject(readOnlyTestingProjectId);

        assertThat(project.getId()).isEqualTo(readOnlyTestingProjectId);
        assertThat(project.getName()).isEqualTo(READ_ONLY_TESTING_PROJECT_NAME);
    }

    /**
     * Test getting a refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testRefset() throws Exception {

        final Refset refset = getUtil.getRefsetFromRefsetIdAndVersion(MAIN_NRC_TESTING_REFSET_ID, MAIN_NRC_TESTING_REFSET_VERSION);

        validateRefsetMetadata(refset);
    }

    /**
     * Test getting editions.
     *
     * @throws Exception the exception
     */
    @Test
    public void testEditions() throws Exception {

        final ResultList<TypeKeyValue> editions = getUtil.getEditions();
        assertThat(editions.getItems().size()).isGreaterThan(8);

        boolean editionFound = false;

        for (TypeKeyValue keyValue : editions.getItems()) {

            if ("belgian edition".equalsIgnoreCase(keyValue.getKey())) {

                editionFound = true;
                break;
            }

        }

        assertThat(editionFound).isTrue();
    }

    /**
     * Test getting branch versions.
     *
     * @throws Exception the exception
     */
    @Test
    public void testBranchVersions() throws Exception {

        final ResultList<String> versions = getUtil.getBranches("SNOMEDCT-BE");
        assertThat(versions.getItems().size()).isGreaterThan(0);

        for (final String version : versions.getItems()) {

            assertThat(version.matches("\\d{4}-\\d{2}-\\d{2}"));
        }

    }

    /**
     * Test searching for projects.
     *
     * @throws Exception the exception
     */
    @Test
    public void testSearchProject() throws Exception {

        final ResultList<Project> resultList = getUtil.searchProjects();

        assertThat(resultList.getItems().size()).isGreaterThanOrEqualTo(1);
    }

    /**
     * Test listing refsets.
     *
     * @throws Exception the exception
     */
    @Test
    public void testSearchDirectory() throws Exception {

        ResultList<Refset> refsetList;
        Refset refset;

        // Test by name
        refsetList = getUtil.searchDirectory("animal");
        refset = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);
        validateRefsetMetadata(refset);

        // Test by partial name
        refsetList = getUtil.searchDirectory("ani");
        refset = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);
        validateRefsetMetadata(refset);

        // Test by alternate refset name (using translation)
        refsetList = getUtil.searchDirectory("ensemble de référence simple belge pour les matières animales traduites");
        refset = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);
        validateRefsetMetadata(refset);

        // Test by partial alternate refset name (using translation)
        refsetList = getUtil.searchDirectory("matiè");
        refset = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);
        validateRefsetMetadata(refset);

        // Test by edition name
        refsetList = getUtil.searchDirectory("editionName:Belgian Edition");
        refset = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);
        validateRefsetMetadata(refset);

        // Test by combination
        refsetList = getUtil.searchDirectory("animal AND editionName:Belgian Edition");
        refset = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);
        validateRefsetMetadata(refset);

        // Test by combination with partials
        refsetList = getUtil.searchDirectory("anim AND editionName:Belgian Edi");
        refset = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);
        validateRefsetMetadata(refset);

        // Test on inactive concept that is active member of an active refset
        refsetList = getUtil.searchDirectory(INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
        refset = validateRefsetExists(refsetList, REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_ID);
        assertThat(refset).isNotNull();

        // Test by term per language (at least on non-pt)
        // Test by concept Id
        // by term id
        // by refset id
        // By narrative
        String searchTerms[] = new String[] {
            "Dog", "squame", "huidschilfer", "olie uit lever van vis", "260154005", "999861000172117", "561000172108", "Allergies General"
        };

        for (int i = 0; i < searchTerms.length; i++) {

            logger.info("Testing term - " + searchTerms[i]);

            refsetList = getUtil.searchDirectory(searchTerms[i]);
            refset = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);

            validateRefsetMetadata(refset);
        }

        // Verify when expect to return no matching refsets
        for (int i = 0; i < invalidSearchTerms.length; i++) {

            logger.info("Testing term - " + invalidSearchTerms[i]);

            refsetList = getUtil.searchDirectory(invalidSearchTerms[i]);
            refset = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);

            assertThat(refset).isNull();
        }

        // Test Inactive Concept search
        refsetList = getUtil.searchDirectory(INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
        refset = validateRefsetExists(refsetList, REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_ID);
        validateRefsetMetadata(refset);

        // Test graceful handling of zero results
        try {

            refsetList = getUtil.searchDirectory("1234567890");
        } catch (AssertionError e) {

            assertThat(refsetList.getItems().isEmpty()).isTrue();
            assertThat(refsetList.getTotal()).isEqualTo(0);
        }

    }

    /**
     * Test exporting a refset SCTID list.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExportSctidList() throws Exception {

        final JsonNode root = exportUtil.exportSctIds(mainNrcTestingRefsetInternalId);

        // Validate
        validateExportFiles(root, LIST_OF_SCTIDS_FILE);
    }

    /**
     * Test exporting as RF2 Snapshot.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExportRf2Snapshot() throws Exception {

        // Clear content on AWS first to ensure actually are generating export
        // rather than just returning cached content
        exportUtil.deleteRefsetExportsFromAws(MAIN_NRC_TESTING_REFSET_ID, REFSET_SNAPSHOT_EXPORT_VERSION);

        final JsonNode root = exportUtil.exportRf2Snapshot(mainNrcTestingRefsetInternalId, REFSET_SNAPSHOT_EXPORT_VERSION);

        // Validate
        validateExportFiles(root, SNAPSHOT_FILE);
    }

    /**
     * Test exporting as RF2 Delta.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExportRf2Delta() throws Exception {

        /* Test delta between two versions */
        exportUtil.deleteRefsetExportsFromAwsAllVersions(REFSET_DELTA_TO_EXPORT_REFSET_ID);

        JsonNode root = exportUtil.exportRf2Delta(deltaExportRefsetVersionInternalId, REFSET_DELTA_FROM_EXPORT_VERSION, REFSET_DELTA_TO_EXPORT_TWO_VERSIONS);

        validateExportFiles(root, TWO_VERSION_DELTA_FILE);

        /* Test delta between three versions */
        exportUtil.deleteRefsetExportsFromAwsAllVersions(REFSET_DELTA_TO_EXPORT_REFSET_ID);

        root = exportUtil.exportRf2Delta(deltaExportRefsetVersionInternalId, REFSET_DELTA_FROM_EXPORT_VERSION, REFSET_DELTA_TO_EXPORT_THREE_VERSIONS);

        validateExportFiles(root, THREE_VERSION_DELTA_FILE);
    }

    /**
     * Test getting concept details - This doesn't include parents or children as there are dedicated tests for them in the class.
     *
     * @throws Exception the exception
     */
    @Test
    public void testConceptDetails() throws Exception {

        // Test normal concept Details Call
        Concept concept = getUtil.getConceptDetails(mainNrcTestingRefsetInternalId, FIRST_MAIN_NRC_REFSET_CONCEPT_ID);
        validateConcept(concept, FIRST_MAIN_NRC_REFSET_CONCEPT_ID, null, false, firstConceptDescList, 0, 0, 0);

        // Try second concept
        concept = getUtil.getConceptDetails(mainNrcTestingRefsetInternalId, SECOND_MAIN_NRC_REFSET_CONCEPT_ID);
        validateConcept(concept, SECOND_MAIN_NRC_REFSET_CONCEPT_ID, null, false, secondConceptAllDescTypeList, 0, 0, 0);

        concept = getUtil.getConceptDetails(inactiveRefsetVersionInternalId, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
        validateConcept(concept, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID, null, false, inactiveConceptDescList, 0, 0, 0);

        // Test invalid refset is handled gracefully
        try {

            concept = null;
            concept = getUtil.getConceptDetails(INVALID_INTERNAL_REFSET_ID, FIRST_MAIN_NRC_REFSET_CONCEPT_ID);
        } catch (AssertionError ae) {

            assertThat(concept).isNull();

            logger.info("Successfully identified that refset is invalid");
        }

    }

    /**
     * Test getting the list of concepts that represent refsets for dropdown options.
     *
     * @throws Exception the exception
     */
    @Test
    public void testRefsetConcepts() throws Exception {

        final String branch = "MAIN/2022-01-31";

        // call the api to get the refset concept list for refset parents
        final ConceptResultList existingRefsetConcepts = getUtil.getRefsetConcepts(branch, RefsetConceptsType.ALL_SIMPLE_TYPE_CONCEPTS);
        assertThat(existingRefsetConcepts.getItems().size()).isEqualTo(39);

        final Concept existingRefsetConcept = identifyMemberFromList(existingRefsetConcepts, GPS_REFSET_ID);
        assertThat(existingRefsetConcept).isNotNull();

        // call the api to get the refset concept list for using as the
        // underlying concept for a new refset
        final ConceptResultList newRefsetConcepts = getUtil.getRefsetConcepts(branch, RefsetConceptsType.NEW_REFSET_CONCEPTS);
        assertThat(newRefsetConcepts.getItems().size()).isEqualTo(29);

        final Concept newRefsetConcept = identifyMemberFromList(newRefsetConcepts, GPS_REFSET_ID);
        assertThat(newRefsetConcept).isNull();
    }

    /**
     * Test getting all members of a given refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testMemberList() throws Exception {

        ConceptResultList members = getUtil.getMembers(mainNrcTestingRefsetInternalId);

        // At time last update, 101 members were found in the refsets
        assertThat(members.size()).isEqualTo(101);

        // Membership info and descriptions, but no parents/children
        Concept concept = identifyMemberFromList(members, FIRST_MAIN_NRC_REFSET_CONCEPT_ID);
        validateConcept(concept, FIRST_MAIN_NRC_REFSET_CONCEPT_ID, "20200315", true, firstConceptDescList, 0, 0, 0);

        // Membership info and descriptions, but no parents/children
        concept = identifyMemberFromList(members, SECOND_MAIN_NRC_REFSET_CONCEPT_ID);
        validateConcept(concept, SECOND_MAIN_NRC_REFSET_CONCEPT_ID, "20200315", true, secondConceptPtAndFsnOnlyDescList, 0, 0, 0);

        members = getUtil.getMembers(inactiveRefsetVersionInternalId);
        concept = identifyMemberFromList(members, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
        assertThat(concept).isNotNull();
        assertThat(concept.isActive()).isFalse();
        assertThat(concept.isMemberOfRefset()).isTrue();
    }

    /**
     * Test searching refset members for when examining which members of a given refset match on the search criteria
     *
     * @throws Exception the exception
     */
    @Test
    public void testSearchMember() throws Exception {

        for (int i = 0; i < membersSearchQueryList.length; i++) {

            logger.debug("Testing term - : " + membersSearchQueryList[i]);

            final ConceptResultList members = getUtil.searchMembers(mainNrcTestingRefsetInternalId, membersSearchQueryList[i]);
            final Concept concept = identifyMemberFromList(members, SECOND_MAIN_NRC_REFSET_CONCEPT_ID);

            // Doesn't include relationships
            validateConcept(concept, SECOND_MAIN_NRC_REFSET_CONCEPT_ID, "20200315", true, secondConceptPtAndFsnOnlyDescList, 0, 0, 0);
        }

        /*-
         * TODO: Add this test once replace ECL-based search with one that returns inactive concepts
         * 
         * // Test Inactive
         * logger.debug("Testing term - : " + INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
         * 
         * ConceptResultList members = getUtil.searchMembers(inactiveRefsetVersionInternalId, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
         * Concept concept = identifyMemberFromList(members, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
         * 
         * // Doesn't include relationships
         * validateConcept(concept, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID, "20200315", true, secondConceptPtAndFsnOnlyDescList, 0, 0, 0);
         */

        // Test Invalid
        for (int i = 0; i < invalidSearchTerms.length; i++) {

            logger.info("Testing term - " + invalidSearchTerms[i]);

            final ConceptResultList members = getUtil.searchMembers(mainNrcTestingRefsetInternalId, invalidSearchTerms[i]);
            final Concept concept = identifyMemberFromList(members, SECOND_MAIN_NRC_REFSET_CONCEPT_ID);

            // Doesn't include relationships
            assertThat(concept).isNull();
        }

    }

    /**
     * Test searching refset members taxonomy
     *
     * @throws Exception the exception
     */
    @Test
    public void testSearchTaxonomy() throws Exception {

        for (int i = 0; i < membersSearchQueryList.length; i++) {

            logger.debug("Testing term - : " + membersSearchQueryList[i]);

            final ConceptResultList members = getUtil.searchTaxonomy(mainNrcTestingRefsetInternalId, membersSearchQueryList[i]);
            final Concept concept = identifyMemberFromList(members, SECOND_MAIN_NRC_REFSET_CONCEPT_ID);

            // Doesn't include membership status nor memberEffectiveTime
            validateConcept(concept, SECOND_MAIN_NRC_REFSET_CONCEPT_ID, null, false, secondConceptPtAndFsnOnlyDescList, 0, -1, 0);
        }

        /*-
         * TODO: Add this test once replace ECL-based search with one that returns inactive concepts
         * 
         * // Test Inactive
         * logger.debug("Testing term - : " + INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
         * 
         * ConceptResultList members = getUtil.searchTaxonomy(inactiveRefsetVersionInternalId, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
         * Concept concept = identifyMemberFromList(members, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
         * 
         * // Doesn't include relationships
         * validateConcept(concept, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID, "20200315", true, secondConceptPtAndFsnOnlyDescList, 0, 0, 0);
         */

        for (int i = 0; i < invalidSearchTerms.length; i++) {

            logger.info("Testing term - " + invalidSearchTerms[i]);

            final ConceptResultList members = getUtil.searchTaxonomy(mainNrcTestingRefsetInternalId, invalidSearchTerms[i]);
            final Concept concept = identifyMemberFromList(members, SECOND_MAIN_NRC_REFSET_CONCEPT_ID);

            // Doesn't include relationships
            assertThat(concept).isNull();
        }

    }

    /**
     * Test getting taxonomy children.
     *
     * @throws Exception the exception
     */
    @Test
    public void testTaxonomyChildren() throws Exception {

        // First concept with grandparent with: Animal Material (256363008) is
        // parent of
        // Animal Agent (105899005) which is a parent to firstConIdToExamine
        // (Venon)

        // Call this first
        // @RequestMapping(method = RequestMethod.GET, value = "/refset/{refsetInternalId}/ancestorCache", produces = "application/json")
        final boolean cacheSuccess = getUtil.setupAncestorCache(MAIN_NRC_TESTING_REFSET_ID, MAIN_NRC_TESTING_REFSET_VERSION);
        assertThat(cacheSuccess).isTrue();

        // Search on grandparent
        final List<String> hierarchy = new ArrayList<>();
        hierarchy.addAll(List.of("105590001", "115668003", "289958009", "256363008", "105899005", FIRST_MAIN_NRC_REFSET_CONCEPT_ID));

        for (int i = 0; i < hierarchy.size() - 1; i++) {

            final String parentId = hierarchy.get(i);
            logger.info("Testing parentID: " + parentId);

            final ConceptResultList children = getUtil.getChildren(mainNrcTestingRefsetInternalId, parentId);
            final Concept childConcept = identifyMemberFromList(children, hierarchy.get(i + 1));

            assertThat(childConcept.getHasDescendantRefsetMembers()).isTrue();
        }

    }

    /**
     * Test getting taxonomy children.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAncestorPath() throws Exception {

        final Concept concept = getUtil.getAncestorPath(FIRST_MAIN_CORE_REFSET_CONCEPT_ID, mainCoreTestingRefsetInternalId);

        // Verify that nodes in ancestor path is as expected
        final ConceptResultList ancestorList = new ConceptResultList(concept.getParents());
        assertThat(ancestorList.size()).isEqualTo(5);

        // Verify that the main concept isn't added as an ancestor of itself
        Concept nonAncestor = identifyMemberFromList(ancestorList, FIRST_MAIN_CORE_REFSET_CONCEPT_ID);
        assertThat(nonAncestor).isNull();

        // Verify that the main concept isn't added as an ancestor of itself
        Concept ancestor = identifyMemberFromList(ancestorList, "118950002");
        assertThat(ancestor).isNotNull();

        // Verify all expected ancestors are listed
        ancestor = identifyMemberFromList(ancestorList, "771329004");
        assertThat(ancestor).isNotNull();

        ancestor = identifyMemberFromList(ancestorList, "362958002");
        assertThat(ancestor).isNotNull();

        ancestor = identifyMemberFromList(ancestorList, "362958002");
        assertThat(ancestor).isNotNull();

        ancestor = identifyMemberFromList(ancestorList, "138875005");
        assertThat(ancestor).isNotNull();

    }

    /**
     * Test getting member history.
     *
     * @throws Exception the exception
     */
    @Test
    public void testMemberHistory() throws Exception {

        // MEMBER_WITH_HX_CONCEPT_ID activated in Jan 31 2017 and inactivated in
        // Jan 31 2019
        final ResultList<Map<String, String>> memberHistory = getUtil.getMemberHistory(inactiveRefsetVersionInternalId, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);

        for (final Map<String, String> historyEntry : memberHistory.getItems()) {

            final String version = historyEntry.get("version");
            final String change = historyEntry.get("change");

            assertThat(version.equals("2017-07-31") || version.equals("2018-01-31"));

            if (version.equals("2018-01-31")) {

                assertThat(change.equals("Inactivated"));
            } else {

                assertThat(change.equals("Added"));
            }

        }

    }

    /**
     * Test getting list of version statuses.
     *
     * @throws Exception the exception
     */
    @Test
    public void testVersionStatuses() throws Exception {
        // One-off test so not pushing to utils

        final String url = baseUrl + "/versionStatuses";
        logger.info("Testing url - " + url);
        final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        final String content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        ResultList<TypeKeyValue> versionStatuses = new ObjectMapper().readValue(content, (new TypeReference<ResultList<TypeKeyValue>>() {
            /* NA */}));
        assertThat(versionStatuses).isNotNull();
        assertThat(versionStatuses.getTotal()).isEqualTo(VersionStatus.values().length);

    }

    /**
     * Test getting list of version statuses.
     * @return
     *
     * @throws Exception the exception
     */
    @Test
    public void testVersionsAcrossRefsets() throws Exception {
        /*
         * Testing across concept details
         */

        // Was active in Orig Version
        if (!skipEarlierInactiveVersionTests) {

            Concept matchedConcept = getUtil.getConceptDetails(earlierInactiveRefsetInternalId, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
            assertThat(matchedConcept.isActive()).isTrue();
        }

        // Inactivated in latest Version
        Concept latestConcept = getUtil.getConceptDetails(inactiveRefsetVersionInternalId, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
        assertThat(latestConcept.isActive()).isFalse();

        /*
         * In List Search
         */
        if (!skipEarlierInactiveVersionTests) {

            ConceptResultList members = getUtil.searchMembers(earlierInactiveRefsetInternalId, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
            Concept member = identifyMemberFromList(members, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
            assertThat(member.isActive()).isTrue();
        }

        ConceptResultList members = getUtil.searchMembers(inactiveRefsetVersionInternalId, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
        Concept member = identifyMemberFromList(members, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
        // assertThat(member).isNull();

        /*
         * In Taxonomy
         */
        // Orig version was a child of INACTIVE_CONCEPT_PARENT_ID
        if (!skipEarlierInactiveVersionTests) {

            ConceptResultList children = getUtil.getChildren(earlierInactiveRefsetInternalId, INACTIVE_CONCEPT_ACTIVE_MEMBER_PARENT_CONCEPT_ID);
            member = identifyMemberFromList(children, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
            assertThat(member.isActive()).isTrue();
        }

        ConceptResultList children = null;

        // This should throw an exception to detect if we get here somehow
        try {

            children = getUtil.getChildren(inactiveRefsetVersionInternalId, INACTIVE_CONCEPT_ACTIVE_MEMBER_PARENT_CONCEPT_ID);
        } catch (AssertionError e) {

            assertThat(children).isNull();
        }

        /*
         * In Taxonomy Search -
         */
        // Taxonomy Search returns active member
        if (!skipEarlierInactiveVersionTests) {

            members = getUtil.searchTaxonomy(earlierInactiveRefsetInternalId, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
            member = identifyMemberFromList(members, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
            assertThat(member).isNotNull();
            assertThat(member.isActive()).isTrue();
        }

        try {

            members = getUtil.searchTaxonomy(inactiveRefsetVersionInternalId, INACTIVE_CONCEPT_ACTIVE_MEMBER_PARENT_CONCEPT_ID);
        } catch (AssertionError e) {

            assertThat(members.getItems()).isEmpty();
        }

        // Search for an INACTIVE_CONCEPT that is still an ACTIVE_REFSET_MEMBER of an ACTIVE_REFSET

        /*- 
         * TODO: Once we determine how to best handle searching refset for inactive concepts (right now ECL-based so only return actives)
         * ... Do this for List only (doesn't work for taxonomy which is only active concepts)
        
        members = getUtil.searchMembers(refsetWithInactiveConceptAsActiveMember, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
        member = identifyMemberFromList(members, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
        assertThat(member).isNotNull();
        assertThat(member.isActive()).isFalse();
        assertThat(member.isMemberOfRefset()).isTrue();
        */

    }

    @Test
    public void testTags() {

        final String testTag = "General / Allergies";
        logger.info("Testing term - " + testTag);

        ResultList<Refset> refsetList = getUtil.searchDirectory(testTag);
        Refset refsetIdentified = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);

        // Collection - Tags
        assertThat(refsetIdentified.getTags().size()).isEqualTo(1);
        assertThat(refsetIdentified.getTags().iterator().next()).isEqualTo("General / Allergies");

    }

    /*
     *
     * Supporting Methods - return the concept from the list that matches with the conceptId specified
     *
     */
    private Concept identifyMemberFromList(ConceptResultList concepts, String conceptId) {

        // Test first concept
        Concept concept = null;

        for (Concept conceptBeingTested : concepts.getItems()) {

            if (conceptBeingTested.getCode().equals(conceptId)) {

                concept = conceptBeingTested;
                break;
            }

        }

        return concept;
    }
    
    /**
     * Test the RTT Migration **** DO NOT CHECK THIS IN WITH @Test UNCOMMENTED.
     *
     * @throws Exception the exception
     */
    // **** DO NOT CHECK THIS IN WITH @Test UNCOMMENTED ****
    // @Test
    public void testMigration() throws Exception {

        final String url = "/admin/migration/rtt";
        logger.info("Testing url - " + url);
        final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        final String content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);

        assertThat(content).isEqualTo("RTT data migration completed successfully");
    }

}
