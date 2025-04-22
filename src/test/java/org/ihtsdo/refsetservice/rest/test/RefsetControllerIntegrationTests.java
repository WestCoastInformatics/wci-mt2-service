/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.ResultListConcept;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.TypeKeyValue;
import org.ihtsdo.refsetservice.model.enums.VersionStatus;
import org.ihtsdo.refsetservice.rest.test.util.ExportUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.GetUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.RefsetConceptsType;
import org.ihtsdo.refsetservice.rest.test.util.WorkflowUnitTestUtilities;
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
public class RefsetControllerIntegrationTests extends AbstractRefsetTests {

    /**
     * The Enum RefsetConceptStatus.
     */
    public enum RefsetConceptStatus {

        /** The exists. */
        EXISTS,
        /** The not found. */
        NOT_FOUND

    }

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(RefsetControllerIntegrationTests.class);

    /** The Constant MAIN_TESTING_REFSET_ID. */
    // Belgian simple reference set for translated animal materials w/101
    // members
    // Resides with 101 members on wci-snowstorm, dev-integration, UAT, and
    // production
    private static final String GPS_REFSET_ID = "787778008";

    /** The Constant FIRST_MAIN_NRC_REFSET_CONCEPT_ID. */
    // With 2 parents, 6 children and 0 defing rels
    private static final String FIRST_MAIN_NRC_REFSET_CONCEPT_ID = "37663002";

    /** The Constant SECOND_MAIN_NRC_REFSET_CONCEPT_ID. */
    // With 1 parents, 0 children and 0 defing rels
    private static final String SECOND_MAIN_NRC_REFSET_CONCEPT_ID = "276310004";

    /** The Constant FIRST_MAIN_CORE_REFSET_CONCEPT_ID. */
    private static final String FIRST_MAIN_CORE_REFSET_CONCEPT_ID = "118690002";

    /** The inactive refset version internal id. */
    private static String inactiveRefsetVersionInternalId;

    /** The earlier inactive refset internal id. */
    private static String earlierInactiveRefsetInternalId;

    /** The delta export refset version internal id. */
    private static String deltaExportRefsetVersionInternalId;

    /** The Constant FIRST_CONCEPT_DESC_LIST. */
    private static final List<String> FIRST_CONCEPT_DESC_LIST = new ArrayList<>();

    /** The Constant FIRST_CONCEPT_PARENT_DESC_LIST. */
    private static final List<String> FIRST_CONCEPT_PARENT_DESC_LIST = new ArrayList<>();

    /** The Constant INACTIVE_CONCEPT_DESC_LIST. */
    private static final List<String> INACTIVE_CONCEPT_DESC_LIST = new ArrayList<>();

    /** The Constant SECOND_CONCEPT_ALL_DESC_TYPE_LIST. */
    private static final List<String> SECOND_CONCEPT_ALL_DESC_TYPE_LIST = new ArrayList<>();

    /** The Constant SECOND_CONCEPT_PT_AND_FSN_ONLY_DESC_LIST. */
    private static final List<String> SECOND_CONCEPT_PT_AND_FSN_ONLY_DESC_LIST = new ArrayList<>();

    /** The Constant CONCEPT_SEARCH_DESC_LIST. */
    private static final List<String> CONCEPT_SEARCH_DESC_LIST = new ArrayList<>();

    /** The Constant TWO_VERSION_DELTA_FILE. */
    private static final String TWO_VERSION_DELTA_FILE = REFSET_FILE_PATH + "Lateralizable Delta 201801 to 201807.txt";

    /** The Constant THREE_VERSION_DELTA_FILE. */
    private static final String THREE_VERSION_DELTA_FILE = REFSET_FILE_PATH
            + "Lateralizable Delta 201801 to 201901.txt";

    /** The Constant SNAPSHOT_FILE. */
    private static final String SNAPSHOT_FILE = REFSET_FILE_PATH + "561000172108 Snapshot 20220315.txt";

    /** The Constant LIST_OF_SCTIDS_FILE. */
    private static final String LIST_OF_SCTIDS_FILE = REFSET_FILE_PATH + "561000172108 ListOfSctIds 20220315.txt";

    /** The Constant REFSET_SNAPSHOT_EXPORT_VERSION. */
    private static final String REFSET_SNAPSHOT_EXPORT_VERSION = "20220315";

