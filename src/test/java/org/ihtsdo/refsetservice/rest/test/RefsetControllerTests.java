
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
import org.assertj.core.util.Arrays;
import org.ihtsdo.refsetservice.handler.ExportHandler;
import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.PfsParameter;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.TypeKeyValue;
import org.ihtsdo.refsetservice.model.VersionStatus;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.S3ConnectionWrapper;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Integration tests for MetadataController.
 */

@AutoConfigureMockMvc
public class RefsetControllerTests extends BaseTest {
    private final static SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");

    /** The config properties. */
    private final Properties properties = PropertyUtility.getProperties();

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(RefsetControllerTests.class);

    /** The Constant TESTING_REFSET_ID. */
    // Belgian simple reference set for translated animal materials w/101
    // members
    // Resides with 101 members on wci-snowstorm, dev-integration, UAT, and
    // production
    private static String mainTestingRefsetInternalId;

    private static final String GPS_REFSET_ID = "787778008";
    
    private static String testingProjectId = "";
    
    private static final String TESTING_PROJECT_NAME = "SNOMED International Project";
    
    private static String testingOrganizationId = "";
    
    private static final String TESTING_ORGANIZATION_NAME = "";
    
    private static String testingEditionId = "";
    
    private static final String TESTING_EDITION_NAME = "International Edition";
    
    private static final String TESTING_REFSET_ID = "561000172108"; // Belgian

    private static final String TESTING_REFSET_VERSION = "20200915";

    // With 2 parents, 6 children and 0 defing rels
    private static final String FIRST_CONCEPT_ID = "37663002";

    // With 1 parents, 0 children and 0 defing rels
    private static final String SECOND_CONCEPT_ID = "260206005";

    private static final String DETAILS_SEARCH_CONCEPT_ID = "276310004";

    private static String inactiveConceptRefsetInternalId;

    private static final String INACTIVE_REFSET_ID = "723264001";

    private static final String INACTIVE_REFSET_VERSION = "20210731";

    // Added 20170731 and Inactived in Lateralizable (723264001) on 20180131
    private static final String INACTIVE_CONCEPT_ID = "727156001";

    private static final String INACTIVE_REFSET_DIFFERENT_VERSION = "20170731";

    private static final String INVALID_INTERNAL_REFSET_ID = "12345678901234567890";

    private static final String DESCRIPTION_TERM = "term";

    private static final String SNOMED_ROOT = "138875005";

    private static final List<String> firstConceptDescList = new ArrayList<>();

    private static final List<String> secondConceptDescList = new ArrayList<>();

    private static final List<String> inactiveConceptDescList = new ArrayList<>();

    private static final List<String> detailSearchNonAcceptableConceptDescList = new ArrayList<>();

    private static final String REFSET_FILE_PATH =
            "src/test/resources/refsetService/";
    
    private static final String TWO_VERSION_DELTA_FILE =
            REFSET_FILE_PATH + "Lateralizable Delta 201801 to 201807.txt";

    private static final String THREE_VERSION_DELTA_FILE =
            REFSET_FILE_PATH + "Lateralizable Delta 201801 to 201901.txt";

    private static final String SNAPSHOT_FILE =
            REFSET_FILE_PATH + "561000172108 Snapshot 20200315.txt";

    private static final String LIST_OF_SCTIDS_FILE =
            REFSET_FILE_PATH + "561000172108 ListOfSctIds 20200315.txt";
    
    private static final String MEMBER_ID_LIST_FILE_NAME =
            "member_concept_id_list.txt";
    
    private static final String MEMBER_ID_LIST_FILE =
            REFSET_FILE_PATH + MEMBER_ID_LIST_FILE_NAME;
    
    private static final String MEMBER_ID_RF2_FILE_NAME =
            "member_concept_ids_rf2.txt";
    
    private static final String MEMBER_ID_RF2_FILE =
            REFSET_FILE_PATH + MEMBER_ID_RF2_FILE_NAME;

    /** The mvc. */
    @Autowired
    private MockMvc mvc;

    /** The object mapper. */
    private static ObjectMapper objectMapper;

    /** The base url. */
    private static String baseUrl = "";

