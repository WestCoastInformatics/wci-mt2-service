package org.ihtsdo.refsetservice.rest.test.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.model.UpgradeInactiveConcecpt;
import org.ihtsdo.refsetservice.model.UpgradeReplacementConcecpt;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.util.ConceptResultList;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class EditUnitTestUtilities {

    public enum RefsetType {
        EXTENSIONAL, INTENSIONAL

    }

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(EditUnitTestUtilities.class);

    private MockMvc mvc;

    private String baseUrl;

    protected SimpleDateFormat sdf = null;

    private String testingProjectId;

    private String testingEditionId;

    private static final String TESTING_PARENT_CONCEPT = "446609009";

    private static final String TESTING_MODULE_ID = "900000000000207008";

    public EditUnitTestUtilities(final MockMvc mvc, final String baseUrl, final SimpleDateFormat sdf, final String testingProjectId, final String testingEditionId) {

        this.mvc = mvc;
        this.baseUrl = baseUrl;
        this.sdf = sdf;
        this.testingProjectId = testingProjectId;
        this.testingEditionId = testingEditionId;
    }

    public String createNewRefsetVersion(String refsetId) {

        try {

            final String url = baseUrl + "/" + refsetId + "/newVersion";
            logger.info("Testing url - " + url);

            final ObjectNode newVersionBody = new ObjectMapper().createObjectNode();// .put("readVersion",
            // "");

            final MvcResult newVersionResult =
                mvc.perform(post(url).content(newVersionBody.toString()).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

            final String newVersionContent = newVersionResult.getResponse().getContentAsString();
            logger.info(" content = " + newVersionContent);

            final JsonNode newVersionRoot = new ObjectMapper().readTree(newVersionContent);
            final JsonNode newVersionNode = newVersionRoot;

            assertThat(newVersionNode.has("refsetInternalId")).isTrue();

            final String newRefsetVersionInternalId = newVersionNode.get("refsetInternalId").asText();
            assertThat(newRefsetVersionInternalId).isNotNull();
            return newRefsetVersionInternalId;

        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }

    }

    public void modifyRefsetMetadata(String refsetId, Map<String, String> modifyData) {

        try {

            final String modifyUrl = baseUrl + "/" + refsetId;
            logger.info("Testing url - " + modifyUrl);

            // the body of the modification call
            final ObjectNode modifyBody = new ObjectMapper().createObjectNode().put("narrative", modifyData.get("narrative")).put("versionNotes", modifyData.get("versionNotes")).set("tags",
                new ObjectMapper().createArrayNode().add(modifyData.get("tag1")).add(modifyData.get("tag2")));

            final MvcResult modifyResult =
                mvc.perform(put(modifyUrl).content(modifyBody.toString()).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

            final String modifyContent = modifyResult.getResponse().getContentAsString();
            logger.info(" content = " + modifyContent);

            final JsonNode modifyRoot = new ObjectMapper().readTree(modifyContent);
            assertThat(modifyRoot.has("refsetInternalId")).isTrue();

        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    public Map<String, String> defineExtensionalRefsetConcept(String testName, String fileType, String memberFileName, String listFile) {

        final Map<String, String> refsetNewConcept = new HashMap<>();

        // Define the SctIds File
        refsetNewConcept.put("fileType", fileType);
        refsetNewConcept.put("conceptFileName", memberFileName);
        refsetNewConcept.put("conceptFile", listFile);

        return defineRefsetConcept(refsetNewConcept, testName, RefsetType.EXTENSIONAL);
    }

    public Map<String, String> defineExtensionalRefsetConcept(String testName, String memberConceptIds) {

        final Map<String, String> refsetNewConcept = new HashMap<>();

        // the data to create a refset from a list of Ids
        refsetNewConcept.put("list", memberConceptIds);

        return defineRefsetConcept(refsetNewConcept, testName, RefsetType.EXTENSIONAL);
    }

    private Map<String, String> defineRefsetConcept(Map<String, String> refsetNewConcept, String testName, RefsetType refsetType) {

        refsetNewConcept.put("name", "UnitTest New Extensional Refset for " + testName);
        refsetNewConcept.put("parentConceptId", TESTING_PARENT_CONCEPT);
        refsetNewConcept.put("moduleId", TESTING_MODULE_ID);
        refsetNewConcept.put("editionId", testingEditionId);
        refsetNewConcept.put("projectId", testingProjectId);
        refsetNewConcept.put("narrative", "Narative for " + testName);
        refsetNewConcept.put("type", refsetType.toString());
        refsetNewConcept.put("privateRefset", "false");
        refsetNewConcept.put("localSet", "false");
        refsetNewConcept.put("refsetDeleteStatus", "deleted");

        // prepare the call to create refset from a new concept
        final ObjectNode refsetNewConceptBody = new ObjectMapper().createObjectNode().put("name", refsetNewConcept.get("name")).put("parentConceptId", refsetNewConcept.get("parentConceptId"))
            .put("moduleId", refsetNewConcept.get("moduleId")).put("editionId", refsetNewConcept.get("editionId")).put("projectId", refsetNewConcept.get("projectId"))
            .put("narrative", refsetNewConcept.get("narrative")).put("type", refsetNewConcept.get("type")).put("privateRefset", Boolean.parseBoolean(refsetNewConcept.get("privateRefset")))
            .put("localSet", Boolean.parseBoolean(refsetNewConcept.get("localSet")));

        if (refsetNewConcept.containsKey("ecl")) {

            refsetNewConceptBody.set("definitionClauses",
                new ObjectMapper().createArrayNode().add(new ObjectMapper().createObjectNode().put("value", refsetNewConcept.get("ecl")).put("negated", "false")));
        }

        refsetNewConcept.put("body", refsetNewConceptBody.toString());

        return refsetNewConcept;
    }

    public Map<String, String> defineIntensionalRefsetConcept(String testName, String ecl) {

        final Map<String, String> refsetNewConcept = new HashMap<>();

        // the data to create a refset from a list of Ids
        refsetNewConcept.put("ecl", ecl);

        return defineRefsetConcept(refsetNewConcept, testName, RefsetType.INTENSIONAL);
    }

    public String createRefset(Map<String, String> refsetDetail) {

        try {

            // make the call to create refset from a new concept
            logger.debug("with : " + refsetDetail.get("body"));
            final MvcResult result =
                mvc.perform(post(baseUrl).content(refsetDetail.get("body")).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final JsonNode root = new ObjectMapper().readTree(content);
            final JsonNode refsetNode = root;
            Refset refset = null;

            assertThat(refsetNode.has("refsetId")).isTrue();
            final String refsetId = refsetNode.get("refsetId").asText();

            // verify the refset from a new concept in the RT2 DB
            try (final TerminologyService service = new TerminologyService()) {

                GetUnitTestUtilities getUtil = new GetUnitTestUtilities(mvc, baseUrl, sdf);

                refset = getUtil.getRefsetFromRefsetIdAndVersion(refsetId, Refset.IN_DEVELOPMENT);
                assertThat(refset).isNotNull();
                assertThat(refset.getName()).isEqualTo(refsetDetail.get("name"));
                assertThat(refset.getModuleId()).isEqualTo(refsetDetail.get("moduleId"));
                assertThat(refset.getEditionId()).isEqualTo(refsetDetail.get("editionId"));
                assertThat(refset.getProjectId()).isEqualTo(refsetDetail.get("projectId"));
                assertThat(refset.getNarrative()).isEqualTo(refsetDetail.get("narrative"));
                assertThat(refset.getType()).isEqualTo(refsetDetail.get("type"));
                assertThat(refset.isPrivateRefset()).isEqualTo(Boolean.parseBoolean(refsetDetail.get("privateRefset")));
                assertThat(refset.isLocalSet()).isEqualTo(Boolean.parseBoolean(refsetDetail.get("localSet")));

                assertThat(refset.getRefsetId()).isNotNull();

                if (refsetDetail.containsKey("refsetId")) {

                    assertThat(refset.getRefsetId()).isEqualTo(refsetDetail.get("refsetId"));
                }

            }

            return refset.getId();

        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }

    }

    public JsonNode populateRefset(String refsetInternalId, Map<String, String> refsetDetail) {

        // prepare and make call to add members to the refset
        String membersUrl = baseUrl + "/" + refsetInternalId + "/members?conceptIds=";
        MvcResult membersResult = null;

        try {

            if (!refsetDetail.containsKey("fileType")) {

                // For list or ECL-based defintion of concepts
                assertThat(refsetDetail.containsKey("ecl") || refsetDetail.containsKey("list")).isTrue();

                if (refsetDetail.containsKey("list")) {

                    membersUrl += refsetDetail.get("list");
                } else {

                    membersUrl += "&ecl=" + refsetDetail.get("ecl");
                }

                // make the call to add members to the refset from a new concept
                membersResult = mvc.perform(post(membersUrl).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

            } else {

                // For file-based definition of concepts
                MockMultipartFile mockMultipartFile =
                    new MockMultipartFile("conceptFile", refsetDetail.get("conceptFileName"), "text/plain", Files.readAllBytes(Paths.get(refsetDetail.get("conceptFile"))));

                membersResult =
                    mvc.perform(multipart(membersUrl + "&fileType=" + refsetDetail.get("fileType")).file(mockMultipartFile).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
            }

            // Validate population return object
            final String membersContent = membersResult.getResponse().getContentAsString();
            logger.info(" membersContent = " + membersContent);

            final JsonNode membersRoot = new ObjectMapper().readTree(membersContent);
            final JsonNode membersNode = membersRoot;

            assertThat(membersNode.has("status")).isTrue();
            assertThat(membersNode.get("status").asText().equals("All concepts added.")).isTrue();

            return membersNode;
        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }

    }

    public void validateRefsetContents(String refsetInternalId, int numConceptsAdded) {

        try {

            // Validate contents are as expected
            final String url = baseUrl + "/" + refsetInternalId + "/members?limit=500&offset=0" + "&displayType=list&refsetInternalId=" + refsetInternalId;

            logger.info("Testing url - " + url);

            final MvcResult countResult = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String countContent = countResult.getResponse().getContentAsString();
            logger.info(" content = " + countContent);

            final JsonNode conceptRoot = new ObjectMapper().readTree(countContent);
            assertThat(conceptRoot.get("total").asInt()).isEqualTo(numConceptsAdded);
        } catch (Exception e) {

            e.printStackTrace();
        }

    }
    
    public String resolveBackgroundOperation(String refsetInternalId) {
        
        try {
            
            boolean locked = true;
            String status = "";
            int callDelayMilliseconds = 1000;
            int callNumber = 0;
            final String url = baseUrl + "/" + refsetInternalId + "/isLocked";
            logger.info("Testing url - " + url);
            
            // keep testing to see if the refset has unlocked 
            while (locked) {
                
                callNumber++;
                final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
                status = result.getResponse().getContentAsString();
                logger.info(" status = " + status);
                
                if (status.equals("true")) {
                    
                    if (callNumber == 20) {
                        callDelayMilliseconds = 4000;

                    } else if (callNumber == 30) {
                        callDelayMilliseconds = 15000;
                    }
                    
                    try {
                        Thread.sleep(callDelayMilliseconds);
                    } catch(InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    locked = false;
                }
            }
            
            return status;
            
        } catch (Exception e) {
            
            e.printStackTrace();
            return null;
        }
    }

    public void removeRefsetContent(String refsetInternalId, Map<String, String> refsetDetail, JsonNode membersNode) {

        try {

            // remove the members of the refset from a new concept
            if (membersNode.has("status")) {

                MvcResult removeResult = null;

                if (refsetDetail.containsKey("fileType")) {

                    String removeUrl = baseUrl + "/" + refsetInternalId + "/removeMembers?conceptFile=";

                    // make the call to remove members
                    MockMultipartFile mockMultipartFile =
                        new MockMultipartFile("conceptFile", refsetDetail.get("conceptFileName"), "text/plain", Files.readAllBytes(Paths.get(refsetDetail.get("conceptFile"))));

                    removeResult = mvc.perform(multipart(removeUrl + "&fileType=" + refsetDetail.get("fileType")).file(mockMultipartFile).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
                        .andReturn();
                } else {

                    String removeUrl = baseUrl + "/" + refsetInternalId + "/removeMembers?conceptIds=";

                    if (refsetDetail.containsKey("ecl")) {

                        removeUrl += "&ecl=" + refsetDetail.get("ecl");
                    } else {

                        String additionalMemberIds = "";

                        // for one test we will try to remove a member that has
                        // already been published
                        if (refsetDetail.containsKey("additionalMemberIdsToRemove")) {

                            additionalMemberIds = "," + refsetDetail.get("additionalMemberIdsToRemove");
                        }

                        removeUrl += refsetDetail.get("list") + additionalMemberIds;
                    }

                    // make the call to remove the refset members
                    removeResult = mvc.perform(post(removeUrl).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

                }

                final String removeContent = removeResult.getResponse().getContentAsString();
                final JsonNode removeRoot = new ObjectMapper().readTree(removeContent);
                final JsonNode removeNode = removeRoot;

                assertThat(removeNode.has("status")).isTrue();
                assertThat(removeNode.get("status").asText().equals("All concepts removed.")).isTrue();
            }

        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    public void deleteVersionedRefset(String refsetId) {

        try {

            final String deleteUrl = baseUrl + "/" + refsetId + "/editVersion";
            final MvcResult deleteResult = mvc.perform(delete(deleteUrl)).andExpect(status().isOk()).andReturn();
            final String deleteContent = deleteResult.getResponse().getContentAsString();
            final JsonNode deleteRoot = new ObjectMapper().readTree(deleteContent);
            final JsonNode deleteNode = deleteRoot;

            assertThat(deleteNode.has("status")).isTrue();
            assertThat(deleteNode.get("status").asText().equals("deleted")).isTrue();

        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    public void deleteUnversionedRefset(String refsetInternalId, String deleteStatus) {

        try {

            // delete the refset from a new concept
            if (refsetInternalId != null && !refsetInternalId.equals("")) {

                final String deleteUrl = baseUrl + "/" + refsetInternalId;
                final MvcResult deleteResult = mvc.perform(delete(deleteUrl)).andExpect(status().isOk()).andReturn();
                final String deleteContent = deleteResult.getResponse().getContentAsString();
                final JsonNode deleteRoot = new ObjectMapper().readTree(deleteContent);
                final JsonNode deleteNode = deleteRoot;

                assertThat(deleteNode.has("status")).isTrue();
                assertThat(deleteNode.get("status").asText().equals(deleteStatus)).isTrue();
            } else {

                throw new Exception("Unable to identify refset to delete: " + refsetInternalId);
            }

        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    /**
     * deleteNewRefsetVerion
     * @return
     *
     * @throws Exception the exception
     */
    public boolean deleteRefsetVersion(String refsetVersionInternalId) {

        try (final TerminologyService service = new TerminologyService()) {

            // DELETE NEW VERSION
            final String deleteUrl = baseUrl + "/" + refsetVersionInternalId + "/editVersion";
            final MvcResult deleteResult = mvc.perform(delete(deleteUrl)).andExpect(status().isOk()).andReturn();
            final String deleteContent = deleteResult.getResponse().getContentAsString();
            final JsonNode deleteRoot = new ObjectMapper().readTree(deleteContent);
            final JsonNode deleteNode = deleteRoot;

            assertThat(deleteNode.has("status")).isTrue();
            assertThat(deleteNode.get("status").asText()).isEqualTo("deleted");

            // verify the original refset is back to the latest version

            Refset refset = service.get(refsetVersionInternalId, Refset.class);
            assertThat(refset).isNotNull();
            assertThat(refset.isLatestPublishedVersion()).isTrue();

            return true;
        } catch (Exception e) {

            e.printStackTrace();

            return false;
        }

    }

    /**
     * deleteNewRefsetVerion
     *
     * @throws Exception the exception
     */
    public boolean addMembers(String refsetInternalId, List<String> conceptIds) {

        String urlConceptIds = "";

        for (String conceptId : conceptIds) {

            urlConceptIds += conceptId + ",";
        }

        urlConceptIds = StringUtils.removeEnd(urlConceptIds, ",");

        try (final TerminologyService service = new TerminologyService()) {

            final String url = baseUrl + "/" + refsetInternalId + "/editVersion?conceptIds=" + conceptIds;

            final MvcResult result = mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);

            final JsonNode root = new ObjectMapper().readTree(content);
            assertThat(root.has("status")).isTrue();
            assertThat(root.get("status").asText()).isEqualTo("All concepts added.");

            return true;
        } catch (Exception e) {

            e.printStackTrace();

            return false;
        }

    }
    
    public void compileUpgradeData(String refsetInternalId) {
        
        try {
            
            final String url = baseUrl + "/" + refsetInternalId + "/compileUpgradeData";

            logger.info("Testing url - " + url);

            mvc.perform(get(url));

            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public ResultList<UpgradeInactiveConcecpt> getUpgradeData(String refsetInternalId) {
        
        try {
            
            final String url = baseUrl + "/" + refsetInternalId + "/upgradeData";

            logger.info("Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);
            
            final ResultList<UpgradeInactiveConcecpt> resultList = new ObjectMapper().readValue(content, (new TypeReference<ResultList<UpgradeInactiveConcecpt>>(){}));
            assertThat(resultList).isNotNull();
            
            return resultList;
            
        } catch (Exception e) {
            
            e.printStackTrace();
            return null;
        }
    }

    public void updateUpgradeConcept(String refsetInternalId, String changed, final String inactiveConceptId, final String replacementConceptId, UpgradeReplacementConcecpt manualReplacementConcept) {
    
        try {
            
            String url = baseUrl + "/" + refsetInternalId + "/modifyUpgradeConcept?inactiveConceptId=" + inactiveConceptId + "&changed=" + changed;
            String body = "";
            
            if (replacementConceptId != null) {
                url += "&replacementConceptId=" + replacementConceptId;
            }
            
            if (manualReplacementConcept != null) {
                body = manualReplacementConcept.toString();
            }
            
            logger.info("Testing url - " + url + " ; body: " + body);
    
            final MvcResult result = mvc.perform(post(url).content(body).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            logger.info(" content = " + content);
            
            assertThat(content).contains("All changes made successfully");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public ResultList<UpgradeReplacementConcecpt> searchReplacementConcepts(String internalRefsetId, String searchTerm) {

        try {

            final String url = "/refset/" + internalRefsetId + "/replacementConceptSearch?limit=10&query=" + searchTerm;

            logger.info("Testing url - " + url);
            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();

            logger.info(" content = " + content);
            final ResultList<UpgradeReplacementConcecpt> results = new ObjectMapper().readValue(content, (new TypeReference<ResultList<UpgradeReplacementConcecpt>>(){}));

            // Testing Results
            assertThat(results).isNotNull();
            return results;

        } catch (Exception e) {

            e.printStackTrace();
            return null;
        }

    }
}
