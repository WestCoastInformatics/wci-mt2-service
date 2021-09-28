
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
public class RefsetEditingTests extends AbstractRefsetTests {
    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(RefsetEditingTests.class);

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
        refsetDetails.add(refsetNewConcept);

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
        refsetEclMembers.put("ecl", "183814000 | Admission funding status (finding) |");

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
        refsetDetails.add(refsetEclMembers);

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
        refsetDetails.add(refsetRf2MemberAdd);

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
        refsetDetails.add(refsetExistingConcept);

        // TESTS BEGIN - loop over the details object to run various tests
        for (final Map<String, String> refsetDetail : refsetDetails) {

            // make the call to create refset from a new concept
            final MvcResult result = mvc.perform(post(baseUrl).content(refsetDetail.get("body"))
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

                if (refsetDetail.containsKey("refsetId")) {
                    assertThat(refset.getRefsetId()).isEqualTo(refsetDetail.get("refsetId"));
                }
            }

            // prepare the call to add members to the refset from a new concept
            String membersUrl = baseUrl + "/" + refsetInternalId + "/members?conceptIds=";
            MvcResult membersResult = null;

            if (!refsetDetail.containsKey("fileType")) {

                if (!refsetDetail.containsKey("ecl")) {
                    membersUrl += memberConceptIds;
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
                            additionalMemberIds =
                                    "," + refsetDetail.get("additionalMemberIdsToRemove");
                        }

                        removeUrl += memberConceptIds + additionalMemberIds;
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

                    removeResult = mvc.perform(
                            multipart(removeUrl + "&fileType=" + refsetDetail.get("fileType"))
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

}
