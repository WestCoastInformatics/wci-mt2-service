package org.ihtsdo.refsetservice.util.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

import org.ihtsdo.refsetservice.model.DefinitionClause;
import org.ihtsdo.refsetservice.model.Edition;
import org.ihtsdo.refsetservice.model.HasModified;
import org.ihtsdo.refsetservice.model.Organization;
import org.ihtsdo.refsetservice.model.Project;
import org.ihtsdo.refsetservice.model.Refset;
import org.ihtsdo.refsetservice.service.TerminologyService;
import org.ihtsdo.refsetservice.terminologyservice.SnowstormConnection;
import org.ihtsdo.refsetservice.test.BaseTest;
import org.ihtsdo.refsetservice.util.ModelUtility;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Validates the code-system-* files in the "src/resources" folder.
 */
public class RefsetMetadataMigrationTest extends BaseTest {

    /** The formatter. */
    SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");

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

    /** The Constant DEFAULT_LANGUAGE_SET_ID. */
    private static final String DEFAULT_LANGUAGE_SET_ID = "900000000000509007";

    /** The logger. */
    private final Logger logger = LoggerFactory.getLogger(RefsetMetadataMigrationTest.class);

    /** The projects file. */
    private final String projectsFile = "src/test/resources/migration/refsetsToImport/projects.txt";

    /** The clauses file. */
    private final String clausesFile = "src/test/resources/migration/refsetsToImport/clauses.txt";

    /** The all refsets file path. */
    private final String allRefsetsFilePath =
            "src/test/resources/migration/refsetsToImport/AllFromRTT.txt";

    /** The metadata map. */
    private final Map<String, Metadata> metadataMap = new HashMap<>();

    /** The json map. */
    private final Map<String, String> refsetInternalIdMap = new HashMap<>();

    private final Map<String, String> refsetSctIdToInternalIdMap = new HashMap<>();

    /** The rtt refset to clauses map. */
    private final Map<String, ArrayList<String>> rttRefsetToClausesMap = new HashMap<>();

    /** The projects map. */
    private final Map<String, String> projectsMap = new HashMap<>();

    /** The refset to project map. */
    private final Map<String, String> refsetToProjectMap = new HashMap<>();

    /** The short name editions map. */
    private final Map<String, Edition> shortNameEditionsMap = new HashMap<>();

    private final Set<String> refsetsToIgnore = new HashSet<>();

    /**
     * Test all refsets.
     *
     * @throws Exception the exception
     */
    @Test
    public void testAllRefsets() throws Exception {
        createEditionsFromSnowstorm();
        Set<Refset> allRefsets = createRefsetsFromSnowstorm();

        preprocessingSupportingFiles();

        // Skip those refsets that live on SnowS, but I don't have a dmp of
        // yet
        for (Refset refset : allRefsets) {
            if (!refsetSctIdToInternalIdMap.keySet().contains(refset.getRefsetId())) {
                refsetsToIgnore.add(refset.getRefsetId());
            }
        }

        updateRefsets(allRefsets);

        // TODO: Update this
        createAllRefsetVersions(allRefsets);

        persistObjects(allRefsets);
    }

    private void createAllRefsetVersions(Set<Refset> allRefsets) {
        for (Refset refset : allRefsets) {
            if (refsetsToIgnore.contains(refset.getRefsetId())) {
                continue;
            }

            refset.setVersionStatus("PUBLISHED");
        }
    }

    private void updateRefsets(Set<Refset> allRefsets)
        throws JsonMappingException, JsonProcessingException {
        for (Refset refset : allRefsets) {
            if (refsetsToIgnore.contains(refset.getRefsetId())) {
                continue;
            }

            final String rttId = refsetSctIdToInternalIdMap.get(refset.getRefsetId());
            final String refsetJsonString = refsetInternalIdMap.get(rttId);

            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode refsetJson = mapper.readTree(refsetJsonString);
            logger.debug(refsetJson.toString());

            refset.setType(refsetJson.get("type").asText());
            refset.setNarrative(refsetJson.get("narrative").asText());
            refset.setPrivateRefset(refsetJson.get("privateRefset").asBoolean());

            // Tags
            if (refsetJson.has("tags")) {
                Iterator<JsonNode> tagsIterator = refsetJson.get("tags").iterator();
                while (tagsIterator.hasNext()) {
                    refset.getTags().add(tagsIterator.next().asText());
                }
            }
        }

    }

