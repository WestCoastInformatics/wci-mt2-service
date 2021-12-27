
package org.ihtsdo.refsetservice.rest.test;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.ihtsdo.refsetservice.rest.test.util.ExportUnitTestUtilities;
import org.ihtsdo.refsetservice.rest.test.util.GetUnitTestUtilities;
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
abstract public class AbstractRefsetTests extends BaseTest {
    protected final static SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");

    /** The config properties. */
    protected final Properties properties = PropertyUtility.getProperties();

    /** The logger. */
    private static Logger logger = LoggerFactory.getLogger(AbstractRefsetTests.class);

    protected static String testingProjectId = "";

    protected static String testingEditionId = "";

    protected static final String SNOMED_ROOT = "138875005";

    protected static final String DESCRIPTION_TERM = "term";

    protected static final String REFSET_FILE_PATH = "src/test/resources/refsetService/";

    protected static final String INACTIVE_REFSET_ID = "723264001";

    protected static final String INACTIVE_REFSET_VERSION = "20210731";

    protected static final String INACTIVE_REFSET_EARLIER_VERSION = "20170731";

    // Added 20170731 and Inactived in Lateralizable (723264001) on 20180131
    protected static final String INACTIVE_CONCEPT_ID = "727156001";

    protected static final String INVALID_INTERNAL_REFSET_ID = "12345678901234567890";

    protected static final String MAIN_TESTING_REFSET_ID = "561000172108"; // Belgian

    protected static final String MAIN_TESTING_REFSET_VERSION = "20200915";

    protected static final String TESTING_PROJECT_NAME = "SNOMED International Project";

    protected static final String TESTING_EDITION_NAME = "International Edition";

    protected static final String MEMBER_ID_LIST_FILE_NAME = "member_concept_id_list.txt";

    protected static final String MEMBER_ID_LIST_FILE_PATH = REFSET_FILE_PATH + MEMBER_ID_LIST_FILE_NAME;

    protected static final String MEMBER_ID_RF2_FILE_NAME = "member_concept_ids_rf2.txt";

    protected static final String MEMBER_ID_RF2_FILE = REFSET_FILE_PATH + MEMBER_ID_RF2_FILE_NAME;

    /** The mvc. */
    @Autowired
    protected MockMvc mvc;

    /** The object mapper. */
    protected static ObjectMapper objectMapper;

    /** The base url. */
    protected static String baseUrl = "/refset";

    protected GetUnitTestUtilities getUtil;

    protected ExportUnitTestUtilities exportUtil;

    protected EditUnitTestUtilities editUtil;

    protected void validateRefsetMetadata(Refset refset) {
        assertThat(refset).isNotNull();

        if (refset.getRefsetId().equals(INACTIVE_REFSET_ID)) {
            assertThat(refset.getRefsetId()).isEqualTo(INACTIVE_REFSET_ID);

            assertThat(refset.getName())
                    .isEqualToIgnoringCase("Lateralizable body structure reference set");
            assertThat(refset.getNarrative()).isEqualToIgnoringCase(
                    "The reference set contains all body structures that can be lateralized.");
            // assertThat(refset.getModifiedBy().equalsIgnoreCase("Migration")
            // || refset.getModifiedBy().equalsIgnoreCase("RT2")).isTrue();
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
            assertThat(refset.getRefsetId()).isEqualTo(MAIN_TESTING_REFSET_ID);

            assertThat(refset.getName()).isEqualToIgnoringCase(
                    "Belgian simple reference set for translated animal materials");
            assertThat(refset.getNarrative()).isEqualToIgnoringCase(
                    "descendants of 256363008 |Animal material (substance)| translated in the Belgian extension");
            // assertThat(refset.getModifiedBy().equalsIgnoreCase("Migration")
            // || refset.getModifiedBy().equalsIgnoreCase("RT2")).isTrue();
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

    // useDescriptions Should the validation use the description array or the
    // concept name for validating descriptions
    protected void validateConcept(Concept concept, String conceptId, String memberEfectiveTime,
        boolean isRefsetMember, List<String> descriptionList, int roleGroupSize, int parentSize,
        int childSize, boolean useDescriptions) throws ParseException {

        assertThat(concept).isNotNull();
        assertThat(concept.getCode()).isEqualTo(conceptId);

        if (memberEfectiveTime != null) {

            assertThat(concept.getMemberEffectiveTime())
                    .isEqualTo(SIMPLE_DATE_FORMAT.parseObject(memberEfectiveTime));
            assertThat(concept.isMemberOfRefset()).isEqualTo(isRefsetMember);
        }

        if (useDescriptions) {

            assertThat(concept.getDescriptions().size()).isEqualTo(descriptionList.size());

            for (String matchingDesc : descriptionList) {
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

    protected void validateConcept(Concept concept, String conId, String memberEfectiveTime,
        boolean isRefsetMember, List<String> descriptionList, int roleGroupSize, int parentSize,
        int childSize) throws ParseException {
        validateConcept(concept, conId, memberEfectiveTime, isRefsetMember, descriptionList,
                roleGroupSize, parentSize, childSize, true);
    }

    protected void validateDescExist(List<Map<String, String>> descriptions, String matchingTerm) {
        boolean descFound = false;

        for (Map<String, String> descriptionGroup : descriptions) {
            if (descriptionGroup.get(DESCRIPTION_TERM).equalsIgnoreCase(matchingTerm)) {
                descFound = true;
                break;
            }
        }

        assertTrue(descFound);
    }

    protected void validateExportFiles(JsonNode root, String expectedFilePath) throws IOException {
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

    protected Refset validateRefsetExists(final ResultList<Refset> refsetList,
        final String internalRefsetId) {
        for (Refset r : refsetList.getItems()) {
            if (r.getRefsetId().equals(internalRefsetId)) {
                return r;
            }
        }

        return null;
    }
}