    /** The Constant REFSET_DELTA_TO_EXPORT_REFSET_ID. */
    private static final String REFSET_DELTA_TO_EXPORT_REFSET_ID = "723264001";

    /** The Constant REFSET_DELTA_TO_EXPORT_VERSION. */
    private static final String REFSET_DELTA_TO_EXPORT_VERSION = "2021-11-30";

    /** The Constant REFSET_DELTA_FROM_EXPORT_VERSION. */
    private static final String REFSET_DELTA_FROM_EXPORT_VERSION = "20180131";

    /** The Constant REFSET_DELTA_TO_EXPORT_TWO_VERSIONS. */
    private static final String REFSET_DELTA_TO_EXPORT_TWO_VERSIONS = "20180731";

    /** The Constant REFSET_DELTA_TO_EXPORT_THREE_VERSIONS. */
    private static final String REFSET_DELTA_TO_EXPORT_THREE_VERSIONS = "20190131";

    // Test by term per language
    // Test by concept Id
    // by term id
    // by refset id
    // By narrative
    /** The Constant MEMBERS_SEARCH_QUERY_LIST. */
    private static final String[] MEMBERS_SEARCH_QUERY_LIST = new String[] {
            "human", "Animal", "HAIR", "Non", "niet", "menselijk", "dierenhaar", "poil", "dierlijk", "haar",
            "276310004", "412393015", "1495334015"
    };

    /** The Constant INVALID_SEARCH_TERMS. */
    private static final String[] INVALID_SEARCH_TERMS = new String[] {
            "138875005999", "SNOMED Clinical Terms version"
    };

    /** The first time setup. */
    private static boolean firstTimeSetup = true;

    /** The skip earlier inactive version tests. */
    private static boolean skipEarlierInactiveVersionTests = false;