    /**
     * Populate editions.
     *
     * @param internationalModules the international modules
     * @return the sets the
     * @throws Exception the exception
     */
    private Set<Refset> createRefsetsFromSnowstorm() throws Exception {
        Set<Refset> allRefsets = new HashSet<>();

        String url = SnowstormConnection.BASE_URL + "browser/{branch}/members";

        try (final TerminologyService service = new TerminologyService()) {
            service.setModifiedBy("Migration");
            service.setModifiedFlag(true);

            HashSet<String> internationalRefsets = new HashSet<>();
            List<Edition> editions = service.getAll(Edition.class);

            BufferedWriter writer = new BufferedWriter(new FileWriter("RefsetsAdded.txt"));

            for (Edition edition : editions) {
                logger.debug("Processing Edition: " + edition.getName());
                writer.append("\n\n\nProcessing Edition: " + edition.getName() + "\n");
                try (final Response response = SnowstormConnection
                        .getResponse(url.replace("{branch}", edition.getBranch()))) {
                    if (response.getStatusInfo().getFamily() != Family.SUCCESSFUL) {
                        if (edition.getBranch().startsWith("MAIN")) {
                            throw new Exception("Unable to process this edition: " + edition);
                        } else {
                            logger.debug("Found that '" + edition.getName() + "' has odd branch: "
                                    + edition.getBranch());
                            continue;
                        }
                    }
                    final String resultString = response.readEntity(String.class);
                    final ObjectMapper mapper = new ObjectMapper();
                    final JsonNode root = mapper.readTree(resultString.toString());

                    // get RefSets from edition as long as a) active & b) within
                    // edition's module
                    final Iterator<JsonNode> refsetIterator = root.get("referenceSets").iterator();

                    while (refsetIterator.hasNext()) {
                        final JsonNode refsetNode = refsetIterator.next();
                        final String moduleId = refsetNode.get("moduleId").asText();
                        final String refsetId = refsetNode.get("conceptId").asText();

                        if (refsetNode.get("active").asBoolean() && (internationalRefsets.isEmpty()
                                || !internationalRefsets.contains(refsetId))) {
                            // Process Valid Refset
                            try {
                                Refset refset = new Refset();

                                refset.setRefsetId(refsetNode.get("conceptId").asText());
                                String refsetDate = refsetNode.get("effectiveTime").asText();
                                refset.setVersionDate(formatter.parse(refsetDate));
                                refset.setModuleId(moduleId);
                                refset.setEdition(edition);
                                refset.setActive(true);

                                if (refsetNode.get("pt").has("term")) {
                                    refset.setName(refsetNode.get("pt").get("term").asText());
                                } else {
                                    refset.setName(lookupRefsetName(refsetId, edition));
                                }

                                writer.write("Adding refset: " + refsetId);
                                allRefsets.add(refset);

                                if (edition.getName().equals("International Edition")) {
                                    internationalRefsets.add(refsetNode.get("conceptId").asText());
                                }

                                writer.write("\n");
                            } catch (Exception e) {
                                logger.error("Failed with refsetNode: " + refsetNode);
                            }
                        }
                    }

                }

            }
            writer.close();
        }

        return allRefsets;
    }

    /**
     * Lookup refset name.
     *
     * @param refsetId the refset id
     * @param edition the edition
     * @return the string
     * @throws Exception the exception
     */
    private String lookupRefsetName(String refsetId, Edition edition) throws Exception {
        String url = SnowstormConnection.BASE_URL + "browser/" + edition.getBranch() + "/concepts/"
                + refsetId;

        try (final Response response = SnowstormConnection.getResponse(url)) {
            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode conceptNode = mapper.readTree(resultString.toString());

            Iterator<JsonNode> descriptionIterator = conceptNode.get("descriptions").iterator();
            while (descriptionIterator.hasNext()) {
                JsonNode descriptionNode = descriptionIterator.next();
                String acceptability = null;

                if (descriptionNode.get("type").asText().equals("SYNONYM") && descriptionNode
                        .get("lang").asText().equals(edition.getDefaultLanguageCode())) {
                    final JsonNode acceptabilityMap = descriptionNode.get("acceptabilityMap");
                    for (String langRefsetId : edition.getDefaultLanguageRefsets()) {
                        if (acceptabilityMap.has(langRefsetId)) {
                            acceptability = acceptabilityMap.get(langRefsetId).asText();
                            break;
                        }
                    }

                    if (acceptability == null) {
                        throw new Exception(
                                "Not able to properly identify refset name for description: "
                                        + descriptionNode);
                    }
                    if (acceptability.equals("PREFERRED")) {
                        return descriptionNode.get("term").asText();
                    }
                }
            }

            throw new Exception("Unable to find PrefTerm for refset concept: " + conceptNode);
        }
    }

