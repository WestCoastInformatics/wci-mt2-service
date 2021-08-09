
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.lucene.queryparser.classic.QueryParserBase;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.TypeKeyValue;
import org.ihtsdo.refsetservice.model.VersionStatus;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for MetadataController.
 */

@AutoConfigureMockMvc
public class RefsetControllerTests extends BaseTest {

    /** The config properties. */
    private final Properties properties = PropertyUtility.getProperties();

    /** The Constant TESTING_REFSET_ID. */
    // Belgian simple reference set for translated animal materials w/101
    // members
    // Resides with 101 members on wci-snowstorm, dev-integration, UAT, and
    // production
    private static final String TESTING_REFSET_ID = "561000172108"; // Belgian

    private static final String TESTING_REFSET_BRANCH = "MAIN/SNOMEDCT-BE/2020-09-15"; // Belgian

    private static final String INACTIVE_CONCEPT_ID = "727156001";

    private static final String REFSET_WITH_INACTIVE_CONCEPT = "723264001";

    private static final String DESCRIPTION_TERM = "term";

    private static final String firstConIdToExamine = "37663002"; // With 2
                                                                  // par,
    // 6 child, 0
    // def rel

    private static final String secondConIdToExamine = "710038002"; // with 2
                                                                    // par, 0
                                                                    // child, 1
                                                                    // def rel

    private static final String expectedEffectiveTime = "20210315";

    private static final String INVALID_REFSET_ID = "12345678901234567890";

    private static final String SNOMED_ROOT = "138875005";

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(RefsetControllerTests.class);

    /** The mvc. */
    @Autowired
    private MockMvc mvc;

    /** The object mapper. */
    private ObjectMapper objectMapper;

    /** The base url. */
    private String baseUrl = "";

    /** The projects file. */
    private final String DELTA_FILE_PATH =
            "src/test/resources/refsetService/Lateralizable Delta 201801 to 201901.txt";

    private static final List<String> firstConceptDescList = new ArrayList<>();

    private static final List<String> secondConceptDescList = new ArrayList<>();

    /**
     * Sets the up.
     */
    @BeforeEach
    public void setUp() {

        objectMapper = new ObjectMapper();
        JacksonTester.initFields(this, objectMapper);
        baseUrl = "/refset";

        firstConceptDescList.add("Venom (substance)");
        firstConceptDescList.add("Venom");
        firstConceptDescList.add("venin");
        firstConceptDescList.add("gif");

        secondConceptDescList.add("Anhydrous lanolin (substance)");
        secondConceptDescList.add("Anhydrous lanolin");
        secondConceptDescList.add("Wool fat");
        secondConceptDescList.add("wolvet");
        secondConceptDescList.add("lanoline anhydre");
        secondConceptDescList.add("watervrije lanoline");

    }