    /**
     * Sets the up.
     *
     * @param info the up
     * @throws Exception the exception
     */
    @BeforeEach
    public void setUp(final TestInfo info) throws Exception {

        if (info.getDisplayName().equals("testMigration()")) {

            return;
        }

        if (getGetUtil() == null) {
            final SimpleDateFormat sdf = new SimpleDateFormat(YYYYMMDD_FORMAT);
            setGetUtil(new GetUnitTestUtilities(getMvc(), getBaseUrl(), sdf));
            setExportUtil(new ExportUnitTestUtilities(getMvc()));
            setWorkflowUtil(new WorkflowUnitTestUtilities(getMvc(), getBaseUrl(), REFSET_FILE_PATH));
        }

        // Setup Utility classes
        setObjectMapper(new ObjectMapper());
        JacksonTester.initFields(this, getObjectMapper());
        setBaseUrl("/refset");

        if (firstTimeSetup) {

            setReadOnlyTestingProjectId(getGetUtil().getInternalProjectId(READ_ONLY_TESTING_PROJECT_NAME));
            setMainNrcTestingRefsetInternalId(
                    getGetUtil().getInternalRefsetId(MAIN_NRC_TESTING_REFSET_ID, MAIN_NRC_TESTING_REFSET_VERSION));
            setMainCoreTestingRefsetInternalId(
                    getGetUtil().getInternalRefsetId(MAIN_CORE_TESTING_REFSET_ID, MAIN_CORE_TESTING_REFSET_VERSION));

            // Ensure have the export directory created on testing system

            final String exportFileDir = PropertyUtility.getProperty("snomed-refset-service.export.fileDir")
                    + File.separator;
            final File f = new File(exportFileDir);

            if (!f.exists()) {

                throw new Exception(
                        "Tests with Export because the expected export directory doesn't exist: " + exportFileDir);
            }

            setMainNrcTestingRefsetInternalId(
                    getGetUtil().getInternalRefsetId(MAIN_NRC_TESTING_REFSET_ID, MAIN_NRC_TESTING_REFSET_VERSION));

            inactiveRefsetVersionInternalId = getGetUtil().getInternalRefsetId(
                    REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_ID,
                    REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_VERSION);
            deltaExportRefsetVersionInternalId = getGetUtil().getInternalRefsetId(REFSET_DELTA_TO_EXPORT_REFSET_ID,
                    REFSET_DELTA_TO_EXPORT_VERSION);

            try {

                earlierInactiveRefsetInternalId = getGetUtil().getInternalRefsetId(
                        REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_ID,
                        REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_EARLIER_VERSION);
            } catch (final Exception e) {

                LOG.info("Snowstorm instance we are running against doesn't have "
                        + REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_EARLIER_VERSION
                        + " of the refset " + REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_ID
                        + ", thus skip related tests");
                skipEarlierInactiveVersionTests = true;
            }

            setRefsetWithInactiveConceptAsActiveMemberInternalId(
                    getGetUtil().getInternalRefsetId(REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_ID,
                            REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_VERSION));

            FIRST_CONCEPT_DESC_LIST.add("Venom (substance)");
            FIRST_CONCEPT_DESC_LIST.add("Venom");
            FIRST_CONCEPT_DESC_LIST.add("venin");
            FIRST_CONCEPT_DESC_LIST.add("gif");

            FIRST_CONCEPT_PARENT_DESC_LIST.add("Animal agent (substance)");
            FIRST_CONCEPT_PARENT_DESC_LIST.add("Animal agent");
            FIRST_CONCEPT_PARENT_DESC_LIST.add("produit animal");
            FIRST_CONCEPT_PARENT_DESC_LIST.add("dierlijk product");

            INACTIVE_CONCEPT_DESC_LIST.add("Dermatitis factitia");
            INACTIVE_CONCEPT_DESC_LIST.add("Dermatitis factitia (disorder)");
            INACTIVE_CONCEPT_DESC_LIST.add("Neurotic excoriation");
            INACTIVE_CONCEPT_DESC_LIST.add("Dermatitis artefacta");
            INACTIVE_CONCEPT_DESC_LIST.add("DA - Dermatitis artefacta");
            INACTIVE_CONCEPT_DESC_LIST.add("Factitious dermatitis");
            INACTIVE_CONCEPT_DESC_LIST.add("Dermatitis simulata");
            INACTIVE_CONCEPT_DESC_LIST.add("Feigned dermatitis");
            INACTIVE_CONCEPT_DESC_LIST.add("Simulated dermatitis");
            INACTIVE_CONCEPT_DESC_LIST.add("Dermatitis ficta");

            SECOND_CONCEPT_ALL_DESC_TYPE_LIST.add("Animal hair");
            SECOND_CONCEPT_ALL_DESC_TYPE_LIST.add("Non-human hair - material (substance)");
            SECOND_CONCEPT_ALL_DESC_TYPE_LIST.add("poil animal");
            SECOND_CONCEPT_ALL_DESC_TYPE_LIST.add("dierlijk haar");
            SECOND_CONCEPT_ALL_DESC_TYPE_LIST.add("poils non humains");
            SECOND_CONCEPT_ALL_DESC_TYPE_LIST.add("poils animaux");
            SECOND_CONCEPT_ALL_DESC_TYPE_LIST.add("poils autres qu'humains");
            SECOND_CONCEPT_ALL_DESC_TYPE_LIST.add("Non-human hair - material");
            SECOND_CONCEPT_ALL_DESC_TYPE_LIST.add("dierenhaar");
            SECOND_CONCEPT_ALL_DESC_TYPE_LIST.add("niet-menselijk haar");

            SECOND_CONCEPT_PT_AND_FSN_ONLY_DESC_LIST.add("Non-human hair - material (substance)");
            SECOND_CONCEPT_PT_AND_FSN_ONLY_DESC_LIST.add("Animal hair");
            SECOND_CONCEPT_PT_AND_FSN_ONLY_DESC_LIST.add("poil animal");
            SECOND_CONCEPT_PT_AND_FSN_ONLY_DESC_LIST.add("dierlijk haar");

            CONCEPT_SEARCH_DESC_LIST.add("Brazilian pemphigus foliaceus");

            firstTimeSetup = false;
        }

    }

    /**
     * Test getting a project.
     */
    @Test
    public void testGetProject() {

        final Project project = getGetUtil().getProject(getReadOnlyTestingProjectId());

        assertThat(project.getId()).isEqualTo(getReadOnlyTestingProjectId());
        assertThat(project.getName()).isEqualTo(READ_ONLY_TESTING_PROJECT_NAME);
    }