    /**
     * Populate editions.
     *
     * @return the sets the
     * @throws Exception the exception
     */
    private void createEditionsFromSnowstorm() throws Exception {
        // SHould have 3 results
        String url = SnowstormConnection.BASE_URL + "/codesystems";

        try (final Response response = SnowstormConnection.getResponse(url)) {

            final String resultString = response.readEntity(String.class);
            final ObjectMapper mapper = new ObjectMapper();
            final JsonNode root = mapper.readTree(resultString.toString());

            try (final TerminologyService service = new TerminologyService()) {
                service.setModifiedBy("Migration");
                service.setModifiedFlag(true);

                final Iterator<JsonNode> responseIterator = root.iterator();

                while (responseIterator.hasNext()) {

                    final Iterator<JsonNode> codeSystems = responseIterator.next().iterator();

                    while (codeSystems.hasNext()) {
                        JsonNode codeSystem = codeSystems.next();

                        // Process Edition
                        Edition edition = new Edition();

                        edition.setName(codeSystem.get("name").asText());
                        edition.setShortName(codeSystem.get("shortName").asText());
                        edition.setBranch(codeSystem.get("branchPath").asText());

                        if (codeSystem.has("defaultLanguageReferenceSets")) {
                            final JsonNode defaultLanguageReferenceSets =
                                    codeSystem.get("defaultLanguageReferenceSets");
                            final Iterator<JsonNode> defaultLanguageReferencesSetIterator =
                                    defaultLanguageReferenceSets.iterator();
                            while (defaultLanguageReferencesSetIterator.hasNext()) {
                                edition.getDefaultLanguageRefsets()
                                        .add(defaultLanguageReferencesSetIterator.next().asText());
                            }

                        } else {
                            edition.getDefaultLanguageRefsets().add(DEFAULT_LANGUAGE_SET_ID);
                        }

                        if (codeSystem.has("defaultLanguageCode")) {
                            edition.setDefaultLanguageCode(
                                    codeSystem.get("defaultLanguageCode").asText());
                        } else if (codeSystem.has("languages")) {
                            edition.setDefaultLanguageCode(
                                    codeSystem.get("languages").fieldNames().next());
                        } else {
                            throw new Exception("No langauages for edition: " + edition.toString());
                        }

                        final Edition storedEdition = service.add(edition);

                        // TODO: Still need this?
                        shortNameEditionsMap.put(storedEdition.getShortName(), storedEdition);
                    }
                }
            }
        }
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
                        refsetInternalIdMap.put(line.split(",")[0], refsetJson);
                        refsetSctIdToInternalIdMap.put(line.split(",")[8], line.split(",")[0]);
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
    private void persistObjects(Set<Refset> allRefsets) throws Exception {

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
            logger.info("About to import " + refsetInternalIdMap.keySet().size()
                    + " refsets and their respsective clauses");

            for (Refset refset : allRefsets) {
                if (refsetsToIgnore.contains(refset.getRefsetId())) {
                    continue;
                }
                final String rttId = refsetSctIdToInternalIdMap.get(refset.getRefsetId());
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

                    service.update(refset);
                }

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
        } else {
            String[] values = line.split(",");

            projectDescription = values[1];
            projectName = values[7].replaceAll("\"", "");
            organizationName = values[9].replaceAll("\"", "");
            modified = values[4];
            modifiedBy = values[5];
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
        String narrative;

        try {
            // Clean up narrative if has commas which some do
            if (updatedLine.split(",")[9].startsWith("\"")) {
                // Can't rely on splitting on comma. Must identify narrative and
                // then remove from line before finding other values
                final int descStartIdx = updatedLine.indexOf(updatedLine.split(",")[9]);
                final int descEndIdx = updatedLine.substring(descStartIdx + 1).indexOf("\"");
                narrative = updatedLine.substring(descStartIdx + 1, descStartIdx + descEndIdx + 1);

                // Cleanup updateLine to remove ',' in narrative
                updatedLine = updatedLine.substring(0, descStartIdx) + narrative.replaceAll(",", "")
                        + updatedLine.substring(descStartIdx + descEndIdx + 2);
            } else {
                narrative = updatedLine.split(",")[9];
            }

            // Clean up name if has commas (which some do)
            if (updatedLine.split(",")[17].startsWith("\"")) {
                // Can't rely on splitting on comma. Must identify name portion
                // and then remove from line before finding other values
                final int descStartIdx = updatedLine.indexOf(updatedLine.split(",")[17]);
                final int descEndIdx = updatedLine.substring(descStartIdx + 1).indexOf("\"");

                // Cleanup line to remove ',' in narrative
                updatedLine = updatedLine.substring(0, descStartIdx) + narrative.replaceAll(",", "")
                        + updatedLine.substring(descStartIdx + descEndIdx + 2);
            }

            updatedLine = updatedLine.replace("\"", "");
            final String values[] = updatedLine.split(",");
            final StringBuffer buf = new StringBuffer();
            final String rttRefsetId = values[0];

            // Begin RefsetJson
            buf.append("{");
            buf.append("\"type\": \"" + values[24] + "\",");
            buf.append("\"narrative\": \"" + narrative + "\",");
            buf.append("\"privateRefset\": " + ((values[15].equals("0")) ? "true" : "false"));

            // Tags
            if (values[28] != null && !values[28].isEmpty() && !values[28].equals("NULL")) {
                buf.append(",");
                buf.append("\"tags\": [\"" + values[28] + "\"]");
            }
            buf.append("}");
            // End RefsetJson

            // Store ability to map from RefsetId to ProjectId
            refsetToProjectMap.put(rttRefsetId, values[27]);

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
     * Pre-processing supporting files.
     *
     * @throws Exception the exception
     */
    private void preprocessingSupportingFiles() throws Exception {
        populateFromFile(clausesFile, FileProcessType.CLAUSE);
        populateFromFile(projectsFile, FileProcessType.PROJECT);
        populateFromFile(allRefsetsFilePath, FileProcessType.REFSET);
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
