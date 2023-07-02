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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedSet;
import java.util.TreeSet;

import org.ihtsdo.refsetservice.model.Concept;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.rest.test.util.EditUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.ExportUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.GetUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.WorkflowUnitTestUtilities;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.PropertyUtility;
import org.ihtsdo.refsetservice.util.ResultList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Integration tests for MetadataController.
 */

@AutoConfigureMockMvc
public abstract class AbstractRefsetTests extends BaseTest {

    /** The Constant SIMPLE_DATE_FORMAT. */
    protected static final String YYYYMMDD_FORMAT = "yyyyMMdd";

    /** The config properties. */
    protected static final Properties PROPERTIES = PropertyUtility.getProperties();

    /** The Constant LOG. */
    private static final Logger LOG = LoggerFactory.getLogger(AbstractRefsetTests.class);

    /** The read only testing project id. */
    private static String readOnlyTestingProjectId = null;

    /** The read only testing edition id. */
    private static String readOnlyTestingEditionId = null;

    /** The wci testing project id. */
    private static String wciTestingProjectId = null;

    /** The wci testing edition id. */
    private static String wciTestingEditionId = null;

    /** The main core testing refset internal id. */
    private static String mainCoreTestingRefsetInternalId = "";

    /** The refset with inactive concept as active member internal id. */
    private static String refsetWithInactiveConceptAsActiveMemberInternalId = "";

    /** The main nrc testing refset internal id. */
    private static String mainNrcTestingRefsetInternalId = "";

    /** The Constant SNOMED_ROOT. */
    protected static final String SNOMED_ROOT = "138875005";

    /** The Constant DESCRIPTION_TERM. */
    protected static final String DESCRIPTION_TERM = "term";

    /** The Constant REFSET_FILE_PATH. */
    protected static final String REFSET_FILE_PATH = "src/test/resources/refsetService/";

    /** The Constant INVALID_INTERNAL_REFSET_ID. */
    protected static final String INVALID_INTERNAL_REFSET_ID = "12345678901234567890";

    /** The Constant MAIN_NRC_TESTING_REFSET_ID. */
    protected static final String MAIN_NRC_TESTING_REFSET_ID = "561000172108"; // Belgian

    /** The Constant MAIN_NRC_TESTING_REFSET_VERSION. */
    protected static final String MAIN_NRC_TESTING_REFSET_VERSION = "2022-03-15";

    /** The Constant MAIN_CORE_TESTING_REFSET_ID. */
    protected static final String MAIN_CORE_TESTING_REFSET_ID = "721145008"; // Belgian

    /** The Constant MAIN_CORE_TESTING_REFSET_VERSION. */
    protected static final String MAIN_CORE_TESTING_REFSET_VERSION = "2021-11-30";

    /** The Constant MEMBER_ID_LIST_FILE_NAME. */
    protected static final String MEMBER_ID_LIST_FILE_NAME = "member_concept_id_list.txt";

    /** The Constant MEMBER_ID_LIST_FILE_PATH. */
    protected static final String MEMBER_ID_LIST_FILE_PATH = REFSET_FILE_PATH + MEMBER_ID_LIST_FILE_NAME;

    /** The Constant MEMBER_ID_RF2_FILE_NAME. */
    protected static final String MEMBER_ID_RF2_FILE_NAME = "member_concept_ids_rf2.txt";

    /** The Constant MEMBER_ID_RF2_FILE. */
    protected static final String MEMBER_ID_RF2_FILE = REFSET_FILE_PATH + MEMBER_ID_RF2_FILE_NAME;

    /** The Constant READ_ONLY_TESTING_PROJECT_NAME. */
    protected static final String READ_ONLY_TESTING_PROJECT_NAME = "SNOMED International Project";

    /** The Constant READ_ONLY_TESTING_EDITION_NAME. */
    protected static final String READ_ONLY_TESTING_EDITION_NAME = "International Edition";

    /** The Constant WCI_TESTING_PROJECT_NAME. */
    protected static final String WCI_TESTING_PROJECT_NAME = "WCI Testing Project";

    /** The Constant WCI_TESTING_EDITION_NAME. */
    protected static final String WCI_TESTING_EDITION_NAME = "WCI Testing Extension";

    /** The Constant REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_ID. */
    protected static final String REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_ID = "450970008";