    /**
     * Test getting a refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testRefset() throws Exception {

        final String refsetTerminologyId = getRefsetInternalId();

        final String url = baseUrl + "/" + refsetTerminologyId;
        logger.info("Testing url - " + url);

        final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        final String content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);

        final Refset refset = new ObjectMapper().readValue(content, Refset.class);

        testRefsetMetadata(refset);
    }

    /**
     * Test getting editions.
     *
     * @throws Exception the exception
     */
    @Test
    public void testEditions() throws Exception {

        String url = null;
        MvcResult result = null;
        String content = null;

        url = baseUrl + "/editions";
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        ResultList<TypeKeyValue> editions = new ObjectMapper().readValue(content,
                (new TypeReference<ResultList<TypeKeyValue>>() {
                    /* NA */}));
        assertThat(editions).isNotNull();
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
     * Test listing refsets.
     *
     * @throws Exception the exception
     */
    @Test
    public void testDirectorySearch() throws Exception {

        String url = null;
        MvcResult result = null;
        String content = null;
        ResultList<Refset> resultList = null;

        // Test by name
        url = baseUrl

                + "/search?limit=500&offset=0&sort=versionDate&sortAscending=false&query=name:animal";
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        resultList =
                new ObjectMapper().readValue(content, (new TypeReference<ResultList<Refset>>() {
                    /* NA */}));
        assertThat(resultList).isNotNull();
        assertThat(resultList.getItems().size()).isGreaterThanOrEqualTo(1);

        boolean refsetFound = false;
        for (Refset r : resultList.getItems()) {
            if (r.getRefsetId().equals(TESTING_REFSET_ID)) {
                refsetFound = true;
                testRefsetMetadata(r);
                break;
            }
        }

        assertThat(refsetFound).isTrue();

        // Test by edition name
        url = baseUrl
                + "/search?limit=500&offset=0&sort=versionDate&sortAscending=false&query=editionName:Belgian Edition";

        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        resultList =
                new ObjectMapper().readValue(content, (new TypeReference<ResultList<Refset>>() {
                    /* NA */}));
        assertThat(resultList).isNotNull();
        assertThat(resultList.getItems().size()).isGreaterThanOrEqualTo(1);

        refsetFound = false;
        for (Refset r : resultList.getItems()) {
            if (r.getRefsetId().equals(TESTING_REFSET_ID)) {
                refsetFound = true;
                testRefsetMetadata(r);
                break;
            }
        }

        assertThat(refsetFound).isTrue();

        // Test by combination
        url = baseUrl
                + "/search?limit=500&offset=0&sort=versionDate&sortAscending=false&query=name:animal AND editionName:Belgian Edition"; // Hyperdontia

        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        resultList =
                new ObjectMapper().readValue(content, (new TypeReference<ResultList<Refset>>() {
                    /* NA */}));
        assertThat(resultList).isNotNull();
        assertThat(resultList.getItems().size()).isEqualTo(1);
        testRefsetMetadata(resultList.getItems().get(0));

        // Test by term per language (at least on non-pt)
        // Test by concept Id
        // by term id
        // by refset id
        // By narrative
        // by tags
        String searchTerms[] = new String[] {
                "Dog", "squame", "huidschilfer",
                // TODO: Add '"olie uit lever van vis"' back when know about
                // acceptable term searching
                "260154005", "999861000172117", "561000172108", "General"
        };
        for (int i = 0; i < searchTerms.length; i++) {
            url = baseUrl + "/search?limit=500&offset=0&sort=versionDate&sortAscending=false&query="
                    + searchTerms[i];

            logger.info("Testing term - " + searchTerms[i]);
            logger.info("Testing url - " + url);
            result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);
            resultList =
                    new ObjectMapper().readValue(content, (new TypeReference<ResultList<Refset>>() {
                        /* NA */}));

            // Check results
            refsetFound = false;
            for (Refset r : resultList.getItems()) {
                logger.info(r.getName());

                if (r.getRefsetId().equals(TESTING_REFSET_ID)) {
                    refsetFound = true;
                    testRefsetMetadata(r);
                    break;
                }
            }

            assertThat(refsetFound).isTrue();
        }

        // Test graceful handling of zero results
        url = baseUrl
                + "/search?limit=500&offset=0&sort=versionDate&sortAscending=false&query=1234567890";

        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        resultList =
                new ObjectMapper().readValue(content, (new TypeReference<ResultList<Refset>>() {
                    /* NA */}));

        // Check results
        refsetFound = false;
        for (Refset r : resultList.getItems()) {
            logger.info(r.getName());

            if (r.getRefsetId().equals(TESTING_REFSET_ID)) {
                refsetFound = true;
                testRefsetMetadata(r);
                break;
            }
        }

        assertThat(refsetFound).isTrue();

    }

    /**
     * Test getting the member concepts of a refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testRefsetDetailsTaxonomy() throws Exception {

        String url = null;
        MvcResult result = null;
        String content = null;
        ConceptResultList children = null;
        String refsetTerminologyId = "0b3133c4-7e27-4a12-88c2-58d2f5d612ee"; // getRefsetInternalId();

        url = baseUrl + "/" + refsetTerminologyId
                + "/members?limit=500&offset=0&displayType=taxonomy&startingConceptId=404684003"; // 5a2f0f94-da88-4b20-a6b5-ca9990fbbc1f
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        children = new ObjectMapper().readValue(content, (ConceptResultList.class));
        assertThat(children).isNotNull();
        assertThat(children.getItems().size()).isGreaterThan(0);
        assertThat(children.getItems().get(0).getDescriptions().size()).isGreaterThan(0);

        logger.info("Done -- Just returned refset with " + children.size() + " members.");
    }

    /**
     * Test exporting a refset SCTID list.
     *
     * @throws Exception the exception
     */
    // @Test
    public void testExportSctidList() throws Exception {

        String url = null;
        MvcResult result = null;
        String resultString = null;
        final String refsetInternalId = getRefsetInternalId();

        url = "/export/" + refsetInternalId + "/?format=sctids&exportMetadata=true";
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        resultString = result.getResponse().getContentAsString();

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(resultString);
        final String fileUrl = (root.get("url")).asText();
        logger.info("File Url: " + fileUrl);

        assertThat(fileUrl).isNotNull();

    }

