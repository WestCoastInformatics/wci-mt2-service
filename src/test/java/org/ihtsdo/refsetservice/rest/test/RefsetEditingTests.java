
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.rest.test.util.EditUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.ExportUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.GetUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.WorkflowUnitTestUtilities;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.json.JacksonTester;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for MetadataController.
 */

@AutoConfigureMockMvc
public class RefsetEditingTests extends AbstractRefsetTests {

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(RefsetEditingTests.class);

    static private boolean firstTimeSetup = true;

    /**
     * Sets the up.
     */
    @BeforeEach
    public void setUp(TestInfo info) {

        if (getUtil == null) {

            getUtil = new GetUnitTestUtilities(mvc, baseUrl, SIMPLE_DATE_FORMAT);
            exportUtil = new ExportUnitTestUtilities(mvc);
            workflowUtil = new WorkflowUnitTestUtilities(mvc, baseUrl, REFSET_FILE_PATH);
        }

        objectMapper = new ObjectMapper();
        JacksonTester.initFields(this, objectMapper);
        baseUrl = "/refset";

        try {

            if (firstTimeSetup) {

                testingProjectId = getUtil.getProjectInternalId(TESTING_PROJECT_NAME);
                testingEditionId = getUtil.getEditionInternalId(TESTING_EDITION_NAME);
                mainTestingRefsetInternalId = getUtil.getRefsetInternalId(MAIN_TESTING_REFSET_ID, MAIN_TESTING_REFSET_VERSION);

                editUtil = new EditUnitTestUtilities(mvc, baseUrl, SIMPLE_DATE_FORMAT, testingProjectId, testingEditionId);

                firstTimeSetup = false;
            }

        } catch (Exception e) {

            e.printStackTrace();
        }

    }

    /**
     * Test creating a refset concept and populating it from a list of sctids.
     *
     * @throws Exception the exception
     */
    @Test
    public void testCreateFromListOnNewRefsetConcept() throws Exception {

        // the data to create a refset from a list of Ids
        final String memberConceptIds = "53527002,226528004,404684003,260385009";

        Map<String, String> refsetConcept = editUtil.defineExtensionalRefsetConcept("testCreateFromListonNewRefsetConcept", memberConceptIds);

        String refsetInternalId = editUtil.createRefset(refsetConcept);
        JsonNode membersNode = editUtil.populateRefset(refsetInternalId, refsetConcept);
        editUtil.validateRefsetContents(refsetInternalId, 4);
        editUtil.removeRefsetContent(refsetInternalId, refsetConcept, membersNode);
        editUtil.deleteUnversionedRefset(refsetInternalId, refsetConcept.get("refsetDeleteStatus"));
    }

    /**
     * Test creating a refset concept and populating it from a list of sctids.
     *
     * @throws Exception the exception
     */
    // @Test
    // TODO: Handle existing vs new concept. Thus ensure others (such as test
    // above) is indeed on new concept. See below for initial pass in Summer.
    public void testCreateFromListOnExistingRefsetConcept() throws Exception {

        // the data to create a refset from a list of Ids
        final String memberConceptIds = "53527002,226528004,404684003,260385009";

        Map<String, String> refsetConcept = editUtil.defineExtensionalRefsetConcept("testCreateFromListonNewRefsetConcept", memberConceptIds);

        String refsetInternalId = editUtil.createRefset(refsetConcept);
        JsonNode membersNode = editUtil.populateRefset(refsetInternalId, refsetConcept);
        editUtil.validateRefsetContents(refsetInternalId, 4);
        editUtil.removeRefsetContent(refsetInternalId, refsetConcept, membersNode);
        editUtil.deleteUnversionedRefset(refsetInternalId, refsetConcept.get("refsetDeleteStatus"));
    }

    /**
     * earlier createRefsetFromExisting test public void testCreateRefsetFromExistingConcept() throws Exception { // TODO: Review purpose // the data to create a refset from
     * an existing concept (but can't be a // refset already in RT2) final Map<String, String> refsetExistingConcept = new HashMap<>(); refsetExistingConcept.put("refsetId",
     * "762103008"); refsetExistingConcept.put("name", "OWL ontology reference set"); refsetExistingConcept.put("parentConceptId", "446609009");
     * refsetExistingConcept.put("moduleId", "900000000000207008"); refsetExistingConcept.put("editionId", testingEditionId); refsetExistingConcept.put("projectId",
     * testingProjectId); refsetExistingConcept.put("narrative", "Test."); refsetExistingConcept.put("type", "EXTENSIONAL"); refsetExistingConcept.put("privateRefset",
     * "false"); refsetExistingConcept.put("localSet", "false"); refsetExistingConcept.put("refsetDeleteStatus", "deleted"); // DO NOT LEAVE THIS UNCOMMENTED - for one test we
     * will try to remove a // member that has already been published // refsetExistingConcept.put("additionalMemberIdsToRemove", // "734147008");
     * 
     * // prepare the call to create refset from an existing concept final ObjectNode refsetExistingConceptBody = new ObjectMapper().createObjectNode() .put("refsetId",
     * refsetExistingConcept.get("refsetId")) .put("name", refsetExistingConcept.get("name")) .put("parentConceptId", refsetExistingConcept.get("parentConceptId"))
     * .put("moduleId", refsetExistingConcept.get("moduleId")) .put("editionId", refsetExistingConcept.get("editionId")) .put("projectId",
     * refsetExistingConcept.get("projectId")) .put("narrative", refsetExistingConcept.get("narrative")) .put("type", refsetExistingConcept.get("type")) .put("privateRefset",
     * Boolean.parseBoolean(refsetExistingConcept.get("privateRefset"))) .put("localSet", Boolean.parseBoolean(refsetExistingConcept.get("localSet")));
     * 
     * refsetExistingConcept.put("body", refsetExistingConceptBody.toString());
     * 
     * // TODO: Add execution }
     */