    /** The Constant INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID. */
    protected static final String INACTIVE_CONCEPT_ACTIVE_MEMBER_CONCEPT_ID = "27720003";

    /** The Constant INACTIVE_CONCEPT_ACTIVE_MEMBER_PARENT_CONCEPT_ID. */
    protected static final String INACTIVE_CONCEPT_ACTIVE_MEMBER_PARENT_CONCEPT_ID = "76318008";

    /** The Constant REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_VERSION. */
    protected static final String REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_VERSION = "2021-11-30"; // "2021-07-31";

    /** The Constant REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_EARLIER_VERSION. */
    protected static final String REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_EARLIER_VERSION = "2020-07-31";

    /** The mvc. */
    @Autowired
    private MockMvc mvc;

    /** The object mapper. */
    private static ObjectMapper objectMapper;

    /** The base url. */
    private static String baseUrl = "/refset";

    /** The get util. */
    private static GetUnitTestUtilities getUtil;

    /** The export util. */
    private static ExportUnitTestUtilities exportUtil;

    /** The workflow util. */
    private static WorkflowUnitTestUtilities workflowUtil;

    /** The edit util. */
    private static EditUnitTestUtilities editUtil;

    /**
     * Returns the mvc.
     *
     * @return the mvc
     */
    protected MockMvc getMvc() {

        return mvc;
    }

    /**
     * Sets the mvc.
     *
     * @param mvc the mvc to set
     */
    protected void setMvc(final MockMvc mvc) {

        this.mvc = mvc;
    }

    /**
     * Returns the object mapper.
     *
     * @return the objectMapper
     */
    protected static ObjectMapper getObjectMapper() {

        return objectMapper;
    }

    /**
     * Sets the object mapper.
     *
     * @param objectMapper the objectMapper to set
     */
    protected static void setObjectMapper(final ObjectMapper objectMapper) {

        AbstractRefsetTests.objectMapper = objectMapper;
    }

    /**
     * Returns the base url.
     *
     * @return the baseUrl
     */
    protected static String getBaseUrl() {

        return baseUrl;
    }

    /**
     * Sets the base url.
     *
     * @param baseUrl the baseUrl to set
     */
    protected static void setBaseUrl(final String baseUrl) {

        AbstractRefsetTests.baseUrl = baseUrl;
    }

    /**
     * Returns the returns the util.
     *
     * @return the getUtil
     */
    protected static GetUnitTestUtilities getGetUtil() {

        return getUtil;
    }

    /**
     * Sets the returns the util.
     *
     * @param getUtil the getUtil to set
     */
    protected static void setGetUtil(final GetUnitTestUtilities getUtil) {

        AbstractRefsetTests.getUtil = getUtil;
    }

    /**
     * Returns the export util.
     *
     * @return the exportUtil
     */
    protected static ExportUnitTestUtilities getExportUtil() {

        return exportUtil;
    }

    /**
     * Sets the export util.
     *
     * @param exportUtil the exportUtil to set
     */
    protected static void setExportUtil(final ExportUnitTestUtilities exportUtil) {

        AbstractRefsetTests.exportUtil = exportUtil;
    }

    /**
     * Returns the workflow util.
     *
     * @return the workflowUtil
     */
    protected static WorkflowUnitTestUtilities getWorkflowUtil() {

        return workflowUtil;
    }

    /**
     * Sets the workflow util.
     *
     * @param workflowUtil the workflowUtil to set
     */
    protected static void setWorkflowUtil(final WorkflowUnitTestUtilities workflowUtil) {

        AbstractRefsetTests.workflowUtil = workflowUtil;
    }

    /**
     * Returns the edits the util.
     *
     * @return the editUtil
     */
    protected static EditUnitTestUtilities getEditUtil() {

        return editUtil;
    }

    /**
     * Sets the edits the util.
     *
     * @param editUtil the editUtil to set
     */
    protected static void setEditUtil(final EditUnitTestUtilities editUtil) {

        AbstractRefsetTests.editUtil = editUtil;
    }