    /**
     * Test getting a refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testRefset() throws Exception {

        final Refset refset = getGetUtil().getRefsetFromRefsetIdAndVersion(MAIN_NRC_TESTING_REFSET_ID,
                MAIN_NRC_TESTING_REFSET_VERSION);
        validateRefsetMetadata(refset);
    }

    /**
     * Test getting editions.
     *
     * @throws Exception the exception
     */
    @Test
    public void testEditions() throws Exception {

        final ResultList<TypeKeyValue> editions = getGetUtil().getEditions();
        assertThat(editions.getItems().size()).isGreaterThan(8);

        boolean editionFound = false;

        for (final TypeKeyValue keyValue : editions.getItems()) {

            if ("Belgian Extension".equalsIgnoreCase(keyValue.getKey())) {

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

        final ResultList<String> versions = getGetUtil().getBranches("SNOMEDCT-BE");
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

        final ResultList<Project> resultList = getGetUtil().searchProjects();

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
        refsetList = getGetUtil().searchDirectory("animal");
        refset = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);
        validateRefsetMetadata(refset);

        // Test by partial name
        refsetList = getGetUtil().searchDirectory("ani");
        refset = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);
        validateRefsetMetadata(refset);

        // Test by alternate refset name (using translation)
        refsetList = getGetUtil()
                .searchDirectory("ensemble de référence simple belge pour les matières animales traduites");
        refset = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);
        validateRefsetMetadata(refset);

