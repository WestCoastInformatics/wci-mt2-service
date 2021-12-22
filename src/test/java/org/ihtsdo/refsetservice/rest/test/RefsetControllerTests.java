
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
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

    /**
     * Sets the up.
     */
    @BeforeEach
    public void setUp(TestInfo info) {

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

        String url = null;
        MvcResult result = null;
        String content = null;

        // Test normal concept Details Call
        url = "/concept/" + FIRST_CONCEPT_ID + "?refsetInternalId=" + mainTestingRefsetInternalId;
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        Concept concept = new ObjectMapper().readValue(content, Concept.class);

        // doesn't include membership status nor memberEffectiveTime
        validateConcept(concept, FIRST_CONCEPT_ID, null, false, firstConceptDescList, 0, 0, 0);

        // Try second concept
        url = "/concept/" + SECOND_CONCEPT_ID + "?refsetInternalId=" + mainTestingRefsetInternalId;
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        concept = new ObjectMapper().readValue(content, Concept.class);

        // doesn't include membership status nor memberEffectiveTime
        validateConcept(concept, SECOND_CONCEPT_ID, null, false, secondConceptDescList, 0, 0, 0);

        // Test invalid refset is handled gracefully
        url = "/concept/" + FIRST_CONCEPT_ID + "?refsetInternalId=" + INVALID_INTERNAL_REFSET_ID;

        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().is5xxServerError()).andReturn();
        content = result.getResponse().getContentAsString();

        assertThat(content).isEmpty();
    }

    /**
     * Test getting the list of concepts that represent refsets for dropdown
     * options.
     *
     * @throws Exception the exception
     */
    @Test
    public void testRefsetConcepts() throws Exception {

        String url = null;
        MvcResult result = null;
        String content = null;
        String standardRefsetId = GPS_REFSET_ID;
        boolean standardRefsetFound = false;
        final String branch = "MAIN";

        // call the api to get the refset concept list for refset parents
        url = "/general/refsetConcepts?branch=" + branch + "&areParentConcepts=true";
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        final ConceptResultList parentsResultList =
                new ObjectMapper().readValue(content, ConceptResultList.class);

        assertThat(parentsResultList.getItems().size()).isGreaterThan(0);

        // make sure a standard refset is present
        for (final Concept concept : parentsResultList.getItems()) {

            if (concept.getCode().equals(standardRefsetId)) {

                standardRefsetFound = true;
                break;
            }
        }

        assertThat(standardRefsetFound).isTrue();
        standardRefsetFound = false;

        // call the api to get the refset concept list for using as the
        // underlying concept for a new refset
        url = "/general/refsetConcepts?branch=" + branch + "&areParentConcepts=false";
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        final ConceptResultList newRefsetResultList =
                new ObjectMapper().readValue(content, ConceptResultList.class);

        assertThat(newRefsetResultList.getItems().size()).isGreaterThan(0);

        // make sure a standard refset is not present
        for (final Concept concept : newRefsetResultList.getItems()) {

            if (concept.getCode().equals(standardRefsetId)) {

                standardRefsetFound = true;
                break;
            }
        }

        assertThat(standardRefsetFound).isFalse();
    }

    /**
     * Test getting all members of a given refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testMemberList() throws Exception {
        String url = null;
        MvcResult result = null;
        String content = null;

        url = "/refset/" + mainTestingRefsetInternalId
                + "/members?limit=500&offset=0&displayType=list";
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        ConceptResultList members =
                new ObjectMapper().readValue(content, (ConceptResultList.class));

        // Testing Results
        assertThat(members).isNotNull();
        assertThat(members.size()).isEqualTo(101);

        // Test first concept
        Concept concept = null;
        for (Concept conceptBeingTested : members.getItems()) {
            if (conceptBeingTested.getCode().equals(FIRST_CONCEPT_ID)) {
                concept = conceptBeingTested;
                break;
            }
        }

        // Membership info and descriptions, but no parents/children
        validateConcept(concept, FIRST_CONCEPT_ID, "20200315", true, firstConceptDescList, 0, 0, 0);

        // Test second concept
        concept = null;
        for (Concept conceptBeingTested : members.getItems()) {
            if (conceptBeingTested.getCode().equals(SECOND_CONCEPT_ID)) {
                concept = conceptBeingTested;
                break;
            }
        }

        // Membership info and descriptions, but no parents/children
        validateConcept(concept, SECOND_CONCEPT_ID, "20200315", true, secondConceptDescList, 0, 0,
                0);

        // Test invalid refset is handled gracefully
        url = "/refset/" + INVALID_INTERNAL_REFSET_ID
                + "/members?limit=500&offset=0&displayType=list";

        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().is5xxServerError()).andReturn();
        content = result.getResponse().getContentAsString();

        assertThat(content).isEmpty();
    }

    /**
     * Test searching refset members for when examining which members of a given
     * refset match on the search criteria
     *
     * @throws Exception the exception
     */
    @Test
    public void testMemberSearch() throws Exception {
        String url = null;
        MvcResult result = null;
        String content = null;

        // Test by term per language
        // Test by concept Id
        // by term id
        // by refset id
        // By narrative
        // by tags
        String searchTerms[] = new String[] {
                "human", "Animal", "HAIR", "Non", "niet", "menselijk", "dierenhaar", "poil",
                "dierlijk", "haar", "276310004", "412393015", "1495334015"
        };

        for (int i = 0; i < searchTerms.length; i++) {

            url = "/refset/" + mainTestingRefsetInternalId + "/members?limit=500&offset=0&query="
                    + searchTerms[i] + "&displayType=list";

            logger.info("Testing term - " + searchTerms[i]);
            logger.info("Testing url - " + url);
            result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);
            ConceptResultList members =
                    new ObjectMapper().readValue(content, (ConceptResultList.class));

            // Testing Results
            assertThat(members).isNotNull();

            Concept concept = null;
            for (Concept conceptBeingTested : members.getItems()) {
                if (conceptBeingTested.getCode().equals(DETAILS_SEARCH_CONCEPT_ID)) {
                    concept = conceptBeingTested;
                    break;
                }
            }

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
        String searchTerms[] = new String[] {
                "human", "Animal", "HAIR", "Non", "niet", "menselijk", "dierenhaar", "poil",
                "dierlijk", "haar", "276310004", "412393015", "1495334015"
        };

        String url = null;
        MvcResult result = null;
        String content = null;

        for (int i = 0; i < searchTerms.length; i++) {
            url = "/refset/" + mainTestingRefsetInternalId
                    + "/taxonomySearch?limit=500&offset=0&query=" + searchTerms[i];

            logger.info("Testing term - " + searchTerms[i]);
            logger.info("Testing url - " + url);
            result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);
            ConceptResultList members =
                    new ObjectMapper().readValue(content, (ConceptResultList.class));

            // Testing Results
            assertThat(members).isNotNull();

            Concept concept = null;
            for (Concept conceptBeingTested : members.getItems()) {
                if (conceptBeingTested.getCode().equals(DETAILS_SEARCH_CONCEPT_ID)) {
                    concept = conceptBeingTested;
                    break;
                }
            }
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
                "fogo"
        };

        String url = null;
        MvcResult result = null;
        String content = null;

        for (int i = 0; i < searchTerms.length; i++) {

            url = "/refset/" + mainTestingRefsetInternalId
                    + "/conceptSearch?limit=500&offset=0&query=" + searchTerms[i];

            logger.info("Testing term - " + searchTerms[i]);
            logger.info("Testing url - " + url);
            result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);
            ConceptResultList members =
                    new ObjectMapper().readValue(content, (ConceptResultList.class));

            // Testing Results
            assertThat(members).isNotNull();
            assertThat(members.getItems().size()).isGreaterThan(0);

            Concept concept = null;

            for (Concept conceptBeingTested : members.getItems()) {
                if (conceptBeingTested.getCode().equals(CONCEPT_SEARCH_CONCEPT_ID)) {
                    concept = conceptBeingTested;
                    break;
                }
            }
            // Doesn't include membership status nor memberEffectiveTime
            // validateConcept(concept, CONCEPT_SEARCH_CONCEPT_ID, null, false,
            // conceptSearchDescList, 0, -1, 0);
        }
    }

    /**
     * Test getting concept details.
     *
     * @throws Exception the exception
     */
    @Test
    public void testInactiveMembers() throws Exception {
        String url = null;
        MvcResult result = null;
        String content = null;
        Concept inactiveConcept = null;

        /*
         * List member - Test no failure when populating table with inactive
         * concept
         */

        /*
         * TODO: Uncomment once Kai issue worked out
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
        final Map<String, String> parChildMap = new HashMap<>();
        parChildMap.put(SNOMED_ROOT, "105590001");
        parChildMap.put("105590001", "115668003");
        parChildMap.put("115668003", "289958009");
        parChildMap.put("289958009", "256363008");
        parChildMap.put("256363008", "105899005");
        parChildMap.put("105899005", FIRST_CONCEPT_ID);

        Concept childConcept = null;

        String parentId = SNOMED_ROOT;
        while (childConcept == null || !FIRST_CONCEPT_ID.equals(childConcept.getCode())) {
            final String childId = parChildMap.get(parentId);
            logger.info("Testing parentID: " + parentId + " and childId: " + childId);

            final String url = "/refset/" + mainTestingRefsetInternalId
                    + "/members?limit=500&offset=0&displayType=taxonomy&startingConceptId="
                    + parentId + "&language=nl-X-31000172101";
            logger.info("Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);
            ConceptResultList children =
                    new ObjectMapper().readValue(content, (ConceptResultList.class));

            // Find Child
            childConcept = null;
            for (Concept child : children.getItems()) {

                if (child.getCode().equals(childId)) {

                    // assertThat(child.getHasDescendantRefsetMembers()).isTrue();
                    childConcept = child;
                    break;
                }
            }

            assertThat(childConcept).isNotNull();
            parentId = childId;
        }

        // pull out the descriptions needed
        final List<String> descriptionList = new ArrayList<>();
        descriptionList.add(firstConceptDescList.get(3));
        descriptionList.add(firstConceptDescList.get(0));

        // Expected concept found
        validateConcept(childConcept, FIRST_CONCEPT_ID, "20200315", true, descriptionList, 0, 0, 0,
                false);

        // Test bad root
        final String url = "/refset/" + INVALID_INTERNAL_REFSET_ID
                + "/members?limit=500&offset=0&displayType=taxonomy&startingConceptId="
                + SNOMED_ROOT;
        logger.info("Testing url - " + url);

        final MvcResult result =
                mvc.perform(get(url)).andExpect(status().is5xxServerError()).andReturn();
        final String content = result.getResponse().getContentAsString();

        assertThat(content).isEmpty();
    }

    /**
     * Test getting taxonomy children.
     *
     * @throws Exception the exception
     */
    @Test
    public void testTaxonomyParents() throws Exception {

        // First concept should have 2 parents
        Concept parentConcept = null;

        String url = "/refset/" + mainTestingRefsetInternalId
                + "/members?limit=500&offset=0&displayType=taxonomy&startingConceptId="
                + FIRST_CONCEPT_ID + "&language=nl-X-31000172101&returnChildren=false";
        logger.info("Testing url - " + url);

        MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        String content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        ConceptResultList parents =
                new ObjectMapper().readValue(content, (ConceptResultList.class));

        assertThat(parents.getItems()).isNotNull();
        assertThat(parents.getItems().size()).isEqualTo(2);

        // Find Parent
        for (Concept parent : parents.getItems()) {

            if (parent.getCode().equals(FIRST_CONCEPT_PARENT_ID)) {

                // assertThat(child.getHasDescendantRefsetMembers()).isTrue();
                parentConcept = parent;
                break;
            }
        }

        assertThat(parentConcept).isNotNull();

        // pull out the descriptions needed
        final List<String> descriptionList = new ArrayList<>();
        descriptionList.add(firstConceptParentDescList.get(3));
        descriptionList.add(firstConceptParentDescList.get(0));

        // Expected concept found
        validateConcept(parentConcept, FIRST_CONCEPT_PARENT_ID, null, false, descriptionList, 0, 0,
                0, false);

        // Test bad root
        url = "/refset/" + INVALID_INTERNAL_REFSET_ID
                + "/members?limit=500&offset=0&displayType=taxonomy&startingConceptId="
                + SNOMED_ROOT;
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().is5xxServerError()).andReturn();
        content = result.getResponse().getContentAsString();

        assertThat(content).isEmpty();
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

        final String url =
                "/refset/" + inactiveConceptRefsetInternalId + "/member/" + INACTIVE_CONCEPT_ID;
        logger.info("Testing url - " + url);

        final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        final String content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);

        ResultList<Map<String, String>> memberHistory = new ObjectMapper().readValue(content,
                (new TypeReference<ResultList<Map<String, String>>>() {
                    /* NA */}));

        assertThat(memberHistory).isNotNull();
        assertThat(memberHistory.getTotal()).isEqualTo(2);

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
     * Test the RTT Migration **** DO NOT CHECK THIS IN WITH @Test UNCOMMENTED.
     *
     * @throws Exception the exception
     */
    // **** DO NOT CHECK THIS IN WITH @Test UNCOMMENTED ****
    // @Test
    public void testRttMigration() throws Exception {

        final String url = "/admin/migration/rtt";
        logger.info("Testing url - " + url);
        final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        final String content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);

        assertThat(content).isEqualTo("RTT data migration completed successfully");
    }

    /**
     * Test getting list of version statuses.
     *
     * @throws Exception the exception
     */
    @Test
    public void testVersionStatuses() throws Exception {

        String url = null;
        MvcResult result = null;
        String content = null;

        url = baseUrl + "/versionStatuses";
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
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
    public void testNumberOfVersionsAcrossRefsets() throws Exception {

        String url = null;
        MvcResult result = null;
        String content = null;

        url = baseUrl + "/versions";
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        ResultList<TypeKeyValue> versions = new ObjectMapper().readValue(content,
                (new TypeReference<ResultList<TypeKeyValue>>() {
                    /* NA */}));
        assertThat(versions).isNotNull();
        assertThat(versions.getTotal()).isGreaterThan(20);

    }

    /**
     * Test getting list of version statuses.
     * @return
     *
     * @throws Exception the exception
     */
    @Test
    public void testVersionsAcrossRefsets() throws Exception {
        String url = null;
        MvcResult result = null;
        String content = null;
        Concept matchedConcept = null;
        ConceptResultList members = null;

        String origRefsetVersionId = internalidGetterUtil.getRefsetInternalId(INACTIVE_REFSET_ID,
                INACTIVE_REFSET_DIFFERENT_VERSION);

        /*
         * Testing across concept details
         */
        url = "/concept/" + INACTIVE_CONCEPT_ID + "?refsetInternalId=" + origRefsetVersionId;
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        Concept origConcept = new ObjectMapper().readValue(content, Concept.class);

        // Was active in Orig Version
        assertThat(origConcept.isActive()).isTrue();

        url = "/concept/" + INACTIVE_CONCEPT_ID + "?refsetInternalId="
                + inactiveConceptRefsetInternalId;
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        Concept latestConcept = new ObjectMapper().readValue(content, Concept.class);

        // Inactivated in latest Version
        assertThat(latestConcept.isActive()).isFalse();

        /*
         * In Taxonomy
         */
        url = "/refset/" + origRefsetVersionId
                + "/members?limit=500&offset=0&displayType=taxonomy&startingConceptId="
                + INACTIVE_CONCEPT_ID + "&refsetInternalId=" + origRefsetVersionId;
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        ConceptResultList children =
                new ObjectMapper().readValue(content, (ConceptResultList.class));

        // Orig version was a child of INACTIVE_CONCEPT_PARENT_ID
        assertThat(children).isNotNull();
        assertThat(children.getItems().isEmpty()).isTrue();

        url = "/refset/" + inactiveConceptRefsetInternalId
                + "/members?limit=500&offset=0&displayType=taxonomy&startingConceptId="
                + INACTIVE_CONCEPT_ID + "&refsetInternalId=" + inactiveConceptRefsetInternalId;
        logger.info("Testing url - " + url);

        // This should throw an exception to detect if we get here somehow
        result = mvc.perform(get(url)).andExpect(status().is5xxServerError()).andReturn();
        content = result.getResponse().getContentAsString();

        assertThat(content).isEmpty();

        /*
         * In Taxonomy Search
         */

        url = "/refset/" + origRefsetVersionId + "/taxonomySearch?limit=500&offset=0&query="
                + INACTIVE_CONCEPT_ID;

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);

        members = new ObjectMapper().readValue(content, (ConceptResultList.class));

        matchedConcept = null;
        for (Concept conceptBeingTested : members.getItems()) {
            if (conceptBeingTested.getCode().equals(INACTIVE_CONCEPT_ID)) {
                matchedConcept = conceptBeingTested;
                break;
            }
        }

        // Taxonomy Search returns active member
        assertThat(matchedConcept).isNotNull();

        url = "/refset/" + inactiveConceptRefsetInternalId
                + "/taxonomySearch?limit=500&offset=0&query=" + INACTIVE_CONCEPT_ID;

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);

        members = new ObjectMapper().readValue(content, (ConceptResultList.class));

        // Testing Results
        assertThat(members).isNotNull();
        assertThat(members.getItems().isEmpty()).isTrue();

        /*
         * In List
         */
        // TODO: Add once Kai resolved the 10k issue

        /*
         * In List Search
         */
        url = "/refset/" + origRefsetVersionId + "/members?limit=500&offset=0&query="
                + INACTIVE_CONCEPT_ID + "&displayType=list&refsetInternalId=" + origRefsetVersionId;

        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        members = new ObjectMapper().readValue(content, (ConceptResultList.class));

        // Testing Results
        assertThat(members).isNotNull();

        matchedConcept = null;
        for (Concept conceptBeingTested : members.getItems()) {
            if (conceptBeingTested.getCode().equals(INACTIVE_CONCEPT_ID)) {
                matchedConcept = conceptBeingTested;
                break;
            }
        }

        // Validate concept returned and is active
        assertThat(matchedConcept).isNotNull();
        assertThat(matchedConcept.isActive()).isTrue();

        url = "/refset/" + inactiveConceptRefsetInternalId + "/members?limit=500&offset=0&query="
                + INACTIVE_CONCEPT_ID + "&displayType=list&refsetInternalId="
                + inactiveConceptRefsetInternalId;

        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        members = new ObjectMapper().readValue(content, (ConceptResultList.class));

        // Testing Results
        assertThat(members).isNotNull();

        matchedConcept = null;
        for (Concept conceptBeingTested : members.getItems()) {
            if (conceptBeingTested.getCode().equals(INACTIVE_CONCEPT_ID)) {
                matchedConcept = conceptBeingTested;
                break;
            }
        }

        // TODO - FIND INACTIVE CONCEPT THAT IS ACTIVE REFSET MEMBER
        assertThat(matchedConcept).isNull();
        // Validate concept returned and is inactive
        // assertThat(matchedConcept).isNotNull();
        // assertThat(matchedConcept.isActive()).isFalse();

    }
}
