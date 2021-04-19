package org.ihtsdo.refsetservice.util.test;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.HasModified;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.util.FileUtility;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

// TODO: Auto-generated Javadoc
/**
 * Validates the code-system-* files in the "src/resources" folder.
 */
public class RefsetMetadataMigrationTest extends BaseTest {

    /**
     * The Class Metadata.
     */
    private class Metadata {

        /** The modified. */
        private Date modified;

        /** The modified by. */
        private String modifiedBy;

        /**
         * Instantiates a new metadata.
         *
         * @param modified the modified
         * @param modifiedBy the modified by
         */
        public Metadata(final String modified, final String modifiedBy) {
            String updatedModified = modified;
            try {
                if (modified == null || modified.isEmpty() || modified.equals("NULL")) {
                    updatedModified = new Date().toString();
                }

                this.modified = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .parse(updatedModified.replaceAll("\"", ""));
                this.modifiedBy = modifiedBy;
            } catch (Exception e) {
                logger.error("Failed with mod/modBy: " + updatedModified.replaceAll("\"", "")
                        + " / " + modifiedBy);
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

    /**
     * The Enum FileProcessType.
     */
    private enum FileProcessType {

        /** The refset. */
        REFSET,
        /** The clause. */
        CLAUSE,
        /** The project. */
        PROJECT;
    }

    /** The logger. */
    private final Logger logger = LoggerFactory.getLogger(RefsetMetadataMigrationTest.class);

    /** The projects file. */
    private final String projectsFile = "src/test/resources/migration/resources/projects.txt";

    /** The editions file. */
    private final String editionsFile = "src/test/resources/migration/resources/editions.txt";

    /** The clauses file. */
    private final String clausesFile = "src/test/resources/migration/resources/clauses.txt";

    /** The multiple versions file path. */
    private final String multipleVersionsFilePath =
            "src/test/resources/migration/refsetsToImport/GeneralDentistryAllVersions.txt";

    /** The single version file path. */
    private final String singleVersionWithRefsetFilePath =
            "src/test/resources/migration/refsetsToImport/refsetECLClausesJoins.txt";

    /** The single version file path. */
    private final String singleVersionFilePath =
            "src/test/resources/migration/refsetsToImport/GeneralDentistrySingleVersion.txt";

    /** The all refsets file path. */
    private final String allRefsetsFilePath =
            "src/test/resources/migration/refsetsToImport/AllFromRTT.txt";

    /** The metadata map. */
    private final Map<String, Metadata> metadataMap = new HashMap<>();

    /** The json map. */
    private final Map<String, String> refsetsMap = new HashMap<>();

    /** The rtt refset to clauses map. */
    private final Map<String, ArrayList<String>> rttRefsetToClausesMap = new HashMap<>();

    /** The projects map. */
    private final Map<String, String> projectsMap = new HashMap<>();

    /** The refset to project map. */
    private final Map<String, String> refsetToProjectMap = new HashMap<>();

    /** The short name to namespace map. */
    private final Map<String, String> shortNameToNamespaceMap = new HashMap<>();

    /** The short name editions map. */
    private final Map<String, Edition> shortNameEditionsMap = new HashMap<>();

    /** The namespace editions map. */
    private final Map<String, Edition> namespaceEditionsMap = new HashMap<>();

    /** The refset namespace map. */
    private final Map<String, String> refsetNamespaceMap = new HashMap<>();

    /** The refset shortname map. */
    private final Map<String, String> refsetShortnameMap = new HashMap<>();

    /*-
     * For missing namespace analysis only
    private Map<String, Set<String>> cccNameToNamespace = new HashMap<>();
     */

    /**
     * Test Single Version of General Dentistry Refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testGeneralDensitrySingleVersion() throws Exception {
        preprocessingSupportingFiles();

        populateFromFile(singleVersionFilePath, FileProcessType.REFSET);
        importObjects();
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
        importObjects();
    }

    /**
     * Test All Versions of General Dentistry Refset.
     *
     * @throws Exception the exception
     */
    @Test
    public void testRefsetWithClauses() throws Exception {
        preprocessingSupportingFiles();

        populateFromFile(singleVersionWithRefsetFilePath, FileProcessType.REFSET);
        importObjects();
    }

    /**
     * Test all refsets.
     *
     * @throws Exception the exception
     */
    // @Test
    public void testAllRefsets() throws Exception {
        preprocessingSupportingFiles();

        populateFromFile(allRefsetsFilePath, FileProcessType.REFSET);

        /*-
         * For missing namespace analysis only
        for (String shortName : cccNameToNamespace.keySet()) {
            if (cccNameToNamespace.get(shortName).size() == 1) {
                continue;
            }
            for (String namespace : cccNameToNamespace.get(shortName)) {
                logger.debug("CCC - "
                        + "inconsistent use of shortname and namespace across projects. Trying to "
                        + "map Shortname/Namespace: " + shortName + " / " + namespace);
            }
        }
         */

        importObjects();
    }

    /**
     * Generate json from sql file.
     *
     * @param inputFile the input file
     * @param processType the process type
     * @throws Exception the exception
     */
    private void populateFromFile(final String inputFile, final FileProcessType processType)
        throws Exception {
        BufferedReader reader;

        try {
            reader = new BufferedReader(new FileReader(inputFile));

            // Grab Header on 2nd time through
            String line = reader.readLine();
            line = reader.readLine();

            while (line != null) {
                switch (processType) {
                    case REFSET:
                        final String refsetJson = lineToRefsetJson(line);
                        refsetsMap.put(line.split(",")[0], refsetJson);
                        break;

                    case CLAUSE:
                        // Combine multiline clauses into one
                        while (line.indexOf("\"") >= 0
                                && line.indexOf("\"") == line.lastIndexOf("\"")) {
                            line = line + " " + reader.readLine();
                        }

                        final String clauseJson = lineToClauseJson(line);

                        // store all clauses associated wtih a given refset
                        final String rttRefsetId = line.split(",")[0];
                        if (!rttRefsetToClausesMap.containsKey(rttRefsetId)) {
                            rttRefsetToClausesMap.put(rttRefsetId, new ArrayList<String>());
                        }
                        rttRefsetToClausesMap.get(rttRefsetId).add(clauseJson);
                        break;

                    case PROJECT:
                        final String projectJson = lineToProjectJson(line);
                        projectsMap.put(line.split(",")[0], projectJson);
                        break;

                    default:
                        throw new Exception(
                                "Should never reach here have processType: " + processType);
                }

                // read next line
                line = reader.readLine();
            }

            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Import refset json.
     *
     * @throws Exception the exception
     */
    private void importObjects() throws Exception {
        generateEditions(editionsFile);

        try (final TerminologyService service = new TerminologyService()) {
            service.setModifiedFlag(false);

            // Persist Projects and Organizations
            int projectCount = 0;
            int organizationCount = 0;
            final Map<String, Project> projectIdToClassMap = new HashMap<>();
            final Map<String, Organization> organizationsAdded = new HashMap<>();

            for (String index : projectsMap.keySet()) {
                final Project project =
                        ModelUtility.fromJson(projectsMap.get(index), Project.class);

                if (!organizationsAdded.containsKey(project.getOrganization().getName())) {
                    setMetadata(project.getOrganization(), metadataMap.get("project-" + index));
                    service.add(project.getOrganization());

                    organizationsAdded.put(project.getOrganization().getName(),
                            project.getOrganization());
                    organizationCount++;
                } else {
                    project.setOrganization(
                            organizationsAdded.get(project.getOrganization().getName()));
                }

                setMetadata(project, metadataMap.get("project-" + index));
                service.add(project);
                projectIdToClassMap.put(index, project);
                projectCount++;
            }

            logger.debug("Have imported " + projectCount + " projects and " + organizationCount
                    + " organizations");

            // Persist Refsets & ECL Definition Clauses
            int count = 0;
            logger.info("About to import " + refsetsMap.keySet().size()
                    + " refsets and their respsective clauses");
            for (String rttId : refsetsMap.keySet()) {
                final Refset refset = ModelUtility.fromJson(refsetsMap.get(rttId), Refset.class);
                String projectId = refsetToProjectMap.get(rttId);
                refset.setProject(projectIdToClassMap.get(projectId));

                setMetadata(refset, metadataMap.get("refset-" + rttId));
                service.add(refset);

                if (rttRefsetToClausesMap.containsKey(rttId)) {
                    for (String clauseJson : rttRefsetToClausesMap.get(rttId)) {
                        final DefinitionClause clause =
                                ModelUtility.fromJson(clauseJson, DefinitionClause.class);

                        setMetadata(clause, metadataMap.get("refset-" + rttId));
                        service.add(clause);
                        refset.getDefinitionClauses().add(clause);
                    }
                }

                String namespace = null;
                String shortname = null;
                // Connect to proper edition
                if (refsetNamespaceMap.containsKey(refset.getRefsetId())) {
                    namespace = refsetNamespaceMap.get(refset.getRefsetId());
                }
                if (refsetShortnameMap.containsKey(refset.getRefsetId())) {
                    shortname = refsetShortnameMap.get(refset.getRefsetId());
                }

                Edition edition = null;
                if (namespace != null) {
                    edition = namespaceEditionsMap.get(namespace);
                }

                if (shortname != null) {
                    edition = shortNameEditionsMap.get(shortname);
                }

                if (edition == null) {
                    logger.debug("BBB - No edition for refset: " + refset.getRefsetId());
                }

                refset.setEdition(edition);
                service.update(refset);
                count++;

                if (count % 250 == 0) {
                    logger.debug("Imported + " + count + " refsets thus far");
                }
            }

            logger.info("Total of " + count + " refsets successfully added");
        } catch (Exception e) {
            logger.error("Have issue with: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Line to project json.
     *
     * @param line the line
     * @return the string
     * @throws Exception the exception
     */
    private String lineToProjectJson(final String line) throws Exception {
        String projectName;
        String projectDescription;
        String organizationName;
        String modified;
        String modifiedBy;

        if (line.split(",")[1].startsWith("\"")) {
            // If description has commas (and some do), can't rely on splitting
            // on comma. Must identify Description and then remove from line
            // before finding other values
            final int descStartIdx = line.indexOf("\"");
            final int descEndIdx = line.substring(descStartIdx + 1).indexOf("\"");

            projectDescription = line.substring(descStartIdx + 1, descStartIdx + descEndIdx + 1);
            String[] values = line.substring(descStartIdx + descEndIdx + 3).split(",");

            projectName = values[5].replaceAll("\"", "");
            organizationName = values[7].replaceAll("\"", "");
            modified = values[2];
            modifiedBy = values[3];

            // Based on Project Info
            identifyEditionInfo(values[6].replaceAll("\"", ""), values[8].replaceAll("\"", ""));
        } else {
            String[] values = line.split(",");

            projectDescription = values[1];
            projectName = values[7].replaceAll("\"", "");
            organizationName = values[9].replaceAll("\"", "");
            modified = values[4];
            modifiedBy = values[5];

            // Based on Project Info
            identifyEditionInfo(values[8].replaceAll("\"", ""), values[10].replaceAll("\"", ""));
        }

        StringBuffer buf = new StringBuffer();

        buf.append("{");
        buf.append("\"name\": \"" + projectName + "\",");
        buf.append("\"description\": \"" + projectDescription + "\",");
        buf.append("\"organization\": {\"name\": \"" + organizationName + "\"}");
        buf.append("}");

        metadataMap.put("project-" + line.split(",")[0], new Metadata(modified, modifiedBy));

        return buf.toString();
    }

    /**
     * Identify edition info.
     *
     * @param namespace the namespace
     * @param shortName the short name
     */
    private void identifyEditionInfo(final String namespace, final String shortName) {
        /*-
         * For missing namespace analysis only
        if (!shortNameToNamespaceMap.containsKey(shortName)) {
            shortNameToNamespaceMap.put(shortName, namespace);
            cccNameToNamespace.put(shortName, new HashSet<>());
            cccNameToNamespace.get(shortName).add(namespace);
        } else if (!shortNameToNamespaceMap.get(shortName).equals(namespace)) {
            cccNameToNamespace.get(shortName).add(namespace);
        }
        */

        if (!shortNameToNamespaceMap.containsKey(shortName)) {
            shortNameToNamespaceMap.put(shortName, namespace);
        }
    }

    /**
     * Line to clause json.
     *
     * @param line the line
     * @return the string
     */
    private String lineToClauseJson(final String line) {
        StringBuffer buf = new StringBuffer();
        String[] clauseValues = line.split(",");
        buf.append("{ \"negated\":\"");
        buf.append(clauseValues[1].equals("0") ? "false" : "true");
        buf.append("\",");

        buf.append(
                "\"value\":\"" + clauseValues[2].replaceAll("\"", "").replaceAll("\t", "") + "\"}");
        return buf.toString();
    }

    /**
     * Line to refset json.
     *
     * @param line the line
     * @return the string
     */
    private String lineToRefsetJson(final String line) {
        String updatedLine = line;

        try {
            String narrative;
            String name;
            if (updatedLine.split(",")[9].startsWith("\"")) {
                // If narrative has commas (and some do), can't rely on
                // splitting on comma. Must identify narrative and then
                // remove from line before finding other values
                final int descStartIdx = updatedLine.indexOf(updatedLine.split(",")[9]);
                final int descEndIdx = updatedLine.substring(descStartIdx + 1).indexOf("\"");
                narrative = updatedLine.substring(descStartIdx + 1, descStartIdx + descEndIdx + 1);

                // Cleanup updateLine to remove ',' in narrative
                updatedLine = updatedLine.substring(0, descStartIdx) + narrative.replaceAll(",", "")
                        + updatedLine.substring(descStartIdx + descEndIdx + 2);
            } else {
                narrative = updatedLine.split(",")[9];
            }

            if (updatedLine.split(",")[17].startsWith("\"")) {
                // If narrative has commas (and some do), can't rely on
                // splitting on comma. Must identify narrative and then
                // remove from line before finding other values
                final int descStartIdx = updatedLine.indexOf(updatedLine.split(",")[17]);
                final int descEndIdx = updatedLine.substring(descStartIdx + 1).indexOf("\"");
                name = updatedLine.substring(descStartIdx + 1, descStartIdx + descEndIdx + 1);

                // Cleanup line to remove ',' in narrative
                updatedLine = updatedLine.substring(0, descStartIdx) + narrative.replaceAll(",", "")
                        + updatedLine.substring(descStartIdx + descEndIdx + 2);
            } else {
                name = updatedLine.split(",")[17];
            }

            updatedLine = updatedLine.replace("\"", "");
            final String values[] = updatedLine.split(",");
            final StringBuffer buf = new StringBuffer();
            final String rttRefsetId = values[0];

            buf.append("{");
            buf.append("\"refsetId\": \"" + values[8] + "\",");
            buf.append("\"active\": " + ((values[1].equals("1")) ? "true" : "false") + ",");
            buf.append("\"name\": \"" + name + "\",");
            buf.append("\"type\": \"" + values[24] + "\",");
            buf.append("\"narrative\": \"" + narrative + "\",");

            if (!values[2].equals("NULL")) {
                final String versionDate = values[2].replace(" ", "T");
                buf.append("\"versionDate\": \"" + versionDate + "\",");
            }

            if (values[28] != null && !values[28].isEmpty() && !values[28].equals("NULL")) {
                buf.append("\"tags\": [\"" + values[28] + "\"],");
            }
            buf.append("\"versionStatus\": \"" + values[26] + "\",");
            buf.append("\"privateRefset\": " + ((values[15].equals("0")) ? "true" : "false") + ",");
            buf.append("\"localSet\": " + ((values[29].equals("1")) ? "true" : "false") + ",");

            if (!values[11].equals("NULL")) {
                buf.append("\"externalUrl\": \"" + values[11] + "\",");
            }

            // RefsetId to ProjectId
            refsetToProjectMap.put(rttRefsetId, values[27]);

            // RefsetId to Namespace
            if (values[18] != null && !values[18].equalsIgnoreCase("null")) {
                refsetNamespaceMap.put(values[8], values[18]);
            }

            // RefsetId to ShortName
            if (values[18] != null && !values[23].equalsIgnoreCase("null")) {
                refsetShortnameMap.put(values[8], values[23]);
            }

            // Finish Refset
            buf.append("\"moduleId\": \"" + values[15] + "\"");
            buf.append("}");

            // Based on Refset Data
            identifyEditionInfo(values[18], values[23]);

            Metadata meta = new Metadata(values[3], values[4]);
            metadataMap.put("refset-" + rttRefsetId, meta);

            return buf.toString();
        } catch (Exception e) {
            logger.error("Line: " + line + " and updateLine: " + updatedLine);
            e.printStackTrace();

            throw e;
        }
    }

    /**
     * Generate editions.
     *
     * @param filePath the file path
     * @throws Exception the exception
     */
    private void generateEditions(final String filePath) throws Exception {
        final List<String> projectsJson = FileUtility.readFileToArray(filePath);

        final ObjectMapper mapper = new ObjectMapper();
        final JsonNode root = mapper.readTree(projectsJson.toString());

        try (final TerminologyService service = new TerminologyService()) {
            service.setModifiedBy("Migration");
            service.setModifiedFlag(true);

            int count = 0;
            Iterator<JsonNode> itr = root.iterator();
            while (itr.hasNext()) {
                JsonNode editionJson = itr.next();

                Edition e = new Edition();
                e.setName(editionJson.get("name").asText());
                e.setShortName(editionJson.get("shortName").asText());
                e.setBranch(editionJson.get("branchPath").asText());
                e.setDefaultLanguageCode(editionJson.get("defaultLanguageCode").asText());

                if (!shortNameToNamespaceMap.containsKey(e.getShortName())) {
                    logger.debug("DDD - Listing for Customer Feedback: Edition without defined "
                            + "namespace (from project or refsets). We probably need to use "
                            + "module?: " + e.getName());
                    e.setNamespace("To Be Defined");
                } else {
                    e.setNamespace(shortNameToNamespaceMap.get(e.getShortName()));
                }

                if (editionJson.has("defaultLanguageReferenceSets")) {
                    ArrayNode languageNodeArray =
                            (ArrayNode) editionJson.get("defaultLanguageReferenceSets");
                    for (JsonNode languageNode : languageNodeArray) {
                        e.getDefaultLanguageRefsets().add(languageNode.asText());
                    }
                }

                service.add(e);
                shortNameEditionsMap.put(e.getShortName(), e);
                namespaceEditionsMap.put(e.getNamespace(), e);
                count++;
            }

            logger.info("Imported " + count + " editions");
        } catch (Exception e) {
            logger.error("Have issue with: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Preprocessing supporting files.
     *
     * @throws Exception the exception
     */
    private void preprocessingSupportingFiles() throws Exception {
        populateFromFile(clausesFile, FileProcessType.CLAUSE);
        populateFromFile(projectsFile, FileProcessType.PROJECT);
    }

    /**
     * Sets the metadata.
     *
     * @param object the object
     * @param metadata the metadata
     */
    private void setMetadata(final HasModified object, final Metadata metadata) {
        object.setModified(metadata.getModified());
        object.setCreated(metadata.getModified());
        object.setModifiedBy(metadata.getModifiedBy());
    }
}
