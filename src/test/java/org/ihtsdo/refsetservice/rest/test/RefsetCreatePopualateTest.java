
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.json.JacksonTester;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Integration tests for MetadataController.
 */

@AutoConfigureMockMvc
public class RefsetCreatePopualateTest extends AbstractRefsetTests {
    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(RefsetCreatePopualateTest.class);

    /**
     * Sets the up.
     */
    @BeforeEach
    public void setUp(TestInfo info) {

        if (testingEditionId != null && testingEditionId.isEmpty()) {

            objectMapper = new ObjectMapper();
            JacksonTester.initFields(this, objectMapper);
            baseUrl = "/refset";

            try {
                testingEditionId = getEditionInternalId(TESTING_EDITION_NAME);
                testingProjectId = getProjectInternalId(TESTING_PROJECT_NAME);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
    /**
     * Test creating, modifying, and deleting a new version of an existing
     * refset in edit mode.
     *
     * @throws Exception the exception
     */
    @Test
    public void testNewVersionCreateModifyDelete() throws Exception {

        final String originalRefsetInternalId =
                getRefsetInternalId(TESTING_REFSET_ID, TESTING_REFSET_VERSION);
        final String url = baseUrl + "/" + originalRefsetInternalId + "/newVersion";
        logger.info("Testing url - " + url);

        // ADD NEW VERSION
        final ObjectNode newVersionBody = objectMapper.createObjectNode();// .put("readVersion",
                                                                          // "");

        final MvcResult newVersionResult = mvc
                .perform(post(url).content(newVersionBody.toString())
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        final String newVersionContent = newVersionResult.getResponse().getContentAsString();
        logger.info(" content = " + newVersionContent);

        final JsonNode newVersionRoot = objectMapper.readTree(newVersionContent);
        final JsonNode newVersionNode = newVersionRoot;

        assertTrue(newVersionNode.has("refsetInternalId"));
        final String newRefsetInternalId = newVersionNode.get("refsetInternalId").asText();
        assertThat(newRefsetInternalId).isNotEqualTo(originalRefsetInternalId);
        logger.info("New Version Internal ID - " + newRefsetInternalId);

        // verify the new version
        try (final TerminologyService service = new TerminologyService()) {

            Refset refset = service.get(newRefsetInternalId, Refset.class);
            assertThat(refset).isNotNull();
            assertThat(refset.getRefsetId()).isEqualTo(TESTING_REFSET_ID);
            assertThat(refset.getVersionStatus()).isEqualTo(Refset.IN_DEVELOPMENT);
            assertThat(refset.getVersionDate()).isNull();
            assertTrue(refset.isLatestVersion());
        }

        // TODO - need to figure out why indexing is not writing fast enough and
        // get rid of this!
        Thread.sleep(500);

        // MODIFY NEW VERSION
        final String modifyUrl = baseUrl + "/" + newRefsetInternalId;
        logger.info("Testing url - " + modifyUrl);

        // the modification data
        final Map<String, String> modifyData = new HashMap<>();
        modifyData.put("tag1", "tag1");
        modifyData.put("tag2", "tag2");
        modifyData.put("versionNotes", testingProjectId);
        modifyData.put("narrative", "Test.");

        // the body of the modification call
        final ObjectNode modifyBody = objectMapper.createObjectNode()
                .put("narrative", modifyData.get("narrative"))
                .put("versionNotes", modifyData.get("versionNotes")).set("tags", objectMapper
                        .createArrayNode().add(modifyData.get("tag1")).add(modifyData.get("tag2")));

        final MvcResult modifyResult = mvc
                .perform(put(modifyUrl).content(modifyBody.toString())
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();

        final String modifyContent = modifyResult.getResponse().getContentAsString();
        logger.info(" content = " + modifyContent);

        final JsonNode modifyRoot = objectMapper.readTree(modifyContent);
        final JsonNode modifyNode = modifyRoot;

        assertTrue(modifyNode.has("refsetInternalId"));

        // verify the modifications
        try (final TerminologyService service = new TerminologyService()) {

            Refset refset = service.get(newRefsetInternalId, Refset.class);
            assertThat(refset.getNarrative()).isEqualTo(modifyData.get("narrative"));
            assertThat(refset.getVersionNotes()).isEqualTo(modifyData.get("versionNotes"));
            assertTrue(refset.getTags().contains(modifyData.get("tag1")));
            assertTrue(refset.getTags().contains(modifyData.get("tag2")));
        }

        // DELETE NEW VERSION
        final String deleteUrl = baseUrl + "/" + newRefsetInternalId + "/editVersion";
        final MvcResult deleteResult =
                mvc.perform(delete(deleteUrl)).andExpect(status().isOk()).andReturn();
        final String deleteContent = deleteResult.getResponse().getContentAsString();
        final JsonNode deleteRoot = objectMapper.readTree(deleteContent);
        final JsonNode deleteNode = deleteRoot;

        assertTrue(deleteNode.has("status"));
        assertTrue(deleteNode.get("status").asText().equals("deleted"));

        // verify the original refset is back to the latest version
        try (final TerminologyService service = new TerminologyService()) {

            Refset refset = service.get(originalRefsetInternalId, Refset.class);
            assertThat(refset).isNotNull();
            assertTrue(refset.isLatestVersion());
        }
    }

    /**
     * Test creating a refset concept and populating it from a list of sctids.
     *
     * @throws Exception the exception
     */
    @Test
    public void testCreateRefsetPopulateFromSctIdList() throws Exception {
        final String memberConceptIds = "48176007,280416009,10828004,260385009";

        final ObjectMapper mapper = new ObjectMapper();

        // the data to create a refset from a list of Ids
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

        // Define the member list
        refsetNewConcept.put("list", memberConceptIds);

        // prepare the call to create refset from a new concept
        final ObjectNode refsetNewConceptBody =
                mapper.createObjectNode().put("name", refsetNewConcept.get("name"))
                        .put("parentConceptId", refsetNewConcept.get("parentConceptId"))
                        .put("moduleId", refsetNewConcept.get("moduleId"))
                        .put("editionId", refsetNewConcept.get("editionId"))
                        .put("projectId", refsetNewConcept.get("projectId"))
                        .put("narrative", refsetNewConcept.get("narrative"))
                        .put("type", refsetNewConcept.get("type"))
                        .put("privateRefset",
                                Boolean.parseBoolean(refsetNewConcept.get("privateRefset")))
                        .put("localSet", Boolean.parseBoolean(refsetNewConcept.get("localSet")));

        refsetNewConcept.put("body", refsetNewConceptBody.toString());

        processAndPopulateRefset(refsetNewConcept, mapper, 4);
    }

    /**
     * Test creating a refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testCreateRefsetPopulateFromECL() throws Exception {

        final ObjectMapper mapper = new ObjectMapper();

        // the data to create a refset with members from ECL
        final Map<String, String> refsetEclMembers = new HashMap<>();
        refsetEclMembers.put("name", "ZZZ RT2 Test ECL Members Refset");
        refsetEclMembers.put("parentConceptId", "446609009");
        refsetEclMembers.put("moduleId", "900000000000207008");
        refsetEclMembers.put("editionId", testingEditionId);
        refsetEclMembers.put("projectId", testingProjectId);
        refsetEclMembers.put("narrative", "Test.");
        refsetEclMembers.put("type", "EXTENSIONAL");
        refsetEclMembers.put("privateRefset", "false");
        refsetEclMembers.put("localSet", "false");
        refsetEclMembers.put("refsetDeleteStatus", "deleted");

        // Define the ECL
        refsetEclMembers.put("ecl", "<< 183814000 | Admission funding status (finding) |");

        // prepare the call to create refset from a new concept
        final ObjectNode refsetEclMembersBody =
                mapper.createObjectNode().put("name", refsetEclMembers.get("name"))
                        .put("parentConceptId", refsetEclMembers.get("parentConceptId"))
                        .put("moduleId", refsetEclMembers.get("moduleId"))
                        .put("editionId", refsetEclMembers.get("editionId"))
                        .put("projectId", refsetEclMembers.get("projectId"))
                        .put("narrative", refsetEclMembers.get("narrative"))
                        .put("type", refsetEclMembers.get("type"))
                        .put("privateRefset",
                                Boolean.parseBoolean(refsetEclMembers.get("privateRefset")))
                        .put("localSet", Boolean.parseBoolean(refsetEclMembers.get("localSet")));

        refsetEclMembers.put("body", refsetEclMembersBody.toString());

        processAndPopulateRefset(refsetEclMembers, mapper, 3);
    }

    /**
     * Test creating a refset concept.
     *
     * @throws Exception the exception
     */
    @Test
    public void testCreateRefsetPopulateFromSctIdFile() throws Exception {

        final ObjectMapper mapper = new ObjectMapper();

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

        // Define the SctIds File
        refsetNewConcept.put("fileType", "list");
        refsetNewConcept.put("conceptFileName", MEMBER_ID_LIST_FILE_NAME);
        refsetNewConcept.put("conceptFile", MEMBER_ID_LIST_FILE);

        // prepare the call to create refset from a new concept
        final ObjectNode refsetNewConceptBody =
                mapper.createObjectNode().put("name", refsetNewConcept.get("name"))
                        .put("parentConceptId", refsetNewConcept.get("parentConceptId"))
                        .put("moduleId", refsetNewConcept.get("moduleId"))
                        .put("editionId", refsetNewConcept.get("editionId"))
                        .put("projectId", refsetNewConcept.get("projectId"))
                        .put("narrative", refsetNewConcept.get("narrative"))
                        .put("type", refsetNewConcept.get("type"))
                        .put("privateRefset",
                                Boolean.parseBoolean(refsetNewConcept.get("privateRefset")))
                        .put("localSet", Boolean.parseBoolean(refsetNewConcept.get("localSet")));

        refsetNewConcept.put("body", refsetNewConceptBody.toString());

        processAndPopulateRefset(refsetNewConcept, mapper, 4);
    }

    /**
     * Test creating a refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testCreateRefsetPopulateFromRF2() throws Exception {

        final ObjectMapper mapper = new ObjectMapper();

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

        // Define the Rf2 File
        refsetRf2MemberAdd.put("fileType", "rf2");
        refsetRf2MemberAdd.put("conceptFileName", MEMBER_ID_RF2_FILE_NAME);
        refsetRf2MemberAdd.put("conceptFile", MEMBER_ID_RF2_FILE);

        // prepare the call to create refset with members added from an RF2 file
        final ObjectNode refsetRf2MemberAddBody =
                mapper.createObjectNode().put("name", refsetRf2MemberAdd.get("name"))
                        .put("parentConceptId", refsetRf2MemberAdd.get("parentConceptId"))
                        .put("moduleId", refsetRf2MemberAdd.get("moduleId"))
                        .put("editionId", refsetRf2MemberAdd.get("editionId"))
                        .put("projectId", refsetRf2MemberAdd.get("projectId"))
                        .put("narrative", refsetRf2MemberAdd.get("narrative"))
                        .put("type", refsetRf2MemberAdd.get("type"))
                        .put("privateRefset",
                                Boolean.parseBoolean(refsetRf2MemberAdd.get("privateRefset")))
                        .put("localSet", Boolean.parseBoolean(refsetRf2MemberAdd.get("localSet")));

        refsetRf2MemberAdd.put("body", refsetRf2MemberAddBody.toString());

        processAndPopulateRefset(refsetRf2MemberAdd, mapper, 4);
    }

    /**
     * Test creating a refset.
     *
     * @throws Exception the exception
     */
    // Test is commented out for new as no means of "reverting" the concept from
    // refset concept to normal concept
    // @Test
    public void testCreateRefsetFromExistingConcept() throws Exception {

        final ObjectMapper mapper = new ObjectMapper();

        // the data to create a refset from an existing concept (but can't be a
        // refset already in RT2)
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
        // DO NOT LEAVE THIS UNCOMMENTED - for one test we will try to remove a
        // member that has already been published
        // refsetExistingConcept.put("additionalMemberIdsToRemove",
        // "734147008");

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
                .put("privateRefset",
                        Boolean.parseBoolean(refsetExistingConcept.get("privateRefset")))
                .put("localSet", Boolean.parseBoolean(refsetExistingConcept.get("localSet")));

        refsetExistingConcept.put("body", refsetExistingConceptBody.toString());

        processAndPopulateRefset(refsetExistingConcept, mapper, 0);
    }

    private void processAndPopulateRefset(Map<String, String> refsetDetail, ObjectMapper mapper,
        int numConceptsAdded) throws Exception {

        // make the call to create refset from a new concept
        final MvcResult result = mvc
                .perform(post(baseUrl).content(refsetDetail.get("body"))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
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
            assertThat(refset.isPrivateRefset())
                    .isEqualTo(Boolean.parseBoolean(refsetDetail.get("privateRefset")));
            assertThat(refset.isLocalSet())
                    .isEqualTo(Boolean.parseBoolean(refsetDetail.get("localSet")));

            assertThat(refset.getRefsetId()).isNotNull();
            if (refsetDetail.containsKey("refsetId")) {
                assertThat(refset.getRefsetId()).isEqualTo(refsetDetail.get("refsetId"));
            }
        }

        // prepare and make call to add members to the refset
        String membersUrl = baseUrl + "/" + refsetInternalId + "/members?conceptIds=";
        MvcResult membersResult = null;

        if (!refsetDetail.containsKey("fileType")) {

            assertThat(refsetDetail.containsKey("ecl") || refsetDetail.containsKey("list"))
                    .isTrue();
            if (refsetDetail.containsKey("list")) {
                membersUrl += refsetDetail.get("list");
            } else {
                membersUrl += "&ecl=" + refsetDetail.get("ecl");
            }

            // make the call to add members to the refset from a new concept
            membersResult = mvc.perform(post(membersUrl).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn();

        } else {

            MockMultipartFile mockMultipartFile = new MockMultipartFile("conceptFile",
                    refsetDetail.get("conceptFileName"), "text/plain",
                    Files.readAllBytes(Paths.get(refsetDetail.get("conceptFile"))));

            membersResult = mvc
                    .perform(multipart(membersUrl + "&fileType=" + refsetDetail.get("fileType"))
                            .file(mockMultipartFile).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn();
        }

        final String membersContent = membersResult.getResponse().getContentAsString();
        logger.info(" membersContent = " + membersContent);

        final JsonNode membersRoot = mapper.readTree(membersContent);
        final JsonNode membersNode = membersRoot;

        assertTrue(membersNode.has("status"));
        assertTrue(membersNode.get("status").asText().equals("All concepts added."));

        // Verifying contents as a second measure
        final String url = baseUrl + "/" + refsetInternalId + "/members?limit=500&offset=0"
                + "&displayType=list&refsetInternalId=" + refsetInternalId;

        logger.info("Testing url - " + url);

        final MvcResult countResult = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        final String countContent = countResult.getResponse().getContentAsString();
        logger.info(" content = " + countContent);

        final JsonNode conceptRoot = mapper.readTree(countContent);
        assertThat(conceptRoot.get("total").asInt()).isEqualTo(numConceptsAdded);

        // remove the members of the refset from a new concept
        if (membersNode.has("status")) {

            String removeUrl = baseUrl + "/" + refsetInternalId + "/removeMembers?conceptIds=";
            MvcResult removeResult = null;

            if (!refsetDetail.containsKey("fileType")) {

                if (!refsetDetail.containsKey("ecl")) {

                    String additionalMemberIds = "";

                    // for one test we will try to remove a member that has
                    // already been published
                    if (refsetDetail.containsKey("additionalMemberIdsToRemove")) {
                        additionalMemberIds = "," + refsetDetail.get("additionalMemberIdsToRemove");
                    }

                    removeUrl += refsetDetail.get("list") + additionalMemberIds;
                } else {
                    removeUrl += "&ecl=" + refsetDetail.get("ecl");
                }

                // make the call to add members to the refset from a new
                // concept
                removeResult = mvc.perform(post(removeUrl).accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk()).andReturn();

            } else {

                MockMultipartFile mockMultipartFile = new MockMultipartFile("conceptFile",
                        refsetDetail.get("conceptFileName"), "text/plain",
                        Files.readAllBytes(Paths.get(refsetDetail.get("conceptFile"))));

                removeResult = mvc
                        .perform(multipart(removeUrl + "&fileType=" + refsetDetail.get("fileType"))
                                .file(mockMultipartFile).accept(MediaType.APPLICATION_JSON))
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
            final MvcResult deleteResult =
                    mvc.perform(delete(deleteUrl)).andExpect(status().isOk()).andReturn();
            final String deleteContent = deleteResult.getResponse().getContentAsString();
            final JsonNode deleteRoot = mapper.readTree(deleteContent);
            final JsonNode deleteNode = deleteRoot;

            assertTrue(deleteNode.has("status"));
            assertTrue(deleteNode.get("status").asText()
                    .equals(refsetDetail.get("refsetDeleteStatus")));
        }
    }

}
