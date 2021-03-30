/*
 * Copyright 2021 SNOMED International - All Rights Reserved.
 *
 * NOTICE:  All information contained herein is, and remains the property of SNOMED International
 * The intellectual and technical concepts contained herein are proprietary to
 * SNOMED International and may be covered by U.S. and Foreign Patents, patents in process,
 * and are protected by trade secret or copyright law.  Dissemination of this information
 * or reproduction of this material is strictly forbidden.
 */

package org.ihtsdo.refsetservice.util;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.ihtsdo.refsetservice.BaseTest;
import org.ihtsdo.refsetservice.model.DefinitionClause;
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
    private class Metadata {

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
                if (modified == null || modified.isEmpty()
                        || modified.equals("NULL")) {
                    modified = new Date().toString();
                }
                this.modified = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .parse(modified.replaceAll("\"", ""));
                this.modifiedBy = modifiedBy;
            } catch (Exception e) {
                logger.error("Failed with mod/modBy: "
                        + modified.replaceAll("\"", "") + " / " + modifiedBy);
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

    private enum FileProcessType {
        REFSET, CLAUSE, PROJECT;
    }

    /** The logger. */
    private final Logger logger =
            LoggerFactory.getLogger(RefsetMetadataMigrationTest.class);

    /** The multiple versions file path. */
    private final String projectsFile =
            "src/test/resources/migration/refset/refsetProjects.txt";

    /** The multiple versions file path. */
    private final String clausesFile =
            "src/test/resources/migration/refset/refsetClauses.txt";

    /** The multiple versions file path. */
    private final String multipleVersionsFilePath =
            "src/test/resources/migration/refset/GeneralDentistryAllVersions.txt";

    /** The single version file path. */
    private final String refsetClausesJoinFilePath =
            "src/test/resources/migration/refset/refsetECLClausesJoins.txt";

    /** The single version file path. */
    private final String singleVersionFilePath =
            "src/test/resources/migration/refset/GeneralDentistrySingleVersion.txt";

    /** The all refsets file path. */
    private final String allRefsetsFilePath =
            "src/test/resources/migration/refset/AllFromRTT.txt";

    /** The metadata map. */
    private final Map<Integer, Metadata> metadataMap = new HashMap<>();

    /** The json map. */
    private final Map<Integer, String> jsonMap = new HashMap<>();

    private final Map<String, HashSet<String>> clausesMap = new HashMap<>();

    private final Map<String, String> projectsMap = new HashMap<>();

    private final Map<String, Metadata> projectMetadataMap = new HashMap<>();

    /**
     * Test Single Version of General Dentistry Refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGeneralDensitrySingleVersion() throws Exception {
        preprocessingSupportingFiles();

        populateFromFile(singleVersionFilePath, FileProcessType.REFSET);
        importRefsetJson();
    }

    /**
     * Test All Versions of General Dentistry Refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGeneralDensitryAllVersions() throws Exception {
        preprocessingSupportingFiles();

        populateFromFile(multipleVersionsFilePath, FileProcessType.REFSET);
        importRefsetJson();
    }

    /**
     * Test All Versions of General Dentistry Refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testRefsetWithClauses() throws Exception {
        preprocessingSupportingFiles();

        populateFromFile(refsetClausesJoinFilePath, FileProcessType.REFSET);
        importRefsetJson();
    }

    /**
     * Test all refsets.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAllRefsets() throws Exception {
        preprocessingSupportingFiles();

        populateFromFile(allRefsetsFilePath, FileProcessType.REFSET);
        importRefsetJson();
    }

    private void preprocessingSupportingFiles() throws Exception {
        populateFromFile(clausesFile, FileProcessType.CLAUSE);
        populateFromFile(projectsFile, FileProcessType.PROJECT);
    }

    /**
     * Import refset json.
     */
    private void importRefsetJson() {
        Refset refset = null;
        int count = 0;

        try (final TerminologyService service = new TerminologyService()) {
            for (Integer index : jsonMap.keySet()) {
                // logger.debug("BBB: " + jsonMap.get(index));
                if (jsonMap.get(index).contains("TMP-7746xq")) {
                    int a = 1;
                }
                refset = ModelUtility.fromJson(jsonMap.get(index),
                        Refset.class);

                Metadata m = metadataMap.get(index);
                service.setModifiedFlag(false);
                ((HasModified) refset).setModified(m.getModified());
                ((HasModified) refset).setCreated(m.getModified());
                ((HasModified) refset).setModifiedBy(m.getModifiedBy());

                for (DefinitionClause clause : refset.getDefinitionClauses()) {
                    clause.setModified(m.getModified());
                    clause.setCreated(m.getModified());
                    clause.setModifiedBy(m.getModifiedBy());
                }

                refset.getEdition().setModified(projectMetadataMap
                        .get(refset.getEdition().getName()).getModified());
                refset.getEdition().setCreated(projectMetadataMap
                        .get(refset.getEdition().getName()).getModified());
                refset.getEdition().setModifiedBy(projectMetadataMap
                        .get(refset.getEdition().getName()).getModifiedBy());

                // Add an object
                service.add(refset.getEdition());
                service.add((HasModified) refset);

                count++;
                logger.info("Refset " + refset.getId() + " successfully added");
            }
        } catch (Exception e) {
            logger.error("Have issue with: " + e.getMessage());

            e.printStackTrace();
        }

        logger.info("Total of " + count + " refsets successfully added");
    }

    /**
     * Generate json from sql file.
     *
     * @param inputFile the input file
     * @param processType the process type
     * @return the string
     * @throws Exception the exception
     */
    private void populateFromFile(String inputFile, FileProcessType processType)
        throws Exception {
        BufferedReader reader;

        try {
            reader = new BufferedReader(new FileReader(inputFile));

            // Grab Header on 2nd time through
            String line = reader.readLine();
            line = reader.readLine();

            int count = 0;
            while (line != null) {
                switch (processType) {
                    case REFSET:
                        /*
                         * if (!line.contains("TMP-7746xq")) { line =
                         * reader.readLine(); continue; }
                         */
                        final String json = lineToRefsetJson(line);
                        final Metadata meta = lineToRefsetMetadata(line);
                        metadataMap.put(count, meta);
                        jsonMap.put(count, json);
                        break;
                    case CLAUSE:
                        while (line.indexOf("\"") >= 0 && line
                                .indexOf("\"") == line.lastIndexOf("\"")) {
                            line = line + " " + reader.readLine();
                        }

                        final String[] clauseColumns = line.split(",");

                        if (!clausesMap.containsKey(clauseColumns[0])) {
                            clausesMap.put(clauseColumns[0], new HashSet<>());
                        }
                        clausesMap.get(clauseColumns[0])
                                .add(clauseColumns[1] + "," + clauseColumns[2]);
                        break;
                    case PROJECT:
                        final String[] projectColumns = line.split(",");
                        projectsMap.put(projectColumns[0], line);
                        break;
                    default:
                        break;
                }

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
            String narrative;
            String name;
            if (line.split(",")[9].startsWith("\"")) {
                // If narrative has commas (and some do), can't rely on
                // splitting on comma. Must identify narrative and then
                // remove from line before finding other values
                final int descStartIdx = line.indexOf(line.split(",")[9]);
                final int descEndIdx =
                        line.substring(descStartIdx + 1).indexOf("\"");
                narrative = line.substring(descStartIdx + 1,
                        descStartIdx + descEndIdx + 1);

                // Cleanup line to remove ',' in narrative
                line = line.substring(0, descStartIdx)
                        + narrative.replaceAll(",", "")
                        + line.substring(descStartIdx + descEndIdx + 2);
                // logger.debug(line);
            } else {
                narrative = line.split(",")[9];
            }

            if (line.split(",")[17].startsWith("\"")) {
                // If narrative has commas (and some do), can't rely on
                // splitting on comma. Must identify narrative and then
                // remove from line before finding other values
                final int descStartIdx = line.indexOf(line.split(",")[17]);
                final int descEndIdx =
                        line.substring(descStartIdx + 1).indexOf("\"");
                name = line.substring(descStartIdx + 1,
                        descStartIdx + descEndIdx + 1);

                // Cleanup line to remove ',' in narrative
                line = line.substring(0, descStartIdx)
                        + narrative.replaceAll(",", "")
                        + line.substring(descStartIdx + descEndIdx + 2);
                // logger.debug(line);
            } else {
                name = line.split(",")[17];
            }

            line = line.replace("\"", "");
            String values[] = line.split(",");
            StringBuffer buf = new StringBuffer();
            final String refsetTableId = values[0];

            buf.append("{");
            buf.append("\"refsetId\": \"" + values[8] + "\",");
            buf.append("\"active\": "
                    + ((values[1].equals("1")) ? "true" : "false") + ",");
            buf.append("\"name\": \"" + name + "\",");
            buf.append("\"type\": \"" + values[24] + "\",");
            buf.append("\"narrative\": \"" + narrative + "\",");

            if (!values[2].equals("NULL")) {
                final String versionDate = values[2].replace(" ", "T");
                buf.append("\"versionDate\": \"" + versionDate + "\",");
            }

            if (values[28] != null && !values[28].isEmpty()
                    && !values[28].equals("NULL")) {
                buf.append("\"tags\": [\"" + values[28] + "\"],");
            }
            buf.append("\"versionStatus\": \"" + values[26] + "\",");
            buf.append("\"privateRefset\": "
                    + ((values[15].equals("0")) ? "true" : "false") + ",");
            buf.append("\"localSet\": "
                    + ((values[29].equals("1")) ? "true" : "false") + ",");
            // buf.append("\"projectId\": \"" + values[1] + "\",");

            if (!values[11].equals("NULL")) {
                buf.append("\"externalUrl\": \"" + values[11] + "\",");
            }

            // Create Definition Clauses
            if (clausesMap.containsKey(refsetTableId)) {
                buf.append("\"definitionClauses\": [");
                boolean initialProcessing = true;

                for (String clause : clausesMap.get(refsetTableId)) {
                    if (initialProcessing) {
                        initialProcessing = false;
                    } else {
                        buf.append(", ");
                    }

                    String[] clauseValues = clause.split(",");
                    buf.append("{ \"negated\":\"");
                    buf.append(clauseValues[0].equals("0") ? "false" : "true");
                    buf.append("\",");

                    buf.append("\"value\":\"" + clauseValues[1]
                            .replaceAll("\"", "").replaceAll("\t", "") + "\"}");
                }

                buf.append("], ");
            }

            // Create Edition
            final String projectId = values[27];
            if (projectsMap.containsKey(projectId)) {
                String namespace = values[18];
                final String projectLine = projectsMap.get(projectId);

                String description;
                String projectName;
                String modified;
                String modifiedBy;

                if (projectLine.split(",")[1].startsWith("\"")) {
                    // If description has commas (and some do), can't rely on
                    // splitting on comma. Must identify Description and then
                    // remove from line before finding other values
                    final int descStartIdx = projectLine.indexOf("\"");
                    final int descEndIdx = projectLine
                            .substring(descStartIdx + 1).indexOf("\"");

                    description = projectLine.substring(descStartIdx + 1,
                            descStartIdx + descEndIdx + 1);
                    String[] splitValues = projectsMap.get(projectId)
                            .substring(descStartIdx + descEndIdx + 3)
                            .split(",");

                    projectName = splitValues[5].replaceAll("\"", "");
                    modified = splitValues[2];
                    modifiedBy = splitValues[3];
                } else {
                    String[] splitValues = projectLine.split(",");

                    description = splitValues[1];
                    projectName = splitValues[7].replaceAll("\"", "");
                    modified = splitValues[4];
                    modifiedBy = splitValues[5];
                }

                // 7, 1, 4, 5

                buf.append("\"edition\": {");
                if (namespace == null || namespace.isEmpty()
                        || namespace.equals("NULL")) {
                    namespace = "Never Defined";
                }
                buf.append("\"code\": \"" + namespace + "\",");
                buf.append("\"name\": \"" + projectName + "\",");
                buf.append("\"branch\": \"" + values[25] + "\",");
                buf.append("\"description\": \"" + description + "\"");
                buf.append("}, ");
                projectMetadataMap.put(projectName,
                        new Metadata(modified, modifiedBy));
            }

            // Finish Refset
            buf.append("\"moduleId\": \"" + values[15] + "\"");
            buf.append("}");
            // logger.debug("AAA: " + buf.toString());
            return buf.toString();
        } catch (Exception e) {
            logger.error("Line: " + line);
            e.printStackTrace();

            throw e;
        }
    }

}