    /**
     * Validate refset metadata.
     *
     * @param refset the refset
     */
    protected void validateRefsetMetadata(final Refset refset) {

        assertThat(refset).isNotNull();

        if (refset.getRefsetId().equals(REFSET_WITH_INACTIVE_CONCEPT_ACTIVE_MEMBER_REFSET_ID)) {

            assertThat(refset.getName()).isEqualToIgnoringCase("General Practice / Family Practice reference set");

            // NO LONGER HAS NARRATIVE
            // assertThat(refset.getNarrative()).isEqualToIgnoringCase("Description of refset General Practice / Family Practice reference set");

            assertThat(refset.getModifiedBy()).isNotEmpty();
            assertThat(refset.getType()).isEqualToIgnoringCase("extensional");
            assertThat(refset.getModuleId()).isEqualTo("900000000000012004");
            assertThat(refset.getEdition().getName()).isEqualToIgnoringCase("International Edition");
            assertThat(refset.getType()).isEqualToIgnoringCase("extensional");
            assertThat(refset.getVersionStatus()).isEqualToIgnoringCase("published");
            assertThat(refset.getVersionNotes()).isNull();
            assertThat(refset.getProject().getName()).isEqualTo("SNOMED International Project");

            assertThat(refset.isActive()).isTrue();
            assertThat(refset.isLocalSet()).isFalse();
            assertThat(refset.isPrivateRefset()).isFalse();

            // Version Date
            assertThat(refset.getVersionDate()).isNotNull();

        } else if (refset.getRefsetId().equals(MAIN_NRC_TESTING_REFSET_ID)) {

            assertThat(refset.getRefsetId()).isEqualTo(MAIN_NRC_TESTING_REFSET_ID);

            assertThat(refset.getName()).isEqualToIgnoringCase("Belgian simple reference set for translated animal materials");

            // NO LONGER HAS NARRATIVE
            // assertThat(refset.getNarrative()).isEqualToIgnoringCase("descendants of 256363008 |Animal material (substance)| translated in the Belgian
            // extension");

            assertThat(refset.getModifiedBy()).isNotBlank();
            assertThat(refset.getType()).isEqualToIgnoringCase("extensional");
            assertThat(refset.getModuleId()).isEqualTo("11000172109");
            assertThat(refset.getEdition().getName()).isEqualToIgnoringCase("Belgian Extension");
            assertThat(refset.getType()).isEqualToIgnoringCase("extensional");
            assertThat(refset.getVersionStatus()).isEqualToIgnoringCase("published");
            assertThat(refset.getVersionNotes()).isNull();
            /*
             * this refset has no tags assertThat(refset.getTags().size()).isEqualTo(1);
             * assertThat(refset.getTags().iterator().next()).isEqualToIgnoringCase("General / Allergies");
             */
            assertThat(refset.getProject().getName()).isEqualTo("Belgian Extension Project");

            assertThat(refset.isActive()).isTrue();
            assertThat(refset.isLocalSet()).isFalse();
            assertThat(refset.isPrivateRefset()).isFalse();

            // Version Date
            assertThat(refset.getVersionDate()).isNotNull();
        } else {
            LOG.error("Unexpected refset in validateRefsetMetadata.  Refset:{}", refset);
            assertThat(refset.getRefsetId().equals("UNKNOWN REFSET"));

        }

    }

    // useDescriptions Should the validation use the description array or the
    /**
     * Validate concept.
     *
     * @param concept the concept
     * @param conceptId the concept id
     * @param memberEfectiveTime the member efective time
     * @param isRefsetMember the is refset member
     * @param descriptionList the description list
     * @param roleGroupSize the role group size
     * @param parentSize the parent size
     * @param childSize the child size
     * @param useDescriptions the use descriptions
     * @throws ParseException the parse exception
     */
    // concept name for validating descriptions
    protected void validateConcept(final Concept concept, final String conceptId, final String memberEfectiveTime, final boolean isRefsetMember,
        final List<String> descriptionList, final int roleGroupSize, final int parentSize, final int childSize, final boolean useDescriptions)
        throws ParseException {

        assertThat(concept).isNotNull();
        assertThat(concept.getCode()).isEqualTo(conceptId);

        final SimpleDateFormat sdf = new SimpleDateFormat(YYYYMMDD_FORMAT);

        if (memberEfectiveTime != null) {

            assertThat(concept.getMemberEffectiveTime()).isEqualTo(sdf.parseObject(memberEfectiveTime));
            assertThat(concept.isMemberOfRefset()).isEqualTo(isRefsetMember);
        }

        if (useDescriptions) {

            assertThat(concept.getDescriptions().size()).isEqualTo(descriptionList.size());

            for (final String matchingDesc : descriptionList) {

                validateDescExist(concept.getDescriptions(), matchingDesc);
            }

        } else {

            assertThat(concept.getName()).isEqualTo(descriptionList.get(0));

            if (descriptionList.size() == 2) {

                assertThat(concept.getFsn()).isEqualTo(descriptionList.get(1));
            }

        }

        assertThat(concept.getRoleGroups().size()).isEqualTo(roleGroupSize);

        if (parentSize >= 0) {

            assertThat(concept.getParents().size()).isEqualTo(parentSize);
        }

        assertThat(concept.getChildren().size()).isEqualTo(childSize);

    }

