/*
 * Copyright 2023 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */
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
import org.ihtsdo.refsetservice.model.RefsetMemberComparison;
import org.ihtsdo.refsetservice.model.UpgradeInactiveConcept;
import org.ihtsdo.refsetservice.model.UpgradeReplacementConcept;
import org.ihtsdo.refsetservice.service.TerminologyService;
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

/**
 * The Class EditUnitTestUtilities.
 */
public class EditUnitTestUtilities {

    /**
     * The Enum RefsetType.
     */
    public enum RefsetType {

        /** The extensional. */
        EXTENSIONAL,
        /** The intensional. */
        INTENSIONAL

    }

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(EditUnitTestUtilities.class);

    /** The mvc. */
    private MockMvc mvc;

    /** The base url. */
    private String baseUrl;

    /** The sdf. */
    private SimpleDateFormat sdf = null;

    /** The testing project id. */
    private String testingProjectId;

    /** The testing edition id. */
    private String testingEditionId;

    /** The Constant TESTING_PARENT_CONCEPT. */
    private static final String TESTING_PARENT_CONCEPT = "446609009";

    /** The Constant TESTING_MODULE_ID. */
    private static final String TESTING_MODULE_ID = "900000000000207008";

    /**
     * Instantiates a {@link EditUnitTestUtilities} from the specified parameters.
     *
     * @param mvc the mvc
     * @param baseUrl the base url
     * @param sdf the sdf
     * @param testingProjectId the testing project id
     * @param testingEditionId the testing edition id
     */
    public EditUnitTestUtilities(final MockMvc mvc, final String baseUrl, final SimpleDateFormat sdf, final String testingProjectId,
        final String testingEditionId) {

        this.mvc = mvc;
        this.baseUrl = baseUrl;
        this.sdf = sdf;
        this.testingProjectId = testingProjectId;
        this.testingEditionId = testingEditionId;
    }