    /**
     * Sets the up.
     */
    @BeforeEach
    public void setUp(TestInfo info) {
        
        // skip @BeforeEach in testRttMigration
        if (info.getDisplayName().equals("testRttMigration()")) {
            return;
        }

        if (mainTestingRefsetInternalId == null) {
            
            objectMapper = new ObjectMapper();
            JacksonTester.initFields(this, objectMapper);
            baseUrl = "/refset";
            
            try {
                
                mainTestingRefsetInternalId =
                        getRefsetInternalId(TESTING_REFSET_ID, TESTING_REFSET_VERSION);

                inactiveConceptRefsetInternalId =
                        getRefsetInternalId(INACTIVE_REFSET_ID, INACTIVE_REFSET_VERSION);
                
                testingEditionId = getEditionInternalId(TESTING_EDITION_NAME); 
                testingProjectId = getProjectInternalId(TESTING_PROJECT_NAME);
                
                firstConceptDescList.add("Venom (substance)");
                firstConceptDescList.add("Venom");
                firstConceptDescList.add("venin");
                firstConceptDescList.add("gif");
                
                inactiveConceptDescList.add("Entire sclerocorneal junction (body structure)");
                inactiveConceptDescList.add("Entire sclerocorneal junction");
                
                detailSearchNonAcceptableConceptDescList.add("Non-human hair - material (substance)");
                detailSearchNonAcceptableConceptDescList.add("Animal hair");
                detailSearchNonAcceptableConceptDescList.add("dierlijk haar");
                detailSearchNonAcceptableConceptDescList.add("poil animal");
                
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
    public void testGetProject() throws Exception {

        final String url = "/project/" + testingProjectId;
        logger.info("Testing url - " + url);

        final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        final String content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);

        final Project project = new ObjectMapper().readValue(content, Project.class);

        assertThat(project).isNotNull();
        assertThat(project.getId()).isEqualTo(testingProjectId);
        assertThat(project.getName()).isEqualTo(TESTING_PROJECT_NAME);
    }
    
    /**
     * Test searching for projects.
     *
     * @throws Exception the exception
     */
    @Test
    public void testProjectSearch() throws Exception {
        
        String url = "/project/search?limit=500&offset=0&sort=name&sortAscending=false";
        logger.info("Testing url - " + url);
        
        MvcResult result = null;
        String content = null;
        ResultList<Project> resultList = null;
        Refset projectFound = null;

        // Test full list
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        
        resultList =
                new ObjectMapper().readValue(content, (new TypeReference<ResultList<Project>>() {
                }));
        assertThat(resultList).isNotNull();
        assertThat(resultList.getItems().size()).isGreaterThanOrEqualTo(1);
    }
    
    /**
     * Test getting a refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testRefset() throws Exception {

        final String url = baseUrl + "/" + mainTestingRefsetInternalId;
        logger.info("Testing url - " + url);

        final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        final String content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);

        final Refset refset = new ObjectMapper().readValue(content, Refset.class);

        validateRefsetMetadata(refset);
    }
    
    /**
     * Test creating a refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testCreateRefset() throws Exception {

        final ObjectMapper mapper = new ObjectMapper();
        final String memberConceptIds = "48176007,280416009,10828004,260385009";
        final List<Map<String, String>> refsetDetails = new ArrayList<>();
        
        // the data to create a refset from a new concept
        final Map<String, String> refsetNewConcept = new HashMap<>();
        refsetNewConcept.put("name", "ZZZ RT2 Test New Concept Refset");
        refsetNewConcept.put("parentConceptId", "446609009");
        refsetNewConcept.put("moduleId", "900000000000207008");
        refsetNewConcept.put("editionId", testingEditionId);
        refsetNewConcept.put("projectId", testingProjectId);
        refsetNewConcept.put("narrative", "Test.");
        refsetNewConcept.put("type", "EXTENSIONAL");
        refsetNewConcept.put("privateRefset", "false");
        refsetNewConcept.put("localSet", "false");
        refsetNewConcept.put("refsetDeleteStatus", "deleted");
        refsetNewConcept.put("fileType", "list");
        refsetNewConcept.put("conceptFileName", MEMBER_ID_LIST_FILE_NAME);
        refsetNewConcept.put("conceptFile", MEMBER_ID_LIST_FILE);
        
        // prepare the call to create refset from a new concept
        final ObjectNode refsetNewConceptBody = mapper.createObjectNode()
                .put("name", refsetNewConcept.get("name"))
                .put("parentConceptId", refsetNewConcept.get("parentConceptId"))
                .put("moduleId", refsetNewConcept.get("moduleId"))
                .put("editionId", refsetNewConcept.get("editionId"))
                .put("projectId", refsetNewConcept.get("projectId"))
                .put("narrative", refsetNewConcept.get("narrative"))
                .put("type", refsetNewConcept.get("type"))
                .put("privateRefset", Boolean.parseBoolean(refsetNewConcept.get("privateRefset")))
                .put("localSet", Boolean.parseBoolean(refsetNewConcept.get("localSet")));
        
        refsetNewConcept.put("body", refsetNewConceptBody.toString());
        refsetDetails.add(refsetNewConcept);
        
        // the data to create a refset with members added from an RF2 file
        final Map<String, String> refsetRf2MemberAdd = new HashMap<>();
        refsetRf2MemberAdd.put("name", "ZZZ RT2 Test RF2 Member Add Refset");
        refsetRf2MemberAdd.put("parentConceptId", "446609009");
        refsetRf2MemberAdd.put("moduleId", "900000000000207008");
        refsetRf2MemberAdd.put("editionId", testingEditionId);
        refsetRf2MemberAdd.put("projectId", testingProjectId);
        refsetRf2MemberAdd.put("narrative", "Test.");
        refsetRf2MemberAdd.put("type", "EXTENSIONAL");
        refsetRf2MemberAdd.put("privateRefset", "false");
        refsetRf2MemberAdd.put("localSet", "false");
        refsetRf2MemberAdd.put("refsetDeleteStatus", "deleted");
        refsetRf2MemberAdd.put("fileType", "rf2");
        refsetRf2MemberAdd.put("conceptFileName", MEMBER_ID_RF2_FILE_NAME);
        refsetRf2MemberAdd.put("conceptFile", MEMBER_ID_RF2_FILE);
        
        // prepare the call to create refset with members added from an RF2 file
        final ObjectNode refsetRf2MemberAddBody = mapper.createObjectNode()
                .put("name", refsetRf2MemberAdd.get("name"))
                .put("parentConceptId", refsetRf2MemberAdd.get("parentConceptId"))
                .put("moduleId", refsetRf2MemberAdd.get("moduleId"))
                .put("editionId", refsetRf2MemberAdd.get("editionId"))
                .put("projectId", refsetRf2MemberAdd.get("projectId"))
                .put("narrative", refsetRf2MemberAdd.get("narrative"))
                .put("type", refsetRf2MemberAdd.get("type"))
                .put("privateRefset", Boolean.parseBoolean(refsetRf2MemberAdd.get("privateRefset")))
                .put("localSet", Boolean.parseBoolean(refsetRf2MemberAdd.get("localSet")));
        
        refsetRf2MemberAdd.put("body", refsetRf2MemberAddBody.toString());
        refsetDetails.add(refsetRf2MemberAdd);
        
        // the data to create a refset from an existing concept (but can't be a refset already in RT2)
        final Map<String, String> refsetExistingConcept = new HashMap<>();
        refsetExistingConcept.put("refsetId", "762103008");
        refsetExistingConcept.put("name", "OWL ontology reference set");
        refsetExistingConcept.put("parentConceptId", "446609009");
        refsetExistingConcept.put("moduleId", "900000000000207008");
        refsetExistingConcept.put("editionId", testingEditionId);
        refsetExistingConcept.put("projectId", testingProjectId);
        refsetExistingConcept.put("narrative", "Test.");
        refsetExistingConcept.put("type", "EXTENSIONAL");
        refsetExistingConcept.put("privateRefset", "false");
        refsetExistingConcept.put("localSet", "false");
        refsetExistingConcept.put("refsetDeleteStatus", "deleted");
        // DO NOT LEAVE THIS UNCOMMENTED - for one test we will try to remove a member that has already been published
        //refsetExistingConcept.put("additionalMemberIdsToRemove", "734147008");
        
        // prepare the call to create refset from an existing concept
        final ObjectNode refsetExistingConceptBody = mapper.createObjectNode()
                .put("refsetId", refsetExistingConcept.get("refsetId"))
                .put("name", refsetExistingConcept.get("name"))
                .put("parentConceptId", refsetExistingConcept.get("parentConceptId"))
                .put("moduleId", refsetExistingConcept.get("moduleId"))
                .put("editionId", refsetExistingConcept.get("editionId"))
                .put("projectId", refsetExistingConcept.get("projectId"))
                .put("narrative", refsetExistingConcept.get("narrative"))
                .put("type", refsetExistingConcept.get("type"))
                .put("privateRefset", Boolean.parseBoolean(refsetExistingConcept.get("privateRefset")))
                .put("localSet", Boolean.parseBoolean(refsetExistingConcept.get("localSet")));
        
        refsetExistingConcept.put("body", refsetExistingConceptBody.toString());
        refsetDetails.add(refsetExistingConcept);

        // TESTS BEGIN - loop over the details object to run various tests
        for (final Map<String, String> refsetDetail : refsetDetails) {
            
            // make the call to create refset from a new concept
            final MvcResult result = mvc.perform(
                    post(baseUrl).content(refsetDetail.get("body"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);
            
            final JsonNode root = mapper.readTree(content);
            final JsonNode refsetNode = root;
            
            assertTrue(refsetNode.has("refsetInternalId")); 
            final String refsetInternalId = refsetNode.get("refsetInternalId").asText();
            
            // verify the refset from a new concept in the RT2 DB
            try (final TerminologyService service = new TerminologyService()) {
                
                Refset refset = service.get(refsetInternalId, Refset.class);
                assertThat(refset).isNotNull();
                assertThat(refset.getName()).isEqualTo(refsetDetail.get("name"));
                assertThat(refset.getModuleId()).isEqualTo(refsetDetail.get("moduleId"));
                assertThat(refset.getEditionId()).isEqualTo(refsetDetail.get("editionId"));
                assertThat(refset.getProjectId()).isEqualTo(refsetDetail.get("projectId"));
                assertThat(refset.getNarrative()).isEqualTo(refsetDetail.get("narrative"));
                assertThat(refset.getType()).isEqualTo(refsetDetail.get("type"));
                assertThat(refset.isPrivateRefset()).isEqualTo(Boolean.parseBoolean(refsetDetail.get("privateRefset")));
                assertThat(refset.isLocalSet()).isEqualTo(Boolean.parseBoolean(refsetDetail.get("localSet")));
                
                if (refsetDetail.containsKey("refsetId")) {
                    assertThat(refset.getRefsetId()).isEqualTo(refsetDetail.get("refsetId"));
                }
            }
            
            // prepare the call to add members to the refset from a new concept
            final String membersUrl = baseUrl + "/" + refsetInternalId + "/members?conceptIds=";
            MvcResult membersResult = null;
            
            if (!refsetDetail.containsKey("fileType")) {
            
                // make the call to add members to the refset from a new concept
                membersResult = mvc.perform(
                        post(membersUrl + memberConceptIds)
                        .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn();
                
            } else {
                
                MockMultipartFile mockMultipartFile = new MockMultipartFile("conceptFile", refsetDetail.get("conceptFileName"),
                        "text/plain", Files.readAllBytes(Paths.get(refsetDetail.get("conceptFile"))));

                membersResult = mvc.perform(
                        multipart(membersUrl + "&fileType=" + refsetDetail.get("fileType"))
                        .file(mockMultipartFile)
                        .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn();
            }
            
            final String membersContent = membersResult.getResponse().getContentAsString();
            logger.info(" membersContent = " + membersContent);
            
            final JsonNode membersRoot = mapper.readTree(membersContent);
            final JsonNode membersNode = membersRoot;
            
            assertTrue(membersNode.has("status")); 
            assertTrue(membersNode.get("status").asText().equals("All concepts added."));
            
            // remove the members of the refset from a new concept
            if (membersNode.has("status")) {
                
                final String removeUrl = baseUrl + "/" + refsetInternalId + "/removeMembers?conceptIds=";
                MvcResult removeResult = null;
                
                if (!refsetDetail.containsKey("fileType")) {
                
                    String additionalMemberIds = "";
                    
                    // for one test we will try to remove a member that has already been published
                    if (refsetDetail.containsKey("additionalMemberIdsToRemove")) {
                        additionalMemberIds = "," + refsetDetail.get("additionalMemberIdsToRemove");
                    }
                    
                    // make the call to add members to the refset from a new concept
                    removeResult = mvc.perform(
                            post(removeUrl + memberConceptIds + additionalMemberIds)
                            .accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk()).andReturn();
                    
                } else {
                    
                    MockMultipartFile mockMultipartFile = new MockMultipartFile("conceptFile", refsetDetail.get("conceptFileName"),
                            "text/plain", Files.readAllBytes(Paths.get(refsetDetail.get("conceptFile"))));

                    removeResult = mvc.perform(
                            multipart(removeUrl + "&fileType=" + refsetDetail.get("fileType"))
                            .file(mockMultipartFile)
                            .accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk()).andReturn();
                }
 
                final String removeContent = removeResult.getResponse().getContentAsString();
                final JsonNode removeRoot = mapper.readTree(removeContent);
                final JsonNode removeNode = removeRoot;
                
                assertTrue(removeNode.has("status"));
                assertTrue(removeNode.get("status").asText().equals("All concepts removed."));
            }
            
            // delete the refset from a new concept
            if (refsetInternalId != null && !refsetInternalId.equals("")) {
                
                final String deleteUrl = baseUrl + "/" + refsetInternalId;
                final MvcResult deleteResult = mvc.perform(
                        delete(deleteUrl))
                    .andExpect(status().isOk()).andReturn();
                final String deleteContent = deleteResult.getResponse().getContentAsString();
                final JsonNode deleteRoot = mapper.readTree(deleteContent);
                final JsonNode deleteNode = deleteRoot;
                
                assertTrue(deleteNode.has("status"));
                assertTrue(deleteNode.get("status").asText().equals(refsetDetail.get("refsetDeleteStatus")));
            }
        }
        
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
        Refset refsetFound = null;

        // Test by name
        url = baseUrl

                + "/search?limit=500&offset=0&sort=versionDate&sortAscending=false&query=name:animal";
        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        resultList =
                new ObjectMapper().readValue(content, (new TypeReference<ResultList<Refset>>() {
                }));
        assertThat(resultList).isNotNull();
        assertThat(resultList.getItems().size()).isGreaterThanOrEqualTo(1);

        refsetFound = null;
        for (Refset r : resultList.getItems()) {
            if (r.getRefsetId().equals(TESTING_REFSET_ID)) {
                refsetFound = r;
                break;
            }
        }

        validateRefsetMetadata(refsetFound);

        // Test by edition name
        url = baseUrl
                + "/search?limit=500&offset=0&sort=versionDate&sortAscending=false&query=editionName:Belgian Edition";

        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        resultList =
                new ObjectMapper().readValue(content, (new TypeReference<ResultList<Refset>>() {
                }));
        assertThat(resultList).isNotNull();
        assertThat(resultList.getItems().size()).isGreaterThanOrEqualTo(1);

        refsetFound = null;
        for (Refset r : resultList.getItems()) {
            if (r.getRefsetId().equals(TESTING_REFSET_ID)) {
                refsetFound = r;
                break;
            }
        }

        validateRefsetMetadata(refsetFound);

        // Test by combination
        url = baseUrl
                + "/search?limit=500&offset=0&sort=versionDate&sortAscending=false&query=name:animal AND editionName:Belgian Edition"; // Hyperdontia

        logger.info("Testing url - " + url);
        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        resultList =
                new ObjectMapper().readValue(content, (new TypeReference<ResultList<Refset>>() {
                }));
        assertThat(resultList).isNotNull();
        assertThat(resultList.getItems().size()).isEqualTo(1);
        validateRefsetMetadata(resultList.getItems().get(0));

        // Test by term per language (at least on non-pt)
        // Test by concept Id
        // by term id
        // by refset id
        // By narrative
        // by tags
        String searchTerms[] = new String[] {
                "Dog", "squame", "huidschilfer", "olie uit lever van vis", "260154005",
                "999861000172117", "561000172108", "General"
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
                    }));

            // Check results
            refsetFound = null;
            for (Refset r : resultList.getItems()) {
                if (r.getRefsetId().equals(TESTING_REFSET_ID)) {
                    refsetFound = r;
                    break;
                }
            }

            validateRefsetMetadata(refsetFound);
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
        assertThat(resultList.getItems().isEmpty()).isTrue();
        assertThat(resultList.getTotal()).isEqualTo(0);
    }

    /**
     * Test exporting a refset SCTID list.
     *
     * @throws Exception the exception
     */
    @Test
    public void testExportSctidList() throws Exception {

        String url = null;
        MvcResult result = null;
        String resultString = null;

        url = "/export/" + mainTestingRefsetInternalId + "/?format=sctids";
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        resultString = result.getResponse().getContentAsString();

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(resultString);

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
        try {
            S3ConnectionWrapper.connectToAmazonS3();
            ExportHandler exporter = new ExportHandler();
            String awsPath = exporter.getTopLevelAwsPath() + TESTING_REFSET_ID + "/20200315";
            S3ConnectionWrapper.deleteRefsetFromAws(awsPath);
        } catch (Exception e) {
            // do nothing
        }

        final String format = "rf2";
        final String url = "/export/" + mainTestingRefsetInternalId + "/?format=" + format
                + "&exportType=SNAPSHOT&fileNameDate=20200315&transientEffectiveTime=20200315&languageId=900000000000509007FSN";

        logger.info("Testing url - " + url);

        final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        final String resultString = result.getResponse().getContentAsString();

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(resultString);

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

        // TODO: Delete refsets specific files or dates (once finalize naming
        // convention)
        try {
            S3ConnectionWrapper.connectToAmazonS3();
            ExportHandler exporter = new ExportHandler();
            String awsPath = exporter.getTopLevelAwsPath() + INACTIVE_REFSET_ID;
            S3ConnectionWrapper.deleteRefsetFromAws(awsPath);
        } catch (Exception e) {
            // do nothing
        }

        Path unzippedPath = null;
        try {
            // v1 (20180131) & v2 (20180731)
            final String url = "/export/" + inactiveConceptRefsetInternalId
                    + "/?format=rf2&exportType=DELTA&languageId=900000000000509007PT&fileNameDate=20210806&transientEffectiveTime=20180731&startEffectiveTime=20180131";
            logger.info("Testing url - " + url);
            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String resultString = result.getResponse().getContentAsString();

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString);

            // Validate
            validateExportFiles(root, TWO_VERSION_DELTA_FILE);
        } finally {
            if (unzippedPath != null) {
                FileUtility.deleteDirectory(unzippedPath.toFile());
            }
        }

        unzippedPath = null;

        try {
            // v1 (20180131) & v3 (20190131)
            final String url = "/export/" + inactiveConceptRefsetInternalId
                    + "/?format=rf2&exportType=DELTA&languageId=900000000000509007PT&fileNameDate=20210806&transientEffectiveTime=20190131&startEffectiveTime=20180131";
            logger.info("Testing url - " + url);
            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String resultString = result.getResponse().getContentAsString();

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString);

            // Validate
            validateExportFiles(root, THREE_VERSION_DELTA_FILE);
        } finally {
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
        validateConcept(concept, FIRST_CONCEPT_ID, null, false, firstConceptDescList, 0, 2, 6);

        // Try second concept
        url = "/concept/" + SECOND_CONCEPT_ID + "?refsetInternalId=" + mainTestingRefsetInternalId;
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        concept = new ObjectMapper().readValue(content, Concept.class);

        // doesn't include membership status nor memberEffectiveTime
        validateConcept(concept, SECOND_CONCEPT_ID, null, false, secondConceptDescList, 0, 1, 0);

        // Test invalid refset is handled gracefully
        url = "/concept/" + FIRST_CONCEPT_ID + "?refsetInternalId=" + INVALID_INTERNAL_REFSET_ID;

        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().is5xxServerError()).andReturn();
        content = result.getResponse().getContentAsString();

        assertThat(content).isEmpty();
    }
    
