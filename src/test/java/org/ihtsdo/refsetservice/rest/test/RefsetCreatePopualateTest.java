
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.rest.test.util.ExportUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.GetUnitTestUtilities;
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

    private static String mainTestingRefsetInternalId;

    /**
     * Sets the up.
     */
    @BeforeEach
    public void setUp(TestInfo info) {
        if (getUtil == null) {
            getUtil = new GetUnitTestUtilities(mvc, baseUrl, SIMPLE_DATE_FORMAT);
            exportUtil = new ExportUnitTestUtilities(mvc);
            editUtil = new EditUnitTestUtilities(mvc, baseUrl, SIMPLE_DATE_FORMAT);
        }

        if (testingEditionId != null && testingEditionId.isEmpty()) {

            objectMapper = new ObjectMapper();
            JacksonTester.initFields(this, objectMapper);
            baseUrl = "/refset";

            try {
                testingEditionId = getUtil.getEditionInternalId(TESTING_EDITION_NAME);
                testingProjectId = getUtil.getProjectInternalId(TESTING_PROJECT_NAME);
                mainTestingRefsetInternalId = getUtil.getRefsetInternalId(MAIN_TESTING_REFSET_ID,
                        MAIN_TESTING_REFSET_VERSION);

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
    public void testCreateNewVersionModifyDelete() throws Exception {

        // ADD NEW VERSION
        final String newRefsetInternalId =
                editUtil.createNewRefsetVersion(mainTestingRefsetInternalId);
        assertThat(newRefsetInternalId).isNotEqualTo(mainTestingRefsetInternalId);
        logger.info("New Version Internal ID - " + newRefsetInternalId);

        // validate the new version
        try (final TerminologyService service = new TerminologyService()) {

            Refset refset = service.get(newRefsetInternalId, Refset.class);
            assertThat(refset).isNotNull();
            assertThat(refset.getRefsetId()).isEqualTo(MAIN_TESTING_REFSET_ID);
            assertThat(refset.getVersionStatus()).isEqualTo(Refset.IN_DEVELOPMENT);
            assertThat(refset.getVersionDate()).isNull();
            assertThat(refset.isLatestVersion()).isTrue();
        }

        // MODIFY NEW VERSION
        // Define the modifications
        final Map<String, String> modifyData = new HashMap<>();
        modifyData.put("tag1", "tag1");
        modifyData.put("tag2", "tag2");
        modifyData.put("versionNotes", testingProjectId);
        modifyData.put("narrative", "Test.");

        editUtil.modifyRefsetMetadata(newRefsetInternalId, modifyData);

        // validate the modifications
        try (final TerminologyService service = new TerminologyService()) {

            Refset refset = service.get(newRefsetInternalId, Refset.class);

            // Validate modified data
            assertThat(refset.getNarrative()).isEqualTo(modifyData.get("narrative"));
            assertThat(refset.getVersionNotes()).isEqualTo(modifyData.get("versionNotes"));
            assertThat(refset.getTags().contains(modifyData.get("tag1"))).isTrue();
            assertThat(refset.getTags().contains(modifyData.get("tag2"))).isTrue();

            // Validate refset versioning
            assertThat(refset).isNotNull();
            assertThat(refset.isLatestVersion()).isTrue();

            refset = service.get(mainTestingRefsetInternalId, Refset.class);
            assertThat(refset).isNotNull();
            assertThat(refset.isLatestVersion()).isFalse();
        }

        // DELETE NEW VERSION
        editUtil.deleteRefset(newRefsetInternalId);

        // validate the original refset is back to the latest version
        try (final TerminologyService service = new TerminologyService()) {

            Refset refset = service.get(mainTestingRefsetInternalId, Refset.class);
            assertThat(refset).isNotNull();
            assertThat(refset.isLatestVersion()).isTrue();
        }
    }

    /**
     * Test creating a refset concept and populating it from a list of sctids.
     *
     * @throws Exception the exception
     */
    @Test
    // JESSE
    public void testCreateRefsetPopulateFromSctIdList() throws Exception {
        final String memberConceptIds = "48176007000,280416009000,10828004000,260385009";

        // the data to create a refset from a list of Ids
        final Map<String, String> refsetNewConcept = new HashMap<>();
        refsetNewConcept.put("name", "ZZZ RT2 Test New Extensinoal Refset");
        refsetNewConcept.put("type", "EXTENSIONAL");

        // Define the member list
        refsetNewConcept.put("list", memberConceptIds);

        // prepare the call to create refset from a new concept
        final ObjectNode refsetNewConceptBody =
                new ObjectMapper().createObjectNode().put("name", refsetNewConcept.get("name"))
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

        processAndPopulateRefset(refsetNewConcept, 4);
    }

    /**
     * Test creating a refset concept and populating it from a list of sctids.
     *
     * @throws Exception the exception
     */
    @Test
    public void testCreateIntensionalRefset() throws Exception {

        final String memberConceptIds = "48176007,280416009,10828004,260385009";

        // the data to create a refset from a list of Ids
        final Map<String, String> refsetNewConcept = new HashMap<>();
        refsetNewConcept.put("name", "ZZZ RT2 Test New Intensional Refset");
        refsetNewConcept.put("parentConceptId", "446609009");
        refsetNewConcept.put("moduleId", "900000000000207008");
        refsetNewConcept.put("editionId", testingEditionId);
        refsetNewConcept.put("projectId", testingProjectId);
        refsetNewConcept.put("narrative", "Test.");
        refsetNewConcept.put("type", "INTENSIONAL");
        refsetNewConcept.put("privateRefset", "false");
        refsetNewConcept.put("localSet", "false");
        refsetNewConcept.put("refsetDeleteStatus", "deleted");
        refsetNewConcept.put("fileType", "list");
        refsetNewConcept.put("conceptFileName", MEMBER_ID_LIST_FILE_NAME);
        refsetNewConcept.put("conceptFile", MEMBER_ID_LIST_FILE);

        // Define the member list
        refsetNewConcept.put("list", memberConceptIds);

        // prepare the call to create refset from a new concept

        final ObjectNode refsetNewConceptBody = new ObjectMapper().createObjectNode()
                .put("name", refsetNewConcept.get("name"))
                .put("parentConceptId", refsetNewConcept.get("parentConceptId"))
                .put("moduleId", refsetNewConcept.get("moduleId"))
                .put("editionId", refsetNewConcept.get("editionId"))
                .put("projectId", refsetNewConcept.get("projectId"))
                .put("narrative", refsetNewConcept.get("narrative"))
                .put("type", refsetNewConcept.get("type"))
                .put("privateRefset", Boolean.parseBoolean(refsetNewConcept.get("privateRefset")))
                .put("localSet", Boolean.parseBoolean(refsetNewConcept.get("localSet")))
                .set("definitionClauses", new ObjectMapper().createArrayNode()
                        .add(new ObjectMapper().createObjectNode().put("value",
                                "<< 277457005 |Histological grading systems (staging scale)|")
                                .put("negated", "false")));

        refsetNewConcept.put("body", refsetNewConceptBody.toString());

        processAndPopulateRefset(refsetNewConcept, 4);
    }

    /**
     * Test creating a refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testCreateRefsetPopulateFromECL() throws Exception {

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
                new ObjectMapper().createObjectNode().put("name", refsetEclMembers.get("name"))
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

        processAndPopulateRefset(refsetEclMembers, 3);
    }

    /**
     * Test creating a refset concept.
     *
     * @throws Exception the exception
     */
    @Test
    public void testCreateRefsetPopulateFromSctIdFile() throws Exception {

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
                new ObjectMapper().createObjectNode().put("name", refsetNewConcept.get("name"))
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

        processAndPopulateRefset(refsetNewConcept, 4);
    }

    /**
     * Test creating a refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testCreateRefsetPopulateFromRF2() throws Exception {

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
                new ObjectMapper().createObjectNode().put("name", refsetRf2MemberAdd.get("name"))
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

        processAndPopulateRefset(refsetRf2MemberAdd, 4);
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
        final ObjectNode refsetExistingConceptBody = new ObjectMapper().createObjectNode()
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

        processAndPopulateRefset(refsetExistingConcept, 0);
    }

    // JESSE
    private void processAndPopulateRefset(Map<String, String> refsetDetail, int numConceptsAdded)
        throws Exception {

        // make the call to create refset from a new concept
        final MvcResult result = mvc
                .perform(post(baseUrl).content(refsetDetail.get("body"))
                        .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
        final String content = result.getResponse().getContentAsString();
        logger.info(" content = " + content);

        final JsonNode root = new ObjectMapper().readTree(content);
        final JsonNode refsetNode = root;

        assertThat(refsetNode.has("refsetInternalId")).isTrue();
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

        final JsonNode membersRoot = new ObjectMapper().readTree(membersContent);
        final JsonNode membersNode = membersRoot;

        assertThat(membersNode.has("status")).isTrue();
        assertThat(membersNode.get("status").asText().equals("All concepts added.")).isTrue();

        // Verifying contents as a second measure
        final String url = baseUrl + "/" + refsetInternalId + "/members?limit=500&offset=0"
                + "&displayType=list&refsetInternalId=" + refsetInternalId;

        logger.info("Testing url - " + url);

        final MvcResult countResult = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
        final String countContent = countResult.getResponse().getContentAsString();
        logger.info(" content = " + countContent);

        final JsonNode conceptRoot = new ObjectMapper().readTree(countContent);
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
            final JsonNode removeRoot = new ObjectMapper().readTree(removeContent);
            final JsonNode removeNode = removeRoot;

            assertThat(removeNode.has("status")).isTrue();
            assertThat(removeNode.get("status").asText().equals("All concepts removed.")).isTrue();
        }

        // delete the refset from a new concept
        if (refsetInternalId != null && !refsetInternalId.equals("")) {

            final String deleteUrl = baseUrl + "/" + refsetInternalId;
            final MvcResult deleteResult =
                    mvc.perform(delete(deleteUrl)).andExpect(status().isOk()).andReturn();
            final String deleteContent = deleteResult.getResponse().getContentAsString();
            final JsonNode deleteRoot = new ObjectMapper().readTree(deleteContent);
            final JsonNode deleteNode = deleteRoot;

            assertThat(deleteNode.has("status")).isTrue();
            assertThat(deleteNode.get("status").asText()
                    .equals(refsetDetail.get("refsetDeleteStatus"))).isTrue();
        }
    }

}
