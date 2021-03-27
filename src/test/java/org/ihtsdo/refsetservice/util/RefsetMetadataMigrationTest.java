
package org.ihtsdo.refsetservice.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.ihtsdo.refsetservice.BaseTest;
import org.ihtsdo.refsetservice.model.HasModified;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Validates the code-system-* files in the "src/resources" folder.
 */
public class RefsetMetadataMigrationTest extends BaseTest {

    /**
     * The Class Metadata.
     */
    public class Metadata {

        /** The modified. */
        Date modified;

        /** The modified by. */
        String modifiedBy;

        /**
         * Instantiates a new metadata.
         *
         * @param modified the modified
         * @param modifiedBy the modified by
         */
        public Metadata(String modified, String modifiedBy) {
            try {
                this.modified = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(modified);
                this.modifiedBy = modifiedBy;
            } catch (Exception e) {
                logger.error("Failed with mod/modBy: " + modified + "/" + modifiedBy);
                e.printStackTrace();
            }
        }

        /**
         * Gets the modified.
         *
         * @return the modified
         */
        public Date getModified() {
            return modified;
        }

        /**
         * Gets the modified by.
         *
         * @return the modified by
         */
        public String getModifiedBy() {
            return modifiedBy;
        }
    }

    /** The logger. */
    private final Logger logger = LoggerFactory.getLogger(RefsetMetadataMigrationTest.class);

    /** The multiple versions file path. */
    private final String multipleVersionsFilePath =
            "src/test/resources/migration/refset/GeneralDentistryAllVersions.txt";

    /** The single version file path. */
    private final String singleVersionFilePath =
            "src/test/resources/migration/refset/GeneralDentistrySingleVersion.txt";

    /** The all refsets file path. */
    private final String allRefsetsFilePath = "src/test/resources/migration/refset/AllFromRTT.txt";

    /** The metadata map. */
    private final Map<Integer, Metadata> metadataMap = new HashMap<>();

    /** The json map. */
    private final Map<Integer, String> jsonMap = new HashMap<>();

    /**
     * Test Single Version of General Dentistry Refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGeneralDensitrySingleVersion() throws Exception {
        generateJsonFromSqlFile(singleVersionFilePath);
        importRefsetJson();
    }

    /**
     * Test All Versions of General Dentistry Refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGeneralDensitryAllVersions() throws Exception {
        generateJsonFromSqlFile(multipleVersionsFilePath);
        importRefsetJson();
    }

    /**
     * Test all refsets.
     *
     * @throws Exception the exception
     */
    // @Test
    public void testAllRefsets() throws Exception {
        generateJsonFromSqlFile(allRefsetsFilePath);
        importRefsetJson();
    }

    /**
     * Import refset json.
     */
    private void importRefsetJson() {
        Refset refset = null;
        String currentJson = null;
        boolean termServerCreated = false;
        int count = 0;

        try (final TerminologyService service = new TerminologyService()) {
            termServerCreated = true;

            for (Integer index : jsonMap.keySet()) {
                refset = ModelUtility.fromJson(jsonMap.get(index), Refset.class);

                Metadata m = metadataMap.get(index);
                service.setModifiedFlag(false);
                ((HasModified) refset).setModified(m.getModified());
                ((HasModified) refset).setCreated(m.getModified());
                ((HasModified) refset).setModifiedBy(m.getModifiedBy());

                // Add an object
                logger.info("  test add object = " + refset.getId());
                service.add((HasModified) refset);

                count++;
                logger.info("Refset " + refset.getId() + " successfully added");
            }
        } catch (Exception e) {
            logger.error("Have issue with: " + e.getMessage());
            if (!termServerCreated) {
                logger.error("Unable to start TermService successfully");
            } else {
                logger.error("Failed in adding refset defined in uri: " + currentJson);
            }

            logger.info("Finished adding all " + count + " refsets");

            e.printStackTrace();
        }
    }

    /**
     * Generate json from sql file.
     *
     * @param inputFile the input file
     * @return the string
     * @throws Exception the exception
     */
    private void generateJsonFromSqlFile(String inputFile) throws Exception {
        BufferedReader reader;

        try {
            reader = new BufferedReader(new FileReader(inputFile));

            // Grab Header on 2nd time through
            String line = reader.readLine();
            line = reader.readLine();

            int count = 0;
            while (line != null) {
                String json = lineToRefsetJson(line);
                Metadata meta = lineToRefsetMetadata(line);
                metadataMap.put(count, meta);
                jsonMap.put(count, json);
                count++;

                // read next line
                line = reader.readLine();
            }

            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Line to refset metadata.
     *
     * @param line the line
     * @return the metadata
     */
    private Metadata lineToRefsetMetadata(String line) {
        line = line.replace("\"", "");
        String[] values = line.split(",");

        Metadata m = new Metadata(values[3], values[4]);
        return m;
    }

    /**
     * Line to refset json.
     *
     * @param line the line
     * @return the string
     */
    private String lineToRefsetJson(String line) {
        try {
            line = line.replace("\"", "");
            String[] values = line.split(",");

            StringBuffer buf = new StringBuffer();

            buf.append("{");
            buf.append("\"refsetId\": \"" + values[8] + "\",");
            buf.append("\"active\": " + ((values[1].equals("1")) ? "true" : "false") + ",");
            buf.append("\"name\": \"" + values[17] + "\",");
            buf.append("\"type\": \"" + values[24] + "\",");
            buf.append("\"narrative\": \"" + values[9] + "\",");

            if (!values[2].equals("NULL")) {
                final String versionDate = values[2].replace(" ", "T");
                buf.append("\"versionDate\": \"" + versionDate + "\",");
            }

            buf.append("\"versionStatus\": \"" + values[26] + "\",");
            buf.append("\"privateRefset\": " + ((values[15].equals("0")) ? "true" : "false") + ",");
            buf.append("\"localSet\": " + ((values[29].equals("1")) ? "true" : "false") + ",");
            buf.append("\"projectId\": \"" + values[1] + "\",");

            if (!values[11].equals("NULL")) {
                buf.append("\"externalUrl\": \"" + values[11] + "\",");
            }

            buf.append("\"moduleId\": \"" + values[15] + "\"");
            buf.append("}");

            return buf.toString();
        } catch (Exception e) {
            logger.error("Line: " + line);
            e.printStackTrace();

            throw e;
        }
    }

}