    /**
     * Test getting the list of concepts that represent refsets for dropdown options.
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
        final ConceptResultList parentsResultList = new ObjectMapper().readValue(content, ConceptResultList.class);

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
        
        // call the api to get the refset concept list for using as the underlying concept for a new refset
        url = "/general/refsetConcepts?branch=" + branch + "&areParentConcepts=false";
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);
        final ConceptResultList newRefsetResultList = new ObjectMapper().readValue(content, ConceptResultList.class);

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
     * Test getting concept list.
     *
     * @throws Exception the exception
     */
    @Test
    public void testMemberList() throws Exception {
        String url = null;
        MvcResult result = null;
        String content = null;

        url = "/refset/" + mainTestingRefsetInternalId
                + "/members?limit=500&offset=0&displayType=list&refsetInternalId="
                + mainTestingRefsetInternalId;
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
                + "/members?limit=500&offset=0&displayType=list&refsetInternalId="
                + INVALID_INTERNAL_REFSET_ID;

        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().is5xxServerError()).andReturn();
        content = result.getResponse().getContentAsString();

        assertThat(content).isEmpty();
    }

    /**
     * Test searching refset members
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
                    + searchTerms[i] + "&displayType=list&refsetInternalId="
                    + mainTestingRefsetInternalId;

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
        url = baseUrl + "/search?limit=500&offset=0&sort=versionDate&sortAscending=false&query="
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
                + INACTIVE_CONCEPT_ID + "&displayType=list&refsetInternalId="
                + inactiveConceptRefsetInternalId;

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

        // Membership info and descriptions, but no parents/children
        validateConcept(inactiveConcept, INACTIVE_CONCEPT_ID, "20180731", true,
                inactiveConceptDescList, 0, 0, 0);

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
     * Test getting taxonomy.
     *
     * @throws Exception the exception
     */
    @Test
    public void testTaxonomy() throws Exception {

        // First concept with grandparent with: Animal Material (256363008) is
        // parent of
        // Animal Agent (105899005) which is a parent to firstConIdToExamine
        // (Venon)

        // Must first cache the ancestors for the refset
        String url = "/ancestors/" + mainTestingRefsetInternalId;
        logger.info("Testing url - " + url);
        MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();

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

            url = "/refset/" + mainTestingRefsetInternalId
                    + "/members?limit=500&offset=0&displayType=taxonomy&startingConceptId="
                    + parentId + "&refsetInternalId=" + mainTestingRefsetInternalId;
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
        validateConcept(childConcept, FIRST_CONCEPT_ID, "20200315", true, firstConceptDescList, 0,
                0, 0);

        // Test bad root
        url = "/refset/" + INVALID_INTERNAL_REFSET_ID
                + "/members?limit=500&offset=0&displayType=taxonomy&startingConceptId="
                + SNOMED_ROOT + "&refsetInternalId=" + INVALID_INTERNAL_REFSET_ID;
        logger.info("Testing url - " + url);

        result = mvc.perform(get(url)).andExpect(status().is5xxServerError()).andReturn();
        final String content = result.getResponse().getContentAsString();

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

        String origRefsetVersionId =
                getRefsetInternalId(INACTIVE_REFSET_ID, INACTIVE_REFSET_DIFFERENT_VERSION);

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

        // Validate concept returned and is inactive
        assertThat(matchedConcept).isNotNull();
        assertThat(matchedConcept.isActive()).isFalse();

    }

    /**
     * Get the internal refset ID based on the refset's terminology specific ID
     * .
     * @param version
     * @param refsetWithInactiveConcept
     *
     * @return the internal refset ID
     * @throws Exception the exception
     */
    private String getRefsetInternalId(String requestedId, String version) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();
            pfs.setSort("versionDate");
            pfs.setAscending(false);

            ResultList<Refset> refsets =
                    service.find("refsetId:" + QueryParserBase.escape(requestedId) + "", pfs,
                            Refset.class, null);

            assertThat(refsets.getItems().size()).isGreaterThan(0);

            Refset refsetToReturn = null;
            for (Refset refset : refsets.getItems()) {
                if (version.equals(SIMPLE_DATE_FORMAT.format(refset.getVersionDate()))) {
                    refsetToReturn = refset;
                    break;
                }
            }

            if (refsetToReturn == null) {
                throw new Exception("Refset Internal Id: " + requestedId
                        + " does not exist in the RT2 database");
            }

            assertThat(refsetToReturn).isNotNull();
            assertThat(refsetToReturn.getRefsetId()).isEqualTo(requestedId);

            return refsetToReturn.getId();
        }
    }
    
    /**
     * Get the internal project ID based on the project's name
     * 
     * @param name The name of the project
     * @return the internal project ID
     * @throws Exception the exception
     */
    private String getProjectInternalId(String name) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();

            ResultList<Project> projects =
                    service.find("name:" + QueryParserBase.escape(name) + "", pfs,
                            Project.class, null);

            if (projects.getItems().size() == 0) {
                throw new Exception("Refset Internal Id: " + name
                        + " does not exist in the RT2 database");
            }
            
            Project project = projects.getItems().get(0);

            assertThat(project.getName()).isEqualTo(name);

            return project.getId();
        }
    }
    
    /**
     * Get the internal edition ID based on the edition's name
     * 
     * @param name The name of the edition
     * @return the internal edition ID
     * @throws Exception the exception
     */
    private String getEditionInternalId(String name) throws Exception {

        try (final TerminologyService service = new TerminologyService()) {

            final PfsParameter pfs = new PfsParameter();

            ResultList<Edition> editions =
                    service.find("name:" + QueryParserBase.escape(name) + "", pfs,
                            Edition.class, null);

            if (editions.getItems().size() == 0) {
                throw new Exception("Refset Internal Id: " + name
                        + " does not exist in the RT2 database");
            }
            
            Edition edition = editions.getItems().get(0);

            assertThat(edition.getName()).isEqualTo(name);

            return edition.getId();
        }
    }

    private void validateRefsetMetadata(Refset refset) {
        assertThat(refset).isNotNull();

        if (refset.getRefsetId().equals(INACTIVE_REFSET_ID)) {
            assertThat(refset.getRefsetId()).isEqualTo(INACTIVE_REFSET_ID);

            assertThat(refset.getName())
                    .isEqualToIgnoringCase("Lateralizable body structure reference set");
            assertThat(refset.getNarrative()).isEqualToIgnoringCase(
                    "The reference set contains all body structures that can be lateralized.");
            assertThat(refset.getModifiedBy()).isEqualToIgnoringCase("Migration");
            assertThat(refset.getType()).isEqualToIgnoringCase("extensional");
            assertThat(refset.getModuleId()).isEqualTo("900000000000012004");
            assertThat(refset.getEdition().getName())
                    .isEqualToIgnoringCase("International Edition");
            assertThat(refset.getType()).isEqualToIgnoringCase("extensional");
            assertThat(refset.getVersionStatus()).isEqualToIgnoringCase("published");
            assertThat(refset.getVersionNotes()).isNull();
            assertThat(refset.getProject().getName()).isEqualTo("SNOMED International WIP Project");

            assertThat(refset.isActive()).isTrue();
            assertThat(refset.isLocalSet()).isFalse();
            assertThat(refset.isPrivateRefset()).isFalse();

            // Collection - Tags
            assertThat(refset.getTags().size()).isEqualTo(1);
            assertThat(refset.getTags().iterator().next()).isEqualTo("anatomy");

            // Version Date
            assertThat(refset.getVersionDate()).isEqualToIgnoringHours("2021-07-31");
        } else {
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
    }

    private void validateConcept(Concept concept, String conId, String memberEfectiveTime,
        boolean isRefsetMember, List<String> descriptionList, int roleGroupSize, int parentSize,
        int childSize) throws ParseException {

        assertThat(concept).isNotNull();
        assertThat(concept.getCode()).isEqualTo(conId);

        if (memberEfectiveTime != null) {
            assertThat(concept.getMemberEffectiveTime())
                    .isEqualTo(SIMPLE_DATE_FORMAT.parseObject(memberEfectiveTime));
            assertThat(concept.isMemberOfRefset()).isEqualTo(isRefsetMember);
        }
        assertThat(concept.getDescriptions().size()).isEqualTo(descriptionList.size());
        for (String matchingDesc : descriptionList) {
            validateDescExist(concept.getDescriptions(), matchingDesc);
        }
        assertThat(concept.getRoleGroups().size()).isEqualTo(roleGroupSize);

        if (parentSize >= 0) {
            assertThat(concept.getParents().size()).isEqualTo(parentSize);
        } else {
            assertThat(concept.getParents().get(concept.getParents().size() - 1).getCode())
                    .isEqualTo("395508003");
        }
        assertThat(concept.getChildren().size()).isEqualTo(childSize);

    }

    private void validateDescExist(List<Map<String, String>> descriptions, String matchingTerm) {
        boolean descFound = false;

        for (Map<String, String> descriptionGroup : descriptions) {
            if (descriptionGroup.get(DESCRIPTION_TERM).equalsIgnoreCase(matchingTerm)) {
                descFound = true;
                break;
            }
        }

        assertTrue(descFound);
    }

    private void validateExportFiles(JsonNode root, String expectedFilePath) throws IOException {
        BufferedReader expectedFileReader = null;
        BufferedReader generatedFileReader = null;

        try {
            // Get Zipped File
            final String fileUrl = (root.get("url")).asText();
            logger.info("File Url: " + fileUrl);
            final String zipFileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            final String exportRefsetPath = properties.getProperty("REFSET_EXPORT_DIR");
            logger.info("Refset Directory: " + exportRefsetPath);
            final String downloadedZipFile = exportRefsetPath + "/" + zipFileName;
            logger.info("Zip File Path: " + downloadedZipFile);
            Path unzippedPath = Files.createTempDirectory("exportTest-");
            FileUtility.unzip(downloadedZipFile, unzippedPath.toFile().getAbsolutePath());

            assertThat(1).isEqualTo(unzippedPath.toFile().list().length);

            // Get generated File
            File generatedFile = unzippedPath.toFile().listFiles()[0];

            final SortedSet<String> generatedLines = new TreeSet<>();
            final SortedSet<String> testLines = new TreeSet<>();
            generatedFileReader = new BufferedReader(new FileReader(generatedFile));

            String st;
            while ((st = generatedFileReader.readLine()) != null) {
                generatedLines.add(st);
            }

            // Get test file
            expectedFileReader = new BufferedReader(new FileReader(expectedFilePath));
            while ((st = expectedFileReader.readLine()) != null) {
                testLines.add(st);
            }
            // Compare two but first disregard the header for the generated
            // contents.
            int j = 0;
            for (int i = 0; i < generatedLines.size(); i++) {
                String generatedLine = (String) generatedLines.toArray()[i];

                // If header line, just ignore altogether
                if (!generatedLine.startsWith("id\teffectiveTime")) {
                    String testLine = (String) testLines.toArray()[j++];

                    assertThat(testLine).isEqualTo(generatedLine);
                }
            }

            assertThat(testLines.size()).isEqualTo(j);
        } finally {
            if (expectedFileReader != null) {
                expectedFileReader.close();
            }

            if (generatedFileReader != null) {
                generatedFileReader.close();
            }
        }
    }

}
