
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
import org.ihtsdo.refsetservice.rest.test.util.GetterUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.InternalIdGetterUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.RefsetConceptsType;
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

    /** The Constant TESTING_REFSET_ID. */
    // Belgian simple reference set for translated animal materials w/101
    // members
    // Resides with 101 members on wci-snowstorm, dev-integration, UAT, and
    // production
    private static String mainTestingRefsetInternalId;

    private static final String GPS_REFSET_ID = "787778008";

    // With 2 parents, 6 children and 0 defing rels
    private static final String FIRST_CONCEPT_ID = "37663002";

    private static final String FIRST_CONCEPT_PARENT_ID = "105899005";

    // With 1 parents, 0 children and 0 defing rels
    private static final String SECOND_CONCEPT_ID = "260206005";

    private static final String DETAILS_SEARCH_CONCEPT_ID = "276310004";

    private static final String CONCEPT_SEARCH_CONCEPT_ID = "46459009";

    private static String inactiveConceptRefsetInternalId;

    private static final List<String> firstConceptDescList = new ArrayList<>();

    private static final List<String> firstConceptParentDescList = new ArrayList<>();

    private static final List<String> secondConceptDescList = new ArrayList<>();

    private static final List<String> inactiveConceptDescList = new ArrayList<>();

    private static final List<String> detailSearchNonAcceptableConceptDescList = new ArrayList<>();

    private static final List<String> conceptSearchDescList = new ArrayList<>();

    private static final String TWO_VERSION_DELTA_FILE =
            REFSET_FILE_PATH + "Lateralizable Delta 201801 to 201807.txt";

    private static final String THREE_VERSION_DELTA_FILE =
            REFSET_FILE_PATH + "Lateralizable Delta 201801 to 201901.txt";

    private static final String SNAPSHOT_FILE =
            REFSET_FILE_PATH + "561000172108 Snapshot 20200315.txt";

    private static final String LIST_OF_SCTIDS_FILE =
            REFSET_FILE_PATH + "561000172108 ListOfSctIds 20200315.txt";

    private static final String TESTING_REFSET_SNAPSHOT_EXPORT_VERSION = "20200315";

    private static final String INACTIVE_REFSET_DELTA_FROM_EXPORT_VERSION = "20180131";

    private static final String INACTIVE_REFSET_DELTA_TO_EXPORT_TWO_VERSIONS = "20180731";

    private static final String INACTIVE_REFSET_DELTA_TO_EXPORT_THREE_VERSIONS = "20190131";

    // Test by term per language
    // Test by concept Id
    // by term id
    // by refset id
    // By narrative
    // by tags
    private static final String membersSearchQueryList[] = new String[] {
            "human", "Animal", "HAIR", "Non", "niet", "menselijk", "dierenhaar", "poil", "dierlijk",
            "haar", "276310004", "412393015", "1495334015"
    };;

    /**
     * Sets the up.
     */
    @BeforeEach
    public void setUp(TestInfo info) {
        if (getterUtil == null) {
            getterUtil = new GetterUnitTestUtilities(mvc, baseUrl);
            internalidGetterUtil = new InternalIdGetterUnitTestUtilities(SIMPLE_DATE_FORMAT);
            exportUtil = new ExportUnitTestUtilities(mvc);
        }

        // skip @BeforeEach in testRttMigration
        if (info.getDisplayName().equals("testRttMigration()")) {
            return;
        }

        // Setup Utility classes
        if (mainTestingRefsetInternalId == null) {

            objectMapper = new ObjectMapper();
            JacksonTester.initFields(this, objectMapper);
            baseUrl = "/refset";

            try {
                String exportFileDir =
                        PropertyUtility.getProperty("export.fileDir") + File.separator;

                // Ensure have the export directory created on testing system
                File f = new File(exportFileDir);
                if (!f.exists()) {
                    throw new Exception(
                            "Tests with Export because the expected export directory doesn't exist: "
                                    + exportFileDir);
                }

                mainTestingRefsetInternalId = internalidGetterUtil
                        .getRefsetInternalId(TESTING_REFSET_ID, TESTING_REFSET_VERSION);

                inactiveConceptRefsetInternalId = internalidGetterUtil
                        .getRefsetInternalId(INACTIVE_REFSET_ID, INACTIVE_REFSET_VERSION);

                testingEditionId = internalidGetterUtil.getEditionInternalId(TESTING_EDITION_NAME);
                testingProjectId = internalidGetterUtil.getProjectInternalId(TESTING_PROJECT_NAME);

                firstConceptDescList.add("Venom (substance)");
                firstConceptDescList.add("Venom");
                firstConceptDescList.add("venin");
                firstConceptDescList.add("gif");

                firstConceptParentDescList.add("Animal agent (substance)");
                firstConceptParentDescList.add("Animal agent");
                firstConceptParentDescList.add("produit animal");
                firstConceptParentDescList.add("dierlijk product");

                secondConceptDescList.add("Sheep wool (substance)");
                secondConceptDescList.add("Sheep wool");
                secondConceptDescList.add("schapenwol");
                secondConceptDescList.add("laine de mouton");

                inactiveConceptDescList.add("Entire sclerocorneal junction (body structure)");
                inactiveConceptDescList.add("Entire sclerocorneal junction");

                detailSearchNonAcceptableConceptDescList
                        .add("Non-human hair - material (substance)");
                detailSearchNonAcceptableConceptDescList.add("Animal hair");
                detailSearchNonAcceptableConceptDescList.add("dierlijk haar");
                detailSearchNonAcceptableConceptDescList.add("poil animal");

                conceptSearchDescList.add("Brazilian pemphigus foliaceus");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * Test getting a project.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGetProject() {
        final Project project = getterUtil.getProject(testingProjectId);

        assertThat(project.getId()).isEqualTo(testingProjectId);
        assertThat(project.getName()).isEqualTo(TESTING_PROJECT_NAME);
    }

    /**
     * Test getting a refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testRefset() throws Exception {

        final Refset refset = getterUtil.getRefsetFromInternalId(mainTestingRefsetInternalId);

        validateRefsetMetadata(refset);
    }

    /**
     * Test getting editions.
     *
     * @throws Exception the exception
     */
    @Test
    public void testEditions() throws Exception {

        final ResultList<TypeKeyValue> editions = getterUtil.getEditions();
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

        final ResultList<String> versions = getterUtil.getBranches("SNOMEDCT-BE");
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
    public void testProjectSearch() throws Exception {

        final ResultList<Project> resultList = getterUtil.searchProjects();

        assertThat(resultList.getItems().size()).isGreaterThanOrEqualTo(1);
    }

    /**
     * Test listing refsets.
     *
     * @throws Exception the exception
     */
    @Test
    public void testDirectorySearch() throws Exception {

        ResultList<Refset> refsetList;
        Refset refsetIdentified;

        // Test by name
        refsetList = getterUtil.searchDirectory("query=name:animal");
        refsetIdentified = validateRefsetExists(refsetList, TESTING_REFSET_ID);
        validateRefsetMetadata(refsetIdentified);

        // Test by partial name
        refsetList = getterUtil.searchDirectory("query=name:ani");
        refsetIdentified = validateRefsetExists(refsetList, TESTING_REFSET_ID);
        validateRefsetMetadata(refsetIdentified);

        // Test by edition name
        refsetList = getterUtil.searchDirectory("query=editionName:Belgian Edition");
        refsetIdentified = validateRefsetExists(refsetList, TESTING_REFSET_ID);
        validateRefsetMetadata(refsetIdentified);

        // Test by combination
        refsetList = getterUtil.searchDirectory("name:animal AND editionName:Belgian Edition");
        refsetIdentified = validateRefsetExists(refsetList, TESTING_REFSET_ID);
        validateRefsetMetadata(refsetIdentified);

        // Test by combination with partials
        refsetList = getterUtil.searchDirectory("name:anim AND editionName:Belgian Edi");
        refsetIdentified = validateRefsetExists(refsetList, TESTING_REFSET_ID);
        validateRefsetMetadata(refsetIdentified);

        // Test by term per language (at least on non-pt)
        // Test by concept Id
        // by term id
        // by refset id
        // By narrative
        // by tags
        String searchTerms[] = new String[] {
                "Dog", "squame", "huidschilfer", "olie uit lever van vis", "260154005",
                "999861000172117", "561000172108", "General", "anim Belgian Edi"
        };
        for (int i = 0; i < searchTerms.length; i++) {
            logger.info("Testing term - " + searchTerms[i]);
            refsetList = getterUtil.searchDirectory(searchTerms[i]);

            refsetIdentified = validateRefsetExists(refsetList, TESTING_REFSET_ID);
            validateRefsetMetadata(refsetIdentified);
        }

        // Test graceful handling of zero results
        try {
            refsetList = getterUtil.searchDirectory("1234567890");
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
        final JsonNode root = exportUtil.exportSctIds(mainTestingRefsetInternalId);

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
        exportUtil.deleteRefsetExportsFromAws(TESTING_REFSET_ID,
                TESTING_REFSET_SNAPSHOT_EXPORT_VERSION);

        final JsonNode root = exportUtil.exportRf2Snapshot(mainTestingRefsetInternalId,
                TESTING_REFSET_SNAPSHOT_EXPORT_VERSION);

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
        exportUtil.deleteRefsetExportsFromAwsAllVersions(INACTIVE_REFSET_ID);

        JsonNode root = exportUtil.exportRf2Delta(inactiveConceptRefsetInternalId,
                INACTIVE_REFSET_DELTA_FROM_EXPORT_VERSION,
                INACTIVE_REFSET_DELTA_TO_EXPORT_TWO_VERSIONS);

        validateExportFiles(root, TWO_VERSION_DELTA_FILE);

        /* Test delta between three versions */
        exportUtil.deleteRefsetExportsFromAwsAllVersions(INACTIVE_REFSET_ID);

        root = exportUtil.exportRf2Delta(inactiveConceptRefsetInternalId,
                INACTIVE_REFSET_DELTA_FROM_EXPORT_VERSION,
                INACTIVE_REFSET_DELTA_TO_EXPORT_THREE_VERSIONS);

        validateExportFiles(root, THREE_VERSION_DELTA_FILE);
    }

    /**
     * Test getting concept details - This doesn't include parents or children
     * as there are dedicated tests for them in the class.
     *
     * @throws Exception the exception
     */
    // JESSE
    @Test
    public void testConceptDetails() throws Exception {

        // Test normal concept Details Call
        Concept concept =
                getterUtil.getConceptDetails(FIRST_CONCEPT_ID, mainTestingRefsetInternalId);

        // doesn't include membership status nor memberEffectiveTime
        validateConcept(concept, FIRST_CONCEPT_ID, null, false, firstConceptDescList, 0, 0, 0);

        // Try second concept
        concept = getterUtil.getConceptDetails(SECOND_CONCEPT_ID, mainTestingRefsetInternalId);

        // doesn't include membership status nor memberEffectiveTime
        validateConcept(concept, SECOND_CONCEPT_ID, null, false, secondConceptDescList, 0, 0, 0);

        // Test invalid refset is handled gracefully
        try {
            concept = getterUtil.getConceptDetails(FIRST_CONCEPT_ID, INVALID_INTERNAL_REFSET_ID);
            assertThat(concept).isNull();
        } catch (AssertionError e) {
            logger.info("Successfully identified that refset is invalid");
        }
    }

    /**
     * Test getting the list of concepts that represent refsets for dropdown
     * options.
     *
     * @throws Exception the exception
     */
    @Test
    public void testRefsetConcepts() throws Exception {
        final String branch = "MAIN";

        // call the api to get the refset concept list for refset parents
        ConceptResultList parentsResultList =
                getterUtil.getRefsetConcepts(branch, RefsetConceptsType.ALL_REFSET_CONCEPTS);

        validateRefsetConcepts(parentsResultList, GPS_REFSET_ID, RefsetConceptStatus.EXISTS);

        // call the api to get the refset concept list for using as the
        // underlying concept for a new refset
        parentsResultList =
                getterUtil.getRefsetConcepts(branch, RefsetConceptsType.NEW_REFSET_CONCEPTS);

        validateRefsetConcepts(parentsResultList, GPS_REFSET_ID, RefsetConceptStatus.NOT_FOUND);
    }

    /**
     * Test getting all members of a given refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testMemberList() throws Exception {
        ConceptResultList members = getterUtil.getMembers(mainTestingRefsetInternalId);

        // At time last update, 101 members were found in the refsets
        assertThat(members.size()).isEqualTo(101);

        // Membership info and descriptions, but no parents/children
        Concept concept = identifySpecifiedMember(members, FIRST_CONCEPT_ID);
        validateConcept(concept, FIRST_CONCEPT_ID, "20200315", true, firstConceptDescList, 0, 0, 0);

        // Membership info and descriptions, but no parents/children
        concept = identifySpecifiedMember(members, SECOND_CONCEPT_ID);
        validateConcept(concept, SECOND_CONCEPT_ID, "20200315", true, secondConceptDescList, 0, 0,
                0);
    }

    /**
     * Test searching refset members for when examining which members of a given
     * refset match on the search criteria
     *
     * @throws Exception the exception
     */
    @Test
    public void testMemberSearch() throws Exception {

        for (int i = 0; i < membersSearchQueryList.length; i++) {

            final ConceptResultList members = getterUtil.searchMembers(mainTestingRefsetInternalId,
                    membersSearchQueryList[i]);
            final Concept concept = identifySpecifiedMember(members, DETAILS_SEARCH_CONCEPT_ID);

            // Doesn't include relationships
            validateConcept(concept, DETAILS_SEARCH_CONCEPT_ID, "20200315", true,
                    detailSearchNonAcceptableConceptDescList, 0, 0, 0);
        }
    }

    /**
     * Test searching refset members taxonomy
     *
     * @throws Exception the exception
     */
    @Test
    public void testTaxonomySearch() throws Exception {

        for (int i = 0; i < membersSearchQueryList.length; i++) {

            final ConceptResultList members = getterUtil.searchTaxonomy(mainTestingRefsetInternalId,
                    membersSearchQueryList[i]);

            final Concept concept = identifySpecifiedMember(members, DETAILS_SEARCH_CONCEPT_ID);

            // Doesn't include membership status nor memberEffectiveTime
            validateConcept(concept, DETAILS_SEARCH_CONCEPT_ID, null, false,
                    detailSearchNonAcceptableConceptDescList, 0, -1, 0);
        }
    }

    /**
     * Test searching general concepts when searching across all concepts for
     * possible members to add
     *
     * @throws Exception the exception
     */
    @Test
    public void testConceptSearch() throws Exception {
        String searchTerms[] = new String[] {
                // TODO: Add more
                "fogo"
        };

        for (int i = 0; i < searchTerms.length; i++) {

            final ConceptResultList members = getterUtil.searchConcepts(mainTestingRefsetInternalId,
                    membersSearchQueryList[i]);

            final Concept concept = identifySpecifiedMember(members, CONCEPT_SEARCH_CONCEPT_ID);

            // Doesn't include membership status nor memberEffectiveTime
            validateConcept(concept, CONCEPT_SEARCH_CONCEPT_ID, null, false, conceptSearchDescList,
                    0, -1, 0);
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

        // Search on grandparent
        final List<String> hierarchy = new ArrayList<>(List.of("105590001", "115668003",
                "289958009", "256363008", "105899005", FIRST_CONCEPT_ID));

        for (int i = 0; i < hierarchy.size(); i++) {
            final String parentId = hierarchy.get(0);
            logger.info("Testing parentID: " + parentId);

            final ConceptResultList children =
                    getterUtil.getChildren(mainTestingRefsetInternalId, parentId);
            final Concept childConcept = identifySpecifiedMember(children, hierarchy.get(i + 1));

            if (childConcept.getCode().equals(FIRST_CONCEPT_ID)) {
                assertThat(childConcept.getHasDescendantRefsetMembers()).isTrue();
            }
        }
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
        final ResultList<Map<String, String>> memberHistory =
                getterUtil.getMemberHistory(inactiveConceptRefsetInternalId, INACTIVE_CONCEPT_ID);

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
        ResultList<TypeKeyValue> versionStatuses = new ObjectMapper().readValue(content,
                (new TypeReference<ResultList<TypeKeyValue>>() {
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

        final String origRefsetVersionId = internalidGetterUtil
                .getRefsetInternalId(INACTIVE_REFSET_ID, INACTIVE_REFSET_DIFFERENT_VERSION);

        /*
         * Testing across concept details
         */

        // Was active in Orig Version
        Concept matchedConcept =
                getterUtil.getConceptDetails(INACTIVE_CONCEPT_ID, origRefsetVersionId);
        assertThat(matchedConcept.isActive()).isTrue();

        // Inactivated in latest Version
        Concept latestConcept =
                getterUtil.getConceptDetails(INACTIVE_CONCEPT_ID, inactiveConceptRefsetInternalId);
        assertThat(latestConcept.isActive()).isFalse();
        /*
         * In List
         */
        ConceptResultList members = getterUtil.getMembers(origRefsetVersionId);
        Concept identifiedConcept = identifySpecifiedMember(members, INACTIVE_CONCEPT_ID);
        assertThat(identifiedConcept.isActive()).isTrue();

        members = getterUtil.getMembers(inactiveConceptRefsetInternalId);
        identifiedConcept = identifySpecifiedMember(members, INACTIVE_CONCEPT_ID);
        assertThat(identifiedConcept.isActive()).isFalse();

        /*
         * In List Search
         */
        members = getterUtil.searchMembers(origRefsetVersionId, INACTIVE_CONCEPT_ID);
        identifiedConcept = identifySpecifiedMember(members, INACTIVE_CONCEPT_ID);
        assertThat(identifiedConcept.isActive()).isTrue();

        members = getterUtil.searchMembers(inactiveConceptRefsetInternalId, INACTIVE_CONCEPT_ID);
        identifiedConcept = identifySpecifiedMember(members, INACTIVE_CONCEPT_ID);
        assertThat(identifiedConcept.isActive()).isFalse();

        /*
         * TODO: Fix next two (In Taxonomy & In Taxonomy Search). Need to find
         * different INACTIVE concept whose parent is still active today.
         * Current one's parent points to | 63716004 | Sclerocorneal junction
         * (body structure) | which is inactive too. Thus can't search taxonomy
         * as don't have concept to search upon
         */

        /*
         * In Taxonomy
         */
        // Orig version was a child of INACTIVE_CONCEPT_PARENT_ID
        /*
         * ConceptResultList children =
         * getterUtil.getChildren(INACTIVE_CONCEPTS_PARENT_CONCEPT_ID,
         * origRefsetVersionId); Concept identifiedConcept =
         * identifySpecifiedMember(children, INACTIVE_CONCEPT_ID);
         * assertThat(identifiedConcept.isActive()).isTrue();
         * 
         * // This should throw an exception to detect if we get here somehow
         * try { children =
         * getterUtil.getChildren(INACTIVE_CONCEPTS_PARENT_CONCEPT_ID,
         * inactiveConceptRefsetInternalId); } catch (AssertionError e) {
         * assertThat(children.getItems()).isEmpty(); }
         */
        /*
         * In Taxonomy Search -
         */
        // Taxonomy Search returns active member
        /*
         * ConceptResultList members =
         * getterUtil.searchTaxonomy(origRefsetVersionId,
         * INACTIVE_CONCEPTS_PARENT_CONCEPT_ID); Concept identifiedConcept =
         * identifySpecifiedMember(members, INACTIVE_CONCEPT_ID);
         * assertThat(identifiedConcept).isNotNull();
         * assertThat(matchedConcept.isActive()).isTrue();
         * 
         * try { members =
         * getterUtil.searchTaxonomy(inactiveConceptRefsetInternalId,
         * INACTIVE_CONCEPTS_PARENT_CONCEPT_ID); } catch (AssertionError e) {
         * assertThat(children.getItems()).isEmpty(); }
         */

        // TODO - Add tests once find an INACTIVE_CONCEPT that is still an
        // ACTIVE_REFSET_MEMBER of an ACTIVE_REFSET

    }

    // @Test
    // TODO: Fill out once have capability
    public void testAncestorAreMembersIdentifiers() {

    }

    /**
     * Test getting concept details.
     *
     * @throws Exception the exception
     */
    // TODO: Uncomment once Kai issue worked out
    // TODO: Expand for all inactives
    // @Test
    public void testInactives() throws Exception {
        String url = null;
        MvcResult result = null;
        String content = null;
        Concept inactiveConcept = null;

        /*
         * List member - Test no failure when populating table with inactive
         * concept
         */

        /*
         * 
         * 
         * int offset = 0; int limit = 5000; while (inactiveConcept == null &&
         * offset * limit < 10000) { url = "/refset/" +
         * inactiveConceptRefsetInternalId + "/members?limit=5000&offset=" +
         * offset++ + "&displayType=list&refsetInternalId=" +
         * inactiveConceptRefsetInternalId;
         * 
         * logger.info("Testing url - " + url);
         * 
         * result =
         * mvc.perform(get(url)).andExpect(status().isOk()).andReturn(); content
         * = result.getResponse().getContentAsString();
         * logger.info(" content = " + content); ConceptResultList members = new
         * ObjectMapper().readValue(content, (ConceptResultList.class));
         * 
         * // Testing Results assertThat(members).isNotNull();
         * assertThat(members.size()).isGreaterThan(1);
         * 
         * // Find inactive concept int count = 0; for (Concept
         * conceptBeingTested : members.getItems()) { if (++count % 1000 == 0) {
         * logger.info("Processed " + count + " members"); } if
         * (conceptBeingTested.getCode().equals(INACTIVE_CONCEPT_ID)) {
         * inactiveConcept = conceptBeingTested; break; } } }
         * 
         * // Membership info and descriptions, but no parents/children
         * validateConcept(inactiveConcept, INACTIVE_CONCEPT_ID, "20180731",
         * true, inactiveConceptDescList, 0, 0, 0);
         *
         *
         */

        /*
         * Concept Details - Test no failure when calling conceptDetails on
         * inactive concept
         */
        url = "/concept/" + INACTIVE_CONCEPT_ID + "?refsetInternalId="
                + inactiveConceptRefsetInternalId;
        logger.info("Inactive Concept Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        inactiveConcept = new ObjectMapper().readValue(content, Concept.class);

        // Doesn't include membership status nor memberEffectiveTime
        validateConcept(inactiveConcept, INACTIVE_CONCEPT_ID, null, false, inactiveConceptDescList,
                0, 0, 0);

        /*
         * Directory Search - Ensure refset can be matched on inactive concept
         * id
         */
        url = baseUrl
                + "/search?limit=500&offset=0&sort=versionDate&sortAscending=false&searchConcepts=true&query="
                + INACTIVE_CONCEPT_ID;

        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        final ResultList<Refset> resultList =
                new ObjectMapper().readValue(content, (new TypeReference<ResultList<Refset>>() {
                    /* NA */}));

        // Check results
        Refset refsetFound = null;
        for (Refset r : resultList.getItems()) {
            logger.info(r.getName());

            if (r.getRefsetId().equals(INACTIVE_REFSET_ID)) {
                refsetFound = r;
                break;
            }
        }

        validateRefsetMetadata(refsetFound);

        /*
         * List search - A query that doesn't match on anything will return zero
         * members without error
         */
        url = "/refset/" + inactiveConceptRefsetInternalId + "/members?limit=500&offset=0&query="
                + INACTIVE_CONCEPT_ID + "&displayType=list";

        logger.info("Testing term - " + INACTIVE_CONCEPT_ID);
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        ConceptResultList members =
                new ObjectMapper().readValue(content, (ConceptResultList.class));

        // Find inactive concept
        int count = 0;
        for (Concept conceptBeingTested : members.getItems()) {
            if (++count % 1000 == 0) {
                logger.info("Processed " + count + " members");
            }

            if (conceptBeingTested.getCode().equals(INACTIVE_CONCEPT_ID)) {
                inactiveConcept = conceptBeingTested;
                break;
            }
        }

        // TODO - FIND INACTIVE CONCEPT THAT IS ACTIVE REFSET MEMBER
        // member is inactive so no results.
        assertThat(count).isEqualTo(0);
        // Membership info and descriptions, but no parents/children
        // validateConcept(inactiveConcept, INACTIVE_CONCEPT_ID, "20180731",
        // true, inactiveConceptDescList, 0, 0, 0);

        // Taxonomy Search - Snowstorm does not allow searching for inactive
        // concepts, so should return zero results

        url = "/refset/" + inactiveConceptRefsetInternalId
                + "/taxonomySearch?limit=500&offset=0&query=" + INACTIVE_CONCEPT_ID;

        logger.info("Testing term - " + INACTIVE_CONCEPT_ID);
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);

        members = new ObjectMapper().readValue(content, (ConceptResultList.class));

        // Testing Results
        assertThat(members).isNotNull();
        assertThat(members.getItems().isEmpty()).isTrue();
    }

    /**
     * Test getting concept details.
     *
     * @throws Exception the exception
     */
    // TODO: Expand for all invalids
     @Test
    public void testInvalids() throws Exception {
        // Test invalid refset used in getMembers is handled gracefully
        final ConceptResultList members = getterUtil.getMembers(INVALID_INTERNAL_REFSET_ID);
        assertThat(members).isNull();

        
        // Test bad root
        ConceptResultList children = null;
        try { 
            children = getterUtil.getChildren(INVALID_INTERNAL_REFSET_ID,
                    "VALUE_DOESNT_MATTER");
        } catch (AssertionError e) {
            assertThat(children).isNull();
        }
            
    }

    /*
     *
     * Supporting Methods
     *
     */
    private void validateRefsetConcepts(ConceptResultList parentsResultList, String refsetId,
        RefsetConceptStatus refsetConceptStatus) {
        boolean refsetFound = false;
        // make sure a standard refset is present
        for (final Concept concept : parentsResultList.getItems()) {
            if (concept.getCode().equals(refsetId)) {
                refsetFound = true;
                break;
            }
        }

        if (refsetConceptStatus.equals(RefsetConceptStatus.EXISTS)) {
            assertThat(refsetFound).isTrue();
        } else {
            assertThat(refsetFound).isFalse();
        }
    }

    private Concept identifySpecifiedMember(ConceptResultList members, String conceptId) {
        // Test first concept
        Concept concept = null;
        for (Concept conceptBeingTested : members.getItems()) {
            if (conceptBeingTested.getCode().equals(FIRST_CONCEPT_ID)) {
                concept = conceptBeingTested;
                break;
            }
        }

        return concept;
    }

}