    /**
     * Validate concept.
     *
     * @param concept the concept
     * @param conId the con id
     * @param memberEfectiveTime the member efective time
     * @param isRefsetMember the is refset member
     * @param descriptionList the description list
     * @param roleGroupSize the role group size
     * @param parentSize the parent size
     * @param childSize the child size
     * @throws ParseException the parse exception
     */
    protected void validateConcept(final Concept concept, final String conId, final String memberEfectiveTime, final boolean isRefsetMember,
        final List<String> descriptionList, final int roleGroupSize, final int parentSize, final int childSize) throws ParseException {

        validateConcept(concept, conId, memberEfectiveTime, isRefsetMember, descriptionList, roleGroupSize, parentSize, childSize, true);
    }

    /**
     * Validate desc exist.
     *
     * @param descriptions the descriptions
     * @param matchingTerm the matching term
     */
    protected void validateDescExist(final List<Map<String, String>> descriptions, final String matchingTerm) {

        boolean descFound = false;

        for (final Map<String, String> descriptionGroup : descriptions) {

            if (descriptionGroup.get(DESCRIPTION_TERM).equalsIgnoreCase(matchingTerm)) {

                descFound = true;
                break;
            }

        }

        assertTrue(descFound);
    }

    /**
     * Validate export files.
     *
     * @param root the root
     * @param expectedFilePath the expected file path
     * @throws IOException Signals that an I/O exception has occurred.
     */
    protected void validateExportFiles(final JsonNode root, final String expectedFilePath) throws IOException {

        // Get Zipped File
        final String fileUrl = (root.get("url")).asText();
        LOG.info("File Url: " + fileUrl);
        final String zipFileName = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
        final String exportRefsetPath = PROPERTIES.getProperty("REFSET_EXPORT_DIR");
        LOG.info("Refset Directory: " + exportRefsetPath);
        final String downloadedZipFile = exportRefsetPath + "/" + zipFileName;
        LOG.info("Zip File Path: " + downloadedZipFile);
        final Path unzippedPath = Files.createTempDirectory("exportTest-");

        final File file = unzippedPath.toFile();
        assertNotNull(file);

        final File[] files = unzippedPath.toFile().listFiles();
        assertNotNull(files);

        FileUtility.unzip(downloadedZipFile, file.getAbsolutePath());

        assertThat(1).isEqualTo(files.length);

        // Get generated File
        final File generatedFile = files[0];

        try (final BufferedReader expectedFileReader = new BufferedReader(new FileReader(expectedFilePath));
            final BufferedReader generatedFileReader = new BufferedReader(new FileReader(generatedFile));) {

            final SortedSet<String> generatedLines = new TreeSet<>();
            final SortedSet<String> testLines = new TreeSet<>();

            String st;

            while ((st = generatedFileReader.readLine()) != null) {

                generatedLines.add(st);
            }

            while ((st = expectedFileReader.readLine()) != null) {

                testLines.add(st);
            }

            // Compare two but first disregard the header for the generated
            // contents.
            int j = 0;

            for (int i = 0; i < generatedLines.size(); i++) {

                final String generatedLine = (String) generatedLines.toArray()[i];

                // If header line, just ignore altogether
                if (!generatedLine.startsWith("id\teffectiveTime")) {

                    final String testLine = (String) testLines.toArray()[j++];

                    assertThat(testLine).isEqualTo(generatedLine);
                }

            }

            assertThat(testLines.size()).isEqualTo(j);
        }

    }

    /**
     * Validate refset exists.
     *
     * @param refsetList the refset list
     * @param internalRefsetId the internal refset id
     * @return the refset
     */
    protected Refset validateRefsetExists(final ResultList<Refset> refsetList, final String internalRefsetId) {

        if (refsetList == null || refsetList.getItems() == null || refsetList.getItems().isEmpty()) {
            return null;
        }

        return refsetList.getItems().stream().filter(r -> r.getRefsetId().equals(internalRefsetId)).findFirst().orElse(null);
    }