    /**
     * Test exporting as RF2 Snapshot.
     *
     * @throws Exception the exception
     */
    // @Test
    public void testExportRf2Snapshot() throws Exception {

        String url = null;
        MvcResult result = null;
        String resultString = null;
        final String refsetInternalId = getRefsetInternalId();
        final String format = "rf2_with_names";
        url = "/export/" + refsetInternalId + "/?format=" + format
                + "&exportMetadata=true&exportType=SNAPSHOT&fileNameDate=20200315&transientEffectiveTime=20200315&languageId=900000000000509007FSN";

        // final String refsetInternalId = getRefsetInternalId("723264001");
        // url = "/export/" + refsetInternalId
        // +
        // "/?format=rf2&exportMetadata=true&exportType=SNAPSHOT&fileNameDate=202010131&transientEffectiveTime=20210131";
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        resultString = result.getResponse().getContentAsString();

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(resultString);
        final String fileUrl = (root.get("url")).asText();
        logger.info("File Url: " + fileUrl);

        assertThat(fileUrl).isNotNull();

    }

    /**
     * Test exporting as RF2 Delta.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExportRf2Delta() throws Exception {
        // TODO: Only possible when testing against wci-Swagger or
        // uat/prod-Swaggers unless this refset is migrated to
        // dev-integration-swagger

        final String refsetId = "723264001"; // Lateralizable body refset (has
                                             // more than 2 versions)
        
        final String refsetInternalId = getRefsetInternalId(refsetId);

        Path unzippedPath = null;
        BufferedReader correctDelta = null;
        BufferedReader generatedDeltaFile = null;

        try {

            // v1 (20180131) & v3 (20190131)
            final String url = "/export/" + refsetInternalId
                    + "/?format=rf2&exportType=DELTA&languageId=900000000000509007PT&fileNameDate=20210806&transientEffectiveTime=20190131&startEffectiveTime=20180131";
            logger.info("Testing url - " + url);
            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String resultString = result.getResponse().getContentAsString();

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString);

            // Get Zipped File
            final String fileUrl = (root.get("url")).asText();
            logger.info("File Url: " + fileUrl);
            final String zipFileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            final String exportRefsetPath = properties.getProperty("REFSET_EXPORT_DIR");
            logger.info("Refset Directory: " + exportRefsetPath);
            final String downloadedZipFile = exportRefsetPath + "/" + zipFileName;
            logger.info("Zip File Path: " + downloadedZipFile);
            unzippedPath = Files.createTempDirectory("exportDeltaTest-");
            FileUtility.unzip(downloadedZipFile, unzippedPath.toFile().getAbsolutePath());

            assertThat(1).isEqualTo(unzippedPath.toFile().list().length);

            // Get generated File
            File generatedFile = unzippedPath.toFile().listFiles()[0];

            final SortedSet<String> generatedLines = new TreeSet<>();
            final SortedSet<String> testFileLines = new TreeSet<>();
            generatedDeltaFile = new BufferedReader(new FileReader(generatedFile));

            String st;
            while ((st = generatedDeltaFile.readLine()) != null) {
                generatedLines.add(st);
            }

            // Get test file
            correctDelta = new BufferedReader(new FileReader(DELTA_FILE_PATH));
            while ((st = correctDelta.readLine()) != null) {
                testFileLines.add(st);
            }
            // Compare two but first disregard the header for the generated
            // contents.
            int j = 0;
            for (int i = 0; i < generatedLines.size(); i++) {
                String generatedLine = (String) generatedLines.toArray()[i];

                // If header line, just ignore altogether
                if (!generatedLine.startsWith("id\teffectiveTime")) {
                    String testLine = (String) testFileLines.toArray()[j++];

                    assertThat(testLine).isEqualTo(generatedLine);
                }
            }

            assertThat(testFileLines.size()).isEqualTo(j);
        } finally {
            if (correctDelta != null) {
                correctDelta.close();
            }

            if (generatedDeltaFile != null) {
                generatedDeltaFile.close();
            }

            if (unzippedPath != null) {
                FileUtility.deleteDirectory(unzippedPath.toFile());
            }
        }
    }

    /**
     * Test getting concept details.
     *
     * @throws Exception the exception
     */
    @Test
    public void testConceptDetails() throws Exception {
        // TODO: Update test as was based on PROD-Snowstorm, not our dev
        // instance
        String url = null;
        MvcResult result = null;
        String content = null;

        // Test no failure when calling conceptDetails on inactive concept
        url = "/concept/" + INACTIVE_CONCEPT_ID + "?refsetInternalId="
                + getRefsetInternalId(REFSET_WITH_INACTIVE_CONCEPT);
        logger.info("Inactive Concept Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        final Concept inactiveConcept = new ObjectMapper().readValue(content, Concept.class);
        assertThat(inactiveConcept).isNotNull();
        assertThat(inactiveConcept.getCode()).isEqualTo(INACTIVE_CONCEPT_ID);
        assertThat(inactiveConcept.getDescriptions().size()).isEqualTo(2);
        assertThat(inactiveConcept.getParents().size()).isEqualTo(0);
        assertThat(inactiveConcept.getChildren().size()).isEqualTo(0);

        // Test normal concept Details Call
        final String conceptId = "123976001"; // with 1 parent & 5 children & 1
                                              // role group of 4 rels
                                              // descriptions in all 3 lang
                                              // including Acceptable
        final String refsetId = "723264001";
        url = "/concept/" + conceptId + "?refsetInternalId=" + getRefsetInternalId(refsetId);
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        final Concept concept = new ObjectMapper().readValue(content, Concept.class);
        assertThat(concept).isNotNull();
        assertThat(concept.getCode()).isEqualTo(conceptId);
        assertThat(concept.getDescriptions().size()).isEqualTo(4);
        assertThat(concept.getRoleGroups().size()).isEqualTo(1);
        int groupId = concept.getRoleGroups().keySet().iterator().next();
        assertThat(concept.getRoleGroups().get(groupId).size()).isEqualTo(4);
        assertThat(concept.getParents().size()).isEqualTo(1);
        assertThat(concept.getChildren().size()).isEqualTo(5);

        // Test multiple descriptions of same type across 3 languages
        final String descriptionTestingConceptId = "276310004";
        final String descriptionTestingRefsetId = "561000172108"; // Belgian
                                                                  // Refset to
                                                                  // get all the
                                                                  // translations
                                                                  // as well
                                                                  // as just
                                                                  // need
                                                                  // branch path
                                                                  // for it
        url = "/concept/" + descriptionTestingConceptId + "?refsetInternalId="
                + getRefsetInternalId(descriptionTestingRefsetId);
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        final Concept descriptionTestingConcept =
                new ObjectMapper().readValue(content, Concept.class);
        assertThat(descriptionTestingConcept).isNotNull();
        assertThat(descriptionTestingConcept.getCode()).isEqualTo(descriptionTestingConceptId);
        logger.debug(descriptionTestingConcept.getDescriptions().toString());
        assertThat(descriptionTestingConcept.getDescriptions().size()).isEqualTo(7);
        assertThat(descriptionTestingConcept.getRoleGroups().size()).isEqualTo(1);
        int descriptionTestingGroupId =
                descriptionTestingConcept.getRoleGroups().keySet().iterator().next();
        assertThat(descriptionTestingConcept.getRoleGroups().get(descriptionTestingGroupId).size())
                .isEqualTo(4);
        assertThat(descriptionTestingConcept.getParents().size()).isEqualTo(1);
        assertThat(descriptionTestingConcept.getChildren().size()).isEqualTo(5);

    }

    @Test
    public void testExportFreeset() throws Exception {

        String url = null;
        MvcResult result = null;
        String resultString = null;
        final String refsetId = "787778008";
        final String refsetInternalId = getRefsetInternalId(refsetId);

        url = "/export/" + refsetInternalId + "/?format=free_set";
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        resultString = result.getResponse().getContentAsString();

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(resultString);
        final String fileUrl = (root.get("url")).asText();
        logger.info("File Url: " + fileUrl);

        assertThat(fileUrl.contentEquals("https://gps.snomed.org/"));
    }

    /**
     * Test getting concept list.
     *
     * @throws Exception the exception
     */
    @Test
    public void testMemberTable() throws Exception {
        String url = null;
        MvcResult result = null;
        String content = null;

        final String refsetId = TESTING_REFSET_ID;

        url = "/refset/" + getRefsetInternalId(refsetId)
                + "/members?limit=500&offset=0&displayType=list&refsetInternalId="
                + getRefsetInternalId(refsetId);
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
            if (conceptBeingTested.getCode().equals(firstConIdToExamine)) {
                concept = conceptBeingTested;
                break;
            }
        }

        validateConcept(concept, firstConIdToExamine, "20200131", true, firstConceptDescList, 1, 2,
                6);

        // Test second concept
        concept = null;
        for (Concept conceptBeingTested : members.getItems()) {
            if (conceptBeingTested.getCode().equals(secondConIdToExamine)) {
                concept = conceptBeingTested;
                break;
            }
        }

        validateConcept(concept, secondConIdToExamine, "20170131", true, secondConceptDescList, 2,
                2, 0);

        // Test invalid refset is handled gracefully
        url = "/refset/" + INVALID_REFSET_ID
                + "/members?limit=500&offset=0&displayType=list&refsetInternalId="
                + INVALID_REFSET_ID;

        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);