    /**
     * Test creating a refset concept.
     *
     * @throws Exception the exception
     */
    @Test
    public void testCreateFromFileList() throws Exception {

        Map<String, String> refsetConcept = editUtil.defineExtensionalRefsetConcept("testCreateFromFileList", "list", MEMBER_ID_LIST_FILE_NAME, MEMBER_ID_LIST_FILE_PATH);

        String refsetInternalId = editUtil.createRefset(refsetConcept);
        JsonNode membersNode = editUtil.populateRefset(refsetInternalId, refsetConcept);
        editUtil.validateRefsetContents(refsetInternalId, 4);
        editUtil.removeRefsetContent(refsetInternalId, refsetConcept, membersNode);
        editUtil.deleteUnversionedRefset(refsetInternalId, refsetConcept.get("refsetDeleteStatus"));
    }

    /**
     * Test creating a refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testCreateFromFileRF2() throws Exception {

        Map<String, String> refsetConcept = editUtil.defineExtensionalRefsetConcept("testCreateFromFileRF2", "rf2", MEMBER_ID_RF2_FILE_NAME, MEMBER_ID_RF2_FILE);

        String refsetInternalId = editUtil.createRefset(refsetConcept);
        JsonNode membersNode = editUtil.populateRefset(refsetInternalId, refsetConcept);
        editUtil.validateRefsetContents(refsetInternalId, 4);
        editUtil.removeRefsetContent(refsetInternalId, refsetConcept, membersNode);
        editUtil.deleteUnversionedRefset(refsetInternalId, refsetConcept.get("refsetDeleteStatus"));
    }

    /**
     * Test creating a refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testCreateFromECL() throws Exception {

        // the data to create a refset from an ECL
        final String ecl = "<<226528004 | Whiskey (substance) |";

        Map<String, String> refsetConcept = editUtil.defineIntensionalRefsetConcept("testCreateFromECL", ecl);

        String refsetInternalId = editUtil.createRefset(refsetConcept);
        JsonNode membersNode = editUtil.populateRefset(refsetInternalId, refsetConcept);
        editUtil.validateRefsetContents(refsetInternalId, 3);
        editUtil.removeRefsetContent(refsetInternalId, refsetConcept, membersNode);
        editUtil.deleteUnversionedRefset(refsetInternalId, refsetConcept.get("refsetDeleteStatus"));
    }

    /**
     * Test creating, modifying, and deleting a new version of an existing refset in edit mode.
     *
     * @throws Exception the exception
     */
    // @Test
    // TODO: Need to update this including defining of metadata
    public void testCreateNewVersionWithChangesFromPublished() throws Exception {

        // ADD NEW VERSION
        final String newRefsetInternalId = editUtil.createNewRefsetVersion(mainTestingRefsetInternalId);
        assertThat(newRefsetInternalId).isNotEqualTo(mainTestingRefsetInternalId);
        logger.info("New Version Internal ID - " + newRefsetInternalId);

        final Map<String, String> modifyData = new HashMap<>();

        // validate the new version
        try (final TerminologyService service = new TerminologyService()) {

            Refset refset = service.get(newRefsetInternalId, Refset.class);
            assertThat(refset).isNotNull();
            assertThat(refset.getRefsetId()).isEqualTo(MAIN_TESTING_REFSET_ID);
            assertThat(refset.getVersionStatus()).isEqualTo(Refset.IN_DEVELOPMENT);
            assertThat(refset.getVersionDate()).isNull();
            assertThat(refset.isLatestVersion()).isTrue();
            assertThat(refset.getTags().size()).isLessThanOrEqualTo(1);

            modifyData.put("workflowStatus", refset.getWorkflowStatus());
            modifyData.put("versionNotes", refset.getVersionNotes());
            modifyData.put("narrative", refset.getNarrative());
            modifyData.put("privateRefset", Boolean.toString(refset.isPrivateRefset()));
            modifyData.put("type", refset.getType());
            modifyData.put("externalUrl", refset.getExternalUrl());
            Set<String> tags = refset.getTags();

            // Must have 0 or 1 tags to work. Add if there exists one
            for (String tag : tags) {

                modifyData.put("tags", tag);
            }

        }

        // MODIFY NEW VERSION
        // Define the modifications
        modifyData.put("tags", "test tag1");
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

            // Validate refset versioning
            assertThat(refset).isNotNull();
            assertThat(refset.isLatestVersion()).isTrue();

            refset = service.get(mainTestingRefsetInternalId, Refset.class);
            assertThat(refset).isNotNull();
            assertThat(refset.isLatestVersion()).isFalse();
        }

        // DELETE NEW VERSION
        editUtil.deleteVersionedRefset(newRefsetInternalId);

        // validate the original refset is back to the latest version
        try (final TerminologyService service = new TerminologyService()) {

            Refset refset = service.get(mainTestingRefsetInternalId, Refset.class);
            assertThat(refset).isNotNull();
            assertThat(refset.isLatestVersion()).isTrue();
        }

    }

    /**
     * Test any other ways to add members.
     *
     * @throws Exception the exception
     */
    // @Test
    // TODO: fill out test
    public void testOtherWaysToAdd() throws Exception {

    }

    /**
     * Test all ways to remove members.
     *
     * @throws Exception the exception
     */
    // @Test
    // TODO: fill out test
    public void testAllWaysToRemove() throws Exception {

    }

    /**
     * Test adding and removing via BULK
     *
     * @throws Exception the exception
     */
    // @Test
    // TODO: fill out test
    public void testAddRemoveInBulk() throws Exception {

    }

    public void testCreateInvalids() throws Exception {

        // TODO: Determine approach
    }

}