    /**
     * Returns the read only testing project id.
     *
     * @return the readOnlyTestingProjectId
     */
    public static String getReadOnlyTestingProjectId() {

        return readOnlyTestingProjectId;
    }

    /**
     * Returns the read only testing edition id.
     *
     * @return the readOnlyTestingEditionId
     */
    public static String getReadOnlyTestingEditionId() {

        return readOnlyTestingEditionId;
    }

    /**
     * Returns the wci testing project id.
     *
     * @return the wciTestingProjectId
     */
    public static String getWciTestingProjectId() {

        return wciTestingProjectId;
    }

    /**
     * Returns the wci testing edition id.
     *
     * @return the wciTestingEditionId
     */
    public static String getWciTestingEditionId() {

        return wciTestingEditionId;
    }

    /**
     * Returns the main core testing refset internal id.
     *
     * @return the mainCoreTestingRefsetInternalId
     */
    public static String getMainCoreTestingRefsetInternalId() {

        return mainCoreTestingRefsetInternalId;
    }

    /**
     * Returns the refset with inactive concept as active member internal id.
     *
     * @return the refsetWithInactiveConceptAsActiveMemberInternalId
     */
    public static String getRefsetWithInactiveConceptAsActiveMemberInternalId() {

        return refsetWithInactiveConceptAsActiveMemberInternalId;
    }

    /**
     * Returns the main nrc testing refset internal id.
     *
     * @return the mainNrcTestingRefsetInternalId
     */
    public static String getMainNrcTestingRefsetInternalId() {

        return mainNrcTestingRefsetInternalId;
    }

    /**
     * Sets the read only testing project id.
     *
     * @param readOnlyTestingProjectId the readOnlyTestingProjectId to set
     */
    public static void setReadOnlyTestingProjectId(final String readOnlyTestingProjectId) {

        AbstractRefsetTests.readOnlyTestingProjectId = readOnlyTestingProjectId;
    }

    /**
     * Sets the read only testing edition id.
     *
     * @param readOnlyTestingEditionId the readOnlyTestingEditionId to set
     */
    public static void setReadOnlyTestingEditionId(final String readOnlyTestingEditionId) {

        AbstractRefsetTests.readOnlyTestingEditionId = readOnlyTestingEditionId;
    }

    /**
     * Sets the wci testing project id.
     *
     * @param wciTestingProjectId the wciTestingProjectId to set
     */
    public static void setWciTestingProjectId(final String wciTestingProjectId) {

        AbstractRefsetTests.wciTestingProjectId = wciTestingProjectId;
    }

    /**
     * Sets the wci testing edition id.
     *
     * @param wciTestingEditionId the wciTestingEditionId to set
     */
    public static void setWciTestingEditionId(final String wciTestingEditionId) {

        AbstractRefsetTests.wciTestingEditionId = wciTestingEditionId;
    }

    /**
     * Sets the main core testing refset internal id.
     *
     * @param mainCoreTestingRefsetInternalId the mainCoreTestingRefsetInternalId to set
     */
    public static void setMainCoreTestingRefsetInternalId(final String mainCoreTestingRefsetInternalId) {

        AbstractRefsetTests.mainCoreTestingRefsetInternalId = mainCoreTestingRefsetInternalId;
    }

    /**
     * Sets the refset with inactive concept as active member internal id.
     *
     * @param refsetWithInactiveConceptAsActiveMemberInternalId the refsetWithInactiveConceptAsActiveMemberInternalId to set
     */
    public static void setRefsetWithInactiveConceptAsActiveMemberInternalId(final String refsetWithInactiveConceptAsActiveMemberInternalId) {

        AbstractRefsetTests.refsetWithInactiveConceptAsActiveMemberInternalId = refsetWithInactiveConceptAsActiveMemberInternalId;
    }

    /**
     * Sets the main nrc testing refset internal id.
     *
     * @param mainNrcTestingRefsetInternalId the mainNrcTestingRefsetInternalId to set
     */
    public static void setMainNrcTestingRefsetInternalId(final String mainNrcTestingRefsetInternalId) {

        AbstractRefsetTests.mainNrcTestingRefsetInternalId = mainNrcTestingRefsetInternalId;
    }
}