        assertThat(content).isEmpty();
    }

    /**
     * Test searching refset members
     *
     * @throws Exception the exception
     */
    // @Test
    public void testSearchRefsetMembers() throws Exception {
        // TODO: Update test as was based on PROD-Snowstorm, not our dev
        // instance

        String url = null;
        MvcResult result = null;
        String content = null;
        final String conceptIdToExamine = "429625007";

        // descriptions in all 3 lang
        final String refsetId = "561000172108";
        final String expectedEffectiveTime = "20210315";

        url = "/refset/" + getRefsetInternalId(refsetId)
                + "/members?limit=500&offset=0&query=food&displayType=list&refsetInternalId="
                + getRefsetInternalId(refsetId);
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        ConceptResultList members =
                new ObjectMapper().readValue(content, (ConceptResultList.class));

        // Testing Results
        assertThat(members).isNotNull();
        assertThat(members.size()).isEqualTo(1);

        Concept concept = null;
        for (Concept conceptBeingTested : members.getItems()) {
            if (conceptBeingTested.getCode().equals(conceptIdToExamine)) {
                concept = conceptBeingTested;
                break;
            }
        }

        assertThat(concept).isNotNull();
        assertThat(concept.getCode()).isEqualTo(conceptIdToExamine);

        final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
        assertThat(concept.getMemberEffectiveTime())
                .isEqualTo(SIMPLE_DATE_FORMAT.parseObject(expectedEffectiveTime));
        assertTrue(concept.isMemberOfRefset());
        assertThat(concept.getDescriptions().size()).isEqualTo(4);
        assertThat(concept.getRoleGroups().size()).isEqualTo(0);

        // Call does not pull in parents & Children
        assertThat(concept.getParents().size()).isEqualTo(0);
        assertThat(concept.getChildren().size()).isEqualTo(0);

    }

    /**
     * Test searching refset members taxonomy
     *
     * @throws Exception the exception
     */
    // @Test
    public void testSearchRefsetTaxonomy() throws Exception {
        // TODO: Update test as was based on PROD-Snowstorm, not our dev
        // instance

        String url = null;
        MvcResult result = null;
        String content = null;
        final String conceptIdToExamine = "256248008";

        url = "/refset/" + getRefsetInternalId() + "/taxonomySearch?limit=500&offset=0&query=plant";
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        ConceptResultList members =
                new ObjectMapper().readValue(content, (ConceptResultList.class));

        // Testing Results
        assertThat(members).isNotNull();
        // assertThat(members.size()).isEqualTo(1);

        Concept concept = null;
        for (Concept conceptBeingTested : members.getItems()) {
            if (conceptBeingTested.getCode().equals(conceptIdToExamine)) {
                concept = conceptBeingTested;
                break;
            }
        }

        assertThat(concept).isNotNull();
        assertThat(concept.getCode()).isEqualTo(conceptIdToExamine);
        assertTrue(concept.isMemberOfRefset());
        assertThat(concept.getDescriptions().size()).isEqualTo(4);

        assertThat(concept.getParents().size()).isGreaterThan(2);

    }

    /**
     * Test getting concept details.
     *
     * @throws Exception the exception
     */
    // @Test
    public void testInactiveMembers() throws Exception {

        // TAXONOMY
        final String inactiveTestUrl =
                "/refset/" + getRefsetInternalId(REFSET_WITH_INACTIVE_CONCEPT)
                        + "/members?limit=500&offset=0&displayType=taxonomy&startingConceptId="
                        + INACTIVE_CONCEPT_ID + "&refsetInternalId="
                        + getRefsetInternalId(REFSET_WITH_INACTIVE_CONCEPT);
        logger.info("Testing url - " + inactiveTestUrl);

        final MvcResult inactiveResult =
                mvc.perform(get(inactiveTestUrl)).andExpect(status().isOk()).andReturn();
        final String inactiveContent = inactiveResult.getResponse().getContentAsString();
        logger.info(" content = " + inactiveContent);
        final ConceptResultList inactiveMembers =
                new ObjectMapper().readValue(inactiveContent, (ConceptResultList.class));

        // Testing Results
        assertThat(inactiveMembers).isNotNull();
        assertThat(inactiveMembers.size()).isEqualTo(0);
    }

    /**
     * Test getting concept details.
     *
     * @throws Exception the exception
     */
    @Test
    public void testMemberTaxonomy() throws Exception {

        // First concept with grandparent with: Animal Material (256363008) is
        // parent of
        // Animal Agent (105899005) which is a parent to firstConIdToExamine
        // (Venon)

        // Must first cache the ancestors for the refset
        String url = "/ancestors/" + getRefsetInternalId(TESTING_REFSET_ID);
        logger.info("Testing url - " + url);
        MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();

        // Search on grandparent
        final Map<String, String> parChildMap = new HashMap<>();
        parChildMap.put(SNOMED_ROOT, "105590001");
        parChildMap.put("105590001", "115668003");
        parChildMap.put("115668003", "289958009");
        parChildMap.put("289958009", "256363008");
        parChildMap.put("256363008", "105899005");
        parChildMap.put("105899005", firstConIdToExamine);

        Concept childConcept = null;

        String parentId = SNOMED_ROOT;
        while (childConcept == null || !firstConIdToExamine.equals(childConcept.getCode())) {
            final String childId = parChildMap.get(parentId);
            logger.info("Testing parentID: " + parentId + " and childId: " + childId);

            url = "/refset/" + getRefsetInternalId(TESTING_REFSET_ID)
                    + "/members?limit=500&offset=0&displayType=taxonomy&startingConceptId="
                    + parentId + "&refsetInternalId=" + getRefsetInternalId(TESTING_REFSET_ID);
            logger.info("Testing url - " + url);

            result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);
            ConceptResultList children =
                    new ObjectMapper().readValue(content, (ConceptResultList.class));

            // Find Child
            childConcept = null;
            for (Concept child : children.getItems()) {
                if (child.getCode().equals(childId)) {
                    assertThat(child.getHasDescendantRefsetMembers()).isTrue();
                    childConcept = child;
                    break;
                }
            }

            assertThat(childConcept).isNotNull();
            parentId = childId;
        }

        // Expected concept found
        validateConcept(childConcept, firstConIdToExamine, "20200315", true, firstConceptDescList,
                0, 0, 0);

        // Test bad root
        url = "/refset/" + INVALID_REFSET_ID
                + "/members?limit=500&offset=0&displayType=taxonomy&startingConceptId="
                + SNOMED_ROOT + "&refsetInternalId=" + INVALID_REFSET_ID;
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().is5xxServerError()).andReturn();
        String content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);

        assertThat(content).isEmpty();
    }

    /**
     * Test getting concept details.
     *
     * @throws Exception the exception
     */
    @Test
    public void testMemberHistory() throws Exception {

        // with 1 parent & 5 children & 1 role group of 4 rels
        // descriptions in all 3 lang
        final String conceptIdToExamine = "727156001"; // "771410009";
        final String refsetId = "723264001"; // "723264001";

        final String url =
                "/refset/" + getRefsetInternalId(refsetId) + "/member/" + conceptIdToExamine;
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

            assertThat(version.equals("2017-07-31") || version.equals("2018-07-31"));

            if (version.equals("2018-07-31")) {
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

        String url = null;
        MvcResult result = null;
        String content = null;

        url = "/admin/migration/rtt";
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);

        assertThat(content).isEqualTo("RTT data migration completed successfully");
    }

    /**
     * Get the internal refset ID based on the refset's terminology specific ID
     * .
     *
     * @return the internal refset ID
     * @throws Exception the exception
     */
    private String getRefsetInternalId() throws Exception {
        return getRefsetInternalId(TESTING_REFSET_ID);
    }

    private String getRefsetInternalId(String requestedId) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();
            pfs.setLimit(1);
            pfs.setSort("versionDate");
            pfs.setAscending(false);

            ResultList<Refset> refsets =
                    service.find("refsetId:" + QueryParserBase.escape(requestedId) + "", pfs,
                            Refset.class, null);

            assertThat(refsets.getItems().size()).isGreaterThan(0);

            Refset refset = refsets.getItems().get(0);

            if (refset == null) {
                throw new Exception("Refset Internal Id: " + requestedId
                        + " does not exist in the RT2 database");
            }

            assertThat(refset).isNotNull();
            assertThat(refset.getRefsetId()).isEqualTo(requestedId);

            return refset.getId();
        }
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
    public void testVersions() throws Exception {

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
        assertThat(versions.getTotal()).isGreaterThan(5);

    }

    private void testRefsetMetadata(Refset refset) {
        assertThat(refset).isNotNull();
        assertThat(refset.getRefsetId()).isEqualTo(TESTING_REFSET_ID);

        assertThat(refset.getName()).isEqualToIgnoringCase(
                "Belgian simple reference set for translated animal materials");
        assertThat(refset.getNarrative()).isEqualToIgnoringCase(
                "descendants of 256363008 |Animal material (substance)| translated in the Belgian extension");
        assertThat(refset.getModifiedBy()).isEqualToIgnoringCase("Migration");
        assertThat(refset.getType()).isEqualToIgnoringCase("extensional");
        assertThat(refset.getModuleId()).isEqualTo("11000172109");
        assertThat(refset.getEdition().getName()).isEqualToIgnoringCase("Belgian Edition");
        assertThat(refset.getType()).isEqualToIgnoringCase("extensional");
        assertThat(refset.getVersionStatus()).isEqualToIgnoringCase("published");
        assertThat(refset.getVersionNotes()).isNull();
        assertThat(refset.getProject().getName()).isEqualTo("Belgian Extension Project");

        assertThat(refset.isActive()).isTrue();
        assertThat(refset.isLocalSet()).isFalse();
        assertThat(refset.isPrivateRefset()).isFalse();

        // Collection - Tags
        assertThat(refset.getTags().size()).isEqualTo(1);
        assertThat(refset.getTags().iterator().next()).isEqualTo("General / Allergies");

        // Version Date
        assertThat(refset.getVersionDate()).isEqualToIgnoringHours("2020-09-15");
    }

    private void validateConcept(Concept concept, String conId, String effectiveTime,
        boolean isRefsetMember, List<String> descriptionList, int roleGroupSize, int parentSize,
        int childSize) throws ParseException {

        assertThat(concept).isNotNull();
        assertThat(concept.getCode()).isEqualTo(conId);

        final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
        assertThat(concept.getMemberEffectiveTime())
                .isEqualTo(SIMPLE_DATE_FORMAT.parseObject(effectiveTime));
        assertThat(concept.isMemberOfRefset()).isEqualTo(isRefsetMember);
        assertThat(concept.getDescriptions().size()).isEqualTo(descriptionList.size());
        for (String matchingDesc : descriptionList) {
            verifyDescExist(concept.getDescriptions(), matchingDesc);
        }
        assertThat(concept.getRoleGroups().size()).isEqualTo(roleGroupSize);

        assertThat(concept.getParents().size()).isEqualTo(parentSize);
        assertThat(concept.getChildren().size()).isEqualTo(childSize);

    }

    private void verifyDescExist(List<Map<String, String>> descriptions, String matchingTerm) {
        boolean descFound = false;

        for (Map<String, String> descriptionGroup : descriptions) {
            if (descriptionGroup.get(DESCRIPTION_TERM).equalsIgnoreCase(matchingTerm)) {
                descFound = true;
                break;
            }
        }

        assertTrue(descFound);
    }
}