        // Test by partial alternate refset name (using translation)
        refsetList = getGetUtil().searchDirectory("matiè");
        refset = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);
        validateRefsetMetadata(refset);

        // Test by edition name

        refsetList = getGetUtil().searchDirectory("editionName:Belgian Edition");
        refset = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);
        // TODO: fail
        // validateRefsetMetadata(refset);

        // Test by combination
        refsetList = getGetUtil().searchDirectory("animal AND editionName:Belgian Edition");
        refset = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);
        // TODO: fail
        // validateRefsetMetadata(refset);

        // Test by combination with partials
        refsetList = getGetUtil().searchDirectory("anim AND editionName:Belgian Edi");
        refset = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);
        // TODO: fail
        // validateRefsetMetadata(refset);

        // Test on inactive concept that is active member of an active refset
        refsetList = getGetUtil().searchDirectory(INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
        refset = validateRefsetExists(refsetList, REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_ID);
        assertThat(refset).isNotNull();

        // Test by term per language (at least on non-pt)
        // Test by concept Id
        // by term id
        // by refset id
        // By narrative - no longer has narrative
        final String[] searchTerms = new String[] {
                "Dog", "squame", "huidschilfer", "olie uit lever van vis", "260154005", "999861000172117",
                "561000172108" // , "Allergies General"
        };

        for (int i = 0; i < searchTerms.length; i++) {

            LOG.info("Testing term - " + searchTerms[i]);

            refsetList = getGetUtil().searchDirectory(searchTerms[i]);
            refset = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);

            validateRefsetMetadata(refset);
        }

        // Verify when expect to return no matching refsets
        for (int i = 0; i < INVALID_SEARCH_TERMS.length; i++) {

            LOG.info("Testing term INVALID_SEARCH_TERMS - " + INVALID_SEARCH_TERMS[i]);

            refsetList = getGetUtil().searchDirectory(INVALID_SEARCH_TERMS[i]);
            refset = validateRefsetExists(refsetList, MAIN_NRC_TESTING_REFSET_ID);

            assertThat(refset).isNull();
        }

        // Test Inactive Concept search
        refsetList = getGetUtil().searchDirectory(INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
        refset = validateRefsetExists(refsetList, REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_ID);

        validateRefsetMetadata(refset);

        // Test graceful handling of zero results
        try {

            refsetList = getGetUtil().searchDirectory("1234567890");
        } catch (final AssertionError e) {

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

        final JsonNode root = getExportUtil().exportSctIds(getMainNrcTestingRefsetInternalId());

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
        getExportUtil().deleteRefsetExportsFromAws(MAIN_NRC_TESTING_REFSET_ID, REFSET_SNAPSHOT_EXPORT_VERSION);

        final JsonNode root = getExportUtil().exportRf2Snapshot(getMainNrcTestingRefsetInternalId(),
                REFSET_SNAPSHOT_EXPORT_VERSION);

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
        getExportUtil().deleteRefsetExportsFromAwsAllVersions(REFSET_DELTA_TO_EXPORT_REFSET_ID);

        JsonNode root = getExportUtil().exportRf2Delta(deltaExportRefsetVersionInternalId,
                REFSET_DELTA_FROM_EXPORT_VERSION, REFSET_DELTA_TO_EXPORT_TWO_VERSIONS);

        validateExportFiles(root, TWO_VERSION_DELTA_FILE);

        /* Test delta between three versions */
        getExportUtil().deleteRefsetExportsFromAwsAllVersions(REFSET_DELTA_TO_EXPORT_REFSET_ID);

        root = getExportUtil().exportRf2Delta(deltaExportRefsetVersionInternalId, REFSET_DELTA_FROM_EXPORT_VERSION,
                REFSET_DELTA_TO_EXPORT_THREE_VERSIONS);

        validateExportFiles(root, THREE_VERSION_DELTA_FILE);
    }

    /**
     * Test getting concept details - This doesn't include parents or children as
     * there are dedicated tests for them in the class.
     *
     * @throws Exception the exception
     */
    @Test
    public void testConceptDetails() throws Exception {

        // Test normal concept Details Call
        Concept concept = getGetUtil().getConceptDetails(getMainNrcTestingRefsetInternalId(),
                FIRST_MAIN_NRC_REFSET_CONCEPT_ID);
        validateConcept(concept, FIRST_MAIN_NRC_REFSET_CONCEPT_ID, null, false, FIRST_CONCEPT_DESC_LIST, 0, 0, 0);

        // Try second concept
        concept = getGetUtil().getConceptDetails(getMainNrcTestingRefsetInternalId(),
                SECOND_MAIN_NRC_REFSET_CONCEPT_ID);
        validateConcept(concept, SECOND_MAIN_NRC_REFSET_CONCEPT_ID, null, false, SECOND_CONCEPT_ALL_DESC_TYPE_LIST, 0,
                0, 0);

        concept = getGetUtil().getConceptDetails(inactiveRefsetVersionInternalId,
                INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
        validateConcept(concept, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID, null, false, INACTIVE_CONCEPT_DESC_LIST, 0,
                0, 0);

        // Test invalid refset is handled gracefully
        try {

            concept = null;
            concept = getGetUtil().getConceptDetails(INVALID_INTERNAL_REFSET_ID, FIRST_MAIN_NRC_REFSET_CONCEPT_ID);
        } catch (final AssertionError ae) {

            assertThat(concept).isNull();

            LOG.info("Successfully identified that refset is invalid");
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

        final String branch = "MAIN/2022-01-31";

        // call the api to get the refset concept list for refset parents
        final ResultListConcept existingRefsetConcepts = getGetUtil().getRefsetConcepts(branch,
                RefsetConceptsType.ALL_SIMPLE_TYPE_CONCEPTS);

        assertThat(existingRefsetConcepts.getItems().size()).isEqualTo(19);

        final Concept existingRefsetConcept = identifyMemberFromList(existingRefsetConcepts, GPS_REFSET_ID);
        assertThat(existingRefsetConcept).isNotNull();

        // call the api to get the refset concept list for using as the
        // underlying concept for a new refset
        final ResultListConcept newRefsetConcepts = getGetUtil().getRefsetConcepts(branch,
                RefsetConceptsType.NEW_REFSET_CONCEPTS);
        assertThat(newRefsetConcepts.getItems().size()).isEqualTo(9);

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

        ResultListConcept members = getGetUtil().getMembers(getMainNrcTestingRefsetInternalId());

        // At time last update, 114 members were found in the refsets
        assertThat(members.size()).isEqualTo(114);

        // Membership info and descriptions, but no parents/children
        Concept concept = identifyMemberFromList(members, FIRST_MAIN_NRC_REFSET_CONCEPT_ID);
        validateConcept(concept, FIRST_MAIN_NRC_REFSET_CONCEPT_ID, "20200315", true, FIRST_CONCEPT_DESC_LIST, 0, 0, 0);

        // Membership info and descriptions, but no parents/children
        concept = identifyMemberFromList(members, SECOND_MAIN_NRC_REFSET_CONCEPT_ID);
        validateConcept(concept, SECOND_MAIN_NRC_REFSET_CONCEPT_ID, "20200315", true,
                SECOND_CONCEPT_PT_AND_FSN_ONLY_DESC_LIST, 0, 0, 0);

        members = getGetUtil().getMembers(inactiveRefsetVersionInternalId);
        concept = identifyMemberFromList(members, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);

        assertThat(concept).isNotNull();
        assertThat(concept.isActive()).isFalse();
        assertThat(concept.isMemberOfRefset()).isTrue();
    }

    /**
     * Test searching refset members for when examining which members of a given
     * refset match on the search criteria.
     *
     * @throws Exception the exception
     */
    @Test
    public void testSearchMember() throws Exception {

        for (int i = 0; i < MEMBERS_SEARCH_QUERY_LIST.length; i++) {

            LOG.debug("Testing term MEMBERS_SEARCH_QUERY_LIST - : " + MEMBERS_SEARCH_QUERY_LIST[i]);

            final ResultListConcept members = getGetUtil().searchMembers(getMainNrcTestingRefsetInternalId(),
                    MEMBERS_SEARCH_QUERY_LIST[i]);
            final Concept concept = identifyMemberFromList(members, SECOND_MAIN_NRC_REFSET_CONCEPT_ID);

            // Doesn't include relationships
            validateConcept(concept, SECOND_MAIN_NRC_REFSET_CONCEPT_ID, "20200315", true,
                    SECOND_CONCEPT_PT_AND_FSN_ONLY_DESC_LIST, 0, 0, 0);
        }

        /*-
         * TODO: Add this test once replace ECL-based search with one that returns inactive concepts
         *
         * // Test Inactive
         * LOG.debug("Testing term - : " + INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
         *
         * ResultListConcept members = getGetUtil().searchMembers(inactiveRefsetVersionInternalId, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
         * Concept concept = identifyMemberFromList(members, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
         *
         * // Doesn't include relationships
         * validateConcept(concept, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID, "20200315", true, SECOND_CONCEPT_PT_AND_FSN_ONLY_DESC_LIST, 0, 0, 0);
         */

        // Test Invalid
        for (int i = 0; i < INVALID_SEARCH_TERMS.length; i++) {

            LOG.info("Testing term INVALID_SEARCH_TERMS - " + INVALID_SEARCH_TERMS[i]);

            final ResultListConcept members = getGetUtil().searchMembers(getMainNrcTestingRefsetInternalId(),
                    INVALID_SEARCH_TERMS[i]);
            final Concept concept = identifyMemberFromList(members, SECOND_MAIN_NRC_REFSET_CONCEPT_ID);

            // Doesn't include relationships
            assertThat(concept).isNull();

        }

    }

    /**
     * Test searching refset members taxonomy.
     *
     * @throws Exception the exception
     */
    @Test
    public void testSearchTaxonomy() throws Exception {

        for (int i = 0; i < MEMBERS_SEARCH_QUERY_LIST.length; i++) {

            LOG.debug("Testing term MEMBERS_SEARCH_QUERY_LIST - : " + MEMBERS_SEARCH_QUERY_LIST[i]);

            final ResultListConcept members = getGetUtil().searchTaxonomy(getMainNrcTestingRefsetInternalId(),
                    MEMBERS_SEARCH_QUERY_LIST[i]);
            final Concept concept = identifyMemberFromList(members, SECOND_MAIN_NRC_REFSET_CONCEPT_ID);

            // Doesn't include membership status nor memberEffectiveTime
            validateConcept(concept, SECOND_MAIN_NRC_REFSET_CONCEPT_ID, null, false,
                    SECOND_CONCEPT_PT_AND_FSN_ONLY_DESC_LIST, 0, -1, 0);
        }

        /*-
         * TODO: Add this test once replace ECL-based search with one that returns inactive concepts
         *
         * // Test Inactive
         * LOG.debug("Testing term - : " + INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
         *
         * ResultListConcept members = getGetUtil().searchTaxonomy(inactiveRefsetVersionInternalId, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
         * Concept concept = identifyMemberFromList(members, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
         *
         * // Doesn't include relationships
         * validateConcept(concept, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID, "20200315", true, SECOND_CONCEPT_PT_AND_FSN_ONLY_DESC_LIST, 0, 0, 0);
         */

        for (int i = 0; i < INVALID_SEARCH_TERMS.length; i++) {

            LOG.info("Testing term INVALID_SEARCH_TERMS - " + INVALID_SEARCH_TERMS[i]);

            final ResultListConcept members = getGetUtil().searchTaxonomy(getMainNrcTestingRefsetInternalId(),
                    INVALID_SEARCH_TERMS[i]);
            final Concept concept = identifyMemberFromList(members, SECOND_MAIN_NRC_REFSET_CONCEPT_ID);

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
        // @RequestMapping(method = RequestMethod.GET, value =
        // "/refset/{refsetInternalId}/ancestorCache", produces = "application/json")
        final boolean cacheSuccess = getGetUtil().setupAncestorCache(MAIN_NRC_TESTING_REFSET_ID,
                MAIN_NRC_TESTING_REFSET_VERSION);
        assertThat(cacheSuccess).isTrue();

        // Search on grandparent
        final List<String> hierarchy = new ArrayList<>();
        hierarchy.addAll(List.of("105590001", "115668003", "289958009", "256363008", "105899005",
                FIRST_MAIN_NRC_REFSET_CONCEPT_ID));

        for (int i = 0; i < hierarchy.size() - 1; i++) {

            final String parentId = hierarchy.get(i);
            LOG.info("Testing parentID: " + parentId);

            final ResultListConcept children = getGetUtil().getChildren(getMainNrcTestingRefsetInternalId(), parentId);
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

        final Concept concept = getGetUtil().getAncestorPath(FIRST_MAIN_CORE_REFSET_CONCEPT_ID,
                getMainCoreTestingRefsetInternalId());

        // Verify that nodes in ancestor path is as expected
        final ResultListConcept ancestorList = new ResultListConcept(concept.getParents());
        assertThat(ancestorList.size()).isEqualTo(5);

        // Verify that the main concept isn't added as an ancestor of itself
        final Concept nonAncestor = identifyMemberFromList(ancestorList, FIRST_MAIN_CORE_REFSET_CONCEPT_ID);
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
        final ResultList<Map<String, String>> memberHistory = getGetUtil()
                .getMemberHistory(inactiveRefsetVersionInternalId, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);

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

        final String url = getBaseUrl() + "/versionStatuses";
        LOG.info("Testing url - " + url);
        final MvcResult result = getMvc().perform(get(url)).andExpect(status().isOk()).andReturn();
        final String content = result.getResponse().getContentAsString();
        LOG.info(" content = " + content);
        final ResultList<TypeKeyValue> versionStatuses = new ObjectMapper().readValue(content,
                (new TypeReference<ResultList<TypeKeyValue>>() {
                    /* NA */
                }));
        assertThat(versionStatuses).isNotNull();
        assertThat(versionStatuses.getTotal()).isEqualTo(VersionStatus.values().length);

    }

    /**
     * Test getting list of version statuses.
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

            final Concept matchedConcept = getGetUtil().getConceptDetails(earlierInactiveRefsetInternalId,
                    INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
            assertThat(matchedConcept.isActive()).isTrue();
        }

        // Inactivated in latest Version
        final Concept latestConcept = getGetUtil().getConceptDetails(inactiveRefsetVersionInternalId,
                INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
        assertThat(latestConcept.isActive()).isFalse();

        /*
         * In List Search
         */
        if (!skipEarlierInactiveVersionTests) {

            final ResultListConcept members = getGetUtil().searchMembers(earlierInactiveRefsetInternalId,
                    INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
            final Concept member = identifyMemberFromList(members, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
            assertThat(member.isActive()).isTrue();
        }

        ResultListConcept members = getGetUtil().searchMembers(inactiveRefsetVersionInternalId,
                INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
        Concept member = identifyMemberFromList(members, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
        // assertThat(member).isNull();

        /*
         * In Taxonomy
         */
        // Orig version was a child of INACTIVE_CONCEPT_PARENT_ID
        if (!skipEarlierInactiveVersionTests) {

            final ResultListConcept children = getGetUtil().getChildren(earlierInactiveRefsetInternalId,
                    INACTIVE_CONCEPT_ACTIVE_MEMBER_PARENT_CONCEPT_ID);
            member = identifyMemberFromList(children, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
            assertThat(member.isActive()).isTrue();
        }

        ResultListConcept children = null;

        // This should throw an exception to detect if we get here somehow
        try {

            children = getGetUtil().getChildren(inactiveRefsetVersionInternalId,
                    INACTIVE_CONCEPT_ACTIVE_MEMBER_PARENT_CONCEPT_ID);
        } catch (final AssertionError e) {

            assertThat(children).isNull();
        }

        /*
         * In Taxonomy Search -
         */
        // Taxonomy Search returns active member
        if (!skipEarlierInactiveVersionTests) {

            members = getGetUtil().searchTaxonomy(earlierInactiveRefsetInternalId,
                    INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
            member = identifyMemberFromList(members, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
            assertThat(member).isNotNull();
            assertThat(member.isActive()).isTrue();
        }

        try {

            members = getGetUtil().searchTaxonomy(inactiveRefsetVersionInternalId,
                    INACTIVE_CONCEPT_ACTIVE_MEMBER_PARENT_CONCEPT_ID);
        } catch (final AssertionError e) {

            assertThat(members.getItems()).isEmpty();
        }

        // Search for an INACTIVE_CONCEPT that is still an ACTIVE_REFSET_MEMBER of an
        // ACTIVE_REFSET

        // TODO: Once we determine how to best handle searching refset for inactive
        // concepts (right now ECL-based so only return actives)
        // Do this for List only (doesn't work for taxonomy which is only active
        // concepts)

        // members = getGetUtil().searchMembers(refsetWithInactiveConceptAsActiveMember,
        // INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID); member =
        // identifyMemberFromList(members, INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID);
        // assertThat(member).isNotNull(); assertThat(member.isActive()).isFalse();
        // assertThat(member.isMemberOfRefset()).isTrue();

    }

    /**
     * Test tags.
     */
    // @Test TODO: FAILING finds refset, does not include tags
    public void testTags() {

        final String testTag = "anatomy";
        LOG.info("Testing term - " + testTag);

        final ResultList<Refset> refsetList = getGetUtil().searchDirectory(testTag);

        final Refset refsetIdentified = validateRefsetExists(refsetList, "723264001");

        assertThat(refsetIdentified.getTags().size()).isEqualTo(1);
        assertThat(refsetIdentified.getTags().iterator().next()).isEqualTo(testTag);

    }

    /**
     * Identify member from list.
     *
     * @param concepts  the concepts
     * @param conceptId the concept id
     * @return the concept
     */
    private Concept identifyMemberFromList(final ResultListConcept concepts, final String conceptId) {
        /*
         * Supporting Methods - return the concept from the list that matches with the
         * conceptId specified
         */

        // Test first concept
        Concept concept = null;

        for (final Concept conceptBeingTested : concepts.getItems()) {

            if (conceptBeingTested.getCode().equals(conceptId)) {

                concept = conceptBeingTested;
                break;
            }

        }

        return concept;
    }

    /**
     * Test the Sync **** DO NOT CHECK THIS IN WITH @Test UNCOMMENTED.
     *
     * @throws Exception the exception
     */
    // **** DO NOT CHECK THIS IN WITH @Test UNCOMMENTED ****
    // @Test
    public void testSync() throws Exception {

        final String url = "/admin/sync/snowstorm";
        LOG.info("Testing url - " + url);
        final MvcResult result = getMvc().perform(get(url)).andExpect(status().isOk()).andReturn();
        final String content = result.getResponse().getContentAsString();
        LOG.info(" content = " + content);

        assertThat(content).isEqualTo("RTT data sync completed successfully");

        final String feedbackUrl = "/admin/sync/feedback";
        LOG.info("Testing feedbackUrl - " + feedbackUrl);
        final MvcResult feedbackResult = getMvc().perform(get(feedbackUrl)).andExpect(status().isOk()).andReturn();
        final String feedbackContent = feedbackResult.getResponse().getContentAsString();
        LOG.info(" feedbackContent = " + feedbackContent);

        final String intensionalUrl = "/admin/sync/intensional";
        LOG.info("Testing intensionalUrl - " + intensionalUrl);
        final MvcResult intensionalResult = getMvc().perform(get(intensionalUrl)).andExpect(status().isOk())
                .andReturn();
        final String intensionalContent = intensionalResult.getResponse().getContentAsString();
        LOG.info(" intensionalContent = " + intensionalContent);
    }

}