    /**
     * Creates the new refset version.
     *
     * @param refsetId the refset id
     * @return the string
     */
    public String createNewRefsetVersion(final String refsetId) {

        try {

            final String url = baseUrl + "/" + refsetId + "/newVersion";
            LOG.info("Testing url - " + url);

            final ObjectNode newVersionBody = new ObjectMapper().createObjectNode();
            // .put("readVersion","");

            final MvcResult newVersionResult =
                mvc.perform(post(url).content(newVersionBody.toString()).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn();

            final String newVersionContent = newVersionResult.getResponse().getContentAsString();
            LOG.info(" content = " + newVersionContent);

            final JsonNode newVersionRoot = new ObjectMapper().readTree(newVersionContent);
            final JsonNode newVersionNode = newVersionRoot;

            assertThat(newVersionNode.has("refsetInternalId")).isTrue();

            final String newRefsetVersionInternalId = newVersionNode.get("refsetInternalId").asText();
            assertThat(newRefsetVersionInternalId).isNotNull();
            return newRefsetVersionInternalId;

        } catch (final Exception e) {

            e.printStackTrace();

            return null;
        }

    }

    /**
     * Modify refset metadata.
     *
     * @param refsetId the refset id
     * @param modifyData the modify data
     */
    public void modifyRefsetMetadata(final String refsetId, final Map<String, String> modifyData) {

        try {

            final String modifyUrl = baseUrl + "/" + refsetId;
            LOG.info("Testing url - " + modifyUrl);

            // the body of the modification call
            final ObjectNode modifyBody =
                new ObjectMapper().createObjectNode().put("narrative", modifyData.get("narrative")).put("versionNotes", modifyData.get("versionNotes"))
                    .set("tags", new ObjectMapper().createArrayNode().add(modifyData.get("tag1")).add(modifyData.get("tag2")));

            final MvcResult modifyResult =
                mvc.perform(put(modifyUrl).content(modifyBody.toString()).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn();

            final String modifyContent = modifyResult.getResponse().getContentAsString();
            LOG.info(" content = " + modifyContent);

            final JsonNode modifyRoot = new ObjectMapper().readTree(modifyContent);
            assertThat(modifyRoot.has("refsetInternalId")).isTrue();

        } catch (final Exception e) {

            e.printStackTrace();
        }

    }

    /**
     * Define extensional refset concept.
     *
     * @param testName the test name
     * @param fileType the file type
     * @param memberFileName the member file name
     * @param listFile the list file
     * @return the map
     */
    public Map<String, String> defineExtensionalRefsetConcept(final String testName, final String fileType, final String memberFileName,
        final String listFile) {

        final Map<String, String> refsetNewConcept = new HashMap<>();

        // Define the SctIds File
        refsetNewConcept.put("fileType", fileType);
        refsetNewConcept.put("conceptFileName", memberFileName);
        refsetNewConcept.put("conceptFile", listFile);

        return defineRefsetConcept(refsetNewConcept, testName, RefsetType.EXTENSIONAL);
    }

    /**
     * Define extensional refset concept.
     *
     * @param testName the test name
     * @param memberConceptIds the member concept ids
     * @return the map
     */
    public Map<String, String> defineExtensionalRefsetConcept(final String testName, final String memberConceptIds) {

        final Map<String, String> refsetNewConcept = new HashMap<>();

        // the data to create a refset from a list of Ids
        refsetNewConcept.put("list", memberConceptIds);

        return defineRefsetConcept(refsetNewConcept, testName, RefsetType.EXTENSIONAL);
    }

    /**
     * Define refset concept.
     *
     * @param refsetNewConcept the refset new concept
     * @param testName the test name
     * @param refsetType the refset type
     * @return the map
     */
    private Map<String, String> defineRefsetConcept(final Map<String, String> refsetNewConcept, final String testName, final RefsetType refsetType) {

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
        final ObjectNode refsetNewConceptBody =
            new ObjectMapper().createObjectNode().put("name", refsetNewConcept.get("name")).put("parentConceptId", refsetNewConcept.get("parentConceptId"))
                .put("moduleId", refsetNewConcept.get("moduleId")).put("editionId", refsetNewConcept.get("editionId"))
                .put("projectId", refsetNewConcept.get("projectId")).put("narrative", refsetNewConcept.get("narrative"))
                .put("type", refsetNewConcept.get("type")).put("privateRefset", Boolean.parseBoolean(refsetNewConcept.get("privateRefset")))
                .put("localSet", Boolean.parseBoolean(refsetNewConcept.get("localSet")));

        if (refsetNewConcept.containsKey("ecl")) {

            refsetNewConceptBody.set("definitionClauses", new ObjectMapper().createArrayNode()
                .add(new ObjectMapper().createObjectNode().put("value", refsetNewConcept.get("ecl")).put("negated", "false")));
        }

        refsetNewConcept.put("body", refsetNewConceptBody.toString());

        return refsetNewConcept;
    }

    /**
     * Define intensional refset concept.
     *
     * @param testName the test name
     * @param ecl the ecl
     * @return the map
     */
    public Map<String, String> defineIntensionalRefsetConcept(final String testName, final String ecl) {

        final Map<String, String> refsetNewConcept = new HashMap<>();

        // the data to create a refset from a list of Ids
        refsetNewConcept.put("ecl", ecl);

        return defineRefsetConcept(refsetNewConcept, testName, RefsetType.INTENSIONAL);
    }

    /**
     * Creates the refset.
     *
     * @param refsetDetail the refset detail
     * @return the string
     */
    public String createRefset(final Map<String, String> refsetDetail) {

        try {

            // make the call to create refset from a new concept
            LOG.debug("with : " + refsetDetail.get("body"));
            final MvcResult result =
                mvc.perform(post(baseUrl).content(refsetDetail.get("body")).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            LOG.info(" content = " + content);

            final JsonNode root = new ObjectMapper().readTree(content);
            final JsonNode refsetNode = root;
            Refset refset = null;

            assertThat(refsetNode.has("refsetId")).isTrue();
            final String refsetId = refsetNode.get("refsetId").asText();

            // verify the refset from a new concept in the RT2 DB
            try (final TerminologyService service = new TerminologyService()) {

                final GetUnitTestUtilities getUtil = new GetUnitTestUtilities(mvc, baseUrl, sdf);

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

        } catch (final Exception e) {

            e.printStackTrace();

            return null;
        }

    }

    /**
     * Populate refset.
     *
     * @param refsetInternalId the refset internal id
     * @param refsetDetail the refset detail
     * @return the json node
     */
    public JsonNode populateRefset(final String refsetInternalId, final Map<String, String> refsetDetail) {

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
                final MockMultipartFile mockMultipartFile = new MockMultipartFile("conceptFile", refsetDetail.get("conceptFileName"), "text/plain",
                    Files.readAllBytes(Paths.get(refsetDetail.get("conceptFile"))));

                membersResult =
                    mvc.perform(multipart(membersUrl + "&fileType=" + refsetDetail.get("fileType")).file(mockMultipartFile).accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk()).andReturn();
            }

            // Validate population return object
            final String membersContent = membersResult.getResponse().getContentAsString();
            LOG.info(" membersContent = " + membersContent);

            final JsonNode membersRoot = new ObjectMapper().readTree(membersContent);
            final JsonNode membersNode = membersRoot;

            assertThat(membersNode.has("status")).isTrue();
            assertThat(membersNode.get("status").asText().equals("All concepts added.")).isTrue();

            return membersNode;
        } catch (final Exception e) {

            e.printStackTrace();

            return null;
        }

    }

    /**
     * Validate refset contents.
     *
     * @param refsetInternalId the refset internal id
     * @param numConceptsAdded the num concepts added
     */
    public void validateRefsetContents(final String refsetInternalId, final int numConceptsAdded) {

        try {

            // Validate contents are as expected
            final String url = baseUrl + "/" + refsetInternalId + "/members?limit=500&offset=0" + "&displayType=list&refsetInternalId=" + refsetInternalId;

            LOG.info("Testing url - " + url);

            final MvcResult countResult = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String countContent = countResult.getResponse().getContentAsString();
            LOG.info(" content = " + countContent);

            final JsonNode conceptRoot = new ObjectMapper().readTree(countContent);
            assertThat(conceptRoot.get("total").asInt()).isEqualTo(numConceptsAdded);
        } catch (final Exception e) {

            e.printStackTrace();
        }

    }

    /**
     * Resolve background operation.
     *
     * @param refsetInternalId the refset internal id
     * @return the string
     */
    public String resolveBackgroundOperation(final String refsetInternalId) {

        try {

            boolean locked = true;
            String status = "";
            int callDelayMilliseconds = 1000;
            int callNumber = 0;
            final String url = baseUrl + "/" + refsetInternalId + "/isLocked";
            LOG.info("Testing url - " + url);

            // keep testing to see if the refset has unlocked
            while (locked) {

                callNumber++;
                final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
                status = result.getResponse().getContentAsString();
                LOG.info(" status = " + status);

                if (status.equals("true")) {

                    if (callNumber == 20) {
                        callDelayMilliseconds = 4000;

                    } else if (callNumber == 30) {
                        callDelayMilliseconds = 15000;
                    }

                    try {
                        Thread.sleep(callDelayMilliseconds);
                    } catch (final InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                } else {
                    locked = false;
                }
            }

            return status;

        } catch (final Exception e) {

            e.printStackTrace();
            return null;
        }
    }

    /**
     * Removes the refset content.
     *
     * @param refsetInternalId the refset internal id
     * @param refsetDetail the refset detail
     * @param membersNode the members node
     */
    public void removeRefsetContent(final String refsetInternalId, final Map<String, String> refsetDetail, final JsonNode membersNode) {

        try {

            // remove the members of the refset from a new concept
            if (membersNode.has("status")) {

                MvcResult removeResult = null;

                if (refsetDetail.containsKey("fileType")) {

                    final String removeUrl = baseUrl + "/" + refsetInternalId + "/removeMembers?conceptFile=";

                    // make the call to remove members
                    final MockMultipartFile mockMultipartFile = new MockMultipartFile("conceptFile", refsetDetail.get("conceptFileName"), "text/plain",
                        Files.readAllBytes(Paths.get(refsetDetail.get("conceptFile"))));

                    removeResult = mvc
                        .perform(multipart(removeUrl + "&fileType=" + refsetDetail.get("fileType")).file(mockMultipartFile).accept(MediaType.APPLICATION_JSON))
                        .andExpect(status().isOk()).andReturn();
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

        } catch (final Exception e) {

            e.printStackTrace();
        }

    }

    /**
     * Delete versioned refset.
     *
     * @param refsetId the refset id
     */
    public void deleteVersionedRefset(final String refsetId) {

        try {

            final String deleteUrl = baseUrl + "/" + refsetId + "/editVersion";
            final MvcResult deleteResult = mvc.perform(delete(deleteUrl)).andExpect(status().isOk()).andReturn();
            final String deleteContent = deleteResult.getResponse().getContentAsString();
            final JsonNode deleteRoot = new ObjectMapper().readTree(deleteContent);
            final JsonNode deleteNode = deleteRoot;

            assertThat(deleteNode.has("status")).isTrue();
            assertThat(deleteNode.get("status").asText().equals("deleted")).isTrue();

        } catch (final Exception e) {

            e.printStackTrace();
        }

    }

    /**
     * Delete unversioned refset.
     *
     * @param refsetInternalId the refset internal id
     * @param deleteStatus the delete status
     */
    public void deleteUnversionedRefset(final String refsetInternalId, final String deleteStatus) {

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

        } catch (final Exception e) {

            e.printStackTrace();
        }

    }

    /**
     * deleteNewRefsetVerion.
     *
     * @param refsetVersionInternalId the refset version internal id
     * @return true, if successful
     */
    public boolean deleteRefsetVersion(final String refsetVersionInternalId) {

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

            final Refset refset = service.get(refsetVersionInternalId, Refset.class);
            assertThat(refset).isNotNull();
            assertThat(refset.isLatestPublishedVersion()).isTrue();

            return true;
        } catch (final Exception e) {

            e.printStackTrace();

            return false;
        }

    }

    /**
     * deleteNewRefsetVerion.
     *
     * @param refsetInternalId the refset internal id
     * @param conceptIds the concept ids
     * @return true, if successful
     */
    public boolean addMembers(final String refsetInternalId, final List<String> conceptIds) {

        String urlConceptIds = "";

        for (final String conceptId : conceptIds) {

            urlConceptIds += conceptId + ",";
        }

        urlConceptIds = StringUtils.removeEnd(urlConceptIds, ",");

        try (final TerminologyService service = new TerminologyService()) {

            final String url = baseUrl + "/" + refsetInternalId + "/editVersion?conceptIds=" + conceptIds;

            final MvcResult result =
                mvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andReturn();

            final String content = result.getResponse().getContentAsString();
            LOG.info(" content = " + content);

            final JsonNode root = new ObjectMapper().readTree(content);
            assertThat(root.has("status")).isTrue();
            assertThat(root.get("status").asText()).isEqualTo("All concepts added.");

            return true;
        } catch (final Exception e) {

            e.printStackTrace();

            return false;
        }

    }

    /**
     * Compile upgrade data.
     *
     * @param refsetInternalId the refset internal id
     */
    public void compileUpgradeData(final String refsetInternalId) {

        try {

            final String url = baseUrl + "/" + refsetInternalId + "/compileUpgradeData";

            LOG.info("Testing url - " + url);

            mvc.perform(get(url));

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the upgrade data.
     *
     * @param refsetInternalId the refset internal id
     * @return the upgrade data
     */
    public ResultList<UpgradeInactiveConcept> getUpgradeData(final String refsetInternalId) {

        try {

            final String url = baseUrl + "/" + refsetInternalId + "/upgradeData";

            LOG.info("Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            LOG.info(" content = " + content);

            final ResultList<UpgradeInactiveConcept> resultList =
                new ObjectMapper().readValue(content, (new TypeReference<ResultList<UpgradeInactiveConcept>>() {
                }));
            assertThat(resultList).isNotNull();

            return resultList;

        } catch (final Exception e) {

            e.printStackTrace();
            return null;
        }
    }

    /**
     * Update upgrade concept.
     *
     * @param refsetInternalId the refset internal id
     * @param changed the changed
     * @param inactiveConceptId the inactive concept id
     * @param replacementConceptId the replacement concept id
     * @param manualReplacementConcept the manual replacement concept
     */
    public void updateUpgradeConcept(final String refsetInternalId, final String changed, final String inactiveConceptId, final String replacementConceptId,
        final UpgradeReplacementConcept manualReplacementConcept) {

        try {

            String url = baseUrl + "/" + refsetInternalId + "/modifyUpgradeConcept?inactiveConceptId=" + inactiveConceptId + "&changed=" + changed;
            String body = "";

            if (replacementConceptId != null) {
                url += "&replacementConceptId=" + replacementConceptId;
            }

            if (manualReplacementConcept != null) {
                body = manualReplacementConcept.toString();
            }

            LOG.info("Testing url - " + url + " ; body: " + body);

            final MvcResult result = mvc.perform(post(url).content(body).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            LOG.info(" content = " + content);

            assertThat(content).contains("All changes made successfully");

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Removes the all inactive upgrade concepts.
     *
     * @param refsetInternalId the refset internal id
     */
    public void removeAllInactiveUpgradeConcepts(final String refsetInternalId) {

        try {

            final String url = baseUrl + "/" + refsetInternalId + "/removeAllUpgradeInactiveConcepts";

            LOG.info("Testing url - " + url);

            final MvcResult result = mvc.perform(post(url).content("").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            LOG.info(" content = " + content);

            assertThat(content).contains("All changes made successfully");

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds the all upgrade replacement concepts.
     *
     * @param refsetInternalId the refset internal id
     */
    public void addAllUpgradeReplacementConcepts(final String refsetInternalId) {

        try {

            final String url = baseUrl + "/" + refsetInternalId + "/addAllUpgradeReplacementConcepts";

            LOG.info("Testing url - " + url);

            final MvcResult result = mvc.perform(post(url).content("").contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            LOG.info(" content = " + content);

            assertThat(content).contains("All changes made successfully");

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Search replacement concepts.
     *
     * @param internalRefsetId the internal refset id
     * @param searchTerm the search term
     * @return the result list
     */
    public ResultList<UpgradeReplacementConcept> searchReplacementConcepts(final String internalRefsetId, final String searchTerm) {

        try {

            final String url = "/refset/" + internalRefsetId + "/replacementConceptSearch?limit=10&query=" + searchTerm;

            LOG.info("Testing url - " + url);
            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();

            LOG.info(" content = " + content);
            final ResultList<UpgradeReplacementConcept> results =
                new ObjectMapper().readValue(content, (new TypeReference<ResultList<UpgradeReplacementConcept>>() {
                }));

            // Testing Results
            assertThat(results).isNotNull();
            return results;

        } catch (final Exception e) {

            e.printStackTrace();
            return null;
        }

    }

    /**
     * Compile comparison data.
     *
     * @param activeRefsetInternalId the active refset internal id
     * @param comparisonRefsetInternalId the comparison refset internal id
     */
    public void compileComparisonData(final String activeRefsetInternalId, final String comparisonRefsetInternalId) {

        try {

            final String url = baseUrl + "/" + activeRefsetInternalId + "/compileComparisonData?comparisonRefsetInternalId=" + comparisonRefsetInternalId;

            LOG.info("Testing url - " + url);

            mvc.perform(get(url));

        } catch (final Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns the comparison data.
     *
     * @param activeRefsetInternalId the active refset internal id
     * @return the comparison data
     */
    public RefsetMemberComparison getComparisonData(final String activeRefsetInternalId) {

        try {

            final String url = baseUrl + "/" + activeRefsetInternalId + "/comparisonData";

            LOG.info("Testing url - " + url);

            final MvcResult result = mvc.perform(get(url)).andExpect(status().isOk()).andReturn();
            final String content = result.getResponse().getContentAsString();
            LOG.info(" content = " + content);

            final RefsetMemberComparison refsetMemberComparison = new ObjectMapper().readValue(content, RefsetMemberComparison.class);
            assertThat(refsetMemberComparison).isNotNull();

            return refsetMemberComparison;

        } catch (final Exception e) {

            e.printStackTrace();
            return null;
        }
    }
}
